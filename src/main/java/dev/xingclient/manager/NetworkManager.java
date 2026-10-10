package dev.xingclient.manager;

import dev.xingclient.mixin.ClientWorldAccessor;
import dev.xingclient.event.EventBus;
import dev.xingclient.event.PacketCommitEvent;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.function.ToLongFunction;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;

/** Minecraft transport adapter for module-owned packets and prediction sequences. */
public final class NetworkManager {
    private static final long PENDING_TTL_NANOS = 1_000_000_000L;
    private record PendingPacket(ActionOwner owner, long actionId, int sequence,
            long epoch, ClientConnection connection, long queuedAt, long slotTransaction,
            InventoryManager.PacketPhase slotPhase) {}
    private record CommitNotification(PacketCommitEvent event, long epoch) {}
    private final Map<Packet<?>, PendingPacket> pending = new IdentityHashMap<>();
    private final ConcurrentLinkedQueue<CommitNotification> notifications = new ConcurrentLinkedQueue<>();
    private final NativeCombatBridge bridge;
    private final CombatActionScheduler scheduler;
    private final EventBus events;
    private ToLongFunction<ActionOwner> slotTransactions = ignored -> 0;
    private int quietDepth;

    NetworkManager(NativeCombatBridge bridge, CombatActionScheduler scheduler, EventBus events) {
        this.bridge = bridge;
        this.scheduler = scheduler;
        this.events = events;
    }

    public boolean send(ActionOwner owner, Packet<?> packet) {
        CombatActionScheduler.requireClientThread();
        long transaction = slotTransactions.applyAsLong(owner);
        return sendWithSlotTransaction(owner, packet, transaction, slotPhase(packet, transaction));
    }

    public boolean sendQuiet(ActionOwner owner, Packet<?> packet) {
        CombatActionScheduler.requireClientThread();
        ++quietDepth;
        try { return send(owner, packet); }
        finally { --quietDepth; }
    }

    public boolean isQuietSend() { return MinecraftClient.getInstance().isOnThread() && quietDepth > 0; }

    void slotTransactions(ToLongFunction<ActionOwner> resolver) { slotTransactions = Objects.requireNonNull(resolver); }

    boolean sendWithSlotTransaction(ActionOwner owner, Packet<?> packet, long transaction, InventoryManager.PacketPhase phase) {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(packet);
        CombatActionScheduler.requireClientThread();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.getNetworkHandler() == null) return false;
        ClientConnection connection = client.getNetworkHandler().getConnection();
        if (!connection.isOpen()) return false;
        track(packet, owner, connection, transaction, phase);
        try {
            client.getNetworkHandler().sendPacket(packet);
            return true;
        } catch (RuntimeException | Error error) {
            synchronized (pending) { pending.remove(packet); }
            throw error;
        }
    }

    public boolean sendSequenced(ActionOwner owner, IntFunction<Packet<?>> factory) {
        CombatActionScheduler.requireClientThread();
        Objects.requireNonNull(owner);
        Objects.requireNonNull(factory);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.getNetworkHandler() == null) return false;
        try (var prediction = ((ClientWorldAccessor) client.world)
                .xing$getPendingUpdateManager().incrementSequence()) {
            return send(owner, factory.apply(prediction.getSequence()));
        }
    }

    public ActionOwner ownerOf(Packet<?> packet) {
        synchronized (pending) {
            PendingPacket tagged = pending.get(packet);
            return tagged == null ? null : tagged.owner();
        }
    }

    public long latestCommitId(ActionOwner owner) {
        return bridge.call(NativeCombatBridge.Event.LATEST_COMMIT, Objects.requireNonNull(owner)).commitId();
    }

    public int latestSequence(ActionOwner owner) {
        return bridge.call(NativeCombatBridge.Event.LATEST_COMMIT, Objects.requireNonNull(owner)).sequence();
    }

    public int latencyMillis() {
        CombatActionScheduler.requireClientThread();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null) return 0;
        var entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        return entry == null ? 0 : Math.max(0, entry.getLatency());
    }

    public Packet<?> trackOutgoing(ClientConnection connection, Packet<?> original, Packet<?> outgoing) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!client.isOnThread() || client.getNetworkHandler() == null
                || connection != client.getNetworkHandler().getConnection()) return outgoing;
        PendingPacket tagged;
        synchronized (pending) {
            tagged = original == outgoing ? pending.get(original) : pending.remove(original);
            if (tagged != null) pending.put(outgoing, tagged);
        }
        if (tagged == null && (scheduler.currentOwner() != null || bridge.used())) {
            ActionOwner owner = scheduler.currentOwner();
            long transaction = slotTransactions.applyAsLong(owner);
            track(outgoing, owner, connection, transaction, slotPhase(outgoing, transaction));
        }
        return outgoing;
    }

    public void committed(ClientConnection connection, Packet<?> packet) {
        PendingPacket tagged;
        synchronized (pending) { tagged = pending.remove(packet); }
        if (tagged == null || tagged.connection() != connection) return;
        var reply = bridge.call(NativeCombatBridge.Event.PACKET_COMMIT,
                NativeCombatBridge.Request.empty(tagged.owner()).withId(tagged.actionId()),
                0, 0, tagged.sequence(), tagged.epoch());
        if (reply.commitId() == 0) return;
        PacketCommitEvent notification = new PacketCommitEvent(packet, tagged.owner(),
                tagged.actionId(), reply.commitId(), tagged.sequence(), tagged.slotTransaction(), tagged.slotPhase());
        if (MinecraftClient.getInstance().isOnThread()) events.post(notification);
        else notifications.add(new CommitNotification(notification, tagged.epoch()));
    }

    void tick() {
        CommitNotification notification;
        while ((notification = notifications.poll()) != null) {
            if (notification.epoch() == bridge.epoch()) events.post(notification.event());
        }
        long now = System.nanoTime();
        synchronized (pending) {
            pending.values().removeIf(tagged -> now - tagged.queuedAt() > PENDING_TTL_NANOS);
        }
    }

    void reset() {
        synchronized (pending) { pending.clear(); }
        notifications.clear();
    }

    private void track(Packet<?> packet, ActionOwner owner, ClientConnection connection, long transaction,
            InventoryManager.PacketPhase phase) {
        if (!bridge.used()) bridge.call(NativeCombatBridge.Event.LATEST_COMMIT, owner);
        PendingPacket tagged = new PendingPacket(owner, scheduler.currentActionId(owner),
                sequence(packet), bridge.epoch(), connection, System.nanoTime(), transaction, phase);
        synchronized (pending) { pending.put(packet, tagged); }
    }

    private static int sequence(Packet<?> packet) {
        if (packet instanceof PlayerInteractBlockC2SPacket interaction) return interaction.getSequence();
        if (packet instanceof PlayerInteractItemC2SPacket interaction) return interaction.getSequence();
        if (packet instanceof PlayerActionC2SPacket action) return action.getSequence();
        return -1;
    }

    private static InventoryManager.PacketPhase slotPhase(Packet<?> packet, long transaction) {
        return transaction == 0 || packet instanceof UpdateSelectedSlotC2SPacket
                ? InventoryManager.PacketPhase.UNTRACKED : InventoryManager.PacketPhase.ACTION;
    }
}
