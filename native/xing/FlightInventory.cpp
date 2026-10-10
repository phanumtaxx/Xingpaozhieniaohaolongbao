#include "pch.h"
#include "FlightInventory.h"
#include <limits>

namespace xing { namespace flight {
int ArmorInventoryUtil::menuSlot(int slot) { return slot < 9 ? slot + 36 : slot; }

double ArmorInventoryUtil::durabilityPercent(const ItemSnapshot& item) {
    if (!item.damageable || item.maximumDamage <= 0) return 100.0;
    return (item.maximumDamage - item.damage) * 100.0 / item.maximumDamage;
}

double ArmorInventoryUtil::armorScore(const ItemSnapshot& item, bool allowElytra) {
    if (item.empty) return -std::numeric_limits<double>::infinity();
    const auto& path = item.path;
    const auto contains = [&path](const char* material) { return path.find(material) != std::string::npos; };
    const double material = contains("netherite") ? 600.0
        : contains("diamond") ? 500.0 : path == "turtle_helmet" ? 450.0
        : contains("iron") ? 400.0 : contains("chainmail") ? 350.0
        : contains("copper") ? 325.0 : contains("golden") ? 300.0
        : contains("leather") ? 200.0 : path == "elytra" && allowElytra ? 425.0 : 100.0;
    return material + durabilityPercent(item) * 0.5;
}

int ArmorInventoryUtil::bestChestplate(const InventorySnapshot& inventory) {
    int best = -1;
    double bestScore = -std::numeric_limits<double>::infinity();
    for (int slot = 0; slot < 36; ++slot) {
        const auto& item = inventory[slot];
        if (item.empty || !item.chestArmor || item.path == "elytra" || durabilityPercent(item) < 0.0) continue;
        const auto score = armorScore(item, false);
        if (score > bestScore) { best = slot; bestScore = score; }
    }
    return best;
}

bool ElytraHotswapUtil::usable(const ItemSnapshot& item) {
    return item.elytra && (!item.damageable || item.damage < item.maximumDamage - 1);
}

int ElytraHotswapUtil::find(const InventorySnapshot& inventory) {
    for (int slot = 0; slot < 36; ++slot) if (usable(inventory[slot])) return slot;
    return -1;
}

bool ElytraHotswapUtil::equip(const InventorySnapshot& inventory, const ItemSnapshot& chest,
        bool safe, FlightGameAccess& game) {
    failure_ = Failure::None;
    if (usable(chest)) return true;
    if (active()) { failure_ = Failure::SwapFailed; return false; }
    const int candidate = find(inventory);
    if (candidate < 0) { failure_ = Failure::NoUsableElytra; return false; }
    if (!safe) { failure_ = Failure::UnsafeInventory; return false; }
    if (!game.swapChest(candidate, ArmorInventoryUtil::menuSlot(candidate))) {
        failure_ = Failure::SwapFailed;
        return false;
    }
    inventorySlot_ = candidate;
    return true;
}

bool ElytraHotswapUtil::restore(bool valid, bool alive, bool safe, FlightGameAccess& game) {
    failure_ = Failure::None;
    if (!active()) return true;
    if (!valid || !alive) { reset(); return true; }
    if (!safe) { failure_ = Failure::UnsafeInventory; return false; }
    if (!game.swapChest(inventorySlot_, ArmorInventoryUtil::menuSlot(inventorySlot_))) {
        failure_ = Failure::SwapFailed;
        return false;
    }
    inventorySlot_ = -1;
    return true;
}

void ElytraHotswapUtil::reset() { inventorySlot_ = -1; failure_ = Failure::None; }

int FireworkInventoryUtil::find(const InventorySnapshot& inventory, bool includeInventory) {
    const int limit = includeInventory ? 36 : 9;
    for (int slot = 0; slot < limit; ++slot) if (inventory[slot].rocket) return slot;
    return -1;
}

bool FireworkInventoryUtil::use(const InventorySnapshot& inventory, int slot, bool safe, FlightGameAccess& game) {
    if (slot < 0 || slot >= 36 || !inventory[slot].rocket || (slot >= 9 && !safe)) return false;
    return game.useRocket(slot, ArmorInventoryUtil::menuSlot(slot), slot < 9);
}

double FireworkInventoryUtil::durationSeconds(const ItemSnapshot& rocket) {
    return rocket.rocketDuration * 0.5 + 0.5;
}
} }
