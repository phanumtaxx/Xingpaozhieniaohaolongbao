package dev.xingclient.module;

import dev.xingclient.ClientSettings;
import dev.xingclient.XingClient;
import dev.xingclient.event.ClientTickEvent;
import dev.xingclient.event.EventBus;
import dev.xingclient.render.ResumeClock;
import dev.xingclient.nativebridge.XingNativeBridge;
import java.util.Comparator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Util;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.glfw.GLFWNativeWin32;

/** Minecraft adapter for wind-charge flight and a bounded native pause. Author: uint32. */
public final class MaceKillModule extends Module {
    public static final String ID = "mace_kill";
    private static final Logger LOGGER = LoggerFactory.getLogger("xingclient-mace");
    private record CorrectionSnapshot(MaceSequence.Phase phase, int ticks, Vec3d position,
            Vec3d velocity, boolean onGround) {}
    private final MaceSequence sequence = new MaceSequence();
    private final MaceDiagnostics diagnostics = new MaceDiagnostics();
    private ClientWorld world;
    private ClientPlayerEntity player;
    private ClientSettings.MaceKillSettings options;
    private int originalSlot = -1, ownedSlot = -1;
    private float originalPitch, health;
    private int lastHurt;
    private boolean changedPitch;
    private MaceSequence.Phase loggedPhase;
    private CorrectionSnapshot correctionSnapshot;

    public MaceKillModule(EventBus events) {
        super(ID, "MaceKill", ModuleCategory.COMBAT);
        events.subscribe(ClientTickEvent.class, event -> {
            if (event.phase() == ClientTickEvent.Phase.START) {
                diagnostics.tick(isEnabled());
                if (isEnabled()) tick();
            }
        });
    }

    @Override protected void onEnable() {
        sequence.start();
        player = MinecraftClient.getInstance().player; world = MinecraftClient.getInstance().world;
        originalSlot = ownedSlot = -1; changedPitch = false;
        loggedPhase = null;
        correctionSnapshot = null;
        // Copy normalized settings so a running sequence has stable timing.
        options = XingClient.INSTANCE.settings.maceKill.copy();
    }

    @Override protected void onDisable() {
        var mc = MinecraftClient.getInstance();
        if (player != null && mc.player == player && mc.world == world) {
            restorePitch();
            if (originalSlot >= 0 && player.getInventory().getSelectedSlot() == ownedSlot) select(originalSlot);
        }
        if (sequence.phase() != MaceSequence.Phase.IDLE) sequence.stop("Stopped");
        originalSlot = ownedSlot = -1;
    }

    @Override public String status() {
        return sequence.phase() == MaceSequence.Phase.CHARGING
                ? "Charging " + sequence.impulses() + "/" + options.charges : sequence.message();
    }

    public MaceSequence.Phase phase() { return sequence.phase(); }
    @Deprecated public boolean frozen() { return false; }

    public void pauseAtTickEnd() {
        if (!isEnabled() || sequence.phase() != MaceSequence.Phase.HELD) return;
        var mc = MinecraftClient.getInstance();
        if (mc.getWindow().isFullscreen()) {
            stop("Use windowed mode for title-bar pause");
            return;
        }
        if (mc.player != player || mc.world != world || player.isOnGround()
                || mc.currentScreen != null || !mc.isWindowFocused()
                || !Double.isFinite(groundGap()) || groundGap() < options.minimumFall) {
            stop("Airborne pause conditions changed");
            return;
        }
        LOGGER.info("Native menu pause entering: durationMs={}, position={}, velocity={}, groundGap={}",
                options.stallMillis, player.getPos(), player.getVelocity(), groundGap());
        try {
            long window = GLFWNativeWin32.glfwGetWin32Window(mc.getWindow().getHandle());
            XingNativeBridge.PauseResult result;
            try {
                result = XingNativeBridge.pauseGameThread(window, options.stallMillis);
            } finally {
                ((ResumeClock) mc.getRenderTickCounter()).xing$resetAfterPause(Util.getMeasuringTimeMs());
            }
            LOGGER.info("Native menu pause returned: elapsedMs={}, cancelled={}, position={}, velocity={}",
                    result.elapsedMillis(), result.cancelled(), player.getPos(), player.getVelocity());
            if (result.cancelled()) {
                stop("Pause cancelled by Escape or focus loss");
                return;
            }
            sequence.resumeFlight();
            player.sendMessage(Text.literal("MaceKill / " + sequence.message()), true);
        } catch (LinkageError | RuntimeException error) {
            LOGGER.error("Native pause failed", error);
            stop("Native pause unavailable; check DLL and restart client");
        }
    }

    private void tick() {
        var mc = MinecraftClient.getInstance();
        if (player == null || mc.player != player || mc.world != world || mc.getNetworkHandler() == null
                || !player.isAlive() || player.isRemoved()) { stop("World/player changed"); return; }
        if (player.hasVehicle() || player.isGliding() || player.isTouchingWater() || player.isInLava()
                || player.isClimbing() || player.getAbilities().flying || player.isSpectator()) {
            stop("Unsupported movement"); return;
        }
        if (sequence.phase() == MaceSequence.Phase.PREPARE) {
            sequence.tick();
            if (sequence.ticks() > 200) { stop("Start timed out"); return; }
            if (mc.currentScreen != null) return;
            if (slot(Items.MACE) < 0) { stop("Need mace in hotbar"); return; }
            if (player.isOnGround() && chargeCount() < options.charges) { stop("Need charges in hotbar"); return; }
            if (!player.isOnGround() && (!Double.isFinite(groundGap()) || groundGap() < options.minimumFall)) {
                stop("Need more height above a surface"); return;
            }
            try {
                XingNativeBridge.prepareGameThreadPause();
            } catch (LinkageError | RuntimeException error) {
                LOGGER.error("Native pause initialization failed", error);
                stop("Native pause unavailable; check DLL and restart client");
                return;
            }
            originalSlot = player.getInventory().getSelectedSlot(); originalPitch = player.getPitch();
            health = player.getHealth() + player.getAbsorptionAmount(); lastHurt = player.hurtTime;
            if (player.isOnGround()) sequence.charge(player.getY());
            else {
                select(slot(Items.MACE));
                sequence.launch(player.getY());
            }
        }
        if (mc.currentScreen != null && mc.currentScreen != XingClient.INSTANCE.screen) { stop("Menu opened"); return; }
        if (!mc.isWindowFocused()) { stop("Window unfocused"); return; }
        if (XingClient.INSTANCE.modules.all().stream().anyMatch(m -> m instanceof NativeAutoCrystalModule && m.isEnabled())) {
            stop("Disable AutoCrystal first"); return;
        }
        if (player.getHealth() + player.getAbsorptionAmount() < health || player.hurtTime > lastHurt) {
            stop("Hit: charge invalidated"); return;
        }
        health = player.getHealth() + player.getAbsorptionAmount(); lastHurt = player.hurtTime;
        sequence.tick();
        switch (sequence.phase()) {
            case CHARGING -> charge(mc);
            case ASCENDING, FALLING -> {
                if (sequence.ticks() > 400) { stop("Flight timed out"); return; }
                if (player.isOnGround()) { stop("Ground contact: attempt finished"); return; }
                sequence.flight(player.getY(), player.getVelocity().y, groundGap(), options.holdHeight, options.minimumFall);
                if (sequence.stallCompleted() && options.autoAttack && mc.currentScreen == null) autoAttack(mc);
            }
            default -> { }
        }
        if (isEnabled() && sequence.ticks() % 10 == 0)
            player.sendMessage(Text.literal("MaceKill / " + status()), true);
        if (isEnabled() && sequence.phase() != loggedPhase) {
            loggedPhase = sequence.phase();
            LOGGER.info("Stage {}: tick={}, shots={}, impulses={}, position={}, velocity={}, onGround={}, localDescent={}",
                    loggedPhase, sequence.ticks(), sequence.shots(), sequence.impulses(), player.getPos(),
                    player.getVelocity(), player.isOnGround(), sequence.fallen());
        }
    }

    private void charge(MinecraftClient mc) {
        sequence.observeHeight(player.getY());
        if (sequence.ticks() > options.charges * (options.chargeIntervalTicks + 45) + 40) { stop("Charge timed out"); return; }
        if (sequence.shots() > sequence.impulses() && !sequence.waitingForImpulse()) { stop("No charge impulse received"); return; }
        if (sequence.readyToLaunch(options.charges)) {
            if (slot(Items.MACE) < 0) { stop("Missing mace"); return; }
            select(slot(Items.MACE)); restorePitch();
            sequence.launch(player.getY());
            return;
        }
        if (!sequence.shotDue(options.charges, options.chargeIntervalTicks)) return;
        if (sequence.shots() > 0 && !player.isOnGround()
                && (player.getVelocity().y > 0 || groundGap() > 2)) return;
        int slot = slot(Items.WIND_CHARGE);
        if (slot < 0) { stop("Out of wind charges"); return; }
        select(slot);
        if (player.getItemCooldownManager().isCoolingDown(player.getMainHandStack())) return;
        player.setPitch(90); changedPitch = true;
        var result = mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
        if (result.isAccepted()) {
            sequence.shot();
            player.swingHand(Hand.MAIN_HAND);
        }
    }

    /** Only a pending shot near our feet is an expected self-charge explosion. */
    public void explosion(Vec3d center, Vec3d velocity) {
        if (!isEnabled() || velocity.lengthSquared() < 1.0e-8) return;
        if (sequence.phase() == MaceSequence.Phase.CHARGING && sequence.waitingForImpulse()
                && center.squaredDistanceTo(player.getPos()) < 9 && velocity.y > 0) {
            if (sequence.impulse()) {
                sequence.resetFlightHeight(player.getY());
                LOGGER.info("Charge impulse: tick={}, shot={}, received={}, appliedVelocity={}",
                        sequence.ticks(), sequence.shots(), velocity, player.getVelocity());
            }
        } else stop("External impulse: attempt ended");
    }

    public void velocityUpdate() {
        diagnostics.velocityApplied(isEnabled());
        if (!isEnabled()) return;
        LOGGER.info("Velocity update: phase={}, tick={}, position={}, appliedVelocity={}",
                sequence.phase(), sequence.ticks(), player.getPos(), player.getVelocity());
        if (!sequence.stallCompleted()) stop("Velocity changed before pause");
    }

    public void beforeCorrection() {
        diagnostics.beforeCorrection(isEnabled());
        var mc = MinecraftClient.getInstance();
        if (!mc.isOnThread() || !isEnabled() || mc.player != player) return;
        correctionSnapshot = new CorrectionSnapshot(sequence.phase(), sequence.ticks(), player.getPos(),
                player.getVelocity(), player.isOnGround());
    }

    public void corrected() {
        diagnostics.correctionApplied();
        if (!isEnabled() || MinecraftClient.getInstance().player != player) return;
        CorrectionSnapshot before = correctionSnapshot;
        correctionSnapshot = null;
        LOGGER.info("Server correction: phase={}, tick={}, before={}, velocityBefore={}, onGroundBefore={}, "
                        + "after={}, velocityAfter={}, onGroundAfter={}, shots={}, impulses={}, localDescent={}",
                before == null ? sequence.phase() : before.phase(), before == null ? sequence.ticks() : before.ticks(),
                before == null ? null : before.position(), before == null ? null : before.velocity(),
                before == null ? null : before.onGround(), player.getPos(), player.getVelocity(), player.isOnGround(),
                sequence.shots(), sequence.impulses(), sequence.fallen());
        if (!sequence.stallCompleted()) {
            stop("Unexpected server correction during " + sequence.phase());
            return;
        }
        if (player.isOnGround() || groundGap() <= 1.0e-5 || !world.isSpaceEmpty(player))
            stop("Correction ended airborne attempt");
    }

    public void attacked(Entity target) {
        if (isEnabled() && sequence.stallCompleted()) stop("Attack sent; re-enable for another attempt");
    }

    private void autoAttack(MinecraftClient mc) {
        if (!player.getMainHandStack().isOf(Items.MACE) || player.getAttackCooldownProgress(0) < 0.95F) return;
        double range = Math.min(options.attackRange, player.getEntityInteractionRange());
        PlayerEntity target = world.getPlayers().stream().filter(p -> p != player && p.isAlive() && !p.isSpectator()
                && !player.isTeammate(p) && !XingClient.INSTANCE.friends.isFriend(p.getName().getString())
                && player.squaredDistanceTo(p) <= range * range && player.canSee(p))
                .min(Comparator.comparingDouble(player::squaredDistanceTo)).orElse(null);
        if (target == null) return;
        Vec3d aim = target.getBoundingBox().getCenter().subtract(player.getEyePos());
        player.setYaw((float) (Math.toDegrees(Math.atan2(aim.z, aim.x)) - 90));
        player.setPitch((float) -Math.toDegrees(Math.atan2(aim.y, Math.hypot(aim.x, aim.z))));
        mc.interactionManager.attackEntity(player, target); player.swingHand(Hand.MAIN_HAND);
    }

    private double groundGap() {
        // Five rays account for slabs and edges; never replace server positions with a spoofed gap.
        double gap = Double.POSITIVE_INFINITY;
        double r = player.getWidth() * 0.45;
        for (double[] offset : new double[][]{{0,0},{-r,-r},{-r,r},{r,-r},{r,r}}) {
            Vec3d start = player.getPos().add(offset[0], 0.02, offset[1]);
            var hit = world.raycast(new RaycastContext(start, start.add(0,-64,0), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
            if (hit.getType() == HitResult.Type.BLOCK) gap = Math.min(gap, Math.max(0, player.getY() - hit.getPos().y));
        }
        return gap;
    }

    private int slot(Item item) {
        for (int i = 0; i < 9; i++) if (player.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }
    private int chargeCount() {
        int count = 0;
        for (int i = 0; i < 9; i++) if (player.getInventory().getStack(i).isOf(Items.WIND_CHARGE)) count += player.getInventory().getStack(i).getCount();
        return count;
    }
    private void select(int slot) {
        if (slot < 0) return;
        player.getInventory().setSelectedSlot(slot); ownedSlot = slot;
    }
    private void restorePitch() { if (changedPitch) { player.setPitch(originalPitch); changedPitch = false; } }
    private void stop(String reason) {
        LOGGER.info("Stopped in {} at tick {}: {}", sequence.phase(), sequence.ticks(), reason);
        sequence.stop(reason); setEnabled(false);
        var app = XingClient.INSTANCE;
        var entry = app.settings.modules.get(ID);
        if (entry != null) entry.enabled = false;
        app.changed();
        if (MinecraftClient.getInstance().player != null) MinecraftClient.getInstance().player.sendMessage(Text.literal("MaceKill / " + reason), true);
    }
}
