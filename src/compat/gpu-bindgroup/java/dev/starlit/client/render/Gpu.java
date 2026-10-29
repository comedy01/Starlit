package dev.starlit.client.render;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.starlit.client.StarlitClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Consumer;

public final class Gpu {
    private static final BlendFunction MULTIPLY = new BlendFunction(BlendFactor.DST_COLOR, BlendFactor.ZERO);

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
        RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withLocation(id("pipeline/" + pipeline.name + (overlay ? "_overlay" : "")))
                .withVertexShader(id("core/" + pipeline.vertex))
                .withFragmentShader(id("core/" + pipeline.fragment))
                .withColorTargetState(new ColorTargetState(blend(pipeline.blend)))
                .withVertexBinding(0, format(pipeline.textured))
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withCull(false);
        if (overlay) {
            builder.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false));
        }
        return builder.build();
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
        RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (projection == null || target.getColorTextureView() == null || target.getDepthTextureView() == null) {
            return null;
        }
        Object[] saved = (Object[]) projection;
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix((GpuBufferSlice) saved[0], (ProjectionType) saved[1]);
        DhMask.apply(target.getDepthTextureView().texture(), 1.0f);
        return target;
    }

    public static void endOverlay(Object target) {
        if (target != null) {
            RenderSystem.restoreProjectionMatrix();
        }
    }

    public static void draw(Object frame, GpuPipeline pipeline, GpuMesh mesh, int indexCount, int baseVertex,
                            Matrix4f modelView, Vector4f color, Vector3f offset, Matrix4f extra) {
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer indexBuffer = indices.getBuffer(indexCount);
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(modelView, color, offset, extra);
        RenderTarget target = (RenderTarget) frame;
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Starlit " + pipeline.name,
                target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(handle(pipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, ((GpuBuffer) mesh.handle).slice());
            pass.setIndexBuffer(indexBuffer, indices.type());
            pass.drawIndexed(indexCount, 1, 0, baseVertex, 0);
        }
    }

    public static GpuMesh mesh(String name, boolean textured, int vertices, Consumer<MeshWriter> fill) {
        GpuMesh mesh = new GpuMesh(name, textured, vertices);
        VertexFormat format = format(textured);
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(format.getVertexSize() * Math.max(4, vertices))) {
            BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, format);
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
