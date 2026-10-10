#include "pch.h"
#include "CrystalPlacementCycleEngine.h"
#include <algorithm>
#include <cmath>

namespace xing { namespace autocrystal {

namespace {
const CrystalCycleTargetData* findTargetData(
        const std::vector<CrystalCycleTargetData>& targetData,
        int entityId) {
    const auto found = std::find_if(targetData.begin(), targetData.end(), [entityId](const CrystalCycleTargetData& candidate) {
        return candidate.entityId == entityId;
    });
    return found == targetData.end() ? nullptr : &*found;
}

CrystalPredictionSnapshot predictTarget(
        const PlayerSnapshot& target,
        const CrystalCycleTargetData& targetData,
        bool predictMovement,
        int ticks) {
    CrystalPredictionInput predictionInput;
    predictionInput.hasPlayer = true;
    predictionInput.position = target.position;
    predictionInput.previousPosition = targetData.previousPosition;
    predictionInput.boundingBox = targetData.bounds;
    predictionInput.ticks = predictMovement ? ticks : 0;
    predictionInput.blockCollisionBoxes = targetData.movementCollisionBoxes;
    return CrystalPrediction::predict(predictionInput);
}

bool isReachable(const std::vector<BlockPosition>& reachableBases, const BlockPosition& position) {
    return std::find(reachableBases.begin(), reachableBases.end(), position) != reachableBases.end();
}
}

CrystalPlacementCyclePlan CrystalPlacementCycleEngine::plan(const CrystalPlacementCycleInput& input) {
    CrystalPlacementCyclePlan result;
    if (input.hasLiveReservation
            || (input.packetReaction && !input.packetUpdateMode)
            || !input.clientReady
            || input.externalPause
            || input.setbackPause
            || input.hasPendingAction
            || input.actionCoolingDown) {
        return result;
    }

    if (!CrystalTargetSelector::nearestPlayer(input.targetSelection, result.target)) {
        scanCache_.clear();
        result.status = CrystalPlacementCycleStatus::NoTarget;
        return result;
    }
    result.hasTarget = true;

    const CrystalCycleTargetData* targetData = findTargetData(input.targetData, result.target.entityId);
    if (targetData == nullptr) {
        result.status = CrystalPlacementCycleStatus::MissingTargetData;
        return result;
    }

    const BoundingBox liveTargetBounds = targetData->bounds;
    result.phaseInfo = CrystalTargetPhaseDetector::detect(
            liveTargetBounds, input.minimumPhaseOverlapDepth, input.phaseBlocksInWorldOrder);
    result.placePrediction = predictTarget(
            result.target, *targetData, input.predictMovement, input.placePredictionTicks);
    result.fullPrediction = predictTarget(
            result.target, *targetData, input.predictMovement, input.fullPredictionTicks);

    if (input.breakExistingEnabled) {
        const CrystalBreakSelection breakSelection = CrystalBreakSelector::select(input.breakSelection);
        CrystalBreakActionInput breakAction = input.breakAction;
        breakAction.clientReady = input.clientReady;
        result.earlyBreakPlan = CrystalBreakActionPlanner::plan(breakSelection, breakAction);
        result.hasEarlyBreakPlan = result.earlyBreakPlan.action != CrystalBreakPlanAction::None;
        if (result.hasEarlyBreakPlan) {
            if (result.earlyBreakPlan.action == CrystalBreakPlanAction::StageBreak
                    || !input.sameTickBreakPlace) {
                result.status = CrystalPlacementCycleStatus::EarlyBreakReady;
                return result;
            }
            result.placementRequiresSuccessfulSameTickBreak = true;
        }
    }

    if (!input.hasCrystal) {
        scanCache_.clear();
        result.status = CrystalPlacementCycleStatus::NoCrystal;
        return result;
    }
    if (!input.placementWorld.hasWorld) {
        scanCache_.clear();
        result.status = CrystalPlacementCycleStatus::NoWorldSnapshot;
        return result;
    }

    const double scanX = result.placePrediction.position.x;
    const double scanY = result.placePrediction.position.y;
    const double scanZ = result.placePrediction.position.z;
    CrystalPlacementScanKey cacheKey;
    cacheKey.worldGeneration = input.worldGeneration;
    cacheKey.targetInstanceToken = targetData->instanceToken;
    cacheKey.targetEntityId = result.target.entityId;
    cacheKey.hasSimulatedAirBlock = input.placementWorld.hasSimulatedAirBlock;
    cacheKey.simulatedAirBlock = input.placementWorld.simulatedAirBlock;
    cacheKey.predictionBlock = {
        static_cast<int>(std::floor(scanX)),
        static_cast<int>(std::floor(scanY)),
        static_cast<int>(std::floor(scanZ))
    };
    cacheKey.phaseInfo = result.phaseInfo;

    result.didRescan = scanCache_.shouldRescan(cacheKey, input.scanInterval);
    const int ignoredEntityId = result.placementRequiresSuccessfulSameTickBreak
            ? result.earlyBreakPlan.crystalEntityId : input.placementWorld.ignoredEntityId;
    if (result.didRescan) {
        CrystalPlacementScanInput scanInput = input.placementWorld;
        scanInput.ignoredEntityId = ignoredEntityId;
        scanInput.targetPosition = result.placePrediction.position;
        if (input.antiPhaseEnabled && result.phaseInfo.phased()) {
            scanInput.hasPhaseInfo = true;
            scanInput.phaseInfo = result.phaseInfo;
            scanInput.antiPhaseRadius = input.antiPhaseRadius;
        } else {
            scanInput.hasPhaseInfo = false;
            scanInput.phaseInfo = {};
            scanInput.antiPhaseRadius = 0;
        }

        const std::vector<CrystalPlacement> scanned = CrystalPlacementScanner::scan(scanInput);
        std::vector<CrystalPlacement> reachable;
        reachable.reserve(scanned.size());
        for (const CrystalPlacement& placement : scanned) {
            if (isReachable(input.reachableBasesInWorldOrder, placement.basePosition)) {
                reachable.push_back(placement);
            }
        }

        CrystalPlacementScoringInput scoring = input.scoringSettings;
        scoring.placements = reachable;
        scoring.selfPosition = input.targetSelection.localPosition;
        scoring.targetPosition = result.fullPrediction.position;
        scoring.targetBounds = result.fullPrediction.boundingBox;
        scoring.hasTargetPrediction = true;
        scoring.targetPrediction = result.fullPrediction;
        scoring.targetDamageInput = targetData->damageInput;
        scoring.targetCollisionBoxes = targetData->damageCollisionBoxes;
        scoring.hasSimulatedAirBlock = input.placementWorld.hasSimulatedAirBlock;
        scoring.simulatedAirBlock = input.placementWorld.simulatedAirBlock;
        scoring.hasPhaseInfo = input.antiPhaseEnabled && result.phaseInfo.phased();
        scoring.phaseInfo = scoring.hasPhaseInfo ? result.phaseInfo : PhaseInfo{};
        scanCache_.updatePlacements(CrystalPlacementScorer::rank(scoring));
    }

    CrystalPlacementScanInput validationInput = input.placementWorld;
    validationInput.ignoredEntityId = ignoredEntityId;
    std::vector<CrystalPlacementScore> validPlacements;
    const std::vector<CrystalPlacementScore>& cached = scanCache_.placements();
    validPlacements.reserve(cached.size());
    for (const CrystalPlacementScore& placement : cached) {
        validationInput.hasSimulatedAirBlock = placement.packetMineOpeningBoosted
                && input.placementWorld.hasSimulatedAirBlock;
        validationInput.simulatedAirBlock = input.placementWorld.simulatedAirBlock;
        if (!CrystalPlacementScanner::canPlaceAt(validationInput, placement.placement.basePosition)) continue;
        if (!isReachable(input.reachableBasesInWorldOrder, placement.placement.basePosition)) continue;
        validPlacements.push_back(placement);
    }
    result.placements = validPlacements;

    CrystalPlacementActionInput actionInput;
    actionInput.placements = validPlacements;
    actionInput.clientReady = input.clientReady;
    actionInput.hasCrystal = input.hasCrystal;
    actionInput.hasPendingPlacement = input.hasPendingPlacement;
    actionInput.damageSyncEnabled = input.damageSyncEnabled;
    actionInput.damageSyncReady = input.damageSyncReady;
    actionInput.hasPlacementHand = input.hasPlacementHand;
    actionInput.eatingDefer = input.eatingDefer;
    actionInput.geometryValid = !validPlacements.empty();
    actionInput.hasReachableFace = !validPlacements.empty();
    actionInput.aimValid = !validPlacements.empty()
            && isReachable(
                    input.aimValidBasesInWorldOrder,
                    validPlacements.front().placement.basePosition);
    actionInput.shouldStage = input.shouldStage;
    actionInput.hasSimulatedAirBlock = input.placementWorld.hasSimulatedAirBlock;
    actionInput.simulatedAirBlock = input.placementWorld.simulatedAirBlock;
    result.actionPlan = CrystalPlacementActionPlanner::plan(actionInput);
    result.status = result.actionPlan.action == CrystalPlacementPlanAction::None
            ? CrystalPlacementCycleStatus::NoPlacement
            : CrystalPlacementCycleStatus::Planned;
    return result;
}

void CrystalPlacementCycleEngine::reset() {
    scanCache_.clear();
}

const CrystalPlacementScanCache& CrystalPlacementCycleEngine::scanCache() const {
    return scanCache_;
}

} }
