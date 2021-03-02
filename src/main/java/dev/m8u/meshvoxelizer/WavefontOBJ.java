package dev.m8u.meshvoxelizer;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class WavefontOBJ {
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
