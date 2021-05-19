package com.grinderwolf.swm.api.exceptions;

import org.jetbrains.annotations.NotNull;

/**
 * an exception class that throws when a MC world is too big to be converted into the SRF.
 */
public final class WorldTooBigException extends SlimeException {

  /**
   * ctor.
   *
   * @param world the world.
   */
  public WorldTooBigException(@NotNull final String world) {
    super(String.format("World %s is too big to be converted into the SRF!", world));
  }
}
