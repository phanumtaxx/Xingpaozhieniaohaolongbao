#include "pch.h"
#include "FlightBridge.h"
#include "FlightModule.h"
#include <cmath>
#include <cstring>
#include <exception>

namespace xing { namespace flight {
namespace {
FlightModule module;
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
        if (!env_->ExceptionCheck()) inventoryReady_ = env_->GetMethodID(type, "inventoryReady", "()Z");
        if (!env_->ExceptionCheck()) startEquipped_ = env_->GetMethodID(type, "startGlidingWithElytra", "(I)Z");
        if (!env_->ExceptionCheck()) stage_ = env_->GetMethodID(type, "stageElytra", "(II)Z");
        if (!env_->ExceptionCheck()) stagedMatches_ = env_->GetMethodID(type, "stagedElytraMatches", "()Z");
        if (!env_->ExceptionCheck()) restoreEquipment_ = env_->GetMethodID(type, "restoreFlightEquipment", "()Z");
        if (!env_->ExceptionCheck()) start_ = env_->GetMethodID(type, "startGliding", "()Z");
        if (!env_->ExceptionCheck()) jump_ = env_->GetMethodID(type, "jump", "()V");
        if (!env_->ExceptionCheck()) rocket_ = env_->GetMethodID(type, "useRocket", "(IIZ)Z");
        if (!env_->ExceptionCheck()) rotate_ = env_->GetMethodID(type, "requestRotation", "(FF)V");
        if (!env_->ExceptionCheck()) clientRotate_ = env_->GetMethodID(type, "requestClientRotation", "(FF)Z");
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
    bool inventoryReady() override {
        return healthy() && env_->CallBooleanMethod(game_, inventoryReady_) == JNI_TRUE && healthy();
    }
    bool startGlidingWithElytra(int hotbar) override {
        return healthy() && env_->CallBooleanMethod(game_, startEquipped_, hotbar) == JNI_TRUE && healthy();
    }
    bool stageElytra(int source, int hotbar) override {
        return healthy() && env_->CallBooleanMethod(game_, stage_, source, hotbar) == JNI_TRUE && healthy();
    }
    bool stagedElytraMatches() override {
        return healthy() && env_->CallBooleanMethod(game_, stagedMatches_) == JNI_TRUE && healthy();
    }
    bool restoreFlightEquipment() override {
        return healthy() && env_->CallBooleanMethod(game_, restoreEquipment_) == JNI_TRUE && healthy();
    }
    bool startGliding() override {
        return healthy() && env_->CallBooleanMethod(game_, start_) == JNI_TRUE && healthy();
    }
    void jump() override { if (healthy()) env_->CallVoidMethod(game_, jump_); }
    bool useRocket(int slot, int menuSlot, bool hotbar) override {
        return healthy() && env_->CallBooleanMethod(game_, rocket_, slot, menuSlot, hotbar ? JNI_TRUE : JNI_FALSE) == JNI_TRUE && healthy();
    }
    void requestRotation(float yaw, float pitch) override { if (healthy()) env_->CallVoidMethod(game_, rotate_, yaw, pitch); }
    bool requestClientRotation(float yaw, float pitch) override {
        return healthy() && env_->CallBooleanMethod(game_, clientRotate_, yaw, pitch) == JNI_TRUE && healthy();
    }
    float currentYaw() override { return healthy() ? env_->CallFloatMethod(game_, yaw_) : 0.0f; }
    float currentPitch() override { return healthy() ? env_->CallFloatMethod(game_, pitch_) : 0.0f; }
    void clearRotation() override { if (healthy()) env_->CallVoidMethod(game_, clear_); }
    void setVelocity(double x, double y, double z) override { if (healthy()) env_->CallVoidMethod(game_, velocity_, x, y, z); }
private:
    JNIEnv* env_;
    jobject game_;
    jmethodID swap_ = nullptr, start_ = nullptr, jump_ = nullptr, rocket_ = nullptr, rotate_ = nullptr, clear_ = nullptr, velocity_ = nullptr;
    jmethodID inventoryReady_ = nullptr;
    jmethodID startEquipped_ = nullptr, stage_ = nullptr, stagedMatches_ = nullptr, restoreEquipment_ = nullptr;
    jmethodID clientRotate_ = nullptr;
    jmethodID yaw_ = nullptr, pitch_ = nullptr;
};

bool decode(const std::uint8_t* input, std::size_t size, FlightSnapshot& player, FlightSettings& settings) {
    if (size < 256 || read<int>(input, 0) != 7) return false;
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
    const int mode = read<int>(input, 100), options = read<int>(input, 108);
    if (mode < 0 || mode > 2) return false;
    settings.mode = static_cast<FlightMode>(mode);
    settings.antiKick = (options & 1) != 0; settings.pitch = (options & 2) != 0;
    settings.firework = (options & 4) != 0; settings.idleFlipFlop = (options & 8) != 0;
    settings.flipFlopTicks = read<int>(input, 112);
    settings.horizontal = read<double>(input, 120); settings.vertical = read<double>(input, 128);
    player.inputStrafe = read<float>(input, 136); player.inputForward = read<float>(input, 140);
    player.age = read<int>(input, 144);
    player.selectedSlot = read<int>(input, 172);
    settings.crashoutHorizontal = read<double>(input, 176);
    player.velocity = { read<double>(input, 184), read<double>(input, 192), read<double>(input, 200) };
    if (!std::isfinite(settings.crashoutHorizontal) || settings.crashoutHorizontal < 0.08 || settings.crashoutHorizontal > 2.0
        || !std::isfinite(player.velocity.x) || !std::isfinite(player.velocity.y) || !std::isfinite(player.velocity.z)) return false;
    if (player.selectedSlot < 0 || player.selectedSlot >= 9) return false;
    if (settings.flipFlopTicks < 1 || settings.flipFlopTicks > 20 || !std::isfinite(settings.horizontal)
        || settings.horizontal < 1 || settings.horizontal > 5 || !std::isfinite(settings.vertical)
        || settings.vertical < 1 || settings.vertical > 5 || !std::isfinite(player.inputStrafe)
        || !std::isfinite(player.inputForward)) return false;
    std::size_t offset = 256;
    for (int index = 0; index < count; ++index) {
        if (offset > size || size - offset < 20) return false;
        const int itemFlags = read<int>(input, offset);
        ItemSnapshot& item = index == 36 ? player.chest : player.inventory[index];
        item.empty = (itemFlags & 1) != 0; item.elytra = (itemFlags & 2) != 0;
        item.rocket = (itemFlags & 4) != 0; item.damageable = (itemFlags & 8) != 0;
        item.chestArmor = (itemFlags & 16) != 0;
        item.glider = (itemFlags & 32) != 0;
        item.damage = read<int>(input, offset + 4); item.maximumDamage = read<int>(input, offset + 8);
        item.rocketDuration = read<int>(input, offset + 12);
        const int pathSize = read<int>(input, offset + 16);
        offset += 20;
        if (pathSize < 0 || static_cast<std::size_t>(pathSize) > size - offset) return false;
        item.path.assign(reinterpret_cast<const char*>(input + offset), pathSize);
        offset += pathSize;
    }
    const int rockets = read<int>(input, 156);
    if (rockets < 0 || static_cast<std::size_t>(rockets) > (size - offset) / 8) return false;
    for (int i = 0; i < rockets; ++i) {
        FlightSnapshot::Rocket rocket;
        rocket.life = read<int>(input, offset); rocket.duration = read<int>(input, offset + 4);
        if (rocket.life < 0 || rocket.duration < 0 || rocket.duration > 255) return false;
        player.attachedRockets.push_back(rocket);
        offset += 8;
    }
    return true;
}
}

int dispatchFlight(JNIEnv* env, int event, jobject inputBuffer, jobject outputBuffer, jobject game) {
    auto* input = static_cast<std::uint8_t*>(env->GetDirectBufferAddress(inputBuffer));
    auto* output = static_cast<std::uint8_t*>(env->GetDirectBufferAddress(outputBuffer));
    const auto size = env->GetDirectBufferCapacity(inputBuffer);
    if (input == nullptr || output == nullptr || size < 256 || env->GetDirectBufferCapacity(outputBuffer) != 56) return -2;
    try {
        FlightSnapshot player;
        FlightSettings settings;
        if (!decode(input, static_cast<std::size_t>(size), player, settings)) return -2;
        switch (event) {
        case 0: module.reset(settings.mode); break;
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
        case 6: module.requestExternalMovement(read<std::uint64_t>(input, 160), player.age, player.movement); break;
        case 7: module.clearExternalMovement(read<std::uint64_t>(input, 160)); break;
        case 8: module.reset(settings.mode, false); break;
        case 9: module.onServerCorrection(); break;
        default: return -1;
        }
        std::memset(output, 0, 56);
        write<int>(output, 0, 7);
        write<int>(output, 4, module.allowMovementPacket(player, settings) ? 1 : 0);
        write<int>(output, 8, module.hidePose(player, settings) ? 1 : 0);
        write<int>(output, 12, module.spoofChestplate(player, settings) ? 1 : 0);
        write<int>(output, 16, event == 4 ? module.chestplateSlot(player) : -1);
        const auto movement = event == 2 ? module.applyMovement(player, settings, read<int>(input, 168) != 0) : player.movement;
        write<double>(output, 24, movement.x); write<double>(output, 32, movement.y); write<double>(output, 40, movement.z);
        write<int>(output, 48, module.status(player, settings));
        return 0;
    } catch (const std::exception&) { return -4; }
}
} }
