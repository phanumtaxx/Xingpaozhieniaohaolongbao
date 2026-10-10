#pragma once

#include "CrystalPlacementScorer.h"

namespace xing { namespace autocrystal {

enum class CrystalPlacementPlanAction {
    None,
    StagePlacement,
    ExecutePlacement
};

enum class CrystalPlacementPlanReason {
    None,
    NoCandidate,
    ClientUnavailable,
    NoCrystal,
    PlacementPending,
    DamageSyncWaiting,
    NoPlacementHand,
    EatingDefer,
    GeometryInvalid,
    NoReachableFace,
    AimInvalid,
    StageRequested,
    PlacementReady
};

struct CrystalPlacementActionInput {
    std::vector<CrystalPlacementScore> placements;
    bool clientReady = false;
    bool hasCrystal = false;
    bool hasPendingPlacement = false;
    bool damageSyncEnabled = false;
    bool damageSyncReady = false;
    bool hasPlacementHand = false;
    bool eatingDefer = false;
    bool geometryValid = false;
    bool hasReachableFace = false;
    bool aimValid = false;
    bool shouldStage = false;
    bool hasSimulatedAirBlock = false;
    BlockPosition simulatedAirBlock{};
};

struct CrystalPlacementActionPlan {
    CrystalPlacementPlanAction action = CrystalPlacementPlanAction::None;
    CrystalPlacementPlanReason reason = CrystalPlacementPlanReason::None;
    bool hasCandidate = false;
    CrystalPlacementScore candidate{};
    bool hasOpeningDependency = false;
    BlockPosition openingDependency{};
};

class CrystalPlacementActionPlanner {
public:
    static CrystalPlacementActionPlan plan(const CrystalPlacementActionInput& input);
};

} }
