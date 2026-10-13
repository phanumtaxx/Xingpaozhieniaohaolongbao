#include "pch.h"
#include "PacketMineManager.h"
#include <algorithm>
#include <cmath>
#include <jni.h>
#include <new>

namespace xing { namespace managers {
void PacketMineManager::reset() { *this = PacketMineManager{}; }
float PacketMineManager::damage(const MiningFrame& frame, const MiningTool& tool) {
    if (frame.hardness < 0) return 0;
    if (frame.hardness == 0) return 1;
    float speed = tool.speed;
    if (speed > 1 && tool.efficiency > 0) speed += static_cast<float>(tool.efficiency * tool.efficiency + 1);
    if (frame.haste >= 0) speed *= 1.0f + (static_cast<float>(frame.haste) + 1.0f) * .2f;
    if (frame.fatigue >= 0)
        speed *= frame.fatigue == 0 ? .3f : frame.fatigue == 1 ? .09f : frame.fatigue == 2 ? .0027f : .00081f;
    speed *= frame.blockSpeed;
    if (frame.submerged) speed *= frame.submergedSpeed;
    if (!frame.onGround) speed /= 5.0f;
    return speed / frame.hardness / (!frame.requiresTool || tool.suitable ? 30.0f : 100.0f);
}
PacketMineManager::Result PacketMineManager::tick(const MiningFrame& frame, const std::array<MiningTool, 9>& tools) {
    auto result = [&](Command command) { return Result{command, stage_, slot_, progress_, retries_}; };
    if (frame.air) { stage_ = Stage::Finished; return result(Command::Finish); }
    if (!frame.valid) { stage_ = Stage::Failed; return result(Command::Fail); }
    if (stage_ == Stage::Finished) return result(Command::Finish);
    if (stage_ == Stage::Failed) return result(Command::Fail);
    if (slot_ < 0) {
        slot_ = frame.selectedSlot;
        float best = damage(frame, tools[slot_]);
        for (int slot = 0; slot < 9; ++slot) {
            float candidate = damage(frame, tools[slot]);
            if (candidate > best) { best = candidate; slot_ = slot; }
        }
        if (best <= 0) { stage_ = Stage::Failed; return result(Command::Fail); }
    }
    if (frame.paused) return result(Command::None);
    if (stage_ == Stage::Ready) return result(Command::Start);
    if (stage_ == Stage::Mining) {
        if (frame.heldSlot != slot_) return result(Command::None);
        progress_ = (std::min)(1.0f, progress_ + damage(frame, tools[slot_]) * frame.tickScale);
        if (progress_ >= frame.threshold) return result(Command::Stop);
    }
    if (stage_ == Stage::StartCommit || stage_ == Stage::StopCommit || stage_ == Stage::Confirming) {
        if (++age_ >= frame.confirmationTicks) {
            if (retries_ >= frame.maximumRetries) { stage_ = Stage::Failed; return result(Command::Fail); }
            stage_ = Stage::Retry;
            return result(Command::Abort);
        }
    }
    if (stage_ == Stage::Retry) return result(Command::Abort);
    return result(Command::None);
}
void PacketMineManager::sent(Command command, bool success) {
    if (!success) {
        if (command == Command::Start && stage_ == Stage::StartCommit) stage_ = Stage::Ready;
        if (command == Command::Stop && stage_ == Stage::StopCommit) stage_ = Stage::Mining;
        return;
    }
    if (command == Command::Start && stage_ == Stage::Ready) { stage_ = Stage::StartCommit; age_ = 0; }
    if (command == Command::Stop && stage_ == Stage::Mining) { stage_ = Stage::StopCommit; age_ = 0; }
    if (command == Command::Abort && stage_ == Stage::Retry) {
        ++retries_; stage_ = Stage::Ready; progress_ = 0; age_ = 0; slot_ = -1;
    }
}
void PacketMineManager::committed(Command command) {
    if (command == Command::Start && stage_ == Stage::StartCommit) { stage_ = Stage::Mining; age_ = 0; }
    if (command == Command::Stop && stage_ == Stage::StopCommit) { stage_ = Stage::Confirming; age_ = 0; }
}
} }
using Mining = xing::managers::PacketMineManager;
extern "C" JNIEXPORT jlong JNICALL Java_dev_xingclient_manager_PacketMineManager_create(JNIEnv* env, jclass) {
    auto manager = new (std::nothrow) Mining;
    if (!manager) env->ThrowNew(env->FindClass("java/lang/OutOfMemoryError"), "Packet mining manager");
    return reinterpret_cast<jlong>(manager);
}
extern "C" JNIEXPORT void JNICALL Java_dev_xingclient_manager_PacketMineManager_resetNative(JNIEnv*, jclass, jlong handle) {
    if (handle) reinterpret_cast<Mining*>(handle)->reset();
}
extern "C" JNIEXPORT jdoubleArray JNICALL Java_dev_xingclient_manager_PacketMineManager_tickNative(
        JNIEnv* env, jclass, jlong handle, jdoubleArray frame, jdoubleArray tools) {
    if (!handle || !frame || !tools || env->GetArrayLength(frame) != 17 || env->GetArrayLength(tools) != 27) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Invalid packet mining frame"); return nullptr;
    }
    std::array<double, 17> f;
    std::array<double, 27> t;
    env->GetDoubleArrayRegion(frame, 0, 17, f.data());
    env->GetDoubleArrayRegion(tools, 0, 27, t.data());
    if (env->ExceptionCheck()) return nullptr;
    bool valid = f[8] >= 0 && f[8] <= 8 && f[9] >= -1 && f[9] <= 8;
    for (double value : f) valid = valid && std::isfinite(value);
    for (int slot = 0; slot < 9; ++slot)
        valid = valid && std::isfinite(t[slot * 3]) && t[slot * 3] >= 0
            && std::isfinite(t[slot * 3 + 1]) && t[slot * 3 + 1] >= 0 && t[slot * 3 + 1] <= 255
            && std::isfinite(t[slot * 3 + 2]);
    if (!valid) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Invalid mining snapshot values"); return nullptr;
    }
    xing::managers::MiningFrame snapshot{};
    snapshot.hardness = static_cast<float>(f[0]); snapshot.requiresTool = f[1] != 0;
    snapshot.haste = static_cast<int>(f[2]); snapshot.fatigue = static_cast<int>(f[3]);
    snapshot.blockSpeed = static_cast<float>(f[4]); snapshot.submergedSpeed = static_cast<float>(f[5]);
    snapshot.onGround = f[6] != 0; snapshot.submerged = f[7] != 0;
    snapshot.selectedSlot = static_cast<int>(f[8]); snapshot.heldSlot = static_cast<int>(f[9]);
    snapshot.paused = f[10] != 0; snapshot.tickScale = static_cast<float>(f[11]);
    snapshot.threshold = static_cast<float>(f[12]); snapshot.confirmationTicks = static_cast<int>(f[13]);
    snapshot.maximumRetries = static_cast<int>(f[14]); snapshot.valid = f[15] != 0; snapshot.air = f[16] != 0;
    std::array<xing::managers::MiningTool, 9> inventory;
    for (int slot = 0; slot < 9; ++slot) inventory[slot] = {
        static_cast<float>(t[slot * 3]), static_cast<int>(t[slot * 3 + 1]), t[slot * 3 + 2] != 0};
    auto result = reinterpret_cast<Mining*>(handle)->tick(snapshot, inventory);
    double values[] = {static_cast<double>(result.command), static_cast<double>(result.stage),
        static_cast<double>(result.slot), result.progress, static_cast<double>(result.retries)};
    auto output = env->NewDoubleArray(5);
    if (output) env->SetDoubleArrayRegion(output, 0, 5, values);
    return output;
}
extern "C" JNIEXPORT void JNICALL Java_dev_xingclient_manager_PacketMineManager_sentNative(
        JNIEnv*, jclass, jlong handle, jint command, jboolean success) {
    if (handle && command >= 1 && command <= 3) reinterpret_cast<Mining*>(handle)->sent(static_cast<Mining::Command>(command), success != 0);
}
extern "C" JNIEXPORT void JNICALL Java_dev_xingclient_manager_PacketMineManager_committedNative(
        JNIEnv*, jclass, jlong handle, jint command) {
    if (handle && command >= 1 && command <= 2) reinterpret_cast<Mining*>(handle)->committed(static_cast<Mining::Command>(command));
}
