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
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.Viewport;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.cef.browser.LibGdxCefBrowser;

/** Floating, draggable video window shown only by the menu and waiting room. */
final class PokerVideoOverlay implements AutoCloseable {

    static final float MIN_WIDTH = 260f;
    static final float INITIAL_WIDTH = MIN_WIDTH;
    static final float MAX_WIDTH = 520f;
    static final float HEADER_HEIGHT = 52f;
    private static final float RESIZE_EDGE_HIT = 11f;
    private static final float RESIZE_CORNER_HIT = 22f;
    private static final float VIDEO_CORNER_RADIUS = 15f;
    private static final float SHORT_ASPECT = 9f / 16f;
    private static final float NORMAL_ASPECT = 16f / 9f;
    private static final String VIDEO_VERTEX_SHADER = "attribute vec4 a_position;\n"
            + "attribute vec4 a_color;\n"
            + "attribute vec2 a_texCoord0;\n"
            + "uniform mat4 u_projTrans;\n"
            + "varying vec4 v_color;\n"
            + "varying vec2 v_texCoords;\n"
            + "void main() {\n"
            + "    v_color = a_color;\n"
            + "    v_color.a = v_color.a * (255.0 / 254.0);\n"
            + "    v_texCoords = a_texCoord0;\n"
            + "    gl_Position = u_projTrans * a_position;\n"
            + "}\n";
    private static final String VIDEO_BOTTOM_CORNERS_FRAGMENT_SHADER =
            "#ifdef GL_ES\n"
            + "precision mediump float;\n"
            + "#endif\n"
            + "varying vec4 v_color;\n"
            + "varying vec2 v_texCoords;\n"
            + "uniform sampler2D u_texture;\n"
            + "uniform vec2 u_size;\n"
            + "uniform float u_bottomCornerRadius;\n"
            + "uniform float u_edgeSoftness;\n"
            + "void main() {\n"
            + "    vec2 p = vec2(v_texCoords.x * u_size.x,\n"
            + "            (1.0 - v_texCoords.y) * u_size.y);\n"
            + "    float edge = -u_bottomCornerRadius;\n"
            + "    if (p.y < u_bottomCornerRadius) {\n"
            + "        if (p.x < u_bottomCornerRadius) {\n"
            + "            edge = length(p - vec2(u_bottomCornerRadius))\n"
            + "                    - u_bottomCornerRadius;\n"
            + "        } else if (p.x > u_size.x - u_bottomCornerRadius) {\n"
            + "            edge = length(p - vec2(u_size.x\n"
            + "                    - u_bottomCornerRadius,\n"
            + "                    u_bottomCornerRadius))\n"
            + "                    - u_bottomCornerRadius;\n"
            + "        }\n"
            + "    }\n"
            + "    float mask = 1.0 - smoothstep(-u_edgeSoftness,\n"
            + "            u_edgeSoftness, edge);\n"
            + "    vec4 pixel = texture2D(u_texture, v_texCoords) * v_color;\n"
            + "    pixel.a *= mask;\n"
            + "    if (pixel.a <= 0.001) discard;\n"
            + "    gl_FragColor = pixel;\n"
            + "}\n";
    private static final Color HEADER = new Color(
            GdxSettingsStyle.PANEL_LIGHT_RGBA);
    private static final Color GOLD = new Color(
            GdxSettingsStyle.GOLD_RGBA);
    private static final Color MUTED = new Color(
            GdxSettingsStyle.MUTED_RGBA);

    private final Consumer<Boolean> featurePreferenceChanged;
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
    private final GlyphLayout glyph = new GlyphLayout();
    private final Rectangle iconBounds = new Rectangle();
    private final Rectangle panelBounds = new Rectangle();
    private final Rectangle videoBounds = new Rectangle();
    private final Rectangle headerBounds = new Rectangle();
    private final Rectangle previousBounds = new Rectangle();
    private final Rectangle nextBounds = new Rectangle();
    private final Rectangle closeBounds = new Rectangle();
    private volatile PokerVideoRuntime runtime;
    private Texture loadingSpade;
    private ShaderProgram bottomCornerShader;
    private boolean bottomCornerShaderUnavailable;
    private volatile String loadingState = "BUSCANDO SHORTS DE PÓKER";
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

    PokerVideoOverlay(Consumer<Boolean> featurePreferenceChanged) {
        this.featurePreferenceChanged = Objects.requireNonNull(
                featurePreferenceChanged, "featurePreferenceChanged");
    }

    void update(boolean available, boolean enabled, boolean visible,
            PokerVideoIcon.Placement placement, String language,
            String videoType,
            float width, float height) {
        worldWidth = width;
        worldHeight = height;
        iconPlacement = Objects.requireNonNull(placement, "placement");
        boolean wasAvailable = systemAvailable;
        systemAvailable = available;
        if (!available) {
            if (wasAvailable) {
                initializationGeneration.incrementAndGet();
                if (runtime == null) initializationStarted = false;
            }
            featureEnabled = false;
            minimized = true;
            hostVisible = false;
            PokerVideoRuntime current = runtime;
            if (current != null && current.browser() != null) {
                releaseBrowserSession();
            }
            cancelHiddenCapture();
            browserFocused = false;
            updateBounds();
            return;
        }
        boolean wasEnabled = featureEnabled;
        featureEnabled = enabled;
        if (!enabled) {
            minimized = true;
        } else if (!wasEnabled) {
            minimized = false;
        }
        ensurePosition();
        hostVisible = visible && !releasedForTable;
        String normalizedType = PokerVideoSearch.videoType(videoType);
        updateVideoAspect(normalizedType);
        String catalogKey = PokerVideoSearch.languageCode(language) + ":"
                + normalizedType;
        if (hostVisible && enabled && (!initializationStarted
                || !catalogKey.equals(requestedCatalogKey))) {
            start(language, normalizedType, catalogKey);
        }
        boolean shouldRun = hostVisible && enabled && !minimized
                && !releasedForTable;
        PokerVideoRuntime current = runtime;
        if (current != null && !enabled && current.browser() != null) {
            releaseBrowserSession();
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
        if (!hostVisible) {
            cancelHiddenCapture();
            browserFocused = false;
        }
        updateBounds();
    }

    private void updateVideoAspect(String videoType) {
        if (videoType.equals(selectedVideoType)) return;
        float top = y + videoHeight + HEADER_HEIGHT;
        selectedVideoType = videoType;
        videoAspect = PokerVideoSearch.TYPE_NORMAL.equals(videoType)
                ? NORMAL_ASPECT : SHORT_ASPECT;
        videoHeight = videoWidth / videoAspect;
        if (positioned) {
            y = top - videoHeight - HEADER_HEIGHT;
            clampWindow();
        }
    }

    private void start(String language, String videoType,
            String catalogKey) {
        initializationStarted = true;
        requestedCatalogKey = catalogKey;
        long generation = initializationGeneration.incrementAndGet();
        initializer.submit(() -> {
            try {
                loadingState = PokerVideoSearch.TYPE_SHORTS.equals(videoType)
                        ? "BUSCANDO SHORTS DE PÓKER"
                        : "BUSCANDO VÍDEOS DE PÓKER";
                var videos = PokerVideoSearch.shuffled(
                        PokerVideoSearch.fetch(language, videoType),
                        ThreadLocalRandom.current());
                if (closed.get() || !systemAvailable
                        || generation != initializationGeneration.get()) {
                    return;
                }
                loadingState = "INICIALIZANDO REPRODUCTOR";
                PokerVideoRuntime current = runtime;
                if (current != null) {
                    current.replaceVideos(videos);
                    loadingError = null;
                    return;
                }
                PokerVideoRuntime created = PokerVideoRuntime.initialize(videos,
                        frameBuffer, dataDirectory(), language,
                        Math.round(videoWidth), Math.round(videoHeight),
                        displayScale());
                if (closed.get() || !systemAvailable
                        || generation != initializationGeneration.get()) {
                    created.close();
                    return;
                }
                runtime = created;
                loadingError = created.initializationError();
                runtimeActive = true;
                if (!featureEnabled || minimized || releasedForTable) {
                    runtimeActive = false;
                    created.releaseBrowserSession();
                } else if (!hostVisible) {
                    runtimeActive = false;
                    created.setActive(false);
                }
                created.resize(Math.round(videoWidth),
                        Math.round(videoHeight), displayScale());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                loadingError = "Inicialización cancelada";
            } catch (Exception failure) {
                loadingError = failure.getMessage() == null
                        ? failure.getClass().getSimpleName()
                        : failure.getMessage();
            }
        });
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
        float totalHeight = videoHeight + HEADER_HEIGHT;
        PokerVideoIcon.bounds(worldWidth, iconPlacement, iconBounds);
        panelBounds.set(x, y, videoWidth, totalHeight);
        videoBounds.set(x, y, videoWidth, videoHeight);
        headerBounds.set(x, y + videoHeight, videoWidth, HEADER_HEIGHT);
        previousBounds.set(x + 8f, y + videoHeight + 7f, 40f, 38f);
        nextBounds.set(x + 53f, y + videoHeight + 7f, 40f, 38f);
        closeBounds.set(x + videoWidth - 48f, y + videoHeight + 7f,
                40f, 38f);
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
        GdxAntialiasedRoundedRect.fill(shapes, videoBounds.x, videoBounds.y,
                videoBounds.width, videoBounds.height, VIDEO_CORNER_RADIUS);
        shapes.rect(videoBounds.x,
                videoBounds.y + VIDEO_CORNER_RADIUS,
                videoBounds.width,
                Math.max(0f, videoBounds.height - VIDEO_CORNER_RADIUS));
        shapes.setColor(HEADER);
        GdxAntialiasedRoundedRect.fill(shapes, headerBounds.x,
                headerBounds.y, headerBounds.width,
                headerBounds.height, 15f);
        shapes.rect(headerBounds.x, headerBounds.y,
                headerBounds.width, headerBounds.height / 2f);
        drawHeaderButton(shapes, pointer, previousBounds,
                GdxUiButtonStyle.Tone.NEUTRAL, Capture.PREVIOUS, false);
        drawHeaderButton(shapes, pointer, nextBounds,
                GdxUiButtonStyle.Tone.NEUTRAL, Capture.NEXT, true);
        drawHeaderButton(shapes, pointer, closeBounds,
                GdxUiButtonStyle.Tone.DANGER, Capture.CLOSE, false);
    }

    void drawForegroundShapes(ShapeRenderer shapes, Vector2 pointer) {
        // Resize hit areas are intentionally invisible. The system cursor is
        // the only affordance, keeping the player free of frames and grips.
    }

    private void drawHeaderButton(ShapeRenderer shapes, Vector2 pointer,
            Rectangle bounds, GdxUiButtonStyle.Tone tone,
            Capture action, boolean pointsRight) {
        boolean hovered = bounds.contains(pointer);
        GdxUiButtonStyle.drawBorderless(shapes, bounds.x, bounds.y,
                bounds.width, bounds.height, tone, true,
                hovered ? 1f : 0f, capture == action, 1f);
        Color iconColor = GdxUiButtonStyle.labelColor(tone, true);
        if (action == Capture.CLOSE) {
            GdxUiIconRenderer.draw(shapes, GdxUiIconRenderer.Icon.CLOSE,
                    bounds.x + bounds.width / 2f,
                    bounds.y + bounds.height / 2f,
                    Math.min(bounds.width, bounds.height) * 0.44f,
                    iconColor, 1f);
        } else {
            drawChevron(shapes, bounds, pointsRight, iconColor);
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
        if (texture != null) {
            batch.setColor(Color.WHITE);
            ShaderProgram previous = batch.getShader();
            ShaderProgram corners = bottomCornerShader();
            if (corners != null) {
                batch.setShader(corners);
                corners.setUniformf("u_size", videoBounds.width,
                        videoBounds.height);
                corners.setUniformf("u_bottomCornerRadius",
                        VIDEO_CORNER_RADIUS);
                corners.setUniformf("u_edgeSoftness", 1.15f);
            }
            batch.draw(texture, videoBounds.x, videoBounds.y,
                    videoBounds.width, videoBounds.height);
            if (corners != null) batch.setShader(previous);
        } else {
            tinyFont.setColor(loadingError == null ? MUTED : Color.SALMON);
            String message = loadingError == null ? loadingState
                    : "REPRODUCTOR NO DISPONIBLE\n" + loadingError;
            if (loadingError == null) {
                Texture spade = loadingSpade();
                float size = 34f;
                batch.setColor(Color.WHITE);
                batch.draw(spade,
                        videoBounds.x + (videoBounds.width - size) / 2f,
                        videoBounds.y + videoBounds.height / 2f + 30f,
                        size, size);
            }
            GdxPixelText.draw(tinyFont, batch, message, videoBounds.x + 30f,
                    videoBounds.y + videoBounds.height / 2f + 2f,
                    videoBounds.width - 60f, Align.center, true, viewport);
        }
        PokerVideoRuntime current = runtime;
        String title = current == null ? "VÍDEOS DE PÓKER"
                : current.title();
        float titleWidth = closeBounds.x - nextBounds.x - nextBounds.width
                - 20f;
        font.setColor(GOLD);
        GdxPixelText.draw(font, batch, ellipsize(font, title, titleWidth),
                nextBounds.x + nextBounds.width + 12f,
                headerBounds.y + 33f, viewport);
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

    private ShaderProgram bottomCornerShader() {
        if (bottomCornerShader != null || bottomCornerShaderUnavailable) {
            return bottomCornerShader;
        }
        ShaderProgram candidate = new ShaderProgram(VIDEO_VERTEX_SHADER,
                VIDEO_BOTTOM_CORNERS_FRAGMENT_SHADER);
        if (!candidate.isCompiled()) {
            candidate.dispose();
            bottomCornerShaderUnavailable = true;
            return null;
        }
        bottomCornerShader = candidate;
        return bottomCornerShader;
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
                    featurePreferenceChanged.accept(true);
                    clampWindow();
                    restoreBrowserSession();
                }
            }
            case CLOSE -> {
                if (closeBounds.contains(pointerX, pointerY)) {
                    minimized = true;
                    featurePreferenceChanged.accept(false);
                    blurBrowser();
                    releaseBrowserSession();
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
        float maximum = Math.min(MAX_WIDTH,
                Math.min(Math.max(MIN_WIDTH, worldWidth - 16f),
                        Math.max(MIN_WIDTH,
                                (worldHeight - HEADER_HEIGHT - 16f)
                                        * videoAspect)));
        videoWidth = MathUtils.clamp(candidate, MIN_WIDTH,
                Math.max(MIN_WIDTH, maximum));
        videoHeight = videoWidth / videoAspect;
        float startRight = startWindowX + startWidth;
        float startTop = startWindowY + startHeight + HEADER_HEIGHT;
        float startCenterX = startWindowX + startWidth / 2f;
        float startCenterY = startWindowY
                + (startHeight + HEADER_HEIGHT) / 2f;
        float totalHeight = videoHeight + HEADER_HEIGHT;
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

    private void releaseBrowserSession() {
        runtimeActive = false;
        PokerVideoRuntime current = runtime;
        if (current != null) current.releaseBrowserSession();
        frameBuffer.dispose();
    }

    private void restoreBrowserSession() {
        PokerVideoRuntime current = runtime;
        if (current == null || releasedForTable) return;
        current.resumeBrowserSession(Math.round(videoWidth),
                Math.round(videoHeight), displayScale());
        runtimeActive = true;
    }

    void releaseForTable() {
        restoreWindowAfterTable = hostVisible && !minimized;
        releasedForTable = true;
        hostVisible = false;
        blurBrowser();
        releaseBrowserSession();
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

    private static double displayScale() {
        if (Gdx.graphics == null) return 1.0;
        int logicalWidth = Math.max(1, Gdx.graphics.getWidth());
        return Math.max(1.0,
                Gdx.graphics.getBackBufferWidth() / (double) logicalWidth);
    }

    private void clampWindow() {
        float totalHeight = videoHeight + HEADER_HEIGHT;
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
        if (bottomCornerShader != null) bottomCornerShader.dispose();
        frameBuffer.dispose();
    }

    private enum Capture {
        NONE, ICON, CLOSE, PREVIOUS, NEXT, MOVE, RESIZE, VIDEO
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
