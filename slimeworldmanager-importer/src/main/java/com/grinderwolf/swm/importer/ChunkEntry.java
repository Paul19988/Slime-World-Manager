package com.grinderwolf.swm.importer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
final class ChunkEntry {

  private final int offset;

  private final int paddedSize;
}
