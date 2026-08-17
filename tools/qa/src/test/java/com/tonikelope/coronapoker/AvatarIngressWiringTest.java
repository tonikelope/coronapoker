package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/**
 * Regression guard for every untrusted avatar field on the room protocol.
 */
class AvatarIngressWiringTest {

    @Test
    void allFourWireIngressesUseTheBoundedAvatarStore() throws IOException {
        String source = readWaitingRoomSource();

        assertTrue(source.contains("server_avatar_encoded = partes.length > 1 ? partes[1] : \"*\""));
        assertTrue(source.contains("server_avatar_encoded, server_nick, \"server intro\""));
        assertTrue(source.contains("partes_comando.length >= 6 ? partes_comando[5] : \"*\""));
        assertTrue(source.contains("user_parts.length >= 3 ? user_parts[2] : \"*\""));
        assertTrue(source.contains("decodeRemoteAvatar(partes[2], client_nick, \"JOIN\")"));
        assertEquals(4, count(source, "decodeRemoteAvatar("),
                "server intro, JOIN, bounded roster helper and decoder declaration must remain");

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

    @Test
    void hostRejectsDollarJoinWithDedicatedUnauthorizedNickError() throws IOException {
        String source = readWaitingRoomSource();
        int guard = source.indexOf("hasReservedBotNickCharacter(client_nick)");
        int response = source.indexOf("\"NICKUNAUTHORIZED\"", guard);
        int identity = source.indexOf("verifyJoinSelfSig(client_nick", guard);

        assertTrue(guard >= 0 && response > guard && identity > response,
                "reserved-nick rejection must happen before JOIN identity admission");
        assertTrue(source.contains("Translator.translate(\"conn.nick_unauthorized\")"));
    }

    @Test
    void clientRosterAdmissionPrecedesAvatarAllocationForBothIngresses() throws IOException {
        String source = readWaitingRoomSource();
        int newUser = source.indexOf("case \"NEWUSER\":");
        int newUserGate = source.indexOf("admitRemoteRosterParticipant(", newUser);
        int usersList = source.indexOf("case \"USERSLIST\":", newUserGate);
        int usersListGate = source.indexOf("admitRemoteRosterParticipant(", usersList);
        int init = source.indexOf("case \"INIT\":", usersListGate);
        int admissionHelper = source.indexOf("private synchronized RemoteRosterAdmission admitRemoteRosterParticipant(");
        int capacityGate = source.indexOf("remoteRosterAdmission(participantes.size()", admissionHelper);
        int avatarDecode = source.indexOf("decodeRemoteAvatar(encodedAvatar, nick, source)", capacityGate);
        int helperEnd = source.indexOf("private void rejectRemoteRoster", admissionHelper);

        assertTrue(newUser >= 0 && newUserGate > newUser && usersList > newUserGate);
        assertTrue(usersListGate > usersList && init > usersListGate);
        assertTrue(admissionHelper >= 0 && capacityGate > admissionHelper && avatarDecode > capacityGate);
        assertFalse(source.substring(admissionHelper, helperEnd).contains("synchronized (participantes)"),
                "roster admission must keep the global frame -> synchronizedMap lock order");
        assertEquals(2, count(source, "RemoteRosterAdmission rosterAdmission = admitRemoteRosterParticipant("));
        assertTrue(source.contains("RemoteRosterAdmission.REJECT"));
        assertTrue(source.contains("closeClientSocket();"));
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
