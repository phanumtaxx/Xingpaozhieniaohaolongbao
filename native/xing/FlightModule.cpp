#include "pch.h"
#include "FlightModule.h"
#include <cmath>

namespace xing { namespace flight {
namespace {
constexpr double Radians = 0.017453292519943295;
float wrap(float value) {
    value = std::fmod(value, 360.0f);
    return value >= 180.0f ? value - 360.0f : value < -180.0f ? value + 360.0f : value;
}
float clamp(float value, float limit) { return (std::max)(-limit, (std::min)(limit, value)); }
}

void FlightModule::resetIdle() { idleActive_ = false; idleToggle_ = false; idleTicks_ = 0; }
void FlightModule::reset(FlightMode mode, bool clearExternal) {
    crashout_.reset(); hotswap_.reset(); resetIdle();
    kickTime_ = 0; fireTime_ = 0; controlYaw_ = 0; controlPitch_ = 0;
    if (clearExternal) externalOwner_ = 0;
    previousMode_ = mode;
}
void FlightModule::onServerCorrection() {
    crashout_.onServerCorrection();
}
void FlightModule::disable(const FlightSnapshot& player, FlightGameAccess& game) {
    crashout_.disable(player, game);
    hotswap_.restore(player.valid, player.alive, player.safeInventory, game);
    game.clearRotation(); resetIdle(); kickTime_ = 0; fireTime_ = 0;
}
float FlightModule::moveYaw(const FlightSnapshot& player) {
    float yaw = player.yaw;
    if (player.forward && !player.backward) {
        if (player.left && !player.right) yaw -= 45;
        else if (player.right && !player.left) yaw += 45;
    } else if (player.backward && !player.forward) {
        yaw += 180;
        if (player.left && !player.right) yaw += 45;
        else if (player.right && !player.left) yaw -= 45;
    } else if (player.left && !player.right) yaw -= 90;
    else if (player.right && !player.left) yaw += 90;
    return wrap(yaw);
}
float FlightModule::idleYaw(const FlightSnapshot& player, int interval) {
    if (!idleActive_) {
        idleActive_ = true; idleBaseYaw_ = player.yaw; idleToggle_ = false; idleTicks_ = 0;
    }
    if (idleTicks_++ >= interval) { idleToggle_ = !idleToggle_; idleTicks_ = 0; }
    return idleToggle_ ? idleBaseYaw_ + 180 : idleBaseYaw_;
}
void FlightModule::rotate(const FlightSnapshot& player, const FlightSettings& settings,
    float yaw, float pitch, FlightGameAccess& game) {
    controlYaw_ = wrap(player.yaw + clamp(wrap(yaw - player.yaw), settings.turnSpeed));
    controlPitch_ = (std::max)(-90.0f, (std::min)(90.0f, player.pitch + clamp(pitch - player.pitch, settings.turnSpeed)));
    game.requestRotation(controlYaw_, controlPitch_);
}
bool FlightModule::rocketExpiring(const FlightSnapshot& player) {
    const FlightSnapshot::Rocket* newest = nullptr;
    for (const auto& rocket : player.attachedRockets)
        if (newest == nullptr || rocket.life < newest->life) newest = &rocket;
    return newest == nullptr || newest->life >= 10 * newest->duration + 5 - 3;
}
void FlightModule::tick(const FlightSnapshot& player, const FlightSettings& settings, FlightGameAccess& game) {
    if (!player.valid) return;
    if (settings.mode != previousMode_) {
        disable(player, game);
        if (!game.healthy() || hotswap_.active() || crashout_.hasPendingSwap()) return;
        crashout_.reset(); resetIdle(); previousMode_ = settings.mode;
        return;
    }
    if (settings.mode == FlightMode::Creative) return;
    if (settings.mode == FlightMode::Crashout) { crashout_.tick(player, settings, game); return; }
    const int elytra = ElytraHotswapUtil::find(player.inventory);
    if (!player.chestGlider && elytra < 0) { game.clearRotation(); resetIdle(); return; }
    const bool idle = !player.moving && player.jump == player.sneak;
    if (idle && (!settings.idleFlipFlop || !player.gliding)) { game.clearRotation(); resetIdle(); return; }
    if (!idle) resetIdle();
    bool gliding = player.gliding;
    if (!gliding) {
        if (player.onGround) { game.jump(); return; }
        if (player.chestGlider || hotswap_.equip(player.inventory, player.chest, player.safeInventory, game))
            gliding = game.startGliding();
    }
    if (!game.healthy()) return;
    const float pitch = idle ? 0.1f : player.jump && !player.sneak ? -50.0f : player.sneak && !player.jump ? 50.0f : 0.1f;
    rotate(player, settings, idle ? idleYaw(player, settings.flipFlopTicks) : moveYaw(player), pitch, game);
    if (settings.firework && gliding && rocketExpiring(player) && player.nowMillis - fireTime_ >= 200) {
        const int rocket = FireworkInventoryUtil::find(player.inventory, true);
        if (rocket >= 0) {
            if (FireworkInventoryUtil::use(player.inventory, rocket, player.safeInventory, game))
                fireTime_ = player.nowMillis;
        }
    }
}
Movement FlightModule::applyMovement(const FlightSnapshot& player, const FlightSettings& settings, bool enabled) {
    const auto age = static_cast<std::int64_t>(player.age) - externalAge_;
    if (player.valid && externalOwner_ != 0 && age >= 0 && age <= 2) return externalMovement_;
    if (!enabled || !player.valid) return player.movement;
    if (settings.mode == FlightMode::Creative) {
        double y = player.jump ? settings.vertical : player.sneak ? -settings.vertical : 0;
        if (settings.antiKick && player.nowMillis - kickTime_ >= 3800) { y = -0.04; kickTime_ = player.nowMillis; }
        const auto angle = (player.yaw + 90.0f) * Radians;
        const auto rx = std::cos(angle), rz = std::sin(angle);
        return { player.inputForward * settings.horizontal * rx + player.inputStrafe * settings.horizontal * rz,
            y, player.inputForward * settings.horizontal * rz - player.inputStrafe * settings.horizontal * rx };
    }
    if (settings.mode == FlightMode::Crashout)
        return crashout_.applyMovement(player, settings);
    if (!player.gliding) return player.movement;
    const auto yaw = controlYaw_ * Radians, pitch = controlPitch_ * Radians;
    constexpr double acceleration = 0.0264444413;
    double y = -std::sin(pitch) * acceleration;
    if (player.jump && !player.sneak) y += 0.05;
    else if (player.sneak && !player.jump) y -= 0.08;
    return { player.movement.x - std::sin(yaw) * std::cos(pitch) * acceleration,
        player.movement.y + y, player.movement.z + std::cos(yaw) * std::cos(pitch) * acceleration };
}
bool FlightModule::allowMovementPacket(const FlightSnapshot& player, const FlightSettings& settings) const {
    return settings.mode != FlightMode::Crashout || crashout_.allowMovementPacket(player, settings);
}
bool FlightModule::hidePose(const FlightSnapshot& player, const FlightSettings& settings) const {
    return settings.mode != FlightMode::Creative && player.valid && player.gliding && settings.hideFlyPose;
}
bool FlightModule::spoofChestplate(const FlightSnapshot& player, const FlightSettings& settings) const {
    return settings.mode != FlightMode::Creative && player.valid && player.gliding
        && player.chestGlider && settings.spoofChestplate;
}
int FlightModule::chestplateSlot(const FlightSnapshot& player) const { return ArmorInventoryUtil::bestChestplate(player.inventory); }
int FlightModule::status(const FlightSnapshot& player, const FlightSettings& settings) const {
    if (settings.mode == FlightMode::Crashout) {
        return static_cast<int>(crashout_.failure());
    }
    return static_cast<int>(hotswap_.failure());
}
void FlightModule::requestExternalMovement(std::uint64_t owner, int age, Movement movement) {
    if (owner == 0) return;
    externalOwner_ = owner; externalAge_ = age; externalMovement_ = movement;
}
void FlightModule::clearExternalMovement(std::uint64_t owner) { if (owner != 0 && externalOwner_ == owner) externalOwner_ = 0; }
} }
