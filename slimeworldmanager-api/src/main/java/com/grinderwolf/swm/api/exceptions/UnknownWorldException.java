package com.grinderwolf.swm.api.exceptions;

import org.jetbrains.annotations.NotNull;

/**
 * an exception class throws when a world could not be found.
 */
public final class UnknownWorldException extends SlimeException {

  /**
   * ctor.
   *
   * @param world the world.
   */
  public UnknownWorldException(@NotNull final String world) {
    super(String.format("Unknown world %s", world));
  }
}
