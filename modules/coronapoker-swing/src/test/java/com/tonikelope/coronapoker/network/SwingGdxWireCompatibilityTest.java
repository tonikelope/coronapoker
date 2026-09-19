package com.tonikelope.coronapoker.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.AboutDialog;
import com.tonikelope.coronapoker.Helpers;
import com.tonikelope.coronapoker.WireFrame;
import com.tonikelope.coronapoker.core.LobbyCommand;
import com.tonikelope.coronapoker.core.LobbySession;
import com.tonikelope.coronapoker.core.NewGameConnectionDraft;
import com.tonikelope.coronapoker.core.NewGameRequest;
import com.tonikelope.coronapoker.core.NewGameTableDraft;
import com.tonikelope.coronapoker.core.game.GameConfigCodecV1;
import com.tonikelope.coronapoker.core.game.GameLaunchContext;
import com.tonikelope.coronapoker.core.game.GameTableFactory;
import com.tonikelope.coronapoker.core.identity.PlayerIdentity;
import com.tonikelope.coronapoker.core.network.NetworkLobbyGateway;
import com.tonikelope.coronapoker.table.TableEventBridge;
import com.tonikelope.coronapoker.table.TableRenderer;
import com.tonikelope.coronapoker.table.TableSession;
import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Cross-frontend protocol canary. One endpoint deliberately uses the exact
 * legacy Swing helpers and framing while the other uses NetworkLobbyGateway,
 * which is the native GDX transport.
 */
class SwingGdxWireCompatibilityTest {

    private static final byte[] MAGIC = HexFormat.of().parseHex("5c1f158dd9855cc9");

    @TempDir Path temporary;

    @Test
    void legacySwingClientJoinsGdxHostAndExchangesAcknowledgedGameFrames()
            throws Exception {
        int port = freePort();
        AtomicReference<GameLaunchContext> hostContext = new AtomicReference<>();
        String init = initCommand("gdx-host-swing-client");
        GameTableFactory tables = context -> {
            hostContext.set(context);
            return table(context, () -> broadcast(context, init));
        };

        try (NetworkLobbyGateway gateway = new NetworkLobbyGateway(
                temporary.resolve("gdx-host"), tables);
                LobbySession host = gateway.open(request(false, "GdxHost", port))
                        .get(5, TimeUnit.SECONDS);
                LegacyPeer swing = LegacyPeer.connect(temporary.resolve("swing-client"),
                        "SwingClient", port)) {
            await(() -> host.snapshot().participants().size() == 2);

            // A later native/GDX join must be announced using the exact legacy
            // NEWUSER layout, while that newcomer receives this Swing peer in
            // USERSLIST with its verified identity intact.
            try (NetworkLobbyGateway observerGateway = new NetworkLobbyGateway(
                    temporary.resolve("gdx-observer"));
                    LobbySession observer = observerGateway.open(
                            request(true, "GdxObserver", port)).get(5, TimeUnit.SECONDS)) {
                String joined = swing.readGame("NEWUSER");
                String[] fields = joined.split("#", -1);
                assertEquals("GdxObserver", decode(fields[3]));
                assertEquals("0", fields[4]);
                assertEquals(32, Base64.getDecoder().decode(fields[6]).length);
                assertEquals(64, Base64.getDecoder().decode(fields[7]).length);
                swing.acknowledge(joined);
                await(() -> observer.snapshot().participants().stream()
                        .anyMatch(player -> player.nickname().equals("SwingClient")
                                && player.secure()));
            }
            String departed = swing.readGame("DELUSER");
            assertEquals("GdxObserver", decode(departed.split("#", -1)[3]));
            swing.acknowledge(departed);
            await(() -> host.snapshot().participants().size() == 2);

            host.submit(new LobbyCommand.StartGame()).toCompletableFuture()
                    .get(2, TimeUnit.SECONDS);
            TableSession hostTable = host.tableSession().toCompletableFuture()
                    .get(2, TimeUnit.SECONDS);
            CompletableFuture<Void> started = hostTable.attach(renderer())
                    .toCompletableFuture();

            String initFrame = swing.readGame("INIT");
            assertEquals(init, gameBody(initFrame));
            swing.acknowledge(initFrame);
            started.get(2, TimeUnit.SECONDS);

            AtomicReference<String> inbound = new AtomicReference<>();
            hostContext.get().channel().subscribe(message -> inbound.set(
                    message.peerNickname() + ":" + message.command()));
            swing.writeEncrypted("GAME#101#ACTION#CHECK");
            assertEquals("CONF#102#OK", swing.readApplicationFrame());
            await(() -> "SwingClient:ACTION#CHECK".equals(inbound.get()));
        }
    }

    @Test
    void gdxClientJoinsLegacySwingServerAndExchangesAcknowledgedGameFrames()
            throws Exception {
        int port = freePort();
        AtomicReference<GameLaunchContext> clientContext = new AtomicReference<>();
        GameTableFactory tables = context -> {
            clientContext.set(context);
            return table(context, () -> CompletableFuture.completedFuture(null));
        };

        try (LegacyServer swing = new LegacyServer(temporary.resolve("swing-host"),
                "SwingHost", port);
                NetworkLobbyGateway gateway = new NetworkLobbyGateway(
                        temporary.resolve("gdx-client"), tables)) {
            CompletableFuture<LobbySession> opening = gateway.open(
                    request(true, "GdxClient", port));
            swing.acceptAndHandshake();
            try (LobbySession client = opening.get(5, TimeUnit.SECONDS)) {
                assertEquals(2, client.snapshot().participants().size());
                assertTrue(client.snapshot().participants().stream()
                        .anyMatch(player -> player.nickname().equals("SwingHost")
                                && player.secure()));

                swing.sendExistingParticipant("SwingGuest", 199);
                assertEquals("CONF#200#OK", swing.peer().readApplicationFrame());
                await(() -> client.snapshot().participants().stream()
                        .anyMatch(player -> player.nickname().equals("SwingGuest")
                                && player.secure()));

                String init = initCommand("swing-host-gdx-client");
                swing.peer().writeEncrypted("GAME#201#" + init);
                assertEquals("CONF#202#OK", swing.peer().readApplicationFrame());
                TableSession clientTable = client.tableSession().toCompletableFuture()
                        .get(2, TimeUnit.SECONDS);
                assertEquals("swing-host-gdx-client",
                        clientContext.get().initialConfiguration().sessionId());
                clientTable.attach(renderer()).toCompletableFuture()
                        .get(2, TimeUnit.SECONDS);

                AtomicReference<String> inbound = new AtomicReference<>();
                clientContext.get().channel().subscribe(message -> inbound.set(message.command()));
                swing.peer().writeEncrypted("GAME#203#PAUSE#0#SwingHost");
                assertEquals("CONF#204#OK", swing.peer().readApplicationFrame());
                await(() -> "PAUSE#0#SwingHost".equals(inbound.get()));

                CompletableFuture<Void> sent = clientContext.get().channel()
                        .sendToHost("ACTION#CALL").toCompletableFuture();
                String action = swing.peer().readGame("ACTION");
                assertEquals("ACTION#CALL", gameBody(action));
                swing.peer().acknowledge(action);
                sent.get(2, TimeUnit.SECONDS);
            }
        }
    }

    private static TableSession table(GameLaunchContext context,
            TableSession.Starter starter) {
        return new TableSession(new TableSnapshot(0L,
                context.lobby().localNickname(), TableSnapshot.Street.WAITING,
                0d, "", false, List.of(), List.of()), command -> { },
                new TableEventBridge(), starter);
    }

    private static CompletionStage<Void> broadcast(GameLaunchContext context,
            String command) {
        try {
            return context.channel().broadcastFromHost(command, null);
        } catch (IOException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static TableRenderer renderer() {
        return new TableRenderer() {
            @Override public CompletionStage<Void> open(TableSnapshot initialState) {
                return CompletableFuture.completedFuture(null);
            }
            @Override public CompletionStage<Void> render(TableVisualEvent event) {
                return CompletableFuture.completedFuture(null);
            }
            @Override public void close() { }
        };
    }

    private static NewGameRequest request(boolean joining, String nickname, int port) {
        return new NewGameRequest(new NewGameConnectionDraft.Submission(
                joining ? NewGameConnectionDraft.Mode.JOIN
                        : NewGameConnectionDraft.Mode.CREATE,
                nickname, "", "127.0.0.1", Integer.toString(port), null,
                false, false, null),
                joining ? null : new NewGameTableDraft().snapshot());
    }

    private static String initCommand(String sessionId) {
        return "INIT#" + GameConfigCodecV1.encodeBase64(GameConfigCodecV1.fromSettings(
                new NewGameTableDraft().snapshot(), false, sessionId));
    }

    private static String gameBody(String frame) {
        int first = frame.indexOf('#');
        int second = frame.indexOf('#', first + 1);
        return frame.substring(second + 1);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        assertTrue(condition.getAsBoolean(), "condition did not become true before timeout");
    }

    private static final class LegacyServer implements AutoCloseable {
        private final Path directory;
        private final String nickname;
        private final ServerSocket server;
        private LegacyPeer peer;
        private byte[] session;

        LegacyServer(Path directory, String nickname, int port) throws IOException {
            this.directory = directory;
            this.nickname = nickname;
            server = new ServerSocket(port);
        }

        void acceptAndHandshake() throws Exception {
            Socket socket = server.accept();
            socket.setSoTimeout(5_000);
            InputStream input = new BufferedInputStream(socket.getInputStream());
            OutputStream output = new BufferedOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(input);
            byte[] magic = new byte[MAGIC.length];
            in.readFully(magic);
            assertTrue(java.security.MessageDigest.isEqual(MAGIC, magic));
            byte[] clientPublic = readSized(in);
            KeyPair pair = keyPair();
            session = new byte[16];
            for (int index = 0; index < session.length; index++) session[index] = (byte) (index + 1);
            DataOutputStream out = new DataOutputStream(output);
            out.writeInt(pair.getPublic().getEncoded().length);
            out.write(pair.getPublic().getEncoded());
            out.writeInt(session.length);
            out.write(session);
            out.flush();
            SecretKeySpec[] keys = keys(pair, clientPublic, "");
            peer = new LegacyPeer(socket, input, output, keys[0], keys[1]);
            String join = peer.readEncrypted();
            String[] fields = join.split("#", -1);
            assertEquals(6, fields.length);
            assertEquals(AboutDialog.VERSION, fields[1]);
            assertEquals("JOIN", fields[3]);
            assertEquals("GdxClient", decode(fields[0]));
            assertTrue(PlayerIdentity.verifyJoin(session, "GdxClient",
                    Base64.getDecoder().decode(fields[4]),
                    Base64.getDecoder().decode(fields[5])));

            PlayerIdentity identity = PlayerIdentity.loadOrCreate(directory, nickname);
            String settings = new NewGameTableDraft().snapshot().serializeForWire();
            peer.writeEncrypted("NICKOK#0#" + encode("test|0.1/0.2|100")
                    + "#" + encode(settings));
            peer.writeEncrypted(encode(nickname) + "#*#"
                    + Base64.getEncoder().encodeToString(identity.publicKey()) + "#"
                    + Base64.getEncoder().encodeToString(identity.signJoin(session)));
            peer.writeEncrypted("*");
        }

        void sendExistingParticipant(String existingNickname, int gameId)
                throws Exception {
            PlayerIdentity existing = PlayerIdentity.loadOrCreate(
                    directory.resolve("existing"), existingNickname);
            String entry = encode(existingNickname) + "|0|*|"
                    + Base64.getEncoder().encodeToString(existing.publicKey()) + "|"
                    + Base64.getEncoder().encodeToString(existing.signJoin(session));
            // Swing historically terminates USERSLIST entries with '@'. GDX
            // must accept that exact legacy representation.
            peer.writeEncrypted("GAME#" + gameId + "#USERSLIST#" + entry + "@");
        }

        LegacyPeer peer() { return peer; }

        @Override public void close() throws Exception {
            if (peer != null) peer.close();
            server.close();
        }
    }

    private static final class LegacyPeer implements AutoCloseable {
        private final Socket socket;
        private final InputStream input;
        private final OutputStream output;
        private final SecretKeySpec aes;
        private final SecretKeySpec hmac;

        LegacyPeer(Socket socket, InputStream input, OutputStream output,
                SecretKeySpec aes, SecretKeySpec hmac) {
            this.socket = socket;
            this.input = input;
            this.output = output;
            this.aes = aes;
            this.hmac = hmac;
        }

        static LegacyPeer connect(Path directory, String nickname, int port)
                throws Exception {
            Socket socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(5_000);
            InputStream input = new BufferedInputStream(socket.getInputStream());
            OutputStream output = new BufferedOutputStream(socket.getOutputStream());
            output.write(MAGIC);
            KeyPair pair = keyPair();
            DataOutputStream out = new DataOutputStream(output);
            out.writeInt(pair.getPublic().getEncoded().length);
            out.write(pair.getPublic().getEncoded());
            out.flush();
            DataInputStream in = new DataInputStream(input);
            byte[] serverPublic = readSized(in);
            byte[] session = readSized(in);
            SecretKeySpec[] keys = keys(pair, serverPublic, "");
            LegacyPeer peer = new LegacyPeer(socket, input, output, keys[0], keys[1]);
            PlayerIdentity identity = PlayerIdentity.loadOrCreate(directory, nickname);
            peer.writeEncrypted(encode(nickname) + "#" + AboutDialog.VERSION
                    + "#*#JOIN#" + Base64.getEncoder().encodeToString(identity.publicKey())
                    + "#" + Base64.getEncoder().encodeToString(identity.signJoin(session)));
            assertTrue(peer.readEncrypted().startsWith("NICKOK#"));
            String intro = peer.readEncrypted();
            assertNotNull(intro);
            assertEquals("GdxHost", decode(intro.split("#", -1)[0]));
            assertNotNull(peer.readEncrypted());
            return peer;
        }

        void writeEncrypted(String clear) throws IOException {
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            String frame = Helpers.encryptCommand(clear, aes, iv, hmac);
            output.write((frame + "\n").getBytes(StandardCharsets.UTF_8));
            output.flush();
        }

        String readEncrypted() throws Exception {
            WireFrame.Result frame = WireFrame.read(input, Helpers.MAX_COMMAND_LINE_CHARS);
            assertNotNull(frame);
            assertTrue(frame.isText());
            return Helpers.decryptCommand(frame.text(), aes, hmac);
        }

        String readGame(String subcommand) throws Exception {
            while (true) {
                String frame = readApplicationFrame();
                if (frame.startsWith("GAME#")
                        && gameBody(frame).startsWith(subcommand + "#")) return frame;
            }
        }

        /**
         * Reads the next application frame while servicing the independent
         * Swing heartbeat exactly as a real peer does.  A PING may legally be
         * interleaved with an acknowledgement, so treating the next socket
         * line as the expected CONF makes this protocol canary racy.
         */
        String readApplicationFrame() throws Exception {
            while (true) {
                String frame = readEncrypted();
                if (!frame.startsWith("PING#")) return frame;
                String[] parts = frame.split("#", -1);
                output.write(("PONG#" + (Integer.parseInt(parts[1]) + 1) + "\n")
                        .getBytes(StandardCharsets.UTF_8));
                output.write(("PONG2#" + (Integer.parseInt(parts[1]) + 2) + "\n")
                        .getBytes(StandardCharsets.UTF_8));
                output.flush();
            }
        }

        void acknowledge(String gameFrame) throws IOException {
            String[] parts = gameFrame.split("#", 3);
            writeEncrypted("CONF#" + (Integer.parseInt(parts[1]) + 1) + "#OK");
        }

        @Override public void close() throws IOException { socket.close(); }
    }

    private static SecretKeySpec[] keys(KeyPair local, byte[] remoteEncoded,
            String password) throws Exception {
        PublicKey remote = KeyFactory.getInstance("EC")
                .generatePublic(new X509EncodedKeySpec(remoteEncoded));
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(local.getPrivate());
        agreement.doPhase(remote, true);
        byte[] secret = Helpers.deriveChannelSecret(agreement.generateSecret(), password);
        return new SecretKeySpec[]{new SecretKeySpec(secret, 0, 32, "AES"),
            new SecretKeySpec(secret, 32, 32, "HmacSHA256")};
    }

    private static KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        return generator.generateKeyPair();
    }

    private static byte[] readSized(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length <= 0 || length > 64 * 1024) throw new IOException("invalid length " + length);
        byte[] result = new byte[length];
        input.readFully(result);
        return result;
    }

    private static String encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String text) {
        return new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8);
    }
}
