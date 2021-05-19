package com.grinderwolf.swm.plugin.config;

import com.grinderwolf.swm.plugin.config.data.WorldData;
import com.grinderwolf.swm.plugin.config.data.Worlds;
import io.github.portlek.configs.ConfigHolder;
import io.github.portlek.configs.ConfigLoader;
import io.github.portlek.configs.configuration.ConfigurationSection;
import io.github.portlek.configs.yaml.YamlType;
import java.util.HashMap;
import lombok.Getter;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

@Getter
public final class WorldsConfig implements ConfigHolder {

  public static ConfigLoader loader;

  public static ConfigurationSection section;

  public static Worlds worlds = new Worlds(new HashMap<>());

  public static void addWorld(@NotNull final WorldData worldData) {
    WorldsConfig.worlds.put(worldData.getWorldName(), worldData);
    WorldsConfig.worlds.serialize(WorldsConfig.section.getSectionOrCreate("worlds"));
    WorldsConfig.save();
  }

  public static void load(@NotNull final Plugin plugin) {
    ConfigLoader.builder("worlds", plugin.getDataFolder(), YamlType.get())
      .setConfigHolder(new WorldsConfig())
      .addLoaders(Worlds.Loader.INSTANCE)
      .build()
      .load(true);
  }

  public static void removeWorld(@NotNull final String worldName) {
    WorldsConfig.worlds.remove(worldName);
    WorldsConfig.section.getSectionOrCreate("worlds").remove(worldName);
    WorldsConfig.save();
  }

  public static void save() {
    WorldsConfig.loader.save();
  }

  public static void setDataSource(@NotNull final WorldData worldData, @NotNull final String newSource) {
    worldData.setDataSource(newSource);
    worldData.serialize(WorldsConfig.section.getSectionOrCreate("worlds"));
    WorldsConfig.save();
  }
}
