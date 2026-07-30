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
import java.util.concurrent.atomic.AtomicInteger;

// Estado del velo de "apagar las luces" de la mesa y su pintado. Lo pinta el tapete al final de su
// paint(), y también el panel de GIFs, que es una ventana suelta y se oscurece por su cuenta (esa,
// como no es la mesa, nunca ha forzado el repintado desde arriba). Antes el velo era el LayerUI de
// un JLayer que envolvía la mesa entera, pero un JLayer obliga a que TODO repintado de cualquiera
// de sus componentes arranque en él, también con las luces encendidas, que es el 99 % de la partida.
public class BrightnessOverlay {

    // El velo tiene DOS dueños y este es el único sitio donde se juntan:
    //
    // 1) El interruptor del jugador (el del tapete y el atajo), que es una preferencia suya y dura
    //    hasta que la cambie.
    // 2) Los apagados TEMPORALES que impone la partida: la pausa y los diálogos que corren con la
    //    mesa a oscuras (game over, recover, buy-in inicial). Se CUENTAN, porque pueden solaparse:
    //    cada uno suma al entrar y resta al salir, y el velo aguanta mientras quede alguno vivo.
    //
    // Antes no había tal separación: cada sitio se guardaba el brillo que había, lo forzaba y lo
    // reponía al terminar, así que dos solapados se pisaban. Una pausa que entrase mientras estaba
    // abierto el game over acababa con la mesa ILUMINADA en plena pausa, y con el interruptor
    // diciendo lo contrario de lo que se veía.
    private volatile boolean user_lights_off = false;
    private final AtomicInteger forced_lights_off = new AtomicInteger(0);
    // Brillo EFECTIVO, el que se pinta: derivado de los dos de arriba, nunca se fija a mano. Hoy
    // todo lo que lo escribe y lo lee corre en el EDT (el crupier toca el velo desde dentro de sus
    // GUIRun), pero se deja volatile y el recálculo sincronizado para que llamarlo desde otro hilo
    // no pueda dejarlo desfasado de forma permanente.
    private volatile float brightness = 0f;
    // Color del velo, recreado solo cuando cambia el brillo. Lo comparten todas las superficies
    // que se oscurecen, que siempre pintan al mismo brillo y desde el EDT.
    private Color cached_color = null;
    private float cached_brightness = -1f;

    // Opacidad del velo negro que corresponde a la luminosidad configurada: su complemento
    // (50 % de luz -> 0,50 de velo). Se acota al rango del ajuste por si la clave del fichero de
    // configuración se editó a mano fuera de él.
    private static float lightsOffBrightness() {

        return (100 - Math.max(GameFrame.NIVEL_LUZ_MIN, Math.min(GameFrame.NIVEL_LUZ, GameFrame.NIVEL_LUZ_MAX))) / 100f;
    }

    // Interruptor del jugador.
    public void lightsOFF() {

        user_lights_off = true;
        refreshBrightness();
    }

    public void lightsON() {

        user_lights_off = false;
        refreshBrightness();
    }

    // Lo que pidió el jugador, INDEPENDIENTEMENTE de que la partida esté forzando el velo ahora
    // mismo: es lo que decide si su siguiente clic enciende o apaga.
    public boolean isUserLightsOff() {
        return user_lights_off;
    }

    // Apagado temporal de la partida. SIEMPRE en pareja (el que suma, resta), preferiblemente con
    // el pop en un finally: si alguien se deja un push suelto, la mesa se queda a oscuras.
    public void pushForcedLightsOFF() {

        forced_lights_off.incrementAndGet();
        refreshBrightness();
    }

    public void popForcedLightsOFF() {

        // Nunca por debajo de cero: un pop de más (un camino de error que restara dos veces) no
        // debe dejar el contador en negativo y que el siguiente push no encienda el velo.
        forced_lights_off.updateAndGet(pending -> pending > 0 ? pending - 1 : 0);
        refreshBrightness();
    }

    // Recalcula el brillo efectivo. Público porque cambiar la luminosidad en Ajustes tiene que
    // reflejarse en el velo que ya esté puesto.
    public synchronized void refreshBrightness() {

        brightness = (user_lights_off || forced_lights_off.get() > 0) ? BrightnessOverlay.lightsOffBrightness() : 0f;
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
