package com.tonikelope.coronapoker.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Arrays;
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

            assertEquals(IdentityTrustStore.Observation.CHANGED,
                    store.observe("Invitado", changed));
            assertFalse(store.isVerified("Invitado", first));
            assertFalse(store.isVerified("Invitado", changed),
                    "a rotated key must revoke the previous OOB verification");
            assertFalse(store.markVerified("Invitado", first),
                    "stale dialog material must never verify a replaced key");
            assertTrue(store.markVerified("Invitado", changed));
            assertTrue(store.isVerified("Invitado", changed));
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
