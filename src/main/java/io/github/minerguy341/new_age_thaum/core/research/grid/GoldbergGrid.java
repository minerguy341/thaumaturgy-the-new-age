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
    /** One face of the polyhedron. A pentagon has 5 neighbors, a hexagon 6. */
    public record Cell(int index, boolean pentagon, double x, double y, double z, int[] neighbors) {
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
        this.cells = cells;
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

            // Emit the small triangles and record their edges as cell adjacencies.
            for (int i = 0; i < frequency; i++) {
                for (int j = 0; j < frequency - i; j++) {
                    connect(adjacency, local[i][j], local[i + 1][j], local[i][j + 1]);
                }
            }
            for (int i = 0; i < frequency; i++) {
                for (int j = 0; j < frequency - i - 1; j++) {
                    connect(adjacency, local[i + 1][j], local[i + 1][j + 1], local[i][j + 1]);
                }
            }
        }

        List<Cell> cells = new ArrayList<>(positions.size());
        for (int index = 0; index < positions.size(); index++) {
            double[] pos = positions.get(index);
            int[] neighbors = adjacency.get(index).stream().mapToInt(Integer::intValue).sorted().toArray();
            cells.add(new Cell(index, neighbors.length == 5, pos[0], pos[1], pos[2], neighbors));
        }
        return new GoldbergGrid(frequency, cells);
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

    private static void connect(List<Set<Integer>> adjacency, int u, int v, int w) {
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
