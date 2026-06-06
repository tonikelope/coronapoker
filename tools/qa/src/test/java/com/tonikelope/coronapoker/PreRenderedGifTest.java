/*
 * PreRenderedGif test: clock-indexed (catch-up) frame selection + GIF decode
 * round-trip.
 *
 * The card-flip GIFs (31 frames @ 20 ms) are played by selecting the visible
 * frame from the elapsed wall-clock time instead of sleeping per-frame delays
 * (the AWT Toolkit animator sleeps and never catches up, so the Windows timer
 * granularity stretches the animation). The hard invariants are:
 *
 *  - frameAt(elapsed) maps the elapsed time onto the GIF's cumulative frame
 *    timeline: late ticks must JUMP to the correct frame (drop frames, never
 *    stretch duration), and anything at/past the end clamps to the last frame.
 *  - decode() must reproduce frame count, logical size, per-frame delays
 *    (cumulative total) and pixel content of a known animated GIF.
 */
package com.tonikelope.coronapoker;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PreRenderedGifTest {

    // ---- frameAt: clock-indexed frame selection with catch-up ----

    @Test
    public void playbackStartsAtFrameZero() {
        long[] timeline = {20, 40, 60};
        assertEquals(0, PreRenderedGif.frameAt(timeline, 0));
        assertEquals(0, PreRenderedGif.frameAt(timeline, 19));
    }

    @Test
    public void exactBoundaryAdvancesToNextFrame() {
        // Frame i is visible while elapsed < end[i]: at exactly 20 ms frame 0
        // has consumed its delay and frame 1 must be on screen.
        long[] timeline = {20, 40, 60};
        assertEquals(1, PreRenderedGif.frameAt(timeline, 20));
        assertEquals(2, PreRenderedGif.frameAt(timeline, 40));
    }

    @Test
    public void lateTickJumpsToCorrectFrame() {
        // Catch-up: a tick arriving at 95 ms (e.g. coarse Windows timer) must
        // jump straight to frame 4, skipping the intermediate frames, so the
        // total duration never stretches.
        long[] timeline = {20, 40, 60, 80, 100};
        assertEquals(4, PreRenderedGif.frameAt(timeline, 95));
    }

    @Test
    public void elapsedPastEndClampsToLastFrame() {
        long[] timeline = {20, 40, 60};
        assertEquals(2, PreRenderedGif.frameAt(timeline, 60));
        assertEquals(2, PreRenderedGif.frameAt(timeline, 10_000));
    }

    @Test
    public void negativeElapsedShowsFirstFrame() {
        long[] timeline = {20, 40, 60};
        assertEquals(0, PreRenderedGif.frameAt(timeline, -5));
    }

    @Test
    public void variableDelaysAreHonored() {
        // Non-uniform GIF delays: the timeline is cumulative, not a constant step.
        long[] timeline = {100, 120, 220};
        assertEquals(0, PreRenderedGif.frameAt(timeline, 99));
        assertEquals(1, PreRenderedGif.frameAt(timeline, 110));
        assertEquals(2, PreRenderedGif.frameAt(timeline, 130));
    }

    // ---- decode: round-trip against a generated animated GIF ----

    @Test
    public void decodeRoundTripPreservesFramesDelaysAndPixels() throws Exception {

        Color[] colors = {Color.RED, Color.GREEN, Color.BLUE, Color.WHITE};

        BufferedImage[] frames = new BufferedImage[colors.length];

        for (int i = 0; i < colors.length; i++) {
            frames[i] = new BufferedImage(40, 60, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = frames[i].createGraphics();
            try {
                g.setColor(colors[i]);
                g.fillRect(0, 0, 40, 60);
            } finally {
                g.dispose();
            }
        }

        File gif = File.createTempFile("prerendered_gif_test", ".gif");

        try {
            writeAnimatedGif(gif, frames, 5, "none"); // 5 cs = 50 ms per frame

            PreRenderedGif anim = PreRenderedGif.decode(gif.toURI().toURL());

            assertEquals(4, anim.getFrameCount());
            assertEquals(40, anim.getWidth());
            assertEquals(60, anim.getHeight());
            assertEquals(200L, anim.getTotalMs());

            for (int i = 0; i < colors.length; i++) {
                assertEquals(colors[i].getRGB(), anim.getFrame(i).getRGB(20, 30),
                        "center pixel of frame " + i);
            }

            // Clock indexing against the real decoded timeline (50 ms/frame)
            assertEquals(0, anim.frameAt(0));
            assertEquals(1, anim.frameAt(75));
            assertEquals(3, anim.frameAt(199));
            assertEquals(3, anim.frameAt(5_000));

        } finally {
            gif.delete();
        }
    }

    @Test
    public void selfContainedFramesSkipRecomposition() throws Exception {

        // Full-screen frames with disposal "restoreToBackgroundColor" (the card
        // flip GIF layout) are self-contained: decode must keep the reader's
        // frames as-is — no ARGB recomposition copies (the memory/CPU fast path)
        // — while pixels still match frame by frame.
        Color[] colors = {Color.RED, Color.GREEN, Color.BLUE};

        BufferedImage[] frames = new BufferedImage[colors.length];

        for (int i = 0; i < colors.length; i++) {
            frames[i] = new BufferedImage(40, 60, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = frames[i].createGraphics();
            try {
                g.setColor(colors[i]);
                g.fillRect(0, 0, 40, 60);
            } finally {
                g.dispose();
            }
        }

        File gif = File.createTempFile("prerendered_gif_selfcontained_test", ".gif");

        try {
            writeAnimatedGif(gif, frames, 2, "restoreToBackgroundColor"); // 2 cs = 20 ms, like the deck GIFs

            PreRenderedGif anim = PreRenderedGif.decode(gif.toURI().toURL());

            assertEquals(3, anim.getFrameCount());
            assertEquals(60L, anim.getTotalMs());

            for (int i = 0; i < colors.length; i++) {
                assertEquals(colors[i].getRGB(), anim.getFrame(i).getRGB(20, 30),
                        "center pixel of frame " + i);
                assertNotEquals(BufferedImage.TYPE_INT_ARGB, anim.getFrame(i).getType(),
                        "frame " + i + " must be the reader's own image (fast path), not an ARGB recomposition");
            }

        } finally {
            gif.delete();
        }
    }

    // ---- estimateStorageBytes: RAM prediction from metadata only ----

    @Test
    public void storageEstimateForSelfContainedLayoutIsIndexedSize() {
        // Full-screen frames + restoreToBackgroundColor (the shuffle/card-flip
        // layout): every frame takes the fast path, 1 byte per pixel, and the
        // compositor canvas is never allocated.
        int n = 86, w = 960, h = 540;
        int[] left = new int[n], top = new int[n], fw = new int[n], fh = new int[n];
        String[] disposal = new String[n];
        for (int i = 0; i < n; i++) {
            fw[i] = w;
            fh[i] = h;
            disposal[i] = "restoreToBackgroundColor";
        }

        assertEquals((long) n * w * h,
                PreRenderedGif.estimateStorageBytes(left, top, fw, fh, disposal, w, h));
    }

    @Test
    public void storageEstimateForPartialFramesIsArgbRecomposition() {
        // Partial frames force the compositor: one ARGB canvas plus one ARGB
        // copy of the full logical screen per frame.
        int n = 4, lw = 100, lh = 80;
        int[] left = {0, 10, 20, 30}, top = {0, 5, 10, 15};
        int[] fw = {50, 50, 50, 50}, fh = {40, 40, 40, 40};
        String[] disposal = {"none", "none", "none", "none"};

        long canvas = (long) lw * lh * 4;
        long per_frame = (long) lw * lh * 4;

        assertEquals(canvas + n * per_frame,
                PreRenderedGif.estimateStorageBytes(left, top, fw, fh, disposal, lw, lh));
    }

    @Test
    public void decodeRejectsGifsOverTheMemoryCap() throws Exception {

        BufferedImage frame = new BufferedImage(40, 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = frame.createGraphics();
        try {
            g.setColor(Color.RED);
            g.fillRect(0, 0, 40, 60);
        } finally {
            g.dispose();
        }

        File gif = File.createTempFile("prerendered_gif_cap_test", ".gif");

        try {
            writeAnimatedGif(gif, new BufferedImage[]{frame, frame}, 5, "restoreToBackgroundColor");

            // Two self-contained 40x60 frames = 4800 bytes estimated
            assertThrows(IOException.class,
                    () -> PreRenderedGif.decode(gif.toURI().toURL(), 4_799L));

            PreRenderedGif anim = PreRenderedGif.decode(gif.toURI().toURL(), 4_800L);
            assertEquals(2, anim.getFrameCount());

        } finally {
            gif.delete();
        }
    }

    private static void writeAnimatedGif(File file, BufferedImage[] frames, int delay_cs, String disposal) throws Exception {

        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();

        try (ImageOutputStream ios = ImageIO.createImageOutputStream(file)) {

            writer.setOutput(ios);

            ImageWriteParam param = writer.getDefaultWriteParam();

            ImageTypeSpecifier type = ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_RGB);

            writer.prepareWriteSequence(null);

            for (BufferedImage frame : frames) {

                IIOMetadata md = writer.getDefaultImageMetadata(type, param);

                String fmt = "javax_imageio_gif_image_1.0";

                IIOMetadataNode root = new IIOMetadataNode(fmt);

                IIOMetadataNode gce = new IIOMetadataNode("GraphicControlExtension");
                gce.setAttribute("disposalMethod", disposal);
                gce.setAttribute("userInputFlag", "FALSE");
                gce.setAttribute("transparentColorFlag", "FALSE");
                gce.setAttribute("delayTime", Integer.toString(delay_cs));
                gce.setAttribute("transparentColorIndex", "0");
                root.appendChild(gce);

                md.mergeTree(fmt, root);

                writer.writeToSequence(new IIOImage(frame, null, md), param);
            }

            writer.endWriteSequence();

        } finally {
            writer.dispose();
        }
    }

}
