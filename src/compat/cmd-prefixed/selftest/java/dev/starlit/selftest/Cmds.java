package dev.starlit.selftest;

import net.minecraft.server.MinecraftServer;

final class Cmds {
    private Cmds() {
    }

    static void run(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }
}
