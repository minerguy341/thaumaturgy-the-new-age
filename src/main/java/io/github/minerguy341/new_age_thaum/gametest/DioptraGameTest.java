package io.github.minerguy341.new_age_thaum.gametest;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.content.ThaumicDioptraBlockEntity;
import io.github.minerguy341.new_age_thaum.core.ModRegistries;
import io.github.minerguy341.new_age_thaum.core.aura.AuraField;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

/**
 * The Thaumic Dioptra: group tiling math through the production refresh path (lone
 * block centered, slabs seamless, splits recentering), the comparator and snapshot
 * reads of the aura field, and the scoped diffusion pass behind the
 * auraDiffusionScope config.
 */
//? if neoforge {
@net.neoforged.neoforge.gametest.GameTestHolder(NewAgeThaum.MOD_ID)
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
//?}
public class DioptraGameTest {

    private static ThaumicDioptraBlockEntity place(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModRegistries.THAUMIC_DIOPTRA.get());
        if (helper.getBlockEntity(pos) instanceof ThaumicDioptraBlockEntity dioptra) {
            return dioptra;
        }
        helper.fail("Placed dioptra has no block entity at " + pos);
        return null;
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void loneDioptraCentersOnOwnChunk(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        ThaumicDioptraBlockEntity dioptra = place(helper, pos);
        if (dioptra == null) {
            return;
        }
        dioptra.serverTick(helper.getLevel()); // first tick runs the group refresh
        ChunkPos own = new ChunkPos(helper.absolutePos(pos));
        helper.assertTrue(dioptra.windowCenter().equals(own),
                "A lone dioptra must center on its own chunk, got " + dioptra.windowCenter()
                        + " expected " + own);
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void squareGroupTilesSeamlessly(GameTestHelper helper) {
        ThaumicDioptraBlockEntity nw = place(helper, new BlockPos(1, 2, 1));
        ThaumicDioptraBlockEntity ne = place(helper, new BlockPos(2, 2, 1));
        ThaumicDioptraBlockEntity sw = place(helper, new BlockPos(1, 2, 2));
        ThaumicDioptraBlockEntity se = place(helper, new BlockPos(2, 2, 2));
        if (nw == null || ne == null || sw == null || se == null) {
            return;
        }
        nw.serverTick(helper.getLevel()); // one refresh recomputes the whole group

        // Adjacent windows differing by exactly 13 chunks along the offset axis IS the
        // seamless-tiling condition: no gap, no overlap, a combined 26x26 map.
        ChunkPos base = nw.windowCenter();
        helper.assertTrue(ne.windowCenter().x == base.x + 13 && ne.windowCenter().z == base.z,
                "East neighbor must shift +13 chunks in x, got " + ne.windowCenter() + " from " + base);
        helper.assertTrue(sw.windowCenter().x == base.x && sw.windowCenter().z == base.z + 13,
                "South neighbor must shift +13 chunks in z, got " + sw.windowCenter() + " from " + base);
        helper.assertTrue(se.windowCenter().x == base.x + 13 && se.windowCenter().z == base.z + 13,
                "Diagonal neighbor must shift +13 in both axes, got " + se.windowCenter() + " from " + base);
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void groupSplitRecenters(GameTestHelper helper) {
        BlockPos westPos = new BlockPos(1, 2, 1);
        BlockPos middlePos = new BlockPos(2, 2, 1);
        BlockPos eastPos = new BlockPos(3, 2, 1);
        ThaumicDioptraBlockEntity west = place(helper, westPos);
        ThaumicDioptraBlockEntity middle = place(helper, middlePos);
        ThaumicDioptraBlockEntity east = place(helper, eastPos);
        if (west == null || middle == null || east == null) {
            return;
        }
        west.serverTick(helper.getLevel());
        helper.assertTrue(east.windowCenter().x == west.windowCenter().x + 26,
                "A 1x3 row must span 26 chunks of shift end to end, got "
                        + west.windowCenter() + " .. " + east.windowCenter());

        // Removing the middle splits the group; onRemove refreshes each fragment.
        helper.setBlock(middlePos, Blocks.AIR);
        ChunkPos westOwn = new ChunkPos(helper.absolutePos(westPos));
        ChunkPos eastOwn = new ChunkPos(helper.absolutePos(eastPos));
        helper.assertTrue(west.windowCenter().equals(westOwn),
                "After the split the west survivor must recenter on its own chunk, got "
                        + west.windowCenter() + " expected " + westOwn);
        helper.assertTrue(east.windowCenter().equals(eastOwn),
                "After the split the east survivor must recenter on its own chunk, got "
                        + east.windowCenter() + " expected " + eastOwn);
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void comparatorSignalTracksOwnChunkVis(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        ThaumicDioptraBlockEntity dioptra = place(helper, pos);
        if (dioptra == null) {
            return;
        }
        var level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(pos);
        AuraField aura = AuraField.get(level);
        long ownChunk = new ChunkPos(absolute).toLong();

        aura.add(ownChunk, -AuraField.CHUNK_CAP * 2); // absolute baseline: drain to 0
        dioptra.refreshComparator(level);
        helper.assertTrue(level.getBlockState(absolute).getAnalogOutputSignal(level, absolute) == 0,
                "Zero vis must read comparator 0, got " + dioptra.comparatorSignal());

        aura.add(ownChunk, 50f);
        dioptra.refreshComparator(level);
        helper.assertTrue(level.getBlockState(absolute).getAnalogOutputSignal(level, absolute) == 8,
                "Half-cap vis must read comparator 8, got " + dioptra.comparatorSignal());

        aura.add(ownChunk, AuraField.CHUNK_CAP * 2); // clamps at the cap
        dioptra.refreshComparator(level);
        helper.assertTrue(level.getBlockState(absolute).getAnalogOutputSignal(level, absolute) == 15,
                "Capped vis must read comparator 15, got " + dioptra.comparatorSignal());
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void snapshotMirrorsAuraField(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        ThaumicDioptraBlockEntity dioptra = place(helper, pos);
        if (dioptra == null) {
            return;
        }
        var level = helper.getLevel();
        dioptra.serverTick(level); // settle the window on the block's own chunk
        AuraField aura = AuraField.get(level);
        ChunkPos center = dioptra.windowCenter();
        long ownChunk = center.toLong();
        long cornerChunk = new ChunkPos(center.x + 6, center.z + 6).toLong();

        aura.add(ownChunk, -AuraField.CHUNK_CAP * 2);
        aura.add(ownChunk, 33f);
        aura.add(cornerChunk, -AuraField.CHUNK_CAP * 2);
        aura.add(cornerChunk, 77f);
        aura.addFlux(cornerChunk, 44f); // flux rides the same snapshot
        // The gametest world's AuraField is shared across all concurrent tests, and flux
        // diffuses — so the centre chunk can carry stray flux bled in from a neighbour. Zero
        // it here (mirroring the vis reset above) so "flux-free cell" is genuinely flux-free.
        aura.addFlux(ownChunk, -aura.flux(ownChunk));
        dioptra.refreshSnapshot(level);

        float[] window = dioptra.visWindow();
        float[] fluxWindow = dioptra.fluxWindow();
        int grid = ThaumicDioptraBlockEntity.GRID;
        float centerCell = window[(6 * grid) + 6];
        float cornerCell = window[(12 * grid) + 12];
        helper.assertTrue(Math.abs(centerCell - 33f) < 1.0e-4f,
                "Center cell must mirror the aura field, got " + centerCell);
        helper.assertTrue(Math.abs(cornerCell - 77f) < 1.0e-4f,
                "Window corner cell must mirror the aura field, got " + cornerCell);
        helper.assertTrue(Math.abs(fluxWindow[(12 * grid) + 12] - 44f) < 1.0e-4f,
                "Corner cell flux must mirror the aura field, got " + fluxWindow[(12 * grid) + 12]);
        helper.assertTrue(fluxWindow[(6 * grid) + 6] == 0f,
                "A flux-free cell must read zero flux, got " + fluxWindow[(6 * grid) + 6]);
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void scopedDiffusionSkipsExcludedSources(GameTestHelper helper) {
        AuraField field = new AuraField();
        long source = new ChunkPos(0, 0).toLong();
        long frontier = new ChunkPos(1, 0).toLong();
        long beyond = new ChunkPos(2, 0).toLong();
        field.add(source, 80f);
        field.add(frontier, 60f);

        field.diffuse(chunk -> chunk == source);

        helper.assertTrue(field.vis(source) < 80f,
                "An in-scope chunk must diffuse, got " + field.vis(source));
        helper.assertTrue(field.vis(frontier) > 60f,
                "An out-of-scope chunk must still RECEIVE inflow, got " + field.vis(frontier));
        helper.assertTrue(field.vis(beyond) == 0f,
                "An out-of-scope chunk must not act as a source, got " + field.vis(beyond));
        helper.succeed();
    }
}
