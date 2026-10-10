#include "pch.h"
#include "CombatActionScheduler.h"
#include <algorithm>

namespace xing { namespace managers {

bool CombatActionScheduler::valid(const CombatRequest& request) {
    return request.owner != 0 && (request.channel == -1 || CombatRateLimiter::validChannel(request.channel))
            && CombatResourceManager::validMask(request.resources);
}

int CombatActionScheduler::reservedBudget(int channel) const {
    int reserved = 0;
    for (const auto& pair : executing_) {
        if (!pair.second.finished && !pair.second.counted && pair.second.request.channel == channel) ++reserved;
    }
    return reserved;
}

CombatResult CombatActionScheduler::begin(CombatRequest request) {
    if (request.id == 0) request.id = ++nextId_;
    CombatResult result{CombatStatus::Ready, -1, request.id, request.owner};
    if (!valid(request) || executing_.count(request.id) != 0) {
        result.status = CombatStatus::Invalidated;
    } else {
        for (const auto& pair : executing_) {
            if (!pair.second.finished && pair.second.request.owner == request.owner) {
                return {CombatStatus::Ready, -1, pair.first, request.owner, true};
            }
        }
        if (!rates_.canUse(request.channel, reservedBudget(request.channel))) {
            result.status = CombatStatus::RateLimited;
        } else {
            result.deniedResource = resources_.denied(request.owner, request.priority, request.resources);
            if (result.deniedResource != -1 || !resources_.acquire(request.owner, request.priority, request.holdTicks, request.resources)) {
                result.status = CombatStatus::ResourceDenied;
            } else {
                executing_.emplace(request.id, Execution{request});
            }
        }
    }
    if (result.status != CombatStatus::Ready) recordOutcome(request, result.status);
    return result;
}

void CombatActionScheduler::account(Execution& execution) {
    if (execution.counted) return;
    resources_.commit(execution.request.owner, execution.request.resources);
    rates_.consume(execution.request.channel, execution.request.owner);
    execution.counted = true;
}

CombatResult CombatActionScheduler::finish(std::uint64_t id, CombatStatus outcome) {
    auto found = executing_.find(id);
    if (found == executing_.end()) return {CombatStatus::Invalidated, -1, id};
    auto& execution = found->second;
    if (execution.finished) return {execution.outcome, -1, id, execution.request.owner};
    const bool success = outcome == CombatStatus::Success;
    if (success || execution.committed) account(execution);
    else resources_.release(execution.request.owner, execution.request.resources);
    if (!success && execution.committed) outcome = CombatStatus::ActionFailedCommitted;
    execution.finished = true;
    execution.outcome = outcome;
    recordOutcome(execution.request, outcome);
    return {outcome, -1, id, execution.request.owner};
}

CombatResult CombatActionScheduler::enqueue(CombatRequest request) {
    request.id = ++nextId_;
    if (!valid(request)) return {CombatStatus::Invalidated, -1, request.id, request.owner};
    if (request.key != 0) submittedKeys_.insert(request.key);
    pending_.push_back(request);
    return {CombatStatus::Queued, -1, request.id, request.owner};
}

bool CombatActionScheduler::unresolved(const CombatRequest& request) const {
    const auto hasUnresolved = [this](const std::vector<std::uint64_t>& dependencies) {
        for (auto key : dependencies) {
            if (submittedKeys_.count(key) != 0 && outcomes_.count(key) == 0) return true;
        }
        return false;
    };
    return hasUnresolved(request.after) || hasUnresolved(request.requiresSuccess);
}

CombatResult CombatActionScheduler::next() {
    if (pending_.empty()) return {};
    std::stable_sort(pending_.begin(), pending_.end(), [](const CombatRequest& first, const CombatRequest& second) {
        return first.priority != second.priority ? first.priority > second.priority : first.id < second.id;
    });
    auto found = std::find_if(pending_.begin(), pending_.end(), [this](const CombatRequest& request) { return !unresolved(request); });
    const bool cycle = dependencyCycle_ || found == pending_.end();
    if (cycle) dependencyCycle_ = true;
    if (cycle) found = pending_.begin();
    CombatRequest request = *found;
    pending_.erase(found);
    bool failedDependency = cycle;
    for (auto key : request.requiresSuccess) {
        const auto outcome = outcomes_.find(key);
        if (outcome != outcomes_.end() && outcome->second != CombatStatus::Success) failedDependency = true;
    }
    if (failedDependency) {
        recordOutcome(request, CombatStatus::Invalidated);
        return {CombatStatus::Invalidated, -1, request.id, request.owner};
    }
    selected_.emplace(request.id, request);
    return {CombatStatus::Ready, -1, request.id, request.owner};
}

CombatResult CombatActionScheduler::activate(std::uint64_t id) {
    const auto found = selected_.find(id);
    if (found == selected_.end()) return {CombatStatus::Invalidated, -1, id};
    auto request = found->second;
    selected_.erase(found);
    return begin(request);
}

CombatResult CombatActionScheduler::invalidate(std::uint64_t id) {
    const auto found = selected_.find(id);
    if (found == selected_.end()) return {CombatStatus::Invalidated, -1, id};
    auto request = found->second;
    selected_.erase(found);
    recordOutcome(request, CombatStatus::Invalidated);
    return {CombatStatus::Invalidated, -1, id, request.owner};
}

void CombatActionScheduler::recordOutcome(const CombatRequest& request, CombatStatus status) {
    if (request.key == 0) return;
    auto found = outcomes_.find(request.key);
    if (found == outcomes_.end() || found->second != CombatStatus::Success) outcomes_[request.key] = status;
}

CombatResult CombatActionScheduler::cancel(std::uint64_t id) {
    const auto selected = selected_.find(id);
    if (selected != selected_.end()) {
        auto request = selected->second;
        selected_.erase(selected);
        recordOutcome(request, CombatStatus::Cancelled);
        return {CombatStatus::Cancelled, -1, id, request.owner};
    }
    const auto queued = std::find_if(pending_.begin(), pending_.end(), [id](const CombatRequest& request) { return request.id == id; });
    if (queued != pending_.end()) {
        auto request = *queued;
        pending_.erase(queued);
        recordOutcome(request, CombatStatus::Cancelled);
        return {CombatStatus::Cancelled, -1, id, request.owner};
    }
    return finish(id, CombatStatus::Cancelled);
}

void CombatActionScheduler::cancelOwner(std::uint64_t owner) {
    for (auto iterator = selected_.begin(); iterator != selected_.end();) {
        if (iterator->second.owner != owner) { ++iterator; continue; }
        recordOutcome(iterator->second, CombatStatus::Cancelled);
        iterator = selected_.erase(iterator);
    }
    for (auto iterator = pending_.begin(); iterator != pending_.end();) {
        if (iterator->owner != owner) { ++iterator; continue; }
        recordOutcome(*iterator, CombatStatus::Cancelled);
        iterator = pending_.erase(iterator);
    }
    for (auto& pair : executing_) {
        if (pair.second.request.owner == owner && !pair.second.finished) finish(pair.first, CombatStatus::Cancelled);
    }
    resources_.release(owner, 0xffU);
}

CombatResult CombatActionScheduler::reserve(const CombatRequest& request) {
    if (!valid(request)) return {CombatStatus::Invalidated};
    const int denied = resources_.denied(request.owner, request.priority, request.resources);
    return {resources_.acquire(request.owner, request.priority, request.holdTicks, request.resources)
            ? CombatStatus::Success : CombatStatus::ResourceDenied, denied, 0, request.owner};
}
void CombatActionScheduler::release(std::uint64_t owner, std::uint32_t resources) { resources_.release(owner, resources); }

PacketCommitState CombatActionScheduler::packetCommitted(
        std::uint64_t owner, std::uint64_t actionId, int sequence, std::uint64_t epoch) {
    if (epoch != epoch_) return {};
    const auto commit = packets_.commit(owner, sequence);
    auto found = executing_.find(actionId);
    if (found != executing_.end() && found->second.request.owner == owner) {
        found->second.committed = true;
        account(found->second);
        if (found->second.finished && found->second.outcome != CombatStatus::Success) {
            found->second.outcome = CombatStatus::ActionFailedCommitted;
            recordOutcome(found->second.request, found->second.outcome);
        }
    }
    return commit;
}

void CombatActionScheduler::tick() {
    rates_.tick();
    resources_.tick();
    pending_.clear();
    outcomes_.clear();
    submittedKeys_.clear();
    executing_.clear();
    selected_.clear();
    dependencyCycle_ = false;
}

void CombatActionScheduler::endFrame() { outcomes_.clear(); submittedKeys_.clear(); dependencyCycle_ = false; }
void CombatActionScheduler::reset() {
    ++epoch_;
    rates_.reset();
    resources_.reset();
    packets_.reset();
    pending_.clear();
    executing_.clear();
    selected_.clear();
    dependencyCycle_ = false;
    outcomes_.clear();
    submittedKeys_.clear();
}

} }
