# Slot transactions and combat activity

Author: uint32

`CombatActionState` stores the crystal, PacketMine, and generic combat activity
windows in C++. Marks extend an existing window without shortening it. The shared
manager decrements these windows once at the start of a client tick, before queued
packet notifications are delivered. Successful AutoCrystal placements and breaks
mark the reference's two-tick crystal window. World and connection resets clear it.

`SlotSpoofManager` owns slot priority, finite or indefinite leases, visible restores,
and silent transaction state in C++. `InventoryManager` converts its commands to
Minecraft slot packets. Its owner map only resolves native owner IDs back to Java
packet owners, and removes references when native ownership ends.

Reusable APIs cover lease acquisition, beginning a held silent swap, keeping it
alive, scheduling a restore, restoring immediately, releasing an owner, and querying
transaction state and action commits. The existing `withSlot` adapter uses the same
native manager for temporary visible or silent swaps.

Silent transactions follow swap, action, and restore commits. They remain owned
while a restore awaits its commit, with the reference's 40-tick inactivity timeout.
An indefinite lease is not decremented. Keep-alive and matching packet commits reset
the transaction age. Completing a transaction clears its active commit IDs and
retains its last terminal state, as in Synthetic.

Each queued packet captures its transaction generation and phase. Old packets cannot
advance a newer transaction, and a swap cannot be mistaken for a restore when both
select the same slot. Transaction generations remain increasing across resets.
Commit means outgoing dispatch, not server acknowledgement of the mining action.

Higher-priority handoffs restore an existing silent swap before granting the next
request. When that restore is asynchronous, the caller retries after its commit.
This avoids overlapping silent transactions; Synthetic can replace the active
transaction before that restore commits. Manual hotbar changes take precedence over
pending restores. Send failures clear the affected ownership and scheduler resource.

PacketMine will use these APIs to hold its tool and choose when to restore it after
block confirmation or its mining timeout. That mining policy belongs in
`PacketMineManager`; it is not implemented by the slot transaction manager.
