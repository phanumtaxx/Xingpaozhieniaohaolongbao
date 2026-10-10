#include "pch.h"
#include "CrystalPlacementCycleEngine.h"
#include <algorithm>
#include <cstdint>
#include <cstring>

namespace xing { namespace autocrystalcyclebridge {
using namespace xing::autocrystal;
namespace {
constexpr int EVENT_PLAN = 0;
constexpr int EVENT_BREAK = 1;
constexpr int EVENT_PLACEMENT = 2;
constexpr int STATUS_OK = 0;
constexpr int STATUS_BAD_EVENT = -1;
constexpr int STATUS_BAD_INPUT = -2;
constexpr int STATUS_BAD_OUTPUT = -3;
constexpr int VERSION = 5;
constexpr std::size_t HEADER_BYTES = 368;
constexpr std::size_t BREAK_TARGET_BYTES = 128;
constexpr std::size_t BLOCK_BYTES = 16;
constexpr std::size_t ENTITY_BYTES = 56;
constexpr std::size_t COLLISION_BYTES = 64;
constexpr std::size_t POSITION_BYTES = 12;
constexpr std::size_t CRYSTAL_BYTES = 56;
constexpr std::size_t PHASE_BLOCK_BYTES = 64;
constexpr std::size_t OUTPUT_HEADER_BYTES = 48;
constexpr std::size_t OUTPUT_PLACEMENT_BYTES = 48;
constexpr std::size_t MAX_RECORDS = 65536;
constexpr std::size_t MAX_COLLISION_RECORDS = 131072;

template<typename T>
T read(const std::uint8_t* bytes, std::size_t offset) {
    T value{};
    std::memcpy(&value, bytes + offset, sizeof(T));
    return value;
}

void write(std::uint8_t* bytes, std::size_t offset, const void* value, std::size_t size) {
    std::memcpy(bytes + offset, value, size);
}

double readDouble(const std::uint8_t* bytes, std::size_t offset) {
    return read<double>(bytes, offset);
}

Vector3 readVector(const std::uint8_t* bytes, std::size_t offset) {
    return {readDouble(bytes, offset), readDouble(bytes, offset + 8), readDouble(bytes, offset + 16)};
}

BoundingBox readBounds(const std::uint8_t* bytes, std::size_t offset) {
    return {readDouble(bytes, offset), readDouble(bytes, offset + 8), readDouble(bytes, offset + 16),
            readDouble(bytes, offset + 24), readDouble(bytes, offset + 32), readDouble(bytes, offset + 40)};
}

BlockPosition readPosition(const std::uint8_t* bytes, std::size_t offset) {
    return {read<std::int32_t>(bytes, offset), read<std::int32_t>(bytes, offset + 4),
            read<std::int32_t>(bytes, offset + 8)};
}

bool validCounts(std::int32_t blocks, std::int32_t entities, std::int32_t collisions, std::int32_t reachable) {
    return blocks >= 0 && entities >= 0 && collisions >= 0 && reachable >= 0
            && static_cast<std::size_t>(blocks) <= MAX_RECORDS
            && static_cast<std::size_t>(entities) <= MAX_RECORDS
            && static_cast<std::size_t>(collisions) <= MAX_COLLISION_RECORDS
            && static_cast<std::size_t>(reachable) <= MAX_RECORDS;
}
}

int dispatch(int event, const std::uint8_t* input, std::size_t inputLen,
             std::uint8_t* output, std::size_t outputLen) {
    if (event != EVENT_PLAN && event != EVENT_BREAK && event != EVENT_PLACEMENT) return STATUS_BAD_EVENT;
    if (input == nullptr || inputLen < HEADER_BYTES || read<std::int32_t>(input, 0) != VERSION) {
        return STATUS_BAD_INPUT;
    }

    const std::int32_t blockCount = read<std::int32_t>(input, 24);
    const std::int32_t entityCount = read<std::int32_t>(input, 28);
    const std::int32_t collisionCount = read<std::int32_t>(input, 32);
    const std::int32_t reachableCount = read<std::int32_t>(input, 36);
    const std::int32_t crystalCount = read<std::int32_t>(input, 296);
    const std::int32_t phaseBlockCount = read<std::int32_t>(input, 312);
    const std::int32_t breakTargetCount = read<std::int32_t>(input, 352);
    if (breakTargetCount < 0 || breakTargetCount > 4) return STATUS_BAD_INPUT;
    if (!validCounts(blockCount, entityCount, collisionCount, reachableCount)) return STATUS_BAD_INPUT;
    if (crystalCount < 0 || phaseBlockCount < 0
            || static_cast<std::size_t>(crystalCount) > MAX_RECORDS
            || static_cast<std::size_t>(phaseBlockCount) > MAX_RECORDS) return STATUS_BAD_INPUT;
    const std::size_t expectedInput = HEADER_BYTES
            + static_cast<std::size_t>(blockCount) * BLOCK_BYTES
            + static_cast<std::size_t>(entityCount) * ENTITY_BYTES
            + static_cast<std::size_t>(collisionCount) * COLLISION_BYTES
            + static_cast<std::size_t>(reachableCount) * POSITION_BYTES
            + static_cast<std::size_t>(crystalCount) * CRYSTAL_BYTES
            + static_cast<std::size_t>(phaseBlockCount) * PHASE_BLOCK_BYTES
            + static_cast<std::size_t>(breakTargetCount) * BREAK_TARGET_BYTES;
    if (inputLen != expectedInput) return STATUS_BAD_INPUT;
    if (output == nullptr || outputLen < OUTPUT_HEADER_BYTES) return STATUS_BAD_OUTPUT;

    const std::uint32_t flags = read<std::uint32_t>(input, 4);
    CrystalPlacementCycleInput cycle;
    cycle.clientReady = (flags & 1U) != 0;
    cycle.hasCrystal = (flags & 2U) != 0;
    cycle.hasPlacementHand = (flags & 4U) != 0;
    cycle.placementWorld.hasWorld = (flags & 8U) != 0;
    cycle.hasPendingPlacement = (flags & 16U) != 0;
    cycle.placementWorld.requireTwoBlockSpace = (flags & 32U) != 0;
    const std::uint32_t phaseFlags = read<std::uint32_t>(input, 316);
    cycle.antiPhaseEnabled = (phaseFlags & 1U) != 0;
    cycle.placementWorld.hasSimulatedAirBlock = (phaseFlags & 2U) != 0;
    cycle.minimumPhaseOverlapDepth = readDouble(input, 320);
    cycle.antiPhaseRadius = read<std::int32_t>(input, 328);
    cycle.placementWorld.simulatedAirBlock = readPosition(input, 332);
    cycle.scoringSettings.antiPhaseBoost = 1.5;
    cycle.scoringSettings.packetMineOpeningWeight = readDouble(input, 344);
    cycle.targetSelection.hasPlayer = cycle.clientReady;
    cycle.targetSelection.hasWorld = cycle.placementWorld.hasWorld;
    cycle.targetSelection.localEntityId = read<std::int32_t>(input, 8);
    cycle.targetSelection.localPosition = readVector(input, 48);
    cycle.worldGeneration = read<std::uint64_t>(input, 40);
    cycle.placePredictionTicks = read<std::int32_t>(input, 288);
    cycle.fullPredictionTicks = read<std::int32_t>(input, 292);
    const std::uint32_t breakFlags = read<std::uint32_t>(input, 300);
    cycle.breakExistingEnabled = event != EVENT_PLACEMENT && (breakFlags & 1U) != 0;
    cycle.sameTickBreakPlace = (breakFlags & 2U) != 0;
    cycle.breakSelection.breakRange = readDouble(input, 304);
    cycle.breakAction.clientReady = cycle.clientReady;
    cycle.breakAction.reservationAccepted = true;
    cycle.breakAction.immediateBreak = true;
    cycle.placementWorld.radius = read<std::int32_t>(input, 16);
    cycle.scanInterval = (std::max)(1, read<std::int32_t>(input, 20));
    cycle.scoringSettings.minimumTargetDamage = readDouble(input, 248);
    cycle.scoringSettings.maximumSelfDamage = readDouble(input, 256);
    cycle.breakSelection.maximumSelfDamage = cycle.scoringSettings.maximumSelfDamage;
    cycle.scoringSettings.targetDamageWeight = readDouble(input, 264);
    cycle.scoringSettings.selfDamageWeight = readDouble(input, 272);
    cycle.scoringSettings.selfHealth = readDouble(input, 216);
    cycle.placementWorld.ignoredEntityId = cycle.sameTickBreakPlace
            ? read<std::int32_t>(input, 364) : -1;
    cycle.scoringSettings.selfPosition = cycle.targetSelection.localPosition;

    const int targetId = read<std::int32_t>(input, 12);
    PlayerSnapshot target;
    target.entityId = targetId;
    target.position = readVector(input, 72);
    target.isAlive = true;
    cycle.targetSelection.playersInWorldOrder.push_back(target);

    CrystalCycleTargetData targetData;
    targetData.entityId = targetId;
    targetData.instanceToken = static_cast<std::uint64_t>(static_cast<std::uint32_t>(targetId));
    targetData.previousPosition = readVector(input, 96);
    targetData.bounds = readBounds(input, 120);
    targetData.damageInput.hasEntity = true;
    targetData.damageInput.hasWorld = cycle.placementWorld.hasWorld;
    targetData.damageInput.entityPosition = target.position;
    targetData.damageInput.entityBounds = targetData.bounds;
    targetData.damageInput.armorValue = read<std::int32_t>(input, 224);
    targetData.damageInput.armorToughness = static_cast<float>(readDouble(input, 232));
    targetData.damageInput.resistanceAmplifier = read<std::int32_t>(input, 280);
    targetData.damageInput.hasResistance = targetData.damageInput.resistanceAmplifier >= 0;
    cycle.targetData.push_back(targetData);
    cycle.placementWorld.targetPosition = target.position;
    cycle.scoringSettings.selfDamageInput.hasEntity = true;
    cycle.scoringSettings.selfDamageInput.hasWorld = cycle.placementWorld.hasWorld;
    cycle.scoringSettings.selfDamageInput.entityPosition = cycle.targetSelection.localPosition;
    cycle.scoringSettings.selfDamageInput.entityBounds = readBounds(input, 168);
    cycle.scoringSettings.selfDamageInput.armorValue = read<std::int32_t>(input, 228);
    cycle.scoringSettings.selfDamageInput.armorToughness = static_cast<float>(readDouble(input, 240));
    cycle.scoringSettings.selfDamageInput.resistanceAmplifier = read<std::int32_t>(input, 284);
    cycle.scoringSettings.selfDamageInput.hasResistance = cycle.scoringSettings.selfDamageInput.resistanceAmplifier >= 0;

    std::size_t offset = HEADER_BYTES;
    for (int i = 0; i < blockCount; ++i, offset += BLOCK_BYTES) {
        PlacementBlockSnapshot block;
        block.position = readPosition(input, offset);
        const auto kind = read<std::int32_t>(input, offset + 12);
        if (kind < 0 || kind > static_cast<std::int32_t>(CrystalBlockKind::Bedrock)) return STATUS_BAD_INPUT;
        block.kind = static_cast<CrystalBlockKind>(kind);
        cycle.placementWorld.blocks.push_back(block);
    }
    for (int i = 0; i < entityCount; ++i, offset += ENTITY_BYTES) {
        PlacementEntitySnapshot entity;
        entity.entityId = read<std::int32_t>(input, offset);
        entity.bounds = readBounds(input, offset + 8);
        cycle.placementWorld.entities.push_back(entity);
    }
    std::vector<RayCollisionBox> collisionBoxes;
    collisionBoxes.reserve(static_cast<std::size_t>(collisionCount));
    for (int i = 0; i < collisionCount; ++i, offset += COLLISION_BYTES) {
        RayCollisionBox box;
        box.blockPosition = readPosition(input, offset);
        box.bounds = readBounds(input, offset + 16);
        collisionBoxes.push_back(box);
    }
    targetData.movementCollisionBoxes.reserve(collisionBoxes.size());
    for (const RayCollisionBox& box : collisionBoxes) {
        targetData.movementCollisionBoxes.push_back(box.bounds);
    }
    cycle.targetData.front().movementCollisionBoxes = targetData.movementCollisionBoxes;
    cycle.targetData.front().damageCollisionBoxes = collisionBoxes;
    cycle.scoringSettings.targetCollisionBoxes = collisionBoxes;
    cycle.scoringSettings.selfCollisionBoxes = std::move(collisionBoxes);
    cycle.breakSelection.selfDamageInput = cycle.scoringSettings.selfDamageInput;
    cycle.breakSelection.selfCollisionBoxes = cycle.scoringSettings.selfCollisionBoxes;
    cycle.breakAction.hasPreferredBase = false;
    for (int i = 0; i < reachableCount; ++i, offset += POSITION_BYTES) {
        const BlockPosition position = readPosition(input, offset);
        cycle.reachableBasesInWorldOrder.push_back(position);
        cycle.aimValidBasesInWorldOrder.push_back(position);
    }
    for (int i = 0; i < crystalCount; ++i, offset += CRYSTAL_BYTES) {
        BreakCrystalSnapshot crystal;
        crystal.entityId = read<std::int32_t>(input, offset);
        const std::uint32_t crystalFlags = read<std::uint32_t>(input, offset + 4);
        crystal.isAlive = (crystalFlags & 1U) != 0;
        crystal.aimValid = (crystalFlags & 2U) != 0;
        crystal.attackCoolingDown = (crystalFlags & 4U) != 0;
        crystal.blockPosition = readPosition(input, offset + 8);
        crystal.position = readVector(input, offset + 24);
        crystal.playerDistanceSquared = readDouble(input, offset + 48);
        cycle.breakSelection.crystalsInWorldOrder.push_back(crystal);
    }
    for (int i = 0; i < phaseBlockCount; ++i, offset += PHASE_BLOCK_BYTES) {
        PhaseBlockSnapshot block;
        block.position = readPosition(input, offset);
        const std::uint32_t blockFlags = read<std::uint32_t>(input, offset + 12);
        block.isAir = (blockFlags & 1U) != 0;
        block.canBeReplaced = (blockFlags & 2U) != 0;
        block.hasCollisionShape = (blockFlags & 4U) != 0;
        block.collisionBounds = readBounds(input, offset + 16);
        cycle.phaseBlocksInWorldOrder.push_back(block);
    }
    for (int i = 0; i < breakTargetCount; ++i, offset += BREAK_TARGET_BYTES) {
        BreakTargetSnapshot breakTarget;
        breakTarget.entityId = read<std::int32_t>(input, offset);
        breakTarget.isAlive = true;
        breakTarget.minimumDamage = readDouble(input, offset + 120);
        breakTarget.damageInput.hasEntity = true;
        breakTarget.damageInput.hasWorld = cycle.placementWorld.hasWorld;
        breakTarget.damageInput.armorValue = read<std::int32_t>(input, offset + 4);
        breakTarget.damageInput.armorToughness = static_cast<float>(readDouble(input, offset + 104));
        breakTarget.damageInput.resistanceAmplifier = read<std::int32_t>(input, offset + 112);
        breakTarget.damageInput.hasResistance = breakTarget.damageInput.resistanceAmplifier >= 0;
        CrystalPredictionInput prediction;
        prediction.hasPlayer = true;
        prediction.position = readVector(input, offset + 8);
        prediction.previousPosition = readVector(input, offset + 32);
        prediction.boundingBox = readBounds(input, offset + 56);
        prediction.ticks = read<std::int32_t>(input, 360);
        prediction.blockCollisionBoxes = targetData.movementCollisionBoxes;
        const auto predicted = CrystalPrediction::predict(prediction);
        breakTarget.damageInput.entityPosition = predicted.position;
        breakTarget.damageInput.entityBounds = predicted.boundingBox;
        breakTarget.collisionBoxes = cycle.scoringSettings.selfCollisionBoxes;
        cycle.breakSelection.targetsInCallerOrder.push_back(std::move(breakTarget));
    }

    static CrystalPlacementCycleEngine engine;
    CrystalPlacementCyclePlan plan;
    if (event == EVENT_BREAK) {
        const auto selection = cycle.breakExistingEnabled
                ? CrystalBreakSelector::select(cycle.breakSelection) : CrystalBreakSelection{};
        plan.earlyBreakPlan = CrystalBreakActionPlanner::plan(selection, cycle.breakAction);
        plan.hasEarlyBreakPlan = plan.earlyBreakPlan.action != CrystalBreakPlanAction::None;
        plan.placementRequiresSuccessfulSameTickBreak = plan.hasEarlyBreakPlan && cycle.sameTickBreakPlace;
        if (cycle.hasPendingPlacement) plan.actionPlan.reason = CrystalPlacementPlanReason::PlacementPending;
        plan.status = plan.hasEarlyBreakPlan ? CrystalPlacementCycleStatus::EarlyBreakReady
                : CrystalPlacementCycleStatus::NoPlacement;
    } else {
        plan = engine.plan(cycle);
    }
    const std::size_t placementCount = std::min<std::size_t>(plan.placements.size(), 64);
    const std::size_t requiredOutput = OUTPUT_HEADER_BYTES + placementCount * OUTPUT_PLACEMENT_BYTES;
    if (outputLen < requiredOutput) return STATUS_BAD_OUTPUT;
    std::memset(output, 0, requiredOutput);
    const std::int32_t status = static_cast<std::int32_t>(plan.status);
    const std::int32_t action = static_cast<std::int32_t>(plan.actionPlan.action);
    const std::int32_t count = static_cast<std::int32_t>(placementCount);
    write(output, 0, &status, sizeof(status));
    write(output, 4, &action, sizeof(action));
    write(output, 8, &count, sizeof(count));
    const std::int32_t breakAction = static_cast<std::int32_t>(plan.earlyBreakPlan.action);
    const std::int32_t breakCrystalId = plan.earlyBreakPlan.crystalEntityId;
    const std::int32_t breakTargetId = plan.earlyBreakPlan.targetEntityId;
    const std::int32_t sameTickRequired = plan.placementRequiresSuccessfulSameTickBreak ? 1 : 0;
    const std::int32_t breakReason = static_cast<std::int32_t>(plan.earlyBreakPlan.reason);
    write(output, 12, &breakAction, sizeof(breakAction));
    write(output, 16, &breakCrystalId, sizeof(breakCrystalId));
    write(output, 20, &breakTargetId, sizeof(breakTargetId));
    write(output, 24, &sameTickRequired, sizeof(sameTickRequired));
    write(output, 28, &breakReason, sizeof(breakReason));
    const std::int32_t placementReason = static_cast<std::int32_t>(plan.actionPlan.reason);
    write(output, 32, &placementReason, sizeof(placementReason));
    for (std::size_t i = 0; i < placementCount; ++i) {
        const CrystalPlacementScore& placement = plan.placements[i];
        const std::size_t base = OUTPUT_HEADER_BYTES + i * OUTPUT_PLACEMENT_BYTES;
        const std::int32_t x = placement.placement.basePosition.x;
        const std::int32_t y = placement.placement.basePosition.y;
        const std::int32_t z = placement.placement.basePosition.z;
        write(output, base, &x, 4); write(output, base + 4, &y, 4); write(output, base + 8, &z, 4);
        write(output, base + 16, &placement.score, 8);
        write(output, base + 24, &placement.targetDamage, 8);
        write(output, base + 32, &placement.selfDamage, 8);
    }
    return STATUS_OK;
}
} }

