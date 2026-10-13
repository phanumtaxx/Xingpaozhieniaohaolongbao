#pragma once
#include "FlightInventory.h"
#include "MovementController.h"
#include <cstdint>
#include <vector>

namespace xing { namespace flight {
enum class FlipFlopMode { Full, WithFirework, None };
struct CrashoutSettings {
    float turnSpeed = 25.0f;
    double safetyMargin = 0.2;
    double crashoutHorizontal = 0.35;
    int packetGap = 20;
    FlipFlopMode flipFlop = FlipFlopMode::Full;
    bool inventoryFireworks = true;
    bool hideFlyPose = true;
    bool spoofChestplate = false;
};
using Movement = movement::Vector;
struct FlightSnapshot {
    bool valid = false, alive = false, onGround = false, gliding = false;
    bool chestGlider = false, safeInventory = false, hasServerRotation = false, moving = false;
    bool forward = false, backward = false, left = false, right = false, jump = false, sneak = false;
    float yaw = 0.0f, pitch = 0.0f, serverYaw = 0.0f, serverPitch = 0.0f;
    std::int64_t nowMillis = 0;
    Movement movement, velocity;
    float inputForward = 0.0f, inputStrafe = 0.0f;
    int age = 0, selectedSlot = 0;
    struct Rocket { int life = 0, duration = 1; };
    std::vector<Rocket> attachedRockets;
    InventorySnapshot inventory;
    ItemSnapshot chest;
};

// Synthetic's Crashout flight behavior, ported to Xing by uint32.
class CrashoutModule {
public:
    void reset();
    void onServerCorrection();
    void tick(const FlightSnapshot& player, const CrashoutSettings& settings, FlightGameAccess& game);
    void disable(const FlightSnapshot& player, FlightGameAccess& game);
    Movement applyMovement(const FlightSnapshot& player, const CrashoutSettings& settings) const;
    bool allowMovementPacket(const FlightSnapshot& player, const CrashoutSettings& settings) const;
    ElytraHotswapUtil::Failure failure() const { return failure_; }
    bool hasPendingSwap() const { return hotbarElytra_.active(); }
private:
    void resetFlight();
    void resetSteering();
    bool start(FlightSnapshot& player, FlightGameAccess& game);
    bool rotate(const CrashoutSettings& settings, float yaw, float pitch, FlightGameAccess& game);
    void useFirework(const FlightSnapshot& player, const CrashoutSettings& settings, FlightGameAccess& game);
    static float moveYaw(const FlightSnapshot& player);

    HotbarElytraUtil hotbarElytra_;
    ElytraHotswapUtil::Failure failure_ = ElytraHotswapUtil::Failure::None;
    bool steeringReady_ = false;
    bool still_ = false, flipToggle_ = false, hasFirework_ = false;
    std::uint64_t stillTicks_ = 0;
    float flipBaseYaw_ = 0.0f, controlYaw_ = 0.0f;
    std::int64_t fireworkUsedAt_ = 0;
    double lastFireworkDuration_ = -1.0;
};
} }
