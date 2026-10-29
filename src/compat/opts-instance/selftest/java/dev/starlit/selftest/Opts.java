package dev.starlit.selftest;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.world.entity.player.ChatVisiblity;

final class Opts {
    private Opts() {
    }

    interface Opt<T> {
        T get();

        void set(T value);
    }

    private static <T> Opt<T> wrap(OptionInstance<T> option) {
        return new Opt<>() {
            @Override
            public T get() {
                return option.get();
            }

            @Override
            public void set(T value) {
                option.set(value);
            }
        };
    }

    static Opt<Integer> fov(Minecraft mc) {
        return wrap(mc.options.fov());
    }

    static Opt<CloudStatus> cloudStatus(Minecraft mc) {
        return wrap(mc.options.cloudStatus());
    }

    static Opt<Integer> renderDistance(Minecraft mc) {
        return wrap(mc.options.renderDistance());
    }

    static Opt<Integer> framerateLimit(Minecraft mc) {
        return wrap(mc.options.framerateLimit());
    }

    static Opt<Boolean> enableVsync(Minecraft mc) {
        return wrap(mc.options.enableVsync());
    }

    static Opt<ChatVisiblity> chatVisibility(Minecraft mc) {
        return wrap(mc.options.chatVisibility());
    }
}
