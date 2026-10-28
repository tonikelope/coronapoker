/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core;

import java.text.Normalizer;
import java.util.Objects;

/** Canonical bot-nickname contract shared by lobby, recovery and game code. */
public final class BotNickname {

    public static final char MARKER = '$';
    public static final String STANDARD_PREFIX = "CoronaBot$";
    public static final int MAX_CUSTOM_DISPLAY_LENGTH = 20;

    private BotNickname() {
    }

    public static boolean isBot(String nickname) {
        return nickname != null && nickname.indexOf(MARKER) >= 0;
    }

    public static boolean isStandard(String nickname) {
        if (nickname == null || !nickname.startsWith(STANDARD_PREFIX)) {
            return false;
        }
        try {
            return Integer.parseInt(nickname.substring(
                    STANDARD_PREFIX.length())) > 0;
        } catch (NumberFormatException invalid) {
            return false;
        }
    }

    public static String standard(int number) {
        if (number <= 0) {
            throw new IllegalArgumentException("Bot number must be positive");
        }
        return STANDARD_PREFIX + number;
    }

    /** Converts the player-facing name into the reserved canonical bot name. */
    public static String custom(String displayName) {
        String name = Normalizer.normalize(
                Objects.requireNonNullElse(displayName, "").trim(),
                Normalizer.Form.NFC);
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Bot name must not be blank");
        }
        if (name.length() > MAX_CUSTOM_DISPLAY_LENGTH) {
            throw new IllegalArgumentException("Bot name is too long");
        }
        if (isBot(name)) {
            throw new IllegalArgumentException(
                    "The dollar marker is added automatically");
        }
        if (name.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Bot name contains control characters");
        }
        return name + MARKER;
    }

    public static String displayName(String canonicalNickname) {
        String nickname = Objects.requireNonNullElse(canonicalNickname, "");
        return nickname.endsWith(Character.toString(MARKER))
                ? nickname.substring(0, nickname.length() - 1) : nickname;
    }
}
