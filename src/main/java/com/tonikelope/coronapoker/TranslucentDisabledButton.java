/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker;

import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JButton;

/**
 * JButton que, cuando está DESHABILITADO, se pinta translúcido (al
 * DISABLED_OPACITY * 100 %) para que la acción no disponible se atenúe y deje
 * entrever el tapete detrás, en vez de mostrarse como un botón gris sólido.
 * Habilitado se pinta exactamente igual que un JButton normal (sin coste).
 *
 * Se usa en la botonera del LocalPlayer (ir/pasar/apostar/all-in) vía el .form
 * (clase del componente cambiada a esta), siguiendo el mismo patrón que el
 * resto de componentes custom del proyecto (Card, LatencyDot, RoundedPanel...).
 *
 * El componente NO es opaco a propósito: así, al repintarse, Swing refresca el
 * antecesor opaco (el tapete) y el alpha compone sobre píxeles frescos, sin
 * arrastres.
 *
 * @author tonikelope
 */
public class TranslucentDisabledButton extends JButton {

    // Opacidad del control cuando está deshabilitado (0..1). Igualada al alpha
    // del borde/fondo en reposo del LocalPlayer —Color(204,204,204,75)— para que
    // coincidan exactamente (75/255 ≈ 29,4 %). La comparte TranslucentDisabledSpinner.
    public static final float DISABLED_OPACITY = 75f / 255f;

    public TranslucentDisabledButton() {
        setOpaque(false);
    }

    @Override
    public void paint(Graphics g) {
        if (isEnabled()) {
            super.paint(g);
        } else {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, DISABLED_OPACITY));
                super.paint(g2);
            } finally {
                g2.dispose();
            }
        }
    }
}
