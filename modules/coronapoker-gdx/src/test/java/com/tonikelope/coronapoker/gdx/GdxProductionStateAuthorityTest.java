/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.graphics.Color;
import com.tonikelope.coronapoker.core.LobbyParticipant;
import com.tonikelope.coronapoker.core.LobbySession;
import com.tonikelope.coronapoker.core.LobbySnapshot;
import com.tonikelope.coronapoker.core.PreferencesService;
import com.tonikelope.coronapoker.table.TableCommand;
import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GdxProductionStateAuthorityTest {

    private static final Set<String> FORBIDDEN_SCRIPTED_TABLE_FIELDS = Set.of(
            "ACTIONS", "DEMO_PLAYERS", "DEMO_HAND_RANKS",
            "DEMO_HAND_RESULTS", "communityHands", "holeCardHands",
            "flights", "thinkDurations", "thinkRandom", "HUD_ACTIONS",
            "cardBacks", "shuffleGifs");

    @Test
    void liveTableCannotBeConstructedWithoutAuthoritativeState() {
        assertThrows(NullPointerException.class,
                () -> new CoronaPokerGdxTable(240, null, command -> { },
                        () -> { }, new GdxGameLogSink(), null));
    }

    @Test
    void startupIntroIsTheOnlyStateLessTableMode() throws Exception {
        CoronaPokerGdxTable intro = new CoronaPokerGdxTable(240, () -> { });

        Field state = CoronaPokerGdxTable.class.getDeclaredField("liveState");
        state.setAccessible(true);
        Field startup = CoronaPokerGdxTable.class.getDeclaredField(
                "startupIntroOnly");
        startup.setAccessible(true);
        Field betAmount = CoronaPokerGdxTable.class.getDeclaredField(
                "liveBetAmount");
        betAmount.setAccessible(true);

        assertEquals(null, state.get(intro));
        assertTrue(startup.getBoolean(intro));
        assertEquals(0d, betAmount.getDouble(intro));
    }

    @Test
    void productTableContainsNoScriptedPokerTimelineState() {
        Set<String> fields = Arrays.stream(
                CoronaPokerGdxTable.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertTrue(FORBIDDEN_SCRIPTED_TABLE_FIELDS.stream()
                .noneMatch(fields::contains),
                () -> "Scripted table fields leaked into production: "
                        + FORBIDDEN_SCRIPTED_TABLE_FIELDS.stream()
                                .filter(fields::contains).toList());
    }

    @Test
    void liveProjectionNeverIntroducesAPlayerAbsentFromCoreSnapshot() {
        TableSnapshot input = new TableSnapshot(1L, "human",
                TableSnapshot.Street.PREFLOP, 0.3d, "remote", false,
                List.of(player("human"), player("remote")), List.of());
        GdxTableViewState projection = new GdxTableViewState(input);

        Set<String> expected = input.players().stream()
                .map(TableSnapshot.PlayerSnapshot::nickname)
                .collect(Collectors.toSet());
        Set<String> actual = projection.snapshot().players().stream()
                .map(TableSnapshot.PlayerSnapshot::nickname)
                .collect(Collectors.toSet());

        assertEquals(expected, actual);
        assertFalse(actual.isEmpty());
    }

    @Test
    void dealerFlightOriginSurvivesFoldAndDepartureUntilTheNextHand(
            @TempDir Path temporary) throws Exception {
        TableSnapshot.PlayerSnapshot smallBlind = player("human",
                TableSnapshot.Position.SMALL_BLIND);
        TableSnapshot.PlayerSnapshot bigBlind = player("remote",
                TableSnapshot.Position.BIG_BLIND);
        TableSnapshot initial = new TableSnapshot(1L, "human",
                TableSnapshot.Street.PREFLOP, 0d, "", false,
                List.of(smallBlind, bigBlind,
                        player("dealer", TableSnapshot.Position.DEALER)),
                List.of());
        GdxTableViewState projection = new GdxTableViewState(initial);
        CoronaPokerGdxTable table = nonAnimatedTable(temporary, projection,
                false);
        table.acceptEvent(new TableVisualEvent.HandBoundary(1L, 1L,
                TableVisualEvent.HandBoundary.Phase.PREPARE, initial),
                new CompletableFuture<>());
        placeSeat(table, "dealer", 1460f, 760f);
        placeSeat(table, "human", 960f, 200f);
        assertDealerSource(table, 1460f, 760f);

        table.acceptEvent(new TableVisualEvent.FoldHoleCards(2L, "dealer"),
                new CompletableFuture<>());
        assertFalse(player(projection, "dealer").active());
        assertDealerSource(table, 1460f, 760f);

        table.acceptEvent(new TableVisualEvent.PlayerDeparture(3L, "dealer",
                "SE VA"), new CompletableFuture<>());
        assertFalse(((Map<?, ?>) tableField("seatsByNickname").get(table))
                .containsKey("dealer"));
        assertDealerSource(table, 1460f, 760f);

        TableSnapshot.PlayerSnapshot departed = new TableSnapshot.PlayerSnapshot(
                "dealer", 10d, 0d, 0d, false, false, true, false,
                -1, -1, 0, 0L, false, TableSnapshot.Position.NONE,
                "", "", List.of());
        table.acceptEvent(new TableVisualEvent.SeatRoster(4L,
                List.of(smallBlind, bigBlind, departed)),
                new CompletableFuture<>());
        assertTrue(player(projection, "dealer").exited());
        table.acceptEvent(new TableVisualEvent.RunItTwiceBoard(5L,
                TableVisualEvent.RunItTwiceBoard.Side.B, "BOTE-B:", 0d,
                List.of(3, 4)), new CompletableFuture<>());
        placeSeat(table, "human", 960f, 200f);
        assertDealerSource(table, 1460f, 760f);

        TableSnapshot headsUp = new TableSnapshot(2L, "human",
                TableSnapshot.Street.PREFLOP, 0d, "", false,
                List.of(smallBlind, bigBlind), List.of());
        table.acceptEvent(new TableVisualEvent.HandBoundary(6L, 2L,
                TableVisualEvent.HandBoundary.Phase.PREPARE, headsUp),
                new CompletableFuture<>());
        assertDealerSource(table, 960f, 200f);
    }

    @Test
    void departureRemovesSeatAndRebuyWhileSurvivorStartsRelayout(
            @TempDir Path temporary) throws Exception {
        TableSnapshot initial = new TableSnapshot(1L, "human",
                TableSnapshot.Street.SHOWDOWN, 0d, "remote", false,
                List.of(player("human"), player("remote"), player("second"),
                        player("other")),
                List.of());
        GdxTableViewState projection = new GdxTableViewState(initial);
        CoronaPokerGdxTable table = animatedTable(temporary, projection);
        table.acceptEvent(new TableVisualEvent.PreparationStatus(1L,
                TableVisualEvent.PreparationStatus.Phase.READY),
                new CompletableFuture<>());
        tableField("creationComplete").setBoolean(table, true);
        table.acceptEvent(new TableVisualEvent.RebuyDecision(2L, "remote",
                TableVisualEvent.RebuyDecision.Phase.WAITING),
                new CompletableFuture<>());
        assertTrue(table.hasRemoteRebuyPresentation("remote"));
        placeSeat(table, "other", 1400f, 760f);

        table.acceptEvent(new TableVisualEvent.PlayerDeparture(3L, "remote",
                "SE VA"), new CompletableFuture<>());
        table.acceptEvent(new TableVisualEvent.PlayerDeparture(4L, "second",
                "SE VA"), new CompletableFuture<>());
        table.acceptEvent(new TableVisualEvent.PlayerTimeout(5L, "remote", true),
                new CompletableFuture<>());

        Map<?, ?> seats = (Map<?, ?>) tableField("seatsByNickname").get(table);
        assertEquals(Set.of("human", "other"), seats.keySet());
        Object survivor = seats.get("other");
        Field relayout = survivor.getClass().getDeclaredField("relayoutStartedAt");
        relayout.setAccessible(true);
        assertFalse(Float.isNaN(relayout.getFloat(survivor)));
        Field relayoutFromX = survivor.getClass().getDeclaredField(
                "relayoutFromX");
        relayoutFromX.setAccessible(true);
        assertEquals(1400f, relayoutFromX.getFloat(survivor));
        assertTrue(projection.snapshot().currentTurnNickname().isBlank());
        assertFalse(player(projection, "remote").timedOut());
        assertNull(projection.rebuyDecision("remote"));
        assertFalse(table.hasRemoteRebuyPresentation("remote"));
    }

    @Test
    void kickConfirmationExpiresOnReconnectAndOnDeparture(
            @TempDir Path temporary) {
        TableSnapshot initial = new TableSnapshot(1L, "human",
                TableSnapshot.Street.PREFLOP, 0d, "remote", false,
                List.of(player("human"), player("remote")), List.of());
        LobbySession lobby = new LobbySession(new LobbySnapshot("human",
                "human", "127.0.0.1", true, LobbySnapshot.Phase.IN_GAME, "",
                List.of(new LobbyParticipant("human", null, true, true,
                        false, true, false, true, 0, 0),
                        new LobbyParticipant("remote", null, false, false,
                                false, false, false, true, 0, 0)),
                List.of(), null, false),
                command -> CompletableFuture.completedFuture(null));
        List<TableCommand> submitted = new ArrayList<>();
        CoronaPokerGdxTable table = new CoronaPokerGdxTable(240,
                new GdxTableViewState(initial), submitted::add, () -> { },
                new GdxGameLogSink(), new PreferencesService(
                        temporary.resolve("coronapoker.properties")),
                lobby, null);
        table.acceptEvent(new TableVisualEvent.PlayerTimeout(1L, "remote", true),
                new CompletableFuture<>());
        GdxTableDialog recovered = table.requestKickTimedOutPlayer("remote");
        assertNotNull(recovered);
        table.acceptEvent(new TableVisualEvent.PlayerTimeout(2L, "remote", false),
                new CompletableFuture<>());
        assertFalse(recovered.result().toCompletableFuture().join());

        table.acceptEvent(new TableVisualEvent.PlayerTimeout(3L, "remote", true),
                new CompletableFuture<>());
        recovered.accept();
        assertTrue(submitted.isEmpty());
        GdxTableDialog departed = table.requestKickTimedOutPlayer("remote");
        assertNotNull(departed);
        table.acceptEvent(new TableVisualEvent.PlayerDeparture(4L, "remote",
                "SE VA"), new CompletableFuture<>());
        assertFalse(departed.result().toCompletableFuture().join());
        assertNull(table.requestKickTimedOutPlayer("remote"));
    }

    @Test
    void latencyPresentationDistinguishesPendingAndFailedHeartbeats() {
        var input = new TableSnapshot(1L, "human",
                TableSnapshot.Street.WAITING, 0d, "", false,
                List.of(player("human"), player("remote")), List.of());
        var projection = new GdxTableViewState(input);
        long now = System.currentTimeMillis();
        int pending = LobbyParticipant.NO_LATENCY;
        int red = 0xf44336ff;
        projection.apply(new TableVisualEvent.TelemetryStatus(1L, List.of(
                new TableVisualEvent.PlayerTelemetry("remote", pending,
                        pending, 0, now))));
        assertNotEquals(red, Color.rgba8888(CoronaPokerGdxTable.latencyColor(
                player(projection, "remote"))));
        projection.apply(new TableVisualEvent.TelemetryStatus(2L, List.of(
                new TableVisualEvent.PlayerTelemetry("remote", -1,
                        pending, 0, now))));
        assertEquals(red, Color.rgba8888(CoronaPokerGdxTable.latencyColor(
                player(projection, "remote"))));
    }

    @Test
    void hotJoinUsesTheFoldedDealerAndRefreshesReplacedSeatGeometry(
            @TempDir Path temporary) throws Exception {
        TableSnapshot.PlayerSnapshot local = player("human",
                TableSnapshot.Position.SMALL_BLIND);
        TableSnapshot.PlayerSnapshot bigBlind = player("remote",
                TableSnapshot.Position.BIG_BLIND);
        TableSnapshot initial = new TableSnapshot(1L, "human",
                TableSnapshot.Street.PREFLOP, 0d, "", false,
                List.of(local, bigBlind,
                        player("previous", TableSnapshot.Position.DEALER)),
                List.of());
        CoronaPokerGdxTable table = nonAnimatedTable(temporary,
                new GdxTableViewState(initial), false);
        table.acceptEvent(new TableVisualEvent.HandBoundary(1L, 1L,
                TableVisualEvent.HandBoundary.Phase.PREPARE, initial),
                new CompletableFuture<>());
        placeSeat(table, "previous", 330f, 740f);
        assertDealerSource(table, 330f, 740f);
        Field cachedLayout = tableField("cachedSeatLayoutValid");
        cachedLayout.setBoolean(table, true);

        TableSnapshot host = new TableSnapshot(2L, "human",
                TableSnapshot.Street.FLOP, 0d, "", false,
                List.of(local, bigBlind, player("current",
                        TableSnapshot.Position.DEALER_STRADDLE)), List.of());
        GdxTableViewState hostProjection = new GdxTableViewState(host);
        hostProjection.apply(new TableVisualEvent.FoldHoleCards(1L,
                "current"));
        table.acceptEvent(new TableVisualEvent.HotJoinState(2L,
                hostProjection.snapshot()), new CompletableFuture<>());
        assertFalse(cachedLayout.getBoolean(table));
        placeSeat(table, "human", 960f, 200f);
        placeSeat(table, "current", 1490f, 740f);
        assertDealerSource(table, 1490f, 740f);
    }

    @Test
    void communityDealChangesOnlyAtTheNextHandAfterSaving(
            @TempDir Path temporary) {
        TableSnapshot input = new TableSnapshot(1L, "human",
                TableSnapshot.Street.PREFLOP, 0d, "", false,
                List.of(player("human"), player("remote")), List.of());
        GdxTableViewState projection = new GdxTableViewState(input);
        PreferencesService preferences = new PreferencesService(
                temporary.resolve("coronapoker.properties"));
        preferences.properties().setProperty("animaciones", "false");
        GdxGamePresentationSettings settings =
                new GdxGamePresentationSettings(preferences);
        var choice = GdxAppearanceOptions.COMMUNITY_DEAL_STYLE;
        assertTrue(settings.savedCommunityDealByStreet());
        assertEquals("POR CALLES", GdxAppearanceOptions.selectedLabel(choice,
                preferences.properties(), new GdxGameText("es")));
        preferences.properties().setProperty(choice.key(), "invalid");
        settings.commitCommunityDealStyle();
        assertTrue(settings.savedCommunityDealByStreet());
        preferences.properties().setProperty(choice.key(), "covered");
        settings.commitCommunityDealStyle();
        assertFalse(settings.savedCommunityDealByStreet());
        assertEquals("TAPADAS AL REPARTIR LA MANO",
                GdxAppearanceOptions.selectedLabel(choice,
                        preferences.properties(), new GdxGameText("es")));
        CoronaPokerGdxTable table = new CoronaPokerGdxTable(240, projection,
                command -> { }, () -> { }, new GdxGameLogSink(), preferences,
                null, settings);
        table.acceptEvent(new TableVisualEvent.HandBoundary(1L, 1L,
                TableVisualEvent.HandBoundary.Phase.PREPARE, input),
                new CompletableFuture<>());
        assertTrue(acceptBoardDeal(table, 2L, 0).isDone());
        assertTrue(table.hasRestingCommunityCards(1, false));

        preferences.properties().setProperty(
                GdxSettingsContract.COMMUNITY_DEAL_STYLE_KEY, "streets");
        settings.commitCommunityDealStyle();
        assertTrue(acceptBoardDeal(table, 3L, 1).isDone());
        assertTrue(table.hasRestingCommunityCards(2, false));

        table.acceptEvent(new TableVisualEvent.HandBoundary(4L, 2L,
                TableVisualEvent.HandBoundary.Phase.PREPARE, input),
                new CompletableFuture<>());
        assertTrue(acceptBoardDeal(table, 5L, 0).isDone());
        assertFalse(table.hasRestingCommunityCards(1, false));
        assertTrue(projection.snapshot().communityCards().get(0).visible());

        preferences.properties().setProperty(
                GdxSettingsContract.COMMUNITY_DEAL_STYLE_KEY, "covered");
        table.acceptEvent(new TableVisualEvent.HandBoundary(6L, 3L,
                TableVisualEvent.HandBoundary.Phase.PREPARE, input),
                new CompletableFuture<>());
        assertTrue(acceptBoardDeal(table, 7L, 0).isDone());
        assertFalse(table.hasRestingCommunityCards(1, false),
                "an unsaved selector edit must not affect a new hand");

        settings.commitCommunityDealStyle();
        table.acceptEvent(new TableVisualEvent.HandBoundary(8L, 4L,
                TableVisualEvent.HandBoundary.Phase.PREPARE, input),
                new CompletableFuture<>());
        assertTrue(acceptBoardDeal(table, 9L, 0).isDone());
        assertTrue(table.hasRestingCommunityCards(1, false));
    }

    @Test
    void streetDealKeepsRevealBarriersAndHandlesRedealAndHotJoin(
            @TempDir Path temporary) {
        TableSnapshot input = new TableSnapshot(1L, "human",
                TableSnapshot.Street.PREFLOP, 0d, "", false,
                List.of(player("human"), player("remote")), List.of());
        GdxTableViewState projection = new GdxTableViewState(input);
        PreferencesService preferences = new PreferencesService(
                temporary.resolve("coronapoker.properties"));
        preferences.properties().setProperty("sonido_efectos", "false");
        CoronaPokerGdxTable table = new CoronaPokerGdxTable(240, projection,
                command -> { }, () -> { }, new GdxGameLogSink(), preferences,
                null, new GdxGamePresentationSettings(preferences));
        for (int slot = 0; slot < 5; slot++) {
            assertTrue(acceptBoardDeal(table, slot + 1L, slot).isDone());
            assertFalse(table.hasRestingCommunityCards(slot, 1, false));
        }

        List<TableSnapshot.CardSnapshot> flop = List.of(card("A_C", true),
                card("K_D", true), card("J_P", true));
        CompletableFuture<Void> spread = new CompletableFuture<>();
        table.acceptEvent(new TableVisualEvent.RevealCommunityCards(
                6L, 0, flop, 1000L), spread);
        assertEquals(flop, projection.snapshot().communityCards().subList(0, 3));
        assertFalse(table.hasRestingCommunityCards(3, true));
        table.updateLiveCommunityReveal(1_000_000_000L);
        table.updateLiveCommunityReveal(1_875_000_000L);
        assertFalse(spread.isDone());
        assertFalse(table.hasRestingCommunityCards(3, true));
        table.updateLiveCommunityReveal(2_310_000_000L);
        assertFalse(spread.isDone());
        table.updateLiveCommunityReveal(2_920_000_000L);
        assertFalse(spread.isDone());
        table.updateLiveCommunityReveal(3_250_000_000L);
        assertFalse(spread.isDone());
        table.updateLiveCommunityReveal(3_350_000_000L);
        assertTrue(spread.isDone());
        assertTrue(table.hasRestingCommunityCards(3, true));

        CompletableFuture<Void> turn = new CompletableFuture<>();
        TableSnapshot.CardSnapshot turnCard = card("5_T", true);
        table.acceptEvent(new TableVisualEvent.RevealCommunityCards(
                7L, 3, List.of(turnCard), 1000L), turn);
        table.updateLiveCommunityReveal(4_000_000_000L);
        table.updateLiveCommunityReveal(4_900_000_000L);
        assertFalse(turn.isDone());
        assertFalse(table.hasRestingCommunityCards(3, 1, true));
        table.updateLiveCommunityReveal(5_900_000_000L);
        assertTrue(turn.isDone());
        assertTrue(table.hasRestingCommunityCards(4, true));

        table.acceptEvent(new TableVisualEvent.RunItTwiceBoard(8L,
                TableVisualEvent.RunItTwiceBoard.Side.B, "BOTE-B:", 0d,
                List.of(3, 4)), new CompletableFuture<>());
        assertTrue(acceptBoardDeal(table, 9L, 3).isDone());
        assertTrue(acceptBoardDeal(table, 10L, 4).isDone());
        assertTrue(table.hasRestingCommunityCards(3, true));
        assertFalse(table.hasRestingCommunityCards(3, 1, false));

        CompletableFuture<Void> pending = new CompletableFuture<>();
        table.acceptEvent(new TableVisualEvent.RevealCommunityCards(
                11L, 3, List.of(turnCard), 2000L), pending);
        assertFalse(pending.isDone());
        TableSnapshot snapshot = projection.snapshot();
        table.acceptEvent(new TableVisualEvent.HotJoinState(12L, snapshot),
                new CompletableFuture<>());
        assertTrue(pending.isDone(), "bootstrap releases the old visual barrier");
        assertTrue(table.hasRestingCommunityCards(4, true));
        assertFalse(table.hasRestingCommunityCards(4, 1, false));

        table.acceptEvent(new TableVisualEvent.RabbitCards(13L,
                List.of(new TableVisualEvent.RabbitCard(4, card("", false))),
                true), new CompletableFuture<>());
        assertTrue(table.hasRestingCommunityCards(4, 1, false),
                "an offered Rabbit card must still appear and accept input");
    }

    private static CompletableFuture<Void> acceptBoardDeal(
            CoronaPokerGdxTable table, long sequence, int slot) {
        CompletableFuture<Void> barrier = new CompletableFuture<>();
        table.acceptEvent(new TableVisualEvent.DealCommunityCard(sequence, slot),
                barrier);
        return barrier;
    }

    @Test
    void animatedCardEventsCommitBeforeLaterIndependentEventsCanOvertakeThem(
            @TempDir Path temporary) {
        TableSnapshot input = new TableSnapshot(1L, "human",
                TableSnapshot.Street.PREFLOP, 0.3d, "remote", false,
                List.of(player("human"), player("remote")), List.of());
        GdxTableViewState projection = new GdxTableViewState(input);
        PreferencesService preferences = new PreferencesService(
                temporary.resolve("coronapoker.properties"));
        preferences.properties().setProperty("sonido_efectos", "false");
        preferences.properties().setProperty("animacion_reparto", "true");
        preferences.properties().setProperty("animacion_destape", "true");
        preferences.properties().setProperty(
                GdxSettingsContract.COMMUNITY_DEAL_STYLE_KEY, "covered");
        CoronaPokerGdxTable table = new CoronaPokerGdxTable(240, projection,
                command -> { }, () -> { }, new GdxGameLogSink(), preferences,
                null, new GdxGamePresentationSettings(preferences));

        CompletableFuture<Void> ownDeal = new CompletableFuture<>();
        table.acceptEvent(new TableVisualEvent.DealHoleCard(1L, "human", 0,
                card("A_C", true)), ownDeal);
        assertFalse(ownDeal.isDone(),
                "the visual flight must remain animated");
        table.acceptEvent(telemetry(2L), new CompletableFuture<>());
        assertEquals("A_C", player(projection, "human")
                .holeCards().get(0).code());

        CompletableFuture<Void> rivalDeal = new CompletableFuture<>();
        table.acceptEvent(new TableVisualEvent.DealHoleCard(3L, "remote", 0,
                card("", false)), rivalDeal);
        table.acceptEvent(telemetry(4L), new CompletableFuture<>());
        assertFalse(rivalDeal.isDone());
        assertEquals(1, player(projection, "remote").holeCards().size());

        CompletableFuture<Void> boardDeal = new CompletableFuture<>();
        table.acceptEvent(new TableVisualEvent.DealCommunityCard(5L, 0),
                boardDeal);
        table.acceptEvent(telemetry(6L), new CompletableFuture<>());
        assertFalse(boardDeal.isDone());
        assertEquals(1, projection.snapshot().communityCards().size());

        CompletableFuture<Void> boardReveal = new CompletableFuture<>();
        table.acceptEvent(new TableVisualEvent.RevealCommunityCards(7L, 0,
                List.of(card("K_P", true), card("Q_D", true),
                        card("J_T", true))), boardReveal);
        table.acceptEvent(telemetry(8L), new CompletableFuture<>());
        assertFalse(boardReveal.isDone());
        assertEquals(List.of("K_P", "Q_D", "J_T"),
                projection.snapshot().communityCards().stream()
                        .limit(3).map(TableSnapshot.CardSnapshot::code).toList());

        CompletableFuture<Void> rivalReveal = new CompletableFuture<>();
        table.acceptEvent(new TableVisualEvent.RevealHoleCards(9L, "remote",
                card("10_C", true), card("9_C", true)), rivalReveal);
        table.acceptEvent(telemetry(10L), new CompletableFuture<>());
        assertFalse(rivalReveal.isDone());
        assertEquals(List.of("10_C", "9_C"), player(projection, "remote")
                .holeCards().stream().map(TableSnapshot.CardSnapshot::code)
                .toList());
        assertEquals(10L, projection.lastSequence());
    }

    @Test
    void productTableConsumesHotJoinAsAnAuthoritativePausedSceneCutover(
            @TempDir Path temporary) {
        TableSnapshot initial = new TableSnapshot(1L, "human",
                TableSnapshot.Street.PREFLOP, 0d, "", false,
                List.of(player("human"), player("remote")), List.of());
        TableSnapshot.CardSnapshot back = new TableSnapshot.CardSnapshot(
                "", false, false, true);
        TableSnapshot.PlayerSnapshot warming = new TableSnapshot.PlayerSnapshot(
                "human", 10d, 0d, 0d, false, true, false, false,
                -1, -1, 0, 0L, false, false, TableSnapshot.Position.NONE,
                "CALENTANDO", "", List.of(), 10, 0, true);
        TableSnapshot.PlayerSnapshot remote = new TableSnapshot.PlayerSnapshot(
                "remote", 9d, 1d, 1d, true, false, false, false,
                -1, -1, 0, 0L, false, false,
                TableSnapshot.Position.BIG_BLIND, "VA", "",
                List.of(back, back));
        TableSnapshot bootstrap = new TableSnapshot(2L, "human",
                TableSnapshot.Street.FLOP, 2d, "remote", true,
                List.of(warming, remote), List.of(card("A_C", true),
                        card("K_D", true), card("Q_T", true), back, back));
        GdxTableViewState projection = new GdxTableViewState(initial);
        CoronaPokerGdxTable table = nonAnimatedTable(temporary, projection,
                false);
        assertTrue(table.hasActivePreparationOverlay());

        CompletableFuture<Void> applied = new CompletableFuture<>();
        table.acceptEvent(new TableVisualEvent.HotJoinState(1L, bootstrap),
                applied);

        assertTrue(applied.isDone());
        assertFalse(table.hasActivePreparationOverlay(),
                "the authoritative hot-join snapshot must dismiss PREPARANDO CRUPIER");
        table.acceptEvent(new TableVisualEvent.PreparationStatus(2L,
                TableVisualEvent.PreparationStatus.Phase.STARTING_DEALER),
                new CompletableFuture<>());
        assertFalse(table.hasActivePreparationOverlay(),
                "a delayed provisional phase must not block a live hot-join table again");
        assertTrue(projection.snapshot().paused());
        assertEquals(TableSnapshot.Street.FLOP,
                projection.snapshot().street());
        assertEquals(List.of("A_C", "K_D", "Q_T"),
                projection.snapshot().communityCards().subList(0, 3).stream()
                        .map(TableSnapshot.CardSnapshot::code).toList());
        assertEquals(2, projection.presentedHoleCards("remote").size());
        assertTrue(projection.presentedHoleCards("remote").stream()
                .allMatch(value -> value.visible() && !value.faceUp()
                && value.code().isBlank()));
    }

    @Test
    void hotJoinHydratesAnAlreadyRunningRemoteGameOverCountdown(
            @TempDir Path temporary) {
        TableSnapshot initial = new TableSnapshot(1L, "human",
                TableSnapshot.Street.PREFLOP, 0d, "", false,
                List.of(player("human"), player("host")), List.of());
        TableSnapshot.PlayerPresentation waiting
                = new TableSnapshot.PlayerPresentation(false, false, -1f,
                        false, "", List.of(), false, false, List.of(),
                        List.of(), TableSnapshot.RebuyPhase.WAITING, 0, "");
        TableSnapshot.PlayerSnapshot host = new TableSnapshot.PlayerSnapshot(
                "host", 0d, 0d, 0d, false, false, false, false,
                -1, -1, 0, 0L, false, false, TableSnapshot.Position.NONE,
                TableSnapshot.Decision.NONE, TableSnapshot.ActionKind.NONE,
                "", "", List.of(), 10, 0, false, waiting);
        TableSnapshot bootstrap = new TableSnapshot(2L, "human",
                TableSnapshot.Street.SHOWDOWN, 0d, "", false,
                List.of(player("human"), host), List.of());
        GdxTableViewState projection = new GdxTableViewState(initial);
        CoronaPokerGdxTable table = nonAnimatedTable(temporary, projection,
                false);

        table.acceptEvent(new TableVisualEvent.HotJoinState(1L, bootstrap),
                new CompletableFuture<>());

        assertTrue(table.hasRemoteRebuyPresentation("host"));
        table.acceptEvent(new TableVisualEvent.RebuyDecision(2L, "host",
                TableVisualEvent.RebuyDecision.Phase.REBOUGHT),
                new CompletableFuture<>());
        assertFalse(table.hasRemoteRebuyPresentation("host"));
    }

    @Test
    void hotJoinCutoverKeepsOrdinaryCardAndChipAnimationsEnabled(
            @TempDir Path temporary) {
        assertHotJoinAnimatedEvent(temporary.resolve("turn"),
                new TableVisualEvent.RevealCommunityCards(2L, 3,
                        List.of(card("J_P", true))));
        assertHotJoinAnimatedEvent(temporary.resolve("chips"),
                new TableVisualEvent.CollectBets(2L, List.of(
                        new TableVisualEvent.ChipTransfer("remote", 1d,
                                8d, 0d, 1d)), 2d, 3d));
    }

    @Test
    void animatedMoneyAndPositionEventsCommitBeforeTelemetryCanOvertakeThem(
            @TempDir Path temporary) {
        assertAnimatedEventCommitsBeforeTelemetry(temporary.resolve("position"),
                new TableVisualEvent.PositionRotation(1L, List.of(
                        new TableVisualEvent.PositionTransfer("remote", "human",
                                TableSnapshot.Position.DEALER, true)), 240L));
        assertAnimatedEventCommitsBeforeTelemetry(temporary.resolve("collect"),
                new TableVisualEvent.CollectBets(1L, List.of(
                        new TableVisualEvent.ChipTransfer("remote", 1d, 9d,
                                0d, 1d)), 0.3d, 1.3d));
        assertAnimatedEventCommitsBeforeTelemetry(temporary.resolve("payout"),
                new TableVisualEvent.Payout(1L, "human", 0.3d, 0,
                        10.3d, 0d));
        assertAnimatedEventCommitsBeforeTelemetry(temporary.resolve("rebuy"),
                new TableVisualEvent.Rebuy(1L, List.of(
                        new TableVisualEvent.ChipTransfer("human", 2d, 12d,
                                0d, 0d)), 240L));

        TableSnapshot input = new TableSnapshot(1L, "human",
                TableSnapshot.Street.PREFLOP, 0.3d, "remote", false,
                List.of(player("human"), player("remote")), List.of());
        GdxTableViewState projection = new GdxTableViewState(input);
        CoronaPokerGdxTable table = animatedTable(
                temporary.resolve("pending-collect"), projection);
        table.acceptEvent(new TableVisualEvent.PlayerAction(1L, "remote",
                TableVisualEvent.PlayerAction.ActionKind.CALL, "CALL", 1d,
                1d, 9d, 1d, 1d), new CompletableFuture<>());
        CompletableFuture<Void> collection = new CompletableFuture<>();
        table.acceptEvent(new TableVisualEvent.CollectBets(2L, List.of(
                new TableVisualEvent.ChipTransfer("remote", 1d, 9d, 0d,
                        1d)), 0.3d, 1.3d), collection);
        assertFalse(collection.isDone());
        table.acceptEvent(telemetry(3L), new CompletableFuture<>());
        assertEquals(3L, projection.lastSequence());
    }

    @Test
    void disabledChipFlightsStillUpdateAndSettleTheCentralPot(
            @TempDir Path temporary) throws Exception {
        TableSnapshot input = new TableSnapshot(1L, "human",
                TableSnapshot.Street.PREFLOP, 0.3d, "remote", false,
                List.of(player("human"), player("remote")), List.of());
        GdxTableViewState projection = new GdxTableViewState(input);
        CoronaPokerGdxTable table = nonAnimatedTable(temporary, projection,
                true);

        CompletableFuture<Void> action = new CompletableFuture<>();
        table.acceptEvent(new TableVisualEvent.PlayerAction(1L, "remote",
                TableVisualEvent.PlayerAction.ActionKind.CALL, "CALL", 1d,
                1d, 9d, 1d, 1d), action);

        assertTrue(action.isDone());
        assertEquals(1.3d, displayedPot(table), 0.000_001d,
                "without a flight the accepted contribution must land now");

        CompletableFuture<Void> payout = new CompletableFuture<>();
        table.acceptEvent(new TableVisualEvent.Payout(2L, "human", 1.3d, 0,
                11.3d, 0d), payout);

        assertTrue(payout.isDone());
        assertEquals(0d, displayedPot(table), 0.000_001d,
                "canonical payout remainder must replace cached deltas");
        assertEquals(11.3d, player(projection, "human").stack(), 0.000_001d);
    }

    @Test
    void spectatorRebuyThenAllInKeepsThePhysicalFourColourRack(
            @TempDir Path temporary) throws Exception {
        TableSnapshot.PlayerSnapshot human = playerState("human", 10d,
                true, false, 10, 0);
        TableSnapshot.PlayerSnapshot remote = player("remote");
        TableSnapshot initial = new TableSnapshot(1L, "human",
                TableSnapshot.Street.PREFLOP, 0d, "", false,
                List.of(human, remote), List.of());
        GdxTableViewState projection = new GdxTableViewState(initial);
        CoronaPokerGdxTable table = nonAnimatedTable(temporary, projection,
                true);

        table.acceptEvent(new TableVisualEvent.SeatRoster(1L,
                List.of(human, remote)), new CompletableFuture<>());
        TableSnapshot.PlayerSnapshot busted = playerState("human", 0d,
                false, true, 10, 0);
        table.acceptEvent(new TableVisualEvent.SeatRoster(2L,
                List.of(busted, remote)), new CompletableFuture<>());
        table.acceptEvent(new TableVisualEvent.Rebuy(3L, List.of(
                new TableVisualEvent.ChipTransfer("human", 10d, 10d,
                        0d, 0d)), 240L), new CompletableFuture<>());
        TableSnapshot.PlayerSnapshot returned = playerState("human", 10d,
                true, false, 20, 1);
        table.acceptEvent(new TableVisualEvent.SeatRoster(4L,
                List.of(returned, remote)), new CompletableFuture<>());
        table.acceptEvent(new TableVisualEvent.PlayerAction(5L, "human",
                TableVisualEvent.PlayerAction.ActionKind.ALL_IN, "ALL IN",
                10d, 10d, 0d, 10d, 10d), new CompletableFuture<>());

        List<CoronaPokerGdxTable.VisualChipSpec> pot = potChips(table);
        assertEquals(1_000L, CoronaPokerGdxTable.visualChipAmountMinor(pot));
        assertEquals(4L, pot.stream().map(
                CoronaPokerGdxTable.VisualChipSpec::color).distinct().count(),
                "the rebuy pack must reach both the total-bet rack and pot without becoming all green");
    }

    @Test
    void runItTwiceDisplaysAndPaysAnIndependentExactRackPerBoard(
            @TempDir Path temporary) throws Exception {
        TableSnapshot input = new TableSnapshot(1L, "human",
                TableSnapshot.Street.RIVER, 30.6d, "", false,
                List.of(player("human"), player("remote")), List.of());
        GdxTableViewState projection = new GdxTableViewState(input);
        CoronaPokerGdxTable table = nonAnimatedTable(temporary, projection,
                true);
        Field denomination = CoronaPokerGdxTable.class.getDeclaredField(
                "visualChipSmallBlind");
        denomination.setAccessible(true);
        denomination.setDouble(table, 0.1d);
        GdxPotChipInventory inventory = potInventory(table);
        inventory.clear();
        for (int chip = 0; chip < 30; chip++) {
            inventory.deposit(List.of(new CoronaPokerGdxTable.VisualChipSpec(
                    1d, GdxChipDenominations.color(3))));
        }
        for (int chip = 0; chip < 3; chip++) {
            inventory.deposit(List.of(new CoronaPokerGdxTable.VisualChipSpec(
                    0.2d, GdxChipDenominations.color(1))));
        }

        table.acceptEvent(new TableVisualEvent.RunItTwiceBoard(1L,
                TableVisualEvent.RunItTwiceBoard.Side.A, "BOTE-A:",
                15.3d, List.of()), new CompletableFuture<>());
        List<CoronaPokerGdxTable.VisualChipSpec> sideA = potChips(table);
        assertEquals(1_530L,
                CoronaPokerGdxTable.visualChipAmountMinor(sideA));
        assertEquals(4L, sideA.stream().map(
                CoronaPokerGdxTable.VisualChipSpec::color).distinct().count());

        table.acceptEvent(new TableVisualEvent.PayoutBatch(2L, List.of(
                new TableVisualEvent.PayoutBatch.Transfer("human", 15.3d,
                        0d, 25.3d)), 0d, 0d), new CompletableFuture<>());
        assertTrue(potChips(table).isEmpty());

        table.acceptEvent(new TableVisualEvent.RunItTwiceBoard(3L,
                TableVisualEvent.RunItTwiceBoard.Side.B, "BOTE-B:",
                15.3d, List.of(3, 4)), new CompletableFuture<>());
        List<CoronaPokerGdxTable.VisualChipSpec> sideB = potChips(table);
        assertEquals(1_530L,
                CoronaPokerGdxTable.visualChipAmountMinor(sideB));
        assertEquals(4L, sideB.stream().map(
                CoronaPokerGdxTable.VisualChipSpec::color).distinct().count());
        assertEquals(CoronaPokerGdxTable.visualChipLayout(sideA).columns()
                .stream().map(GdxStackChipLayout.Column::chips)
                .map(List::size).toList(),
                CoronaPokerGdxTable.visualChipLayout(sideB).columns()
                        .stream().map(GdxStackChipLayout.Column::chips)
                        .map(List::size).toList());
    }

    @Test
    void globalAnimationMasterUsesImmediateStatePathsForTableEvents(
            @TempDir Path temporary) throws Exception {
        TableSnapshot input = new TableSnapshot(1L, "human",
                TableSnapshot.Street.PREFLOP, 0.3d, "remote", false,
                List.of(player("human"), player("remote")), List.of());
        GdxTableViewState projection = new GdxTableViewState(input);
        CoronaPokerGdxTable table = nonAnimatedTable(temporary, projection,
                false);

        CompletableFuture<Void> action = new CompletableFuture<>();
        table.acceptEvent(new TableVisualEvent.PlayerAction(1L, "remote",
                TableVisualEvent.PlayerAction.ActionKind.CALL, "CALL", 1d,
                1d, 9d, 1d, 1d), action);
        CompletableFuture<Void> collection = new CompletableFuture<>();
        table.acceptEvent(new TableVisualEvent.CollectBets(2L, List.of(
                new TableVisualEvent.ChipTransfer("remote", 1d, 9d, 0d,
                        1d)), 0.3d, 1.3d), collection);
        CompletableFuture<Void> deal = new CompletableFuture<>();
        table.acceptEvent(new TableVisualEvent.DealHoleCard(3L, "human", 0,
                card("A_C", true)), deal);
        CompletableFuture<Void> runout = new CompletableFuture<>();
        table.acceptEvent(new TableVisualEvent.AllInRunoutPause(4L, 800L),
                runout);

        assertTrue(action.isDone());
        assertTrue(collection.isDone());
        assertTrue(deal.isDone());
        assertTrue(runout.isDone());
        assertEquals(1.3d, displayedPot(table), 0.000_001d);
        assertEquals("A_C", player(projection, "human")
                .holeCards().get(0).code());
        assertEquals(4L, projection.lastSequence());
    }

    private static void assertAnimatedEventCommitsBeforeTelemetry(Path file,
            TableVisualEvent animatedEvent) {
        TableSnapshot input = new TableSnapshot(1L, "human",
                TableSnapshot.Street.PREFLOP, 0.3d, "remote", false,
                List.of(player("human"), player("remote")), List.of());
        GdxTableViewState projection = new GdxTableViewState(input);
        CoronaPokerGdxTable table = animatedTable(file, projection);

        CompletableFuture<Void> visualBarrier = new CompletableFuture<>();
        table.acceptEvent(animatedEvent, visualBarrier);
        assertFalse(visualBarrier.isDone(),
                "the real presentation barrier must remain in flight");
        table.acceptEvent(telemetry(2L), new CompletableFuture<>());
        assertEquals(2L, projection.lastSequence(),
                "telemetry must not overtake an uncommitted visual event");
    }

    private static void assertHotJoinAnimatedEvent(Path file,
            TableVisualEvent animatedEvent) {
        TableSnapshot.CardSnapshot back = new TableSnapshot.CardSnapshot(
                "", false, false, true);
        TableSnapshot initial = new TableSnapshot(1L, "human",
                TableSnapshot.Street.PREFLOP, 0d, "", false,
                List.of(player("human"), player("remote")), List.of());
        TableSnapshot.PlayerSnapshot warming = new TableSnapshot.PlayerSnapshot(
                "human", 10d, 0d, 0d, false, true, false, false,
                -1, -1, 0, 0L, false, false, TableSnapshot.Position.NONE,
                "CALENTANDO", "", List.of(), 10, 0, true);
        TableSnapshot.PlayerSnapshot remote = new TableSnapshot.PlayerSnapshot(
                "remote", 9d, 1d, 1d, true, false, false, false,
                -1, -1, 0, 0L, false, false,
                TableSnapshot.Position.BIG_BLIND, "VA", "",
                List.of(back, back));
        TableSnapshot bootstrap = new TableSnapshot(2L, "human",
                TableSnapshot.Street.FLOP, 2d, "remote", false,
                List.of(warming, remote), List.of(card("A_C", true),
                        card("K_D", true), card("Q_T", true), back, back));
        GdxTableViewState projection = new GdxTableViewState(initial);
        CoronaPokerGdxTable table = animatedTable(file, projection);
        CompletableFuture<Void> cutover = new CompletableFuture<>();
        table.acceptEvent(new TableVisualEvent.HotJoinState(1L, bootstrap),
                cutover);
        assertTrue(cutover.isDone(),
                "the bootstrap itself must not fabricate an animation");

        CompletableFuture<Void> visualBarrier = new CompletableFuture<>();
        table.acceptEvent(animatedEvent, visualBarrier);

        assertFalse(visualBarrier.isDone(),
                "CALENTANDO must use the ordinary animated event path");
        assertEquals(2L, projection.lastSequence());
    }

    private static CoronaPokerGdxTable animatedTable(Path file,
            GdxTableViewState projection) {
        PreferencesService preferences = new PreferencesService(
                file.resolve("coronapoker.properties"));
        preferences.properties().setProperty("sonido_efectos", "false");
        preferences.properties().setProperty("animaciones", "true");
        preferences.properties().setProperty("cinematicas_gameover", "false");
        preferences.properties().setProperty("animacion_reparto", "true");
        preferences.properties().setProperty("animacion_destape", "true");
        preferences.properties().setProperty("animacion_apuestas", "true");
        preferences.properties().setProperty("animacion_contadores", "true");
        return new CoronaPokerGdxTable(240, projection,
                command -> { }, () -> { }, new GdxGameLogSink(), preferences,
                null, new GdxGamePresentationSettings(preferences));
    }

    private static CoronaPokerGdxTable nonAnimatedTable(Path directory,
            GdxTableViewState projection, boolean masterEnabled) {
        PreferencesService preferences = new PreferencesService(
                directory.resolve("coronapoker.properties"));
        preferences.properties().setProperty("sonido_efectos", "false");
        preferences.properties().setProperty("animaciones",
                Boolean.toString(masterEnabled));
        preferences.properties().setProperty("animacion_apuestas", "false");
        preferences.properties().setProperty("animacion_contadores", "false");
        preferences.properties().setProperty("animacion_reparto", "true");
        return new CoronaPokerGdxTable(240, projection,
                command -> { }, () -> { }, new GdxGameLogSink(), preferences,
                null, new GdxGamePresentationSettings(preferences));
    }

    private static double displayedPot(CoronaPokerGdxTable table)
            throws Exception {
        Method method = CoronaPokerGdxTable.class.getDeclaredMethod("livePot");
        method.setAccessible(true);
        return (double) method.invoke(table);
    }

    private static List<CoronaPokerGdxTable.VisualChipSpec> potChips(
            CoronaPokerGdxTable table) throws Exception {
        return potInventory(table).snapshot();
    }

    private static GdxPotChipInventory potInventory(
            CoronaPokerGdxTable table) throws Exception {
        Field field = CoronaPokerGdxTable.class.getDeclaredField(
                "livePotChips");
        field.setAccessible(true);
        return (GdxPotChipInventory) field.get(table);
    }

    private static TableVisualEvent.TelemetryStatus telemetry(long sequence) {
        return new TableVisualEvent.TelemetryStatus(sequence, List.of(
                new TableVisualEvent.PlayerTelemetry("remote", 12, 14, 0,
                        1L)));
    }

    private static TableSnapshot.PlayerSnapshot player(
            GdxTableViewState projection, String nickname) {
        return projection.snapshot().players().stream()
                .filter(candidate -> candidate.nickname().equals(nickname))
                .findFirst().orElseThrow();
    }

    private static TableSnapshot.CardSnapshot card(String code,
            boolean faceUp) {
        return new TableSnapshot.CardSnapshot(code, faceUp, false);
    }

    private static TableSnapshot.PlayerSnapshot player(String nickname) {
        return player(nickname, TableSnapshot.Position.NONE);
    }

    private static TableSnapshot.PlayerSnapshot player(String nickname,
            TableSnapshot.Position position) {
        return new TableSnapshot.PlayerSnapshot(nickname, 10d, 0d, 0d,
                true, false, false, false, -1, -1, 0, 0L, false,
                position, "", "", List.of());
    }

    private static Field tableField(String name) throws Exception {
        Field field = CoronaPokerGdxTable.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void placeSeat(CoronaPokerGdxTable table, String nickname,
            float x, float y) throws Exception {
        Map<?, ?> seats = (Map<?, ?>) tableField("seatsByNickname").get(table);
        Object seat = seats.get(nickname);
        assertNotNull(seat);
        Field seatX = seat.getClass().getDeclaredField("x");
        Field seatY = seat.getClass().getDeclaredField("y");
        Field ready = seat.getClass().getDeclaredField("geometryReady");
        seatX.setAccessible(true);
        seatY.setAccessible(true);
        ready.setAccessible(true);
        seatX.setFloat(seat, x);
        seatY.setFloat(seat, y);
        ready.setBoolean(seat, true);
    }

    private static void assertDealerSource(CoronaPokerGdxTable table,
            float x, float y) throws Exception {
        Method update = CoronaPokerGdxTable.class.getDeclaredMethod(
                "updateDealerSource", float.class, float.class);
        update.setAccessible(true);
        update.invoke(table, 1920f, 1080f);
        assertEquals(x, tableField("dealerSourceX").getFloat(table));
        assertEquals(y, tableField("dealerSourceY").getFloat(table));
    }

    private static TableSnapshot.PlayerSnapshot playerState(String nickname,
            double stack, boolean active, boolean spectator, int buyIn,
            int rebuyCount) {
        return new TableSnapshot.PlayerSnapshot(nickname, stack, 0d, 0d,
                active, spectator, false, false, -1, -1, 0, 0L, false,
                false, TableSnapshot.Position.NONE, "", "", List.of(),
                buyIn, rebuyCount, false);
    }
}
