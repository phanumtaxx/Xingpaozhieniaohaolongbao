#include "pch.h"
#include "ServerStateTracker.h"
#include <algorithm>

namespace xing { namespace managers {

void ServerStateTracker::committed(std::int64_t commitId, int selectedSlot, const ItemSnapshot& selectedHand) {
    lastCommit_ = (std::max)(lastCommit_, commitId);
    if (selectedSlot >= 0 && selectedSlot < 9) {
        selectedSlot_ = selectedSlot;
        mainHand_ = selectedHand;
    }
}

void ServerStateTracker::containerSlot(int container, int revision, int slot, const ItemSnapshot& item) {
    container_ = container;
    revision_ = revision;
    if (container != 0) return;
    if (slot == 45) offHand_ = item;
    if (slot == 36 + selectedSlot_) mainHand_ = item;
}

void ServerStateTracker::containerContent(int container, int revision, const std::vector<ItemSnapshot>& items) {
    container_ = container;
    revision_ = revision;
    if (container != 0) return;
    const auto mainSlot = static_cast<std::size_t>(36 + (std::max)(0, selectedSlot_));
    if (mainSlot < items.size()) mainHand_ = items[mainSlot];
    if (items.size() > 45) offHand_ = items[45];
}

void ServerStateTracker::refreshHands(int selectedSlot, const ItemSnapshot& mainHand, const ItemSnapshot& offHand) {
    if (revision_ >= 0) return;
    if (selectedSlot_ < 0) selectedSlot_ = selectedSlot;
    mainHand_ = mainHand;
    offHand_ = offHand;
}

void ServerStateTracker::addCrystal(CrystalSpawn spawn) {
    crystals_.push_back(spawn);
    while (crystals_.size() > 64) crystals_.pop_front();
}

void ServerStateTracker::tick(std::int64_t now) {
    while (!crystals_.empty() && crystals_.front().receivedMillis < now - 2000) crystals_.pop_front();
}

void ServerStateTracker::clear() {
    selectedSlot_ = container_ = revision_ = -1;
    lastCommit_ = -1;
    mainHand_.clear();
    offHand_.clear();
    crystals_.clear();
}

} }
