package dev.xingclient.module;

import dev.xingclient.nativebridge.NativeCycleSnapshot;
import dev.xingclient.XingClient;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.math.Vec3d;

final class NativeCycleSnapshotFactory {
    private static final int PLACEMENT_RADIUS = 8;
    private static final int COLLISION_RADIUS = 8;
    private static final double PLACE_RANGE_SQUARED = 20.25;
    private static final double WALL_RANGE_SQUARED = 9.0;
    private static final double[][] FACE_SAMPLES = {
            {0.5, 0.5}, {0.1, 0.1}, {0.1, 0.9}, {0.9, 0.1}, {0.9, 0.9}
    };

    private NativeCycleSnapshotFactory() {}

    static NativeCycleSnapshot capture(
            MinecraftClient client,
            ClientPlayerEntity player,
            boolean hasPendingPlacement,
            int pendingBreakEntityId) {
        return capture(client, player, hasPendingPlacement, pendingBreakEntityId, -1);
    }

    static NativeCycleSnapshot capture(
            MinecraftClient client,
            ClientPlayerEntity player,
            boolean hasPendingPlacement,
            int pendingBreakEntityId,
            int onlyCrystalEntityId) {
        return capture(client, player, hasPendingPlacement, pendingBreakEntityId, onlyCrystalEntityId, -1);
    }

    static NativeCycleSnapshot capture(
            MinecraftClient client, ClientPlayerEntity player, boolean hasPendingPlacement,
            int pendingBreakEntityId, int onlyCrystalEntityId, int ignoredPlacementEntityId) {
        var world = client.world;
        PlayerEntity target = world.getPlayers().stream()
                .filter(candidate -> candidate != player && candidate.isAlive() && !candidate.isSpectator())
                .filter(candidate -> XingClient.INSTANCE == null || XingClient.INSTANCE.friends == null
                        || !XingClient.INSTANCE.friends.isFriend(candidate.getName().getString()))
                .min(Comparator.comparingDouble(player::squaredDistanceTo))
                .orElse(null);
        if (target == null) return null;

        Vec3d localPosition = player.getPos();
        Vec3d targetPosition = target.getPos();
        BlockPos targetBlock = target.getBlockPos();
        List<NativeCycleSnapshot.Block> blocks = new ArrayList<>();
        List<NativeCycleSnapshot.Collision> collisionBoxes = new ArrayList<>();
        List<NativeCycleSnapshot.PhaseBlock> phaseBlocks = phaseBlocks(world, target.getBoundingBox());
        List<NativeCycleSnapshot.Position> reachable = new ArrayList<>();
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int y = targetBlock.getY() - PLACEMENT_RADIUS; y <= targetBlock.getY() + PLACEMENT_RADIUS; y++) {
            for (int z = targetBlock.getZ() - PLACEMENT_RADIUS; z <= targetBlock.getZ() + PLACEMENT_RADIUS; z++) {
                for (int x = targetBlock.getX() - PLACEMENT_RADIUS; x <= targetBlock.getX() + PLACEMENT_RADIUS; x++) {
                    mutable.set(x, y, z);
                    var state = world.getBlockState(mutable);
                    int kind = state.isAir() ? 1 : state.isOf(Blocks.OBSIDIAN) ? 2
                            : state.isOf(Blocks.BEDROCK) ? 3 : 0;
                    blocks.add(new NativeCycleSnapshot.Block(new NativeCycleSnapshot.Position(x, y, z), kind));
                    if (kind == 2 || kind == 3) {
                        if (isWithinPlacementRange(player, mutable)) {
                            reachable.add(new NativeCycleSnapshot.Position(x, y, z));
                        }
                    }
                }
            }
        }

        Set<BlockPos> sampledCollisionBlocks = new HashSet<>();
        appendCollisionBoxes(world, targetBlock, collisionBoxes, sampledCollisionBlocks);
        appendCollisionBoxes(world, player.getBlockPos(), collisionBoxes, sampledCollisionBlocks);

        Box entitySearch = new Box(targetBlock).expand(PLACEMENT_RADIUS + 2.0);
        List<NativeCycleSnapshot.Entity> entities = new ArrayList<>();
        entities.add(new NativeCycleSnapshot.Entity(player.getId(), toNative(player.getBoundingBox())));
        List<NativeCycleSnapshot.Crystal> crystals = new ArrayList<>();
        dev.xingclient.MiningSyncState.Opening mineOpening = XingClient.INSTANCE == null
                ? null : XingClient.INSTANCE.miningSync.eligibleOpening();
        Map<Integer, Entity> nearbyEntities = new LinkedHashMap<>();
        for (Entity entity : world.getOtherEntities(player, entitySearch)) {
            nearbyEntities.put(entity.getId(), entity);
        }
        Box crystalSearch = new Box(player.getBlockPos()).expand(7.0);
        for (Entity entity : world.getOtherEntities(player, crystalSearch)) {
            nearbyEntities.put(entity.getId(), entity);
        }
        for (Entity entity : nearbyEntities.values()) {
            if (entity.getId() != ignoredPlacementEntityId) {
                entities.add(new NativeCycleSnapshot.Entity(entity.getId(), toNative(entity.getBoundingBox())));
            }
            if (entity instanceof EndCrystalEntity crystal && player.squaredDistanceTo(crystal) <= 49.0
                    && (onlyCrystalEntityId < 0 || crystal.getId() == onlyCrystalEntityId)) {
                crystals.add(new NativeCycleSnapshot.Crystal(
                        crystal.getId(), toPosition(crystal.getBlockPos()), toNative(crystal.getPos()),
                        crystal.isAlive(), true, crystal.getId() == pendingBreakEntityId,
                        player.squaredDistanceTo(crystal)));
            }
        }

        var settings = XingClient.INSTANCE == null || XingClient.INSTANCE.settings == null
                ? new dev.xingclient.ClientSettings.AutoCrystalSettings()
                : XingClient.INSTANCE.settings.autoCrystal;
        double minimumDamage = target.getHealth() <= settings.lowHealthThreshold
                ? settings.lowHealthMinimumDamage : settings.minimumDamage;

        return new NativeCycleSnapshot(
                player.getId(), target.getId(), PLACEMENT_RADIUS, 1,
                Integer.toUnsignedLong(world.getRegistryKey().hashCode()),
                toNative(localPosition), toNative(targetPosition),
                toNative(target.getPos().subtract(target.getVelocity())),
                toNative(target.getBoundingBox()), toNative(player.getBoundingBox()),
                player.getHealth() + player.getAbsorptionAmount(), target.getArmor(), player.getArmor(),
                target.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS),
                player.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS),
                resistanceAmplifier(target), resistanceAmplifier(player),
                minimumDamage, settings.maximumSelfDamage, 2.0, 0.8,
                true, NativeAutoCrystalModule.hasCrystal(player), crystalHand(player) != null, true,
                hasPendingPlacement, false, settings.breakExisting, settings.sameTickBreakPlace, settings.breakRange,
                blocks, entities, collisionBoxes, reachable, crystals,
                true, 0.12, 2, phaseBlocks,
                mineOpening == null ? null : toPosition(mineOpening.position()),
                mineOpening == null ? 0.0 : mineOpening.scoreWeight(), 1, 2);
    }

    private static List<NativeCycleSnapshot.PhaseBlock> phaseBlocks(
            net.minecraft.client.world.ClientWorld world, Box targetBounds) {
        List<NativeCycleSnapshot.PhaseBlock> result = new ArrayList<>();
        BlockPos.Mutable position = new BlockPos.Mutable();
        int minX = BlockPos.ofFloored(targetBounds.minX, targetBounds.minY, targetBounds.minZ).getX();
        int minY = BlockPos.ofFloored(targetBounds.minX, targetBounds.minY, targetBounds.minZ).getY();
        int minZ = BlockPos.ofFloored(targetBounds.minX, targetBounds.minY, targetBounds.minZ).getZ();
        int maxX = BlockPos.ofFloored(targetBounds.maxX, targetBounds.maxY, targetBounds.maxZ).getX();
        int maxY = BlockPos.ofFloored(targetBounds.maxX, targetBounds.maxY, targetBounds.maxZ).getY();
        int maxZ = BlockPos.ofFloored(targetBounds.maxX, targetBounds.maxY, targetBounds.maxZ).getZ();
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    position.set(x, y, z);
                    var state = world.getBlockState(position);
                    var shape = state.getCollisionShape(world, position);
                    boolean hasShape = !shape.isEmpty();
                    Box collisionBounds = hasShape
                            ? shape.getBoundingBox().offset(x, y, z)
                            : new Box(x, y, z, x, y, z);
                    result.add(new NativeCycleSnapshot.PhaseBlock(
                            new NativeCycleSnapshot.Position(x, y, z), state.isAir(),
                            state.isReplaceable(), hasShape, toNative(collisionBounds)));
                }
            }
        }
        return result;
    }

    static boolean canPlaceNow(MinecraftClient client, ClientPlayerEntity player, BlockPos base,
            int ignoredEntityId) {
        var world = client.world;
        if (world == null) return false;
        var baseState = world.getBlockState(base);
        if (!baseState.isOf(Blocks.OBSIDIAN) && !baseState.isOf(Blocks.BEDROCK)) return false;
        if (!world.getBlockState(base.up()).isAir()) return false;
        Box crystalSpace = new Box(base.getX(), base.getY() + 1, base.getZ(),
                base.getX() + 1, base.getY() + 3, base.getZ() + 1);
        return !player.getBoundingBox().intersects(crystalSpace)
                && world.getOtherEntities(player, crystalSpace,
                        entity -> entity.getId() != ignoredEntityId).isEmpty();
    }

    static boolean hasCrystalAt(MinecraftClient client, ClientPlayerEntity player, BlockPos base) {
        if (client.world == null) return false;
        Box search = new Box(base.getX(), base.getY() + 1, base.getZ(),
                base.getX() + 1, base.getY() + 3, base.getZ() + 1);
        return client.world.getOtherEntities(player, search).stream()
                .anyMatch(entity -> entity instanceof EndCrystalEntity);
    }

    static net.minecraft.util.hit.BlockHitResult placementHitResult(
            net.minecraft.client.world.ClientWorld world,
            ClientPlayerEntity player,
            BlockPos base) {
        Vec3d eyes = player.getEyePos();
        net.minecraft.util.hit.BlockHitResult bestVisible = null;
        net.minecraft.util.hit.BlockHitResult bestThroughWall = null;
        double bestVisibleDistance = Double.MAX_VALUE;
        double bestWallDistance = Double.MAX_VALUE;
        for (Direction face : Direction.values()) {
            if (!isStrictFace(base, eyes, face)) continue;
            for (double[] sample : FACE_SAMPLES) {
                Vec3d hitPosition = facePoint(base, face, sample[0], sample[1]);
                double distanceSquared = eyes.squaredDistanceTo(hitPosition);
                if (distanceSquared > PLACE_RANGE_SQUARED) continue;
                var candidate = new net.minecraft.util.hit.BlockHitResult(hitPosition, face, base, false);
                Vec3d inward = new Vec3d(-face.getOffsetX(), -face.getOffsetY(), -face.getOffsetZ()).multiply(1.0E-4);
                var trace = world.raycast(new RaycastContext(eyes, hitPosition.add(inward),
                        RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
                if (trace.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK
                        && trace.getBlockPos().equals(base) && trace.getSide() == face) {
                    if (distanceSquared < bestVisibleDistance) {
                        bestVisible = candidate;
                        bestVisibleDistance = distanceSquared;
                    }
                } else if (distanceSquared <= WALL_RANGE_SQUARED && distanceSquared < bestWallDistance) {
                    bestThroughWall = candidate;
                    bestWallDistance = distanceSquared;
                }
            }
        }
        return bestVisible != null ? bestVisible : bestThroughWall;
    }

    private static boolean isWithinPlacementRange(ClientPlayerEntity player, BlockPos base) {
        Vec3d eyes = player.getEyePos();
        double dx = Math.max(Math.max(base.getX() - eyes.x, 0.0), eyes.x - base.getX() - 1.0);
        double dy = Math.max(Math.max(base.getY() - eyes.y, 0.0), eyes.y - base.getY() - 1.0);
        double dz = Math.max(Math.max(base.getZ() - eyes.z, 0.0), eyes.z - base.getZ() - 1.0);
        return dx * dx + dy * dy + dz * dz <= PLACE_RANGE_SQUARED;
    }

    private static boolean isStrictFace(BlockPos base, Vec3d eyes, Direction face) {
        double epsilon = 1.0E-5;
        return switch (face) {
            case DOWN -> eyes.y <= base.getY() + epsilon;
            case UP -> eyes.y >= base.getY() + 1.0 - epsilon;
            case NORTH -> eyes.z <= base.getZ() + epsilon;
            case SOUTH -> eyes.z >= base.getZ() + 1.0 - epsilon;
            case WEST -> eyes.x <= base.getX() + epsilon;
            case EAST -> eyes.x >= base.getX() + 1.0 - epsilon;
        };
    }

    private static Vec3d facePoint(BlockPos base, Direction face, double first, double second) {
        double x = base.getX();
        double y = base.getY();
        double z = base.getZ();
        return switch (face) {
            case DOWN -> new Vec3d(x + first, y, z + second);
            case UP -> new Vec3d(x + first, y + 1.0, z + second);
            case NORTH -> new Vec3d(x + first, y + second, z);
            case SOUTH -> new Vec3d(x + first, y + second, z + 1.0);
            case WEST -> new Vec3d(x, y + second, z + first);
            case EAST -> new Vec3d(x + 1.0, y + second, z + first);
        };
    }

    static Hand crystalHand(ClientPlayerEntity player) {
        if (player.getOffHandStack().isOf(Items.END_CRYSTAL)) return Hand.OFF_HAND;
        if (player.getMainHandStack().isOf(Items.END_CRYSTAL)) return Hand.MAIN_HAND;
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getStack(slot).isOf(Items.END_CRYSTAL)) return Hand.MAIN_HAND;
        }
        return null;
    }

    private static NativeCycleSnapshot.Vec3 toNative(Vec3d value) {
        return new NativeCycleSnapshot.Vec3(value.x, value.y, value.z);
    }

    private static NativeCycleSnapshot.Position toPosition(BlockPos value) {
        return new NativeCycleSnapshot.Position(value.getX(), value.getY(), value.getZ());
    }

    private static int resistanceAmplifier(PlayerEntity player) {
        var resistance = player.getStatusEffect(StatusEffects.RESISTANCE);
        return resistance == null ? -1 : resistance.getAmplifier();
    }

    private static void appendCollisionBoxes(
            net.minecraft.client.world.ClientWorld world,
            BlockPos center,
            List<NativeCycleSnapshot.Collision> output,
            Set<BlockPos> sampledBlocks) {
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int y = center.getY() - COLLISION_RADIUS; y <= center.getY() + COLLISION_RADIUS; y++) {
            for (int z = center.getZ() - COLLISION_RADIUS; z <= center.getZ() + COLLISION_RADIUS; z++) {
                for (int x = center.getX() - COLLISION_RADIUS; x <= center.getX() + COLLISION_RADIUS; x++) {
                    mutable.set(x, y, z);
                    BlockPos position = mutable.toImmutable();
                    if (!sampledBlocks.add(position)) continue;
                    var shape = world.getBlockState(position).getCollisionShape(world, position);
                    for (Box box : shape.getBoundingBoxes()) {
                        output.add(new NativeCycleSnapshot.Collision(
                                toPosition(position), toNative(box.offset(x, y, z))));
                    }
                }
            }
        }
    }

    private static NativeCycleSnapshot.Box toNative(Box value) {
        return new NativeCycleSnapshot.Box(value.minX, value.minY, value.minZ,
                value.maxX, value.maxY, value.maxZ);
    }
}
