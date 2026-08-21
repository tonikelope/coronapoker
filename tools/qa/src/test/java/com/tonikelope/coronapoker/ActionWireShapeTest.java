package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

public class ActionWireShapeTest {

    @Test
    void currentActionWireAlwaysHasExactlyNineFields() {
        assertTrue(Crupier.actionWireHasCurrentShape(parts(9)));
        assertFalse(Crupier.actionWireHasCurrentShape(parts(5)));
        assertFalse(Crupier.actionWireHasCurrentShape(parts(6)));
        assertFalse(Crupier.actionWireHasCurrentShape(parts(8)));
        assertFalse(Crupier.actionWireHasCurrentShape(parts(10)));
    }

    @Test
    void malformedKnownActionIsRejectedRatherThanRestored() throws Exception {
        String source = Files.readString(locateRoot().resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java"));
        int receiver = source.indexOf("public Object[] readActionFromRemotePlayer(Player jugador)");
        int known = source.indexOf("partes[2].equals(\"ACTION\")", receiver);
        int shape = source.indexOf("!actionWireHasCurrentShape(partes)", known);
        int reject = source.indexOf("this.received_commands.reject(comando)", shape);
        int parse = source.indexOf("String senderNick", reject);

        assertTrue(receiver >= 0 && receiver < known && known < shape);
        assertTrue(shape < reject && reject < parse);
    }

    private static String[] parts(int count) {
        String[] result = new String[count];
        for (int i = 0; i < count; i++) {
            result[i] = "x";
        }
        if (count > 2) {
            result[2] = "ACTION";
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
