#pragma once
namespace xing { namespace visuals {
// Synthetic's local swing policy. Author: uint32.
class SwingModule {
public:
    static int hand(bool enabled, bool overrideHand, int configured, int original);
    static int duration(bool enabled, bool overrideSpeed, int speed, int original);
    static float progress(bool enabled, bool oldAnimation, float original, float current);
};
} }
