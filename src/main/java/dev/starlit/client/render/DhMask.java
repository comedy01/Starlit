package dev.starlit.client.render;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

// Far-terrain mods (Distant Horizons, Voxy) under a shader pack draw LODs into their own depth texture, so the main depth
// buffer still reads "sky" there. Before the overlay draws, this stamps that terrain into the main depth buffer's sky
// pixels, just short of the far plane, so the overlay's far-plane test rejects it and real terrain depth is untouched.
public final class DhMask {
    private static final String VERTEX = """
            #version 150
            void main() {
                vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
            }
            """;
    private static final String FRAGMENT = """
            #version 150
            uniform sampler2D Far;
            uniform float FarSky;
            uniform vec2 Scale;
            uniform float WriteDepth;
            out vec4 fragColor;
            void main() {
                // Voxy's framebuffer can be rendered at a different resolution than the main target.
                if (texelFetch(Far, ivec2(gl_FragCoord.xy * Scale), 0).r == FarSky) discard;
                gl_FragDepth = WriteDepth;
                fragColor = vec4(0.0);
            }
            """;
    private static final Logger LOGGER = LoggerFactory.getLogger("starlit");
    private static int program;
    private static int farSkyUniform;
    private static int scaleUniform;
    private static int writeDepthUniform;
    private static int vao;
    private static int fbo;
    private static boolean broken;
    private static java.lang.reflect.Method glId;
    public static int applied;

    private DhMask() {
    }

    // 26.x (unobfuscated) GpuTexture packs: reach GlTexture.glId() by name. Obfuscated 1.21.x packs call glId() directly.
    public static void apply(Object depthTexture, float maskDepth) {
        if (broken || depthTexture == null || dev.starlit.client.Compat.farDepthTexture() <= 0) {
            return;
        }
        try {
            if (glId == null) {
                glId = depthTexture.getClass().getMethod("glId");
            }
            apply((int) glId.invoke(depthTexture), maskDepth);
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            LOGGER.warn("Starlit: far-terrain depth mask disabled, depth texture {} has no GL id", depthTexture.getClass().getName(), e);
        }
    }

    public static void apply(int depthTexture, float maskDepth) {
        if (broken || depthTexture <= 0 || Boolean.getBoolean("starlit.nodhmask")) {
            return;
        }
        int farDepth = dev.starlit.client.Compat.farDepthTexture();
        if (farDepth <= 0) {
            return;
        }
        try {
            if (program == 0 && !setup()) {
                return;
            }
            float sky = dev.starlit.client.Compat.farSky();
            draw(depthTexture, farDepth, sky < 0.0f ? 1.0f - maskDepth : sky, maskDepth, false);
            applied++;
        } catch (RuntimeException e) {
            broken = true;
            LOGGER.warn("Starlit: far-terrain depth mask disabled", e);
        }
    }

    // OptiFine shader packs clear the main depth buffer in their final pass; rebuild it from the pack's depthtex0 (sky = 1).
    public static void restore(int mainDepth, int shaderDepth) {
        if (broken || mainDepth <= 0 || shaderDepth <= 0) {
            return;
        }
        try {
            if (program == 0 && !setup()) {
                return;
            }
            draw(mainDepth, shaderDepth, 1.0f, 0.0f, true);
            applied++;
        } catch (RuntimeException e) {
            broken = true;
            LOGGER.warn("Starlit: shader depth restore disabled", e);
        }
    }

    private static boolean setup() {
        int vs = shader(GL20.GL_VERTEX_SHADER, VERTEX);
        int fs = shader(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
        int p = GL20.glCreateProgram();
        GL20.glAttachShader(p, vs);
        GL20.glAttachShader(p, fs);
        GL30.glBindFragDataLocation(p, 0, "fragColor");
        GL20.glLinkProgram(p);
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(p);
            GL20.glDeleteProgram(p);
            throw new IllegalStateException("Far-terrain mask link failed: " + log);
        }
        int previous = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL20.glUseProgram(p);
        GL20.glUniform1i(GL20.glGetUniformLocation(p, "Far"), 0);
        GL20.glUseProgram(previous);
        farSkyUniform = GL20.glGetUniformLocation(p, "FarSky");
        scaleUniform = GL20.glGetUniformLocation(p, "Scale");
        writeDepthUniform = GL20.glGetUniformLocation(p, "WriteDepth");
        vao = GL30.glGenVertexArrays();
        fbo = GL30.glGenFramebuffers();
        program = p;
        return true;
    }

    private static int shader(int type, String source) {
        int s = GL20.glCreateShader(type);
        GL20.glShaderSource(s, source);
        GL20.glCompileShader(s);
        if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(s);
            GL20.glDeleteShader(s);
            throw new IllegalStateException("Far-terrain mask compile failed: " + log);
        }
        return s;
    }

    // maskDepth is where the overlay pins its own depth: 0 for normal Z (main sky at 1), 1 for reversed Z (main sky at 0).
    private static void draw(int depthTexture, int farDepth, float farSky, float maskDepth, boolean clear) {
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int prevTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        double[] depthRange = new double[2];
        GL11.glGetDoublev(GL11.GL_DEPTH_RANGE, depthRange);
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depthTexture, 0);
            GL11.glDrawBuffer(GL11.GL_NONE);
            GL11.glReadBuffer(GL11.GL_NONE);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("Far-terrain mask framebuffer incomplete");
            }
            if (clear) {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL11.glDepthMask(true);
                GL11.glClearDepth(1.0);
                GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            }
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTexture);
            int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            GL11.glViewport(0, 0, width, height);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, farDepth);
            int farWidth = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int farHeight = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            boolean reversed = maskDepth > 0.5f;
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(reversed ? GL11.GL_GREATER : GL11.GL_LESS);
            GL11.glDepthMask(true);
            GL11.glDepthRange(0.0, 1.0);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL20.glUseProgram(program);
            GL20.glUniform1f(farSkyUniform, farSky);
            GL20.glUniform2f(scaleUniform, (float) farWidth / width, (float) farHeight / height);
            GL20.glUniform1f(writeDepthUniform, reversed ? 1.0e-4f : 1.0f - 1.0e-4f);
            GL30.glBindVertexArray(vao);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        } finally {
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, 0, 0);
            GL30.glBindVertexArray(prevVao);
            GL20.glUseProgram(prevProgram);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTexture);
            GL13.glActiveTexture(prevActive);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            GL11.glDepthRange(depthRange[0], depthRange[1]);
            GL11.glDepthFunc(depthFunc);
            GL11.glDepthMask(depthMask);
            toggle(GL11.GL_DEPTH_TEST, depthTest);
            toggle(GL11.GL_BLEND, blend);
            toggle(GL11.GL_CULL_FACE, cull);
            toggle(GL11.GL_SCISSOR_TEST, scissor);
        }
    }

    private static void toggle(int cap, boolean on) {
        if (on) {
            GL11.glEnable(cap);
        } else {
            GL11.glDisable(cap);
        }
    }
}
