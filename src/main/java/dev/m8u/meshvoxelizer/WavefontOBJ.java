package dev.m8u.meshvoxelizer;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;


public class WavefontOBJ {
    ArrayList<Double[]> vertices = new ArrayList<>(),
            texCoords = new ArrayList<>();
    Map<Material, ArrayList<ArrayList<Integer[]>>> faces = new HashMap<>(); // a dict of material-named faces regions, where face is a list of int[] {v, vt} or {v}

    int faceCount = 0;

    boolean hasTextureCoords = false;

    private String meshVoxelizerDirectory;

    WavefontOBJ(String modelPath) throws IOException {
        this.meshVoxelizerDirectory = modelPath.substring(0, modelPath.lastIndexOf('/') + 1);

        Scanner objScanner = new Scanner(new File(modelPath));

        double vertexCoordNorm = 0.0;

        Material currentUsemtl = null;
        while (objScanner.hasNextLine()) {
            String[] objLineContents = objScanner.nextLine().split(" ");
            switch (objLineContents[0]) {
                case "mtllib":
                    Scanner mtlScanner = new Scanner(new File(this.meshVoxelizerDirectory + objLineContents[1]));
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
                                //System.out.println("find a new material: " + mtlLineContents[1]);
                                break;
                            case "Kd":
                                currentNewmtl.base = new Color(Float.parseFloat(mtlLineContents[1]),
                                        Float.parseFloat(mtlLineContents[2]),
                                        Float.parseFloat(mtlLineContents[3]));
                                break;
                            case "map_Kd":
                                currentNewmtl.texture = ImageIO.read(new File(this.meshVoxelizerDirectory + mtlLineContents[1]));
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
                    //System.out.println("USEMTL");
                    for (Material material : faces.keySet()) {
                        System.out.println(material.name);
                        if (material.name.equals(objLineContents[1])) {
                            currentUsemtl = material;
                        }
                    }
                    //System.out.println("using material: "+ currentUsemtl.name);
                    break;
                case "f":
                    ArrayList<Integer[]> face = new ArrayList<>();
                    for (int i = 1; i < objLineContents.length; i++) {
                        if (objLineContents[i].contains("//")) { // v//vn
                            String[] doubleSlashSplit = objLineContents[i].split("//");
                            face.add(new Integer[] { Integer.parseInt(doubleSlashSplit[0])-1 });
                        } else if (objLineContents[i].contains("/")) { // v/vt/vn
                            String[] slashSplit = objLineContents[i].split("/");
                            face.add(new Integer[] { Integer.parseInt(slashSplit[0])-1, Integer.parseInt(slashSplit[1])-1 });
                            hasTextureCoords = true;
                        } else { // v
                            face.add(new Integer[] { Integer.parseInt(objLineContents[1])-1 });
                        }
                    }
                    this.faces.get(currentUsemtl).add(face);
                    this.faceCount++;
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
