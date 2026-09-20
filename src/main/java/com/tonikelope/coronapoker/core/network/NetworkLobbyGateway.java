package com.tonikelope.coronapoker.core.network;

import com.tonikelope.coronapoker.core.ApplicationMetadata;
import com.tonikelope.coronapoker.core.LobbyChatMessage;
import com.tonikelope.coronapoker.core.audio.VoiceWavContract;
import com.tonikelope.coronapoker.core.LobbyCommand;
import com.tonikelope.coronapoker.core.LobbyParticipant;
import com.tonikelope.coronapoker.core.LobbySession;
import com.tonikelope.coronapoker.core.LobbySnapshot;
import com.tonikelope.coronapoker.core.NewGameRequest;
import com.tonikelope.coronapoker.core.NewGameSessionGateway;
import com.tonikelope.coronapoker.core.NewGameTableDraft;
import com.tonikelope.coronapoker.core.RecoverableGameRepository;
import com.tonikelope.coronapoker.core.identity.PlayerIdentity;
import com.tonikelope.coronapoker.core.game.GameChannel;
import com.tonikelope.coronapoker.core.game.GameConfigCodecV1;
import com.tonikelope.coronapoker.core.game.GameLaunchContext;
import com.tonikelope.coronapoker.core.game.GameTableFactory;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Native, UI-free implementation of the existing CoronaPoker lobby wire. */
public final class NetworkLobbyGateway implements NewGameSessionGateway, AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(NetworkLobbyGateway.class.getName());
    static final byte[] MAGIC = java.util.HexFormat.of().parseHex("5c1f158dd9855cc9");
    static final int HANDSHAKE_TIMEOUT_MS = 30_000;
    static final int MAX_PUBLIC_KEY_BYTES = 256;
    static final int MAX_SESSION_ID_BYTES = 64;
    static final int MAX_COMMAND_BYTES = 16 * 1024 * 1024;
    static final int MAX_VOICE_BYTES = 320 * 1024;
    static final long GAME_CONFIRMATION_TIMEOUT_MS = 10_000L;
    static final int GAME_OUTBOX_MAX_ELEMENTS = 10_000;
    static final long GAME_OUTBOX_MAX_BYTES = 16L * 1024L * 1024L;
    static final long HEARTBEAT_INTERVAL_MS = 5_000L;
    static final long HEARTBEAT_REPLY_TIMEOUT_MS = 10_000L;
    static final long HEARTBEAT_WRITE_TIMEOUT_MS = 60_000L;
    static final int MAX_CONSECUTIVE_HEARTBEAT_FAILURES = 3;
    /** Same definitive peer-loss grace as the established Swing transport. */
    static final long HOST_PEER_RECONNECT_TIMEOUT_MS = 45_000L;
    static final long CLIENT_RECONNECT_TIMEOUT_MS = 80_000L;
    static final long CLIENT_RECONNECT_RETRY_MS = 1_000L;
    static final long EXECUTOR_CLOSE_TIMEOUT_MS = 5_000L;
    private static final AtomicInteger THREAD_NUMBER = new AtomicInteger();
    private static final SecureRandom HEARTBEAT_RANDOM = new SecureRandom();

    private final Path coronaDirectory;
    private final GameTableFactory gameTables;
    private final RecoverableGameRepository recoverableGames;
    private final ExecutorService executor;
    private final Set<Thread> workerThreads = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();

    public NetworkLobbyGateway(Path coronaDirectory) {
        this(coronaDirectory, GameTableFactory.unavailable(), null);
    }

    public NetworkLobbyGateway(Path coronaDirectory, GameTableFactory gameTables) {
        this(coronaDirectory, gameTables, null);
    }

    public NetworkLobbyGateway(Path coronaDirectory, GameTableFactory gameTables,
            RecoverableGameRepository recoverableGames) {
        this.coronaDirectory = Objects.requireNonNull(coronaDirectory, "coronaDirectory")
                .toAbsolutePath().normalize();
        this.gameTables = Objects.requireNonNull(gameTables, "gameTables");
        this.recoverableGames = recoverableGames;
        ThreadFactory threads = task -> {
            Thread thread = new Thread(() -> {
                Thread worker = Thread.currentThread();
                workerThreads.add(worker);
                try {
                    task.run();
                } finally {
                    workerThreads.remove(worker);
                }
            }, "coronapoker-network-" + THREAD_NUMBER.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        executor = Executors.newCachedThreadPool(threads);
    }

    public static NetworkLobbyGateway forCurrentUser() {
        return new NetworkLobbyGateway(Path.of(System.getProperty("user.home"), ".coronapoker"));
    }

    public static NetworkLobbyGateway forCurrentUser(GameTableFactory gameTables) {
        return new NetworkLobbyGateway(Path.of(System.getProperty("user.home"), ".coronapoker"),
                gameTables);
    }

    public static NetworkLobbyGateway forCurrentUser(GameTableFactory gameTables,
            RecoverableGameRepository recoverableGames) {
        return new NetworkLobbyGateway(
                Path.of(System.getProperty("user.home"), ".coronapoker"),
                gameTables, recoverableGames);
    }

    @Override
    public CompletableFuture<LobbySession> open(NewGameRequest request) {
        Objects.requireNonNull(request, "request");
        if (closed.get()) return CompletableFuture.failedFuture(new IllegalStateException("Network gateway is closed"));
        return CompletableFuture.supplyAsync(() -> {
            try {
                return request.joining() ? Transport.openClient(request, coronaDirectory, executor, gameTables)
                        : Transport.openHost(request, coronaDirectory, executor,
                                gameTables, recoverableGames);
            } catch (Exception failure) {
                throw new java.util.concurrent.CompletionException(failure);
            }
        }, executor);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        executor.shutdownNow();
        if (workerThreads.contains(Thread.currentThread())) return;
        try {
            executor.awaitTermination(EXECUTOR_CLOSE_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class Transport implements AutoCloseable {
        private final boolean host;
        private final String localNickname;
        private final String endpoint;
        private final String serverAddress;
        private final int serverPort;
        private final Path coronaDirectory;
        private final ExecutorService executor;
        private volatile NewGameTableDraft.Settings tableSettings;
        private final GameTableFactory gameTables;
        private final NativeGameChannel gameChannel;
        private final boolean recovering;
        private final int recoveryGameId;
        private final byte[] sessionId;
        private final PlayerIdentity identity;
        private volatile GameConfigCodecV1.Configuration launchConfiguration;
        private final Map<String, Peer> peers = new LinkedHashMap<>();
        private final List<LobbyChatMessage> chat = new ArrayList<>();
        private final AtomicLong chatSequence = new AtomicLong();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicBoolean clientReconnectRunning = new AtomicBoolean();
        private volatile String password;
        private volatile ServerSocket serverSocket;
        private volatile UpnpPortMapping upnpMapping;
        private volatile Connection serverConnection;
        private volatile LobbySession session;
        private String serverNickname;
        private boolean chatNotifications = true;

        private Transport(boolean host, NewGameRequest request, Path coronaDirectory,
                ExecutorService executor, byte[] sessionId, PlayerIdentity identity,
                NewGameTableDraft.Settings tableSettings, GameTableFactory gameTables) {
            this.host = host;
            this.localNickname = request.connection().nickname();
            this.endpoint = request.connection().server() + ":" + request.connection().port();
            this.serverAddress = request.connection().server();
            this.serverPort = parsePort(request.connection().port());
            this.password = emptyToNull(request.connection().password());
            this.coronaDirectory = coronaDirectory;
            this.executor = executor;
            this.tableSettings = Objects.requireNonNull(tableSettings, "tableSettings");
            this.gameTables = Objects.requireNonNull(gameTables, "gameTables");
            this.recovering = request.connection().recover();
            this.recoveryGameId = request.connection().recoveredGameId() == null
                    ? -1 : request.connection().recoveredGameId();
            this.sessionId = sessionId;
            this.identity = identity;
            this.serverNickname = host ? localNickname : "";
            this.gameChannel = new NativeGameChannel(this, executor);
        }

        static LobbySession openHost(NewGameRequest request, Path directory,
                ExecutorService executor, GameTableFactory gameTables,
                RecoverableGameRepository recoverableGames) throws Exception {
            PlayerIdentity identity = PlayerIdentity.loadOrCreate(directory, request.connection().nickname());
            byte[] sessionId = new byte[16];
            new SecureRandom().nextBytes(sessionId);
            Transport transport = new Transport(true, request, directory, executor, sessionId,
                    identity, request.table(), gameTables);
            int port = parsePort(request.connection().port());
            ServerSocket server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(port));
            transport.serverSocket = server;
            String initialDetail = "";
            if (request.connection().upnp()) {
                UpnpPortMapping.Attempt attempt =
                        UpnpPortMapping.openSystemTcp(port);
                if (attempt.opened()) {
                    transport.upnpMapping = attempt.lease();
                    initialDetail = "UPNP_OK";
                    LOGGER.log(Level.INFO,
                            "UPnP mapping opened for TCP port {0}", port);
                } else {
                    initialDetail = "UPNP_ERROR";
                    LOGGER.log(Level.WARNING,
                            "UPnP mapping failed for TCP port {0}: {1}",
                            new Object[]{port, attempt.status()});
                }
            }
            transport.peers.put(transport.localNickname,
                    Peer.local(transport.localNickname, request.connection().avatar(), true,
                            identity.publicKey(), identity.signJoin(sessionId)));
            if (request.connection().recover() && recoverableGames != null) {
                for (String nickname : recoverableGames.activeBotNicknames(
                        transport.recoveryGameId)) {
                    if (transport.peers.size() >= LobbySnapshot.MAX_PARTICIPANTS) {
                        throw new IllegalStateException(
                                "Recovered bot roster exceeds table capacity");
                    }
                    if (transport.peers.putIfAbsent(nickname,
                            new Peer(nickname, null, false, false, true, true,
                                    null, null, null)) != null) {
                        throw new IllegalStateException(
                                "Recovered bot duplicates a lobby participant");
                    }
                }
            }
            LobbySession session = new LobbySession(transport.snapshot(
                    LobbySnapshot.Phase.WAITING_FOR_PLAYERS, initialDetail),
                    transport::submit, transport);
            transport.session = session;
            executor.execute(transport::acceptLoop);
            return session;
        }

        static LobbySession openClient(NewGameRequest request, Path directory,
                ExecutorService executor, GameTableFactory gameTables) throws Exception {
            PlayerIdentity identity = PlayerIdentity.loadOrCreate(directory, request.connection().nickname());
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(request.connection().server(),
                    parsePort(request.connection().port())), HANDSHAKE_TIMEOUT_MS);
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            socket.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
            Connection connection = clientHandshake(socket, request, identity, directory, executor);
            Transport transport = new Transport(false, request, directory, executor,
                    connection.sessionId, identity,
                    NewGameTableDraft.Settings.parseWire(connection.gameConfig), gameTables);
            transport.serverConnection = connection;
            transport.serverNickname = connection.remoteNickname;
            transport.peers.put(connection.remoteNickname,
                    new Peer(connection.remoteNickname, connection.remoteAvatar, false, true,
                            false, connection.secure, connection,
                            connection.remoteIdentityPublicKey,
                            connection.remoteIdentitySignature));
            transport.peers.put(transport.localNickname,
                    Peer.local(transport.localNickname, request.connection().avatar(), false,
                            identity.publicKey(), identity.signJoin(connection.sessionId)));
            LobbySession session = new LobbySession(transport.snapshot(
                    LobbySnapshot.Phase.CONNECTED, ""), transport::submit, transport);
            transport.session = session;
            socket.setSoTimeout(0);
            executor.execute(() -> transport.readClient(connection));
            connection.startHeartbeat(transport::publishCurrent);
            return session;
        }

        private static Connection clientHandshake(Socket socket, NewGameRequest request,
                PlayerIdentity identity, Path directory,
                ExecutorService executor) throws Exception {
            InputStream input = new BufferedInputStream(socket.getInputStream());
            OutputStream output = new BufferedOutputStream(socket.getOutputStream());
            output.write(MAGIC);
            output.flush();
            KeyPair pair = ecPair();
            DataOutputStream dataOut = new DataOutputStream(output);
            dataOut.writeInt(pair.getPublic().getEncoded().length);
            dataOut.write(pair.getPublic().getEncoded());
            dataOut.flush();
            DataInputStream dataIn = new DataInputStream(input);
            byte[] remotePublic = readBounded(dataIn, MAX_PUBLIC_KEY_BYTES, "server public key");
            byte[] sessionId = readBounded(dataIn, MAX_SESSION_ID_BYTES, "session id");
            SecretKeySpec[] keys = keys(pair, remotePublic, request.connection().password());
            Connection connection = new Connection(socket, input, output, keys[0], keys[1],
                    GameCommandType.Direction.HOST_TO_CLIENT, executor);
            connection.sessionId = sessionId;
            byte[] avatar = readAvatar(request.connection().avatar());
            String join = b64(request.connection().nickname()) + "#" + ApplicationMetadata.VERSION
                    + "#" + (avatar == null ? "*" : Base64.getEncoder().encodeToString(avatar))
                    + "#JOIN#" + Base64.getEncoder().encodeToString(identity.publicKey())
                    + "#" + Base64.getEncoder().encodeToString(identity.signJoin(sessionId));
            connection.writeEncrypted(join);
            String response = connection.readEncryptedText();
            if (response == null) throw new IOException("Secure channel not established");
            String[] parts = response.split("#", -1);
            if (!"NICKOK".equals(parts[0])) throw rejection(parts);
            connection.gameInfo = parts.length > 2 ? text64(parts[2]) : "";
            connection.gameConfig = parts.length > 3 ? text64(parts[3]) : "";
            String intro = connection.readEncryptedText();
            String history = connection.readEncryptedText();
            if (intro == null || history == null) throw new IOException("Incomplete lobby handshake");
            String[] identityParts = intro.split("#", -1);
            connection.remoteNickname = text64(identityParts[0]);
            connection.remoteAvatar = identityParts.length > 1
                    ? saveAvatar(identityParts[1], connection.remoteNickname, directory) : null;
            if (identityParts.length >= 4 && !"*".equals(identityParts[2]) && !"*".equals(identityParts[3])) {
                byte[] key = Base64.getDecoder().decode(identityParts[2]);
                byte[] signature = Base64.getDecoder().decode(identityParts[3]);
                connection.secure = PlayerIdentity.verifyJoin(sessionId,
                        connection.remoteNickname, key, signature);
                if (connection.secure) {
                    connection.remoteIdentityPublicKey = key;
                    connection.remoteIdentitySignature = signature;
                }
            }
            return connection;
        }

        private Connection clientReconnectHandshake(Connection established)
                throws Exception {
            if (closed.get() || gameChannel.isClosed()) {
                throw new IOException("Table closed before reconnect");
            }
            Socket socket = new Socket();
            Connection candidate = null;
            try {
                socket.connect(new InetSocketAddress(serverAddress, serverPort),
                        HANDSHAKE_TIMEOUT_MS);
                if (closed.get() || gameChannel.isClosed()) {
                    throw new IOException("Table closed during reconnect");
                }
                socket.setTcpNoDelay(true);
                socket.setKeepAlive(true);
                socket.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
                InputStream input = new BufferedInputStream(
                        socket.getInputStream());
                OutputStream output = new BufferedOutputStream(
                        socket.getOutputStream());
                output.write(MAGIC);
                output.flush();

                KeyPair pair = ecPair();
                DataOutputStream dataOut = new DataOutputStream(output);
                byte[] publicKey = pair.getPublic().getEncoded();
                dataOut.writeInt(publicKey.length);
                dataOut.write(publicKey);
                dataOut.flush();
                DataInputStream dataIn = new DataInputStream(input);
                byte[] remotePublic = readBounded(dataIn,
                        MAX_PUBLIC_KEY_BYTES, "server public key");
                byte[] receivedSessionId = readBounded(dataIn,
                        MAX_SESSION_ID_BYTES, "session id");
                if (!MessageDigest.isEqual(sessionId, receivedSessionId)) {
                    throw new IOException("Reconnect session id changed");
                }
                SecretKeySpec[] keys = keys(pair, remotePublic, password);
                candidate = new Connection(socket, input, output, keys[0],
                        keys[1], GameCommandType.Direction.HOST_TO_CLIENT,
                        executor, false);
                candidate.sessionId = receivedSessionId;
                candidate.remoteNickname = established.remoteNickname;
                candidate.remoteAvatar = established.remoteAvatar;
                candidate.secure = established.secure;
                candidate.remoteIdentityPublicKey
                        = established.remoteIdentityPublicKey;
                candidate.remoteIdentitySignature
                        = established.remoteIdentitySignature;

                Mac proof = Mac.getInstance("HmacSHA256");
                proof.init(established.originalHmac());
                String proof64 = Base64.getEncoder().encodeToString(
                        proof.doFinal(localNickname.getBytes(
                                StandardCharsets.UTF_8)));
                candidate.writeEncrypted(b64(localNickname) + "#"
                        + ApplicationMetadata.VERSION + "#*#*#" + proof64);
                String ack = candidate.readEncryptedText();
                if (ack == null || !ack.startsWith("RECONNECT_OK")) {
                    throw new IOException("Reconnect denied: " + ack);
                }
                socket.setSoTimeout(0);
                return candidate;
            } catch (Exception failure) {
                if (candidate != null) candidate.close();
                else try { socket.close(); } catch (IOException ignored) { }
                throw failure;
            }
        }

        private void acceptLoop() {
            while (!closed.get()) {
                try {
                    Socket socket = serverSocket.accept();
                    executor.execute(() -> acceptPeer(socket));
                } catch (IOException failure) {
                    if (!closed.get()) fail("No se pudo aceptar la conexión: " + failure.getMessage());
                    return;
                }
            }
        }

        private void acceptPeer(Socket socket) {
            Connection connection = null;
            try {
                socket.setTcpNoDelay(true);
                socket.setKeepAlive(true);
                socket.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
                InputStream input = new BufferedInputStream(socket.getInputStream());
                OutputStream output = new BufferedOutputStream(socket.getOutputStream());
                DataInputStream dataIn = new DataInputStream(input);
                byte[] magic = new byte[MAGIC.length];
                dataIn.readFully(magic);
                if (!MessageDigest.isEqual(MAGIC, magic)) throw new IOException("Bad protocol magic");
                byte[] remotePublic = readBounded(dataIn, MAX_PUBLIC_KEY_BYTES, "client public key");
                KeyPair pair = ecPair();
                DataOutputStream dataOut = new DataOutputStream(output);
                dataOut.writeInt(pair.getPublic().getEncoded().length);
                dataOut.write(pair.getPublic().getEncoded());
                dataOut.writeInt(sessionId.length);
                dataOut.write(sessionId);
                dataOut.flush();
                SecretKeySpec[] keys = keys(pair, remotePublic, password);
                connection = new Connection(socket, input, output, keys[0], keys[1],
                        GameCommandType.Direction.CLIENT_TO_HOST, executor,
                        false);
                String join = connection.readEncryptedText();
                if (join == null) throw new IOException("Client closed during handshake");
                String[] parts = join.split("#", -1);
                if (parts.length == 5) {
                    Connection reconnected = acceptReconnect(connection, parts);
                    if (reconnected != null) {
                        connection = null;
                        executor.execute(() -> readHostPeer(reconnected));
                        reconnected.startHeartbeat(this::publishCurrent);
                    }
                    return;
                }
                if (parts.length != 6 || !ApplicationMetadata.VERSION.equals(parts[1])
                        || !"JOIN".equals(parts[3])) {
                    connection.writeEncrypted("BADVERSION#" + ApplicationMetadata.VERSION);
                    return;
                }
                String nickname = text64(parts[0]);
                if (nickname.contains("$")) {
                    connection.writeEncrypted("NICKUNAUTHORIZED");
                    return;
                }
                byte[] publicKey = Base64.getDecoder().decode(parts[4]);
                byte[] signature = Base64.getDecoder().decode(parts[5]);
                if (!PlayerIdentity.verifyJoin(sessionId, nickname, publicKey, signature)) return;
                synchronized (this) {
                    if (closed.get()) return;
                    if (peers.size() >= LobbySnapshot.MAX_PARTICIPANTS) {
                        connection.writeEncrypted("NOSPACE");
                        return;
                    }
                    if (findNormalized(nickname) != null) {
                        connection.writeEncrypted("NICKFAIL");
                        return;
                    }
                    connection.remoteNickname = nickname;
                    Path avatar = saveAvatar(parts[2], nickname, coronaDirectory);
                    connection.remoteAvatar = avatar;
                    connection.secure = true;
                    connection.writeEncrypted("NICKOK#" + (password == null ? "0" : "1")
                            + "#" + b64(tableSettings.gameInfoForWire())
                            + "#" + b64(tableSettings.serializeForWire()));
                    byte[] hostAvatar = readAvatar(peers.get(localNickname).avatar);
                    connection.writeEncrypted(b64(localNickname) + "#"
                            + (hostAvatar == null ? "*" : Base64.getEncoder().encodeToString(hostAvatar))
                            + "#" + Base64.getEncoder().encodeToString(identity.publicKey())
                            + "#" + Base64.getEncoder().encodeToString(identity.signJoin(sessionId)));
                    connection.writeEncrypted("*");
                    sendUsersList(connection);
                    Peer peer = new Peer(nickname, avatar, false, false, false, true, connection,
                            publicKey, signature);
                    peers.put(nickname, peer);
                    connection.startGameOutbox();
                    addPresence(nickname, LobbyChatMessage.Type.PLAYER_JOINED);
                    publish(LobbySnapshot.Phase.WAITING_FOR_PLAYERS, "");
                    broadcastGame("NEWUSER#" + b64(nickname) + "#0#"
                            + (parts[2].isBlank() ? "*" : parts[2]) + "#" + parts[4] + "#" + parts[5], connection);
                }
                socket.setSoTimeout(0);
                Connection accepted = connection;
                executor.execute(() -> readHostPeer(accepted));
                accepted.startHeartbeat(this::publishCurrent);
                connection = null;
            } catch (Exception ignored) {
                // Rejection or malformed unauthenticated handshake: close without an oracle.
            } finally {
                if (connection != null) connection.close();
            }
        }

        private Connection acceptReconnect(Connection candidate, String[] parts)
                throws Exception {
            String nickname = text64(parts[0]);
            if (!ApplicationMetadata.VERSION.equals(parts[1])
                    || !"*".equals(parts[2]) || !"*".equals(parts[3])) {
                candidate.writeEncrypted("RECONNECT_DENIED#BAD_VERSION");
                return null;
            }
            synchronized (this) {
                Peer existing = findNormalized(nickname);
                if (closed.get() || existing == null || existing.local
                        || existing.bot || existing.connection == null) {
                    candidate.writeEncrypted("RECONNECT_DENIED#UNKNOWN_NICK");
                    return null;
                }
                Mac proof = Mac.getInstance("HmacSHA256");
                proof.init(existing.connection.originalHmac());
                byte[] expected = proof.doFinal(nickname.getBytes(
                        StandardCharsets.UTF_8));
                byte[] received;
                try {
                    received = Base64.getDecoder().decode(parts[4]);
                } catch (IllegalArgumentException invalid) {
                    candidate.writeEncrypted("RECONNECT_DENIED#BAD_HMAC");
                    return null;
                }
                if (!MessageDigest.isEqual(expected, received)) {
                    candidate.writeEncrypted("RECONNECT_DENIED#BAD_HMAC");
                    return null;
                }
                candidate.remoteNickname = existing.nickname;
                candidate.remoteAvatar = existing.avatar;
                candidate.secure = existing.secure;
                candidate.remoteIdentityPublicKey = existing.identityPublicKey;
                candidate.remoteIdentitySignature = existing.identitySignature;
                candidate.currentGeneration().socket.setSoTimeout(0);
                existing.connection.adopt(candidate);
                existing.connection.writeEncrypted("RECONNECT_OK");
                publishCurrent();
                return existing.connection;
            }
        }

        private CompletableFuture<Void> submit(LobbyCommand command) {
            return CompletableFuture.runAsync(() -> {
                try {
                    if (command instanceof LobbyCommand.SendText text) sendChat(text.text());
                    else if (command instanceof LobbyCommand.SendImage image) {
                        sendChat(encodeChatImageUrl(image.url()));
                    }
                    else if (command instanceof LobbyCommand.SendVoice voice) sendVoice(voice.wav());
                    else if (command instanceof LobbyCommand.AddBot) addBot();
                    else if (command instanceof LobbyCommand.Kick kick) kick(kick.nickname());
                    else if (command instanceof LobbyCommand.ChangePassword change) changePassword(change.password());
                    else if (command instanceof LobbyCommand.UpdateTableSettings update) {
                        updateTableSettings(update.settings());
                    }
                    else if (command instanceof LobbyCommand.SetChatNotifications notifications) setNotifications(notifications.enabled());
                    else if (command instanceof LobbyCommand.StartGame) startGame();
                    else if (command instanceof LobbyCommand.Leave) leave();
                } catch (Exception failure) {
                    throw new java.util.concurrent.CompletionException(failure);
                }
            }, executor);
        }

        private synchronized void startGame() throws Exception {
            if (!host) throw new IllegalStateException("Only the host can start the game");
            publish(LobbySnapshot.Phase.INITIALIZING_GAME, "");
            try {
                publishTableSession();
            } catch (Exception failure) {
                publish(LobbySnapshot.Phase.ERROR, failure.getMessage() == null
                        ? failure.getClass().getSimpleName() : failure.getMessage());
                throw failure;
            }
        }

        private void publishTableSession() throws Exception {
            LobbySession active = session;
            if (active == null) throw new IllegalStateException("Lobby session is not ready");
            active.publishTableSession(gameTables.create(
                    new GameLaunchContext(active.snapshot(), gameChannel, identity,
                            Base64.getEncoder().encodeToString(sessionId),
                            coronaDirectory, password, launchConfiguration,
                            recoveryGameId)));
        }

        private synchronized void sendChat(String text) throws Exception {
            String wire = "CHAT#" + b64(localNickname) + "#" + b64(text);
            if (host) broadcastDirect(wire, null); else serverConnection.writeEncrypted(wire);
            addChat(localNickname, text);
            publishCurrent();
        }

        private synchronized void sendVoice(byte[] wav) throws Exception {
            String invalidVoice = VoiceWavContract.validationError(wav);
            if (invalidVoice != null) {
                throw new IllegalArgumentException("Nota de voz no válida: "
                        + invalidVoice);
            }
            byte[] payload = BinaryPayloadCodec.encode(BinaryPayloadCodec.TYPE_VOICE, localNickname, wav);
            if (host) broadcastBinary(payload, null); else serverConnection.writeEncryptedBinary(payload);
            chat.add(new LobbyChatMessage(chatSequence.getAndIncrement(), Instant.now(), localNickname,
                    LobbyChatMessage.Type.VOICE, Base64.getEncoder().encodeToString(wav)));
            publishCurrent();
        }

        private synchronized void addBot() throws Exception {
            if (!host) throw new IllegalStateException("Only the host can add bots");
            int number = 1;
            String nickname;
            do nickname = "CoronaBot$" + number++; while (peers.containsKey(nickname));
            peers.put(nickname, new Peer(nickname, null, false, false, true, true, null,
                    null, null));
            addPresence(nickname, LobbyChatMessage.Type.PLAYER_JOINED);
            broadcastGame("NEWUSER#" + b64(nickname) + "#0", null);
            publishCurrent();
        }

        private synchronized void kick(String nickname) throws Exception {
            if (!host) {
                throw new IllegalStateException(
                        "Only the host can kick participants");
            }
            Peer peer = peers.get(nickname);
            if (peer == null || peer.local || peer.host) throw new IllegalArgumentException("Unknown participant");
            if (peer.connection != null) peer.connection.writeEncrypted("KICKED");
            removePeer(nickname, true);
        }

        private synchronized void changePassword(String next) throws Exception {
            if (!host) {
                throw new IllegalStateException(
                        "Only the host can change the room password");
            }
            password = emptyToNull(next);
            broadcastDirect("NEWPASS#" + (password == null ? "*" : b64(password)), null);
        }

        private synchronized void updateTableSettings(
                NewGameTableDraft.Settings next) throws Exception {
            if (!host) {
                throw new IllegalStateException(
                        "Only the host can update table settings");
            }
            tableSettings = Objects.requireNonNull(next, "table settings");
            broadcastGame("GAMEINFO#" + b64(next.gameInfoForWire()), null);
            broadcastGame("GAMECONFIG#" + b64(next.serializeForWire()), null);
            publishCurrent();
        }

        private synchronized void setNotifications(boolean enabled) {
            chatNotifications = enabled;
            publishCurrent();
        }

        private void leave() {
            // Leaving is terminal locally. A failed write to one stale peer
            // must never prevent the UI from closing, nor prevent the other
            // peers from receiving the graceful EXIT marker. This used to
            // bubble the first IOException out of the command future and left
            // the GDX screen apparently ignoring the confirmed exit.
            if (host) {
                for (Peer peer : List.copyOf(peers.values())) {
                    if (peer.connection == null) continue;
                    try {
                        peer.connection.writeEncrypted("EXIT");
                    } catch (Exception failure) {
                        LOGGER.log(Level.WARNING,
                                "Could not deliver graceful host exit to "
                                + peer.nickname, failure);
                    }
                }
            } else if (serverConnection != null) {
                try {
                    serverConnection.writeEncrypted("EXIT");
                } catch (Exception failure) {
                    LOGGER.log(Level.WARNING,
                            "Could not deliver graceful client exit",
                            failure);
                }
            }
            publish(LobbySnapshot.Phase.CLOSED, "");
            closeAfterGracefulExit();
        }

        private void closeAfterGracefulExit() {
            if (!closed.compareAndSet(false, true)) return;
            gameChannel.close();
            closeUpnpMappingAsync();
            try {
                if (serverSocket != null) serverSocket.close();
            } catch (IOException ignored) { }

            // EXIT has no acknowledgement in the legacy Swing protocol. Keep
            // the already-flushed sockets alive for one short drain window so
            // the peer reader can consume the terminal marker before EOF. An
            // immediate close can race that marker on Windows and make both
            // Swing and native clients misclassify a normal exit as a drop.
            executor.execute(() -> {
                try {
                    Thread.sleep(250L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } finally {
                    closeConnections();
                }
            });
        }

        private void readHostPeer(Connection connection) {
            Connection.Generation generation = connection.currentGeneration();
            try {
                readLoop(connection, generation, true);
            } finally {
                handleHostGenerationEnd(connection, generation);
            }
        }

        private void readClient(Connection connection) {
            Connection.Generation generation = connection.currentGeneration();
            try {
                readLoop(connection, generation, false);
            } finally {
                handleClientGenerationEnd(connection, generation);
            }
        }

        private void handleHostGenerationEnd(Connection connection,
                Connection.Generation generation) {
            long reconnectAttempt = connection.markGenerationDown(generation);
            if (closed.get() || reconnectAttempt < 0L) {
                return;
            }
            synchronized (this) {
                Peer peer = connection.remoteNickname == null ? null
                        : findNormalized(connection.remoteNickname);
                if (peer == null || peer.connection != connection) return;
                LobbySession active = session;
                boolean gameActive = active != null
                        && active.snapshot().startingOrStarted();
                if (gameActive) {
                    publishCurrent();
                    startHostPeerLossWatchdog(connection, reconnectAttempt);
                } else {
                    try {
                        removePeer(connection.remoteNickname, true);
                    } catch (Exception ignored) { }
                }
            }
        }

        private void startHostPeerLossWatchdog(Connection connection,
                long reconnectAttempt) {
            try {
                executor.execute(() -> {
                    try {
                        Thread.sleep(HOST_PEER_RECONNECT_TIMEOUT_MS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    if (closed.get()
                            || !connection.closeIfStillReconnecting(reconnectAttempt)) {
                        return;
                    }
                    String nickname = connection.remoteNickname;
                    LOGGER.log(Level.WARNING,
                            "Native peer {0} did not reconnect within {1}ms; declaring definitive loss",
                            new Object[]{nickname, HOST_PEER_RECONNECT_TIMEOUT_MS});
                    gameChannel.publishPeerLoss(nickname);
                    publishCurrent();
                });
            } catch (RejectedExecutionException rejected) {
                if (!executor.isShutdown()) throw rejected;
            }
        }

        private void handleClientGenerationEnd(Connection connection,
                Connection.Generation generation) {
            if (closed.get() || connection.markGenerationDown(generation) < 0L) {
                return;
            }
            LobbySession active = session;
            if (gameChannel.isClosed()) {
                // The renderer-neutral dealer has already crossed its
                // terminal CloseTable barrier. EOF now belongs to the normal
                // table teardown, not to transient reconnection. Publishing a
                // reconnecting phase here stranded the final screen/session
                // after an otherwise completely successful game.
                publish(LobbySnapshot.Phase.CLOSED, "");
                return;
            }
            if (active != null && active.snapshot().startingOrStarted()) {
                publish(LobbySnapshot.Phase.RECONNECTING, "");
                startClientReconnect(connection);
            } else {
                fail("Se ha perdido la conexión con el servidor");
            }
        }

        private void startClientReconnect(Connection connection) {
            if (!clientReconnectRunning.compareAndSet(false, true)) return;
            executor.execute(() -> {
                long deadline = System.nanoTime()
                        + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(
                                CLIENT_RECONNECT_TIMEOUT_MS);
                try {
                    // A normal table close and the server socket EOF are very
                    // close together. Let the terminal CloseTable barrier
                    // close the game channel before classifying the EOF as a
                    // transient network failure.
                    try {
                        Thread.sleep(100L);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    while (!closed.get() && connection.isReconnecting()
                            && !gameChannel.isClosed()
                            && System.nanoTime() < deadline) {
                        Connection candidate = null;
                        try {
                            candidate = clientReconnectHandshake(connection);
                            connection.adopt(candidate);
                            candidate = null;
                            serverConnection = connection;
                            publish(LobbySnapshot.Phase.IN_GAME, "");
                            executor.execute(() -> readClient(connection));
                            connection.startHeartbeat(this::publishCurrent);
                            return;
                        } catch (Exception failure) {
                            if (candidate != null) candidate.close();
                            LOGGER.log(Level.FINE,
                                    "Native client reconnect attempt failed",
                                    failure);
                        }
                        try {
                            Thread.sleep(CLIENT_RECONNECT_RETRY_MS);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                    if (gameChannel.isClosed()) {
                        connection.close();
                        return;
                    }
                    if (!closed.get() && connection.isReconnecting()) {
                        fail("No se pudo recuperar la conexión con el servidor");
                    }
                } finally {
                    clientReconnectRunning.set(false);
                }
            });
        }

        private void readLoop(Connection connection,
                Connection.Generation generation, boolean fromClient) {
            try {
                while (!closed.get() && connection.isCurrent(generation)) {
                    WireFrameCodec.Frame frame = WireFrameCodec.read(
                            generation.input, MAX_COMMAND_BYTES);
                    if (frame == null) return;
                    if (frame.isBinary()) {
                        byte[] clear = SecureChannelCodec.decryptBytes(
                                frame.binary(), generation.aes,
                                generation.hmac);
                        if (clear != null) receiveBinary(connection, clear, fromClient);
                        continue;
                    }
                    String command;
                    try {
                        command = SecureChannelCodec.decryptCommand(
                                frame.text(), generation.aes,
                                generation.hmac);
                    } catch (java.security.KeyException invalid) {
                        continue;
                    }
                    if (command != null) receiveText(connection, generation,
                            command, fromClient);
                }
            } catch (Exception failure) {
                if (!closed.get() && connection.isCurrent(generation)) {
                    LOGGER.log(Level.WARNING, "Authenticated lobby channel failed for "
                            + connection.remoteNickname, failure);
                }
            }
        }

        private synchronized void receiveText(Connection source,
                Connection.Generation generation, String command,
                boolean fromClient) throws Exception {
            String[] parts = command.split("#", -1);
            switch (parts[0]) {
                case "PING" -> {
                    int ping = Integer.parseInt(parts[1]);
                    // Swing's heartbeat deliberately measures two paths. Its socket reader
                    // returns PONG immediately and its command consumer returns PONG2; a peer
                    // is healthy only when both arrive. The native gateway has one compact
                    // dispatcher for both paths, but it must preserve that established wire
                    // contract or a Swing peer ejects an otherwise healthy GDX peer after
                    // three rounds (roughly 45 seconds).
                    source.writePlain("PONG#" + (ping + 1));
                    source.writePlain("PONG2#" + (ping + 2));
                }
                case "PONG", "PONG2" -> source.recordHeartbeatReply(
                        generation, parts[0], Integer.parseInt(parts[1]));
                case "CONF" -> source.confirm(generation,
                        Integer.parseInt(parts[1]));
                case "EXIT" -> {
                    if (fromClient) removePeer(source.remoteNickname, true);
                    else { publish(LobbySnapshot.Phase.CLOSED, "El servidor ha cancelado la timba"); close(); }
                }
                case "KICKED" -> { publish(LobbySnapshot.Phase.CLOSED, "Has sido expulsado de la timba"); close(); }
                case "NEWPASS" -> { if (!fromClient && parts.length > 1) password = "*".equals(parts[1]) ? null : text64(parts[1]); }
                case "CHAT" -> {
                    String nickname = fromClient ? source.remoteNickname : text64(parts[1]);
                    String text = parts.length == 3 ? text64(parts[2]) : "";
                    if (fromClient) broadcastDirect("CHAT#" + b64(nickname) + "#" + b64(text), source);
                    addChat(nickname, text);
                    publishCurrent();
                }
                case "GAME" -> {
                    if (parts.length < 3) throw new IOException("Malformed GAME frame");
                    int id = Integer.parseInt(parts[1]);
                    String subcommand = parts[2];
                    GameCommandGate.Decision decision = source.gameCommandGate.accept(
                            subcommand, id, command);
                    boolean lobbyCommand = isLobbyGameCommand(subcommand);
                    LobbySession activeSession = session;
                    boolean gameActive = activeSession != null
                            && activeSession.snapshot().startingOrStarted();
                    if (decision.closeConnection()
                            || (fromClient && lobbyCommand)
                            || (!lobbyCommand && !gameActive)) {
                        throw new IOException("Unsupported GAME command for current phase: "
                                + subcommand + " [phase="
                                + (activeSession == null ? "NO_SESSION"
                                        : activeSession.snapshot().phase())
                                + ", direction="
                                + (fromClient ? "CLIENT_TO_HOST"
                                        : "HOST_TO_CLIENT")
                                + ", registered=" + !decision.closeConnection()
                                + ", lobbyCommand=" + lobbyCommand + "]");
                    }
                    if (decision.acknowledge()) {
                        source.writeEncrypted("CONF#" + (id + 1) + "#OK");
                    }
                    if (decision.enqueue()) {
                        if (lobbyCommand) receiveGame(parts);
                        if (gameActive || "INIT".equals(subcommand)) {
                            if ("INIT".equals(subcommand) && !host) {
                                if (parts.length != 4) {
                                    throw new IOException("Malformed INIT configuration");
                                }
                                GameConfigCodecV1.Result decoded
                                        = GameConfigCodecV1.decodeBase64(parts[3]);
                                if (!decoded.isOk()) {
                                    throw new IOException("Invalid INIT configuration: "
                                            + decoded.error());
                                }
                                launchConfiguration = decoded.value();
                                publish(LobbySnapshot.Phase.IN_GAME, "");
                                publishTableSession();
                            }
                            gameChannel.receive(source.remoteNickname,
                                    command.substring(command.indexOf('#', command.indexOf('#') + 1) + 1));
                        }
                    }
                }
                default -> { }
            }
        }

        private void receiveGame(String[] parts) throws Exception {
            switch (parts[2]) {
                case "NEWUSER" -> {
                    String nickname = text64(parts[3]);
                    if (findNormalized(nickname) == null) {
                        boolean bot = nickname.startsWith("CoronaBot$");
                        Path avatar = parts.length > 5 ? saveAvatar(parts[5], nickname, coronaDirectory) : null;
                        Peer peer = remotePeer(nickname, avatar, false, bot,
                                parts.length > 4 && "1".equals(parts[4]),
                                parts.length > 6 ? parts[6] : "*",
                                parts.length > 7 ? parts[7] : "*");
                        peers.put(nickname, peer);
                        addPresence(nickname, LobbyChatMessage.Type.PLAYER_JOINED);
                        publishCurrent();
                    }
                }
                case "USERSLIST" -> {
                    if (parts.length > 3) for (String encoded : parts[3].split("@")) {
                        if (encoded.isBlank()) continue;
                        String[] user = encoded.split("\\|", -1);
                        String nickname = text64(user[0]);
                        if (findNormalized(nickname) == null) {
                            boolean bot = nickname.startsWith("CoronaBot$");
                            Path avatar = user.length > 2 ? saveAvatar(user[2], nickname, coronaDirectory) : null;
                            Peer peer = remotePeer(nickname, avatar, false, bot,
                                    user.length > 1 && "1".equals(user[1]),
                                    user.length > 3 ? user[3] : "*",
                                    user.length > 4 ? user[4] : "*");
                            peers.put(nickname, peer);
                        }
                    }
                    publishCurrent();
                }
                case "DELUSER" -> removePeer(text64(parts[3]), false);
                case "GAMEINFO" -> { }
                case "GAMECONFIG" -> {
                    if (parts.length != 4) {
                        throw new IOException("Malformed GAMECONFIG frame");
                    }
                    tableSettings = NewGameTableDraft.Settings.parseWire(
                            text64(parts[3]));
                    publishCurrent();
                }
                case "INIT" -> { }
                default -> { }
            }
        }

        private static boolean isLobbyGameCommand(String command) {
            return switch (command) {
                case "NEWUSER", "USERSLIST", "DELUSER", "GAMEINFO", "GAMECONFIG",
                        "INIT", "YOUARELATE", "SERVEREXIT", "SERVEREXITRECOVER" -> true;
                default -> false;
            };
        }

        private synchronized void receiveBinary(Connection source, byte[] clear,
                boolean fromClient) throws Exception {
            BinaryPayloadCodec.Payload payload = BinaryPayloadCodec.decode(clear);
            if (payload.type() != BinaryPayloadCodec.TYPE_VOICE
                    || !VoiceWavContract.isValid(payload.body())) return;
            String nickname = fromClient ? source.remoteNickname : payload.nickname();
            if (fromClient) broadcastBinary(BinaryPayloadCodec.encode(payload.type(), nickname, payload.body()), source);
            chat.add(new LobbyChatMessage(chatSequence.getAndIncrement(), Instant.now(), nickname,
                    LobbyChatMessage.Type.VOICE, Base64.getEncoder().encodeToString(payload.body())));
            publishCurrent();
        }

        private void sendUsersList(Connection newcomer) throws Exception {
            StringBuilder users = new StringBuilder("USERSLIST#");
            for (Peer peer : peers.values()) {
                if (peer.local || peer.connection == newcomer) continue;
                if (users.length() > "USERSLIST#".length()) users.append('@');
                byte[] avatar = readAvatar(peer.avatar);
                users.append(b64(peer.nickname)).append('|').append(peer.secure ? '0' : '1')
                        .append('|').append(avatar == null ? "*" : Base64.getEncoder().encodeToString(avatar))
                        .append('|').append(peer.identityPublicKey == null ? "*"
                                : Base64.getEncoder().encodeToString(peer.identityPublicKey))
                        .append('|').append(peer.identitySignature == null ? "*"
                                : Base64.getEncoder().encodeToString(peer.identitySignature));
            }
            if (users.length() > "USERSLIST#".length()) sendGame(newcomer, users.toString());
        }

        private Peer remotePeer(String nickname, Path avatar, boolean peerHost, boolean bot,
                boolean unsecure, String encodedKey, String encodedSignature) {
            byte[] key = null;
            byte[] signature = null;
            boolean identityValid = bot;
            if (!bot && !"*".equals(encodedKey) && !"*".equals(encodedSignature)) {
                try {
                    byte[] candidateKey = Base64.getDecoder().decode(encodedKey);
                    byte[] candidateSignature = Base64.getDecoder().decode(encodedSignature);
                    if (PlayerIdentity.verifyJoin(sessionId, nickname,
                            candidateKey, candidateSignature)) {
                        key = candidateKey;
                        signature = candidateSignature;
                        identityValid = true;
                    }
                } catch (IllegalArgumentException ignored) {
                    // Keep the participant visible but explicitly mark its identity as unverified.
                }
            }
            return new Peer(nickname, avatar, false, peerHost, bot,
                    !unsecure && identityValid, null, key, signature);
        }

        private void broadcastGame(String body, Connection except) throws Exception {
            for (Peer peer : List.copyOf(peers.values())) {
                if (peer.connection != null && peer.connection != except) sendGame(peer.connection, body);
            }
        }

        private void sendGame(Connection connection, String body) throws Exception {
            connection.enqueueGame(body);
        }

        private synchronized java.util.concurrent.CompletionStage<Void> sendGameTo(
                String nickname, String body) throws IOException {
            if (!host) throw new IllegalStateException("Only the host can send to a peer");
            Peer peer = findNormalized(nickname);
            if (peer == null || peer.connection == null || peer.local || peer.bot) {
                throw new IOException("Game peer is unavailable: " + nickname);
            }
            return peer.connection.enqueueGame(body);
        }

        private synchronized java.util.concurrent.CompletionStage<Void> sendGameToHost(
                String body) throws IOException {
            if (host) throw new IllegalStateException("The host cannot send to itself");
            if (serverConnection == null) throw new IOException("Host connection is unavailable");
            return serverConnection.enqueueGame(body);
        }

        private synchronized java.util.concurrent.CompletionStage<Void> broadcastGameFromChannel(String body,
                String skipNickname) throws IOException {
            if (!host) throw new IllegalStateException("Only the host can broadcast");
            List<java.util.concurrent.CompletableFuture<Void>> deliveries = new ArrayList<>();
            for (Peer peer : List.copyOf(peers.values())) {
                if (peer.connection != null
                        && (skipNickname == null || !peer.nickname.equals(skipNickname))) {
                    deliveries.add(peer.connection.enqueueGame(body).toCompletableFuture());
                }
            }
            return java.util.concurrent.CompletableFuture.allOf(
                    deliveries.toArray(java.util.concurrent.CompletableFuture[]::new));
        }

        private void broadcastDirect(String body, Connection except) throws Exception {
            for (Peer peer : List.copyOf(peers.values())) {
                if (peer.connection != null && peer.connection != except) peer.connection.writeEncrypted(body);
            }
        }

        private void broadcastBinary(byte[] clear, Connection except) throws Exception {
            for (Peer peer : List.copyOf(peers.values())) {
                if (peer.connection != null && peer.connection != except) peer.connection.writeEncryptedBinary(clear);
            }
        }

        private synchronized void removePeer(String nickname, boolean broadcast) throws Exception {
            Peer removed = peers.remove(nickname);
            if (removed == null || removed.local) return;
            if (removed.connection != null) removed.connection.close();
            addPresence(nickname, LobbyChatMessage.Type.PLAYER_LEFT);
            if (host && broadcast) broadcastGame("DELUSER#" + b64(nickname), removed.connection);
            publishCurrent();
        }

        private void addChat(String nickname, String text) {
            LobbyChatMessage.Type type = isChatImageUrl(text)
                    ? LobbyChatMessage.Type.IMAGE : LobbyChatMessage.Type.TEXT;
            chat.add(new LobbyChatMessage(chatSequence.getAndIncrement(), Instant.now(), nickname, type, text));
        }

        /** Preserves Swing's established on-wire http(s) -> img(s) convention. */
        private static String encodeChatImageUrl(String url) {
            if (url.regionMatches(true, 0, "https://", 0, 8)) {
                return "imgs://" + url.substring(8);
            }
            if (url.regionMatches(true, 0, "http://", 0, 7)) {
                return "img://" + url.substring(7);
            }
            throw new IllegalArgumentException("Chat images require an HTTP or HTTPS URL");
        }

        private static boolean isChatImageUrl(String text) {
            return text.regionMatches(true, 0, "img://", 0, 6)
                    || text.regionMatches(true, 0, "imgs://", 0, 7);
        }

        private void addPresence(String nickname, LobbyChatMessage.Type type) {
            chat.add(new LobbyChatMessage(chatSequence.getAndIncrement(), Instant.now(), nickname, type, ""));
        }

        private synchronized LobbySnapshot snapshot(LobbySnapshot.Phase phase, String detail) {
            List<LobbyParticipant> participants = peers.values().stream().map(peer ->
                    new LobbyParticipant(peer.nickname, peer.avatar, peer.local, peer.host,
                            peer.bot, peer.connected(), false, peer.secure,
                            peer.connection == null ? LobbyParticipant.NO_LATENCY
                                    : peer.connection.latency(),
                            peer.connection == null ? LobbyParticipant.NO_LATENCY
                                    : peer.connection.secondaryLatency(),
                            peer.identityPublicKey)).toList();
            return new LobbySnapshot(localNickname, serverNickname, endpoint, host, phase, detail,
                    participants, List.copyOf(chat), tableSettings, recovering, chatNotifications);
        }

        private synchronized void publish(LobbySnapshot.Phase phase, String detail) {
            LobbySession active = session;
            if (active != null) {
                try { active.publish(snapshot(phase, detail)); } catch (IllegalStateException ignored) { }
            }
        }

        private synchronized void publishCurrent() {
            LobbySession active = session;
            if (active != null) {
                publish(active.snapshot().phase(), "");
            }
        }

        private synchronized Peer findNormalized(String nickname) {
            String canonical = Normalizer.normalize(nickname, Normalizer.Form.NFC);
            return peers.values().stream().filter(peer ->
                    Normalizer.normalize(peer.nickname, Normalizer.Form.NFC).equals(canonical)).findFirst().orElse(null);
        }

        private synchronized Connection gameConnection(String nickname) {
            Peer peer = findNormalized(nickname);
            if (peer == null || peer.local || peer.bot) return null;
            // The host owns one authenticated logical connection per human.
            // A client uses its single authenticated server connection for all
            // remote-human traffic relayed by the canonical host.
            return host ? peer.connection : serverConnection;
        }

        private int forceReconnectRemotePeers() {
            if (!host) {
                throw new IllegalStateException(
                        "Only the host can force peer reconnection");
            }
            if (closed.get() || gameChannel.isClosed()) return 0;
            java.util.List<Connection> targets;
            synchronized (this) {
                targets = peers.values().stream()
                        .filter(peer -> !peer.local && !peer.bot
                                && peer.connection != null)
                        .map(peer -> peer.connection)
                        .distinct()
                        .toList();
            }
            int started = 0;
            for (Connection connection : targets) {
                if (connection.forceReconnectCurrentGeneration()) started++;
            }
            return started;
        }

        private void fail(String detail) {
            publish(LobbySnapshot.Phase.ERROR, detail);
            close();
        }

        @Override public void close() {
            if (!closed.compareAndSet(false, true)) return;
            gameChannel.close();
            closeUpnpMappingAsync();
            try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) { }
            closeConnections();
        }

        private void closeUpnpMappingAsync() {
            UpnpPortMapping mapping = upnpMapping;
            upnpMapping = null;
            if (mapping == null) return;
            try {
                executor.execute(mapping::close);
            } catch (RejectedExecutionException rejected) {
                Thread cleanup = new Thread(mapping::close,
                        "CoronaPoker-UPnP-close");
                cleanup.setDaemon(true);
                cleanup.start();
            }
        }

        private void closeConnections() {
            if (serverConnection != null) serverConnection.close();
            synchronized (this) {
                for (Peer peer : peers.values()) if (peer.connection != null) peer.connection.close();
            }
        }
    }

    /** Ordered handoff queue; network reads never invoke the controller inline. */
    private static final class NativeGameChannel implements GameChannel {
        private static final int MAX_PENDING_COMMANDS = 10_000;
        private static final long MAX_PENDING_BYTES = 16L * 1024L * 1024L;
        private final Transport transport;
        private final ExecutorService executor;
        private final java.util.ArrayDeque<Inbound> pending = new java.util.ArrayDeque<>();
        private boolean closed;
        private boolean draining;
        private long pendingBytes;
        private Consumer<Inbound> listener;
        private Consumer<String> peerLossListener;
        private final java.util.LinkedHashSet<String> pendingPeerLosses
                = new java.util.LinkedHashSet<>();

        NativeGameChannel(Transport transport, ExecutorService executor) {
            this.transport = transport;
            this.executor = executor;
        }

        synchronized void receive(String peerNickname, String command) throws IOException {
            if (closed) {
                // The dealer closes its table channel independently on each
                // peer. A final authenticated GAME frame may already be in the
                // socket while the local CloseTable barrier completes. It is
                // stale presentation/game traffic, not a lobby transport
                // failure: dropping it keeps the authenticated lobby alive for
                // the final screen and avoids evicting a healthy peer.
                return;
            }
            int bytes = command.getBytes(StandardCharsets.UTF_8).length;
            if (pending.size() >= MAX_PENDING_COMMANDS
                    || bytes > MAX_PENDING_BYTES - pendingBytes) {
                close();
                throw new IOException("Game input queue is full");
            }
            pending.addLast(new Inbound(peerNickname, command));
            pendingBytes += bytes;
            scheduleDrain();
        }

        @Override
        public synchronized AutoCloseable subscribe(Consumer<Inbound> next) {
            if (closed) throw new IllegalStateException("Game channel is closed");
            if (listener != null) throw new IllegalStateException("Game channel already has a consumer");
            listener = Objects.requireNonNull(next, "listener");
            scheduleDrain();
            return this::detach;
        }

        @Override
        public synchronized AutoCloseable subscribePeerLoss(
                Consumer<String> next) {
            if (closed) throw new IllegalStateException("Game channel is closed");
            if (peerLossListener != null) {
                throw new IllegalStateException(
                        "Game channel already has a peer-loss consumer");
            }
            peerLossListener = Objects.requireNonNull(next, "listener");
            if (!pendingPeerLosses.isEmpty()) {
                java.util.List<String> losses = java.util.List.copyOf(
                        pendingPeerLosses);
                pendingPeerLosses.clear();
                executor.execute(() -> losses.forEach(this::deliverPeerLoss));
            }
            return this::detachPeerLoss;
        }

        private synchronized void detachPeerLoss() {
            peerLossListener = null;
        }

        synchronized void publishPeerLoss(String nickname) {
            if (closed || nickname == null || nickname.isBlank()) return;
            if (peerLossListener == null) {
                pendingPeerLosses.add(nickname);
                return;
            }
            executor.execute(() -> deliverPeerLoss(nickname));
        }

        private void deliverPeerLoss(String nickname) {
            Consumer<String> target;
            synchronized (this) {
                if (closed) return;
                target = peerLossListener;
                if (target == null) {
                    pendingPeerLosses.add(nickname);
                    return;
                }
            }
            try {
                target.accept(nickname);
            } catch (RuntimeException failure) {
                transport.fail("El motor rechazó la pérdida definitiva de un jugador: "
                        + failure.getMessage());
                close();
            }
        }

        private synchronized void detach() {
            listener = null;
        }

        private void scheduleDrain() {
            if (listener == null || draining || pending.isEmpty()) return;
            draining = true;
            executor.execute(this::drain);
        }

        synchronized boolean isClosed() { return closed; }

        private void drain() {
            while (true) {
                Consumer<Inbound> target;
                Inbound command;
                synchronized (this) {
                    if (closed || listener == null || pending.isEmpty()) {
                        draining = false;
                        return;
                    }
                    target = listener;
                    command = pending.removeFirst();
                    pendingBytes -= command.command().getBytes(StandardCharsets.UTF_8).length;
                }
                try {
                    target.accept(command);
                } catch (RuntimeException failure) {
                    transport.fail("El motor rechazó un comando de partida: "
                            + failure.getMessage());
                    close();
                    return;
                }
            }
        }

        @Override public java.util.concurrent.CompletionStage<Void> sendToHost(String command)
                throws IOException {
            return transport.sendGameToHost(requireCommand(command));
        }

        @Override public java.util.concurrent.CompletionStage<Void> broadcastFromHost(
                String command, String skipNickname)
                throws IOException {
            return transport.broadcastGameFromChannel(requireCommand(command), skipNickname);
        }

        @Override public java.util.concurrent.CompletionStage<Void> sendFromHost(
                String nickname, String command) throws IOException {
            return transport.sendGameTo(Objects.requireNonNull(nickname, "nickname"),
                    requireCommand(command));
        }

        @Override public boolean isPeerReconnecting(String nickname) {
            Connection connection = transport.gameConnection(nickname);
            return connection != null && connection.isReconnecting();
        }

        @Override public int peerReconnectionCount(String nickname) {
            Connection connection = transport.gameConnection(nickname);
            return connection == null ? 0 : connection.reconnectionCount();
        }

        @Override public int peerLatency(String nickname) {
            Connection connection = transport.gameConnection(nickname);
            return connection == null ? Integer.MIN_VALUE : connection.latency();
        }

        @Override public int peerSecondaryLatency(String nickname) {
            Connection connection = transport.gameConnection(nickname);
            return connection == null ? Integer.MIN_VALUE
                    : connection.secondaryLatency();
        }

        @Override public int forceReconnectRemotePeers() {
            return transport.forceReconnectRemotePeers();
        }

        private static String requireCommand(String command) {
            String checked = Objects.requireNonNull(command, "command");
            if (checked.isBlank() || checked.startsWith("GAME#")) {
                throw new IllegalArgumentException("A game channel command must omit the GAME envelope");
            }
            return checked;
        }

        @Override public synchronized void close() {
            closed = true;
            pending.clear();
            pendingPeerLosses.clear();
            pendingBytes = 0L;
            listener = null;
            peerLossListener = null;
        }
    }

    private static final class Connection implements AutoCloseable {
        /** One authenticated socket incarnation of a logical game peer. */
        private static final class Generation {
            private final long id;
            private final Socket socket;
            private final InputStream input;
            private final OutputStream output;
            private final SecretKeySpec aes;
            private final SecretKeySpec hmac;
            private final Object writeLock = new Object();
            private final Object heartbeatLock = new Object();
            private final AtomicBoolean closed = new AtomicBoolean();
            private final AtomicBoolean heartbeatStarted = new AtomicBoolean();
            private Integer expectedHeartbeat;
            private Integer heartbeatPong;
            private Integer heartbeatPong2;
            private long heartbeatStartNanos;

            private Generation(long id, Socket socket, InputStream input,
                    OutputStream output, SecretKeySpec aes,
                    SecretKeySpec hmac) {
                this.id = id;
                this.socket = socket;
                this.input = input;
                this.output = output;
                this.aes = aes;
                this.hmac = hmac;
            }

            private void close() {
                if (!closed.compareAndSet(false, true)) return;
                synchronized (heartbeatLock) { heartbeatLock.notifyAll(); }
                try { socket.close(); } catch (IOException ignored) { }
            }
        }

        private final GameCommandGate gameCommandGate;
        private final ExecutorService executor;
        private final SessionOutbox gameOutbox = new SessionOutbox(
                GAME_OUTBOX_MAX_ELEMENTS, GAME_OUTBOX_MAX_BYTES);
        private final java.util.ArrayDeque<java.util.concurrent.CompletableFuture<Void>>
                gameDeliveries = new java.util.ArrayDeque<>();
        private final Object generationLock = new Object();
        private final Object confirmationLock = new Object();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicBoolean gameOutboxStarted = new AtomicBoolean();
        private final AtomicLong generationSequence = new AtomicLong(1L);
        private final SecretKeySpec originalHmac;
        private volatile Generation generation;
        private volatile boolean reconnecting;
        private volatile int reconnectionCount;
        private long reconnectAttempt;
        private volatile Integer expectedConfirmation;
        private volatile Generation expectedConfirmationGeneration;
        private volatile boolean confirmationReceived;
        private volatile int latency = LobbyParticipant.NO_LATENCY;
        private volatile int secondaryLatency = LobbyParticipant.NO_LATENCY;
        private byte[] sessionId;
        private String remoteNickname;
        private Path remoteAvatar;
        private boolean secure;
        private byte[] remoteIdentityPublicKey;
        private byte[] remoteIdentitySignature;
        private String gameInfo;
        private String gameConfig;

        Connection(Socket socket, InputStream input, OutputStream output,
                SecretKeySpec aes, SecretKeySpec hmac,
                GameCommandType.Direction inboundGameDirection,
                ExecutorService executor) {
            this(socket, input, output, aes, hmac, inboundGameDirection,
                    executor, true);
        }

        Connection(Socket socket, InputStream input, OutputStream output,
                SecretKeySpec aes, SecretKeySpec hmac,
                GameCommandType.Direction inboundGameDirection,
                ExecutorService executor, boolean startGameOutbox) {
            this.generation = new Generation(0L, socket, input, output,
                    aes, hmac);
            this.originalHmac = copyKey(hmac);
            this.gameCommandGate = new GameCommandGate(inboundGameDirection);
            this.executor = executor;
            if (startGameOutbox) startGameOutbox();
        }

        void startGameOutbox() {
            if (gameOutboxStarted.compareAndSet(false, true)) {
                executor.execute(this::runGameOutbox);
            }
        }

        Generation currentGeneration() { return generation; }

        boolean isCurrent(Generation expected) {
            return expected != null && generation == expected
                    && !expected.closed.get() && !closed.get();
        }

        boolean isConnected() {
            Generation current = generation;
            return current != null && !current.closed.get() && !closed.get();
        }

        boolean isReconnecting() { return reconnecting && !closed.get(); }
        int reconnectionCount() { return reconnectionCount; }

        boolean forceReconnectCurrentGeneration() {
            synchronized (generationLock) {
                Generation current = generation;
                if (closed.get() || reconnecting || current == null
                        || current.closed.get()) {
                    return false;
                }
                // The reader remains the sole owner of markGenerationDown(),
                // publication and watchdog startup.  Closing only this
                // generation wakes it without destroying the authenticated
                // logical connection or its ordered outbox.
                current.close();
                return true;
            }
        }

        SecretKeySpec originalHmac() { return copyKey(originalHmac); }

        long markGenerationDown(Generation expected) {
            if (expected == null) return -1L;
            synchronized (generationLock) {
                if (closed.get() || generation != expected) return -1L;
                generation = null;
                reconnecting = true;
                long attempt = ++reconnectAttempt;
                gameOutbox.advanceGenerationPreservingEntries();
                expected.close();
                generationLock.notifyAll();
                synchronized (confirmationLock) {
                    confirmationLock.notifyAll();
                }
                return attempt;
            }
        }

        boolean closeIfStillReconnecting(long expectedAttempt) {
            synchronized (generationLock) {
                if (closed.get() || !reconnecting || generation != null
                        || reconnectAttempt != expectedAttempt) {
                    return false;
                }
                close();
                return true;
            }
        }

        void adopt(Connection candidate) throws IOException {
            Objects.requireNonNull(candidate, "candidate");
            Generation replacement = candidate.detachGeneration();
            synchronized (generationLock) {
                if (closed.get()) {
                    replacement.close();
                    throw new IOException("Logical game connection is closed");
                }
                Generation previous = generation;
                if (previous != null) {
                    gameOutbox.advanceGenerationPreservingEntries();
                    previous.close();
                }
                replacement = new Generation(generationSequence.getAndIncrement(),
                        replacement.socket, replacement.input,
                        replacement.output, replacement.aes,
                        replacement.hmac);
                generation = replacement;
                reconnecting = false;
                reconnectionCount++;
                generationLock.notifyAll();
            }
            synchronized (confirmationLock) {
                confirmationLock.notifyAll();
            }
        }

        private Generation detachGeneration() throws IOException {
            synchronized (generationLock) {
                if (!closed.compareAndSet(false, true) || generation == null) {
                    throw new IOException("Reconnect candidate is unavailable");
                }
                Generation detached = generation;
                generation = null;
                generationLock.notifyAll();
                return detached;
            }
        }

        void startHeartbeat(Runnable onSample) {
            Generation current = generation;
            if (current != null
                    && current.heartbeatStarted.compareAndSet(false, true)) {
                executor.execute(() -> runHeartbeat(current, onSample));
            }
        }

        private void runHeartbeat(Generation active, Runnable onSample) {
            int consecutiveFailures = 0;
            while (isCurrent(active)) {
                int ping = HEARTBEAT_RANDOM.nextInt();
                synchronized (active.heartbeatLock) {
                    active.expectedHeartbeat = ping;
                    active.heartbeatPong = null;
                    active.heartbeatPong2 = null;
                    active.heartbeatStartNanos = System.nanoTime();
                }
                java.util.concurrent.Future<?> write = executor.submit(() -> {
                    writePlain(active, "PING#" + ping);
                    return null;
                });
                try {
                    write.get(HEARTBEAT_WRITE_TIMEOUT_MS,
                            java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (Exception failure) {
                    write.cancel(true);
                    active.close();
                    return;
                }

                long deadline = System.nanoTime()
                        + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(
                                HEARTBEAT_REPLY_TIMEOUT_MS);
                synchronized (active.heartbeatLock) {
                    while (isCurrent(active) && active.expectedHeartbeat != null
                            && (active.heartbeatPong == null
                            || active.heartbeatPong2 == null)) {
                        long remaining = deadline - System.nanoTime();
                        if (remaining <= 0L) break;
                        try {
                            long millis = Math.max(1L,
                                    java.util.concurrent.TimeUnit.NANOSECONDS
                                            .toMillis(remaining));
                            active.heartbeatLock.wait(millis);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            active.close();
                            return;
                        }
                    }
                    boolean firstOk = active.heartbeatPong != null
                            && active.heartbeatPong == ping + 1;
                    boolean secondOk = active.heartbeatPong2 != null
                            && active.heartbeatPong2 == ping + 2;
                    if (firstOk && secondOk) {
                        consecutiveFailures = 0;
                    } else {
                        consecutiveFailures++;
                        if (!firstOk) latency = -1;
                        if (!secondOk) secondaryLatency = -1;
                    }
                    active.expectedHeartbeat = null;
                }
                try {
                    onSample.run();
                } catch (RuntimeException ignored) {
                    // A closing lobby may reject its final latency projection.
                }
                if (consecutiveFailures >= MAX_CONSECUTIVE_HEARTBEAT_FAILURES) {
                    LOGGER.log(Level.WARNING,
                            "Native peer {0} lost {1} consecutive heartbeat rounds; closing socket",
                            new Object[]{remoteNickname, consecutiveFailures});
                    active.close();
                    return;
                }
                try {
                    Thread.sleep(HEARTBEAT_INTERVAL_MS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    active.close();
                    return;
                }
            }
        }

        void recordHeartbeatReply(Generation source, String verb, int value) {
            if (!isCurrent(source)) return;
            synchronized (source.heartbeatLock) {
                Integer ping = source.expectedHeartbeat;
                if (ping == null || !isCurrent(source)) return;
                long elapsed = Math.max(0L,
                        (System.nanoTime() - source.heartbeatStartNanos)
                                / 1_000_000L);
                int measured = (int) Math.min(Integer.MAX_VALUE, elapsed);
                if ("PONG".equals(verb) && value == ping + 1) {
                    source.heartbeatPong = value;
                    latency = measured;
                } else if ("PONG2".equals(verb) && value == ping + 2) {
                    source.heartbeatPong2 = value;
                    secondaryLatency = measured;
                }
                source.heartbeatLock.notifyAll();
            }
        }

        int latency() { return latency; }
        int secondaryLatency() { return secondaryLatency; }

        void confirm(Generation source, int confirmationId) {
            synchronized (confirmationLock) {
                if (source == expectedConfirmationGeneration
                        && expectedConfirmation != null
                        && expectedConfirmation.intValue() == confirmationId) {
                    confirmationReceived = true;
                    confirmationLock.notifyAll();
                }
            }
        }

        java.util.concurrent.CompletionStage<Void> enqueueGame(String body)
                throws IOException {
            java.util.concurrent.CompletableFuture<Void> delivery
                    = new java.util.concurrent.CompletableFuture<>();
            synchronized (gameOutbox) {
                if (closed.get() || !gameOutbox.offer(body)) {
                    close();
                    throw new IOException("Critical GAME outbox is closed or full");
                }
                gameDeliveries.addLast(delivery);
            }
            return delivery;
        }

        private void runGameOutbox() {
            while (!closed.get()) {
                SessionOutbox.Entry entry;
                synchronized (gameOutbox) {
                    entry = gameOutbox.peek();
                    if (entry == null && !closed.get()) {
                        try { gameOutbox.wait(250L); }
                        catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            close();
                            return;
                        }
                        continue;
                    }
                }
                if (entry == null) continue;
                Generation active = awaitGeneration();
                if (active == null) return;
                if (!gameOutbox.isCurrent(entry)) continue;

                int confirmationId = entry.wireId() + 1;
                synchronized (confirmationLock) {
                    expectedConfirmation = confirmationId;
                    expectedConfirmationGeneration = active;
                    confirmationReceived = false;
                }
                try {
                    writeEncrypted(active, "GAME#" + entry.wireId() + "#"
                            + entry.command());
                    long deadline = System.nanoTime()
                            + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(
                                    GAME_CONFIRMATION_TIMEOUT_MS);
                    boolean confirmed;
                    synchronized (confirmationLock) {
                        while (!closed.get() && isCurrent(active)
                                && !confirmationReceived) {
                            long remaining = deadline - System.nanoTime();
                            if (remaining <= 0L) break;
                            long millis = Math.max(1L,
                                    java.util.concurrent.TimeUnit.NANOSECONDS
                                            .toMillis(remaining));
                            confirmationLock.wait(millis);
                        }
                        confirmed = confirmationReceived && isCurrent(active);
                    }
                    if (confirmed) {
                        synchronized (gameOutbox) {
                            if (gameOutbox.isCurrent(entry)
                                    && gameOutbox.removeIfHead(entry)) {
                                java.util.concurrent.CompletableFuture<Void>
                                        completed = gameDeliveries.removeFirst();
                                completed.complete(null);
                            }
                        }
                    } else if (isCurrent(active)) {
                        // Closing only this socket wakes its reader. The reader
                        // owns the transition to reconnecting and publication.
                        active.close();
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    close();
                    return;
                } catch (IOException failure) {
                    active.close();
                } finally {
                    synchronized (confirmationLock) {
                        if (expectedConfirmationGeneration == active) {
                            expectedConfirmation = null;
                            expectedConfirmationGeneration = null;
                            confirmationReceived = false;
                        }
                    }
                }
            }
        }

        private Generation awaitGeneration() {
            synchronized (generationLock) {
                while (!closed.get()) {
                    Generation current = generation;
                    if (current != null && !current.closed.get()) return current;
                    try { generationLock.wait(250L); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        close();
                        return null;
                    }
                }
                return null;
            }
        }

        void writeEncrypted(String clear) throws IOException {
            Generation current = requireGeneration();
            writeEncrypted(current, clear);
        }

        private void writeEncrypted(Generation target, String clear)
                throws IOException {
            String frame = SecureChannelCodec.encryptCommand(clear,
                    target.aes, target.hmac);
            if (frame == null) throw new IOException("Cannot encrypt command");
            writeFrame(target, (frame + "\n").getBytes(StandardCharsets.UTF_8));
        }

        void writePlain(String frame) throws IOException {
            writePlain(requireGeneration(), frame);
        }

        private void writePlain(Generation target, String frame)
                throws IOException {
            writeFrame(target, (frame + "\n").getBytes(StandardCharsets.UTF_8));
        }

        void writeEncryptedBinary(byte[] clear) throws IOException {
            Generation target = requireGeneration();
            byte[] encrypted = SecureChannelCodec.encryptBytes(clear,
                    target.aes, target.hmac);
            if (encrypted == null) {
                throw new IOException("Cannot encrypt binary payload");
            }
            synchronized (target.writeLock) {
                if (!isCurrent(target)) {
                    throw new IOException("Socket generation changed");
                }
                WireFrameCodec.writeBinary(target.output, encrypted);
            }
        }

        private void writeFrame(Generation target, byte[] frame)
                throws IOException {
            synchronized (target.writeLock) {
                if (!isCurrent(target)) {
                    throw new IOException("Socket generation changed");
                }
                target.output.write(frame);
                target.output.flush();
            }
        }

        String readEncryptedText() throws Exception {
            Generation current = requireGeneration();
            WireFrameCodec.Frame frame = WireFrameCodec.read(current.input,
                    MAX_COMMAND_BYTES);
            if (frame == null || !frame.isText()) return null;
            return SecureChannelCodec.decryptCommand(frame.text(),
                    current.aes, current.hmac);
        }

        private Generation requireGeneration() throws IOException {
            Generation current = generation;
            if (current == null || current.closed.get() || closed.get()) {
                throw new IOException("Authenticated socket is unavailable");
            }
            return current;
        }

        @Override public void close() {
            if (!closed.compareAndSet(false, true)) return;
            Generation current;
            synchronized (generationLock) {
                current = generation;
                generation = null;
                reconnecting = false;
                generationLock.notifyAll();
            }
            synchronized (gameOutbox) {
                gameOutbox.advanceGeneration();
                IOException failure = new IOException(
                        "Game connection closed before delivery");
                while (!gameDeliveries.isEmpty()) {
                    gameDeliveries.removeFirst()
                            .completeExceptionally(failure);
                }
            }
            synchronized (confirmationLock) { confirmationLock.notifyAll(); }
            if (current != null) current.close();
        }

        private static SecretKeySpec copyKey(SecretKeySpec source) {
            return new SecretKeySpec(source.getEncoded(), source.getAlgorithm());
        }
    }

    private record Peer(String nickname, Path avatar, boolean local, boolean host,
            boolean bot, boolean secure, Connection connection,
            byte[] identityPublicKey, byte[] identitySignature) {
        private Peer {
            identityPublicKey = identityPublicKey == null ? null : identityPublicKey.clone();
            identitySignature = identitySignature == null ? null : identitySignature.clone();
        }
        boolean connected() {
            return connection == null ? bot || local : connection.isConnected();
        }
        static Peer local(String nickname, Path avatar, boolean host,
                byte[] identityPublicKey, byte[] identitySignature) {
            return new Peer(nickname, avatar, true, host, false, true, null,
                    identityPublicKey, identitySignature);
        }
    }

    private static KeyPair ecPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        return generator.generateKeyPair();
    }

    private static SecretKeySpec[] keys(KeyPair local, byte[] remoteEncoded,
            String password) throws Exception {
        PublicKey remote = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(remoteEncoded));
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(local.getPrivate());
        agreement.doPhase(remote, true);
        byte[] secret = SecureChannelCodec.deriveChannelSecret(agreement.generateSecret(), emptyToNull(password));
        return new SecretKeySpec[]{new SecretKeySpec(secret, 0, 32, "AES"),
            new SecretKeySpec(secret, 32, 32, "HmacSHA256")};
    }

    private static byte[] readBounded(DataInputStream input, int cap, String label) throws IOException {
        int length = input.readInt();
        if (length <= 0 || length > cap) throw new IOException("Invalid " + label + " length: " + length);
        byte[] value = new byte[length]; input.readFully(value); return value;
    }

    private static int parsePort(String port) {
        int value = Integer.parseInt(port);
        if (value < 1 || value > 65535) throw new IllegalArgumentException("Puerto fuera de rango");
        return value;
    }

    private static byte[] readAvatar(Path avatar) throws IOException {
        if (avatar == null) return null;
        byte[] bytes = Files.readAllBytes(avatar);
        if (bytes.length > 256 * 1024) throw new IOException("Avatar too large");
        return bytes;
    }

    private static Path saveAvatar(String encoded, String nickname, Path directory) throws IOException {
        if (encoded == null || encoded.isBlank() || "*".equals(encoded)) return null;
        byte[] bytes = Base64.getDecoder().decode(encoded);
        if (bytes.length > 256 * 1024) throw new IOException("Remote avatar too large");
        Path avatars = directory.resolve("cache").resolve("lobby-avatars");
        Files.createDirectories(avatars);
        String digest;
        try {
            digest = java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes), 0, 12);
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
        Path target = avatars.resolve(digest + ".img");
        if (!Files.exists(target)) Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
        return target;
    }

    private static IOException rejection(String[] parts) {
        String code = parts.length == 0 ? "UNKNOWN" : parts[0];
        String message = switch (code) {
            case "BADVERSION" -> "Versión incompatible";
            case "YOUARELATE" -> "La timba ya ha empezado";
            case "NOSPACE" -> "La timba está llena";
            case "NICKFAIL" -> "El nick ya está en uso";
            case "NICKUNAUTHORIZED" -> "El nick contiene caracteres reservados";
            default -> "El servidor rechazó la conexión";
        };
        return new IOException(message);
    }

    private static String b64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
    private static String text64(String encoded) {
        return new String(Base64.getDecoder().decode(
                encoded.replaceAll("[^A-Za-z0-9+/=]", "")), StandardCharsets.UTF_8);
    }
    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
