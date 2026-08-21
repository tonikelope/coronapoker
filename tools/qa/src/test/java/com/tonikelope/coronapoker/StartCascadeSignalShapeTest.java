package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

public class StartCascadeSignalShapeTest {

    @Test
    void currentStartSignalHasExactlyThreeFields() {
        assertTrue(Crupier.startCascadeSignalHasCurrentShape(
                new String[]{"GAME", "7", "START_SRA_CASCADE"}));
        assertFalse(Crupier.startCascadeSignalHasCurrentShape(
                new String[]{"GAME", "7", "START_SRA_CASCADE", "extra"}));
        assertFalse(Crupier.startCascadeSignalHasCurrentShape(
                new String[]{"GAME", "7", "START_SRA_CASCADE", ""}));
        assertFalse(Crupier.startCascadeSignalHasCurrentShape(
                new String[]{"GAME", "7"}));
    }

    @Test
    void malformedKnownStartSignalClosesHostChannel() throws Exception {
        String source = Files.readString(locateRoot().resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java"));
        int wait = source.indexOf("private void readyForNextHand()");
        int split = source.indexOf("comando.split(\"#\", -1)", wait);
        int known = source.indexOf("partes[2].equals(\"START_SRA_CASCADE\")", wait);
        int shape = source.indexOf("!startCascadeSignalHasCurrentShape(partes)", known);
        int reject = source.indexOf("this.received_commands.reject(comando)", shape);
        int finish = source.indexOf("setFin_de_la_transmision(true)", reject);
        int close = source.indexOf("closeClientSocket()", finish);

        assertTrue(wait >= 0 && wait < split && split < known && known < shape);
        assertTrue(shape < reject && reject < finish && finish < close);

        String normalized = source.replace("\r\n", "\n");
        assertTrue(normalized.contains("readyForNextHand();\n\n"
                + "        if (isFin_de_la_transmision()) {\n"
                + "            return false;\n"
                + "        }"));
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
