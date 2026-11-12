/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Texture;
import java.awt.EventQueue;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Map;
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

/** Runs the bundled PDF.js viewer in an isolated Chromium context. */
final class GdxRobertRulesBrowser implements AutoCloseable {

    private static final int IDLE_FRAME_RATE = 30;
    private static final int SCROLL_FRAME_RATE = 60;
    private static final int MAX_COPY_LENGTH = 262_144;
    private static final long SCROLL_BOOST = TimeUnit.MILLISECONDS.toNanos(400);
    private static final long RESIZE_PAINT_RETRY = TimeUnit.MILLISECONDS.toNanos(150);
    private static final Logger LOG = Logger.getLogger(GdxRobertRulesBrowser.class.getName());
    private final long started = System.nanoTime();
    private final PokerVideoFrameBuffer frames = new PokerVideoFrameBuffer();
    private final GdxBrowserKeyboard keyboard = new GdxBrowserKeyboard();
    private volatile LibGdxCefBrowser browser;
    private volatile boolean failed;
    private volatile boolean closed;
    private volatile boolean selectionMenuOpen;
    private CefClient client;
    private boolean creating;
    private boolean closing;
    private boolean pressed;
    private boolean secondaryPressed;
    private double queuedWheel;
    private int wheelX;
    private int wheelY;
    private int wheelModifiers;
    private long scrollingUntil;
    private long nextResizeRepaint;
    private boolean scrolling;

    GdxRobertRulesBrowser(int width, int height, double scale, String language) {
        Thread worker = new Thread(() -> open(width, height, scale, language), "CoronaPoker-rules-browser");
        worker.setDaemon(true);
        worker.start();
    }

    private void open(int width, int height, double scale, String language) {
        try {
            Map<String, byte[]> assets = GdxRobertRulesAssets.load();
            if (closed) return;
            CefClient created = PokerChromium.createClient(null);
            synchronized (this) {
                if (closed) {
                    EventQueue.invokeLater(() -> PokerChromium.disposeClient(created));
                    return;
                }
                client = created;
                installHandlers(created, assets);
                var settings = new CefBrowserSettings();
                settings.windowless_frame_rate = IDLE_FRAME_RATE;
                var context = CefRequestContext.createContext(null);
                if (context == null) throw new IllegalStateException("No isolated Chromium context");
                try {
                    browser = new LibGdxCefBrowser(created, GdxRobertRulesAssets.initialUrl(language),
                            context, settings, frames, width, height);
                } catch (RuntimeException | LinkageError failure) {
                    context.dispose();
                    throw failure;
                }
                frames.setAcceptingFrames(true);
                creating = true;
                try {
                    browser.resizeBrowser(width, height, scale);
                    browser.createImmediately();
                } catch (RuntimeException | LinkageError failure) {
                    creating = false;
                    browser.onBeforeClose();
                    browser = null;
                    throw failure;
                }
            }
        } catch (Exception | LinkageError failure) {
            if (!closed) LOG.log(Level.WARNING, "Could not open Robert's Rules", failure);
            failed = true;
            EventQueue.invokeLater(this::closeBrowserOrClient);
        }
    }

    private void installHandlers(CefClient target, Map<String, byte[]> assets) {
        installClipboard(target);
        var offline = new CefResourceRequestHandlerAdapter() {
            @Override
            public CefResourceHandler getResourceHandler(CefBrowser source, CefFrame frame,
                    CefRequest request) {
                String path = GdxRobertRulesAssets.localPath(request.getURL());
                byte[] bytes = assets.get(path);
                return "GET".equals(request.getMethod()) && bytes != null
                        ? new ViewerResource(path, bytes) : null;
            }
        };
        target.addRequestHandler(new CefRequestHandlerAdapter() {
            @Override
            public boolean onBeforeBrowse(CefBrowser source, CefFrame frame,
                    CefRequest request, boolean gesture, boolean redirect) {
                String url = request.getURL();
                return !GdxRobertRulesAssets.VIEWER.equals(GdxRobertRulesAssets.localPath(url))
                        && !browserResource(url);
            }

            @Override
            public CefResourceRequestHandler getResourceRequestHandler(CefBrowser source,
                    CefFrame frame, CefRequest request, boolean navigation, boolean download,
                    String initiator, BoolRef disableDefaultHandling) {
                if (browserResource(request.getURL())) return null;
                disableDefaultHandling.set(true);
                return offline;
            }

            @Override
            public void onRenderProcessTerminated(CefBrowser source,
                    TerminationStatus status, int errorCode, String errorString) {
                failed = true;
            }
        });
        target.addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadError(CefBrowser source, CefFrame frame, ErrorCode code,
                    String detail, String url) {
                if (!closed && frame != null && frame.isMain()
                        && GdxRobertRulesAssets.VIEWER.equals(GdxRobertRulesAssets.localPath(url))
                        && code != ErrorCode.ERR_ABORTED) {
                    failed = true;
                    LOG.log(Level.WARNING, "Robert's Rules PDF load failed: {0}", code);
                }
            }
        });
        target.addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
            @Override
            public boolean onBeforePopup(CefBrowser source, CefFrame frame,
                    String url, String targetFrame) {
                return true;
            }

            @Override
            public void onAfterCreated(CefBrowser source) {
                synchronized (GdxRobertRulesBrowser.this) {
                    creating = false;
                    if (closed || failed) {
                        EventQueue.invokeLater(GdxRobertRulesBrowser.this::closeBrowserOrClient);
                    }
                }
                if (source instanceof LibGdxCefBrowser rendered) rendered.requestRepaint();
            }

            @Override
            public void onBeforeClose(CefBrowser source) {
                synchronized (GdxRobertRulesBrowser.this) {
                    creating = false;
                    closing = false;
                    browser = null;
                    failed = true;
                }
                EventQueue.invokeLater(GdxRobertRulesBrowser.this::closeBrowserOrClient);
            }
        });
        target.addFocusHandler(new CefFocusHandlerAdapter() {
            @Override
            public boolean onSetFocus(CefBrowser source, FocusSource focusSource) {
                return focusSource == FocusSource.FOCUS_SOURCE_NAVIGATION;
            }
        });
    }

    private void installClipboard(CefClient target) {
        target.addContextMenuHandler(new CefContextMenuHandlerAdapter() {
            @Override
            public void onBeforeContextMenu(CefBrowser source, CefFrame frame,
                    CefContextMenuParams params, CefMenuModel menu) {
                menu.clear();
            }
        });
        var router = CefMessageRouter.create();
        router.addHandler(new CefMessageRouterHandlerAdapter() {
            @Override
            public boolean onQuery(CefBrowser source, CefFrame frame, long id,
                    String request, boolean persistent, CefQueryCallback reply) {
                if (closed || source != browser || frame == null || !frame.isMain() || persistent
                        || request == null || !GdxRobertRulesAssets.VIEWER.equals(
                                GdxRobertRulesAssets.localPath(frame.getURL()))) return false;
                switch (request) {
                    case "menu:open", "menu:close" -> {
                        selectionMenuOpen = request.equals("menu:open");
                        reply.success("");
                    }
                    default -> {
                        if (!request.startsWith("copy:") || request.length() > MAX_COPY_LENGTH + 5) return false;
                        String text = request.substring(5);
                        if (text.isBlank()) return false;
                        Gdx.app.postRunnable(() -> {
                            try {
                                Gdx.app.getClipboard().setContents(text);
                            } catch (RuntimeException failure) {
                                if (!closed) reply.failure(500, "Clipboard unavailable");
                                return;
                            }
                            if (!closed) reply.success("");
                        });
                    }
                }
                return true;
            }
        }, true);
        target.addMessageRouter(router);
    }

    boolean dismissSelectionMenu() {
        if (!selectionMenuOpen) return false;
        selectionMenuOpen = false;
        LibGdxCefBrowser current = browser;
        if (current != null) {
            current.executeJavaScript("window.dispatchEvent(new Event('rules-dismiss-selection-menu'))",
                    current.getURL(), 0);
        }
        return true;
    }

    private static boolean browserResource(String url) {
        return url != null && (url.equals("about:blank")
                || url.startsWith("blob:" + GdxRobertRulesAssets.ORIGIN + "/")
                || url.startsWith("data:"));
    }

    boolean failed() {
        return failed || frames.uploadedFrames() == 0
                && System.nanoTime() - started > TimeUnit.SECONDS.toNanos(35);
    }

    Texture texture(int width, int height, double scale) {
        LibGdxCefBrowser current = browser;
        long now = System.nanoTime();
        if (current != null) {
            current.resizeBrowser(width, height, scale);
            boolean boost = pressed || now < scrollingUntil;
            if (boost != scrolling) {
                current.setWindowlessFrameRate(boost ? SCROLL_FRAME_RATE : IDLE_FRAME_RATE);
                scrolling = boost;
            }
            dispatchWheel(current);
        }
        int rasterWidth = (int) Math.round(width * scale);
        int rasterHeight = (int) Math.round(height * scale);
        Texture texture = frames.uploadPendingFrame(rasterWidth, rasterHeight);
        if (current != null && now >= nextResizeRepaint
                && (texture == null || Math.abs(texture.getWidth() - rasterWidth) > 1
                        || Math.abs(texture.getHeight() - rasterHeight) > 1)) {
            // Several resize events can arrive before Chromium paints the final size.
            current.requestRepaint();
            nextResizeRepaint = now + RESIZE_PAINT_RETRY;
        }
        return texture;
    }

    void mouse(int event, int x, int y) {
        int button = event == MouseEvent.MOUSE_PRESSED || event == MouseEvent.MOUSE_RELEASED
                ? MouseEvent.BUTTON1 : MouseEvent.NOBUTTON;
        mouse(event, x, y, button);
    }

    void mouse(int event, int x, int y, int button) {
        LibGdxCefBrowser current = browser;
        if (current == null) return;
        if (event == MouseEvent.MOUSE_PRESSED || event == MouseEvent.MOUSE_RELEASED) {
            boolean down = event == MouseEvent.MOUSE_PRESSED;
            if (button == MouseEvent.BUTTON1) pressed = down;
            if (button == MouseEvent.BUTTON3) secondaryPressed = down;
            if (down) current.setFocus(true);
        }
        current.dispatchMouse(event, x, y, button, modifiers(),
                button == MouseEvent.NOBUTTON ? 0 : 1);
    }

    void wheel(int x, int y, float amount) {
        if (!Float.isFinite(amount) || amount == 0f) return;
        wheelX = x;
        wheelY = y;
        wheelModifiers = (keyPressed(Input.Keys.ALT_LEFT, Input.Keys.ALT_RIGHT) ? 1 : 0)
                | (keyPressed(Input.Keys.CONTROL_LEFT, Input.Keys.CONTROL_RIGHT) ? 2 : 0)
                | (keyPressed(Input.Keys.SHIFT_LEFT, Input.Keys.SHIFT_RIGHT) ? 8 : 0);
        queuedWheel += amount * 96d;
        scrollingUntil = System.nanoTime() + SCROLL_BOOST;
    }

    private void dispatchWheel(LibGdxCefBrowser current) {
        int distance = (int) queuedWheel;
        if (distance == 0 || frames.uploadedFrames() == 0) return;
        var devTools = current.getDevToolsClient();
        if (devTools == null) return;
        queuedWheel -= distance;
        // The PDF can appear under a stationary pointer after opening or resizing.
        current.dispatchMouse(MouseEvent.MOUSE_MOVED, wheelX, wheelY,
                MouseEvent.NOBUTTON, modifiers(), 0);
        devTools.executeDevToolsMethod("Input.dispatchMouseEvent",
                "{\"type\":\"mouseWheel\",\"x\":" + wheelX + ",\"y\":" + wheelY
                + ",\"deltaX\":0,\"deltaY\":" + distance + ",\"modifiers\":" + wheelModifiers + '}');
    }

    void key(int event, int key, char character) {
        LibGdxCefBrowser current = browser;
        if (current == null) return;
        if (event == KeyEvent.KEY_PRESSED && key == Input.Keys.C
                && keyPressed(Input.Keys.CONTROL_LEFT, Input.Keys.CONTROL_RIGHT)
                && !keyPressed(Input.Keys.ALT_LEFT, Input.Keys.ALT_RIGHT)) {
            current.executeJavaScript("window.dispatchEvent(new Event('rules-copy-selection'))",
                    current.getURL(), 0);
            return;
        }
        keyboard.key(current, event, key, character, modifiers());
    }

    void updateKeyboard(float delta) {
        keyboard.update(delta, browser, this::modifiers);
    }

    void unfocus() {
        dismissSelectionMenu();
        keyboard.clear();
        pressed = false;
        secondaryPressed = false;
        LibGdxCefBrowser current = browser;
        if (current != null) current.setFocus(false);
    }

    private int modifiers() {
        return LibGdxCefBrowser.awtModifiers(
                keyPressed(Input.Keys.SHIFT_LEFT, Input.Keys.SHIFT_RIGHT),
                keyPressed(Input.Keys.CONTROL_LEFT, Input.Keys.CONTROL_RIGHT),
                keyPressed(Input.Keys.ALT_LEFT, Input.Keys.ALT_RIGHT), false,
                pressed, false, secondaryPressed);
    }

    private static boolean keyPressed(int left, int right) {
        return Gdx.input.isKeyPressed(left) || Gdx.input.isKeyPressed(right);
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        keyboard.clear();
        frames.setAcceptingFrames(false);
        frames.close();
        EventQueue.invokeLater(this::closeBrowserOrClient);
    }

    private void closeBrowserOrClient() {
        LibGdxCefBrowser current;
        CefClient disposing = null;
        synchronized (this) {
            if ((!closed && !failed) || client == null || creating || closing) return;
            current = browser;
            if (current == null) {
                disposing = client;
                client = null;
            } else {
                closing = true;
            }
        }
        if (current == null) PokerChromium.disposeClient(disposing);
        else {
            current.setWindowVisibility(false);
            current.setCloseAllowed();
            current.close(true);
        }
    }

    private static final class ViewerResource extends CefResourceHandlerAdapter {
        private final String path;
        private final byte[] bytes;
        private int offset;

        ViewerResource(String path, byte[] bytes) {
            this.path = path;
            this.bytes = bytes;
        }

        @Override
        public boolean open(CefRequest request, BoolRef handled, CefCallback callback) {
            handled.set(true);
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
            response.setMimeType(GdxRobertRulesAssets.mimeType(path));
            response.setHeaderMap(Map.of("X-Content-Type-Options", "nosniff"));
            length.set(bytes.length);
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
            int length = Math.min(requested, bytes.length - offset);
            count.set(length);
            if (length <= 0) return false;
            System.arraycopy(bytes, offset, output, 0, length);
            offset += length;
            return true;
        }
    }
}
