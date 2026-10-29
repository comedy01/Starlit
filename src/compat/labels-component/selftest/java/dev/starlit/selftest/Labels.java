package dev.starlit.selftest;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;

final class Labels {
    private Labels() {
    }

    static String key(Component text) {
        return text instanceof TranslatableComponent translatable ? translatable.getKey() : null;
    }

    static Object[] args(Component text) {
        return text instanceof TranslatableComponent translatable ? translatable.getArgs() : new Object[0];
    }
}
