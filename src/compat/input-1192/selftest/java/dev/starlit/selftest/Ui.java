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
        return widget.x;
    }

    static int y(AbstractWidget widget) {
        return widget.y;
    }

    static void reveal(Screen screen, AbstractWidget widget) {
        for (int i = 0; i < 40 && !widget.visible; i++) {
            screen.mouseScrolled(0.0, 0.0, 1.0);
        }
        for (int i = 0; i < 80 && !widget.visible; i++) {
            screen.mouseScrolled(0.0, 0.0, -1.0);
        }
    }
}
