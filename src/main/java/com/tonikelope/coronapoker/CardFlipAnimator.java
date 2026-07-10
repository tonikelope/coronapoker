/*
 * Copyright (C) 2025 tonikelope
 *
 * Animación de destape de carta renderizada con Swing/Java2D (sustituye a los
 * GIF de giro pre-generados con gifsicle).
 *
 * El giro es una rotación 3D de 180 grados sobre el eje vertical con PERSPECTIVA
 * (la carta se ve como un trapecio, no una simple compresión plana). Se resuelve
 * con un WARP INVERSO por píxel: para cada píxel de salida se calcula su origen
 * exacto en el JPG (fórmula cerrada) y se muestrea con interpolación bilineal 2D.
 * Sin "tiras" (que replican y pixelan) -> nítido como el JPG nativo.
 *
 * Los frames se generan a RESOLUCIÓN FÍSICA (según la escala HiDPI del monitor)
 * para que no haya upscale del sistema. La carta se dibuja a un tamaño <= JPG
 * nativo para no ampliar por encima de la resolución de la fuente.
 *
 * Los frames resultantes se envuelven en un {@link PreRenderedGif} y se
 * reproducen con el motor catch-up existente (mismo timing, hooks y
 * sincronización que los GIF).
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
 *
 * @author tonikelope
 */
public class CardFlipAnimator {

    // Perspectiva fija (valor validado en la prueba de concepto). Menor = más 3D.
    private static final double PERSPECTIVE = 45.0;
    // Holgura del lienzo alrededor de la carta para acomodar el trapecio.
    private static final double MARGIN = 1.5;
    // Supersampling del warp (suaviza el contorno de la carta).
    private static final int SS = 2;

    // Caché de imágenes fuente redondeadas a resolución nativa (cara + trasera),
    // por baraja+carta. Se limpia al cambiar de baraja.
    private static final ConcurrentHashMap<String, BufferedImage> SRC_CACHE = new ConcurrentHashMap<>();
    private static volatile String CACHE_BARAJA = null;
    private static volatile String CACHE_TRASERA = null;

    /**
     * Escala HiDPI del monitor principal (1.0, 1.25, 2.0...). Los frames se
     * generan a esta densidad para quedar 1:1 en píxeles físicos.
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
     * Genera la animación de destape de una carta como PreRenderedGif.
     *
     * @param baraja baraja actual (GameFrame.BARAJA)
     * @param valor_palo clave de la carta, p.ej. "A_P" (o "trasera" no aplica)
     * @param card_w_logical ancho lógico de la carta estática (Card.CARD_WIDTH)
     * @param card_h_logical alto lógico de la carta estática (Card.CARD_HEIGHT)
     * @param corner_logical radio de esquina lógico (Card.CARD_CORNER)
     * @param duration_ms duración total del giro
     * @param num_frames número de frames a generar
     * @return PreRenderedGif con los frames del giro, o null si falla la carga
     */
    public static PreRenderedGif generate(String baraja, String valor_palo,
            int card_w_logical, int card_h_logical, int corner_logical,
            int duration_ms, int num_frames) {

        try {
            BufferedImage front = cachedFace(baraja, valor_palo, card_w_logical, corner_logical);
            BufferedImage back = cachedBack(card_w_logical, corner_logical);
            if (front == null || back == null) {
                return null;
            }

            double dens = screenDensity();
            // Carta y lienzo a resolución FÍSICA con el TAMAÑO EXACTO de CARD (mismo aspecto
            // que la carta estática), para que la animación quede alineada y centrada con ella.
            int draw_w = Math.round(card_w_logical * (float) dens);
            int draw_h = Math.round(card_h_logical * (float) dens);
            int canvas_w = Math.round(canvasWidth(card_w_logical) * (float) dens);
            int canvas_h = Math.round(canvasHeight(card_h_logical) * (float) dens);

            BufferedImage[] frames = new BufferedImage[num_frames];
            for (int i = 0; i < num_frames; i++) {
                double ang = i * 180.0 / (num_frames - 1);
                frames[i] = renderFlipImage(front, back, ang, PERSPECTIVE, draw_w, draw_h, canvas_w, canvas_h, SS);
            }
            return PreRenderedGif.fromFrames(frames, duration_ms);

        } catch (Exception ex) {
            return null;
        }
    }

    // Ancho/alto LÓGICO del lienzo de la animación (para setSize del label). Margen SIMÉTRICO
    // y PAR alrededor de la carta (display = CARD + 2*margen) para que el centrado del overlay
    // sobre la carta estática sea entero al píxel (sin desalineación de medio píxel).
    public static int canvasWidth(int card_w_logical) {
        int margin = Math.round(card_w_logical * (float) (MARGIN - 1) / 2f);
        return card_w_logical + 2 * margin;
    }

    public static int canvasHeight(int card_h_logical) {
        int margin = Math.round(card_h_logical * (float) (MARGIN - 1) / 2f);
        return card_h_logical + 2 * margin;
    }

    /** Invalida la caché de fuentes si cambió la baraja o la trasera seleccionada. */
    private static void ensureCacheValid(String baraja) {
        if (!java.util.Objects.equals(baraja, CACHE_BARAJA)
                || !java.util.Objects.equals(GameFrame.TRASERA, CACHE_TRASERA)) {
            SRC_CACHE.clear();
            CACHE_BARAJA = baraja;
            CACHE_TRASERA = GameFrame.TRASERA;
        }
    }

    /** Cara de la carta (JPG de la baraja), redondeada a resolución nativa, cacheada. */
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

    /** Trasera GLOBAL (juego back/ o mod), redondeada a resolución nativa, cacheada. */
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

    /** Carga la trasera seleccionada (baraja del juego o mod) a resolución nativa. */
    private static BufferedImage loadTraseraRaw() throws Exception {
        String baraja = GameFrame.TRASERA;
        // "default" (o valor no reconocido): la trasera sigue a la baraja actual.
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

    /** Invalida la caché de fuentes (llamar al cambiar de baraja o trasera). */
    public static void clearCache() {
        SRC_CACHE.clear();
        CACHE_BARAJA = null;
        CACHE_TRASERA = null;
    }

    /** Aplica esquinas redondeadas (máscara SrcIn) conservando la resolución. */
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
     * Warp de perspectiva inverso por píxel: renderiza la carta girada angleDeg
     * grados (0 = 'back' de frente, 180 = 'front' de frente) a un BufferedImage.
     */
    private static BufferedImage renderFlipImage(BufferedImage front, BufferedImage back,
            double angleDeg, double persp, int drawW, int drawH, int canvasW, int canvasH, int ss) {

        BufferedImage src = (angleDeg > 90) ? front : back;
        boolean mirror = (angleDeg > 90);
        int srcW = src.getWidth(), srcH = src.getHeight();
        int[] srcPix = ((DataBufferInt) src.getRaster().getDataBuffer()).getData();

        // La carta plana mide drawW x drawH (tamaño y aspecto de CARD = la carta estática),
        // centrada en el lienzo canvasW x canvasH. Así TODA la animación queda alineada en
        // tamaño y centrada con la carta estática (el último frame coincide sin inventar nada).
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

    /** Muestreo bilineal ARGB con alpha premultiplicado (evita halos en bordes). */
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
