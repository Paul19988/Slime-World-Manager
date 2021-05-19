package com.grinderwolf.swm.importer;

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
import com.flowpowered.nbt.stream.NBTOutputStream;
import com.github.luben.zstd.Zstd;
import com.github.tomaslanger.chalk.Chalk;
import com.grinderwolf.swm.api.exceptions.InvalidWorldException;
import com.grinderwolf.swm.api.utils.NibbleArray;
import com.grinderwolf.swm.api.utils.SlimeFormat;
import com.grinderwolf.swm.api.world.SlimeChunk;
import com.grinderwolf.swm.api.world.SlimeChunkSection;
import com.grinderwolf.swm.nms.CraftSlimeChunk;
import com.grinderwolf.swm.nms.CraftSlimeChunkSection;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The SWMImporter class provides the ability to convert
 * a vanilla world folder into a slime file.
 * <p>
 * The importer may be run directly as executable or
 * used as dependency in your own plugins.
 */
@SuppressWarnings("unchecked")
public final class SWMImporter {

  private static final Pattern MAP_FILE_PATTERN = Pattern.compile("^map_([0-9]*).dat$");

  private static final int SECTOR_SIZE = 4096;

  /**
   * Returns a destination file at which the slime file will
   * be placed when run as an executable.
   * <p>
   * This method may be used by your plugin to output slime
   * files identical to the executable.
   *
   * @param worldFolder The world directory to import
   *
   * @return The output file destination
   */
  @NotNull
  public static File getDestinationFile(@NotNull final File worldFolder) {
    return new File(worldFolder.getParentFile(), worldFolder.getName() + ".slime");
  }

  /**
   * Import the given vanilla world directory into
   * a slime world file. The debug boolean may be
   * set to true in order to provide debug prints.
   *
   * @param worldFolder The world directory to import
   * @param outputFile The output file
   * @param debug Whether debug messages should be printed to sysout
   *
   * @throws IOException when the world could not be saved
   * @throws InvalidWorldException when the world is not valid
   * @throws IndexOutOfBoundsException if the world was too big
   */
  public static void importWorld(@NotNull final File worldFolder, @NotNull final File outputFile, final boolean debug)
    throws IOException, InvalidWorldException {
    if (!worldFolder.exists()) {
      throw new InvalidWorldException(worldFolder, "Are you sure the directory exists?");
    }
    if (!worldFolder.isDirectory()) {
      throw new InvalidWorldException(worldFolder, "It appears to be a regular file");
    }
    final File regionDir = new File(worldFolder, "region");
    final String[] regionDirList = regionDir.list();
    if (regionDirList == null || !regionDir.exists() || !regionDir.isDirectory() || regionDirList.length == 0) {
      throw new InvalidWorldException(worldFolder, "The world appears to be corrupted");
    }
    if (debug) {
      System.out.println("Loading world...");
    }
    final File levelFile = new File(worldFolder, "level.dat");
    if (!levelFile.exists() || !levelFile.isFile()) {
      throw new InvalidWorldException(worldFolder, "The world appears to be corrupted");
    }
    final LevelData data;
    try {
      data = SWMImporter.readLevelData(levelFile);
    } catch (final IOException ex) {
      throw new IOException("Failed to load world level file", ex);
    }
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
    if (debug) {
      System.out.println("World version: " + worldVersion);
    }
    final List<SlimeChunk> chunks = new ArrayList<>();
    for (final File file : regionDir.listFiles((dir, name) -> name.endsWith(".mca"))) {
      try {
        chunks.addAll(SWMImporter.loadChunks(file, worldVersion, debug));
      } catch (final IOException ex) {
        throw new IOException("Failed to read region file", ex);
      }
    }
    if (debug) {
      System.out.println("World " + worldFolder.getName() + " contains " + chunks.size() + " chunks.");
    }
    // World maps
    final File dataDir = new File(worldFolder, "data");
    final List<CompoundTag> maps = new ArrayList<>();
    if (dataDir.exists()) {
      if (!dataDir.isDirectory()) {
        throw new InvalidWorldException(worldFolder, "The data directory appears to be invalid");
      }
      try {
        for (final File mapFile : dataDir.listFiles((dir, name) -> SWMImporter.MAP_FILE_PATTERN.matcher(name).matches())) {
          maps.add(SWMImporter.loadMap(mapFile));
        }
      } catch (final IOException ex) {
        throw new IOException("Failed to read world maps", ex);
      }
    }
    final long start = System.currentTimeMillis();
    final byte[] slimeFormattedWorld = SWMImporter.generateSlimeWorld(chunks, worldVersion, data, maps);
    final long duration = System.currentTimeMillis() - start;
    if (debug) {
      System.out.println(Chalk.on("World " + worldFolder.getName() + " successfully serialized to " +
        "the Slime Format in " + duration + "ms!").green());
    }
    outputFile.createNewFile();
    try (final FileOutputStream stream = new FileOutputStream(outputFile)) {
      stream.write(slimeFormattedWorld);
      stream.flush();
    }
  }

  public static void main(final String[] args) {
    if (args.length == 0) {
      System.err.println(
        "Usage: java -jar slimeworldmanager-importer.jar <path-to-world-folder> [--accept] [--silent] [--print-error]");
      return;
    }
    final File worldDir = new File(args[0]);
    final File outputFile = SWMImporter.getDestinationFile(worldDir);
    final List<String> argList = Arrays.asList(args);
    final boolean hasAccepted = argList.contains("--accept");
    final boolean isSilent = argList.contains("--silent");
    final boolean printErrors = argList.contains("--print-error");
    if (!hasAccepted) {
      System.out.println("**** WARNING ****");
      System.out.println(
        "The Slime Format is meant to be used on tiny maps, not big survival worlds. It is recommended " +
          "to trim your world by using the Prune MCEdit tool to ensure you don't save more chunks than you want to.");
      System.out.println();
      System.out.println("NOTE: This utility will automatically ignore every chunk that doesn't contain any blocks.");
      System.out.print("Do you want to continue? [Y/N]: ");
      final Scanner scanner = new Scanner(System.in);
      final String response = scanner.next();
      if (!response.equalsIgnoreCase("Y")) {
        System.out.println("Your wish is my command.");
        return;
      }
    }
    try {
      SWMImporter.importWorld(worldDir, outputFile, !isSilent);
    } catch (final IndexOutOfBoundsException ex) {
      System.err.println("Oops, it looks like the world provided is too big to be imported. " +
        "Please trim it by using the MCEdit tool and try again.");
    } catch (final IOException ex) {
      System.err.println("Failed to save the world file.");
      ex.printStackTrace();
    } catch (final InvalidWorldException ex) {
      if (printErrors) {
        ex.printStackTrace();
      } else {
        System.err.println(ex.getMessage());
      }
    }
  }

  private static byte[] generateSlimeWorld(@NotNull final List<SlimeChunk> chunks, final byte worldVersion,
                                           @NotNull final LevelData levelData,
                                           @NotNull final List<CompoundTag> worldMaps) {
    final List<SlimeChunk> sortedChunks = new ArrayList<>(chunks);
    sortedChunks.sort(Comparator.comparingLong(chunk -> (long) chunk.getZ() * Integer.MAX_VALUE + (long) chunk.getX()));
    final ByteArrayOutputStream outByteStream = new ByteArrayOutputStream();
    final DataOutputStream outStream = new DataOutputStream(outByteStream);
    try {
      // File Header and Slime version
      outStream.write(SlimeFormat.SLIME_HEADER);
      outStream.write(SlimeFormat.SLIME_VERSION);
      // World version
      outStream.writeByte(worldVersion);
      // Lowest chunk coordinates
      final int minX = sortedChunks.stream().mapToInt(SlimeChunk::getX).min().getAsInt();
      final int minZ = sortedChunks.stream().mapToInt(SlimeChunk::getZ).min().getAsInt();
      final int maxX = sortedChunks.stream().mapToInt(SlimeChunk::getX).max().getAsInt();
      final int maxZ = sortedChunks.stream().mapToInt(SlimeChunk::getZ).max().getAsInt();
      outStream.writeShort(minX);
      outStream.writeShort(minZ);
      // Width and depth
      final int width = maxX - minX + 1;
      final int depth = maxZ - minZ + 1;
      outStream.writeShort(width);
      outStream.writeShort(depth);
      // Chunk Bitmask
      final BitSet chunkBitset = new BitSet(width * depth);
      for (final SlimeChunk chunk : sortedChunks) {
        final int bitsetIndex = (chunk.getZ() - minZ) * width + chunk.getX() - minX;
        chunkBitset.set(bitsetIndex, true);
      }
      final int chunkMaskSize = (int) Math.ceil((width * depth) / 8.0D);
      SWMImporter.writeBitSetAsBytes(outStream, chunkBitset, chunkMaskSize);
      // Chunks
      final byte[] chunkData = SWMImporter.serializeChunks(sortedChunks, worldVersion);
      final byte[] compressedChunkData = Zstd.compress(chunkData);
      outStream.writeInt(compressedChunkData.length);
      outStream.writeInt(chunkData.length);
      outStream.write(compressedChunkData);
      // Tile Entities
      final List<CompoundTag> tileEntitiesList = sortedChunks.stream()
        .flatMap(chunk -> chunk.getTileEntities().stream())
        .collect(Collectors.toList());
      final ListTag<CompoundTag> tileEntitiesNbtList = new ListTag<>("tiles", TagType.TAG_COMPOUND, tileEntitiesList);
      final CompoundTag tileEntitiesCompound = new CompoundTag(
        "", new CompoundMap(Collections.singletonList(tileEntitiesNbtList)));
      final byte[] tileEntitiesData = SWMImporter.serializeCompoundTag(tileEntitiesCompound);
      final byte[] compressedTileEntitiesData = Zstd.compress(tileEntitiesData);
      outStream.writeInt(compressedTileEntitiesData.length);
      outStream.writeInt(tileEntitiesData.length);
      outStream.write(compressedTileEntitiesData);
      // Entities
      final List<CompoundTag> entitiesList = sortedChunks.stream()
        .flatMap(chunk -> chunk.getEntities().stream())
        .collect(Collectors.toList());
      outStream.writeBoolean(!entitiesList.isEmpty());
      if (!entitiesList.isEmpty()) {
        final ListTag<CompoundTag> entitiesNbtList = new ListTag<>("entities", TagType.TAG_COMPOUND, entitiesList);
        final CompoundTag entitiesCompound = new CompoundTag(
          "", new CompoundMap(Collections.singletonList(entitiesNbtList)));
        final byte[] entitiesData = SWMImporter.serializeCompoundTag(entitiesCompound);
        final byte[] compressedEntitiesData = Zstd.compress(entitiesData);
        outStream.writeInt(compressedEntitiesData.length);
        outStream.writeInt(entitiesData.length);
        outStream.write(compressedEntitiesData);
      }
      // Extra Tag
      final CompoundMap extraMap = new CompoundMap();
      if (!levelData.getGameRules().isEmpty()) {
        final CompoundMap gamerules = new CompoundMap();
        levelData.getGameRules().forEach((rule, value) -> gamerules.put(rule, new StringTag(rule, value)));
        extraMap.put("gamerules", new CompoundTag("gamerules", gamerules));
      }
      final byte[] extraData = SWMImporter.serializeCompoundTag(new CompoundTag("", extraMap));
      final byte[] compressedExtraData = Zstd.compress(extraData);
      outStream.writeInt(compressedExtraData.length);
      outStream.writeInt(extraData.length);
      outStream.write(compressedExtraData);
      // World Maps
      final CompoundMap map = new CompoundMap();
      map.put("maps", new ListTag<>("maps", TagType.TAG_COMPOUND, worldMaps));
      final CompoundTag mapsCompound = new CompoundTag("", map);
      final byte[] mapArray = SWMImporter.serializeCompoundTag(mapsCompound);
      final byte[] compressedMapArray = Zstd.compress(mapArray);
      outStream.writeInt(compressedMapArray.length);
      outStream.writeInt(mapArray.length);
      outStream.write(compressedMapArray);
    } catch (final IOException ex) { // Ignore
      ex.printStackTrace();
    }
    return outByteStream.toByteArray();
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
  private static List<SlimeChunk> loadChunks(@NotNull final File file, final byte worldVersion, final boolean debug)
    throws IOException {
    if (debug) {
      System.out.println("Loading chunks from region file '" + file.getName() + "':");
    }
    final byte[] regionByteArray = Files.readAllBytes(file.toPath());
    final DataInputStream inputStream = new DataInputStream(new ByteArrayInputStream(regionByteArray));
    final List<ChunkEntry> chunks = new ArrayList<>(1024);
    for (int i = 0; i < 1024; i++) {
      final int entry = inputStream.readInt();
      final int chunkOffset = entry >>> 8;
      final int chunkSize = entry & 15;
      if (entry != 0) {
        final ChunkEntry chunkEntry = new ChunkEntry(
          chunkOffset * SWMImporter.SECTOR_SIZE, chunkSize * SWMImporter.SECTOR_SIZE);
        chunks.add(chunkEntry);
      }
    }
    final List<SlimeChunk> loadedChunks = chunks.stream().map(entry -> {
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
        return SWMImporter.readChunk(levelCompound, worldVersion);
      } catch (final IOException ex) {
        throw new RuntimeException(ex);
      }
    }).filter(Objects::nonNull).collect(Collectors.toList());
    if (debug) {
      System.out.println(loadedChunks.size() + " chunks loaded.");
    }
    return loadedChunks;
  }

  @NotNull
  private static CompoundTag loadMap(@NotNull final File mapFile) throws IOException {
    final String fileName = mapFile.getName();
    final int mapId = Integer.parseInt(fileName.substring(4, fileName.length() - 4));
    final NBTInputStream nbtStream = new NBTInputStream(
      new FileInputStream(mapFile), NBTInputStream.GZIP_COMPRESSION, ByteOrder.BIG_ENDIAN);
    final CompoundTag tag = nbtStream.readTag().getAsCompoundTag().get().getAsCompoundTag("data").get();
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
    final Tag<?> biomesTag = compound.getValue().get("Biomes");
    if (biomesTag instanceof IntArrayTag) {
      biomes = ((IntArrayTag) biomesTag).getValue();
    } else if (biomesTag instanceof ByteArrayTag) {
      final byte[] byteBiomes = ((ByteArrayTag) biomesTag).getValue();
      biomes = SWMImporter.toIntArray(byteBiomes);
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
        if (SWMImporter.isEmpty(blocks)) { // Just skip it
          continue;
        }
        paletteTag = null;
        blockStatesArray = null;
      } else {
        dataArray = null;
        paletteTag = (ListTag<CompoundTag>) sectionTag.getAsListTag("Palette").orElse(null);
        blockStatesArray = sectionTag.getLongArrayValue("BlockStates").orElse(null);
        if (paletteTag == null || blockStatesArray == null || SWMImporter.isEmpty(blockStatesArray)) { // Skip it
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
          null, chunkX, chunkZ, sectionArray, heightMapsCompound, biomes, tileEntities, entities);
      }
    }
    // Chunk is empty
    return null;
  }

  @NotNull
  private static LevelData readLevelData(@NotNull final File file) throws IOException, InvalidWorldException {
    final NBTInputStream nbtStream = new NBTInputStream(new FileInputStream(file));
    final Optional<CompoundTag> tag = nbtStream.readTag().getAsCompoundTag();
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
        return new LevelData(gameRules, dataVersion);
      }
    }
    throw new InvalidWorldException(file.getParentFile());
  }

  private static byte[] serializeChunks(@NotNull final List<SlimeChunk> chunks, final byte worldVersion)
    throws IOException {
    final ByteArrayOutputStream outByteStream = new ByteArrayOutputStream(16384);
    final DataOutputStream outStream = new DataOutputStream(outByteStream);
    for (final SlimeChunk chunk : chunks) {
      // Height Maps
      if (worldVersion >= 0x04) {
        final byte[] heightMaps = SWMImporter.serializeCompoundTag(chunk.getHeightMaps());
        outStream.writeInt(heightMaps.length);
        outStream.write(heightMaps);
      } else {
        final int[] heightMap = chunk.getHeightMaps().getIntArrayValue("heightMap").get();
        for (int i = 0; i < 256; i++) {
          outStream.writeInt(heightMap[i]);
        }
      }
      // Biomes
      final int[] biomes = chunk.getBiomes();
      if (worldVersion >= 0x04) {
        outStream.writeInt(biomes.length);
      }
      for (final int biome : biomes) {
        outStream.writeInt(biome);
      }
      // Chunk sections
      final SlimeChunkSection[] sections = chunk.getSections();
      final BitSet sectionBitmask = new BitSet(16);
      for (int i = 0; i < sections.length; i++) {
        sectionBitmask.set(i, sections[i] != null);
      }
      SWMImporter.writeBitSetAsBytes(outStream, sectionBitmask, 2);
      for (final SlimeChunkSection section : sections) {
        if (section == null) {
          continue;
        }
        // Block Light
        final boolean hasBlockLight = section.getBlockLight() != null;
        outStream.writeBoolean(hasBlockLight);
        if (hasBlockLight) {
          outStream.write(section.getBlockLight().getBacking());
        }
        // Block Data
        if (worldVersion >= 0x04) {
          // Palette
          final List<CompoundTag> palette = section.getPalette().getValue();
          outStream.writeInt(palette.size());
          for (final CompoundTag value : palette) {
            final byte[] serializedValue = SWMImporter.serializeCompoundTag(value);
            outStream.writeInt(serializedValue.length);
            outStream.write(serializedValue);
          }
          // Block states
          final long[] blockStates = section.getBlockStates();
          outStream.writeInt(blockStates.length);
          for (final long value : section.getBlockStates()) {
            outStream.writeLong(value);
          }
        } else {
          outStream.write(section.getBlocks());
          outStream.write(section.getData().getBacking());
        }
        // Sky Light
        final boolean hasSkyLight = section.getSkyLight() != null;
        outStream.writeBoolean(hasSkyLight);
        if (hasSkyLight) {
          outStream.write(section.getSkyLight().getBacking());
        }
      }
    }
    return outByteStream.toByteArray();
  }

  private static byte[] serializeCompoundTag(@NotNull final CompoundTag tag) throws IOException {
    final ByteArrayOutputStream outByteStream = new ByteArrayOutputStream();
    final NBTOutputStream outStream = new NBTOutputStream(
      outByteStream, NBTInputStream.NO_COMPRESSION, ByteOrder.BIG_ENDIAN);
    outStream.writeTag(tag);
    return outByteStream.toByteArray();
  }

  private static int[] toIntArray(final byte[] buf) {
    final ByteBuffer buffer = ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN);
    final int[] ret = new int[buf.length / 4];
    buffer.asIntBuffer().get(ret);
    return ret;
  }

  private static void writeBitSetAsBytes(@NotNull final DataOutputStream outStream, @NotNull final BitSet set,
                                         final int fixedSize) throws IOException {
    final byte[] array = set.toByteArray();
    outStream.write(array);
    final int chunkMaskPadding = fixedSize - array.length;
    for (int i = 0; i < chunkMaskPadding; i++) {
      outStream.write(0);
    }
  }
}
