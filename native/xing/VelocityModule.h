#pragma once
#include "VelocityUtilities.h"

namespace xing { namespace velocity {
// Synthetic Velocity's packet decisions and confirmation state. Author: uint32.
class VelocityModule {
public:
    void reset();
    void refreshWorld(std::int64_t world);
    Reply packet(const PlayerSnapshot& player, const Settings& settings, WorldAccess& world);
    Reply explosion(const PlayerSnapshot& player, const Settings& settings, WorldAccess& world);
    Reply tick(const PlayerSnapshot& player, const Settings& settings, WorldAccess& world);
    Reply push(const PlayerSnapshot& player, const Settings& settings, WorldAccess& world);
    void markPearl() { queued_ = false; }
    AnticheatManager& anticheat() { return anticheat_; }
    PhasePushController& phase() { return phase_; }
    bool circuitOpen() const { return circuitOpen_; }
    int correctionBudget() const { return correctionBudget_; }
private:
    static bool within(std::int64_t tick, std::int64_t previous, int window);
    static bool meaningful(Vector motion);
    static Vector scale(const PlayerSnapshot& player, const Settings& settings, bool redirect);
    bool pause(const PlayerSnapshot& player, const Settings& settings) const;
    bool phaseContext(const PlayerSnapshot& player, const Settings& settings, WorldAccess& world) const;
    Decision environment(const PlayerSnapshot& player, const Settings& settings) const;
    std::int64_t world_ = -1, lastInside_ = noTick, lastExplosion_ = noTick, lastConfirmation_ = noTick;
    bool queued_ = false, circuitOpen_ = false;
    int correctionBudget_ = 0;
    AnticheatManager anticheat_;
    PhasePushController phase_;
};
} }
