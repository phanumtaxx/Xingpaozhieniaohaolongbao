#pragma once
#include <array>

namespace xing { namespace visuals {

// Screen anchoring shared by HTML HUD widgets. Author: uint32.
class HudLayout {
public:
    static std::array<double, 4> place(int anchor, double x, double y, double scale, int width, int height);
};

} }
