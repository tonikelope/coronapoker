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
import java.util.ArrayList;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * GIF animado pre-decodificado a frames completos (compuestos según el
 * disposal de cada frame) para reproducirlo con indexado por reloj.
 *
 * El animador GIF de AWT decodifica sobre la marcha y duerme el delay de cada
 * frame con Thread.sleep, sin recuperar nunca el tiempo perdido: con la
 * granularidad del timer de Windows (15,6 ms por defecto, según el estado
 * global del sistema) una animación de 20 ms/frame se estira hasta un ~50%.
 * Aquí los frames se decodifican ANTES de reproducir y el frame visible se
 * elige por tiempo transcurrido (frameAt), saltando frames si hace falta, de
 * forma que la duración total es siempre la nominal del GIF.
 */
public class PreRenderedGif {

    private final BufferedImage[] frames;

    // Línea de tiempo acumulada: el frame i es visible mientras elapsed < frame_end_ms[i]
    private final long[] frame_end_ms;

    private final int width;

    private final int height;

    private PreRenderedGif(BufferedImage[] frames, long[] frame_end_ms, int width, int height) {
        this.frames = frames;
        this.frame_end_ms = frame_end_ms;
        this.width = width;
        this.height = height;
    }

    public int getFrameCount() {
        return frames.length;
    }

    public BufferedImage getFrame(int i) {
        return frames[i];
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public long getTotalMs() {
        return frame_end_ms[frame_end_ms.length - 1];
    }

    public int frameAt(long elapsed_ms) {
        return frameAt(frame_end_ms, elapsed_ms);
    }

    // Frame que toca según el tiempo transcurrido. Si un tick llega tarde, el
    // resultado salta directamente al frame correcto (catch-up): la animación
    // pierde frames antes que velocidad. Package-private para el test AAA.
    static int frameAt(long[] frame_end_ms, long elapsed_ms) {

        for (int i = 0; i < frame_end_ms.length; i++) {
            if (elapsed_ms < frame_end_ms[i]) {
                return i;
            }
        }

        return frame_end_ms.length - 1;
    }

    public static PreRenderedGif decode(URL url) throws IOException {

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

                // Primera pasada: frames crudos + metadatos por frame
                ArrayList<BufferedImage> raws = new ArrayList<>(n);
                int[] left = new int[n];
                int[] top = new int[n];
                long[] delay_ms = new long[n];
                String[] disposal = new String[n];

                for (int i = 0; i < n; i++) {
                    raws.add(reader.read(i));
                    readFrameMetadata(reader.getImageMetadata(i), i, left, top, delay_ms, disposal);
                }

                // Pantalla lógica: del stream metadata, con fallback al extent máximo de los frames
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
                    lw = Math.max(lw, left[i] + raws.get(i).getWidth());
                    lh = Math.max(lh, top[i] + raws.get(i).getHeight());
                }

                // Segunda pasada: composición respetando el disposal de cada frame
                BufferedImage[] frames = new BufferedImage[n];
                long[] frame_end_ms = new long[n];

                BufferedImage canvas = new BufferedImage(lw, lh, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = canvas.createGraphics();

                try {
                    long t = 0;
                    BufferedImage restore = null;

                    for (int i = 0; i < n; i++) {

                        if ("restoreToPrevious".equals(disposal[i])) {
                            restore = copyOf(canvas);
                        }

                        g.drawImage(raws.get(i), left[i], top[i], null);

                        frames[i] = copyOf(canvas);

                        t += delay_ms[i];
                        frame_end_ms[i] = t;

                        if ("restoreToBackgroundColor".equals(disposal[i])) {
                            clearRect(g, left[i], top[i], raws.get(i).getWidth(), raws.get(i).getHeight());
                        } else if ("restoreToPrevious".equals(disposal[i]) && restore != null) {
                            clearRect(g, 0, 0, lw, lh);
                            g.drawImage(restore, 0, 0, null);
                        }
                    }
                } finally {
                    g.dispose();
                }

                return new PreRenderedGif(frames, frame_end_ms, lw, lh);

            } finally {
                reader.dispose();
            }
        }
    }

    private static void readFrameMetadata(IIOMetadata md, int i, int[] left, int[] top, long[] delay_ms, String[] disposal) {

        // Defaults si faltan extensiones (GIFs mínimos sin GraphicControlExtension)
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

                // delayTime viene en centisegundos. Convención de los navegadores:
                // delays <= 1cs se interpretan como 100 ms.
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
