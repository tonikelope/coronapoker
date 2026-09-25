package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.DatabaseService;
import com.tonikelope.coronapoker.core.LobbyCommand;
import com.tonikelope.coronapoker.core.LobbySession;
import com.tonikelope.coronapoker.core.NewGameConnectionDraft;
import com.tonikelope.coronapoker.core.NewGameRequest;
import com.tonikelope.coronapoker.core.NewGameTableDraft;
import com.tonikelope.coronapoker.core.RecoverableGameRepository;
import com.tonikelope.coronapoker.core.game.ActionControlState;
import com.tonikelope.coronapoker.core.game.GameDecisionSink;
import com.tonikelope.coronapoker.core.game.GameText;
import com.tonikelope.coronapoker.core.identity.PlayerIdentity;
import com.tonikelope.coronapoker.core.network.NetworkLobbyGateway;
import com.tonikelope.coronapoker.table.TableCommand;
import com.tonikelope.coronapoker.table.TableRenderer;
import com.tonikelope.coronapoker.table.TableSession;
import com.tonikelope.coronapoker.table.TableSessionSummary;
import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** GDX-owned counterpart of Swing's reconnect-midhand scenario. */
class GdxReconnectScenarioTest {

    @TempDir Path temporary;

    /**
     * Supporting GDX network coverage for Swing's {@code normal} scenario:
     * one host, one network human, two bots and one complete hand.  It uses the
     * production gateway, sockets, dealer and typed table commands; only the
     * renderer is the deterministic GDX scenario probe.
     */
    @Test
    void normalScenarioCompletesOneHandWithOnePeerAndTwoBots()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = database("normal-host.sqlite");
        DatabaseService clientDatabase = database("normal-client.sqlite");
        try (hostDatabase; clientDatabase;
             NetworkLobbyGateway hostGateway = gateway("normal-host",
                     hostDatabase);
             NetworkLobbyGateway clientGateway = gateway("normal-client",
                     clientDatabase)) {
            LobbySession host = hostGateway.open(
                    request(false, "Anfitrion", port, 1))
                    .get(5, TimeUnit.SECONDS);
            LobbySession client = clientGateway.open(
                    request(true, "Invitado", port, 1))
                    .get(5, TimeUnit.SECONDS);
            try {
                host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                await(() -> host.snapshot().participants().size() == 4
                                && client.snapshot().participants().size() == 4,
                        Duration.ofSeconds(5));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                TableSession hostTable = host.tableSession()
                        .toCompletableFuture().get(8, TimeUnit.SECONDS);
                GdxScenarioRenderer hostRenderer = attach(hostTable, 4, -1);
                TableSession clientTable = client.tableSession()
                        .toCompletableFuture().get(12, TimeUnit.SECONDS);
                GdxScenarioRenderer clientRenderer = attach(clientTable, 4, -1);

                await(() -> hostRenderer.isClosed()
                                && clientRenderer.isClosed(),
                        Duration.ofSeconds(25));
                hostRenderer.assertComplete(1);
                clientRenderer.assertComplete(1);
            } finally {
                client.close();
                host.close();
            }
        }
    }

    @Test
    void normalHeadsUpMatchesTheFastSwingTopology() throws Exception {
        runNormalTopology("normal-heads-up", 1, 0, 5);
    }

    @Test
    void normalSoakMatchesTheFastSwingTopology() throws Exception {
        runNormalTopology("normal-soak", 2, 2, 5);
    }

    @Test
    void normalFullMixedMatchesTheFastSwingTopology() throws Exception {
        runNormalTopology("normal-full-mixed", 4, 5, 1);
    }

    @Test
    void normalFullHumanMatchesTheFastSwingTopology() throws Exception {
        runNormalTopology("normal-full-human", 9, 0, 1);
    }

    /**
     * Exact GDX-owned counterpart of Swing's {@code raise-mix} scenario:
     * three network humans and two bots complete ten hands while the human
     * frontends exercise the real bet/raise controls.  Accepted action amounts
     * must retain exact-cent semantics and every peer must settle the same
     * conserved ledger.
     */
    @Test
    void nativeGdxRaiseMixUsesRealHumanRaiseControlsAndSettlesTenHands()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = database("raise-host.sqlite");
        DatabaseService firstDatabase = database("raise-first.sqlite");
        DatabaseService secondDatabase = database("raise-second.sqlite");
        AtomicInteger raiseSubmissions = new AtomicInteger();
        try (hostDatabase; firstDatabase; secondDatabase;
             NetworkLobbyGateway hostGateway = gateway("raise-host",
                     hostDatabase);
             NetworkLobbyGateway firstGateway = gateway("raise-first",
                     firstDatabase);
             NetworkLobbyGateway secondGateway = gateway("raise-second",
                     secondDatabase)) {
            LobbySession host = hostGateway.open(
                    request(false, "Anfitrion", port, 10))
                    .get(5, TimeUnit.SECONDS);
            LobbySession first = firstGateway.open(
                    request(true, "Invitado1", port, 10))
                    .get(5, TimeUnit.SECONDS);
            LobbySession second = secondGateway.open(
                    request(true, "Invitado2", port, 10))
                    .get(5, TimeUnit.SECONDS);
            try {
                host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                await(() -> host.snapshot().participants().size() == 5
                                && first.snapshot().participants().size() == 5
                                && second.snapshot().participants().size() == 5,
                        Duration.ofSeconds(5));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                TableSession hostTable = host.tableSession()
                        .toCompletableFuture().get(8, TimeUnit.SECONDS);
                RaiseMixScenarioRenderer hostRenderer
                        = new RaiseMixScenarioRenderer(hostTable,
                                raiseSubmissions);
                hostTable.attach(hostRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TableSession firstTable = first.tableSession()
                        .toCompletableFuture().get(12, TimeUnit.SECONDS);
                RaiseMixScenarioRenderer firstRenderer
                        = new RaiseMixScenarioRenderer(firstTable,
                                raiseSubmissions);
                firstTable.attach(firstRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TableSession secondTable = second.tableSession()
                        .toCompletableFuture().get(12, TimeUnit.SECONDS);
                RaiseMixScenarioRenderer secondRenderer
                        = new RaiseMixScenarioRenderer(secondTable,
                                raiseSubmissions);
                secondTable.attach(secondRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                await(() -> hostRenderer.closed()
                                && firstRenderer.closed()
                                && secondRenderer.closed(),
                        Duration.ofSeconds(180));
                hostRenderer.assertComplete();
                firstRenderer.assertComplete();
                secondRenderer.assertComplete();
                assertTrue(hostRenderer.acceptedRaiseActions() >= 3,
                        "raise-mix must accept at least three human raises");
                assertEquals(hostRenderer.balancesByNickname(),
                        firstRenderer.balancesByNickname());
                assertEquals(hostRenderer.balancesByNickname(),
                        secondRenderer.balancesByNickname());
            } finally {
                second.close();
                first.close();
                host.close();
            }
        }
    }

    /** Exact GDX homologue of Swing's {@code pause-resume} scenario. */
    @Test
    void pauseResumeScenarioPreservesTheDecisionAndCompletesTwoHands()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = database("pause-host.sqlite");
        DatabaseService clientDatabase = database("pause-client.sqlite");
        DatabaseService witnessDatabase = database("pause-witness.sqlite");
        try (hostDatabase; clientDatabase; witnessDatabase;
             NetworkLobbyGateway hostGateway = gateway("pause-host",
                     hostDatabase);
             NetworkLobbyGateway clientGateway = gateway("pause-client",
                     clientDatabase);
             NetworkLobbyGateway witnessGateway = gateway("pause-witness",
                     witnessDatabase)) {
            LobbySession host = hostGateway.open(
                    request(false, "Anfitrion", port, 2))
                    .get(5, TimeUnit.SECONDS);
            LobbySession client = clientGateway.open(
                    request(true, "Invitado", port, 2))
                    .get(5, TimeUnit.SECONDS);
            LobbySession witness = witnessGateway.open(
                    request(true, "Testigo", port, 2))
                    .get(5, TimeUnit.SECONDS);
            try {
                host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                await(() -> host.snapshot().participants().size() == 4
                                && client.snapshot().participants().size() == 4
                                && witness.snapshot().participants().size() == 4,
                        Duration.ofSeconds(5));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                TableSession hostTable = host.tableSession()
                        .toCompletableFuture().get(8, TimeUnit.SECONDS);
                GdxScenarioRenderer hostRenderer = attach(hostTable, 4, 1);
                TableSession clientTable = client.tableSession()
                        .toCompletableFuture().get(12, TimeUnit.SECONDS);
                GdxScenarioRenderer clientRenderer = attach(clientTable, 4, -1);
                TableSession witnessTable = witness.tableSession()
                        .toCompletableFuture().get(12, TimeUnit.SECONDS);
                GdxScenarioRenderer witnessRenderer = attach(witnessTable, 4, -1);

                await(hostRenderer::hasHeldAction, Duration.ofSeconds(12));
                hostTable.commands().submit(new TableCommand.TogglePause());
                await(() -> hostRenderer.sawPaused()
                                && clientRenderer.sawPaused()
                                && witnessRenderer.sawPaused()
                                && hostRenderer.isPaused()
                                && clientRenderer.isPaused()
                                && witnessRenderer.isPaused(),
                        Duration.ofSeconds(8));

                hostTable.commands().submit(new TableCommand.TogglePause());
                await(() -> hostRenderer.resumedAfterPause()
                                && clientRenderer.resumedAfterPause()
                                && witnessRenderer.resumedAfterPause()
                                && !hostRenderer.isPaused()
                                && !clientRenderer.isPaused()
                                && !witnessRenderer.isPaused(),
                        Duration.ofSeconds(8));
                hostRenderer.releaseHeldAction();

                await(() -> hostRenderer.isClosed()
                                && clientRenderer.isClosed()
                                && witnessRenderer.isClosed(),
                        Duration.ofSeconds(35));
                hostRenderer.assertComplete(2);
                clientRenderer.assertComplete(2);
                witnessRenderer.assertComplete(2);
                assertEquals(hostRenderer.balancesByNickname(),
                        clientRenderer.balancesByNickname());
                assertEquals(hostRenderer.balancesByNickname(),
                        witnessRenderer.balancesByNickname());
            } finally {
                witness.close();
                client.close();
                host.close();
            }
        }
    }

    /** Exact GDX homologue of Swing's {@code controlled-exit} scenario. */
    @Test
    void controlledExitDuringDecisionLetsTheRemainingTableFinishNormally()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = database("controlled-exit-host.sqlite");
        DatabaseService clientDatabase = database("controlled-exit-client.sqlite");
        DatabaseService witnessDatabase = database(
                "controlled-exit-witness.sqlite");
        try (hostDatabase; clientDatabase; witnessDatabase;
             NetworkLobbyGateway hostGateway = gateway("controlled-exit-host",
                     hostDatabase);
             NetworkLobbyGateway clientGateway = gateway("controlled-exit-client",
                     clientDatabase);
             NetworkLobbyGateway witnessGateway = gateway(
                     "controlled-exit-witness", witnessDatabase)) {
            LobbySession host = hostGateway.open(
                    request(false, "Anfitrion", port, 1))
                    .get(5, TimeUnit.SECONDS);
            LobbySession client = clientGateway.open(
                    request(true, "Invitado", port, 1))
                    .get(5, TimeUnit.SECONDS);
            LobbySession witness = witnessGateway.open(
                    request(true, "Testigo", port, 1))
                    .get(5, TimeUnit.SECONDS);
            try {
                host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                await(() -> host.snapshot().participants().size() == 4
                                && client.snapshot().participants().size() == 4
                                && witness.snapshot().participants().size() == 4,
                        Duration.ofSeconds(5));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                TableSession hostTable = host.tableSession()
                        .toCompletableFuture().get(8, TimeUnit.SECONDS);
                GdxScenarioRenderer hostRenderer = attach(hostTable, 4, -1);
                TableSession clientTable = client.tableSession()
                        .toCompletableFuture().get(12, TimeUnit.SECONDS);
                GdxScenarioRenderer clientRenderer = attach(clientTable, 4, 1);
                TableSession witnessTable = witness.tableSession()
                        .toCompletableFuture().get(12, TimeUnit.SECONDS);
                GdxScenarioRenderer witnessRenderer = attach(witnessTable, 4, -1);

                await(clientRenderer::hasHeldAction, Duration.ofSeconds(12));
                clientTable.commands().submit(new TableCommand.ExitGame());

                await(clientRenderer::isClosed, Duration.ofSeconds(12));
                await(hostRenderer::isClosed, Duration.ofSeconds(30));
                await(witnessRenderer::isClosed, Duration.ofSeconds(30));
                assertEquals(TableSessionSummary.CloseReason.EXITED,
                        clientRenderer.summary().reason());
                assertEquals(TableSessionSummary.CloseReason.COMPLETED,
                        hostRenderer.summary().reason());
                assertEquals(1, hostRenderer.completedHands());
                witnessRenderer.assertComplete(1);
                assertEquals(hostRenderer.balancesByNickname(),
                        witnessRenderer.balancesByNickname());
                double stacks = hostRenderer.summary().balances().stream()
                        .mapToDouble(TableSessionSummary.PlayerBalance::finalStack)
                        .sum();
                double buyins = hostRenderer.summary().balances().stream()
                        .mapToDouble(TableSessionSummary.PlayerBalance::totalBuyin)
                        .sum();
                assertEquals(buyins, stacks, 0.001d,
                        "controlled exit must conserve the complete table ledger");
            } finally {
                witness.close();
                client.close();
                host.close();
            }
        }
    }

    /** Exact GDX homologue of Swing's {@code abrupt-exit} scenario. */
    @Test
    void abruptExitAbortsTheHandAndLeavesSurvivorsRecoverable()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = database("abrupt-host.sqlite");
        DatabaseService victimDatabase = database("abrupt-victim.sqlite");
        DatabaseService witnessDatabase = database("abrupt-witness.sqlite");
        try (hostDatabase; victimDatabase; witnessDatabase;
             NetworkLobbyGateway hostGateway = gateway("abrupt-host",
                     hostDatabase);
             NetworkLobbyGateway victimGateway = gateway("abrupt-victim",
                     victimDatabase);
             NetworkLobbyGateway witnessGateway = gateway("abrupt-witness",
                     witnessDatabase)) {
            LobbySession host = hostGateway.open(
                    request(false, "Anfitrion", port, 1))
                    .get(5, TimeUnit.SECONDS);
            LobbySession victim = victimGateway.open(
                    request(true, "Victima", port, 1))
                    .get(5, TimeUnit.SECONDS);
            LobbySession witness = witnessGateway.open(
                    request(true, "Testigo", port, 1))
                    .get(5, TimeUnit.SECONDS);
            try {
                host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                await(() -> host.snapshot().participants().size() == 4
                                && victim.snapshot().participants().size() == 4
                                && witness.snapshot().participants().size() == 4,
                        Duration.ofSeconds(6));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                GdxScenarioRenderer hostRenderer = attach(
                        host.tableSession().toCompletableFuture()
                                .get(8, TimeUnit.SECONDS), 4, -1);
                GdxScenarioRenderer victimRenderer = attach(
                        victim.tableSession().toCompletableFuture()
                                .get(12, TimeUnit.SECONDS), 4, 1);
                GdxScenarioRenderer witnessRenderer = attach(
                        witness.tableSession().toCompletableFuture()
                                .get(12, TimeUnit.SECONDS), 4, -1);

                await(victimRenderer::hasHeldAction, Duration.ofSeconds(12));
                closeClientTransport(victim);

                await(() -> hostRenderer.isClosed()
                                && witnessRenderer.isClosed(),
                        Duration.ofSeconds(150));
                assertEquals(TableSessionSummary.CloseReason.RECOVERABLE_STOP,
                        hostRenderer.summary().reason());
                assertEquals(TableSessionSummary.CloseReason.RECOVERABLE_STOP,
                        witnessRenderer.summary().reason());
                assertEquals(hostRenderer.balancesByNickname(),
                        witnessRenderer.balancesByNickname());
                assertConservedLedger(hostRenderer.summary(), 4);
            } finally {
                witness.close();
                victim.close();
                host.close();
            }
        }
    }

    /** Exact GDX homologue of Swing's {@code dual-abrupt-exit} scenario. */
    @Test
    void dualAbruptExitRefundsTheTableAndLeavesTheWitnessRecoverable()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = database("dual-abrupt-host.sqlite");
        DatabaseService firstDatabase = database("dual-abrupt-first.sqlite");
        DatabaseService secondDatabase = database("dual-abrupt-second.sqlite");
        DatabaseService witnessDatabase = database("dual-abrupt-witness.sqlite");
        try (hostDatabase; firstDatabase; secondDatabase; witnessDatabase;
             NetworkLobbyGateway hostGateway = gateway("dual-abrupt-host",
                     hostDatabase);
             NetworkLobbyGateway firstGateway = gateway("dual-abrupt-first",
                     firstDatabase);
             NetworkLobbyGateway secondGateway = gateway("dual-abrupt-second",
                     secondDatabase);
             NetworkLobbyGateway witnessGateway = gateway(
                     "dual-abrupt-witness", witnessDatabase)) {
            LobbySession host = hostGateway.open(
                    request(false, "Anfitrion", port, 1))
                    .get(5, TimeUnit.SECONDS);
            LobbySession first = firstGateway.open(
                    request(true, "Victima1", port, 1))
                    .get(5, TimeUnit.SECONDS);
            LobbySession second = secondGateway.open(
                    request(true, "Victima2", port, 1))
                    .get(5, TimeUnit.SECONDS);
            LobbySession witness = witnessGateway.open(
                    request(true, "Testigo", port, 1))
                    .get(5, TimeUnit.SECONDS);
            try {
                host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                await(() -> host.snapshot().participants().size() == 5
                                && first.snapshot().participants().size() == 5
                                && second.snapshot().participants().size() == 5
                                && witness.snapshot().participants().size() == 5,
                        Duration.ofSeconds(6));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                GdxScenarioRenderer hostRenderer = attach(
                        host.tableSession().toCompletableFuture()
                                .get(8, TimeUnit.SECONDS), 5, -1);
                GdxScenarioRenderer firstRenderer = attach(
                        first.tableSession().toCompletableFuture()
                                .get(12, TimeUnit.SECONDS), 5, 1);
                attach(second.tableSession().toCompletableFuture()
                        .get(12, TimeUnit.SECONDS), 5, -1);
                GdxScenarioRenderer witnessRenderer = attach(
                        witness.tableSession().toCompletableFuture()
                                .get(12, TimeUnit.SECONDS), 5, -1);

                await(firstRenderer::hasHeldAction, Duration.ofSeconds(12));
                closeClientTransport(first);
                closeClientTransport(second);

                await(() -> hostRenderer.isClosed()
                                && witnessRenderer.isClosed(),
                        Duration.ofSeconds(150));
                assertEquals(TableSessionSummary.CloseReason.RECOVERABLE_STOP,
                        hostRenderer.summary().reason());
                assertEquals(TableSessionSummary.CloseReason.RECOVERABLE_STOP,
                        witnessRenderer.summary().reason());
                assertEquals(hostRenderer.balancesByNickname(),
                        witnessRenderer.balancesByNickname());
                assertConservedLedger(hostRenderer.summary(), 5);
            } finally {
                witness.close();
                second.close();
                first.close();
                host.close();
            }
        }
    }

    /** Exact GDX homologue of Swing's {@code mixed-exit-crash} scenario. */
    @Test
    void mixedControlledExitAndCrashPreserveTheWitnessAndLedger()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = database("mixed-exit-host.sqlite");
        DatabaseService departingDatabase = database("mixed-exit-clean.sqlite");
        DatabaseService crashedDatabase = database("mixed-exit-crash.sqlite");
        DatabaseService witnessDatabase = database("mixed-exit-witness.sqlite");
        try (hostDatabase; departingDatabase; crashedDatabase; witnessDatabase;
             NetworkLobbyGateway hostGateway = gateway("mixed-exit-host",
                     hostDatabase);
             NetworkLobbyGateway departingGateway = gateway("mixed-exit-clean",
                     departingDatabase);
             NetworkLobbyGateway crashedGateway = gateway("mixed-exit-crash",
                     crashedDatabase);
             NetworkLobbyGateway witnessGateway = gateway(
                     "mixed-exit-witness", witnessDatabase)) {
            LobbySession host = hostGateway.open(
                    request(false, "Anfitrion", port, 1))
                    .get(5, TimeUnit.SECONDS);
            LobbySession departing = departingGateway.open(
                    request(true, "Saliente", port, 1))
                    .get(5, TimeUnit.SECONDS);
            LobbySession crashed = crashedGateway.open(
                    request(true, "Accidentado", port, 1))
                    .get(5, TimeUnit.SECONDS);
            LobbySession witness = witnessGateway.open(
                    request(true, "Testigo", port, 1))
                    .get(5, TimeUnit.SECONDS);
            try {
                host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                await(() -> host.snapshot().participants().size() == 5
                                && departing.snapshot().participants().size() == 5
                                && crashed.snapshot().participants().size() == 5
                                && witness.snapshot().participants().size() == 5,
                        Duration.ofSeconds(6));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                GdxScenarioRenderer hostRenderer = attach(
                        host.tableSession().toCompletableFuture()
                                .get(8, TimeUnit.SECONDS), 5, -1);
                TableSession departingTable = departing.tableSession()
                        .toCompletableFuture().get(12, TimeUnit.SECONDS);
                GdxScenarioRenderer departingRenderer = attach(
                        departingTable, 5, 1);
                attach(crashed.tableSession().toCompletableFuture()
                        .get(12, TimeUnit.SECONDS), 5, -1);
                GdxScenarioRenderer witnessRenderer = attach(
                        witness.tableSession().toCompletableFuture()
                                .get(12, TimeUnit.SECONDS), 5, -1);

                await(departingRenderer::hasHeldAction,
                        Duration.ofSeconds(12));
                departingTable.commands().submit(new TableCommand.ExitGame());
                await(departingRenderer::isClosed, Duration.ofSeconds(12));
                assertEquals(TableSessionSummary.CloseReason.EXITED,
                        departingRenderer.summary().reason());
                closeClientTransport(crashed);

                await(() -> hostRenderer.isClosed()
                                && witnessRenderer.isClosed(),
                        Duration.ofSeconds(150));
                assertEquals(TableSessionSummary.CloseReason.RECOVERABLE_STOP,
                        hostRenderer.summary().reason());
                assertEquals(TableSessionSummary.CloseReason.RECOVERABLE_STOP,
                        witnessRenderer.summary().reason());
                assertEquals(hostRenderer.balancesByNickname(),
                        witnessRenderer.balancesByNickname());
                assertConservedLedger(hostRenderer.summary(), 5);
            } finally {
                witness.close();
                crashed.close();
                departing.close();
                host.close();
            }
        }
    }

    @Test
    void nativeGdxRecoveryReplaysTheRecordedLocalActionAndClosesItsOverlay()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService database = database("native-recovery.sqlite");
        AtomicReference<TableSession> activeTable = new AtomicReference<>();
        AtomicReference<TableCommand> pendingReplay = new AtomicReference<>();
        AtomicReference<GdxTableDialog> recoveryDialog = new AtomicReference<>();
        AtomicInteger replayedActions = new AtomicInteger();
        Object replayLock = new Object();
        GdxGameDecisionSink decisions = new GdxGameDecisionSink(
                GameText.keys(), dialog -> {
                    if (dialog.isExternallyControlled()) {
                        recoveryDialog.set(dialog);
                    } else {
                        dialog.dismiss();
                    }
                }, command -> {
                    replayedActions.incrementAndGet();
                    synchronized (replayLock) {
                        TableSession table = activeTable.get();
                        if (table == null) {
                            pendingReplay.set(command);
                        } else {
                            table.commands().submit(command);
                        }
                    }
                });

        try (database;
             NetworkLobbyGateway gateway
                     = GdxNetworkHumanProjectionIntegrationTest.gateway(
                             temporary.resolve("native-recovery"), database,
                             decisions)) {
            LobbySession initial = gateway.open(
                    request(false, "Anfitrion", port, 2))
                    .get(5, TimeUnit.SECONDS);
            try {
                initial.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                await(() -> initial.snapshot().participants().size() == 2,
                        Duration.ofSeconds(5));
                initial.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TableSession table = initial.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                StopAfterRecordedActionRenderer renderer
                        = new StopAfterRecordedActionRenderer(table);
                table.attach(renderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                await(renderer::heldAfterRecordedAction,
                        Duration.ofSeconds(12));
                table.commands().submit(new TableCommand.StopGame());
                await(renderer::closed, Duration.ofSeconds(10));
                assertEquals(TableSessionSummary.CloseReason.RECOVERABLE_STOP,
                        renderer.summary().reason());
            } finally {
                initial.close();
            }

            RecoverableGameRepository.RecoverableGame recovered
                    = new RecoverableGameRepository(database)
                            .latestLocal().orElseThrow();
            LobbySession resumed = gateway.open(recoveryRequest(
                    "Anfitrion", port, recovered)).get(10, TimeUnit.SECONDS);
            try {
                await(() -> resumed.snapshot().participants().size() == 2,
                        Duration.ofSeconds(5));
                resumed.submit(new LobbyCommand.StartGame())
                        .toCompletableFuture().get(10, TimeUnit.SECONDS);
                TableSession table = resumed.tableSession().toCompletableFuture()
                        .get(15, TimeUnit.SECONDS);
                synchronized (replayLock) {
                    activeTable.set(table);
                    TableCommand pending = pendingReplay.getAndSet(null);
                    if (pending != null) table.commands().submit(pending);
                }
                table.commands().submit(new TableCommand.SetLastHand(true));
                GdxScenarioRenderer renderer = attach(table, 2, -1);

                await(() -> replayedActions.get() > 0,
                        Duration.ofSeconds(15));
                await(renderer::isClosed, Duration.ofSeconds(25));
                assertTrue(recoveryDialog.get() != null,
                        "the native recovery overlay was never presented");
                assertTrue(recoveryDialog.get().complete(),
                        "the dealer did not close the recovery overlay");
                assertTrue(renderer.completedHands() >= 1);
            } finally {
                resumed.close();
            }
        }
    }

    /** Exact GDX homologue of Swing's {@code force-recover} scenario. */
    @Test
    void forceRecoverRebuildsTheNetworkTableAndCompletesTwoHands()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = database("force-recover-host.sqlite");
        DatabaseService clientDatabase = database("force-recover-client.sqlite");
        var hostDirectory = temporary.resolve("force-recover-host");
        var clientDirectory = temporary.resolve("force-recover-client");
        try (hostDatabase; clientDatabase) {
            try (NetworkLobbyGateway hostGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 hostDirectory, hostDatabase);
                 NetworkLobbyGateway clientGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 clientDirectory, clientDatabase)) {
                LobbySession host = hostGateway.open(
                        request(false, "Anfitrion", port, 2))
                        .get(5, TimeUnit.SECONDS);
                LobbySession client = clientGateway.open(
                        request(true, "Invitado", port, 2))
                        .get(5, TimeUnit.SECONDS);
                try {
                    host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    await(() -> host.snapshot().participants().size() == 4
                                    && client.snapshot().participants().size() == 4,
                            Duration.ofSeconds(6));
                    host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    TableSession hostTable = host.tableSession()
                            .toCompletableFuture().get(8, TimeUnit.SECONDS);
                    GdxScenarioRenderer hostRenderer = attach(hostTable, 4, 1);
                    GdxScenarioRenderer clientRenderer = attach(
                            client.tableSession().toCompletableFuture()
                                    .get(12, TimeUnit.SECONDS), 4, -1);
                    await(hostRenderer::hasHeldAction, Duration.ofSeconds(12));
                    hostTable.commands().submit(new TableCommand.StopGame());
                    await(() -> hostRenderer.isClosed()
                                    && clientRenderer.isClosed(),
                            Duration.ofSeconds(20));
                    assertEquals(TableSessionSummary.CloseReason.RECOVERABLE_STOP,
                            hostRenderer.summary().reason());
                    assertEquals(TableSessionSummary.CloseReason.RECOVERABLE_STOP,
                            clientRenderer.summary().reason());
                } finally {
                    client.close();
                    host.close();
                }
            }

            RecoverableGameRepository.RecoverableGame recovered
                    = new RecoverableGameRepository(hostDatabase)
                            .latestLocal().orElseThrow();
            try (NetworkLobbyGateway hostGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 hostDirectory, hostDatabase);
                 NetworkLobbyGateway clientGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 clientDirectory, clientDatabase)) {
                LobbySession host = hostGateway.open(recoveryRequest(
                        "Anfitrion", port, recovered)).get(10, TimeUnit.SECONDS);
                LobbySession client = clientGateway.open(
                        request(true, "Invitado", port, 2))
                        .get(10, TimeUnit.SECONDS);
                try {
                    await(() -> host.snapshot().participants().size() == 4
                                    && client.snapshot().participants().size() == 4,
                            Duration.ofSeconds(8));
                    host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                            .get(10, TimeUnit.SECONDS);
                    GdxScenarioRenderer hostRenderer = attach(
                            host.tableSession().toCompletableFuture()
                                    .get(15, TimeUnit.SECONDS), 4, -1);
                    GdxScenarioRenderer clientRenderer = attach(
                            client.tableSession().toCompletableFuture()
                                    .get(20, TimeUnit.SECONDS), 4, -1);
                    await(() -> hostRenderer.isClosed()
                                    && clientRenderer.isClosed(),
                            Duration.ofSeconds(70));
                    hostRenderer.assertComplete(2);
                    clientRenderer.assertComplete(2);
                    assertEquals(2, hostRenderer.summary().handCount());
                    assertEquals(2, clientRenderer.summary().handCount());
                    assertEquals(hostRenderer.balancesByNickname(),
                            clientRenderer.balancesByNickname());
                    assertConservedLedger(hostRenderer.summary(), 4);
                } finally {
                    client.close();
                    host.close();
                }
            }
        }
    }

    /** Exact GDX homologue of Swing's {@code double-force-recover} scenario. */
    @Test
    void doubleForceRecoverRebuildsHandsOneAndThreeAndCompletesFourHands()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = database("double-recover-host.sqlite");
        DatabaseService clientDatabase = database("double-recover-client.sqlite");
        var hostDirectory = temporary.resolve("double-recover-host");
        var clientDirectory = temporary.resolve("double-recover-client");
        try (hostDatabase; clientDatabase) {
            stopNetworkTableForRecovery(hostDirectory, clientDirectory,
                    hostDatabase, clientDatabase, port, 4, 1, false);
            stopNetworkTableForRecovery(hostDirectory, clientDirectory,
                    hostDatabase, clientDatabase, port, 4, 3, true);
            completeRecoveredNetworkTable(hostDirectory, clientDirectory,
                    hostDatabase, clientDatabase, port, 4, 2);
        }
    }

    /** Exact GDX homologue of Swing's {@code reconnect-force-recover}. */
    @Test
    void reconnectOverlappingForceRecoveryConvergesAcrossEveryGdxPeer()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = database(
                "reconnect-force-recover-host.sqlite");
        DatabaseService firstDatabase = database(
                "reconnect-force-recover-first.sqlite");
        DatabaseService secondDatabase = database(
                "reconnect-force-recover-second.sqlite");
        Path hostDirectory = temporary.resolve("reconnect-force-recover-host");
        Path firstDirectory = temporary.resolve("reconnect-force-recover-first");
        Path secondDirectory = temporary.resolve("reconnect-force-recover-second");
        try (hostDatabase; firstDatabase; secondDatabase) {
            try (NetworkLobbyGateway hostGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 hostDirectory, hostDatabase);
                 NetworkLobbyGateway firstGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 firstDirectory, firstDatabase);
                 NetworkLobbyGateway secondGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 secondDirectory, secondDatabase)) {
                LobbySession host = hostGateway.open(request(
                        false, "Anfitrion", port, 3))
                        .get(10, TimeUnit.SECONDS);
                LobbySession first = firstGateway.open(request(
                        true, "Invitado1", port, 3))
                        .get(10, TimeUnit.SECONDS);
                LobbySession second = secondGateway.open(request(
                        true, "Invitado2", port, 3))
                        .get(10, TimeUnit.SECONDS);
                try {
                    host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    await(() -> host.snapshot().participants().size() == 4
                                    && first.snapshot().participants().size() == 4
                                    && second.snapshot().participants().size() == 4,
                            Duration.ofSeconds(8));
                    host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                            .get(10, TimeUnit.SECONDS);
                    TableSession hostTable = host.tableSession()
                            .toCompletableFuture().get(15, TimeUnit.SECONDS);
                    GdxScenarioRenderer hostRenderer = attach(
                            hostTable, 4, 1);
                    GdxScenarioRenderer firstRenderer = attach(
                            first.tableSession().toCompletableFuture()
                                    .get(20, TimeUnit.SECONDS), 4, -1);
                    GdxScenarioRenderer secondRenderer = attach(
                            second.tableSession().toCompletableFuture()
                                    .get(20, TimeUnit.SECONDS), 4, -1);

                    await(hostRenderer::hasHeldAction, Duration.ofSeconds(15));
                    closeNativeClientSocket(first);
                    // As in the immutable Swing scenario, force recovery only
                    // after the ordinary reconnect machinery has claimed the
                    // dropped generation. Reconnect or teardown may win from
                    // this point; both orderings must converge.
                    await(() -> clientReconnectStarted(first),
                            Duration.ofSeconds(10));
                    hostTable.commands().submit(new TableCommand.StopGame());
                    await(() -> hostRenderer.isClosed()
                                    && firstRenderer.isClosed()
                                    && secondRenderer.isClosed(),
                            Duration.ofSeconds(25));
                    assertEquals(TableSessionSummary.CloseReason.RECOVERABLE_STOP,
                            hostRenderer.summary().reason());
                    assertEquals(TableSessionSummary.CloseReason.RECOVERABLE_STOP,
                            firstRenderer.summary().reason());
                    assertEquals(TableSessionSummary.CloseReason.RECOVERABLE_STOP,
                            secondRenderer.summary().reason());
                } finally {
                    second.close();
                    first.close();
                    host.close();
                }
            }

            OpenHandIdentity opening = latestOpenHandIdentity(hostDatabase);
            assertEquals(opening, latestOpenHandIdentity(firstDatabase));
            assertEquals(opening, latestOpenHandIdentity(secondDatabase));

            RecoverableGameRepository.RecoverableGame recovered
                    = new RecoverableGameRepository(hostDatabase)
                            .latestLocal().orElseThrow();
            try (NetworkLobbyGateway hostGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 hostDirectory, hostDatabase);
                 NetworkLobbyGateway firstGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 firstDirectory, firstDatabase);
                 NetworkLobbyGateway secondGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 secondDirectory, secondDatabase)) {
                LobbySession host = hostGateway.open(recoveryRequest(
                        "Anfitrion", port, recovered)).get(10, TimeUnit.SECONDS);
                LobbySession first = firstGateway.open(request(
                        true, "Invitado1", port, 3))
                        .get(10, TimeUnit.SECONDS);
                LobbySession second = secondGateway.open(request(
                        true, "Invitado2", port, 3))
                        .get(10, TimeUnit.SECONDS);
                try {
                    await(() -> host.snapshot().participants().size() == 4
                                    && first.snapshot().participants().size() == 4
                                    && second.snapshot().participants().size() == 4,
                            Duration.ofSeconds(8));
                    host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                            .get(10, TimeUnit.SECONDS);
                    GdxScenarioRenderer hostRenderer = attach(
                            host.tableSession().toCompletableFuture()
                                    .get(15, TimeUnit.SECONDS), 4, -1);
                    GdxScenarioRenderer firstRenderer = attach(
                            first.tableSession().toCompletableFuture()
                                    .get(20, TimeUnit.SECONDS), 4, -1);
                    GdxScenarioRenderer secondRenderer = attach(
                            second.tableSession().toCompletableFuture()
                                    .get(20, TimeUnit.SECONDS), 4, -1);
                    await(() -> hostRenderer.isClosed()
                                    && firstRenderer.isClosed()
                                    && secondRenderer.isClosed(),
                            Duration.ofSeconds(90));
                    hostRenderer.assertComplete(3);
                    firstRenderer.assertComplete(3);
                    secondRenderer.assertComplete(3);
                    assertEquals(3, hostRenderer.summary().handCount());
                    assertEquals(3, firstRenderer.summary().handCount());
                    assertEquals(3, secondRenderer.summary().handCount());
                    assertEquals(hostRenderer.balancesByNickname(),
                            firstRenderer.balancesByNickname());
                    assertEquals(hostRenderer.balancesByNickname(),
                            secondRenderer.balancesByNickname());
                    assertConservedLedger(hostRenderer.summary(), 4);
                } finally {
                    second.close();
                    first.close();
                    host.close();
                }
            }
        }
    }

    /** Exact GDX homologue of Swing's {@code force-recover-add-client}. */
    @Test
    void forceRecoveryAdmitsNewGdxClientForFreshSecondHand()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = database(
                "force-recover-add-client-host.sqlite");
        DatabaseService incumbentDatabase = database(
                "force-recover-add-client-incumbent.sqlite");
        DatabaseService newcomerDatabase = database(
                "force-recover-add-client-newcomer.sqlite");
        Path hostDirectory = temporary.resolve(
                "force-recover-add-client-host");
        Path incumbentDirectory = temporary.resolve(
                "force-recover-add-client-incumbent");
        Path newcomerDirectory = temporary.resolve(
                "force-recover-add-client-newcomer");
        try (hostDatabase; incumbentDatabase; newcomerDatabase) {
            // Swing starts with one client and both bots. Stop hand 1 at the
            // host's real preflop decision so the original four-seat ring is
            // durable on both incumbent peers.
            try (NetworkLobbyGateway hostGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 hostDirectory, hostDatabase);
                 NetworkLobbyGateway incumbentGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 incumbentDirectory, incumbentDatabase)) {
                LobbySession host = hostGateway.open(request(
                        false, "Anfitrion", port, 2))
                        .get(10, TimeUnit.SECONDS);
                LobbySession incumbent = incumbentGateway.open(request(
                        true, "Invitado1", port, 2))
                        .get(10, TimeUnit.SECONDS);
                try {
                    host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    await(() -> host.snapshot().participants().size() == 4
                                    && incumbent.snapshot().participants().size() == 4,
                            Duration.ofSeconds(8));
                    host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                            .get(10, TimeUnit.SECONDS);
                    TableSession hostTable = host.tableSession()
                            .toCompletableFuture().get(15, TimeUnit.SECONDS);
                    GdxScenarioRenderer hostRenderer = attach(
                            hostTable, 4, 1);
                    GdxScenarioRenderer incumbentRenderer = attach(
                            incumbent.tableSession().toCompletableFuture()
                                    .get(20, TimeUnit.SECONDS), 4, -1);
                    await(hostRenderer::hasHeldAction, Duration.ofSeconds(15));
                    hostTable.commands().submit(new TableCommand.StopGame());
                    await(() -> hostRenderer.isClosed()
                                    && incumbentRenderer.isClosed(),
                            Duration.ofSeconds(25));
                    assertEquals(TableSessionSummary.CloseReason.RECOVERABLE_STOP,
                            hostRenderer.summary().reason());
                    assertEquals(TableSessionSummary.CloseReason.RECOVERABLE_STOP,
                            incumbentRenderer.summary().reason());
                } finally {
                    incumbent.close();
                    host.close();
                }
            }

            OpenHandIdentity opening = latestOpenHandIdentity(hostDatabase);
            assertEquals(opening, latestOpenHandIdentity(incumbentDatabase));

            // Add a second human from a genuinely fresh database to the rebuilt
            // lobby. It must observe recovered hand 1 passively, then join the
            // original members and both bots as a normal player in hand 2.
            RecoverableGameRepository.RecoverableGame recovered
                    = new RecoverableGameRepository(hostDatabase)
                            .latestLocal().orElseThrow();
            try (NetworkLobbyGateway hostGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 hostDirectory, hostDatabase);
                 NetworkLobbyGateway incumbentGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 incumbentDirectory, incumbentDatabase);
                 NetworkLobbyGateway newcomerGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 newcomerDirectory, newcomerDatabase)) {
                LobbySession host = hostGateway.open(recoveryRequest(
                        "Anfitrion", port, recovered)).get(10, TimeUnit.SECONDS);
                LobbySession incumbent = incumbentGateway.open(request(
                        true, "Invitado1", port, 2))
                        .get(10, TimeUnit.SECONDS);
                LobbySession newcomer = newcomerGateway.open(request(
                        true, "Invitado2", port, 1))
                        .get(10, TimeUnit.SECONDS);
                try {
                    await(() -> host.snapshot().participants().size() == 5
                                    && incumbent.snapshot().participants().size() == 5
                                    && newcomer.snapshot().participants().size() == 5,
                            Duration.ofSeconds(10));
                    host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                            .get(10, TimeUnit.SECONDS);
                    GdxScenarioRenderer hostRenderer = attach(
                            host.tableSession().toCompletableFuture()
                                    .get(15, TimeUnit.SECONDS), 5, -1);
                    GdxScenarioRenderer incumbentRenderer = attach(
                            incumbent.tableSession().toCompletableFuture()
                                    .get(20, TimeUnit.SECONDS), 5, -1);
                    GdxScenarioRenderer newcomerRenderer = attach(
                            newcomer.tableSession().toCompletableFuture()
                                    .get(20, TimeUnit.SECONDS), 5, -1);
                    await(() -> hostRenderer.isClosed()
                                    && incumbentRenderer.isClosed()
                                    && newcomerRenderer.isClosed(),
                            Duration.ofSeconds(90));

                    hostRenderer.assertComplete(2);
                    incumbentRenderer.assertComplete(2);
                    // The newcomer has no private material for the old hand:
                    // it sees its boundary but completes only fresh hand 2.
                    newcomerRenderer.assertComplete(1);
                    assertEquals(2, hostRenderer.summary().handCount());
                    assertEquals(2, incumbentRenderer.summary().handCount());
                    assertEquals(2, newcomerRenderer.summary().handCount());
                    assertEquals(hostRenderer.balancesByNickname(),
                            incumbentRenderer.balancesByNickname());
                    assertEquals(hostRenderer.balancesByNickname(),
                            newcomerRenderer.balancesByNickname());
                    assertConservedLedger(hostRenderer.summary(), 5);
                } finally {
                    newcomer.close();
                    incumbent.close();
                    host.close();
                }
            }
        }
    }

    /** Exact GDX homologue of Swing's {@code force-recover-add-two}. */
    @Test
    void forceRecoveryAdmitsTwoNewGdxClientsForFreshSecondHand()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = database(
                "force-recover-add-two-host.sqlite");
        DatabaseService incumbentDatabase = database(
                "force-recover-add-two-incumbent.sqlite");
        DatabaseService firstNewcomerDatabase = database(
                "force-recover-add-two-first-newcomer.sqlite");
        DatabaseService secondNewcomerDatabase = database(
                "force-recover-add-two-second-newcomer.sqlite");
        Path hostDirectory = temporary.resolve("force-recover-add-two-host");
        Path incumbentDirectory = temporary.resolve(
                "force-recover-add-two-incumbent");
        Path firstNewcomerDirectory = temporary.resolve(
                "force-recover-add-two-first-newcomer");
        Path secondNewcomerDirectory = temporary.resolve(
                "force-recover-add-two-second-newcomer");
        try (hostDatabase; incumbentDatabase; firstNewcomerDatabase;
             secondNewcomerDatabase) {
            // Exact reference topology before the cut: host, one existing
            // client and one bot. The two remaining clients do not exist yet.
            try (NetworkLobbyGateway hostGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 hostDirectory, hostDatabase);
                 NetworkLobbyGateway incumbentGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 incumbentDirectory, incumbentDatabase)) {
                LobbySession host = hostGateway.open(request(
                        false, "Anfitrion", port, 2))
                        .get(10, TimeUnit.SECONDS);
                LobbySession incumbent = incumbentGateway.open(request(
                        true, "Invitado1", port, 2))
                        .get(10, TimeUnit.SECONDS);
                try {
                    host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    await(() -> host.snapshot().participants().size() == 3
                                    && incumbent.snapshot().participants().size() == 3,
                            Duration.ofSeconds(8));
                    host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                            .get(10, TimeUnit.SECONDS);
                    TableSession hostTable = host.tableSession()
                            .toCompletableFuture().get(15, TimeUnit.SECONDS);
                    GdxScenarioRenderer hostRenderer = attach(
                            hostTable, 3, 1);
                    GdxScenarioRenderer incumbentRenderer = attach(
                            incumbent.tableSession().toCompletableFuture()
                                    .get(20, TimeUnit.SECONDS), 3, -1);
                    await(hostRenderer::hasHeldAction, Duration.ofSeconds(15));
                    hostTable.commands().submit(new TableCommand.StopGame());
                    await(() -> hostRenderer.isClosed()
                                    && incumbentRenderer.isClosed(),
                            Duration.ofSeconds(25));
                    assertEquals(TableSessionSummary.CloseReason.RECOVERABLE_STOP,
                            hostRenderer.summary().reason());
                    assertEquals(TableSessionSummary.CloseReason.RECOVERABLE_STOP,
                            incumbentRenderer.summary().reason());
                } finally {
                    incumbent.close();
                    host.close();
                }
            }

            OpenHandIdentity opening = latestOpenHandIdentity(hostDatabase);
            assertEquals(opening, latestOpenHandIdentity(incumbentDatabase));

            RecoverableGameRepository.RecoverableGame recovered
                    = new RecoverableGameRepository(hostDatabase)
                            .latestLocal().orElseThrow();
            try (NetworkLobbyGateway hostGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 hostDirectory, hostDatabase);
                 NetworkLobbyGateway incumbentGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 incumbentDirectory, incumbentDatabase);
                 NetworkLobbyGateway firstNewcomerGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 firstNewcomerDirectory, firstNewcomerDatabase);
                 NetworkLobbyGateway secondNewcomerGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 secondNewcomerDirectory, secondNewcomerDatabase)) {
                LobbySession host = hostGateway.open(recoveryRequest(
                        "Anfitrion", port, recovered)).get(10, TimeUnit.SECONDS);
                LobbySession incumbent = incumbentGateway.open(request(
                        true, "Invitado1", port, 2))
                        .get(10, TimeUnit.SECONDS);
                LobbySession firstNewcomer = firstNewcomerGateway.open(request(
                        true, "Invitado2", port, 1))
                        .get(10, TimeUnit.SECONDS);
                LobbySession secondNewcomer = secondNewcomerGateway.open(request(
                        true, "Invitado3", port, 1))
                        .get(10, TimeUnit.SECONDS);
                try {
                    await(() -> host.snapshot().participants().size() == 5
                                    && incumbent.snapshot().participants().size() == 5
                                    && firstNewcomer.snapshot().participants().size() == 5
                                    && secondNewcomer.snapshot().participants().size() == 5,
                            Duration.ofSeconds(10));
                    host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                            .get(10, TimeUnit.SECONDS);
                    GdxScenarioRenderer hostRenderer = attach(
                            host.tableSession().toCompletableFuture()
                                    .get(15, TimeUnit.SECONDS), 5, -1);
                    GdxScenarioRenderer incumbentRenderer = attach(
                            incumbent.tableSession().toCompletableFuture()
                                    .get(20, TimeUnit.SECONDS), 5, -1);
                    GdxScenarioRenderer firstNewcomerRenderer = attach(
                            firstNewcomer.tableSession().toCompletableFuture()
                                    .get(20, TimeUnit.SECONDS), 5, -1);
                    GdxScenarioRenderer secondNewcomerRenderer = attach(
                            secondNewcomer.tableSession().toCompletableFuture()
                                    .get(20, TimeUnit.SECONDS), 5, -1);
                    await(() -> hostRenderer.isClosed()
                                    && incumbentRenderer.isClosed()
                                    && firstNewcomerRenderer.isClosed()
                                    && secondNewcomerRenderer.isClosed(),
                            Duration.ofSeconds(90));

                    hostRenderer.assertComplete(2);
                    incumbentRenderer.assertComplete(2);
                    firstNewcomerRenderer.assertComplete(1);
                    secondNewcomerRenderer.assertComplete(1);
                    assertEquals(2, hostRenderer.summary().handCount());
                    assertEquals(2, incumbentRenderer.summary().handCount());
                    assertEquals(2, firstNewcomerRenderer.summary().handCount());
                    assertEquals(2, secondNewcomerRenderer.summary().handCount());
                    Map<String, Double> balances
                            = hostRenderer.balancesByNickname();
                    assertEquals(balances,
                            incumbentRenderer.balancesByNickname());
                    assertEquals(balances,
                            firstNewcomerRenderer.balancesByNickname());
                    assertEquals(balances,
                            secondNewcomerRenderer.balancesByNickname());
                    assertConservedLedger(hostRenderer.summary(), 5);
                } finally {
                    secondNewcomer.close();
                    firstNewcomer.close();
                    incumbent.close();
                    host.close();
                }
            }
        }
    }

    /** Exact GDX homologue of Swing's {@code force-recover-swap-client}. */
    @Test
    void forceRecoveryReplacesMissingClientAndStartsFreshSecondHand()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = database(
                "force-recover-swap-host.sqlite");
        DatabaseService missingDatabase = database(
                "force-recover-swap-missing.sqlite");
        DatabaseService survivorDatabase = database(
                "force-recover-swap-survivor.sqlite");
        DatabaseService newcomerDatabase = database(
                "force-recover-swap-newcomer.sqlite");
        Path hostDirectory = temporary.resolve("force-recover-swap-host");
        Path missingDirectory = temporary.resolve("force-recover-swap-missing");
        Path survivorDirectory = temporary.resolve("force-recover-swap-survivor");
        Path newcomerDirectory = temporary.resolve("force-recover-swap-newcomer");
        try (hostDatabase; missingDatabase; survivorDatabase; newcomerDatabase) {
            try (NetworkLobbyGateway hostGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 hostDirectory, hostDatabase);
                 NetworkLobbyGateway missingGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 missingDirectory, missingDatabase);
                 NetworkLobbyGateway survivorGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 survivorDirectory, survivorDatabase)) {
                LobbySession host = hostGateway.open(request(
                        false, "Anfitrion", port, 2))
                        .get(10, TimeUnit.SECONDS);
                LobbySession missing = missingGateway.open(request(
                        true, "Invitado1", port, 2))
                        .get(10, TimeUnit.SECONDS);
                LobbySession survivor = survivorGateway.open(request(
                        true, "Invitado2", port, 2))
                        .get(10, TimeUnit.SECONDS);
                try {
                    host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    await(() -> host.snapshot().participants().size() == 4
                                    && missing.snapshot().participants().size() == 4
                                    && survivor.snapshot().participants().size() == 4,
                            Duration.ofSeconds(8));
                    host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                            .get(10, TimeUnit.SECONDS);
                    TableSession hostTable = host.tableSession()
                            .toCompletableFuture().get(15, TimeUnit.SECONDS);
                    GdxScenarioRenderer hostRenderer = attach(
                            hostTable, 4, 1);
                    GdxScenarioRenderer missingRenderer = attach(
                            missing.tableSession().toCompletableFuture()
                                    .get(20, TimeUnit.SECONDS), 4, -1);
                    GdxScenarioRenderer survivorRenderer = attach(
                            survivor.tableSession().toCompletableFuture()
                                    .get(20, TimeUnit.SECONDS), 4, -1);
                    await(hostRenderer::hasHeldAction, Duration.ofSeconds(15));
                    hostTable.commands().submit(new TableCommand.StopGame());
                    await(() -> hostRenderer.isClosed()
                                    && missingRenderer.isClosed()
                                    && survivorRenderer.isClosed(),
                            Duration.ofSeconds(25));
                    assertEquals(TableSessionSummary.CloseReason.RECOVERABLE_STOP,
                            hostRenderer.summary().reason());
                    assertEquals(TableSessionSummary.CloseReason.RECOVERABLE_STOP,
                            missingRenderer.summary().reason());
                    assertEquals(TableSessionSummary.CloseReason.RECOVERABLE_STOP,
                            survivorRenderer.summary().reason());
                } finally {
                    survivor.close();
                    missing.close();
                    host.close();
                }
            }

            OpenHandIdentity opening = latestOpenHandIdentity(hostDatabase);
            assertEquals(opening, latestOpenHandIdentity(missingDatabase));
            assertEquals(opening, latestOpenHandIdentity(survivorDatabase));

            RecoverableGameRepository.RecoverableGame recovered
                    = new RecoverableGameRepository(hostDatabase)
                            .latestLocal().orElseThrow();
            // Invitado1 deliberately never rejoins. Invitado3 is a distinct,
            // fresh identity: without the missing member's SRA material the old
            // hand must be refunded/skipped, never replayed approximately.
            try (NetworkLobbyGateway hostGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 hostDirectory, hostDatabase);
                 NetworkLobbyGateway survivorGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 survivorDirectory, survivorDatabase);
                 NetworkLobbyGateway newcomerGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 newcomerDirectory, newcomerDatabase)) {
                LobbySession host = hostGateway.open(recoveryRequest(
                        "Anfitrion", port, recovered)).get(10, TimeUnit.SECONDS);
                LobbySession survivor = survivorGateway.open(request(
                        true, "Invitado2", port, 2))
                        .get(10, TimeUnit.SECONDS);
                LobbySession newcomer = newcomerGateway.open(request(
                        true, "Invitado3", port, 1))
                        .get(10, TimeUnit.SECONDS);
                try {
                    await(() -> host.snapshot().participants().size() == 4
                                    && survivor.snapshot().participants().size() == 4
                                    && newcomer.snapshot().participants().size() == 4,
                            Duration.ofSeconds(10));
                    host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                            .get(10, TimeUnit.SECONDS);
                    GdxScenarioRenderer hostRenderer = attach(
                            host.tableSession().toCompletableFuture()
                                    .get(15, TimeUnit.SECONDS), 4, -1);
                    GdxScenarioRenderer survivorRenderer = attach(
                            survivor.tableSession().toCompletableFuture()
                                    .get(20, TimeUnit.SECONDS), 4, -1);
                    GdxScenarioRenderer newcomerRenderer = attach(
                            newcomer.tableSession().toCompletableFuture()
                                    .get(20, TimeUnit.SECONDS), 4, -1);
                    await(() -> hostRenderer.isClosed()
                                    && survivorRenderer.isClosed()
                                    && newcomerRenderer.isClosed(),
                            Duration.ofSeconds(75));

                    // No renderer may claim a completed replay of hand 1. All
                    // three complete exactly the newly dealt global hand 2.
                    hostRenderer.assertCompleteWithHistoricalBalances(1, 4, 5);
                    survivorRenderer.assertCompleteWithHistoricalBalances(1, 4, 5);
                    // A newcomer admitted during recovery warms up as a passive
                    // observer until the next hand boundary. The one fresh hand
                    // in this scenario may therefore finish without ever
                    // enabling local poker controls for that renderer.
                    newcomerRenderer.assertCompleteAsPassiveObserver(1, 4, 5);
                    assertEquals(2, hostRenderer.summary().handCount());
                    assertEquals(2, survivorRenderer.summary().handCount());
                    assertEquals(2, newcomerRenderer.summary().handCount());
                    Map<String, Double> balances
                            = hostRenderer.balancesByNickname();
                    // Swing's immutable reference contract retains the missing
                    // peer as an auditor-only balance row.  The GDX model must
                    // preserve that evidence while its table renderer filters
                    // the exited peer out of the four active seats.
                    assertTrue(balances.containsKey("Invitado1"));
                    assertTrue(balances.containsKey("Invitado3"));
                    assertEquals(Set.of("Anfitrion", "Invitado2", "Invitado3",
                                    "CoronaBot$1"),
                            hostRenderer.activeNicknames());
                    assertEquals(hostRenderer.activeNicknames(),
                            survivorRenderer.activeNicknames());
                    assertEquals(hostRenderer.activeNicknames(),
                            newcomerRenderer.activeNicknames());
                    assertEquals(balances,
                            survivorRenderer.balancesByNickname());
                    assertEquals(balances,
                            newcomerRenderer.balancesByNickname());
                    assertConservedLedger(hostRenderer.summary(), 5);
                } finally {
                    newcomer.close();
                    survivor.close();
                    host.close();
                }
            }
        }
    }

    /** Exact GDX homologue of Swing's {@code spectator-rebuy-cycle}. */
    @Test
    void bustedGdxHumanSpectatesThenRebuysAndReturnsToTheActiveRing()
            throws Exception {
        String property = "coronapoker.qa.spectatorOnBrokeNicks";
        String previous = System.getProperty(property);
        System.setProperty(property, "Invitado1,Invitado2");
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        List<DatabaseService> databases = new ArrayList<>();
        List<NetworkLobbyGateway> gateways = new ArrayList<>();
        List<LobbySession> sessions = new ArrayList<>();
        try {
            AtomicInteger rebuyChoices = new AtomicInteger();
            for (int index = 0; index < 4; index++) {
                String nick = index == 0 ? "Anfitrion" : "Invitado" + index;
                DatabaseService database = database(
                        "spectator-rebuy-" + index + ".sqlite");
                databases.add(database);
                NetworkLobbyGateway gateway
                        = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                temporary.resolve("spectator-rebuy-" + index),
                                database,
                                acceptingImmediateRebuyDecisions(rebuyChoices));
                gateways.add(gateway);
                sessions.add(gateway.open(request(index != 0, nick, port, 7))
                        .get(index == 0 ? 5 : 10, TimeUnit.SECONDS));
            }
            LobbySession host = sessions.get(0);
            await(() -> sessions.stream().allMatch(session ->
                            session.snapshot().participants().size() == 4),
                    Duration.ofSeconds(10));
            host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            List<GdxScenarioRenderer> renderers = new ArrayList<>();
            for (int index = 0; index < sessions.size(); index++) {
                TableSession table = sessions.get(index).tableSession()
                        .toCompletableFuture().get(index == 0 ? 10 : 20,
                                TimeUnit.SECONDS);
                GdxScenarioRenderer renderer
                        = new GdxScenarioRenderer(table, 4);
                // Any participant can be the one that the real shuffled hand
                // busts. Gate every surviving local surface at hand four; the
                // first canonical turn reached will hold the table while the
                // actual spectators request their rebuys.
                renderer.gateActionOnHand(4);
                if (index == 1 || index == 2) {
                    renderer.allInOnHand(1);
                }
                renderer.requestImmediateRebuyOnHand(4);
                table.attach(renderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                renderers.add(renderer);
            }

            GdxScenarioRenderer hostRenderer = renderers.get(0);
            await(() -> renderers.stream().anyMatch(
                            GdxScenarioRenderer::hasHeldAction),
                    Duration.ofSeconds(60));
            List<GdxScenarioRenderer> spectators = renderers.stream()
                    .filter(GdxScenarioRenderer::sawLocalSpectator).toList();
            assertFalse(spectators.isEmpty(),
                    "the real all-in hand must produce a busted spectator");
            await(() -> spectators.stream().allMatch(
                            GdxScenarioRenderer::requestedImmediateRebuy),
                    Duration.ofSeconds(30));
            await(() -> rebuyChoices.get() == spectators.size(),
                    Duration.ofSeconds(30));
            await(() -> spectators.stream().allMatch(spectator
                            -> hostRenderer.hasImmediateRebuy(
                                    spectator.localNickname())),
                    Duration.ofSeconds(30));
            List<GdxScenarioRenderer> heldRenderers = renderers.stream()
                    .filter(GdxScenarioRenderer::hasHeldAction).toList();
            assertEquals(1, heldRenderers.size(),
                    "only the current canonical turn may hold the table");
            GdxScenarioRenderer gateOwner = heldRenderers.get(0);
            renderers.stream().filter(renderer -> renderer != gateOwner)
                    .forEach(renderer -> renderer.gateActionOnHand(-1L));
            gateOwner.releaseHeldAction();

            await(() -> renderers.stream().allMatch(
                            GdxScenarioRenderer::isClosed),
                    Duration.ofSeconds(120));
            for (GdxScenarioRenderer spectator : spectators) {
                assertTrue(spectator.requestedImmediateRebuy());
                assertTrue(spectator.returnedAfterSpectating());
            }
            assertEquals(spectators.size(), rebuyChoices.get(),
                    "each spectator must use exactly one real immediate-rebuy decision");
            Map<String, Double> balances = renderers.get(0).balancesByNickname();
            for (GdxScenarioRenderer renderer : renderers) {
                renderer.assertComplete(7);
                assertEquals(balances, renderer.balancesByNickname());
                assertEquals(Set.of("Anfitrion", "Invitado1", "Invitado2",
                                "Invitado3"), renderer.activeNicknames());
            }
            assertTrue(renderers.get(0).summary().balances().stream()
                    .mapToInt(TableSessionSummary.PlayerBalance::rebuyCount)
                    .sum() >= spectators.size());
            assertConservedLedger(renderers.get(0).summary(), 4);
        } finally {
            for (int index = sessions.size() - 1; index >= 0; index--) {
                sessions.get(index).close();
            }
            for (int index = gateways.size() - 1; index >= 0; index--) {
                gateways.get(index).close();
            }
            for (int index = databases.size() - 1; index >= 0; index--) {
                databases.get(index).close();
            }
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }

    /** Exact GDX homologue of Swing's {@code spectator-recovery-mix}. */
    @Test
    void spectatorsSurviveRecoveryRebuyAndTwoNewHumansJoining()
            throws Exception {
        String property = "coronapoker.qa.spectatorOnBrokeNicks";
        String previous = System.getProperty(property);
        System.setProperty(property,
                "Invitado1,Invitado2,Invitado3,Invitado4");
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        List<DatabaseService> databases = new ArrayList<>();
        List<Path> directories = new ArrayList<>();
        try {
            for (int index = 0; index < 7; index++) {
                databases.add(database("spectator-recovery-" + index
                        + ".sqlite"));
                directories.add(temporary.resolve(
                        "spectator-recovery-" + index));
            }

            List<String> spectatorNicks;
            try (NetworkLobbyGateway hostGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 directories.get(0), databases.get(0));
                 NetworkLobbyGateway firstGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 directories.get(1), databases.get(1));
                 NetworkLobbyGateway secondGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 directories.get(2), databases.get(2));
                 NetworkLobbyGateway thirdGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 directories.get(3), databases.get(3));
                 NetworkLobbyGateway fourthGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 directories.get(4), databases.get(4))) {
                List<NetworkLobbyGateway> gateways = List.of(hostGateway,
                        firstGateway, secondGateway, thirdGateway,
                        fourthGateway);
                List<LobbySession> sessions = new ArrayList<>();
                sessions.add(hostGateway.open(request(false, "Anfitrion",
                        port, 7)).get(10, TimeUnit.SECONDS));
                for (int index = 1; index <= 4; index++) {
                    sessions.add(gateways.get(index).open(request(true,
                            "Invitado" + index, port, 7))
                            .get(15, TimeUnit.SECONDS));
                }
                try {
                    LobbySession host = sessions.get(0);
                    host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    await(() -> sessions.stream().allMatch(session ->
                                    session.snapshot().participants().size() == 6),
                            Duration.ofSeconds(12));
                    host.submit(new LobbyCommand.StartGame())
                            .toCompletableFuture().get(10, TimeUnit.SECONDS);

                    List<GdxScenarioRenderer> renderers = new ArrayList<>();
                    for (int index = 0; index < sessions.size(); index++) {
                        TableSession table = sessions.get(index).tableSession()
                                .toCompletableFuture().get(25,
                                        TimeUnit.SECONDS);
                        GdxScenarioRenderer renderer
                                = new GdxScenarioRenderer(table, 6);
                        if (index == 0) {
                            renderer.gateActionOnHand(4);
                        } else {
                            renderer.allInOnHand(1);
                        }
                        table.attach(renderer).toCompletableFuture()
                                .get(5, TimeUnit.SECONDS);
                        renderers.add(renderer);
                    }
                    GdxScenarioRenderer hostRenderer = renderers.get(0);
                    try {
                        await(hostRenderer::hasHeldAction,
                                Duration.ofSeconds(90));
                    } catch (AssertionError timeout) {
                        StringBuilder diagnostic = new StringBuilder(
                                "spectator-recovery-mix action traces");
                        for (int index = 0; index < renderers.size(); index++) {
                            diagnostic.append("\nnode ").append(index)
                                    .append(" ")
                                    .append(renderers.get(index).localNickname())
                                    .append(": ")
                                    .append(renderers.get(index).actionTrace());
                        }
                        throw new AssertionError(diagnostic.toString(), timeout);
                    }
                    spectatorNicks = renderers.subList(1, 5).stream()
                            .filter(GdxScenarioRenderer::sawLocalSpectator)
                            .map(GdxScenarioRenderer::localNickname).toList();
                    assertTrue(spectatorNicks.size() >= 2,
                            "at least two forced all-in humans must spectate");
                    sessions.get(0).tableSession().toCompletableFuture()
                            .get(5, TimeUnit.SECONDS).commands()
                            .submit(new TableCommand.StopGame());
                    await(() -> renderers.stream().allMatch(
                                    GdxScenarioRenderer::isClosed),
                            Duration.ofSeconds(35));
                    for (GdxScenarioRenderer renderer : renderers) {
                        assertEquals(
                                TableSessionSummary.CloseReason.RECOVERABLE_STOP,
                                renderer.summary().reason());
                    }
                } finally {
                    for (int index = sessions.size() - 1;
                            index >= 0; index--) {
                        sessions.get(index).close();
                    }
                }
            }

            RecoverableGameRepository.RecoverableGame recovered
                    = new RecoverableGameRepository(databases.get(0))
                            .latestLocal().orElseThrow();
            AtomicInteger rebuyChoices = new AtomicInteger();
            List<NetworkLobbyGateway> gateways = new ArrayList<>();
            List<LobbySession> sessions = new ArrayList<>();
            try {
                for (int index = 0; index < 7; index++) {
                    GameDecisionSink decisions = index > 0 && index <= 4
                            ? acceptingImmediateRebuyDecisions(rebuyChoices)
                            : GameDecisionSink.noop();
                    gateways.add(
                            GdxNetworkHumanProjectionIntegrationTest.gateway(
                                    directories.get(index),
                                    databases.get(index), decisions));
                }
                LobbySession host = gateways.get(0).open(recoveryRequest(
                        "Anfitrion", port, recovered))
                        .get(15, TimeUnit.SECONDS);
                sessions.add(host);
                for (int index = 1; index < 7; index++) {
                    int requestedHands = index <= 4 ? 7 : 3;
                    sessions.add(gateways.get(index).open(request(true,
                            "Invitado" + index, port, requestedHands))
                            .get(20, TimeUnit.SECONDS));
                }
                await(() -> sessions.stream().allMatch(session ->
                                session.snapshot().participants().size() == 8),
                        Duration.ofSeconds(18));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(15, TimeUnit.SECONDS);

                List<GdxScenarioRenderer> renderers = new ArrayList<>();
                for (int index = 0; index < sessions.size(); index++) {
                    TableSession table = sessions.get(index).tableSession()
                            .toCompletableFuture().get(35, TimeUnit.SECONDS);
                    GdxScenarioRenderer renderer
                            = new GdxScenarioRenderer(table, 8);
                    if (index == 0) {
                        renderer.gateActionOnHand(4);
                    }
                    if (spectatorNicks.contains("Invitado" + index)) {
                        renderer.requestImmediateRebuyOnHand(4);
                    }
                    table.attach(renderer).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    renderers.add(renderer);
                }

                GdxScenarioRenderer hostRenderer = renderers.get(0);
                await(hostRenderer::hasHeldAction, Duration.ofSeconds(90));
                List<GdxScenarioRenderer> spectators = renderers.stream()
                        .filter(renderer -> spectatorNicks.contains(
                                renderer.localNickname())).toList();
                spectators.forEach(
                        GdxScenarioRenderer::requestImmediateRebuyNow);
                await(() -> spectators.stream().allMatch(
                                GdxScenarioRenderer::requestedImmediateRebuy),
                        Duration.ofSeconds(45));
                await(() -> rebuyChoices.get() == spectators.size(),
                        Duration.ofSeconds(30));
                await(() -> spectators.stream().allMatch(spectator
                                -> hostRenderer.hasImmediateRebuy(
                                        spectator.localNickname())),
                        Duration.ofSeconds(30));
                hostRenderer.releaseHeldAction();

                await(() -> renderers.stream().allMatch(
                                GdxScenarioRenderer::isClosed),
                        Duration.ofSeconds(210));
                Map<String, Double> balances
                        = hostRenderer.balancesByNickname();
                for (int index = 0; index < renderers.size(); index++) {
                    GdxScenarioRenderer renderer = renderers.get(index);
                    renderer.assertComplete(index <= 4 ? 4 : 3);
                    assertEquals(7, renderer.summary().handCount());
                    assertEquals(balances, renderer.balancesByNickname());
                    assertEquals(Set.of("Anfitrion", "Invitado1",
                                    "Invitado2", "Invitado3", "Invitado4",
                                    "Invitado5", "Invitado6", "CoronaBot$1"),
                            renderer.activeNicknames());
                }
                for (GdxScenarioRenderer spectator : spectators) {
                    assertTrue(spectator.returnedAfterSpectating());
                }
                assertTrue(hostRenderer.summary().balances().stream()
                        .mapToInt(TableSessionSummary.PlayerBalance::rebuyCount)
                        .sum() >= spectators.size());
                assertConservedLedger(hostRenderer.summary(), 8);
            } finally {
                for (int index = sessions.size() - 1;
                        index >= 0; index--) {
                    sessions.get(index).close();
                }
                for (int index = gateways.size() - 1;
                        index >= 0; index--) {
                    gateways.get(index).close();
                }
            }
        } finally {
            for (int index = databases.size() - 1; index >= 0; index--) {
                databases.get(index).close();
            }
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }

    /**
     * Exact GDX homologue of Swing's
     * {@code human-bust-exit-rejoin-rebuy} scenario.
     *
     * <p>A genuinely busted network human remains connected as a spectator,
     * exits cleanly while hand four is held at a real decision boundary,
     * rejoins with the same persistent Ed25519 identity, enters the recovered
     * hand as a spectator and uses the ordinary immediate-rebuy command to
     * return to the active ring at the next canonical hand boundary.</p>
     */
    @Test
    void bustedHumanExitsRejoinsWithSameIdentityAndRebuysAfterRecovery()
            throws Exception {
        String property = "coronapoker.qa.spectatorOnBrokeNicks";
        String previous = System.getProperty(property);
        System.setProperty(property, "Invitado1,Invitado2");
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        List<DatabaseService> databases = new ArrayList<>();
        List<Path> directories = new ArrayList<>();
        String departingNick = null;
        byte[] departingPublicKey = null;
        try {
            for (int index = 0; index < 4; index++) {
                databases.add(database("human-bust-rejoin-" + index
                        + ".sqlite"));
                directories.add(temporary.resolve("human-bust-rejoin-"
                        + index));
            }

            try (NetworkLobbyGateway hostGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 directories.get(0), databases.get(0));
                 NetworkLobbyGateway firstGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 directories.get(1), databases.get(1));
                 NetworkLobbyGateway secondGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 directories.get(2), databases.get(2));
                 NetworkLobbyGateway thirdGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 directories.get(3), databases.get(3))) {
                List<LobbySession> sessions = new ArrayList<>();
                sessions.add(hostGateway.open(request(false, "Anfitrion",
                        port, 7)).get(10, TimeUnit.SECONDS));
                sessions.add(firstGateway.open(request(true, "Invitado1",
                        port, 7)).get(15, TimeUnit.SECONDS));
                sessions.add(secondGateway.open(request(true, "Invitado2",
                        port, 7)).get(15, TimeUnit.SECONDS));
                sessions.add(thirdGateway.open(request(true, "Invitado3",
                        port, 7)).get(15, TimeUnit.SECONDS));
                try {
                    LobbySession host = sessions.get(0);
                    host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    await(() -> sessions.stream().allMatch(session ->
                                    session.snapshot().participants().size() == 5),
                            Duration.ofSeconds(12));
                    host.submit(new LobbyCommand.StartGame())
                            .toCompletableFuture().get(10, TimeUnit.SECONDS);

                    List<GdxScenarioRenderer> renderers = new ArrayList<>();
                    for (int index = 0; index < sessions.size(); index++) {
                        TableSession table = sessions.get(index).tableSession()
                                .toCompletableFuture().get(25,
                                        TimeUnit.SECONDS);
                        GdxScenarioRenderer renderer
                                = new GdxScenarioRenderer(table, 5);
                        if (index == 0) {
                            renderer.gateActionOnHand(4);
                        } else if (index == 1 || index == 2) {
                            renderer.allInOnHand(1);
                        }
                        table.attach(renderer).toCompletableFuture()
                                .get(5, TimeUnit.SECONDS);
                        renderers.add(renderer);
                    }

                    GdxScenarioRenderer hostRenderer = renderers.get(0);
                    await(hostRenderer::hasHeldAction,
                            Duration.ofSeconds(90));
                    List<GdxScenarioRenderer> bustedHumans
                            = renderers.subList(1, 3).stream()
                                    .filter(GdxScenarioRenderer::sawLocalSpectator)
                                    .toList();
                    assertFalse(bustedHumans.isEmpty(),
                            "at least one forced all-in human must bust");
                    GdxScenarioRenderer departing = bustedHumans.get(0);
                    departingNick = departing.localNickname();
                    int departingIndex = Integer.parseInt(
                            departingNick.substring("Invitado".length()));
                    departingPublicKey = PlayerIdentity.loadOrCreate(
                            directories.get(departingIndex), departingNick)
                            .publicKey();

                    TableSession departingTable = sessions.get(departingIndex)
                            .tableSession().toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    departingTable.commands().submit(
                            new TableCommand.ExitGame());
                    await(departing::isClosed, Duration.ofSeconds(30));
                    assertEquals(TableSessionSummary.CloseReason.EXITED,
                            departing.summary().reason());

                    sessions.get(0).tableSession().toCompletableFuture()
                            .get(5, TimeUnit.SECONDS).commands()
                            .submit(new TableCommand.StopGame());
                    await(() -> renderers.stream()
                                    .filter(renderer -> renderer != departing)
                                    .allMatch(GdxScenarioRenderer::isClosed),
                            Duration.ofSeconds(40));
                    for (GdxScenarioRenderer renderer : renderers) {
                        if (renderer != departing) {
                            assertEquals(
                                    TableSessionSummary.CloseReason.RECOVERABLE_STOP,
                                    renderer.summary().reason());
                        }
                    }
                } finally {
                    for (int index = sessions.size() - 1;
                            index >= 0; index--) {
                        sessions.get(index).close();
                    }
                }
            }

            assertNotNull(departingNick);
            assertNotNull(departingPublicKey);
            String rejoinedNick = departingNick;
            RecoverableGameRepository.RecoverableGame recovered
                    = new RecoverableGameRepository(databases.get(0))
                            .latestLocal().orElseThrow();
            AtomicInteger rebuyChoices = new AtomicInteger();
            List<NetworkLobbyGateway> gateways = new ArrayList<>();
            List<LobbySession> sessions = new ArrayList<>();
            try {
                for (int index = 0; index < 4; index++) {
                    String nick = index == 0 ? "Anfitrion"
                            : "Invitado" + index;
                    GameDecisionSink decisions = nick.equals(rejoinedNick)
                            ? acceptingImmediateRebuyDecisions(rebuyChoices)
                            : GameDecisionSink.noop();
                    gateways.add(
                            GdxNetworkHumanProjectionIntegrationTest.gateway(
                                    directories.get(index), databases.get(index),
                                    decisions));
                }

                LobbySession host = gateways.get(0).open(recoveryRequest(
                        "Anfitrion", port, recovered))
                        .get(15, TimeUnit.SECONDS);
                sessions.add(host);
                for (int index = 1; index < 4; index++) {
                    sessions.add(gateways.get(index).open(request(true,
                            "Invitado" + index, port, 7))
                            .get(20, TimeUnit.SECONDS));
                }
                // The recovery lobby itself must reconstruct the surviving bot;
                // no test-only AddBot command is allowed here.
                await(() -> sessions.stream().allMatch(session ->
                                session.snapshot().participants().size() == 5),
                        Duration.ofSeconds(18));
                assertArrayEquals(departingPublicKey,
                        PlayerIdentity.loadOrCreate(
                                directories.get(Integer.parseInt(
                                        rejoinedNick.substring(
                                                "Invitado".length()))),
                                rejoinedNick).publicKey(),
                        "rejoining must reuse the original Ed25519 identity");

                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(15, TimeUnit.SECONDS);
                List<GdxScenarioRenderer> renderers = new ArrayList<>();
                GdxScenarioRenderer rejoinedRenderer = null;
                for (int index = 0; index < sessions.size(); index++) {
                    TableSession table = sessions.get(index).tableSession()
                            .toCompletableFuture().get(35, TimeUnit.SECONDS);
                    GdxScenarioRenderer renderer
                            = new GdxScenarioRenderer(table, 5);
                    if (index == 0) {
                        renderer.gateActionOnHand(4);
                    }
                    table.attach(renderer).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    renderers.add(renderer);
                    if (renderer.localNickname().equals(rejoinedNick)) {
                        rejoinedRenderer = renderer;
                    }
                }
                assertNotNull(rejoinedRenderer);
                GdxScenarioRenderer selectedRejoined = rejoinedRenderer;
                GdxScenarioRenderer hostRenderer = renderers.get(0);
                await(hostRenderer::hasHeldAction,
                        Duration.ofSeconds(90));
                await(selectedRejoined::sawLocalSpectator,
                        Duration.ofSeconds(45));
                selectedRejoined.requestImmediateRebuyNow();
                await(selectedRejoined::requestedImmediateRebuy,
                        Duration.ofSeconds(30));
                await(() -> rebuyChoices.get() == 1,
                        Duration.ofSeconds(30));
                await(() -> hostRenderer.hasImmediateRebuy(rejoinedNick),
                        Duration.ofSeconds(30));
                hostRenderer.releaseHeldAction();

                await(selectedRejoined::returnedAfterSpectating,
                        Duration.ofSeconds(90));
                assertTrue(selectedRejoined.playingNicknames().contains(
                        rejoinedNick));
                assertTrue(selectedRejoined.sawSpectatorReactivated(
                        rejoinedNick));
                await(() -> renderers.stream().allMatch(
                                GdxScenarioRenderer::isClosed),
                        Duration.ofSeconds(210));
                assertEquals(1, rebuyChoices.get(),
                        "the rejoined human must accept exactly one real rebuy");

                Map<String, Double> balances
                        = hostRenderer.balancesByNickname();
                Set<String> expectedRoster = Set.of("Anfitrion", "Invitado1",
                        "Invitado2", "Invitado3", "CoronaBot$1");
                for (GdxScenarioRenderer renderer : renderers) {
                    assertEquals(4, renderer.completedHands());
                    assertEquals(7, renderer.summary().handCount());
                    assertEquals(balances, renderer.balancesByNickname());
                    assertEquals(expectedRoster, renderer.activeNicknames());
                }
                assertConservedLedger(hostRenderer.summary(), 5);
            } finally {
                for (int index = sessions.size() - 1;
                        index >= 0; index--) {
                    sessions.get(index).close();
                }
                for (int index = gateways.size() - 1;
                        index >= 0; index--) {
                    gateways.get(index).close();
                }
            }
        } finally {
            for (int index = databases.size() - 1; index >= 0; index--) {
                databases.get(index).close();
            }
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }

    /**
     * Exact GDX homologue of Swing's
     * {@code spectator-double-recovery-crash-mix} scenario.
     */
    @Test
    void spectatorsAndNewcomersSurviveTwoRecoveriesAndARealClientCrash()
            throws Exception {
        String property = "coronapoker.qa.spectatorOnBrokeNicks";
        String previous = System.getProperty(property);
        System.setProperty(property, "Invitado1,Invitado2");
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        List<DatabaseService> databases = new ArrayList<>();
        List<Path> directories = new ArrayList<>();
        Set<String> ruinedSpectators;
        byte[] crashTargetPublicKey;
        try {
            for (int index = 0; index < 7; index++) {
                databases.add(database("spectator-double-recovery-" + index
                        + ".sqlite"));
                directories.add(temporary.resolve(
                        "spectator-double-recovery-" + index));
            }

            // Phase one is the original six-seat table. Invitado1 and
            // Invitado2 take the same forced all-ins as Swing while all other
            // human controls fold until a real busted spectator exists.
            List<NetworkLobbyGateway> gateways = new ArrayList<>();
            List<LobbySession> sessions = new ArrayList<>();
            try {
                for (int index = 0; index < 5; index++) {
                    gateways.add(
                            GdxNetworkHumanProjectionIntegrationTest.gateway(
                                    directories.get(index), databases.get(index)));
                }
                sessions.add(gateways.get(0).open(request(false, "Anfitrion",
                        port, 8)).get(10, TimeUnit.SECONDS));
                for (int index = 1; index < 5; index++) {
                    sessions.add(gateways.get(index).open(request(true,
                            "Invitado" + index, port, 8))
                            .get(15, TimeUnit.SECONDS));
                }
                LobbySession host = sessions.get(0);
                host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                List<LobbySession> initialSessions = List.copyOf(sessions);
                await(() -> initialSessions.stream().allMatch(session ->
                                session.snapshot().participants().size() == 6),
                        Duration.ofSeconds(12));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(10, TimeUnit.SECONDS);

                List<GdxScenarioRenderer> renderers = new ArrayList<>();
                for (int index = 0; index < sessions.size(); index++) {
                    TableSession table = sessions.get(index).tableSession()
                            .toCompletableFuture().get(25, TimeUnit.SECONDS);
                    GdxScenarioRenderer renderer
                            = new GdxScenarioRenderer(table, 6);
                    if (index == 1 || index == 2) {
                        renderer.allInOnHand(1);
                    } else {
                        renderer.foldAutomatically(true);
                    }
                    if (index == 3) {
                        renderer.gateActionOnHand(4);
                    }
                    table.attach(renderer).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    renderers.add(renderer);
                }
                GdxScenarioRenderer hostRenderer = renderers.get(0);
                AtomicLong spectatorSetupHand = new AtomicLong(1L);
                await(() -> hostRenderer.spectatorNicknames().stream()
                                .anyMatch(nick -> nick.equals("Invitado1")
                                || nick.equals("Invitado2"))
                        || rearmSpectatorSetupAfterSplitPot(renderers,
                                hostRenderer, spectatorSetupHand),
                        Duration.ofSeconds(90));
                ruinedSpectators = hostRenderer.spectatorNicknames().stream()
                        .filter(nick -> nick.equals("Invitado1")
                        || nick.equals("Invitado2"))
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
                assertFalse(ruinedSpectators.isEmpty());
                renderers.forEach(renderer -> renderer.foldAutomatically(false));
                GdxScenarioRenderer crashTargetRenderer = renderers.get(3);
                await(crashTargetRenderer::hasHeldAction,
                        Duration.ofSeconds(90));
                assertTrue(java.util.Collections.disjoint(ruinedSpectators,
                        hostRenderer.playingNicknames()));
                assertTrue(hostRenderer.playingNicknames().contains(
                        "Invitado3"));
                crashTargetPublicKey = PlayerIdentity.loadOrCreate(
                        directories.get(3), "Invitado3").publicKey();

                host.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS).commands()
                        .submit(new TableCommand.StopGame());
                await(() -> renderers.stream().allMatch(
                                GdxScenarioRenderer::isClosed),
                        Duration.ofSeconds(40));
                for (GdxScenarioRenderer renderer : renderers) {
                    assertEquals(TableSessionSummary.CloseReason.RECOVERABLE_STOP,
                            renderer.summary().reason());
                }
            } finally {
                for (int index = sessions.size() - 1;
                        index >= 0; index--) {
                    sessions.get(index).close();
                }
                for (int index = gateways.size() - 1;
                        index >= 0; index--) {
                    gateways.get(index).close();
                }
            }

            // Phase two rebuilds the still-open hand four, adds two genuinely
            // new humans and then cuts Invitado3's complete transport while its
            // real preflop decision is held. The host must convert that loss
            // into the same recoverable MISDEAL boundary as Swing.
            RecoverableGameRepository.RecoverableGame firstRecovery
                    = new RecoverableGameRepository(databases.get(0))
                            .latestLocal().orElseThrow();
            gateways = new ArrayList<>();
            sessions = new ArrayList<>();
            try {
                for (int index = 0; index < 7; index++) {
                    gateways.add(
                            GdxNetworkHumanProjectionIntegrationTest.gateway(
                                    directories.get(index), databases.get(index)));
                }
                LobbySession host = gateways.get(0).open(recoveryRequest(
                        "Anfitrion", port, firstRecovery))
                        .get(15, TimeUnit.SECONDS);
                sessions.add(host);
                for (int index = 1; index < 7; index++) {
                    sessions.add(gateways.get(index).open(request(true,
                            "Invitado" + index, port, 8))
                            .get(20, TimeUnit.SECONDS));
                }
                List<LobbySession> firstRecoverySessions
                        = List.copyOf(sessions);
                await(() -> firstRecoverySessions.stream().allMatch(session ->
                                session.snapshot().participants().size() == 8),
                        Duration.ofSeconds(18));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(15, TimeUnit.SECONDS);

                List<GdxScenarioRenderer> renderers = new ArrayList<>();
                for (int index = 0; index < sessions.size(); index++) {
                    TableSession table = sessions.get(index).tableSession()
                            .toCompletableFuture().get(35, TimeUnit.SECONDS);
                    GdxScenarioRenderer renderer
                            = new GdxScenarioRenderer(table, 8);
                    if (index == 3) {
                        renderer.gateActionOnHand(4);
                    }
                    table.attach(renderer).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    renderers.add(renderer);
                }
                GdxScenarioRenderer hostRenderer = renderers.get(0);
                GdxScenarioRenderer crashTargetRenderer = renderers.get(3);
                await(crashTargetRenderer::hasHeldAction,
                        Duration.ofSeconds(90));
                await(() -> ruinedSpectators.stream().allMatch(
                                hostRenderer.spectatorNicknames()::contains),
                        Duration.ofSeconds(30));
                await(() -> renderers.get(5).sawLocalSpectator()
                                && renderers.get(6).sawLocalSpectator(),
                        Duration.ofSeconds(30));
                assertTrue(hostRenderer.playingNicknames().contains(
                        "Invitado3"));

                closeClientTransport(sessions.get(3));
                await(() -> renderers.stream()
                                .filter(renderer -> renderer
                                != crashTargetRenderer)
                                .allMatch(GdxScenarioRenderer::isClosed),
                        Duration.ofSeconds(150));
                for (GdxScenarioRenderer renderer : renderers) {
                    if (renderer != crashTargetRenderer) {
                        assertEquals(
                                TableSessionSummary.CloseReason.RECOVERABLE_STOP,
                                renderer.summary().reason());
                    }
                }
            } finally {
                for (int index = sessions.size() - 1;
                        index >= 0; index--) {
                    sessions.get(index).close();
                }
                for (int index = gateways.size() - 1;
                        index >= 0; index--) {
                    gateways.get(index).close();
                }
            }

            // Phase three restarts the crashed identity and every surviving
            // peer. Hand four is already atomically refunded; therefore the
            // first actionable recovered boundary is fresh hand five.
            RecoverableGameRepository.RecoverableGame secondRecovery
                    = new RecoverableGameRepository(databases.get(0))
                            .latestLocal().orElseThrow();
            gateways = new ArrayList<>();
            sessions = new ArrayList<>();
            try {
                for (int index = 0; index < 7; index++) {
                    gateways.add(
                            GdxNetworkHumanProjectionIntegrationTest.gateway(
                                    directories.get(index), databases.get(index)));
                }
                LobbySession host = gateways.get(0).open(recoveryRequest(
                        "Anfitrion", port, secondRecovery))
                        .get(15, TimeUnit.SECONDS);
                sessions.add(host);
                for (int index = 1; index < 7; index++) {
                    sessions.add(gateways.get(index).open(request(true,
                            "Invitado" + index, port, 8))
                            .get(20, TimeUnit.SECONDS));
                }
                List<LobbySession> secondRecoverySessions
                        = List.copyOf(sessions);
                await(() -> secondRecoverySessions.stream().allMatch(session ->
                                session.snapshot().participants().size() == 8),
                        Duration.ofSeconds(18));
                assertArrayEquals(crashTargetPublicKey,
                        PlayerIdentity.loadOrCreate(directories.get(3),
                                "Invitado3").publicKey(),
                        "crashed client must retain its Ed25519 identity");
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(15, TimeUnit.SECONDS);

                List<GdxScenarioRenderer> renderers = new ArrayList<>();
                for (int index = 0; index < sessions.size(); index++) {
                    TableSession table = sessions.get(index).tableSession()
                            .toCompletableFuture().get(35, TimeUnit.SECONDS);
                    GdxScenarioRenderer renderer
                            = new GdxScenarioRenderer(table, 8);
                    if (index == 3) {
                        renderer.gateActionOnHand(5);
                    }
                    table.attach(renderer).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    renderers.add(renderer);
                }
                GdxScenarioRenderer hostRenderer = renderers.get(0);
                GdxScenarioRenderer restartedRenderer = renderers.get(3);
                await(restartedRenderer::hasHeldAction,
                        Duration.ofSeconds(90));
                await(() -> ruinedSpectators.stream().allMatch(
                                hostRenderer.spectatorNicknames()::contains),
                        Duration.ofSeconds(30));
                await(() -> hostRenderer.playingNicknames().containsAll(
                                Set.of("Invitado3", "Invitado5", "Invitado6")),
                        Duration.ofSeconds(30));
                assertTrue(java.util.Collections.disjoint(ruinedSpectators,
                        hostRenderer.playingNicknames()));
                restartedRenderer.releaseHeldAction();

                await(() -> renderers.stream().allMatch(
                                GdxScenarioRenderer::isClosed),
                        Duration.ofSeconds(240));
                Map<String, Double> balances
                        = hostRenderer.balancesByNickname();
                Set<String> expectedRoster = Set.of("Anfitrion", "Invitado1",
                        "Invitado2", "Invitado3", "Invitado4", "Invitado5",
                        "Invitado6", "CoronaBot$1");
                for (GdxScenarioRenderer renderer : renderers) {
                    assertEquals(4, renderer.completedHands());
                    assertEquals(8, renderer.summary().handCount());
                    assertEquals(balances, renderer.balancesByNickname());
                    assertEquals(expectedRoster, renderer.activeNicknames());
                }
                assertConservedLedger(hostRenderer.summary(), 8);
            } finally {
                for (int index = sessions.size() - 1;
                        index >= 0; index--) {
                    sessions.get(index).close();
                }
                for (int index = gateways.size() - 1;
                        index >= 0; index--) {
                    gateways.get(index).close();
                }
            }
        } finally {
            for (int index = databases.size() - 1; index >= 0; index--) {
                databases.get(index).close();
            }
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }

    /**
     * Repeats only the spectator-creation setup when the two designated
     * all-in players split the pot.  The production shuffle is intentionally
     * non-deterministic, so a board tie must not turn this recovery scenario
     * into a random timeout before either target player is actually ruined.
     */
    private static boolean rearmSpectatorSetupAfterSplitPot(
            List<GdxScenarioRenderer> renderers,
            GdxScenarioRenderer hostRenderer, AtomicLong armedHand) {
        long currentHand = hostRenderer.currentHand();
        long previousHand = armedHand.get();
        if (currentHand <= previousHand
                || !armedHand.compareAndSet(previousHand, currentHand)) {
            return false;
        }
        renderers.get(1).allInOnHand(currentHand);
        renderers.get(2).allInOnHand(currentHand);
        return false;
    }

    /** Exact GDX homologue of Swing's {@code transport-chaos} scenario. */
    @Test
    void transportChaosConvergesAfterDualCutRelapsePauseRecoveryAndLaterCut()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        List<DatabaseService> databases = new ArrayList<>();
        List<Path> directories = new ArrayList<>();
        try {
            for (int index = 0; index < 4; index++) {
                databases.add(database("transport-chaos-" + index + ".sqlite"));
                directories.add(temporary.resolve("transport-chaos-" + index));
            }

            // Swing first cuts two peers together, immediately cuts the first
            // peer's replacement socket again, then pauses and force-recovers
            // while the host owns the held hand-two decision.
            List<NetworkLobbyGateway> gateways = new ArrayList<>();
            List<LobbySession> sessions = new ArrayList<>();
            try {
                for (int index = 0; index < 4; index++) {
                    gateways.add(
                            GdxNetworkHumanProjectionIntegrationTest.gateway(
                                    directories.get(index), databases.get(index)));
                }
                sessions.add(gateways.get(0).open(request(false, "Anfitrion",
                        port, 5)).get(10, TimeUnit.SECONDS));
                for (int index = 1; index < 4; index++) {
                    sessions.add(gateways.get(index).open(request(true,
                            "Invitado" + index, port, 5))
                            .get(15, TimeUnit.SECONDS));
                }
                LobbySession host = sessions.get(0);
                host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                List<LobbySession> initialSessions = List.copyOf(sessions);
                await(() -> initialSessions.stream().allMatch(session ->
                                session.snapshot().participants().size() == 5),
                        Duration.ofSeconds(12));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(10, TimeUnit.SECONDS);

                List<GdxScenarioRenderer> renderers = new ArrayList<>();
                for (int index = 0; index < sessions.size(); index++) {
                    TableSession table = sessions.get(index).tableSession()
                            .toCompletableFuture().get(25, TimeUnit.SECONDS);
                    GdxScenarioRenderer renderer
                            = new GdxScenarioRenderer(table, 5);
                    if (index == 0) {
                        renderer.gateActionOnHand(2);
                    } else if (index == 1) {
                        renderer.gateActionOnHand(1);
                    }
                    table.attach(renderer).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    renderers.add(renderer);
                }
                GdxScenarioRenderer hostRenderer = renderers.get(0);
                GdxScenarioRenderer firstRenderer = renderers.get(1);

                await(firstRenderer::hasHeldAction, Duration.ofSeconds(20));
                closeNativeClientSocket(sessions.get(1));
                closeNativeClientSocket(sessions.get(2));
                LobbySession first = sessions.get(1);
                LobbySession second = sessions.get(2);
                await(() -> peerReconnectionCount(host, "Invitado1") == 1
                                && peerReconnectionCount(first, "Anfitrion") == 1
                                && peerReconnectionCount(host, "Invitado2") == 1
                                && peerReconnectionCount(second, "Anfitrion") == 1,
                        Duration.ofSeconds(30));
                closeNativeClientSocket(first);
                await(() -> peerReconnectionCount(host, "Invitado1") == 2
                                && peerReconnectionCount(first, "Anfitrion") == 2,
                        Duration.ofSeconds(30));
                firstRenderer.releaseHeldAction();

                await(hostRenderer::hasHeldAction, Duration.ofSeconds(45));
                host.tableSession().toCompletableFuture().get(5, TimeUnit.SECONDS)
                        .commands().submit(new TableCommand.TogglePause());
                await(() -> renderers.stream().allMatch(
                                GdxScenarioRenderer::sawPaused)
                                && renderers.stream().allMatch(
                                GdxScenarioRenderer::isPaused),
                        Duration.ofSeconds(20));
                host.tableSession().toCompletableFuture().get(5, TimeUnit.SECONDS)
                        .commands().submit(new TableCommand.TogglePause());
                await(() -> renderers.stream().allMatch(
                                GdxScenarioRenderer::resumedAfterPause)
                                && renderers.stream().noneMatch(
                                GdxScenarioRenderer::isPaused),
                        Duration.ofSeconds(20));
                host.tableSession().toCompletableFuture().get(5, TimeUnit.SECONDS)
                        .commands().submit(new TableCommand.StopGame());
                await(() -> renderers.stream().allMatch(
                                GdxScenarioRenderer::isClosed),
                        Duration.ofSeconds(45));
                for (GdxScenarioRenderer renderer : renderers) {
                    assertEquals(TableSessionSummary.CloseReason.RECOVERABLE_STOP,
                            renderer.summary().reason());
                }
                assertEquals(2, peerReconnectionCount(host, "Invitado1"));
                assertEquals(1, peerReconnectionCount(host, "Invitado2"));
            } finally {
                for (int index = sessions.size() - 1; index >= 0; index--) {
                    sessions.get(index).close();
                }
                for (int index = gateways.size() - 1; index >= 0; index--) {
                    gateways.get(index).close();
                }
            }

            assertEquals(latestOpenHandIdentity(databases.get(0)),
                    latestOpenHandIdentity(databases.get(1)));
            assertEquals(latestOpenHandIdentity(databases.get(0)),
                    latestOpenHandIdentity(databases.get(2)));
            assertEquals(latestOpenHandIdentity(databases.get(0)),
                    latestOpenHandIdentity(databases.get(3)));

            // The recovered hand two and fresh hand three must settle before
            // client three loses and securely replaces its channel in hand four.
            RecoverableGameRepository.RecoverableGame recovered
                    = new RecoverableGameRepository(databases.get(0))
                            .latestLocal().orElseThrow();
            gateways = new ArrayList<>();
            sessions = new ArrayList<>();
            try {
                for (int index = 0; index < 4; index++) {
                    gateways.add(
                            GdxNetworkHumanProjectionIntegrationTest.gateway(
                                    directories.get(index), databases.get(index)));
                }
                LobbySession host = gateways.get(0).open(recoveryRequest(
                        "Anfitrion", port, recovered)).get(15, TimeUnit.SECONDS);
                sessions.add(host);
                for (int index = 1; index < 4; index++) {
                    sessions.add(gateways.get(index).open(request(true,
                            "Invitado" + index, port, 5))
                            .get(20, TimeUnit.SECONDS));
                }
                List<LobbySession> recoveredSessions = List.copyOf(sessions);
                await(() -> recoveredSessions.stream().allMatch(session ->
                                session.snapshot().participants().size() == 5),
                        Duration.ofSeconds(15));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(15, TimeUnit.SECONDS);

                List<GdxScenarioRenderer> renderers = new ArrayList<>();
                for (int index = 0; index < sessions.size(); index++) {
                    TableSession table = sessions.get(index).tableSession()
                            .toCompletableFuture().get(30, TimeUnit.SECONDS);
                    GdxScenarioRenderer renderer
                            = new GdxScenarioRenderer(table, 5);
                    if (index == 3) {
                        renderer.gateActionOnHand(4);
                    }
                    table.attach(renderer).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    renderers.add(renderer);
                }
                GdxScenarioRenderer clientThreeRenderer = renderers.get(3);
                await(clientThreeRenderer::hasHeldAction,
                        Duration.ofSeconds(90));
                closeNativeClientSocket(sessions.get(3));
                LobbySession clientThree = sessions.get(3);
                await(() -> peerReconnectionCount(host, "Invitado3") == 1
                                && peerReconnectionCount(clientThree,
                                "Anfitrion") == 1,
                        Duration.ofSeconds(30));
                clientThreeRenderer.releaseHeldAction();

                await(() -> renderers.stream().allMatch(
                                GdxScenarioRenderer::isClosed),
                        Duration.ofSeconds(180));
                Map<String, Double> balances
                        = renderers.get(0).balancesByNickname();
                for (GdxScenarioRenderer renderer : renderers) {
                    renderer.assertComplete(4);
                    assertEquals(5, renderer.summary().handCount());
                    assertEquals(balances, renderer.balancesByNickname());
                }
                assertEquals(1, peerReconnectionCount(host, "Invitado3"));
                assertConservedLedger(renderers.get(0).summary(), 5);
            } finally {
                for (int index = sessions.size() - 1; index >= 0; index--) {
                    sessions.get(index).close();
                }
                for (int index = gateways.size() - 1; index >= 0; index--) {
                    gateways.get(index).close();
                }
            }
        } finally {
            for (int index = databases.size() - 1; index >= 0; index--) {
                databases.get(index).close();
            }
        }
    }

    /** Exact GDX homologue of Swing's {@code lifecycle-chaos} scenario. */
    @Test
    void lifecycleChaosConvergesAcrossReconnectPauseAndTwoRecoveryCycles()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        List<DatabaseService> databases = new ArrayList<>();
        List<Path> directories = new ArrayList<>();
        try {
            for (int index = 0; index < 3; index++) {
                databases.add(database("lifecycle-chaos-" + index + ".sqlite"));
                directories.add(temporary.resolve("lifecycle-chaos-" + index));
            }

            // Phase one mirrors Swing: reconnect client one in hand one,
            // pause/resume the held host decision in hand two and interrupt
            // hand three at an explicit recoverable boundary.
            List<NetworkLobbyGateway> gateways = new ArrayList<>();
            List<LobbySession> sessions = new ArrayList<>();
            try {
                for (int index = 0; index < 3; index++) {
                    gateways.add(
                            GdxNetworkHumanProjectionIntegrationTest.gateway(
                                    directories.get(index), databases.get(index)));
                }
                sessions.add(gateways.get(0).open(request(false, "Anfitrion",
                        port, 7)).get(10, TimeUnit.SECONDS));
                sessions.add(gateways.get(1).open(request(true, "Invitado1",
                        port, 7)).get(15, TimeUnit.SECONDS));
                sessions.add(gateways.get(2).open(request(true, "Invitado2",
                        port, 7)).get(15, TimeUnit.SECONDS));
                LobbySession host = sessions.get(0);
                host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                List<LobbySession> initialSessions = List.copyOf(sessions);
                await(() -> initialSessions.stream().allMatch(session ->
                                session.snapshot().participants().size() == 4),
                        Duration.ofSeconds(12));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(10, TimeUnit.SECONDS);

                List<GdxScenarioRenderer> renderers = new ArrayList<>();
                for (int index = 0; index < sessions.size(); index++) {
                    TableSession table = sessions.get(index).tableSession()
                            .toCompletableFuture().get(25, TimeUnit.SECONDS);
                    GdxScenarioRenderer renderer
                            = new GdxScenarioRenderer(table, 4);
                    if (index == 0) {
                        renderer.gateActionOnHand(2);
                    } else if (index == 1) {
                        renderer.gateActionOnHand(1);
                    }
                    table.attach(renderer).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    renderers.add(renderer);
                }
                GdxScenarioRenderer hostRenderer = renderers.get(0);
                GdxScenarioRenderer firstRenderer = renderers.get(1);
                LobbySession first = sessions.get(1);

                await(firstRenderer::hasHeldAction, Duration.ofSeconds(20));
                closeNativeClientSocket(first);
                await(() -> peerReconnectionCount(host, "Invitado1") == 1
                                && peerReconnectionCount(first, "Anfitrion") == 1,
                        Duration.ofSeconds(30));
                firstRenderer.releaseHeldAction();

                await(hostRenderer::hasHeldAction, Duration.ofSeconds(45));
                host.tableSession().toCompletableFuture().get(5, TimeUnit.SECONDS)
                        .commands().submit(new TableCommand.TogglePause());
                await(() -> renderers.stream().allMatch(
                                GdxScenarioRenderer::sawPaused)
                                && renderers.stream().allMatch(
                                GdxScenarioRenderer::isPaused),
                        Duration.ofSeconds(20));
                host.tableSession().toCompletableFuture().get(5, TimeUnit.SECONDS)
                        .commands().submit(new TableCommand.TogglePause());
                await(() -> renderers.stream().allMatch(
                                GdxScenarioRenderer::resumedAfterPause)
                                && renderers.stream().noneMatch(
                                GdxScenarioRenderer::isPaused),
                        Duration.ofSeconds(20));
                hostRenderer.releaseHeldActionAndGate(3,
                        TableSnapshot.Street.PREFLOP);

                await(hostRenderer::hasHeldAction, Duration.ofSeconds(60));
                host.tableSession().toCompletableFuture().get(5, TimeUnit.SECONDS)
                        .commands().submit(new TableCommand.StopGame());
                await(() -> renderers.stream().allMatch(
                                GdxScenarioRenderer::isClosed),
                        Duration.ofSeconds(45));
                for (GdxScenarioRenderer renderer : renderers) {
                    assertEquals(TableSessionSummary.CloseReason.RECOVERABLE_STOP,
                            renderer.summary().reason());
                }
                assertEquals(1, peerReconnectionCount(host, "Invitado1"));
            } finally {
                for (int index = sessions.size() - 1; index >= 0; index--) {
                    sessions.get(index).close();
                }
                for (int index = gateways.size() - 1; index >= 0; index--) {
                    gateways.get(index).close();
                }
            }

            OpenHandIdentity firstOpen = latestOpenHandIdentity(databases.get(0));
            assertEquals(firstOpen, latestOpenHandIdentity(databases.get(1)));
            assertEquals(firstOpen, latestOpenHandIdentity(databases.get(2)));

            // Recover hand three, reconnect client two in fresh hand five and
            // force a second recoverable stop while hand six is held.
            RecoverableGameRepository.RecoverableGame firstRecovery
                    = new RecoverableGameRepository(databases.get(0))
                            .latestLocal().orElseThrow();
            gateways = new ArrayList<>();
            sessions = new ArrayList<>();
            try {
                for (int index = 0; index < 3; index++) {
                    gateways.add(
                            GdxNetworkHumanProjectionIntegrationTest.gateway(
                                    directories.get(index), databases.get(index)));
                }
                LobbySession host = gateways.get(0).open(recoveryRequest(
                        "Anfitrion", port, firstRecovery)).get(15,
                        TimeUnit.SECONDS);
                sessions.add(host);
                sessions.add(gateways.get(1).open(request(true, "Invitado1",
                        port, 7)).get(20, TimeUnit.SECONDS));
                sessions.add(gateways.get(2).open(request(true, "Invitado2",
                        port, 7)).get(20, TimeUnit.SECONDS));
                List<LobbySession> firstRecoverySessions = List.copyOf(sessions);
                await(() -> firstRecoverySessions.stream().allMatch(session ->
                                session.snapshot().participants().size() == 4),
                        Duration.ofSeconds(15));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(15, TimeUnit.SECONDS);

                List<GdxScenarioRenderer> renderers = new ArrayList<>();
                for (int index = 0; index < sessions.size(); index++) {
                    TableSession table = sessions.get(index).tableSession()
                            .toCompletableFuture().get(30, TimeUnit.SECONDS);
                    GdxScenarioRenderer renderer
                            = new GdxScenarioRenderer(table, 4);
                    if (index == 0) {
                        renderer.gateActionOnHand(6);
                    } else if (index == 2) {
                        renderer.gateActionOnHand(5);
                    }
                    table.attach(renderer).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    renderers.add(renderer);
                }
                GdxScenarioRenderer hostRenderer = renderers.get(0);
                GdxScenarioRenderer secondRenderer = renderers.get(2);
                LobbySession second = sessions.get(2);

                await(secondRenderer::hasHeldAction, Duration.ofSeconds(100));
                closeNativeClientSocket(second);
                await(() -> peerReconnectionCount(host, "Invitado2") == 1
                                && peerReconnectionCount(second, "Anfitrion") == 1,
                        Duration.ofSeconds(30));
                secondRenderer.releaseHeldAction();

                await(hostRenderer::hasHeldAction, Duration.ofSeconds(75));
                host.tableSession().toCompletableFuture().get(5, TimeUnit.SECONDS)
                        .commands().submit(new TableCommand.StopGame());
                await(() -> renderers.stream().allMatch(
                                GdxScenarioRenderer::isClosed),
                        Duration.ofSeconds(45));
                for (GdxScenarioRenderer renderer : renderers) {
                    assertEquals(TableSessionSummary.CloseReason.RECOVERABLE_STOP,
                            renderer.summary().reason());
                }
                assertEquals(1, peerReconnectionCount(host, "Invitado2"));
            } finally {
                for (int index = sessions.size() - 1; index >= 0; index--) {
                    sessions.get(index).close();
                }
                for (int index = gateways.size() - 1; index >= 0; index--) {
                    gateways.get(index).close();
                }
            }

            OpenHandIdentity secondOpen = latestOpenHandIdentity(databases.get(0));
            assertEquals(secondOpen, latestOpenHandIdentity(databases.get(1)));
            assertEquals(secondOpen, latestOpenHandIdentity(databases.get(2)));

            // The second recovery resumes hand six and must still settle the
            // final fresh hand seven identically for every GDX peer.
            RecoverableGameRepository.RecoverableGame secondRecovery
                    = new RecoverableGameRepository(databases.get(0))
                            .latestLocal().orElseThrow();
            gateways = new ArrayList<>();
            sessions = new ArrayList<>();
            try {
                for (int index = 0; index < 3; index++) {
                    gateways.add(
                            GdxNetworkHumanProjectionIntegrationTest.gateway(
                                    directories.get(index), databases.get(index)));
                }
                LobbySession host = gateways.get(0).open(recoveryRequest(
                        "Anfitrion", port, secondRecovery)).get(15,
                        TimeUnit.SECONDS);
                sessions.add(host);
                sessions.add(gateways.get(1).open(request(true, "Invitado1",
                        port, 7)).get(20, TimeUnit.SECONDS));
                sessions.add(gateways.get(2).open(request(true, "Invitado2",
                        port, 7)).get(20, TimeUnit.SECONDS));
                List<LobbySession> secondRecoverySessions = List.copyOf(sessions);
                await(() -> secondRecoverySessions.stream().allMatch(session ->
                                session.snapshot().participants().size() == 4),
                        Duration.ofSeconds(15));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(15, TimeUnit.SECONDS);

                List<GdxScenarioRenderer> renderers = new ArrayList<>();
                for (LobbySession session : sessions) {
                    TableSession table = session.tableSession()
                            .toCompletableFuture().get(30, TimeUnit.SECONDS);
                    GdxScenarioRenderer renderer
                            = new GdxScenarioRenderer(table, 4);
                    table.attach(renderer).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    renderers.add(renderer);
                }
                await(() -> renderers.stream().allMatch(
                                GdxScenarioRenderer::isClosed),
                        Duration.ofSeconds(150));
                Map<String, Double> balances
                        = renderers.get(0).balancesByNickname();
                for (GdxScenarioRenderer renderer : renderers) {
                    renderer.assertComplete(2);
                    assertEquals(7, renderer.summary().handCount());
                    assertEquals(balances, renderer.balancesByNickname());
                }
                assertConservedLedger(renderers.get(0).summary(), 4);
            } finally {
                for (int index = sessions.size() - 1; index >= 0; index--) {
                    sessions.get(index).close();
                }
                for (int index = gateways.size() - 1; index >= 0; index--) {
                    gateways.get(index).close();
                }
            }
        } finally {
            for (int index = databases.size() - 1; index >= 0; index--) {
                databases.get(index).close();
            }
        }
    }

    /** Exact GDX homologue of Swing's {@code rit-network-cut} scenario. */
    @Test
    void nativeGdxRunItTwiceVoteSurvivesReconnectBeforeTheDelayedFinalVote()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = database("rit-cut-host.sqlite");
        DatabaseService voterDatabase = database("rit-cut-voter.sqlite");
        DatabaseService delayedDatabase = database("rit-cut-delayed.sqlite");
        AtomicInteger hostDialogs = new AtomicInteger();
        AtomicInteger voterDialogs = new AtomicInteger();
        AtomicInteger delayedDialogs = new AtomicInteger();
        AtomicReference<GdxTableDialog> hostDialog = new AtomicReference<>();
        AtomicReference<GdxTableDialog> voterDialog = new AtomicReference<>();
        AtomicReference<GdxTableDialog> delayedDialog = new AtomicReference<>();
        AtomicReference<CoronaPokerGdxTable> hostDialogTable
                = new AtomicReference<>();
        AtomicReference<CoronaPokerGdxTable> voterDialogTable
                = new AtomicReference<>();
        AtomicReference<CoronaPokerGdxTable> delayedDialogTable
                = new AtomicReference<>();
        GameDecisionSink hostDecisions = nativeRunItTwiceDecisions(
                hostDialogs, hostDialog, hostDialogTable, true);
        GameDecisionSink voterDecisions = nativeRunItTwiceDecisions(
                voterDialogs, voterDialog, voterDialogTable, true);
        GameDecisionSink delayedDecisions = nativeRunItTwiceDecisions(
                delayedDialogs, delayedDialog, delayedDialogTable, false);
        try (hostDatabase; voterDatabase; delayedDatabase;
             NetworkLobbyGateway hostGateway
                     = GdxNetworkHumanProjectionIntegrationTest.gateway(
                             temporary.resolve("rit-cut-host"), hostDatabase,
                             hostDecisions);
             NetworkLobbyGateway voterGateway
                     = GdxNetworkHumanProjectionIntegrationTest.gateway(
                             temporary.resolve("rit-cut-voter"), voterDatabase,
                             voterDecisions);
             NetworkLobbyGateway delayedGateway
                     = GdxNetworkHumanProjectionIntegrationTest.gateway(
                             temporary.resolve("rit-cut-delayed"),
                             delayedDatabase, delayedDecisions)) {
            LobbySession host = hostGateway.open(runItTwiceRequest(false,
                    "Anfitrion", port)).get(10, TimeUnit.SECONDS);
            LobbySession voter = voterGateway.open(runItTwiceRequest(true,
                    "Invitado1", port)).get(15, TimeUnit.SECONDS);
            LobbySession delayed = delayedGateway.open(runItTwiceRequest(true,
                    "Invitado2", port)).get(15, TimeUnit.SECONDS);
            try {
                await(() -> host.snapshot().participants().size() == 3
                                && voter.snapshot().participants().size() == 3
                                && delayed.snapshot().participants().size() == 3,
                        Duration.ofSeconds(12));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(10, TimeUnit.SECONDS);

                TableSession hostTable = host.tableSession().toCompletableFuture()
                        .get(20, TimeUnit.SECONDS);
                GdxScenarioRenderer hostRenderer
                        = new GdxScenarioRenderer(hostTable, 3,
                                hostDialogTable);
                hostRenderer.allInOnHand(1);
                hostTable.attach(hostRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                TableSession voterTable = voter.tableSession().toCompletableFuture()
                        .get(35, TimeUnit.SECONDS);
                GdxScenarioRenderer voterRenderer
                        = new GdxScenarioRenderer(voterTable, 3,
                                voterDialogTable);
                voterRenderer.allInOnHand(1);
                voterTable.attach(voterRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                TableSession delayedTable = delayed.tableSession()
                        .toCompletableFuture().get(35, TimeUnit.SECONDS);
                GdxScenarioRenderer delayedRenderer
                        = new GdxScenarioRenderer(delayedTable, 3,
                                delayedDialogTable);
                delayedRenderer.allInOnHand(1);
                delayedTable.attach(delayedRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                // This is the immutable Swing ordering: client one's signed
                // vote is already accepted, client two is still holding the
                // final dialog vote, and only then does client one lose its
                // transport generation.
                await(() -> voterDialogs.get() == 1
                                && delayedDialogs.get() == 1
                                && hostDialog.get() != null
                                && hostDialog.get().message().contains(
                                        "2 DOS VECES")
                                && delayedDialog.get() != null
                                && !delayedDialog.get().complete()
                                && delayedDialogTable.get() != null,
                        Duration.ofSeconds(60));
                closeNativeClientSocket(voter);
                await(() -> peerReconnectionCount(host, "Invitado1") == 1
                                && peerReconnectionCount(voter, "Anfitrion") == 1,
                        Duration.ofSeconds(30));
                assertTrue(delayedDialogTable.get()
                                .resolveActiveDialogChoice(true),
                        "the delayed native GDX RIT dialog must accept after "
                                + "the peer reconnects");

                await(() -> hostRenderer.isClosed()
                                && voterRenderer.isClosed()
                                && delayedRenderer.isClosed(),
                        Duration.ofSeconds(120));
                assertEquals(1, hostDialogs.get());
                assertEquals(1, voterDialogs.get());
                assertEquals(1, delayedDialogs.get());
                assertTrue(hostRenderer.completedRunItTwiceBoards());
                assertTrue(voterRenderer.completedRunItTwiceBoards());
                assertTrue(delayedRenderer.completedRunItTwiceBoards());
                hostRenderer.assertComplete(1);
                voterRenderer.assertComplete(1);
                delayedRenderer.assertComplete(1);
                assertEquals(1, hostRenderer.summary().handCount());
                assertEquals(hostRenderer.balancesByNickname(),
                        voterRenderer.balancesByNickname());
                assertEquals(hostRenderer.balancesByNickname(),
                        delayedRenderer.balancesByNickname());
                assertConservedLedger(hostRenderer.summary(), 3);
            } finally {
                delayed.close();
                voter.close();
                host.close();
            }
        }
    }

    /** Exact GDX homologue of Swing's {@code bot-bust-recover-regrow}. */
    @Test
    void bustedBotRegrowsAtTheRecoveredHandBoundary() throws Exception {
        runBotBustRecovery(true);
    }

    /** Exact GDX homologue of Swing's {@code bot-bust-recover-drop}. */
    @Test
    void bustedBotDropsFromTheRecoveredActiveRing() throws Exception {
        runBotBustRecovery(false);
    }

    private void runBotBustRecovery(boolean enableBotRebuy) throws Exception {
        String property = "coronapoker.qa.forceBotAllInNicks";
        String previous = System.getProperty(property);
        System.setProperty(property, "CoronaBot$1,CoronaBot$2");
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        List<DatabaseService> databases = new ArrayList<>();
        List<Path> directories = new ArrayList<>();
        String suffix = enableBotRebuy ? "regrow" : "drop";
        try {
            for (int index = 0; index < 3; index++) {
                databases.add(database("bot-bust-" + suffix + "-" + index
                        + ".sqlite"));
                directories.add(temporary.resolve("bot-bust-" + suffix + "-"
                        + index));
            }

            Set<String> bustedBots;
            try (NetworkLobbyGateway hostGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 directories.get(0), databases.get(0));
                 NetworkLobbyGateway firstGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 directories.get(1), databases.get(1));
                 NetworkLobbyGateway secondGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 directories.get(2), databases.get(2))) {
                List<LobbySession> sessions = new ArrayList<>();
                sessions.add(hostGateway.open(request(false, "Anfitrion", port,
                        7, false)).get(10, TimeUnit.SECONDS));
                sessions.add(firstGateway.open(request(true, "Invitado1", port,
                        7)).get(15, TimeUnit.SECONDS));
                sessions.add(secondGateway.open(request(true, "Invitado2", port,
                        7)).get(15, TimeUnit.SECONDS));
                try {
                    LobbySession host = sessions.get(0);
                    host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    await(() -> sessions.stream().allMatch(session ->
                                    session.snapshot().participants().size() == 5),
                            Duration.ofSeconds(12));
                    host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                            .get(10, TimeUnit.SECONDS);

                    List<GdxScenarioRenderer> renderers = new ArrayList<>();
                    for (int index = 0; index < sessions.size(); index++) {
                        TableSession table = sessions.get(index).tableSession()
                                .toCompletableFuture().get(25,
                                        TimeUnit.SECONDS);
                        GdxScenarioRenderer renderer
                                = new GdxScenarioRenderer(table, 5);
                        renderer.foldAutomatically(true);
                        if (index == 0) renderer.gateActionOnHand(4);
                        table.attach(renderer).toCompletableFuture()
                                .get(5, TimeUnit.SECONDS);
                        renderers.add(renderer);
                    }
                    GdxScenarioRenderer hostRenderer = renderers.get(0);
                    await(() -> hostRenderer.spectatorNicknames().stream()
                                    .anyMatch(nick -> nick.startsWith("CoronaBot$")),
                            Duration.ofSeconds(90));
                    bustedBots = hostRenderer.spectatorNicknames().stream()
                            .filter(nick -> nick.startsWith("CoronaBot$"))
                            .collect(java.util.stream.Collectors.toUnmodifiableSet());
                    assertFalse(bustedBots.isEmpty());
                    // Stop forcing bot all-ins as soon as the first canonical
                    // bust is observed. Waiting for every renderer first leaves
                    // a window in which the solvent bot can enter another
                    // forced all-in and invalidate the sealed next-hand roster.
                    System.clearProperty(property);
                    try {
                        await(() -> renderers.stream().allMatch(renderer ->
                                        bustedBots.stream().allMatch(
                                                renderer::sawSpectator)),
                                Duration.ofSeconds(30));
                    } catch (AssertionError timeout) {
                        String diagnostic = renderers.stream()
                                .map(renderer -> renderer.localNickname()
                                + " current=" + renderer.spectatorNicknames()
                                + " expectedEver=" + bustedBots
                                + " config=" + renderer.configurationDiagnostic()
                                + " players=" + renderer.playerStateDiagnostic()
                                + " actions=" + renderer.actionTrace())
                                .collect(java.util.stream.Collectors.joining("\n"));
                        throw new AssertionError(
                                "bot spectator transition did not reach every GDX renderer\n"
                                + diagnostic, timeout);
                    }
                    renderers.forEach(renderer -> renderer.foldAutomatically(false));
                    try {
                        await(hostRenderer::hasHeldAction, Duration.ofSeconds(90));
                    } catch (AssertionError timeout) {
                        String diagnostic = renderers.stream()
                                .map(renderer -> renderer.localNickname()
                                + " hand=" + renderer.currentHand()
                                + " spectators=" + renderer.spectatorNicknames()
                                + " actions=" + renderer.actionTrace())
                                .collect(java.util.stream.Collectors.joining("\n"));
                        throw new AssertionError("bot recovery setup stalled\n"
                                + diagnostic, timeout);
                    }
                    assertTrue(java.util.Collections.disjoint(bustedBots,
                            hostRenderer.playingNicknames()),
                            "busted bots must be outside the active ring before recovery");
                    sessions.get(0).tableSession().toCompletableFuture()
                            .get(5, TimeUnit.SECONDS).commands()
                            .submit(new TableCommand.StopGame());
                    await(() -> renderers.stream().allMatch(
                                    GdxScenarioRenderer::isClosed),
                            Duration.ofSeconds(35));
                    for (GdxScenarioRenderer renderer : renderers) {
                        assertEquals(TableSessionSummary.CloseReason.RECOVERABLE_STOP,
                                renderer.summary().reason());
                    }
                } finally {
                    for (int index = sessions.size() - 1; index >= 0; index--) {
                        sessions.get(index).close();
                    }
                }
            }

            RecoverableGameRepository.RecoverableGame recovered
                    = new RecoverableGameRepository(databases.get(0))
                            .latestLocal().orElseThrow();
            List<NetworkLobbyGateway> gateways = new ArrayList<>();
            List<LobbySession> sessions = new ArrayList<>();
            try {
                for (int index = 0; index < 3; index++) {
                    gateways.add(
                            GdxNetworkHumanProjectionIntegrationTest.gateway(
                                    directories.get(index), databases.get(index)));
                }
                LobbySession host = gateways.get(0).open(recoveryRequest(
                        "Anfitrion", port, recovered, enableBotRebuy))
                        .get(15, TimeUnit.SECONDS);
                sessions.add(host);
                host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                sessions.add(gateways.get(1).open(request(true, "Invitado1",
                        port, 7)).get(20, TimeUnit.SECONDS));
                sessions.add(gateways.get(2).open(request(true, "Invitado2",
                        port, 7)).get(20, TimeUnit.SECONDS));
                // Recovery restores the surviving bot from the persisted roster;
                // AddBot then recreates the busted seat with its canonical number.
                await(() -> sessions.stream().allMatch(session ->
                                session.snapshot().participants().size() == 5),
                        Duration.ofSeconds(15));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(15, TimeUnit.SECONDS);

                List<GdxScenarioRenderer> renderers = new ArrayList<>();
                for (LobbySession session : sessions) {
                    TableSession table = session.tableSession().toCompletableFuture()
                            .get(35, TimeUnit.SECONDS);
                    GdxScenarioRenderer renderer
                            = new GdxScenarioRenderer(table, 5);
                    table.attach(renderer).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    renderers.add(renderer);
                }
                await(() -> renderers.stream().allMatch(renderer ->
                                bustedBots.stream().allMatch(
                                        renderer::sawSpectator)),
                        Duration.ofSeconds(90));
                if (enableBotRebuy) {
                    await(() -> renderers.stream().allMatch(renderer ->
                                    bustedBots.stream().allMatch(
                                            renderer::sawSpectatorReactivated)),
                            Duration.ofSeconds(90));
                } else {
                    await(() -> renderers.stream().allMatch(renderer ->
                                    renderer.currentHand() >= 5
                                    || renderer.isClosed()),
                            Duration.ofSeconds(90));
                    for (GdxScenarioRenderer renderer : renderers) {
                        assertTrue(java.util.Collections.disjoint(bustedBots,
                                renderer.playingNicknames()));
                    }
                }
                await(() -> renderers.stream().allMatch(
                                GdxScenarioRenderer::isClosed),
                        Duration.ofSeconds(210));
                Map<String, Double> balances = renderers.get(0)
                        .balancesByNickname();
                for (GdxScenarioRenderer renderer : renderers) {
                    // Recovery is requested with global hand 4 still open.
                    // That hand is refunded/aborted by the canonical recovery
                    // path and emits its own renderer-neutral EndHand before
                    // recovered hands 5, 6 and 7 complete. The summary below
                    // also proves the global hand sequence reached 7, matching
                    // the immutable Swing scenario.
                    renderer.assertCompleteWithHistoricalBalances(4,
                            enableBotRebuy ? 5 : 4, 5);
                    assertEquals(7, renderer.summary().handCount());
                    assertEquals(balances, renderer.balancesByNickname());
                }
                assertConservedLedger(renderers.get(0).summary(), 5);
            } finally {
                for (int index = sessions.size() - 1; index >= 0; index--) {
                    sessions.get(index).close();
                }
                for (int index = gateways.size() - 1; index >= 0; index--) {
                    gateways.get(index).close();
                }
            }
        } finally {
            for (int index = databases.size() - 1; index >= 0; index--) {
                databases.get(index).close();
            }
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }

    private void stopNetworkTableForRecovery(java.nio.file.Path hostDirectory,
            java.nio.file.Path clientDirectory, DatabaseService hostDatabase,
            DatabaseService clientDatabase, int port, int maximumHands,
            long interruptedHand, boolean recovering) throws Exception {
        try (NetworkLobbyGateway hostGateway
                     = GdxNetworkHumanProjectionIntegrationTest.gateway(
                             hostDirectory, hostDatabase);
             NetworkLobbyGateway clientGateway
                     = GdxNetworkHumanProjectionIntegrationTest.gateway(
                             clientDirectory, clientDatabase)) {
            NewGameRequest hostRequest;
            if (recovering) {
                RecoverableGameRepository.RecoverableGame recovered
                        = new RecoverableGameRepository(hostDatabase)
                                .latestLocal().orElseThrow();
                hostRequest = recoveryRequest("Anfitrion", port, recovered);
            } else {
                hostRequest = request(false, "Anfitrion", port, maximumHands);
            }
            LobbySession host = hostGateway.open(hostRequest)
                    .get(10, TimeUnit.SECONDS);
            LobbySession client;
            if (recovering) {
                client = clientGateway.open(request(
                        true, "Invitado", port, maximumHands))
                        .get(10, TimeUnit.SECONDS);
            } else {
                client = clientGateway.open(request(
                        true, "Invitado", port, maximumHands))
                        .get(10, TimeUnit.SECONDS);
                host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
            }
            try {
                await(() -> host.snapshot().participants().size() == 4
                                && client.snapshot().participants().size() == 4,
                        Duration.ofSeconds(8));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(10, TimeUnit.SECONDS);
                TableSession hostTable = host.tableSession().toCompletableFuture()
                        .get(15, TimeUnit.SECONDS);
                GdxScenarioRenderer hostRenderer = attach(
                        hostTable, 4, interruptedHand);
                GdxScenarioRenderer clientRenderer = attach(
                        client.tableSession().toCompletableFuture()
                                .get(20, TimeUnit.SECONDS), 4, -1);
                await(hostRenderer::hasHeldAction,
                        Duration.ofSeconds(interruptedHand == 1 ? 15 : 70));
                hostTable.commands().submit(new TableCommand.StopGame());
                await(() -> hostRenderer.isClosed() && clientRenderer.isClosed(),
                        Duration.ofSeconds(25));
                assertEquals(TableSessionSummary.CloseReason.RECOVERABLE_STOP,
                        hostRenderer.summary().reason());
                assertEquals(TableSessionSummary.CloseReason.RECOVERABLE_STOP,
                        clientRenderer.summary().reason());
            } finally {
                client.close();
                host.close();
            }
        }
        assertEquals(latestOpenHandIdentity(hostDatabase),
                latestOpenHandIdentity(clientDatabase),
                "force-recover must leave the same open HAND_ID and roster in every peer database");
    }

    private void completeRecoveredNetworkTable(
            java.nio.file.Path hostDirectory,
            java.nio.file.Path clientDirectory, DatabaseService hostDatabase,
            DatabaseService clientDatabase, int port, int maximumHands,
            int expectedRenderedHands) throws Exception {
        RecoverableGameRepository.RecoverableGame recovered
                = new RecoverableGameRepository(hostDatabase)
                        .latestLocal().orElseThrow();
        try (NetworkLobbyGateway hostGateway
                     = GdxNetworkHumanProjectionIntegrationTest.gateway(
                             hostDirectory, hostDatabase);
             NetworkLobbyGateway clientGateway
                     = GdxNetworkHumanProjectionIntegrationTest.gateway(
                             clientDirectory, clientDatabase)) {
            LobbySession host = hostGateway.open(recoveryRequest(
                    "Anfitrion", port, recovered)).get(10, TimeUnit.SECONDS);
            LobbySession client = clientGateway.open(request(
                    true, "Invitado", port, maximumHands))
                    .get(10, TimeUnit.SECONDS);
            try {
                await(() -> host.snapshot().participants().size() == 4
                                && client.snapshot().participants().size() == 4,
                        Duration.ofSeconds(8));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(10, TimeUnit.SECONDS);
                GdxScenarioRenderer hostRenderer = attach(
                        host.tableSession().toCompletableFuture()
                                .get(15, TimeUnit.SECONDS), 4, -1);
                GdxScenarioRenderer clientRenderer = attach(
                        client.tableSession().toCompletableFuture()
                                .get(20, TimeUnit.SECONDS), 4, -1);
                await(() -> hostRenderer.isClosed() && clientRenderer.isClosed(),
                        Duration.ofSeconds(80));
                hostRenderer.assertComplete(expectedRenderedHands);
                clientRenderer.assertComplete(expectedRenderedHands);
                assertEquals(maximumHands, hostRenderer.summary().handCount());
                assertEquals(maximumHands, clientRenderer.summary().handCount());
                assertEquals(hostRenderer.balancesByNickname(),
                        clientRenderer.balancesByNickname());
                assertConservedLedger(hostRenderer.summary(), 4);
            } finally {
                client.close();
                host.close();
            }
        }
    }

    private static OpenHandIdentity latestOpenHandIdentity(
            DatabaseService database) throws Exception {
        synchronized (database) {
            String sql = "SELECT hand_id_b64, preflop_players FROM hand "
                    + "WHERE end IS NULL ORDER BY id DESC LIMIT 1";
            try (java.sql.PreparedStatement statement
                         = database.connection().prepareStatement(sql);
                 java.sql.ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next(),
                        "recoverable stop must preserve one open hand");
                return new OpenHandIdentity(rows.getString("hand_id_b64"),
                        com.tonikelope.coronapoker.RecoveryBalanceReconciler
                                .decodeRoster(rows.getString("preflop_players")));
            }
        }
    }

    private record OpenHandIdentity(String handId,
            java.util.Set<String> roster) {
    }

    /** Exact GDX homologue of Swing's {@code crash-rejoin-recover} scenario. */
    @Test
    void crashedPeerRejoinsTheRecoverableGameAndCompletesTheNextHand()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = database("crash-rejoin-host.sqlite");
        DatabaseService clientDatabase = database("crash-rejoin-client.sqlite");
        var hostDirectory = temporary.resolve("crash-rejoin-host");
        var clientDirectory = temporary.resolve("crash-rejoin-client");
        AtomicReference<ServerSocket> initialHostSocket = new AtomicReference<>();
        try (hostDatabase; clientDatabase) {
            try (NetworkLobbyGateway hostGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 hostDirectory, hostDatabase);
                 NetworkLobbyGateway clientGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 clientDirectory, clientDatabase)) {
                LobbySession host = hostGateway.open(
                        request(false, "Anfitrion", port, 2))
                        .get(5, TimeUnit.SECONDS);
                initialHostSocket.set((ServerSocket) field(
                        field(host, "resource"), "serverSocket"));
                LobbySession client = clientGateway.open(
                        request(true, "Invitado", port, 2))
                        .get(5, TimeUnit.SECONDS);
                try {
                    host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    await(() -> host.snapshot().participants().size() == 4
                                    && client.snapshot().participants().size() == 4,
                            Duration.ofSeconds(6));
                    host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
                    GdxScenarioRenderer hostRenderer = attach(
                            host.tableSession().toCompletableFuture()
                                    .get(8, TimeUnit.SECONDS), 4, -1);
                    GdxScenarioRenderer clientRenderer = attach(
                            client.tableSession().toCompletableFuture()
                                    .get(12, TimeUnit.SECONDS), 4, 1);
                    await(clientRenderer::hasHeldAction, Duration.ofSeconds(12));
                    closeClientTransport(client);
                    await(hostRenderer::isClosed, Duration.ofSeconds(150));
                    assertEquals(TableSessionSummary.CloseReason.RECOVERABLE_STOP,
                            hostRenderer.summary().reason());
                } finally {
                    client.close();
                    host.close();
                }
            }
            assertTrue(initialHostSocket.get().isClosed(),
                    "closing the first GDX host session leaked its listening socket");
            RecoverableGameRepository.RecoverableGame hostRecovered
                    = new RecoverableGameRepository(hostDatabase)
                            .latestLocal().orElseThrow();
            try (NetworkLobbyGateway hostGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 hostDirectory, hostDatabase);
                 NetworkLobbyGateway clientGateway
                         = GdxNetworkHumanProjectionIntegrationTest.gateway(
                                 clientDirectory, clientDatabase)) {
                LobbySession host = hostGateway.open(recoveryRequest(
                        "Anfitrion", port, hostRecovered))
                        .get(10, TimeUnit.SECONDS);
                // The killed client owns no local=1 game row. Swing restarts
                // that same identity by joining the recovered host, and
                // the client rebuilds its durable state from the recovered
                // wire protocol rather than fabricating a local host record.
                LobbySession client = clientGateway.open(request(
                        true, "Invitado", port, 2))
                        .get(10, TimeUnit.SECONDS);
                try {
                    await(() -> host.snapshot().participants().size() == 4
                                    && client.snapshot().participants().size() == 4,
                            Duration.ofSeconds(8));
                    host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                            .get(10, TimeUnit.SECONDS);
                    GdxScenarioRenderer hostRenderer = attach(
                            host.tableSession().toCompletableFuture()
                                    .get(15, TimeUnit.SECONDS), 4, -1);
                    GdxScenarioRenderer clientRenderer = attach(
                            client.tableSession().toCompletableFuture()
                                    .get(20, TimeUnit.SECONDS), 4, -1);

                    await(() -> hostRenderer.isClosed()
                                    && clientRenderer.isClosed(),
                            Duration.ofSeconds(60));
                    // This renderer is attached only to the restarted table, so
                    // it observes one completed hand. The durable session
                    // counter must nevertheless continue at Swing's HAND 2.
                    hostRenderer.assertComplete(1);
                    clientRenderer.assertComplete(1);
                    assertEquals(2, hostRenderer.summary().handCount());
                    assertEquals(2, clientRenderer.summary().handCount());
                    assertEquals(hostRenderer.balancesByNickname(),
                            clientRenderer.balancesByNickname());
                    assertConservedLedger(hostRenderer.summary(), 4);
                } finally {
                    client.close();
                    host.close();
                }
            }
        }
    }

    @Test
    void reconnectMidHandPreservesBothGdxTablesAndCompletesTheGame()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = database("midhand-host.sqlite");
        DatabaseService clientDatabase = database("midhand-client.sqlite");
        DatabaseService witnessDatabase = database("midhand-witness.sqlite");
        try (hostDatabase; clientDatabase; witnessDatabase;
             NetworkLobbyGateway hostGateway = gateway("midhand-host",
                     hostDatabase);
             NetworkLobbyGateway clientGateway = gateway("midhand-client",
                     clientDatabase);
             NetworkLobbyGateway witnessGateway = gateway("midhand-witness",
                     witnessDatabase)) {
            LobbySession host = hostGateway.open(
                    request(false, "Anfitrion", port, 2))
                    .get(5, TimeUnit.SECONDS);
            LobbySession client = clientGateway.open(
                    request(true, "Invitado", port, 2))
                    .get(5, TimeUnit.SECONDS);
            LobbySession witness = witnessGateway.open(
                    request(true, "Testigo", port, 2))
                    .get(5, TimeUnit.SECONDS);
            try {
                host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                await(() -> host.snapshot().participants().size() == 4
                                && client.snapshot().participants().size() == 4
                                && witness.snapshot().participants().size() == 4,
                        Duration.ofSeconds(5));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                GdxScenarioRenderer hostRenderer = attach(
                        host.tableSession().toCompletableFuture()
                                .get(5, TimeUnit.SECONDS), 4, -1);
                GdxScenarioRenderer clientRenderer = attach(
                        client.tableSession().toCompletableFuture()
                                .get(10, TimeUnit.SECONDS), 4, 1);
                GdxScenarioRenderer witnessRenderer = attach(
                        witness.tableSession().toCompletableFuture()
                                .get(10, TimeUnit.SECONDS), 4, -1);

                await(clientRenderer::hasHeldAction,
                        Duration.ofSeconds(8));
                closeNativeClientSocket(client);
                await(() -> peerReconnectionCount(host, "Invitado") == 1
                                && peerReconnectionCount(client, "Anfitrion") == 1,
                        Duration.ofSeconds(20));
                clientRenderer.releaseHeldAction();

                await(() -> hostRenderer.isClosed()
                                && clientRenderer.isClosed()
                                && witnessRenderer.isClosed(),
                        Duration.ofSeconds(30));
                hostRenderer.assertComplete(2);
                clientRenderer.assertComplete(2);
                witnessRenderer.assertComplete(2);
                assertEquals(hostRenderer.balancesByNickname(),
                        clientRenderer.balancesByNickname());
                assertEquals(hostRenderer.balancesByNickname(),
                        witnessRenderer.balancesByNickname());
            } finally {
                witness.close();
                client.close();
                host.close();
            }
        }
    }

    @Test
    void reconnectTwiceUsesDifferentGdxPeersAndCompletesThreeHands()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = database("twice-host.sqlite");
        DatabaseService firstDatabase = database("twice-first.sqlite");
        DatabaseService secondDatabase = database("twice-second.sqlite");
        try (hostDatabase; firstDatabase; secondDatabase;
             NetworkLobbyGateway hostGateway = gateway("twice-host",
                     hostDatabase);
             NetworkLobbyGateway firstGateway = gateway("twice-first",
                     firstDatabase);
             NetworkLobbyGateway secondGateway = gateway("twice-second",
                     secondDatabase)) {
            LobbySession host = hostGateway.open(
                    request(false, "Anfitrion", port, 3))
                    .get(5, TimeUnit.SECONDS);
            LobbySession first = firstGateway.open(
                    request(true, "Invitado1", port, 3))
                    .get(5, TimeUnit.SECONDS);
            LobbySession second = secondGateway.open(
                    request(true, "Invitado2", port, 3))
                    .get(5, TimeUnit.SECONDS);
            try {
                host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                await(() -> host.snapshot().participants().size() == 4
                                && first.snapshot().participants().size() == 4
                                && second.snapshot().participants().size() == 4,
                        Duration.ofSeconds(5));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                TableSession hostTable = host.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                GdxScenarioRenderer hostRenderer
                        = attach(hostTable, 4, -1);
                TableSession firstTable = first.tableSession()
                        .toCompletableFuture().get(10, TimeUnit.SECONDS);
                GdxScenarioRenderer firstRenderer
                        = attach(firstTable, 4, 1);
                TableSession secondTable = second.tableSession()
                        .toCompletableFuture().get(10, TimeUnit.SECONDS);
                GdxScenarioRenderer secondRenderer
                        = attach(secondTable, 4, 2);

                await(firstRenderer::hasHeldAction, Duration.ofSeconds(8));
                closeNativeClientSocket(first);
                await(() -> peerReconnectionCount(host, "Invitado1") == 1
                                && peerReconnectionCount(first, "Anfitrion") == 1,
                        Duration.ofSeconds(20));
                firstRenderer.releaseHeldAction();

                await(secondRenderer::hasHeldAction, Duration.ofSeconds(15));
                closeNativeClientSocket(second);
                await(() -> peerReconnectionCount(host, "Invitado2") == 1
                                && peerReconnectionCount(second, "Anfitrion") == 1,
                        Duration.ofSeconds(20));
                secondRenderer.releaseHeldAction();

            await(() -> hostRenderer.isClosed()
                            && firstRenderer.isClosed()
                            && secondRenderer.isClosed(),
                    Duration.ofSeconds(40));
                hostRenderer.assertComplete(3);
                firstRenderer.assertComplete(3);
                secondRenderer.assertComplete(3);
                assertEquals(hostRenderer.balancesByNickname(),
                        firstRenderer.balancesByNickname());
                assertEquals(hostRenderer.balancesByNickname(),
                        secondRenderer.balancesByNickname());
            } finally {
                second.close();
                first.close();
                host.close();
            }
        }
    }

    @Test
    void allInReconnectPreservesAcceptedActionAndSettlesEveryGdxTable()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = database("allin-host.sqlite");
        DatabaseService actingDatabase = database("allin-acting.sqlite");
        DatabaseService callerDatabase = database("allin-caller.sqlite");
        try (hostDatabase; actingDatabase; callerDatabase;
             NetworkLobbyGateway hostGateway = cinematicGateway("allin-host",
                     hostDatabase);
             NetworkLobbyGateway actingGateway = cinematicGateway("allin-acting",
                     actingDatabase);
             NetworkLobbyGateway callerGateway = cinematicGateway("allin-caller",
                     callerDatabase)) {
            LobbySession host = hostGateway.open(
                    request(false, "Anfitrion", port, 1))
                    .get(5, TimeUnit.SECONDS);
            LobbySession acting = actingGateway.open(
                    request(true, "Alliner", port, 1))
                    .get(5, TimeUnit.SECONDS);
            LobbySession caller = callerGateway.open(
                    request(true, "Caller", port, 1))
                    .get(5, TimeUnit.SECONDS);
            try {
                await(() -> host.snapshot().participants().size() == 3,
                        Duration.ofSeconds(5));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                TableSession hostTable = host.tableSession().toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                GdxScenarioRenderer hostRenderer
                        = attach(hostTable, 3, -1);
                TableSession actingTable = acting.tableSession()
                        .toCompletableFuture().get(10, TimeUnit.SECONDS);
                GdxScenarioRenderer actingRenderer
                        = new GdxScenarioRenderer(actingTable, 3);
                actingRenderer.allInOnHand(1,
                        () -> closeNativeClientSocketUnchecked(acting));
                actingTable.attach(actingRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                TableSession callerTable = caller.tableSession()
                        .toCompletableFuture().get(10, TimeUnit.SECONDS);
                GdxScenarioRenderer callerRenderer
                        = attach(callerTable, 3, -1);

                await(() -> peerReconnectionCount(host, "Alliner") == 1
                                && peerReconnectionCount(acting, "Anfitrion") == 1,
                        Duration.ofSeconds(20));
                await(hostRenderer::sawAllInAction, Duration.ofSeconds(8));

                await(() -> hostRenderer.isClosed()
                                && actingRenderer.isClosed()
                                && callerRenderer.isClosed(),
                        Duration.ofSeconds(25));
                assertTrue(hostRenderer.sawAllInCinematic());
                assertTrue(actingRenderer.sawAllInCinematic());
                assertTrue(callerRenderer.sawAllInCinematic());
                hostRenderer.assertComplete(1);
                actingRenderer.assertComplete(1);
                callerRenderer.assertComplete(1);
            } finally {
                caller.close();
                acting.close();
                host.close();
            }
        }
    }

    /** Exact GDX homologue of Swing's {@code allin-abrupt-exit} scenario. */
    @Test
    void allInAbruptExitRefundsTheHandAndLeavesTheWitnessRecoverable()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = database("allin-abrupt-host.sqlite");
        DatabaseService victimDatabase = database("allin-abrupt-victim.sqlite");
        DatabaseService witnessDatabase = database("allin-abrupt-witness.sqlite");
        try (hostDatabase; victimDatabase; witnessDatabase;
             NetworkLobbyGateway hostGateway = cinematicGateway(
                     "allin-abrupt-host", hostDatabase);
             NetworkLobbyGateway victimGateway = cinematicGateway(
                     "allin-abrupt-victim", victimDatabase);
             NetworkLobbyGateway witnessGateway = cinematicGateway(
                     "allin-abrupt-witness", witnessDatabase)) {
            LobbySession host = hostGateway.open(
                    request(false, "Anfitrion", port, 1))
                    .get(5, TimeUnit.SECONDS);
            LobbySession victim = victimGateway.open(
                    request(true, "Alliner", port, 1))
                    .get(5, TimeUnit.SECONDS);
            LobbySession witness = witnessGateway.open(
                    request(true, "Testigo", port, 1))
                    .get(5, TimeUnit.SECONDS);
            try {
                await(() -> host.snapshot().participants().size() == 3
                                && victim.snapshot().participants().size() == 3
                                && witness.snapshot().participants().size() == 3,
                        Duration.ofSeconds(6));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                GdxScenarioRenderer hostRenderer = attach(
                        host.tableSession().toCompletableFuture()
                                .get(8, TimeUnit.SECONDS), 3, -1);
                TableSession victimTable = victim.tableSession()
                        .toCompletableFuture().get(12, TimeUnit.SECONDS);
                GdxScenarioRenderer victimRenderer
                        = new GdxScenarioRenderer(victimTable, 3);
                victimRenderer.allInOnHand(1,
                        () -> closeClientTransportUnchecked(victim));
                victimTable.attach(victimRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                GdxScenarioRenderer witnessRenderer = attach(
                        witness.tableSession().toCompletableFuture()
                                .get(12, TimeUnit.SECONDS), 3, -1);

                await(() -> hostRenderer.isClosed()
                                && witnessRenderer.isClosed(),
                        Duration.ofSeconds(150));
                assertEquals(TableSessionSummary.CloseReason.RECOVERABLE_STOP,
                        hostRenderer.summary().reason());
                assertEquals(TableSessionSummary.CloseReason.RECOVERABLE_STOP,
                        witnessRenderer.summary().reason());
                assertEquals(hostRenderer.balancesByNickname(),
                        witnessRenderer.balancesByNickname());
                assertConservedLedger(hostRenderer.summary(), 3);
            } finally {
                witness.close();
                victim.close();
                host.close();
            }
        }
    }

    /**
     * Exact GDX-owned counterpart of Swing's {@code allin-controlled-exit}
     * scenario. The departing peer leaves only after its all-in has become an
     * accepted table action; the host must retain the mandatory showdown proof
     * and settle the hand normally.
     */
    @Test
    void allInControlledExitRetainsTheProofAndSettlesTheHostTable()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = database("allin-exit-host.sqlite");
        DatabaseService clientDatabase = database("allin-exit-client.sqlite");
        try (hostDatabase; clientDatabase;
             NetworkLobbyGateway hostGateway = cinematicGateway(
                     "allin-exit-host", hostDatabase);
             NetworkLobbyGateway clientGateway = cinematicGateway(
                     "allin-exit-client", clientDatabase)) {
            LobbySession host = hostGateway.open(
                    request(false, "Anfitrion", port, 1))
                    .get(5, TimeUnit.SECONDS);
            LobbySession client = clientGateway.open(
                    request(true, "Alliner", port, 1))
                    .get(5, TimeUnit.SECONDS);
            try {
                await(() -> host.snapshot().participants().size() == 2,
                        Duration.ofSeconds(5));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                TableSession hostTable = host.tableSession()
                        .toCompletableFuture().get(8, TimeUnit.SECONDS);
                GdxScenarioRenderer hostRenderer = attach(hostTable, 2, -1);
                TableSession clientTable = client.tableSession()
                        .toCompletableFuture().get(12, TimeUnit.SECONDS);
                GdxScenarioRenderer clientRenderer
                        = new GdxScenarioRenderer(clientTable, 2);
                clientRenderer.allInOnHand(1);
                clientTable.attach(clientRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                await(clientRenderer::hasAcceptedLocalAllIn,
                        Duration.ofSeconds(12));
                clientTable.commands().submit(new TableCommand.ExitGame());

                await(clientRenderer::isClosed, Duration.ofSeconds(12));
                await(hostRenderer::isClosed, Duration.ofSeconds(30));
                assertEquals(TableSessionSummary.CloseReason.EXITED,
                        clientRenderer.summary().reason());
                assertEquals(TableSessionSummary.CloseReason.COMPLETED,
                        hostRenderer.summary().reason());
                assertEquals(1, hostRenderer.completedHands());
                assertTrue(hostRenderer.sawAllInAction(),
                        "host never accepted the departing peer's all-in");
                assertTrue(hostRenderer.sawAllInCinematic(),
                        "host skipped the all-in causal barrier");
                double stacks = hostRenderer.summary().balances().stream()
                        .mapToDouble(TableSessionSummary.PlayerBalance::finalStack)
                        .sum();
                double buyins = hostRenderer.summary().balances().stream()
                        .mapToDouble(TableSessionSummary.PlayerBalance::totalBuyin)
                        .sum();
                assertEquals(buyins, stacks, 0.001d,
                        "all-in controlled exit must conserve the ledger");
            } finally {
                client.close();
                host.close();
            }
        }
    }

    /** Exact GDX-owned counterpart of Swing's {@code allin-single-board}. */
    @Test
    void allInSingleBoardCompletesWithOneBoardAndConservedBalances()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = database("allin-single-host.sqlite");
        DatabaseService clientDatabase = database("allin-single-client.sqlite");
        try (hostDatabase; clientDatabase;
             NetworkLobbyGateway hostGateway = cinematicGateway(
                     "allin-single-host", hostDatabase);
             NetworkLobbyGateway clientGateway = cinematicGateway(
                     "allin-single-client", clientDatabase)) {
            LobbySession host = hostGateway.open(
                    request(false, "Anfitrion", port, 1))
                    .get(5, TimeUnit.SECONDS);
            LobbySession client = clientGateway.open(
                    request(true, "Alliner", port, 1))
                    .get(5, TimeUnit.SECONDS);
            try {
                await(() -> host.snapshot().participants().size() == 2,
                        Duration.ofSeconds(5));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                GdxScenarioRenderer hostRenderer = attach(
                        host.tableSession().toCompletableFuture()
                                .get(8, TimeUnit.SECONDS), 2, -1);
                TableSession clientTable = client.tableSession()
                        .toCompletableFuture().get(12, TimeUnit.SECONDS);
                GdxScenarioRenderer clientRenderer
                        = new GdxScenarioRenderer(clientTable, 2);
                clientRenderer.allInOnHand(1);
                clientTable.attach(clientRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                await(() -> hostRenderer.isClosed()
                                && clientRenderer.isClosed(),
                        Duration.ofSeconds(30));
                assertTrue(hostRenderer.sawAllInAction());
                assertTrue(clientRenderer.hasAcceptedLocalAllIn());
                assertTrue(hostRenderer.sawAllInCinematic());
                assertTrue(clientRenderer.sawAllInCinematic());
                hostRenderer.assertComplete(1);
                clientRenderer.assertComplete(1);
            } finally {
                client.close();
                host.close();
            }
        }
    }

    @Test
    void reconnectStormReplacesFreshSocketTwiceAndAnotherPeerNextHand()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = database("storm-host.sqlite");
        DatabaseService firstDatabase = database("storm-first.sqlite");
        DatabaseService secondDatabase = database("storm-second.sqlite");
        try (hostDatabase; firstDatabase; secondDatabase;
             NetworkLobbyGateway hostGateway = gateway("storm-host",
                     hostDatabase);
             NetworkLobbyGateway firstGateway = gateway("storm-first",
                     firstDatabase);
             NetworkLobbyGateway secondGateway = gateway("storm-second",
                     secondDatabase)) {
            LobbySession host = hostGateway.open(
                    request(false, "Anfitrion", port, 4))
                    .get(5, TimeUnit.SECONDS);
            LobbySession first = firstGateway.open(
                    request(true, "Invitado1", port, 4))
                    .get(5, TimeUnit.SECONDS);
            LobbySession second = secondGateway.open(
                    request(true, "Invitado2", port, 4))
                    .get(5, TimeUnit.SECONDS);
            try {
                host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                await(() -> host.snapshot().participants().size() == 4
                                && first.snapshot().participants().size() == 4
                                && second.snapshot().participants().size() == 4,
                        Duration.ofSeconds(5));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                GdxScenarioRenderer hostRenderer = attach(
                        host.tableSession().toCompletableFuture()
                                .get(5, TimeUnit.SECONDS), 4, -1);
                GdxScenarioRenderer firstRenderer = attach(
                        first.tableSession().toCompletableFuture()
                                .get(10, TimeUnit.SECONDS), 4, 1);
                GdxScenarioRenderer secondRenderer = attach(
                        second.tableSession().toCompletableFuture()
                                .get(10, TimeUnit.SECONDS), 4, 2);

                await(firstRenderer::hasHeldAction, Duration.ofSeconds(8));
                closeNativeClientSocket(first);
                await(() -> peerReconnectionCount(host, "Invitado1") == 1
                                && peerReconnectionCount(first, "Anfitrion") == 1,
                        Duration.ofSeconds(20));
                closeNativeClientSocket(first);
                await(() -> peerReconnectionCount(host, "Invitado1") == 2
                                && peerReconnectionCount(first, "Anfitrion") == 2,
                        Duration.ofSeconds(20));
                firstRenderer.releaseHeldAction();

                await(secondRenderer::hasHeldAction, Duration.ofSeconds(15));
                closeNativeClientSocket(second);
                await(() -> peerReconnectionCount(host, "Invitado2") == 1
                                && peerReconnectionCount(second, "Anfitrion") == 1,
                        Duration.ofSeconds(20));
                secondRenderer.releaseHeldAction();

                await(() -> hostRenderer.isClosed()
                                && firstRenderer.isClosed()
                                && secondRenderer.isClosed(),
                        Duration.ofSeconds(40));
                assertEquals(2, peerReconnectionCount(host, "Invitado1"));
                assertEquals(1, peerReconnectionCount(host, "Invitado2"));
                hostRenderer.assertComplete(4);
                firstRenderer.assertComplete(4);
                secondRenderer.assertComplete(4);
                assertEquals(hostRenderer.balancesByNickname(),
                        firstRenderer.balancesByNickname());
                assertEquals(hostRenderer.balancesByNickname(),
                        secondRenderer.balancesByNickname());
            } finally {
                second.close();
                first.close();
                host.close();
            }
        }
    }

    /** Exact GDX homologue of Swing's {@code dual-reconnect} scenario. */
    @Test
    void dualReconnectRecoversTwoPeersTogetherAndSettlesEveryGdxTable()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = database("dual-host.sqlite");
        DatabaseService firstDatabase = database("dual-first.sqlite");
        DatabaseService secondDatabase = database("dual-second.sqlite");
        DatabaseService witnessDatabase = database("dual-witness.sqlite");
        try (hostDatabase; firstDatabase; secondDatabase; witnessDatabase;
             NetworkLobbyGateway hostGateway = gateway("dual-host", hostDatabase);
             NetworkLobbyGateway firstGateway = gateway("dual-first", firstDatabase);
             NetworkLobbyGateway secondGateway = gateway("dual-second", secondDatabase);
             NetworkLobbyGateway witnessGateway = gateway("dual-witness", witnessDatabase)) {
            LobbySession host = hostGateway.open(
                    request(false, "Anfitrion", port, 3))
                    .get(5, TimeUnit.SECONDS);
            LobbySession first = firstGateway.open(
                    request(true, "Invitado1", port, 3))
                    .get(5, TimeUnit.SECONDS);
            LobbySession second = secondGateway.open(
                    request(true, "Invitado2", port, 3))
                    .get(5, TimeUnit.SECONDS);
            LobbySession witness = witnessGateway.open(
                    request(true, "Testigo", port, 3))
                    .get(5, TimeUnit.SECONDS);
            try {
                host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                await(() -> host.snapshot().participants().size() == 5
                                && first.snapshot().participants().size() == 5
                                && second.snapshot().participants().size() == 5
                                && witness.snapshot().participants().size() == 5,
                        Duration.ofSeconds(6));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                GdxScenarioRenderer hostRenderer = attach(
                        host.tableSession().toCompletableFuture()
                                .get(5, TimeUnit.SECONDS), 5, -1);
                GdxScenarioRenderer firstRenderer = attach(
                        first.tableSession().toCompletableFuture()
                                .get(12, TimeUnit.SECONDS), 5, 1);
                GdxScenarioRenderer secondRenderer = attach(
                        second.tableSession().toCompletableFuture()
                                .get(12, TimeUnit.SECONDS), 5, -1);
                GdxScenarioRenderer witnessRenderer = attach(
                        witness.tableSession().toCompletableFuture()
                                .get(12, TimeUnit.SECONDS), 5, -1);

                await(firstRenderer::hasHeldAction, Duration.ofSeconds(12));
                closeNativeClientSocket(first);
                closeNativeClientSocket(second);
                await(() -> peerReconnectionCount(host, "Invitado1") == 1
                                && peerReconnectionCount(first, "Anfitrion") == 1
                                && peerReconnectionCount(host, "Invitado2") == 1
                                && peerReconnectionCount(second, "Anfitrion") == 1,
                        Duration.ofSeconds(25));
                firstRenderer.releaseHeldAction();

                await(() -> hostRenderer.isClosed()
                                && firstRenderer.isClosed()
                                && secondRenderer.isClosed()
                                && witnessRenderer.isClosed(),
                        Duration.ofSeconds(55));
                hostRenderer.assertComplete(3);
                firstRenderer.assertComplete(3);
                secondRenderer.assertComplete(3);
                witnessRenderer.assertComplete(3);
                assertEquals(hostRenderer.balancesByNickname(),
                        firstRenderer.balancesByNickname());
                assertEquals(hostRenderer.balancesByNickname(),
                        secondRenderer.balancesByNickname());
                assertEquals(hostRenderer.balancesByNickname(),
                        witnessRenderer.balancesByNickname());
            } finally {
                witness.close();
                second.close();
                first.close();
                host.close();
            }
        }
    }

    /** Exact GDX homologue of Swing's {@code host-channel-flap} scenario. */
    @Test
    void hostChannelFlapRecoversEveryClientAndSettlesEveryGdxTable()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = database("flap-host.sqlite");
        DatabaseService firstDatabase = database("flap-first.sqlite");
        DatabaseService secondDatabase = database("flap-second.sqlite");
        DatabaseService thirdDatabase = database("flap-third.sqlite");
        try (hostDatabase; firstDatabase; secondDatabase; thirdDatabase;
             NetworkLobbyGateway hostGateway = gateway("flap-host", hostDatabase);
             NetworkLobbyGateway firstGateway = gateway("flap-first", firstDatabase);
             NetworkLobbyGateway secondGateway = gateway("flap-second", secondDatabase);
             NetworkLobbyGateway thirdGateway = gateway("flap-third", thirdDatabase)) {
            LobbySession host = hostGateway.open(
                    request(false, "Anfitrion", port, 2))
                    .get(5, TimeUnit.SECONDS);
            LobbySession first = firstGateway.open(
                    request(true, "Invitado1", port, 2))
                    .get(5, TimeUnit.SECONDS);
            LobbySession second = secondGateway.open(
                    request(true, "Invitado2", port, 2))
                    .get(5, TimeUnit.SECONDS);
            LobbySession third = thirdGateway.open(
                    request(true, "Invitado3", port, 2))
                    .get(5, TimeUnit.SECONDS);
            try {
                host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                await(() -> host.snapshot().participants().size() == 5
                                && first.snapshot().participants().size() == 5
                                && second.snapshot().participants().size() == 5
                                && third.snapshot().participants().size() == 5,
                        Duration.ofSeconds(6));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                GdxScenarioRenderer hostRenderer = attach(
                        host.tableSession().toCompletableFuture()
                                .get(5, TimeUnit.SECONDS), 5, -1);
                GdxScenarioRenderer firstRenderer = attach(
                        first.tableSession().toCompletableFuture()
                                .get(12, TimeUnit.SECONDS), 5, 1);
                GdxScenarioRenderer secondRenderer = attach(
                        second.tableSession().toCompletableFuture()
                                .get(12, TimeUnit.SECONDS), 5, -1);
                GdxScenarioRenderer thirdRenderer = attach(
                        third.tableSession().toCompletableFuture()
                                .get(12, TimeUnit.SECONDS), 5, -1);

                await(firstRenderer::hasHeldAction, Duration.ofSeconds(12));
                closeNativeClientSocket(first);
                closeNativeClientSocket(second);
                closeNativeClientSocket(third);
                await(() -> peerReconnectionCount(host, "Invitado1") == 1
                                && peerReconnectionCount(first, "Anfitrion") == 1
                                && peerReconnectionCount(host, "Invitado2") == 1
                                && peerReconnectionCount(second, "Anfitrion") == 1
                                && peerReconnectionCount(host, "Invitado3") == 1
                                && peerReconnectionCount(third, "Anfitrion") == 1,
                        Duration.ofSeconds(30));
                firstRenderer.releaseHeldAction();

                await(() -> hostRenderer.isClosed()
                                && firstRenderer.isClosed()
                                && secondRenderer.isClosed()
                                && thirdRenderer.isClosed(),
                        Duration.ofSeconds(50));
                hostRenderer.assertComplete(2);
                firstRenderer.assertComplete(2);
                secondRenderer.assertComplete(2);
                thirdRenderer.assertComplete(2);
                assertEquals(hostRenderer.balancesByNickname(),
                        firstRenderer.balancesByNickname());
                assertEquals(hostRenderer.balancesByNickname(),
                        secondRenderer.balancesByNickname());
                assertEquals(hostRenderer.balancesByNickname(),
                        thirdRenderer.balancesByNickname());
            } finally {
                third.close();
                second.close();
                first.close();
                host.close();
            }
        }
    }

    /** Exact GDX homologue of Swing's {@code reconnect-every-street}. */
    @Test
    void reconnectEveryStreetCompletesFourHandsWithIdenticalSettlements()
            throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        DatabaseService hostDatabase = database("streets-host.sqlite");
        DatabaseService actorDatabase = database("streets-actor.sqlite");
        DatabaseService witnessDatabase = database("streets-witness.sqlite");
        try (hostDatabase; actorDatabase; witnessDatabase;
             NetworkLobbyGateway hostGateway = gateway("streets-host", hostDatabase);
             NetworkLobbyGateway actorGateway = gateway("streets-actor", actorDatabase);
             NetworkLobbyGateway witnessGateway = gateway(
                     "streets-witness", witnessDatabase)) {
            LobbySession host = hostGateway.open(
                    request(false, "Anfitrion", port, 4))
                    .get(5, TimeUnit.SECONDS);
            LobbySession actor = actorGateway.open(
                    request(true, "Invitado", port, 4))
                    .get(5, TimeUnit.SECONDS);
            LobbySession witness = witnessGateway.open(
                    request(true, "Testigo", port, 4))
                    .get(5, TimeUnit.SECONDS);
            try {
                host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                await(() -> host.snapshot().participants().size() == 4
                                && actor.snapshot().participants().size() == 4
                                && witness.snapshot().participants().size() == 4,
                        Duration.ofSeconds(6));
                host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);

                GdxScenarioRenderer hostRenderer = attach(
                        host.tableSession().toCompletableFuture()
                                .get(5, TimeUnit.SECONDS), 4, -1);
                TableSession actorTable = actor.tableSession().toCompletableFuture()
                        .get(12, TimeUnit.SECONDS);
                GdxScenarioRenderer actorRenderer
                        = new GdxScenarioRenderer(actorTable, 4);
                actorRenderer.gateActionOnStreet(1,
                        TableSnapshot.Street.PREFLOP);
                actorTable.attach(actorRenderer).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                GdxScenarioRenderer witnessRenderer = attach(
                        witness.tableSession().toCompletableFuture()
                                .get(12, TimeUnit.SECONDS), 4, -1);

                TableSnapshot.Street[] streets = {
                    TableSnapshot.Street.PREFLOP,
                    TableSnapshot.Street.FLOP,
                    TableSnapshot.Street.TURN,
                    TableSnapshot.Street.RIVER
                };
                for (int index = 0; index < streets.length; index++) {
                    int occurrence = index + 1;
                    await(actorRenderer::hasHeldAction,
                            Duration.ofSeconds(20));
                    closeNativeClientSocket(actor);
                    await(() -> peerReconnectionCount(host, "Invitado")
                                    == occurrence
                                    && peerReconnectionCount(actor, "Anfitrion")
                                    == occurrence,
                            Duration.ofSeconds(25));
                    if (occurrence < streets.length) {
                        actorRenderer.releaseHeldActionAndGate(
                                occurrence + 1L, streets[occurrence]);
                    } else {
                        actorRenderer.releaseHeldAction();
                    }
                }

                await(() -> hostRenderer.isClosed()
                                && actorRenderer.isClosed()
                                && witnessRenderer.isClosed(),
                        Duration.ofSeconds(60));
                assertEquals(4, peerReconnectionCount(host, "Invitado"));
                hostRenderer.assertComplete(4);
                actorRenderer.assertComplete(4);
                witnessRenderer.assertComplete(4);
                assertEquals(hostRenderer.balancesByNickname(),
                        actorRenderer.balancesByNickname());
                assertEquals(hostRenderer.balancesByNickname(),
                        witnessRenderer.balancesByNickname());
            } finally {
                witness.close();
                actor.close();
                host.close();
            }
        }
    }

    private DatabaseService database(String filename) throws Exception {
        DatabaseService database = new DatabaseService(
                temporary.resolve(filename).toString());
        database.start();
        return database;
    }

    private void runNormalTopology(String label, int clientCount,
            int botCount, int hands) throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        List<DatabaseService> databases = new ArrayList<>();
        List<NetworkLobbyGateway> gateways = new ArrayList<>();
        List<LobbySession> sessions = new ArrayList<>();
        try {
            DatabaseService hostDatabase = database(label + "-host.sqlite");
            databases.add(hostDatabase);
            NetworkLobbyGateway hostGateway = gateway(label + "-host",
                    hostDatabase);
            gateways.add(hostGateway);
            LobbySession host = hostGateway.open(
                    request(false, "Anfitrion", port, hands))
                    .get(5, TimeUnit.SECONDS);
            sessions.add(host);

            for (int index = 1; index <= clientCount; index++) {
                DatabaseService clientDatabase = database(label + "-client-"
                        + index + ".sqlite");
                databases.add(clientDatabase);
                NetworkLobbyGateway clientGateway = gateway(label + "-client-"
                        + index, clientDatabase);
                gateways.add(clientGateway);
                sessions.add(clientGateway.open(request(true,
                        "Invitado" + index, port, hands))
                        .get(8, TimeUnit.SECONDS));
            }
            for (int index = 0; index < botCount; index++) {
                host.submit(new LobbyCommand.AddBot()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
            }
            int expectedPlayers = 1 + clientCount + botCount;
            await(() -> sessions.stream().allMatch(session ->
                            session.snapshot().participants().size()
                            == expectedPlayers),
                    Duration.ofSeconds(10));
            host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            List<GdxScenarioRenderer> renderers = new ArrayList<>();
            for (int index = 0; index < sessions.size(); index++) {
                TableSession table = sessions.get(index).tableSession()
                        .toCompletableFuture().get(index == 0 ? 8 : 20,
                                TimeUnit.SECONDS);
                renderers.add(attach(table, expectedPlayers, -1));
            }
            await(() -> renderers.stream().allMatch(
                            GdxScenarioRenderer::isClosed),
                    Duration.ofSeconds(Math.max(45, hands * 20L)));
            Map<String, Double> hostBalances
                    = renderers.get(0).balancesByNickname();
            for (GdxScenarioRenderer renderer : renderers) {
                renderer.assertComplete(hands);
                assertEquals(hostBalances, renderer.balancesByNickname(),
                        label + " settlement divergence");
            }
        } finally {
            for (int index = sessions.size() - 1; index >= 0; index--) {
                sessions.get(index).close();
            }
            for (int index = gateways.size() - 1; index >= 0; index--) {
                gateways.get(index).close();
            }
            for (int index = databases.size() - 1; index >= 0; index--) {
                databases.get(index).close();
            }
        }
    }

    private NetworkLobbyGateway gateway(String directory,
            DatabaseService database) throws Exception {
        return GdxNetworkHumanProjectionIntegrationTest.gateway(
                temporary.resolve(directory), database);
    }

    private NetworkLobbyGateway cinematicGateway(String directory,
            DatabaseService database) {
        return GdxNetworkHumanProjectionIntegrationTest.cinematicGateway(
                temporary.resolve(directory), database);
    }

    private static GdxScenarioRenderer attach(TableSession table,
            int players, long gatedHand) throws Exception {
        GdxScenarioRenderer renderer = new GdxScenarioRenderer(table, players);
        if (gatedHand > 0) {
            renderer.gateActionOnHand(gatedHand);
        }
        table.attach(renderer).toCompletableFuture().get(5, TimeUnit.SECONDS);
        return renderer;
    }

    private static NewGameRequest request(boolean joining, String nickname,
            int port, int hands) {
        return request(joining, nickname, port, hands, true);
    }

    private static NewGameRequest request(boolean joining, String nickname,
            int port, int hands, boolean botRebuy) {
        NewGameConnectionDraft.Submission connection
                = new NewGameConnectionDraft.Submission(
                        joining ? NewGameConnectionDraft.Mode.JOIN
                                : NewGameConnectionDraft.Mode.CREATE,
                        nickname, "", "127.0.0.1", Integer.toString(port),
                        null, false, false, null);
        if (joining) {
            return new NewGameRequest(connection, null);
        }
        NewGameTableDraft table = new NewGameTableDraft();
        table.setHandLimit(true);
        table.setHandLimitCount(hands);
        table.setThinkTime(false);
        table.setBotRebuy(botRebuy);
        return new NewGameRequest(connection, table.snapshot());
    }

    private static NewGameRequest runItTwiceRequest(boolean joining,
            String nickname, int port) {
        NewGameConnectionDraft.Submission connection
                = new NewGameConnectionDraft.Submission(
                        joining ? NewGameConnectionDraft.Mode.JOIN
                                : NewGameConnectionDraft.Mode.CREATE,
                        nickname, "", "127.0.0.1", Integer.toString(port),
                        null, false, false, null);
        if (joining) {
            return new NewGameRequest(connection, null);
        }
        NewGameTableDraft table = new NewGameTableDraft();
        table.setHandLimit(true);
        table.setHandLimitCount(1);
        table.setThinkTime(false);
        table.setRunItTwice(true);
        return new NewGameRequest(connection, table.snapshot());
    }

    private static GameDecisionSink nativeRunItTwiceDecisions(
            AtomicInteger dialogs,
            AtomicReference<GdxTableDialog> dialogRef,
            AtomicReference<CoronaPokerGdxTable> tableRef,
            boolean acceptImmediately) {
        return new GdxGameDecisionSink(GameText.keys(), dialog -> {
            if (!"RUN IT TWICE".equals(dialog.title())) {
                throw new AssertionError("unexpected GDX decision dialog: "
                        + dialog.title());
            }
            dialogs.incrementAndGet();
            if (!dialogRef.compareAndSet(null, dialog)) {
                throw new AssertionError("duplicate native GDX RIT dialog");
            }
            CoronaPokerGdxTable table = tableRef.get();
            if (table == null) {
                throw new AssertionError(
                        "RIT dialog arrived before the real GDX table opened");
            }
            table.showDialog(dialog);
            if (acceptImmediately
                    && !table.resolveActiveDialogChoice(true)) {
                throw new AssertionError(
                        "the native GDX RIT dialog did not accept the vote");
            }
        });
    }

    private static NewGameRequest recoveryRequest(String nickname, int port,
            RecoverableGameRepository.RecoverableGame recovered) {
        return recoveryRequest(nickname, port, recovered,
                recovered.settings().botRebuy());
    }

    private static NewGameRequest recoveryRequest(String nickname, int port,
            RecoverableGameRepository.RecoverableGame recovered,
            boolean botRebuy) {
        NewGameConnectionDraft.Submission connection
                = new NewGameConnectionDraft.Submission(
                        NewGameConnectionDraft.Mode.RECOVER, nickname, "",
                        "127.0.0.1", Integer.toString(port), null, false, true,
                        recovered.id());
        NewGameTableDraft table = NewGameTableDraft.from(recovered.settings());
        table.setBotRebuy(botRebuy);
        return new NewGameRequest(connection, table.snapshot());
    }

    private static void assertConservedLedger(TableSessionSummary summary,
            int expectedPlayers) {
        assertNotNull(summary);
        assertEquals(expectedPlayers, summary.balances().size());
        double stacks = summary.balances().stream()
                .mapToDouble(TableSessionSummary.PlayerBalance::finalStack)
                .sum();
        double buyins = summary.balances().stream()
                .mapToDouble(TableSessionSummary.PlayerBalance::totalBuyin)
                .sum();
        assertEquals(buyins, stacks, 0.001d,
                "aborted hand must refund and conserve the complete ledger");
    }

    private static GameDecisionSink acceptingImmediateRebuyDecisions(
            AtomicInteger choices) {
        GameDecisionSink fallback = GameDecisionSink.noop();
        return (GameDecisionSink) Proxy.newProxyInstance(
                GameDecisionSink.class.getClassLoader(),
                new Class<?>[]{GameDecisionSink.class},
                (proxy, method, arguments) -> {
                    if ("showRebuy".equals(method.getName())) {
                        GameDecisionSink.RebuyRequest request
                                = (GameDecisionSink.RebuyRequest) arguments[0];
                        choices.incrementAndGet();
                        GameDecisionSink.RebuyResult accepted
                                = new GameDecisionSink.RebuyResult(true,
                                        request.defaultAmount());
                        return new GameDecisionSink.RebuyHandle() {
                            @Override
                            public CompletionStage<GameDecisionSink.RebuyResult>
                                    result() {
                                return CompletableFuture.completedFuture(accepted);
                            }

                            @Override public void close() { }
                        };
                    }
                    return method.invoke(fallback, arguments);
                });
    }

    private static final class StopAfterRecordedActionRenderer
            implements TableRenderer {
        private final TableSession table;
        private final AtomicReference<GdxTableViewState> state
                = new AtomicReference<>();
        private final AtomicReference<CoronaPokerGdxTable> productTable
                = new AtomicReference<>();
        private final AtomicInteger localTurns = new AtomicInteger();
        private final AtomicBoolean held = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicReference<TableSessionSummary> summary
                = new AtomicReference<>();

        StopAfterRecordedActionRenderer(TableSession table) {
            this.table = table;
        }

        @Override
        public CompletionStage<Void> open(TableSnapshot initialState) {
            GdxTableViewState projection = new GdxTableViewState(initialState);
            state.set(projection);
            productTable.set(new CoronaPokerGdxTable(60, projection,
                    table.commands(), () -> { }, new GdxGameLogSink(), null));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> render(TableVisualEvent event) {
            GdxTableViewState projection = state.get();
            assertNotNull(projection, "renderer must open before events arrive");
            projection.apply(event);
            if (event instanceof TableVisualEvent.ActionControls controls
                    && controls.state().callAction()
                    != ActionControlState.CallAction.DISABLED) {
                if (localTurns.incrementAndGet() == 1) {
                    assertTrue(productTable.get().activateCheckOrCallAction(),
                            "native GDX recovery setup action did not submit");
                } else {
                    held.set(true);
                }
            } else if (event instanceof TableVisualEvent.CloseTable close) {
                summary.set(close.summary());
                closed.set(true);
            }
            return CompletableFuture.completedFuture(null);
        }

        boolean heldAfterRecordedAction() { return held.get(); }
        boolean closed() { return closed.get(); }
        TableSessionSummary summary() { return summary.get(); }
        @Override public void close() { }
    }

    private static final class RaiseMixScenarioRenderer
            implements TableRenderer {
        private static final int EXPECTED_HANDS = 10;
        private static final int EXPECTED_PLAYERS = 5;
        private static final int TARGET_RAISE_SUBMISSIONS = 6;

        private final TableSession table;
        private final AtomicInteger sharedRaiseSubmissions;
        private final AtomicReference<GdxTableViewState> state
                = new AtomicReference<>();
        private final AtomicReference<TableSessionSummary> summary
                = new AtomicReference<>();
        private final AtomicInteger endedHands = new AtomicInteger();
        private final AtomicInteger acceptedRaiseActions = new AtomicInteger();
        private final AtomicLong lastControlSequence = new AtomicLong(-1L);
        private final AtomicBoolean sawLocalControls = new AtomicBoolean();
        private final AtomicBoolean sawRemoteAction = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final EnumSet<TableSnapshot.Street> streets
                = EnumSet.noneOf(TableSnapshot.Street.class);

        RaiseMixScenarioRenderer(TableSession table,
                AtomicInteger sharedRaiseSubmissions) {
            this.table = table;
            this.sharedRaiseSubmissions = sharedRaiseSubmissions;
        }

        @Override
        public synchronized CompletionStage<Void> open(
                TableSnapshot initialState) {
            state.set(new GdxTableViewState(initialState));
            streets.add(initialState.street());
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public synchronized CompletionStage<Void> render(
                TableVisualEvent event) {
            GdxTableViewState projection = state.get();
            assertNotNull(projection, "renderer must open before events arrive");
            projection.apply(event);
            TableSnapshot snapshot = projection.snapshot();
            streets.add(snapshot.street());
            assertFalse(snapshot.localNickname().isBlank());
            assertTrue(snapshot.pot() >= 0d);

            if (event instanceof TableVisualEvent.HandBoundary boundary
                    && boundary.phase()
                    == TableVisualEvent.HandBoundary.Phase.END) {
                endedHands.incrementAndGet();
            } else if (event instanceof TableVisualEvent.ActionControls controls
                    && (controls.state().callAction()
                    != ActionControlState.CallAction.DISABLED
                    || controls.state().allInEnabled())
                    && lastControlSequence.getAndSet(controls.sequence())
                    != controls.sequence()) {
                assertEquals(snapshot.localNickname(),
                        snapshot.currentTurnNickname());
                sawLocalControls.set(true);
                ActionControlState actions = controls.state();
                assertExactCents(actions.callAmount());
                assertExactCents(actions.raiseMinimum());
                assertExactCents(actions.raiseMaximum());
                assertExactCents(actions.raiseStep());
                assertExactCents(actions.raiseAmount());
                boolean raise = actions.raiseAction()
                        != ActionControlState.RaiseAction.DISABLED
                        && claimRaiseSubmission();
                CoronaPokerGdxTable nativeTable = new CoronaPokerGdxTable(60,
                        projection, table.commands(), () -> { },
                        new GdxGameLogSink(), null);
                if (raise) {
                    assertTrue(actions.raiseAmount() > 0d);
                    assertTrue(nativeTable.activateBetAction(),
                            "native GDX bet/raise control did not submit");
                } else if (actions.callAction()
                        != ActionControlState.CallAction.DISABLED) {
                    assertTrue(nativeTable.activateCheckOrCallAction(),
                            "native GDX check/call control did not submit");
                } else {
                    assertTrue(nativeTable.activateAllInAction(),
                            "native GDX ALL-IN control did not arm");
                    assertTrue(nativeTable.activateAllInAction(),
                            "native GDX ALL-IN control did not submit");
                }
            } else if (event instanceof TableVisualEvent.PlayerAction action) {
                assertExactCents(action.amount());
                assertExactCents(action.contributionDelta());
                if (!action.nickname().equals(snapshot.localNickname())) {
                    sawRemoteAction.set(true);
                }
                if (action.kind() == TableVisualEvent.PlayerAction.ActionKind.BET
                        || action.kind()
                        == TableVisualEvent.PlayerAction.ActionKind.RAISE
                        || action.kind()
                        == TableVisualEvent.PlayerAction.ActionKind.RERAISE) {
                    acceptedRaiseActions.incrementAndGet();
                }
            } else if (event instanceof TableVisualEvent.CloseTable close) {
                summary.set(close.summary());
                closed.set(true);
            }
            return CompletableFuture.completedFuture(null);
        }

        private boolean claimRaiseSubmission() {
            int current;
            do {
                current = sharedRaiseSubmissions.get();
                if (current >= TARGET_RAISE_SUBMISSIONS) return false;
            } while (!sharedRaiseSubmissions.compareAndSet(current,
                    current + 1));
            return true;
        }

        private static void assertExactCents(double amount) {
            assertTrue(Double.isFinite(amount) && amount >= 0d);
            assertEquals(Math.rint(amount * 100d), amount * 100d, 0.000001d,
                    "betting amount lost exact-cent precision");
        }

        boolean closed() {
            return closed.get();
        }

        int acceptedRaiseActions() {
            return acceptedRaiseActions.get();
        }

        Map<String, Double> balancesByNickname() {
            TableSessionSummary result = summary.get();
            assertNotNull(result);
            Map<String, Double> balances = new TreeMap<>();
            result.balances().forEach(balance -> balances.put(
                    balance.nickname(), balance.finalStack()));
            return balances;
        }

        void assertComplete() {
            GdxTableViewState projection = state.get();
            TableSessionSummary result = summary.get();
            assertNotNull(projection);
            assertNotNull(result);
            assertEquals(TableSessionSummary.CloseReason.COMPLETED,
                    result.reason());
            assertEquals(EXPECTED_HANDS, endedHands.get());
            assertEquals(EXPECTED_HANDS, result.handCount());
            assertEquals(EXPECTED_PLAYERS, result.balances().size());
            assertTrue(sawLocalControls.get());
            assertTrue(sawRemoteAction.get());
            assertTrue(streets.containsAll(EnumSet.of(
                    TableSnapshot.Street.PREFLOP,
                    TableSnapshot.Street.FLOP,
                    TableSnapshot.Street.TURN,
                    TableSnapshot.Street.RIVER,
                    TableSnapshot.Street.SHOWDOWN)));
            double stacks = result.balances().stream()
                    .mapToDouble(TableSessionSummary.PlayerBalance::finalStack)
                    .sum();
            double buyins = result.balances().stream()
                    .mapToDouble(TableSessionSummary.PlayerBalance::totalBuyin)
                    .sum();
            assertEquals(buyins, stacks, 0.001d,
                    "raise-mix must conserve the complete table ledger");
        }

        @Override
        public void close() { }
    }

    private static void closeNativeClientSocket(LobbySession session)
            throws Exception {
        Object transport = field(session, "resource");
        Object connection = field(transport, "serverConnection");
        Object generation = field(connection, "generation");
        ((Socket) field(generation, "socket")).close();
    }

    private static boolean clientReconnectStarted(LobbySession session) {
        try {
            Object transport = field(session, "resource");
            Object connection = field(transport, "serverConnection");
            return (boolean) field(connection, "reconnecting")
                    || (int) field(connection, "reconnectionCount") > 0;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("cannot inspect GDX reconnect attempt",
                    failure);
        }
    }

    private static void closeClientTransport(LobbySession session)
            throws Exception {
        ((AutoCloseable) field(session, "resource")).close();
    }

    private static void closeClientTransportUnchecked(LobbySession session) {
        try {
            closeClientTransport(session);
        } catch (Exception failure) {
            throw new AssertionError("cannot stop GDX client transport", failure);
        }
    }

    private static void closeNativeClientSocketUnchecked(LobbySession session) {
        try {
            closeNativeClientSocket(session);
        } catch (Exception failure) {
            throw new AssertionError("cannot cut GDX client socket", failure);
        }
    }

    private static int peerReconnectionCount(LobbySession session,
            String nickname) {
        try {
            Object transport = field(session, "resource");
            Object channel = field(transport, "gameChannel");
            var method = channel.getClass().getDeclaredMethod(
                    "peerReconnectionCount", String.class);
            method.setAccessible(true);
            return (int) method.invoke(channel, nickname);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("cannot inspect reconnect count", failure);
        }
    }

    private static Object field(Object target, String name)
            throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void await(BooleanSupplier condition, Duration timeout)
            throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        while (!condition.getAsBoolean() && Instant.now().isBefore(deadline)) {
            Thread.sleep(10L);
        }
        assertTrue(condition.getAsBoolean(),
                "timed out waiting for GDX reconnect scenario");
    }
}
