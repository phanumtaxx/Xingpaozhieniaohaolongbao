#include "pch.h"
#include "CrystalTargetPhaseDetector.h"

namespace xing { namespace autocrystal {

namespace {
double overlap(double firstMin, double firstMax, double secondMin, double secondMax) {
    const double overlapStart = firstMin > secondMin ? firstMin : secondMin;
    const double overlapEnd = firstMax < secondMax ? firstMax : secondMax;
    return overlapEnd > overlapStart ? overlapEnd - overlapStart : 0.0;
}

bool isPhaseRelevant(const PhaseBlockSnapshot& block) {
    return !block.isAir && block.hasCollisionShape && !block.canBeReplaced;
}
}

bool PhaseInfo::phased() const {
    return state != TargetPhaseState::Normal;
}

PhaseInfo CrystalTargetPhaseDetector::detect(
    const BoundingBox& targetBounds,
    double minimumOverlapDepth,
    const std::vector<PhaseBlockSnapshot>& blocksInWorldOrder) {
    PhaseInfo result;

    for (const PhaseBlockSnapshot& block : blocksInWorldOrder) {
        if (!isPhaseRelevant(block) || !targetBounds.intersects(block.collisionBounds)) continue;

        const double overlapX = overlap(
            targetBounds.minX, targetBounds.maxX,
            block.collisionBounds.minX, block.collisionBounds.maxX);
        const double overlapZ = overlap(
            targetBounds.minZ, targetBounds.maxZ,
            block.collisionBounds.minZ, block.collisionBounds.maxZ);
        const double depth = overlapX > overlapZ ? overlapX : overlapZ;
        const double area = overlapX * overlapZ;

        if (depth > result.maxOverlapDepth
                || (depth == result.maxOverlapDepth && area > result.maxOverlapArea)) {
            result.maxOverlapDepth = depth;
            result.maxOverlapArea = area;
        }
        result.phasedBlocks.push_back(block.position);
    }

    if (result.phasedBlocks.empty()) return result;

    result.state = result.maxOverlapDepth >= minimumOverlapDepth
            ? TargetPhaseState::Phased
            : TargetPhaseState::PartialPhased;
    return result;
}

} }
