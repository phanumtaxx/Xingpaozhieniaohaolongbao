#pragma once

#include <cstdint>

namespace xing { namespace autocrystal {

struct BlockPosition {
    int x = 0;
    int y = 0;
    int z = 0;

    bool operator==(const BlockPosition& other) const;
    bool operator!=(const BlockPosition& other) const;
};

enum class CrystalAction {
    Idle,
    PlaceSent,
    SpawnSeen,
    BreakSent
};

class CrystalActionController {
public:
    void tick();

    bool hasPendingPlacement() const;
    bool isPending(const BlockPosition& base) const;
    void markPlaced(const BlockPosition& base);
    void markPlaced(const BlockPosition& base, int targetId, int timeoutTicks = 5);
    void markSpawned(int crystalId, const BlockPosition& base);
    void markAttack(int crystalId);
    void markAttackCooldown(int crystalId, int ticks);
    bool attackCoolingDown(int crystalId) const;
    bool beginPacketReaction(std::int64_t tick);
    bool markRemoved(int crystalId);
    void markBroken();
    void clearPending();
    void reset();

    bool hasPendingBase() const;
    BlockPosition pendingBase() const;
    int pendingTargetId() const;
    int pendingAge() const;
    int pendingTimeout() const;
    CrystalAction lastAction() const;
    int expectedCycles() const;
    int spawnedCrystalId() const;
    bool hasSpawnedCrystal() const;
    bool hasSpawnedBase() const;
    BlockPosition spawnedBase() const;
    int spawnedAge() const;
    int attacks() const;
    int deadAge() const;
    bool isDeadOnTick() const;
    int replacementCrystalId(bool sameTickBreakPlace) const;

private:
    bool hasPendingBase_ = false;
    BlockPosition pendingBase_{};
    int pendingTargetId_ = -1;
    int pendingAge_ = 0;
    int pendingTimeout_ = 5;
    int expectedCycles_ = 0;
    int spawnedCrystalId_ = -1;
    bool hasSpawnedBase_ = false;
    BlockPosition spawnedBase_{};
    int spawnedAge_ = 0;
    int attacks_ = 0;
    int deadAge_ = -1;
    CrystalAction lastAction_ = CrystalAction::Idle;
    int lastAttackedCrystalId_ = -1;
    int attackCooldown_ = 0;
    bool hasPacketReactionTick_ = false;
    std::int64_t lastPacketReactionTick_ = 0;
};

} }
