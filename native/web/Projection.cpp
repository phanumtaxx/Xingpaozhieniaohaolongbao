#include <array>
#include <cmath>
#include <vector>
#include <jni.h>

// Camera-relative projection preserves precision far from the world origin. Author: uint32.
extern "C" JNIEXPORT void JNICALL Java_dev_xingclient_render_web_NativeWebEngine_project(
        JNIEnv* env, jclass, jdoubleArray matrixArray, jdoubleArray cameraArray, jdoubleArray pointsArray,
        jdoubleArray outputArray, jint width, jint height) {
    if (!matrixArray || !cameraArray || !pointsArray || !outputArray || width <= 0 || height <= 0
        || env->GetArrayLength(matrixArray) != 16 || env->GetArrayLength(cameraArray) != 3
        || env->GetArrayLength(pointsArray) % 3 != 0
        || env->GetArrayLength(outputArray) != env->GetArrayLength(pointsArray) / 3 * 4) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Invalid projection frame or point arrays");
        return;
    }
    std::array<double,16> matrix; std::array<double,3> camera;
    std::vector<double> points(env->GetArrayLength(pointsArray)), output(env->GetArrayLength(outputArray),0.0);
    env->GetDoubleArrayRegion(matrixArray,0,16,matrix.data());
    env->GetDoubleArrayRegion(cameraArray,0,3,camera.data());
    env->GetDoubleArrayRegion(pointsArray,0,static_cast<jsize>(points.size()),points.data());
    if(env->ExceptionCheck()) return;
    for(std::size_t point=0;point<points.size()/3;point++) {
        std::array<double,4> relative={points[point*3]-camera[0],points[point*3+1]-camera[1],points[point*3+2]-camera[2],1.0};
        std::array<double,4> clip={};
        for(int row=0;row<4;row++) for(int column=0;column<4;column++) clip[row]+=matrix[column*4+row]*relative[column];
        if(!std::isfinite(clip[3]) || clip[3]<=1.e-7) continue;
        double x=clip[0]/clip[3],y=clip[1]/clip[3],z=clip[2]/clip[3];
        if(!std::isfinite(x)||!std::isfinite(y)||!std::isfinite(z)) continue;
        output[point*4]=(x+1.0)*0.5*width;
        output[point*4+1]=(1.0-y)*0.5*height;
        output[point*4+2]=(z+1.0)*0.5;
        output[point*4+3]=x>=-1.0&&x<=1.0&&y>=-1.0&&y<=1.0&&z>=-1.0&&z<=1.0?1.0:0.0;
    }
    env->SetDoubleArrayRegion(outputArray,0,static_cast<jsize>(output.size()),output.data());
}

namespace {
using ClipPoint = std::array<double, 4>;
double planeDistance(const ClipPoint& p, int plane) {
    switch (plane) {
        case 0: return p[3] - 1.e-7;
        case 1: return p[3] + p[0];
        case 2: return p[3] - p[0];
        case 3: return p[3] + p[1];
        case 4: return p[3] - p[1];
        case 5: return p[3] + p[2];
        default: return p[3] - p[2];
    }
}
std::vector<ClipPoint> clipPolygon(std::vector<ClipPoint> polygon) {
    for (int plane = 0; plane < 7 && !polygon.empty(); ++plane) {
        std::vector<ClipPoint> clipped;
        auto previous = polygon.back();
        double previousDistance = planeDistance(previous, plane);
        for (const auto& point : polygon) {
            double distance = planeDistance(point, plane);
            if ((distance >= 0) != (previousDistance >= 0)) {
                double t = previousDistance / (previousDistance - distance);
                ClipPoint intersection;
                for (int axis = 0; axis < 4; ++axis) intersection[axis] = previous[axis] + t * (point[axis] - previous[axis]);
                clipped.push_back(intersection);
            }
            if (distance >= 0) clipped.push_back(point);
            previous = point;
            previousDistance = distance;
        }
        polygon = std::move(clipped);
    }
    return polygon;
}
}

extern "C" JNIEXPORT void JNICALL Java_dev_xingclient_render_web_NativeWebEngine_projectPolygons(
        JNIEnv* env, jclass, jdoubleArray matrixArray, jdoubleArray cameraArray, jdoubleArray pointsArray,
        jintArray sizesArray, jdoubleArray outputArray, jint width, jint height, jboolean twoSided) {
    constexpr int stride = 49;
    if (!matrixArray || !cameraArray || !pointsArray || !sizesArray || !outputArray || width < 1 || height < 1
            || env->GetArrayLength(matrixArray) != 16 || env->GetArrayLength(cameraArray) != 3
            || env->GetArrayLength(sizesArray) > 4096
            || env->GetArrayLength(outputArray) != env->GetArrayLength(sizesArray) * stride) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Invalid polygon projection arrays");
        return;
    }
    if (env->GetArrayLength(sizesArray) == 0) return;
    std::array<double, 16> matrix;
    std::array<double, 3> camera;
    std::vector<jint> sizes(env->GetArrayLength(sizesArray));
    std::vector<double> points(env->GetArrayLength(pointsArray));
    std::vector<double> output(env->GetArrayLength(outputArray), 0.0);
    env->GetDoubleArrayRegion(matrixArray, 0, 16, matrix.data());
    env->GetDoubleArrayRegion(cameraArray, 0, 3, camera.data());
    env->GetIntArrayRegion(sizesArray, 0, static_cast<jsize>(sizes.size()), sizes.data());
    env->GetDoubleArrayRegion(pointsArray, 0, static_cast<jsize>(points.size()), points.data());
    if (env->ExceptionCheck()) return;
    std::size_t required = 0;
    for (int size : sizes) {
        if (size < 3 || size > 8) {
            env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Invalid polygon size");
            return;
        }
        required += size * 3;
    }
    if (required != points.size()) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Polygon point count mismatch");
        return;
    }
    std::size_t at = 0;
    for (std::size_t face = 0; face < sizes.size(); ++face) {
        std::vector<ClipPoint> polygon;
        bool valid = true;
        for (int vertex = 0; vertex < sizes[face]; ++vertex) {
            ClipPoint relative = {points[at] - camera[0], points[at + 1] - camera[1], points[at + 2] - camera[2], 1.0};
            at += 3;
            ClipPoint clip = {};
            for (int row = 0; row < 4; ++row) for (int column = 0; column < 4; ++column) clip[row] += matrix[column * 4 + row] * relative[column];
            for (double value : clip) if (!std::isfinite(value)) valid = false;
            polygon.push_back(clip);
        }
        if (!valid) continue;
        polygon = clipPolygon(std::move(polygon));
        if (polygon.size() < 3 || polygon.size() > 16) continue;
        double area = 0;
        for (std::size_t i = 0; i < polygon.size(); ++i) {
            const auto& a = polygon[i];
            const auto& b = polygon[(i + 1) % polygon.size()];
            area += (a[0] * b[1] - b[0] * a[1]) / (a[3] * b[3]);
        }
        if (std::abs(area) < 1.e-12 || (!twoSided && area <= 0)) continue;
        output[face * stride] = static_cast<double>(polygon.size());
        for (std::size_t i = 0; i < polygon.size(); ++i) {
            const auto& point = polygon[i];
            std::size_t target = face * stride + 1 + i * 3;
            output[target] = (point[0] / point[3] + 1.0) * 0.5 * width;
            output[target + 1] = (1.0 - point[1] / point[3]) * 0.5 * height;
            output[target + 2] = (point[2] / point[3] + 1.0) * 0.5;
        }
    }
    env->SetDoubleArrayRegion(outputArray, 0, static_cast<jsize>(output.size()), output.data());
}
