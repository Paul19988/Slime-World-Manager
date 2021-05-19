package com.grinderwolf.swm.nms;

import com.flowpowered.nbt.CompoundTag;
import com.flowpowered.nbt.ListTag;
import com.grinderwolf.swm.api.utils.NibbleArray;
import com.grinderwolf.swm.api.world.SlimeChunkSection;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;

@Getter
@RequiredArgsConstructor
public final class CraftSlimeChunkSection implements SlimeChunkSection {

  @Nullable
  private final NibbleArray blockLight;

  private final long[] blockStates;

  // Pre 1.13 block data
  private final byte[] blocks;

  private final NibbleArray data;

  private final long[] entityStates;

  // Post 1.13 block data
  private final ListTag<CompoundTag> palette;

  @Nullable
  private final NibbleArray skyLight;
}
