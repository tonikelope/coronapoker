# 🧪 Batería consolidada de pruebas manuales — Sprint 1-5

**Cuándo ejecutar:** ANTES de mergear cualquier sprint a master.
**Tiempo estimado:** ~30-40 min total. Algunos pasos son opcionales / solo si tienes la herramienta.
**Necesitas:** 1 host + 1-2 clientes (pueden ser instancias separadas en la misma máquina con loopback).

## Cómo usar

1. Cada checkbox `[ ]` se marca `[x]` al verificar OK.
2. Si **cualquier** paso falla → anotar síntoma + parar.
3. Para reportar fallo al chat: indicar el ID exacto del paso (ej. `Sec-3 falla: ...`).
4. La automatización cubre lo que cubre (`mvn -o test -Dtest='*Smoke'` = 39 tests AAA). Esta checklist cubre lo que la automatización NO puede.

---

## 0. Preparación (~5 min)

- [ ] **P-1** Compilación limpia desde raíz: `mvn install -DskipTests` → BUILD SUCCESS.
- [ ] **P-2** Smoke automatizado pasa: en `tools/qa/`, `mvn -o test -Dtest='*Smoke'` → 39/0/0 OK.
- [ ] **P-3** Tener limpio `~/.coronapoker/`: opcionalmente backup de `coronapoker.db`, `coronapoker.properties`, `identity_*` para restaurar al final.
- [ ] **P-4** Activar TTS si tienes (Sprint 5 lo toca, sin TTS te saltas paso L-1).

---

## 1. 🔐 Sprint 1 — Seguridad bloqueante (~10 min)

### Sec-1: Path traversal en nick (🔴-3)
- [ ] **Sec-1.1** Cliente intenta entrar al host con nick `../../foo`.
- [ ] **Sec-1.2** En `%TEMP%`, verificar que NO aparece ningún fichero fuera del propio tmpdir. El avatar (si se envió) debe estar como `corona_______foo_avatarXXX` (o similar saneado) dentro de `%TEMP%`.
- [ ] **Sec-1.3** Repetir con nick `..\\..\\Windows\\Startup\\evil.bat`. Mismo resultado: confinado en `%TEMP%`.
- [ ] **Sec-1.4** Cliente entra con nick `CON` o `NUL`. La conexión debe completarse (el avatar queda como `corona__CON_avatarXXX` con `_` prefix por reservado Windows — el nick se ve normal en la UI).

### Sec-2: Handshake DoS protection (🔴-2 + 🟠-4)
- [ ] **Sec-2.1** _(Opcional, requiere netcat)_ `echo "$(printf 'A%.0s' {1..20000000})" | nc 127.0.0.1 <puerto>` — el host NO debe crashear ni consumir RAM hasta OOM. Debe cerrar el socket por límite.
- [ ] **Sec-2.2** _(Opcional)_ Cliente con `nc 127.0.0.1 <puerto>` que queda quieto sin enviar bytes — el host cierra el socket en **~30 segundos** (HANDSHAKE_TIMEOUT_MS). NO debe quedarse colgado.

### Sec-3: ObjectInputStream RCE filter (🔴-1)
- [ ] **Sec-3.1** Host arranca partida normal con 1 cliente + 2 bots. Juegan 2 manos.
- [ ] **Sec-3.2** Mata host con Alt+F4 brusco.
- [ ] **Sec-3.3** Reabre host. Reabre cliente. La reconexión + recovery del cliente debe completarse SIN error.
- [ ] **Sec-3.4** Verificar en `~/.coronapoker/Debug/coronapoker_debug_*.log`: NO `SEVERE` durante RECOVERDATA legítimo. NO `InvalidClassException`.
- [ ] **Sec-3.5** El balance recuperado debe ser correcto.

### Sec-4: Smoke flujo básico (regresión)
- [ ] **Sec-4.1** Host + 2 clientes + 2 bots. 3 manos completas sin errores nuevos en logs.
- [ ] **Sec-4.2** Cierre limpio: salir desde el menú. NO `SEVERE` durante shutdown.

---

## 2. 🐛 Sprint 2 — Bugs lógica + integridad (~7 min)

### Bug-1: TOFU CHANGED no enmascarado (🟠-6)
- [ ] **Bug-1.1** Borrar `~/.coronapoker/identity_*` completo.
- [ ] **Bug-1.2** Cliente entra con nick `bob` al host → TOFU NEW (primera vez visto).
- [ ] **Bug-1.3** Cierra cliente. Borra `~/.coronapoker/identity_*<hash>*` solo del cliente. Cliente reabre con mismo nick `bob` → nueva keypair generada.
- [ ] **Bug-1.4** Reentra al host. Verificar en `~/.coronapoker/Debug/coronapoker_debug_*.log` del host: `TOFU: pubkey CHANGED for bob (verified_oob reset to 0)` (NIVEL WARNING, no SEVERE).
- [ ] **Bug-1.5** Identicon de `bob` en el host **cambia visualmente** vs el de antes.

### Bug-2: IdentityManager ACL ordering (🟠-7)
- [ ] **Bug-2.1** Borrar `~/.coronapoker/identity_*` completo.
- [ ] **Bug-2.2** Arrancar juego con nick nuevo → genera keypair.
- [ ] **Bug-2.3** (Windows) `icacls "$env:USERPROFILE\.coronapoker\identity_<hash>.ed25519"` debe mostrar **solo al usuario actual con (F)**. NO debe aparecer `BUILTIN\Users` ni `Authenticated Users`.
- [ ] **Bug-2.4** (Linux/Mac) `ls -l ~/.coronapoker/identity_*.ed25519` debe ser `-rw-------` (0600).

### Bug-3: Recovery flow regresión
- [ ] **Bug-3.1** Repetir Sec-3.1-Sec-3.5 — debe seguir funcionando (recovery sigue siendo robusto post-fix).

---

## 3. ⚙️ Sprint 3 — Concurrencia chapuzas (~5 min)

### Conc-1: getHeight==0 polling eliminated (🟠-10)
- [ ] **Conc-1.1** Arranca host + 1 cliente. Verifica que la UI del tapete se renderiza completa sin demoras visibles ni "flashes" en el primer frame.
- [ ] **Conc-1.2** Cambiar zoom (Ctrl++/Ctrl+-) — los iconos del bote/ciegas se reescalan sin "flashing". Sin freezes de 125ms.
- [ ] **Conc-1.3** _(Opcional, perf)_ Si tienes profiler conectado, NO debes ver threads ociosos en `Helpers.pausar` durante la inicialización del tapete (antes los había).

### Conc-2: NetClient writeCommand atomic (🟠-11)
- [ ] **Conc-2.1** Host + cliente conectados, partida en curso.
- [ ] **Conc-2.2** Forzar caída de red al cliente (desconectar Wi-Fi o `iptables -A OUTPUT -p tcp --dport <puerto> -j DROP` y luego eliminar).
- [ ] **Conc-2.3** Cliente detecta caída → muestra Reconnect2ServerDialog → reconecta.
- [ ] **Conc-2.4** Durante la reconexión NO debe haber `SEVERE` por "socket closed mid-write" en el log del cliente.

### Conc-3: SHUTDOWN_THREAD_POOL graceful (🟠-13)
- [ ] **Conc-3.1** Cierre normal del juego (menú File → Exit o cerrar ventana).
- [ ] **Conc-3.2** El proceso Java termina en <10 segundos. NO queda hung.
- [ ] **Conc-3.3** En el log: si tareas tardaban, debería aparecer `"Thread pool did not terminate within grace period"` (WARNING). En cierre normal, no aparece — terminan en <3s.

### Conc-4: receiveMyCards wait/notify (🟠-9)
- [ ] **Conc-4.1** Partida con 2+ jugadores, juega 1 mano completa hasta el reparto de cartas pocket.
- [ ] **Conc-4.2** Las cartas pocket de cada cliente aparecen inmediato cuando llega POCKET_CARDS — NO debe haber el delay típico de hasta 100ms que el pausar(100) introducía.

---

## 4. 🚀 Sprint 4 — Rendimiento (~5 min)

### Perf-1: SQLite WAL + índices (🔴-4 + 🔴-5)
- [ ] **Perf-1.1** Verificar que tras la PRIMERA apertura del juego post-upgrade, aparecen ficheros `~/.coronapoker/coronapoker.db-wal` y `coronapoker.db-shm` junto al `.db`. NO error en log.
- [ ] **Perf-1.2** Jugar 1 mano completa para que se escriban acciones en la DB.
- [ ] **Perf-1.3** Abrir StatsDialog (menú o atajo). Si tienes muchas partidas previas, el load debe ser perceptiblemente más rápido que antes (índices funcionando). Sin medición precisa, solo "no se cuelga".
- [ ] **Perf-1.4** _(Opcional)_ Verificar índices manualmente: `sqlite3 ~/.coronapoker/coronapoker.db ".indexes"` debe listar `idx_hand_game`, `idx_action_hand`, `idx_showdown_hand`, `idx_balance_hand`, `idx_showcards_hand`.

### Perf-2: arraycopy encrypt/decrypt (🔴-7)
- [ ] **Perf-2.1** Smoke implícito: si Sec-4.1 (3 manos) pasó, encrypt/decrypt funcionan correctamente. La diferencia es solo velocidad, no observable directo.

### Perf-3: logVerbose gate (🟡-1)
- [ ] **Perf-3.1** Verificar en `~/.coronapoker/Debug/coronapoker_debug_*.log`: los `[BOT AI]` siguen apareciendo SI el nivel JUL es INFO (default). Si configuras nivel a WARNING, no aparecen NI se calcula el `String.format` (ahorro).

---

## 5. 💧 Sprint 5 — Memory leaks (~10 min, requiere sesión LARGA)

### Leak-1: savePropertiesFile FDs (🟠-19)
- [ ] **Leak-1.1** _(Linux/Mac)_ `lsof -p <pid del juego> | grep coronapoker.properties` — debe mostrar **0 fichero abierto** (cerrado tras cada cambio).
- [ ] **Leak-1.2** Cambiar 10 preferencias seguidas (volumen, zoom, sonidos, etc.). Repetir lsof — sigue 0.
- [ ] **Leak-1.3** _(Windows)_ Sin lsof; usar Process Explorer → Handles del proceso, filtrar `coronapoker`. Mismo invariante.

### Leak-2: InGameNotifyDialog TTS no acumula (🟠-22)
- [ ] **Leak-2.1** Si tienes TTS activado, jugar partida con varios mensajes de chat que disparen TTS.
- [ ] **Leak-2.2** _(Opcional)_ Heap dump tras 30 minutos: `jmap -dump:format=b,file=heap.bin <pid>` y abrir con VisualVM/Eclipse MAT. Buscar instancias de `InGameNotifyDialog`. Debe haber a lo sumo **1** activa (no decenas/cientos como antes).

### Leak-3: GIF / ImageReader dispose (🟡-29/30/31)
- [ ] **Leak-3.1** Si tienes imágenes/GIFs en el chat: enviar 10 imágenes seguidas. La memoria del proceso NO debe crecer monotónicamente más de lo esperado por las imágenes mismas.

### Leak-4: Player timers cleanup (🟡-33)
- [ ] **Leak-4.1** Jugar partida normal, salir al menú principal (no cerrar juego), arrancar otra partida. Repetir 3 veces.
- [ ] **Leak-4.2** _(Opcional)_ Heap dump tras 3 ciclos. Buscar `Timer` retenidas. Idealmente solo las activas de la partida actual.

---

## 🔄 Regresión cross-sprint (~3 min)

- [ ] **Reg-1** Juego entero: host + 2 clientes + 1 bot. **3 manos completas con showdown**. Verificar:
  - [ ] **Reg-1a** Pots correctos (ganador se lleva el bote esperado).
  - [ ] **Reg-1b** Stacks suman al total inicial (conservación).
  - [ ] **Reg-1c** Sin error rojos en logs.
  - [ ] **Reg-1d** Identicons de todos los participantes visibles.
- [ ] **Reg-2** Cerrar y reabrir el juego. `~/.coronapoker/` mantiene `.db`, `.db-wal`, `.db-shm`, `identity_*`, `coronapoker.properties` consistentes.
- [ ] **Reg-3** _(Si Sprint 1+ está en master)_ Probar update flow del coronaupdater si tienes acceso a versión vieja.

---

## ✅ Resultado final

- ☑️ **Todos los pasos OK** → APROBAR MERGE de Sprint 1-5 a master.
- ❌ **Algún paso falla** → anotar ID exacto + síntoma → reportar al chat. NO mergear hasta diagnóstico.

---

## 📋 Trazabilidad: cobertura por commit

| Sprint | Commit | Cubre paso(s) checklist |
|---|---|---|
| 1 | 7b854dc9 setSoTimeout | Sec-2.2 |
| 1 | c689d3b9 readLine cap | Sec-2.1 |
| 1 | d1b41c26 safeNickForFilename | Sec-1.1 / 1.2 / 1.3 / 1.4 |
| 1 | d3922b46 ObjectInputFilter | Sec-3.* |
| 1 | d38eb777 (cherry-pick) reconnect ack cap | Conc-2.* |
| 2 | 95d82f36 TOFU CHANGED | Bug-1.* |
| 2 | f1b70d19 IdentityManager ACL | Bug-2.* |
| 3 | c4b166cb runWhenLaidOut | Conc-1.* |
| 3 | 83fc3eec SHUTDOWN await | Conc-3.* |
| 3 | 3bf0e5e1 writeCommand lock | Conc-2.* |
| 3 | 6dac0770 receiveMyCards wait | Conc-4.* |
| 4 | a7664071 SQLite WAL+índices | Perf-1.* |
| 4 | 4a441729 arraycopy | Perf-2.1 (implícito) |
| 4 | dec2b9a8 logVerbose gate | Perf-3.1 |
| 5 | 5e6cb3f8 4× try-with-resources | Leak-1.* |
| 5 | e80e03ee URL streams + ImageReader | Leak-3.* |
| 5 | 57436fda LATEST_NOTIFICATION dispose | Leak-2.* |
| 5 | b173ccf9 icon_zoom_timer stop | Leak-4.* |
| 5 | af17a784 coronaupdater paridad | (silencioso — sin checklist) |
