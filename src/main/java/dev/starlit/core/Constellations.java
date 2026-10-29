package dev.starlit.core;

import java.util.ArrayList;
import java.util.List;

public final class Constellations {
    public record Shape(String name, float[] center, float rotation, float scale, float[][] points, int[][] edges) {
    }

    public record Segment(float[] from, float[] to) {
    }

    public static final List<Shape> SHAPES = List.of(
            new Shape("creeper", Vec.normalize(Vec.of(-0.30F, -0.86F, 0.40F)), 0.0F, 1.55F,
                    new float[][]{
                            {1, 6}, {3, 6}, {3, 4}, {1, 4},
                            {5, 6}, {7, 6}, {7, 4}, {5, 4},
                            {3, 3}, {5, 3}, {5, 2}, {6, 2}, {6, 0}, {5, 0}, {5, 1}, {3, 1}, {3, 0}, {2, 0}, {2, 2}, {3, 2}},
                    new int[][]{
                            {0, 1}, {1, 2}, {2, 3}, {3, 0},
                            {4, 5}, {5, 6}, {6, 7}, {7, 4},
                            {8, 9}, {9, 10}, {10, 11}, {11, 12}, {12, 13}, {13, 14}, {14, 15}, {15, 16}, {16, 17},
                            {17, 18}, {18, 19}, {19, 8}}),
            new Shape("sword", Vec.normalize(Vec.of(0.55F, -0.62F, -0.45F)), 0.3F, 2.3F,
                    new float[][]{{6, 6}, {3.6F, 3.6F}, {1.5F, 1.5F}, {0.4F, 2.6F}, {2.6F, 0.4F}, {-0.9F, -0.9F}},
                    new int[][]{{0, 1}, {1, 2}, {3, 2}, {2, 4}, {2, 5}}),
            new Shape("pickaxe", Vec.normalize(Vec.of(-0.70F, -0.45F, -0.55F)), -0.4F, 2.0F,
                    new float[][]{{0, 4}, {2, 5.5F}, {4, 6}, {6, 5.5F}, {8, 4}, {4, 3}, {4, 0}},
                    new int[][]{{0, 1}, {1, 2}, {2, 3}, {3, 4}, {2, 5}, {5, 6}}),
            new Shape("heart", Vec.normalize(Vec.of(0.20F, -0.55F, 0.81F)), 0.15F, 1.6F,
                    new float[][]{{4, 0.5F}, {1, 3.6F}, {0.5F, 5.8F}, {2, 7.1F}, {4, 5.9F}, {6, 7.1F}, {7.5F, 5.8F}, {7, 3.6F}},
                    new int[][]{{0, 1}, {1, 2}, {2, 3}, {3, 4}, {4, 5}, {5, 6}, {6, 7}, {7, 0}}),
            new Shape("ghast", Vec.normalize(Vec.of(0.82F, -0.30F, 0.48F)), -0.1F, 1.5F,
                    new float[][]{{0, 8}, {8, 8}, {8, 2}, {0, 2}, {2, 5.5F}, {6, 5.5F}, {1, 0}, {3.2F, -0.6F}, {5, 0}, {7, -0.4F}},
                    new int[][]{{0, 1}, {1, 2}, {2, 3}, {3, 0}, {3, 6}, {2, 9}, {6, 7}, {8, 9}}));

    private Constellations() {
    }

    public static float[][] stars(Shape shape) {
        float[][] out = new float[shape.points().length][];
        for (int i = 0; i < out.length; i++) {
            out[i] = place(shape, shape.points()[i]);
        }
        return out;
    }

    public static List<Segment> segments(Shape shape) {
        float[][] stars = stars(shape);
        List<Segment> out = new ArrayList<>();
        for (int[] edge : shape.edges()) {
            out.add(new Segment(stars[edge[0]], stars[edge[1]]));
        }
        return out;
    }

    public static float[] place(Shape shape, float[] point) {
        float[] center = shape.center();
        float[] east = Vec.anyPerpendicular(center);
        float[] north = Vec.cross(center, east);
        float c = (float) Math.cos(shape.rotation());
        float s = (float) Math.sin(shape.rotation());
        float px = point[0] - 4.0F;
        float py = point[1] - 4.0F;
        float rx = px * c - py * s;
        float ry = px * s + py * c;
        float radians = (float) Math.toRadians(shape.scale());
        float[] offset = Vec.add(Vec.scale(east, rx * radians), Vec.scale(north, ry * radians));
        return Vec.normalize(Vec.add(center, offset));
    }
}
