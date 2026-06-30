package com.tonikelope.coronapoker;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Peer-to-peer statistics database synchronization — data layer.
 *
 * <p>Serializes complete game subtrees ({@code game} → {@code hand}s → each
 * hand's {@code action}/{@code showdown}/{@code balance}/{@code showcards}) and
 * merges them into the local SQLite database, deduplicating by the
 * globally-shared {@code game.ugi}. The host generates the ugi once per table
 * and propagates it to every client in the INIT command, so the same real game
 * carries the same ugi in everyone's database — that is the only cross-database
 * identity. Auto-increment primary keys are dropped on export and regenerated
 * on import.
 *
 * <p>Import is <b>atomic per game</b> and <b>idempotent</b>: a game whose ugi
 * already exists is skipped, and a malformed game is rolled back and skipped
 * without ever leaving partial rows behind. The whole blob is decoded and
 * validated in memory before any row is written.
 *
 * <p>The {@link Connection}-taking methods are the core (used by tests with a
 * throwaway database); the no-argument production wrappers operate on
 * {@link Helpers#getSQLITE()} under {@link GameFrame#SQL_LOCK} and never let an
 * exception escape into the sync thread.
 */
public final class StatsSync {

    private static final Logger LOGGER = Logger.getLogger(StatsSync.class.getName());

    // Blob header: magic + format version, so a future format change is detectable.
    private static final int MAGIC = 0x43504442; // "CPDB"
    private static final int FORMAT_VERSION = 1;

    // Hard caps guarding against malformed / hostile blobs (the wire frame is
    // already ≤ 16 MB, but a gzip payload can expand, and lengths are attacker
    // controlled until validated).
    // 32 MB: the sender batches 25 games, so a legitimate GAMES blob inflates to
    // a few MB. Far below this; well above any real batch; bounds a hostile blob
    // and the work done while holding SQL_LOCK during the insert loop.
    private static final long MAX_INFLATED_BYTES = 32L * 1024 * 1024;
    private static final int MAX_STRING_BYTES = 8 * 1024 * 1024;

    // Column type tags for the generic row codec.
    private static final char INT = 'I';   // SQLite INTEGER affinity (read/written as long)
    private static final char REAL = 'R';  // SQLite REAL affinity (double)
    private static final char TEXT = 'T';  // SQLite TEXT affinity (UTF-8 string)

    // Payload columns per table, in a fixed order. Structural columns (the
    // auto-increment id and the parent FK) are intentionally NOT listed: ids are
    // regenerated on import and FKs are wired from the freshly inserted parent.
    // Verified one-to-one against the CREATE TABLE + ALTER TABLE statements in
    // Helpers.initSQLITE().
    private static final Cols GAME = new Cols(new String[]{
        "start:I", "end:I", "play_time:I", "server:T", "players:T", "buyin:I",
        "sb:R", "blinds_time:I", "rebuy:I", "last_deck:T", "blinds_time_type:I",
        "ugi:T", "local:I", "recover_settings:T"
    });
    private static final Cols HAND = new Cols(new String[]{
        "counter:I", "sbval:R", "blinds_double:I", "dealer:T", "sb:T", "bb:T",
        "start:I", "end:I", "com_cards:T", "preflop_players:T", "flop_players:T",
        "turn_players:T", "river_players:T", "pot:R", "hand_id_b64:T"
    });
    private static final Cols ACTION = new Cols(new String[]{
        "player:T", "counter:I", "round:I", "action:I", "bet:R",
        "conta_raise:I", "response_time:I", "record_b64:T", "sig_b64:T"
    });
    private static final Cols SHOWDOWN = new Cols(new String[]{
        "player:T", "hole_cards:T", "hand_cards:T", "hand_val:I",
        "winner:I", "pay:R", "profit:R"
    });
    private static final Cols BALANCE = new Cols(new String[]{
        "player:T", "stack:R", "buyin:I", "rebuy_count:I"
    });
    private static final Cols SHOWCARDS = new Cols(new String[]{
        "player:T", "parguela:I"
    });

    private StatsSync() {
    }

    // =========================================================================
    // Production wrappers (local DB, locked, exception-safe)
    // =========================================================================

    /**
     * UGIs of the games this peer is willing to share: finished games that carry
     * a merge key ({@code ugi}) and are not marked private. Pre-ugi games (no key)
     * and unfinished games (no {@code end}) are never propagated.
     *
     * <p>The {@code game.local} column is deliberately NOT a filter here: it means
     * "this machine was the <em>host</em> of the table" (it is written as
     * {@code isPartida_local() ? 1 : 0} and used by the recover-last-game feature),
     * <em>not</em> "offline/solo game". Filtering it out would mute every game this
     * peer hosted — exactly the games it is the authoritative source for — so it is
     * left untouched and convergence is by {@code ugi} alone.
     */
    public static List<String> listShareableUgis() {
        synchronized (GameFrame.SQL_LOCK) {
            try {
                return listShareableUgis(Helpers.getSQLITE());
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "StatsSync: listing shareable ugis failed", ex);
                return new ArrayList<>();
            }
        }
    }

    /**
     * Serializes the given games into a gzipped blob, ready to send over the
     * wire. Returns {@code null} on failure (logged) so the caller can simply
     * skip this batch.
     */
    public static byte[] exportGames(Collection<String> ugis) {
        synchronized (GameFrame.SQL_LOCK) {
            try {
                return exportGames(Helpers.getSQLITE(), ugis);
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "StatsSync: export failed", ex);
                return null;
            }
        }
    }

    /**
     * Merges a received blob into the local database and returns the number of
     * games newly inserted (already-known and malformed games are skipped).
     * Never throws.
     */
    public static int importGames(byte[] blob) {
        try {
            // Decode (inflate + parse) is pure CPU/memory — do it WITHOUT holding
            // SQL_LOCK so a large or hostile blob can never stall the live game's
            // DB writes. Only the actual INSERT loop takes the lock.
            List<GameData> games = decodeGames(blob);
            if (games.isEmpty()) {
                return 0;
            }
            synchronized (GameFrame.SQL_LOCK) {
                return insertGames(Helpers.getSQLITE(), games);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "StatsSync: import failed", ex);
            return 0;
        }
    }

    /**
     * Manifest diff — the set difference {@code a \ b}: ugis present in {@code a}
     * but not in {@code b}, preserving the order of {@code a}. Given my ugis and
     * the peer's ugis, {@code difference(mine, theirs)} is what the peer lacks
     * (and would receive if I share my games), and {@code difference(theirs,
     * mine)} is what I lack (and would receive if I sync). O(|a| + |b|).
     */
    public static List<String> difference(Collection<String> a, Collection<String> b) {
        java.util.HashSet<String> exclude = new java.util.HashSet<>(b);
        List<String> out = new ArrayList<>();
        for (String x : a) {
            if (x != null && !exclude.contains(x)) {
                out.add(x);
            }
        }
        return out;
    }

    // =========================================================================
    // Core — Connection injected (testable, may throw)
    // =========================================================================

    public static List<String> listShareableUgis(Connection conn) throws Exception {
        List<String> out = new ArrayList<>();
        // Shareable = finished (end set) + has a merge key (ugi) + not private.
        // NOTE: game.local is intentionally absent from this filter — it flags
        // "I was the host" (see the public wrapper's javadoc), not "offline", so it
        // must not gate sharing. private = 1 games are deliberately withheld (the
        // user marked them not shareable), regardless of the global "share"
        // preference; the NULL guard covers pre-migration rows (before the column).
        String sql = "SELECT ugi FROM game WHERE ugi IS NOT NULL AND ugi <> '' AND end IS NOT NULL AND (private IS NULL OR private = 0)";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                out.add(rs.getString("ugi"));
            }
        }
        return out;
    }

    public static byte[] exportGames(Connection conn, Collection<String> ugis) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int serialized = 0;
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(baos))) {
            out.writeInt(MAGIC);
            out.writeInt(FORMAT_VERSION);
            for (String ugi : ugis) {
                if (ugi == null) {
                    continue;
                }
                Long gameId = selectId(conn, "SELECT id FROM game WHERE ugi = ?", ugi);
                if (gameId == null) {
                    continue; // game vanished between manifest and export — harmless
                }
                out.writeBoolean(true); // another game follows
                writeRowById(conn, out, "game", GAME, gameId);
                serializeHands(conn, out, gameId);
                serialized++;
                LOGGER.log(Level.FINE, "StatsSync: exported game ugi={0}", ugi);
            }
            out.writeBoolean(false); // end of games
        }
        byte[] result = baos.toByteArray();
        // Per-batch detail behind the manager's INFO headline ("sent N game(s) to X").
        LOGGER.log(Level.FINE, "StatsSync: exported {0} of {1} requested games ({2} bytes gzipped)",
                new Object[]{serialized, ugis.size(), result.length});
        return result;
    }

    public static int importGames(Connection conn, byte[] blob) throws Exception {
        return insertGames(conn, decodeGames(blob));
    }

    /**
     * Decode phase (no DB, no lock): inflate the blob and parse every game subtree
     * into memory, fully bound-checked. Robust to a bad gzip / bad header (returns
     * empty) and to a truncated stream (returns the games decoded so far).
     */
    private static List<GameData> decodeGames(byte[] blob) {
        List<GameData> games = new ArrayList<>();
        byte[] inflated;
        try {
            inflated = inflate(blob);
        } catch (IOException badGzip) {
            LOGGER.log(Level.WARNING, "StatsSync: blob is not valid gzip, rejected ({0})", badGzip.getMessage());
            return games;
        }
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(inflated));
        try {
            if (in.readInt() != MAGIC) {
                LOGGER.log(Level.WARNING, "StatsSync: bad magic, blob rejected");
                return games;
            }
            int version = in.readInt();
            if (version != FORMAT_VERSION) {
                LOGGER.log(Level.WARNING, "StatsSync: unsupported format version {0}", version);
                return games;
            }
        } catch (IOException shortHeader) {
            LOGGER.log(Level.WARNING, "StatsSync: blob too short for header, rejected ({0})", shortHeader.getMessage());
            return games;
        }
        while (true) {
            try {
                if (!in.readBoolean()) {
                    break;
                }
                games.add(readGame(in)); // fully decode + bound-check this game
            } catch (IOException truncated) {
                // Stream cut past this point — expected when a sync is interrupted.
                // Keep the games decoded so far; the rest arrives on the next
                // (idempotent) sync. No stack trace: a normal, handled outcome.
                LOGGER.log(Level.INFO, "StatsSync: blob ended early (truncated/cut), stopping at the last complete game");
                break;
            }
        }
        return games;
    }

    /**
     * Insert phase (caller holds SQL_LOCK in production): merge each decoded game
     * atomically and idempotently. A game that fails is rolled back and skipped.
     */
    private static int insertGames(Connection conn, List<GameData> games) {
        int imported = 0;
        int skipped = 0;
        int failed = 0;
        for (GameData game : games) {
            try {
                if (insertGameIfNew(conn, game)) {
                    imported++;
                } else {
                    skipped++;
                }
            } catch (Exception ex) {
                // Already rolled back inside insertGameIfNew — skip this one game.
                failed++;
                LOGGER.log(Level.WARNING, "StatsSync: a game was rejected on import", ex);
            }
        }
        // Per-batch breakdown behind the manager's INFO headline ("imported N new game(s) from X").
        LOGGER.log(Level.FINE, "StatsSync: import done — {0} new, {1} already-known, {2} rejected",
                new Object[]{imported, skipped, failed});
        return imported;
    }

    // =========================================================================
    // Export helpers
    // =========================================================================

    private static void serializeHands(Connection conn, DataOutputStream out, long gameId) throws Exception {
        // Collect hand ids first so no parent ResultSet stays open while child
        // queries run on the same single SQLite connection.
        // ORDER BY id (not counter): id is the unique non-null PK, so the order is
        // total and a re-export after import is byte-stable. counter is monotonic
        // in practice but id can never tie.
        List<Long> handIds = selectIds(conn, "SELECT id FROM hand WHERE id_game = ? ORDER BY id", gameId);
        for (Long hid : handIds) {
            out.writeBoolean(true);
            writeRowById(conn, out, "hand", HAND, hid);
            serializeChildren(conn, out, "action", ACTION, hid);
            serializeChildren(conn, out, "showdown", SHOWDOWN, hid);
            serializeChildren(conn, out, "balance", BALANCE, hid);
            serializeChildren(conn, out, "showcards", SHOWCARDS, hid);
        }
        out.writeBoolean(false);
    }

    private static void serializeChildren(Connection conn, DataOutputStream out, String table, Cols cols, long handId) throws Exception {
        String sql = "SELECT " + cols.selectList + " FROM " + table + " WHERE id_hand = ? ORDER BY id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, handId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.writeBoolean(true);
                    writeRow(out, rs, cols);
                }
            }
        }
        out.writeBoolean(false);
    }

    private static void writeRowById(Connection conn, DataOutputStream out, String table, Cols cols, long id) throws Exception {
        String sql = "SELECT " + cols.selectList + " FROM " + table + " WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IOException("row vanished from " + table + " id=" + id);
                }
                writeRow(out, rs, cols);
            }
        }
    }

    private static void writeRow(DataOutputStream out, ResultSet rs, Cols cols) throws Exception {
        for (int i = 0; i < cols.names.length; i++) {
            String name = cols.names[i];
            switch (cols.types[i]) {
                case INT: {
                    long v = rs.getLong(name);
                    writePresence(out, !rs.wasNull());
                    if (!rs.wasNull()) {
                        out.writeLong(v);
                    }
                    break;
                }
                case REAL: {
                    double v = rs.getDouble(name);
                    writePresence(out, !rs.wasNull());
                    if (!rs.wasNull()) {
                        out.writeDouble(v);
                    }
                    break;
                }
                default: { // TEXT
                    String v = rs.getString(name);
                    writePresence(out, v != null);
                    if (v != null) {
                        writeStr(out, v);
                    }
                }
            }
        }
    }

    // =========================================================================
    // Import helpers
    // =========================================================================

    private static GameData readGame(DataInputStream in) throws IOException {
        GameData g = new GameData();
        g.game = readRow(in, GAME);
        while (in.readBoolean()) {
            HandData h = new HandData();
            h.hand = readRow(in, HAND);
            h.actions = readRowList(in, ACTION);
            h.showdowns = readRowList(in, SHOWDOWN);
            h.balances = readRowList(in, BALANCE);
            h.showcards = readRowList(in, SHOWCARDS);
            g.hands.add(h);
        }
        return g;
    }

    private static List<Object[]> readRowList(DataInputStream in, Cols cols) throws IOException {
        List<Object[]> rows = new ArrayList<>();
        while (in.readBoolean()) {
            rows.add(readRow(in, cols));
        }
        return rows;
    }

    private static Object[] readRow(DataInputStream in, Cols cols) throws IOException {
        Object[] vals = new Object[cols.names.length];
        for (int i = 0; i < cols.names.length; i++) {
            boolean present = in.readBoolean();
            if (!present) {
                vals[i] = null;
                continue;
            }
            switch (cols.types[i]) {
                case INT:
                    vals[i] = in.readLong();
                    break;
                case REAL:
                    vals[i] = in.readDouble();
                    break;
                default: // TEXT
                    vals[i] = readStr(in);
            }
        }
        return vals;
    }

    /**
     * Inserts one game subtree atomically if its ugi is new. Returns {@code true}
     * if it was inserted, {@code false} if skipped (no merge key or already
     * present). On any SQL error the transaction is rolled back and the
     * exception is rethrown for the caller to log.
     */
    private static boolean insertGameIfNew(Connection conn, GameData g) throws Exception {
        String ugi = (String) g.game[GAME.indexOf("ugi")];
        if (ugi == null || ugi.isEmpty()) {
            LOGGER.log(Level.FINE, "StatsSync: game without ugi skipped (no merge key)");
            return false; // cannot deduplicate without a key
        }
        if (selectId(conn, "SELECT id FROM game WHERE ugi = ?", ugi) != null) {
            LOGGER.log(Level.FINE, "StatsSync: game already present, skipped (ugi={0})", ugi);
            return false; // idempotent: already have it
        }

        // 'local' is a per-machine flag ("I hosted this table here") that the
        // recover-last-game feature reads. A received game was, by definition, not
        // hosted on this machine — if it had been, the dedup check above would have
        // skipped it — so it must never inherit the exporter's local=1.
        g.game[GAME.indexOf("local")] = 0L;

        boolean previousAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            long gameId = insert(conn, "game", GAME, null, 0L, g.game, true);
            for (HandData h : g.hands) {
                long handId = insert(conn, "hand", HAND, "id_game", gameId, h.hand, true);
                for (Object[] a : h.actions) {
                    insert(conn, "action", ACTION, "id_hand", handId, a, false);
                }
                for (Object[] s : h.showdowns) {
                    insert(conn, "showdown", SHOWDOWN, "id_hand", handId, s, false);
                }
                for (Object[] b : h.balances) {
                    insert(conn, "balance", BALANCE, "id_hand", handId, b, false);
                }
                for (Object[] c : h.showcards) {
                    insert(conn, "showcards", SHOWCARDS, "id_hand", handId, c, false);
                }
            }
            conn.commit();
            LOGGER.log(Level.FINE, "StatsSync: imported game ugi={0} ({1} hands)",
                    new Object[]{ugi, g.hands.size()});
            return true;
        } catch (Exception ex) {
            conn.rollback();
            throw ex;
        } finally {
            // Restore autocommit even if the connection is sick; if THIS throws, the
            // shared connection would otherwise be stuck in autocommit=false. Swallow
            // and log (parity with Crupier's transaction sites).
            try {
                conn.setAutoCommit(previousAutoCommit);
            } catch (Exception restoreEx) {
                LOGGER.log(Level.WARNING, "StatsSync: could not restore autocommit after import", restoreEx);
            }
        }
    }

    /**
     * Inserts one row. {@code fkCol}/{@code fkVal} prepend the parent FK (null
     * for the root game). When {@code returnId} is set the generated primary key
     * is returned, otherwise {@code -1}.
     */
    private static long insert(Connection conn, String table, Cols cols, String fkCol, long fkVal, Object[] vals, boolean returnId) throws Exception {
        String sql = cols.insertSql(table, fkCol);
        try (PreparedStatement ps = returnId
                ? conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
                : conn.prepareStatement(sql)) {
            int idx = 1;
            if (fkCol != null) {
                ps.setLong(idx++, fkVal);
            }
            for (int i = 0; i < cols.names.length; i++) {
                bind(ps, idx++, cols.types[i], vals[i]);
            }
            ps.executeUpdate();
            if (!returnId) {
                return -1;
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
                throw new IOException("no generated key for " + table);
            }
        }
    }

    private static void bind(PreparedStatement ps, int idx, char type, Object v) throws Exception {
        switch (type) {
            case INT:
                if (v == null) {
                    ps.setNull(idx, Types.INTEGER);
                } else {
                    ps.setLong(idx, (Long) v);
                }
                break;
            case REAL:
                if (v == null) {
                    ps.setNull(idx, Types.REAL);
                } else {
                    ps.setDouble(idx, (Double) v);
                }
                break;
            default: // TEXT
                if (v == null) {
                    ps.setNull(idx, Types.VARCHAR);
                } else {
                    ps.setString(idx, (String) v);
                }
        }
    }

    // =========================================================================
    // Low-level codec / SQL helpers
    // =========================================================================

    private static void writePresence(DataOutputStream out, boolean present) throws IOException {
        out.writeBoolean(present);
    }

    private static void writeStr(DataOutputStream out, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        out.writeInt(b.length);
        out.write(b);
    }

    private static String readStr(DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len < 0 || len > MAX_STRING_BYTES) {
            throw new IOException("string length out of bounds: " + len);
        }
        byte[] b = new byte[len];
        in.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    private static byte[] inflate(byte[] blob) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(blob))) {
            int n;
            while ((n = gz.read(buf)) != -1) {
                total += n;
                if (total > MAX_INFLATED_BYTES) {
                    throw new IOException("inflated blob exceeds " + MAX_INFLATED_BYTES + " bytes");
                }
                out.write(buf, 0, n);
            }
        }
        return out.toByteArray();
    }

    private static Long selectId(Connection conn, String sql, String arg) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, arg);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    private static List<Long> selectIds(Connection conn, String sql, long arg) throws Exception {
        List<Long> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, arg);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getLong(1));
                }
            }
        }
        return out;
    }

    // =========================================================================
    // Small value holders
    // =========================================================================

    private static final class Cols {

        final String[] names;
        final char[] types;
        final String selectList;

        Cols(String[] specs) {
            names = new String[specs.length];
            types = new char[specs.length];
            StringBuilder sel = new StringBuilder();
            for (int i = 0; i < specs.length; i++) {
                int c = specs[i].indexOf(':');
                names[i] = specs[i].substring(0, c);
                types[i] = specs[i].charAt(c + 1);
                if (i > 0) {
                    sel.append(',');
                }
                sel.append(names[i]);
            }
            selectList = sel.toString();
        }

        int indexOf(String name) {
            for (int i = 0; i < names.length; i++) {
                if (names[i].equals(name)) {
                    return i;
                }
            }
            return -1;
        }

        String insertSql(String table, String fkCol) {
            StringBuilder colsPart = new StringBuilder();
            StringBuilder qsPart = new StringBuilder();
            if (fkCol != null) {
                colsPart.append(fkCol);
                qsPart.append('?');
            }
            for (String name : names) {
                if (colsPart.length() > 0) {
                    colsPart.append(',');
                    qsPart.append(',');
                }
                colsPart.append(name);
                qsPart.append('?');
            }
            return "INSERT INTO " + table + "(" + colsPart + ") VALUES (" + qsPart + ")";
        }
    }

    private static final class GameData {

        Object[] game;
        final List<HandData> hands = new ArrayList<>();
    }

    private static final class HandData {

        Object[] hand;
        List<Object[]> actions;
        List<Object[]> showdowns;
        List<Object[]> balances;
        List<Object[]> showcards;
    }
}
