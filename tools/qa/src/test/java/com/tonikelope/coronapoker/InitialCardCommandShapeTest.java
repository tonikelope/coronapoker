package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

public class InitialCardCommandShapeTest {

    @Test
    void everyInitialCardCriticalTypeRejectsItsWrongFieldCount() {
        assertTrue(Crupier.isInitialCardCommand("MEGAPACKET"));
        assertTrue(Crupier.initialCardCommandHasCurrentShape(parts("MEGAPACKET", 7)));
        assertFalse(Crupier.initialCardCommandHasCurrentShape(parts("MEGAPACKET", 6)));

        assertTrue(Crupier.isInitialCardCommand("POCKET_CARDS"));
        assertTrue(Crupier.initialCardCommandHasCurrentShape(parts("POCKET_CARDS", 5)));
        assertFalse(Crupier.initialCardCommandHasCurrentShape(parts("POCKET_CARDS", 4)));

        assertTrue(Crupier.isInitialCardCommand("POCKET_DEFERRED"));
        assertTrue(Crupier.initialCardCommandHasCurrentShape(parts("POCKET_DEFERRED", 4)));
        assertFalse(Crupier.initialCardCommandHasCurrentShape(parts("POCKET_DEFERRED", 3)));

        assertTrue(Crupier.isInitialCardCommand("MISDEAL"));
        assertTrue(Crupier.initialCardCommandHasCurrentShape(parts("MISDEAL", 4)));
        assertFalse(Crupier.initialCardCommandHasCurrentShape(parts("MISDEAL", 3)));

        assertFalse(Crupier.isInitialCardCommand("ACTION"));
        assertTrue(Crupier.initialCardCommandHasCurrentShape(parts("ACTION", 3)));
    }

    @Test
    void initialDealConsumerClosesInsteadOfRestoringWrongShape() throws Exception {
        String source = Files.readString(locateRoot().resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java"));
        int receiver = source.indexOf("private ArrayList<String> recibirMisCartas()");
        int shape = source.indexOf("!initialCardCommandHasCurrentShape(partes)", receiver);
        int reject = source.indexOf("this.received_commands.reject(comando)", shape);
        int finish = source.indexOf("setFin_de_la_transmision(true)", reject);
        int close = source.indexOf("closeClientSocket()", finish);
        int result = source.indexOf("return null;", close);

        assertTrue(receiver >= 0 && receiver < shape && shape < reject);
        assertTrue(reject < finish && finish < close && close < result);
        assertFalse(source.contains("Malformed command dropped (receiveMyCards)"));
    }

    private static String[] parts(String type, int count) {
        String[] result = new String[count];
        for (int i = 0; i < count; i++) {
            result[i] = "x";
        }
        if (count > 2) {
            result[2] = type;
        }
        return result;
    }

    private static Path locateRoot() {
        Path start = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (Path path = start; path != null; path = path.getParent()) {
            if (Files.exists(path.resolve("src/main/java/com/tonikelope/coronapoker/Crupier.java"))) {
                return path;
            }
        }
        throw new IllegalStateException("repository root not found");
    }
}
