#pragma once

#include <vector>

namespace xing { namespace autocrystal {

struct Vector3 {
    double x = 0.0;
    double y = 0.0;
    double z = 0.0;

    Vector3 operator+(const Vector3& other) const;
    Vector3 operator-(const Vector3& other) const;
    Vector3 scaled(double amount) const;
    double lengthSquared() const;
};

struct BoundingBox {
    double minX = 0.0;
    double minY = 0.0;
    double minZ = 0.0;
    double maxX = 0.0;
    double maxY = 0.0;
    double maxZ = 0.0;

    bool intersects(const BoundingBox& other) const;
    BoundingBox moved(const Vector3& offset) const;
};

struct CrystalPredictionInput {
    bool hasPlayer = false;
    Vector3 position{};
    Vector3 previousPosition{};
    BoundingBox boundingBox{};
    int ticks = 0;
    std::vector<BoundingBox> blockCollisionBoxes;
};

struct CrystalPredictionSnapshot {
    Vector3 position{};
    BoundingBox boundingBox{};
};

class CrystalPrediction {
public:
    static CrystalPredictionSnapshot predict(const CrystalPredictionInput& input);
};

} }
