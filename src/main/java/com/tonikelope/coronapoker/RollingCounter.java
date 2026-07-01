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

import java.util.function.DoubleConsumer;

/**
 * Contador numérico que rueda hacia su objetivo a VELOCIDAD CONSTANTE (lineal),
 * para los contadores VIVOS del juego (stack / bote del jugador / bote general).
 *
 * Es fire-and-forget (nunca bloquea ningún hilo) y COALESCENTE: si el objetivo
 * cambia a mitad del rodaje, recalcula el tramo desde el valor mostrado ACTUAL
 * hacia el nuevo objetivo, sin quedarse atrás ni dar saltos. La duración de cada
 * tramo = distancia / velocidad, acotada a [min, max] (un cambio minúsculo no es
 * instantáneo, uno enorme no se eterniza — los "casos extremos" se afinan con
 * estas palancas).
 *
 * CONFINADO AL EDT: roll/set/invalidate y el tick del Timer corren todos en el
 * Event Dispatch Thread, así que los campos no necesitan sincronización. El
 * pintado real lo hace el callback 'render' (escribe el texto del label con el
 * valor dado); este contador solo gobierna la interpolación.
 */
public class RollingCounter {

    private final DoubleConsumer render;

    private double speed;   // unidades de dinero por segundo
    private long min_ms;
    private long max_ms;
    private long fixed_ms;  // >0 => duración FIJA por tramo (tiempo constante); ignora speed/min/max

    private double shown;          // valor pintado ahora mismo
    private boolean shown_valid;   // false si el label muestra algo no-numérico ("----", "ver buy-in"...)
    private double target;
    private double leg_from;
    private long leg_start_ms;
    private long leg_dur_ms;

    private javax.swing.Timer timer;

    public RollingCounter(DoubleConsumer render, double speed, long min_ms, long max_ms) {
        this.render = render;
        this.speed = speed;
        this.min_ms = min_ms;
        this.max_ms = max_ms;
        this.shown_valid = false;
    }

    /**
     * Variante de TIEMPO CONSTANTE: cada tramo dura fixed_ms sin importar la
     * distancia recorrida, así varios contadores que arrancan a la vez terminan
     * a la vez (p.ej. las probabilidades del all-in, que deben alcanzar su valor
     * todas en el mismo tiempo). El resto del comportamiento (coalescente, salto
     * si !animate o valor no válido) es idéntico al de velocidad constante.
     */
    public RollingCounter(DoubleConsumer render, long fixed_ms) {
        this.render = render;
        this.fixed_ms = fixed_ms;
        this.shown_valid = false;
    }

    /**
     * Rueda hacia 'value'. Si animate es false o el valor mostrado no es válido
     * (venía de un estado no-numérico), salta de golpe. EDT-only.
     */
    public void roll(double value, boolean animate) {
        value = Helpers.doubleClean(value);

        if (!animate || !shown_valid) {
            set(value);
            return;
        }

        // Ya vamos justo hacia ese objetivo: no reiniciar el tramo.
        if (timer != null && timer.isRunning() && Helpers.doubleSecureCompare(value, target) == 0) {
            return;
        }

        double dist = Math.abs(value - shown);
        if (Helpers.doubleSecureCompare(0f, dist) == 0) {
            set(value);
            return;
        }

        this.leg_from = shown;
        this.target = value;
        this.leg_start_ms = System.currentTimeMillis();
        this.leg_dur_ms = fixed_ms > 0
                ? fixed_ms
                : Math.max(min_ms, Math.min(max_ms, Math.round(dist / speed * 1000.0)));

        if (timer == null) {
            timer = new javax.swing.Timer(16, (e) -> tick());
        }
        if (!timer.isRunning()) {
            timer.start();
        }
    }

    private void tick() {
        long elapsed = System.currentTimeMillis() - leg_start_ms;
        double p = leg_dur_ms <= 0 ? 1.0 : Math.min(1.0, elapsed / (double) leg_dur_ms);

        shown = leg_from + (target - leg_from) * p;

        if (p >= 1.0) {
            shown = target;
            timer.stop();
        }

        render.accept(Helpers.doubleClean(shown));
    }

    /**
     * Fija 'value' de golpe (sin animar) y lo marca como válido. EDT-only. Lo usan
     * los resets/recover y la cortinilla de llenado (que ya anima frame a frame).
     */
    public void set(double value) {
        if (timer != null) {
            timer.stop();
        }
        value = Helpers.doubleClean(value);
        this.shown = value;
        this.target = value;
        this.shown_valid = true;
        render.accept(value);
    }

    /**
     * El label pasa a mostrar un texto NO numérico (lo pinta el caller). El próximo
     * roll saltará en vez de animar desde un valor que ya no se corresponde. EDT-only.
     */
    public void invalidate() {
        if (timer != null) {
            timer.stop();
        }
        this.shown_valid = false;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public void setBounds(long min_ms, long max_ms) {
        this.min_ms = min_ms;
        this.max_ms = max_ms;
    }
}
