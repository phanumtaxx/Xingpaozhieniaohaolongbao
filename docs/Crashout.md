# Crashout

Author: uint32.

This ports the Crashout mode from Synthetic's Flight module. It is registered
under Movement as Crashout; Creative, Grim, and Elytra Fly are separate modes
and are not included in this module.

Have a usable Elytra and fireworks in your inventory. Start airborne, then
enable Crashout. It temporarily equips the Elytra to start gliding and restores
the chest slot. Direction keys steer; jump and sneak control vertical movement.

The defaults match Synthetic: inventory fireworks enabled, 0.2 seconds of
rocket safety margin, 25 degrees of turning per tick, Full idle flip-flop,
20 ticks between idle packets when flip-flop is inactive, hidden glide pose,
and chestplate render spoofing disabled.

Flight decisions, movement math, rocket timing, inventory selection, armor
scoring, and Elytra swap state live in C++. Java supplies Minecraft snapshots,
executes game operations, and connects settings and mixins. Shared native
inventory, rotation, and resource managers coordinate the operations.

The original rocket duration estimate and idle behavior are preserved. This
port does not establish compatibility with a particular multiplayer server.
The original swap utility also requires a nonempty source stack when restoring
the chest slot; start with a chestplate equipped if you want it restored.
