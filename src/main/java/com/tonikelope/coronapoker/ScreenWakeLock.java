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

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Mantiene la pantalla despierta mientras la partida está en pantalla completa,
 * SIN simular actividad de usuario donde el sistema operativo ofrece una API
 * nativa para ello:
 *
 * <ul>
 *   <li><b>Windows</b>: {@code SetThreadExecutionState} (Kernel32, vía JNA). Le
 *       dice al SO "no apagues la pantalla ni suspendas" — no toca ratón ni
 *       teclado. Es por-hilo, así que siempre se invoca desde el mismo hilo del
 *       timer; al cerrar la app el SO libera el estado solo.</li>
 *   <li><b>Linux (X11)</b>: {@code XResetScreenSaver} de libX11 — resetea el
 *       temporizador del salvapantallas sin inyectar input.</li>
 *   <li><b>Resto (macOS, Wayland sin XWayland, o si la vía nativa falla)</b>:
 *       fallback a una tecla no-op (F15) vía Robot. No mueve el cursor (a
 *       diferencia de un "jiggle" de ratón, no dispara hover) y es inocua, pero
 *       sí simula input — solo se usa donde no hay alternativa nativa limpia.</li>
 * </ul>
 *
 * Todo fallo nativo (JNA ausente, libX11 ausente en Wayland puro, etc.) se
 * captura y degrada al fallback sin romper nada: la clase nunca propaga.
 *
 * @author tonikelope
 */
public final class ScreenWakeLock {

    private static final Logger LOGGER = Logger.getLogger(ScreenWakeLock.class.getName());

    // X11: ScreenSaverReset / no-op para XForceScreenSaver no hace falta; usamos
    // XResetScreenSaver, que resetea el contador del salvapantallas del display.
    private interface X11 extends Library {

        Pointer XOpenDisplay(String name);

        int XResetScreenSaver(Pointer display);

        int XFlush(Pointer display);

        int XCloseDisplay(Pointer display);
    }

    private static volatile boolean native_unavailable = false;
    private static volatile X11 x11 = null;
    private static volatile boolean x11_load_tried = false;

    private ScreenWakeLock() {
    }

    /**
     * Refresca el wake-lock según el estado de pantalla completa. Pensado para
     * llamarse periódicamente (el timer anti-screensaver). Idempotente y
     * silencioso ante fallos.
     *
     * @param fullscreen true si el juego está en pantalla completa (único estado
     * en el que se mantiene la pantalla despierta).
     * @param fallback_robot Robot reutilizable para el fallback de tecla; puede
     * ser null (entonces el fallback simplemente no actúa).
     */
    public static void refresh(boolean fullscreen, Robot fallback_robot) {

        if (!native_unavailable) {
            try {
                if (Helpers.OSValidator.isWindows()) {
                    // ES_CONTINUOUS hace el estado persistente para este hilo;
                    // con los flags de display/sistema en fullscreen se mantiene
                    // despierto, y sin ellos (solo ES_CONTINUOUS) se libera.
                    int flags = WinBase.ES_CONTINUOUS
                            | (fullscreen ? (WinBase.ES_DISPLAY_REQUIRED | WinBase.ES_SYSTEM_REQUIRED) : 0);
                    Kernel32.INSTANCE.SetThreadExecutionState(flags);
                    return;
                } else if (Helpers.OSValidator.isUnix()) {
                    // Fuera de fullscreen no hay nada que hacer: XResetScreenSaver
                    // solo resetea el contador, no instala un lock persistente.
                    if (!fullscreen) {
                        return;
                    }
                    X11 lib = x11Lib();
                    if (lib != null) {
                        Pointer display = lib.XOpenDisplay(null);
                        if (display != null) {
                            try {
                                lib.XResetScreenSaver(display);
                                lib.XFlush(display);
                            } finally {
                                lib.XCloseDisplay(display);
                            }
                            return;
                        }
                    }
                    // libX11 no disponible (Wayland puro/headless): NO hacemos
                    // return — caemos al fallback de tecla de abajo.
                }
                // macOS / otros: no hay vía nativa sin dependencias extra → fallback.
            } catch (Throwable t) {
                native_unavailable = true;
                LOGGER.log(Level.WARNING, "Native screen wake-lock unavailable — falling back to no-op key", t);
            }
        }

        // Fallback (macOS/otros, o si la vía nativa falló): tecla no-op. No mueve
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

    private static X11 x11Lib() {
        if (!x11_load_tried) {
            synchronized (ScreenWakeLock.class) {
                if (!x11_load_tried) {
                    try {
                        x11 = Native.load("X11", X11.class);
                    } catch (Throwable t) {
                        // Wayland puro sin libX11, o headless: nos quedaremos en
                        // el fallback de tecla.
                        LOGGER.log(Level.INFO, "libX11 not available — no native screen wake-lock on this Linux session", t);
                        x11 = null;
                    }
                    x11_load_tried = true;
                }
            }
        }
        return x11;
    }
}
