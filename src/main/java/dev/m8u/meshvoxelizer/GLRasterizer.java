package dev.m8u.meshvoxelizer;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import org.jline.utils.Display;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;

import java.awt.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.*;


public class GLRasterizer {
    VoxelizerScreen voxelizerScreen;
    private BlockPos originBlockPos;
    private int voxelResolution;
    private long window;
    WavefontOBJ model;

    public boolean isWorking = false;
    private int pId;
    private int vaoId;
    private int vboiId;
    private int indicesCount;

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

        if ( !glfwInit() )
            throw new IllegalStateException("Unable to initialize GLFW");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_FOCUS_ON_SHOW, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);
        glfwWindowHint(GLFW_DECORATED, GLFW_FALSE);

        window = glfwCreateWindow(this.voxelResolution, this.voxelResolution,
                "MeshVoxelizer", NULL, NULL);
        if ( window == NULL )
            throw new RuntimeException("Failed to create the GLFW window");
        glfwSetWindowPos(window, 0, 0);

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);

        glfwShowWindow(window);

        //glViewport(0, 0, this.voxelResolution, this.voxelResolution);
    }

    private void process() {
        this.isWorking = true;
        System.out.println("processing...");
        int cutsDone = 0;

        float[] mask = new float[3*this.voxelResolution*this.voxelResolution];
        float[] cut = new float[3*this.voxelResolution*this.voxelResolution];

        GL.createCapabilities();

        Map<String, GLTexture> textures = new HashMap<>();


        for (Map.Entry<WavefontOBJ.Material, ArrayList<ArrayList<Integer[]>>> materialRegion : model.faces.entrySet()) {
            WavefontOBJ.Material material = materialRegion.getKey();
            if (material.hasTexture()) {
                int textureId = glGenTextures();
                int width = material.texture.getWidth(), height = material.texture.getHeight();
                int[] pixels = new int[width * height];
                material.texture.getRGB(0, 0, width, height, pixels, 0, width);
                ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 3);
                for (int p = pixels.length - 1; p >= 0; p--) {
                    Color color = new Color(pixels[p]);
                    buffer.put((byte) (color.getRed()));
                    buffer.put((byte) (color.getGreen()));
                    buffer.put((byte) (color.getBlue()));
                }
                buffer.flip();

                glBindTexture(GL_TEXTURE_2D, textureId);

                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB16, width, height, 0, GL_RGB, GL_UNSIGNED_BYTE, buffer);

                textures.put(material.name, new GLTexture(textureId, buffer, width, height));
            }
        }

        setupModel(model);
        System.out.println("[RETAINED RENDERING] MODEL SET UP");
        setupShaders();
        System.out.println("[RETAINED RENDERING] SHADERS SET UP");

        Map<String, int[][]> passes = new HashMap<>();
        passes.put("z", new int[][] { new int[] { 0, 0, 1, 0 }, new int[] { 0, 0, 1, 0 } });
        passes.put("x", new int[][] { new int[] { -90, 0, 1, 0 }, new int[] { 0, 0, 1, 0 } });
        passes.put("y", new int[][] { new int[] { -90, 1, 0, 0 }, new int[] { 180, 0, 1, 0 } });

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
                                && cut[component+1] == mask[component+1]
                                && cut[component+2] == mask[component+2]) {
                                    this.voxelizerScreen.setBlockClosestToColor(this.originBlockPos.add(
                                                    -this.voxelResolution / 2 + (this.voxelResolution - 1 - x),
                                                    -this.voxelResolution / 2 + y,
                                                    -this.voxelResolution / 2 + z - 1),
                                                        new Color(cut[component+0], cut[component+1], cut[component+2]));
                                }
                                break;
                            case "x":
                                if (cut[component] == mask[component]
                                && cut[component+1] == mask[component+1]
                                && cut[component+2] == mask[component+2]) {
                                    this.voxelizerScreen.setBlockClosestToColor(this.originBlockPos.add(
                                                    -this.voxelResolution / 2 + z - 1,
                                                    -this.voxelResolution / 2 + y,
                                                    -this.voxelResolution / 2 + x),
                                                        new Color(cut[component+0], cut[component+1], cut[component+2]));
                                }
                                break;
                            case "y":
                                if (cut[component] == mask[component]
                                && cut[component+1] == mask[component+1]
                                && cut[component+2] == mask[component+2]) {
                                    this.voxelizerScreen.setBlockClosestToColor(this.originBlockPos.add(
                                                    -this.voxelResolution / 2 + x,
                                                    -this.voxelResolution / 2 + z - 1,
                                                    -this.voxelResolution / 2 + y),
                                                        new Color(cut[component+0], cut[component+1], cut[component+2]));
                                }
                                break;
                        }
                    }
                }
                if (glZ > -1.0f && glZ <= 1.0f) {
                    cutsDone++;
                    this.voxelizerScreen.setProgress((int)(cutsDone / (this.voxelResolution * 3.0f) * 100));
                }
            }
        }
        this.isWorking = false;

        for (Map.Entry<String, GLTexture> entry : textures.entrySet()) {
            glDeleteTextures(entry.getValue().id);
        }
    }

    void renderCutImmediate(Color clearColor, Map.Entry<String, int[][]> pass, float glZ, Map<String, GLTexture> textures) {
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
                    glColor3f(material.base.getRed()/255f, material.base.getGreen()/255f, material.base.getBlue()/255f);
                    for (Integer[] indices : face) {
                        //System.out.println(Arrays.toString(model.texCoords.get(indices[1])));
                        glTexCoord2d(1.0 - model.texCoords.get(indices[1])[0],
                                model.texCoords.get(indices[1])[1]);
                        glVertex3d(model.vertices.get(indices[0])[0],
                                model.vertices.get(indices[0])[1],
                                model.vertices.get(indices[0])[2]);

                        /*System.out.println("kek"
                                + model.vertices.get(indices[0])[0] + " "
                                + model.vertices.get(indices[0])[1] + " "
                                + model.vertices.get(indices[0])[2]);*/
                    }
                }
                glEnd();
                glDisable(GL_TEXTURE_2D);
            }
        }
    }



    void renderCutRetained(Color clearColor, Map.Entry<String, int[][]> pass, float glZ, Map<String, GLTexture> textures) {
        GL11.glLoadIdentity();
        GL11.glOrtho(-1.0f, 1.0f, -1.0f, 1.0f, glZ, glZ + (2.0f / this.voxelResolution));

        // Clear
        GL11.glClearColor(clearColor.getRed()/255.0f,
                clearColor.getGreen()/255.0f,
                clearColor.getBlue()/255.0f,
                1.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        GL11.glRotatef(pass.getValue()[0][0], pass.getValue()[0][1], pass.getValue()[0][2], pass.getValue()[0][3]);
        GL11.glRotatef(pass.getValue()[1][0], pass.getValue()[1][1], pass.getValue()[1][2], pass.getValue()[1][3]);

        GL20.glUseProgram(pId);

        for (Map.Entry<WavefontOBJ.Material, ArrayList<ArrayList<Integer[]>>> materialRegion : this.model.faces.entrySet()) {
            WavefontOBJ.Material material = materialRegion.getKey();

            // Bind the texture
            if (material.hasTexture()) {
                GLTexture texture = textures.get(material.name);
                GL11.glBindTexture(GL_TEXTURE_2D, texture.id);
            }

            // Bind to the VAO that has all the information about the vertices
            GL30.glBindVertexArray(vaoId);
            GL20.glEnableVertexAttribArray(0);
            GL20.glEnableVertexAttribArray(1);
            GL20.glEnableVertexAttribArray(2);

            // Bind to the index VBO that has all the information about the order of the vertices
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, vboiId);

            // Draw the vertices
            GL11.glDrawElements(GL11.GL_TRIANGLES, indicesCount, GL11.GL_UNSIGNED_BYTE, 0);

            // Put everything back to default (deselect)
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
            GL20.glDisableVertexAttribArray(0);
            GL20.glDisableVertexAttribArray(1);
            GL20.glDisableVertexAttribArray(2);
            GL30.glBindVertexArray(0);

            GL20.glUseProgram(0);
        }
    }

    private void setupModel(WavefontOBJ model) {
        ArrayList<TexturedVertex> texturedVertices = new ArrayList<>();                   // assuming the model is triangulated!!!!!!!!!!!!11111111111
                                                                                         // and stored in counter-clockwise order!!!!!
        for (Map.Entry<WavefontOBJ.Material, ArrayList<ArrayList<Integer[]>>> materialRegion : this.model.faces.entrySet()) {
            for (ArrayList<Integer[]> face : materialRegion.getValue()) {
                for (Integer[] indices : face) {
                    Double[] vertex = model.vertices.get(indices[0]);
                    Double[] texCoord = model.texCoords.get(indices[1]);

                    Color base = materialRegion.getKey().base;
                            TexturedVertex texturedVertex = new TexturedVertex();
                    texturedVertex.setXYZ(vertex[0].floatValue(), vertex[1].floatValue(), vertex[2].floatValue());
                    texturedVertex.setRGB(base.getRed(), base.getGreen(), base.getBlue());
                    texturedVertex.setUV(texCoord[0].floatValue(), texCoord[1].floatValue());
                    if (!texturedVertices.contains(texturedVertex))
                        texturedVertices.add(texturedVertex);
                }
            }
        }
        System.out.println("[RETAINED RENDERING] built textured vertices array");

        // Put each 'Vertex' in one FloatBuffer
        FloatBuffer verticesBuffer = BufferUtils.createFloatBuffer(texturedVertices.size() *
                TexturedVertex.elementCount);
        // Add position, color and texture floats to the buffer
        for (TexturedVertex vertex : texturedVertices) {
            verticesBuffer.put(vertex.getElements());
        }
        verticesBuffer.flip();

        System.out.println("[RETAINED RENDERING] built vertex buffer");

        // OpenGL expects to draw vertices in counter clockwise order by default
        ArrayList<Integer> indices = new ArrayList<>();                    // SOMETHING's wrong i can feel it
        for (Map.Entry<WavefontOBJ.Material, ArrayList<ArrayList<Integer[]>>> materialRegion : this.model.faces.entrySet()) {
            for (ArrayList<Integer[]> face : materialRegion.getValue()) {
                indices.add(face.get(0)[0]);
                boolean clockwise = false;
                if (model.vertices.get(face.get(1)[0])[2] > model.vertices.get(face.get(2)[0])[2]) {
                    clockwise = true;
                    indices.add(face.get(2)[0]);
                } else {
                    indices.add(face.get(1)[0]);
                }
                if (clockwise) {
                    indices.add(face.get(1)[0]);
                } else {
                    indices.add(face.get(2)[0]);
                }
            }
        }

        indicesCount = indices.size();
        IntBuffer indicesBuffer = BufferUtils.createIntBuffer(indicesCount);
        for (Integer index : indices) {
            indicesBuffer.put(index);
        }
        indicesBuffer.flip();

        System.out.println("[RETAINED RENDERING] built indices buffer");

        // Create a new Vertex Array Object in memory and select it (bind)
        vaoId = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vaoId);

        // Create a new Vertex Buffer Object in memory and select it (bind)
        int vboId = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, verticesBuffer, GL15.GL_STATIC_DRAW);

        // Put the position coordinates in attribute list 0
        GL20.glVertexAttribPointer(0, TexturedVertex.positionElementCount, GL11.GL_FLOAT,
                false, TexturedVertex.stride, TexturedVertex.positionByteOffset);
        // Put the color components in attribute list 1
        GL20.glVertexAttribPointer(1, TexturedVertex.colorElementCount, GL11.GL_FLOAT,
                false, TexturedVertex.stride, TexturedVertex.colorByteOffset);
        // Put the texture coordinates in attribute list 2
        GL20.glVertexAttribPointer(2, TexturedVertex.textureElementCount, GL11.GL_FLOAT,
                false, TexturedVertex.stride, TexturedVertex.textureByteOffset);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        // Deselect (bind to 0) the VAO
        GL30.glBindVertexArray(0);

        // Create a new VBO for the indices and select it (bind) - INDICES
        vboiId = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, vboiId);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    private void setupShaders() {
        // Load the vertex shader
        String vertexShaderSource = "#version 150 core\n" +
                "\n" +
                "in vec4 in_Position;\n" +
                "in vec4 in_Color;\n" +
                "in vec2 in_TextureCoord;\n" +
                "\n" +
                "out vec4 pass_Color;\n" +
                "out vec2 pass_TextureCoord;\n" +
                "\n" +
                "void main(void) {\n" +
                "\tgl_Position = in_Position;\n" +
                "\t\n" +
                "\tpass_Color = in_Color;\n" +
                "\tpass_TextureCoord = in_TextureCoord;\n" +
                "}";
        int vsId = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        GL20.glShaderSource(vsId, vertexShaderSource);
        GL20.glCompileShader(vsId);

        // Load the fragment shader
        String fragmentShaderSource = "#version 150 core\n" +
                "\n" +
                "uniform sampler2D texture_diffuse;\n" +
                "\n" +
                "in vec4 pass_Color;\n" +
                "in vec2 pass_TextureCoord;\n" +
                "\n" +
                "out vec4 out_Color;\n" +
                "\n" +
                "void main(void) {\n" +
                //"\tout_Color = pass_Color;\n" +
                "\tout_Color = texture(texture_diffuse, pass_TextureCoord) * pass_Color;\n" +
                "}";
        int fsId = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(fsId, fragmentShaderSource);
        GL20.glCompileShader(fsId);

        // Create a new shader program that links both shaders
        pId = GL20.glCreateProgram();
        GL20.glAttachShader(pId, vsId);
        GL20.glAttachShader(pId, fsId);

        // Position information will be attribute 0
        GL20.glBindAttribLocation(pId, 0, "in_Position");
        // Color information will be attribute 1
        GL20.glBindAttribLocation(pId, 1, "in_Color");
        // Textute information will be attribute 2
        GL20.glBindAttribLocation(pId, 2, "in_TextureCoord");

        GL20.glLinkProgram(pId);
        GL20.glValidateProgram(pId);
    }

    class TexturedVertex {
        // Vertex data
        private float[] xyzw = new float[] {0f, 0f, 0f, 1f};
        private float[] rgba = new float[] {1f, 1f, 1f, 1f};
        private float[] uv = new float[] {0f, 0f};

        // The amount of bytes an element has
        public static final int elementBytes = 4;

        // Elements per parameter
        public static final int positionElementCount = 4;
        public static final int colorElementCount = 4;
        public static final int textureElementCount = 2;

        // Bytes per parameter
        public static final int positionBytesCount = positionElementCount * elementBytes;
        public static final int colorByteCount = colorElementCount * elementBytes;
        public static final int textureByteCount = textureElementCount * elementBytes;

        // Byte offsets per parameter
        public static final int positionByteOffset = 0;
        public static final int colorByteOffset = positionByteOffset + positionBytesCount;
        public static final int textureByteOffset = colorByteOffset + colorByteCount;

        // The amount of elements that a vertex has
        public static final int elementCount = positionElementCount +
                colorElementCount + textureElementCount;
        // The size of a vertex in bytes, like in C/C++: sizeof(Vertex)
        public static final int stride = positionBytesCount + colorByteCount +
                textureByteCount;

        public TexturedVertex() {

        }

        // Setters
        public void setXYZ(float x, float y, float z) {
            this.setXYZW(x, y, z, 1f);
        }

        public void setRGB(float r, float g, float b) {
            this.setRGBA(r, g, b, 1f);
        }

        public void setUV(float u, float v) {
            this.uv = new float[] {u, v};
        }

        public void setXYZW(float x, float y, float z, float w) {
            this.xyzw = new float[] {x, y, z, w};
        }

        public void setRGBA(float r, float g, float b, float a) {
            this.rgba = new float[] {r, g, b, 1f};
        }

        // Getters
        public float[] getElements() {
            float[] out = new float[TexturedVertex.elementCount];
            int i = 0;

            // Insert XYZW elements
            out[i++] = this.xyzw[0];
            out[i++] = this.xyzw[1];
            out[i++] = this.xyzw[2];
            out[i++] = this.xyzw[3];
            // Insert RGBA elements
            out[i++] = this.rgba[0];
            out[i++] = this.rgba[1];
            out[i++] = this.rgba[2];
            out[i++] = this.rgba[3];
            // Insert UV elements
            out[i++] = this.uv[0];
            out[i++] = this.uv[1];

            return out;
        }

        public float[] getXYZW() {
            return new float[] {this.xyzw[0], this.xyzw[1], this.xyzw[2], this.xyzw[3]};
        }

        public float[] getRGBA() {
            return new float[] {this.rgba[0], this.rgba[1], this.rgba[2], this.rgba[3]};
        }

        public float[] getST() {
            return new float[] {this.uv[0], this.uv[1]};
        }
    }

    class GLTexture {
        int id;
        ByteBuffer buffer;
        int width, height;

        GLTexture(int id, ByteBuffer buffer, int width, int height) {
            this.id = id;
            this.buffer = buffer;
            this.width = width;
            this.height = height;
        }
    }
}
