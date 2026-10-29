package dev.starlit.selftest;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

final class Labels {
    private Labels() {
    }

    static String key(Component text) {
        return text.getContents() instanceof TranslatableContents contents ? contents.getKey() : null;
    }

    static Object[] args(Component text) {
        return text.getContents() instanceof TranslatableContents contents ? contents.getArgs() : new Object[0];
    }
}
