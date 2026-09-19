/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import com.tonikelope.coronapoker.HandCreateTransaction;
import com.tonikelope.coronapoker.core.DatabaseService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Game persistence adapter backed by the process-owned database service. */
public final class CoreGameDatabase implements GameDatabase {

    private static final Logger LOGGER = Logger.getLogger(
            CoreGameDatabase.class.getName());
    private final DatabaseService database;
    private final Object lock = new Object();
    private final int recoveryGameId;
    private final Supplier<String> recoverySettings;

    public CoreGameDatabase(DatabaseService database) throws SQLException {
        this(database, -1);
    }

    public CoreGameDatabase(DatabaseService database, int recoveryGameId)
            throws SQLException {
        this(database, recoveryGameId, null);
    }

    public CoreGameDatabase(DatabaseService database, int recoveryGameId,
            Supplier<String> recoverySettings) throws SQLException {
        this.database = Objects.requireNonNull(database, "database");
        this.recoveryGameId = recoveryGameId;
        this.recoverySettings = recoverySettings;
        ensureSchema(database.connection());
    }

    @Override public Object lock() { return lock; }
    @Override public Connection connection() throws SQLException {
        return database.connection();
    }
    @Override public int recoveryGameId() { return recoveryGameId; }

    @Override
    public void persistRecoverySettings(int gameId) {
        if (gameId <= 0 || recoverySettings == null) return;
        synchronized (lock) {
            try (PreparedStatement statement = connection().prepareStatement(
                    "UPDATE game SET recover_settings=? WHERE id=?")) {
                statement.setQueryTimeout(30);
                statement.setString(1, recoverySettings.get());
                statement.setInt(2, gameId);
                statement.executeUpdate();
            } catch (RuntimeException | SQLException failure) {
                LOGGER.log(Level.SEVERE,
                        "Failed to persist neutral recovery settings", failure);
            }
        }
    }

    private static void ensureSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(30);
            statement.execute("CREATE TABLE IF NOT EXISTS game(id INTEGER PRIMARY KEY, start INTEGER, end INTEGER, play_time INTEGER, server TEXT, players TEXT, buyin INTEGER, sb REAL, blinds_time INTEGER, rebuy INTEGER, last_deck TEXT, blinds_time_type INTEGER, ugi TEXT, local INTEGER NOT NULL DEFAULT 0, recover_settings TEXT, private INTEGER NOT NULL DEFAULT 0, imported INTEGER NOT NULL DEFAULT 0, imported_from TEXT)");
            statement.execute("CREATE TABLE IF NOT EXISTS hand(id INTEGER PRIMARY KEY, id_game INTEGER, counter INTEGER, sbval REAL, blinds_double INTEGER, dealer TEXT, sb TEXT, bb TEXT, start INTEGER, end INTEGER, com_cards TEXT, preflop_players TEXT, flop_players TEXT, turn_players TEXT, river_players TEXT, pot REAL, hand_id_b64 TEXT, FOREIGN KEY(id_game) REFERENCES game(id) ON DELETE CASCADE)");
            statement.execute("CREATE TABLE IF NOT EXISTS action(id INTEGER PRIMARY KEY, id_hand INTEGER, player TEXT, counter INTEGER, round INTEGER, action INTEGER, bet REAL, conta_raise INTEGER, response_time INTEGER, record_b64 TEXT, sig_b64 TEXT, FOREIGN KEY(id_hand) REFERENCES hand(id) ON DELETE CASCADE)");
            statement.execute("CREATE TABLE IF NOT EXISTS showdown(id INTEGER PRIMARY KEY, id_hand INTEGER, player TEXT, hole_cards TEXT, hand_cards TEXT, hand_val INTEGER, winner INTEGER, pay REAL, profit REAL, FOREIGN KEY(id_hand) REFERENCES hand(id) ON DELETE CASCADE)");
            statement.execute("CREATE TABLE IF NOT EXISTS balance(id INTEGER PRIMARY KEY, id_hand INTEGER, player TEXT, stack REAL, buyin INTEGER, rebuy_count INTEGER NOT NULL DEFAULT 0, FOREIGN KEY(id_hand) REFERENCES hand(id) ON DELETE CASCADE)");
            statement.execute("CREATE TABLE IF NOT EXISTS showcards(id INTEGER PRIMARY KEY, id_hand INTEGER, player TEXT, parguela INTEGER, FOREIGN KEY(id_hand) REFERENCES hand(id) ON DELETE CASCADE)");
            statement.execute("CREATE TABLE IF NOT EXISTS permutationkey(id INTEGER PRIMARY KEY, hash TEXT, key TEXT)");
            statement.execute("CREATE TABLE IF NOT EXISTS hand_state(id_game INTEGER PRIMARY KEY, payload TEXT, FOREIGN KEY(id_game) REFERENCES game(id) ON DELETE CASCADE)");
            statement.execute("CREATE TABLE IF NOT EXISTS known_identities(nick TEXT PRIMARY KEY, pubkey BLOB NOT NULL, first_seen INTEGER NOT NULL, last_seen INTEGER NOT NULL, sessions_count INTEGER NOT NULL DEFAULT 0, verified_oob INTEGER NOT NULL DEFAULT 0)");
            statement.execute("CREATE TABLE IF NOT EXISTS disputed_hands(id INTEGER PRIMARY KEY, id_hand INTEGER NOT NULL, timestamp INTEGER NOT NULL, receipts BLOB NOT NULL, local_h BLOB NOT NULL, reason TEXT, FOREIGN KEY(id_hand) REFERENCES hand(id) ON DELETE CASCADE)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_hand_game ON hand(id_game)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_action_hand ON action(id_hand)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_showdown_hand ON showdown(id_hand)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_balance_hand ON balance(id_hand)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_showcards_hand ON showcards(id_hand)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_action_round_action_player ON action(round, action, player, id_hand)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_action_player_hand ON action(player, id_hand)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_showdown_player_winner ON showdown(player, winner, id_hand)");
        }
        HandCreateTransaction.ensureUniqueBalanceRows(connection);
    }
}
