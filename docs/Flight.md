# Flight

Author: uint32.

Movement > Flight contains Creative, Grim, and Crashout modes. Crashout is the
default. Module decisions and movement math live in C++; Java supplies Minecraft
snapshots and executes calls through the shared inventory, network, rotation,
and action managers.

## Crashout source baseline

Compared against the local Synthetic source at revision
`dc08e191f516a013503bf69800ab990fe2d185f6`:
`FlightModule`, `ElytraHotswapUtil`, `ArmorInventoryUtil`, `SlotSpoofManager`,
and `RotationManager` under `core/src/main/java/dev/synthetic/client`.

Start airborne with a usable Elytra and fireworks in inventory. Jump manually;
Crashout does not jump automatically. Close the GUI so inventory swaps are allowed.

At the end of each client tick:

1. Finish any pending equipment restoration.
2. If gliding stopped, reset idle steering and the rocket timer, equip an Elytra, send
   the regular `START_FALL_FLYING` command, and immediately restore the chest slot.
3. Request flight rotation through the shared manager, applying it to the local
   player while keeping camera yaw and pitch independent.
4. While movement or vertical input is held and rotation was granted, use a rocket
   when its timer permits. Apply movement steering and idle behavior.

| Behavior | Current Xing behavior |
| --- | --- |
| Crashout equipment exchange | SWAP chest slot 6 with the Elytra hotbar slot, START_FALL_FLYING, then the same SWAP to restore |
| Elytra location | Prefer an existing hotbar Elytra; otherwise stage once in an unselected, non-rocket hotbar slot and return it on disable or mode change |
| Hotbar rocket | Select slot, interact, swing, immediately restore selection |
| Inventory rocket | SWAP into selected slot, interact and swing, SWAP back |
| Rocket delay | Flight duration times 0.5 plus 0.5 seconds, minus Rocket margin |
| Lost glide | Reset rocket cooldown and retry the glide within the same tick |
| Horizontal movement | Redirect speed toward control yaw, minimum 0.08, capped by Crashout Horizontal speed |
| Vertical movement | Space/Shift aim the local player up/down; preserve rocket and gravity movement during vertical input |
| Idle movement | Zero velocity and movement; configured flip-flop rotation and packet gap |

The pitch launch fallback, server-glide confirmation gate, restart pause,
experimental altitude hover, and active diagnostic sequence have been removed.
Their old config keys are ignored. Crashout now uses hotbar staging and the shared
inventory click budget to address the confirmed server click-packet limit.

Local equipment verification, resource ownership, cursor checks, and restoration
on disable remain. Cleanup can run with the ClickGUI open when the player inventory
is otherwise safe. A failed glide send also attempts equipment restoration.
Empty chest slots can be restored. These are cleanup protections; they do not
insert a delay into the successful flight sequence.

## Evidence and limitations

The old `run/logs/latest.log` from October 12 at 16:37-16:38 contains two-click
hotbar SWAP cycles, separate staging clicks, waiting states, and correction
recovery pauses. Those sequences differ from the reference and predate this update.
They cannot establish whether the new sequence is accepted by the server.

`xingclient-flight` logs now mark `path=hotbar-swap-rocket-climb`. Passive packet logs
include commit IDs, microseconds between committed actions, selected slots,
swings, and the preceding movement commit. Timestamps are captured in the network
commit callback before queued notification delivery. The observed tick is the
client tick when the log is emitted, not the server's processing tick. These
records describe client transport handoff, not server acknowledgment or wire
arrival time. Server glide flags and correction positions remain logged.

Java and the DLL must be updated together: Flight bridge version 7 uses a 56-byte
reply. No game or automated tests are run for this change; compilation alone does
not establish multiplayer consistency. Matching the module sequence does not remove
differences between Minecraft versions, protocol translation, or server anticheat builds.

## Slot parity and vertical correction fix

Flight uses the shared InventoryManager's synchronous visible-slot action. Its
native finish operation receives the slot captured before the callback and restores
that slot even if the callback changes the selection. Selection is applied before
the regular slot-change packet; Minecraft's interaction-manager cache is left alone,
matching Synthetic's visible helper. Minecraft may send its own slot synchronization
packet during interaction or on the next tick. Existing callers of withSlot retain
their previous cache behavior. Native slot ownership and resource checks still apply.

The October 12 session at 16:56:03-16:56:05 recorded 15 glide requests, 84 inventory
clicks, 15 rocket interactions, four server glide-on observations, and seven
corrections. Several corrections undid approximately one block of upward movement
while local velocity was forced to +1. The first hotbar selection was logged without
a subsequent Flight-owned restoration. This does not by itself identify why the
original restoration was absent or why the connection reset.

An earlier movement experiment removed the source's +/-1 vertical input override.
That preserved falling velocity while Space was held and did not provide explicit
climb control. The source override was restored in both the tick controller and movement hook,
then removed for rocket-driven climbing. Corrections seen during the forced climb
motivated aligning local physics and outgoing rotation. They do not establish the
specific server check or prove that this update resolves every correction.

Glide restart and rocket timing follow the source baseline, subject to the shared
click budget. A rejected inventory reservation defers the next cycle without
equipping the Elytra.

Passive logs also record slot-change packets without a Flight owner, so a vanilla
resynchronization is distinguishable from Flight's explicit restoration.

## Inventory packet limit fix

The October 12 session ended at 17:04:23 with an explicit disconnect:
`Too many packets. Limit: packets.CLICK_WINDOW (82 / 4000 ms).`
Its final attempt issued 14 glide starts, 84 inventory clicks, and 14 rocket uses
in roughly two seconds. No position corrections were recorded in that attempt.
Earlier sections with an Elytra continuously equipped are not evidence of sustained
chestplate flight.

Crashout now uses two chest SWAP clicks per restart instead of six PICKUP clicks.
The start-gliding command remains between equip and restore. Hotbar rocket selection,
interaction, swing, and selection restoration retain their previous order.

The shared native budget allows 60 automated inventory clicks per rolling four
seconds, including Crashout equipment, inventory rockets, and existing refill/swap
callers. A temporary swap reserves both outgoing and return clicks before starting.
Its return reservation stays held until used or safely released; the four-second
window begins when each reserved click is consumed. Module toggles do not reset it.
Manual clicks and callers outside this budget still contribute to server limits.

Native code chooses the Elytra and staging slot. Java's reusable inventory transaction
owns stack verification, click execution, and cleanup. Staging persists through glide
loss and landing until disable or mode change. It prefers an empty, unselected slot,
then an unselected non-rocket slot. It does not displace the selected item or rockets.
If no such slot exists, Flight waits for inventory space.

Restoration runs in reverse transaction order: chest first, then staged Elytra.
Unfinished cleanup remains in the shared manager and retries even after Flight is
disabled. Changed items are not blindly exchanged; normal Elytra durability changes
are allowed. Refills and other managed swaps cannot overwrite reserved slots.
Pending restoration blocks new Crashout cycles. No cleanup clicks are replayed into
a replacement player/world or after death. These checks verify local state, not a
server acknowledgment.

This deliberately departs from Synthetic's six-click sequence. Java and native code
were compiled; server acceptance and sustained flight still require in-game use.

## Other modes

Creative uses the Horizontal and Vertical speed settings, with optional periodic
Anti-Kick movement. Grim keeps its Elytra equipped, uses its existing steering,
and optionally renews fireworks based on attached rocket age. Both modes remain
available. The Pitch toggle is exposed as in Synthetic; its source does not use
that toggle in the control-pitch calculation.

## Airborne restart cooldown and steering

The 17:27-17:29 session on October 12 recorded 414 position corrections and no
packet-limit disconnect or inventory cleanup warning. At 17:28:59, one five-second
summary contained 36 glide starts and 36 rocket uses. The previous restart path
cleared the rocket cooldown every time local gliding stopped, so brief glide losses
could trigger another rocket immediately regardless of the configured margin.

Preserving the rocket cooldown across glide loss was tried after that run. The
17:38-17:44 session and user feedback showed a regression: many restart sequences
contained no rocket use, and climbing was ineffective. At 17:44:41, a five-second
summary recorded 36 starts but only 11 uses. A successful local item interaction
was being treated as a continuing boost even after gliding was lost.

Crashout again resets its rocket timer on glide loss, matching Synthetic. A new
successful glide start can use a replacement rocket immediately when movement or
exclusive vertical input is held. The normal duration/margin cooldown still applies
during uninterrupted gliding. The two-click equipment cycle, held cleanup reservation,
and shared click budget remain in place.

After Minecraft applies a position correction, native Crashout clears idle steering
and suspends its movement override until the next end-tick update establishes new
steering. The correction callback preserves staged equipment; a subsequent glide
restart resets the rocket timer. Normal movement
packets pass during that interval. There is no added restart delay or replacement
launch sequence. Java and native compilation cannot establish server acceptance.

## Crashout horizontal speed

Crashout has its own Horizontal speed slider, from 0.08 to 2.00 blocks per tick.
It defaults to 0.35 (about 7 blocks per second at 20 ticks per second). Creative's
Horizontal setting is separate. Existing configs acquire the default when loaded.

Native code limits the combined X/Z movement and caps accumulated X/Z velocity at
the end of the tick. It preserves direction when limiting velocity and leaves Y
unchanged. Space/Shift now direct rocket thrust through local flight pitch. The movement
limit also applies while steering is being refreshed after a correction. Lower
horizontal movement is an experiment for consistency, not confirmed server acceptance.

## Rocket-driven climbing and independent camera

Space requests local and outgoing flight pitch toward -90 degrees; Shift requests
+90 degrees, with the existing Turn speed controlling the transition. Crashout now
uses the shared client-movement rotation request before item interaction. A rejected
rotation request prevents the rocket action for that tick. Rocket decisions remain
native; Java applies Minecraft angles and interactions.

The camera and first-person hands use the saved view angles while the local player
uses the flight angles for rocket physics. Movement targeting reads the camera yaw,
so lateral inputs do not repeatedly rotate an already rotated player. The existing
camera, look, and hand mixins provide rendering and mouse-look separation.

The fixed +/-1 vertical velocity and movement override have been removed. During
Space/Shift input, Minecraft's rocket and gravity movement is retained, including
horizontal momentum subject to the configured cap. Without useful rocket thrust,
climb is not guaranteed. Ordinary horizontal steering and idle behavior remain.

A server correction retains Minecraft's corrected player angles. The camera view
survives one manager tick while Flight reacquires its rotation lease; explicit
release, disable, and world changes still restore the view normally. Movement packets
carry the shared manager's requested rotation on their normal send path. Local
angle agreement and successful compilation do not establish server acceptance.
