package dev.xingclient.module;

import com.mojang.authlib.GameProfile;
import dev.xingclient.event.ClientTickEvent;
import dev.xingclient.event.EventBus;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.Entity.RemovalReason;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/** Spawns a local player-shaped target for combat module practice. */
public final class FakePlayerModule extends Module {
    private static final UUID PROFILE_ID = UUID.nameUUIDFromBytes(
            "xing_fake_player".getBytes(StandardCharsets.UTF_8));
    private static final int PREFERRED_ENTITY_ID = Integer.MIN_VALUE + 1337;
    private static final String PLAYER_NAME = "XingDummy";

    private final EventBus events;
    private EventBus.Subscription tickSubscription;
    private OtherClientPlayerEntity fakePlayer;
    private ClientWorld fakeWorld;
    private float simulatedHealth;

    public FakePlayerModule(EventBus events) {
        super("fake_player", "Fake Player", ModuleCategory.MISCELLANEOUS);
        this.events = events;
    }

    @Override
    protected void onEnable() {
        tickSubscription = events.subscribe(ClientTickEvent.class, this::onTick);
        spawnIfReady();
    }

    @Override
    protected void onDisable() {
        if (tickSubscription != null) {
            tickSubscription.close();
            tickSubscription = null;
        }
        removeFakePlayer();
    }

    @Override
    public String status() {
        return isEnabled() && fakePlayer != null ? "Spawned" : isEnabled() ? "Waiting for world" : "Disabled";
    }

    public boolean handlesAttack(Entity target, ClientPlayerEntity attacker) {
        if (!isEnabled() || fakePlayer == null || target != fakePlayer || attacker == null) return false;
        float baseDamage = (float) attacker.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
        float attackStrength = attacker.getAttackCooldownProgress(0.5F);
        float damage = baseDamage * (0.2F + attackStrength * attackStrength * 0.8F);
        boolean critical = attackStrength > 0.9F && attacker.fallDistance > 0.0F
                && !attacker.isOnGround() && !attacker.isClimbing() && !attacker.isTouchingWater();
        if (critical) damage *= 1.5F;

        fakePlayer.handleStatus((byte) 2);
        simulatedHealth -= damage;
        if (simulatedHealth <= 0.0F) {
            popTotem();
        } else {
            fakePlayer.setHealth(simulatedHealth);
        }
        return true;
    }

    private void onTick(ClientTickEvent event) {
        if (event.phase() != ClientTickEvent.Phase.END) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            removeFakePlayer();
            return;
        }
        if (fakePlayer == null || fakeWorld != client.world || fakePlayer.isRemoved()) {
            spawnIfReady();
        }
    }

    private void spawnIfReady() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (player == null || world == null) return;
        removeFakePlayer();

        OtherClientPlayerEntity dummy = new OtherClientPlayerEntity(
                world, new GameProfile(PROFILE_ID, PLAYER_NAME));
        dummy.setId(uniqueEntityId(world));
        Vec3d position = player.getPos();
        dummy.setPosition(position.x, position.y, position.z);
        dummy.setYaw(player.getYaw());
        dummy.setPitch(player.getPitch());
        dummy.headYaw = player.headYaw;
        dummy.bodyYaw = player.bodyYaw;
        dummy.setOnGround(player.isOnGround());
        dummy.setVelocity(Vec3d.ZERO);
        dummy.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        dummy.setCustomName(net.minecraft.text.Text.literal(PLAYER_NAME));
        dummy.setCustomNameVisible(true);
        world.addEntity(dummy);
        fakePlayer = dummy;
        fakeWorld = world;
        simulatedHealth = dummy.getMaxHealth();
    }

    private void popTotem() {
        if (fakePlayer == null || fakeWorld == null) return;
        simulatedHealth = fakePlayer.getMaxHealth();
        fakePlayer.setHealth(simulatedHealth);
        fakePlayer.handleStatus((byte) 35);
        fakeWorld.playSound(null, fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(),
                SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 1.0F, 1.0F);

        for (int i = 0; i < 30; i++) {
            double offsetX = (fakePlayer.getRandom().nextDouble() - 0.5) * fakePlayer.getWidth();
            double offsetY = fakePlayer.getRandom().nextDouble() * fakePlayer.getHeight();
            double offsetZ = (fakePlayer.getRandom().nextDouble() - 0.5) * fakePlayer.getWidth();
            double velocityX = fakePlayer.getRandom().nextGaussian() * 0.05;
            double velocityY = fakePlayer.getRandom().nextGaussian() * 0.05;
            double velocityZ = fakePlayer.getRandom().nextGaussian() * 0.05;
            MinecraftClient.getInstance().particleManager.addParticle(ParticleTypes.TOTEM_OF_UNDYING,
                    fakePlayer.getX() + offsetX, fakePlayer.getY() + offsetY,
                    fakePlayer.getZ() + offsetZ, velocityX, velocityY, velocityZ);
        }
    }

    private void removeFakePlayer() {
        if (fakePlayer != null && fakeWorld != null
                && fakeWorld.getEntityById(fakePlayer.getId()) == fakePlayer) {
            fakeWorld.removeEntity(fakePlayer.getId(), RemovalReason.DISCARDED);
        }
        fakePlayer = null;
        fakeWorld = null;
        simulatedHealth = 0.0F;
    }

    private static int uniqueEntityId(ClientWorld world) {
        int candidate = PREFERRED_ENTITY_ID;
        while (world.getEntityById(candidate) != null) candidate++;
        return candidate;
    }
}
