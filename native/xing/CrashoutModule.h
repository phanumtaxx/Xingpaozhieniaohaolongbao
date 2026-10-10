#pragma once
#include "FlightInventory.h"
#include <cstdint>

namespace xing { namespace flight {
enum class FlipFlopMode { Full, WithFirework, None };
struct CrashoutSettings {
    float turnSpeed = 25.0f;
    double safetyMargin = 0.2;
    int packetGap = 20;
    FlipFlopMode flipFlop = FlipFlopMode::Full;
    bool inventoryFireworks = true;
    bool hideFlyPose = true;
    bool spoofChestplate = false;
};
struct Movement { double x = 0.0, y = 0.0, z = 0.0; };
struct FlightSnapshot {
    bool valid = false, alive = false, onGround = false, gliding = false;
    bool chestGlider = false, safeInventory = false, hasServerRotation = false, moving = false;
    bool forward = false, backward = false, left = false, right = false, jump = false, sneak = false;
    float yaw = 0.0f, pitch = 0.0f, serverYaw = 0.0f, serverPitch = 0.0f;
    std::int64_t nowMillis = 0;
    Movement movement;
    InventorySnapshot inventory;
    ItemSnapshot chest;
};

// Synthetic's Crashout flight behavior, ported to Xing by uint32.
class CrashoutModule {
public:
    void reset();
    void tick(const FlightSnapshot& player, const CrashoutSettings& settings, FlightGameAccess& game);
    void disable(const FlightSnapshot& player, FlightGameAccess& game);
    Movement applyMovement(const FlightSnapshot& player) const;
    bool allowMovementPacket(const FlightSnapshot& player, const CrashoutSettings& settings) const;
    bool hidePose(const FlightSnapshot& player, const CrashoutSettings& settings) const;
    bool spoofChestplate(const FlightSnapshot& player, const CrashoutSettings& settings) const;
    int chestplateSlot(const FlightSnapshot& player) const;
    ElytraHotswapUtil::Failure failure() const { return hotswap_.failure(); }
private:
    void resetFlight();
    bool start(const FlightSnapshot& player, FlightGameAccess& game);
    void rotate(const CrashoutSettings& settings, float yaw, float pitch, FlightGameAccess& game);
    void useFirework(const FlightSnapshot& player, const CrashoutSettings& settings, FlightGameAccess& game);
    static float moveYaw(const FlightSnapshot& player);
    ElytraHotswapUtil hotswap_;
    bool still_ = false, flipToggle_ = false, hasFirework_ = false;
    std::uint64_t stillTicks_ = 0;
    float flipBaseYaw_ = 0.0f, controlYaw_ = 0.0f, controlPitch_ = 0.0f;
    std::int64_t fireworkUsedAt_ = 0;
    double lastFireworkDuration_ = -1.0;
};
} }
