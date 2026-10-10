#pragma once

#include "CrystalTargetPhaseDetector.h"
#include <vector>

namespace xing { namespace autocrystal {

enum class CrystalBlockKind {
    Other,
    Air,
    Obsidian,
    Bedrock
};

struct PlacementBlockSnapshot {
    BlockPosition position{};
    CrystalBlockKind kind = CrystalBlockKind::Other;
};

struct PlacementEntitySnapshot {
    int entityId = -1;
    BoundingBox bounds{};
};

struct CrystalPlacement {
    BlockPosition basePosition{};
    double targetDistanceSquared = 0.0;
};

struct CrystalPlacementScanInput {
    bool hasWorld = false;
    Vector3 targetPosition{};
    int radius = 0;
    bool hasSimulatedAirBlock = false;
    BlockPosition simulatedAirBlock{};
    bool requireTwoBlockSpace = true;
    int ignoredEntityId = -1;
    bool hasPhaseInfo = false;
    PhaseInfo phaseInfo{};
    int antiPhaseRadius = 0;
    std::vector<PlacementBlockSnapshot> blocks;
    std::vector<PlacementEntitySnapshot> entities;
};

class CrystalPlacementScanner {
public:
    static std::vector<CrystalPlacement> scan(const CrystalPlacementScanInput& input);
    static bool closestBase(const CrystalPlacementScanInput& input, CrystalPlacement& result);
    static bool canPlaceAt(const CrystalPlacementScanInput& input, const BlockPosition& basePosition);
};

} }
