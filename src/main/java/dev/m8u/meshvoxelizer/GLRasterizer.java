package dev.m8u.meshvoxelizer;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;

import java.awt.*;
import java.io.*;
import java.util.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.*;

public class GLRasterizer {
    private Minecraft minecraft;
    private BlockPos originBlockPos;
    private int voxelResolution;
    private long window;
    WavefontOBJ model;

    ArrayList<VoxelizerStackEntry> voxelizerStack = new ArrayList<>();

    public static GLRasterizer getInstance() {
        return new GLRasterizer();
    }

    public void rasterizeMeshCuts(Minecraft minecraft, BlockPos originBlockPos, String filename, int voxelResolution) {
        this.minecraft = minecraft;
        this.originBlockPos = originBlockPos;
        this.voxelResolution = voxelResolution;

        initializeGLFW();

        try {
            model = new WavefontOBJ(Minecraft.getInstance().gameDir.getAbsolutePath() + "/mods/MeshVoxelizer/" + filename);
        } catch (FileNotFoundException e) {
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
        float[] pixelsCut = new float[3*this.voxelResolution*this.voxelResolution];

        GL.createCapabilities();
        glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

        Map<String, int[]> passes = new HashMap<>();
        passes.put("z", new int[] { 0, 0, 1, 0 });
        passes.put("x", new int[] { 90, 0, 1, 0 });
        passes.put("y", new int[] { 90, 1, 0, 0 });

        for (Map.Entry<String, int[]> pass : passes.entrySet()) {
            int z = 0;
            for (float glZ = -1.0f - (2.0f / this.voxelResolution);
                    glZ <= 1.0f + (2.0f / this.voxelResolution);
                    glZ += (2.0f / this.voxelResolution), z++) {
                glLoadIdentity();
                glOrtho(-1.0f, 1.0f, -1.0f, 1.0f, glZ, glZ + (2.0f / this.voxelResolution));
                glClear(GL_COLOR_BUFFER_BIT);
                glRotatef(pass.getValue()[0], pass.getValue()[1], pass.getValue()[2], pass.getValue()[3]);

                for (Map.Entry<String, ArrayList<ArrayList<Integer[]>>> faceRegion : model.faces.entrySet()) {
                    for (ArrayList<Integer[]> face : faceRegion.getValue()) {
                        glBegin(GL_POLYGON);
                        {
                            glColor3f(1.0f, 1.0f, 1.0f);
                            for (Integer[] indices : face) {
                                glVertex3d(model.vertices.get(indices[0])[0],
                                        model.vertices.get(indices[0])[1],
                                        model.vertices.get(indices[0])[2]);
                            }
                        }
                        glEnd();
                    }
                }

                glReadBuffer(GL_COLOR_BUFFER_BIT);
                glReadPixels(0, 0, this.voxelResolution, this.voxelResolution, GL_RGB, GL_FLOAT, pixelsCut);
                for (int y = 0; y < this.voxelResolution; y++) {
                    for (int component = y * this.voxelResolution * 3, x = 0;
                            component < y * this.voxelResolution * 3 + this.voxelResolution * 3;
                            component += 3, x++) {
                        switch (pass.getKey()) {
                            case "z":
                                if (pixelsCut[component] == 1.0f) {
                                    voxelizerStack.add(new VoxelizerStackEntry(this.originBlockPos.add(-this.voxelResolution / 2 + x,
                                            -this.voxelResolution / 2 + y,
                                            -this.voxelResolution / 2 + z), Blocks.STONE.getDefaultState()));
                                }
                                break;
                            case "x":
                                if (pixelsCut[component] == 1.0f) {
                                    voxelizerStack.add(new VoxelizerStackEntry(this.originBlockPos.add(-this.voxelResolution / 2 + z,
                                            -this.voxelResolution / 2 + y,
                                            -this.voxelResolution / 2 + (this.voxelResolution - x)), Blocks.STONE.getDefaultState()));
                                }
                                break;
                            case "y":
                                if (pixelsCut[component] == 1.0f) {
                                    voxelizerStack.add(new VoxelizerStackEntry(this.originBlockPos.add(-this.voxelResolution / 2 + x,
                                            -this.voxelResolution / 2 + (this.voxelResolution - z),
                                            -this.voxelResolution / 2 + y), Blocks.STONE.getDefaultState()));
                                }
                                break;
                        }
                    }
                }

                glfwSwapBuffers(window);
                glfwPollEvents();
            }
        }
    }


    class VoxelizerStackEntry {
        BlockPos blockPos;
        BlockState blockState;
        VoxelizerStackEntry(BlockPos blockPos, BlockState blockState) {
            this.blockPos = blockPos;
            this.blockState = blockState;
        }
    }


}
