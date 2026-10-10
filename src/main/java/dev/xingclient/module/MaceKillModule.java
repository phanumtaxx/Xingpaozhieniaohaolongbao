package dev.xingclient.module;

import dev.xingclient.ClientSettings;
import dev.xingclient.XingClient;
import dev.xingclient.event.ClientTickEvent;
import dev.xingclient.event.EventBus;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import java.util.Comparator;

/** Experimental local movement hold. Does not claim to reproduce a proprietary Air Stuck. */
public final class MaceKillModule extends Module {
    public static final String ID = "mace_kill";
    private final MaceSequence sequence = new MaceSequence();
    private ClientWorld world;
    private ClientPlayerEntity player;
    private ClientSettings.MaceKillSettings options;
    private Vec3d impulse = Vec3d.ZERO, anchor;
    private int originalSlot = -1, ownedSlot = -1;
    private float originalPitch, health;
    private int lastHurt;
    private boolean changedPitch;
    private MaceSequence.Phase loggedPhase;

    public MaceKillModule(EventBus events) {
        super(ID, "MaceKill", ModuleCategory.COMBAT);
        events.subscribe(ClientTickEvent.class, event -> {
            if (isEnabled() && event.phase() == ClientTickEvent.Phase.START) tick();
        });
    }

    @Override protected void onEnable() {
        sequence.start(); impulse = Vec3d.ZERO; anchor = null;
        player = MinecraftClient.getInstance().player; world = MinecraftClient.getInstance().world;
        originalSlot = ownedSlot = -1; changedPitch = false;
        loggedPhase = null;
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
        impulse = Vec3d.ZERO; anchor = null; originalSlot = ownedSlot = -1;
    }

    @Override public String status() {
        return sequence.phase() == MaceSequence.Phase.CHARGING
                ? "Charging " + sequence.impulses() + "/" + options.charges : sequence.message();
    }

    public MaceSequence.Phase phase() { return sequence.phase(); }
    public boolean frozen() {
        var mc = MinecraftClient.getInstance();
        return isEnabled() && sequence.frozen() && mc.player == player && mc.world == world;
    }

    /** Called before vanilla movement, while input, camera, network and item ticks stay alive. */
    public void freezeMovement() {
        if (frozen()) {
            player.setVelocity(Vec3d.ZERO);
            player.input.playerInput = net.minecraft.util.PlayerInput.DEFAULT;
            player.setSprinting(false);
        }
    }

    /** Only the normal movement sender is throttled. Teleport acknowledgments are untouched. */
    public void sendFrozenMovement() {
        if (frozen() && sequence.heartbeatDue(options.heartbeatTicks)) {
            player.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(player.getPos(),
                    player.getYaw(), player.getPitch(), player.isOnGround(), player.horizontalCollision));
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
            if (!player.isOnGround() || groundGap() > 0.1) { stop("Start on a solid block"); return; }
            if (slot(Items.MACE) < 0 || chargeCount() < options.charges) { stop("Need mace + charges in hotbar"); return; }
            originalSlot = player.getInventory().getSelectedSlot(); originalPitch = player.getPitch();
            health = player.getHealth() + player.getAbsorptionAmount(); lastHurt = player.hurtTime;
            anchor = player.getPos(); sequence.charge(player.getY());
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
                if (player.isOnGround() && sequence.ticks() > 3) { stop("Landed / launch rejected"); return; }
                sequence.flight(player.getY(), player.getVelocity().y, groundGap(), options.holdHeight, options.minimumFall);
                if (sequence.phase() == MaceSequence.Phase.HELD) {
                    anchor = player.getPos(); player.setVelocity(Vec3d.ZERO);
                }
            }
            case HELD -> {
                if (player.isOnGround() || groundGap() <= 1.0e-5) { stop("Ground contact: charge lost"); return; }
                if (sequence.ticks() > options.holdSeconds * 20) { stop("Hold timed out"); return; }
                if (player.getPos().squaredDistanceTo(anchor) > 0.01) { stop("Position changed"); return; }
                if (options.autoAttack && mc.currentScreen == null) autoAttack(mc);
            }
            default -> { }
        }
        if (isEnabled() && sequence.ticks() % 10 == 0)
            player.sendMessage(Text.literal("MaceKill / " + status()), true);
        if (isEnabled() && sequence.phase() != loggedPhase) {
            loggedPhase = sequence.phase();
            org.slf4j.LoggerFactory.getLogger("xingclient-mace").info("Stage {}: {} impulses, local descent {}",
                    loggedPhase, sequence.impulses(), sequence.fallen());
        }
    }

    private void charge(MinecraftClient mc) {
        if (groundGap() > 0.1 || player.getPos().squaredDistanceTo(anchor) > 0.01) { stop("Charging position changed"); return; }
        if (sequence.ticks() > options.charges * (options.chargeIntervalTicks + 45) + 40) { stop("Charge timed out"); return; }
        if (sequence.shots() > sequence.impulses() && !sequence.waitingForImpulse()) { stop("No charge impulse received"); return; }
        if (sequence.readyToLaunch(options.charges)) {
            if (impulse.y <= 0.05 || slot(Items.MACE) < 0) { stop("No launch / missing mace"); return; }
            select(slot(Items.MACE)); restorePitch();
            sequence.launch(player.getY()); player.setVelocity(impulse); impulse = Vec3d.ZERO;
            return;
        }
        if (!sequence.shotDue(options.charges, options.chargeIntervalTicks)) return;
        int slot = slot(Items.WIND_CHARGE);
        if (slot < 0) { stop("Out of wind charges"); return; }
        select(slot);
        if (player.getItemCooldownManager().isCoolingDown(player.getMainHandStack())) return;
        player.setPitch(90); changedPitch = true;
        var result = mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
        if (result.isAccepted()) { sequence.shot(); player.swingHand(Hand.MAIN_HAND); }
    }

    /** Only a pending shot near our feet is an expected self-charge explosion. */
    public void explosion(Vec3d center, Vec3d velocity) {
        if (!isEnabled() || velocity.lengthSquared() < 1.0e-8) return;
        if (sequence.phase() == MaceSequence.Phase.CHARGING && sequence.waitingForImpulse()
                && center.squaredDistanceTo(player.getPos()) < 9 && velocity.y > 0) {
            if (sequence.impulse()) impulse = impulse.add(velocity);
            player.setVelocity(Vec3d.ZERO);
        } else stop("External impulse: charge lost");
    }

    public void velocityUpdate() {
        if (!isEnabled()) return;
        // Absolute velocity updates must not be added to an explosion impulse a second time.
        if (sequence.phase() == MaceSequence.Phase.CHARGING) player.setVelocity(Vec3d.ZERO);
        else stop("Velocity changed: charge lost");
    }

    public void corrected() {
        if (!isEnabled() || MinecraftClient.getInstance().player != player) return;
        if (sequence.phase() != MaceSequence.Phase.HELD) { stop("Unexpected server correction"); return; }
        // Accept only a nearby downward correction, with a real gap remaining above the floor.
        Vec3d next = player.getPos();
        double dx = next.x - anchor.x, dz = next.z - anchor.z;
        if (dx * dx + dz * dz > 1 || next.y > anchor.y + 0.1 || anchor.y - next.y > 8
                || groundGap() <= 1.0e-5 || !world.isSpaceEmpty(player)) {
            stop("Correction invalidated hold"); return;
        }
        anchor = next; player.setVelocity(Vec3d.ZERO);
    }

    public void attacked(Entity target) {
        if (isEnabled() && sequence.phase() == MaceSequence.Phase.HELD) stop("Attack sent; re-enable to charge");
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
        player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(player.getYaw(), player.getPitch(), player.isOnGround(), player.horizontalCollision));
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
        player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
    }
    private void restorePitch() { if (changedPitch) { player.setPitch(originalPitch); changedPitch = false; } }
    private void stop(String reason) {
        org.slf4j.LoggerFactory.getLogger("xingclient-mace").info("Stopped in {}: {}", sequence.phase(), reason);
        sequence.stop(reason); setEnabled(false);
        var app = XingClient.INSTANCE;
        var entry = app.settings.modules.get(ID);
        if (entry != null) entry.enabled = false;
        app.changed();
        if (MinecraftClient.getInstance().player != null) MinecraftClient.getInstance().player.sendMessage(Text.literal("MaceKill / " + reason), true);
    }
}
