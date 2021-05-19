package com.grinderwolf.swm.plugin.loaders.file;

import com.grinderwolf.swm.api.exceptions.UnknownWorldException;
import com.grinderwolf.swm.api.loaders.SlimeLoader;
import com.grinderwolf.swm.plugin.log.Logging;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.NotDirectoryException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;

public final class FileLoader implements SlimeLoader {

  private static final FilenameFilter WORLD_FILE_FILTER = (dir, name) -> name.endsWith(".slime");

  @NotNull
  private final File worldDir;

  private final Map<String, RandomAccessFile> worldFiles = Collections.synchronizedMap(new HashMap<>());

  public FileLoader(@NotNull final File worldDir) {
    this.worldDir = worldDir;
    if (worldDir.exists() && !worldDir.isDirectory()) {
      Logging.warning("A file named '" + worldDir.getName() + "' has been deleted, as this is the name used for the worlds directory.");
      worldDir.delete();
    }
    worldDir.mkdirs();
  }

  @Override
  public void deleteWorld(@NotNull final String worldName) throws UnknownWorldException {
    if (!this.worldExists(worldName)) {
      throw new UnknownWorldException(worldName);
    } else {
      try {
        final RandomAccessFile worldFile = this.worldFiles.get(worldName);
        System.out.println("Deleting world.. " + worldName + ".");
        final RandomAccessFile randomAccessFile = this.worldFiles.get(worldName);
        this.unlockWorld(worldName);
        FileUtils.forceDelete(new File(this.worldDir, worldName + ".slime"));
        if (randomAccessFile != null) {
          System.out.print("Attempting to delete worldData " + worldName + ".");
          worldFile.seek(0); // Make sure we're at the start of the file
          worldFile.setLength(0); // Delete old data
          worldFile.write(null);
          randomAccessFile.close();
          this.worldFiles.remove(worldName);
        }
        System.out.println("World.. " + worldName + " deleted.");
      } catch (final IOException e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  public boolean isWorldLocked(@NotNull final String worldName) throws IOException {
    RandomAccessFile file = this.worldFiles.get(worldName);
    if (file == null) {
      file = new RandomAccessFile(new File(this.worldDir, worldName + ".slime"), "rw");
    }
    if (file.getChannel().isOpen()) {
      file.close();
    } else {
      return true;
    }
    return false;
  }

  @NotNull
  @Override
  public List<String> listWorlds() throws NotDirectoryException {
    final String[] worlds = this.worldDir.list(FileLoader.WORLD_FILE_FILTER);
    if (worlds == null) {
      throw new NotDirectoryException(this.worldDir.getPath());
    }
    return Arrays.stream(worlds).map(c -> c.substring(0, c.length() - 6)).collect(Collectors.toList());
  }

  @Override
  public byte[] loadWorld(@NotNull final String worldName, final boolean readOnly) throws UnknownWorldException,
    IOException {
    if (!this.worldExists(worldName)) {
      throw new UnknownWorldException(worldName);
    }
    final RandomAccessFile file = this.worldFiles.computeIfAbsent(worldName, world -> {
      try {
        return new RandomAccessFile(new File(this.worldDir, worldName + ".slime"), "rw");
      } catch (final FileNotFoundException ex) {
        return null; // This is never going to happen as we've just checked if the world exists
      }
    });
    if (!readOnly) {
      if (file != null && file.getChannel().isOpen()) {
        System.out.print("World is unlocked");
      }
    }
    if (file != null && file.length() > Integer.MAX_VALUE) {
      throw new IndexOutOfBoundsException("World is too big!");
    }
    byte[] serializedWorld = new byte[0];
    if (file != null) {
      serializedWorld = new byte[(int) file.length()];
      file.seek(0); // Make sure we're at the start of the file
      file.readFully(serializedWorld);
    }
    return serializedWorld;
  }

  @Override
  public void saveWorld(@NotNull final String worldName, final byte[] serializedWorld, final boolean lock)
    throws IOException {
    RandomAccessFile worldFile = this.worldFiles.get(worldName);
    final boolean tempFile = worldFile == null;
    if (tempFile) {
      worldFile = new RandomAccessFile(new File(this.worldDir, worldName + ".slime"), "rw");
    }
    worldFile.seek(0); // Make sure we're at the start of the file
    worldFile.setLength(0); // Delete old data
    worldFile.write(serializedWorld);
    if (lock) {
      final FileChannel channel = worldFile.getChannel();
      try {
        channel.tryLock();
      } catch (final OverlappingFileLockException ignored) {
      }
    }
    if (tempFile) {
      worldFile.close();
    }
  }

  @Override
  public void unlockWorld(@NotNull final String worldName) throws UnknownWorldException, IOException {
    if (!this.worldExists(worldName)) {
      throw new UnknownWorldException(worldName);
    }
    final RandomAccessFile file = this.worldFiles.remove(worldName);
    if (file != null) {
      final FileChannel channel = file.getChannel();
      if (channel.isOpen()) {
        file.close();
      }
    }
  }

  @Override
  public boolean worldExists(@NotNull final String worldName) {
    return new File(this.worldDir, worldName + ".slime").exists();
  }
}
