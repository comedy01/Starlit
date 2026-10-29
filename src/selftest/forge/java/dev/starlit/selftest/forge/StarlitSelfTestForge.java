package dev.starlit.selftest.forge;

import dev.starlit.selftest.StarlitSelfTest;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("starlit_selftest")
public final class StarlitSelfTestForge {
    public StarlitSelfTestForge() {
        StarlitSelfTest test = new StarlitSelfTest();
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                test.tick();
            }
        });
    }
}
