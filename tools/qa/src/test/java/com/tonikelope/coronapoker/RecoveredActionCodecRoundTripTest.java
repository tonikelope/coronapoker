package com.tonikelope.coronapoker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RecoveredActionCodecRoundTripTest {
    @Test
    public void v1UsesExactIntegerCents() {
        String encoded = RecoveredActionCodec.encodeV1("álîce", Player.BET,
                12.34d, null, null);
        RecoveredActionCodec.Result decoded = RecoveredActionCodec.decode(encoded);
        assertTrue(decoded.isOk());
        assertEquals("álîce", decoded.value().actor());
        assertEquals(Player.BET, decoded.value().decision());
        assertEquals(1234L, decoded.value().amountCents());
    }

    @Test
    public void unsupportedFormatIsRejectedBecauseMixedVersionsCannotConnect() {
        RecoveredActionCodec.Result decoded = RecoveredActionCodec.decode("YWxpY2U=#3#12.34#*#*");
        assertFalse(decoded.isOk());
    }
}
