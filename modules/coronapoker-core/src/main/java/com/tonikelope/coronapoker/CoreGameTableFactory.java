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
import com.tonikelope.coronapoker.core.game.GameEntropySource;
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
import com.tonikelope.coronapoker.core.game.HotJoinSnapshotCodecV1;
import com.tonikelope.coronapoker.core.game.HotJoinVisualEventCodecV1;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Builds the canonical controller for a renderer-neutral GDX table session. */
public final class CoreGameTableFactory implements GameTableFactory {

    private static final long TABLE_EXECUTOR_CLOSE_TIMEOUT_SECONDS = 5L;
    private static final long RECOVERY_SHUFFLE_PROOF_DRAIN_TIMEOUT_MS = 10_000L;
    private static final long EXIT_SHUFFLE_PROOF_DRAIN_TIMEOUT_MS = 2_000L;

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
    private final GameIdentityTrust identityTrust;
    private final GameEntropySource gameEntropy;

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
            GameCinematicAssets cinematicAssets,
            GameEntropySource gameEntropy) {
        this(database, gameText, gameLog, gameDialogs, gameDecisions,
                presentationSettings, cinematicAssets, false,
                new Properties(), GameIdentityTrust.unverified(), gameEntropy);
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
        this(database, gameText, gameLog, gameDialogs, gameDecisions,
                presentationSettings, cinematicAssets, modActive, preferences,
                GameIdentityTrust.unverified());
    }

    public CoreGameTableFactory(DatabaseService database, GameText gameText,
            GameLogSink gameLog, GameDialogSink gameDialogs,
            GameDecisionSink gameDecisions,
            GamePresentationSettings presentationSettings,
            GameCinematicAssets cinematicAssets, boolean modActive,
            Properties preferences, GameIdentityTrust identityTrust) {
        this(database, gameText, gameLog, gameDialogs, gameDecisions,
                presentationSettings, cinematicAssets, modActive, preferences,
                identityTrust, GameEntropySource.secure());
    }

    public CoreGameTableFactory(DatabaseService database, GameText gameText,
            GameLogSink gameLog, GameDialogSink gameDialogs,
            GameDecisionSink gameDecisions,
            GamePresentationSettings presentationSettings,
            GameCinematicAssets cinematicAssets, boolean modActive,
            Properties preferences, GameIdentityTrust identityTrust,
            GameEntropySource gameEntropy) {
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
        this.identityTrust = Objects.requireNonNull(identityTrust,
                "identityTrust");
        this.gameEntropy = Objects.requireNonNull(gameEntropy, "gameEntropy");
    }

    @Override
    public TableSession create(GameLaunchContext context) throws Exception {
        Objects.requireNonNull(context, "context");
        LobbySnapshot lobby = context.lobby();
        if (lobby.participants().size() < 2) {
            throw new IllegalStateException(
                    "La timba necesita al menos dos participantes");
        }

        GameConfigCodecV1.Configuration decodedConfiguration
                = context.initialConfiguration() == null
                        ? GameConfigCodecV1.fromSettings(lobby.tableSettings(),
                                lobby.recovering(), context.sessionId())
                        : context.initialConfiguration();
        GameConfigCodecV1.Configuration initialConfiguration
                = context.hotJoining()
                        ? decodedConfiguration.withRecover(true)
                        : decodedConfiguration;
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
        HotJoinSync hotJoin = new HotJoinSync(lobby.host(), context.channel(),
                gameLog, events, lobby.localNickname());
        events.observe(hotJoin::observePresentationEvent);
        ArrayList<CorePlayerController> players = createPlayers(lobby,
                initialConfiguration.buyin(), gameEntropy);
        CorePlayerController local = players.get(0);
        if (context.hotJoining()) {
            local.setSpectator(gameText.translate("game.calentando"));
        }
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
            if (!events.isAttached()) {
                return CompletableFuture.completedFuture(
                        new GameCinematicSink.Result(false, false));
            }
            TableVisualEvent.Cinematic.Type type =
                    TableVisualEvent.Cinematic.Type.valueOf(
                            request.type().name());
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
                context.channel().retirePeerAfterExit(nickname);
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
                context.identity(), hotJoin.logSink(), gameDialogs,
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
                identityTrust, text,
                cards -> new CoreGameHand(cards, text), CoreGamePot::new,
                GameRuntimeEnvironment.at(context.dataDirectory(), modActive),
                cinematicState,
                cinematicAssets, GameValueFormatter.plain(),
                 GameBotService.standalone(), gameEntropy, events);
        hotJoin.bind(dealer);
        dealer.initializeCommunicationRules(textToSpeech.get(),
                voiceMessages.get());
        if (context.hotJoining()) {
            dealer.markPassiveHotJoinObserver();
        }

        players.forEach(player -> player.bindPotRegistration(() -> {
            if (dealer.getGamePot() != null) {
                dealer.getGamePot().addPlayerController(player);
            }
        }));
        players.forEach(player -> player.bindCommittedRebuy(
                () -> dealer.consumeCommittedRebuy(
                        player.getNickname(), player.getStack())));
        local.bindAcceptedLocalFoldSignal(dealer::soundFold);
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
        Map<String, Long> immediateRebuySources = new ConcurrentHashMap<>();
        AtomicLong immediateRebuySource = new AtomicLong();
        for (Map.Entry<String, GamePeerController> peer : peers.entrySet()) {
            if (peer.getValue() != null && !peer.getValue().isCpu()) {
                immediateRebuySources.put(peer.getKey(),
                        immediateRebuySource.incrementAndGet());
            }
        }
        AtomicReference<TableSessionSummary.CloseReason> requestedCloseReason
                = new AtomicReference<>(
                        TableSessionSummary.CloseReason.COMPLETED);
        AutoCloseable inbound = context.channel().subscribe(command -> {
            String envelope = transport.inboundEnvelope(command);
            if (command.command().startsWith("HOTJOIN#")) {
                try {
                    String expectedSource = lobby.host()
                            ? lobby.localNickname() : lobby.serverNickname();
                    if (!command.peerNickname().equals(expectedSource)) {
                        throw new IllegalArgumentException(
                                "HOTJOIN source is not authoritative");
                    }
                    String[] fields = command.command().split("#", -1);
                    if (fields.length != 5) {
                        throw new IllegalArgumentException(
                                "Malformed HOTJOIN notification");
                    }
                    String nickname = new String(Base64.getDecoder().decode(
                            fields[1]), StandardCharsets.UTF_8).trim();
                    byte[] identityKey = Base64.getDecoder().decode(fields[3]);
                    byte[] identitySignature = Base64.getDecoder().decode(
                            fields[4]);
                    if (nickname.isEmpty() || identityKey.length == 0
                            || identitySignature.length == 0
                            || peers.containsKey(nickname)) {
                        throw new IllegalArgumentException(
                                "Invalid or duplicate HOTJOIN participant");
                    }
                    CorePlayerController newcomer
                            = CorePlayerController.remote(nickname);
                    initializePlayer(newcomer, initialConfiguration.buyin());
                    GamePeerController newcomerPeer
                            = new GameChannelPeerController(nickname,
                                    context.channel(),
                                    transport.confirmations(), identityKey,
                                    LobbyParticipant.NO_LATENCY,
                                    LobbyParticipant.NO_LATENCY);
                    hotJoin.register(nickname);
                    dealer.queueHotJoin(newcomer, newcomerPeer,
                            () -> {
                                immediateRebuySources.put(nickname,
                                        immediateRebuySource.incrementAndGet());
                                peers.put(nickname, newcomerPeer);
                                hotJoin.completeAdmission(nickname);
                            },
                            commandBody -> hotJoin.sendBoundary(nickname,
                                    commandBody));
                    if (lobby.host()) {
                        hotJoin.bootstrap(nickname);
                    }
                } catch (RuntimeException invalid) {
                    context.channel().close();
                }
                return;
            }
            if (!lobby.host()
                    && command.command().equals("HOTJOIN_LOG_RESET")) {
                if (!command.peerNickname().equals(lobby.serverNickname())) {
                    context.channel().close();
                    return;
                }
                hotJoin.replaceHistory();
                return;
            }
            if (!lobby.host()
                    && command.command().startsWith("HOTJOIN_LOG#")) {
                try {
                    if (!command.peerNickname().equals(lobby.serverNickname())) {
                        throw new IllegalArgumentException(
                                "HOTJOIN_LOG source is not the table host");
                    }
                    String[] fields = command.command().split("#", -1);
                    if (fields.length != 2) {
                        throw new IllegalArgumentException(
                                "Malformed HOTJOIN_LOG notification");
                    }
                    hotJoin.acceptLog(new String(Base64.getDecoder().decode(
                            fields[1]), StandardCharsets.UTF_8));
                } catch (RuntimeException invalid) {
                    context.channel().close();
                }
                return;
            }
            if (!lobby.host()
                    && command.command().startsWith("HOTJOIN_STATE#")) {
                try {
                    if (!command.peerNickname().equals(lobby.serverNickname())) {
                        throw new IllegalArgumentException(
                                "HOTJOIN_STATE source is not the table host");
                    }
                    String[] fields = command.command().split("#", -1);
                    if (fields.length != 2) {
                        throw new IllegalArgumentException(
                                "Malformed HOTJOIN_STATE notification");
                    }
                    hotJoin.acceptSnapshot(fields[1]);
                } catch (RuntimeException invalid) {
                    context.channel().close();
                }
                return;
            }
            if (!lobby.host()
                    && command.command().startsWith("HOTJOIN_EVENT#")) {
                try {
                    if (!command.peerNickname().equals(lobby.serverNickname())) {
                        throw new IllegalArgumentException(
                                "HOTJOIN_EVENT source is not the table host");
                    }
                    String[] fields = command.command().split("#", -1);
                    if (fields.length != 2) {
                        throw new IllegalArgumentException(
                                "Malformed HOTJOIN_EVENT notification");
                    }
                    hotJoin.acceptPresentation(fields[1]);
                } catch (RuntimeException invalid) {
                    context.channel().close();
                }
                return;
            }
            if (!lobby.host()
                    && command.command().equals("HOTJOIN_EXITED")) {
                if (!command.peerNickname().equals(lobby.serverNickname())) {
                    context.channel().close();
                    return;
                }
                transport.acceptHostExit(true);
                return;
            }
            if (!lobby.host()
                    && command.command().equals("EXIT_ACCEPTED")) {
                if (!command.peerNickname().equals(lobby.serverNickname())) {
                    context.channel().close();
                    return;
                }
                transport.acceptHostExit(false);
                return;
            }
            if (!lobby.host()
                    && command.command().startsWith("DELUSER#")) {
                try {
                    if (!command.peerNickname().equals(
                            lobby.serverNickname())) {
                        throw new IllegalArgumentException(
                                "DELUSER source is not the table host");
                    }
                    String[] fields = command.command().split("#", -1);
                    if (fields.length != 2) {
                        throw new IllegalArgumentException(
                                "Malformed DELUSER notification");
                    }
                    String nickname = new String(Base64.getDecoder().decode(
                            fields[1]), StandardCharsets.UTF_8);
                    if (hotJoin.isWarming(nickname)) {
                        // The lobby and every dealer must cancel the same
                        // reservation. Leaving it queued on an incumbent makes
                        // that peer admit a ghost seat at the next boundary and
                        // diverge the crypto ring from the host.
                        hotJoin.disconnect(nickname);
                        return;
                    }
                } catch (RuntimeException invalid) {
                    context.channel().close();
                    return;
                }
            }
            if (lobby.host() && command.command().equals("HOTJOIN_EXIT")) {
                String nickname = command.peerNickname();
                try {
                    if (!hotJoin.disconnect(nickname)) {
                        throw new IllegalArgumentException(
                                "HOTJOIN_EXIT source is not warming");
                    }
                    // A warming observer is not an active poker player: it has
                    // no hand testament, private-card proof or ordinary EXIT
                    // transition to apply. Remove its pending seat immediately
                    // and retire only its authenticated transport peer.
                    context.channel().sendFromHost(nickname,
                            "HOTJOIN_EXITED").toCompletableFuture().join();
                } catch (RuntimeException invalid) {
                    hotJoin.disconnect(nickname);
                } catch (java.io.IOException failure) {
                    hotJoin.disconnect(nickname);
                } finally {
                    context.channel().retirePeerAfterExit(nickname);
                }
                return;
            }
            if (command.command().startsWith("YOUARELATE#")) {
                try {
                    if (!command.peerNickname().equals(lobby.serverNickname())) {
                        throw new IllegalArgumentException(
                                "YOUARELATE source is not the table host");
                    }
                    String[] fields = command.command().split("#", -1);
                    if (fields.length != 3) {
                        throw new IllegalArgumentException(
                                "Malformed YOUARELATE notification");
                    }
                    String nickname = new String(Base64.getDecoder().decode(
                            fields[1]), StandardCharsets.UTF_8).trim();
                    if (nickname.isEmpty()) {
                        throw new IllegalArgumentException(
                                "Blank YOUARELATE nickname");
                    }
                    events.publish(sequence
                            -> new TableVisualEvent.LateJoinRequest(
                                    sequence, nickname))
                            .whenComplete((ignored, failure) -> {
                                if (failure != null) context.channel().close();
                            });
                } catch (RuntimeException invalid) {
                    context.channel().close();
                }
                return;
            }
            if (!lobby.host()
                    && command.command().startsWith("TIMEOUT#")) {
                try {
                    if (!command.peerNickname().equals(lobby.serverNickname())) {
                        throw new IllegalArgumentException(
                                "TIMEOUT source is not the table host");
                    }
                    String[] fields = command.command().split("#", -1);
                    if (fields.length != 2) {
                        throw new IllegalArgumentException(
                                "Malformed TIMEOUT notification");
                    }
                    String nickname = new String(Base64.getDecoder().decode(
                            fields[1]), StandardCharsets.UTF_8).trim();
                    if (!peers.containsKey(nickname)) {
                        throw new IllegalArgumentException(
                                "Unknown TIMEOUT player");
                    }
                    dealer.applyPlayerTimeout(nickname, true);
                } catch (RuntimeException invalid) {
                    context.channel().close();
                }
                return;
            }
            if (!lobby.host()
                    && (command.command().equals("SERVEREXIT")
                    || command.command().startsWith("SERVEREXITRECOVER"))) {
                dealer.acceptAuthoritativeTableExit(command.command());
                pauseCoordinator.resumeForShutdown();
                notifyBettingWait(dealer);
                return;
            }
            if (lobby.host() && command.command().startsWith("EXIT#")) {
                try {
                    PlayerExitWire.Command playerExit = PlayerExitWire.parseClientRequest(
                            envelope.split("#", -1), command.peerNickname());
                    GamePeerController exitingPeer = peers.get(playerExit.nick());
                    if (exitingPeer == null || exitingPeer.isCpu()) {
                        throw new IllegalArgumentException(
                                "EXIT source is not a current remote human");
                    }
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

                    // A voluntary EXIT is an ordering-critical transition.
                    // Apply it on the ordered channel drain before EOF can
                    // publish a definitive peer loss. Offloading this work to
                    // controlExecutor allowed the socket-loss callback to win
                    // the race, void an honest hand and show RECONNECTING for
                    // a player who had deliberately left the table.
                    dealer.remotePlayerQuit(playerExit.nick(),
                            playerExit.testamentWire(),
                            playerExit.pocketKeyWire(),
                            playerExit.pocketSignatureWire());
                    // Fence the later end-of-hand model cleanup before the
                    // socket is retired. That cleanup is nickname-based; a
                    // fresh same-identity incarnation may already own the nick
                    // by the time the old hand settles.
                    dealer.markPeerExitTransportRetired(playerExit.nick());
                    notifyBettingWait(dealer);
                    context.channel().sendFromHost(playerExit.nick(),
                            "EXIT_ACCEPTED").toCompletableFuture().join();
                    peers.remove(playerExit.nick());
                    immediateRebuySources.remove(playerExit.nick());
                    context.channel().retirePeerAfterExit(playerExit.nick());
                } catch (java.io.IOException failure) {
                    peers.remove(command.peerNickname());
                    immediateRebuySources.remove(command.peerNickname());
                    context.channel().retirePeerAfterExit(
                            command.peerNickname());
                } catch (RuntimeException invalid) {
                    peers.remove(command.peerNickname());
                    immediateRebuySources.remove(command.peerNickname());
                    context.channel().retirePeerAfterExit(
                            command.peerNickname());
                }
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
                    // Keep the transport roster symmetrical with the host.
                    // The exited GamePlayerController deliberately remains in
                    // the dealer until a same-identity hot join replaces it,
                    // but this peer slot is no longer occupied. Retaining it
                    // here made surviving clients reject that legitimate
                    // HOTJOIN as a duplicate and close their whole channel.
                    peers.remove(playerExit.nick());
                    immediateRebuySources.remove(playerExit.nick());
                    notifyBettingWait(dealer);
                } catch (RuntimeException invalid) {
                    context.channel().close();
                }
                return;
            }
            if (pauseCoordinator.accept(command, envelope)) {
                return;
            }
            if (lobby.host() && command.command().equals("IWTSTH")) {
                GamePeerController requester = peers.get(command.peerNickname());
                if (requester == null || requester.isCpu()
                        || requester.isExit() || !dealer.isShow_time()) {
                    context.channel().close();
                    return;
                }
                dealer.IWTSTH_HANDLER(command.peerNickname());
                return;
            }
            if (!lobby.host()
                    && command.command().startsWith("IWTSTH#")) {
                try {
                    String[] fields = command.command().split("#", -1);
                    if (fields.length != 2) {
                        throw new IllegalArgumentException(
                                "Malformed IWTSTH notification");
                    }
                    String requester = new String(Base64.getDecoder().decode(
                            fields[1]), StandardCharsets.UTF_8).trim();
                    if (requester.isEmpty() || !peers.containsKey(requester)) {
                        throw new IllegalArgumentException(
                                "Unknown IWTSTH requester");
                    }
                    dealer.IWTSTH_HANDLER(requester);
                } catch (RuntimeException invalid) {
                    context.channel().close();
                }
                return;
            }
            if (!lobby.host()
                    && command.command().startsWith("IWTSTHSHOW#")) {
                try {
                    String[] fields = command.command().split("#", -1);
                    if (fields.length != 3) {
                        throw new IllegalArgumentException(
                                "Malformed IWTSTHSHOW notification");
                    }
                    String requester = new String(Base64.getDecoder().decode(
                            fields[1]), StandardCharsets.UTF_8).trim();
                    if (requester.isEmpty() || !peers.containsKey(requester)) {
                        throw new IllegalArgumentException(
                                "Unknown IWTSTHSHOW requester");
                    }
                    if (!"true".equals(fields[2])
                            && !"false".equals(fields[2])) {
                        throw new IllegalArgumentException(
                                "Invalid IWTSTHSHOW verdict");
                    }
                    dealer.IWTSTH_SHOW(requester,
                            Boolean.parseBoolean(fields[2]));
                } catch (RuntimeException invalid) {
                    context.channel().close();
                }
                return;
            }
            if (lobby.host()
                    && command.command().startsWith("RABBIT_REQ#")) {
                try {
                    String[] fields = command.command().split("#", -1);
                    if (fields.length != 2) {
                        throw new IllegalArgumentException(
                                "Malformed Rabbit request");
                    }
                    RabbitFeeLedger.Result<RabbitFeeLedger.Request> decoded
                            = RabbitFeeLedger.Request.decode(
                                    Base64.getDecoder().decode(fields[1]));
                    if (!decoded.isOk()
                            || !command.peerNickname().equals(
                                    decoded.value().playerId())) {
                        throw new IllegalArgumentException(
                                "Invalid Rabbit request identity or wire");
                    }
                    GamePeerController requester = peers.get(
                            command.peerNickname());
                    if (requester == null || requester.isCpu()
                            || requester.isExit()) {
                        throw new IllegalArgumentException(
                                "Rabbit requester is not an active remote human");
                    }
                    dealer.RABBIT_REQUEST_HANDLER(decoded.value());
                } catch (RuntimeException invalid) {
                    context.channel().close();
                }
                return;
            }
            if (!lobby.host()
                    && command.command().startsWith("RABBIT_AUTH#")) {
                try {
                    String[] fields = command.command().split("#", -1);
                    if (fields.length != 2) {
                        throw new IllegalArgumentException(
                                "Malformed Rabbit authorization");
                    }
                    RabbitFeeLedger.Result<RabbitFeeLedger.Authorization> decoded
                            = RabbitFeeLedger.Authorization.decode(
                                    Base64.getDecoder().decode(fields[1]));
                    if (!decoded.isOk()
                            || !peers.containsKey(
                                    decoded.value().request().playerId())) {
                        throw new IllegalArgumentException(
                                "Invalid Rabbit authorization wire");
                    }
                    dealer.RABBIT_AUTHORIZATION_HANDLER(decoded.value());
                } catch (RuntimeException invalid) {
                    context.channel().close();
                }
                return;
            }
            if (lobby.host()
                    && command.command().startsWith("REBUYNOW#")) {
                try {
                    if (hotJoin.isWarming(command.peerNickname())) {
                        // CALENTANDO owns no seat in the current hand and can
                        // never alter money. The same rule is enforced by the
                        // frontend and dealer, but the host is authoritative
                        // against a forged wire command.
                        return;
                    }
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
                    context.channel().retirePeerAfterExit(
                            command.peerNickname());
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
                    if (hotJoin.isWarming(command.peerNickname())) {
                        if (!hotJoin.acceptEarlyReady(
                                command.peerNickname(), envelope)) {
                            throw new IllegalArgumentException(
                                    "HAND_READY source is not warming");
                        }
                        if (peers.containsKey(command.peerNickname())) {
                            hotJoin.completeAdmission(command.peerNickname());
                        }
                    } else if (peers.containsKey(command.peerNickname())) {
                        dealer.acceptRemoteHandReady(command.peerNickname(),
                                envelope);
                    } else {
                        throw new IllegalArgumentException(
                                "HAND_READY source is not seated or warming");
                    }
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
            if (peer == null && hotJoin.isWarming(nickname)) {
                // Loss callbacks belong to a physical socket generation, but
                // the dealer seat is keyed by nickname. A delayed callback
                // from the incarnation that already exited must not cancel a
                // newly authenticated warming incarnation with the same nick.
                // The transport map points at the current generation, so a
                // live current socket proves this notification is stale.
                if (hotJoin.disconnectDefinitiveLoss(nickname)) return;
            }
            // A normal authenticated EXIT may be followed immediately by EOF
            // from the same socket. Do not use peer.isExit() for this test:
            // the transport also marks a socket-dead peer out to release ACK
            // waits. Treating that transport flag as a voluntary EXIT hid an
            // unannounced crash whenever another player had just left cleanly.
            if (peer == null || peer.isCpu()
                    || dealer.hasAcceptedPeerExit(nickname)) return;
            // The watchdog runs outside the dealer thread. It may only publish
            // the definitive loss and wake waits; refunding here can race an
            // accepted action while its bet is still being committed. The
            // dealer consumes this signal at the next safe betting boundary.
            dealer.requestDefinitivePeerLossAbort(nickname);
            notifyBettingWait(dealer);
        });

                TableSession table = new TableSession(initialSnapshot(lobby,
                initialConfiguration.buyin(), context.hotJoining()), command -> {
                    submit(command, dealer, local, pauseCoordinator, events,
                            presentationSettings, context.channel(),
                            lobby.host(), context.hotJoining(), controlExecutor,
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
                    hotJoin.close();
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
            LobbySnapshot lobby, int buyin, GameEntropySource entropy) {
        ArrayList<CorePlayerController> players = new ArrayList<>();
        CorePlayerController local = CorePlayerController.local(
                lobby.localNickname());
        initializePlayer(local, buyin);
        players.add(local);
        for (LobbyParticipant participant : lobby.participants()) {
            if (participant.local()) continue;
            java.util.Random botRandom = participant.bot()
                    ? entropy.botDecisionRandom(participant.nickname()) : null;
            CorePlayerController player = participant.bot()
                    ? botRandom == null
                            ? CorePlayerController.bot(participant.nickname())
                            : CorePlayerController.bot(participant.nickname(),
                                    botRandom)
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

    static Map<String, GamePeerController> createPeers(
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

    private static TableSnapshot initialSnapshot(LobbySnapshot lobby, int buyin,
            boolean hotJoining) {
        List<TableSnapshot.PlayerSnapshot> players = lobby.participants().stream()
                .map(participant -> new TableSnapshot.PlayerSnapshot(
                participant.nickname(), buyin, 0d, 0d,
                !(hotJoining && participant.local()),
                hotJoining && participant.local(), false,
                false, LobbyParticipant.NO_LATENCY,
                LobbyParticipant.NO_LATENCY, 0, 0L,
                false, false, TableSnapshot.Position.NONE, "", "",
                List.of(), buyin, 0, hotJoining && participant.local()))
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
            boolean host, boolean hotJoining,
            ExecutorService controlExecutor,
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
        } else if (command instanceof TableCommand.RequestIwtsth request) {
            dealer.requestIwtsthFromTable(local.getNickname(),
                    request.candidateNickname());
        } else if (command instanceof TableCommand.RequestRabbit) {
            dealer.REQUEST_RABBIT(local.getNickname());
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
                    if (hotJoining && local.isCalentando()) {
                        pauseCoordinator.resumeForShutdown();
                        notifyBettingWait(dealer);
                        dealer.requestPassiveHotJoinExit(() -> {
                            try {
                                channel.sendToHost("HOTJOIN_EXIT")
                                        .toCompletableFuture().join();
                            } catch (java.io.IOException failure) {
                                throw new IllegalStateException(
                                        "Cannot notify the host that the warming observer left",
                                        failure);
                            }
                        });
                        pauseCoordinator.resumeForShutdown();
                        notifyBettingWait(dealer);
                        return;
                    }
                    // Admission and the first SRA deal overlap briefly. If the
                    // newcomer closes in that window, keep its socket servicing
                    // cascade/rotation requests until the hand reaches the same
                    // safe exit point as every established player. Otherwise a
                    // voluntary UI exit tears down the shared crypto ring and
                    // creates a table-wide misdeal.
                    if (hotJoining) {
                        dealer.awaitHotJoinFirstDealSafeExitPoint();
                    }
                    // A player may later rejoin a recoverable table with the
                    // same identity. Give an already-running verifier a short,
                    // bounded chance to persist its genuine verdict before the
                    // terminal flag cancels it. This stays responsive for a
                    // normal exit and never promotes an unfinished proof.
                    dealer.awaitCurrentShuffleProofForRecoverableShutdown(
                            EXIT_SHUFFLE_PROOF_DRAIN_TIMEOUT_MS);
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
                    // Preserve the current hand's genuine shuffle verdict before
                    // requestTableExit marks the transmission finished.  The
                    // verifier deliberately cancels once that terminal flag is
                    // visible, so draining only from dealer.run()'s finally block
                    // is too late: recovery can then find a valid megapacket but
                    // no durable SHUFFLE_VERIFIED marker and must reject it.  This
                    // bounded wait never manufactures a verdict; a timeout stays
                    // fail-closed and the recovered hand remains unusable.
                    dealer.awaitCurrentShuffleProofForRecoverableShutdown(
                            RECOVERY_SHUFFLE_PROOF_DRAIN_TIMEOUT_MS);
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
        } else if (command instanceof TableCommand.KickTimedOutPlayer kick) {
            if (!host) {
                throw new IllegalStateException(
                        "Only the host can kick a timed-out player");
            }
            controlExecutor.execute(()
                    -> dealer.kickTimedOutRemotePlayer(kick.nickname()));
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

    /**
     * Keeps live newcomers outside the active hand while giving them a bounded,
     * public-only view and the complete visible log.  Admission remains owned
     * by the dealer at the next hand boundary.
     */
    private static final class HotJoinSync implements AutoCloseable {

        private static final int MAX_LOG_ENTRIES = 2_000;
        private static final int MAX_LOG_CHARS = 16_384;
        private static final java.util.logging.Logger LOGGER
                = java.util.logging.Logger.getLogger(
                        HotJoinSync.class.getName());

        private final boolean host;
        private final com.tonikelope.coronapoker.core.game.GameChannel channel;
        private final GameLogSink delegate;
        private final TableEventBridge events;
        private final String localNickname;
        private final Object historyLock = new Object();
        private final ArrayList<String> history = new ArrayList<>();
        private final Set<String> warming = ConcurrentHashMap.newKeySet();
        private final ConcurrentMap<String, String> earlyReady
                = new ConcurrentHashMap<>();
        /*
         * A warming peer has exactly one state bootstrap followed by the same
         * ordered public presentation events as every other GDX table.  Keep
         * events that race the asynchronous bootstrap here: letting one pass
         * before HOTJOIN_STATE would allow that older snapshot to repaint the
         * already animated cards/chips afterwards.
         */
        private final Object presentationLock = new Object();
        private final Map<String, HotJoinPresentationCutover<String>>
                presentationCutovers
                = new LinkedHashMap<>();
        private final ScheduledExecutorService scheduler;
        private volatile Crupier dealer;

        HotJoinSync(boolean host,
                com.tonikelope.coronapoker.core.game.GameChannel channel,
                GameLogSink delegate, TableEventBridge events,
                String localNickname) {
            this.host = host;
            this.channel = Objects.requireNonNull(channel, "channel");
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.events = Objects.requireNonNull(events, "events");
            this.localNickname = Objects.requireNonNull(localNickname,
                    "localNickname");
            this.scheduler = host
                    ? Executors.newSingleThreadScheduledExecutor(task -> {
                        Thread thread = new Thread(task,
                                "CoronaPoker-hot-join-sync");
                        thread.setDaemon(true);
                        return thread;
                    }) : null;
        }

        GameLogSink logSink() {
            return new GameLogSink() {
                @Override
                public void print(String message) {
                    delegate.print(message);
                    if (!host || message == null) return;
                    String bounded = message.length() > MAX_LOG_CHARS
                            ? message.substring(0, MAX_LOG_CHARS) : message;
                    synchronized (historyLock) {
                        history.add(bounded);
                        if (history.size() > MAX_LOG_ENTRIES) {
                            history.remove(0);
                        }
                    }
                    for (String nickname : warming) {
                        send(nickname, logCommand(bounded));
                    }
                }

                @Override
                public void updateShowdownCards(List<ShowdownEntry> entries) {
                    delegate.updateShowdownCards(entries);
                }

                @Override
                public void replaceHistory(List<String> messages) {
                    delegate.replaceHistory(messages);
                }
            };
        }

        void bind(Crupier dealer) {
            this.dealer = Objects.requireNonNull(dealer, "dealer");
        }

        void register(String nickname) {
            String checked = requireNickname(nickname);
            synchronized (presentationLock) {
                if (!warming.add(checked)) {
                    throw new IllegalArgumentException(
                            "Duplicate warming participant: " + nickname);
                }
                presentationCutovers.put(checked,
                        new HotJoinPresentationCutover<>());
            }
        }

        void bootstrap(String nickname) {
            if (!host || scheduler == null || !warming.contains(nickname)) {
                throw new IllegalStateException(
                        "Hot-join bootstrap is not available");
            }
            scheduler.execute(() -> {
                Crupier current = dealer;
                if (current == null || !warming.contains(nickname)) return;
                LOGGER.log(java.util.logging.Level.INFO,
                        "HOT JOIN: bootstrapping public table state for {0}",
                        nickname);
                send(nickname, "HOTJOIN_LOG_RESET");
                List<String> copy;
                synchronized (historyLock) {
                    copy = List.copyOf(history);
                }
                for (String message : copy) {
                    send(nickname, logCommand(message));
                }
                send(nickname, current.hotJoinSeatsCommand());
                send(nickname, "RECOVERDATA#"
                        + current.hotJoinRecoveryPayload());
                long snapshotFrontier;
                TableSnapshot publicSnapshot;
                synchronized (presentationLock) {
                    if (!warming.contains(nickname)) return;
                    /*
                     * Read the sequence before the model snapshot. Any event
                     * assigned at or below this frontier is necessarily old
                     * enough to be represented by the following snapshot.
                     * An event racing after the frontier is deliberately
                     * replayed: whether or not its model mutation also reached
                     * the snapshot, replaying the same canonical transition is
                     * safe and preserves the normal animation stream.
                     */
                    snapshotFrontier = events.lastSequence();
                    publicSnapshot = current.publicHotJoinSnapshot();
                    presentationCutovers.get(nickname)
                            .beginSnapshot(snapshotFrontier);
                }
                sendSnapshot(nickname, publicSnapshot);
                current.publicHotJoinTurnTimer().ifPresent(timer -> send(
                        nickname, "HOTJOIN_EVENT#"
                        + HotJoinVisualEventCodecV1.encode(timer)
                                .orElseThrow()));
                synchronized (presentationLock) {
                    if (!warming.contains(nickname)) return;
                    HotJoinPresentationCutover<String> cutover
                            = presentationCutovers.get(nickname);
                    if (cutover != null) {
                        for (String command : cutover.completeSnapshot()) {
                            send(nickname, command);
                        }
                    }
                }
                LOGGER.log(java.util.logging.Level.INFO,
                        "HOT JOIN: public table bootstrap queued for {0}",
                        nickname);
            });
        }

        boolean acceptEarlyReady(String nickname, String envelope) {
            if (!host || !warming.contains(nickname)) return false;
            String checked = Objects.requireNonNull(envelope, "envelope");
            int incomingHand = readyHand(checked);
            earlyReady.compute(nickname, (ignored, previous) -> {
                if (previous == null) return checked;
                int previousHand = readyHand(previous);
                if (incomingHand < previousHand) {
                    throw new IllegalArgumentException(
                            "Regressive early HAND_READY from " + nickname);
                }
                return incomingHand == previousHand ? previous : checked;
            });
            return true;
        }

        void completeAdmission(String nickname) {
            /*
             * Admission is a replicated dealer transition, not a consequence
             * of observing HAND_READY. Only the host receives a newcomer's
             * early readiness frame; incumbent clients therefore used to keep
             * the nickname in their warming presentation/transport sets after
             * the canonical seat had already become playable. That stale
             * reservation could later reinterpret an ordinary DELUSER as a
             * pre-admission exit and repaint CALENTANDO as ESPECTADOR.
             *
             * Retire the replicated warming presentation on every non-host
             * node as soon as its dealer admits the canonical seat. The host
             * keeps both presentation relay and the transport gate until it
             * has the matching HAND_READY: opening ordinary GAME traffic
             * before recovery is confirmed would let the first playable hand
             * overtake the newcomer's bootstrap.
             */
            if (host) {
                String envelope = earlyReady.get(nickname);
                if (envelope == null
                        || readyHand(envelope) != dealer.getMano() + 1) {
                    return;
                }
                dealer.acceptRemoteHandReady(nickname, envelope);
                earlyReady.remove(nickname, envelope);
            }
            forgetPresentation(nickname);
            channel.activatePeer(nickname);
        }

        boolean isWarming(String nickname) {
            return warming.contains(nickname);
        }

        java.util.concurrent.CompletionStage<Void> sendBoundary(
                String nickname, String command) {
            if (!warming.contains(nickname)) {
                return CompletableFuture.completedFuture(null);
            }
            try {
                java.util.concurrent.CompletionStage<Void> delivery
                        = channel.sendFromHost(nickname, command);
                delivery.whenComplete((ignored, failure) -> {
                    if (failure != null) disconnect(nickname);
                });
                return delivery;
            } catch (java.io.IOException failure) {
                disconnect(nickname);
                return CompletableFuture.failedFuture(failure);
            }
        }

        private static int readyHand(String envelope) {
            String[] fields = envelope.split("#", -1);
            if (fields.length != 4 || !"GAME".equals(fields[0])
                    || !"HAND_READY".equals(fields[2])) {
                throw new IllegalArgumentException("Malformed HAND_READY");
            }
            int hand = Integer.parseInt(fields[3]);
            if (hand < 1) {
                throw new IllegalArgumentException("Invalid HAND_READY hand");
            }
            return hand;
        }

        boolean disconnect(String nickname) {
            boolean removed = forgetPresentation(nickname);
            earlyReady.remove(nickname);
            Crupier current = dealer;
            if (removed && current != null) {
                // Every pre-admission loss converges here, including an
                // explicit HOTJOIN_EXIT, authoritative DELUSER, definitive
                // EOF and a failed bootstrap delivery. Every peer owns a local
                // dealer queue, so cancellation must converge on incumbents as
                // well as the host or their next-hand money/ring snapshots
                // retain a ghost newcomer.
                current.cancelHotJoin(nickname);
            }
            return removed;
        }

        boolean disconnectDefinitiveLoss(String nickname) {
            boolean removed;
            synchronized (presentationLock) {
                if (!warming.contains(nickname)
                        || channel.isPeerConnected(nickname)
                        || !channel.retirePeerAfterDefinitiveLoss(nickname)) {
                    return false;
                }
                // register() uses this same lock. Keep transport retirement
                // and reservation removal indivisible with respect to a fresh
                // HOTJOIN using the same persistent identity.
                presentationCutovers.remove(nickname);
                removed = warming.remove(nickname);
            }
            earlyReady.remove(nickname);
            Crupier current = dealer;
            if (removed && current != null) {
                current.cancelHotJoin(nickname);
            }
            return removed;
        }

        void replaceHistory() {
            delegate.replaceHistory(List.of());
        }

        void acceptLog(String message) {
            delegate.print(Objects.requireNonNull(message, "message"));
        }

        void acceptSnapshot(String encoded) {
            TableSnapshot snapshot = HotJoinSnapshotCodecV1.decode(encoded,
                    localNickname);
            LOGGER.log(java.util.logging.Level.INFO,
                    "HOT JOIN: accepted public table bootstrap for {0} with {1} seat(s)",
                    new Object[]{localNickname, snapshot.players().size()});
            events.publish(sequence -> new TableVisualEvent.HotJoinState(
                    sequence, snapshot));
        }

        /**
         * Relays the ordinary public presentation event itself.  The warming
         * client feeds the decoded event through its normal TableEventBridge,
         * so GDX has one animation path for players and every spectator state.
         */
        void observePresentationEvent(TableVisualEvent event) {
            if (!host || warming.isEmpty()) return;
            TableVisualEvent publicEvent = event;
            if (event instanceof TableVisualEvent.SeatRoster roster) {
                Crupier current = dealer;
                if (current == null) return;
                publicEvent = new TableVisualEvent.SeatRoster(
                        roster.sequence(), current.publicHotJoinRoster());
            }
            String encoded = HotJoinVisualEventCodecV1.encode(publicEvent)
                    .orElse(null);
            if (encoded == null) return;
            String command = "HOTJOIN_EVENT#" + encoded;
            synchronized (presentationLock) {
                for (String nickname : warming) {
                    HotJoinPresentationCutover<String> cutover
                            = presentationCutovers.get(nickname);
                    if (cutover != null) {
                        cutover.accept(publicEvent.sequence(), command)
                                .ifPresent(relay -> send(nickname, relay));
                    }
                }
            }
        }

        void acceptPresentation(String encoded) {
            events.publish(sequence -> HotJoinVisualEventCodecV1.decode(
                    encoded, sequence));
        }

        private void sendSnapshot(String nickname, TableSnapshot snapshot) {
            send(nickname, "HOTJOIN_STATE#"
                    + HotJoinSnapshotCodecV1.encode(snapshot));
        }

        private boolean forgetPresentation(String nickname) {
            synchronized (presentationLock) {
                presentationCutovers.remove(nickname);
                return warming.remove(nickname);
            }
        }

        private static String logCommand(String message) {
            return "HOTJOIN_LOG#" + Base64.getEncoder().encodeToString(
                    message.getBytes(StandardCharsets.UTF_8));
        }

        private void send(String nickname, String command) {
            sendBoundary(nickname, command);
        }

        private static String requireNickname(String nickname) {
            String checked = Objects.requireNonNull(nickname, "nickname").trim();
            if (checked.isEmpty()) {
                throw new IllegalArgumentException("nickname is required");
            }
            return checked;
        }

        @Override
        public void close() {
            synchronized (presentationLock) {
                warming.clear();
                presentationCutovers.clear();
            }
            earlyReady.clear();
            if (scheduler != null) scheduler.shutdownNow();
        }

    }

    private static final class ChannelGameTransport implements GameTransport {
        private final GameLaunchContext context;
        private final ConfirmationTracker confirmations = new ConfirmationTracker();
        private final AtomicInteger inboundIds = new AtomicInteger();
        private final CompletableFuture<Void> warmingExitAccepted
                = new CompletableFuture<>();
        private final CompletableFuture<Void> playerExitAccepted
                = new CompletableFuture<>();

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
            context.channel().closeLocalHostConnection();
        }

        void acceptHostExit(boolean warming) {
            (warming ? warmingExitAccepted : playerExitAccepted)
                    .complete(null);
        }

        @Override public void awaitHostExitAcceptance(boolean warming) {
            try {
                (warming ? warmingExitAccepted : playerExitAccepted)
                        .get(15L, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Interrupted while awaiting host exit acceptance",
                        interrupted);
            } catch (java.util.concurrent.ExecutionException
                    | java.util.concurrent.TimeoutException failure) {
                throw new IllegalStateException(
                        "Host did not accept the table exit", failure);
            }
        }
    }
}
