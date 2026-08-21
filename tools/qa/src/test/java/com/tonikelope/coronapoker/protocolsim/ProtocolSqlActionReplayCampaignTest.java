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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Seeded real-SQLite action persistence and signed-chain replay campaign. */
@Tag("slow")
@Tag("protocol-sim")
class ProtocolSqlActionReplayCampaignTest {

    private static final String ALICE = "sql-alice";
    private static final String BOB = "sql-bob";
    private static IdentityManager aliceIdentity;
    private static IdentityManager bobIdentity;

    @BeforeAll
    static void identities() {
        aliceIdentity = IdentityManager.initializeForNick(ALICE);
        bobIdentity = IdentityManager.initializeForNick(BOB);
        assertTrue(aliceIdentity.isReady(), aliceIdentity.getLoadError());
        assertTrue(bobIdentity.isReady(), bobIdentity.getLoadError());
    }

    @Test
    void persistedRowsReplayInCounterOrderOrRejectAtomically() throws Exception {
        int cases = intProperty("qa.sim.hands", 2_000, 1, 100_000);
        long seed = longProperty("qa.sim.seed", 3231711270L);
        Random random = new Random(seed ^ 0xAC710A5L);
        Class.forName("org.sqlite.JDBC");

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createSchema(connection);
            for (int caseNumber = 0; caseNumber < cases; caseNumber++) {
                String context = "seed=" + seed + " sql_action_case=" + caseNumber;
                Genesis genesis = genesis(random, caseNumber);
                HandStateChain live = genesis.startChain();
                int actionCount = 1 + random.nextInt(8);
                List<Row> rows = new ArrayList<>();
                for (int counter = 1; counter <= actionCount; counter++) {
                    String actor = (counter & 1) == 0 ? ALICE : BOB;
                    IdentityManager identity = actor.equals(ALICE) ? aliceIdentity : bobIdentity;
                    int decision = randomDecision(random);
                    long cents = decision == Player.BET ? random.nextInt(100_001) : 0L;
                    byte[] record = CanonicalActionRecord.encode(live.getCurrentHash(), genesis.handId,
                            CanonicalActionRecord.playerIdFromNick(actor),
                            random.nextInt(4), wireAction(decision),
                            cents, decision == Player.ALLIN, true);
                    byte[] signature = identity.signAction(record);
                    live.absorb(record, signature);
                    rows.add(new Row(counter, actor, decision, cents,
                            b64(record), b64(signature)));
                }
                List<Row> expectedRows = List.copyOf(rows);

                boolean corrupt = caseNumber % 17 == 0;
                if (corrupt) {
                    Row row = rows.get(rows.size() / 2);
                    rows.set(rows.size() / 2, new Row(row.counter, row.actor,
                            row.decision, row.cents, "not-base64", row.signatureB64));
                }
                Collections.shuffle(rows, random);
                insertRows(connection, caseNumber + 1, rows);

                HandStateChain recovered = genesis.startChain();
                byte[] before = recovered.getCurrentHash();
                try (PreparedStatement query = connection.prepareStatement(
                        Crupier.RECOVERY_HAND_ACTIONS_SQL)) {
                    Crupier.bindRecoveryHandActionsQuery(query, caseNumber + 1);
                    try (ResultSet result = query.executeQuery()) {
                        String batch = RecoveredActionSqlReader.readAll(result);
                        RecoveryActionReceiveState receive = new RecoveryActionReceiveState();
                        receive.acceptFrame("GAME#1#ACTIONDATA#" + b64(
                                batch.getBytes(StandardCharsets.UTF_8)));
                        assertTrue(receive.isSuccess(), context + " " + receive.error());

                        RecoveredActionBatch.Result decoded = RecoveredActionBatch.decode(
                                receive.actions());
                        if (corrupt) {
                            assertFalse(decoded.isOk(), context + " accepted corrupt SQL row");
                            assertArrayEquals(before, recovered.getCurrentHash(), context);
                            assertEquals(0, recovered.getAbsorbedActions(), context);
                            continue;
                        }
                        assertTrue(decoded.isOk(), context);
                        assertEquals(actionCount, decoded.actions().size(), context);
                        for (int i = 0; i < decoded.actions().size(); i++) {
                            RecoveredActionCodec.Wire action = decoded.actions().get(i).wire();
                            Row expected = expectedRows.get(i);
                            assertEquals(expected.actor, action.actor(), context);
                            assertEquals(expected.decision, action.decision(), context);
                            assertEquals(expected.cents, action.amountCents(), context);
                            assertEquals(wireAction(expected.decision),
                                    CanonicalActionRecord.readActionType(action.record()), context);
                            assertEquals(expected.cents,
                                    CanonicalActionRecord.readAmountCents(action.record()), context);
                            assertArrayEquals(CanonicalActionRecord.playerIdFromNick(expected.actor),
                                    CanonicalActionRecord.readPlayerId(action.record()), context);
                            IdentityManager identity = action.actor().equals(ALICE)
                                    ? aliceIdentity : bobIdentity;
                            assertTrue(IdentityManager.verifyAction(identity.getPublicKey(),
                                    action.record(), action.signature()), context);
                            assertArrayEquals(recovered.getCurrentHash(),
                                    Arrays.copyOfRange(action.record(),
                                            CanonicalActionRecord.OFFSET_PREV_H,
                                            CanonicalActionRecord.OFFSET_PREV_H
                                            + CanonicalActionRecord.HASH_BYTES), context);
                            recovered.absorb(action.record(), action.signature());
                        }
                        assertArrayEquals(live.getCurrentHash(), recovered.getCurrentHash(), context);
                        assertEquals(live.getAbsorbedActions(), recovered.getAbsorbedActions(), context);
                    }
                }
            }
        }
    }

    private static void createSchema(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE action (id_hand INTEGER, counter INTEGER, player TEXT, action INTEGER, bet REAL, record_b64 TEXT, sig_b64 TEXT)");
        }
    }

    private static void insertRows(Connection connection, int handId,
            List<Row> rows) throws Exception {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO action VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            for (Row row : rows) {
                insert.setInt(1, handId);
                insert.setInt(2, row.counter);
                insert.setString(3, row.actor);
                insert.setInt(4, row.decision);
                insert.setDouble(5, row.cents / 100d);
                insert.setString(6, row.recordB64);
                insert.setString(7, row.signatureB64);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static Genesis genesis(Random random, int marker) {
        byte[] handId = new byte[CanonicalActionRecord.HAND_ID_BYTES];
        random.nextBytes(handId);
        java.nio.ByteBuffer.wrap(handId).putInt(marker);
        List<byte[]> ids = List.of(CanonicalActionRecord.playerIdFromNick(ALICE),
                CanonicalActionRecord.playerIdFromNick(BOB));
        List<byte[]> pockets = commits(random, 2);
        List<byte[]> communities = commits(random, 2);
        byte[] deck = new byte[52 * 32];
        random.nextBytes(deck);
        return new Genesis(handId, ids, pockets, communities, deck);
    }

    private static List<byte[]> commits(Random random, int count) {
        List<byte[]> values = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            byte[] value = new byte[32];
            random.nextBytes(value);
            values.add(value);
        }
        return values;
    }

    private static int randomDecision(Random random) {
        return switch (random.nextInt(4)) {
            case 0 -> Player.FOLD;
            case 1 -> Player.CHECK;
            case 2 -> Player.BET;
            default -> Player.ALLIN;
        };
    }

    private static int wireAction(int decision) {
        return switch (decision) {
            case Player.FOLD -> CanonicalActionRecord.ACTION_FOLD;
            case Player.CHECK -> CanonicalActionRecord.ACTION_CHECK;
            case Player.BET -> CanonicalActionRecord.ACTION_BET;
            case Player.ALLIN -> CanonicalActionRecord.ACTION_ALLIN;
            default -> throw new IllegalArgumentException("unexpected decision");
        };
    }

    private static String b64(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
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

    private record Row(int counter, String actor, int decision, long cents,
            String recordB64, String signatureB64) {
    }

    private record Genesis(byte[] handId, List<byte[]> playerIds,
            List<byte[]> pocketCommits, List<byte[]> communityCommits, byte[] deck) {

        private HandStateChain startChain() {
            return HandStateChain.start(handId, playerIds, pocketCommits,
                    communityCommits, deck);
        }
    }
}
