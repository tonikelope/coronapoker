/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

/** Produces a human-readable forensic companion for a Zero Trust DB row. */
final class ZeroTrustEvidenceReport {

    private static final int HAND_ID_BYTES = 16;
    private static final int FINAL_HASH_BYTES = 32;
    private static final int FLAGS_BYTES = 1;
    private static final int SIGNATURE_BYTES = 64;
    private static final int RECEIPT_BYTES = HAND_ID_BYTES + FINAL_HASH_BYTES
            + FLAGS_BYTES + SIGNATURE_BYTES;
    private static final DateTimeFormatter FILE_TIME
            = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")
                    .withZone(ZoneOffset.UTC);

    private ZeroTrustEvidenceReport() {
    }

    static Result write(Connection connection, String databaseLocation,
            long timestampMillis, int handOrdinal, int sqliteHandId,
            String reason, String localNickname, boolean host,
            byte[] localFinalHash, Map<String, byte[]> receipts,
            long openingRemainderCents, long closingRemainderCents) {
        Path output = null;
        Throwable pathFailure = null;
        try {
            Path directory = evidenceDirectory(databaseLocation);
            Files.createDirectories(directory);
            String base = "zero_trust_evidence_"
                    + FILE_TIME.format(Instant.ofEpochMilli(timestampMillis));
            output = directory.resolve(base + ".log");
            int collision = 1;
            while (Files.exists(output)) {
                output = directory.resolve(base + "_" + collision++ + ".log");
            }
            output = output.toAbsolutePath().normalize();
        } catch (IOException | RuntimeException failure) {
            pathFailure = failure;
        }

        String report = build(connection, databaseLocation, output,
                timestampMillis, handOrdinal, sqliteHandId, reason,
                localNickname, host, localFinalHash, receipts,
                openingRemainderCents, closingRemainderCents);
        if (output == null) {
            return new Result(report, null, pathFailure);
        }
        try {
            Files.writeString(output, report, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            return new Result(report, output, null);
        } catch (IOException | RuntimeException failure) {
            return new Result(report, null, failure);
        }
    }

    private static String build(Connection connection, String databaseLocation,
            Path evidenceFile, long timestampMillis, int handOrdinal,
            int sqliteHandId, String reason, String localNickname,
            boolean host, byte[] localFinalHash,
            Map<String, byte[]> receipts, long openingRemainderCents,
            long closingRemainderCents) {
        StringBuilder report = new StringBuilder(16_384);
        report.append("ZERO_TRUST_EVIDENCE_V1\n")
                .append("timestamp_utc=")
                .append(Instant.ofEpochMilli(timestampMillis)).append('\n')
                .append("reason=").append(line(reason)).append('\n')
                .append("hand_ordinal=").append(handOrdinal).append('\n')
                .append("sqlite_hand_id=").append(sqliteHandId).append('\n')
                .append("local_nickname=").append(line(localNickname)).append('\n')
                .append("local_role=").append(host ? "HOST" : "CLIENT").append('\n')
                .append("database=").append(line(databaseLocation)).append('\n')
                .append("evidence_file=").append(line(evidenceFile)).append('\n')
                .append("opening_remainder_cents=")
                .append(openingRemainderCents).append('\n')
                .append("closing_remainder_cents=")
                .append(closingRemainderCents).append('\n')
                .append("local_h_final_b64=")
                .append(base64(localFinalHash))
                .append("\n\nRECEIPTS\n[receipts]\n");

        int index = 0;
        Map<String, byte[]> safeReceipts = receipts == null
                ? Map.of() : receipts;
        for (Map.Entry<String, byte[]> entry
                : new TreeMap<>(safeReceipts).entrySet()) {
            byte[] receipt = entry.getValue();
            report.append("receipt.").append(index).append(".nickname=")
                    .append(line(entry.getKey())).append('\n')
                    .append("receipt.").append(index).append(".nickname_b64=")
                    .append(Base64.getEncoder().encodeToString(
                            entry.getKey().getBytes(StandardCharsets.UTF_8)))
                    .append('\n')
                    .append("receipt.").append(index).append(".bytes=")
                    .append(receipt == null ? -1 : receipt.length).append('\n')
                    .append("receipt.").append(index).append(".raw_b64=")
                    .append(base64(receipt)).append('\n');
            if (receipt != null && receipt.length == RECEIPT_BYTES) {
                report.append("receipt.").append(index).append(".hand_id_b64=")
                        .append(base64(Arrays.copyOfRange(receipt, 0,
                                HAND_ID_BYTES))).append('\n')
                        .append("receipt.").append(index).append(".h_final_b64=")
                        .append(base64(Arrays.copyOfRange(receipt,
                                HAND_ID_BYTES,
                                HAND_ID_BYTES + FINAL_HASH_BYTES))).append('\n')
                        .append("receipt.").append(index).append(".flags=")
                        .append(Byte.toUnsignedInt(receipt[
                                HAND_ID_BYTES + FINAL_HASH_BYTES])).append('\n')
                        .append("receipt.").append(index).append(".signature_b64=")
                        .append(base64(Arrays.copyOfRange(receipt,
                                HAND_ID_BYTES + FINAL_HASH_BYTES + FLAGS_BYTES,
                                RECEIPT_BYTES))).append('\n');
            }
            report.append('\n');
            index++;
        }

        report.append("\nDATABASE_SNAPSHOT\n");
        appendQuery(report, connection, "game",
                "SELECT game.* FROM game JOIN hand ON hand.id_game=game.id "
                        + "WHERE hand.id=?", sqliteHandId);
        appendQuery(report, connection, "hand",
                "SELECT * FROM hand WHERE id=?", sqliteHandId);
        appendQuery(report, connection, "balances",
                "SELECT * FROM balance WHERE id_hand=? ORDER BY player,id",
                sqliteHandId);
        appendQuery(report, connection, "actions",
                "SELECT * FROM action WHERE id_hand=? ORDER BY counter,id",
                sqliteHandId);
        appendQuery(report, connection, "showdown",
                "SELECT * FROM showdown WHERE id_hand=? ORDER BY player,id",
                sqliteHandId);
        appendQuery(report, connection, "dispute",
                "SELECT id,id_hand,timestamp,receipt_nicks,local_h,reason "
                        + "FROM disputed_hands WHERE id_hand=? ORDER BY id DESC LIMIT 1",
                sqliteHandId);
        return report.toString();
    }

    private static void appendQuery(StringBuilder report, Connection connection,
            String section, String sql, int handId) {
        report.append('\n').append('[').append(section).append("]\n");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(30);
            statement.setInt(1, handId);
            try (ResultSet rows = statement.executeQuery()) {
                ResultSetMetaData metadata = rows.getMetaData();
                int row = 0;
                while (rows.next()) {
                    report.append("row.").append(row++).append('=');
                    for (int column = 1; column <= metadata.getColumnCount();
                            column++) {
                        if (column > 1) report.append(" | ");
                        report.append(metadata.getColumnLabel(column)).append('=');
                        Object value = rows.getObject(column);
                        if (value instanceof byte[] bytes) {
                            report.append(base64(bytes));
                        } else {
                            report.append(line(value));
                        }
                    }
                    report.append('\n');
                }
                if (row == 0) report.append("rows=0\n");
            }
        } catch (SQLException | RuntimeException failure) {
            report.append("query_error=").append(line(failure)).append('\n');
        }
    }

    private static Path evidenceDirectory(String databaseLocation) {
        if (databaseLocation != null && !databaseLocation.isBlank()
                && !":memory:".equals(databaseLocation)) {
            Path database = Path.of(databaseLocation).toAbsolutePath()
                    .normalize();
            Path parent = database.getParent();
            if (parent != null) return parent.resolve("logs");
        }
        return Path.of(System.getProperty("user.home"), ".coronapoker",
                "logs").toAbsolutePath().normalize();
    }

    private static String base64(byte[] value) {
        return value == null ? "null"
                : Base64.getEncoder().encodeToString(value);
    }

    private static String line(Object value) {
        if (value == null) return "null";
        return String.valueOf(value).replace("\\", "\\\\")
                .replace("\r", "\\r").replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    static void persist(Connection connection, long disputeId, Result evidence)
            throws SQLException {
        if (connection == null) {
            throw new SQLException("Zero Trust evidence database is unavailable");
        }
        if (disputeId < 0L) {
            throw new SQLException("Zero Trust dispute id is unavailable");
        }
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE disputed_hands SET evidence_text=?, evidence_path=? WHERE id=?")) {
            update.setQueryTimeout(30);
            update.setString(1, evidence.report());
            update.setString(2, evidence.path() == null ? ""
                    : evidence.path().toString());
            update.setLong(3, disputeId);
            if (update.executeUpdate() != 1) {
                throw new SQLException("Zero Trust dispute row was not updated: "
                        + disputeId);
            }
        }
    }

    record Result(String report, Path path, Throwable writeFailure) {
    }
}
