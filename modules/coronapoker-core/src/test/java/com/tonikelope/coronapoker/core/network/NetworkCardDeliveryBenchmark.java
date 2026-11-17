package com.tonikelope.coronapoker.core.network;

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
import com.tonikelope.coronapoker.table.TableCommand;
import com.tonikelope.coronapoker.table.TableRenderer;
import com.tonikelope.coronapoker.table.TableSession;
import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Opt-in benchmark of a complete production hand over real encrypted sockets. */
public final class NetworkCardDeliveryBenchmark {

    private NetworkCardDeliveryBenchmark() { }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createDirectories(Path.of(args[0]));
        int seats = Integer.parseInt(args[1]);
        int remote = Integer.parseInt(args[2]);
        int jitter = Integer.parseInt(args[3]);
        run(directory, seats, remote, jitter);
    }

    private static void run(Path directory, int seats, int remote, int jitter) throws Exception {
        if (seats < 2 || seats > 10 || remote < 0 || remote >= seats || jitter < 0 || jitter > 100) {
            throw new IllegalArgumentException("Expected 2-10 seats, fewer remote seats, and 0-100 ms one-way jitter");
        }
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        AtomicLong shuffleStart = new AtomicLong();
        AtomicLong shuffleVerified = new AtomicLong();
        AtomicLongArray streetStarts = new AtomicLongArray(3);
        List<LatencyProxy> proxies = new ArrayList<>();
        Traffic[] startTraffic = new Traffic[1];
        Traffic[] endTraffic = new Traffic[1];
        List<AutoCloseable> resources = new ArrayList<>();
        List<LobbySession> lobbies = new ArrayList<>();
        List<BenchRenderer> renderers = new ArrayList<>();
        Logger logger = Logger.getLogger(Crupier.class.getName());
        Handler timings = new Handler() {
            @Override public void publish(LogRecord record) {
                if ("Initiating SRA street unlock: {0}".equals(record.getMessage())) {
                    int index = (int) record.getParameters()[0] - Crupier.FLOP;
                    streetStarts.set(index, System.nanoTime());
                }
            }
            @Override public void flush() { }
            @Override public void close() { }
        };
        logger.addHandler(timings);
        try {
            for (int seat = 0; seat < seats; seat++) {
                Path data = directory.resolve("seat-" + seat);
                Files.createDirectories(data);
                DatabaseService db = new DatabaseService(data.resolve("game.sqlite").toString());
                resources.add(db);
                db.start();
                boolean host = seat == 0;
                GameLogSink log = host ? message -> {
                    if (message.startsWith("game.barajado_verificado")) {
                        shuffleVerified.compareAndSet(0L, System.nanoTime());
                    }
                } : GameLogSink.noop();
                NetworkLobbyGateway gateway = new NetworkLobbyGateway(data, tableFactory(db, log));
                resources.add(gateway);
                int joinPort = port;
                if (!host) {
                    LatencyProxy proxy = new LatencyProxy(port, seat <= remote ? 150 : 1,
                            seat <= remote ? jitter : 0, seat);
                    proxies.add(proxy);
                    resources.add(proxy);
                    joinPort = proxy.port();
                }
                LobbySession lobby = gateway.open(request(host, seat, joinPort)).get(20, TimeUnit.SECONDS);
                lobbies.add(lobby);
                resources.add(lobby);
            }
            LobbySession host = lobbies.get(0);
            if (host.snapshot().participants().size() != seats) {
                throw new AssertionError("Host roster is incomplete");
            }
            host.submit(new LobbyCommand.StartGame()).toCompletableFuture().get(20, TimeUnit.SECONDS);
            for (LobbySession lobby : lobbies) {
                TableSession table = lobby.tableSession().toCompletableFuture().get(30, TimeUnit.SECONDS);
                Runnable onShuffle = lobby == host ? () -> {
                    startTraffic[0] = Traffic.read(proxies);
                    shuffleStart.set(System.nanoTime());
                } : () -> { };
                Runnable onHandEnd = lobby == host ? () -> endTraffic[0] = Traffic.read(proxies) : () -> { };
                BenchRenderer renderer = new BenchRenderer(table, onShuffle, onHandEnd);
                renderers.add(renderer);
                table.attach(renderer).toCompletableFuture().get(20, TimeUnit.SECONDS);
            }
            CompletableFuture.allOf(renderers.stream().map(r -> r.finished)
                    .toArray(CompletableFuture<?>[]::new)).get(3, TimeUnit.MINUTES);

            long lastPocket = renderers.stream().mapToLong(r -> r.pocket.get()).max().orElseThrow();
            double[] streets = new double[3];
            for (int i = 0; i < streets.length; i++) {
                int street = i;
                long last = renderers.stream().mapToLong(r -> r.board.get(street)).max().orElseThrow();
                if (renderers.stream().anyMatch(r -> r.board.get(street) == 0L)) {
                    throw new AssertionError("Peer did not reveal street " + street);
                }
                streets[i] = elapsedMs(streetStarts.get(i), last);
            }
            if (renderers.stream().anyMatch(r -> r.pocket.get() == 0L)) {
                throw new AssertionError("Peer did not reach private cards");
            }
            System.out.printf(Locale.ROOT, "CARD_BENCH seats=%d remote=%d jitter=%d "
                    + "shuffle_ms=%.1f pocket_ms=%.1f flop_ms=%.1f turn_ms=%.1f river_ms=%.1f "
                    + "wire_bytes=%d wire_lines=%d%n", seats, remote, jitter,
                    elapsedMs(shuffleStart.get(), shuffleVerified.get()),
                    elapsedMs(shuffleVerified.get(), lastPocket), streets[0], streets[1], streets[2],
                    endTraffic[0].bytes - startTraffic[0].bytes,
                    endTraffic[0].lines - startTraffic[0].lines);
        } finally {
            logger.removeHandler(timings);
            for (int index = resources.size() - 1; index >= 0; index--) {
                resources.get(index).close();
            }
        }
    }

    private static double elapsedMs(long start, long end) {
        if (start == 0L || end < start) throw new AssertionError("Missing or inverted timing sample");
        return (end - start) / 1_000_000.0;
    }

    private static NewGameRequest request(boolean host, int seat, int port) {
        var connection = new NewGameConnectionDraft.Submission(
                host ? NewGameConnectionDraft.Mode.CREATE : NewGameConnectionDraft.Mode.JOIN,
                "Seat" + seat, "", "127.0.0.1", Integer.toString(port), null, false, false, null);
        if (!host) return new NewGameRequest(connection, null);
        var table = new NewGameTableDraft();
        table.setHandLimit(true);
        table.setHandLimitCount(1);
        table.setThinkTime(false);
        return new NewGameRequest(connection, table.snapshot());
    }

    private static CoreGameTableFactory tableFactory(DatabaseService db, GameLogSink log) {
        GamePresentationSettings defaults = GamePresentationSettings.defaults();
        var settings = (GamePresentationSettings) Proxy.newProxyInstance(
                GamePresentationSettings.class.getClassLoader(),
                new Class<?>[]{GamePresentationSettings.class},
                (proxy, method, args) -> "testMode".equals(method.getName())
                        ? true : method.invoke(defaults, args));
        var assets = new GameCinematicAssets() {
            @Override public long durationMillis(String name) { return 0L; }
            @Override public boolean hasCinematic(String name) { return false; }
            @Override public boolean hasCompanionAudio(String name) { return false; }
        };
        return new CoreGameTableFactory(db, GameText.keys(), log, GameDialogSink.noop(),
                GameDecisionSink.noop(), settings, assets);
    }

    private static final class BenchRenderer implements TableRenderer {
        private final TableSession table;
        private final Runnable onShuffle;
        private final Runnable onHandEnd;
        private final AtomicLong pocket = new AtomicLong();
        private final AtomicLongArray board = new AtomicLongArray(3);
        private final CompletableFuture<Void> finished = new CompletableFuture<>();

        BenchRenderer(TableSession table, Runnable onShuffle, Runnable onHandEnd) {
            this.table = table;
            this.onShuffle = onShuffle;
            this.onHandEnd = onHandEnd;
        }

        @Override public CompletionStage<Void> open(TableSnapshot initialState) {
            return CompletableFuture.completedFuture(null);
        }

        @Override public CompletionStage<Void> render(TableVisualEvent event) {
            if (event instanceof TableVisualEvent.Shuffle shuffle
                    && shuffle.phase() == TableVisualEvent.Shuffle.Phase.START) {
                onShuffle.run();
            } else if (event instanceof TableVisualEvent.DealHoleCard deal
                    && deal.nickname().equals(table.initialState().localNickname())) {
                pocket.set(System.nanoTime());
            } else if (event instanceof TableVisualEvent.RevealCommunityCards reveal) {
                int index = switch (reveal.street()) {
                    case FLOP -> 0;
                    case TURN -> 1;
                    case RIVER -> 2;
                    default -> throw new AssertionError("Unexpected board street");
                };
                board.compareAndSet(index, 0L, System.nanoTime());
            } else if (event instanceof TableVisualEvent.ActionControls controls
                    && controls.state().callAction() != ActionControlState.CallAction.DISABLED) {
                table.commands().submit(new TableCommand.CheckOrCall());
            } else if (event instanceof TableVisualEvent.HandBoundary boundary
                    && boundary.phase() == TableVisualEvent.HandBoundary.Phase.END) {
                onHandEnd.run();
                finished.complete(null);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override public void close() { }
    }

    private record Traffic(long bytes, long lines) {
        static Traffic read(List<LatencyProxy> proxies) {
            return new Traffic(proxies.stream().mapToLong(proxy -> proxy.bytes.get()).sum(),
                    proxies.stream().mapToLong(proxy -> proxy.lines.get()).sum());
        }
    }

    /** Delays queued chunks in TCP order in each direction without decoding them. */
    private static final class LatencyProxy implements Closeable {
        private final ServerSocket listener = new ServerSocket(0);
        private final AtomicLong bytes = new AtomicLong();
        private final AtomicLong lines = new AtomicLong();
        private final int hostPort;
        private final int delayMs;
        private final int jitter;
        private final int seed;

        LatencyProxy(int hostPort, int delayMs, int jitter, int seed) throws IOException {
            this.hostPort = hostPort;
            this.delayMs = delayMs;
            this.jitter = jitter;
            this.seed = seed;
            startWorker(this::accept);
        }

        int port() { return listener.getLocalPort(); }

        private void accept() {
            while (!listener.isClosed()) {
                try {
                    Socket client = listener.accept();
                    startWorker(() -> connect(client));
                } catch (IOException closed) {
                    return;
                }
            }
        }

        private void connect(Socket client) {
            try (client; Socket host = new Socket("127.0.0.1", hostPort)) {
                client.setTcpNoDelay(true);
                host.setTcpNoDelay(true);
                Thread upstream = startWorker(() -> pipe(client, host, seed * 2));
                pipe(host, client, seed * 2 + 1);
                upstream.join();
            } catch (IOException closed) {
                // EOF and reset are observable by the actual test endpoints.
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        private void pipe(Socket from, Socket to, int pipeSeed) {
            var queue = new LinkedBlockingQueue<Chunk>(1024);
            Thread reader = startWorker(() -> read(from, pipeSeed, queue));
            try (var output = to.getOutputStream()) {
                for (Chunk chunk = queue.take(); chunk.payload.length != 0; chunk = queue.take()) {
                    long left;
                    while ((left = chunk.due - System.nanoTime()) > 0L) {
                        LockSupport.parkNanos(left);
                    }
                    output.write(chunk.payload);
                    bytes.addAndGet(chunk.payload.length);
                    for (byte b : chunk.payload) {
                        if (b == '\n') lines.incrementAndGet();
                    }
                }
            } catch (IOException closed) {
                // Closing the output also releases the other direction's reader.
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                reader.interrupt();
            }
        }

        private void read(Socket from, int pipeSeed, LinkedBlockingQueue<Chunk> queue) {
            Random random = new Random(pipeSeed);
            byte[] buffer = new byte[8192];
            try {
                var in = from.getInputStream();
                for (int length = in.read(buffer); length != -1; length = in.read(buffer)) {
                    long delay = delayMs + random.nextInt(-jitter, jitter + 1);
                    queue.put(new Chunk(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delay),
                            Arrays.copyOf(buffer, length)));
                }
            } catch (IOException closed) {
                // Signal EOF after the bytes already queued.
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                queue.put(new Chunk(0L, new byte[0]));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        @Override public void close() throws IOException { listener.close(); }

        private static Thread startWorker(Runnable body) {
            Thread thread = new Thread(body, "card-benchmark-latency-proxy");
            thread.setDaemon(true);
            thread.start();
            return thread;
        }

        private record Chunk(long due, byte[] payload) { }
    }
}
