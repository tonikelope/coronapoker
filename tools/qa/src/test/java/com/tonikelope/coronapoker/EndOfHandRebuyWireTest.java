package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class EndOfHandRebuyWireTest {

    @Test
    void parsesExactCurrentFrameForAnAllowedPlayer() {
        String nick = Base64.getEncoder().encodeToString("alice".getBytes(StandardCharsets.UTF_8));
        EndOfHandRebuyWire parsed = EndOfHandRebuyWire.parse(
                new String[]{"GAME", "8", "REBUY", nick, "1200"}, Set.of("alice"));

        assertEquals("alice", parsed.nick());
        assertEquals(1200, parsed.requestedAmount());

        assertThrows(IllegalArgumentException.class, () -> EndOfHandRebuyWire.parse(
                new String[]{"GAME", "8", "REBUY", nick, "1200", ""}, Set.of("alice")));
        assertThrows(IllegalArgumentException.class, () -> EndOfHandRebuyWire.parse(
                new String[]{"GAME", "8", "REBUY", nick, "NaN"}, Set.of("alice")));
        assertThrows(IllegalArgumentException.class, () -> EndOfHandRebuyWire.parse(
                new String[]{"GAME", "8", "REBUY", nick, "1200"}, Set.of("bob")));
    }

    @Test
    void acceptsCanonicalZeroButRejectsBadNickEncoding() {
        String nick = Base64.getEncoder().encodeToString("alice".getBytes(StandardCharsets.UTF_8));
        assertEquals(0, EndOfHandRebuyWire.parse(
                new String[]{"GAME", "8", "REBUY", nick, "0"}, Set.of("alice")).requestedAmount());
        assertThrows(IllegalArgumentException.class, () -> EndOfHandRebuyWire.parse(
                new String[]{"GAME", "8", "REBUY", "%%%", "0"}, Set.of("alice")));
        String invalidUtf8 = Base64.getEncoder().encodeToString(new byte[]{(byte) 0xc3, 0x28});
        assertThrows(IllegalArgumentException.class, () -> EndOfHandRebuyWire.parse(
                new String[]{"GAME", "8", "REBUY", invalidUtf8, "0"}, Set.of("alice")));
    }

    @Test
    void localOriginatorWaitsForAndConsumesTheHostsCanonicalRelay() throws Exception {
        String source = Files.readString(locateRoot().resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java")).replace("\r\n", "\n");

        assertEquals(true, source.contains("if (!rebuy_players.isEmpty()\n"
                + "                || (local_ruined && !GameFrame.getInstance().isPartida_local()))"));
        int start = source.indexOf("private void recibirRebuys(");
        int end = source.indexOf("private void recibirBuyinsIniciales(", start);
        String method = source.substring(start, end);
        assertEquals(true, method.contains("while ((!pending.isEmpty() || pendingLocalRelay != null)"));
        assertEquals(true, method.contains("if (!pending.isEmpty() || pendingLocalRelay != null)"));
        assertEquals(true, method.contains("EndOfHandRebuyWire.parse(partes, allowed)"));
        assertEquals(true, method.contains("this.received_commands.reject(comando)"));
        assertEquals(true, method.contains("pendingLocalRelay = null"));
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
