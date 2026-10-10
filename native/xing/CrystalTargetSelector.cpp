#include "pch.h"
#include "CrystalTargetSelector.h"
#include <algorithm>
#include <cmath>

namespace xing { namespace autocrystal {

namespace {
double distanceSquared(const Vector3& first, const Vector3& second) {
    const double x = first.x - second.x;
    const double y = first.y - second.y;
    const double z = first.z - second.z;
    return x * x + y * y + z * z;
}

bool isLocal(const PlayerSnapshot& player, const PlayerSelectionInput& input) {
    const bool sameEntity = input.localEntityId >= 0 && player.entityId == input.localEntityId;
    const bool sameUuid = input.hasLocalUuid
            && player.uuidMostSignificantBits == input.localUuidMostSignificantBits
            && player.uuidLeastSignificantBits == input.localUuidLeastSignificantBits;
    return player.isLocalPlayer || sameEntity || sameUuid;
}

bool comesBeforeByDistance(double first, double second) {
    if (first < second) return true;
    if (first > second) return false;
    if (first == second) return std::signbit(first) && !std::signbit(second);
    return !std::isnan(first) && std::isnan(second);
}
}

std::vector<PlayerSnapshot> CrystalTargetSelector::players(const PlayerSelectionInput& input) {
    std::vector<PlayerSnapshot> selected;
    if (!input.hasPlayer || !input.hasWorld) return selected;

    for (const PlayerSnapshot& player : input.playersInWorldOrder) {
        if (isLocal(player, input)
                || player.isSpectator
                || !player.isAlive
                || (input.ignoreFriends && player.isFriend)) {
            continue;
        }
        selected.push_back(player);
    }

    std::stable_sort(selected.begin(), selected.end(), [&input](const PlayerSnapshot& first, const PlayerSnapshot& second) {
        return comesBeforeByDistance(
                distanceSquared(input.localPosition, first.position),
                distanceSquared(input.localPosition, second.position));
    });
    return selected;
}

bool CrystalTargetSelector::nearestPlayer(const PlayerSelectionInput& input, PlayerSnapshot& result) {
    std::vector<PlayerSnapshot> eligiblePlayers = players(input);
    if (eligiblePlayers.empty()) return false;
    result = eligiblePlayers.front();
    return true;
}

} }
