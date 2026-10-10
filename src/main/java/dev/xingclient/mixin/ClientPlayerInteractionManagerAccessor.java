package dev.xingclient.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientPlayerInteractionManager.class)
public interface ClientPlayerInteractionManagerAccessor {
    @Accessor("lastSelectedSlot")
    void xing$setLastSelectedSlot(int slot);

    @Accessor("currentBreakingPos")
    BlockPos xing$getCurrentBreakingPos();

    @Accessor("currentBreakingProgress")
    float xing$getCurrentBreakingProgress();

    @Accessor("breakingBlock")
    boolean xing$isBreakingBlock();
}
