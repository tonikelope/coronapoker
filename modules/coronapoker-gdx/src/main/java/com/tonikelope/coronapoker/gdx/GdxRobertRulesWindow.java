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
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.Viewport;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/** A floating document window shared by About, the lobby and the table. */
final class GdxRobertRulesWindow implements AutoCloseable {

    static final String ICON = "images/open-book.png";
    static final String TITLE = "menu.reglas_de_robert";
    static final String OPACITY = "gdx_robert_rules_opacity";
    private static final int MIN_OPACITY = 10;
    private static final int MAX_OPACITY = 100;
    private static final float SIDE_RESIZE_REACH = 8f;
    private static final float DOCUMENT_BORDER = 2f;
    private static final float FOOTER_HEIGHT = 44f;
    private static final long RESIZE_INTERVAL = TimeUnit.MILLISECONDS.toNanos(72);
    private static final Color PANEL = new Color(0x0b1522ff);
    private static final Color BORDER = new Color(GdxSettingsStyle.CYAN_RGBA);
    private static final Color TRACK = new Color(GdxSettingsStyle.TOGGLE_OFF_RGBA);
    private static final Color GOLD = new Color(GdxSettingsStyle.GOLD_RGBA);

    private final GdxFloatingWindowPointer pointer = new GdxFloatingWindowPointer();
    private final GdxPointerHold webContextPointer = new GdxPointerHold();
    private final GlyphLayout label = new GlyphLayout();
    private final GdxWindowTooltip tooltip = new GdxWindowTooltip();
    private Rectangle panel;
    private Rectangle start;
    private float startX;
    private float startY;
    private Gesture gesture = Gesture.NONE;
    private GdxFloatingGameLogLayout.ResizeHandle resize = GdxFloatingGameLogLayout.ResizeHandle.NONE;
    private GdxRobertRulesBrowser browser;
    private Viewport viewport;
    private Properties properties;
    private Runnable persist;
    private int opacity;
    private int browserWidth;
    private int browserHeight;
    private double browserScale;
    private long nextResizePaint;
    private boolean focused;
    private boolean webHovered;

    boolean open(Viewport viewport, Properties properties, Runnable persist, String language) {
        if (isOpen()) return true;
        if (!PokerChromium.available()) return false;
        this.viewport = viewport;
        this.properties = properties;
        this.persist = persist;
        opacity = readOpacity(properties);
        if (panel == null) panel = GdxFloatingGameLogLayout.initial(worldWidth(), worldHeight());
        else resizePanel();
        measureBrowser(documentBounds(panel));
        browser = new GdxRobertRulesBrowser(browserWidth, browserHeight, browserScale, language);
        return true;
    }

    boolean isOpen() {
        return browser != null;
    }

    boolean contains(float x, float y) {
        return isOpen() && (panel.contains(x, y) || sideResizeHandle(x, y)
                != GdxFloatingGameLogLayout.ResizeHandle.NONE);
    }

    boolean ownsPointer(float x, float y) {
        return isOpen() && (pointer.active() || contains(x, y));
    }

    boolean focused() {
        return isOpen() && focused;
    }

    static int readOpacity(Properties properties) {
        try {
            return MathUtils.clamp(Integer.parseInt(properties.getProperty(OPACITY, "100")),
                    MIN_OPACITY, MAX_OPACITY);
        } catch (NumberFormatException invalid) {
            return MAX_OPACITY;
        }
    }

    static int opacityAt(Rectangle track, float x) {
        return Math.round(MathUtils.lerp(MIN_OPACITY, MAX_OPACITY,
                MathUtils.clamp((x - track.x) / track.width, 0f, 1f)));
    }

    static Rectangle documentBounds(Rectangle panel) {
        return new Rectangle(panel.x + DOCUMENT_BORDER, panel.y + FOOTER_HEIGHT,
                panel.width - 2f * DOCUMENT_BORDER,
                panel.height - GdxFloatingGameLogLayout.HEADER_HEIGHT - FOOTER_HEIGHT);
    }

    private Rectangle closeBounds() {
        return GdxFloatingGameLogLayout.closeBounds(panel);
    }

    private Rectangle sliderBounds() {
        return new Rectangle(panel.x + (panel.width - 310f) / 2f,
                panel.y + 10f, 250f, 29f);
    }

    private Rectangle trackBounds() {
        Rectangle slider = sliderBounds();
        return new Rectangle(slider.x + 9f, slider.y + (slider.height - 5f) / 2f,
                slider.width - 18f, 5f);
    }

    void draw(ShapeRenderer shapes, SpriteBatch batch, BitmapFont titleFont,
            BitmapFont smallFont, GdxGameText text, float x, float y) {
        if (!isOpen()) return;
        Rectangle web = documentBounds(panel);
        long now = System.nanoTime();
        if (gesture != Gesture.RESIZE || now >= nextResizePaint) {
            measureBrowser(web);
            nextResizePaint = now + RESIZE_INTERVAL;
        }
        boolean failed = browser.failed();
        Texture pdf = failed ? null : browser.texture(browserWidth, browserHeight, browserScale);
        Rectangle close = closeBounds();
        Rectangle slider = sliderBounds();
        Rectangle track = trackBounds();
        float fill = track.width * (opacity - MIN_OPACITY) / (MAX_OPACITY - MIN_OPACITY);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(PANEL);
        if (opacity == MAX_OPACITY) {
            GdxAntialiasedRoundedRect.fill(shapes, panel.x, panel.y, panel.width, panel.height, 18f);
        }
        GdxAntialiasedRoundedRect.fill(shapes, panel.x + 1f, web.y + web.height - 1f,
                panel.width - 2f, GdxFloatingGameLogLayout.HEADER_HEIGHT, 16f);
        GdxAntialiasedRoundedRect.fill(shapes, panel.x + 1f, panel.y + 1f,
                panel.width - 2f, FOOTER_HEIGHT, 16f);
        // The inside edges of the title and footer are straight where they meet the PDF.
        shapes.rect(panel.x + 1f, web.y + web.height - 1f, panel.width - 2f, 17f);
        shapes.rect(panel.x + 1f, web.y - 16f, panel.width - 2f, 17f);
        if (pdf == null) {
            shapes.setColor(PANEL);
            shapes.rect(web.x, web.y, web.width, web.height);
        }
        GdxUiDialogStyle.drawWindowClose(shapes, close.x, close.y, close.width, close.height,
                true, close.contains(x, y) ? 1f : 0f, gesture == Gesture.CLOSE, 1f);
        shapes.setColor(TRACK);
        GdxAntialiasedRoundedRect.fill(shapes, track.x, track.y, track.width, track.height, 2.5f);
        shapes.setColor(GOLD);
        if (fill > 0f) GdxAntialiasedRoundedRect.fill(shapes, track.x, track.y, fill, track.height, 2.5f);
        if (slider.contains(x, y) || gesture == Gesture.OPACITY) shapes.setColor(Color.WHITE);
        shapes.circle(track.x + fill, track.y + track.height / 2f, 5f, 20);
        shapes.end();

        batch.begin();
        if (pdf != null) {
            drawPdf(batch, pdf, web);
        } else {
            drawLabel(batch, smallFont, text.translate(failed ? "gdx.rules.unavailable"
                    : "gdx.rules.loading"), web, Align.center);
        }
        GdxUiDialogStyle.drawWindowTitle(batch, titleFont, label, text.translate(TITLE),
                GdxFloatingGameLogLayout.headerBounds(panel), viewport, 1f);
        drawLabel(batch, smallFont, opacity + " %",
                new Rectangle(slider.x + slider.width + 5f, slider.y, 55f, slider.height),
                Align.center);
        batch.end();
        // Draw the common frame over the Chromium texture and both chrome bands.
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        GdxUiDialogStyle.drawPanelOutline(shapes, panel.x, panel.y, panel.width, panel.height, BORDER, 1f);
        shapes.end();
        String help = null;
        if (gesture == Gesture.NONE) {
            if (slider.contains(x, y)) help = text.translate("gdx.rules.opacity", opacity);
            else if (close.contains(x, y)) help = text.translate("ui.cerrar");
        }
        tooltip.draw(shapes, batch, smallFont, viewport, x, y, help);
    }

    private void drawPdf(SpriteBatch batch, Texture pdf, Rectangle web) {
        float left = GdxPixelText.snapX(web.x, viewport);
        float right = GdxPixelText.snapX(web.x + web.width, viewport);
        float bottom = GdxPixelText.snapY(web.y, viewport);
        float top = GdxPixelText.snapY(web.y + web.height, viewport);
        float width = right - left;
        float height = top - bottom;
        int pixelWidth = (int) Math.round(width * pixelsPerWorldX());
        int pixelHeight = (int) Math.round(height * pixelsPerWorldY());
        int sourceWidth = pdf.getWidth();
        int sourceHeight = pdf.getHeight();
        batch.setColor(1f, 1f, 1f, opacity / 100f);
        if (nativeSampling(pixelWidth, pixelHeight, sourceWidth, sourceHeight, browserScale)) {
            // Fractional CEF scale and snapped GL bounds can differ by a pixel.
            // Crop or clamp the last texel instead of filtering the entire page.
            batch.draw(pdf, left, bottom, width, height, 0f, pixelHeight / (float) sourceHeight,
                    pixelWidth / (float) sourceWidth, 0f);
        } else {
            // Keep the previous paint visible while Chromium handles a real resize.
            batch.draw(pdf, left, bottom, width, height);
        }
        batch.setColor(Color.WHITE);
    }

    static boolean nativeSampling(int pixelWidth, int pixelHeight,
            int sourceWidth, int sourceHeight, double deviceScale) {
        int rounding = (int) Math.ceil(deviceScale / 2d + 1d);
        return pixelWidth > 0 && pixelHeight > 0 && Math.abs(pixelWidth - sourceWidth) <= rounding
                && Math.abs(pixelHeight - sourceHeight) <= rounding;
    }

    private void drawLabel(SpriteBatch batch, BitmapFont font, String value, Rectangle box, int align) {
        label.setText(font, value, 0, value.length(), Color.WHITE, box.width, align, false, "…");
        GdxPixelText.draw(font, batch, label, box.x, box.y + (box.height + label.height) / 2f, viewport);
    }

    boolean touchDown(float x, float y, int index, int button) {
        if (!contains(x, y)) {
            if (ownsPointer(x, y)) {
                pointer.captureSecondary(index, button);
                return true;
            }
            unfocus();
            return false;
        }
        Rectangle web = documentBounds(panel);
        if (!web.contains(x, y)) browser.dismissSelectionMenu();
        if (!pointer.capture(index, button)) {
            if (button == Input.Buttons.RIGHT && web.contains(x, y)
                    && webContextPointer.capture(index, button)) {
                focused = true;
                browser.mouse(MouseEvent.MOUSE_PRESSED, mouseX(web, x), mouseY(web, y), MouseEvent.BUTTON3);
            }
            return true;
        }
        resize = resizeHandleAt(x, y);
        if (closeBounds().contains(x, y)) gesture = Gesture.CLOSE;
        else if (resize != GdxFloatingGameLogLayout.ResizeHandle.NONE) begin(Gesture.RESIZE, x, y);
        else if (GdxFloatingGameLogLayout.headerBounds(panel).contains(x, y)) begin(Gesture.MOVE, x, y);
        else if (sliderBounds().contains(x, y)) {
            gesture = Gesture.OPACITY;
            opacity = opacityAt(trackBounds(), x);
        } else if (web.contains(x, y)) {
            gesture = Gesture.WEB;
            focused = true;
            mouse(MouseEvent.MOUSE_PRESSED, x, y);
        }
        return true;
    }

    private void begin(Gesture kind, float x, float y) {
        gesture = kind;
        start = new Rectangle(panel);
        startX = x;
        startY = y;
    }

    boolean touchDragged(float x, float y, int index) {
        if (!pointer.ownsPrimary(index)) return ownsPointer(x, y);
        switch (gesture) {
            case MOVE -> panel = GdxFloatingGameLogLayout.moved(start, x - startX, y - startY,
                    worldWidth(), worldHeight());
            case RESIZE -> panel = GdxFloatingGameLogLayout.resized(start, resize, x - startX, y - startY,
                    worldWidth(), worldHeight());
            case OPACITY -> opacity = opacityAt(trackBounds(), x);
            case WEB -> mouse(MouseEvent.MOUSE_DRAGGED, x, y);
            case NONE, CLOSE -> { }
        }
        return true;
    }

    boolean touchUp(float x, float y, int index, int button) {
        if (!pointer.releasePrimary(index, button)) {
            if (webContextPointer.release(index, button)) {
                Rectangle web = documentBounds(panel);
                browser.mouse(MouseEvent.MOUSE_RELEASED, mouseX(web, x), mouseY(web, y), MouseEvent.BUTTON3);
            }
            return pointer.releaseSecondary(index, button) || contains(x, y);
        }
        switch (gesture) {
            case CLOSE -> { if (closeBounds().contains(x, y)) close(); }
            case OPACITY -> { opacity = opacityAt(trackBounds(), x); saveOpacity(); }
            case WEB -> mouse(MouseEvent.MOUSE_RELEASED, x, y);
            case RESIZE -> measureBrowser(documentBounds(panel));
            case NONE, MOVE -> { }
        }
        gesture = Gesture.NONE;
        start = null;
        return true;
    }

    boolean cancel(int index, int button) {
        if (webContextPointer.release(index, button)) {
            browser.mouse(MouseEvent.MOUSE_RELEASED, -1, -1, MouseEvent.BUTTON3);
        }
        if (!pointer.releasePrimary(index, button)) return pointer.releaseSecondary(index, button);
        if (gesture == Gesture.OPACITY) saveOpacity();
        gesture = Gesture.NONE;
        start = null;
        unfocus();
        return true;
    }

    boolean mouseMoved(float x, float y) {
        if (!isOpen()) return false;
        boolean over = documentBounds(panel).contains(x, y);
        if (over || webHovered) mouse(over ? MouseEvent.MOUSE_MOVED : MouseEvent.MOUSE_EXITED, x, y);
        webHovered = over;
        return ownsPointer(x, y);
    }

    private void mouse(int event, float x, float y) {
        Rectangle web = documentBounds(panel);
        browser.mouse(event, mouseX(web, x), mouseY(web, y));
    }

    boolean scrolled(float x, float y, float amount) {
        if (!ownsPointer(x, y)) return false;
        Rectangle web = documentBounds(panel);
        if (web.contains(x, y)) {
            browser.wheel(MathUtils.clamp(mouseX(web, x), 0, browserWidth - 1),
                    MathUtils.clamp(mouseY(web, y), 0, browserHeight - 1), amount);
        }
        return true;
    }

    boolean key(int event, int key, char character) {
        if (!isOpen()) return false;
        if (event == KeyEvent.KEY_PRESSED && key == Input.Keys.ESCAPE) {
            if (!browser.dismissSelectionMenu()) close();
            return true;
        }
        if (!focused) return false;
        browser.key(event, key, character);
        return true;
    }

    void updateKeyboard(float delta) {
        if (focused()) browser.updateKeyboard(delta);
    }

    void unfocus() {
        if (focused()) browser.unfocus();
        focused = false;
    }

    void suspendPointer() {
        if (!isOpen()) return;
        if (webContextPointer.active()) {
            browser.mouse(MouseEvent.MOUSE_RELEASED, -1, -1, MouseEvent.BUTTON3);
            webContextPointer.clear();
        }
        if (gesture == Gesture.WEB) browser.mouse(MouseEvent.MOUSE_RELEASED, -1, -1);
        if (gesture == Gesture.OPACITY) saveOpacity();
        if (webHovered) browser.mouse(MouseEvent.MOUSE_EXITED, -1, -1);
        webHovered = false;
        pointer.clear();
        gesture = Gesture.NONE;
        start = null;
        tooltip.clear();
        unfocus();
    }

    void resize() {
        if (isOpen()) resizePanel();
    }

    private void resizePanel() {
        panel = GdxFloatingGameLogLayout.normalize(panel, worldWidth(), worldHeight());
    }

    private GdxFloatingGameLogLayout.ResizeHandle sideResizeHandle(float x, float y) {
        return sideResizeHandle(panel, x, y);
    }

    static GdxFloatingGameLogLayout.ResizeHandle sideResizeHandle(Rectangle panel, float x, float y) {
        Rectangle web = documentBounds(panel);
        if (y >= web.y && y <= web.y + web.height) {
            if (x >= panel.x - SIDE_RESIZE_REACH && x < web.x) {
                return GdxFloatingGameLogLayout.ResizeHandle.LEFT;
            }
            if (x > web.x + web.width && x <= panel.x + panel.width + SIDE_RESIZE_REACH) {
                return GdxFloatingGameLogLayout.ResizeHandle.RIGHT;
            }
        }
        return GdxFloatingGameLogLayout.ResizeHandle.NONE;
    }

    private GdxFloatingGameLogLayout.ResizeHandle resizeHandleAt(float x, float y) {
        if (documentBounds(panel).contains(x, y)) {
            return GdxFloatingGameLogLayout.ResizeHandle.NONE;
        }
        var side = sideResizeHandle(x, y);
        return side == GdxFloatingGameLogLayout.ResizeHandle.NONE
                ? GdxFloatingGameLogLayout.resizeHandleAt(panel, x, y) : side;
    }

    Cursor.SystemCursor cursor(float x, float y) {
        var handle = gesture == Gesture.RESIZE ? resize : resizeHandleAt(x, y);
        return switch (handle) {
            case LEFT, RIGHT -> Cursor.SystemCursor.HorizontalResize;
            case TOP, BOTTOM -> Cursor.SystemCursor.VerticalResize;
            case TOP_LEFT, BOTTOM_RIGHT -> Cursor.SystemCursor.NWSEResize;
            case TOP_RIGHT, BOTTOM_LEFT -> Cursor.SystemCursor.NESWResize;
            case NONE -> GdxFloatingGameLogLayout.headerBounds(panel).contains(x, y)
                    ? Cursor.SystemCursor.AllResize : Cursor.SystemCursor.Arrow;
        };
    }

    private int mouseX(Rectangle web, float x) { return Math.round((x - web.x) * browserWidth / web.width); }
    private int mouseY(Rectangle web, float y) { return Math.round((web.y + web.height - y) * browserHeight / web.height); }
    private float worldWidth() { return viewport.getWorldWidth(); }
    private float worldHeight() { return viewport.getWorldHeight(); }

    private void measureBrowser(Rectangle web) {
        double px = pixelsPerWorldX();
        double py = pixelsPerWorldY();
        // Chromium establishes its device scale when the browser is created.
        // On a monitor change, adapt the CSS viewport to keep the raster native.
        if (browser == null) browserScale = Math.max(1d, Math.min(px, py) * 1.15d);
        browserWidth = browserPixels(web.width, px, browserScale);
        browserHeight = browserPixels(web.height, py, browserScale);
    }

    private double pixelsPerWorldX() {
        return viewport.getScreenWidth() / (double) worldWidth()
                * Gdx.graphics.getBackBufferWidth() / Math.max(1, Gdx.graphics.getWidth());
    }

    private double pixelsPerWorldY() {
        return viewport.getScreenHeight() / (double) worldHeight()
                * Gdx.graphics.getBackBufferHeight() / Math.max(1, Gdx.graphics.getHeight());
    }

    static int browserPixels(float worldLength, double pixelsPerWorldUnit, double deviceScale) {
        return Math.max(1, (int) Math.round(worldLength * pixelsPerWorldUnit / deviceScale));
    }

    private void saveOpacity() {
        if (opacity == readOpacity(properties)) return;
        properties.setProperty(OPACITY, Integer.toString(opacity));
        persist.run();
    }

    @Override
    public void close() {
        if (!isOpen()) return;
        saveOpacity();
        browser.close();
        browser = null;
        focused = false;
        webHovered = false;
        gesture = Gesture.NONE;
        start = null;
        tooltip.clear();
        pointer.clear();
        webContextPointer.clear();
    }

    private enum Gesture { NONE, WEB, MOVE, RESIZE, OPACITY, CLOSE }
}
