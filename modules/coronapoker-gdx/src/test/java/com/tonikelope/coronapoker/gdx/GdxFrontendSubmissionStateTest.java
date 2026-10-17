package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.NewGameConnectionDraft;
import com.tonikelope.coronapoker.core.network.UpnpPortReservation;
import java.net.BindException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
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
    void everyUpnpOpeningFailureHasADistinctUserFacingMessage() {
        assertEquals("gdx.lobby.upnp_unavailable",
                GdxFrontendScreen.upnpOpenErrorKey(
                        UpnpPortReservation.Status.UNAVAILABLE));
        assertEquals("gdx.lobby.upnp_already_mapped",
                GdxFrontendScreen.upnpOpenErrorKey(
                        UpnpPortReservation.Status.ALREADY_MAPPED));
        assertEquals("gdx.lobby.upnp_failed",
                GdxFrontendScreen.upnpOpenErrorKey(
                        UpnpPortReservation.Status.FAILED));
        assertEquals("", GdxFrontendScreen.upnpOpenErrorKey(
                UpnpPortReservation.Status.OPEN));
    }

    @Test
    void upnpCanBeRetriedAfterTheModalCheckFinishes() {
        NewGameConnectionDraft draft = NewGameConnectionDraft.from(
                new Properties(), NewGameConnectionDraft.Mode.CREATE);
        draft.setPort("21339");

        assertTrue(GdxFrontendScreen.upnpToggleEnabled(draft, false));
        assertFalse(GdxFrontendScreen.upnpToggleEnabled(draft, true));

        draft.setUpnp(false);
        assertTrue(GdxFrontendScreen.upnpToggleEnabled(draft, false));
        draft.setPort("");
        assertFalse(GdxFrontendScreen.upnpToggleEnabled(draft, false));
    }

    @Test
    void openingCreateForgetsLegacyUpnpAndStartsFromOff() {
        Properties properties = new Properties();
        properties.setProperty("upnp", "true");
        NewGameConnectionDraft draft = NewGameConnectionDraft.from(
                properties, NewGameConnectionDraft.Mode.CREATE);

        assertFalse(draft.upnp());
        assertTrue(GdxFrontendScreen.forgetPersistedUpnp(properties));
        assertFalse(properties.containsKey("upnp"));
        assertTrue(GdxFrontendScreen.upnpToggleEnabled(draft, false));
    }

    @Test
    void reopeningCreateDoesNotAnimateThePreviousUpnpStateToOff() {
        Map<String, Float> animations = new HashMap<>();
        animations.put(GdxToggleMotion.stableKey("NEW_GAME:0", "UPnP",
                1170f, 475f, 635f, false), 1f);
        animations.put(GdxToggleMotion.stableKey("SETTINGS:MENU:GRAPHICS:0",
                "FPS", 100f, 100f, 500f, false), 1f);

        GdxFrontendScreen.clearNewGameToggleAnimations(animations);

        assertEquals(1, animations.size());
        assertTrue(animations.keySet().iterator().next()
                .startsWith("SETTINGS:"));
    }
}
