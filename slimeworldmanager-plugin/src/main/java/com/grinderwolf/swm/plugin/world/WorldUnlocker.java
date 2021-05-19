package com.grinderwolf.swm.plugin.world;

import com.grinderwolf.swm.api.exceptions.UnknownWorldException;
import com.grinderwolf.swm.api.world.SlimeWorld;
import com.grinderwolf.swm.plugin.SWMPlugin;
import com.grinderwolf.swm.plugin.log.Logging;
import java.io.IOException;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldUnloadEvent;
import org.jetbrains.annotations.NotNull;

public class WorldUnlocker implements Listener {

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onWorldUnload(final WorldUnloadEvent event) {
    final SlimeWorld world = SWMPlugin.getInstance().getNms().getSlimeWorld(event.getWorld());
    if (world != null) {
      Bukkit.getScheduler().runTaskAsynchronously(SWMPlugin.getInstance(), () -> this.unlockWorld(world));
    }
  }

  private void unlockWorld(@NotNull final SlimeWorld world) {
    try {
      world.getLoader().unlockWorld(world.getName());
    } catch (final IOException ex) {
      Logging.error("Failed to unlock world " + world.getName() + ". Retrying in 5 seconds. Stack trace:");
      ex.printStackTrace();
      Bukkit.getScheduler().runTaskLaterAsynchronously(SWMPlugin.getInstance(), () -> this.unlockWorld(world), 100);
    } catch (final UnknownWorldException ignored) {
    }
  }
}
