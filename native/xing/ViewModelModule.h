#pragma once
#include <array>
namespace xing { namespace visuals {
// First-person transforms from Synthetic. Author: uint32.
class ViewModelModule {
public:
    static std::array<double, 9> transform(const std::array<double, 12>& settings, bool mainHand, bool rightArm, bool syncHands);
    static bool renders(bool enabled, bool configured) { return !enabled || configured; }
};
} }
