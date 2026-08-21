package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class InitialBuyinWireTest {

    @Test
    void parsesOnlyTheExactCurrentFrameForAnExpectedPlayer() {
        String nick = Base64.getEncoder().encodeToString("alice".getBytes(StandardCharsets.UTF_8));
        InitialBuyinWire parsed = InitialBuyinWire.parse(
                new String[]{"GAME", "9", "BUYIN", nick, "1500"}, Set.of("alice"));

        assertEquals("alice", parsed.nick());
        assertEquals(1500, parsed.requestedAmount());

        assertThrows(IllegalArgumentException.class, () -> InitialBuyinWire.parse(
                new String[]{"GAME", "9", "BUYIN", nick, "1500", ""}, Set.of("alice")));
        assertThrows(IllegalArgumentException.class, () -> InitialBuyinWire.parse(
                new String[]{"GAME", "9", "BUYIN", nick, "NaN"}, Set.of("alice")));
        assertThrows(IllegalArgumentException.class, () -> InitialBuyinWire.parse(
                new String[]{"GAME", "9", "BUYIN", nick, "1500"}, Set.of("bob")));
    }

    @Test
    void rejectsMalformedOrNonCanonicalNickEncoding() {
        assertThrows(IllegalArgumentException.class, () -> InitialBuyinWire.parse(
                new String[]{"GAME", "9", "BUYIN", "%%%", "1500"}, Set.of("alice")));
        String invalidUtf8 = Base64.getEncoder().encodeToString(new byte[]{(byte) 0xc3, 0x28});
        assertThrows(IllegalArgumentException.class, () -> InitialBuyinWire.parse(
                new String[]{"GAME", "9", "BUYIN", invalidUtf8, "1500"}, Set.of("alice")));
        String empty = Base64.getEncoder().encodeToString(new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> InitialBuyinWire.parse(
                new String[]{"GAME", "9", "BUYIN", empty, "1500"}, Set.of("")));
    }

    @Test
    void buyinWaitRejectsMalformedKnownFramesBeforeMutation() throws Exception {
        String source = Files.readString(locateRoot().resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java")).replace("\r\n", "\n");
        int start = source.indexOf("private void recibirBuyinsIniciales(");
        int end = source.indexOf("public void remotePlayerQuit(", start);
        String method = source.substring(start, end);

        int split = method.indexOf("comando.split(\"#\", -1)");
        int known = method.indexOf("partes[2].equals(\"BUYIN\")");
        int parse = method.indexOf("InitialBuyinWire.parse(partes, pending)", known);
        int apply = method.indexOf("aplicarBuyinInicial(parsed.nick()", parse);
        int reject = method.indexOf("this.received_commands.reject(comando)", parse);
        assertTrue(split >= 0 && split < known && known < parse && parse < apply);
        assertTrue(parse < reject);
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
