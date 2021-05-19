package com.grinderwolf.swm.api.exceptions;

import org.jetbrains.annotations.NotNull;

/**
 * an exception class that throws when a world is loaded when trying to import it.
 */
public final class WorldLoadedException extends SlimeException {

  /**
   * ctor.
   *
   * @param world the world.
   */
  public WorldLoadedException(@NotNull final String world) {
    super(String.format("World %s is loaded! Unload it before importing it.", world));
  }
}
