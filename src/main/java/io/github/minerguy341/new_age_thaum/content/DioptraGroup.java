package io.github.minerguy341.new_age_thaum.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Dioptra tiling: side-by-side dioptras form one contiguous chunk map. Each block
 * renders its own {@link #WINDOW}x{@link #WINDOW}-chunk window, shifted a full window
 * per block of offset inside the group, and the whole map is centered on the group
 * (TC6-style) — so a lone dioptra is centered on its own chunk and a 2x2 slab shows a
 * seamless 26x26-chunk map with no gaps or overlaps. Groups are discovered by a bounded
 * flood fill only when membership changes (placement / removal), never per tick.
 */
public final class DioptraGroup {
    /** Chunks per dioptra window edge (13x13, the dioptra's chunk ± {@link #HALF}). */
    public static final int WINDOW = 13;
    public static final int HALF = WINDOW / 2;
    /**
     * Chebyshev cap on the flood fill, blocks from the fill's origin. Any group whose
     * bounding box fits 8x8 is seen whole from every member; larger slabs degrade to
     * per-origin views (deterministic, never unbounded). 8x8 already maps 104x104 chunks.
     */
    private static final int MAX_REACH = 7;

    private DioptraGroup() {
    }

    /**
     * Recomputes window centers for the whole group containing {@code origin} and
     * pushes them into the member block entities (which sync any change to clients).
     * One flood fill + one pass over the members; no-op if origin isn't a dioptra.
     */
    public static void refresh(ServerLevel level, BlockPos origin) {
        if (!(level.getBlockState(origin).getBlock() instanceof ThaumicDioptraBlock)) {
            return;
        }
        List<BlockPos> members = discover(level, origin);
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos member : members) {
            minX = Math.min(minX, member.getX());
            minZ = Math.min(minZ, member.getZ());
            maxX = Math.max(maxX, member.getX());
            maxZ = Math.max(maxZ, member.getZ());
        }
        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        for (BlockPos member : members) {
            if (level.getBlockEntity(member) instanceof ThaumicDioptraBlockEntity dioptra) {
                ChunkPos center = windowCenter(member.getX(), member.getZ(), minX, minZ, width, depth);
                dioptra.applyWindowCenter(level, center.x, center.z);
            }
        }
    }

    /**
     * The window-center chunk for one member of a group with the given bounding box.
     * Anchored at the min-corner member's chunk, shifted a full window per block of
     * offset, then pulled back half the group's span so the combined map is centered
     * on the group. Adjacent members always differ by exactly {@link #WINDOW}, which is
     * precisely the seamless-tiling condition. Pure math, exercised directly by tests.
     */
    public static ChunkPos windowCenter(int memberX, int memberZ, int minX, int minZ, int width, int depth) {
        int anchorChunkX = SectionPos.blockToSectionCoord(minX);
        int anchorChunkZ = SectionPos.blockToSectionCoord(minZ);
        int shiftX = WINDOW * (width - 1) / 2;
        int shiftZ = WINDOW * (depth - 1) / 2;
        return new ChunkPos(
                anchorChunkX + WINDOW * (memberX - minX) - shiftX,
                anchorChunkZ + WINDOW * (memberZ - minZ) - shiftZ);
    }

    /** 4-way flood fill over same-Y dioptra blocks, bounded by {@link #MAX_REACH}. */
    private static List<BlockPos> discover(ServerLevel level, BlockPos origin) {
        List<BlockPos> members = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> frontier = new ArrayDeque<>();
        visited.add(origin);
        frontier.add(origin);
        while (!frontier.isEmpty()) {
            BlockPos pos = frontier.poll();
            if (!(level.getBlockState(pos).getBlock() instanceof ThaumicDioptraBlock)) {
                continue;
            }
            members.add(pos);
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos next = pos.relative(direction);
                if (Math.max(Math.abs(next.getX() - origin.getX()), Math.abs(next.getZ() - origin.getZ())) <= MAX_REACH
                        && visited.add(next)) {
                    frontier.add(next);
                }
            }
        }
        return members;
    }
}
