package com.grinderwolf.swm.nms.v1_16_R3;

import com.flowpowered.nbt.CompoundMap;
import com.flowpowered.nbt.CompoundTag;
import com.flowpowered.nbt.LongArrayTag;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.grinderwolf.swm.api.exceptions.UnknownWorldException;
import com.grinderwolf.swm.api.world.SlimeChunk;
import com.grinderwolf.swm.api.world.SlimeChunkSection;
import com.grinderwolf.swm.api.world.properties.SlimeProperties;
import com.grinderwolf.swm.api.world.properties.SlimePropertyMap;
import com.grinderwolf.swm.nms.CraftSlimeChunk;
import com.grinderwolf.swm.nms.CraftSlimeWorld;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.v1_16_R3.BiomeBase;
import net.minecraft.server.v1_16_R3.BiomeStorage;
import net.minecraft.server.v1_16_R3.Block;
import net.minecraft.server.v1_16_R3.BlockPosition;
import net.minecraft.server.v1_16_R3.Chunk;
import net.minecraft.server.v1_16_R3.ChunkConverter;
import net.minecraft.server.v1_16_R3.ChunkCoordIntPair;
import net.minecraft.server.v1_16_R3.ChunkGenerator;
import net.minecraft.server.v1_16_R3.ChunkSection;
import net.minecraft.server.v1_16_R3.DimensionManager;
import net.minecraft.server.v1_16_R3.EntityTypes;
import net.minecraft.server.v1_16_R3.EnumDifficulty;
import net.minecraft.server.v1_16_R3.EnumSkyBlock;
import net.minecraft.server.v1_16_R3.FluidType;
import net.minecraft.server.v1_16_R3.FluidTypes;
import net.minecraft.server.v1_16_R3.HeightMap;
import net.minecraft.server.v1_16_R3.IBlockData;
import net.minecraft.server.v1_16_R3.IProgressUpdate;
import net.minecraft.server.v1_16_R3.IRegistry;
import net.minecraft.server.v1_16_R3.IWorldDataServer;
import net.minecraft.server.v1_16_R3.LightEngine;
import net.minecraft.server.v1_16_R3.MinecraftKey;
import net.minecraft.server.v1_16_R3.MinecraftServer;
import net.minecraft.server.v1_16_R3.NBTTagCompound;
import net.minecraft.server.v1_16_R3.NBTTagList;
import net.minecraft.server.v1_16_R3.ProtoChunkExtension;
import net.minecraft.server.v1_16_R3.ProtoChunkTickList;
import net.minecraft.server.v1_16_R3.ResourceKey;
import net.minecraft.server.v1_16_R3.SectionPosition;
import net.minecraft.server.v1_16_R3.TicketType;
import net.minecraft.server.v1_16_R3.TileEntity;
import net.minecraft.server.v1_16_R3.Unit;
import net.minecraft.server.v1_16_R3.World;
import net.minecraft.server.v1_16_R3.WorldDimension;
import net.minecraft.server.v1_16_R3.WorldServer;
import org.bukkit.Bukkit;
import org.bukkit.event.world.WorldSaveEvent;
import org.jetbrains.annotations.NotNull;

public final class CustomWorldServer extends WorldServer {

  private static final TicketType<Unit> SWM_TICKET = TicketType.a("swm-chunk", (a, b) -> 0);

  private static final ExecutorService WORLD_SAVER_SERVICE = Executors.newFixedThreadPool(4, new ThreadFactoryBuilder()
    .setNameFormat("SWM Pool Thread #%1$d").build());

  private final BiomeBase defaultBiome;

  private final Object saveLock = new Object();

  @Getter
  private final CraftSlimeWorld slimeWorld;

  @Getter
  @Setter
  private boolean ready = false;

  public CustomWorldServer(final CraftSlimeWorld world, final IWorldDataServer worldData,
                           final ResourceKey<World> worldKey, final ResourceKey<WorldDimension> dimensionKey,
                           final DimensionManager dimensionManager, final ChunkGenerator chunkGenerator,
                           final org.bukkit.World.Environment environment) throws IOException {
    super(MinecraftServer.getServer(), MinecraftServer.getServer().executorService,
      v1_16_R3SlimeNMS.CONVERTABLE.c(world.getName(), dimensionKey),
      worldData, worldKey, dimensionManager, MinecraftServer.getServer().worldLoadListenerFactory.create(11),
      chunkGenerator, false, 0, new ArrayList<>(), true, environment, null);
    this.slimeWorld = world;
    final SlimePropertyMap propertyMap = world.getPropertyMap();
    this.worldDataServer.setDifficulty(EnumDifficulty.valueOf(
      propertyMap.getValue(SlimeProperties.DIFFICULTY).toUpperCase(Locale.ROOT)));
    this.worldDataServer.setSpawn(new BlockPosition(
      propertyMap.getValue(SlimeProperties.SPAWN_X),
      propertyMap.getValue(SlimeProperties.SPAWN_Y),
      propertyMap.getValue(SlimeProperties.SPAWN_Z)), 0);
    super.setSpawnFlags(
      propertyMap.getValue(SlimeProperties.ALLOW_MONSTERS),
      propertyMap.getValue(SlimeProperties.ALLOW_ANIMALS));
    this.pvpMode = propertyMap.getValue(SlimeProperties.PVP);
    final String biomeStr = this.slimeWorld.getPropertyMap().getValue(SlimeProperties.DEFAULT_BIOME);
    final ResourceKey<BiomeBase> biomeKey = ResourceKey.a(IRegistry.ay, new MinecraftKey(biomeStr));
    this.defaultBiome = MinecraftServer.getServer().getCustomRegistry().b(IRegistry.ay).a(biomeKey);
  }

  @Override
  public void save(final IProgressUpdate iprogressupdate, final boolean flag, final boolean flag1) {
    if (this.slimeWorld.isReadOnly() || flag1) {
      return;
    }
    if (flag) { // TODO Is this really 'forceSave'? Doesn't look like it tbh
      Bukkit.getPluginManager().callEvent(new WorldSaveEvent(this.getWorld()));
    }
    this.getChunkProvider().save(flag);
    this.worldDataServer.a(this.getWorldBorder().t());
    this.worldDataServer.setCustomBossEvents(MinecraftServer.getServer().getBossBattleCustomData().save());
    // Update level data
    final NBTTagCompound compound = new NBTTagCompound();
    this.worldDataServer.a(MinecraftServer.getServer().getCustomRegistry(), compound);
    this.slimeWorld.getExtraData().getValue().put(Converter.convertTag("LevelData", compound));
    if (MinecraftServer.getServer().isStopped()) { // Make sure the world gets saved before stopping the server by running it from the main thread
      this.save();
      // Have to manually unlock the world as well
      try {
        this.slimeWorld.getLoader().unlockWorld(this.slimeWorld.getName());
      } catch (final IOException ex) {
        ex.printStackTrace();
      } catch (final UnknownWorldException ignored) {
      }
    } else {
      CustomWorldServer.WORLD_SAVER_SERVICE.execute(this::save);
    }
  }

  @NotNull
  ProtoChunkExtension getChunk(final int x, final int z) {
    final SlimeChunk slimeChunk = this.slimeWorld.getChunk(x, z);
    final Chunk chunk;
    if (slimeChunk instanceof NMSSlimeChunk) {
      chunk = ((NMSSlimeChunk) slimeChunk).getChunk();
    } else {
      if (slimeChunk == null) {
        final ChunkCoordIntPair pos = new ChunkCoordIntPair(x, z);
        // Biomes
        final BiomeBase[] biomes = new BiomeBase[BiomeStorage.a];
        Arrays.fill(biomes, this.defaultBiome);
        final BiomeStorage biomeStorage = new BiomeStorage(this.r().b(IRegistry.ay), biomes);
        // Tick lists
        final ProtoChunkTickList<Block> blockTickList = new ProtoChunkTickList<>(block ->
          block == null || block.getBlockData().isAir(), pos);
        final ProtoChunkTickList<FluidType> fluidTickList = new ProtoChunkTickList<>(type ->
          type == null || type == FluidTypes.EMPTY, pos);
        chunk = new Chunk(this, pos, biomeStorage, ChunkConverter.a, blockTickList, fluidTickList,
          0L, null, null);
        // Height Maps
//                HeightMap.a(chunk, ChunkStatus.FULL.h());
      } else {
        chunk = this.createChunk(slimeChunk);
      }
      this.slimeWorld.updateChunk(new NMSSlimeChunk(chunk));
    }
    return new ProtoChunkExtension(chunk);
  }

  void saveChunk(@NotNull final Chunk chunk) {
    final SlimeChunk slimeChunk = this.slimeWorld.getChunk(chunk.getPos().x, chunk.getPos().z);
    // In case somehow the chunk object changes (might happen for some reason)
    if (slimeChunk instanceof NMSSlimeChunk) {
      ((NMSSlimeChunk) slimeChunk).setChunk(chunk);
    } else {
      this.slimeWorld.updateChunk(new NMSSlimeChunk(chunk));
    }
  }

  @NotNull
  private Chunk createChunk(@NotNull final SlimeChunk chunk) {
    final int x = chunk.getX();
    final int z = chunk.getZ();
    final ChunkCoordIntPair pos = new ChunkCoordIntPair(x, z);
    // Biomes
    final int[] biomeIntArray = chunk.getBiomes();
    final BiomeStorage biomeStorage = new BiomeStorage(
      MinecraftServer.getServer().getCustomRegistry().b(IRegistry.ay), pos,
      this.getChunkProvider().getChunkGenerator().getWorldChunkManager(), biomeIntArray);
    // Tick lists
    final ProtoChunkTickList<Block> blockTickList = new ProtoChunkTickList<>(
      block -> block == null || block.getBlockData().isAir(), pos);
    final ProtoChunkTickList<FluidType> fluidTickList = new ProtoChunkTickList<>(
      type -> type == null || type == FluidTypes.EMPTY, pos);
    // Chunk sections
    final ChunkSection[] sections = new ChunkSection[16];
    final LightEngine lightEngine = this.getChunkProvider().getLightEngine();
    lightEngine.b(pos, true);
    for (int sectionId = 0; sectionId < chunk.getSections().length; sectionId++) {
      final SlimeChunkSection slimeSection = chunk.getSections()[sectionId];
      if (slimeSection != null) {
        final ChunkSection section = new ChunkSection(sectionId << 4);
        section.getBlocks().a((NBTTagList) Converter.convertTag(
          slimeSection.getPalette()), slimeSection.getBlockStates());
        if (slimeSection.getBlockLight() != null) {
          lightEngine.a(EnumSkyBlock.BLOCK, SectionPosition.a(
            pos, sectionId), Converter.convertArray(slimeSection.getBlockLight()), true);
        }
        if (slimeSection.getSkyLight() != null) {
          lightEngine.a(EnumSkyBlock.SKY, SectionPosition.a(
            pos, sectionId), Converter.convertArray(slimeSection.getSkyLight()), true);
        }
        section.recalcBlockCounts();
        sections[sectionId] = section;
      }
    }
    // Keep the chunk loaded at level 33 to avoid light glitches
    // Such a high level will let the server not tick the chunk,
    // but at the same time it won't be completely unloaded from memory
    // getChunkProvider().addTicket(SWM_TICKET, pos, 33, Unit.INSTANCE);
    final Consumer<Chunk> loadEntities = nmsChunk -> {
      // Load tile entities
      // System.out.println("Loading tile entities for chunk (" + pos.x + ", " + pos.z + ") on world " + slimeWorld.getName());
      final List<CompoundTag> tileEntities = chunk.getTileEntities();
      for (final CompoundTag tag : tileEntities) {
        final Optional<String> type = tag.getStringValue("id");
        // Sometimes null tile entities are saved
        if (type.isPresent()) {
          final BlockPosition blockPosition = new BlockPosition(
            tag.getIntValue("x").get(), tag.getIntValue("y").get(), tag.getIntValue("z").get());
          final IBlockData blockData = nmsChunk.getType(blockPosition);
          final TileEntity entity = TileEntity.create(blockData, (NBTTagCompound) Converter.convertTag(tag));
          if (entity != null) {
            nmsChunk.setTileEntity(blockPosition, entity);
          }
        }
      }
      // Load entities
      final List<CompoundTag> entities = chunk.getEntities();
      for (final CompoundTag tag : entities) {
        EntityTypes.a((NBTTagCompound) Converter.convertTag(tag), nmsChunk.world, entity -> {
          nmsChunk.a(entity);
          return entity;
        });
        nmsChunk.d(true);
      }
    };
    final CompoundTag upgradeDataTag = ((CraftSlimeChunk) chunk).getUpgradeData();
    final Chunk nmsChunk = new Chunk(this, pos, biomeStorage, upgradeDataTag == null
      ? ChunkConverter.a
      : new ChunkConverter((NBTTagCompound)
      Converter.convertTag(upgradeDataTag)), blockTickList, fluidTickList, 0L, sections, loadEntities);
    // Height Maps
    final EnumSet<HeightMap.Type> heightMapTypes = nmsChunk.getChunkStatus().h();
    final CompoundMap heightMaps = chunk.getHeightMaps().getValue();
    final EnumSet<HeightMap.Type> unsetHeightMaps = EnumSet.noneOf(HeightMap.Type.class);
    for (final HeightMap.Type type : heightMapTypes) {
      final String name = type.getName();
      if (heightMaps.containsKey(name)) {
        final LongArrayTag heightMap = (LongArrayTag) heightMaps.get(name);
        nmsChunk.a(type, heightMap.getValue());
      } else {
        unsetHeightMaps.add(type);
      }
    }
    HeightMap.a(nmsChunk, unsetHeightMaps);
    return nmsChunk;
  }

  private void save() {
    synchronized (this.saveLock) { // Don't want to save the SlimeWorld from multiple threads simultaneously
      try {
        Bukkit.getLogger().log(Level.INFO, "Saving world " + this.slimeWorld.getName() + "...");
        final long start = System.currentTimeMillis();
        final byte[] serializedWorld = this.slimeWorld.serialize();
        this.slimeWorld.getLoader().saveWorld(this.slimeWorld.getName(), serializedWorld, false);
        Bukkit.getLogger().log(Level.INFO, String.format("World %s saved in %dms.",
          this.slimeWorld.getName(), System.currentTimeMillis() - start));
      } catch (final IOException | IllegalStateException ex) {
        ex.printStackTrace();
      }
    }
  }
}
