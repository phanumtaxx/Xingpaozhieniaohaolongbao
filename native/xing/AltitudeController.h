#pragma once

namespace xing { namespace movement {

struct AltitudePlan {
    float pitch;
    bool needsRocket;
};

// Rocket pitch feedback for an idle height target. Author: uint32.
class AltitudeController {
public:
    void reset() { active_ = captured_ = false; }
    void suspend() { active_ = false; }
    bool active() const { return active_; }
    bool captured() const { return captured_; }
    double targetHeight() const { return targetHeight_; }
    AltitudePlan update(double height, double verticalSpeed, double boostTicks, double tolerance);
private:
    bool active_ = false, captured_ = false;
    double targetHeight_ = 0;
};

} }
