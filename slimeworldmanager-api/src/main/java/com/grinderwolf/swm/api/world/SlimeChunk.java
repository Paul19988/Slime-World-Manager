package com.grinderwolf.swm.api.world;

import com.flowpowered.nbt.CompoundTag;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * In-memory representation of a SRF chunk.
 */
public interface SlimeChunk {

  /**
   * Returns all the biomes of the chunk. In case it's a pre 1.13 world,
   * every {@code int} inside the array will contain two biomes,
   * and should be converted into a {@code byte[]}.
   *
   * @return A {@code int[]} containing all the biomes of the chunk.
   */
  int[] getBiomes();

  /**
   * Returns all the entities of the chunk.
   *
   * @return A {@link CompoundTag} containing all the entities of the chunk.
   */
  @NotNull
  List<CompoundTag> getEntities();

  /**
   * Returns the height maps of the chunk. If it's a pre 1.13 world,
   * a {@link com.flowpowered.nbt.IntArrayTag} containing the height
   * map will be stored inside here by the name of 'heightMap'.
   *
   * @return A {@link CompoundTag} containing all the height maps of the chunk.
   */
  @NotNull
  CompoundTag getHeightMaps();

  /**
   * Returns all the sections of the chunk.
   *
   * @return A {@link SlimeChunkSection} array.
   */
  @Nullable
  SlimeChunkSection[] getSections();

  /**
   * Returns all the tile entities of the chunk.
   *
   * @return A {@link CompoundTag} containing all the tile entities of the chunk.
   */
  @NotNull
  List<CompoundTag> getTileEntities();

  /**
   * Returns the name of the world this chunk belongs to.
   *
   * @return The name of the world of this chunk.
   */
  @NotNull
  String getWorldName();

  /**
   * Returns the X coordinate of the chunk.
   *
   * @return X coordinate of the chunk.
   */
  int getX();

  /**
   * Returns the Z coordinate of the chunk.
   *
   * @return Z coordinate of the chunk.
   */
  int getZ();
}
