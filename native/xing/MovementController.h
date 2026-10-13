#pragma once
#include <cstdint>

namespace xing { namespace movement {

struct Vector { double x = 0.0, y = 0.0, z = 0.0; };
struct Rotation { float yaw = 0.0f, pitch = 0.0f; };

// Shared movement steering and correction recovery. Author: uint32.
class MovementController {
public:
    void reset();
    void corrected(std::int64_t nowMillis, int minimumPauseMillis);
    bool needsRecovery() const { return recoveryUntil_ != 0; }
    bool resume(std::int64_t nowMillis);
    int remainingRecoveryMillis(std::int64_t nowMillis) const;
    int correctionCount() const { return correctionCount_; }
    static Vector limitHorizontal(Vector movement, double maximumSpeed);
    static Rotation steer(Rotation current, Rotation target, float maximumTurnDegrees);
private:
    std::int64_t lastCorrectionAt_ = 0, recoveryUntil_ = 0;
    int correctionCount_ = 0;
};
} }
