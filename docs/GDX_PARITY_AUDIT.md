# Auditoría viva de paridad Swing/GDX

El estado ordenado y el punto exacto de reanudación se mantienen en
`GDX_MIGRATION_EXECUTION_STATUS.md`.

Fecha de corte: 2026-09-06. Esta lista complementa
`CORONAPOKER_GDX_FULL_MIGRATION_PLAN.md`: no sustituye los criterios del plan ni
convierte una opción visible en una opción funcional.

## Regla de estado

- `HECHO`: existe consumidor real y prueba automática o comprobación indicada.
- `PARCIAL`: existe una parte real, pero falta flujo, estado, red o QA visual.
- `PENDIENTE`: no debe presentarse como operativo.
- Los escenarios Swing no se modifican para hacer pasar GDX.

## P0 — mesa y flujo de una mano

- PARCIAL: estado/eventos/comandos tipados, acciones básicas, calles, showdown,
  animaciones de reparto/fichas/pago, tiempo común y coste de igualar. La
  pausa visual existe y su coordinación host/cliente ya usa el protocolo
  autoritativo autenticado; una prueba real pausa y reanuda ambos extremos en
  pleno turno y después completa dos manos. Falta QA visual interactiva del
  overlay, luces, permisos y contador de pausas.
- PARCIAL: rebuy manual/por bancarrota; el consumo del rebuy comprometido está
  corregido y la recompra inmediata de la barra rápida ya delega rango, límite,
  cancelación y aplicación al Crupier. Una prueba de dos mesas humanas fuerza
  una bancarrota, acepta la recompra y completa la mano siguiente con saldos
  coincidentes. La recompra automática ya es estado vivo de sesión, sólo se
  habilita si la timba permite rebuys y el Crupier la consume dinámicamente.
  La integración de dos mesas fuerza esta ruta, comprueba que omite la elección
  de game-over y completa la mano siguiente; falta QA visual GDX.
- PARCIAL: straddle voluntario tiene decisión GDX y lógica core. Una integración
  con anfitrión y dos clientes humanos completa una mano con la decisión firmada
  real; falta configuración en vivo, recuperación y QA de presentación.
- PARCIAL: botones AUTO fuera de turno, selección persistente, Auto Call con
  límite/sin límite y veto de Auto Mode con cuenta atrás ya tienen señal core,
  consumidor GDX y pruebas. Auto Call dispone de diálogo nativo; falta la
  matriz E2E completa y la certificación visual.
- PARCIAL: última mano, detener timba y límite de manos. El límite y la orden
  manual generan `LASTHAND` autoritativo y cierre natural. Salir, detener para
  recuperar con contraseña y la salida controlada de un cliente están cableados
  y probados en red. La pantalla final GDX ya consume el resumen real y el host
  puede solicitar la recuperación real de la última timba persistida en formato
  compatible con Swing. Una integración ya detiene, carga y completa una mano
  recuperada real con bot. El cliente dispone de la acción JOIN de reconexión
  conservando sus campos; faltan QA visual y certificación con dos procesos.
- HECHO: dos mesas core/GDX con jugadores humanos reales completan dos manos
  autenticadas consecutivas sobre el mismo canal, validan showdown, firmas,
  consenso y saldos, y reciben cierre natural en ambos extremos. La misma clase
  certifica pausa/reanudación, barrera ALL-IN, recompra automática, straddle con
  tres humanos, última mano y las tres rutas de cierre. RIT publica ambos
  tableros, conserva el flop compartido y vuelve a repartir turn/river B sobre
  la red real; el escenario Swing `allin-rit` también pasa sin tocarlo.
- PARCIAL: el all-in local y remoto inicia la cinemática GDX y el crupier ya
  impide que cualquier asiento humano o bot reciba turno hasta que finaliza su
  barrera causal. La integración de dos mesas humanas retiene expresamente el
  primer GIF y verifica que ningún control de turno lo adelante en ninguno de
  los extremos. Falta validar visualmente cada GIF/sonido contra la demo.

## P1 — controles durante la timba

- PARCIAL: menú contextual GDX; Ajustes, Registro, Confirmar, baraja, pausa y
  salida tienen alguna operación. Faltan jerarquía, estados y consumidores.
- PARCIAL: barra rápida inferior izquierda. Chat, voz, imagen, registro y
  pantalla completa están cableados; recompra inmediata también tiene consumidor
  real del Crupier. Falta certificarla E2E y completar sus estados visuales.
- PARCIAL: chat rápido usa la sesión de red real durante la mesa, representa los
  1.826 emojis originales, envía/recibe imagen o GIF sobre el asiento emisor y
  graba/reproduce notas de voz WAV compatibles con Swing (botón/F9, 15 s y
  cancelación). Faltan selector de dispositivo, QA visual/E2E y completar el
  chat del lobby.
- PARCIAL: el registro GDX tiene scroll, selección por líneas, copiar,
  seleccionar todo y menú contextual; faltan contenido, estructura y colores
  totalmente equivalentes a Swing. Visores de capturas y cartas pendientes.
- PARCIAL: clic derecho sobre cartas ya cambia la baraja sin interferir con el
  menú contextual. El doble clic en tapete vacío recorre y persiste los cinco
  tapetes en el orden Swing, protegiendo todas las zonas ocupadas; falta QA
  visual interactiva de ambos gestos.
- PENDIENTE: ayuda aplicable (atajos, reglas de Robert, generador de jugadas).

## P1 — Ajustes GDX aplicables

- PARCIAL: una misma identidad visual desde menú, lobby y mesa; todavía hay dos
  implementaciones de dibujo que deben converger en un componente/modelo común.
- PARCIAL: audio maestro, música, efectos, sonidos de coña, baraja, coste de
  igualar, pantalla completa automática y confirmar acciones.
- PARCIAL: voz de mesa respeta sonido maestro, bloqueo local y reproducir la
  propia nota; faltan selector de entrada/salida, volumen y galería GDX.
- PARCIAL: Auto Call, Auto Mode, persistencia AUTO y recompra automática tienen
  estado y consumidores reales. Auto Call ya dispone de editor GDX nativo para
  activar/desactivar, elegir sin límite o ajustar el máximo en pasos de 0,05;
  faltan pruebas E2E de las variantes y certificación visual.
- PENDIENTE: reglas editables según permisos: manos, tiempo de pensar, showdown,
  IWTSTH, RIT, rabbit, estructura/ciegas, aumentos, tope, ante, straddle, bots,
  buy-in y rebuys.
- PARCIAL: selector de idioma realmente aplicado.
- HECHO: la mesa GDX consume el formato persistido por Swing, traduce los
  keycodes AWT a libGDX, conserva alias fijos y exige modificadores exactos.
  La pestaña Atajos edita las 19 acciones con consumidor GDX, pagina, captura,
  rechaza colisiones y permite restaurar, guardar o cancelar de forma
  transaccional. Zoom y vista compacta continúan excluidos por diseño GDX.
- EXCLUIDO POR DISEÑO GDX: Zoom, Vista compacta y desactivar animaciones. El
  viewport escala la mesa y las animaciones del contrato visual permanecen
  activas; sí se admiten parámetros de calidad/rendimiento que no rompan ese
  contrato si resultan necesarios.

## P1 — shell, menú y lobby

- PARCIAL: arranque, intro, menú, nueva timba, lobby, bots y entrada a mesa.
- PENDIENTE: unirse a servidor humano y certificar servidor/cliente cruzado
  Swing↔GDX, errores, salida, desconexión, reconexión y recuperación.
- PARCIAL: el chat de lobby ya envía texto, los 1.826 emojis originales,
  imagen/GIF por URL y notas de voz de hasta 15 s por la `LobbySession` real;
  reproduce voz y anima miniaturas GIF recibidas. El transporte cliente-servidor
  de texto, imagen y WAV está probado; faltan scroll/historial completo,
  dispositivos de audio y certificación visual con dos procesos.
- PARCIAL: campos GDX con caret, selección con teclado/ratón, Unicode,
  copiar/cortar/pegar y menú contextual. Los emojis del compositor de lobby se
  dibujan como imágenes atómicas. El registro de sólo lectura permite seleccionar
  y copiar; falta extender el mismo contrato a todos los campos auxiliares.
- PARCIAL: imágenes/GIF usan caché persistente compatible con Swing
  (`ChatImagesCache`, MD5, 16 MiB, escritura atómica) más LRU de memoria.
- PENDIENTE: idioma inmediato en todas las pantallas sin textos inventados.
- PENDIENTE: todos los estados/acciones host y cliente del lobby y sus permisos.

## P2 — cierre, estadísticas y contenido auxiliar

- PARCIAL: pantalla final GDX con fecha, duración, manos, resultado global,
  tarjetas de participantes y contador de ganancias/pérdidas con el timing de
  Swing. Menú, continuación local del anfitrión y reconexión JOIN del cliente
  están cableados; faltan QA visual final, Registro y Estadísticas.
- PENDIENTE: Estadísticas GDX reutilizada desde menú principal y fin de timba,
  incluidas gráficas y sincronización aplicable.
- PENDIENTE: Acerca de, ayudas, capturas y pantallas auxiliares restantes.

## P2 — recursos, calidad y entrega

- PARCIAL: recursos oficiales compartidos; Goliat es la baraja oficial por
  defecto. Los mods externos no deben incrustarse en el JAR oficial.
- PENDIENTE: auditoría de todos los textos a resoluciones/ratios admitidos,
  estados normal/hover/press/disabled y ausencia de solapes o recortes.
- PENDIENTE: carga asíncrona/progresiva para eliminar congelaciones entre lobby
  y mesa y evitar cargar la mesa completa dos veces durante el arranque.
- PENDIENTE: certificación visual contra la demo `627c71e4f`, audio/tiempo,
  75/240 Hz, cambio de monitor y empaquetado final Swing/GDX.
- PENDIENTE: suite de escenarios `balanced` al cierre, sin alterar sus fuentes.
