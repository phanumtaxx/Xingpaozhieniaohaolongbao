#include "pch.h"
#include "SwingModule.h"
#include <algorithm>
#include <jni.h>
namespace xing { namespace visuals {
int SwingModule::hand(bool enabled, bool overrideHand, int configured, int original) {
    return enabled && overrideHand ? configured : original;
}
int SwingModule::duration(bool enabled, bool overrideSpeed, int speed, int original) {
    return enabled && overrideSpeed ? (std::max)(2, 20 - speed) : original;
}
float SwingModule::progress(bool enabled, bool oldAnimation, float original, float current) {
    return enabled && oldAnimation ? current : original;
}
} }
extern "C" JNIEXPORT jint JNICALL Java_dev_xingclient_module_NativeSwingModule_nativeHand(
    JNIEnv*, jclass, jboolean enabled, jboolean overrideHand, jint configured, jint original) {
    return xing::visuals::SwingModule::hand(enabled, overrideHand, configured, original);
}
extern "C" JNIEXPORT jint JNICALL Java_dev_xingclient_module_NativeSwingModule_nativeDuration(
    JNIEnv*, jclass, jboolean enabled, jboolean overrideSpeed, jint speed, jint original) {
    return xing::visuals::SwingModule::duration(enabled, overrideSpeed, speed, original);
}
extern "C" JNIEXPORT jfloat JNICALL Java_dev_xingclient_module_NativeSwingModule_nativeProgress(
    JNIEnv*, jclass, jboolean enabled, jboolean oldAnimation, jfloat original, jfloat current) {
    return xing::visuals::SwingModule::progress(enabled, oldAnimation, original, current);
}
