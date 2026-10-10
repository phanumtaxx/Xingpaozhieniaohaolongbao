#include "pch.h"
#include "CrystalPlacementScanner.h"
#include <cmath>
#include <cstdint>
#include <cstdlib>
#include <unordered_map>
#include <unordered_set>

namespace xing { namespace autocrystal {

namespace {
struct BlockPositionHash {
    std::size_t operator()(const BlockPosition& position) const {
        std::size_t hash = static_cast<std::uint32_t>(position.x);
        hash ^= static_cast<std::size_t>(static_cast<std::uint32_t>(position.y)) + 0x9e3779b9U + (hash << 6) + (hash >> 2);
        hash ^= static_cast<std::size_t>(static_cast<std::uint32_t>(position.z)) + 0x9e3779b9U + (hash << 6) + (hash >> 2);
        return hash;
    }
};

using BlockMap = std::unordered_map<BlockPosition, CrystalBlockKind, BlockPositionHash>;

BlockPosition containing(const Vector3& position) {
    return {
        static_cast<int>(std::floor(position.x)),
        static_cast<int>(std::floor(position.y)),
        static_cast<int>(std::floor(position.z))
    };
}

BlockPosition offset(const BlockPosition& position, int x, int y, int z) {
    return {position.x + x, position.y + y, position.z + z};
}

double distanceToBlockCenterSquared(const BlockPosition& position, const Vector3& point) {
    const double x = static_cast<double>(position.x) + 0.5 - point.x;
    const double y = static_cast<double>(position.y) + 0.5 - point.y;
    const double z = static_cast<double>(position.z) + 0.5 - point.z;
    return x * x + y * y + z * z;
}

BlockMap makeBlockMap(const CrystalPlacementScanInput& input) {
    BlockMap blocks;
    blocks.reserve(input.blocks.size());
    for (const PlacementBlockSnapshot& block : input.blocks) {
        blocks[block.position] = block.kind;
    }
    return blocks;
}

CrystalBlockKind blockAt(const BlockMap& blocks, const CrystalPlacementScanInput& input, const BlockPosition& position) {
    if (input.hasSimulatedAirBlock && position == input.simulatedAirBlock) {
        return CrystalBlockKind::Air;
    }
    const auto found = blocks.find(position);
    return found == blocks.end() ? CrystalBlockKind::Other : found->second;
}

bool hasEntityInCrystalSpace(
        const CrystalPlacementScanInput& input,
        const BlockPosition& basePosition) {
    const BoundingBox crystalSpace{
        static_cast<double>(basePosition.x),
        static_cast<double>(basePosition.y + 1),
        static_cast<double>(basePosition.z),
        static_cast<double>(basePosition.x + 1),
        static_cast<double>(basePosition.y + 3),
        static_cast<double>(basePosition.z + 1)
    };

    for (const PlacementEntitySnapshot& entity : input.entities) {
        if (entity.entityId != input.ignoredEntityId && entity.bounds.intersects(crystalSpace)) {
            return true;
        }
    }
    return false;
}

bool canPlaceAtInSnapshot(
        const CrystalPlacementScanInput& input,
        const BlockMap& blocks,
        const BlockPosition& basePosition) {
    if (!input.hasWorld) return false;

    const CrystalBlockKind base = blockAt(blocks, input, basePosition);
    if (base != CrystalBlockKind::Obsidian && base != CrystalBlockKind::Bedrock) return false;
    if (blockAt(blocks, input, offset(basePosition, 0, 1, 0)) != CrystalBlockKind::Air) return false;
    if (input.requireTwoBlockSpace
            && blockAt(blocks, input, offset(basePosition, 0, 2, 0)) != CrystalBlockKind::Air) {
        return false;
    }
    return !hasEntityInCrystalSpace(input, basePosition);
}

void addPlacement(
        const CrystalPlacementScanInput& input,
        const BlockMap& blocks,
        const BlockPosition& position,
        std::unordered_set<BlockPosition, BlockPositionHash>& knownPositions,
        std::vector<CrystalPlacement>& placements) {
    if (knownPositions.find(position) != knownPositions.end()
            || !canPlaceAtInSnapshot(input, blocks, position)) {
        return;
    }

    knownPositions.insert(position);
    placements.push_back({position, distanceToBlockCenterSquared(position, input.targetPosition)});
}

void addAround(
        const CrystalPlacementScanInput& input,
        const BlockMap& blocks,
        const BlockPosition& center,
        int radius,
        std::unordered_set<BlockPosition, BlockPositionHash>& knownPositions,
        std::vector<CrystalPlacement>& placements) {
    const BlockPosition minimum = offset(center, -radius, -1, -radius);
    const BlockPosition maximum = offset(center, radius, 1, radius);

    for (int z = minimum.z; z <= maximum.z; ++z) {
        for (int y = minimum.y; y <= maximum.y; ++y) {
            for (int x = minimum.x; x <= maximum.x; ++x) {
                addPlacement(input, blocks, {x, y, z}, knownPositions, placements);
            }
        }
    }
}

bool isNearPhase(const BlockPosition& block, const PhaseInfo& phaseInfo) {
    for (const BlockPosition& phasedBlock : phaseInfo.phasedBlocks) {
        const long long distance = std::llabs(static_cast<long long>(block.x) - phasedBlock.x)
                + std::llabs(static_cast<long long>(block.y) - phasedBlock.y)
                + std::llabs(static_cast<long long>(block.z) - phasedBlock.z);
        if (distance <= 1) return true;
    }
    return false;
}

}

bool CrystalPlacementScanner::canPlaceAt(
        const CrystalPlacementScanInput& input,
        const BlockPosition& basePosition) {
    const BlockMap blocks = makeBlockMap(input);
    return canPlaceAtInSnapshot(input, blocks, basePosition);
}

std::vector<CrystalPlacement> CrystalPlacementScanner::scan(const CrystalPlacementScanInput& input) {
    std::vector<CrystalPlacement> placements;
    if (!input.hasWorld) return placements;

    const BlockMap blocks = makeBlockMap(input);
    const BlockPosition center = containing(input.targetPosition);
    const BlockPosition minimum = offset(center, -input.radius, -input.radius, -input.radius);
    const BlockPosition maximum = offset(center, input.radius, input.radius, input.radius);

    for (int z = minimum.z; z <= maximum.z; ++z) {
        for (int y = minimum.y; y <= maximum.y; ++y) {
            for (int x = minimum.x; x <= maximum.x; ++x) {
                const BlockPosition position{x, y, z};
                if (canPlaceAtInSnapshot(input, blocks, position)) {
                    placements.push_back({position, distanceToBlockCenterSquared(position, input.targetPosition)});
                }
            }
        }
    }

    if (!input.hasPhaseInfo || !input.phaseInfo.phased() || input.antiPhaseRadius <= 0) {
        return placements;
    }

    std::unordered_set<BlockPosition, BlockPositionHash> knownPositions;
    knownPositions.reserve(placements.size());
    for (const CrystalPlacement& placement : placements) {
        knownPositions.insert(placement.basePosition);
    }

    for (const BlockPosition& phasedBlock : input.phaseInfo.phasedBlocks) {
        addAround(input, blocks, phasedBlock, input.antiPhaseRadius, knownPositions, placements);
        const int lowerRadius = input.antiPhaseRadius - 1 > 1 ? input.antiPhaseRadius - 1 : 1;
        addAround(input, blocks, offset(phasedBlock, 0, -1, 0), lowerRadius, knownPositions, placements);
    }

    if (input.hasSimulatedAirBlock && isNearPhase(input.simulatedAirBlock, input.phaseInfo)) {
        addAround(input, blocks, input.simulatedAirBlock, input.antiPhaseRadius, knownPositions, placements);
    }

    addAround(input, blocks, containing(input.targetPosition), 1, knownPositions, placements);
    return placements;
}

bool CrystalPlacementScanner::closestBase(
        const CrystalPlacementScanInput& input,
        CrystalPlacement& result) {
    CrystalPlacementScanInput baseScanInput = input;
    baseScanInput.hasPhaseInfo = false;
    baseScanInput.hasSimulatedAirBlock = false;
    baseScanInput.requireTwoBlockSpace = true;
    baseScanInput.ignoredEntityId = -1;
    const std::vector<CrystalPlacement> placements = scan(baseScanInput);
    if (placements.empty()) return false;

    result = placements.front();
    for (std::size_t index = 1; index < placements.size(); ++index) {
        if (placements[index].targetDistanceSquared < result.targetDistanceSquared) {
            result = placements[index];
        }
    }
    return true;
}

} }
