#pragma once

#include <cstdint>
#include <unordered_map>

namespace xing { namespace managers {

enum class PredictionKind { BlockPlace, BlockBreak, Crystal };
enum class PredictionStatus { Pending, Confirmed, Mismatch };
enum class ExpectedBlockType { Any, Block, Item, Unsupported, BlockState };

struct BlockPrediction {
    std::uint64_t generation = 0;
    std::int64_t position = 0;
    std::uint64_t owner = 0;
    std::int64_t createdMillis = 0;
    std::int64_t deadlineMillis = 0;
    PredictionKind kind = PredictionKind::BlockPlace;
    ExpectedBlockType expectedType = ExpectedBlockType::Any;
    int expectedId = -1;
    int expectedState = -1;
    PredictionStatus status = PredictionStatus::Pending;
};

class WorldPredictionLedger {
public:
    BlockPrediction predict(BlockPrediction prediction, int latencyMillis, std::int64_t now);
    const BlockPrediction* get(std::int64_t position) const;
    bool pending(std::int64_t position, std::int64_t now) const;
    bool reconcile(std::int64_t position, std::uint64_t generation, int blockId, int itemId,
        bool absent, bool air, bool replaceable);
    void clearOwner(std::uint64_t owner);
    void clearPosition(std::int64_t position);
    void tick(std::int64_t now);
    void clear();

private:
    std::uint64_t generation_ = 0;
    std::unordered_map<std::int64_t, BlockPrediction> predictions_;
};

} }
