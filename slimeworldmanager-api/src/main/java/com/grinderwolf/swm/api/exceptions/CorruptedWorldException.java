package com.grinderwolf.swm.api.exceptions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * an exception class that throws when a world could not be read from its data file.
 */
public final class CorruptedWorldException extends SlimeException {

  /**
   * ctor.
   *
   * @param world the world.
   */
  public CorruptedWorldException(@NotNull final String world) {
    this(world, null);
  }

  /**
   * ctor.
   *
   * @param world the world.
   * @param exception the exception.
   */
  public CorruptedWorldException(@NotNull final String world, @Nullable final Exception exception) {
    super(String.format("World %s seems to be corrupted", world), exception);
  }
}
