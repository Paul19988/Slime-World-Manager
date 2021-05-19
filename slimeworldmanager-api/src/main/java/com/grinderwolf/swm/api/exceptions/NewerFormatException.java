package com.grinderwolf.swm.api.exceptions;

/**
 * an exception class throws when a world is encoded using a newer SRF format than the one that SWM supports.
 */
public final class NewerFormatException extends SlimeException {

  /**
   * ctor.
   *
   * @param version the version.
   */
  public NewerFormatException(final byte version) {
    super(String.format("v%s", version));
  }
}
