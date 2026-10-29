package dev.starlit.selftest;

import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;

final class Lists {
    private Lists() {
    }

    static boolean shows(AbstractSelectionList<?> list, AbstractWidget widget) {
        return true;
    }

    static void reveal(AbstractSelectionList<?> list, AbstractWidget widget) {
    }
}
