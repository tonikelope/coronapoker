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
import javax.swing.JSpinner;

/**
 * JSpinner que, cuando está DESHABILITADO, se pinta translúcido (a la misma
 * opacidad que los botones de la botonera, TranslucentDisabledButton
 * .DISABLED_OPACITY) para que el control no disponible se atenúe y deje
 * entrever el tapete detrás. Habilitado se pinta exactamente igual que un
 * JSpinner normal.
 *
 * Es el spinner de apuesta del LocalPlayer; se cablea vía el .form igual que
 * el resto de componentes custom del proyecto. NO es opaco a propósito: al
 * repintarse, Swing refresca el antecesor opaco (el tapete) y el alpha compone
 * sobre píxeles frescos.
 *
 * @author tonikelope
 */
public class TranslucentDisabledSpinner extends JSpinner {

    public TranslucentDisabledSpinner() {
        setOpaque(false);
    }

    @Override
    public void paint(Graphics g) {
        if (isEnabled()) {
            super.paint(g);
        } else {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, TranslucentDisabledButton.DISABLED_OPACITY));
                super.paint(g2);
            } finally {
                g2.dispose();
            }
        }
    }
}
