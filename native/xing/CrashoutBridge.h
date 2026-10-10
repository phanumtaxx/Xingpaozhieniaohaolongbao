#pragma once
#include <jni.h>
namespace xing { namespace flight {
int dispatchCrashout(JNIEnv* env, int event, jobject input, jobject output, jobject game);
} }
