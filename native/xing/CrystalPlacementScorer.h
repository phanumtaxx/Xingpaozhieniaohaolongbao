#pragma once

#include "CrystalDamageCalculator.h"
#include "CrystalPlacementScanner.h"
#include <vector>

namespace xing { namespace autocrystal {

struct CrystalPlacementScore {
    int candidateId = -1;
    CrystalPlacement placement{};
    double score = 0.0;
    double targetDamage = 0.0;
    double selfDamage = 0.0;
    double targetDistanceSquared = 0.0;
    double selfDistanceSquared = 0.0;
    bool packetMineOpeningBoosted = false;
    bool antiPhaseBoosted = false;
};

struct CrystalPlacementScoringInput {
    std::vector<CrystalPlacement> placements;
    CrystalDamageInput targetDamageInput{};
    CrystalDamageInput selfDamageInput{};
    std::vector<RayCollisionBox> targetCollisionBoxes;
    std::vector<RayCollisionBox> selfCollisionBoxes;
    Vector3 selfPosition{};
    Vector3 targetPosition{};
    BoundingBox targetBounds{};
    bool hasTargetPrediction = false;
    CrystalPredictionSnapshot targetPrediction{};
    bool hasSimulatedAirBlock = false;
    BlockPosition simulatedAirBlock{};
    double targetDamageWeight = 0.0;
    double selfDamageWeight = 0.0;
    double packetMineOpeningWeight = 0.0;
    bool hasPhaseInfo = false;
    PhaseInfo phaseInfo{};
    double antiPhaseBoost = 0.0;
    double minimumTargetDamage = 0.0;
    double maximumSelfDamage = 0.0;
    double selfHealth = 0.0;
};

class CrystalPlacementScorer {
public:
    static std::vector<CrystalPlacementScore> rank(const CrystalPlacementScoringInput& input);
};

} }
