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
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Container;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import javax.swing.JLayeredPane;

/**
 * Aviso efímero con el nivel de zoom de la mesa (una lupa y "ZOOM: 100%"), en
 * la esquina superior izquierda del tapete: cada vez que el zoom cambia (menú,
 * atajos, rueda, botones de la barra rápida o el desplegable de Ajustes) sale
 * el nuevo valor, aguanta un momento y se desvanece.
 *
 * Solo hay un aviso a la vez: tocar el zoom con uno puesto lo reescribe y
 * reinicia su cuenta atrás, en vez de apilar avisos. Es transparente al ratón,
 * así que taparle un asiento no le roba el hover ni el click.
 *
 * @author tonikelope
 */
public final class ZoomToast extends javax.swing.JComponent {

    // Separación respecto a la esquina del tapete y cuerpo de la letra. Los dos
    // escalan con el zoom vigente: el aviso crece y encoge con la mesa que anuncia,
    // con un suelo para que a zoom bajo siga leyéndose.
    private static final float MARGIN = 24f;
    private static final float FONT_SIZE = 26f;
    private static final float MIN_FONT_SIZE = 14f;

    // Relleno alrededor del texto y radio de las esquinas, relativos al alto de la
    // línea (así la caja acompaña al cuerpo de la letra sea cual sea el zoom).
    private static final float PAD_RATIO = 0.5f;
    private static final float ARC_RATIO = 0.6f;

    // Lupa a la izquierda del texto. Va dibujada a trazo, no con el icono del menú
    // ZOOM (24 px): así sale nítida a cualquier zoom y comparte el blanco y el grosor
    // de la letra. Lado, hueco hasta el texto, cristal y trazo, todos relativos al
    // alto de la letra para que el conjunto escale de una pieza.
    private static final float LENS_SIDE_RATIO = 1f;
    private static final float LENS_GAP_RATIO = 0.35f;
    private static final float LENS_GLASS_RATIO = 0.68f;
    private static final float LENS_STROKE_RATIO = 0.1f;

    // Tiempo a la vista antes de empezar a desvanecerse, y duración del fundido.
    private static final int HOLD_MS = 1200;
    private static final int FADE_MS = 400;
    private static final int FADE_INTERVAL_MS = 16;

    private static final Color BACKGROUND = new Color(0, 0, 0, 190);
    private static final Color BORDER = new Color(255, 255, 255, 90);
    private static final Color TEXT = Color.WHITE;

    private static volatile ZoomToast current = null;
    private static javax.swing.Timer hold_timer = null;
    private static javax.swing.Timer fade_timer = null;

    // Estado del único aviso vivo: solo se toca desde el EDT.
    private String text = null;
    private float opacity = 1f;

    private ZoomToast() {
        setOpaque(false);
        setFocusable(false);
    }

    /**
     * Anuncia el zoom vigente de la mesa. Si ya había un aviso puesto lo
     * reescribe (mismo sitio, texto nuevo y cuenta atrás desde cero). Seguro
     * desde cualquier hilo; fuera de partida no hace nada.
     */
    public static void showZoom() {

        // El porcentaje se lee ya dentro del EDT, no lo trae el llamante: varias
        // pulsaciones seguidas del zoom trabajan cada una en su hilo, y así el aviso
        // canta el nivel vigente y no el que había cuando arrancó ese hilo.
        Helpers.GUIRun(() -> display("ZOOM: " + Math.round((1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP) * 100f) + "%"));
    }

    // Monta (o reaprovecha) el aviso sobre el tapete. Solo en el EDT.
    private static void display(final String text) {

        GameFrame gf = GameFrame.getInstance();

        if (gf == null || gf.getTapete() == null || !gf.getTapete().isShowing()) {
            return;
        }

        final JLayeredPane tapete = gf.getTapete();

        ZoomToast toast = current;

        // Se reaprovecha el que ya está puesto; si el tablero ha cambiado bajo él
        // (partida nueva), se descarta y se monta uno en el tapete actual.
        if (toast == null || toast.getParent() != tapete) {
            hideZoom();
            toast = new ZoomToast();
            current = toast;
            tapete.add(toast, JLayeredPane.DRAG_LAYER);
        }

        final Rectangle old = toast.getBounds();

        toast.text = text;
        toast.opacity = 1f;
        toast.layoutIn(tapete);

        restartTimers();

        // La caja nueva puede ser más estrecha que la que sustituye (de "100%" a
        // "95%"): se repinta la unión para no dejar el resto colgado.
        final Rectangle dirty = old.union(toast.getBounds());

        tapete.repaint(dirty.x, dirty.y, dirty.width, dirty.height);
    }

    // Ajusta la caja al texto con la letra del zoom vigente y la clava en la esquina
    // superior izquierda del tapete, sin salirse de él en ventanas diminutas.
    private void layoutIn(final JLayeredPane tapete) {

        final float zoom = 1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP;

        final Font font = zoomedFont(zoom);

        setFont(font);

        final FontMetrics fm = getFontMetrics(font);

        final int pad = Math.round(fm.getHeight() * PAD_RATIO);
        final int w = Math.min(fm.stringWidth(text) + contentOffset(fm) + 2 * pad, tapete.getWidth());
        final int h = fm.getHeight() + pad;
        final int margin = Math.round(MARGIN * zoom);

        setBounds(Math.min(margin, Math.max(0, tapete.getWidth() - w)),
                Math.min(margin, Math.max(0, tapete.getHeight() - h)), w, h);
    }

    // Lado de la lupa con la letra dada.
    private static int lensSide(final FontMetrics fm) {
        return Math.round(fm.getAscent() * LENS_SIDE_RATIO);
    }

    // Lo que la lupa (y su hueco) desplaza al texto hacia la derecha.
    private static int contentOffset(final FontMetrics fm) {

        final int side = lensSide(fm);

        return side + Math.round(side * LENS_GAP_RATIO);
    }

    // Dibuja la lupa dentro del cuadrado (x, y, side): el cristal arriba a la
    // izquierda y el mango en diagonal hasta la esquina de abajo a la derecha.
    private static void paintLens(final Graphics2D g2, final int x, final int y, final int side) {

        final float stroke = Math.max(1.5f, side * LENS_STROKE_RATIO);
        final float inset = stroke / 2f;
        final float glass = side * LENS_GLASS_RATIO;

        g2.setColor(TEXT);
        g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(new Ellipse2D.Float(x + inset, y + inset, glass, glass));

        // El mango arranca donde el cristal corta la diagonal a 45 grados.
        final float radius = glass / 2f;
        final float center = radius + inset;
        final float diagonal = (float) (radius * Math.sqrt(0.5));

        g2.draw(new Line2D.Float(x + center + diagonal, y + center + diagonal, x + side - inset, y + side - inset));
    }

    private static Font zoomedFont(final float zoom) {

        final float size = Math.max(MIN_FONT_SIZE, FONT_SIZE * zoom);

        return Helpers.GUI_FONT != null ? Helpers.GUI_FONT.deriveFont(Font.BOLD, size) : new Font("Dialog", Font.BOLD, Math.round(size));
    }

    // (Re)arranca la cuenta: HOLD_MS a la vista y luego el fundido. Tocar el zoom con
    // el aviso puesto la reinicia desde cero, así el último valor manda.
    private static void restartTimers() {

        if (hold_timer == null) {
            hold_timer = new javax.swing.Timer(HOLD_MS, e -> {
                hold_timer.stop();
                startFade();
            });
            hold_timer.setRepeats(false);
        }

        if (fade_timer == null) {
            fade_timer = new javax.swing.Timer(FADE_INTERVAL_MS, e -> {
                ZoomToast toast = current;
                if (toast == null) {
                    fade_timer.stop();
                    return;
                }
                toast.opacity -= FADE_INTERVAL_MS / (float) FADE_MS;
                if (toast.opacity <= 0f) {
                    hideZoom();
                } else {
                    toast.repaint();
                }
            });
        }

        fade_timer.stop();
        hold_timer.restart();
    }

    private static void startFade() {

        ZoomToast toast = current;

        if (toast != null) {
            toast.opacity = 1f;
            fade_timer.start();
        }
    }

    // Retira el aviso y para sus temporizadores. Idempotente; solo en el EDT.
    private static void hideZoom() {

        if (hold_timer != null) {
            hold_timer.stop();
        }

        if (fade_timer != null) {
            fade_timer.stop();
        }

        ZoomToast toast = current;

        if (toast != null) {
            Container parent = toast.getParent();
            if (parent != null) {
                Rectangle bounds = toast.getBounds();
                parent.remove(toast);
                parent.repaint(bounds.x, bounds.y, bounds.width, bounds.height);
            }
            current = null;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {

        if (text == null || text.isEmpty()) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();

        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, opacity))));

            final int arc = Math.round(getHeight() * ARC_RATIO);

            g2.setColor(BACKGROUND);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g2.setColor(BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);

            g2.setFont(getFont());

            final FontMetrics fm = g2.getFontMetrics();

            // Lupa y texto van como un bloque centrado en la caja.
            final int side = lensSide(fm);

            int x = (getWidth() - (contentOffset(fm) + fm.stringWidth(text))) / 2;

            paintLens(g2, x, (getHeight() - side) / 2, side);

            x += contentOffset(fm);

            g2.setColor(TEXT);
            g2.drawString(text, x, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());

        } finally {
            g2.dispose();
        }
    }

    // Transparente al ratón: es un cartel, no debe quedarse con el hover ni con los
    // clicks de lo que tape (asientos, tapete).
    @Override
    public boolean contains(int x, int y) {
        return false;
    }
}
