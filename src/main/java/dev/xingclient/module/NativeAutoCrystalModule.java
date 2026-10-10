package dev.xingclient.module;

import dev.xingclient.event.ClientTickEvent;
import dev.xingclient.event.CrystalSpawnEvent;
import dev.xingclient.event.EntitiesRemovedEvent;
import dev.xingclient.event.EventBus;
import dev.xingclient.event.WorldBlockUpdateEvent;
import dev.xingclient.XingClient;
import dev.xingclient.manager.InteractionManager;
import dev.xingclient.nativebridge.NativeCyclePlan;
import dev.xingclient.nativebridge.NativeCycleSnapshot;
import dev.xingclient.nativebridge.XingNativeBridge;
import dev.xingclient.mixin.ClientPlayerInteractionManagerAccessor;
import java.nio.ByteBuffer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Java snapshot and game-action adapter for the native placement cycle. */
public final class NativeAutoCrystalModule extends Module {
    private static final int OUTPUT_CAPACITY = XingNativeBridge.CYCLE_OUTPUT_HEADER_BYTES
            + 64 * XingNativeBridge.CYCLE_OUTPUT_PLACEMENT_BYTES;
    private static final int BREAK_ROTATION_PRIORITY = 70;
    private static final int PLACE_ROTATION_PRIORITY = 65;
    private static final int PLACE_SLOT_PRIORITY = 75;
    private static final int BREAK_ACTION_PRIORITY = 80;
    private static final int ROTATION_HOLD_TICKS = 2;
    private static final Logger LOGGER = LoggerFactory.getLogger("xingclient-autocrystal");
    private static final long STALL_LOG_INTERVAL_NANOS = 5_000_000_000L;

    private final EventBus events;
    private EventBus.Subscription tickSubscription;
    private EventBus.Subscription spawnSubscription;
    private EventBus.Subscription removalSubscription;
    private EventBus.Subscription worldUpdateSubscription;
    private String cycleStatus = "Idle";
    private int pendingBreakEntityId = -1;
    private boolean packetReactionQueued;
    private ClientWorld cycleWorld;
    private ClientPlayerEntity cyclePlayer;
    private long lastStallLogNanos;
    private long lastAttackLogNanos;

    public NativeAutoCrystalModule(EventBus events) {
        super("auto_crystal", "Auto Crystal", ModuleCategory.COMBAT);
        this.events = events;
    }

    @Override
    protected void onEnable() {
        tickSubscription = events.subscribe(ClientTickEvent.class, this::onTick);
        spawnSubscription = events.subscribe(CrystalSpawnEvent.class, this::onCrystalSpawn);
        removalSubscription = events.subscribe(EntitiesRemovedEvent.class, this::onEntitiesRemoved);
        worldUpdateSubscription = events.subscribe(WorldBlockUpdateEvent.class, this::onWorldBlockUpdate);
    }

    @Override
    protected void onDisable() {
        if (tickSubscription != null) {
            tickSubscription.close();
            tickSubscription = null;
        }
        if (spawnSubscription != null) {
            spawnSubscription.close();
            spawnSubscription = null;
        }
        if (removalSubscription != null) {
            removalSubscription.close();
            removalSubscription = null;
        }
        if (worldUpdateSubscription != null) {
            worldUpdateSubscription.close();
            worldUpdateSubscription = null;
        }
        cycleStatus = "Disabled";
        resetCycle();
        cycleWorld = null;
        cyclePlayer = null;
        dev.xingclient.XingClient.INSTANCE.miningSync.clear();
    }

    private void resetCycle() {
        pendingBreakEntityId = -1;
        XingClient.INSTANCE.managers.crystalActions.reset();
        packetReactionQueued = false;
        lastStallLogNanos = 0;
        lastAttackLogNanos = 0;
    }

    private void synchronizeWorld(MinecraftClient client, ClientPlayerEntity player) {
        if (cycleWorld == client.world && cyclePlayer == player) return;
        resetCycle();
        cycleWorld = client.world;
        cyclePlayer = player;
    }

    @Override
    public String status() {
        return isEnabled() ? cycleStatus : "Disabled";
    }

    private void onTick(ClientTickEvent event) {
        if (event.phase() != ClientTickEvent.Phase.END) return;
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        synchronizeWorld(client, player);
        if (player == null || client.world == null || client.interactionManager == null) {
            cycleStatus = "Waiting for world";
            return;
        }

        ClientPlayerInteractionManagerAccessor mining =
                (ClientPlayerInteractionManagerAccessor) client.interactionManager;
        if (mining.xing$isBreakingBlock()) {
            dev.xingclient.XingClient.INSTANCE.miningSync.update(
                    mining.xing$getCurrentBreakingPos(), mining.xing$getCurrentBreakingProgress(), true, false);
        } else {
            dev.xingclient.XingClient.INSTANCE.miningSync.clear();
        }

        if (pendingBreakEntityId != -1) {
            Entity pendingCrystal = client.world.getEntityById(pendingBreakEntityId);
            if (!(pendingCrystal instanceof EndCrystalEntity)) {
                pendingBreakEntityId = -1;
            }
        }

        runPlanner(client, player);
    }

    private void runPlanner(MinecraftClient client, ClientPlayerEntity player) {
        runPlanner(client, player, -1);
    }

    private void runPlanner(MinecraftClient client, ClientPlayerEntity player, int onlyCrystalEntityId) {
        if (client.world == null || client.interactionManager == null) return;
        synchronizeWorld(client, player);
        var crystalState = XingClient.INSTANCE.managers.crystalActions.state();
        NativeCycleSnapshot snapshot = NativeCycleSnapshotFactory.capture(
                client, player, crystalState.hasPendingPlacement(), onlyCrystalEntityId,
                onlyCrystalEntityId >= 0 ? crystalState.pendingTargetId() : -1,
                NativeCycleSnapshotFactory.Stage.BREAK);
        if (snapshot == null) {
            cycleStatus = "No target";
            reportStall();
            return;
        }
        NativeCyclePlan breakPlan = planSnapshot(snapshot, NativeCycleSnapshotFactory.Stage.BREAK);
        if (breakPlan == null) return;
        cycleStatus = "Break: " + breakPlan.breakReasonName();
        boolean breakSent = false;
        if (breakPlan.breakAction() == 2) {
            Entity entity = client.world.getEntityById(breakPlan.breakCrystalEntityId());
            if (entity instanceof EndCrystalEntity crystal && crystal.isAlive()) {
                breakSent = XingClient.INSTANCE.managers.rotations.withRotationRequest(actionOwner, BREAK_ROTATION_PRIORITY,
                        crystal.getPos().add(0.0, crystal.getHeight() * 0.5, 0.0), ROTATION_HOLD_TICKS,
                        () -> XingClient.INSTANCE.managers.interactions.attackImmediate(actionOwner, BREAK_ACTION_PRIORITY, crystal,
                                InteractionManager.Transport.PACKET, true));
                if (breakSent) {
                    XingClient.INSTANCE.managers.combatActions.markCrystalAction(2);
                    pendingBreakEntityId = crystal.getId();
                    XingClient.INSTANCE.managers.crystalActions.markAttack(
                            crystal.getId(), XingClient.INSTANCE.settings.autoCrystal.attackRetryTicks);
                    XingClient.INSTANCE.managers.crystalActions.markBroken();
                    cycleStatus = "Break sent for crystal " + crystal.getId();
                    reportAttack(player, crystal);
                } else {
                    cycleStatus = "Break send blocked by rotation, action resources, or transport";
                }
            }
        }
        if (onlyCrystalEntityId >= 0) return;
        if (breakSent && !breakPlan.placementRequiresSuccessfulBreak()) return;
        if (!breakSent && breakPlan.waitingForCrystalSpawn()) {
            cycleStatus = "Waiting for crystal spawn, break: " + breakPlan.breakReasonName();
            reportStall();
            return;
        }
        snapshot = NativeCycleSnapshotFactory.capture(client, player,
                XingClient.INSTANCE.managers.crystalActions.state().hasPendingPlacement(), -1, -1,
                NativeCycleSnapshotFactory.Stage.PLACEMENT);
        if (snapshot == null) return;
        NativeCyclePlan plan = planSnapshot(snapshot, NativeCycleSnapshotFactory.Stage.PLACEMENT);
        if (plan == null) return;
        cycleStatus = plan.placementReasonName() + ", " + plan.placements().size()
                + " placements, break: " + breakPlan.breakReasonName();
        if (plan.action() != 2 || plan.placements().isEmpty()) {
            if (!breakSent) reportStall();
            return;
        }

        for (NativeCyclePlan.Placement placement : plan.placements()) {
            BlockPos base = new BlockPos(placement.base().x(), placement.base().y(), placement.base().z());
            int replacementCrystalId = XingClient.INSTANCE.managers.crystalActions.replacementCrystalId(
                    XingClient.INSTANCE.settings.autoCrystal.sameTickBreakPlace);
            if (!NativeCycleSnapshotFactory.canPlaceNow(client, player, base, replacementCrystalId)) {
                cycleStatus = "Placement blocked by current block or entity collision";
                continue;
            }
            BlockHitResult hit = NativeCycleSnapshotFactory.placementHitResult(client.world, player, base);
            if (hit == null) {
                cycleStatus = "Placement has no reachable block face";
                continue;
            }
            boolean placed = XingClient.INSTANCE.managers.rotations.withRotationRequest(actionOwner, PLACE_ROTATION_PRIORITY,
                    hit.getPos(), ROTATION_HOLD_TICKS, () -> XingClient.INSTANCE.managers.interactions.useBlock(
                            actionOwner, PLACE_SLOT_PRIORITY, hit, stack -> stack.isOf(Items.END_CRYSTAL),
                            XingClient.INSTANCE.settings.autoCrystal.swapMode,
                            XingClient.INSTANCE.settings.autoCrystal.swapMode == dev.xingclient.manager.InventoryManager.SwapMode.SILENT
                                    ? XingClient.INSTANCE.settings.autoCrystal.silentRestoreDelay
                                    : XingClient.INSTANCE.settings.autoCrystal.restoreSlotDelay,
                            InteractionManager.Transport.PACKET, true,
                            XingClient.INSTANCE.settings.autoCrystal.handMode));
            if (placed) {
                XingClient.INSTANCE.managers.combatActions.markCrystalAction(2);
                XingClient.INSTANCE.managers.crystalActions.markPlaced(base, snapshot.targetEntityId(),
                        XingClient.INSTANCE.settings.autoCrystal.predictionTimeout);
                cycleStatus = "Placement sent at " + base.toShortString();
                return;
            }
            cycleStatus = "Placement send blocked by rotation, slot, action resources, or transport";
        }
        if (!breakSent) reportStall();
    }

    private NativeCyclePlan planSnapshot(NativeCycleSnapshot snapshot, NativeCycleSnapshotFactory.Stage stage) {
        ByteBuffer output = XingNativeBridge.cycleOutputBuffer(OUTPUT_CAPACITY);
        try {
            int result = stage == NativeCycleSnapshotFactory.Stage.BREAK
                    ? XingNativeBridge.planBreak(snapshot.encode(), output)
                    : XingNativeBridge.planPlacement(snapshot.encode(), output);
            if (result == 0) return NativeCycleSnapshot.decode(output);
            cycleStatus = "Native planner error " + result;
            reportStall();
        } catch (LinkageError error) {
            cycleStatus = "Native library unavailable";
            setEnabled(false);
        }
        return null;
    }

    private void reportStall() {
        long now = System.nanoTime();
        if (lastStallLogNanos != 0 && now - lastStallLogNanos < STALL_LOG_INTERVAL_NANOS) return;
        lastStallLogNanos = now;
        var app = XingClient.INSTANCE;
        LOGGER.info("{}; pendingPlace={}, pendingBreak={}, velocityEnabled={}, minDamage={}, maxSelfDamage={}",
                cycleStatus, app.managers.crystalActions.state().pendingBase(), pendingBreakEntityId,
                app.velocity != null && app.velocity.isEnabled(),
                app.settings.autoCrystal.minimumDamage, app.settings.autoCrystal.maximumSelfDamage);
    }

    private void reportAttack(ClientPlayerEntity player, EndCrystalEntity crystal) {
        long now = System.nanoTime();
        if (lastAttackLogNanos != 0 && now - lastAttackLogNanos < STALL_LOG_INTERVAL_NANOS) return;
        lastAttackLogNanos = now;
        LOGGER.info("Attack queued: crystal={}, distance={}, player={}, crystalPos={}, hand={}, weakness={}, lastCommit={}",
                crystal.getId(), player.distanceTo(crystal), player.getPos(), crystal.getPos(),
                player.getMainHandStack().getItem(), player.hasStatusEffect(StatusEffects.WEAKNESS),
                XingClient.INSTANCE.managers.network.latestCommitId(actionOwner));
    }

    private void onWorldBlockUpdate(WorldBlockUpdateEvent event) {
        queuePacketReaction();
    }

    private void queuePacketReaction() {
        if (!isEnabled()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.interactionManager == null) return;
        synchronizeWorld(client, client.player);
        if (packetReactionQueued) return;
        packetReactionQueued = true;
        ClientWorld expectedWorld = client.world;
        client.execute(() -> {
            packetReactionQueued = false;
            if (!isEnabled() || client.player == null || client.world == null
                    || client.interactionManager == null || client.world != expectedWorld) {
                return;
            }
            if (!XingClient.INSTANCE.managers.crystalActions.beginPacketReaction(client.world.getTime())) return;
            runPlanner(client, client.player);
        });
    }

    private void onCrystalSpawn(CrystalSpawnEvent event) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        EndCrystalEntity crystal = event.crystal();
        if (isEnabled() && player != null && client.world != null) synchronizeWorld(client, player);
        if (!isEnabled() || player == null || client.world == null
                || client.interactionManager == null || !crystal.isAlive()) {
            return;
        }
        var state = XingClient.INSTANCE.managers.crystalActions.state();
        BlockPos base = crystal.getBlockPos().down();
        if (base.equals(state.pendingBase())) {
            XingClient.INSTANCE.managers.crystalActions.markSpawned(base, crystal.getId());
            runPlanner(client, player, crystal.getId());
        }
        queuePacketReaction();
    }

    private void onEntitiesRemoved(EntitiesRemovedEvent event) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        synchronizeWorld(client, client.player);
        boolean crystalRemoved = false;
        for (int entityId : event.entityIds()) {
            crystalRemoved |= XingClient.INSTANCE.managers.crystalActions.markRemoved(entityId);
            if (entityId == pendingBreakEntityId) pendingBreakEntityId = -1;
        }
        if (crystalRemoved) queuePacketReaction();
    }

    static boolean hasCrystal(ClientPlayerEntity player) {
        if (player.getOffHandStack().isOf(Items.END_CRYSTAL)
                || player.getMainHandStack().isOf(Items.END_CRYSTAL)) return true;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            if (player.getInventory().getStack(slot).isOf(Items.END_CRYSTAL)) return true;
        }
        return false;
    }
}
