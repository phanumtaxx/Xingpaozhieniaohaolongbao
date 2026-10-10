# World state managers

Author: uint32

`ServerStateTracker` owns the selected server slot, container revision, serialized hand
snapshots, last packet commit ID, and recent crystal spawns in C++. Java converts
Minecraft packets and serializes items with Minecraft's item packet codec, preserving
item components. Hand queries decode independent item stacks.

The selected slot follows committed outgoing slot packets. This records what the
client sent; it does not claim the server acknowledged the slot change. Incoming
inventory packets supply hand contents and container revisions.

`WorldPredictionLedger` owns block predictions, owners, generations, deadlines, and
confirmation status in C++. Its matching rules follow Synthetic: breaks accept air
or replaceable blocks; placement expectations match block or item identity rather
than every block property. Prediction deadlines use latency multiplied by five plus
50 milliseconds, clamped to 250–1500 milliseconds.

Single and section block updates reconcile predictions before the world update
event is published. Confirmed, mismatched, and expired entries are removed on the
next manager tick. A generation-specific reconciliation cannot modify a newer
prediction for the same position. Generations remain increasing across world resets.

Both managers are registered through `ClientManagers`. World or connection changes
clear their state; disabling a module clears its predictions. Packet adapters run on
the client thread, including commit notifications forwarded from the network thread.

Prediction creation is an explicit manager API for future module ports. These
trackers do not add PacketMine behavior or alter AutoCrystal's action selection.
The reference's NewChunks, PlaceRender, and ServerStatusTracker callbacks belong to
those future ports and are not registered here.
