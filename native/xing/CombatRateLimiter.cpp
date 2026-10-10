#include "pch.h"
#include "CombatRateLimiter.h"
#include <algorithm>
#include <limits>

namespace xing { namespace managers {

CombatRateLimiter::CombatRateLimiter() { reset(); }
bool CombatRateLimiter::validChannel(int channel) {
    return channel >= 0 && channel < static_cast<int>(CombatChannel::Count);
}

void CombatRateLimiter::restoreLimits() {
    const int defaults[] = {blockPlaceLimit_, itemUseLimit_, 1, 1, (std::numeric_limits<int>::max)()};
    for (std::size_t index = 0; index < states_.size(); ++index) states_[index].limit = defaults[index];
}

void CombatRateLimiter::tick() {
    restoreLimits();
    for (auto& state : states_) {
        state.used = 0;
        if (state.cooldown > 0) --state.cooldown;
    }
}

void CombatRateLimiter::reset() {
    states_ = {};
    cooldowns_ = {};
    restoreLimits();
}

bool CombatRateLimiter::canUse(int channel, int reserved) const {
    if (channel == -1) return true;
    if (!validChannel(channel)) return false;
    const auto& value = states_[channel];
    return value.cooldown == 0 && static_cast<std::int64_t>(value.used) + reserved < value.limit;
}

void CombatRateLimiter::consume(int channel, std::uint64_t owner) {
    if (!validChannel(channel)) return;
    auto& value = states_[channel];
    if (value.used < (std::numeric_limits<int>::max)()) ++value.used;
    value.lastOwner = owner;
    value.cooldown = cooldowns_[channel];
}

void CombatRateLimiter::setLimit(int channel, int limit) {
    if (validChannel(channel)) states_[channel].limit = (std::max)(1, limit);
}
void CombatRateLimiter::setCooldown(int channel, int ticks) {
    if (validChannel(channel)) cooldowns_[channel] = (std::max)(0, ticks);
}
void CombatRateLimiter::setProfileLimits(int blockPlace, int itemUse) {
    blockPlaceLimit_ = (std::max)(1, blockPlace);
    itemUseLimit_ = (std::max)(1, itemUse);
    restoreLimits();
}
RateState CombatRateLimiter::state(int channel) const {
    return validChannel(channel) ? states_[channel] : RateState{};
}

} }
