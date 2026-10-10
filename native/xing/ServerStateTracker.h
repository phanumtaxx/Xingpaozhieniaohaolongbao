#pragma once

#include <cstdint>
#include <deque>
#include <vector>

namespace xing { namespace managers {

using ItemSnapshot = std::vector<std::uint8_t>;

struct CrystalSpawn {
    int entityId = 0;
    double x = 0, y = 0, z = 0;
    std::int64_t receivedMillis = 0;
};

class ServerStateTracker {
public:
    void committed(std::int64_t commitId, int selectedSlot, const ItemSnapshot& selectedHand);
    void containerSlot(int container, int revision, int slot, const ItemSnapshot& item);
    void containerContent(int container, int revision, const std::vector<ItemSnapshot>& items);
    void refreshHands(int selectedSlot, const ItemSnapshot& mainHand, const ItemSnapshot& offHand);
    void addCrystal(CrystalSpawn spawn);
    void tick(std::int64_t now);
    void clear();

    int selectedSlot() const { return selectedSlot_; }
    int container() const { return container_; }
    int revision() const { return revision_; }
    std::int64_t lastCommit() const { return lastCommit_; }
    const ItemSnapshot& mainHand() const { return mainHand_; }
    const ItemSnapshot& offHand() const { return offHand_; }
    const std::deque<CrystalSpawn>& crystals() const { return crystals_; }

private:
    int selectedSlot_ = -1, container_ = -1, revision_ = -1;
    std::int64_t lastCommit_ = -1;
    ItemSnapshot mainHand_, offHand_;
    std::deque<CrystalSpawn> crystals_;
};

} }
