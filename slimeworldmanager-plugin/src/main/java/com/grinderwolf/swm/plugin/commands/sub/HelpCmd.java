package com.grinderwolf.swm.plugin.commands.sub;

import com.grinderwolf.swm.plugin.commands.CommandManager;
import com.grinderwolf.swm.plugin.log.Logging;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@Getter
public final class HelpCmd implements Subcommand {

  private final String description = "Shows this page.";

  private final String usage = "help";

  @Override
  public boolean onCommand(final CommandSender sender, final String[] args) {
    sender.sendMessage(Logging.COMMAND_PREFIX + "Command list:");
    for (final Subcommand cmd : CommandManager.getInstance().getCommands()) {
      if (cmd.inGameOnly() && !(sender instanceof Player) || !cmd.getPermission().equals("") && !sender.hasPermission(cmd.getPermission()) && !sender.hasPermission("swm.*")) {
        continue;
      }
      sender.sendMessage(ChatColor.GRAY + "  -" + ChatColor.AQUA + "/swm " + cmd.getUsage() + ChatColor.GRAY + " - " + cmd.getDescription());
    }
    return true;
  }

  @Override
  public List<String> onTabComplete(final CommandSender sender, final String[] args) {
    return Collections.emptyList();
  }
}
