package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.NewGameConnectionDraft;
import com.tonikelope.coronapoker.core.network.UpnpPortProbe;
import java.net.BindException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Properties;
import org.junit.jupiter.api.Test;

final class GdxFrontendSubmissionStateTest {

    @Test
    void everyNewGameTextFieldKeepsKeyboardOwnershipOnTheModalSurface() {
        assertTrue(GdxFrontendScreen.newGameTextField("nick"));
        assertTrue(GdxFrontendScreen.newGameTextField("password"));
        assertTrue(GdxFrontendScreen.newGameTextField("server"));
        assertTrue(GdxFrontendScreen.newGameTextField("port"));
        assertFalse(GdxFrontendScreen.newGameTextField("lobbyPassword"));
    }

    @Test
    void createAndJoinRemainDisabledUntilRequiredFieldsAreValid() {
        for (NewGameConnectionDraft.Mode mode : new NewGameConnectionDraft.Mode[]{
            NewGameConnectionDraft.Mode.CREATE,
            NewGameConnectionDraft.Mode.JOIN}) {
            NewGameConnectionDraft draft = NewGameConnectionDraft.from(
                    new Properties(), mode);
            assertFalse(GdxFrontendScreen.newGameSubmitEnabled(false, draft));

            draft.setNickname("Alice");
            assertTrue(GdxFrontendScreen.newGameSubmitEnabled(false, draft));
            assertFalse(GdxFrontendScreen.newGameSubmitEnabled(true, draft));

            draft.setPort("");
            assertFalse(GdxFrontendScreen.newGameSubmitEnabled(false, draft));
            draft.setPort("99999");
            assertFalse(GdxFrontendScreen.newGameSubmitEnabled(false, draft));
        }
    }

    @Test
    void recoveryWaitsForTheRealRecoveredGame() {
        NewGameConnectionDraft draft = NewGameConnectionDraft.from(
                new Properties(), NewGameConnectionDraft.Mode.RECOVER);
        draft.setNickname("Alice");

        assertFalse(GdxFrontendScreen.newGameSubmitEnabled(false, draft));
        assertTrue(draft.beginRecoverLoad());
        assertFalse(GdxFrontendScreen.newGameSubmitEnabled(false, draft));
        draft.completeRecoverLoad(23);
        assertTrue(GdxFrontendScreen.newGameSubmitEnabled(false, draft));
    }

    @Test
    void commonPortAndConnectionFailuresHaveUserFacingMessages() {
        assertEquals("gdx.lobby.port_in_use",
                GdxFrontendScreen.submissionErrorKey(
                        new RuntimeException(new BindException())));
        assertEquals("gdx.lobby.unknown_host",
                GdxFrontendScreen.submissionErrorKey(
                        new UnknownHostException()));
        assertEquals("gdx.lobby.connection_timeout",
                GdxFrontendScreen.submissionErrorKey(
                        new SocketTimeoutException()));
        assertEquals("gdx.lobby.connection_refused",
                GdxFrontendScreen.submissionErrorKey(
                        new ConnectException()));
    }

    @Test
    void everyUpnpProbeFailureHasADistinctUserFacingMessage() {
        assertEquals("gdx.lobby.upnp_unavailable",
                GdxFrontendScreen.upnpProbeErrorKey(
                        UpnpPortProbe.Status.UNAVAILABLE));
        assertEquals("gdx.lobby.upnp_already_mapped",
                GdxFrontendScreen.upnpProbeErrorKey(
                        UpnpPortProbe.Status.ALREADY_MAPPED));
        assertEquals("gdx.lobby.upnp_failed",
                GdxFrontendScreen.upnpProbeErrorKey(
                        UpnpPortProbe.Status.FAILED));
        assertEquals("", GdxFrontendScreen.upnpProbeErrorKey(
                UpnpPortProbe.Status.AVAILABLE));
    }
}
