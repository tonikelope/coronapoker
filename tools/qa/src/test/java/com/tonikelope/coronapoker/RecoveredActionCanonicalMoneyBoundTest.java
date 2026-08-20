package com.tonikelope.coronapoker;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

public class RecoveredActionCanonicalMoneyBoundTest {

    @Test
    public void amountAboveCanonicalTableDomainIsRejected() {
        String actor = Base64.getEncoder().encodeToString("alice".getBytes(StandardCharsets.UTF_8));
        String wire = "V1#" + actor + "#" + Player.BET + "#"
                + (MoneyCents.MAX_CENTS + 1L) + "#*#*";

        RecoveredActionCodec.Result decoded = RecoveredActionCodec.decode(wire);
        assertFalse(decoded.isOk());
        assertEquals(RecoveredActionCodec.Error.BAD_AMOUNT, decoded.error());
    }
}
