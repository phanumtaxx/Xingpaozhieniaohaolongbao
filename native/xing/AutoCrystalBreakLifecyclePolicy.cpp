#include "pch.h"
#include "XingNativeBridge.h"

#include <cstdint>
#include <cstring>
#include <mutex>

// Native lifecycle tracker for AutoCrystal's break lifecycle (see
// AutoCrystalBreakLifecycleShadowEngine.java). No world/entity access, no packets, no rotation, no
// scheduler submission - but EVT_CANDIDATE_SELECTED's lane-coalescing arbitration below IS
// authoritative: Java must obey the returned action, not just log it. Every other event here
// remains pure state tracking for Java-side comparison logging.
//
// One engine, two bounded lanes: LANE_ORDINARY (runCrystalCycle's ranked candidate) and
// LANE_SPAWN_REACTIVE (CrystalSpawnProcessor's packet-triggered immediate break). Each lane has its
// own independent sessionId/generation/commandId sequence and is addressed by the input's lane
// field. Both lanes live in g_lanes[LANE_COUNT] under one mutex, which is what makes this module -
// not Java - the natural place to decide the one piece of cross-lane state: EVT_CANDIDATE_SELECTED
// checks whether the *other* lane already holds a live (non-terminal) claim on the same entityId,
// and if so, coalesces this lane straight into STATE_COALESCED and returns ACTION_DENY_COALESCED
// instead of accepting - AutoCrystal.claimBreakAttempt must not publish a Java-side claim token
// unless the returned action allows it. The old Java-only equality check this replaced is kept
// there only as the dev-build fallback for when native can't be reached at all, not deleted.
//
// Reachable from two real Java threads, same as AutoCrystalPlaceLifecyclePolicy: the ordinary lane
// runs on the render thread (runCrystalCycle/onClientTick), while the spawn-reactive lane runs
// synchronously on the Netty IO thread (CrystalSpawnProcessor.handle is called directly from
// onPacketReceive, not deferred via Minecraft.execute). dispatch() takes one mutex for its entire
// body guarding both lanes - the state machine is small and every event is a short, bounded
// operation, so per-lane locking would add complexity without a measurable benefit.
namespace xing { namespace autocrystalbreaklifecycle {
	constexpr int32_t STATE_IDLE = 0;
	constexpr int32_t STATE_REQUESTED = 1;
	constexpr int32_t STATE_STAGED = 2;
	constexpr int32_t STATE_QUEUED = 3;
	constexpr int32_t STATE_ATTACKING = 4;
	constexpr int32_t STATE_EXECUTED = 5;
	constexpr int32_t STATE_PACKET_COMMITTED = 6;
	constexpr int32_t STATE_REMOVAL_OBSERVED = 7;
	constexpr int32_t STATE_EXPIRED = 8;
	constexpr int32_t STATE_INVALIDATED = 9;
	constexpr int32_t STATE_DENIED = 10;
	constexpr int32_t STATE_CLEARED = 11;
	constexpr int32_t STATE_COALESCED = 12;

	constexpr int32_t EVT_LANE_RESET = 0;
	constexpr int32_t EVT_CANDIDATE_SELECTED = 1;
	// This lane lost the claim for the entity to the other lane. EVT_CANDIDATE_SELECTED's own
	// arbitration (see below) now reaches STATE_COALESCED directly in the normal case; this event
	// is only still dispatched from AutoCrystal.claimBreakAttempt's ACTION_DISPATCH_FAILED fallback
	// path (native unreachable, dev builds only), where Java has to re-derive the decision itself.
	// Terminal, mirrors CLEARED/DENIED but distinct so logs/tests can tell "two paths wanted the
	// same crystal" apart from a real denial.
	constexpr int32_t EVT_COALESCED = 2;
	constexpr int32_t EVT_EATING_DEFER = 3;
	constexpr int32_t EVT_AIM_INVALID = 4;
	constexpr int32_t EVT_DAMAGE_REJECTED = 5;
	// Also used for "candidate no longer actionable" generally (missing, dead, or out of range) -
	// the exact cause is a diagnostic label on the Java side, not something this module needs to
	// distinguish for state-transition purposes.
	constexpr int32_t EVT_CRYSTAL_MISSING = 6;
	constexpr int32_t EVT_COOLDOWN_ACTIVE = 7;
	constexpr int32_t EVT_STAGED = 8;
	constexpr int32_t EVT_STAGE_TIMEOUT = 9;
	constexpr int32_t EVT_SUBMISSION_ACCEPTED = 10;
	constexpr int32_t EVT_SUBMISSION_DENIED = 11;
	constexpr int32_t EVT_ROTATION_DENIED = 12;
	// The one event every path fires immediately before the real attack (packet or gameMode.attack)
	// is ever issued - issues the commandId and moves to ATTACKING.
	constexpr int32_t EVT_COMMAND_ISSUED = 13;
	// boolFlag: 1 = the attack was actually sent, 0 = it failed.
	constexpr int32_t EVT_ACTION_RESULT = 14;
	constexpr int32_t EVT_PACKET_COMMITTED = 15;
	constexpr int32_t EVT_REMOVAL_OBSERVED = 16;
	constexpr int32_t EVT_PREDICTION_TIMEOUT = 17;
	constexpr int32_t EVT_EXTERNAL_CLEAR = 18;

	constexpr int32_t LANE_ORDINARY = 0;
	constexpr int32_t LANE_SPAWN_REACTIVE = 1;
	constexpr int32_t LANE_COUNT = 2;

	constexpr int32_t ACTION_NONE = 0;
	// EVT_CANDIDATE_SELECTED-only: this lane lost the coalescing arbitration to the other lane, which
	// already holds a live claim on the same entityId. Must match
	// AutoCrystalBreakLifecycleShadowEngine.ACTION_DENY_COALESCED numerically - no translation layer
	// on the Java side.
	constexpr int32_t ACTION_DENY_COALESCED = 1;
	// EVT_COMMAND_ISSUED-only: this lane's state wasn't legal for issuance (not
	// REQUESTED/STAGED/QUEUED - stale session/generation, already ATTACKING, or terminal). Java
	// must not create/publish a command token or send the real attack packet/gameMode.attack call
	// when this comes back - see the caller-side gate in AutoCrystal.buildBreakLifecycleHooks's
	// commandIssued() override. Must match
	// AutoCrystalBreakLifecycleShadowEngine.ACTION_DENY_COMMAND numerically.
	constexpr int32_t ACTION_DENY_COMMAND = 2;

	constexpr int32_t REASON_NONE = 0;
	constexpr int32_t REASON_EATING = 1 << 0;
	constexpr int32_t REASON_AIM_INVALID = 1 << 1;
	constexpr int32_t REASON_DAMAGE_REJECTED = 1 << 2;
	constexpr int32_t REASON_CRYSTAL_MISSING = 1 << 3;
	constexpr int32_t REASON_COOLDOWN = 1 << 4;
	constexpr int32_t REASON_ROTATION = 1 << 5;
	constexpr int32_t REASON_RATE_LIMIT = 1 << 6;
	constexpr int32_t REASON_ACTION_FAILED = 1 << 7;
	constexpr int32_t REASON_STAGE_TIMEOUT = 1 << 8;
	constexpr int32_t REASON_PREDICTION_TIMEOUT = 1 << 9;
	constexpr int32_t REASON_EXTERNAL_CLEAR = 1 << 10;
	constexpr int32_t REASON_COALESCED = 1 << 11;

	struct ShadowState {
		int64_t sessionId = -1;
		int32_t generation = -1;
		int32_t state = STATE_IDLE;
		int64_t nextCommandId = 1;
		int64_t pendingCommandId = -1;
		int32_t reasonFlags = REASON_NONE;
		int32_t entityId = 0;
		bool hasEntity = false;
	};

	constexpr size_t INPUT_BYTES = 48;
	constexpr size_t OUTPUT_BYTES = 24;

	constexpr int STATUS_OK = 0;
	constexpr int STATUS_UNKNOWN_EVENT = -1;
	constexpr int STATUS_INPUT_SIZE_MISMATCH = -2;
	constexpr int STATUS_OUTPUT_SIZE_MISMATCH = -3;
	constexpr int STATUS_NULL_BUFFER = -4;
	constexpr int STATUS_INVALID_LANE = -5;

	ShadowState g_lanes[LANE_COUNT];
	std::mutex g_mutex;

	struct Input {
		int64_t sessionId;
		int32_t lane;
		int32_t generation;
		int32_t boolFlag;
		int32_t entityId;
		int64_t gameTime;
		int32_t coalescedIntoLane;
		int32_t reserved;
		int64_t commandId;
	};

	Input readInput(const uint8_t* in) {
		Input input{};
		std::memcpy(&input.sessionId, in + 0, 8);
		std::memcpy(&input.lane, in + 8, 4);
		std::memcpy(&input.generation, in + 12, 4);
		std::memcpy(&input.boolFlag, in + 16, 4);
		std::memcpy(&input.entityId, in + 20, 4);
		std::memcpy(&input.gameTime, in + 24, 8);
		std::memcpy(&input.coalescedIntoLane, in + 32, 4);
		std::memcpy(&input.reserved, in + 36, 4);
		std::memcpy(&input.commandId, in + 40, 8);
		return input;
	}

	void writeOutput(uint8_t* out, const ShadowState& state, int64_t commandId, int32_t recommendedAction) {
		int32_t stateOut = state.state;
		int32_t reservedOut = 0;
		int32_t reasonOut = state.reasonFlags;
		std::memcpy(out + 0, &stateOut, 4);
		std::memcpy(out + 4, &reservedOut, 4); // reserved padding, always zero
		std::memcpy(out + 8, &commandId, 8);
		std::memcpy(out + 16, &recommendedAction, 4);
		std::memcpy(out + 20, &reasonOut, 4);
	}

	bool isStale(const ShadowState& state, const Input& input) {
		return state.sessionId != input.sessionId || state.generation != input.generation;
	}

	bool sameEntity(const ShadowState& state, const Input& input) {
		return state.hasEntity && state.entityId == input.entityId;
	}

	int dispatch(int event, const uint8_t* inputBytes, size_t inputLen, uint8_t* outputBytes, size_t outputLen) {
		if (inputBytes == nullptr || outputBytes == nullptr) {
			return STATUS_NULL_BUFFER;
		}
		if (inputLen != INPUT_BYTES) {
			return STATUS_INPUT_SIZE_MISMATCH;
		}
		if (outputLen != OUTPUT_BYTES) {
			return STATUS_OUTPUT_SIZE_MISMATCH;
		}

		std::lock_guard<std::mutex> lock(g_mutex);

		Input input = readInput(inputBytes);

		if (event == EVT_LANE_RESET) {
			// A full engine reset (module onEnable/onDisable/client-null-branch) clears both lanes,
			// not just one - there is no per-lane reset event, matching how Java resets the whole
			// engine at once rather than lane-by-lane.
			g_lanes[LANE_ORDINARY] = ShadowState{};
			g_lanes[LANE_SPAWN_REACTIVE] = ShadowState{};
			writeOutput(outputBytes, g_lanes[LANE_ORDINARY], -1, ACTION_NONE);
			return STATUS_OK;
		}

		if (input.lane != LANE_ORDINARY && input.lane != LANE_SPAWN_REACTIVE) {
			return STATUS_INVALID_LANE;
		}

		ShadowState& state = g_lanes[input.lane];

		if (event == EVT_CANDIDATE_SELECTED) {
			// Coalescing arbitration: does the *other* lane already hold a live (non-terminal) claim
			// on this same entity? Both lanes live in g_lanes[] under the same lock this function
			// already holds, so this is a plain same-process read, no extra synchronization needed.
			int32_t otherLane = input.lane == LANE_ORDINARY ? LANE_SPAWN_REACTIVE : LANE_ORDINARY;
			const ShadowState& otherState = g_lanes[otherLane];
			bool otherLaneHoldsLiveClaim = otherState.hasEntity && otherState.entityId == input.entityId
					&& otherState.state != STATE_EXPIRED && otherState.state != STATE_INVALIDATED
					&& otherState.state != STATE_DENIED && otherState.state != STATE_CLEARED
					&& otherState.state != STATE_COALESCED;

			if (otherLaneHoldsLiveClaim) {
				// Reset fresh (discard any stale prior attempt in this lane) and record COALESCED
				// directly, same as the old Java-driven EVT_COALESCED path did - nothing in this lane
				// was ever actually requested.
				state = ShadowState{};
				state.sessionId = input.sessionId;
				state.generation = input.generation;
				state.state = STATE_COALESCED;
				state.entityId = input.entityId;
				state.hasEntity = true;
				state.reasonFlags = REASON_COALESCED;
				writeOutput(outputBytes, state, -1, ACTION_DENY_COALESCED);
				return STATUS_OK;
			}

			state = ShadowState{};
			state.sessionId = input.sessionId;
			state.generation = input.generation;
			state.state = STATE_REQUESTED;
			state.entityId = input.entityId;
			state.hasEntity = true;
			writeOutput(outputBytes, state, -1, ACTION_NONE);
			return STATUS_OK;
		}

		if (event == EVT_COALESCED) {
			// Java already lost the claim to the other lane before this lane ever had a chance to
			// act - reset fresh (so any stale prior attempt in this lane is discarded) and record
			// COALESCED directly rather than passing through REQUESTED first, since nothing in this
			// lane was ever actually requested.
			state = ShadowState{};
			state.sessionId = input.sessionId;
			state.generation = input.generation;
			state.state = STATE_COALESCED;
			state.entityId = input.entityId;
			state.hasEntity = true;
			state.reasonFlags = REASON_COALESCED;
			writeOutput(outputBytes, state, -1, ACTION_NONE);
			return STATUS_OK;
		}

		// Every other event is scoped to the current session/generation for this lane - a mismatch
		// means this event belongs to an already-superseded attempt (stale callback) and must not
		// mutate state.
		if (isStale(state, input)) {
			writeOutput(outputBytes, state, -1, ACTION_NONE);
			return STATUS_OK;
		}

		switch (event) {
			case EVT_EATING_DEFER: {
				if (state.state == STATE_REQUESTED || state.state == STATE_STAGED) {
					state.state = STATE_DENIED;
					state.reasonFlags = REASON_EATING;
				}
				writeOutput(outputBytes, state, -1, ACTION_NONE);
				return STATUS_OK;
			}
			case EVT_AIM_INVALID: {
				if (state.state == STATE_REQUESTED || state.state == STATE_STAGED) {
					state.state = STATE_INVALIDATED;
					state.reasonFlags = REASON_AIM_INVALID;
				}
				writeOutput(outputBytes, state, -1, ACTION_NONE);
				return STATUS_OK;
			}
			case EVT_DAMAGE_REJECTED: {
				if (state.state == STATE_REQUESTED || state.state == STATE_STAGED) {
					state.state = STATE_DENIED;
					state.reasonFlags = REASON_DAMAGE_REJECTED;
				}
				writeOutput(outputBytes, state, -1, ACTION_NONE);
				return STATUS_OK;
			}
			case EVT_CRYSTAL_MISSING: {
				if (state.state == STATE_REQUESTED || state.state == STATE_STAGED) {
					state.state = STATE_INVALIDATED;
					state.reasonFlags = REASON_CRYSTAL_MISSING;
				}
				writeOutput(outputBytes, state, -1, ACTION_NONE);
				return STATUS_OK;
			}
			case EVT_COOLDOWN_ACTIVE: {
				if (state.state == STATE_REQUESTED || state.state == STATE_STAGED) {
					state.state = STATE_DENIED;
					state.reasonFlags = REASON_COOLDOWN;
				}
				writeOutput(outputBytes, state, -1, ACTION_NONE);
				return STATUS_OK;
			}
			case EVT_STAGED: {
				if (state.state == STATE_REQUESTED) {
					state.state = STATE_STAGED;
				}
				writeOutput(outputBytes, state, -1, ACTION_NONE);
				return STATUS_OK;
			}
			case EVT_STAGE_TIMEOUT: {
				if (state.state == STATE_STAGED) {
					state.state = STATE_EXPIRED;
					state.reasonFlags = REASON_STAGE_TIMEOUT;
				}
				writeOutput(outputBytes, state, -1, ACTION_NONE);
				return STATUS_OK;
			}
			case EVT_SUBMISSION_ACCEPTED: {
				if (state.state == STATE_REQUESTED || state.state == STATE_STAGED) {
					state.state = STATE_QUEUED;
				}
				writeOutput(outputBytes, state, -1, ACTION_NONE);
				return STATUS_OK;
			}
			case EVT_SUBMISSION_DENIED: {
				// Valid from QUEUED too, not just REQUESTED/STAGED - CombatFrame's own arbitration
				// can reject an already-queued intent before ever running the action.
				if (state.state == STATE_REQUESTED || state.state == STATE_STAGED || state.state == STATE_QUEUED) {
					state.state = STATE_DENIED;
					state.reasonFlags = REASON_RATE_LIMIT;
				}
				writeOutput(outputBytes, state, -1, ACTION_NONE);
				return STATUS_OK;
			}
			case EVT_ROTATION_DENIED: {
				if (state.state == STATE_REQUESTED || state.state == STATE_STAGED || state.state == STATE_QUEUED) {
					state.state = STATE_DENIED;
					state.reasonFlags = REASON_ROTATION;
				}
				writeOutput(outputBytes, state, -1, ACTION_NONE);
				return STATUS_OK;
			}
			case EVT_COMMAND_ISSUED: {
				// Authoritative gate, not just a state-tracking report: this is the point Java must
				// obey before creating/publishing a command token or sending the real attack. Legal
				// only from REQUESTED/STAGED/QUEUED; anything else (stale session/generation already
				// filtered above, or a defensive double-issue) is denied.
				if (state.state == STATE_REQUESTED || state.state == STATE_STAGED || state.state == STATE_QUEUED) {
					int64_t issuedCommandId = state.nextCommandId++;
					state.pendingCommandId = issuedCommandId;
					state.state = STATE_ATTACKING;
					writeOutput(outputBytes, state, issuedCommandId, ACTION_NONE);
					return STATUS_OK;
				}
				writeOutput(outputBytes, state, -1, ACTION_DENY_COMMAND);
				return STATUS_OK;
			}
			case EVT_ACTION_RESULT: {
				bool success = input.boolFlag != 0;
				if (input.commandId == state.pendingCommandId) {
					if (success) {
						// Only advances from ATTACKING - if PACKET_COMMITTED or REMOVAL_OBSERVED
						// already arrived (both can chronologically precede this), leave that
						// more-advanced state alone rather than regressing.
						if (state.state == STATE_ATTACKING) {
							state.state = STATE_EXECUTED;
						}
					} else if (state.state == STATE_ATTACKING) {
						// Same reasoning in the failure direction: once REMOVAL_OBSERVED (or
						// PACKET_COMMITTED) has already moved state past ATTACKING, a late local
						// "failure" report can no longer invalidate what the server already
						// confirmed - the guard above simply won't match anymore.
						state.state = STATE_DENIED;
						state.reasonFlags = REASON_ACTION_FAILED;
					}
				}
				writeOutput(outputBytes, state, -1, ACTION_NONE);
				return STATUS_OK;
			}
			case EVT_PACKET_COMMITTED: {
				// Accepted only from ATTACKING - the commandId that identifies "this attempt's"
				// packet is only allocated when EVT_COMMAND_ISSUED moves state into ATTACKING, and
				// the commandId match on top of that rejects a stray commit left over from an
				// older, since-replaced attempt in the same lane.
				if (state.state == STATE_ATTACKING && input.commandId == state.pendingCommandId) {
					state.state = STATE_PACKET_COMMITTED;
				}
				writeOutput(outputBytes, state, -1, ACTION_NONE);
				return STATUS_OK;
			}
			case EVT_REMOVAL_OBSERVED: {
				// Valid from ATTACKING too, not just EXECUTED/PACKET_COMMITTED - a server-observed
				// removal is stronger confirmation than either of those (both are still just local
				// evidence), and the server can genuinely respond before the local
				// ACTION_RESULT/PACKET_COMMITTED bookkeeping catches up. Once here, EVT_ACTION_RESULT
				// can no longer regress or invalidate this - its own guard only ever fires from
				// ATTACKING, which this state no longer is.
				if ((state.state == STATE_ATTACKING || state.state == STATE_EXECUTED
						|| state.state == STATE_PACKET_COMMITTED) && sameEntity(state, input)) {
					state.state = STATE_REMOVAL_OBSERVED;
				}
				writeOutput(outputBytes, state, -1, ACTION_NONE);
				return STATUS_OK;
			}
			case EVT_PREDICTION_TIMEOUT: {
				if (state.state == STATE_EXECUTED || state.state == STATE_PACKET_COMMITTED) {
					state.state = STATE_EXPIRED;
					state.reasonFlags = REASON_PREDICTION_TIMEOUT;
				}
				writeOutput(outputBytes, state, -1, ACTION_NONE);
				return STATUS_OK;
			}
			case EVT_EXTERNAL_CLEAR: {
				state.state = STATE_CLEARED;
				state.reasonFlags = REASON_EXTERNAL_CLEAR;
				writeOutput(outputBytes, state, -1, ACTION_NONE);
				return STATUS_OK;
			}
			default:
				writeOutput(outputBytes, state, -1, ACTION_NONE);
				return STATUS_UNKNOWN_EVENT;
		}
	}
} } // namespace xing::autocrystalbreaklifecycle
