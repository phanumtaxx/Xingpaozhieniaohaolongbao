#pragma once
#include <array>
#include <string>

namespace xing { namespace flight {

// Minecraft data is supplied by the Java adapter. Author: uint32.
struct ItemSnapshot {
    bool empty = true;
    bool elytra = false;
    bool rocket = false;
    bool damageable = false;
    bool chestArmor = false;
    int damage = 0;
    int maximumDamage = 0;
    int rocketDuration = 1;
    std::string path;
};

using InventorySnapshot = std::array<ItemSnapshot, 36>;

class FlightGameAccess {
public:
    virtual ~FlightGameAccess() = default;
    virtual bool healthy() const = 0;
    virtual bool swapChest(int inventorySlot, int menuSlot) = 0;
    virtual bool startGliding() = 0;
    virtual bool useRocket(int inventorySlot, int menuSlot, bool hotbar) = 0;
    virtual void requestRotation(float yaw, float pitch) = 0;
    virtual float currentYaw() = 0;
    virtual float currentPitch() = 0;
    virtual void clearRotation() = 0;
    virtual void setVelocity(double x, double y, double z) = 0;
};

class ArmorInventoryUtil {
public:
    static int menuSlot(int inventorySlot);
    static double durabilityPercent(const ItemSnapshot& item);
    static double armorScore(const ItemSnapshot& item, bool allowElytra);
    static int bestChestplate(const InventorySnapshot& inventory);
};

class ElytraHotswapUtil {
public:
    enum class Failure { None, InvalidContext, NoUsableElytra, UnsafeInventory, InventoryBusy, SwapFailed };
    bool equip(const InventorySnapshot& inventory, const ItemSnapshot& chest, bool safe, FlightGameAccess& game);
    bool restore(bool valid, bool alive, bool safe, FlightGameAccess& game);
    bool active() const { return inventorySlot_ >= 0; }
    Failure failure() const { return failure_; }
    void reset();
    static bool usable(const ItemSnapshot& item);
    static int find(const InventorySnapshot& inventory);
private:
    int inventorySlot_ = -1;
    Failure failure_ = Failure::None;
};

class FireworkInventoryUtil {
public:
    static int find(const InventorySnapshot& inventory, bool includeInventory);
    static bool use(const InventorySnapshot& inventory, int slot, bool safe, FlightGameAccess& game);
    static double durationSeconds(const ItemSnapshot& rocket);
};
} }
