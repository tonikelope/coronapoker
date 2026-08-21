package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SqlGameLookupTest {

    @Test
    void missingUgiIsNotReportedAsGameZero() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE game(id INTEGER PRIMARY KEY, ugi TEXT NOT NULL)");
            }

            assertNull(Crupier.findGameIdByUgi(connection, "missing"));
        }
    }

    @Test
    void existingUgiReturnsItsPositiveGameId() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE game(id INTEGER PRIMARY KEY, ugi TEXT NOT NULL)");
                statement.execute("INSERT INTO game(id, ugi) VALUES (42, 'known')");
            }

            assertEquals(42, Crupier.findGameIdByUgi(connection, "known"));
        }
    }

    @Test
    void corruptZeroGameIdIsTreatedAsMissing() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE game(id INTEGER PRIMARY KEY, ugi TEXT NOT NULL)");
                statement.execute("INSERT INTO game(id, ugi) VALUES (0, 'broken')");
            }

            assertNull(Crupier.findGameIdByUgi(connection, "broken"));
        }
    }

    @Test
    void positiveGameWinsOverAStaleZeroRowForTheSameUgi() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE game(id INTEGER PRIMARY KEY, ugi TEXT NOT NULL)");
                statement.execute("INSERT INTO game(id, ugi) VALUES (0, 'same')");
                statement.execute("INSERT INTO game(id, ugi) VALUES (17, 'same')");
            }

            assertEquals(17, Crupier.findGameIdByUgi(connection, "same"));
        }
    }

    @Test
    void sqliteReturnsOnePositiveGeneratedGameId() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE game(id INTEGER PRIMARY KEY AUTOINCREMENT, ugi TEXT NOT NULL)");
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO game(ugi) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, "new-game");
                assertEquals(1, statement.executeUpdate());
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    org.junit.jupiter.api.Assertions.assertTrue(keys.next());
                    org.junit.jupiter.api.Assertions.assertTrue(keys.getInt(1) > 0);
                    org.junit.jupiter.api.Assertions.assertFalse(keys.next());
                }
            }
        }
    }

    @Test
    void missingRecoveryGameIsCreatedOnlyAfterPlayersAreSeated() throws Exception {
        String source = Files.readString(sourceRoot().resolve("Crupier.java"));
        int run = source.indexOf("public void run()");
        int defer = source.indexOf("create_client_recovery_game = true", run);
        int seat = source.indexOf("sentarParticipantes();", run);
        int indexPlayers = source.indexOf("nick2player.put(jugador.getNickname(), jugador)", seat);
        int create = source.indexOf("if (create_client_recovery_game", indexPlayers);
        int insert = source.indexOf("sqlNewGame()", create);

        org.junit.jupiter.api.Assertions.assertTrue(run >= 0 && run < defer);
        org.junit.jupiter.api.Assertions.assertTrue(defer < seat && seat < indexPlayers);
        org.junit.jupiter.api.Assertions.assertTrue(indexPlayers < create && create < insert);
    }

    private static Path sourceRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("src/main/java/com/tonikelope/coronapoker");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("CoronaPoker source root not found");
    }
}
