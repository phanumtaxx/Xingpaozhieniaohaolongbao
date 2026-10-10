#include "pch.h"
#include "VelocityUtilities.h"
#include <algorithm>
#include <array>
#include <cmath>
#include <set>
#include <tuple>

namespace xing { namespace velocity {
bool CollisionStateUtil::inside(const PlayerSnapshot& player, WorldAccess& world) {
    return player.valid && world.blockCollision(player.bounds);
}
bool CollisionStateUtil::nearCollision(const PlayerSnapshot& player, double distance, WorldAccess& world) {
    if (!player.valid) return false;
    const double inflate = (std::max)(0.0, distance);
    auto box = player.bounds;
    box.minX -= inflate; box.minY -= .02; box.minZ -= inflate;
    box.maxX += inflate; box.maxY += .02; box.maxZ += inflate;
    return world.blockCollision(box);
}
bool CollisionStateUtil::wallContext(const PlayerSnapshot& player, WorldAccess& world) {
    if (!inside(player, world)) return false;
    constexpr double epsilon = 1.0E-4;
    const int minX = static_cast<int>(std::floor(player.bounds.minX + epsilon));
    const int maxX = static_cast<int>(std::floor(player.bounds.maxX - epsilon));
    const int minZ = static_cast<int>(std::floor(player.bounds.minZ + epsilon));
    const int maxZ = static_cast<int>(std::floor(player.bounds.maxZ - epsilon));
    const int y = static_cast<int>(std::floor(player.bounds.minY + epsilon));
    std::set<std::tuple<int, int>> seen;
    const int directions[4][2] = { {0, -1}, {0, 1}, {-1, 0}, {1, 0} };
    for (int x = minX; x <= maxX; ++x) for (int z = minZ; z <= maxZ; ++z) {
        for (const auto& direction : directions) {
            const int nx = x + direction[0], nz = z + direction[1];
            if (nx >= minX && nx <= maxX && nz >= minZ && nz <= maxZ) continue;
            if (seen.emplace(nx, nz).second && world.air(nx, y, nz)) return true;
            if (!world.healthy()) return false;
        }
    }
    return false;
}

bool MovementState::movingOrInputting(const PlayerSnapshot& player) {
    const float inputSquared = player.inputX * player.inputX + player.inputY * player.inputY;
    return player.valid && (player.velocity.x * player.velocity.x + player.velocity.z * player.velocity.z > 1.0E-5
        || (player.inputPresent && ((player.inputKeys & 31) != 0 || inputSquared > 1.0E-5f)));
}
Vector MovementState::redirect(const PlayerSnapshot& player, Vector scaled) {
    if (!player.valid || !player.inputPresent) return scaled;
    const double magnitude = std::sqrt(scaled.x * scaled.x + scaled.z * scaled.z);
    float squared = player.inputX * player.inputX + player.inputY * player.inputY;
    if (magnitude < 1.0E-6 || squared < 1.0E-5f) return scaled;
    float x = player.inputX, y = player.inputY;
    if (squared > 1.0f) { const float length = std::sqrt(squared); x /= length; y /= length; }
    static const auto sine = [] {
        std::array<float, 65536> values{};
        for (int i = 0; i < 65536; ++i) values[i] = static_cast<float>(std::sin(i * 3.14159265358979323846 * 2.0 / 65536.0));
        return values;
    }();
    const float radians = player.yaw * (3.14159265358979323846f / 180.0f);
    const float sin = sine[static_cast<int>(radians * 10430.378f) & 65535];
    const float cos = sine[static_cast<int>(radians * 10430.378f + 16384.0f) & 65535];
    const double dx = x * cos - y * sin, dz = x * sin + y * cos;
    const double length = std::sqrt(dx * dx + dz * dz);
    if (length < 1.0E-6) return scaled;
    const double factor = magnitude / length;
    return { dx * factor, scaled.y, dz * factor };
}

void AnticheatManager::correct(const PlayerSnapshot& player) {
    lastSetback_ = player.now; teleportId_ = player.teleportId; position_ = player.correctionPosition;
    expected_ = expectedUntil_ >= 0 && player.now <= expectedUntil_;
    ++sequence_; expectedUntil_ = -1;
}
void AnticheatManager::expectTeleport(std::int64_t now, std::int64_t timeout) { expectedUntil_ = now + (std::max)(std::int64_t{0}, timeout); }
bool AnticheatManager::recentlySetback(std::int64_t now, std::int64_t duration) const {
    return lastSetback_ >= 0 && now - lastSetback_ <= duration;
}
void AnticheatManager::clear() { lastSetback_ = -1; expectedUntil_ = -1; expected_ = false; position_ = {}; teleportId_ = -1; }

void PhasePushController::markRecent(std::int64_t tick, int ticks, bool extend) {
    const auto until = tick + (std::max)(0, ticks);
    recentUntil_ = extend ? (std::max)(recentUntil_, until) : until;
}
void PhasePushController::requestAssist(std::uint64_t owner, std::int64_t tick, int ticks, bool stopOnSetback, std::int64_t pause) {
    if (owner == 0 || ticks <= 0) { clearAssist(owner); return; }
    assistOwner_ = owner; assistUntil_ = tick + ticks;
    stopOnSetback_ = stopOnSetback; setbackPause_ = (std::max)(std::int64_t{0}, pause);
}
void PhasePushController::clearAssist(std::uint64_t owner) {
    if (assistOwner_ == 0 || assistOwner_ == owner) { assistOwner_ = 0; assistUntil_ = noTick; }
}
bool PhasePushController::assistActive(const PlayerSnapshot& player, const AnticheatManager& anticheat) {
    if (!player.valid || assistOwner_ == 0) return false;
    if ((stopOnSetback_ && anticheat.recentlySetback(player.now, setbackPause_)) || player.tick > assistUntil_) {
        clearAssist(assistOwner_); return false;
    }
    return true;
}
bool PhasePushController::cancelPush(const PlayerSnapshot& player, const Settings& settings, WorldAccess& world, const AnticheatManager& anticheat) {
    if (!settings.blockPush || !player.valid) return false;
    const bool inside = CollisionStateUtil::inside(player, world);
    const bool nearCollision = inside || CollisionStateUtil::nearCollision(player, .12, world);
    const bool assist = assistActive(player, anticheat), recentPhase = recent(player.tick);
    if (settings.onlyIntersecting && !inside && (!settings.lenient || !nearCollision)) return false;
    if (settings.requireAssist && !assist) return false;
    if (settings.requireRecent && !recentPhase) return false;
    if (player.water && !nearCollision) return false;
    if (player.gliding && (!nearCollision || (!assist && !recentPhase))) return false;
    return true;
}
bool PhasePushController::debugDue(std::int64_t now) {
    if (now - lastDebug_ < 500) return false;
    lastDebug_ = now; return true;
}
void PhasePushController::clear() { recentUntil_ = noTick; assistOwner_ = 0; assistUntil_ = noTick; }
} }
