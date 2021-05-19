package com.grinderwolf.swm.api.world.properties.type;

import com.flowpowered.nbt.CompoundMap;
import com.flowpowered.nbt.StringTag;
import com.flowpowered.nbt.Tag;
import com.grinderwolf.swm.api.world.properties.SlimeProperty;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A slime property of type integer
 */
public final class SlimePropertyString extends SlimeProperty<String> {

  /**
   * ctor.
   *
   * @param nbtName the nbt name.
   * @param defaultValue the default value.
   */
  public SlimePropertyString(@NotNull final String nbtName, @NotNull final String defaultValue) {
    super(nbtName, defaultValue);
  }

  /**
   * ctor.
   *
   * @param nbtName the nbt name.
   * @param defaultValue the default value.
   * @param validator the validator.
   */
  public SlimePropertyString(@NotNull final String nbtName, @NotNull final String defaultValue,
                             @Nullable final Function<String, Boolean> validator) {
    super(nbtName, defaultValue, validator);
  }

  @NotNull
  @Override
  protected String readValue(@NotNull final Tag<?> compoundTag) {
    return compoundTag.getAsStringTag()
      .map(Tag::getValue)
      .orElse(this.getDefaultValue());
  }

  @Override
  protected void writeValue(@NotNull final CompoundMap compoundMap, @NotNull final String value) {
    compoundMap.put(this.getNbtName(), new StringTag(this.getNbtName(), value));
  }
}
