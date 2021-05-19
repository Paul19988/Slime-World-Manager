package com.grinderwolf.swm.plugin.commands.sub;

import com.grinderwolf.swm.api.exceptions.CorruptedWorldException;
import com.grinderwolf.swm.api.exceptions.NewerFormatException;
import com.grinderwolf.swm.api.exceptions.UnknownWorldException;
import com.grinderwolf.swm.api.exceptions.WorldAlreadyExistsException;
import com.grinderwolf.swm.api.exceptions.WorldInUseException;
import com.grinderwolf.swm.api.loaders.SlimeLoader;
import com.grinderwolf.swm.api.world.SlimeWorld;
import com.grinderwolf.swm.plugin.SWMPlugin;
import com.grinderwolf.swm.plugin.commands.CommandManager;
import com.grinderwolf.swm.plugin.config.WorldsConfig;
import com.grinderwolf.swm.plugin.config.data.WorldData;
import com.grinderwolf.swm.plugin.loaders.LoaderUtils;
import com.grinderwolf.swm.plugin.log.Logging;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

@Getter
public final class CloneWorldCmd implements Subcommand {

  private final String description = "Clones a world";

  private final String permission = "swm.cloneworld";

  private final String usage = "clone-world <template-world> <world-name> [new-data-source]";

  @Override
  public boolean onCommand(final CommandSender sender, final String[] args) {
    if (args.length > 1) {
      final String worldName = args[1];
      final World world = Bukkit.getWorld(worldName);
      if (world != null) {
        sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "World " + worldName + " is already loaded!");
        return true;
      }
      final String templateWorldName = args[0];
      final WorldData worldData = WorldsConfig.worlds.get(templateWorldName);
      if (worldData == null) {
        sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "Failed to find world " + templateWorldName + " inside the worlds config file.");
        return true;
      }
      if (templateWorldName.equals(worldName)) {
        sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "The template world name cannot be the same as the cloned world one!");
        return true;
      }
      if (CommandManager.getInstance().getWorldsInUse().contains(worldName)) {
        sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "World " + worldName + " is already being used on another command! Wait some time and try again.");
        return true;
      }
      final String dataSource = args.length > 2 ? args[2] : worldData.getDataSource();
      final SlimeLoader initLoader = SWMPlugin.getInstance().getLoader(worldData.getDataSource());
      final SlimeLoader loader = SWMPlugin.getInstance().getLoader(dataSource);
      if (initLoader == null) {
        sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "Unknown data source " + worldData.getDataSource() + "!");
        return true;
      }
      if (loader == null) {
        sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "Unknown data source " + dataSource + "!");
        return true;
      }
      CommandManager.getInstance().getWorldsInUse().add(worldName);
      sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.GRAY + "Creating world " + ChatColor.YELLOW + worldName
        + ChatColor.GRAY + " using " + ChatColor.YELLOW + templateWorldName + ChatColor.GRAY + " as a template...");
      // It's best to load the world async, and then just go back to the server thread and add it to the world list
      Bukkit.getScheduler().runTaskAsynchronously(SWMPlugin.getInstance(), () -> {
        try {
          final long start = System.currentTimeMillis();
          final SlimeWorld slimeWorld = SWMPlugin.getInstance().loadWorld(initLoader, templateWorldName, true, worldData.toPropertyMap()).clone(worldName, loader);
          Bukkit.getScheduler().runTask(SWMPlugin.getInstance(), () -> {
            try {
              SWMPlugin.getInstance().generateWorld(slimeWorld);
            } catch (final IllegalArgumentException ex) {
              sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "Failed to generate world " + worldName + ": " + ex.getMessage() + ".");
              return;
            }
            sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.GREEN + "World " + ChatColor.YELLOW + worldName
              + ChatColor.GREEN + " loaded and generated in " + (System.currentTimeMillis() - start) + "ms!");
          });
        } catch (final WorldAlreadyExistsException ex) {
          sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "There is already a world called " + worldName + " stored in " + dataSource + ".");
        } catch (final CorruptedWorldException ex) {
          if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "Failed to load world " + templateWorldName +
              ": world seems to be corrupted.");
          }
          Logging.error("Failed to load world " + templateWorldName + ": world seems to be corrupted.");
          ex.printStackTrace();
        } catch (final NewerFormatException ex) {
          sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "Failed to load world " + templateWorldName + ": this world" +
            " was serialized with a newer version of the Slime Format (" + ex.getMessage() + ") that SWM cannot understand.");
        } catch (final UnknownWorldException ex) {
          sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "Failed to load world " + templateWorldName +
            ": world could not be found (using data source '" + worldData.getDataSource() + "').");
        } catch (final IllegalArgumentException ex) {
          sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "Failed to load world " + templateWorldName +
            ": " + ex.getMessage());
        } catch (final IOException ex) {
          if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "Failed to load world " + templateWorldName
              + ". Take a look at the server console for more information.");
          }
          Logging.error("Failed to load world " + templateWorldName + ":");
          ex.printStackTrace();
        } catch (final WorldInUseException ignored) {
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
    if (args.length == 4) {
      return new LinkedList<>(LoaderUtils.getAvailableLoadersNames());
    }
    return Collections.emptyList();
  }
}
