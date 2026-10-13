#include "pch.h"
#include <chrono>
#include <deque>
#include <jni.h>

// Author: uint32. Shared by Flight and Auto Refill inventory transactions.
namespace {
using Clock = std::chrono::steady_clock;
struct Reservation { Clock::time_point time; int clicks; };
std::deque<Reservation> reservations;
int reservedClicks = 0;
int heldClicks = 0;
constexpr int ClickLimit = 60;
constexpr auto Window = std::chrono::milliseconds(4000);
void expire(Clock::time_point now) {
    while (!reservations.empty() && now - reservations.front().time >= Window) {
        reservedClicks -= reservations.front().clicks;
        reservations.pop_front();
    }
}
bool reserve(int clicks, bool hold) {
    const auto now = Clock::now();
    expire(now);
    if (clicks <= 0 || clicks > ClickLimit || reservedClicks + heldClicks + clicks > ClickLimit) return false;
    if (hold) heldClicks += clicks;
    else { reservations.push_back({now, clicks}); reservedClicks += clicks; }
    return true;
}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_xingclient_manager_InventoryClickBudget_nativeReserve(JNIEnv*, jclass, jint clicks) {
    return reserve(clicks, false) ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_dev_xingclient_manager_InventoryClickBudget_nativeHold(JNIEnv*, jclass, jint clicks) {
    return reserve(clicks, true) ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT void JNICALL
Java_dev_xingclient_manager_InventoryClickBudget_nativeConsume(JNIEnv*, jclass, jint clicks) {
    if (clicks <= 0 || clicks > heldClicks) return;
    reservations.push_back({Clock::now(), clicks});
    heldClicks -= clicks;
    reservedClicks += clicks;
}
extern "C" JNIEXPORT void JNICALL
Java_dev_xingclient_manager_InventoryClickBudget_nativeRelease(JNIEnv*, jclass, jint clicks) {
    if (clicks > 0 && clicks <= heldClicks) heldClicks -= clicks;
}
