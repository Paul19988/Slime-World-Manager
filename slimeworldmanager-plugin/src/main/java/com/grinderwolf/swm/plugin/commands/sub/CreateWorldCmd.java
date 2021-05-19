package com.grinderwolf.swm.plugin.commands.sub;

import com.grinderwolf.swm.api.exceptions.WorldAlreadyExistsException;
import com.grinderwolf.swm.api.loaders.SlimeLoader;
import com.grinderwolf.swm.api.world.SlimeWorld;
import com.grinderwolf.swm.api.world.properties.SlimePropertyMap;
import com.grinderwolf.swm.plugin.SWMPlugin;
import com.grinderwolf.swm.plugin.commands.CommandManager;
import com.grinderwolf.swm.plugin.config.WorldsConfig;
import com.grinderwolf.swm.plugin.config.data.WorldData;
import com.grinderwolf.swm.plugin.log.Logging;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

@Getter
public final class CreateWorldCmd implements Subcommand {

  private final String description = "Create an empty world.";

  private final String permission = "swm.createworld";

  private final String usage = "create <world> <data-source>";

  @Override
  public boolean onCommand(final CommandSender sender, final String[] args) {
    if (args.length > 1) {
      final String worldName = args[0];
      if (CommandManager.getInstance().getWorldsInUse().contains(worldName)) {
        sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "World " + worldName + " is already being used on another command! Wait some time and try again.");
        return true;
      }
      final World world = Bukkit.getWorld(worldName);
      if (world != null) {
        sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "World " + worldName + " already exists!");
        return true;
      }
      if (WorldsConfig.worlds.containsKey(worldName)) {
        sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "There is already a world called  " + worldName + " inside the worlds config file.");
        return true;
      }
      final String dataSource = args[1];
      final SlimeLoader loader = SWMPlugin.getInstance().getLoader(dataSource);
      if (loader == null) {
        sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "Unknown data source  " + dataSource + ".");
        return true;
      }
      CommandManager.getInstance().getWorldsInUse().add(worldName);
      sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.GRAY + "Creating empty world " + ChatColor.YELLOW + worldName + ChatColor.GRAY + "...");
      // It's best to load the world async, and then just go back to the server thread and add it to the world list
      Bukkit.getScheduler().runTaskAsynchronously(SWMPlugin.getInstance(), () -> {
        try {
          final long start = System.currentTimeMillis();
          final WorldData worldData = new WorldData(worldName, dataSource, "0, 64, 0");
          final SlimePropertyMap propertyMap = worldData.toPropertyMap();
          final SlimeWorld slimeWorld = SWMPlugin.getInstance().createEmptyWorld(loader, worldName, false, propertyMap);
          Bukkit.getScheduler().runTask(SWMPlugin.getInstance(), () -> {
            try {
              SWMPlugin.getInstance().generateWorld(slimeWorld);
              // Bedrock block
              final Location location = new Location(Bukkit.getWorld(worldName), 0, 61, 0);
              location.getBlock().setType(Material.BEDROCK);
              // Config
              WorldsConfig.addWorld(worldData);
              sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.GREEN + "World " + ChatColor.YELLOW + worldName
                + ChatColor.GREEN + " created in " + (System.currentTimeMillis() - start) + "ms!");
            } catch (final IllegalArgumentException ex) {
              sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "Failed to create world " + worldName + ": " + ex.getMessage() + ".");
            }
          });
        } catch (final WorldAlreadyExistsException ex) {
          sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "Failed to create world " + worldName +
            ": world already exists (using data source '" + dataSource + "').");
        } catch (final IOException ex) {
          if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "Failed to create world " + worldName
              + ". Take a look at the server console for more information.");
          }
          Logging.error("Failed to load world " + worldName + ":");
          ex.printStackTrace();
        } finally {
          CommandManager.getInstance().getWorldsInUse().remove(worldName);
        }
      });
      return true;
    }
    return false;
  }

  @Override
  public List<String> onTabComplete(final CommandSender sender, final String[] args) {
    return Collections.emptyList();
  }
}
