package dev.starlit.selftest;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

final class Ui {
    private Ui() {
    }

    static void click(Screen screen, double x, double y) {
        screen.mouseClicked(x, y, 0);
        screen.mouseReleased(x, y, 0);
    }

    static int x(AbstractWidget widget) {
        return widget.getX();
    }

    static int y(AbstractWidget widget) {
        return widget.getY();
    }

    static void reveal(Screen screen, AbstractWidget widget) {
    }
}
