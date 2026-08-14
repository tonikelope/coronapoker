# Auditoría adversaria automatizada de 23.45

Fecha de ejecución: 2026-08-14
Base auditada: `204481c6c` (23.44)
Rama de integración: `release/23.45-game-fixes`

Este documento registra una auditoría hostil sin pruebas manuales. No sustituye
al TDD de cada arreglo: los tests de regresión siguen viviendo junto al código y
cada mutación negativa se ejecutó contra ellos. La finalidad aquí es intentar
romper las invariantes con entradas deterministas, límites numéricos, comandos
malformados y transporte fragmentado.

## 1. Superficie y nuevos tests

### Pots y all-ins

`HandPotAdversarialInvariantTest` usa la semilla fija `0x2345ADDE5L` y genera
2.000 manos. Cada mano tiene entre 2 y 8 jugadores, aportaciones enteras en
céntimos de 0 a 2.000, y una mezcla determinista de `FOLD`, `CHECK`, `BET` y
`ALLIN`, incluyendo all-ins desconectados (`activo=false`). Para cada cadena
se comprueba:

* conservación exacta (con tolerancia `1e-7`) entre lo comprometido y la suma de
  todas las capas;
* caps y totales no negativos;
* todo side-pot tiene al menos un reclamante elegible;
* la cadena y `getSide_pot_count()` coinciden y no hay más capas que jugadores.

La prueba está en
`tools/qa/src/test/java/com/tonikelope/coronapoker/HandPotAdversarialInvariantTest.java`.
Las caracterizaciones dirigidas de `HandPotCharacterizationTest` siguen cubriendo
los casos de negocio trazados, incluidos dos all-ins salidos en capas distintas.

### Rebuys y comandos hostiles

`RebuyAmountValidationTest` conserva 6 casos dirigidos y añade 3 pruebas:

* matriz de `Integer.MIN_VALUE`, negativos, cero, límites, `Integer.MAX_VALUE`
  y headrooms inválidos; el resultado siempre queda en `0..headroom`;
* textos nulos, vacíos, con espacios/tabulador, negativos, overflow y no
  numéricos nunca se convierten en crédito positivo ni se retransmiten;
* la máquina de estado aceptar -> repetir -> toggle/denegación es idempotente y
  no deja entradas de importe cero.

El importe aceptado sigue siendo el canónico del host y la denegación limpia la
entrada optimista local. No se ha añadido una aceptación implícita de nicks que
no existan en la tabla: ese flujo completo requiere estado de cliente/host y se
mantiene como no cubierto (ver §4), sin introducir un cambio especulativo.

### Transporte y canal autenticado

La batería dirigida ejecutada contra el JAR 23.45 cubre 58 tests verdes:

| Área | Tests |
|---|---:|
| `HandPotCharacterizationTest` | 17 |
| `HandPotAdversarialInvariantTest` | 1 (2.000 casos) |
| `RebuyAmountValidationTest` | 9 |
| `WireFrameTest` | 18 |
| `SocketFramingIntegrationTest` | 13 |

Los dos últimos usan sockets localhost reales y entradas de un byte por lectura,
frames binarios/texto mezclados, corrupción HMAC, plaintext inyectado,
keepalives, reconexión, truncamiento, límites DoS y orden concurrente. No son una
prueba manual de Swing, pero sí el sustituto determinista de la capa de transporte.

## 2. Resultado de las lanes

Las ejecuciones se hicieron con un POM transitorio de auditoría que apuntaba al
JAR local `target/CoronaPoker-23.45.jar`; se eliminó al terminar. El POM mantenido
en `tools/qa/pom.xml` sigue siendo la definición de las lanes.

| Lane | Resultado | Interpretación |
|---|---:|---|
| Fast limpia (`clean test`, slow excluida) | 640 tests / 88 clases; 631 pass | 3 fallos + 6 errores únicamente en ACL/identidad Windows (`IdentityKeypairAclSmoke`, `ReceiptSignatureTest`); cero fallos funcionales |
| Dirigida adversaria | 58/58 | pots, rebuys y framing verdes |
| `slow-crypto` | 88/88 | sin fallos |
| `slow-integration` | 3/3 | sin fallos en `SocketStallIntegrationTest` |

Las pruebas estadísticas de calidad de bots permanecen separadas en
`slow-bot`; no se mezclan con la decisión de bugs deterministas de esta auditoría.
La evidencia de volumen y los casos estadísticos incompletos están registrados en
`TEST-LANES-23.45.md` y no se convierten en un falso bug funcional.

## 3. Mutaciones controladas (prueba de que el TDD rompe)

Las mutaciones se aplicaron sólo en el árbol de trabajo, se empaquetó el JAR, se
ejecutaron los tests y se restauró el código sano antes de continuar.

1. `HandPot.compite`: se restauró temporalmente la regla 23.44 que exigía
   `isActivo()` y descartaba all-ins desconectados. `HandPotCharacterizationTest`
   ejecutó 17 tests y falló en 2: `exitedAllInStillCapsAndCompetesForItsPot` y
   `multipleExitedAllInsRemainEligibleAcrossEveryPotLayer`.
2. `canonicalImmediateRebuyAmount`: se eliminó temporalmente `Math.min` para
   devolver el importe solicitado sin cap. `RebuyAmountValidationTest` ejecutó 9
   tests y falló en 3: `immediateRebuyRelayUsesTheHostCanonicalAmount`,
   `immediateCanonicalizerIsBoundedForEveryIntegerBoundary` y
   `optimisticRebuyStateIsIdempotentAcrossAcceptToggleAndDenial`.

Esto demuestra que los tests no son sólo documentación: detectan precisamente
las dos clases de regresión que motivaron los arreglos de 23.45.

## 4. Límites deliberados y no-cambios

No se realizaron pruebas manuales ni se usó una ventana Swing para dos clientes.
Por tanto no se afirma cobertura de estos cruces de extremo a extremo:

* dos instancias Swing con la secuencia completa de `REBUYNOW`/`REBUYDENIED`,
  incluyendo la confirmación de cada cola y la pintura de menús;
* recuperación SQLite completa entre dos procesos y reanudación de una partida;
* identidad ACL del propietario real de Windows (los 9 resultados de esa familia
  son limitación del entorno, no evidencia de un bug de juego).

Se ejecutaron sustitutos automatizados de menor nivel (normalizadores puros,
estado de mapa, framing autenticado y sockets/stall), pero no se extrapola su
resultado a la UI ni a dos procesos. No se cambia código en esos puntos sin un
test determinista que falle primero: con la filosofía de pies de plomo el riesgo
de una regresión pesa más que una mejora especulativa.

## 5. Veredicto operativo

Los únicos cambios de producción de esta pasada siguen siendo los ya auditados:

* all-ins desconectados permanecen elegibles en todas sus capas;
* el host canoniza y retransmite el importe inmediato de rebuy, y la denegación
  limpia el estado optimista;
* los tests de límites adversarios y de transporte quedan en la rama para evitar
  regresiones.

No apareció un bug funcional nuevo en las entradas adversarias ejecutables. La
23.45 puede continuar a revisión de release sólo con la salvedad explícita de
los tres límites de §4; no se deben convertir en “verdes” por ejecutar el juego
manualmente, porque esta auditoría no los usa como criterio.
