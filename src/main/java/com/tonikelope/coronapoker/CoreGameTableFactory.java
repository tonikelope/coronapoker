/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.core.DatabaseService;
import com.tonikelope.coronapoker.core.LobbyParticipant;
import com.tonikelope.coronapoker.core.LobbySnapshot;
import com.tonikelope.coronapoker.core.game.CoreCardController;
import com.tonikelope.coronapoker.core.game.CoreGameDatabase;
import com.tonikelope.coronapoker.core.game.CoreGameHand;
import com.tonikelope.coronapoker.core.game.CoreGamePot;
import com.tonikelope.coronapoker.core.game.CorePlayerController;
import com.tonikelope.coronapoker.core.game.ClasspathGameCinematicAssets;
import com.tonikelope.coronapoker.core.game.GameAsync;
import com.tonikelope.coronapoker.core.game.GameAudioSink;
import com.tonikelope.coronapoker.core.game.GameBotService;
import com.tonikelope.coronapoker.core.game.GameCinematicAssets;
import com.tonikelope.coronapoker.core.game.GameCinematicSink;
import com.tonikelope.coronapoker.core.game.GameCinematicState;
import com.tonikelope.coronapoker.core.game.GameConfigCodecV1;
import com.tonikelope.coronapoker.core.game.GameDecisionSink;
import com.tonikelope.coronapoker.core.game.GameDialogSink;
import com.tonikelope.coronapoker.core.game.GameIdentityTrust;
import com.tonikelope.coronapoker.core.game.GameLaunchContext;
import com.tonikelope.coronapoker.core.game.GameLogSink;
import com.tonikelope.coronapoker.core.game.GamePeerController;
import com.tonikelope.coronapoker.core.game.GamePresentationSettings;
import com.tonikelope.coronapoker.core.game.GameProgressSink;
import com.tonikelope.coronapoker.core.game.GameRuntimeEnvironment;
import com.tonikelope.coronapoker.core.game.GameSession;
import com.tonikelope.coronapoker.core.game.GameSessionIds;
import com.tonikelope.coronapoker.core.game.GameStateMirror;
import com.tonikelope.coronapoker.core.game.GameTableFactory;
import com.tonikelope.coronapoker.core.game.GameText;
import com.tonikelope.coronapoker.core.game.GameUiExecutor;
import com.tonikelope.coronapoker.core.game.GameValueFormatter;
import com.tonikelope.coronapoker.core.game.GameWindowSink;
import com.tonikelope.coronapoker.core.game.HostGameConfigurationSource;
import com.tonikelope.coronapoker.core.game.LobbyTransitionSink;
import com.tonikelope.coronapoker.core.game.PauseGate;
import com.tonikelope.coronapoker.core.game.RecoveredSettingsSynchronizer;
import com.tonikelope.coronapoker.core.game.TableDisplaySink;
import com.tonikelope.coronapoker.core.network.ConfirmationTracker;
import com.tonikelope.coronapoker.core.network.GameTransport;
import com.tonikelope.coronapoker.table.TableCommand;
import com.tonikelope.coronapoker.table.TableEventBridge;
import com.tonikelope.coronapoker.table.TableSession;
import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Builds the canonical controller for a renderer-neutral GDX table session. */
public final class CoreGameTableFactory implements GameTableFactory {

    private final DatabaseService database;
    private final GameText gameText;
    private final GameLogSink gameLog;
    private final GamePresentationSettings presentationSettings;
    private final GameDialogSink gameDialogs;
    private final GameDecisionSink gameDecisions;

    public CoreGameTableFactory(DatabaseService database) {
        this(database, GameText.keys(), GameLogSink.noop(), GameDialogSink.noop(),
                GameDecisionSink.noop());
    }

    public CoreGameTableFactory(DatabaseService database, GameText gameText) {
        this(database, gameText, GameLogSink.noop(), GameDialogSink.noop(),
                GameDecisionSink.noop());
    }

    public CoreGameTableFactory(DatabaseService database, GameText gameText,
            GameDialogSink gameDialogs) {
        this(database, gameText, GameLogSink.noop(), gameDialogs,
                GameDecisionSink.noop());
    }

    public CoreGameTableFactory(DatabaseService database, GameText gameText,
            GameDialogSink gameDialogs, GameDecisionSink gameDecisions) {
        this(database, gameText, GameLogSink.noop(), gameDialogs,
                gameDecisions);
    }

    public CoreGameTableFactory(DatabaseService database, GameText gameText,
            GameLogSink gameLog, GameDialogSink gameDialogs,
            GameDecisionSink gameDecisions) {
        this(database, gameText, gameLog, gameDialogs, gameDecisions,
                GamePresentationSettings.defaults());
    }

    public CoreGameTableFactory(DatabaseService database, GameText gameText,
            GameLogSink gameLog, GameDialogSink gameDialogs,
            GameDecisionSink gameDecisions,
            GamePresentationSettings presentationSettings) {
        this.database = Objects.requireNonNull(database, "database");
        this.gameText = Objects.requireNonNull(gameText, "gameText");
        this.gameLog = Objects.requireNonNull(gameLog, "gameLog");
        this.presentationSettings = Objects.requireNonNull(
                presentationSettings, "presentationSettings");
        this.gameDialogs = Objects.requireNonNull(gameDialogs, "gameDialogs");
        this.gameDecisions = Objects.requireNonNull(gameDecisions,
                "gameDecisions");
    }

    @Override
    public TableSession create(GameLaunchContext context) throws Exception {
        Objects.requireNonNull(context, "context");
        LobbySnapshot lobby = context.lobby();
        if (!lobby.host()) {
            throw new IllegalStateException(
                    "La mesa GDX cliente se conectara en el siguiente bloque de red");
        }
        List<LobbyParticipant> unsupported = lobby.participants().stream()
                .filter(participant -> !participant.local() && !participant.bot())
                .toList();
        if (!unsupported.isEmpty()) {
            throw new IllegalStateException(
                    "La mesa GDX con jugadores remotos aun no esta conectada");
        }
        if (lobby.participants().size() < 2) {
            throw new IllegalStateException(
                    "La timba necesita al menos dos participantes");
        }

        GameConfigCodecV1.Configuration initialConfiguration
                = GameConfigCodecV1.fromSettings(lobby.tableSettings(),
                        lobby.recovering(), GameSessionIds.random());
        GameSession game = new GameSession(lobby.localNickname(), true,
                initialConfiguration);
        TableEventBridge events = new TableEventBridge();
        ArrayList<CorePlayerController> players = createPlayers(lobby,
                initialConfiguration.buyin());
        CorePlayerController local = players.get(0);
        Map<String, GamePeerController> peers = createPeers(lobby);
        CoreCardController[] community = new CoreCardController[5];
        for (int slot = 0; slot < community.length; slot++) {
            community[slot] = new CoreCardController();
        }

        SessionPauseGate pause = new SessionPauseGate(game);
        ChannelGameTransport transport = new ChannelGameTransport(context);
        AtomicBoolean windowOpen = new AtomicBoolean(true);
        AtomicReference<TableSession> tableReference = new AtomicReference<>();
        ExecutorService dealerExecutor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "CoronaPoker-GDX-dealer");
            thread.setDaemon(true);
            return thread;
        });

        HostGameConfigurationSource hostConfiguration = sessionId
                -> GameConfigCodecV1.fromSettings(lobby.tableSettings(),
                        lobby.recovering(), sessionId);
        GameText text = gameText;
        GameWindowSink window = gameWindow(game, windowOpen);
        GameCinematicAssets cinematicAssets =
                new ClasspathGameCinematicAssets("cinematics/allin");
        GameCinematicSink cinematics = request -> {
            if (request.type() != GameCinematicSink.Type.ALL_IN
                    || !events.isAttached()) {
                return CompletableFuture.completedFuture(
                        new GameCinematicSink.Result(false, false));
            }
            TableVisualEvent.Cinematic.Type type =
                    TableVisualEvent.Cinematic.Type.ALL_IN;
            return events.publish(sequence -> new TableVisualEvent.Cinematic(
                    sequence, type, TableVisualEvent.Cinematic.Phase.START,
                    request.assetName(), request.durationMillis()))
                    .thenCompose(ignored -> events.publish(sequence ->
                    new TableVisualEvent.Cinematic(sequence, type,
                            TableVisualEvent.Cinematic.Phase.FINISH,
                            request.assetName(), request.durationMillis())))
                    .thenApply(ignored ->
                    new GameCinematicSink.Result(true, false));
        };
        Crupier dealer = new Crupier(game, players, local, peers, community,
                context.identity(), gameLog, gameDialogs,
                gameDecisions, new CoreGameDatabase(database),
                hostConfiguration, GameStateMirror.noop(),
                RecoveredSettingsSynchronizer.noop(), cinematics,
                GameProgressSink.noop(), pause, transport,
                LobbyTransitionSink.noop(), TableDisplaySink.noop(), window,
                GameUiExecutor.direct(), GameAudioSink.silent(),
                GameAsync.standalone(), presentationSettings,
                GameIdentityTrust.unverified(), text,
                cards -> new CoreGameHand(cards, text), CoreGamePot::new,
                GameRuntimeEnvironment.defaults(), GameCinematicState.idle(),
                cinematicAssets, GameValueFormatter.plain(),
                GameBotService.standalone(), events);

        players.forEach(player -> player.bindPotRegistration(
                () -> dealer.getGamePot().addPlayerController(player)));
        local.bindAcceptedLocalAllInSignal(() -> {
            if (!dealer.localCinematicAllin()) {
                dealer.soundAllin();
            }
        });
        local.bindTurnCompletionSignal(() -> notifyBettingWait(dealer));
        AtomicBoolean started = new AtomicBoolean();
        AtomicBoolean closing = new AtomicBoolean();
        AutoCloseable inbound = context.channel().subscribe(command ->
                dealer.enqueueReceivedCommand(transport.inboundEnvelope(command),
                        context.channel()::close));

        TableSession table = new TableSession(initialSnapshot(lobby,
                initialConfiguration.buyin()), command -> submit(command, dealer,
                        local, game, pause), events, () -> {
                    if (!started.compareAndSet(false, true)) {
                        return CompletableFuture.failedFuture(
                                new IllegalStateException("La mesa GDX ya esta iniciada"));
                    }
                    game.start();
                    dealerExecutor.execute(() -> {
                        try {
                            dealer.run();
                        } finally {
                            game.finish();
                            events.publish(TableVisualEvent.CloseTable::new)
                                    .whenComplete((ignored, failure) -> {
                                        TableSession active = tableReference.get();
                                        if (active != null) active.close();
                                    });
                        }
                    });
                    return CompletableFuture.completedFuture(null);
                }, () -> {
                    if (!closing.compareAndSet(false, true)) return;
                    windowOpen.set(false);
                    dealer.setFin_de_la_transmision(true);
                    local.setExit();
                    pause.resume();
                    notifyBettingWait(dealer);
                    inbound.close();
                    context.channel().close();
                    dealerExecutor.shutdownNow();
                    game.close();
                });
        tableReference.set(table);
        return table;
    }

    private static ArrayList<CorePlayerController> createPlayers(
            LobbySnapshot lobby, int buyin) {
        ArrayList<CorePlayerController> players = new ArrayList<>();
        CorePlayerController local = CorePlayerController.local(
                lobby.localNickname());
        initializePlayer(local, buyin);
        players.add(local);
        int placeholder = 1;
        for (LobbyParticipant participant : lobby.participants()) {
            if (participant.local()) continue;
            CorePlayerController player = CorePlayerController.remote(
                    "GdxSeat" + placeholder++);
            initializePlayer(player, buyin);
            players.add(player);
        }
        return players;
    }

    private static void initializePlayer(CorePlayerController player, int buyin) {
        player.setStack(buyin);
        player.setBuyin(buyin);
    }

    private static Map<String, GamePeerController> createPeers(
            LobbySnapshot lobby) {
        Map<String, GamePeerController> peers = new LinkedHashMap<>();
        for (LobbyParticipant participant : lobby.participants()) {
            peers.put(participant.nickname(), participant.local() ? null
                    : GamePeerController.recoveryBot(participant.nickname()));
        }
        return peers;
    }

    private static TableSnapshot initialSnapshot(LobbySnapshot lobby, int buyin) {
        List<TableSnapshot.PlayerSnapshot> players = lobby.participants().stream()
                .map(participant -> new TableSnapshot.PlayerSnapshot(
                participant.nickname(), buyin, 0d, 0d, true, false, false,
                false, false, TableSnapshot.Position.NONE, "", "",
                List.of()))
                .toList();
        return new TableSnapshot(0L, lobby.localNickname(),
                TableSnapshot.Street.WAITING, 0d, "", false, players,
                List.of());
    }

    private static void submit(TableCommand command, Crupier dealer,
            CorePlayerController local, GameSession game,
            SessionPauseGate pause) {
        Objects.requireNonNull(command, "command");
        if (command instanceof TableCommand.Fold) {
            local.submitDecision(CorePlayerController.FOLD, 0d);
        } else if (command instanceof TableCommand.CheckOrCall) {
            local.submitDecision(CorePlayerController.CHECK, 0d);
        } else if (command instanceof TableCommand.Bet bet) {
            local.submitDecision(CorePlayerController.BET, bet.amount());
        } else if (command instanceof TableCommand.AllIn) {
            local.submitDecision(CorePlayerController.ALLIN, 0d);
        } else if (command instanceof TableCommand.ShowCards) {
            dealer.showAndBroadcastPlayerCards(local.getNickname());
        } else if (command instanceof TableCommand.ExitGame) {
            local.setExit();
            dealer.setFin_de_la_transmision(true);
            pause.resume();
            notifyBettingWait(dealer);
        } else if (command instanceof TableCommand.TogglePause) {
            game.setPaused(!game.isPaused());
            pause.resume();
        }
    }

    private static void notifyBettingWait(Crupier dealer) {
        synchronized (dealer.getLock_apuestas()) {
            dealer.getLock_apuestas().notifyAll();
        }
    }

    private static GameWindowSink gameWindow(GameSession game,
            AtomicBoolean open) {
        return new GameWindowSink() {
            @Override public boolean isOpen() { return open.get(); }
            @Override public void requestExit() { open.set(false); }
            @Override public void setExitEnabled(boolean enabled) { }
            @Override public void setFullscreenEnabled(boolean enabled) { }
            @Override public void resetImmediateRebuy() { }
            @Override public void setGameOverDialogOpen(boolean value) { }
            @Override public void applyAutomaticFullscreen(boolean enabled) { }
            @Override public void stopGameClock() { }
            @Override public void finishTransmission(boolean ended) {
                open.set(false);
                game.finish();
            }
        };
    }

    private static final class SessionPauseGate implements PauseGate {
        private final GameSession game;
        private final Object monitor = new Object();

        SessionPauseGate(GameSession game) {
            this.game = game;
        }

        @Override
        public boolean await() {
            boolean waited = false;
            synchronized (monitor) {
                while (game.isPaused()
                        && game.phase() != GameSession.Phase.CLOSED) {
                    waited = true;
                    try {
                        monitor.wait(250L);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return waited;
                    }
                }
            }
            return waited;
        }

        void resume() {
            synchronized (monitor) {
                monitor.notifyAll();
            }
        }
    }

    private static final class ChannelGameTransport implements GameTransport {
        private final GameLaunchContext context;
        private final ConfirmationTracker confirmations = new ConfirmationTracker();
        private final AtomicInteger inboundIds = new AtomicInteger();

        ChannelGameTransport(GameLaunchContext context) {
            this.context = context;
        }

        String inboundEnvelope(com.tonikelope.coronapoker.core.game.GameChannel.Inbound inbound) {
            return "GAME#" + inboundIds.incrementAndGet() + "#" + inbound.command();
        }

        @Override public String hostNickname() {
            return context.lobby().serverNickname();
        }

        @Override public String tablePassword() { return null; }
        @Override public boolean gameStarted() { return true; }
        @Override public ConfirmationTracker confirmations() { return confirmations; }

        @Override
        public void sendCommandToHost(String clearText) {
            String[] parts = clearText.split("#", 3);
            if (parts.length != 3 || !"GAME".equals(parts[0])) {
                throw new IllegalArgumentException("Invalid dealer GAME envelope");
            }
            int confirmationId = Integer.parseInt(parts[1]) + 1;
            try {
                context.channel().sendToHost(parts[2]).whenComplete((ignored, failure) -> {
                    if (failure == null) {
                        confirmations.confirm(hostNickname(), confirmationId);
                    } else {
                        context.channel().close();
                    }
                });
            } catch (java.io.IOException failure) {
                throw new IllegalStateException("Cannot send game command", failure);
            }
        }

        @Override public void closeHostConnection() {
            context.channel().close();
        }
    }
}
