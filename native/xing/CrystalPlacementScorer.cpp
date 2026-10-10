#include "pch.h"
#include "CrystalPlacementScorer.h"
#include <algorithm>
#include <cmath>

namespace xing { namespace autocrystal {

namespace {
long long manhattanDistance(const BlockPosition& first, const BlockPosition& second) {
    const auto difference = [](int left, int right) {
        const long long delta = static_cast<long long>(left) - static_cast<long long>(right);
        return delta < 0 ? -delta : delta;
    };
    return difference(first.x, second.x) + difference(first.y, second.y) + difference(first.z, second.z);
}

double blockDistanceSquared(const BlockPosition& first, const BlockPosition& second) {
    const double x = static_cast<double>(first.x) - second.x;
    const double y = static_cast<double>(first.y) - second.y;
    const double z = static_cast<double>(first.z) - second.z;
    return x * x + y * y + z * z;
}

double distanceToBlockCenterSquared(const BlockPosition& block, const Vector3& point) {
    const double x = static_cast<double>(block.x) + 0.5 - point.x;
    const double y = static_cast<double>(block.y) + 0.5 - point.y;
    const double z = static_cast<double>(block.z) + 0.5 - point.z;
    return x * x + y * y + z * z;
}

BlockPosition containing(const Vector3& position) {
    return {
        static_cast<int>(std::floor(position.x)),
        static_cast<int>(std::floor(position.y)),
        static_cast<int>(std::floor(position.z))
    };
}

bool openingEligible(
        const CrystalPlacement& placement,
        const Vector3& targetPosition,
        bool hasSimulatedAirBlock,
        const BlockPosition& simulatedAirBlock,
        double openingWeight) {
    if (!hasSimulatedAirBlock || openingWeight <= 0.0) return false;

    const BlockPosition targetFeet = containing(targetPosition);
    return manhattanDistance(simulatedAirBlock, targetFeet) == 1
            && blockDistanceSquared(placement.basePosition, simulatedAirBlock) <= 9.0;
}

bool antiPhaseEligible(
        const CrystalPlacement& placement,
        double targetDamage,
        bool hasSimulatedAirBlock,
        const BlockPosition& simulatedAirBlock,
        bool hasPhaseInfo,
        const PhaseInfo& phaseInfo,
        double antiPhaseBoost) {
    if (!hasPhaseInfo || !phaseInfo.phased() || antiPhaseBoost <= 0.0 || targetDamage <= 0.0) return false;

    for (const BlockPosition& phasedBlock : phaseInfo.phasedBlocks) {
        if (blockDistanceSquared(placement.basePosition, phasedBlock) <= 9.0) return true;
        if (hasSimulatedAirBlock && manhattanDistance(simulatedAirBlock, phasedBlock) <= 1
                && blockDistanceSquared(placement.basePosition, simulatedAirBlock) <= 9.0) {
            return true;
        }
    }
    return false;
}

bool comesBeforeDescending(double first, double second) {
    if (std::isnan(first)) return !std::isnan(second);
    if (std::isnan(second)) return false;
    if (first > second) return true;
    if (first < second) return false;
    if (first == 0.0 && second == 0.0) return !std::signbit(first) && std::signbit(second);
    return false;
}

}

std::vector<CrystalPlacementScore> CrystalPlacementScorer::rank(const CrystalPlacementScoringInput& input) {
    if (input.placements.empty()) return {};

    const CrystalDamageCalculator targetDamageCalculator(input.targetCollisionBoxes);
    const CrystalDamageCalculator selfDamageCalculator(input.selfCollisionBoxes);
    const Vector3 damagePosition = input.hasTargetPrediction
            ? input.targetPrediction.position
            : input.targetPosition;
    const BoundingBox damageBounds = input.hasTargetPrediction
            ? input.targetPrediction.boundingBox
            : input.targetBounds;

    CrystalDamageInput targetInput = input.targetDamageInput;
    targetInput.hasDamagePosition = true;
    targetInput.damagePosition = damagePosition;
    targetInput.hasDamageBounds = true;
    targetInput.damageBounds = damageBounds;
    targetInput.hasSimulatedAirBlock = input.hasSimulatedAirBlock;
    targetInput.simulatedAirBlock = input.simulatedAirBlock;

    std::vector<CrystalPlacementScore> ranked;
    ranked.reserve(input.placements.size());
    for (std::size_t index = 0; index < input.placements.size(); ++index) {
        const CrystalPlacement& placement = input.placements[index];
        const Vector3 center = CrystalDamageCalculator::explosionCenter(placement.basePosition);

        targetInput.hasExplosionCenter = true;
        targetInput.explosionCenter = center;
        CrystalDamageInput selfInput = input.selfDamageInput;
        selfInput.hasExplosionCenter = true;
        selfInput.explosionCenter = center;
        selfInput.hasSimulatedAirBlock = input.hasSimulatedAirBlock;
        selfInput.simulatedAirBlock = input.simulatedAirBlock;

        const double targetDamage = targetDamageCalculator.estimateDamage(targetInput);
        const double selfDamage = selfDamageCalculator.estimateDamage(selfInput);
        const bool openingBoosted = openingEligible(placement, damagePosition,
                input.hasSimulatedAirBlock, input.simulatedAirBlock, input.packetMineOpeningWeight);
        const bool phaseBoosted = antiPhaseEligible(placement, targetDamage,
                input.hasSimulatedAirBlock, input.simulatedAirBlock,
                input.hasPhaseInfo, input.phaseInfo, input.antiPhaseBoost);
        const double selfDistanceSquared = distanceToBlockCenterSquared(placement.basePosition, input.selfPosition);
        const double distancePenalty = std::sqrt(placement.targetDistanceSquared) * 0.05;

        double score = targetDamage * input.targetDamageWeight
                - selfDamage * input.selfDamageWeight - distancePenalty;
        if (openingBoosted) score += input.packetMineOpeningWeight;
        if (phaseBoosted) score += input.antiPhaseBoost;

        ranked.push_back({
            static_cast<int>(index), placement, score, targetDamage, selfDamage,
            placement.targetDistanceSquared, selfDistanceSquared, openingBoosted, phaseBoosted
        });
    }

    std::stable_sort(ranked.begin(), ranked.end(), [](const CrystalPlacementScore& first, const CrystalPlacementScore& second) {
        return comesBeforeDescending(first.score, second.score);
    });

    ranked.erase(std::remove_if(ranked.begin(), ranked.end(), [&input](const CrystalPlacementScore& candidate) {
        return !(candidate.targetDamage >= input.minimumTargetDamage)
                || !(candidate.selfDamage <= input.maximumSelfDamage)
                || !(candidate.selfDamage + 2.0 < input.selfHealth);
    }), ranked.end());
    return ranked;
}

} }
