#include "pch.h"
#include "CrystalSpawnProcessor.h"
#include <cmath>

namespace xing { namespace autocrystal {

namespace {
int floorToInt(double value) {
    return static_cast<int>(std::floor(value));
}

BlockPosition spawnedBase(const Vector3& position) {
    return {
        floorToInt(position.x),
        floorToInt(position.y) - 1,
        floorToInt(position.z)
    };
}
}

CrystalSpawnResult CrystalSpawnProcessor::handle(
        const CrystalSpawnInput& input,
        CrystalActionController& actionController) {
    CrystalSpawnResult result;
    if (!input.enabled || !input.isEndCrystal) return result;

    const BlockPosition base = spawnedBase(input.packetPosition);
    if (!actionController.isPending(base)) return result;
    if (!input.hasPlayer || !input.hasWorld || !input.hasGameMode) return result;

    int targetEntityId = -1;
    const int pendingTargetId = actionController.pendingTargetId();
    if (pendingTargetId >= 0 && input.hasTargetById) {
        targetEntityId = input.targetById;
    } else if (input.hasFallbackTarget) {
        targetEntityId = input.fallbackTargetId;
    }
    if (targetEntityId < 0) return result;

    actionController.markSpawned(input.spawnedEntityId, base);
    result.shouldBreak = true;
    result.targetEntityId = targetEntityId;
    result.basePosition = base;
    return result;
}

} }
