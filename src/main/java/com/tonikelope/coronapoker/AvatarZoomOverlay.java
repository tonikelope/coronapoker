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

    // Sin espera: la lupa sale al entrar en el avatar. La primera de cada avatar
    // tarda lo que cueste decodificar su fichero (fuera del EDT); a partir de ahí
    // sale ya cacheada, en el mismo evento del ratón.
    public static final int HOVER_DELAY_MS = 0;

    // Tamaño de la ampliación: N veces el alto del avatar del asiento, con tope
    // en una fracción del alto del tapete para que en ventanas pequeñas (o con
    // el zoom muy subido) no se coma la mesa.
    private static final float ZOOM_FACTOR = 2f;
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
    private static javax.swing.Timer watchdog = null;

    private final BufferedImage image;
    private final int pad;

    // Stack del jugador (al lado de la foto) y nick (debajo), los dos con la fuente
    // y el color del asiento escalados por el mismo factor que la imagen. Se leen
    // de sus JLabel en cada pintada (no una copia al abrir): el stack cambia
    // durante la mano y la lupa puede estar puesta mientras cambia.
    private final JLabel stack;
    private final java.awt.Font stack_font;
    private final Color stack_color;
    private String painted_stack = null;

    private final JLabel name;
    private final java.awt.Font name_font;
    private final Color name_color;
    private String painted_name = null;

    private AvatarZoomOverlay(BufferedImage image, int pad, JLabel stack, JLabel name, float factor) {
        this.image = image;
        this.pad = pad;
        this.stack = stack;
        this.stack_font = stack != null && stack.getFont() != null
                ? stack.getFont().deriveFont(stack.getFont().getSize2D() * factor) : null;
        this.stack_color = stack != null ? stack.getForeground() : null;
        this.painted_stack = stackText();
        this.name = name;
        this.name_color = name != null ? name.getForeground() : null;
        this.name_font = name != null && name.getFont() != null
                ? name.getFont().deriveFont(name.getFont().getSize2D() * factor) : null;
        this.painted_name = nameText();
        setOpaque(false);
        setFocusable(false);
        setSize(preferredBox());

        // La ampliación es SÓLIDA al ratón: se queda con los clicks que caen sobre
        // ella en vez de dejarlos pasar a lo que tape (el nick del asiento, el
        // tapete...). Y como es el mismo avatar en grande, el click se reenvía al
        // original: sobre un humano abre su identicon, sobre un bot no hace nada,
        // igual que pinchando el pequeño. Que el overlay se coma los eventos del
        // avatar no afecta a la lupa: quien decide retirarla es el vigilante, que
        // sondea la posición del puntero y no depende de entered/exited.
        addMouseListener(new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent e) {
                redispatchToAvatar(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                redispatchToAvatar(e);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                redispatchToAvatar(e);
            }
        });
    }

    // Reenvía el evento al avatar que la lupa está ampliando, apuntando a su
    // centro: pinchar cualquier punto de la imagen grande equivale a pinchar el
    // avatar, así que los guards de "soltado dentro del componente" se cumplen.
    private void redispatchToAvatar(MouseEvent e) {

        JLabel target = current_avatar;

        if (target == null || !target.isShowing() || target.getWidth() <= 0) {
            return;
        }

        target.dispatchEvent(new MouseEvent(target, e.getID(), e.getWhen(), e.getModifiersEx(),
                target.getWidth() / 2, target.getHeight() / 2, e.getClickCount(), e.isPopupTrigger(), e.getButton()));
    }

    // Texto del stack tal y como lo muestra el asiento ahora mismo, o null si ese
    // asiento no tiene stack que enseñar.
    private String stackText() {

        if (stack == null || stack_font == null) {
            return null;
        }

        String text = stack.getText();

        return text != null && !text.trim().isEmpty() ? text.trim() : null;
    }

    // Nick tal y como lo muestra el asiento, o null si no hay.
    private String nameText() {

        if (name == null || name_font == null) {
            return null;
        }

        String text = name.getText();

        return text != null && !text.trim().isEmpty() ? text.trim() : null;
    }

    // Columna de la izquierda: la foto y, debajo, el nick. Se ensancha si el nick
    // es más largo que la foto (va a su tamaño normal, así que rara vez).
    private int columnWidth() {

        int w = image.getWidth();

        if (painted_name != null) {
            w = Math.max(w, getFontMetrics(name_font).stringWidth(painted_name));
        }

        return w;
    }

    // Desplazamiento de la FOTO dentro de la lupa: es ella la que se ancla al
    // avatar del asiento, no el borde del componente.
    private int imageOffsetX() {
        return pad + (columnWidth() - image.getWidth()) / 2;
    }

    // Caja que ocupa la lupa: la columna (foto + nick) y, si hay, el stack al lado.
    private java.awt.Dimension preferredBox() {

        int column = columnWidth();

        int w = column + 2 * pad;
        int h = image.getHeight() + 2 * pad;

        if (painted_name != null) {
            h += getFontMetrics(name_font).getHeight();
        }

        if (painted_stack != null) {
            java.awt.FontMetrics fm = getFontMetrics(stack_font);
            w += fm.stringWidth(painted_stack) + pad;
            h = Math.max(h, fm.getHeight() + 2 * pad);
        }

        return new java.awt.Dimension(w, h);
    }

    /**
     * Reajusta la lupa si el stack o el nick han cambiado mientras estaba puesta
     * (una apuesta, el reparto de un bote). Si la caja crece y eso la sacaría del
     * tapete, se recoloca dentro.
     */
    private void refreshIfChanged() {

        String new_stack = stackText();
        String new_name = nameText();

        if (java.util.Objects.equals(new_stack, painted_stack) && java.util.Objects.equals(new_name, painted_name)) {
            return;
        }

        painted_stack = new_stack;
        painted_name = new_name;

        java.awt.Dimension box = preferredBox();

        if (!box.equals(getSize())) {
            setSize(box);
            java.awt.Container parent = getParent();
            if (parent != null) {
                setLocation(Math.max(0, Math.min(getX(), parent.getWidth() - getWidth())),
                        Math.max(0, Math.min(getY(), parent.getHeight() - getHeight())));
            }
        }

        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g2.setColor(new Color(0f, 0f, 0f, FRAME_ALPHA));
            int arc = cornerRadius(image.getWidth()) + pad;
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc));

            int column = columnWidth();
            int image_x = imageOffsetX();

            g2.drawImage(image, image_x, pad, null);

            if (painted_name != null) {
                g2.setFont(name_font);
                g2.setColor(name_color != null ? name_color : Color.WHITE);
                java.awt.FontMetrics fm = g2.getFontMetrics();
                // Centrado bajo la foto, en el hueco que la caja reservó para él.
                g2.drawString(painted_name, pad + (column - fm.stringWidth(painted_name)) / 2,
                        pad + image.getHeight() + fm.getAscent());
            }

            if (painted_stack != null) {
                g2.setFont(stack_font);
                g2.setColor(stack_color != null ? stack_color : Color.WHITE);
                java.awt.FontMetrics fm = g2.getFontMetrics();
                // Centrado en vertical respecto a la FOTO, no al componente: el
                // nick de debajo no debe descolgar el número.
                int baseline = pad + (image.getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(painted_stack, pad + column + pad, baseline);
            }
        } finally {
            g2.dispose();
        }
    }

    /**
     * Engancha la lupa al avatar de un asiento, con el stack de ese asiento para
     * mostrarlo ampliado al lado de la foto. El proveedor devuelve la MISMA cadena
     * que usa setAvatar para pintar el asiento (ruta del fichero, "*" para un bot
     * o "" para el avatar por defecto) y se consulta en el momento de mostrar, no
     * aquí: al instalarse, el asiento todavía no tiene nick.
     */
    public static void install(final JLabel avatar, final JLabel stack, final JLabel name, final Supplier<String> source) {

        final javax.swing.Timer[] delay = new javax.swing.Timer[1];

        delay[0] = new javax.swing.Timer(HOVER_DELAY_MS, e -> {
            delay[0].stop();
            show(avatar, stack, name, source);
        });

        delay[0].setRepeats(false);

        avatar.addMouseListener(new MouseAdapter() {

            @Override
            public void mouseEntered(MouseEvent e) {
                if (!canShow()) {
                    return;
                }
                if (HOVER_DELAY_MS <= 0) {
                    // Sin espera: en el mismo evento, para no perder ni un ciclo
                    // del EDT en un timer que vencería de inmediato.
                    show(avatar, stack, name, source);
                } else {
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
    private static void show(final JLabel avatar, final JLabel stack, final JLabel name, final Supplier<String> source) {

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

        // Ya generada: se pinta en este mismo evento del ratón, sin rebotar por un
        // hilo y volver al EDT. Es lo normal salvo la primera vez de cada avatar.
        final BufferedImage cached = cachedImage(src, size);

        if (cached != null) {
            display(avatar, stack, name, tapete, cached, size);
            return;
        }

        Helpers.threadRun(() -> {

            final BufferedImage img = zoomedImage(src, size);

            if (img == null) {
                return;
            }

            Helpers.GUIRun(() -> display(avatar, stack, name, tapete, img, size));
        });
    }

    // Coloca la ampliación centrada sobre su avatar. Solo en el EDT.
    private static void display(final JLabel avatar, final JLabel stack, final JLabel name, final JLayeredPane tapete, final BufferedImage img, final int size) {

        if (!canShow() || !avatar.isShowing() || !pointerOver(avatar)) {
            return;
        }

        hideZoom();

        int seat = Math.min(avatar.getWidth(), avatar.getHeight());

        AvatarZoomOverlay overlay = new AvatarZoomOverlay(img, Math.max(4, size / 24), stack, name,
                seat > 0 ? size / (float) seat : ZOOM_FACTOR);

        // Mismo cursor que el avatar que amplía: si pinchar el pequeño hace algo
        // (identicon), la imagen grande lo anuncia igual; sobre un bot, cursor
        // normal en los dos.
        overlay.setCursor(avatar.getCursor());

        // La lupa tapa el avatar, así que hereda su tooltip: pasar el ratón por la
        // imagen grande sigue explicando lo que hace el click. El del avatar se
        // deja intacto (con la lupa puesta no puede salir de todas formas).
        overlay.setToolTipText(avatar.getToolTipText());

        Point p = SwingUtilities.convertPoint(avatar, 0, 0, tapete);

        // La FOTO va centrada sobre el avatar original (el stack sobresale a su
        // derecha y el nick cuelga debajo), y el conjunto se acota al tapete para
        // que en los asientos de las esquinas se vea entero.
        int x = p.x + avatar.getWidth() / 2 - (overlay.imageOffsetX() + img.getWidth() / 2);
        int y = p.y + avatar.getHeight() / 2 - (overlay.pad + img.getHeight() / 2);

        x = Math.max(0, Math.min(x, tapete.getWidth() - overlay.getWidth()));
        y = Math.max(0, Math.min(y, tapete.getHeight() - overlay.getHeight()));

        overlay.setLocation(x, y);

        tapete.add(overlay, JLayeredPane.DRAG_LAYER);

        current = overlay;
        current_avatar = avatar;

        startWatchdog();

        // Solo su rectángulo: ahora la lupa sale sin espera, así que cruzar la
        // mesa con el ratón no debe costar un repintado del tapete entero.
        tapete.repaint(x, y, overlay.getWidth(), overlay.getHeight());
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

            current_avatar = null;
        });
    }

    // Mientras la lupa esté puesta, comprueba que el puntero siga dentro de su
    // área (el avatar MÁS la propia ampliación). Es la única vía para retirarla:
    // el overlay tapa el avatar, así que no hay entered/exited aprovechables, y el
    // exited del avatar salta justo al pisar la ampliación, que es cuando NO hay
    // que retirarla. De paso refresca el stack si ha cambiado con la lupa puesta.
    private static void startWatchdog() {

        if (watchdog == null) {
            watchdog = new javax.swing.Timer(POLL_MS, e -> {
                JLabel avatar = current_avatar;
                if (avatar == null || !avatar.isShowing() || !canShow() || !pointerInHoverArea(avatar)) {
                    hideZoom();
                    return;
                }
                AvatarZoomOverlay overlay = current;
                if (overlay != null) {
                    overlay.refreshIfChanged();
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

    private static String cacheKey(String src, int size) {
        return (src != null ? src : "") + "@" + size;
    }

    // La ampliación ya generada, o null si toca hacerla. Barato: sirve para
    // decidir si se puede pintar en el acto o hay que salir del EDT.
    private static BufferedImage cachedImage(String src, int size) {
        java.lang.ref.SoftReference<BufferedImage> ref = CACHE.get(cacheKey(src, size));
        return ref != null ? ref.get() : null;
    }

    // Ampliación cacheada por (origen, tamaño): el mismo avatar solo se decodifica
    // y reescala una vez por tamaño, y el tamaño solo cambia con el zoom.
    private static BufferedImage zoomedImage(String src, int size) {

        String key = cacheKey(src, size);

        BufferedImage cached = cachedImage(src, size);

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
