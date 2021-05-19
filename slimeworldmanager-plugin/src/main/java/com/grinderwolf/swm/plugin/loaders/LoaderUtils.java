package com.grinderwolf.swm.plugin.loaders;

import com.flowpowered.nbt.CompoundMap;
import com.flowpowered.nbt.CompoundTag;
import com.flowpowered.nbt.DoubleTag;
import com.flowpowered.nbt.IntArrayTag;
import com.flowpowered.nbt.IntTag;
import com.flowpowered.nbt.ListTag;
import com.flowpowered.nbt.TagType;
import com.flowpowered.nbt.stream.NBTInputStream;
import com.github.luben.zstd.Zstd;
import com.grinderwolf.swm.api.exceptions.CorruptedWorldException;
import com.grinderwolf.swm.api.exceptions.NewerFormatException;
import com.grinderwolf.swm.api.loaders.SlimeLoader;
import com.grinderwolf.swm.api.utils.NibbleArray;
import com.grinderwolf.swm.api.utils.SlimeFormat;
import com.grinderwolf.swm.api.world.SlimeChunk;
import com.grinderwolf.swm.api.world.SlimeChunkSection;
import com.grinderwolf.swm.api.world.properties.SlimePropertyMap;
import com.grinderwolf.swm.nms.CraftSlimeChunk;
import com.grinderwolf.swm.nms.CraftSlimeChunkSection;
import com.grinderwolf.swm.nms.CraftSlimeWorld;
import com.grinderwolf.swm.plugin.config.DatasourceConfig;
import com.grinderwolf.swm.plugin.loaders.file.FileLoader;
import com.grinderwolf.swm.plugin.loaders.mongo.MongoLoader;
import com.grinderwolf.swm.plugin.loaders.mysql.MysqlLoader;
import com.grinderwolf.swm.plugin.log.Logging;
import com.mongodb.MongoException;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LoaderUtils {

  public static final long LOCK_INTERVAL = 60000L;

  public static final long MAX_LOCK_TIME = 300000L; // Max time difference between current time millis and world lock

  private static final Map<String, SlimeLoader> loaderMap = new HashMap<>();

  public static CraftSlimeWorld deserializeWorld(@NotNull final SlimeLoader loader, @NotNull final String worldName,
                                                 final byte[] serializedWorld,
                                                 @NotNull final SlimePropertyMap propertyMap, final boolean readOnly)
    throws IOException, CorruptedWorldException, NewerFormatException {
    final DataInputStream dataStream = new DataInputStream(new ByteArrayInputStream(serializedWorld));
    try {
      final byte[] fileHeader = new byte[SlimeFormat.SLIME_HEADER.length];
      dataStream.read(fileHeader);
      if (!Arrays.equals(SlimeFormat.SLIME_HEADER, fileHeader)) {
        throw new CorruptedWorldException(worldName);
      }
      // File version
      final byte version = dataStream.readByte();
      if (version > SlimeFormat.SLIME_VERSION) {
        throw new NewerFormatException(version);
      }
      // World version
      byte worldVersion;
      if (version >= 6) {
        worldVersion = dataStream.readByte();
      } else if (version >= 4) { // In v4 there's just a boolean indicating whether the world is pre-1.13 or post-1.13
        worldVersion = (byte) (dataStream.readBoolean() ? 0x04 : 0x01);
      } else {
        worldVersion = 0; // We'll try to automatically detect it later
      }
      // Chunk
      final short minX = dataStream.readShort();
      final short minZ = dataStream.readShort();
      final int width = dataStream.readShort();
      final int depth = dataStream.readShort();
      if (width <= 0 || depth <= 0) {
        throw new CorruptedWorldException(worldName);
      }
      final int bitmaskSize = (int) Math.ceil((width * depth) / 8.0D);
      final byte[] chunkBitmask = new byte[bitmaskSize];
      dataStream.read(chunkBitmask);
      final BitSet chunkBitset = BitSet.valueOf(chunkBitmask);
      final int compressedChunkDataLength = dataStream.readInt();
      final int chunkDataLength = dataStream.readInt();
      final byte[] compressedChunkData = new byte[compressedChunkDataLength];
      final byte[] chunkData = new byte[chunkDataLength];
      dataStream.read(compressedChunkData);
      // Tile Entities
      final int compressedTileEntitiesLength = dataStream.readInt();
      final int tileEntitiesLength = dataStream.readInt();
      final byte[] compressedTileEntities = new byte[compressedTileEntitiesLength];
      final byte[] tileEntities = new byte[tileEntitiesLength];
      dataStream.read(compressedTileEntities);
      // Entities
      byte[] compressedEntities = new byte[0];
      byte[] entities = new byte[0];
      if (version >= 3) {
        final boolean hasEntities = dataStream.readBoolean();
        if (hasEntities) {
          final int compressedEntitiesLength = dataStream.readInt();
          final int entitiesLength = dataStream.readInt();
          compressedEntities = new byte[compressedEntitiesLength];
          entities = new byte[entitiesLength];
          dataStream.read(compressedEntities);
        }
      }
      // Extra NBT tag
      byte[] compressedExtraTag = new byte[0];
      byte[] extraTag = new byte[0];
      if (version >= 2) {
        final int compressedExtraTagLength = dataStream.readInt();
        final int extraTagLength = dataStream.readInt();
        compressedExtraTag = new byte[compressedExtraTagLength];
        extraTag = new byte[extraTagLength];
        dataStream.read(compressedExtraTag);
      }
      // World Map NBT tag
      byte[] compressedMapsTag = new byte[0];
      byte[] mapsTag = new byte[0];
      if (version >= 7) {
        final int compressedMapsTagLength = dataStream.readInt();
        final int mapsTagLength = dataStream.readInt();
        compressedMapsTag = new byte[compressedMapsTagLength];
        mapsTag = new byte[mapsTagLength];
        dataStream.read(compressedMapsTag);
      }
      if (dataStream.read() != -1) {
        throw new CorruptedWorldException(worldName);
      }
      // Data decompression
      Zstd.decompress(chunkData, compressedChunkData);
      Zstd.decompress(tileEntities, compressedTileEntities);
      Zstd.decompress(entities, compressedEntities);
      Zstd.decompress(extraTag, compressedExtraTag);
      Zstd.decompress(mapsTag, compressedMapsTag);
      // Chunk deserialization
      final Map<Long, SlimeChunk> chunks = LoaderUtils.readChunks(
        worldVersion, version, worldName, minX, minZ, width, depth, chunkBitset, chunkData);
      // Entity deserialization
      final CompoundTag entitiesCompound = LoaderUtils.readCompoundTag(entities);
      if (entitiesCompound != null) {
        final ListTag<CompoundTag> entitiesList = (ListTag<CompoundTag>) entitiesCompound.getValue().get("entities");
        for (final CompoundTag entityCompound : entitiesList.getValue()) {
          final ListTag<DoubleTag> listTag = (ListTag<DoubleTag>) entityCompound.getAsListTag("Pos").get();
          final int chunkX = LoaderUtils.floor(listTag.getValue().get(0).getValue()) >> 4;
          final int chunkZ = LoaderUtils.floor(listTag.getValue().get(2).getValue()) >> 4;
          final long chunkKey = (long) chunkZ * Integer.MAX_VALUE + (long) chunkX;
          final SlimeChunk chunk = chunks.get(chunkKey);
          if (chunk == null) {
            throw new CorruptedWorldException(worldName);
          }
          chunk.getEntities().add(entityCompound);
        }
      }
      // Tile Entity deserialization
      final CompoundTag tileEntitiesCompound = LoaderUtils.readCompoundTag(tileEntities);
      if (tileEntitiesCompound != null) {
        final ListTag<CompoundTag> tileEntitiesList = (ListTag<CompoundTag>) tileEntitiesCompound.getValue().get("tiles");
        for (final CompoundTag tileEntityCompound : tileEntitiesList.getValue()) {
          final int chunkX = ((IntTag) tileEntityCompound.getValue().get("x")).getValue() >> 4;
          final int chunkZ = ((IntTag) tileEntityCompound.getValue().get("z")).getValue() >> 4;
          final long chunkKey = (long) chunkZ * Integer.MAX_VALUE + (long) chunkX;
          final SlimeChunk chunk = chunks.get(chunkKey);
          if (chunk == null) {
            throw new CorruptedWorldException(worldName);
          }
          chunk.getTileEntities().add(tileEntityCompound);
        }
      }
      // Extra Data
      CompoundTag extraCompound = LoaderUtils.readCompoundTag(extraTag);
      if (extraCompound == null) {
        extraCompound = new CompoundTag("", new CompoundMap());
      }
      // World Maps
      final CompoundTag mapsCompound = LoaderUtils.readCompoundTag(mapsTag);
      final List<CompoundTag> mapList;
      if (mapsCompound != null) {
        mapList = (List<CompoundTag>) mapsCompound.getAsListTag("maps").map(ListTag::getValue).orElse(new ArrayList<>());
      } else {
        mapList = new ArrayList<>();
      }
      // v1_13 world format detection for old versions
      if (worldVersion == 0) {
        mainLoop:
        for (final SlimeChunk chunk : chunks.values()) {
          for (final SlimeChunkSection section : chunk.getSections()) {
            if (section != null) {
              worldVersion = (byte) (section.getBlocks() == null ? 0x04 : 0x01);
              break mainLoop;
            }
          }
        }
      }
      // World properties
      SlimePropertyMap worldPropertyMap = propertyMap;
      final Optional<CompoundMap> propertiesMap = extraCompound
        .getAsCompoundTag("properties")
        .map(CompoundTag::getValue);
      if (propertiesMap.isPresent()) {
        worldPropertyMap = new SlimePropertyMap(propertiesMap.get());
        worldPropertyMap.merge(propertyMap); // Override world properties
      } else if (propertyMap == null) { // Make sure the property map is never null
        worldPropertyMap = new SlimePropertyMap();
      }
      return new CraftSlimeWorld(
        chunks, extraCompound, !readOnly, worldName, worldPropertyMap, readOnly, mapList, loader, worldVersion);
    } catch (final EOFException ex) {
      throw new CorruptedWorldException(worldName, ex);
    }
  }

  @NotNull
  public static List<String> getAvailableLoadersNames() {
    return new LinkedList<>(LoaderUtils.loaderMap.keySet());
  }

  @Nullable
  public static SlimeLoader getLoader(final String dataSource) {
    return LoaderUtils.loaderMap.get(dataSource);
  }

  public static void registerLoader(@NotNull final String dataSource, @NotNull final SlimeLoader loader) {
    if (LoaderUtils.loaderMap.containsKey(dataSource)) {
      throw new IllegalArgumentException("Data source " + dataSource + " already has a declared loader!");
    }
    if (loader instanceof UpdatableLoader) {
      try {
        ((UpdatableLoader) loader).update();
      } catch (final UpdatableLoader.NewerDatabaseException e) {
        Logging.error("Data source " + dataSource + " version is " + e.getDatabaseVersion() + ", while" +
          " this SWM version only supports up to version " + e.getCurrentVersion() + ".");
        return;
      } catch (final IOException ex) {
        Logging.error("Failed to check if data source " + dataSource + " is updated:");
        ex.printStackTrace();
        return;
      }
    }
    LoaderUtils.loaderMap.put(dataSource, loader);
  }

  public static void registerLoaders() {
    // File loader
    LoaderUtils.registerLoader("file", new FileLoader(new File(DatasourceConfig.File.path)));
    // Mysql loader
    if (DatasourceConfig.Mysql.enabled) {
      try {
        LoaderUtils.registerLoader("mysql", new MysqlLoader());
      } catch (final SQLException ex) {
        Logging.error("Failed to establish connection to the MySQL server:");
        ex.printStackTrace();
      }
    }
    // MongoDB loader
    if (DatasourceConfig.MongoDB.enabled) {
      try {
        LoaderUtils.registerLoader("mongodb", new MongoLoader());
      } catch (final MongoException ex) {
        Logging.error("Failed to establish connection to the MongoDB server:");
        ex.printStackTrace();
      }
    }
  }

  private static int floor(final double num) {
    final int floor = (int) num;
    return floor == num ? floor : floor - (int) (Double.doubleToRawLongBits(num) >>> 63);
  }

  @NotNull
  private static SlimeChunkSection[] readChunkSections(@NotNull final DataInputStream dataStream,
                                                       final byte worldVersion, final int version) throws IOException {
    final SlimeChunkSection[] chunkSectionArray = new SlimeChunkSection[16];
    final byte[] sectionBitmask = new byte[2];
    dataStream.read(sectionBitmask);
    final BitSet sectionBitset = BitSet.valueOf(sectionBitmask);
    for (int i = 0; i < 16; i++) {
      if (sectionBitset.get(i)) {
        // Block Light Nibble Array
        final NibbleArray blockLightArray;
        if (version < 5 || dataStream.readBoolean()) {
          final byte[] blockLightByteArray = new byte[2048];
          dataStream.read(blockLightByteArray);
          blockLightArray = new NibbleArray(blockLightByteArray);
        } else {
          blockLightArray = null;
        }
        // Block data
        final byte[] blockArray;
        final NibbleArray dataArray;
        final ListTag<CompoundTag> paletteTag;
        final long[] blockStatesArray;
        // Post 1.13 block format
        if (worldVersion >= 0x04) {
          // Palette
          final int paletteLength = dataStream.readInt();
          final List<CompoundTag> paletteList = new ArrayList<>(paletteLength);
          for (int index = 0; index < paletteLength; index++) {
            final int tagLength = dataStream.readInt();
            final byte[] serializedTag = new byte[tagLength];
            dataStream.read(serializedTag);
            paletteList.add(LoaderUtils.readCompoundTag(serializedTag));
          }
          paletteTag = new ListTag<>("", TagType.TAG_LIST, paletteList);
          // Block states
          final int blockStatesArrayLength = dataStream.readInt();
          blockStatesArray = new long[blockStatesArrayLength];
          for (int index = 0; index < blockStatesArrayLength; index++) {
            blockStatesArray[index] = dataStream.readLong();
          }
          blockArray = null;
          dataArray = null;
        } else {
          blockArray = new byte[4096];
          dataStream.read(blockArray);
          // Block Data Nibble Array
          final byte[] dataByteArray = new byte[2048];
          dataStream.read(dataByteArray);
          dataArray = new NibbleArray(dataByteArray);
          paletteTag = null;
          blockStatesArray = null;
        }
        // Sky Light Nibble Array
        final NibbleArray skyLightArray;
        if (version < 5 || dataStream.readBoolean()) {
          final byte[] skyLightByteArray = new byte[2048];
          dataStream.read(skyLightByteArray);
          skyLightArray = new NibbleArray(skyLightByteArray);
        } else {
          skyLightArray = null;
        }
        // HypixelBlocks 3
        if (version < 4) {
          final short hypixelBlocksLength = dataStream.readShort();
          dataStream.skip(hypixelBlocksLength);
        }
        chunkSectionArray[i] = new CraftSlimeChunkSection(
          blockLightArray, blockStatesArray, blockArray, dataArray, null, paletteTag, skyLightArray);
      }
    }
    return chunkSectionArray;
  }

  @NotNull
  private static Map<Long, SlimeChunk> readChunks(final byte worldVersion, final int version,
                                                  @NotNull final String worldName, final int minX, final int minZ,
                                                  final int width, final int depth, @NotNull final BitSet chunkBitset,
                                                  final byte[] chunkData) throws IOException {
    final DataInputStream dataStream = new DataInputStream(new ByteArrayInputStream(chunkData));
    final Map<Long, SlimeChunk> chunkMap = new HashMap<>();
    for (int z = 0; z < depth; z++) {
      for (int x = 0; x < width; x++) {
        final int bitsetIndex = z * width + x;
        if (chunkBitset.get(bitsetIndex)) {
          // Height Maps
          CompoundTag heightMaps;
          if (worldVersion >= 0x04) {
            final int heightMapsLength = dataStream.readInt();
            final byte[] heightMapsArray = new byte[heightMapsLength];
            dataStream.read(heightMapsArray);
            heightMaps = LoaderUtils.readCompoundTag(heightMapsArray);
            // Height Maps might be null if empty
            if (heightMaps == null) {
              heightMaps = new CompoundTag("", new CompoundMap());
            }
          } else {
            final int[] heightMap = new int[256];
            for (int i = 0; i < 256; i++) {
              heightMap[i] = dataStream.readInt();
            }
            final CompoundMap map = new CompoundMap();
            map.put("heightMap", new IntArrayTag("heightMap", heightMap));
            heightMaps = new CompoundTag("", map);
          }
          // Biome array
          final int[] biomes;
          if (version == 8 && worldVersion < 0x04) {
            // Patch the v8 bug: biome array size is wrong for old worlds
            dataStream.readInt();
          }
          if (worldVersion >= 0x04) {
            final int biomesArrayLength = version >= 8 ? dataStream.readInt() : 256;
            biomes = new int[biomesArrayLength];
            for (int i = 0; i < biomes.length; i++) {
              biomes[i] = dataStream.readInt();
            }
          } else {
            final byte[] byteBiomes = new byte[256];
            dataStream.read(byteBiomes);
            biomes = LoaderUtils.toIntArray(byteBiomes);
          }
          // Chunk Sections
          final SlimeChunkSection[] sections = LoaderUtils.readChunkSections(dataStream, worldVersion, version);
          final CraftSlimeChunk chunk = new CraftSlimeChunk(
            biomes, new ArrayList<>(), heightMaps, sections, new ArrayList<>(), worldName, minX + x, minZ + z);
          chunkMap.put(((long) minZ + z) * Integer.MAX_VALUE + (long) minX + x, chunk);
        }
      }
    }
    return chunkMap;
  }

  @Nullable
  private static CompoundTag readCompoundTag(final byte[] serializedCompound) throws IOException {
    if (serializedCompound.length == 0) {
      return null;
    }
    final NBTInputStream stream = new NBTInputStream(new ByteArrayInputStream(serializedCompound), NBTInputStream.NO_COMPRESSION, ByteOrder.BIG_ENDIAN);
    final CompoundTag compoundTag = (CompoundTag) stream.readTag();
//        System.out.println("READING TAG: " + compoundTag);
    return compoundTag;
  }

  private static int[] toIntArray(final byte[] buf) {
    final ByteBuffer buffer = ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN);
    final int[] ret = new int[buf.length / 4];
    buffer.asIntBuffer().get(ret);
    return ret;
  }
}
