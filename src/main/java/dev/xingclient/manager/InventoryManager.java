package dev.xingclient.manager;

import dev.xingclient.event.EventBus;
import dev.xingclient.event.PacketCommitEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.EquipmentSlot;
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
    private final List<TemporarySwap> temporarySwaps = new ArrayList<>();

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
        if (source < 9 || source >= 36 || target < 36 || target >= 45
                || reservedMenuSlot(source) || reservedMenuSlot(target)) return false;
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
            if (!InventoryClickBudget.reserve(3)) return false;
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
    public static boolean safeEquipmentState(MinecraftClient client) {
        var player = client.player;
        return player != null && client.world != null && client.interactionManager != null
                && player.isAlive() && !player.isDead() && client.getNetworkHandler() != null
                && client.getNetworkHandler().getConnection().isOpen()
                && player.currentScreenHandler == player.playerScreenHandler
                && player.currentScreenHandler.syncId == 0 && player.currentScreenHandler.getCursorStack().isEmpty();
    }

    public boolean swapWithHotbar(ActionOwner owner, int source, int hotbar) {
        CombatActionScheduler.requireClientThread();
        if (source < 9 || source >= 36 || hotbar < 0 || hotbar >= 9
                || reservedMenuSlot(source) || reservedMenuSlot(hotbar + 36)) return false;
        try {
            return scheduler.execute(owner, 10, 1, null, () -> {
                var client = MinecraftClient.getInstance();
                if (!safeEquipmentState(client) || executing) return false;
                if (!InventoryClickBudget.reserve(1)) return false;
                var player = client.player;
                var menu = player.currentScreenHandler;
                var expectedSource = player.getInventory().getStack(hotbar).copy();
                var expectedHotbar = player.getInventory().getStack(source).copy();
                executing = true;
                try {
                    client.interactionManager.clickSlot(menu.syncId, source, hotbar, SlotActionType.SWAP, player);
                    return ItemStack.areEqual(player.getInventory().getStack(source), expectedSource)
                            && ItemStack.areEqual(player.getInventory().getStack(hotbar), expectedHotbar);
                } finally { executing = false; }
            }, ActionResource.INVENTORY).success();
        } finally { scheduler.release(owner, ActionResource.INVENTORY); }
    }

    public TemporarySwap temporarySwap(ActionOwner owner, int menuSlot, int hotbar) {
        CombatActionScheduler.requireClientThread();
        Objects.requireNonNull(owner);
        if ((menuSlot != 6 && (menuSlot < 9 || menuSlot >= 36)) || hotbar < 0 || hotbar >= 9) return null;
        var client = MinecraftClient.getInstance();
        if (!safeEquipmentState(client) || executing || hasPendingRestore()) return null;
        for (var existing : temporarySwaps) {
            boolean overlaps = existing.menuSlot == menuSlot || existing.hotbar == hotbar;
            boolean chestCycle = existing.owner == owner && menuSlot == 6 && existing.menuSlot >= 9;
            if (overlaps && !chestCycle) return null;
        }
        var budget = InventoryClickBudget.hold(2);
        if (budget == null) return null;
        var swap = new TemporarySwap(owner, menuSlot, hotbar, budget);
        temporarySwaps.add(swap);
        try {
            if (swap.exchange(false)) return swap;
        } catch (RuntimeException | Error error) {
            try { swap.requestRestore(); }
            catch (RuntimeException | Error cleanupError) { error.addSuppressed(cleanupError); }
            throw error;
        }
        swap.requestRestore();
        return null;
    }

    private boolean reservedMenuSlot(int slot) {
        return temporarySwaps.stream().anyMatch(swap -> swap.menuSlot == slot || swap.hotbar + 36 == slot);
    }

    public boolean hasPendingRestore() {
        return temporarySwaps.stream().anyMatch(swap -> swap.restoreRequested);
    }

    public final class TemporarySwap {
        private final ActionOwner owner;
        private final int menuSlot, hotbar;
        private final ClientPlayerEntity player;
        private final ClientWorld world;
        private final ItemStack originalSource, originalHotbar;
        private final InventoryClickBudget.Reservation budget;
        private boolean restoreRequested, finished, swapped, warned;

        private TemporarySwap(ActionOwner owner, int menuSlot, int hotbar, InventoryClickBudget.Reservation budget) {
            this.owner = owner;
            this.menuSlot = menuSlot;
            this.hotbar = hotbar;
            this.budget = budget;
            var client = MinecraftClient.getInstance();
            player = client.player;
            world = client.world;
            originalSource = player.playerScreenHandler.getSlot(menuSlot).getStack().copy();
            originalHotbar = player.getInventory().getStack(hotbar).copy();
        }

        public boolean matches() {
            return !finished && currentWorld() && swapped
                    && matchesStack(player.playerScreenHandler.getSlot(menuSlot).getStack(), originalHotbar)
                    && matchesStack(player.getInventory().getStack(hotbar), originalSource);
        }

        public boolean restore() {
            CombatActionScheduler.requireClientThread();
            if (finished) return true;
            restoreRequested = true;
            // Never replay inventory clicks against a replacement player or world.
            if (!currentWorld() || !player.isAlive()) { finish(); return true; }
            var client = MinecraftClient.getInstance();
            if (!safeEquipmentState(client) || executing) return false;
            if (originalLayout()) { finish(); return true; }
            if (!matches()) {
                if (!warned) org.slf4j.LoggerFactory.getLogger("xingclient-inventory").warn(
                        "Restore waiting: items changed in menu slot {} or hotbar slot {}", menuSlot, hotbar);
                warned = true;
                return false;
            }
            if (!exchange(true)) return false;
            finish();
            return true;
        }

        public void requestRestore() { restoreRequested = true; restore(); }

        private boolean originalLayout() {
            return matchesStack(player.playerScreenHandler.getSlot(menuSlot).getStack(), originalSource)
                    && matchesStack(player.getInventory().getStack(hotbar), originalHotbar);
        }

        private boolean currentWorld() {
            var client = MinecraftClient.getInstance();
            return client.player == player && client.world == world;
        }

        private boolean exchange(boolean restoring) {
            try {
                return scheduler.execute(owner, 10, 1, null, () -> {
                    var client = MinecraftClient.getInstance();
                    if (!currentWorld() || !safeEquipmentState(client) || executing) return false;
                    if (restoring ? !matches() : !originalLayout()) return false;
                    if (!budget.consume(1) && !InventoryClickBudget.reserve(1)) return false;
                    executing = true;
                    try {
                        if (!restoring) swapped = true;
                        client.interactionManager.clickSlot(player.playerScreenHandler.syncId, menuSlot, hotbar,
                                SlotActionType.SWAP, player);
                        return restoring ? originalLayout() : matches();
                    } finally { executing = false; }
                }, ActionResource.INVENTORY).success();
            } finally { scheduler.release(owner, ActionResource.INVENTORY); }
        }

        private void finish() {
            budget.close();
            finished = true;
            temporarySwaps.remove(this);
        }
    }

    private static boolean matchesStack(ItemStack actual, ItemStack expected) {
        if (ItemStack.areEqual(actual, expected)) return true;
        if (!actual.isOf(Items.ELYTRA) || !expected.isOf(Items.ELYTRA)) return false;
        var left = actual.copy();
        var right = expected.copy();
        left.remove(DataComponentTypes.DAMAGE);
        right.remove(DataComponentTypes.DAMAGE);
        return ItemStack.areEqual(left, right);
    }

    public boolean swapChest(ActionOwner owner, int inventorySlot) {
        CombatActionScheduler.requireClientThread();
        if (inventorySlot < 0 || inventorySlot >= 36 || reservedMenuSlot(6)
                || reservedMenuSlot(inventorySlot < 9 ? inventorySlot + 36 : inventorySlot)) return false;
        try {
            return scheduler.execute(owner, 10, 2, null, () -> {
                var client = MinecraftClient.getInstance();
                if (!safeEquipmentState(client) || executing) return false;
                var player = client.player;
                var menu = player.currentScreenHandler;
                var expectedChest = player.getInventory().getStack(inventorySlot).copy();
                var expectedInventory = player.getEquippedStack(EquipmentSlot.CHEST).copy();
                executing = true;
                try {
                    int source = inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
                    client.interactionManager.clickSlot(menu.syncId, source, 0, SlotActionType.PICKUP, player);
                    try {
                        client.interactionManager.clickSlot(menu.syncId, 6, 0, SlotActionType.PICKUP, player);
                    } finally {
                        client.interactionManager.clickSlot(menu.syncId, source, 0, SlotActionType.PICKUP, player);
                    }
                    return menu.getCursorStack().isEmpty()
                            && ItemStack.areEqual(player.getEquippedStack(EquipmentSlot.CHEST), expectedChest)
                            && ItemStack.areEqual(player.getInventory().getStack(inventorySlot), expectedInventory);
                } finally { executing = false; }
            }, ActionResource.INVENTORY).success();
        } finally { scheduler.release(owner, ActionResource.INVENTORY); }
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

    public boolean beginToolLease(ActionOwner owner, int priority, int slot, SwapMode mode, int holdTicks) {
        if (slot < 0 || slot > 8 || mode == null) return false;
        var player = MinecraftClient.getInstance().player;
        if (player == null || (mode == SwapMode.NONE && player.getInventory().getSelectedSlot() != slot)) return false;
        return requestLease(mode == SwapMode.SILENT ? NativeManagerBridge.SLOT_BEGIN_SILENT : NativeManagerBridge.SLOT_BEGIN,
                owner, priority, slot, holdTicks, mode == SwapMode.SILENT);
    }

    private boolean requestLease(int event, ActionOwner owner, int priority, int slot, int holdTicks) {
        return requestLease(event, owner, priority, slot, holdTicks, true);
    }

    private boolean requestLease(int event, ActionOwner owner, int priority, int slot, int holdTicks, boolean silent) {
        CombatActionScheduler.requireClientThread();
        Objects.requireNonNull(owner);
        var player = MinecraftClient.getInstance().player;
        if (player == null || MinecraftClient.getInstance().getNetworkHandler() == null) return false;
        if (!scheduler.reserve(owner, priority, holdTicks, ActionResource.INVENTORY)) return false;
        owners.put(owner.id(), owner);
        var result = bridge.slotRequest(event, owner, priority, slot, player.getInventory().getSelectedSlot(),
                serverState.selectedServerSlot(), holdTicks, silent);
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

    public boolean withVisibleSlot(ActionOwner owner, int priority, int slot, BooleanSupplier action) {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(action);
        return scheduler.execute(owner, priority, 1, null,
                () -> executeSlot(owner, priority, slot, SwapMode.CLIENT, 0, 1, action, true),
                ActionResource.INVENTORY).success();
    }

    private boolean executeSlot(ActionOwner owner, int priority, int slot, SwapMode mode,
            int restoreDelay, int holdTicks, BooleanSupplier action) {
        return executeSlot(owner, priority, slot, mode, restoreDelay, holdTicks, action, false);
    }

    private boolean executeSlot(ActionOwner owner, int priority, int slot, SwapMode mode,
            int restoreDelay, int holdTicks, BooleanSupplier action, boolean vanillaSelection) {
        var player = MinecraftClient.getInstance().player;
        if (player == null || slot < 0 || slot > 8 || executing) return false;
        int selected = player.getInventory().getSelectedSlot();
        if (mode == SwapMode.NONE) return selected == slot && action.getAsBoolean();
        owners.put(owner.id(), owner);
        var begin = bridge.slotRequest(NativeManagerBridge.SLOT_BEGIN, owner, priority, slot, selected,
                vanillaSelection ? selected : serverState.selectedServerSlot(), holdTicks, mode == SwapMode.SILENT);
        boolean success = false;
        boolean restored = false;
        executing = true;
        try {
            if (apply(begin, owner, vanillaSelection) && begin.accepted()) success = action.getAsBoolean();
        } finally {
            try {
                if (begin.accepted()) {
                    var finish = vanillaSelection
                            ? bridge.finishVisibleAction(owner, selected, player.getInventory().getSelectedSlot())
                            : bridge.finishSlot(owner, restoreDelay, success, player.getInventory().getSelectedSlot());
                    restored = apply(finish, owner, vanillaSelection) && finish.accepted();
                }
            } finally { executing = false; }
        }
        return success && (!vanillaSelection || restored);
    }

    public void release(ActionOwner owner) {
        CombatActionScheduler.requireClientThread();
        for (var swap : List.copyOf(temporarySwaps).reversed()) if (swap.owner == owner) swap.requestRestore();
        Objects.requireNonNull(owner);
        var player = MinecraftClient.getInstance().player;
        if (bridge.used() && player != null) apply(bridge.call(NativeManagerBridge.SLOT_RELEASE,
                owner, player.getInventory().getSelectedSlot()), owner);
        if (!bridge.used() || !isOwnedBy(owner)) scheduler.release(owner, ActionResource.INVENTORY);
    }

    void tick() {
        for (var swap : List.copyOf(temporarySwaps).reversed()) if (swap.restoreRequested) swap.restore();
        var player = MinecraftClient.getInstance().player;
        if (!bridge.used() || player == null) return;
        apply(bridge.call(NativeManagerBridge.SLOT_TICK, null, player.getInventory().getSelectedSlot()), restoreOwner);
    }

    void reset() {
        // restore() keeps unfinished work queued while its player and world still exist.
        for (var swap : List.copyOf(temporarySwaps).reversed()) swap.requestRestore();
        owners.clear();
        executing = false;
    }

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
        return apply(commands, owner, false);
    }

    private boolean apply(NativeManagerBridge.Result commands, ActionOwner owner, boolean vanillaSelection) {
        try {
            if (commands.restoreSlot() >= 0) {
                ActionOwner restoring = owners.getOrDefault(commands.restoreOwnerId(), owner);
                if (!select(restoring, commands.restoreSlot(), commands.restoreClient(), commands.restoreTransaction(), PacketPhase.RESTORE, vanillaSelection)) {
                    fail(restoring, commands.restoreTransaction());
                    if (commands.selectSlot() >= 0) fail(owner, commands.slot().transactionId());
                    return false;
                }
            }
            if (commands.selectSlot() >= 0 && !select(owner, commands.selectSlot(), commands.selectClient(),
                    commands.slot().transactionId(), PacketPhase.SWAP, vanillaSelection)) {
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

    private boolean select(ActionOwner owner, int slot, boolean clientSelection, long transaction, PacketPhase phase, boolean vanillaSelection) {
        var player = MinecraftClient.getInstance().player;
        if (player == null) return false;
        if (clientSelection && vanillaSelection) player.getInventory().setSelectedSlot(slot);
        try {
            if (!network.sendWithSlotTransaction(owner, new UpdateSelectedSlotC2SPacket(slot), transaction, phase)) return false;
        } catch (RuntimeException | Error error) {
            fail(owner, transaction);
            throw error;
        }
        if (clientSelection && !vanillaSelection) {
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
