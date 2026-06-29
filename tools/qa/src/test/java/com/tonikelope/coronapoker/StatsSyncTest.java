package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Data-layer tests for {@link StatsSync}: serialization round-trip, primary-key
 * remapping, ugi-based deduplication / idempotency, the manifest set-difference,
 * and — the part that matters most for the "never corrupt the database" rule — a
 * truncation fuzz that proves no malformed blob can leave orphan rows behind.
 *
 * <p>Each test runs against throwaway in-memory SQLite databases, so nothing
 * touches the real {@code ~/.coronapoker/coronapoker.db}.
 */
public class StatsSyncTest {

    @BeforeAll
    static void loadDriver() throws Exception {
        Class.forName("org.sqlite.JDBC");
    }

    // =========================================================================
    // Tests
    // =========================================================================

    @Test
    void listShareableUgis_keepsOnlyFinishedP2pGamesWithKey() throws Exception {
        try (Connection c = mem()) {
            seedRichGame(c, "UGI_OK");                                            // local=0, end set, ugi set
            ins(c, "game", "ugi", "UGI_LOCAL", "local", 1, "start", 1000L, "end", 2000L); // offline
            ins(c, "game", "ugi", "UGI_OPEN", "local", 0, "start", 1000L);                // unfinished (end NULL)
            ins(c, "game", "local", 0, "start", 1000L, "end", 2000L);                     // no ugi (no key)
            ins(c, "game", "ugi", "UGI_PRIVATE", "local", 0, "start", 1000L, "end", 2000L, "private", 1); // marked private

            assertEquals(List.of("UGI_OK"), StatsSync.listShareableUgis(c));
        }
    }

    @Test
    void roundTrip_preservesSubtreeAndRemapsKeys() throws Exception {
        try (Connection src = mem(); Connection dst = mem()) {
            seedRichGame(src, "UGI1");
            // Pre-existing unrelated game in the destination so the auto-increment
            // PKs differ from the source — this is what exercises the remapping.
            seedRichGame(dst, "UGI_PRE");

            byte[] blob = StatsSync.exportGames(src, StatsSync.listShareableUgis(src));
            assertEquals(1, StatsSync.importGames(dst, blob));

            assertEquals(2, count(dst, "game"));
            assertNoOrphans(dst);

            // Re-export the imported game from the destination: the inflated
            // payload must be byte-identical to the source export (PKs dropped,
            // children ordered deterministically) — a faithful round-trip.
            byte[] reblob = StatsSync.exportGames(dst, List.of("UGI1"));
            assertArrayEquals(gunzip(blob), gunzip(reblob));

            // Spot-check that a concrete value and an explicit NULL both survived.
            assertEquals("As Kd Qh", scalarStr(dst,
                    "SELECT com_cards FROM hand h JOIN game g ON h.id_game=g.id WHERE g.ugi='UGI1' AND h.counter=1"));
            assertNull(scalarStr(dst,
                    "SELECT com_cards FROM hand h JOIN game g ON h.id_game=g.id WHERE g.ugi='UGI1' AND h.counter=2"));
        }
    }

    @Test
    void import_isIdempotentByUgi() throws Exception {
        try (Connection src = mem(); Connection dst = mem()) {
            seedRichGame(src, "UGI1");
            byte[] blob = StatsSync.exportGames(src, List.of("UGI1"));

            assertEquals(1, StatsSync.importGames(dst, blob));
            int games = count(dst, "game"), hands = count(dst, "hand"), actions = count(dst, "action");

            assertEquals(0, StatsSync.importGames(dst, blob)); // already known → no-op
            assertEquals(games, count(dst, "game"));
            assertEquals(hands, count(dst, "hand"));
            assertEquals(actions, count(dst, "action"));
            assertNoOrphans(dst);
        }
    }

    @Test
    void manifestDifference_isSetMinus() {
        assertEquals(List.of("b", "c"), StatsSync.difference(List.of("a", "b", "c"), List.of("a", "x")));
        assertEquals(List.of(), StatsSync.difference(List.of("a"), List.of("a", "b")));
        assertEquals(List.of("a", "b"), StatsSync.difference(List.of("a", "b"), List.of()));
    }

    @Test
    void malformedBlob_leavesDatabaseUntouched() throws Exception {
        try (Connection dst = mem()) {
            seedRichGame(dst, "UGI_PRE");
            int g = count(dst, "game"), h = count(dst, "hand"), a = count(dst, "action");

            byte[] garbage = new byte[256];
            new Random(42).nextBytes(garbage);
            assertEquals(0, StatsSync.importGames(dst, garbage));        // not even gzip
            assertEquals(0, StatsSync.importGames(dst, gzip(new byte[]{1, 2, 3}))); // gzip, bad magic

            assertEquals(g, count(dst, "game"));
            assertEquals(h, count(dst, "hand"));
            assertEquals(a, count(dst, "action"));
            assertNoOrphans(dst);
        }
    }

    @Test
    void truncationFuzz_neverCorrupts() throws Exception {
        try (Connection src = mem()) {
            seedRichGame(src, "A");
            seedRichGame(src, "B");
            byte[] inflated = gunzip(StatsSync.exportGames(src, List.of("A", "B")));

            // Cut the logical stream at many points and feed each truncation back
            // in. Invariants at every cut: never throws, the number of games that
            // actually landed equals the number reported, and there are never any
            // orphan child rows.
            for (int cut = inflated.length - 1; cut > 10; cut -= 3) {
                byte[] reblob = gzip(Arrays.copyOf(inflated, cut));
                try (Connection dst = mem()) {
                    int reported = StatsSync.importGames(dst, reblob);
                    assertEquals(reported, count(dst, "game"), "cut=" + cut);
                    assertTrue(reported >= 0 && reported <= 2, "cut=" + cut);
                    assertNoOrphans(dst);
                }
            }
        }
    }

    // =========================================================================
    // Fixtures
    // =========================================================================

    /** Fresh in-memory database with the full stats schema and FKs enforced. */
    private static Connection mem() throws Exception {
        Connection c = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA foreign_keys=ON");
            st.execute("CREATE TABLE game(id INTEGER PRIMARY KEY, start INTEGER, end INTEGER, play_time INTEGER, server TEXT, players TEXT, buyin INTEGER, sb REAL, blinds_time INTEGER, rebuy INTEGER, last_deck TEXT, blinds_time_type INTEGER, ugi TEXT, local INTEGER DEFAULT 0, recover_settings TEXT, private INTEGER DEFAULT 0)");
            st.execute("CREATE TABLE hand(id INTEGER PRIMARY KEY, id_game INTEGER, counter INTEGER, sbval REAL, blinds_double INTEGER, dealer TEXT, sb TEXT, bb TEXT, start INTEGER, end INTEGER, com_cards TEXT, preflop_players TEXT, flop_players TEXT, turn_players TEXT, river_players TEXT, pot REAL, hand_id_b64 TEXT, FOREIGN KEY(id_game) REFERENCES game(id) ON DELETE CASCADE)");
            st.execute("CREATE TABLE action(id INTEGER PRIMARY KEY, id_hand INTEGER, player TEXT, counter INTEGER, round INTEGER, action INTEGER, bet REAL, conta_raise INTEGER, response_time INTEGER, record_b64 TEXT, sig_b64 TEXT, FOREIGN KEY(id_hand) REFERENCES hand(id) ON DELETE CASCADE)");
            st.execute("CREATE TABLE showdown(id INTEGER PRIMARY KEY, id_hand INTEGER, player TEXT, hole_cards TEXT, hand_cards TEXT, hand_val INTEGER, winner INTEGER, pay REAL, profit REAL, FOREIGN KEY(id_hand) REFERENCES hand(id) ON DELETE CASCADE)");
            st.execute("CREATE TABLE balance(id INTEGER PRIMARY KEY, id_hand INTEGER, player TEXT, stack REAL, buyin INTEGER, rebuy_count INTEGER DEFAULT 0, FOREIGN KEY(id_hand) REFERENCES hand(id) ON DELETE CASCADE)");
            st.execute("CREATE TABLE showcards(id INTEGER PRIMARY KEY, id_hand INTEGER, player TEXT, parguela INTEGER, FOREIGN KEY(id_hand) REFERENCES hand(id) ON DELETE CASCADE)");
        }
        return c;
    }

    /**
     * Inserts a game with two hands covering the awkward cases: hand 1 is fully
     * populated with children across every table; hand 2 carries NULLs (no end,
     * no pot, no community cards) and empty child lists. Returns the game id.
     */
    private static long seedRichGame(Connection c, String ugi) throws Exception {
        long gid = ins(c, "game",
                "start", 1000L, "end", 2000L, "play_time", null, "server", "hostNick",
                "players", "cDEjcDI=", "buyin", 100, "sb", 0.5, "blinds_time", 600,
                "rebuy", 1, "last_deck", null, "blinds_time_type", 0,
                "ugi", ugi, "local", 0, "recover_settings", null);

        long h1 = ins(c, "hand",
                "id_game", gid, "counter", 1, "sbval", 0.5, "blinds_double", 0,
                "dealer", "p1", "sb", "p1", "bb", "p2", "start", 1000L, "end", 1500L,
                "com_cards", "As Kd Qh", "preflop_players", "cDEjcDI=", "flop_players", "cDEjcDI=",
                "turn_players", "cDE=", "river_players", null, "pot", 10.5, "hand_id_b64", "aGFuZDE=");
        ins(c, "action", "id_hand", h1, "player", "p1", "counter", 1, "round", 0,
                "action", 2, "bet", 1.0, "conta_raise", 0, "response_time", 1200,
                "record_b64", "cmVj", "sig_b64", null);
        ins(c, "action", "id_hand", h1, "player", "p2", "counter", 2, "round", 0,
                "action", 3, "bet", 2.0, "conta_raise", 1, "response_time", 800,
                "record_b64", null, "sig_b64", null);
        ins(c, "showdown", "id_hand", h1, "player", "p1", "hole_cards", "As#Ks",
                "hand_cards", "As Ks Qh Jh Th", "hand_val", 8, "winner", 1, "pay", 10.5, "profit", 5.0);
        ins(c, "showdown", "id_hand", h1, "player", "p2", "hole_cards", null,
                "hand_cards", null, "hand_val", -1, "winner", 0, "pay", 0.0, "profit", null);
        ins(c, "balance", "id_hand", h1, "player", "p1", "stack", 105.0, "buyin", 100, "rebuy_count", 0);
        ins(c, "balance", "id_hand", h1, "player", "p2", "stack", 95.0, "buyin", 100, "rebuy_count", 1);
        ins(c, "showcards", "id_hand", h1, "player", "p1", "parguela", 1);
        ins(c, "showcards", "id_hand", h1, "player", "p2", "parguela", 0);

        long h2 = ins(c, "hand",
                "id_game", gid, "counter", 2, "sbval", 0.5, "blinds_double", 0,
                "dealer", "p2", "sb", "p2", "bb", "p1", "start", 1500L, "end", null,
                "com_cards", null, "preflop_players", "cDEjcDI=", "flop_players", null,
                "turn_players", null, "river_players", null, "pot", null, "hand_id_b64", null);
        ins(c, "action", "id_hand", h2, "player", "p1", "counter", 1, "round", 0,
                "action", 0, "bet", 0.0, "conta_raise", 0, "response_time", 500,
                "record_b64", null, "sig_b64", null);
        ins(c, "balance", "id_hand", h2, "player", "p1", "stack", 104.0, "buyin", 100, "rebuy_count", 0);

        return gid;
    }

    /** Generic INSERT from alternating (column, value) pairs; returns the new id. */
    private static long ins(Connection c, String table, Object... kv) throws Exception {
        StringBuilder cols = new StringBuilder(), qs = new StringBuilder();
        for (int i = 0; i < kv.length; i += 2) {
            if (i > 0) {
                cols.append(',');
                qs.append(',');
            }
            cols.append((String) kv[i]);
            qs.append('?');
        }
        String sql = "INSERT INTO " + table + "(" + cols + ") VALUES (" + qs + ")";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < kv.length; i += 2) {
                Object v = kv[i + 1];
                int idx = i / 2 + 1;
                if (v == null) {
                    ps.setNull(idx, Types.VARCHAR);
                } else if (v instanceof Integer) {
                    ps.setInt(idx, (Integer) v);
                } else if (v instanceof Long) {
                    ps.setLong(idx, (Long) v);
                } else if (v instanceof Double) {
                    ps.setDouble(idx, (Double) v);
                } else {
                    ps.setString(idx, v.toString());
                }
            }
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) {
                k.next();
                return k.getLong(1);
            }
        }
    }

    private static int count(Connection c, String table) throws Exception {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static String scalarStr(Connection c, String sql) throws Exception {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    /** Asserts the FK graph is intact — no child row points at a missing parent. */
    private static void assertNoOrphans(Connection c) throws Exception {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("PRAGMA foreign_key_check")) {
            assertFalse(rs.next(), "foreign_key_check reported orphan rows");
        }
    }

    private static byte[] gzip(byte[] raw) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(raw);
        }
        return out.toByteArray();
    }

    private static byte[] gunzip(byte[] blob) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(blob))) {
            int n;
            while ((n = gz.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
        }
        return out.toByteArray();
    }
}
