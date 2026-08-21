package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class SeatDrawRosterBindingTest {

    @Test
    void firstRosterMustExactlyMatchKnownParticipants() {
        assertTrue(Crupier.seatDrawRosterMatchesKnownParticipants(
                Arrays.asList("alice", "bot", "host"),
                Arrays.asList("host", "alice", "bot")));

        assertFalse(Crupier.seatDrawRosterMatchesKnownParticipants(
                Arrays.asList("alice", "host"),
                Arrays.asList("host", "alice", "bot")), "omitted participant");
        assertFalse(Crupier.seatDrawRosterMatchesKnownParticipants(
                Arrays.asList("alice", "bot", "host", "mallory"),
                Arrays.asList("host", "alice", "bot")), "invented participant");
        assertFalse(Crupier.seatDrawRosterMatchesKnownParticipants(
                Arrays.asList("alice", "bot", "host", "host"),
                Arrays.asList("host", "alice", "bot")), "duplicate participant");
        assertFalse(Crupier.seatDrawRosterMatchesKnownParticipants(
                Arrays.asList("alice", "bot", ""),
                Arrays.asList("host", "alice", "bot")), "empty nickname");
    }

    @Test
    void clientChecksBindingBeforeContributing() throws Exception {
        String source = Files.readString(locateRoot().resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java")).replace("\r\n", "\n");
        int client = source.indexOf("private String[] clientSeatDraw()");
        int start = source.indexOf("case \"SEAT_DRAW_BEGIN\":", client);
        int end = source.indexOf("case \"SEAT_COMMITS\":", start);
        String begin = source.substring(start, end);

        int binding = begin.indexOf("seatDrawRosterMatchesKnownParticipants");
        int contribution = begin.indexOf("signSeatCommitLocal");
        assertTrue(binding >= 0, "missing first-roster binding");
        assertTrue(contribution > binding, "client contributed before checking roster binding");
        assertTrue(begin.contains("rejectCriticalSeatDrawHostCommand(null"));
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
