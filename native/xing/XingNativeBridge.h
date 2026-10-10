#pragma once

#include <cstddef>
#include <cstdint>

namespace xing {
using AuthorId = std::uint32_t;
static_assert(sizeof(AuthorId) == 4, "AuthorId must remain a 32-bit value");

namespace autocrystalpolicy {
    int dispatch(int event, const std::uint8_t* input, std::size_t inputLen, std::uint8_t* output, std::size_t outputLen);
}
namespace autocrystalbreakpolicy {
    int dispatch(int event, const std::uint8_t* input, std::size_t inputLen, std::uint8_t* output, std::size_t outputLen);
}
namespace autocrystalplacelifecycle {
    int dispatch(int event, const std::uint8_t* input, std::size_t inputLen, std::uint8_t* output, std::size_t outputLen);
}
namespace autocrystalbreaklifecycle {
    int dispatch(int event, const std::uint8_t* input, std::size_t inputLen, std::uint8_t* output, std::size_t outputLen);
}
namespace autocrystaldirectpreplacelifecycle {
    int dispatch(int event, const std::uint8_t* input, std::size_t inputLen, std::uint8_t* output, std::size_t outputLen);
}
namespace autocrystalexecutionpolicy {
    int dispatch(int event, const std::uint8_t* input, std::size_t inputLen, std::uint8_t* output, std::size_t outputLen);
}
namespace autocrystalcyclebridge {
    int dispatch(int event, const std::uint8_t* input, std::size_t inputLen, std::uint8_t* output, std::size_t outputLen);
}
}
