package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CommunityPriorCardDomainTest {

    @Test
    void storedOneBasedRunoutUsesTheSameExplicitBoundaryConversion() {
        assertEquals(0, Card.cardIndexFromOneBased(1));
        assertEquals(51, Card.cardIndexFromOneBased(52));
        assertEquals(-1, Card.cardIndexFromOneBased(0));
        assertEquals(-1, Card.cardIndexFromOneBased(53));
    }

    @Test
    void liveCommunityDuplicateGateUsesCanonicalZeroBasedIndices() throws Exception {
        String source = Files.readString(findCrupierSource(), StandardCharsets.UTF_8);
        String method = source.substring(
                source.indexOf("private java.util.List<Integer> priorCommunityCardsForCurrentStreet()"),
                source.indexOf("private boolean recibirCartasComunitarias()"));

        assertFalse(method.contains("getCartaComoEntero()"),
                "SRA community indices are 0..51; UI card values are 1..52");
        assertTrue(method.contains("getCardIndex()"),
                "live flop/turn cards must cross the UI-to-SRA boundary explicitly");
        assertFalse(method.contains("prior.addAll(this.rit_side_a_runout_cards)"),
                "RIT side-A values are stored for the 1..52 Monte Carlo deck and must be converted");
        assertTrue(method.contains("Card.cardIndexFromOneBased(oneBased)"));
    }

    private static Path findCrupierSource() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && current != null; i++, current = current.getParent()) {
            Path candidate = current.resolve(
                    "src/main/java/com/tonikelope/coronapoker/Crupier.java");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Crupier.java not found");
    }
}
