#include "pch.h"
#include "CrystalActionController.h"

namespace xing { namespace autocrystal {

bool BlockPosition::operator==(const BlockPosition& other) const {
    return x == other.x && y == other.y && z == other.z;
}

bool BlockPosition::operator!=(const BlockPosition& other) const {
    return !(*this == other);
}

void CrystalActionController::tick() {
    if (hasPendingBase_) ++pendingAge_;
    if (spawnedCrystalId_ >= 0) ++spawnedAge_;
    if (deadAge_ >= 0) ++deadAge_;
}

bool CrystalActionController::hasPendingPlacement() const {
    return hasPendingBase_;
}

bool CrystalActionController::isPending(const BlockPosition& base) const {
    return hasPendingBase_ && pendingBase_ == base;
}

void CrystalActionController::markPlaced(const BlockPosition& base) {
    markPlaced(base, -1);
}

void CrystalActionController::markPlaced(const BlockPosition& base, int targetId) {
    pendingBase_ = base;
    hasPendingBase_ = true;
    pendingTargetId_ = targetId;
    pendingAge_ = 0;
    ++expectedCycles_;
    spawnedCrystalId_ = -1;
    hasSpawnedBase_ = false;
    spawnedBase_ = {};
    spawnedAge_ = 0;
    attacks_ = 0;
    deadAge_ = -1;
    lastAction_ = CrystalAction::PlaceSent;
}

void CrystalActionController::markSpawned(int crystalId, const BlockPosition& base) {
    if (hasPendingBase_ && pendingBase_ != base) return;

    spawnedCrystalId_ = crystalId;
    spawnedBase_ = base;
    hasSpawnedBase_ = true;
    spawnedAge_ = 0;
    deadAge_ = -1;
    lastAction_ = CrystalAction::SpawnSeen;
}

void CrystalActionController::markAttack(int crystalId) {
    ++attacks_;
    if (spawnedCrystalId_ != crystalId) {
        spawnedCrystalId_ = crystalId;
        spawnedBase_ = pendingBase_;
        hasSpawnedBase_ = hasPendingBase_;
        spawnedAge_ = 0;
    }
    lastAction_ = CrystalAction::BreakSent;
}

void CrystalActionController::markBroken() {
    clearPending();
    deadAge_ = 0;
    lastAction_ = CrystalAction::BreakSent;
}

void CrystalActionController::clearPending() {
    hasPendingBase_ = false;
    pendingBase_ = {};
    pendingTargetId_ = -1;
    pendingAge_ = 0;
}

void CrystalActionController::reset() {
    hasPendingBase_ = false;
    pendingBase_ = {};
    pendingTargetId_ = -1;
    pendingAge_ = 0;
    expectedCycles_ = 0;
    spawnedCrystalId_ = -1;
    hasSpawnedBase_ = false;
    spawnedBase_ = {};
    spawnedAge_ = 0;
    attacks_ = 0;
    deadAge_ = -1;
    lastAction_ = CrystalAction::Idle;
}

bool CrystalActionController::hasPendingBase() const {
    return hasPendingBase_;
}

BlockPosition CrystalActionController::pendingBase() const {
    return pendingBase_;
}

int CrystalActionController::pendingTargetId() const {
    return pendingTargetId_;
}

int CrystalActionController::pendingAge() const {
    return pendingAge_;
}

CrystalAction CrystalActionController::lastAction() const {
    return lastAction_;
}

int CrystalActionController::expectedCycles() const {
    return expectedCycles_;
}

int CrystalActionController::spawnedCrystalId() const {
    return spawnedCrystalId_;
}

bool CrystalActionController::hasSpawnedCrystal() const {
    return spawnedCrystalId_ >= 0;
}

bool CrystalActionController::hasSpawnedBase() const {
    return hasSpawnedBase_;
}

BlockPosition CrystalActionController::spawnedBase() const {
    return spawnedBase_;
}

int CrystalActionController::spawnedAge() const {
    return spawnedAge_;
}

int CrystalActionController::attacks() const {
    return attacks_;
}

int CrystalActionController::deadAge() const {
    return deadAge_;
}

bool CrystalActionController::isDeadOnTick() const {
    return deadAge_ == 0;
}

} }
