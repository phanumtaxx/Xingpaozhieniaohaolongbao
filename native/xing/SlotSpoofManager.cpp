#include "pch.h"
#include "SlotSpoofManager.h"
#include <algorithm>
#include <limits>

namespace xing { namespace managers {
namespace {
constexpr int TransactionTimeoutTicks = 40;
constexpr int InfiniteLease = (std::numeric_limits<int>::max)();
bool validSlot(int slot) { return slot >= 0 && slot < 9; }
}

bool SlotSpoofManager::canAcquire(std::uint64_t owner, int priority) const {
    if (owner == 0) return false;
    if (state_.owner != 0 && state_.owner != owner) return priority > state_.priority;
    return tickOwner_ == 0 || tickOwner_ == owner;
}

SlotCommands SlotSpoofManager::acquire(std::uint64_t owner, int priority, int holdTicks, int selectedSlot) {
    if (!validSlot(selectedSlot) || !canAcquire(owner, priority)) return {};
    if (state_.state == SlotTransactionState::RestoreSent) return {};
    if (state_.silentOwner != 0 && state_.silentOwner != owner) return requestSilentRestore(selectedSlot);
    SlotCommands commands;
    if (state_.owner != 0 && state_.owner != owner && originalSlot_ >= 0) commands = restoreVisible(selectedSlot);
    state_.owner = owner;
    state_.priority = priority;
    state_.leaseTicks = (std::max)(1, holdTicks);
    commands.accepted = true;
    return commands;
}

SlotCommands SlotSpoofManager::begin(std::uint64_t owner, int priority, int slot, int selectedSlot,
        int serverSlot, bool silent, int holdTicks) {
    if (!validSlot(slot)) return {};
    if (state_.silentOwner == owner && (!silent || state_.state == SlotTransactionState::RestoreSent))
        return restoreSilent(owner, selectedSlot);
    auto commands = acquire(owner, priority, holdTicks, selectedSlot);
    if (!commands.accepted) return commands;
    if (commands.restoreClient) selectedSlot = commands.restoreSlot;
    tickOwner_ = owner;
    if (silent) {
        if (originalSlot_ >= 0) {
            commands = restoreVisible(selectedSlot);
            if (commands.restoreClient) selectedSlot = commands.restoreSlot;
            state_.owner = owner;
            state_.priority = priority;
            state_.leaseTicks = (std::max)(1, holdTicks);
        }
        const bool sameTarget = state_.silentOwner == owner && state_.targetSlot == slot;
        if (state_.silentOwner == 0 || sameTarget) state_.realSlot = selectedSlot;
        state_.silentOwner = owner;
        state_.targetSlot = slot;
        state_.restoreTicks = -1;
        state_.age = 0;
        if (sameTarget && serverSlot == slot) {
            commands.accepted = true;
            return commands;
        }
        state_.transaction = ++generations_;
        state_.swapCommit = state_.actionCommit = state_.restoreCommit = -1;
        state_.state = state_.lastState = SlotTransactionState::SwapSent;
        commands.selectSlot = slot;
    } else {
        if (originalSlot_ < 0 && selectedSlot != slot) originalSlot_ = selectedSlot;
        visibleTarget_ = slot;
        visibleRestoreTicks_ = -1;
        if (selectedSlot != slot || serverSlot != slot) commands.selectSlot = slot;
        commands.selectClient = true;
    }
    commands.accepted = true;
    return commands;
}

SlotCommands SlotSpoofManager::finish(std::uint64_t owner, int restoreDelay, bool success, int selectedSlot) {
    if (state_.owner != owner) return {};
    if (state_.silentOwner == owner) {
        scheduleRestore(owner, success ? restoreDelay : 0);
        if (state_.restoreTicks == 0) return requestSilentRestore(selectedSlot);
    } else {
        visibleRestoreTicks_ = success ? (std::max)(0, restoreDelay) : 0;
        const auto hold = (std::min)(static_cast<long long>(InfiniteLease), static_cast<long long>(visibleRestoreTicks_) + 1);
        state_.leaseTicks = (std::max)(state_.leaseTicks, static_cast<int>(hold));
        if (visibleRestoreTicks_ == 0) return restoreVisible(selectedSlot);
    }
    SlotCommands commands;
    commands.accepted = true;
    return commands;
}

void SlotSpoofManager::scheduleRestore(std::uint64_t owner, int delay) {
    if (state_.silentOwner != owner || state_.state == SlotTransactionState::RestoreSent) return;
    state_.restoreTicks = (std::max)(0, delay);
    const auto hold = (std::min)(static_cast<long long>(InfiniteLease), static_cast<long long>(state_.restoreTicks) + 1);
    state_.leaseTicks = (std::max)(state_.leaseTicks, static_cast<int>(hold));
}

SlotCommands SlotSpoofManager::tick(int selectedSlot) {
    tickOwner_ = 0;
    if (state_.silentOwner != 0) {
        if (++state_.age > TransactionTimeoutTicks) {
            auto commands = requestSilentRestore(selectedSlot);
            complete(SlotTransactionState::Timeout);
            return commands;
        }
        if (state_.state == SlotTransactionState::RestoreSent) return {};
        if (selectedSlot != state_.realSlot) return requestSilentRestore(selectedSlot);
        if (state_.leaseTicks != InfiniteLease && --state_.leaseTicks <= 0) return requestSilentRestore(selectedSlot);
        if (state_.restoreTicks > 0) --state_.restoreTicks;
        else if (state_.restoreTicks == 0) return requestSilentRestore(selectedSlot);
        return {};
    }
    if (originalSlot_ >= 0) {
        if (selectedSlot != visibleTarget_) return restoreVisible(selectedSlot);
        if (visibleRestoreTicks_ > 0) --visibleRestoreTicks_;
        else if (visibleRestoreTicks_ == 0) return restoreVisible(selectedSlot);
        return {};
    }
    if (state_.owner != 0 && state_.leaseTicks != InfiniteLease && --state_.leaseTicks <= 0) clearLease();
    return {};
}

SlotCommands SlotSpoofManager::restoreVisible(int selectedSlot) {
    SlotCommands commands;
    commands.accepted = true;
    if (originalSlot_ >= 0) {
        const bool manualChange = selectedSlot != visibleTarget_;
        commands.restoreSlot = manualChange ? selectedSlot : originalSlot_;
        commands.restoreClient = !manualChange;
        commands.restoreOwner = state_.owner;
    }
    originalSlot_ = visibleTarget_ = visibleRestoreTicks_ = -1;
    clearLease();
    return commands;
}

SlotCommands SlotSpoofManager::requestSilentRestore(int selectedSlot) {
    if (state_.silentOwner == 0 || state_.state == SlotTransactionState::RestoreSent) return {};
    SlotCommands commands;
    if (validSlot(selectedSlot)) state_.realSlot = selectedSlot;
    if (!validSlot(state_.realSlot)) { complete(SlotTransactionState::Timeout); return commands; }
    commands.restoreSlot = state_.realSlot;
    commands.restoreOwner = state_.silentOwner;
    commands.restoreTransaction = state_.transaction;
    state_.restoreTicks = -1;
    state_.state = state_.lastState = SlotTransactionState::RestoreSent;
    return commands;
}

SlotCommands SlotSpoofManager::restoreSilent(std::uint64_t owner, int selectedSlot) {
    return state_.silentOwner == owner ? requestSilentRestore(selectedSlot) : SlotCommands{};
}

SlotCommands SlotSpoofManager::release(std::uint64_t owner, int selectedSlot) {
    if (owner != 0 && state_.owner != owner) return {};
    if (state_.silentOwner != 0) return requestSilentRestore(selectedSlot);
    return restoreVisible(selectedSlot);
}

void SlotSpoofManager::keepAlive(std::uint64_t owner, int selectedSlot) {
    if (state_.silentOwner != owner) return;
    state_.age = 0;
    if (validSlot(selectedSlot) && state_.state != SlotTransactionState::RestoreSent) state_.realSlot = selectedSlot;
}

void SlotSpoofManager::committed(std::uint64_t owner, std::uint64_t transaction, SlotPacketPhase phase, int packetSlot,
        int serverSlot, std::int64_t commitId) {
    if (owner == 0 || state_.silentOwner != owner || state_.transaction != transaction || commitId <= 0) return;
    state_.age = 0;
    if (packetSlot >= 0) {
        if (phase == SlotPacketPhase::Swap && packetSlot == state_.targetSlot) state_.swapCommit = commitId;
        else if (phase == SlotPacketPhase::Restore && state_.state == SlotTransactionState::RestoreSent && packetSlot == state_.realSlot) {
            state_.restoreCommit = commitId;
            complete(SlotTransactionState::Confirmed);
        }
        return;
    }
    if (phase == SlotPacketPhase::Action
            && (state_.state == SlotTransactionState::SwapSent || state_.state == SlotTransactionState::ActionSent
                || state_.state == SlotTransactionState::RestoreSent)
            && serverSlot == state_.targetSlot) {
        state_.actionCommit = commitId;
        if (state_.state != SlotTransactionState::RestoreSent)
            state_.state = state_.lastState = SlotTransactionState::ActionSent;
    }
}

void SlotSpoofManager::failed(std::uint64_t owner, std::uint64_t transaction) {
    if (state_.silentOwner == owner && state_.transaction == transaction) complete(SlotTransactionState::Timeout);
}

void SlotSpoofManager::clearLease() { state_.owner = 0; state_.priority = state_.leaseTicks = 0; }
void SlotSpoofManager::complete(SlotTransactionState terminal) {
    const auto owner = state_.silentOwner;
    state_.lastState = terminal;
    state_.silentOwner = state_.transaction = 0;
    state_.targetSlot = state_.realSlot = state_.restoreTicks = -1;
    state_.state = SlotTransactionState::Idle;
    state_.swapCommit = state_.actionCommit = state_.restoreCommit = -1;
    state_.age = 0;
    if (state_.owner == owner) clearLease();
}
void SlotSpoofManager::reset() {
    state_ = SlotState{};
    tickOwner_ = 0;
    originalSlot_ = visibleTarget_ = visibleRestoreTicks_ = -1;
}
} }
