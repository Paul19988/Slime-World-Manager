package com.grinderwolf.swm.plugin.config;

import io.github.portlek.configs.ConfigHolder;
import io.github.portlek.configs.ConfigLoader;
import io.github.portlek.configs.annotation.Route;
import io.github.portlek.configs.yaml.YamlType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public final class MainConfig implements ConfigHolder {

  public static Update update = new Update();

  static void load(@NotNull final Plugin plugin) {
    ConfigLoader.builder("main", plugin.getDataFolder(), YamlType.get())
      .setConfigHolder(new MainConfig())
      .build()
      .load(true);
  }

  public static final class Update implements ConfigHolder {

    public static boolean enabled = true;

    @Route("onjoinmessage")
    public static boolean onJoinMessage = true;
  }
}
