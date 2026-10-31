package com.tonikelope.coronapoker.core.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.DatabaseService;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CoreGameDatabaseTest {

    @TempDir
    Path temporary;

    @Test
    void seatProvenanceMigrationTrustsOnlyExistingLocalHistory()
            throws Exception {
        try (DatabaseService database = new DatabaseService(
                temporary.resolve("old-schema.sqlite").toString())) {
            database.start();
            try (Statement statement = database.connection().createStatement()) {
                statement.execute("CREATE TABLE game(id INTEGER PRIMARY KEY, "
                        + "imported INTEGER NOT NULL DEFAULT 0)");
                statement.execute("CREATE TABLE hand(id INTEGER PRIMARY KEY, "
                        + "id_game INTEGER, counter INTEGER)");
                statement.execute("INSERT INTO game(id, imported) VALUES (1,0),(2,1)");
                statement.execute("INSERT INTO hand(id,id_game,counter) VALUES "
                        + "(10,1,1),(20,2,1)");
            }

            new CoreGameDatabase(database);

            try (Statement statement = database.connection().createStatement();
                    ResultSet rs = statement.executeQuery(
                            "SELECT id,seats_verified FROM game ORDER BY id")) {
                rs.next();
                assertEquals(1, rs.getInt("id"));
                assertEquals(1, rs.getInt("seats_verified"));
                rs.next();
                assertEquals(2, rs.getInt("id"));
                assertEquals(0, rs.getInt("seats_verified"));
            }
        }
    }

    @Test
    void newGameRowsStartWithoutBorrowedSeatProvenance() throws Exception {
        try (DatabaseService database = new DatabaseService(
                temporary.resolve("new-schema.sqlite").toString())) {
            database.start();
            new CoreGameDatabase(database);
            try (Statement statement = database.connection().createStatement()) {
                statement.execute("INSERT INTO game(id) VALUES (1)");
                try (ResultSet rs = statement.executeQuery(
                        "SELECT seats_verified FROM game WHERE id=1")) {
                    rs.next();
                    assertEquals(0, rs.getInt("seats_verified"));
                }
            }
        }
    }

    @Test
    void disputedHandMigrationAddsReceiptIdentityManifest()
            throws Exception {
        try (DatabaseService database = new DatabaseService(
                temporary.resolve("old-disputes.sqlite").toString())) {
            database.start();
            try (Statement statement = database.connection().createStatement()) {
                statement.execute("CREATE TABLE disputed_hands("
                        + "id INTEGER PRIMARY KEY, id_hand INTEGER NOT NULL, "
                        + "timestamp INTEGER NOT NULL, receipts BLOB NOT NULL, "
                        + "local_h BLOB NOT NULL, reason TEXT)");
            }

            new CoreGameDatabase(database);

            Set<String> found = new HashSet<>();
            try (Statement statement = database.connection().createStatement();
                    ResultSet rs = statement.executeQuery(
                            "PRAGMA table_info(disputed_hands)")) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    if (Set.of("receipt_nicks", "evidence_text",
                            "evidence_path").contains(name)) {
                        found.add(name);
                        assertEquals(1, rs.getInt("notnull"));
                    }
                }
            }
            assertTrue(found.containsAll(Set.of("receipt_nicks",
                    "evidence_text", "evidence_path")));
        }
    }
}
