package com.grinderwolf.swm.plugin.commands.sub;

import java.util.List;
import org.bukkit.command.CommandSender;

public interface Subcommand {

  String getDescription();

  default String getPermission() {
    return "";
  }

  String getUsage();

  default boolean inGameOnly() {
    return false;
  }

  boolean onCommand(CommandSender sender, String[] args);

  List<String> onTabComplete(CommandSender sender, String[] args);
}
