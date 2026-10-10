package dev.xingclient.mixin;

import dev.xingclient.render.ResumeClock;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(RenderTickCounter.Dynamic.class)
public abstract class RenderTickCounterMixin implements ResumeClock {
    @Shadow private long lastTimeMillis;
    @Shadow private long timeMillis;
    @Shadow private float dynamicDeltaTicks;
    @Shadow private float fixedDeltaTicks;

    @Override
    public void xing$resetAfterPause(long nowMillis) {
        lastTimeMillis = nowMillis;
        timeMillis = nowMillis;
        dynamicDeltaTicks = 0;
        fixedDeltaTicks = 0;
    }
}
