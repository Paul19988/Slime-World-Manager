package com.grinderwolf.swm.plugin.log;

import lombok.experimental.UtilityClass;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.jetbrains.annotations.NotNull;

@UtilityClass
public class Logging {

  public final String COMMAND_PREFIX = ChatColor.BLUE + ChatColor.BOLD.toString() + "SWM " + ChatColor.GRAY + ">> ";

  private final String CONSOLE_PREFIX = ChatColor.BLUE + "[SWM] ";

  public void error(@NotNull final String message) {
    Bukkit.getConsoleSender().sendMessage(Logging.CONSOLE_PREFIX + ChatColor.RED + message);
  }

  public void info(@NotNull final String message) {
    Bukkit.getConsoleSender().sendMessage(Logging.CONSOLE_PREFIX + ChatColor.GRAY + message);
  }

  public void warning(@NotNull final String message) {
    Bukkit.getConsoleSender().sendMessage(Logging.CONSOLE_PREFIX + ChatColor.YELLOW + message);
  }
}
