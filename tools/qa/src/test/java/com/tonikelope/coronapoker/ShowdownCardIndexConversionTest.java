package com.tonikelope.coronapoker;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class ShowdownCardIndexConversionTest {

    @Test
    void uiCardRangeMapsToCanonicalCryptoIndicesIncludingCard52() {
        Card first = new Card(false);
        first.iniciarConValorNumerico(1);
        Card last = new Card(false);
        last.iniciarConValorNumerico(52);

        assertEquals(0, first.getCardIndex());
        assertEquals(51, last.getCardIndex(),
                "UI card 52 must sign and verify as canonical SRA index 51");

        IdentityManager signer = IdentityManager.initializeForNick(
                "__qa_showdown_index_" + System.nanoTime());
        byte[] handId = new byte[CanonicalActionRecord.HAND_ID_BYTES];
        byte[] key = new byte[32];
        byte[] signature = signer.signShowdownReveal(handId, "bot", key,
                first.getCardIndex(), last.getCardIndex());

        assertTrue(IdentityManager.verifyShowdownReveal(signer.getPublicKey(), handId,
                "bot", key, 0, 51, signature));
    }

    @Test
    void showdownSigningAndBoardCollisionChecksUseCanonicalIndices() throws Exception {
        Path root = locateRoot();
        String source = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java"));

        String signing = slice(source,
                "public String signShowdownRevealForBroadcast(",
                "public boolean unlockPlayerCardsWithSRAKey(");
        assertTrue(signing.contains("getHoleCard1().getCardIndex()"));
        assertTrue(signing.contains("getHoleCard2().getCardIndex()"));

        String verification = slice(source,
                "private LinkedHashMap<String, int[]> verifyPotCardsEnvelope(",
                "private static boolean sameUnorderedCards(");
        assertTrue(verification.contains("boardCard.getCardIndex()"),
                "board duplicate detection must use the same 0-51 domain as POTCARDS");
    }

    private static String slice(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        assertTrue(from >= 0 && to > from, "expected production source block not found");
        return source.substring(from, to);
    }

    private static Path locateRoot() {
        Path start = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (Path path = start; path != null; path = path.getParent()) {
            if (Files.isRegularFile(path.resolve("tools/qa/pom.xml"))) {
                return path;
            }
        }
        throw new IllegalStateException("CoronaPoker root not found from " + start);
    }
}
