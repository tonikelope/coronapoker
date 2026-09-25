/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.core.game.GameSession;
import com.tonikelope.coronapoker.core.game.RecoveredSettingsSynchronizer;

/** Replays the classic recovery fossil through the existing live-rule setters. */
final class SwingRecoveredSettingsSynchronizer implements RecoveredSettingsSynchronizer {
    private final GameSession session;

    SwingRecoveredSettingsSynchronizer(GameSession session) {
        this.session = java.util.Objects.requireNonNull(session, "session");
    }

    @Override
    public void apply() {
        if (GameFrame.IWTSTH_RULE_RECOVER != null) {
            boolean value = GameFrame.IWTSTH_RULE_RECOVER;
            GameFrame.IWTSTH_RULE_RECOVER = null;
            if (value != session.configuration().iwtsth()) GameFrame.setIwtsthRule(value);
        }
        if (GameFrame.RABBIT_HUNTING_RECOVER != null) {
            int value = GameFrame.RABBIT_HUNTING_RECOVER;
            GameFrame.RABBIT_HUNTING_RECOVER = null;
            if (value != session.configuration().rabbitHunting()) GameFrame.setRabbitHunting(value);
        }
        if (GameFrame.RUN_IT_TWICE_RECOVER != null) {
            boolean value = GameFrame.RUN_IT_TWICE_RECOVER;
            GameFrame.RUN_IT_TWICE_RECOVER = null;
            if (value != session.configuration().runItTwice()) GameFrame.setRunItTwiceRule(value);
        }
        if (GameFrame.VOICE_MESSAGES_RECOVER != null) {
            boolean value = GameFrame.VOICE_MESSAGES_RECOVER;
            GameFrame.VOICE_MESSAGES_RECOVER = null;
            if (value != GameFrame.VOICE_MESSAGES) GameFrame.setVoiceMessages(value);
        }
        if (GameFrame.TTS_SERVER_RECOVER != null) {
            boolean value = GameFrame.TTS_SERVER_RECOVER;
            GameFrame.TTS_SERVER_RECOVER = null;
            if (value != GameFrame.TTS_SERVER) GameFrame.setTTSGlobal(value);
        }
    }
}
