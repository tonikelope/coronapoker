package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.tonikelope.coronapoker.core.LobbyCommand;
import com.tonikelope.coronapoker.core.LobbyParticipant;
import com.tonikelope.coronapoker.core.LobbySnapshot;
import com.tonikelope.coronapoker.core.NewGameTableDraft;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GdxFrontendSettingsWiringTest {

    @Test
    void hostPublishesChangedTableAndChatNotificationSettings() {
        NewGameTableDraft table = new NewGameTableDraft();
        NewGameTableDraft.Settings original = table.snapshot();
        table.setThinkSeconds(original.thinkSeconds() + 1);
        NewGameTableDraft.Settings changed = table.snapshot();

        List<LobbyCommand> commands = GdxFrontendScreen
                .lobbySettingsCommands(snapshot(true, original, true),
                        changed, false);

        assertEquals(2, commands.size());
        assertEquals(changed, assertInstanceOf(
                LobbyCommand.UpdateTableSettings.class, commands.get(0))
                .settings());
        assertEquals(false, assertInstanceOf(
                LobbyCommand.SetChatNotifications.class, commands.get(1))
                .enabled());
    }

    @Test
    void clientPublishesOnlyItsLocalChatNotificationPreference() {
        NewGameTableDraft.Settings settings =
                new NewGameTableDraft().snapshot();

        List<LobbyCommand> commands = GdxFrontendScreen
                .lobbySettingsCommands(snapshot(false, settings, true),
                        settings, false);

        assertEquals(1, commands.size());
        assertInstanceOf(LobbyCommand.SetChatNotifications.class,
                commands.get(0));
    }

    @Test
    void unchangedLobbySettingsDoNotProduceNetworkWork() {
        NewGameTableDraft.Settings settings =
                new NewGameTableDraft().snapshot();

        assertEquals(List.of(), GdxFrontendScreen.lobbySettingsCommands(
                snapshot(true, settings, true), settings, true));
    }

    private static LobbySnapshot snapshot(boolean host,
            NewGameTableDraft.Settings settings, boolean notifications) {
        LobbyParticipant local = new LobbyParticipant(
                host ? "server" : "client", null, true, host, false, true,
                false, true, LobbyParticipant.NO_LATENCY,
                LobbyParticipant.NO_LATENCY);
        LobbyParticipant remote = new LobbyParticipant(
                host ? "client" : "server", null, false, !host, false, true,
                false, true, LobbyParticipant.NO_LATENCY,
                LobbyParticipant.NO_LATENCY);
        return new LobbySnapshot(local.nickname(), "server",
                "localhost:2345", host,
                LobbySnapshot.Phase.WAITING_FOR_PLAYERS, "",
                List.of(local, remote), List.of(), settings, false,
                notifications);
    }
}
