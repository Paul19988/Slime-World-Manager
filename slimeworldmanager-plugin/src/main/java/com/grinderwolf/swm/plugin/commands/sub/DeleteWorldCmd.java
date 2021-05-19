package com.grinderwolf.swm.plugin.commands.sub;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.grinderwolf.swm.api.exceptions.UnknownWorldException;
import com.grinderwolf.swm.api.loaders.SlimeLoader;
import com.grinderwolf.swm.plugin.SWMPlugin;
import com.grinderwolf.swm.plugin.commands.CommandManager;
import com.grinderwolf.swm.plugin.config.WorldsConfig;
import com.grinderwolf.swm.plugin.config.data.WorldData;
import com.grinderwolf.swm.plugin.loaders.LoaderUtils;
import com.grinderwolf.swm.plugin.log.Logging;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

@Getter
public final class DeleteWorldCmd implements Subcommand {

  private final Cache<String, String[]> deleteCache = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).build();

  private final String description = "Delete a world.";

  private final String permission = "swm.deleteworld";

  private final String usage = "delete <world> [data-source]";

  @Override
  public boolean onCommand(final CommandSender sender, final String[] args) {
    if (args.length > 0) {
      final String worldName = args[0];
      final World world = Bukkit.getWorld(worldName);
      if (world != null) {
        sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "World " + worldName + " is loaded on this server! Unload " +
          "it by running the command " + ChatColor.GRAY + "/swm unload " + worldName + ChatColor.RED + ".");
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
      if (loader == null) {
        sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "Unknown data source " + source + "!  Are you sure you've typed it correctly?");
        return true;
      }
      if (CommandManager.getInstance().getWorldsInUse().contains(worldName)) {
        sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "World " + worldName + " is already being used on another command! Wait some time and try again.");
        return true;
      }
      final String[] oldArgs = this.deleteCache.getIfPresent(sender.getName());
      if (oldArgs != null) {
          this.deleteCache.invalidate(sender.getName());
        if (Arrays.equals(args, oldArgs)) { // Make sure it's exactly the same command
          sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.GRAY + "Deleting world " + ChatColor.YELLOW + worldName + ChatColor.GRAY + "...");
          // No need to do this synchronously
          CommandManager.getInstance().getWorldsInUse().add(worldName);
          Bukkit.getScheduler().runTaskAsynchronously(SWMPlugin.getInstance(), () -> {
            try {
              if (loader.isWorldLocked(worldName)) {
                sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "World " + worldName + " is being used on another server.");
                return;
              }
              final long start = System.currentTimeMillis();
              loader.deleteWorld(worldName);
              // Now let's delete it from the config file
              WorldsConfig.removeWorld(worldName);
              sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.GREEN + "World " + ChatColor.YELLOW + worldName
                + ChatColor.GREEN + " deleted in " + (System.currentTimeMillis() - start) + "ms!");
            } catch (final IOException ex) {
              if (!(sender instanceof ConsoleCommandSender)) {
                sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "Failed to delete world " + worldName
                  + ". Take a look at the server console for more information.");
              }
              Logging.error("Failed to delete world " + worldName + ". Stack trace:");
              ex.printStackTrace();
            } catch (final UnknownWorldException ex) {
              sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "Data source " + source + " does not contain any world called " + worldName + ".");
            } finally {
              CommandManager.getInstance().getWorldsInUse().remove(worldName);
            }
          });
          return true;
        }
      }
      sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + ChatColor.BOLD + "WARNING: " + ChatColor.GRAY + "You're about to delete " +
        "world " + ChatColor.YELLOW + worldName + ChatColor.GRAY + ". This action cannot be undone.");
      sender.sendMessage(" ");
      sender.sendMessage(ChatColor.GRAY + "If you are sure you want to continue, type again this command.");
        this.deleteCache.put(sender.getName(), args);
      return true;
    }
    return false;
  }

  @Override
  public List<String> onTabComplete(final CommandSender sender, final String[] args) {
    List<String> toReturn = null;
    final String typed = args[1].toLowerCase(Locale.ROOT);
    if (args.length == 2) {
      for (final World world : Bukkit.getWorlds()) {
        final String worldName = world.getName();
        if (worldName.toLowerCase(Locale.ROOT).startsWith(typed)) {
          if (toReturn == null) {
            toReturn = new LinkedList<>();
          }
          toReturn.add(worldName);
        }
      }
      return toReturn;
    }
    if (args.length == 3) {
      toReturn = new LinkedList<>(LoaderUtils.getAvailableLoadersNames());
    }
    if (args.length == 4) {
      toReturn = new LinkedList<>(LoaderUtils.getAvailableLoadersNames());
    }
    return toReturn == null ? Collections.emptyList() : toReturn;
  }
}
