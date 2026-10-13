#include "pch.h"
#include "CrashoutModule.h"
#include <cmath>

namespace xing { namespace flight {
namespace {
constexpr double Radians = 0.017453292519943295;
float wrapDegrees(float angle) {
    angle = std::fmod(angle, 360.0f);
    if (angle >= 180.0f) angle -= 360.0f;
    if (angle < -180.0f) angle += 360.0f;
    return angle;
}
}

void CrashoutModule::resetSteering() {
    steeringReady_ = false;
    still_ = false;
    stillTicks_ = 0;
    flipBaseYaw_ = 0.0f;
    flipToggle_ = false;
    hasFirework_ = false;
}

void CrashoutModule::resetFlight() {
    resetSteering();
    fireworkUsedAt_ = 0;
    lastFireworkDuration_ = -1.0;
}

void CrashoutModule::onServerCorrection() {
    resetSteering();
}

void CrashoutModule::reset() {
    resetFlight();
    hotbarElytra_.reset();
    failure_ = ElytraHotswapUtil::Failure::None;
    controlYaw_ = 0.0f;
}

void CrashoutModule::disable(const FlightSnapshot& player, FlightGameAccess& game) {
    hotbarElytra_.restore(game);
    failure_ = hotbarElytra_.failure();
    game.clearRotation();
    resetFlight();
}

bool CrashoutModule::start(FlightSnapshot& player, FlightGameAccess& game) {
    failure_ = ElytraHotswapUtil::Failure::None;
    if (player.onGround || !player.alive) return false;
    if (player.chestGlider) return game.startGliding();
    const int slot = hotbarElytra_.prepare(player.inventory, player.selectedSlot, player.safeInventory, game);
    if (slot < 0) { failure_ = hotbarElytra_.failure(); return false; }
    if (!game.startGlidingWithElytra(slot)) {
        failure_ = ElytraHotswapUtil::Failure::InventoryBusy;
        return false;
    }
    return true;
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

bool CrashoutModule::rotate(const CrashoutSettings& settings,
        float yaw, float pitch, FlightGameAccess& game) {
    const movement::Rotation current = { game.currentYaw(), game.currentPitch() };
    if (!game.healthy()) return false;
    const auto rotation = movement::MovementController::steer(current, { yaw, pitch }, settings.turnSpeed);
    if (!game.requestClientRotation(rotation.yaw, rotation.pitch)) return false;
    controlYaw_ = rotation.yaw;
    return true;
}

void CrashoutModule::useFirework(const FlightSnapshot& player, const CrashoutSettings& settings, FlightGameAccess& game) {
    const double elapsed = (player.nowMillis - fireworkUsedAt_) / 1000.0;
    const double useAt = lastFireworkDuration_ < 0.0 ? 0.0 : (std::max)(0.0, lastFireworkDuration_ - settings.safetyMargin);
    if (fireworkUsedAt_ != 0 && elapsed < useAt) return;
    const int slot = FireworkInventoryUtil::find(player.inventory, settings.inventoryFireworks);
    if (slot < 0) return;
    if (FireworkInventoryUtil::use(player.inventory, slot, player.safeInventory, game)) {
        lastFireworkDuration_ = FireworkInventoryUtil::durationSeconds(player.inventory[slot]);
        fireworkUsedAt_ = player.nowMillis;
    }
}

void CrashoutModule::tick(const FlightSnapshot& snapshot, const CrashoutSettings& settings, FlightGameAccess& game) {
    if (!snapshot.valid || !snapshot.alive || snapshot.onGround) {
        failure_ = ElytraHotswapUtil::Failure::None;
        resetFlight();
        game.clearRotation();
        return;
    }
    if (!game.inventoryReady()) {
        resetSteering();
        failure_ = ElytraHotswapUtil::Failure::InventoryBusy;
        game.clearRotation();
        return;
    }
    failure_ = ElytraHotswapUtil::Failure::None;
    FlightSnapshot player = snapshot;
    if (!player.gliding) {
        resetFlight();
        if (!start(player, game)) {
            game.clearRotation();
            return;
        }
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
    if (flipFlopping) {
        game.setVelocity(0.0, 0.0, 0.0);
        if (!rotate(settings, flipToggle_ ? flipBaseYaw_ + 180.0f : flipBaseYaw_, 0.0f, game)) {
            resetSteering();
            game.clearRotation();
            return;
        }
        flipToggle_ = !flipToggle_;
    } else if (!still_) {
        const bool jump = player.jump && !player.sneak;
        const bool sneak = player.sneak && !player.jump;
        if (!rotate(settings, moveYaw(player), jump ? -90.0f : sneak ? 90.0f : 0.0f, game)) {
            resetSteering();
            game.clearRotation();
            return;
        }
        useFirework(player, settings, game);
    } else {
        game.setVelocity(0.0, 0.0, 0.0);
        game.clearRotation();
    }
    if (!game.healthy()) return;
    if (!still_) {
        const auto velocity = movement::MovementController::limitHorizontal(player.velocity, settings.crashoutHorizontal);
        if (velocity.x != player.velocity.x || velocity.z != player.velocity.z)
            game.setVelocity(velocity.x, velocity.y, velocity.z);
    }
    steeringReady_ = game.healthy();
}

Movement CrashoutModule::applyMovement(const FlightSnapshot& player, const CrashoutSettings& settings) const {
    if (!player.valid || !player.gliding) return player.movement;
    const auto limited = movement::MovementController::limitHorizontal(player.movement, settings.crashoutHorizontal);
    if (!steeringReady_) return limited;
    if (still_) return { 0.0, 0.0, 0.0 };
    if (player.jump != player.sneak) return limited;
    const double speed = (std::max)(0.08, std::hypot(limited.x, limited.z));
    const double yaw = controlYaw_ * Radians;
    return { -std::sin(yaw) * speed, player.movement.y * 0.35, std::cos(yaw) * speed };
}

bool CrashoutModule::allowMovementPacket(const FlightSnapshot& player, const CrashoutSettings& settings) const {
    if (!player.valid || !player.gliding || !steeringReady_ || !still_ || settings.flipFlop == FlipFlopMode::Full
        || (settings.flipFlop == FlipFlopMode::WithFirework && hasFirework_)) return true;
    return stillTicks_ % static_cast<unsigned int>((std::max)(1, settings.packetGap)) == 0;
}
} }
