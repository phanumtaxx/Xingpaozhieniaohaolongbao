#pragma once

// Author: uint32.
namespace xing { namespace visuals {
const char* vertexShader();
const char* fragmentShader(bool sky);
const char* blurShader();
bool captures(int category, unsigned targets, double distanceSquared, double range,
              bool invisible, bool includeInvisible, bool friendPlayer, bool includeFriends);
} }
