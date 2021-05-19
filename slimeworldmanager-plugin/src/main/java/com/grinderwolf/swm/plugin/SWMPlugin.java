package com.grinderwolf.swm.plugin;

import static com.grinderwolf.swm.api.world.properties.SlimeProperties.ALLOW_ANIMALS;
import static com.grinderwolf.swm.api.world.properties.SlimeProperties.ALLOW_MONSTERS;
import static com.grinderwolf.swm.api.world.properties.SlimeProperties.DIFFICULTY;
import static com.grinderwolf.swm.api.world.properties.SlimeProperties.ENVIRONMENT;
import static com.grinderwolf.swm.api.world.properties.SlimeProperties.PVP;
import static com.grinderwolf.swm.api.world.properties.SlimeProperties.SPAWN_X;
import static com.grinderwolf.swm.api.world.properties.SlimeProperties.SPAWN_Y;
import static com.grinderwolf.swm.api.world.properties.SlimeProperties.SPAWN_Z;
import com.flowpowered.nbt.CompoundMap;
import com.flowpowered.nbt.CompoundTag;
import com.github.luben.zstd.util.Native;
import com.grinderwolf.swm.api.SlimePlugin;
import com.grinderwolf.swm.api.exceptions.CorruptedWorldException;
import com.grinderwolf.swm.api.exceptions.InvalidVersionException;
import com.grinderwolf.swm.api.exceptions.InvalidWorldException;
import com.grinderwolf.swm.api.exceptions.NewerFormatException;
import com.grinderwolf.swm.api.exceptions.UnknownWorldException;
import com.grinderwolf.swm.api.exceptions.WorldAlreadyExistsException;
import com.grinderwolf.swm.api.exceptions.WorldInUseException;
import com.grinderwolf.swm.api.exceptions.WorldLoadedException;
import com.grinderwolf.swm.api.exceptions.WorldTooBigException;
import com.grinderwolf.swm.api.loaders.SlimeLoader;
import com.grinderwolf.swm.api.world.SlimeWorld;
import com.grinderwolf.swm.api.world.properties.SlimePropertyMap;
import com.grinderwolf.swm.nms.CraftSlimeWorld;
import com.grinderwolf.swm.nms.SlimeNMS;
import com.grinderwolf.swm.nms.v1_16_R1.v1_16_R1SlimeNMS;
import com.grinderwolf.swm.nms.v1_16_R2.v1_16_R2SlimeNMS;
import com.grinderwolf.swm.nms.v1_16_R3.v1_16_R3SlimeNMS;
import com.grinderwolf.swm.plugin.commands.CommandManager;
import com.grinderwolf.swm.plugin.config.ConfigManager;
import com.grinderwolf.swm.plugin.config.MainConfig;
import com.grinderwolf.swm.plugin.config.WorldsConfig;
import com.grinderwolf.swm.plugin.config.data.WorldData;
import com.grinderwolf.swm.plugin.loaders.LoaderUtils;
import com.grinderwolf.swm.plugin.log.Logging;
import com.grinderwolf.swm.plugin.update.Updater;
import com.grinderwolf.swm.plugin.upgrade.WorldUpgrader;
import com.grinderwolf.swm.plugin.world.WorldUnlocker;
import com.grinderwolf.swm.plugin.world.importer.WorldImporter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import lombok.Getter;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class SWMPlugin extends JavaPlugin implements SlimePlugin {

  private static boolean isPaperMC = false;

  private final List<SlimeWorld> worlds = new ArrayList<>();

  @Getter
  private SlimeNMS nms;

  public static SWMPlugin getInstance() {
    return SWMPlugin.getPlugin(SWMPlugin.class);
  }

  public static boolean isPaperMC() {
    return SWMPlugin.isPaperMC;
  }

  private static boolean checkIsPaper() {
    try {
      Class.forName("com.destroystokyo.paper.PaperConfig");
      return true;
    } catch (final ClassNotFoundException ex) {
      return false;
    }
  }

  @NotNull
  private static SlimeNMS getNMSBridge() throws InvalidVersionException {
    final String version = Bukkit.getServer().getClass().getPackage().getName();
    final String nmsVersion = version.substring(version.lastIndexOf('.') + 1);
    switch (nmsVersion) {
      case "v1_16_R1":
        return new v1_16_R1SlimeNMS(SWMPlugin.isPaperMC);
      case "v1_16_R2":
        return new v1_16_R2SlimeNMS(SWMPlugin.isPaperMC);
      case "v1_16_R3":
        return new v1_16_R3SlimeNMS(SWMPlugin.isPaperMC);
      default:
        throw new InvalidVersionException(nmsVersion);
    }
  }

  @NotNull
  private static SlimePropertyMap propertiesToMap(@NotNull final SlimeWorld.SlimeProperties properties) {
    final SlimePropertyMap propertyMap = new SlimePropertyMap();
    propertyMap.setValue(SPAWN_X, (int) properties.getSpawnX());
    propertyMap.setValue(SPAWN_Y, (int) properties.getSpawnY());
    propertyMap.setValue(SPAWN_Z, (int) properties.getSpawnZ());
    propertyMap.setValue(DIFFICULTY, Difficulty.getByValue(properties.getDifficulty()).name());
    propertyMap.setValue(ALLOW_MONSTERS, properties.allowMonsters());
    propertyMap.setValue(ALLOW_ANIMALS, properties.allowAnimals());
    propertyMap.setValue(PVP, properties.isPvp());
    propertyMap.setValue(ENVIRONMENT, properties.getEnvironment());
    return propertyMap;
  }

  @NotNull
  @Override
  public SlimeWorld createEmptyWorld(@NotNull final SlimeLoader loader, @NotNull final String worldName,
                                     final SlimeWorld.@NotNull SlimeProperties properties)
    throws WorldAlreadyExistsException, IOException {
    Objects.requireNonNull(properties, "Properties cannot be null");
    return this.createEmptyWorld(loader, worldName, properties.isReadOnly(), SWMPlugin.propertiesToMap(properties));
  }

  @NotNull
  @Override
  public SlimeWorld createEmptyWorld(@NotNull final SlimeLoader loader, @NotNull final String worldName,
                                     final boolean readOnly, @NotNull final SlimePropertyMap propertyMap)
    throws WorldAlreadyExistsException, IOException {
    Objects.requireNonNull(loader, "Loader cannot be null");
    Objects.requireNonNull(worldName, "World name cannot be null");
    Objects.requireNonNull(propertyMap, "Properties cannot be null");
    if (loader.worldExists(worldName)) {
      throw new WorldAlreadyExistsException(worldName);
    }
    Logging.info("Creating empty world " + worldName + ".");
    final long start = System.currentTimeMillis();
    final CraftSlimeWorld world = new CraftSlimeWorld(new HashMap<>(), new CompoundTag("", new CompoundMap()),
      !readOnly, worldName, propertyMap, readOnly, new ArrayList<>(), loader, this.nms.getWorldVersion());
    loader.saveWorld(worldName, world.serialize(), !readOnly);
    Logging.info("World " + worldName + " created in " + (System.currentTimeMillis() - start) + "ms.");
    return world;
  }

  @Override
  public void generateWorld(@NotNull final SlimeWorld world) {
    Objects.requireNonNull(world, "SlimeWorld cannot be null");
    if (!world.isReadOnly() && !world.isLocked()) {
      throw new IllegalArgumentException("This world cannot be loaded, as it has not been locked.");
    }
    this.nms.generateWorld(world);
  }

  @Override
  public SlimeLoader getLoader(@NotNull final String dataSource) {
    Objects.requireNonNull(dataSource, "Data source cannot be null");
    return LoaderUtils.getLoader(dataSource);
  }

  @Override
  public void importWorld(@NotNull final File worldDir, @NotNull final String worldName,
                          @NotNull final SlimeLoader loader) throws WorldAlreadyExistsException,
    InvalidWorldException, WorldLoadedException, WorldTooBigException, IOException {
    Objects.requireNonNull(worldDir, "World directory cannot be null");
    Objects.requireNonNull(worldName, "World name cannot be null");
    Objects.requireNonNull(loader, "Loader cannot be null");
    if (loader.worldExists(worldName)) {
      throw new WorldAlreadyExistsException(worldName);
    }
    final World bukkitWorld = Bukkit.getWorld(worldDir.getName());
    if (bukkitWorld != null && this.nms.getSlimeWorld(bukkitWorld) == null) {
      throw new WorldLoadedException(worldDir.getName());
    }
    final CraftSlimeWorld world = WorldImporter.readFromDirectory(worldDir);
    final byte[] serializedWorld;
    try {
      serializedWorld = world.serialize();
    } catch (final IndexOutOfBoundsException ex) {
      throw new WorldTooBigException(worldDir.getName());
    }
    loader.saveWorld(worldName, serializedWorld, false);
  }

  @NotNull
  @Override
  public SlimeWorld loadWorld(@NotNull final SlimeLoader loader, @NotNull final String worldName,
                              final boolean readOnly, @NotNull final SlimePropertyMap propertyMap)
    throws UnknownWorldException, IOException,
    CorruptedWorldException, NewerFormatException, WorldInUseException {
    final long start = System.currentTimeMillis();
    Logging.info("Loading world " + worldName + ".");
    final byte[] serializedWorld = loader.loadWorld(worldName, readOnly);
    final CraftSlimeWorld world;
    try {
      world = LoaderUtils.deserializeWorld(loader, worldName, serializedWorld, propertyMap, readOnly);
      if (world.getVersion() > this.nms.getWorldVersion()) {
        WorldUpgrader.downgradeWorld(world);
      } else if (world.getVersion() < this.nms.getWorldVersion()) {
        WorldUpgrader.upgradeWorld(world);
      }
    } catch (final Exception ex) {
      if (!readOnly) { // Unlock the world as we're not using it
        loader.unlockWorld(worldName);
      }
      throw ex;
    }
    Logging.info("World " + worldName + " loaded in " + (System.currentTimeMillis() - start) + "ms.");
    return world;
  }

  @Override
  public SlimeWorld loadWorld(@NotNull final SlimeLoader loader, @NotNull final String worldName,
                              @NotNull final SlimeWorld.SlimeProperties properties) throws UnknownWorldException,
    IOException, CorruptedWorldException, NewerFormatException, WorldInUseException {
    Objects.requireNonNull(properties, "Properties cannot be null");
    return this.loadWorld(loader, worldName, properties.isReadOnly(), SWMPlugin.propertiesToMap(properties));
  }

  @Override
  public void migrateWorld(@NotNull final String worldName, @NotNull final SlimeLoader currentLoader,
                           @NotNull final SlimeLoader newLoader) throws IOException,
    WorldInUseException, WorldAlreadyExistsException, UnknownWorldException {
    Objects.requireNonNull(worldName, "World name cannot be null");
    Objects.requireNonNull(currentLoader, "Current loader cannot be null");
    Objects.requireNonNull(newLoader, "New loader cannot be null");
    if (newLoader.worldExists(worldName)) {
      throw new WorldAlreadyExistsException(worldName);
    }
    final World bukkitWorld = Bukkit.getWorld(worldName);
    boolean leaveLock = false;
    if (bukkitWorld != null) {
      // Make sure the loaded world really is a SlimeWorld and not a normal Bukkit world
      final CraftSlimeWorld slimeWorld = (CraftSlimeWorld) SWMPlugin.getInstance().getNms().getSlimeWorld(bukkitWorld);
      if (slimeWorld != null && currentLoader.equals(slimeWorld.getLoader())) {
        slimeWorld.setLoader(newLoader);
        if (!slimeWorld.isReadOnly()) { // We have to manually unlock the world so no WorldInUseException is thrown
          currentLoader.unlockWorld(worldName);
          leaveLock = true;
        }
      }
    }
    final byte[] serializedWorld = currentLoader.loadWorld(worldName, false);
    newLoader.saveWorld(worldName, serializedWorld, leaveLock);
    currentLoader.deleteWorld(worldName);
  }

  @Override
  public void registerLoader(@NotNull final String dataSource, @NotNull final SlimeLoader loader) {
    Objects.requireNonNull(dataSource, "Data source cannot be null");
    Objects.requireNonNull(loader, "Loader cannot be null");
    LoaderUtils.registerLoader(dataSource, loader);
  }

  @Override
  public void onLoad() {
    Native.load();
    SWMPlugin.isPaperMC = SWMPlugin.checkIsPaper();
    ConfigManager.initialize(this);
    LoaderUtils.registerLoaders();
    try {
      this.nms = SWMPlugin.getNMSBridge();
    } catch (final InvalidVersionException ex) {
      Logging.error(ex.getMessage());
      return;
    }
    final List<String> erroredWorlds = this.loadWorlds();
    // Default world override
    try {
      final Properties props = new Properties();
      props.load(new FileInputStream("server.properties"));
      final String defaultWorldName = props.getProperty("level-name");
      if (erroredWorlds.contains(defaultWorldName)) {
        Logging.error("Shutting down server, as the default world could not be loaded.");
        System.exit(1);
      } else if (this.getServer().getAllowNether() && erroredWorlds.contains(defaultWorldName + "_nether")) {
        Logging.error("Shutting down server, as the default nether world could not be loaded.");
        System.exit(1);
      } else if (this.getServer().getAllowEnd() && erroredWorlds.contains(defaultWorldName + "_the_end")) {
        Logging.error("Shutting down server, as the default end world could not be loaded.");
        System.exit(1);
      }
      final SlimeWorld defaultWorld = this.worlds.stream()
        .filter(world -> world.getName().equals(defaultWorldName))
        .findFirst()
        .orElse(null);
      final SlimeWorld netherWorld;
      if (this.getServer().getAllowNether()) {
        netherWorld = this.worlds.stream()
          .filter(world -> world.getName().equals(defaultWorldName + "_nether"))
          .findFirst()
          .orElse(null);
      } else {
        netherWorld = null;
      }
      final SlimeWorld endWorld;
      if (this.getServer().getAllowEnd()) {
        endWorld = this.worlds.stream()
          .filter(world -> world.getName().equals(defaultWorldName + "_the_end"))
          .findFirst()
          .orElse(null);
      } else {
        endWorld = null;
      }
      this.nms.setDefaultWorlds(defaultWorld, netherWorld, endWorld);
    } catch (final IOException ex) {
      Logging.error("Failed to retrieve default world name:");
      ex.printStackTrace();
    }
  }

  @Override
  public void onEnable() {
    if (this.nms == null) {
      this.setEnabled(false);
      return;
    }
    new Metrics(this, 5419);
    final CommandManager commandManager = new CommandManager(this);
    final PluginCommand swmCommand = this.getCommand("swm");
    if (swmCommand != null) {
      swmCommand.setExecutor(commandManager);
      try {
        swmCommand.setTabCompleter(commandManager);
      } catch (final Throwable throwable) {
        // For some versions that does not have TabComplete?
      }
    }
    this.getServer().getPluginManager().registerEvents(new WorldUnlocker(), this);
    if (MainConfig.Update.enabled) {
      this.getServer().getPluginManager().registerEvents(new Updater(), this);
    }
    for (final SlimeWorld world : this.worlds) {
      if (Bukkit.getWorld(world.getName()) == null) {
        this.generateWorld(world);
      }
    }
    this.worlds.clear();
  }

  @NotNull
  private List<String> loadWorlds() {
    final List<String> erroredWorlds = new ArrayList<>();
    for (final Map.Entry<String, WorldData> entry : WorldsConfig.worlds.entrySet()) {
      final String worldName = entry.getKey();
      final WorldData worldData = entry.getValue();
      if (worldData.isLoadOnStartup()) {
        String message = null;
        try {
          final SlimeLoader loader = this.getLoader(worldData.getDataSource());
          if (loader == null) {
            throw new IllegalArgumentException("invalid data source " + worldData.getDataSource() + "");
          }
          final SlimePropertyMap propertyMap = worldData.toPropertyMap();
          final SlimeWorld world = this.loadWorld(loader, worldName, worldData.isReadOnly(), propertyMap);
          this.worlds.add(world);
        } catch (final IllegalArgumentException e) {
          message = e.getMessage();
        } catch (final UnknownWorldException e) {
          message = "world does not exist, are you sure you've set the correct data source?";
        } catch (final NewerFormatException e) {
          message = "world is serialized in a newer Slime Format version (" + e.getMessage() + ") that SWM does not understand.";
        } catch (final WorldInUseException e) {
          message = "world is in use! If you think this is a mistake, please wait some time and try again.";
        } catch (final CorruptedWorldException e) {
          message = "world seems to be corrupted.";
        } catch (final Exception e) {
          message = "";
          e.printStackTrace();
        }
        if (message != null) {
          Logging.error("Failed to load world " + worldName + (message.isEmpty() ? "." : ": " + message));
          erroredWorlds.add(worldName);
        }
      }
    }
    WorldsConfig.save();
    return erroredWorlds;
  }
}
