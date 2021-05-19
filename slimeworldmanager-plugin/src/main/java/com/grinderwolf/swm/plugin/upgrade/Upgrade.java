package com.grinderwolf.swm.plugin.upgrade;

import com.grinderwolf.swm.nms.CraftSlimeWorld;
import org.jetbrains.annotations.NotNull;

public interface Upgrade {

  void downgrade(@NotNull CraftSlimeWorld world);

  void upgrade(@NotNull CraftSlimeWorld world);
}
