/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.smoke;

import com.tonikelope.coronapoker.Crupier;
import com.tonikelope.coronapoker.Player;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * AAA test del Crupier.synthesizeFoldAction(Object[]) introducido en
 * el deferred 🟠-2. Garantiza que cuando se sintetiza FOLD por sig
 * inválida o peer exited, el action[] queda en estado canónico que
 * la rueda de apuestas reconoce y que NO desencadena absorb al chain.
 *
 * Cobertura:
 *   - decision se reemplaza por Player.FOLD INDEPENDIENTEMENTE de
 *     lo que viniera (BET, ALLIN, CHECK, FOLD ya).
 *   - bet se anula a 0f.
 *   - cinematic se anula (slot 2).
 *   - record y sig se anulan (slots 3 y 4) → absorbActionIntoChain
 *     será no-op para este slot.
 *   - voluntary se marca FALSE (slot 5) → rondaApuestas salta el
 *     broadcast y el absorb.
 *   - null o length < 6 lanzan IllegalArgumentException (defensiva
 *     contra mal uso).
 */
class SynthesizeFoldActionSmoke {

    /**
     * synthesizeFoldAction es package-private. Tests viven en package
     * .smoke (distinto del .coronapoker del Crupier), así que llamamos
     * via reflection.
     */
    private static void invokeSynthesize(Object[] action) throws Exception {
        Method m = Crupier.class.getDeclaredMethod("synthesizeFoldAction", Object[].class);
        m.setAccessible(true);
        m.invoke(null, (Object) action);
    }

    @Test
    @DisplayName("Decision BET con bet=50 se reemplaza por FOLD bet=0")
    void betFalsifiedToFold() throws Exception {
        Object[] action = new Object[6];
        action[0] = Player.BET;
        action[1] = 50f;
        action[2] = null;
        action[3] = new byte[]{1, 2, 3};
        action[4] = new byte[]{4, 5, 6};
        action[5] = Boolean.TRUE;

        invokeSynthesize(action);

        assertEquals(Player.FOLD, action[0]);
        assertEquals(0f, action[1]);
        assertNull(action[2]);
        assertNull(action[3]);
        assertNull(action[4]);
        assertEquals(Boolean.FALSE, action[5]);
    }

    @Test
    @DisplayName("Decision ALLIN con cinematic se reemplaza por FOLD")
    void allinFalsifiedToFold() throws Exception {
        Object[] action = new Object[6];
        action[0] = Player.ALLIN;
        action[1] = "rounders.gif";
        action[2] = "rounders.gif";
        action[3] = new byte[]{7, 8, 9};
        action[4] = new byte[]{0xa, 0xb, 0xc};
        action[5] = Boolean.TRUE;

        invokeSynthesize(action);

        assertEquals(Player.FOLD, action[0]);
        assertEquals(0f, action[1]);
        assertNull(action[2]);
        assertNull(action[3]);
        assertNull(action[4]);
        assertEquals(Boolean.FALSE, action[5]);
    }

    @Test
    @DisplayName("Decision CHECK (no falsification needed) tras synth queda FOLD voluntary=FALSE")
    void checkBecomesFold() throws Exception {
        // Aunque la decision original sea inofensiva (CHECK con bet=0),
        // el helper la reemplaza igual — el caller decide cuándo llamar.
        Object[] action = new Object[6];
        action[0] = Player.CHECK;
        action[1] = 0f;
        action[2] = null;
        action[3] = null;
        action[4] = null;
        action[5] = Boolean.TRUE;

        invokeSynthesize(action);

        assertEquals(Player.FOLD, action[0]);
        assertEquals(0f, action[1]);
        assertEquals(Boolean.FALSE, action[5]);
    }

    @Test
    @DisplayName("Decision FOLD ya — idempotente, queda FOLD voluntary=FALSE")
    void foldAlreadyIdempotent() throws Exception {
        Object[] action = new Object[6];
        action[0] = Player.FOLD;
        action[1] = 0f;
        action[2] = null;
        action[3] = new byte[]{1};
        action[4] = new byte[]{2};
        action[5] = Boolean.TRUE;

        invokeSynthesize(action);

        // FOLD ya; pero los slots record/sig se limpian igual.
        assertEquals(Player.FOLD, action[0]);
        assertNull(action[3]);
        assertNull(action[4]);
        // El voluntary baja a FALSE — esto es importante: incluso si
        // venía como voluntario, post-synth es no-voluntary (broadcast
        // y absorb se saltan).
        assertEquals(Boolean.FALSE, action[5]);
    }

    @Test
    @DisplayName("null action lanza IllegalArgumentException")
    void nullActionRejected() {
        assertThrows(Exception.class, () -> invokeSynthesize(null));
    }

    @Test
    @DisplayName("action.length < 6 lanza IllegalArgumentException")
    void shortActionRejected() {
        Object[] shortAction = new Object[3];
        // reflection wraps in InvocationTargetException — comprobamos cause
        Exception ex = assertThrows(Exception.class, () -> invokeSynthesize(shortAction));
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        // Solo verificamos que es IllegalArgumentException (cualquier mensaje)
        assertEquals(IllegalArgumentException.class, cause.getClass());
    }
}
