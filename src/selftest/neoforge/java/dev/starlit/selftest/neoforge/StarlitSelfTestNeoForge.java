package dev.starlit.selftest.neoforge;

import dev.starlit.selftest.StarlitSelfTest;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "starlit_selftest", dist = Dist.CLIENT)
public final class StarlitSelfTestNeoForge {
    public StarlitSelfTestNeoForge() {
        StarlitSelfTest test = new StarlitSelfTest();
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> test.tick());
    }
}
