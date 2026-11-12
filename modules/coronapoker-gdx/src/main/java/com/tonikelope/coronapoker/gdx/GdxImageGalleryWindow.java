/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.Viewport;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Properties;
import java.util.function.BiPredicate;

/** The same non-modal Chromium gallery and window behavior in the lobby and at the table. */
final class GdxImageGalleryWindow implements AutoCloseable {

    private static final float DEFAULT_WIDTH = 1280f;
    private static final float DEFAULT_HEIGHT = 760f;
    private static final Color ACCENT = new Color(0x16839aff);
    private static final Color PANEL = new Color(0x0b1522ff);
    private static final Color CLOSE_LINE = new Color(0xf45b56ff);
    private static final Color CLOSE_FILL = new Color(0x692a32ff);
    private static final Color CLOSE_HOVER = new Color(0x8f3540ff);
    private static final Color GRIP = new Color(0x3e6079ff);

    private final GdxPointerHold pointer = new GdxPointerHold();
    private final GlyphLayout title = new GlyphLayout();
    private Rectangle panel;
    private Rectangle start;
    private float startX;
    private float startY;
    private Gesture gesture = Gesture.NONE;
    private GdxFloatingGameLogLayout.ResizeHandle resize =
            GdxFloatingGameLogLayout.ResizeHandle.NONE;
    private boolean keyboardFocused;
    private boolean webHovered;
    private PokerImageGalleryBrowser browser;
    private Viewport viewport;
    private int renderWidth;
    private int renderHeight;

    boolean open(Viewport viewport, GdxGameText text, Properties properties,
            Runnable persist, BiPredicate<String, Boolean> sendToChat) {
        if (isOpen()) return true;
        if (!PokerChromium.available()) return false;
        this.viewport = viewport;
        panel = panel == null ? initial(viewport.getWorldWidth(), viewport.getWorldHeight())
                : GdxFloatingGameLogLayout.normalize(panel,
                        viewport.getWorldWidth(), viewport.getWorldHeight());
        Rectangle web = webBounds(panel);
        renderWidth = browserWidth(web);
        renderHeight = browserHeight(web);
        browser = new PokerImageGalleryBrowser("en".equalsIgnoreCase(text.language()),
                renderWidth, renderHeight,
                GdxChatImageHistory.read(properties), GdxChatImageHistory.autoReceive(properties),
                url -> {
                    GdxChatImageHistory.remember(properties, url, true);
                    persist.run();
                    sync(properties);
                },
                sendToChat,
                enabled -> {
                    GdxChatImageHistory.setAutoReceive(properties, enabled);
                    persist.run();
                    sync(properties);
                },
                () -> {
                    GdxChatImageHistory.clear(properties);
                    persist.run();
                    sync(properties);
                },
                url -> {
                    GdxChatImageHistory.remove(properties, url);
                    persist.run();
                    sync(properties);
                },
                GdxImageSearchHistory.read(properties),
                search -> {
                    GdxImageSearchHistory.write(properties, search);
                    persist.run();
                }, this::close);
        return true;
    }

    void sync(Properties properties) {
        if (isOpen()) browser.setGallery(GdxChatImageHistory.read(properties),
                GdxChatImageHistory.autoReceive(properties));
    }

    static Rectangle initial(float width, float height) {
        return GdxFloatingGameLogLayout.normalize(new Rectangle(
                (width - DEFAULT_WIDTH) / 2f,
                Math.max(16f, (height - DEFAULT_HEIGHT) / 2f + 108f),
                DEFAULT_WIDTH, DEFAULT_HEIGHT), width, height);
    }

    static Rectangle webBounds(Rectangle panel) {
        return new Rectangle(panel.x + 16f, panel.y + 16f,
                panel.width - 32f, panel.height - 32f);
    }

    // CSS uses the same title bar height and reserves one square for the close button.
    static Rectangle titleDragBounds(Rectangle web, int browserWidth, int browserHeight) {
        float size = Math.max(32f, Math.min(60f, browserWidth * 0.045f));
        float height = web.height * size / browserHeight;
        float closeWidth = web.width * size / browserWidth;
        return new Rectangle(web.x, web.y + web.height - height, web.width - closeWidth, height);
    }

    private Rectangle titleDragBounds() {
        return titleDragBounds(webBounds(panel), renderWidth, renderHeight);
    }

    private Rectangle fallbackCloseBounds() {
        Rectangle web = webBounds(panel);
        Rectangle drag = titleDragBounds(web, renderWidth, renderHeight);
        float left = drag.x + drag.width;
        return new Rectangle(left + 4f, drag.y + 4f, web.x + web.width - left - 8f, drag.height - 8f);
    }

    private int browserWidth(Rectangle web) {
        return Math.max(1, Math.round(web.width * viewport.getScreenWidth() / viewport.getWorldWidth()));
    }

    private int browserHeight(Rectangle web) {
        return Math.max(1, Math.round(web.height * viewport.getScreenHeight() / viewport.getWorldHeight()));
    }

    void draw(ShapeRenderer shapes, SpriteBatch batch, BitmapFont statusFont,
            GdxGameText text, float x, float y) {
        if (!isOpen()) return;
        Rectangle web = webBounds(panel);
        if (gesture != Gesture.RESIZE) {
            renderWidth = browserWidth(web);
            renderHeight = browserHeight(web);
        }
        Texture frame = browser.failed() ? null : browser.texture(renderWidth, renderHeight);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        GdxUiDialogStyle.drawPanel(shapes, panel.x, panel.y, panel.width, panel.height, PANEL, ACCENT, 1f);
        if (frame == null) drawFallbackClose(shapes, x, y);
        shapes.setColor(GRIP);
        float right = panel.x + panel.width;
        shapes.rectLine(right - 17f, panel.y + 7f, right - 7f, panel.y + 17f, 2f);
        shapes.rectLine(right - 12f, panel.y + 7f, right - 7f, panel.y + 12f, 2f);
        shapes.end();

        batch.begin();
        batch.setColor(Color.WHITE);
        if (frame != null) {
            float px = viewport.getScreenWidth() / viewport.getWorldWidth()
                    * Gdx.graphics.getBackBufferWidth() / Gdx.graphics.getWidth();
            float py = viewport.getScreenHeight() / viewport.getWorldHeight()
                    * Gdx.graphics.getBackBufferHeight() / Gdx.graphics.getHeight();
            if (px > 0f && py > 0f) {
                batch.draw(frame, Math.round(web.x * px) / px, Math.round(web.y * py) / py,
                        Math.round(web.width * px) / px, Math.round(web.height * py) / py);
            }
        } else {
            drawLabel(batch, statusFont, text.translate(browser.failed()
                    ? "gdx.lobby.image_search_unavailable" : "gdx.lobby.media_loading"),
                    web, GdxUiDialogStyle.PREPARATION_STATUS, Align.center);
        }
        batch.end();
    }

    private void drawFallbackClose(ShapeRenderer shapes, float x, float y) {
        Rectangle close = fallbackCloseBounds();
        shapes.setColor(CLOSE_LINE);
        GdxAntialiasedRoundedRect.fill(shapes, close.x, close.y, close.width, close.height, 10f);
        shapes.setColor(close.contains(x, y) ? CLOSE_HOVER : CLOSE_FILL);
        GdxAntialiasedRoundedRect.fill(shapes, close.x + 2f, close.y + 2f,
                close.width - 4f, close.height - 4f, 8f);
        shapes.setColor(Color.WHITE);
        float centerX = close.x + close.width / 2f;
        float centerY = close.y + close.height / 2f;
        shapes.rectLine(centerX - 6f, centerY - 6f, centerX + 6f, centerY + 6f, 2.6f);
        shapes.rectLine(centerX - 6f, centerY + 6f, centerX + 6f, centerY - 6f, 2.6f);
    }

    private void drawLabel(SpriteBatch batch, BitmapFont font, String value,
            Rectangle bounds, Color color, int align) {
        title.setText(font, value, 0, value.length(), color, bounds.width, align, false, "…");
        GdxPixelText.draw(font, batch, title, bounds.x,
                bounds.y + (bounds.height + title.height) / 2f, viewport);
    }

    boolean isOpen() {
        return browser != null;
    }

    boolean contains(float x, float y) {
        return isOpen() && panel.contains(x, y);
    }

    boolean focused() {
        return isOpen() && keyboardFocused;
    }

    boolean touchDown(float x, float y, int index, int button) {
        if (!contains(x, y)) {
            unfocus();
            return false;
        }
        if (button != Input.Buttons.LEFT || !pointer.capture(index, button)) return true;
        resize = GdxFloatingGameLogLayout.resizeHandleAt(panel, x, y);
        if ((!browser.interactive() || browser.failed()) && fallbackCloseBounds().contains(x, y)) {
            gesture = Gesture.CLOSE;
        } else if (resize != GdxFloatingGameLogLayout.ResizeHandle.NONE) {
            beginMove(Gesture.RESIZE, x, y);
        } else if (titleDragBounds().contains(x, y)) {
            beginMove(Gesture.MOVE, x, y);
        } else if (browser.interactive() && webBounds(panel).contains(x, y)) {
            gesture = Gesture.WEB;
            keyboardFocused = true;
            mouse(MouseEvent.MOUSE_PRESSED, x, y);
        }
        return true;
    }

    private void beginMove(Gesture kind, float x, float y) {
        gesture = kind;
        start = new Rectangle(panel);
        startX = x;
        startY = y;
    }

    boolean touchDragged(float x, float y, int index) {
        if (!pointer.owns(index, Input.Buttons.LEFT)) return false;
        if (gesture == Gesture.WEB) {
            mouse(MouseEvent.MOUSE_DRAGGED, x, y);
        } else if (gesture == Gesture.MOVE) {
            panel = GdxFloatingGameLogLayout.moved(start, x - startX, y - startY,
                    viewport.getWorldWidth(), viewport.getWorldHeight());
        } else if (gesture == Gesture.RESIZE) {
            panel = GdxFloatingGameLogLayout.resized(start, resize, x - startX, y - startY,
                    viewport.getWorldWidth(), viewport.getWorldHeight());
        }
        return true;
    }

    boolean touchUp(float x, float y, int index, int button) {
        if (!pointer.release(index, button)) return false;
        if (gesture == Gesture.CLOSE && fallbackCloseBounds().contains(x, y)) {
            close();
        } else if (gesture == Gesture.WEB) {
            mouse(MouseEvent.MOUSE_RELEASED, x, y);
        }
        gesture = Gesture.NONE;
        start = null;
        return true;
    }

    boolean cancel(int index, int button) {
        if (!pointer.release(index, button)) return false;
        gesture = Gesture.NONE;
        start = null;
        unfocus();
        return true;
    }

    boolean mouseMoved(float x, float y) {
        if (!isOpen()) return false;
        boolean over = webBounds(panel).contains(x, y);
        if (over || webHovered) mouse(over ? MouseEvent.MOUSE_MOVED : MouseEvent.MOUSE_EXITED, x, y);
        webHovered = over;
        return contains(x, y);
    }

    private void mouse(int event, float x, float y) {
        Rectangle web = webBounds(panel);
        browser.mouse(event, (x - web.x) / web.width, (web.y + web.height - y) / web.height);
    }

    boolean scrolled(float x, float y, float amount) {
        if (!contains(x, y)) return false;
        Rectangle web = webBounds(panel);
        if (web.contains(x, y)) {
            browser.wheel((x - web.x) / web.width, (web.y + web.height - y) / web.height, amount);
        }
        return true;
    }

    boolean key(int event, int key, char character) {
        if (!focused()) return false;
        if (key == Input.Keys.ESCAPE && event == KeyEvent.KEY_PRESSED) close();
        else browser.key(event, key, character);
        return true;
    }

    void updateKeyboard(float delta, boolean enabled) {
        if (isOpen()) browser.updateKeyboard(delta, enabled && keyboardFocused);
    }

    void unfocus() {
        if (keyboardFocused && isOpen()) browser.unfocus();
        keyboardFocused = false;
    }

    void resize(Viewport viewport) {
        if (panel != null) panel = GdxFloatingGameLogLayout.normalize(panel,
                viewport.getWorldWidth(), viewport.getWorldHeight());
    }

    Cursor.SystemCursor cursor(float x, float y) {
        var handle = gesture == Gesture.RESIZE ? resize
                : GdxFloatingGameLogLayout.resizeHandleAt(panel, x, y);
        return switch (handle) {
            case LEFT, RIGHT -> Cursor.SystemCursor.HorizontalResize;
            case TOP, BOTTOM -> Cursor.SystemCursor.VerticalResize;
            case TOP_LEFT, BOTTOM_RIGHT -> Cursor.SystemCursor.NWSEResize;
            case TOP_RIGHT, BOTTOM_LEFT -> Cursor.SystemCursor.NESWResize;
            case NONE -> innerCursor(x, y);
        };
    }

    private Cursor.SystemCursor innerCursor(float x, float y) {
        if (titleDragBounds().contains(x, y)) return Cursor.SystemCursor.AllResize;
        if (webBounds(panel).contains(x, y)) return browser.cursor();
        return Cursor.SystemCursor.Arrow;
    }

    @Override
    public void close() {
        if (!isOpen()) return;
        browser.close();
        browser = null;
        gesture = Gesture.NONE;
        start = null;
        pointer.clear();
        keyboardFocused = false;
        webHovered = false;
    }

    private enum Gesture { NONE, WEB, MOVE, RESIZE, CLOSE }
}
