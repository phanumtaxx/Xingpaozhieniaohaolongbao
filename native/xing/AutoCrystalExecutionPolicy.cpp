#include "pch.h"
#include "XingNativeBridge.h"

#include <cstdint>
#include <cstring>

// Shadow-mode execution-command verdict for AutoCrystal's ordinary place, ordinary break, and
// spawn-reactive break paths (see AutoCrystalExecutionPolicyShadowEngine.java). Stateless,
// single-shot: unlike the lifecycle engines, this module tracks no session/generation state at
// all - every call is independent, computing NOOP/PLACE/ATTACK plus the command's shape purely
// from the facts Java passes in for that one call. Pure observability: this module's verdict is
// never acted on, only compared against whatever Java's own (unmodified) execution logic actually
// did - see the class doc on the Java side for why, and for the explicit scope boundary (no
// direct-preplace, no staged paths, no CombatFrame/CombatActionScheduler arbitration - "scheduler
// eligible" arrives as a single Java-computed fact, this module does not reason about resources
// or timing itself).
//
// The success criterion this module exists to support is native *independently* arriving at the
// same verdict Java's real logic did, from the same underlying facts - not echoing a command Java
// already built. Each source's logic below is a from-scratch reimplementation of the
// corresponding Java decision (AutoCrystal.crystalPlacementHand's ItemHandMode switch,
// CrystalBreakPipeline's packet/gameMode branch), not a passthrough.
namespace xing { namespace autocrystalexecutionpolicy {
	constexpr int32_t SOURCE_ORDINARY_PLACE = 0;
	constexpr int32_t SOURCE_ORDINARY_BREAK = 1;
	constexpr int32_t SOURCE_SPAWN_REACTIVE_BREAK = 2;

	constexpr int32_t COMMAND_NOOP = 0;
	constexpr int32_t COMMAND_PLACE = 1;
	constexpr int32_t COMMAND_ATTACK = 2;

	constexpr int32_t REASON_NONE = 0;
	constexpr int32_t REASON_NOT_ELIGIBLE = 1 << 0;
	constexpr int32_t REASON_NO_HAND = 1 << 1;

	// Bit layout of the input's flags field - must match
	// AutoCrystalExecutionPolicyShadowEngine.java's FLAG_* constants exactly.
	constexpr int32_t FLAG_PACKET_MODE = 1 << 0;
	constexpr int32_t FLAG_SWING = 1 << 1;
	constexpr int32_t FLAG_ROTATE = 1 << 2;
	constexpr int32_t FLAG_MAINHAND_CRYSTAL = 1 << 3;
	constexpr int32_t FLAG_OFFHAND_CRYSTAL = 1 << 4;
	constexpr int32_t FLAG_HOTBAR_IN_RANGE = 1 << 5;
	constexpr int32_t FLAG_CONNECTION_AVAILABLE = 1 << 6;
	constexpr int32_t FLAG_SCHEDULER_ELIGIBLE = 1 << 7;

	// ItemHandMode.java's declared enum order - ordinal values, not arbitrary.
	constexpr int32_t HAND_MODE_MAINHAND = 0;
	constexpr int32_t HAND_MODE_OFFHAND = 1;
	constexpr int32_t HAND_MODE_PREFER_OFFHAND = 2;
	constexpr int32_t HAND_MODE_PREFER_MAINHAND = 3;

	// SwapMode.java's declared enum order.
	constexpr int32_t SWAP_MODE_NONE = 2;

	// InteractionHand output encoding - arbitrary but must match the Java side's expectations.
	constexpr int32_t HAND_MAIN = 0;
	constexpr int32_t HAND_OFF = 1;
	constexpr int32_t HAND_NONE = -1;

	// SlotActionPriority.java's real constant values - native's own independent expectation for
	// what each source's real priority should be, not something Java passes in to be echoed.
	constexpr int32_t PRIORITY_AUTOCRYSTAL_PLACE = 75;
	constexpr int32_t PRIORITY_AUTOCRYSTAL_BREAK = 80;

	constexpr size_t INPUT_BYTES = 64;
	constexpr size_t OUTPUT_BYTES = 32;

	constexpr int STATUS_OK = 0;
	constexpr int STATUS_INPUT_SIZE_MISMATCH = -2;
	constexpr int STATUS_OUTPUT_SIZE_MISMATCH = -3;
	constexpr int STATUS_NULL_BUFFER = -4;
	constexpr int STATUS_INVALID_SOURCE = -5;

	struct Input {
		int64_t sessionId;
		int64_t commandId;
		int32_t source;
		int32_t lane;
		int32_t entityId;
		int32_t basePosX;
		int32_t basePosY;
		int32_t basePosZ;
		int32_t flags;
		int32_t handMode;
		int32_t swapMode;
		int32_t hotbarSlot;
		int32_t rotationHoldTicks;
	};

	Input readInput(const uint8_t* in) {
		Input input{};
		std::memcpy(&input.sessionId, in + 0, 8);
		std::memcpy(&input.commandId, in + 8, 8);
		std::memcpy(&input.source, in + 16, 4);
		std::memcpy(&input.lane, in + 20, 4);
		std::memcpy(&input.entityId, in + 24, 4);
		std::memcpy(&input.basePosX, in + 28, 4);
		std::memcpy(&input.basePosY, in + 32, 4);
		std::memcpy(&input.basePosZ, in + 36, 4);
		std::memcpy(&input.flags, in + 40, 4);
		std::memcpy(&input.handMode, in + 44, 4);
		std::memcpy(&input.swapMode, in + 48, 4);
		std::memcpy(&input.hotbarSlot, in + 52, 4);
		std::memcpy(&input.rotationHoldTicks, in + 56, 4);
		return input;
	}

	void writeOutput(uint8_t* out, int32_t command, int32_t reasonFlags, int32_t hand, int32_t slot,
			bool swing, bool usePacket, int32_t priority) {
		int32_t swingOut = swing ? 1 : 0;
		int32_t packetOut = usePacket ? 1 : 0;
		int32_t reservedOut = 0;
		std::memcpy(out + 0, &command, 4);
		std::memcpy(out + 4, &reasonFlags, 4);
		std::memcpy(out + 8, &hand, 4);
		std::memcpy(out + 12, &slot, 4);
		std::memcpy(out + 16, &swingOut, 4);
		std::memcpy(out + 20, &packetOut, 4);
		std::memcpy(out + 24, &priority, 4);
		std::memcpy(out + 28, &reservedOut, 4);
	}

	// From-scratch reimplementation of AutoCrystal.crystalPlacementHand's ItemHandMode switch -
	// not a passthrough. Returns HAND_NONE if no eligible hand exists for the given mode/facts,
	// exactly mirroring the Java method's null-return cases.
	int32_t resolvePlaceHand(const Input& input) {
		bool mainhand = (input.flags & FLAG_MAINHAND_CRYSTAL) != 0;
		bool offhand = (input.flags & FLAG_OFFHAND_CRYSTAL) != 0;
		bool hotbar = (input.flags & FLAG_HOTBAR_IN_RANGE) != 0;
		bool swapEligible = input.swapMode != SWAP_MODE_NONE && hotbar;
		bool mainhandEligible = mainhand || swapEligible;

		switch (input.handMode) {
			case HAND_MODE_OFFHAND:
				return offhand ? HAND_OFF : HAND_NONE;
			case HAND_MODE_MAINHAND:
				return mainhandEligible ? HAND_MAIN : HAND_NONE;
			case HAND_MODE_PREFER_OFFHAND:
				if (offhand) return HAND_OFF;
				return mainhandEligible ? HAND_MAIN : HAND_NONE;
			case HAND_MODE_PREFER_MAINHAND:
				if (mainhandEligible) return HAND_MAIN;
				return offhand ? HAND_OFF : HAND_NONE;
			default:
				return HAND_NONE;
		}
	}

	int dispatch(int event, const uint8_t* inputBytes, size_t inputLen, uint8_t* outputBytes, size_t outputLen) {
		(void) event; // stateless single-operation module - no event discrimination needed
		if (inputBytes == nullptr || outputBytes == nullptr) {
			return STATUS_NULL_BUFFER;
		}
		if (inputLen != INPUT_BYTES) {
			return STATUS_INPUT_SIZE_MISMATCH;
		}
		if (outputLen != OUTPUT_BYTES) {
			return STATUS_OUTPUT_SIZE_MISMATCH;
		}

		Input input = readInput(inputBytes);
		bool schedulerEligible = (input.flags & FLAG_SCHEDULER_ELIGIBLE) != 0;
		bool swingSetting = (input.flags & FLAG_SWING) != 0;
		bool packetModeSetting = (input.flags & FLAG_PACKET_MODE) != 0;

		switch (input.source) {
			case SOURCE_ORDINARY_PLACE: {
				if (!schedulerEligible) {
					writeOutput(outputBytes, COMMAND_NOOP, REASON_NOT_ELIGIBLE, HAND_NONE, -1, false, false, PRIORITY_AUTOCRYSTAL_PLACE);
					return STATUS_OK;
				}
				int32_t hand = resolvePlaceHand(input);
				if (hand == HAND_NONE) {
					writeOutput(outputBytes, COMMAND_NOOP, REASON_NO_HAND, HAND_NONE, -1, false, false, PRIORITY_AUTOCRYSTAL_PLACE);
					return STATUS_OK;
				}
				writeOutput(outputBytes, COMMAND_PLACE, REASON_NONE, hand, input.hotbarSlot, swingSetting, packetModeSetting, PRIORITY_AUTOCRYSTAL_PLACE);
				return STATUS_OK;
			}
			case SOURCE_ORDINARY_BREAK: {
				if (!schedulerEligible) {
					writeOutput(outputBytes, COMMAND_NOOP, REASON_NOT_ELIGIBLE, HAND_NONE, -1, false, false, PRIORITY_AUTOCRYSTAL_BREAK);
					return STATUS_OK;
				}
				// Break has no hand/slot decision at all - omitted entirely per the Java side's
				// explicit scope (attacking an entity is not an item-placement decision). usePacket
				// mirrors CrystalBreakPipeline's real "packetBreak setting && connection available"
				// gate - matches ordinary break's real execution paths exactly.
				bool connectionAvailable = (input.flags & FLAG_CONNECTION_AVAILABLE) != 0;
				bool usePacket = packetModeSetting && connectionAvailable;
				writeOutput(outputBytes, COMMAND_ATTACK, REASON_NONE, HAND_NONE, -1, swingSetting, usePacket, PRIORITY_AUTOCRYSTAL_BREAK);
				return STATUS_OK;
			}
			case SOURCE_SPAWN_REACTIVE_BREAK: {
				if (!schedulerEligible) {
					writeOutput(outputBytes, COMMAND_NOOP, REASON_NOT_ELIGIBLE, HAND_NONE, -1, false, false, PRIORITY_AUTOCRYSTAL_BREAK);
					return STATUS_OK;
				}
				// Spawn-reactive break is intentionally always-packet, independent of the
				// packetBreak setting or connection-availability facts - this is
				// CrystalBreakPipeline.tryBreakSpawnedCrystal's real, deliberate behavior (its
				// entry point already requires client.getConnection() != null before this method
				// can even be reached), not a setting-driven decision like ordinary break's. Do
				// not "fix" this to match ordinary break's gate - that would be a real behavior
				// change to a working, intentional path, not a parity fix.
				writeOutput(outputBytes, COMMAND_ATTACK, REASON_NONE, HAND_NONE, -1, swingSetting, true, PRIORITY_AUTOCRYSTAL_BREAK);
				return STATUS_OK;
			}
			default:
				return STATUS_INVALID_SOURCE;
		}
	}
} } // namespace xing::autocrystalexecutionpolicy
