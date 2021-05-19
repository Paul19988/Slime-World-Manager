package com.grinderwolf.swm.api.exceptions;

import org.jetbrains.annotations.NotNull;

/**
 * an exception class throws when a world is locked and is being accessed on write-mode.
 */
public final class WorldInUseException extends SlimeException {

  /**
   * ctor.
   *
   * @param world the world.
   */
  public WorldInUseException(@NotNull final String world) {
    super(world);
  }
}
