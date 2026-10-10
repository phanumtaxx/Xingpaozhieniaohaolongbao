package dev.xingclient.event;

import java.util.Objects;
import net.minecraft.network.packet.Packet;

/** Raised once before an incoming play packet is applied on the client thread. */
public final class PacketReceiveEvent implements ClientEvent {
    private final Packet<?> packet;
    private boolean cancelled;
    public PacketReceiveEvent(Packet<?> packet) { this.packet = Objects.requireNonNull(packet); }
    public Packet<?> packet() { return packet; }
    public boolean isCancelled() { return cancelled; }
    public void cancel() { cancelled = true; }
}
