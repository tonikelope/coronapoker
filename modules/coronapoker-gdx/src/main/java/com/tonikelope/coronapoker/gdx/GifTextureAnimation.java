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
import com.badlogic.gdx.utils.Disposable;
import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.metadata.IIOMetadata;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/** Pre-decodes a CoronaPoker GIF into time-addressable GPU textures. */
final class GifTextureAnimation implements Disposable {

    private final Texture[] frames;
    private final long[] frameEndMs;
    private final long durationMs;
    private final int width;
    private final int height;

    private GifTextureAnimation(Texture[] frames, long[] frameEndMs, int width, int height) {
        this.frames = frames;
        this.frameEndMs = frameEndMs;
        this.durationMs = frameEndMs[frameEndMs.length - 1];
        this.width = width;
        this.height = height;
    }

    static GifTextureAnimation load(String path, int maxWidth) throws IOException {
        try (InputStream input = Gdx.files.internal(path).read()) {
            return load(input, path, maxWidth);
        }
    }

    static GifTextureAnimation load(byte[] data, String label, int maxWidth)
            throws IOException {
        try (InputStream input = new ByteArrayInputStream(data)) {
            return load(input, label, maxWidth);
        }
    }

    private static GifTextureAnimation load(InputStream input, String label,
            int maxWidth) throws IOException {
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) {
                throw new IOException("No GIF reader available");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, false, false);
                int count = reader.getNumImages(true);
                if (count <= 0) {
                    throw new IOException("GIF without frames: " + label);
                }

                int[] left = new int[count];
                int[] top = new int[count];
                int[] frameWidth = new int[count];
                int[] frameHeight = new int[count];
                long[] delayMs = new long[count];
                String[] disposal = new String[count];
                int logicalWidth = 0;
                int logicalHeight = 0;

                IIOMetadata streamMetadata = reader.getStreamMetadata();
                if (streamMetadata != null) {
                    Node root = streamMetadata.getAsTree("javax_imageio_gif_stream_1.0");
                    for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
                        if ("LogicalScreenDescriptor".equals(child.getNodeName())) {
                            logicalWidth = intAttribute(child, "logicalScreenWidth", 0);
                            logicalHeight = intAttribute(child, "logicalScreenHeight", 0);
                        }
                    }
                }

                for (int i = 0; i < count; i++) {
                    readFrameMetadata(reader.getImageMetadata(i), i, left, top, delayMs, disposal);
                    frameWidth[i] = reader.getWidth(i);
                    frameHeight[i] = reader.getHeight(i);
                    logicalWidth = Math.max(logicalWidth, left[i] + frameWidth[i]);
                    logicalHeight = Math.max(logicalHeight, top[i] + frameHeight[i]);
                }

                float scale = Math.min(1f, maxWidth / (float) logicalWidth);
                int outputWidth = Math.max(1, Math.round(logicalWidth * scale));
                int outputHeight = Math.max(1, Math.round(logicalHeight * scale));
                Texture[] textures = new Texture[count];
                long[] frameEndMs = new long[count];
                BufferedImage canvas = new BufferedImage(
                        logicalWidth, logicalHeight, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = canvas.createGraphics();
                BufferedImage restore = null;
                long elapsed = 0L;

                try {
                    graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                            RenderingHints.VALUE_RENDER_QUALITY);
                    for (int i = 0; i < count; i++) {
                        BufferedImage raw = reader.read(i);
                        if ("restoreToPrevious".equals(disposal[i])) {
                            restore = copy(canvas);
                        }
                        graphics.drawImage(raw, left[i], top[i], null);
                        textures[i] = upload(canvas, outputWidth, outputHeight);
                        elapsed += delayMs[i];
                        frameEndMs[i] = elapsed;

                        if ("restoreToBackgroundColor".equals(disposal[i])) {
                            clear(graphics, left[i], top[i], frameWidth[i], frameHeight[i]);
                        } else if ("restoreToPrevious".equals(disposal[i]) && restore != null) {
                            clear(graphics, 0, 0, logicalWidth, logicalHeight);
                            graphics.drawImage(restore, 0, 0, null);
                        }
                    }
                } catch (Exception failure) {
                    for (Texture texture : textures) {
                        if (texture != null) {
                            texture.dispose();
                        }
                    }
                    throw failure;
                } finally {
                    graphics.dispose();
                }

                System.out.printf("GIF GPU: %s | %d frames | %d ms | %dx%d%n",
                        label, count, elapsed, outputWidth, outputHeight);
                return new GifTextureAnimation(textures, frameEndMs, outputWidth, outputHeight);
            } finally {
                reader.dispose();
            }
        }
    }

    Texture frameAt(float elapsedSeconds, boolean loop) {
        long elapsed = Math.max(0L, Math.round(elapsedSeconds * 1000f));
        if (loop && durationMs > 0L) {
            elapsed %= durationMs;
        } else {
            elapsed = Math.min(elapsed, durationMs - 1L);
        }
        for (int i = 0; i < frameEndMs.length; i++) {
            if (elapsed < frameEndMs[i]) {
                return frames[i];
            }
        }
        return frames[frames.length - 1];
    }

    float durationSeconds() {
        return durationMs / 1000f;
    }

    float frameStartSeconds(int oneBasedFrame) {
        if (oneBasedFrame <= 1) {
            return 0f;
        }
        int previousFrame = Math.min(oneBasedFrame - 2, frameEndMs.length - 1);
        return frameEndMs[previousFrame] / 1000f;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    int frameCount() {
        return frames.length;
    }

    @Override
    public void dispose() {
        for (Texture frame : frames) {
            frame.dispose();
        }
    }

    private static Texture upload(BufferedImage source, int width, int height) {
        BufferedImage image = source;
        if (source.getWidth() != width || source.getHeight() != height) {
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D scaled = image.createGraphics();
            try {
                scaled.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                scaled.drawImage(source, 0, 0, width, height, null);
            } finally {
                scaled.dispose();
            }
        }

        int[] argb = image.getRGB(0, 0, width, height, null, 0, width);
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        ByteBuffer pixels = pixmap.getPixels();
        pixels.clear();
        for (int value : argb) {
            pixels.put((byte) ((value >>> 16) & 0xff));
            pixels.put((byte) ((value >>> 8) & 0xff));
            pixels.put((byte) (value & 0xff));
            pixels.put((byte) ((value >>> 24) & 0xff));
        }
        pixels.flip();
        Texture texture = new Texture(pixmap, true);
        texture.setFilter(TextureFilter.MipMapLinearLinear, TextureFilter.Linear);
        pixmap.dispose();
        return texture;
    }

    private static void readFrameMetadata(IIOMetadata metadata, int index,
            int[] left, int[] top, long[] delayMs, String[] disposal) {
        left[index] = 0;
        top[index] = 0;
        delayMs[index] = 100L;
        disposal[index] = "none";
        Node root = metadata.getAsTree("javax_imageio_gif_image_1.0");
        for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            if ("ImageDescriptor".equals(child.getNodeName())) {
                left[index] = intAttribute(child, "imageLeftPosition", 0);
                top[index] = intAttribute(child, "imageTopPosition", 0);
            } else if ("GraphicControlExtension".equals(child.getNodeName())) {
                int centiseconds = intAttribute(child, "delayTime", 10);
                delayMs[index] = centiseconds <= 1 ? 100L : centiseconds * 10L;
                NamedNodeMap attributes = child.getAttributes();
                Node method = attributes == null ? null : attributes.getNamedItem("disposalMethod");
                if (method != null) {
                    disposal[index] = method.getNodeValue();
                }
            }
        }
    }

    private static int intAttribute(Node node, String name, int fallback) {
        NamedNodeMap attributes = node.getAttributes();
        Node value = attributes == null ? null : attributes.getNamedItem(name);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.getNodeValue());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static BufferedImage copy(BufferedImage source) {
        BufferedImage result = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    private static void clear(Graphics2D graphics, int x, int y, int width, int height) {
        Composite previous = graphics.getComposite();
        graphics.setComposite(AlphaComposite.Clear);
        graphics.fillRect(x, y, width, height);
        graphics.setComposite(previous);
    }
}

