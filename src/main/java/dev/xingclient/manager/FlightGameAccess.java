package dev.xingclient.manager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

/** Minecraft operations called by the native flight utilities. Author: uint32. */
public final class FlightGameAccess {
    private final ClientManagers managers;
    private final ActionOwner owner;
    private long lastRocketFailure;
    private InventoryManager.TemporarySwap stagedElytra;
    private InventoryManager.TemporarySwap chestCycle;

    public FlightGameAccess(ClientManagers managers, ActionOwner owner) {
        this.managers = managers;
        this.owner = owner;
    }

    public static boolean safeInventoryState(MinecraftClient client) {
        var player = client.player;
        return player != null && client.world != null && client.interactionManager != null
                && player.isAlive() && !player.isDead() && client.currentScreen == null
                && client.getNetworkHandler() != null && client.getNetworkHandler().getConnection().isOpen()
                && player.currentScreenHandler == player.playerScreenHandler
                && player.currentScreenHandler.syncId == 0 && player.currentScreenHandler.getCursorStack().isEmpty();
    }

    public boolean swapChest(int inventorySlot, int menuSlot) {
        if (inventorySlot < 0 || inventorySlot >= 36
                || menuSlot != (inventorySlot < 9 ? inventorySlot + 36 : inventorySlot)) return false;
        return managers.inventory.swapChest(owner, inventorySlot);
    }

    public boolean inventoryReady() {
        if (managers.inventory.hasPendingRestore()) return false;
        if (chestCycle != null) {
            if (!chestCycle.restore()) return false;
            chestCycle = null;
        }
        return true;
    }

    public boolean stageElytra(int source, int hotbar) {
        var client = MinecraftClient.getInstance();
        if (source < 9 || source >= 36 || hotbar < 0 || hotbar >= 9
                || stagedElytra != null || chestCycle != null || !safeInventoryState(client)
                || !client.player.getInventory().getStack(source).isOf(Items.ELYTRA)) return false;
        stagedElytra = managers.inventory.temporarySwap(owner, source, hotbar);
        return stagedElytra != null;
    }

    public boolean stagedElytraMatches() { return stagedElytra != null && stagedElytra.matches(); }

    public boolean restoreFlightEquipment() {
        if (chestCycle != null) {
            if (!chestCycle.restore()) return false;
            chestCycle = null;
        }
        if (stagedElytra != null) {
            if (!stagedElytra.restore()) return false;
            stagedElytra = null;
        }
        return true;
    }

    public boolean startGlidingWithElytra(int hotbar) {
        var client = MinecraftClient.getInstance();
        if (!safeInventoryState(client) || hotbar < 0 || hotbar >= 9
                || !client.player.getInventory().getStack(hotbar).isOf(Items.ELYTRA)) return false;
        if (chestCycle != null) {
            if (!chestCycle.restore()) return false;
            chestCycle = null;
        }
        chestCycle = managers.inventory.temporarySwap(owner, 6, hotbar);
        if (chestCycle == null) return false;
        boolean started;
        boolean restored;
        try { started = startGliding(); }
        finally {
            restored = chestCycle.restore();
            if (restored) chestCycle = null;
        }
        return started && restored;
    }

    public boolean startGliding() {
        var client = MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null) return false;
        client.player.startGliding();
        return managers.network.send(owner,
                new ClientCommandC2SPacket(client.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
    }

    public void jump() {
        var player = MinecraftClient.getInstance().player;
        if (player != null) ((dev.xingclient.mixin.LivingEntityJumpInvoker) player).xing$jump();
    }

    public boolean useRocket(int inventorySlot, int menuSlot, boolean hotbar) {
        var client = MinecraftClient.getInstance();
        if (managers.inventory.hasPendingRestore() || client.player == null || client.interactionManager == null
                || inventorySlot < 0 || inventorySlot >= 36
                || hotbar != (inventorySlot < 9)
                || menuSlot != (inventorySlot < 9 ? inventorySlot + 36 : inventorySlot)) return false;
        if (hotbar) return managers.inventory.withVisibleSlot(owner, 10, inventorySlot, this::interactRocket);
        if (!safeInventoryState(client)) return false;
        try {
            return managers.scheduler.execute(owner, 10, 2, null,
                    () -> clickRocketSwap(client, menuSlot), ActionResource.INVENTORY, ActionResource.MAIN_HAND).success();
        } finally { managers.scheduler.release(owner, ActionResource.INVENTORY, ActionResource.MAIN_HAND); }
    }

    private boolean clickRocketSwap(MinecraftClient client, int menuSlot) {
        if (!InventoryClickBudget.reserve(2)) return false;
        var player = client.player;
        int selected = player.getInventory().getSelectedSlot();
        int id = player.playerScreenHandler.syncId;
        client.interactionManager.clickSlot(id, menuSlot, selected, SlotActionType.SWAP, player);
        try { return interactRocket(); }
        finally { client.interactionManager.clickSlot(id, menuSlot, selected, SlotActionType.SWAP, player); }
    }

    private boolean interactRocket() {
        var client = MinecraftClient.getInstance();
        var player = client.player;
        if (player == null || client.interactionManager == null
                || !player.getMainHandStack().isOf(Items.FIREWORK_ROCKET)) return false;
        var result = client.interactionManager.interactItem(player, Hand.MAIN_HAND);
        player.swingHand(Hand.MAIN_HAND);
        if (!result.isAccepted()) {
            long now = System.nanoTime();
            if (now - lastRocketFailure >= 5_000_000_000L) {
                org.slf4j.LoggerFactory.getLogger("xingclient-flight").warn(
                    "Rocket interaction rejected: result={}, gliding={}, grounded={}",
                    result, player.isGliding(), player.isOnGround());
                lastRocketFailure = now;
            }
        }
        return result.isAccepted();
    }

    public void requestRotation(float yaw, float pitch) { managers.rotations.requestMovement(owner, 80, yaw, pitch, 2); }
    public boolean requestClientRotation(float yaw, float pitch) {
        return managers.rotations.requestClientMovement(owner, 80, yaw, pitch, 2);
    }
    public float currentYaw() {
        var rotation = managers.rotations.serverRotation();
        return rotation.known() ? rotation.yaw() : MinecraftClient.getInstance().player.getYaw();
    }
    public float currentPitch() {
        var rotation = managers.rotations.serverRotation();
        return rotation.known() ? rotation.pitch() : MinecraftClient.getInstance().player.getPitch();
    }
    public void clearRotation() { managers.rotations.release(owner); }
    public void setVelocity(double x, double y, double z) {
        var player = MinecraftClient.getInstance().player;
        if (player != null) player.setVelocity(x, y, z);
    }
}
