#include "pch.h"
#include "CrystalPlacementActionPlanner.h"

namespace xing { namespace autocrystal {

CrystalPlacementActionPlan CrystalPlacementActionPlanner::plan(const CrystalPlacementActionInput& input) {
    CrystalPlacementActionPlan result;
    if (!input.clientReady) {
        result.reason = CrystalPlacementPlanReason::ClientUnavailable;
        return result;
    }
    if (!input.hasCrystal) {
        result.reason = CrystalPlacementPlanReason::NoCrystal;
        return result;
    }
    if (input.placements.empty()) {
        result.reason = CrystalPlacementPlanReason::NoCandidate;
        return result;
    }

    result.candidate = input.placements.front();
    result.hasCandidate = true;
    result.hasOpeningDependency = result.candidate.packetMineOpeningBoosted
            && input.hasSimulatedAirBlock;
    if (result.hasOpeningDependency) result.openingDependency = input.simulatedAirBlock;

    if (input.hasPendingPlacement) {
        result.reason = CrystalPlacementPlanReason::PlacementPending;
        return result;
    }
    if (input.damageSyncEnabled && !input.damageSyncReady) {
        result.reason = CrystalPlacementPlanReason::DamageSyncWaiting;
        return result;
    }
    if (!input.hasPlacementHand) {
        result.reason = CrystalPlacementPlanReason::NoPlacementHand;
        return result;
    }
    if (input.eatingDefer) {
        result.reason = CrystalPlacementPlanReason::EatingDefer;
        return result;
    }
    if (!input.geometryValid) {
        result.reason = CrystalPlacementPlanReason::GeometryInvalid;
        return result;
    }
    if (!input.hasReachableFace) {
        result.reason = CrystalPlacementPlanReason::NoReachableFace;
        return result;
    }
    if (!input.aimValid) {
        result.reason = CrystalPlacementPlanReason::AimInvalid;
        return result;
    }
    if (input.shouldStage) {
        result.action = CrystalPlacementPlanAction::StagePlacement;
        result.reason = CrystalPlacementPlanReason::StageRequested;
        return result;
    }

    result.action = CrystalPlacementPlanAction::ExecutePlacement;
    result.reason = CrystalPlacementPlanReason::PlacementReady;
    return result;
}

} }
