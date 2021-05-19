package com.grinderwolf.swm.plugin.commands.sub;

import com.grinderwolf.swm.plugin.SWMPlugin;
import com.grinderwolf.swm.plugin.config.WorldsConfig;
import com.grinderwolf.swm.plugin.log.Logging;
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
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

@Getter
public final class GotoCmd implements Subcommand {

  private final String description = "Teleport yourself (or someone else) to a world.";

  private final String permission = "swm.goto";

  private final String usage = "goto <world> [player]";

  @Override
  public boolean onCommand(final CommandSender sender, final String[] args) {
    if (args.length > 0) {
      final World world = Bukkit.getWorld(args[0]);
      if (world == null) {
        sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "World " + args[0] + " does not exist!");
        return true;
      }
      final Player target;
      if (args.length > 1) {
        target = Bukkit.getPlayerExact(args[1]);
      } else {
        if (!(sender instanceof Player)) {
          sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "The console cannot be teleported to a world! Please specify a player.");
          return true;
        }
        target = (Player) sender;
      }
      if (target == null) {
        sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + args[1] + " is offline.");
        return true;
      }
      sender.sendMessage(Logging.COMMAND_PREFIX + "Teleporting " + (target.getName().equals(sender.getName())
        ? "yourself" : ChatColor.YELLOW + target.getName() + ChatColor.GRAY) + " to " + ChatColor.AQUA + world.getName() + ChatColor.GRAY + "...");
      final Location spawnLocation;
      if (WorldsConfig.worlds.containsKey(world.getName())) {
        final String spawn = WorldsConfig.worlds.get(world.getName()).getSpawn();
        final String[] coords = spawn.split(", ");
        final double x = Double.parseDouble(coords[0]);
        final double y = Double.parseDouble(coords[1]);
        final double z = Double.parseDouble(coords[2]);
        spawnLocation = new Location(world, x, y, z);
      } else {
        spawnLocation = world.getSpawnLocation();
      }
      // Safe Spawn Location
      spawnLocation.setY(0);
      while (spawnLocation.getBlock().getType() != Material.AIR || spawnLocation.getBlock().getRelative(BlockFace.UP).getType() != Material.AIR) {
        if (spawnLocation.getY() >= 256) {
          spawnLocation.getWorld().getBlockAt(0, 64, 0).setType(Material.BEDROCK);
        } else {
          spawnLocation.add(0, 1, 0);
        }
      }
      if (SWMPlugin.isPaperMC()) {
        target.teleportAsync(spawnLocation);
      } else {
        target.teleport(spawnLocation);
      }
      return true;
    }
    return false;
  }

  @Override
  public List<String> onTabComplete(final CommandSender sender, final String[] args) {
    List<String> toReturn = null;
    if (sender instanceof ConsoleCommandSender) {
      return Collections.emptyList();
    }
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
    if (args.length == 3) {
      final String typed = args[2].toLowerCase(Locale.ROOT);
      for (final Player player : Bukkit.getOnlinePlayers()) {
        final String playerName = player.getName();
        if (playerName.toLowerCase(Locale.ROOT).startsWith(typed)) {
          if (toReturn == null) {
            toReturn = new LinkedList<>();
          }
          toReturn.add(playerName);
        }
      }
    }
    return toReturn == null ? Collections.emptyList() : toReturn;
  }
}
