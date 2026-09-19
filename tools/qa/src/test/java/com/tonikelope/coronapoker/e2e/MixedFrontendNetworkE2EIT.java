package com.tonikelope.coronapoker.e2e;

import com.tonikelope.coronapoker.CoreGameTableFactory;
import com.tonikelope.coronapoker.core.DatabaseService;
import com.tonikelope.coronapoker.core.LobbyCommand;
import com.tonikelope.coronapoker.core.LobbySession;
import com.tonikelope.coronapoker.core.LobbySnapshot;
import com.tonikelope.coronapoker.core.NewGameConnectionDraft;
import com.tonikelope.coronapoker.core.NewGameRequest;
import com.tonikelope.coronapoker.core.NewGameTableDraft;
import com.tonikelope.coronapoker.core.RecoverableGameRepository;
import com.tonikelope.coronapoker.core.game.ActionControlState;
import com.tonikelope.coronapoker.core.game.GameConfigCodecV1;
import com.tonikelope.coronapoker.core.game.GameDecisionSink;
import com.tonikelope.coronapoker.core.game.GameDialogSink;
import com.tonikelope.coronapoker.core.game.GameLogSink;
import com.tonikelope.coronapoker.core.game.GamePresentationSettings;
import com.tonikelope.coronapoker.core.game.GameText;
import com.tonikelope.coronapoker.core.network.NetworkLobbyGateway;
import com.tonikelope.coronapoker.table.TableCommand;
import com.tonikelope.coronapoker.table.TableRenderer;
import com.tonikelope.coronapoker.table.TableSession;
import com.tonikelope.coronapoker.table.TableSessionSummary;
import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs complete hands with legacy Swing and renderer-neutral GDX peers in the
 * same authenticated table. This is deliberately above codec compatibility:
 * every endpoint owns a production Crupier, participates in SRA/consensus and
 * produces an independently conserved final ledger.
 */
@Tag("real-game-e2e")
final class MixedFrontendNetworkE2EIT {

    @TempDir Path temporary;

    @Test
    @Timeout(value = 6, unit = TimeUnit.MINUTES)
    void swingHostServesSwingAndGdxHumanClients() throws Exception {
        List<ChildNode> children = new ArrayList<>();
        DatabaseService gdxDatabase = database("swing-host-gdx-client.sqlite");
        try (gdxDatabase;
             NetworkLobbyGateway gdxGateway = gateway(
                     temporary.resolve("swing-host-gdx-client"), gdxDatabase)) {
            ChildNode host = startSwingNode(temporary.resolve("swing-host"),
                    "host", "server", 0, 2, 1, 41001L);
            children.add(host);
            assertTrue(host.await("CP_E2E_READY", Duration.ofSeconds(60)),
                    host.diagnostic());
            int port = host.intValueAfter("CP_E2E_READY", "port=");
            assertTrue(port > 0, host.diagnostic());

            ChildNode swingClient = startSwingNode(
                    temporary.resolve("swing-client"), "client", "client1",
                    port, 2, 1, 41002L);
            children.add(swingClient);
            assertTrue(swingClient.await("CP_E2E_READY", Duration.ofSeconds(60)),
                    swingClient.diagnostic());

            LobbySession gdxClient = gdxGateway.open(request(true,
                    "client2", port)).get(15, TimeUnit.SECONDS);
            try {
                await(() -> gdxClient.snapshot().participants().size() == 3,
                        Duration.ofSeconds(15));
                host.send("APPLY_LOBBY_CONFIG");
                assertTrue(host.await("CP_E2E_LOBBY_CONFIG_APPLIED hands=1"
                                + " ante=true straddle=false blinds=0.25/0.5"
                                + " think=47 showdown=12 difficulty=HARD",
                        Duration.ofSeconds(30)), host.diagnostic());
                await(() -> {
                    NewGameTableDraft.Settings settings =
                            gdxClient.snapshot().tableSettings();
                    NewGameTableDraft.BlindLevel blinds =
                            settings.selectedBlindLevel();
                    return settings.handLimit()
                            && settings.handLimitCount() == 1
                            && settings.ante()
                            && !settings.straddle()
                            && Double.compare(blinds.smallBlind(), 0.25d) == 0
                            && Double.compare(blinds.bigBlind(), 0.50d) == 0
                            && settings.thinkSeconds() == 47
                            && settings.showdownSeconds() == 12
                            && settings.botDifficulty()
                                    == NewGameTableDraft.BotDifficulty.HARD;
                }, Duration.ofSeconds(15));
                swingClient.send("DUMP_LOBBY_CONFIG");
                assertTrue(swingClient.await("CP_E2E_LOBBY_CONFIG hands=1"
                                + " ante=true straddle=false blinds=0.25/0.5"
                                + " think=47 showdown=12 difficulty=HARD",
                        Duration.ofSeconds(30)), swingClient.diagnostic());
                releaseLobbyChecks(host, swingClient);
                assertTrue(host.await("CP_E2E_LOBBY_READY",
                        Duration.ofSeconds(60)), host.diagnostic());
                assertTrue(swingClient.await("CP_E2E_LOBBY_READY",
                        Duration.ofSeconds(60)), swingClient.diagnostic());

                host.send("START_GAME");
                assertTrue(host.await("CP_E2E_GAME_START_REQUESTED",
                        Duration.ofSeconds(30)), host.diagnostic());
                TableSession table = gdxClient.tableSession()
                        .toCompletableFuture().get(30, TimeUnit.SECONDS);
                AutoCallRenderer renderer = new AutoCallRenderer(table);
                table.attach(renderer).toCompletableFuture()
                        .get(15, TimeUnit.SECONDS);

                await(() -> renderer.heldAction.get(), Duration.ofSeconds(30),
                        renderer::diagnostic);
                table.commands().submit(new TableCommand.TogglePause());
                await(() -> renderer.sawPaused.get()
                                && host.contains("CP_E2E_PAUSE_STATE paused=true")
                                && swingClient.contains(
                                        "CP_E2E_PAUSE_STATE paused=true"),
                        Duration.ofSeconds(15), () -> renderer.diagnostic()
                                + "; host=" + host.diagnostic()
                                + "; swingClient=" + swingClient.diagnostic());
                table.commands().submit(new TableCommand.TogglePause());
                await(() -> renderer.sawResumed.get()
                                && host.contains("CP_E2E_PAUSE_STATE paused=false")
                                && swingClient.contains(
                                        "CP_E2E_PAUSE_STATE paused=false"),
                        Duration.ofSeconds(15), () -> renderer.diagnostic()
                                + "; host=" + host.diagnostic()
                                + "; swingClient=" + swingClient.diagnostic());
                renderer.releaseHeldAction();

                await(() -> renderer.closed.get(), Duration.ofSeconds(75),
                        renderer::diagnostic);
                assertTrue(host.await("CP_E2E_HANDS_COMPLETE",
                        Duration.ofSeconds(30)), host.diagnostic());
                assertTrue(swingClient.await("CP_E2E_HANDS_COMPLETE",
                        Duration.ofSeconds(30)), swingClient.diagnostic());
                assertHealthySwingNode(host);
                assertHealthySwingNode(swingClient);
                assertSummary(renderer.summary.get(), "client2", 3);
            } finally {
                gdxClient.close();
            }
        } finally {
            closeChildren(children);
        }
    }

    @Test
    @Timeout(value = 6, unit = TimeUnit.MINUTES)
    void gdxHostServesSwingAndGdxHumanClients() throws Exception {
        List<ChildNode> children = new ArrayList<>();
        int port = freePort();
        DatabaseService hostDatabase = database("gdx-host.sqlite");
        DatabaseService clientDatabase = database("gdx-client.sqlite");
        try (hostDatabase; clientDatabase;
             NetworkLobbyGateway hostGateway = gateway(
                     temporary.resolve("gdx-host"), hostDatabase);
             NetworkLobbyGateway clientGateway = gateway(
                     temporary.resolve("gdx-client"), clientDatabase)) {
            LobbySession gdxHost = hostGateway.open(request(false,
                    "server", port)).get(15, TimeUnit.SECONDS);
            try {
                ChildNode swingClient = startSwingNode(
                        temporary.resolve("gdx-host-swing-client"), "client",
                        "client1", port, 2, 1, 42001L);
                children.add(swingClient);
                assertTrue(swingClient.await("CP_E2E_READY",
                        Duration.ofSeconds(60)), swingClient.diagnostic());

                LobbySession gdxClient = clientGateway.open(request(true,
                        "client2", port)).get(15, TimeUnit.SECONDS);
                try {
                    await(() -> gdxHost.snapshot().participants().size() == 3
                            && gdxClient.snapshot().participants().size() == 3,
                            Duration.ofSeconds(15));
                    NewGameTableDraft lobbyDraft = NewGameTableDraft.from(
                            gdxHost.snapshot().tableSettings());
                    lobbyDraft.setBlindStructure("qa-lobby", List.of(
                            new NewGameTableDraft.BlindLevel(0.2d, 0.4d),
                            new NewGameTableDraft.BlindLevel(0.3d, 0.6d)), 0);
                    lobbyDraft.setAnte(true);
                    lobbyDraft.setThinkSeconds(55);
                    lobbyDraft.setShowdownSeconds(15);
                    lobbyDraft.setBotDifficulty(
                            NewGameTableDraft.BotDifficulty.HARD);
                    NewGameTableDraft.Settings lobbySettings =
                            lobbyDraft.snapshot();
                    gdxHost.submit(new LobbyCommand.UpdateTableSettings(
                            lobbySettings)).toCompletableFuture()
                            .get(15, TimeUnit.SECONDS);
                    await(() -> lobbySettings.serializeForWire().equals(
                                    gdxClient.snapshot().tableSettings()
                                            .serializeForWire()),
                            Duration.ofSeconds(15));
                    swingClient.send("DUMP_LOBBY_CONFIG");
                    assertTrue(swingClient.await("CP_E2E_LOBBY_CONFIG hands=1"
                                    + " ante=true straddle=false blinds=0.2/0.4"
                                    + " think=55 showdown=15 difficulty=HARD",
                            Duration.ofSeconds(30)), swingClient.diagnostic());
                    releaseLobbyChecks(swingClient);
                    assertTrue(swingClient.await("CP_E2E_LOBBY_READY",
                            Duration.ofSeconds(60)), swingClient.diagnostic());

                    gdxHost.submit(new LobbyCommand.StartGame())
                            .toCompletableFuture().get(15, TimeUnit.SECONDS);
                    TableSession hostTable = gdxHost.tableSession()
                            .toCompletableFuture().get(15, TimeUnit.SECONDS);
                    AutoCallRenderer hostRenderer = new AutoCallRenderer(hostTable);
                    hostTable.attach(hostRenderer).toCompletableFuture()
                            .get(15, TimeUnit.SECONDS);

                    TableSession clientTable = gdxClient.tableSession()
                            .toCompletableFuture().get(30, TimeUnit.SECONDS);
                    AutoCallRenderer clientRenderer =
                            new AutoCallRenderer(clientTable);
                    clientTable.attach(clientRenderer).toCompletableFuture()
                            .get(15, TimeUnit.SECONDS);

                    await(() -> hostRenderer.heldAction.get()
                                    || clientRenderer.heldAction.get(),
                            Duration.ofSeconds(30), () -> "host="
                                    + hostRenderer.diagnostic() + "; client="
                                    + clientRenderer.diagnostic());
                    TableSession pausingTable = hostRenderer.heldAction.get()
                            ? hostTable : clientTable;
                    pausingTable.commands().submit(
                            new TableCommand.TogglePause());
                    await(() -> hostRenderer.sawPaused.get()
                                    && clientRenderer.sawPaused.get()
                                    && swingClient.contains(
                                            "CP_E2E_PAUSE_STATE paused=true"),
                            Duration.ofSeconds(15), () -> "host="
                                    + hostRenderer.diagnostic() + "; client="
                                    + clientRenderer.diagnostic() + "; swing="
                                    + swingClient.diagnostic());
                    pausingTable.commands().submit(
                            new TableCommand.TogglePause());
                    await(() -> hostRenderer.sawResumed.get()
                                    && clientRenderer.sawResumed.get()
                                    && swingClient.contains(
                                            "CP_E2E_PAUSE_STATE paused=false"),
                            Duration.ofSeconds(15), () -> "host="
                                    + hostRenderer.diagnostic() + "; client="
                                    + clientRenderer.diagnostic() + "; swing="
                                    + swingClient.diagnostic());
                    hostRenderer.releaseHeldAction();
                    clientRenderer.releaseHeldAction();

                    await(() -> hostRenderer.closed.get()
                            && clientRenderer.closed.get(), Duration.ofSeconds(75),
                            () -> "host=" + hostRenderer.diagnostic()
                                    + "; client=" + clientRenderer.diagnostic());
                    assertTrue(swingClient.await("CP_E2E_HANDS_COMPLETE",
                            Duration.ofSeconds(30)), swingClient.diagnostic());
                    assertHealthySwingNode(swingClient);
                    assertSummary(hostRenderer.summary.get(), "server", 3);
                    assertSummary(clientRenderer.summary.get(), "client2", 3);
                    assertEquals(canonicalBalances(hostRenderer.summary.get()),
                            canonicalBalances(clientRenderer.summary.get()),
                            "GDX host/client final ledgers diverged");
                } finally {
                    gdxClient.close();
                }
            } finally {
                gdxHost.close();
            }
        } finally {
            closeChildren(children);
        }
    }

    @Test
    @Timeout(value = 4, unit = TimeUnit.MINUTES)
    void gdxHostEnforcesLobbyKickPermissionsAndContinuesWithSwingPeer()
            throws Exception {
        List<ChildNode> children = new ArrayList<>();
        int port = freePort();
        DatabaseService hostDatabase = database("gdx-kick-host.sqlite");
        DatabaseService clientDatabase = database("gdx-kick-client.sqlite");
        try (hostDatabase; clientDatabase;
             NetworkLobbyGateway hostGateway = gateway(
                     temporary.resolve("gdx-kick-host"), hostDatabase);
             NetworkLobbyGateway clientGateway = gateway(
                     temporary.resolve("gdx-kick-client"), clientDatabase)) {
            LobbySession gdxHost = hostGateway.open(request(false,
                    "server", port)).get(15, TimeUnit.SECONDS);
            try {
                ChildNode swingClient = startSwingNode(temporary.resolve(
                        "gdx-kick-swing-client"), "client", "client1",
                        port, 1, 1, 42451L);
                children.add(swingClient);
                assertTrue(swingClient.await("CP_E2E_READY",
                        Duration.ofSeconds(60)), swingClient.diagnostic());

                LobbySession gdxClient = clientGateway.open(request(true,
                        "client2", port)).get(15, TimeUnit.SECONDS);
                try {
                    await(() -> gdxHost.snapshot().participants().size() == 3
                                    && gdxClient.snapshot().participants().size()
                                    == 3,
                            Duration.ofSeconds(15));

                    assertThrows(IllegalStateException.class, () -> gdxClient
                            .submit(new LobbyCommand.Kick("client1")));
                    assertEquals(3, gdxHost.snapshot().participants().size(),
                            "a non-host client altered the authoritative roster");

                    gdxHost.submit(new LobbyCommand.Kick("client2"))
                            .toCompletableFuture().get(15, TimeUnit.SECONDS);
                    await(() -> gdxHost.snapshot().participants().size() == 2
                                    && gdxClient.snapshot().phase()
                                    == LobbySnapshot.Phase.CLOSED,
                            Duration.ofSeconds(15));

                    releaseLobbyChecks(swingClient);
                    assertTrue(swingClient.await("CP_E2E_LOBBY_READY",
                            Duration.ofSeconds(60)), swingClient.diagnostic());
                    gdxHost.submit(new LobbyCommand.StartGame())
                            .toCompletableFuture().get(15, TimeUnit.SECONDS);
                    TableSession table = gdxHost.tableSession()
                            .toCompletableFuture().get(15, TimeUnit.SECONDS);
                    AutoCallRenderer renderer = new AutoCallRenderer(table);
                    table.attach(renderer).toCompletableFuture()
                            .get(15, TimeUnit.SECONDS);
                    renderer.releaseHeldAction();
                    await(renderer.closed::get, Duration.ofSeconds(75),
                            renderer::diagnostic);
                    assertTrue(swingClient.await("CP_E2E_HANDS_COMPLETE",
                            Duration.ofSeconds(30)), swingClient.diagnostic());
                    assertHealthySwingNode(swingClient);
                    assertSummary(renderer.summary.get(), "server", 2);
                } finally {
                    gdxClient.close();
                }
            } finally {
                gdxHost.close();
            }
        } finally {
            closeChildren(children);
        }
    }

    @Test
    @Timeout(value = 4, unit = TimeUnit.MINUTES)
    void swingPasswordHostAcceptsGdxClientAndCompletesHand()
            throws Exception {
        List<ChildNode> children = new ArrayList<>();
        DatabaseService clientDatabase = database(
                "swing-password-gdx-client.sqlite");
        try (clientDatabase;
             NetworkLobbyGateway clientGateway = gateway(temporary.resolve(
                     "swing-password-gdx-client"), clientDatabase)) {
            ChildNode swingHost = startSwingNode(temporary.resolve(
                    "swing-password-host"), "host", "server", 0, 1, 1,
                    42501L, "normal", true, "clave segura");
            children.add(swingHost);
            assertTrue(swingHost.await("CP_E2E_READY",
                    Duration.ofSeconds(60)), swingHost.diagnostic());
            int port = swingHost.intValueAfter("CP_E2E_READY", "port=");
            LobbySession gdxClient = clientGateway.open(passwordRequest(true,
                    "client1", port, "clave segura", 1))
                    .get(15, TimeUnit.SECONDS);
            try {
                await(() -> gdxClient.snapshot().participants().size() == 2,
                        Duration.ofSeconds(15));
                releaseLobbyChecks(swingHost);
                assertTrue(swingHost.await("CP_E2E_LOBBY_READY",
                        Duration.ofSeconds(60)), swingHost.diagnostic());
                swingHost.send("START_GAME");

                TableSession table = gdxClient.tableSession()
                        .toCompletableFuture().get(30, TimeUnit.SECONDS);
                AutoCallRenderer renderer = new AutoCallRenderer(table);
                table.attach(renderer).toCompletableFuture()
                        .get(15, TimeUnit.SECONDS);
                renderer.releaseHeldAction();
                await(renderer.closed::get, Duration.ofSeconds(75),
                        renderer::diagnostic);
                assertTrue(swingHost.await("CP_E2E_HANDS_COMPLETE",
                        Duration.ofSeconds(30)), swingHost.diagnostic());
                assertHealthySwingNode(swingHost);
                assertSummary(renderer.summary.get(), "client1", 2);
            } finally {
                gdxClient.close();
            }
        } finally {
            closeChildren(children);
        }
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void swingPasswordHostRejectsWrongGdxPassword() throws Exception {
        List<ChildNode> children = new ArrayList<>();
        DatabaseService clientDatabase = database(
                "swing-password-wrong-gdx-client.sqlite");
        try (clientDatabase;
             NetworkLobbyGateway clientGateway = gateway(temporary.resolve(
                     "swing-password-wrong-gdx-client"), clientDatabase)) {
            ChildNode swingHost = startSwingNode(temporary.resolve(
                    "swing-password-rejection-host"), "host", "server", 0,
                    1, 1, 42551L, "normal", true, "clave correcta");
            children.add(swingHost);
            assertTrue(swingHost.await("CP_E2E_READY",
                    Duration.ofSeconds(60)), swingHost.diagnostic());
            int port = swingHost.intValueAfter("CP_E2E_READY", "port=");

            CompletableFuture<LobbySession> rejected = clientGateway.open(
                    passwordRequest(true, "client1", port,
                            "clave incorrecta", 1));
            assertThrows(ExecutionException.class,
                    () -> rejected.get(15, TimeUnit.SECONDS));
            assertFalse(swingHost.contains("TABLE_FAILURE_V1"),
                    swingHost.diagnostic());
        } finally {
            closeChildren(children);
        }
    }

    @Test
    @Timeout(value = 4, unit = TimeUnit.MINUTES)
    void gdxPasswordHostAcceptsSwingClientAndCompletesHand()
            throws Exception {
        List<ChildNode> children = new ArrayList<>();
        int port = freePort();
        DatabaseService hostDatabase = database(
                "gdx-password-host.sqlite");
        try (hostDatabase;
             NetworkLobbyGateway hostGateway = gateway(temporary.resolve(
                     "gdx-password-host"), hostDatabase)) {
            LobbySession gdxHost = hostGateway.open(passwordRequest(false,
                    "server", port, "clave segura", 1))
                    .get(15, TimeUnit.SECONDS);
            try {
                ChildNode swingClient = startSwingNode(temporary.resolve(
                        "gdx-password-swing-client"), "client", "client1",
                        port, 1, 1, 42601L, "normal", true,
                        "clave segura");
                children.add(swingClient);
                assertTrue(swingClient.await("CP_E2E_READY",
                        Duration.ofSeconds(60)), swingClient.diagnostic());
                await(() -> gdxHost.snapshot().participants().size() == 2,
                        Duration.ofSeconds(15));
                releaseLobbyChecks(swingClient);
                assertTrue(swingClient.await("CP_E2E_LOBBY_READY",
                        Duration.ofSeconds(60)), swingClient.diagnostic());

                gdxHost.submit(new LobbyCommand.StartGame())
                        .toCompletableFuture().get(15, TimeUnit.SECONDS);
                TableSession table = gdxHost.tableSession()
                        .toCompletableFuture().get(15, TimeUnit.SECONDS);
                AutoCallRenderer renderer = new AutoCallRenderer(table);
                table.attach(renderer).toCompletableFuture()
                        .get(15, TimeUnit.SECONDS);
                renderer.releaseHeldAction();
                await(renderer.closed::get, Duration.ofSeconds(75),
                        renderer::diagnostic);
                assertTrue(swingClient.await("CP_E2E_HANDS_COMPLETE",
                        Duration.ofSeconds(30)), swingClient.diagnostic());
                assertHealthySwingNode(swingClient);
                assertSummary(renderer.summary.get(), "server", 2);
            } finally {
                gdxHost.close();
            }
        } finally {
            closeChildren(children);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void gdxHostPropagatesLiveRulesToSwingClientAndCompletesGame()
            throws Exception {
        List<ChildNode> children = new ArrayList<>();
        int port = freePort();
        DatabaseService hostDatabase = database(
                "gdx-live-settings-host.sqlite");
        try (hostDatabase;
             NetworkLobbyGateway hostGateway = gateway(temporary.resolve(
                     "gdx-live-settings-host"), hostDatabase)) {
            LobbySession gdxHost = hostGateway.open(request(false,
                    "server", port, 2)).get(15, TimeUnit.SECONDS);
            try {
                ChildNode swingClient = startSwingNode(temporary.resolve(
                        "gdx-live-settings-swing-client"), "client",
                        "client1", port, 1, 3, 42701L);
                children.add(swingClient);
                assertTrue(swingClient.await("CP_E2E_READY",
                        Duration.ofSeconds(60)), swingClient.diagnostic());
                await(() -> gdxHost.snapshot().participants().size() == 2,
                        Duration.ofSeconds(15));
                releaseLobbyChecks(swingClient);
                assertTrue(swingClient.await("CP_E2E_LOBBY_READY",
                        Duration.ofSeconds(60)), swingClient.diagnostic());

                gdxHost.submit(new LobbyCommand.StartGame())
                        .toCompletableFuture().get(15, TimeUnit.SECONDS);
                TableSession table = gdxHost.tableSession()
                        .toCompletableFuture().get(15, TimeUnit.SECONDS);
                AutoCallRenderer renderer = new AutoCallRenderer(table);
                table.attach(renderer).toCompletableFuture()
                        .get(15, TimeUnit.SECONDS);
                await(() -> renderer.heldAction.get()
                                && renderer.configuration.get() != null,
                        Duration.ofSeconds(30), renderer::diagnostic);

                GameConfigCodecV1.Configuration current
                        = renderer.configuration.get();
                table.commands().submit(new TableCommand.ApplyGameConfiguration(
                        current.withHands(3)
                                .withBlindSettings(0.2d, 0.4d, 0, 1, 1.0d,
                                        List.of(
                                                new GameConfigCodecV1.BlindLevel(0.2d, 0.4d),
                                                new GameConfigCodecV1.BlindLevel(0.3d, 0.6d),
                                                new GameConfigCodecV1.BlindLevel(0.5d, 1.0d)))
                                .withAnte(true)
                                .withIwtsth(true)
                                .withRunItTwice(true)
                                .withRabbitHunting(2)
                                .withBotRebuy(true)
                                .withBotBalanceToHumans(true)));
                await(() -> hasQaLiveConfiguration(
                                renderer.configuration.get()),
                        Duration.ofSeconds(15), renderer::diagnostic);
                swingClient.send("DUMP_CONFIG");
                assertTrue(swingClient.await(
                        "CP_E2E_GAME_CONFIG hands=3 iwtsth=true rit=true"
                                + " rabbit=2 botRebuy=true botBalance=true"
                                + " blinds=0.2/0.4 blindInterval=0/1"
                                + " blindCap=1.0 ante=true straddle=false"
                                + " blindLevels=3",
                        Duration.ofSeconds(15)), swingClient.diagnostic());

                renderer.releaseHeldAction();
                await(renderer.closed::get, Duration.ofSeconds(90),
                        renderer::diagnostic);
                assertTrue(swingClient.await("CP_E2E_HANDS_COMPLETE",
                        Duration.ofSeconds(30)), swingClient.diagnostic());
                assertHealthySwingNode(swingClient);
                assertEquals(3, renderer.completedHands.get(),
                        renderer.diagnostic());
                assertTrue(renderer.sawConfiguredBlinds.get(),
                        renderer.diagnostic());
                assertSummary(renderer.summary.get(), "server", 2);
            } finally {
                gdxHost.close();
            }
        } finally {
            closeChildren(children);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void swingHostPropagatesLiveRulesToGdxClientAndCompletesGame()
            throws Exception {
        List<ChildNode> children = new ArrayList<>();
        DatabaseService clientDatabase = database(
                "swing-live-settings-gdx-client.sqlite");
        try (clientDatabase;
             NetworkLobbyGateway clientGateway = gateway(temporary.resolve(
                     "swing-live-settings-gdx-client"), clientDatabase)) {
            ChildNode swingHost = startSwingNode(temporary.resolve(
                    "swing-live-settings-host"), "host", "server", 0,
                    1, 3, 42801L);
            children.add(swingHost);
            assertTrue(swingHost.await("CP_E2E_READY",
                    Duration.ofSeconds(60)), swingHost.diagnostic());
            int port = swingHost.intValueAfter("CP_E2E_READY", "port=");
            LobbySession gdxClient = clientGateway.open(request(true,
                    "client1", port, 3)).get(15, TimeUnit.SECONDS);
            try {
                await(() -> gdxClient.snapshot().participants().size() == 2,
                        Duration.ofSeconds(15));
                releaseLobbyChecks(swingHost);
                assertTrue(swingHost.await("CP_E2E_LOBBY_READY",
                        Duration.ofSeconds(60)), swingHost.diagnostic());
                swingHost.send("START_GAME");

                TableSession table = gdxClient.tableSession()
                        .toCompletableFuture().get(30, TimeUnit.SECONDS);
                AutoCallRenderer renderer = new AutoCallRenderer(table);
                table.attach(renderer).toCompletableFuture()
                        .get(15, TimeUnit.SECONDS);
                await(() -> renderer.heldAction.get()
                                && renderer.configuration.get() != null,
                        Duration.ofSeconds(30), renderer::diagnostic);

                swingHost.send("APPLY_LIVE_CONFIG");
                assertTrue(swingHost.await(
                        "CP_E2E_LIVE_CONFIG_APPLIED hands=3 iwtsth=true"
                                + " rit=true rabbit=2 botRebuy=true"
                                + " botBalance=true blinds=0.2/0.4"
                                + " blindInterval=0/1 blindCap=1.0 ante=true"
                                + " straddle=false blindLevels=3",
                        Duration.ofSeconds(15)), swingHost.diagnostic());
                await(() -> hasQaLiveConfiguration(
                                renderer.configuration.get()),
                        Duration.ofSeconds(15), renderer::diagnostic);

                renderer.releaseHeldAction();
                await(renderer.closed::get, Duration.ofSeconds(90),
                        renderer::diagnostic);
                assertTrue(swingHost.await("CP_E2E_HANDS_COMPLETE",
                        Duration.ofSeconds(30)), swingHost.diagnostic());
                assertHealthySwingNode(swingHost);
                assertEquals(3, renderer.completedHands.get(),
                        renderer.diagnostic());
                assertTrue(renderer.sawConfiguredBlinds.get(),
                        renderer.diagnostic());
                assertSummary(renderer.summary.get(), "client1", 2);
            } finally {
                gdxClient.close();
            }
        } finally {
            closeChildren(children);
        }
    }

    @Test
    @Timeout(value = 6, unit = TimeUnit.MINUTES)
    void gdxHumanClientCanRaiseFoldAndAllInAtSwingHostedTable()
            throws Exception {
        List<ChildNode> children = new ArrayList<>();
        DatabaseService gdxDatabase = database(
                "swing-host-gdx-human-actions.sqlite");
        try (gdxDatabase;
             NetworkLobbyGateway gdxGateway = gateway(temporary.resolve(
                     "swing-host-gdx-human-actions"), gdxDatabase)) {
            ChildNode host = startSwingNode(temporary.resolve(
                    "swing-host-gdx-human-actions"), "host", "server", 0,
                    1, 3, 42001L, "pause-resume");
            children.add(host);
            assertTrue(host.await("CP_E2E_READY", Duration.ofSeconds(60)),
                    host.diagnostic());
            int port = host.intValueAfter("CP_E2E_READY", "port=");

            LobbySession gdxClient = gdxGateway.open(request(true,
                    "client1", port)).get(15, TimeUnit.SECONDS);
            try {
                await(() -> gdxClient.snapshot().participants().size() == 2,
                        Duration.ofSeconds(15));
                releaseLobbyChecks(host);
                assertTrue(host.await("CP_E2E_LOBBY_READY",
                        Duration.ofSeconds(60)), host.diagnostic());
                host.send("START_GAME");

                TableSession table = gdxClient.tableSession()
                        .toCompletableFuture().get(30, TimeUnit.SECONDS);
                HumanActionRenderer renderer = new HumanActionRenderer(table);
                table.attach(renderer).toCompletableFuture()
                        .get(15, TimeUnit.SECONDS);

                await(renderer.closed::get, Duration.ofSeconds(150),
                        renderer::diagnostic);
                assertTrue(host.await("CP_E2E_HANDS_COMPLETE",
                        Duration.ofSeconds(45)), host.diagnostic());
                assertHealthySwingNode(host);
                renderer.assertComplete();
                assertSummary(renderer.summary.get(), "client1", 2);
            } finally {
                gdxClient.close();
            }
        } finally {
            closeChildren(children);
        }
    }

    @Test
    @Timeout(value = 6, unit = TimeUnit.MINUTES)
    void gdxHumanHostCanRaiseFoldAndAllInWithSwingClient()
            throws Exception {
        List<ChildNode> children = new ArrayList<>();
        int port = freePort();
        DatabaseService hostDatabase = database(
                "gdx-human-actions-host.sqlite");
        try (hostDatabase;
             NetworkLobbyGateway hostGateway = gateway(temporary.resolve(
                     "gdx-human-actions-host"), hostDatabase)) {
            LobbySession gdxHost = hostGateway.open(request(false,
                    "server", port, 3)).get(15, TimeUnit.SECONDS);
            try {
                ChildNode swingClient = startSwingNode(temporary.resolve(
                        "gdx-human-actions-swing-client"), "client",
                        "client1", port, 1, 3, 42101L, "pause-resume");
                children.add(swingClient);
                assertTrue(swingClient.await("CP_E2E_READY",
                        Duration.ofSeconds(60)), swingClient.diagnostic());
                await(() -> gdxHost.snapshot().participants().size() == 2,
                        Duration.ofSeconds(15));
                releaseLobbyChecks(swingClient);
                assertTrue(swingClient.await("CP_E2E_LOBBY_READY",
                        Duration.ofSeconds(60)), swingClient.diagnostic());

                gdxHost.submit(new LobbyCommand.StartGame())
                        .toCompletableFuture().get(15, TimeUnit.SECONDS);
                TableSession table = gdxHost.tableSession()
                        .toCompletableFuture().get(15, TimeUnit.SECONDS);
                HumanActionRenderer renderer = new HumanActionRenderer(table);
                table.attach(renderer).toCompletableFuture()
                        .get(15, TimeUnit.SECONDS);

                await(renderer.closed::get, Duration.ofSeconds(150),
                        renderer::diagnostic);
                assertTrue(swingClient.await("CP_E2E_HANDS_COMPLETE",
                        Duration.ofSeconds(45)), swingClient.diagnostic());
                assertHealthySwingNode(swingClient);
                renderer.assertComplete();
                assertSummary(renderer.summary.get(), "server", 2);
            } finally {
                gdxHost.close();
            }
        } finally {
            closeChildren(children);
        }
    }

    @Test
    @Timeout(value = 6, unit = TimeUnit.MINUTES)
    void swingHostWaitsForGdxAllInCinematicBeforeAdvancing() throws Exception {
        List<ChildNode> children = new ArrayList<>();
        DatabaseService gdxDatabase = database(
                "swing-host-gdx-allin-client.sqlite");
        try (gdxDatabase;
             NetworkLobbyGateway gdxGateway = gateway(temporary.resolve(
                     "swing-host-gdx-allin-client"), gdxDatabase)) {
            ChildNode host = startSwingNode(temporary.resolve(
                    "swing-allin-host"), "host", "server", 0, 2, 1,
                    43001L, "allin-single-board");
            children.add(host);
            assertTrue(host.await("CP_E2E_READY", Duration.ofSeconds(60)),
                    host.diagnostic());
            int port = host.intValueAfter("CP_E2E_READY", "port=");
            ChildNode swingClient = startSwingNode(temporary.resolve(
                    "swing-allin-client"), "client", "client1", port, 2, 1,
                    43002L, "allin-single-board");
            children.add(swingClient);
            assertTrue(swingClient.await("CP_E2E_READY",
                    Duration.ofSeconds(60)), swingClient.diagnostic());

            LobbySession gdxClient = gdxGateway.open(request(true,
                    "client2", port)).get(15, TimeUnit.SECONDS);
            try {
                await(() -> gdxClient.snapshot().participants().size() == 3,
                        Duration.ofSeconds(15));
                releaseLobbyChecks(host, swingClient);
                assertTrue(host.await("CP_E2E_LOBBY_READY",
                        Duration.ofSeconds(60)), host.diagnostic());
                assertTrue(swingClient.await("CP_E2E_LOBBY_READY",
                        Duration.ofSeconds(60)), swingClient.diagnostic());
                host.send("START_GAME");

                TableSession table = gdxClient.tableSession()
                        .toCompletableFuture().get(30, TimeUnit.SECONDS);
                AllInBarrierRenderer renderer = new AllInBarrierRenderer(table);
                table.attach(renderer).toCompletableFuture()
                        .get(15, TimeUnit.SECONDS);
                await(renderer.cinematicStarted::get, Duration.ofSeconds(40),
                        renderer::diagnostic);
                Thread.sleep(300L);
                assertFalse(renderer.sawControlsDuringCinematic.get(),
                        renderer::diagnostic);
                renderer.releaseCinematic();

                await(renderer.closed::get, Duration.ofSeconds(90),
                        renderer::diagnostic);
                assertTrue(host.await("CP_E2E_HANDS_COMPLETE",
                        Duration.ofSeconds(30)), host.diagnostic());
                assertTrue(swingClient.await("CP_E2E_HANDS_COMPLETE",
                        Duration.ofSeconds(30)), swingClient.diagnostic());
                assertHealthySwingNode(host);
                assertHealthySwingNode(swingClient);
                assertSummary(renderer.summary.get(), "client2", 3);
            } finally {
                gdxClient.close();
            }
        } finally {
            closeChildren(children);
        }
    }

    @Test
    @Timeout(value = 6, unit = TimeUnit.MINUTES)
    void gdxHostWaitsForAllInCinematicsWithSwingPeer() throws Exception {
        List<ChildNode> children = new ArrayList<>();
        int port = freePort();
        DatabaseService hostDatabase = database("gdx-allin-host.sqlite");
        DatabaseService clientDatabase = database("gdx-allin-client.sqlite");
        try (hostDatabase; clientDatabase;
             NetworkLobbyGateway hostGateway = gateway(
                     temporary.resolve("gdx-allin-host"), hostDatabase);
             NetworkLobbyGateway clientGateway = gateway(
                     temporary.resolve("gdx-allin-client"), clientDatabase)) {
            LobbySession gdxHost = hostGateway.open(request(false,
                    "server", port)).get(15, TimeUnit.SECONDS);
            try {
                ChildNode swingClient = startSwingNode(temporary.resolve(
                        "gdx-allin-host-swing-client"), "client", "client1",
                        port, 2, 1, 44001L, "allin-single-board");
                children.add(swingClient);
                assertTrue(swingClient.await("CP_E2E_READY",
                        Duration.ofSeconds(60)), swingClient.diagnostic());
                LobbySession gdxClient = clientGateway.open(request(true,
                        "client2", port)).get(15, TimeUnit.SECONDS);
                try {
                    await(() -> gdxHost.snapshot().participants().size() == 3
                                    && gdxClient.snapshot().participants().size()
                                    == 3,
                            Duration.ofSeconds(15));
                    releaseLobbyChecks(swingClient);
                    assertTrue(swingClient.await("CP_E2E_LOBBY_READY",
                            Duration.ofSeconds(60)), swingClient.diagnostic());
                    gdxHost.submit(new LobbyCommand.StartGame())
                            .toCompletableFuture().get(15, TimeUnit.SECONDS);

                    TableSession hostTable = gdxHost.tableSession()
                            .toCompletableFuture().get(15, TimeUnit.SECONDS);
                    AllInBarrierRenderer hostRenderer
                            = new AllInBarrierRenderer(hostTable);
                    hostTable.attach(hostRenderer).toCompletableFuture()
                            .get(15, TimeUnit.SECONDS);
                    TableSession clientTable = gdxClient.tableSession()
                            .toCompletableFuture().get(30, TimeUnit.SECONDS);
                    AllInBarrierRenderer clientRenderer
                            = new AllInBarrierRenderer(clientTable);
                    clientTable.attach(clientRenderer).toCompletableFuture()
                            .get(15, TimeUnit.SECONDS);

                    await(() -> hostRenderer.cinematicStarted.get()
                                    && clientRenderer.cinematicStarted.get(),
                            Duration.ofSeconds(40), () -> "host="
                                    + hostRenderer.diagnostic() + "; client="
                                    + clientRenderer.diagnostic());
                    Thread.sleep(300L);
                    assertFalse(hostRenderer.sawControlsDuringCinematic.get(),
                            hostRenderer::diagnostic);
                    assertFalse(clientRenderer.sawControlsDuringCinematic.get(),
                            clientRenderer::diagnostic);
                    hostRenderer.releaseCinematic();
                    clientRenderer.releaseCinematic();

                    await(() -> hostRenderer.closed.get()
                                    && clientRenderer.closed.get(),
                            Duration.ofSeconds(90), () -> "host="
                                    + hostRenderer.diagnostic() + "; client="
                                    + clientRenderer.diagnostic());
                    assertTrue(swingClient.await("CP_E2E_HANDS_COMPLETE",
                            Duration.ofSeconds(30)), swingClient.diagnostic());
                    assertHealthySwingNode(swingClient);
                    assertSummary(hostRenderer.summary.get(), "server", 3);
                    assertSummary(clientRenderer.summary.get(), "client2", 3);
                    assertEquals(canonicalBalances(hostRenderer.summary.get()),
                            canonicalBalances(clientRenderer.summary.get()));
                } finally {
                    gdxClient.close();
                }
            } finally {
                gdxHost.close();
            }
        } finally {
            closeChildren(children);
        }
    }

    @Test
    @Timeout(value = 6, unit = TimeUnit.MINUTES)
    void gdxHostKeepsAllInBarrierAcrossSwingClientReconnect()
            throws Exception {
        List<ChildNode> children = new ArrayList<>();
        int port = freePort();
        DatabaseService hostDatabase = database(
                "gdx-allin-reconnect-host.sqlite");
        try (hostDatabase;
             NetworkLobbyGateway hostGateway = gateway(temporary.resolve(
                     "gdx-allin-reconnect-host"), hostDatabase)) {
            LobbySession gdxHost = hostGateway.open(request(false,
                    "server", port)).get(15, TimeUnit.SECONDS);
            try {
                ChildNode client1 = startSwingNode(temporary.resolve(
                        "gdx-allin-reconnecting-swing-client1"), "client",
                        "client1", port, 2, 1, 45001L,
                        "allin-reconnect");
                children.add(client1);
                assertTrue(client1.await("CP_E2E_READY",
                        Duration.ofSeconds(60)), client1.diagnostic());
                ChildNode client2 = startSwingNode(temporary.resolve(
                        "gdx-allin-reconnecting-swing-client2"), "client",
                        "client2", port, 2, 1, 45002L,
                        "allin-reconnect");
                children.add(client2);
                assertTrue(client2.await("CP_E2E_READY",
                        Duration.ofSeconds(60)), client2.diagnostic());

                await(() -> gdxHost.snapshot().participants().size() == 3,
                        Duration.ofSeconds(15));
                client1.send("ARM_ACTION_GATE#1#1");
                client2.send("ARM_ACTION_GATE#1#1");
                assertTrue(client1.await(
                        "CP_E2E_ACTION_GATE_ARMED gate=1#1",
                        Duration.ofSeconds(10)), client1.diagnostic());
                assertTrue(client2.await(
                        "CP_E2E_ACTION_GATE_ARMED gate=1#1",
                        Duration.ofSeconds(10)), client2.diagnostic());
                releaseLobbyChecks(client1, client2);
                assertTrue(client1.await("CP_E2E_LOBBY_READY",
                        Duration.ofSeconds(60)), client1.diagnostic());
                assertTrue(client2.await("CP_E2E_LOBBY_READY",
                        Duration.ofSeconds(60)), client2.diagnostic());

                gdxHost.submit(new LobbyCommand.StartGame())
                        .toCompletableFuture().get(15, TimeUnit.SECONDS);
                TableSession table = gdxHost.tableSession()
                        .toCompletableFuture().get(15, TimeUnit.SECONDS);
                AllInBarrierRenderer renderer = new AllInBarrierRenderer(table);
                table.attach(renderer).toCompletableFuture()
                        .get(15, TimeUnit.SECONDS);

                await(() -> client1.contains(
                                "CP_E2E_ACTION_GATE_REACHED gate=1#1")
                                || client2.contains(
                                        "CP_E2E_ACTION_GATE_REACHED gate=1#1"),
                        Duration.ofSeconds(40), () -> "client1="
                                + client1.diagnostic() + "; client2="
                                + client2.diagnostic());
                ChildNode allInClient = client1.contains(
                        "CP_E2E_ACTION_GATE_REACHED gate=1#1")
                        ? client1 : client2;
                ChildNode observingClient = allInClient == client1
                        ? client2 : client1;
                String allInNickname = allInClient == client1
                        ? "client1" : "client2";
                allInClient.send("ALLIN_THEN_DROP_SOCKET");
                assertTrue(allInClient.await(
                        "CP_E2E_ORDERED_ALLIN_ACTION_CLICKED",
                        Duration.ofSeconds(30)), allInClient.diagnostic());
                assertTrue(allInClient.await(
                        "CP_E2E_SOCKET_DROP_REQUESTED",
                        Duration.ofSeconds(10)), allInClient.diagnostic());
                await(() -> nativePeerReconnectionCount(gdxHost,
                                allInNickname) == 1,
                        Duration.ofSeconds(20), () -> renderer.diagnostic()
                                + "; allIn=" + allInClient.diagnostic()
                                + "; observer=" + observingClient.diagnostic());
                assertTrue(allInClient.contains(
                        "Reconnected successfully to server"),
                        allInClient.diagnostic());
                client1.send("RELEASE_ACTION_GATE#1#1");
                client2.send("RELEASE_ACTION_GATE#1#1");
                assertTrue(client1.await(
                        "CP_E2E_ACTION_GATE_RELEASED gate=1#1",
                        Duration.ofSeconds(10)), client1.diagnostic());
                assertTrue(client2.await(
                        "CP_E2E_ACTION_GATE_RELEASED gate=1#1",
                        Duration.ofSeconds(10)), client2.diagnostic());

                await(renderer.cinematicStarted::get,
                        Duration.ofSeconds(40), renderer::diagnostic);
                Thread.sleep(300L);
                assertFalse(renderer.sawControlsDuringCinematic.get(),
                        renderer::diagnostic);
                renderer.releaseCinematic();

                await(renderer.closed::get, Duration.ofSeconds(90),
                        renderer::diagnostic);
                assertTrue(client1.await("CP_E2E_HANDS_COMPLETE",
                        Duration.ofSeconds(30)), client1.diagnostic());
                assertTrue(client2.await("CP_E2E_HANDS_COMPLETE",
                        Duration.ofSeconds(30)), client2.diagnostic());
                assertHealthySwingNode(client1);
                assertHealthySwingNode(client2);
                assertSummary(renderer.summary.get(), "server", 3);
                client1.close();
                children.remove(client1);
                client2.close();
                children.remove(client2);
            } finally {
                gdxHost.close();
            }
        } finally {
            closeChildren(children);
        }
    }

    @Test
    @Timeout(value = 4, unit = TimeUnit.MINUTES)
    void gdxHostAndSwingClientCompleteRunItTwiceOnBothBoards()
            throws Exception {
        List<ChildNode> children = new ArrayList<>();
        int port = freePort();
        DatabaseService hostDatabase = database("gdx-host-rit.sqlite");
        AtomicInteger gdxVotes = new AtomicInteger();
        try (hostDatabase;
             NetworkLobbyGateway hostGateway = gateway(
                     temporary.resolve("gdx-host-rit"), hostDatabase,
                     acceptingRunItTwiceDecisions(gdxVotes),
                     acceleratedSettings())) {
            LobbySession gdxHost = hostGateway.open(runItTwiceRequest(false,
                    "server", port)).get(15, TimeUnit.SECONDS);
            try {
                ChildNode swingClient = startSwingNode(temporary.resolve(
                        "gdx-rit-swing-client"), "client", "client1", port,
                        1, 1, 44501L, "allin-rit");
                children.add(swingClient);
                assertTrue(swingClient.await("CP_E2E_READY",
                        Duration.ofSeconds(60)), swingClient.diagnostic());
                await(() -> gdxHost.snapshot().participants().size() == 2,
                        Duration.ofSeconds(15));
                releaseLobbyChecks(swingClient);
                assertTrue(swingClient.await("CP_E2E_LOBBY_READY",
                        Duration.ofSeconds(60)), swingClient.diagnostic());
                gdxHost.submit(new LobbyCommand.StartGame())
                        .toCompletableFuture().get(15, TimeUnit.SECONDS);

                TableSession table = gdxHost.tableSession()
                        .toCompletableFuture().get(15, TimeUnit.SECONDS);
                RunItTwiceRenderer renderer = new RunItTwiceRenderer(table);
                table.attach(renderer).toCompletableFuture()
                        .get(15, TimeUnit.SECONDS);

                await(renderer.closed::get, Duration.ofSeconds(90),
                        renderer::diagnostic);
                assertTrue(swingClient.await(
                        "CP_E2E_RIT_VOTE decision=run-it-twice",
                        Duration.ofSeconds(30)), swingClient.diagnostic());
                assertTrue(swingClient.await("CP_E2E_HANDS_COMPLETE",
                        Duration.ofSeconds(30)), swingClient.diagnostic());
                renderer.assertComplete();
                assertTrue(gdxVotes.get() >= 1,
                        "GDX seat never cast its RIT vote: "
                                + renderer.diagnostic());
                assertHealthySwingNode(swingClient);
                assertSummary(renderer.summary.get(), "server", 2);
            } finally {
                gdxHost.close();
            }
        } finally {
            closeChildren(children);
        }
    }

    @Test
    @Timeout(value = 4, unit = TimeUnit.MINUTES)
    void swingHostAndGdxClientCompleteRunItTwiceOnBothBoards()
            throws Exception {
        List<ChildNode> children = new ArrayList<>();
        DatabaseService clientDatabase = database("swing-host-gdx-rit.sqlite");
        AtomicInteger gdxVotes = new AtomicInteger();
        try (clientDatabase;
             NetworkLobbyGateway clientGateway = gateway(
                     temporary.resolve("swing-host-gdx-rit"), clientDatabase,
                     acceptingRunItTwiceDecisions(gdxVotes),
                     acceleratedSettings())) {
            ChildNode swingHost = startSwingNode(temporary.resolve(
                    "swing-rit-host"), "host", "server", 0, 1, 1,
                    44502L, "allin-rit");
            children.add(swingHost);
            assertTrue(swingHost.await("CP_E2E_READY",
                    Duration.ofSeconds(60)), swingHost.diagnostic());
            int port = swingHost.intValueAfter("CP_E2E_READY", "port=");
            LobbySession gdxClient = clientGateway.open(runItTwiceRequest(true,
                    "client1", port)).get(15, TimeUnit.SECONDS);
            try {
                await(() -> gdxClient.snapshot().participants().size() == 2,
                        Duration.ofSeconds(15));
                releaseLobbyChecks(swingHost);
                assertTrue(swingHost.await("CP_E2E_LOBBY_READY",
                        Duration.ofSeconds(60)), swingHost.diagnostic());
                swingHost.send("START_GAME");

                TableSession table = gdxClient.tableSession()
                        .toCompletableFuture().get(30, TimeUnit.SECONDS);
                RunItTwiceRenderer renderer = new RunItTwiceRenderer(table);
                table.attach(renderer).toCompletableFuture()
                        .get(15, TimeUnit.SECONDS);

                await(renderer.closed::get, Duration.ofSeconds(90),
                        renderer::diagnostic);
                assertTrue(swingHost.await("CP_E2E_HANDS_COMPLETE",
                        Duration.ofSeconds(30)), swingHost.diagnostic());
                renderer.assertComplete();
                assertTrue(gdxVotes.get() >= 1,
                        "GDX client never cast its RIT vote: "
                                + renderer.diagnostic());
                assertHealthySwingNode(swingHost);
                assertSummary(renderer.summary.get(), "client1", 2);
            } finally {
                gdxClient.close();
            }
        } finally {
            closeChildren(children);
        }
    }

    @Test
    @Timeout(value = 4, unit = TimeUnit.MINUTES)
    void mixedThreeHumanTablePostsOneCanonicalStraddleEverywhere()
            throws Exception {
        List<ChildNode> children = new ArrayList<>();
        int port = freePort();
        DatabaseService hostDatabase = database("gdx-host-straddle.sqlite");
        DatabaseService clientDatabase = database("gdx-client-straddle.sqlite");
        AtomicInteger gdxChoices = new AtomicInteger();
        AtomicReference<String> gdxStraddler = new AtomicReference<>();
        try (hostDatabase; clientDatabase;
             NetworkLobbyGateway hostGateway = gateway(
                     temporary.resolve("gdx-host-straddle"), hostDatabase,
                     acceptingStraddleDecisions("server", gdxChoices,
                             gdxStraddler), acceleratedSettings());
             NetworkLobbyGateway clientGateway = gateway(
                     temporary.resolve("gdx-client-straddle"), clientDatabase,
                     acceptingStraddleDecisions("client2", gdxChoices,
                             gdxStraddler), acceleratedSettings())) {
            LobbySession gdxHost = hostGateway.open(straddleRequest(false,
                    "server", port)).get(15, TimeUnit.SECONDS);
            try {
                ChildNode swingClient = startSwingNode(temporary.resolve(
                        "gdx-straddle-swing-client"), "client", "client1",
                        port, 2, 1, 44601L, "straddle-post");
                children.add(swingClient);
                assertTrue(swingClient.await("CP_E2E_READY",
                        Duration.ofSeconds(60)), swingClient.diagnostic());
                LobbySession gdxClient = clientGateway.open(
                        straddleRequest(true, "client2", port))
                        .get(15, TimeUnit.SECONDS);
                try {
                    await(() -> gdxHost.snapshot().participants().size() == 3
                                    && gdxClient.snapshot().participants().size()
                                    == 3,
                            Duration.ofSeconds(15));
                    releaseLobbyChecks(swingClient);
                    assertTrue(swingClient.await("CP_E2E_LOBBY_READY",
                            Duration.ofSeconds(60)), swingClient.diagnostic());
                    gdxHost.submit(new LobbyCommand.StartGame())
                            .toCompletableFuture().get(15, TimeUnit.SECONDS);

                    TableSession hostTable = gdxHost.tableSession()
                            .toCompletableFuture().get(15, TimeUnit.SECONDS);
                    StraddleRenderer hostRenderer
                            = new StraddleRenderer(hostTable);
                    hostTable.attach(hostRenderer).toCompletableFuture()
                            .get(15, TimeUnit.SECONDS);
                    TableSession clientTable = gdxClient.tableSession()
                            .toCompletableFuture().get(30, TimeUnit.SECONDS);
                    StraddleRenderer clientRenderer
                            = new StraddleRenderer(clientTable);
                    clientTable.attach(clientRenderer).toCompletableFuture()
                            .get(15, TimeUnit.SECONDS);

                    await(() -> hostRenderer.closed.get()
                                    && clientRenderer.closed.get(),
                            Duration.ofSeconds(90), () -> "host="
                                    + hostRenderer.diagnostic() + "; client="
                                    + clientRenderer.diagnostic());
                    assertTrue(swingClient.await("CP_E2E_HANDS_COMPLETE",
                            Duration.ofSeconds(30)), swingClient.diagnostic());
                    int swingChoice = swingClient.contains(
                            "CP_E2E_STRADDLE_ACCEPTED") ? 1 : 0;
                    assertEquals(1, gdxChoices.get() + swingChoice,
                            "exactly the canonical UTG seat must accept: gdx="
                                    + gdxStraddler + "\n"
                                    + swingClient.diagnostic());
                    hostRenderer.assertComplete();
                    clientRenderer.assertComplete();
                    assertEquals(canonicalBalances(hostRenderer.summary.get()),
                            canonicalBalances(clientRenderer.summary.get()));
                    assertHealthySwingNode(swingClient);
                } finally {
                    gdxClient.close();
                }
            } finally {
                gdxHost.close();
            }
        } finally {
            closeChildren(children);
        }
    }

    @Test
    @Timeout(value = 4, unit = TimeUnit.MINUTES)
    void swingHostedMixedTablePostsOneCanonicalStraddleEverywhere()
            throws Exception {
        List<ChildNode> children = new ArrayList<>();
        DatabaseService clientDatabase = database(
                "swing-host-mixed-straddle.sqlite");
        AtomicInteger gdxChoices = new AtomicInteger();
        AtomicReference<String> gdxStraddler = new AtomicReference<>();
        try (clientDatabase;
             NetworkLobbyGateway clientGateway = gateway(temporary.resolve(
                     "swing-host-mixed-straddle"), clientDatabase,
                     acceptingStraddleDecisions("client2", gdxChoices,
                             gdxStraddler), acceleratedSettings())) {
            ChildNode swingHost = startSwingNode(temporary.resolve(
                    "swing-straddle-host"), "host", "server", 0, 2, 1,
                    44602L, "straddle-post");
            children.add(swingHost);
            assertTrue(swingHost.await("CP_E2E_READY",
                    Duration.ofSeconds(60)), swingHost.diagnostic());
            int port = swingHost.intValueAfter("CP_E2E_READY", "port=");

            ChildNode swingClient = startSwingNode(temporary.resolve(
                    "swing-straddle-client"), "client", "client1", port,
                    2, 1, 44603L, "straddle-post");
            children.add(swingClient);
            assertTrue(swingClient.await("CP_E2E_READY",
                    Duration.ofSeconds(60)), swingClient.diagnostic());

            LobbySession gdxClient = clientGateway.open(straddleRequest(true,
                    "client2", port)).get(15, TimeUnit.SECONDS);
            try {
                await(() -> gdxClient.snapshot().participants().size() == 3,
                        Duration.ofSeconds(15));
                releaseLobbyChecks(swingHost, swingClient);
                assertTrue(swingHost.await("CP_E2E_LOBBY_READY",
                        Duration.ofSeconds(60)), swingHost.diagnostic());
                assertTrue(swingClient.await("CP_E2E_LOBBY_READY",
                        Duration.ofSeconds(60)), swingClient.diagnostic());
                swingHost.send("START_GAME");

                TableSession table = gdxClient.tableSession()
                        .toCompletableFuture().get(30, TimeUnit.SECONDS);
                StraddleRenderer renderer = new StraddleRenderer(table);
                table.attach(renderer).toCompletableFuture()
                        .get(15, TimeUnit.SECONDS);

                await(renderer.closed::get, Duration.ofSeconds(90),
                        renderer::diagnostic);
                assertTrue(swingHost.await("CP_E2E_HANDS_COMPLETE",
                        Duration.ofSeconds(30)), swingHost.diagnostic());
                assertTrue(swingClient.await("CP_E2E_HANDS_COMPLETE",
                        Duration.ofSeconds(30)), swingClient.diagnostic());
                int swingChoices = (swingHost.contains(
                        "CP_E2E_STRADDLE_ACCEPTED") ? 1 : 0)
                        + (swingClient.contains(
                                "CP_E2E_STRADDLE_ACCEPTED") ? 1 : 0);
                assertEquals(1, gdxChoices.get() + swingChoices,
                        "exactly the canonical UTG seat must accept: gdx="
                                + gdxStraddler + "\nhost="
                                + swingHost.diagnostic() + "\nclient="
                                + swingClient.diagnostic());
                renderer.assertComplete();
                assertHealthySwingNode(swingHost);
                assertHealthySwingNode(swingClient);
                assertSummary(renderer.summary.get(), "client2", 3);
            } finally {
                gdxClient.close();
            }
        } finally {
            closeChildren(children);
        }
    }

    @Test
    @Timeout(value = 7, unit = TimeUnit.MINUTES)
    void swingHostAndGdxClientCarryAcceptedRebuyIntoLaterHands()
            throws Exception {
        List<ChildNode> children = new ArrayList<>();
        DatabaseService gdxDatabase = database("swing-host-gdx-rebuy.sqlite");
        AtomicInteger rebuyChoices = new AtomicInteger();
        GameDecisionSink decisions = acceptingAutomaticRebuyDecisions(
                rebuyChoices);
        try (gdxDatabase;
             NetworkLobbyGateway gdxGateway = gateway(
                     temporary.resolve("swing-host-gdx-rebuy"), gdxDatabase,
                     decisions, liveAcceleratedRebuySettings())) {
            ChildNode host = startSwingNode(temporary.resolve(
                    "swing-rebuy-host"), "host", "server", 0, 1, 5,
                    47001L, "allin-rebuy", false);
            children.add(host);
            assertTrue(host.await("CP_E2E_READY", Duration.ofSeconds(60)),
                    host.diagnostic());
            int port = host.intValueAfter("CP_E2E_READY", "port=");
            LobbySession gdxClient = gdxGateway.open(request(true,
                    "client1", port, 5)).get(15, TimeUnit.SECONDS);
            try {
                await(() -> gdxClient.snapshot().participants().size() == 2,
                        Duration.ofSeconds(15));
                releaseLobbyChecks(host);
                assertTrue(host.await("CP_E2E_LOBBY_READY",
                        Duration.ofSeconds(60)), host.diagnostic());
                host.send("START_GAME");
                TableSession table = gdxClient.tableSession()
                        .toCompletableFuture().get(30, TimeUnit.SECONDS);
                RebuyRenderer renderer = new RebuyRenderer(table);
                table.attach(renderer).toCompletableFuture()
                        .get(15, TimeUnit.SECONDS);

                await(renderer.closed::get, Duration.ofSeconds(180),
                        renderer::diagnostic);
                assertTrue(host.await("CP_E2E_HANDS_COMPLETE",
                        Duration.ofSeconds(30)), host.diagnostic());
                assertRebuyCompleted(renderer, rebuyChoices, host);
                assertTrue(metric(host.lastLineContaining("CP_E2E_LEDGER"),
                                "buyinCents") > 2_000L,
                        "Swing ledger did not account for a rebuy\n"
                                + host.diagnostic());
                assertHealthySwingNode(host);
            } finally {
                gdxClient.close();
            }
        } finally {
            closeChildren(children);
        }
    }

    @Test
    @Timeout(value = 7, unit = TimeUnit.MINUTES)
    void gdxHostAndSwingClientCarryAcceptedRebuyIntoLaterHands()
            throws Exception {
        List<ChildNode> children = new ArrayList<>();
        int port = freePort();
        DatabaseService hostDatabase = database("gdx-host-rebuy.sqlite");
        AtomicInteger rebuyChoices = new AtomicInteger();
        GameDecisionSink decisions = acceptingAutomaticRebuyDecisions(
                rebuyChoices);
        try (hostDatabase;
             NetworkLobbyGateway hostGateway = gateway(
                     temporary.resolve("gdx-host-rebuy"), hostDatabase,
                     decisions, liveAcceleratedRebuySettings())) {
            LobbySession gdxHost = hostGateway.open(request(false,
                    "server", port, 5)).get(15, TimeUnit.SECONDS);
            try {
                ChildNode swingClient = startSwingNode(temporary.resolve(
                        "gdx-rebuy-swing-client"), "client", "client1", port,
                        1, 5, 48001L, "allin-rebuy", false);
                children.add(swingClient);
                assertTrue(swingClient.await("CP_E2E_READY",
                        Duration.ofSeconds(60)), swingClient.diagnostic());
                await(() -> gdxHost.snapshot().participants().size() == 2,
                        Duration.ofSeconds(15));
                releaseLobbyChecks(swingClient);
                assertTrue(swingClient.await("CP_E2E_LOBBY_READY",
                        Duration.ofSeconds(60)), swingClient.diagnostic());
                gdxHost.submit(new LobbyCommand.StartGame())
                        .toCompletableFuture().get(15, TimeUnit.SECONDS);
                TableSession table = gdxHost.tableSession()
                        .toCompletableFuture().get(15, TimeUnit.SECONDS);
                RebuyRenderer renderer = new RebuyRenderer(table);
                table.attach(renderer).toCompletableFuture()
                        .get(15, TimeUnit.SECONDS);

                await(renderer.closed::get, Duration.ofSeconds(180),
                        renderer::diagnostic);
                assertTrue(swingClient.await("CP_E2E_HANDS_COMPLETE",
                        Duration.ofSeconds(30)), swingClient.diagnostic());
                assertRebuyCompleted(renderer, rebuyChoices, swingClient);
                assertTrue(metric(swingClient.lastLineContaining(
                                "CP_E2E_LEDGER"), "buyinCents") > 2_000L,
                        "Swing ledger did not account for a rebuy\n"
                                + swingClient.diagnostic());
                assertHealthySwingNode(swingClient);
            } finally {
                gdxHost.close();
            }
        } finally {
            closeChildren(children);
        }
    }

    @Test
    @Timeout(value = 4, unit = TimeUnit.MINUTES)
    void gdxHostTimesOutHeldTurnWithoutBlockingSwingPeer() throws Exception {
        List<ChildNode> children = new ArrayList<>();
        int port = freePort();
        DatabaseService hostDatabase = database("gdx-host-timeout.sqlite");
        try (hostDatabase;
             NetworkLobbyGateway hostGateway = gateway(
                     temporary.resolve("gdx-host-timeout"), hostDatabase,
                     GameDecisionSink.noop(), liveTimeoutSettings())) {
            LobbySession gdxHost = hostGateway.open(timeoutRequest(false,
                    "server", port)).get(15, TimeUnit.SECONDS);
            try {
                ChildNode swingClient = startSwingNode(temporary.resolve(
                        "gdx-timeout-swing-client"), "client", "client1",
                        port, 1, 1, 49001L, "normal", false);
                children.add(swingClient);
                assertTrue(swingClient.await("CP_E2E_READY",
                        Duration.ofSeconds(60)), swingClient.diagnostic());
                await(() -> gdxHost.snapshot().participants().size() == 2,
                        Duration.ofSeconds(15));
                releaseLobbyChecks(swingClient);
                assertTrue(swingClient.await("CP_E2E_LOBBY_READY",
                        Duration.ofSeconds(60)), swingClient.diagnostic());

                gdxHost.submit(new LobbyCommand.StartGame())
                        .toCompletableFuture().get(15, TimeUnit.SECONDS);
                TableSession table = gdxHost.tableSession()
                        .toCompletableFuture().get(15, TimeUnit.SECONDS);
                AutoCallRenderer renderer = new AutoCallRenderer(table);
                table.attach(renderer).toCompletableFuture()
                        .get(15, TimeUnit.SECONDS);

                await(renderer.heldAction::get, Duration.ofSeconds(35),
                        renderer::diagnostic);
                await(renderer.sawTimeoutCue::get, Duration.ofSeconds(20),
                        renderer::diagnostic);
                await(renderer.closed::get, Duration.ofSeconds(45),
                        renderer::diagnostic);
                assertTrue(renderer.sawHurryCue.get(), renderer::diagnostic);
                assertTrue(renderer.sawHurryStop.get(), renderer::diagnostic);
                assertEquals(1, renderer.completedHands.get(),
                        renderer::diagnostic);
                assertTrue(swingClient.await("CP_E2E_HANDS_COMPLETE",
                        Duration.ofSeconds(30)), swingClient.diagnostic());
                assertHealthySwingNode(swingClient);
                assertSummary(renderer.summary.get(), "server", 2);
            } finally {
                gdxHost.close();
            }
        } finally {
            closeChildren(children);
        }
    }

    @Test
    @Timeout(value = 4, unit = TimeUnit.MINUTES)
    void gdxClientTimesOutHeldTurnWithoutBlockingSwingHost() throws Exception {
        List<ChildNode> children = new ArrayList<>();
        DatabaseService clientDatabase = database(
                "swing-host-gdx-client-timeout.sqlite");
        try (clientDatabase;
             NetworkLobbyGateway clientGateway = gateway(temporary.resolve(
                     "swing-host-gdx-client-timeout"), clientDatabase,
                     GameDecisionSink.noop(), liveTimeoutSettings())) {
            ChildNode swingHost = startSwingNode(temporary.resolve(
                    "timeout-swing-host"), "host", "server", 0, 1, 1,
                    49101L, "normal", false);
            children.add(swingHost);
            assertTrue(swingHost.await("CP_E2E_READY",
                    Duration.ofSeconds(60)), swingHost.diagnostic());
            int port = swingHost.intValueAfter("CP_E2E_READY", "port=");

            LobbySession gdxClient = clientGateway.open(request(true,
                    "client1", port)).get(15, TimeUnit.SECONDS);
            try {
                await(() -> gdxClient.snapshot().participants().size() == 2,
                        Duration.ofSeconds(15));
                releaseLobbyChecks(swingHost);
                assertTrue(swingHost.await("CP_E2E_LOBBY_READY",
                        Duration.ofSeconds(60)), swingHost.diagnostic());
                swingHost.send("START_GAME");

                TableSession table = gdxClient.tableSession()
                        .toCompletableFuture().get(30, TimeUnit.SECONDS);
                AutoCallRenderer renderer = new AutoCallRenderer(table);
                table.attach(renderer).toCompletableFuture()
                        .get(15, TimeUnit.SECONDS);

                await(renderer.heldAction::get, Duration.ofSeconds(35),
                        renderer::diagnostic);
                await(renderer.sawTimeoutCue::get, Duration.ofSeconds(20),
                        renderer::diagnostic);
                await(renderer.closed::get, Duration.ofSeconds(45),
                        renderer::diagnostic);
                assertTrue(renderer.sawHurryCue.get(), renderer::diagnostic);
                assertTrue(renderer.sawHurryStop.get(), renderer::diagnostic);
                assertEquals(1, renderer.completedHands.get(),
                        renderer::diagnostic);
                assertTrue(swingHost.await("CP_E2E_HANDS_COMPLETE",
                        Duration.ofSeconds(30)), swingHost.diagnostic());
                assertHealthySwingNode(swingHost);
                assertSummary(renderer.summary.get(), "client1", 2);
            } finally {
                gdxClient.close();
            }
        } finally {
            closeChildren(children);
        }
    }

    @Test
    @Timeout(value = 6, unit = TimeUnit.MINUTES)
    void gdxClientCanExitSwingHostedTurnWithoutBlockingRemainingPeers()
            throws Exception {
        List<ChildNode> children = new ArrayList<>();
        DatabaseService gdxDatabase = database("swing-host-gdx-exit.sqlite");
        try (gdxDatabase;
             NetworkLobbyGateway gdxGateway = gateway(
                     temporary.resolve("swing-host-gdx-exit"), gdxDatabase)) {
            ChildNode host = startSwingNode(temporary.resolve(
                    "swing-exit-host"), "host", "server", 0, 2, 1, 45001L);
            children.add(host);
            assertTrue(host.await("CP_E2E_READY", Duration.ofSeconds(60)),
                    host.diagnostic());
            int port = host.intValueAfter("CP_E2E_READY", "port=");
            ChildNode swingClient = startSwingNode(temporary.resolve(
                    "swing-exit-client"), "client", "client1", port, 2, 1,
                    45002L);
            children.add(swingClient);
            assertTrue(swingClient.await("CP_E2E_READY",
                    Duration.ofSeconds(60)), swingClient.diagnostic());
            LobbySession gdxClient = gdxGateway.open(request(true,
                    "client2", port)).get(15, TimeUnit.SECONDS);
            try {
                await(() -> gdxClient.snapshot().participants().size() == 3,
                        Duration.ofSeconds(15));
                releaseLobbyChecks(host, swingClient);
                assertTrue(host.await("CP_E2E_LOBBY_READY",
                        Duration.ofSeconds(60)), host.diagnostic());
                assertTrue(swingClient.await("CP_E2E_LOBBY_READY",
                        Duration.ofSeconds(60)), swingClient.diagnostic());
                host.send("START_GAME");
                TableSession table = gdxClient.tableSession()
                        .toCompletableFuture().get(30, TimeUnit.SECONDS);
                AutoCallRenderer renderer = new AutoCallRenderer(table);
                table.attach(renderer).toCompletableFuture()
                        .get(15, TimeUnit.SECONDS);
                await(renderer.heldAction::get, Duration.ofSeconds(30),
                        renderer::diagnostic);

                table.commands().submit(new TableCommand.ExitGame());
                await(renderer.closed::get, Duration.ofSeconds(30),
                        renderer::diagnostic);
                assertNotNull(renderer.summary.get());
                assertEquals(TableSessionSummary.CloseReason.EXITED,
                        renderer.summary.get().reason());
                assertTrue(host.await("CP_E2E_HANDS_COMPLETE",
                        Duration.ofSeconds(75)), host.diagnostic());
                assertTrue(swingClient.await("CP_E2E_HANDS_COMPLETE",
                        Duration.ofSeconds(75)), swingClient.diagnostic());
                assertHealthySwingNode(host);
                assertHealthySwingNode(swingClient);
            } finally {
                gdxClient.close();
            }
        } finally {
            closeChildren(children);
        }
    }

    @Test
    @Timeout(value = 6, unit = TimeUnit.MINUTES)
    void swingClientCanExitGdxHostedTurnWithoutBlockingRemainingPeers()
            throws Exception {
        List<ChildNode> children = new ArrayList<>();
        int port = freePort();
        DatabaseService hostDatabase = database(
                "gdx-host-swing-exit-host.sqlite");
        DatabaseService clientDatabase = database(
                "gdx-host-swing-exit-client.sqlite");
        try (hostDatabase; clientDatabase;
             NetworkLobbyGateway hostGateway = gateway(
                     temporary.resolve("gdx-host-swing-exit-host"),
                     hostDatabase);
             NetworkLobbyGateway clientGateway = gateway(
                     temporary.resolve("gdx-host-swing-exit-client"),
                     clientDatabase)) {
            LobbySession gdxHost = hostGateway.open(request(false,
                    "server", port)).get(15, TimeUnit.SECONDS);
            try {
                ChildNode swingClient = startSwingNode(temporary.resolve(
                        "gdx-host-exiting-swing-client"), "client", "client1",
                        port, 2, 1, 45501L);
                children.add(swingClient);
                assertTrue(swingClient.await("CP_E2E_READY",
                        Duration.ofSeconds(60)), swingClient.diagnostic());
                LobbySession gdxClient = clientGateway.open(request(true,
                        "client2", port)).get(15, TimeUnit.SECONDS);
                try {
                    await(() -> gdxHost.snapshot().participants().size() == 3
                                    && gdxClient.snapshot().participants().size()
                                    == 3,
                            Duration.ofSeconds(15));
                    releaseLobbyChecks(swingClient);
                    assertTrue(swingClient.await("CP_E2E_LOBBY_READY",
                            Duration.ofSeconds(60)), swingClient.diagnostic());

                    gdxHost.submit(new LobbyCommand.StartGame())
                            .toCompletableFuture().get(15, TimeUnit.SECONDS);
                    TableSession hostTable = gdxHost.tableSession()
                            .toCompletableFuture().get(15, TimeUnit.SECONDS);
                    AutoCallRenderer hostRenderer = new AutoCallRenderer(hostTable);
                    hostTable.attach(hostRenderer).toCompletableFuture()
                            .get(15, TimeUnit.SECONDS);
                    TableSession clientTable = gdxClient.tableSession()
                            .toCompletableFuture().get(30, TimeUnit.SECONDS);
                    AutoCallRenderer clientRenderer
                            = new AutoCallRenderer(clientTable);
                    clientTable.attach(clientRenderer).toCompletableFuture()
                            .get(15, TimeUnit.SECONDS);
                    await(() -> hostRenderer.heldAction.get()
                                    || clientRenderer.heldAction.get(),
                            Duration.ofSeconds(30), () -> "host="
                                    + hostRenderer.diagnostic() + "; client="
                                    + clientRenderer.diagnostic());

                    swingClient.send("CONTROLLED_EXIT");
                    assertTrue(swingClient.await("CP_E2E_CONTROLLED_EXIT_SENT",
                            Duration.ofSeconds(15)), swingClient.diagnostic());
                    assertTrue(swingClient.await("CP_E2E_EXPECTED_EXIT_COMPLETE",
                            Duration.ofSeconds(30)), swingClient.diagnostic());
                    hostRenderer.releaseHeldAction();
                    clientRenderer.releaseHeldAction();

                    await(() -> hostRenderer.closed.get()
                                    && clientRenderer.closed.get(),
                            Duration.ofSeconds(75), () -> "host="
                                    + hostRenderer.diagnostic() + "; client="
                                    + clientRenderer.diagnostic());
                    assertNoCriticalSwingFailure(swingClient);
                    assertEquals(1, hostRenderer.completedHands.get(),
                            hostRenderer::diagnostic);
                    assertEquals(1, clientRenderer.completedHands.get(),
                            clientRenderer::diagnostic);
                    assertSummary(hostRenderer.summary.get(), "server", 3);
                    assertSummary(clientRenderer.summary.get(), "client2", 3);
                } finally {
                    gdxClient.close();
                }
            } finally {
                gdxHost.close();
            }
        } finally {
            closeChildren(children);
        }
    }

    @Test
    @Timeout(value = 6, unit = TimeUnit.MINUTES)
    void gdxHostExitWhilePausedClosesSwingAndGdxPeers() throws Exception {
        List<ChildNode> children = new ArrayList<>();
        int port = freePort();
        DatabaseService hostDatabase = database("gdx-paused-exit-host.sqlite");
        DatabaseService clientDatabase = database(
                "gdx-paused-exit-client.sqlite");
        try (hostDatabase; clientDatabase;
             NetworkLobbyGateway hostGateway = gateway(
                     temporary.resolve("gdx-paused-exit-host"), hostDatabase);
             NetworkLobbyGateway clientGateway = gateway(
                     temporary.resolve("gdx-paused-exit-client"),
                     clientDatabase)) {
            LobbySession gdxHost = hostGateway.open(request(false,
                    "server", port)).get(15, TimeUnit.SECONDS);
            try {
                ChildNode swingClient = startSwingNode(temporary.resolve(
                        "gdx-paused-exit-swing-client"), "client", "client1",
                        port, 2, 1, 46001L);
                children.add(swingClient);
                assertTrue(swingClient.await("CP_E2E_READY",
                        Duration.ofSeconds(60)), swingClient.diagnostic());
                LobbySession gdxClient = clientGateway.open(request(true,
                        "client2", port)).get(15, TimeUnit.SECONDS);
                try {
                    await(() -> gdxHost.snapshot().participants().size() == 3
                                    && gdxClient.snapshot().participants().size()
                                    == 3,
                            Duration.ofSeconds(15));
                    releaseLobbyChecks(swingClient);
                    assertTrue(swingClient.await("CP_E2E_LOBBY_READY",
                            Duration.ofSeconds(60)), swingClient.diagnostic());
                    gdxHost.submit(new LobbyCommand.StartGame())
                            .toCompletableFuture().get(15, TimeUnit.SECONDS);
                    TableSession hostTable = gdxHost.tableSession()
                            .toCompletableFuture().get(15, TimeUnit.SECONDS);
                    AutoCallRenderer hostRenderer = new AutoCallRenderer(hostTable);
                    hostTable.attach(hostRenderer).toCompletableFuture()
                            .get(15, TimeUnit.SECONDS);
                    TableSession clientTable = gdxClient.tableSession()
                            .toCompletableFuture().get(30, TimeUnit.SECONDS);
                    AutoCallRenderer clientRenderer
                            = new AutoCallRenderer(clientTable);
                    clientTable.attach(clientRenderer).toCompletableFuture()
                            .get(15, TimeUnit.SECONDS);
                    await(() -> hostRenderer.heldAction.get()
                                    || clientRenderer.heldAction.get(),
                            Duration.ofSeconds(30));
                    hostTable.commands().submit(new TableCommand.TogglePause());
                    await(() -> hostRenderer.sawPaused.get()
                                    && clientRenderer.sawPaused.get()
                                    && swingClient.contains(
                                            "CP_E2E_PAUSE_STATE paused=true"),
                            Duration.ofSeconds(15));
                    swingClient.send("EXPECT_REMOTE_EXIT");
                    assertTrue(swingClient.await("CP_E2E_REMOTE_EXIT_ARMED",
                            Duration.ofSeconds(10)), swingClient.diagnostic());

                    hostTable.commands().submit(new TableCommand.ExitGame());
                    await(() -> hostRenderer.closed.get()
                                    && clientRenderer.closed.get(),
                            Duration.ofSeconds(30), () -> "host="
                                    + hostRenderer.diagnostic() + "; client="
                                    + clientRenderer.diagnostic());
                    assertTrue(swingClient.await("CP_E2E_EXPECTED_EXIT_COMPLETE",
                            Duration.ofSeconds(30)), swingClient.diagnostic());
                    assertNoCriticalSwingFailure(swingClient);
                    assertEquals(TableSessionSummary.CloseReason.EXITED,
                            hostRenderer.summary.get().reason());
                    assertNotNull(clientRenderer.summary.get());
                } finally {
                    gdxClient.close();
                }
            } finally {
                gdxHost.close();
            }
        } finally {
            closeChildren(children);
        }
    }

    @Test
    @Timeout(value = 6, unit = TimeUnit.MINUTES)
    void swingClientReconnectsMidHandToGdxHost() throws Exception {
        List<ChildNode> children = new ArrayList<>();
        int port = freePort();
        DatabaseService hostDatabase = database(
                "gdx-host-swing-reconnect.sqlite");
        try (hostDatabase;
             NetworkLobbyGateway hostGateway = gateway(
                     temporary.resolve("gdx-host-swing-reconnect"),
                     hostDatabase)) {
            LobbySession gdxHost = hostGateway.open(request(false,
                    "server", port, 2)).get(15, TimeUnit.SECONDS);
            try {
                ChildNode swingClient = startSwingNode(temporary.resolve(
                        "gdx-host-reconnecting-swing-client"), "client",
                        "client1", port, 1, 2, 47001L);
                children.add(swingClient);
                assertTrue(swingClient.await("CP_E2E_READY",
                        Duration.ofSeconds(60)), swingClient.diagnostic());
                await(() -> gdxHost.snapshot().participants().size() == 2,
                        Duration.ofSeconds(15));
                releaseLobbyChecks(swingClient);
                assertTrue(swingClient.await("CP_E2E_LOBBY_READY",
                        Duration.ofSeconds(60)), swingClient.diagnostic());

                gdxHost.submit(new LobbyCommand.StartGame())
                        .toCompletableFuture().get(15, TimeUnit.SECONDS);
                TableSession table = gdxHost.tableSession()
                        .toCompletableFuture().get(15, TimeUnit.SECONDS);
                AutoCallRenderer renderer = new AutoCallRenderer(table);
                table.attach(renderer).toCompletableFuture()
                        .get(15, TimeUnit.SECONDS);
                await(renderer.heldAction::get, Duration.ofSeconds(30),
                        renderer::diagnostic);

                swingClient.send("DROP_SOCKET");
                assertTrue(swingClient.await("CP_E2E_SOCKET_DROP_REQUESTED",
                        Duration.ofSeconds(10)), swingClient.diagnostic());
                await(() -> nativePeerReconnectionCount(gdxHost,
                                "client1") == 1,
                        Duration.ofSeconds(20), () -> renderer.diagnostic()
                                + "; swing=" + swingClient.diagnostic());
                renderer.releaseHeldAction();

                await(renderer.closed::get, Duration.ofSeconds(120),
                        renderer::diagnostic);
                assertTrue(swingClient.await("CP_E2E_HANDS_COMPLETE",
                        Duration.ofSeconds(45)), swingClient.diagnostic());
                assertHealthySwingNode(swingClient);
                assertEquals(2, renderer.completedHands.get(),
                        renderer::diagnostic);
                assertSummary(renderer.summary.get(), "server", 2);
            } finally {
                gdxHost.close();
            }
        } finally {
            closeChildren(children);
        }
    }

    @Test
    @Timeout(value = 6, unit = TimeUnit.MINUTES)
    void gdxClientReconnectsMidHandToSwingHost() throws Exception {
        List<ChildNode> children = new ArrayList<>();
        DatabaseService clientDatabase = database(
                "swing-host-gdx-reconnect.sqlite");
        try (clientDatabase;
             NetworkLobbyGateway clientGateway = gateway(
                     temporary.resolve("swing-host-gdx-reconnect"),
                     clientDatabase)) {
            ChildNode swingHost = startSwingNode(temporary.resolve(
                    "swing-host-reconnecting-gdx-client"), "host",
                    "server", 0, 1, 2, 48001L);
            children.add(swingHost);
            assertTrue(swingHost.await("CP_E2E_READY",
                    Duration.ofSeconds(60)), swingHost.diagnostic());
            int port = swingHost.intValueAfter("CP_E2E_READY", "port=");
            LobbySession gdxClient = clientGateway.open(request(true,
                    "client1", port)).get(15, TimeUnit.SECONDS);
            try {
                await(() -> gdxClient.snapshot().participants().size() == 2,
                        Duration.ofSeconds(15));
                releaseLobbyChecks(swingHost);
                assertTrue(swingHost.await("CP_E2E_LOBBY_READY",
                        Duration.ofSeconds(60)), swingHost.diagnostic());
                swingHost.send("START_GAME");
                TableSession table = gdxClient.tableSession()
                        .toCompletableFuture().get(30, TimeUnit.SECONDS);
                AutoCallRenderer renderer = new AutoCallRenderer(table);
                table.attach(renderer).toCompletableFuture()
                        .get(15, TimeUnit.SECONDS);
                await(renderer.heldAction::get, Duration.ofSeconds(30),
                        renderer::diagnostic);

                closeNativeClientSocket(gdxClient);
                renderer.releaseHeldAction();
                await(() -> nativePeerReconnectionCount(gdxClient,
                                "server") == 1
                                && swingHost.contains(
                                        "has reconnected successfully"),
                        Duration.ofSeconds(20), () -> renderer.diagnostic()
                                + "; swing=" + swingHost.diagnostic());

                await(renderer.closed::get, Duration.ofSeconds(120),
                        renderer::diagnostic);
                assertTrue(swingHost.await("CP_E2E_HANDS_COMPLETE",
                        Duration.ofSeconds(45)), swingHost.diagnostic());
                assertHealthySwingNode(swingHost);
                assertEquals(2, renderer.completedHands.get(),
                        renderer::diagnostic);
                assertSummary(renderer.summary.get(), "client1", 2);
            } finally {
                gdxClient.close();
            }
        } finally {
            closeChildren(children);
        }
    }

    @Test
    @Timeout(value = 6, unit = TimeUnit.MINUTES)
    void swingClientReconnectsInsideThreeHumanMixedGdxTable()
            throws Exception {
        List<ChildNode> children = new ArrayList<>();
        int port = freePort();
        DatabaseService hostDatabase = database(
                "three-human-reconnect-host.sqlite");
        DatabaseService clientDatabase = database(
                "three-human-reconnect-client.sqlite");
        try (hostDatabase; clientDatabase;
             NetworkLobbyGateway hostGateway = gateway(temporary.resolve(
                     "three-human-reconnect-host"), hostDatabase);
             NetworkLobbyGateway clientGateway = gateway(temporary.resolve(
                     "three-human-reconnect-client"), clientDatabase)) {
            LobbySession gdxHost = hostGateway.open(request(false,
                    "server", port, 2)).get(15, TimeUnit.SECONDS);
            try {
                ChildNode swingClient = startSwingNode(temporary.resolve(
                        "three-human-reconnecting-swing-client"), "client",
                        "client1", port, 2, 2, 49001L);
                children.add(swingClient);
                assertTrue(swingClient.await("CP_E2E_READY",
                        Duration.ofSeconds(60)), swingClient.diagnostic());

                LobbySession gdxClient = clientGateway.open(request(true,
                        "client2", port)).get(15, TimeUnit.SECONDS);
                try {
                    await(() -> gdxHost.snapshot().participants().size() == 3
                                    && gdxClient.snapshot().participants()
                                            .size() == 3,
                            Duration.ofSeconds(15));
                    releaseLobbyChecks(swingClient);
                    assertTrue(swingClient.await("CP_E2E_LOBBY_READY",
                            Duration.ofSeconds(60)), swingClient.diagnostic());

                    gdxHost.submit(new LobbyCommand.StartGame())
                            .toCompletableFuture().get(15, TimeUnit.SECONDS);
                    TableSession hostTable = gdxHost.tableSession()
                            .toCompletableFuture().get(15, TimeUnit.SECONDS);
                    AutoCallRenderer hostRenderer = new AutoCallRenderer(
                            hostTable);
                    hostTable.attach(hostRenderer).toCompletableFuture()
                            .get(15, TimeUnit.SECONDS);
                    TableSession clientTable = gdxClient.tableSession()
                            .toCompletableFuture().get(30, TimeUnit.SECONDS);
                    AutoCallRenderer clientRenderer = new AutoCallRenderer(
                            clientTable);
                    clientTable.attach(clientRenderer).toCompletableFuture()
                            .get(15, TimeUnit.SECONDS);

                    await(() -> hostRenderer.heldAction.get()
                                    || clientRenderer.heldAction.get(),
                            Duration.ofSeconds(30), () -> "host="
                                    + hostRenderer.diagnostic() + "; client="
                                    + clientRenderer.diagnostic());
                    swingClient.send("DROP_SOCKET");
                    assertTrue(swingClient.await(
                            "CP_E2E_SOCKET_DROP_REQUESTED",
                            Duration.ofSeconds(10)), swingClient.diagnostic());
                    await(() -> nativePeerReconnectionCount(gdxHost,
                                    "client1") == 1,
                            Duration.ofSeconds(20), () -> "host="
                                    + hostRenderer.diagnostic() + "; client="
                                    + clientRenderer.diagnostic() + "; swing="
                                    + swingClient.diagnostic());
                    hostRenderer.releaseHeldAction();
                    clientRenderer.releaseHeldAction();

                    await(() -> hostRenderer.closed.get()
                                    && clientRenderer.closed.get(),
                            Duration.ofSeconds(120), () -> "host="
                                    + hostRenderer.diagnostic() + "; client="
                                    + clientRenderer.diagnostic());
                    assertTrue(swingClient.await("CP_E2E_HANDS_COMPLETE",
                            Duration.ofSeconds(45)), swingClient.diagnostic());
                    assertHealthySwingNode(swingClient);
                    assertEquals(2, hostRenderer.completedHands.get(),
                            hostRenderer::diagnostic);
                    assertEquals(2, clientRenderer.completedHands.get(),
                            clientRenderer::diagnostic);
                    assertSummary(hostRenderer.summary.get(), "server", 3);
                    assertSummary(clientRenderer.summary.get(), "client2", 3);
                    assertEquals(canonicalBalances(hostRenderer.summary.get()),
                            canonicalBalances(clientRenderer.summary.get()),
                            "three-human mixed ledgers diverged after reconnect");
                    // This scenario owns only the mid-hand reconnect. Stop the
                    // Swing harness while its completed table is still
                    // mounted; transport-terminal classification is covered
                    // independently by the native gateway tests.
                    swingClient.close();
                    children.remove(swingClient);
                } finally {
                    gdxClient.close();
                }
            } finally {
                gdxHost.close();
            }
        } finally {
            closeChildren(children);
        }
    }

    @Test
    @Timeout(value = 6, unit = TimeUnit.MINUTES)
    void gdxClientReconnectsInsideThreeHumanMixedSwingTable()
            throws Exception {
        List<ChildNode> children = new ArrayList<>();
        DatabaseService clientDatabase = database(
                "three-human-swing-host-gdx-reconnect.sqlite");
        try (clientDatabase;
             NetworkLobbyGateway clientGateway = gateway(temporary.resolve(
                     "three-human-swing-host-gdx-reconnect"),
                     clientDatabase)) {
            ChildNode swingHost = startSwingNode(temporary.resolve(
                    "three-human-reconnect-swing-host"), "host", "server",
                    0, 2, 2, 50001L);
            children.add(swingHost);
            assertTrue(swingHost.await("CP_E2E_READY",
                    Duration.ofSeconds(60)), swingHost.diagnostic());
            int port = swingHost.intValueAfter("CP_E2E_READY", "port=");

            ChildNode swingClient = startSwingNode(temporary.resolve(
                    "three-human-reconnect-swing-client"), "client",
                    "client1", port, 2, 2, 50002L);
            children.add(swingClient);
            assertTrue(swingClient.await("CP_E2E_READY",
                    Duration.ofSeconds(60)), swingClient.diagnostic());

            LobbySession gdxClient = clientGateway.open(request(true,
                    "client2", port)).get(15, TimeUnit.SECONDS);
            try {
                await(() -> gdxClient.snapshot().participants().size() == 3,
                        Duration.ofSeconds(15));
                releaseLobbyChecks(swingHost, swingClient);
                assertTrue(swingHost.await("CP_E2E_LOBBY_READY",
                        Duration.ofSeconds(60)), swingHost.diagnostic());
                assertTrue(swingClient.await("CP_E2E_LOBBY_READY",
                        Duration.ofSeconds(60)), swingClient.diagnostic());

                swingHost.send("START_GAME");
                assertTrue(swingHost.await("CP_E2E_GAME_START_REQUESTED",
                        Duration.ofSeconds(30)), swingHost.diagnostic());
                TableSession table = gdxClient.tableSession()
                        .toCompletableFuture().get(30, TimeUnit.SECONDS);
                AutoCallRenderer renderer = new AutoCallRenderer(table);
                table.attach(renderer).toCompletableFuture()
                        .get(15, TimeUnit.SECONDS);
                await(renderer.heldAction::get, Duration.ofSeconds(30),
                        renderer::diagnostic);

                closeNativeClientSocket(gdxClient);
                renderer.releaseHeldAction();
                await(() -> nativePeerReconnectionCount(gdxClient,
                                "server") == 1
                                && swingHost.contains(
                                        "has reconnected successfully"),
                        Duration.ofSeconds(20), () -> renderer.diagnostic()
                                + "; host=" + swingHost.diagnostic()
                                + "; client=" + swingClient.diagnostic());

                await(renderer.closed::get, Duration.ofSeconds(120),
                        renderer::diagnostic);
                assertTrue(swingHost.await("CP_E2E_HANDS_COMPLETE",
                        Duration.ofSeconds(45)), swingHost.diagnostic());
                assertTrue(swingClient.await("CP_E2E_HANDS_COMPLETE",
                        Duration.ofSeconds(45)), swingClient.diagnostic());
                assertHealthySwingNode(swingHost);
                assertHealthySwingNode(swingClient);
                assertEquals(2, renderer.completedHands.get(),
                        renderer::diagnostic);
                assertSummary(renderer.summary.get(), "client2", 3);

                swingClient.close();
                children.remove(swingClient);
                swingHost.close();
                children.remove(swingHost);
            } finally {
                gdxClient.close();
            }
        } finally {
            closeChildren(children);
        }
    }

    @Test
    @Timeout(value = 6, unit = TimeUnit.MINUTES)
    void swingClientCompletesGameRecoveredByGdxHost()
            throws Exception {
        List<ChildNode> children = new ArrayList<>();
        int port = freePort();
        Path hostData = temporary.resolve("mixed-recovery-gdx-host");
        Path swingHome = temporary.resolve("mixed-recovery-swing-client");
        DatabaseService hostDatabase = database(
                "mixed-recovery-gdx-host.sqlite");
        try (hostDatabase;
             NetworkLobbyGateway hostGateway = gateway(hostData,
                     hostDatabase)) {
            LobbySession initialHost = hostGateway.open(request(false,
                    "server", port, 2)).get(15, TimeUnit.SECONDS);
            ChildNode swingClient = null;
            try {
                swingClient = startSwingNode(swingHome, "client", "client1",
                        port, 1, 1, 51001L, "force-recover");
                children.add(swingClient);
                assertTrue(swingClient.await("CP_E2E_READY",
                        Duration.ofSeconds(60)), swingClient.diagnostic());
                await(() -> initialHost.snapshot().participants().size() == 2,
                        Duration.ofSeconds(15));
                releaseLobbyChecks(swingClient);
                assertTrue(swingClient.await("CP_E2E_LOBBY_READY",
                        Duration.ofSeconds(60)), swingClient.diagnostic());

                initialHost.submit(new LobbyCommand.StartGame())
                        .toCompletableFuture().get(15, TimeUnit.SECONDS);
                TableSession initialTable = initialHost.tableSession()
                        .toCompletableFuture().get(15, TimeUnit.SECONDS);
                AutoCallRenderer initialRenderer
                        = new AutoCallRenderer(initialTable);
                initialTable.attach(initialRenderer).toCompletableFuture()
                        .get(15, TimeUnit.SECONDS);
                await(initialRenderer.heldAction::get,
                        Duration.ofSeconds(30), initialRenderer::diagnostic);

                initialTable.commands().submit(new TableCommand.StopGame());
                await(initialRenderer.closed::get, Duration.ofSeconds(30),
                        initialRenderer::diagnostic);
                assertEquals(TableSessionSummary.CloseReason.RECOVERABLE_STOP,
                        initialRenderer.summary.get().reason(),
                        initialRenderer::diagnostic);
            } finally {
                initialHost.close();
            }

            RecoverableGameRepository.RecoverableGame recovered
                    = new RecoverableGameRepository(hostDatabase)
                            .latestLocal().orElseThrow();
            assertTrue(recovered.id() > 0);

            LobbySession recoveredHost = hostGateway.open(recoveryRequest(
                    "server", port, recovered)).get(15, TimeUnit.SECONDS);
            try {
                assertNotNull(swingClient);
                ChildNode recoveringSwing = swingClient;
                assertTrue(recoveringSwing.await(
                        "CP_E2E_RECOVERY_DIALOG_SUBMITTED",
                        Duration.ofSeconds(60)), recoveringSwing.diagnostic());
                await(() -> recoveredHost.snapshot().participants().size() == 2,
                        Duration.ofSeconds(60), recoveringSwing::diagnostic);

                recoveredHost.submit(new LobbyCommand.StartGame())
                        .toCompletableFuture().get(15, TimeUnit.SECONDS);
                TableSession recoveredTable = recoveredHost.tableSession()
                        .toCompletableFuture().get(30, TimeUnit.SECONDS);
                AutoCallRenderer recoveredRenderer
                        = new AutoCallRenderer(recoveredTable);
                recoveredTable.commands().submit(
                        new TableCommand.SetLastHand(true));
                recoveredTable.attach(recoveredRenderer).toCompletableFuture()
                        .get(15, TimeUnit.SECONDS);
                recoveredRenderer.releaseHeldAction();

                await(recoveredRenderer.closed::get,
                        Duration.ofSeconds(120), recoveredRenderer::diagnostic);
                assertTrue(recoveringSwing.await("CP_E2E_HANDS_COMPLETE",
                        Duration.ofSeconds(45)), recoveringSwing.diagnostic());
                assertHealthySwingNode(recoveringSwing);
                assertTrue(recoveredRenderer.completedHands.get() >= 1,
                        recoveredRenderer::diagnostic);
                assertSummary(recoveredRenderer.summary.get(), "server", 2);
            } finally {
                recoveredHost.close();
            }
        } finally {
            closeChildren(children);
        }
    }

    @Test
    @Timeout(value = 6, unit = TimeUnit.MINUTES)
    void gdxClientCompletesGameRecoveredBySwingHost() throws Exception {
        List<ChildNode> children = new ArrayList<>();
        Path clientData = temporary.resolve("mixed-recovery-gdx-client");
        DatabaseService clientDatabase = database(
                "mixed-recovery-gdx-client.sqlite");
        try (clientDatabase;
             NetworkLobbyGateway clientGateway = gateway(clientData,
                     clientDatabase)) {
            ChildNode swingHost = startSwingNode(temporary.resolve(
                    "mixed-recovery-swing-host"), "host", "server", 0, 1,
                    1, 52001L, "force-recover");
            children.add(swingHost);
            assertTrue(swingHost.await("CP_E2E_READY",
                    Duration.ofSeconds(60)), swingHost.diagnostic());
            int port = swingHost.intValueAfter("CP_E2E_READY", "port=");

            LobbySession initialClient = clientGateway.open(request(true,
                    "client1", port)).get(15, TimeUnit.SECONDS);
            try {
                await(() -> initialClient.snapshot().participants().size() == 2,
                        Duration.ofSeconds(15));
                releaseLobbyChecks(swingHost);
                assertTrue(swingHost.await("CP_E2E_LOBBY_READY",
                        Duration.ofSeconds(60)), swingHost.diagnostic());
                swingHost.send("START_GAME");

                TableSession initialTable = initialClient.tableSession()
                        .toCompletableFuture().get(30, TimeUnit.SECONDS);
                AutoCallRenderer initialRenderer
                        = new AutoCallRenderer(initialTable);
                initialTable.attach(initialRenderer).toCompletableFuture()
                        .get(15, TimeUnit.SECONDS);
                await(initialRenderer.heldAction::get,
                        Duration.ofSeconds(30), initialRenderer::diagnostic);

                swingHost.send("FORCE_RECOVER");
                assertTrue(swingHost.await("CP_E2E_FORCE_RECOVER_REQUESTED",
                        Duration.ofSeconds(30)), swingHost.diagnostic());
                await(initialRenderer.closed::get, Duration.ofSeconds(30),
                        initialRenderer::diagnostic);
                assertEquals(TableSessionSummary.CloseReason.RECOVERABLE_STOP,
                        initialRenderer.summary.get().reason(),
                        initialRenderer::diagnostic);
            } finally {
                initialClient.close();
            }

            assertTrue(swingHost.await("CP_E2E_RECOVERY_DIALOG_SUBMITTED",
                    Duration.ofSeconds(60)), swingHost.diagnostic());
            LobbySession recoveredClient = openEventually(clientGateway,
                    request(true, "client1", port), Duration.ofSeconds(60));
            try {
                await(() -> recoveredClient.snapshot().participants().size()
                                == 2,
                        Duration.ofSeconds(60), swingHost::diagnostic);
                swingHost.send("START_RECOVERED_GAME");
                assertTrue(swingHost.await(
                        "CP_E2E_RECOVERED_GAME_START_REQUESTED",
                        Duration.ofSeconds(30)), swingHost.diagnostic());

                TableSession recoveredTable = recoveredClient.tableSession()
                        .toCompletableFuture().get(60, TimeUnit.SECONDS);
                AutoCallRenderer recoveredRenderer
                        = new AutoCallRenderer(recoveredTable);
                recoveredTable.attach(recoveredRenderer).toCompletableFuture()
                        .get(15, TimeUnit.SECONDS);
                recoveredRenderer.releaseHeldAction();

                await(recoveredRenderer.closed::get,
                        Duration.ofSeconds(120), recoveredRenderer::diagnostic);
                assertTrue(swingHost.await("CP_E2E_HANDS_COMPLETE",
                        Duration.ofSeconds(45)), swingHost.diagnostic());
                assertHealthySwingNode(swingHost);
                assertTrue(recoveredRenderer.completedHands.get() >= 1,
                        recoveredRenderer::diagnostic);
                assertSummary(recoveredRenderer.summary.get(), "client1", 2);
            } finally {
                recoveredClient.close();
            }
        } finally {
            closeChildren(children);
        }
    }

    private DatabaseService database(String filename) throws Exception {
        DatabaseService database = new DatabaseService(
                temporary.resolve(filename).toString());
        database.start();
        return database;
    }

    private static NetworkLobbyGateway gateway(Path data,
            DatabaseService database) {
        return gateway(data, database, GameDecisionSink.noop(),
                acceleratedSettings());
    }

    private static NetworkLobbyGateway gateway(Path data,
            DatabaseService database, GameDecisionSink decisions,
            GamePresentationSettings settings) {
        CoreGameTableFactory tables = new CoreGameTableFactory(database,
                GameText.keys(), GameLogSink.noop(), GameDialogSink.noop(),
                decisions, settings);
        return new NetworkLobbyGateway(data, tables);
    }

    private static GamePresentationSettings acceleratedSettings() {
        GamePresentationSettings defaults = GamePresentationSettings.defaults();
        return (GamePresentationSettings) java.lang.reflect.Proxy.newProxyInstance(
                GamePresentationSettings.class.getClassLoader(),
                new Class<?>[]{GamePresentationSettings.class},
                (proxy, method, args) -> "testMode".equals(method.getName())
                        ? true : method.invoke(defaults, args));
    }

    private static GamePresentationSettings liveAcceleratedRebuySettings() {
        GamePresentationSettings defaults = GamePresentationSettings.defaults();
        return (GamePresentationSettings) java.lang.reflect.Proxy.newProxyInstance(
                GamePresentationSettings.class.getClassLoader(),
                new Class<?>[]{GamePresentationSettings.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "autoRebuyOnBroke" -> true;
                    case "ambientMusic", "cinematics",
                            "gameOverCinematics", "blindDealerAnimation",
                            "betAnimation", "counterAnimation",
                            "shuffleAnimation", "dealAnimation",
                            "flipAnimation", "swapAnimation", "callSound",
                            "betSound", "blindSound", "shuffleSound",
                            "dealSound", "flipSound", "cashSound",
                            "iwtsthSound", "startSound", "warningSound",
                            "errorSound" -> false;
                    default -> method.invoke(defaults, args);
                });
    }

    private static GamePresentationSettings liveTimeoutSettings() {
        GamePresentationSettings defaults = GamePresentationSettings.defaults();
        return (GamePresentationSettings) java.lang.reflect.Proxy.newProxyInstance(
                GamePresentationSettings.class.getClassLoader(),
                new Class<?>[]{GamePresentationSettings.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "ambientMusic", "cinematics",
                            "gameOverCinematics", "blindDealerAnimation",
                            "betAnimation", "counterAnimation",
                            "shuffleAnimation", "dealAnimation",
                            "flipAnimation", "swapAnimation", "callSound",
                            "betSound", "blindSound", "shuffleSound",
                            "dealSound", "flipSound", "cashSound",
                            "iwtsthSound", "startSound", "errorSound" -> false;
                    default -> method.invoke(defaults, args);
                });
    }

    private static GameDecisionSink acceptingAutomaticRebuyDecisions(
            AtomicInteger choices) {
        GameDecisionSink fallback = GameDecisionSink.noop();
        return (GameDecisionSink) java.lang.reflect.Proxy.newProxyInstance(
                GameDecisionSink.class.getClassLoader(),
                new Class<?>[]{GameDecisionSink.class},
                (proxy, method, args) -> {
                    if ("showRebuy".equals(method.getName())) {
                        GameDecisionSink.RebuyRequest request
                                = (GameDecisionSink.RebuyRequest) args[0];
                        assertTrue(request.automatic(),
                                "broke GDX player must use automatic rebuy");
                        choices.incrementAndGet();
                        GameDecisionSink.RebuyResult accepted
                                = new GameDecisionSink.RebuyResult(true,
                                        request.defaultAmount());
                        return new GameDecisionSink.RebuyHandle() {
                            @Override
                            public CompletionStage<GameDecisionSink.RebuyResult>
                                    result() {
                                return CompletableFuture.completedFuture(
                                        accepted);
                            }

                            @Override public void close() { }
                        };
                    }
                    return method.invoke(fallback, args);
                });
    }

    private static GameDecisionSink acceptingRunItTwiceDecisions(
            AtomicInteger votes) {
        GameDecisionSink fallback = GameDecisionSink.noop();
        return (GameDecisionSink) java.lang.reflect.Proxy.newProxyInstance(
                GameDecisionSink.class.getClassLoader(),
                new Class<?>[]{GameDecisionSink.class},
                (proxy, method, args) -> {
                    if ("showRunItTwice".equals(method.getName())) {
                        votes.incrementAndGet();
                        java.util.function.IntConsumer listener
                                = (java.util.function.IntConsumer) args[3];
                        if (listener != null) {
                            CompletableFuture.runAsync(() -> listener.accept(
                                    GameDecisionSink.VOTE_RUN_IT_TWICE));
                        }
                        return new GameDecisionSink.RunItTwiceHandle() {
                            @Override public int currentVote() {
                                return GameDecisionSink.VOTE_RUN_IT_TWICE;
                            }

                            @Override public void updateTally(int normal,
                                    int runItTwice) { }

                            @Override public void close() { }
                        };
                    }
                    return method.invoke(fallback, args);
                });
    }

    private static GameDecisionSink acceptingStraddleDecisions(String local,
            AtomicInteger choices, AtomicReference<String> straddler) {
        GameDecisionSink fallback = GameDecisionSink.noop();
        return (GameDecisionSink) java.lang.reflect.Proxy.newProxyInstance(
                GameDecisionSink.class.getClassLoader(),
                new Class<?>[]{GameDecisionSink.class},
                (proxy, method, args) -> {
                    if ("showStraddle".equals(method.getName())) {
                        choices.incrementAndGet();
                        assertTrue(straddler.compareAndSet(null, local),
                                "more than one GDX seat requested straddle");
                        CompletableFuture<Integer> answer
                                = CompletableFuture.completedFuture(
                                        GameDecisionSink.POST_STRADDLE);
                        return new GameDecisionSink.StraddleHandle() {
                            @Override public CompletionStage<Integer> decision() {
                                return answer;
                            }

                            @Override public boolean isOpen() {
                                return !answer.isDone();
                            }

                            @Override public void accept() { }
                            @Override public void decline() { }
                            @Override public void refreshLayout() { }
                        };
                    }
                    return method.invoke(fallback, args);
                });
    }

    private static NewGameRequest request(boolean joining, String nickname,
            int port) {
        return request(joining, nickname, port, 1);
    }

    private static NewGameRequest request(boolean joining, String nickname,
            int port, int hands) {
        return passwordRequest(joining, nickname, port, "", hands);
    }

    private static NewGameRequest passwordRequest(boolean joining,
            String nickname, int port, String password, int hands) {
        NewGameConnectionDraft.Submission connection
                = new NewGameConnectionDraft.Submission(
                        joining ? NewGameConnectionDraft.Mode.JOIN
                                : NewGameConnectionDraft.Mode.CREATE,
                        nickname, password, "127.0.0.1", Integer.toString(port),
                        null, false, false, null);
        if (joining) return new NewGameRequest(connection, null);
        NewGameTableDraft table = new NewGameTableDraft();
        table.setHandLimit(true);
        table.setHandLimitCount(hands);
        table.setThinkTime(false);
        return new NewGameRequest(connection, table.snapshot());
    }

    private static NewGameRequest recoveryRequest(String nickname, int port,
            RecoverableGameRepository.RecoverableGame recovered) {
        NewGameConnectionDraft.Submission connection
                = new NewGameConnectionDraft.Submission(
                        NewGameConnectionDraft.Mode.RECOVER, nickname, "",
                        "127.0.0.1", Integer.toString(port), null, false, true,
                        recovered.id());
        return new NewGameRequest(connection, recovered.settings());
    }

    private static NewGameRequest timeoutRequest(boolean joining,
            String nickname, int port) {
        NewGameConnectionDraft.Submission connection
                = new NewGameConnectionDraft.Submission(
                        joining ? NewGameConnectionDraft.Mode.JOIN
                                : NewGameConnectionDraft.Mode.CREATE,
                        nickname, "", "127.0.0.1", Integer.toString(port),
                        null, false, false, null);
        if (joining) return new NewGameRequest(connection, null);
        NewGameTableDraft table = new NewGameTableDraft();
        table.setHandLimit(true);
        table.setHandLimitCount(1);
        table.setThinkTime(true);
        table.setThinkSeconds(10);
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
        if (joining) return new NewGameRequest(connection, null);
        NewGameTableDraft table = new NewGameTableDraft();
        table.setHandLimit(true);
        table.setHandLimitCount(1);
        table.setThinkTime(false);
        table.setRunItTwice(true);
        return new NewGameRequest(connection, table.snapshot());
    }

    private static NewGameRequest straddleRequest(boolean joining,
            String nickname, int port) {
        NewGameConnectionDraft.Submission connection
                = new NewGameConnectionDraft.Submission(
                        joining ? NewGameConnectionDraft.Mode.JOIN
                                : NewGameConnectionDraft.Mode.CREATE,
                        nickname, "", "127.0.0.1", Integer.toString(port),
                        null, false, false, null);
        if (joining) return new NewGameRequest(connection, null);
        NewGameTableDraft table = new NewGameTableDraft();
        table.setHandLimit(true);
        table.setHandLimitCount(1);
        table.setThinkTime(false);
        table.setStraddle(true);
        return new NewGameRequest(connection, table.snapshot());
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static ChildNode startSwingNode(Path home, String role,
            String nick, int port, int clients, int hands, long seed)
            throws IOException {
        return startSwingNode(home, role, nick, port, clients, hands, seed,
                "normal");
    }

    private static ChildNode startSwingNode(Path home, String role,
            String nick, int port, int clients, int hands, long seed,
            String scenario) throws IOException {
        return startSwingNode(home, role, nick, port, clients, hands, seed,
                scenario, true);
    }

    private static ChildNode startSwingNode(Path home, String role,
            String nick, int port, int clients, int hands, long seed,
            String scenario, boolean testMode) throws IOException {
        return startSwingNode(home, role, nick, port, clients, hands, seed,
                scenario, testMode, null);
    }

    private static ChildNode startSwingNode(Path home, String role,
            String nick, int port, int clients, int hands, long seed,
            String scenario, boolean testMode, String password)
            throws IOException {
        Files.createDirectories(home);
        // The production installer creates this persistent directory. Each
        // isolated child gets a fresh user.home, so the harness must provide
        // the same precondition before finTransmision stores the game log.
        Files.createDirectories(home.resolve(".coronapoker").resolve("Logs"));
        String java = Path.of(System.getProperty("java.home"), "bin", "java")
                .toString();
        String classpath = System.getProperty("surefire.test.class.path",
                System.getProperty("java.class.path"));
        ProcessBuilder builder = new ProcessBuilder(java,
                "-Djava.awt.headless=false",
                "-Dcoronapoker.qa.suppressDialogs=true",
                "-Dcoronapoker.qa.windowMode=hidden",
                "-Dcoronapoker.qa.screen=2",
                "-Dcoronapoker.qa.animations=false",
                "-Dcoronapoker.qa.scenario=" + scenario,
                "-Dcoronapoker.testMode=" + testMode,
                "-Duser.home=" + home.toAbsolutePath(),
                "-cp", classpath, RealGameNodeMain.class.getName(),
                role, nick, Integer.toString(port), Integer.toString(clients),
                "0", Integer.toString(hands), Long.toString(seed), "false");
        if (password != null && !password.isBlank()) {
            builder.command().add(1,
                    "-Dcoronapoker.qa.password=" + password);
        }
        builder.redirectErrorStream(true);
        return new ChildNode(role + ":" + nick, builder.start());
    }

    private static void releaseLobbyChecks(ChildNode... nodes)
            throws Exception {
        for (ChildNode node : nodes) node.send("CHECK_LOBBY");
        for (ChildNode node : nodes) {
            assertTrue(node.await("CP_E2E_LOBBY_CHECK_RELEASED",
                    Duration.ofSeconds(30)), node.diagnostic());
        }
    }

    private static void assertHealthySwingNode(ChildNode node) {
        assertNoCriticalSwingFailure(node);
        String ledger = node.lastLineContaining("CP_E2E_LEDGER");
        assertNotNull(ledger, node.diagnostic());
        assertEquals(metric(ledger, "buyinCents"), metric(ledger, "stackCents"),
                node.diagnostic());
    }

    private static void assertNoCriticalSwingFailure(ChildNode node) {
        assertFalse(node.contains("CP_E2E_FAIL"), node.diagnostic());
        assertFalse(node.contains("TABLE_FAILURE_V1"), node.diagnostic());
        assertFalse(node.contains("invalid atomic POTCARDS"), node.diagnostic());
        assertFalse(node.contains("FAILED signature verify"), node.diagnostic());
        assertFalse(node.contains("missing mandatory"), node.diagnostic());
        assertFalse(node.contains("NoSuchFileException"), node.diagnostic());
        assertFalse(node.contains("lost 3 consecutive PONGs"),
                node.diagnostic());
        assertFalse(node.contains("Server denied reconnect"),
                node.diagnostic());
        assertFalse(node.contains("Could not encrypt game command"),
                node.diagnostic());
    }

    private static long metric(String line, String name) {
        String prefix = name + "=";
        for (String part : line.split("\\s+")) {
            if (part.startsWith(prefix)) {
                return Long.parseLong(part.substring(prefix.length()));
            }
        }
        throw new AssertionError("missing " + name + " in " + line);
    }

    private static void assertSummary(TableSessionSummary summary,
            String localNickname, int seats) {
        assertNotNull(summary, "GDX endpoint did not publish a final summary");
        assertEquals(localNickname, summary.localNickname());
        assertTrue(summary.handCount() >= 1);
        assertEquals(seats, summary.balances().size());
        double stacks = summary.balances().stream()
                .mapToDouble(TableSessionSummary.PlayerBalance::finalStack).sum();
        double buyins = summary.balances().stream()
                .mapToDouble(TableSessionSummary.PlayerBalance::totalBuyin).sum();
        assertEquals(buyins, stacks, 0.001d,
                "GDX final ledger does not conserve table money");
    }

    private static void assertRebuyCompleted(RebuyRenderer renderer,
            AtomicInteger gdxChoices, ChildNode swingNode) {
        assertNotNull(renderer.summary.get(), renderer::diagnostic);
        assertEquals(5, renderer.completedHands.get(), renderer::diagnostic);
        assertTrue(renderer.rebuyEvents.get() >= 1,
                "accepted rebuy did not cross the renderer barrier: "
                        + renderer.diagnostic());
        assertTrue(renderer.summary.get().balances().stream()
                        .anyMatch(balance -> balance.totalBuyin() > 10d),
                "no player carried a rebuy into the final GDX ledger: "
                        + renderer.diagnostic());
        int swingChoices = swingNode.contains("CP_E2E_REBUY_ACCEPTED") ? 1 : 0;
        assertTrue(gdxChoices.get() + swingChoices >= 1,
                "neither frontend completed a real rebuy decision: gdx="
                        + gdxChoices + "\nswing=" + swingNode.diagnostic());
        assertSummary(renderer.summary.get(),
                renderer.table.initialState().localNickname(), 2);
    }

    private static List<String> canonicalBalances(TableSessionSummary summary) {
        return summary.balances().stream()
                .map(balance -> balance.nickname() + "=" + balance.finalStack()
                        + "/" + balance.totalBuyin())
                .sorted().toList();
    }

    private static boolean hasQaLiveConfiguration(
            GameConfigCodecV1.Configuration configuration) {
        return configuration != null
                && configuration.hands() == 3
                && configuration.iwtsth()
                && configuration.runItTwice()
                && configuration.rabbitHunting() == 2
                && configuration.botRebuy()
                && configuration.botBalanceToHumans()
                && Double.compare(configuration.smallBlind(), 0.2d) == 0
                && Double.compare(configuration.bigBlind(), 0.4d) == 0
                && configuration.blindsDouble() == 0
                && configuration.blindsDoubleType() == 1
                && Double.compare(configuration.blindCap(), 1.0d) == 0
                && configuration.ante()
                && !configuration.straddle()
                && configuration.blindStructure().equals(List.of(
                        new GameConfigCodecV1.BlindLevel(0.2d, 0.4d),
                        new GameConfigCodecV1.BlindLevel(0.3d, 0.6d),
                        new GameConfigCodecV1.BlindLevel(0.5d, 1.0d)));
    }

    private static void await(BooleanSupplier condition, Duration timeout)
            throws Exception {
        await(condition, timeout, () -> "");
    }

    private static void await(BooleanSupplier condition, Duration timeout,
            java.util.function.Supplier<String> diagnostic) throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Timed out waiting for mixed table: "
                        + diagnostic.get());
            }
            Thread.sleep(20L);
        }
    }

    private static LobbySession openEventually(NetworkLobbyGateway gateway,
            NewGameRequest request, Duration timeout) throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        Throwable lastFailure = null;
        while (Instant.now().isBefore(deadline)) {
            try {
                return gateway.open(request).get(5, TimeUnit.SECONDS);
            } catch (java.util.concurrent.ExecutionException
                    | java.util.concurrent.TimeoutException failure) {
                lastFailure = failure;
                Thread.sleep(100L);
            }
        }
        throw new AssertionError("Timed out reopening mixed recovery client",
                lastFailure);
    }

    private static void closeChildren(List<ChildNode> nodes) {
        for (ChildNode node : nodes) node.close();
    }

    private static int nativePeerReconnectionCount(LobbySession session,
            String nickname) {
        try {
            Object transport = field(session, "resource");
            Object gameChannel = field(transport, "gameChannel");
            java.lang.reflect.Method count = gameChannel.getClass()
                    .getDeclaredMethod("peerReconnectionCount", String.class);
            count.setAccessible(true);
            return (int) count.invoke(gameChannel, nickname);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot inspect native reconnect count",
                    failure);
        }
    }

    private static void closeNativeClientSocket(LobbySession session)
            throws Exception {
        Object transport = field(session, "resource");
        Object connection = field(transport, "serverConnection");
        Object generation = field(connection, "generation");
        ((Socket) field(generation, "socket")).close();
    }

    private static Object field(Object target, String name)
            throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static final class HumanActionRenderer implements TableRenderer {
        private final TableSession table;
        private final AtomicInteger hand = new AtomicInteger();
        private final AtomicInteger endedHands = new AtomicInteger();
        private final AtomicBoolean raiseSubmitted = new AtomicBoolean();
        private final AtomicBoolean foldSubmitted = new AtomicBoolean();
        private final AtomicBoolean allInSubmitted = new AtomicBoolean();
        private final AtomicBoolean sawRaise = new AtomicBoolean();
        private final AtomicBoolean sawFold = new AtomicBoolean();
        private final AtomicBoolean sawAllIn = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicReference<TableSessionSummary> summary
                = new AtomicReference<>();
        private final List<String> events
                = new java.util.concurrent.CopyOnWriteArrayList<>();

        HumanActionRenderer(TableSession table) {
            this.table = table;
        }

        @Override
        public CompletionStage<Void> open(TableSnapshot initialState) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> render(TableVisualEvent event) {
            events.add(event.getClass().getSimpleName());
            if (event instanceof TableVisualEvent.HandBoundary boundary) {
                if (boundary.phase()
                        == TableVisualEvent.HandBoundary.Phase.END) {
                    endedHands.incrementAndGet();
                }
            } else if (event instanceof TableVisualEvent.TableInfo info
                    && info.handNumber() > 0) {
                hand.set(info.handNumber());
            } else if (event instanceof TableVisualEvent.ActionControls controls) {
                ActionControlState state = controls.state();
                int currentHand = hand.get();
                if (currentHand == 1 && !raiseSubmitted.get()
                        && state.raiseAction()
                        != ActionControlState.RaiseAction.DISABLED
                        && raiseSubmitted.compareAndSet(false, true)) {
                    table.commands().submit(new TableCommand.Bet(
                            state.raiseAmount()));
                } else if (currentHand == 2 && state.foldEnabled()
                        && foldSubmitted.compareAndSet(false, true)) {
                    table.commands().submit(new TableCommand.Fold());
                } else if (currentHand >= 3 && state.allInEnabled()
                        && allInSubmitted.compareAndSet(false, true)) {
                    table.commands().submit(new TableCommand.AllIn());
                } else if (state.callAction()
                        != ActionControlState.CallAction.DISABLED) {
                    table.commands().submit(new TableCommand.CheckOrCall());
                }
            } else if (event instanceof TableVisualEvent.PlayerAction action
                    && action.nickname().equals(
                            table.initialState().localNickname())) {
                if (action.kind() == TableVisualEvent.PlayerAction.ActionKind.BET
                        || action.kind()
                        == TableVisualEvent.PlayerAction.ActionKind.RAISE
                        || action.kind()
                        == TableVisualEvent.PlayerAction.ActionKind.RERAISE) {
                    sawRaise.set(true);
                } else if (action.kind()
                        == TableVisualEvent.PlayerAction.ActionKind.FOLD) {
                    sawFold.set(true);
                } else if (action.kind()
                        == TableVisualEvent.PlayerAction.ActionKind.ALL_IN) {
                    sawAllIn.set(true);
                }
            } else if (event instanceof TableVisualEvent.CloseTable close) {
                summary.set(close.summary());
                closed.set(true);
            }
            return CompletableFuture.completedFuture(null);
        }

        void assertComplete() {
            assertEquals(3, endedHands.get(), diagnostic());
            assertTrue(raiseSubmitted.get() && sawRaise.get(), diagnostic());
            assertTrue(foldSubmitted.get() && sawFold.get(), diagnostic());
            assertTrue(allInSubmitted.get() && sawAllIn.get(), diagnostic());
            assertNotNull(summary.get(), diagnostic());
        }

        String diagnostic() {
            return "hand=" + hand + ", ended=" + endedHands
                    + ", raise=" + raiseSubmitted + "/" + sawRaise
                    + ", fold=" + foldSubmitted + "/" + sawFold
                    + ", allIn=" + allInSubmitted + "/" + sawAllIn
                    + ", closed=" + closed + ", events=" + events;
        }

        @Override public void close() { }
    }

    private static final class AutoCallRenderer implements TableRenderer {
        private final TableSession table;
        private final AtomicInteger completedHands = new AtomicInteger();
        private final AtomicBoolean sawRemoteAction = new AtomicBoolean();
        private final AtomicBoolean holdFirstAction = new AtomicBoolean(true);
        private final AtomicBoolean heldAction = new AtomicBoolean();
        private final AtomicBoolean sawPaused = new AtomicBoolean();
        private final AtomicBoolean sawResumed = new AtomicBoolean();
        private final AtomicBoolean sawHurryCue = new AtomicBoolean();
        private final AtomicBoolean sawHurryStop = new AtomicBoolean();
        private final AtomicBoolean sawTimeoutCue = new AtomicBoolean();
        private final AtomicBoolean sawConfiguredBlinds = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicReference<TableSessionSummary> summary
                = new AtomicReference<>();
        private final AtomicReference<GameConfigCodecV1.Configuration>
                configuration = new AtomicReference<>();
        private final List<String> events = new java.util.concurrent.CopyOnWriteArrayList<>();

        AutoCallRenderer(TableSession table) {
            this.table = table;
        }

        @Override
        public CompletionStage<Void> open(TableSnapshot initialState) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> render(TableVisualEvent event) {
            events.add(event.getClass().getSimpleName());
            if (event instanceof TableVisualEvent.PauseStatus pause) {
                if (pause.paused()) {
                    sawPaused.set(true);
                } else if (sawPaused.get()) {
                    sawResumed.set(true);
                }
            } else if (event instanceof TableVisualEvent.GameConfigurationStatus
                    status) {
                configuration.set(status.configuration());
            } else if (event instanceof TableVisualEvent.TableInfo info
                    && info.handNumber() >= 2
                    && Double.compare(info.smallBlind(), 0.2d) == 0
                    && Double.compare(info.bigBlind(), 0.4d) == 0) {
                sawConfiguredBlinds.set(true);
            } else if (event instanceof TableVisualEvent.PlayerAction action
                    && !action.nickname().equals(
                            table.initialState().localNickname())) {
                sawRemoteAction.set(true);
            } else if (event instanceof TableVisualEvent.AudioCue cue) {
                if ("misc/hurryup.wav".equals(cue.resource())) {
                    if (cue.operation()
                            == TableVisualEvent.AudioCue.Operation.STOP) {
                        sawHurryStop.set(true);
                    } else if (cue.operation()
                            == TableVisualEvent.AudioCue.Operation.PLAY) {
                        sawHurryCue.set(true);
                    }
                } else if ("misc/timeout.wav".equals(cue.resource())) {
                    sawTimeoutCue.set(true);
                }
            } else if (event instanceof TableVisualEvent.ActionControls controls
                    && controls.state().callAction()
                    != ActionControlState.CallAction.DISABLED) {
                if (holdFirstAction.get()
                        && heldAction.compareAndSet(false, true)) {
                    return CompletableFuture.completedFuture(null);
                }
                table.commands().submit(new TableCommand.CheckOrCall());
            } else if (event instanceof TableVisualEvent.HandBoundary boundary
                    && boundary.phase() == TableVisualEvent.HandBoundary.Phase.END) {
                completedHands.incrementAndGet();
            } else if (event instanceof TableVisualEvent.CloseTable close) {
                summary.set(close.summary());
                closed.set(true);
            }
            return CompletableFuture.completedFuture(null);
        }

        void releaseHeldAction() {
            holdFirstAction.set(false);
            if (heldAction.getAndSet(false)) {
                table.commands().submit(new TableCommand.CheckOrCall());
            }
        }

        String diagnostic() {
            return "hands=" + completedHands + ", remote=" + sawRemoteAction
                    + ", held=" + heldAction + ", paused=" + sawPaused
                    + ", resumed=" + sawResumed + ", closed=" + closed
                    + ", hurry=" + sawHurryCue + ", hurryStop="
                    + sawHurryStop + ", timeout=" + sawTimeoutCue
                    + ", configuredBlinds=" + sawConfiguredBlinds
                    + ", config=" + configuration.get()
                    + ", events=" + events;
        }

        @Override public void close() { }
    }

    private static final class AllInBarrierRenderer implements TableRenderer {
        private final TableSession table;
        private final AtomicBoolean cinematicStarted = new AtomicBoolean();
        private final AtomicBoolean cinematicPending = new AtomicBoolean();
        private final AtomicBoolean sawControlsDuringCinematic
                = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicReference<TableSessionSummary> summary
                = new AtomicReference<>();
        private final CompletableFuture<Void> firstCinematic
                = new CompletableFuture<>();
        private final List<String> events
                = new java.util.concurrent.CopyOnWriteArrayList<>();

        AllInBarrierRenderer(TableSession table) {
            this.table = table;
        }

        @Override
        public CompletionStage<Void> open(TableSnapshot initialState) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> render(TableVisualEvent event) {
            events.add(event.getClass().getSimpleName());
            if (event instanceof TableVisualEvent.Cinematic cinematic
                    && cinematic.type()
                    == TableVisualEvent.Cinematic.Type.ALL_IN
                    && cinematic.phase()
                    == TableVisualEvent.Cinematic.Phase.START
                    && cinematicStarted.compareAndSet(false, true)) {
                cinematicPending.set(true);
                return firstCinematic;
            }
            if (event instanceof TableVisualEvent.ActionControls controls) {
                if (cinematicPending.get()) {
                    sawControlsDuringCinematic.set(true);
                }
                if (controls.state().allInEnabled()) {
                    table.commands().submit(new TableCommand.AllIn());
                } else if (controls.state().callAction()
                        != ActionControlState.CallAction.DISABLED) {
                    table.commands().submit(new TableCommand.CheckOrCall());
                }
            } else if (event instanceof TableVisualEvent.CloseTable close) {
                summary.set(close.summary());
                closed.set(true);
            }
            return CompletableFuture.completedFuture(null);
        }

        void releaseCinematic() {
            cinematicPending.set(false);
            firstCinematic.complete(null);
        }

        String diagnostic() {
            return "started=" + cinematicStarted + ", pending="
                    + cinematicPending + ", controlsDuring="
                    + sawControlsDuringCinematic + ", closed=" + closed
                    + ", events=" + events;
        }

        @Override public void close() { }
    }

    private static final class RunItTwiceRenderer implements TableRenderer {
        private final TableSession table;
        private final AtomicLong sideASequence = new AtomicLong();
        private final AtomicLong sideBSequence = new AtomicLong();
        private final List<Integer> sideBDeals
                = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final Set<String> revealedPlayers
                = ConcurrentHashMap.newKeySet();
        private final Set<String> monteCarloPlayers
                = ConcurrentHashMap.newKeySet();
        private final Map<String, Boolean> results
                = new ConcurrentHashMap<>();
        private final Map<String, Double> payouts
                = new ConcurrentHashMap<>();
        private final AtomicReference<Double> finalPotAfterPayout
                = new AtomicReference<>();
        private final AtomicInteger endedHands = new AtomicInteger();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicReference<TableSessionSummary> summary
                = new AtomicReference<>();
        private final List<String> events
                = new java.util.concurrent.CopyOnWriteArrayList<>();

        RunItTwiceRenderer(TableSession table) {
            this.table = table;
        }

        @Override
        public CompletionStage<Void> open(TableSnapshot initialState) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> render(TableVisualEvent event) {
            events.add(event.getClass().getSimpleName());
            if (event instanceof TableVisualEvent.ActionControls controls) {
                if (controls.state().allInEnabled()) {
                    table.commands().submit(new TableCommand.AllIn());
                } else if (controls.state().callAction()
                        != ActionControlState.CallAction.DISABLED) {
                    table.commands().submit(new TableCommand.CheckOrCall());
                }
            } else if (event instanceof TableVisualEvent.RunItTwiceBoard board) {
                if (board.side() == TableVisualEvent.RunItTwiceBoard.Side.A) {
                    sideASequence.compareAndSet(0L, board.sequence());
                } else {
                    sideBSequence.compareAndSet(0L, board.sequence());
                }
            } else if (event instanceof TableVisualEvent.DealCommunityCard deal
                    && sideBSequence.get() > 0L
                    && deal.sequence() > sideBSequence.get()) {
                sideBDeals.add(deal.slot());
            } else if (event instanceof TableVisualEvent.RevealHoleCards reveal
                    && !reveal.left().code().isBlank()
                    && !reveal.right().code().isBlank()) {
                revealedPlayers.add(reveal.nickname());
            } else if (event instanceof TableVisualEvent.PartialHand partial) {
                monteCarloPlayers.add(partial.nickname());
            } else if (event instanceof TableVisualEvent.HandResult result) {
                assertFalse(result.handName().isBlank());
                results.put(result.nickname(), result.winner());
            } else if (event instanceof TableVisualEvent.Payout payout) {
                payouts.merge(payout.nickname(), payout.amount(), Double::sum);
                finalPotAfterPayout.set(payout.potAfter());
            } else if (event instanceof TableVisualEvent.HandBoundary boundary
                    && boundary.phase()
                    == TableVisualEvent.HandBoundary.Phase.END) {
                endedHands.incrementAndGet();
            } else if (event instanceof TableVisualEvent.CloseTable close) {
                summary.set(close.summary());
                closed.set(true);
            }
            return CompletableFuture.completedFuture(null);
        }

        void assertComplete() {
            assertTrue(sideASequence.get() > 0L, diagnostic());
            assertTrue(sideBSequence.get() > sideASequence.get(), diagnostic());
            assertEquals(List.of(0, 1, 2, 3, 4), sideBDeals,
                    diagnostic());
            assertEquals(Set.of("server", "client1"), revealedPlayers,
                    diagnostic());
            assertEquals(revealedPlayers, monteCarloPlayers, diagnostic());
            assertEquals(revealedPlayers, results.keySet(), diagnostic());
            assertTrue(results.containsValue(Boolean.TRUE), diagnostic());
            assertTrue(results.containsValue(Boolean.FALSE), diagnostic());
            assertFalse(payouts.isEmpty(), diagnostic());
            assertEquals(0d, finalPotAfterPayout.get(), 0.001d,
                    diagnostic());
            assertEquals(1, endedHands.get(), diagnostic());
            assertNotNull(summary.get(), diagnostic());
            double totalBuyin = summary.get().balances().stream()
                    .mapToDouble(TableSessionSummary.PlayerBalance::totalBuyin)
                    .sum();
            double totalPayout = payouts.values().stream()
                    .mapToDouble(Double::doubleValue).sum();
            assertEquals(totalBuyin, totalPayout, 0.001d, diagnostic());
            summary.get().balances().forEach(balance -> assertEquals(
                    balance.finalStack(),
                    payouts.getOrDefault(balance.nickname(), 0d), 0.001d,
                    diagnostic()));
        }

        String diagnostic() {
            return "sideA=" + sideASequence + ", sideB=" + sideBSequence
                    + ", sideBDeals=" + sideBDeals + ", reveals="
                    + revealedPlayers + ", monteCarlo=" + monteCarloPlayers
                    + ", results=" + results + ", payouts=" + payouts
                    + ", finalPot=" + finalPotAfterPayout
                    + ", ended=" + endedHands
                    + ", closed=" + closed
                    + ", events=" + events;
        }

        @Override public void close() { }
    }

    private static final class StraddleRenderer implements TableRenderer {
        private final TableSession table;
        private final AtomicBoolean sawStraddlePosition = new AtomicBoolean();
        private final AtomicLong straddleSequence = new AtomicLong();
        private final AtomicLong firstActionSequence = new AtomicLong();
        private final AtomicInteger endedHands = new AtomicInteger();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicReference<TableSessionSummary> summary
                = new AtomicReference<>();
        private final List<String> events
                = new java.util.concurrent.CopyOnWriteArrayList<>();

        StraddleRenderer(TableSession table) {
            this.table = table;
        }

        @Override
        public CompletionStage<Void> open(TableSnapshot initialState) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> render(TableVisualEvent event) {
            events.add(event.getClass().getSimpleName());
            if (event instanceof TableVisualEvent.PositionRotation rotation
                    && rotation.transfers().stream().anyMatch(transfer
                    -> transfer.position() == TableSnapshot.Position.STRADDLE)) {
                sawStraddlePosition.set(true);
                straddleSequence.compareAndSet(0L, rotation.sequence());
            } else if (event instanceof TableVisualEvent.ActionControls controls
                    && controls.state().callAction()
                    != ActionControlState.CallAction.DISABLED) {
                firstActionSequence.compareAndSet(0L, controls.sequence());
                table.commands().submit(new TableCommand.CheckOrCall());
            } else if (event instanceof TableVisualEvent.HandBoundary boundary
                    && boundary.phase()
                    == TableVisualEvent.HandBoundary.Phase.END) {
                endedHands.incrementAndGet();
            } else if (event instanceof TableVisualEvent.CloseTable close) {
                summary.set(close.summary());
                closed.set(true);
            }
            return CompletableFuture.completedFuture(null);
        }

        void assertComplete() {
            assertEquals(1, endedHands.get(), diagnostic());
            assertTrue(sawStraddlePosition.get(), diagnostic());
            assertTrue(firstActionSequence.get() > straddleSequence.get(),
                    "betting overtook straddle publication: " + diagnostic());
            assertNotNull(summary.get(), diagnostic());
            assertEquals(3, summary.get().balances().size(), diagnostic());
        }

        String diagnostic() {
            return "straddle=" + sawStraddlePosition + "@"
                    + straddleSequence + ", firstAction="
                    + firstActionSequence + ", hands=" + endedHands
                    + ", closed=" + closed + ", events=" + events;
        }

        @Override public void close() { }
    }

    private static final class RebuyRenderer implements TableRenderer {
        private final TableSession table;
        private final AtomicInteger completedHands = new AtomicInteger();
        private final AtomicInteger rebuyEvents = new AtomicInteger();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicReference<TableSessionSummary> summary
                = new AtomicReference<>();
        private final List<String> events
                = new java.util.concurrent.CopyOnWriteArrayList<>();

        RebuyRenderer(TableSession table) {
            this.table = table;
        }

        @Override
        public CompletionStage<Void> open(TableSnapshot initialState) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> render(TableVisualEvent event) {
            events.add(event.getClass().getSimpleName());
            if (event instanceof TableVisualEvent.ActionControls controls) {
                if (controls.state().allInEnabled()) {
                    table.commands().submit(new TableCommand.AllIn());
                } else if (controls.state().callAction()
                        != ActionControlState.CallAction.DISABLED) {
                    table.commands().submit(new TableCommand.CheckOrCall());
                }
            } else if (event instanceof TableVisualEvent.Rebuy) {
                rebuyEvents.incrementAndGet();
            } else if (event instanceof TableVisualEvent.HandBoundary boundary
                    && boundary.phase()
                    == TableVisualEvent.HandBoundary.Phase.END) {
                completedHands.incrementAndGet();
            } else if (event instanceof TableVisualEvent.CloseTable close) {
                summary.set(close.summary());
                closed.set(true);
            }
            return CompletableFuture.completedFuture(null);
        }

        String diagnostic() {
            return "hands=" + completedHands + ", rebuys=" + rebuyEvents
                    + ", closed=" + closed + ", summary=" + summary
                    + ", events=" + events;
        }

        @Override public void close() { }
    }

    private static final class ChildNode implements AutoCloseable {
        private final String name;
        private final Process process;
        private final List<String> output = Collections.synchronizedList(
                new ArrayList<>());
        private final CountDownLatch readerDone = new CountDownLatch(1);

        ChildNode(String name, Process process) {
            this.name = name;
            this.process = process;
            Thread reader = new Thread(() -> {
                try (BufferedReader lines = new BufferedReader(
                        new InputStreamReader(process.getInputStream(),
                                StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = lines.readLine()) != null) {
                        output.add(line);
                        System.out.println("[mixed " + name + "] " + line);
                    }
                } catch (IOException failure) {
                    output.add("reader failure: " + failure);
                } finally {
                    readerDone.countDown();
                }
            }, "mixed-output-" + name);
            reader.setDaemon(true);
            reader.start();
        }

        boolean await(String token, Duration timeout) throws Exception {
            Instant deadline = Instant.now().plus(timeout);
            while (Instant.now().isBefore(deadline)) {
                if (contains(token)) return true;
                if (contains("CP_E2E_FAIL") || contains("TABLE_FAILURE_V1")) {
                    return false;
                }
                if (!process.isAlive()) {
                    readerDone.await(2, TimeUnit.SECONDS);
                    return contains(token);
                }
                Thread.sleep(25L);
            }
            return contains(token);
        }

        boolean contains(String token) {
            synchronized (output) {
                return output.stream().anyMatch(line -> line.contains(token));
            }
        }

        String lastLineContaining(String token) {
            synchronized (output) {
                for (int index = output.size() - 1; index >= 0; index--) {
                    if (output.get(index).contains(token)) return output.get(index);
                }
            }
            return null;
        }

        int intValueAfter(String marker, String field) {
            String line = lastLineContaining(marker);
            if (line == null) return -1;
            int start = line.indexOf(field);
            if (start < 0) return -1;
            start += field.length();
            int end = start;
            while (end < line.length() && Character.isDigit(line.charAt(end))) end++;
            return end == start ? -1 : Integer.parseInt(line.substring(start, end));
        }

        void send(String command) throws IOException {
            process.getOutputStream().write((command + "\n")
                    .getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();
        }

        String diagnostic() {
            synchronized (output) {
                int start = Math.max(0, output.size() - 100);
                return "Node " + name + " alive=" + process.isAlive() + "\n"
                        + String.join("\n", output.subList(start, output.size()));
            }
        }

        @Override
        public void close() {
            try {
                if (process.isAlive()) {
                    try { send("STOP"); } catch (IOException ignored) { }
                }
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(3, TimeUnit.SECONDS);
                }
                readerDone.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
    }
}
