#include "pch.h"
#include "NativeExampleModule.h"

void NativeExampleModule::setEnabled(bool enabled) {
    enabled_ = enabled;
    if (!enabled_) {
        tickCount_ = 0;
    }
}

void NativeExampleModule::onTick() {
    if (enabled_) {
        ++tickCount_;
    }
}

std::int64_t NativeExampleModule::tickCount() const {
    return tickCount_;
}
