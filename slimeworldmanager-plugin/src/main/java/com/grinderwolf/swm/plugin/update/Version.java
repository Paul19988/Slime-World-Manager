package com.grinderwolf.swm.plugin.update;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

@Getter
public final class Version implements Comparable<Version> {

  private static final Pattern PATTERN = Pattern.compile(
    "(?<major>0|[1-9]\\d*)\\.(?<minor>0|[1-9]\\d*)(?:\\.(?<patch>0|[1-9]\\d*))?(?:-(?<tag>[A-z0-9.-]*))?");

  @NotNull
  private final String tag;

  private final int[] version = new int[3];

  Version(@NotNull final String value) {
    final Matcher matcher = Version.PATTERN.matcher(value);
    if (!matcher.find()) {
      throw new IllegalArgumentException("Invalid version format " + value);
    }
    this.version[0] = Integer.parseInt(matcher.group("major"));
    this.version[1] = Integer.parseInt(matcher.group("minor"));
    this.version[2] = Integer.parseInt(matcher.group("patch"));
    this.tag = matcher.group("tag") != null ? matcher.group("tag") : "";
  }

  @Override
  public int compareTo(@NotNull final Version other) {
    if (other == this) {
      return 0;
    }
    for (int i = 0; i < 3; i++) {
      final int partA = this.version[i];
      final int partB = other.getVersion()[i];
      if (partA > partB) {
        return 1;
      }
      if (partA < partB) {
        return -1;
      }
    }
    if (this.tag.length() == 0 && other.getTag().length() > 0) {
      return -1;
    }
    if (this.tag.length() > 0 && other.getTag().length() == 0) {
      return 1;
    }
    return 0;
  }
}
