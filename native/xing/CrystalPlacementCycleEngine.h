#pragma once

#include "CrystalPlacementActionPlanner.h"
#include "CrystalPlacementScanCache.h"
#include "CrystalBreakActionPlanner.h"
#include "CrystalTargetSelector.h"

namespace xing { namespace autocrystal {

struct CrystalCycleTargetData {
    int entityId = -1;
    std::uint64_t instanceToken = 0;
    Vector3 previousPosition{};
    BoundingBox bounds{};
    std::vector<BoundingBox> movementCollisionBoxes;
    CrystalDamageInput damageInput{};
    std::vector<RayCollisionBox> damageCollisionBoxes;
};

enum class CrystalPlacementCycleStatus {
    Skipped,
    NoTarget,
    MissingTargetData,
    NoCrystal,
    NoWorldSnapshot,
    EarlyBreakReady,
    NoPlacement,
    Planned
};

struct CrystalPlacementCycleInput {
    bool hasLiveReservation = false;
    bool packetReaction = false;
    bool packetUpdateMode = false;
    bool clientReady = false;
    bool externalPause = false;
    bool setbackPause = false;
    bool hasPendingAction = false;
    bool hasPendingPlacement = false;
    bool actionCoolingDown = false;
    bool breakExistingEnabled = false;
    bool sameTickBreakPlace = false;
    bool hasCrystal = false;

    CrystalBreakSelectionInput breakSelection{};
    CrystalBreakActionInput breakAction{};

    PlayerSelectionInput targetSelection{};
    std::vector<CrystalCycleTargetData> targetData;
    std::uint64_t worldGeneration = 0;
    bool predictMovement = true;
    int placePredictionTicks = 0;
    int fullPredictionTicks = 0;

    bool antiPhaseEnabled = false;
    double minimumPhaseOverlapDepth = 0.0;
    std::vector<PhaseBlockSnapshot> phaseBlocksInWorldOrder;
    int antiPhaseRadius = 0;
    CrystalPlacementScanInput placementWorld{};
    CrystalPlacementScoringInput scoringSettings{};
    std::vector<BlockPosition> reachableBasesInWorldOrder;
    int scanInterval = 1;

    bool damageSyncEnabled = false;
    bool damageSyncReady = false;
    bool hasPlacementHand = false;
    bool eatingDefer = false;
    std::vector<BlockPosition> aimValidBasesInWorldOrder;
    bool shouldStage = false;
};

struct CrystalPlacementCyclePlan {
    CrystalPlacementCycleStatus status = CrystalPlacementCycleStatus::Skipped;
    bool didRescan = false;
    bool hasTarget = false;
    PlayerSnapshot target{};
    CrystalPredictionSnapshot placePrediction{};
    CrystalPredictionSnapshot fullPrediction{};
    PhaseInfo phaseInfo{};
    bool hasEarlyBreakPlan = false;
    CrystalBreakActionPlan earlyBreakPlan{};
    bool placementRequiresSuccessfulSameTickBreak = false;
    std::vector<CrystalPlacementScore> placements;
    CrystalPlacementActionPlan actionPlan{};
};

class CrystalPlacementCycleEngine {
public:
    CrystalPlacementCyclePlan plan(const CrystalPlacementCycleInput& input);
    void reset();
    const CrystalPlacementScanCache& scanCache() const;

private:
    CrystalPlacementScanCache scanCache_;
};

} }
