package com.grinderwolf.swm.plugin.commands.sub;

import com.grinderwolf.swm.plugin.SWMPlugin;
import com.grinderwolf.swm.plugin.config.WorldsConfig;
import com.grinderwolf.swm.plugin.log.Logging;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

@Getter
public final class WorldListCmd implements Subcommand {

  private static final int MAX_ITEMS_PER_PAGE = 5;

  private final String description = "List all worlds. To only list slime worlds, use the 'slime' argument.";

  private final String permission = "swm.worldlist";

  private final String usage = "list [slime] [page]";

  @Override
  public boolean onCommand(final CommandSender sender, final String[] args) {
    final Map<String, Boolean> loadedWorlds = Bukkit.getWorlds().stream().collect(Collectors.toMap(World::getName,
      world -> SWMPlugin.getInstance().getNms().getSlimeWorld(world) != null));
    final boolean onlySlime = args.length > 0 && args[0].equalsIgnoreCase("slime");
    if (onlySlime) {
      loadedWorlds.entrySet().removeIf(entry -> !entry.getValue());
    }
    final int page;
    if (args.length == 0 || args.length == 1 && args[0].equalsIgnoreCase("slime")) {
      page = 1;
    } else {
      final String pageString = args[args.length - 1];
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
    final List<String> worldsList = new ArrayList<>(loadedWorlds.keySet());
    WorldsConfig.worlds.keySet().stream()
      .filter(world -> !worldsList.contains(world))
      .forEach(worldsList::add);
    if (worldsList.isEmpty()) {
      sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "There are no worlds configured.");
      return true;
    }
    final int offset = (page - 1) * WorldListCmd.MAX_ITEMS_PER_PAGE;
    final double d = worldsList.size() / (double) WorldListCmd.MAX_ITEMS_PER_PAGE;
    final int maxPages = (int) d + (d > (int) d ? 1 : 0);
    if (offset >= worldsList.size()) {
      sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "There " + (maxPages == 1 ? "is" :
        "are") + " only " + maxPages + " page" + (maxPages == 1 ? "" : "s") + "!");
      return true;
    }
    worldsList.sort(String::compareTo);
    sender.sendMessage(Logging.COMMAND_PREFIX + "World list " + ChatColor.YELLOW + "[" + page + "/" + maxPages + "]" + ChatColor.GRAY + ":");
    for (int i = offset; i - offset < WorldListCmd.MAX_ITEMS_PER_PAGE && i < worldsList.size(); i++) {
      final String world = worldsList.get(i);
      if (loadedWorlds.containsKey(world)) {
        sender.sendMessage(ChatColor.GRAY + " - " + ChatColor.GREEN + world + " " + (loadedWorlds.get(world)
          ? "" : ChatColor.BLUE + ChatColor.ITALIC.toString() + ChatColor.UNDERLINE + "(not in SRF)"));
      } else {
        sender.sendMessage(ChatColor.GRAY + " - " + ChatColor.RED + world);
      }
    }
    return true;
  }

  @Override
  public List<String> onTabComplete(final CommandSender sender, final String[] args) {
    return Collections.emptyList();
  }
}
