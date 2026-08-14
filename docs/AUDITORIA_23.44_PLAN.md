# Plan operativo integral de auditoría — CoronaPoker 23.44

## 1. Mandato y límites

Base única de la auditoría: commit 204481c6c, CoronaPoker 23.44.

La auditoría debe partir del código y del comportamiento de 23.44. No debe usar
la antigua rama experimental como lista de bugs, fuente de parches ni prueba de
que un diagnóstico sea correcto. Los hallazgos deben descubrirse y demostrarse
contra 23.44.

Objetivo final: preparar una rama local de mejora para 23.45 que contenga
únicamente bugs confirmados, sus pruebas TDD y los cambios documentales
expresamente previstos en este plan.

Quedan fuera, salvo que causen un fallo funcional demostrado:

- refactors preventivos;
- optimizaciones sin medida reproducible;
- cambios visuales o de audio puramente cosméticos;
- ampliaciones de alcance no necesarias para corregir el bug;
- cambios de tests que solo aumenten cantidad sin proteger una invariante.

## 2. Estado y aislamiento del trabajo

La rama de entrega es release/23.45-game-fixes. La auditoría de base debe
realizarse sobre un worktree o rama temporal creado exactamente desde
204481c6c, para que los cambios ya presentes en 23.45 no contaminen el
diagnóstico.

Comprobaciones iniciales:

~~~powershell
git status --short --branch
git show --no-patch --format='%H %s' 204481c6c
git diff --quiet 204481c6c master
~~~

Worktree recomendado:

~~~powershell
git worktree add ..\coronapoker-audit-23.44 204481c6c
~~~

Si master ya no coincide con 204481c6c, no se usa como base. No restaurar la
rama experimental eliminada.

## 3. Regla obligatoria: TDD para todo fix

Todo commit funcional debe cerrar un único ciclo TDD:

1. Reproducir el bug contra 23.44.
2. Identificar la invariante incumplida.
3. Escribir o ampliar una prueba que falle por la causa correcta.
4. Ejecutarla y conservar el comando y la salida roja.
5. Implementar el cambio mínimo.
6. Ejecutar la misma prueba en verde.
7. Ejecutar pruebas vecinas y la lane correspondiente.
8. Revisar efectos en red, persistencia, recovery y concurrencia.
9. Incluir prueba y código en el mismo commit.

No se permiten commits funcionales sin regresión, commits de test separados del
fix ni commits que mezclen bugs independientes.

Si no existe una prueba adecuada, hay que crearla. La falta de infraestructura
no permite saltarse TDD. Se elegirá el nivel más bajo que reproduzca el defecto:

- prueba de dominio para reglas y dinero;
- integración SQLite para transacciones y recovery;
- prueba de protocolo para serialización, autorización y orden;
- smoke host/cliente para cruces reales de cola o socket;
- prueba de concurrencia con coordinación determinista para carreras.

Cuando un flujo necesite GUI, red o temporización, la prueba de dominio no
sustituye al smoke de integración. Deben existir ambos cuando cada uno cubra una
parte distinta del fallo. Un smoke manual complementa TDD, nunca lo reemplaza.

Los commits exclusivamente documentales o de ordenación de tests no son fixes de
producción; se mantendrán separados y tendrán sus propias comprobaciones
objetivas descritas en la fase final.

## 4. Registro de evidencia y handoff

Crear un informe por hallazgo bajo docs/audits/23.44/ con esta plantilla:

~~~text
ID y severidad:
Commit base:
Subsistema:
Escenario mínimo:
Resultado esperado:
Resultado observado:
Invariante rota:
Ruta de código y causa:
Prueba nueva o ampliada:
Comando y evidencia roja:
Parche mínimo:
Comando y evidencia verde:
Pruebas vecinas:
Riesgos explorados:
Veredicto: aceptar / ampliar / descartar
Commit final:
~~~

Mantener además un índice docs/audits/23.44/INDEX.md con:

- hallazgos abiertos, ordenados P0 a P3;
- hallazgos cerrados y veredicto;
- fase y fila de matriz en curso;
- último comando ejecutado;
- próxima acción concreta.

Otro modelo debe poder continuar leyendo únicamente este plan, INDEX.md, el
último informe y git status.

## 5. Severidad y criterio de decisión

- P0: dinero incorrecto, ganador incorrecto, reglas de apuesta incorrectas,
  corrupción, doble liquidación, divergencia host/cliente o partida bloqueada.
- P1: recovery/reconexión incorrectos, autorización de comando de juego
  defectuosa, pérdida de estado o fallo repetible que impide completar un flujo.
- P2: configuración funcional incoherente, UI que provoca una acción errónea o
  degradación recuperable.
- P3: visual, texto, audio o rendimiento sin impacto en reglas o continuidad.

Veredictos:

- Aceptar: bug reproducido, causa demostrada, TDD rojo/verde, parche mínimo y
  escenarios vecinos comprobados.
- Ampliar: arregla el síntoma pero queda un camino de rollback, protocolo,
  persistencia o concurrencia sin cerrar.
- Descartar: no reproduce, ya está corregido en 23.44, solo toca tests/refactor o
  requiere un cambio desproporcionado sin evidencia.

No hay cierre de release con P0/P1 abierto.

## 6. Preparación técnica y línea base

El build raíz no ejecuta tests. La suite vive en tools/qa y el reactor opt-in
tools/reactor compila juego y QA conjuntamente.

Requisitos: JDK compatible y Maven 3.x. En el entorno donde se redactó este plan
Maven no estaba disponible, por lo que el siguiente agente debe ejecutar y
registrar la línea base antes de declarar cualquier lote verde.

Comandos recomendados:

~~~powershell
mvn -f tools/reactor/pom.xml test
mvn -f tools/reactor/pom.xml test -P slow
mvn -f tools/reactor/pom.xml test -P all
mvn -f tools/reactor/pom.xml test '-Dtest=PotMathTest' '-Dsurefire.failIfNoSpecifiedTests=false'
~~~

Modo standalone, solo si se necesita:

~~~powershell
mvn -DskipTests install
mvn -f tools/qa/pom.xml test '-Dcoronapoker.version=23.44'
~~~

Antes de auditar:

1. Guardar versiones de Java y Maven.
2. Ejecutar fast, slow y all contra 23.44 sin cambios.
3. Registrar número real de tests, skips, fallos y duración.
4. No corregir fallos de infraestructura como si fueran bugs del juego.
5. Confirmar que una prueba dirigida realmente se ejecuta y no queda filtrada.

## 7. Fase A — Mapa del sistema y estados

Objetivo: saber quién escribe cada estado antes de buscar fallos.

Inventariar:

- Crupier: ciclo de mano, apuestas, showdown, RIT, recovery y cierre.
- HandPot: formación de botes, side pots, dinero muerto y totales.
- Player, LocalPlayer y RemotePlayer: decisión, actividad, salida, stack y bote.
- GameFrame: ciclo de vida, configuración y ownership de UI.
- Participant, WaitingRoomFrame y NetClient: entrada, autenticación y relay.
- Helpers y SQLite: transacciones, migraciones y utilidades monetarias.
- colas, executors, locks, latches, timers y flags de sesión/mano.

Construir dos tablas:

1. Evento → estado previo → escritor → estado posterior → red/persistencia.
2. Lock/cola → productor → consumidor → cierre → ruta de error.

Salida obligatoria: mapa actualizado en el índice y lista de invariantes que
pueden probarse.

## 8. Fase B — Turnos y reglas de apuesta (P0)

Auditar sistemáticamente esta matriz:

| Área | Casos mínimos |
| --- | --- |
| Inicio | 2 jugadores, multiway, stack menor que ciega, jugador warming/spectator |
| Ciegas/ante | ciegas normales, ante, straddle, all-in forzado, posiciones tras salida |
| Call/check | call exacto, call all-in, check permitido/prohibido, autocall |
| Raise | mínimo, raise exacto, short all-in, varios short raises acumulados |
| Reapertura | jugador que ya actuó, heads-up, multiway, acción heredada |
| Fin de calle | todos igualados, uno con stack, todos all-in, desconexión en turno |
| Timeout | check/fold automático, pausa, reconexión dentro/fuera de gracia |

Para cada caso comprobar:

- orden de actuación determinista;
- apuesta actual y mínimo de raise;
- derechos de reapertura por asiento;
- ausencia de acción extra o calle prematura;
- igualdad de estado entre host y clientes.

Pruebas preferidas: BetRulesTest, pruebas de dominio nuevas y smoke de flujo si
la decisión cruza red.

## 9. Fase C — Dinero, botes y liquidación (P0)

Matriz mínima:

| Caso | Comprobaciones |
| --- | --- |
| Bote único | todos igualan; total exacto |
| All-in corto | cap del main pot y side pot correcto |
| Varios all-in | capas ordenadas y conservación total |
| Fold | dinero muerto en la capa correcta; nunca compite |
| Salida/desconexión | diferencia entre fold, exit y all-in ya comprometido |
| Empate | reparto, odd chip y redondeo a céntimos |
| Sobrante | devolución única al jugador correcto |
| Showdown tardío | callbacks/Rabbit/muck no alteran ganadores ni pagos |

Invariantes obligatorias:

~~~text
suma de stacks + dinero en tránsito = capital inicial
suma de botes liquidados = suma de aportaciones - devoluciones
un fold nunca gana
un all-in solo compite hasta el nivel igualado
cada bote se paga exactamente una vez
hand.end y balances representan la misma liquidación
~~~

Usar cantidades con céntimos y empates para recorrer redondeo. Tirar de la manta
desde la entrada de apuesta hasta el registro, la red, SQLite y recovery.

## 10. Fase D — Rebuy, buy-in y límites económicos (P0/P1)

Casos obligatorios:

- importe válido, cero, negativo, texto inválido y overflow;
- importe superior al headroom;
- límite de rebuys alcanzado;
- timeout y elección de espectador;
- comando duplicado, tardío o reordenado;
- desconexión/reconexión durante la decisión;
- host y cliente con valores distintos;
- valor recibido, canónico, retransmitido, aplicado y persistido.

Comprobar que un rechazo no elimina prematuramente al jugador de una espera, no
deja UI/flags bloqueados y no modifica saldo. La prueba pura del normalizador no
sustituye una prueba del flujo completo si el bug puede surgir en pending,
broadcast, rebuy_now o persistencia.

## 11. Fase E — Showdown, Rabbit, IWTSTH y Run It Twice (P0/P1)

Showdown:

- último agresor y checked-down;
- auto-show, muck, IWTSTH y espectador;
- cartas reveladas antes/después de Rabbit;
- jugador salido tras all-in;
- callback de mano anterior.

Rabbit e IWTSTH:

- autorización por participante y estado de calle;
- aceptación, denegación y timeout;
- petición duplicada, tardía y de mano anterior;
- liberación de barra, locks y flags en todos los peers;
- ningún cambio de saldo después del cierre.

Run It Twice:

- voto normal/RIT, timeout y unanimidad;
- votos duplicados, contradictorios o de mano anterior;
- aborto antes, durante y después del board B;
- main pot y todos los side pots;
- empate y odd chips por board;
- cierre, SQLite y recovery atómicos.

Toda corrección de autorización debe revisar también la respuesta de rechazo y
el rollback del solicitante; validar solo el predicado no basta.

## 12. Fase F — SQLite, cierre y recovery (P0)

Puntos de corte:

| Corte | Debe conservar | Debe impedir |
| --- | --- | --- |
| Antes de mano | asientos, dealer, ciegas, contador | mano duplicada |
| Durante calle | turno, apuestas, acciones | repetir acción |
| Tras all-in | capas y elegibilidad | bote perdido |
| Showdown | ganadores y ledger | doble pago |
| RIT board A/B | boards, votos y snapshot | cierre parcial |
| Rebuy | decisión y saldo canónico | rebuy doble |
| Fallo SQL | estado anterior o rollback | hand.end sin balances |

Procedimiento:

1. Preparar stacks y acciones conocidos.
2. Capturar filas SQL antes del corte.
3. Interrumpir exactamente en el punto elegido.
4. Recuperar y completar la mano.
5. Comparar mesa, registro, contador, balances y hand.end.
6. Repetir recovery sobre el mismo estado para probar idempotencia.
7. Inyectar fallos en creación de mano, balances y cierre usando una base
   temporal, nunca datos reales del usuario.
8. Probar esquema antiguo y preferencias corruptas.

## 13. Fase G — Red, identidad y protocolo (P1)

Para cada comando sensible —acción, rebuy, pause, Rabbit, IWTSTH, RIT,
reconexión y cierre— validar:

- emisor autenticado;
- tamaño y campos;
- hand/session id;
- estado de juego compatible;
- idempotencia;
- orden;
- rechazo sin mutación;
- respuesta/ack y rollback;
- límites de cola y memoria.

Casos adversarios: truncado, duplicado, reordenado, retrasado, payload extremo,
nick incorrecto, conexión reemplazada y llegada tras cerrar la mano.

Una prueba de parser no prueba el flujo de socket. Añadir smoke host/cliente
cuando la vulnerabilidad dependa de relay, cola o lifecycle.

## 14. Fase H — Concurrencia, EDT y ciclo de vida (P1)

Para cada lock, flag, timer, future o executor, buscar:

- retorno temprano sin finally;
- tarea rechazada antes de arrancar;
- tarea aceptada que falla o se cancela tarde;
- callback de sesión/mano anterior;
- orden inverso de locks;
- espera no despertada al cerrar;
- flag/latch que no se libera;
- acceso Swing fuera del EDT;
- teardown concurrente con recovery/reconexión.

No sustituir un deadlock por un timeout silencioso. La corrección debe dejar un
estado observable y recuperable. Las carreras deben probarse con barreras o
latches deterministas, no con sleeps frágiles.

## 15. Fase I — Configuración y UI funcional (P2)

Revisar únicamente configuración con impacto funcional:

- buy-in, rebuy, ciegas, ante, straddle;
- tiempo de decisión;
- Rabbit, IWTSTH y RIT;
- valores nulos, corruptos o fuera de rango;
- cambios durante pausa, diálogo, cierre o recovery;
- coherencia valor visible/enviado/aplicado/persistido.

Separar defectos cosméticos y de audio, salvo que bloqueen una decisión o el
ciclo de partida.

## 16. Revisión ampliada de cada fix

Antes de aceptar cualquier commit:

1. Seguir el dato desde entrada hasta salida.
2. Revisar estados vecinos y límites.
3. Probar rechazo, timeout, duplicado y cierre.
4. Revisar host, cliente y recovery.
5. Revisar persistencia y redondeo si hay dinero.
6. Revertir mentalmente o mediante prueba el comportamiento para confirmar que
   la regresión detecta el bug real.
7. Ampliar o descartar si el parche solo arregla el síntoma.

## 17. Fase J — Auditoría final de tests y README

Esta fase ocurre después de cerrar los bugs de producción y usa commits
separados.

### Inventario

Inventariar tools/qa/src/test/java por:

- paquete y subsistema;
- clase y comportamiento protegido;
- tipo: unidad, SQLite, protocolo, smoke, bot, crypto o harness;
- fast/slow;
- duración;
- estado compartido y recursos;
- determinismo;
- prueba duplicada o vacua.

### Calidad

Para cada prueba sospechosa confirmar:

- falla al revertir el comportamiento protegido;
- aserta una regla observable de producción;
- se ejecuta aislada;
- no depende del orden;
- usa semilla cuando hay aleatoriedad;
- limpia hilos, archivos, sockets y SQLite temporal;
- está en la lane y paquete correctos.

Eliminar, fusionar o reescribir únicamente con evidencia. Para una mejora de
test, demostrar primero el defecto del test —por ejemplo, mutación no detectada,
test no descubierto o dependencia de orden— y después demostrar que la mejora
lo detecta. Los movimientos deben conservar cobertura y ejecutar clase aislada
más lane afectada.

### Ordenación

- reglas/dinero junto a sus objetos de dominio;
- recovery/SQLite en grupo explícito;
- red/protocolo separado;
- UI/smoke separado;
- bots y crypto en slow cuando corresponda;
- nombres por comportamiento, no por método privado;
- helpers solo en código de test.

### README Testing

Actualizar la sección Testing de README.md al final, usando resultados medidos:

1. requisitos reales de JDK/Maven;
2. reactor recomendado;
3. comandos fast, slow, all y una clase;
4. modo standalone y sincronización de versión;
5. tabla subsistema → lane mínima;
6. regla TDD y creación de tests inexistentes;
7. tiempos y recuentos solo si se midieron;
8. explicación de Smoke discovery y etiqueta slow si sigue siendo relevante.

Ejecutar cada comando documentado antes del commit. No conservar cifras
históricas no verificadas.

## 18. Verificación por commit y por lote

Por commit funcional:

- rojo registrado;
- verde dirigido;
- pruebas vecinas;
- git diff --check;
- revisión del diff contra su padre;
- prueba y fix en el mismo commit.

Por lote:

- fast lane;
- slow si toca bot/crypto/red pesada;
- smoke de subsistema;
- informe actualizado.

Antes de release:

- all lane limpia;
- smokes manuales host/cliente y recovery;
- ningún P0/P1 abierto;
- versión coherente en pom raíz, AboutDialog y QA;
- README verificado;
- worktree limpio.

## 19. Criterio de finalización y continuación

La auditoría termina cuando todas las filas de las matrices tienen resultado,
todos los hallazgos tienen veredicto, todos los fixes aceptados tienen TDD y no
queda P0/P1 abierto.

Al pausar, dejar en INDEX.md:

~~~text
Fase y fila:
Hallazgo:
Estado rojo/verde:
Último comando y resultado:
Archivos modificados:
Bloqueo, si existe:
Siguiente acción exacta:
~~~

El siguiente modelo debe continuar desde esa acción, no reiniciar la auditoría
ni asumir que una prueba verde cubre un flujo no ejecutado.
