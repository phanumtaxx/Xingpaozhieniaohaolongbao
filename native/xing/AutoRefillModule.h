#pragma once
#include <cstddef>
#include <cstdint>

namespace xing { namespace refill {
// Author: uint32.
int dispatch(int event, const std::uint8_t* input, std::size_t inputLength,
    std::uint8_t* output, std::size_t outputLength);
}
}
