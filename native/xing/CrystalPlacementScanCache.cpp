#include "pch.h"
#include "CrystalPlacementScanCache.h"
#include <utility>

namespace xing { namespace autocrystal {

namespace {
bool sameBlock(const BlockPosition& first, const BlockPosition& second) {
    return first == second;
}
}

bool CrystalPlacementScanCache::sameKey(
        const CrystalPlacementScanKey& first,
        const CrystalPlacementScanKey& second) {
    if (first.worldGeneration != second.worldGeneration
            || first.targetInstanceToken != second.targetInstanceToken
            || first.targetEntityId != second.targetEntityId
            || first.hasSimulatedAirBlock != second.hasSimulatedAirBlock
            || !sameBlock(first.predictionBlock, second.predictionBlock)
            || first.phaseInfo.state != second.phaseInfo.state
            || first.phaseInfo.phasedBlocks != second.phaseInfo.phasedBlocks) {
        return false;
    }
    return !first.hasSimulatedAirBlock || sameBlock(first.simulatedAirBlock, second.simulatedAirBlock);
}

bool CrystalPlacementScanCache::shouldRescan(const CrystalPlacementScanKey& key, int scanInterval) {
    if (cooldown_ > 0 && hasKey_ && sameKey(key_, key)) {
        --cooldown_;
        return false;
    }

    key_ = key;
    hasKey_ = true;
    cooldown_ = scanInterval - 1;
    return true;
}

void CrystalPlacementScanCache::updatePlacements(std::vector<CrystalPlacementScore> placements) {
    placements_ = std::move(placements);
}

const std::vector<CrystalPlacementScore>& CrystalPlacementScanCache::placements() const {
    return placements_;
}

int CrystalPlacementScanCache::cooldown() const {
    return cooldown_;
}

void CrystalPlacementScanCache::clear() {
    hasKey_ = false;
    key_ = {};
    placements_.clear();
    cooldown_ = 0;
}

} }
