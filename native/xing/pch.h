// pch.h: This is a precompiled header file.
// Files listed below are compiled only once, improving build performance for future builds.
// This also affects IntelliSense performance, including code completion and many code browsing features.
// However, files listed here are ALL re-compiled if any one of them is updated between builds.
// Do not add files here that you will be updating frequently as this negates the performance advantage.

#ifndef PCH_H
#define PCH_H

// JDK 21 can preload MSVC 14.36. Use Microsoft's compatibility constructor
// for std::mutex instead of the newer constexpr ABI (VS 2022 17.10+).
#define _DISABLE_CONSTEXPR_MUTEX_CONSTRUCTOR

// add headers that you want to pre-compile here
#include "framework.h"

#endif //PCH_H
