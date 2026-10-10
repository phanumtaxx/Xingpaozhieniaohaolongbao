package dev.xingclient.manager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/** Minecraft collision queries requested by native movement utilities. Author: uint32. */
public final class MovementWorldAccess {
    public boolean blockCollision(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        var client = MinecraftClient.getInstance();
        return client.world != null && client.player != null
                && client.world.getBlockCollisions(client.player, new Box(minX, minY, minZ, maxX, maxY, maxZ)).iterator().hasNext();
    }
    public boolean air(int x, int y, int z) {
        var world = MinecraftClient.getInstance().world;
        return world != null && world.getBlockState(new BlockPos(x, y, z)).isAir();
    }
}
