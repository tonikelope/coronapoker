# Smoke manual — Sprint 1 (Seguridad bloqueante)

**Cobertura:** 🔴-1 RCE filter en RECOVERDATA, 🔴-2 readLine cap + handshake, 🔴-3 path traversal en nick, 🟠-4 handshake `setSoTimeout`.

**Tiempo estimado:** 8-10 minutos. Necesita **2 máquinas** o **2 instancias** del JAR (host + cliente).

## Preparación

- [ ] Compilación limpia: en raíz `mvn install -DskipTests`, en `tools/qa/` `mvn -o test -Dtest='GameFlowSmoke'` (debe pasar).
- [ ] Borrar `~/.coronapoker/coronapoker_balance*` (o equivalentes) para que recovery arranque de cero.
- [ ] Tener una conexión local (`127.0.0.1`) configurada para host y cliente.

## 1. Smoke flujo básico (sin tocar los cambios)

- [ ] **1.1** Arranca host (sin password). Arranca cliente con nick normal (`pepe`). Cliente entra a la sala.
- [ ] **1.2** Inicia partida con 2 bots adicionales. Juegan **3 manos completas**.
- [ ] **1.3** Cierra cliente normalmente. El host muestra al cliente como desconectado (no como timeout error).
- [ ] **1.4** Sale del host normalmente.

**Si falla cualquiera de 1.1-1.4:** STOP, revertir todo el Sprint 1 — algo fundamental se rompió.

## 2. Cambios específicos del Sprint

### 🔴-3 Path traversal en nick

- [ ] **2.1** Cliente intenta entrar con nick `../../../foo` o `..\\bar`. Verifica:
  - El servidor RECHAZA o sanea el nick antes de crear el avatar temp.
  - En `%TEMP%` NO aparece ningún fichero fuera de `%TEMP%\corona_*`.
  - Comportamiento esperado tras el fix: o bien el nick se sanea a algo como `corona_avatar_ABC123XYZ_0` (usando hex del playerIdHex), o bien el servidor rechaza el JOIN con error legible.

### 🔴-1 RCE filter en RECOVERDATA

- [ ] **2.2** Provocar recovery: host inicia partida con 2 clientes, juegan 2 manos, mata host bruscamente (Alt+F4). Reabre host. Reabre clientes — cliente debe recibir RECOVERDATA del host sin error.
- [ ] **2.3** Verificar en `~/.coronapoker/Debug/coronapoker_debug_*.log`:
  - NO debe haber `SEVERE` durante el RECOVERDATA recibido legítimo.
  - El balance recuperado debe ser correcto.

### 🔴-2 readLine cap

- [ ] **2.4** (Solo si se quiere reproducir) Usar un cliente sintético con netcat:
  ```
  echo $(printf 'A%.0s' {1..20000000}) | nc 127.0.0.1 <puerto>
  ```
  El host **NO debe crashear** ni consumir RAM hasta OOM. Debe cerrar el socket por límite excedido. *Si no tienes netcat / no quieres probarlo, saltar.*

### 🟠-4 Handshake timeout

- [ ] **2.5** Cliente con netcat abre socket al puerto del host y queda quieto (no envía bytes). El host debe cerrar el socket en **~30 s** (HANDSHAKE_TIMEOUT_MS configurado). NO debe quedarse colgado indefinido.

## 3. Regresión: nada visualmente cambió

- [ ] **3.1** El nick saneado (paso 2.1) aparece correctamente en la sala de espera (no `___` ni codepoints raros visibles al usuario).
- [ ] **3.2** Los identicons de los participantes se siguen viendo correctamente.
- [ ] **3.3** El chat funciona normal (mensajes enviados / recibidos).
- [ ] **3.4** Una mano completa termina con showdown visible y pot asignado al ganador.

## Resultado

- ✅ Todos OK → **APROBAR MERGE Sprint 1 a master**.
- ❌ Algún paso falla → anotar síntoma + `git revert <sha del commit problemático>` + chat para diagnóstico.
