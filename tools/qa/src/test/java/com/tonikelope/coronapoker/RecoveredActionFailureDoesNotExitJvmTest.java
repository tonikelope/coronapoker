package com.tonikelope.coronapoker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class RecoveredActionFailureDoesNotExitJvmTest {
    @Test
    public void arbitraryMalformedInputsAlwaysReturnAnError() {
        String[] malformed = {null, "", "#", "@@@@", "V1", "V1#######",
            "!!!!#3#1", "YQ==#99999999999999999999#1", "YQ==#3#-1",
            "YQ==#3#1.001", "YQ==#3#1e999999", "YQ==#3#NaN"};
        assertDoesNotThrow(() -> {
            for (String input : malformed) {
                assertFalse(RecoveredActionCodec.decode(input).isOk());
            }
        });
    }

    @Test
    public void deterministicFuzzNeverEscapesTheCodec() {
        java.util.Random random = new java.util.Random(0x5245434f56455259L);
        assertDoesNotThrow(() -> {
            for (int sample = 0; sample < 10_000; sample++) {
                int length = random.nextInt(160);
                StringBuilder candidate = new StringBuilder(length);
                for (int i = 0; i < length; i++) {
                    candidate.append((char) random.nextInt(0x10000));
                }
                RecoveredActionCodec.Result result = RecoveredActionCodec.decode(candidate.toString());
                org.junit.jupiter.api.Assertions.assertNotNull(result);
            }
        });
    }
}
