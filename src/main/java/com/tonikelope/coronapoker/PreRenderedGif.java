/*
 * Copyright (C) 2026 tonikelope
 _              _ _        _
| |_ ___  _ __ (_) | _____| | ___  _ __   ___
| __/ _ \| '_ \| | |/ / _ \ |/ _ \| '_ \ / _ \
| || (_) | | | | |   <  __/ | (_) | |_) |  __/
 \__\___/|_| |_|_|_|\_\___|_|\___/| .__/ \___|
 ____    ___  ____    ___
|___ \  / _ \|___ \  / _ \
  __) || | | | __) || | | |
 / __/ | |_| |/ __/ | |_| |
|_____| \___/|_____| \___/

https://github.com/tonikelope/coronapoker
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tonikelope.coronapoker;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Animated GIF pre-decoded into fully composited frames (per-frame disposal
 * already applied) so playback can index by elapsed clock time instead of
 * decoding on the fly.
 *
 * AWT's GIF animator decodes on the fly and sleeps each frame's delay via
 * {@code Thread.sleep}, never recovering lost time: with Windows' timer
 * granularity (15.6 ms by default, and it varies with global system state) a
 * 20 ms/frame animation can stretch by ~50%. Here frames are decoded up
 * front and the visible frame is picked by elapsed time ({@link #frameAt}),
 * skipping frames when needed, so the total playback duration always
 * matches the GIF's nominal duration.
 */
public class PreRenderedGif {

    private final BufferedImage[] frames;

    // Cumulative timeline: frame i is visible while elapsed < frame_end_ms[i].
    private final long[] frame_end_ms;

    private final int width;

    private final int height;

    private PreRenderedGif(BufferedImage[] frames, long[] frame_end_ms, int width, int height) {
        this.frames = frames;
        this.frame_end_ms = frame_end_ms;
        this.width = width;
        this.height = height;
    }

    /**
     * Builds a PreRenderedGif from frames already rendered elsewhere (e.g. by
     * {@link CardFlipAnimator}), splitting the total duration evenly across
     * them. Reuses the same catch-up playback engine as decoded GIFs.
     *
     * @param frames pre-rendered frames, in playback order
     * @param total_ms total playback duration to spread across the frames
     * @return a PreRenderedGif ready for {@link #frameAt}-based playback
     */
    public static PreRenderedGif fromFrames(BufferedImage[] frames, int total_ms) {
        long[] end = new long[frames.length];
        for (int i = 0; i < frames.length; i++) {
            end[i] = Math.round((i + 1) * (double) total_ms / frames.length);
        }
        return new PreRenderedGif(frames, end, frames[0].getWidth(), frames[0].getHeight());
    }

    /** @return the number of pre-rendered frames. */
    public int getFrameCount() {
        return frames.length;
    }

    /** @return the composited frame at index {@code i}. */
    public BufferedImage getFrame(int i) {
        return frames[i];
    }

    /** @return the GIF's logical width, in pixels. */
    public int getWidth() {
        return width;
    }

    /** @return the GIF's logical height, in pixels. */
    public int getHeight() {
        return height;
    }

    /** @return the GIF's total nominal playback duration, in milliseconds. */
    public long getTotalMs() {
        return frame_end_ms[frame_end_ms.length - 1];
    }

    /** @return the frame that should be visible after {@code elapsed_ms} of playback. */
    public int frameAt(long elapsed_ms) {
        return frameAt(frame_end_ms, elapsed_ms);
    }

    // Frame due at the given elapsed time. If a tick arrives late, this jumps
    // straight to the correct frame (catch-up): the animation drops frames
    // rather than slowing down. Package-private so the AAA unit test can
    // call it directly.
    static int frameAt(long[] frame_end_ms, long elapsed_ms) {

        for (int i = 0; i < frame_end_ms.length; i++) {
            if (elapsed_ms < frame_end_ms[i]) {
                return i;
            }
        }

        return frame_end_ms.length - 1;
    }

    /** {@link #decode(URL, long)} with no memory cap. */
    public static PreRenderedGif decode(URL url) throws IOException {
        return decode(url, Long.MAX_VALUE);
    }

    /**
     * Like {@link #decode(URL)} but with a memory cap: the storage estimate
     * is computed from the metadata-only pass (without decoding a single
     * pixel), and if it exceeds {@code max_bytes} an IOException is thrown
     * so the caller can fall back to the legacy playback path without
     * having paid for the decode.
     *
     * @param url location of the GIF to decode
     * @param max_bytes upper bound on the estimated decoded size, in bytes
     * @return the pre-decoded, pre-composited GIF
     * @throws IOException if the GIF can't be read, has no frames, or its
     * estimated storage exceeds {@code max_bytes}
     */
    public static PreRenderedGif decode(URL url, long max_bytes) throws IOException {

        try (InputStream is = url.openStream(); ImageInputStream iis = ImageIO.createImageInputStream(is)) {

            Iterator<ImageReader> it = ImageIO.getImageReadersByFormatName("gif");

            if (!it.hasNext()) {
                throw new IOException("No GIF ImageReader available");
            }

            ImageReader reader = it.next();

            try {
                reader.setInput(iis, false, false);

                int n = reader.getNumImages(true);

                if (n <= 0) {
                    throw new IOException("GIF has no frames: " + url);
                }

                // Metadata-only pass (no pixel decoding): per-frame geometry, delay and disposal.
                int[] left = new int[n];
                int[] top = new int[n];
                int[] fw = new int[n];
                int[] fh = new int[n];
                long[] delay_ms = new long[n];
                String[] disposal = new String[n];

                for (int i = 0; i < n; i++) {
                    readFrameMetadata(reader.getImageMetadata(i), i, left, top, delay_ms, disposal);
                    fw[i] = reader.getWidth(i);
                    fh[i] = reader.getHeight(i);
                }

                // Logical screen size: from the stream metadata, falling back to the frames' max extent.
                int lw = 0;
                int lh = 0;

                IIOMetadata stream_md = reader.getStreamMetadata();

                if (stream_md != null) {
                    Node root = stream_md.getAsTree("javax_imageio_gif_stream_1.0");
                    for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
                        if ("LogicalScreenDescriptor".equals(child.getNodeName())) {
                            lw = parseIntAttribute(child, "logicalScreenWidth", 0);
                            lh = parseIntAttribute(child, "logicalScreenHeight", 0);
                        }
                    }
                }

                for (int i = 0; i < n; i++) {
                    lw = Math.max(lw, left[i] + fw[i]);
                    lh = Math.max(lh, top[i] + fh[i]);
                }

                long estimate = estimateStorageBytes(left, top, fw, fh, disposal, lw, lh);

                if (estimate > max_bytes) {
                    throw new IOException("GIF pre-render estimate " + estimate + " bytes exceeds cap " + max_bytes + ": " + url);
                }

                // Single decode+composite pass: each raw frame is discarded
                // immediately (peak memory roughly equals live memory).
                BufferedImage[] frames = new BufferedImage[n];
                long[] frame_end_ms = new long[n];

                // Lazy canvas: spin GIFs (full frames with background disposal)
                // are self-contained and stored as-is straight from the reader
                // (8-bit indexed, ~4x less RAM than ARGB) without ever touching
                // the compositor.
                BufferedImage canvas = null;
                Graphics2D g = null;
                boolean canvas_clean = true;

                try {
                    long t = 0;
                    BufferedImage restore = null;
                    boolean restore_clean = true;

                    for (int i = 0; i < n; i++) {

                        BufferedImage raw = reader.read(i);

                        t += delay_ms[i];
                        frame_end_ms[i] = t;

                        boolean full = (left[i] == 0 && top[i] == 0 && fw[i] == lw && fh[i] == lh);
                        boolean to_background = "restoreToBackgroundColor".equals(disposal[i]);

                        if (canvas_clean && full && to_background) {
                            // Self-contained frame: the composited result IS the raw
                            // frame, and its disposal leaves the canvas clean again.
                            frames[i] = raw;
                            continue;
                        }

                        if (canvas == null) {
                            // Any earlier frames left the canvas clean, so starting
                            // transparent is the correct state.
                            canvas = new BufferedImage(lw, lh, BufferedImage.TYPE_INT_ARGB);
                            g = canvas.createGraphics();
                        }

                        boolean was_clean = canvas_clean;

                        if ("restoreToPrevious".equals(disposal[i])) {
                            restore = copyOf(canvas);
                            restore_clean = was_clean;
                        }

                        g.drawImage(raw, left[i], top[i], null);

                        frames[i] = copyOf(canvas);

                        if (to_background) {
                            clearRect(g, left[i], top[i], fw[i], fh[i]);
                            canvas_clean = full || was_clean;
                        } else if ("restoreToPrevious".equals(disposal[i]) && restore != null) {
                            clearRect(g, 0, 0, lw, lh);
                            g.drawImage(restore, 0, 0, null);
                            canvas_clean = restore_clean;
                        } else {
                            canvas_clean = false;
                        }
                    }
                } finally {
                    if (g != null) {
                        g.dispose();
                    }
                }

                return new PreRenderedGif(frames, frame_end_ms, lw, lh);

            } finally {
                reader.dispose();
            }
        }
    }

    // Estimates the memory the decoded GIF will retain, computed from
    // metadata ONLY. Mirrors the canvas_clean state machine of the decode
    // loop: self-contained frames cost their indexed size (1 byte/pixel,
    // what the GIF reader returns), everything else a full ARGB copy of the
    // whole canvas; the compositor canvas and the restoreToPrevious snapshot
    // are each counted once, if they ever come into existence.
    // Package-private so the AAA unit test can call it directly.
    static long estimateStorageBytes(int[] left, int[] top, int[] fw, int[] fh, String[] disposal, int lw, int lh) {

        long bytes = 0;
        boolean canvas_clean = true;
        boolean canvas_counted = false;
        boolean restore_counted = false;

        for (int i = 0; i < left.length; i++) {

            boolean full = (left[i] == 0 && top[i] == 0 && fw[i] == lw && fh[i] == lh);
            boolean to_background = "restoreToBackgroundColor".equals(disposal[i]);

            if (canvas_clean && full && to_background) {
                bytes += (long) fw[i] * fh[i];
                continue;
            }

            if (!canvas_counted) {
                canvas_counted = true;
                bytes += (long) lw * lh * 4L;
            }

            bytes += (long) lw * lh * 4L;

            boolean was_clean = canvas_clean;

            if (to_background) {
                canvas_clean = full || was_clean;
            } else if ("restoreToPrevious".equals(disposal[i])) {
                if (!restore_counted) {
                    restore_counted = true;
                    bytes += (long) lw * lh * 4L;
                }
                canvas_clean = was_clean;
            } else {
                canvas_clean = false;
            }
        }

        return bytes;
    }

    private static void readFrameMetadata(IIOMetadata md, int i, int[] left, int[] top, long[] delay_ms, String[] disposal) {

        // Defaults for missing extensions (minimal GIFs without a GraphicControlExtension).
        left[i] = 0;
        top[i] = 0;
        delay_ms[i] = 100;
        disposal[i] = "none";

        Node root = md.getAsTree("javax_imageio_gif_image_1.0");

        for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {

            if ("ImageDescriptor".equals(child.getNodeName())) {

                left[i] = parseIntAttribute(child, "imageLeftPosition", 0);
                top[i] = parseIntAttribute(child, "imageTopPosition", 0);

            } else if ("GraphicControlExtension".equals(child.getNodeName())) {

                // delayTime is in centiseconds. Browser convention: delays
                // <= 1cs are treated as 100 ms.
                int delay_cs = parseIntAttribute(child, "delayTime", 10);
                delay_ms[i] = (delay_cs <= 1) ? 100 : delay_cs * 10L;

                NamedNodeMap attrs = child.getAttributes();
                Node disp = (attrs != null) ? attrs.getNamedItem("disposalMethod") : null;

                if (disp != null) {
                    disposal[i] = disp.getNodeValue();
                }
            }
        }
    }

    private static int parseIntAttribute(Node node, String name, int def) {

        NamedNodeMap attrs = node.getAttributes();

        if (attrs == null) {
            return def;
        }

        Node attr = attrs.getNamedItem(name);

        if (attr == null) {
            return def;
        }

        try {
            return Integer.parseInt(attr.getNodeValue());
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    private static BufferedImage copyOf(BufferedImage src) {

        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = copy.createGraphics();

        try {
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }

        return copy;
    }

    private static void clearRect(Graphics2D g, int x, int y, int w, int h) {

        Composite old = g.getComposite();

        g.setComposite(AlphaComposite.Clear);
        g.fillRect(x, y, w, h);
        g.setComposite(old);
    }

}
