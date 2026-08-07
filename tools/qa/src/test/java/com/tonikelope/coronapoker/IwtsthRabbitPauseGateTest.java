/*
 * Regression tests for the showdown-pause race around IWTSTH and RABBIT.
 *
 * WHY these exist: the showdown countdown (Crupier.pausaConBarra) must NOT keep
 * draining while an IWTSTH ("I want to see the hand") or a RABBIT round-trip is in
 * flight, or the pause closes show_time mid-flight and the request is dropped. The
 * original fix froze the countdown only on the REQUESTER (iwtsthing_request is set
 * only in IWTSTH_REQUEST), leaving the host and other clients draining during the
 * round-trip; it also introduced a regression where a request the host dropped left
 * iwtsthing_request stuck true, freezing the whole table until MAX_VUELTAS_SIN_BAJAR
 * (~10 min).
 *
 * The per-tick decision was extracted into the pure, side-effect-free
 * Crupier.decidePauseTick so it can be pinned here without a GUI, a socket, or real
 * timing. handIdMatches backs the RABBIT hand-id gate that makes the rabbit fee
 * deterministic across peers (replacing the transient show_time guard that caused a
 * one-small-blind stack divergence -> false DIVERGENT "host manipulation" alarm on
 * the next hand).
 *
 * These cover the pure logic only; the actual cross-machine network timing is not
 * unit-testable without the host-authoritative redesign.
 */
package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.Crupier.PauseTick;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IwtsthRabbitPauseGateTest {

    private static final int MAX_VUELTAS = 600;         // tope largo (humano decidiendo si/no)
    private static final int MAX_REQUEST_VUELTAS = 15;  // tope corto (round-trip perdido)

    // Atajo con los dos topes fijados; los flags/contador varían por caso.
    private static PauseTick tick(boolean paused, boolean fin, boolean iwtsthing,
            boolean request, boolean rabbit, int vueltasBefore) {
        return Crupier.decidePauseTick(paused, fin, iwtsthing, request, rabbit,
                vueltasBefore, MAX_VUELTAS, MAX_REQUEST_VUELTAS);
    }

    // ---- decidePauseTick ----

    @Test
    public void decrementsWhenNothingIsInFlight() {
        assertEquals(PauseTick.DECREMENT, tick(false, false, false, false, false, 0));
        assertEquals(PauseTick.DECREMENT, tick(false, false, false, false, false, 123));
    }

    @Test
    public void pausedOrFinishedIsAlwaysIdle() {
        assertEquals(PauseTick.IDLE, tick(true, false, false, false, false, 0));
        assertEquals(PauseTick.IDLE, tick(false, true, false, false, false, 0));
        // Timba pausada gana aunque haya un IWTSTH/rabbit encima: no se cuenta ni se baja.
        assertEquals(PauseTick.IDLE, tick(true, false, true, true, true, 42));
    }

    @Test
    public void holdsWhileAnIwtsthIsActivelyBeingHandled() {
        // iwtsthing=true es el host contestando el si/no: tolerancia LARGA.
        assertEquals(PauseTick.HOLD, tick(false, false, true, false, false, 0));
        assertEquals(PauseTick.HOLD, tick(false, false, true, false, false, 100));
        // Justo en el borde inferior del tope largo sigue congelando.
        assertEquals(PauseTick.HOLD, tick(false, false, true, false, false, MAX_VUELTAS - 1));
    }

    @Test
    public void givesUpOnlyBeyondTheLongCapWhileActive() {
        // vueltasBefore=599 -> este tick es el 600 -> aun HOLD (no supera 600).
        assertEquals(PauseTick.HOLD, tick(false, false, true, false, false, MAX_VUELTAS - 1));
        // vueltasBefore=600 -> este tick es el 601 -> GIVE_UP.
        assertEquals(PauseTick.GIVE_UP, tick(false, false, true, false, false, MAX_VUELTAS));
    }

    @Test
    public void aPendingRequestFreezesBrieflyThenClears() {
        // Peticion enviada que aun no escalo a IWTSTH activo: tolerancia CORTA.
        assertEquals(PauseTick.HOLD, tick(false, false, false, true, false, 0));
        assertEquals(PauseTick.HOLD, tick(false, false, false, true, false, MAX_REQUEST_VUELTAS - 1));
        // Pasado el tope corto, se suelta el flag (mata el cuelgue de 10 min).
        assertEquals(PauseTick.CLEAR_REQUEST, tick(false, false, false, true, false, MAX_REQUEST_VUELTAS));
        assertEquals(PauseTick.CLEAR_REQUEST, tick(false, false, false, true, false, MAX_REQUEST_VUELTAS + 50));
    }

    @Test
    public void anActiveIwtsthNeverGetsClearedByTheShortTimeout() {
        // Si la peticion YA escalo a IWTSTH activo, el tope corto no aplica: manda el largo.
        assertEquals(PauseTick.HOLD, tick(false, false, true, true, false, MAX_REQUEST_VUELTAS + 100));
    }

    @Test
    public void rabbitInFlightFreezesTheCountdown() {
        assertEquals(PauseTick.HOLD, tick(false, false, false, false, true, 0));
        assertEquals(PauseTick.HOLD, tick(false, false, false, false, true, MAX_REQUEST_VUELTAS + 5));
    }

    @Test
    public void aRabbitInFlightKeepsAPendingRequestFrozen() {
        // Con rabbit en curso NO se suelta iwtsthing_request por el tope corto:
        // seguimos congelados (HOLD), no CLEAR_REQUEST.
        assertEquals(PauseTick.HOLD, tick(false, false, false, true, true, MAX_REQUEST_VUELTAS + 5));
    }

    @Test
    public void giveUpTakesPrecedenceOverClearRequest() {
        // Aunque haya una peticion pendiente, superar el tope largo reanuda a la fuerza.
        assertEquals(PauseTick.GIVE_UP, tick(false, false, false, true, false, MAX_VUELTAS));
    }

    // ---- handIdMatches (gate del fee/revelado de RABBIT) ----

    @Test
    public void handIdMatchesOnlyWhenBothPresentAndEqual() {
        byte[] a = {1, 2, 3, 4};
        byte[] aCopy = {1, 2, 3, 4};
        byte[] b = {1, 2, 3, 5};
        assertTrue(Crupier.handIdMatches(a, aCopy), "misma mano -> acepta");
        assertFalse(Crupier.handIdMatches(a, b), "otra mano -> rechaza");
    }

    @Test
    public void handIdMatchesRejectsNullsAndDifferentLengths() {
        byte[] a = {1, 2, 3, 4};
        assertFalse(Crupier.handIdMatches(null, a), "cmd nulo -> rechaza");
        assertFalse(Crupier.handIdMatches(a, null), "current nulo (mano ya cerrada) -> rechaza");
        assertFalse(Crupier.handIdMatches(null, null), "ambos nulos -> rechaza");
        assertFalse(Crupier.handIdMatches(a, new byte[]{1, 2, 3}), "longitud distinta -> rechaza");
    }
}
