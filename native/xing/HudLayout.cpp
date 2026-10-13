#include "pch.h"
#include "HudLayout.h"
#include <algorithm>
#include <cmath>
#include <vector>
#include <jni.h>

namespace xing { namespace visuals {

std::array<double, 4> HudLayout::place(int anchor, double x, double y, double scale, int width, int height) {
    const int column = anchor % 3;
    const int row = anchor / 3;
    const double originX = column * 0.5;
    const double originY = row * 0.5;
    const double screenX = width * originX + x * scale * (column == 2 ? -1.0 : 1.0);
    const double screenY = height * originY + y * scale * (row == 2 ? -1.0 : 1.0);

    return {
        (std::max)(0.0, (std::min)(screenX, static_cast<double>(width))),
        (std::max)(0.0, (std::min)(screenY, static_cast<double>(height))),
        originX,
        originY
    };
}

} }

extern "C" JNIEXPORT void JNICALL Java_dev_xingclient_render_hud_NativeHudLayout_place(
        JNIEnv* env, jclass, jintArray anchors, jdoubleArray offsets, jdoubleArray output,
        jint width, jint height, jdouble scale) {
    if (!anchors || !offsets || !output || width < 1 || height < 1 || !std::isfinite(scale) || scale <= 0) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Invalid HUD viewport");
        return;
    }

    const int count = env->GetArrayLength(anchors);
    if (env->GetArrayLength(offsets) != count * 2 || env->GetArrayLength(output) != count * 4) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Invalid HUD layout arrays");
        return;
    }
    if (count == 0) return;

    std::vector<jint> anchorValues(count);
    std::vector<double> positions(count * 2);
    std::vector<double> result(count * 4);
    env->GetIntArrayRegion(anchors, 0, count, anchorValues.data());
    env->GetDoubleArrayRegion(offsets, 0, count * 2, positions.data());
    if (env->ExceptionCheck()) return;

    for (int i = 0; i < count; ++i) {
        if (anchorValues[i] < 0 || anchorValues[i] > 8
                || !std::isfinite(positions[i * 2]) || !std::isfinite(positions[i * 2 + 1])) {
            env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Invalid HUD widget placement");
            return;
        }
        const auto layout = xing::visuals::HudLayout::place(
            anchorValues[i], positions[i * 2], positions[i * 2 + 1], scale, width, height);
        std::copy(layout.begin(), layout.end(), result.begin() + i * 4);
    }
    env->SetDoubleArrayRegion(output, 0, count * 4, result.data());
}

extern "C" JNIEXPORT jstring JNICALL Java_dev_xingclient_module_NativeWatermarkModule_nativeText(JNIEnv* env, jclass) {
    return env->NewStringUTF("Xing 0.1a");
}
