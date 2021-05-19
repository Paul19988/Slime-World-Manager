package com.grinderwolf.swm.plugin.config.data;

import static com.grinderwolf.swm.api.world.properties.SlimeProperties.ALLOW_ANIMALS;
import static com.grinderwolf.swm.api.world.properties.SlimeProperties.ALLOW_MONSTERS;
import static com.grinderwolf.swm.api.world.properties.SlimeProperties.DEFAULT_BIOME;
import static com.grinderwolf.swm.api.world.properties.SlimeProperties.DIFFICULTY;
import static com.grinderwolf.swm.api.world.properties.SlimeProperties.DRAGON_BATTLE;
import static com.grinderwolf.swm.api.world.properties.SlimeProperties.ENVIRONMENT;
import static com.grinderwolf.swm.api.world.properties.SlimeProperties.PVP;
import static com.grinderwolf.swm.api.world.properties.SlimeProperties.SPAWN_X;
import static com.grinderwolf.swm.api.world.properties.SlimeProperties.SPAWN_Y;
import static com.grinderwolf.swm.api.world.properties.SlimeProperties.SPAWN_Z;
import static com.grinderwolf.swm.api.world.properties.SlimeProperties.WORLD_TYPE;
import com.grinderwolf.swm.api.world.properties.SlimePropertyMap;
import io.github.portlek.configs.configuration.ConfigurationSection;
import io.github.portlek.configs.loaders.DataSerializer;
import java.util.Locale;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Difficulty;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

@AllArgsConstructor
@Getter
public final class WorldData implements DataSerializer {

  private final boolean allowAnimals;

  private final boolean allowMonsters;

  @NotNull
  private final String defaultBiome;

  @NotNull
  private final String difficulty;

  private final boolean dragonBattle;

  @NotNull
  private final String environment;

  private final boolean loadOnStartup;

  private final boolean pvp;

  private final boolean readOnly;

  @NotNull
  private final String spawn;

  @NotNull
  private final String worldName;

  @NotNull
  private final String worldType;

  @NotNull
  @Setter
  private String dataSource;

  public WorldData(@NotNull final String worldName, @NotNull final String dataSource, @NotNull final String spawn) {
    this(true, true, "minecraft:plains", "peaceful", false, "NORMAL", true, true, false, spawn, worldName,
      "DEFAULT", dataSource);
  }

  @NotNull
  public static Optional<WorldData> deserialize(@NotNull final ConfigurationSection section) {
    final String dataSource = section.getString("dataSource");
    final String defaultBiome = section.getString("defaultBiome");
    final String difficulty = section.getString("difficulty");
    final String environment = section.getString("environment");
    final String spawn = section.getString("spawn");
    final String worldName = section.getString("worldName");
    final String worldType = section.getString("worldType");
    if (dataSource == null || defaultBiome == null || difficulty == null || environment == null || spawn == null ||
      worldName == null || worldType == null) {
      return Optional.empty();
    }
    final boolean allowAnimals = section.getBoolean("allowAnimals");
    final boolean allowMonsters = section.getBoolean("allowMonsters");
    final boolean pvp = section.getBoolean("pvp");
    final boolean readOnly = section.getBoolean("readOnly");
    final boolean loadOnStartup = section.getBoolean("loadOnStartup");
    final boolean dragonBattle = section.getBoolean("dragonBattle");
    return Optional.of(new WorldData(allowAnimals, allowMonsters, defaultBiome, difficulty, dragonBattle,
      environment, loadOnStartup, pvp, readOnly, spawn, worldName, worldType, dataSource));
  }

  @Override
  public void serialize(@NotNull final ConfigurationSection section) {
    section.set("dataSource", this.dataSource);
    section.set("defaultBiome", this.defaultBiome);
    section.set("difficulty", this.difficulty);
    section.set("environment", this.environment);
    section.set("spawn", this.spawn);
    section.set("worldName", this.worldName);
    section.set("worldType", this.worldType);
    section.set("allowAnimals", this.allowAnimals);
    section.set("allowMonsters", this.allowMonsters);
    section.set("pvp", this.pvp);
    section.set("readOnly", this.readOnly);
    section.set("loadOnStartup", this.loadOnStartup);
    section.set("dragonBattle", this.dragonBattle);
  }

  @NotNull
  public SlimePropertyMap toPropertyMap() {
    try {
      Enum.valueOf(Difficulty.class, this.difficulty.toUpperCase(Locale.ROOT));
    } catch (final IllegalArgumentException ex) {
      throw new IllegalArgumentException("unknown difficulty '" + this.difficulty + "'");
    }
    final String[] spawnLocationSplit = this.spawn.split(", ");
    final double spawnX;
    final double spawnY;
    final double spawnZ;
    try {
      spawnX = Double.parseDouble(spawnLocationSplit[0]);
      spawnY = Double.parseDouble(spawnLocationSplit[1]);
      spawnZ = Double.parseDouble(spawnLocationSplit[2]);
    } catch (final NumberFormatException | ArrayIndexOutOfBoundsException ex) {
      throw new IllegalArgumentException("invalid spawn location '" + this.spawn + "'");
    }
    String environment = this.environment;
    try {
      Enum.valueOf(World.Environment.class, environment.toUpperCase(Locale.ROOT));
    } catch (final IllegalArgumentException ex) {
      try {
        final int envId = Integer.parseInt(environment);
        if (envId < -1 || envId > 1) {
          throw new NumberFormatException(environment);
        }
        environment = World.Environment.getEnvironment(envId).name();
      } catch (final NumberFormatException ex2) {
        throw new IllegalArgumentException("unknown environment '" + this.environment + "'");
      }
    }
    final SlimePropertyMap propertyMap = new SlimePropertyMap();
    propertyMap.setValue(SPAWN_X, (int) spawnX);
    propertyMap.setValue(SPAWN_Y, (int) spawnY);
    propertyMap.setValue(SPAWN_Z, (int) spawnZ);
    propertyMap.setValue(DIFFICULTY, this.difficulty);
    propertyMap.setValue(ALLOW_MONSTERS, this.allowMonsters);
    propertyMap.setValue(ALLOW_ANIMALS, this.allowAnimals);
    propertyMap.setValue(DRAGON_BATTLE, this.dragonBattle);
    propertyMap.setValue(PVP, this.pvp);
    propertyMap.setValue(ENVIRONMENT, environment);
    propertyMap.setValue(WORLD_TYPE, this.worldType);
    propertyMap.setValue(DEFAULT_BIOME, this.defaultBiome);
    return propertyMap;
  }
}
