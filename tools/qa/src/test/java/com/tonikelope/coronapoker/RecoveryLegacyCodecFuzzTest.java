package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import java.util.Random;
import org.junit.jupiter.api.Test;

public class RecoveryLegacyCodecFuzzTest {
    @Test
    public void arbitraryAndJavaSerializationBytesAreTotalAndRejected() {
        assertFalse(RecoverySnapshotV1.decode(new byte[]{(byte) 0xac, (byte) 0xed, 0, 5}, "session-a").isOk());
        Random random = new Random(0x43505253L);
        for (int i = 0; i < 10_000; i++) {
            byte[] input = new byte[random.nextInt(512)];
            random.nextBytes(input);
            assertDoesNotThrow(() -> assertFalse(RecoverySnapshotV1.decode(input, "session-a").isOk()));
        }
    }
}
