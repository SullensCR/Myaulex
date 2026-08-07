package myau.render.ui;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

public final class UiShaderProgram {
    private static final Logger LOGGER = LogManager.getLogger("Myaulex-UI");

    private final String name;
    private final int program;

    public UiShaderProgram(String name, String vertexSource, String fragmentSource) {
        this.name = name;
        int vertex = compile(name + ":vertex", GL20.GL_VERTEX_SHADER, vertexSource);
        int fragment = compile(name + ":fragment", GL20.GL_FRAGMENT_SHADER, fragmentSource);
        program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertex);
        GL20.glAttachShader(program, fragment);
        GL20.glLinkProgram(program);
        String log = GL20.glGetProgramInfoLog(program, 32768);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            GL20.glDeleteShader(vertex);
            GL20.glDeleteShader(fragment);
            GL20.glDeleteProgram(program);
            throw failure("link", log);
        }
        if (log != null && !log.trim().isEmpty()) {
            LOGGER.info("{} link log: {}", name, log);
        }
        GL20.glDetachShader(program, vertex);
        GL20.glDetachShader(program, fragment);
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
    }

    private int compile(String stage, int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        String log = GL20.glGetShaderInfoLog(shader, 32768);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            GL20.glDeleteShader(shader);
            throw failure(stage, log);
        }
        if (log != null && !log.trim().isEmpty()) {
            LOGGER.info("{} compile log: {}", stage, log);
        }
        return shader;
    }

    private IllegalStateException failure(String stage, String log) {
        String details = String.format(
                "%s shader %s failed. vendor=%s renderer=%s gl=%s glsl=%s log=%s",
                name,
                stage,
                GL11.glGetString(GL11.GL_VENDOR),
                GL11.glGetString(GL11.GL_RENDERER),
                GL11.glGetString(GL11.GL_VERSION),
                GL11.glGetString(GL20.GL_SHADING_LANGUAGE_VERSION),
                log
        );
        LOGGER.error(details);
        return new IllegalStateException(details);
    }

    public void bind() {
        GL20.glUseProgram(program);
    }

    public void unbind() {
        GL20.glUseProgram(0);
    }

    public void uniform1i(String uniform, int value) {
        int location = GL20.glGetUniformLocation(program, uniform);
        if (location >= 0) GL20.glUniform1i(location, value);
    }

    public void uniform1f(String uniform, float value) {
        int location = GL20.glGetUniformLocation(program, uniform);
        if (location >= 0) GL20.glUniform1f(location, value);
    }

    public void uniform2f(String uniform, float x, float y) {
        int location = GL20.glGetUniformLocation(program, uniform);
        if (location >= 0) GL20.glUniform2f(location, x, y);
    }

    public void uniform4f(String uniform, float x, float y, float z, float w) {
        int location = GL20.glGetUniformLocation(program, uniform);
        if (location >= 0) GL20.glUniform4f(location, x, y, z, w);
    }

    public void delete() {
        GL20.glDeleteProgram(program);
    }
}
