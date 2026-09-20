/*
 * Copyright (C) 2020-2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;
import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Memory-bounded desktop GIF player for long in-table cinematics.
 *
 * <p>Decoding and GIF disposal/compositing run outside the render thread. The
 * render thread owns one reusable GPU texture and uploads only the newest frame
 * whose presentation time has arrived. This avoids both a visible first-frame
 * stall and the hundreds of megabytes required by one texture per frame.</p>
 */
final class StreamingGifTextureAnimation implements Disposable {

    private static final int DECODED_FRAME_BUFFER = 6;

    private final BlockingQueue<CpuFrame> decoded =
            new ArrayBlockingQueue<>(DECODED_FRAME_BUFFER);
    private final Thread decoderThread;
    private volatile boolean disposed;
    private volatile Throwable failure;
    private volatile int decodedWidth;
    private volatile int decodedHeight;
    private final boolean looping;
    private final long[] frameEndMs;
    private final long durationMs;
    private Texture texture;
    private int textureWidth;
    private int textureHeight;

    private StreamingGifTextureAnimation(DataSource source, String label,
            int maxWidth) {
        this(source, label, maxWidth, false, null);
    }

    private StreamingGifTextureAnimation(DataSource source, String label,
            int maxWidth, boolean looping, GifTiming timing) {
        this.looping = looping;
        frameEndMs = timing == null ? null : timing.frameEndMs();
        durationMs = timing == null ? 0L : timing.durationMs();
        if (timing != null) {
            decodedWidth = timing.width();
            decodedHeight = timing.height();
        }
        decoderThread = new Thread(() -> {
            try {
                byte[] data = source.read();
                long cycleOffset = 0L;
                do {
                    long currentOffset = cycleOffset;
                    decode(data, label, maxWidth, frame -> {
                        if (disposed) return;
                        decodedWidth = frame.width();
                        decodedHeight = frame.height();
                        try {
                            decoded.put(new CpuFrame(frame.width(),
                                    frame.height(),
                                    frame.startMs() + currentOffset,
                                    frame.rgba()));
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                        }
                    }, () -> disposed
                            || Thread.currentThread().isInterrupted());
                    cycleOffset += durationMs;
                } while (looping && durationMs > 0L && !disposed
                        && !Thread.currentThread().isInterrupted());
            } catch (Throwable problem) {
                if (!disposed) failure = problem;
            }
        }, "coronapoker-gdx-gif-" + sanitizeThreadLabel(label));
        decoderThread.setDaemon(true);
        decoderThread.start();
    }

    static StreamingGifTextureAnimation load(String path, int maxWidth)
            throws IOException {
        Objects.requireNonNull(path, "path");
        if (maxWidth <= 0) throw new IllegalArgumentException("maxWidth <= 0");
        FileHandle handle = Gdx.files.internal(path);
        if (!handle.exists()) throw new IOException("Missing GIF " + path);
        return new StreamingGifTextureAnimation(() -> {
            try {
                return handle.readBytes();
            } catch (RuntimeException problem) {
                throw new IOException("Could not read GIF " + path, problem);
            }
        }, path, maxWidth);
    }

    static StreamingGifTextureAnimation load(Path path, int maxWidth)
            throws IOException {
        Objects.requireNonNull(path, "path");
        if (maxWidth <= 0) throw new IllegalArgumentException("maxWidth <= 0");
        if (!Files.isRegularFile(path)) {
            throw new IOException("Missing external GIF " + path);
        }
        return new StreamingGifTextureAnimation(
                () -> Files.readAllBytes(path), path.toString(), maxWidth);
    }

    /**
     * Opens a memory-bounded animation whose frame timeline repeats exactly as
     * the source GIF. Metadata is inspected synchronously, but pixel decoding
     * and scaling remain on the decoder thread.
     */
    static StreamingGifTextureAnimation loadLooping(String path, int maxWidth)
            throws IOException {
        Objects.requireNonNull(path, "path");
        if (maxWidth <= 0) throw new IllegalArgumentException("maxWidth <= 0");
        FileHandle handle = Gdx.files.internal(path);
        if (!handle.exists()) throw new IOException("Missing GIF " + path);
        byte[] data = handle.readBytes();
        GifTiming timing = inspect(data, path, maxWidth);
        return new StreamingGifTextureAnimation(() -> data, path, maxWidth,
                true, timing);
    }

    static StreamingGifTextureAnimation loadLooping(Path path, int maxWidth)
            throws IOException {
        Objects.requireNonNull(path, "path");
        if (maxWidth <= 0) throw new IllegalArgumentException("maxWidth <= 0");
        if (!Files.isRegularFile(path)) {
            throw new IOException("Missing external GIF " + path);
        }
        byte[] data = Files.readAllBytes(path);
        GifTiming timing = inspect(data, path.toString(), maxWidth);
        return new StreamingGifTextureAnimation(() -> data, path.toString(),
                maxWidth, true, timing);
    }

    /** Returns the current frame, or {@code null} while the first one decodes. */
    Texture frameAt(float elapsedSeconds) {
        if (disposed) return null;
        long elapsedMs = Math.max(0L, Math.round(elapsedSeconds * 1000f));
        CpuFrame due = null;
        while (true) {
            CpuFrame candidate = decoded.peek();
            if (candidate == null || candidate.startMs() > elapsedMs) {
                break;
            }
            due = decoded.poll();
        }
        if (due != null) upload(due);
        return texture;
    }

    boolean failed() {
        return failure != null;
    }

    Throwable failure() {
        return failure;
    }

    int width() {
        return decodedWidth;
    }

    int height() {
        return decodedHeight;
    }

    float durationSeconds() {
        return durationMs / 1000f;
    }

    float frameStartSeconds(int oneBasedFrame) {
        if (frameEndMs == null || oneBasedFrame <= 1) return 0f;
        int previousFrame = Math.min(oneBasedFrame - 2,
                frameEndMs.length - 1);
        return frameEndMs[previousFrame] / 1000f;
    }

    private void upload(CpuFrame frame) {
        Pixmap pixmap = new Pixmap(frame.width(), frame.height(),
                Pixmap.Format.RGBA8888);
        try {
            ByteBuffer pixels = pixmap.getPixels();
            pixels.clear();
            // ARGB conversion happens on the decoder thread. Keep the render
            // thread to one contiguous native-buffer copy plus one texture
            // upload so a cinematic cannot disturb table pacing.
            pixels.put(frame.rgba());
            pixels.flip();
            if (texture == null || textureWidth != frame.width()
                    || textureHeight != frame.height()) {
                if (texture != null) texture.dispose();
                // Long cinematics stay close to their native size. A single
                // linear texture is sharper and dramatically smaller than a
                // full mipmapped texture for every GIF frame.
                texture = new Texture(pixmap, false);
                texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
                textureWidth = frame.width();
                textureHeight = frame.height();
            } else {
                texture.draw(pixmap, 0, 0);
            }
        } finally {
            pixmap.dispose();
        }
    }

    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        decoderThread.interrupt();
        decoded.clear();
        if (texture != null) {
            texture.dispose();
            texture = null;
        }
    }

    static void decode(byte[] data, String label, int maxWidth,
            Consumer<CpuFrame> consumer, BooleanSupplier cancelled)
            throws IOException {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(consumer, "consumer");
        Objects.requireNonNull(cancelled, "cancelled");
        try (ByteArrayInputStream input = new ByteArrayInputStream(data);
                ImageInputStream imageInput =
                        ImageIO.createImageInputStream(input)) {
            Iterator<ImageReader> readers =
                    ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) throw new IOException("No GIF reader available");
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, false, false);
                int count = reader.getNumImages(true);
                if (count <= 0) throw new IOException("GIF without frames: " + label);

                int logicalWidth = 0;
                int logicalHeight = 0;
                IIOMetadata streamMetadata = reader.getStreamMetadata();
                if (streamMetadata != null) {
                    Node root = streamMetadata.getAsTree(
                            "javax_imageio_gif_stream_1.0");
                    for (Node child = root.getFirstChild(); child != null;
                            child = child.getNextSibling()) {
                        if ("LogicalScreenDescriptor".equals(
                                child.getNodeName())) {
                            logicalWidth = intAttribute(child,
                                    "logicalScreenWidth", 0);
                            logicalHeight = intAttribute(child,
                                    "logicalScreenHeight", 0);
                        }
                    }
                }
                // Valid GIFs carry their canvas size in stream metadata. Avoid
                // scanning every image descriptor before producing frame zero;
                // this materially reduces time-to-first-frame for long GIFs.
                if (logicalWidth <= 0 || logicalHeight <= 0) {
                    logicalWidth = reader.getWidth(0);
                    logicalHeight = reader.getHeight(0);
                }

                float scale = Math.min(1f, maxWidth / (float) logicalWidth);
                int outputWidth = Math.max(1,
                        Math.round(logicalWidth * scale));
                int outputHeight = Math.max(1,
                        Math.round(logicalHeight * scale));
                BufferedImage canvas = new BufferedImage(logicalWidth,
                        logicalHeight, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = canvas.createGraphics();
                BufferedImage restore = null;
                long elapsed = 0L;
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                            RenderingHints.VALUE_RENDER_QUALITY);
                    for (int i = 0; i < count; i++) {
                        if (cancelled.getAsBoolean()) return;
                        FrameMetadata metadata = readFrameMetadata(
                                reader.getImageMetadata(i));
                        int frameWidth = reader.getWidth(i);
                        int frameHeight = reader.getHeight(i);
                        BufferedImage raw = reader.read(i);
                        if ("restoreToPrevious".equals(
                                metadata.disposal())) {
                            restore = copy(canvas);
                        }
                        graphics.drawImage(raw, metadata.left(),
                                metadata.top(), null);
                        BufferedImage output = scale(canvas, outputWidth,
                                outputHeight);
                        int[] argb = output.getRGB(0, 0, outputWidth,
                                outputHeight, null, 0, outputWidth);
                        consumer.accept(new CpuFrame(outputWidth, outputHeight,
                                elapsed, toRgba(argb)));
                        elapsed += metadata.delayMs();

                        if ("restoreToBackgroundColor".equals(
                                metadata.disposal())) {
                            clear(graphics, metadata.left(), metadata.top(),
                                    frameWidth, frameHeight);
                        } else if ("restoreToPrevious".equals(
                                metadata.disposal())
                                && restore != null) {
                            clear(graphics, 0, 0, logicalWidth, logicalHeight);
                            graphics.drawImage(restore, 0, 0, null);
                        }
                    }
                } finally {
                    graphics.dispose();
                }
            } finally {
                reader.dispose();
            }
        }
    }

    static GifTiming inspect(byte[] data, String label, int maxWidth)
            throws IOException {
        try (ByteArrayInputStream input = new ByteArrayInputStream(data);
                ImageInputStream imageInput =
                        ImageIO.createImageInputStream(input)) {
            Iterator<ImageReader> readers =
                    ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) throw new IOException("No GIF reader available");
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, false, false);
                int count = reader.getNumImages(true);
                if (count <= 0) throw new IOException("GIF without frames: " + label);
                int logicalWidth = 0;
                int logicalHeight = 0;
                IIOMetadata streamMetadata = reader.getStreamMetadata();
                if (streamMetadata != null) {
                    Node root = streamMetadata.getAsTree(
                            "javax_imageio_gif_stream_1.0");
                    for (Node child = root.getFirstChild(); child != null;
                            child = child.getNextSibling()) {
                        if ("LogicalScreenDescriptor".equals(
                                child.getNodeName())) {
                            logicalWidth = intAttribute(child,
                                    "logicalScreenWidth", 0);
                            logicalHeight = intAttribute(child,
                                    "logicalScreenHeight", 0);
                        }
                    }
                }
                if (logicalWidth <= 0 || logicalHeight <= 0) {
                    logicalWidth = reader.getWidth(0);
                    logicalHeight = reader.getHeight(0);
                }
                float scale = Math.min(1f, maxWidth / (float) logicalWidth);
                int outputWidth = Math.max(1,
                        Math.round(logicalWidth * scale));
                int outputHeight = Math.max(1,
                        Math.round(logicalHeight * scale));
                long elapsed = 0L;
                long[] frameEnds = new long[count];
                for (int frame = 0; frame < count; frame++) {
                    elapsed += readFrameMetadata(
                            reader.getImageMetadata(frame)).delayMs();
                    frameEnds[frame] = elapsed;
                }
                return new GifTiming(outputWidth, outputHeight, frameEnds,
                        elapsed);
            } finally {
                reader.dispose();
            }
        }
    }

    private static BufferedImage scale(BufferedImage source, int width,
            int height) {
        if (source.getWidth() == width && source.getHeight() == height) {
            return source;
        }
        BufferedImage scaled = new BufferedImage(width, height,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    private static FrameMetadata readFrameMetadata(IIOMetadata metadata) {
        int left = 0;
        int top = 0;
        long delayMs = 100L;
        String disposal = "none";
        Node root = metadata.getAsTree("javax_imageio_gif_image_1.0");
        for (Node child = root.getFirstChild(); child != null;
                child = child.getNextSibling()) {
            if ("ImageDescriptor".equals(child.getNodeName())) {
                left = intAttribute(child, "imageLeftPosition", 0);
                top = intAttribute(child, "imageTopPosition", 0);
            } else if ("GraphicControlExtension".equals(
                    child.getNodeName())) {
                int centiseconds = intAttribute(child, "delayTime", 10);
                delayMs = centiseconds <= 1
                        ? 100L : centiseconds * 10L;
                NamedNodeMap attributes = child.getAttributes();
                Node method = attributes == null ? null
                        : attributes.getNamedItem("disposalMethod");
                if (method != null) disposal = method.getNodeValue();
            }
        }
        return new FrameMetadata(left, top, delayMs, disposal);
    }

    private static byte[] toRgba(int[] argb) {
        byte[] rgba = new byte[argb.length * 4];
        int output = 0;
        for (int value : argb) {
            rgba[output++] = (byte) ((value >>> 16) & 0xff);
            rgba[output++] = (byte) ((value >>> 8) & 0xff);
            rgba[output++] = (byte) (value & 0xff);
            rgba[output++] = (byte) ((value >>> 24) & 0xff);
        }
        return rgba;
    }

    private static int intAttribute(Node node, String name, int fallback) {
        NamedNodeMap attributes = node.getAttributes();
        Node value = attributes == null ? null
                : attributes.getNamedItem(name);
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value.getNodeValue());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static BufferedImage copy(BufferedImage source) {
        BufferedImage result = new BufferedImage(source.getWidth(),
                source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    private static void clear(Graphics2D graphics, int x, int y, int width,
            int height) {
        Composite previous = graphics.getComposite();
        graphics.setComposite(AlphaComposite.Clear);
        graphics.fillRect(x, y, width, height);
        graphics.setComposite(previous);
    }

    private static String sanitizeThreadLabel(String label) {
        String sanitized = label == null ? "animation"
                : label.replaceAll("[^A-Za-z0-9._-]", "-");
        return sanitized.length() <= 48 ? sanitized
                : sanitized.substring(sanitized.length() - 48);
    }

    @FunctionalInterface
    private interface DataSource {

        byte[] read() throws IOException;
    }

    private record FrameMetadata(int left, int top, long delayMs,
            String disposal) {
    }

    record GifTiming(int width, int height, long[] frameEndMs,
            long durationMs) {
    }

    record CpuFrame(int width, int height, long startMs, byte[] rgba) {
        CpuFrame {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Invalid GIF frame size");
            }
            Objects.requireNonNull(rgba, "rgba");
            if (rgba.length != width * height * 4) {
                throw new IllegalArgumentException("Invalid GIF pixel count");
            }
        }
    }
}
