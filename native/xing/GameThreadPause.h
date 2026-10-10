#pragma once

#include <cstdint>

namespace xing {

struct GameThreadPauseResult {
    int elapsedMillis = 0;
    bool cancelled = false;
    bool valid = false;
};

// Timed system-menu loop on the window-owning thread. Author: uint32.
GameThreadPauseResult pauseGameThread(std::uintptr_t windowHandle, int durationMillis);

}
