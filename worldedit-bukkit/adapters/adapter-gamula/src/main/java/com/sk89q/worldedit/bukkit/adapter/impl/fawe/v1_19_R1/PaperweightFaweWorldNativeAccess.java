package com.sk89q.worldedit.bukkit.adapter.impl.fawe.v1_19_R1;

import com.fastasyncworldedit.core.Fawe;
import com.fastasyncworldedit.core.math.IntPair;
import com.fastasyncworldedit.core.util.TaskManager;
import com.fastasyncworldedit.core.util.task.RunnableVal;
import com.sk89q.jnbt.CompoundTag;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.internal.block.BlockStateIdAccess;
import com.sk89q.worldedit.internal.wna.WorldNativeAccess;
import com.sk89q.worldedit.util.SideEffect;
import com.sk89q.worldedit.util.SideEffectSet;
import com.sk89q.worldedit.util.nbt.CompoundBinaryTag;
import com.sk89q.worldedit.world.block.BlockState;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.PlayerChunk;
import net.minecraft.server.level.ChunkProviderServer;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.TileEntity;
import net.minecraft.world.level.chunk.Chunk;
import org.bukkit.craftbukkit.v1_19_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R1.block.data.CraftBlockData;
import org.bukkit.event.block.BlockPhysicsEvent;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class PaperweightFaweWorldNativeAccess implements WorldNativeAccess<Chunk,
        net.minecraft.world.level.block.state.IBlockData, BlockPosition> {

    private static final int UPDATE = 1;
    private static final int NOTIFY = 2;
    private static final EnumDirection[] NEIGHBOUR_ORDER = {
            EnumDirection.EAST,
            EnumDirection.WEST,
            EnumDirection.DOWN,
            EnumDirection.UP,
            EnumDirection.NORTH,
            EnumDirection.SOUTH
    };
    private final PaperweightFaweAdapter paperweightFaweAdapter;
    private final WeakReference<World> level;
    private final AtomicInteger lastTick;
    private final Set<CachedChange> cachedChanges = new HashSet<>();
    private final Set<IntPair> cachedChunksToSend = new HashSet<>();
    private SideEffectSet sideEffectSet;

    public PaperweightFaweWorldNativeAccess(PaperweightFaweAdapter paperweightFaweAdapter, WeakReference<World> level) {
        this.paperweightFaweAdapter = paperweightFaweAdapter;
        this.level = level;
        // Use the actual tick as minecraft-defined so we don't try to force blocks into the world when the server's already lagging.
        //  - With the caveat that we don't want to have too many cached changed (1024) so we'd flush those at 1024 anyway.
        this.lastTick = new AtomicInteger(MinecraftServer.currentTick);
    }

    private World getLevel() {
        return Objects.requireNonNull(level.get(), "The reference to the world was lost");
    }

    @Override
    public void setCurrentSideEffectSet(SideEffectSet sideEffectSet) {
        this.sideEffectSet = sideEffectSet;
    }

    @Override
    public Chunk getChunk(int x, int z) {
        return getLevel().getChunk(x, z);
    }

    @Override
    public net.minecraft.world.level.block.state.IBlockData toNative(BlockState blockState) {
        int stateId = paperweightFaweAdapter.ordinalToIbdID(blockState.getOrdinalChar());
        return BlockStateIdAccess.isValidInternalId(stateId)
                ? Block.stateById(stateId)
                : ((CraftBlockData) BukkitAdapter.adapt(blockState)).getState();
    }

    @Override
    public net.minecraft.world.level.block.state.IBlockData getBlockState(Chunk levelChunk, BlockPosition blockPos) {
        return levelChunk.getBlockState(blockPos);
    }

    @Override
    public synchronized net.minecraft.world.level.block.state.IBlockData setBlockState(
            Chunk levelChunk, BlockPosition blockPos,
            net.minecraft.world.level.block.state.IBlockData blockState
    ) {
        int currentTick = MinecraftServer.currentTick;
        if (Fawe.isMainThread()) {
            return levelChunk.setBlockState(blockPos, blockState,
                    this.sideEffectSet != null && this.sideEffectSet.shouldApply(SideEffect.UPDATE)
            );
        }
        // Since FAWE is.. Async we need to do it on the main thread (wooooo.. :( )
        cachedChanges.add(new CachedChange(levelChunk, blockPos, blockState));
        cachedChunksToSend.add(new IntPair(levelChunk.locX, levelChunk.locZ));
        boolean nextTick = lastTick.get() > currentTick;
        if (nextTick || cachedChanges.size() >= 1024) {
            if (nextTick) {
                lastTick.set(currentTick);
            }
            flushAsync(nextTick);
        }
        return blockState;
    }

    @Override
    public net.minecraft.world.level.block.state.IBlockData getValidBlockForPosition(
            net.minecraft.world.level.block.state.IBlockData blockState,
            BlockPosition blockPos
    ) {
        return Block.updateFromNeighbourShapes(blockState, getLevel(), blockPos);
    }

    @Override
    public BlockPosition getPosition(int x, int y, int z) {
        return new BlockPosition(x, y, z);
    }

    @Override
    public void updateLightingForBlock(BlockPosition blockPos) {
        getLevel().getChunkSource().getLightEngine().checkBlock(blockPos);
    }

    @Override
    public boolean updateTileEntity(BlockPosition blockPos, CompoundBinaryTag tag) {
        // We will assume that the tile entity was created for us,
        // though we do not do this on the other versions
        TileEntity blockEntity = getLevel().getBlockEntity(blockPos);
        if (blockEntity == null) {
            return false;
        }
        net.minecraft.nbt.NBTBase nativeTag = paperweightFaweAdapter.fromNativeBinary(tag);
        blockEntity.load((NBTTagCompound) nativeTag);
        return true;
    }

    @Override
    public void notifyBlockUpdate(
            Chunk levelChunk, BlockPosition blockPos,
            net.minecraft.world.level.block.state.IBlockData oldState,
            net.minecraft.world.level.block.state.IBlockData newState
    ) {
        if (levelChunk.getSections()[level.get().getSectionIndex(blockPos.getY())] != null) {
            getLevel().sendBlockUpdated(blockPos, oldState, newState, UPDATE | NOTIFY);
        }
    }

    @Override
    public boolean isChunkTicking(Chunk levelChunk) {
        return levelChunk.getFullStatus().isOrAfter(PlayerChunk.State.TICKING);
    }

    @Override
    public void markBlockChanged(Chunk levelChunk, BlockPosition blockPos) {
        if (levelChunk.getSections()[level.get().getSectionIndex(blockPos.getY())] != null) {
            ((ChunkProviderServer) getLevel().getChunkSource()).blockChanged(blockPos);
        }
    }

    @Override
    public void notifyNeighbors(
            BlockPosition blockPos,
            net.minecraft.world.level.block.state.IBlockData oldState,
            net.minecraft.world.level.block.state.IBlockData newState
    ) {
        World level = getLevel();
        if (sideEffectSet.shouldApply(SideEffect.EVENTS)) {
            level.blockUpdated(blockPos, oldState.getBlock());
        } else {
            // When we don't want events, manually run the physics without them.
            // Un-nest neighbour updating
            for (EnumDirection direction : NEIGHBOUR_ORDER) {
                BlockPosition shifted = blockPos.relative(direction);
                level.getBlockState(shifted).neighborChanged(level, shifted, oldState.getBlock(), blockPos, false);
            }
        }
        if (newState.hasAnalogOutputSignal()) {
            level.updateNeighbourForOutputSignal(blockPos, newState.getBlock());
        }
    }

    @Override
    public void updateNeighbors(
            BlockPosition blockPos,
            net.minecraft.world.level.block.state.IBlockData oldState,
            net.minecraft.world.level.block.state.IBlockData newState,
            int recursionLimit
    ) {
        World level = getLevel();
        // a == updateNeighbors
        // b == updateDiagonalNeighbors
        oldState.updateIndirectNeighbourShapes(level, blockPos, NOTIFY, recursionLimit);
        if (sideEffectSet.shouldApply(SideEffect.EVENTS)) {
            CraftWorld craftWorld = level.getWorld();
            if (craftWorld != null) {
                BlockPhysicsEvent event = new BlockPhysicsEvent(
                        craftWorld.getBlockAt(blockPos.getX(), blockPos.getY(), blockPos.getZ()),
                        CraftBlockData.fromData(newState)
                );
                level.getCraftServer().getPluginManager().callEvent(event);
                if (event.isCancelled()) {
                    return;
                }
            }
        }
        newState.triggerEvent(level, blockPos, NOTIFY, recursionLimit);
        newState.updateIndirectNeighbourShapes(level, blockPos, NOTIFY, recursionLimit);
    }

    @Override
    public void onBlockStateChange(
            BlockPosition blockPos,
            net.minecraft.world.level.block.state.IBlockData oldState,
            net.minecraft.world.level.block.state.IBlockData newState
    ) {
        getLevel().onBlockStateChange(blockPos, oldState, newState);
    }

    private synchronized void flushAsync(final boolean sendChunks) {
        final Set<CachedChange> changes = Set.copyOf(cachedChanges);
        cachedChanges.clear();
        final Set<IntPair> toSend;
        if (sendChunks) {
            toSend = Set.copyOf(cachedChunksToSend);
            cachedChunksToSend.clear();
        } else {
            toSend = Collections.emptySet();
        }
        RunnableVal<Object> runnableVal = new RunnableVal<>() {
            @Override
            public void run(Object value) {
                changes.forEach(cc -> cc.levelChunk.setBlockState(cc.blockPos, cc.blockState,
                        sideEffectSet != null && sideEffectSet.shouldApply(SideEffect.UPDATE)
                ));
                if (!sendChunks) {
                    return;
                }
                for (IntPair chunk : toSend) {
                    PaperweightPlatformAdapter.sendChunk(getLevel().getWorld().getHandle(), chunk.x(), chunk.z(), false);
                }
            }
        };
        TaskManager.taskManager().async(() -> TaskManager.taskManager().sync(runnableVal));
    }

    @Override
    public synchronized void flush() {
        RunnableVal<Object> runnableVal = new RunnableVal<>() {
            @Override
            public void run(Object value) {
                cachedChanges.forEach(cc -> cc.levelChunk.setBlockState(cc.blockPos, cc.blockState,
                        sideEffectSet != null && sideEffectSet.shouldApply(SideEffect.UPDATE)
                ));
                for (IntPair chunk : cachedChunksToSend) {
                    PaperweightPlatformAdapter.sendChunk(getLevel().getWorld().getHandle(), chunk.x(), chunk.z(), false);
                }
            }
        };
        if (Fawe.isMainThread()) {
            runnableVal.run();
        } else {
            TaskManager.taskManager().sync(runnableVal);
        }
        cachedChanges.clear();
        cachedChunksToSend.clear();
    }

    private record CachedChange(
            Chunk levelChunk,
            BlockPosition blockPos,
            net.minecraft.world.level.block.state.IBlockData blockState
    ) {

    }

}
