package dev.starlit.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

public final class Texts {
    private Texts() {
    }

    public static Component tr(String key, Object... args) {
        return new TranslatableComponent(key, args);
    }

    public static Component literal(String text) {
        return new TextComponent(text);
    }
}
