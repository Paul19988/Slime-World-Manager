package com.grinderwolf.swm.plugin.config;

import io.github.portlek.configs.ConfigHolder;
import io.github.portlek.configs.ConfigLoader;
import io.github.portlek.configs.yaml.YamlType;
import lombok.Getter;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

@Getter
public final class DatasourceConfig implements ConfigHolder {

  public static File file = new File();

  public static MongoDB mongodb = new MongoDB();

  public static Mysql mysql = new Mysql();

  public static void load(@NotNull final Plugin plugin) {
    ConfigLoader.builder("sources", plugin.getDataFolder(), YamlType.get())
      .setConfigHolder(new DatasourceConfig())
      .build()
      .load(true);
  }

  @Getter
  public static class File implements ConfigHolder {

    public static String path = "slime_worlds";
  }

  @Getter
  public static final class MongoDB implements ConfigHolder {

    public static String auth = "admin";

    public static String collection = "worlds";

    public static String database = "slimeworldmanager";

    public static boolean enabled = false;

    public static String host = "127.0.0.1";

    public static String password = "";

    public static int port = 27017;

    public static String uri = "";

    public static String username = "slimeworldmanager";
  }

  @Getter
  public static final class Mysql implements ConfigHolder {

    public static String database = "slimeworldmanager";

    public static boolean enabled = false;

    public static String host = "127.0.0.1";

    public static String password = "";

    public static int port = 3306;

    public static String sqlUrl = "jdbc:mysql://{host}:{port}/{database}?autoReconnect=true&allowMultiQueries=true&useSSL={usessl}";

    public static String username = "slimeworldmanager";

    public static boolean usessl = false;
  }
}
