package com.tonikelope.coronapoker;

import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class VoluntaryExitLifecycleTest {

    @Test
    void acceptedCommunityTestamentSurvivesSocketAndParticipantTeardown() throws Exception {
        String source = Files.readString(sourceRoot().resolve("Crupier.java"));
        String acceptedExit = methodBody(source,
                "public void remotePlayerQuit(String nick, String testamento,");
        String abruptExit = methodBody(source, "public void remotePlayerQuit(String nick)");
        String quit = methodBody(source,
                "remotePlayerQuit(String nick, String testamento,\n            String pocketKey, String pocketSignature, boolean acceptedVoluntaryExit)");
        String testament = methodBody(source, "getTestamentoCriptografico(String nick)");
        String newHand = methodBody(source, "private boolean NUEVA_MANO()");

        assertTrue(source.contains("exit_community_testaments"),
                "an accepted EXIT testament must belong to hand state, not only to the socket Participant");
        assertTrue(quit.indexOf("rememberExitCommunityTestament") >= 0,
                "remotePlayerQuit must retain the validated community testament");
        assertTrue(quit.contains("acceptedVoluntaryExit && testamento != null")
                        && quit.contains("!testamento.isEmpty()")
                        && quit.contains("!\"*\".equals(testamento)")
                        && quit.contains("accepted_voluntary_exits.add(nick)"),
                "null, empty and absent-marker testaments must not waive the future receipt");
        assertTrue(acceptedExit.contains("pocketSignature, true)"),
                "the strict EXIT handler path must register a voluntary departure");
        assertTrue(abruptExit.contains("null, null, null, false)"),
                "socket loss and automatic expulsion must not waive the missing receipt");
        assertTrue(quit.indexOf("rememberExitCommunityTestament")
                < quit.indexOf("exitAndCloseSocket"),
                "the testament must be retained before the source socket is closed");
        assertTrue(testament.contains("exitCommunityTestament(nick, null)"),
                "community-card recovery must fall back to the retained EXIT testament");
        assertTrue(newHand.contains("exit_community_testaments.clear()"),
                "a per-hand testament must not leak into the next hand");
    }

    private static String methodBody(String source, String signature) {
        int name = source.indexOf(signature);
        int open = source.indexOf('{', name);
        int depth = 0;
        assertTrue(name >= 0 && open > name, "method not found: " + signature);
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return source.substring(open, i + 1);
            }
        }
        throw new IllegalArgumentException("unterminated method: " + signature);
    }

    private static Path sourceRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("src/main/java/com/tonikelope/coronapoker");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("CoronaPoker source root not found");
    }
}
