#include "pch.h"
#include "XingNativeBridge.h"

#include <cstdint>
#include <cstring>
#include <vector>

// Shadow evaluation for AutoCrystal's break candidate ranking slice (see
// CrystalBreakPipeline.selectBestCrystal and AutoCrystalBreakShadowEngine.java). Pure arithmetic
// over an already-computed, already-scanned observation snapshot - no world/entity access, no
// packets, no rotation, no scheduler submission. Java stays fully authoritative; this module
// independently reproduces Java's eligibility filter, max-comparator tie-break chain, and the
// eating/cooldown/aim/stage decision chain that picks NONE vs STAGE_BREAK vs
// REQUEST_IMMEDIATE_BREAK, over the same scan-order observations Java computed. It never sees
// Java's own winner or action bucket as input.
namespace xing { namespace autocrystalbreakpolicy {
	constexpr int32_t SCHEMA_VERSION = 1;

	constexpr int32_t INPUT_HEADER_BYTES = 32;
	constexpr int32_t INPUT_CANDIDATE_BYTES = 80;
	constexpr int32_t OUTPUT_BYTES = 32;

	constexpr int STATUS_OK = 0;
	constexpr int STATUS_UNKNOWN_EVENT = -1;
	constexpr int STATUS_SCHEMA_VERSION_MISMATCH = -2;
	constexpr int STATUS_INPUT_SIZE_MISMATCH = -3;
	constexpr int STATUS_OUTPUT_SIZE_MISMATCH = -4;
	constexpr int STATUS_NULL_BUFFER = -5;

	constexpr int32_t EVT_RANK_BREAK_CANDIDATES = 0;

	constexpr int32_t ACTION_NONE = 0;
	constexpr int32_t ACTION_STAGE_BREAK = 1;
	constexpr int32_t ACTION_REQUEST_IMMEDIATE_BREAK = 2;

	constexpr int32_t REASON_EATING_DEFER = 1 << 0;
	constexpr int32_t REASON_NO_ELIGIBLE_CANDIDATE = 1 << 1;
	constexpr int32_t REASON_ATTACK_COOLING_DOWN = 1 << 2;
	constexpr int32_t REASON_AIM_INVALID = 1 << 3;
	constexpr int32_t REASON_STAGED = 1 << 4;

	struct Header {
		int32_t schemaVersion;
		int32_t candidateCount;
		bool eatingDefer;
		bool immediateBreakSetting;
		bool stageRequested;
		double maxSelfDamage;
	};

	struct Candidate {
		int32_t candidateId;
		int32_t crystalEntityId;
		bool alive;
		bool inRange;
		bool aimValid;
		bool hasPrimaryTarget;
		int32_t primaryTargetEntityId;
		bool hasPreferredBase;
		bool attackCoolingDown;
		double selfDamage;
		double aggregateTargetDamage;
		double primaryTargetDamage;
		double preferredBaseDistanceSq;
		double playerDistanceSq;
	};

	Header readHeader(const uint8_t* in) {
		Header header{};
		int32_t schemaVersion = 0, candidateCount = 0, eatingDefer = 0, immediateBreakSetting = 0, stageRequested = 0;
		std::memcpy(&schemaVersion, in + 0, 4);
		std::memcpy(&candidateCount, in + 4, 4);
		std::memcpy(&eatingDefer, in + 8, 4);
		std::memcpy(&immediateBreakSetting, in + 12, 4);
		std::memcpy(&stageRequested, in + 16, 4);
		std::memcpy(&header.maxSelfDamage, in + 24, 8);
		header.schemaVersion = schemaVersion;
		header.candidateCount = candidateCount;
		header.eatingDefer = eatingDefer != 0;
		header.immediateBreakSetting = immediateBreakSetting != 0;
		header.stageRequested = stageRequested != 0;
		return header;
	}

	Candidate readCandidate(const uint8_t* in, int32_t index) {
		const uint8_t* base = in + INPUT_HEADER_BYTES + static_cast<size_t>(index) * INPUT_CANDIDATE_BYTES;
		Candidate candidate{};
		int32_t alive = 0, inRange = 0, aimValid = 0, hasPrimaryTarget = 0, hasPreferredBase = 0, attackCoolingDown = 0;
		std::memcpy(&candidate.candidateId, base + 0, 4);
		std::memcpy(&candidate.crystalEntityId, base + 4, 4);
		std::memcpy(&alive, base + 8, 4);
		std::memcpy(&inRange, base + 12, 4);
		std::memcpy(&aimValid, base + 16, 4);
		std::memcpy(&hasPrimaryTarget, base + 20, 4);
		std::memcpy(&candidate.primaryTargetEntityId, base + 24, 4);
		std::memcpy(&hasPreferredBase, base + 28, 4);
		std::memcpy(&attackCoolingDown, base + 32, 4);
		std::memcpy(&candidate.selfDamage, base + 40, 8);
		std::memcpy(&candidate.aggregateTargetDamage, base + 48, 8);
		std::memcpy(&candidate.primaryTargetDamage, base + 56, 8);
		std::memcpy(&candidate.preferredBaseDistanceSq, base + 64, 8);
		std::memcpy(&candidate.playerDistanceSq, base + 72, 8);
		candidate.alive = alive != 0;
		candidate.inRange = inRange != 0;
		candidate.aimValid = aimValid != 0;
		candidate.hasPrimaryTarget = hasPrimaryTarget != 0;
		candidate.hasPreferredBase = hasPreferredBase != 0;
		candidate.attackCoolingDown = attackCoolingDown != 0;
		return candidate;
	}

	void writeOutput(uint8_t* out, int32_t candidateCount, int32_t winnerCandidateId, int32_t winnerCrystalEntityId,
	                  bool winnerFound, int32_t winnerPrimaryTargetEntityId, int32_t action, int32_t reasonFlags) {
		int32_t schemaOut = SCHEMA_VERSION;
		int32_t winnerFoundOut = winnerFound ? 1 : 0;
		std::memcpy(out + 0, &schemaOut, 4);
		std::memcpy(out + 4, &candidateCount, 4);
		std::memcpy(out + 8, &winnerCandidateId, 4);
		std::memcpy(out + 12, &winnerCrystalEntityId, 4);
		std::memcpy(out + 16, &winnerFoundOut, 4);
		std::memcpy(out + 20, &winnerPrimaryTargetEntityId, 4);
		std::memcpy(out + 24, &action, 4);
		std::memcpy(out + 28, &reasonFlags, 4);
	}

	// Bit-exact mirror of java.lang.Double.compare - see AutoCrystalPolicy.cpp's identical helper
	// for the full rationale. Duplicated locally rather than shared across translation units to
	// keep each policy file a self-contained, independently auditable slice.
	int64_t doubleToLongBitsJavaStyle(double value) {
		int64_t bits;
		std::memcpy(&bits, &value, 8);
		int32_t exponent = static_cast<int32_t>((bits >> 52) & 0x7FFLL);
		int64_t mantissa = bits & 0xFFFFFFFFFFFFFLL;
		if (exponent == 0x7FF && mantissa != 0) {
			return 0x7FF8000000000000LL;
		}
		return bits;
	}

	int compareDoubleJavaStyle(double a, double b) {
		if (a < b) return -1;
		if (a > b) return 1;
		int64_t bitsA = doubleToLongBitsJavaStyle(a);
		int64_t bitsB = doubleToLongBitsJavaStyle(b);
		if (bitsA == bitsB) return 0;
		return bitsA < bitsB ? -1 : 1;
	}

	// Exactly bestCrystalToBreak's four-key Comparator.comparingDouble(...).thenComparingDouble(...)
	// chain: aggregate target damage, then primary target damage, then preferred-base proximity
	// (0.0 for every candidate when there is no preferred base, matching Java's ternary being
	// evaluated the same way for every candidate rather than per-candidate), then player proximity.
	int compareCandidates(const Candidate& a, const Candidate& b, bool hasPreferredBase) {
		int c = compareDoubleJavaStyle(a.aggregateTargetDamage, b.aggregateTargetDamage);
		if (c != 0) return c;
		c = compareDoubleJavaStyle(a.primaryTargetDamage, b.primaryTargetDamage);
		if (c != 0) return c;
		double aBase = hasPreferredBase ? -a.preferredBaseDistanceSq : 0.0;
		double bBase = hasPreferredBase ? -b.preferredBaseDistanceSq : 0.0;
		c = compareDoubleJavaStyle(aBase, bBase);
		if (c != 0) return c;
		double aDist = -a.playerDistanceSq;
		double bDist = -b.playerDistanceSq;
		return compareDoubleJavaStyle(aDist, bDist);
	}

	int dispatch(int event, const uint8_t* input, size_t inputLen, uint8_t* output, size_t outputLen) {
		if (event != EVT_RANK_BREAK_CANDIDATES) {
			return STATUS_UNKNOWN_EVENT;
		}
		if (input == nullptr || output == nullptr) {
			return STATUS_NULL_BUFFER;
		}
		if (inputLen < static_cast<size_t>(INPUT_HEADER_BYTES)) {
			return STATUS_INPUT_SIZE_MISMATCH;
		}

		Header header = readHeader(input);
		if (header.schemaVersion != SCHEMA_VERSION) {
			return STATUS_SCHEMA_VERSION_MISMATCH;
		}
		if (header.candidateCount < 0) {
			return STATUS_INPUT_SIZE_MISMATCH;
		}

		size_t expectedInputLen = static_cast<size_t>(INPUT_HEADER_BYTES)
				+ static_cast<size_t>(header.candidateCount) * static_cast<size_t>(INPUT_CANDIDATE_BYTES);
		if (inputLen != expectedInputLen) {
			return STATUS_INPUT_SIZE_MISMATCH;
		}
		if (outputLen != static_cast<size_t>(OUTPUT_BYTES)) {
			return STATUS_OUTPUT_SIZE_MISMATCH;
		}

		int32_t candidateCount = header.candidateCount;

		std::vector<Candidate> candidates;
		candidates.reserve(static_cast<size_t>(candidateCount));
		for (int32_t i = 0; i < candidateCount; i++) {
			candidates.push_back(readCandidate(input, i));
		}

		// selfDamage <= maxSelfDamage && hasPrimaryTarget - exactly bestCrystalToBreak's two
		// .filter() calls. hasPrimaryTarget already encodes the per-target minimum-damage
		// filtering Java did while building the observation (scoreCrystal), so it is not redone
		// here.
		int32_t winnerIndex = -1;
		bool hasPreferredBase = candidateCount > 0 && candidates[0].hasPreferredBase;
		for (int32_t i = 0; i < candidateCount; i++) {
			const Candidate& candidate = candidates[static_cast<size_t>(i)];
			if (candidate.selfDamage > header.maxSelfDamage || !candidate.hasPrimaryTarget) {
				continue;
			}
			// Stream.max(comparator)'s reduce (Java's BinaryOperator.maxBy) keeps the earlier
			// accumulator on an exact tie - only replace on a STRICT improvement, same as here.
			if (winnerIndex < 0 || compareCandidates(candidate, candidates[static_cast<size_t>(winnerIndex)], hasPreferredBase) > 0) {
				winnerIndex = i;
			}
		}

		int32_t action = ACTION_NONE;
		int32_t reasonFlags = 0;
		int32_t winnerCandidateId = -1;
		int32_t winnerCrystalEntityId = -1;
		int32_t winnerPrimaryTargetEntityId = -1;
		bool winnerFound = winnerIndex >= 0;

		if (winnerFound) {
			const Candidate& winner = candidates[static_cast<size_t>(winnerIndex)];
			winnerCandidateId = winner.candidateId;
			winnerCrystalEntityId = winner.crystalEntityId;
			winnerPrimaryTargetEntityId = winner.primaryTargetEntityId;
		}

		if (header.eatingDefer) {
			action = ACTION_NONE;
			reasonFlags |= REASON_EATING_DEFER;
		} else if (!winnerFound) {
			action = ACTION_NONE;
			reasonFlags |= REASON_NO_ELIGIBLE_CANDIDATE;
		} else if (candidates[static_cast<size_t>(winnerIndex)].attackCoolingDown) {
			action = ACTION_NONE;
			reasonFlags |= REASON_ATTACK_COOLING_DOWN;
		} else if (!candidates[static_cast<size_t>(winnerIndex)].aimValid) {
			action = ACTION_NONE;
			reasonFlags |= REASON_AIM_INVALID;
		} else if (header.stageRequested && !header.immediateBreakSetting) {
			action = ACTION_STAGE_BREAK;
			reasonFlags |= REASON_STAGED;
		} else {
			action = ACTION_REQUEST_IMMEDIATE_BREAK;
		}

		writeOutput(output, candidateCount, winnerCandidateId, winnerCrystalEntityId, winnerFound,
				winnerPrimaryTargetEntityId, action, reasonFlags);

		return STATUS_OK;
	}
} } // namespace xing::autocrystalbreakpolicy
