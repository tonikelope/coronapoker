package com.tonikelope.coronapoker.core;

import java.time.Instant;
import java.util.Objects;

/** One ordered waiting-room chat or presence item, before frontend rendering. */
public record LobbyChatMessage(long sequence, Instant timestamp, String nickname,
        Type type, String content) {

    public enum Type {
        TEXT,
        IMAGE,
        VOICE,
        PLAYER_JOINED,
        PLAYER_LEFT
    }

    public LobbyChatMessage {
        if (sequence < 0L) {
            throw new IllegalArgumentException("Chat sequence cannot be negative");
        }
        Objects.requireNonNull(timestamp, "timestamp");
        nickname = Objects.requireNonNull(nickname, "nickname").trim();
        if (nickname.isEmpty()) {
            throw new IllegalArgumentException("Chat nickname is required");
        }
        Objects.requireNonNull(type, "type");
        content = Objects.requireNonNullElse(content, "");
        if ((type == Type.TEXT || type == Type.IMAGE || type == Type.VOICE)
                && content.isBlank()) {
            throw new IllegalArgumentException("Chat content is required");
        }
    }
}
