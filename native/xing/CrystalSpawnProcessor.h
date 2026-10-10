#pragma once

#include "CrystalActionController.h"
#include "CrystalPrediction.h"

namespace xing { namespace autocrystal {

struct CrystalSpawnInput {
    bool enabled = false;
    bool isEndCrystal = false;
    int spawnedEntityId = -1;
    Vector3 packetPosition{};
    bool hasPlayer = false;
    bool hasWorld = false;
    bool hasGameMode = false;
    bool hasTargetById = false;
    int targetById = -1;
    bool hasFallbackTarget = false;
    int fallbackTargetId = -1;
};

struct CrystalSpawnResult {
    bool shouldBreak = false;
    int targetEntityId = -1;
    BlockPosition basePosition{};
};

class CrystalSpawnProcessor {
public:
    static CrystalSpawnResult handle(const CrystalSpawnInput& input, CrystalActionController& actionController);
};

} }
