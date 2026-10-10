#include "pch.h"
#include "VelocityBridge.h"
#include "VelocityModule.h"
#include <cmath>
#include <cstring>
#include <exception>

namespace xing { namespace velocity {
namespace {
VelocityModule module;
template<typename T> T read(const std::uint8_t* data, std::size_t offset) { T value; std::memcpy(&value, data + offset, sizeof(value)); return value; }
template<typename T> void write(std::uint8_t* data, std::size_t offset, T value) { std::memcpy(data + offset, &value, sizeof(value)); }
class JniWorldAccess final : public WorldAccess {
public:
    JniWorldAccess(JNIEnv* env, jobject world) : env_(env), world_(world) {
        const auto type = env_->GetObjectClass(world_);
        if (type == nullptr) return;
        collision_ = env_->GetMethodID(type, "blockCollision", "(DDDDDD)Z");
        if (!env_->ExceptionCheck()) air_ = env_->GetMethodID(type, "air", "(III)Z");
        env_->DeleteLocalRef(type);
    }
    bool healthy() const override { return !env_->ExceptionCheck() && air_ != nullptr; }
    bool blockCollision(const Bounds& box) override {
        return healthy() && env_->CallBooleanMethod(world_, collision_, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ) == JNI_TRUE;
    }
    bool air(int x, int y, int z) override { return healthy() && env_->CallBooleanMethod(world_, air_, x, y, z) == JNI_TRUE; }
private:
    JNIEnv* env_; jobject world_;
    jmethodID collision_ = nullptr, air_ = nullptr;
};
}
int dispatchVelocity(JNIEnv* env, int event, jobject request, jobject response, jobject world) {
    const auto* input = static_cast<std::uint8_t*>(env->GetDirectBufferAddress(request));
    auto* output = static_cast<std::uint8_t*>(env->GetDirectBufferAddress(response));
    if (input == nullptr || output == nullptr || env->GetDirectBufferCapacity(request) != 320
        || env->GetDirectBufferCapacity(response) != 112 || world == nullptr || read<int>(input, 0) != 1) return -2;
    try {
        PlayerSnapshot player; Settings settings;
        const int flags = read<int>(input, 4), options = read<int>(input, 12);
        const int mode = read<int>(input, 8), motionMode = read<int>(input, 92);
        if (mode < 0 || mode > 1 || motionMode < 0 || motionMode > 2) return -2;
        settings.mode = static_cast<Mode>(mode); settings.motionMode = static_cast<MotionMode>(motionMode);
        player.valid = (flags & 1) != 0; player.water = (flags & 2) != 0; player.lava = (flags & 4) != 0;
        player.gliding = (flags & 8) != 0; player.connected = (flags & 16) != 0; player.inputPresent = (flags & 32) != 0; player.enabled = (flags & 64) != 0;
        settings.cancelAll = (options & 1) != 0; settings.redirect = (options & 2) != 0; settings.walls = (options & 4) != 0;
        settings.noRotation = (options & 8) != 0; settings.whileLiquid = (options & 16) != 0; settings.whileElytra = (options & 32) != 0;
        settings.explosions = (options & 64) != 0; settings.phaseLock = (options & 128) != 0; settings.blockPush = (options & 256) != 0;
        settings.onlyIntersecting = (options & 512) != 0; settings.lenient = (options & 1024) != 0;
        settings.requireAssist = (options & 2048) != 0; settings.requireRecent = (options & 4096) != 0;
        settings.pushDebug = (options & 8192) != 0; settings.debug = (options & 16384) != 0;
        player.tick = read<std::int64_t>(input, 16); player.now = read<std::int64_t>(input, 24); player.world = read<std::int64_t>(input, 32);
        player.inputX = read<float>(input, 40); player.inputY = read<float>(input, 44); player.yaw = read<float>(input, 48);
        player.pitch = read<float>(input, 52); player.inputKeys = read<int>(input, 56);
        settings.horizontal = read<double>(input, 64); settings.vertical = read<double>(input, 72);
        settings.lagPauseMillis = read<double>(input, 80); settings.clippedGraceTicks = read<int>(input, 88); settings.nearDistance = read<double>(input, 96);
        player.incoming = {read<double>(input, 104), read<double>(input, 112), read<double>(input, 120)};
        player.packetKind = read<int>(input, 128); player.localMotion = read<int>(input, 132) != 0;
        player.bounds = {read<double>(input, 136), read<double>(input, 144), read<double>(input, 152), read<double>(input, 160), read<double>(input, 168), read<double>(input, 176)};
        player.position = {read<double>(input, 184), read<double>(input, 192), read<double>(input, 200)};
        player.serverYaw = read<float>(input, 208); player.serverPitch = read<float>(input, 212); player.teleportId = read<int>(input, 216);
        player.correctionPosition = {read<double>(input, 224), read<double>(input, 232), read<double>(input, 240)};
        player.velocity = {read<double>(input, 248), read<double>(input, 256), read<double>(input, 264)};
        const auto finiteVector = [](Vector value) { return std::isfinite(value.x) && std::isfinite(value.y) && std::isfinite(value.z); };
        if (!finiteVector(player.incoming) || !finiteVector(player.velocity) || !finiteVector(player.position)
            || !finiteVector(player.correctionPosition) || !std::isfinite(player.inputX) || !std::isfinite(player.inputY)
            || !std::isfinite(player.yaw) || !std::isfinite(player.pitch) || !std::isfinite(player.serverYaw) || !std::isfinite(player.serverPitch)
            || !finiteVector({ player.bounds.minX, player.bounds.minY, player.bounds.minZ })
            || !finiteVector({ player.bounds.maxX, player.bounds.maxY, player.bounds.maxZ })) return -2;
        if (!std::isfinite(settings.horizontal) || settings.horizontal < 0 || settings.horizontal > 100
            || !std::isfinite(settings.vertical) || settings.vertical < 0 || settings.vertical > 100
            || !std::isfinite(settings.nearDistance) || settings.nearDistance < .02 || settings.nearDistance > .35
            || !std::isfinite(settings.lagPauseMillis) || settings.lagPauseMillis < 0 || settings.lagPauseMillis > 1000
            || settings.clippedGraceTicks < 0 || settings.clippedGraceTicks > 20) return -2;
        JniWorldAccess access(env, world);
        if (!access.healthy()) return -3;
        module.refreshWorld(player.world);
        Reply reply; reply.movement = player.incoming;
        switch (event) {
        case 0: module.reset(); break;
        case 1: reply = module.tick(player, settings, access); break;
        case 2: reply = module.packet(player, settings, access); break;
        case 3: reply = module.explosion(player, settings, access); break;
        case 4: reply = module.push(player, settings, access); break;
        case 5: module.markPearl(); break;
        case 6: module.anticheat().expectTeleport(player.now, read<std::int64_t>(input, 280)); break;
        case 7: if (player.valid) module.phase().markRecent(player.tick, read<int>(input, 280), read<int>(input, 284) != 0); break;
        case 8: if (player.valid) module.phase().requestAssist(read<std::uint64_t>(input, 272), player.tick, read<int>(input, 280), read<int>(input, 284) != 0, read<std::int64_t>(input, 288)); break;
        case 9: module.phase().clearAssist(read<std::uint64_t>(input, 272)); break;
        case 10: break;
        default: return -1;
        }
        if (!access.healthy()) return -3;
        std::memset(output, 0, 112);
        write<int>(output, 0, 1);
        reply.debug = reply.debug || (settings.debug && reply.decision != Decision::None && reply.decision != Decision::PhasePush);
        write<int>(output, 4, (reply.cancel ? 1 : 0) | (reply.apply ? 2 : 0) | (reply.confirm ? 4 : 0)
            | (reply.positionOnly ? 8 : 0) | (reply.cancelPush ? 16 : 0) | (reply.debug ? 32 : 0));
        write<int>(output, 8, static_cast<int>(reply.decision));
        write<float>(output, 16, reply.yaw); write<float>(output, 20, reply.pitch);
        write<double>(output, 24, reply.movement.x); write<double>(output, 32, reply.movement.y); write<double>(output, 40, reply.movement.z);
        write<int>(output, 48, reply.blockX); write<int>(output, 52, reply.blockY); write<int>(output, 56, reply.blockZ);
        write<int>(output, 64, module.circuitOpen() ? 1 : 0); write<int>(output, 68, module.correctionBudget());
        write<int>(output, 72, module.anticheat().expected() ? 1 : 0); write<int>(output, 76, module.anticheat().teleportId());
        write<std::int64_t>(output, 80, module.anticheat().sequence());
        const auto position = module.anticheat().position();
        write<double>(output, 88, position.x); write<double>(output, 96, position.y); write<double>(output, 104, position.z);
        return 0;
    } catch (const std::exception&) { return -4; }
}
} }
