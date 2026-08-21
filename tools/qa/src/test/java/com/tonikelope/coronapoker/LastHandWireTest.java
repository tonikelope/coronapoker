package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

public class LastHandWireTest {

    @Test
    public void parsesOffFinalAndRecoverModes() {
        LastHandWire.Command off = LastHandWire.parse(new String[]{"GAME", "1", "LASTHAND", "0"});
        assertFalse(off.enabled());
        assertFalse(off.recover());
        assertNull(off.password());

        LastHandWire.Command last = LastHandWire.parse(new String[]{"GAME", "2", "LASTHAND", "1"});
        assertTrue(last.enabled());
        assertFalse(last.recover());
        assertNull(last.password());

        String encoded = Base64.getEncoder().encodeToString("secret".getBytes(StandardCharsets.UTF_8));
        LastHandWire.Command recover = LastHandWire.parse(
                new String[]{"GAME", "3", "LASTHAND", "2", encoded});
        assertTrue(recover.enabled());
        assertTrue(recover.recover());
        assertEquals("secret", recover.password());
    }

    @Test
    public void rejectsCompatibilityAndMalformedForms() {
        assertThrows(IllegalArgumentException.class, () -> LastHandWire.parse(
                new String[]{"GAME", "1", "LASTHAND", "true"}));
        assertThrows(IllegalArgumentException.class, () -> LastHandWire.parse(
                new String[]{"GAME", "1", "LASTHAND", "0", "ignored"}));
        assertThrows(IllegalArgumentException.class, () -> LastHandWire.parse(
                new String[]{"GAME", "1", "LASTHAND", "1", "ignored"}));
        assertThrows(IllegalArgumentException.class, () -> LastHandWire.parse(
                new String[]{"GAME", "1", "LASTHAND", "2", "%%%"}));
        assertThrows(IllegalArgumentException.class, () -> LastHandWire.parse(
                new String[]{"GAME", "1", "LASTHAND", "2", "c2VjcmV0", "ignored"}));
    }
}
