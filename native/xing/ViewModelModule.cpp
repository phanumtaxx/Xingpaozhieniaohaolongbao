#include "pch.h"
#include "ViewModelModule.h"
#include <jni.h>
namespace xing { namespace visuals {
std::array<double, 9> ViewModelModule::transform(const std::array<double, 12>& settings, bool mainHand, bool rightArm, bool syncHands) {
    constexpr double Radians = 0.017453292519943295;
    const int offset = mainHand || syncHands ? 0 : 3;
    const double side = rightArm ? 1.0 : -1.0;
    return { settings[offset] * side, settings[offset + 1], settings[offset + 2],
        settings[6] * Radians, settings[7] * side * Radians, settings[8] * side * Radians,
        settings[9], settings[10], settings[11] };
}
} }
extern "C" JNIEXPORT void JNICALL Java_dev_xingclient_module_NativeViewModelModule_transform(
    JNIEnv* env, jclass, jdoubleArray input, jdoubleArray output, jboolean mainHand, jboolean rightArm, jboolean syncHands) {
    if (!input || !output || env->GetArrayLength(input) != 12 || env->GetArrayLength(output) != 9) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Invalid ViewModel transform arrays");
        return;
    }
    std::array<double, 12> settings;
    env->GetDoubleArrayRegion(input, 0, 12, settings.data());
    if (env->ExceptionCheck()) return;
    const auto result = xing::visuals::ViewModelModule::transform(settings, mainHand, rightArm, syncHands);
    env->SetDoubleArrayRegion(output, 0, 9, result.data());
}
extern "C" JNIEXPORT jboolean JNICALL Java_dev_xingclient_module_NativeViewModelModule_renders(
    JNIEnv*, jclass, jboolean enabled, jboolean configured) {
    return xing::visuals::ViewModelModule::renders(enabled, configured) ? JNI_TRUE : JNI_FALSE;
}
