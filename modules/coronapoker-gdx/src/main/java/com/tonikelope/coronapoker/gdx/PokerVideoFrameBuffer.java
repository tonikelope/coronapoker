/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import java.awt.Rectangle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Thread boundary between JCEF paint callbacks and the libGDX OpenGL thread.
 *
 * <p>CEF provides a temporary full BGRA buffer with an upper-left origin. Dirty rows are copied
 * into one persistent direct staging buffer during the callback. The GL thread later uploads only
 * the accumulated dirty rectangles. No OpenGL call is ever made by a CEF thread.</p>
 */
public final class PokerVideoFrameBuffer {
    private static final int BYTES_PER_PIXEL = 4;
    private static final int MAX_DIRTY_RECTS = 64;
    private static final int GL_BGRA = 0x80E1;
    private static final int GL_UNPACK_ROW_LENGTH = 0x0CF2;
    private static final int GL_UNPACK_SKIP_ROWS = 0x0CF3;
    private static final int GL_UNPACK_SKIP_PIXELS = 0x0CF4;

    private final Object lock = new Object();
    private final int[] dirtyX = new int[MAX_DIRTY_RECTS];
    private final int[] dirtyY = new int[MAX_DIRTY_RECTS];
    private final int[] dirtyWidth = new int[MAX_DIRTY_RECTS];
    private final int[] dirtyHeight = new int[MAX_DIRTY_RECTS];

    private ByteBuffer pixels;
    private int width;
    private int height;
    private int dirtyCount;
    private boolean fullDirty;
    private boolean acceptingFrames = true;
    private boolean closed;
    private boolean popupVisible;
    private Rectangle popupRectangle = new Rectangle();
    private Texture texture;
    private int textureWidth;
    private int textureHeight;
    private volatile int cursorType;
    private volatile long copiedFrames;
    private volatile long uploadedFrames;
    private volatile double uploadsPerSecond;
    private volatile double averageUploadMillis;
    private long metricWindowStart = System.nanoTime();
    private long metricUploadNanos;
    private int metricUploads;

    public void onPaint(boolean popup, Rectangle[] dirtyRects, ByteBuffer source, int sourceWidth,
            int sourceHeight) {
        synchronized (lock) {
            if (!acceptingFrames || sourceWidth <= 0 || sourceHeight <= 0) return;
            if (popup) {
                copyPopup(source, sourceWidth, sourceHeight, dirtyRects);
            } else {
                ensureBuffer(sourceWidth, sourceHeight);
                copyView(source, sourceWidth, sourceHeight, dirtyRects);
            }
            copiedFrames++;
        }
    }

    private void ensureBuffer(int newWidth, int newHeight) {
        if (pixels != null && width == newWidth && height == newHeight) return;
        width = newWidth;
        height = newHeight;
        int bytes = Math.multiplyExact(Math.multiplyExact(width, height), BYTES_PER_PIXEL);
        if (pixels == null || pixels.capacity() < bytes) {
            int capacity = pixels == null ? bytes : Math.max(bytes, pixels.capacity() + pixels.capacity() / 4);
            pixels = ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder());
        }
        pixels.clear();
        dirtyCount = 0;
        fullDirty = true;
    }

    private void copyView(ByteBuffer source, int sourceWidth, int sourceHeight,
            Rectangle[] dirtyRects) {
        if (dirtyRects == null || dirtyRects.length == 0 || fullDirty) {
            copyWhole(source, sourceWidth, sourceHeight);
            fullDirty = true;
            dirtyCount = 0;
            return;
        }
        long dirtyArea = 0;
        for (Rectangle rectangle : dirtyRects) {
            if (rectangle != null) dirtyArea += (long) rectangle.width * rectangle.height;
        }
        if (dirtyArea * 2L >= (long) sourceWidth * sourceHeight
                || dirtyRects.length + dirtyCount > MAX_DIRTY_RECTS) {
            copyWhole(source, sourceWidth, sourceHeight);
            fullDirty = true;
            dirtyCount = 0;
            return;
        }
        for (Rectangle dirty : dirtyRects) {
            Rectangle clipped = clip(dirty, sourceWidth, sourceHeight);
            if (clipped.isEmpty()) continue;
            copyRows(source, sourceWidth, clipped.x, clipped.y, pixels, width, clipped.x, clipped.y,
                    clipped.width, clipped.height);
            addDirty(clipped.x, clipped.y, clipped.width, clipped.height);
        }
    }

    private void copyPopup(ByteBuffer source, int sourceWidth, int sourceHeight,
            Rectangle[] dirtyRects) {
        if (!popupVisible || pixels == null || popupRectangle.isEmpty()) return;
        Rectangle popup = new Rectangle(popupRectangle);
        Rectangle destinationBounds = new Rectangle(0, 0, width, height);
        Rectangle destination = popup.intersection(destinationBounds);
        if (destination.isEmpty()) return;

        int sourceX = destination.x - popup.x;
        int sourceY = destination.y - popup.y;
        int copyWidth = Math.min(destination.width, sourceWidth - sourceX);
        int copyHeight = Math.min(destination.height, sourceHeight - sourceY);
        if (copyWidth <= 0 || copyHeight <= 0) return;
        copyRows(source, sourceWidth, sourceX, sourceY, pixels, width, destination.x, destination.y,
                copyWidth, copyHeight);
        addDirty(destination.x, destination.y, copyWidth, copyHeight);
    }

    private void copyWhole(ByteBuffer source, int sourceWidth, int sourceHeight) {
        int bytes = Math.multiplyExact(Math.multiplyExact(sourceWidth, sourceHeight),
                BYTES_PER_PIXEL);
        pixels.put(0, source, 0, bytes);
    }

    private static void copyRows(ByteBuffer source, int sourceStridePixels, int sourceX,
            int sourceY, ByteBuffer destination, int destinationStridePixels, int destinationX,
            int destinationY, int copyWidth, int copyHeight) {
        int rowBytes = copyWidth * BYTES_PER_PIXEL;
        for (int row = 0; row < copyHeight; row++) {
            int sourceOffset = ((sourceY + row) * sourceStridePixels + sourceX) * BYTES_PER_PIXEL;
            int destinationOffset =
                    ((destinationY + row) * destinationStridePixels + destinationX)
                            * BYTES_PER_PIXEL;
            destination.put(destinationOffset, source, sourceOffset, rowBytes);
        }
    }

    private static Rectangle clip(Rectangle source, int maxWidth, int maxHeight) {
        if (source == null) return new Rectangle();
        int x = Math.max(0, source.x);
        int y = Math.max(0, source.y);
        int right = Math.min(maxWidth, source.x + source.width);
        int bottom = Math.min(maxHeight, source.y + source.height);
        return new Rectangle(x, y, Math.max(0, right - x), Math.max(0, bottom - y));
    }

    private void addDirty(int x, int y, int dirtyWidthValue, int dirtyHeightValue) {
        if (fullDirty) return;
        if (dirtyCount >= MAX_DIRTY_RECTS) {
            fullDirty = true;
            dirtyCount = 0;
            return;
        }
        dirtyX[dirtyCount] = x;
        dirtyY[dirtyCount] = y;
        dirtyWidth[dirtyCount] = dirtyWidthValue;
        dirtyHeight[dirtyCount] = dirtyHeightValue;
        dirtyCount++;
    }

    /** Must be called only from libGDX's render thread. */
    public Texture uploadPendingFrame() {
        return uploadPendingFrame(0, 0);
    }

    /** Retains the previous texture until the browser paints the requested viewport. */
    public Texture uploadPendingFrame(int expectedWidth, int expectedHeight) {
        synchronized (lock) {
            if (pixels == null) return texture;
            if (expectedWidth > 0 && (Math.abs(width - expectedWidth) > 1
                    || Math.abs(height - expectedHeight) > 1)) return texture;
            boolean recreate = texture == null || textureWidth != width || textureHeight != height;
            if (recreate) {
                if (texture != null) texture.dispose();
                texture = new Texture(width, height, Pixmap.Format.RGBA8888);
                texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                textureWidth = width;
                textureHeight = height;
                fullDirty = true;
                dirtyCount = 0;
            }
            if (!fullDirty && dirtyCount == 0) return texture;

            long started = System.nanoTime();
            texture.bind(0);
            Gdx.gl.glPixelStorei(GL20.GL_UNPACK_ALIGNMENT, 1);
            Gdx.gl.glPixelStorei(GL_UNPACK_ROW_LENGTH, width);
            pixels.position(0);
            pixels.limit(width * height * BYTES_PER_PIXEL);
            if (fullDirty) {
                Gdx.gl.glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
                Gdx.gl.glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
                Gdx.gl.glTexSubImage2D(GL20.GL_TEXTURE_2D, 0, 0, 0, width, height, GL_BGRA,
                        GL20.GL_UNSIGNED_BYTE, pixels);
            } else {
                for (int index = 0; index < dirtyCount; index++) {
                    Gdx.gl.glPixelStorei(GL_UNPACK_SKIP_PIXELS, dirtyX[index]);
                    Gdx.gl.glPixelStorei(GL_UNPACK_SKIP_ROWS, dirtyY[index]);
                    Gdx.gl.glTexSubImage2D(GL20.GL_TEXTURE_2D, 0, dirtyX[index], dirtyY[index],
                            dirtyWidth[index], dirtyHeight[index], GL_BGRA, GL20.GL_UNSIGNED_BYTE,
                            pixels);
                }
            }
            Gdx.gl.glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
            Gdx.gl.glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
            Gdx.gl.glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);

            long elapsed = System.nanoTime() - started;
            uploadedFrames++;
            metricUploads++;
            metricUploadNanos += elapsed;
            updateMetrics(System.nanoTime());
            fullDirty = false;
            dirtyCount = 0;
            return texture;
        }
    }

    private void updateMetrics(long now) {
        long windowNanos = now - metricWindowStart;
        if (windowNanos < 1_000_000_000L) return;
        uploadsPerSecond = metricUploads * 1_000_000_000.0 / windowNanos;
        averageUploadMillis = metricUploads == 0 ? 0.0
                : metricUploadNanos / 1_000_000.0 / metricUploads;
        metricWindowStart = now;
        metricUploads = 0;
        metricUploadNanos = 0;
    }

    public void setAcceptingFrames(boolean accepting) {
        synchronized (lock) {
            acceptingFrames = accepting && !closed;
            if (!acceptingFrames) {
                dirtyCount = 0;
                fullDirty = false;
            } else if (pixels != null) {
                fullDirty = true;
            }
        }
    }

    public void setPopupVisible(boolean visible) {
        synchronized (lock) {
            popupVisible = visible;
        }
    }

    public void setPopupRectangle(Rectangle rectangle) {
        synchronized (lock) {
            popupRectangle = rectangle == null ? new Rectangle() : new Rectangle(rectangle);
        }
    }

    public void setCursorType(int cursorType) {
        this.cursorType = cursorType;
    }

    public int cursorType() {
        return cursorType;
    }

    public int width() {
        synchronized (lock) {
            return width;
        }
    }

    public int height() {
        synchronized (lock) {
            return height;
        }
    }

    public long copiedFrames() {
        return copiedFrames;
    }

    public long uploadedFrames() {
        return uploadedFrames;
    }

    public double uploadsPerSecond() {
        return uploadsPerSecond;
    }

    public double averageUploadMillis() {
        return averageUploadMillis;
    }

    public Texture texture() {
        synchronized (lock) {
            return texture;
        }
    }

    public void dispose() {
        synchronized (lock) {
            if (texture != null) {
                texture.dispose();
                texture = null;
            }
            pixels = null;
        }
    }

    void close() {
        synchronized (lock) {
            closed = true;
            acceptingFrames = false;
            dispose();
        }
    }
}
