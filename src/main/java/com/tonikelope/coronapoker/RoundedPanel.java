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

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JPanel;

/**
 *
 * @author tonikelope
 */
public class RoundedPanel extends JPanel {

    public static final int DEFAULT_ARC = 20;
    private final int arc;
    // Mismo patrón que el asiento (LocalPlayer/RemotePlayer.setOpaque): el panel NUNCA es opaco
    // de verdad. Si lo fuese, Swing no repintaría el fondo detrás y las esquinas —fuera del
    // arco— mostrarían basura: un setBackground AISLADO (fuera de un repaint grande) deja
    // negro en ellas, porque el RepaintManager optimiza el repaint de la región cubierta por un
    // componente opaco y no re-pinta el ancestro. Se intercepta setOpaque y se recuerda la
    // INTENCIÓN de relleno en rounded_fill, que pintamos nosotros (redondeado) en paintComponent;
    // Swing repinta el fondo detrás (el panel es no-opaco) y las esquinas quedan limpias.
    private volatile boolean rounded_fill = false;

    public RoundedPanel() {
        this(DEFAULT_ARC);
    }

    public RoundedPanel(int arc) {
        super();
        this.arc = arc;
        setOpaque(true);
    }

    @Override
    public void setOpaque(boolean isOpaque) {
        this.rounded_fill = isOpaque;
        super.setOpaque(false);
    }

    // Intención de relleno (lo que se pasó a setOpaque). No usar isOpaque() para esto: el panel
    // es SIEMPRE no-opaco de cara a Swing (para que repinte el fondo detrás), así que isOpaque()
    // devuelve false aunque el panel esté pintando su relleno.
    public boolean isRoundedFill() {
        return rounded_fill;
    }

    @Override
    protected void paintComponent(Graphics g) {

        if (rounded_fill) {
            Graphics2D g2d = (Graphics2D) g.create();
            try {
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(getBackground());
                float effective_arc = arc * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP);
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), Math.round(effective_arc), Math.round(effective_arc));
            } finally {
                g2d.dispose();
            }
            // No se llama a super.paintComponent: Swing ya ha repintado el fondo detrás (el panel
            // es no-opaco) y el relleno redondeado va encima; super pintaría un fondo cuadrado.
        } else {
            super.paintComponent(g);
        }
    }
}
