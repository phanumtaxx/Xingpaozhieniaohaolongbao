package dev.xingclient.nativebridge;

import java.util.List;

public record NativeCyclePlan(
        int status,
        int action,
        int breakAction,
        int breakCrystalEntityId,
        int breakTargetEntityId,
        int breakReason,
        boolean placementRequiresSuccessfulBreak,
        List<Placement> placements) {

    public String breakReasonName() {
        return switch (breakReason) {
            case 0 -> "None";
            case 1 -> "Client unavailable";
            case 2 -> "No eligible crystal";
            case 3 -> "Reservation denied";
            case 4 -> "Eating defer";
            case 5 -> "Attack cooling down";
            case 6 -> "Crystal missing";
            case 7 -> "Target missing";
            case 8 -> "Out of range";
            case 9 -> "Damage rejected";
            case 10 -> "Aim invalid";
            case 11 -> "Stage requested";
            case 12 -> "Break ready";
            default -> "Unknown (" + breakReason + ")";
        };
    }

    public record Placement(NativeCycleSnapshot.Position base, double score, double targetDamage, double selfDamage) {}
}
