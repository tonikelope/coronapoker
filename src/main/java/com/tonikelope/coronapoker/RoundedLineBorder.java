/*
 * Copyright (C) 2025 tonikelope
 */
package com.tonikelope.coronapoker;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.border.Border;

/**
 * Borde de línea con esquinas REDONDEADAS (el createLineBorder de Swing solo dibuja esquinas
 * cuadradas). Mismo color y grosor que un LineBorder normal, pero traza un rectángulo redondeado
 * con antialiasing. Se usa para los recuadros finos de agrupación de los diálogos de configuración
 * (tope/escalada de ciegas, recompra), que antes tenían esquinas cuadradas.
 *
 * Las esquinas del panel que quedan FUERA del arco muestran el fondo del propio panel; como en los
 * diálogos Nimbus ese fondo coincide con el del contenedor, el redondeo se ve limpio sin necesidad
 * de hacer el panel no-opaco.
 *
 * @author tonikelope
 */
public class RoundedLineBorder implements Border {

    private final Color color;
    private final int thickness;
    private final int arc;

    public RoundedLineBorder(Color color, int thickness, int arc) {
        this.color = color;
        this.thickness = Math.max(1, thickness);
        this.arc = arc;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(thickness));
        // Desplaza medio grosor para que el trazo quede dentro de los límites del componente.
        float off = thickness / 2f;
        g2.draw(new RoundRectangle2D.Float(x + off, y + off, width - thickness, height - thickness, arc, arc));
        g2.dispose();
    }

    @Override
    public Insets getBorderInsets(Component c) {
        int n = thickness + 2;
        return new Insets(n, n, n, n);
    }

    @Override
    public boolean isBorderOpaque() {
        return false;
    }
}
