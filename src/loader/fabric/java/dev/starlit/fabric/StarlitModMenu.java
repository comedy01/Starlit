package dev.starlit.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.starlit.client.gui.StarlitSettingsScreen;
import net.minecraft.client.Minecraft;

public final class StarlitModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new StarlitSettingsScreen(parent, Minecraft.getInstance().options);
    }
}
