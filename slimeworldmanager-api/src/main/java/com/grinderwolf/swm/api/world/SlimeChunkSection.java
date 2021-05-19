package com.grinderwolf.swm.api.world;

import com.flowpowered.nbt.CompoundTag;
import com.flowpowered.nbt.ListTag;
import com.grinderwolf.swm.api.utils.NibbleArray;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * In-memory representation of a SRF chunk section.
 */
public interface SlimeChunkSection {

  /**
   * Returns the block light data.
   *
   * @return A {@link NibbleArray} with the block light data.
   */
  @Nullable
  NibbleArray getBlockLight();

  /**
   * Returns all the states of the blocks of the chunk section, or
   * {@code null} in case it's a pre 1.13 world.
   *
   * @return A {@code long[]} with every block state.
   */
  long[] getBlockStates();

  /**
   * Returns all the blocks of the chunk section, or {@code null}
   * in case it's a post 1.13 world.
   *
   * @return A {@code byte[]} with all the blocks of a chunk section.
   */
  byte[] getBlocks();

  /**
   * Returns the data of all the blocks of the chunk section, or
   * {@code null} if it's a post 1.13 world.
   *
   * @return A {@link NibbleArray} containing all the blocks of a chunk section.
   */
  @NotNull
  NibbleArray getData();

  /**
   * Returns all the states of the entities of the chunk section, or
   * {@code null} in case it's a pre 1.13 world.
   *
   * @return A {@code long[]} with every block state.
   */
  long[] getEntityStates();

  /**
   * Returns the block palette of the chunk section, or
   * {@code null} if it's a pre 1.13 world.
   *
   * @return The block palette, contained inside a {@link ListTag}
   */
  @NotNull
  ListTag<CompoundTag> getPalette();

  /**
   * Returns the sky light data.
   *
   * @return A {@link NibbleArray} containing the sky light data.
   */
  @Nullable
  NibbleArray getSkyLight();
}
