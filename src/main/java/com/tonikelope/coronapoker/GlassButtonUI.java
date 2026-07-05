/*
 * Copyright (C) 2026 tonikelope
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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonModel;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicButtonUI;

/**
 * ButtonUI "cristal" (glassmorphism) para la botonera de la pantalla de inicio: pinta un
 * fondo negro TRANSLÚCIDO redondeado (se ve el tapete a través), un brillo superior sutil,
 * un borde fino y, opcionalmente, un tinte/halo de acento. El estado de hover/pressed se lee
 * del propio ButtonModel (rollover), así que NO hace falta MouseListener externo.
 *
 * <p>No es invasivo: se aplica con {@code button.setUI(new GlassButtonUI(...))} sobre los
 * JButton existentes (sin tocar el código generado ni el .form); {@code installUI} deja el
 * botón no-opaco y sin borde/relleno propios para pintar solo lo nuestro.
 *
 * @author tonikelope
 */
public class GlassButtonUI extends BasicButtonUI {

    // Acento tintado del relleno + borde/halo (null = cristal neutro, borde blanco tenue).
    private final Color accent;
    // Relleno tintado con el acento (acción primaria destacada, p. ej. CREAR TIMBA).
    private final boolean filled_accent;
    // El acento SOLO aparece al pasar el ratón (borde/halo); en reposo el botón es neutro
    // (p. ej. SALIR: cristal neutro que se tiñe de rojo al hover).
    private final boolean hover_only_accent;
    // Opacidad base del cristal en reposo (secundarios más transparentes).
    private final float base_alpha;
    private final int radius;

    public GlassButtonUI(Color accent, boolean filled_accent, boolean hover_only_accent, float base_alpha, int radius) {
        this.accent = accent;
        this.filled_accent = filled_accent;
        this.hover_only_accent = hover_only_accent;
        this.base_alpha = base_alpha;
        this.radius = radius;
    }

    @Override
    public void installUI(JComponent c) {
        super.installUI(c);
        AbstractButton b = (AbstractButton) c;
        b.setOpaque(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setRolloverEnabled(true);
        b.setForeground(Color.WHITE);
        // Margen interior: el icono/texto no debe tocar el borde redondeado.
        b.setBorder(BorderFactory.createEmptyBorder(10, 22, 10, 22));
        b.setIconTextGap(14);
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        AbstractButton b = (AbstractButton) c;
        ButtonModel m = b.getModel();
        int w = c.getWidth();
        int h = c.getHeight();

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        boolean enabled = b.isEnabled();
        boolean pressed = m.isArmed() && m.isPressed();
        boolean hover = enabled && m.isRollover();

        // Hover/pressed suben la opacidad RELATIVA a la base (así al subir base_alpha el realce
        // se mantiene proporcional en vez de saturarse).
        float fill_alpha;
        if (!enabled) {
            fill_alpha = 0.30f;
        } else if (pressed) {
            fill_alpha = Math.min(0.92f, base_alpha + 0.22f);
        } else if (hover) {
            fill_alpha = Math.min(0.85f, base_alpha + 0.12f);
        } else {
            fill_alpha = base_alpha;
        }

        RoundRectangle2D rr = new RoundRectangle2D.Float(1.5f, 1.5f, w - 3f, h - 3f, radius, radius);

        // 1. Cristal: negro translúcido.
        g2.setColor(new Color(0f, 0f, 0f, fill_alpha));
        g2.fill(rr);

        // 2. Tinte de acento (solo primario relleno).
        if (filled_accent && accent != null && enabled) {
            int top = Math.round((hover ? 0.34f : 0.22f) * 255);
            int bot = Math.round((hover ? 0.14f : 0.08f) * 255);
            GradientPaint gp = new GradientPaint(
                    0, 0, new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), top),
                    0, h, new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), bot));
            g2.setPaint(gp);
            g2.fill(rr);
        }

        // 3. Brillo superior (glass highlight) en la mitad de arriba.
        GradientPaint gloss = new GradientPaint(
                0, 1.5f, new Color(255, 255, 255, hover ? 60 : 42),
                0, h * 0.55f, new Color(255, 255, 255, 0));
        g2.setPaint(gloss);
        g2.fill(new RoundRectangle2D.Float(1.5f, 1.5f, w - 3f, h * 0.55f, radius, radius));

        // (Sin borde: los botones van sin línea de contorno, solo el cristal redondeado.)
        // 5. Halo de acento al pasar el ratón (glow suave; SALIR se tiñe de rojo).
        if (hover && accent != null) {
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 70));
            g2.setStroke(new BasicStroke(3.5f));
            g2.draw(new RoundRectangle2D.Float(0f, 0f, w - 0.5f, h - 0.5f, radius + 4f, radius + 4f));
        }

        g2.dispose();

        // Etiqueta (icono + texto) encima, delegando en BasicButtonUI.
        super.paint(g, c);
    }

    // Texto con una sombra sutil para asegurar legibilidad sobre el cristal + tapete.
    @Override
    protected void paintText(Graphics g, AbstractButton b, Rectangle textRect, String text) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Color fg = b.getForeground();
        Rectangle shadow = new Rectangle(textRect.x + 1, textRect.y + 1, textRect.width, textRect.height);
        b.setForeground(new Color(0, 0, 0, 150));
        super.paintText(g2, b, shadow, text);
        b.setForeground(fg);
        super.paintText(g2, b, textRect, text);
        g2.dispose();
    }
}
