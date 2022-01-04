package dev.m8u.meshvoxelizer;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;


public class WavefrontOBJ {
    ArrayList<Double[]> vertices = new ArrayList<>(),
            texCoords = new ArrayList<>();
    Map<Material, ArrayList<ArrayList<Integer[]>>> faces = new HashMap<>(); // a dict of material-named faces regions, where face is a list of int[] {v, vt} or {v}

    int faceCount = 0;

    boolean hasTextureCoords = false;

    WavefrontOBJ(String modelPath) throws IOException {
        String meshVoxelizerDirectory = modelPath.substring(0, modelPath.lastIndexOf('/') + 1);

        Scanner objScanner = new Scanner(new File(modelPath));

        double vertexCoordNorm = 0.0;

        Material currentUsemtl = null;
        while (objScanner.hasNextLine()) {
            if (GLRasterizer.getInstance().shouldInterrupt)
                return;

            String[] objLineContents = objScanner.nextLine().split(" ");
            switch (objLineContents[0]) {
                case "mtllib":
                    Scanner mtlScanner = new Scanner(new File(meshVoxelizerDirectory + objLineContents[1]));
                    Material currentNewmtl = null;
                    while (mtlScanner.hasNextLine()) {
                        String[] mtlLineContents = mtlScanner.nextLine().split(" ");
                        switch (mtlLineContents[0]) {
                            case "newmtl":
                                if (currentNewmtl != null) {
                                    faces.put(currentNewmtl, new ArrayList<>());
                                }
                                currentNewmtl = new Material();
                                currentNewmtl.name = mtlLineContents[1];
                                break;
                            case "Kd":
                                currentNewmtl.base = new Color(Float.parseFloat(mtlLineContents[1]),
                                        Float.parseFloat(mtlLineContents[2]),
                                        Float.parseFloat(mtlLineContents[3]));
                                break;
                            case "map_Ke":
                            case "map_Kd":
                                if (mtlLineContents[1].contains("/") || mtlLineContents[1].contains("\\\\")) {
                                    currentNewmtl.texture = ImageIO.read(new File(mtlLineContents[1].replace("\\\\", "/")));
                                } else {
                                    currentNewmtl.texture = ImageIO.read(new File(meshVoxelizerDirectory + mtlLineContents[1]));
                                }
                                break;
                        }
                    }
                    mtlScanner.close();

                    if (currentNewmtl != null) {
                        faces.put(currentNewmtl, new ArrayList<>());
                    }
                    break;
                case "v":
                    Double[] vertex = new Double[] {
                            Double.parseDouble(objLineContents[1]),
                            Double.parseDouble(objLineContents[2]),
                            Double.parseDouble(objLineContents[3])
                    };
                    for (double coord : vertex) {
                        if (Math.abs(coord) > vertexCoordNorm)
                            vertexCoordNorm = Math.abs(coord);
                    }
                    this.vertices.add(vertex);
                    break;
                case "vt":
                    this.texCoords.add(new Double[] {
                            Double.parseDouble(objLineContents[1]),
                            Double.parseDouble(objLineContents[2])
                    });
                    break;
                case "usemtl":
                    for (Material material : faces.keySet()) {
                        if (material.name.equals(objLineContents[1])) {
                            currentUsemtl = material;
                        }
                    }
                    break;
                case "f":
                    for (int i = 2; i < objLineContents.length-1; i++) {
                        ArrayList<Integer[]> face = new ArrayList<>();
                        if (objLineContents[i].contains("//")) { // v//vn
                            String[][] doubleSlashSplits = { objLineContents[1].split("//"),
                                    objLineContents[i].split("//"),
                                    objLineContents[i+1].split("//") };
                            face.add(new Integer[] { Integer.parseInt(doubleSlashSplits[0][0])-1 });
                            face.add(new Integer[] { Integer.parseInt(doubleSlashSplits[1][0])-1 });
                            face.add(new Integer[] { Integer.parseInt(doubleSlashSplits[2][0])-1 });
                        } else if (objLineContents[i].contains("/")) { // v/vt/vn
                            String[][] slashSplits = { objLineContents[1].split("/"),
                                    objLineContents[i].split("/"),
                                    objLineContents[i+1].split("/") };
                            face.add(new Integer[] { Integer.parseInt(slashSplits[0][0])-1,
                                    Integer.parseInt(slashSplits[0][1])-1 });
                            face.add(new Integer[] { Integer.parseInt(slashSplits[1][0])-1,
                                    Integer.parseInt(slashSplits[1][1])-1 });
                            face.add(new Integer[] { Integer.parseInt(slashSplits[2][0])-1,
                                    Integer.parseInt(slashSplits[2][1])-1 });
                            hasTextureCoords = true;
                        } else { // v
                            face.add(new Integer[] { Integer.parseInt(objLineContents[1])-1 });
                            face.add(new Integer[] { Integer.parseInt(objLineContents[i])-1 });
                            face.add(new Integer[] { Integer.parseInt(objLineContents[i+1])-1 });
                        }
                        this.faces.get(currentUsemtl).add(face);

                        this.faceCount++;
                    }
                    break;
            }
        }
        objScanner.close();

        System.out.println("DONE MODEL PARSING");

        double finalVertexCoordNorm = vertexCoordNorm;
        vertices.forEach((vertex) -> {
            for (int i = 0; i < 3; i++) {
                vertex[i] /= finalVertexCoordNorm;
            }
        });
    }

    static class Material {
        String name;
        Color base;
        BufferedImage texture;

        boolean hasTexture() {
            return texture != null;
        }
    }
}
