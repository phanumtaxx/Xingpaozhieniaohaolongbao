#pragma once

#include "CrystalDamageCalculator.h"
#include <vector>

namespace xing { namespace autocrystal {

struct BreakTargetSnapshot {
    int entityId = -1;
    bool isAlive = false;
    bool isSpectator = false;
    double minimumDamage = 0.0;
    CrystalDamageInput damageInput{};
    std::vector<RayCollisionBox> collisionBoxes;
};

struct BreakCrystalSnapshot {
    int entityId = -1;
    BlockPosition blockPosition{};
    Vector3 position{};
    bool isAlive = false;
    bool passesCandidateFilter = true;
    bool aimValid = false;
    bool attackCoolingDown = false;
    double playerDistanceSquared = 0.0;
};

struct CrystalBreakObservation {
    int candidateId = -1;
    int crystalEntityId = -1;
    bool isAlive = false;
    bool inRange = false;
    bool aimValid = false;
    double selfDamage = 0.0;
    double aggregateTargetDamage = 0.0;
    double primaryTargetDamage = 0.0;
    bool hasPrimaryTarget = false;
    int primaryTargetEntityId = -1;
    double preferredBaseDistanceSquared = 0.0;
    bool hasPreferredBase = false;
    double playerDistanceSquared = 0.0;
    bool attackCoolingDown = false;
};

struct CrystalBreakSelectionInput {
    std::vector<BreakCrystalSnapshot> crystalsInWorldOrder;
    std::vector<BreakTargetSnapshot> targetsInCallerOrder;
    CrystalDamageInput selfDamageInput{};
    std::vector<RayCollisionBox> selfCollisionBoxes;
    double breakRange = 0.0;
    double maximumSelfDamage = 0.0;
    bool hasPreferredBase = false;
    BlockPosition preferredBase{};
};

struct CrystalBreakSelection {
    std::vector<CrystalBreakObservation> observations;
    int winnerCandidateId = -1;
};

class CrystalBreakSelector {
public:
    static CrystalBreakSelection select(const CrystalBreakSelectionInput& input);
};

} }
