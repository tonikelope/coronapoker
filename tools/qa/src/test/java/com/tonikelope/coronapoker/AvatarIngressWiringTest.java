package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/** Regression guard for every untrusted avatar field on the room protocol. */
class AvatarIngressWiringTest {

    @Test
    void allFourWireIngressesUseTheBoundedAvatarStore() throws IOException {
        String source = readWaitingRoomSource();

        assertTrue(source.contains("server_avatar_encoded = partes.length > 1 ? partes[1] : \"*\""));
        assertTrue(source.contains("server_avatar_encoded, server_nick, \"server intro\""));
        assertTrue(source.contains("partes_comando.length >= 6 ? partes_comando[5] : \"*\""));
        assertTrue(source.contains("user_parts.length >= 3 ? user_parts[2] : \"*\""));
        assertTrue(source.contains("decodeRemoteAvatar(partes[2], client_nick, \"JOIN\")"));
        assertEquals(5, count(source, "decodeRemoteAvatar("),
                "four callers plus the helper declaration must remain");

        assertFalse(source.contains("Base64.getDecoder().decode(partes_comando[5])"));
        assertFalse(source.contains("Base64.getDecoder().decode(user_parts[2])"));
    }

    @Test
    void joinAllocatesOnlyAfterTheLockedRaceRecheckAndFailedAdoptionDeletes() throws IOException {
        String source = readWaitingRoomSource();
        int lock = source.indexOf("synchronized (lock_new_client)");
        int raceGate = source.indexOf("!nickCollisionNFC(client_nick)", lock);
        int decode = source.indexOf("decodeRemoteAvatar(partes[2], client_nick, \"JOIN\")", raceGate);
        int adopt = source.indexOf("nuevoParticipanteRemoto(client_nick", decode);

        assertTrue(lock >= 0 && raceGate > lock && decode > raceGate && adopt > decode);
        assertTrue(source.contains("if (!adopted) {\n                avatar_io.deleteOwned(avatar);"));
    }

    private static int count(String text, String needle) {
        int result = 0;
        for (int at = 0; (at = text.indexOf(needle, at)) >= 0; at += needle.length()) {
            result++;
        }
        return result;
    }

    private static String readWaitingRoomSource() throws IOException {
        return Files.readString(locateSourceDir().resolve("WaitingRoomFrame.java"))
                .replace("\r\n", "\n");
    }

    private static Path locateSourceDir() {
        Path start = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (Path path = start; path != null; path = path.getParent()) {
            Path candidate = path.resolve("src/main/java/com/tonikelope/coronapoker");
            if (Files.isRegularFile(candidate.resolve("WaitingRoomFrame.java"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("CoronaPoker source directory not found from " + start);
    }
}
