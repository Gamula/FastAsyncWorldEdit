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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Lifecycle;
import com.sk89q.jnbt.NBTConstants;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.blocks.BaseItem;
import com.sk89q.worldedit.blocks.BaseItemStack;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.adapter.BukkitImplAdapter;
import com.sk89q.worldedit.bukkit.adapter.Refraction;
import com.sk89q.worldedit.bukkit.adapter.impl.fawe.v1_19_R1.PaperweightPlatformAdapter;
import com.sk89q.worldedit.entity.BaseEntity;
import com.sk89q.worldedit.extension.platform.Watchdog;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.internal.Constants;
import com.sk89q.worldedit.internal.block.BlockStateIdAccess;
import com.sk89q.worldedit.internal.wna.WorldNativeAccess;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.registry.state.BooleanProperty;
import com.sk89q.worldedit.registry.state.DirectionalProperty;
import com.sk89q.worldedit.registry.state.EnumProperty;
import com.sk89q.worldedit.registry.state.IntegerProperty;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.util.Direction;
import com.sk89q.worldedit.util.SideEffect;
import com.sk89q.worldedit.util.concurrency.LazyReference;
import com.sk89q.worldedit.util.formatting.text.Component;
import com.sk89q.worldedit.util.formatting.text.TranslatableComponent;
import com.sk89q.worldedit.util.io.file.SafeFiles;
import com.sk89q.worldedit.util.nbt.BinaryTag;
import com.sk89q.worldedit.util.nbt.ByteArrayBinaryTag;
import com.sk89q.worldedit.util.nbt.ByteBinaryTag;
import com.sk89q.worldedit.util.nbt.CompoundBinaryTag;
import com.sk89q.worldedit.util.nbt.DoubleBinaryTag;
import com.sk89q.worldedit.util.nbt.EndBinaryTag;
import com.sk89q.worldedit.util.nbt.FloatBinaryTag;
import com.sk89q.worldedit.util.nbt.IntArrayBinaryTag;
import com.sk89q.worldedit.util.nbt.IntBinaryTag;
import com.sk89q.worldedit.util.nbt.ListBinaryTag;
import com.sk89q.worldedit.util.nbt.LongArrayBinaryTag;
import com.sk89q.worldedit.util.nbt.LongBinaryTag;
import com.sk89q.worldedit.util.nbt.ShortBinaryTag;
import com.sk89q.worldedit.util.nbt.StringBinaryTag;
import com.sk89q.worldedit.world.DataFixer;
import com.sk89q.worldedit.world.RegenOptions;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.biome.BiomeTypes;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.sk89q.worldedit.world.item.ItemType;
import net.minecraft.SystemUtils;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.IRegistry;
import net.minecraft.network.protocol.game.PacketPlayOutTileEntityData;
import net.minecraft.network.protocol.game.PacketPlayOutEntityStatus;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.PlayerChunk;
import net.minecraft.server.level.ChunkProviderServer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.server.level.progress.WorldLoadListener;
import net.minecraft.util.INamable;
import net.minecraft.util.thread.IAsyncTaskHandler;
import net.minecraft.world.Clearable;
import net.minecraft.world.EnumHand;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.ItemActionContext;
import net.minecraft.world.level.ChunkCoordIntPair;
import net.minecraft.world.level.WorldSettings;
import net.minecraft.world.level.biome.BiomeBase;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.TileEntity;
import net.minecraft.world.level.block.entity.TileEntityStructure;
import net.minecraft.world.level.block.state.BlockStateList;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.block.state.properties.BlockStateDirection;
import net.minecraft.world.level.block.state.properties.BlockStateEnum;
import net.minecraft.world.level.chunk.IChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.Chunk;
import net.minecraft.world.level.dimension.WorldDimension;
import net.minecraft.world.level.levelgen.GeneratorSettings;
import net.minecraft.world.level.storage.Convertable;
import net.minecraft.world.level.storage.WorldDataServer;
import net.minecraft.world.phys.MovingObjectPositionBlock;
import net.minecraft.world.phys.Vec3D;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World.Environment;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.v1_19_R1.CraftServer;
import org.bukkit.craftbukkit.v1_19_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R1.block.data.CraftBlockData;
import org.bukkit.craftbukkit.v1_19_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_19_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_19_R1.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_19_R1.util.CraftMagicNumbers;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.generator.ChunkGenerator;
import org.spigotmc.SpigotConfig;
import org.spigotmc.WatchdogThread;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public final class PaperweightAdapter implements BukkitImplAdapter<net.minecraft.nbt.NBTBase> {

    private final Logger LOGGER = Logger.getLogger(getClass().getCanonicalName());

    private final Field serverWorldsField;
    private final Method getChunkFutureMethod;
    private final Field chunkProviderExecutorField;
    private final Watchdog watchdog;

    // ------------------------------------------------------------------------
    // Code that may break between versions of Minecraft
    // ------------------------------------------------------------------------

    public PaperweightAdapter() throws NoSuchFieldException, NoSuchMethodException {
        // A simple test
        CraftServer.class.cast(Bukkit.getServer());

        serverWorldsField = CraftServer.class.getDeclaredField("worlds");
        serverWorldsField.setAccessible(true);

        getChunkFutureMethod = ChunkProviderServer.class.getDeclaredMethod(
                Refraction.pickName("getChunkFutureMainThread", "c"),
                int.class, int.class, ChunkStatus.class, boolean.class
        );
        getChunkFutureMethod.setAccessible(true);

        chunkProviderExecutorField = ChunkProviderServer.class.getDeclaredField(
                Refraction.pickName("mainThreadProcessor", "g")
        );
        chunkProviderExecutorField.setAccessible(true);

        new PaperweightDataConverters(CraftMagicNumbers.INSTANCE.getDataVersion(), this).buildUnoptimized();

        Watchdog watchdog;
        try {
            Class.forName("org.spigotmc.WatchdogThread");
            watchdog = new SpigotWatchdog();
        } catch (ClassNotFoundException | NoSuchFieldException e) {
            try {
                watchdog = new MojangWatchdog(((CraftServer) Bukkit.getServer()).getServer());
            } catch (NoSuchFieldException ex) {
                watchdog = null;
            }
        }
        this.watchdog = watchdog;

        try {
            Class.forName("org.spigotmc.SpigotConfig");
            SpigotConfig.config.set("world-settings.faweregentempworld.verbose", false);
        } catch (ClassNotFoundException ignored) {
        }
    }

    @Override
    public DataFixer getDataFixer() {
        return PaperweightDataConverters.INSTANCE;
    }

    /**
     * Read the given NBT data into the given tile entity.
     *
     * @param tileEntity the tile entity
     * @param tag the tag
     */
    static void readTagIntoTileEntity(net.minecraft.nbt.NBTTagCompound tag, TileEntity tileEntity) {
        tileEntity.load(tag);
        tileEntity.setChanged();
    }

    /**
     * Get the ID string of the given entity.
     *
     * @param entity the entity
     * @return the entity ID
     */
    private static String getEntityId(Entity entity) {
        return EntityTypes.getKey(entity.getType()).toString();
    }

    /**
     * Create an entity using the given entity ID.
     *
     * @param id the entity ID
     * @param world the world
     * @return an entity or null
     */
    private static Entity createEntityFromId(String id, net.minecraft.world.level.World world) {
        return EntityTypes.byString(id).map(t -> t.create(world)).orElse(null);
    }

    /**
     * Write the given NBT data into the given entity.
     *
     * @param entity the entity
     * @param tag the tag
     */
    private static void readTagIntoEntity(net.minecraft.nbt.NBTTagCompound tag, Entity entity) {
        entity.load(tag);
    }

    /**
     * Write the entity's NBT data to the given tag.
     *
     * @param entity the entity
     * @param tag the tag
     */
    private static void readEntityIntoTag(Entity entity, net.minecraft.nbt.NBTTagCompound tag) {
        //FAWE start - avoid villager async catcher
        PaperweightPlatformAdapter.readEntityIntoTag(entity, tag);
        //FAWE end
    }

    private static Block getBlockFromType(BlockType blockType) {

        return DedicatedServer.getServer().registryAccess().registryOrThrow(IRegistry.BLOCK_REGISTRY).get(MinecraftKey.tryParse(blockType.getId()));
    }

    private static Item getItemFromType(ItemType itemType) {
        return DedicatedServer.getServer().registryAccess().registryOrThrow(IRegistry.ITEM_REGISTRY).get(MinecraftKey.tryParse(itemType.getId()));
    }

    @Override
    public OptionalInt getInternalBlockStateId(BlockData data) {
        net.minecraft.world.level.block.state.IBlockData state = ((CraftBlockData) data).getState();
        int combinedId = Block.getId(state);
        return combinedId == 0 && state.getBlock() != Blocks.AIR ? OptionalInt.empty() : OptionalInt.of(combinedId);
    }

    @Override
    public OptionalInt getInternalBlockStateId(BlockState state) {
        Block mcBlock = getBlockFromType(state.getBlockType());
        net.minecraft.world.level.block.state.IBlockData newState = mcBlock.defaultBlockState();
        Map<Property<?>, Object> states = state.getStates();
        newState = applyProperties(mcBlock.getStateDefinition(), newState, states);
        final int combinedId = Block.getId(newState);
        return combinedId == 0 && state.getBlockType() != BlockTypes.AIR ? OptionalInt.empty() : OptionalInt.of(combinedId);
    }

    @Override
    public BlockState getBlock(Location location) {
        checkNotNull(location);

        CraftWorld craftWorld = ((CraftWorld) location.getWorld());
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        final WorldServer handle = craftWorld.getHandle();
        Chunk chunk = handle.getChunk(x >> 4, z >> 4);
        final BlockPosition blockPos = new BlockPosition(x, y, z);
        final net.minecraft.world.level.block.state.IBlockData blockData = chunk.getBlockState(blockPos);
        int internalId = Block.getId(blockData);
        BlockState state = BlockStateIdAccess.getBlockStateById(internalId);
        if (state == null) {
            org.bukkit.block.Block bukkitBlock = location.getBlock();
            state = BukkitAdapter.adapt(bukkitBlock.getBlockData());
        }

        return state;
    }

    @Override
    public BaseBlock getFullBlock(Location location) {
        BlockState state = getBlock(location);

        CraftWorld craftWorld = ((CraftWorld) location.getWorld());
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        final WorldServer handle = craftWorld.getHandle();
        Chunk chunk = handle.getChunk(x >> 4, z >> 4);
        final BlockPosition blockPos = new BlockPosition(x, y, z);

        // Read the NBT data
        TileEntity te = chunk.getBlockEntity(blockPos);
        if (te != null) {
            net.minecraft.nbt.NBTTagCompound tag = te.saveWithId();
            return state.toBaseBlock((CompoundBinaryTag) toNativeBinary(tag));
        }

        return state.toBaseBlock();
    }

    @Override
    public WorldNativeAccess<?, ?, ?> createWorldNativeAccess(org.bukkit.World world) {
        return new PaperweightWorldNativeAccess(this,
                new WeakReference<>(((CraftWorld) world).getHandle()));
    }

    private static net.minecraft.core.EnumDirection adapt(Direction face) {
        switch (face) {
            case NORTH:
                return net.minecraft.core.EnumDirection.NORTH;
            case SOUTH:
                return net.minecraft.core.EnumDirection.SOUTH;
            case WEST:
                return net.minecraft.core.EnumDirection.WEST;
            case EAST:
                return net.minecraft.core.EnumDirection.EAST;
            case DOWN:
                return net.minecraft.core.EnumDirection.DOWN;
            case UP:
            default:
                return net.minecraft.core.EnumDirection.UP;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private net.minecraft.world.level.block.state.IBlockData applyProperties(
            BlockStateList<Block, net.minecraft.world.level.block.state.IBlockData> stateContainer,
            net.minecraft.world.level.block.state.IBlockData newState,
            Map<Property<?>, Object> states
    ) {
        for (Map.Entry<Property<?>, Object> state : states.entrySet()) {
            net.minecraft.world.level.block.state.properties.IBlockState<?> property =
                    stateContainer.getProperty(state.getKey().getName());
            Comparable<?> value = (Comparable) state.getValue();
            // we may need to adapt this value, depending on the source prop
            if (property instanceof BlockStateDirection) {
                Direction dir = (Direction) value;
                value = adapt(dir);
            } else if (property instanceof BlockStateEnum<?>) {
                String enumName = (String) value;
                value = ((BlockStateEnum<?>) property)
                        .getValue(enumName).orElseThrow(() ->
                                new IllegalStateException(
                                        "Enum property " + property.getName() + " does not contain " + enumName
                                )
                        );
            }

            newState = newState.setValue(
                    (net.minecraft.world.level.block.state.properties.IBlockState<?>) property,
                    (Comparable) value
            );
        }
        return newState;
    }

    @Override
    public BaseEntity getEntity(org.bukkit.entity.Entity entity) {
        checkNotNull(entity);

        CraftEntity craftEntity = ((CraftEntity) entity);
        Entity mcEntity = craftEntity.getHandle();

        // Do not allow creating of passenger entity snapshots, passengers are included in the vehicle entity
        if (mcEntity.isPassenger()) {
            return null;
        }

        String id = getEntityId(mcEntity);

        net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
        readEntityIntoTag(mcEntity, tag);
        return new BaseEntity(
                com.sk89q.worldedit.world.entity.EntityTypes.get(id),
                LazyReference.from(() -> (CompoundBinaryTag) toNativeBinary(tag))
        );
    }

    @Override
    public org.bukkit.entity.Entity createEntity(Location location, BaseEntity state) {
        checkNotNull(location);
        checkNotNull(state);

        CraftWorld craftWorld = ((CraftWorld) location.getWorld());
        WorldServer worldServer = craftWorld.getHandle();

        Entity createdEntity = createEntityFromId(state.getType().getId(), craftWorld.getHandle());

        if (createdEntity != null) {
            CompoundBinaryTag nativeTag = state.getNbt();
            if (nativeTag != null) {
                net.minecraft.nbt.NBTTagCompound tag = (net.minecraft.nbt.NBTTagCompound) fromNativeBinary(nativeTag);
                for (String name : Constants.NO_COPY_ENTITY_NBT_FIELDS) {
                    tag.remove(name);
                }
                readTagIntoEntity(tag, createdEntity);
            }

            createdEntity.absMoveTo(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());

            worldServer.addFreshEntity(createdEntity, SpawnReason.CUSTOM);
            return createdEntity.getBukkitEntity();
        } else {
            return null;
        }
    }

    // This removes all unwanted tags from the main entity and all its passengers
    private void removeUnwantedEntityTagsRecursively(net.minecraft.nbt.NBTTagCompound tag) {
        for (String name : Constants.NO_COPY_ENTITY_NBT_FIELDS) {
            tag.remove(name);
        }

        // Adapted from net.minecraft.world.entity.EntityTypes#loadEntityRecursive
        if (tag.contains("Passengers", NBTConstants.TYPE_LIST)) {
            net.minecraft.nbt.NBTTagList nbttaglist = tag.getList("Passengers", NBTConstants.TYPE_COMPOUND);

            for (int i = 0; i < nbttaglist.size(); ++i) {
                removeUnwantedEntityTagsRecursively(nbttaglist.getCompound(i));
            }
        }
    }

    @Override
    public Component getRichBlockName(BlockType blockType) {
        return TranslatableComponent.of(getBlockFromType(blockType).getDescriptionId());
    }

    @Override
    public Component getRichItemName(ItemType itemType) {
        return TranslatableComponent.of(getItemFromType(itemType).getDescriptionId());
    }

    @Override
    public Component getRichItemName(BaseItemStack itemStack) {
        return TranslatableComponent.of(CraftItemStack.asNMSCopy(BukkitAdapter.adapt(itemStack)).getDescriptionId());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static final LoadingCache<net.minecraft.world.level.block.state.properties.IBlockState, Property<?>> PROPERTY_CACHE =
            CacheBuilder.newBuilder().build(new CacheLoader<net.minecraft.world.level.block.state.properties.IBlockState,
                    Property<?>>() {
        @Override
        public Property<?> load(net.minecraft.world.level.block.state.properties.IBlockState state) throws Exception {
            if (state instanceof net.minecraft.world.level.block.state.properties.BlockStateBoolean) {
                return new BooleanProperty(state.getName(), ImmutableList.copyOf(state.getPossibleValues()));
            } else if (state instanceof BlockStateDirection) {
                return new DirectionalProperty(state.getName(),
                        (List<Direction>) state.getPossibleValues().stream().map(e -> Direction.valueOf(((INamable) e).getSerializedName().toUpperCase(Locale.ROOT))).collect(Collectors.toList()));
            } else if (state instanceof BlockStateEnum<?>) {
                return new EnumProperty(state.getName(),
                        (List<String>) state.getPossibleValues().stream().map(e -> ((INamable) e).getSerializedName()).collect(Collectors.toList()));
            } else if (state instanceof net.minecraft.world.level.block.state.properties.BlockStateInteger) {
                return new IntegerProperty(state.getName(), ImmutableList.copyOf(state.getPossibleValues()));
            } else {
                throw new IllegalArgumentException("WorldEdit needs an update to support " + state.getClass().getSimpleName());
            }
        }
    });

    @SuppressWarnings({ "rawtypes" })
    @Override
    public Map<String, ? extends Property<?>> getProperties(BlockType blockType) {
        Map<String, Property<?>> properties = new TreeMap<>();
        Block block = getBlockFromType(blockType);
        BlockStateList<Block, IBlockData> blockStateList =
                block.getStateDefinition();
        for (net.minecraft.world.level.block.state.properties.IBlockState state : blockStateList.getProperties()) {
            Property<?> property = PROPERTY_CACHE.getUnchecked(state);
            properties.put(property.getName(), property);
        }
        return properties;
    }

    @Override
    public void sendFakeNBT(Player player, BlockVector3 pos, CompoundBinaryTag nbtData) {
        ((CraftPlayer) player).getHandle().connection.send(PacketPlayOutTileEntityData.create(
                new TileEntityStructure(
                        new BlockPosition(pos.getBlockX(), pos.getBlockY(), pos.getBlockZ()),
                        Blocks.STRUCTURE_BLOCK.defaultBlockState()
                ),
                __ -> (net.minecraft.nbt.NBTTagCompound) fromNativeBinary(nbtData)
        ));
    }

    @Override
    public void sendFakeOP(Player player) {
        ((CraftPlayer) player).getHandle().connection.send(new PacketPlayOutEntityStatus(
                ((CraftPlayer) player).getHandle(), (byte) 28
        ));
    }

    @Override
    public org.bukkit.inventory.ItemStack adapt(BaseItemStack item) {
        ItemStack stack = new ItemStack(
                DedicatedServer.getServer().registryAccess().registryOrThrow(IRegistry.ITEM_REGISTRY).get(MinecraftKey.tryParse(item.getType().getId())),
                item.getAmount()
        );
        stack.setTag(((net.minecraft.nbt.NBTTagCompound) fromNative(item.getNbtData())));
        return CraftItemStack.asCraftMirror(stack);
    }

    @Override
    public BaseItemStack adapt(org.bukkit.inventory.ItemStack itemStack) {
        final ItemStack nmsStack = CraftItemStack.asNMSCopy(itemStack);
        final BaseItemStack weStack = new BaseItemStack(BukkitAdapter.asItemType(itemStack.getType()), itemStack.getAmount());
        weStack.setNbt(((CompoundBinaryTag) toNativeBinary(nmsStack.getTag())));
        return weStack;
    }

    private final LoadingCache<WorldServer, PaperweightFakePlayer> fakePlayers
            = CacheBuilder.newBuilder().weakKeys().softValues().build(CacheLoader.from(PaperweightFakePlayer::new));

    @Override
    public boolean simulateItemUse(org.bukkit.World world, BlockVector3 position, BaseItem item, Direction face) {
        CraftWorld craftWorld = (CraftWorld) world;
        WorldServer worldServer = craftWorld.getHandle();
        ItemStack stack = CraftItemStack.asNMSCopy(BukkitAdapter.adapt(item instanceof BaseItemStack
                ? ((BaseItemStack) item) : new BaseItemStack(item.getType(), item.getNbtData(), 1)));
        stack.setTag((net.minecraft.nbt.NBTTagCompound) fromNative(item.getNbtData()));

        PaperweightFakePlayer fakePlayer;
        try {
            fakePlayer = fakePlayers.get(worldServer);
        } catch (ExecutionException ignored) {
            return false;
        }
        fakePlayer.setItemInHand(EnumHand.MAIN_HAND, stack);
        fakePlayer.absMoveTo(position.getBlockX(), position.getBlockY(), position.getBlockZ(),
                (float) face.toVector().toYaw(), (float) face.toVector().toPitch());

        final BlockPosition blockPos = new BlockPosition(position.getBlockX(), position.getBlockY(), position.getBlockZ());
        final Vec3D blockVec = Vec3D.atLowerCornerOf(blockPos);
        final net.minecraft.core.EnumDirection enumFacing = adapt(face);
        MovingObjectPositionBlock rayTrace = new MovingObjectPositionBlock(blockVec, enumFacing, blockPos, false);
        ItemActionContext context = new ItemActionContext(fakePlayer, EnumHand.MAIN_HAND, rayTrace);
        EnumInteractionResult result = stack.useOn(context, EnumHand.MAIN_HAND);
        if (result != EnumInteractionResult.SUCCESS) {
            if (worldServer.getBlockState(blockPos).use(worldServer, fakePlayer, EnumHand.MAIN_HAND, rayTrace).consumesAction()) {
                result = EnumInteractionResult.SUCCESS;
            } else {
                result = stack.getItem().use(worldServer, fakePlayer, EnumHand.MAIN_HAND).getResult();
            }
        }

        return result == EnumInteractionResult.SUCCESS;
    }

    @Override
    public boolean canPlaceAt(org.bukkit.World world, BlockVector3 position, BlockState blockState) {
        int internalId = BlockStateIdAccess.getBlockStateId(blockState);
        net.minecraft.world.level.block.state.IBlockData blockData = Block.stateById(internalId);
        return blockData.canSurvive(((CraftWorld) world).getHandle(), new BlockPosition(position.getX(), position.getY(), position.getZ()));
    }

    @Override
    public boolean regenerate(org.bukkit.World bukkitWorld, Region region, Extent extent, RegenOptions options) {
        try {
            doRegen(bukkitWorld, region, extent, options);
        } catch (Exception e) {
            throw new IllegalStateException("Regen failed.", e);
        }

        return true;
    }

    private void doRegen(org.bukkit.World bukkitWorld, Region region, Extent extent, RegenOptions options) throws Exception {
        Environment env = bukkitWorld.getEnvironment();
        ChunkGenerator gen = bukkitWorld.getGenerator();

        Path tempDir = Files.createTempDirectory("WorldEditWorldGen");
        Convertable levelStorage = Convertable.createDefault(tempDir);
        ResourceKey<WorldDimension> worldDimKey = getWorldDimKey(env);
        try (Convertable.ConversionSession session = levelStorage.createAccess("faweregentempworld", worldDimKey)) {
            WorldServer originalWorld = ((CraftWorld) bukkitWorld).getHandle();
            WorldDataServer levelProperties = (WorldDataServer) originalWorld.getServer()
                    .getWorldData().overworldData();
            GeneratorSettings originalOpts = levelProperties.worldGenSettings();

            long seed = options.getSeed().orElse(originalWorld.getSeed());
            GeneratorSettings newOpts = options.getSeed().isPresent()
                    ? originalOpts.withSeed(originalWorld.serverLevelData.isHardcore(), OptionalLong.of(seed))
                    : originalOpts;

            WorldSettings newWorldSettings = new WorldSettings(
                    "faweregentempworld",
                    levelProperties.settings.gameType(),
                    levelProperties.settings.hardcore(),
                    levelProperties.settings.difficulty(),
                    levelProperties.settings.allowCommands(),
                    levelProperties.settings.gameRules(),
                    levelProperties.settings.getDataPackConfig()
            );


            WorldDataServer newWorldData = new WorldDataServer(newWorldSettings, newOpts, Lifecycle.stable());

            WorldServer freshWorld = new WorldServer(
                    originalWorld.getServer(),
                    originalWorld.getServer().executor,
                    session, newWorldData,
                    originalWorld.dimension(),
                    new WorldDimension(
                            originalWorld.dimensionTypeRegistration(),
                            originalWorld.getChunkSource().getGenerator()
                    ),
                    new NoOpWorldLoadListener(),
                    originalWorld.isDebug(),
                    seed,
                    ImmutableList.of(),
                    false,
                    env,
                    gen,
                    bukkitWorld.getBiomeProvider()
            );
            try {
                regenForWorld(region, extent, freshWorld, options);
            } finally {
                freshWorld.getChunkSource().close(false);
            }
        } finally {
            try {
                @SuppressWarnings("unchecked")
                Map<String, org.bukkit.World> map = (Map<String, org.bukkit.World>) serverWorldsField.get(Bukkit.getServer());
                map.remove("faweregentempworld");
            } catch (IllegalAccessException ignored) {
            }
            SafeFiles.tryHardToDeleteDir(tempDir);
        }
    }

    private BiomeType adapt(WorldServer serverWorld, BiomeBase origBiome) {
        MinecraftKey key = serverWorld.registryAccess().registryOrThrow(IRegistry.BIOME_REGISTRY).getKey(origBiome);
        if (key == null) {
            return null;
        }
        return BiomeTypes.get(key.toString());
    }

    @SuppressWarnings("unchecked")
    private void regenForWorld(Region region, Extent extent, WorldServer serverWorld, RegenOptions options) throws WorldEditException {
        List<CompletableFuture<IChunkAccess>> chunkLoadings = submitChunkLoadTasks(region, serverWorld);
        IAsyncTaskHandler<Runnable> executor;
        try {
            executor = (IAsyncTaskHandler<Runnable>) chunkProviderExecutorField.get(serverWorld.getChunkSource());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Couldn't get executor for chunk loading.", e);
        }
        executor.managedBlock(() -> {
            // bail out early if a future fails
            if (chunkLoadings.stream().anyMatch(ftr ->
                    ftr.isDone() && Futures.getUnchecked(ftr) == null
            )) {
                return false;
            }
            return chunkLoadings.stream().allMatch(CompletableFuture::isDone);
        });
        Map<ChunkCoordIntPair, IChunkAccess> chunks = new HashMap<>();
        for (CompletableFuture<IChunkAccess> future : chunkLoadings) {
            IChunkAccess chunk = future.getNow(null);
            checkState(chunk != null, "Failed to generate a chunk, regen failed.");
            chunks.put(chunk.getPos(), chunk);
        }

        for (BlockVector3 vec : region) {
            BlockPosition pos = new BlockPosition(vec.getBlockX(), vec.getBlockY(), vec.getBlockZ());
            IChunkAccess chunk = chunks.get(new ChunkCoordIntPair(pos));
            final net.minecraft.world.level.block.state.IBlockData blockData = chunk.getBlockState(pos);
            int internalId = Block.getId(blockData);
            BlockStateHolder<?> state = BlockStateIdAccess.getBlockStateById(internalId);
            Objects.requireNonNull(state);
            TileEntity tileEntity = chunk.getBlockEntity(pos);
            if (tileEntity != null) {
                net.minecraft.nbt.NBTTagCompound tag = tileEntity.saveWithId();
                state = state.toBaseBlock(((CompoundBinaryTag) toNativeBinary(tag)));
            }
            extent.setBlock(vec, state.toBaseBlock());
            if (options.shouldRegenBiomes()) {
                BiomeBase origBiome = chunk.getNoiseBiome(vec.getX(), vec.getY(), vec.getZ()).value();
                BiomeType adaptedBiome = adapt(serverWorld, origBiome);
                if (adaptedBiome != null) {
                    extent.setBiome(vec, adaptedBiome);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<CompletableFuture<IChunkAccess>> submitChunkLoadTasks(Region region, WorldServer serverWorld) {
        ChunkProviderServer chunkManager = serverWorld.getChunkSource();
        List<CompletableFuture<IChunkAccess>> chunkLoadings = new ArrayList<>();
        // Pre-gen all the chunks
        for (BlockVector2 chunk : region.getChunks()) {
            try {
                //noinspection unchecked
                chunkLoadings.add(
                        ((CompletableFuture<Either<IChunkAccess, PlayerChunk.Failure>>)
                                getChunkFutureMethod.invoke(chunkManager, chunk.getX(), chunk.getZ(), ChunkStatus.FEATURES, true))
                                .thenApply(either -> either.left().orElse(null))
                );
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("Couldn't load chunk for regen.", e);
            }
        }
        return chunkLoadings;
    }

    private ResourceKey<WorldDimension> getWorldDimKey(Environment env) {
        switch (env) {
            case NETHER:
                return WorldDimension.NETHER;
            case THE_END:
                return WorldDimension.END;
            case NORMAL:
            default:
                return WorldDimension.OVERWORLD;
        }
    }

    private static final Set<SideEffect> SUPPORTED_SIDE_EFFECTS = Sets.immutableEnumSet(
            SideEffect.NEIGHBORS,
            SideEffect.LIGHTING,
            SideEffect.VALIDATION,
            SideEffect.ENTITY_AI,
            SideEffect.EVENTS,
            SideEffect.UPDATE
    );

    @Override
    public Set<SideEffect> getSupportedSideEffects() {
        return SUPPORTED_SIDE_EFFECTS;
    }

    @Override
    public boolean clearContainerBlockContents(org.bukkit.World world, BlockVector3 pt) {
        WorldServer originalWorld = ((CraftWorld) world).getHandle();

        TileEntity entity = originalWorld.getBlockEntity(new BlockPosition(pt.getBlockX(), pt.getBlockY(), pt.getBlockZ()));
        if (entity instanceof Clearable) {
            ((Clearable) entity).clearContent();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------------
    // Code that is less likely to break
    // ------------------------------------------------------------------------

    /**
     * Converts from a non-native NMS NBT structure to a native WorldEdit NBT
     * structure.
     *
     * @param foreign non-native NMS NBT structure
     * @return native WorldEdit NBT structure
     */
    @SuppressWarnings("UnstableApiUsage")
    @Override
    public BinaryTag toNativeBinary(net.minecraft.nbt.NBTBase foreign) {
        if (foreign == null) {
            return null;
        }
        if (foreign instanceof net.minecraft.nbt.NBTTagCompound) {
            Map<String, BinaryTag> values = new HashMap<>();
            Set<String> foreignKeys = ((net.minecraft.nbt.NBTTagCompound) foreign).getAllKeys();

            for (String str : foreignKeys) {
                net.minecraft.nbt.NBTBase base = ((net.minecraft.nbt.NBTTagCompound) foreign).get(str);
                values.put(str, toNativeBinary(base));
            }
            return CompoundBinaryTag.from(values);
        } else if (foreign instanceof net.minecraft.nbt.NBTTagByte) {
            return ByteBinaryTag.of(((net.minecraft.nbt.NBTTagByte) foreign).getAsByte());
        } else if (foreign instanceof net.minecraft.nbt.NBTTagByteArray) {
            return ByteArrayBinaryTag.of(((net.minecraft.nbt.NBTTagByteArray) foreign).getAsByteArray());
        } else if (foreign instanceof net.minecraft.nbt.NBTTagDouble) {
            return DoubleBinaryTag.of(((net.minecraft.nbt.NBTTagDouble) foreign).getAsDouble());
        } else if (foreign instanceof net.minecraft.nbt.NBTTagFloat) {
            return FloatBinaryTag.of(((net.minecraft.nbt.NBTTagFloat) foreign).getAsFloat());
        } else if (foreign instanceof net.minecraft.nbt.NBTTagInt) {
            return IntBinaryTag.of(((net.minecraft.nbt.NBTTagInt) foreign).getAsInt());
        } else if (foreign instanceof net.minecraft.nbt.NBTTagIntArray) {
            return IntArrayBinaryTag.of(((net.minecraft.nbt.NBTTagIntArray) foreign).getAsIntArray());
        } else if (foreign instanceof net.minecraft.nbt.NBTTagLongArray) {
            return LongArrayBinaryTag.of(((net.minecraft.nbt.NBTTagLongArray) foreign).getAsLongArray());
        } else if (foreign instanceof net.minecraft.nbt.NBTTagList) {
            try {
                return toNativeList((net.minecraft.nbt.NBTTagList) foreign);
            } catch (Throwable e) {
                LOGGER.log(Level.WARNING, "Failed to convert net.minecraft.nbt.NBTTagList", e);
                return ListBinaryTag.empty();
            }
        } else if (foreign instanceof net.minecraft.nbt.NBTTagLong) {
            return LongBinaryTag.of(((net.minecraft.nbt.NBTTagLong) foreign).getAsLong());
        } else if (foreign instanceof net.minecraft.nbt.NBTTagShort) {
            return ShortBinaryTag.of(((net.minecraft.nbt.NBTTagShort) foreign).getAsShort());
        } else if (foreign instanceof net.minecraft.nbt.NBTTagString) {
            return StringBinaryTag.of(foreign.getAsString());
        } else if (foreign instanceof net.minecraft.nbt.NBTTagEnd) {
            return EndBinaryTag.get();
        } else {
            throw new IllegalArgumentException("Don't know how to make native " + foreign.getClass().getCanonicalName());
        }
    }

    /**
     * Convert a foreign NBT list tag into a native WorldEdit one.
     *
     * @param foreign the foreign tag
     * @return the converted tag
     * @throws SecurityException on error
     * @throws IllegalArgumentException on error
     */
    private ListBinaryTag toNativeList(net.minecraft.nbt.NBTTagList foreign) throws SecurityException, IllegalArgumentException {
        ListBinaryTag.Builder values = ListBinaryTag.builder();

        for (net.minecraft.nbt.NBTBase tag : foreign) {
            values.add(toNativeBinary(tag));
        }

        return values.build();
    }

    /**
     * Converts a WorldEdit-native NBT structure to a NMS structure.
     *
     * @param foreign structure to convert
     * @return non-native structure
     */
    @Override
    public net.minecraft.nbt.NBTBase fromNativeBinary(BinaryTag foreign) {
        if (foreign == null) {
            return null;
        }
        if (foreign instanceof CompoundBinaryTag) {
            net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
            for (String key : ((CompoundBinaryTag) foreign).keySet()) {
                tag.put(key, fromNativeBinary(((CompoundBinaryTag) foreign).get(key)));
            }
            return tag;
        } else if (foreign instanceof ByteBinaryTag) {
            return net.minecraft.nbt.NBTTagByte.valueOf(((ByteBinaryTag) foreign).value());
        } else if (foreign instanceof ByteArrayBinaryTag) {
            return new net.minecraft.nbt.NBTTagByteArray(((ByteArrayBinaryTag) foreign).value());
        } else if (foreign instanceof DoubleBinaryTag) {
            return net.minecraft.nbt.NBTTagDouble.valueOf(((DoubleBinaryTag) foreign).value());
        } else if (foreign instanceof FloatBinaryTag) {
            return net.minecraft.nbt.NBTTagFloat.valueOf(((FloatBinaryTag) foreign).value());
        } else if (foreign instanceof IntBinaryTag) {
            return net.minecraft.nbt.NBTTagInt.valueOf(((IntBinaryTag) foreign).value());
        } else if (foreign instanceof IntArrayBinaryTag) {
            return new net.minecraft.nbt.NBTTagIntArray(((IntArrayBinaryTag) foreign).value());
        } else if (foreign instanceof LongArrayBinaryTag) {
            return new net.minecraft.nbt.NBTTagLongArray(((LongArrayBinaryTag) foreign).value());
        } else if (foreign instanceof ListBinaryTag) {
            net.minecraft.nbt.NBTTagList tag = new net.minecraft.nbt.NBTTagList();
            ListBinaryTag foreignList = (ListBinaryTag) foreign;
            for (BinaryTag t : foreignList) {
                tag.add(fromNativeBinary(t));
            }
            return tag;
        } else if (foreign instanceof LongBinaryTag) {
            return net.minecraft.nbt.NBTTagLong.valueOf(((LongBinaryTag) foreign).value());
        } else if (foreign instanceof ShortBinaryTag) {
            return net.minecraft.nbt.NBTTagShort.valueOf(((ShortBinaryTag) foreign).value());
        } else if (foreign instanceof StringBinaryTag) {
            return net.minecraft.nbt.NBTTagString.valueOf(((StringBinaryTag) foreign).value());
        } else if (foreign instanceof EndBinaryTag) {
            return net.minecraft.nbt.NBTTagEnd.INSTANCE;
        } else {
            throw new IllegalArgumentException("Don't know how to make NMS " + foreign.getClass().getCanonicalName());
        }
    }

    @Override
    public boolean supportsWatchdog() {
        return watchdog != null;
    }

    @Override
    public void tickWatchdog() {
        watchdog.tick();
    }

    private class SpigotWatchdog implements Watchdog {
        private final Field instanceField;
        private final Field lastTickField;

        SpigotWatchdog() throws NoSuchFieldException {
            Field instanceField = WatchdogThread.class.getDeclaredField("instance");
            instanceField.setAccessible(true);
            this.instanceField = instanceField;

            Field lastTickField = WatchdogThread.class.getDeclaredField("lastTick");
            lastTickField.setAccessible(true);
            this.lastTickField = lastTickField;
        }

        @Override
        public void tick() {
            try {
                WatchdogThread instance = (WatchdogThread) this.instanceField.get(null);
                if ((long) lastTickField.get(instance) != 0) {
                    WatchdogThread.tick();
                }
            } catch (IllegalAccessException e) {
                LOGGER.log(Level.WARNING, "Failed to tick watchdog", e);
            }
        }
    }

    private static class MojangWatchdog implements Watchdog {
        private final DedicatedServer server;
        private final Field tickField;

        MojangWatchdog(DedicatedServer server) throws NoSuchFieldException {
            this.server = server;
            Field tickField = MinecraftServer.class.getDeclaredField(
                    Refraction.pickName("nextTickTime", "ah")
            );
            if (tickField.getType() != long.class) {
                throw new IllegalStateException("nextTickTime is not a long field, mapping is likely incorrect");
            }
            tickField.setAccessible(true);
            this.tickField = tickField;
        }

        @Override
        public void tick() {
            try {
                tickField.set(server, SystemUtils.getMillis());
            } catch (IllegalAccessException ignored) {
            }
        }
    }

    private static class NoOpWorldLoadListener implements WorldLoadListener {
        @Override
        public void updateSpawnPos(ChunkCoordIntPair spawnPos) {
        }

        @Override
        public void onStatusChange(ChunkCoordIntPair pos, @org.jetbrains.annotations.Nullable ChunkStatus status) {
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public void setChunkRadius(int radius) {
        }
    }
}
