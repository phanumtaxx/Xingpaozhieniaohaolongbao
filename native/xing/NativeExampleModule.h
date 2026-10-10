#pragma once

#include <cstdint>

class NativeExampleModule {
public:
    void setEnabled(bool enabled);
    void onTick();
    std::int64_t tickCount() const;

private:
    bool enabled_ = false;
    std::int64_t tickCount_ = 0;
};
