#include "pch.h"
#include "CrystalBreakActionPlanner.h"
#include <cmath>

namespace xing { namespace autocrystal {

namespace {
CrystalBreakActionPlan makeRequestPlan(
        const CrystalBreakRequestInput& input,
        bool exactRequest) {
    CrystalBreakActionPlan result;
    result.hasPreferredBase = true;
    result.crystalEntityId = input.crystalEntityId;
    result.targetEntityId = input.targetEntityId;
    result.preferredBase = {
        input.crystalBlockPosition.x,
        input.crystalBlockPosition.y - 1,
        input.crystalBlockPosition.z
    };

    if (exactRequest && !input.clientReady) {
        result.reason = CrystalBreakPlanReason::ClientUnavailable;
        return result;
    }
    if (!input.hasCrystal || !input.crystalAlive) {
        result.reason = CrystalBreakPlanReason::CrystalMissing;
        return result;
    }
    if (exactRequest && (!input.hasTarget || !input.targetAlive)) {
        result.reason = CrystalBreakPlanReason::TargetMissing;
        return result;
    }
    if (exactRequest && !input.reservationAccepted) {
        result.reason = CrystalBreakPlanReason::ReservationDenied;
        return result;
    }
    if (exactRequest && input.eatingDefer) {
        result.reason = CrystalBreakPlanReason::EatingDefer;
        return result;
    }
    if (input.attackCoolingDown) {
        result.reason = CrystalBreakPlanReason::AttackCoolingDown;
        return result;
    }
    if ((!exactRequest && !input.hasPlayer)
            || input.crystalDistanceSquared > input.breakRange * input.breakRange) {
        result.reason = CrystalBreakPlanReason::OutOfRange;
        return result;
    }
    if (!exactRequest && !input.hasTarget) {
        result.reason = CrystalBreakPlanReason::TargetMissing;
        return result;
    }

    const bool trustedExactReservation = exactRequest && input.trustedReservation;
    if (!trustedExactReservation) {
        const bool ignoreSelfDamage = exactRequest && (input.playerCreative
                || input.playerInvulnerable
                || !std::isfinite(input.playerHealthAndAbsorption));
        if ((!ignoreSelfDamage && input.selfDamage > input.maximumSelfDamage)
                || input.targetDamage < input.minimumTargetDamage) {
            result.reason = CrystalBreakPlanReason::DamageRejected;
            return result;
        }
        if (!input.aimValid) {
            result.reason = CrystalBreakPlanReason::AimInvalid;
            return result;
        }
    }

    if (exactRequest && input.stageRequested && !input.immediateBreak) {
        result.action = CrystalBreakPlanAction::StageBreak;
        result.reason = CrystalBreakPlanReason::StageRequested;
        return result;
    }
    result.action = CrystalBreakPlanAction::ExecuteBreak;
    result.reason = CrystalBreakPlanReason::BreakReady;
    return result;
}
}

CrystalBreakActionPlan CrystalBreakActionPlanner::plan(
        const CrystalBreakSelection& selection,
        const CrystalBreakActionInput& input) {
    CrystalBreakActionPlan result;
    if (!input.clientReady) {
        result.reason = CrystalBreakPlanReason::ClientUnavailable;
        return result;
    }

    if (selection.winnerCandidateId < 0) {
        result.reason = CrystalBreakPlanReason::NoEligibleCrystal;
        return result;
    }

    for (const CrystalBreakObservation& observation : selection.observations) {
        if (observation.candidateId == selection.winnerCandidateId) {
            result.candidate = observation;
            result.hasCandidate = true;
            break;
        }
    }
    if (!result.hasCandidate) {
        result.reason = CrystalBreakPlanReason::NoEligibleCrystal;
        return result;
    }
    result.crystalEntityId = result.candidate.crystalEntityId;
    result.targetEntityId = result.candidate.primaryTargetEntityId;
    result.hasPreferredBase = input.hasPreferredBase;
    result.preferredBase = input.preferredBase;

    if (!input.reservationAccepted) {
        result.reason = CrystalBreakPlanReason::ReservationDenied;
        return result;
    }
    if (input.eatingDefer) {
        result.reason = CrystalBreakPlanReason::EatingDefer;
        return result;
    }
    if (result.candidate.attackCoolingDown) {
        result.reason = CrystalBreakPlanReason::AttackCoolingDown;
        return result;
    }
    if (!result.candidate.aimValid) {
        result.reason = CrystalBreakPlanReason::AimInvalid;
        return result;
    }
    if (input.stageRequested && !input.immediateBreak) {
        result.action = CrystalBreakPlanAction::StageBreak;
        result.reason = CrystalBreakPlanReason::StageRequested;
        return result;
    }

    result.action = CrystalBreakPlanAction::ExecuteBreak;
    result.reason = CrystalBreakPlanReason::BreakReady;
    return result;
}

CrystalBreakActionPlan CrystalBreakActionPlanner::planExactRequest(const CrystalBreakRequestInput& input) {
    return makeRequestPlan(input, true);
}

CrystalBreakActionPlan CrystalBreakActionPlanner::planPendingRequest(const CrystalBreakRequestInput& input) {
    return makeRequestPlan(input, false);
}

CrystalBreakActionPlan CrystalBreakActionPlanner::planSpawnedBreak(const CrystalSpawnedBreakInput& input) {
    CrystalBreakActionPlan result;
    result.crystalEntityId = input.crystalEntityId;
    result.targetEntityId = input.targetEntityId;

    if (!input.hasPlayer || !input.hasWorld || !input.hasConnection
            || !input.hasTarget || !input.hasCrystalPosition) {
        result.reason = CrystalBreakPlanReason::ClientUnavailable;
        return result;
    }
    if (!input.reservationAccepted) {
        result.reason = CrystalBreakPlanReason::ReservationDenied;
        return result;
    }
    if (input.eatingDefer) {
        result.reason = CrystalBreakPlanReason::EatingDefer;
        return result;
    }
    if (input.attackCoolingDown) {
        result.reason = CrystalBreakPlanReason::AttackCoolingDown;
        return result;
    }
    if (input.crystalDistanceSquared > input.breakRange * input.breakRange) {
        result.reason = CrystalBreakPlanReason::OutOfRange;
        return result;
    }
    if (input.selfDamage > input.maximumSelfDamage
            || input.targetDamage < input.minimumTargetDamage) {
        result.reason = CrystalBreakPlanReason::DamageRejected;
        return result;
    }
    if (!input.aimValid) {
        result.reason = CrystalBreakPlanReason::AimInvalid;
        return result;
    }

    if (input.immediateBreak && input.packetBreak) {
        result.action = CrystalBreakPlanAction::TryImmediatePacketThenQueue;
        result.reason = CrystalBreakPlanReason::BreakReady;
        return result;
    }
    result.action = CrystalBreakPlanAction::QueueAttack;
    result.reason = CrystalBreakPlanReason::BreakReady;
    return result;
}

} }
