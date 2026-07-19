package io.github.minerguy341.new_age_thaum.core.research.grid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A class-I Goldberg polyhedron GP(frequency, 0): the sphere the research linking
 * puzzle plays on. Built as the dual of a geodesic icosphere — each geodesic vertex
 * becomes a cell, so the 12 original icosahedron vertices (degree 5) are the only
 * pentagons and every other cell is a hexagon.
 *
 * <p>This class is pure geometry/topology with no Minecraft dependency, so it is
 * fully unit-testable. Cell count is always {@code 10 * frequency^2 + 2}. Positions
 * are unit vectors for a future renderer; the puzzle rules only use {@link Cell#neighbors()}.
 */
public final class GoldbergGrid {
    /**
     * One face of the polyhedron. A pentagon has 5 neighbors/corners, a hexagon 6.
     * {@code corners} are the ordered polygon vertices on the unit sphere (for rendering
     * and hit-testing); {@code x,y,z} is the face centre.
     *
     * <p>The {@code neighbors}/{@code corners} arrays are the grid's own storage, shared
     * by every puzzle on both sides through the global grid cache — treat them as
     * immutable; never sort or write into them.
     */
    public record Cell(int index, boolean pentagon, double x, double y, double z,
                       int[] neighbors, double[][] corners) {
    }

    private static final double PHI = (1.0 + Math.sqrt(5.0)) / 2.0;
    private static final double DEDUP_SCALE = 1.0e6;

    // Icosahedron: 12 vertices and the 20 triangular faces indexing them.
    private static final double[][] ICO_VERTICES = {
            {-1, PHI, 0}, {1, PHI, 0}, {-1, -PHI, 0}, {1, -PHI, 0},
            {0, -1, PHI}, {0, 1, PHI}, {0, -1, -PHI}, {0, 1, -PHI},
            {PHI, 0, -1}, {PHI, 0, 1}, {-PHI, 0, -1}, {-PHI, 0, 1}
    };
    private static final int[][] ICO_FACES = {
            {0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11},
            {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6}, {7, 1, 8},
            {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9},
            {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1}
    };

    private final int frequency;
    private final List<Cell> cells;

    private GoldbergGrid(int frequency, List<Cell> cells) {
        this.frequency = frequency;
        // Immutable view: grids are cached globally and shared across every puzzle on
        // both logical sides, so no caller may restructure the list.
        this.cells = List.copyOf(cells);
    }

    public int frequency() {
        return frequency;
    }

    public List<Cell> cells() {
        return cells;
    }

    public Cell cell(int index) {
        return cells.get(index);
    }

    public int size() {
        return cells.size();
    }

    /**
     * Builds GP(frequency, 0). Frequency 1 is the plain dodecahedron (12 pentagons);
     * 2+ add hexagons. Cell count is {@code 10 * frequency^2 + 2}.
     */
    public static GoldbergGrid generate(int frequency) {
        if (frequency < 1) {
            throw new IllegalArgumentException("frequency must be >= 1, got " + frequency);
        }

        Map<String, Integer> keyToIndex = new HashMap<>();
        List<double[]> positions = new ArrayList<>();
        List<Set<Integer>> adjacency = new ArrayList<>();
        List<int[]> triangles = new ArrayList<>();

        for (int[] face : ICO_FACES) {
            double[] a = ICO_VERTICES[face[0]];
            double[] b = ICO_VERTICES[face[1]];
            double[] c = ICO_VERTICES[face[2]];

            // Resolve every lattice point of this subdivided face to a global cell index.
            int[][] local = new int[frequency + 1][];
            for (int i = 0; i <= frequency; i++) {
                local[i] = new int[frequency + 1 - i];
                for (int j = 0; j <= frequency - i; j++) {
                    int k = frequency - i - j;
                    double[] point = normalize(
                            (i * a[0] + j * b[0] + k * c[0]),
                            (i * a[1] + j * b[1] + k * c[1]),
                            (i * a[2] + j * b[2] + k * c[2]));
                    local[i][j] = resolve(point, keyToIndex, positions, adjacency);
                }
            }

            // Emit the small triangles: their edges are cell adjacencies, and their
            // centroids are the corners of the surrounding cells' faces (the dual).
            for (int i = 0; i < frequency; i++) {
                for (int j = 0; j < frequency - i; j++) {
                    emit(adjacency, triangles, local[i][j], local[i + 1][j], local[i][j + 1]);
                }
            }
            for (int i = 0; i < frequency; i++) {
                for (int j = 0; j < frequency - i - 1; j++) {
                    emit(adjacency, triangles, local[i + 1][j], local[i + 1][j + 1], local[i][j + 1]);
                }
            }
        }

        // Face corners: each cell's polygon vertices are the centroids of the geodesic
        // triangles around it, ordered by angle in the cell's tangent plane.
        List<List<double[]>> cornerCandidates = new ArrayList<>(positions.size());
        for (int i = 0; i < positions.size(); i++) {
            cornerCandidates.add(new ArrayList<>());
        }
        for (int[] tri : triangles) {
            double[] centroid = normalize(
                    positions.get(tri[0])[0] + positions.get(tri[1])[0] + positions.get(tri[2])[0],
                    positions.get(tri[0])[1] + positions.get(tri[1])[1] + positions.get(tri[2])[1],
                    positions.get(tri[0])[2] + positions.get(tri[1])[2] + positions.get(tri[2])[2]);
            cornerCandidates.get(tri[0]).add(centroid);
            cornerCandidates.get(tri[1]).add(centroid);
            cornerCandidates.get(tri[2]).add(centroid);
        }

        List<Cell> cells = new ArrayList<>(positions.size());
        for (int index = 0; index < positions.size(); index++) {
            double[] pos = positions.get(index);
            int[] neighbors = adjacency.get(index).stream().mapToInt(Integer::intValue).sorted().toArray();
            double[][] corners = orderCorners(pos, cornerCandidates.get(index));
            cells.add(new Cell(index, neighbors.length == 5, pos[0], pos[1], pos[2], neighbors, corners));
        }
        return new GoldbergGrid(frequency, cells);
    }

    /** Orders a cell's corner points counter-clockwise in the tangent plane at its centre. */
    private static double[][] orderCorners(double[] center, List<double[]> corners) {
        // Tangent basis (u, v) at the cell centre normal.
        double[] ref = Math.abs(center[1]) < 0.9 ? new double[]{0, 1, 0} : new double[]{1, 0, 0};
        double[] u = normalize(cross(ref, center)[0], cross(ref, center)[1], cross(ref, center)[2]);
        double[] v = cross(center, u);
        corners.sort((p, q) -> Double.compare(angle(p, u, v), angle(q, u, v)));
        double[][] out = new double[corners.size()][];
        for (int i = 0; i < corners.size(); i++) {
            out[i] = corners.get(i);
        }
        return out;
    }

    private static double angle(double[] p, double[] u, double[] v) {
        return Math.atan2(dot(p, v), dot(p, u));
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]
        };
    }

    private static int resolve(double[] point, Map<String, Integer> keyToIndex,
            List<double[]> positions, List<Set<Integer>> adjacency) {
        String key = Math.round(point[0] * DEDUP_SCALE) + ":"
                + Math.round(point[1] * DEDUP_SCALE) + ":"
                + Math.round(point[2] * DEDUP_SCALE);
        Integer existing = keyToIndex.get(key);
        if (existing != null) {
            return existing;
        }
        int index = positions.size();
        keyToIndex.put(key, index);
        positions.add(point);
        adjacency.add(new LinkedHashSet<>());
        return index;
    }

    private static void emit(List<Set<Integer>> adjacency, List<int[]> triangles, int u, int v, int w) {
        triangles.add(new int[]{u, v, w});
        edge(adjacency, u, v);
        edge(adjacency, v, w);
        edge(adjacency, w, u);
    }

    private static void edge(List<Set<Integer>> adjacency, int u, int v) {
        if (u != v) {
            adjacency.get(u).add(v);
            adjacency.get(v).add(u);
        }
    }

    private static double[] normalize(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        return new double[]{x / length, y / length, z / length};
    }
}
