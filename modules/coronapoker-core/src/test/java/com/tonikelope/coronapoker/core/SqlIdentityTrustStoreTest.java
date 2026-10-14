package com.tonikelope.coronapoker.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqlIdentityTrustStoreTest {

    @TempDir Path temporary;

    @Test void pinsMarksAndRevokesChangedIdentities() throws Exception {
        DatabaseService database = new DatabaseService(
                temporary.resolve("identity.db").toString());
        database.start();
        try {
            SqlIdentityTrustStore store = new SqlIdentityTrustStore(database);
            byte[] first = key(1);
            byte[] changed = key(2);

            assertEquals(IdentityTrustStore.Observation.NEW,
                    store.observe("Invitado", first));
            assertFalse(store.isVerified("Invitado", first));
            assertTrue(store.markVerified("Invitado", first));
            assertTrue(store.isVerified("Invitado", first));
            assertEquals(IdentityTrustStore.Observation.MATCH,
                    store.observe("Invitado", first));
            assertTrue(store.isVerified("Invitado", first));
            assertTrue(store.drainIdentityChanges().isEmpty());

            assertEquals(IdentityTrustStore.Observation.CHANGED,
                    store.observe("Invitado", changed));
            assertFalse(store.isVerified("Invitado", first));
            assertFalse(store.isVerified("Invitado", changed),
                    "a rotated key must revoke the previous OOB verification");
            assertFalse(store.markVerified("Invitado", first),
                    "stale dialog material must never verify a replaced key");
            assertEquals(List.of(
                    new IdentityTrustStore.IdentityChange("Invitado", true)),
                    store.drainIdentityChanges());
            assertTrue(store.drainIdentityChanges().isEmpty(),
                    "a verified rotation notice must be delivered once");
            assertTrue(store.markVerified("Invitado", changed));
            assertTrue(store.isVerified("Invitado", changed));
        } finally {
            database.close();
        }
    }

    @Test void unverifiedIdentityRotationRemainsAvailableForLogs()
            throws Exception {
        DatabaseService database = new DatabaseService(
                temporary.resolve("identity-unverified.db").toString());
        database.start();
        try {
            SqlIdentityTrustStore store = new SqlIdentityTrustStore(database);
            assertEquals(IdentityTrustStore.Observation.NEW,
                    store.observe("Invitado", key(5)));
            assertEquals(IdentityTrustStore.Observation.CHANGED,
                    store.observe("Invitado", key(6)));
            assertEquals(List.of(
                    new IdentityTrustStore.IdentityChange("Invitado", false)),
                    store.drainIdentityChanges(),
                    "unverified rotations stay available for logs while the frontend suppresses their popup");
        } finally {
            database.close();
        }
    }

    @Test void updateFailureCannotDisguiseMatchOrChangedAsNew()
            throws Exception {
        DatabaseService database = new DatabaseService(
                temporary.resolve("identity-update-failure.db").toString());
        database.start();
        try {
            SqlIdentityTrustStore store = new SqlIdentityTrustStore(database);
            byte[] first = key(3);
            byte[] changed = key(4);
            assertEquals(IdentityTrustStore.Observation.NEW,
                    store.observe("Invitado", first));
            try (var statement = database.connection().createStatement()) {
                statement.execute("CREATE TRIGGER reject_identity_update "
                        + "BEFORE UPDATE ON known_identities BEGIN "
                        + "SELECT RAISE(FAIL, 'simulated update failure'); END");
            }

            assertEquals(IdentityTrustStore.Observation.MATCH,
                    store.observe("Invitado", first));
            assertEquals(IdentityTrustStore.Observation.CHANGED,
                    store.observe("Invitado", changed));
            assertTrue(store.drainIdentityChanges().isEmpty(),
                    "a failed pin replacement must not emit a false identity-change notice");
        } finally {
            database.close();
        }
    }

    private static byte[] key(int value) {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) value);
        return key;
    }
}
