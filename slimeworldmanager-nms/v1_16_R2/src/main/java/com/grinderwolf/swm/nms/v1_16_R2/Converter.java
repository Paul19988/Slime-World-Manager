package com.grinderwolf.swm.nms.v1_16_R2;

import com.flowpowered.nbt.ByteArrayTag;
import com.flowpowered.nbt.ByteTag;
import com.flowpowered.nbt.CompoundMap;
import com.flowpowered.nbt.CompoundTag;
import com.flowpowered.nbt.DoubleTag;
import com.flowpowered.nbt.FloatTag;
import com.flowpowered.nbt.IntArrayTag;
import com.flowpowered.nbt.IntTag;
import com.flowpowered.nbt.ListTag;
import com.flowpowered.nbt.LongArrayTag;
import com.flowpowered.nbt.LongTag;
import com.flowpowered.nbt.ShortTag;
import com.flowpowered.nbt.StringTag;
import com.flowpowered.nbt.Tag;
import com.flowpowered.nbt.TagType;
import com.grinderwolf.swm.api.utils.NibbleArray;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.v1_16_R2.NBTBase;
import net.minecraft.server.v1_16_R2.NBTTagByte;
import net.minecraft.server.v1_16_R2.NBTTagByteArray;
import net.minecraft.server.v1_16_R2.NBTTagCompound;
import net.minecraft.server.v1_16_R2.NBTTagDouble;
import net.minecraft.server.v1_16_R2.NBTTagFloat;
import net.minecraft.server.v1_16_R2.NBTTagInt;
import net.minecraft.server.v1_16_R2.NBTTagIntArray;
import net.minecraft.server.v1_16_R2.NBTTagList;
import net.minecraft.server.v1_16_R2.NBTTagLong;
import net.minecraft.server.v1_16_R2.NBTTagLongArray;
import net.minecraft.server.v1_16_R2.NBTTagShort;
import net.minecraft.server.v1_16_R2.NBTTagString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public final class Converter {

  private static final Logger LOGGER = LogManager.getLogger("SWM Converter");

  @NotNull
  static net.minecraft.server.v1_16_R2.NibbleArray convertArray(@NotNull final NibbleArray array) {
    return new net.minecraft.server.v1_16_R2.NibbleArray(array.getBacking());
  }

  @NotNull
  static NibbleArray convertArray(@NotNull final net.minecraft.server.v1_16_R2.NibbleArray array) {
    return new NibbleArray(array.asBytes());
  }

  @NotNull
  static NBTBase convertTag(@NotNull final Tag<?> tag) {
    try {
      switch (tag.getType()) {
        case TAG_BYTE:
          return NBTTagByte.a(((ByteTag) tag).getValue());
        case TAG_SHORT:
          return NBTTagShort.a(((ShortTag) tag).getValue());
        case TAG_INT:
          return NBTTagInt.a(((IntTag) tag).getValue());
        case TAG_LONG:
          return NBTTagLong.a(((LongTag) tag).getValue());
        case TAG_FLOAT:
          return NBTTagFloat.a(((FloatTag) tag).getValue());
        case TAG_DOUBLE:
          return NBTTagDouble.a(((DoubleTag) tag).getValue());
        case TAG_BYTE_ARRAY:
          return new NBTTagByteArray(((ByteArrayTag) tag).getValue());
        case TAG_STRING:
          return NBTTagString.a(((StringTag) tag).getValue());
        case TAG_LIST:
          final NBTTagList list = new NBTTagList();
          ((ListTag<?>) tag).getValue().stream().map(Converter::convertTag).forEach(list::add);
          return list;
        case TAG_COMPOUND:
          final NBTTagCompound compound = new NBTTagCompound();
          ((CompoundTag) tag).getValue().forEach((key, value) -> compound.set(key, Converter.convertTag(value)));
          return compound;
        case TAG_INT_ARRAY:
          return new NBTTagIntArray(((IntArrayTag) tag).getValue());
        case TAG_LONG_ARRAY:
          return new NBTTagLongArray(((LongArrayTag) tag).getValue());
        default:
          throw new IllegalArgumentException("Invalid tag type " + tag.getType().name());
      }
    } catch (final Exception ex) {
      Converter.LOGGER.error("Failed to convert NBT object:");
      Converter.LOGGER.error(tag.toString());
      throw ex;
    }
  }

  @NotNull
  static Tag<?> convertTag(@NotNull final String name, @NotNull final NBTBase base) {
    switch (base.getTypeId()) {
      case 1:
        return new ByteTag(name, ((NBTTagByte) base).asByte());
      case 2:
        return new ShortTag(name, ((NBTTagShort) base).asShort());
      case 3:
        return new IntTag(name, ((NBTTagInt) base).asInt());
      case 4:
        return new LongTag(name, ((NBTTagLong) base).asLong());
      case 5:
        return new FloatTag(name, ((NBTTagFloat) base).asFloat());
      case 6:
        return new DoubleTag(name, ((NBTTagDouble) base).asDouble());
      case 7:
        return new ByteArrayTag(name, ((NBTTagByteArray) base).getBytes());
      case 8:
        return new StringTag(name, base.asString());
      case 9:
        final List<Tag<?>> list = new ArrayList<>();
        final NBTTagList originalList = (NBTTagList) base;
        for (final NBTBase entry : originalList) {
          list.add(Converter.convertTag("", entry));
        }
        return new ListTag<>(name, TagType.getById(originalList.d_()), list);
      case 10:
        final NBTTagCompound originalCompound = (NBTTagCompound) base;
        final CompoundTag compound = new CompoundTag(name, new CompoundMap());
        for (final String key : originalCompound.getKeys()) {
          final NBTBase nbtBase = originalCompound.get(key);
          if (nbtBase == null) {
            continue;
          }
          compound.getValue().put(key, Converter.convertTag(key, nbtBase));
        }
        return compound;
      case 11:
        return new IntArrayTag("", ((NBTTagIntArray) base).getInts());
      case 12:
        return new LongArrayTag("", ((NBTTagLongArray) base).getLongs());
      default:
        throw new IllegalArgumentException("Invalid tag type " + base.getTypeId());
    }
  }
}
