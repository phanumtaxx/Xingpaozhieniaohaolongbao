#pragma once
#include <cstdint>

namespace xing { namespace managers {
enum class SlotTransactionState { Idle, SwapSent, ActionSent, RestoreSent, Confirmed, Timeout };
enum class SlotPacketPhase { Untracked, Swap, Action, Restore };

struct SlotCommands {
    bool accepted = false;
    int restoreSlot = -1;
    bool restoreClient = false;
    int selectSlot = -1;
    bool selectClient = false;
    std::uint64_t restoreOwner = 0;
    std::uint64_t restoreTransaction = 0;
};

struct SlotState {
    std::uint64_t owner = 0;
    int priority = 0;
    int leaseTicks = 0;
    std::uint64_t silentOwner = 0;
    std::uint64_t transaction = 0;
    int targetSlot = -1;
    int realSlot = -1;
    SlotTransactionState state = SlotTransactionState::Idle;
    SlotTransactionState lastState = SlotTransactionState::Idle;
    std::int64_t swapCommit = -1, actionCommit = -1, restoreCommit = -1;
    int age = 0;
    int restoreTicks = -1;
};

// Owns slot leases and silent packet transactions. Author: uint32.
class SlotSpoofManager {
public:
    SlotCommands acquire(std::uint64_t owner, int priority, int holdTicks, int selectedSlot);
    SlotCommands begin(std::uint64_t owner, int priority, int slot, int selectedSlot,
        int serverSlot, bool silent, int holdTicks);
    SlotCommands finish(std::uint64_t owner, int restoreDelay, bool success, int selectedSlot);
    SlotCommands tick(int selectedSlot);
    SlotCommands release(std::uint64_t owner, int selectedSlot);
    void scheduleRestore(std::uint64_t owner, int delay);
    SlotCommands restoreSilent(std::uint64_t owner, int selectedSlot);
    void keepAlive(std::uint64_t owner, int selectedSlot);
    void committed(std::uint64_t owner, std::uint64_t transaction, SlotPacketPhase phase, int packetSlot,
        int serverSlot, std::int64_t commitId);
    void failed(std::uint64_t owner, std::uint64_t transaction);
    const SlotState& state() const { return state_; }
    void reset();
private:
    bool canAcquire(std::uint64_t owner, int priority) const;
    SlotCommands restoreVisible(int selectedSlot);
    SlotCommands requestSilentRestore(int selectedSlot);
    void complete(SlotTransactionState terminal);
    void clearLease();
    SlotState state_;
    std::uint64_t generations_ = 0;
    std::uint64_t tickOwner_ = 0;
    int originalSlot_ = -1;
    int visibleTarget_ = -1;
    int visibleRestoreTicks_ = -1;
};
} }
