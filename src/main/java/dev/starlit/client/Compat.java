package dev.starlit.client;

import java.lang.reflect.Method;

public final class Compat {
    private static boolean looked;
    private static Object irisApi;
    private static Method shaderPackInUse;
    private static boolean dhLooked;
    private static Method pipelineManager;
    private static Method pipelineNullable;
    private static Method dhCompat;
    private static Method dhDepthTex;
    private static boolean ofLooked;
    private static java.lang.reflect.Field ofLoaded;
    private static boolean ofDepthLooked;
    private static java.lang.reflect.Field ofDfb;
    private static java.lang.reflect.Field ofDepthTextures;
    private static boolean voxyLooked;
    private static Method voxySystem;
    private static java.lang.reflect.Field voxyPipeline;
    private static Class<?> voxyIrisPipeline;
    private static Method voxyIrisData;
    private static java.lang.reflect.Field voxyIrisDataField;
    private static java.lang.reflect.Field voxyVanillaDepth;
    private static java.lang.reflect.Field voxyFb;
    private static java.lang.reflect.Field voxyProperties;
    private static Method voxyClearDepth;
    private static Method voxyDepthTex;
    private static java.lang.reflect.Field voxyTexId;
    private static float farSky;

    private Compat() {
    }

    public static boolean shaderPackInUse() {
        return irisShaders() || optifineShaders();
    }

    // OptiFine (via OptiFabric on Fabric) keeps its shader state in static fields on net.optifine.shaders.Shaders.
    public static boolean optifineShaders() {
        if (!ofLooked) {
            ofLooked = true;
            try {
                ofLoaded = Class.forName("net.optifine.shaders.Shaders").getField("shaderPackLoaded");
            } catch (ReflectiveOperationException | LinkageError e) {
                ofLoaded = null;
            }
        }
        if (ofLoaded == null) {
            return false;
        }
        try {
            return ofLoaded.getBoolean(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            ofLoaded = null;
            return false;
        }
    }

    public static boolean optifineShadowPass() {
        try {
            return optifineShaders() && Class.forName("net.optifine.shaders.Shaders").getField("isShadowPass").getBoolean(null);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return false;
        }
    }

    // OptiFine's depthtex0 (sky = 1), which survives the shader pack's final pass; 0 when unavailable.
    public static int optifineDepthTexture() {
        if (!ofDepthLooked) {
            ofDepthLooked = true;
            try {
                ofDfb = Class.forName("net.optifine.shaders.Shaders").getDeclaredField("dfb");
                ofDfb.setAccessible(true);
                ofDepthTextures = Class.forName("net.optifine.shaders.ShadersFramebuffer").getDeclaredField("depthTextures");
                ofDepthTextures.setAccessible(true);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                ofDfb = null;
            }
        }
        if (ofDfb == null) {
            return 0;
        }
        try {
            Object dfb = ofDfb.get(null);
            Object textures = dfb == null ? null : ofDepthTextures.get(dfb);
            return textures instanceof java.nio.IntBuffer ? ((java.nio.IntBuffer) textures).get(0) : 0;
        } catch (ReflectiveOperationException | RuntimeException e) {
            ofDfb = null;
            return 0;
        }
    }

    private static boolean irisShaders() {
        if (!looked) {
            looked = true;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisApi = api.getMethod("getInstance").invoke(null);
                shaderPackInUse = api.getMethod("isShaderPackInUse");
            } catch (ReflectiveOperationException | LinkageError e) {
                irisApi = null;
            }
        }
        if (irisApi == null) {
            return false;
        }
        try {
            return (Boolean) shaderPackInUse.invoke(irisApi);
        } catch (ReflectiveOperationException | RuntimeException e) {
            irisApi = null;
            return false;
        }
    }

    // Iris keeps Distant Horizons' depth in its own texture; 0 when DH, Iris or a shader pack is absent.
    public static int dhDepthTexture() {
        if (!dhLooked) {
            dhLooked = true;
            try {
                pipelineManager = Class.forName("net.irisshaders.iris.Iris").getMethod("getPipelineManager");
                pipelineNullable = Class.forName("net.irisshaders.iris.pipeline.PipelineManager").getMethod("getPipelineNullable");
                dhCompat = Class.forName("net.irisshaders.iris.pipeline.IrisRenderingPipeline").getMethod("getDHCompat");
                dhDepthTex = Class.forName("net.irisshaders.iris.compat.dh.DHCompat").getMethod("getDepthTex");
            } catch (ReflectiveOperationException | LinkageError e) {
                pipelineManager = null;
            }
        }
        if (pipelineManager == null) {
            return 0;
        }
        try {
            Object pipeline = pipelineNullable.invoke(pipelineManager.invoke(null));
            if (!dhCompat.getDeclaringClass().isInstance(pipeline)) {
                return 0;
            }
            Object compat = dhCompat.invoke(pipeline);
            return compat == null ? 0 : (Integer) dhDepthTex.invoke(compat);
        } catch (ReflectiveOperationException | RuntimeException e) {
            pipelineManager = null;
            return 0;
        }
    }

    // Depth texture holding far terrain the main depth buffer is missing (DH or Voxy under an Iris shader pack); 0 when none.
    // farSky() is the value that texture holds where there is no terrain; negative means the far end of the main depth range.
    public static int farDepthTexture() {
        int dh = dhDepthTexture();
        if (dh > 0) {
            farSky = -1.0f;
            return dh;
        }
        return irisShaders() ? voxyDepthTexture() : 0;
    }

    public static float farSky() {
        return farSky;
    }

    // Voxy under Iris renders LODs into its own framebuffer (vanilla terrain blitted in) unless the pack asks for vanilla depth.
    private static int voxyDepthTexture() {
        if (!voxyLooked) {
            voxyLooked = true;
            try {
                // Voxy 0.2.20 names; 0.2.16 (1.21.11) has IGetVoxyRenderSystem and only a private data field.
                Class<?> holder;
                try {
                    holder = Class.forName("me.cortex.voxy.client.core.IVoxyRenderSystemHolder");
                } catch (ClassNotFoundException e) {
                    holder = Class.forName("me.cortex.voxy.client.core.IGetVoxyRenderSystem");
                }
                voxySystem = holder.getMethod("getNullable");
                voxyPipeline = Class.forName("me.cortex.voxy.client.core.VoxyRenderSystem").getDeclaredField("pipeline");
                voxyPipeline.setAccessible(true);
                voxyIrisPipeline = Class.forName("me.cortex.voxy.client.core.IrisVoxyRenderPipeline");
                try {
                    voxyIrisData = voxyIrisPipeline.getMethod("_getData");
                } catch (NoSuchMethodException e) {
                    voxyIrisDataField = voxyIrisPipeline.getDeclaredField("data");
                    voxyIrisDataField.setAccessible(true);
                }
                voxyVanillaDepth = Class.forName("me.cortex.voxy.client.iris.IrisVoxyRenderPipelineData").getField("renderToVanillaDepth");
                Class<?> abstractPipeline = Class.forName("me.cortex.voxy.client.core.AbstractRenderPipeline");
                voxyFb = abstractPipeline.getField("fb");
                voxyProperties = abstractPipeline.getField("properties");
                voxyClearDepth = Class.forName("me.cortex.voxy.client.core.RenderProperties").getMethod("clearDepth");
                voxyDepthTex = Class.forName("me.cortex.voxy.client.core.rendering.util.DepthFramebuffer").getMethod("getDepthTex");
                voxyTexId = Class.forName("me.cortex.voxy.client.core.gl.GlTexture").getField("id");
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                voxySystem = null;
            }
        }
        if (voxySystem == null) {
            return 0;
        }
        try {
            Object system = voxySystem.invoke(null);
            Object pipeline = system == null ? null : voxyPipeline.get(system);
            if (!voxyIrisPipeline.isInstance(pipeline)) {
                return 0;
            }
            Object data = voxyIrisData != null ? voxyIrisData.invoke(pipeline) : voxyIrisDataField.get(pipeline);
            if (data == null || voxyVanillaDepth.getBoolean(data)) {
                return 0;
            }
            Object texture = voxyDepthTex.invoke(voxyFb.get(pipeline));
            if (texture == null) {
                return 0;
            }
            farSky = (Float) voxyClearDepth.invoke(voxyProperties.get(pipeline));
            return voxyTexId.getInt(texture);
        } catch (ReflectiveOperationException | RuntimeException e) {
            voxySystem = null;
            return 0;
        }
    }
}
