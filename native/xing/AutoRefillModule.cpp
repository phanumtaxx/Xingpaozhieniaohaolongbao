#include "pch.h"
#include "AutoRefillModule.h"
#include <algorithm>
#include <cstring>

namespace xing { namespace refill {
namespace {
int cooldown = 0;
int read(const std::uint8_t* input, std::size_t offset) {
    int value;
    std::memcpy(&value, input + offset, sizeof(value));
    return value;
}
}

int dispatch(int event, const std::uint8_t* input, std::size_t inputLength,
    std::uint8_t* output, std::size_t outputLength) {
    if (inputLength != 160 || outputLength < 8 || event < 0 || event > 2) return -1;
    int source = -1;
    int target = -1;
    if (event == 2) cooldown = 0;
    else if (event == 1) cooldown = (std::max)(0, (std::min)(40, read(input, 4)));
    else if (cooldown > 0) --cooldown;
    else if (read(input, 0) != 0) {
        const int threshold = (std::max)(1, (std::min)(63, read(input, 8)));
        const unsigned enabledItems = static_cast<unsigned>(read(input, 12));
        for (int slot = 0; slot < 9; ++slot) {
            const auto offset = 16 + slot * 16;
            const int count = read(input, offset);
            const int maximum = read(input, offset + 4);
            const int item = read(input, offset + 8);
            if (count <= 0 || count > threshold || count >= maximum || item < 0 || item >= 6
                || (enabledItems & (1u << item)) == 0) continue;
            const unsigned matches = static_cast<unsigned>(read(input, offset + 12));
            for (int inventorySlot = 9; inventorySlot < 36; ++inventorySlot) {
                if ((matches & (1u << (inventorySlot - 9))) == 0) continue;
                source = inventorySlot;
                target = 36 + slot;
                break;
            }
            if (source >= 0) break;
        }
    }
    std::memcpy(output, &source, sizeof(source));
    std::memcpy(output + 4, &target, sizeof(target));
    return 0;
}
}
}
