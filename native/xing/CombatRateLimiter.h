#pragma once

#include <array>
#include <cstdint>

namespace xing { namespace managers {

enum class CombatChannel { BlockPlace, ItemUse, Attack, Mine, GenericCombat, Count };

struct RateState {
    int used = 0;
    int limit = 0;
    int cooldown = 0;
    std::uint64_t lastOwner = 0;
};

class CombatRateLimiter {
public:
    CombatRateLimiter();
    void tick();
    void reset();
    bool canUse(int channel, int reserved = 0) const;
    void consume(int channel, std::uint64_t owner);
    void setLimit(int channel, int limit);
    void setCooldown(int channel, int ticks);
    void setProfileLimits(int blockPlace, int itemUse);
    RateState state(int channel) const;
    static bool validChannel(int channel);

private:
    static constexpr std::size_t CHANNEL_COUNT = static_cast<std::size_t>(CombatChannel::Count);
    std::array<RateState, CHANNEL_COUNT> states_{};
    std::array<int, CHANNEL_COUNT> cooldowns_{};
    int blockPlaceLimit_ = 2;
    int itemUseLimit_ = 8;
    void restoreLimits();
};

} }
