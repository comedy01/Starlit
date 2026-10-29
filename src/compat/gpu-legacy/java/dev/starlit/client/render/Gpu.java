package dev.starlit.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class Gpu {
    private static final Map<String, ShaderInstance> SHADERS = new HashMap<>();
    private static final int GL_LEQUAL = 515;
    private static Matrix4f overlayProjection;

    private Gpu() {
    }

    public static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(StarlitClient.MOD_ID, name);
    }

    public static VertexFormat format(boolean textured) {
        return textured ? DefaultVertexFormat.POSITION_TEX_COLOR : DefaultVertexFormat.POSITION;
    }

    public static void loaded(String name, ShaderInstance shader) {
        SHADERS.put(name, shader);
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
        return new Matrix4f(RenderSystem.getProjectionMatrix());
    }

    public static Object beginOverlay(Object projection) {
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        DhMask.apply(target.getDepthTextureId(), 0.0f);
        target.bindWrite(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL_LEQUAL);
        GL11.glDepthRange(1.0, 1.0);
        overlayProjection = (Matrix4f) projection;
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
        RenderSystem.setTextureMatrix(extra);
        try {
            buffer.bind();
            Matrix4f projection = overlayProjection != null ? overlayProjection : RenderSystem.getProjectionMatrix();
            buffer.drawWithShader(modelView, projection, (ShaderInstance) pipeline.handle);
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
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(format.getVertexSize() * Math.max(4, vertices))) {
            BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, format);
            fill.accept(new MeshWriter() {
                @Override
                public void vertex(float x, float y, float z) {
                    builder.addVertex(x, y, z);
                }

                @Override
                public void vertex(float x, float y, float z, float u, float v, float red, float green, float blue, float alpha) {
                    builder.addVertex(x, y, z).setUv(u, v).setColor(red, green, blue, alpha);
                }
            });
            try (MeshData data = builder.buildOrThrow()) {
                VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                buffer.bind();
                buffer.upload(data);
                VertexBuffer.unbind();
                mesh.handle = buffer;
            }
        }
        return mesh;
    }

    public static void close(GpuMesh mesh) {
        if (mesh != null && mesh.handle != null) {
            ((VertexBuffer) mesh.handle).close();
            mesh.handle = null;
        }
    }
}
