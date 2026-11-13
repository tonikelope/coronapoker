/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.tonikelope.coronapoker.core.BotNickname;
import com.tonikelope.coronapoker.core.StatsDashboard;
import com.tonikelope.coronapoker.core.StatsRepository;
import java.awt.EventQueue;
import java.awt.event.KeyEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.cef.CefBrowserSettings;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.browser.CefRequestContext;
import org.cef.browser.LibGdxCefBrowser;
import org.cef.callback.CefCallback;
import org.cef.callback.CefContextMenuParams;
import org.cef.callback.CefMenuModel;
import org.cef.callback.CefQueryCallback;
import org.cef.callback.CefResourceReadCallback;
import org.cef.handler.CefContextMenuHandlerAdapter;
import org.cef.handler.CefFocusHandlerAdapter;
import org.cef.handler.CefLifeSpanHandlerAdapter;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.handler.CefResourceHandler;
import org.cef.handler.CefResourceHandlerAdapter;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.handler.CefResourceRequestHandlerAdapter;
import org.cef.misc.BoolRef;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;

/** One isolated statistics browser and one immutable analysis snapshot per opening. */
final class PokerStatsBrowser implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(PokerStatsBrowser.class.getName());
    private static final String ORIGIN = "https://stats.coronapoker.invalid";
    private static final String ROOT = "/poker-stats/";
    private static final String CSP = "default-src 'none'; script-src 'self'; "
            + "style-src 'self' 'unsafe-inline'; font-src 'self'; img-src 'self' data:; "
            + "base-uri 'none'; form-action 'none'; frame-ancestors 'none'";
    private static final long STARTUP_TIMEOUT = TimeUnit.SECONDS.toNanos(35);
    private static final double SCROLL_PIXELS_PER_STEP = 96d;

    private final StatsRepository repository;
    private final String preferredPlayer;
    private final String pageUrl;
    private final PokerVideoFrameBuffer frames = new PokerVideoFrameBuffer();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "CoronaPoker-statistics-browser");
        thread.setDaemon(true);
        return thread;
    });
    private final long started = System.nanoTime();
    private final Json json = new Json(JsonWriter.OutputType.json);

    private volatile boolean closed;
    private volatile boolean ready;
    private volatile boolean failed;
    private volatile LibGdxCefBrowser browser;
    private volatile Selection selection = new Selection(0, 0, "", false);
    private CefClient client;
    private boolean creatingBrowser;
    private boolean closingBrowser;
    private StatsDashboard snapshot;
    private List<GameOption> games;
    private int handsGameId = -1;
    private List<HandOption> hands = List.of();
    private final GdxBrowserKeyboard keyboard = new GdxBrowserKeyboard();
    private boolean pressed;
    private double scrollRemainder;
    private int width;
    private int height;

    PokerStatsBrowser(StatsRepository repository, String player, boolean english,
            boolean animate, int width, int height, GdxChipAmountText.ZeroMode zeroMode) {
        this.repository = repository;
        preferredPlayer = player;
        this.width = width;
        this.height = height;
        pageUrl = ORIGIN + ROOT + "index.html?lang=" + (english ? "en" : "es")
                + "&motion=" + (animate ? "1" : "0") + "&botMarker=" + BotNickname.MARKER
                + "&amountZeros=" + zeroMode.setting;
        json.setUsePrototypes(false);
        json.setTypeName(null);
        double scale = scale();
        worker.execute(() -> open(width, height, scale));
    }

    private void open(int width, int height, double scale) {
        try {
            Map<String, Asset> assets = Map.of(
                    pageUrl, asset(ROOT + "index.html", "text/html"),
                    ORIGIN + ROOT + "dashboard.css", asset(ROOT + "dashboard.css", "text/css"),
                    ORIGIN + ROOT + "dashboard.js", asset(ROOT + "dashboard.js", "text/javascript"),
                    ORIGIN + "/ui-tooltip.js", asset("/ui-tooltip.js", "text/javascript"),
                    ORIGIN + "/ui-tooltip.css", asset("/ui-tooltip.css", "text/css"),
                    ORIGIN + ROOT + "echarts.min.js", asset(ROOT + "echarts.min.js", "text/javascript"),
                    ORIGIN + "/fonts/McLaren-Regular.ttf", asset("/fonts/McLaren-Regular.ttf", "font/ttf"));
            if (closed) return;
            CefClient created = PokerChromium.createClient(null);
            synchronized (this) {
                if (closed) {
                    EventQueue.invokeLater(() -> PokerChromium.disposeClient(created));
                    return;
                }
                client = created;
                installHandlers(created, assets);
                CefBrowserSettings settings = new CefBrowserSettings();
                settings.windowless_frame_rate = 60;
                CefRequestContext context = CefRequestContext.createContext(null);
                if (context == null) throw new IllegalStateException("No isolated Chromium context");
                try {
                    // The browser owns and disposes this context on native close.
                    browser = new LibGdxCefBrowser(created, pageUrl, context, settings,
                            frames, width, height);
                } catch (RuntimeException | LinkageError failure) {
                    context.dispose();
                    throw failure;
                }
                frames.setAcceptingFrames(true);
                creatingBrowser = true;
                try {
                    browser.createImmediately();
                } catch (RuntimeException | LinkageError failure) {
                    if (creatingBrowser) {
                        creatingBrowser = false;
                        LibGdxCefBrowser unregistered = browser;
                        browser = null;
                        unregistered.onBeforeClose();
                    }
                    throw failure;
                }
                browser.resizeBrowser(width, height, scale);
            }
        } catch (Exception | LinkageError failure) {
            if (!closed) {
                LOG.log(Level.WARNING, "Could not start statistics browser", failure);
                failed = true;
            }
        }
    }

    private void installHandlers(CefClient target, Map<String, Asset> assets) {
        CefResourceRequestHandler offline = new CefResourceRequestHandlerAdapter() {
            @Override
            public CefResourceHandler getResourceHandler(CefBrowser source, CefFrame frame,
                    CefRequest request) {
                Asset asset = "GET".equals(request.getMethod()) ? assets.get(request.getURL()) : null;
                return asset == null ? null : new AssetHandler(asset);
            }
        };
        target.addRequestHandler(new CefRequestHandlerAdapter() {
            @Override
            public boolean onBeforeBrowse(CefBrowser source, CefFrame frame,
                    CefRequest request, boolean gesture, boolean redirect) {
                return frame == null || !frame.isMain() || !pageUrl.equals(request.getURL());
            }

            @Override
            public CefResourceRequestHandler getResourceRequestHandler(CefBrowser source,
                    CefFrame frame, CefRequest request, boolean navigation, boolean download,
                    String initiator, BoolRef disableDefaultHandling) {
                disableDefaultHandling.set(true);
                return offline;
            }

            @Override
            public void onRenderProcessTerminated(CefBrowser source,
                    TerminationStatus status, int errorCode, String errorString) {
                failed = true;
            }
        });
        target.addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
            @Override
            public boolean onBeforePopup(CefBrowser source, CefFrame frame,
                    String url, String targetFrame) {
                return true;
            }

            @Override
            public void onAfterCreated(CefBrowser created) {
                synchronized (PokerStatsBrowser.this) {
                    creatingBrowser = false;
                    if (closed) {
                        EventQueue.invokeLater(PokerStatsBrowser.this::closeBrowserOrClient);
                        return;
                    }
                }
                if (created instanceof LibGdxCefBrowser rendered) {
                    rendered.requestRepaint();
                }
            }

            @Override
            public void onBeforeClose(CefBrowser closing) {
                synchronized (PokerStatsBrowser.this) {
                    creatingBrowser = false;
                    closingBrowser = false;
                    browser = null;
                    if (closed) {
                        EventQueue.invokeLater(PokerStatsBrowser.this::closeBrowserOrClient);
                    } else {
                        ready = false;
                        failed = true;
                    }
                }
            }
        });
        target.addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadError(CefBrowser source, CefFrame frame, ErrorCode errorCode,
                    String errorText, String failedUrl) {
                if (frame != null && frame.isMain() && pageUrl.equals(failedUrl) && !closed) {
                    LOG.log(Level.WARNING, "Statistics page load failed: {0}", errorCode);
                    failed = true;
                }
            }
        });
        target.addFocusHandler(new CefFocusHandlerAdapter() {
            @Override
            public boolean onSetFocus(CefBrowser source, FocusSource focusSource) {
                return focusSource == FocusSource.FOCUS_SOURCE_NAVIGATION;
            }
        });
        target.addContextMenuHandler(new CefContextMenuHandlerAdapter() {
            @Override
            public void onBeforeContextMenu(CefBrowser source, CefFrame frame,
                    CefContextMenuParams params, CefMenuModel menu) {
                menu.clear();
            }
        });
        CefMessageRouter router = CefMessageRouter.create();
        router.addHandler(new CefMessageRouterHandlerAdapter() {
            @Override
            public boolean onQuery(CefBrowser source, CefFrame frame, long id,
                    String request, boolean persistent, CefQueryCallback reply) {
                if (closed || source != browser || frame == null || !frame.isMain()
                        || !pageUrl.equals(frame.getURL()) || persistent
                        || request == null || request.length() > 65) return false;
                switch (request) {
                    case "ready" -> {
                        ready = true;
                        reply.success("");
                    }
                    case "broken" -> {
                        failed = true;
                        reply.success("");
                    }
                    default -> {
                        try {
                            worker.execute(() -> answer(request, reply));
                        } catch (RejectedExecutionException closing) {
                            reply.failure(503, "Statistics are closing");
                        }
                    }
                }
                return true;
            }
        }, true);
        target.addMessageRouter(router);
    }

    private void answer(String request, CefQueryCallback reply) {
        if (closed) return;
        try {
            String response = responseFor(request);
            if (!closed) reply.success(response);
        } catch (DateTimeParseException | IllegalArgumentException invalid) {
            if (!closed) reply.failure(400, "Invalid statistics filter");
        } catch (Exception failure) {
            if (!closed) {
                LOG.log(Level.WARNING, "Could not read poker statistics", failure);
                reply.failure(500, "Could not read poker statistics");
            }
        }
    }

    private String responseFor(String request) throws SQLException {
        if ("games".equals(request)) return json.toJson(gameOptions());
        String[] parts = request.split("\\t", -1);
        if (parts.length == 4 && "select".equals(parts[0])) {
            boolean excludeBots = switch (parts[3]) {
                case "0" -> false;
                case "1" -> true;
                default -> throw new IllegalArgumentException("Invalid bot filter");
            };
            return json.toJson(select(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), excludeBots));
        }
        if (snapshot == null || parts.length != 5) {
            throw new IllegalArgumentException("Unknown statistics request");
        }
        int scope = Integer.parseInt(parts[1]);
        LocalDate from = parseDate(parts[3]);
        LocalDate through = parseDate(parts[4]);
        if (scope != selection.gameId || selection.handId != 0
                || from != null && through != null && through.isBefore(from)) {
            throw new IllegalArgumentException("Invalid statistics filter");
        }
        return switch (parts[0]) {
            case "classic" -> json.toJson(repository.classic(
                    StatsRepository.ClassicMode.valueOf(parts[2]),
                    scope == 0 ? null : scope, from, through, selection.excludeBots));
            case "view" -> {
                int index = Integer.parseInt(parts[2]);
                List<String> players = players(scope, selection.excludeBots);
                if (index < 0 || index >= players.size()) {
                    throw new IllegalArgumentException("Unknown player");
                }
                String player = players.get(index);
                String result = json.toJson(snapshot.view(player, scope, from, through, selection.excludeBots));
                selection = new Selection(scope, 0, player, selection.excludeBots);
                yield result;
            }
            default -> throw new IllegalArgumentException("Unknown statistics request");
        };
    }

    private List<GameOption> gameOptions() throws SQLException {
        if (games == null) {
            games = repository.games().stream().map(game ->
                    new GameOption(game.id(), game.server(), game.startedAtMillis())).toList();
        }
        return games;
    }

    private Setup select(int gameId, int handId, boolean excludeBots) throws SQLException {
        if (gameId < 0 || handId < 0 || gameId == 0 && handId != 0
                || gameId > 0 && gameOptions().stream().noneMatch(game -> game.id == gameId)) {
            throw new IllegalArgumentException("Unknown game selection");
        }
        if (handsGameId != gameId) {
            hands = gameId == 0 ? List.of() : repository.hands(gameId).stream()
                    .map(hand -> new HandOption(hand.id(), hand.counter())).toList();
            handsGameId = gameId;
        }
        String previousPlayer = selection.player;
        if (handId != 0) {
            if (hands.stream().noneMatch(hand -> hand.id == handId)) {
                throw new IllegalArgumentException("Unknown hand selection");
            }
            StatsRepository.HandDetail detail = repository.handDetail(gameId, handId);
            selection = new Selection(gameId, handId, previousPlayer, excludeBots);
            return new Setup(gameId, handId, hands, List.of(), 0, detail);
        }
        if (snapshot == null) snapshot = repository.dashboard();
        List<String> players = players(gameId, excludeBots);
        int previous = players.indexOf(previousPlayer);
        int selected = previous >= 0 ? previous : Math.max(0, players.indexOf(preferredPlayer));
        selection = new Selection(gameId, 0, players.isEmpty() ? "" : players.get(selected), excludeBots);
        return new Setup(gameId, 0, hands, players, selected, null);
    }

    private List<String> players(int gameId, boolean excludeBots) {
        List<String> players = snapshot.players(gameId);
        return excludeBots ? players.stream().filter(player -> !BotNickname.isBot(player)).toList() : players;
    }

    private static LocalDate parseDate(String value) {
        if (value.isEmpty()) return null;
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || value.startsWith("0000")) {
            throw new DateTimeParseException("Expected ISO date", value, 0);
        }
        return LocalDate.parse(value);
    }

    boolean failed() {
        return failed || !interactive() && System.nanoTime() - started > STARTUP_TIMEOUT;
    }

    Selection selection() {
        return selection;
    }

    Texture texture(int width, int height) {
        this.width = width;
        this.height = height;
        LibGdxCefBrowser current = browser;
        if (current != null) {
            current.resizeBrowser(width, height, scale());
        }
        return ready ? frames.uploadPendingFrame() : null;
    }

    private static double scale() {
        return Math.min(Gdx.graphics.getBackBufferWidth() / (double) Math.max(1, Gdx.graphics.getWidth()),
                Gdx.graphics.getBackBufferHeight() / (double) Math.max(1, Gdx.graphics.getHeight()));
    }

    boolean pressed() {
        return pressed;
    }

    boolean interactive() {
        return ready && frames.uploadedFrames() > 0;
    }

    void unfocus() {
        keyboard.clear();
        pressed = false;
        LibGdxCefBrowser current = browser;
        if (current != null) current.setFocus(false);
    }

    void mouse(int event, float x, float y) {
        LibGdxCefBrowser current = browser;
        if (!ready || current == null) return;
        if (event == MouseEvent.MOUSE_PRESSED) {
            keyboard.clear();
            current.setFocus(true);
            pressed = true;
        } else if (event == MouseEvent.MOUSE_RELEASED) {
            pressed = false;
        }
        int button = event == MouseEvent.MOUSE_PRESSED || event == MouseEvent.MOUSE_RELEASED
                ? MouseEvent.BUTTON1 : MouseEvent.NOBUTTON;
        current.dispatchMouse(event, Math.round(x * width), Math.round(y * height),
                button, modifiers(), button == MouseEvent.BUTTON1 ? 1 : 0);
    }

    void wheel(float x, float y, float amount) {
        LibGdxCefBrowser current = browser;
        if (!ready || current == null || !Float.isFinite(amount) || amount == 0f) return;
        int mouseX = Math.round(x * width);
        int mouseY = Math.round(y * height);
        int modifiers = modifiers();
        if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) {
            int steps = Math.max(1, Math.round(Math.abs(amount)));
            current.dispatchWheel(mouseX, mouseY, modifiers, amount > 0 ? -steps : steps);
            return;
        }
        // CEF consumes pixels with the opposite sign to GDX. Preserve subpixel
        // remainders from precision wheels and touchpads instead of rounding to a notch.
        double distance = amount * SCROLL_PIXELS_PER_STEP + scrollRemainder;
        int pixels = (int) distance;
        scrollRemainder = distance - pixels;
        if (pixels != 0) current.dispatchWheelPixels(mouseX, mouseY, modifiers, -pixels);
    }

    void key(int event, int key, char character) {
        LibGdxCefBrowser current = browser;
        if (!ready || current == null) return;
        int modifiers = modifiers();
        if (keyboard.key(current, event, key, character, modifiers)) return;
        int awt = awtKey(key);
        if (awt != KeyEvent.VK_UNDEFINED) {
            current.dispatchKey(event, awt, KeyEvent.CHAR_UNDEFINED, modifiers);
        }
    }

    void updateKeyboard(float delta, boolean enabled) {
        keyboard.update(delta, enabled && ready ? browser : null, this::modifiers);
    }

    private int modifiers() {
        return LibGdxCefBrowser.awtModifiers(
                either(Input.Keys.SHIFT_LEFT, Input.Keys.SHIFT_RIGHT),
                either(Input.Keys.CONTROL_LEFT, Input.Keys.CONTROL_RIGHT),
                either(Input.Keys.ALT_LEFT, Input.Keys.ALT_RIGHT),
                Gdx.input.isKeyPressed(Input.Keys.SYM), pressed, false, false);
    }

    private static boolean either(int first, int second) {
        return Gdx.input.isKeyPressed(first) || Gdx.input.isKeyPressed(second);
    }

    private static int awtKey(int key) {
        if (key >= Input.Keys.A && key <= Input.Keys.Z) return KeyEvent.VK_A + key - Input.Keys.A;
        if (key >= Input.Keys.NUM_0 && key <= Input.Keys.NUM_9) return KeyEvent.VK_0 + key - Input.Keys.NUM_0;
        return switch (key) {
            case Input.Keys.SPACE -> KeyEvent.VK_SPACE;
            case Input.Keys.MINUS -> KeyEvent.VK_MINUS;
            case Input.Keys.EQUALS -> KeyEvent.VK_EQUALS;
            case Input.Keys.SHIFT_LEFT, Input.Keys.SHIFT_RIGHT -> KeyEvent.VK_SHIFT;
            case Input.Keys.CONTROL_LEFT, Input.Keys.CONTROL_RIGHT -> KeyEvent.VK_CONTROL;
            case Input.Keys.ALT_LEFT, Input.Keys.ALT_RIGHT -> KeyEvent.VK_ALT;
            default -> KeyEvent.VK_UNDEFINED;
        };
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        ready = false;
        keyboard.clear();
        worker.shutdownNow();
        frames.setAcceptingFrames(false);
        try {
            EventQueue.invokeLater(this::closeBrowserOrClient);
        } finally {
            frames.close();
        }
    }

    private void closeBrowserOrClient() {
        LibGdxCefBrowser current;
        CefClient disposing = null;
        synchronized (this) {
            if (!closed || client == null || creatingBrowser || closingBrowser) return;
            current = browser;
            if (current != null) {
                closingBrowser = true;
            } else {
                disposing = client;
                client = null;
            }
        }
        if (current == null) {
            PokerChromium.disposeClient(disposing);
        } else {
            current.setWindowVisibility(false);
            current.setCloseAllowed();
            current.close(true);
        }
    }

    record Selection(int gameId, int handId, String player, boolean excludeBots) { }

    private record GameOption(int id, String server, long startedAtMillis) { }

    private record HandOption(int id, int counter) { }

    private record Setup(int scope, int handId, List<HandOption> hands,
            List<String> players, int selected, StatsRepository.HandDetail detail) { }

    private record Asset(byte[] bytes, String mimeType) { }

    private static Asset asset(String path, String mimeType) throws IOException {
        try (InputStream source = PokerStatsBrowser.class.getResourceAsStream(path)) {
            if (source == null) throw new IOException("Missing dashboard asset " + path);
            return new Asset(source.readAllBytes(), mimeType);
        }
    }

    private static final class AssetHandler extends CefResourceHandlerAdapter {
        private final Asset asset;
        private int offset;

        AssetHandler(Asset asset) {
            this.asset = asset;
        }

        @Override
        public boolean open(CefRequest request, BoolRef handleRequest, CefCallback callback) {
            handleRequest.set(true);
            return true;
        }

        @Override
        public boolean processRequest(CefRequest request, CefCallback callback) {
            callback.Continue();
            return true;
        }

        @Override
        public void getResponseHeaders(CefResponse response, IntRef length, StringRef redirect) {
            response.setStatus(200);
            response.setStatusText("OK");
            response.setMimeType(asset.mimeType);
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Security-Policy", CSP);
            headers.put("X-Content-Type-Options", "nosniff");
            headers.put("Cache-Control", "no-store");
            response.setHeaderMap(headers);
            length.set(asset.bytes.length);
        }

        @Override
        public boolean read(byte[] output, int requested, IntRef count, CefResourceReadCallback callback) {
            return copy(output, requested, count);
        }

        @Override
        public boolean readResponse(byte[] output, int requested, IntRef count, CefCallback callback) {
            return copy(output, requested, count);
        }

        private boolean copy(byte[] output, int requested, IntRef count) {
            int size = Math.min(requested, asset.bytes.length - offset);
            count.set(size);
            if (size <= 0) return false;
            System.arraycopy(asset.bytes, offset, output, 0, size);
            offset += size;
            return true;
        }
    }
}
