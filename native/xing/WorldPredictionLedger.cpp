#include "pch.h"
#include "WorldPredictionLedger.h"
#include <algorithm>

namespace xing { namespace managers {

BlockPrediction WorldPredictionLedger::predict(BlockPrediction prediction, int latencyMillis, std::int64_t now) {
    const auto timeout = (std::min)(1500LL, (std::max)(250LL, static_cast<long long>(latencyMillis) * 5 + 50));
    prediction.generation = ++generation_;
    prediction.createdMillis = now;
    prediction.deadlineMillis = now + timeout;
    prediction.status = PredictionStatus::Pending;
    predictions_[prediction.position] = prediction;
    return prediction;
}

const BlockPrediction* WorldPredictionLedger::get(std::int64_t position) const {
    const auto entry = predictions_.find(position);
    return entry == predictions_.end() ? nullptr : &entry->second;
}

bool WorldPredictionLedger::pending(std::int64_t position, std::int64_t now) const {
    const auto* prediction = get(position);
    return prediction && prediction->status == PredictionStatus::Pending && prediction->deadlineMillis > now;
}

bool WorldPredictionLedger::reconcile(std::int64_t position, std::uint64_t generation, int blockId,
        int itemId, bool absent, bool air, bool replaceable) {
    auto entry = predictions_.find(position);
    if (entry == predictions_.end() || (generation != 0 && entry->second.generation != generation)) return false;
    auto& prediction = entry->second;
    bool matches = false;
    if (prediction.kind == PredictionKind::BlockBreak) matches = absent || air || replaceable;
    else if (!absent && !air) {
        switch (prediction.expectedType) {
        case ExpectedBlockType::Any: matches = true; break;
        case ExpectedBlockType::Block:
        case ExpectedBlockType::BlockState: matches = prediction.expectedId == blockId; break;
        case ExpectedBlockType::Item: matches = prediction.expectedId == itemId; break;
        default: break;
        }
    }
    prediction.status = matches ? PredictionStatus::Confirmed : PredictionStatus::Mismatch;
    return matches;
}

void WorldPredictionLedger::clearOwner(std::uint64_t owner) {
    if (owner == 0) return;
    for (auto entry = predictions_.begin(); entry != predictions_.end();) {
        if (entry->second.owner == owner) entry = predictions_.erase(entry);
        else ++entry;
    }
}

void WorldPredictionLedger::clearPosition(std::int64_t position) { predictions_.erase(position); }

void WorldPredictionLedger::tick(std::int64_t now) {
    for (auto entry = predictions_.begin(); entry != predictions_.end();) {
        const auto& prediction = entry->second;
        if (prediction.deadlineMillis <= now || prediction.status != PredictionStatus::Pending)
            entry = predictions_.erase(entry);
        else ++entry;
    }
}

void WorldPredictionLedger::clear() { predictions_.clear(); }

} }
