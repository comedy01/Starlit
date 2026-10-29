package dev.starlit.client.gui;

import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

public abstract class StarlitOptionsScreen extends OptionsSubScreen {
    protected StarlitOptionsScreen(Screen lastScreen, Options options, Component title) {
        super(lastScreen, options, title);
    }

    protected void addHeader(Component text) {
        list.addHeader(text);
    }

    protected void addRow(AbstractWidget left, AbstractWidget right) {
        list.addSmall(left, right);
    }
}
