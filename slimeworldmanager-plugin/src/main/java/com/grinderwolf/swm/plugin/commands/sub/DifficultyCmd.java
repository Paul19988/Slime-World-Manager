package com.grinderwolf.swm.plugin.commands.sub;

import java.util.List;
import org.bukkit.command.CommandSender;

public final class DifficultyCmd implements Subcommand {

  private final String description = "Changes world difficulty";

  private final String permission = "swm.difficulty";

  private final String usage = "difficulty <difficulty> (<world>)";

  @Override
  public boolean onCommand(final CommandSender sender, final String[] args) {
    return false;
  }

  @Override
  public List<String> onTabComplete(final CommandSender sender, final String[] args) {
    return null;
  }

  @Override
  public String getUsage() {
    return null;
  }

  @Override
  public String getDescription() {
    return null;
  }
}
