package dev.xingclient.manager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

/** Minecraft operations called by the native flight utilities. Author: uint32. */
public final class FlightGameAccess {
    private final ClientManagers managers;
    private final ActionOwner owner;

    public FlightGameAccess(ClientManagers managers, ActionOwner owner) {
        this.managers = managers;
        this.owner = owner;
    }

    public static boolean safeInventoryState(MinecraftClient client) {
        var player = client.player;
        return player != null && client.world != null && client.interactionManager != null
                && player.isAlive() && !player.isDead() && client.currentScreen == null
                && player.currentScreenHandler instanceof PlayerScreenHandler
                && player.currentScreenHandler.syncId == 0 && player.currentScreenHandler.getCursorStack().isEmpty();
    }

    public boolean swapChest(int inventorySlot, int menuSlot) {
        var client = MinecraftClient.getInstance();
        if (!safeInventoryState(client) || inventorySlot < 0 || inventorySlot >= 36) return false;
        try {
            return managers.scheduler.execute(owner, 10, 2, null,
                    () -> clickChestSwap(client, inventorySlot, menuSlot), ActionResource.INVENTORY).success();
        } finally { managers.scheduler.release(owner, ActionResource.INVENTORY); }
    }

    private boolean clickChestSwap(MinecraftClient client, int inventorySlot, int menuSlot) {
        var player = client.player;
        ItemStack expectedChest = player.getInventory().getStack(inventorySlot).copy();
        ItemStack expectedInventory = player.getEquippedStack(EquipmentSlot.CHEST).copy();
        if (expectedChest.isEmpty()) return false;
        int id = player.currentScreenHandler.syncId;
        client.interactionManager.clickSlot(id, menuSlot, 0, SlotActionType.PICKUP, player);
        client.interactionManager.clickSlot(id, 6, 0, SlotActionType.PICKUP, player);
        client.interactionManager.clickSlot(id, menuSlot, 0, SlotActionType.PICKUP, player);
        return player.currentScreenHandler.getCursorStack().isEmpty()
                && ItemStack.areItemsAndComponentsEqual(player.getEquippedStack(EquipmentSlot.CHEST), expectedChest)
                && ItemStack.areItemsAndComponentsEqual(player.getInventory().getStack(inventorySlot), expectedInventory);
    }

    public boolean startGliding() {
        var client = MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null) return false;
        client.player.startGliding();
        return managers.network.send(owner,
                new ClientCommandC2SPacket(client.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
    }

    public boolean useRocket(int inventorySlot, int menuSlot, boolean hotbar) {
        var client = MinecraftClient.getInstance();
        if (client.player == null || client.interactionManager == null || inventorySlot < 0 || inventorySlot >= 36) return false;
        if (hotbar) return managers.inventory.withSlot(owner, 10, inventorySlot, InventoryManager.SwapMode.CLIENT, 0, this::interactRocket);
        if (!safeInventoryState(client)) return false;
        try {
            return managers.scheduler.execute(owner, 10, 2, null,
                    () -> clickRocketSwap(client, menuSlot), ActionResource.INVENTORY, ActionResource.MAIN_HAND).success();
        } finally { managers.scheduler.release(owner, ActionResource.INVENTORY, ActionResource.MAIN_HAND); }
    }

    private boolean clickRocketSwap(MinecraftClient client, int menuSlot) {
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
        if (player == null || client.interactionManager == null || !player.getMainHandStack().isOf(Items.FIREWORK_ROCKET)) return false;
        var result = client.interactionManager.interactItem(player, Hand.MAIN_HAND);
        player.swingHand(Hand.MAIN_HAND);
        return result.isAccepted();
    }

    public void requestRotation(float yaw, float pitch) { managers.rotations.request(owner, 80, yaw, pitch, 2); }
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
