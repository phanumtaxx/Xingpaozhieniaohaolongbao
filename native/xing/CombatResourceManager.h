#pragma once

#include <array>
#include <cstdint>

namespace xing { namespace managers {

enum class ActionResource { MainHand, OffHand, Inventory, Rotation, BlockPlacePacket, ItemUsePacket, AttackPacket, MinePacket, Count };

class CombatResourceManager {
public:
    int denied(std::uint64_t owner, int priority, std::uint32_t resources) const;
    bool acquire(std::uint64_t owner, int priority, int holdTicks, std::uint32_t resources);
    void commit(std::uint64_t owner, std::uint32_t resources);
    void release(std::uint64_t owner, std::uint32_t resources);
    void tick();
    void reset();
    static bool validMask(std::uint32_t resources) { return (resources & ~0xffU) == 0; }

private:
    struct Lease { std::uint64_t owner = 0; int priority = 0; int ticks = 0; };
    std::array<Lease, static_cast<std::size_t>(ActionResource::Count)> leases_{};
    std::array<std::uint64_t, static_cast<std::size_t>(ActionResource::Count)> committed_{};
};

} }
