package dev.xingclient.event;

import dev.xingclient.manager.ActionOwner;
import dev.xingclient.manager.InventoryManager;
import net.minecraft.network.packet.Packet;

public record PacketCommitEvent(Packet<?> packet, ActionOwner owner, long actionId, long commitId,
        int sequence, long slotTransaction, InventoryManager.PacketPhase slotPhase)
        implements ClientEvent {}
