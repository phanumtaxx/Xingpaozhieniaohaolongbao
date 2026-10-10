#include "pch.h"
#include "CombatActionState.h"
#include <algorithm>

namespace xing { namespace managers {
void CombatActionState::mark(CombatActivity activity, int ticks) {
    const auto index = static_cast<int>(activity);
    if (index < 0 || index >= static_cast<int>(ticks_.size())) return;
    ticks_[index] = (std::max)(ticks_[index], (std::max)(0, ticks));
}
void CombatActionState::tick() { for (auto& ticks : ticks_) if (ticks > 0) --ticks; }
void CombatActionState::clear() { ticks_.fill(0); }
int CombatActionState::remaining(CombatActivity activity) const {
    const auto index = static_cast<int>(activity);
    return index >= 0 && index < static_cast<int>(ticks_.size()) ? ticks_[index] : 0;
}
bool CombatActionState::active() const {
    return std::any_of(ticks_.begin(), ticks_.end(), [](int ticks) { return ticks > 0; });
}
} }
