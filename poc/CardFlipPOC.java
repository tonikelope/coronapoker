package poc;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.*;

/**
 * PRUEBA DE CONCEPTO (independiente del juego).
 *
 * Destape de una carta (giro de 180 grados sobre su eje vertical) dibujado con
 * Swing/Java2D en lugar del GIF pre-renderizado. El GIF actual de goliat tiene
 * 31 frames a 20 ms -> 50 fps -> 620 ms, y NO es una compresion plana: es una
 * rotacion 3D con PERSPECTIVA (la carta se ve como un trapecio).
 *
 * TECNICA: WARP DE PERSPECTIVA POR PIXEL (mapeo inverso). Para cada pixel de
 * salida se calcula analiticamente su origen exacto en el JPG (rotacion sobre
 * eje Y + proyeccion perspectiva) y se muestrea con interpolacion BILINEAL 2D.
 * Esto da interpolacion completa (nada de "tiras" de 1px que replican y
 * pixelan) -> nitido como un GIF. El contorno se suaviza por SUPERSAMPLING.
 *
 * NOTA sobre antialiasing: KEY_ANTIALIASING NO afecta al escalado de imagenes
 * (solo a formas vectoriales). La nitidez de una imagen escalada la da el
 * muestreo/interpolacion, que aqui es bilineal por pixel + supersampling.
 *
 * FLUIDEZ: los 61 frames del giro se PRE-RENDERIZAN una vez; la animacion es un
 * simple drawImage por frame -> cientos de FPS, fluida sea cual sea la duracion.
 *
 * La carta se dibuja MENOR que el JPG nativo (652 alto) para no hacer upscale.
 * Esquinas redondeadas como el juego. Sin sombra.
 *
 * Arg "dump <dir>": vuelca frames del render para comparar (calibracion).
 */
public class CardFlipPOC {

    private static final int DRAW_H = 430;      // alto de la carta en pantalla (< 652 nativo)
    private static final int SS = 2;            // supersampling (suaviza el contorno)
    private static final double MARGIN = 1.5;   // holgura para el trapecio
    private static final int STEPS = 61;        // frames pre-renderizados (0..180)

    // Datos del GIF actual (medidos sobre goliat/gif/A_P.gif).
    private static final int GIF_FRAMES = 31;
    private static final int GIF_MS = 620;
    private static final int GIF_FPS = 50;

    public static void main(String[] args) throws IOException {
        if (args.length > 0 && args[0].equals("dump")) {
            dumpFrames(args.length > 1 ? args[1] : ".");
            return;
        }
        if (args.length > 0 && args[0].equals("bench")) {
            benchmark();
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                new CardFlipPOC().buildAndShow();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(null, "No se pudieron cargar las imagenes: " + ex.getMessage());
            }
        });
    }

    private static BufferedImage[] loadCards() throws IOException {
        BufferedImage frontRaw = ImageIO.read(CardFlipPOC.class.getResource("/images/decks/goliat/A_P.jpg"));
        BufferedImage backRaw = ImageIO.read(CardFlipPOC.class.getResource("/images/decks/goliat/trasera.jpg"));
        int radius = Math.round(frontRaw.getWidth() * 0.08f);
        return new BufferedImage[]{ rounded(frontRaw, radius), rounded(backRaw, radius) };
    }

    private void buildAndShow() throws IOException {
        BufferedImage[] c = loadCards();
        FlipPanel flip = new FlipPanel(c[0], c[1]);

        JFrame frame = new JFrame("CoronaPoker - POC destape carta (Swing/Java2D)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(flip, BorderLayout.CENTER);
        frame.add(buildControls(flip), BorderLayout.SOUTH);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JPanel buildControls(FlipPanel flip) {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 8));
        bar.setBackground(new Color(20, 20, 20));

        JButton flipBtn = new JButton("GIRAR / DESTAPAR");
        flipBtn.setFont(flipBtn.getFont().deriveFont(Font.BOLD, 14f));
        flipBtn.addActionListener(e -> flip.toggleFlip());
        bar.add(flipBtn);

        JLabel dval = white(GIF_MS + " ms");
        JSlider dur = new JSlider(120, 1500, GIF_MS);
        dur.setBackground(new Color(20, 20, 20));
        dur.setPreferredSize(new Dimension(120, dur.getPreferredSize().height));
        dur.addChangeListener(e -> { flip.setDurationMs(dur.getValue()); dval.setText(dur.getValue() + " ms"); });
        bar.add(white("Duracion:")); bar.add(dur); bar.add(dval);

        // Perspectiva FIJA en 45 (sin control): valor definitivo elegido.
        bar.add(white("Perspectiva: 45 (fija)"));

        return bar;
    }

    private static JLabel white(String s) {
        JLabel l = new JLabel(s);
        l.setForeground(Color.WHITE);
        return l;
    }

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
     * Renderiza la carta girada 'angleDeg' grados a un BufferedImage (tamano del
     * frame en pantalla), mediante warp de perspectiva inverso por pixel con
     * muestreo bilineal + supersampling. angleDeg 0 = 'back' de frente, 180 =
     * 'front' de frente.
     */
    static BufferedImage renderFlipImage(BufferedImage front, BufferedImage back,
                                         double angleDeg, double persp, int drawH, int ss) {
        BufferedImage src = (angleDeg > 90) ? front : back;
        boolean mirror = (angleDeg > 90);
        int srcW = src.getWidth(), srcH = src.getHeight();
        int[] srcPix = ((DataBufferInt) src.getRaster().getDataBuffer()).getData();

        double drawW = drawH * (double) srcW / srcH;
        double finalW = drawW * MARGIN, finalH = drawH * MARGIN;
        int fw = (int) Math.ceil(finalW), fh = (int) Math.ceil(finalH);
        int bw = fw * ss, bh = fh * ss;

        double ang = Math.toRadians(angleDeg);
        double halfW = drawW / 2.0;
        double D = drawW * (persp / 45.0) * 2.0;
        double a = halfW * Math.cos(ang); // factor horizontal
        double b = halfW * Math.sin(ang); // profundidad
        double fcx = finalW / 2.0, fcy = finalH / 2.0;

        BufferedImage big = new BufferedImage(bw, bh, BufferedImage.TYPE_INT_ARGB);
        int[] dst = ((DataBufferInt) big.getRaster().getDataBuffer()).getData();

        for (int dys = 0; dys < bh; dys++) {
            double Y = (dys + 0.5) / ss - fcy;
            int row = dys * bw;
            for (int dxs = 0; dxs < bw; dxs++) {
                double X = (dxs + 0.5) / ss - fcx;
                double denom = a * D - X * b;
                if (denom == 0) { dst[row + dxs] = 0; continue; }
                double u = X * D / denom; // coordenada horizontal en la carta [-1,1]
                if (u < -1 || u > 1) { dst[row + dxs] = 0; continue; }
                double f = D / (D + u * b); // escala vertical por perspectiva
                double srcRowF = (Y / (f * drawH) + 0.5) * srcH;
                if (srcRowF < 0 || srcRowF >= srcH) { dst[row + dxs] = 0; continue; }
                double uu = mirror ? -u : u;
                double srcColF = (uu + 1) / 2.0 * srcW;
                dst[row + dxs] = sampleBilinear(srcPix, srcW, srcH, srcColF - 0.5, srcRowF - 0.5);
            }
        }

        // Reduccion a tamano final con bicubica (supersampling -> contorno suave).
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

    /** Mide el coste del warp por frame a varios tamanos de carta (alto en px). */
    private static void benchmark() throws IOException {
        BufferedImage[] c = loadCards();
        int[] sizes = {120, 150, 200, 300, 430}; // 120-200 ~ tamano tipico en mesa
        int ss = 2;
        System.out.println("Warp por frame (ss=" + ss + "), promedio sobre 200 frames:");
        System.out.println("  alto(px)  frameW x frameH   ms/frame   fps_en_vivo   RAM_61frames");
        for (int h : sizes) {
            // warmup
            for (int i = 0; i < 30; i++) renderFlipImage(c[0], c[1], i % 180, 45, h, ss);
            long t0 = System.nanoTime();
            int N = 200;
            BufferedImage last = null;
            for (int i = 0; i < N; i++) last = renderFlipImage(c[0], c[1], (i * 180.0 / N), 45, h, ss);
            double msFrame = (System.nanoTime() - t0) / 1_000_000.0 / N;
            int fw = last.getWidth(), fh = last.getHeight();
            double ramMb = (fw * (double) fh * 4 * 61) / (1024 * 1024);
            System.out.printf("  %6d   %5d x %-5d   %7.2f   %10d   %8.1f MB%n",
                    h, fw, fh, msFrame, (int) (1000.0 / msFrame), ramMb);
        }
    }

    private static void dumpFrames(String outDir) throws IOException {
        BufferedImage[] c = loadCards();
        for (int a : new int[]{0, 45, 75, 90, 105, 135, 180}) {
            BufferedImage flip = renderFlipImage(c[0], c[1], a, 45, DRAW_H, SS);
            BufferedImage canvas = new BufferedImage(flip.getWidth() + 60, flip.getHeight() + 40, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = canvas.createGraphics();
            g2.setColor(new Color(11, 82, 42));
            g2.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
            g2.drawImage(flip, (canvas.getWidth() - flip.getWidth()) / 2, (canvas.getHeight() - flip.getHeight()) / 2, null);
            g2.dispose();
            ImageIO.write(canvas, "png", new File(outDir, "poc_" + String.format("%03d", a) + ".png"));
        }
        System.out.println("Dump OK -> " + outDir);
    }

    /** Panel: pre-renderiza los frames y reproduce con un simple blit. */
    private static class FlipPanel extends JPanel {

        private final BufferedImage front, back;
        private final int frameW, frameH; // tamano LOGICO del frame en pantalla
        private final double dens;        // escala HiDPI del monitor (1.0, 1.5, 2.0...)
        private final int renderSS;       // supersampling del warp (segun densidad)

        private volatile BufferedImage[] frames;
        private volatile boolean genRunning = false;
        private volatile boolean genPending = false;
        private volatile long genMs = 0;

        private double angle = 0.0;
        private int direction = 0;
        private int durationMs = GIF_MS;
        private long lastNanos = 0;
        private double persp = 45;

        private int frameCount = 0;
        private long fpsWindowStart = 0;
        private int measuredFps = 0;

        FlipPanel(BufferedImage front, BufferedImage back) {
            this.front = front;
            this.back = back;
            double drawW = DRAW_H * (double) front.getWidth() / front.getHeight();
            this.frameW = (int) Math.ceil(drawW * MARGIN);
            this.frameH = (int) Math.ceil(DRAW_H * MARGIN);
            // Escala del monitor: pre-renderizamos a resolucion FISICA para que no
            // haya upscale del sistema (causa del pixelado en pantallas HiDPI).
            double d = 1.0;
            try {
                d = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                        .getDefaultConfiguration().getDefaultTransform().getScaleX();
            } catch (Exception ignore) {}
            this.dens = Math.max(1.0, Math.min(3.0, d));
            this.renderSS = dens >= 2 ? 1 : 2; // resolucion efectiva ~2x el logico
            setBackground(new Color(11, 82, 42));
            setPreferredSize(new Dimension(frameW + 140, frameH + 60));
            new Timer(2, e -> tick()).start();
            regenerate();
        }

        void toggleFlip() {
            direction = (angle >= 90) ? -1 : 1;
            lastNanos = System.nanoTime();
            fpsWindowStart = lastNanos;
            frameCount = 0;
            measuredFps = 0;
        }

        void setDurationMs(int ms) { this.durationMs = Math.max(50, ms); }
        void setPerspective(int p) { this.persp = p; regenerate(); }

        private void regenerate() {
            genPending = true;
            if (genRunning) return;
            genRunning = true;
            new Thread(() -> {
                while (genPending) {
                    genPending = false;
                    double p = persp;
                    long t0 = System.nanoTime();
                    BufferedImage[] fr = new BufferedImage[STEPS];
                    int drawHphys = (int) Math.round(DRAW_H * dens);
                    for (int s = 0; s < STEPS; s++) {
                        double ang = s * 180.0 / (STEPS - 1);
                        fr[s] = renderFlipImage(front, back, ang, p, drawHphys, renderSS);
                    }
                    genMs = (System.nanoTime() - t0) / 1_000_000;
                    frames = fr;
                    repaint();
                }
                genRunning = false;
            }, "flip-prerender").start();
        }

        private void tick() {
            if (direction == 0) return;
            long now = System.nanoTime();
            double dtMs = (now - lastNanos) / 1_000_000.0;
            lastNanos = now;
            angle += direction * (180.0 / durationMs) * dtMs;
            if (angle >= 180) { angle = 180; direction = 0; }
            if (angle <= 0) { angle = 0; direction = 0; }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();

            BufferedImage[] fr = frames;
            if (fr != null) {
                int idx = (int) Math.round(angle / 180.0 * (STEPS - 1));
                idx = Math.max(0, Math.min(STEPS - 1, idx));
                BufferedImage img = fr[idx];
                // Dibujo a tamano LOGICO con bicubica: la imagen ya esta a resolucion
                // fisica, asi que el escalado del sistema (HiDPI) queda 1:1 y nitido.
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.drawImage(img, (getWidth() - frameW) / 2, (getHeight() - frameH) / 2, frameW, frameH, null);
            }

            if (direction != 0) {
                long now = System.nanoTime();
                frameCount++;
                if (now - fpsWindowStart >= 300_000_000L) {
                    measuredFps = (int) Math.round(frameCount * 1_000_000_000.0 / (now - fpsWindowStart));
                    frameCount = 0;
                    fpsWindowStart = now;
                }
            }
            drawHud(g2, fr == null);
            g2.dispose();
        }

        private void drawHud(Graphics2D g2, boolean generating) {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
            String[] lines = {
                "GIF actual:  " + GIF_FRAMES + " frames / " + GIF_MS + " ms / " + GIF_FPS + " fps",
                "Swing POC:   " + STEPS + " frames pre-render (" + genMs + " ms)  |  duracion " + durationMs + " ms",
                "FPS reales:  " + (measuredFps > 0 ? measuredFps : (direction != 0 ? "..." : "-"))
                        + "   |   Angulo: " + (int) angle + "   |   Persp: " + (int) persp
            };
            g2.setColor(new Color(0, 0, 0, 150));
            g2.fillRoundRect(8, 6, 340, 16 * lines.length + 10, 8, 8);
            g2.setColor(new Color(120, 255, 160));
            int yy = 20;
            for (String ln : lines) { g2.drawString(ln, 16, yy); yy += 16; }

            if (generating) {
                g2.setColor(Color.WHITE);
                g2.setFont(getFont().deriveFont(Font.BOLD, 18f));
                g2.drawString("Generando frames...", getWidth() / 2 - 90, getHeight() / 2);
            }
        }
    }
}
