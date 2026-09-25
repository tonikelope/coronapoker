package com.tonikelope.coronapoker.core;

import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/** SQLite-backed identity pinning shared by the native lobby and dealer. */
public final class SqlIdentityTrustStore implements IdentityTrustStore {

    private static final Logger LOGGER = Logger.getLogger(
            SqlIdentityTrustStore.class.getName());
    private final DatabaseService database;

    public SqlIdentityTrustStore(DatabaseService database) {
        this.database = Objects.requireNonNull(database, "database");
        synchronized (database.lock()) {
            try (Statement statement = database.connection().createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS known_identities("
                        + "nick TEXT PRIMARY KEY, pubkey BLOB NOT NULL, "
                        + "first_seen INTEGER NOT NULL, last_seen INTEGER NOT NULL, "
                        + "sessions_count INTEGER NOT NULL DEFAULT 0, "
                        + "verified_oob INTEGER NOT NULL DEFAULT 0)");
            } catch (Exception failure) {
                throw new IllegalStateException(
                        "Cannot initialize the identity trust store", failure);
            }
        }
    }

    @Override
    public Observation observe(String nickname, byte[] publicKey) {
        requireIdentity(nickname, publicKey);
        long now = System.currentTimeMillis() / 1000L;
        synchronized (database.lock()) {
            try {
                byte[] existing = null;
                int sessions = 0;
                try (PreparedStatement query = database.connection().prepareStatement(
                        "SELECT pubkey, sessions_count FROM known_identities WHERE nick=?")) {
                    query.setString(1, nickname);
                    try (ResultSet result = query.executeQuery()) {
                        if (result.next()) {
                            existing = result.getBytes(1);
                            sessions = result.getInt(2);
                        }
                    }
                }
                if (existing == null) {
                    try (PreparedStatement insert = database.connection().prepareStatement(
                            "INSERT INTO known_identities(nick,pubkey,first_seen,last_seen,sessions_count,verified_oob) VALUES(?,?,?,?,1,0)")) {
                        insert.setString(1, nickname);
                        insert.setBytes(2, publicKey);
                        insert.setLong(3, now);
                        insert.setLong(4, now);
                        insert.executeUpdate();
                    }
                    return Observation.NEW;
                }
                boolean matches = MessageDigest.isEqual(existing, publicKey);
                String sql = matches
                        ? "UPDATE known_identities SET last_seen=?, sessions_count=? WHERE nick=?"
                        : "UPDATE known_identities SET last_seen=?, sessions_count=?, pubkey=?, verified_oob=0 WHERE nick=?";
                try (PreparedStatement update = database.connection().prepareStatement(sql)) {
                    update.setLong(1, now);
                    update.setInt(2, sessions + 1);
                    if (matches) {
                        update.setString(3, nickname);
                    } else {
                        update.setBytes(3, publicKey);
                        update.setString(4, nickname);
                    }
                    update.executeUpdate();
                }
                return matches ? Observation.MATCH : Observation.CHANGED;
            } catch (Exception failure) {
                LOGGER.log(Level.SEVERE,
                        "Failed to observe identity for " + nickname, failure);
                return Observation.NEW;
            }
        }
    }

    @Override
    public boolean markVerified(String nickname, byte[] publicKey) {
        if (!validIdentity(nickname, publicKey)) return false;
        synchronized (database.lock()) {
            try (PreparedStatement update = database.connection().prepareStatement(
                    "UPDATE known_identities SET verified_oob=1 WHERE nick=? AND pubkey=?")) {
                update.setString(1, nickname);
                update.setBytes(2, publicKey);
                return update.executeUpdate() == 1;
            } catch (Exception failure) {
                LOGGER.log(Level.SEVERE,
                        "Failed to verify identity for " + nickname, failure);
                return false;
            }
        }
    }

    @Override
    public boolean isVerified(String nickname, byte[] publicKey) {
        if (!validIdentity(nickname, publicKey)) return false;
        synchronized (database.lock()) {
            try (PreparedStatement query = database.connection().prepareStatement(
                    "SELECT pubkey, verified_oob FROM known_identities WHERE nick=?")) {
                query.setString(1, nickname);
                try (ResultSet result = query.executeQuery()) {
                    return result.next() && result.getInt(2) != 0
                            && MessageDigest.isEqual(result.getBytes(1), publicKey);
                }
            } catch (Exception failure) {
                LOGGER.log(Level.SEVERE,
                        "Failed to read identity trust for " + nickname, failure);
                return false;
            }
        }
    }

    private static void requireIdentity(String nickname, byte[] publicKey) {
        if (!validIdentity(nickname, publicKey)) {
            throw new IllegalArgumentException(
                    "Identity requires a nickname and 32-byte Ed25519 key");
        }
    }

    private static boolean validIdentity(String nickname, byte[] publicKey) {
        return nickname != null && !nickname.isBlank()
                && publicKey != null && publicKey.length == 32;
    }
}
