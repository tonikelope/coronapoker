/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.smoke;

import com.tonikelope.coronapoker.Init;
import com.tonikelope.coronapoker.TOFUResolver;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression contract for fail-closed TOFU identity pinning. */
class TofuResolverOutcomeSmoke {

    private Connection realConn;
    private Connection previousSQLITE;

    @BeforeEach
    void setUpDb() throws Exception {
        Class.forName("org.sqlite.JDBC");
        realConn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement s = realConn.createStatement()) {
            s.execute("CREATE TABLE known_identities("
                    + "nick TEXT PRIMARY KEY, pubkey BLOB NOT NULL, "
                    + "first_seen INTEGER NOT NULL, last_seen INTEGER NOT NULL, "
                    + "sessions_count INTEGER NOT NULL DEFAULT 0, "
                    + "verified_oob INTEGER NOT NULL DEFAULT 0)");
        }
        previousSQLITE = Init.SQLITE;
        Init.SQLITE = realConn;
    }

    @AfterEach
    void tearDownDb() throws Exception {
        Init.SQLITE = previousSQLITE;
        if (realConn != null && !realConn.isClosed()) {
            realConn.close();
        }
    }

    private static byte[] pubkey(int seed) {
        byte[] out = new byte[32];
        for (int i = 0; i < 32; i++) {
            out[i] = (byte) (seed + i);
        }
        return out;
    }

    @Test
    void happyNew() {
        TOFUResolver.Resolution r = TOFUResolver.resolve("alice", pubkey(1));
        assertEquals(TOFUResolver.Outcome.NEW, r.getOutcome());
        assertEquals(1, r.getSessionsCount());
    }

    @Test
    void happyMatch() {
        TOFUResolver.resolve("bob", pubkey(2));
        TOFUResolver.Resolution r = TOFUResolver.resolve("bob", pubkey(2));
        assertEquals(TOFUResolver.Outcome.MATCH, r.getOutcome());
        assertEquals(2, r.getSessionsCount());
    }

    @Test
    @DisplayName("CHANGED rejects rotation and preserves the trusted pin")
    void changedPreservesPinTrustAndCounter() {
        byte[] pinned = pubkey(3);
        TOFUResolver.resolve("eve", pinned);
        assertTrue(TOFUResolver.markVerified("eve", pinned));

        TOFUResolver.Resolution r = TOFUResolver.resolve("eve", pubkey(99));

        assertEquals(TOFUResolver.Outcome.CHANGED, r.getOutcome());
        assertEquals(1, r.getSessionsCount());
        assertTrue(r.isVerifiedOob());
        assertArrayEquals(pinned, TOFUResolver.getPinnedPubkey("eve"));
        assertEquals(1, identityInt("eve", "verified_oob"));
        assertEquals(1, identityInt("eve", "sessions_count"));
    }

    @Test
    void changedDoesNotAttemptImplicitRotation() {
        TOFUResolver.resolve("alice", pubkey(1));
        Init.SQLITE = wrapWithSqlFailure(realConn, "UPDATE", new AtomicBoolean(true));

        TOFUResolver.Resolution r = TOFUResolver.resolve("alice", pubkey(99));

        assertEquals(TOFUResolver.Outcome.CHANGED, r.getOutcome());
    }

    @Test
    void matchUpdateFailureReturnsError() {
        TOFUResolver.resolve("carol", pubkey(7));
        Init.SQLITE = wrapWithSqlFailure(realConn, "UPDATE", new AtomicBoolean(true));

        TOFUResolver.Resolution r = TOFUResolver.resolve("carol", pubkey(7));

        assertEquals(TOFUResolver.Outcome.ERROR, r.getOutcome());
    }

    @Test
    void insertFailureReturnsError() {
        Init.SQLITE = wrapWithSqlFailure(realConn, "INSERT", new AtomicBoolean(true));

        TOFUResolver.Resolution r = TOFUResolver.resolve("dave", pubkey(11));

        assertEquals(TOFUResolver.Outcome.ERROR, r.getOutcome());
    }

    @Test
    void canonicallyEquivalentNickCannotRepinIdentity() {
        byte[] pinned = pubkey(21);
        assertEquals(TOFUResolver.Outcome.NEW,
                TOFUResolver.resolve("\u00e9", pinned).getOutcome());

        TOFUResolver.Resolution changed = TOFUResolver.resolve("e\u0301", pubkey(77));

        assertEquals(TOFUResolver.Outcome.CHANGED, changed.getOutcome());
        assertArrayEquals(pinned, TOFUResolver.getPinnedPubkey("e\u0301"));
        assertEquals(1, identityRowCount());
    }

    @Test
    void legacyDecomposedPinIsMigratedToNfc() throws Exception {
        byte[] pinned = pubkey(31);
        insertIdentity("e\u0301", pinned);

        TOFUResolver.Resolution match = TOFUResolver.resolve("\u00e9", pinned);

        assertEquals(TOFUResolver.Outcome.MATCH, match.getOutcome());
        assertEquals(1, identityRowCount());
        try (java.sql.PreparedStatement ps = realConn.prepareStatement(
                "SELECT COUNT(*) FROM known_identities WHERE nick = ?")) {
            ps.setString(1, "\u00e9");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
        }
    }

    @Test
    void conflictingLegacyCanonicalPinsFailClosed() throws Exception {
        insertIdentity("\u00e9", pubkey(41));
        insertIdentity("e\u0301", pubkey(42));

        TOFUResolver.Resolution result = TOFUResolver.resolve("\u00e9", pubkey(41));

        assertEquals(TOFUResolver.Outcome.ERROR, result.getOutcome());
        assertEquals(2, identityRowCount());
    }

    private void insertIdentity(String nick, byte[] key) throws SQLException {
        try (java.sql.PreparedStatement ps = realConn.prepareStatement(
                "INSERT INTO known_identities(nick,pubkey,first_seen,last_seen,sessions_count,verified_oob) "
                + "VALUES(?,?,1,1,1,0)")) {
            ps.setString(1, nick);
            ps.setBytes(2, key);
            assertEquals(1, ps.executeUpdate());
        }
    }

    private int identityRowCount() {
        try (Statement statement = realConn.createStatement();
                ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM known_identities")) {
            assertTrue(rs.next());
            return rs.getInt(1);
        } catch (SQLException ex) {
            throw new AssertionError(ex);
        }
    }

    private int identityInt(String nick, String column) {
        try (java.sql.PreparedStatement ps = realConn.prepareStatement(
                "SELECT " + column + " FROM known_identities WHERE nick = ?")) {
            ps.setString(1, nick);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        } catch (SQLException ex) {
            throw new AssertionError(ex);
        }
    }

    private static Connection wrapWithSqlFailure(Connection real, String prefix, AtomicBoolean enabled) {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if (enabled.get()
                        && "prepareStatement".equals(method.getName())
                        && args != null && args.length > 0
                        && args[0] instanceof String
                        && ((String) args[0]).trim().toUpperCase().startsWith(prefix)) {
                    throw new SQLException("simulated " + prefix + " failure");
                }
                try {
                    return method.invoke(real, args);
                } catch (java.lang.reflect.InvocationTargetException ex) {
                    throw ex.getCause();
                }
            }
        };
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class[]{Connection.class}, handler);
    }
}
