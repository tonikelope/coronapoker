/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.PreferencesService;
import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
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
        return new TableSnapshot.PlayerSnapshot(nickname, 10d, 0d, 0d,
                true, false, false, false, -1, -1, 0, 0L, false,
                TableSnapshot.Position.NONE, "", "", List.of());
    }
}
