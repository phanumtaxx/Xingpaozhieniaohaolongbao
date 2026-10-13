package dev.xingclient.module;

import dev.xingclient.XingClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Minecraft input adapter for packet mining. Author: uint32. */
public final class NativeSpeedMineModule extends Module {
    public NativeSpeedMineModule() { super("speed_mine", "SpeedMine", ModuleCategory.COMBAT); }

    public boolean attack(BlockPos position, Direction face) {
        return isEnabled() && XingClient.INSTANCE.managers.mining.request(actionOwner, position, face,
                XingClient.INSTANCE.settings.speedMine);
    }

    public boolean ownsTarget() { return XingClient.INSTANCE.managers.mining.isOwnedBy(actionOwner); }

    @Override
    public String status() { return isEnabled() ? XingClient.INSTANCE.managers.mining.status() : "Disabled"; }
}
