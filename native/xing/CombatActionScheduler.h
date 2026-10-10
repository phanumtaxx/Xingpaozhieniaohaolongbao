#pragma once

#include "CombatRateLimiter.h"
#include "CombatResourceManager.h"
#include "PacketCommitTracker.h"
#include <unordered_map>
#include <unordered_set>
#include <vector>

namespace xing { namespace managers {

enum class CombatStatus { Ready, Queued, Success, RateLimited, ResourceDenied, ActionFailed, ActionFailedCommitted, Invalidated, Cancelled, Empty };

struct CombatRequest {
    std::uint64_t id = 0;
    std::uint64_t key = 0;
    std::uint64_t owner = 0;
    int priority = 0;
    int holdTicks = 1;
    int channel = -1;
    std::uint32_t resources = 0;
    std::vector<std::uint64_t> after;
    std::vector<std::uint64_t> requiresSuccess;
};

struct CombatResult {
    CombatStatus status = CombatStatus::Empty;
    int deniedResource = -1;
    std::uint64_t id = 0;
    std::uint64_t owner = 0;
    bool nested = false;
};

// Native scheduling policy ported from Synthetic by uint32.
class CombatActionScheduler {
public:
    CombatResult begin(CombatRequest request);
    CombatResult finish(std::uint64_t id, CombatStatus outcome);
    CombatResult enqueue(CombatRequest request);
    CombatResult next();
    CombatResult activate(std::uint64_t id);
    CombatResult invalidate(std::uint64_t id);
    CombatResult cancel(std::uint64_t id);
    void cancelOwner(std::uint64_t owner);
    CombatResult reserve(const CombatRequest& request);
    void release(std::uint64_t owner, std::uint32_t resources);
    PacketCommitState packetCommitted(std::uint64_t owner, std::uint64_t actionId,
            int sequence, std::uint64_t epoch);
    void tick();
    void endFrame();
    void reset();
    std::uint64_t epoch() const { return epoch_; }
    CombatRateLimiter& rates() { return rates_; }
    bool canUse(int channel) const { return rates_.canUse(channel, reservedBudget(channel)); }
    const PacketCommitTracker& packets() const { return packets_; }

private:
    struct Execution {
        CombatRequest request;
        bool committed = false;
        bool counted = false;
        bool finished = false;
        CombatStatus outcome = CombatStatus::Ready;
    };
    std::uint64_t nextId_ = 0;
    std::uint64_t epoch_ = 1;
    CombatRateLimiter rates_;
    CombatResourceManager resources_;
    PacketCommitTracker packets_;
    std::vector<CombatRequest> pending_;
    std::unordered_map<std::uint64_t, Execution> executing_;
    std::unordered_map<std::uint64_t, CombatRequest> selected_;
    std::unordered_map<std::uint64_t, CombatStatus> outcomes_;
    std::unordered_set<std::uint64_t> submittedKeys_;
    bool dependencyCycle_ = false;
    bool unresolved(const CombatRequest& request) const;
    void recordOutcome(const CombatRequest& request, CombatStatus status);
    void account(Execution& execution);
    int reservedBudget(int channel) const;
    static bool valid(const CombatRequest& request);
};

} }
