package dev.starlit.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.starlit.client.StarlitClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class Gpu {
    private static final Map<String, ShaderInstance> SHADERS = new HashMap<>();
    private static final float[] VALUES = new float[16];
    private static final FloatBuffer SCRATCH = FloatBuffer.wrap(VALUES);
    private static final int GL_LEQUAL = 515;
    private static com.mojang.math.Matrix4f overlayProjection;

    private Gpu() {
    }

    public static ResourceLocation id(String name) {
        return new ResourceLocation(StarlitClient.MOD_ID, name);
    }

    public static VertexFormat format(boolean textured) {
        return textured ? DefaultVertexFormat.POSITION_TEX_COLOR : DefaultVertexFormat.POSITION;
    }

    public static void loaded(String name, ShaderInstance shader) {
        SHADERS.put(name, shader);
    }

    public static Matrix4f joml(com.mojang.math.Matrix4f matrix) {
        SCRATCH.clear();
        matrix.store(SCRATCH);
        return new Matrix4f().set(VALUES);
    }

    public static com.mojang.math.Matrix4f mojang(Matrix4f matrix) {
        matrix.get(VALUES);
        SCRATCH.clear();
        com.mojang.math.Matrix4f result = new com.mojang.math.Matrix4f();
        result.load(SCRATCH);
        return result;
    }

    private static void blend(GpuPipeline.Blend blend) {
        RenderSystem.enableBlend();
        switch (blend) {
            case ADDITIVE -> RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
            case MULTIPLY -> RenderSystem.blendFunc(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ZERO);
            case TRANSLUCENT -> RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            case PREMULTIPLIED -> RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        }
    }

    public static boolean ready(GpuPipeline pipeline) {
        pipeline.handle = SHADERS.get(pipeline.name);
        return pipeline.handle != null;
    }

    public static boolean canOverlay() {
        return true;
    }

    public static Object projection() {
        return RenderSystem.getProjectionMatrix().copy();
    }

    public static Object beginOverlay(Object projection) {
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        DhMask.apply(target.getDepthTextureId(), 0.0f);
        target.bindWrite(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL_LEQUAL);
        // Shader packs leave sky pixels at the cleared depth 1.0; pin every overlay fragment there so it only lands on sky.
        GL11.glDepthRange(1.0, 1.0);
        overlayProjection = (com.mojang.math.Matrix4f) projection;
        return target;
    }

    public static void endOverlay(Object pass) {
        overlayProjection = null;
        GL11.glDepthRange(0.0, 1.0);
        RenderSystem.depthFunc(GL_LEQUAL);
        RenderSystem.disableDepthTest();
    }

    public static void draw(Object frame, GpuPipeline pipeline, GpuMesh mesh, int indexCount, int baseVertex,
                            Matrix4f modelView, Vector4f color, Vector3f offset, Matrix4f extra) {
        VertexBuffer buffer = (VertexBuffer) mesh.handle;
        blend(pipeline.blend);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(color.x, color.y, color.z, color.w);
        RenderSystem.setTextureMatrix(mojang(extra));
        try {
            buffer.bind();
            com.mojang.math.Matrix4f projection = overlayProjection != null ? overlayProjection : RenderSystem.getProjectionMatrix();
            buffer.drawWithShader(mojang(modelView), projection, (ShaderInstance) pipeline.handle);
            VertexBuffer.unbind();
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.resetTextureMatrix();
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
        }
    }

    public static GpuMesh mesh(String name, boolean textured, int vertices, Consumer<MeshWriter> fill) {
        GpuMesh mesh = new GpuMesh(name, textured, vertices);
        VertexFormat format = format(textured);
        BufferBuilder builder = new BufferBuilder(format.getVertexSize() * Math.max(4, vertices));
        builder.begin(VertexFormat.Mode.QUADS, format);
        fill.accept(new MeshWriter() {
            @Override
            public void vertex(float x, float y, float z) {
                builder.vertex(x, y, z).endVertex();
            }

            @Override
            public void vertex(float x, float y, float z, float u, float v, float red, float green, float blue, float alpha) {
                builder.vertex(x, y, z).uv(u, v).color(red, green, blue, alpha).endVertex();
            }
        });
        VertexBuffer buffer = new VertexBuffer();
        buffer.bind();
        buffer.upload(builder.end());
        VertexBuffer.unbind();
        mesh.handle = buffer;
        return mesh;
    }

    public static void close(GpuMesh mesh) {
        if (mesh != null && mesh.handle != null) {
            ((VertexBuffer) mesh.handle).close();
            mesh.handle = null;
        }
    }
}
