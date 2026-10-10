#include "pch.h"
#include "GameThreadPause.h"

namespace xing {

namespace {
struct MenuPause {
    HWND window;
    ULONGLONG started;
    int durationMillis;
    UINT_PTR timerId = 0;
    bool completed = false;
};

thread_local MenuPause* activePause = nullptr;

void CALLBACK checkMenuPause(HWND, UINT, UINT_PTR timerId, DWORD) {
    auto* pause = activePause;
    if (pause == nullptr || pause->timerId != timerId) return;
    if (GetForegroundWindow() != pause->window || (GetAsyncKeyState(VK_ESCAPE) & 0x8000) != 0) {
        EndMenu();
    } else if (GetTickCount64() - pause->started >= static_cast<ULONGLONG>(pause->durationMillis)) {
        pause->completed = true;
        EndMenu();
    }
}
}

GameThreadPauseResult pauseGameThread(std::uintptr_t windowHandle, int durationMillis) {
    const auto window = reinterpret_cast<HWND>(windowHandle);
    if (durationMillis < 100 || durationMillis > 4000 || !IsWindow(window)) return {};

    DWORD processId = 0;
    const DWORD threadId = GetWindowThreadProcessId(window, &processId);
    if (processId != GetCurrentProcessId() || threadId != GetCurrentThreadId()) return {};
    if (activePause != nullptr || GetForegroundWindow() != window) return {};

    const auto style = GetWindowLongPtrW(window, GWL_STYLE);
    HMENU menu = GetSystemMenu(window, FALSE);
    RECT bounds{};
    if ((style & WS_CAPTION) != WS_CAPTION || menu == nullptr || !GetWindowRect(window, &bounds)) return {};

    MenuPause pause{ window, GetTickCount64(), durationMillis };
    pause.timerId = SetTimer(window, reinterpret_cast<UINT_PTR>(&pause), 10, checkMenuPause);
    if (pause.timerId == 0) return {};
    activePause = &pause;

    // Return menu commands rather than executing Close, Move or Resize.
    TrackPopupMenuEx(menu, TPM_RETURNCMD | TPM_NONOTIFY | TPM_RIGHTBUTTON | TPM_NOANIMATION,
            bounds.left + 16, bounds.top + GetSystemMetrics(SM_CYCAPTION), window, nullptr);

    activePause = nullptr;
    KillTimer(window, pause.timerId);
    return { static_cast<int>(GetTickCount64() - pause.started), !pause.completed, true };
}

}
