package com.grinderwolf.swm.plugin.commands.sub;

import com.grinderwolf.swm.api.exceptions.UnknownWorldException;
import com.grinderwolf.swm.api.loaders.SlimeLoader;
import com.grinderwolf.swm.plugin.config.WorldsConfig;
import com.grinderwolf.swm.plugin.config.data.WorldData;
import com.grinderwolf.swm.plugin.loaders.LoaderUtils;
import com.grinderwolf.swm.plugin.log.Logging;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@Getter
public final class UnloadWorldCmd implements Subcommand {

  private final String description = "Unload a world.";

  private final String permission = "swm.unloadworld";

  private final String usage = "unload <world> [data-source]";

  @Override
  public boolean onCommand(final CommandSender sender, final String[] args) {
    if (args.length > 0) {
      final String worldName = args[0];
      final World world = Bukkit.getWorld(args[0]);
      if (world == null) {
        sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "World " + worldName + " is not loaded!");
        return true;
      }
      final String source;
      if (args.length > 1) {
        source = args[1];
      } else {
        final WorldData worldData = WorldsConfig.worlds.get(worldName);
        if (worldData == null) {
          sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "Unknown world " + worldName + "! Are you sure you've typed it correctly?");
          return true;
        }
        source = worldData.getDataSource();
      }
      final SlimeLoader loader = LoaderUtils.getLoader(source);
      // Teleport all players outside the world before unloading it
      final List<Player> players = world.getPlayers();
      if (!players.isEmpty()) {
        final World defaultWorld = Bukkit.getWorlds().get(0);
        final Location spawnLocation = defaultWorld.getSpawnLocation();
        spawnLocation.setY(64);
        while (spawnLocation.getBlock().getType() != Material.AIR || spawnLocation.getBlock().getRelative(BlockFace.UP).getType() != Material.AIR) {
          if (spawnLocation.getY() >= 256) {
            spawnLocation.getWorld().getBlockAt(0, 64, 0).setType(Material.BEDROCK);
          } else {
            spawnLocation.add(0, 1, 0);
          }
        }
        for (final Player player : players) {
          player.teleportAsync(spawnLocation);
        }
      }
      if (Bukkit.unloadWorld(world, true)) {
        System.out.println("Attempting to unload world.. " + worldName + ".");
        try {
          if (loader.isWorldLocked(worldName)) {
            System.out.println("World.. " + worldName + " is locked.");
            loader.unlockWorld(worldName);
            System.out.println("Attempted to unlock world.. " + worldName + ".");
          }
        } catch (final UnknownWorldException | IOException e) {
          e.printStackTrace();
        }
        sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.GREEN + "World " + ChatColor.YELLOW + worldName + ChatColor.GREEN + " unloaded correctly.");
      } else {
        sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "Failed to unload world " + worldName + ".");
      }
      return true;
    }
    return false;
  }

  @Override
  public List<String> onTabComplete(final CommandSender sender, final String[] args) {
    List<String> toReturn = null;
    if (args.length == 2) {
      final String typed = args[1].toLowerCase(Locale.ROOT);
      for (final World world : Bukkit.getWorlds()) {
        final String worldName = world.getName();
        if (worldName.toLowerCase(Locale.ROOT).startsWith(typed)) {
          if (toReturn == null) {
            toReturn = new LinkedList<>();
          }
          toReturn.add(worldName);
        }
      }
    }
    return toReturn == null ? Collections.emptyList() : toReturn;
  }
}
