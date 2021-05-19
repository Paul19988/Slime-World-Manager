package com.grinderwolf.swm.api.exceptions;

import org.jetbrains.annotations.NotNull;

/**
 * an exception class throws when SWM is loaded on a non-supported Spigot version.
 */
public final class InvalidVersionException extends SlimeException {

  /**
   * ctor.
   *
   * @param version the version.
   */
  public InvalidVersionException(@NotNull final String version) {
    super(String.format("SlimeWorldManager does not support Spigot %s!", version));
  }
}
