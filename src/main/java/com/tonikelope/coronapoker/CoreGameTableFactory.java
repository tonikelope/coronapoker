/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.core.DatabaseService;
import com.tonikelope.coronapoker.core.LobbyParticipant;
import com.tonikelope.coronapoker.core.LobbySnapshot;
import com.tonikelope.coronapoker.core.RecoverableGameRepository;
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
import com.tonikelope.coronapoker.core.game.GameChannelPeerController;
import com.tonikelope.coronapoker.core.game.GameCancellationException;
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
import com.tonikelope.coronapoker.core.game.GameSessionClock;
import com.tonikelope.coronapoker.core.game.GameSessionIds;
import com.tonikelope.coronapoker.core.game.GameStateMirror;
import com.tonikelope.coronapoker.core.game.GameTableFactory;
import com.tonikelope.coronapoker.core.game.GameText;
import com.tonikelope.coronapoker.core.game.GameUiExecutor;
import com.tonikelope.coronapoker.core.game.GameValueFormatter;
import com.tonikelope.coronapoker.core.game.GameWindowSink;
import com.tonikelope.coronapoker.core.game.HostGameConfigurationSource;
import com.tonikelope.coronapoker.core.game.LobbyTransitionSink;
import com.tonikelope.coronapoker.core.game.MoneyMath;
import com.tonikelope.coronapoker.core.game.PauseGate;
import com.tonikelope.coronapoker.core.game.RecoveredSettingsSynchronizer;
import com.tonikelope.coronapoker.core.game.TableDisplaySink;
import com.tonikelope.coronapoker.core.game.TableEventGameAudioSink;
import com.tonikelope.coronapoker.core.network.ConfirmationTracker;
import com.tonikelope.coronapoker.core.network.GameTransport;
import com.tonikelope.coronapoker.table.TableCommand;
import com.tonikelope.coronapoker.table.TableEventBridge;
import com.tonikelope.coronapoker.table.TableSession;
import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableSnapshotMapper;
import com.tonikelope.coronapoker.table.TableSessionSummary;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Builds the canonical controller for a renderer-neutral GDX table session. */
public final class CoreGameTableFactory implements GameTableFactory {

    private static final long TABLE_EXECUTOR_CLOSE_TIMEOUT_SECONDS = 5L;
    private static final long RECOVERY_SHUFFLE_PROOF_DRAIN_TIMEOUT_MS = 10_000L;

    private static final Set<String> RENDERER_OWNED_AUDIO = Set.of(
            "misc/shuffle.wav", "misc/deal.wav", "misc/uncover.wav",
            "misc/check.wav", "misc/call.wav", "misc/bet.wav",
            "misc/fold.wav", "misc/allin.wav");

    private final DatabaseService database;
    private final GameText gameText;
    private final GameLogSink gameLog;
    private final GamePresentationSettings presentationSettings;
    private final GameDialogSink gameDialogs;
    private final GameDecisionSink gameDecisions;
    private final GameCinematicAssets cinematicAssets;
    private final boolean modActive;
    private final Properties preferences;

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
        this(database, gameText, gameLog, gameDialogs, gameDecisions,
                presentationSettings,
                new ClasspathGameCinematicAssets("cinematics/allin"));
    }

    public CoreGameTableFactory(DatabaseService database, GameText gameText,
            GameLogSink gameLog, GameDialogSink gameDialogs,
            GameDecisionSink gameDecisions,
            GamePresentationSettings presentationSettings,
            GameCinematicAssets cinematicAssets) {
        this(database, gameText, gameLog, gameDialogs, gameDecisions,
                presentationSettings, cinematicAssets, false);
    }

    public CoreGameTableFactory(DatabaseService database, GameText gameText,
            GameLogSink gameLog, GameDialogSink gameDialogs,
            GameDecisionSink gameDecisions,
            GamePresentationSettings presentationSettings,
            GameCinematicAssets cinematicAssets, boolean modActive) {
        this(database, gameText, gameLog, gameDialogs, gameDecisions,
                presentationSettings, cinematicAssets, modActive,
                new Properties());
    }

    public CoreGameTableFactory(DatabaseService database, GameText gameText,
            GameLogSink gameLog, GameDialogSink gameDialogs,
            GameDecisionSink gameDecisions,
            GamePresentationSettings presentationSettings,
            GameCinematicAssets cinematicAssets, boolean modActive,
            Properties preferences) {
        this.database = Objects.requireNonNull(database, "database");
        this.gameText = Objects.requireNonNull(gameText, "gameText");
        this.gameLog = Objects.requireNonNull(gameLog, "gameLog");
        this.presentationSettings = Objects.requireNonNull(
                presentationSettings, "presentationSettings");
        this.gameDialogs = Objects.requireNonNull(gameDialogs, "gameDialogs");
        this.gameDecisions = Objects.requireNonNull(gameDecisions,
                "gameDecisions");
        this.cinematicAssets = Objects.requireNonNull(cinematicAssets,
                "cinematicAssets");
        this.modActive = modActive;
        this.preferences = Objects.requireNonNull(preferences, "preferences");
    }

    @Override
    public TableSession create(GameLaunchContext context) throws Exception {
        Objects.requireNonNull(context, "context");
        LobbySnapshot lobby = context.lobby();
        if (lobby.participants().size() < 2) {
            throw new IllegalStateException(
                    "La timba necesita al menos dos participantes");
        }

        GameConfigCodecV1.Configuration initialConfiguration
                = context.initialConfiguration() == null
                        ? GameConfigCodecV1.fromSettings(lobby.tableSettings(),
                                lobby.recovering(), context.sessionId())
                        : context.initialConfiguration();
        AtomicReference<com.tonikelope.coronapoker.core.NewGameTableDraft.BotDifficulty>
                botDifficulty = new AtomicReference<>(
                        lobby.tableSettings().botDifficulty());
        AtomicBoolean textToSpeech = new AtomicBoolean(booleanPreference(
                preferences, "tts_server", true));
        AtomicBoolean voiceMessages = new AtomicBoolean(booleanPreference(
                preferences, "voice_messages", true));
        if (lobby.host()) {
            Bot.DIFFICULTY = botDifficulty(botDifficulty.get());
        }
        GameSession game = new GameSession(lobby.localNickname(), lobby.host(),
                initialConfiguration);
        TableEventBridge events = new TableEventBridge();
        ArrayList<CorePlayerController> players = createPlayers(lobby,
                initialConfiguration.buyin());
        CorePlayerController local = players.get(0);
        ChannelGameTransport transport = new ChannelGameTransport(context);
        Map<String, GamePeerController> peers = createPeers(lobby,
                context.channel(), transport.confirmations());
        CoreCardController[] community = new CoreCardController[5];
        for (int slot = 0; slot < community.length; slot++) {
            community[slot] = new CoreCardController();
        }

        SessionPauseGate pause = new SessionPauseGate(game);
        NetworkPauseCoordinator pauseCoordinator = new NetworkPauseCoordinator(
                context, game, pause, events);
        AtomicBoolean windowOpen = new AtomicBoolean(true);
        AtomicReference<TableSession> tableReference = new AtomicReference<>();
        AtomicReference<Thread> dealerWorker = new AtomicReference<>();
        AtomicReference<Thread> controlWorker = new AtomicReference<>();
        AtomicReference<Thread> terminationWorker = new AtomicReference<>();
        ExecutorService dealerExecutor = ownedSingleThreadExecutor(
                "CoronaPoker-GDX-dealer", dealerWorker);
        ExecutorService controlExecutor = ownedSingleThreadExecutor(
                "CoronaPoker-GDX-control", controlWorker);
        ExecutorService terminationExecutor = ownedSingleThreadExecutor(
                "CoronaPoker-GDX-termination", terminationWorker);
        GameAsync.OwnedAsync gameAsync = GameAsync.owned(
                "CoronaPoker-GDX-game-task");

        HostGameConfigurationSource hostConfiguration = sessionId
                -> GameConfigCodecV1.fromSettings(lobby.tableSettings(),
                        lobby.recovering(), sessionId);
        GameText text = gameText;
        GameWindowSink window = gameWindow(game, windowOpen,
                dealerExecutor::shutdownNow);
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
                    request.nickname(), request.assetName(),
                    request.durationMillis()))
                    .thenCompose(ignored -> events.publish(sequence ->
                    new TableVisualEvent.Cinematic(sequence, type,
                            TableVisualEvent.Cinematic.Phase.FINISH,
                            request.nickname(), request.assetName(),
                            request.durationMillis())))
                    .thenApply(ignored ->
                    new GameCinematicSink.Result(true, false));
        };
        GameCinematicState cinematicState = GameCinematicState.coordinated();
        GameProgressSink progress = new GameProgressSink() {
            private void publish(TableVisualEvent.SharedProgress.Mode mode,
                    int seconds) {
                events.publish(sequence -> new TableVisualEvent.SharedProgress(
                        sequence, mode, Math.max(0, seconds)));
            }

            @Override
            public void countdown(int seconds) {
                publish(TableVisualEvent.SharedProgress.Mode.COUNTDOWN,
                        seconds);
            }

            @Override
            public void indeterminate() {
                publish(TableVisualEvent.SharedProgress.Mode.INDETERMINATE, 0);
            }

            @Override
            public void reset(int seconds) {
                publish(TableVisualEvent.SharedProgress.Mode.RESET, seconds);
            }

            @Override
            public void setIndeterminate(boolean enabled) {
                publish(enabled
                        ? TableVisualEvent.SharedProgress.Mode.INDETERMINATE
                        : TableVisualEvent.SharedProgress.Mode.RESET, 0);
            }
        };
        LobbyTransitionSink lobbyTransition = new LobbyTransitionSink() {
            @Override
            public void removeParticipant(String nickname) {
                // The network lobby owns its roster until the table transition
                // is complete. Dealer-side removals are already reflected by
                // the canonical seat roster published below.
            }

            @Override
            public void seatingPlayers() {
                events.publish(sequence -> new TableVisualEvent.PreparationStatus(
                        sequence,
                        TableVisualEvent.PreparationStatus.Phase.DRAWING_SEATS));
            }

            @Override
            public void hideLobby() {
                // GDX retains the preparation shield until gameStarted().
            }

            @Override
            public void gameStarted() {
                events.publish(sequence -> new TableVisualEvent.PreparationStatus(
                        sequence,
                        TableVisualEvent.PreparationStatus.Phase.READY));
            }
        };
        Crupier dealer = new Crupier(game, players, local, peers, community,
                context.identity(), gameLog, gameDialogs,
                gameDecisions, new CoreGameDatabase(database,
                        context.recoveryGameId(), () ->
                        RecoverableGameRepository.encodeSettings(
                                game.configuration(),
                                botDifficulty.get(),
                                voiceMessages.get(), textToSpeech.get())),
                hostConfiguration, GameStateMirror.noop(),
                RecoveredSettingsSynchronizer.noop(), cinematics,
                progress, pause, transport,
                lobbyTransition, TableDisplaySink.noop(), window,
                GameUiExecutor.direct(), new TableEventGameAudioSink(events,
                        RENDERER_OWNED_AUDIO),
                gameAsync, presentationSettings,
                GameIdentityTrust.unverified(), text,
                cards -> new CoreGameHand(cards, text), CoreGamePot::new,
                GameRuntimeEnvironment.at(context.dataDirectory(), modActive),
                cinematicState,
                cinematicAssets, GameValueFormatter.plain(),
                GameBotService.standalone(), events);
        dealer.initializeCommunicationRules(textToSpeech.get(),
                voiceMessages.get());

        players.forEach(player -> player.bindPotRegistration(
                () -> dealer.getGamePot().addPlayerController(player)));
        players.forEach(player -> player.bindCommittedRebuy(
                () -> dealer.consumeCommittedRebuy(
                        player.getNickname(), player.getStack())));
        local.bindAcceptedLocalAllInSignal(() -> {
            if (!dealer.localCinematicAllin()) {
                dealer.soundAllin();
            }
        });
        local.bindTurnCompletionSignal(() -> notifyBettingWait(dealer));
        AtomicBoolean started = new AtomicBoolean();
        AtomicBoolean closing = new AtomicBoolean();
        AtomicBoolean recoveryProofDrainAttempted = new AtomicBoolean();
        AtomicLong immediateRebuyRequestSequence = new AtomicLong();
        AtomicLong immediateRebuyRelaySequence = new AtomicLong();
        Map<String, Long> immediateRebuySources = new LinkedHashMap<>();
        long immediateRebuySource = 0L;
        for (Map.Entry<String, GamePeerController> peer : peers.entrySet()) {
            if (peer.getValue() != null && !peer.getValue().isCpu()) {
                immediateRebuySources.put(peer.getKey(), ++immediateRebuySource);
            }
        }
        AtomicReference<TableSessionSummary.CloseReason> requestedCloseReason
                = new AtomicReference<>(
                        TableSessionSummary.CloseReason.COMPLETED);
        AutoCloseable inbound = context.channel().subscribe(command -> {
            String envelope = transport.inboundEnvelope(command);
            if (!lobby.host()
                    && (command.command().equals("SERVEREXIT")
                    || command.command().startsWith("SERVEREXITRECOVER"))) {
                dealer.acceptAuthoritativeTableExit(command.command());
                pauseCoordinator.resumeForShutdown();
                notifyBettingWait(dealer);
                return;
            }
            if (lobby.host() && command.command().startsWith("EXIT#")) {
                PlayerExitWire.Command playerExit;
                try {
                    playerExit = PlayerExitWire.parseClientRequest(
                            envelope.split("#", -1), command.peerNickname());
                    GamePeerController exitingPeer = peers.get(playerExit.nick());
                    if (exitingPeer == null || exitingPeer.isCpu()) {
                        throw new IllegalArgumentException(
                                "EXIT source is not a current remote human");
                    }
                } catch (RuntimeException invalid) {
                    context.channel().close();
                    return;
                }
                controlExecutor.execute(() -> {
                    try {
                        GamePeerController exitingPeer = peers.get(playerExit.nick());
                        if (dealer.requiresExitPocketProof(playerExit.nick())
                                && !playerExit.hasPocketReveal()) {
                            throw new IllegalArgumentException(
                                    "all-in EXIT requires a signed pocket reveal");
                        }
                        if (playerExit.hasPocketReveal()
                                && !dealer.acceptExitShowdownProof(
                                        playerExit.nick(),
                                        playerExit.pocketKeyWire(),
                                        playerExit.pocketSignatureWire())) {
                            throw new IllegalArgumentException(
                                    "EXIT carries an invalid showdown proof");
                        }
                        if (playerExit.hasTestament()) {
                            exitingPeer.setSra_unlock_community(
                                    playerExit.testament());
                        }
                        dealer.remotePlayerQuit(playerExit.nick(),
                                playerExit.testamentWire(),
                                playerExit.pocketKeyWire(),
                                playerExit.pocketSignatureWire());
                        notifyBettingWait(dealer);
                    } catch (RuntimeException invalid) {
                        context.channel().close();
                    }
                });
                return;
            }
            if (!lobby.host() && command.command().startsWith("EXIT#")) {
                // EXIT is an ordering-critical state transition, just as it is
                // in WaitingRoomFrame's Swing socket reader.  It must be
                // validated and applied on the ordered GameChannel drain before
                // a following street-unlock request is dispatched.  Feeding it
                // into the dealer mailbox lets that mailbox remain blocked on a
                // departed seat while the channel acknowledges later frames;
                // the host can then enter FLOP while this client is still in
                // PREFLOP and the early-unlock defence correctly refuses it.
                try {
                    PlayerExitWire.Command playerExit = PlayerExitWire
                            .parseHostRelay(envelope.split("#", -1));
                    GamePeerController exitingPeer = peers.get(playerExit.nick());
                    if (exitingPeer == null) {
                        throw new IllegalArgumentException(
                                "EXIT target is not a current participant");
                    }
                    if (playerExit.hasTestament()) {
                        exitingPeer.setSra_unlock_community(
                                playerExit.testament());
                    }
                    dealer.remotePlayerQuit(playerExit.nick(),
                            playerExit.testamentWire(),
                            playerExit.pocketKeyWire(),
                            playerExit.pocketSignatureWire());
                    notifyBettingWait(dealer);
                } catch (RuntimeException invalid) {
                    context.channel().close();
                }
                return;
            }
            if (pauseCoordinator.accept(command, envelope)) {
                return;
            }
            if (lobby.host()
                    && command.command().startsWith("REBUYNOW#")) {
                try {
                    GamePeerController requestingPeer = peers.get(
                            command.peerNickname());
                    Long source = immediateRebuySources.get(
                            command.peerNickname());
                    if (requestingPeer == null || requestingPeer.isCpu()
                            || requestingPeer.isExit() || source == null) {
                        throw new IllegalArgumentException(
                                "REBUYNOW source is not an active remote human");
                    }
                    int amount = ImmediateRebuyWire.parseClientRequest(
                            envelope.split("#", -1));
                    long arrival = immediateRebuyRequestSequence
                            .incrementAndGet();
                    if (gameAsync.execute(() -> dealer.rebuyNowFromClient(
                            command.peerNickname(), amount, source, arrival))
                            .isCancelled()) {
                        throw new IllegalStateException(
                                "REBUYNOW worker rejected");
                    }
                } catch (RuntimeException invalid) {
                    context.channel().close();
                }
                return;
            }
            if (!lobby.host()
                    && (command.command().startsWith("REBUYNOW#")
                    || command.command().startsWith("REBUYDENIED#"))) {
                long arrival = immediateRebuyRelaySequence.incrementAndGet();
                try {
                    ImmediateRebuyWire.Relay relay
                            = ImmediateRebuyWire.parseHostRelay(
                                    envelope.split("#", -1));
                    if (!peers.containsKey(relay.nick())) {
                        throw new IllegalArgumentException(
                                "immediate-rebuy relay target is not seated");
                    }
                    dealer.registerRemoteRebuyRelay(arrival);
                    int amount = relay.denied() ? 0 : relay.amount();
                    if (gameAsync.execute(() -> dealer.applyRemoteRebuyNow(
                            relay.nick(), amount, arrival)).isCancelled()) {
                        dealer.cancelRemoteRebuyRelay(arrival);
                        throw new IllegalStateException(
                                "immediate-rebuy relay worker rejected");
                    }
                } catch (RuntimeException invalid) {
                    dealer.cancelRemoteRebuyRelay(arrival);
                    context.channel().close();
                }
                return;
            }
            if (!lobby.host()
                    && command.command().startsWith("START_SRA_CASCADE#")) {
                long rebuyBoundary = immediateRebuyRelaySequence.get();
                synchronized (dealer.getReceived_commands()) {
                    if (dealer.enqueueRemoteRebuyBarrier(rebuyBoundary,
                            context.channel()::close)) {
                        dealer.enqueueReceivedCommand(envelope,
                                context.channel()::close);
                    }
                    dealer.getReceived_commands().notifyAll();
                }
                return;
            }
            if (lobby.host() && command.command().startsWith("HAND_READY#")) {
                try {
                    dealer.acceptRemoteHandReady(command.peerNickname(), envelope);
                } catch (RuntimeException invalid) {
                    context.channel().close();
                }
                return;
            }
            if (!lobby.host()
                    && dealer.handleClientCriticalHostCommand(command.command())) {
                return;
            }
            dealer.enqueueReceivedCommand(envelope, context.channel()::close);
        });
        AutoCloseable peerLoss = context.channel().subscribePeerLoss(nickname -> {
            if (!lobby.host() || closing.get()) return;
            GamePeerController peer = peers.get(nickname);
            if (peer == null || peer.isCpu()) return;
            // The watchdog runs outside the dealer thread. It may only publish
            // the definitive loss and wake waits; refunding here can race an
            // accepted action while its bet is still being committed. The
            // dealer consumes this signal at the next safe betting boundary.
            dealer.requestDefinitivePeerLossAbort(nickname);
            notifyBettingWait(dealer);
        });

                TableSession table = new TableSession(initialSnapshot(lobby,
                initialConfiguration.buyin()), command -> {
                    submit(command, dealer, local, pauseCoordinator, events,
                            presentationSettings, context.channel(),
                            lobby.host(), controlExecutor,
                            terminationExecutor, requestedCloseReason,
                            botDifficulty, textToSpeech, voiceMessages,
                            closing);
                }, events, () -> {
                    if (!started.compareAndSet(false, true)) {
                        return CompletableFuture.failedFuture(
                                new IllegalStateException("La mesa GDX ya esta iniciada"));
                    }
                    events.publish(sequence -> new TableVisualEvent.PreparationStatus(
                            sequence,
                            TableVisualEvent.PreparationStatus.Phase.STARTING_DEALER));
                    game.start();
                    GameSessionClock.start(game, gameAsync, windowOpen::get,
                            seconds -> events.publish(sequence
                                    -> new TableVisualEvent.GameClock(
                                            sequence, seconds)));
                    dealerExecutor.execute(() -> {
                        try {
                            dealer.run();
                        } catch (GameCancellationException cancelled) {
                            // Expected control flow when the renderer-neutral
                            // window closes while the dealer is in a cancellable
                            // presentation/bot delay.
                        } finally {
                            // A recoverable CloseTable event is the public hand-off
                            // point used by the lobby/scenario to reopen the table.
                            // A recoverable stop gets a bounded durability drain before
                            // consumers can reopen the table. A final EXIT may persist a
                            // verdict that is already available, but must never hold the
                            // closing UI for the recovery timeout: an unfinished proof
                            // remains unverified and therefore unusable for recovery.
                            TableSessionSummary.CloseReason closeReason
                                    = requestedCloseReason.get();
                            if ((dealer.isForce_recover()
                                    || closeReason
                                    == TableSessionSummary.CloseReason.RECOVERABLE_STOP
                                    || closeReason
                                    == TableSessionSummary.CloseReason.EXITED)
                                    && recoveryProofDrainAttempted
                                            .compareAndSet(false, true)) {
                                dealer.awaitCurrentShuffleProofForRecoverableShutdown(
                                        closeReason
                                        == TableSessionSummary.CloseReason.EXITED
                                                ? 0L
                                                : RECOVERY_SHUFFLE_PROOF_DRAIN_TIMEOUT_MS);
                            }
                            TableSessionSummary summary = tableSummary(
                                    dealer, game, players, local,
                                    requestedCloseReason.get());
                            game.finish();
                            events.publish(sequence
                                    -> new TableVisualEvent.CloseTable(
                                            sequence, summary,
                                            TableSnapshot.Street.FINISHED))
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
                    TableSessionSummary.CloseReason closeReason
                            = requestedCloseReason.get();
                    if (dealer.isForce_recover()
                            || closeReason
                            == TableSessionSummary.CloseReason.RECOVERABLE_STOP
                            || closeReason
                            == TableSessionSummary.CloseReason.EXITED) {
                        if (recoveryProofDrainAttempted.compareAndSet(false, true)) {
                            dealer.awaitCurrentShuffleProofForRecoverableShutdown(
                                    closeReason
                                    == TableSessionSummary.CloseReason.EXITED
                                            ? 0L
                                            : RECOVERY_SHUFFLE_PROOF_DRAIN_TIMEOUT_MS);
                        }
                    }
                    dealer.setFin_de_la_transmision(true);
                    local.setExit();
                    pause.resume();
                    notifyBettingWait(dealer);
                    peerLoss.close();
                    inbound.close();
                    context.channel().close();
                    dealer.shutdownShuffleVerifyQueue();
                    gameAsync.close();
                    shutdownAndAwait(controlExecutor, controlWorker);
                    shutdownAndAwait(terminationExecutor, terminationWorker);
                    shutdownAndAwait(dealerExecutor, dealerWorker);
                    game.close();
                });
        tableReference.set(table);
        return table;
    }

    private static ExecutorService ownedSingleThreadExecutor(String name,
            AtomicReference<Thread> workerReference) {
        return Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(() -> {
                Thread worker = Thread.currentThread();
                workerReference.set(worker);
                try {
                    task.run();
                } finally {
                    workerReference.compareAndSet(worker, null);
                }
            }, name);
            thread.setDaemon(true);
            return thread;
        });
    }

    private static void shutdownAndAwait(ExecutorService executor,
            AtomicReference<Thread> workerReference) {
        executor.shutdownNow();
        if (workerReference.get() == Thread.currentThread()) return;
        boolean interrupted = false;
        try {
            executor.awaitTermination(TABLE_EXECUTOR_CLOSE_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS);
        } catch (InterruptedException cancellation) {
            interrupted = true;
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static TableSessionSummary tableSummary(Crupier dealer,
            GameSession game, List<CorePlayerController> players,
            CorePlayerController local,
            TableSessionSummary.CloseReason requestedCloseReason) {
        long endedAt = System.currentTimeMillis();
        long elapsed = game.startTimestampMillis() > 0L
                ? Math.max(0L,
                        (endedAt - game.startTimestampMillis()) / 1_000L)
                : 0L;
        long duration = Math.max(game.playTimeSeconds(), elapsed);
        TableSessionSummary.CloseReason reason;
        if (dealer.getTableFailure() != null) {
            reason = TableSessionSummary.CloseReason.FAILURE;
        } else if (dealer.isForce_recover()
                || requestedCloseReason
                == TableSessionSummary.CloseReason.RECOVERABLE_STOP) {
            reason = TableSessionSummary.CloseReason.RECOVERABLE_STOP;
        } else if (requestedCloseReason
                == TableSessionSummary.CloseReason.EXITED) {
            reason = requestedCloseReason;
        } else {
            reason = TableSessionSummary.CloseReason.COMPLETED;
        }

        Map<String, Double[]> audited = new LinkedHashMap<>(
                dealer.getAuditor());
        Map<String, TableSessionSummary.PlayerBalance> balances
                = new LinkedHashMap<>();
        for (CorePlayerController player : players) {
            // The dealer's auditor is refreshed at hand boundaries, before
            // settlement has necessarily moved the winning amount into pagar.
            // At table close the live controller is therefore authoritative
            // for every seat still owned by this session. Preferring the stale
            // auditor here made the GDX final screen report the opening 10/10
            // stacks after a real 20/0 all-in payout. Keep auditor-only entries
            // below solely for players no longer present in the live roster.
            audited.remove(player.getNickname());
            double stack = player.getStack() + player.getPagar();
            double buyin = player.getBuyin();
            balances.put(player.getNickname(), new TableSessionSummary.PlayerBalance(
                    player.getNickname(), cleanMoney(stack), cleanMoney(buyin),
                    dealer.getRebuyCount(player.getNickname())));
        }
        audited.forEach((nickname, account) -> {
            if (account == null || account.length < 2
                    || account[0] == null || account[1] == null) {
                return;
            }
            balances.putIfAbsent(nickname,
                    new TableSessionSummary.PlayerBalance(nickname,
                            cleanMoney(account[0]), cleanMoney(account[1]),
                            dealer.getRebuyCount(nickname)));
        });
        return new TableSessionSummary(game.localNickname(), dealer.getMano(),
                duration, endedAt, reason, List.copyOf(balances.values()));
    }

    private static double cleanMoney(double value) {
        return Math.max(0d, MoneyMath.clean(value));
    }

    private static ArrayList<CorePlayerController> createPlayers(
            LobbySnapshot lobby, int buyin) {
        ArrayList<CorePlayerController> players = new ArrayList<>();
        CorePlayerController local = CorePlayerController.local(
                lobby.localNickname());
        initializePlayer(local, buyin);
        players.add(local);
        for (LobbyParticipant participant : lobby.participants()) {
            if (participant.local()) continue;
            CorePlayerController player = participant.bot()
                    ? CorePlayerController.bot(participant.nickname())
                    : CorePlayerController.remote(participant.nickname());
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
            LobbySnapshot lobby,
            com.tonikelope.coronapoker.core.game.GameChannel channel,
            ConfirmationTracker confirmations) {
        Map<String, GamePeerController> peers = new LinkedHashMap<>();
        for (LobbyParticipant participant : lobby.participants()) {
            GamePeerController peer;
            if (participant.local()) {
                peer = null;
            } else if (participant.bot()) {
                peer = GamePeerController.recoveryBot(participant.nickname());
            } else {
                peer = new GameChannelPeerController(participant.nickname(),
                        channel, confirmations, participant.identityPublicKey(),
                        participant.latency(), participant.previousLatency());
            }
            peers.put(participant.nickname(), peer);
        }
        return peers;
    }

    private static TableSnapshot initialSnapshot(LobbySnapshot lobby, int buyin) {
        List<TableSnapshot.PlayerSnapshot> players = lobby.participants().stream()
                .map(participant -> new TableSnapshot.PlayerSnapshot(
                participant.nickname(), buyin, 0d, 0d, true, false, false,
                false, LobbyParticipant.NO_LATENCY,
                LobbyParticipant.NO_LATENCY, 0, 0L,
                false, TableSnapshot.Position.NONE, "", "",
                List.of()))
                .toList();
        return new TableSnapshot(0L, lobby.localNickname(),
                TableSnapshot.Street.WAITING, 0d, "", false, players,
                List.of());
    }

    private static void submit(TableCommand command, Crupier dealer,
            CorePlayerController local,
            NetworkPauseCoordinator pauseCoordinator,
            TableEventBridge events,
            GamePresentationSettings presentationSettings,
            com.tonikelope.coronapoker.core.game.GameChannel channel,
            boolean host, ExecutorService controlExecutor,
            ExecutorService terminationExecutor,
            AtomicReference<TableSessionSummary.CloseReason>
                    requestedCloseReason,
            AtomicReference<com.tonikelope.coronapoker.core.NewGameTableDraft.BotDifficulty>
                    botDifficulty,
            AtomicBoolean textToSpeech, AtomicBoolean voiceMessages,
            AtomicBoolean closing) {
        Objects.requireNonNull(command, "command");
        if (closing.get()) return;
        try {
        if (command instanceof TableCommand.Fold) {
            local.submitDecision(CorePlayerController.FOLD, 0d);
        } else if (command instanceof TableCommand.CheckOrCall) {
            local.submitDecision(CorePlayerController.CHECK, 0d);
        } else if (command instanceof TableCommand.Bet bet) {
            local.submitDecision(CorePlayerController.BET, bet.amount());
        } else if (command instanceof TableCommand.AllIn) {
            local.submitDecision(CorePlayerController.ALLIN, 0d);
        } else if (command instanceof TableCommand.ShowCards) {
            dealer.requestVoluntaryShowCards(local.getNickname());
        } else if (command instanceof TableCommand.ExitGame) {
            // Termination must never queue behind a live-settings operation or
            // another control request that is waiting for network consensus.
            // Arm the cancellation fence BEFORE waking local waits. If the
            // dealer wakes first, observes a still-clear fence and goes back to
            // sleep, the ordered wire shutdown can wait indefinitely while no
            // subsequent notifier is able to reach it.
            requestedCloseReason.set(TableSessionSummary.CloseReason.EXITED);
            terminationExecutor.execute(() -> {
                try {
                    dealer.setTerminationPending();
                    pauseCoordinator.resumeForShutdown();
                    notifyBettingWait(dealer);
                    dealer.requestTableExit(false);
                    pauseCoordinator.resumeForShutdown();
                    notifyBettingWait(dealer);
                } catch (RuntimeException failure) {
                    // A failure on this dedicated executor cannot propagate to
                    // TableCommandSink.submit(). Contain it explicitly; without
                    // this handoff the renderer would wait forever for a
                    // CloseTable event that the failed task can no longer cause.
                    dealer.containExternalTableFailure(failure);
                }
            });
        } else if (command instanceof TableCommand.StopGame) {
            if (!host) {
                throw new IllegalStateException(
                        "Only the host can stop a table for recovery");
            }
            requestedCloseReason.set(
                    TableSessionSummary.CloseReason.RECOVERABLE_STOP);
            terminationExecutor.execute(() -> {
                try {
                    dealer.setTerminationPending();
                    pauseCoordinator.resumeForShutdown();
                    notifyBettingWait(dealer);
                    dealer.requestTableExit(true);
                    pauseCoordinator.resumeForShutdown();
                    notifyBettingWait(dealer);
                } catch (RuntimeException failure) {
                    dealer.containExternalTableFailure(failure);
                }
            });
        } else if (command instanceof TableCommand.ForceReconnectPlayers) {
            if (!host) {
                throw new IllegalStateException(
                        "Only the host can force peer reconnection");
            }
            // Starting a socket replacement is immediate and non-blocking.
            // Do not queue it behind live-settings consensus: the host chose
            // this action specifically to recover those transports now.
            channel.forceReconnectRemotePeers();
        } else if (command instanceof TableCommand.TogglePause) {
            pauseCoordinator.toggleLocal();
        } else if (command instanceof TableCommand.ChangeDeck) {
            String deck = presentationSettings.selectNextDeck();
            events.publish(sequence -> new TableVisualEvent.DeckChanged(
                    sequence, deck));
        } else if (command instanceof TableCommand.SelectDeck selectDeck) {
            String deck = presentationSettings.selectDeck(selectDeck.deck());
            events.publish(sequence -> new TableVisualEvent.DeckChanged(
                    sequence, deck));
        } else if (command instanceof TableCommand.ToggleImmediateRebuy) {
            dealer.requestImmediateRebuy();
        } else if (command instanceof TableCommand.SetLastHand lastHand) {
            if (!host) {
                throw new IllegalStateException(
                        "Only the host can schedule the last hand");
            }
            controlExecutor.execute(()
                    -> dealer.requestLastHand(lastHand.enabled()));
        } else if (command instanceof TableCommand.SetHandLimit handLimit) {
            if (!host) {
                throw new IllegalStateException(
                        "Only the host can change the hand limit");
            }
            controlExecutor.execute(()
                    -> dealer.requestHandLimit(handLimit.maximumHands()));
        } else if (command instanceof TableCommand.ApplyGameConfiguration update) {
            if (!host) {
                throw new IllegalStateException(
                        "Only the host can change live table settings");
            }
            controlExecutor.execute(()
                    -> dealer.requestGameConfiguration(update.configuration()));
        } else if (command instanceof TableCommand.SetBotDifficulty update) {
            if (!host) {
                throw new IllegalStateException(
                        "Only the host can change bot difficulty");
            }
            controlExecutor.execute(() -> {
                botDifficulty.set(update.difficulty());
                dealer.requestBotDifficulty(botDifficulty(update.difficulty()));
            });
        } else if (command instanceof TableCommand.SetCommunicationRules update) {
            if (!host) {
                throw new IllegalStateException(
                        "Only the host can change global communication rules");
            }
            controlExecutor.execute(() -> {
                textToSpeech.set(update.textToSpeech());
                voiceMessages.set(update.voiceMessages());
                dealer.requestCommunicationRules(update.textToSpeech(),
                        update.voiceMessages());
            });
        } else {
            throw new IllegalArgumentException(
                    "Unsupported table command " + command.getClass().getName());
        }
        } catch (RejectedExecutionException rejected) {
            // A final renderer frame and the owned table teardown are delivered
            // asynchronously. A user action can therefore race the short gap
            // between the controller closing and the renderer observing close.
            // Once closing is armed, that late input belongs to the dead table
            // and must be ignored instead of escaping on the render/UI thread.
            if (!closing.get()) throw rejected;
        }
    }

    private static boolean booleanPreference(Properties properties, String key,
            boolean fallback) {
        return Boolean.parseBoolean(properties.getProperty(key,
                Boolean.toString(fallback)));
    }

    private static Bot.Difficulty botDifficulty(
            com.tonikelope.coronapoker.core.NewGameTableDraft.BotDifficulty value) {
        return switch (Objects.requireNonNull(value, "value")) {
            case EASY -> Bot.Difficulty.EASY;
            case MEDIUM -> Bot.Difficulty.MEDIUM;
            case HARD -> Bot.Difficulty.HARD;
        };
    }

    private static void notifyBettingWait(Crupier dealer) {
        synchronized (dealer.getLock_apuestas()) {
            dealer.getLock_apuestas().notifyAll();
        }
    }

    private static GameWindowSink gameWindow(GameSession game,
            AtomicBoolean open, Runnable interruptDealer) {
        Objects.requireNonNull(interruptDealer, "interruptDealer");
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
                // Legacy Swing owns a global shutdown path which interrupts the
                // dealer when rondaApuestas parks after fin_de_la_transmision.
                // The renderer-neutral session has its own executor, so it must
                // provide that same wake-up here. Otherwise CloseTable waits for
                // dealer.run(), while the parked dealer waits for TableSession
                // cleanup: the UI remains indefinitely on "SALIENDO...".
                if (open.getAndSet(false)) {
                    interruptDealer.run();
                }
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

    /**
     * Host-authoritative PAUSE coordination over the existing authenticated
     * GAME channel. A client only requests a state; every endpoint applies the
     * canonical host relay, so a local overlay can never resume a remote table
     * on its own.
     */
    private static final class NetworkPauseCoordinator {
        private final GameLaunchContext context;
        private final GameSession game;
        private final SessionPauseGate gate;
        private final TableEventBridge events;
        private final Object lock = new Object();
        private String owner;
        private boolean transitionInFlight;

        NetworkPauseCoordinator(GameLaunchContext context, GameSession game,
                SessionPauseGate gate, TableEventBridge events) {
            this.context = context;
            this.game = game;
            this.gate = gate;
            this.events = events;
        }

        void toggleLocal() {
            final boolean requested;
            synchronized (lock) {
                if (transitionInFlight) return;
                requested = !game.isPaused();
                if (!requested && !context.lobby().host()
                        && !context.lobby().localNickname().equals(owner)) {
                    return;
                }
                transitionInFlight = true;
            }
            if (context.lobby().host()) {
                String canonicalOwner;
                synchronized (lock) {
                    canonicalOwner = requested
                            ? context.lobby().localNickname() : owner;
                }
                if (canonicalOwner == null || canonicalOwner.isBlank()) {
                    canonicalOwner = context.lobby().localNickname();
                }
                apply(requested, canonicalOwner);
                broadcast(requested, canonicalOwner);
            } else {
                try {
                    context.channel().sendToHost("PAUSE#" + (requested ? "1" : "0"))
                            .whenComplete((ignored, failure) -> {
                                if (failure != null) {
                                    synchronized (lock) {
                                        transitionInFlight = false;
                                    }
                                    context.channel().close();
                                    return;
                                }
                                /*
                                 * The legacy Swing host deliberately excludes
                                 * the requester from its PAUSE relay because a
                                 * Swing client applies its own request locally.
                                 * Apply only after the authenticated command is
                                 * confirmed so a renderer-neutral client obeys
                                 * the same wire contract. A GDX host may already
                                 * have echoed the authoritative relay; apply()
                                 * is idempotent for that case.
                                 */
                                apply(requested,
                                        context.lobby().localNickname());
                            });
                } catch (java.io.IOException failure) {
                    synchronized (lock) { transitionInFlight = false; }
                    context.channel().close();
                }
            }
        }

        boolean accept(com.tonikelope.coronapoker.core.game.GameChannel.Inbound inbound,
                String envelope) {
            if (!inbound.command().startsWith("PAUSE#")) return false;
            try {
                String[] fields = envelope.split("#", -1);
                if (context.lobby().host()) {
                    boolean requested = PauseWire.parseClientRequest(fields);
                    acceptClientRequest(inbound.peerNickname(), requested);
                } else {
                    PauseWire.Relay relay = PauseWire.parseHostRelay(fields);
                    if (!knownParticipant(relay.owner())) {
                        throw new IllegalArgumentException("unknown PAUSE owner");
                    }
                    apply(relay.paused(), relay.owner());
                }
            } catch (RuntimeException invalid) {
                context.channel().close();
            }
            return true;
        }

        private void acceptClientRequest(String requester, boolean requested) {
            if (!knownParticipant(requester)) {
                throw new IllegalArgumentException("unknown PAUSE requester");
            }
            synchronized (lock) {
                if (transitionInFlight || requested == game.isPaused()) return;
                if (!requested && !requester.equals(owner)) return;
                transitionInFlight = true;
            }
            String canonicalOwner = requested ? requester : owner;
            apply(requested, canonicalOwner);
            broadcast(requested, canonicalOwner);
        }

        private boolean knownParticipant(String nickname) {
            return context.lobby().participants().stream().anyMatch(
                    participant -> participant.nickname().equals(nickname));
        }

        private void broadcast(boolean paused, String canonicalOwner) {
            String relay = "PAUSE#" + (paused ? "1" : "0") + "#"
                    + Base64.getEncoder().encodeToString(
                            canonicalOwner.getBytes(StandardCharsets.UTF_8));
            try {
                context.channel().broadcastFromHost(relay, null)
                        .whenComplete((ignored, failure) -> {
                            synchronized (lock) { transitionInFlight = false; }
                            if (failure != null) context.channel().close();
                        });
            } catch (java.io.IOException failure) {
                synchronized (lock) { transitionInFlight = false; }
                context.channel().close();
            }
        }

        private void apply(boolean paused, String canonicalOwner) {
            synchronized (lock) {
                if (paused == game.isPaused()) {
                    if (paused && owner == null) owner = canonicalOwner;
                    transitionInFlight = false;
                    return;
                }
                if (!paused && owner != null && !owner.equals(canonicalOwner)) {
                    throw new IllegalArgumentException("PAUSE owner mismatch");
                }
                game.setPaused(paused);
                owner = paused ? canonicalOwner : null;
                transitionInFlight = false;
            }
            if (!paused) gate.resume();
            events.publish(sequence -> new TableVisualEvent.PauseStatus(
                    sequence, paused));
        }

        void resumeForShutdown() {
            synchronized (lock) {
                if (game.isPaused()) game.setPaused(false);
                owner = null;
                transitionInFlight = false;
            }
            gate.resume();
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

        @Override public String tablePassword() {
            return context.tablePassword();
        }
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
