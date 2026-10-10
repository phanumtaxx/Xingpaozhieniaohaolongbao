#pragma once
#include <jni.h>
namespace xing { namespace velocity {
int dispatchVelocity(JNIEnv* env, int event, jobject input, jobject output, jobject world);
} }
