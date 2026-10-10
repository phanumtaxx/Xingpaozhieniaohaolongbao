#include "pch.h"
#include "XingNativeBridge.h"

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <vector>

// Shadow evaluation for AutoCrystal's candidate ranking/scoring slice (see
// CrystalPlacementScorer/CrystalPlacementSelector.java and AutoCrystalShadowEngine.java). Pure
// arithmetic over an already-computed, already-scanned observation snapshot - no world/entity
// access, no packets, no Minecraft objects. Java stays fully authoritative; this module
// independently reproduces Java's scoring formula and stable ranking/tie-break over the same
// scan-order observations Java computed, and reports its own ranking + winner back for
// shadow-mode comparison logging. It never sees Java's own score or ranked order as input.
//
// Wire format is a private, versioned, explicitly-offset contract with the Java side - never a
// raw struct overlay (endianness and padding are not portable across compilers/ABIs, and the
// direct ByteBuffer on the Java side is always little-endian regardless of host byte order).
namespace xing { namespace autocrystalpolicy {
	constexpr int32_t SCHEMA_VERSION = 1;

	constexpr int32_t INPUT_HEADER_BYTES = 64;
	constexpr int32_t INPUT_CANDIDATE_BYTES = 48;
	constexpr int32_t OUTPUT_HEADER_BYTES = 32;
	constexpr int32_t OUTPUT_CANDIDATE_BYTES = 24;

	// Status codes returned to Java (see NativeBridge.cpp's nativeDispatch and
	// AutoCrystalShadowEngine.evaluateInternal's status check). 0 is the only success code -
	// everything else means Java must skip this shadow evaluation rather than trust partial output.
	constexpr int STATUS_OK = 0;
	constexpr int STATUS_UNKNOWN_EVENT = -1;
	constexpr int STATUS_SCHEMA_VERSION_MISMATCH = -2;
	constexpr int STATUS_INPUT_SIZE_MISMATCH = -3;
	constexpr int STATUS_OUTPUT_SIZE_MISMATCH = -4;
	constexpr int STATUS_NULL_BUFFER = -5;

	constexpr int32_t EVT_RANK_CANDIDATES = 0;

	struct Header {
		int32_t schemaVersion;
		int32_t candidateCount;
		double targetDamageWeight;
		double selfDamageWeight;
		double packetMineOpeningWeight;
		double antiPhaseBoost;
		double effectiveMinDamage;
		double maxSelfDamage;
		double selfHealth;
	};

	struct Candidate {
		int32_t candidateId;
		double targetDistanceSq;
		double selfDistanceSq;
		double targetDamage;
		double selfDamage;
		bool openingEligible;
		bool antiPhaseEligible;
	};

	struct Scored {
		int32_t candidateId;
		double score;
		bool openingBoosted;
		bool antiPhaseBoosted;
		double targetDamage;
		double selfDamage;
	};

	Header readHeader(const uint8_t* in) {
		Header header{};
		std::memcpy(&header.schemaVersion, in + 0, 4);
		std::memcpy(&header.candidateCount, in + 4, 4);
		std::memcpy(&header.targetDamageWeight, in + 8, 8);
		std::memcpy(&header.selfDamageWeight, in + 16, 8);
		std::memcpy(&header.packetMineOpeningWeight, in + 24, 8);
		std::memcpy(&header.antiPhaseBoost, in + 32, 8);
		std::memcpy(&header.effectiveMinDamage, in + 40, 8);
		std::memcpy(&header.maxSelfDamage, in + 48, 8);
		std::memcpy(&header.selfHealth, in + 56, 8);
		return header;
	}

	Candidate readCandidate(const uint8_t* in, int32_t index) {
		const uint8_t* base = in + INPUT_HEADER_BYTES + static_cast<size_t>(index) * INPUT_CANDIDATE_BYTES;
		Candidate candidate{};
		int32_t openingFlag = 0;
		int32_t antiPhaseFlag = 0;
		std::memcpy(&candidate.candidateId, base + 0, 4);
		std::memcpy(&candidate.targetDistanceSq, base + 8, 8);
		std::memcpy(&candidate.selfDistanceSq, base + 16, 8);
		std::memcpy(&candidate.targetDamage, base + 24, 8);
		std::memcpy(&candidate.selfDamage, base + 32, 8);
		std::memcpy(&openingFlag, base + 40, 4);
		std::memcpy(&antiPhaseFlag, base + 44, 4);
		candidate.openingEligible = openingFlag != 0;
		candidate.antiPhaseEligible = antiPhaseFlag != 0;
		return candidate;
	}

	void writeScored(uint8_t* out, int32_t index, const Scored& scored) {
		uint8_t* base = out + OUTPUT_HEADER_BYTES + static_cast<size_t>(index) * OUTPUT_CANDIDATE_BYTES;
		int32_t reserved = 0;
		int32_t openingFlag = scored.openingBoosted ? 1 : 0;
		int32_t antiPhaseFlag = scored.antiPhaseBoosted ? 1 : 0;
		std::memcpy(base + 0, &scored.candidateId, 4);
		std::memcpy(base + 4, &reserved, 4);
		std::memcpy(base + 8, &scored.score, 8);
		std::memcpy(base + 16, &openingFlag, 4);
		std::memcpy(base + 20, &antiPhaseFlag, 4);
	}

	void writeOutputHeader(uint8_t* out, int32_t candidateCount, int32_t winnerCandidateId,
	                        bool winnerFound, double winnerScore, bool winnerOpeningBoosted,
	                        bool winnerAntiPhaseBoosted) {
		int32_t schemaOut = SCHEMA_VERSION;
		int32_t winnerFoundOut = winnerFound ? 1 : 0;
		int32_t winnerOpeningOut = winnerOpeningBoosted ? 1 : 0;
		int32_t winnerAntiPhaseOut = winnerAntiPhaseBoosted ? 1 : 0;
		std::memcpy(out + 0, &schemaOut, 4);
		std::memcpy(out + 4, &candidateCount, 4);
		std::memcpy(out + 8, &winnerCandidateId, 4);
		std::memcpy(out + 12, &winnerFoundOut, 4);
		std::memcpy(out + 16, &winnerScore, 8);
		std::memcpy(out + 24, &winnerOpeningOut, 4);
		std::memcpy(out + 28, &winnerAntiPhaseOut, 4);
	}

	// Bit-exact mirror of java.lang.Double.compare: NaN compares as the single largest value
	// (canonicalized, so all NaN bit patterns compare equal to each other), -0.0 compares strictly
	// less than +0.0, everything else follows normal IEEE-754 ordering. A naive `<`/`>` comparator
	// would silently disagree with Java on any NaN or signed-zero input, which score arithmetic
	// here should never produce in practice - but "should never" is exactly the case a shadow
	// engine exists to catch, so the comparator itself must not introduce its own divergence.
	int64_t doubleToLongBitsJavaStyle(double value) {
		int64_t bits;
		std::memcpy(&bits, &value, 8);
		int32_t exponent = static_cast<int32_t>((bits >> 52) & 0x7FFLL);
		int64_t mantissa = bits & 0xFFFFFFFFFFFFFLL;
		if (exponent == 0x7FF && mantissa != 0) {
			return 0x7FF8000000000000LL; // canonical NaN, matches Double.doubleToLongBits
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

	// Exactly CrystalPlacementScorer.scoreFromObservation's arithmetic, same operation order, same
	// double precision throughout (no float narrowing anywhere in this path on either side).
	Scored score(const Candidate& candidate, const Header& header) {
		double distancePenalty = std::sqrt(candidate.targetDistanceSq) * 0.05;
		double value = candidate.targetDamage * header.targetDamageWeight
				- candidate.selfDamage * header.selfDamageWeight
				- distancePenalty;
		if (candidate.openingEligible) {
			value += header.packetMineOpeningWeight;
		}
		if (candidate.antiPhaseEligible) {
			value += header.antiPhaseBoost;
		}
		Scored scored{};
		scored.candidateId = candidate.candidateId;
		scored.score = value;
		scored.openingBoosted = candidate.openingEligible;
		scored.antiPhaseBoosted = candidate.antiPhaseEligible;
		scored.targetDamage = candidate.targetDamage;
		scored.selfDamage = candidate.selfDamage;
		return scored;
	}

	int dispatch(int event, const uint8_t* input, size_t inputLen, uint8_t* output, size_t outputLen) {
		if (event != EVT_RANK_CANDIDATES) {
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

		size_t expectedOutputLen = static_cast<size_t>(OUTPUT_HEADER_BYTES)
				+ static_cast<size_t>(header.candidateCount) * static_cast<size_t>(OUTPUT_CANDIDATE_BYTES);
		if (outputLen != expectedOutputLen) {
			return STATUS_OUTPUT_SIZE_MISMATCH;
		}

		int32_t candidateCount = header.candidateCount;

		std::vector<Scored> scored;
		scored.reserve(static_cast<size_t>(candidateCount));
		for (int32_t i = 0; i < candidateCount; i++) {
			Candidate candidate = readCandidate(input, i);
			scored.push_back(score(candidate, header));
		}

		// Stable sort, descending by score using Java's exact total order. std::stable_sort leaves
		// equal-scoring candidates in their original (scan) order, matching Java's Stream.sorted -
		// this is the tie-break, not an explicit candidateId comparison.
		std::stable_sort(scored.begin(), scored.end(), [](const Scored& a, const Scored& b) {
			return compareDoubleJavaStyle(a.score, b.score) > 0;
		});

		for (int32_t i = 0; i < candidateCount; i++) {
			writeScored(output, i, scored[static_cast<size_t>(i)]);
		}

		int32_t winnerCandidateId = -1;
		bool winnerFound = false;
		double winnerScore = 0.0;
		bool winnerOpeningBoosted = false;
		bool winnerAntiPhaseBoosted = false;
		for (const Scored& candidate : scored) {
			if (candidate.targetDamage >= header.effectiveMinDamage
					&& candidate.selfDamage <= header.maxSelfDamage
					&& candidate.selfDamage + 2.0 < header.selfHealth) {
				winnerCandidateId = candidate.candidateId;
				winnerFound = true;
				winnerScore = candidate.score;
				winnerOpeningBoosted = candidate.openingBoosted;
				winnerAntiPhaseBoosted = candidate.antiPhaseBoosted;
				break;
			}
		}

		writeOutputHeader(output, candidateCount, winnerCandidateId, winnerFound, winnerScore,
				winnerOpeningBoosted, winnerAntiPhaseBoosted);

		return STATUS_OK;
	}
} } // namespace xing::autocrystalpolicy
