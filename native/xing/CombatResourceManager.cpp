#include "pch.h"
#include "CombatResourceManager.h"
#include <algorithm>
#include <limits>

namespace xing { namespace managers {

int CombatResourceManager::denied(std::uint64_t owner, int priority, std::uint32_t resources) const {
    for (std::size_t index = 0; index < leases_.size(); ++index) {
        if ((resources & (1U << index)) == 0) continue;
        if (committed_[index] != 0 && committed_[index] != owner) return static_cast<int>(index);
        const auto& lease = leases_[index];
        if (lease.owner != 0 && lease.owner != owner && priority <= lease.priority) return static_cast<int>(index);
    }
    return -1;
}

bool CombatResourceManager::acquire(std::uint64_t owner, int priority, int holdTicks, std::uint32_t resources) {
    if (owner == 0 || !validMask(resources) || denied(owner, priority, resources) != -1) return false;
    for (std::size_t index = 0; index < leases_.size(); ++index) {
        if ((resources & (1U << index)) != 0) leases_[index] = {owner, (std::max)(0, priority), (std::max)(1, holdTicks)};
    }
    return true;
}

void CombatResourceManager::commit(std::uint64_t owner, std::uint32_t resources) {
    for (std::size_t index = 0; index < leases_.size(); ++index) {
        if ((resources & (1U << index)) != 0 && committed_[index] == 0) committed_[index] = owner;
    }
}

void CombatResourceManager::release(std::uint64_t owner, std::uint32_t resources) {
    for (std::size_t index = 0; index < leases_.size(); ++index) {
        if ((resources & (1U << index)) != 0 && leases_[index].owner == owner) leases_[index] = {};
    }
}

void CombatResourceManager::tick() {
    committed_ = {};
    for (auto& lease : leases_) {
        if (lease.owner != 0 && lease.ticks != (std::numeric_limits<int>::max)() && --lease.ticks <= 0) lease = {};
    }
}
void CombatResourceManager::reset() { leases_ = {}; committed_ = {}; }

} }
