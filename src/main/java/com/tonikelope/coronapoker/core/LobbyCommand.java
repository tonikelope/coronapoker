package com.tonikelope.coronapoker.core;

import java.util.Arrays;
import java.util.Objects;

/** Typed waiting-room actions; transport framing remains in its adapter. */
public sealed interface LobbyCommand permits LobbyCommand.SendText,
        LobbyCommand.SendImage, LobbyCommand.SendVoice, LobbyCommand.AddBot,
        LobbyCommand.Kick, LobbyCommand.StartGame, LobbyCommand.Leave,
        LobbyCommand.ChangePassword, LobbyCommand.SetChatNotifications {

    record SendText(String text) implements LobbyCommand {
        public SendText {
            text = Objects.requireNonNull(text, "text");
            if (text.isBlank()) {
                throw new IllegalArgumentException("text is required");
            }
        }
    }

    record SendImage(String url) implements LobbyCommand {
        public SendImage {
            url = required(url, "url");
        }
    }

    record SendVoice(byte[] wav) implements LobbyCommand {
        public SendVoice {
            Objects.requireNonNull(wav, "wav");
            if (wav.length == 0) {
                throw new IllegalArgumentException("Voice message is empty");
            }
            wav = Arrays.copyOf(wav, wav.length);
        }

        @Override
        public byte[] wav() {
            return Arrays.copyOf(wav, wav.length);
        }
    }

    record AddBot() implements LobbyCommand { }

    record Kick(String nickname) implements LobbyCommand {
        public Kick {
            nickname = required(nickname, "nickname");
        }
    }

    record StartGame() implements LobbyCommand { }

    record Leave() implements LobbyCommand { }

    record ChangePassword(String password) implements LobbyCommand {
        public ChangePassword {
            password = Objects.requireNonNullElse(password, "");
        }
    }

    record SetChatNotifications(boolean enabled) implements LobbyCommand { }

    private static String required(String value, String label) {
        String text = Objects.requireNonNull(value, label).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return text;
    }
}
