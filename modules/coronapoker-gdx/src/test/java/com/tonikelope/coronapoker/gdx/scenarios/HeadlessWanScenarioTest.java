/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.CoreGameTableFactory;
import com.tonikelope.coronapoker.Crupier;
import com.tonikelope.coronapoker.core.DatabaseService;
import com.tonikelope.coronapoker.core.LobbyCommand;
import com.tonikelope.coronapoker.core.LobbySession;
import com.tonikelope.coronapoker.core.NewGameConnectionDraft;
import com.tonikelope.coronapoker.core.NewGameRequest;
import com.tonikelope.coronapoker.core.NewGameTableDraft;
import com.tonikelope.coronapoker.core.game.ActionControlState;
import com.tonikelope.coronapoker.core.game.GameCinematicAssets;
import com.tonikelope.coronapoker.core.game.GameDecisionSink;
import com.tonikelope.coronapoker.core.game.GameDialogSink;
import com.tonikelope.coronapoker.core.game.GameLogSink;
import com.tonikelope.coronapoker.core.game.GamePresentationSettings;
import com.tonikelope.coronapoker.core.game.GameText;
import com.tonikelope.coronapoker.core.network.NetworkLobbyGateway;
import com.tonikelope.coronapoker.core.network.SessionOutbox;
import com.tonikelope.coronapoker.table.TableCommand;
import com.tonikelope.coronapoker.table.TableRenderer;
import com.tonikelope.coronapoker.table.TableSession;
import com.tonikelope.coronapoker.table.TableSessionSummary;
import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.awt.GraphicsEnvironment;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real ten-peer encrypted games driven entirely through the renderer-neutral API. */
@Tag("certification")
class HeadlessWanScenarioTest {
    private static final int PLAYERS = 10;

    @Test
    void tenHeadlessPlayersCompleteAllHandsUnderContinuousWanCongestion(@TempDir Path root)
            throws Exception {
        run(root, Integer.getInteger("coronapoker.qa.gdx.soakHands", 3), 325, 90, 128 * 1024);
    }

    @Test
    void tenHeadlessPlayersCompleteThreeHandsUnderExtremeWanCongestion(@TempDir Path root)
            throws Exception {
        run(root, 3, 3000, 750, 8 * 1024);
    }

    private static void run(Path root, int hands, int delay, int jitter, int bandwidth) throws Exception {
        System.setProperty("java.awt.headless", "true");
        assertTrue(GraphicsEnvironment.isHeadless());
        AtomicLong progress = new AtomicLong(System.nanoTime());
        Evidence evidence = new Evidence();
        Logger logger = Logger.getLogger(Crupier.class.getName());
        logger.addHandler(evidence);
        try (Resources resources = new Resources()) {
            int port;
            try (ServerSocket reservation = new ServerSocket(0)) {
                port = reservation.getLocalPort();
            }
            List<LobbySession> lobbies = new ArrayList<>();
            for (int seat = 0; seat < PLAYERS; seat++) {
                Path home = Files.createDirectories(root.resolve("seat-" + seat));
                DatabaseService database = resources.own(new DatabaseService(home.resolve("game.sqlite").toString()));
                database.start();
                var factory = new CoreGameTableFactory(database, GameText.keys(), GameLogSink.noop(),
                        GameDialogSink.noop(), GameDecisionSink.noop(), GamePresentationSettings.defaults(),
                        GameCinematicAssets.none());
                NetworkLobbyGateway gateway = resources.own(new NetworkLobbyGateway(home, factory));
                int joinPort = seat == 0 ? port
                        : resources.own(new GdxWanTrafficProxy(port, delay, jitter, bandwidth, seat)).port();
                LobbySession lobby = resources.own(gateway.open(request(seat, joinPort, hands))
                        .get(15, TimeUnit.MINUTES));
                lobbies.add(lobby);
            }
            assertEquals(PLAYERS, lobbies.get(0).snapshot().participants().size());
            List<Object> transportOwners = new ArrayList<>();
            for (LobbySession lobby : lobbies) {
                try {
                    transportOwners.add(field(lobby, "resource"));
                } catch (ReflectiveOperationException unavailable) {
                    System.out.println("WAN_QUEUE_DIAGNOSTIC_UNAVAILABLE " + unavailable);
                }
            }
            lobbies.get(0).submit(new LobbyCommand.StartGame()).toCompletableFuture().get(15, TimeUnit.MINUTES);
            List<BackendRenderer> renderers = new ArrayList<>();
            for (LobbySession lobby : lobbies) {
                TableSession table = resources.own(lobby.tableSession().toCompletableFuture().get(15, TimeUnit.MINUTES));
                BackendRenderer renderer = new BackendRenderer(table, hands, progress);
                renderers.add(renderer);
                table.attach(renderer).toCompletableFuture().get(15, TimeUnit.MINUTES);
            }
            var completed = CompletableFuture.allOf(renderers.stream().map(renderer -> renderer.finished)
                    .toArray(CompletableFuture<?>[]::new));
            long lastDiagnostic = System.nanoTime();
            while (!completed.isDone()) {
                if (Boolean.getBoolean("coronapoker.qa.gdx.wanDiagnostics")
                        && System.nanoTime() - lastDiagnostic > Duration.ofSeconds(20).toNanos()) {
                    dumpTransportQueues(transportOwners);
                    lastDiagnostic = System.nanoTime();
                }
                if (evidence.failure.isDone()) {
                    dumpTransportQueues(transportOwners);
                    evidence.failure.join();
                }
                for (BackendRenderer renderer : renderers) {
                    if (renderer.finished.isCompletedExceptionally()) renderer.finished.join();
                }
                assertTrue(System.nanoTime() - progress.get() < Duration.ofMinutes(15).toNanos(),
                        "headless WAN game stopped making gameplay progress");
                Thread.sleep(20L);
            }
            completed.get();
            if (evidence.failure.isDone()) evidence.failure.join();
            List<TableSessionSummary.PlayerBalance> expected = balances(renderers.get(0).finished.join());
            for (BackendRenderer renderer : renderers) {
                assertEquals(expected, balances(renderer.finished.join()));
            }
            assertEquals(expected.stream().mapToLong(balance -> Math.round(balance.totalBuyin() * 100d)).sum(),
                    expected.stream().mapToLong(balance -> Math.round(balance.finalStack() * 100d)).sum());
            evidence.assertComplete(hands);
        } finally {
            logger.removeHandler(evidence);
        }
        if (evidence.failure.isDone()) evidence.failure.join();
        System.out.printf("HEADLESS_WAN_PASS players=%d hands=%d rtt_ms=%d jitter_ms=%d bytes_per_second=%d%n",
                PLAYERS, hands, delay * 2, jitter, bandwidth);
    }

    private static List<TableSessionSummary.PlayerBalance> balances(TableSessionSummary summary) {
        return summary.balances().stream().sorted(Comparator.comparing(
                TableSessionSummary.PlayerBalance::nickname)).toList();
    }

    private static void dumpTransportQueues(List<Object> transportOwners) {
        try {
            for (Object transport : transportOwners) {
                if (transport == null) continue;
                Object server = field(transport, "serverConnection");
                if (server != null) {
                    dumpConnection("client-to-host", server);
                } else {
                    Map<?, ?> peers = (Map<?, ?>) field(transport, "peers");
                    synchronized (peers) {
                        for (var entry : peers.entrySet()) {
                            dumpConnection("host-to-" + entry.getKey(), field(entry.getValue(), "connection"));
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException diagnosticFailure) {
            System.out.println("WAN_QUEUE_DIAGNOSTIC_UNAVAILABLE " + diagnosticFailure);
        }
    }

    private static void dumpConnection(String direction, Object connection) throws ReflectiveOperationException {
        if (connection == null) return;
        SessionOutbox outbox = (SessionOutbox) field(connection, "gameOutbox");
        synchronized (outbox) {
            SessionOutbox.Entry head = outbox.peek();
            String command = head == null ? "EMPTY" : head.command().split("#", 2)[0];
            System.out.printf("WAN_QUEUE direction=%s entries=%d bytes=%d generation=%d head=%s latency=%s%n",
                    direction, outbox.size(), outbox.queuedBytes(), outbox.generation(), command,
                    field(connection, "latency"));
        }
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static NewGameRequest request(int seat, int port, int hands) {
        var connection = new NewGameConnectionDraft.Submission(seat == 0
                ? NewGameConnectionDraft.Mode.CREATE : NewGameConnectionDraft.Mode.JOIN,
                "Headless" + seat, "", "127.0.0.1", Integer.toString(port), null, false, false, null);
        if (seat != 0) return new NewGameRequest(connection, null);
        NewGameTableDraft table = new NewGameTableDraft();
        table.setHandLimit(true);
        table.setHandLimitCount(hands);
        table.setThinkTime(false);
        table.setMaxBuyinBb(NewGameTableDraft.MAX_BUYIN_BB);
        table.setBuyin(table.maximumBuyin());
        return new NewGameRequest(connection, table.snapshot());
    }

    private static final class BackendRenderer implements TableRenderer {
        private final TableSession table;
        private final int expectedHands;
        private final AtomicLong progress;
        private final EnumSet<TableSnapshot.Street> streets = EnumSet.noneOf(TableSnapshot.Street.class);
        private final CompletableFuture<TableSessionSummary> finished = new CompletableFuture<>();
        private long previousSequence;
        private int closedHands;

        BackendRenderer(TableSession table, int expectedHands, AtomicLong progress) {
            this.table = table;
            this.expectedHands = expectedHands;
            this.progress = progress;
        }

        @Override public CompletionStage<Void> open(TableSnapshot snapshot) {
            assertEquals(PLAYERS, snapshot.players().size());
            return CompletableFuture.completedFuture(null);
        }

        @Override public CompletionStage<Void> render(TableVisualEvent event) {
            try {
                assertTrue(event.sequence() > previousSequence, "table events lost their order");
                previousSequence = event.sequence();
                if (event instanceof TableVisualEvent.HandBoundary boundary) {
                    if (boundary.phase() == TableVisualEvent.HandBoundary.Phase.PREPARE) streets.clear();
                    if (boundary.phase() == TableVisualEvent.HandBoundary.Phase.END) {
                        assertEquals(EnumSet.of(TableSnapshot.Street.FLOP, TableSnapshot.Street.TURN,
                                TableSnapshot.Street.RIVER), streets);
                        closedHands++;
                    }
                } else if (event instanceof TableVisualEvent.RevealCommunityCards reveal) {
                    streets.add(reveal.street());
                } else if (event instanceof TableVisualEvent.ActionControls controls
                        && controls.state().callAction() != ActionControlState.CallAction.DISABLED) {
                    table.commands().submit(new TableCommand.CheckOrCall());
                } else if (event instanceof TableVisualEvent.CloseTable close) {
                    assertEquals(TableSessionSummary.CloseReason.COMPLETED, close.summary().reason());
                    assertEquals(expectedHands, closedHands);
                    assertEquals(expectedHands, close.summary().handCount());
                    assertEquals(PLAYERS, close.summary().balances().size());
                    finished.complete(close.summary());
                }
                if (event instanceof TableVisualEvent.HandBoundary || event instanceof TableVisualEvent.Shuffle
                        || event instanceof TableVisualEvent.DealHoleCard || event instanceof TableVisualEvent.PlayerAction
                        || event instanceof TableVisualEvent.RevealCommunityCards || event instanceof TableVisualEvent.HandResult) {
                    progress.set(System.nanoTime());
                }
                return CompletableFuture.completedFuture(null);
            } catch (Throwable failure) {
                finished.completeExceptionally(failure);
                return CompletableFuture.failedFuture(failure);
            }
        }

        @Override public void close() { }
    }

    private static final class Evidence extends Handler {
        private final CompletableFuture<Void> failure = new CompletableFuture<>();
        private static final Pattern BALANCE = Pattern.compile("Balance after hand (\\d+) -> (.*)");
        private final Map<Integer, ConcurrentLinkedQueue<String>> hashes = new ConcurrentHashMap<>();
        private final Map<Integer, ConcurrentLinkedQueue<List<String>>> balances = new ConcurrentHashMap<>();

        @Override public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= java.util.logging.Level.SEVERE.intValue()) {
                AssertionError error = new AssertionError(record.getMessage());
                if (record.getThrown() != null) error.initCause(record.getThrown());
                failure.completeExceptionally(error);
            }
            if (record.getMessage() == null) return;
            if ("Hand {0} verified: {1} receipts unanimous, H={2}".equals(record.getMessage())) {
                Object[] parameters = record.getParameters();
                assertEquals(PLAYERS, ((Number) parameters[1]).intValue());
                hashes.computeIfAbsent(((Number) parameters[0]).intValue(),
                        ignored -> new ConcurrentLinkedQueue<>()).add(parameters[2].toString());
            }
            var match = BALANCE.matcher(record.getMessage());
            if (match.matches()) {
                balances.computeIfAbsent(Integer.parseInt(match.group(1)),
                        ignored -> new ConcurrentLinkedQueue<>()).add(Arrays.stream(match.group(2).split("@")).sorted().toList());
            }
        }

        void assertComplete(int hands) {
            assertEquals(hands, hashes.size());
            assertEquals(hands, balances.size());
            for (int hand = 1; hand <= hands; hand++) {
                assertEquals(PLAYERS, hashes.get(hand).size());
                assertEquals(1L, hashes.get(hand).stream().distinct().count());
                assertEquals(PLAYERS, balances.get(hand).size());
                assertEquals(1L, balances.get(hand).stream().distinct().count());
            }
        }

        @Override public void flush() { }
        @Override public void close() { }
    }

    private static final class Resources implements AutoCloseable {
        private final ArrayDeque<AutoCloseable> owned = new ArrayDeque<>();

        <T extends AutoCloseable> T own(T resource) {
            owned.push(resource);
            return resource;
        }

        @Override public void close() throws Exception {
            Exception first = null;
            while (!owned.isEmpty()) {
                try {
                    owned.pop().close();
                } catch (Exception failure) {
                    if (first == null) first = failure;
                    else first.addSuppressed(failure);
                }
            }
            if (first != null) throw first;
        }
    }
}
