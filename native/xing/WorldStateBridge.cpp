#include "pch.h"
#include "WorldStateBridge.h"
#include "ServerStateTracker.h"
#include "WorldPredictionLedger.h"
#include <cstring>
#include <mutex>
#include <limits>

namespace xing { namespace worldstatebridge {
using namespace xing::managers;
namespace {
constexpr std::size_t HeaderBytes = 160;
enum class Event { Reset, Tick, Commit, ContainerSlot, ContainerContent, RefreshHands, CrystalSpawn,
    Read, ReadHand, ReadCrystal, Predict, GetPrediction, Reconcile, ClearOwner, ClearPosition, ClearPredictions };
ServerStateTracker server;
WorldPredictionLedger predictions;
std::mutex stateMutex;

template<typename T> T read(const std::uint8_t* bytes, std::size_t offset) {
    T value;
    std::memcpy(&value, bytes + offset, sizeof(value));
    return value;
}
template<typename T> void write(std::uint8_t* bytes, std::size_t offset, T value) {
    std::memcpy(bytes + offset, &value, sizeof(value));
}

void writePrediction(std::uint8_t* output, const BlockPrediction& prediction) {
    write(output, 40, prediction.generation);
    write(output, 48, prediction.position);
    write(output, 56, prediction.owner);
    write(output, 64, prediction.createdMillis);
    write(output, 72, prediction.deadlineMillis);
    write(output, 80, static_cast<int>(prediction.kind));
    write(output, 84, static_cast<int>(prediction.expectedType));
    write(output, 88, prediction.expectedId);
    write(output, 92, static_cast<int>(prediction.status));
    write(output, 96, prediction.expectedState);
}
}

int dispatch(int event, const std::uint8_t* input, std::size_t inputLength,
        std::uint8_t* output, std::size_t outputLength) {
    if (inputLength < HeaderBytes || outputLength < HeaderBytes || read<int>(input, 0) != 1) return -2;
    const auto firstSize = read<std::uint32_t>(input, 24);
    const auto secondSize = read<std::uint32_t>(input, 28);
    if (HeaderBytes + static_cast<std::uint64_t>(firstSize) + secondSize != inputLength) return -2;
    const auto* payload = input + HeaderBytes;
    const auto now = read<std::int64_t>(input, 64);
    const auto position = read<std::int64_t>(input, 48);
    std::lock_guard<std::mutex> lock(stateMutex);
    std::memset(output, 0, outputLength);
    write<int>(output, 156, 1);
    const ItemSnapshot* hand = nullptr;
    switch (static_cast<Event>(event)) {
    case Event::Reset: server.clear(); predictions.clear(); break;
    case Event::Tick: server.tick(now); predictions.tick(now); break;
    case Event::Commit:
        server.committed(read<std::int64_t>(input, 16), read<int>(input, 4), ItemSnapshot(payload, payload + firstSize)); break;
    case Event::ContainerSlot:
        server.containerSlot(read<int>(input, 8), read<int>(input, 12), read<int>(input, 4), ItemSnapshot(payload, payload + firstSize)); break;
    case Event::ContainerContent: {
        std::vector<ItemSnapshot> items;
        std::size_t offset = 0;
        while (offset < firstSize) {
            if (firstSize - offset < sizeof(std::uint32_t)) return -2;
            const auto length = read<std::uint32_t>(payload, offset);
            offset += sizeof(std::uint32_t);
            if (length > firstSize - offset) return -2;
            items.emplace_back(payload + offset, payload + offset + length);
            offset += length;
        }
        server.containerContent(read<int>(input, 8), read<int>(input, 12), items);
        break;
    }
    case Event::RefreshHands:
        server.refreshHands(read<int>(input, 4), ItemSnapshot(payload, payload + firstSize),
            ItemSnapshot(payload + firstSize, payload + firstSize + secondSize)); break;
    case Event::CrystalSpawn:
        server.addCrystal({read<int>(input, 100), read<double>(input, 112), read<double>(input, 120),
            read<double>(input, 128), now}); break;
    case Event::Read: break;
    case Event::ReadHand: hand = read<int>(input, 4) == 0 ? &server.mainHand() : &server.offHand(); break;
    case Event::ReadCrystal: {
        const int index = read<int>(input, 4);
        if (index < 0 || static_cast<std::size_t>(index) >= server.crystals().size()) break;
        const auto& spawn = server.crystals()[index];
        write<int>(output, 0, 1);
        write(output, 100, spawn.entityId);
        write(output, 104, spawn.receivedMillis);
        write(output, 112, spawn.x); write(output, 120, spawn.y); write(output, 128, spawn.z);
        break;
    }
    case Event::Predict: {
        const int kind = read<int>(input, 80), type = read<int>(input, 84);
        if (kind < 0 || kind > 2 || type < 0 || type > 4) return -2;
        BlockPrediction prediction;
        prediction.position = position;
        prediction.owner = read<std::uint64_t>(input, 56);
        prediction.kind = static_cast<PredictionKind>(kind);
        prediction.expectedType = static_cast<ExpectedBlockType>(type);
        prediction.expectedId = read<int>(input, 88);
        prediction.expectedState = read<int>(input, 96);
        writePrediction(output, predictions.predict(prediction, read<int>(input, 4), now));
        write<int>(output, 0, 1);
        break;
    }
    case Event::GetPrediction:
        if (const auto* prediction = predictions.get(position)) {
            writePrediction(output, *prediction);
            write<int>(output, 0, predictions.pending(position, now) ? 2 : 1);
        }
        break;
    case Event::Reconcile:
        write<int>(output, 0, predictions.reconcile(position, read<std::uint64_t>(input, 40),
            read<int>(input, 88), read<int>(input, 96), read<int>(input, 80) != 0,
            read<int>(input, 84) != 0, read<int>(input, 92) != 0) ? 1 : 0); break;
    case Event::ClearOwner: predictions.clearOwner(read<std::uint64_t>(input, 56)); break;
    case Event::ClearPosition: predictions.clearPosition(position); break;
    case Event::ClearPredictions: predictions.clear(); break;
    default: return -1;
    }
    write(output, 4, server.selectedSlot()); write(output, 8, server.container()); write(output, 12, server.revision());
    write(output, 16, server.lastCommit());
    write(output, 24, static_cast<int>(server.mainHand().size()));
    write(output, 28, static_cast<int>(server.offHand().size()));
    write(output, 32, static_cast<int>(server.crystals().size()));
    if (hand) {
        if (hand->size() > (std::numeric_limits<int>::max)() - HeaderBytes) return -2;
        write(output, 36, static_cast<int>(HeaderBytes + hand->size()));
        if (outputLength < HeaderBytes + hand->size()) return -3;
        if (!hand->empty()) std::memcpy(output + HeaderBytes, hand->data(), hand->size());
    }
    return 0;
}
} }
