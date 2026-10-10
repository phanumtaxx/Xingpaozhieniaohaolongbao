package dev.xingclient.manager;

import dev.xingclient.event.EventBus;
import dev.xingclient.event.PacketCommitEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;

/** Executes slot commands issued by the native manager. Author: uint32. */
public final class InventoryManager {
    public enum SwapMode { CLIENT, SILENT, NONE }
    public enum TransactionState { IDLE, SWAP_SENT, ACTION_SENT, RESTORE_SENT, CONFIRMED, TIMEOUT }
    public enum PacketPhase { UNTRACKED, SWAP, ACTION, RESTORE }
    public record SlotState(long ownerId, int priority, int leaseTicks, long silentOwnerId,
            long transactionId, int targetSlot, int realSlot, TransactionState transactionState,
            TransactionState lastTransactionState, long swapCommitId, long actionCommitId,
            long restoreCommitId, int ageTicks, int restoreTicks) {}

    private final NativeManagerBridge bridge;
    private final NetworkManager network;
    private final CombatActionScheduler scheduler;
    private final ServerStateTracker serverState;
    private final Map<Long, ActionOwner> owners = new HashMap<>();
    private final ActionOwner restoreOwner = new ActionOwner("Inventory restore");
    private boolean executing;

    InventoryManager(NativeManagerBridge bridge, NetworkManager network, CombatActionScheduler scheduler,
            ServerStateTracker serverState, EventBus events) {
        this.bridge = bridge;
        this.network = network;
        this.scheduler = scheduler;
        this.serverState = serverState;
        network.slotTransactions(this::transactionId);
        events.subscribe(PacketCommitEvent.class, this::handleCommitted);
    }

    public int findHotbar(Predicate<ItemStack> matches) {
        CombatActionScheduler.requireClientThread();
        Objects.requireNonNull(matches);
        var player = MinecraftClient.getInstance().player;
        if (player == null) return -1;
        int selected = player.getInventory().getSelectedSlot();
        if (matches.test(player.getInventory().getStack(selected))) return selected;
        for (int slot = 0; slot < 9; slot++) if (matches.test(player.getInventory().getStack(slot))) return slot;
        return -1;
    }

    public SlotState state() { return bridge.call(NativeManagerBridge.SLOT_INSPECT, null, -1).slot(); }
    public boolean refill(ActionOwner owner, int source, int target) {
        CombatActionScheduler.requireClientThread();
        if (source < 9 || source >= 36 || target < 36 || target >= 45) return false;
        return scheduler.execute(owner, 30, 1, null, () -> {
            var client = MinecraftClient.getInstance();
            var player = client.player;
            if (player == null || client.interactionManager == null || client.currentScreen != null
                    || player.currentScreenHandler != player.playerScreenHandler
                    || !player.currentScreenHandler.getCursorStack().isEmpty() || executing) return false;
            var menu = player.currentScreenHandler;
            var from = menu.getSlot(source).getStack();
            var to = menu.getSlot(target).getStack();
            if (from.isEmpty() || to.isEmpty() || !ItemStack.areItemsAndComponentsEqual(from, to)
                    || to.getCount() >= to.getMaxCount()) return false;
            executing = true;
            try {
                client.interactionManager.clickSlot(menu.syncId, source, 0,
                        SlotActionType.PICKUP, player);
                try {
                    client.interactionManager.clickSlot(menu.syncId, target, 0,
                            SlotActionType.PICKUP, player);
                } finally {
                    if (!menu.getCursorStack().isEmpty()) client.interactionManager.clickSlot(menu.syncId, source, 0,
                            SlotActionType.PICKUP, player);
                }
                return true;
            } finally { executing = false; }
        }, ActionResource.INVENTORY).success();
    }
    public boolean isOwnedBy(ActionOwner owner) { return state().ownerId() == Objects.requireNonNull(owner).id(); }
    public boolean hasSilentSwap(ActionOwner owner) { return state().silentOwnerId() == Objects.requireNonNull(owner).id(); }
    public long actionCommitId(ActionOwner owner) {
        SlotState state = state();
        return state.silentOwnerId() == Objects.requireNonNull(owner).id() ? state.actionCommitId() : -1;
    }

    public boolean tryAcquire(ActionOwner owner, int priority, int holdTicks) {
        return requestLease(NativeManagerBridge.SLOT_ACQUIRE, owner, priority, -1, holdTicks);
    }

    public boolean beginSilentSwap(ActionOwner owner, int priority, int slot, int holdTicks) {
        if (slot < 0 || slot > 8) return false;
        return requestLease(NativeManagerBridge.SLOT_BEGIN_SILENT, owner, priority, slot, holdTicks);
    }

    private boolean requestLease(int event, ActionOwner owner, int priority, int slot, int holdTicks) {
        CombatActionScheduler.requireClientThread();
        Objects.requireNonNull(owner);
        var player = MinecraftClient.getInstance().player;
        if (player == null || MinecraftClient.getInstance().getNetworkHandler() == null) return false;
        if (!scheduler.reserve(owner, priority, holdTicks, ActionResource.INVENTORY)) return false;
        owners.put(owner.id(), owner);
        var result = bridge.slotRequest(event, owner, priority, slot, player.getInventory().getSelectedSlot(),
                serverState.selectedServerSlot(), holdTicks, true);
        boolean applied = apply(result, owner);
        if (!result.accepted() || !applied) {
            if (!isOwnedBy(owner)) scheduler.release(owner, ActionResource.INVENTORY);
            return false;
        }
        return true;
    }

    public void scheduleSilentRestore(ActionOwner owner, int delayTicks) {
        Objects.requireNonNull(owner);
        bridge.scheduleRestore(owner, delayTicks);
    }

    public void restoreSilentSwap(ActionOwner owner) {
        CombatActionScheduler.requireClientThread();
        Objects.requireNonNull(owner);
        var player = MinecraftClient.getInstance().player;
        if (player != null) apply(bridge.call(NativeManagerBridge.SLOT_RESTORE_SILENT, owner,
                player.getInventory().getSelectedSlot()), owner);
    }

    public void keepAlive(ActionOwner owner) {
        CombatActionScheduler.requireClientThread();
        Objects.requireNonNull(owner);
        var player = MinecraftClient.getInstance().player;
        if (player != null) bridge.call(NativeManagerBridge.SLOT_KEEP_ALIVE, owner, player.getInventory().getSelectedSlot());
    }

    public boolean withSlot(ActionOwner owner, int priority, int slot, SwapMode mode,
            int restoreDelay, BooleanSupplier action) {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(mode);
        Objects.requireNonNull(action);
        int holdTicks = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, restoreDelay + 1L));
        return scheduler.execute(owner, priority, holdTicks, null,
                () -> executeSlot(owner, priority, slot, mode, restoreDelay, holdTicks, action), ActionResource.INVENTORY).success();
    }

    private boolean executeSlot(ActionOwner owner, int priority, int slot, SwapMode mode,
            int restoreDelay, int holdTicks, BooleanSupplier action) {
        var player = MinecraftClient.getInstance().player;
        if (player == null || slot < 0 || slot > 8 || executing) return false;
        int selected = player.getInventory().getSelectedSlot();
        if (mode == SwapMode.NONE) return selected == slot && action.getAsBoolean();
        owners.put(owner.id(), owner);
        var begin = bridge.slotRequest(NativeManagerBridge.SLOT_BEGIN, owner, priority, slot, selected,
                serverState.selectedServerSlot(), holdTicks, mode == SwapMode.SILENT);
        boolean success = false;
        executing = true;
        try {
            if (!apply(begin, owner) || !begin.accepted()) return false;
            success = action.getAsBoolean();
            return success;
        } finally {
            try {
                if (begin.accepted()) apply(bridge.finishSlot(owner, restoreDelay, success,
                        player.getInventory().getSelectedSlot()), owner);
            } finally { executing = false; }
        }
    }

    public void release(ActionOwner owner) {
        CombatActionScheduler.requireClientThread();
        Objects.requireNonNull(owner);
        var player = MinecraftClient.getInstance().player;
        if (bridge.used() && player != null) apply(bridge.call(NativeManagerBridge.SLOT_RELEASE,
                owner, player.getInventory().getSelectedSlot()), owner);
        if (!bridge.used() || !isOwnedBy(owner)) scheduler.release(owner, ActionResource.INVENTORY);
    }

    void tick() {
        var player = MinecraftClient.getInstance().player;
        if (!bridge.used() || player == null) return;
        apply(bridge.call(NativeManagerBridge.SLOT_TICK, null, player.getInventory().getSelectedSlot()), restoreOwner);
    }

    void reset() { owners.clear(); executing = false; }

    private long transactionId(ActionOwner owner) {
        if (owner == null || !bridge.used() || !owners.containsKey(owner.id())) return 0;
        SlotState state = state();
        return state.silentOwnerId() == owner.id() ? state.transactionId() : 0;
    }

    private void handleCommitted(PacketCommitEvent event) {
        if (event.owner() == null || event.slotTransaction() == 0 || !bridge.used()) return;
        int slot = event.packet() instanceof UpdateSelectedSlotC2SPacket selected ? selected.getSelectedSlot() : -1;
        bridge.slotCommitted(event.owner(), event.slotTransaction(), event.slotPhase(), slot,
                serverState.selectedServerSlot(), event.commitId());
        if (!isOwnedBy(event.owner())) scheduler.release(event.owner(), ActionResource.INVENTORY);
        pruneOwners();
    }

    private boolean apply(NativeManagerBridge.Result commands, ActionOwner owner) {
        try {
            if (commands.restoreSlot() >= 0) {
                ActionOwner restoring = owners.getOrDefault(commands.restoreOwnerId(), owner);
                if (!select(restoring, commands.restoreSlot(), commands.restoreClient(), commands.restoreTransaction(), PacketPhase.RESTORE)) {
                    fail(restoring, commands.restoreTransaction());
                    if (commands.selectSlot() >= 0) fail(owner, commands.slot().transactionId());
                    return false;
                }
            }
            if (commands.selectSlot() >= 0 && !select(owner, commands.selectSlot(), commands.selectClient(),
                    commands.slot().transactionId(), PacketPhase.SWAP)) {
                fail(owner, commands.slot().transactionId());
                return false;
            }
            return true;
        } catch (RuntimeException | Error error) {
            if (commands.selectSlot() >= 0) fail(owner, commands.slot().transactionId());
            throw error;
        } finally { pruneOwners(); }
    }

    private void fail(ActionOwner owner, long transaction) {
        if (transaction != 0) bridge.slotFailed(owner, transaction);
        else bridge.call(NativeManagerBridge.SLOT_RELEASE, owner, -1);
        scheduler.release(owner, ActionResource.INVENTORY);
        pruneOwners();
    }

    private boolean select(ActionOwner owner, int slot, boolean clientSelection, long transaction, PacketPhase phase) {
        var player = MinecraftClient.getInstance().player;
        if (player == null) return false;
        try {
            if (!network.sendWithSlotTransaction(owner, new UpdateSelectedSlotC2SPacket(slot), transaction, phase)) return false;
        } catch (RuntimeException | Error error) {
            fail(owner, transaction);
            throw error;
        }
        if (clientSelection) {
            player.getInventory().setSelectedSlot(slot);
            var interaction = MinecraftClient.getInstance().interactionManager;
            if (interaction != null) ((dev.xingclient.mixin.ClientPlayerInteractionManagerAccessor) interaction).xing$setLastSelectedSlot(slot);
        }
        return true;
    }

    private void pruneOwners() {
        SlotState current = state();
        owners.entrySet().removeIf(entry -> {
            if (entry.getKey() == current.ownerId() || entry.getKey() == current.silentOwnerId()) return false;
            scheduler.release(entry.getValue(), ActionResource.INVENTORY);
            return true;
        });
    }
}
