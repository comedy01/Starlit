package dev.starlit.selftest.fabric;

import dev.starlit.selftest.StarlitSelfTest;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class StarlitSelfTestFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        StarlitSelfTest test = new StarlitSelfTest();
        ClientTickEvents.END_CLIENT_TICK.register(client -> test.tick());
    }
}
