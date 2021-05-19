package com.grinderwolf.swm.nms;

import com.flowpowered.nbt.CompoundTag;
import com.grinderwolf.swm.api.world.SlimeChunk;
import com.grinderwolf.swm.api.world.SlimeChunkSection;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@AllArgsConstructor
public final class CraftSlimeChunk implements SlimeChunk {

  private final int[] biomes;

  private final List<CompoundTag> entities;

  private final CompoundTag heightMaps;

  private final SlimeChunkSection[] sections;

  private final List<CompoundTag> tileEntities;

  private final String worldName;

  private final int x;

  private final int z;

  // Optional data for 1.13 world upgrading
  private CompoundTag upgradeData;
}
