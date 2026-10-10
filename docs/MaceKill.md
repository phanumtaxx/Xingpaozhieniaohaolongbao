# MaceKill (experimental)

Author: uint32.

## Starting an attempt

Enable Combat > MaceKill with a mace in the hotbar, then close the GUI. Starting on the ground requires the configured number of wind charges. Starting airborne skips charges and follows the existing flight. Disable AutoCrystal first.

Ground starts throw charges with normal physics. Later shots wait until grounded or descending within two blocks of a surface; this remains a series of boosts, not a burst. Impulses are never stored for an artificial launch. After the final boost, the module follows ascent and schedules a pause when descent begins above the configured minimum height. Airborne starts use the same height requirement.

## Native pause

The native `GameThreadPause` helper opens the Minecraft window's Windows system menu after a complete client tick. Minecraft must be windowed with a title bar. Default duration is 1500 ms, configurable from 100 to 4000 ms. A Windows timer closes the menu when the interval ends. Escape, dismissing the menu or focus loss ends the attempt early. Menu selections are returned without executing Close, Move or Resize. Rendering and client ticks stop while Windows runs the menu loop; Windows messages continue being processed. Other threads, including network threads and an integrated server, may continue running; responses requiring the client thread wait until it resumes.

On return, the render clock is reset so the pause does not become a burst of catch-up ticks. Vanilla resumes physics and applies queued server corrections and velocity updates. The module never zeroes velocity, alters server positions, or rejects corrections to manufacture a remaining ground gap. Manual attacks remain available after resuming. Optional auto attack selects a visible nearby player, excluding friends, teammates and spectators. An attack or ground contact ends the attempt; re-enable to repeat.

The pause implementation is C++ in `native/xing/GameThreadPause.cpp`. The flight sequence and Minecraft adapter still live in Java; this change is not a complete native port of MaceKill. The DLL must be reloaded by restarting Minecraft after compilation.

## Settings and diagnostics

The UI exposes Charges, Shot ticks, Min height, Pause ms, Attack range and Auto attack. The old packet interval, hold height and hold timeout no longer control the pause. Timing changes apply to the next activation.

Logs under `xingclient-mace` in `run/logs/latest.log` include shot impulses, phase changes, pause entry and elapsed duration, and server position/velocity before and after corrections. Missing or outdated DLL exports stop the attempt with a restart message rather than crashing the game.

For a manual comparison, leave MaceKill disabled, hold the mace before opening the title-bar menu, and hold it through resuming. Passive diagnostics record client tick gaps of at least 250 ms, server corrections and server velocity updates while the mace is held. The snapshots include local fall distance for comparison; it is not authoritative server fall distance. A tick gap alone does not identify its cause. These diagnostics do not change movement or packets.

This is an experiment inspired by the reported title-bar-menu stall. The earlier native wait paused the game for 1500 ms but produced no logged server correction; the player resumed falling and landed. This revision uses a Windows menu loop. Equivalence to the manual sequence's network behavior and claimed persistent mace damage has not been established. Neither a remaining ground gap nor a local fall distance confirms server-side mace damage. Multiplayer results must be checked in game.

Compile only: `gradlew.bat compileJava` and the native project's `ClCompile;_Link` targets. No tests or game checks were run for this revision. Existing smoke checks target the previous local-hold implementation.

Keep `_DISABLE_CONSTEXPR_MUTEX_CONSTRUCTOR` enabled in the native precompiled header; it avoids the previously observed incompatibility with the C++ runtime supplied by the installed JDK.
