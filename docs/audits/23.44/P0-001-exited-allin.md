# P0-001 — all-in desconectado pierde elegibilidad y rompe la capa de botes

## Identificación

- Base auditada: `204481c6c` (CoronaPoker 23.44).
- Subsistemas: `HandPot`, liquidación/showdown en `Crupier`.
- Veredicto: aceptar el fix mínimo; ampliar la auditoría de la familia antes de
  dar 23.45 por cerrada.
- Commits: `fc82a14de fix(game): keep exited all-ins eligible for pots` y
  `30971c327 fix(game): retain exited all-ins across runout streets`.

## Escenario reproducible

Tres jugadores aportan 50, 100 y 100. El primero queda `ALLIN` y abandona la
mesa, por lo que `isActivo()` pasa a `false`. Los dos restantes cubren 100.

Invariante: abandonar la mesa solo afecta a manos futuras; las fichas ya
comprometidas siguen compitiendo en esta mano. El bote principal debe tener
capacidad 50 por jugador (150 total), el side pot debe contener el exceso de los
dos jugadores que cubrieron (100), y la suma debe conservar 250.

## Evidencia TDD

Se añadió `HandPotCharacterizationTest.exitedAllInStillCapsAndCompetesForItsPot`.

Rojo contra 23.44, antes del parche:

```text
mvn -o -f tools/qa/pom.xml -Dtest=HandPotCharacterizationTest test
Tests run: 15, Failures: 1
... expected: <1> but was: <0>
```

La causa es que `HandPot` trataba un `ALLIN` inactivo como dinero muerto:
`compite()` exigía simultáneamente `decision != FOLD` e `isActivo()`.

Fix mínimo:

- `HandPot.competesForPot` conserva la competición de `ALLIN` aunque esté
  inactivo y se reutiliza en total, corte de la capa y side pots.
- La lista `resisten` de showdown solo elimina jugadores salidos que no están
  `ALLIN`, mediante `Crupier.shouldRemoveExitedPlayerFromShowdown`.

La revisión de la familia encontró una segunda pérdida del mismo estado: al
entrar en cada calle, `rondaApuestas` eliminaba de `resisten` a todo jugador
inactivo. Un `ALLIN` que se desconecta antes del flop podía desaparecer antes
del showdown aunque el bote ya estuviera bien formado. El segundo commit
centraliza esa decisión en `shouldRemoveInactivePlayerFromBettingRound` y
conserva el `ALLIN`, manteniendo fuera a un jugador inactivo que hizo `FOLD`.

Se añadió la regresión
`HandPotCharacterizationTest.exitedAllInSurvivesNextStreetParticipantFilter`.
Contra el artefacto original 23.44 el rojo se manifestó como fallo de
compilación (los dos helpers de la decisión aún no existían); con el jar
recompilado, el caso y los vecinos pasaron en verde.

Verde dirigido con el artefacto recompilado de la rama:

```text
mvn -o -f tools/qa/pom-audit.xml \
  -Dtest=HandPotCharacterizationTest test
BUILD SUCCESS
```

Verde de vecinos:

```text
mvn -o -f tools/qa/pom-audit.xml \
  -Dtest=HandPotCharacterizationTest,PotMathTest,RunItTwiceGateTest,\
RunItTwiceSideBAbortTest,RunItTwiceSplitTest,RabbitShowdownHandSelectionTest,\
IwtsthRabbitPauseGateTest test
BUILD SUCCESS
```

Cada regresión y su cambio de producción están en su commit funcional pequeño
correspondiente. El POM auxiliar solo se usó para probar el jar recompilado sin
sobrescribir el repositorio Maven protegido; no se incluye en la rama.

La ampliación de familia `multipleExitedAllInsRemainEligibleAcrossEveryPotLayer`
pincha dos all-ins desconectados (2 y 5) contra dos jugadores que cubren 10:
espera dos side pots y conservación de 27 fichas. El rojo TDD contra la lógica
23.44 fue 17 tests con dos fallos (`expected side-pot count 2, got 0` y el caso
original de 50/100); al restaurar el predicado de 23.45, los 17 tests pasaron.
No se añadió otro cambio de producción: el fix existente cubre ya toda la
familia de capas.

## Auditoría de familia pendiente

Antes de cerrar P0-001 hay que revisar con escenarios TDD o smoke:

- all-in desconectado en cada calle y con más de un side pot;
- jugador `ALLIN` que se reconecta, timeout o cierra la GUI;
- empate y reparto de resto en el bote principal y side pots;
- RIT/Rabbit/IWTSTH después de la salida;
- recuperación SQLite y retransmisión de la liquidación;
- jugador salido que hizo `FOLD` (debe seguir excluido);
- bots y host/cliente, sin usar la lane estadística como única evidencia.

No se acepta una ampliación de producción si no aparece primero una regresión
reproducible y una prueba adecuada.
