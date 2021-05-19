package com.grinderwolf.swm.plugin.world.importer;

import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class LevelData {

  private final Map<String, String> gameRules;

  private final int spawnX;

  private final int spawnY;

  private final int spawnZ;

  private final int version;
}
