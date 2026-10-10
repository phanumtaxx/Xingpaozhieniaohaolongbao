#include "pch.h"
#include "CombatSchedulerBridge.h"
#include "CombatActionScheduler.h"
#include <cstring>
#include <mutex>

namespace xing { namespace combatbridge {
using namespace xing::managers;
namespace {
enum class Event { Reset, Tick, Limit, Cooldown, Profile, Begin, Finish, Enqueue, Next,
    CancelOwner, Reserve, Release, PacketCommit, LatestCommit, Cancel, InspectRate, EndFrame, Activate, Invalidate,
    CanUse, Consume, TryConsume, ResetRates };
CombatActionScheduler scheduler;
std::mutex schedulerMutex;

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
    if (inputLength < 80 || outputLength != 64 || read<int>(input, 0) != 1) return -2;
    const auto afterCount = read<std::uint32_t>(input, 52);
    const auto requiresCount = read<std::uint32_t>(input, 56);
    const std::uint64_t expected = 80ULL + (static_cast<std::uint64_t>(afterCount) + requiresCount) * 8ULL;
    if (expected != inputLength) return -2;

    CombatRequest request;
    request.id = read<std::uint64_t>(input, 8);
    request.key = read<std::uint64_t>(input, 16);
    request.owner = read<std::uint64_t>(input, 24);
    request.priority = read<int>(input, 32);
    request.holdTicks = read<int>(input, 36);
    request.channel = read<int>(input, 40);
    request.resources = read<std::uint32_t>(input, 44);
    std::size_t offset = 80;
    for (std::uint32_t index = 0; index < afterCount; ++index, offset += 8) request.after.push_back(read<std::uint64_t>(input, offset));
    for (std::uint32_t index = 0; index < requiresCount; ++index, offset += 8) request.requiresSuccess.push_back(read<std::uint64_t>(input, offset));

    std::lock_guard<std::mutex> lock(schedulerMutex);
    CombatResult result{CombatStatus::Success, -1, request.id, request.owner};
    PacketCommitState commit;
    const int value = read<int>(input, 48);
    switch (static_cast<Event>(event)) {
    case Event::Reset: scheduler.reset(); break;
    case Event::Tick: scheduler.tick(); break;
    case Event::Limit: scheduler.rates().setLimit(request.channel, value); break;
    case Event::Cooldown: scheduler.rates().setCooldown(request.channel, value); break;
    case Event::Profile: scheduler.rates().setProfileLimits(value, read<int>(input, 60)); break;
    case Event::Begin: result = scheduler.begin(request); break;
    case Event::Finish:
        if (value != static_cast<int>(CombatStatus::Success) && value != static_cast<int>(CombatStatus::ActionFailed)
                && value != static_cast<int>(CombatStatus::Invalidated) && value != static_cast<int>(CombatStatus::Cancelled)) return -2;
        result = scheduler.finish(request.id, static_cast<CombatStatus>(value)); break;
    case Event::Enqueue: result = scheduler.enqueue(request); break;
    case Event::Next: result = scheduler.next(); break;
    case Event::CancelOwner: scheduler.cancelOwner(request.owner); break;
    case Event::Reserve: result = scheduler.reserve(request); break;
    case Event::Release: scheduler.release(request.owner, request.resources); break;
    case Event::PacketCommit: commit = scheduler.packetCommitted(request.owner, request.id,
                read<int>(input, 64), read<std::uint64_t>(input, 72)); break;
    case Event::LatestCommit: commit = scheduler.packets().latest(request.owner); break;
    case Event::Cancel: result = scheduler.cancel(request.id); break;
    case Event::InspectRate: break;
    case Event::EndFrame: scheduler.endFrame(); break;
    case Event::Activate: result = scheduler.activate(request.id); break;
    case Event::Invalidate: result = scheduler.invalidate(request.id); break;
    case Event::CanUse: result.status = scheduler.canUse(request.channel) ? CombatStatus::Success : CombatStatus::RateLimited; break;
    case Event::Consume: scheduler.rates().consume(request.channel, request.owner); break;
    case Event::TryConsume:
        result.status = scheduler.canUse(request.channel) ? CombatStatus::Success : CombatStatus::RateLimited;
        if (result.status == CombatStatus::Success) scheduler.rates().consume(request.channel, request.owner);
        break;
    case Event::ResetRates: scheduler.rates().reset(); break;
    default: return -1;
    }
    const auto rate = scheduler.rates().state(request.channel);
    if (static_cast<Event>(event) == Event::InspectRate) result.owner = rate.lastOwner;
    write<int>(output, 0, static_cast<int>(result.status));
    write<int>(output, 4, result.deniedResource);
    write<std::uint64_t>(output, 8, result.id);
    write<std::uint64_t>(output, 16, result.owner);
    write<std::uint64_t>(output, 24, commit.id);
    write<int>(output, 32, commit.sequence);
    write<int>(output, 36, result.nested ? 1 : 0);
    write<int>(output, 40, rate.used);
    write<int>(output, 44, rate.limit);
    write<int>(output, 48, rate.cooldown);
    write<int>(output, 52, 1);
    write<std::uint64_t>(output, 56, scheduler.epoch());
    return 0;
}

} }
