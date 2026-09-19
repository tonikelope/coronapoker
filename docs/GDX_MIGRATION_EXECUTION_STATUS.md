# Estado de ejecución de la migración completa GDX

Última actualización: 2026-09-19 19:36 (Europe/Madrid)

Este es el documento vivo para reanudar el trabajo tras cualquier corte. Debe
actualizarse al cerrar cada bloque funcional, al descubrir una carencia nueva o
al cambiar el resultado de una certificación.

Documentos relacionados:

- Plan contractual: `CORONAPOKER_GDX_FULL_MIGRATION_PLAN.md`.
- Auditoría resumida de paridad: `GDX_PARITY_AUDIT.md`.
- Canon visual y de animaciones de la mesa: commit `627c71e4f`, archivo
  `reference/gdx-demo/src/main/java/com/tonikelope/coronapoker/gdxdemo/CoronaPokerGdxDemo.java`.

Estructura canónica actual: código de producto en `src` y `modules`, referencia
visual archivada en `reference/gdx-demo` y únicos ejecutables en `target`.

## Checkpoint de consolidación 2026-09-19

- Unificado el idioma visible de Ajustes entre menú y mesa para Controles,
  Timba, Ciegas, Bots, Sesión y Atajos. Las acciones operativas y los estados
  del editor de teclas ya no dependen de literales españoles; consumen el mismo
  catálogo ES/EN, incluidos valores calculados como dificultad, unidad, límites
  y antialiasing. El aviso transaccional al cancelar con cambios pendientes
  también es común y traducible. Pruebas focalizadas de idioma/atajos:
  **24/24**; contrato de snapshot, restauración y transacción: **36/36**.
  JAR GDX: 266.249.150 bytes, SHA-256
  `1574E96CDD056465CC261AF7D897F5B0E950EEA0EAC1524D781361816D7C6E16`.
- Los resúmenes de Ajustes dentro de una partida ya consumen el idioma activo:
  tiempos de timba, compra/recompra y los cuatro niveles de Rabbit Hunting no
  conservan textos españoles al cambiar a inglés. El mismo formateador se usa
  tanto en menú como en mesa, sin duplicar reglas. Pruebas focalizadas de
  catálogo y resumen: **7/7**. JAR GDX: 266.246.617 bytes, SHA-256
  `C9F1F5598B6213492AD0296CC6B04DFAA8D86EDC32451E0F3D77E99B642C0A6F`.
- Completado el segundo corte de idioma y claridad funcional de Nueva timba:
  perfiles, carga/cancelación de recuperación y todo el editor de estructuras
  de ciegas responden ahora al idioma activo. Los controles de escalado de
  ciegas y Partida ya muestran explícitamente unidad, intervalo, tope, número
  de manos y segundos, eliminando contadores mudos. Prueba focalizada de
  catálogo y formato: **1/1**. JAR GDX: 266.245.826 bytes, SHA-256
  `3AD36B36A665F73B8ACD0894D1B82D5F3CE9AD6B3DD4D0DAFCBB1CE11E933E68`.
- La página Compra/Recompra de Nueva timba ya consume el mismo catálogo ES/EN
  que Ajustes: buy-in fijo, compra inicial, extremos mínimo/máximo del rango,
  recompra, límite por jugador, máximo y tope dejan de estar escritos
  directamente en español. Rabbit hunting usa también las traducciones
  existentes y el segundo extremo del rango deja de mostrarse como una flecha
  sin significado. Prueba focalizada de idioma: **1/1**. JAR GDX:
  266.245.030 bytes, SHA-256
  `9F7A6DE7B9581BC17B8AD9D200B6867D98115A3104A1FDA592127172B40C57C8`.
- Unificado el volumen general entre todas las superficies GDX. En una
  instalación sin `master_volume`, el menú y Swing usaban 80 %, pero la mesa
  GDX usaba 100 % y podía introducir un salto al entrar en partida. Menú, lobby,
  mesa, Cancelar y Restaurar consumen ahora un único lector validado con el
  valor canónico 80 %; valores no numéricos, infinitos o fuera de rango vuelven
  también a ese valor. El contrato focalizado pasa **18/18**. JAR GDX:
  266.245.070 bytes, SHA-256
  `414E700023E70730324411CE5B6360A0FA5619B9D93C4E2D6018B0AC6DCD9FFC`.
- Cerrada una carencia real de paridad en Ajustes/arranque: la intro GDX ya no
  usa el sonido genérico de interruptor, sino el mismo `misc/init.wav` de Swing
  en el instante en que se enciende la intro. Respeta `sonido_arranque`, el
  maestro de efectos, el mute global y el volumen general. La opción aparece en
  el catálogo compartido de Audio tanto en menú como en mesa y se traduce en
  español e inglés. El contrato focalizado pasa **17/17**. JAR GDX:
  266.244.885 bytes, SHA-256
  `9FEABF3BACFA432BDC44618008F1B4C57CEA405EA21F90FCD0C4F8617AA2CA58`.
- Corregido un P0 confirmado en el log GDX real: rotación de posiciones,
  recogida de apuestas, pagos, recompras y cierre de barajado podían conservar
  su evento canónico hasta el final de la animación y ser adelantados por reloj
  o telemetría (`24 after 25`, `283 after 285`, `293 after 294`). Ahora el
  estado ordenado se consume al aceptar el evento y sólo la barrera visual
  espera el aterrizaje o fin real. La presentación conserva su snapshot previo
  y reconstruye stack/bote durante el vuelo, incluidos cobros que esperan una
  apuesta todavía en el aire. Regresión directa del renderer y estado:
  **101/101**, sin fallos ni errores. JAR GDX: 266.244.821 bytes, SHA-256
  `35C94A9D90E497E06C655E55082F4E653D0509DB8F5DAD673B0C4D29910A6389`.
  Una integración de red real host/cliente recorrió además preflop, flop, turn,
  river, showdown, consenso y pago con saldo final concordante (**1/1**).
  Sigue pendiente la QA OpenGL del flujo completo.
- Corregida después del checkpoint la presentación del ganador sin showdown:
  el `Payout` canónico pinta ahora el marco completo de ganador (también en el
  HUD local), evita la atenuación incorrecta y reemplaza la acción previa por
  `GANA/GANAS`, sin fabricar un `HandResult` ni destapar cartas. La familia
  focalizada de estado/render pasa **94/94**. JAR GDX: 266.244.411 bytes,
  SHA-256
  `61CCE19E88E81B5BDCA7974E4ED71B161253BF0BC101EE0D1B60085FF10779AB`.
- No se abre funcionalidad nueva en este corte. El objetivo es asegurar el
  trabajo existente, fijar un artefacto reproducible y volver a una ejecución
  por bloques cerrados.
- El bloque focalizado actual pasa **150/150** pruebas: 145 de estado de mesa,
  chat, Ajustes, Nueva Timba, perfiles, texto y depuración GDX, más 5 fronteras
  arquitectónicas. No se detectaron fallos ni errores.
- Se reparó la caché local de plugins Maven antes de empaquetar; sus dos fallos
  previos (`MavenFilteringException` y `plexus-archiver/FileSet`) ocurrieron
  antes de ejecutar código del proyecto y no eran regresiones de CoronaPoker.
- JAR GDX de este checkout: `target/CoronaPoker-24.11-gdx.jar`, 266.244.212
  bytes, SHA-256
  `DA9782FFB28A6709AEC8244244991F1AFBFC1493E4B9B11C6B7AF2C756461527`.
- Este artefacto ya incluye el ganador por `Payout` cuando todos los rivales se
  retiran, el arranque visual del ganador al aterrizar el pago y la transición
  visible de cierre desde Fin de timba. Sigue pendiente la comprobación manual
  OpenGL del caso exacto comunicado por el usuario.
- La última certificación amplia conservada sigue siendo 48/48 escenarios GDX,
  30/30 escenarios mixtos y 42/42 fases FAST. No se eleva ese corte con las
  correcciones posteriores hasta cerrar el siguiente hito funcional.
- Estrategia desde este checkpoint: primero una beta GDX jugable de extremo a
  extremo; después chat/medios y pantalla final; finalmente pulido visual,
  rendimiento, estadísticas y certificación BALANCED. Las suites amplias se
  ejecutan sólo al cerrar un bloque, no tras cada retoque.

## Reglas innegociables

- La demo es el canon visual y de animaciones de la mesa.
- Ningún retardo falso: las barreras deben terminar cuando termina la operación
  o animación real.
- GDX puede enriquecer una animación (vuelos de fichas, trayectorias, partículas
  o transiciones), pero sólo como presentación de un evento real: la animación
  completa la barrera causal y nunca adelanta, retrasa artificialmente ni decide
  el flujo de juego.
- Swing y GDX son dos frontends del mismo core; GDX no reimplementa reglas.
- Los escenarios Swing no se modifican para hacer pasar GDX.
- No se declaran completas funciones visibles sin consumidor real y prueba o QA
  expresamente indicada.
- El código de la demo archivada es exclusivamente referencia visual. El
  producto no puede contener jugadores, manos, acciones, apuestas, turnos ni
  secuencias de póquer prefabricadas; toda mesa real nace de un snapshot del
  core y avanza exclusivamente mediante sus eventos ordenados.
- No se incrustan mods externos en el JAR oficial. Goliat es la baraja oficial.
- Se preserva el árbol de trabajo existente y no se publica ni limpia sin orden.
- Las pruebas visuales se ejecutan normalmente en el monitor 2, sin sonido y sin
  robar foco cuando sea posible.

## Posición actual en el plan

- Fase 8: renderer de mesa integrado, pero sin certificación visual completa
  contra la demo.
- Fase 9: en ejecución. Existe juego core real con bots y una base de red humana,
  pero todavía falta cerrar funciones obligatorias de mesa, ajustes y red.
- Fases 10 y 11: pendientes de certificación, compatibilidad, rendimiento y
  entrega.

No debe presentarse todavía como migración terminada ni como sustituto completo
de Swing.

## Corte de certificación actual

- Corte de trabajo empaquetado a las 23:43: el chat de sala admite scroll con
  rueda y conserva el anclaje cuando llegan mensajes; el avatar personalizado
  se resuelve también en la mesa; el proceso GDX crea un registro persistente
  en `.coronapoker/Debug`; y las notificaciones durante la partida han salido
  de la sala de espera y viven en la página Chat de Ajustes. Nueva Timba ya no
  dibuja el texto residual `LISTO PARA CONTINUAR`, agrupa ESC/F11 en el pie y
  alinea sus steppers principales. Los avisos de texto usan el mínimo de tres
  segundos de Swing y los de voz se retiran al terminar la reproducción. El
  bloque enfocado pasa 135 pruebas.
- Regresión P0 de victoria sin showdown: cada `Payout` canónico marca al
  receptor como ganador aunque la mano termine por folds y no exista
  `HandResult` ni destape. Hay una regresión explícita que exige ganador visual
  sin inventar showdown. Queda QA OpenGL del caso informado por el usuario.
- JAR que contiene este corte: `target/CoronaPoker-24.11-gdx.jar`
  (266.243.353 bytes, SHA-256
  `66630A0D61488E3EAA3DA1C49C76DE322CEF10A604489CCE89A7FE985262E17D`).
- Siguiente bloqueo concreto: hacer inequívoca y no aparentemente congelada la
  transición de Fin de timba al menú sin adelantar el cierre autoritativo ni
  mover barreras de red; después continuar la auditoría funcional de Ajustes,
  Nueva Timba y sala de espera.

- Corte consolidado verde del checkout actual, sin reusar artefactos de una
  compilación anterior:
  - escenarios GDX estrictos: **48/48**, evidencia en
    `target/gdx-scenarios/20260913-145304-fast`;
  - matriz humana mixta Swing/GDX en ambos sentidos: **30/30**, evidencia en
    `target/gdx-mixed-scenarios/20260913-151954-fast`;
  - certificación FAST completa de core/Swing: **42/42 fases**, semilla
    `388439920`, evidencia en
    `target/certification/20260913-153646`.
- El último fallo real estaba en la salida voluntaria de un cliente all-in: la
  prueba criptográfica de showdown ya se había recibido, pero la espera visual
  posterior trataba el asiento salido como una caída no autenticada. El crupier
  reutiliza ahora esa prueba retenida antes de abortar, conserva la barrera y
  completa el settlement normal. No se rebajó ninguna aserción del escenario.
- Tras ese corte se ha retomado P1.1. Ajustes de menú, sala y mesa comparten ya
  sesión transaccional, contrato de secciones, geometría, chrome y pie. La
  restauración de Atajos usa ahora el mismo pie canónico que Apariencia y Audio,
  y los selectores de baraja, trasera y tapete del menú permiten recorrer las
  opciones en ambos sentidos. Se han añadido traducciones vivas para secciones,
  subpáginas, opciones y valores, y se han ensanchado las regiones de valor de
  los steppers para que el texto no invada sus botones. El bloque dirigido
  posterior pasa **38/38**.
- El bloque actual de Ajustes incorpora además gestión nativa y transaccional
  de estructuras de ciegas (crear, duplicar, renombrar, borrar y editar
  niveles), incluida su restauración al cancelar desde la sala. Las reglas
  globales de comunicación ya quedan deshabilitadas para clientes tanto en la
  mesa como en la sala, mientras micrófono, reproducción propia y bloqueos
  locales siguen siendo editables. Restaurar Apariencia durante la partida
  repone también modo de pantalla y MSAA, y aplica el modo de ventana en vivo.
  El bloque focalizado de contrato, sesión, geometría, perfiles y estructuras
  pasa completo. El JAR actualizado que contiene este bloque está disponible en
  `target/CoronaPoker-24.11-gdx.jar` (266.237.494 bytes, SHA-256
  `713F58CAAA76C3C998B7F1122B3BE2141424A8B4E82657367FA1D0B6929553B3`).
  La fábrica de mesa conserva por referencia las propiedades transaccionales y
  lee TTS/notas de voz al crear la sesión; el crupier host difunde ambas reglas
  antes de arrancar. Por tanto, los cambios del anfitrión en sala sí alcanzan
  la partida y sus clientes, mientras el cliente no puede sobrescribirlos.
- Una ejecución completa posterior del reactor detectó una intermitencia real
  (319/320): una identidad humana quebrada que salía durante la mano abierta
  podía reincorporarse a la recuperación antes de que su proof de barajado
  verificado llegase al fósil local. El cierre ya distingue verificación en
  memoria de persistencia durable, espera el mazo exacto y aplica el mismo drenaje
  a EXIT voluntario. El escenario completo de cuatro humanos, bot, salida,
  recuperación, recompra y continuación hasta la mano 7 pasa ahora **3/3**
  ejecuciones consecutivas. El clúster de salida controlada, force-recover,
  reincorporación tras caída y salida all-in pasa además **4/4**. Falta repetir
  el reactor completo antes de elevar de nuevo el corte consolidado.

- El contrato estricto conserva los 37 nombres de escenario Swing y los cubre
  mediante 48 métodos GDX ejecutables de lógica, red y proyección de eventos.
  `tools/qa/gdx-scenarios.cmd` ejecuta cada método en un proceso Maven/JVM nuevo
  para impedir contaminación por propiedades estáticas, sockets, crupieres o
  ejecutores de pruebas anteriores. Este 37/37 no equivale todavía a 37 pruebas
  de ventana OpenGL ni certifica por sí solo la interacción visual completa.
- Los 41 métodos que componían el catálogo anterior pasaron una ejecución FAST
  aislada completa. La campaña se reanudó después de cada corrección para no
  repetir horas ya certificadas; la evidencia está en
  `target/gdx-scenarios/20260913-045009-fast`,
  `target/gdx-scenarios/20260913-050608-fast` y
  `target/gdx-scenarios/20260913-050815-fast`. El método 42, añadido para
  atravesar la confirmación real de salida GDX durante una pausa, pasa junto a
  su homólogo lógico 2/2 en
  `target/gdx-scenarios/20260913-070739-fast`. El método 43 atraviesa el botón
  ALL-IN real de la mesa GDX y demuestra que la primera pulsación sólo lo arma
  y la segunda envía exactamente una orden; pasa junto al escenario de red
  homólogo 2/2 en `target/gdx-scenarios/20260913-071407-fast`. Las ampliaciones
  posteriores añaden recompra manual en dos etapas, pausa/reanudación, RIT con
  reconexión y acciones normales desde controles de producción. La campaña
  completa 48/48 ya se ha repetido y está verde en el corte consolidado citado
  arriba.
- La nueva ejecución aislada descubrió dos defectos reales que la ejecución
  agregada podía ocultar: una orden tardía de espectador contra un ejecutor ya
  cerrado y la cancelación prematura de la verificación honesta del barajado al
  guardar una recuperación. Ambos se corrigieron sin saltarse barreras ni
  marcar pruebas como verificadas artificialmente.
- Las topologías de recompra real `host GDX + cliente Swing` y
  `host Swing + cliente GDX` pasan por separado (1/1 cada una), con cinco manos,
  consenso, evento de recompra en la proyección GDX, buy-in acumulado y
  conservación monetaria. Se eliminó una aserción probabilística que suponía
  qué asiento debía quebrar; el camino de decisión GDX sigue cubierto por el
  escenario GDX nativo de recompra.
- Los runners ya no aceptan como evidencia clases o artefactos anteriores. El
  runner estricto elimina únicamente los `target` generados del reactor de
  módulos, recompila el checkout actual y después aísla cada método en una JVM.
  El runner mixto instala primero el checkout actual en su repositorio Maven
  local y después aísla cada topología. Este endurecimiento descubrió una lista
  incompleta de fuentes del módulo core y un resumen final GDX que prefería un
  auditor obsoleto a los stacks vivos; ambos defectos quedaron corregidos.
- El escenario de bloqueo `GAME OVER -> ESPECTADOR` atraviesa ahora el mismo
  método de resolución de diálogo que usan el ratón y el teclado de la mesa
  GDX; ya no resuelve directamente el modelo desde el test. Host y cliente
  poseen una mesa GDX unida a su sesión y proyección reales: la mano usa sus
  controles nativos, el diálogo final se promueve y retira mediante la misma
  máquina de estados que el bucle de render y la prueba exige que no quede
  ningún overlay activo. Con el callback de audio perdido deliberadamente, la
  prueba focalizada vuelve a pasar y el crupier libera las dos mesas. Los
  contratos cortos de catálogo pasan 6/6; uno de ellos impide reintroducir una
  mesa sintética separada para contestar diálogos de red.
- El escenario `controlled-exit` ya no se limita a enviar `ExitGame` desde el
  harness: una segunda prueba arma y acepta el diálogo real de salida de
  `CoronaPokerGdxTable` mientras la mesa de red está pausada y exige el cierre
  de host y cliente. Ambos métodos pasan 2/2 tras preflight limpio.
- El escenario `allin-single-board` incluye una segunda prueba que pulsa el
  control ALL-IN de producción de `CoronaPokerGdxTable`: la primera activación
  no envía ninguna orden y la segunda envía una única `TableCommand.AllIn`.
  Así se cubre el armado real del botón además del resultado de negocio.
- RIT, straddle y recompra manual ya no contestan mediante proxies que eluden
  la interfaz. RIT acepta desde el diálogo de producción y completa ambos
  tableros (`20260913-072824-fast`); straddle rota por tres humanos y sobrevive
  a un corte posterior a la aceptación (`20260913-073111-fast` y
  `20260913-073158-fast`); recompra recorre `GAME OVER -> CONTINUAR -> importe`
  y, junto a la variante automática de cinco manos, pasa 2/2 en
  `target/gdx-scenarios/20260913-073803-fast`.
- El endurecimiento posterior elimina además la última mesa de diálogo
  artificial de la integración de red. Cada peer de RIT, straddle y recompra
  posee su propio `CoronaPokerGdxTable`, comparte con el renderer el estado de
  esa sesión y envía ALL-IN o pasar/igualar mediante los métodos usados por el
  ratón/teclado de producto. `GAME OVER -> CONTINUAR -> RECOMPRA` avanza por la
  cola real de esa misma mesa. El bloque completo de 13 métodos `nativeGdx*`
  pasa 13/13 en una sola ejecución FAST (2026-09-13 09:05-09:08); incluye RIT,
  straddle con corte/reconexión, espectador, recompra, salida en pausa,
  recuperación y acciones humanas. No equivale a QA visual OpenGL.
- El arnés común `GdxScenarioRenderer` ya no envía `Fold`, `CheckOrCall` ni
  `AllIn` directamente al `TableSession`: cada peer construye al abrirse su
  `CoronaPokerGdxTable` de producto sobre la misma proyección y las decisiones
  atraviesan sus controles nativos. También se eliminó la mesa auxiliar que aún
  quedaba en `rit-network-cut`; los tres diálogos RIT pertenecen ahora a las
  mesas reales de host y clientes. Tras el cambio pasan 1/1 RIT con corte y
  reconexión, 2/2 partida normal/reconexión a mitad de mano y 1/1
  `GAME OVER -> ESPECTADOR` con callback de audio perdido. El contrato pasa
  7/7 e impide reintroducir esos atajos. Esto acerca el arnés al método de la
  suite Swing (componentes reales sin ventana visible), pero sigue sin ser una
  prueba visual OpenGL.
- La preparación del escenario nativo de recuperación también dejó de enviar
  su primera acción directamente al core: la registra mediante el control
  pasar/igualar de la mesa GDX y después fuerza la recuperación real. Esa
  regresión y el contrato anti-atajos pasan juntos 8/8.
- `rit-network-cut` conserva ahora el diálogo RIT real del cliente retrasado
  abierto mientras se corta y reconecta el socket de otro votante; después lo
  acepta por `CoronaPokerGdxTable.resolveActiveDialogChoice`, completa las dos
  caras y conserva el mismo ledger en los tres peers. Pasa 1/1 en
  `target/gdx-scenarios/20260913-082007-fast`; cada diálogo está ligado a la
  identidad local real de su peer.
- Pausa/reanudación ya no se limita a enviar `TogglePause` desde el harness:
  botón, overlay, atajo y prueba comparten `togglePauseAction()`. El escenario
  homólogo de dos manos y la partida humana accionada desde la mesa GDX pasan
  2/2 en `target/gdx-scenarios/20260913-080218-fast`; la acción retenida tras
  reanudar también cruza el control GDX real de pasar/igualar.
- Las rutas de ratón, atajo y pruebas de fold, pasar/igualar y apostar/subir
  comparten ahora métodos de activación de producción. `normal` pasa 5/5,
  incluyendo una mano en la que ambos humanos accionan el control GDX real, en
  `target/gdx-scenarios/20260913-080335-fast`. `raise-mix` pasa 1/1 con diez
  manos y las decisiones humanas enviadas por los controles GDX de subir,
  pasar/igualar o ALL-IN en
  `target/gdx-scenarios/20260913-080826-fast`.
- La regresión adicional en la que el local hace fold y observa el showdown de
  otros dos humanos ya forma parte del bloque estricto `normal`. Las tres
  sesiones usan su propio `CoronaPokerGdxTable` y accionan fold, pasar/igualar
  y ALL-IN por los controles de producción; el escenario exige los destapes
  remotos, Monte Carlo, resultados con ganador y conservación de las cartas
  reveladas tras `HandBoundary.END`. La prueba focalizada pasa 1/1; el bloque
  `normal` completo, ahora de seis métodos, aún no se ha repetido.
- El escenario nativo de bancarrota, espectador y recompra ya no presupone qué
  identidad concreta perderá una mano barajada de verdad: detecta al quebrado
  real, atraviesa en su propia mesa GDX la recompra inmediata y libera el turno
  retenido por el control de producción correspondiente. La prueba focalizada
  completa siete manos con cuatro peers, consenso y retorno al anillo activo
  (1/1, 2026-09-13 10:08).
- `force-recover` incluye ya la prueba nativa que presenta la superposición GDX
  no descartable, reproduce la acción local registrada y exige que sea el
  crupier quien cierre la superposición al terminar la recuperación. Sus dos
  métodos pasan 2/2 en
  `target/gdx-scenarios/20260913-081619-fast`.
- La matriz mixta dispone además de un runner aislado propio:
  `tools/qa/gdx-mixed-scenarios.cmd`. Descubre actualmente 30 escenarios
  `MixedFrontendNetworkE2EIT` y ejecuta cada topología Swing/GDX en una JVM
  limpia con perfiles FAST/BALANCED/STRESS. Las dos topologías RIT pasan 1/1
  cada una exigiendo dos boards, destapes, Monte Carlo, resultados, `Payout`,
  bote final cero y concordancia del ledger/resumen. El escenario Swing RIT
  exacto también pasa 1/1. Todavía no se ha repetido la campaña completa de 30
  tras este cambio localizado.
- El reactor completo empaquetó el checkout actual y reemplazó el JAR GDX:
  `target/CoronaPoker-24.11-gdx.jar` (266.193.725 bytes, SHA-256
  `FF763436D3C97D462E00046E3FE3F647C0275C7640447C1F435E2B5EF6694791`).
  Un arranque real controlado en ventana y sin sonido permaneció vivo durante
  ocho segundos, creó el back buffer MSAA 4x y se cerró expresamente. Este smoke
  prueba arranque y carga nativa, no valida aspecto visual ni una partida.

## Evidencia verificada más reciente

- Una confirmación AUTO que permaneciera abierta ya no puede enviar una orden
  atrasada mientras el cliente está reconectando ni después de solicitar la
  terminación de la mesa. La misma indisponibilidad desactiva el turno local y
  retira el veto pendiente al consumir el siguiente evento. Pasan 89/89 pruebas
  del estado GDX y 1/1 escenario de reconexión a mitad de mano con tres humanos,
  dos manos y consenso (2026-09-13 10:20).
- Estado de turno GDX endurecido sin cambiar core ni Swing: un
  `currentTurnNickname` atrasado ya no puede mantener brillo activo,
  `PENSANDO...`, `TU TURNO`, controles ni envío de acciones para un jugador
  retirado, espectador, salido, desconectado o ausente. Teclado, ratón,
  confirmación AUTO y HUD consumen la misma regla canónica. Pasan 89/89 pruebas
  de estado de mesa y 1/1 integración de red real con transición a espectador y
  pérdida deliberada del callback de audio. Falta comprobar visualmente los
  estados de reconexión con procesos GDX visibles.
- Regresión P0 de espectador y showdown cerrada: una pérdida del callback de
  audio de `nocontinue.wav` ya no puede dejar al crupier esperando para siempre
  después de elegir **ESPECTADOR**. Una prueba de red real fuerza la bancarrota
  de un humano, usa el `GdxGameDecisionSink` y la mesa GDX unidos a la sesión
  real, pierde adrede ese callback, comprueba la retirada del overlay, la
  entrada efectiva como espectador y el cierre de ambas mesas. El escenario
  homólogo Swing de 4 humanos/7 manos también completa
  espectador, recompra y retorno al anillo. El fold queda retenido hasta
  `HandBoundary.PREPARE`: una instantánea END tardía no puede reactivar el
  asiento ni sus cartas, mientras que un `RevealHoleCards` explícito conserva
  SHOW/IWTSTH sin reactivar al jugador. Evidencia dirigida: 111/111 de estado,
  diálogo y decisión; 1/1 red GDX con diálogo real; 1/1 ciclo largo de
  espectador; 1/1 fold local con Monte Carlo/showdown remoto. El contrato de
  escenarios exige ahora que los 37 escenarios Swing tengan cobertura GDX
  estricta, que los mapeos sean métodos `@Test` ejecutables y que una misma
  prueba no certifique dos escenarios distintos. El contrato obliga además a
  que las interacciones GDX bloqueantes catalogadas tengan una prueba de la
  mesa nativa y pasa 7/7. JAR generado:
  `target/CoronaPoker-24.11-gdx.jar`, SHA-256
  `FF763436D3C97D462E00046E3FE3F647C0275C7640447C1F435E2B5EF6694791`.

- Auditoría causal de residuos de la demo ampliada: un turno sin tiempo de
  pensar ya no se convierte artificialmente en una barra llena; el HUD parte
  de apuesta `0` y sólo adopta el importe publicado por `ActionControls`; los
  repartos sin jugadores activos y las transferencias hacia jugadores ausentes
  fallan como violaciones del contrato en vez de continuar con asientos o
  posiciones inventados. También se eliminó la compatibilidad con el esquema
  de URL malformado de previews GDX y se conserva únicamente el transporte
  `img://` / `imgs://` real de Swing. Las barreras arquitectónicas impiden
  reintroducir estos fallbacks. Verificación dirigida: 73/73 GDX y 5/5 de
  arquitectura, sin cambios en `reference/gdx-demo` ni en el flujo Swing.

- Frontera demo/producto ampliada: GDX ya no fabrica un `AudioCue` canónico
  falso para reproducir el sonido de fin de partida; la reproducción interna
  recibe únicamente parámetros audiovisuales. La prueba arquitectónica impide
  construir cualquier `TableVisualEvent` desde producción GDX y también
  bloquea los símbolos de cronología, manos, acciones y showdown de la película
  demo. El actor de una cinemática ALL-IN viaja ahora explícitamente desde el
  crupier: GDX ya no lo adivina inspeccionando la última acción ni sustituye un
  evento incompleto por una película predeterminada. Además, el layout cuenta
  el mismo roster visible que dibuja, sin incluir jugadores remotos ya salidos
  ni dejar asientos fantasma. Verificación dirigida: 69/69 GDX y 5/5 de
  arquitectura; certificación Swing/Core posterior: 1.120/1.120 FAST.

- Autoridad de estado de la mesa GDX reforzada: se eliminó del renderer de
  producto el guión residual heredado de la demo (jugadores y acciones
  prefabricados, manos/board, rotaciones, botes, vuelos y temporizadores
  simulados), junto con sus arrays y helpers ya muertos. La intro es ahora el
  único modo sin snapshot y sólo contiene composición visual; una mesa normal
  rechaza construirse sin `GdxTableViewState`. También se retiraron del
  artefacto de producción el launcher y los fallbacks de preview del menú: sus
  servicios, persistencia, recuperación y entrega de sesión son obligatorios.
  `GdxProductionStateAuthorityTest` protege estas fronteras y el bloque dirigido
  completo pasa 77/77 pruebas. La proyección rechaza explícitamente cualquier
  tipo de evento del core que no tenga consumidor GDX, impidiendo que una futura
  ampliación se convierta silenciosamente en un no-op. La frontera arquitectónica
  adicional pasa 5/5 y una compilación limpia confirma que no quedan clases de
  preview/demo en el artefacto. La referencia `reference/gdx-demo` no se tocó.

- Compatibilidad de transporte Swing/GDX: dos pruebas cruzadas reproducen el
  framing, cifrado y autenticación Ed25519 reales. Un cliente con el codec
  legado Swing entra en un host `NetworkLobbyGateway`, y un cliente GDX entra
  en un servidor legado Swing; se verifican JOIN, versión, identidades, roster,
  INIT, GAME, ACTION y confirmaciones. El cifrado GDX es idéntico byte a byte al
  codec Swing para las mismas claves, IV y orden.
- Partida humana mixta real: `MixedFrontendNetworkE2EIT` pasa actualmente 30/30
  pruebas en una ejecución de 13:12 min, sin fallos, errores ni omitidas. Se han
  completado mesas de tres humanos hasta cierre y saldo final en las dos
  topologías exigidas: host Swing con clientes Swing/GDX y host GDX con
  clientes Swing/GDX. Las manos normales recorren preflop, flop, turn, river,
  showdown, consenso unánime y conservación monetaria independiente; el bloque
  incluye además pausa, ALL-IN/RIT, recompra, salida, reconexión y recuperación.
  Esta evidencia protege core, transporte y barreras con frontend adjunto, pero
  no sustituye la QA visual multiproceso con ventanas OpenGL reales.
- Certificación agregada de red GDX: 54/54 pruebas correctas en una misma
  ejecución (16 de partida humana/proyección real y 38 de reconexión,
  recuperación y fallos de transporte). Incluye timeout, pausa/salida,
  straddle, RIT, recompra, sustitución de clientes y recuperación entre manos.
  Se corrigieron dos carreras de estado de espectadores sin modificar Swing:
  limpieza del `ALL_IN` residual y convergencia `EXIT`/`SPECTATOR` para bots
  quebrados. La protección Swing posterior pasa actualmente 1.120/1.120 pruebas
  `qa-fast`.
- Pausa mixta corregida y probada: un cliente GDX contra host Swing aplica su
  petición local únicamente después de confirmar el comando autenticado,
  respetando que el host Swing excluye al solicitante del relay. Los tres
  participantes observan pausa y reanudación antes de liberar la acción.
- ALL-IN mixto probado en ambas topologías: el renderer GDX retiene de forma
  deliberada el `CompletionStage` de la cinemática y se comprueba que ningún
  control posterior la adelanta. Tras liberarla, los tres peers llegan al mismo
  showdown y al mismo saldo conservado.
- Recompra humana mixta real probada en ambos sentidos de red, fuera de modo de
  prueba: host Swing + cliente GDX y host GDX + cliente Swing completan cinco
  manos consecutivas, aceptan y aplican recompras entre manos, conservan saldo y
  alcanzan consenso en todos los extremos. Las ejecuciones duraron 125 y 151
  segundos, respectivamente.
- Heartbeat nativo GDX terminado: conserva el contrato dual `PING/PONG/PONG2` de
  Swing, mide ambas latencias, actualiza el `LobbySnapshot` y cierra canales mudos
  tras tres rondas fallidas. Se corrigió la expulsión de clientes GDX sanos a los
  45 segundos y se verificaron de nuevo las dos topologías mixtas de tres humanos.
- Regresión Swing de reconstrucción de mesa corregida: al eliminar un bot
  quebrado, los nuevos controladores visuales vuelven a enlazarse al crupier
  canónico antes de iniciar la siguiente mano. La semilla exacta que fallaba
  (`1417940584`) completó siete manos con caída, reducción, recompra y
  reincorporación del bot, consenso unánime y conservación monetaria.
- Certificación FAST: las 38 fases anteriores al fallo pasaron; después de la
  reproducción exacta, la continuación pasó 5/5 (compilación y los cuatro
  escenarios finales: bot que vuelve, bot que abandona, humano que
  sale/reentra/recompra y doble recuperación con caída). Evidencia en
  `target/certification/20260907-020735` y
  `target/certification/20260907-025633`.

- Reactor completo `modules/pom.xml`: compilación correcta y 172 pruebas
  correctas (101 core + 67 GDX + 4 de arquitectura), 0 fallos, 0 errores y 0
  omitidas. Incluye los módulos Swing y GDX, pero no ejecuta ni modifica los
  escenarios Swing. Duración: 1:41 min.
- Recuperación GDX/Swing:
  - el cargador GDX lee el último juego local con el esquema estricto de Swing;
  - host y cliente transportan el identificador/configuración autoritativos;
  - GDX persiste `recover_settings` con las mismas claves y formato que Swing;
  - la acción final `CONTINUAR ESTA TIMBA` del anfitrión inicia la recuperación
    real con la contraseña conservada, no una timba nueva aproximada;
  - están probados el codec completo y la escritura real en SQLite;
  - una integración real detiene una mano con bot, carga la fila SQLite,
    reproduce las acciones persistidas y completa la mano recuperada con
    consenso y saldo final correctos;
  - una segunda integración termina una timba, ejecuta la misma continuación
    desde pantalla final, abre la siguiente mano recuperada, repone
    dealer/pequeña/gran ciega y la completa con consenso y saldos correctos. El
    defecto descubierto era causal: la recuperación reiniciaba a los jugadores
    antes de restaurar las posiciones de la mano. Falta QA visual.

- `NetworkHumanGameTableIntegrationTest`: 14 pruebas correctas.
  - Dos mesas humanas completan dos manos autenticadas.
  - Pausa y reanudación coordinadas entre ambos extremos.
  - La primera cinemática ALL-IN permanece retenida y ningún control de turno la
    adelanta.
  - La recompra automática evita la selección de game-over, se aplica y permite
    completar la siguiente mano con saldos coincidentes.
  - Straddle real con anfitrión y dos clientes humanos, incluyendo la decisión
    firmada y el cierre completo de la mano.
  - ÚLTIMA MANO manual se propaga desde el anfitrión y cierra naturalmente al
    terminar la mano.
  - SALIR del anfitrión, DETENER para recuperar con contraseña y SALIR del
    cliente cierran todas las mesas; la salida del cliente entrega y valida su
    testamento criptográfico antes de cerrar el canal.
  - Una mano interrumpida se recupera desde SQLite y termina correctamente.
  - Una timba ya terminada continúa desde la pantalla final y la nueva mano
    recuperada conserva y cobra sus ciegas obligatorias.
- Compra inicial variable: integración real host+cliente correcta. Cada frontend
  eligió una cantidad distinta (7 y 13), ambos extremos conservaron esos
  `totalBuyin` y la mano cerró con conservación exacta del dinero.
- Contrato GDX de compras/recompras probado: compra inicial obligatoria,
  recompra automática cancelable y recompra inmediata cancelable mantienen
  timeout, rango, cantidad y resultado distintos. El vuelo de recompra es un
  evento causal con barrera: las fichas salen del centro inferior, aterrizan en
  cada jugador y sólo después se aplica el estado final y continúa la mesa.
- Calidad de back buffer: 4x MSAA por defecto y selector compartido 0x/2x/4x/8x
  en Ajustes de menú y mesa. Una ejecución real en el monitor 2 verificó
  `GL_SAMPLES=4`; la interfaz distingue el valor solicitado del activo cuando
  el cambio necesita reinicio. Las cartas vivas usan exclusivamente recursos
  `hq/`, mipmaps, filtrado lineal y la anisotropía máxima disponible.
- Ajustes vivos GDX/Swing: existe ya una orden tipada de configuración completa,
  validación que impide modificar campos inmutables durante la timba y un evento
  autoritativo que proyecta la configuración real en GDX. El anfitrión emite los
  comandos clásicos compatibles (`UPDATEBLINDS`, `MAXHANDS`, `IWTSTHRULE`,
  `RUNITWICERULE`, `RABBITRULE`, `BOTREBUYRULE`, `BOTBALRULE`) y el cliente GDX
  ya los consume. Un E2E real host-cliente completó una mano y verificó que
  ambos extremos terminaron con ciegas/reglas idénticas. La pantalla de mesa
  ofrece borrador con Cancelar/Guardar para estructura y nivel de ciegas,
  aumento y tope, ante, straddle, límite de manos, IWTSTH, Run It Twice,
  Rabbit y reglas de bots. Compra, tiempo de pensar y tiempo de showdown se
  presentan explícitamente como datos fijados al crear la timba, igual que en
  Swing. Una política única bloquea la edición al cliente, bloquea Run It Twice
  cuando ya quedó fijado por la mano y sólo permite recompra de bots si la
  timba admite recompras. El bloque dirigido pasa 44/44 pruebas; falta QA
  visual multiventana y terminar las opciones de Ajustes que aún no tienen
  consumidor GDX real.
- Audio desde Ajustes de mesa: el interruptor maestro ya actualiza en la misma
  acción el estado compartido de audio, la preferencia persistida, la música,
  los loops del crupier y el volumen TTS. Antes sólo cambiaba la propiedad y
  podía discrepar de lo que realmente sonaba. La señal `sonido_entra` conserva
  ahora su semántica Swing de crear partida/nuevo jugador y no se confunde con
  la solicitud de admisión pendiente (`sonido_entrar_sala`), cuyo flujo GDX aún
  no existe y por ello no se anuncia como ajuste operativo.
- Estructura del diálogo de Ajustes contrastada directamente con Swing:
  Apariencia, Audio, Atajos y Debug son comunes; la misma pantalla añade
  Partida únicamente dentro de una mesa. GDX conserva ya ese orden y esa
  condición, sin mantener dos diálogos divergentes.
- Ejecución integral GDX más reciente: 217 pruebas ejecutadas en 24:19 min;
  216 correctas y un timeout en el escenario de caos de ciclo de vida durante
  reconexión, pausa y dos recuperaciones. La reproducción aislada del escenario
  fallido completó las dos recuperaciones y la mano 7 correctamente (1/1 en
  54,09 s). Por tanto no se trata como regresión reproducida ni se maquilla como
  suite integral verde: queda pendiente repetir el bloque acumulado para
  distinguir saturación/contaminación entre escenarios de un defecto
  intermitente. Los 20 tests dirigidos añadidos para Ajustes y audio pasan 20/20.
- Pruebas GDX dirigidas: 8 correctas.
  - Estado de presentación y recompra automática.
  - Modelo de diálogos.
  - Diálogo Auto Call.
  - Decisión nativa de straddle con aceptar, rechazar y timeout.
- Atajos personalizados Swing→GDX: la mesa GDX ya interpreta el formato
  persistido `shortcut.<id>` sin confundir los keycodes AWT con los de libGDX.
  Están conectadas las acciones GDX existentes de póquer, pausa, luces, salida,
  parada, registro, sonido/volumen, recompra, voz, imagen y pantalla completa; se
  conservan los alias laterales de apuesta y se exigen modificadores exactos.
  La pestaña Atajos ya ofrece las 19 acciones aplicables, captura paginada,
  bloqueo de colisiones, restauración y Guardar/Cancelar transaccional.
  Compilación GDX correcta y 13/13 pruebas dirigidas correctas.
- Restauración de Ajustes GDX: Audio y Apariencia ofrecen ya
  `RESTAURAR PREDETERMINADOS` tanto desde el menú como durante la partida.
  Reponen los valores de fábrica compartidos de Swing salvo la baraja, que
  conserva Goliat como valor GDX aprobado, aplican la previsualización inmediata
  y respetan Guardar/Cancelar. En partida no alteran opciones gráficas
  que exigen reinicio y un cliente conserva las reglas globales de voz/TTS
  impuestas por el anfitrión. El contrato y los consumidores de presentación
  pasan 25/25 pruebas dirigidas; la suite integral se interrumpió al entrar en
  simuladores de red/core no relacionados y no se contabiliza como evidencia.
- Sonido de fin de partida GDX cableado a su consumidor real y expuesto en la
  página de Audio. Conserva el contrato temporal de Swing: `GAME OVER` silencia
  los loops mientras está activo, se detiene al decidir, `rebuy.wav` no retrasa
  el diálogo de compra y `nocontinue.wav` mantiene su barrera aunque la opción
  esté silenciada. Las decisiones no quedan bloqueadas si falla el dispositivo
  de audio. Compilación y 34/34 pruebas dirigidas correctas.
- Resaltado de avatares portado como consumidor GDX real: tras los mismos
  250 ms de hover que Swing aparece una ampliación local con nick y stack
  vivos, limitada al 45 % de la altura de la mesa. Se cierra al salir de su
  zona, al desactivar la preferencia o al abrir otra capa, y consume el puntero
  para impedir clics sobre controles tapados. Compilación correcta y familia
  dirigida de Apariencia/Ajustes/presentación: 28/28 pruebas correctas. Falta
  QA visual interactiva con avatares reales y resoluciones extremas.
- Menú contextual de mesa contrastado de nuevo con `TapetePopupMenu` de Swing:
  conserva Ajustes, registro, capturas, controles AUTO, confirmaciones,
  recompras, última mano, parada y salida; incorpora además pantalla completa,
  cinemáticas, imágenes del chat, reloj y selección directa de los cinco tapetes
  Swing. Los booleanos muestran su estado con el interruptor verde y Barajas,
  Tapetes y Ayuda son submenús reales. Ayuda contiene Atajos, Reglas de Robert
  y Generador de jugadas; el selector de barajas instalado mantiene también las
  barajas del mod. El generador es nativo,
  conserva el orden, probabilidades y cantidad visible de cartas de Swing y
  renderiza recursos HQ de la baraja activa. Las filas booleanas, incluida Recomprar
  siguiente mano, reflejan su estado y ningún clic se propaga al HUD inferior.
  Compilación correcta; el incremento de paridad pasa 102/102 pruebas dirigidas
  de contrato, presentación, estado de mesa, diálogos y generador.
  Falta QA visual interactiva del popup y generador.
- Corregido el contrato temporal del reparto vivo GDX: ya no usa siempre los
  tiempos fijos de la demo, sino la misma fórmula histórica de Swing según el
  número de jugadores activos y `reparto_velocidad` (pisos separados de pausa
  y vuelo). Con la animación de reparto desactivada conserva la cadencia de
  Swing sin dibujar el vuelo; con destape desactivado la carta local aterriza
  directamente visible. Los tiempos de la demo canónica permanecen intactos.
  Compilación y bloque dirigido de estado/Ajustes/presentación: 83/83 pruebas
  correctas. Falta QA visual de las tres velocidades y del modo sin animación.
- Ordenación y destape de cartas ligados ya a sus ajustes reales GDX: el swap
  queda instantáneo cuando está desactivado y, cuando está activo, consume la
  duración y el arco configurados en vez de constantes de la demo. En showdown
  las dos cartas rivales se destapan simultáneamente con un único sonido como
  Swing; sin animación aparecen juntas, y el destape plano del straddle local
  permanece silencioso. El jugador local que ya ve sus cartas no repite la
  animación. Compilación y bloque dirigido: 86/86 pruebas correctas. Falta QA
  visual y auditar barajado, comunitarias, fichas y contadores.
- Barajado vivo GDX ligado ya a `animacion_barajado`: con animación muestra el
  GIF de la baraja activa y corta su WAV en el fotograma canónico de Swing; sin
  animación oculta las comunitarias y usa `BARAJANDO…`. Si el sonido está
  activo, el ciclo sin GIF dura exactamente lo que el WAV real (2,7196 s para
  el recurso oficial); si también está desactivado conserva los ciclos de
  300 ms de Swing. No se carga ni dibuja el GIF como sustituto de una opción
  apagada.
- El destape vivo de comunitarias recibe ahora del crupier el lead-in causal
  real de Swing (1.000 ms normal, 2.000 ms en resistencia/all-in y cero al
  salir). El flop respeta la secuencia de sus tres cartas, la cola de 100 ms de
  cada giro y el modo sin animación, en vez de usar el solape rápido de la demo.
  El contrato/puente pasa 12/12, el bloque GDX dirigido 87/87 y la protección
  Swing/Core posterior 1.118/1.118 `qa-fast`. Falta QA visual/auditiva.
- La carga inicial de stacks ya no cae en el `noop` visual al usar GDX: el
  crupier publica un evento causal de un segundo con las cantidades reales,
  GDX representa el recorrido 0→buy-in, reproduce el sonido configurado y
  sólo libera la barrera al terminar. El evento es transitorio y no altera la
  contabilidad canónica. Tras este cambio pasan 88/88 pruebas GDX dirigidas,
  13/13 de contrato/puente y 1.119/1.119 `qa-fast` Swing/Core, sin fallos,
  errores ni omitidas. Falta QA audiovisual de la secuencia completa.
- Se intentó repetir las 28 clases de prueba GDX en una sola invocación, pero
  se canceló deliberadamente al entrar en los watchdogs reales de 45 segundos
  del escenario largo de caos/reconexión. No se contabiliza como certificación
  ni como fallo; ese acumulado se reserva para el siguiente hito grande.
- La ruta de terminación GDX reutiliza una sola confirmación pendiente, emite
  una sola orden y espera el `CloseTable` autoritativo. Salida del host activa,
  salida del host durante pausa, salida del cliente y cableado de confirmación
  pasan 6/6 pruebas dirigidas. El escenario largo de reconexión, pausa y dos
  recuperaciones ha vuelto a pasar aislado en 59,18 s; la última integral se
  mantiene honestamente en 216/217 hasta repetir el acumulado.
- Se ha eliminado de la superficie GDX el interruptor global Swing para apagar
  todas las animaciones. GDX conserva los interruptores específicos y preserva
  sin modificar la preferencia Swing oculta. También neutraliza en su backend
  la vista compacta y los zoom de mesa/diálogo Swing (`0`, `1.0`, `1.0`), para
  que preferencias heredadas no alteren cartas, barreras ni geometría GDX. Las
  familias dirigidas de Ajustes/presentación pasan 36/36 y la verificación
  específica posterior 27/27.
- La certificación completa `qa-fast` se ha repetido después del endurecimiento
  de salida/detención y del aislamiento de preferencias exclusivamente Swing:
  1.120/1.120 pruebas correctas, sin fallos, errores ni omitidas. Esto protege el
  core y el frontend Swing frente a los cambios compartidos actuales; no
  sustituye la QA visual GDX pendiente.
- Reconexión forzada desde la mesa GDX cerrada como control exclusivo del host:
  conserva cada peer lógico y su cola, sustituye sólo la generación física del
  socket y deja que el lector canónico publique la caída y active el watchdog.
  El cliente no puede invocarla, los bots quedan fuera y dos activaciones rápidas
  reutilizan una sola confirmación y emiten una única orden. La regresión ampliada
  pasa 8/8 pruebas core/red y 23/23 GDX, incluida una mano humana hasta consenso;
  la protección posterior Swing/Core vuelve a pasar 1.120/1.120 `qa-fast`.
  Falta QA visual con dos procesos y documentar como transitorio el aviso de
  socket cerrado que acompaña a la sustitución intencionada.
- Los escenarios Swing no han sido modificados.
- Perfiles de Nueva timba ya no son controles de muestra en GDX: el catálogo
  persistente se ha extraído a un contrato neutral compartido por Swing y GDX,
  y GDX carga, restaura el perfil por defecto, guarda, confirma sobrescritura y
  confirma borrado sobre las mismas claves `game_preset.*`. La edición del
  nombre admite caret, selección, portapapeles y repetición de borrado. Pruebas
  dirigidas: 3/3 del catálogo neutral, 1/1 de compatibilidad Swing y 21/21 del
  bloque GDX relacionado. Falta QA visual del modal y del nuevo subpanel.
- Las estructuras de ciegas ya no dependen del editor Swing: el catálogo
  neutral puede escribir el formato clásico validando el conjunto completo
  antes de tocar propiedades, y Nueva Timba incorpora un editor GDX nativo
  para crear, duplicar, renombrar, borrar y ajustar niveles. El anfitrión puede
  abrir el mismo editor desde Ajustes de la sala; allí forma parte de la
  transacción exterior, de modo que Cancelar restaura también el catálogo y
  Guardar lo persiste junto con la configuración autoritativa. Pasan los tests
  focalizados de catálogo, modelo editor, contrato y sesión de Ajustes. Falta
  QA visual del modal en el JAR.
- La intro GDX usa ahora `coronapoker_logo_big.png` (1050x616) en vez de ampliar
  `corona_poker_splash.png` (525x308). La geometría y los tiempos no cambian;
  falta confirmar visualmente la nitidez en el JAR actual.
- No se ha repetido todavía la suite `balanced`; se reserva para el cierre de un
  bloque estable.
- El ejecutable GDX actualizado se genera únicamente como
  `target/CoronaPoker-24.11-gdx.jar`; no usar JARs intermedios de módulos.

## Orden de ejecución pendiente

### P0.1 — Pausa y continuidad de la mano

Estado: coordinación core/red probada; QA interactiva GDX pendiente.

- Reproducir y cerrar el bloqueo observado al reanudar.
- Verificar reanudación desde botón, atajo y clic en overlay.
- Restaurar correctamente controles y temporizador del mismo turno.
- Apagar/restaurar luces como Swing.
- Validar permisos del cliente, propietario de la pausa y contador.
- Confirmar que salida/cierre liberan cualquier espera de pausa.
- HECHO automático: temporizador con pausa/reanudación conserva el turno; el
  timeout humano ejecuta la acción canónica, cierra la mano en host/cliente y la
  salida inmediata funciona tanto activa como pausada (6 pruebas dirigidas,
  incluidas 3 integraciones de red reales).

### P0.2 — Recompras y compra inicial

Estado: rutas core y red mixtas probadas; QA interactiva y límites avanzados pendientes.

- Certificar visualmente recompra por bancarrota.
- Certificar recompra automática y cancelación.
- Certificar recompra inmediata, límites, tope y red.
- HECHO automático: diálogo inicial de buy-in variable antes de la primera mano,
  con cantidades distintas en una integración host+cliente.
- HECHO en código: vuelo causal de fichas desde el centro inferior hacia el
  jugador, contador por aterrizaje y barrera de finalización. Falta QA visual y
  certificar el sonido en ejecución.
- HECHO automático: la decisión de recompra se completa antes de evaluar el
  cierre de la timba. Dos pruebas mixtas largas completaron cinco manos y varias
  recompras con host Swing/GDX en ambos sentidos sin convertir al quebrado en
  espectador ni cerrar la mesa prematuramente.

### P0.3 — Barreras causales y animaciones

Estado: ALL-IN probado automáticamente; resto pendiente de certificación.

- Reparto de cartas y velocidad constante entre manos.
- Destape y swap de cartas locales.
- Fichas de jugador al bote.
- Pago del bote al ganador.
- Recompra hacia el jugador.
- GIF y audio ALL-IN local/remoto.
- Showdown y cascadas.
- Revisar sincronía de todos los sonidos de impacto.
- HECHO en código: los impactos de fichas se disparan al aterrizar y el audio
  vivo respeta los interruptores Swing de barajado, reparto, destape general y
  propio, pasar, retirarse, igualar, apostar y ALL-IN. La matriz de preferencias
  pasa 9/9 pruebas dirigidas; falta QA auditiva GDX.
- HECHO en código: reparto, barajado, destape, swap, apuestas, cobros, recompra,
  carga inicial de stacks y contadores GDX ya consultan sus interruptores y
  tiempos reales sin alterar los tiempos de la demo. Falta QA audiovisual
  conjunta antes de declarar completa esta sección.

### P0.4 — Straddle completo

Estado: flujo core certificado con tres humanos; QA visual y edición en vivo
pendientes.

- HECHO: prueba de integración con anfitrión y dos clientes humanos.
- HECHO: decisión afirmativa real firmada; el diálogo unitario cubre también
  negativa y timeout.
- Certificar que las cartas permanecen ocultas hasta decidir.
- Verificar orden preflop, ficha/posición y recuperación.
- HECHO en código/E2E: el host puede cambiar straddle y ante desde Ajustes y se
  propagan de forma autoritativa al cliente. Falta QA visual interactiva.

### P0.5 — Automatismos

Estado: reglas de ejecución y persistencia AUTO certificadas en red real;
queda QA interactiva del diálogo de veto y de sus controles.

- Auto Call: diálogo GDX ya permite activar, desactivar, sin límite y máximo en
  pasos de 0,05.
- HECHO automático: Auto Call se ha ejercitado en una mesa humana real ante
  check, call, apuesta superior al máximo, cambio de calle y call que consume
  el stack y debe convertirse en ALL-IN.
- HECHO automático: una selección AUTO persistente cruza una frontera de mano;
  con persistencia desactivada, la misma selección queda limpiada y no se
  ejecuta en las manos siguientes. La matriz dirigida completa pasa 4/4.
- HECHO: la señal neutral de preacciones conserva también la selección de un
  jugador retirado para la mano siguiente, igual que Swing; sigue bloqueada para
  all-in, espectador, salida y showdown. Cubierto por 6/6 pruebas QA dirigidas.
- HECHO en modelo GDX: Auto Mode usa diálogo no modal para utilidades, bloquea
  sólo las acciones de póquer como Swing, congela su cuenta atrás durante pausa,
  acepta por teclado y se invalida al terminar el turno local. Falta E2E visual.
- HECHO en código/modelo: deshabilitar los botones AUTO limpia la preacción y
  el puente de activación inmediata pendientes. Falta QA de puntero sobre el
  control visible.
- HECHO para consumo: GDX usa los valores predeterminados Swing y también sus
  personalizaciones persistidas (`shortcut.<id>`), traduce AWT→libGDX y exige
  modificadores exactos para no disparar acciones peligrosas por accidente.
- HECHO para edición: la pestaña GDX captura, valida, restaura, guarda y cancela
  transaccionalmente las 19 acciones aplicables sin colisiones.

### P0.6 — Finalización manual y estados de mesa

Estado: límite y controles manuales principales funcionales en core/GDX.

- HECHO: ÚLTIMA MANO manual es orden tipada del anfitrión y evento autoritativo.
- HECHO: DETENER TIMBA emite `SERVEREXITRECOVER` con la contraseña real.
- HECHO: salir, detener para recuperar y salida de un cliente tienen rutas
  distintas; el anfitrión valida el `EXIT` contra el nick autenticado y conserva
  las pruebas criptográficas necesarias.
- HECHO en GDX: al confirmar salir o detener, la mesa entra en un estado terminal
  visible y bloquea órdenes duplicadas hasta recibir el `CloseTable` real del
  dealer. No introduce temporizadores ni cierres locales que puedan adelantar la
  despedida de red o el settlement.
- HECHO: una confirmación terminal ya no puede duplicarse ni dejar otro modal
  encolado cubriendo el estado de salida. Si la tarea asíncrona de SALIR/DETENER
  falla, el error se entrega al mismo cierre fail-closed del crupier en lugar de
  perderse en el ejecutor y dejar GDX esperando indefinidamente. Pasan 3/3
  pruebas de cableado del modal y 12/12 pruebas dirigidas de contención,
  cancelación y handoff terminal.
- HECHO: la pantalla final GDX recibe el resumen real, reproduce el contador con
  el timing de Swing y muestra hasta nueve participantes de forma adaptable.
- HECHO en core: `CONTINUAR ESTA TIMBA` del anfitrión reabre la recuperación
  local real usando el formato compartido Swing/GDX y una integración completa
  termina la mano recuperada.
- PARCIAL en frontend: el cliente muestra `RECONECTAR AL SERVIDOR` y reutiliza
  nick, servidor, puerto, contraseña y avatar para una conexión JOIN real. Falta
  certificar visualmente ambos botones y la reconexión con dos procesos GDX.
- HECHO en frontend/transporte: el host dispone de `Forzar reconexión de
  jugadores`, con confirmación idempotente y una orden tipada que reemplaza los
  sockets humanos remotos sin destruir sus peers lógicos ni sus colas. La acción
  se deshabilita sin humanos conectados y está prohibida al cliente. Falta QA
  visual multiproceso del estado visible durante la reconexión.
- HECHO automático: un propietario de turno atrasado no reactiva ni ilumina a
  un jugador inactivo, espectador, salido, desconectado o ausente, y tampoco
  permite que GDX envíe una acción por teclado, ratón o confirmación AUTO.
  Queda QA visual multiproceso de los estados desconectado/reconectando.
- HECHO automático/en código: la aportación canónica completa de cada jugador
  se conserva entre calles y sólo se descuenta visualmente la parte que aún
  está volando. Falta QA visual de los contadores durante una mano completa.
- HECHO automático/en código: tras resolver el showdown se mantienen enfocadas
  sólo las cinco cartas de cada combinación ganadora y se atenúan perdedores y
  comunitarias no usadas, sin amarillo por defecto. Falta QA visual OpenGL.
- Reservar amarillo para resaltado interactivo.
- Certificar paleta Swing de action labels, ganador/perdedor, stack y bote.
- Impedir solapes entre comunitarias, HUD comunitario, cartas locales y HUD
  local.
- Hacer inequívoco el jugador que tiene el turno.

### P1.1 — Una única pantalla de Ajustes GDX

Estado: parcial; menú y mesa todavía tienen implementaciones de dibujo
separadas que deben converger en un componente/modelo común.

Audio pendiente o incompleto:

- Volumen maestro.
- Música y pistas aplicables.
- Efectos y categorías individuales.
- Sonidos de coña.
- Notas de voz.
- Dispositivo de salida y micrófono.
- Captura, reproducción propia y volumen de voz.
- Estado correcto del icono de sonido en todas las pantallas.

Apariencia aplicable a GDX:

- Baraja y trasera.
- Tapete y nivel de luz.
- Coste de igualar, reloj y resaltados.
- Imágenes del chat y captura final.
- HECHO en código: resaltado de avatares con hover, límite adaptativo y
  protección contra click-through; falta QA visual.
- Pantalla completa/borderless.
- VSync, monitor activo, antialiasing y calidad gráfica.

Juego durante la partida:

- Número máximo de manos.
- Nivel y estructura de ciegas.
- Aumento por tiempo/manos y tope.
- Ante y straddle.
- IWTSTH, Run It Twice y rabbit hunting.
- Tiempo de showdown y reglas editables permitidas.
- Rebuys, límites y bots cuando sean editables.
- Host editable, cliente sólo lectura.
- Guardado transaccional y propagación real por red.

Automatismos:

- Botones AUTO.
- Auto Call y máximo.
- Auto Mode y confirmación.
- Confirmar acciones.
- Recompra automática.
- Persistencia entre manos.

Otros:

- Atajos configurables.
- Idioma aplicado inmediatamente.
- Sin textos solapados, cortados o fuera de panel.

Exclusiones GDX acordadas: zoom Swing, vista compacta y desactivar todas las
animaciones.

### P1.2 — Menú contextual y barra rápida

- Reconstruir jerarquía y submenús completos.
- Estados seleccionados, permitidos y deshabilitados reales.
- Ajustes, Registro, visores, automatismos, confirmación, recompra, baraja,
  última mano, pausa, detener, salir y ayudas aplicables.
- Certificar barra rápida: chat, voz, imagen, recompra, registro y pantalla
  completa.
- HECHO: pausa, luces, salir, detener, registro, sonido/volumen y pantalla
  completa usan las combinaciones canónicas de Swing, y el editor GDX persiste
  y aplica sus reasignaciones sin permitir conflictos.

### P1.3 — Chat de lobby y de partida

Estado: transporte y consumidores parciales existentes; falta certificación
completa.

- Texto, historial y scroll.
- Los 1.826 emojis originales.
- Emoji flotante sobre el asiento emisor.
- Imágenes y GIF animados sobre el jugador correcto.
- Notas de voz WAV: grabar, cancelar, límite, enviar y reproducir.
- Icono de nuevo mensaje durante la mano.
- Preferencias y bloqueo de contenido.
- Errores y contenido inválido.
- Compatibilidad Swing/GDX con dos procesos.
- Selector de dispositivos de audio.
- HECHO en edición nativa: caret parpadeante, colocación con ratón,
  `Shift+clic`, selección por arrastre, Unicode, selección total y
  copiar/cortar/pegar. Los tokens visuales de emoji del lobby son atómicos.
- HECHO en medios: caché de memoria LRU y caché persistente compartida con
  Swing en `ChatImagesCache`, identidad MD5 compatible, límite de 16 MiB y
  escritura atómica. Falta la certificación visual y cruzada de todo el flujo.

### P1.4 — Interacciones y utilidades del tapete

- HECHO en código: doble clic izquierdo sobre una zona realmente vacía cambia
  el tapete en el mismo orden de Swing (verde, azul, rojo, negro, madera),
  persiste `color_tapete` y evita cartas, asientos, HUD, mesa central y barra
  rápida. Falta QA visual interactiva.
- HECHO: clic derecho sobre cualquier carta avanza la baraja activa sin abrir
  ni atravesar el menú contextual.
- HECHO: visor de cartas nativo por clic izquierdo, con la baraja activa y los
  recursos HQ/mod-aware, ajuste de aspecto sin reescalar por encima del original
  y cierre por clic o ESC.
- HECHO: captura GDX por el atajo canónico `Ctrl+P`, escritura asíncrona en la
  carpeta compartida `Screenshots` y visor nativo navegable, ordenado de más
  reciente a más antigua. Pendientes del visor: copiar/eliminar y QA visual.
- PARCIAL: el registro GDX ya tiene scroll, selección por líneas, copiar,
  seleccionar todo y menú contextual nativo. Falta igualar por completo la
  estructura, colores y contenido del registro Swing, incluidas acciones y
  showdown.
- Ayudas aplicables y generador de jugadas.

### P1.5 — Red humana y compatibilidad cruzada

Estado: matriz automatizada de juego, fallos y recuperación cerrada; queda QA
interactiva multiproceso y pulido de estados visibles.

- Pantalla funcional UNIRME A TIMBA.
- HECHO con mano real: GDX servidor con GDX y Swing clientes.
- HECHO con mano real: Swing servidor con Swing y GDX clientes.
- HECHO en ambas topologías: calles completas, consenso, saldos, pausa/reanudar
  y barrera visual ALL-IN.
- Contraseña, rechazo y errores de conexión.
- Salida, expulsión y cierre limpio.
- Desconexión y reconexión.
- Recuperación de partida.
- Rebuy, straddle y RIT en red mixta.
- Latencia, reconexiones e identidad visibles.

### P1.6 — Lobby completo

- Permisos y acciones reales de host/cliente.
- Añadir y eliminar bots/jugadores.
- Avatares e indicadores de latencia.
- Chat completo.
- Ajustes compartidos.
- Confirmación de inicio opaca y legible.
- Compra inicial variable.
- Estados de espera/conexión/arranque.
- Barra de carga con progreso real.
- Entrada a mesa sin congelación ni carga duplicada.
- NO PRIORITARIO: la animación de `PREPARANDO LA MESA` todavía puede quedar
  visiblemente parada justo antes de mostrar el tapete porque
  `CoronaPokerGdxTable.create()` crea shaders, texturas, fuentes, GIF y audio
  de forma síncrona en el hilo de render. Resolver más adelante mediante fases
  reales de inicialización GDX repartidas entre frames; no simular porcentajes
  ni introducir barreras nuevas en el core.

### P1.7 — Shell, inicio e idioma

- Splash oficial durante cualquier arranque negro evitable.
- Certificar intro y movimiento final del logo a la esquina.
- Música e icono de sonido.
- Selector de idioma con tamaño coherente.
- Traducción inmediata completa, sin textos técnicos de preview/sesión.
- Botones, iconos, hover, press, foco y disabled uniformes.

### P2.1 — Pantalla de fin de timba

- Clonar toda la información funcional de Swing.
- Ganancias/pérdidas, buy-in, rebuys, saldos y bote sobrante.
- Tratamiento configurado de los bots.
- Acceso a estadísticas, menú y salida.
- Animación GDX de calidad sin ocultar información.

### P2.2 — Estadísticas GDX

- Acceso desde menú principal y fin de timba.
- Tablas, filtros, gráficas e historial.
- Sincronización aplicable.
- Partidas recuperadas/importadas.
- Diseño legible en todas las resoluciones.

### P2.3 — Calidad, rendimiento y entrega

- Comparación completa contra la demo `627c71e4f`.
- Auditoría de textos, recortes y solapes.
- Ratios, DPI y escalado de Windows.
- 75 Hz y 240 Hz.
- Actualización del límite/VSync al mover la ventana de monitor.
- Verificación efectiva de `GL_SAMPLES` y opción gráfica aplicable.
- Carga asíncrona y eliminación de recursos no utilizados.
- Goliat oficial; ningún mod externo dentro del JAR.
- Un solo JAR GDX identificable y directorios de salida claros.
- Suite de escenarios `balanced` completa sin alterar sus fuentes.
- Certificación final Swing y GDX.

### P3 — Suite de escenarios GDX homóloga

Estado: operativa para lógica, red y proyección headless; ampliación activa en
paralelo con la estabilización P0. La certificación OpenGL/visual real continúa
separada y pendiente. No se considerará completa la suite GDX sólo porque el
catálogo lógico esté 37/37: cada escenario con una decisión, diálogo o control
capaz de bloquear el crupier necesita además atravesar el cableado de producción
GDX correspondiente, como ya hace `GAME OVER -> ESPECTADOR`.

- El catálogo lógico estricto ya conserva los 37 escenarios Swing mediante 48
  pruebas GDX y tiene perfiles fast/balanced/stress con aislamiento por proceso.
- La matriz adicional de 30 escenarios mixtos cubre host y cliente Swing/GDX;
  su runner aislado evita que un bloqueo o fuga quede oculto por otra prueba.
- Reutilizar los mismos escenarios y expectativas de negocio mediante el core
  compartido; no copiar ni reinterpretar reglas en el renderer.
- Ejecutar el frontend GDX oculto/headless cuando sea viable y separar las
  comprobaciones de estado/eventos/barreras de las pocas verificaciones que
  requieran OpenGL real.
- Certificar barreras causales de reparto, destape, fichas, pagos, ALL-IN, RIT,
  recompra, pausa, salida y final de timba.
- Mantener intacta la suite Swing: ambas suites deben poder detectar de forma
  independiente cualquier regresión de su frontend sobre el mismo core.

## Siguiente trabajo exacto

Prioridad vigente acordada el 2026-09-07: antes de pulir Ajustes, demostrar una
partida de red completa con humanos y combinaciones mixtas de frontend. El
renderer no puede alterar reglas, barreras, locks, protocolo ni criptografía.

1. CERRADO: partida completa y saldo consensuado con host Swing + clientes
   Swing/GDX y con host GDX + clientes Swing/GDX; pausa/reanudación y ALL-IN
   causal incluidos.
2. CERRADO automáticamente: timeout, cierre/salida (también estando pausado),
   straddle, RIT, recompra, desconexión/reconexión y recuperación; 54/54 en la
   certificación agregada. Queda QA visual multiproceso, no una carencia de core
   conocida.
3. CERRADO para los defectos encontrados: se corrigieron sólo dos transiciones
   del controlador neutral GDX y Swing queda protegido actualmente con
   1.120/1.120 FAST.
4. EN CURSO: cerrar la superficie funcional normal de mesa. El menú contextual
   ya reproduce las acciones de Swing y su submenú Ayuda; falta la QA visual y
   revisar los últimos consumidores de una partida humana ordinaria.
5. Después cerrar Ajustes GDX como superficie única de menú y mesa, con
   Apariencia, Audio, Atajos, Debug y Juego completamente cableados.
6. En paralelo, reforzar los escenarios GDX cuando aparezca una regresión P0:
   la prueba debe recorrer el consumidor de producto responsable, no resolver
   directamente el modelo ni limitarse a comprobar el core.
7. Después continuar chat/medios, pantalla final, registro/navegación, lobby,
   estadísticas, rendimiento y certificación final FAST/BALANCED.

## Protocolo de actualización

Al cerrar cada bloque se debe registrar aquí:

- Qué consumidor real se añadió o corrigió.
- Qué prueba automática pasó y su alcance exacto.
- Qué QA visual sigue pendiente.
- Qué artefacto/JAR contiene el cambio.
- Cuál es el siguiente bloqueo concreto.
