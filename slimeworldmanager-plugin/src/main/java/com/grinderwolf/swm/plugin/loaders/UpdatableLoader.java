package com.grinderwolf.swm.plugin.loaders;

import com.grinderwolf.swm.api.loaders.SlimeLoader;
import java.io.IOException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

public abstract class UpdatableLoader implements SlimeLoader {

  public abstract void update() throws NewerDatabaseException, IOException;

  @Getter
  @RequiredArgsConstructor
  public static final class NewerDatabaseException extends Exception {

    private final int currentVersion;

    private final int databaseVersion;
  }
}
