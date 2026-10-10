package dev.xingclient.module;

import dev.xingclient.XingClient;
import dev.xingclient.event.ClientTickEvent;
import dev.xingclient.event.EventBus;
import dev.xingclient.event.PacketReceiveEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Minecraft lifecycle and packet adapter for native Velocity. Author: uint32. */
public final class NativeVelocityModule extends Module {
    private final EventBus events;
    private EventBus.Subscription subscription;
    public NativeVelocityModule(EventBus events) {
        super("velocity", "Velocity", ModuleCategory.COMBAT);
        this.events = events;
    }
    @Override
    protected void onEnable() {
        XingClient.INSTANCE.managers.movementState.resetVelocity();
        subscription = events.subscribe(ClientTickEvent.class, this::tick);
    }
    @Override
    protected void onDisable() {
        if (subscription != null) { subscription.close(); subscription = null; }
        XingClient.INSTANCE.managers.movementState.resetVelocity();
    }
    public void receive(PacketReceiveEvent event) {
        var result = XingClient.INSTANCE.managers.movementState.receive(event.packet());
        if (result.cancel()) event.cancel();
        var player = MinecraftClient.getInstance().player;
        if (result.apply() && player != null) {
            var movement = result.movement();
            player.setVelocityClient(movement.x, movement.y, movement.z);
            player.setVelocity(movement);
        }
    }
    private void tick(ClientTickEvent event) {
        if (event.phase() != ClientTickEvent.Phase.END) return;
        var result = XingClient.INSTANCE.managers.movementState.tickVelocity();
        var player = MinecraftClient.getInstance().player;
        if (!result.confirm() || player == null) return;
        var position = result.movement();
        var network = XingClient.INSTANCE.managers.network;
        if (result.positionOnly()) {
            network.sendQuiet(actionOwner, new PlayerMoveC2SPacket.PositionAndOnGround(position.x, position.y, position.z,
                    player.isOnGround(), player.horizontalCollision));
        } else {
            network.sendQuiet(actionOwner, new PlayerMoveC2SPacket.Full(position.x, position.y, position.z,
                    result.yaw(), result.pitch(), player.isOnGround(), player.horizontalCollision));
        }
        network.send(actionOwner, new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                new BlockPos(result.blockX(), result.blockY(), result.blockZ()), Direction.DOWN));
    }
    @Override
    public String status() {
        if (!isEnabled()) return "Disabled / uint32";
        return XingClient.INSTANCE.managers.movementState.confirmationCircuitOpen()
                ? "Confirmation paused after correction" : "Native Velocity / uint32";
    }
}
