package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

public class TableTerminationWireTest {

    @Test
    public void acceptsOnlyTheCurrentServerExitShapes() {
        TableTerminationWire.ExitCommand plain = TableTerminationWire.parse(
                new String[]{"GAME", "42", "SERVEREXIT"});
        assertFalse(plain.recover());
        assertNull(plain.password());

        TableTerminationWire.ExitCommand recoverWithoutPassword = TableTerminationWire.parse(
                new String[]{"GAME", "42", "SERVEREXITRECOVER"});
        assertTrue(recoverWithoutPassword.recover());
        assertNull(recoverWithoutPassword.password());

        String encoded = Base64.getEncoder().encodeToString("secret-123".getBytes(StandardCharsets.UTF_8));
        TableTerminationWire.ExitCommand recoverWithPassword = TableTerminationWire.parse(
                new String[]{"GAME", "42", "SERVEREXITRECOVER", encoded});
        assertTrue(recoverWithPassword.recover());
        assertEquals("secret-123", recoverWithPassword.password());
    }

    @Test
    public void rejectsTrailingFieldsAndMalformedPasswordsBeforeChangingLifecycleState() {
        assertThrows(IllegalArgumentException.class, () -> TableTerminationWire.parse(
                new String[]{"GAME", "42", "SERVEREXIT", "ignored"}));
        assertThrows(IllegalArgumentException.class, () -> TableTerminationWire.parse(
                new String[]{"GAME", "42", "SERVEREXITRECOVER", "%%%"}));
        assertThrows(IllegalArgumentException.class, () -> TableTerminationWire.parse(
                new String[]{"GAME", "42", "SERVEREXITRECOVER", "", "ignored"}));
        assertThrows(IllegalArgumentException.class, () -> TableTerminationWire.parse(
                new String[]{"GAME", "42", "SERVEREXITRECOVER", "YQ"}));
    }

    @Test
    public void gracefulReaderClassificationUsesTheSameStrictParser() {
        assertTrue(TableTerminationWire.isValidTerminationFrame(
                "GAME#42#SERVEREXITRECOVER#c2VjcmV0"));
        assertFalse(TableTerminationWire.isValidTerminationFrame(
                "GAME#42#SERVEREXITRECOVER#%%%"));
        assertFalse(TableTerminationWire.isValidTerminationFrame(
                "GAME#42#SERVEREXIT#ignored"));
    }
}
