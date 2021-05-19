package com.grinderwolf.swm.api.exceptions;

import org.jetbrains.annotations.NotNull;

/**
 * an exception class throws when a world already exists inside a data source.
 */
public final class WorldAlreadyExistsException extends SlimeException {

  /**
   * ctor.
   *
   * @param world the world.
   */
  public WorldAlreadyExistsException(@NotNull final String world) {
    super(String.format("World %s already exists!", world));
  }
}
