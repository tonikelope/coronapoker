package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

public class MisdealWireTest {

    @Test
    public void parsesTheSingleCurrentMisdealShape() {
        String reason = "peer.state_inconsistent";
        String encoded = Base64.getEncoder().encodeToString(reason.getBytes(StandardCharsets.UTF_8));
        assertEquals(reason, MisdealWire.parse(
                new String[]{"GAME", "7", "MISDEAL", encoded}));
    }

    @Test
    public void rejectsMalformedMisdealInsteadOfRefundingAndContinuing() {
        assertThrows(IllegalArgumentException.class, () -> MisdealWire.parse(
                new String[]{"GAME", "7", "MISDEAL"}));
        assertThrows(IllegalArgumentException.class, () -> MisdealWire.parse(
                new String[]{"GAME", "7", "MISDEAL", "%%%"}));
        assertThrows(IllegalArgumentException.class, () -> MisdealWire.parse(
                new String[]{"GAME", "7", "MISDEAL", "", "ignored"}));
        assertThrows(IllegalArgumentException.class, () -> MisdealWire.parse(
                new String[]{"GAME", "7", "MISDEAL", ""}));
        assertThrows(IllegalArgumentException.class, () -> MisdealWire.parse(
                new String[]{"GAME", "7", "MISDEAL", "cGVlci5zdGF0ZV9pbmNvbnNpc3RlbnQ"}));
    }
}
