package dev.xingclient.nativebridge;

import java.nio.ByteBuffer;
import java.util.List;

/** Versioned snapshot shared with the native placement-cycle planner. */
public record NativeCycleSnapshot(
        int localEntityId,
        int targetEntityId,
        int scanRadius,
        int scanInterval,
        long worldGeneration,
        Vec3 localPosition,
        Vec3 targetPosition,
        Vec3 targetPreviousPosition,
        Box targetBounds,
        Box localBounds,
        double localHealth,
        int targetArmor,
        int localArmor,
        double targetArmorToughness,
        double localArmorToughness,
        int targetResistanceAmplifier,
        int localResistanceAmplifier,
        double minimumTargetDamage,
        double maximumSelfDamage,
        double targetDamageWeight,
        double selfDamageWeight,
        boolean clientReady,
        boolean hasCrystal,
        boolean hasPlacementHand,
        boolean hasWorld,
        boolean hasPendingPlacement,
        boolean strictPlacementSpace,
        boolean breakExistingEnabled,
        boolean sameTickBreakPlace,
        double breakRange,
        List<Block> blocks,
        List<Entity> entities,
        List<Collision> collisionBoxes,
        List<Position> reachableBases,
        List<Crystal> crystals,
        boolean antiPhaseEnabled,
        double minimumPhaseOverlapDepth,
        int antiPhaseRadius,
        List<PhaseBlock> phaseBlocks,
        Position simulatedAirBlock,
        double packetMineOpeningWeight,
        int placePredictionTicks,
        int fullPredictionTicks) {

    public static final int VERSION = 2;
    private static final int HEADER_BYTES = 352;

    public ByteBuffer encode() {
        ByteBuffer buffer = XingNativeBridge.cycleInputBuffer(HEADER_BYTES
                + blocks.size() * 16
                + entities.size() * 56
                + collisionBoxes.size() * 64
                + reachableBases.size() * 12
                + crystals.size() * 56
                + phaseBlocks.size() * 64);

        int flags = (clientReady ? 1 : 0)
                | (hasCrystal ? 2 : 0)
                | (hasPlacementHand ? 4 : 0)
                | (hasWorld ? 8 : 0)
                | (hasPendingPlacement ? 16 : 0)
                | (strictPlacementSpace ? 32 : 0);
        buffer.putInt(VERSION).putInt(flags);
        buffer.putInt(localEntityId).putInt(targetEntityId);
        buffer.putInt(scanRadius).putInt(scanInterval);
        buffer.putInt(blocks.size()).putInt(entities.size());
        buffer.putInt(collisionBoxes.size()).putInt(reachableBases.size());
        buffer.putLong(worldGeneration);
        putVec(buffer, localPosition);
        putVec(buffer, targetPosition);
        putVec(buffer, targetPreviousPosition);
        putBox(buffer, targetBounds);
        putBox(buffer, localBounds);
        buffer.putDouble(localHealth);
        buffer.putInt(targetArmor).putInt(localArmor);
        buffer.putDouble(targetArmorToughness).putDouble(localArmorToughness);
        buffer.putDouble(minimumTargetDamage).putDouble(maximumSelfDamage);
        buffer.putDouble(targetDamageWeight).putDouble(selfDamageWeight);
        buffer.putInt(targetResistanceAmplifier).putInt(localResistanceAmplifier);
        buffer.putInt(placePredictionTicks).putInt(fullPredictionTicks);
        buffer.putInt(crystals.size());
        int breakFlags = (breakExistingEnabled ? 1 : 0) | (sameTickBreakPlace ? 2 : 0);
        buffer.putInt(breakFlags).putDouble(breakRange);
        buffer.putInt(phaseBlocks.size());
        buffer.putInt((antiPhaseEnabled ? 1 : 0) | (simulatedAirBlock != null ? 2 : 0));
        buffer.putDouble(minimumPhaseOverlapDepth);
        buffer.putInt(antiPhaseRadius);
        putPosition(buffer, simulatedAirBlock == null ? new Position(0, 0, 0) : simulatedAirBlock);
        buffer.putDouble(packetMineOpeningWeight);
        if (buffer.position() != HEADER_BYTES) {
            throw new IllegalStateException("Native cycle header layout mismatch");
        }

        for (Block block : blocks) {
            putPosition(buffer, block.position);
            buffer.putInt(block.kind);
        }
        for (Entity entity : entities) {
            buffer.putInt(entity.entityId).putInt(0);
            putBox(buffer, entity.bounds);
        }
        for (Collision collision : collisionBoxes) {
            putPosition(buffer, collision.position);
            buffer.putInt(0);
            putBox(buffer, collision.bounds);
        }
        for (Position position : reachableBases) putPosition(buffer, position);
        for (Crystal crystal : crystals) {
            buffer.putInt(crystal.entityId);
            int crystalFlags = (crystal.alive ? 1 : 0) | (crystal.aimValid ? 2 : 0)
                    | (crystal.attackCoolingDown ? 4 : 0);
            buffer.putInt(crystalFlags);
            putPosition(buffer, crystal.blockPosition);
            putVec(buffer, crystal.position);
            buffer.putDouble(crystal.playerDistanceSquared);
            buffer.putInt(0);
        }
        for (PhaseBlock block : phaseBlocks) {
            putPosition(buffer, block.position);
            int phaseFlags = (block.isAir ? 1 : 0) | (block.replaceable ? 2 : 0)
                    | (block.hasCollisionShape ? 4 : 0);
            buffer.putInt(phaseFlags);
            putBox(buffer, block.collisionBounds);
        }
        return buffer.flip();
    }

    public static NativeCyclePlan decode(ByteBuffer output) {
        output.rewind();
        int status = output.getInt();
        int action = output.getInt();
        int count = output.getInt();
        int breakAction = output.getInt();
        int breakCrystalId = output.getInt();
        int breakTargetId = output.getInt();
        boolean placeAfterBreak = output.getInt() != 0;
        int breakReason = output.getInt();
        if (count < 0 || count > 64
                || output.capacity() < XingNativeBridge.CYCLE_OUTPUT_HEADER_BYTES
                    + count * XingNativeBridge.CYCLE_OUTPUT_PLACEMENT_BYTES) {
            throw new IllegalArgumentException("Invalid native cycle output");
        }
        output.position(XingNativeBridge.CYCLE_OUTPUT_HEADER_BYTES);
        java.util.ArrayList<NativeCyclePlan.Placement> placements = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int x = output.getInt();
            int y = output.getInt();
            int z = output.getInt();
            output.getInt();
            double score = output.getDouble();
            double targetDamage = output.getDouble();
            double selfDamage = output.getDouble();
            output.position(output.position() + 8);
            placements.add(new NativeCyclePlan.Placement(new Position(x, y, z), score, targetDamage, selfDamage));
        }
        return new NativeCyclePlan(status, action, breakAction, breakCrystalId,
                breakTargetId, breakReason, placeAfterBreak, placements);
    }

    private static void putVec(ByteBuffer buffer, Vec3 value) {
        buffer.putDouble(value.x).putDouble(value.y).putDouble(value.z);
    }

    private static void putBox(ByteBuffer buffer, Box value) {
        buffer.putDouble(value.minX).putDouble(value.minY).putDouble(value.minZ)
                .putDouble(value.maxX).putDouble(value.maxY).putDouble(value.maxZ);
    }

    private static void putPosition(ByteBuffer buffer, Position value) {
        buffer.putInt(value.x).putInt(value.y).putInt(value.z);
    }

    public record Vec3(double x, double y, double z) {}
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}
    public record Position(int x, int y, int z) {}
    public record Block(Position position, int kind) {}
    public record Entity(int entityId, Box bounds) {}
    public record Collision(Position position, Box bounds) {}
    public record Crystal(int entityId, Position blockPosition, Vec3 position,
                          boolean alive, boolean aimValid, boolean attackCoolingDown,
                          double playerDistanceSquared) {}
    public record PhaseBlock(Position position, boolean isAir, boolean replaceable,
                             boolean hasCollisionShape, Box collisionBounds) {}
}
