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
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.Viewport;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.cef.browser.LibGdxCefBrowser;

/** Floating, draggable video window shown only by the menu and waiting room. */
final class PokerVideoOverlay implements AutoCloseable {

    static final float MIN_WIDTH = 260f;
    static final float INITIAL_WIDTH = MIN_WIDTH;
    static final float HEADER_HEIGHT = 52f;
    static final float FOOTER_HEIGHT = 48f;
    static final float CHROME_HEIGHT = HEADER_HEIGHT + FOOTER_HEIGHT;
    private static final float RESIZE_EDGE_HIT = 11f;
    private static final float RESIZE_CORNER_HIT = 22f;
    private static final float SHORT_ASPECT = 9f / 16f;
    private static final float NORMAL_ASPECT = 16f / 9f;
    private static final Color HEADER = new Color(
            (GdxSettingsStyle.PANEL_LIGHT_RGBA & 0xffffff00) | 0xff);
    private static final Color GOLD = new Color(
            GdxSettingsStyle.GOLD_RGBA);
    private static final Color MUTED = new Color(
            GdxSettingsStyle.MUTED_RGBA);

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
    private boolean startingNative;
    private final GlyphLayout glyph = new GlyphLayout();
    private final Rectangle iconBounds = new Rectangle();
    private final Rectangle panelBounds = new Rectangle();
    private final Rectangle videoBounds = new Rectangle();
    private final Rectangle headerBounds = new Rectangle();
    private final Rectangle footerBounds = new Rectangle();
    private final Rectangle previousBounds = new Rectangle();
    private final Rectangle nextBounds = new Rectangle();
    private final Rectangle closeBounds = new Rectangle();
    private final Rectangle favoriteBounds = new Rectangle();
    private final Rectangle favoritesModeBounds = new Rectangle();
    private final Rectangle videoTypeBounds = new Rectangle();
    private final Rectangle retryBounds = new Rectangle();
    private final PokerVideoCatalog catalog;
    private final Consumer<String> videoTypeChanged;
    private final boolean openOnStartup;
    private boolean startupHandled;
    private final GdxGameText text = new GdxGameText("es");
    private volatile boolean catalogLoading;
    private volatile long appliedCatalogRevision = -1;
    private volatile String appliedLanguage = "es";
    private boolean favoriteSaveFailed;
    private boolean documentErrorVisible;
    private volatile PokerVideoRuntime runtime;
    private Texture loadingSpade;
    private volatile String loadingState = "BUSCANDO VÍDEOS";
    private volatile String loadingError;
    private boolean initializationStarted;
    private volatile boolean featureEnabled;
    private volatile boolean systemAvailable;
    private volatile boolean hostVisible;
    private volatile boolean minimized;
    private boolean positioned;
    private boolean runtimeActive;
    private boolean browserFocused;
    private volatile boolean releasedForTable;
    private boolean restoreWindowAfterTable;
    private volatile String requestedCatalogKey;
    private String selectedVideoType = PokerVideoSearch.TYPE_SHORTS;
    private float videoAspect = SHORT_ASPECT;
    private float worldWidth;
    private float worldHeight;
    private float x;
    private float y;
    private float videoWidth = INITIAL_WIDTH;
    private float videoHeight = INITIAL_WIDTH / SHORT_ASPECT;
    private Capture capture = Capture.NONE;
    private int capturedPointer = -1;
    private int capturedButton = -1;
    private float captureStartX;
    private float captureStartY;
    private float startWindowX;
    private float startWindowY;
    private float startWidth;
    private ResizeHandle resizeHandle = ResizeHandle.NONE;
    private int pressedButtons;
    private PokerVideoIcon.Placement iconPlacement =
            PokerVideoIcon.Placement.MENU;

    PokerVideoOverlay() {
        this(PokerVideoFavorites.userStore(), ignored -> { });
    }

    PokerVideoOverlay(Consumer<String> videoTypeChanged) {
        this(PokerVideoFavorites.userStore(), videoTypeChanged);
    }

    PokerVideoOverlay(boolean openOnStartup, Consumer<String> videoTypeChanged) {
        this(PokerVideoFavorites.userStore(), videoTypeChanged, openOnStartup);
    }

    PokerVideoOverlay(PokerVideoFavorites favorites, Consumer<String> videoTypeChanged) {
        this(favorites, videoTypeChanged, true);
    }

    PokerVideoOverlay(PokerVideoFavorites favorites, Consumer<String> videoTypeChanged,
            boolean openOnStartup) {
        catalog = new PokerVideoCatalog(Objects.requireNonNull(favorites));
        this.videoTypeChanged = Objects.requireNonNull(videoTypeChanged);
        this.openOnStartup = openOnStartup;
    }

    void update(boolean available, boolean enabled, boolean visible,
            PokerVideoIcon.Placement placement, String language,
            String videoType,
            float width, float height) {
        boolean firstUpdate = !startupHandled;
        startupHandled = true;
        worldWidth = width;
        worldHeight = height;
        iconPlacement = Objects.requireNonNull(placement, "placement");
        boolean wasAvailable = systemAvailable;
        systemAvailable = available;
        String languageCode = PokerVideoSearch.languageCode(language);
        if (!text.language().equals(languageCode)) text.setLanguage(languageCode);
        if (!available) {
            if (wasAvailable) {
                initializationGeneration.incrementAndGet();
                cancelInitializationTask();
                catalogLoading = false;
                if (runtime == null) initializationStarted = false;
            }
            featureEnabled = false;
            minimized = true;
            hostVisible = false;
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
            if (wasEnabled) {
                initializationGeneration.incrementAndGet();
                cancelInitializationTask();
                catalogLoading = false;
                initializationStarted = false;
                requestedCatalogKey = null;
            }
            minimized = true;
        } else if (!wasEnabled) {
            minimized = firstUpdate && !openOnStartup;
        }
        ensurePosition();
        // A disabled feature is fully absent. "Minimized" is only a runtime
        // window state while the feature remains enabled; it must not turn a
        // disabled preference back into a floating activation icon.
        hostVisible = visible && enabled && !releasedForTable;
        String normalizedType = PokerVideoSearch.videoType(videoType);
        catalog.setType(normalizedType);
        updateVideoAspect(normalizedType);
        String catalogKey = PokerVideoSearch.languageCode(language) + ":"
                + normalizedType;
        if (hostVisible && !minimized && (!initializationStarted
                || !catalogKey.equals(requestedCatalogKey))) {
            start(language, normalizedType, catalogKey);
        }
        PokerVideoRuntime current = runtime;
        if (current != null) {
            if (current.initializationError() != null) loadingError = current.initializationError();
            current.pollDocumentLoad(hostVisible && enabled && !minimized && !releasedForTable && !catalogLoading);
            if (current.documentLoadError() != null) {
                loadingError = current.documentLoadError();
                documentErrorVisible = true;
            } else if (documentErrorVisible) {
                loadingError = null;
                documentErrorVisible = false;
            }
        }
        if (current != null && !catalogLoading
                && (appliedCatalogRevision != catalog.revision()
                || !appliedLanguage.equals(languageCode))) {
            List<PokerVideoSearch.Video> selected = catalog.videos();
            if (!selected.isEmpty()) current.replaceVideos(selected, languageCode);
            appliedCatalogRevision = catalog.revision();
            appliedLanguage = languageCode;
        }
        boolean shouldRun = hostVisible && enabled && !minimized
                && !releasedForTable && !emptyFavorites() && !catalogLoading && loadingError == null;
        if (current != null && !enabled && current.browser() != null) {
            releaseBrowserSession(false);
        }
        if (current != null && shouldRun && current.browser() == null) {
            current.resumeBrowserSession(Math.round(videoWidth),
                    Math.round(videoHeight), displayScale());
            runtimeActive = true;
        }
        if (current != null && shouldRun != runtimeActive) {
            runtimeActive = shouldRun;
            current.setActive(shouldRun);
        }
        if (current != null && shouldRun) {
            current.resize(Math.round(videoWidth), Math.round(videoHeight),
                    displayScale());
        }
        if (!hostVisible) {
            cancelHiddenCapture();
            browserFocused = false;
        }
        updateBounds();
    }

    private void updateVideoAspect(String videoType) {
        if (videoType.equals(selectedVideoType)) return;
        float top = y + videoHeight + CHROME_HEIGHT;
        selectedVideoType = videoType;
        videoAspect = PokerVideoSearch.TYPE_NORMAL.equals(videoType)
                ? NORMAL_ASPECT : SHORT_ASPECT;
        videoHeight = videoWidth / videoAspect;
        if (positioned) {
            y = top - videoHeight - CHROME_HEIGHT;
            clampWindow();
        }
    }

    private void start(String language, String videoType,
            String catalogKey) {
        initializationStarted = true;
        catalogLoading = true;
        loadingError = null;
        requestedCatalogKey = catalogKey;
        long generation = initializationGeneration.incrementAndGet();
        synchronized (initializationLock) {
        initializationTask = initializer.submit(() -> {
            try {
                if (closed.get() || generation != initializationGeneration.get()) return;
                loadingState = "BUSCANDO VÍDEOS";
                List<PokerVideoSearch.Video> searched = catalog.favoritesMode()
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
                loadingState = "INICIALIZANDO REPRODUCTOR";
                PokerVideoRuntime current = runtime;
                if (current != null) {
                    loadingError = null;
                    return;
                }
                long revision = catalog.revision();
                synchronized (initializationLock) {
                    if (closed.get() || generation != initializationGeneration.get()) return;
                    startingNative = true;
                }
                PokerVideoRuntime created = PokerVideoRuntime.initialize(videos,
                        frameBuffer, dataDirectory(), language,
                        Math.round(videoWidth), Math.round(videoHeight),
                        displayScale());
                if (closed.get()) {
                    created.close();
                    return;
                }
                // Once CEF has started it must remain owned until app exit:
                // disposing it after a canceled load makes later reopening
                // impossible. A canceled/minimized load keeps only idle CEF.
                runtime = created;
                appliedCatalogRevision = revision;
                appliedLanguage = PokerVideoSearch.languageCode(language);
                loadingError = created.initializationError();
                runtimeActive = true;
                if (!systemAvailable || !featureEnabled || minimized
                        || releasedForTable
                        || generation != initializationGeneration.get()) {
                    runtimeActive = false;
                    created.releaseBrowserSession(false);
                } else if (!hostVisible) {
                    runtimeActive = false;
                    created.setActive(false);
                }
                created.resize(Math.round(videoWidth),
                        Math.round(videoHeight), displayScale());
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
                synchronized (initializationLock) { startingNative = false; }
                if (generation == initializationGeneration.get()) catalogLoading = false;
            }
        });
        }
    }

    private static Path dataDirectory() {
        String os = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                return Path.of(localAppData, "CoronaPoker", "jcef");
            }
        }
        if (os.contains("mac")) {
            return Path.of(System.getProperty("user.home"), "Library",
                    "Caches", "CoronaPoker", "jcef");
        }
        String xdg = System.getenv("XDG_CACHE_HOME");
        return xdg == null || xdg.isBlank()
                ? Path.of(System.getProperty("user.home"), ".cache",
                        "coronapoker", "jcef")
                : Path.of(xdg, "coronapoker", "jcef");
    }

    private void ensurePosition() {
        if (positioned) {
            clampWindow();
            return;
        }
        x = Math.max(8f, worldWidth - videoWidth - 24f);
        y = 104f;
        positioned = true;
        clampWindow();
    }

    private void updateBounds() {
        float totalHeight = videoHeight + CHROME_HEIGHT;
        PokerVideoIcon.bounds(worldWidth, iconPlacement, iconBounds);
        panelBounds.set(x, y, videoWidth, totalHeight);
        videoBounds.set(x, y + FOOTER_HEIGHT, videoWidth, videoHeight);
        headerBounds.set(x, videoBounds.y + videoHeight, videoWidth, HEADER_HEIGHT);
        footerBounds.set(x, y, videoWidth, FOOTER_HEIGHT);
        previousBounds.set(x + 8f, headerBounds.y + 7f, 40f, 38f);
        nextBounds.set(x + 53f, headerBounds.y + 7f, 40f, 38f);
        closeBounds.set(x + videoWidth - 48f, headerBounds.y + 7f, 40f, 38f);
        float starWidth = videoWidth < 420f ? 32f : 40f;
        float available = videoWidth - 26f - starWidth;
        float modeWidth = Math.min(catalog.favoritesMode() ? 174f : 156f, available * 0.55f);
        float typeWidth = Math.min(124f, available - modeWidth);
        float left = x + 8f;
        favoriteBounds.set(left, y + 5f, starWidth, 38f);
        favoritesModeBounds.set(left + starWidth + 5f, y + 5f, modeWidth, 38f);
        videoTypeBounds.set(x + videoWidth - 8f - typeWidth, y + 5f, typeWidth, 38f);
        retryBounds.set(videoBounds.x + (videoWidth - 128f) / 2f,
                videoBounds.y + videoBounds.height / 2f - 90f, 128f, 38f);
    }

    void prepareFrame() {
        if (hostVisible && !minimized) frameBuffer.uploadPendingFrame();
    }

    void drawShapes(ShapeRenderer shapes, Vector2 pointer) {
        if (!hostVisible) return;
        if (minimized) {
            PokerVideoIcon.draw(shapes, iconBounds, pointer,
                    capture == Capture.ICON, 1f);
            return;
        }
        shapes.setColor(Color.BLACK);
        shapes.rect(videoBounds.x, videoBounds.y, videoBounds.width, videoBounds.height);
        shapes.setColor(HEADER);
        GdxAntialiasedRoundedRect.fill(shapes, headerBounds.x,
                headerBounds.y, headerBounds.width,
                headerBounds.height, 15f);
        shapes.rect(headerBounds.x, headerBounds.y,
                headerBounds.width, headerBounds.height / 2f);
        GdxAntialiasedRoundedRect.fill(shapes, footerBounds.x, footerBounds.y,
                footerBounds.width, footerBounds.height, 15f);
        shapes.rect(footerBounds.x, footerBounds.y + footerBounds.height / 2f,
                footerBounds.width, footerBounds.height / 2f);
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
        drawButton(shapes, pointer, videoTypeBounds,
                GdxUiButtonStyle.Tone.NEUTRAL, Capture.VIDEO_TYPE, false);
        drawButton(shapes, pointer, closeBounds,
                GdxUiButtonStyle.Tone.DANGER, Capture.CLOSE, false);
    }

    void drawForegroundShapes(ShapeRenderer shapes, Vector2 pointer) {
        // Resize hit areas are intentionally invisible. The system cursor is
        // the only affordance, keeping the player free of frames and grips.
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
        } else if (action == Capture.CLOSE) {
            GdxUiIconRenderer.draw(shapes, GdxUiIconRenderer.Icon.CLOSE,
                    bounds.x + bounds.width / 2f,
                    bounds.y + bounds.height / 2f,
                    Math.min(bounds.width, bounds.height) * 0.44f,
                    iconColor, 1f);
        } else if (action == Capture.FAVORITE) {
            drawStar(shapes, bounds.x + bounds.width / 2f,
                    bounds.y + bounds.height / 2f, 10f,
                    currentIsFavorite(), iconColor);
        } else if (action == Capture.FAVORITES_MODE) {
            if (bounds.width < 132f) return;
            float cx = bounds.x + (bounds.width > 40f ? 20f : bounds.width / 2f);
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
            Viewport viewport) {
        if (!hostVisible || minimized) return;
        Texture texture = frameBuffer.texture();
        if (texture != null && !emptyFavorites() && !catalogLoading && loadingError == null) {
            batch.setColor(Color.WHITE);
            batch.draw(texture, videoBounds.x, videoBounds.y,
                    videoBounds.width, videoBounds.height);
        } else {
            tinyFont.setColor(loadingError == null || emptyFavorites() ? MUTED : Color.SALMON);
            String message = emptyFavorites() ? text.translate("gdx.video.favorites.empty")
                    : loadingError == null ? loadingState
                    : "REPRODUCTOR NO DISPONIBLE\n" + loadingError;
            if (loadingError == null && !emptyFavorites()) {
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
        PokerVideoRuntime current = runtime;
        String title = emptyFavorites() ? text.translate("gdx.video.favorites.label")
                : current == null ? "VÍDEOS DE PÓKER"
                : current.title();
        float titleWidth = closeBounds.x - nextBounds.x - nextBounds.width
                - 20f;
        font.setColor(GOLD);
        GdxPixelText.draw(font, batch, ellipsize(font, title, titleWidth),
                nextBounds.x + nextBounds.width + 12f,
                headerBounds.y + 33f, viewport);
        drawButtonLabel(batch, tinyFont, viewport, favoritesModeBounds,
                    text.translate(catalog.favoritesMode()
                            ? "gdx.video.normal.label" : "gdx.video.favorites.label"));
        drawButtonLabel(batch, tinyFont, viewport, videoTypeBounds,
                    text.translate(PokerVideoSearch.TYPE_SHORTS.equals(selectedVideoType)
                            ? "gdx.settings.choice.tipo_videos_poker.normal"
                            : "gdx.settings.choice.tipo_videos_poker.shorts"));
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
        PokerVideoRuntime current = runtime;
        return current != null && !emptyFavorites() && catalog.isFavorite(current.video());
    }

    private boolean actionEnabled(Capture action) {
        if (action == Capture.CLOSE) return true;
        if (action == Capture.RETRY) return canRetry();
        if (catalogLoading) return false;
        if (action == Capture.FAVORITES_MODE || action == Capture.VIDEO_TYPE) return true;
        if (loadingError != null) return false;
        PokerVideoRuntime current = runtime;
        return current != null && current.ready() && !emptyFavorites();
    }

    void addTooltips(BiConsumer<Rectangle, String> add) {
        if (!hostVisible || minimized) return;
        add.accept(favoriteBounds, favoriteSaveFailed ? "gdx.video.favorite.error"
                : currentIsFavorite() ? "gdx.video.favorite.remove" : "gdx.video.favorite.add");
        add.accept(favoritesModeBounds, catalog.favoritesMode()
                ? "gdx.video.normal.tooltip" : "gdx.video.favorites.tooltip");
        add.accept(videoTypeBounds, PokerVideoSearch.TYPE_SHORTS.equals(selectedVideoType)
                ? "gdx.video.type.normal" : "gdx.video.type.shorts");
    }

    private void toggleFavorite() {
        if (!actionEnabled(Capture.FAVORITE)) return;
        try {
            catalog.toggleFavorite(runtime.video());
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

    private Texture loadingSpade() {
        if (loadingSpade == null) {
            loadingSpade = new Texture(Gdx.files.internal(
                    "images/menu/pica_roja.png"));
            loadingSpade.setFilter(Texture.TextureFilter.Linear,
                    Texture.TextureFilter.Linear);
        }
        return loadingSpade;
    }

    boolean touchDown(float pointerX, float pointerY, int pointerIndex,
            int button) {
        if (!hostVisible || capture != Capture.NONE) return false;
        updateBounds();
        if (minimized) {
            if (button == Input.Buttons.LEFT
                    && iconBounds.contains(pointerX, pointerY)) {
                capture(Capture.ICON, pointerIndex, button, pointerX, pointerY);
                return true;
            }
            return false;
        }
        if (!panelBounds.contains(pointerX, pointerY)) {
            blurBrowser();
            return false;
        }
        if (button == Input.Buttons.LEFT) {
            ResizeHandle hit = resizeHandleAt(panelBounds, pointerX, pointerY);
            if (hit != ResizeHandle.NONE) {
                capture(Capture.RESIZE, pointerIndex, button,
                        pointerX, pointerY);
                resizeHandle = hit;
                startWindowX = x;
                startWindowY = y;
                startWidth = videoWidth;
                return true;
            }
            if (closeBounds.contains(pointerX, pointerY)) {
                capture(Capture.CLOSE, pointerIndex, button,
                        pointerX, pointerY);
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
            if (favoriteBounds.contains(pointerX, pointerY)) {
                capture(Capture.FAVORITE, pointerIndex, button, pointerX, pointerY);
                return true;
            }
            if (favoritesModeBounds.contains(pointerX, pointerY)) {
                capture(Capture.FAVORITES_MODE, pointerIndex, button, pointerX, pointerY);
                return true;
            }
            if (videoTypeBounds.contains(pointerX, pointerY)) {
                capture(Capture.VIDEO_TYPE, pointerIndex, button, pointerX, pointerY);
                return true;
            }
            Rectangle draggable = new Rectangle(headerBounds);
            draggable.x = nextBounds.x + nextBounds.width + 4f;
            draggable.width = closeBounds.x - draggable.x - 4f;
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
        return true;
    }

    boolean touchDragged(float pointerX, float pointerY, int pointerIndex) {
        if (capture == Capture.NONE || capturedPointer != pointerIndex) {
            return false;
        }
        switch (capture) {
            case MOVE -> {
                x = startWindowX + pointerX - captureStartX;
                y = startWindowY + pointerY - captureStartY;
                clampWindow();
                updateBounds();
            }
            case RESIZE -> resizeFrom(pointerX, pointerY);
            case VIDEO -> dispatchMouse(MouseEvent.MOUSE_DRAGGED,
                    pointerX, pointerY, MouseEvent.NOBUTTON, 0);
            default -> { }
        }
        return true;
    }

    boolean touchUp(float pointerX, float pointerY, int pointerIndex,
            int button) {
        if (capture == Capture.NONE || capturedPointer != pointerIndex
                || capturedButton != button) return false;
        Capture completed = capture;
        capture = Capture.NONE;
        capturedPointer = -1;
        capturedButton = -1;
        switch (completed) {
            case ICON -> {
                if (iconBounds.contains(pointerX, pointerY)) {
                    minimized = false;
                    clampWindow();
                    if (canRetry()) retryInitialization();
                    else restoreBrowserSession();
                }
            }
            case CLOSE -> {
                if (closeBounds.contains(pointerX, pointerY)) {
                    minimized = true;
                    cancelPendingInitialization();
                    blurBrowser();
                    releaseBrowserSession(false);
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
            case VIDEO_TYPE -> {
                if (videoTypeBounds.contains(pointerX, pointerY) && actionEnabled(Capture.VIDEO_TYPE)) {
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
        if (capture == Capture.NONE || capturedPointer != pointerIndex) {
            return false;
        }
        if (capture == Capture.VIDEO) {
            pressedButtons &= ~(1 << button);
        }
        capture = Capture.NONE;
        resizeHandle = ResizeHandle.NONE;
        capturedPointer = -1;
        capturedButton = -1;
        return true;
    }

    private void cancelHiddenCapture() {
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

    boolean mouseMoved(float pointerX, float pointerY) {
        if (!hostVisible || minimized) return false;
        updateBounds();
        if (resizeHandleAt(panelBounds, pointerX, pointerY)
                != ResizeHandle.NONE) return true;
        if (!videoBounds.contains(pointerX, pointerY)) return false;
        dispatchMouse(MouseEvent.MOUSE_MOVED, pointerX, pointerY,
                MouseEvent.NOBUTTON, 0);
        return true;
    }

    boolean scrolled(float pointerX, float pointerY, float amountY) {
        if (!hostVisible || minimized || amountY == 0f
                || !videoBounds.contains(pointerX, pointerY)) return false;
        PokerVideoRuntime current = runtime;
        LibGdxCefBrowser browser = current == null ? null : current.browser();
        if (browser != null) {
            browser.dispatchWheel(cefX(pointerX), cefY(pointerY),
                    modifiers(), amountY > 0f ? 1 : -1);
        }
        return true;
    }

    private void resizeFrom(float pointerX, float pointerY) {
        if (resizeHandle == ResizeHandle.NONE) return;
        float deltaX = pointerX - captureStartX;
        float deltaY = pointerY - captureStartY;
        float startHeight = startWidth / videoAspect;
        float horizontal = resizeHandle.left() ? startWidth - deltaX
                : resizeHandle.right() ? startWidth + deltaX : startWidth;
        float vertical = resizeHandle.bottom()
                ? (startHeight - deltaY) * videoAspect
                : resizeHandle.top()
                        ? (startHeight + deltaY) * videoAspect : startWidth;
        float candidate;
        if (resizeHandle.horizontal() && resizeHandle.vertical()) {
            candidate = Math.abs(horizontal - startWidth)
                    >= Math.abs(vertical - startWidth)
                            ? horizontal : vertical;
        } else {
            candidate = resizeHandle.horizontal() ? horizontal : vertical;
        }
        videoWidth = MathUtils.clamp(candidate, MIN_WIDTH,
                maximumVideoWidth());
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
        updateBounds();
        PokerVideoRuntime current = runtime;
        if (current != null) {
            current.resize(Math.round(videoWidth), Math.round(videoHeight),
                    displayScale());
        }
    }

    private void select(int delta) {
        if (!actionEnabled(Capture.NEXT)) return;
        PokerVideoRuntime current = runtime;
        if (current != null && current.ready()) current.selectRelative(delta);
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
        runtimeActive = false;
        PokerVideoRuntime current = runtime;
        if (current != null) {
            current.releaseBrowserSession(preserveAudioForResume);
        }
        frameBuffer.dispose();
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
                initializationTask.cancel(!startingNative);
            }
        }
    }

    private void restoreBrowserSession() {
        PokerVideoRuntime current = runtime;
        if (current == null || releasedForTable || emptyFavorites()) return;
        current.resumeBrowserSession(Math.round(videoWidth),
                Math.round(videoHeight), displayScale());
        runtimeActive = true;
    }

    void releaseForTable() {
        restoreWindowAfterTable = systemAvailable && featureEnabled
                && !minimized;
        releasedForTable = true;
        hostVisible = false;
        blurBrowser();
        releaseBrowserSession(restoreWindowAfterTable);
    }

    void restoreAfterTable() {
        releasedForTable = false;
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
            browser.dispatchMouse(eventId, cefX(pointerX), cefY(pointerY),
                    awtButton, modifiers(), clickCount);
        }
    }

    private int cefX(float pointerX) {
        return MathUtils.clamp(Math.round(pointerX - videoBounds.x), 0,
                Math.max(0, Math.round(videoBounds.width) - 1));
    }

    private int cefY(float pointerY) {
        return MathUtils.clamp(Math.round(videoBounds.y + videoBounds.height
                - 1f - pointerY), 0,
                Math.max(0, Math.round(videoBounds.height) - 1));
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

    private float maximumVideoWidth() {
        return Math.max(MIN_WIDTH, Math.min(worldWidth - 16f,
                (worldHeight - CHROME_HEIGHT - 16f) * videoAspect));
    }

    private void clampWindow() {
        videoWidth = Math.min(videoWidth, maximumVideoWidth());
        videoHeight = videoWidth / videoAspect;
        float totalHeight = videoHeight + CHROME_HEIGHT;
        x = MathUtils.clamp(x, 8f,
                Math.max(8f, worldWidth - videoWidth - 8f));
        y = MathUtils.clamp(y, 8f,
                Math.max(8f, worldHeight - totalHeight - 8f));
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
        if (!hostVisible || minimized) return Cursor.SystemCursor.Arrow;
        ResizeHandle hovered = capture == Capture.RESIZE ? resizeHandle
                : resizeHandleAt(panelBounds, pointerX, pointerY);
        return switch (hovered) {
            case LEFT, RIGHT -> Cursor.SystemCursor.HorizontalResize;
            case TOP, BOTTOM -> Cursor.SystemCursor.VerticalResize;
            case BOTTOM_LEFT, TOP_RIGHT -> Cursor.SystemCursor.NESWResize;
            case TOP_LEFT, BOTTOM_RIGHT -> Cursor.SystemCursor.NWSEResize;
            case NONE -> Cursor.SystemCursor.Arrow;
        };
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        initializer.shutdownNow();
        PokerVideoRuntime current = runtime;
        if (current != null) current.close();
        if (loadingSpade != null) loadingSpade.dispose();
        frameBuffer.dispose();
    }

    private enum Capture {
        NONE, ICON, CLOSE, PREVIOUS, NEXT, FAVORITE, FAVORITES_MODE, VIDEO_TYPE, RETRY, MOVE, RESIZE, VIDEO
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
