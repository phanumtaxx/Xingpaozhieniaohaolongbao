#include "pch.h"
#include "ManagerBridge.h"
#include "SlotSpoofManager.h"
#include "RotationManager.h"
#include "CombatActionState.h"
#include "CrystalActionController.h"
#include <cstring>

namespace xing { namespace managers {
namespace {
SlotSpoofManager slots;
RotationManager rotations;
CombatActionState activity;
autocrystal::CrystalActionController crystals;

template<typename T> T read(const std::uint8_t* input, std::size_t offset) {
    T value;
    std::memcpy(&value, input + offset, sizeof(value));
    return value;
}
template<typename T> void write(std::uint8_t* output, std::size_t offset, T value) {
    std::memcpy(output + offset, &value, sizeof(value));
}
}

int dispatch(int event, const std::uint8_t* input, std::size_t inputLength,
        std::uint8_t* output, std::size_t outputLength) {
    if (inputLength != 96 || outputLength != 176) return -2;
    const auto owner = read<std::uint64_t>(input, 0);
    const int priority = read<int>(input, 8);
    const int selectedSlot = read<int>(input, 16);
    SlotCommands commands;
    switch (static_cast<ManagerEvent>(event)) {
    case ManagerEvent::SlotBegin: commands = slots.begin(owner, priority, read<int>(input, 12), selectedSlot,
                read<int>(input, 28), read<int>(input, 20) != 0, read<int>(input, 36)); break;
    case ManagerEvent::SlotFinish: commands = slots.finish(owner, read<int>(input, 24), read<int>(input, 40) != 0, selectedSlot); break;
    case ManagerEvent::SlotTick: commands = slots.tick(selectedSlot); break;
    case ManagerEvent::SlotRelease: commands = slots.release(owner, selectedSlot); break;
    case ManagerEvent::RotationRequest: commands.accepted = rotations.requestLookAt(owner, priority,
                read<double>(input, 40), read<double>(input, 48), read<double>(input, 56), read<int>(input, 36)); break;
    case ManagerEvent::RotationTick: rotations.tick(); break;
    case ManagerEvent::RotationRelease: rotations.release(owner); break;
    case ManagerEvent::RotationRead: break;
    case ManagerEvent::RotationRequestAngles: commands.accepted = rotations.request(owner, priority,
                read<float>(input, 40), read<float>(input, 44), read<int>(input, 36)); break;
    case ManagerEvent::RotationObserve: rotations.observe(read<float>(input, 40), read<float>(input, 44)); break;
    case ManagerEvent::RotationServerRead: break;
    case ManagerEvent::Reset: slots.reset(); rotations.reset(); activity.clear(); crystals.reset(); break;
    case ManagerEvent::CrystalTick:
        crystals.tick();
        if (crystals.pendingAge() > crystals.pendingTimeout()) crystals.clearPending();
        break;
    case ManagerEvent::CrystalAttack: crystals.markAttackCooldown(read<int>(input, 12), read<int>(input, 36)); break;
    case ManagerEvent::CrystalAttackCoolingDown: commands.accepted = crystals.attackCoolingDown(read<int>(input, 12)); break;
    case ManagerEvent::CrystalReset: crystals.reset(); break;
    case ManagerEvent::CrystalPlaced:
        crystals.markPlaced({read<int>(input, 40), read<int>(input, 44), read<int>(input, 48)},
                read<int>(input, 12), read<int>(input, 36));
        break;
    case ManagerEvent::CrystalSpawned:
        crystals.markSpawned(read<int>(input, 12), {read<int>(input, 40), read<int>(input, 44), read<int>(input, 48)});
        break;
    case ManagerEvent::CrystalBroken: crystals.markBroken(); break;
    case ManagerEvent::CrystalClearPending: crystals.clearPending(); break;
    case ManagerEvent::CrystalRead: break;
    case ManagerEvent::CrystalPacketReaction: commands.accepted = crystals.beginPacketReaction(read<std::int64_t>(input, 56)); break;
    case ManagerEvent::CrystalRemoved: commands.accepted = crystals.markRemoved(read<int>(input, 12)); break;
    case ManagerEvent::CrystalReplacementReady:
        commands.accepted = crystals.replacementCrystalId(read<int>(input, 36) != 0) >= 0;
        break;
    case ManagerEvent::RotationOwnership: commands.accepted = rotations.ownedBy(owner); break;
    case ManagerEvent::SlotAcquire: commands = slots.acquire(owner, priority, read<int>(input, 36), selectedSlot); break;
    case ManagerEvent::SlotBeginSilent: commands = slots.begin(owner, priority, read<int>(input, 12), selectedSlot,
                read<int>(input, 28), true, read<int>(input, 36)); break;
    case ManagerEvent::SlotScheduleRestore: slots.scheduleRestore(owner, read<int>(input, 24)); break;
    case ManagerEvent::SlotRestoreSilent: commands = slots.restoreSilent(owner, selectedSlot); break;
    case ManagerEvent::SlotKeepAlive: slots.keepAlive(owner, selectedSlot); break;
    case ManagerEvent::SlotInspect: break;
    case ManagerEvent::SlotCommit:
        if (read<int>(input, 20) < 0 || read<int>(input, 20) > 3) return -2;
        slots.committed(owner, read<std::uint64_t>(input, 64), static_cast<SlotPacketPhase>(read<int>(input, 20)), read<int>(input, 12),
                read<int>(input, 28), read<std::int64_t>(input, 72)); break;
    case ManagerEvent::SlotFailed: slots.failed(owner, read<std::uint64_t>(input, 64)); break;
    case ManagerEvent::ActivityTick: activity.tick(); break;
    case ManagerEvent::ActivityMark:
        if (read<int>(input, 12) < 0 || read<int>(input, 12) >= static_cast<int>(CombatActivity::Count)) return -2;
        activity.mark(static_cast<CombatActivity>(read<int>(input, 12)), read<int>(input, 36)); break;
    case ManagerEvent::ActivityRead: break;
    case ManagerEvent::ActivityClear: activity.clear(); break;
    default: return -1;
    }
    write<int>(output, 0, commands.accepted ? 1 : 0);
    write<int>(output, 4, commands.restoreSlot);
    write<int>(output, 8, commands.restoreClient ? 1 : 0);
    write<int>(output, 12, commands.selectSlot);
    write<int>(output, 16, commands.selectClient ? 1 : 0);
    const bool serverRead = static_cast<ManagerEvent>(event) == ManagerEvent::RotationServerRead;
    write<float>(output, 20, serverRead ? rotations.serverYaw() : rotations.yaw());
    write<float>(output, 24, serverRead ? rotations.serverPitch() : rotations.pitch());
    write<int>(output, 28, (serverRead ? rotations.serverRotationKnown() : rotations.active()) ? 1 : 0);
    const auto& slot = slots.state();
    write(output, 32, slot.owner); write(output, 40, slot.priority); write(output, 44, slot.leaseTicks);
    write(output, 48, slot.silentOwner); write(output, 56, slot.transaction);
    write(output, 64, slot.targetSlot); write(output, 68, slot.realSlot);
    write(output, 72, static_cast<int>(slot.state)); write(output, 76, static_cast<int>(slot.lastState));
    write(output, 80, slot.swapCommit); write(output, 88, slot.actionCommit); write(output, 96, slot.restoreCommit);
    write(output, 104, commands.restoreOwner); write(output, 112, commands.restoreTransaction);
    write(output, 120, slot.age); write(output, 124, slot.restoreTicks);
    write(output, 128, activity.remaining(CombatActivity::Crystal));
    write(output, 132, activity.remaining(CombatActivity::PacketMine));
    write(output, 136, activity.remaining(CombatActivity::Generic));
    write<int>(output, 140, 2);
    write<int>(output, 144, crystals.hasPendingBase() ? 1 : 0);
    const auto base = crystals.pendingBase();
    write<int>(output, 148, base.x); write<int>(output, 152, base.y); write<int>(output, 156, base.z);
    write<int>(output, 160, crystals.pendingTargetId());
    write<int>(output, 164, crystals.pendingAge());
    write<int>(output, 168, crystals.spawnedCrystalId());
    write<int>(output, 172, crystals.deadAge());
    return 0;
}
} }
