package com.grinderwolf.swm.plugin.commands.sub;

import com.grinderwolf.swm.plugin.config.ConfigManager;
import com.grinderwolf.swm.plugin.log.Logging;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

@Getter
@RequiredArgsConstructor
public final class ReloadConfigCmd implements Subcommand {

  private final String description = "Reloads the config files.";

  private final String permission = "swm.reload";

  @NotNull
  private final Plugin plugin;

  private final String usage = "reload";

  @Override
  public boolean onCommand(final CommandSender sender, final String[] args) {
    ConfigManager.initialize(this.plugin);
    sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.GREEN + "Config reloaded.");
    return true;
  }

  @Override
  public List<String> onTabComplete(final CommandSender sender, final String[] args) {
    return Collections.emptyList();
  }
}
