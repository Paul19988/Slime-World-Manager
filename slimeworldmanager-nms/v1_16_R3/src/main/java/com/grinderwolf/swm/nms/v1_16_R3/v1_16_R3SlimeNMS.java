package com.grinderwolf.swm.nms.v1_16_R3;

import com.flowpowered.nbt.CompoundTag;
import com.grinderwolf.swm.api.world.SlimeWorld;
import com.grinderwolf.swm.api.world.properties.SlimeProperties;
import com.grinderwolf.swm.nms.CraftSlimeWorld;
import com.grinderwolf.swm.nms.SlimeNMS;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.Getter;
import net.minecraft.server.v1_16_R3.ChunkGenerator;
import net.minecraft.server.v1_16_R3.Convertable;
import net.minecraft.server.v1_16_R3.DataConverterRegistry;
import net.minecraft.server.v1_16_R3.DataFixTypes;
import net.minecraft.server.v1_16_R3.DedicatedServer;
import net.minecraft.server.v1_16_R3.DedicatedServerProperties;
import net.minecraft.server.v1_16_R3.DimensionManager;
import net.minecraft.server.v1_16_R3.DynamicOpsNBT;
import net.minecraft.server.v1_16_R3.EnderDragonBattle;
import net.minecraft.server.v1_16_R3.GameProfileSerializer;
import net.minecraft.server.v1_16_R3.GameRules;
import net.minecraft.server.v1_16_R3.IRegistry;
import net.minecraft.server.v1_16_R3.LevelVersion;
import net.minecraft.server.v1_16_R3.MinecraftKey;
import net.minecraft.server.v1_16_R3.MinecraftServer;
import net.minecraft.server.v1_16_R3.NBTBase;
import net.minecraft.server.v1_16_R3.NBTTagCompound;
import net.minecraft.server.v1_16_R3.RegistryMaterials;
import net.minecraft.server.v1_16_R3.ResourceKey;
import net.minecraft.server.v1_16_R3.SharedConstants;
import net.minecraft.server.v1_16_R3.WorldDataServer;
import net.minecraft.server.v1_16_R3.WorldDimension;
import net.minecraft.server.v1_16_R3.WorldServer;
import net.minecraft.server.v1_16_R3.WorldSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.libs.org.apache.commons.io.FileUtils;
import org.bukkit.craftbukkit.v1_16_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_16_R3.util.CraftMagicNumbers;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
public final class v1_16_R3SlimeNMS implements SlimeNMS {

  private static final Logger LOGGER = LogManager.getLogger("SWM");

  private static final File UNIVERSE_DIR;

  public static Convertable CONVERTABLE;

  private static boolean isPaperMC;

  private final byte worldVersion = 0x06;

  private CustomWorldServer defaultEndWorld;

  private CustomWorldServer defaultNetherWorld;

  private CustomWorldServer defaultWorld;

  private boolean loadingDefaultWorlds = true; // If true, the addWorld method will not be skipped

  static {
    Path path;
    try {
      path = Files.createTempDirectory("swm-" + UUID.randomUUID().toString().substring(0, 5) + "-");
    } catch (final IOException ex) {
//            LOGGER.log(Level.FATAL, "Failed to create temp directory", ex);
      path = null;
      System.exit(1);
    }
    UNIVERSE_DIR = path.toFile();
    v1_16_R3SlimeNMS.CONVERTABLE = Convertable.a(path);
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        FileUtils.deleteDirectory(v1_16_R3SlimeNMS.UNIVERSE_DIR);
      } catch (final IOException ex) {
//                LOGGER.log(Level.FATAL, "Failed to delete temp directory", ex);
      }
    }));
  }

  public v1_16_R3SlimeNMS(final boolean isPaper) {
    try {
      v1_16_R3SlimeNMS.isPaperMC = isPaper;
      CraftCLSMBridge.initialize(this);
    } catch (final NoClassDefFoundError ex) {
//            LOGGER.error("Failed to find ClassModifier classes. Are you sure you installed it correctly?");
      System.exit(1); // No ClassModifier, no party
    }
  }

  @NotNull
  private static CustomWorldServer createDefaultWorld(
    @NotNull final SlimeWorld world, @NotNull final ResourceKey<WorldDimension> dimensionKey,
    @NotNull final ResourceKey<net.minecraft.server.v1_16_R3.World> worldKey) {
    final WorldDataServer worldDataServer = v1_16_R3SlimeNMS.createWorldData(world);
    final RegistryMaterials<WorldDimension> registryMaterials = worldDataServer.getGeneratorSettings().d();
    final WorldDimension worldDimension = registryMaterials.a(dimensionKey);
    final DimensionManager dimensionManager = worldDimension.b();
    final ChunkGenerator chunkGenerator = worldDimension.c();
    final World.Environment environment = v1_16_R3SlimeNMS.getEnvironment(world);
    if (dimensionKey == WorldDimension.OVERWORLD && environment != World.Environment.NORMAL) {
//            LOGGER.warn("The environment for the default world should always be 'NORMAL'.");
    }
    try {
      return new CustomWorldServer((CraftSlimeWorld) world, worldDataServer,
        worldKey, dimensionKey, dimensionManager, chunkGenerator, environment);
    } catch (final IOException ex) {
      throw new RuntimeException(ex); // TODO do something better with this?
    }
  }

  @NotNull
  private static WorldDataServer createWorldData(@NotNull final SlimeWorld world) {
    final String worldName = world.getName();
    final CompoundTag extraData = world.getExtraData();
    final WorldDataServer worldDataServer;
    final NBTTagCompound extraTag = (NBTTagCompound) Converter.convertTag(extraData);
    final MinecraftServer mcServer = MinecraftServer.getServer();
    final DedicatedServerProperties serverProps = ((DedicatedServer) mcServer).getDedicatedServerProperties();
    if (extraTag.hasKeyOfType("LevelData", CraftMagicNumbers.NBT.TAG_COMPOUND)) {
      final NBTTagCompound levelData = extraTag.getCompound("LevelData");
      final int dataVersion = levelData.hasKeyOfType("DataVersion", 99) ? levelData.getInt("DataVersion") : -1;
      final Dynamic<NBTBase> dynamic = mcServer.getDataFixer().update(DataFixTypes.LEVEL.a(),
        new Dynamic<>(DynamicOpsNBT.a, levelData), dataVersion, SharedConstants.getGameVersion()
          .getWorldVersion());
      final LevelVersion levelVersion = LevelVersion.a(dynamic);
      final WorldSettings worldSettings = WorldSettings.a(dynamic, mcServer.datapackconfiguration);
      worldDataServer = WorldDataServer.a(dynamic, mcServer.getDataFixer(), dataVersion, null,
        worldSettings, levelVersion, serverProps.generatorSettings, Lifecycle.stable());
    } else {
      final WorldSettings worldSettings = new WorldSettings(worldName, serverProps.gamemode, false,
        serverProps.difficulty, false, new GameRules(), mcServer.datapackconfiguration);
      // Game rules
      final Optional<CompoundTag> gameRules = extraData.getAsCompoundTag("gamerules");
      gameRules.ifPresent(compoundTag -> {
        final NBTTagCompound compound = (NBTTagCompound) Converter.convertTag(compoundTag);
        final Map<String, GameRules.GameRuleKey<?>> gameRuleKeys = CraftWorld.getGameRulesNMS();
        final GameRules rules = worldSettings.getGameRules();
        compound.getKeys().forEach(gameRule -> {
          if (gameRuleKeys.containsKey(gameRule)) {
            final GameRules.GameRuleValue<?> gameRuleValue = rules.get(gameRuleKeys.get(gameRule));
            final String theValue = compound.getString(gameRule);
            gameRuleValue.setValue(theValue);
            gameRuleValue.onChange(mcServer);
          }
        });
      });
      worldDataServer = new WorldDataServer(worldSettings, serverProps.generatorSettings, Lifecycle.stable());
    }
    worldDataServer.checkName(worldName);
    worldDataServer.a(mcServer.getServerModName(), mcServer.getModded().isPresent());
    worldDataServer.c(true);
    return worldDataServer;
  }

  @NotNull
  private static World.Environment getEnvironment(@NotNull final SlimeWorld world) {
    return World.Environment.valueOf(
      world.getPropertyMap().getValue(SlimeProperties.ENVIRONMENT).toUpperCase(Locale.ROOT));
  }

  @NotNull
  @Override
  public CompoundTag convertChunk(@NotNull final CompoundTag chunkTag) {
    final NBTTagCompound nmsTag = (NBTTagCompound) Converter.convertTag(chunkTag);
    final int version = nmsTag.getInt("DataVersion");
    final NBTTagCompound newNmsTag = GameProfileSerializer.a(
      DataConverterRegistry.a(), DataFixTypes.CHUNK, nmsTag, version);
    return (CompoundTag) Converter.convertTag("", newNmsTag);
  }

  @Override
  public void generateWorld(@NotNull final SlimeWorld world) {
    final String worldName = world.getName();
    if (Bukkit.getWorld(worldName) != null) {
      throw new IllegalArgumentException(String.format(
        "World %s already exists! Maybe it's an outdated SlimeWorld object?",
        worldName));
    }
    final WorldDataServer worldDataServer = v1_16_R3SlimeNMS.createWorldData(world);
    final World.Environment environment = v1_16_R3SlimeNMS.getEnvironment(world);
    final ResourceKey<WorldDimension> dimension;
    switch (environment) {
      case NORMAL:
        dimension = WorldDimension.OVERWORLD;
        break;
      case NETHER:
        dimension = WorldDimension.THE_NETHER;
        break;
      case THE_END:
        dimension = WorldDimension.THE_END;
        break;
      default:
        throw new IllegalArgumentException("Unknown dimension supplied");
    }
    final RegistryMaterials<WorldDimension> materials = worldDataServer.getGeneratorSettings().d();
    final WorldDimension worldDimension = materials.a(dimension);
    final DimensionManager dimensionManager = worldDimension.b();
    final ChunkGenerator chunkGenerator = worldDimension.c();
    final ResourceKey<net.minecraft.server.v1_16_R3.World> worldKey = ResourceKey.a(IRegistry.L,
      new MinecraftKey(worldName.toLowerCase(java.util.Locale.ENGLISH)));
    final CustomWorldServer server;
    try {
      server = new CustomWorldServer((CraftSlimeWorld) world, worldDataServer,
        worldKey, dimension, dimensionManager, chunkGenerator, environment);
    } catch (final IOException ex) {
      throw new RuntimeException(ex); // TODO do something better with this?
    }
    final EnderDragonBattle dragonBattle = server.getDragonBattle();
    final boolean runBattle = world.getPropertyMap().getValue(SlimeProperties.DRAGON_BATTLE);
    if (dragonBattle != null && !runBattle) {
      dragonBattle.bossBattle.setVisible(false);
      try {
        final Field battleField = WorldServer.class.getDeclaredField("dragonBattle");
        battleField.setAccessible(true);
        battleField.set(server, null);
      } catch (final NoSuchFieldException | IllegalAccessException ex) {
        throw new RuntimeException(ex);
      }
    }
    server.setReady(true);
    final MinecraftServer mcServer = MinecraftServer.getServer();
    mcServer.initWorld(server, worldDataServer, mcServer.getSaveData(), worldDataServer.getGeneratorSettings());
    mcServer.server.addWorld(server.getWorld());
    mcServer.worldServer.put(worldKey, server);
    server.setSpawnFlags(
      world.getPropertyMap().getValue(SlimeProperties.ALLOW_MONSTERS),
      world.getPropertyMap().getValue(SlimeProperties.ALLOW_ANIMALS));
    Bukkit.getPluginManager().callEvent(new WorldInitEvent(server.getWorld()));
    mcServer.loadSpawn(server.getChunkProvider().playerChunkMap.worldLoadListener, server);
//        try {
//            world.getLoader().loadWorld(worldName, world.isReadOnly());
//        } catch(UnknownWorldException | WorldInUseException | IOException e) {
//            e.printStackTrace();
//        }
    Bukkit.getPluginManager().callEvent(new WorldLoadEvent(server.getWorld()));
  }

  @Nullable
  @Override
  public SlimeWorld getSlimeWorld(@NotNull final World world) {
    final CraftWorld craftWorld = (CraftWorld) world;
    if (!(craftWorld.getHandle() instanceof CustomWorldServer)) {
      return null;
    }
    final CustomWorldServer worldServer = (CustomWorldServer) craftWorld.getHandle();
    return worldServer.getSlimeWorld();
  }

  @Override
  public void setDefaultWorlds(@Nullable final SlimeWorld normalWorld, @Nullable final SlimeWorld netherWorld,
                               @Nullable final SlimeWorld endWorld) {
    if (normalWorld != null) {
      this.defaultWorld = v1_16_R3SlimeNMS.createDefaultWorld(
        normalWorld,
        WorldDimension.OVERWORLD,
        net.minecraft.server.v1_16_R3.World.OVERWORLD);
    }
    if (netherWorld != null) {
      this.defaultNetherWorld = v1_16_R3SlimeNMS.createDefaultWorld(
        netherWorld,
        WorldDimension.THE_NETHER,
        net.minecraft.server.v1_16_R3.World.THE_NETHER);
    }
    if (endWorld != null) {
      this.defaultEndWorld = v1_16_R3SlimeNMS.createDefaultWorld(
        endWorld,
        WorldDimension.THE_END,
        net.minecraft.server.v1_16_R3.World.THE_END);
    }
    this.loadingDefaultWorlds = false;
  }
}