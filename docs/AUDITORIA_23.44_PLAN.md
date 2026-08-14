# Plan de auditoría integral — base 23.44

Base auditada: `204481c6c` (`CoronaPoker 23.44`).

## Objetivo

Buscar bugs reales en 23.44 a partir de su propio código, sus invariantes y sus flujos de producción. Cada hallazgo debe reproducirse, tener una causa demostrable, una regresión TDD y una corrección mínima. No se aceptan cambios preventivos, refactors ni cambios de tests sin bug demostrado.

## Regla no negociable: TDD por commit

Todo commit de una rama de release debe contener el ciclo TDD completo para un
único bug: prueba de regresión que falla contra la base sin el fix, corrección
mínima y la misma prueba en verde. La prueba viaja en el mismo commit que el
código; no se aceptan commits de producción sin prueba, commits de tests
separados ni agrupaciones de bugs sin una causa común demostrada.

La inexistencia de una prueba adecuada nunca justifica omitir TDD: si el
hallazgo descubre un hueco de cobertura, crear primero la prueba mínima que
reproduzca la regla rota. Si el flujo completo depende de GUI, sockets, SQLite
o temporización, combinar una prueba de dominio nueva para la invariante con el
smoke o integración mínimo que pruebe la conexión real; ambos resultados se
registran en el mismo hallazgo.

Antes de crear el commit, registrar: el comando que demuestra rojo, el comando
que demuestra verde y la prueba de regresión vecina ejecutada. Si un flujo no
es automatizable, se debe primero extraer una costura de dominio pequeña y
probable, probarla en rojo/verde y documentar además el smoke manual. Un smoke
manual nunca sustituye la prueba TDD.

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

- **Aceptar:** bug reproducible, causa comprobada, parche mínimo, regresión roja/verde en el mismo commit y verificación dirigida.
- **Mejorar:** la idea es válida pero falla un camino de rollback, concurrencia o compatibilidad; se rehace, no se porta el commit.
- **Descartar:** no cambia producción, duplica 23.44, no reproduce el bug o amplía alcance sin prueba.

Cada lote cerrará con un informe: escenario, severidad, archivos, invariante,
comando rojo, comando verde, prueba vecina y decisión. La release solo avanza
si no hay P0/P1 abierto y la batería correspondiente está verde.

## Regla para cambios previamente aceptados

Un fix que parezca correcto se somete igualmente a revisión de alcance: se
sigue su dato desde la entrada hasta la persistencia, la red y la recuperación;
se enumeran los estados vecinos y se prueban sus rutas de rechazo. Un hallazgo
adyacente obliga a ampliar el parche y su regresión, o a retirar el cambio si
no puede aislarse con seguridad.

## Fase 5 — Auditoría, ordenación y mejora de la suite de tests (final)

Esta fase se ejecuta después de cerrar la auditoría de código y en commits
separados de cualquier fix de producción. Su objetivo es que la suite sea una
fuente fiable de regresiones para releases posteriores, no inflar el número de
tests.

### Inventario obligatorio

1. Inventariar `tools/qa/src/test/java` por paquete, clase, etiqueta, tiempo de
   ejecución y subsistema protegido.
2. Clasificar cada prueba como: unidad de dominio, integración/SQLite, protocolo
   de red, smoke de UI, simulación bot, criptografía o utilidad de harness.
3. Documentar cada prueba lenta con `@Tag("slow")`; toda prueba corta y
   determinista debe permanecer en la lane rápida.
4. Identificar nombres duplicados, tests sin aserción significativa, asserts que
   verifican solo el harness, aleatoriedad sin semilla y dependencias de orden o
   estado estático.

### Revisión de calidad

Para cada prueba o grupo sospechoso, responder y dejarlo registrado:

- ¿Falla si se revierte el comportamiento que dice proteger?
- ¿La aserción mide una regla observable de producción, no una implementación
  accidental o una llamada de mock?
- ¿Puede ejecutarse aislada y en paralelo sin leer/escribir estado global de otra
  prueba?
- ¿Su fixture es mínimo, determinista y limpia recursos, hilos, archivos y base
  SQLite temporal?
- ¿Está en el paquete y lane correctos?

Eliminar o reescribir únicamente los tests que se demuestren vacuos, duplicados
o erróneos. Cualquier reorganización debe conservar cobertura funcional y se
verifica ejecutando la clase movida de forma aislada y la lane que la contiene.

### Ordenación de la suite

1. Mantener tests de reglas y dinero próximos a los objetos de dominio que
   protegen (`HandPot`, reglas de apuesta, botes, showdown).
2. Mantener tests de recovery/SQLite, red/protocolo, UI/smoke, bots y crypto en
   paquetes inequívocos; no usar un paquete genérico como cajón de sastre.
3. Nombrar clases y métodos por el comportamiento/regla: por ejemplo,
   `exitedAllInCompetesForMatchedPot`, no por el método privado ejercitado.
4. Mantener helpers de test fuera de producción y evitar que una prueba fuerce
   visibilidad pública de código que no la necesita.
5. Si se modifica configuración de Surefire, documentar su motivación, el
   impacto en lanes rápida/lenta y una medición antes/después.

### Actualización del README

Tras la auditoría de tests, actualizar la sección **Testing** de `README.md`
para que coincida exactamente con la suite real. Debe incluir:

1. requisitos (JDK, Maven y primera descarga de dependencias);
2. reactor recomendado: `mvn -f tools/reactor/pom.xml test`;
3. comandos de lane rápida, lenta, completa y una sola clase;
4. alternativa del módulo `tools/qa` independiente y el requisito de publicar
   antes el artefacto raíz;
5. tabla “subsystema tocado → lane mínima”; y
6. regla TDD: todo commit de producción contiene su regresión roja/verde en el
   mismo commit.

No conservar cifras de número de tests ni tiempos si no se han medido durante
esta auditoría. Comprobar cada comando copiado desde el README antes de hacer el
commit de documentación.

### Criterio de cierre de la fase 5

- Inventario y clasificación terminados.
- Ningún test vacuo, dependiente de orden o no determinista conocido queda sin
  veredicto.
- Lanes rápida, lenta y completa ejecutadas y registradas en un entorno limpio.
- README verificado comando por comando contra la configuración final.
- Los commits de tests/README son independientes de los commits de producción.

## Handoff para otro modelo

Al terminar una sesión, actualizar el último informe de hallazgo y dejar una
nota corta con: fase/fila actual, comando que se estaba ejecutando, resultado,
archivos modificados y siguiente acción concreta. Antes de continuar, el nuevo
agente debe leer este plan, `git status`, el último informe y el diff pendiente;
no debe repetir una fase marcada como cerrada ni asumir que un test verde prueba
un flujo de red/recovery no ejercitado.
