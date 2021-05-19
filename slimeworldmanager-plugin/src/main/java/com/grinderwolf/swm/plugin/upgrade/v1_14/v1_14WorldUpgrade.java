package com.grinderwolf.swm.plugin.upgrade.v1_14;

import com.flowpowered.nbt.ByteTag;
import com.flowpowered.nbt.CompoundMap;
import com.flowpowered.nbt.CompoundTag;
import com.flowpowered.nbt.IntTag;
import com.flowpowered.nbt.StringTag;
import com.grinderwolf.swm.api.world.SlimeChunk;
import com.grinderwolf.swm.api.world.SlimeChunkSection;
import com.grinderwolf.swm.nms.CraftSlimeWorld;
import com.grinderwolf.swm.plugin.upgrade.Upgrade;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

public final class v1_14WorldUpgrade implements Upgrade {

  private static final int[] VILLAGER_XP = {0, 10, 50, 100, 150};

  private static final Map<String, String> newToOldMap = new HashMap<>();

  private static final Map<String, String> oldToNewMap = new HashMap<>();

  static {
    v1_14WorldUpgrade.rename("minecraft:tube_coral_fan", "minecraft:tube_coral_wall_fan");
    v1_14WorldUpgrade.rename("minecraft:brain_coral_fan", "minecraft:brain_coral_wall_fan");
    v1_14WorldUpgrade.rename("minecraft:bubble_coral_fan", "minecraft:bubble_coral_wall_fan");
    v1_14WorldUpgrade.rename("minecraft:fire_coral_fan", "minecraft:fire_coral_wall_fan");
    v1_14WorldUpgrade.rename("minecraft:horn_coral_fan", "minecraft:horn_coral_wall_fan");
    v1_14WorldUpgrade.rename("minecraft:stone_slab", "minecraft:smooth_stone_slab");
    v1_14WorldUpgrade.rename("minecraft:sign", "minecraft:oak_sign");
    v1_14WorldUpgrade.rename("minecraft:wall_sign", "minecraft:oak_wall_sign");
  }

  private static int clamp(final int i, final int i1, final int i2) {
    return i < i1 ? i1 : Math.min(i, i2);
  }

  private static int[] getVillagerProfession(final String profession) {
    switch (profession) {
      case "minecraft:farmer":
        return new int[]{0, 1};
      case "minecraft:fisherman":
        return new int[]{0, 2};
      case "minecraft:shepherd":
        return new int[]{0, 3};
      case "minecraft:fletcher":
        return new int[]{0, 4};
      case "minecraft:librarian":
        return new int[]{1, 1};
      case "minecraft:cartographer":
        return new int[]{1, 2};
      case "minecraft:cleric":
        return new int[]{2, 1};
      case "minecraft:armorer":
        return new int[]{3, 1};
      case "minecraft:weaponsmith":
        return new int[]{3, 2};
      case "minecraft:toolsmith":
        return new int[]{3, 3};
      case "minecraft:butcher":
        return new int[]{4, 1};
      case "minecraft:leatherworker":
        return new int[]{4, 2};
      case "minecraft:nitwit":
        return new int[]{5, 1};
      default:
        return new int[]{0, 0};
    }
  }

  @NotNull
  private static String getVillagerProfession(final int profession, final int career) {
    if (profession == 0) {
      if (career == 2) {
        return "minecraft:fisherman";
      }
      if (career == 3) {
        return "minecraft:shepherd";
      }
      if (career == 4) {
        return "minecraft:fletcher";
      }
      return "minecraft:farmer";
    }
    if (profession == 1) {
      return career == 2 ? "minecraft:cartographer" : "minecraft:librarian";
    }
    if (profession == 2) {
      return "minecraft:cleric";
    }
    if (profession == 3) {
      if (career == 2) {
        return "minecraft:weaponsmith";
      }
      if (career == 3) {
        return "minecraft:toolsmith";
      }
      return "minecraft:armorer";
    }
    if (profession == 4) {
      if (career == 2) {
        return "minecraft:leatherworker";
      }
      return "minecraft:butcher";
    }
    if (profession == 5) {
      return "minecraft:nitwit";
    }
    return "minecraft:none";
  }

  private static void rename(@NotNull final String oldName, @NotNull final String newName) {
    v1_14WorldUpgrade.oldToNewMap.put(oldName, newName);
    v1_14WorldUpgrade.newToOldMap.put(newName, oldName);
  }

  private static void updateBlockEntities(@NotNull final SlimeChunk chunk, final int sectionIndex,
                                          final int paletteIndex, @NotNull final String oldName,
                                          @NotNull final String newName) {
    final SlimeChunkSection section = chunk.getSections()[sectionIndex];
    if (section == null) {
      return;
    }
    final long[] blockData = section.getBlockStates();
    final int bitsPerBlock = Math.max(4, blockData.length * 64 / 4096);
    final long maxEntryValue = (1L << bitsPerBlock) - 1;
    for (int y = 0; y < 16; y++) {
      for (int z = 0; z < 16; z++) {
        for (int x = 0; x < 16; x++) {
          final int arrayIndex = y << 8 | z << 4 | x;
          final int bitIndex = arrayIndex * bitsPerBlock;
          final int startIndex = bitIndex / 64;
          final int endIndex = ((arrayIndex + 1) * bitsPerBlock - 1) / 64;
          final int startBitSubIndex = bitIndex % 64;
          final int val;
          if (startIndex == endIndex) {
            val = (int) (blockData[startIndex] >>> startBitSubIndex & maxEntryValue);
          } else {
            final int endBitSubIndex = 64 - startBitSubIndex;
            val = (int) ((blockData[startIndex] >>> startBitSubIndex | blockData[endIndex] << endBitSubIndex) & maxEntryValue);
          }
          // It's the right block type
          if (val != paletteIndex) {
            continue;
          }
          final int blockX = x + chunk.getX() * 16;
          final int blockY = y + sectionIndex * 16;
          final int blockZ = z + chunk.getZ() * 16;
          for (final CompoundTag tileEntityTag : chunk.getTileEntities()) {
            final int tileX = tileEntityTag.getIntValue("x").get();
            final int tileY = tileEntityTag.getIntValue("y").get();
            final int tileZ = tileEntityTag.getIntValue("z").get();
            if (tileX == blockX && tileY == blockY && tileZ == blockZ) {
              final String type = tileEntityTag.getStringValue("id").get();
              if (!type.equals(oldName)) {
                throw new IllegalStateException("Expected block entity to be " + oldName + ", not " + type);
              }
              tileEntityTag.getValue().put("id", new StringTag("id", newName));
              break;
            }
          }
        }
      }
    }
  }

  @Override
  public void downgrade(@NotNull final CraftSlimeWorld world) {
    for (final SlimeChunk chunk : new ArrayList<>(world.getChunks().values())) {
      // Update renamed blocks
      for (int sectionIndex = 0; sectionIndex < chunk.getSections().length; sectionIndex++) {
        final SlimeChunkSection section = chunk.getSections()[sectionIndex];
        if (section != null) {
          final List<CompoundTag> palette = section.getPalette().getValue();
          for (int paletteIndex = 0; paletteIndex < palette.size(); paletteIndex++) {
            final CompoundTag blockTag = palette.get(paletteIndex);
            final String name = blockTag.getStringValue("Name").get();
            // The trapped chest tile entity type didn't exist until 1.13
            if (name.equals("minecraft:trapped_chest")) {
              v1_14WorldUpgrade.updateBlockEntities(chunk, sectionIndex, paletteIndex, "minecraft:trapped_chest", "minecraft:chest");
            }
            final String newName = v1_14WorldUpgrade.newToOldMap.get(name);
            if (newName != null) {
              blockTag.getValue().put("Name", new StringTag("Name", newName));
            }
          }
        }
      }
      chunk.getEntities();
      for (final CompoundTag entityTag : chunk.getEntities()) {
        final String type = entityTag.getStringValue("id").get();
        switch (type) {
          case "minecraft:cat":
            // Cats are ocelots
            entityTag.getValue().put("id", new StringTag("id", "minecraft:ocelot"));
            break;
          case "minecraft:villager":
          case "minecraft:zombie_villager":
            // Villager data has changed
            final CompoundTag dataTag = entityTag.getAsCompoundTag("VillagerData").get();
            final String profession = dataTag.getStringValue("profession").get();
            final int[] professionData = v1_14WorldUpgrade.getVillagerProfession(profession);
            entityTag.getValue().remove("VillagerData");
            entityTag.getValue().put("Profession", new IntTag("Profession", professionData[0]));
            entityTag.getValue().put("Career", new IntTag("Career", professionData[1]));
            entityTag.getValue().put("CareerLevel", new IntTag("Career", 1));
            break;
          case "minecraft:banner":
            // The illager banners changed the translation message
            final Optional<String> customName = entityTag.getStringValue("CustomName");
            if (customName.isPresent()) {
              final String newName = customName.get().replace("\"translate\":\"block.minecraft.ominous_banner\"",
                "\"translate\":\"block.minecraft.illager_banner\"");
              entityTag.getValue().put("CustomName", new StringTag("CustomName", newName));
            }
            break;
        }
      }
    }
  }

  @Override
  public void upgrade(@NotNull final CraftSlimeWorld world) {
    for (final SlimeChunk chunk : new ArrayList<>(world.getChunks().values())) {
      // Update renamed blocks
      for (int sectionIndex = 0; sectionIndex < chunk.getSections().length; sectionIndex++) {
        final SlimeChunkSection section = chunk.getSections()[sectionIndex];
        if (section != null) {
          final List<CompoundTag> palette = section.getPalette().getValue();
          for (int paletteIndex = 0; paletteIndex < palette.size(); paletteIndex++) {
            final CompoundTag blockTag = palette.get(paletteIndex);
            final String name = blockTag.getStringValue("Name").get();
            // Trapped chests have now a different tile entity,
            // so we have to update every block entity type
            if (name.equals("minecraft:trapped_chest")) {
              v1_14WorldUpgrade.updateBlockEntities(chunk, sectionIndex, paletteIndex, "minecraft:chest", "minecraft:trapped_chest");
            }
            final String newName = v1_14WorldUpgrade.oldToNewMap.get(name);
            if (newName != null) {
              blockTag.getValue().put("Name", new StringTag("Name", newName));
            }
          }
        }
      }
      for (final CompoundTag entityTag : chunk.getEntities()) {
        final String type = entityTag.getStringValue("id").get();
        switch (type) {
          case "minecraft:ocelot":
            // Cats are no longer ocelots
            final int catType = entityTag.getIntValue("CatType").orElse(0);
            if (catType == 0) {
              final Optional<String> owner = entityTag.getStringValue("Owner");
              final Optional<String> ownerId = entityTag.getStringValue("OwnerUUID");
              if (owner.isPresent() || ownerId.isPresent()) {
                entityTag.getValue().put("Trusting", new ByteTag("Trusting", (byte) 1));
              }
              entityTag.getValue().remove("CatType");
            } else if (catType > 0 && catType < 4) {
              entityTag.getValue().put("id", new StringTag("id", "minecraft:cat"));
            }
            break;
          case "minecraft:villager":
          case "minecraft:zombie_villager":
            // Villager data has changed
            final int profession = entityTag.getIntValue("Profession").orElse(0);
            final int career = entityTag.getIntValue("Career").orElse(0);
            int careerLevel = entityTag.getIntValue("CareerLevel").orElse(1);
            // Villager level and xp has to be rebuilt
            final Optional<CompoundTag> offersOpt = entityTag.getAsCompoundTag("Offers");
            if (offersOpt.isPresent()) {
              if (careerLevel == 0 || careerLevel == 1) {
                final int amount = offersOpt.flatMap(offers -> offers.getAsCompoundTag("Recipes")).map(recipes -> recipes.getValue().size()).orElse(0);
                careerLevel = v1_14WorldUpgrade.clamp(amount / 2, 1, 5);
              }
            }
            final Optional<CompoundTag> xp = entityTag.getAsCompoundTag("Xp");
            if (!xp.isPresent()) {
              entityTag.getValue().put("Xp", new IntTag("Xp", v1_14WorldUpgrade.VILLAGER_XP[v1_14WorldUpgrade.clamp(careerLevel - 1, 0, v1_14WorldUpgrade.VILLAGER_XP.length - 1)]));
            }
            entityTag.getValue().remove("Profession");
            entityTag.getValue().remove("Career");
            entityTag.getValue().remove("CareerLevel");
            final CompoundMap dataMap = new CompoundMap();
            dataMap.put("type", new StringTag("type", "minecraft:plains"));
            dataMap.put("profession", new StringTag("profession", v1_14WorldUpgrade.getVillagerProfession(profession, career)));
            dataMap.put("level", new IntTag("level", careerLevel));
            entityTag.getValue().put("VillagerData", new CompoundTag("VillagerData", dataMap));
            break;
          case "minecraft:banner":
            // The illager banners changed the translation message
            final Optional<String> customName = entityTag.getStringValue("CustomName");
            if (customName.isPresent()) {
              final String newName = customName.get().replace("\"translate\":\"block.minecraft.illager_banner\"",
                "\"translate\":\"block.minecraft.ominous_banner\"");
              entityTag.getValue().put("CustomName", new StringTag("CustomName", newName));
            }
            break;
        }
      }
    }
  }
}
