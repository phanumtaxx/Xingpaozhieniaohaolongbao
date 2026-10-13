# SpeedMine

Author: uint32.

Enable **Combat > SpeedMine**, then click a block once. Mining continues after releasing the mouse. Click another block to replace the target; disable the module to cancel it.

The native PacketMineManager selects the best hotbar tool and calculates progress using hardness, tool suitability, Efficiency, Haste, Mining Fatigue, mining attributes, water and ground state. Java adapts Minecraft snapshots and executes requests through the shared network, inventory, rotation and combat scheduler managers.

START and STOP packets use prediction sequences. Mining begins after START commits, and completion waits for the world to report air. The tool lease is restored on completion or cancellation. Missing confirmation causes a bounded ABORT/restart retry. No client-side air replacement is used.

Settings include range, completion threshold, silent/visible/no tool swap, multitasking, strict face visibility, rotation, swing, confirmation timeout and retry count. Threshold 1 is the default; lower thresholds can cause servers to reject early STOP packets.

Break ESP consumes native progress, and AutoCrystal receives the same target through MiningSyncState. This implementation has one active target; Synthetic's dual/triple mining lanes and TPS synchronization are not included.

Compilation checks cover Java and the native DLL only. In-game and server behavior still need to be checked.
