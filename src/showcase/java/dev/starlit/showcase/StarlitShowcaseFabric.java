package dev.starlit.showcase;

import net.fabricmc.api.ClientModInitializer;

public final class StarlitShowcaseFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Showcase.start();
    }
}
