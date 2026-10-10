#include "pch.h"
#include "NativeExampleModule.h"
#include "XingNativeBridge.h"
#include "ManagerBridge.h"
#include "CombatSchedulerBridge.h"
#include "WorldStateBridge.h"
#include <cstdint>
#include <jni.h>

namespace {
NativeExampleModule module;
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_xingclient_nativebridge_XingNativeBridge_nativeDispatch(
        JNIEnv* env, jclass, jint policy, jint event, jobject inputBuffer, jobject outputBuffer) {
    auto* input = static_cast<std::uint8_t*>(env->GetDirectBufferAddress(inputBuffer));
    auto* output = static_cast<std::uint8_t*>(env->GetDirectBufferAddress(outputBuffer));
    const jlong inputCapacity = env->GetDirectBufferCapacity(inputBuffer);
    const jlong outputCapacity = env->GetDirectBufferCapacity(outputBuffer);
    if (input == nullptr || output == nullptr || inputCapacity < 0 || outputCapacity < 0) return -1;
    const auto inputLen = static_cast<std::size_t>(inputCapacity);
    const auto outputLen = static_cast<std::size_t>(outputCapacity);
    switch (policy) {
    case 0: return xing::autocrystalpolicy::dispatch(event, input, inputLen, output, outputLen);
    case 1: return xing::autocrystalbreakpolicy::dispatch(event, input, inputLen, output, outputLen);
    case 2: return xing::autocrystalplacelifecycle::dispatch(event, input, inputLen, output, outputLen);
    case 3: return xing::autocrystalbreaklifecycle::dispatch(event, input, inputLen, output, outputLen);
    case 4: return xing::autocrystaldirectpreplacelifecycle::dispatch(event, input, inputLen, output, outputLen);
    case 5: return xing::autocrystalexecutionpolicy::dispatch(event, input, inputLen, output, outputLen);
    case 6: return xing::autocrystalcyclebridge::dispatch(event, input, inputLen, output, outputLen);
    case 7: return xing::managers::dispatch(event, input, inputLen, output, outputLen);
    case 8: return xing::combatbridge::dispatch(event, input, inputLen, output, outputLen);
    case 9: return xing::worldstatebridge::dispatch(event, input, inputLen, output, outputLen);
    default: return -1;
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_xing_NativeBridge_add(JNIEnv*, jclass, jint left, jint right) {
    return left + right;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_xingclient_module_NativeExampleModule_setNativeEnabled(JNIEnv*, jclass, jboolean enabled) {
    module.setEnabled(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_xingclient_module_NativeExampleModule_recordNativeTick(JNIEnv*, jclass) {
    module.onTick();
}

extern "C" JNIEXPORT jlong JNICALL
Java_dev_xingclient_module_NativeExampleModule_getNativeTickCount(JNIEnv*, jclass) {
    return static_cast<jlong>(module.tickCount());
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM*, void*) {
    return JNI_VERSION_1_8;
}

BOOL APIENTRY DllMain(HMODULE, DWORD, LPVOID) {
    return TRUE;
}
