package dev.starlit.selftest;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.ChatVisiblity;

import java.util.function.Consumer;
import java.util.function.Supplier;

final class Opts {
    private Opts() {
    }

    interface Opt<T> {
        T get();

        void set(T value);
    }

    private static <T> Opt<T> of(Supplier<T> getter, Consumer<T> setter) {
        return new Opt<>() {
            @Override
            public T get() {
                return getter.get();
            }

            @Override
            public void set(T value) {
                setter.accept(value);
            }
        };
    }

    static Opt<Integer> fov(Minecraft mc) {
        return of(() -> (int) Math.round(mc.options.fov), value -> mc.options.fov = value);
    }

    static Opt<CloudStatus> cloudStatus(Minecraft mc) {
        return of(() -> mc.options.renderClouds, value -> mc.options.renderClouds = value);
    }

    static Opt<Integer> renderDistance(Minecraft mc) {
        return of(() -> mc.options.renderDistance, value -> {
            mc.options.renderDistance = value;
            mc.levelRenderer.needsUpdate();
        });
    }

    static Opt<Integer> framerateLimit(Minecraft mc) {
        return of(() -> mc.options.framerateLimit, value -> {
            mc.options.framerateLimit = value;
            mc.getWindow().setFramerateLimit(value);
        });
    }

    static Opt<Boolean> enableVsync(Minecraft mc) {
        return of(() -> mc.options.enableVsync, value -> {
            mc.options.enableVsync = value;
            Window window = mc.getWindow();
            window.updateVsync(value);
        });
    }

    static Opt<ChatVisiblity> chatVisibility(Minecraft mc) {
        return of(() -> mc.options.chatVisibility, value -> mc.options.chatVisibility = value);
    }
}
