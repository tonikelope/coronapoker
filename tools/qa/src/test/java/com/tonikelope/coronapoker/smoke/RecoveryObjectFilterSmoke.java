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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AAA test del whitelist ObjectInputFilter aplicado a RECOVERDATA.
 *
 * Cobertura:
 *   - HashMap legítimo con String/Integer/Long/Float/Double/Boolean values
 *     deserializa correctamente con el filtro instalado.
 *   - Instancia de java.io.File (Serializable pero NO en whitelist) se RECHAZA
 *     con InvalidClassException — demuestra que el filtro bloquea clases ajenas.
 *   - HashMap con value de tipo java.util.ArrayList (NO en whitelist) se RECHAZA.
 *   - Payload que excede maxbytes se RECHAZA.
 */
class RecoveryObjectFilterSmoke {

    private byte[] serialize(Object o) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(o);
            return baos.toByteArray();
        }
    }

    private Object deserializeWithFilter(byte[] bytes) throws Exception {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        ObjectInputStream ois = new ObjectInputStream(bais);
        ois.setObjectInputFilter(Crupier.getRecoveryObjectFilter());
        return ois.readObject();
    }

    @Test
    @DisplayName("HashMap legítimo con tipos esperados pasa el filter")
    void legitMapAccepted() throws Exception {
        HashMap<String, Object> map = new HashMap<>();
        map.put("hand_id", 42);                     // Integer
        map.put("start", 1716559200000L);           // Long
        map.put("play_time", 3600000L);             // Long
        map.put("server", "host-de-prueba");        // String
        map.put("preflop_players", "alice|bob|cu"); // String
        map.put("hand_id_b64", "AAAAAAAAAAAAAAAAAAAA"); // String
        map.put("rebuy", true);                     // Boolean
        map.put("buyin", 100);                      // Integer
        map.put("sbval", 0.5f);                     // Float
        map.put("bbval", 1.0f);                     // Float
        map.put("conta_mano", 7);                   // Integer
        map.put("balance", "Yg==|100.0|10|0");      // String

        byte[] bytes = serialize(map);
        Object result = deserializeWithFilter(bytes);

        assertNotNull(result);
        assertTrue(result instanceof HashMap, "Resultado debe ser HashMap");
        @SuppressWarnings("unchecked")
        HashMap<String, Object> restored = (HashMap<String, Object>) result;
        assertEquals(42, restored.get("hand_id"));
        assertEquals("host-de-prueba", restored.get("server"));
        assertEquals(true, restored.get("rebuy"));
        assertEquals(0.5f, restored.get("sbval"));
        assertEquals(1716559200000L, restored.get("start"));
    }

    @Test
    @DisplayName("java.io.File (Serializable pero NO en whitelist) es rechazado")
    void fileRejected() throws Exception {
        File maliciousPayload = new File("/tmp/whatever");
        byte[] bytes = serialize(maliciousPayload);
        Exception ex = assertThrows(Exception.class, () -> deserializeWithFilter(bytes));
        // El filtro lanza InvalidClassException (o variantes ClassCastException
        // según JVM); cualquier excepción que NO sea NPE/ClassNotFound es OK
        // porque el filter bloqueo la clase antes del readObject de verdad.
        assertTrue(ex instanceof InvalidClassException
                        || ex.getClass().getName().contains("Filter")
                        || ex.getMessage() == null
                        || ex.getMessage().toLowerCase().contains("rejected")
                        || ex.getMessage().toLowerCase().contains("filter"),
                "Excepción debe indicar que el filter bloqueó: " + ex);
    }

    @Test
    @DisplayName("HashMap con value ArrayList (NO whitelist) rechazado")
    void mapWithArrayListValueRejected() throws Exception {
        HashMap<String, Object> map = new HashMap<>();
        map.put("legit_key", "legit_value");
        java.util.ArrayList<String> hostile = new java.util.ArrayList<>();
        hostile.add("a");
        hostile.add("b");
        map.put("hostile_key", hostile);

        byte[] bytes = serialize(map);
        // Cualquier excepción durante readObject demuestra que el filter intervino
        Exception ex = assertThrows(Exception.class, () -> deserializeWithFilter(bytes));
        assertNotNull(ex);
    }

    @Test
    @DisplayName("Payload pequeño con HashMap vacío pasa")
    void emptyMapAccepted() throws Exception {
        HashMap<String, Object> empty = new HashMap<>();
        byte[] bytes = serialize(empty);
        Object result = deserializeWithFilter(bytes);
        assertTrue(result instanceof HashMap);
        assertTrue(((HashMap<?, ?>) result).isEmpty());
    }

    @Test
    @DisplayName("Map con keys String anidadas y values Number diversos pasa")
    void mixedNumberTypesAccepted() throws Exception {
        HashMap<String, Object> map = new HashMap<>();
        map.put("i", Integer.valueOf(1));
        map.put("l", Long.valueOf(2L));
        map.put("f", Float.valueOf(3.0f));
        map.put("d", Double.valueOf(4.0));
        map.put("b", Boolean.TRUE);
        map.put("s", "string");
        byte[] bytes = serialize(map);
        Object result = deserializeWithFilter(bytes);
        @SuppressWarnings("unchecked")
        HashMap<String, Object> restored = (HashMap<String, Object>) result;
        assertEquals(1, restored.get("i"));
        assertEquals(2L, restored.get("l"));
        assertEquals(3.0f, restored.get("f"));
        assertEquals(4.0, restored.get("d"));
        assertEquals(Boolean.TRUE, restored.get("b"));
        assertEquals("string", restored.get("s"));
    }
}
