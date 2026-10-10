#include "pch.h"
#include "CrystalPrediction.h"

namespace xing { namespace autocrystal {
namespace {
constexpr double GRAVITY = 0.0784;
constexpr double MINIMUM_MOVEMENT_SQUARED = 1.0E-8;

bool isSpaceEmpty(const BoundingBox& box, const std::vector<BoundingBox>& collisions) {
    for (const BoundingBox& collision : collisions) {
        if (box.intersects(collision)) return false;
    }
    return true;
}
}

Vector3 Vector3::operator+(const Vector3& other) const {
    return {x + other.x, y + other.y, z + other.z};
}

Vector3 Vector3::operator-(const Vector3& other) const {
    return {x - other.x, y - other.y, z - other.z};
}

Vector3 Vector3::scaled(double amount) const {
    return {x * amount, y * amount, z * amount};
}

double Vector3::lengthSquared() const {
    return x * x + y * y + z * z;
}

bool BoundingBox::intersects(const BoundingBox& other) const {
    return maxX > other.minX && minX < other.maxX
        && maxY > other.minY && minY < other.maxY
        && maxZ > other.minZ && minZ < other.maxZ;
}

BoundingBox BoundingBox::moved(const Vector3& offset) const {
    return {
        minX + offset.x, minY + offset.y, minZ + offset.z,
        maxX + offset.x, maxY + offset.y, maxZ + offset.z
    };
}

CrystalPredictionSnapshot CrystalPrediction::predict(const CrystalPredictionInput& input) {
    if (!input.hasPlayer) return {};
    if (input.ticks <= 0) return {input.position, input.boundingBox};

    Vector3 step = input.position - input.previousPosition;
    if (step.lengthSquared() < MINIMUM_MOVEMENT_SQUARED) {
        return {input.position, input.boundingBox};
    }

    Vector3 offset{};
    for (int tick = 0; tick < input.ticks; ++tick) {
        Vector3 next = offset + step;
        if (!isSpaceEmpty(input.boundingBox.moved(next), input.blockCollisionBoxes)) {
            const Vector3 vertical{0.0, next.y, 0.0};
            const Vector3 horizontal{next.x, 0.0, next.z};
            if (isSpaceEmpty(input.boundingBox.moved(vertical), input.blockCollisionBoxes)) {
                next = {offset.x, next.y, offset.z};
            } else if (isSpaceEmpty(input.boundingBox.moved(horizontal), input.blockCollisionBoxes)) {
                next = {next.x, offset.y, next.z};
            } else {
                next = offset;
            }
        }

        offset = next;
        step.y -= GRAVITY;
    }

    return {input.position + offset, input.boundingBox.moved(offset)};
}

} }
