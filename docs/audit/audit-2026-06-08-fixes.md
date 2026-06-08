# CoronaPoker — Informe de cambios de la auditoría 2026-06-08

Aplicación de los hallazgos de [`audit-2026-06-08.md`](audit-2026-06-08.md). **17 fixes**,
cada uno en su **rama local independiente desde `master` limpio**, un commit, compilando.
Ninguno pusheado ni mergeado a `master` (a la espera de tu OK + decisión de release).

Disciplina seguida: pies de plomo, un fix por rama, compilación (`mvn -o compile`) tras cada
uno, y auto-auditoría del diff + grep de callsites antes de cerrar cada commit. Después,
**reauditoría adversaria del conjunto combinado** (rama de integración) que cazó y corrigió
una regresión P0 antes de que llegara aquí (ver §Reauditoría).

---

## Resumen

| Sev | Fix | Rama | Commit | Ficheros |
|-----|-----|------|--------|----------|
| P2 | Cliente: blindar lector de control-frames (zombie) | `fix-client-control-frame-zombie` | `352e0b13` | WaitingRoomFrame |
| P2 | Cliente: un frame in-game malformado no tumba la sesión | `fix-client-consumer-frame-guards` | `c97abaf0` | WaitingRoomFrame |
| P2 | `cliente_last_received` → ConcurrentHashMap | `fix-netclient-last-received-chm` | `41f31155` | NetClient |
| P2 | `VOICE_RECORDING` booleano → contador AtomicInteger | `fix-voice-recording-counter` | `0ee6db2c` | Audio |
| P2 | Relay de notas de voz fuera del hilo lector | `fix-voice-relay-async` | `74cc5db5` | WaitingRoomFrame |
| P2 | Purga de `VOICE_DIR` por antigüedad al arrancar | `fix-voice-dir-purge` | `9ea7bb9f` | Helpers |
| P3 | `CoronaMP3FilePlayer.stop()` drain → flush | `fix-mp3player-stop-flush` | `24c6f406` | CoronaMP3FilePlayer |
| P3 | `setVolume` acota gain también al máximo | `fix-mp3player-setvolume-clamp` | `a62de3cc` | CoronaMP3FilePlayer |
| P3 | Sufijo aleatorio en nombre de nota de voz | `fix-voicenote-filename-unique` | `608ed551` | WaitingRoomFrame |
| P3 | CHAT/VOICEMSG anclados al nick autenticado | `fix-voicenote-nick-anchor` | `7b0613cc` | Participant |
| P3 | Limpiar estado si el micro abre pero no da datos | `fix-voicerecorder-dead-mic-cleanup` | `cc197b28` | VoiceRecorder, VoiceMessageManager |
| P3 | `TTS_SERVER_RECOVER` en snapshot de RESET_GAME | `fix-tts-server-recover-symmetry` | `09e7f95a` | GameFrame |
| P3 | `castVote` atómico (solo gana el primer voto) | `fix-rit-castvote-atomic` | `13ee9e4c` | RunItTwiceDialog |
| P3 | Bot: reusar el sizing evaluado al ejecutar | `fix-bot-betsize-cache` | `12d30488` | Bot |
| P3 | Guard de longitud en payload de handshake | `fix-handshake-partes-guard` | `0cf70bc9` | WaitingRoomFrame |
| P3 | `deleteOnExit` en avatares temporales | `fix-avatar-tmp-cleanup` | `103dd25d` | WaitingRoomFrame, Participant |
| P3 | Persistir la nota antes de publicar el ancla | `fix-playfromchat-sync-write` | `24df5441` | WaitingRoomFrame |

**Diferido a propósito:** **A10** (sacar la descarga HTTP del TTS fuera de `TTS_LOCK`). Es un
refactor del camino TTS (sensible) para un P3 de latencia borderline-by-design; riesgo >
beneficio. Confirmado en la reauditoría que la descarga sigue dentro de `synchronized(TTS_LOCK)`,
sin tocar.

---

## Detalle por fix

### P2 — `fix-client-control-frame-zombie` (N1)
**Qué:** el lector del cliente (`runSocketReaderClientThread`) parseaba `PING/PONG/PONG2` con
`partes_comando[1]` sin guard de longitud ni try-catch, y el switch estaba fuera del único
`try`. Un frame de control malformado mataba el hilo lector → cliente zombie (consumer colgado
en `take()`, sin reconexión). **Cambio:** guard `length>=2` + try-catch por frame en los tres
cases, dejando el `put()` del PING fuera del guard. Espejo literal del blindaje que el servidor
(`Participant.runSocketReaderThread`) ya tenía desde la auditoría anterior.

### P2 — `fix-client-consumer-frame-guards` (N2)
**Qué:** el `do-while` del consumer del cliente tenía su único `try/catch` FUERA del bucle;
una excepción al parsear un frame (`GAME` con <3 segmentos, `CHAT`/`CONF`/`PING` sin campos)
caía en el catch externo y, con la partida empezada, ejecutaba `finTransmision(false)` → tiraba
la sesión por un solo frame. **Cambio:** `try/catch` por iteración que envuelve el `switch`,
loguea y descarta el frame corrupto y sigue, como el host ya hace en `recibirRebuys`. El
`take()` queda fuera del try (los fallos reales de cola siguen yendo al teardown). El cuerpo
del switch se deja a su indentación original (diff mínimo y merge-safe; reformateable aparte).

### P2 — `fix-netclient-last-received-chm` (N3)
**Qué:** `cliente_last_received` era un `HashMap` llano escrito/leído por el consumer y
`clear()`-eado por el reader (dos hilos), corrompible en un resize. **Cambio:** a
`ConcurrentHashMap` (getter devuelve `Map`). Sin null-key/value (id es `int`, get protegido por
short-circuit). Mismo patrón que el fix de `auditor`.

### P2 — `fix-voice-recording-counter` (A2)
**Qué:** `Audio.VOICE_RECORDING` (booleano global) se clobbeaba en re-grabación rápida
(doble-tap F9): el `stop()` de la nota A bajaba el flag bajo los pies de la nota B → B grababa
con música/efectos audibles. **Cambio:** respaldar el flag con un `AtomicInteger` contador
(true=+1, false=−1 con clamp a 0); `VOICE_RECORDING = count>0`. Los 5 lectores no cambian.

### P2 — `fix-voice-relay-async` (A8)
**Qué:** el host relayaba cada nota de voz (~427 KB cifrados/peer) síncronamente en el hilo
lector del peer emisor → head-of-line de sus comandos de juego si algún destinatario iba lento
o reconectando. **Cambio:** envolver el relay en `Helpers.threadRun` tras el snapshot, como ya
hacen `enviarNotaVoz`/`enviarMensajeChat`.

### P2 — `fix-voice-dir-purge` (A3)
**Qué:** `VOICE_DIR` crecía sin tope (cada nota persistida nunca se borraba). **Cambio:**
`Helpers.purgeOldVoiceNotes()` borra best-effort `.wav` de >7 días, llamada al arrancar
(`loadPropertiesFile`), NO en `RESET_GAME` (rompería el replay intra-partida). Try-with-resources
para no fugar el Stream.

### P3 — `fix-mp3player-stop-flush` (A1)
**Qué:** `stop()` hacía `line.drain()` (bloquea hasta vaciar el buffer; mal para un corte y
puede colgar el EDT si el dispositivo se estanca) + semánticamente incorrecto (una cancelación
no debe reproducir la cola). **Cambio:** `line.stop()` + `line.flush()` (corta y descarta al
instante). El `drain()` del fin natural en `play()` se queda. Idiomático: `setVolume` ya usa
`flush()`.

### P3 — `fix-mp3player-setvolume-clamp` (A6)
**Qué:** `setVolume` solo acotaba el gain por el mínimo; un `vol>1` futuro lanzaría
`IllegalArgumentException` tragada → volumen sin cambiar. **Cambio:** acotar a min Y max, espejo
de `setClipVolume`. Hardening defensivo (hoy no disparable, todas las rutas pasan `vol≤1`).

### P3 — `fix-voicenote-filename-unique` (A4)
**Qué:** el nombre `millis+nick` podía colisionar (cliente hostil inundando frames en el mismo
ms). **Cambio:** sufijo `genRandomString(8)` (solo `a-z`, filesystem-safe).

### P3 — `fix-voicenote-nick-anchor` (A5)
**Qué:** el host atribuía CHAT/VOICEMSG al nick suministrado por el cliente
(`partes_comando[1]`), spoofeable. **Cambio:** usar el nick autenticado de la conexión (campo
`nick`) en ambos casos (también cierra el spoofing en el relay a los demás peers). De paso
elimina el acceso a `partes_comando[1]`.

### P3 — `fix-voicerecorder-dead-mic-cleanup` (A7)
**Qué:** si el micro abría pero no entregaba datos (driver catatónico), el reader salía sin
limpiar `RECORDER`/`VOICE_RECORDING` → todo el audio del juego mudo hasta soltar la tecla.
**Cambio:** `start()` recibe un `on_no_data` que el reader invoca al salir sin datos
(`!live && !stop_requested`); el manager limpia el estado, guardado por `RECORDER==recorder`.

### P3 — `fix-tts-server-recover-symmetry` (C1)
**Qué:** `RESET_GAME(recover)` snapshoteaba 4 reglas pero omitía `TTS_SERVER_RECOVER`
(asimetría latente). **Cambio:** línea simétrica. No era bug activo (el persist-on-toggle lo
cubría), hardening de consistencia.

### P3 — `fix-rit-castvote-atomic` (C2)
**Qué:** `castVote` era check-then-act sobre un `volatile`; el clic y el timeout automático
podían disparar el listener dos veces. **Cambio:** compuerta `AtomicBoolean.compareAndSet` (solo
gana el primer voto).

### P3 — `fix-bot-betsize-cache` (B1)
**Qué:** `getBetSize()` (con RNG embebido) se evaluaba dos veces por BET postflop → el importe
ejecutado difería del que justificó la decisión. **Cambio:** cachear el sizing en
`computeRawDecision` (`lastBetSize`) y devolverlo en `getBetSize()` sin args, eliminando la
segunda pasada de RNG. **Cachea en AMBAS ramas** (preflop y postflop) — ver §Reauditoría: el
preflop fue una corrección posterior. No cambia la legalidad (min-raise y guard del 75% usan el
mismo valor).

### P3 — `fix-handshake-partes-guard` (N4)
**Qué:** `serverSocketHandler` accedía a `partes[1]` sin guard; un peer ya autenticado con
payload malformado → `AIOOBE` al catch general, que no cierra el socket (fuga de FD). **Cambio:**
guard `partes.length<2` que cierra el socket y retorna, en un punto donde el socket aún no se ha
cedido a un `Participant`. No se toca el catch general (cerrar ahí rompería las ramas de éxito).

### P3 — `fix-avatar-tmp-cleanup` (L1)
**Qué:** los avatares temporales de los peers (y su miniatura `_chat`) nunca se borraban de
`tmpdir`. **Cambio:** `deleteOnExit()` en los 5 sitios de creación. `deleteOnExit` (no un
barrido de tmpdir) para no borrar los temporales de otra instancia en la misma máquina.

### P3 — `fix-playfromchat-sync-write` (A9)
**Qué:** el ancla clicable de la nota se publicaba antes de que terminara la escritura asíncrona
del `.wav` → un clic rápido daba "nota no encontrada". **Cambio:** escribir el fichero síncrono
ANTES del `chatHTMLAppend` (`recibirNotaVoz` corre off-EDT; el wav es pequeño).

---

## Reauditoría (a petición tuya)

Rama `audit-2026-06-08-integration` = `master` + los 17 merges (todos limpios), clean-compila.
Workflow de **18 revisores adversarios + crítico de completitud**, cada uno releyendo el código
combinado y `git diff`, buscando regresiones y verificando que cada fix cierra su hallazgo.

### Regresión P0 cazada y corregida — `fix-bot-betsize-cache`
La primera versión de B1 cacheaba `lastBetSize` SOLO en la rama postflop de
`computeRawDecision`; la rama **preflop retorna antes** (`Bot.java:706-711`), así que
`getBetSize()` devolvía `0f`/rancio → **el bot abría/subía con 0 fichas preflop**, corrompiendo
la contabilidad y la legalidad del min-raise. Lo verifiqué a mano (re-lectura ±30 dos veces) y
lo **corregí**: cacheo también en la rama preflop cuando la decisión es BET
(`getBetSize(lastEffectiveStrength)`, igual que computaba master). Es suficiente cachear solo en
BET porque `injectRecreationalMistake` **nunca convierte un no-BET en BET** (verificado en
javadoc y cuerpo). Re-verificado SOUND por una pasada adversaria independiente: para toda
decisión BET (preflop y postflop) `lastBetSize` es ≥ ciega grande > 0 antes de que el crupier lo
lea; un único valor recorre min-raise, guard del 75% y wire.

**Lección:** cachear un valor "siempre calculado" exige verificar TODAS las ramas de salida del
método, no solo la que tienes delante.

### Resto: 16/17 SOUND a la primera
Confirmado por la reauditoría (lectura fichero:línea de cada uno): cierran su hallazgo, sin
regresión, y conviven correctamente. En particular el cluster de notas de voz (A2 contador + A7
dead-mic + A8 relay + A4 nombre + A9 write-antes-de-ancla) coexiste sin desbalances.

### Borde residual documentado (no arreglado)
A2+A7: bajo micro catatónico + interleave preciso de 3 hilos, el contador `VOICE_RECORDING`
podría doble-decrementar para una grabación (TOCTOU entre las lecturas de `RECORDER`). El clamp
`updateAndGet(n>0?n-1:0)` lo absorbe (nunca negativo, nunca silencio colgado permanente); peor
caso = destello transitorio de un-mute durante una nota. Es una clase de carrera **pre-existente**
(el camino `start()==false` de master ya la tenía) y no bloqueante; se documenta, no se arregla.

### Falsos positivos confirmados (del informe original)
- `VoiceRecorder.stop()` leyendo `pcm` concurrentemente — `ByteArrayOutputStream` ya es
  `synchronized` en la JDK; sin race observable.
- `AudioSettingsDialog.open()` sin reutilizar `INSTANCE` — el modal serializa el input y
  `refreshVolume` es null-safe; sin impacto.

---

## Validación de integración

Las 17 ramas mergean a `master` **sin un solo conflicto** en este orden (las 7 que tocan
`WaitingRoomFrame.java` afectan regiones disjuntas tras evitar el reindentado masivo de N2):

```
fix-netclient-last-received-chm · fix-voice-recording-counter · fix-mp3player-stop-flush ·
fix-mp3player-setvolume-clamp · fix-tts-server-recover-symmetry · fix-rit-castvote-atomic ·
fix-bot-betsize-cache · fix-voice-dir-purge · fix-voicerecorder-dead-mic-cleanup ·
fix-voicenote-nick-anchor · fix-client-consumer-frame-guards · fix-client-control-frame-zombie ·
fix-voice-relay-async · fix-voicenote-filename-unique · fix-playfromchat-sync-write ·
fix-handshake-partes-guard · fix-avatar-tmp-cleanup
```

El resultado combinado **clean-compila** (`mvn -o clean compile` → BUILD SUCCESS). Los tests
viven en el proyecto QA aparte (`tools/qa`, sims de bot pesadas + cripto): no se ejecutan por
coste y porque ningún cambio toca cripto y el de bot es legalidad-neutral (verificado). Smoke
manual pendiente de tu lado (en especial: abrir/jugar una partida con notas de voz, salir como
host, y una mano con bots subiendo preflop tras el fix B1).

**Nada pusheado ni mergeado a `master`.** Pendiente tu OK + decisión de release.
