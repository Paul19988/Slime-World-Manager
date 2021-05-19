package com.grinderwolf.swm.api.world.properties.type;

import com.flowpowered.nbt.ByteTag;
import com.flowpowered.nbt.CompoundMap;
import com.flowpowered.nbt.Tag;
import com.grinderwolf.swm.api.world.properties.SlimeProperty;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A slime property of type boolean
 */
public final class SlimePropertyBoolean extends SlimeProperty<Boolean> {

  /**
   * ctor.
   *
   * @param nbtName the nbt name.
   * @param defaultValue the default value.
   */
  public SlimePropertyBoolean(@NotNull final String nbtName, final boolean defaultValue) {
    super(nbtName, defaultValue);
  }

  /**
   * ctor.
   *
   * @param nbtName the nbt name.
   * @param defaultValue the default value.
   * @param validator the validator.
   */
  public SlimePropertyBoolean(@NotNull final String nbtName, final boolean defaultValue,
                              @Nullable final Function<Boolean, Boolean> validator) {
    super(nbtName, defaultValue, validator);
  }

  @NotNull
  @Override
  protected Boolean readValue(@NotNull final Tag<?> compoundTag) {
    return compoundTag.getAsByteTag()
      .map(value -> value.getValue() == 1)
      .orElse(this.getDefaultValue());
  }

  @Override
  protected void writeValue(@NotNull final CompoundMap compoundMap, @NotNull final Boolean value) {
    compoundMap.put(this.getNbtName(), new ByteTag(this.getNbtName(), (byte) (value ? 1 : 0)));
  }
}
