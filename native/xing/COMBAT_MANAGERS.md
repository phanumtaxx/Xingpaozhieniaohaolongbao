# Shared combat managers

Author: uint32.

The scheduling rules live in C++. Java keeps the callbacks that call Minecraft and reports their results. Modules reuse the managers through `XingClient.INSTANCE.managers`.

## Execution

`scheduler.execute` runs immediately if native resource ownership and rate limits allow it. A successful action keeps its leases for the requested hold time. A failed action releases its leases unless a packet was already committed.

`scheduler.submit` collects actions during the client tick. After END tick listeners finish, native code chooses actions by descending priority, with submission order breaking ties. Completion callbacks receive the actual outcome. Submission acceptance does not mean the action has run.

An `ActionKey` names a queued action. `IntentOptions.after` waits for another submitted key to finish. `requiring` also requires its success. Missing keys are ignored, matching Synthetic. Dependency cycles invalidate the remaining actions. `onlyWhen` checks Java world state before native acquisition and execution.

Submissions outside collection execute immediately, keeping packet reactions responsive. Nested calls by the executing owner share the outer reservation and budget. The outer action must declare all the resources its nested calls need.

## Budgets and ownership

The default per-tick limits are two block placements, eight item uses, one ordinary attack, one mining action, and an unrestricted generic channel. Per-tick overrides reset at the next tick. Profile limits and configured cooldowns persist until changed or reset.

Immediate AutoCrystal breaks follow Synthetic's resource-only path through `attackImmediate`; they do not consume the ordinary attack budget. Block placement uses the shared placement budget. Inventory and rotation managers also participate in native resource ownership.

Higher priority can take an uncommitted lease from another owner. Once an action commits a resource in a tick, another owner cannot take it during that tick. Every module inherits a persistent `actionOwner`; use it for its interactions and queued callbacks.

## Packet commits and cleanup

The connection mixin reports a packet after `sendImmediately` returns, matching Synthetic's dispatch boundary. This reports acceptance for outgoing dispatch, not socket-write success or server acceptance. Packet ownership survives rotation packet replacement. Native code assigns commit IDs and tracks the latest commit and prediction sequence by owner.

Packets from network threads are reported through a separate JNI buffer and a native mutex. Java event notifications are delivered on the client thread. Session generations discard late reports from an old connection or a server correction.

Module disable cancels its queued callbacks and releases its slot, rotation, and resource ownership. Disconnects, world changes, and server corrections clear scheduling state. Cancellation and exception paths finish callbacks and release uncommitted resources.

The anticheat profile manager has not been ported; its future adapter can supply profile limits through `managers.rates.setProfileLimits`.
