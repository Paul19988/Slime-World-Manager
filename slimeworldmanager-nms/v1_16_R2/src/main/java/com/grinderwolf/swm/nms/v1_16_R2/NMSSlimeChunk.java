package com.grinderwolf.swm.nms.v1_16_R2;

import com.flowpowered.nbt.CompoundMap;
import com.flowpowered.nbt.CompoundTag;
import com.flowpowered.nbt.ListTag;
import com.flowpowered.nbt.LongArrayTag;
import com.grinderwolf.swm.api.utils.NibbleArray;
import com.grinderwolf.swm.api.world.SlimeChunk;
import com.grinderwolf.swm.api.world.SlimeChunkSection;
import com.grinderwolf.swm.nms.CraftSlimeChunkSection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import net.minecraft.server.v1_16_R2.Chunk;
import net.minecraft.server.v1_16_R2.ChunkSection;
import net.minecraft.server.v1_16_R2.DataPaletteBlock;
import net.minecraft.server.v1_16_R2.EnumSkyBlock;
import net.minecraft.server.v1_16_R2.LightEngine;
import net.minecraft.server.v1_16_R2.NBTTagCompound;
import net.minecraft.server.v1_16_R2.NBTTagList;
import net.minecraft.server.v1_16_R2.SectionPosition;
import net.minecraft.server.v1_16_R2.TileEntity;
import net.minecraft.server.v1_16_R2.WorldDataServer;
import org.jetbrains.annotations.NotNull;

@Data
@AllArgsConstructor
public final class NMSSlimeChunk implements SlimeChunk {

  private Chunk chunk;

  @Override
  public int[] getBiomes() {
    return this.chunk.getBiomeIndex().a();
  }

  @NotNull
  @Override
  public List<CompoundTag> getEntities() {
    final List<CompoundTag> entities = new ArrayList<>();
    Arrays.stream(this.chunk.getEntitySlices())
      .flatMap(Collection::stream)
      .forEach(entity -> {
        final NBTTagCompound entityNbt = new NBTTagCompound();
        if (entity.d(entityNbt)) {
          this.chunk.d(true);
          entities.add((CompoundTag) Converter.convertTag("", entityNbt));
        }
      });
    return entities;
  }

  @NotNull
  @Override
  public CompoundTag getHeightMaps() {
    // HeightMap
    final CompoundMap heightMaps = new CompoundMap();
    this.chunk.heightMap.forEach((type, map) ->
      heightMaps.put(type.getName(), new LongArrayTag(type.getName(), map.a())));
    return new CompoundTag("", heightMaps);
  }

  @Override
  public SlimeChunkSection[] getSections() {
    final SlimeChunkSection[] sections = new SlimeChunkSection[16];
    final LightEngine lightEngine = this.chunk.world.getChunkProvider().getLightEngine();
    for (int sectionId = 0; sectionId < this.chunk.getSections().length; sectionId++) {
      final ChunkSection section = this.chunk.getSections()[sectionId];
      if (section != null) {
        section.recalcBlockCounts();
        if (!section.c()) { // If the section is empty, just ignore it to save space
          // Block Light Nibble Array
          final NibbleArray blockLightArray = Converter.convertArray(
            lightEngine.a(EnumSkyBlock.BLOCK).a(SectionPosition.a(this.chunk.getPos(), sectionId)));
          // Sky light Nibble Array
          final NibbleArray skyLightArray = Converter.convertArray(
            lightEngine.a(EnumSkyBlock.SKY).a(SectionPosition.a(this.chunk.getPos(), sectionId)));
          // Block Data
          final DataPaletteBlock<?> dataPaletteBlock = section.getBlocks();
          final NBTTagCompound blocksCompound = new NBTTagCompound();
          dataPaletteBlock.a(blocksCompound, "Palette", "BlockStates");
          final NBTTagList paletteList = blocksCompound.getList("Palette", 10);
          final ListTag<CompoundTag> palette = (ListTag<CompoundTag>) Converter.convertTag("", paletteList);
          final long[] blockStates = blocksCompound.getLongArray("BlockStates");
          sections[sectionId] = new CraftSlimeChunkSection(
            blockLightArray, blockStates, null, null, null, palette, skyLightArray);
        }
      }
    }
    return sections;
  }

  @NotNull
  @Override
  public List<CompoundTag> getTileEntities() {
    final List<CompoundTag> tileEntities = new ArrayList<>();
    for (final TileEntity entity : this.chunk.getTileEntities().values()) {
      final NBTTagCompound entityNbt = new NBTTagCompound();
      entity.save(entityNbt);
      tileEntities.add((CompoundTag) Converter.convertTag("", entityNbt));
    }
    return tileEntities;
  }

  @NotNull
  @Override
  public String getWorldName() {
    return ((WorldDataServer) this.chunk.getWorld().worldData).getName();
  }

  @Override
  public int getX() {
    return this.chunk.getPos().x;
  }

  @Override
  public int getZ() {
    return this.chunk.getPos().z;
  }
}
