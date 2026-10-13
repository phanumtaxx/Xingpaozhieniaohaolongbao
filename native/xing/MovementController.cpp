#include "pch.h"
#include "MovementController.h"
#include <algorithm>
#include <cmath>

namespace xing { namespace movement {
namespace {
constexpr int StableIntervalMillis = 5000;
constexpr int MaximumRecoveryMillis = 2000;
double wrapDegrees(double angle) {
    angle = std::fmod(angle, 360.0);
    if (angle >= 180.0) angle -= 360.0;
    if (angle < -180.0) angle += 360.0;
    return angle;
}
}

void MovementController::reset() {
    lastCorrectionAt_ = recoveryUntil_ = 0;
    correctionCount_ = 0;
}

void MovementController::corrected(std::int64_t nowMillis, int minimumPauseMillis) {
    if (lastCorrectionAt_ == 0 || nowMillis - lastCorrectionAt_ >= StableIntervalMillis) correctionCount_ = 0;
    correctionCount_ = (std::min)(6, correctionCount_ + 1);
    const int basePause = (std::max)(350, (std::min)(MaximumRecoveryMillis, minimumPauseMillis));
    recoveryUntil_ = nowMillis + basePause;
    lastCorrectionAt_ = nowMillis;
}

bool MovementController::resume(std::int64_t nowMillis) {
    if (nowMillis < recoveryUntil_) return false;
    recoveryUntil_ = 0;
    return true;
}

int MovementController::remainingRecoveryMillis(std::int64_t nowMillis) const {
    return static_cast<int>((std::max)(std::int64_t{0},
        (std::min)(static_cast<std::int64_t>(MaximumRecoveryMillis), recoveryUntil_ - nowMillis)));
}

Vector MovementController::limitHorizontal(Vector movement, double maximumSpeed) {
    if (!std::isfinite(maximumSpeed) || maximumSpeed < 0.0) return movement;
    const double speed = std::hypot(movement.x, movement.z);
    if (!std::isfinite(speed) || speed <= maximumSpeed) return movement;
    const double scale = maximumSpeed / speed;
    return { movement.x * scale, movement.y, movement.z * scale };
}

Rotation MovementController::steer(Rotation current, Rotation target, float maximumTurnDegrees) {
    if (!std::isfinite(current.yaw) || !std::isfinite(current.pitch) || !std::isfinite(target.yaw)
        || !std::isfinite(target.pitch) || !std::isfinite(maximumTurnDegrees)) return current;
    const double limit = (std::max)(0.0, (std::min)(180.0, static_cast<double>(maximumTurnDegrees)));
    const double yawChange = (std::max)(-limit, (std::min)(limit, wrapDegrees(target.yaw - current.yaw)));
    const double pitchChange = (std::max)(-limit, (std::min)(limit, static_cast<double>(target.pitch - current.pitch)));
    return { static_cast<float>(wrapDegrees(current.yaw + yawChange)),
        static_cast<float>((std::max)(-90.0, (std::min)(90.0, current.pitch + pitchChange))) };
}
} }
