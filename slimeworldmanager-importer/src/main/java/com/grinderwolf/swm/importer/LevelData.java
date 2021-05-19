package com.grinderwolf.swm.importer;

import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
final class LevelData {

  private final Map<String, String> gameRules;

  private final int version;
}
