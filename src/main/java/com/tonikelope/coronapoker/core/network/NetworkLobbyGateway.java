package com.tonikelope.coronapoker.core.network;

import com.tonikelope.coronapoker.core.ApplicationMetadata;
import com.tonikelope.coronapoker.core.LobbyChatMessage;
import com.tonikelope.coronapoker.core.LobbyCommand;
import com.tonikelope.coronapoker.core.LobbyParticipant;
import com.tonikelope.coronapoker.core.LobbySession;
import com.tonikelope.coronapoker.core.LobbySnapshot;
import com.tonikelope.coronapoker.core.NewGameRequest;
import com.tonikelope.coronapoker.core.NewGameSessionGateway;
import com.tonikelope.coronapoker.core.NewGameTableDraft;
import com.tonikelope.coronapoker.core.identity.PlayerIdentity;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.SecretKeySpec;

/** Native, UI-free implementation of the existing CoronaPoker lobby wire. */
public final class NetworkLobbyGateway implements NewGameSessionGateway, AutoCloseable {
    static final byte[] MAGIC = java.util.HexFormat.of().parseHex("5c1f158dd9855cc9");
    static final int HANDSHAKE_TIMEOUT_MS = 30_000;
    static final int MAX_PUBLIC_KEY_BYTES = 256;
    static final int MAX_SESSION_ID_BYTES = 64;
    static final int MAX_COMMAND_BYTES = 16 * 1024 * 1024;
    static final int MAX_VOICE_BYTES = 320 * 1024;
    private static final AtomicInteger THREAD_NUMBER = new AtomicInteger();

    private final Path coronaDirectory;
    private final ExecutorService executor;
    private final AtomicBoolean closed = new AtomicBoolean();

    public NetworkLobbyGateway(Path coronaDirectory) {
        this.coronaDirectory = Objects.requireNonNull(coronaDirectory, "coronaDirectory")
                .toAbsolutePath().normalize();
        ThreadFactory threads = task -> {
            Thread thread = new Thread(task, "coronapoker-network-" + THREAD_NUMBER.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        executor = Executors.newCachedThreadPool(threads);
    }

    public static NetworkLobbyGateway forCurrentUser() {
        return new NetworkLobbyGateway(Path.of(System.getProperty("user.home"), ".coronapoker"));
    }

    @Override
    public CompletableFuture<LobbySession> open(NewGameRequest request) {
        Objects.requireNonNull(request, "request");
        if (closed.get()) return CompletableFuture.failedFuture(new IllegalStateException("Network gateway is closed"));
        return CompletableFuture.supplyAsync(() -> {
            try {
                return request.joining() ? Transport.openClient(request, coronaDirectory, executor)
                        : Transport.openHost(request, coronaDirectory, executor);
            } catch (Exception failure) {
                throw new java.util.concurrent.CompletionException(failure);
            }
        }, executor);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) executor.shutdownNow();
    }

    private static final class Transport implements AutoCloseable {
        private final boolean host;
        private final String localNickname;
        private final String endpoint;
        private final Path coronaDirectory;
        private final ExecutorService executor;
        private final NewGameTableDraft.Settings tableSettings;
        private final boolean recovering;
        private final byte[] sessionId;
        private final PlayerIdentity identity;
        private final Map<String, Peer> peers = new LinkedHashMap<>();
        private final List<LobbyChatMessage> chat = new ArrayList<>();
        private final AtomicLong chatSequence = new AtomicLong();
        private final AtomicInteger commandId = new AtomicInteger(new SecureRandom().nextInt());
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile String password;
        private volatile ServerSocket serverSocket;
        private volatile Connection serverConnection;
        private volatile LobbySession session;
        private String serverNickname;
        private boolean chatNotifications = true;

        private Transport(boolean host, NewGameRequest request, Path coronaDirectory,
                ExecutorService executor, byte[] sessionId, PlayerIdentity identity,
                NewGameTableDraft.Settings tableSettings) {
            this.host = host;
            this.localNickname = request.connection().nickname();
            this.endpoint = request.connection().server() + ":" + request.connection().port();
            this.password = emptyToNull(request.connection().password());
            this.coronaDirectory = coronaDirectory;
            this.executor = executor;
            this.tableSettings = Objects.requireNonNull(tableSettings, "tableSettings");
            this.recovering = request.connection().recover();
            this.sessionId = sessionId;
            this.identity = identity;
            this.serverNickname = host ? localNickname : "";
        }

        static LobbySession openHost(NewGameRequest request, Path directory,
                ExecutorService executor) throws Exception {
            PlayerIdentity identity = PlayerIdentity.loadOrCreate(directory, request.connection().nickname());
            byte[] sessionId = new byte[16];
            new SecureRandom().nextBytes(sessionId);
            Transport transport = new Transport(true, request, directory, executor, sessionId,
                    identity, request.table());
            int port = parsePort(request.connection().port());
            ServerSocket server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(port));
            transport.serverSocket = server;
            transport.peers.put(transport.localNickname,
                    Peer.local(transport.localNickname, request.connection().avatar(), true,
                            identity.publicKey(), identity.signJoin(sessionId)));
            LobbySession session = new LobbySession(transport.snapshot(
                    LobbySnapshot.Phase.WAITING_FOR_PLAYERS, ""), transport::submit, transport);
            transport.session = session;
            executor.execute(transport::acceptLoop);
            return session;
        }

        static LobbySession openClient(NewGameRequest request, Path directory,
                ExecutorService executor) throws Exception {
            PlayerIdentity identity = PlayerIdentity.loadOrCreate(directory, request.connection().nickname());
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(request.connection().server(),
                    parsePort(request.connection().port())), HANDSHAKE_TIMEOUT_MS);
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            socket.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
            Connection connection = clientHandshake(socket, request, identity, directory);
            Transport transport = new Transport(false, request, directory, executor,
                    connection.sessionId, identity,
                    NewGameTableDraft.Settings.parseWire(connection.gameConfig));
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
            return session;
        }

        private static Connection clientHandshake(Socket socket, NewGameRequest request,
                PlayerIdentity identity, Path directory) throws Exception {
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
            Connection connection = new Connection(socket, input, output, keys[0], keys[1]);
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
                connection = new Connection(socket, input, output, keys[0], keys[1]);
                String join = connection.readEncryptedText();
                if (join == null) throw new IOException("Client closed during handshake");
                String[] parts = join.split("#", -1);
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
                    addPresence(nickname, LobbyChatMessage.Type.PLAYER_JOINED);
                    publish(LobbySnapshot.Phase.WAITING_FOR_PLAYERS, "");
                    broadcastGame("NEWUSER#" + b64(nickname) + "#0#"
                            + (parts[2].isBlank() ? "*" : parts[2]) + "#" + parts[4] + "#" + parts[5], connection);
                }
                socket.setSoTimeout(0);
                Connection accepted = connection;
                executor.execute(() -> readHostPeer(accepted));
                connection = null;
            } catch (Exception ignored) {
                // Rejection or malformed unauthenticated handshake: close without an oracle.
            } finally {
                if (connection != null) connection.close();
            }
        }

        private CompletableFuture<Void> submit(LobbyCommand command) {
            return CompletableFuture.runAsync(() -> {
                try {
                    if (command instanceof LobbyCommand.SendText text) sendChat(text.text());
                    else if (command instanceof LobbyCommand.SendImage image) sendChat("img://" + image.url());
                    else if (command instanceof LobbyCommand.SendVoice voice) sendVoice(voice.wav());
                    else if (command instanceof LobbyCommand.AddBot) addBot();
                    else if (command instanceof LobbyCommand.Kick kick) kick(kick.nickname());
                    else if (command instanceof LobbyCommand.ChangePassword change) changePassword(change.password());
                    else if (command instanceof LobbyCommand.SetChatNotifications notifications) setNotifications(notifications.enabled());
                    else if (command instanceof LobbyCommand.StartGame) publish(LobbySnapshot.Phase.INITIALIZING_GAME, "");
                    else if (command instanceof LobbyCommand.Leave) leave();
                } catch (Exception failure) {
                    throw new java.util.concurrent.CompletionException(failure);
                }
            }, executor);
        }

        private synchronized void sendChat(String text) throws Exception {
            String wire = "CHAT#" + b64(localNickname) + "#" + b64(text);
            if (host) broadcastDirect(wire, null); else serverConnection.writeEncrypted(wire);
            addChat(localNickname, text);
            publish(currentPhase(), "");
        }

        private synchronized void sendVoice(byte[] wav) throws Exception {
            if (wav.length > MAX_VOICE_BYTES) throw new IllegalArgumentException("La nota de voz es demasiado grande");
            byte[] payload = BinaryPayloadCodec.encode(BinaryPayloadCodec.TYPE_VOICE, localNickname, wav);
            if (host) broadcastBinary(payload, null); else serverConnection.writeEncryptedBinary(payload);
            chat.add(new LobbyChatMessage(chatSequence.getAndIncrement(), Instant.now(), localNickname,
                    LobbyChatMessage.Type.VOICE, Base64.getEncoder().encodeToString(wav)));
            publish(currentPhase(), "");
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
            publish(currentPhase(), "");
        }

        private synchronized void kick(String nickname) throws Exception {
            Peer peer = peers.get(nickname);
            if (peer == null || peer.local || peer.host) throw new IllegalArgumentException("Unknown participant");
            if (peer.connection != null) peer.connection.writeEncrypted("KICKED");
            removePeer(nickname, true);
        }

        private synchronized void changePassword(String next) throws Exception {
            password = emptyToNull(next);
            broadcastDirect("NEWPASS#" + (password == null ? "*" : b64(password)), null);
        }

        private synchronized void setNotifications(boolean enabled) {
            chatNotifications = enabled;
            publish(currentPhase(), "");
        }

        private void leave() throws Exception {
            if (host) broadcastDirect("EXIT", null); else if (serverConnection != null) serverConnection.writeEncrypted("EXIT");
            publish(LobbySnapshot.Phase.CLOSED, "");
            close();
        }

        private void readHostPeer(Connection connection) {
            try {
                readLoop(connection, true);
            } finally {
                synchronized (this) {
                    if (!closed.get() && connection.remoteNickname != null) {
                        try { removePeer(connection.remoteNickname, true); } catch (Exception ignored) { }
                    }
                }
            }
        }

        private void readClient(Connection connection) {
            try {
                readLoop(connection, false);
            } finally {
                if (!closed.get()) fail("Se ha perdido la conexión con el servidor");
            }
        }

        private void readLoop(Connection connection, boolean fromClient) {
            try {
                while (!closed.get()) {
                    WireFrameCodec.Frame frame = WireFrameCodec.read(connection.input, MAX_COMMAND_BYTES);
                    if (frame == null) return;
                    if (frame.isBinary()) {
                        byte[] clear = SecureChannelCodec.decryptBytes(frame.binary(), connection.aes, connection.hmac);
                        if (clear != null) receiveBinary(connection, clear, fromClient);
                        continue;
                    }
                    String command;
                    try {
                        command = SecureChannelCodec.decryptCommand(frame.text(), connection.aes, connection.hmac);
                    } catch (java.security.KeyException invalid) {
                        continue;
                    }
                    if (command != null) receiveText(connection, command, fromClient);
                }
            } catch (Exception ignored) { }
        }

        private synchronized void receiveText(Connection source, String command,
                boolean fromClient) throws Exception {
            String[] parts = command.split("#", -1);
            switch (parts[0]) {
                case "PING" -> source.writePlain("PONG2#" + (Integer.parseInt(parts[1]) + 2));
                case "PONG", "PONG2", "CONF" -> { }
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
                    publish(currentPhase(), "");
                }
                case "GAME" -> {
                    if (parts.length < 3) throw new IOException("Malformed GAME frame");
                    source.writeEncrypted("CONF#" + (Integer.parseInt(parts[1]) + 1) + "#OK");
                    if (!fromClient) receiveGame(parts);
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
                        publish(currentPhase(), "");
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
                    publish(currentPhase(), "");
                }
                case "DELUSER" -> removePeer(text64(parts[3]), false);
                case "GAMEINFO", "GAMECONFIG" -> { }
                case "INIT" -> publish(LobbySnapshot.Phase.IN_GAME, "");
                default -> { }
            }
        }

        private synchronized void receiveBinary(Connection source, byte[] clear,
                boolean fromClient) throws Exception {
            BinaryPayloadCodec.Payload payload = BinaryPayloadCodec.decode(clear);
            if (payload.type() != BinaryPayloadCodec.TYPE_VOICE || payload.body().length > MAX_VOICE_BYTES) return;
            String nickname = fromClient ? source.remoteNickname : payload.nickname();
            if (fromClient) broadcastBinary(BinaryPayloadCodec.encode(payload.type(), nickname, payload.body()), source);
            chat.add(new LobbyChatMessage(chatSequence.getAndIncrement(), Instant.now(), nickname,
                    LobbyChatMessage.Type.VOICE, Base64.getEncoder().encodeToString(payload.body())));
            publish(currentPhase(), "");
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
            connection.writeEncrypted("GAME#" + commandId.getAndIncrement() + "#" + body);
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
            publish(currentPhase(), "");
        }

        private void addChat(String nickname, String text) {
            LobbyChatMessage.Type type = text.startsWith("img://")
                    ? LobbyChatMessage.Type.IMAGE : LobbyChatMessage.Type.TEXT;
            chat.add(new LobbyChatMessage(chatSequence.getAndIncrement(), Instant.now(), nickname, type, text));
        }

        private void addPresence(String nickname, LobbyChatMessage.Type type) {
            chat.add(new LobbyChatMessage(chatSequence.getAndIncrement(), Instant.now(), nickname, type, ""));
        }

        private synchronized LobbySnapshot snapshot(LobbySnapshot.Phase phase, String detail) {
            List<LobbyParticipant> participants = peers.values().stream().map(peer ->
                    new LobbyParticipant(peer.nickname, peer.avatar, peer.local, peer.host,
                            peer.bot, peer.connected(), false, peer.secure,
                            LobbyParticipant.NO_LATENCY, LobbyParticipant.NO_LATENCY)).toList();
            return new LobbySnapshot(localNickname, serverNickname, endpoint, host, phase, detail,
                    participants, List.copyOf(chat), tableSettings, recovering, chatNotifications);
        }

        private void publish(LobbySnapshot.Phase phase, String detail) {
            LobbySession active = session;
            if (active != null) {
                try { active.publish(snapshot(phase, detail)); } catch (IllegalStateException ignored) { }
            }
        }

        private LobbySnapshot.Phase currentPhase() {
            LobbySession active = session;
            return active == null ? LobbySnapshot.Phase.CONNECTING : active.snapshot().phase();
        }

        private synchronized Peer findNormalized(String nickname) {
            String canonical = Normalizer.normalize(nickname, Normalizer.Form.NFC);
            return peers.values().stream().filter(peer ->
                    Normalizer.normalize(peer.nickname, Normalizer.Form.NFC).equals(canonical)).findFirst().orElse(null);
        }

        private void fail(String detail) {
            publish(LobbySnapshot.Phase.ERROR, detail);
            close();
        }

        @Override public void close() {
            if (!closed.compareAndSet(false, true)) return;
            try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) { }
            if (serverConnection != null) serverConnection.close();
            synchronized (this) {
                for (Peer peer : peers.values()) if (peer.connection != null) peer.connection.close();
            }
        }
    }

    private static final class Connection implements AutoCloseable {
        private final Socket socket;
        private final InputStream input;
        private final OutputStream output;
        private final SecretKeySpec aes;
        private final SecretKeySpec hmac;
        private byte[] sessionId;
        private String remoteNickname;
        private Path remoteAvatar;
        private boolean secure;
        private byte[] remoteIdentityPublicKey;
        private byte[] remoteIdentitySignature;
        private String gameInfo;
        private String gameConfig;

        Connection(Socket socket, InputStream input, OutputStream output,
                SecretKeySpec aes, SecretKeySpec hmac) {
            this.socket = socket; this.input = input; this.output = output;
            this.aes = aes; this.hmac = hmac;
        }
        synchronized void writeEncrypted(String clear) throws IOException {
            String frame = SecureChannelCodec.encryptCommand(clear, aes, hmac);
            if (frame == null) throw new IOException("Cannot encrypt command");
            output.write((frame + "\n").getBytes(StandardCharsets.UTF_8)); output.flush();
        }
        synchronized void writePlain(String frame) throws IOException {
            output.write((frame + "\n").getBytes(StandardCharsets.UTF_8)); output.flush();
        }
        synchronized void writeEncryptedBinary(byte[] clear) throws IOException {
            byte[] encrypted = SecureChannelCodec.encryptBytes(clear, aes, hmac);
            if (encrypted == null) throw new IOException("Cannot encrypt binary payload");
            WireFrameCodec.writeBinary(output, encrypted);
        }
        String readEncryptedText() throws Exception {
            WireFrameCodec.Frame frame = WireFrameCodec.read(input, MAX_COMMAND_BYTES);
            if (frame == null || !frame.isText()) return null;
            return SecureChannelCodec.decryptCommand(frame.text(), aes, hmac);
        }
        @Override public void close() { try { socket.close(); } catch (IOException ignored) { } }
    }

    private record Peer(String nickname, Path avatar, boolean local, boolean host,
            boolean bot, boolean secure, Connection connection,
            byte[] identityPublicKey, byte[] identitySignature) {
        private Peer {
            identityPublicKey = identityPublicKey == null ? null : identityPublicKey.clone();
            identitySignature = identitySignature == null ? null : identitySignature.clone();
        }
        boolean connected() { return connection == null ? bot || local : !connection.socket.isClosed(); }
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
        return new String(Base64.getDecoder().decode(encoded.replaceAll("[^A-Za-z0-9+/=]", "")), StandardCharsets.UTF_8).trim();
    }
    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
