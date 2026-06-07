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

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Mantiene la pantalla despierta mientras la partida está en pantalla completa.
 *
 * <ul>
 *   <li><b>Windows</b>: {@code SetThreadExecutionState} (Kernel32, vía JNA). Le
 *       dice al SO "no apagues la pantalla ni suspendas" — NO simula input
 *       (no toca ratón ni teclado). Es por-hilo, así que siempre se invoca desde
 *       el mismo hilo del timer; al cerrar la app el SO libera el estado solo.</li>
 *   <li><b>Linux y macOS</b>: no hay API nativa de inhibición accesible de forma
 *       limpia y uniforme (en Linux variaría entre X11 y Wayland, y D-Bus exigiría
 *       otra dependencia), así que se usa una tecla no-op (F15) vía Robot: NO mueve
 *       el cursor —a diferencia de un "jiggle" de ratón, no dispara hover— y es
 *       inocua. Es el único punto donde se simula input, deliberadamente, por no
 *       haber alternativa nativa limpia, y funciona igual en X11 y Wayland.</li>
 * </ul>
 *
 * Cualquier fallo de la vía nativa de Windows (JNA ausente, etc.) se captura y
 * degrada al fallback de tecla sin romper: la clase nunca propaga.
 *
 * @author tonikelope
 */
public final class ScreenWakeLock {

    private static final Logger LOGGER = Logger.getLogger(ScreenWakeLock.class.getName());

    private static volatile boolean native_unavailable = false;

    private ScreenWakeLock() {
    }

    /**
     * Refresca el wake-lock según el estado de pantalla completa. Pensado para
     * llamarse periódicamente (el timer anti-screensaver). Idempotente y
     * silencioso ante fallos.
     *
     * @param fullscreen true si el juego está en pantalla completa (único estado
     * en el que se mantiene la pantalla despierta).
     * @param fallback_robot Robot reutilizable para el fallback de tecla
     * (Linux/macOS); puede ser null (entonces el fallback simplemente no actúa).
     */
    public static void refresh(boolean fullscreen, Robot fallback_robot) {

        // Windows: API nativa, sin simular input.
        if (Helpers.OSValidator.isWindows() && !native_unavailable) {
            try {
                // ES_CONTINUOUS hace el estado persistente para este hilo; con
                // los flags de display/sistema en fullscreen se mantiene
                // despierto, y sin ellos (solo ES_CONTINUOUS) se libera.
                int flags = WinBase.ES_CONTINUOUS
                        | (fullscreen ? (WinBase.ES_DISPLAY_REQUIRED | WinBase.ES_SYSTEM_REQUIRED) : 0);
                Kernel32.INSTANCE.SetThreadExecutionState(flags);
                return;
            } catch (Throwable t) {
                native_unavailable = true;
                LOGGER.log(Level.WARNING, "Native screen wake-lock unavailable — falling back to no-op key", t);
            }
        }

        // Linux / macOS (o Windows si la vía nativa falló): tecla no-op. No mueve
        // el cursor, así que no dispara eventos de hover; solo en fullscreen.
        if (fullscreen && fallback_robot != null) {
            try {
                fallback_robot.keyPress(KeyEvent.VK_F15);
                fallback_robot.keyRelease(KeyEvent.VK_F15);
            } catch (Exception ex) {
                // VK_F15 puede no estar mapeada en alguna plataforma: lo dejamos
                // pasar (ese tick no refresca el idle timer, sin más).
                LOGGER.log(Level.FINE, "No-op key fallback failed", ex);
            }
        }
    }
}
