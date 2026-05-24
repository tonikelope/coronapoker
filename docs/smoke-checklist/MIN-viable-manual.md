# 🏃 Checklist MÍNIMO VIABLE — 10-13 min total

**Cuándo:** ANTES del primer merge a master del bloque Sprint 1-10.
**Quién:** tú (yo no puedo ejecutar UI, abrir partidas reales, ni medir memoria real).
**Si CUALQUIER paso falla:** anotar ID + síntoma + no mergear hasta diagnóstico.

> Esta es la **versión absoluta mínima** que pediste. Si quieres más cobertura, está `CONSOLIDATED-manual-test-battery.md` (30-40 min).

---

## Pre-flight automatizado (5 min, auto)

- [ ] **A-1** En raíz: `mvn install -DskipTests` → BUILD SUCCESS.
- [ ] **A-2** En `tools/qa/`:
  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.1.8-hotspot"
  & "C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd" -o test -Dtest='*Smoke'
  ```
  → **39/0/0 PASS**. Si no es 39/0/0, NO mergear.

## Smoke manual mínimo (8-10 min)

### Bloque 1 — el juego arranca y juega (3-4 min)

- [ ] **M-1** Lanza el JAR. La ventana inicial aparece, fuente correcta, sin errores rojos visibles.
- [ ] **M-2** Crea timba local **solo bots** (host + 2-3 bots, sin clientes). Comienza la partida.
- [ ] **M-3** Espera a que se jueguen **2-3 manos completas**. Verifica que:
  - Cartas aparecen al destaparse (sin "flashing" raro).
  - Botes se asignan al ganador correcto al showdown.
  - Sin diálogos de error.
- [ ] **M-4** Sal del menú File → Exit. La aplicación cierra en menos de 5 segundos.

### Bloque 2 — el cliente reconecta (4-5 min)

- [ ] **M-5** Lanza el JAR como host (sin password). Lanza una segunda instancia como cliente, conecta a `127.0.0.1`. Entran a la sala.
- [ ] **M-6** Inicia la partida (host + cliente + 1 bot). Juegan **1 mano completa**.
- [ ] **M-7** Cierra el CLIENTE bruscamente (Alt+F4). El host debe mostrar al cliente como desconectado, NO debe crashear ni colgarse.
- [ ] **M-8** Relanza el cliente, conecta al mismo host. Debe reconectarse OK (puede tardar unos segundos por el grace period). La mano sigue.
- [ ] **M-9** Sal limpio del cliente. Sal limpio del host. Sin errores.

### Bloque 3 — verificación de logs (1-2 min)

- [ ] **M-10** Abre el último `~/.coronapoker/Debug/coronapoker_debug_*.log` generado.
- [ ] **M-11** Buscar (`grep -i SEVERE` o equivalente): **0 niveles SEVERE no esperados** durante el flujo manual.
  - Esperados (OK): `SQLException: simulated UPDATE failure` (NO debe aparecer en sesión real, solo en tests).
  - **NO** esperados (KO si aparecen): `OutOfMemoryError`, `NullPointerException`, `InvalidClassException`, `IllegalMonitorStateException`.

## ✅ Resultado

- ☑️ **Todos OK** → mergear bloque Sprint 1-10 a master.
- ❌ **Algún paso falla** → anotar ID + síntoma → reportar al chat con el log relevante.

---

## Cobertura mínima vs commits

| Bloque del checklist | Sprints que valida |
|---|---|
| A-1, A-2 (pre-flight) | TODOS los Tier A (compile + AAA cubren el grueso) |
| M-1, M-2 (boot + UI) | 1 (handshake timeouts), 3 (concurrencia Swing), 5 (LATEST_NOTIFICATION) |
| M-3 (juego completo) | 4 (perf cripto), 5 (Image disposal), 10 (modulo positivo permutadoPos2Nick) |
| M-5 a M-9 (cliente + reconexión) | 1 (path traversal, OOM, RCE filter, ACL), 2 (TOFU CHANGED, ACL), 3 (writeCommand atomic, receiveMyCards wait) |
| M-10, M-11 (logs) | Todos (catches silenciosos, nuevos errores) |

## ⚠️ NO está cubierto por este checklist mínimo (riesgos remanentes)

- **Sesión LARGA con TTS** → leak InGameNotifyDialog (🟠-22). Lo verá quien juegue varios días seguidos.
- **Recovery de partida HALT + restart** → ObjectInputFilter RCE prevention (🔴-1). Cubierto solo por código, no por manual.
- **Update flow del coronaupdater** → regex literal fix (🟡-38). Solo se ejerce en upgrade real.
- **Edge cases Windows** (nicks con `\` o `:`, AV quarantine de gifsicle.exe) → 🟠-25 createDirectories defensivo.
- **DoS attacks reales** (netcat con bytes infinitos / sin bytes) → 🔴-2 + 🟠-4. Solo si quieres probar con `nc`.

Para cobertura COMPLETA de estos: `CONSOLIDATED-manual-test-battery.md` (30-40 min).
