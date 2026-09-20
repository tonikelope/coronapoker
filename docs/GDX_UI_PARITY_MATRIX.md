# Matriz de paridad de interfaz Swing / GDX

Esta matriz impide declarar completa una pantalla GDX por el mero hecho de que
exista visualmente. La fuente funcional es Swing; el estilo de navegación GDX
puede reorganizar esa funcionalidad, pero no añadirla, omitirla ni duplicar sus
reglas.

## Contrato visual GDX aprobado

El 2 de septiembre de 2026 se aprobó la pantalla interactiva de referencia de
`Nueva timba`. Este lenguaje visual es obligatorio para todas las superficies
GDX ajenas a la mesa, sin alterar los nombres, opciones ni semántica de Swing:

- `images/tapete_verde.jpg` cubre toda la ventana, también bajo cabecera, menú
  lateral y pie, sin bandas opacas.
- Paneles y controles de cristal oscuro realmente translúcido, sin barras de
  sombra ni artefactos circulares en las esquinas.
- Paleta de la demo canónica: panel `#101A2E`, cian `#36D9FF`, naranja
  `#FF6B27`, dorado `#FFE07A` y líneas `#31445F`; sólo puede variar el alfa
  necesario para el efecto de cristal.
- Montserrat Bold/SemiBold en títulos y acciones; Inter Medium en texto de
  lectura. El estado de un control nunca cambia de tipografía ni desplaza el
  texto.
- Iconos vectoriales nítidos, textos siempre dentro de sus límites, respuesta
  visual de hover/pulsación, interruptores animados y activación al soltar el
  botón del ratón.

La aprobación es exclusivamente visual. La preview aislada no cuenta como
`conectado`: cada fila permanece `pendiente` hasta usar el core real y superar
las pruebas correspondientes.

Estados permitidos:

- `pendiente`: no existe contraparte GDX funcional.
- `conectado`: el control GDX lee y escribe un modelo neutral del core y ejecuta
  la misma operación que Swing.
- `prueba automatizada`: además de conectado, están verificadas sus reglas,
  límites y transiciones relevantes.
- `validación visual`: además de lo anterior, se ha revisado manualmente en la
  aplicación GDX real, incluidas resoluciones y textos.

Ningún control pasa a `conectado` si sólo modifica estado local de la pantalla.
Una reorganización en pestañas o pasos no crea funcionalidad nueva: todas las
filas siguientes deben seguir siendo accesibles y conservar su semántica.

## Inventario global de superficies Swing

| Superficie Swing | Contraparte GDX | Estado | Observaciones |
|---|---|---|---|
| `Init` | Arranque y menú principal | pendiente | Crear, unirse, recuperar, ajustes, acerca de y salir dentro de la única ventana GDX. |
| `NewGameDialog` — crear | Pantalla Crear timba | pendiente | Inventario detallado más abajo. |
| `NewGameDialog` — unirse | Pantalla Unirse a timba | pendiente | Comparte identidad/red; no debe mostrar configuración exclusiva del host. |
| `NewGameDialog` — recuperar | Flujo Recuperar timba | pendiente | Carga asíncrona, economía bloqueada y opciones permitidas según el flujo Swing. |
| `BlindStructureManagerDialog` | Gestión de estructuras | pendiente | Overlay o pantalla GDX dentro de la misma ventana. |
| `WaitingRoomFrame` | Sala de espera a pantalla completa | pendiente | Superficie GDX y contrato neutral creados; falta conectar el adaptador real de red, chat binario y transición a mesa. |
| `GameFrame` | Mesa canónica GDX | pendiente | Hay familias causales ya conectadas, pero la superficie completa aún no cumple paridad. |
| `SettingsDialog` | Ajustes | pendiente | Pantalla GDX navegable; inventario detallado aún pendiente. |
| `AboutDialog` | Acerca de | pendiente | Overlay/pantalla GDX. |
| `AutoCallMaxDialog` | Límite de auto-call | pendiente | Overlay GDX con resultado asíncrono. |
| `CardVisorDialog` | Visor de cartas | pendiente | Overlay GDX. |
| `ChatImageDialog` | Imagen de chat | pendiente | Overlay GDX. |
| `ExitDialog` | Confirmación de salida | pendiente | Overlay GDX. |
| `FastChatDialog` | Chat rápido | pendiente | Overlay GDX. |
| `GameLogDialog` | Registro de partida | pendiente | Pantalla/overlay GDX. |
| `GameOverDialog` | Fin de partida | pendiente | Pantalla/overlay GDX. |
| `GifAnimationDialog` | Animación GIF | pendiente | Overlay GDX. |
| `HandGeneratorDialog` | Generador de manos | pendiente | Pantalla/overlay GDX. |
| `IdenticonDialog` | Identicon de identidad/sesión | parcial nativo | Clic derecho en participante muestra ambas huellas; falta persistir el marcado OOB verificado. |
| `SessionIdenticonMosaicDialog` | Mosaico de identicons | parcial nativo | Cada canal se puede inspeccionar por participante; falta la vista simultánea del host. |
| `InGameNotifyDialog` | Notificación en partida | pendiente | Overlay GDX no bloqueante según el comportamiento real. |
| `PauseDialog` | Pausa | pendiente | Overlay GDX. |
| `RecoverDialog` | Recuperación | pendiente | Overlay GDX. |
| `Reconnect2ServerDialog` | Reconexión | pendiente | Overlay GDX y ciclo asíncrono real. |
| `RebuyDialog` | Recompra | pendiente | Overlay GDX conectado a las reglas reales de recompra. |
| `RunItTwiceDialog` | Run It Twice | pendiente | Overlay GDX conectado al protocolo real. |
| `ScreenshotViewerDialog` | Visor de captura | pendiente | Overlay GDX. |
| `ShortcutsDialog` | Atajos | pendiente | Pantalla/overlay GDX. |
| `StatsDialog` | Estadísticas | pendiente | Pantalla GDX; carga y consultas fuera del hilo de render. |
| `VoiceNotesViewerDialog` | Notas de voz | pendiente | Pantalla/overlay GDX. |
| `VolumeControlDialog` | Volumen | pendiente | Overlay GDX. |

El inventario global enumera las ventanas Swing existentes. Cada una deberá
desglosarse por control y acción antes de implementarse en GDX, igual que se
hace a continuación con `NewGameDialog`.

## `WaitingRoomFrame`: sala, participantes y sesión

| Control/estado Swing | Contrato funcional que debe conservar GDX | Estado |
|---|---|---|
| `status` / `barra` | Conectando, intercambio de claves, recepción de información, conectado, espera, reconexión, error e inicialización sin bloquear el render. | pendiente |
| `conectados` / `tot_conectados` | Lista ordenada de 1–10 participantes con avatar, latencias, bot, espera asíncrona y aviso de canal inseguro. | pendiente |
| clic derecho en participantes | Identicon de identidad y canal cifrado por participante; mosaico completo para host aún pendiente. | parcial nativo |
| `new_bot_button` | Sólo host; añade `CoronaBot$N`, vuelve a comprobar aforo bajo el mismo turno de admisión y difunde el alta. | pendiente |
| `kick_user` | Sólo host y participante remoto seleccionado; expulsa, rota contraseña cuando corresponde y actualiza el resto de canales. | pendiente |
| `empezar_timba` | Sólo host, mínimo dos participantes, confirmación previa, protección contra doble inicio, recuperación de ausentes y transición asíncrona a mesa. | pendiente |
| `server_address_label` | Muestra la dirección y permite copiarla al portapapeles. | pendiente |
| `pass_icon` | Copiar, cambiar, generar o eliminar la contraseña; difundirla ordenadamente a quienes permanecen. | pendiente |
| `game_info_buyin` | Buy-in fijo o variable real; ayuda contextual sin permitir al cliente modificarlo. | pendiente |
| `game_info_blinds` | Ciegas e intervalo de subida reales. | pendiente |
| `game_info_hands` | Límite de manos cuando existe. | pendiente |
| `game_info_recover` | Indica explícitamente que se continúa una timba anterior. | pendiente |
| `settings_icon` | Abre Ajustes dentro de la misma ventana GDX. | pendiente |
| `sound_icon` | Alterna sonido y persiste la preferencia compartida. | pendiente |
| cierre de sala | Confirmación, salida de red ordenada y retorno al menú sin terminar por la fuerza el proceso. | pendiente |

## `WaitingRoomFrame`: chat

| Control/acción Swing | Contrato funcional que debe conservar GDX | Estado |
|---|---|---|
| historial `chat` | Orden causal de mensajes, altas, bajas, texto, enlaces, BBCode, emoji, imágenes/GIF y notas de voz. | pendiente |
| `chat_box` / `send_label` | Envío con Enter o botón, texto vacío rechazado y anti-flood de 500 ms. | pendiente |
| `emoji_button` / `emoji_panel` | Selector e historial de emoji sin perder el foco de escritura. | pendiente |
| `image_button` / `ChatImageDialog` | Envío por URL, búsqueda, historial, recepción automática y anti-flood existentes dentro de GDX. | pendiente |
| notas de voz | Validación WAV, máximo real de 320 KiB, transporte binario y reproducción desde el historial. | pendiente |
| `chat_notifications` | Activa y persiste las notificaciones durante la partida. | pendiente |
| `tts_warning` | Conserva el aviso de privacidad y su detalle. | pendiente |
| `max_min_label` | Maximiza/minimiza el área de chat sin perder estado ni scroll. | pendiente |

## `NewGameDialog`: identidad, conexión y acciones

| Control/acción Swing | Modos | Contrato funcional que debe conservar GDX | Estado |
|---|---|---|---|
| `avatar_label` / `nick_label` | crear, unirse, recuperar | Clic abre selector de imagen; clic derecho restaura avatar; archivo legible y máximo `256 KB`; avatar predeterminado si no es válido. | pendiente |
| `nick` | crear, unirse, recuperar | Máximo 15 caracteres; persistencia; no vacío para continuar; `$` reservado y eliminado en el flujo humano. | pendiente |
| `pass_text` | crear, unirse, recuperar | Máximo 30 caracteres; revelado y ayuda de fortaleza; la etiqueta/acción de contraseña sólo se habilita con contenido. | pendiente |
| `server_ip_textfield` | crear, unirse, recuperar | Obligatorio; en crear usa la IP local y no es editable; en unirse usa historial/configuración persistida. | pendiente |
| `server_port_textfield` | crear, unirse, recuperar | Obligatorio, sólo numérico, máximo 5 dígitos, valor predeterminado `7234`. | pendiente |
| historial de servidor | unirse | Conserva la navegación por servidores usados y su persistencia. | pendiente |
| `upnp_checkbox` | crear | Preferencia persistida del host; oculto al unirse. | pendiente |
| `vamos` | crear, unirse, recuperar | Sólo habilitado con nick, servidor y puerto; valida y ejecuta la operación real una sola vez. | pendiente |
| `cancel_button` | crear, unirse, recuperar | Cancela sin aplicar configuración staged ni modificar la sesión. | pendiente |

## `NewGameDialog`: presets del host

| Control Swing | Contrato funcional que debe conservar GDX | Estado |
|---|---|---|
| `presets_combobox` | Sólo crear; `Default` más presets guardados; aplicar un preset sólo modifica el estado staged de la pantalla. | pendiente |
| `preset_save_button` | Solicita nombre, lo recorta al máximo real y guarda la instantánea completa de `GamePreset.Settings`. | pendiente |
| `preset_delete_button` | Sólo habilitado para un preset guardado; elimina el seleccionado. | pendiente |

## `NewGameDialog`: ciegas y estructura

| Control Swing | Contrato funcional que debe conservar GDX | Estado |
|---|---|---|
| `estructura_combobox` | Selecciona estructura predeterminada o guardada y repuebla todos sus niveles. | pendiente |
| `ciegas_combobox` | Selecciona el nivel SB/BB real; recalcula buy-in, pasos, topes y etiquetas dependientes. | pendiente |
| `doblar_checkbox` | Activa el incremento de ciegas y habilita tipo, intervalo y tope. | pendiente |
| `double_blinds_radio_minutos` | Incremento por minutos; excluyente con manos. | pendiente |
| `doblar_ciegas_spinner_minutos` | Intervalo positivo en minutos; sólo habilitado con incremento y tipo minutos. | pendiente |
| `double_blinds_radio_manos` | Incremento por manos; excluyente con minutos. | pendiente |
| `doblar_ciegas_spinner_manos` | Intervalo positivo en manos; sólo habilitado con incremento y tipo manos. | pendiente |
| `blind_cap_checkbox` | Activa el tope sólo cuando existe incremento de ciegas. | pendiente |
| `blind_cap_spinner` | Expresa el número de subidas y deriva el tope monetario desde la ciega actual. | pendiente |

## `NewGameDialog`: buy-in y recompra

| Control Swing | Contrato funcional que debe conservar GDX | Estado |
|---|---|---|---|
| `fixed_buyin_checkbox` | Alterna buy-in fijo frente a rango permitido; habilita el valor fijo. | pendiente |
| `buyin_spinner` | Valor entero, límites y paso derivados de la ciega y de `BuyinRules`. | pendiente |
| `buyin_min_bb_spinner` | Mínimo en BB; forma un rango válido con el máximo y reconstruye el modelo de buy-in. | pendiente |
| `buyin_max_bb_spinner` | Máximo en BB; forma un rango válido con el mínimo y reconstruye el modelo de buy-in. | pendiente |
| `rebuy_checkbox` | Activa recompra y gobierna límite, recompra de bots y política de tope. | pendiente |
| `rebuy_limit_checkbox` | Activa un número máximo de recompras sólo si recompra está activa. | pendiente |
| `rebuy_limit_spinner` | Límite positivo; sólo habilitado con ambas opciones anteriores. | pendiente |
| `bot_rebuy_checkbox` | Permite recompra de bots; sólo habilitado con recompra. | pendiente |
| `bot_balance_checkbox` | Al terminar, reparte entre humanos el balance conjunto de bots según la regla existente. | pendiente |
| `rebuy_cap_combo` | Política real: buy-in o stack más alto; sólo habilitada con recompra. | pendiente |

## `NewGameDialog`: reglas y ritmo

| Control Swing | Contrato funcional que debe conservar GDX | Estado |
|---|---|---|---|
| `manos_checkbox` | Activa límite de manos. | pendiente |
| `manos_spinner` | Límite positivo; deshabilitado si no se activa. | pendiente |
| `think_time_checkbox` | Activa o elimina el límite para pensar. | pendiente |
| `think_time_spinner` | Segundos dentro de `GameFrame.THINK_TIME_MIN..MAX`; depende del toggle. | pendiente |
| `showdown_time_spinner` | Pausa de showdown dentro de `GameFrame.SHOWDOWN_TIME_MIN..MAX`; siempre activa. | pendiente |
| `ante_checkbox` | Activa ante y conserva su semántica/etiqueta dependiente del nivel de ciegas. | pendiente |
| `straddle_checkbox` | Activa straddle y conserva su semántica/etiqueta dependiente del nivel de ciegas. | pendiente |
| `iwtsth_checkbox` | Activa la regla IWTSTH. | pendiente |
| `rit_checkbox` | Activa Run It Twice para all-in. | pendiente |
| `rabbit_combo` | Índices reales: desactivado, gratis, gratis+SB, gratis+SB+BB. | pendiente |

## `NewGameDialog`: bots

| Control Swing | Modos | Contrato funcional que debe conservar GDX | Estado |
|---|---|---|---|
| `bots_combobox` | crear, recuperar | Dificultad exacta `EASY`, `MEDIUM` o `HARD`; panel oculto al unirse. | pendiente |

## `NewGameDialog`: recuperación

| Control/estado Swing | Contrato funcional que debe conservar GDX | Estado |
|---|---|---|
| `recover_checkbox` | Sólo host; carga la última partida local recuperable sin bloquear la UI y evita cargas concurrentes. | pendiente |
| estado `recover_loading` | Deshabilita continuar durante la carga y restaura su estado al finalizar o si no se puede programar el trabajo. | pendiente |
| `last_game_key` / `game_label` | Identifica y muestra exactamente la partida recuperable cargada. | pendiente |
| economía recuperada | Buy-in y ciegas recuperados quedan bloqueados como en Swing; sólo permanecen editables las opciones permitidas. | pendiente |
| `force_recover` | Conserva el flujo de recuperación forzada y su orden de inicialización. | pendiente |

## Reglas de aceptación de la pantalla GDX

1. Los grupos anteriores pueden distribuirse en pasos o pestañas, pero no
   cambiar de significado ni desaparecer.
2. Crear, unirse y recuperar son variantes explícitas del mismo flujo; la
   visibilidad y editabilidad se derivan del modelo, no de widgets ocultos.
3. El formulario devuelve modelos tipados y staged. Cancelar no produce efectos.
4. Validación, presets, persistencia, base de datos, red e identidad se ejecutan
   mediante servicios neutrales; el hilo de render GDX nunca espera E/S.
5. Tooltips y ayudas de Swing se transforman en ayuda contextual GDX, no se
   eliminan.
6. La validación visual incluye como mínimo 1280x720, 1920x1080 y 2560x1440,
   textos largos traducidos, navegación completa por teclado y ausencia de
   recortes o desbordamientos.
