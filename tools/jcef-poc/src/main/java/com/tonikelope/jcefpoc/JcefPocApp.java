package com.tonikelope.jcefpoc;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Locale;
import org.cef.browser.LibGdxCefBrowser;

/** libGDX presentation and interaction layer for the JCEF experiment. */
public final class JcefPocApp extends ApplicationAdapter {
    private static final Color WHITE = Color.WHITE;
    private final JcefRuntime runtime;
    private final ChromiumFrameBuffer frameBuffer;
    private Stage stage;
    private Texture whitePixel;
    private BitmapFont font;
    private BitmapFont titleFont;
    private FloatingBrowserPanel browserPanel;
    private FlatButton openButton;
    private MetricsActor metricsActor;
    private BackdropActor backdropActor;
    private boolean renderingEnabled = true;
    private long frameMetricStart = System.nanoTime();
    private long frameMetricNanos;
    private int frameMetricCount;
    private volatile double averageFrameMillis;

    public JcefPocApp(JcefRuntime runtime) {
        this.runtime = runtime;
        this.frameBuffer = runtime.frameBuffer();
    }

    @Override
    public void create() {
        Pixmap pixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixel.setColor(WHITE);
        pixel.fill();
        whitePixel = new Texture(pixel);
        pixel.dispose();

        font = new BitmapFont();
        font.getData().markupEnabled = true;
        titleFont = new BitmapFont();
        titleFont.getData().setScale(1.55f);
        titleFont.getData().markupEnabled = true;

        stage = new Stage(new ScreenViewport());
        backdropActor = new BackdropActor();
        backdropActor.setBounds(0, 0, stage.getWidth(), stage.getHeight());
        stage.addActor(backdropActor);

        openButton = new FlatButton("ABRIR REPRODUCTOR", () -> {
            browserPanel.openPanel();
            openButton.setVisible(false);
        });
        openButton.setBounds(24, stage.getHeight() - 58, 190, 36);
        openButton.setVisible(false);
        stage.addActor(openButton);

        browserPanel = new FloatingBrowserPanel(640f);
        browserPanel.setPosition((stage.getWidth() - browserPanel.getWidth()) * 0.5f,
                (stage.getHeight() - browserPanel.getHeight()) * 0.52f);
        stage.addActor(browserPanel);

        metricsActor = new MetricsActor();
        metricsActor.setBounds(16, 16, 470, 180);
        stage.addActor(metricsActor);

        InputAdapter shortcuts = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.F8) {
                    setRenderingEnabled(!renderingEnabled);
                    return true;
                }
                return false;
            }
        };
        Gdx.input.setInputProcessor(new InputMultiplexer(shortcuts, stage));
        runtime.resizeBrowser(640, 360, displayScale());
    }

    @Override
    public void render() {
        long started = System.nanoTime();
        ScreenUtils.clear(0.018f, 0.035f, 0.055f, 1f);
        frameBuffer.uploadPendingFrame();
        stage.act(Math.min(Gdx.graphics.getDeltaTime(), 1f / 15f));
        stage.draw();

        long elapsed = System.nanoTime() - started;
        frameMetricNanos += elapsed;
        frameMetricCount++;
        long now = System.nanoTime();
        if (now - frameMetricStart >= 1_000_000_000L) {
            averageFrameMillis = frameMetricNanos / 1_000_000.0 / frameMetricCount;
            frameMetricStart = now;
            frameMetricNanos = 0L;
            frameMetricCount = 0;
        }
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        backdropActor.setBounds(0, 0, stage.getWidth(), stage.getHeight());
        openButton.setPosition(24, stage.getHeight() - 58);
        browserPanel.clampToStage();
        runtime.resizeBrowser(Math.round(browserPanel.contentWidth()),
                Math.round(browserPanel.contentHeight()), displayScale());
    }

    private double displayScale() {
        int logicalWidth = Math.max(1, Gdx.graphics.getWidth());
        return Math.max(1.0, Gdx.graphics.getBackBufferWidth() / (double) logicalWidth);
    }

    private void setRenderingEnabled(boolean enabled) {
        renderingEnabled = enabled;
        browserPanel.updateRenderButton();
        runtime.setBrowserActive(enabled && browserPanel.isOpenAndRestored());
    }

    @Override
    public void dispose() {
        Gdx.input.setInputProcessor(null);
        runtime.close();
        frameBuffer.dispose();
        stage.dispose();
        font.dispose();
        titleFont.dispose();
        whitePixel.dispose();
    }

    private void fill(Batch batch, float x, float y, float width, float height, Color color,
            float parentAlpha) {
        Color old = batch.getColor();
        batch.setColor(color.r, color.g, color.b, color.a * parentAlpha);
        batch.draw(whitePixel, x, y, width, height);
        batch.setColor(old);
    }

    private final class BackdropActor extends Actor {
        private final Color top = new Color(0.025f, 0.075f, 0.105f, 1f);
        private final Color middle = new Color(0.035f, 0.13f, 0.12f, 1f);
        private final Color panel = new Color(0.035f, 0.055f, 0.075f, 0.92f);

        @Override
        public void draw(Batch batch, float parentAlpha) {
            fill(batch, getX(), getY(), getWidth(), getHeight(), top, parentAlpha);
            fill(batch, getX(), getY(), getWidth(), getHeight() * 0.56f, middle, parentAlpha);
            fill(batch, getX() + 28, getY() + 235, 236, 260, panel, parentAlpha);
            fill(batch, getX() + getWidth() - 270, getY() + 92, 232, 140, panel, parentAlpha);
            titleFont.setColor(0.9f, 0.96f, 0.94f, parentAlpha);
            titleFont.draw(batch, "CORONAPOKER", getX() + 38, getY() + getHeight() - 34);
            font.setColor(0.55f, 0.72f, 0.7f, parentAlpha);
            font.draw(batch, "LABORATORIO JCEF / OSR", getX() + 40,
                    getY() + getHeight() - 64);
            font.setColor(0.55f, 0.64f, 0.68f, parentAlpha);
            font.draw(batch, "PARTIDA PRIVADA", getX() + 52, getY() + 465);
            font.draw(batch, "CREAR TIMBA", getX() + 52, getY() + 420);
            font.draw(batch, "UNIRSE A TIMBA", getX() + 52, getY() + 375);
            font.draw(batch, "AJUSTES", getX() + 52, getY() + 330);
        }
    }

    private final class FlatButton extends Actor {
        private final GlyphLayout layout = new GlyphLayout();
        private final Runnable action;
        private String label;
        private boolean hovered;
        private boolean pressed;

        FlatButton(String label, Runnable action) {
            this.action = action;
            setLabel(label);
            addListener(new InputListener() {
                @Override
                public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                    hovered = true;
                }

                @Override
                public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                    hovered = false;
                    if (pointer == -1) pressed = false;
                }

                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer,
                        int button) {
                    if (button != Input.Buttons.LEFT) return false;
                    pressed = true;
                    return true;
                }

                @Override
                public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                    boolean run = pressed && x >= 0 && x <= getWidth() && y >= 0
                            && y <= getHeight();
                    pressed = false;
                    if (run) action.run();
                }
            });
        }

        void setLabel(String label) {
            this.label = label;
            layout.setText(font, label);
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Color background = pressed ? new Color(0.10f, 0.46f, 0.38f, 1f)
                    : hovered ? new Color(0.10f, 0.34f, 0.31f, 1f)
                              : new Color(0.065f, 0.18f, 0.19f, 1f);
            fill(batch, getX(), getY(), getWidth(), getHeight(), background, parentAlpha);
            font.setColor(0.87f, 0.96f, 0.93f, parentAlpha);
            font.draw(batch, label, getX() + (getWidth() - layout.width) * 0.5f,
                    getY() + (getHeight() + layout.height) * 0.5f);
        }
    }

    private final class HeaderActor extends Actor {
        @Override
        public void draw(Batch batch, float parentAlpha) {
            fill(batch, getX(), getY(), getWidth(), getHeight(),
                    new Color(0.035f, 0.075f, 0.095f, 1f), parentAlpha);
            font.setColor(0.9f, 0.96f, 0.94f, parentAlpha);
            font.draw(batch, "YOUTUBE / JCEF OSR", getX() + 12, getY() + 23);
        }
    }

    private final class BrowserSurfaceActor extends Actor {
        private int pressedButtons;

        BrowserSurfaceActor() {
            setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
            addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer,
                        int button) {
                    if (!renderingEnabled || runtime.browser() == null) return false;
                    getStage().setKeyboardFocus(BrowserSurfaceActor.this);
                    runtime.requestFocus(true);
                    pressedButtons |= 1 << button;
                    runtime.browser().dispatchMouse(MouseEvent.MOUSE_PRESSED, cefX(x), cefY(y),
                            awtButton(button), modifiers(), 1);
                    return true;
                }

                @Override
                public void touchDragged(InputEvent event, float x, float y, int pointer) {
                    LibGdxCefBrowser browser = runtime.browser();
                    if (browser != null) browser.dispatchMouse(MouseEvent.MOUSE_DRAGGED, cefX(x),
                            cefY(y), MouseEvent.NOBUTTON, modifiers(), 0);
                }

                @Override
                public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                    LibGdxCefBrowser browser = runtime.browser();
                    if (browser != null) browser.dispatchMouse(MouseEvent.MOUSE_RELEASED, cefX(x),
                            cefY(y), awtButton(button), modifiers(), 1);
                    pressedButtons &= ~(1 << button);
                }

                @Override
                public boolean mouseMoved(InputEvent event, float x, float y) {
                    LibGdxCefBrowser browser = runtime.browser();
                    if (browser != null && renderingEnabled) {
                        browser.dispatchMouse(MouseEvent.MOUSE_MOVED, cefX(x), cefY(y),
                                MouseEvent.NOBUTTON, modifiers(), 0);
                        return true;
                    }
                    return false;
                }

                @Override
                public boolean scrolled(InputEvent event, float x, float y, float amountX,
                        float amountY) {
                    LibGdxCefBrowser browser = runtime.browser();
                    if (browser == null || !renderingEnabled) return false;
                    int rotation = amountY == 0f ? 0 : Math.max(1, Math.round(Math.abs(amountY)))
                            * (amountY < 0f ? -1 : 1);
                    browser.dispatchWheel(cefX(x), cefY(y), modifiers(), rotation);
                    return true;
                }

                @Override
                public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                    LibGdxCefBrowser browser = runtime.browser();
                    if (browser != null && pointer == -1) {
                        browser.dispatchMouse(MouseEvent.MOUSE_ENTERED, cefX(x), cefY(y),
                                MouseEvent.NOBUTTON, modifiers(), 0);
                    }
                }

                @Override
                public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                    LibGdxCefBrowser browser = runtime.browser();
                    if (browser != null && pointer == -1) {
                        browser.dispatchMouse(MouseEvent.MOUSE_EXITED, cefX(x), cefY(y),
                                MouseEvent.NOBUTTON, modifiers(), 0);
                    }
                }

                @Override
                public boolean keyDown(InputEvent event, int keycode) {
                    int awt = awtKeyCode(keycode);
                    if (awt == KeyEvent.VK_UNDEFINED || runtime.browser() == null) return false;
                    runtime.browser().dispatchKey(KeyEvent.KEY_PRESSED, awt,
                            KeyEvent.CHAR_UNDEFINED, modifiers());
                    return true;
                }

                @Override
                public boolean keyUp(InputEvent event, int keycode) {
                    int awt = awtKeyCode(keycode);
                    if (awt == KeyEvent.VK_UNDEFINED || runtime.browser() == null) return false;
                    runtime.browser().dispatchKey(KeyEvent.KEY_RELEASED, awt,
                            KeyEvent.CHAR_UNDEFINED, modifiers());
                    return true;
                }

                @Override
                public boolean keyTyped(InputEvent event, char character) {
                    if (runtime.browser() == null || Character.isISOControl(character)) return false;
                    runtime.browser().dispatchKey(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED,
                            character, modifiers());
                    return true;
                }
            });
        }

        private int cefX(float x) {
            return MathUtils.clamp(Math.round(x), 0, Math.max(0, Math.round(getWidth()) - 1));
        }

        private int cefY(float y) {
            return MathUtils.clamp(Math.round(getHeight() - 1f - y), 0,
                    Math.max(0, Math.round(getHeight()) - 1));
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

        private boolean keyPressed(int first, int second) {
            return Gdx.input.isKeyPressed(first) || Gdx.input.isKeyPressed(second);
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            fill(batch, getX(), getY(), getWidth(), getHeight(), Color.BLACK, parentAlpha);
            Texture texture = frameBuffer.texture();
            if (renderingEnabled && texture != null) {
                batch.setColor(1f, 1f, 1f, parentAlpha);
                batch.draw(texture, getX(), getY(), getWidth(), getHeight());
                batch.setColor(Color.WHITE);
                return;
            }
            font.setColor(0.76f, 0.84f, 0.84f, parentAlpha);
            String message = runtime.initializationError() != null
                    ? "ERROR JCEF\n" + runtime.initializationError()
                    : renderingEnabled ? "ESPERANDO EL PRIMER FOTOGRAMA DE CHROMIUM…"
                                       : "RENDERIZADO DE CHROMIUM DESACTIVADO (F8)";
            font.draw(batch, message, getX() + 30, getY() + getHeight() * 0.55f,
                    getWidth() - 60, Align.center, true);
        }
    }

    private final class FloatingBrowserPanel extends Group {
        private static final float HEADER_HEIGHT = 34f;
        private static final float MIN_WIDTH = 320f;
        private final HeaderActor header = new HeaderActor();
        private final BrowserSurfaceActor surface = new BrowserSurfaceActor();
        private final FlatButton minimizeButton;
        private final FlatButton closeButton;
        private final FlatButton renderButton;
        private final Actor resizeHandle = new Actor() {
            @Override
            public void draw(Batch batch, float parentAlpha) {
                fill(batch, getX(), getY(), getWidth(), getHeight(),
                        new Color(0.12f, 0.44f, 0.39f, 1f), parentAlpha);
            }
        };
        private boolean minimized;
        private float fullWidth;
        private float fullContentHeight;

        FloatingBrowserPanel(float initialWidth) {
            minimizeButton = new FlatButton("_", this::toggleMinimize);
            closeButton = new FlatButton("X", this::closePanel);
            renderButton = new FlatButton("CEF: ON", () ->
                    setRenderingEnabled(!renderingEnabled));
            addActor(surface);
            addActor(header);
            addActor(renderButton);
            addActor(minimizeButton);
            addActor(closeButton);
            addActor(resizeHandle);
            setFullWidth(initialWidth);
            installDragBehavior();
            installResizeBehavior();
        }

        private void installDragBehavior() {
            header.addListener(new InputListener() {
                private float startStageX;
                private float startStageY;
                private float startX;
                private float startY;

                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer,
                        int button) {
                    if (button != Input.Buttons.LEFT) return false;
                    startStageX = event.getStageX();
                    startStageY = event.getStageY();
                    startX = getX();
                    startY = getY();
                    toFront();
                    return true;
                }

                @Override
                public void touchDragged(InputEvent event, float x, float y, int pointer) {
                    setPosition(startX + event.getStageX() - startStageX,
                            startY + event.getStageY() - startStageY);
                    clampToStage();
                }
            });
        }

        private void installResizeBehavior() {
            resizeHandle.addListener(new InputListener() {
                private float startStageX;
                private float startStageY;
                private float startWidth;
                private float fixedTop;

                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer,
                        int button) {
                    if (button != Input.Buttons.LEFT || minimized) return false;
                    startStageX = event.getStageX();
                    startStageY = event.getStageY();
                    startWidth = fullWidth;
                    fixedTop = getY() + getHeight();
                    return true;
                }

                @Override
                public void touchDragged(InputEvent event, float x, float y, int pointer) {
                    float horizontal = startWidth + event.getStageX() - startStageX;
                    float verticalHeight = fullContentHeight + startStageY - event.getStageY();
                    float fromVertical = verticalHeight * 16f / 9f;
                    float maxWidth = Math.min(1100f, getStage().getWidth() - getX() - 8f);
                    float candidate = MathUtils.clamp(Math.max(horizontal, fromVertical),
                            MIN_WIDTH, Math.max(MIN_WIDTH, maxWidth));
                    setFullWidth(candidate);
                    setY(Math.max(0f, fixedTop - getHeight()));
                    runtime.resizeBrowser(Math.round(fullWidth), Math.round(fullContentHeight),
                            displayScale());
                }
            });
        }

        private void setFullWidth(float width) {
            fullWidth = Math.max(MIN_WIDTH, width);
            fullContentHeight = fullWidth * 9f / 16f;
            if (!minimized) setSize(fullWidth, fullContentHeight + HEADER_HEIGHT);
            layoutChildren();
        }

        private void layoutChildren() {
            float currentContentHeight = minimized ? 0f : fullContentHeight;
            header.setBounds(0, currentContentHeight, fullWidth, HEADER_HEIGHT);
            surface.setBounds(0, 0, fullWidth, fullContentHeight);
            renderButton.setBounds(fullWidth - 220, currentContentHeight + 4, 112, 26);
            minimizeButton.setBounds(fullWidth - 102, currentContentHeight + 4, 44, 26);
            closeButton.setBounds(fullWidth - 52, currentContentHeight + 4, 44, 26);
            resizeHandle.setBounds(fullWidth - 18, 0, 18, 18);
            surface.setVisible(!minimized);
            resizeHandle.setVisible(!minimized);
        }

        private void toggleMinimize() {
            float fixedTop = getY() + getHeight();
            minimized = !minimized;
            setSize(fullWidth, minimized ? HEADER_HEIGHT : fullContentHeight + HEADER_HEIGHT);
            setY(Math.max(0f, fixedTop - getHeight()));
            minimizeButton.setLabel(minimized ? "□" : "_");
            layoutChildren();
            runtime.setBrowserActive(renderingEnabled && !minimized && isVisible());
        }

        private void closePanel() {
            setVisible(false);
            runtime.setBrowserActive(false);
            openButton.setVisible(true);
        }

        private void openPanel() {
            setVisible(true);
            if (minimized) toggleMinimize();
            toFront();
            metricsActor.toFront();
            runtime.resizeBrowser(Math.round(fullWidth), Math.round(fullContentHeight),
                    displayScale());
            runtime.setBrowserActive(renderingEnabled);
            clampToStage();
        }

        private boolean isOpenAndRestored() {
            return isVisible() && !minimized;
        }

        private void updateRenderButton() {
            renderButton.setLabel(renderingEnabled ? "CEF: ON" : "CEF: OFF");
        }

        private float contentWidth() {
            return fullWidth;
        }

        private float contentHeight() {
            return fullContentHeight;
        }

        private void clampToStage() {
            if (getStage() == null) return;
            float maxX = Math.max(0f, getStage().getWidth() - getWidth());
            float maxY = Math.max(0f, getStage().getHeight() - getHeight());
            setPosition(MathUtils.clamp(getX(), 0f, maxX), MathUtils.clamp(getY(), 0f, maxY));
        }
    }

    private final class MetricsActor extends Actor {
        private final StringBuilder text = new StringBuilder(512);
        private float refreshAccumulator;
        private String displayed = "";

        @Override
        public void act(float delta) {
            refreshAccumulator += delta;
            if (refreshAccumulator < 0.25f) return;
            refreshAccumulator = 0f;
            long heap = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            text.setLength(0);
            text.append("[LIGHT_GRAY]FPS libGDX:[WHITE] ")
                    .append(Gdx.graphics.getFramesPerSecond())
                    .append("   [LIGHT_GRAY]Frame:[WHITE] ")
                    .append(String.format(Locale.ROOT, "%.2f ms", averageFrameMillis))
                    .append("\n[LIGHT_GRAY]Heap Java:[WHITE] ")
                    .append(String.format(Locale.ROOT, "%.1f MiB", heap / 1048576.0))
                    .append("   [LIGHT_GRAY]Textura CEF:[WHITE] ")
                    .append(frameBuffer.width()).append('x').append(frameBuffer.height())
                    .append("\n[LIGHT_GRAY]Uploads textura/s:[WHITE] ")
                    .append(String.format(Locale.ROOT, "%.1f", frameBuffer.uploadsPerSecond()))
                    .append("   [LIGHT_GRAY]Upload medio:[WHITE] ")
                    .append(String.format(Locale.ROOT, "%.3f ms",
                            frameBuffer.averageUploadMillis()))
                    .append("\n[LIGHT_GRAY]Estado:[WHITE] ").append(runtime.playerState())
                    .append("   [LIGHT_GRAY]Render:[WHITE] ")
                    .append(renderingEnabled ? "ON" : "OFF")
                    .append("\n[LIGHT_GRAY]Versión:[WHITE] ").append(runtime.versionText())
                    .append("\n[GRAY]Heap/tiempos no incluyen toda la memoria ni GPU de los procesos CEF.")
                    .append("  F8: comparar ON/OFF");
            displayed = text.toString();
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            fill(batch, getX(), getY(), getWidth(), getHeight(),
                    new Color(0.018f, 0.028f, 0.04f, 0.88f), parentAlpha);
            font.setColor(Color.WHITE);
            font.draw(batch, displayed, getX() + 12, getY() + getHeight() - 12,
                    getWidth() - 24, Align.left, true);
        }
    }

    private static int awtButton(int gdxButton) {
        if (gdxButton == Input.Buttons.LEFT) return MouseEvent.BUTTON1;
        if (gdxButton == Input.Buttons.MIDDLE) return MouseEvent.BUTTON2;
        if (gdxButton == Input.Buttons.RIGHT) return MouseEvent.BUTTON3;
        return MouseEvent.NOBUTTON;
    }

    private static int awtKeyCode(int keycode) {
        if (keycode >= Input.Keys.A && keycode <= Input.Keys.Z) {
            return KeyEvent.VK_A + keycode - Input.Keys.A;
        }
        if (keycode >= Input.Keys.NUM_0 && keycode <= Input.Keys.NUM_9) {
            return KeyEvent.VK_0 + keycode - Input.Keys.NUM_0;
        }
        return switch (keycode) {
            case Input.Keys.SPACE -> KeyEvent.VK_SPACE;
            case Input.Keys.ENTER -> KeyEvent.VK_ENTER;
            case Input.Keys.TAB -> KeyEvent.VK_TAB;
            case Input.Keys.ESCAPE -> KeyEvent.VK_ESCAPE;
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
            case Input.Keys.F1 -> KeyEvent.VK_F1;
            case Input.Keys.F2 -> KeyEvent.VK_F2;
            case Input.Keys.F3 -> KeyEvent.VK_F3;
            case Input.Keys.F4 -> KeyEvent.VK_F4;
            case Input.Keys.F5 -> KeyEvent.VK_F5;
            case Input.Keys.F6 -> KeyEvent.VK_F6;
            case Input.Keys.F7 -> KeyEvent.VK_F7;
            case Input.Keys.F8 -> KeyEvent.VK_F8;
            case Input.Keys.F9 -> KeyEvent.VK_F9;
            case Input.Keys.F10 -> KeyEvent.VK_F10;
            case Input.Keys.F11 -> KeyEvent.VK_F11;
            case Input.Keys.F12 -> KeyEvent.VK_F12;
            default -> KeyEvent.VK_UNDEFINED;
        };
    }
}
