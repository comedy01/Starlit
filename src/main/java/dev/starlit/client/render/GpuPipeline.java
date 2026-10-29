package dev.starlit.client.render;

public final class GpuPipeline {
    // True while Starlit draws its sky on top of a shader pack's finished frame, depth-tested against the world.
    public static boolean overlay;

    public enum Blend {
        ADDITIVE, MULTIPLY, TRANSLUCENT, PREMULTIPLIED
    }

    public final String name;
    public final String vertex;
    public final String fragment;
    public final boolean textured;
    public final Blend blend;
    public Object handle;
    public Object overlayHandle;
    public boolean failed;

    public GpuPipeline(String name, String vertex, String fragment, boolean textured, Blend blend) {
        this.name = name;
        this.vertex = vertex;
        this.fragment = fragment;
        this.textured = textured;
        this.blend = blend;
    }
}
