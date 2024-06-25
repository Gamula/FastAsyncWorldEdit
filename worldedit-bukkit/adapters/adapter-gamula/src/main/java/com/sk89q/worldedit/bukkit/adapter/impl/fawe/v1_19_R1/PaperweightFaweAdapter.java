package com.sk89q.worldedit.bukkit.adapter.impl.fawe.v1_19_R1;

import com.fastasyncworldedit.bukkit.adapter.FaweAdapter;
import com.fastasyncworldedit.bukkit.adapter.NMSRelighterFactory;
import com.fastasyncworldedit.core.FaweCache;
import com.fastasyncworldedit.core.entity.LazyBaseEntity;
import com.fastasyncworldedit.core.extent.processor.lighting.RelighterFactory;
import com.fastasyncworldedit.core.queue.IBatchProcessor;
import com.fastasyncworldedit.core.queue.IChunkGet;
import com.fastasyncworldedit.core.queue.implementation.packet.ChunkPacket;
import com.fastasyncworldedit.core.util.NbtUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.sk89q.jnbt.Tag;
import com.sk89q.worldedit.blocks.BaseItemStack;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.adapter.BukkitImplAdapter;
import com.sk89q.worldedit.bukkit.adapter.ext.fawe.v1_19_R1.PaperweightAdapter;
import com.sk89q.worldedit.bukkit.adapter.impl.fawe.v1_19_R1.nbt.PaperweightLazyCompoundTag;
import com.sk89q.worldedit.bukkit.adapter.impl.fawe.v1_19_R1.regen.PaperweightRegen;
import com.sk89q.worldedit.entity.BaseEntity;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.internal.block.BlockStateIdAccess;
import com.sk89q.worldedit.internal.util.LogManagerCompat;
import com.sk89q.worldedit.internal.wna.WorldNativeAccess;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.registry.state.BooleanProperty;
import com.sk89q.worldedit.registry.state.DirectionalProperty;
import com.sk89q.worldedit.registry.state.EnumProperty;
import com.sk89q.worldedit.registry.state.IntegerProperty;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.util.Direction;
import com.sk89q.worldedit.util.SideEffect;
import com.sk89q.worldedit.util.SideEffectSet;
import com.sk89q.worldedit.util.formatting.text.Component;
import com.sk89q.worldedit.util.nbt.BinaryTag;
import com.sk89q.worldedit.util.nbt.CompoundBinaryTag;
import com.sk89q.worldedit.util.nbt.StringBinaryTag;
import com.sk89q.worldedit.world.RegenOptions;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypesCache;
import com.sk89q.worldedit.world.entity.EntityType;
import com.sk89q.worldedit.world.item.ItemType;
import com.sk89q.worldedit.world.registry.BlockMaterial;
import io.papermc.lib.PaperLib;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.IRegistry;
import net.minecraft.core.IRegistryWritable;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.PlayerChunk;
import net.minecraft.server.level.WorldServer;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.util.INamable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.BiomeBase;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.TileEntity;
import net.minecraft.world.level.block.state.properties.BlockProperties;
import net.minecraft.world.level.block.state.properties.BlockStateDirection;
import net.minecraft.world.level.block.state.properties.BlockStateEnum;
import net.minecraft.world.level.chunk.Chunk;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.v1_19_R1.CraftServer;
import org.bukkit.craftbukkit.v1_19_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R1.block.data.CraftBlockData;
import org.bukkit.craftbukkit.v1_19_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_19_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_19_R1.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_19_R1.util.CraftNamespacedKey;
import org.bukkit.entity.Player;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class PaperweightFaweAdapter extends FaweAdapter<net.minecraft.nbt.NBTBase, WorldServer> {

    private static final Logger LOGGER = LogManagerCompat.getLogger();
    private static Method CHUNK_HOLDER_WAS_ACCESSIBLE_SINCE_LAST_SAVE;

    static {
        try {
            CHUNK_HOLDER_WAS_ACCESSIBLE_SINCE_LAST_SAVE = PlayerChunk.class.getDeclaredMethod("wasAccessibleSinceLastSave");
        } catch (NoSuchMethodException ignored) { // may not be present in newer paper versions
        }
    }

    private final PaperweightAdapter parent;
    // ------------------------------------------------------------------------
    // Code that may break between versions of Minecraft
    // ------------------------------------------------------------------------
    private final PaperweightMapChunkUtil mapUtil = new PaperweightMapChunkUtil();
    private char[] ibdToStateOrdinal = null;
    private int[] ordinalToIbdID = null;
    private boolean initialised = false;
    private Map<String, List<Property<?>>> allBlockProperties = null;

    public PaperweightFaweAdapter() throws NoSuchFieldException, NoSuchMethodException {
        this.parent = new PaperweightAdapter();
    }

    private static String getEntityId(Entity entity) {
        MinecraftKey resourceLocation = net.minecraft.world.entity.EntityTypes.getKey(entity.getType());
        return resourceLocation == null ? null : resourceLocation.toString();
    }

    @Override
    public BukkitImplAdapter<net.minecraft.nbt.NBTBase> getParent() {
        return parent;
    }

    private synchronized boolean init() {
        if (ibdToStateOrdinal != null && ibdToStateOrdinal[1] != 0) {
            return false;
        }
        ibdToStateOrdinal = new char[BlockTypesCache.states.length]; // size
        ordinalToIbdID = new int[ibdToStateOrdinal.length]; // size
        for (int i = 0; i < ibdToStateOrdinal.length; i++) {
            BlockState blockState = BlockTypesCache.states[i];
            PaperweightBlockMaterial material = (PaperweightBlockMaterial) blockState.getMaterial();
            int id = Block.BLOCK_STATE_REGISTRY.getId(material.getState());
            char ordinal = blockState.getOrdinalChar();
            ibdToStateOrdinal[id] = ordinal;
            ordinalToIbdID[ordinal] = id;
        }
        Map<String, List<Property<?>>> properties = new HashMap<>();
        try {
            for (Field field : BlockProperties.class.getDeclaredFields()) {
                Object obj = field.get(null);
                if (!(obj instanceof net.minecraft.world.level.block.state.properties.IBlockState<?> state)) {
                    continue;
                }
                Property<?> property;
                if (state instanceof net.minecraft.world.level.block.state.properties.BlockStateBoolean) {
                    property = new BooleanProperty(
                            state.getName(),
                            (List<Boolean>) ImmutableList.copyOf(state.getPossibleValues())
                    );
                } else if (state instanceof BlockStateDirection) {
                    property = new DirectionalProperty(
                            state.getName(),
                            state
                                    .getPossibleValues()
                                    .stream()
                                    .map(e -> Direction.valueOf(((INamable) e).getSerializedName().toUpperCase()))
                                    .collect(Collectors.toList())
                    );
                } else if (state instanceof BlockStateEnum<?>) {
                    property = new EnumProperty(
                            state.getName(),
                            state
                                    .getPossibleValues()
                                    .stream()
                                    .map(e -> ((INamable) e).getSerializedName())
                                    .collect(Collectors.toList())
                    );
                } else if (state instanceof net.minecraft.world.level.block.state.properties.BlockStateInteger) {
                    property = new IntegerProperty(
                            state.getName(),
                            (List<Integer>) ImmutableList.copyOf(state.getPossibleValues())
                    );
                } else {
                    throw new IllegalArgumentException("FastAsyncWorldEdit needs an update to support " + state
                            .getClass()
                            .getSimpleName());
                }
                properties.compute(property.getName().toLowerCase(Locale.ROOT), (k, v) -> {
                    if (v == null) {
                        v = new ArrayList<>(Collections.singletonList(property));
                    } else {
                        v.add(property);
                    }
                    return v;
                });
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } finally {
            allBlockProperties = ImmutableMap.copyOf(properties);
        }
        initialised = true;
        return true;
    }

    @Override
    public BlockMaterial getMaterial(BlockType blockType) {
        Block block = getBlock(blockType);
        return new PaperweightBlockMaterial(block);
    }

    @Override
    public synchronized BlockMaterial getMaterial(BlockState state) {
        net.minecraft.world.level.block.state.IBlockData blockState = ((CraftBlockData) Bukkit.createBlockData(state.getAsString())).getState();
        return new PaperweightBlockMaterial(blockState.getBlock(), blockState);
    }

    public Block getBlock(BlockType blockType) {
        return DedicatedServer.getServer().registryAccess().registryOrThrow(IRegistry.BLOCK_REGISTRY)
                .get(new MinecraftKey(blockType.getNamespace(), blockType.getResource()));
    }

    @Deprecated
    @Override
    public BlockState getBlock(Location location) {
        Preconditions.checkNotNull(location);

        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        final WorldServer handle = getServerLevel(location.getWorld());
        Chunk chunk = handle.getChunk(x >> 4, z >> 4);
        final BlockPosition blockPos = new BlockPosition(x, y, z);
        final net.minecraft.world.level.block.state.IBlockData blockData = chunk.getBlockState(blockPos);
        BlockState state = adapt(blockData);
        if (state == null) {
            org.bukkit.block.Block bukkitBlock = location.getBlock();
            state = BukkitAdapter.adapt(bukkitBlock.getBlockData());
        }
        return state;
    }

    @Override
    public BaseBlock getFullBlock(final Location location) {
        Preconditions.checkNotNull(location);

        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        final WorldServer handle = getServerLevel(location.getWorld());
        Chunk chunk = handle.getChunk(x >> 4, z >> 4);
        final BlockPosition blockPos = new BlockPosition(x, y, z);
        final net.minecraft.world.level.block.state.IBlockData blockData = chunk.getBlockState(blockPos);
        BlockState state = adapt(blockData);
        if (state == null) {
            org.bukkit.block.Block bukkitBlock = location.getBlock();
            state = BukkitAdapter.adapt(bukkitBlock.getBlockData());
        }
        if (state.getBlockType().getMaterial().hasContainer()) {

            // Read the NBT data
            TileEntity blockEntity = chunk.getBlockEntity(blockPos, Chunk.EnumTileEntityState.CHECK);
            if (blockEntity != null) {
                net.minecraft.nbt.NBTTagCompound tag = blockEntity.saveWithId();
                return state.toBaseBlock((CompoundBinaryTag) toNativeBinary(tag));
            }
        }

        return state.toBaseBlock();
    }

    @Override
    public Set<SideEffect> getSupportedSideEffects() {
        return SideEffectSet.defaults().getSideEffectsToApply();
    }

    @Override
    public WorldNativeAccess<?, ?, ?> createWorldNativeAccess(org.bukkit.World world) {
        return new PaperweightFaweWorldNativeAccess(this, new WeakReference<>(getServerLevel(world)));
    }

    @Override
    public BaseEntity getEntity(org.bukkit.entity.Entity entity) {
        Preconditions.checkNotNull(entity);

        CraftEntity craftEntity = ((CraftEntity) entity);
        Entity mcEntity = craftEntity.getHandle();

        String id = getEntityId(mcEntity);

        if (id != null) {
            EntityType type = com.sk89q.worldedit.world.entity.EntityTypes.get(id);
            Supplier<CompoundBinaryTag> saveTag = () -> {
                final net.minecraft.nbt.NBTTagCompound minecraftTag = new net.minecraft.nbt.NBTTagCompound();
                PaperweightPlatformAdapter.readEntityIntoTag(mcEntity, minecraftTag);
                //add Id for AbstractChangeSet to work
                final CompoundBinaryTag tag = (CompoundBinaryTag) toNativeBinary(minecraftTag);
                final Map<String, BinaryTag> tags = NbtUtils.getCompoundBinaryTagValues(tag);
                tags.put("Id", StringBinaryTag.of(id));
                return CompoundBinaryTag.from(tags);
            };
            return new LazyBaseEntity(type, saveTag);
        } else {
            return null;
        }
    }

    @Override
    public Component getRichBlockName(BlockType blockType) {
        return parent.getRichBlockName(blockType);
    }

    @Override
    public Component getRichItemName(ItemType itemType) {
        return parent.getRichItemName(itemType);
    }

    @Override
    public Component getRichItemName(BaseItemStack itemStack) {
        return parent.getRichItemName(itemStack);
    }

    @Override
    public OptionalInt getInternalBlockStateId(BlockState state) {
        PaperweightBlockMaterial material = (PaperweightBlockMaterial) state.getMaterial();
        net.minecraft.world.level.block.state.IBlockData mcState = material.getCraftBlockData().getState();
        return OptionalInt.of(Block.BLOCK_STATE_REGISTRY.getId(mcState));
    }

    @Override
    public BlockState adapt(BlockData blockData) {
        CraftBlockData cbd = ((CraftBlockData) blockData);
        net.minecraft.world.level.block.state.IBlockData ibd = cbd.getState();
        return adapt(ibd);
    }

    public BlockState adapt(net.minecraft.world.level.block.state.IBlockData blockState) {
        return BlockTypesCache.states[adaptToChar(blockState)];
    }

    public char adaptToChar(net.minecraft.world.level.block.state.IBlockData blockState) {
        int id = Block.BLOCK_STATE_REGISTRY.getId(blockState);
        if (initialised) {
            return ibdToStateOrdinal[id];
        }
        synchronized (this) {
            if (initialised) {
                return ibdToStateOrdinal[id];
            }
            try {
                init();
                return ibdToStateOrdinal[id];
            } catch (ArrayIndexOutOfBoundsException e1) {
                LOGGER.error("Attempted to convert {} with ID {} to char. ibdToStateOrdinal length: {}. Defaulting to air!",
                        blockState.getBlock(), Block.BLOCK_STATE_REGISTRY.getId(blockState), ibdToStateOrdinal.length, e1
                );
                return BlockTypesCache.ReservedIDs.AIR;
            }
        }
    }

    public char ibdIDToOrdinal(int id) {
        if (initialised) {
            return ibdToStateOrdinal[id];
        }
        synchronized (this) {
            if (initialised) {
                return ibdToStateOrdinal[id];
            }
            init();
            return ibdToStateOrdinal[id];
        }
    }

    @Override
    public char[] getIbdToStateOrdinal() {
        if (initialised) {
            return ibdToStateOrdinal;
        }
        synchronized (this) {
            if (initialised) {
                return ibdToStateOrdinal;
            }
            init();
            return ibdToStateOrdinal;
        }
    }

    public int ordinalToIbdID(char ordinal) {
        if (initialised) {
            return ordinalToIbdID[ordinal];
        }
        synchronized (this) {
            if (initialised) {
                return ordinalToIbdID[ordinal];
            }
            init();
            return ordinalToIbdID[ordinal];
        }
    }

    @Override
    public int[] getOrdinalToIbdID() {
        if (initialised) {
            return ordinalToIbdID;
        }
        synchronized (this) {
            if (initialised) {
                return ordinalToIbdID;
            }
            init();
            return ordinalToIbdID;
        }
    }

    @Override
    public <B extends BlockStateHolder<B>> BlockData adapt(B state) {
        PaperweightBlockMaterial material = (PaperweightBlockMaterial) state.getMaterial();
        return material.getCraftBlockData();
    }

    @Override
    public void sendFakeChunk(org.bukkit.World world, Player player, ChunkPacket chunkPacket) {
        WorldServer nmsWorld = getServerLevel(world);
        PlayerChunk map = PaperweightPlatformAdapter.getPlayerChunk(nmsWorld, chunkPacket.getChunkX(), chunkPacket.getChunkZ());
        if (map != null && wasAccessibleSinceLastSave(map)) {
            boolean flag = false;
            // PlayerChunk.d players = map.players;
            Stream<EntityPlayer> stream = /*players.a(new ChunkCoordIntPair(packet.getChunkX(), packet.getChunkZ()), flag)
             */ Stream.empty();

            EntityPlayer checkPlayer = player == null ? null : ((CraftPlayer) player).getHandle();
            stream.filter(entityPlayer -> checkPlayer == null || entityPlayer == checkPlayer)
                    .forEach(entityPlayer -> {
                        synchronized (chunkPacket) {
                            ClientboundLevelChunkWithLightPacket nmsPacket = (ClientboundLevelChunkWithLightPacket) chunkPacket.getNativePacket();
                            if (nmsPacket == null) {
                                nmsPacket = mapUtil.create(this, chunkPacket);
                                chunkPacket.setNativePacket(nmsPacket);
                            }
                            try {
                                FaweCache.INSTANCE.CHUNK_FLAG.get().set(true);
                                entityPlayer.connection.send(nmsPacket);
                            } finally {
                                FaweCache.INSTANCE.CHUNK_FLAG.get().set(false);
                            }
                        }
                    });
        }
    }

    @Override
    public Map<String, ? extends Property<?>> getProperties(BlockType blockType) {
        return getParent().getProperties(blockType);
    }

    @Override
    public boolean canPlaceAt(org.bukkit.World world, BlockVector3 blockVector3, BlockState blockState) {
        int internalId = BlockStateIdAccess.getBlockStateId(blockState);
        net.minecraft.world.level.block.state.IBlockData blockState1 = Block.stateById(internalId);
        return blockState1.hasPostProcess(
                getServerLevel(world),
                new BlockPosition(blockVector3.getX(), blockVector3.getY(), blockVector3.getZ())
        );
    }

    @Override
    public org.bukkit.inventory.ItemStack adapt(BaseItemStack baseItemStack) {
        ItemStack stack = new ItemStack(
                DedicatedServer.getServer().registryAccess().registryOrThrow(IRegistry.ITEM_REGISTRY)
                        .get(MinecraftKey.tryParse(baseItemStack.getType().getId())),
                baseItemStack.getAmount()
        );
        stack.setTag(((net.minecraft.nbt.NBTTagCompound) fromNative(baseItemStack.getNbtData())));
        return CraftItemStack.asCraftMirror(stack);
    }

    @Override
    protected void preCaptureStates(final WorldServer serverLevel) {
        serverLevel.captureTreeGeneration = true;
        serverLevel.captureBlockStates = true;
    }

    @Override
    protected List<org.bukkit.block.BlockState> getCapturedBlockStatesCopy(final WorldServer serverLevel) {
        return new ArrayList<>(serverLevel.capturedBlockStates.values());
    }

    @Override
    protected void postCaptureBlockStates(final WorldServer serverLevel) {
        serverLevel.captureBlockStates = false;
        serverLevel.captureTreeGeneration = false;
        serverLevel.capturedBlockStates.clear();
    }

    @Override
    protected WorldServer getServerLevel(final World world) {
        return ((CraftWorld) world).getHandle();
    }

    @Override
    public BaseItemStack adapt(org.bukkit.inventory.ItemStack itemStack) {
        final ItemStack nmsStack = CraftItemStack.asNMSCopy(itemStack);
        final BaseItemStack weStack = new BaseItemStack(BukkitAdapter.asItemType(itemStack.getType()), itemStack.getAmount());
        weStack.setNbt(((CompoundBinaryTag) toNativeBinary(nmsStack.getTag())));
        return weStack;
    }

    @Override
    public Tag toNative(net.minecraft.nbt.NBTBase foreign) {
        return parent.toNative(foreign);
    }

    @Override
    public net.minecraft.nbt.NBTBase fromNative(Tag foreign) {
        if (foreign instanceof PaperweightLazyCompoundTag) {
            return ((PaperweightLazyCompoundTag) foreign).get();
        }
        return parent.fromNative(foreign);
    }

    @Override
    public boolean regenerate(org.bukkit.World bukkitWorld, Region region, Extent target, RegenOptions options) throws Exception {
        return new PaperweightRegen(bukkitWorld, region, target, options).regenerate();
    }

    @Override
    public IChunkGet get(org.bukkit.World world, int chunkX, int chunkZ) {
        return new PaperweightGetBlocks(world, chunkX, chunkZ);
    }

    @Override
    public int getInternalBiomeId(BiomeType biomeType) {
        final IRegistry<BiomeBase> registry = MinecraftServer
                .getServer()
                .registryAccess()
                .registryOrThrow(IRegistry.BIOME_REGISTRY);
        MinecraftKey resourceLocation = MinecraftKey.tryParse(biomeType.getId());
        BiomeBase biome = registry.get(resourceLocation);
        return registry.getId(biome);
    }

    @Override
    public Iterable<NamespacedKey> getRegisteredBiomes() {
        IRegistryWritable<BiomeBase> biomeRegistry = (IRegistryWritable<BiomeBase>) ((CraftServer) Bukkit.getServer())
                .getServer()
                .registryAccess()
                .registryOrThrow(IRegistry.BIOME_REGISTRY);
        List<MinecraftKey> keys = biomeRegistry.stream()
                .map(biomeRegistry::getKey).filter(Objects::nonNull).toList();
        List<NamespacedKey> namespacedKeys = new ArrayList<>();
        for (MinecraftKey key : keys) {
            try {
                namespacedKeys.add(CraftNamespacedKey.fromMinecraft(key));
            } catch (IllegalArgumentException e) {
                LOGGER.error("Error converting biome key {}", key.toString(), e);
            }
        }
        return namespacedKeys;
    }

    @Override
    public RelighterFactory getRelighterFactory() {
        if (PaperLib.isPaper()) {
            return new PaperweightStarlightRelighterFactory();
        } else {
            return new NMSRelighterFactory();
        }
    }

    @Override
    public Map<String, List<Property<?>>> getAllProperties() {
        if (initialised) {
            return allBlockProperties;
        }
        synchronized (this) {
            if (initialised) {
                return allBlockProperties;
            }
            init();
            return allBlockProperties;
        }
    }

    @Override
    public IBatchProcessor getTickingPostProcessor() {
        return new PaperweightPostProcessor();
    }

    private boolean wasAccessibleSinceLastSave(PlayerChunk holder) {
        if (!PaperLib.isPaper() || !PaperweightPlatformAdapter.POST_CHUNK_REWRITE) {
            try {
                return (boolean) CHUNK_HOLDER_WAS_ACCESSIBLE_SINCE_LAST_SAVE.invoke(holder);
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                // fall-through
            }
        }
        // Papers new chunk system has no related replacement - therefor we assume true.
        return true;
    }

}
