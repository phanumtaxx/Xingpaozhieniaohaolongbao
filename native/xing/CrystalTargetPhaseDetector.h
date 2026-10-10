#pragma once

#include "CrystalActionController.h"
#include "CrystalPrediction.h"
#include <vector>

namespace xing { namespace autocrystal {

enum class TargetPhaseState {
    Normal,
    PartialPhased,
    Phased
};

struct PhaseBlockSnapshot {
    BlockPosition position{};
    bool isAir = false;
    bool canBeReplaced = false;
    bool hasCollisionShape = false;
    BoundingBox collisionBounds{};
};

struct PhaseInfo {
    TargetPhaseState state = TargetPhaseState::Normal;
    std::vector<BlockPosition> phasedBlocks;
    double maxOverlapDepth = 0.0;
    double maxOverlapArea = 0.0;

    bool phased() const;
};

class CrystalTargetPhaseDetector {
public:
    static PhaseInfo detect(
        const BoundingBox& targetBounds,
        double minimumOverlapDepth,
        const std::vector<PhaseBlockSnapshot>& blocksInWorldOrder);
};

} }
