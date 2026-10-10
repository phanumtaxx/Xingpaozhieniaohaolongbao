package dev.xingclient.manager;

import dev.xingclient.event.EventBus;
import dev.xingclient.event.PacketCommitEvent;
import io.netty.buffer.Unpooled;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.util.math.Vec3d;

/** Packet and item conversion for native server state. Author: uint32. */
public final class ServerStateTracker {
    public record State(int selectedServerSlot, int containerId, int containerRevision,
            long lastPacketCommitId, int crystalSpawnCount) {}
    public record CrystalSpawn(int entityId, Vec3d position, long receivedMillis) {}

    private final NativeWorldStateBridge bridge;

    ServerStateTracker(NativeWorldStateBridge bridge, EventBus events) {
        this.bridge = bridge;
        events.subscribe(PacketCommitEvent.class, this::handleCommitted);
    }

    public State state() {
        ByteBuffer reply = bridge.call(NativeWorldStateBridge.Event.READ, ignored -> {});
        return new State(reply.getInt(4), reply.getInt(8), reply.getInt(12), reply.getLong(16), reply.getInt(32));
    }

    public int selectedServerSlot() { return state().selectedServerSlot(); }
    public int containerId() { return state().containerId(); }
    public int containerRevision() { return state().containerRevision(); }
    public long lastPacketCommitId() { return state().lastPacketCommitId(); }
    public ItemStack mainHand() { return hand(0); }
    public ItemStack offHand() { return hand(1); }

    public List<CrystalSpawn> crystalSpawns() {
        int count = state().crystalSpawnCount();
        List<CrystalSpawn> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int spawnIndex = index;
            ByteBuffer reply = bridge.call(NativeWorldStateBridge.Event.READ_CRYSTAL, input -> input.putInt(4, spawnIndex));
            if (reply.getInt(0) != 0) result.add(new CrystalSpawn(reply.getInt(100),
                    new Vec3d(reply.getDouble(112), reply.getDouble(120), reply.getDouble(128)), reply.getLong(104)));
        }
        return List.copyOf(result);
    }

    public void handleSlotUpdate(ScreenHandlerSlotUpdateS2CPacket packet) {
        bridge.call(NativeWorldStateBridge.Event.CONTAINER_SLOT, input -> {
            input.putInt(4, packet.getSlot());
            input.putInt(8, packet.getSyncId());
            input.putInt(12, packet.getRevision());
        }, encode(packet.getStack()), new byte[0]);
    }

    public void handleInventory(InventoryS2CPacket packet) {
        List<byte[]> items = packet.contents().stream().map(ServerStateTracker::encode).toList();
        int size = 0;
        for (byte[] item : items) size = Math.addExact(size, Math.addExact(Integer.BYTES, item.length));
        ByteBuffer payload = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        for (byte[] item : items) payload.putInt(item.length).put(item);
        bridge.call(NativeWorldStateBridge.Event.CONTAINER_CONTENT, input -> {
            input.putInt(8, packet.syncId());
            input.putInt(12, packet.revision());
        }, payload.array(), new byte[0]);
    }

    public void handleCrystalSpawn(int entityId, Vec3d position) {
        bridge.call(NativeWorldStateBridge.Event.CRYSTAL_SPAWN, input -> {
            input.putInt(100, entityId);
            input.putLong(64, System.currentTimeMillis());
            input.putDouble(112, position.x);
            input.putDouble(120, position.y);
            input.putDouble(128, position.z);
        });
    }

    void tick(MinecraftClient client) {
        State state = state();
        if (client.player != null && state.containerRevision() < 0) {
            int slot = state.selectedServerSlot() < 0 ? client.player.getInventory().getSelectedSlot() : state.selectedServerSlot();
            bridge.call(NativeWorldStateBridge.Event.REFRESH_HANDS, input -> input.putInt(4, slot),
                    encode(client.player.getInventory().getStack(slot)), encode(client.player.getOffHandStack()));
        }
        bridge.call(NativeWorldStateBridge.Event.TICK, input -> input.putLong(64, System.currentTimeMillis()));
    }

    private void handleCommitted(PacketCommitEvent event) {
        MinecraftClient client = MinecraftClient.getInstance();
        int slot = event.packet() instanceof UpdateSelectedSlotC2SPacket selected ? selected.getSelectedSlot() : -1;
        byte[] item = slot >= 0 && slot < 9 && client.player != null
                ? encode(client.player.getInventory().getStack(slot)) : new byte[0];
        bridge.call(NativeWorldStateBridge.Event.COMMIT, input -> {
            input.putInt(4, slot);
            input.putLong(16, event.commitId());
        }, item, new byte[0]);
    }

    private ItemStack hand(int index) {
        ByteBuffer reply = bridge.call(NativeWorldStateBridge.Event.READ_HAND, input -> input.putInt(4, index));
        int size = reply.getInt(index == 0 ? 24 : 28);
        if (size == 0) return ItemStack.EMPTY;
        byte[] encoded = new byte[size];
        reply.get(NativeWorldStateBridge.HEADER_BYTES, encoded);
        RegistryByteBuf buffer = registryBuffer(encoded);
        try { return ItemStack.OPTIONAL_PACKET_CODEC.decode(buffer); }
        finally { buffer.release(); }
    }

    private static byte[] encode(ItemStack stack) {
        CombatActionScheduler.requireClientThread();
        RegistryByteBuf buffer = registryBuffer(null);
        try {
            ItemStack.OPTIONAL_PACKET_CODEC.encode(buffer, stack);
            byte[] result = new byte[buffer.readableBytes()];
            buffer.readBytes(result);
            return result;
        } finally { buffer.release(); }
    }

    private static RegistryByteBuf registryBuffer(byte[] encoded) {
        var connection = MinecraftClient.getInstance().getNetworkHandler();
        if (connection == null) throw new IllegalStateException("Item snapshots require an active world connection");
        return new RegistryByteBuf(encoded == null ? Unpooled.buffer() : Unpooled.wrappedBuffer(encoded), connection.getRegistryManager());
    }
}
