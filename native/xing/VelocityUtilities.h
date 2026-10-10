#pragma once
#include "VelocityTypes.h"
#include <limits>

namespace xing { namespace velocity {
constexpr std::int64_t noTick = (std::numeric_limits<std::int64_t>::min)();

// Shared movement, collision, phase, and correction utilities. Author: uint32.
class CollisionStateUtil {
public:
    static bool inside(const PlayerSnapshot& player, WorldAccess& world);
    static bool nearCollision(const PlayerSnapshot& player, double distance, WorldAccess& world);
    static bool wallContext(const PlayerSnapshot& player, WorldAccess& world);
};
class MovementState {
public:
    static bool movingOrInputting(const PlayerSnapshot& player);
    static Vector redirect(const PlayerSnapshot& player, Vector scaled);
};
class AnticheatManager {
public:
    void correct(const PlayerSnapshot& player);
    void expectTeleport(std::int64_t now, std::int64_t timeout);
    bool expected() const { return expected_; }
    bool recentlySetback(std::int64_t now, std::int64_t duration) const;
    void clear();
    std::int64_t sequence() const { return sequence_; }
    Vector position() const { return position_; }
    int teleportId() const { return teleportId_; }
private:
    std::int64_t lastSetback_ = -1, expectedUntil_ = -1, sequence_ = 0;
    bool expected_ = false;
    Vector position_;
    int teleportId_ = -1;
};
class PhasePushController {
public:
    void markRecent(std::int64_t tick, int ticks, bool extend);
    bool recent(std::int64_t tick) const { return tick <= recentUntil_; }
    void requestAssist(std::uint64_t owner, std::int64_t tick, int ticks, bool stopOnSetback, std::int64_t pause);
    void clearAssist(std::uint64_t owner);
    bool assistActive(const PlayerSnapshot& player, const AnticheatManager& anticheat);
    bool cancelPush(const PlayerSnapshot& player, const Settings& settings, WorldAccess& world, const AnticheatManager& anticheat);
    bool debugDue(std::int64_t now);
    void clear();
private:
    std::int64_t recentUntil_ = noTick, assistUntil_ = noTick, setbackPause_ = 0, lastDebug_ = 0;
    std::uint64_t assistOwner_ = 0;
    bool stopOnSetback_ = false;
};
} }
