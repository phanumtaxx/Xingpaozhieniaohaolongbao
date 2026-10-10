#include "pch.h"
#include "CrashoutBridge.h"
#include "CrashoutModule.h"
#include <cmath>
#include <cstring>
#include <exception>

namespace xing { namespace flight {
namespace {
CrashoutModule module;
template<typename T> T read(const std::uint8_t* input, std::size_t offset) {
    T value; std::memcpy(&value, input + offset, sizeof(value)); return value;
}
template<typename T> void write(std::uint8_t* output, std::size_t offset, T value) {
    std::memcpy(output + offset, &value, sizeof(value));
}

class JniFlightGameAccess final : public FlightGameAccess {
public:
    JniFlightGameAccess(JNIEnv* env, jobject game) : env_(env), game_(game) {
        jclass type = env_->GetObjectClass(game_);
        if (type == nullptr) return;
        swap_ = env_->GetMethodID(type, "swapChest", "(II)Z");
        if (!env_->ExceptionCheck()) start_ = env_->GetMethodID(type, "startGliding", "()Z");
        if (!env_->ExceptionCheck()) rocket_ = env_->GetMethodID(type, "useRocket", "(IIZ)Z");
        if (!env_->ExceptionCheck()) rotate_ = env_->GetMethodID(type, "requestRotation", "(FF)V");
        if (!env_->ExceptionCheck()) clear_ = env_->GetMethodID(type, "clearRotation", "()V");
        if (!env_->ExceptionCheck()) velocity_ = env_->GetMethodID(type, "setVelocity", "(DDD)V");
        if (!env_->ExceptionCheck()) yaw_ = env_->GetMethodID(type, "currentYaw", "()F");
        if (!env_->ExceptionCheck()) pitch_ = env_->GetMethodID(type, "currentPitch", "()F");
        env_->DeleteLocalRef(type);
    }
    bool healthy() const override { return !env_->ExceptionCheck() && pitch_ != nullptr; }
    bool swapChest(int slot, int menuSlot) override {
        return healthy() && env_->CallBooleanMethod(game_, swap_, slot, menuSlot) == JNI_TRUE && healthy();
    }
    bool startGliding() override {
        return healthy() && env_->CallBooleanMethod(game_, start_) == JNI_TRUE && healthy();
    }
    bool useRocket(int slot, int menuSlot, bool hotbar) override {
        return healthy() && env_->CallBooleanMethod(game_, rocket_, slot, menuSlot, hotbar ? JNI_TRUE : JNI_FALSE) == JNI_TRUE && healthy();
    }
    void requestRotation(float yaw, float pitch) override { if (healthy()) env_->CallVoidMethod(game_, rotate_, yaw, pitch); }
    float currentYaw() override { return healthy() ? env_->CallFloatMethod(game_, yaw_) : 0.0f; }
    float currentPitch() override { return healthy() ? env_->CallFloatMethod(game_, pitch_) : 0.0f; }
    void clearRotation() override { if (healthy()) env_->CallVoidMethod(game_, clear_); }
    void setVelocity(double x, double y, double z) override { if (healthy()) env_->CallVoidMethod(game_, velocity_, x, y, z); }
private:
    JNIEnv* env_;
    jobject game_;
    jmethodID swap_ = nullptr, start_ = nullptr, rocket_ = nullptr, rotate_ = nullptr, clear_ = nullptr, velocity_ = nullptr;
    jmethodID yaw_ = nullptr, pitch_ = nullptr;
};

bool decode(const std::uint8_t* input, std::size_t size, FlightSnapshot& player, CrashoutSettings& settings) {
    if (size < 128 || read<int>(input, 0) != 1) return false;
    const int flags = read<int>(input, 4), keys = read<int>(input, 8);
    player.valid = (flags & 1) != 0; player.alive = (flags & 2) != 0;
    player.onGround = (flags & 4) != 0; player.gliding = (flags & 8) != 0;
    player.chestGlider = (flags & 16) != 0; player.safeInventory = (flags & 32) != 0;
    player.hasServerRotation = (flags & 64) != 0; player.moving = (flags & 128) != 0;
    player.forward = (keys & 1) != 0; player.backward = (keys & 2) != 0;
    player.left = (keys & 4) != 0; player.right = (keys & 8) != 0;
    player.jump = (keys & 16) != 0; player.sneak = (keys & 32) != 0;
    const int flipFlop = read<int>(input, 12);
    if (flipFlop < 0 || flipFlop > 2) return false;
    settings.flipFlop = static_cast<FlipFlopMode>(flipFlop);
    player.nowMillis = read<std::int64_t>(input, 16);
    player.yaw = read<float>(input, 24); player.pitch = read<float>(input, 28);
    player.serverYaw = read<float>(input, 32); player.serverPitch = read<float>(input, 36);
    settings.turnSpeed = read<float>(input, 40); settings.packetGap = read<int>(input, 44);
    settings.safetyMargin = read<double>(input, 48);
    settings.inventoryFireworks = read<int>(input, 56) != 0;
    settings.hideFlyPose = read<int>(input, 60) != 0; settings.spoofChestplate = read<int>(input, 64) != 0;
    player.movement = { read<double>(input, 72), read<double>(input, 80), read<double>(input, 88) };
    if (!std::isfinite(player.yaw) || !std::isfinite(player.pitch) || !std::isfinite(player.serverYaw)
        || !std::isfinite(player.serverPitch) || !std::isfinite(settings.turnSpeed) || settings.turnSpeed < 1.0f
        || settings.turnSpeed > 180.0f || settings.packetGap < 1 || settings.packetGap > 100
        || !std::isfinite(settings.safetyMargin) || settings.safetyMargin < 0.0 || settings.safetyMargin > 2.0
        || !std::isfinite(player.movement.x) || !std::isfinite(player.movement.y) || !std::isfinite(player.movement.z)) return false;
    const int count = read<int>(input, 96);
    if (count != 0 && count != 37) return false;
    std::size_t offset = 128;
    for (int index = 0; index < count; ++index) {
        if (offset > size || size - offset < 20) return false;
        const int itemFlags = read<int>(input, offset);
        ItemSnapshot& item = index == 36 ? player.chest : player.inventory[index];
        item.empty = (itemFlags & 1) != 0; item.elytra = (itemFlags & 2) != 0;
        item.rocket = (itemFlags & 4) != 0; item.damageable = (itemFlags & 8) != 0;
        item.chestArmor = (itemFlags & 16) != 0;
        item.damage = read<int>(input, offset + 4); item.maximumDamage = read<int>(input, offset + 8);
        item.rocketDuration = read<int>(input, offset + 12);
        const int pathSize = read<int>(input, offset + 16);
        offset += 20;
        if (pathSize < 0 || static_cast<std::size_t>(pathSize) > size - offset) return false;
        item.path.assign(reinterpret_cast<const char*>(input + offset), pathSize);
        offset += pathSize;
    }
    return true;
}
}

int dispatchCrashout(JNIEnv* env, int event, jobject inputBuffer, jobject outputBuffer, jobject game) {
    auto* input = static_cast<std::uint8_t*>(env->GetDirectBufferAddress(inputBuffer));
    auto* output = static_cast<std::uint8_t*>(env->GetDirectBufferAddress(outputBuffer));
    const auto size = env->GetDirectBufferCapacity(inputBuffer);
    if (input == nullptr || output == nullptr || size < 128 || env->GetDirectBufferCapacity(outputBuffer) != 64) return -2;
    try {
        FlightSnapshot player;
        CrashoutSettings settings;
        if (!decode(input, static_cast<std::size_t>(size), player, settings)) return -2;
        switch (event) {
        case 0: module.reset(); break;
        case 1:
        case 5: {
            if (game == nullptr || (event == 1 && player.valid && read<int>(input, 96) != 37)) return -2;
            JniFlightGameAccess access(env, game);
            if (!access.healthy()) return -3;
            if (event == 1) module.tick(player, settings, access);
            else module.disable(player, access);
            if (!access.healthy()) return -3;
            break;
        }
        case 2: case 3: case 4: break;
        default: return -1;
        }
        std::memset(output, 0, 64);
        write<int>(output, 0, 1);
        write<int>(output, 4, module.allowMovementPacket(player, settings) ? 1 : 0);
        write<int>(output, 8, module.hidePose(player, settings) ? 1 : 0);
        write<int>(output, 12, module.spoofChestplate(player, settings) ? 1 : 0);
        write<int>(output, 16, event == 4 ? module.chestplateSlot(player) : -1);
        const auto movement = module.applyMovement(player);
        write<double>(output, 24, movement.x); write<double>(output, 32, movement.y); write<double>(output, 40, movement.z);
        write<int>(output, 48, static_cast<int>(module.failure()));
        return 0;
    } catch (const std::exception&) { return -4; }
}
} }
