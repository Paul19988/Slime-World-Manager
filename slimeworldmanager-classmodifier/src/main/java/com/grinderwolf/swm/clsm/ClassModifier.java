package com.grinderwolf.swm.clsm;

import com.mojang.datafixers.util.Either;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class serves as a bridge between the SWM and the Minecraft server.
 * <p>
 * As plugins are loaded using a different ClassLoader, their code cannot
 * be accessed from a NMS method. Because of this, it's impossible to make
 * any calls to any method when rewriting the bytecode of a NMS class.
 * <p>
 * As a workaround, this bridge simply calls a method of the {@link CLSMBridge} interface,
 * which is implemented by the SWM plugin when loaded.
 */
public class ClassModifier {

  // Required for Paper 1.13 as javassist can't compile this class
  public static final BooleanSupplier BOOLEAN_SUPPLIER = () -> true;

  @Nullable
  private static CLSMBridge customLoader;

  @Nullable
  public static Object getDefaultGamemode() {
    return ClassModifier.customLoader != null ? ClassModifier.customLoader.getDefaultGamemode() : null;
  }

  public static Object @Nullable [] getDefaultWorlds() {
    return ClassModifier.customLoader != null
      ? ClassModifier.customLoader.getDefaultWorlds()
      : null;
  }

  @Nullable
  public static CompletableFuture<Either<?, ?>> getFutureChunk(@NotNull final Object world, final int x, final int z) {
    if (ClassModifier.customLoader == null || !ClassModifier.isCustomWorld(world)) {
      return null;
    }
    return CompletableFuture.supplyAsync(() ->
      Either.left(ClassModifier.customLoader.getChunk(world, x, z))
    );
  }

  public static boolean isCustomWorld(@NotNull final Object world) {
    return ClassModifier.customLoader != null && ClassModifier.customLoader.isCustomWorld(world);
  }

  public static boolean saveChunk(@NotNull final Object world, @NotNull final Object chunkAccess) {
    return ClassModifier.customLoader != null && ClassModifier.customLoader.saveChunk(world, chunkAccess);
  }

  public static void setLoader(@NotNull final CLSMBridge loader) {
    ClassModifier.customLoader = loader;
  }

  public static boolean skipWorldAdd(@NotNull final Object world) {
    return ClassModifier.customLoader != null && ClassModifier.customLoader.skipWorldAdd(world);
  }
}
