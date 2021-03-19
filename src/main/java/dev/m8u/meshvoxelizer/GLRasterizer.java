package dev.m8u.meshvoxelizer;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;

import java.awt.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.*;


public class GLRasterizer {
    VoxelizerScreen caller;
    private BlockPos originBlockPos;
    private int voxelResolution;
    private long window;
    WavefontOBJ model;

    public static GLRasterizer getInstance() {
        return new GLRasterizer();
    }

    public void rasterizeMeshCuts(VoxelizerScreen caller, BlockPos originBlockPos, String filename, int voxelResolution) {
        this.caller = caller;
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
    }

    private void process() {
        System.out.println("processing...");
        float[] mask = new float[3*this.voxelResolution*this.voxelResolution];
        float[] cut = new float[3*this.voxelResolution*this.voxelResolution];

        GL.createCapabilities();

        Map<String, GLTexture> textures = new HashMap<>();

        for (Map.Entry<WavefontOBJ.Material, ArrayList<ArrayList<Integer[]>>> materialRegion : model.faces.entrySet()) {
            WavefontOBJ.Material material = materialRegion.getKey();
            int textureId = glGenTextures();
            int width = material.texture.getWidth(), height = material.texture.getHeight();
            int[] pixels = new int[width * height];
            material.texture.getRGB(0, 0, width, height, pixels, 0, width);
            ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 3);
            for (int p = pixels.length-1; p >= 0; p--) {
                Color color = new Color(pixels[p]);
                buffer.put((byte) (color.getRed()));
                buffer.put((byte) (color.getGreen()));
                buffer.put((byte) (color.getBlue()));
            }
            buffer.flip();

            textures.put(material.name, new GLTexture(textureId, buffer, width, height));
        }

        Map<String, int[][]> passes = new HashMap<>();
        passes.put("z", new int[][] { new int[] { 0, 0, 1, 0 }, new int[] { 0, 0, 1, 0 } });
        passes.put("x", new int[][] { new int[] { -90, 0, 1, 0 }, new int[] { 0, 0, 1, 0 } });
        passes.put("y", new int[][] { new int[] { -90, 1, 0, 0 }, new int[] { 180, 0, 1, 0 } });

        for (Map.Entry<String, int[][]> pass : passes.entrySet()) {
            int z = 0;
            for (float glZ = -1.0f - (2.0f / this.voxelResolution);
                    glZ <= 1.0f + (2.0f / this.voxelResolution);
                    glZ += (2.0f / this.voxelResolution), z++) {

                renderCut(new Color(255, 0, 255), pass, glZ, textures);
                glReadPixels(0, 0, this.voxelResolution, this.voxelResolution, GL_RGB, GL_FLOAT, mask);
                glfwSwapBuffers(window);
                renderCut(new Color(0, 0, 0), pass, glZ, textures);
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
                                    this.caller.setBlockClosestToColor(this.originBlockPos.add(
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
                                    this.caller.setBlockClosestToColor(this.originBlockPos.add(
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
                                    this.caller.setBlockClosestToColor(this.originBlockPos.add(
                                                    -this.voxelResolution / 2 + x,
                                                    -this.voxelResolution / 2 + z - 1,
                                                    -this.voxelResolution / 2 + y),
                                                        new Color(cut[component+0], cut[component+1], cut[component+2]));
                                }
                                break;
                        }
                    }
                }
            }
        }
    }

    void renderCut(Color clearColor, Map.Entry<String, int[][]> pass, float glZ, Map<String, GLTexture> textures) {
        glClearColor((float) (clearColor.getRed() / 255),
                (float) (clearColor.getGreen() / 255),
                (float) (clearColor.getBlue() / 255), 1.0f);
        glLoadIdentity();
        glOrtho(-1.0f, 1.0f, -1.0f, 1.0f, glZ, glZ + (2.0f / this.voxelResolution));
        glClear(GL_COLOR_BUFFER_BIT);
        //glPushAttrib(GL_COLOR_BUFFER_BIT);
        glRotatef(pass.getValue()[0][0], pass.getValue()[0][1], pass.getValue()[0][2], pass.getValue()[0][3]);
        glRotatef(pass.getValue()[1][0], pass.getValue()[1][1], pass.getValue()[1][2], pass.getValue()[1][3]);

        for (Map.Entry<WavefontOBJ.Material, ArrayList<ArrayList<Integer[]>>> materialRegion : model.faces.entrySet()) {
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
                    }
                }
                glEnd();
                glDisable(GL_TEXTURE_2D);
            }
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
