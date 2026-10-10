package dev.xingclient.module;

import dev.xingclient.nativebridge.NativeCycleSnapshot;
import dev.xingclient.XingClient;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
    enum Stage { BREAK, PLACEMENT }
    private static final int COLLISION_RADIUS = 8;
    private static final int MAX_BREAK_TARGETS = 4;
    private static final double[][] FACE_SAMPLES = {
            {0.5, 0.5}, {0.1, 0.1}, {0.1, 0.9}, {0.9, 0.1}, {0.9, 0.9}
    };

    private NativeCycleSnapshotFactory() {}

    static NativeCycleSnapshot capture(
            MinecraftClient client, ClientPlayerEntity player, boolean hasPendingPlacement,
            int onlyCrystalEntityId, int preferredTargetId, Stage stage) {
        var world = client.world;
        var settings = XingClient.INSTANCE.settings.autoCrystal;
        int replacementCrystalId = stage == Stage.PLACEMENT
                ? XingClient.INSTANCE.managers.crystalActions.replacementCrystalId(settings.sameTickBreakPlace) : -1;
        var targets = world.getPlayers().stream()
                .filter(candidate -> candidate != player && candidate.isAlive() && !candidate.isSpectator())
                .filter(candidate -> XingClient.INSTANCE == null || XingClient.INSTANCE.friends == null
                        || !XingClient.INSTANCE.friends.isFriend(candidate.getName().getString()))
                .sorted(Comparator.comparingDouble(player::squaredDistanceTo)).toList();
        PlayerEntity target = targets.stream().filter(candidate -> candidate.getId() == preferredTargetId)
                .findFirst().orElse(targets.isEmpty() ? null : targets.getFirst());
        if (target == null) return null;

        Vec3d localPosition = player.getPos();
        Vec3d targetPosition = target.getPos();
        BlockPos targetBlock = target.getBlockPos();
        List<NativeCycleSnapshot.Block> blocks = new ArrayList<>();
        List<NativeCycleSnapshot.Collision> collisionBoxes = new ArrayList<>();
        List<NativeCycleSnapshot.PhaseBlock> phaseBlocks = stage == Stage.BREAK ? List.of()
                : phaseBlocks(world, target.getBoundingBox());
        List<NativeCycleSnapshot.Position> reachable = new ArrayList<>();
        if (stage == Stage.PLACEMENT) {
            appendPlacementBlocks(client, player, targetBlock, settings.scanRadius,
                    replacementCrystalId, blocks, reachable);
        }

        Box entitySearch = new Box(targetBlock).expand(settings.scanRadius + 2.0);
        List<NativeCycleSnapshot.Entity> entities = new ArrayList<>();
        if (stage == Stage.PLACEMENT) {
            entities.add(new NativeCycleSnapshot.Entity(player.getId(), toNative(player.getBoundingBox())));
        }
        List<NativeCycleSnapshot.Crystal> crystals = new ArrayList<>();
        dev.xingclient.MiningSyncState.Opening mineOpening = XingClient.INSTANCE == null
                ? null : XingClient.INSTANCE.miningSync.eligibleOpening();
        Box search = stage == Stage.BREAK
                ? player.getBoundingBox().expand(settings.breakRange) : entitySearch;
        for (Entity entity : world.getOtherEntities(player, search)) {
            if (stage == Stage.PLACEMENT) {
                entities.add(new NativeCycleSnapshot.Entity(entity.getId(), toNative(entity.getBoundingBox())));
            }
            if (stage == Stage.BREAK && settings.breakExisting
                    && entity instanceof EndCrystalEntity crystal
                    && player.squaredDistanceTo(crystal) <= settings.breakRange * settings.breakRange
                    && (onlyCrystalEntityId < 0 || crystal.getId() == onlyCrystalEntityId)) {
                crystals.add(new NativeCycleSnapshot.Crystal(
                        crystal.getId(), toPosition(crystal.getBlockPos()), toNative(crystal.getPos()),
                        crystal.isAlive(), true, XingClient.INSTANCE.managers.crystalActions.attackCoolingDown(crystal.getId()),
                        player.squaredDistanceTo(crystal)));
            }
        }

        List<? extends PlayerEntity> breakPlayers = stage == Stage.PLACEMENT ? List.of()
                : onlyCrystalEntityId >= 0 ? List.of(target) : targets.stream().limit(MAX_BREAK_TARGETS).toList();
        List<NativeCycleSnapshot.BreakTarget> breakTargets = new ArrayList<>(breakPlayers.size());
        Set<BlockPos> sampledCollisionBlocks = new HashSet<>();
        if (stage == Stage.PLACEMENT || !crystals.isEmpty()) {
            appendCollisionBoxes(world, targetBlock, collisionBoxes, sampledCollisionBlocks);
            appendCollisionBoxes(world, player.getBlockPos(), collisionBoxes, sampledCollisionBlocks);
            for (PlayerEntity breakPlayer : breakPlayers) {
                appendCollisionBoxes(world, breakPlayer.getBlockPos(), collisionBoxes, sampledCollisionBlocks);
            }
        }
        for (PlayerEntity breakPlayer : breakPlayers) {
            double requiredDamage = settings.facePlace && breakPlayer.getHealth() <= settings.lowHealthThreshold
                    ? settings.lowHealthMinimumDamage : settings.minimumDamage;
            breakTargets.add(new NativeCycleSnapshot.BreakTarget(
                    breakPlayer.getId(), breakPlayer.getArmor(), toNative(breakPlayer.getPos()),
                    new NativeCycleSnapshot.Vec3(breakPlayer.lastX, breakPlayer.lastY, breakPlayer.lastZ),
                    toNative(breakPlayer.getBoundingBox()),
                    breakPlayer.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS),
                    resistanceAmplifier(breakPlayer), requiredDamage));
        }
        double minimumDamage = settings.facePlace && target.getHealth() <= settings.lowHealthThreshold
                ? settings.lowHealthMinimumDamage : settings.minimumDamage;

        return new NativeCycleSnapshot(
                player.getId(), target.getId(), settings.scanRadius, settings.scanInterval,
                Integer.toUnsignedLong(world.getRegistryKey().hashCode()),
                toNative(localPosition), toNative(targetPosition),
                new NativeCycleSnapshot.Vec3(target.lastX, target.lastY, target.lastZ),
                toNative(target.getBoundingBox()), toNative(player.getBoundingBox()),
                player.getHealth() + player.getAbsorptionAmount(), target.getArmor(), player.getArmor(),
                target.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS),
                player.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS),
                resistanceAmplifier(target), resistanceAmplifier(player),
                minimumDamage, settings.maximumSelfDamage, settings.targetWeight, settings.safetyWeight,
                true, NativeAutoCrystalModule.hasCrystal(player), crystalHand(player) != null, true,
                hasPendingPlacement, settings.strictPlacementSpace, stage == Stage.BREAK && settings.breakExisting,
                settings.sameTickBreakPlace, settings.breakRange,
                blocks, entities, collisionBoxes, reachable, crystals,
                true, 0.12, 2, phaseBlocks,
                mineOpening == null ? null : toPosition(mineOpening.position()),
                mineOpening == null ? 0.0 : mineOpening.scoreWeight(),
                settings.predictMovement ? settings.placePredictionTicks : 0,
                settings.predictMovement ? settings.fullPredictionTicks : 0, breakTargets,
                settings.predictMovement ? settings.breakPredictionTicks : 0, replacementCrystalId);
    }

    private static void appendPlacementBlocks(MinecraftClient client,
            ClientPlayerEntity player, BlockPos center, int radius, int replacementCrystalId,
            List<NativeCycleSnapshot.Block> blocks,
            List<NativeCycleSnapshot.Position> reachable) {
        var world = client.world;
        BlockPos.Mutable position = new BlockPos.Mutable();
        for (int y = center.getY() - radius; y <= center.getY() + radius; y++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
                    position.set(x, y, z);
                    var state = world.getBlockState(position);
                    int kind = state.isAir() ? 1 : state.isOf(Blocks.OBSIDIAN) ? 2 : state.isOf(Blocks.BEDROCK) ? 3 : 0;
                    var nativePosition = new NativeCycleSnapshot.Position(x, y, z);
                    blocks.add(new NativeCycleSnapshot.Block(nativePosition, kind));
                    if ((kind == 2 || kind == 3) && isWithinPlacementRange(player, position)
                            && canPlaceNow(client, player, position, replacementCrystalId)
                            && placementHitResult(world, player, position) != null) {
                        reachable.add(nativePosition);
                    }
                }
            }
        }
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
            int replacementCrystalId) {
        var world = client.world;
        if (world == null) return false;
        var baseState = world.getBlockState(base);
        if (!baseState.isOf(Blocks.OBSIDIAN) && !baseState.isOf(Blocks.BEDROCK)) return false;
        if (!world.getBlockState(base.up()).isAir()) return false;
        if (XingClient.INSTANCE.settings.autoCrystal.strictPlacementSpace
                && !world.getBlockState(base.up(2)).isAir()) return false;
        Box crystalSpace = new Box(base.getX(), base.getY() + 1, base.getZ(),
                base.getX() + 1, base.getY() + 3, base.getZ() + 1);
        return !player.getBoundingBox().intersects(crystalSpace)
                && world.getOtherEntities(player, crystalSpace,
                        entity -> !(entity instanceof EndCrystalEntity) || entity.getId() != replacementCrystalId).isEmpty();
    }

    static net.minecraft.util.hit.BlockHitResult placementHitResult(
            net.minecraft.client.world.ClientWorld world,
            ClientPlayerEntity player,
            BlockPos base) {
        Vec3d eyes = player.getEyePos();
        var settings = XingClient.INSTANCE.settings.autoCrystal;
        double placeRangeSquared = settings.placeRange * settings.placeRange;
        double wallRange = Math.min(settings.placeRange, settings.wallsRange);
        double wallRangeSquared = wallRange * wallRange;
        net.minecraft.util.hit.BlockHitResult bestVisible = null;
        net.minecraft.util.hit.BlockHitResult bestThroughWall = null;
        double bestVisibleDistance = Double.MAX_VALUE;
        double bestWallDistance = Double.MAX_VALUE;
        for (Direction face : Direction.values()) {
            if (settings.strictDirection && !isStrictFace(base, eyes, face)) continue;
            for (double[] sample : FACE_SAMPLES) {
                Vec3d hitPosition = facePoint(base, face, sample[0], sample[1]);
                double distanceSquared = eyes.squaredDistanceTo(hitPosition);
                if (distanceSquared > placeRangeSquared) continue;
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
                } else if (distanceSquared <= wallRangeSquared && distanceSquared < bestWallDistance) {
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
        double range = XingClient.INSTANCE.settings.autoCrystal.placeRange;
        return dx * dx + dy * dy + dz * dz <= range * range;
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
        var settings = XingClient.INSTANCE.settings.autoCrystal;
        return XingClient.INSTANCE.managers.interactions.placementHand(
                stack -> stack.isOf(Items.END_CRYSTAL), settings.swapMode, settings.handMode);
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
