package com.grinderwolf.swm.plugin.commands.sub;

import com.grinderwolf.swm.api.loaders.SlimeLoader;
import com.grinderwolf.swm.api.world.SlimeWorld;
import com.grinderwolf.swm.plugin.SWMPlugin;
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
import org.jetbrains.annotations.NotNull;

@Getter
public final class DSListCmd implements Subcommand {

  private static final int MAX_ITEMS_PER_PAGE = 5;

  private final String description = "List all worlds inside a data source.";

  private final String permission = "swm.dslist";

  private final String usage = "dslist <data-source> [page]";

  private static boolean isLoaded(@NotNull final SlimeLoader loader, @NotNull final String worldName) {
    final World world = Bukkit.getWorld(worldName);
    if (world != null) {
      final SlimeWorld slimeWorld = SWMPlugin.getInstance().getNms().getSlimeWorld(world);
      if (slimeWorld != null) {
        return loader.equals(slimeWorld.getLoader());
      }
    }
    return false;
  }

  @Override
  public boolean onCommand(final CommandSender sender, final String[] args) {
    if (args.length > 0) {
      final int page;
      if (args.length == 1) {
        page = 1;
      } else {
        final String pageString = args[1];
        try {
          page = Integer.parseInt(pageString);
          if (page < 1) {
            throw new NumberFormatException();
          }
        } catch (final NumberFormatException ex) {
          sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "'" + pageString + "' is not a valid number.");
          return true;
        }
      }
      final String source = args[0];
      final SlimeLoader loader = LoaderUtils.getLoader(source);
      if (loader == null) {
        sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "Unknown data source " + source + ".");
        return true;
      }
      Bukkit.getScheduler().runTaskAsynchronously(SWMPlugin.getInstance(), () -> {
        final List<String> worldList;
        try {
          worldList = loader.listWorlds();
        } catch (final IOException ex) {
          if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "Failed to load world list. Take a look at the server console for more information.");
          }
          Logging.error("Failed to load world list:");
          ex.printStackTrace();
          return;
        }
        if (worldList.isEmpty()) {
          sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "There are no worlds stored in data source " + source + ".");
          return;
        }
        final int offset = (page - 1) * DSListCmd.MAX_ITEMS_PER_PAGE;
        final double d = worldList.size() / (double) DSListCmd.MAX_ITEMS_PER_PAGE;
        final int maxPages = (int) d + (d > (int) d ? 1 : 0);
        if (offset >= worldList.size()) {
          sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "There " + (maxPages == 1 ? "is" :
            "are") + " only " + maxPages + " page" + (maxPages == 1 ? "" : "s") + "!");
          return;
        }
        worldList.sort(String::compareTo);
        sender.sendMessage(Logging.COMMAND_PREFIX + "World list " + ChatColor.YELLOW + "[" + page + "/" + maxPages + "]" + ChatColor.GRAY + ":");
        for (int i = offset; i - offset < DSListCmd.MAX_ITEMS_PER_PAGE && i < worldList.size(); i++) {
          final String world = worldList.get(i);
          sender.sendMessage(ChatColor.GRAY + " - " + (DSListCmd.isLoaded(loader, world) ? ChatColor.GREEN : ChatColor.RED) + world);
        }
      });
      return true;
    }
    return false;
  }

  @Override
  public List<String> onTabComplete(final CommandSender sender, final String[] args) {
    if (args.length == 2) {
      return new LinkedList<>(LoaderUtils.getAvailableLoadersNames());
    }
    return Collections.emptyList();
  }
}
