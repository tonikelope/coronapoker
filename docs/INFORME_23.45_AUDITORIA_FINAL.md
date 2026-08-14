# Informe final de auditoría y mejora de tests — CoronaPoker 23.45

Fecha: 2026-08-14
Base funcional auditada: 23.44, commit `204481c6c`
Rama de entrega local: `release/23.45-game-fixes`
Último commit de esta auditoría: `e31ae78e4`

## 1. Resumen ejecutivo

La rama conserva únicamente dos familias de cambios de juego rescatadas de la
auditoría experimental:

1. All-in desconectado: las fichas ya comprometidas no pierden elegibilidad
   para el bote ni al formar side-pots, ni al pasar de calle, ni al llegar al
   showdown.
2. Rebuy remoto/inmediato: toda cantidad se valida, limita al headroom y se
   retransmite en forma canónica, evitando excepciones, créditos negativos,
   divergencias entre peers y estados optimistas que sobrevivan a una
   denegación.

La auditoría adversaria automatizada no encontró un tercer bug funcional. No se
introdujeron cambios especulativos en los cruces que no tienen un harness
determinista de dos clientes Swing o de ACL Windows real.

El árbol final está limpio. La suite funcional automatizada no tiene fallos de
juego: la fast limpia obtuvo 631 resultados verdes; los 9 resultados restantes
son exclusivamente la limitación de identidad/ACL de Windows ya aislada.

## 2. Selección de la rama experimental

La comparación se hizo contra `204481c6c` (23.44), documentada en
`docs/AUDITORIA_EXPERIMENTAL_23.45.md`.

### Cambios rescatados

- `fc82a14de`: primer arreglo de pots/all-ins y validación de REBUY remoto.
- `30971c327`: conservación del all-in desconectado al filtrar participantes
  entre calles.
- `fa46e641a`: relay canónico de `REBUYNOW` y limpieza de denegaciones.

### Cambios descartados

Se descartaron propuestas que eran sólo tests, refactors de tests, simuladores,
audio/UI, estadísticas, SQL/recovery, identidad o seguridad sin un caso
determinista de release. No se portó parcialmente ningún cambio sin rojo TDD.
La rama experimental ya no existe en el árbol final; su decisión queda
registrada en el documento de selección.

## 3. Bugs de producción corregidos

### P0 — All-in desconectado tratado como dinero muerto

#### Causa

`HandPot.compite()` exigía simultáneamente que el jugador no hubiese hecho
`FOLD` y que siguiera activo. Un jugador que había puesto todas sus fichas y se
desconectaba pasaba a `activo=false`, por lo que dejaba de competir aunque sus
fichas siguieran dentro del bote.

#### Corrección

- `HandPot.compite()` considera elegible `ALLIN` aunque esté inactivo.
- `genSidePots()` reutiliza el mismo predicado al cortar capas y escoger los
  competidores de cada side-pot.
- `Crupier.shouldRemoveExitedPlayerFromShowdown()` conserva el all-in salido y
  sigue excluyendo a quien salió tras `FOLD` u otra decisión.
- `Crupier.shouldRemoveInactivePlayerFromBettingRound()` conserva el all-in
  durante las calles siguientes, sin reintroducir jugadores retirados.

#### Efecto económico

Para aportaciones 50/100/100, el bote principal queda en 150 y el side-pot en
100. Para dos all-ins desconectados de 2 y 5 frente a dos jugadores que cubren
10, se mantienen las capas 8, 9 y 10 y la conservación total es 27.

Commits de producción: `fc82a14de`, `30971c327`.

### P1 — REBUY remoto no canónico y REBUYNOW divergente

#### Causas

- `Integer.parseInt()` podía propagar excepciones o aceptar estados inválidos
  después de consumir la espera de rebuys.
- El host limitaba a veces el importe aplicado, pero podía retransmitir el
  valor bruto enviado por el cliente.
- `REBUYNOW` era un toggle local; aplicar el eco del host como otro toggle podía
  invertir el estado en vez de reflejarlo.
- Una denegación por límite o falta de headroom podía dejar una entrada
  optimista pendiente en el cliente.

#### Corrección

- `parseRequestedRebuy()` convierte texto inválido en cero sin lanzar.
- `normalizeRequestedRebuy()` y `canonicalRemoteRebuyAmount()` aceptan sólo
  positivos y limitan siempre a `0..headroom`.
- `recibirRebuys()` calcula una sola cantidad segura para animación, contabilidad
  y retransmisión.
- `canonicalImmediateRebuyAmount()` centraliza la política de `REBUYNOW`.
- El host retransmite el importe canónico a todos los peers, incluido el
  originador; el toggle-off viaja como cero.
- `applyRemoteRebuyNow()` aplica un valor recibido como estado absoluto, no como
  toggle local.
- `REBUYDENIED` elimina la entrada optimista mediante
  `clearImmediateRebuyOnDenied()`.
- Una solicitud sin headroom se representa con cero canónico en la partida local,
  sin crear fichas.

Commits de producción: `fc82a14de`, `fa46e641a`.

## 4. Cambios de versión y de infraestructura de QA

- Versión raíz del juego: `23.44` -> `23.45` en `pom.xml`.
- Versión visible en `AboutDialog`: `23.45`.
- Artefacto QA sincronizado con `23.45` en `tools/qa/pom.xml`.
- Los perfiles lentos quedaron separados y seleccionables:
  `slow-bot`, `slow-crypto`, `slow-integration`, además de `slow` y `all`.
- Cada perfil lento activa explícitamente el tag `slow` y limpia la exclusión
  heredada. Un `BUILD SUCCESS` con `Tests run: 0` se documenta como resultado
  inválido de configuración.
- Los patrones Surefire incluyen también `*Smoke`, que antes podían quedar
  fuera de una ejecución normal.
- `README.md` documenta lanes, comandos, fallback de NetBeans, separación de
  bots y la auditoría adversaria automatizada.

Commits relevantes: `b309e5a86`, `8eb6ed7d5`, `a47a415c9`.

## 5. Tests TDD añadidos o ampliados

### Pots y showdown

- `HandPotCharacterizationTest`: 17 tests actuales; casos de all-in salido,
  filtro entre calles, varias capas, dinero muerto, antes y straddle.
- `ShowdownEligibilityTest`: regresión pura del predicado de elegibilidad del
  showdown.
- `HandPotAdversarialInvariantTest`: un test JUnit con semilla fija
  `0x2345ADDE5L` que genera 2.000 manos, 2–8 jugadores, aportaciones en
  céntimos, folds, bets, checks y all-ins desconectados. Comprueba conservación,
  caps/totales no negativos, side-pots reclamables y coherencia de la cadena.

Commits TDD: `d921b1d52`, `e79aa6aa4`.

### Rebuys

`RebuyAmountValidationTest` tiene 9 tests actuales:

- no positivos, malformed y headroom cero/negativo;
- clamp al headroom y relay remoto canónico;
- overflow, `null` y espacios;
- relay inmediato con host como autoridad;
- limpieza de la entrada optimista tras `REBUYDENIED`;
- matriz de límites `Integer.MIN_VALUE`/`MAX_VALUE`, headrooms extremos;
- textos hostiles sin crédito positivo;
- idempotencia aceptar -> repetir -> toggle/denegación.

Commits TDD: `84490d8a8`, `e79aa6aa4`.

### Transporte y seguridad del canal

La batería dirigida conserva y ejecuta:

- `WireFrameTest`: 18 tests de frames binarios/texto, fragmentación de un byte,
  límites DoS, truncamiento, mezcla de frames y escritores concurrentes.
- `SocketFramingIntegrationTest`: 13 tests en sockets localhost reales,
  comandos cifrados, binario de voz, keepalive, corrupción HMAC, plaintext
  inyectado y reconexión.
- `SocketStallIntegrationTest`: 3 tests de la lane lenta de stalls.

## 6. Evidencia TDD y mutaciones

Los cambios se trataron como rojo -> verde -> ampliación de familia:

| Mutación temporal | Resultado rojo | Código restaurado |
|---|---|---|
| Regla 23.44 `isActivo()` para `HandPot.compite()` | 2 fallos en 17 caracterizaciones: all-in salido simple y dos capas salidas | 17/17 |
| Eliminación de `Math.min(requested, headroom)` en rebuy inmediato | 3 fallos en 9 tests: relay, límites e idempotencia | 9/9 |

Las mutaciones sólo se aplicaron al árbol de trabajo, se empaquetó el JAR, se
ejecutó el test y se restauró antes de continuar. No queda ninguna mutación en
producción.

## 7. Resultados de ejecución

Artefacto probado: `target/CoronaPoker-23.45.jar`.

| Lane | Resultado | Lectura |
|---|---:|---|
| Dirigida pots/rebuys/framing | 58/58 | Verde |
| Fast limpia, 88 clases | 640 tests; 631 verdes | Los 9 no verdes son ACL/identidad Windows |
| `IdentityKeypairAclSmoke` | 4 tests; 3 fallos | ACL/propietario del entorno |
| `ReceiptSignatureTest` | 6 errores | identidad/ACL del entorno |
| `slow-crypto` | 88/88 | Verde |
| `slow-integration` | 3/3 | Verde |

La suite estadística `slow-bot` permanece separada: 25 tests terminaron verdes
con volumen 200x50; cuatro clases se observaron a 40x25, dos verdes y dos con
estadístico `t` positivo pero por debajo de 2. La repetición completa de una de
ellas agotó el timeout del sandbox sin aserción ni excepción. No se modificó el
código de bots por esa señal estadística y no se presenta como un fallo de juego.

El build offline del juego con `-DskipTests package` terminó correctamente. El
reactor QA y `mvn install` tienen incidencias de classpath/ACL en este Windows;
para la evidencia se usó un POM temporal contra el JAR de `target`, eliminado
después. No forma parte de la rama.

## 8. Mejoras de la suite y de la documentación de tests

- Separación explícita de tests rápidos, calidad estadística de bots, cripto
  pesada e integración de sockets.
- Inclusión de smoke tests que no entraban en los patrones por defecto.
- Tests de familia en vez de un único ejemplo feliz: capas múltiples, calles,
  fold frente a all-in, overflow, headroom, relay y denegación.
- Generación determinista con semilla documentada para repetir los casos
  adversarios sin azar no reproducible.
- Mutaciones negativas para demostrar que las aserciones realmente protegen los
  arreglos.
- README actualizado con comandos, significado de cada lane, criterio de
  `Tests run: 0` y distinción entre tests de código y bots lentos.
- Informe de plan, índice de auditoría, fichas P0 y residual automatizado
  enlazados desde `docs/audits/23.44/`.

## 9. No-cambios y límites honestos

No se realizaron pruebas manuales. Por ello no se afirma cobertura de:

- dos instancias Swing/cliente ejecutando el flujo completo de
  `REBUYNOW`/`REBUYDENIED` y pintura de menús;
- recuperación SQLite completa entre dos procesos y reanudación de partida;
- identidad ACL del propietario real de Windows;
- carreras de GUI, timing humano o reconexión visual no expresadas por un
  harness determinista.

Sí se ejecutaron sustitutos automáticos de menor nivel (normalizadores puros,
mapa de estado, framing autenticado y sockets/stalls). No se convirtió su éxito
en una afirmación de que cubren Swing o ACL. No se añadió producción sin un test
rojo en esos puntos, porque la regresión sería un precio mayor que una mejora
especulativa.

## 10. Mapa de commits de la entrega

- `b309e5a86` — versión 23.45.
- `fc82a14de` — all-in desconectado y REBUY remoto seguro.
- `30971c327` — all-in salido entre calles.
- `fa46e641a` — relay canónico de REBUYNOW.
- `a47a415c9` — lanes lentas ejecutables.
- `d921b1d52` — familia de pots con dos all-ins salidos.
- `84490d8a8` — malformed/overflow de rebuy.
- `e79aa6aa4` — invariantes adversarias deterministas.
- `e31ae78e4` — este informe y documentación final.

## Veredicto

Los dos bugs de juego rescatados están corregidos con TDD, ampliados por familia
y protegidos por pruebas adversarias. La rama está preparada para continuar la
revisión de release 23.45 con la salvedad explícita de los límites de §9; esos
límites no son fallos funcionales confirmados y no deben ocultarse como tests
verdes.
