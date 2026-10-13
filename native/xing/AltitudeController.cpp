#include "pch.h"
#include "AltitudeController.h"
#include <algorithm>
#include <cmath>

namespace xing { namespace movement {

AltitudePlan AltitudeController::update(double height, double verticalSpeed, double boostTicks, double tolerance) {
    if (!captured_) {
        targetHeight_ = height;
        captured_ = true;
    }
    active_ = true;
    const double error = targetHeight_ - height;
    const bool boosted = boostTicks > 0;
    const double lookAhead = boosted ? (std::min)(2.0, boostTicks) : 2.0;
    const double predictedHeight = height + verticalSpeed * lookAhead;
    const double predictedError = targetHeight_ - predictedHeight;
    const bool settled = std::abs(error) <= tolerance && std::abs(verticalSpeed) < 0.025;
    const double desiredVelocity = settled ? 0.0
        : (std::max)(-0.16, (std::min)(0.16, predictedError * 0.2));

    // Match the boost to the desired vertical speed, including on its first tick.
    const double lift = (std::max)(-1.0, (std::min)(1.0, (desiredVelocity - verticalSpeed * 0.5 + 0.02) / 0.85));
    const float pitch = static_cast<float>(-std::asin(lift) * 180.0 / 3.141592653589793);
    const bool needsRocket = !boosted && verticalSpeed < -0.025 && predictedError > tolerance;
    return {pitch, needsRocket};
}

} }
