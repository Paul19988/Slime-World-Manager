package com.grinderwolf.swm.plugin.commands;

import com.grinderwolf.swm.plugin.commands.sub.CloneWorldCmd;
import com.grinderwolf.swm.plugin.commands.sub.CreateWorldCmd;
import com.grinderwolf.swm.plugin.commands.sub.DSListCmd;
import com.grinderwolf.swm.plugin.commands.sub.DeleteWorldCmd;
import com.grinderwolf.swm.plugin.commands.sub.GotoCmd;
import com.grinderwolf.swm.plugin.commands.sub.HelpCmd;
import com.grinderwolf.swm.plugin.commands.sub.ImportWorldCmd;
import com.grinderwolf.swm.plugin.commands.sub.LoadTemplateWorldCmd;
import com.grinderwolf.swm.plugin.commands.sub.LoadWorldCmd;
import com.grinderwolf.swm.plugin.commands.sub.MigrateWorldCmd;
import com.grinderwolf.swm.plugin.commands.sub.ReloadConfigCmd;
import com.grinderwolf.swm.plugin.commands.sub.Subcommand;
import com.grinderwolf.swm.plugin.commands.sub.UnloadWorldCmd;
import com.grinderwolf.swm.plugin.commands.sub.VersionCmd;
import com.grinderwolf.swm.plugin.commands.sub.WorldListCmd;
import com.grinderwolf.swm.plugin.log.Logging;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public final class CommandManager implements TabExecutor {

  @Getter
  private static CommandManager instance;

  private final Map<String, Subcommand> commands = new HashMap<>();

  /* A list containing all the worlds that are being performed operations on, so two commands cannot be run at the same time */
  @Getter
  private final Set<String> worldsInUse = new HashSet<>();

  public CommandManager(@NotNull final Plugin plugin) {
    CommandManager.instance = this;
    this.commands.put("help", new HelpCmd());
    this.commands.put("version", new VersionCmd());
    this.commands.put("goto", new GotoCmd());
    this.commands.put("load", new LoadWorldCmd());
    this.commands.put("load-template", new LoadTemplateWorldCmd());
    this.commands.put("clone-world", new CloneWorldCmd());
    this.commands.put("unload", new UnloadWorldCmd());
    this.commands.put("list", new WorldListCmd());
    this.commands.put("dslist", new DSListCmd());
    this.commands.put("migrate", new MigrateWorldCmd());
    this.commands.put("delete", new DeleteWorldCmd());
    this.commands.put("import", new ImportWorldCmd());
    this.commands.put("reload", new ReloadConfigCmd(plugin));
    this.commands.put("create", new CreateWorldCmd());
  }

  public Collection<Subcommand> getCommands() {
    return this.commands.values();
  }

  @Override
  public boolean onCommand(@NotNull final CommandSender sender, @NotNull final Command command,
                           @NotNull final String label, final String[] args) {
    if (args.length == 0) {
      sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.AQUA + "Slime World Manager" + ChatColor.GRAY + " is a plugin that implements the Slime Region Format, " +
        "designed by the Hypixel Dev Team to load and save worlds more efficiently. To check out the help page, type "
        + ChatColor.YELLOW + "/swm help" + ChatColor.GRAY + ".");
      return true;
    }
    final Subcommand subcommand = this.commands.get(args[0]);
    if (subcommand == null) {
      sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "Unknown subcommand. To check out the help page, type " + ChatColor.GRAY + "/swm help" + ChatColor.RED + ".");
      return true;
    }
    if (subcommand.inGameOnly() && !(sender instanceof Player)) {
      sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "This subcommand can only be run in-game.");
      return true;
    }
    if (!subcommand.getPermission().equals("") && !sender.hasPermission(subcommand.getPermission()) && !sender.hasPermission("swm.*")) {
      sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "You do not have permission to perform this subcommand.");
      return true;
    }
    final String[] subCmdArgs = new String[args.length - 1];
    System.arraycopy(args, 1, subCmdArgs, 0, subCmdArgs.length);
    if (!subcommand.onCommand(sender, subCmdArgs)) {
      sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "Command usage: /swm " + ChatColor.GRAY + subcommand.getUsage() + ChatColor.RED + ".");
    }
    return true;
  }

  @Override
  public List<String> onTabComplete(@NotNull final CommandSender sender, @NotNull final Command command,
                                    @NotNull final String alias, final String[] args) {
    List<String> toReturn = null;
    final String typed = args[0].toLowerCase(Locale.ROOT);
    if (args.length == 1) {
      for (final Map.Entry<String, Subcommand> entry : this.commands.entrySet()) {
        final String name = entry.getKey();
        final Subcommand subcommand = entry.getValue();
        if (name.startsWith(typed) && !subcommand.getPermission().equals("")
          && (sender.hasPermission(subcommand.getPermission()) || sender.hasPermission("swm.*"))) {
          if (name.equalsIgnoreCase("goto") && sender instanceof ConsoleCommandSender) {
            continue;
          }
          if (toReturn == null) {
            toReturn = new LinkedList<>();
          }
          toReturn.add(name);
        }
      }
    }
    if (args.length > 1) {
      final String subName = args[0];
      final Subcommand subcommand = this.commands.get(subName);
      if (subcommand != null) {
        toReturn = subcommand.onTabComplete(sender, args);
      }
    }
    return toReturn == null ? Collections.emptyList() : toReturn;
  }
}
