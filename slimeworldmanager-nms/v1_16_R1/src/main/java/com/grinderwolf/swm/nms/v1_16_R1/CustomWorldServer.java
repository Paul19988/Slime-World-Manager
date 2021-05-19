package com.grinderwolf.swm.nms.v1_16_R1;

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
import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.v1_16_R1.BiomeBase;
import net.minecraft.server.v1_16_R1.BiomeStorage;
import net.minecraft.server.v1_16_R1.Block;
import net.minecraft.server.v1_16_R1.BlockPosition;
import net.minecraft.server.v1_16_R1.Chunk;
import net.minecraft.server.v1_16_R1.ChunkConverter;
import net.minecraft.server.v1_16_R1.ChunkCoordIntPair;
import net.minecraft.server.v1_16_R1.ChunkGenerator;
import net.minecraft.server.v1_16_R1.ChunkSection;
import net.minecraft.server.v1_16_R1.ChunkStatus;
import net.minecraft.server.v1_16_R1.DimensionManager;
import net.minecraft.server.v1_16_R1.EntityTypes;
import net.minecraft.server.v1_16_R1.EnumDifficulty;
import net.minecraft.server.v1_16_R1.EnumSkyBlock;
import net.minecraft.server.v1_16_R1.FluidType;
import net.minecraft.server.v1_16_R1.FluidTypes;
import net.minecraft.server.v1_16_R1.HeightMap;
import net.minecraft.server.v1_16_R1.IBlockData;
import net.minecraft.server.v1_16_R1.IProgressUpdate;
import net.minecraft.server.v1_16_R1.IRegistry;
import net.minecraft.server.v1_16_R1.IWorldDataServer;
import net.minecraft.server.v1_16_R1.LightEngine;
import net.minecraft.server.v1_16_R1.MinecraftKey;
import net.minecraft.server.v1_16_R1.MinecraftServer;
import net.minecraft.server.v1_16_R1.NBTTagCompound;
import net.minecraft.server.v1_16_R1.NBTTagList;
import net.minecraft.server.v1_16_R1.ProtoChunkExtension;
import net.minecraft.server.v1_16_R1.ProtoChunkTickList;
import net.minecraft.server.v1_16_R1.ResourceKey;
import net.minecraft.server.v1_16_R1.SectionPosition;
import net.minecraft.server.v1_16_R1.TicketType;
import net.minecraft.server.v1_16_R1.TileEntity;
import net.minecraft.server.v1_16_R1.Unit;
import net.minecraft.server.v1_16_R1.World;
import net.minecraft.server.v1_16_R1.WorldDimension;
import net.minecraft.server.v1_16_R1.WorldServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.event.world.WorldSaveEvent;

public final class CustomWorldServer extends WorldServer {

  private static final Logger LOGGER = LogManager.getLogger("SWM World");

  private static final TicketType<Unit> SWM_TICKET = TicketType.a("swm-chunk", (a, b) -> 0);

  private static final ExecutorService WORLD_SAVER_SERVICE = Executors.newFixedThreadPool(4,
    new ThreadFactoryBuilder().setNameFormat("SWM Pool Thread #%1$d").build());

  private final BiomeBase defaultBiome;

  private final Object saveLock = new Object();

  @Getter
  private final CraftSlimeWorld slimeWorld;

  @Getter
  @Setter
  private boolean ready = false;

  CustomWorldServer(final CraftSlimeWorld world, final IWorldDataServer worldData, final ResourceKey<World> worldKey,
                    final ResourceKey<WorldDimension> dimensionKey, final ResourceKey<DimensionManager> dmKey,
                    final DimensionManager dimensionManager, final ChunkGenerator chunkGenerator,
                    final org.bukkit.World.Environment env) throws IOException {
    super(
      MinecraftServer.getServer(),
      MinecraftServer.getServer().executorService,
      v1_16_R1SlimeNMS.CONVERTABLE.c(world.getName(), dimensionKey),
      worldData,
      worldKey,
      dmKey,
      dimensionManager,
      MinecraftServer.getServer().worldLoadListenerFactory.create(11),
      chunkGenerator,
      false,
      0,
      new ArrayList<>(),
      true,
      env,
      null);
    // SUPER FINISH
    this.slimeWorld = world;
    final SlimePropertyMap propertyMap = world.getPropertyMap();
    this.worldDataServer.setDifficulty(EnumDifficulty.valueOf(
      propertyMap.getValue(SlimeProperties.DIFFICULTY).toUpperCase(Locale.ROOT)));
    this.worldDataServer.setSpawn(new BlockPosition(
      propertyMap.getValue(SlimeProperties.SPAWN_X),
      propertyMap.getValue(SlimeProperties.SPAWN_Y),
      propertyMap.getValue(SlimeProperties.SPAWN_Z)));
    super.setSpawnFlags(
      propertyMap.getValue(SlimeProperties.ALLOW_MONSTERS),
      propertyMap.getValue(SlimeProperties.ALLOW_ANIMALS));
    this.pvpMode = propertyMap.getValue(SlimeProperties.PVP);
    final String biomeStr = this.slimeWorld.getPropertyMap().getValue(SlimeProperties.DEFAULT_BIOME);
    this.defaultBiome = IRegistry.BIOME.get(new MinecraftKey(biomeStr));
  }

  @Override
  public void save(final IProgressUpdate iprogressupdate, final boolean flag, final boolean flag1) {
    if (this.slimeWorld.isReadOnly() || flag1) {
      return;
    }
    if (flag) { // TODO Is this really 'forceSave'? Doesn't look like it tbh
      Bukkit.getPluginManager().callEvent(new WorldSaveEvent(this.getWorld()));
    }
    this.timings.tracker.startTiming();
    this.getChunkProvider().save(flag);
    this.timings.tracker.stopTiming();
    this.worldDataServer.a(this.getWorldBorder().t());
    this.worldDataServer.setCustomBossEvents(MinecraftServer.getServer().getBossBattleCustomData().save());
    // Update level data
    final NBTTagCompound compound = new NBTTagCompound();
    this.worldDataServer.a(MinecraftServer.getServer().f, compound);
    this.slimeWorld.getExtraData().getValue().put(Converter.convertTag("LevelData", compound));
    if (MinecraftServer.getServer().isStopped()) { // Make sure the world gets saved before stopping the server by running it from the main thread
      this.save();
      // Have to manually unlock the world as well
      try {
        this.slimeWorld.getLoader().unlockWorld(this.slimeWorld.getName());
      } catch (final IOException ex) {
        CustomWorldServer.LOGGER.error(String.format(
          "Failed to unlock the world %s. Please unlock it manually by using the command /swm manualunlock. Stack trace:",
          this.slimeWorld.getName()));
        ex.printStackTrace();
      } catch (final UnknownWorldException ignored) {
      }
    } else {
      CustomWorldServer.WORLD_SAVER_SERVICE.execute(this::save);
    }
  }

  ProtoChunkExtension getChunk(final int x, final int z) {
    final SlimeChunk slimeChunk = this.slimeWorld.getChunk(x, z);
    final Chunk chunk;
    if (slimeChunk instanceof NMSSlimeChunk) {
      chunk = ((NMSSlimeChunk) slimeChunk).getChunk();
      return new ProtoChunkExtension(chunk);
    }
    if (slimeChunk == null) {
      final ChunkCoordIntPair pos = new ChunkCoordIntPair(x, z);
      // Biomes
      final BiomeBase[] biomes = new BiomeBase[BiomeStorage.a];
      Arrays.fill(biomes, this.defaultBiome);
      final BiomeStorage biomeStorage = new BiomeStorage(biomes);
      // Tick lists
      final ProtoChunkTickList<Block> blockTickList = new ProtoChunkTickList<>(block ->
        block == null || block.getBlockData().isAir(), pos);
      final ProtoChunkTickList<FluidType> fluidTickList = new ProtoChunkTickList<>(type ->
        type == null || type == FluidTypes.EMPTY, pos);
      chunk = new Chunk(this, pos, biomeStorage, ChunkConverter.a, blockTickList, fluidTickList,
        0L, null, null);
      // Height Maps
      HeightMap.a(chunk, ChunkStatus.FULL.h());
    } else {
      chunk = this.createChunk(slimeChunk);
    }
    this.slimeWorld.updateChunk(new NMSSlimeChunk(chunk));
    return new ProtoChunkExtension(chunk);
  }

  void saveChunk(final Chunk chunk) {
    final SlimeChunk slimeChunk = this.slimeWorld.getChunk(chunk.getPos().x, chunk.getPos().z);
    if (slimeChunk instanceof NMSSlimeChunk) { // In case somehow the chunk object changes (might happen for some reason)
      ((NMSSlimeChunk) slimeChunk).setChunk(chunk);
    } else {
      this.slimeWorld.updateChunk(new NMSSlimeChunk(chunk));
    }
  }

  private Chunk createChunk(final SlimeChunk chunk) {
    final int x = chunk.getX();
    final int z = chunk.getZ();
    CustomWorldServer.LOGGER.debug("Loading chunk (" + x + ", " + z + ") on world " + this.slimeWorld.getName());
    final ChunkCoordIntPair pos = new ChunkCoordIntPair(x, z);
    // Biomes
    final int[] biomeIntArray = chunk.getBiomes();
    final BiomeStorage biomeStorage = new BiomeStorage(
      pos, this.getChunkProvider().getChunkGenerator().getWorldChunkManager(), biomeIntArray);
    // Tick lists
    final ProtoChunkTickList<Block> blockTickList = new ProtoChunkTickList<>(
      block -> block == null || block.getBlockData().isAir(), pos);
    final ProtoChunkTickList<FluidType> fluidTickList = new ProtoChunkTickList<>(
      type -> type == null || type == FluidTypes.EMPTY, pos);
    // Chunk sections
    CustomWorldServer.LOGGER.debug(String.format("Loading chunk sections for chunk (%d, %d) on world %s",
      pos.x, pos.z, this.slimeWorld.getName()));
    final ChunkSection[] sections = new ChunkSection[16];
    final LightEngine lightEngine = this.getChunkProvider().getLightEngine();
    lightEngine.b(pos, true);
    for (int sectionId = 0; sectionId < chunk.getSections().length; sectionId++) {
      final SlimeChunkSection slimeSection = chunk.getSections()[sectionId];
      if (slimeSection != null) {
        final ChunkSection section = new ChunkSection(sectionId << 4);
        CustomWorldServer.LOGGER.debug(String.format("ChunkSection #%d - Chunk (%d, %d) - World %s:",
          sectionId, pos.x, pos.z, this.slimeWorld.getName()));
        CustomWorldServer.LOGGER.debug("Block palette:");
        CustomWorldServer.LOGGER.debug(slimeSection.getPalette().toString());
        CustomWorldServer.LOGGER.debug("Block states array:");
        CustomWorldServer.LOGGER.debug(slimeSection.getBlockStates());
        CustomWorldServer.LOGGER.debug("Block light array:");
        CustomWorldServer.LOGGER.debug(slimeSection.getBlockLight() != null
          ? slimeSection.getBlockLight().getBacking()
          : "Not present");
        CustomWorldServer.LOGGER.debug("Sky light array:");
        CustomWorldServer.LOGGER.debug(slimeSection.getSkyLight() != null
          ? slimeSection.getSkyLight().getBacking()
          : "Not present");
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
      CustomWorldServer.LOGGER.debug(String.format("Loading tile entities for chunk (%d, %d) on world %s",
        pos.x, pos.z, this.slimeWorld.getName()));
      final List<CompoundTag> tileEntities = chunk.getTileEntities();
      int loadedEntities = 0;
      for (final CompoundTag tag : tileEntities) {
        final Optional<String> type = tag.getStringValue("id");
        // Sometimes null tile entities are saved
        if (type.isPresent()) {
          final IBlockData blockData = nmsChunk.getType(new BlockPosition(tag.getIntValue("x").get(), tag.getIntValue("y").get(), tag.getIntValue("z").get()));
          final TileEntity entity = TileEntity.create(blockData, (NBTTagCompound) Converter.convertTag(tag));
          if (entity != null) {
            nmsChunk.a(entity);
            loadedEntities++;
          }
        }
      }
      CustomWorldServer.LOGGER.debug(String.format("Loaded %d tile entities for chunk (%d, %d) on world %s",
        loadedEntities, pos.x, pos.z, this.slimeWorld.getName()));
      // Load entities
      CustomWorldServer.LOGGER.debug(String.format("Loading entities for chunk (%d, %d) on world %s",
        pos.x, pos.z, this.slimeWorld.getName()));
      final List<CompoundTag> entities = chunk.getEntities();
      loadedEntities = 0;
      for (final CompoundTag tag : entities) {
        EntityTypes.a((NBTTagCompound) Converter.convertTag(tag), nmsChunk.world, entity -> {
          nmsChunk.a(entity);
          return entity;
        });
        nmsChunk.d(true);
        loadedEntities++;
      }
      CustomWorldServer.LOGGER.debug(String.format("Loaded %d entities for chunk (%d, %d) on world %s",
        loadedEntities, pos.x, pos.z, this.slimeWorld.getName()));
    };
    final CompoundTag upgradeDataTag = ((CraftSlimeChunk) chunk).getUpgradeData();
    final Chunk nmsChunk = new Chunk(
      this, pos, biomeStorage, upgradeDataTag == null ? ChunkConverter.a : new ChunkConverter((NBTTagCompound)
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
    CustomWorldServer.LOGGER.debug("Loaded chunk (" + pos.x + ", " + pos.z + ") on world " + this.slimeWorld.getName());
    return nmsChunk;
  }

  private void save() {
    synchronized (this.saveLock) { // Don't want to save the SlimeWorld from multiple threads simultaneously
      try {
        CustomWorldServer.LOGGER.info("Saving world " + this.slimeWorld.getName() + "...");
        final long start = System.currentTimeMillis();
        final byte[] serializedWorld = this.slimeWorld.serialize();
        this.slimeWorld.getLoader().saveWorld(this.slimeWorld.getName(), serializedWorld, false);
        CustomWorldServer.LOGGER.info(String.format("World %s saved in %dms.",
          this.slimeWorld.getName(), System.currentTimeMillis() - start));
      } catch (final IOException ex) {
        ex.printStackTrace();
      }
    }
  }
}
