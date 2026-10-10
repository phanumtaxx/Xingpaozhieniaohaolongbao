#pragma once

#include <cstddef>
#include <cstdint>

namespace xing { namespace managers {
enum class ManagerEvent {
    SlotBegin, SlotFinish, SlotTick, SlotRelease,
    RotationRequest, RotationTick, RotationRelease, RotationRead, Reset, RotationOwnership,
    SlotAcquire, SlotBeginSilent, SlotScheduleRestore, SlotRestoreSilent, SlotKeepAlive,
    SlotInspect, SlotCommit, SlotFailed, ActivityTick, ActivityMark, ActivityRead, ActivityClear,
    RotationRequestAngles, RotationObserve, RotationServerRead,
    CrystalTick, CrystalAttack, CrystalAttackCoolingDown, CrystalReset,
    CrystalPlaced, CrystalSpawned, CrystalBroken, CrystalClearPending, CrystalRead, CrystalPacketReaction, CrystalRemoved,
    CrystalReplacementReady
};
int dispatch(int event, const std::uint8_t* input, std::size_t inputLength,
        std::uint8_t* output, std::size_t outputLength);
} }
