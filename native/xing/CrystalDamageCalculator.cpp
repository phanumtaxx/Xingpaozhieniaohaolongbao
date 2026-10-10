#include "pch.h"
#include "CrystalDamageCalculator.h"
#include <algorithm>
#include <cmath>
#include <limits>
#include <cstdint>
#include <utility>

namespace xing { namespace autocrystal {

namespace {
constexpr float CRYSTAL_EXPLOSION_RADIUS = 6.0F;
constexpr double RAY_EPSILON = 1.0E-7;
constexpr double SIMULATED_AIR_ADVANCE = 1.0E-4;

struct RayHit {
    Vector3 location{};
    double distanceSquaredToEnd = 0.0;
};

double lerp(double amount, double start, double end) {
    return start + (end - start) * amount;
}

int floorToInt(double value) {
    return static_cast<int>(std::floor(value));
}

double fraction(double value) {
    return value - std::floor(value);
}

int sign(double value) {
    return value > 0.0 ? 1 : (value < 0.0 ? -1 : 0);
}

bool considerClipPlane(
        double startAxis,
        double startFirst,
        double startSecond,
        double deltaAxis,
        double deltaFirst,
        double deltaSecond,
        double plane,
        double firstMinimum,
        double secondMinimum,
        double firstMaximum,
        double secondMaximum,
        double& closestFraction) {
    if (std::abs(deltaAxis) <= RAY_EPSILON) return false;

    const double candidateFraction = (plane - startAxis) / deltaAxis;
    if (candidateFraction <= 0.0 || candidateFraction >= closestFraction) return false;

    const double first = startFirst + candidateFraction * deltaFirst;
    const double second = startSecond + candidateFraction * deltaSecond;
    if (first <= firstMinimum - RAY_EPSILON || first >= firstMaximum + RAY_EPSILON
            || second <= secondMinimum - RAY_EPSILON || second >= secondMaximum + RAY_EPSILON) {
        return false;
    }

    closestFraction = candidateFraction;
    return true;
}

bool clipBox(const BoundingBox& box, const Vector3& start, const Vector3& end, RayHit& hit) {
    const Vector3 delta = end - start;
    double closestFraction = 1.0;
    bool didHit = false;

    if (delta.x > RAY_EPSILON) {
        didHit = considerClipPlane(start.x, start.y, start.z, delta.x, delta.y, delta.z,
                box.minX, box.minY, box.minZ, box.maxY, box.maxZ, closestFraction) || didHit;
    } else if (delta.x < -RAY_EPSILON) {
        didHit = considerClipPlane(start.x, start.y, start.z, delta.x, delta.y, delta.z,
                box.maxX, box.minY, box.minZ, box.maxY, box.maxZ, closestFraction) || didHit;
    }

    if (delta.y > RAY_EPSILON) {
        didHit = considerClipPlane(start.y, start.z, start.x, delta.y, delta.z, delta.x,
                box.minY, box.minZ, box.minX, box.maxZ, box.maxX, closestFraction) || didHit;
    } else if (delta.y < -RAY_EPSILON) {
        didHit = considerClipPlane(start.y, start.z, start.x, delta.y, delta.z, delta.x,
                box.maxY, box.minZ, box.minX, box.maxZ, box.maxX, closestFraction) || didHit;
    }

    if (delta.z > RAY_EPSILON) {
        didHit = considerClipPlane(start.z, start.x, start.y, delta.z, delta.x, delta.y,
                box.minZ, box.minX, box.minY, box.maxX, box.maxY, closestFraction) || didHit;
    } else if (delta.z < -RAY_EPSILON) {
        didHit = considerClipPlane(start.z, start.x, start.y, delta.z, delta.x, delta.y,
                box.maxZ, box.minX, box.minY, box.maxX, box.maxY, closestFraction) || didHit;
    }

    if (!didHit) return false;
    hit.location = start + delta.scaled(closestFraction);
    hit.distanceSquaredToEnd = (end - hit.location).lengthSquared();
    return true;
}

bool samePosition(const BlockPosition& first, const BlockPosition& second) {
    return first.x == second.x && first.y == second.y && first.z == second.z;
}

bool comesBefore(const BlockPosition& first, const BlockPosition& second) {
    if (first.x != second.x) return first.x < second.x;
    if (first.y != second.y) return first.y < second.y;
    return first.z < second.z;
}

bool clipBlockShape(
        const std::vector<RayCollisionBox>& collisionBoxes,
        std::vector<RayCollisionBox>::const_iterator firstBox,
        const Vector3& start,
        const Vector3& end,
        RayHit& result) {
    bool foundHit = false;
    for (auto box = firstBox; box != collisionBoxes.end(); ++box) {
        if (!samePosition(box->blockPosition, firstBox->blockPosition)) break;
        RayHit candidate;
        if (clipBox(box->bounds, start, end, candidate)
                && (!foundHit || candidate.distanceSquaredToEnd > result.distanceSquaredToEnd)) {
            result = candidate;
            foundHit = true;
        }
    }
    return foundHit;
}

bool clipWorld(
        const std::vector<RayCollisionBox>& collisions,
        const Vector3& start,
        const Vector3& end,
        BlockPosition& hitBlock,
        RayHit& hit) {
    if (start.x == end.x && start.y == end.y && start.z == end.z) return false;

    const double traversalEndX = lerp(-RAY_EPSILON, end.x, start.x);
    const double traversalEndY = lerp(-RAY_EPSILON, end.y, start.y);
    const double traversalEndZ = lerp(-RAY_EPSILON, end.z, start.z);
    const double traversalStartX = lerp(-RAY_EPSILON, start.x, end.x);
    const double traversalStartY = lerp(-RAY_EPSILON, start.y, end.y);
    const double traversalStartZ = lerp(-RAY_EPSILON, start.z, end.z);

    BlockPosition position{
        floorToInt(traversalStartX),
        floorToInt(traversalStartY),
        floorToInt(traversalStartZ)
    };
    const double deltaX = traversalEndX - traversalStartX;
    const double deltaY = traversalEndY - traversalStartY;
    const double deltaZ = traversalEndZ - traversalStartZ;
    const int stepX = sign(deltaX);
    const int stepY = sign(deltaY);
    const int stepZ = sign(deltaZ);
    const double stepFractionX = stepX == 0 ? (std::numeric_limits<double>::max)() : stepX / deltaX;
    const double stepFractionY = stepY == 0 ? (std::numeric_limits<double>::max)() : stepY / deltaY;
    const double stepFractionZ = stepZ == 0 ? (std::numeric_limits<double>::max)() : stepZ / deltaZ;
    double nextX = stepFractionX * (stepX > 0 ? 1.0 - fraction(traversalStartX) : fraction(traversalStartX));
    double nextY = stepFractionY * (stepY > 0 ? 1.0 - fraction(traversalStartY) : fraction(traversalStartY));
    double nextZ = stepFractionZ * (stepZ > 0 ? 1.0 - fraction(traversalStartZ) : fraction(traversalStartZ));

    for (;;) {
        const auto found = std::lower_bound(
                collisions.begin(), collisions.end(), position,
                [](const RayCollisionBox& collision, const BlockPosition& target) {
                    return comesBefore(collision.blockPosition, target);
                });
        if (found != collisions.end() && samePosition(found->blockPosition, position)
                && clipBlockShape(collisions, found, start, end, hit)) {
            hitBlock = position;
            return true;
        }

        if (nextX > 1.0 && nextY > 1.0 && nextZ > 1.0) return false;
        if (nextX < nextY) {
            if (nextX < nextZ) {
                position.x += stepX;
                nextX += stepFractionX;
            } else {
                position.z += stepZ;
                nextZ += stepFractionZ;
            }
        } else if (nextY < nextZ) {
            position.y += stepY;
            nextY += stepFractionY;
        } else {
            position.z += stepZ;
            nextZ += stepFractionZ;
        }
    }
}

bool isVisible(
        const std::vector<RayCollisionBox>& collisions,
        const Vector3& from,
        const Vector3& to,
        bool hasSimulatedAirBlock,
        const BlockPosition& simulatedAirBlock) {
    Vector3 start = from;
    const Vector3 direction = to - from;
    const double length = std::sqrt(direction.lengthSquared());
    const Vector3 normalizedDirection = length < static_cast<double>(1.0E-5F)
            ? Vector3{}
            : direction.scaled(1.0 / length);

    for (int attempt = 0; attempt < 8; ++attempt) {
        BlockPosition hitBlock;
        RayHit hit;
        if (!clipWorld(collisions, start, to, hitBlock, hit)) return true;
        if (!hasSimulatedAirBlock || hitBlock != simulatedAirBlock) return false;
        if (hit.distanceSquaredToEnd < 1.0E-7) return true;
        start = hit.location + normalizedDirection.scaled(SIMULATED_AIR_ADVANCE);
    }
    return false;
}

float seenPercent(
        const CrystalDamageInput& input,
        const std::vector<RayCollisionBox>& collisions,
        const Vector3& explosionCenter,
        const BoundingBox& box) {
    const double stepX = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
    const double stepY = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
    const double stepZ = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);
    if (stepX <= 0.0 || stepY <= 0.0 || stepZ <= 0.0) return 0.0F;

    const double offsetX = (1.0 - std::floor(1.0 / stepX) * stepX) * 0.5;
    const double offsetZ = (1.0 - std::floor(1.0 / stepZ) * stepZ) * 0.5;
    int visible = 0;
    int total = 0;

    for (double x = 0.0; x <= 1.0; x += stepX) {
        for (double y = 0.0; y <= 1.0; y += stepY) {
            for (double z = 0.0; z <= 1.0; z += stepZ) {
                const Vector3 sample{
                    lerp(x, box.minX, box.maxX) + offsetX,
                    lerp(y, box.minY, box.maxY),
                    lerp(z, box.minZ, box.maxZ) + offsetZ
                };
                if (isVisible(collisions, sample, explosionCenter,
                        input.hasSimulatedAirBlock, input.simulatedAirBlock)) {
                    ++visible;
                }
                ++total;
            }
        }
    }

    return total == 0 ? 0.0F : static_cast<float>(visible) / static_cast<float>(total);
}

float applyResistance(const CrystalDamageInput& input, float damage) {
    if (!input.hasResistance) return damage;
    const int absorbValue = (input.resistanceAmplifier + 1) * 5;
    const int absorb = 25 - absorbValue;
    const float reducedDamage = damage * static_cast<float>(absorb) / 25.0F;
    if (std::isnan(reducedDamage)) return reducedDamage;
    return reducedDamage > 0.0F ? reducedDamage : 0.0F;
}

float applyLivingReductions(const CrystalDamageInput& input, float damage) {
    const float toughnessFactor = 2.0F + input.armorToughness / 4.0F;
    const float rawArmorReduction = static_cast<float>(input.armorValue) - damage / toughnessFactor;
    const float minimumReduction = static_cast<float>(input.armorValue) * 0.2F;
    float armorReduction = rawArmorReduction;
    if (armorReduction < minimumReduction) armorReduction = minimumReduction;
    if (armorReduction > 20.0F) armorReduction = 20.0F;
    const float armorRatio = armorReduction / 25.0F;
    const float afterArmor = damage * (1.0F - armorRatio);
    return applyResistance(input, afterArmor);
}

double normalizedDistance(const Vector3& position, const Vector3& explosionCenter) {
    const Vector3 difference = position - explosionCenter;
    return std::sqrt(difference.lengthSquared()) / (CRYSTAL_EXPLOSION_RADIUS * 2.0F);
}

float rawExplosionDamage(double impact) {
    return static_cast<float>((impact * impact + impact) / 2.0 * 7.0
            * (CRYSTAL_EXPLOSION_RADIUS * 2.0F) + 1.0);
}

}

Vector3 CrystalDamageCalculator::explosionCenter(const BlockPosition& basePosition) {
    return {
        static_cast<double>(basePosition.x) + 0.5,
        static_cast<double>(basePosition.y) + 1.0,
        static_cast<double>(basePosition.z) + 0.5
    };
}

CrystalDamageCalculator::CrystalDamageCalculator(std::vector<RayCollisionBox> collisionBoxes)
        : collisionBoxes_(std::move(collisionBoxes)) {
    std::stable_sort(collisionBoxes_.begin(), collisionBoxes_.end(),
            [](const RayCollisionBox& first, const RayCollisionBox& second) {
                return comesBefore(first.blockPosition, second.blockPosition);
            });
}

double CrystalDamageCalculator::estimateDamage(const CrystalDamageInput& input) const {
    if (!input.hasEntity || !input.hasWorld || !input.hasExplosionCenter) return 0.0;

    const Vector3 position = input.hasDamagePosition ? input.damagePosition : input.entityPosition;
    const BoundingBox box = input.hasDamageBounds
            ? input.damageBounds
            : input.entityBounds.moved(position - input.entityPosition);
    const double distance = normalizedDistance(position, input.explosionCenter);
    if (distance > 1.0) return 0.0;

    const float exposure = seenPercent(input, collisionBoxes_, input.explosionCenter, box);
    const double impact = (1.0 - distance) * exposure;
    return applyLivingReductions(input, rawExplosionDamage(impact));
}

double CrystalDamageCalculator::estimateMaxDamage(const CrystalDamageInput& input) const {
    if (!input.hasEntity || !input.hasWorld || !input.hasExplosionCenter) return 0.0;

    const Vector3 position = input.hasDamagePosition ? input.damagePosition : input.entityPosition;
    const double distance = normalizedDistance(position, input.explosionCenter);
    if (distance > 1.0) return 0.0;

    const double impact = 1.0 - distance;
    return applyLivingReductions(input, rawExplosionDamage(impact));
}

} }
