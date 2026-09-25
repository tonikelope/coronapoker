package com.tonikelope.coronapoker.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.game.CoreGameDatabase;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class StatsRepositoryTest {

    @TempDir
    Path temporary;

    @Test
    void exposesTheClassicHistoryWithoutAnySwingDependency() throws Exception {
        DatabaseService database = new DatabaseService(
                temporary.resolve("stats.db").toString());
        database.start();
        try {
            new CoreGameDatabase(database);
            seed(database);
            StatsRepository repository = new StatsRepository(database);

            StatsRepository.GameSummary game = repository.games().get(0);
            assertEquals(7, game.id());
            assertEquals("server", game.server());
            assertEquals(java.util.List.of("server", "invitado"),
                    game.players());
            assertEquals(1, game.hands());
            assertTrue(game.imported());

            StatsRepository.HandSummary hand = repository.hands(7).get(0);
            assertEquals(3, hand.counter());
            assertEquals(java.util.List.of("A_C", "K_D", "Q_T"),
                    hand.communityCards());
            assertEquals(java.util.List.of("server", "invitado"),
                    hand.preflopPlayers());

            var balances = repository.balances(7, null);
            assertEquals(2, balances.size());
            assertEquals("server", balances.get(0).player());
            assertEquals(2d, balances.get(0).profit(), 0.0001d);
            assertEquals(20d, balances.get(0).roiPercent(), 0.0001d);
            var history = repository.balanceHistory(7);
            assertEquals(2, history.size());
            assertEquals("invitado", history.get(0).player());
            assertEquals(3, history.get(0).handCounter());
            assertEquals(8d, history.get(0).stack(), 0.0001d);

            var showdown = repository.showdown(11).get(0);
            assertTrue(showdown.winner());
            assertEquals(java.util.List.of("A_C", "A_D"),
                    showdown.holeCards());

            assertEquals(3d, repository.averageResponse(7, null)
                    .get(0).value(), 0.0001d);
            assertEquals(100d, repository.raiseFrequency(7, 1)
                    .get(0).value(), 0.0001d);
            assertEquals("server", repository.performance(7).get(0).player());
            assertEquals(20d, repository.performance(7).get(0).roiPercent(),
                    0.0001d);
            assertEquals("server", repository.bestHands(7).get(0).player());

            repository.setPrivate(7, true);
            assertTrue(repository.games().get(0).privateGame());
            seedSecondGame(database);
            assertEquals(2, repository.setPrivate(
                    java.util.List.of(7, 8), false));
            assertTrue(repository.games().stream()
                    .noneMatch(StatsRepository.GameSummary::privateGame));
            assertEquals(1, repository.deleteGames(java.util.List.of(8)));
            assertEquals(1, repository.games().size());
            assertEquals(1, repository.deleteImportedGames());
            assertTrue(repository.games().isEmpty());
        } finally {
            database.close();
        }
    }

    @Test
    void legacyPlainPlayerNamesRemainReadable() {
        assertEquals(java.util.List.of("plain"),
                StatsRepository.decodePlayers("plain"));
        assertFalse(StatsRepository.splitCards("").iterator().hasNext());
    }

    private static void seed(DatabaseService database) throws Exception {
        String players = encoded("server") + "#" + encoded("invitado");
        try (PreparedStatement game = database.connection().prepareStatement(
                "INSERT INTO game(id,start,end,play_time,server,players,buyin,sb,"
                + "blinds_time,rebuy,blinds_time_type,local,private,imported,"
                + "imported_from) VALUES(7,1000,9000,8,'server',?,10,0.1,5,1,1,1,0,1,'peer')")) {
            game.setString(1, players);
            game.executeUpdate();
        }
        try (PreparedStatement hand = database.connection().prepareStatement(
                "INSERT INTO hand(id,id_game,counter,sbval,blinds_double,dealer,sb,bb,"
                + "start,end,com_cards,preflop_players,flop_players,turn_players,"
                + "river_players,pot) VALUES(11,7,3,0.1,0,'server','server',"
                + "'invitado',2000,8000,'A_C#K_D#Q_T',?,?,?, ?,20)")) {
            hand.setString(1, players);
            hand.setString(2, players);
            hand.setString(3, encoded("server"));
            hand.setString(4, encoded("server"));
            hand.executeUpdate();
        }
        try (PreparedStatement balance = database.connection().prepareStatement(
                "INSERT INTO balance(id,id_hand,player,stack,buyin,rebuy_count) "
                + "VALUES(1,11,'server',12,10,0),(2,11,'invitado',8,10,0)")) {
            balance.executeUpdate();
        }
        try (PreparedStatement showdown = database.connection().prepareStatement(
                "INSERT INTO showdown(id,id_hand,player,hole_cards,hand_cards,hand_val,"
                + "winner,pay,profit) VALUES(1,11,'server','A_C#A_D',"
                + "'A_C#A_D#K_D#Q_T',100,1,20,2)")) {
            showdown.executeUpdate();
        }
        try (PreparedStatement action = database.connection().prepareStatement(
                "INSERT INTO action(id,id_hand,player,counter,round,action,bet,"
                + "conta_raise,response_time) VALUES"
                + "(1,11,'server',1,1,3,1,1,2),"
                + "(2,11,'server',2,1,2,1,1,4),"
                + "(3,11,'invitado',1,1,2,1,0,1)")) {
            action.executeUpdate();
        }
    }

    private static void seedSecondGame(DatabaseService database)
            throws Exception {
        try (PreparedStatement game = database.connection().prepareStatement(
                "INSERT INTO game(id,start,end,play_time,server,players,buyin,sb,"
                + "blinds_time,rebuy,blinds_time_type,local,private,imported) "
                + "VALUES(8,500,600,1,'other',?,10,0.1,5,1,1,1,0,0)")) {
            game.setString(1, encoded("invitado"));
            game.executeUpdate();
        }
    }

    private static String encoded(String value) {
        return Base64.getEncoder().encodeToString(
                value.getBytes(StandardCharsets.UTF_8));
    }
}
