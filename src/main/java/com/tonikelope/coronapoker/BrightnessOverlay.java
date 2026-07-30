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

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;

// Estado del velo de "apagar las luces" de la mesa y su pintado. Lo pintan el propio tapete al
// final de su paint() y las ventanas sueltas que también deben oscurecerse (el panel de GIFs).
// Antes era el LayerUI de un JLayer que envolvía la mesa entera, pero un JLayer obliga a que TODO
// repintado de cualquiera de sus componentes arranque en él, también con las luces encendidas,
// que es el 99 % de la partida.
public class BrightnessOverlay {

    // Lo escribe el EDT y lo leen los hilos del crupier (que guardan el brillo previo antes de
    // apagar por su cuenta en el game over, el recover y el buy-in inicial).
    private volatile float brightness = 0f;
    // Color del velo, recreado solo cuando cambia el brillo. Lo comparten todas las superficies
    // que se oscurecen, que siempre pintan al mismo brillo y desde el EDT.
    private Color cached_color = null;
    private float cached_brightness = -1f;

    // Opacidad del velo negro que corresponde a la luminosidad configurada: su complemento
    // (50 % de luz -> 0,50 de velo). Se acota al rango del ajuste por si la clave del fichero de
    // configuración se editó a mano fuera de él.
    public static float lightsOffBrightness() {

        return (100 - Math.max(GameFrame.NIVEL_LUZ_MIN, Math.min(GameFrame.NIVEL_LUZ, GameFrame.NIVEL_LUZ_MAX))) / 100f;
    }

    public void lightsOFF() {

        setBrightness(BrightnessOverlay.lightsOffBrightness());
    }

    public void lightsON() {

        setBrightness(0f);
    }

    public void setBrightness(float brightness) {
        this.brightness = brightness;
    }

    public float getBrightness() {
        return brightness;
    }

    // Vuelca el velo sobre toda la superficie indicada. No-op con las luces encendidas. Se llama
    // DESPUÉS de pintar el contenido (en paint(), no en paintComponent()), para que quede encima.
    public void paintOverlay(Graphics g, int width, int height) {

        float b = getBrightness();

        if (b > 0f) {
            if (cached_color == null || cached_brightness != b) {
                cached_color = new Color(0f, 0f, 0f, b);
                cached_brightness = b;
            }
            Graphics2D g2d = (Graphics2D) g.create();
            try {
                g2d.setColor(cached_color);
                g2d.fillRect(0, 0, width, height);
            } finally {
                g2d.dispose();
            }
        }
    }

}
