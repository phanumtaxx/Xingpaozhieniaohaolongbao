#pragma once
#include <array>
namespace xing { namespace visuals {
// Native player selection and render interpolation. Author: uint32.
class NametagsModule {
public:
    static std::array<double,4> anchor(const double* player,int flags,double range,double offset,double delta,bool invisible);
};
} }
