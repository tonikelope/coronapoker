/*
 * Copyright (C) 2020 tonikelope
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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.SwingUtilities;

/**
 * Lupa del avatar de un asiento: dejando el ratón sobre él HOVER_DELAY_MS, el
 * mismo avatar aparece AMPLIADO en un overlay centrado sobre el original, y se
 * retira en cuanto el ratón sale.
 *
 * El overlay vive en la capa DRAG del tapete (por encima de todo el tablero) y
 * es transparente a los eventos de ratón (contains siempre false), así que
 * taparlo no le roba el hover al avatar ni el click al identicon.
 *
 * La ampliación NO se saca del icono del asiento (~64 px): se rehace desde el
 * fichero original del avatar, que se conserva íntegro, escalando por pasos con
 * interpolación bicúbica. Los avatares por defecto y de bot son de 200x200, así
 * que a los tamaños habituales la lupa no inventa detalle.
 *
 * @author tonikelope
 */
public final class AvatarZoomOverlay extends javax.swing.JComponent {

    // Por debajo del retardo con el que Swing saca sus tooltips (750 ms): así la
    // lupa se adelanta al globo del identicon en vez de relevarlo a medias.
    public static final int HOVER_DELAY_MS = 700;

    // Tamaño de la ampliación: N veces el alto del avatar del asiento, con tope
    // en una fracción del alto del tapete para que en ventanas pequeñas (o con
    // el zoom muy subido) no se coma la mesa.
    private static final float ZOOM_FACTOR = 3f;
    private static final float MAX_TABLE_FRACTION = 0.45f;

    // Radio de las esquinas del avatar del asiento (setAvatar) relativo a su
    // alto: la lupa lo mantiene proporcional para verse como "el mismo" avatar.
    private static final int SEAT_CORNER_RADIUS = 20;
    private static final int SEAT_CORNER_REFERENCE = 64;

    // Marco oscuro alrededor de la imagen: despega la lupa del tapete y de las
    // cartas cuando el avatar es de tonos parecidos.
    private static final float FRAME_ALPHA = 0.55f;

    // Cadencia del vigilante que retira la lupa. Sondea la posición del puntero
    // en vez de escuchar eventos porque ni el overlay los recibe (es transparente
    // al ratón) ni el mouseExited del avatar sirve: ese salta justo al pisar la
    // ampliación, y encima no llega si el asiento se oculta bajo el puntero
    // (cambio de mano, vista compacta, fin de timba).
    private static final int POLL_MS = 100;

    // Ampliaciones ya generadas. Por referencia blanda: sobreviven a la partida
    // entera (rehacerlas en cada hover daría un tirón), pero el recolector puede
    // llevárselas si hace falta memoria, y se regeneran solas.
    private static final ConcurrentHashMap<String, java.lang.ref.SoftReference<BufferedImage>> CACHE = new ConcurrentHashMap<>();

    private static volatile AvatarZoomOverlay current = null;
    private static volatile JLabel current_avatar = null;
    private static volatile String current_tooltip = null;
    private static javax.swing.Timer watchdog = null;

    private final BufferedImage image;
    private final int pad;

    private AvatarZoomOverlay(BufferedImage image, int pad) {
        this.image = image;
        this.pad = pad;
        setOpaque(false);
        setFocusable(false);
        setSize(image.getWidth() + 2 * pad, image.getHeight() + 2 * pad);
    }

    /**
     * Transparente al ratón: aunque el overlay quede justo debajo del puntero,
     * el hit-test lo atraviesa y el avatar sigue recibiendo sus eventos (si no,
     * al aparecer la lupa el avatar recibiría un mouseExited y se retiraría
     * sola, en bucle).
     */
    @Override
    public boolean contains(int x, int y) {
        return false;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(0f, 0f, 0f, FRAME_ALPHA));
            int arc = cornerRadius(image.getWidth()) + pad;
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc));
            g2.drawImage(image, pad, pad, null);
        } finally {
            g2.dispose();
        }
    }

    /**
     * Engancha la lupa al avatar de un asiento. El proveedor devuelve la MISMA
     * cadena que usa setAvatar para pintar el asiento (ruta del fichero, "*"
     * para un bot o "" para el avatar por defecto) y se consulta en el momento
     * de mostrar, no aquí: al instalarse, el asiento todavía no tiene nick.
     */
    public static void install(final JLabel avatar, final Supplier<String> source) {

        final javax.swing.Timer[] delay = new javax.swing.Timer[1];

        delay[0] = new javax.swing.Timer(HOVER_DELAY_MS, e -> {
            delay[0].stop();
            show(avatar, source);
        });

        delay[0].setRepeats(false);

        avatar.addMouseListener(new MouseAdapter() {

            @Override
            public void mouseEntered(MouseEvent e) {
                if (canShow()) {
                    delay[0].restart();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                // Solo cancela la aparición pendiente. Si la lupa YA está puesta,
                // salir del avatar no la retira: la ampliación es más grande que
                // él, y el ratón sigue dentro de ella. De eso decide el vigilante,
                // que mira toda el área (avatar + ampliación).
                delay[0].stop();
            }
        });
    }

    // La lupa puede aparecer: la partida sigue viva.
    private static boolean canShow() {
        GameFrame gf = GameFrame.getInstance();
        return gf != null && gf.getCrupier() != null && !gf.getCrupier().isFin_de_la_transmision();
    }

    /**
     * Prepara la ampliación FUERA del EDT (decodificar el fichero original y
     * reescalarlo cuesta lo suyo la primera vez de cada avatar) y la muestra
     * después, si para entonces el puntero sigue sobre el mismo avatar.
     */
    private static void show(final JLabel avatar, final Supplier<String> source) {

        if (!canShow() || !avatar.isShowing() || !pointerOver(avatar)) {
            return;
        }

        final JLayeredPane tapete = GameFrame.getInstance().getTapete();

        if (tapete == null || tapete.getHeight() <= 0) {
            return;
        }

        final int size = zoomSize(avatar, tapete);

        // Si el tope del tapete deja la "ampliación" en el tamaño del propio
        // avatar (ventana muy baja, zoom muy alto), no hay nada que ampliar.
        if (size <= Math.min(avatar.getWidth(), avatar.getHeight())) {
            return;
        }

        final String src;

        try {
            src = source.get();
        } catch (Exception ex) {
            // El asiento aún no tiene con qué resolver su avatar (sala de espera
            // no enlazada, nick sin asignar): sin lupa, y sin ruido.
            return;
        }

        Helpers.threadRun(() -> {

            final BufferedImage img = zoomedImage(src, size);

            if (img == null) {
                return;
            }

            Helpers.GUIRun(() -> {

                if (!canShow() || !avatar.isShowing() || !pointerOver(avatar)) {
                    return;
                }

                hideZoom();

                AvatarZoomOverlay overlay = new AvatarZoomOverlay(img, Math.max(4, size / 24));

                Point p = SwingUtilities.convertPoint(avatar, 0, 0, tapete);

                // Centrada sobre el avatar original, y acotada al tapete para que
                // en los asientos de las esquinas se vea entera.
                int x = p.x + (avatar.getWidth() - overlay.getWidth()) / 2;
                int y = p.y + (avatar.getHeight() - overlay.getHeight()) / 2;

                x = Math.max(0, Math.min(x, tapete.getWidth() - overlay.getWidth()));
                y = Math.max(0, Math.min(y, tapete.getHeight() - overlay.getHeight()));

                overlay.setLocation(x, y);

                tapete.add(overlay, JLayeredPane.DRAG_LAYER);

                current = overlay;
                current_avatar = avatar;

                // El avatar ya tiene su propio tooltip (el del identicon): con la
                // lupa puesta el globo sobra y encima la tapa. Se retira mientras
                // dura y se devuelve al ocultarla.
                current_tooltip = avatar.getToolTipText();
                avatar.setToolTipText(null);

                startWatchdog();

                tapete.repaint();
            });
        });
    }

    /**
     * Retira la lupa. Idempotente y seguro desde cualquier hilo.
     */
    public static void hideZoom() {

        Helpers.GUIRun(() -> {

            if (watchdog != null) {
                watchdog.stop();
            }

            AvatarZoomOverlay overlay = current;

            if (overlay != null) {
                java.awt.Container parent = overlay.getParent();
                if (parent != null) {
                    Rectangle bounds = overlay.getBounds();
                    parent.remove(overlay);
                    parent.repaint(bounds.x, bounds.y, bounds.width, bounds.height);
                }
                current = null;
            }

            JLabel avatar = current_avatar;

            if (avatar != null) {
                if (current_tooltip != null) {
                    avatar.setToolTipText(current_tooltip);
                }
                current_avatar = null;
                current_tooltip = null;
            }
        });
    }

    // Mientras la lupa esté puesta, comprueba que el puntero siga dentro de su
    // área (el avatar MÁS la propia ampliación). Es la única vía para retirarla:
    // el overlay es transparente a los eventos, así que moverse por encima de él
    // no genera entered/exited propios, y el exited del avatar salta en cuanto el
    // ratón pisa la ampliación, que es justo cuando NO hay que retirarla.
    private static void startWatchdog() {

        if (watchdog == null) {
            watchdog = new javax.swing.Timer(POLL_MS, e -> {
                JLabel avatar = current_avatar;
                if (avatar == null || !avatar.isShowing() || !canShow() || !pointerInHoverArea(avatar)) {
                    hideZoom();
                }
            });
        }

        watchdog.start();
    }

    // El puntero está sobre el avatar. Con MouseInfo (coordenadas de pantalla)
    // para no depender de que llegue el evento.
    private static boolean pointerOver(java.awt.Component c) {

        if (c == null || !c.isShowing()) {
            return false;
        }

        try {
            java.awt.PointerInfo pi = java.awt.MouseInfo.getPointerInfo();

            if (pi == null) {
                return false;
            }

            return new Rectangle(c.getLocationOnScreen(), c.getSize()).contains(pi.getLocation());

        } catch (java.awt.IllegalComponentStateException ex) {
            // El componente ha dejado de estar en pantalla entre el isShowing y la
            // consulta: cuenta como puntero fuera.
            return false;
        }
    }

    // Área que mantiene viva la lupa: el avatar o la ampliación que sale de él.
    private static boolean pointerInHoverArea(JLabel avatar) {
        return pointerOver(avatar) || pointerOver(current);
    }

    /**
     * ¿La lupa está tapando este punto de pantalla? El overlay es transparente al
     * ratón (para no romper el hover del avatar), así que los clicks sobre la
     * ampliación caen en lo que haya debajo: el tapete lo consulta para no
     * disparar sus propias acciones cuando el usuario cree estar pinchando en la
     * imagen ampliada.
     */
    public static boolean coversScreenPoint(java.awt.Point screen_point) {

        AvatarZoomOverlay overlay = current;

        if (overlay == null || screen_point == null || !overlay.isShowing()) {
            return false;
        }

        try {
            return new Rectangle(overlay.getLocationOnScreen(), overlay.getSize()).contains(screen_point);
        } catch (java.awt.IllegalComponentStateException ex) {
            return false;
        }
    }

    // Lado de la ampliación: ZOOM_FACTOR veces el avatar del asiento, sin pasar
    // de MAX_TABLE_FRACTION del alto del tapete.
    private static int zoomSize(JLabel avatar, JLayeredPane tapete) {

        int seat = Math.min(avatar.getWidth(), avatar.getHeight());

        if (seat <= 0) {
            return 0;
        }

        int size = Math.round(seat * ZOOM_FACTOR);

        return Math.min(size, Math.round(tapete.getHeight() * MAX_TABLE_FRACTION));
    }

    private static int cornerRadius(int size) {
        return Math.max(1, Math.round(SEAT_CORNER_RADIUS * size / (float) SEAT_CORNER_REFERENCE));
    }

    // Ampliación cacheada por (origen, tamaño): el mismo avatar solo se decodifica
    // y reescala una vez por tamaño, y el tamaño solo cambia con el zoom.
    private static BufferedImage zoomedImage(String src, int size) {

        String key = (src != null ? src : "") + "@" + size;

        java.lang.ref.SoftReference<BufferedImage> ref = CACHE.get(key);

        BufferedImage cached = ref != null ? ref.get() : null;

        if (cached != null) {
            return cached;
        }

        BufferedImage original = readOriginal(src);

        if (original == null) {
            return null;
        }

        BufferedImage zoomed = Helpers.makeImageRoundedCorner(scale(original, size), cornerRadius(size));

        CACHE.put(key, new java.lang.ref.SoftReference<>(zoomed));

        return zoomed;
    }

    // Fichero original del avatar tal cual lo mandó su dueño (hasta 256 KB), o el
    // recurso empaquetado cuando el asiento es un bot ("*") o no tiene avatar ("").
    private static BufferedImage readOriginal(String src) {

        try {
            if (src == null || src.isEmpty()) {
                return readResource("/images/avatar_default.png");
            }

            if ("*".equals(src)) {
                return readResource("/images/avatar_bot.png");
            }

            File f = new File(src);

            if (!f.exists() || !f.canRead()) {
                return readResource("/images/avatar_default.png");
            }

            return ImageIO.read(f);

        } catch (Exception ex) {
            Logger.getLogger(AvatarZoomOverlay.class.getName()).log(Level.WARNING, "Could not read avatar image for zoom overlay", ex);
            return null;
        }
    }

    private static BufferedImage readResource(String path) throws java.io.IOException {
        URL url = AvatarZoomOverlay.class.getResource(path);
        return url != null ? ImageIO.read(url) : null;
    }

    /**
     * Escalado de calidad: bicúbico, y por pasos de mitad en mitad mientras la
     * imagen sea más del doble del destino (reducir de golpe una foto grande se
     * come detalle y deja bordes duros).
     */
    private static BufferedImage scale(BufferedImage src, int size) {

        BufferedImage current_img = src;

        int w = src.getWidth();
        int h = src.getHeight();

        while (w > 2 * size && h > 2 * size) {
            w = Math.max(size, w / 2);
            h = Math.max(size, h / 2);
            current_img = drawScaled(current_img, w, h);
        }

        return drawScaled(current_img, size, size);
    }

    private static BufferedImage drawScaled(Image src, int w, int h) {

        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2 = dst.createGraphics();

        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setComposite(AlphaComposite.Src);
            g2.drawImage(src, 0, 0, w, h, null);
        } finally {
            g2.dispose();
        }

        return dst;
    }
}
