package com.grinderwolf.swm.api.world.properties.type;

import com.flowpowered.nbt.CompoundMap;
import com.flowpowered.nbt.IntTag;
import com.flowpowered.nbt.Tag;
import com.grinderwolf.swm.api.world.properties.SlimeProperty;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A slime property of type integer
 */
public final class SlimePropertyInt extends SlimeProperty<Integer> {

  /**
   * ctor.
   *
   * @param nbtName the nbt name.
   * @param defaultValue the default value.
   */
  public SlimePropertyInt(@NotNull final String nbtName, final int defaultValue) {
    super(nbtName, defaultValue);
  }

  /**
   * ctor.
   *
   * @param nbtName the nbt name.
   * @param defaultValue the default value.
   * @param validator the validator.
   */
  public SlimePropertyInt(@NotNull final String nbtName, final int defaultValue,
                          @Nullable final Function<Integer, Boolean> validator) {
    super(nbtName, defaultValue, validator);
  }

  @NotNull
  @Override
  protected Integer readValue(@NotNull final Tag<?> compoundTag) {
    return compoundTag.getAsIntTag()
      .map(Tag::getValue)
      .orElse(this.getDefaultValue());
  }

  @Override
  protected void writeValue(@NotNull final CompoundMap compoundMap, @NotNull final Integer value) {
    compoundMap.put(this.getNbtName(), new IntTag(this.getNbtName(), value));
  }
}
