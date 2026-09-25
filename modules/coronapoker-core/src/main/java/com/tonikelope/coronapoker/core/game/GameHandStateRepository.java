/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Durable per-game hand-state fossil used by recovery. */
public interface GameHandStateRepository {

    void save(int gameId, String payload);

    String load(int gameId);

    void delete(int gameId);

    static GameHandStateRepository sql(GameDatabase database) {
        return new SqlRepository(database);
    }

    final class SqlRepository implements GameHandStateRepository {
        private static final Logger LOGGER = Logger.getLogger(SqlRepository.class.getName());
        private final GameDatabase database;

        private SqlRepository(GameDatabase database) {
            this.database = Objects.requireNonNull(database, "database");
        }

        @Override
        public void save(int gameId, String payload) {
            if (gameId <= 0) {
                return;
            }
            synchronized (database.lock()) {
                try (PreparedStatement statement = database.connection().prepareStatement(
                        "INSERT OR REPLACE INTO hand_state(id_game, payload) VALUES (?, ?)")) {
                    statement.setQueryTimeout(30);
                    statement.setInt(1, gameId);
                    statement.setString(2, payload);
                    statement.executeUpdate();
                } catch (SQLException failure) {
                    LOGGER.log(Level.SEVERE, "Failed to save hand fossil", failure);
                }
            }
        }

        @Override
        public String load(int gameId) {
            if (gameId <= 0) {
                return null;
            }
            synchronized (database.lock()) {
                try (PreparedStatement statement = database.connection().prepareStatement(
                        "SELECT payload FROM hand_state WHERE id_game=?")) {
                    statement.setQueryTimeout(30);
                    statement.setInt(1, gameId);
                    try (ResultSet result = statement.executeQuery()) {
                        return result.next() ? result.getString("payload") : null;
                    }
                } catch (SQLException failure) {
                    LOGGER.log(Level.SEVERE, "Failed to load hand fossil", failure);
                    return null;
                }
            }
        }

        @Override
        public void delete(int gameId) {
            if (gameId <= 0) {
                return;
            }
            synchronized (database.lock()) {
                try (PreparedStatement statement = database.connection().prepareStatement(
                        "DELETE FROM hand_state WHERE id_game=?")) {
                    statement.setQueryTimeout(30);
                    statement.setInt(1, gameId);
                    statement.executeUpdate();
                } catch (SQLException failure) {
                    LOGGER.log(Level.SEVERE, "Failed to delete hand fossil", failure);
                }
            }
        }
    }
}
