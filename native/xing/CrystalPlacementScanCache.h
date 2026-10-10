#pragma once

#include "CrystalPlacementScorer.h"
#include <cstdint>
#include <vector>

namespace xing { namespace autocrystal {

struct CrystalPlacementScanKey {
    std::uint64_t worldGeneration = 0;
    std::uint64_t targetInstanceToken = 0;
    int targetEntityId = -1;
    bool hasSimulatedAirBlock = false;
    BlockPosition simulatedAirBlock{};
    BlockPosition predictionBlock{};
    PhaseInfo phaseInfo{};
};

class CrystalPlacementScanCache {
public:
    bool shouldRescan(const CrystalPlacementScanKey& key, int scanInterval);
    void updatePlacements(std::vector<CrystalPlacementScore> placements);
    const std::vector<CrystalPlacementScore>& placements() const;
    int cooldown() const;
    void clear();

private:
    static bool sameKey(const CrystalPlacementScanKey& first, const CrystalPlacementScanKey& second);

    bool hasKey_ = false;
    CrystalPlacementScanKey key_{};
    std::vector<CrystalPlacementScore> placements_;
    int cooldown_ = 0;
};

} }
