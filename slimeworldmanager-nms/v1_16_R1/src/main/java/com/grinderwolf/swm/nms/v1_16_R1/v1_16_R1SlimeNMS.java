package com.grinderwolf.swm.nms.v1_16_R1;

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
import net.minecraft.server.v1_16_R1.BlockPosition;
import net.minecraft.server.v1_16_R1.ChunkCoordIntPair;
import net.minecraft.server.v1_16_R1.ChunkGenerator;
import net.minecraft.server.v1_16_R1.ChunkProviderServer;
import net.minecraft.server.v1_16_R1.Convertable;
import net.minecraft.server.v1_16_R1.DataConverterRegistry;
import net.minecraft.server.v1_16_R1.DataFixTypes;
import net.minecraft.server.v1_16_R1.DedicatedServer;
import net.minecraft.server.v1_16_R1.DedicatedServerProperties;
import net.minecraft.server.v1_16_R1.DimensionManager;
import net.minecraft.server.v1_16_R1.DynamicOpsNBT;
import net.minecraft.server.v1_16_R1.EnderDragonBattle;
import net.minecraft.server.v1_16_R1.ForcedChunk;
import net.minecraft.server.v1_16_R1.GameProfileSerializer;
import net.minecraft.server.v1_16_R1.GameRules;
import net.minecraft.server.v1_16_R1.IRegistry;
import net.minecraft.server.v1_16_R1.LevelVersion;
import net.minecraft.server.v1_16_R1.MinecraftKey;
import net.minecraft.server.v1_16_R1.MinecraftServer;
import net.minecraft.server.v1_16_R1.NBTBase;
import net.minecraft.server.v1_16_R1.NBTTagCompound;
import net.minecraft.server.v1_16_R1.RegistryMaterials;
import net.minecraft.server.v1_16_R1.ResourceKey;
import net.minecraft.server.v1_16_R1.SharedConstants;
import net.minecraft.server.v1_16_R1.WorldDataServer;
import net.minecraft.server.v1_16_R1.WorldDimension;
import net.minecraft.server.v1_16_R1.WorldLoadListener;
import net.minecraft.server.v1_16_R1.WorldServer;
import net.minecraft.server.v1_16_R1.WorldSettings;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.libs.it.unimi.dsi.fastutil.longs.LongIterator;
import org.bukkit.craftbukkit.libs.org.apache.commons.io.FileUtils;
import org.bukkit.craftbukkit.v1_16_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_16_R1.util.CraftMagicNumbers;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
public final class v1_16_R1SlimeNMS implements SlimeNMS {

  private static final Logger LOGGER = LogManager.getLogger("SWM");

  private static final File UNIVERSE_DIR;

  public static Convertable CONVERTABLE;

  private static boolean isPaperMC = false;

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
      v1_16_R1SlimeNMS.LOGGER.log(Level.FATAL, "Failed to create temp directory", ex);
      path = null;
      System.exit(1);
    }
    UNIVERSE_DIR = path.toFile();
    v1_16_R1SlimeNMS.CONVERTABLE = Convertable.a(path);
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        FileUtils.deleteDirectory(v1_16_R1SlimeNMS.UNIVERSE_DIR);
      } catch (final IOException ex) {
        v1_16_R1SlimeNMS.LOGGER.log(Level.FATAL, "Failed to delete temp directory", ex);
      }
    }));
  }

  public v1_16_R1SlimeNMS(final boolean isPaper) {
    v1_16_R1SlimeNMS.isPaperMC = isPaper;
    try {
      CraftCLSMBridge.initialize(this);
    } catch (final NoClassDefFoundError ex) {
      v1_16_R1SlimeNMS.LOGGER.error("Failed to find ClassModifier classes. Are you sure you installed it correctly?");
      System.exit(1); // No ClassModifier, no party
    }
  }

  @NotNull
  private static CustomWorldServer createDefaultWorld(
    @NotNull final SlimeWorld world, @NotNull final ResourceKey<WorldDimension> dimensionKey,
    @NotNull final ResourceKey<net.minecraft.server.v1_16_R1.World> worldKey,
    @NotNull final ResourceKey<DimensionManager> dmKey) {
    final WorldDataServer worldDataServer = v1_16_R1SlimeNMS.createWorldData(world);
    final RegistryMaterials<WorldDimension> registryMaterials = worldDataServer.getGeneratorSettings().e();
    final WorldDimension worldDimension = registryMaterials.a(dimensionKey);
    final DimensionManager dimensionManager = worldDimension.b();
    final ChunkGenerator chunkGenerator = worldDimension.c();
    final World.Environment environment = v1_16_R1SlimeNMS.getEnvironment(world);
    if (dimensionKey == WorldDimension.OVERWORLD && environment != World.Environment.NORMAL) {
      v1_16_R1SlimeNMS.LOGGER.warn("The environment for the default world should always be 'NORMAL'.");
    }
    try {
      return new CustomWorldServer((CraftSlimeWorld) world, worldDataServer, worldKey, dimensionKey, dmKey,
        dimensionManager, chunkGenerator, environment);
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
      final Lifecycle lifecycle = Lifecycle.stable();
      final LevelVersion levelVersion = LevelVersion.a(dynamic);
      final WorldSettings worldSettings = WorldSettings.a(dynamic, mcServer.datapackconfiguration);
      worldDataServer = WorldDataServer.a(dynamic, mcServer.getDataFixer(), dataVersion, null,
        worldSettings, levelVersion, serverProps.generatorSettings, lifecycle);
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
    final NBTTagCompound newNmsTag = GameProfileSerializer.a(DataConverterRegistry.a(), DataFixTypes.CHUNK, nmsTag, version);
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
    final WorldDataServer worldDataServer = v1_16_R1SlimeNMS.createWorldData(world);
    final World.Environment environment = v1_16_R1SlimeNMS.getEnvironment(world);
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
    final RegistryMaterials<WorldDimension> materials = worldDataServer.getGeneratorSettings().e();
    final WorldDimension worldDimension = materials.a(WorldDimension.OVERWORLD);
    final DimensionManager dimensionManager = worldDimension.b();
    final ChunkGenerator chunkGenerator = worldDimension.c();
    final ResourceKey<net.minecraft.server.v1_16_R1.World> worldKey = ResourceKey.a(IRegistry.ae,
      new MinecraftKey(worldName.toLowerCase(Locale.ENGLISH)));
    final ResourceKey<DimensionManager> dmKey = ResourceKey.a(IRegistry.ad,
      new MinecraftKey(worldName.toLowerCase(Locale.ENGLISH)));
    final CustomWorldServer server;
    try {
      server = new CustomWorldServer((CraftSlimeWorld) world, worldDataServer,
        worldKey, dimension, dmKey, dimensionManager, chunkGenerator, environment);
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
    v1_16_R1SlimeNMS.LOGGER.info("Loading world " + worldName);
    final long startTime = System.currentTimeMillis();
    server.setReady(true);
    final MinecraftServer mcServer = MinecraftServer.getServer();
    mcServer.initWorld(server, worldDataServer, mcServer.getSaveData(), worldDataServer.getGeneratorSettings());
    mcServer.server.addWorld(server.getWorld());
    mcServer.worldServer.put(worldKey, server);
    final WorldLoadListener worldloadlistener = server.getChunkProvider().playerChunkMap.worldLoadListener;
    Bukkit.getPluginManager().callEvent(new WorldInitEvent(server.getWorld()));
    if (v1_16_R1SlimeNMS.isPaperMC) {
      if (server.getWorld().getKeepSpawnInMemory()) {
        v1_16_R1SlimeNMS.LOGGER.info("Preparing start region for dimension {}", server.getDimensionKey().a());
        final BlockPosition blockposition = server.getSpawn();
        worldloadlistener.a(new ChunkCoordIntPair(blockposition));
        final ChunkProviderServer chunkproviderserver = server.getChunkProvider();
        chunkproviderserver.getLightEngine().a(500);
        server.getWorld().getChunkAtAsync(blockposition.getX(), blockposition.getZ());
        final ForcedChunk forcedchunk = server.getWorldPersistentData().b(ForcedChunk::new, "chunks");
        if (forcedchunk != null) {
          final LongIterator longiterator = forcedchunk.a().iterator();
          while (longiterator.hasNext()) {
            final long i = longiterator.nextLong();
            final ChunkCoordIntPair chunkcoordintpair = new ChunkCoordIntPair(i);
            server.getChunkProvider().a(chunkcoordintpair, true);
          }
        }
        worldloadlistener.b();
        chunkproviderserver.getLightEngine().a(5);
        server.setSpawnFlags(
          world.getPropertyMap().getValue(SlimeProperties.ALLOW_MONSTERS),
          world.getPropertyMap().getValue(SlimeProperties.ALLOW_ANIMALS)
        );
      }
    } else {
      mcServer.loadSpawn(server.getChunkProvider().playerChunkMap.worldLoadListener, server);
    }
    Bukkit.getPluginManager().callEvent(new WorldLoadEvent(server.getWorld()));
    v1_16_R1SlimeNMS.LOGGER.info(String.format("World %s loaded in %dms.",
      worldName, System.currentTimeMillis() - startTime));
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
      this.defaultWorld = v1_16_R1SlimeNMS.createDefaultWorld(
        normalWorld,
        WorldDimension.OVERWORLD,
        net.minecraft.server.v1_16_R1.World.OVERWORLD,
        ResourceKey.a(IRegistry.ad, new MinecraftKey(normalWorld.getName().toLowerCase(Locale.ENGLISH))));
    }
    if (netherWorld != null) {
      this.defaultNetherWorld = v1_16_R1SlimeNMS.createDefaultWorld(
        netherWorld,
        WorldDimension.THE_NETHER,
        net.minecraft.server.v1_16_R1.World.THE_NETHER,
        ResourceKey.a(IRegistry.ad, new MinecraftKey(netherWorld.getName().toLowerCase(Locale.ENGLISH))));
    }
    if (endWorld != null) {
      this.defaultEndWorld = v1_16_R1SlimeNMS.createDefaultWorld(
        endWorld,
        WorldDimension.THE_END,
        net.minecraft.server.v1_16_R1.World.THE_END,
        ResourceKey.a(IRegistry.ad, new MinecraftKey(endWorld.getName().toLowerCase(Locale.ENGLISH))));
    }
    this.loadingDefaultWorlds = false;
  }
}
