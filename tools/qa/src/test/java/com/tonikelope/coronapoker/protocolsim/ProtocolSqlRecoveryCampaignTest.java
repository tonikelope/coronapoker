/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Base64;
import java.util.HashMap;
import java.util.Random;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Seeded real-SQLite recovery-row to strict-snapshot campaign. */
@Tag("slow")
@Tag("protocol-sim")
class ProtocolSqlRecoveryCampaignTest {

    @Test
    void latestDurableHandBecomesAnAtomicCurrentVersionSnapshot() throws Exception {
        int cases = intProperty("qa.sim.hands", 2_000, 1, 100_000);
        long seed = longProperty("qa.sim.seed", 3231711270L);
        Random random = new Random(seed ^ 0x51A17E5L);
        Class.forName("org.sqlite.JDBC");

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createSchema(connection);
            for (int caseNumber = 0; caseNumber < cases; caseNumber++) {
                insertCase(connection, random, caseNumber);
            }

            int rejectedOpenHandsWithoutCryptoId = 0;
            int acceptedClosedHandsWithoutCryptoId = 0;
            try (PreparedStatement query = connection.prepareStatement(
                    Crupier.RECOVERY_GAME_KEY_DATA_SQL)) {
                for (int caseNumber = 0; caseNumber < cases; caseNumber++) {
                    int gameId = caseNumber + 1;
                    String context = "seed=" + seed + " sql_recovery_case=" + caseNumber;
                    Crupier.bindRecoveryGameKeyDataQuery(query, gameId);
                    try (ResultSet result = query.executeQuery()) {
                        assertTrue(result.next(), context + " missing latest hand");
                        HashMap<String, Object> map = RecoveryGameKeyDataReader.read(result);
                        assertFalse(result.next(), context + " returned more than one hand");
                        assertEquals(caseNumber * 10 + 7, map.get("conta_mano"), context);
                        assertEquals(caseNumber * 2 + 2, map.get("hand_id"), context);

                        boolean missingCryptoHandId = caseNumber % 17 == 0;
                        boolean open = ((Long) map.get("hand_end")) == 0L;
                        RecoverySnapshotV1.Result snapshot = RecoverySnapshotV1.fromMap(
                                map, "sql-session-" + caseNumber);
                        if (missingCryptoHandId && open) {
                            assertFalse(snapshot.isOk(),
                                    context + " accepted open hand without cryptographic hand id");
                            rejectedOpenHandsWithoutCryptoId++;
                        } else {
                            assertTrue(snapshot.isOk(), context + " " + snapshot.error());
                            if (missingCryptoHandId) {
                                acceptedClosedHandsWithoutCryptoId++;
                            }
                            byte[] wire = snapshot.value().encode();
                            RecoverySnapshotV1.Result decoded = RecoverySnapshotV1.decode(
                                    wire, "sql-session-" + caseNumber);
                            assertTrue(decoded.isOk(), context + " " + decoded.error());
                            assertEquals(open ? caseNumber * 10 + 7 : caseNumber * 10 + 8,
                                    Crupier.handCounterForRecovery(
                                            (Integer) map.get("conta_mano"), open), context);
                        }
                    }
                }
            }
            if (cases >= 18) {
                assertTrue(rejectedOpenHandsWithoutCryptoId > 0,
                        "campaign did not exercise an open hand without cryptographic hand id");
                assertTrue(acceptedClosedHandsWithoutCryptoId > 0,
                        "campaign did not exercise a closed aborted hand without cryptographic hand id");
            }
        }
    }

    private static void createSchema(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE game (id INTEGER PRIMARY KEY, server TEXT, start INTEGER, buyin INTEGER, rebuy BOOLEAN, play_time INTEGER, blinds_time INTEGER, blinds_time_type INTEGER)");
            statement.execute("CREATE TABLE hand (id INTEGER PRIMARY KEY, id_game INTEGER, counter INTEGER, end INTEGER, preflop_players TEXT, hand_id_b64 TEXT, sbval REAL, blinds_double INTEGER, dealer TEXT, sb TEXT, bb TEXT)");
        }
    }

    private static void insertCase(Connection connection, Random random,
            int caseNumber) throws Exception {
        int gameId = caseNumber + 1;
        String dealer = "dealer-" + caseNumber;
        String smallBlind = "small-" + caseNumber;
        String bigBlind = "big-" + caseNumber;
        String roster = b64(dealer) + "#" + b64(smallBlind) + "#" + b64(bigBlind);
        try (PreparedStatement game = connection.prepareStatement(
                "INSERT INTO game VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
                PreparedStatement hand = connection.prepareStatement(
                        "INSERT INTO hand VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");) {
            game.setInt(1, gameId);
            game.setString(2, dealer);
            game.setLong(3, 1_700_000_000_000L + caseNumber);
            game.setInt(4, 1 + random.nextInt(10_000));
            game.setBoolean(5, random.nextBoolean());
            game.setLong(6, random.nextInt(1_000_000));
            game.setInt(7, random.nextInt(1_000));
            game.setInt(8, random.nextInt(3));
            game.executeUpdate();

            insertHand(hand, caseNumber * 2 + 1, gameId, caseNumber * 10 + 1,
                    1L, roster, validHandId(caseNumber - 1), 0.01d,
                    dealer, smallBlind, bigBlind);
            boolean missingCryptoHandId = caseNumber % 17 == 0;
            boolean open = missingCryptoHandId
                    ? (caseNumber / 17) % 2 == 0
                    : random.nextBoolean();
            long end = open ? 0L : 1_700_000_100_000L + caseNumber;
            double smallBlindValue = (1 + random.nextInt(100_000)) / 100.0d;
            insertHand(hand, caseNumber * 2 + 2, gameId, caseNumber * 10 + 7,
                    end, roster, missingCryptoHandId ? null : validHandId(caseNumber), smallBlindValue,
                    dealer, smallBlind, bigBlind);
        }
    }

    private static void insertHand(PreparedStatement hand, int id, int gameId,
            int counter, long end, String roster, String handId, double smallBlind,
            String dealer, String sb, String bb) throws Exception {
        hand.setInt(1, id);
        hand.setInt(2, gameId);
        hand.setInt(3, counter);
        hand.setLong(4, end);
        hand.setString(5, roster);
        hand.setString(6, handId);
        hand.setDouble(7, smallBlind);
        hand.setInt(8, 0);
        hand.setString(9, dealer);
        hand.setString(10, sb);
        hand.setString(11, bb);
        hand.executeUpdate();
    }

    private static String validHandId(int marker) {
        byte[] handId = new byte[CanonicalActionRecord.HAND_ID_BYTES];
        java.nio.ByteBuffer.wrap(handId).putInt(marker);
        return Base64.getEncoder().encodeToString(handId);
    }

    private static String b64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static int intProperty(String name, int fallback, int min, int max) {
        String text = System.getProperty(name);
        if (text == null || text.isBlank() || text.startsWith("${")) {
            return fallback;
        }
        int value = Integer.parseInt(text);
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be " + min + ".." + max);
        }
        return value;
    }

    private static long longProperty(String name, long fallback) {
        String text = System.getProperty(name);
        return text == null || text.isBlank() || text.startsWith("${")
                ? fallback : Long.parseLong(text);
    }
}
