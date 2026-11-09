/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.cef.CefApp.CefAppState;
import org.cef.CefBrowserSettings;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.browser.LibGdxCefBrowser;
import org.cef.callback.CefContextMenuParams;
import org.cef.callback.CefMenuModel;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefContextMenuHandlerAdapter;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefFocusHandlerAdapter;
import org.cef.handler.CefLifeSpanHandlerAdapter;
import org.cef.handler.CefLoadHandler.ErrorCode;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.handler.CefRequestHandler;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.handler.CefResourceRequestHandlerAdapter;
import org.cef.misc.BoolRef;
import org.cef.network.CefRequest;

/** Owns the isolated windowless browser used by the frontend video overlay. */
final class PokerVideoRuntime implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(PokerVideoRuntime.class.getName());
    // Read-only observer of the native control: Shorts may recreate it after
    // loadVideoById without reflecting an already-muted media element. Signal
    // that concrete mismatch, then refresh through the public outer Player API.
    // No control clicks, DOM changes, reloads or repeated audio toggles.
    private static final String MUTED_CONTROL_OBSERVER = """
        (() => {
          if (window.coronaMutedControlObserver) return;
          window.coronaMutedControlObserver = true;
          let scheduled = false;
          let inFlight = false;
          function check() {
            scheduled = false;
            const video = document.querySelector('video');
            if (!video || !video.muted || inFlight) return;
            const stale = [...document.querySelectorAll('button')].some(button => {
              if (!button.getClientRects().length) return false;
              const label = button.getAttribute('aria-label') || button.title || '';
              return /^(Silenciar|Mute)(?:\\b|\\s|$)/i.test(label);
            });
            if (!stale || typeof window.cefQuery !== 'function') return;
            inFlight = true;
            window.cefQuery({ request: 'SYNC_MUTED_CONTROL',
              onSuccess: () => { inFlight = false; },
              onFailure: () => { inFlight = false; } });
          }
          function schedule() {
            if (scheduled) return;
            scheduled = true;
            requestAnimationFrame(check);
          }
          new MutationObserver(schedule).observe(document.documentElement, {
            childList: true, subtree: true, attributes: true,
            attributeFilter: ['aria-label', 'title'] });
          document.addEventListener('volumechange', schedule, true);
          schedule();
        })();
        """;
    private volatile List<PokerVideoSearch.Video> videos;
    private final PokerVideoFrameBuffer frameBuffer;
    private volatile String playerLanguage;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile PokerVideoPage localPage;
    private final java.util.function.Consumer<CefAppState> cefStateListener = this::onCefStateChanged;
    private CefClient client;
    private CefMessageRouter messageRouter;
    private volatile LibGdxCefBrowser browser;
    private volatile String state = "INICIALIZANDO REPRODUCTOR";
    private volatile String loadingKey = "gdx.video.loading.player";
    private volatile String initializationError;
    private volatile boolean browserCreated;
    private volatile boolean browserClosing;
    private volatile boolean playerReady;
    private boolean coldPlayerBootstrapPending = true;
    private volatile boolean active = true;
    private volatile int currentIndex;
    private volatile Selection selection;
    private volatile int resumeSeconds;
    private volatile boolean resumeMuted = true;
    private volatile int resumeVolume = 100;
    private volatile boolean restoreAudioOnNextSession;
    private int apiRecoveryAttempts;
    private final PokerVideoDocumentLoad documentLoad = new PokerVideoDocumentLoad();
    private final AtomicReference<CefBrowser> stalledPlayback = new AtomicReference<>();
    private volatile int sessionNumber;
    private long sessionStartNanos;
    private long copiedAtOpen;
    private long uploadedAtOpen;
    private boolean loggedFirstUpload;
    private volatile long playlistRevision;
    private volatile long browserPlaylistRevision;
    private long discoveryRevisionFloor;
    private volatile long discoveryRequestRevision = -1L;
    private volatile boolean orderedPlaylist;
    private volatile String pageUrl;
    private volatile String refererOrigin;

    private PokerVideoRuntime(List<PokerVideoSearch.Video> videos,
            PokerVideoFrameBuffer frameBuffer, String language) {
        this.videos = List.copyOf(videos);
        setCurrentIndex(0);
        this.frameBuffer = frameBuffer;
        playerLanguage = PokerVideoSearch.languageCode(language);
    }

    static PokerVideoRuntime initialize(List<PokerVideoSearch.Video> videos,
            PokerVideoFrameBuffer frameBuffer,
            String language, boolean orderedPlaylist, int width, int height, double scaleFactor) {
        PokerVideoRuntime runtime = new PokerVideoRuntime(videos, frameBuffer,
                language);
        runtime.orderedPlaylist = orderedPlaylist;
        if (!PokerVideoPlatformSupport.isAvailable()) {
            runtime.initializationError = "JCEF no está disponible para "
                    + "esta plataforma o este artefacto";
            runtime.state = "REPRODUCTOR NO DISPONIBLE";
            return runtime;
        }
        try {
            runtime.initializeInternal(width, height,
                    scaleFactor);
        } catch (Throwable failure) {
            Logger.getLogger(PokerVideoRuntime.class.getName()).log(Level.WARNING,
                    "Poker video initialization failed", failure);
            runtime.initializationError = failure.getClass().getSimpleName()
                    + ": " + (failure.getMessage() == null
                            ? "sin detalle" : failure.getMessage());
            runtime.state = "REPRODUCTOR NO DISPONIBLE";
        }
        return runtime;
    }

    private void initializeInternal(int width, int height,
            double scaleFactor) throws Exception {
        String osName = System.getProperty("os.name", "");
        if (!PokerVideoPlatformSupport.hasBrowserDisplay(osName,
                System.getenv("DISPLAY"))) {
            initializationFailed("en".equals(playerLanguage)
                    ? "Chromium requires an accessible X11 or XWayland display."
                    : "Chromium necesita una pantalla X11 o XWayland accesible.");
            return;
        }
        client = PokerChromium.createClient(cefStateListener);
        installHandlers(osName);
        openBrowserSession(width, height, scaleFactor);
    }

    void onCefStateChanged(CefAppState appState) {
        if (closed.get()) return;
        LOG.log(Level.INFO, "Poker video CEF state: {0}", appState);
        if (appState == CefAppState.INITIALIZATION_FAILED
                || appState == CefAppState.TERMINATED) {
            initializationFailed("en".equals(playerLanguage)
                    ? "Chromium could not start or stopped unexpectedly."
                    : "Chromium no pudo arrancar o se cerró inesperadamente.");
        }
    }

    private void initializationFailed(String message) {
        initializationError = message;
        state = "REPRODUCTOR NO DISPONIBLE";
    }

    private synchronized void openBrowserSession(int width, int height,
            double scaleFactor) throws Exception {
        if (browser != null || browserClosing || closed.get()
                || initializationError != null) return;
        boolean restoreAudio = restoreAudioOnNextSession;
        boolean initialMuted = restoreAudio ? resumeMuted : true;
        int initialVolume = restoreAudio ? resumeVolume : 100;
        PokerVideoPage page = PokerVideoPage.create(videos, currentIndex,
                resumeSeconds, initialMuted, initialVolume, playerLanguage,
                orderedPlaylist, playlistRevision);
        localPage = page;
        restoreAudioOnNextSession = false;
        browserPlaylistRevision = playlistRevision;
        apiRecoveryAttempts = 0;
        documentLoad.reset();
        loadingKey = "gdx.video.loading.player";
        pageUrl = page.playerUrl();
        refererOrigin = page.origin();
        CefBrowserSettings browserSettings = new CefBrowserSettings();
        browserSettings.windowless_frame_rate = 30;
        sessionNumber++;
        sessionStartNanos = System.nanoTime();
        copiedAtOpen = frameBuffer.copiedFrames();
        uploadedAtOpen = frameBuffer.uploadedFrames();
        loggedFirstUpload = false;
        logStage("open " + width + "x" + height + " scale=" + scaleFactor
                + " index=" + currentIndex);
        browser = new LibGdxCefBrowser(client, pageUrl, null,
                browserSettings, frameBuffer, width, height);
        active = true;
        frameBuffer.setAcceptingFrames(true);
        browser.createImmediately();
        browser.resizeBrowser(width, height, scaleFactor);
    }

    private void installHandlers(String osName) {
        String platform = osName.toLowerCase(Locale.ROOT);
        if (platform.startsWith("windows") || platform.startsWith("linux")) {
            // Navigation focus in the windowless browser can re-enter JCEF's
            // focus callbacks on the AWT thread and exhaust its stack.
            // Explicit focus requested by player input is still allowed.
            client.addFocusHandler(new CefFocusHandlerAdapter() {
                @Override
                public boolean onSetFocus(CefBrowser source, FocusSource focusSource) {
                    return focusSource == FocusSource.FOCUS_SOURCE_NAVIGATION;
                }
            });
        }
        client.addDisplayHandler(new CefDisplayHandlerAdapter() {
            private long windowStarted = System.nanoTime();
            private int lines;

            @Override
            public boolean onConsoleMessage(CefBrowser source, CefSettings.LogSeverity severity,
                    String message, String origin, int line) {
                if (source != browser || closed.get()) return false;
                Level level = switch (severity) {
                    case LOGSEVERITY_WARNING -> Level.WARNING;
                    case LOGSEVERITY_ERROR, LOGSEVERITY_FATAL -> Level.SEVERE;
                    default -> null;
                };
                if (level == null) return false;
                long now = System.nanoTime();
                if (now - windowStarted >= TimeUnit.SECONDS.toNanos(10)) {
                    windowStarted = now;
                    lines = 0;
                }
                if (lines++ < 12) {
                    LOG.log(level, "Chromium console | session {0} | line {1} | {2}",
                            new Object[]{sessionNumber, line, PokerVideoChromiumLog.clean(message)});
                }
                return false;
            }
        });
        client.addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
            @Override
            public boolean onBeforePopup(CefBrowser source, CefFrame frame,
                    String targetUrl, String targetFrameName) {
                state = "ENLACE EXTERNO BLOQUEADO";
                return true;
            }

            @Override
            public void onAfterCreated(CefBrowser created) {
                if (created != browser) return;
                logStage("browser created");
                browserCreated = true;
                created.setWindowlessFrameRate(30);
                created.setWindowVisibility(active);
                if (active && created instanceof LibGdxCefBrowser rendered) rendered.requestRepaint();
            }

            @Override
            public void onBeforeClose(CefBrowser closing) {
                logStage("browser closed");
                browserClosing = false;
                if (closing == browser) {
                    browserCreated = false;
                    playerReady = false;
                    browser = null;
                    if (!closed.get()) documentLoad.failed(
                            "El navegador del vídeo se cerró. Pulsa Reintentar.");
                }
            }
        });
        client.addContextMenuHandler(new CefContextMenuHandlerAdapter() {
            @Override
            public void onBeforeContextMenu(CefBrowser source, CefFrame frame,
                    CefContextMenuParams params, CefMenuModel model) {
                model.clear();
            }
        });
        client.addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadStart(CefBrowser loaded, CefFrame frame,
                    org.cef.network.CefRequest.TransitionType transition) {
                if (loaded == browser && frame != null && frame.isMain()) {
                    playerReady = false;
                    loadingKey = "gdx.video.loading.player";
                    documentLoad.loading();
                    logStage("document load started");
                }
            }

            @Override
            public void onLoadEnd(CefBrowser loaded, CefFrame frame,
                    int statusCode) {
                if (loaded == browser && frame != null && !frame.isMain()
                        && isYouTubeHost(frame.getURL())
                        && frame.getURL().contains("/embed/")) {
                    frame.executeJavaScript(MUTED_CONTROL_OBSERVER,
                            frame.getURL(), 0);
                }
                if (loaded == browser && frame != null && frame.isMain()) {
                    logStage("document HTTP " + statusCode);
                    loaded.executeJavaScript("window.coronaSetActive && "
                            + "window.coronaSetActive(" + active + ");",
                            pageUrl, 0);
                }
                if (loaded == browser && frame != null && frame.isMain()
                        && statusCode >= 400) {
                    state = "ERROR HTTP " + statusCode;
                    documentLoad.failed(state);
                }
            }

            @Override
            public void onLoadError(CefBrowser failed, CefFrame frame,
                    ErrorCode errorCode, String errorText, String failedUrl) {
                if (failed == browser && frame != null && frame.isMain()
                        && errorCode != ErrorCode.ERR_ABORTED) {
                    state = "ERROR DE CARGA: " + errorCode;
                    documentLoad.failed(state);
                    Logger.getLogger(PokerVideoRuntime.class.getName()).log(
                            Level.WARNING, "Poker video main document failed: {0}", errorCode);
                }
            }
        });

        CefResourceRequestHandler refererHandler =
                new CefResourceRequestHandlerAdapter() {
                    @Override
                    public boolean onBeforeResourceLoad(CefBrowser source,
                            CefFrame frame, CefRequest request) {
                        String referer = request.getHeaderByName("Referer");
                        if (referer == null || referer.isBlank()) {
                            request.setHeaderByName("Referer",
                                    refererOrigin + "/", true);
                        }
                        return false;
                    }
                };
        client.addRequestHandler(new CefRequestHandlerAdapter() {
            @Override
            public void onRenderProcessTerminated(CefBrowser source,
                    CefRequestHandler.TerminationStatus status, int errorCode, String errorString) {
                if (source != browser || closed.get()) return;
                playerReady = false;
                documentLoad.failed("El proceso del vídeo se interrumpió. Pulsa Reintentar.");
                Logger.getLogger(PokerVideoRuntime.class.getName()).log(Level.WARNING,
                        "Poker video session {0} renderer terminated: {1} ({2}) {3}",
                        new Object[]{sessionNumber, status, errorCode, PokerVideoChromiumLog.clean(errorString)});
            }

            @Override
            public boolean onBeforeBrowse(CefBrowser source, CefFrame frame,
                    CefRequest request, boolean userGesture,
                    boolean isRedirect) {
                if (frame != null && frame.isMain()
                        && !allowedMainNavigation(request.getURL(), pageUrl)) {
                    state = "NAVEGACIÓN EXTERNA BLOQUEADA";
                    return true;
                }
                if (frame != null && !frame.isMain() && userGesture) {
                    state = "NAVEGACIÓN EXTERNA BLOQUEADA";
                    return true;
                }
                return false;
            }

            @Override
            public boolean onOpenURLFromTab(CefBrowser source, CefFrame frame,
                    String targetUrl, boolean userGesture) {
                state = "ENLACE EXTERNO BLOQUEADO";
                return true;
            }

            @Override
            public CefResourceRequestHandler getResourceRequestHandler(
                    CefBrowser source, CefFrame frame, CefRequest request,
                    boolean isNavigation, boolean isDownload,
                    String requestInitiator, BoolRef disableDefaultHandling) {
                PokerVideoPage page = localPage;
                if (source == browser && page != null && page.owns(request.getURL())) {
                    disableDefaultHandling.set(true);
                    return page;
                }
                return isYouTubeHost(request.getURL()) ? refererHandler : null;
            }
        });

        messageRouter = CefMessageRouter.create();
        messageRouter.addHandler(new CefMessageRouterHandlerAdapter() {
            @Override
            public boolean onQuery(CefBrowser source, CefFrame frame,
                    long queryId, String request, boolean persistent,
                    CefQueryCallback callback) {
                // A closing session may still deliver queued audio/position
                // messages. They must never overwrite a new muted opening.
                if (source != browser || frame == null) return false;
                if (!frame.isMain()
                        && "SYNC_MUTED_CONTROL".equals(request)
                        && isYouTubeHost(frame.getURL())
                        && frame.getURL().contains("/embed/")) {
                    source.executeJavaScript("window.coronaRefreshMutedControl"
                            + " && window.coronaRefreshMutedControl();",
                            pageUrl, 0);
                    callback.success("ok");
                    return true;
                }
                if (!frame.isMain() || !pageUrl.equals(frame.getURL())) {
                    return false;
                }
                if (request.startsWith("RETRY_API_MANUAL|")) {
                    apiRecoveryAttempts = 0;
                    playerReady = false;
                    callback.success("reload");
                    return true;
                }
                if (request.startsWith("RETRY_API|")) {
                    boolean retry = !playerReady && apiRecoveryAttempts++ < 1;
                    Logger.getLogger(PokerVideoRuntime.class.getName()).log(Level.WARNING,
                            "Poker video API recovery: {0}", retry ? "retry" : "exhausted");
                    callback.success(retry ? "reload" : "stop");
                    return true;
                }
                if (request.startsWith("READY|")
                        && coldPlayerBootstrapPending) {
                    // CEF's first Shorts document can initialize its native
                    // control model after the API model. Prime that document
                    // without playing, then reload exactly once before the
                    // game considers the player ready. Subsequent sessions
                    // use the already initialized renderer directly.
                    coldPlayerBootstrapPending = false;
                    logStage("YouTube ready, reloading cold document once");
                    callback.success("reload");
                    return true;
                }
                if (request.startsWith("PLAYBACK_STALLED|")) {
                    logStage("iframe playback stalled | "
                            + request.substring("PLAYBACK_STALLED|".length()) + " | state=" + state);
                    stalledPlayback.set(source);
                    callback.success("ok");
                    return true;
                }
                if (request.startsWith("PROGRESS|")) stalledPlayback.compareAndSet(source, null);
                long revision = playlistRevision;
                if (request.equals("DISCOVER|" + revision) && !orderedPlaylist) {
                    discoveryRequestRevision = revision;
                    callback.success("wait");
                    return true;
                }
                boolean handled = handlePlayerMessage(request);
                if (handled) callback.success("ok");
                return handled;
            }
        }, true);
        client.addMessageRouter(messageRouter);
    }

    private boolean handlePlayerMessage(String request) {
        if (request.startsWith("BOOT|")) {
            documentLoad.loaded();
            logStage("JS boot " + request.substring("BOOT|".length()));
            state = "CONECTANDO CON EL REPRODUCTOR";
            return true;
        }
        if (request.equals("HEARTBEAT|")) {
            documentLoad.pulse();
            return true;
        }
        if (request.startsWith("PLAYING|") || request.startsWith("PROGRESS|")) {
            logStage(request);
            return true;
        }
        if (request.startsWith("RECOVERY|")) {
            Logger.getLogger(PokerVideoRuntime.class.getName()).log(Level.WARNING,
                    "Poker video recovery: {0}", request.substring("RECOVERY|".length()));
            return true;
        }
        if (request.startsWith("READY|")) {
            try {
                browserPlaylistRevision = Long.parseLong(request.substring("READY|".length()));
            } catch (NumberFormatException ignored) {
                browserPlaylistRevision = -1L;
            }
            playerReady = true;
            logStage("YouTube ready");
            synchronizeBrowserPlaylist();
            state = "CARGANDO";
            return true;
        }
        if (request.startsWith("STATE|")) {
            state = request.substring("STATE|".length());
            return true;
        }
        if (request.startsWith("LOADING|")) {
            loadingKey = switch (request.substring("LOADING|".length())) {
                case "connecting" -> "gdx.video.loading.player";
                case "reconnecting" -> "gdx.video.loading.reconnecting";
                case "discovery" -> "gdx.video.loading.search";
                case "video" -> "gdx.video.loading.video";
                case "" -> null;
                default -> loadingKey;
            };
            return true;
        }
        if (request.startsWith("ERROR|")) {
            loadingKey = null;
            state = request.substring("ERROR|".length());
            Logger.getLogger(PokerVideoRuntime.class.getName()).log(Level.WARNING,
                    "Poker video player error: {0}", state);
            return true;
        }
        if (request.startsWith("INDEX|")) {
            updateSelection(request.substring("INDEX|".length()), false);
            return true;
        }
        if (request.startsWith("POSITION|")) {
            documentLoad.pulse();
            updateSelection(request.substring("POSITION|".length()), true);
            return true;
        }
        if (request.startsWith("AUDIO|")) {
            String[] parts = request.substring("AUDIO|".length())
                    .split(":", 2);
            try {
                resumeMuted = parts.length == 0
                        || !"0".equals(parts[0]);
                resumeVolume = parts.length > 1
                        ? Math.max(0, Math.min(100,
                                Integer.parseInt(parts[1])))
                        : 100;
            } catch (NumberFormatException ignored) {
            }
            return true;
        }
        if (request.startsWith("FAILED|")) {
            state = "VÍDEO NO REPRODUCIBLE; PASANDO AL SIGUIENTE";
            return true;
        }
        return false;
    }

    static boolean allowedMainNavigation(String value, String playerUrl) {
        try {
            URI uri = URI.create(value);
            URI page = URI.create(playerUrl);
            return "about:blank".equals(value)
                    || uri.getUserInfo() == null
                    && uri.getScheme().equalsIgnoreCase(page.getScheme())
                    && uri.getHost().equalsIgnoreCase(page.getHost())
                    && uri.getPort() == page.getPort()
                    && List.of("/", "/player", "/player/")
                            .contains(uri.getPath());
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private static boolean isYouTubeHost(String value) {
        try {
            String host = URI.create(value).getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            return host.equals("youtube.com") || host.endsWith(".youtube.com")
                    || host.equals("youtube-nocookie.com")
                    || host.endsWith(".youtube-nocookie.com");
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    void resize(int width, int height, double scaleFactor) {
        LibGdxCefBrowser current = browser;
        if (current != null && browserCreated) {
            current.resizeBrowser(width, height, scaleFactor);
        }
    }

    void setActive(boolean enabled) {
        if (active == enabled) return;
        active = enabled;
        frameBuffer.setAcceptingFrames(enabled);
        LibGdxCefBrowser current = browser;
        if (current == null || !browserCreated) return;
        current.setWindowVisibility(enabled);
        current.executeJavaScript("window.coronaSetActive && "
                + "window.coronaSetActive(" + enabled + ");", pageUrl, 0);
        if (enabled) current.requestRepaint();
    }

    void selectRelative(int delta) {
        if (!playerReady || videos.isEmpty() || browser == null) return;
        int direction = delta < 0 ? -1 : 1;
        logStage("select direction=" + direction);
        browser.executeJavaScript("window.coronaSelectRelative && "
                + "window.coronaSelectRelative(" + direction + ");",
                pageUrl, 0);
    }

    private synchronized void updateSelection(String message, boolean hasPosition) {
        String[] parts = message.split(":", 3);
        if (parts.length != 3) return;
        try {
            int index = Integer.parseInt(parts[0]);
            long revision = Long.parseLong(parts[2]);
            if (revision != playlistRevision || index < 0 || index >= videos.size()) return;
            int seconds = hasPosition ? Math.max(0, Integer.parseInt(parts[1])) : 0;
            if (hasPosition || currentIndex != index) resumeSeconds = seconds;
            if (currentIndex != index) setCurrentIndex(index);
        } catch (NumberFormatException ignored) {
        }
    }

    synchronized void replaceVideos(List<PokerVideoSearch.Video> replacements,
            String language, boolean ordered) {
        List<PokerVideoSearch.Video> next = List.copyOf(replacements);
        if (next.isEmpty()) return;
        int nextIndex = orderedPlaylist && ordered ? Math.min(currentIndex, next.size() - 1) : 0;
        orderedPlaylist = ordered;
        videos = next;
        setCurrentIndex(nextIndex);
        resumeSeconds = 0;
        playlistRevision++;
        discoveryRevisionFloor = playlistRevision;
        discoveryRequestRevision = -1L;
        String nextLanguage = PokerVideoSearch.languageCode(language);
        if (!playerLanguage.equals(nextLanguage)) {
            playerLanguage = nextLanguage;
            releaseBrowserSession(true);
            return;
        }
        synchronizeBrowserPlaylist();
    }

    boolean discoveryRequested() {
        return discoveryRequestRevision == playlistRevision;
    }

    synchronized void extendDiscovery(List<PokerVideoSearch.Video> extended) {
        if (orderedPlaylist || closed.get()) return;
        int index = extended.indexOf(selection.video());
        if (index < 0) return;
        if (videos.equals(extended)) {
            if (discoveryRequested() && browser != null) {
                browser.executeJavaScript("window.coronaDiscoveryUnavailable("
                        + playlistRevision + ");", pageUrl, 0);
            }
        } else {
            videos = extended;
            setCurrentIndex(index);
            playlistRevision++;
            synchronizeBrowserPlaylist();
        }
        discoveryRequestRevision = -1L;
    }

    private synchronized void synchronizeBrowserPlaylist() {
        LibGdxCefBrowser current = browser;
        long revision = playlistRevision;
        if (!playerReady || current == null || !browserCreated
                || browserPlaylistRevision == revision) return;
        StringBuilder ids = new StringBuilder("[");
        List<PokerVideoSearch.Video> snapshot = videos;
        for (int index = 0; index < snapshot.size(); index++) {
            if (index > 0) ids.append(',');
            ids.append('\'').append(snapshot.get(index).id()).append('\'');
        }
        ids.append(']');
        boolean extending = !orderedPlaylist && browserPlaylistRevision >= discoveryRevisionFloor
                && browserPlaylistRevision < revision;
        String command = extending
                ? "window.coronaExtendVideos(" + ids + "," + revision + "," + currentIndex + ");"
                : "window.coronaReplaceVideos(" + ids + "," + orderedPlaylist + ","
                        + revision + "," + currentIndex + ");";
        current.executeJavaScript(command, pageUrl, 0);
        browserPlaylistRevision = revision;
    }

    void requestFocus(boolean focused) {
        if (browser != null && browserCreated) browser.setFocus(focused);
    }

    LibGdxCefBrowser browser() {
        return browser;
    }

    boolean ready() {
        return playerReady;
    }

    String state() {
        return state;
    }

    String loadingKey() {
        return loadingKey;
    }

    record Selection(PokerVideoSearch.Video video, int index, int total) { }

    Selection selection() {
        return selection;
    }

    private void setCurrentIndex(int index) {
        currentIndex = index;
        selection = new Selection(videos.get(index), index, videos.size());
    }

    String initializationError() {
        return initializationError;
    }

    synchronized boolean pollRecovery(boolean running) {
        if (!running || closed.get() || (browser == null && !browserClosing)) {
            documentLoad.poll(false, false, System.nanoTime());
            return false;
        }
        boolean hasFrame = frameBuffer.copiedFrames() > copiedAtOpen
                && frameBuffer.uploadedFrames() > uploadedAtOpen;
        if (!loggedFirstUpload && hasFrame) {
            loggedFirstUpload = true;
            logStage("first frame uploaded");
        }
        boolean documentTimedOut = documentLoad.poll(true, hasFrame || browserClosing, System.nanoTime());
        if (documentTimedOut) {
            LOG.log(Level.WARNING, "Poker video session {0} watchdog | {1} | created={2}, closing={3}, ready={4}, frames={5}/{6}",
                    new Object[]{sessionNumber, documentLoad.error(), browserCreated, browserClosing,
                        playerReady, frameBuffer.copiedFrames() - copiedAtOpen,
                        frameBuffer.uploadedFrames() - uploadedAtOpen});
        }
        CefBrowser stalled = stalledPlayback.getAndSet(null);
        return browser != null && (documentTimedOut || stalled == browser);
    }

    String documentLoadError() { return documentLoad.error(); }

    private void logStage(String stage) {
        LOG.log(Level.INFO, "Poker video session {0} +{1}ms | {2} | frames copied/uploaded={3}/{4}",
                new Object[]{sessionNumber, Long.toString((System.nanoTime() - sessionStartNanos) / 1_000_000),
                    stage, frameBuffer.copiedFrames() - copiedAtOpen, frameBuffer.uploadedFrames() - uploadedAtOpen});
    }

    synchronized void releaseBrowserSession(boolean preserveAudioForResume) {
        boolean hadSession = browser != null || documentLoad.error() != null;
        documentLoad.reset();
        stalledPlayback.set(null);
        frameBuffer.setAcceptingFrames(false);
        LibGdxCefBrowser current = browser;
        restoreAudioOnNextSession = preserveAudioForResume && hadSession;
        if (!restoreAudioOnNextSession) {
            resumeMuted = true;
            resumeVolume = 100;
        }
        browser = null;
        browserCreated = false;
        playerReady = false;
        active = false;
        if (current != null) {
            logStage("closing browser preserveAudio=" + preserveAudioForResume);
            browserClosing = true;
            try {
                current.executeJavaScript("window.coronaReportSession && "
                        + "window.coronaReportSession();", pageUrl, 0);
                current.setWindowVisibility(false);
                current.setCloseAllowed();
                current.close(true);
            } catch (RuntimeException ignored) {
            }
        }
        if (localPage != null) {
            localPage.close();
            localPage = null;
        }
        state = "REPRODUCTOR EN REPOSO";
    }

    synchronized void resumeBrowserSession(int width, int height,
            double scaleFactor) {
        if (browser != null || browserClosing || closed.get()
                || initializationError != null) {
            return;
        }
        try {
            state = "RESTAURANDO VÍDEO";
            openBrowserSession(width, height, scaleFactor);
        } catch (Exception failure) {
            initializationError = failure.getClass().getSimpleName() + ": "
                    + (failure.getMessage() == null
                            ? "sin detalle" : failure.getMessage());
            state = "REPRODUCTOR NO DISPONIBLE";
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        releaseBrowserSession(false);
        PokerChromium.removeListener(cefStateListener);
        if (client != null) client.dispose();
    }
}
