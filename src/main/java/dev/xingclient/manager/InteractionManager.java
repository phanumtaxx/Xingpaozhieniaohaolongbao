package dev.xingclient.manager;

import java.util.EnumSet;
import java.util.function.Predicate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;

/** Shared entity attacks and block interactions for every module. */
public final class InteractionManager {
    public enum Transport { PACKET, VANILLA }

    private final NetworkManager network;
    private final InventoryManager inventory;
    private final CombatActionScheduler scheduler;
    private final RotationManager rotations;

    InteractionManager(NetworkManager network, InventoryManager inventory, CombatActionScheduler scheduler,
            RotationManager rotations) {
        this.network = network;
        this.inventory = inventory;
        this.scheduler = scheduler;
        this.rotations = rotations;
    }

    public boolean attack(ActionOwner owner, Entity entity, Transport transport, boolean swing) {
        return attack(owner, 60, entity, transport, swing, CombatRateLimiter.Channel.ATTACK);
    }

    public boolean attackImmediate(ActionOwner owner, int priority, Entity entity, Transport transport, boolean swing) {
        return attack(owner, priority, entity, transport, swing, null);
    }

    private boolean attack(ActionOwner owner, int priority, Entity entity, Transport transport, boolean swing,
            CombatRateLimiter.Channel channel) {
        return scheduler.execute(owner, priority, 1, channel, () -> sendAttack(owner, entity, transport, swing),
                resources(owner, Hand.MAIN_HAND, ActionResource.ATTACK_PACKET, false)).success();
    }

    private boolean sendAttack(ActionOwner owner, Entity entity, Transport transport, boolean swing) {
        var client = MinecraftClient.getInstance();
        var player = client.player;
        if (player == null || client.interactionManager == null || !entity.isAlive()) return false;
        if (transport == Transport.PACKET) {
            if (!network.send(owner, PlayerInteractEntityC2SPacket.attack(entity, player.isSneaking()))) return false;
        } else {
            client.interactionManager.attackEntity(player, entity);
        }
        if (swing) swing(owner, Hand.MAIN_HAND, transport);
        return true;
    }

    public boolean useBlock(ActionOwner owner, int priority, BlockHitResult hit,
            Predicate<ItemStack> item, InventoryManager.SwapMode swap, int restoreDelay,
            Transport transport, boolean swing) {
        var player = MinecraftClient.getInstance().player;
        if (player == null || hit == null) return false;
        if (item.test(player.getOffHandStack())) {
            return scheduler.execute(owner, priority, 1, CombatRateLimiter.Channel.BLOCK_PLACE,
                    () -> interact(owner, Hand.OFF_HAND, hit, transport, swing),
                    resources(owner, Hand.OFF_HAND, ActionResource.BLOCK_PLACE_PACKET, false)).success();
        }
        int slot = inventory.findHotbar(item);
        if (slot < 0 || (swap == InventoryManager.SwapMode.SILENT && transport == Transport.VANILLA)) return false;
        boolean needsInventory = swap != InventoryManager.SwapMode.NONE
                && player.getInventory().getSelectedSlot() != slot;
        ActionResource[] resources = resources(owner, Hand.MAIN_HAND, ActionResource.BLOCK_PLACE_PACKET, needsInventory);
        return scheduler.execute(owner, priority, 1, CombatRateLimiter.Channel.BLOCK_PLACE,
                () -> inventory.withSlot(owner, priority, slot, swap, restoreDelay,
                        () -> interact(owner, Hand.MAIN_HAND, hit, transport, swing)), resources).success();
    }

    private boolean interact(ActionOwner owner, Hand hand, BlockHitResult hit, Transport transport, boolean swing) {
        var client = MinecraftClient.getInstance();
        if (client.player == null || client.interactionManager == null) return false;
        boolean success = transport == Transport.PACKET
                ? network.sendSequenced(owner, sequence -> new PlayerInteractBlockC2SPacket(hand, hit, sequence))
                : client.interactionManager.interactBlock(client.player, hand, hit).isAccepted();
        if (success && swing) swing(owner, hand, transport);
        return success;
    }

    private void swing(ActionOwner owner, Hand hand, Transport transport) {
        if (transport == Transport.PACKET) network.send(owner, new HandSwingC2SPacket(hand));
        else MinecraftClient.getInstance().player.swingHand(hand);
    }

    private ActionResource[] resources(ActionOwner owner, Hand hand, ActionResource packet, boolean inventorySwap) {
        var resources = EnumSet.of(hand == Hand.MAIN_HAND ? ActionResource.MAIN_HAND : ActionResource.OFF_HAND, packet);
        if (inventorySwap) resources.add(ActionResource.INVENTORY);
        if (rotations.isOwnedBy(owner)) resources.add(ActionResource.ROTATION);
        return resources.toArray(ActionResource[]::new);
    }
}
