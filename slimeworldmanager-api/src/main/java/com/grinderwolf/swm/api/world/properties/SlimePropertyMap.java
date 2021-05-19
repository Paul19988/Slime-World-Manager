package com.grinderwolf.swm.api.world.properties;

import com.flowpowered.nbt.CompoundMap;
import com.flowpowered.nbt.CompoundTag;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * A Property Map object.
 */
@RequiredArgsConstructor
public final class SlimePropertyMap {

  /**
   * the properties.
   */
  @Getter(value = AccessLevel.PRIVATE)
  private final CompoundMap properties;

  /**
   * ctor.
   */
  public SlimePropertyMap() {
    this(new CompoundMap());
  }

  /**
   * Return the current value of the given property
   *
   * @param property The slime property
   *
   * @return The current value
   */
  @NotNull
  public <T> T getValue(@NotNull final SlimeProperty<T> property) {
    if (this.properties.containsKey(property.getNbtName())) {
      return property.readValue(this.properties.get(property.getNbtName()));
    }
    return property.getDefaultValue();
  }

  /**
   * Copies all values from the specified {@code this}.
   * If the same property has different values on both maps, the one
   * on the providen map will be used.
   *
   * @param propertyMap A {@code this}.
   */
  public void merge(@NotNull final SlimePropertyMap propertyMap) {
    this.properties.putAll(propertyMap.properties);
  }

  /**
   * @deprecated Use setValue()
   */
  @Deprecated
  public void setBoolean(@NotNull final SlimeProperty<Boolean> property, final boolean value) {
    this.setValue(property, value);
  }

  /**
   * @deprecated Use setValue()
   */
  @Deprecated
  public void setInt(@NotNull final SlimeProperty<Integer> property, final int value) {
    this.setValue(property, value);
  }

  /**
   * @deprecated Use setValue()
   */
  @Deprecated
  public void setString(@NotNull final SlimeProperty<String> property, @NotNull final String value) {
    this.setValue(property, value);
  }

  /**
   * Update the value of the given property
   *
   * @param property The slime property
   * @param value The new value
   *
   * @throws IllegalArgumentException if the value fails validation.
   */
  public <T> void setValue(@NotNull final SlimeProperty<T> property, @NotNull final T value) {
    if (property.getValidator() != null && !property.getValidator().apply(value)) {
      throw new IllegalArgumentException(String.format("'%s' is not a valid property value.", value));
    }
    property.writeValue(this.properties, value);
  }

  /**
   * Returns a {@link CompoundTag} containing every property set in this map.
   *
   * @return A {@link CompoundTag} with all the properties stored in this map.
   */
  @NotNull
  public CompoundTag toCompound() {
    return new CompoundTag("properties", this.properties);
  }

  @NotNull
  @Override
  public String toString() {
    return "SlimePropertyMap" + this.properties;
  }
}
