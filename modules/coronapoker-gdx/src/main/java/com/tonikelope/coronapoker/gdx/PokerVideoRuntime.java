/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import me.friwi.jcefmaven.CefAppBuilder;
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter;
import org.cef.CefApp;
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
import org.cef.handler.CefLifeSpanHandlerAdapter;
import org.cef.handler.CefLoadHandler.ErrorCode;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.handler.CefResourceRequestHandlerAdapter;
import org.cef.misc.BoolRef;
import org.cef.network.CefRequest;

/** Owns the isolated windowless browser used by the frontend video overlay. */
final class PokerVideoRuntime implements AutoCloseable {

    static final String JCEF_MAVEN_VERSION = "152.0.6";
    private volatile List<PokerVideoSearch.Video> videos;
    private final PokerVideoFrameBuffer frameBuffer;
    private final String playerLanguage;
    private final AtomicBoolean closed = new AtomicBoolean();
    private PokerVideoLocalServer localServer;
    private CefApp cefApp;
    private CefClient client;
    private CefMessageRouter messageRouter;
    private volatile LibGdxCefBrowser browser;
    private volatile String state = "INICIALIZANDO REPRODUCTOR";
    private volatile String initializationError;
    private volatile boolean browserCreated;
    private volatile boolean browserClosing;
    private volatile boolean playerReady;
    private volatile boolean active = true;
    private volatile int currentIndex;
    private volatile int resumeSeconds;
    private volatile boolean resumeMuted = true;
    private volatile int resumeVolume = 100;
    private volatile long playlistRevision;
    private volatile long browserPlaylistRevision;
    private volatile String pageUrl;
    private volatile String refererOrigin;

    private PokerVideoRuntime(List<PokerVideoSearch.Video> videos,
            PokerVideoFrameBuffer frameBuffer, String language) {
        this.videos = List.copyOf(videos);
        this.frameBuffer = frameBuffer;
        playerLanguage = PokerVideoSearch.languageCode(language);
    }

    static PokerVideoRuntime initialize(List<PokerVideoSearch.Video> videos,
            PokerVideoFrameBuffer frameBuffer, Path dataDirectory,
            String language, int width, int height, double scaleFactor) {
        PokerVideoRuntime runtime = new PokerVideoRuntime(videos, frameBuffer,
                language);
        if (!PokerVideoPlatformSupport.isAvailable()) {
            runtime.initializationError = "JCEF no está disponible para "
                    + "esta plataforma o este artefacto";
            runtime.state = "REPRODUCTOR NO DISPONIBLE";
            return runtime;
        }
        try {
            runtime.initializeInternal(dataDirectory, width, height,
                    scaleFactor);
        } catch (Throwable failure) {
            runtime.initializationError = failure.getClass().getSimpleName()
                    + ": " + (failure.getMessage() == null
                            ? "sin detalle" : failure.getMessage());
            runtime.state = "REPRODUCTOR NO DISPONIBLE";
        }
        return runtime;
    }

    private void initializeInternal(Path dataDirectory, int width, int height,
            double scaleFactor) throws Exception {
        Files.createDirectories(dataDirectory);
        Path nativeDirectory = dataDirectory.resolve(
                "jcef-" + JCEF_MAVEN_VERSION);
        Path cacheRoot = dataDirectory.resolve("cache");
        Path logDirectory = dataDirectory.resolve("logs");
        Files.createDirectories(cacheRoot);
        Files.createDirectories(logDirectory);

        CefAppBuilder builder = new CefAppBuilder();
        builder.setInstallDir(nativeDirectory.toFile());
        builder.addJcefArgs("--use-alloy-style");
        // This isolated browser exists only for the user-enabled poker-video
        // overlay. Allow it to resume after its resources have been released
        // while minimized, even when the saved audio state was unmuted.
        builder.addJcefArgs("--autoplay-policy=no-user-gesture-required");
        CefSettings settings = builder.getCefSettings();
        settings.windowless_rendering_enabled = true;
        settings.root_cache_path = cacheRoot.toAbsolutePath().toString();
        // Keep the anonymous video profile in memory. This prevents an old
        // YouTube caption preference from turning subtitles on by default in
        // a later CoronaPoker run.
        settings.cache_path = "";
        settings.log_file = logDirectory.resolve("poker-video-cef.log")
                .toAbsolutePath().toString();
        settings.log_severity = CefSettings.LogSeverity.LOGSEVERITY_WARNING;
        settings.background_color = settings.new ColorType(255, 5, 8, 12);
        builder.setAppHandler(new MavenCefAppHandlerAdapter() {
            @Override
            public void stateHasChanged(CefAppState appState) {
                if (appState == CefAppState.TERMINATED && !closed.get()) {
                    state = "REPRODUCTOR FINALIZADO";
                }
            }
        });

        cefApp = builder.build();
        client = cefApp.createClient();
        installHandlers();
        openBrowserSession(width, height, scaleFactor);
    }

    private synchronized void openBrowserSession(int width, int height,
            double scaleFactor) throws Exception {
        if (browser != null || browserClosing || closed.get()) return;
        localServer = PokerVideoLocalServer.start(videos, currentIndex,
                resumeSeconds, resumeMuted, resumeVolume, playerLanguage);
        browserPlaylistRevision = playlistRevision;
        pageUrl = localServer.playerUrl();
        refererOrigin = localServer.origin();
        CefBrowserSettings browserSettings = new CefBrowserSettings();
        browserSettings.windowless_frame_rate = 30;
        browser = new LibGdxCefBrowser(client, pageUrl, null,
                browserSettings, frameBuffer, width, height);
        frameBuffer.setAcceptingFrames(true);
        browser.createImmediately();
        browser.resizeBrowser(width, height, scaleFactor);
        active = true;
    }

    private void installHandlers() {
        client.addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
            @Override
            public boolean onBeforePopup(CefBrowser source, CefFrame frame,
                    String targetUrl, String targetFrameName) {
                state = "ENLACE EXTERNO BLOQUEADO";
                return true;
            }

            @Override
            public void onAfterCreated(CefBrowser created) {
                browserCreated = true;
                created.setWindowlessFrameRate(30);
            }

            @Override
            public void onBeforeClose(CefBrowser closing) {
                browserClosing = false;
                browserCreated = false;
                playerReady = false;
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
            public void onLoadEnd(CefBrowser loaded, CefFrame frame,
                    int statusCode) {
                if (frame != null && frame.isMain() && statusCode >= 400) {
                    state = "ERROR HTTP " + statusCode;
                }
            }

            @Override
            public void onLoadError(CefBrowser failed, CefFrame frame,
                    ErrorCode errorCode, String errorText, String failedUrl) {
                if (frame != null && frame.isMain()) {
                    state = "ERROR DE CARGA: " + errorCode;
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
            public boolean onBeforeBrowse(CefBrowser source, CefFrame frame,
                    CefRequest request, boolean userGesture,
                    boolean isRedirect) {
                if (frame != null && frame.isMain()
                        && !allowedMainNavigation(request.getURL())) {
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
                return isYouTubeHost(request.getURL()) ? refererHandler : null;
            }
        });

        messageRouter = CefMessageRouter.create();
        messageRouter.addHandler(new CefMessageRouterHandlerAdapter() {
            @Override
            public boolean onQuery(CefBrowser source, CefFrame frame,
                    long queryId, String request, boolean persistent,
                    CefQueryCallback callback) {
                boolean handled = handlePlayerMessage(request);
                if (handled) callback.success("ok");
                return handled;
            }
        }, true);
        client.addMessageRouter(messageRouter);
    }

    private boolean handlePlayerMessage(String request) {
        if (request.startsWith("READY|")) {
            playerReady = true;
            synchronizeBrowserPlaylist();
            state = resumeMuted ? "REPRODUCIENDO EN SILENCIO"
                    : "REPRODUCIENDO CON AUDIO";
            return true;
        }
        if (request.startsWith("STATE|")) {
            state = request.substring("STATE|".length());
            return true;
        }
        if (request.startsWith("ERROR|")) {
            state = request.substring("ERROR|".length());
            return true;
        }
        if (request.startsWith("INDEX|")) {
            String[] parts = request.substring("INDEX|".length())
                    .split(":", 2);
            try {
                int next = Integer.parseInt(parts[0]);
                if (next >= 0 && next < videos.size()) {
                    currentIndex = next;
                }
            } catch (NumberFormatException ignored) {
            }
            return true;
        }
        if (request.startsWith("POSITION|")) {
            String[] parts = request.substring("POSITION|".length())
                    .split(":", 2);
            try {
                int index = Integer.parseInt(parts[0]);
                int seconds = parts.length > 1
                        ? Integer.parseInt(parts[1]) : 0;
                if (index >= 0 && index < videos.size()) {
                    currentIndex = index;
                    resumeSeconds = Math.max(0, seconds);
                }
            } catch (NumberFormatException ignored) {
            }
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
                        : resumeVolume;
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

    private boolean allowedMainNavigation(String value) {
        try {
            URI uri = URI.create(value);
            URI page = URI.create(pageUrl);
            return "about".equalsIgnoreCase(uri.getScheme())
                    || uri.getScheme().equalsIgnoreCase(page.getScheme())
                    && uri.getHost().equalsIgnoreCase(page.getHost())
                    && uri.getPort() == page.getPort();
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
        resumeSeconds = 0;
        state = "CARGANDO";
        browser.executeJavaScript("window.coronaSelectRelative && "
                + "window.coronaSelectRelative(" + direction + ");",
                pageUrl, 0);
    }

    void replaceVideos(List<PokerVideoSearch.Video> replacements) {
        List<PokerVideoSearch.Video> next = List.copyOf(replacements);
        if (next.isEmpty()) return;
        videos = next;
        currentIndex = 0;
        resumeSeconds = 0;
        playlistRevision++;
        synchronizeBrowserPlaylist();
    }

    private void synchronizeBrowserPlaylist() {
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
        current.executeJavaScript("window.coronaReplaceVideos && "
                + "window.coronaReplaceVideos(" + ids + ");",
                pageUrl, 0);
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

    String title() {
        return videos.get(Math.max(0, Math.min(currentIndex,
                videos.size() - 1))).title();
    }

    String initializationError() {
        return initializationError;
    }

    synchronized void releaseBrowserSession() {
        frameBuffer.setAcceptingFrames(false);
        LibGdxCefBrowser current = browser;
        browser = null;
        browserCreated = false;
        playerReady = false;
        active = false;
        if (current != null) {
            browserClosing = true;
            try {
                current.executeJavaScript("window.coronaReportPosition && "
                        + "window.coronaReportPosition();", pageUrl, 0);
                current.setWindowVisibility(false);
                current.setCloseAllowed();
                current.close(true);
            } catch (RuntimeException ignored) {
            }
        }
        if (localServer != null) {
            localServer.close();
            localServer = null;
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
        frameBuffer.setAcceptingFrames(false);
        if (browser != null) {
            try {
                browser.executeJavaScript("window.coronaSetActive && "
                        + "window.coronaSetActive(false);", pageUrl, 0);
                browser.setCloseAllowed();
                browser.close(true);
            } catch (RuntimeException ignored) {
            }
        }
        if (cefApp != null) cefApp.dispose();
        if (localServer != null) localServer.close();
    }
}
