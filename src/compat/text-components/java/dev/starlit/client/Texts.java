package dev.starlit.client;

import net.minecraft.network.chat.Component;

public final class Texts {
    private Texts() {
    }

    public static Component tr(String key, Object... args) {
        return Component.translatable(key, args);
    }

    public static Component literal(String text) {
        return Component.literal(text);
    }
}
