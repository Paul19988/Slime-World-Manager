package com.grinderwolf.swm.nms;

import com.flowpowered.nbt.CompoundTag;
import com.grinderwolf.swm.api.world.SlimeWorld;
import java.io.IOException;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface SlimeNMS {

  @NotNull
  default CompoundTag convertChunk(@NotNull final CompoundTag chunkTag) {
    return chunkTag;
  }

  void generateWorld(@NotNull SlimeWorld world);

  @Nullable
  SlimeWorld getSlimeWorld(@NotNull World world);

  byte getWorldVersion();

  void setDefaultWorlds(@Nullable SlimeWorld normalWorld, @Nullable SlimeWorld netherWorld,
                        @Nullable SlimeWorld endWorld) throws IOException;
}
