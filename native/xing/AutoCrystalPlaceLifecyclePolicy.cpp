#include "pch.h"
#include "XingNativeBridge.h"

#include <cstdint>
#include <cstring>
#include <mutex>

// Shadow evaluation for AutoCrystal's ordinary runCrystalCycle placement lifecycle (see
// AutoCrystalPlaceLifecycleShadowEngine.java). Pure state tracking - no world/entity access, no
// packets, no rotation, no scheduler submission. Java stays fully authoritative; this module only
// tracks its own state machine in parallel and reports for Java-side comparison logging.
//
// Direct preplace and CEV share some of the same Java call sites (executePlaceOnBlock etc.) as the
// ordinary path, but the Java side now threads an explicit ordinaryLifecycle parameter through
// every one of those call sites rather than relying on ambient/shared state, so those callers never
// even dispatch an event here at all - there is nothing for this module to reject as stale from
// them, because they simply don't call in.
//
// Unlike PacketMinePolicy/AutoCrystalPolicy/AutoCrystalBreakPolicy, this module is genuinely
// called from two different Java threads: PacketCommitEvent fires wherever NetworkManager sent
// the packet from (the render thread, for our own outbound sends), while inbound spawn-packet
// observation runs on the Netty IO thread. Both paths dispatch into the same global g_state, so
// dispatch() takes a mutex for its entire body - there is no fine-grained locking here, the whole
// state machine is small and every event is already a short, bounded operation.
namespace xing { namespace autocrystalplacelifecycle {
	constexpr int32_t STATE_IDLE = 0;
	constexpr int32_t STATE_REQUESTED = 1;
	constexpr int32_t STATE_STAGED = 2;
	constexpr int32_t STATE_QUEUED = 3;
	constexpr int32_t STATE_ATTEMPTING = 4;
	constexpr int32_t STATE_EXECUTED = 5;
	constexpr int32_t STATE_PACKET_COMMITTED = 6;
	constexpr int32_t STATE_SPAWN_OBSERVED = 7;
	constexpr int32_t STATE_EXPIRED = 8;
	constexpr int32_t STATE_INVALIDATED = 9;
	constexpr int32_t STATE_DENIED = 10;
	constexpr int32_t STATE_CLEARED = 11;

	constexpr int32_t EVT_LANE_RESET = 0;
	constexpr int32_t EVT_CANDIDATE_SELECTED = 1;
	constexpr int32_t EVT_NO_ITEM = 2;
	constexpr int32_t EVT_EATING_DEFER = 3;
	constexpr int32_t EVT_GEOMETRY_INVALID = 4;
	constexpr int32_t EVT_STAGED = 5;
	constexpr int32_t EVT_STAGE_TIMEOUT = 6;
	// Real scheduler admission - collected path only, informational, does not issue a commandId
	// and does not by itself move past QUEUED into execution.
	constexpr int32_t EVT_SUBMISSION_ACCEPTED = 7;
	constexpr int32_t EVT_SUBMISSION_DENIED = 8;
	constexpr int32_t EVT_ROTATION_DENIED = 9;
	// The one event every path fires, immediately before useItemOn is ever called - issues the
	// commandId and moves to ATTEMPTING. For the direct/reactive path (no real scheduler step)
	// this fires straight from REQUESTED or STAGED; for the collected path it fires from QUEUED,
	// after EVT_SUBMISSION_ACCEPTED already ran.
	constexpr int32_t EVT_COMMAND_ISSUED = 10;
	// boolFlag: 1 = useItemOn returned true, 0 = it returned false.
	constexpr int32_t EVT_ACTION_RESULT = 11;
	constexpr int32_t EVT_PACKET_COMMITTED = 12;
	constexpr int32_t EVT_SPAWN_OBSERVED = 13;
	constexpr int32_t EVT_PREDICTION_TIMEOUT = 14;
	constexpr int32_t EVT_EXTERNAL_CLEAR = 15;

	constexpr int32_t ACTION_NONE = 0;
	// EVT_COMMAND_ISSUED-only: state wasn't legal for issuance (not REQUESTED/STAGED/QUEUED - stale
	// session/generation, already ATTEMPTING, or terminal). Java must not create/publish a command
	// token or send the real packet when this comes back - see the caller-side gate in
	// AutoCrystal.executePlaceOnBlock. Must match
	// AutoCrystalPlaceLifecycleShadowEngine.ACTION_DENY_COMMAND numerically.
	constexpr int32_t ACTION_DENY_COMMAND = 1;

	constexpr int32_t REASON_NONE = 0;
	constexpr int32_t REASON_NO_ITEM = 1 << 0;
	constexpr int32_t REASON_EATING = 1 << 1;
	constexpr int32_t REASON_GEOMETRY = 1 << 2;
	constexpr int32_t REASON_ROTATION = 1 << 3;
	constexpr int32_t REASON_RATE_LIMIT = 1 << 4;
	constexpr int32_t REASON_ACTION_FAILED = 1 << 5;
	constexpr int32_t REASON_STAGE_TIMEOUT = 1 << 6;
	constexpr int32_t REASON_PREDICTION_TIMEOUT = 1 << 7;
	constexpr int32_t REASON_EXTERNAL_CLEAR = 1 << 8;

	struct ShadowState {
		int64_t sessionId = -1;
		int32_t generation = -1;
		int32_t state = STATE_IDLE;
		int64_t nextCommandId = 1;
		int64_t pendingCommandId = -1;
		int32_t reasonFlags = REASON_NONE;
		int32_t basePosX = 0;
		int32_t basePosY = 0;
		int32_t basePosZ = 0;
		bool hasBase = false;
	};

	constexpr size_t INPUT_BYTES = 56;
	constexpr size_t OUTPUT_BYTES = 24;

	constexpr int STATUS_OK = 0;
	constexpr int STATUS_UNKNOWN_EVENT = -1;
	constexpr int STATUS_INPUT_SIZE_MISMATCH = -2;
	constexpr int STATUS_OUTPUT_SIZE_MISMATCH = -3;
	constexpr int STATUS_NULL_BUFFER = -4;

	// Single global slot: AutoCrystal only ever tracks one pending ordinary placement attempt at a
	// time (one PendingAction, one CrystalActionController), same reasoning as PacketMinePolicy's
	// single-lane assumption. Guarded by g_mutex (see file header comment) - unlike the other
	// policy modules, this one really is reachable from two different Java threads.
	ShadowState g_state;
	std::mutex g_mutex;

	struct Input {
		int64_t sessionId;
		int32_t generation;
		int32_t boolFlag;
		int64_t gameTime;
		int32_t candidateId;
		int32_t basePosX;
		int32_t basePosY;
		int32_t basePosZ;
		int32_t targetEntityId;
		int64_t commandId;
	};

	Input readInput(const uint8_t* in) {
		Input input{};
		std::memcpy(&input.sessionId, in + 0, 8);
		std::memcpy(&input.generation, in + 8, 4);
		std::memcpy(&input.boolFlag, in + 12, 4);
		std::memcpy(&input.gameTime, in + 16, 8);
		std::memcpy(&input.candidateId, in + 24, 4);
		std::memcpy(&input.basePosX, in + 28, 4);
		std::memcpy(&input.basePosY, in + 32, 4);
		std::memcpy(&input.basePosZ, in + 36, 4);
		std::memcpy(&input.targetEntityId, in + 40, 4);
		std::memcpy(&input.commandId, in + 48, 8);
		return input;
	}

	void writeOutput(uint8_t* out, int64_t commandId, int32_t recommendedAction) {
		int32_t stateOut = g_state.state;
		int32_t reasonOut = g_state.reasonFlags;
		int32_t reservedOut = 0;
		std::memcpy(out + 0, &stateOut, 4);
		std::memcpy(out + 4, &reservedOut, 4); // reserved padding, always zero
		std::memcpy(out + 8, &commandId, 8);
		std::memcpy(out + 16, &recommendedAction, 4);
		std::memcpy(out + 20, &reasonOut, 4);
	}

	bool isStale(const Input& input) {
		return g_state.sessionId != input.sessionId || g_state.generation != input.generation;
	}

	bool sameBase(const Input& input) {
		return g_state.hasBase && g_state.basePosX == input.basePosX
				&& g_state.basePosY == input.basePosY && g_state.basePosZ == input.basePosZ;
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
			g_state = ShadowState{};
			writeOutput(outputBytes, -1, ACTION_NONE);
			return STATUS_OK;
		}

		if (event == EVT_CANDIDATE_SELECTED) {
			g_state = ShadowState{};
			g_state.sessionId = input.sessionId;
			g_state.generation = input.generation;
			g_state.state = STATE_REQUESTED;
			g_state.basePosX = input.basePosX;
			g_state.basePosY = input.basePosY;
			g_state.basePosZ = input.basePosZ;
			g_state.hasBase = true;
			writeOutput(outputBytes, -1, ACTION_NONE);
			return STATUS_OK;
		}

		// Every other event is scoped to the current session/generation - a mismatch means this
		// event belongs to an already-superseded attempt (stale callback) and must not mutate
		// state. Direct preplace/CEV never dispatch here at all (see the file header comment), so
		// this is purely the ordinary-session staleness guard, not a cross-caller filter.
		if (isStale(input)) {
			writeOutput(outputBytes, -1, ACTION_NONE);
			return STATUS_OK;
		}

		switch (event) {
			case EVT_NO_ITEM: {
				if (g_state.state == STATE_REQUESTED || g_state.state == STATE_STAGED) {
					g_state.state = STATE_DENIED;
					g_state.reasonFlags = REASON_NO_ITEM;
				}
				writeOutput(outputBytes, -1, ACTION_NONE);
				return 0;
			}
			case EVT_EATING_DEFER: {
				if (g_state.state == STATE_REQUESTED || g_state.state == STATE_STAGED) {
					g_state.state = STATE_DENIED;
					g_state.reasonFlags = REASON_EATING;
				}
				writeOutput(outputBytes, -1, ACTION_NONE);
				return 0;
			}
			case EVT_GEOMETRY_INVALID: {
				if (g_state.state == STATE_REQUESTED || g_state.state == STATE_STAGED) {
					g_state.state = STATE_INVALIDATED;
					g_state.reasonFlags = REASON_GEOMETRY;
				}
				writeOutput(outputBytes, -1, ACTION_NONE);
				return 0;
			}
			case EVT_STAGED: {
				if (g_state.state == STATE_REQUESTED) {
					g_state.state = STATE_STAGED;
				}
				writeOutput(outputBytes, -1, ACTION_NONE);
				return 0;
			}
			case EVT_STAGE_TIMEOUT: {
				if (g_state.state == STATE_STAGED) {
					g_state.state = STATE_EXPIRED;
					g_state.reasonFlags = REASON_STAGE_TIMEOUT;
				}
				writeOutput(outputBytes, -1, ACTION_NONE);
				return 0;
			}
			case EVT_SUBMISSION_ACCEPTED: {
				if (g_state.state == STATE_REQUESTED || g_state.state == STATE_STAGED) {
					g_state.state = STATE_QUEUED;
				}
				writeOutput(outputBytes, -1, ACTION_NONE);
				return 0;
			}
			case EVT_SUBMISSION_DENIED: {
				// Valid from QUEUED too, not just REQUESTED/STAGED: the collected path's completion
				// callback can still report a denial after EVT_SUBMISSION_ACCEPTED already moved
				// state to QUEUED - CombatFrame's own arbitration (dependency/validWhen/resource
				// checks in executeIntent) rejecting the intent before ever running the action.
				if (g_state.state == STATE_REQUESTED || g_state.state == STATE_STAGED || g_state.state == STATE_QUEUED) {
					g_state.state = STATE_DENIED;
					g_state.reasonFlags = REASON_RATE_LIMIT;
				}
				writeOutput(outputBytes, -1, ACTION_NONE);
				return 0;
			}
			case EVT_ROTATION_DENIED: {
				if (g_state.state == STATE_QUEUED) {
					g_state.state = STATE_DENIED;
					g_state.reasonFlags = REASON_ROTATION;
				}
				writeOutput(outputBytes, -1, ACTION_NONE);
				return 0;
			}
			case EVT_COMMAND_ISSUED: {
				// Authoritative gate, not just a state-tracking report: this is the point Java must
				// obey before creating/publishing a command token or sending the real placement
				// packet. Legal only from REQUESTED/STAGED/QUEUED; anything else (stale
				// session/generation already filtered above, or a defensive double-issue) is denied.
				if (g_state.state == STATE_REQUESTED || g_state.state == STATE_STAGED || g_state.state == STATE_QUEUED) {
					int64_t issuedCommandId = g_state.nextCommandId++;
					g_state.pendingCommandId = issuedCommandId;
					g_state.state = STATE_ATTEMPTING;
					writeOutput(outputBytes, issuedCommandId, ACTION_NONE);
					return 0;
				}
				writeOutput(outputBytes, -1, ACTION_DENY_COMMAND);
				return 0;
			}
			case EVT_ACTION_RESULT: {
				bool success = input.boolFlag != 0;
				if (input.commandId == g_state.pendingCommandId) {
					if (success) {
						// Only advances from ATTEMPTING - if PACKET_COMMITTED or SPAWN_OBSERVED
						// already arrived (both can chronologically precede this - see their own
						// comments), leave that more-advanced state alone rather than regressing.
						if (g_state.state == STATE_ATTEMPTING) {
							g_state.state = STATE_EXECUTED;
						}
					} else if (g_state.state == STATE_ATTEMPTING) {
						// Same reasoning in the failure direction: once SPAWN_OBSERVED (or
						// PACKET_COMMITTED) has already moved state past ATTEMPTING, a late local
						// "failure" report can no longer invalidate what the server already
						// confirmed - the guard below simply won't match anymore.
						g_state.state = STATE_DENIED;
						g_state.reasonFlags = REASON_ACTION_FAILED;
					}
				}
				writeOutput(outputBytes, -1, ACTION_NONE);
				return 0;
			}
			case EVT_PACKET_COMMITTED: {
				// Accepted only from ATTEMPTING (never QUEUED) - a real packet-commit event
				// arriving before ATTEMPTING would mean it belongs to some other attempt, since
				// the command that identifies "this attempt's" packet is only allocated when
				// EVT_COMMAND_ISSUED moves state into ATTEMPTING. The commandId match is required
				// on top of that: a stray commit event left over from an older, since-replaced
				// attempt within the same session still can't advance a newer one.
				if (g_state.state == STATE_ATTEMPTING && input.commandId == g_state.pendingCommandId) {
					g_state.state = STATE_PACKET_COMMITTED;
				}
				writeOutput(outputBytes, -1, ACTION_NONE);
				return 0;
			}
			case EVT_SPAWN_OBSERVED: {
				// Independently verify the spawn's base against our own tracked base rather than
				// trusting that Java only calls this for a real match - a mismatch is a pure
				// no-op, never consumes/invalidates/advances the active lifecycle.
				//
				// Valid from ATTEMPTING too, not just EXECUTED/PACKET_COMMITTED: a server-observed
				// spawn is stronger confirmation than either of those (both are still just local
				// evidence - one that the client-side call succeeded, one that a packet reached the
				// network layer), and the server can genuinely respond before the local
				// ACTION_RESULT/PACKET_COMMITTED bookkeeping catches up. Once here, EVT_ACTION_RESULT
				// can no longer regress or invalidate this - its own guard only ever fires from
				// ATTEMPTING, which this state no longer is.
				if ((g_state.state == STATE_ATTEMPTING || g_state.state == STATE_EXECUTED
						|| g_state.state == STATE_PACKET_COMMITTED) && sameBase(input)) {
					g_state.state = STATE_SPAWN_OBSERVED;
				}
				writeOutput(outputBytes, -1, ACTION_NONE);
				return 0;
			}
			case EVT_PREDICTION_TIMEOUT: {
				if (g_state.state == STATE_EXECUTED || g_state.state == STATE_PACKET_COMMITTED) {
					g_state.state = STATE_EXPIRED;
					g_state.reasonFlags = REASON_PREDICTION_TIMEOUT;
				}
				writeOutput(outputBytes, -1, ACTION_NONE);
				return 0;
			}
			case EVT_EXTERNAL_CLEAR: {
				g_state.state = STATE_CLEARED;
				g_state.reasonFlags = REASON_EXTERNAL_CLEAR;
				writeOutput(outputBytes, -1, ACTION_NONE);
				return 0;
			}
			default:
				writeOutput(outputBytes, -1, ACTION_NONE);
				return -1;
		}
	}
} } // namespace xing::autocrystalplacelifecycle
