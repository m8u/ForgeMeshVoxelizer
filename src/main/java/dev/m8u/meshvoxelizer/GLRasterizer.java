package dev.m8u.meshvoxelizer;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.*;

import java.awt.Color;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

import org.joml.Matrix4f;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.GL_VERTEX_SHADER;
import static org.lwjgl.system.MemoryUtil.*;


public class GLRasterizer {
    VoxelizerScreen voxelizerScreen;
    private BlockPos originBlockPos;
    private int voxelResolution;
    private long window;
    WavefontOBJ model;
    Mesh mesh;

    ShaderProgram shaderProgram;

    public boolean isWorking = false;

    public static GLRasterizer getInstance() {
        return new GLRasterizer();
    }

    public void rasterizeMeshCuts(VoxelizerScreen caller, BlockPos originBlockPos, String filename, int voxelResolution) {
        this.voxelizerScreen = caller;
        this.originBlockPos = originBlockPos;
        this.voxelResolution = voxelResolution;

        initializeGLFW();

        try {
            model = new WavefontOBJ(Minecraft.getInstance().gameDir.getAbsolutePath() + "/mods/MeshVoxelizer/" + filename);
            System.out.println("MODEL LOADED SUCCESSFULLY");
        } catch (IOException e) {
            e.printStackTrace();
        }

        process();

        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
    }

    private void initializeGLFW() {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit())
            throw new IllegalStateException("Unable to initialize GLFW");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_FOCUS_ON_SHOW, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);
        glfwWindowHint(GLFW_DECORATED, GLFW_FALSE);

        window = glfwCreateWindow(this.voxelResolution, this.voxelResolution,
                "MeshVoxelizer", NULL, NULL);
        if (window == NULL)
            throw new RuntimeException("Failed to create the GLFW window");
        glfwSetWindowPos(window, 0, 0);

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);

        glfwShowWindow(window);
    }

    private void process() {
        this.isWorking = true;
        System.out.println("processing...");
        int cutsDone = 0;

        float[] mask = new float[3 * this.voxelResolution * this.voxelResolution];
        float[] cut = new float[3 * this.voxelResolution * this.voxelResolution];

        GL.createCapabilities();
        glEnable(GL_DEPTH_TEST);

        Map<String, Integer> textures = new HashMap<>();

        for (Map.Entry<WavefontOBJ.Material, ArrayList<ArrayList<Integer[]>>> materialRegion : model.faces.entrySet()) {
            WavefontOBJ.Material material = materialRegion.getKey();
            if (material.hasTexture()) {
                int width = material.texture.getWidth(), height = material.texture.getHeight();
                int[] pixels = new int[width * height];
                material.texture.getRGB(0, 0, width, height, pixels, 0, width);
                ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
                for (int i = pixels.length-1; i >= 0; i--) {
                    buffer.put((byte) ((pixels[i] >> 16) & 0xFF));
                    buffer.put((byte) ((pixels[i] >> 8) & 0xFF));
                    buffer.put((byte) (pixels[i] & 0xFF));
                    buffer.put((byte) ((pixels[i] >> 24) & 0xFF));
                }
                buffer.flip();

                int textureId = glGenTextures();
                glBindTexture(GL_TEXTURE_2D, textureId);

                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

                textures.put(material.name, textureId);
            }
        }

        try {
            this.shaderProgram = new ShaderProgram();
            String vertexShaderSource = "#version 450 core\n" +
                    "\n" +
                    "layout (location=0) in vec3 position;\n" +
                    "layout (location=1) in vec2 uvs;\n" +
                    "\n" +
                    "out vec2 pass_uvs;\n" +
                    "\n" +
                    "uniform mat4 projectionMatrix;\n" +
                    "\n" +
                    "void main(void){\n" +
                    "\tgl_Position = projectionMatrix * vec4(position, 1.0);\n" +
                    "\tpass_uvs = uvs;\n" +
                    "}";
            this.shaderProgram.createVertexShader(vertexShaderSource);
            String fragmentShaderSource = "#version 450 core\n" +
                    "\n" +
                    "in vec2 pass_uvs;\n" +
                    "\n" +
                    "out vec4 out_Color;\n" +
                    "\n" +
                    "uniform int textureFlag;\n" +
                    "uniform sampler2D textureSampler;\n" +
                    "\n" +
                    "void main(){\n" +
                    "\tout_Color = textureFlag * texture(textureSampler, pass_uvs) * vec4(0.8, 0.8, 0.8, 1.0)\n" +
                    "\t\t+ (1.0 - textureFlag) * vec4(0.8, 0.8, 0.8, 1.0);\n" +
                    "}";
            this.shaderProgram.createFragmentShader(fragmentShaderSource);
            this.shaderProgram.link();
            this.shaderProgram.createUniform("projectionMatrix");
            this.shaderProgram.createUniform("textureFlag");
        } catch (Exception e) {
            e.printStackTrace();
        }

        float[] vertices = new float[this.model.texCoords.size() * 3]; // yes texCoords size
        float[] uvs = new float[this.model.texCoords.size() * 2];

        ArrayList<Integer> indicesArrayList = new ArrayList<>();
        this.model.faces.forEach((materialRegion, faces) -> {
            faces.forEach((face) -> face.forEach((objVertex) -> {
                vertices[objVertex[1]*3] = this.model.vertices.get(objVertex[0])[0].floatValue();
                vertices[objVertex[1]*3 + 1] = this.model.vertices.get(objVertex[0])[1].floatValue();
                vertices[objVertex[1]*3 + 2] = this.model.vertices.get(objVertex[0])[2].floatValue();
                uvs[objVertex[1]*2] = 1 - this.model.texCoords.get(objVertex[1])[0].floatValue();
                uvs[objVertex[1]*2 + 1] = this.model.texCoords.get(objVertex[1])[1].floatValue();
                indicesArrayList.add(objVertex[1]);
            }));
        });
        System.out.println("built vertices and uvs arrays");
        System.out.println("vertices: " + vertices.length);

        int[] indices = new int[indicesArrayList.size()];
        for (int i = 0; i < indicesArrayList.size(); i += 3) {
            indices[i] = indicesArrayList.get(i);
            boolean clockwise = false;
            if (vertices[indicesArrayList.get(i + 1) * 3 + 2] >= vertices[indicesArrayList.get(i + 2) * 3 + 2]) {
                clockwise = true;
                indices[i + 1] = indicesArrayList.get(i + 2);
            } else {
                indices[i + 1] = indicesArrayList.get(i + 1);
            }
            if (clockwise) {
                indices[i + 2] = indicesArrayList.get(i + 1);
            } else {
                indices[i + 2] = indicesArrayList.get(i + 2);
            }
        }
        System.out.println("built indices array");
        System.out.println("indices: " + indices.length);

        this.mesh = MeshLoader.createMesh(vertices, uvs, indices);

        Map<String, int[][]> passes = new HashMap<>();
        passes.put("z", new int[][]{new int[]{0, 0, 1, 0}, new int[]{0, 0, 1, 0}});
        passes.put("x", new int[][]{new int[]{-90, 0, 1, 0}, new int[]{0, 0, 1, 0}});
        passes.put("y", new int[][]{new int[]{-90, 1, 0, 0}, new int[]{180, 0, 1, 0}});

        for (Map.Entry<String, int[][]> pass : passes.entrySet()) {
            System.out.println("[RETAINED RENDERING] pass");
            int z = 0;
            for (float glZ = -1.0f - (2.0f / this.voxelResolution);
                 glZ <= 1.0f + (2.0f / this.voxelResolution);
                 glZ += (2.0f / this.voxelResolution), z++) {

                renderCutRetained(new Color(255, 0, 255), pass, glZ, textures);
                glReadPixels(0, 0, this.voxelResolution, this.voxelResolution, GL_RGB, GL_FLOAT, mask);
                glfwSwapBuffers(window);
                renderCutRetained(new Color(0, 0, 0), pass, glZ, textures);
                glReadPixels(0, 0, this.voxelResolution, this.voxelResolution, GL_RGB, GL_FLOAT, cut);
                glfwSwapBuffers(window);

                for (int y = 0; y < this.voxelResolution; y++) {
                    for (int component = y * this.voxelResolution * 3, x = 0;
                         component < y * this.voxelResolution * 3 + this.voxelResolution * 3;
                         component += 3, x++) {
                        switch (pass.getKey()) {
                            case "z":
                                if (cut[component] == mask[component]
                                        && cut[component + 1] == mask[component + 1]
                                        && cut[component + 2] == mask[component + 2]) {
                                    this.voxelizerScreen.setBlockClosestToColor(this.originBlockPos.add(
                                            -this.voxelResolution / 2 + (this.voxelResolution - 1 - x),
                                            -this.voxelResolution / 2 + y,
                                            -this.voxelResolution / 2 + z - 1),
                                            new Color(cut[component + 0], cut[component + 1], cut[component + 2]));
                                }
                                break;
                            case "x":
                                if (cut[component] == mask[component]
                                        && cut[component + 1] == mask[component + 1]
                                        && cut[component + 2] == mask[component + 2]) {
                                    this.voxelizerScreen.setBlockClosestToColor(this.originBlockPos.add(
                                            -this.voxelResolution / 2 + z - 1,
                                            -this.voxelResolution / 2 + y,
                                            -this.voxelResolution / 2 + x),
                                            new Color(cut[component + 0], cut[component + 1], cut[component + 2]));
                                }
                                break;
                            case "y":
                                if (cut[component] == mask[component]
                                        && cut[component + 1] == mask[component + 1]
                                        && cut[component + 2] == mask[component + 2]) {
                                    this.voxelizerScreen.setBlockClosestToColor(this.originBlockPos.add(
                                            -this.voxelResolution / 2 + x,
                                            -this.voxelResolution / 2 + z - 1,
                                            -this.voxelResolution / 2 + y),
                                            new Color(cut[component + 0], cut[component + 1], cut[component + 2]));
                                }
                                break;
                        }
                    }
                }
                if (glZ > -1.0f && glZ <= 1.0f) {
                    cutsDone++;
                    this.voxelizerScreen.setProgress((int) (cutsDone / (this.voxelResolution * 3.0f) * 100));
                }
            }
        }

        for (Map.Entry<String, Integer> entry : textures.entrySet()) {
            glDeleteTextures(entry.getValue());
        }
        this.shaderProgram.cleanup();

        this.isWorking = false;
    }

    /*void renderCutImmediate(Color clearColor, Map.Entry<String, int[][]> pass, float glZ, Map<String, GLTexture> textures) {
        glClearColor((float) (clearColor.getRed() / 255),
                (float) (clearColor.getGreen() / 255),
                (float) (clearColor.getBlue() / 255), 1.0f);
        glLoadIdentity();
        glOrtho(-1.0f, 1.0f, -1.0f, 1.0f, glZ, glZ + (2.0f / this.voxelResolution));
        glClear(GL_COLOR_BUFFER_BIT);
        //glPushAttrib(GL_COLOR_BUFFER_BIT);
        glRotatef(pass.getValue()[0][0], pass.getValue()[0][1], pass.getValue()[0][2], pass.getValue()[0][3]);
        glRotatef(pass.getValue()[1][0], pass.getValue()[1][1], pass.getValue()[1][2], pass.getValue()[1][3]);

        for (Map.Entry<WavefontOBJ.Material, ArrayList<ArrayList<Integer[]>>> materialRegion : this.model.faces.entrySet()) {
            WavefontOBJ.Material material = materialRegion.getKey();
            if (material.hasTexture()) {
                GLTexture texture = textures.get(material.name);
                glBindTexture(GL_TEXTURE_2D, texture.id);
                //glTexEnvf(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_REPLACE);

                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB16, texture.width, texture.height, 0, GL_RGB, GL_UNSIGNED_BYTE, texture.buffer);
            }
            //System.out.println(material.name);

            for (ArrayList<Integer[]> face : materialRegion.getValue()) {
                glEnable(GL_TEXTURE_2D);
                glBegin(GL_POLYGON);
                {
                    glColor3f(material.base.getRed() / 255f, material.base.getGreen() / 255f, material.base.getBlue() / 255f);
                    for (Integer[] indices : face) {
                        //System.out.println(Arrays.toString(model.texCoords.get(indices[1])));
                        glTexCoord2d(1.0 - model.texCoords.get(indices[1])[0],
                                model.texCoords.get(indices[1])[1]);
                        glVertex3d(model.vertices.get(indices[0])[0],
                                model.vertices.get(indices[0])[1],
                                model.vertices.get(indices[0])[2]);

                        *//*System.out.println("kek"
                                + model.vertices.get(indices[0])[0] + " "
                                + model.vertices.get(indices[0])[1] + " "
                                + model.vertices.get(indices[0])[2]);*//*
                    }
                }
                glEnd();
                glDisable(GL_TEXTURE_2D);
            }
        }
    }*/

    void renderCutRetained(Color clearColor, Map.Entry<String, int[][]> pass, float glZ, Map<String, Integer> textures) {
        GL11.glClearColor((float) (clearColor.getRed() / 255),
                (float) (clearColor.getGreen() / 255),
                (float) (clearColor.getBlue() / 255), 1.0f);

        this.shaderProgram.bind();

        Matrix4f projectionMatrix = new Matrix4f();
        projectionMatrix.ortho(-1.0f, 1.0f, -1.0f, 1.0f, glZ, glZ + (2.0f / this.voxelResolution));
        projectionMatrix.rotate((float) (pass.getValue()[0][0] / (180/Math.PI)), pass.getValue()[0][1], pass.getValue()[0][2], pass.getValue()[0][3]);
        projectionMatrix.rotate((float) (pass.getValue()[1][0] / (180/Math.PI)), pass.getValue()[1][1], pass.getValue()[1][2], pass.getValue()[1][3]);
        shaderProgram.setUniform("projectionMatrix", projectionMatrix);

        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        if (textures.size() > 0) {
            shaderProgram.setUniform("textureFlag", 1);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            int id = textures.entrySet().iterator().next().getValue();
            glBindTexture(GL_TEXTURE_2D, id);
        } else {
            shaderProgram.setUniform("textureFlag", 0);
        }
        GL30.glBindVertexArray(this.mesh.getVaoID());
        GL20.glEnableVertexAttribArray(0);
        GL20.glEnableVertexAttribArray(1);

        GL11.glDrawElements(GL11.GL_TRIANGLES, this.mesh.getVertexCount(), GL11.GL_UNSIGNED_INT, 0);

        GL20.glDisableVertexAttribArray(0);
        GL20.glDisableVertexAttribArray(1);
        GL30.glBindVertexArray(0);

        this.shaderProgram.unbind();
    }


    static class MeshLoader {
        private static List<Integer> vaos = new ArrayList<>();
        private static List<Integer> vbos = new ArrayList<>();

        private static FloatBuffer createFloatBuffer(float[] data) {
            FloatBuffer buffer = BufferUtils.createFloatBuffer(data.length);
            buffer.put(data);
            buffer.flip();
            return buffer;
        }

        private static IntBuffer createIntBuffer(int[] data) {
            IntBuffer buffer = BufferUtils.createIntBuffer(data.length);
            buffer.put(data);
            buffer.flip();
            return buffer;
        }

        private static void storeData(int attribute, int dimensions, float[] data) {
            int vbo = GL15.glGenBuffers(); //Creates a VBO ID
            vbos.add(vbo);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo); //Loads the current VBO to store the data
            FloatBuffer buffer = createFloatBuffer(data);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);
            GL20.glVertexAttribPointer(attribute, dimensions, GL11.GL_FLOAT, false, 0, 0);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0); //Unloads the current VBO when done.
        }

        private static void bindIndices(int[] data) {
            int vbo = GL15.glGenBuffers();
            vbos.add(vbo);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, vbo);
            IntBuffer buffer = createIntBuffer(data);
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);
        }

        public static Mesh createMesh(float[] positions, float[] uvs, int[] indices) {
            int vao = genVAO();
            storeData(0, 3, positions);
            storeData(1, 2, uvs);
            bindIndices(indices);
            GL30.glBindVertexArray(0);
            return new Mesh(vao, indices.length);
        }

        private static int genVAO() {
            int vao = GL30.glGenVertexArrays();
            vaos.add(vao);
            GL30.glBindVertexArray(vao);
            return vao;
        }
    }


    static class Mesh {
        private int vao;
        private int vertices;

        public Mesh(int vao, int vertex) {
            this.vao = vao;
            this.vertices = vertex;
        }

        public int getVaoID() {
            return vao;
        }

        public int getVertexCount() {
            return vertices;
        }
    }


    public class ShaderProgram {
        private final int programId;
        private int vertexShaderId;
        private int fragmentShaderId;
        private final Map<String, Integer> uniforms;

        public ShaderProgram() throws Exception {
            programId = GL20.glCreateProgram();
            if (programId == 0) {
                throw new Exception("Could not create Shader");
            }
            uniforms = new HashMap<>();
        }

        public void createVertexShader(String shaderCode) throws Exception {
            vertexShaderId = createShader(shaderCode, GL_VERTEX_SHADER);
        }

        public void createFragmentShader(String shaderCode) throws Exception {
            fragmentShaderId = createShader(shaderCode, GL20.GL_FRAGMENT_SHADER);
        }

        public void createUniform(String uniformName) throws Exception {
            int uniformLocation = GL20.glGetUniformLocation(programId, uniformName);
            if (uniformLocation < 0) {
                throw new Exception("Could not find uniform:" + uniformName);
            }
            uniforms.put(uniformName, uniformLocation);
        }

        public void setUniform(String uniformName, int value) {
            GL20.glUniform1i(uniforms.get(uniformName), value);
        }

        public void setUniform(String uniformName, Matrix4f value) {
            // Dump the matrix into a float buffer
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer fb = stack.mallocFloat(16);
                value.get(fb);
                GL20C.glUniformMatrix4fv(uniforms.get(uniformName), false, fb);
            }
        }

        protected int createShader(String shaderCode, int shaderType) throws Exception {
            int shaderId = GL20.glCreateShader(shaderType);
            if (shaderId == 0) {
                throw new Exception("Error creating shader. Type: " + shaderType);
            }

            GL20.glShaderSource(shaderId, shaderCode);
            GL20.glCompileShader(shaderId);

            if (GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS) == 0) {
                throw new Exception("Error compiling Shader code: " + GL20.glGetShaderInfoLog(shaderId, 1024));
            }

            GL20.glAttachShader(programId, shaderId);

            return shaderId;
        }

        public void link() throws Exception {
            GL20.glLinkProgram(programId);
            if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == 0) {
                throw new Exception("Error linking Shader code: " + GL20.glGetProgramInfoLog(programId, 1024));
            }

            if (vertexShaderId != 0) {
                GL20.glDetachShader(programId, vertexShaderId);
            }
            if (fragmentShaderId != 0) {
                GL20.glDetachShader(programId, fragmentShaderId);
            }

            GL20.glValidateProgram(programId);
            if (GL20.glGetProgrami(programId, GL20.GL_VALIDATE_STATUS) == 0) {
                System.err.println("Warning validating Shader code: " + GL20.glGetProgramInfoLog(programId, 1024));
            }

        }

        public void bind() {
            GL20.glUseProgram(programId);
        }

        public void unbind() {
            GL20.glUseProgram(0);
        }

        public void cleanup() {
            unbind();
            if (programId != 0) {
                GL20.glDeleteProgram(programId);
            }
        }
    }
}
