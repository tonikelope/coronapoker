# Evidencia de suites de testing de 23.45

Fecha de esta pasada: 2026-08-14. Artefacto probado: `target/CoronaPoker-23.45.jar`
de `release/23.45-game-fixes`. La suite de calidad de bots se considera una
lane estadística lenta y separada de los tests deterministas del juego.

## Corrección de selección de lanes

Los perfiles `slow-bot`, `slow-crypto` y `slow-integration` heredaban
`excludedGroups=slow` desde la configuración por defecto. El resultado era un
`BUILD SUCCESS` con `Tests run: 0`, aunque el perfil pareciera correcto. Se
añadieron propiedades explícitas (`qa.groups=slow` y
`qa.excludedGroups=`) en cada perfil. La prueba mínima de `slow-bot` ejecutó
`BaselineVsRockTest` (1 test, verde) antes de lanzar las baterías completas.

## Resultados

### Fast y pruebas dirigidas

- Fast sobre el JAR 23.45: 734 tests en 128 clases; 725 pasan.
- Los 9 restantes son exclusivamente el bloqueo de identidad/ACL de Windows
  del runner (`IdentityKeypairAclSmoke`: 3 assertions; `ReceiptSignatureTest`:
  6 errores). No hay fallos funcionales de dominio, protocolo, crypto o smoke
  en los 725 que sí se pudieron ejecutar.
- Pruebas dirigidas de pot/rebuy y vecinos: 48/48 verdes.

### `slow-bot` (calidad estadística de bots)

Con el volumen de validación (`200` sesiones × `50` manos), 13 clases
terminaron antes del límite operativo: 25 tests, 0 fallos y 0 errores. Incluye
los matchups baseline, `BotBenchmark`, `HeadsUpSimulator`, `BluffBalance`,
`MemoizedHandPotential`, los matchups heads-up HARD/EASY/MEDIUM y las tres
mesas baseline 6-max, además de `GameFlowSmoke`.

Las cuatro clases restantes se ejecutaron en el modo de iteración documentado
(`40` × `25`, 1.000 manos por matchup):

- `MixedMatchup_MediumVsEasyTest`: verde (`t=2,40`).
- `Multiway_HardVs5EasyTest`: verde (`t=4,36`).
- `Multiway_HardVs5MediumTest`: media positiva, pero rojo estadístico con
  `t=0,54` (el contrato exige `t>2`); no es una excepción ni un fallo de
  conservación del juego.
- `Multiway_MediumVs5EasyTest`: media positiva, pero rojo estadístico con
  `t=1,63`; misma limitación de potencia muestral.

El intento de volumen completo de `Multiway_HardVs5MediumTest` alcanzó 60% de
las 10.000 manos a los 600 s sin aserción ni excepción; la máquina sandbox lo
terminó por timeout. Por ello no se presenta la lane bot como validación
estadística completa: los dos rojos de iteración requieren repetición con el
volumen completo bajo NetBeans/usuario real antes de atribuirlos a un cambio
del bot. No se modificó código de producción de bots por esta señal.

### `slow-crypto`

88 tests, 0 fallos, 0 errores. Incluye pruebas diferenciales, de concurrencia,
rendimiento, locks, Ristretto/SRA, rotaciones y shuffle.

### `slow-integration`

`SocketStallIntegrationTest`: 3 tests, 0 fallos y 0 errores.

## Límites del entorno y siguiente verificación

- El reactor `tools/reactor` sigue sin compilar QA en este Windows sandbox
  porque el compilador no recibe clases como `Helpers` o `Crupier`; se conserva
  como incidencia de classpath/permisos, no de juego.
- `mvn install` de la raíz tampoco puede reemplazar el artefacto local por el
  ACL protegido. La ejecución usada aquí apuntó al JAR de `target` mediante un
  POM auxiliar temporal, eliminado al terminar.
- Para cerrar la evidencia de bots, ejecutar bajo NetBeans con la cuenta
  propietaria y sin límite de 600 s:

  ```text
  mvn -f tools/qa/pom.xml -P slow-bot test -Dqa.sessions=200 -Dqa.hands=50
  ```

  Los perfiles ya no pueden informar falsamente cero tests. Hasta esa pasada,
  `slow-bot` queda correctamente separado y las pruebas automáticas
  funcionales de 23.45 siguen verdes; los dos rojos indicados son una
  advertencia estadística explícita, no un defecto confirmado.
