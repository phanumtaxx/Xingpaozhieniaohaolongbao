package dev.xingclient.module;

import dev.xingclient.XingClient;
import dev.xingclient.event.ClientTickEvent;
import dev.xingclient.event.EventBus;
import dev.xingclient.nativebridge.XingNativeBridge;
import java.nio.ByteBuffer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Minecraft inventory snapshot adapter. Author: uint32. */
public final class NativeAutoRefillModule extends Module {
    private static final int TICK = 0, REFILL_COMPLETED = 1, RESET = 2;
    private static final Item[] REFILL_ITEMS = { Items.END_CRYSTAL, Items.OBSIDIAN,
            Items.FIREWORK_ROCKET, Items.EXPERIENCE_BOTTLE, Items.GOLDEN_APPLE, Items.ENDER_PEARL };
    private final EventBus events;
    private final ByteBuffer input = XingNativeBridge.allocate(160);
    private final ByteBuffer output = XingNativeBridge.allocate(8);
    private EventBus.Subscription ticks;
    private ClientWorld world;
    private ClientPlayerEntity player;

    public NativeAutoRefillModule(EventBus events) {
        super("auto_refill", "Auto Refill", ModuleCategory.PLAYER);
        this.events = events;
    }

    @Override
    protected void onEnable() {
        XingNativeBridge.loadLibrary();
        ticks = events.subscribe(ClientTickEvent.class, this::tick);
    }

    @Override
    protected void onDisable() {
        if (ticks != null) { ticks.close(); ticks = null; }
    }

    private void tick(ClientTickEvent event) {
        if (event.phase() != ClientTickEvent.Phase.END) return;
        var client = MinecraftClient.getInstance();
        if (world != client.world || player != client.player) {
            dispatch(RESET);
            world = client.world;
            player = client.player;
        }
        var settings = XingClient.INSTANCE.settings.autoRefill;
        boolean ready = client.player != null && client.interactionManager != null && client.currentScreen == null
                && client.player.currentScreenHandler == client.player.playerScreenHandler
                && client.player.currentScreenHandler.getCursorStack().isEmpty();
        input.clear();
        input.putInt(ready ? 1 : 0).putInt(settings.delay).putInt(settings.threshold).putInt(settings.itemMask());
        for (int slot = 0; slot < 9; slot++) {
            ItemStack target = ready ? client.player.getInventory().getStack(slot) : ItemStack.EMPTY;
            int matches = 0;
            if (ready && !target.isEmpty()) {
                for (int source = 9; source < 36; source++) {
                    ItemStack stack = client.player.getInventory().getStack(source);
                    if (!stack.isEmpty() && ItemStack.areItemsAndComponentsEqual(target, stack))
                        matches |= 1 << (source - 9);
                }
            }
            input.putInt(target.getCount()).putInt(target.getMaxCount()).putInt(itemKind(target)).putInt(matches);
        }
        dispatch(TICK);
        int source = output.getInt(0), target = output.getInt(4);
        if (source >= 0 && XingClient.INSTANCE.managers.inventory.refill(actionOwner, source, target)) dispatch(REFILL_COMPLETED);
    }

    private static int itemKind(ItemStack stack) {
        for (int i = 0; i < REFILL_ITEMS.length; i++) if (stack.isOf(REFILL_ITEMS[i])) return i;
        return -1;
    }

    private void dispatch(int event) {
        if (XingNativeBridge.dispatch(XingNativeBridge.AUTO_REFILL, event, input, output) != 0)
            throw new IllegalStateException("Native Auto Refill rejected its inventory snapshot");
    }
}
