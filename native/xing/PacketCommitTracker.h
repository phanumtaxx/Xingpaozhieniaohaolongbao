#pragma once

#include <cstdint>
#include <unordered_map>

namespace xing { namespace managers {

struct PacketCommitState { std::uint64_t id = 0; int sequence = -1; };

class PacketCommitTracker {
public:
    PacketCommitState commit(std::uint64_t owner, int sequence) {
        PacketCommitState result{++nextId_, sequence};
        if (owner != 0) {
            auto& latest = owners_[owner];
            latest.id = result.id;
            if (sequence >= 0) latest.sequence = sequence;
        }
        return result;
    }
    PacketCommitState latest(std::uint64_t owner) const {
        const auto found = owners_.find(owner);
        return found == owners_.end() ? PacketCommitState{} : found->second;
    }
    void reset() { owners_.clear(); }

private:
    std::uint64_t nextId_ = 0;
    std::unordered_map<std::uint64_t, PacketCommitState> owners_;
};

} }
