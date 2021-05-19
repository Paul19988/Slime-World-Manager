package com.grinderwolf.swm.nms.v1_16_R3;

import com.grinderwolf.swm.clsm.CLSMBridge;
import com.grinderwolf.swm.clsm.ClassModifier;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.minecraft.server.v1_16_R3.Chunk;
import net.minecraft.server.v1_16_R3.DedicatedServer;
import net.minecraft.server.v1_16_R3.IChunkAccess;
import net.minecraft.server.v1_16_R3.MinecraftServer;
import net.minecraft.server.v1_16_R3.ProtoChunkExtension;
import net.minecraft.server.v1_16_R3.WorldServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class CraftCLSMBridge implements CLSMBridge {

  private final v1_16_R3SlimeNMS nmsInstance;

  static void initialize(@NotNull final v1_16_R3SlimeNMS instance) {
    ClassModifier.setLoader(new CraftCLSMBridge(instance));
  }

  @NotNull
  @Override
  public Object getChunk(@NotNull final Object world, final int x, final int z) {
    return ((CustomWorldServer) world).getChunk(x, z);
  }

  @Nullable
  @Override
  public Object getDefaultGamemode() {
    if (this.nmsInstance.isLoadingDefaultWorlds()) {
      return ((DedicatedServer) MinecraftServer.getServer()).getDedicatedServerProperties().gamemode;
    }
    return null;
  }

  @Override
  public Object @Nullable [] getDefaultWorlds() {
    final WorldServer defaultWorld = this.nmsInstance.getDefaultWorld();
    final WorldServer netherWorld = this.nmsInstance.getDefaultNetherWorld();
    final WorldServer endWorld = this.nmsInstance.getDefaultEndWorld();
    if (defaultWorld != null || netherWorld != null || endWorld != null) {
      return new WorldServer[]{defaultWorld, netherWorld, endWorld};
    }
    // Returning null will just run the original load world method
    return null;
  }

  @Override
  public boolean isCustomWorld(@NotNull final Object world) {
    return world instanceof CustomWorldServer;
  }

  @Override
  public boolean saveChunk(@NotNull final Object world, @NotNull final Object chunkAccess) {
    if (!(world instanceof CustomWorldServer)) {
      return false; // Returning false will just run the original saveChunk method
    }
    if (!(chunkAccess instanceof ProtoChunkExtension ||
      chunkAccess instanceof Chunk) ||
      !((IChunkAccess) chunkAccess).isNeedsSaving()) {
      // We're only storing fully-loaded chunks that need to be saved
      return true;
    }
    final Chunk chunk;
    if (chunkAccess instanceof ProtoChunkExtension) {
      chunk = ((ProtoChunkExtension) chunkAccess).u();
    } else {
      chunk = (Chunk) chunkAccess;
    }
    ((CustomWorldServer) world).saveChunk(chunk);
    chunk.setNeedsSaving(false);
    return true;
  }

  @Override
  public boolean skipWorldAdd(@NotNull final Object world) {
    if (!this.isCustomWorld(world) || this.nmsInstance.isLoadingDefaultWorlds()) {
      return false;
    }
    final CustomWorldServer worldServer = (CustomWorldServer) world;
    return !worldServer.isReady();
  }
}
