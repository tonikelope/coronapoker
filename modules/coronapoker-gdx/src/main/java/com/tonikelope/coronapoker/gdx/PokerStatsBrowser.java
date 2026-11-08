/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.tonikelope.coronapoker.core.StatsDashboard;
import com.tonikelope.coronapoker.core.StatsRepository;
import java.awt.event.KeyEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.InputStream;
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
    private volatile Scope selection;
    private CefClient client;
    private StatsDashboard snapshot;
    private boolean pressed;
    private double scrollRemainder;
    private int width;
    private int height;

    PokerStatsBrowser(StatsRepository repository, String player, boolean english,
            boolean animate, int gameId, int handId, int width, int height) {
        this.repository = repository;
        preferredPlayer = player;
        selection = new Scope(gameId, handId);
        this.width = width;
        this.height = height;
        pageUrl = ORIGIN + ROOT + "index.html?lang=" + (english ? "en" : "es")
                + "&motion=" + (animate ? "1" : "0");
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
                    ORIGIN + ROOT + "echarts.min.js", asset(ROOT + "echarts.min.js", "text/javascript"),
                    ORIGIN + "/fonts/Inter-Medium.ttf", asset("/fonts/Inter-Medium.ttf", "font/ttf"),
                    ORIGIN + "/fonts/Montserrat-Bold.ttf", asset("/fonts/Montserrat-Bold.ttf", "font/ttf"));
            if (closed) return;
            CefClient created = PokerChromium.createClient(null);
            synchronized (this) {
                if (closed) {
                    created.dispose();
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
                browser.createImmediately();
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
                if (closed) {
                    created.setCloseAllowed();
                    created.close(true);
                } else if (created instanceof LibGdxCefBrowser rendered) {
                    rendered.requestRepaint();
                }
            }

            @Override
            public void onBeforeClose(CefBrowser closing) {
                if (!closed) failed = true;
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
            String response;
            if ("init".equals(request)) {
                Scope scope = selection;
                List<String> players = List.of();
                if (scope.handId == 0) {
                    if (snapshot == null) snapshot = repository.dashboard();
                    players = snapshot.players(scope.gameId);
                }
                response = json.toJson(new Setup(scope.gameId, scope.handId, players,
                        Math.max(0, players.indexOf(preferredPlayer))));
            } else {
                String[] parts = request.split("\\t", -1);
                if (parts.length == 3 && "hand".equals(parts[0])) {
                    Scope requested = new Scope(Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2]));
                    if (!selection.equals(requested) || requested.gameId <= 0 || requested.handId <= 0) {
                        throw new IllegalArgumentException("Unknown hand selection");
                    }
                    StatsRepository.HandDetail detail = repository.handDetail(
                            requested.gameId, requested.handId);
                    if (!closed) reply.success(detail == null ? "null" : json.toJson(detail));
                    return;
                }
                if (snapshot == null || parts.length != 5) {
                    reply.failure(400, "Unknown statistics request");
                    return;
                }
                int scope = Integer.parseInt(parts[1]);
                LocalDate from = parseDate(parts[3]);
                LocalDate through = parseDate(parts[4]);
                if (scope < 0 || from != null && through != null && through.isBefore(from)) {
                    reply.failure(400, "Invalid statistics filter");
                    return;
                }
                response = switch (parts[0]) {
                    case "classic" -> json.toJson(repository.classic(
                            StatsRepository.ClassicMode.valueOf(parts[2]),
                            scope == 0 ? null : scope, from, through));
                    case "view" -> {
                        int player = Integer.parseInt(parts[2]);
                        List<String> players = snapshot.players(scope);
                        if (player < 0 || player >= players.size()) {
                            throw new IllegalArgumentException("Unknown player");
                        }
                        yield json.toJson(snapshot.view(players.get(player), scope, from, through));
                    }
                    default -> throw new IllegalArgumentException("Unknown statistics request");
                };
            }
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

    void setScope(int gameId, int handId) {
        if (selection.gameId == gameId && selection.handId == handId) return;
        selection = new Scope(gameId, handId);
        LibGdxCefBrowser current = browser;
        if (ready && current != null) {
            current.executeJavaScript("window.coronaStatsScope?.()", pageUrl, 0);
        }
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
        pressed = false;
        LibGdxCefBrowser current = browser;
        if (current != null) current.setFocus(false);
    }

    void mouse(int event, float x, float y) {
        LibGdxCefBrowser current = browser;
        if (!ready || current == null) return;
        if (event == MouseEvent.MOUSE_PRESSED) {
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
        if (event == KeyEvent.KEY_TYPED) {
            if (!Character.isISOControl(character)) {
                current.dispatchKey(event, KeyEvent.VK_UNDEFINED, character, modifiers());
            }
            return;
        }
        int awt = awtKey(key);
        if (awt != KeyEvent.VK_UNDEFINED) {
            current.dispatchKey(event, awt, KeyEvent.CHAR_UNDEFINED, modifiers());
        }
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
            case Input.Keys.TAB -> KeyEvent.VK_TAB;
            case Input.Keys.ENTER, Input.Keys.NUMPAD_ENTER -> KeyEvent.VK_ENTER;
            case Input.Keys.BACKSPACE -> KeyEvent.VK_BACK_SPACE;
            case Input.Keys.FORWARD_DEL -> KeyEvent.VK_DELETE;
            case Input.Keys.LEFT -> KeyEvent.VK_LEFT;
            case Input.Keys.RIGHT -> KeyEvent.VK_RIGHT;
            case Input.Keys.UP -> KeyEvent.VK_UP;
            case Input.Keys.DOWN -> KeyEvent.VK_DOWN;
            case Input.Keys.HOME -> KeyEvent.VK_HOME;
            case Input.Keys.END -> KeyEvent.VK_END;
            case Input.Keys.PAGE_UP -> KeyEvent.VK_PAGE_UP;
            case Input.Keys.PAGE_DOWN -> KeyEvent.VK_PAGE_DOWN;
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
        worker.shutdownNow();
        frames.setAcceptingFrames(false);
        LibGdxCefBrowser current = browser;
        browser = null;
        if (current != null) {
            current.setWindowVisibility(false);
            current.setCloseAllowed();
        }
        try {
            if (client != null) client.dispose();
        } finally {
            frames.dispose();
        }
    }

    private record Scope(int gameId, int handId) { }

    private record Setup(int scope, int handId, List<String> players, int selected) { }

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
