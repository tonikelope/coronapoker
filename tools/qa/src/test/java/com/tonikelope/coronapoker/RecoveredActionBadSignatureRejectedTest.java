package com.tonikelope.coronapoker;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RecoveredActionBadSignatureRejectedTest {
    @Test
    public void schemaValidActionStillRequiresSignatureVerification() {
        byte[] record = new byte[CanonicalActionRecord.RECORD_BYTES];
        byte[] signature = new byte[HandStateChain.SIG_BYTES];
        String wire = "V1#YQ==#1#0#" + Base64.getEncoder().encodeToString(record)
                + "#" + Base64.getEncoder().encodeToString(signature);
        RecoveredActionCodec.Result decoded = RecoveredActionCodec.decode(wire);
        assertTrue(decoded.isOk());
        assertFalse(IdentityManager.verifyAction(new byte[32],
                decoded.value().record(), decoded.value().signature()));
        assertFalse(Crupier.recoveredActionSignatureIsValid(null,
                decoded.value().record(), decoded.value().signature()),
                "missing identity key must fail closed during recovery");
        assertFalse(Crupier.recoveredActionSignatureIsValid(new byte[32],
                decoded.value().record(), decoded.value().signature()));
    }
}
