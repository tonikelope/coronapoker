package com.tonikelope.coronapoker.core;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable state rendered by both waiting-room frontends. */
public record LobbySnapshot(String localNickname, String serverNickname,
        String serverAddress, boolean host, Phase phase, String statusDetail,
        List<LobbyParticipant> participants, List<LobbyChatMessage> chat,
        NewGameTableDraft.Settings tableSettings, boolean recovering,
        boolean chatNotifications) {

    public static final int MAX_PARTICIPANTS = 10;

    public enum Phase {
        CONNECTING,
        KEY_EXCHANGE,
        RECEIVING_SERVER_INFO,
        CONNECTED,
        WAITING_FOR_PLAYERS,
        INITIALIZING_GAME,
        RECONNECTING,
        IN_GAME,
        ERROR,
        CLOSED
    }

    public LobbySnapshot {
        localNickname = requireText(localNickname, "localNickname");
        serverNickname = Objects.requireNonNullElse(serverNickname, "").trim();
        serverAddress = requireText(serverAddress, "serverAddress");
        Objects.requireNonNull(phase, "phase");
        statusDetail = Objects.requireNonNullElse(statusDetail, "");
        participants = List.copyOf(participants);
        chat = List.copyOf(chat);
        if (participants.isEmpty() || participants.size() > MAX_PARTICIPANTS) {
            throw new IllegalArgumentException("Lobby participant count must be between 1 and 10");
        }
        Set<String> normalizedNicks = new HashSet<>();
        int localCount = 0;
        int hostCount = 0;
        for (LobbyParticipant participant : participants) {
            Objects.requireNonNull(participant, "participant");
            String normalized = Normalizer.normalize(participant.nickname(),
                    Normalizer.Form.NFC);
            if (!normalizedNicks.add(normalized)) {
                throw new IllegalArgumentException("Duplicate normalized participant nickname");
            }
            localCount += participant.local() ? 1 : 0;
            hostCount += participant.host() ? 1 : 0;
        }
        if (localCount != 1 || hostCount != 1) {
            throw new IllegalArgumentException("Lobby requires exactly one local and one host participant");
        }
        boolean matchingLocal = false;
        for (LobbyParticipant participant : participants) {
            if (participant.local() && participant.nickname().equals(localNickname)) {
                matchingLocal = true;
                break;
            }
        }
        if (!matchingLocal) {
            throw new IllegalArgumentException("Local participant does not match local nickname");
        }
    }

    public boolean startingOrStarted() {
        return phase == Phase.INITIALIZING_GAME || phase == Phase.IN_GAME;
    }

    private static String requireText(String value, String label) {
        String text = Objects.requireNonNull(value, label).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return text;
    }
}
