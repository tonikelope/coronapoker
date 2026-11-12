/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.Viewport;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.cef.browser.LibGdxCefBrowser;

/** Floating, draggable video window shown only by the menu and waiting room. */
final class PokerVideoOverlay implements AutoCloseable {

    static final float MIN_WIDTH = 260f;
    static final float HEADER_HEIGHT = 52f;
    static final float FOOTER_HEIGHT = 48f;
    static final float CHROME_HEIGHT = HEADER_HEIGHT + FOOTER_HEIGHT;
    // The frontend viewport scales this 1920×1080 layout to the display.
    private static final float DEFAULT_NORMAL_WIDTH = 514f;
    private static final float DEFAULT_SHORT_WIDTH = 330f;
    private static final float DEFAULT_NORMAL_BOTTOM = 100f;
    private static final float DEFAULT_SHORT_BOTTOM = 108f;
    private static final float WINDOW_MARGIN = 8f;
    private static final String OPACITY_PREFERENCE = "poker_video_opacity";
    private static final int MIN_OPACITY = 10;
    private static final int MAX_OPACITY = 100;
    private static final float OPACITY_TRACK_INSET = 7f;
    private static final float COMPACT_FAVORITES_WIDTH = 70f;
    private static final float RESIZE_EDGE_HIT = 11f;
    private static final float RESIZE_CORNER_HIT = 22f;
    private static final float CHROME_RADIUS = 15f;
    private static final float SHORT_ASPECT = 9f / 16f;
    private static final float NORMAL_ASPECT = 16f / 9f;
    private static final float WINDOW_TRANSITION_SECONDS = 0.24f;
    private static final Color HEADER = new Color(
            (GdxSettingsStyle.PANEL_LIGHT_RGBA & 0xffffff00) | 0xff);
    private static final Color GOLD = new Color(
            GdxSettingsStyle.GOLD_RGBA);
    private static final Color MUTED = new Color(
            GdxSettingsStyle.MUTED_RGBA);
    private static final Color OPACITY_TRACK = new Color(
            GdxSettingsStyle.TOGGLE_OFF_RGBA);

    private final PokerVideoFrameBuffer frameBuffer =
            new PokerVideoFrameBuffer();
    private final ExecutorService initializer =
            Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task,
                        "CoronaPoker-poker-video-initializer");
                thread.setDaemon(true);
                return thread;
            });
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong initializationGeneration = new AtomicLong();
    private final Object initializationLock = new Object();
    private Future<?> initializationTask;
    private final PokerVideoTitles titles = new PokerVideoTitles();
    private Future<List<PokerVideoSearch.Video>> discoveryTask;
    private long discoveryCatalogRevision;
    private long nextDiscoverySearch;
    private int discoveryVariant;
    private volatile long nativeStartupSince;
    private final GlyphLayout glyph = new GlyphLayout();
    private final Rectangle iconBounds = new Rectangle();
    private final Rectangle panelBounds = new Rectangle();
    private final Rectangle transitionBounds = new Rectangle();
    private final Rectangle videoBounds = new Rectangle();
    private final Rectangle headerBounds = new Rectangle();
    private final Rectangle footerBounds = new Rectangle();
    private final Rectangle previousBounds = new Rectangle();
    private final Rectangle nextBounds = new Rectangle();
    private final Rectangle closeBounds = new Rectangle();
    private final Rectangle maximizeBounds = new Rectangle();
    private final Rectangle favoriteBounds = new Rectangle();
    private final Rectangle favoritesModeBounds = new Rectangle();
    private final Rectangle shareBounds = new Rectangle();
    private final Rectangle videoTypeBounds = new Rectangle();
    private final Rectangle opacityBounds = new Rectangle();
    private final Rectangle retryBounds = new Rectangle();
    private final PokerVideoCatalog catalog;
    private final Consumer<String> videoTypeChanged;
    private Consumer<PokerVideoSearch.Video> shareAction;
    private final Properties windowPreferences;
    private final Runnable persistWindowPreferences;
    private final boolean openOnStartup;
    private boolean startupHandled;
    private final GdxGameText text = new GdxGameText("es");
    private volatile boolean catalogLoading;
    private volatile long appliedCatalogRevision = -1;
    private volatile String appliedLanguage = "es";
    private boolean favoriteSaveFailed;
    private boolean documentErrorVisible;
    private boolean recreatedAfterTimeout;
    private volatile PokerVideoRuntime runtime;
    private Texture loadingSpade;
    private Texture minimizedLogo;
    private volatile String loadingState = "gdx.video.loading.search";
    private volatile String loadingError;
    private boolean initializationStarted;
    private volatile boolean featureEnabled;
    private volatile boolean systemAvailable;
    private volatile boolean hostVisible;
    private volatile boolean minimized;
    private boolean restoreWindowAfterTable;
    private boolean animateWindow;
    private float transitionElapsed = WINDOW_TRANSITION_SECONDS;
    private boolean geometryInitialized;
    private boolean geometryDirty;
    private boolean runtimeActive;
    private boolean browserFocused;
    private volatile String requestedCatalogKey;
    private String selectedVideoType = PokerVideoSearch.TYPE_NORMAL;
    private PokerVideoRuntime.Selection displayedSelection;
    private float videoAspect = NORMAL_ASPECT;
    private float worldWidth;
    private float worldHeight;
    private float x;
    private float y;
    private float videoWidth = DEFAULT_NORMAL_WIDTH;
    private float videoHeight = DEFAULT_NORMAL_WIDTH / NORMAL_ASPECT;
    private int videoOpacity;
    private Capture capture = Capture.NONE;
    private final GdxFloatingWindowPointer coveredPress = new GdxFloatingWindowPointer();
    private boolean videoHovered;
    private int capturedPointer = -1;
    private int capturedButton = -1;
    private float captureStartX;
    private float captureStartY;
    private float startWindowX;
    private float startWindowY;
    private float startWidth;
    private ResizeHandle resizeHandle = ResizeHandle.NONE;
    private final Vector2 resizeCursor = new Vector2();
    private int pressedButtons;

    PokerVideoOverlay() {
        this(PokerVideoFavorites.userStore(), ignored -> { });
    }

    PokerVideoOverlay(Consumer<String> videoTypeChanged) {
        this(PokerVideoFavorites.userStore(), videoTypeChanged);
    }

    PokerVideoOverlay(boolean openOnStartup, Consumer<String> videoTypeChanged,
            Properties windowPreferences, Runnable persistWindowPreferences) {
        this(PokerVideoFavorites.userStore(), videoTypeChanged, openOnStartup,
                windowPreferences, persistWindowPreferences);
    }

    PokerVideoOverlay(PokerVideoFavorites favorites, Consumer<String> videoTypeChanged) {
        this(favorites, videoTypeChanged, true);
    }

    PokerVideoOverlay(PokerVideoFavorites favorites, Consumer<String> videoTypeChanged,
            boolean openOnStartup) {
        this(favorites, videoTypeChanged, openOnStartup, new Properties(), () -> { });
    }

    PokerVideoOverlay(PokerVideoFavorites favorites, Consumer<String> videoTypeChanged,
            boolean openOnStartup, Properties windowPreferences, Runnable persistWindowPreferences) {
        catalog = new PokerVideoCatalog(Objects.requireNonNull(favorites));
        this.videoTypeChanged = Objects.requireNonNull(videoTypeChanged);
        this.openOnStartup = openOnStartup;
        this.windowPreferences = Objects.requireNonNull(windowPreferences);
        this.persistWindowPreferences = Objects.requireNonNull(persistWindowPreferences);
        videoOpacity = readOpacity(windowPreferences);
    }

    private static int readOpacity(Properties preferences) {
        try {
            return MathUtils.clamp(Integer.parseInt(preferences.getProperty(
                    OPACITY_PREFERENCE, Integer.toString(MAX_OPACITY))),
                    MIN_OPACITY, MAX_OPACITY);
        } catch (NumberFormatException ignored) {
            return MAX_OPACITY;
        }
    }

    void update(boolean available, boolean enabled, boolean visible,
            String language,
            String videoType,
            float width, float height) {
        boolean firstUpdate = !startupHandled;
        startupHandled = true;
        if (width != worldWidth || height != worldHeight) {
            if (capture == Capture.MOVE || capture == Capture.RESIZE) {
                cancelHiddenCapture();
            }
            // Refit from the user's saved layout, not from geometry already
            // constrained by a smaller viewport.
            geometryInitialized = false;
        }
        worldWidth = width;
        worldHeight = height;
        boolean wasAvailable = systemAvailable;
        systemAvailable = available;
        String languageCode = PokerVideoSearch.languageCode(language);
        if (!text.language().equals(languageCode)) text.setLanguage(languageCode);
        if (!available) {
            titles.update(null);
            if (wasAvailable) {
                initializationGeneration.incrementAndGet();
                cancelInitializationTask();
                catalogLoading = false;
                if (runtime == null) initializationStarted = false;
            }
            featureEnabled = false;
            restoreWindowAfterTable = false;
            minimized = true;
            hostVisible = false;
            finishWindowTransition();
            PokerVideoRuntime current = runtime;
            if (current != null && current.browser() != null) {
                releaseBrowserSession(false);
            }
            cancelHiddenCapture();
            browserFocused = false;
            updateBounds();
            return;
        }
        boolean wasEnabled = featureEnabled;
        featureEnabled = enabled;
        if (!enabled) {
            restoreWindowAfterTable = false;
            if (wasEnabled) {
                initializationGeneration.incrementAndGet();
                cancelInitializationTask();
                catalogLoading = false;
                initializationStarted = false;
                requestedCatalogKey = null;
            }
            minimized = true;
        } else if (!wasEnabled) {
            minimized = !firstUpdate || !openOnStartup;
            recreatedAfterTimeout = false;
        }
        String normalizedType = PokerVideoSearch.videoType(videoType);
        catalog.setType(normalizedType);
        // A disabled feature is fully absent. "Minimized" is only a runtime
        // window state while the feature remains enabled; it must not turn a
        // disabled preference back into a floating activation icon.
        hostVisible = visible && enabled;
        String catalogKey = PokerVideoSearch.languageCode(language) + ":"
                + normalizedType;
        boolean favoritesMode = catalog.favoritesMode();
        boolean sharedMode = catalog.sharedMode();
        PokerVideoRuntime current = runtime;
        if (current != null) {
            String startupError = current.initializationError();
            if (startupError != null) {
                loadingError = startupError;
                documentErrorVisible = false;
            } else {
                boolean needsRecovery = current.pollRecovery(hostVisible && !minimized
                        && !catalogLoading && !emptyFavorites());
                if (needsRecovery && !recreatedAfterTimeout) {
                    recreatedAfterTimeout = true;
                    // Reuse the same close/reopen path as the video window.
                    releaseBrowserSession(true);
                }
                if (current.documentLoadError() != null) {
                    loadingError = current.documentLoadError();
                    documentErrorVisible = true;
                } else if (documentErrorVisible) {
                    loadingError = null;
                    documentErrorVisible = false;
                }
            }
        }
        if (current != null && !catalogLoading
                && (appliedCatalogRevision != catalog.revision()
                || !appliedLanguage.equals(languageCode))) {
            List<PokerVideoSearch.Video> selected = catalog.videos();
            if (!selected.isEmpty()) {
                current.replaceVideos(selected, languageCode, favoritesMode || sharedMode);
            }
            appliedCatalogRevision = catalog.revision();
            appliedLanguage = languageCode;
        }
        List<PokerVideoSearch.Video> videos = catalog.videos();
        displayedSelection = null;
        if (current != null && appliedCatalogRevision == catalog.revision() && !videos.isEmpty()) {
            displayedSelection = current.selection();
        } else if ((favoritesMode || sharedMode) && !videos.isEmpty()) {
            displayedSelection = new PokerVideoRuntime.Selection(videos.get(0), 0, videos.size());
        }
        updateVideoGeometry((favoritesMode || sharedMode) && displayedSelection != null
                ? displayedSelection.video().type() : normalizedType);
        if (firstUpdate && !minimized) beginWindowTransition();
        if (hostVisible && !minimized && (!initializationStarted
                || !favoritesMode && !sharedMode
                        && !catalogKey.equals(requestedCatalogKey))) {
            start(language, normalizedType, catalogKey);
        }
        boolean shouldRun = hostVisible && !minimized && !emptyFavorites()
                && !catalogLoading && loadingError == null;
        titles.update(shouldRun && displayedSelection != null ? displayedSelection.video() : null);
        if (current != null && !enabled && current.browser() != null) {
            releaseBrowserSession(false);
        }
        BrowserSize size = browserSize();
        if (current != null && shouldRun && current.browser() == null) {
            current.resumeBrowserSession(size.width(), size.height(), displayScale());
            runtimeActive = true;
        }
        if (current != null && shouldRun != runtimeActive) {
            runtimeActive = shouldRun;
            current.setActive(shouldRun);
        }
        if (current != null && shouldRun) {
            current.resize(size.width(), size.height(), displayScale());
        }
        if (shouldRun && current != null && current.ready()
                && !favoritesMode && !sharedMode
                && catalogKey.equals(requestedCatalogKey)
                && appliedCatalogRevision == catalog.revision()) {
            updateDiscovery(current, languageCode, normalizedType);
        } else if (discoveryTask != null) {
            discoveryTask.cancel(true);
            discoveryTask = null;
        }
        if (!hostVisible) {
            finishWindowTransition();
            cancelHiddenCapture();
            browserFocused = false;
        }
        updateBounds();
    }

    private void updateVideoGeometry(String videoType) {
        if (capture == Capture.MOVE || capture == Capture.RESIZE) return;
        if (!geometryInitialized || !videoType.equals(selectedVideoType)) {
            geometryInitialized = true;
            selectedVideoType = videoType;
            boolean shorts = PokerVideoSearch.TYPE_SHORTS.equals(videoType);
            videoAspect = shorts ? SHORT_ASPECT : NORMAL_ASPECT;
            videoWidth = Math.min(shorts ? DEFAULT_SHORT_WIDTH : DEFAULT_NORMAL_WIDTH, maximumVideoWidth());
            x = worldWidth - videoWidth - WINDOW_MARGIN;
            y = shorts ? DEFAULT_SHORT_BOTTOM : DEFAULT_NORMAL_BOTTOM;
            restoreWindowGeometry();
        }
        clampWindow();
    }

    private String windowGeometryKey() {
        return "poker_video_window_" + selectedVideoType;
    }

    private void restoreWindowGeometry() {
        String[] stored = windowPreferences.getProperty(windowGeometryKey(), "").split(",", 3);
        if (stored.length != 3) return;
        try {
            float savedX = Float.parseFloat(stored[0]);
            float savedY = Float.parseFloat(stored[1]);
            float savedWidth = Float.parseFloat(stored[2]);
            if (Float.isFinite(savedX) && Float.isFinite(savedY)
                    && Float.isFinite(savedWidth) && savedWidth > 0f) {
                x = savedX;
                y = savedY;
                videoWidth = savedWidth;
            }
        } catch (NumberFormatException ignored) {
            // Use the default layout if the preference is invalid.
        }
    }

    private void saveWindowGeometry() {
        if (!geometryDirty) return;
        geometryDirty = false;
        String key = windowGeometryKey();
        String geometry = x + "," + y + "," + videoWidth;
        if (geometry.equals(windowPreferences.getProperty(key))) return;
        windowPreferences.setProperty(key, geometry);
        persistWindowPreferences.run();
    }

    private void start(String language, String videoType,
            String catalogKey) {
        if (runtime == null) {
            java.util.logging.Logger.getLogger(PokerVideoOverlay.class.getName())
                    .info("Poker video | game OpenGL renderer=" + GdxGraphicsInfo.renderer());
        }
        initializationStarted = true;
        catalogLoading = true;
        loadingError = null;
        requestedCatalogKey = catalogKey;
        discoveryVariant = 0;
        nextDiscoverySearch = 0L;
        long generation = initializationGeneration.incrementAndGet();
        synchronized (initializationLock) {
        initializationTask = initializer.submit(() -> {
            try {
                if (closed.get() || generation != initializationGeneration.get()) return;
                loadingState = "gdx.video.loading.search";
                boolean ordered = catalog.favoritesMode() || catalog.sharedMode();
                List<PokerVideoSearch.Video> searched = ordered
                        ? null : PokerVideoSearch.shuffled(
                                PokerVideoSearch.fetch(language, videoType),
                                ThreadLocalRandom.current());
                if (closed.get() || !systemAvailable || !featureEnabled
                        || generation != initializationGeneration.get()) {
                    return;
                }
                if (searched != null) catalog.replaceNormal(searched, videoType);
                var videos = catalog.videos();
                if (videos.isEmpty()) return;
                loadingState = "gdx.video.loading.player";
                PokerVideoRuntime current = runtime;
                if (current != null) {
                    loadingError = null;
                    return;
                }
                long revision = catalog.revision();
                synchronized (initializationLock) {
                    if (closed.get() || generation != initializationGeneration.get()) return;
                    nativeStartupSince = System.nanoTime();
                }
                BrowserSize initialSize = browserSize();
                PokerVideoRuntime created = PokerVideoRuntime.initialize(videos,
                        frameBuffer, language, ordered,
                        initialSize.width(), initialSize.height(),
                        displayScale());
                // Once CEF has started it must remain owned until app exit:
                // disposing it after a canceled load makes later reopening
                // impossible. A canceled/minimized load keeps only idle CEF.
                synchronized (initializationLock) {
                    if (closed.get()) {
                        created.close();
                        return;
                    }
                    appliedCatalogRevision = revision;
                    appliedLanguage = PokerVideoSearch.languageCode(language);
                    runtime = created;
                }
                loadingError = created.initializationError();
                runtimeActive = true;
                if (!systemAvailable || !featureEnabled || minimized
                        || generation != initializationGeneration.get()) {
                    runtimeActive = false;
                    created.releaseBrowserSession(false);
                } else if (!hostVisible) {
                    runtimeActive = false;
                    created.setActive(false);
                }
                BrowserSize currentSize = browserSize();
                created.resize(currentSize.width(), currentSize.height(), displayScale());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                if (generation == initializationGeneration.get() && !closed.get()) {
                    loadingError = "Inicialización cancelada";
                }
            } catch (Exception failure) {
                if (generation != initializationGeneration.get() || closed.get()) return;
                java.util.logging.Logger.getLogger(PokerVideoOverlay.class.getName()).log(
                        java.util.logging.Level.WARNING, "Poker video catalogue initialization failed", failure);
                loadingError = failure.getMessage() == null
                        ? failure.getClass().getSimpleName()
                        : failure.getMessage();
            } finally {
                synchronized (initializationLock) { nativeStartupSince = 0L; }
                if (generation == initializationGeneration.get()) catalogLoading = false;
            }
        });
        }
    }

    private void updateDiscovery(PokerVideoRuntime current, String language, String type) {
        var selection = current.selection();
        long now = System.nanoTime();
        if (discoveryTask != null) {
            if (!discoveryTask.isDone()) return;
            try {
                var additions = discoveryTask.get();
                if (discoveryCatalogRevision == catalog.revision()) {
                    var extended = catalog.extendNormal(additions, selection.index());
                    current.extendDiscovery(extended);
                    nextDiscoverySearch = now + TimeUnit.SECONDS.toNanos(2);
                }
            } catch (ExecutionException failure) {
                nextDiscoverySearch = now + TimeUnit.SECONDS.toNanos(20);
                current.extendDiscovery(catalog.videos());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                discoveryTask = null;
            }
            return;
        }
        if (!current.discoveryRequested() && selection.total() - selection.index() > 8) return;
        if (nextDiscoverySearch != 0L && now - nextDiscoverySearch < 0L) {
            if (current.discoveryRequested()) current.extendDiscovery(catalog.videos());
            return;
        }
        discoveryCatalogRevision = catalog.revision();
        int variant = ++discoveryVariant;
        discoveryTask = initializer.submit(() -> PokerVideoSearch.shuffled(
                PokerVideoSearch.fetch(language, type, variant), ThreadLocalRandom.current()));
    }

    private void updateBounds() {
        float totalHeight = videoHeight + CHROME_HEIGHT;
        PokerVideoIcon.bounds(worldWidth, iconBounds);
        panelBounds.set(x, y, videoWidth, totalHeight);
        videoBounds.set(x, y + FOOTER_HEIGHT, videoWidth, videoHeight);
        headerBounds.set(x, videoBounds.y + videoHeight, videoWidth, HEADER_HEIGHT);
        footerBounds.set(x, y, videoWidth, FOOTER_HEIGHT);
        previousBounds.set(x + 8f, headerBounds.y + 7f, 40f, 38f);
        nextBounds.set(x + 53f, headerBounds.y + 7f, 40f, 38f);
        closeBounds.set(x + videoWidth - 48f, headerBounds.y + 7f, 40f, 38f);
        maximizeBounds.set(closeBounds.x - 45f, closeBounds.y,
                closeBounds.width, closeBounds.height);
        float starWidth = videoWidth < 420f ? 32f : 40f;
        float opacityWidth = MathUtils.clamp(videoWidth * 0.23f, 62f, 160f);
        float left = x + 8f;
        favoriteBounds.set(left, y + 5f, starWidth, 38f);
        float modeLeft = left + starWidth + 5f;
        float shareWidth = shareAction == null ? 0f : 36f;
        float shareSpace = shareWidth == 0f ? 0f : shareWidth + 5f;
        float opacityX = x + (videoWidth - opacityWidth) / 2f;
        if (shareAction != null) {
            opacityX = Math.max(opacityX, modeLeft + 32f + shareSpace + 5f);
        }
        opacityBounds.set(opacityX, y + RESIZE_EDGE_HIT + 1f, opacityWidth, 32f);
        float modeWidth = Math.min(catalog.favoritesMode() ? 174f : 156f,
                opacityBounds.x - 5f - modeLeft - shareSpace);
        favoritesModeBounds.set(modeLeft, y + 5f, modeWidth, 38f);
        shareBounds.set(modeLeft + modeWidth + 5f, y + 5f, shareWidth, 38f);
        float right = x + videoWidth - 8f;
        float typeWidth = Math.min(124f, right - (opacityBounds.x + opacityWidth + 5f));
        videoTypeBounds.set(right - typeWidth, y + 5f, typeWidth, 38f);
        retryBounds.set(videoBounds.x + (videoWidth - 128f) / 2f,
                videoBounds.y + videoBounds.height / 2f - 90f, 128f, 38f);
    }

    void prepareFrame() {
        if (hostVisible && !minimized) frameBuffer.uploadPendingFrame();
    }

    void updateWindowAnimation(boolean enabled, float delta) {
        animateWindow = enabled;
        if (!windowTransitionActive()) return;
        if (!enabled || transitionElapsed + delta >= WINDOW_TRANSITION_SECONDS) {
            finishWindowTransition();
        } else {
            transitionElapsed += Math.max(0f, delta);
        }
    }

    private boolean windowTransitionActive() {
        return transitionElapsed < WINDOW_TRANSITION_SECONDS;
    }

    private void beginWindowTransition() {
        if (!animateWindow || !hostVisible) return;
        transitionElapsed = 0f;
        transitionBounds.set(minimized ? panelBounds : iconBounds);
    }

    private void finishWindowTransition() {
        if (!windowTransitionActive()) return;
        transitionElapsed = WINDOW_TRANSITION_SECONDS;
        if (minimized) frameBuffer.dispose();
    }

    private float updateTransitionBounds() {
        float phase = Interpolation.smooth.apply(
                transitionElapsed / WINDOW_TRANSITION_SECONDS);
        float openness = minimized ? 1f - phase : phase;
        PokerVideoIcon.logoBounds(minimizedLogo(), iconBounds, transitionBounds);
        transitionBounds.set(
                MathUtils.lerp(transitionBounds.x, panelBounds.x, openness),
                MathUtils.lerp(transitionBounds.y, panelBounds.y, openness),
                MathUtils.lerp(transitionBounds.width, panelBounds.width, openness),
                MathUtils.lerp(transitionBounds.height, panelBounds.height, openness));
        float crossfade = MathUtils.clamp((openness - 0.18f) / 0.64f, 0f, 1f);
        return Interpolation.smooth.apply(crossfade);
    }

    void drawShapes(ShapeRenderer shapes, Vector2 pointer) {
        if (!hostVisible) return;
        if (windowTransitionActive()) {
            float alpha = updateTransitionBounds();
            if (alpha > 0f) {
                float backingAlpha = alpha * videoAlpha();
                shapes.setColor(HEADER.r, HEADER.g, HEADER.b, HEADER.a * backingAlpha);
                GdxAntialiasedRoundedRect.fill(shapes, transitionBounds.x,
                        transitionBounds.y, transitionBounds.width,
                        transitionBounds.height,
                        CHROME_RADIUS * transitionBounds.height / panelBounds.height);
            }
            return;
        }
        if (minimized) return;
        float backdropAlpha = videoBackdropAlpha();
        if (backdropAlpha > 0f) {
            shapes.setColor(0f, 0f, 0f, backdropAlpha);
            shapes.rect(videoBounds.x, videoBounds.y, videoBounds.width, videoBounds.height);
        }
        shapes.setColor(HEADER);
        GdxAntialiasedRoundedRect.fill(shapes, headerBounds.x,
                headerBounds.y, headerBounds.width,
                headerBounds.height, CHROME_RADIUS);
        shapes.rect(headerBounds.x, headerBounds.y,
                headerBounds.width, headerBounds.height / 2f);
        GdxAntialiasedRoundedRect.fill(shapes, footerBounds.x, footerBounds.y,
                footerBounds.width, footerBounds.height, CHROME_RADIUS);
        shapes.rect(footerBounds.x, footerBounds.y + footerBounds.height / 2f,
                footerBounds.width, footerBounds.height / 2f);
        drawOpacitySlider(shapes, pointer);
        if (canRetry()) drawButton(shapes, pointer, retryBounds,
                GdxUiButtonStyle.Tone.NEUTRAL, Capture.RETRY, false);
        drawButton(shapes, pointer, previousBounds,
                GdxUiButtonStyle.Tone.NEUTRAL, Capture.PREVIOUS, false);
        drawButton(shapes, pointer, nextBounds,
                GdxUiButtonStyle.Tone.NEUTRAL, Capture.NEXT, true);
        drawButton(shapes, pointer, favoriteBounds,
                GdxUiButtonStyle.Tone.NEUTRAL, Capture.FAVORITE, false);
        drawButton(shapes, pointer, favoritesModeBounds,
                GdxUiButtonStyle.Tone.NEUTRAL, Capture.FAVORITES_MODE, false);
        if (shareAction != null) {
            drawButton(shapes, pointer, shareBounds,
                    GdxUiButtonStyle.Tone.NEUTRAL, Capture.SHARE, false);
        }
        if (!catalog.favoritesMode()) {
            drawButton(shapes, pointer, videoTypeBounds,
                    GdxUiButtonStyle.Tone.NEUTRAL, Capture.VIDEO_TYPE, false);
        }
        if (canMaximize()) {
            drawButton(shapes, pointer, maximizeBounds,
                    GdxUiButtonStyle.Tone.NEUTRAL, Capture.MAXIMIZE, false);
        }
        GdxUiDialogStyle.drawWindowClose(shapes, closeBounds.x, closeBounds.y,
                closeBounds.width, closeBounds.height, true,
                closeBounds.contains(pointer) ? 1f : 0f, capture == Capture.CLOSE, 1f);
    }

    private void drawOpacitySlider(ShapeRenderer shapes, Vector2 pointer) {
        float trackX = opacityBounds.x + OPACITY_TRACK_INSET;
        float trackWidth = opacityBounds.width - 2f * OPACITY_TRACK_INSET;
        float trackY = opacityBounds.y + 3f;
        float fill = trackWidth * (videoOpacity - MIN_OPACITY) / (MAX_OPACITY - MIN_OPACITY);
        shapes.setColor(OPACITY_TRACK);
        GdxAntialiasedRoundedRect.fill(shapes, trackX, trackY, trackWidth, 5f, 2.5f);
        shapes.setColor(GOLD);
        if (fill > 0f) {
            GdxAntialiasedRoundedRect.fill(shapes, trackX, trackY, fill, 5f, 2.5f);
        }
        if (opacityBounds.contains(pointer) || capture == Capture.OPACITY) {
            shapes.setColor(Color.WHITE);
        }
        shapes.circle(trackX + fill, trackY + 2.5f, 5f, 20);
    }

    void drawForegroundShapes(ShapeRenderer shapes, Vector2 pointer, Viewport viewport) {
        ResizeHandle handle = resizeCursorHandle(pointer.x, pointer.y);
        if (handle == ResizeHandle.NONE) return;
        if (capture == Capture.RESIZE) {
            handle.cursorPosition(panelBounds, pointer.x, pointer.y, resizeCursor);
        } else {
            resizeCursor.set(pointer);
        }
        float unitsPerPixel = viewport.getWorldWidth() / Math.max(1, viewport.getScreenWidth());
        boolean diagonal = handle.horizontal() && handle.vertical();
        float axisX = handle.horizontal() ? 1f : 0f;
        float axisY = handle.vertical() ? 1f : 0f;
        if (diagonal) {
            axisX = MathUtils.cosDeg(45f);
            axisY = handle.left() == handle.top() ? -axisX : axisX;
        }
        shapes.setColor(Color.BLACK);
        drawResizeArrow(shapes, resizeCursor.x, resizeCursor.y,
                axisX, axisY, unitsPerPixel, 3.2f);
        shapes.setColor(Color.WHITE);
        drawResizeArrow(shapes, resizeCursor.x, resizeCursor.y,
                axisX, axisY, unitsPerPixel, 1.5f);
    }

    private static void drawResizeArrow(ShapeRenderer shapes, float x, float y,
            float axisX, float axisY, float unitsPerPixel, float strokePixels) {
        float reach = 8.5f * unitsPerPixel;
        float wing = 4f * unitsPerPixel;
        float stroke = strokePixels * unitsPerPixel;
        shapes.rectLine(x - reach * axisX, y - reach * axisY,
                x + reach * axisX, y + reach * axisY, stroke);
        for (int end = -1; end <= 1; end += 2) {
            float tipX = x + end * reach * axisX;
            float tipY = y + end * reach * axisY;
            float baseX = tipX - end * wing * axisX;
            float baseY = tipY - end * wing * axisY;
            shapes.rectLine(tipX, tipY, baseX - wing * axisY,
                    baseY + wing * axisX, stroke);
            shapes.rectLine(tipX, tipY, baseX + wing * axisY,
                    baseY - wing * axisX, stroke);
        }
    }

    private void drawButton(ShapeRenderer shapes, Vector2 pointer,
            Rectangle bounds, GdxUiButtonStyle.Tone tone,
            Capture action, boolean pointsRight) {
        boolean hovered = bounds.contains(pointer);
        boolean enabled = actionEnabled(action);
        GdxUiButtonStyle.drawBorderless(shapes, bounds.x, bounds.y,
                bounds.width, bounds.height, tone, enabled,
                hovered ? 1f : 0f, capture == action, 1f);
        Color iconColor = GdxUiButtonStyle.labelColor(tone, enabled);
        if (action == Capture.RETRY) {
            return; // The translated retry label is painted with the other text.
        } else if (action == Capture.MAXIMIZE) {
            float size = Math.min(bounds.width, bounds.height) * 0.40f;
            shapes.setColor(iconColor);
            GdxAntialiasedRoundedRect.outline(shapes,
                    bounds.x + (bounds.width - size) / 2f,
                    bounds.y + (bounds.height - size) / 2f,
                    size, size, 2f, 1.8f);
        } else if (action == Capture.FAVORITE) {
            drawStar(shapes, bounds.x + bounds.width / 2f,
                    bounds.y + bounds.height / 2f, 10f,
                    currentIsFavorite(), iconColor);
        } else if (action == Capture.SHARE) {
            GdxUiIconRenderer.draw(shapes, GdxUiIconRenderer.Icon.SHARE,
                    bounds.x + bounds.width / 2f, bounds.y + bounds.height / 2f,
                    23f, iconColor, 1f);
        } else if (action == Capture.FAVORITES_MODE) {
            boolean iconOnly = bounds.width < COMPACT_FAVORITES_WIDTH;
            if (!iconOnly && bounds.width < 132f) return;
            float cx = bounds.x + (iconOnly ? bounds.width / 2f : 20f);
            float cy = bounds.y + bounds.height / 2f;
            if (catalog.favoritesMode()) {
                drawChevron(shapes, new Rectangle(cx - 12f, cy - 19f, 24f, 38f), false, iconColor);
            } else {
                shapes.setColor(iconColor);
                for (int line = -1; line <= 1; line++) {
                    shapes.rectLine(cx - 10f, cy + line * 6f, cx, cy + line * 6f, 1.8f);
                }
                drawStar(shapes, cx + 6f, cy, 6.5f, true, iconColor);
            }
        } else if (action == Capture.VIDEO_TYPE) {
            if (bounds.width < 110f) return;
            boolean shorts = !PokerVideoSearch.TYPE_SHORTS.equals(selectedVideoType);
            float cx = bounds.x + (bounds.width > 40f ? 20f : bounds.width / 2f);
            float cy = bounds.y + bounds.height / 2f;
            float w = shorts ? 12f : 21f;
            float h = shorts ? 21f : 12f;
            shapes.setColor(iconColor);
            GdxAntialiasedRoundedRect.outline(shapes, cx - w / 2f, cy - h / 2f, w, h, 2.5f, 1.7f);
            shapes.triangle(cx - 2f, cy - 3f, cx - 2f, cy + 3f, cx + 3f, cy);
        } else {
            drawChevron(shapes, bounds, pointsRight, iconColor);
        }
    }

    private static void drawStar(ShapeRenderer shapes, float cx, float cy,
            float radius, boolean filled, Color color) {
        shapes.setColor(color);
        for (int point = 0; point < 10; point++) {
            float a = (float) Math.toRadians(90 + point * 36);
            float b = (float) Math.toRadians(90 + (point + 1) * 36);
            float ra = radius * (point % 2 == 0 ? 1f : 0.45f);
            float rb = radius * (point % 2 == 0 ? 0.45f : 1f);
            float ax = cx + MathUtils.cos(a) * ra, ay = cy + MathUtils.sin(a) * ra;
            float bx = cx + MathUtils.cos(b) * rb, by = cy + MathUtils.sin(b) * rb;
            if (filled) shapes.triangle(cx, cy, ax, ay, bx, by);
            else shapes.rectLine(ax, ay, bx, by, 1.6f);
        }
    }

    private static void drawChevron(ShapeRenderer shapes, Rectangle bounds,
            boolean pointsRight, Color color) {
        float centerX = bounds.x + bounds.width / 2f;
        float centerY = bounds.y + bounds.height / 2f;
        float direction = pointsRight ? 1f : -1f;
        float outerX = centerX + direction * 6f;
        float innerX = centerX - direction * 5f;
        shapes.setColor(color);
        shapes.rectLine(innerX, centerY + 8f, outerX, centerY, 3.2f);
        shapes.rectLine(outerX, centerY, innerX, centerY - 8f, 3.2f);
    }

    void drawContent(SpriteBatch batch, BitmapFont font, BitmapFont tinyFont,
            Viewport viewport, Vector2 pointer) {
        if (!hostVisible) return;
        if (windowTransitionActive()) {
            drawWindowTransition(batch);
            return;
        }
        if (minimized) {
            PokerVideoIcon.drawLogo(batch, minimizedLogo(), iconBounds,
                    iconBounds.contains(pointer), capture == Capture.ICON);
            return;
        }
        Texture texture = visibleVideoTexture();
        if (texture != null) {
            batch.setColor(1f, 1f, 1f, videoAlpha());
            batch.draw(texture, videoBounds.x, videoBounds.y,
                    videoBounds.width, videoBounds.height);
            batch.setColor(Color.WHITE);
        } else {
            PokerVideoRuntime current = runtime;
            String playerLoading = current == null ? null : current.loadingKey();
            boolean empty = emptyFavorites();
            String displayedError = loadingError;
            if (displayedError == null && catalogLoading
                    && nativeStartupTimedOut(nativeStartupSince, System.nanoTime())) {
                displayedError = text.translate("gdx.video.startup_timeout");
            }
            tinyFont.setColor(displayedError == null || empty ? MUTED : Color.SALMON);
            String message = empty ? text.translate("gdx.video.favorites.empty")
                    : displayedError == null
                    ? text.translate(catalogLoading || playerLoading == null ? loadingState : playerLoading)
                    : "REPRODUCTOR NO DISPONIBLE\n" + displayedError;
            if (displayedError == null && !empty) {
                Texture spade = loadingSpade();
                float size = 34f;
                batch.setColor(Color.WHITE);
                batch.draw(spade,
                        videoBounds.x + (videoBounds.width - size) / 2f,
                        videoBounds.y + videoBounds.height / 2f + 30f,
                        size, size);
            }
            GdxPixelText.draw(tinyFont, batch, message, videoBounds.x + 30f,
                    videoBounds.y + videoBounds.height / 2f + (canRetry() ? 50f : 2f),
                    videoBounds.width - 60f, Align.center, true, viewport);
            if (canRetry()) {
                tinyFont.setColor(GOLD);
                GdxPixelText.draw(tinyFont, batch, text.translate("gdx.video.retry"),
                        retryBounds.x, retryBounds.y + 26f, retryBounds.width,
                        Align.center, false, viewport);
            }
        }
        boolean favoritesMode = catalog.favoritesMode();
        var selection = displayedSelection;
        String title = selection != null ? titles.title(selection.video())
                : emptyFavorites() ? text.translate("gdx.video.favorites.label") : "VÍDEOS DE PÓKER";
        float titleX = nextBounds.x + nextBounds.width + 12f;
        if (favoritesMode) {
            String counter = selection == null ? "0/0" : (selection.index() + 1) + "/" + selection.total();
            tinyFont.setColor(GOLD);
            glyph.setText(tinyFont, counter);
            float counterWidth = glyph.width;
            GdxPixelText.draw(tinyFont, batch, counter, titleX, headerBounds.y + 32f, viewport);
            titleX += counterWidth + 12f;
        }
        float titleWidth = Math.max(0f, headerControlsLeft() - titleX - 8f);
        font.setColor(GOLD);
        GdxPixelText.draw(font, batch, ellipsize(font, title, titleWidth),
                titleX, headerBounds.y + 33f, viewport);
        if (favoritesModeBounds.width >= COMPACT_FAVORITES_WIDTH) {
            drawButtonLabel(batch, tinyFont, viewport, favoritesModeBounds,
                    text.translate(favoritesMode
                            ? "gdx.video.normal.label" : "gdx.video.favorites.label"));
        }
        tinyFont.setColor(GOLD);
        GdxPixelText.draw(tinyFont, batch, videoOpacity + "%",
                opacityBounds.x, opacityBounds.y + 27f, opacityBounds.width,
                Align.center, false, viewport);
        if (!favoritesMode) drawButtonLabel(batch, tinyFont, viewport, videoTypeBounds,
                    text.translate(PokerVideoSearch.TYPE_SHORTS.equals(selectedVideoType)
                            ? "gdx.settings.choice.tipo_videos_poker.normal"
                            : "gdx.settings.choice.tipo_videos_poker.shorts"));
    }

    float videoBackdropAlpha() {
        // With a translucent frame the texture itself supplies the only layer.
        return videoOpacity == MAX_OPACITY || visibleVideoTexture() == null
                ? videoAlpha() : 0f;
    }

    private float videoAlpha() {
        return videoOpacity / (float) MAX_OPACITY;
    }

    private Texture visibleVideoTexture() {
        PokerVideoRuntime current = runtime;
        if (catalogLoading || loadingError != null || emptyFavorites()
                || current != null && current.loadingKey() != null) return null;
        return frameBuffer.texture();
    }

    private void drawButtonLabel(SpriteBatch batch, BitmapFont font,
            Viewport viewport, Rectangle bounds, String label) {
        font.setColor(catalogLoading ? MUTED : GOLD);
        boolean icon = bounds == favoritesModeBounds ? bounds.width >= 132f : bounds.width >= 110f;
        float inset = icon ? 35f : 6f;
        float width = bounds.width - inset - 6f;
        glyph.setText(font, label);
        boolean wrap = bounds == favoritesModeBounds && glyph.width > width;
        GdxPixelText.draw(font, batch, wrap ? label : ellipsize(font, label, width),
                bounds.x + inset, bounds.y + (wrap ? 34f : 26f), width,
                icon ? Align.left : Align.center, wrap, viewport);
    }

    private boolean emptyFavorites() {
        return catalog.favoritesMode() && catalog.videos().isEmpty();
    }

    private boolean currentIsFavorite() {
        return displayedSelection != null && catalog.isFavorite(displayedSelection.video());
    }

    private boolean actionEnabled(Capture action) {
        if (action == Capture.CLOSE) return true;
        if (action == Capture.MAXIMIZE) return canMaximize();
        if (action == Capture.RETRY) return canRetry();
        if (action == Capture.SHARE && shareAction == null) return false;
        if (catalogLoading) return false;
        if (action == Capture.FAVORITES_MODE) return true;
        if (action == Capture.VIDEO_TYPE) return !catalog.favoritesMode();
        if (loadingError != null) return false;
        PokerVideoRuntime current = runtime;
        return current != null && current.ready() && displayedSelection != null
                && appliedCatalogRevision == catalog.revision() && !emptyFavorites();
    }

    void addTooltips(BiConsumer<Rectangle, String> add) {
        if (!hostVisible || windowTransitionActive() || capture == Capture.RESIZE) return;
        if (minimized) {
            add.accept(iconBounds, "gdx.video.open");
            return;
        }
        add.accept(previousBounds, "gdx.video.previous");
        add.accept(nextBounds, "gdx.video.next");
        if (canMaximize()) add.accept(maximizeBounds, "gdx.video.maximize");
        add.accept(closeBounds, "gdx.video.minimize");
        add.accept(opacityBounds, "gdx.video.opacity");
        add.accept(favoriteBounds, favoriteSaveFailed ? "gdx.video.favorite.error"
                : currentIsFavorite() ? "gdx.video.favorite.remove" : "gdx.video.favorite.add");
        add.accept(favoritesModeBounds, catalog.favoritesMode()
                ? "gdx.video.normal.tooltip" : "gdx.video.favorites.tooltip");
        if (shareAction != null) add.accept(shareBounds, "gdx.video.share");
        if (!catalog.favoritesMode()) {
            add.accept(videoTypeBounds, PokerVideoSearch.TYPE_SHORTS.equals(selectedVideoType)
                    ? "gdx.video.type.normal" : "gdx.video.type.shorts");
        }
    }

    private void toggleFavorite() {
        if (!actionEnabled(Capture.FAVORITE)) return;
        try {
            var video = displayedSelection.video();
            catalog.toggleFavorite(video.hasTitle() ? video
                    : new PokerVideoSearch.Video(video.id(), titles.title(video), video.type()));
            favoriteSaveFailed = false;
        } catch (IOException failure) {
            favoriteSaveFailed = true;
            java.util.logging.Logger.getLogger(PokerVideoOverlay.class.getName())
                    .log(java.util.logging.Level.WARNING, "Cannot save poker video favorites", failure);
        }
    }

    private void toggleFavoritesMode() {
        if (!actionEnabled(Capture.FAVORITES_MODE)) return;
        catalog.toggleMode();
        loadingError = null;
        if (runtime == null || !catalog.favoritesMode() && !catalog.hasNormalVideos()) {
            initializationStarted = false;
        }
    }

    private boolean canRetry() {
        PokerVideoRuntime current = runtime;
        return loadingError != null && !catalogLoading && !emptyFavorites()
                && (current == null || current.initializationError() == null);
    }

    private void retryInitialization() {
        if (!canRetry()) return;
        recreatedAfterTimeout = false;
        PokerVideoRuntime current = runtime;
        if (current != null && current.documentLoadError() != null) releaseBrowserSession(true);
        documentErrorVisible = false;
        loadingError = null;
        initializationStarted = false;
        requestedCatalogKey = null;
    }

    private String ellipsize(BitmapFont font, String value, float maximum) {
        glyph.setText(font, value);
        if (glyph.width <= maximum) return value;
        String suffix = "…";
        int end = value.length();
        while (end > 0) {
            end = value.offsetByCodePoints(end, -1);
            String candidate = value.substring(0, end).stripTrailing()
                    + suffix;
            glyph.setText(font, candidate);
            if (glyph.width <= maximum) return candidate;
        }
        return suffix;
    }

    Texture loadingSpade() {
        if (loadingSpade == null) {
            loadingSpade = new Texture(Gdx.files.internal(
                    "images/menu/pica_roja.png"));
            loadingSpade.setFilter(Texture.TextureFilter.Linear,
                    Texture.TextureFilter.Linear);
        }
        return loadingSpade;
    }

    Texture minimizedLogo() {
        if (minimizedLogo == null) {
            minimizedLogo = new Texture(Gdx.files.internal("images/video_logo.png"), true);
            minimizedLogo.setFilter(Texture.TextureFilter.MipMapLinearLinear,
                    Texture.TextureFilter.Linear);
        }
        return minimizedLogo;
    }

    private void drawWindowTransition(SpriteBatch batch) {
        float alpha = updateTransitionBounds();
        Texture frame = frameBuffer.texture();
        if (frame != null && alpha > 0f) {
            float verticalScale = transitionBounds.height / panelBounds.height;
            float videoAreaHeight = videoHeight * verticalScale;
            float scale = Math.min(transitionBounds.width / frame.getWidth(),
                    videoAreaHeight / frame.getHeight());
            float width = frame.getWidth() * scale;
            float height = frame.getHeight() * scale;
            batch.setColor(1f, 1f, 1f, alpha * videoAlpha());
            batch.draw(frame, transitionBounds.x + (transitionBounds.width - width) / 2f,
                    transitionBounds.y + FOOTER_HEIGHT * verticalScale
                    + (videoAreaHeight - height) / 2f, width, height);
            batch.setColor(Color.WHITE);
        }
        if (alpha < 1f) {
            PokerVideoIcon.drawLogo(batch, minimizedLogo(), transitionBounds,
                    1f, 1f - alpha);
        }
    }

    boolean touchDown(float pointerX, float pointerY, int pointerIndex,
            int button) {
        if (!hostVisible) return false;
        if (capture != Capture.NONE || coveredPress.active()) {
            if (capturedPointer != pointerIndex || capturedButton != button) {
                coveredPress.captureSecondary(pointerIndex, button);
            }
            return true;
        }
        if (windowTransitionActive()) {
            if (!ownsHover(pointerX, pointerY)) return false;
            coveredPress.captureSecondary(pointerIndex, button);
            return true;
        }
        updateBounds();
        if (minimized) {
            if (button == Input.Buttons.LEFT
                    && iconBounds.contains(pointerX, pointerY)) {
                capture(Capture.ICON, pointerIndex, button, pointerX, pointerY);
                return true;
            }
            if (!iconBounds.contains(pointerX, pointerY)) return false;
            coveredPress.captureSecondary(pointerIndex, button);
            return true;
        }
        if (!panelBounds.contains(pointerX, pointerY)) {
            blurBrowser();
            return false;
        }
        if (button == Input.Buttons.LEFT) {
            ResizeHandle hit = resizeHandleAt(panelBounds, pointerX, pointerY);
            if (hit != ResizeHandle.NONE) {
                startWindowX = x;
                startWindowY = y;
                startWidth = videoWidth;
                resizeHandle = hit;
                capture(Capture.RESIZE, pointerIndex, button,
                        pointerX, pointerY);
                return true;
            }
            if (closeBounds.contains(pointerX, pointerY)) {
                capture(Capture.CLOSE, pointerIndex, button,
                        pointerX, pointerY);
                return true;
            }
            if (canMaximize() && maximizeBounds.contains(pointerX, pointerY)) {
                capture(Capture.MAXIMIZE, pointerIndex, button, pointerX, pointerY);
                return true;
            }
            if (previousBounds.contains(pointerX, pointerY)) {
                capture(Capture.PREVIOUS, pointerIndex, button,
                        pointerX, pointerY);
                return true;
            }
            if (nextBounds.contains(pointerX, pointerY)) {
                capture(Capture.NEXT, pointerIndex, button,
                        pointerX, pointerY);
                return true;
            }
            if (opacityBounds.contains(pointerX, pointerY)) {
                capture(Capture.OPACITY, pointerIndex, button, pointerX, pointerY);
                updateOpacity(pointerX);
                return true;
            }
            if (favoriteBounds.contains(pointerX, pointerY)) {
                capture(Capture.FAVORITE, pointerIndex, button, pointerX, pointerY);
                return true;
            }
            if (favoritesModeBounds.contains(pointerX, pointerY)) {
                capture(Capture.FAVORITES_MODE, pointerIndex, button, pointerX, pointerY);
                return true;
            }
            if (shareAction != null && shareBounds.contains(pointerX, pointerY)) {
                capture(Capture.SHARE, pointerIndex, button, pointerX, pointerY);
                return true;
            }
            if (videoTypeBounds.contains(pointerX, pointerY)) {
                capture(Capture.VIDEO_TYPE, pointerIndex, button, pointerX, pointerY);
                return true;
            }
            Rectangle draggable = new Rectangle(headerBounds);
            draggable.x = nextBounds.x + nextBounds.width + 4f;
            draggable.width = headerControlsLeft() - draggable.x - 4f;
            if (draggable.contains(pointerX, pointerY)) {
                capture(Capture.MOVE, pointerIndex, button,
                        pointerX, pointerY);
                startWindowX = x;
                startWindowY = y;
                return true;
            }
        }
        if (canRetry() && retryBounds.contains(pointerX, pointerY)
                && button == Input.Buttons.LEFT) {
            capture(Capture.RETRY, pointerIndex, button, pointerX, pointerY);
            return true;
        }
        if (videoBounds.contains(pointerX, pointerY)) {
            capture(Capture.VIDEO, pointerIndex, button, pointerX, pointerY);
            browserFocused = true;
            PokerVideoRuntime current = runtime;
            if (current != null) {
                current.requestFocus(true);
                pressedButtons |= 1 << button;
                dispatchMouse(MouseEvent.MOUSE_PRESSED, pointerX, pointerY,
                        awtButton(button), 1);
            }
            return true;
        }
        coveredPress.captureSecondary(pointerIndex, button);
        return true;
    }

    boolean touchDragged(float pointerX, float pointerY, int pointerIndex) {
        if (capture == Capture.NONE || capturedPointer != pointerIndex) {
            return ownsHover(pointerX, pointerY);
        }
        switch (capture) {
            case MOVE -> {
                float previousX = x;
                float previousY = y;
                x = startWindowX + (pointerX - captureStartX);
                y = startWindowY + (pointerY - captureStartY);
                clampWindow();
                geometryDirty |= x != previousX || y != previousY;
                updateBounds();
            }
            case RESIZE -> resizeFrom(pointerX, pointerY);
            case OPACITY -> updateOpacity(pointerX);
            case VIDEO -> dispatchMouse(MouseEvent.MOUSE_DRAGGED,
                    pointerX, pointerY, MouseEvent.NOBUTTON, 0);
            default -> { }
        }
        return true;
    }

    boolean touchUp(float pointerX, float pointerY, int pointerIndex,
            int button) {
        if (capture == Capture.NONE || capturedPointer != pointerIndex
                || capturedButton != button) {
            return coveredPress.releaseSecondary(pointerIndex, button) || ownsHover(pointerX, pointerY);
        }
        Capture completed = capture;
        capture = Capture.NONE;
        capturedPointer = -1;
        capturedButton = -1;
        switch (completed) {
            case MOVE, RESIZE -> saveWindowGeometry();
            case OPACITY -> {
                updateOpacity(pointerX);
                saveOpacity();
            }
            case ICON -> {
                if (iconBounds.contains(pointerX, pointerY)) {
                    restoreWindowAfterTable = false;
                    minimized = false;
                    recreatedAfterTimeout = false;
                    beginWindowTransition();
                    clampWindow();
                    if (canRetry()) retryInitialization();
                    else restoreBrowserSession();
                }
            }
            case CLOSE -> {
                if (closeBounds.contains(pointerX, pointerY)) {
                    minimized = true;
                    beginWindowTransition();
                    cancelPendingInitialization();
                    blurBrowser();
                    if (windowTransitionActive()) stopBrowserSession(false);
                    else releaseBrowserSession(false);
                }
            }
            case MAXIMIZE -> {
                if (canMaximize() && maximizeBounds.contains(pointerX, pointerY)) {
                    maximizeWindow();
                }
            }
            case PREVIOUS -> {
                if (previousBounds.contains(pointerX, pointerY)) {
                    select(-1);
                }
            }
            case NEXT -> {
                if (nextBounds.contains(pointerX, pointerY)) select(1);
            }
            case FAVORITE -> {
                if (favoriteBounds.contains(pointerX, pointerY)) toggleFavorite();
            }
            case FAVORITES_MODE -> {
                if (favoritesModeBounds.contains(pointerX, pointerY)) toggleFavoritesMode();
            }
            case SHARE -> {
                if (shareBounds.contains(pointerX, pointerY) && actionEnabled(Capture.SHARE)) {
                    var video = displayedSelection.video();
                    shareAction.accept(new PokerVideoSearch.Video(video.id(),
                            titles.title(video), video.type()));
                }
            }
            case VIDEO_TYPE -> {
                if (videoTypeBounds.contains(pointerX, pointerY) && actionEnabled(Capture.VIDEO_TYPE)) {
                    resumeSharedCatalog();
                    videoTypeChanged.accept(PokerVideoSearch.TYPE_SHORTS.equals(selectedVideoType)
                            ? PokerVideoSearch.TYPE_NORMAL : PokerVideoSearch.TYPE_SHORTS);
                }
            }
            case RETRY -> {
                if (retryBounds.contains(pointerX, pointerY)) retryInitialization();
            }
            case VIDEO -> {
                dispatchMouse(MouseEvent.MOUSE_RELEASED, pointerX, pointerY,
                        awtButton(button), 1);
                pressedButtons &= ~(1 << button);
            }
            default -> { }
        }
        resizeHandle = ResizeHandle.NONE;
        updateBounds();
        return true;
    }

    boolean cancelTouch(int pointerIndex, int button) {
        if (capture == Capture.NONE || capturedPointer != pointerIndex || capturedButton != button) {
            return coveredPress.releaseSecondary(pointerIndex, button);
        }
        if (capture == Capture.VIDEO) {
            pressedButtons &= ~(1 << button);
        }
        saveWindowGeometry();
        if (capture == Capture.OPACITY) saveOpacity();
        capture = Capture.NONE;
        resizeHandle = ResizeHandle.NONE;
        capturedPointer = -1;
        capturedButton = -1;
        return true;
    }

    private void cancelHiddenCapture() {
        coveredPress.clear();
        saveWindowGeometry();
        if (capture == Capture.OPACITY) saveOpacity();
        if (capture == Capture.VIDEO && capturedButton >= 0) {
            dispatchMouse(MouseEvent.MOUSE_RELEASED, captureStartX,
                    captureStartY, awtButton(capturedButton), 1);
        }
        capture = Capture.NONE;
        resizeHandle = ResizeHandle.NONE;
        capturedPointer = -1;
        capturedButton = -1;
        pressedButtons = 0;
    }

    private void updateOpacity(float pointerX) {
        float trackX = opacityBounds.x + OPACITY_TRACK_INSET;
        float trackWidth = opacityBounds.width - 2f * OPACITY_TRACK_INSET;
        float fraction = MathUtils.clamp((pointerX - trackX) / trackWidth, 0f, 1f);
        videoOpacity = Math.round(MathUtils.lerp(MIN_OPACITY, MAX_OPACITY, fraction));
    }

    private void saveOpacity() {
        String value = Integer.toString(videoOpacity);
        if (value.equals(windowPreferences.getProperty(OPACITY_PREFERENCE,
                Integer.toString(MAX_OPACITY)))) return;
        windowPreferences.setProperty(OPACITY_PREFERENCE, value);
        persistWindowPreferences.run();
    }

    boolean ownsHover(float pointerX, float pointerY) {
        if (!hostVisible) return false;
        if (capture != Capture.NONE || coveredPress.active()) return true;
        if (windowTransitionActive()) {
            return transitionBounds.contains(pointerX, pointerY)
                    || iconBounds.contains(pointerX, pointerY);
        }
        return (minimized ? iconBounds : panelBounds).contains(pointerX, pointerY);
    }

    boolean mouseMoved(float pointerX, float pointerY) {
        boolean owns = ownsHover(pointerX, pointerY);
        boolean over = owns && !minimized && !windowTransitionActive()
                && videoBounds.contains(pointerX, pointerY)
                && resizeHandleAt(panelBounds, pointerX, pointerY) == ResizeHandle.NONE;
        if (over || videoHovered) {
            dispatchMouse(over ? MouseEvent.MOUSE_MOVED : MouseEvent.MOUSE_EXITED, pointerX, pointerY,
                    MouseEvent.NOBUTTON, 0);
        }
        videoHovered = over;
        return owns;
    }

    boolean scrolled(float pointerX, float pointerY, float amountY) {
        if (!ownsHover(pointerX, pointerY)) return false;
        if (minimized || amountY == 0f
                || windowTransitionActive()
                || !videoBounds.contains(pointerX, pointerY)) return true;
        PokerVideoRuntime current = runtime;
        LibGdxCefBrowser browser = current == null ? null : current.browser();
        if (browser != null) {
            var bounds = browser.getViewRect(browser);
            browser.dispatchWheel(cefX(pointerX, bounds.width),
                    cefY(pointerY, bounds.height),
                    modifiers(), amountY > 0f ? 1 : -1);
        }
        return true;
    }

    private void resizeFrom(float pointerX, float pointerY) {
        if (resizeHandle == ResizeHandle.NONE) return;
        float previousX = x;
        float previousY = y;
        float previousWidth = videoWidth;
        float deltaX = pointerX - captureStartX;
        float deltaY = pointerY - captureStartY;
        float startHeight = startWidth / videoAspect;
        float horizontal = resizeHandle.left() ? -deltaX : deltaX;
        float vertical = resizeHandle.bottom() ? -deltaY : deltaY;
        float widthChange;
        if (resizeHandle.horizontal() && resizeHandle.vertical()) {
            // Project the pointer's movement onto the fixed-aspect diagonal.
            float heightPerWidth = 1f / videoAspect;
            widthChange = (horizontal + vertical * heightPerWidth)
                    / (1f + heightPerWidth * heightPerWidth);
        } else {
            widthChange = resizeHandle.horizontal() ? horizontal : vertical * videoAspect;
        }
        float maximum = maximumVideoWidth();
        videoWidth = MathUtils.clamp(startWidth + widthChange,
                Math.min(MIN_WIDTH, maximum), maximum);
        videoHeight = videoWidth / videoAspect;
        float startRight = startWindowX + startWidth;
        float startTop = startWindowY + startHeight + CHROME_HEIGHT;
        float startCenterX = startWindowX + startWidth / 2f;
        float startCenterY = startWindowY
                + (startHeight + CHROME_HEIGHT) / 2f;
        float totalHeight = videoHeight + CHROME_HEIGHT;
        x = resizeHandle.left() ? startRight - videoWidth
                : resizeHandle.right() ? startWindowX
                        : startCenterX - videoWidth / 2f;
        y = resizeHandle.bottom() ? startTop - totalHeight
                : resizeHandle.top() ? startWindowY
                        : startCenterY - totalHeight / 2f;
        clampWindow();
        geometryDirty |= x != previousX || y != previousY
                || videoWidth != previousWidth;
        updateBounds();
    }

    private void select(int delta) {
        if (resumeSharedCatalog()) return;
        if (!actionEnabled(Capture.NEXT)) return;
        PokerVideoRuntime current = runtime;
        if (current != null && current.ready()) current.selectRelative(delta);
    }

    void setShareAction(Consumer<PokerVideoSearch.Video> action) {
        shareAction = action;
    }

    void openSharedVideo(PokerVideoSearch.Video video) {
        cancelPendingInitialization();
        catalog.showShared(video);
        restoreWindowAfterTable = false;
        boolean opening = minimized;
        minimized = false;
        recreatedAfterTimeout = false;
        PokerVideoRuntime current = runtime;
        if (current != null && current.documentLoadError() != null) {
            releaseBrowserSession(true);
        }
        loadingError = current == null ? null : current.initializationError();
        documentErrorVisible = false;
        updateVideoGeometry(video.type());
        if (opening) beginWindowTransition();
        updateBounds();
    }

    private boolean resumeSharedCatalog() {
        if (!catalog.sharedMode()) return false;
        catalog.resumeCatalog();
        if (catalog.videos().isEmpty() && !catalog.favoritesMode()) {
            initializationStarted = false;
            requestedCatalogKey = null;
        }
        return true;
    }

    private void capture(Capture nextCapture, int pointerIndex, int button,
            float pointerX, float pointerY) {
        capture = nextCapture;
        capturedPointer = pointerIndex;
        capturedButton = button;
        captureStartX = pointerX;
        captureStartY = pointerY;
    }

    private void activateRuntime(boolean enabled) {
        PokerVideoRuntime current = runtime;
        boolean next = hostVisible && enabled;
        runtimeActive = next;
        if (current != null) current.setActive(next);
    }

    private void releaseBrowserSession(boolean preserveAudioForResume) {
        stopBrowserSession(preserveAudioForResume);
        frameBuffer.dispose();
    }

    private void stopBrowserSession(boolean preserveAudioForResume) {
        runtimeActive = false;
        PokerVideoRuntime current = runtime;
        if (current != null) {
            current.releaseBrowserSession(preserveAudioForResume);
        }
    }

    private void cancelPendingInitialization() {
        if ((runtime != null && !catalogLoading) || !initializationStarted) return;
        initializationGeneration.incrementAndGet();
        cancelInitializationTask();
        catalogLoading = false;
        initializationStarted = false;
        requestedCatalogKey = null;
    }

    private void cancelInitializationTask() {
        synchronized (initializationLock) {
            if (initializationTask != null && !initializationTask.isDone()) {
                // Search interruption cancels its HTTP request. Native CEF
                // initialization must finish and remain owned even if hidden.
                initializationTask.cancel(nativeStartupSince == 0L);
            }
        }
    }

    static boolean nativeStartupTimedOut(long started, long now) {
        return started != 0L && now - started >= TimeUnit.SECONDS.toNanos(60);
    }

    private void restoreBrowserSession() {
        PokerVideoRuntime current = runtime;
        if (current == null || emptyFavorites()) return;
        BrowserSize size = browserSize();
        current.resumeBrowserSession(size.width(), size.height(), displayScale());
        runtimeActive = true;
    }

    void resetHostInput() {
        if (videoHovered) dispatchMouse(MouseEvent.MOUSE_EXITED, -10_000f, -10_000f, MouseEvent.NOBUTTON, 0);
        videoHovered = false;
        cancelHiddenCapture();
        blurBrowser();
    }

    void minimizeForTable() {
        resetHostInput();
        finishWindowTransition();
        restoreWindowAfterTable = systemAvailable && featureEnabled && !minimized;
        if (!restoreWindowAfterTable) return;
        minimized = true;
        cancelPendingInitialization();
        releaseBrowserSession(true);
    }

    void restoreAfterTable() {
        if (restoreWindowAfterTable) minimized = false;
        restoreWindowAfterTable = false;
    }

    private void blurBrowser() {
        browserFocused = false;
        PokerVideoRuntime current = runtime;
        if (current != null) current.requestFocus(false);
    }

    private void dispatchMouse(int eventId, float pointerX, float pointerY,
            int awtButton, int clickCount) {
        PokerVideoRuntime current = runtime;
        LibGdxCefBrowser browser = current == null ? null : current.browser();
        if (browser != null) {
            var bounds = browser.getViewRect(browser);
            browser.dispatchMouse(eventId, cefX(pointerX, bounds.width),
                    cefY(pointerY, bounds.height),
                    awtButton, modifiers(), clickCount);
        }
    }

    private int cefX(float pointerX, int width) {
        return browserCoordinate(pointerX - videoBounds.x, videoBounds.width, width);
    }

    private int cefY(float pointerY, int height) {
        return browserCoordinate(videoBounds.y + videoBounds.height - pointerY,
                videoBounds.height, height);
    }

    static int browserCoordinate(float offset, float renderedSize, int browserSize) {
        return MathUtils.clamp((int) (offset * browserSize / renderedSize),
                0, browserSize - 1);
    }

    private int modifiers() {
        return LibGdxCefBrowser.awtModifiers(
                keyPressed(Input.Keys.SHIFT_LEFT, Input.Keys.SHIFT_RIGHT),
                keyPressed(Input.Keys.CONTROL_LEFT, Input.Keys.CONTROL_RIGHT),
                keyPressed(Input.Keys.ALT_LEFT, Input.Keys.ALT_RIGHT),
                Gdx.input.isKeyPressed(Input.Keys.SYM),
                (pressedButtons & (1 << Input.Buttons.LEFT)) != 0,
                (pressedButtons & (1 << Input.Buttons.MIDDLE)) != 0,
                (pressedButtons & (1 << Input.Buttons.RIGHT)) != 0);
    }

    private static boolean keyPressed(int first, int second) {
        return Gdx.input.isKeyPressed(first) || Gdx.input.isKeyPressed(second);
    }

    private static int awtButton(int gdxButton) {
        if (gdxButton == Input.Buttons.LEFT) return MouseEvent.BUTTON1;
        if (gdxButton == Input.Buttons.MIDDLE) return MouseEvent.BUTTON2;
        if (gdxButton == Input.Buttons.RIGHT) return MouseEvent.BUTTON3;
        return MouseEvent.NOBUTTON;
    }

    private double displayScale() {
        if (Gdx.graphics == null) return 1.0;
        return Math.max(1.0,
                Math.min(Gdx.graphics.getBackBufferWidth()
                                / (double) Math.max(1f, worldWidth),
                        Gdx.graphics.getBackBufferHeight()
                                / (double) Math.max(1f, worldHeight)));
    }

    BrowserSize browserSize() {
        // Scale the live texture during the gesture and resize Chromium after release.
        float width = capture == Capture.RESIZE ? startWidth : videoWidth;
        return browserSize(width, PokerVideoSearch.TYPE_SHORTS.equals(selectedVideoType));
    }

    static BrowserSize browserSize(float width, boolean shorts) {
        // Keep the browser viewport at the exact aspect ratio even between layout pixels.
        int widthUnit = shorts ? 9 : 16;
        int heightUnit = shorts ? 16 : 9;
        int units = Math.max(1, Math.round(width / widthUnit));
        return new BrowserSize(units * widthUnit, units * heightUnit);
    }

    record BrowserSize(int width, int height) {
    }

    private boolean canMaximize() {
        return videoWidth < maximumVideoWidth();
    }

    private float headerControlsLeft() {
        return canMaximize() ? maximizeBounds.x : closeBounds.x;
    }

    private void maximizeWindow() {
        videoWidth = maximumVideoWidth();
        videoHeight = videoWidth / videoAspect;
        x = (worldWidth - videoWidth) / 2f;
        y = (worldHeight - videoHeight - CHROME_HEIGHT) / 2f;
        clampWindow();
        geometryDirty = true;
        saveWindowGeometry();
    }

    private float maximumVideoWidth() {
        return Math.max(1f, Math.min(worldWidth - 2f * WINDOW_MARGIN,
                (worldHeight - CHROME_HEIGHT - 2f * WINDOW_MARGIN) * videoAspect));
    }

    private void clampWindow() {
        float maximum = maximumVideoWidth();
        videoWidth = MathUtils.clamp(videoWidth, Math.min(MIN_WIDTH, maximum),
                maximum);
        videoHeight = videoWidth / videoAspect;
        float totalHeight = videoHeight + CHROME_HEIGHT;
        x = MathUtils.clamp(x, WINDOW_MARGIN,
                Math.max(WINDOW_MARGIN, worldWidth - videoWidth - WINDOW_MARGIN));
        y = MathUtils.clamp(y, WINDOW_MARGIN,
                Math.max(WINDOW_MARGIN, worldHeight - totalHeight - WINDOW_MARGIN));
    }

    boolean capturesPointer() {
        return capture != Capture.NONE;
    }

    boolean hostVisible() {
        return hostVisible;
    }

    static ResizeHandle resizeHandleAt(Rectangle bounds, float pointerX,
            float pointerY) {
        if (bounds == null || !bounds.contains(pointerX, pointerY)) {
            return ResizeHandle.NONE;
        }
        float left = pointerX - bounds.x;
        float right = bounds.x + bounds.width - pointerX;
        float bottom = pointerY - bounds.y;
        float top = bounds.y + bounds.height - pointerY;
        boolean nearLeft = left <= RESIZE_EDGE_HIT;
        boolean nearRight = right <= RESIZE_EDGE_HIT;
        boolean nearBottom = bottom <= RESIZE_EDGE_HIT;
        boolean nearTop = top <= RESIZE_EDGE_HIT;
        if ((nearLeft && bottom <= RESIZE_CORNER_HIT)
                || (nearBottom && left <= RESIZE_CORNER_HIT)) {
            return ResizeHandle.BOTTOM_LEFT;
        }
        if ((nearRight && bottom <= RESIZE_CORNER_HIT)
                || (nearBottom && right <= RESIZE_CORNER_HIT)) {
            return ResizeHandle.BOTTOM_RIGHT;
        }
        if ((nearLeft && top <= RESIZE_CORNER_HIT)
                || (nearTop && left <= RESIZE_CORNER_HIT)) {
            return ResizeHandle.TOP_LEFT;
        }
        if ((nearRight && top <= RESIZE_CORNER_HIT)
                || (nearTop && right <= RESIZE_CORNER_HIT)) {
            return ResizeHandle.TOP_RIGHT;
        }
        if (nearLeft) return ResizeHandle.LEFT;
        if (nearRight) return ResizeHandle.RIGHT;
        if (nearBottom) return ResizeHandle.BOTTOM;
        if (nearTop) return ResizeHandle.TOP;
        return ResizeHandle.NONE;
    }

    Cursor.SystemCursor systemCursor(float pointerX, float pointerY) {
        return resizeCursorHandle(pointerX, pointerY) == ResizeHandle.NONE
                ? Cursor.SystemCursor.Arrow : Cursor.SystemCursor.None;
    }

    private ResizeHandle resizeCursorHandle(float pointerX, float pointerY) {
        if (!hostVisible || minimized || windowTransitionActive()) {
            return ResizeHandle.NONE;
        }
        return switch (capture) {
            case RESIZE -> resizeHandle;
            case NONE -> resizeHandleAt(panelBounds, pointerX, pointerY);
            default -> ResizeHandle.NONE;
        };
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        saveWindowGeometry();
        if (capture == Capture.OPACITY) saveOpacity();
        cancelInitializationTask();
        initializer.shutdown();
        titles.close();
        PokerVideoRuntime current;
        synchronized (initializationLock) {
            current = runtime;
            runtime = null;
        }
        if (current != null) current.close();
        if (loadingSpade != null) loadingSpade.dispose();
        if (minimizedLogo != null) minimizedLogo.dispose();
        frameBuffer.close();
    }

    private enum Capture {
        NONE, ICON, CLOSE, MAXIMIZE, PREVIOUS, NEXT, FAVORITE,
        FAVORITES_MODE, SHARE, VIDEO_TYPE, OPACITY, RETRY, MOVE, RESIZE, VIDEO
    }

    enum ResizeHandle {
        NONE(false, false, false, false),
        LEFT(true, false, false, false),
        RIGHT(false, true, false, false),
        BOTTOM(false, false, true, false),
        TOP(false, false, false, true),
        BOTTOM_LEFT(true, false, true, false),
        BOTTOM_RIGHT(false, true, true, false),
        TOP_LEFT(true, false, false, true),
        TOP_RIGHT(false, true, false, true);

        private final boolean left;
        private final boolean right;
        private final boolean bottom;
        private final boolean top;

        ResizeHandle(boolean left, boolean right, boolean bottom,
                boolean top) {
            this.left = left;
            this.right = right;
            this.bottom = bottom;
            this.top = top;
        }

        void cursorPosition(Rectangle bounds, float x, float y, Vector2 result) {
            float rightEdge = bounds.x + bounds.width;
            float topEdge = bounds.y + bounds.height;
            if (horizontal() && vertical()) {
                // Place the arrow on the curved part of the grabbed corner.
                float inset = CHROME_RADIUS * (1f - MathUtils.cosDeg(45f));
                result.set(left ? bounds.x + inset : rightEdge - inset,
                        bottom ? bounds.y + inset : topEdge - inset);
            } else if (horizontal()) {
                result.set(left ? bounds.x : rightEdge,
                        MathUtils.clamp(y, bounds.y + CHROME_RADIUS, topEdge - CHROME_RADIUS));
            } else {
                result.set(MathUtils.clamp(x, bounds.x + CHROME_RADIUS, rightEdge - CHROME_RADIUS),
                        bottom ? bounds.y : topEdge);
            }
        }

        boolean left() {
            return left;
        }

        boolean right() {
            return right;
        }

        boolean bottom() {
            return bottom;
        }

        boolean top() {
            return top;
        }

        boolean horizontal() {
            return left || right;
        }

        boolean vertical() {
            return bottom || top;
        }
    }
}
