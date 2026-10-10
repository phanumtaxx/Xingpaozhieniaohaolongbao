package dev.xingclient;

import net.minecraft.util.math.BlockPos;

/** Current mining context supplied by a mining module to AutoCrystal. */
public final class MiningSyncState {
    private BlockPos target;
    private float progress;
    private boolean owned;
    private boolean autoMineOpening;

    public void update(BlockPos target, float progress, boolean owned, boolean autoMineOpening) {
        if (target == null || !Float.isFinite(progress)) {
            clear();
            return;
        }
        this.target = target.toImmutable();
        this.progress = Math.clamp(progress, 0.0F, 1.0F);
        this.owned = owned;
        this.autoMineOpening = autoMineOpening;
    }

    public void clear() {
        target = null;
        progress = 0.0F;
        owned = false;
        autoMineOpening = false;
    }

    public Opening eligibleOpening() {
        if (target == null || progress < 0.85F || !owned) return null;
        return new Opening(target, autoMineOpening ? 2.5 : 1.0);
    }

    public record Opening(BlockPos position, double scoreWeight) {}
}
