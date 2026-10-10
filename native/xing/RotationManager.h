#pragma once

#include "OwnershipLease.h"

namespace xing { namespace managers {

class RotationManager {
public:
    bool request(std::uint64_t owner, int priority, float yaw, float pitch, int holdTicks);
    bool requestLookAt(std::uint64_t owner, int priority, double x, double y, double z, int holdTicks);
    void tick();
    void release(std::uint64_t owner);
    void reset();
    bool active() const { return lease_.active(); }
    bool ownedBy(std::uint64_t owner) const { return lease_.ownedBy(owner); }
    float yaw() const { return yaw_; }
    float pitch() const { return pitch_; }

private:
    OwnershipLease lease_;
    float yaw_ = 0.0f;
    float pitch_ = 0.0f;
};

} }
