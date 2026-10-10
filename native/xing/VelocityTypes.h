#pragma once
#include <cstdint>

namespace xing { namespace velocity {
struct Vector { double x = 0, y = 0, z = 0; };
struct Bounds { double minX = 0, minY = 0, minZ = 0, maxX = 0, maxY = 0, maxZ = 0; };
struct PlayerSnapshot {
    bool valid = false, water = false, lava = false, gliding = false, connected = false, inputPresent = false;
    bool enabled = false, localMotion = false;
    int packetKind = 0, teleportId = -1, inputKeys = 0;
    std::int64_t tick = 0, now = 0, world = 0;
    float inputX = 0, inputY = 0, yaw = 0, pitch = 0, serverYaw = 0, serverPitch = 0;
    Vector incoming, velocity, position, correctionPosition;
    Bounds bounds;
};
enum class Mode { Ncp, GrimV3 };
enum class MotionMode { Always, OnlyStill, Never };
struct Settings {
    Mode mode = Mode::GrimV3;
    MotionMode motionMode = MotionMode::OnlyStill;
    double horizontal = 0, vertical = 0, lagPauseMillis = 250, nearDistance = .18;
    int clippedGraceTicks = 6;
    bool cancelAll = false, redirect = false, walls = true, noRotation = false;
    bool whileLiquid = false, whileElytra = false, explosions = true, phaseLock = true;
    bool blockPush = true, onlyIntersecting = true, lenient = true;
    bool requireAssist = false, requireRecent = false, pushDebug = false, debug = false;
};
enum class Decision { None, NcpMotion, GrimCanceled, CorrectionDrain, ExplosionScaled,
    ExplosionAllowed, SetbackPause, Liquid, Elytra, Confirmation, CircuitOpened, PhasePush };
struct Reply {
    bool cancel = false, apply = false, confirm = false, positionOnly = false, cancelPush = false, debug = false;
    Vector movement;
    float yaw = 0, pitch = 0;
    int blockX = 0, blockY = 0, blockZ = 0;
    Decision decision = Decision::None;
};
class WorldAccess {
public:
    virtual ~WorldAccess() = default;
    virtual bool healthy() const = 0;
    virtual bool blockCollision(const Bounds& bounds) = 0;
    virtual bool air(int x, int y, int z) = 0;
};
} }
