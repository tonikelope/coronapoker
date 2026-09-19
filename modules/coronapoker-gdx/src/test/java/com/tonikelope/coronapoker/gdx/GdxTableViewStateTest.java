package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableSessionSummary;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import com.tonikelope.coronapoker.core.game.ActionControlState;
import com.tonikelope.coronapoker.core.NewGameTableDraft;
import com.tonikelope.coronapoker.core.LobbyChatMessage;
import com.tonikelope.coronapoker.core.game.GameConfigCodecV1;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

final class GdxTableViewStateTest {

    @Test
    void preparationMilestonesConsumeTheirSequenceWithoutMutatingTheTable() {
        GdxTableViewState state = new GdxTableViewState(snapshot());
        TableSnapshot before = state.snapshot();

        state.apply(new TableVisualEvent.PreparationStatus(1L,
                TableVisualEvent.PreparationStatus.Phase.STARTING_DEALER));
        state.apply(new TableVisualEvent.PreparationStatus(2L,
                TableVisualEvent.PreparationStatus.Phase.DRAWING_SEATS));
        state.apply(new TableVisualEvent.PreparationStatus(3L,
                TableVisualEvent.PreparationStatus.Phase.READY));

        assertEquals(before, state.snapshot());
        assertEquals("Iniciando crupier…", CoronaPokerGdxTable
                .preparationStatusText(TableVisualEvent.PreparationStatus.Phase
                        .STARTING_DEALER));
        assertEquals("Sorteando sitios…", CoronaPokerGdxTable
                .preparationStatusText(TableVisualEvent.PreparationStatus.Phase
                        .DRAWING_SEATS));
        assertEquals("Mesa preparada", CoronaPokerGdxTable
                .preparationStatusText(TableVisualEvent.PreparationStatus.Phase.READY));
    }

    @Test
    void foldedSeatAlwaysOverridesSettledShowdownReactivation() {
        assertTrue(CoronaPokerGdxTable.shouldDimSeat(false, false, false));
        assertFalse(CoronaPokerGdxTable.shouldDimSeat(false, true, false));
        assertFalse(CoronaPokerGdxTable.shouldDimSeat(true, false, false));
        assertTrue(CoronaPokerGdxTable.shouldDimSeat(true, true, true));
    }

    @Test
    void uncontestedPayoutGetsTheSameSettledFrameAsAShowdownWinner() {
        assertTrue(CoronaPokerGdxTable.hasSettledPresentation(false, true));
        assertTrue(CoronaPokerGdxTable.hasSettledPresentation(true, false));
        assertFalse(CoronaPokerGdxTable.hasSettledPresentation(false, null));
    }

    @Test
    void foldedActionLabelRemainsAttenuatedEvenAfterShowdownSettles() {
        assertEquals(0.34f,
                CoronaPokerGdxTable.seatActionSurfaceAlpha(true, true),
                0.000_001f);
        assertEquals(0.56f,
                CoronaPokerGdxTable.seatActionTextAlpha(true), 0.000_001f);
        assertEquals(1f,
                CoronaPokerGdxTable.seatActionSurfaceAlpha(false, true),
                0.000_001f);
        assertEquals(0.92f,
                CoronaPokerGdxTable.seatActionSurfaceAlpha(false, false),
                0.000_001f);
        assertEquals(1f,
                CoronaPokerGdxTable.seatActionTextAlpha(false), 0.000_001f);
    }

    @Test
    void localHudKeepsAnActiveFrameWhileWaitingForAnotherPlayer() {
        assertEquals(0.88f,
                CoronaPokerGdxTable.localHudIdleFrameAlpha(false),
                0.000_001f);
        assertEquals(0.55f,
                CoronaPokerGdxTable.localHudIdleFrameAlpha(true),
                0.000_001f);
    }

    @Test
    void settingsBlurUsesABoundedDownsampledBackdrop() {
        assertEquals(1920, CoronaPokerGdxTable.settingsBackdropDimension(3840));
        assertEquals(1080, CoronaPokerGdxTable.settingsBackdropDimension(2160));
        assertEquals(1, CoronaPokerGdxTable.settingsBackdropDimension(1));
        assertEquals(1, CoronaPokerGdxTable.settingsBackdropDimension(0));
    }

    @Test
    void disabledCardsRemainReadableAndKeepTheirOriginalColour() {
        assertTrue(CoronaPokerGdxTable.DISABLED_CARD_ALPHA >= 0.25f);
        assertTrue(CoronaPokerGdxTable.DISABLED_CARD_ALPHA <= 0.30f);
    }

    @Test
    void lowRivalSeatMovesAsOneUnitAboveTheLocalHud() {
        float cardAspect = 1242f / 923f;
        float lowAnchor = 0.145f * 1080f;
        float adjusted = CoronaPokerGdxTable.adjustedRivalSeatY(
                lowAnchor, 1080f, cardAspect);

        assertTrue(adjusted > lowAnchor);
        assertEquals(adjusted,
                CoronaPokerGdxTable.adjustedRivalSeatY(
                        adjusted, 1080f, cardAspect), 0.000_001f);
        assertTrue(CoronaPokerGdxTable.adjustedRivalSeatY(
                1070f, 1080f, cardAspect) < 1070f);
    }

    @Test
    void rivalHandKeepsOfficialSizeAndContainsTallModBacks() {
        float officialAspect = 1242f / 923f;
        assertEquals(125f,
                CoronaPokerGdxTable.rivalHoleCardWidth(officialAspect),
                0.000_001f);

        for (float aspect : new float[]{0.75f, officialAspect, 2f, 3f}) {
            Rectangle envelope = CoronaPokerGdxTable.rivalHandEnvelope(aspect);
            assertTrue(envelope.x >= 4f,
                    "left edge for aspect " + aspect);
            assertTrue(envelope.x + envelope.width <= 290.000_1f,
                    "right edge for aspect " + aspect);

            float adjusted = CoronaPokerGdxTable.adjustedRivalSeatY(
                    0.145f * 1080f, 1080f, aspect);
            assertTrue(adjusted + envelope.y >= 190f,
                    "local HUD clearance for aspect " + aspect);
            assertTrue(adjusted + envelope.y + envelope.height <= 1072.000_1f,
                    "top clearance for aspect " + aspect);
        }
    }

    @Test
    void invalidCardAspectCannotCorruptSeatGeometry() {
        assertThrows(IllegalArgumentException.class,
                () -> CoronaPokerGdxTable.rivalHoleCardWidth(0f));
        assertThrows(IllegalArgumentException.class,
                () -> CoronaPokerGdxTable.rivalHoleCardWidth(Float.NaN));
    }

    @Test
    void eightPlayerTableUsesUniformSymmetricSideRows() {
        float[][] anchors = CoronaPokerGdxTable.createSeatAnchors(8);

        assertEquals(0.024f, anchors[1][0], 0.000_001f);
        assertEquals(0.024f, anchors[2][0], 0.000_001f);
        assertEquals(0.36f, anchors[1][1], 0.000_001f);
        assertEquals(0.70f, anchors[2][1], 0.000_001f);
        assertEquals(0.34f, anchors[2][1] - anchors[1][1], 0.000_001f);

        assertEquals(0.976f, anchors[6][0], 0.000_001f);
        assertEquals(0.976f, anchors[7][0], 0.000_001f);
        assertEquals(anchors[2][1], anchors[6][1], 0.000_001f);
        assertEquals(anchors[1][1], anchors[7][1], 0.000_001f);

        assertEquals(0.25f, anchors[3][0], 0.000_001f);
        assertEquals(0.50f, anchors[4][0], 0.000_001f);
        assertEquals(0.75f, anchors[5][0], 0.000_001f);
    }

    @Test
    void paymentSoundWaitsForTheFirstChipToLand() {
        assertFalse(CoronaPokerGdxTable.actionChipSoundDue(-1f));
        assertFalse(CoronaPokerGdxTable.actionChipSoundDue(0f));
        assertFalse(CoronaPokerGdxTable.actionChipSoundDue(0.999f));
        assertTrue(CoronaPokerGdxTable.actionChipSoundDue(1f));
    }

    @Test
    void settingsContentHeadingSitsAboveTheFirstRowWithoutTouchingIt() {
        float firstRowY = 500f;
        float headingY = CoronaPokerGdxTable.settingsContentTitleY(firstRowY);
        assertTrue(headingY >= firstRowY + 64f);
        assertTrue(headingY + 28f <= firstRowY + 104f);
    }

    @Test
    void allInAlwaysKeepsTheSwingTwoPressSafeguard() {
        assertTrue(CoronaPokerGdxTable.requiresActionConfirmation(
                6, false, false));
        assertTrue(CoronaPokerGdxTable.requiresActionConfirmation(
                6, true, false));
        assertFalse(CoronaPokerGdxTable.requiresActionConfirmation(
                6, true, true));
        assertFalse(CoronaPokerGdxTable.requiresActionConfirmation(
                2, false, false));
        assertTrue(CoronaPokerGdxTable.requiresActionConfirmation(
                2, true, false));
    }

    @Test
    void armedHudActionUsesAnUnmistakableGreenSurface() {
        Color normal = new Color(0xffff00ff);
        assertEquals(normal,
                CoronaPokerGdxTable.hudArmedSurfaceColor(normal, false));
        Color armed = CoronaPokerGdxTable.hudArmedSurfaceColor(normal, true);
        assertFalse(normal.equals(armed));
        assertTrue(armed.g > armed.r * 1.5f);
        assertTrue(armed.g > armed.b * 1.5f);
    }

    @Test
    void allInCinematicAlwaysCarriesItsCanonicalActorAndAsset() {
        TableVisualEvent.Cinematic cinematic = new TableVisualEvent.Cinematic(
                1, TableVisualEvent.Cinematic.Type.ALL_IN,
                TableVisualEvent.Cinematic.Phase.START, "ana",
                "rounders.gif", 1_000L);

        assertEquals("ana", cinematic.nickname());
        assertEquals("rounders.gif", cinematic.assetName());
        assertThrows(IllegalArgumentException.class,
                () -> new TableVisualEvent.Cinematic(2,
                        TableVisualEvent.Cinematic.Type.ALL_IN,
                        TableVisualEvent.Cinematic.Phase.START, "",
                        "rounders.gif", 1_000L));
        assertThrows(IllegalArgumentException.class,
                () -> new TableVisualEvent.Cinematic(3,
                        TableVisualEvent.Cinematic.Type.ALL_IN,
                        TableVisualEvent.Cinematic.Phase.START, "ana", "",
                        1_000L));
    }

    @Test
    void visibleSeatRosterDropsExitedRemotesButKeepsLocalUntilClose() {
        TableSnapshot.PlayerSnapshot local = player("local", true);
        TableSnapshot.PlayerSnapshot survivor = player("survivor", false);
        TableSnapshot.PlayerSnapshot exited = player("exited", true);
        TableSnapshot snapshot = new TableSnapshot(4L, "local",
                TableSnapshot.Street.SHOWDOWN, 0d, "", false,
                List.of(exited, survivor, local), List.of());

        assertEquals(List.of("survivor", "local"),
                CoronaPokerGdxTable.visibleSeatPlayers(snapshot).stream()
                        .map(TableSnapshot.PlayerSnapshot::nickname).toList());
        assertEquals(2, CoronaPokerGdxTable.visibleSeatCount(snapshot));
    }

    @Test
    void seatRosterCannotOverwriteTheCurrentHand() {
        TableSnapshot live = new TableSnapshot(8L, "ana",
                TableSnapshot.Street.TURN, 37.5d, "borja", false,
                snapshot().players(), List.of(card("A_C"), card("K_D"),
                        card("Q_T"), card("2_P")));
        GdxTableViewState state = new GdxTableViewState(live);
        TableSnapshot.PlayerSnapshot updatedAna = new TableSnapshot.PlayerSnapshot(
                "ana", 812.5d, 25d, 187.5d, true, false, false,
                false, 8, 11, 2, 1234L, false,
                TableSnapshot.Position.DEALER, "IGUALA", "",
                List.of(card("10_C"), card("9_C")));

        state.apply(new TableVisualEvent.SeatRoster(1,
                List.of(updatedAna, player("nuevo", false))));

        assertEquals(TableSnapshot.Street.TURN, state.snapshot().street());
        assertEquals(37.5d, state.snapshot().pot());
        assertEquals("borja", state.snapshot().currentTurnNickname());
        assertEquals(List.of("A_C", "K_D", "Q_T", "2_P"),
                state.snapshot().communityCards().stream()
                        .map(TableSnapshot.CardSnapshot::code).toList());
        assertEquals(812.5d, player(state, "ana").stack());
        assertEquals(List.of("ana", "nuevo"), state.snapshot().players()
                .stream().map(TableSnapshot.PlayerSnapshot::nickname).toList());
    }

    @Test
    void handBoundaryConsumesTheDealerSnapshotWithoutResettingItLocally() {
        TableSnapshot.PlayerSnapshot exactPlayer
                = new TableSnapshot.PlayerSnapshot("ana", 998.75d, 0d,
                        1.25d, true, false, false, false, -2, -2, 0, 0L,
                        false, TableSnapshot.Position.DEALER, "", "",
                        List.of());
        TableSnapshot exact = new TableSnapshot(9L, "ana",
                TableSnapshot.Street.PREFLOP, 1.25d, "", false,
                List.of(exactPlayer), List.of());
        GdxTableViewState state = new GdxTableViewState(snapshot());

        state.apply(new TableVisualEvent.HandBoundary(1, 2,
                TableVisualEvent.HandBoundary.Phase.PREPARE, exact));

        assertEquals(exact, state.snapshot());
    }

    @Test
    void tableTtsUsesTheSameNotificationGatesAsSwing() {
        assertTrue(CoronaPokerGdxTable.shouldSpeakTableChat(
                LobbyChatMessage.Type.TEXT, true, true, true, false, false));
        assertFalse(CoronaPokerGdxTable.shouldSpeakTableChat(
                LobbyChatMessage.Type.TEXT, false, true, true, false, false));
        assertFalse(CoronaPokerGdxTable.shouldSpeakTableChat(
                LobbyChatMessage.Type.TEXT, true, false, true, false, false));
        assertFalse(CoronaPokerGdxTable.shouldSpeakTableChat(
                LobbyChatMessage.Type.TEXT, true, true, false, false, false));
        assertFalse(CoronaPokerGdxTable.shouldSpeakTableChat(
                LobbyChatMessage.Type.TEXT, true, true, true, true, false));
        assertFalse(CoronaPokerGdxTable.shouldSpeakTableChat(
                LobbyChatMessage.Type.TEXT, true, true, true, false, true));
        assertFalse(CoronaPokerGdxTable.shouldSpeakTableChat(
                LobbyChatMessage.Type.IMAGE, true, true, true, false, false));
    }

    @Test
    void tableTtsCleansChatExactlyBeforeSendingItToTheVoiceService() {
        String chat = "[b]Hola[/b] #42# https://example.test/a.gif "
                + "[color=red]AMIGO[/color]";
        assertEquals("Hola   AMIGO",
                GdxTextToSpeechPlayback.cleanChatMessage(chat));
        assertEquals("hola amigo",
                GdxTextToSpeechPlayback.serviceText(
                        GdxTextToSpeechPlayback.cleanChatMessage(chat)));
        assertEquals("¿qué tal!",
                GdxTextToSpeechPlayback.serviceText("¿Qué tal! 🚀"));
    }

    @Test
    void tableTtsUsesSwingDoubleMasterVolumeWithAUnitCeiling() {
        assertEquals(0f, GdxTextToSpeechPlayback.ttsVolume(0d), 0f);
        assertEquals(0.5f, GdxTextToSpeechPlayback.ttsVolume(0.25d),
                0.000_001f);
        assertEquals(1f, GdxTextToSpeechPlayback.ttsVolume(0.5d), 0f);
        assertEquals(1f, GdxTextToSpeechPlayback.ttsVolume(1d), 0f);
    }

    @Test
    void cardViewerPreservesAspectRatioAndNeverUpscalesHqAssets() {
        Rectangle nativeSize = CoronaPokerGdxTable.cardViewerBounds(
                400, 600, 2_048f, 1_152f);
        assertEquals(400f, nativeSize.width);
        assertEquals(600f, nativeSize.height);
        assertEquals(2f / 3f, nativeSize.width / nativeSize.height,
                0.000_001f);

        Rectangle fitted = CoronaPokerGdxTable.cardViewerBounds(
                1_200, 1_800, 1_280f, 720f);
        assertTrue(fitted.width < 1_200f);
        assertTrue(fitted.height < 1_800f);
        assertEquals(2f / 3f, fitted.width / fitted.height,
                0.000_001f);
        assertEquals((1_280f - fitted.width) / 2f, fitted.x,
                0.000_001f);
        assertEquals((720f - fitted.height) / 2f, fitted.y,
                0.000_001f);
    }

    @Test
    void screenshotContractMatchesSwingAndGalleryNeverUpscales() {
        assertEquals("coronapoker_screenshot_1234.png",
                CoronaPokerGdxTable.screenshotFilename(1234L));
        assertTrue(CoronaPokerGdxTable.isScreenshotFile(
                java.nio.file.Path.of("coronapoker_screenshot_1234.png")));
        assertFalse(CoronaPokerGdxTable.isScreenshotFile(
                java.nio.file.Path.of("other.png")));

        Rectangle bounds = CoronaPokerGdxTable.fitInside(
                800, 600, 100f, 50f, 1_600f, 900f, true);
        assertEquals(800f, bounds.width);
        assertEquals(600f, bounds.height);
        assertEquals(500f, bounds.x);
        assertEquals(200f, bounds.y);
    }

    @Test
    void startupLogoFinishesExactlyDockedBeforeTheMenuReveal() {
        assertEquals(0f, CoronaPokerGdxTable.introLogoDockProgress(3.0f));
        assertTrue(CoronaPokerGdxTable.introLogoDockProgress(4.0f) > 0f);
        assertTrue(CoronaPokerGdxTable.introLogoDockProgress(4.0f) < 1f);
        assertEquals(1f, CoronaPokerGdxTable.introLogoDockProgress(4.8f));
    }

    @Test
    void startupIntroShowsShuffledCardsThenTurnsOnLightsAfterTwoSeconds() {
        assertFalse(CoronaPokerGdxTable.introLightSwitchReached(0f));
        assertEquals(0f, CoronaPokerGdxTable.introLightProgress(0f));
        assertEquals(0f, CoronaPokerGdxTable.introCardAppearProgress(0f));

        assertFalse(CoronaPokerGdxTable.introLightSwitchReached(1.99f));
        assertEquals(0f, CoronaPokerGdxTable.introLightProgress(1.99f));
        assertEquals(1f, CoronaPokerGdxTable.introCardAppearProgress(0.28f),
                0.000_001f);

        assertTrue(CoronaPokerGdxTable.introLightSwitchReached(2.0f));
        assertTrue(CoronaPokerGdxTable.introLightProgress(2.17f) > 0f);
        assertEquals(1f, CoronaPokerGdxTable.introLightProgress(2.34f),
                0.000_001f);
    }

    @Test
    void finalCounterUsesSwingRouteTimingAndBlinkCadence() {
        assertEquals(150d, CoronaPokerGdxTable
                .finalAmountAnimationRange(100d, 150d)[0]);
        assertEquals(50d, CoronaPokerGdxTable
                .finalAmountAnimationRange(100d, 150d)[1]);
        assertEquals(150d,
                CoronaPokerGdxTable.finalAmountValue(0f, 100d, 150d));
        assertEquals(50d,
                CoronaPokerGdxTable.finalAmountValue(1.5f, 100d, 150d));
        assertTrue(CoronaPokerGdxTable.finalAmountVisible(1.50f));
        assertFalse(CoronaPokerGdxTable.finalAmountVisible(1.64f));
        assertTrue(CoronaPokerGdxTable.finalAmountVisible(1.77f));
        assertTrue(CoronaPokerGdxTable.finalAmountVisible(2.29f));
    }

    @Test
    void neutralFinalSummaryDoesNotDrawAGiantMoneyZero() {
        assertFalse(CoronaPokerGdxTable.finalMoneyCounterVisible(0d));
        assertFalse(CoronaPokerGdxTable.finalMoneyCounterVisible(-0d));
        assertTrue(CoronaPokerGdxTable.finalMoneyCounterVisible(0.01d));
        assertTrue(CoronaPokerGdxTable.finalMoneyCounterVisible(-0.01d));
    }

    @Test
    void finalSummaryUsesSwingNeutralCopyAndWaitsUntilItIsVisibleToCapture() {
        TableSessionSummary.PlayerBalance tie =
                new TableSessionSummary.PlayerBalance("server", 10d, 10d, 0);
        TableSessionSummary.PlayerBalance winner =
                new TableSessionSummary.PlayerBalance("server", 14.7d, 10d, 0);

        assertEquals("NI GANAS NI PIERDES", CoronaPokerGdxTable
                .finalSummaryHero(TableSessionSummary.CloseReason.COMPLETED, 0d));
        assertFalse(CoronaPokerGdxTable.finalSummaryScreenshotReady(0.59f,
                tie, TableSessionSummary.CloseReason.COMPLETED, true));
        assertTrue(CoronaPokerGdxTable.finalSummaryScreenshotReady(0.60f,
                tie, TableSessionSummary.CloseReason.COMPLETED, true));
        assertFalse(CoronaPokerGdxTable.finalSummaryScreenshotReady(2.27f,
                winner, TableSessionSummary.CloseReason.COMPLETED, true));
        assertTrue(CoronaPokerGdxTable.finalSummaryScreenshotReady(2.28f,
                winner, TableSessionSummary.CloseReason.COMPLETED, true));
        assertFalse(CoronaPokerGdxTable.finalSummaryScreenshotReady(10f,
                winner, TableSessionSummary.CloseReason.RECOVERABLE_STOP, true));
        assertFalse(CoronaPokerGdxTable.finalSummaryScreenshotReady(10f,
                null, TableSessionSummary.CloseReason.COMPLETED, true));
    }

    @Test
    void finalSummaryNavigationNeverPaintsContinueAsDisabled() {
        assertTrue(CoronaPokerGdxTable.finalSummaryNavEnabled(0));
        assertTrue(CoronaPokerGdxTable.finalSummaryNavEnabled(1));
        assertFalse(CoronaPokerGdxTable.finalSummaryNavEnabled(2));
        assertTrue(CoronaPokerGdxTable.finalSummaryNavEnabled(3));
        assertFalse(CoronaPokerGdxTable.finalSummaryNavEnabled(-1));
        assertFalse(CoronaPokerGdxTable.finalSummaryNavEnabled(4));
    }

    @Test
    void projectsAuthoritativeHandLimitAndLastHandSeparately() {
        GdxTableViewState state = new GdxTableViewState(snapshot());

        state.apply(new TableVisualEvent.HandLimitStatus(1, 20));
        state.apply(new TableVisualEvent.LastHandStatus(2, true));

        assertEquals(20, state.maximumHands());
        assertTrue(state.lastHand());
        state.apply(new TableVisualEvent.HandLimitStatus(3, -1));
        assertEquals(-1, state.maximumHands());
    }

    @Test
    void initialStackFillConsumesSequenceWithoutChangingCanonicalMoney() {
        GdxTableViewState state = new GdxTableViewState(snapshot());
        double before = state.snapshot().players().get(0).stack();

        state.apply(new TableVisualEvent.InitialStackFill(1,
                List.of(new TableVisualEvent.ChipTransfer(
                        "server", before, before, 0d, 0d)),
                1_000L, "misc/balance_count.wav"));

        assertEquals(before, state.snapshot().players().get(0).stack());
        assertEquals(1L, state.lastSequence());
    }

    @Test
    void runItTwiceRewindsOnlyTheRunoutAndLabelsEachHalfPot() {
        GdxTableViewState state = new GdxTableViewState(snapshot());
        state.apply(new TableVisualEvent.RevealCommunityCards(1, 0,
                List.of(card("A_C"), card("K_C"), card("Q_C"))));
        state.apply(new TableVisualEvent.RevealCommunityCards(2, 3,
                List.of(card("J_C"))));
        state.apply(new TableVisualEvent.RevealCommunityCards(3, 4,
                List.of(card("10_C"))));

        state.apply(new TableVisualEvent.RunItTwiceBoard(4,
                TableVisualEvent.RunItTwiceBoard.Side.A,
                "BOTE (CARA-A):", 50d, List.of()));
        assertEquals("BOTE (CARA-A):", state.runItTwicePotPrefix());
        assertEquals(50d, state.snapshot().pot());

        state.apply(new TableVisualEvent.RunItTwiceBoard(5,
                TableVisualEvent.RunItTwiceBoard.Side.B,
                "BOTE (CARA-B):", 50d, List.of(3, 4)));
        assertTrue(state.snapshot().communityCards().get(0).faceUp());
        assertTrue(state.snapshot().communityCards().get(1).faceUp());
        assertTrue(state.snapshot().communityCards().get(2).faceUp());
        assertFalse(state.snapshot().communityCards().get(3).visible());
        assertFalse(state.snapshot().communityCards().get(4).visible());
        assertEquals("BOTE (CARA-B):", state.runItTwicePotPrefix());

        state.apply(new TableVisualEvent.DealCommunityCard(6, 3));
        assertTrue(state.snapshot().communityCards().get(3).visible());
        assertFalse(state.snapshot().communityCards().get(3).faceUp());

        state.apply(new TableVisualEvent.HandBoundary(7, 2,
                TableVisualEvent.HandBoundary.Phase.PREPARE, snapshot()));
        assertEquals("", state.runItTwicePotPrefix());
    }

    @Test
    void runItTwiceSideBClearsSideAVerdictsButKeepsAcceptedReveals() {
        GdxTableViewState state = new GdxTableViewState(snapshot());
        state.apply(new TableVisualEvent.RevealHoleCards(1, "ana",
                card("A_C"), card("K_C"), "COLOR"));
        state.apply(new TableVisualEvent.PartialHand(2, "ana", "COLOR",
                true, 100f));
        state.apply(new TableVisualEvent.ShowdownHighlight(3, "ana", true,
                List.of(0, 1), List.of(0, 1, 2)));
        state.apply(new TableVisualEvent.HandResult(4, "ana", "COLOR", true,
                TableSnapshot.Street.SHOWDOWN));

        state.apply(new TableVisualEvent.RunItTwiceBoard(5,
                TableVisualEvent.RunItTwiceBoard.Side.B,
                "BOTE (CARA-B):", 50d, List.of(3, 4)));

        assertFalse(state.hasHandResult("ana"));
        assertFalse(state.hasShowdownHighlights());
        assertEquals(null, state.partialHandPercentage("ana"));
        assertFalse(player(state, "ana").winner());
        assertEquals("", player(state, "ana").handName());
        assertEquals(List.of("A_C", "K_C"),
                state.presentedHoleCards("ana").stream()
                        .map(TableSnapshot.CardSnapshot::code).toList());
        assertTrue(state.presentedHoleCards("ana").stream()
                .allMatch(TableSnapshot.CardSnapshot::faceUp));
    }

    @Test
    void derivesEveryPokerStreetFromTheOrderedPresentationEvents() {
        GdxTableViewState state = new GdxTableViewState(snapshot());

        state.apply(new TableVisualEvent.HandBoundary(1, 1,
                TableVisualEvent.HandBoundary.Phase.PREPARE, snapshot()));
        assertEquals(TableSnapshot.Street.PREFLOP, state.snapshot().street());

        state.apply(new TableVisualEvent.RevealCommunityCards(2, 0,
                List.of(card("A_C"), card("K_C"), card("Q_C"))));
        assertEquals(TableSnapshot.Street.FLOP, state.snapshot().street());

        state.apply(new TableVisualEvent.RevealCommunityCards(3, 3,
                List.of(card("J_C"))));
        assertEquals(TableSnapshot.Street.TURN, state.snapshot().street());

        state.apply(new TableVisualEvent.RevealCommunityCards(4, 4,
                List.of(card("10_C"))));
        assertEquals(TableSnapshot.Street.RIVER, state.snapshot().street());

        state.apply(new TableVisualEvent.HandBoundary(5, 1,
                TableVisualEvent.HandBoundary.Phase.END,
                snapshotAt(TableSnapshot.Street.SHOWDOWN)));
        assertEquals(TableSnapshot.Street.SHOWDOWN, state.snapshot().street());

        state.apply(new TableVisualEvent.CloseTable(6,
                com.tonikelope.coronapoker.table.TableSessionSummary.empty(),
                TableSnapshot.Street.FINISHED));
        assertEquals(TableSnapshot.Street.FINISHED, state.snapshot().street());
    }

    @Test
    void projectsTheCompleteAuthoritativeLiveConfiguration() {
        GdxTableViewState state = new GdxTableViewState(snapshot());
        NewGameTableDraft draft = new NewGameTableDraft();
        draft.setBlindLevelIndex(2);
        draft.setHandLimit(true);
        draft.setHandLimitCount(30);
        GameConfigCodecV1.Configuration configuration
                = GameConfigCodecV1.fromSettings(draft.snapshot(), false,
                        "live-settings-test");

        state.apply(new TableVisualEvent.GameConfigurationStatus(
                1, configuration));

        assertEquals(configuration, state.gameConfiguration());
        assertEquals(configuration.smallBlind(), state.smallBlind());
        assertEquals(configuration.bigBlind(), state.bigBlind());
        assertEquals(30, state.maximumHands());
    }

    @Test
    void resolvedHandNeverClaimsTheLocalPlayerIsWaitingForATurn() {
        assertEquals("", CoronaPokerGdxTable.localHudTurnStatus(false, true));
        assertEquals("TU TURNO",
                CoronaPokerGdxTable.localHudTurnStatus(true, false));
        assertEquals("ESPERANDO TURNO",
                CoronaPokerGdxTable.localHudTurnStatus(false, false));
    }

    @Test
    void showdownDoesNotFlipCardsThatAreAlreadyFaceUpAgain() {
        TableSnapshot.CardSnapshot left = new TableSnapshot.CardSnapshot(
                "A_C", true, false);
        TableSnapshot.CardSnapshot right = new TableSnapshot.CardSnapshot(
                "K_C", true, false);
        TableSnapshot.PlayerSnapshot local = new TableSnapshot.PlayerSnapshot(
                "ana", 1_000d, 0d, 0d, true, false, false, false,
                -2, -2, 0, 0L, false, TableSnapshot.Position.DEALER,
                "", "", List.of(left, right));
        TableVisualEvent.RevealHoleCards reveal
                = new TableVisualEvent.RevealHoleCards(
                        2L, "ana", left, right, "PAREJA");

        assertTrue(CoronaPokerGdxTable.holeCardsAlreadyRevealed(
                local, reveal));
        TableVisualEvent.RevealHoleCards different
                = new TableVisualEvent.RevealHoleCards(3L, "ana",
                        new TableSnapshot.CardSnapshot("Q_C", true, false),
                        right, "PAREJA");
        assertFalse(CoronaPokerGdxTable.holeCardsAlreadyRevealed(
                local, different));

        // Local sorting can reverse the visual order. A showdown result must
        // still update the hand label without replaying an uncover animation.
        TableVisualEvent.RevealHoleCards sortedShowdown
                = new TableVisualEvent.RevealHoleCards(4L, "ana",
                        right, left, "DOBLE PAREJA");
        assertFalse(CoronaPokerGdxTable.shouldAnimateHoleReveal(
                "ana", local, sortedShowdown));

        // The empty hand name is the deferred blind-straddle reveal, which is
        // a real uncover and must remain animated.
        TableSnapshot.PlayerSnapshot coveredLocal
                = new TableSnapshot.PlayerSnapshot(
                        "ana", 1_000d, 0d, 0d, true, false, false, false,
                        -2, -2, 0, 0L, false,
                        TableSnapshot.Position.DEALER, "", "",
                        List.of(new TableSnapshot.CardSnapshot(
                                "", true, false),
                                new TableSnapshot.CardSnapshot(
                                        "", true, false)));
        TableVisualEvent.RevealHoleCards straddleReveal
                = new TableVisualEvent.RevealHoleCards(5L, "ana",
                        left, right, "");
        assertTrue(CoronaPokerGdxTable.shouldAnimateHoleReveal(
                "ana", coveredLocal, straddleReveal));
    }

    @Test
    void actionVisualContractMatchesTheCanonicalSwingPalette() {
        assertColor(0x64, 0x75, 0x94, 0xff,
                CoronaPokerGdxTable.liveActionColor(null));
        assertColor(0x80, 0x80, 0x80, 0xff,
                CoronaPokerGdxTable.liveActionColor(
                        TableVisualEvent.PlayerAction.ActionKind.FOLD));
        assertColor(0x00, 0x82, 0x00, 0xff,
                CoronaPokerGdxTable.liveActionColor(
                        TableVisualEvent.PlayerAction.ActionKind.CHECK));
        assertColor(0xff, 0xff, 0xff, 0xff,
                CoronaPokerGdxTable.liveActionColor(
                        TableVisualEvent.PlayerAction.ActionKind.CALL));
        assertColor(0xff, 0xff, 0x00, 0xff,
                CoronaPokerGdxTable.liveActionColor(
                        TableVisualEvent.PlayerAction.ActionKind.BET));
        assertColor(0xff, 0xff, 0x00, 0xff,
                CoronaPokerGdxTable.liveActionColor(
                        TableVisualEvent.PlayerAction.ActionKind.RAISE));
        assertColor(0x7d, 0x05, 0xe1, 0xff,
                CoronaPokerGdxTable.liveActionColor(
                        TableVisualEvent.PlayerAction.ActionKind.RERAISE));
        assertColor(0x00, 0x00, 0x00, 0xff,
                CoronaPokerGdxTable.liveActionColor(
                        TableVisualEvent.PlayerAction.ActionKind.ALL_IN));

        assertColor(0xff, 0xff, 0xff, 0xff,
                CoronaPokerGdxTable.liveActionTextColor(
                        TableVisualEvent.PlayerAction.ActionKind.FOLD));
        assertColor(0x00, 0x00, 0x00, 0xff,
                CoronaPokerGdxTable.liveActionTextColor(
                        TableVisualEvent.PlayerAction.ActionKind.CALL));
        assertColor(0x00, 0x00, 0x00, 0xff,
                CoronaPokerGdxTable.liveActionTextColor(
                        TableVisualEvent.PlayerAction.ActionKind.RAISE));
        assertColor(0xff, 0xff, 0xff, 0xff,
                CoronaPokerGdxTable.liveActionTextColor(
                        TableVisualEvent.PlayerAction.ActionKind.RERAISE));
        assertColor(0xff, 0xff, 0xff, 0xff,
                CoronaPokerGdxTable.liveActionTextColor(
                        TableVisualEvent.PlayerAction.ActionKind.ALL_IN));

        assertColor(0xff, 0xff, 0xff, 0xff,
                CoronaPokerGdxTable.hudCallSurfaceColor(
                        ActionControlState.CallAction.CALL));
        assertColor(0x00, 0x82, 0x00, 0xff,
                CoronaPokerGdxTable.hudCallSurfaceColor(
                        ActionControlState.CallAction.CHECK));
        assertColor(0x40, 0x40, 0x40, 0xff,
                CoronaPokerGdxTable.hudCallSurfaceColor(
                        ActionControlState.CallAction.DISABLED));
        assertColor(0x00, 0x00, 0x00, 0xff,
                CoronaPokerGdxTable.hudCallTextColor(
                        ActionControlState.CallAction.CALL));
        assertColor(0xff, 0xff, 0xff, 0xff,
                CoronaPokerGdxTable.hudCallTextColor(
                        ActionControlState.CallAction.DISABLED));
        assertColor(0xff, 0xff, 0x00, 0xff,
                CoronaPokerGdxTable.hudRaiseSurfaceColor(
                        ActionControlState.RaiseAction.RAISE));
        assertColor(0x7d, 0x05, 0xe1, 0xff,
                CoronaPokerGdxTable.hudRaiseSurfaceColor(
                        ActionControlState.RaiseAction.RERAISE));
        assertColor(0x40, 0x40, 0x40, 0xff,
                CoronaPokerGdxTable.hudRaiseSurfaceColor(
                        ActionControlState.RaiseAction.DISABLED));
        assertColor(0x00, 0x00, 0x00, 0xff,
                CoronaPokerGdxTable.hudRaiseTextColor(
                        ActionControlState.RaiseAction.BET));
        assertColor(0xff, 0xff, 0xff, 0xff,
                CoronaPokerGdxTable.hudRaiseTextColor(
                        ActionControlState.RaiseAction.DISABLED));
        assertFalse(CoronaPokerGdxTable.hudCallSurfaceColor(
                ActionControlState.CallAction.CALL).equals(
                CoronaPokerGdxTable.hudRaiseSurfaceColor(
                        ActionControlState.RaiseAction.RAISE)));

        Color call = CoronaPokerGdxTable.hudCallSurfaceColor(
                ActionControlState.CallAction.CALL);
        assertEquals(call, CoronaPokerGdxTable.hudActionRimColor(call, false));
        assertColor(0xff, 0xe0, 0x7a, 0xff,
                CoronaPokerGdxTable.hudActionRimColor(call, true));
    }

    @Test
    void disabledCallNeverBecomesAnUnlabelledWhiteButton() {
        ActionControlState disabledWithCall = new ActionControlState(true,
                ActionControlState.CallAction.DISABLED, 0.2d,
                ActionControlState.RaiseAction.DISABLED,
                0d, 0d, 0d, 0d, false, false, 0.2d, 10d);
        ActionControlState disabledCheck = new ActionControlState(true,
                ActionControlState.CallAction.DISABLED, 0d,
                ActionControlState.RaiseAction.DISABLED,
                0d, 0d, 0d, 0d, false, false, 0d, 10d);

        assertEquals("IR (+0.2)",
                CoronaPokerGdxTable.callLabel(disabledWithCall));
        assertEquals("PASAR", CoronaPokerGdxTable.callLabel(disabledCheck));
    }

    @Test
    void anchoredConsoleScrollUsesTheNaturalWheelDirection() {
        assertEquals(7, CoronaPokerGdxTable
                .anchoredScrollAfterWheel(4, 20, -1f));
        assertEquals(1, CoronaPokerGdxTable
                .anchoredScrollAfterWheel(4, 20, 1f));
        assertEquals(0, CoronaPokerGdxTable
                .anchoredScrollAfterWheel(0, 20, 1f));
    }

    @Test
    void gameLogKeepsSwingStyleAmountsAndRanksInsteadOfFlatWhiteText() {
        List<GdxGameLogFormatter.Run> runs = GdxGameLogFormatter
                .runs("CoronaBot$3 SUBE (+0,50) -> Pareja");
        GdxGameLogFormatter.Run amount = runs.stream()
                .filter(run -> run.text().equals("(+0,50)"))
                .findFirst().orElseThrow();
        GdxGameLogFormatter.Run rank = runs.stream()
                .filter(run -> run.text().contains("-> Pareja"))
                .findFirst().orElseThrow();

        assertColor(0xff, 0xc8, 0x5a, 0xff, amount.color());
        assertColor(0xcd, 0xcd, 0xcd, 0xff, rank.color());
    }

    @Test
    void gameLogInterpretsSwingRoleMarkersInsteadOfPrintingThem() {
        assertEquals(GdxGameLogFormatter.Marker.DEALER,
                GdxGameLogFormatter.marker("(D ) server 10 10"));
        assertEquals(GdxGameLogFormatter.Marker.SMALL_BLIND,
                GdxGameLogFormatter.marker("(SB) player 9,9 10"));
        assertEquals(GdxGameLogFormatter.Marker.MONEY,
                GdxGameLogFormatter.marker("($$) 70 70"));
        assertEquals(" NICK STACK BUYIN",
                GdxGameLogFormatter.visibleText(
                        "(##) NICK STACK BUYIN"));
        assertEquals(" server 10 10",
                GdxGameLogFormatter.runs("(  ) server 10 10")
                        .stream().map(GdxGameLogFormatter.Run::text)
                        .reduce("", String::concat));
    }

    @Test
    void thinkingSurfacePreservesSwingTransparency() {
        assertColor(0xcc, 0xcc, 0xcc, 0x4b,
                CoronaPokerGdxTable.LEGACY_THINKING);
        assertEquals((0x4b / 255f) * 0.92f * 0.75f,
                CoronaPokerGdxTable.composedAlpha(
                        CoronaPokerGdxTable.LEGACY_THINKING, 0.92f, 0.75f),
                0.000_001f);
    }

    @Test
    void liveShowdownRimsUseThePreservedOrderedWinner() {
        assertColor(0x00, 0xff, 0x00, 0xff,
                CoronaPokerGdxTable.settledShowdownColor(true));
        assertColor(0xff, 0x00, 0x00, 0xff,
                CoronaPokerGdxTable.settledShowdownColor(false));
    }

    @Test
    void timeoutRimOverridesOrdinaryAndActiveSeatColours() {
        TableSnapshot.PlayerSnapshot timedOut = timedOutPlayer("remote");

        assertColor(0xff, 0x00, 0xff, 0xff,
                CoronaPokerGdxTable.timeoutAwareRim(timedOut, Color.GREEN));
        assertEquals(Color.GREEN,
                CoronaPokerGdxTable.timeoutAwareRim(
                        showdownPlayer("healthy", false), Color.GREEN));
    }

    @Test
    void gameTextResolvesShowdownKeysWithoutSwing() {
        assertEquals("DOBLE PAREJA",
                new GdxGameText("es").translate("hand.two_pair"));
        assertEquals("TWO PAIRS",
                new GdxGameText("en").translate("hand.two_pair"));
        assertEquals("NO VA",
                new GdxGameText("es").translate("action.label.fold2"));
    }

    @Test
    void liveHudHitMapRoutesEveryCanonicalPokerAction() {
        assertEquals(1, CoronaPokerGdxTable.hudTarget(700f, 70f, 1920f));
        assertEquals(2, CoronaPokerGdxTable.hudTarget(900f, 70f, 1920f));
        assertEquals(3, CoronaPokerGdxTable.hudTarget(1035f, 70f, 1920f));
        assertEquals(4, CoronaPokerGdxTable.hudTarget(1140f, 70f, 1920f));
        assertEquals(5, CoronaPokerGdxTable.hudTarget(1250f, 70f, 1920f));
        assertEquals(6, CoronaPokerGdxTable.hudTarget(1420f, 70f, 1920f));
        assertEquals(0, CoronaPokerGdxTable.hudTarget(300f, 70f, 1920f));
    }

    @Test
    void projectsCausalOpeningAndBetLandingWithoutReadingWidgets() {
        GdxTableViewState state = new GdxTableViewState(snapshot());
        TableSnapshot nextHand = new TableSnapshot(2L, "ana",
                TableSnapshot.Street.PREFLOP, 0d, "", false, List.of(
                player("ana", TableSnapshot.Position.BIG_BLIND),
                player("borja", TableSnapshot.Position.DEALER)), List.of());

        state.apply(new TableVisualEvent.HandBoundary(1, 2,
                TableVisualEvent.HandBoundary.Phase.PREPARE, nextHand));
        state.apply(new TableVisualEvent.PositionRotation(2, List.of(
                new TableVisualEvent.PositionTransfer("ana", "borja",
                        TableSnapshot.Position.DEALER, false),
                new TableVisualEvent.PositionTransfer("borja", "ana",
                        TableSnapshot.Position.BIG_BLIND, false)), 440));
        state.apply(new TableVisualEvent.CollectBets(3,
                List.of(new TableVisualEvent.ChipTransfer(
                        "ana", 100d, 900d, 0d, 100d)),
                0d, 100d));
        state.apply(new TableVisualEvent.PlayerAction(4, "borja",
                TableVisualEvent.PlayerAction.ActionKind.CALL,
                "CALL", 100d, 100d, 900d, 100d, 100d));
        assertEquals(TableVisualEvent.PlayerAction.ActionKind.CALL,
                state.actionKind("borja"));
        assertEquals("CALL", state.actionLabel("borja"));
        state.apply(new TableVisualEvent.CollectBets(5,
                List.of(new TableVisualEvent.ChipTransfer(
                        "borja", 100d, 900d, 0d, 100d)),
                100d, 200d));

        TableSnapshot.PlayerSnapshot ana = player(state, "ana");
        TableSnapshot.PlayerSnapshot borja = player(state, "borja");
        assertEquals(TableSnapshot.Position.BIG_BLIND, ana.position());
        assertEquals(TableSnapshot.Position.DEALER, borja.position());
        assertEquals(900d, ana.stack());
        assertEquals(900d, borja.stack());
        assertEquals(0d, borja.streetBet());
        assertEquals(100d, borja.potContribution());
        assertEquals(200d, state.snapshot().pot());
        assertEquals(5L, state.lastSequence());

        assertThrows(IllegalArgumentException.class,
                () -> state.apply(new TableVisualEvent.CloseTable(5,
                        com.tonikelope.coronapoker.table.TableSessionSummary.empty(),
                        TableSnapshot.Street.FINISHED)));
    }

    @Test
    void keepsEachPlayersWholeHandContributionAfterChipsReachThePot() {
        GdxTableViewState state = new GdxTableViewState(snapshot());

        state.apply(new TableVisualEvent.CollectBets(1,
                List.of(new TableVisualEvent.ChipTransfer(
                        "borja", 20d, 980d, 0d, 20d)),
                0d, 20d));
        state.apply(new TableVisualEvent.PlayerAction(2, "borja",
                TableVisualEvent.PlayerAction.ActionKind.CALL,
                "VA", 50d, 30d, 950d, 30d, 50d));

        TableSnapshot.PlayerSnapshot borja = player(state, "borja");
        assertEquals(50d, borja.potContribution());
        assertEquals(30d, borja.streetBet());
        assertEquals(20d, CoronaPokerGdxTable.displayedPotContribution(
                borja.potContribution(), 30d));
        assertEquals(50d, CoronaPokerGdxTable.displayedPotContribution(
                borja.potContribution(), 0d));
    }

    @Test
    void consumesCanonicalMoneyAfterValuesWithoutReplayingPokerAccounting() {
        GdxTableViewState state = new GdxTableViewState(snapshot());

        state.apply(new TableVisualEvent.PlayerAction(1, "borja",
                TableVisualEvent.PlayerAction.ActionKind.RAISE,
                "SUBE", 300d, 125d, 875d, 125d, 145d));

        TableSnapshot.PlayerSnapshot borja = player(state, "borja");
        assertEquals(875d, borja.stack());
        assertEquals(125d, borja.streetBet());
        assertEquals(145d, borja.potContribution());

        state.apply(new TableVisualEvent.Payout(
                2, "borja", 400d, 0, 1_275d, 35d));
        assertEquals(1_275d, player(state, "borja").stack());
        assertEquals(35d, state.snapshot().pot());
    }

    @Test
    void payoutMarksAnUncontestedWinnerWithoutInventingAShowdownResult() {
        GdxTableViewState state = new GdxTableViewState(snapshot());

        state.apply(new TableVisualEvent.Payout(
                1, "borja", 40d, 0, 1_040d, 0d));

        assertTrue(player(state, "borja").winner());
        assertEquals(Boolean.TRUE, state.resolvedHandWinner("borja"));
        assertFalse(state.hasHandResult("borja"));
    }

    @Test
    void rejectsChipCollectionForAPlayerAbsentFromTheCanonicalSnapshot() {
        GdxTableViewState state = new GdxTableViewState(snapshot());

        assertThrows(IllegalArgumentException.class,
                () -> state.apply(new TableVisualEvent.CollectBets(1,
                        List.of(new TableVisualEvent.ChipTransfer(
                                "demo-ghost", 20d, 0d, 0d, 20d)),
                        0d, 20d)));
    }

    @Test
    void keepsCanonicalActionLabelAcrossAuxiliaryEventsAndClearsItNextHand() {
        GdxTableViewState state = new GdxTableViewState(snapshot());

        state.apply(new TableVisualEvent.PlayerAction(1, "borja",
                TableVisualEvent.PlayerAction.ActionKind.RAISE,
                "SUBE", 40d, 20d, 980d, 20d, 20d));
        state.apply(new TableVisualEvent.TelemetryStatus(2, List.of()));

        assertEquals("SUBE", state.actionLabel("borja"));

        state.apply(new TableVisualEvent.HandBoundary(3, 2,
                TableVisualEvent.HandBoundary.Phase.PREPARE, snapshot()));
        assertEquals("", state.actionLabel("borja"));
    }

    @Test
    void newCommunityStreetClearsExpiredActionsButKeepsFoldVisible() {
        GdxTableViewState state = new GdxTableViewState(snapshot());
        state.apply(new TableVisualEvent.PlayerAction(1, "ana",
                TableVisualEvent.PlayerAction.ActionKind.RAISE,
                "RESUBE", 40d, 20d, 980d, 20d, 20d));
        state.apply(new TableVisualEvent.PlayerAction(2, "borja",
                TableVisualEvent.PlayerAction.ActionKind.FOLD,
                "NO VA", 0d, 0d, 1_000d, 0d, 0d));
        state.apply(new TableVisualEvent.CallCost(3, "+0.2", "ana"));

        state.apply(new TableVisualEvent.RevealCommunityCards(4, 0,
                List.of(card("A_C"), card("K_C"), card("Q_C"))));

        assertEquals("", state.actionLabel("ana"));
        assertEquals("", player(state, "ana").lastAction());
        assertEquals("NO VA", state.actionLabel("borja"));
        assertEquals("NO VA", player(state, "borja").lastAction());
        assertEquals("", state.callCostText());
        assertEquals("", state.callCostAggressorNickname());
    }

    @Test
    void actionControlsFollowTurnLifecycle() {
        GdxTableViewState state = new GdxTableViewState(snapshot());
        ActionControlState controls = ActionControlState.forTurn(
                20d, 10d, 10d, 5d, 10d, 100d, 3, true, 0);
        state.apply(new TableVisualEvent.TurnTimer(1, "ana", 30_000,
                30_000, TableVisualEvent.TurnTimer.Phase.START));
        state.apply(new TableVisualEvent.ActionControls(2, controls));

        assertEquals(ActionControlState.CallAction.CALL,
                state.actionControls().callAction());
        assertEquals(10d, state.actionControls().callAmount());

        state.apply(new TableVisualEvent.ActionControls(3,
                ActionControlState.disabled().withShowCards(true)));
        assertTrue(state.actionControls().showCards());

        state.apply(new TableVisualEvent.TurnTimer(4, "", 30_000, 0,
                TableVisualEvent.TurnTimer.Phase.STOP));
        assertEquals(ActionControlState.disabled(), state.actionControls());
    }

    @Test
    void turnWithoutCanonicalDeadlineDoesNotInventAFullTimerBar() {
        GdxTableViewState state = new GdxTableViewState(snapshot());

        state.apply(new TableVisualEvent.TurnTimer(1, "ana", 0, 0,
                TableVisualEvent.TurnTimer.Phase.START));

        assertEquals("ana", state.snapshot().currentTurnNickname());
        assertFalse(state.turnTimerVisible());
        assertEquals(0L, state.turnRemainingMillis());

        state.apply(new TableVisualEvent.TurnTimer(2, "", 0, 0,
                TableVisualEvent.TurnTimer.Phase.STOP));
        assertTrue(state.snapshot().currentTurnNickname().isBlank());
        assertFalse(state.turnTimerVisible());
    }

    @Test
    void preActionControlsFollowTheCanonicalDealerSignal() {
        GdxTableViewState state = new GdxTableViewState(snapshot());

        state.apply(new TableVisualEvent.PreActionControls(1, true, false));
        assertTrue(state.preActionControlsActive());

        state.apply(new TableVisualEvent.PreActionControls(2, false, true));
        assertFalse(state.preActionControlsActive());
    }

    @Test
    void executedAutoChoiceStaysArmedUntilCoreOrUserClearsIt() {
        assertEquals(1, CoronaPokerGdxTable.retainedPreAction(1, 1, true));
        assertEquals(2, CoronaPokerGdxTable.retainedPreAction(2, 2, true));
        assertEquals(2, CoronaPokerGdxTable.retainedPreAction(2, 6, true));
        assertEquals(0, CoronaPokerGdxTable.retainedPreAction(2, 0, true));
        assertEquals(0, CoronaPokerGdxTable.retainedPreAction(2, 2, false));
    }

    @Test
    void autoChoiceWithoutALegalTargetIsDisarmedLikeSwing() {
        assertEquals(0,
                CoronaPokerGdxTable.queuedPreActionAfterTargetResolution(2, 0));
        assertEquals(0,
                CoronaPokerGdxTable.queuedPreActionAfterTargetResolution(2, 2));
        assertEquals(0,
                CoronaPokerGdxTable.queuedPreActionAfterTargetResolution(2, 6));
    }

    @Test
    void canonicalHandBoundaryPreservesOrClearsTheQueuedAutoChoice() {
        assertEquals(2, CoronaPokerGdxTable
                .queuedPreActionAfterControlsEvent(2, false, false));
        assertEquals(2, CoronaPokerGdxTable
                .queuedPreActionAfterControlsEvent(2, true, false));
        assertEquals(0, CoronaPokerGdxTable
                .queuedPreActionAfterControlsEvent(2, false, true));
    }

    @Test
    void disablingAutoButtonsAlwaysDisarmsTheQueuedChoice() {
        assertEquals(0, CoronaPokerGdxTable
                .queuedPreActionAfterAutoButtonsToggle(2, false));
        assertEquals(2, CoronaPokerGdxTable
                .queuedPreActionAfterAutoButtonsToggle(2, true));
        assertEquals(0, CoronaPokerGdxTable
                .queuedPreActionAfterAutoButtonsToggle(0, true));
    }

    @Test
    void pausedOverlayConsumesOnlyACompletedClickWhilePaused() {
        assertTrue(CoronaPokerGdxTable
                .pauseOverlayConsumesRelease(true, true));
        assertFalse(CoronaPokerGdxTable
                .pauseOverlayConsumesRelease(true, false));
        assertFalse(CoronaPokerGdxTable
                .pauseOverlayConsumesRelease(false, true));
    }

    @Test
    void liveDealTimingMatchesClassicFloorsAndConfiguredSpeed() {
        assertEquals(0.10f, CoronaPokerGdxTable
                .liveDealCadenceSeconds(10, 100), 0.000_001f);
        assertEquals(0.15f, CoronaPokerGdxTable
                .liveDealFlightSeconds(10, 100), 0.000_001f);
        assertEquals(0.25f, CoronaPokerGdxTable
                .liveDealCadenceSeconds(2, 100), 0.000_001f);
        assertEquals(0.25f, CoronaPokerGdxTable
                .liveDealFlightSeconds(2, 100), 0.000_001f);
        assertEquals(0.06f, CoronaPokerGdxTable
                .liveDealCadenceSeconds(10, 60), 0.000_001f);
        assertEquals(0.09f, CoronaPokerGdxTable
                .liveDealFlightSeconds(10, 60), 0.000_001f);
        assertEquals(0.15f, CoronaPokerGdxTable
                .liveDealCadenceSeconds(10, 150), 0.000_001f);
        assertEquals(0.225f, CoronaPokerGdxTable
                .liveDealFlightSeconds(10, 150), 0.000_001f);
    }

    @Test
    void openingAutoCallSettingsDisarmsOnlyAnOutOfTurnChoiceLikeSwing() {
        assertEquals(0, CoronaPokerGdxTable
                .preActionAfterOpeningAutoCallSettings(2, false));
        assertEquals(2, CoronaPokerGdxTable
                .preActionAfterOpeningAutoCallSettings(2, true));
    }

    @Test
    void autoActionVetoClosesAsSoonAsTheLocalDecisionResolves() {
        TableSnapshot localTurn = snapshot(false, "ana");
        TableVisualEvent.PlayerAction localAction =
                new TableVisualEvent.PlayerAction(1, "ana",
                        TableVisualEvent.PlayerAction.ActionKind.FOLD,
                        "NO VA", 0d, 0d, 1_000d, 0d, 0d);
        TableVisualEvent.PlayerAction rivalAction =
                new TableVisualEvent.PlayerAction(2, "borja",
                        TableVisualEvent.PlayerAction.ActionKind.CALL,
                        "VA", 1d, 1d, 999d, 1d, 1d);

        assertTrue(CoronaPokerGdxTable.shouldDismissAutoActionDialog(
                localAction, localTurn));
        assertFalse(CoronaPokerGdxTable.shouldDismissAutoActionDialog(
                rivalAction, localTurn));
        assertTrue(CoronaPokerGdxTable.shouldDismissAutoActionDialog(
                rivalAction, snapshot(false, "borja")));
    }

    @Test
    void staleTurnNicknameCannotReactivateAnUnavailablePlayer() {
        TableSnapshot.PlayerSnapshot active = playerWithAvailability(
                "ana", true, false, false, false);
        assertTrue(CoronaPokerGdxTable.isActionableTurn(
                snapshotWithPlayers(false, "ana", active), "ana"));
        assertFalse(CoronaPokerGdxTable.isActionableTurn(
                snapshotWithPlayers(false, "ana", active), "ana", true),
                "a reconnecting or terminating frontend cannot submit a stale action");
        assertTrue(CoronaPokerGdxTable.isActionableTurn(
                snapshotWithPlayers(false, "ana", active), "ana", false));

        assertFalse(CoronaPokerGdxTable.isActionableTurn(
                snapshotWithPlayers(true, "ana", active), "ana"));
        assertFalse(CoronaPokerGdxTable.isActionableTurn(
                snapshotWithPlayers(false, "borja", active), "ana"));
        assertFalse(CoronaPokerGdxTable.isActionableTurn(
                snapshotWithPlayers(false, "ana", playerWithAvailability(
                        "ana", false, false, false, false)), "ana"));
        assertFalse(CoronaPokerGdxTable.isActionableTurn(
                snapshotWithPlayers(false, "ana", playerWithAvailability(
                        "ana", true, true, false, false)), "ana"));
        assertFalse(CoronaPokerGdxTable.isActionableTurn(
                snapshotWithPlayers(false, "ana", playerWithAvailability(
                        "ana", true, false, true, false)), "ana"));
        assertFalse(CoronaPokerGdxTable.isActionableTurn(
                snapshotWithPlayers(false, "ana", playerWithAvailability(
                        "ana", true, false, false, true)), "ana"));
        assertFalse(CoronaPokerGdxTable.isActionableTurn(
                snapshotWithPlayers(false, "ana", playerWithAvailability(
                        "borja", true, false, false, false)), "ana"));
    }

    @Test
    void quickChatUsesTheClassicOrdinalKeyAsAnExclusiveToggle() {
        assertTrue(CoronaPokerGdxTable.isQuickChatToggleCharacter('º', false));
        assertFalse(CoronaPokerGdxTable.isQuickChatToggleCharacter('º', true));
        assertFalse(CoronaPokerGdxTable.isQuickChatToggleCharacter('`', false));
    }

    @Test
    void pauseFreezesAndResumeContinuesTheTurnTimer() {
        AtomicLong now = new AtomicLong(1_000_000_000L);
        GdxTableViewState state = new GdxTableViewState(snapshot(), now::get);
        state.apply(new TableVisualEvent.TurnTimer(1, "ana", 10_000,
                10_000, TableVisualEvent.TurnTimer.Phase.START));
        ActionControlState controls = ActionControlState.forTurn(
                20d, 10d, 10d, 5d, 10d, 100d, 3, true, 0);
        state.apply(new TableVisualEvent.ActionControls(2, controls));
        now.addAndGet(2_000_000_000L);
        assertEquals(8_000L, state.turnRemainingMillis());

        state.apply(new TableVisualEvent.PauseStatus(3, true));
        now.addAndGet(20_000_000_000L);
        assertEquals(8_000L, state.turnRemainingMillis());
        assertEquals(controls, state.actionControls());
        assertEquals("ana", state.snapshot().currentTurnNickname());

        state.apply(new TableVisualEvent.PauseStatus(4, false));
        now.addAndGet(1_500_000_000L);
        assertEquals(6_500L, state.turnRemainingMillis());
        assertEquals(controls, state.actionControls());
        assertEquals("ana", state.snapshot().currentTurnNickname());
    }

    @Test
    void showdownProgressCountsDownWithoutOwningAPlayerTurn() {
        AtomicLong now = new AtomicLong(1_000_000_000L);
        GdxTableViewState state = new GdxTableViewState(snapshot(), now::get);
        state.apply(new TableVisualEvent.TurnTimer(1, "ana", 30_000,
                30_000, TableVisualEvent.TurnTimer.Phase.START));
        assertEquals("ana", state.snapshot().currentTurnNickname());

        // Real dealer order: the final player turn ends before the hand enters
        // showdown and its table-wide countdown starts.  HandBoundary.END must
        // therefore clear the stale owner even if no auxiliary synchronize has
        // happened in between.
        state.apply(new TableVisualEvent.HandBoundary(2, 1,
                TableVisualEvent.HandBoundary.Phase.END,
                snapshotAt(TableSnapshot.Street.SHOWDOWN)));
        assertTrue(state.snapshot().currentTurnNickname().isBlank());
        assertEquals(0L, state.turnRemainingMillis());

        state.apply(new TableVisualEvent.SharedProgress(3,
                TableVisualEvent.SharedProgress.Mode.COUNTDOWN, 10));

        assertTrue(state.snapshot().currentTurnNickname().isBlank());
        assertTrue(state.sharedProgressVisible());
        assertEquals(1f, state.sharedProgressFraction(), 0.001f);

        now.addAndGet(2_500_000_000L);
        assertEquals(0.75f, state.sharedProgressFraction(), 0.001f);

        state.apply(new TableVisualEvent.PauseStatus(4, true));
        now.addAndGet(5_000_000_000L);
        assertEquals(0.75f, state.sharedProgressFraction(), 0.001f);

        state.apply(new TableVisualEvent.PauseStatus(5, false));
        now.addAndGet(1_000_000_000L);
        assertEquals(0.65f, state.sharedProgressFraction(), 0.001f);

        state.apply(new TableVisualEvent.SharedProgress(6,
                TableVisualEvent.SharedProgress.Mode.RESET, 0));
        assertTrue(!state.sharedProgressVisible());
    }

    @Test
    void cardsAppearOnlyWhenTheirOwnDealEventLands() {
        GdxTableViewState state = new GdxTableViewState(snapshot());
        assertEquals(0, player(state, "ana").holeCards().size());
        assertEquals(0, state.snapshot().communityCards().size());

        state.apply(new TableVisualEvent.DealHoleCard(1, "ana", 0,
                new TableSnapshot.CardSnapshot("", false, false)));
        assertEquals(1, player(state, "ana").holeCards().size());

        state.apply(new TableVisualEvent.DealHoleCard(2, "ana", 1,
                new TableSnapshot.CardSnapshot("", false, false)));
        assertEquals(2, player(state, "ana").holeCards().size());

        state.apply(new TableVisualEvent.DealCommunityCard(3, 0));
        assertEquals(1, state.snapshot().communityCards().size());

        state.apply(new TableVisualEvent.ShowdownHighlight(4, "ana", true,
                List.of(0, 1), List.of(0)));
        assertTrue(state.hasShowdownHighlights());

        state.apply(new TableVisualEvent.HandBoundary(5, 2,
                TableVisualEvent.HandBoundary.Phase.PREPARE, snapshot()));
        assertEquals(0, player(state, "ana").holeCards().size());
        assertEquals(0, state.snapshot().communityCards().size());
        assertTrue(!state.hasShowdownHighlights());
    }

    @Test
    void showdownKeepsOnlyTheWinningFiveCardsFocused() {
        GdxTableViewState state = new GdxTableViewState(snapshot());
        state.apply(new TableVisualEvent.RevealHoleCards(1, "ana",
                card("A_C"), card("K_D")));
        state.apply(new TableVisualEvent.RevealHoleCards(2, "borja",
                card("Q_C"), card("J_D")));
        state.apply(new TableVisualEvent.RevealCommunityCards(3, 0,
                List.of(card("10_C"), card("9_C"), card("8_C"))));
        state.apply(new TableVisualEvent.RevealCommunityCards(4, 3,
                List.of(card("2_D"))));
        state.apply(new TableVisualEvent.RevealCommunityCards(5, 4,
                List.of(card("3_D"))));
        state.apply(new TableVisualEvent.ShowdownHighlight(6, "ana", true,
                List.of(0), List.of(0, 1, 2, 3)));
        state.apply(new TableVisualEvent.ShowdownHighlight(7, "borja", true,
                List.of(0, 1), List.of(0, 1, 2)));
        state.apply(new TableVisualEvent.HandResult(8, "ana",
                "ESCALERA", true, TableSnapshot.Street.SHOWDOWN));
        state.apply(new TableVisualEvent.HandResult(9, "borja",
                "PAREJA", false, TableSnapshot.Street.SHOWDOWN));

        assertEquals(Boolean.TRUE,
                state.showdownCardSelected("ana", 0, false));
        assertEquals(Boolean.FALSE,
                state.showdownCardSelected("ana", 1, false));
        assertEquals(Boolean.FALSE,
                state.showdownCardSelected("borja", 0, false));
        assertEquals(Boolean.TRUE,
                state.showdownCardSelected("", 3, true));
        assertEquals(Boolean.FALSE,
                state.showdownCardSelected("", 4, true));
    }

    @Test
    void newHandReactivatesAPlayerWhoOnlyFoldedThePreviousHand() {
        GdxTableViewState state = new GdxTableViewState(snapshot());
        state.apply(new TableVisualEvent.FoldHoleCards(1, "borja"));
        assertTrue(!player(state, "borja").active());

        state.apply(new TableVisualEvent.HandBoundary(2, 2,
                TableVisualEvent.HandBoundary.Phase.PREPARE, snapshot()));

        assertTrue(player(state, "borja").active());
    }

    @Test
    void acceptedFoldImmediatelyDisablesBothCards() {
        GdxTableViewState state = new GdxTableViewState(snapshot());
        state.apply(new TableVisualEvent.DealHoleCard(1, "borja", 0,
                card("A_C")));
        state.apply(new TableVisualEvent.DealHoleCard(2, "borja", 1,
                card("K_C")));

        state.apply(new TableVisualEvent.PlayerAction(3, "borja",
                TableVisualEvent.PlayerAction.ActionKind.FOLD,
                "NO VA", 0d, 0d, 1_000d, 0d, 0d));
        state.apply(new TableVisualEvent.FoldHoleCards(4, "borja"));

        assertFalse(player(state, "borja").active());
        assertTrue(player(state, "borja").holeCards().stream()
                .allMatch(TableSnapshot.CardSnapshot::disabled));
    }

    @Test
    void endSnapshotCannotResurrectFoldedSeatOrItsCardsDuringShowdown() {
        GdxTableViewState state = new GdxTableViewState(snapshot());
        state.apply(new TableVisualEvent.DealHoleCard(1, "borja", 0,
                card("A_C")));
        state.apply(new TableVisualEvent.DealHoleCard(2, "borja", 1,
                card("K_C")));
        TableSnapshot preFold = state.snapshot();

        state.apply(new TableVisualEvent.PlayerAction(3, "borja",
                TableVisualEvent.PlayerAction.ActionKind.FOLD,
                "NO VA", 0d, 0d, 1_000d, 0d, 0d));
        state.apply(new TableVisualEvent.FoldHoleCards(4, "borja"));
        state.apply(new TableVisualEvent.HandBoundary(5, 1,
                TableVisualEvent.HandBoundary.Phase.END,
                new TableSnapshot(preFold.revision(), preFold.localNickname(),
                        TableSnapshot.Street.SHOWDOWN, preFold.pot(), "",
                        preFold.paused(), preFold.players(),
                        preFold.communityCards())));

        assertTrue(state.foldedThisHand("borja"));
        assertTrue(state.presentedHoleCards("borja").isEmpty());
        assertTrue(CoronaPokerGdxTable.shouldDimSeat(
                player(state, "borja").active(), state.hasHandResult("borja"),
                state.foldedThisHand("borja")));
    }

    @Test
    void explicitVoluntaryRevealCanShowFoldedCardsWithoutReactivatingSeat() {
        GdxTableViewState state = new GdxTableViewState(snapshot());
        state.apply(new TableVisualEvent.DealHoleCard(1, "borja", 0,
                card("A_C")));
        state.apply(new TableVisualEvent.DealHoleCard(2, "borja", 1,
                card("K_C")));
        state.apply(new TableVisualEvent.PlayerAction(3, "borja",
                TableVisualEvent.PlayerAction.ActionKind.FOLD,
                "NO VA", 0d, 0d, 1_000d, 0d, 0d));
        state.apply(new TableVisualEvent.FoldHoleCards(4, "borja"));

        state.apply(new TableVisualEvent.RevealHoleCards(5, "borja",
                card("A_C"), card("K_C"), "CARTA ALTA"));

        assertEquals(List.of(card("A_C"), card("K_C")),
                state.presentedHoleCards("borja"),
                "SHOW/IWTSTH is an explicit reveal even after folding");
        assertTrue(state.foldedThisHand("borja"),
                "showing cards must not reactivate a folded player");
        assertTrue(CoronaPokerGdxTable.shouldDimSeat(
                player(state, "borja").active(), state.hasHandResult("borja"),
                state.foldedThisHand("borja")));
    }

    @Test
    void pauseStatusCannotRestoreFoldedCards() {
        GdxTableViewState state = new GdxTableViewState(snapshot());
        state.apply(new TableVisualEvent.DealHoleCard(1, "borja", 0,
                card("A_C")));
        state.apply(new TableVisualEvent.DealHoleCard(2, "borja", 1,
                card("K_C")));
        state.apply(new TableVisualEvent.PlayerAction(3, "borja",
                TableVisualEvent.PlayerAction.ActionKind.FOLD,
                "NO VA", 0d, 0d, 1_000d, 0d, 0d));
        state.apply(new TableVisualEvent.FoldHoleCards(4, "borja"));

        state.apply(new TableVisualEvent.PauseStatus(5, true));

        assertFalse(player(state, "borja").active());
        assertTrue(player(state, "borja").holeCards().stream()
                .allMatch(TableSnapshot.CardSnapshot::disabled));
    }

    @Test
    void auxiliaryStatusEventsCannotInventOrEraseDealtCards() {
        GdxTableViewState state = new GdxTableViewState(snapshot());
        state.apply(new TableVisualEvent.TelemetryStatus(1, List.of()));
        assertTrue(player(state, "ana").holeCards().stream()
                .noneMatch(TableSnapshot.CardSnapshot::visible));
        assertTrue(state.snapshot().communityCards().stream()
                .noneMatch(TableSnapshot.CardSnapshot::visible));

        state.apply(new TableVisualEvent.DealHoleCard(2, "ana", 0,
                card("A_C")));
        state.apply(new TableVisualEvent.DealCommunityCard(3, 0));
        state.apply(new TableVisualEvent.PauseStatus(4, true));

        assertTrue(player(state, "ana").holeCards().get(0).visible());
        assertEquals(1, player(state, "ana").holeCards().size());
        assertTrue(state.snapshot().communityCards().get(0).visible());
        assertEquals(1, state.snapshot().communityCards().size());
    }

    @Test
    void telemetryCanOnlyUpdateTelemetryFields() {
        TableSnapshot live = new TableSnapshot(12L, "ana",
                TableSnapshot.Street.RIVER, 42.75d, "borja", false,
                snapshot().players(), List.of(card("A_C"), card("K_D"),
                        card("Q_T"), card("2_P"), card("7_C")));
        GdxTableViewState state = new GdxTableViewState(live);

        state.apply(new TableVisualEvent.TelemetryStatus(1, List.of(
                new TableVisualEvent.PlayerTelemetry(
                        "borja", 81, 77, 3, 9_876L))));

        assertEquals(TableSnapshot.Street.RIVER, state.snapshot().street());
        assertEquals(42.75d, state.snapshot().pot());
        assertEquals("borja", state.snapshot().currentTurnNickname());
        assertEquals(List.of("A_C", "K_D", "Q_T", "2_P", "7_C"),
                state.snapshot().communityCards().stream()
                        .map(TableSnapshot.CardSnapshot::code).toList());
        TableSnapshot.PlayerSnapshot updated = player(state, "borja");
        assertEquals(81, updated.latency());
        assertEquals(77, updated.previousLatency());
        assertEquals(3, updated.reconnectionCount());
        assertEquals(9_876L, updated.telemetryAt());
        assertEquals(player("borja", false).stack(), updated.stack());
        assertEquals(player("borja", false).holeCards(), updated.holeCards());
    }

    @Test
    void revealedHandStaysNeutralUntilItsResultArrives() {
        GdxTableViewState state = new GdxTableViewState(snapshot());
        state.apply(new TableVisualEvent.RevealHoleCards(1, "borja",
                card("A_C"), card("K_C"), "COLOR"));

        assertEquals("COLOR", player(state, "borja").handName());
        assertFalse(state.hasHandResult("borja"));

        state.apply(new TableVisualEvent.HandResult(2, "borja", "COLOR", false,
                TableSnapshot.Street.SHOWDOWN));
        assertTrue(state.hasHandResult("borja"));
    }

    @Test
    void partialHandCarriesTheDealersMonteCarloResultUntilShowdown() {
        GdxTableViewState state = new GdxTableViewState(snapshot());

        state.apply(new TableVisualEvent.PartialHand(1, "borja", "COLOR",
                true, -1f));
        assertEquals("COLOR", player(state, "borja").handName());
        assertTrue(player(state, "borja").winner());
        assertEquals(-1f, state.partialHandPercentage("borja"));

        state.apply(new TableVisualEvent.PartialHand(2, "borja", "COLOR",
                false, 63.25f));
        assertFalse(player(state, "borja").winner());
        assertEquals(63.25f, state.partialHandPercentage("borja"));

        state.apply(new TableVisualEvent.HandResult(3, "borja", "COLOR", true,
                TableSnapshot.Street.SHOWDOWN));
        assertEquals(null, state.partialHandPercentage("borja"));
    }

    @Test
    void partialHandPercentageUsesSwingsFixedLinear150MillisecondRoll() {
        long half = Math.round(CoronaPokerGdxTable.PARTIAL_HAND_ROLL_SECONDS
                * 500_000_000d);
        long complete = Math.round(CoronaPokerGdxTable.PARTIAL_HAND_ROLL_SECONDS
                * 1_000_000_000d);

        assertEquals(20f, CoronaPokerGdxTable.partialHandProbabilityValue(
                20f, 80f, 0L, true), 0.0001f);
        assertEquals(50f, CoronaPokerGdxTable.partialHandProbabilityValue(
                20f, 80f, half, true), 0.0001f);
        assertEquals(80f, CoronaPokerGdxTable.partialHandProbabilityValue(
                20f, 80f, complete, true), 0.0001f);
        assertEquals(80f, CoronaPokerGdxTable.partialHandProbabilityValue(
                20f, 80f, 0L, false), 0.0001f);
    }

    @Test
    void partialHandRejectsAnythingExceptPlaceholderOrARealPercentage() {
        assertThrows(IllegalArgumentException.class,
                () -> new TableVisualEvent.PartialHand(
                        1, "borja", "COLOR", true, -0.5f));
        assertThrows(IllegalArgumentException.class,
                () -> new TableVisualEvent.PartialHand(
                        1, "borja", "COLOR", true, 100.01f));
        assertThrows(IllegalArgumentException.class,
                () -> new TableVisualEvent.PartialHand(
                        1, "", "COLOR", true, 50f));
    }

    @Test
    void endBoundaryUsesTheDealersExactAuthoritativeSnapshot() {
        GdxTableViewState state = new GdxTableViewState(snapshot());
        state.apply(new TableVisualEvent.RevealHoleCards(1, "borja",
                card("A_C"), card("K_C"), "COLOR"));
        state.apply(new TableVisualEvent.HandResult(2, "borja", "COLOR", true,
                TableSnapshot.Street.SHOWDOWN));

        TableSnapshot.PlayerSnapshot neutralBoundaryPlayer
                = new TableSnapshot.PlayerSnapshot(
                        "borja", 1_075d, 0d, 0d, true, false, false, false,
                        18, 22, 1, 4_321L, false,
                        TableSnapshot.Position.BIG_BLIND, "", "", List.of());
        TableSnapshot end = new TableSnapshot(14L, "ana",
                TableSnapshot.Street.SHOWDOWN, 0d, "", false,
                List.of(player("ana", TableSnapshot.Position.DEALER),
                        neutralBoundaryPlayer),
                List.of(card("2_C"), card("3_C"), card("4_C"),
                        card("5_C"), card("9_D")));

        state.apply(new TableVisualEvent.HandBoundary(3, 1,
                TableVisualEvent.HandBoundary.Phase.END, end));

        assertEquals(end, state.snapshot());
        TableSnapshot.PlayerSnapshot borja = player(state, "borja");
        assertFalse(borja.winner());
        assertTrue(borja.handName().isBlank());
        assertTrue(borja.holeCards().isEmpty());
        assertTrue(state.hasHandResult("borja"));
        assertEquals("COLOR", state.resolvedHandName("borja"));
        assertEquals(Boolean.TRUE, state.resolvedHandWinner("borja"));
        assertEquals(List.of(card("A_C"), card("K_C")),
                state.presentedHoleCards("borja"),
                "an exact end snapshot must not visually cover an accepted showdown reveal");

        state.apply(new TableVisualEvent.HandBoundary(4, 2,
                TableVisualEvent.HandBoundary.Phase.PREPARE, snapshot()));
        assertTrue(state.presentedHoleCards("borja").isEmpty(),
                "the next hand, not END, owns reveal-presentation cleanup");
    }

    @Test
    void flopRevealUsesTheOriginalDemoCascadeTiming() {
        float flipSeconds = 0.620f;
        float leadInSeconds = 0.350f;

        assertEquals(0.350f,
                CoronaPokerGdxTable.communityRevealStartSeconds(
                        0, leadInSeconds), 0.000_001f);
        assertEquals(0.550f,
                CoronaPokerGdxTable.communityRevealStartSeconds(
                        1, leadInSeconds), 0.000_001f);
        assertEquals(0.750f,
                CoronaPokerGdxTable.communityRevealStartSeconds(
                        2, leadInSeconds), 0.000_001f);
        assertTrue(CoronaPokerGdxTable.communityRevealStartSeconds(
                1, leadInSeconds)
                < CoronaPokerGdxTable.communityRevealStartSeconds(
                        0, leadInSeconds) + flipSeconds);
        assertEquals(1.470f,
                CoronaPokerGdxTable.communityRevealAnimationSeconds(
                        3, leadInSeconds, flipSeconds), 0.000_001f);
    }

    @Test
    void callCostTracksItsStreetAnchorAndClearsAtTheNextHand() {
        GdxTableViewState state = new GdxTableViewState(snapshot());
        state.apply(new TableVisualEvent.CallCost(1, "+2.50", "borja"));

        assertEquals("+2.50", state.callCostText());
        assertEquals("borja", state.callCostAggressorNickname());

        state.apply(new TableVisualEvent.CallCost(2, "", ""));
        assertEquals("", state.callCostText());

        state.apply(new TableVisualEvent.CallCost(3, "+4", "borja"));
        state.apply(new TableVisualEvent.HandBoundary(4, 2,
                TableVisualEvent.HandBoundary.Phase.PREPARE, snapshot()));
        assertEquals("", state.callCostText());
        assertEquals("", state.callCostAggressorNickname());
    }

    @Test
    void lastHandIndicatorFollowsOnlyTheAuthoritativeEvent() {
        GdxTableViewState state = new GdxTableViewState(snapshot());

        state.apply(new TableVisualEvent.LastHandStatus(1, true));
        assertTrue(state.lastHand());

        state.apply(new TableVisualEvent.LastHandStatus(2, false));
        assertFalse(state.lastHand());
    }

    @Test
    void immediateRebuyIndicatorFollowsTheCanonicalDealerStatus() {
        GdxTableViewState state = new GdxTableViewState(snapshot());
        assertFalse(state.immediateRebuyEnabled("ana"));

        state.apply(new TableVisualEvent.ImmediateRebuyStatus(1, "ana", 10));
        assertTrue(state.immediateRebuyEnabled("ana"));

        state.apply(new TableVisualEvent.ImmediateRebuyStatus(2, "ana", 0));
        assertFalse(state.immediateRebuyEnabled("ana"));
    }

    @Test
    void confirmActionsUsesThePersistedSwingDefaultInsteadOfAnInventedCheck() {
        Properties properties = new Properties();
        assertTrue(!CoronaPokerGdxTable.booleanPreference(properties,
                "confirmar_todo", false));
        properties.setProperty("confirmar_todo", "true");
        assertTrue(CoronaPokerGdxTable.booleanPreference(properties,
                "confirmar_todo", false));
    }

    @Test
    void acceptsEveryStateEventFamilyThroughPayoutAndClose() {
        GdxTableViewState state = new GdxTableViewState(snapshot());
        state.apply(new TableVisualEvent.PlayerAction(2, "ana",
                TableVisualEvent.PlayerAction.ActionKind.CALL, "IGUALA",
                20, 10, 990d, 10d, 10d));
        state.apply(new TableVisualEvent.RevealHoleCards(3, "ana",
                card("A_C"), card("K_C")));
        state.apply(new TableVisualEvent.HandResult(4, "ana", "COLOR", true,
                TableSnapshot.Street.SHOWDOWN));
        state.apply(new TableVisualEvent.ShowdownHighlight(5, "ana", true,
                List.of(0, 1), List.of(0, 1, 2)));
        assertEquals(List.of(0, 1),
                state.showdownHighlight("ana").holeCardSlots());
        state.apply(new TableVisualEvent.Payout(
                6, "ana", 40, 0, 1_030d, 0d));
        state.apply(new TableVisualEvent.DeckChanged(7, "goliat"));
        state.apply(new TableVisualEvent.Cinematic(8,
                TableVisualEvent.Cinematic.Type.ALL_IN,
                TableVisualEvent.Cinematic.Phase.START, "ana",
                "rounders.gif", 1_000L));
        state.apply(new TableVisualEvent.SpecialCardSound(9, "A_C"));
        TableSnapshot beforeClose = state.snapshot();
        TableSnapshot terminal = new TableSnapshot(beforeClose.revision(),
                beforeClose.localNickname(), TableSnapshot.Street.FINISHED,
                beforeClose.pot(), "", beforeClose.paused(),
                beforeClose.players(), beforeClose.communityCards());
        state.apply(new TableVisualEvent.CloseTable(10,
                com.tonikelope.coronapoker.table.TableSessionSummary.empty(),
                TableSnapshot.Street.FINISHED));

        assertEquals(10, state.lastSequence());
        assertEquals(terminal, state.snapshot());
        assertEquals("COLOR", player(state, "ana").handName());
        assertTrue(player(state, "ana").winner());
    }

    @Test
    void communicationRulesAlwaysComeFromTheAuthoritativeTableEvent() {
        GdxTableViewState state = new GdxTableViewState(snapshot());
        assertFalse(state.textToSpeechEnabled());
        assertFalse(state.voiceMessagesEnabled());

        state.apply(new TableVisualEvent.CommunicationRulesStatus(
                1, false, true));
        assertFalse(state.textToSpeechEnabled());
        assertTrue(state.voiceMessagesEnabled());

        state.apply(new TableVisualEvent.CommunicationRulesStatus(
                2, true, false));
        assertTrue(state.textToSpeechEnabled());
        assertFalse(state.voiceMessagesEnabled());
    }

    @Test
    void gameClockConsumesTheOrderedRendererEvent() {
        GdxTableViewState state = new GdxTableViewState(snapshot());

        state.apply(new TableVisualEvent.GameClock(1, 3_661L));

        assertEquals(3_661L, state.playTimeSeconds());
        assertEquals(1L, state.lastSequence());
        assertEquals("01:01:01", CoronaPokerGdxTable.formatPlayTime(
                state.playTimeSeconds()));
    }

    @Test
    void runItTwiceLockAlwaysComesFromTheAuthoritativeTableEvent() {
        GdxTableViewState state = new GdxTableViewState(snapshot());
        assertTrue(state.runItTwiceLocked());

        state.apply(new TableVisualEvent.RunItTwiceLockStatus(1, true));
        assertTrue(state.runItTwiceLocked());

        state.apply(new TableVisualEvent.RunItTwiceLockStatus(2, false));
        assertFalse(state.runItTwiceLocked());
    }

    @Test
    void specialCardSoundRejectsUnsafeOrUnknownResourceNames() {
        assertEquals("10_D",
                new TableVisualEvent.SpecialCardSound(1, "10_D").cardCode());
        assertThrows(IllegalArgumentException.class,
                () -> new TableVisualEvent.SpecialCardSound(2, "../A_C"));
        assertThrows(IllegalArgumentException.class,
                () -> new TableVisualEvent.SpecialCardSound(3, "ACE_CLUBS"));
    }

    private static TableSnapshot.CardSnapshot card(String code) {
        return new TableSnapshot.CardSnapshot(code, true, false);
    }

    private static void assertColor(int red, int green, int blue, int alpha,
            Color actual) {
        assertEquals(red / 255f, actual.r, 0.000_001f);
        assertEquals(green / 255f, actual.g, 0.000_001f);
        assertEquals(blue / 255f, actual.b, 0.000_001f);
        assertEquals(alpha / 255f, actual.a, 0.000_001f);
    }

    private static TableSnapshot.PlayerSnapshot player(
            GdxTableViewState state, String nickname) {
        return state.snapshot().players().stream()
                .filter(player -> player.nickname().equals(nickname))
                .findFirst().orElseThrow();
    }

    private static TableSnapshot snapshot() {
        return snapshot(false, "");
    }

    private static TableSnapshot snapshotAt(TableSnapshot.Street street) {
        TableSnapshot base = snapshot();
        return new TableSnapshot(base.revision(), base.localNickname(), street,
                base.pot(), "", base.paused(), base.players(),
                base.communityCards());
    }

    private static TableSnapshot snapshot(boolean paused, String turn) {
        return new TableSnapshot(1L, "ana", TableSnapshot.Street.PREFLOP,
                0d, turn, paused, List.of(
                player("ana", TableSnapshot.Position.DEALER),
                player("borja", TableSnapshot.Position.BIG_BLIND)), List.of());
    }

    private static TableSnapshot.PlayerSnapshot player(String nickname,
            TableSnapshot.Position position) {
        return new TableSnapshot.PlayerSnapshot(nickname, 1_000d, 0d, 0d,
                true, false, false, false, -2, -2, 0, 0L,
                false, position, "", "", List.of());
    }

    private static TableSnapshot.PlayerSnapshot player(String nickname,
            boolean exited) {
        return new TableSnapshot.PlayerSnapshot(nickname, 1_000d, 0d, 0d,
                true, false, exited, false, -2, -2, 0, 0L,
                false, TableSnapshot.Position.NONE, "", "", List.of());
    }

    private static TableSnapshot.PlayerSnapshot showdownPlayer(
            String nickname, boolean winner) {
        return new TableSnapshot.PlayerSnapshot(nickname, 1_000d, 0d, 0d,
                true, false, false, false, -2, -2, 0, 0L,
                winner, TableSnapshot.Position.NONE, "", "PAIR", List.of());
    }

    private static TableSnapshot.PlayerSnapshot timedOutPlayer(
            String nickname) {
        return new TableSnapshot.PlayerSnapshot(nickname, 1_000d, 0d, 0d,
                true, false, false, true, -2, -2, 0, 0L,
                false, TableSnapshot.Position.NONE, "", "", List.of());
    }

    private static TableSnapshot snapshotWithPlayers(boolean paused,
            String turn, TableSnapshot.PlayerSnapshot... players) {
        return new TableSnapshot(1L, "ana", TableSnapshot.Street.PREFLOP,
                0d, turn, paused, List.of(players), List.of());
    }

    private static TableSnapshot.PlayerSnapshot playerWithAvailability(
            String nickname, boolean active, boolean spectator,
            boolean exited, boolean timedOut) {
        return new TableSnapshot.PlayerSnapshot(nickname, 1_000d, 0d, 0d,
                active, spectator, exited, timedOut, -2, -2, 0, 0L,
                false, TableSnapshot.Position.NONE, "", "", List.of());
    }
}
