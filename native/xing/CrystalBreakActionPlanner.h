#pragma once

#include "CrystalBreakSelector.h"

namespace xing { namespace autocrystal {

enum class CrystalBreakPlanAction {
    None,
    StageBreak,
    ExecuteBreak,
    TryImmediatePacketThenQueue,
    QueueAttack
};

enum class CrystalBreakPlanReason {
    None,
    ClientUnavailable,
    NoEligibleCrystal,
    ReservationDenied,
    EatingDefer,
    AttackCoolingDown,
    CrystalMissing,
    TargetMissing,
    OutOfRange,
    DamageRejected,
    AimInvalid,
    StageRequested,
    BreakReady
};

struct CrystalBreakActionInput {
    bool clientReady = false;
    bool reservationAccepted = false;
    bool eatingDefer = false;
    bool immediateBreak = false;
    bool stageRequested = false;
    bool hasPreferredBase = false;
    BlockPosition preferredBase{};
};

struct CrystalBreakRequestInput {
    bool clientReady = false;
    bool hasPlayer = false;
    bool hasCrystal = false;
    bool crystalAlive = false;
    bool hasTarget = false;
    bool targetAlive = false;
    bool reservationAccepted = false;
    bool eatingDefer = false;
    bool attackCoolingDown = false;
    double crystalDistanceSquared = 0.0;
    double breakRange = 0.0;
    bool trustedReservation = false;
    bool playerCreative = false;
    bool playerInvulnerable = false;
    double playerHealthAndAbsorption = 0.0;
    double selfDamage = 0.0;
    double maximumSelfDamage = 0.0;
    double targetDamage = 0.0;
    double minimumTargetDamage = 0.0;
    bool aimValid = false;
    bool stageRequested = false;
    bool immediateBreak = false;
    int crystalEntityId = -1;
    int targetEntityId = -1;
    BlockPosition crystalBlockPosition{};
};

struct CrystalSpawnedBreakInput {
    bool hasPlayer = false;
    bool hasWorld = false;
    bool hasConnection = false;
    bool hasTarget = false;
    bool hasCrystalPosition = false;
    bool reservationAccepted = false;
    bool eatingDefer = false;
    bool attackCoolingDown = false;
    double crystalDistanceSquared = 0.0;
    double breakRange = 0.0;
    double selfDamage = 0.0;
    double maximumSelfDamage = 0.0;
    double targetDamage = 0.0;
    double minimumTargetDamage = 0.0;
    bool aimValid = false;
    bool immediateBreak = false;
    bool packetBreak = false;
    int crystalEntityId = -1;
    int targetEntityId = -1;
};

struct CrystalBreakActionPlan {
    CrystalBreakPlanAction action = CrystalBreakPlanAction::None;
    CrystalBreakPlanReason reason = CrystalBreakPlanReason::None;
    bool hasCandidate = false;
    CrystalBreakObservation candidate{};
    bool hasPreferredBase = false;
    int crystalEntityId = -1;
    int targetEntityId = -1;
    BlockPosition preferredBase{};
};

class CrystalBreakActionPlanner {
public:
    static CrystalBreakActionPlan plan(
        const CrystalBreakSelection& selection,
        const CrystalBreakActionInput& input);
    static CrystalBreakActionPlan planExactRequest(const CrystalBreakRequestInput& input);
    static CrystalBreakActionPlan planPendingRequest(const CrystalBreakRequestInput& input);
    static CrystalBreakActionPlan planSpawnedBreak(const CrystalSpawnedBreakInput& input);
};

} }
