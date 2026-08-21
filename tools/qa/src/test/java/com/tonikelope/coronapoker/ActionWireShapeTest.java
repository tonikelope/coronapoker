package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void betFromFloatBackedEngineValueIsSerializedAtCanonicalCentPrecision() {
        double promotedFloat = (double) 0.7f;

        String wire = Crupier.buildActionWireCommand(
                "bot", Player.BET, promotedFloat, "*", new byte[]{1}, new byte[]{2});

        String[] fields = ("GAME#1#" + wire).split("#", -1);
        assertEquals("0.70", fields[5],
                "honest float-backed bets must not leak binary noise onto the strict money wire");
        assertEquals(70L, MoneyCents.parse(fields[5]).cents());
    }

    @Test
    void wireBetAndSignedRecordFormulaStayIdenticalAcrossEngineMoneyShapes() {
        double[] engineValues = {
            (double) 0.1f,
            (double) (0.1f + 0.2f),
            (double) 10.05f,
            12.34d,
            200000.07d
        };

        for (double engineValue : engineValues) {
            String wire = Crupier.buildActionWireCommand(
                    "bot", Player.BET, engineValue, "*", new byte[]{1}, new byte[]{2});
            String wireAmount = ("GAME#1#" + wire).split("#", -1)[5];

            assertEquals(
                    Crupier.expectedActionAmountCents(
                            Player.BET, engineValue, 0d, 1_000_000d, 0d),
                    MoneyCents.parse(wireAmount).cents(),
                    "plaintext and signed amount must use the same cent value for " + engineValue);
        }
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
