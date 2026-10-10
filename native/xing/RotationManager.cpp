#include "pch.h"
#include "RotationManager.h"
#include <cmath>

namespace xing { namespace managers {

bool RotationManager::requestLookAt(
        std::uint64_t owner, int priority, double x, double y, double z, int holdTicks) {
    if (!std::isfinite(x) || !std::isfinite(y) || !std::isfinite(z)) return false;
    constexpr double radiansToDegrees = 57.29577951308232;
    const auto yaw = static_cast<float>(std::atan2(z, x) * radiansToDegrees - 90.0);
    const auto pitch = static_cast<float>(-std::atan2(y, std::hypot(x, z)) * radiansToDegrees);
    return request(owner, priority, yaw, pitch, holdTicks);
}

bool RotationManager::request(std::uint64_t owner, int priority, float yaw, float pitch, int holdTicks) {
    if (!std::isfinite(yaw) || !std::isfinite(pitch) || !lease_.acquire(owner, priority, holdTicks)) return false;
    yaw_ = std::fmod(yaw, 360.0f);
    if (yaw_ >= 180.0f) yaw_ -= 360.0f;
    if (yaw_ < -180.0f) yaw_ += 360.0f;
    pitch_ = (std::max)(-90.0f, (std::min)(90.0f, pitch));
    return true;
}

void RotationManager::tick() { lease_.tick(); }
void RotationManager::release(std::uint64_t owner) {
    if (owner == 0 || lease_.ownedBy(owner)) reset();
}
void RotationManager::reset() { lease_.clear(); yaw_ = 0.0f; pitch_ = 0.0f; }

} }
