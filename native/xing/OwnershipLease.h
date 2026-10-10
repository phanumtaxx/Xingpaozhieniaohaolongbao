#pragma once

#include <algorithm>
#include <cstdint>

namespace xing { namespace managers {

// Shared resource ownership, ported from Synthetic by uint32.
class OwnershipLease {
public:
    bool canAcquire(std::uint64_t owner, int priority) const {
        return owner != 0 && (owner_ == 0 || owner_ == owner || priority > priority_);
    }

    bool acquire(std::uint64_t owner, int priority, int ticks) {
        if (!canAcquire(owner, priority)) return false;
        owner_ = owner;
        priority_ = priority;
        ticks_ = (std::max)(1, ticks);
        return true;
    }

    bool tick() {
        if (owner_ == 0) return false;
        if (--ticks_ > 0) return false;
        clear();
        return true;
    }

    bool ownedBy(std::uint64_t owner) const { return owner_ != 0 && owner_ == owner; }
    bool active() const { return owner_ != 0; }
    void clear() { owner_ = 0; priority_ = 0; ticks_ = 0; }

private:
    std::uint64_t owner_ = 0;
    int priority_ = 0;
    int ticks_ = 0;
};

} }
