package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import org.junit.jupiter.api.Test;

public class PositionWireV1Test {

    @Test
    void validPositionFrameIsDecodedBeforeAnyCallerMutation() {
        PositionWireV1.Result result = PositionWireV1.parse(frame(
                b64("utg"), b64("bb"), b64("sb"), b64("dealer"), "1234", "1"));

        assertTrue(result.isOk());
        assertEquals("utg", result.value().utg());
        assertEquals("bb", result.value().bigBlind());
        assertEquals("sb", result.value().smallBlind());
        assertEquals("dealer", result.value().dealer());
        assertEquals(1234L, result.value().playTime());
        assertTrue(result.value().doubleBlinds());
    }

    @Test
    void malformedCriticalPositionFramesAreRejectedAtomically() {
        assertFalse(PositionWireV1.parse("GAME#7#POSITIONS").isOk());
        assertFalse(PositionWireV1.parse(frame("%%%", b64("bb"), b64("sb"), b64("dealer"), "1", "0")).isOk());
        assertFalse(PositionWireV1.parse(frame(
                Base64.getEncoder().encodeToString(new byte[]{(byte) 0xc3, 0x28}),
                b64("bb"), b64("sb"), b64("dealer"), "1", "0")).isOk());
        assertFalse(PositionWireV1.parse(frame(b64("utg"), b64("bb"), b64("sb"), b64("dealer"), "-1", "0")).isOk());
        assertFalse(PositionWireV1.parse(frame(b64("utg"), b64("bb"), b64("sb"), b64("dealer"), "1", "2")).isOk());
        assertFalse(PositionWireV1.parse(frame(b64(""), b64("bb"), b64("sb"), b64("dealer"), "1", "0")).isOk());
    }

    @Test
    void crupierFailsClosedAndStopsHandInitializationAfterPositionFailure() throws Exception {
        String source = Files.readString(locateRoot().resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java"));
        int receive = source.indexOf("private void recibirPosiciones()");
        int parse = source.indexOf("PositionWireV1.parse(comando)", receive);
        int apply = source.indexOf("this.utg_nick = positions.utg()", parse);
        int failure = source.indexOf("if (!ok && !isFin_de_la_transmision())", apply);
        int force = source.indexOf("setForce_recover(true)", failure);
        int pending = source.indexOf("setTerminationPending()", force);
        int finish = source.indexOf("setFin_de_la_transmision(true)", pending);
        int close = source.indexOf("closeClientSocket()", finish);
        int setPositions = source.indexOf("this.setPositions();");
        int handAbort = source.indexOf("if (isFin_de_la_transmision())", setPositions);
        int handReturn = source.indexOf("return false;", handAbort);

        assertTrue(receive >= 0 && receive < parse && parse < apply && apply < failure);
        assertTrue(failure < force && force < pending && pending < finish && finish < close);
        assertTrue(setPositions >= 0 && setPositions < handAbort && handAbort < handReturn);
        assertFalse(source.contains("POSITIONS malformed dropped"));
    }

    private static String frame(String utg, String bb, String sb, String dealer,
            String playTime, String doubleBlinds) {
        return String.join("#", "GAME", "7", "POSITIONS", utg, bb, sb, dealer,
                playTime, doubleBlinds);
    }

    private static String b64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
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
