#include "pch.h"
#include "VelocityModule.h"
#include <cmath>

namespace xing { namespace velocity {
void VelocityModule::reset() {
    queued_ = false; lastInside_ = noTick; lastExplosion_ = noTick; lastConfirmation_ = noTick; correctionBudget_ = 0;
}
void VelocityModule::refreshWorld(std::int64_t world) {
    if (world == world_) return;
    world_ = world; circuitOpen_ = false; queued_ = false; correctionBudget_ = 0; lastConfirmation_ = noTick;
    anticheat_.clear(); phase_.clear();
}
bool VelocityModule::within(std::int64_t tick, std::int64_t previous, int window) {
    return previous != noTick && tick >= previous && tick - previous <= window;
}
bool VelocityModule::meaningful(Vector motion) { return motion.x * motion.x + motion.z * motion.z > 1.0E-6 || motion.y > .01; }
Vector VelocityModule::scale(const PlayerSnapshot& player, const Settings& settings, bool redirect) {
    if (settings.cancelAll) return {};
    const double horizontalScale = settings.horizontal / 100.0, verticalScale = settings.vertical / 100.0;
    Vector scaled{ player.incoming.x * horizontalScale, player.incoming.y * verticalScale,
        player.incoming.z * horizontalScale };
    return redirect && settings.redirect ? MovementState::redirect(player, scaled) : scaled;
}
bool VelocityModule::pause(const PlayerSnapshot& player, const Settings& settings) const {
    if (anticheat_.expected()) return false;
    return settings.mode == Mode::GrimV3 ? correctionBudget_ > 0
        : anticheat_.recentlySetback(player.now, static_cast<std::int64_t>(settings.lagPauseMillis));
}
bool VelocityModule::phaseContext(const PlayerSnapshot& player, const Settings& settings, WorldAccess& world) const {
    if (!settings.phaseLock || !player.valid) return false;
    if (CollisionStateUtil::inside(player, world)) return true;
    const bool recentInside = lastInside_ != noTick && player.tick - lastInside_ <= settings.clippedGraceTicks;
    return CollisionStateUtil::nearCollision(player, settings.nearDistance, world) && (phase_.recent(player.tick) || recentInside);
}
Decision VelocityModule::environment(const PlayerSnapshot& player, const Settings& settings) const {
    if ((player.water || player.lava) && !settings.whileLiquid) return Decision::Liquid;
    if (player.gliding && !settings.whileElytra) return Decision::Elytra;
    return Decision::None;
}
Reply VelocityModule::packet(const PlayerSnapshot& player, const Settings& settings, WorldAccess& world) {
    Reply reply; reply.movement = player.incoming;
    if (!player.valid) return reply;
    if (player.packetKind == 2) {
        anticheat_.correct(player);
        if (!player.enabled) return reply;
        if (!circuitOpen_ && !anticheat_.expected() && within(player.tick, lastConfirmation_, 8)) {
            circuitOpen_ = true; reply.decision = Decision::CircuitOpened;
        }
        correctionBudget_ = settings.mode == Mode::GrimV3 && !anticheat_.expected() ? 5 : 0;
        queued_ = false;
        return reply;
    }
    if (!player.enabled) return reply;
    const int guardBefore = correctionBudget_;
    if (correctionBudget_ > 0) --correctionBudget_;
    if (player.packetKind != 1 || !player.localMotion) return reply;
    if (settings.mode == Mode::GrimV3 && guardBefore > 0) {
        reply.cancel = phaseContext(player, settings, world) && meaningful(player.incoming);
        queued_ = false; reply.decision = Decision::CorrectionDrain;
        return reply;
    }
    if (settings.mode == Mode::Ncp) {
        reply.cancel = true; reply.apply = true; reply.movement = scale(player, settings, true);
        reply.decision = Decision::NcpMotion;
        return reply;
    }
    if (pause(player, settings)) { queued_ = false; reply.decision = Decision::SetbackPause; return reply; }
    reply.cancel = true;
    if (meaningful(player.incoming) && !within(player.tick, lastExplosion_, 2)
        && !within(player.tick, lastConfirmation_, 3) && !circuitOpen_) {
        if (std::abs(player.incoming.x) > 1.0E-6 || std::abs(player.incoming.y) > 1.0E-6 || std::abs(player.incoming.z) > 1.0E-6) queued_ = true;
    }
    reply.decision = Decision::GrimCanceled;
    return reply;
}
Reply VelocityModule::explosion(const PlayerSnapshot& player, const Settings& settings, WorldAccess&) {
    Reply reply; reply.movement = player.incoming;
    if (!player.enabled || !player.valid || !settings.explosions) return reply;
    if (pause(player, settings)) { reply.decision = Decision::SetbackPause; return reply; }
    if (settings.mode != Mode::Ncp) {
        reply.decision = environment(player, settings);
        if (reply.decision != Decision::None) return reply;
    }
    reply.movement = scale(player, settings, !within(player.tick, lastExplosion_, 2));
    lastExplosion_ = player.tick;
    reply.decision = Decision::ExplosionScaled;
    return reply;
}
Reply VelocityModule::tick(const PlayerSnapshot& player, const Settings& settings, WorldAccess& world) {
    Reply reply;
    if (!player.enabled) return reply;
    if (CollisionStateUtil::inside(player, world)) lastInside_ = player.tick;
    if (!queued_) return reply;
    queued_ = false;
    if (!player.valid || !player.connected || circuitOpen_ || pause(player, settings) || settings.mode != Mode::GrimV3) return reply;
    const bool strict = settings.walls && CollisionStateUtil::wallContext(player, world);
    lastConfirmation_ = player.tick;
    reply.confirm = true; reply.positionOnly = settings.noRotation && !strict;
    reply.movement = player.position; reply.yaw = player.serverYaw; reply.pitch = strict ? 89.0f : player.serverPitch;
    reply.blockX = static_cast<int>(std::floor((player.bounds.minX + player.bounds.maxX) * .5));
    reply.blockY = static_cast<int>(std::floor(player.bounds.minY));
    reply.blockZ = static_cast<int>(std::floor((player.bounds.minZ + player.bounds.maxZ) * .5));
    reply.decision = Decision::Confirmation;
    return reply;
}
Reply VelocityModule::push(const PlayerSnapshot& player, const Settings& settings, WorldAccess& world) {
    Reply reply;
    reply.cancelPush = player.enabled && phase_.cancelPush(player, settings, world, anticheat_);
    if (reply.cancelPush) {
        reply.decision = Decision::PhasePush;
        reply.debug = settings.pushDebug && phase_.debugDue(player.now);
    }
    return reply;
}
} }
