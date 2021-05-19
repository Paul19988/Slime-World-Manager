package com.grinderwolf.swm.api.world.properties;

import com.flowpowered.nbt.CompoundMap;
import com.flowpowered.nbt.Tag;
import java.util.function.Function;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A property describing behavior of a slime world.
 */
@Getter
public abstract class SlimeProperty<T> {

  /**
   * the default value.
   */
  private final T defaultValue;

  /**
   * the nbt name.
   */
  @NotNull
  private final String nbtName;

  /**
   * the validator.
   */
  @Nullable
  private final Function<T, Boolean> validator;

  /**
   * ctor.
   *
   * @param nbtName the nbt name.
   * @param defaultValue the default value.
   */
  protected SlimeProperty(@NotNull final String nbtName, @NotNull final T defaultValue) {
    this(nbtName, defaultValue, null);
  }

  /**
   * ctor.
   *
   * @param nbtName the nbt name.
   * @param defaultValue the default value.
   * @param validator the validator.
   */
  protected SlimeProperty(@NotNull final String nbtName, @NotNull final T defaultValue,
                          @Nullable final Function<T, Boolean> validator) {
    this.nbtName = nbtName;
    if (validator != null && !validator.apply(defaultValue)) {
      throw new IllegalArgumentException(String.format("Invalid default value for property %s! %s",
        nbtName, defaultValue));
    }
    this.defaultValue = defaultValue;
    this.validator = validator;
  }

  @Override
  public final String toString() {
    return "SlimeProperty{" +
      "nbtName='" + this.nbtName + '\'' +
      ", defaultValue=" + this.defaultValue +
      '}';
  }

  /**
   * reads the compound tag values.
   *
   * @param compoundTag the compound tag to read.
   *
   * @return value.
   */
  @NotNull
  protected abstract T readValue(@NotNull Tag<?> compoundTag);

  /**
   * writes the value into the compound map.
   *
   * @param compoundMap the compound map to write.
   * @param value the value to write.
   */
  protected abstract void writeValue(@NotNull CompoundMap compoundMap, @NotNull T value);
}
