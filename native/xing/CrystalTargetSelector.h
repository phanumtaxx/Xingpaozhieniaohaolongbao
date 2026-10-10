#pragma once

#include "CrystalPrediction.h"
#include <cstdint>
#include <vector>

namespace xing { namespace autocrystal {

struct PlayerSnapshot {
    int entityId = -1;
    std::uint64_t uuidMostSignificantBits = 0;
    std::uint64_t uuidLeastSignificantBits = 0;
    Vector3 position{};
    bool isLocalPlayer = false;
    bool isSpectator = false;
    bool isAlive = false;
    bool isFriend = false;
};

struct PlayerSelectionInput {
    bool hasPlayer = false;
    bool hasWorld = false;
    int localEntityId = -1;
    bool hasLocalUuid = false;
    std::uint64_t localUuidMostSignificantBits = 0;
    std::uint64_t localUuidLeastSignificantBits = 0;
    Vector3 localPosition{};
    bool ignoreFriends = false;
    std::vector<PlayerSnapshot> playersInWorldOrder;
};

class CrystalTargetSelector {
public:
    static std::vector<PlayerSnapshot> players(const PlayerSelectionInput& input);
    static bool nearestPlayer(const PlayerSelectionInput& input, PlayerSnapshot& result);
};

} }
