package com.grinderwolf.swm.plugin.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.grinderwolf.swm.plugin.SWMPlugin;
import com.grinderwolf.swm.plugin.config.ConfigManager;
import com.grinderwolf.swm.plugin.config.MainConfig;
import com.grinderwolf.swm.plugin.log.Logging;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.NotNull;

public final class Updater implements Listener {

  private final boolean outdatedVersion;

  public Updater() {
    final String currentVersionString = SWMPlugin.getInstance().getDescription().getVersion();
    if (currentVersionString.equals("${project.version}")) {
      Logging.warning("You are using a custom version of SWM. Update checking is disabled.");
      this.outdatedVersion = false;
      return;
    }
    final Version currentVersion = new Version(currentVersionString);
    if (currentVersion.getTag().toLowerCase(Locale.ROOT).endsWith("snapshot")) {
      Logging.warning("You are using a snapshot version of SWM. Update checking is disabled.");
      this.outdatedVersion = false;
      return;
    }
    Logging.info("Checking for updates...");
    final Version latestVersion;
    try {
      latestVersion = new Version(Updater.getLatestVersion());
    } catch (final IOException ex) {
      Logging.error("Failed to check for updates:");
      this.outdatedVersion = false;
      ex.printStackTrace();
      return;
    }
    final int result = latestVersion.compareTo(currentVersion);
    this.outdatedVersion = result > 0;
    if (result == 0) {
      Logging.info("You are running the latest version of Slime World Manager.");
    } else if (this.outdatedVersion) {
      Logging.warning("You are running an outdated version of Slime World Manager. Please download the latest version at SpigotMC.org.");
    } else {
      Logging.warning("You are running an unreleased version of Slime World Manager.");
    }
  }

  @NotNull
  private static String getLatestVersion() throws IOException {
    final URL url = new URL("https://api.spiget.org/v2/resources/87209/versions/latest?" + System.currentTimeMillis());
    final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.addRequestProperty("User-Agent", "SWM " + SWMPlugin.getInstance().getDescription().getVersion());
    connection.setUseCaches(true);
    connection.setDoOutput(true);
    final StringBuilder content = new StringBuilder();
    try (final BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
      String input;
      while ((input = br.readLine()) != null) {
        content.append(input);
      }
    }
    final JsonObject statistics = new JsonParser().parse(content.toString()).getAsJsonObject();
    return statistics.get("name").getAsString();
  }

  @EventHandler
  public void onPlayerJoin(final PlayerJoinEvent event) {
    final Player player = event.getPlayer();
    if (this.outdatedVersion && MainConfig.Update.onJoinMessage && player.hasPermission("swm.updater")) {
      player.sendMessage(Logging.COMMAND_PREFIX + "This server is running an outdated of Slime World Manager. Please download the latest version at SpigotMC.org.");
    }
  }
}
