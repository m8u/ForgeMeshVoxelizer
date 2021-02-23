package dev.m8u.meshvoxelizer;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;

import java.io.*;
import java.util.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Rasterizer {
    private long window;
    WavefontOBJ model;

    public void run(String filename) {
        init();

        try {
            model = new WavefontOBJ(Minecraft.getInstance().gameDir.getAbsolutePath() + "/mods/MeshVoxelizer/" + filename);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        }

        loop();

        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();

        if ( !glfwInit() )
            throw new IllegalStateException("Unable to initialize GLFW");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);

        window = glfwCreateWindow(300, 300, "MeshVoxelizer", NULL, NULL);
        if ( window == NULL )
            throw new RuntimeException("Failed to create the GLFW window");
        glfwSetWindowPos(window, 0, 0);

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);

        glfwShowWindow(window);
    }

    private void loop() {
        GL.createCapabilities();

        glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

        while ( !glfwWindowShouldClose(window) ) {
            glClear(GL_COLOR_BUFFER_BIT);

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

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    static class WavefontOBJ {
        ArrayList<Double[]> vertices = new ArrayList<>(),
            texCoords = new ArrayList<>();
        Map<String, ArrayList<ArrayList<Integer[]>>> faces = new HashMap<>(); // a dict of material-named faces regions, where face is a list of int[] {v, vt} or {v}

        boolean hasTextureCoords = false;

        WavefontOBJ(String modelPath) throws FileNotFoundException {
            File modelFile = new File(modelPath);
            Scanner scanner = new Scanner(modelFile);
            String currentUsemtl = "";

            double vertexCoordNorm = 0.0;

            while (scanner.hasNextLine()) {
                String[] lineContents = scanner.nextLine().split(" ");
                switch (lineContents[0]) {
                    case "mtllib":

                        break;
                    case "v":
                        Double[] vertex = new Double[] {
                                Double.parseDouble(lineContents[1]),
                                Double.parseDouble(lineContents[2]),
                                Double.parseDouble(lineContents[3])
                        };
                        for (double coord : vertex) {
                            if (coord > vertexCoordNorm)
                                vertexCoordNorm = coord;
                        }
                        this.vertices.add(vertex);
                        break;
                    case "vt":
                        this.texCoords.add(new Double[] {
                            Double.parseDouble(lineContents[1]),
                            Double.parseDouble(lineContents[2])
                        });
                        break;
                    case "usemtl":
                        faces.put(lineContents[1], new ArrayList<>());
                        currentUsemtl = lineContents[1];
                        break;
                    case "f":
                        ArrayList<Integer[]> face = new ArrayList<>();
                        for (int i = 1; i < lineContents.length; i++) {
                            if (lineContents[i].contains("//")) { // v//vn
                                String[] doubleSlashSplit = lineContents[i].split("//");
                                face.add(new Integer[] { Integer.parseInt(doubleSlashSplit[0])-1 });
                            } else if (lineContents[i].contains("/")) { // v/vt/vn
                                String[] slashSplit = lineContents[i].split("/");
                                face.add(new Integer[] { Integer.parseInt(slashSplit[0])-1, Integer.parseInt(slashSplit[1])-1 });
                                hasTextureCoords = true;
                            } else { // v
                                face.add(new Integer[] { Integer.parseInt(lineContents[1])-1 });
                            }
                        }
                        faces.get(currentUsemtl).add(face);
                        break;
                }
            }
            scanner.close();

            double finalVertexCoordNorm = vertexCoordNorm;
            vertices.forEach((vertex) -> {
                for (int i = 0; i < 3; i++) {
                    vertex[i] /= finalVertexCoordNorm;
                }
            });
        }
    }
}
