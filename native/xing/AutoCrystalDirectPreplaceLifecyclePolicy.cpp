#include "pch.h"
#include "XingNativeBridge.h"

#include <cstdint>
#include <cstring>
#include <mutex>

// Native lifecycle tracker for AutoCrystal's direct-preplace path (see
// AutoCrystalDirectPreplaceLifecycleShadowEngine.java) - AutoCityModule.queueDirectPreplace ->
// AutoCrystal.tickDirectPreplace -> AutoCrystal.executePlaceOnBlock. Pure observability: no
// authorization, recommendedAction is always ACTION_NONE on every path - this module exists only
// to make direct-preplace's lifecycle visible the same way the ordinary placement/break paths
// already are, not to gate anything. Deliberately separate from AutoCrystalPlaceLifecyclePolicy's
// module/state (own module id, own g_state, own session/generation sequence) - no lane concept,
// no coalescing arbitration, no shared token with ordinary placement. See the Java class doc for
// why: direct-preplace is provably single-threaded relative to itself (both tick entry points
// funnel through the render thread) and already mutually exclusive with the ordinary path via
// existing tick-sequencing/actionController - there is no real race for a lane/mutex-protected
// array to solve here, unlike AutoCrystalBreakLifecyclePolicy's genuine dual-thread lanes.
//
// Narrower event set than the ordinary place engine on purpose - no STAGED/QUEUED/ROTATION_DENIED/
// SUBMISSION_* states, since this slice only instruments the queueDirectPreplace -> tickDirectPreplace
// -> executePlaceOnBlock path (item/geometry checks inside tryPlaceOnBlock's shared non-staged
// fallthrough, and anything that goes through stagePlace/executePendingPlace, are out of scope -
// see the Java class doc for the exact boundary).
namespace xing { namespace autocrystaldirectpreplacelifecycle {
	constexpr int32_t STATE_IDLE = 0;
	constexpr int32_t STATE_REQUESTED = 1;
	constexpr int32_t STATE_ATTEMPTING = 2;
	constexpr int32_t STATE_EXECUTED = 3;
	constexpr int32_t STATE_PACKET_COMMITTED = 4;
	constexpr int32_t STATE_SPAWN_OBSERVED = 5;
	constexpr int32_t STATE_EXPIRED = 6;
	constexpr int32_t STATE_INVALIDATED = 7;
	constexpr int32_t STATE_DENIED = 8;
	constexpr int32_t STATE_REPLACED = 9;

	constexpr int32_t EVT_LANE_RESET = 0;
	constexpr int32_t EVT_REQUESTED = 1;
	// The previous request (if any) is being replaced by a new one before it ever resolved - see
	// AutoCrystal.queueDirectPreplace. Informational/terminal for the OLD session, dispatched
	// before the new EVT_REQUESTED bumps session/generation.
	constexpr int32_t EVT_REPLACED = 2;
	constexpr int32_t EVT_EXPIRED = 3;
	constexpr int32_t EVT_INVALIDATED = 4;
	constexpr int32_t EVT_DENIED = 5;
	constexpr int32_t EVT_COMMAND_ISSUED = 6;
	// boolFlag: 1 = useItemOn returned true, 0 = it returned false.
	constexpr int32_t EVT_ACTION_RESULT = 7;
	constexpr int32_t EVT_PACKET_COMMITTED = 8;
	// Java pre-filters the base match before ever dispatching this (see the Java class doc's "CAS-
	// clear only on matching spawn") - unlike the ordinary place engine, this module does not
	// independently re-check the spawn's base itself, it trusts the caller already matched.
	constexpr int32_t EVT_SPAWN_OBSERVED = 9;

	constexpr int32_t ACTION_NONE = 0;

	constexpr int32_t REASON_NONE = 0;
	constexpr int32_t REASON_EXPIRED = 1 << 0;
	constexpr int32_t REASON_INVALIDATED = 1 << 1;
	constexpr int32_t REASON_DENIED = 1 << 2;
	constexpr int32_t REASON_ACTION_FAILED = 1 << 3;
	constexpr int32_t REASON_REPLACED = 1 << 4;

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

	// Single global slot: direct-preplace only ever tracks one pending request at a time (matches
	// AutoCrystal's own single directPreplaceRequest field). Guarded by g_mutex even though every
	// write today comes from the render thread, matching the same defensive discipline the other
	// modules use rather than assuming the threading model never changes.
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
		int32_t reservedOut = 0;
		int32_t reasonOut = g_state.reasonFlags;
		std::memcpy(out + 0, &stateOut, 4);
		std::memcpy(out + 4, &reservedOut, 4); // reserved padding, always zero
		std::memcpy(out + 8, &commandId, 8);
		std::memcpy(out + 16, &recommendedAction, 4);
		std::memcpy(out + 20, &reasonOut, 4);
	}

	bool isStale(const Input& input) {
		return g_state.sessionId != input.sessionId || g_state.generation != input.generation;
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

		if (event == EVT_REQUESTED) {
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

		if (event == EVT_REPLACED) {
			// Dispatched against the OLD session/generation, before the new EVT_REQUESTED bumps
			// them - always accepted (informational), no staleness guard, matching
			// EVT_EXTERNAL_CLEAR's unconditional-accept shape in the other lifecycle modules.
			g_state.state = STATE_REPLACED;
			g_state.reasonFlags = REASON_REPLACED;
			writeOutput(outputBytes, -1, ACTION_NONE);
			return STATUS_OK;
		}

		// Every other event is scoped to the current session/generation - a mismatch means this
		// event belongs to an already-superseded request (stale callback) and must not mutate state.
		if (isStale(input)) {
			writeOutput(outputBytes, -1, ACTION_NONE);
			return STATUS_OK;
		}

		switch (event) {
			case EVT_EXPIRED: {
				if (g_state.state == STATE_REQUESTED) {
					g_state.state = STATE_EXPIRED;
					g_state.reasonFlags = REASON_EXPIRED;
				}
				writeOutput(outputBytes, -1, ACTION_NONE);
				return STATUS_OK;
			}
			case EVT_INVALIDATED: {
				if (g_state.state == STATE_REQUESTED) {
					g_state.state = STATE_INVALIDATED;
					g_state.reasonFlags = REASON_INVALIDATED;
				}
				writeOutput(outputBytes, -1, ACTION_NONE);
				return STATUS_OK;
			}
			case EVT_DENIED: {
				if (g_state.state == STATE_REQUESTED) {
					g_state.state = STATE_DENIED;
					g_state.reasonFlags = REASON_DENIED;
				}
				writeOutput(outputBytes, -1, ACTION_NONE);
				return STATUS_OK;
			}
			case EVT_COMMAND_ISSUED: {
				int64_t issuedCommandId = -1;
				if (g_state.state == STATE_REQUESTED) {
					issuedCommandId = g_state.nextCommandId++;
					g_state.pendingCommandId = issuedCommandId;
					g_state.state = STATE_ATTEMPTING;
				}
				writeOutput(outputBytes, issuedCommandId, ACTION_NONE);
				return STATUS_OK;
			}
			case EVT_ACTION_RESULT: {
				bool success = input.boolFlag != 0;
				if (input.commandId == g_state.pendingCommandId) {
					if (success) {
						if (g_state.state == STATE_ATTEMPTING) {
							g_state.state = STATE_EXECUTED;
						}
					} else if (g_state.state == STATE_ATTEMPTING) {
						g_state.state = STATE_DENIED;
						g_state.reasonFlags = REASON_ACTION_FAILED;
					}
				}
				writeOutput(outputBytes, -1, ACTION_NONE);
				return STATUS_OK;
			}
			case EVT_PACKET_COMMITTED: {
				if (g_state.state == STATE_ATTEMPTING && input.commandId == g_state.pendingCommandId) {
					g_state.state = STATE_PACKET_COMMITTED;
				}
				writeOutput(outputBytes, -1, ACTION_NONE);
				return STATUS_OK;
			}
			case EVT_SPAWN_OBSERVED: {
				if (g_state.state == STATE_ATTEMPTING || g_state.state == STATE_EXECUTED
						|| g_state.state == STATE_PACKET_COMMITTED) {
					g_state.state = STATE_SPAWN_OBSERVED;
				}
				writeOutput(outputBytes, -1, ACTION_NONE);
				return STATUS_OK;
			}
			default:
				writeOutput(outputBytes, -1, ACTION_NONE);
				return STATUS_UNKNOWN_EVENT;
		}
	}
} } // namespace xing::autocrystaldirectpreplacelifecycle
