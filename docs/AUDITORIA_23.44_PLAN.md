# Plan de auditoría integral — base 23.44

Base auditada: `204481c6c` (`CoronaPoker 23.44`).

## Objetivo

Buscar bugs reales en 23.44 a partir de su propio código, sus invariantes y sus flujos de producción. Cada hallazgo debe reproducirse, tener una causa demostrable, una regresión TDD y una corrección mínima. No se aceptan cambios preventivos, refactors ni cambios de tests sin bug demostrado.

## Fase 0 — Preparación y mapa de riesgo

1. Compilar 23.44 en un entorno con Maven y ejecutar la batería rápida y las pruebas dirigidas existentes.
2. Construir un mapa de llamadas y estados para `Crupier`, `HandPot`, `Player`, `GameFrame`, `Participant` y persistencia SQLite.
3. Fijar los invariantes verificables: conservación de fichas, elegibilidad de botes, orden de turnos, cierre único de mano, concordancia host/cliente y recuperación idempotente.

Salida: inventario de invariantes, puntos de entrada de red y matriz de escenarios.

## Fase 1 — Motor de apuestas y liquidación (P0)

Revisar y ejercitar primero:

1. Ciegas, antes, straddle, call, raise mínimo, short all-in, reapertura de acción y heads-up.
2. Botes principal/laterales, folds, desconexión tras all-in, empates, redondeo a céntimos y devolución de sobrantes.
3. Showdown, muck, Rabbit e IWTSTH, comprobando que eventos tardíos no cambian ni cartas, ni ganadores, ni saldos.
4. Run It Twice: voto, timeout, reparto de cada bote, abortos y transición a recovery.

Para cada caso: prueba de dominio sin GUI; cuando haya protocolo, smoke host/cliente separado. Cualquier diferencia de saldo o cierre doble bloquea la release.

## Fase 2 — Recovery y persistencia (P0)

1. Cortar en cada calle y durante showdown, rebuy, Run It Twice y cierre de mano.
2. Verificar que la recuperación conserva contador, asientos, dealer/ciegas, stack, bote, historial y estado de la mano.
3. Inyectar fallos SQLite en creación, actualización de balances y cierre; validar rollback y ausencia de doble pago.
4. Probar esquemas antiguos y preferencias corruptas sin borrar datos válidos.

Salida: matriz de recuperación con “antes/después” de los saldos y estado SQL.

## Fase 3 — Protocolo, identidad y concurrencia (P1)

1. Reordenar, duplicar y truncar comandos de juego: acciones, rebuy, pause, Rabbit, IWTSTH, RIT y reconexión.
2. Contrastar autorización, identidad de peer, límites de cola y transiciones host/cliente.
3. Revisar bloqueos entre EDT, hilos del crupier, sockets, audio y cierre de partida; buscar deadlocks, flags que no se liberan y tareas aceptadas que fallan tarde.

Los cambios de esta fase solo se aceptan con una reproducción determinista y un test o smoke que cubra el flujo entero.

## Fase 4 — Configuración, UI y periféricos (P2)

1. Validar límites y persistencia de ajustes que cambian reglas: buy-in, rebuy, ciegas, tiempo, RIT, Rabbit y IWTSTH.
2. Probar cambio de estado durante diálogos, cierre, pausa, recovery y cambio de dispositivo de audio.
3. Clasificar problemas puramente visuales o de audio por separado; no se mezclarán con fixes de economía/reglas.

## Método de decisión

- **Aceptar:** bug reproducible, causa comprobada, parche mínimo, regresión roja/verde y verificación dirigida.
- **Mejorar:** la idea es válida pero falla un camino de rollback, concurrencia o compatibilidad; se rehace, no se porta el commit.
- **Descartar:** no cambia producción, duplica 23.44, no reproduce el bug o amplía alcance sin prueba.

Cada lote cerrará con un informe: escenario, severidad, archivos, invariante, prueba y decisión. La release solo avanza si no hay P0/P1 abierto y la batería correspondiente está verde.
