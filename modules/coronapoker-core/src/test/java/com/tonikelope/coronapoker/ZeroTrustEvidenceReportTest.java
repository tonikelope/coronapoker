/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.DatabaseService;
import com.tonikelope.coronapoker.core.game.CoreGameDatabase;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ZeroTrustEvidenceReportTest {

    @TempDir
    Path temporary;

    @Test
    void writesCompleteEnglishEvidenceBesideDatabase() throws Exception {
        Path databasePath = temporary.resolve("coronapoker.db");
        try (DatabaseService database = new DatabaseService(
                databasePath.toString())) {
            database.start();
            new CoreGameDatabase(database);
            try (Statement statement = database.connection().createStatement()) {
                statement.execute("INSERT INTO game(id,start,server,players,buyin) "
                        + "VALUES(1,10,'host','host,client',100)");
                statement.execute("INSERT INTO hand(id,id_game,counter,start,pot) "
                        + "VALUES(7,1,3,20,30.1)");
                statement.execute("INSERT INTO balance(id_hand,player,stack,buyin) "
                        + "VALUES(7,'host',69.9,100)");
                statement.execute("INSERT INTO action(id_hand,player,counter,round,action,bet) "
                        + "VALUES(7,'client',1,0,1,0.2)");
                statement.execute("INSERT INTO showdown(id_hand,player,winner,pay,profit) "
                        + "VALUES(7,'host',1,30.2,0.1)");
                statement.execute("INSERT INTO disputed_hands(id_hand,timestamp,"
                        + "receipts,receipt_nicks,local_h,reason) VALUES(7,30,X'00',"
                        + "'aG9zdA==',X'01','DIVERGENT peers=client')");
            }

            byte[] localHash = new byte[32];
            java.util.Arrays.fill(localHash, (byte) 0x2a);
            byte[] receipt = new byte[113];
            for (int index = 0; index < receipt.length; index++) {
                receipt[index] = (byte) index;
            }
            Map<String, byte[]> receipts = new LinkedHashMap<>();
            receipts.put("client", receipt);

            long timestamp = Instant.parse("2026-10-30T20:35:25.123Z")
                    .toEpochMilli();
            ZeroTrustEvidenceReport.Result result
                    = ZeroTrustEvidenceReport.write(database.connection(),
                            database.databaseLocation(), timestamp, 3, 7,
                            "DIVERGENT peers=client", "host", true,
                            localHash, receipts, 10L, 0L);

            assertNull(result.writeFailure());
            assertNotNull(result.path());
            assertEquals(databasePath.getParent().resolve("logs")
                    .toAbsolutePath().normalize(), result.path().getParent());
            assertTrue(result.path().getFileName().toString().startsWith(
                    "zero_trust_evidence_20261030_203525_123"));
            String persisted = Files.readString(result.path(),
                    StandardCharsets.UTF_8);
            assertEquals(result.report(), persisted);
            assertTrue(persisted.contains("ZERO_TRUST_EVIDENCE_V1"));
            assertTrue(persisted.contains("RECEIPTS\n[receipts]\n"));
            assertTrue(persisted.contains("DATABASE_SNAPSHOT\n"));
            assertTrue(persisted.contains("reason=DIVERGENT peers=client"));
            assertTrue(persisted.contains("local_role=HOST"));
            assertTrue(persisted.contains("opening_remainder_cents=10"));
            assertTrue(persisted.contains("receipt.0.nickname=client"));
            assertTrue(persisted.contains("receipt.0.flags=48"));
            assertTrue(persisted.contains("[actions]"));
            assertTrue(persisted.contains("player=client"));
            assertTrue(persisted.contains("[showdown]"));
            assertTrue(persisted.contains("pay=30.2"));

            ZeroTrustEvidenceReport.persist(database.connection(), 1L,
                    result);
            try (Statement statement = database.connection().createStatement();
                    ResultSet row = statement.executeQuery(
                            "SELECT evidence_text,evidence_path FROM disputed_hands WHERE id=1")) {
                assertTrue(row.next());
                assertEquals(persisted, row.getString("evidence_text"));
                assertEquals(result.path().toString(),
                        row.getString("evidence_path"));
            }
        }
    }

    @Test
    void keepsCompleteDatabaseEvidenceWhenTheCompanionFileCannotBeWritten()
            throws Exception {
        Path databasePath = temporary.resolve("fallback.db");
        Path blockedParent = temporary.resolve("blocked-evidence");
        Files.createDirectories(blockedParent);
        Files.writeString(blockedParent.resolve("logs"), "not-a-directory",
                StandardCharsets.UTF_8);
        try (DatabaseService database = new DatabaseService(
                    databasePath.toString())) {
            database.start();
            new CoreGameDatabase(database);
            try (Statement statement = database.connection().createStatement()) {
                statement.execute("INSERT INTO game(id,start,server,players,buyin) "
                        + "VALUES(1,10,'host','host,client',100)");
                statement.execute("INSERT INTO hand(id,id_game,counter,start,pot) "
                        + "VALUES(7,1,3,20,30.1)");
                statement.execute("INSERT INTO disputed_hands(id_hand,timestamp,"
                        + "receipts,receipt_nicks,local_h,reason) VALUES(7,30,X'00',"
                        + "'',X'01','FILE_WRITE_FAILURE')");
            }

            ZeroTrustEvidenceReport.Result result
                    = ZeroTrustEvidenceReport.write(database.connection(),
                            blockedParent.resolve("coronapoker.db").toString(),
                            Instant.parse("2026-10-30T20:35:25.123Z")
                                    .toEpochMilli(),
                            3, 7, "FILE_WRITE_FAILURE", "client", false,
                            new byte[32], Map.of(), 0L, 0L);

            assertNotNull(result.writeFailure());
            assertNull(result.path());
            assertTrue(result.report().startsWith(
                    "ZERO_TRUST_EVIDENCE_V1\n"));
            assertTrue(result.report().contains("DATABASE_SNAPSHOT\n"));
            assertTrue(result.report().contains("reason=FILE_WRITE_FAILURE"));
            assertTrue(result.report().contains("[hand]\nrow.0="));

            ZeroTrustEvidenceReport.persist(database.connection(), 1L,
                    result);
            try (Statement statement = database.connection().createStatement();
                    ResultSet row = statement.executeQuery(
                            "SELECT evidence_text,evidence_path "
                            + "FROM disputed_hands WHERE id=1")) {
                assertTrue(row.next());
                assertEquals(result.report(), row.getString("evidence_text"));
                assertEquals("", row.getString("evidence_path"));
            }
        }
    }
}
