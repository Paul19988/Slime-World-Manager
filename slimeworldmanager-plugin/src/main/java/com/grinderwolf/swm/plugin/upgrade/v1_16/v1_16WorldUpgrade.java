package com.grinderwolf.swm.plugin.upgrade.v1_16;

import com.flowpowered.nbt.CompoundTag;
import com.flowpowered.nbt.LongArrayTag;
import com.flowpowered.nbt.Tag;
import com.grinderwolf.swm.api.world.SlimeChunk;
import com.grinderwolf.swm.api.world.SlimeChunkSection;
import com.grinderwolf.swm.nms.CraftSlimeChunk;
import com.grinderwolf.swm.nms.CraftSlimeChunkSection;
import com.grinderwolf.swm.nms.CraftSlimeWorld;
import com.grinderwolf.swm.plugin.upgrade.Upgrade;
import java.util.ArrayList;
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;

public final class v1_16WorldUpgrade implements Upgrade {

  private static final int[] MULTIPLY_DE_BRUIJN_BIT_POSITION = new int[]{
    0, 1, 28, 2, 29, 14, 24, 3, 30, 22, 20, 15, 25, 17, 4, 8, 31, 27, 13, 23, 21, 19, 16, 7, 26, 12, 18, 6, 11, 5, 10, 9
  };

  // Taken from DataConverterBitStorageAlign.java
  private static long[] addPadding(final int indices, final int bitsPerIndex, final long[] originalArray) {
    final int k = originalArray.length;
    if (k == 0) {
      return originalArray;
    }
    final long l = (1L << bitsPerIndex) - 1L;
    final int i1 = 64 / bitsPerIndex;
    final int j1 = (indices + i1 - 1) / i1;
    final long[] along1 = new long[j1];
    int k1 = 0;
    int l1 = 0;
    long i2 = 0L;
    int j2 = 0;
    long k2 = originalArray[0];
    long l2 = k > 1 ? originalArray[1] : 0L;
    for (int i3 = 0; i3 < indices; ++i3) {
      final int j3 = i3 * bitsPerIndex;
      final int k3 = j3 >> 6;
      final int l3 = (i3 + 1) * bitsPerIndex - 1 >> 6;
      final int i4 = j3 ^ k3 << 6;
      if (k3 != j2) {
        k2 = l2;
        l2 = k3 + 1 < k ? originalArray[k3 + 1] : 0L;
        j2 = k3;
      }
      final long j4;
      int k4;
      if (k3 == l3) {
        j4 = k2 >>> i4 & l;
      } else {
        k4 = 64 - i4;
        j4 = (k2 >>> i4 | l2 << k4) & l;
      }
      k4 = l1 + bitsPerIndex;
      if (k4 >= 64) {
        along1[k1++] = i2;
        i2 = j4;
        l1 = bitsPerIndex;
      } else {
        i2 |= j4 << l1;
        l1 = k4;
      }
    }
    if (i2 != 0L) {
      along1[k1] = i2;
    }
    return along1;
  }

  private static int ceillog2(int input) {
    input = v1_16WorldUpgrade.isPowerOfTwo(input) ? input : v1_16WorldUpgrade.smallestEncompassingPowerOfTwo(input);
    return v1_16WorldUpgrade.MULTIPLY_DE_BRUIJN_BIT_POSITION[(int) ((long) input * 125613361L >> 27) & 31];
  }

  private static boolean isPowerOfTwo(final int input) {
    return input != 0 && (input & input - 1) == 0;
  }

  private static int smallestEncompassingPowerOfTwo(final int input) {
    int result = input - 1;
    result |= result >> 1;
    result |= result >> 2;
    result |= result >> 4;
    result |= result >> 8;
    result |= result >> 16;
    return result + 1;
  }

  @Override
  public void downgrade(@NotNull final CraftSlimeWorld world) {
    for (final SlimeChunk chunk : world.getChunks().values()) {
      // Remove padding from height maps and block states
      // TODO
      for (int sectionIndex = 0; sectionIndex < chunk.getSections().length; sectionIndex++) {
        final SlimeChunkSection section = chunk.getSections()[sectionIndex];
        if (section != null) {
        }
      }
      // Remove extra data from biomes
      // TODO
    }
  }

  @Override
  public void upgrade(@NotNull final CraftSlimeWorld world) {
    for (final SlimeChunk chunk : new ArrayList<>(world.getChunks().values())) {
      // Add padding to height maps and block states
      final CompoundTag heightMaps = chunk.getHeightMaps();
      for (final Tag<?> map : heightMaps.getValue().values()) {
        if (map instanceof LongArrayTag) {
          final LongArrayTag arrayTag = (LongArrayTag) map;
          arrayTag.setValue(v1_16WorldUpgrade.addPadding(256, 9, arrayTag.getValue()));
        }
      }
      for (int sectionIndex = 0; sectionIndex < chunk.getSections().length; sectionIndex++) {
        SlimeChunkSection section = chunk.getSections()[sectionIndex];
        if (section != null) {
          final int bitsPerBlock = Math.max(4, v1_16WorldUpgrade.ceillog2(section.getPalette().getValue().size()));
          if (!v1_16WorldUpgrade.isPowerOfTwo(bitsPerBlock)) {
            section = new CraftSlimeChunkSection(section.getBlockLight(),
              v1_16WorldUpgrade.addPadding(4096, bitsPerBlock, section.getBlockStates()), null, null,
              section.getEntityStates(), section.getPalette(), section.getSkyLight());
            chunk.getSections()[sectionIndex] = section;
          }
        }
      }
      // Update biome array size
      final int[] newBiomes = new int[1024];
      Arrays.fill(newBiomes, -1);
      final int[] biomes = chunk.getBiomes();
      System.arraycopy(biomes, 0, newBiomes, 0, biomes.length);
      world.updateChunk(new CraftSlimeChunk(newBiomes, chunk.getEntities(), chunk.getHeightMaps(), chunk.getSections(),
        chunk.getTileEntities(), chunk.getWorldName(), chunk.getX(), chunk.getZ(),
        ((CraftSlimeChunk) chunk).getUpgradeData()));
    }
  }
}
