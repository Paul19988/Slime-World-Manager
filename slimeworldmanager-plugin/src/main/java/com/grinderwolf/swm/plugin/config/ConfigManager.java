package com.grinderwolf.swm.plugin.config;

import com.grinderwolf.swm.plugin.SWMPlugin;
import io.leangen.geantyref.TypeToken;
import lombok.AccessLevel;
import lombok.Getter;
import org.spongepowered.configurate.loader.HeaderMode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class ConfigManager {

    private static final File PLUGIN_DIR = new File("plugins", "SlimeWorldManager");
    private static final File MAIN_FILE = new File(PLUGIN_DIR, "main.yml");
    private static final File WORLDS_FILE = new File(PLUGIN_DIR, "worlds.yml");
    private static final File SOURCES_FILE = new File(PLUGIN_DIR, "sources.yml");

    @Getter
    private static MainConfig mainConfig;
    @Getter(value = AccessLevel.PACKAGE)
    private static YamlConfigurationLoader mainConfigLoader;

    @Getter
    private static WorldsConfig worldConfig;
    @Getter(value = AccessLevel.PACKAGE)
    private static YamlConfigurationLoader worldConfigLoader;

    @Getter
    private static DatasourcesConfig datasourcesConfig;

    public static void initialize() throws IOException {
        copyDefaultConfigs();

        mainConfigLoader = YamlConfigurationLoader.builder().path(MAIN_FILE.toPath())
                .nodeStyle(NodeStyle.BLOCK).headerMode(HeaderMode.PRESERVE).build();
        mainConfig = mainConfigLoader.load().get(TypeToken.get(MainConfig.class));

        worldConfigLoader = YamlConfigurationLoader.builder().path(WORLDS_FILE.toPath())
                .nodeStyle(NodeStyle.BLOCK).headerMode(HeaderMode.PRESERVE).build();
        worldConfig = worldConfigLoader.load().get(TypeToken.get(WorldsConfig.class));

        YamlConfigurationLoader datasourcesConfigLoader = YamlConfigurationLoader.builder().path(SOURCES_FILE.toPath())
                .nodeStyle(NodeStyle.BLOCK).headerMode(HeaderMode.PRESERVE).build();
        datasourcesConfig = datasourcesConfigLoader.load().get(TypeToken.get(DatasourcesConfig.class));

        mainConfigLoader.save(mainConfigLoader.createNode().set(TypeToken.get(MainConfig.class), mainConfig));
        worldConfig.save();
        datasourcesConfigLoader.save(datasourcesConfigLoader.createNode().set(TypeToken.get(DatasourcesConfig.class), datasourcesConfig));
    }

    private static void copyDefaultConfigs() throws IOException {
        PLUGIN_DIR.mkdirs();

        if (!MAIN_FILE.exists()) {
            Files.copy(SWMPlugin.getInstance().getResource("main.yml"), MAIN_FILE.toPath());
        }

        if (!WORLDS_FILE.exists()) {
            Files.copy(SWMPlugin.getInstance().getResource("worlds.yml"), WORLDS_FILE.toPath());
        }

        if (!SOURCES_FILE.exists()) {
            Files.copy(SWMPlugin.getInstance().getResource("worlds.yml"), SOURCES_FILE.toPath());
        }
    }
}
