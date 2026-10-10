#include "pch.h"
#include "CrystalBreakSelector.h"
#include <cmath>
#include <cstdint>
#include <cstring>
#include <limits>

namespace xing { namespace autocrystal {

namespace {
constexpr std::size_t MAX_AGGREGATED_TARGETS = 4;

struct ScoredCandidate {
    CrystalBreakObservation observation;
};

std::int64_t javaDoubleBits(double value) {
    std::int64_t bits = 0;
    std::memcpy(&bits, &value, sizeof(bits));
    const std::int32_t exponent = static_cast<std::int32_t>((bits >> 52) & 0x7FFLL);
    const std::int64_t mantissa = bits & 0xFFFFFFFFFFFFFLL;
    if (exponent == 0x7FF && mantissa != 0) return 0x7FF8000000000000LL;
    return bits;
}

int compareDouble(const double first, const double second) {
    if (first < second) return -1;
    if (first > second) return 1;
    const std::int64_t firstBits = javaDoubleBits(first);
    const std::int64_t secondBits = javaDoubleBits(second);
    if (firstBits == secondBits) return 0;
    return firstBits < secondBits ? -1 : 1;
}

int compareCandidates(
        const CrystalBreakObservation& first,
        const CrystalBreakObservation& second,
        bool hasPreferredBase) {
    int comparison = compareDouble(first.aggregateTargetDamage, second.aggregateTargetDamage);
    if (comparison != 0) return comparison;
    comparison = compareDouble(first.primaryTargetDamage, second.primaryTargetDamage);
    if (comparison != 0) return comparison;

    const double firstBaseDistance = hasPreferredBase ? -first.preferredBaseDistanceSquared : 0.0;
    const double secondBaseDistance = hasPreferredBase ? -second.preferredBaseDistanceSquared : 0.0;
    comparison = compareDouble(firstBaseDistance, secondBaseDistance);
    if (comparison != 0) return comparison;
    return compareDouble(-first.playerDistanceSquared, -second.playerDistanceSquared);
}

double blockDistanceSquared(const BlockPosition& first, const BlockPosition& second) {
    const double x = static_cast<double>(first.x) - second.x;
    const double y = static_cast<double>(first.y) - second.y;
    const double z = static_cast<double>(first.z) - second.z;
    return x * x + y * y + z * z;
}

ScoredCandidate scoreCandidate(
        const CrystalBreakSelectionInput& input,
        const CrystalDamageCalculator& selfDamageCalculator,
        const std::vector<CrystalDamageCalculator>& targetDamageCalculators,
        const BreakCrystalSnapshot& crystal,
        int candidateId,
        double rangeSquared) {
    CrystalBreakObservation observation;
    observation.candidateId = candidateId;
    observation.crystalEntityId = crystal.entityId;
    observation.isAlive = crystal.isAlive;
    observation.inRange = crystal.playerDistanceSquared <= rangeSquared;
    observation.aimValid = crystal.aimValid;
    observation.hasPreferredBase = input.hasPreferredBase;
    observation.preferredBaseDistanceSquared = input.hasPreferredBase
            ? blockDistanceSquared(crystal.blockPosition, input.preferredBase)
            : 0.0;
    observation.playerDistanceSquared = crystal.playerDistanceSquared;
    observation.attackCoolingDown = crystal.attackCoolingDown;

    CrystalDamageInput selfInput = input.selfDamageInput;
    selfInput.hasExplosionCenter = true;
    selfInput.explosionCenter = crystal.position;
    const double maximumPossibleSelfDamage = selfDamageCalculator.estimateMaxDamage(selfInput);
    observation.selfDamage = maximumPossibleSelfDamage <= input.maximumSelfDamage
            ? maximumPossibleSelfDamage
            : selfDamageCalculator.estimateDamage(selfInput);

    const std::size_t targetCount = input.targetsInCallerOrder.size() < MAX_AGGREGATED_TARGETS
            ? input.targetsInCallerOrder.size()
            : MAX_AGGREGATED_TARGETS;
    for (std::size_t index = 0; index < targetCount; ++index) {
        const BreakTargetSnapshot& target = input.targetsInCallerOrder[index];
        if (!target.isAlive || target.isSpectator) continue;

        CrystalDamageInput targetInput = target.damageInput;
        targetInput.hasExplosionCenter = true;
        targetInput.explosionCenter = crystal.position;
        const CrystalDamageCalculator& targetDamageCalculator = targetDamageCalculators[index];
        if (targetDamageCalculator.estimateMaxDamage(targetInput) < target.minimumDamage) continue;

        const double damage = targetDamageCalculator.estimateDamage(targetInput);
        if (damage < target.minimumDamage) continue;

        observation.aggregateTargetDamage += damage;
        if (!observation.hasPrimaryTarget || damage > observation.primaryTargetDamage) {
            observation.hasPrimaryTarget = true;
            observation.primaryTargetEntityId = target.entityId;
            observation.primaryTargetDamage = damage;
        }
    }
    return {observation};
}

}

CrystalBreakSelection CrystalBreakSelector::select(const CrystalBreakSelectionInput& input) {
    CrystalBreakSelection selection;
    if (input.targetsInCallerOrder.empty()) return selection;

    const CrystalDamageCalculator selfDamageCalculator(input.selfCollisionBoxes);
    const std::size_t targetCount = input.targetsInCallerOrder.size() < MAX_AGGREGATED_TARGETS
            ? input.targetsInCallerOrder.size()
            : MAX_AGGREGATED_TARGETS;
    std::vector<CrystalDamageCalculator> targetDamageCalculators;
    targetDamageCalculators.reserve(targetCount);
    for (std::size_t index = 0; index < targetCount; ++index) {
        targetDamageCalculators.emplace_back(input.targetsInCallerOrder[index].collisionBoxes);
    }
    const double rangeSquared = input.breakRange * input.breakRange;
    std::vector<ScoredCandidate> scored;
    scored.reserve(input.crystalsInWorldOrder.size());
    for (const BreakCrystalSnapshot& crystal : input.crystalsInWorldOrder) {
        if (!crystal.isAlive || !crystal.passesCandidateFilter
                || crystal.playerDistanceSquared > rangeSquared) {
            continue;
        }
        scored.push_back(scoreCandidate(input, selfDamageCalculator, targetDamageCalculators, crystal,
                static_cast<int>(scored.size()), rangeSquared));
    }

    int winnerIndex = -1;
    for (std::size_t index = 0; index < scored.size(); ++index) {
        const CrystalBreakObservation& candidate = scored[index].observation;
        if (!(candidate.selfDamage <= input.maximumSelfDamage) || !candidate.hasPrimaryTarget) continue;

        if (winnerIndex < 0 || compareCandidates(candidate,
                scored[static_cast<std::size_t>(winnerIndex)].observation,
                input.hasPreferredBase) > 0) {
            winnerIndex = static_cast<int>(index);
        }
    }

    selection.observations.reserve(scored.size());
    for (const ScoredCandidate& candidate : scored) {
        selection.observations.push_back(candidate.observation);
    }
    if (winnerIndex >= 0) {
        selection.winnerCandidateId = scored[static_cast<std::size_t>(winnerIndex)].observation.candidateId;
    }
    return selection;
}

} }
