package com.grinderwolf.swm.plugin.config;

import java.io.File;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public final class ConfigManager {

  public static void initialize(@NotNull final Plugin plugin) {
    final File dataFolder = plugin.getDataFolder();
    if (!dataFolder.exists()) {
      dataFolder.mkdirs();
    }
    MainConfig.load(plugin);
    DatasourceConfig.load(plugin);
    WorldsConfig.load(plugin);
  }
}
