/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package org.cef.browser;

import com.tonikelope.coronapoker.gdx.PokerVideoFrameBuffer;
import java.awt.Canvas;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.cef.CefBrowserSettings;
import org.cef.CefClient;
import org.cef.OS;
import org.cef.callback.CefDragData;
import org.cef.handler.CefRenderHandler;
import org.cef.handler.CefScreenInfo;

/**
 * Minimal JCEF OSR browser that does not create or overlay an AWT/Swing window.
 *
 * <p>JCEF's stock OSR browser owns a JOGL Canvas. This experiment deliberately replaces that
 * presentation layer: CEF still renders windowlessly, while libGDX owns the only visible OpenGL
 * context and receives copied paint regions through
 * {@link PokerVideoFrameBuffer}.</p>
 */
public final class LibGdxCefBrowser extends CefBrowser_N implements CefRenderHandler {
    private final PokerVideoFrameBuffer frameBuffer;
    private final Canvas eventSource = new Canvas();
    private final CopyOnWriteArrayList<Consumer<CefPaintEvent>> paintListeners =
            new CopyOnWriteArrayList<>();
    private final AtomicBoolean requestedFocus = new AtomicBoolean();
    private volatile Rectangle viewRect;
    private volatile double deviceScaleFactor = 1.0;

    public LibGdxCefBrowser(CefClient client, String url, CefRequestContext context,
            CefBrowserSettings settings, PokerVideoFrameBuffer frameBuffer,
            int width, int height) {
        this(client, url, context, null, null, settings, frameBuffer, width, height);
    }

    private LibGdxCefBrowser(CefClient client, String url, CefRequestContext context,
            CefBrowser_N parent, Point inspectAt, CefBrowserSettings settings,
            PokerVideoFrameBuffer frameBuffer, int width, int height) {
        super(client, url, context, parent, inspectAt, settings);
        this.frameBuffer = frameBuffer;
        viewRect = new Rectangle(0, 0, Math.max(1, width), Math.max(1, height));
    }

    @Override
    public void createImmediately() {
        if (getParentBrowser() == null) {
            createBrowser(getClient(), 0L, getUrl(), true, false, null, getRequestContext());
        } else {
            createDevTools(getParentBrowser(), getClient(), 0L, true, false, null, getInspectAt());
        }
    }

    @Override
    public Component getUIComponent() {
        return eventSource;
    }

    @Override
    public CefRenderHandler getRenderHandler() {
        return this;
    }

    @Override
    protected CefBrowser_N createDevToolsBrowser(CefClient client, String url,
            CefRequestContext context, CefBrowser_N parent, Point inspectAt) {
        Rectangle current = viewRect;
        return new LibGdxCefBrowser(client, url, context, parent, inspectAt, null, frameBuffer,
                current.width, current.height);
    }

    public void resizeBrowser(int width, int height, double scaleFactor) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        double safeScale = Math.max(1.0, scaleFactor);
        Rectangle current = viewRect;
        if (current.width == safeWidth && current.height == safeHeight
                && Double.compare(deviceScaleFactor, safeScale) == 0) return;
        deviceScaleFactor = safeScale;
        viewRect = new Rectangle(0, 0, safeWidth, safeHeight);
        wasResized(safeWidth, safeHeight);
    }

    @Override
    public void setFocus(boolean enable) {
        // CefClient.onGotFocus calls setFocus(true) again. On Windows the
        // windowless browser can re-enter that callback before JNI unwinds.
        if (OS.isWindows() && !requestedFocus.compareAndSet(!enable, enable)) return;
        super.setFocus(enable);
    }

    @Override
    public void setWindowVisibility(boolean visible) {
        if (!visible) requestedFocus.set(false);
        super.setWindowVisibility(visible);
    }

    public void requestRepaint() {
        invalidate();
    }

    public void dispatchMouse(int eventId, int x, int y, int button, int modifiers,
            int clickCount) {
        MouseEvent event = new MouseEvent(eventSource, eventId, System.currentTimeMillis(),
                modifiers, x, y, Math.max(0, clickCount), false, button);
        sendMouseEvent(event);
    }

    public void dispatchWheel(int x, int y, int modifiers, int rotation) {
        dispatchWheel(x, y, modifiers, 3, rotation);
    }

    public void dispatchWheelPixels(int x, int y, int modifiers, int pixels) {
        dispatchWheel(x, y, modifiers, 1, pixels);
    }

    private void dispatchWheel(int x, int y, int modifiers, int units, int rotation) {
        MouseWheelEvent event = new MouseWheelEvent(eventSource, MouseEvent.MOUSE_WHEEL,
                System.currentTimeMillis(), modifiers, x, y, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, units, rotation);
        sendMouseWheelEvent(event);
    }

    public void dispatchKey(int eventId, int keyCode, char keyChar, int modifiers) {
        int location = eventId == KeyEvent.KEY_TYPED
                ? KeyEvent.KEY_LOCATION_UNKNOWN : KeyEvent.KEY_LOCATION_STANDARD;
        KeyEvent event = new KeyEvent(eventSource, eventId, System.currentTimeMillis(), modifiers,
                keyCode, keyChar, location);
        sendKeyEvent(event);
    }

    public static int awtModifiers(boolean shift, boolean control, boolean alt, boolean meta,
            boolean primaryButton, boolean middleButton, boolean secondaryButton) {
        int modifiers = 0;
        if (shift) modifiers |= InputEvent.SHIFT_DOWN_MASK;
        if (control) modifiers |= InputEvent.CTRL_DOWN_MASK;
        if (alt) modifiers |= InputEvent.ALT_DOWN_MASK;
        if (meta) modifiers |= InputEvent.META_DOWN_MASK;
        if (primaryButton) modifiers |= InputEvent.BUTTON1_DOWN_MASK;
        if (middleButton) modifiers |= InputEvent.BUTTON2_DOWN_MASK;
        if (secondaryButton) modifiers |= InputEvent.BUTTON3_DOWN_MASK;
        return modifiers;
    }

    @Override
    public Rectangle getViewRect(CefBrowser browser) {
        return new Rectangle(viewRect);
    }

    @Override
    public boolean getScreenInfo(CefBrowser browser, CefScreenInfo screenInfo) {
        Rectangle current = new Rectangle(viewRect);
        screenInfo.Set(deviceScaleFactor, 32, 8, false, current, current);
        return true;
    }

    @Override
    public Point getScreenPoint(CefBrowser browser, Point viewPoint) {
        return new Point(viewPoint);
    }

    @Override
    public void onPopupShow(CefBrowser browser, boolean show) {
        frameBuffer.setPopupVisible(show);
        if (!show) invalidate();
    }

    @Override
    public void onPopupSize(CefBrowser browser, Rectangle size) {
        frameBuffer.setPopupRectangle(size);
    }

    @Override
    public void onPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects,
            ByteBuffer buffer, int width, int height) {
        frameBuffer.onPaint(popup, dirtyRects, buffer, width, height);
        if (!paintListeners.isEmpty()) {
            CefPaintEvent paintEvent =
                    new CefPaintEvent(browser, popup, dirtyRects, buffer, width, height);
            for (Consumer<CefPaintEvent> listener : paintListeners) listener.accept(paintEvent);
        }
    }

    @Override
    public void addOnPaintListener(Consumer<CefPaintEvent> listener) {
        paintListeners.add(listener);
    }

    @Override
    public void setOnPaintListener(Consumer<CefPaintEvent> listener) {
        paintListeners.clear();
        paintListeners.add(listener);
    }

    @Override
    public void removeOnPaintListener(Consumer<CefPaintEvent> listener) {
        paintListeners.remove(listener);
    }

    @Override
    public boolean onCursorChange(CefBrowser browser, int cursorType) {
        frameBuffer.setCursorType(cursorType);
        return true;
    }

    @Override
    public boolean startDragging(CefBrowser browser, CefDragData dragData, int mask, int x, int y) {
        return false;
    }

    @Override
    public void updateDragCursor(CefBrowser browser, int operation) {
        // Native drag-and-drop is intentionally outside the scope of this video-player PoC.
    }

    @Override
    public CompletableFuture<BufferedImage> createScreenshot(boolean nativeResolution) {
        CompletableFuture<BufferedImage> result = new CompletableFuture<>();
        result.completeExceptionally(new UnsupportedOperationException(
                "Screenshots are not exposed by the libGDX OSR bridge"));
        return result;
    }
}
