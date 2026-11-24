/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.LobbyParticipant;
import com.tonikelope.coronapoker.core.LobbySession;
import com.tonikelope.coronapoker.core.LobbySnapshot;
import com.tonikelope.coronapoker.core.NewGameTableDraft;
import com.tonikelope.coronapoker.core.game.GameConfigCodecV1;
import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

final class GdxLiveSettingsRefreshTest {

    @Test
    void clientSettingsFollowHostUpdatesWithoutReopeningOrBecomingDirty() throws Exception {
        try (LobbySession lobby = lobby(false)) {
            var table = table(lobby);
            var initial = configuration().withHands(20);
            accept(table, new TableVisualEvent.GameConfigurationStatus(1, initial));
            accept(table, new TableVisualEvent.CommunicationRulesStatus(2, true, false));
            openSettings(table);

            assertEquals(1f, optionTarget(table, "tts_server"));
            assertFalse(optionEnabled(table, "tts_server"));
            assertTrue(optionEnabled(table, "audio_block_tts_local"));
            assertFalse(optionEnabled(table, "audio_block_voice_messages"));
            assertEquals("20", call(table, "handLimitSettingLabel"));
            assertEquals(false, call(table, "settingsHotJoinDisplayedValue"));

            accept(table, new TableVisualEvent.CommunicationRulesStatus(3, false, true));
            var updated = initial.withHands(47).withRabbitHunting(2).withStraddle(true);
            accept(table, new TableVisualEvent.GameConfigurationStatus(4, updated));
            lobby.publish(lobbySnapshot(false, true));

            assertEquals(0f, optionTarget(table, "tts_server"));
            assertFalse(optionEnabled(table, "tts_server"));
            assertFalse(optionEnabled(table, "audio_block_tts_local"));
            assertEquals(1f, optionTarget(table, "voice_messages"));
            assertFalse(optionEnabled(table, "voice_messages"));
            assertTrue(optionEnabled(table, "audio_block_voice_messages"));
            assertTrue(optionEnabled(table, "audio_play_own_voice"));
            assertEquals(updated, field(table, "liveSettingsDraft"));
            assertEquals("47", call(table, "handLimitSettingLabel"));
            assertEquals(true, call(table, "settingsHotJoinDisplayedValue"));
            assertEquals(false, call(table, "settingsHavePendingChanges"));

            accept(table, new TableVisualEvent.CommunicationRulesStatus(5, true, true));
            assertEquals(1f, optionTarget(table, "audio_block_tts_local"));
        }
    }

    @Test
    void hostSeesBlindAdvanceWhileKeepingAnUnsavedRuleEdit() throws Exception {
        try (LobbySession lobby = lobby(true)) {
            var table = table(lobby);
            var initial = configuration().withIwtsth(false);
            accept(table, new TableVisualEvent.GameConfigurationStatus(1, initial));
            openSettings(table);

            var draftField = CoronaPokerGdxTable.class.getDeclaredField("liveSettingsDraft");
            draftField.setAccessible(true);
            draftField.set(table, initial.withIwtsth(true));

            var advanced = initial.withBlindSettings(0.20d, 0.40d,
                    initial.blindsDouble(), initial.blindsDoubleType(),
                    initial.blindCap(), initial.blindStructure());
            accept(table, new TableVisualEvent.GameConfigurationStatus(2, advanced));

            assertEquals(advanced, field(table, "liveSettingsOpened"));
            assertEquals(advanced.withIwtsth(true), field(table, "liveSettingsDraft"));
            assertEquals(true, call(table, "settingsHavePendingChanges"));
        }
    }

    private static GameConfigCodecV1.Configuration configuration() {
        return GameConfigCodecV1.fromSettings(new NewGameTableDraft().snapshot(),
                false, "live-settings-refresh-test");
    }

    private static LobbySession lobby(boolean host) {
        return new LobbySession(lobbySnapshot(host, false),
                command -> CompletableFuture.failedFuture(new AssertionError(command)));
    }

    private static LobbySnapshot lobbySnapshot(boolean host, boolean hotJoin) {
        var participants = List.of(
                new LobbyParticipant("ana", null, true, host, false,
                        true, false, true, -2, -2),
                new LobbyParticipant("borja", null, false, !host, false,
                        true, false, true, -2, -2));
        return new LobbySnapshot("ana", host ? "ana" : "borja", "local",
                host, LobbySnapshot.Phase.IN_GAME, "", participants, List.of(),
                new NewGameTableDraft().snapshot().withAllowHotJoin(hotJoin), false);
    }

    private static CoronaPokerGdxTable table(LobbySession lobby) {
        var snapshot = new TableSnapshot(1, "ana", TableSnapshot.Street.SHOWDOWN,
                0d, "", false, List.of(), List.of());
        return new CoronaPokerGdxTable(60, new GdxTableViewState(snapshot),
                command -> { throw new AssertionError(command); }, () -> { },
                new GdxGameLogSink(), null, lobby, null);
    }

    private static void openSettings(CoronaPokerGdxTable table) throws Exception {
        call(table, "beginTableSettings");
        var open = CoronaPokerGdxTable.class.getDeclaredMethod("openUiLayer", int.class);
        open.setAccessible(true);
        open.invoke(table, CoronaPokerGdxTable.UI_SETTINGS);
    }

    private static void accept(CoronaPokerGdxTable table, TableVisualEvent event) {
        var accepted = new CompletableFuture<Void>();
        table.acceptEvent(event, accepted);
        assertTrue(accepted.isDone());
        accepted.join();
    }

    private static boolean optionEnabled(CoronaPokerGdxTable table, String key)
            throws Exception {
        return (boolean) callOption(table, "settingsOptionEnabled", key);
    }

    private static float optionTarget(CoronaPokerGdxTable table, String key)
            throws Exception {
        return (float) callOption(table, "tableSettingsOptionTarget", key);
    }

    private static Object callOption(CoronaPokerGdxTable table, String method, String key)
            throws Exception {
        var option = GdxSettingsContract.AUDIO_PAGES.stream()
                .flatMap(page -> page.options().stream())
                .filter(candidate -> candidate.key().equals(key))
                .findFirst().orElseThrow();
        var target = CoronaPokerGdxTable.class.getDeclaredMethod(method,
                GdxSettingsContract.ToggleOption.class);
        target.setAccessible(true);
        return target.invoke(table, option);
    }

    private static Object field(CoronaPokerGdxTable table, String name) throws Exception {
        var field = CoronaPokerGdxTable.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(table);
    }

    private static Object call(CoronaPokerGdxTable table, String name) throws Exception {
        var method = CoronaPokerGdxTable.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(table);
    }
}
