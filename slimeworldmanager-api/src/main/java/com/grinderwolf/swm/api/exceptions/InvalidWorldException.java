package com.grinderwolf.swm.api.exceptions;

import java.io.File;
import org.jetbrains.annotations.NotNull;

/**
 * an exception class throws when a folder does not contain a valid Minecraft world.
 */
public final class InvalidWorldException extends SlimeException {

  /**
   * ctor.
   *
   * @param worldDirectory the world directory.
   * @param reason the reason.
   */
  public InvalidWorldException(@NotNull final File worldDirectory, @NotNull final String reason) {
    super(String.format("Directory %s does not contain a valid MC world! %s", worldDirectory.getPath(), reason));
  }

  /**
   * ctor.
   *
   * @param worldDirectory the world directory.
   */
  public InvalidWorldException(@NotNull final File worldDirectory) {
    super(String.format("Directory %s does not contain a valid MC world!", worldDirectory.getPath()));
  }
}
