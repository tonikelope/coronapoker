/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core;

import java.nio.file.Path;
import java.util.Objects;

/** Host-local presentation and AI settings for one custom bot. */
public record CustomBotProfile(String nickname, Path avatar,
        NewGameTableDraft.BotDifficulty difficulty, Personality personality) {

    public enum Personality { RANDOM, NIT, STATION, TAG, LAG }

    public CustomBotProfile {
        nickname = Objects.requireNonNull(nickname, "nickname").trim();
        if (!BotNickname.isBot(nickname)
                || BotNickname.isStandard(nickname)) {
            throw new IllegalArgumentException(
                    "Custom bots require a non-standard dollar nickname");
        }
        avatar = avatar == null ? null : avatar.toAbsolutePath().normalize();
        difficulty = Objects.requireNonNullElse(difficulty,
                NewGameTableDraft.BotDifficulty.MEDIUM);
        personality = Objects.requireNonNullElse(personality,
                Personality.RANDOM);
    }

    public CustomBotProfile(String nickname, Path avatar,
            NewGameTableDraft.BotDifficulty difficulty) {
        this(nickname, avatar, difficulty, Personality.RANDOM);
    }

    public static CustomBotProfile create(String displayName, Path avatar,
            NewGameTableDraft.BotDifficulty difficulty) {
        return new CustomBotProfile(BotNickname.custom(displayName), avatar,
                difficulty, Personality.RANDOM);
    }

    public static CustomBotProfile create(String displayName, Path avatar,
            NewGameTableDraft.BotDifficulty difficulty,
            Personality personality) {
        return new CustomBotProfile(BotNickname.custom(displayName), avatar,
                difficulty, personality);
    }

    public String displayName() {
        return BotNickname.displayName(nickname);
    }
}
