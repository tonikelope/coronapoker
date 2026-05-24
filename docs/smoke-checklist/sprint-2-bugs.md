# Smoke manual — Sprint 2 (Bugs lógica + integridad)

**Cobertura:** 🟠-5 `partial_raise_cum` reset entre calles, 🟠-6 TOFU CHANGED no enmascarado, 🟠-7 `IdentityManager.writeKeypair` orden ACL, 🟠-2 ACTION fold-on-bad-sig.

**Tiempo estimado:** 10 minutos. Necesita **2 instancias** (host + cliente) en máquina local.

## Preparación

- [ ] Compilación limpia + `mvn -o test -Dtest='GameFlowSmoke'` PASA.
- [ ] Backup de `~/.coronapoker/coronapoker_ed25519_*` (porque vamos a tocar identidad).

## 1. 🟠-5 `partial_raise_cum` entre calles

Este es el más quirúrgico. Necesita un escenario muy concreto:

- [ ] **1.1** Host arranca partida heads-up (1 cliente, sin bots). Ciegas 1/2. Stacks iniciales 100.
- [ ] **1.2** Preflop: cliente raise a 10 (open-raise estándar). Host (BB) hace all-in PARCIAL (push de stack que NO cubre min-raise). Ej: stack del host = 13 → push 13, partial raise de +3 sobre los 10 (no cubre el min-raise de +10).
- [ ] **1.3** Cliente call. Pasan al flop con dos all-in.
- [ ] **1.4** Como no hay más acción posible (uno all-in, otro cubre), pasa directo al showdown. **Verifica en el log del juego (`registro`):**
  - El contador de raises del preflop debe quedar consistente (no inflado).
  - El min-raise calculado para hipotéticas siguientes apuestas (si aplicara) debe ser BB (2), NO BB + partial_raise_cum residual.
- [ ] **1.5** Variante con 3 jugadores: en lugar de all-in directo, repetir el escenario partial-raise PERO que NO cierre la acción → siguiente jugador puede actuar. Verifica que la primera apuesta del flop **NO se cuenta como raise** automáticamente.

**Si 1.4 o 1.5 fallan:** el fix no está aplicado correctamente. Revisar `Crupier.java:7516-7524` que tenga `this.partial_raise_cum = 0f;`.

## 2. 🟠-6 TOFU CHANGED no enmascarado

- [ ] **2.1** Host arranca con nick `host1`. Cliente entra con nick `bob` (primera vez → TOFU NEW). Juegan 1 mano.
- [ ] **2.2** Cliente cierra. **Borra a mano** `~/.coronapoker/coronapoker_ed25519_<hash de bob>*`. Cliente reabre con el mismo nick `bob` → nueva keypair generada.
- [ ] **2.3** Reentra al host (`bob` reconnect). El host detecta TOFU CHANGED.
- [ ] **2.4** Verificar:
  - El identicon de `bob` en la lista del host **cambia visualmente**.
  - Hay un **dialog modal bloqueante** en el host que pregunta "Esta identidad ha cambiado, ¿continúas?" (o equivalente).
  - Si el host elige NO, el cliente queda fuera.

**Si 2.4 muestra TOFU CHANGED SIN dialog modal o solo cambia el identicon silencioso:** el fix de 🟠-3 (TOFU CHANGED silencioso) no está aplicado o este sprint 2 lo invalida; ambos están relacionados.

## 3. 🟠-7 `IdentityManager.writeKeypair` orden ACL

- [ ] **3.1** Borrar `~/.coronapoker/coronapoker_ed25519_*` completamente.
- [ ] **3.2** Arrancar juego con nick nuevo. Esto fuerza generación nueva de keypair.
- [ ] **3.3** Verificar ACL del fichero `coronapoker_ed25519_<hash>_privkey` (Windows):
  ```powershell
  icacls "$env:USERPROFILE\.coronapoker\coronapoker_ed25519_<hash>_privkey"
  ```
  Debe mostrar **solo al usuario actual** con `(F)` Full Control. NO debe aparecer `BUILTIN\Users:(RX)` ni `Authenticated Users`.
- [ ] **3.4** En Unix: `ls -l` debe mostrar `-rw-------` (600).

## 4. 🟠-2 ACTION fold-on-bad-sig

Difícil de reproducir manualmente sin herramienta sintética. Si tienes:
- [ ] **4.1** (Opcional) Modificar a mano un mensaje ACTION cifrado tras la firma para invalidar el sig. Verificar que el receptor lo trata como FOLD voluntario (no aplica la decisión falsificada).
- [ ] **4.2** Si no: confirmar visualmente que dos partidas normales completas (host + 2 clientes + 2 manos cada una) **NO muestran ningún diálogo de SECURITY_LOCKDOWN ni receipts DIVERGENT en `~/.coronapoker/Logs/`**.

## 5. Regresión: el juego normal sigue normal

- [ ] **5.1** Host + 2 clientes + 2 bots, **3 manos completas** sin errores ni warnings nuevos en el log.
- [ ] **5.2** Recovery tras kill brusco del host (Alt+F4) sigue funcionando: relanzar host, clientes reconectan, balance correcto.

## Resultado

- ✅ Todos OK → **APROBAR MERGE Sprint 2 a master**.
- ❌ Algún paso falla → anotar síntoma + `git revert <sha>` + chat.
