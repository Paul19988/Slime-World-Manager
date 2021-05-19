package com.grinderwolf.swm.api.utils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Credits to Minikloon for this class.
 * <p>
 * Source: https://github.com/Minikloon/CraftyWorld/blob/master/crafty-common/src/main/kotlin/world/crafty/common/utils/NibbleArray.kt
 */
@RequiredArgsConstructor
public final class NibbleArray {

  /**
   * the backing.
   */
  @Getter
  private final byte[] backing;

  /**
   * ctor.
   *
   * @param size the size.
   */
  public NibbleArray(final int size) {
    this(new byte[size / 2]);
  }

  /**
   * obtains the byte at the index.
   *
   * @param index the index to get.
   *
   * @return backing value at index.
   */
  public int get(final int index) {
    final int value = this.backing[index / 2];
    return index % 2 == 0 ? value & 0xF : (value & 0xF0) >> 4;
  }

  /**
   * sets the backing at the index.
   *
   * @param index the index to set.
   * @param value the value to set.
   */
  public void set(final int index, final int value) {
    final int nibble = value & 0xF;
    final int halfIndex = index / 2;
    final int previous = this.backing[halfIndex];
    if (index % 2 == 0) {
      this.backing[halfIndex] = (byte) (previous & 0xF0 | nibble);
    } else {
      this.backing[halfIndex] = (byte) (previous & 0xF | nibble << 4);
    }
  }
}
