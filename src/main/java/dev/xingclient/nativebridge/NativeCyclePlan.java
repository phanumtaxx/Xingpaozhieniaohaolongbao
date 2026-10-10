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
        int placementReason,
        List<Placement> placements) {

    public String statusName() {
        return switch (status) {
            case 0 -> "Cycle paused";
            case 1 -> "No target";
            case 2 -> "Target data unavailable";
            case 3 -> "No crystals in inventory";
            case 4 -> "World snapshot unavailable";
            case 5 -> "Break ready";
            case 6 -> "No eligible placement";
            case 7 -> "Placement planned";
            default -> "Unknown plan (" + status + ")";
        };
    }

    public boolean waitingForCrystalSpawn() {
        return placementReason == 4;
    }

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

    public String placementReasonName() {
        return switch (placementReason) {
            case 0 -> statusName();
            case 1 -> "No eligible placement";
            case 2 -> "Client unavailable";
            case 3 -> "No crystals in inventory";
            case 4 -> "Waiting for crystal spawn";
            case 5 -> "Waiting for damage sync";
            case 6 -> "No placement hand";
            case 7 -> "Eating defer";
            case 8 -> "Placement geometry invalid";
            case 9 -> "No reachable block face";
            case 10 -> "Placement aim invalid";
            case 11 -> "Placement staged";
            case 12 -> "Placement ready";
            default -> "Unknown placement reason (" + placementReason + ")";
        };
    }

    public record Placement(NativeCycleSnapshot.Position base, double score, double targetDamage, double selfDamage) {}
}
