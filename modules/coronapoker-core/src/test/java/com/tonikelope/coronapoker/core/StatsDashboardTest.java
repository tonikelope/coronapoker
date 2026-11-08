/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.game.CoreGameDatabase;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class StatsDashboardTest {

    @TempDir
    Path temporary;

    @Test
    void derivesRatesAndCentsFromSettledHistoryWithoutCountingForcedChecks() throws Exception {
        DatabaseService database = new DatabaseService(temporary.resolve("dashboard.db").toString());
        database.start();
        try {
            new CoreGameDatabase(database);
            seed(database);
            StatsDashboard dashboard = new StatsRepository(database).dashboard();

            assertEquals(List.of("Alice", "Bob", "Carol"), dashboard.players(0));
            assertEquals(List.of("Carol"), dashboard.players(2));
            var alice = dashboard.view("Alice", 0, null, null);
            assertEquals(1, alice.sessionCount());
            assertEquals(1, alice.timedSessions());
            assertEquals(2, alice.hands());
            assertEquals(8d, alice.net());
            assertEquals(16d, alice.hourly());
            assertEquals(8d, alice.sessions().get(0).running());
            assertEquals(new StatsDashboard.Rate(50d, 1, 2), alice.rates().vpip());
            assertEquals(new StatsDashboard.Rate(50d, 1, 2), alice.rates().pfr());
            assertEquals(new StatsDashboard.Rate(2d, 2, 1), alice.rates().af());
            assertEquals(new StatsDashboard.Rate(50d, 1, 2), alice.rates().wtsd());
            assertEquals(new StatsDashboard.Rate(100d, 1, 1), alice.rates().wsd());
            assertEquals(List.of(new StatsDashboard.Bucket("AKs", 1, 5d, 5d)), alice.cards());
            assertEquals(List.of(new StatsDashboard.Bucket("BTN", 1, 5d, 5d),
                    new StatsDashboard.Bucket("BB", 1, 3d, 3d)), alice.positions());
            assertTrue(alice.comparison().isEmpty());

            var bob = dashboard.view("Bob", 1, null, null);
            assertEquals(new StatsDashboard.Rate(100d, 1, 1), bob.rates().threeBet());
            assertEquals(new StatsDashboard.Rate(0d, 0, 1), bob.rates().wsd());
            assertEquals(-8d, bob.net());
            assertTrue(bob.cards().isEmpty());

            var firstDate = Instant.ofEpochMilli(1764676800000L).atZone(ZoneId.systemDefault()).toLocalDate();
            var onDate = dashboard.view("Alice", 1, firstDate, firstDate);
            assertEquals(8d, onDate.net());
            assertEquals(0, dashboard.view("Alice", 1, firstDate.plusDays(1), null).sessionCount());
            var untracked = dashboard.view("Carol", 2, null, null);
            assertNull(untracked.net());
            assertNull(untracked.hourly());
            assertNull(untracked.rates().vpip().value());
        } finally {
            database.close();
        }
    }

    @Test
    void classifiesTheCanonicalCardWireFormat() {
        assertEquals("AA", StatsDashboard.handClass("A_C#A_D"));
        assertEquals("ATo", StatsDashboard.handClass("10_D#A_P"));
        assertEquals("KTs", StatsDashboard.handClass("K_T#10_T"));
        assertNull(StatsDashboard.handClass("A_D#A_D"));
        assertNull(StatsDashboard.handClass("A_X#A_D"));
    }

    @Test
    void classicViewsReuseTheNativeMetricsAndFilterBySessionStart() throws Exception {
        DatabaseService database = new DatabaseService(temporary.resolve("classic.db").toString());
        database.start();
        try {
            new CoreGameDatabase(database);
            seed(database);
            try (Statement sql = database.connection().createStatement()) {
                sql.executeUpdate("UPDATE action SET response_time=3 WHERE round=1");
                sql.executeUpdate("UPDATE action SET response_time=5 WHERE round>1");
            }
            StatsRepository repository = new StatsRepository(database);
            var firstDate = Instant.ofEpochMilli(1764676800000L).atZone(ZoneId.systemDefault()).toLocalDate();
            var balance = repository.classic(StatsRepository.ClassicMode.BALANCE, null, firstDate, firstDate);
            assertEquals(repository.balances(1, null), balance.balances());
            var stack = repository.classic(StatsRepository.ClassicMode.BALANCE, 1, firstDate, firstDate);
            assertEquals(repository.balanceHistory(1), stack.stacks());
            assertEquals(repository.performance(1), repository.classic(
                    StatsRepository.ClassicMode.PERFORMANCE, null, firstDate, firstDate).performance());
            assertEquals(repository.averageResponse(1, null), repository.classic(
                    StatsRepository.ClassicMode.RESPONSE, null, firstDate, firstDate).metrics());
            var winningHands = repository.classic(
                    StatsRepository.ClassicMode.BEST_HANDS, null, firstDate, firstDate);
            assertEquals(repository.bestHands(1), winningHands.bestHands());
            assertEquals(List.of(new StatsRepository.RankCount(2, 1)), winningHands.winningRanks());
            var preflop = repository.classic(StatsRepository.ClassicMode.PREFLOP, null, firstDate, firstDate);
            assertEquals(repository.raiseFrequency(1, 1).stream()
                    .filter(row -> !"Carol".equals(row.player())).toList(), preflop.metrics());
            assertTrue(repository.classic(StatsRepository.ClassicMode.BALANCE,
                    1, firstDate.plusDays(1), null).balances().isEmpty());
            assertTrue(repository.classic(StatsRepository.ClassicMode.BEST_HANDS,
                    1, null, firstDate.minusDays(1)).bestHands().isEmpty());
        } finally {
            database.close();
        }
    }

    private static void seed(DatabaseService database) throws Exception {
        String headsUp = encoded("Bob") + "#" + encoded("Alice");
        String threeWay = encoded("Alice") + "#" + encoded("Bob") + "#" + encoded("Carol");
        try (Statement statement = database.connection().createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO game(id,start,end,play_time) VALUES
                    (1,1764676800000,1764678600000,1800),
                    (2,1764763200000,NULL,NULL)
                    """);
            // Hand IDs are deliberately out of chronological order.
            statement.executeUpdate("""
                    INSERT INTO hand(id,id_game,counter,sbval,dealer,sb,bb,end,pot,preflop_players,flop_players) VALUES
                    (20,1,1,0.5,'Alice','Bob','Carol',1,18,'%s','%s'),
                    (10,1,2,0.5,'Bob','Bob','Alice',2,2,'%s','%s'),
                    (30,1,3,0.5,'Bob','Bob','Alice',3,0,'%s','%s'),
                    (40,2,1,0.5,'Carol','Carol','Carol',4,1,'%s',NULL)
                    """.formatted(threeWay, headsUp, headsUp, headsUp, headsUp, headsUp, encoded("Carol")));
            statement.executeUpdate("""
                    INSERT INTO action(id_hand,player,counter,round,action,bet) VALUES
                    (20,'Alice',1,1,3,3),(20,'Bob',2,1,3,8),(20,'Carol',3,1,1,1),(20,'Alice',4,1,2,8),
                    (20,'Alice',5,2,3,2),(20,'Bob',6,2,2,2),(20,'Bob',7,3,3,1),(20,'Alice',8,3,2,1),
                    (10,'Bob',1,1,2,1),(10,'Alice',2,1,2,1),(10,'Alice',3,2,3,1),(10,'Bob',4,2,1,0),
                    (30,'Alice',1,1,3,5)
                    """);
            statement.executeUpdate("""
                    INSERT INTO balance(id_hand,player,stack,buyin) VALUES
                    (20,'Alice',15,10),(20,'Bob',6,10),(20,'Carol',9,10),
                    (10,'Alice',28,20),(10,'Bob',2,10),(30,'Alice',100,20)
                    """);
            statement.executeUpdate("""
                    INSERT INTO showdown(id_hand,player,winner,hole_cards,hand_val) VALUES
                    (20,'Alice',1,'A_D#K_D',2),(20,'Bob',0,NULL,-1),(10,'Alice',1,NULL,-1)
                    """);
        }
    }

    private static String encoded(String name) {
        return Base64.getEncoder().encodeToString(name.getBytes(StandardCharsets.UTF_8));
    }
}
