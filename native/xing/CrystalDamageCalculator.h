#pragma once

#include "CrystalActionController.h"
#include "CrystalPrediction.h"
#include <vector>

namespace xing { namespace autocrystal {

struct RayCollisionBox {
    BlockPosition blockPosition{};
    BoundingBox bounds{};
};

struct CrystalDamageInput {
    bool hasEntity = false;
    bool hasWorld = false;
    Vector3 entityPosition{};
    BoundingBox entityBounds{};
    bool hasExplosionCenter = false;
    Vector3 explosionCenter{};
    bool hasDamagePosition = false;
    Vector3 damagePosition{};
    bool hasDamageBounds = false;
    BoundingBox damageBounds{};
    bool hasSimulatedAirBlock = false;
    BlockPosition simulatedAirBlock{};
    int armorValue = 0;
    float armorToughness = 0.0F;
    bool hasResistance = false;
    int resistanceAmplifier = 0;
};

class CrystalDamageCalculator {
public:
    explicit CrystalDamageCalculator(std::vector<RayCollisionBox> collisionBoxes);

    static Vector3 explosionCenter(const BlockPosition& basePosition);
    double estimateDamage(const CrystalDamageInput& input) const;
    double estimateMaxDamage(const CrystalDamageInput& input) const;

private:
    std::vector<RayCollisionBox> collisionBoxes_;
};

} }
