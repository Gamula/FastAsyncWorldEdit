/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.bukkit.adapter.ext.fawe.v1_19_R1;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.internal.block.BlockStateIdAccess;
import com.sk89q.worldedit.internal.wna.WorldNativeAccess;
import com.sk89q.worldedit.util.SideEffect;
import com.sk89q.worldedit.util.SideEffectSet;
import com.sk89q.worldedit.util.nbt.CompoundBinaryTag;
import com.sk89q.worldedit.world.block.BlockState;
import net.minecraft.core.BlockPosition;
import net.minecraft.server.level.PlayerChunk;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.Chunk;
import org.bukkit.craftbukkit.v1_19_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R1.block.data.CraftBlockData;
import org.bukkit.event.block.BlockPhysicsEvent;

import java.lang.ref.WeakReference;
import java.util.Objects;

public class PaperweightWorldNativeAccess implements WorldNativeAccess<Chunk, net.minecraft.world.level.block.state.IBlockData, BlockPosition> {
    private static final int UPDATE = 1;
    private static final int NOTIFY = 2;

    private final PaperweightAdapter adapter;
    private final WeakReference<WorldServer> world;
    private SideEffectSet sideEffectSet;

    public PaperweightWorldNativeAccess(PaperweightAdapter adapter, WeakReference<WorldServer> world) {
        this.adapter = adapter;
        this.world = world;
    }

    private WorldServer getWorld() {
        return Objects.requireNonNull(world.get(), "The reference to the world was lost");
    }

    @Override
    public void setCurrentSideEffectSet(SideEffectSet sideEffectSet) {
        this.sideEffectSet = sideEffectSet;
    }

    @Override
    public Chunk getChunk(int x, int z) {
        return getWorld().getChunk(x, z);
    }

    @Override
    public net.minecraft.world.level.block.state.IBlockData toNative(BlockState state) {
        int stateId = BlockStateIdAccess.getBlockStateId(state);
        return BlockStateIdAccess.isValidInternalId(stateId)
                ? Block.stateById(stateId)
                : ((CraftBlockData) BukkitAdapter.adapt(state)).getState();
    }

    @Override
    public net.minecraft.world.level.block.state.IBlockData getBlockState(Chunk chunk, BlockPosition position) {
        return chunk.getBlockState(position);
    }

    @Override
    public net.minecraft.world.level.block.state.IBlockData setBlockState(Chunk chunk, BlockPosition position, net.minecraft.world.level.block.state.IBlockData state) {
        return chunk.setBlockState(position, state, false, this.sideEffectSet.shouldApply(SideEffect.UPDATE));
    }

    @Override
    public net.minecraft.world.level.block.state.IBlockData getValidBlockForPosition(net.minecraft.world.level.block.state.IBlockData block, BlockPosition position) {
        return Block.updateFromNeighbourShapes(block, getWorld(), position);
    }

    @Override
    public BlockPosition getPosition(int x, int y, int z) {
        return new BlockPosition(x, y, z);
    }

    @Override
    public void updateLightingForBlock(BlockPosition position) {
        getWorld().getChunkSource().getLightEngine().checkBlock(position);
    }

    @Override
    public boolean updateTileEntity(final BlockPosition position, final CompoundBinaryTag tag) {
        return false;
    }

    @Override
    public void notifyBlockUpdate(Chunk chunk, BlockPosition position, net.minecraft.world.level.block.state.IBlockData oldState, net.minecraft.world.level.block.state.IBlockData newState) {
        if (chunk.getSections()[getWorld().getSectionIndex(position.getY())] != null) {
            getWorld().sendBlockUpdated(position, oldState, newState, UPDATE | NOTIFY);
        }
    }

    @Override
    public boolean isChunkTicking(Chunk chunk) {
        return chunk.getFullStatus().isOrAfter(PlayerChunk.State.TICKING);
    }

    @Override
    public void markBlockChanged(Chunk chunk, BlockPosition position) {
        if (chunk.getSections()[getWorld().getSectionIndex(position.getY())] != null) {
            getWorld().getChunkSource().blockChanged(position);
        }
    }

    @Override
    public void notifyNeighbors(BlockPosition pos, net.minecraft.world.level.block.state.IBlockData oldState, net.minecraft.world.level.block.state.IBlockData newState) {
        WorldServer world = getWorld();
        if (sideEffectSet.shouldApply(SideEffect.EVENTS)) {
            world.updateNeighborsAt(pos, oldState.getBlock());
        } else {
            // When we don't want events, manually run the physics without them.
            Block block = oldState.getBlock();
            fireNeighborChanged(pos, world, block, pos.west());
            fireNeighborChanged(pos, world, block, pos.east());
            fireNeighborChanged(pos, world, block, pos.below());
            fireNeighborChanged(pos, world, block, pos.above());
            fireNeighborChanged(pos, world, block, pos.north());
            fireNeighborChanged(pos, world, block, pos.south());
        }
        if (newState.hasAnalogOutputSignal()) {
            world.updateNeighbourForOutputSignal(pos, newState.getBlock());
        }
    }

    // Not sure why neighborChanged is deprecated
    @SuppressWarnings("deprecation")
    private void fireNeighborChanged(BlockPosition pos, WorldServer world, Block block, BlockPosition neighborPos) {
        world.getBlockState(neighborPos).neighborChanged(world, neighborPos, block, pos, false);
    }

    @Override
    public void updateNeighbors(BlockPosition pos, net.minecraft.world.level.block.state.IBlockData oldState, net.minecraft.world.level.block.state.IBlockData newState, int recursionLimit) {
        WorldServer world = getWorld();
        // a == updateNeighbors
        // b == updateDiagonalNeighbors
        oldState.updateIndirectNeighbourShapes(world, pos, NOTIFY, recursionLimit);
        if (sideEffectSet.shouldApply(SideEffect.EVENTS)) {
            CraftWorld craftWorld = world.getWorld();
            BlockPhysicsEvent event = new BlockPhysicsEvent(craftWorld.getBlockAt(pos.getX(), pos.getY(), pos.getZ()), CraftBlockData.fromData(newState));
            world.getCraftServer().getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return;
            }
        }
        newState.updateNeighbourShapes(world, pos, NOTIFY, recursionLimit);
        newState.updateIndirectNeighbourShapes(world, pos, NOTIFY, recursionLimit);
    }

    @Override
    public void onBlockStateChange(BlockPosition pos, net.minecraft.world.level.block.state.IBlockData oldState, net.minecraft.world.level.block.state.IBlockData newState) {
        getWorld().onBlockStateChange(pos, oldState, newState);
    }

    @Override
    public void flush() {

    }
}
