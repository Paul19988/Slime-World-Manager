package com.grinderwolf.swm.nms;

import static com.grinderwolf.swm.api.world.properties.SlimeProperties.ALLOW_ANIMALS;
import static com.grinderwolf.swm.api.world.properties.SlimeProperties.ALLOW_MONSTERS;
import static com.grinderwolf.swm.api.world.properties.SlimeProperties.DIFFICULTY;
import static com.grinderwolf.swm.api.world.properties.SlimeProperties.ENVIRONMENT;
import static com.grinderwolf.swm.api.world.properties.SlimeProperties.PVP;
import static com.grinderwolf.swm.api.world.properties.SlimeProperties.SPAWN_X;
import static com.grinderwolf.swm.api.world.properties.SlimeProperties.SPAWN_Y;
import static com.grinderwolf.swm.api.world.properties.SlimeProperties.SPAWN_Z;
import com.flowpowered.nbt.CompoundMap;
import com.flowpowered.nbt.CompoundTag;
import com.flowpowered.nbt.ListTag;
import com.flowpowered.nbt.TagType;
import com.flowpowered.nbt.stream.NBTInputStream;
import com.flowpowered.nbt.stream.NBTOutputStream;
import com.github.luben.zstd.Zstd;
import com.grinderwolf.swm.api.exceptions.WorldAlreadyExistsException;
import com.grinderwolf.swm.api.loaders.SlimeLoader;
import com.grinderwolf.swm.api.utils.SlimeFormat;
import com.grinderwolf.swm.api.world.SlimeChunk;
import com.grinderwolf.swm.api.world.SlimeChunkSection;
import com.grinderwolf.swm.api.world.SlimeWorld;
import com.grinderwolf.swm.api.world.properties.SlimePropertyMap;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.bukkit.Difficulty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
@AllArgsConstructor
public final class CraftSlimeWorld implements SlimeWorld {

  private final Map<Long, SlimeChunk> chunks;

  private final CompoundTag extraData;

  private final boolean locked;

  private final String name;

  private final SlimePropertyMap propertyMap;

  private final boolean readOnly;

  private final List<CompoundTag> worldMaps;

  private SlimeLoader loader;

  private byte version;

  private static byte[] serializeChunks(@NotNull final List<SlimeChunk> chunks, final byte worldVersion)
    throws IOException {
    final ByteArrayOutputStream outByteStream = new ByteArrayOutputStream(16384);
    final DataOutputStream outStream = new DataOutputStream(outByteStream);
    for (final SlimeChunk chunk : chunks) {
      // Height Maps
      if (worldVersion >= 0x04) {
        final byte[] heightMaps = CraftSlimeWorld.serializeCompoundTag(chunk.getHeightMaps());
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
      CraftSlimeWorld.writeBitSetAsBytes(outStream, sectionBitmask, 2);
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
            final byte[] serializedValue = CraftSlimeWorld.serializeCompoundTag(value);
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

  private static byte[] serializeCompoundTag(@Nullable final CompoundTag tag) throws IOException {
    if (tag == null || tag.getValue().isEmpty()) {
      return new byte[0];
    }
    final ByteArrayOutputStream outByteStream = new ByteArrayOutputStream();
    final NBTOutputStream outStream = new NBTOutputStream(
      outByteStream, NBTInputStream.NO_COMPRESSION, ByteOrder.BIG_ENDIAN);
    outStream.writeTag(tag);
    return outByteStream.toByteArray();
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

  @SneakyThrows
  @NotNull
  @Override
  public SlimeWorld clone(@NotNull final String worldName) {
    return this.clone(worldName, null);
  }

  @NotNull
  @Override
  public SlimeWorld clone(@NotNull final String worldName, @Nullable final SlimeLoader loader)
    throws WorldAlreadyExistsException, IOException {
    return this.clone(worldName, loader, true);
  }

  @NotNull
  @Override
  public SlimeWorld clone(@NotNull final String worldName, @Nullable final SlimeLoader loader, final boolean lock)
    throws WorldAlreadyExistsException, IOException {
    if (this.name.equals(worldName)) {
      throw new IllegalArgumentException("The clone world cannot have the same name as the original world!");
    }
    if (loader != null && loader.worldExists(worldName)) {
      throw new WorldAlreadyExistsException(worldName);
    }
    final CraftSlimeWorld world;
    synchronized (this.chunks) {
      world = new CraftSlimeWorld(new HashMap<>(this.chunks), this.extraData.clone(), lock, worldName, this.propertyMap,
        loader == null, new ArrayList<>(this.worldMaps), loader == null ? this.loader : loader, this.version);
    }
    if (loader != null) {
      loader.saveWorld(worldName, world.serialize(), lock);
    }
    return world;
  }

  @Nullable
  @Override
  public SlimeChunk getChunk(final int x, final int z) {
    synchronized (this.chunks) {
      final Long index = (long) z * Integer.MAX_VALUE + (long) x;
      return this.chunks.get(index);
    }
  }

  @NotNull
  @Override
  public SlimeProperties getProperties() {
    return SlimeProperties.builder().spawnX(this.propertyMap.getValue(SPAWN_X))
      .spawnY(this.propertyMap.getValue(SPAWN_Y))
      .spawnZ(this.propertyMap.getValue(SPAWN_Z))
      .environment(this.propertyMap.getValue(ENVIRONMENT))
      .pvp(this.propertyMap.getValue(PVP))
      .allowMonsters(this.propertyMap.getValue(ALLOW_MONSTERS))
      .allowAnimals(this.propertyMap.getValue(ALLOW_ANIMALS))
      .difficulty(Difficulty.valueOf(this.propertyMap.getValue(DIFFICULTY).toUpperCase(Locale.ROOT)).getValue())
      .readOnly(this.readOnly).build();
  }

  public byte[] serialize() {
    final List<SlimeChunk> sortedChunks;
    synchronized (this.chunks) {
      sortedChunks = new ArrayList<>(this.chunks.values());
    }
    sortedChunks.sort(Comparator.comparingLong(chunk -> (long) chunk.getZ() * Integer.MAX_VALUE + (long) chunk.getX()));
    sortedChunks.removeIf(chunk -> chunk == null || Arrays.stream(chunk.getSections()).allMatch(Objects::isNull)); // Remove empty chunks to save space
    // Store world properties
    if (!this.extraData.getValue().containsKey("properties")) {
      this.extraData.getValue().putIfAbsent("properties", this.propertyMap.toCompound());
    } else {
      this.extraData.getValue().replace("properties", this.propertyMap.toCompound());
    }
    final ByteArrayOutputStream outByteStream = new ByteArrayOutputStream();
    final DataOutputStream outStream = new DataOutputStream(outByteStream);
    try {
      // File Header and Slime version
      outStream.write(SlimeFormat.SLIME_HEADER);
      outStream.write(SlimeFormat.SLIME_VERSION);
      // World version
      outStream.writeByte(this.version);
      // Lowest chunk coordinates
      final int minX = sortedChunks.stream().mapToInt(SlimeChunk::getX).min().orElse(0);
      final int minZ = sortedChunks.stream().mapToInt(SlimeChunk::getZ).min().orElse(0);
      final int maxX = sortedChunks.stream().mapToInt(SlimeChunk::getX).max().orElse(0);
      final int maxZ = sortedChunks.stream().mapToInt(SlimeChunk::getZ).max().orElse(0);
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
      CraftSlimeWorld.writeBitSetAsBytes(outStream, chunkBitset, chunkMaskSize);
      // Chunks
      final byte[] chunkData = CraftSlimeWorld.serializeChunks(sortedChunks, this.version);
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
        tileEntitiesNbtList.getName(), new CompoundMap(Collections.singletonList(tileEntitiesNbtList)));
      final byte[] tileEntitiesData = CraftSlimeWorld.serializeCompoundTag(tileEntitiesCompound);
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
          entitiesNbtList.getName(), new CompoundMap(Collections.singletonList(entitiesNbtList)));
        final byte[] entitiesData = CraftSlimeWorld.serializeCompoundTag(entitiesCompound);
        final byte[] compressedEntitiesData = Zstd.compress(entitiesData);
        outStream.writeInt(compressedEntitiesData.length);
        outStream.writeInt(entitiesData.length);
        outStream.write(compressedEntitiesData);
      }
      // Extra Tag
      final byte[] extra = CraftSlimeWorld.serializeCompoundTag(this.extraData);
      final byte[] compressedExtra = Zstd.compress(extra);
      outStream.writeInt(compressedExtra.length);
      outStream.writeInt(extra.length);
      outStream.write(compressedExtra);
      // World Maps
      final CompoundMap map = new CompoundMap();
      map.put("maps", new ListTag<>("maps", TagType.TAG_COMPOUND, this.worldMaps));
      final CompoundTag mapsCompound = new CompoundTag("", map);
      final byte[] mapArray = CraftSlimeWorld.serializeCompoundTag(mapsCompound);
      final byte[] compressedMapArray = Zstd.compress(mapArray);
      outStream.writeInt(compressedMapArray.length);
      outStream.writeInt(mapArray.length);
      outStream.write(compressedMapArray);
    } catch (final IOException ex) { // Ignore
      ex.printStackTrace();
    }
    return outByteStream.toByteArray();
  }

  public void updateChunk(@NotNull final SlimeChunk chunk) {
    if (!chunk.getWorldName().equals(this.getName())) {
      throw new IllegalArgumentException("Chunk (" + chunk.getX() + ", " + chunk.getZ() + ") belongs to world '"
        + chunk.getWorldName() + "', not to '" + this.getName() + "'!");
    }
    synchronized (this.chunks) {
      this.chunks.put((long) chunk.getZ() * Integer.MAX_VALUE + (long) chunk.getX(), chunk);
    }
  }
}
