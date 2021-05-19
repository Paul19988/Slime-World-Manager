package com.grinderwolf.swm.plugin.loaders.mongo;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.grinderwolf.swm.api.exceptions.UnknownWorldException;
import com.grinderwolf.swm.api.exceptions.WorldInUseException;
import com.grinderwolf.swm.plugin.config.DatasourceConfig;
import com.grinderwolf.swm.plugin.loaders.LoaderUtils;
import com.grinderwolf.swm.plugin.loaders.UpdatableLoader;
import com.grinderwolf.swm.plugin.log.Logging;
import com.mongodb.MongoException;
import com.mongodb.MongoNamespace;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;

public final class MongoLoader extends UpdatableLoader {

  // World locking executor service
  private static final ScheduledExecutorService SERVICE = Executors.newScheduledThreadPool(2,
    new ThreadFactoryBuilder().setNameFormat("SWM MongoDB Lock Pool Thread #%1$d").build());

  private final MongoClient client;

  private final String collection;

  private final String database;

  private final Map<String, ScheduledFuture<?>> lockedWorlds = new HashMap<>();

  public MongoLoader() throws MongoException {
    this.database = DatasourceConfig.MongoDB.database;
    this.collection = DatasourceConfig.MongoDB.collection;
    final String authParams = !DatasourceConfig.MongoDB.username.isEmpty() && !DatasourceConfig.MongoDB.password.isEmpty()
      ? DatasourceConfig.MongoDB.username + ":" + DatasourceConfig.MongoDB.password + "@"
      : "";
    final String authSource = !DatasourceConfig.MongoDB.auth.isEmpty()
      ? "/?authSource=" + DatasourceConfig.MongoDB.auth
      : "";
    final String uri = !DatasourceConfig.MongoDB.uri.isEmpty()
      ? DatasourceConfig.MongoDB.uri
      : "mongodb://" + authParams + DatasourceConfig.MongoDB.host + ":" + DatasourceConfig.MongoDB.port + authSource;
    this.client = MongoClients.create(uri);
    final MongoDatabase mongoDatabase = this.client.getDatabase(this.database);
    final MongoCollection<Document> mongoCollection = mongoDatabase.getCollection(this.collection);
    mongoCollection.createIndex(Indexes.ascending("name"), new IndexOptions().unique(true));
  }

  @Override
  public void deleteWorld(@NotNull final String worldName) throws IOException, UnknownWorldException {
    final ScheduledFuture<?> future = this.lockedWorlds.remove(worldName);
    if (future != null) {
      future.cancel(false);
    }
    try {
      final MongoDatabase mongoDatabase = this.client.getDatabase(this.database);
      final GridFSBucket bucket = GridFSBuckets.create(mongoDatabase, this.collection);
      GridFSFile file = bucket.find(Filters.eq("filename", worldName)).first();
      if (file == null) {
        throw new UnknownWorldException(worldName);
      }
      bucket.delete(file.getObjectId());
      // Delete backup file
      file = bucket.find(Filters.eq("filename", worldName + "_backup")).first();
      if (file != null) {
        bucket.delete(file.getObjectId());
      }
      final MongoCollection<Document> mongoCollection = mongoDatabase.getCollection(this.collection);
      mongoCollection.deleteOne(Filters.eq("name", worldName));
    } catch (final MongoException ex) {
      throw new IOException(ex);
    }
  }

  @Override
  public boolean isWorldLocked(@NotNull final String worldName) throws IOException, UnknownWorldException {
    if (this.lockedWorlds.containsKey(worldName)) {
      return true;
    }
    try {
      final MongoDatabase mongoDatabase = this.client.getDatabase(this.database);
      final MongoCollection<Document> mongoCollection = mongoDatabase.getCollection(this.collection);
      final Document worldDoc = mongoCollection.find(Filters.eq("name", worldName)).first();
      if (worldDoc == null) {
        throw new UnknownWorldException(worldName);
      }
      return System.currentTimeMillis() - worldDoc.getLong("locked") <= LoaderUtils.MAX_LOCK_TIME;
    } catch (final MongoException ex) {
      throw new IOException(ex);
    }
  }

  @NotNull
  @Override
  public List<String> listWorlds() throws IOException {
    final List<String> worldList = new ArrayList<>();
    try {
      final MongoDatabase mongoDatabase = this.client.getDatabase(this.database);
      final MongoCollection<Document> mongoCollection = mongoDatabase.getCollection(this.collection);
      final MongoCursor<Document> documents = mongoCollection.find().cursor();
      while (documents.hasNext()) {
        worldList.add(documents.next().getString("name"));
      }
    } catch (final MongoException ex) {
      throw new IOException(ex);
    }
    return worldList;
  }

  @Override
  public byte[] loadWorld(@NotNull final String worldName, final boolean readOnly) throws UnknownWorldException,
    IOException, WorldInUseException {
    try {
      final MongoDatabase mongoDatabase = this.client.getDatabase(this.database);
      final MongoCollection<Document> mongoCollection = mongoDatabase.getCollection(this.collection);
      final Document worldDoc = mongoCollection.find(Filters.eq("name", worldName)).first();
      if (worldDoc == null) {
        throw new UnknownWorldException(worldName);
      }
      if (!readOnly) {
        final long lockedMillis = worldDoc.getLong("locked");
        if (System.currentTimeMillis() - lockedMillis <= LoaderUtils.MAX_LOCK_TIME) {
          throw new WorldInUseException(worldName);
        }
        this.updateLock(worldName, true);
      }
      final GridFSBucket bucket = GridFSBuckets.create(mongoDatabase, this.collection);
      final ByteArrayOutputStream stream = new ByteArrayOutputStream();
      bucket.downloadToStream(worldName, stream);
      return stream.toByteArray();
    } catch (final MongoException ex) {
      throw new IOException(ex);
    }
  }

  @Override
  public void saveWorld(@NotNull final String worldName, final byte[] serializedWorld, final boolean lock)
    throws IOException {
    try {
      final MongoDatabase mongoDatabase = this.client.getDatabase(this.database);
      final GridFSBucket bucket = GridFSBuckets.create(mongoDatabase, this.collection);
      final GridFSFile oldFile = bucket.find(Filters.eq("filename", worldName)).first();
      if (oldFile != null) {
        bucket.rename(oldFile.getObjectId(), worldName + "_backup");
      }
      bucket.uploadFromStream(worldName, new ByteArrayInputStream(serializedWorld));
      final MongoCollection<Document> mongoCollection = mongoDatabase.getCollection(this.collection);
      final Document worldDoc = mongoCollection.find(Filters.eq("name", worldName)).first();
      final long lockMillis = lock ? System.currentTimeMillis() : 0L;
      if (worldDoc == null) {
        mongoCollection.insertOne(new Document().append("name", worldName).append("locked", lockMillis));
      } else if (System.currentTimeMillis() - worldDoc.getLong("locked") > LoaderUtils.MAX_LOCK_TIME && lock) {
        this.updateLock(worldName, true);
      }
    } catch (final MongoException ex) {
      throw new IOException(ex);
    }
  }

  @Override
  public void unlockWorld(@NotNull final String worldName) throws IOException, UnknownWorldException {
    final ScheduledFuture<?> future = this.lockedWorlds.remove(worldName);
    if (future != null) {
      future.cancel(false);
    }
    try {
      final MongoDatabase mongoDatabase = this.client.getDatabase(this.database);
      final MongoCollection<Document> mongoCollection = mongoDatabase.getCollection(this.collection);
      final UpdateResult result = mongoCollection.updateOne(Filters.eq("name", worldName), Updates.set("locked", 0L));
      if (result.getMatchedCount() == 0) {
        throw new UnknownWorldException(worldName);
      }
    } catch (final MongoException ex) {
      throw new IOException(ex);
    }
  }

  @Override
  public boolean worldExists(@NotNull final String worldName) throws IOException {
    try {
      final MongoDatabase mongoDatabase = this.client.getDatabase(this.database);
      final MongoCollection<Document> mongoCollection = mongoDatabase.getCollection(this.collection);
      final Document worldDoc = mongoCollection.find(Filters.eq("name", worldName)).first();
      return worldDoc != null;
    } catch (final MongoException ex) {
      throw new IOException(ex);
    }
  }

  @Override
  public void update() {
    final MongoDatabase mongoDatabase = this.client.getDatabase(this.database);
    // Old GridFS importing
    for (final String collectionName : mongoDatabase.listCollectionNames()) {
      if (collectionName.equals(this.collection + "_files.files") ||
        collectionName.equals(this.collection + "_files.chunks")) {
        Logging.info("Updating MongoDB database...");
        mongoDatabase.getCollection(this.collection + "_files.files")
          .renameCollection(new MongoNamespace(this.database, this.collection + ".files"));
        mongoDatabase.getCollection(this.collection + "_files.chunks")
          .renameCollection(new MongoNamespace(this.database, this.collection + ".chunks"));
        Logging.info("MongoDB database updated!");
        break;
      }
    }
    final MongoCollection<Document> mongoCollection = mongoDatabase.getCollection(this.collection);
    // Old world lock importing
    final MongoCursor<Document> documents = mongoCollection.find(Filters.or(Filters.eq("locked", true),
      Filters.eq("locked", false))).cursor();
    if (documents.hasNext()) {
      Logging.warning("Your SWM MongoDB database is outdated. The update process will start in 10 seconds.");
      Logging.warning("Note that this update will make your database incompatible with older SWM versions.");
      Logging.warning("Make sure no other servers with older SWM versions are using this database.");
      Logging.warning("Shut down the server to prevent your database from being updated.");
      try {
        Thread.sleep(10000L);
      } catch (final InterruptedException ignored) {
      }
      while (documents.hasNext()) {
        final String worldName = documents.next().getString("name");
        mongoCollection.updateOne(Filters.eq("name", worldName), Updates.set("locked", 0L));
      }
    }
  }

  private void updateLock(final String worldName, final boolean forceSchedule) {
    try {
      final MongoDatabase mongoDatabase = this.client.getDatabase(this.database);
      final MongoCollection<Document> mongoCollection = mongoDatabase.getCollection(this.collection);
      mongoCollection.updateOne(Filters.eq("name", worldName), Updates.set("locked", System.currentTimeMillis()));
    } catch (final MongoException ex) {
      Logging.error("Failed to update the lock for world " + worldName + ":");
      ex.printStackTrace();
    }
    if (forceSchedule || this.lockedWorlds.containsKey(worldName)) { // Only schedule another update if the world is still on the map
      this.lockedWorlds.put(worldName, MongoLoader.SERVICE.schedule(() ->
        this.updateLock(worldName, false), LoaderUtils.LOCK_INTERVAL, TimeUnit.MILLISECONDS));
    }
  }
}
