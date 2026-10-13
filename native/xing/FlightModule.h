#pragma once
#include "CrashoutModule.h"

namespace xing { namespace flight {
enum class FlightMode { Creative, Grim, Crashout };
struct FlightSettings : CrashoutSettings {
    FlightMode mode = FlightMode::Creative;
    double horizontal = 3.0, vertical = 3.0;
    bool antiKick = false, pitch = false, firework = false, idleFlipFlop = true;
    int flipFlopTicks = 1;
};

// Synthetic's complete Flight module. Author: uint32.
class FlightModule {
public:
    void reset(FlightMode mode = FlightMode::Creative, bool clearExternal = true);
    void onServerCorrection();
    void disable(const FlightSnapshot& player, FlightGameAccess& game);
    void tick(const FlightSnapshot& player, const FlightSettings& settings, FlightGameAccess& game);
    Movement applyMovement(const FlightSnapshot& player, const FlightSettings& settings, bool enabled);
    bool allowMovementPacket(const FlightSnapshot& player, const FlightSettings& settings) const;
    bool hidePose(const FlightSnapshot& player, const FlightSettings& settings) const;
    bool spoofChestplate(const FlightSnapshot& player, const FlightSettings& settings) const;
    int chestplateSlot(const FlightSnapshot& player) const;
    int status(const FlightSnapshot& player, const FlightSettings& settings) const;
    void requestExternalMovement(std::uint64_t owner, int age, Movement movement);
    void clearExternalMovement(std::uint64_t owner);
private:
    void resetIdle();
    float idleYaw(const FlightSnapshot& player, int interval);
    void rotate(const FlightSnapshot& player, const FlightSettings& settings, float yaw, float pitch, FlightGameAccess& game);
    static float moveYaw(const FlightSnapshot& player);
    static bool rocketExpiring(const FlightSnapshot& player);
    CrashoutModule crashout_;
    ElytraHotswapUtil hotswap_;
    FlightMode previousMode_ = FlightMode::Creative;
    float controlYaw_ = 0.0f, controlPitch_ = 0.0f, idleBaseYaw_ = 0.0f;
    bool idleActive_ = false, idleToggle_ = false;
    int idleTicks_ = 0;
    std::int64_t kickTime_ = 0, fireTime_ = 0;
    std::uint64_t externalOwner_ = 0;
    int externalAge_ = 0;
    Movement externalMovement_;
};
} }
