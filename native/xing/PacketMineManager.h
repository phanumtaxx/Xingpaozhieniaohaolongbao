#pragma once
#include <array>

namespace xing { namespace managers {
struct MiningTool {
    float speed;
    int efficiency;
    bool suitable;
};
struct MiningFrame {
    float hardness, blockSpeed, submergedSpeed, tickScale, threshold;
    int haste, fatigue, selectedSlot, heldSlot, confirmationTicks, maximumRetries;
    bool requiresTool, onGround, submerged, paused, valid, air;
};

// Author: uint32.
class PacketMineManager {
public:
    enum class Stage { Ready, StartCommit, Mining, StopCommit, Confirming, Retry, Finished, Failed };
    enum class Command { None, Start, Stop, Abort, Finish, Fail };
    struct Result { Command command; Stage stage; int slot; float progress; int retries; };
    void reset();
    Result tick(const MiningFrame& frame, const std::array<MiningTool, 9>& tools);
    void sent(Command command, bool success);
    void committed(Command command);
private:
    static float damage(const MiningFrame& frame, const MiningTool& tool);
    Stage stage_ = Stage::Ready;
    float progress_ = 0;
    int slot_ = -1, age_ = 0, retries_ = 0;
};
} }
