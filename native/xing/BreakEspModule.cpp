#include "pch.h"
#include <jni.h>
#include <algorithm>
#include <array>
#include <cmath>
#include <map>
#include <vector>
#include <set>

namespace xing { namespace visuals {

// Animated block silhouettes for HTML rendering. Author: uint32.
class BreakEspModule {
    struct Block {
        double progress = 0, target = 0, lastSeen = 0, started = 0;
        int kind = 0;
    };
    using Position = std::array<int, 3>;
    std::map<Position, Block> blocks_;
    double lastFrame_ = 0;

    static double clamp(double value, double low, double high) {
        return (std::max)(low, (std::min)(high, value));
    }
    static double ease(double progress, int mode) {
        switch (mode) {
            case 1: return progress * progress * (3.0 - 2.0 * progress);
            case 2: return 1.0 - std::pow(1.0 - progress, 3.0);
            case 3: return 0.5 - 0.5 * std::cos(progress * 3.141592653589793);
            case 4: return 1.0 - std::pow(1.0 - progress, 5.0);
            default: return progress;
        }
    }
    static int color(double progress, int start, int end, bool gradient) {
        if (!gradient) return start;
        int result = 0;
        for (int shift : {0, 8, 16}) {
            double a = (start >> shift) & 255, b = (end >> shift) & 255;
            result |= static_cast<int>(std::round(a + (b - a) * progress)) << shift;
        }
        return result;
    }
public:
    void reset() { blocks_.clear(); lastFrame_ = 0; }

    std::vector<double> update(const std::vector<double>& observations, const std::vector<double>& settings,
                               const std::array<double, 3>& camera, double now) {
        const int animation = static_cast<int>(settings[0]), easing = static_cast<int>(settings[1]);
        const double range = settings[2], smoothing = settings[3] / 1000.0, fade = settings[4] / 1000.0;
        const double minimum = settings[5], inset = settings[6], rotation = settings[7];
        const double pulse = settings[8], frequency = settings[9];
        const int startColor = static_cast<int>(settings[10]), endColor = static_cast<int>(settings[11]);
        const bool gradient = settings[12] != 0, self = settings[13] != 0, others = settings[14] != 0;
        const int maximum = static_cast<int>(settings[15]);
        double delta = lastFrame_ == 0 ? 0 : clamp(now - lastFrame_, 0, 0.1);
        lastFrame_ = now;
        auto distance = [&camera](const Position& position) {
            double result = 0;
            for (int axis = 0; axis < 3; ++axis) result += std::pow(position[axis] + 0.5 - camera[axis], 2);
            return result;
        };
        struct Observation { Position position; double progress; int kind; };
        std::vector<Observation> observed;
        for (std::size_t at = 0; at < observations.size(); at += 5) {
            Position position = {static_cast<int>(observations[at]), static_cast<int>(observations[at + 1]), static_cast<int>(observations[at + 2])};
            int kind = static_cast<int>(observations[at + 4]);
            if ((kind == 0 && !self) || (kind != 0 && !others) || distance(position) > range * range) continue;
            observed.push_back({position, clamp(observations[at + 3], 0, 1), kind});
        }
        std::sort(observed.begin(), observed.end(), [&distance](const Observation& a, const Observation& b) {
            return distance(a.position) < distance(b.position);
        });
        if (observed.size() > static_cast<std::size_t>(maximum)) observed.resize(maximum);
        for (const auto& observation : observed) {
            auto found = blocks_.find(observation.position);
            if (found == blocks_.end()) {
                Block block;
                block.started = now;
                found = blocks_.emplace(observation.position, block).first;
            }
            auto& block = found->second;
            if ((block.lastSeen > 0 && now - block.lastSeen > (std::max)(fade, 0.5)) || observation.progress + 0.15 < block.target) {
                block.progress = 0;
                block.started = now;
            }
            block.target = observation.progress;
            block.lastSeen = now;
            block.kind = observation.kind;
        }
        std::vector<Position> renderOrder;
        for (const auto& entry : blocks_) {
            bool visibleKind = entry.second.kind == 0 ? self : others;
            double missing = now - entry.second.lastSeen;
            if (visibleKind && distance(entry.first) <= range * range
                    && (missing <= 0 || (fade > 0 && missing < fade))) renderOrder.push_back(entry.first);
        }
        std::sort(renderOrder.begin(), renderOrder.end(), [&distance](const Position& a, const Position& b) {
            return distance(a) < distance(b);
        });
        if (renderOrder.size() > static_cast<std::size_t>(maximum)) renderOrder.resize(maximum);
        std::set<Position> renderPositions(renderOrder.begin(), renderOrder.end());
        std::vector<double> output;
        const int faces[6][4] = {{0,4,6,2},{1,3,7,5},{0,1,5,4},{2,6,7,3},{0,2,3,1},{4,5,7,6}};
        for (auto it = blocks_.begin(); it != blocks_.end();) {
            auto& block = it->second;
            double missing = now - block.lastSeen;
            bool visibleKind = block.kind == 0 ? self : others;
            if (!visibleKind || distance(it->first) > range * range || (missing > 0 && (fade <= 0 || missing >= fade))) {
                it = blocks_.erase(it);
                continue;
            }
            if (missing > 0 && block.target >= 0.85) block.target = 1;
            double smoothingFactor = smoothing <= 0 ? 1 : 1.0 - std::exp(-delta / smoothing);
            block.progress += (block.target - block.progress) * smoothingFactor;
            if (renderPositions.find(it->first) == renderPositions.end()) { ++it; continue; }
            double progress = clamp(block.progress, 0, 1);
            double growth = minimum + (1.0 - minimum) * ease(progress, easing);
            double age = now - block.started;
            double oscillation = std::sin(age * frequency * 6.283185307179586);
            if (animation == 2) growth *= 1.0 - pulse * (0.5 + 0.5 * oscillation) * (1.0 - progress);
            if (animation == 4) growth += std::sin(progress * 3.141592653589793 * 3.0) * (1.0 - progress) * pulse;
            if (animation == 5) growth = 1.0 - (1.0 - minimum) * ease(progress, easing);
            growth = clamp(growth, 0, 1);
            double half = (0.5 - inset) * growth;
            double angle = animation == 3 ? (1.0 - progress) * rotation * age * 3.141592653589793 / 180.0 : 0;
            std::array<double, 3> center = {it->first[0] + 0.5, it->first[1] + 0.5, it->first[2] + 0.5};
            output.insert(output.end(), center.begin(), center.end());
            output.push_back(progress);
            output.push_back(missing <= 0 ? 1.0 : 1.0 - ease(clamp(missing / fade, 0, 1), 1));
            output.push_back(block.kind);
            output.push_back(color(progress, startColor, endColor, gradient));
            std::array<std::array<double, 3>, 8> vertices;
            for (int vertex = 0; vertex < 8; ++vertex) {
                double x = (vertex & 1 ? half : -half), y = (vertex & 2 ? half : -half), z = (vertex & 4 ? half : -half);
                if (animation == 1) y += -(0.5 - inset) + half;
                vertices[vertex] = {center[0] + x * std::cos(angle) - z * std::sin(angle),
                    center[1] + y, center[2] + x * std::sin(angle) + z * std::cos(angle)};
            }
            for (const auto& face : faces) for (int vertex : face) output.insert(output.end(), vertices[vertex].begin(), vertices[vertex].end());
            ++it;
        }
        return output;
    }
};
} }

extern "C" JNIEXPORT jlong JNICALL Java_dev_xingclient_module_NativeBreakEspModule_create(JNIEnv*, jclass) {
    return reinterpret_cast<jlong>(new xing::visuals::BreakEspModule());
}
extern "C" JNIEXPORT void JNICALL Java_dev_xingclient_module_NativeBreakEspModule_destroy(JNIEnv*, jclass, jlong handle) {
    delete reinterpret_cast<xing::visuals::BreakEspModule*>(handle);
}
extern "C" JNIEXPORT void JNICALL Java_dev_xingclient_module_NativeBreakEspModule_reset(JNIEnv*, jclass, jlong handle) {
    if (handle) reinterpret_cast<xing::visuals::BreakEspModule*>(handle)->reset();
}
extern "C" JNIEXPORT jdoubleArray JNICALL Java_dev_xingclient_module_NativeBreakEspModule_update(
        JNIEnv* env, jclass, jlong handle, jdoubleArray blocksArray, jdoubleArray settingsArray,
        jdoubleArray cameraArray, jdouble now) {
    if (!handle || !blocksArray || !settingsArray || !cameraArray || env->GetArrayLength(blocksArray) % 5 != 0
            || env->GetArrayLength(settingsArray) != 16 || env->GetArrayLength(cameraArray) != 3 || !std::isfinite(now)) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Invalid Break ESP frame");
        return nullptr;
    }
    std::vector<double> blocks(env->GetArrayLength(blocksArray)), settings(16);
    std::array<double, 3> camera;
    env->GetDoubleArrayRegion(blocksArray, 0, static_cast<jsize>(blocks.size()), blocks.data());
    env->GetDoubleArrayRegion(settingsArray, 0, 16, settings.data());
    env->GetDoubleArrayRegion(cameraArray, 0, 3, camera.data());
    if (env->ExceptionCheck()) return nullptr;
    for (double value : blocks) if (!std::isfinite(value) || std::abs(value) > 30000000) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Invalid Break ESP block"); return nullptr;
    }
    for (double value : settings) if (!std::isfinite(value)) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Invalid Break ESP setting"); return nullptr;
    }
    for (double value : camera) if (!std::isfinite(value)) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Invalid Break ESP camera"); return nullptr;
    }
    settings[15] = (std::max)(1.0, (std::min)(64.0, settings[15]));
    auto output = reinterpret_cast<xing::visuals::BreakEspModule*>(handle)->update(blocks, settings, camera, now);
    auto result = env->NewDoubleArray(static_cast<jsize>(output.size()));
    if (result) env->SetDoubleArrayRegion(result, 0, static_cast<jsize>(output.size()), output.data());
    return result;
}
