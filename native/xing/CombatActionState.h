#pragma once
#include <array>

namespace xing { namespace managers {
enum class CombatActivity { Crystal, PacketMine, Generic, Count };

// Shared combat activity windows. Author: uint32.
class CombatActionState {
public:
    void mark(CombatActivity activity, int ticks);
    void tick();
    void clear();
    int remaining(CombatActivity activity) const;
    bool active() const;
private:
    std::array<int, static_cast<int>(CombatActivity::Count)> ticks_{};
};
} }
