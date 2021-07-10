package com.grinderwolf.swm.plugin.config;

import com.grinderwolf.swm.plugin.log.Logging;
import io.leangen.geantyref.TypeToken;
import lombok.Data;
import lombok.Getter;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.io.IOException;

@Data
@ConfigSerializable
public class MainConfig {
    @Setting("updater")
    private UpdaterOptions updaterOptions = new UpdaterOptions();

    @Getter
    @ConfigSerializable
    public static class UpdaterOptions {

        @Setting(value = "enabled")
        private final boolean enabled = true;

        @Setting(value = "onjoinmessage")
        private final boolean messageEnabled = true;
    }

    public void save() {
        try {
            ConfigManager.getMainConfigLoader().save(ConfigManager.getMainConfigLoader().createNode().set(TypeToken.get(MainConfig.class), this));
        } catch (IOException ex) {
            Logging.error("Failed to save worlds config file:");
            ex.printStackTrace();
        }
    }
}
