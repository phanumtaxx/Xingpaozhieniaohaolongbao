#include "pch.h"
#include "NoRenderModule.h"

namespace xing { namespace norender {
bool hides(bool enabled, unsigned settings, int effect) {
    return enabled && effect >= 0 && effect < 10 && (settings & (1u << effect)) != 0;
}
}
}
