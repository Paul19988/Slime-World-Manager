package com.grinderwolf.swm.api.exceptions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * an abstract class that represents SWM exceptions.
 */
public abstract class SlimeException extends Exception {

  /**
   * ctor.
   *
   * @param message the message.
   */
  protected SlimeException(@NotNull final String message) {
    super(message);
  }

  /**
   * ctor.
   *
   * @param message the message.
   * @param exception the exception.
   */
  protected SlimeException(@NotNull final String message, @Nullable final Exception exception) {
    super(message, exception);
  }
}
