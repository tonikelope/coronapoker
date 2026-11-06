package com.tonikelope.jcefpoc;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import me.friwi.jcefmaven.CefAppBuilder;
import me.friwi.jcefmaven.CefBuildInfo;
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter;
import me.friwi.jcefmaven.impl.progress.ConsoleProgressHandler;
import org.cef.CefApp;
import org.cef.CefApp.CefAppState;
import org.cef.CefBrowserSettings;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.browser.LibGdxCefBrowser;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefLifeSpanHandlerAdapter;
import org.cef.handler.CefLoadHandler.ErrorCode;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.handler.CefResourceRequestHandlerAdapter;
import org.cef.misc.BoolRef;
import org.cef.network.CefRequest;

/** Owns JCEF, the local player origin and the windowless browser lifecycle. */
public final class JcefRuntime implements AutoCloseable {
    public static final String JCEF_MAVEN_VERSION = "152.0.6";

    private final PocConfig config;
    private final ChromiumFrameBuffer frameBuffer;
    private final AtomicBoolean closed = new AtomicBoolean();
    private LocalPlayerServer localServer;
    private CefApp cefApp;
    private CefClient client;
    private CefMessageRouter messageRouter;
    private LibGdxCefBrowser browser;
    private volatile String playerState = "INICIALIZANDO";
    private volatile String versionText = "JCEF Maven " + JCEF_MAVEN_VERSION;
    private volatile String initializationError;
    private volatile boolean browserCreated;
    private volatile boolean browserActive = true;
    private String pageUrl;
    private String refererOrigin;

    private JcefRuntime(PocConfig config, ChromiumFrameBuffer frameBuffer) {
        this.config = config;
        this.frameBuffer = frameBuffer;
    }

    public static JcefRuntime initialize(PocConfig config, ChromiumFrameBuffer frameBuffer) {
        JcefRuntime runtime = new JcefRuntime(config, frameBuffer);
        try {
            runtime.initializeInternal();
        } catch (Throwable failure) {
            failure.printStackTrace(System.err);
            runtime.initializationError = failure.getClass().getSimpleName() + ": "
                    + (failure.getMessage() == null ? "sin detalle" : failure.getMessage());
            runtime.playerState = "ERROR DE INICIALIZACIÓN";
        }
        return runtime;
    }

    private void initializeInternal() throws Exception {
        Files.createDirectories(config.dataDirectory());
        configurePlayerPage();

        Path nativeDirectory = config.dataDirectory().resolve("jcef-" + JCEF_MAVEN_VERSION);
        Path cacheRoot = config.dataDirectory().resolve("cache");
        Path cacheProfile = cacheRoot.resolve("profile");
        Path logDirectory = config.dataDirectory().resolve("logs");
        Files.createDirectories(cacheProfile);
        Files.createDirectories(logDirectory);

        CefAppBuilder builder = new CefAppBuilder();
        builder.setInstallDir(nativeDirectory.toFile());
        builder.setProgressHandler(new ConsoleProgressHandler());
        builder.addJcefArgs("--use-alloy-style");
        CefSettings settings = builder.getCefSettings();
        settings.windowless_rendering_enabled = true;
        settings.root_cache_path = cacheRoot.toAbsolutePath().toString();
        settings.cache_path = cacheProfile.toAbsolutePath().toString();
        settings.log_file = logDirectory.resolve("cef.log").toAbsolutePath().toString();
        settings.log_severity = CefSettings.LogSeverity.LOGSEVERITY_INFO;
        settings.background_color = settings.new ColorType(255, 5, 8, 12);

        builder.setAppHandler(new MavenCefAppHandlerAdapter() {
            @Override
            public void stateHasChanged(CefAppState state) {
                if (state == CefAppState.TERMINATED && !closed.get()) {
                    playerState = "CEF TERMINADO";
                }
            }
        });

        cefApp = builder.build();
        CefApp.CefVersion cefVersion = cefApp.getVersion();
        CefBuildInfo buildInfo = CefBuildInfo.fromClasspath();
        if (cefVersion != null) {
            versionText = "JCEF " + cefVersion.getJcefVersion() + " | CEF "
                    + cefVersion.getCefVersion() + " | Chromium "
                    + cefVersion.getChromeVersion() + " | " + buildInfo.getReleaseTag();
        }

        client = cefApp.createClient();
        installHandlers();
        CefBrowserSettings browserSettings = new CefBrowserSettings();
        browserSettings.windowless_frame_rate = 30;
        browser = new LibGdxCefBrowser(client, pageUrl, null, browserSettings, frameBuffer,
                config.browserWidth(), config.browserHeight());
        browser.createImmediately();
    }

    private void configurePlayerPage() throws Exception {
        if (config.pageUrl().isBlank()) {
            localServer = LocalPlayerServer.start(config.videoId());
            pageUrl = localServer.playerUrl();
            refererOrigin = localServer.origin();
            return;
        }
        String separator = config.pageUrl().contains("?") ? "&" : "?";
        pageUrl = config.pageUrl() + separator + "video="
                + URLEncoder.encode(config.videoId(), StandardCharsets.UTF_8);
        URI uri = URI.create(config.pageUrl());
        refererOrigin = uri.getScheme() + "://" + uri.getRawAuthority();
    }

    private void installHandlers() {
        client.addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
            @Override
            public boolean onBeforePopup(CefBrowser browser, CefFrame frame, String targetUrl,
                    String targetFrameName) {
                playerState = "POPUP BLOQUEADO";
                return true;
            }

            @Override
            public void onAfterCreated(CefBrowser createdBrowser) {
                browserCreated = true;
                playerState = "NAVEGADOR CREADO";
                createdBrowser.setWindowlessFrameRate(30);
            }

            @Override
            public void onBeforeClose(CefBrowser closingBrowser) {
                browserCreated = false;
            }
        });

        client.addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadEnd(CefBrowser loadedBrowser, CefFrame frame, int httpStatusCode) {
                if (frame != null && frame.isMain()) {
                    playerState = httpStatusCode >= 400
                            ? "HTTP " + httpStatusCode
                            : "PÁGINA CARGADA";
                }
            }

            @Override
            public void onLoadError(CefBrowser failedBrowser, CefFrame frame, ErrorCode errorCode,
                    String errorText, String failedUrl) {
                if (frame != null && frame.isMain()) {
                    playerState = "ERROR DE CARGA: " + errorCode + " / " + errorText;
                }
            }
        });

        client.addDisplayHandler(new CefDisplayHandlerAdapter() {
            @Override
            public boolean onConsoleMessage(CefBrowser consoleBrowser,
                    CefSettings.LogSeverity level, String message, String source, int line) {
                System.out.printf(Locale.ROOT, "[CEF %s] %s (%s:%d)%n", level, message, source,
                        line);
                return false;
            }
        });

        CefResourceRequestHandler refererHandler = new CefResourceRequestHandlerAdapter() {
            @Override
            public boolean onBeforeResourceLoad(CefBrowser requestBrowser, CefFrame frame,
                    CefRequest request) {
                String currentReferer = request.getHeaderByName("Referer");
                if (currentReferer == null || currentReferer.isBlank()) {
                    request.setHeaderByName("Referer", refererOrigin + "/", true);
                }
                return false;
            }
        };
        client.addRequestHandler(new CefRequestHandlerAdapter() {
            @Override
            public CefResourceRequestHandler getResourceRequestHandler(CefBrowser requestBrowser,
                    CefFrame frame, CefRequest request, boolean isNavigation, boolean isDownload,
                    String requestInitiator, BoolRef disableDefaultHandling) {
                return isYouTubeHost(request.getURL()) ? refererHandler : null;
            }
        });

        messageRouter = CefMessageRouter.create();
        messageRouter.addHandler(new CefMessageRouterHandlerAdapter() {
            @Override
            public boolean onQuery(CefBrowser queryBrowser, CefFrame frame, long queryId,
                    String request, boolean persistent, CefQueryCallback callback) {
                if (request.startsWith("STATE|")) {
                    playerState = request.substring("STATE|".length());
                } else if (request.startsWith("AUTOPLAY|")) {
                    playerState = "AUTOPLAY: " + request.substring("AUTOPLAY|".length());
                } else if (request.startsWith("ERROR|")) {
                    playerState = request.substring("ERROR|".length());
                } else {
                    return false;
                }
                callback.success("ok");
                return true;
            }
        }, true);
        client.addMessageRouter(messageRouter);
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

    public void resizeBrowser(int width, int height, double scaleFactor) {
        if (browser != null && browserCreated) {
            browser.resizeBrowser(width, height, scaleFactor);
        }
    }

    public void setBrowserActive(boolean active) {
        browserActive = active;
        frameBuffer.setAcceptingFrames(active);
        if (browser == null || !browserCreated) return;
        browser.setWindowVisibility(active);
        browser.executeJavaScript("window.pocSetActive && window.pocSetActive("
                + (active ? "true" : "false") + ");", pageUrl, 0);
        if (active) browser.requestRepaint();
    }

    public void requestFocus(boolean focused) {
        if (browser != null && browserCreated) browser.setFocus(focused);
    }

    public LibGdxCefBrowser browser() {
        return browser;
    }

    public ChromiumFrameBuffer frameBuffer() {
        return frameBuffer;
    }

    public String playerState() {
        return playerState;
    }

    public String versionText() {
        return versionText;
    }

    public String initializationError() {
        return initializationError;
    }

    public boolean browserCreated() {
        return browserCreated;
    }

    public boolean browserActive() {
        return browserActive;
    }

    public String pageUrl() {
        return pageUrl;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        frameBuffer.setAcceptingFrames(false);
        if (browser != null && browserCreated) {
            try {
                browser.executeJavaScript("window.pocSetActive && window.pocSetActive(false);",
                        pageUrl, 0);
                browser.setCloseAllowed();
                browser.close(true);
            } catch (RuntimeException failure) {
                failure.printStackTrace(System.err);
            }
        }
        if (cefApp != null) cefApp.dispose();
        if (localServer != null) localServer.close();
    }
}
