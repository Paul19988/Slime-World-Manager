package com.grinderwolf.swm.plugin.world.importer;

import com.flowpowered.nbt.ByteArrayTag;
import com.flowpowered.nbt.CompoundMap;
import com.flowpowered.nbt.CompoundTag;
import com.flowpowered.nbt.IntArrayTag;
import com.flowpowered.nbt.IntTag;
import com.flowpowered.nbt.ListTag;
import com.flowpowered.nbt.StringTag;
import com.flowpowered.nbt.Tag;
import com.flowpowered.nbt.TagType;
import com.flowpowered.nbt.stream.NBTInputStream;
import com.grinderwolf.swm.api.exceptions.InvalidWorldException;
import com.grinderwolf.swm.api.utils.NibbleArray;
import com.grinderwolf.swm.api.world.SlimeChunk;
import com.grinderwolf.swm.api.world.SlimeChunkSection;
import com.grinderwolf.swm.api.world.properties.SlimeProperties;
import com.grinderwolf.swm.api.world.properties.SlimePropertyMap;
import com.grinderwolf.swm.nms.CraftSlimeChunk;
import com.grinderwolf.swm.nms.CraftSlimeChunkSection;
import com.grinderwolf.swm.nms.CraftSlimeWorld;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WorldImporter {

  private static final Pattern MAP_FILE_PATTERN = Pattern.compile("^(?:map_([0-9]*).dat)$");

  private static final int SECTOR_SIZE = 4096;

  @NotNull
  public static CraftSlimeWorld readFromDirectory(@NotNull final File worldDir) throws InvalidWorldException,
    IOException {
    final File levelFile = new File(worldDir, "level.dat");
    if (!levelFile.exists() || !levelFile.isFile()) {
      throw new InvalidWorldException(worldDir);
    }
    final LevelData data = WorldImporter.readLevelData(levelFile);
    // World version
    final byte worldVersion;
    if (data.getVersion() == -1) { // DataVersion tag was added in 1.9
      worldVersion = 0x01;
    } else if (data.getVersion() < 818) {
      worldVersion = 0x02; // 1.9 world
    } else if (data.getVersion() < 1501) {
      worldVersion = 0x03; // 1.11 world
    } else if (data.getVersion() < 1517) {
      worldVersion = 0x04; // 1.13 world
    } else if (data.getVersion() < 2566) {
      worldVersion = 0x05; // 1.14 world
    } else {
      worldVersion = 0x07;
    }
    // Chunks
    final File regionDir = new File(worldDir, "region");
    if (!regionDir.exists() || !regionDir.isDirectory()) {
      throw new InvalidWorldException(worldDir);
    }
    final Map<Long, SlimeChunk> chunks = new HashMap<>();
    for (final File file : regionDir.listFiles((dir, name) -> name.endsWith(".mca"))) {
      chunks.putAll(WorldImporter.loadChunks(file, worldVersion).stream()
        .collect(Collectors.toMap(
          chunk -> (long) chunk.getZ() * Integer.MAX_VALUE + (long) chunk.getX(),
          chunk -> chunk)));
    }
    if (chunks.isEmpty()) {
      throw new InvalidWorldException(worldDir);
    }
    // World maps
    final File dataDir = new File(worldDir, "data");
    final List<CompoundTag> maps = new ArrayList<>();
    if (dataDir.exists()) {
      if (!dataDir.isDirectory()) {
        throw new InvalidWorldException(worldDir);
      }
      for (final File mapFile : dataDir.listFiles((dir, name) -> WorldImporter.MAP_FILE_PATTERN.matcher(name).matches())) {
        maps.add(WorldImporter.loadMap(mapFile));
      }
    }
    // Extra Data
    final CompoundMap extraData = new CompoundMap();
    if (!data.getGameRules().isEmpty()) {
      final CompoundMap gamerules = new CompoundMap();
      data.getGameRules().forEach((rule, value) -> gamerules.put(rule, new StringTag(rule, value)));
      extraData.put("gamerules", new CompoundTag("gamerules", gamerules));
    }
    final SlimePropertyMap propertyMap = new SlimePropertyMap();
    propertyMap.setValue(SlimeProperties.SPAWN_X, data.getSpawnX());
    propertyMap.setValue(SlimeProperties.SPAWN_Y, data.getSpawnY());
    propertyMap.setValue(SlimeProperties.SPAWN_Z, data.getSpawnZ());
    return new CraftSlimeWorld(chunks, new CompoundTag("", extraData), true, worldDir.getName(), propertyMap,
      false, maps, null, worldVersion);
  }

  private static boolean isEmpty(final byte[] array) {
    for (final byte b : array) {
      if (b != 0) {
        return false;
      }
    }
    return true;
  }

  private static boolean isEmpty(final long[] array) {
    return Arrays.stream(array).noneMatch(b -> b != 0L);
  }

  @NotNull
  private static List<SlimeChunk> loadChunks(@NotNull final File file, final byte worldVersion) throws IOException {
    final byte[] regionByteArray = Files.readAllBytes(file.toPath());
    final DataInputStream inputStream = new DataInputStream(new ByteArrayInputStream(regionByteArray));
    final List<ChunkEntry> chunks = new ArrayList<>(1024);
    for (int i = 0; i < 1024; i++) {
      final int entry = inputStream.readInt();
      final int chunkOffset = entry >>> 8;
      final int chunkSize = entry & 15;
      if (entry != 0) {
        final ChunkEntry chunkEntry = new ChunkEntry(
          chunkOffset * WorldImporter.SECTOR_SIZE, chunkSize * WorldImporter.SECTOR_SIZE);
        chunks.add(chunkEntry);
      }
    }
    return chunks.stream()
      .map(entry -> {
        try {
          final DataInputStream headerStream = new DataInputStream(
            new ByteArrayInputStream(regionByteArray, entry.getOffset(), entry.getPaddedSize()));
          final int chunkSize = headerStream.readInt() - 1;
          final int compressionScheme = headerStream.readByte();
          final DataInputStream chunkStream = new DataInputStream(
            new ByteArrayInputStream(regionByteArray, entry.getOffset() + 5, chunkSize));
          final InputStream decompressorStream = compressionScheme == 1
            ? new GZIPInputStream(chunkStream)
            : new InflaterInputStream(chunkStream);
          final NBTInputStream nbtStream = new NBTInputStream(
            decompressorStream, NBTInputStream.NO_COMPRESSION, ByteOrder.BIG_ENDIAN);
          final CompoundTag globalCompound = (CompoundTag) nbtStream.readTag();
          final CompoundMap globalMap = globalCompound.getValue();
          if (!globalMap.containsKey("Level")) {
            throw new RuntimeException("Missing Level tag?");
          }
          final CompoundTag levelCompound = (CompoundTag) globalMap.get("Level");
          return WorldImporter.readChunk(levelCompound, worldVersion);
        } catch (final IOException ex) {
          throw new RuntimeException(ex);
        }
      })
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  @NotNull
  private static CompoundTag loadMap(@NotNull final File mapFile) throws IOException {
    final String fileName = mapFile.getName();
    final int mapId = Integer.parseInt(fileName.substring(4, fileName.length() - 4));
    final CompoundTag tag;
    try (final NBTInputStream nbtStream = new NBTInputStream(new FileInputStream(mapFile),
      NBTInputStream.GZIP_COMPRESSION, ByteOrder.BIG_ENDIAN)) {
      tag = nbtStream.readTag().getAsCompoundTag().get().getAsCompoundTag("data").get();
    }
    tag.getValue().put("id", new IntTag("id", mapId));
    return tag;
  }

  @Nullable
  private static SlimeChunk readChunk(@NotNull final CompoundTag compound, final byte worldVersion) {
    final int chunkX = compound.getAsIntTag("xPos").get().getValue();
    final int chunkZ = compound.getAsIntTag("zPos").get().getValue();
    final Optional<String> status = compound.getStringValue("Status");
    if (status.isPresent() && !status.get().equals("postprocessed") && !status.get().startsWith("full")) {
      // It's a protochunk
      return null;
    }
    final int[] biomes;
    final Tag biomesTag = compound.getValue().get("Biomes");
    if (biomesTag instanceof IntArrayTag) {
      biomes = ((IntArrayTag) biomesTag).getValue();
    } else if (biomesTag instanceof ByteArrayTag) {
      final byte[] byteBiomes = ((ByteArrayTag) biomesTag).getValue();
      biomes = WorldImporter.toIntArray(byteBiomes);
    } else {
      biomes = null;
    }
    final Optional<CompoundTag> optionalHeightMaps = compound.getAsCompoundTag("Heightmaps");
    final CompoundTag heightMapsCompound;
    if (worldVersion >= 0x04) {
      heightMapsCompound = optionalHeightMaps.orElse(new CompoundTag("", new CompoundMap()));
    } else {
      // Pre 1.13 world
      final int[] heightMap = compound.getIntArrayValue("HeightMap").orElse(new int[256]);
      heightMapsCompound = new CompoundTag("", new CompoundMap());
      heightMapsCompound.getValue().put("heightMap", new IntArrayTag("heightMap", heightMap));
    }
    final List<CompoundTag> tileEntities = ((ListTag<CompoundTag>) compound.getAsListTag("TileEntities")
      .orElse(new ListTag<>("TileEntities", TagType.TAG_COMPOUND, new ArrayList<>())))
      .getValue();
    final List<CompoundTag> entities = ((ListTag<CompoundTag>) compound.getAsListTag("Entities")
      .orElse(new ListTag<>("Entities", TagType.TAG_COMPOUND, new ArrayList<>())))
      .getValue();
    final ListTag<CompoundTag> sectionsTag = (ListTag<CompoundTag>) compound.getAsListTag("Sections").get();
    final SlimeChunkSection[] sectionArray = new SlimeChunkSection[16];
    for (final CompoundTag sectionTag : sectionsTag.getValue()) {
      final int index = sectionTag.getByteValue("Y").get();
      if (index < 0) {
        // For some reason MC 1.14 worlds contain an empty section with Y = -1.
        continue;
      }
      final byte[] blocks = sectionTag.getByteArrayValue("Blocks").orElse(null);
      final NibbleArray dataArray;
      final ListTag<CompoundTag> paletteTag;
      final long[] blockStatesArray;
      if (worldVersion < 0x04) {
        dataArray = new NibbleArray(sectionTag.getByteArrayValue("Data").get());
        if (WorldImporter.isEmpty(blocks)) { // Just skip it
          continue;
        }
        paletteTag = null;
        blockStatesArray = null;
      } else {
        dataArray = null;
        paletteTag = (ListTag<CompoundTag>) sectionTag.getAsListTag("Palette").orElse(null);
        blockStatesArray = sectionTag.getLongArrayValue("BlockStates").orElse(null);
        if (paletteTag == null || blockStatesArray == null || WorldImporter.isEmpty(blockStatesArray)) { // Skip it
          continue;
        }
      }
      final NibbleArray blockLightArray = sectionTag.getValue().containsKey("BlockLight")
        ? new NibbleArray(sectionTag.getByteArrayValue("BlockLight").get())
        : null;
      final NibbleArray skyLightArray = sectionTag.getValue().containsKey("SkyLight")
        ? new NibbleArray(sectionTag.getByteArrayValue("SkyLight").get())
        : null;
      sectionArray[index] = new CraftSlimeChunkSection(
        blockLightArray, blockStatesArray, blocks, dataArray, null, paletteTag, skyLightArray);
    }
    for (final SlimeChunkSection section : sectionArray) {
      if (section != null) { // Chunk isn't empty
        return new CraftSlimeChunk(
          biomes, entities, heightMapsCompound, sectionArray, tileEntities, null, chunkX, chunkZ);
      }
    }
    // Chunk is empty
    return null;
  }

  private static LevelData readLevelData(final File file) throws IOException, InvalidWorldException {
    final Optional<CompoundTag> tag;
    try (final NBTInputStream nbtStream = new NBTInputStream(new FileInputStream(file))) {
      tag = nbtStream.readTag().getAsCompoundTag();
    }
    if (tag.isPresent()) {
      final Optional<CompoundTag> dataTag = tag.get().getAsCompoundTag("Data");
      if (dataTag.isPresent()) {
        // Data version
        final int dataVersion = dataTag.get().getIntValue("DataVersion").orElse(-1);
        // Game rules
        final Map<String, String> gameRules = new HashMap<>();
        final Optional<CompoundTag> rulesList = dataTag.get().getAsCompoundTag("GameRules");
        rulesList.ifPresent(compoundTag -> compoundTag.getValue().forEach((ruleName, ruleTag) ->
          gameRules.put(ruleName, ruleTag.getAsStringTag().get().getValue())));
        final int spawnX = dataTag.get().getIntValue("SpawnX").orElse(0);
        final int spawnY = dataTag.get().getIntValue("SpawnY").orElse(255);
        final int spawnZ = dataTag.get().getIntValue("SpawnZ").orElse(0);
        return new LevelData(gameRules, spawnX, spawnY, spawnZ, dataVersion);
      }
    }
    throw new InvalidWorldException(file.getParentFile());
  }

  private static int[] toIntArray(final byte[] buf) {
    final ByteBuffer buffer = ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN);
    final int[] ret = new int[buf.length / 4];
    buffer.asIntBuffer().get(ret);
    return ret;
  }

  @Getter
  @RequiredArgsConstructor
  private static class ChunkEntry {

    private final int offset;

    private final int paddedSize;
  }
}
