package com.grinderwolf.swm.clsm;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface CLSMBridge {

  @Nullable
  default Object getChunk(final Object world, final int x, final int z) {
    return null;
  }

  // When creating a world in 1.16, the WorldServer constructor sets the world's gamemode
  // to the value that the server has as the default gamemode. However, when overriding
  // the default world, this value is not yet accessible (savedata in Minecraftserver is
  // null at this stage), so this method acts as a patch to avoid that NPE in the constructor
  @Nullable
  default Object getDefaultGamemode() {
    return null;
  }
  // Array containing the normal world, the nether and the end

  Object @Nullable [] getDefaultWorlds();

  boolean isCustomWorld(@NotNull Object world);

  default boolean saveChunk(@NotNull final Object world, @NotNull final Object chunkAccess) {
    return false;
  }

  default boolean skipWorldAdd(@NotNull final Object world) {
    return false; // If true, the world won't be added to the bukkit world list
  }
}
