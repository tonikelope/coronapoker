/*
 * Copyright (C) 2025 tonikelope
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
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

/**
 * Swing/Java2D card flip animation (replaces the pre-generated gifsicle spin GIFs).
 *
 * The flip is a 180-degree 3D rotation around the vertical axis with PERSPECTIVE (the card
 * reads as a trapezoid, not a flat squeeze). It's solved with a per-pixel inverse warp: each
 * output pixel's exact source coordinate in the JPG is computed with a closed-form formula and
 * sampled with bilinear interpolation — no replicated "strips", so it stays as sharp as the
 * native JPG.
 *
 * Frames are generated at the monitor's PHYSICAL HiDPI resolution so the system never upscales
 * them, and the card is drawn at or below native JPG size.
 *
 * The resulting frames are wrapped in a {@link PreRenderedGif} and played by the existing
 * catch-up engine (same timing, hooks and sync as regular GIFs).
 *
 * @author tonikelope
 */
public class CardFlipAnimator {

    // Fixed perspective (value validated in the proof of concept). Lower = more 3D.
    private static final double PERSPECTIVE = 45.0;
    // Canvas slack around the card to accommodate the trapezoid shape.
    private static final double MARGIN = 1.5;
    // Warp supersampling (smooths the card's outline).
    private static final int SS = 2;

    // Cache of source images (face + back) rounded at native resolution, keyed by deck+card.
    // Cleared whenever the deck changes.
    private static final ConcurrentHashMap<String, BufferedImage> SRC_CACHE = new ConcurrentHashMap<>();
    private static volatile String CACHE_BARAJA = null;
    private static volatile String CACHE_TRASERA = null;

    /**
     * HiDPI scale of the primary monitor (1.0, 1.25, 2.0...). Frames are generated at this
     * density so they land 1:1 in physical pixels.
     *
     * @return clamped scale factor in [1.0, 3.0], or 1.0 if it can't be determined
     */
    public static double screenDensity() {
        try {
            double d = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                    .getDefaultConfiguration().getDefaultTransform().getScaleX();
            return Math.max(1.0, Math.min(3.0, d));
        } catch (Exception ex) {
            return 1.0;
        }
    }

    /**
     * Generates a card flip animation as a {@link PreRenderedGif}.
     *
     * @param baraja current deck (GameFrame.BARAJA)
     * @param valor_palo card key, e.g. "A_P" (never "trasera" — the back is loaded separately)
     * @param card_w_logical logical width of the static card (Card.CARD_WIDTH)
     * @param card_h_logical logical height of the static card (Card.CARD_HEIGHT)
     * @param corner_logical logical corner radius (Card.CARD_CORNER)
     * @param duration_ms total flip duration
     * @param num_frames number of frames to generate
     * @param zoom zoom factor over the static card size (1.0 = pixel-perfect alignment;
     * &gt;1.0 draws the card larger for a zoom-in effect)
     * @param top_half compact view: flip only the card's TOP HALF (matching the split static
     * view) instead of the whole card
     * @return the flip animation, or null if the source images fail to load
     */
    public static PreRenderedGif generate(String baraja, String valor_palo,
            int card_w_logical, int card_h_logical, int corner_logical,
            int duration_ms, int num_frames, float zoom, boolean top_half) {

        try {
            BufferedImage front = cachedFace(baraja, valor_palo, card_w_logical, corner_logical);
            BufferedImage back = cachedBack(card_w_logical, corner_logical);
            if (front == null || back == null) {
                return null;
            }

            // Compact view: the static card shows only its TOP HALF (native-res crop, no
            // scaling). Flattening the frame to match would distort the warp's trapezoid (the
            // card would come out too wide and squat), so instead we flip a card that's ALREADY
            // half: crop the source to its top half and shrink the canvas to match. The warp
            // then produces the correct trapezoid for a mini card, and the last frame lines up
            // with the static one. Width is unchanged (the flip rotates around the vertical
            // axis, which compresses width, not height).
            int card_h_eff = card_h_logical;
            if (top_half) {
                front = topHalf(front, corner_logical, card_w_logical);
                back = topHalf(back, corner_logical, card_w_logical);
                card_h_eff = card_h_logical / 2;
            }

            double dens = screenDensity();
            // Card and canvas at PHYSICAL resolution. The card is drawn at CARD * zoom: with
            // zoom=1 it matches the static card's exact size (pixel-perfect handoff); with
            // zoom>1 it's drawn larger (zoom-in effect) and the canvas grows proportionally so
            // the flip stays centered on the static card.
            int drawn_w_logical = Math.round(card_w_logical * zoom);
            int drawn_h_logical = Math.round(card_h_eff * zoom);
            int draw_w = Math.round(drawn_w_logical * (float) dens);
            int draw_h = Math.round(drawn_h_logical * (float) dens);
            int canvas_w = Math.round(canvasWidth(card_w_logical, zoom) * (float) dens);
            int canvas_h = Math.round(canvasHeight(card_h_eff, zoom) * (float) dens);

            // Quality mode (default): full SS supersampling, as before. Performance mode: warp
            // without supersampling (ss=1, ~1/4 the per-pixel loop cost; the final downscale
            // also becomes a free 1:1 copy).
            int ss = GameFrame.ANIM_CALIDAD ? SS : 1;
            BufferedImage[] frames = new BufferedImage[num_frames];
            for (int i = 0; i < num_frames; i++) {
                double ang = i * 180.0 / (num_frames - 1);
                frames[i] = renderFlipImage(front, back, ang, PERSPECTIVE, draw_w, draw_h, canvas_w, canvas_h, ss);
            }
            return PreRenderedGif.fromFrames(frames, duration_ms);

        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Logical canvas width for the flip animation (used to size the label). The margin is
     * symmetric and even around the drawn card (canvas = drawn + 2*margin) so overlay centering
     * on the static card lands on an integer pixel. With zoom=1 this matches the original
     * pixel-perfect canvas exactly.
     */
    public static int canvasWidth(int card_w_logical, float zoom) {
        int drawn = Math.round(card_w_logical * zoom);
        int margin = Math.round(drawn * (float) (MARGIN - 1) / 2f);
        return drawn + 2 * margin;
    }

    /** Logical canvas height for the flip animation; see {@link #canvasWidth}. */
    public static int canvasHeight(int card_h_logical, float zoom) {
        int drawn = Math.round(card_h_logical * zoom);
        int margin = Math.round(drawn * (float) (MARGIN - 1) / 2f);
        return drawn + 2 * margin;
    }

    /** Invalidates the source cache if the deck or the selected card back changed. */
    private static void ensureCacheValid(String baraja) {
        if (!java.util.Objects.equals(baraja, CACHE_BARAJA)
                || !java.util.Objects.equals(GameFrame.TRASERA, CACHE_TRASERA)) {
            SRC_CACHE.clear();
            CACHE_BARAJA = baraja;
            CACHE_TRASERA = GameFrame.TRASERA;
        }
    }

    /** Card face (deck JPG), rounded at native resolution and cached. */
    private static BufferedImage cachedFace(String baraja, String valor_palo,
            int card_w_logical, int corner_logical) throws Exception {

        ensureCacheValid(baraja);

        String key = "face:" + valor_palo;
        BufferedImage cached = SRC_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        URL url = CardFlipAnimator.class.getResource("/images/decks/" + baraja + "/" + valor_palo + ".jpg");
        if (url == null) {
            return null;
        }
        BufferedImage raw = ImageIO.read(url);
        if (raw == null) {
            return null;
        }
        int radius = Math.max(1, Math.round(raw.getWidth() * (corner_logical / (float) card_w_logical)));
        BufferedImage rounded = rounded(raw, radius);
        SRC_CACHE.put(key, rounded);
        return rounded;
    }

    /** Global card back (game deck or mod), rounded at native resolution and cached. */
    private static BufferedImage cachedBack(int card_w_logical, int corner_logical) throws Exception {

        String key = "back:" + GameFrame.TRASERA;
        BufferedImage cached = SRC_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        BufferedImage raw = loadTraseraRaw();
        if (raw == null) {
            return null;
        }
        int radius = Math.max(1, Math.round(raw.getWidth() * (corner_logical / (float) card_w_logical)));
        BufferedImage rounded = rounded(raw, radius);
        SRC_CACHE.put(key, rounded);
        return rounded;
    }

    /** Loads the selected card back (game deck or mod) at native resolution. */
    private static BufferedImage loadTraseraRaw() throws Exception {
        String baraja = GameFrame.TRASERA;
        // "default" (or an unrecognized value): the back follows the current deck.
        if (baraja == null || !Card.BARAJAS.containsKey(baraja)) {
            baraja = GameFrame.BARAJA;
        }
        boolean mod = false;
        try {
            mod = (boolean) ((Object[]) Card.BARAJAS.get(baraja))[1];
        } catch (Exception ignore) {
        }
        if (mod) {
            java.io.File f = new java.io.File(Helpers.getCurrentJarParentPath()
                    + "/mod/decks/" + baraja + "/trasera.jpg");
            if (f.exists()) {
                return ImageIO.read(f);
            }
        } else if (baraja != null) {
            URL res = CardFlipAnimator.class.getResource("/images/decks/" + baraja + "/trasera.jpg");
            if (res != null) {
                return ImageIO.read(res);
            }
        }
        URL def = CardFlipAnimator.class.getResource("/images/decks/" + GameFrame.BARAJA_DEFAULT + "/trasera.jpg");
        return def != null ? ImageIO.read(def) : null;
    }

    /** Invalidates the source cache; call when the deck or card back changes. */
    public static void clearCache() {
        SRC_CACHE.clear();
        CACHE_BARAJA = null;
        CACHE_TRASERA = null;
    }

    /**
     * Crops the TOP HALF at native resolution (first h/2 pixel rows, unscaled) for the compact
     * flip view — the same crop the split static card shows. Copies pixels (not getSubimage) so
     * the cached source raster isn't shared, and rounds the new bottom corners at the crop line
     * to the same native radius rounded() used for the top ones, so all four corners of the
     * split card match, just like on the static card.
     */
    private static BufferedImage topHalf(BufferedImage src, int corner_logical, int card_w_logical) {
        int w = src.getWidth();
        int h = Math.max(1, src.getHeight() / 2);
        int radius = Math.max(1, Math.round(w * (corner_logical / (float) card_w_logical)));
        BufferedImage cut = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = cut.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fill(new RoundRectangle2D.Float(0, 0, w, h, radius, radius));
            g.setComposite(AlphaComposite.SrcIn);
            g.drawImage(src, 0, 0, w, h, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return cut;
    }

    /** Applies rounded corners (SrcIn mask) while preserving resolution. */
    private static BufferedImage rounded(BufferedImage src, int radius) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Float(0, 0, w, h, radius, radius));
        g.setComposite(AlphaComposite.SrcIn);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return dst;
    }

    /**
     * Per-pixel inverse perspective warp: renders the card rotated angleDeg degrees (0 = back
     * facing forward, 180 = front facing forward) into a BufferedImage.
     */
    private static BufferedImage renderFlipImage(BufferedImage front, BufferedImage back,
            double angleDeg, double persp, int drawW, int drawH, int canvasW, int canvasH, int ss) {

        BufferedImage src = (angleDeg > 90) ? front : back;
        boolean mirror = (angleDeg > 90);
        int srcW = src.getWidth(), srcH = src.getHeight();
        int[] srcPix = ((DataBufferInt) src.getRaster().getDataBuffer()).getData();

        // The flat card is drawW x drawH (same size/aspect as the static CARD), centered on the
        // canvasW x canvasH canvas, so the whole animation stays aligned and centered on the
        // static card — the last frame matches it exactly, with nothing invented.
        int fw = canvasW, fh = canvasH;
        int bw = fw * ss, bh = fh * ss;

        double ang = Math.toRadians(angleDeg);
        double halfW = drawW / 2.0;
        double D = drawW * (persp / 45.0) * 2.0;
        double a = halfW * Math.cos(ang);
        double b = halfW * Math.sin(ang);
        double fcx = canvasW / 2.0, fcy = canvasH / 2.0;

        BufferedImage big = new BufferedImage(bw, bh, BufferedImage.TYPE_INT_ARGB);
        int[] dst = ((DataBufferInt) big.getRaster().getDataBuffer()).getData();

        for (int dys = 0; dys < bh; dys++) {
            double Y = (dys + 0.5) / ss - fcy;
            int row = dys * bw;
            for (int dxs = 0; dxs < bw; dxs++) {
                double X = (dxs + 0.5) / ss - fcx;
                double denom = a * D - X * b;
                if (denom == 0) { dst[row + dxs] = 0; continue; }
                double u = X * D / denom;
                if (u < -1 || u > 1) { dst[row + dxs] = 0; continue; }
                double f = D / (D + u * b);
                double srcRowF = (Y / (f * drawH) + 0.5) * srcH;
                if (srcRowF < 0 || srcRowF >= srcH) { dst[row + dxs] = 0; continue; }
                double uu = mirror ? -u : u;
                double srcColF = (uu + 1) / 2.0 * srcW;
                dst[row + dxs] = sampleBilinear(srcPix, srcW, srcH, srcColF - 0.5, srcRowF - 0.5);
            }
        }

        BufferedImage out = new BufferedImage(fw, fh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(big, 0, 0, fw, fh, null);
        g.dispose();
        return out;
    }

    /** Bilinear ARGB sampling with premultiplied alpha (avoids edge halos). */
    private static int sampleBilinear(int[] p, int w, int h, double fx, double fy) {
        int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
        double tx = fx - x0, ty = fy - y0;
        int x1 = x0 + 1, y1 = y0 + 1;
        if (x0 < 0) x0 = 0; else if (x0 > w - 1) x0 = w - 1;
        if (x1 < 0) x1 = 0; else if (x1 > w - 1) x1 = w - 1;
        if (y0 < 0) y0 = 0; else if (y0 > h - 1) y0 = h - 1;
        if (y1 < 0) y1 = 0; else if (y1 > h - 1) y1 = h - 1;

        int c00 = p[y0 * w + x0], c10 = p[y0 * w + x1], c01 = p[y1 * w + x0], c11 = p[y1 * w + x1];
        double w00 = (1 - tx) * (1 - ty), w10 = tx * (1 - ty), w01 = (1 - tx) * ty, w11 = tx * ty;

        double a00 = (c00 >>> 24), a10 = (c10 >>> 24), a01 = (c01 >>> 24), a11 = (c11 >>> 24);
        double A = a00 * w00 + a10 * w10 + a01 * w01 + a11 * w11;
        if (A < 0.5) return 0;
        double R = (c00 >> 16 & 255) * a00 * w00 + (c10 >> 16 & 255) * a10 * w10
                + (c01 >> 16 & 255) * a01 * w01 + (c11 >> 16 & 255) * a11 * w11;
        double G = (c00 >> 8 & 255) * a00 * w00 + (c10 >> 8 & 255) * a10 * w10
                + (c01 >> 8 & 255) * a01 * w01 + (c11 >> 8 & 255) * a11 * w11;
        double B = (c00 & 255) * a00 * w00 + (c10 & 255) * a10 * w10
                + (c01 & 255) * a01 * w01 + (c11 & 255) * a11 * w11;
        int ai = (int) Math.round(A); if (ai > 255) ai = 255;
        int ri = (int) Math.round(R / A); if (ri > 255) ri = 255;
        int gi = (int) Math.round(G / A); if (gi > 255) gi = 255;
        int bi = (int) Math.round(B / A); if (bi > 255) bi = 255;
        return (ai << 24) | (ri << 16) | (gi << 8) | bi;
    }
}
