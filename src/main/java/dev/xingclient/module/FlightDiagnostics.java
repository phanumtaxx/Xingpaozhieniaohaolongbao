package dev.xingclient.module;

import dev.xingclient.event.EventBus;
import dev.xingclient.event.PacketCommitEvent;
import dev.xingclient.event.PacketReceiveEvent;
import dev.xingclient.manager.ActionOwner;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerPosition;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Records transport evidence separately from the local Flight state. Author: uint32. */
final class FlightDiagnostics implements AutoCloseable {
    private static final int ENTITY_FLAGS_ID = 0;
    private static final int GLIDING_FLAG = 1 << 7;
    private static final Logger LOG = LoggerFactory.getLogger("xingclient-flight");
    private final ActionOwner owner;
    private final EventBus.Subscription commits, receives;
    private long reportedAt = System.nanoTime();
    private int starts, clicks, uses, movements, corrections, glideOn, glideOff;
    private final StringBuilder actions = new StringBuilder();
    private long attempt, attemptAt;
    private Boolean serverGliding;
    private long sequenceStartedAt, sequenceMovementCommit, sequenceMovementAt;
    private long lastMovementCommit, lastMovementAt;

    FlightDiagnostics(EventBus events, ActionOwner owner) {
        this.owner = owner;
        commits = events.subscribe(PacketCommitEvent.class, this::committed);
        receives = events.subscribe(PacketReceiveEvent.class, this::received);
    }

    private void committed(PacketCommitEvent event) {
        if (event.packet() instanceof PlayerMoveC2SPacket) {
            movements++;
            lastMovementCommit = event.commitId();
            lastMovementAt = event.committedAtNanos();
        }
        if (event.packet() instanceof UpdateSelectedSlotC2SPacket selected && !owner.equals(event.owner())) {
            recordAction(event, "select[" + selected.getSelectedSlot()
                    + ",origin=" + (event.owner() == null ? "unowned" : "other-module") + "]");
        }
        if (!owner.equals(event.owner())) return;
        if (event.packet() instanceof ClickSlotC2SPacket click) {
            clicks++;
            recordAction(event, "click[" + click.slot() + ",button=" + click.button() + ",action=" + click.actionType()
                    + ",revision=" + click.revision() + "]");
        } else if (event.packet() instanceof PlayerInteractItemC2SPacket) {
            uses++;
            recordAction(event, "useItem");
        } else if (event.packet() instanceof UpdateSelectedSlotC2SPacket selected) {
            recordAction(event, "select[" + selected.getSelectedSlot() + "]");
        } else if (event.packet() instanceof HandSwingC2SPacket) {
            recordAction(event, "swing");
        } else if (event.packet() instanceof ClientCommandC2SPacket command
                && command.getMode() == ClientCommandC2SPacket.Mode.START_FALL_FLYING) {
            starts++;
            attempt++;
            attemptAt = event.committedAtNanos();
            recordAction(event, "startGlide");
        }
    }

    private void received(PacketReceiveEvent event) {
        if (event.isCancelled()) return;
        var player = MinecraftClient.getInstance().player;
        if (player == null) return;
        if (event.packet() instanceof PlayerPositionLookS2CPacket correction) {
            corrections++;
            flushActions();
            LOG.info("Correction: attempt={}, elapsedMs={}, localGliding={}, serverGliding={}, grounded={}, chest={}, velocity={}",
                    attempt, elapsedSinceAttempt(), player.isGliding(), serverGliding, player.isOnGround(),
                    player.getEquippedStack(EquipmentSlot.CHEST).getItem(), player.getVelocity());
            var before = new PlayerPosition(player.getPos(), player.getVelocity(), player.getYaw(), player.getPitch());
            var target = PlayerPosition.apply(before, correction.change(), correction.relatives());
            var displacement = target.position().subtract(before.position());
            LOG.info("Correction position: teleportId={}, before={}, raw={}, flags={}, target={}, displacement={}, distance={}, targetVelocity={}",
                    correction.teleportId(), before.position(), correction.change().position(), correction.relatives(),
                    target.position(), displacement, displacement.length(), target.deltaMovement());
        }
        if (event.packet() instanceof EntityTrackerUpdateS2CPacket tracker && tracker.id() == player.getId()) {
            for (var value : tracker.trackedValues()) {
                if (value.id() == ENTITY_FLAGS_ID && value.value() instanceof Byte flags) {
                    boolean gliding = (flags & GLIDING_FLAG) != 0;
                    if (gliding) glideOn++;
                    else glideOff++;
                    if (serverGliding == null || serverGliding != gliding) {
                        flushActions();
                        LOG.info("Server glide flag: attempt={}, elapsedMs={}, gliding={}",
                                attempt, elapsedSinceAttempt(), gliding);
                    }
                    serverGliding = gliding;
                }
            }
        }
    }

    private void recordAction(PacketCommitEvent event, String action) {
        if (actions.isEmpty()) {
            sequenceStartedAt = event.committedAtNanos();
            sequenceMovementCommit = lastMovementCommit;
            sequenceMovementAt = lastMovementAt;
        } else actions.append(" -> ");
        actions.append(action).append("#").append(event.commitId())
                .append("@+").append((event.committedAtNanos() - sequenceStartedAt) / 1_000).append("us");
    }

    private long elapsedSinceAttempt() {
        return attemptAt == 0 ? -1 : (System.nanoTime() - attemptAt) / 1_000_000;
    }

    private void flushActions() {
        if (actions.isEmpty()) return;
        var player = MinecraftClient.getInstance().player;
        LOG.info("Flight sends: attempt={}, observedTick={}, previousMoveCommit={}, moveToActionsUs={}, sequence={}",
                attempt, player == null ? -1 : player.age, sequenceMovementCommit,
                sequenceMovementAt == 0 ? -1 : (sequenceStartedAt - sequenceMovementAt) / 1_000, actions);
        actions.setLength(0);
    }

    void tick() {
        flushActions();
        long now = System.nanoTime();
        if (now - reportedAt < 5_000_000_000L) return;
        var player = MinecraftClient.getInstance().player;
        LOG.info("Transport over {} ms: starts={}, clicks={}, itemUses={}, movement={}, corrections={}, serverGlideOn={}, serverGlideOff={}, chest={}",
                (now - reportedAt) / 1_000_000, starts, clicks, uses, movements, corrections, glideOn, glideOff,
                player == null ? "no player" : player.getEquippedStack(EquipmentSlot.CHEST).getItem());
        starts = clicks = uses = movements = corrections = glideOn = glideOff = 0;
        reportedAt = now;
    }

    @Override public void close() {
        flushActions();
        commits.close();
        receives.close();
    }
}
