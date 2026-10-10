#include "pch.h"
#include "CrashoutModule.h"
#include <cmath>
#include <chrono>

namespace xing { namespace flight {
namespace {
float wrapDegrees(float angle) {
    angle = std::fmod(angle, 360.0f);
    if (angle >= 180.0f) angle -= 360.0f;
    if (angle < -180.0f) angle += 360.0f;
    return angle;
}
float clamp(float value, float minimum, float maximum) {
    return (std::max)(minimum, (std::min)(maximum, value));
}
}

void CrashoutModule::resetFlight() {
    still_ = false; stillTicks_ = 0; flipBaseYaw_ = 0.0f; flipToggle_ = false;
    hasFirework_ = false; fireworkUsedAt_ = 0; lastFireworkDuration_ = -1.0;
}

void CrashoutModule::reset() { resetFlight(); hotswap_.reset(); controlYaw_ = 0.0f; controlPitch_ = 0.0f; }

void CrashoutModule::disable(const FlightSnapshot& player, FlightGameAccess& game) {
    hotswap_.restore(player.valid, player.alive, player.safeInventory, game);
    game.clearRotation();
    resetFlight();
}

bool CrashoutModule::start(const FlightSnapshot& player, FlightGameAccess& game) {
    if (player.onGround) return false;
    const bool temporarySwap = !player.chestGlider;
    if (temporarySwap && !hotswap_.equip(player.inventory, player.chest, player.safeInventory, game)) return false;
    if (!game.healthy()) return false;
    const bool started = game.startGliding();
    // Restore the chest slot even when sending the start packet fails.
    const bool restored = !temporarySwap || (game.healthy() && hotswap_.restore(player.valid, player.alive, player.safeInventory, game));
    return started && restored;
}

float CrashoutModule::moveYaw(const FlightSnapshot& player) {
    float yaw = player.yaw;
    const bool forward = player.forward && !player.backward;
    const bool backward = player.backward && !player.forward;
    const bool left = player.left && !player.right;
    const bool right = player.right && !player.left;
    if (forward) { if (left) yaw -= 45.0f; else if (right) yaw += 45.0f; }
    else if (backward) { yaw += 180.0f; if (left) yaw += 45.0f; else if (right) yaw -= 45.0f; }
    else if (left) yaw -= 90.0f;
    else if (right) yaw += 90.0f;
    return wrapDegrees(yaw);
}

void CrashoutModule::rotate(const CrashoutSettings& settings,
        float yaw, float pitch, FlightGameAccess& game) {
    const float currentYaw = game.currentYaw();
    const float currentPitch = game.currentPitch();
    if (!game.healthy()) return;
    controlYaw_ = wrapDegrees(currentYaw + clamp(wrapDegrees(yaw - currentYaw), -settings.turnSpeed, settings.turnSpeed));
    controlPitch_ = clamp(currentPitch + clamp(pitch - currentPitch, -settings.turnSpeed, settings.turnSpeed), -90.0f, 90.0f);
    game.requestRotation(controlYaw_, controlPitch_);
}

void CrashoutModule::useFirework(const FlightSnapshot& player, const CrashoutSettings& settings, FlightGameAccess& game) {
    const double elapsed = (player.nowMillis - fireworkUsedAt_) / 1000.0;
    const double useAt = lastFireworkDuration_ < 0.0 ? 0.0 : (std::max)(0.0, lastFireworkDuration_ - settings.safetyMargin);
    if (fireworkUsedAt_ != 0 && elapsed < useAt) return;
    const int slot = FireworkInventoryUtil::find(player.inventory, settings.inventoryFireworks);
    if (slot < 0) return;
    if (FireworkInventoryUtil::use(player.inventory, slot, player.safeInventory, game)) {
        lastFireworkDuration_ = FireworkInventoryUtil::durationSeconds(player.inventory[slot]);
        fireworkUsedAt_ = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::system_clock::now().time_since_epoch()).count();
    }
}

void CrashoutModule::tick(const FlightSnapshot& player, const CrashoutSettings& settings, FlightGameAccess& game) {
    if (!player.valid) { resetFlight(); return; }
    if (hotswap_.active() && !hotswap_.restore(player.valid, player.alive, player.safeInventory, game)) {
        game.clearRotation(); return;
    }
    if (!game.healthy()) return;
    if (!player.gliding) {
        resetFlight();
        if (!start(player, game)) { game.clearRotation(); return; }
    }
    if (!game.healthy()) return;
    const bool wasStill = still_;
    still_ = !player.moving && player.jump == player.sneak;
    if (still_ && !wasStill) {
        flipBaseYaw_ = player.hasServerRotation ? player.serverYaw : player.yaw;
        flipToggle_ = false;
    }
    stillTicks_ = still_ ? stillTicks_ + 1 : 0;
    hasFirework_ = FireworkInventoryUtil::find(player.inventory, settings.inventoryFireworks) >= 0;
    const bool flipFlopping = still_ && (settings.flipFlop == FlipFlopMode::Full
        || (settings.flipFlop == FlipFlopMode::WithFirework && hasFirework_));
    if (!still_) useFirework(player, settings, game);
    if (!game.healthy()) return;
    if (flipFlopping) {
        game.setVelocity(0.0, 0.0, 0.0);
        rotate(settings, flipToggle_ ? flipBaseYaw_ + 180.0f : flipBaseYaw_, 0.0f, game);
        flipToggle_ = !flipToggle_;
    } else if (!still_) {
        const bool jump = player.jump && !player.sneak;
        const bool sneak = player.sneak && !player.jump;
        rotate(settings, moveYaw(player), jump ? -90.0f : sneak ? 90.0f : 0.0f, game);
        if (jump || sneak) game.setVelocity(0.0, jump ? 1.0 : -1.0, 0.0);
    } else {
        game.setVelocity(0.0, 0.0, 0.0);
        game.clearRotation();
    }
}

Movement CrashoutModule::applyMovement(const FlightSnapshot& player) const {
    if (!player.valid || !player.gliding) return player.movement;
    if (still_) return {};
    if (player.jump && !player.sneak) return { 0.0, 1.0, 0.0 };
    if (player.sneak && !player.jump) return { 0.0, -1.0, 0.0 };
    const double speed = (std::max)(0.08, std::hypot(player.movement.x, player.movement.z));
    const double yaw = controlYaw_ * 0.017453292519943295;
    return { -std::sin(yaw) * speed, player.movement.y * 0.35, std::cos(yaw) * speed };
}

bool CrashoutModule::allowMovementPacket(const FlightSnapshot& player, const CrashoutSettings& settings) const {
    if (!player.valid || !player.gliding || !still_ || settings.flipFlop == FlipFlopMode::Full
        || (settings.flipFlop == FlipFlopMode::WithFirework && hasFirework_)) return true;
    return stillTicks_ % static_cast<unsigned int>((std::max)(1, settings.packetGap)) == 0;
}
bool CrashoutModule::hidePose(const FlightSnapshot& player, const CrashoutSettings& settings) const {
    return player.valid && player.gliding && settings.hideFlyPose;
}
bool CrashoutModule::spoofChestplate(const FlightSnapshot& player, const CrashoutSettings& settings) const {
    return player.valid && player.gliding && player.chestGlider && settings.spoofChestplate;
}
int CrashoutModule::chestplateSlot(const FlightSnapshot& player) const { return ArmorInventoryUtil::bestChestplate(player.inventory); }
} }
