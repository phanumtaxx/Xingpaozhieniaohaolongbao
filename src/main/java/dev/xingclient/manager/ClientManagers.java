package dev.xingclient.manager;

import dev.xingclient.event.EventBus;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;

public final class ClientManagers {
    private final NativeManagerBridge bridge = new NativeManagerBridge();
    private final NativeCombatBridge combatBridge = new NativeCombatBridge();
    private final NativeWorldStateBridge worldStateBridge = new NativeWorldStateBridge();
    public final CombatActionScheduler scheduler = new CombatActionScheduler(combatBridge);
    public final CombatRateLimiter rates = new CombatRateLimiter(combatBridge);
    public final CombatActionState combatActions = new CombatActionState(bridge);
    public final NetworkManager network;
    public final InventoryManager inventory;
    public final RotationManager rotations;
    public final InteractionManager interactions;
    public final ServerStateTracker serverState;
    public final WorldPredictionLedger predictions;
    private ClientWorld world;
    private ClientPlayNetworkHandler connection;

    public ClientManagers(EventBus events) {
        network = new NetworkManager(combatBridge, scheduler, events);
        serverState = new ServerStateTracker(worldStateBridge, events);
        predictions = new WorldPredictionLedger(worldStateBridge, network);
        inventory = new InventoryManager(bridge, network, scheduler, serverState, events);
        rotations = new RotationManager(bridge, network, scheduler);
        interactions = new InteractionManager(network, inventory, scheduler, rotations);
    }

    public void tick(MinecraftClient client) {
        synchronizeWorld(client);
        combatActions.tick();
        network.tick();
        scheduler.beginTick(client.world != null && client.player != null && client.getNetworkHandler() != null);
        if (client.world == null || client.player == null || client.getNetworkHandler() == null) return;
        serverState.tick(client);
        inventory.tick();
        rotations.tick();
    }

    public void synchronizeWorld(MinecraftClient client) {
        CombatActionScheduler.requireClientThread();
        if (world != client.world || connection != client.getNetworkHandler()) {
            scheduler.reset();
            network.reset();
            bridge.reset();
            inventory.reset();
            rotations.reset();
            worldStateBridge.reset();
            world = client.world;
            connection = client.getNetworkHandler();
        }
    }

    public void release(ActionOwner owner) {
        try { scheduler.cancelOwner(owner); }
        finally {
            try { inventory.release(owner); }
            finally {
                try { rotations.release(owner); }
                finally { predictions.clearOwner(owner); }
            }
        }
    }

    public void onServerCorrection() { scheduler.reset(); rotations.corrected(); }
}
