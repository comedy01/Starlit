package dev.starlit.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.starlit.client.StarlitClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Consumer;

public final class Gpu {
    private static final BlendFunction MULTIPLY = new BlendFunction(SourceFactor.DST_COLOR, DestFactor.ZERO);

    private Gpu() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(StarlitClient.MOD_ID, path);
    }

    private static BlendFunction blend(GpuPipeline.Blend blend) {
        return switch (blend) {
            case ADDITIVE -> BlendFunction.ADDITIVE;
            case MULTIPLY -> MULTIPLY;
            case TRANSLUCENT -> BlendFunction.TRANSLUCENT;
            case PREMULTIPLIED -> BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA;
        };
    }

    private static VertexFormat format(boolean textured) {
        return textured ? DefaultVertexFormat.POSITION_TEX_COLOR : DefaultVertexFormat.POSITION;
    }

    private static RenderPipeline build(GpuPipeline pipeline, boolean overlay) {
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(id("pipeline/" + pipeline.name + (overlay ? "_overlay" : "")))
                .withVertexShader(id("core/" + pipeline.vertex))
                .withFragmentShader(id("core/" + pipeline.fragment))
                .withBlend(blend(pipeline.blend))
                .withDepthWrite(false)
                .withDepthTestFunction(overlay ? DepthTestFunction.LEQUAL_DEPTH_TEST : DepthTestFunction.NO_DEPTH_TEST)
                .withVertexFormat(format(pipeline.textured), VertexFormat.Mode.QUADS)
                .withCull(false)
                .build();
    }

    private static RenderPipeline handle(GpuPipeline pipeline) {
        if (GpuPipeline.overlay) {
            if (pipeline.overlayHandle == null) {
                pipeline.overlayHandle = build(pipeline, true);
            }
            return (RenderPipeline) pipeline.overlayHandle;
        }
        if (pipeline.handle == null) {
            pipeline.handle = build(pipeline, false);
        }
        return (RenderPipeline) pipeline.handle;
    }

    public static boolean ready(GpuPipeline pipeline) {
        CompiledRenderPipeline compiled = RenderSystem.getDevice().precompilePipeline(handle(pipeline));
        return compiled != null && compiled.isValid();
    }

    public static boolean canOverlay() {
        return true;
    }

    public static Object projection() {
        return new Object[]{RenderSystem.getProjectionMatrixBuffer(), RenderSystem.getProjectionType()};
    }

    public static Object beginOverlay(Object projection) {
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        if (projection == null || target.getColorTextureView() == null || target.getDepthTextureView() == null) {
            return null;
        }
        Object[] saved = (Object[]) projection;
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix((GpuBufferSlice) saved[0], (ProjectionType) saved[1]);
        if (target.getDepthTextureView().texture() instanceof com.mojang.blaze3d.opengl.GlTexture gl) DhMask.apply(gl.glId(), 0.0f);
        GL11.glDepthRange(1.0, 1.0);
        return target;
    }

    public static void endOverlay(Object pass) {
        if (pass != null) {
            RenderSystem.restoreProjectionMatrix();
            GL11.glDepthRange(0.0, 1.0);
        }
    }

    public static void draw(Object frame, GpuPipeline pipeline, GpuMesh mesh, int indexCount, int baseVertex,
                            Matrix4f modelView, Vector4f color, Vector3f offset, Matrix4f extra) {
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indexBuffer = indices.getBuffer(indexCount);
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(modelView, color, offset, extra);
        RenderTarget target = (RenderTarget) frame;
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Starlit " + pipeline.name,
                target.getColorTextureView(), OptionalInt.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(handle(pipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, (GpuBuffer) mesh.handle);
            pass.setIndexBuffer(indexBuffer, indices.type());
            pass.drawIndexed(baseVertex, 0, indexCount, 1);
        }
    }

    public static GpuMesh mesh(String name, boolean textured, int vertices, Consumer<MeshWriter> fill) {
        GpuMesh mesh = new GpuMesh(name, textured, vertices);
        VertexFormat format = format(textured);
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(format.getVertexSize() * Math.max(4, vertices))) {
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
                mesh.handle = RenderSystem.getDevice().createBuffer(() -> name, GpuBuffer.USAGE_VERTEX, data.vertexBuffer());
            }
        }
        return mesh;
    }

    public static void close(GpuMesh mesh) {
        if (mesh != null && mesh.handle != null) {
            ((GpuBuffer) mesh.handle).close();
            mesh.handle = null;
        }
    }
}
