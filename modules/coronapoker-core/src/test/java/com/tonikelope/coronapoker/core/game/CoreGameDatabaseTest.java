package com.tonikelope.coronapoker.core.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tonikelope.coronapoker.core.DatabaseService;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
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
}
