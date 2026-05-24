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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * AAA test del fix de TOFUResolver: el catch (Exception) ya no enmascara
 * CHANGED como NEW cuando el SELECT detectó pubkey distinto pero el UPDATE
 * posterior falló.
 *
 * Cobertura:
 *   1. Happy path NEW (primer encuentro)
 *   2. Happy path MATCH (mismo pubkey)
 *   3. Happy path CHANGED (pubkey distinto)
 *   4. UPDATE de CHANGED falla → outcome SIGUE siendo CHANGED (no NEW)
 *   5. UPDATE de MATCH falla → outcome SIGUE siendo MATCH (no NEW)
 *
 * Test 4 es el bug que el fix arregla. Tests 1-3 validan que no hay regresión.
 *
 * Setup: instala una Connection SQLite en memoria en Init.SQLITE para que
 * Helpers.getSQLITE() la devuelva. Crea la tabla known_identities a mano.
 * Para los tests 4-5, envuelve la Connection con un dynamic Proxy que hace
 * fallar prepareStatement("UPDATE...") en demanda.
 */
class TofuResolverOutcomeSmoke {

    private Connection realConn;
    private Connection previousSQLITE;

    @BeforeEach
    public void setUpDb() throws Exception {
        Class.forName("org.sqlite.JDBC");
        realConn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement s = realConn.createStatement()) {
            s.execute("CREATE TABLE known_identities("
                    + "nick TEXT PRIMARY KEY, pubkey BLOB NOT NULL, "
                    + "first_seen INTEGER NOT NULL, last_seen INTEGER NOT NULL, "
                    + "sessions_count INTEGER NOT NULL DEFAULT 0, "
                    + "verified_oob INTEGER NOT NULL DEFAULT 0)");
        }
        // Save previous SQLITE so we don't bleed test state into anything else.
        previousSQLITE = Init.SQLITE;
        Init.SQLITE = realConn;
    }

    @AfterEach
    public void tearDownDb() throws Exception {
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
    @DisplayName("Happy path: primer encuentro devuelve NEW")
    void happyNew() {
        TOFUResolver.Resolution r = TOFUResolver.resolve("alice", pubkey(1));
        assertEquals(TOFUResolver.Outcome.NEW, r.getOutcome());
        assertEquals(1, r.getSessionsCount());
    }

    @Test
    @DisplayName("Happy path: segundo encuentro con mismo pubkey devuelve MATCH")
    void happyMatch() {
        TOFUResolver.resolve("bob", pubkey(2));
        TOFUResolver.Resolution r = TOFUResolver.resolve("bob", pubkey(2));
        assertEquals(TOFUResolver.Outcome.MATCH, r.getOutcome());
        assertEquals(2, r.getSessionsCount());
    }

    @Test
    @DisplayName("Happy path: segundo encuentro con DIFERENTE pubkey devuelve CHANGED")
    void happyChanged() {
        TOFUResolver.resolve("eve", pubkey(3));
        TOFUResolver.Resolution r = TOFUResolver.resolve("eve", pubkey(99));
        assertEquals(TOFUResolver.Outcome.CHANGED, r.getOutcome());
        assertEquals(2, r.getSessionsCount());
    }

    @Test
    @DisplayName("CRITICO: UPDATE de CHANGED falla → outcome sigue siendo CHANGED (no NEW)")
    void changedSurvivesUpdateFailure() throws Exception {
        // Setup: registramos alice con pubkey(1). Después llamamos resolve con un
        // pubkey distinto pero hacemos que el UPDATE falle.
        TOFUResolver.resolve("alice", pubkey(1));

        // Wrap connection con proxy que falla en UPDATE.
        AtomicBoolean failUpdates = new AtomicBoolean(true);
        Init.SQLITE = wrapWithUpdateFailure(realConn, failUpdates);

        TOFUResolver.Resolution r = TOFUResolver.resolve("alice", pubkey(99));

        assertEquals(TOFUResolver.Outcome.CHANGED, r.getOutcome(),
                "BUG: el catch enmascaró CHANGED como NEW (silencia un MITM exitoso)");
        assertNotEquals(TOFUResolver.Outcome.NEW, r.getOutcome());
    }

    @Test
    @DisplayName("UPDATE de MATCH falla → outcome sigue siendo MATCH (no NEW)")
    void matchSurvivesUpdateFailure() throws Exception {
        TOFUResolver.resolve("carol", pubkey(7));

        AtomicBoolean failUpdates = new AtomicBoolean(true);
        Init.SQLITE = wrapWithUpdateFailure(realConn, failUpdates);

        TOFUResolver.Resolution r = TOFUResolver.resolve("carol", pubkey(7));

        assertEquals(TOFUResolver.Outcome.MATCH, r.getOutcome(),
                "MATCH degradó a NEW por fallo UPDATE — habría reseteado el trust sin razón");
    }

    /**
     * Dynamic proxy que delega TODO al wrapped Connection EXCEPT prepareStatement
     * con SQL que empiece por UPDATE, en cuyo caso lanza SQLException.
     */
    private static Connection wrapWithUpdateFailure(Connection real, AtomicBoolean failUpdates) {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if (failUpdates.get()
                        && "prepareStatement".equals(method.getName())
                        && args != null && args.length > 0
                        && args[0] instanceof String) {
                    String sql = (String) args[0];
                    if (sql.trim().toUpperCase().startsWith("UPDATE")) {
                        throw new SQLException("simulated UPDATE failure");
                    }
                }
                try {
                    return method.invoke(real, args);
                } catch (java.lang.reflect.InvocationTargetException ite) {
                    throw ite.getCause();
                }
            }
        };
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class[]{Connection.class},
                handler);
    }
}
