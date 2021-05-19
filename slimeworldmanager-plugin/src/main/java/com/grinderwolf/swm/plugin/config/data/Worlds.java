package com.grinderwolf.swm.plugin.config.data;

import io.github.portlek.configs.ConfigHolder;
import io.github.portlek.configs.configuration.ConfigurationSection;
import io.github.portlek.configs.loaders.DataSerializer;
import io.github.portlek.configs.loaders.SectionFieldLoader;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@RequiredArgsConstructor
@Getter
public final class Worlds implements DataSerializer, Map<String, WorldData> {

  @NotNull
  @Delegate
  private final Map<String, WorldData> worlds;

  @NotNull
  public static Worlds deserialize(@NotNull final ConfigurationSection section) {
    return new Worlds(section.getKeys(false).stream()
      .map(section::getSectionOrCreate)
      .map(WorldData::deserialize)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .collect(Collectors.toMap(WorldData::getWorldName, world -> world, (a, b) -> b)));
  }

  @Override
  public void serialize(@NotNull final ConfigurationSection section) {
    this.forEach((s, worldData) ->
      worldData.serialize(section.getSectionOrCreate(s)));
  }

  public static final class Loader extends SectionFieldLoader<Worlds> {

    public static final Func INSTANCE = Loader::new;

    private Loader(@NotNull final ConfigHolder holder, @NotNull final ConfigurationSection section) {
      super(holder, section, Worlds.class);
    }

    @NotNull
    @Override
    public Optional<Worlds> toFinal(@NotNull final ConfigurationSection section, @NotNull final String path,
                                    @Nullable final Worlds fieldValue) {
      return Optional.of(Worlds.deserialize(section.getSectionOrCreate(path)));
    }
  }
}
