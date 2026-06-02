# Auditoría interna de los cambios SRA (Ristretto + dealing verificable)

Auto-auditoría de toda la línea de trabajo (rama `sra-phase4-2`, que acumula todo). Foco
en lo **no validado por smoke** (Fases 4.1b y 4.2). Método: relectura crítica fichero:línea,
con verificación de los flujos de red/consenso y los caminos sutiles. Tres cabos encontrados
y **corregidos**; el resto verificado correcto.

## Cabos encontrados y corregidos

### 🔴 1 (crítico) — race de `peer_k_pocket` en el handler verificable
El pre-parse síncrono del `MEGAPACKET` (`WaitingRoomFrame`) poblaba `active_crypto_ring` y
`local_mega_packet` para que el unlock-handler los viera sin carrera, pero **no** poblaba
`peer_k_pocket`/`_community` (eso ocurría async en `recibirMisCartas`). El handler
`REQ_SRA_UNLOCK_CHAIN` (threadRun) usa esos mapas y podía ejecutarse **antes** de que
estuvieran poblados → `DealChain.verify` contra mapa vacío → **lockdown falso**.
**Fix:** `parseCommitments` se llama ahora de forma **síncrona** en el pre-parse del
`MEGAPACKET`, junto a `active_crypto_ring`/`local_mega_packet`.

### 🟠 2 (medio) — cobertura del RESP no verificada
`requestRemoteUnlockChain` no comprobaba que el helper devolviera **todos** los slots
pedidos (el batch viejo validaba `count`). Un RESP incompleto dejaría un residuo aún
lockeado por el helper, detectable solo al resolver.
**Fix:** el caller del pocket dealing comprueba que cada slot `≠ h` quedó cubierto; si no,
trata el RESP como rechazo (`pocket_unlock_refused`).

### 🟠 3 (medio) — recovery rompía el `H_0` de HAND_V2
`initHandStateChain` siembra ahora `H_0` con HAND_V2 (commitments `K`). En recuperación de
mano la cascade no se re-ejecuta y `peer_k_pocket` queda vacío (se limpia en
`readyForNextHand`), así que el `H_0` recuperado caía a HAND_V1 ≠ al V2 original → cadena de
acciones y consenso divergentes en manos recuperadas.
**Fix:** el fósil persiste los commitments (`COMMITMENTS@`, vía `serializeCommitments`), y
ambas ramas de recovery (host y cliente) los repueblan (`parseCommitments`) antes de
`initHandStateChain`. **Este fix hace imprescindible un smoke de recuperación.**

## Verificado correcto

- **Motor Ristretto255 + DLEQ:** anclado a vectores oficiales RFC 9496 (encode B/2B, A.3,
  constantes auto-validadas); ~49 tests. Sin hallazgos.
- **Fase 3 (group swap):** sólo quedan los 3 `shuffleDeck` (agnósticos); validada por el
  smoke real del autor (humanos+bots, multi-mano, recuperación, testamento, calentando).
- **Enrutado de los comandos nuevos:** el host los entrega a `received_commands` por el
  `default` del switch (`Participant`), y el cliente los enruta en su switch; el dedup
  `(subcomando, id)` no los bloquea porque cada REQ/RESP lleva id único. Correcto.
- **Coherencia de `H_0` host↔cliente:** ambos usan los mismos `K` (del mismo MEGAPACKET) y
  `startV2` reordena por player_id; el fallback a V1 es coherente (mismos K ausentes en ambos).
- **Residuo single-locked:** byte-idéntico al flujo viejo (mismo `applyCommutativeLock`), así
  que el target abre igual, `single_locked_pocket_cards` y el showdown no cambian de formato.
- **Verificación del host en el dealing:** el host verifica cada cadena final contra
  `peer_k_pocket` antes de tomar el tail; un `peer_idx` mal etiquetado o una cadena de otro
  slot no ancla y se rechaza.
- **Testamento dual-lock:** el manejo community-only se preserva; el caso pocket-testament
  es código heredado que no se alcanza en el dealing (el pocket key no se entrega al salir).

## Observaciones (no bloqueantes)

- El comando `MEGAPACKET` crece ~140 B por jugador (campo `COMMITMENTS@`/5º campo). Para
  mesas grandes el comando ronda los pocos KB; vigilar en smoke con muchos jugadores.
- El **recipient** no verifica la cadena (le basta el binding del helper para cerrar el
  oráculo); verificar también en el cliente sería defensa-en-profundidad futura.
- **community dealing sigue sin binding** (usa el batch viejo) — migración incremental
  pendiente (rama `sra-phase4-3`) + colisión-check global (4.3).

## Segunda pasada (doble check, mente fresca)

Re-auditoría centrada en (a) que los tres fixes no introdujeran regresiones y (b) lo que los
tests **no** pueden cazar. Sin cabos nuevos; dos confirmaciones de peso:

- **Fix 3 (recovery) verificado correcto:** los únicos `peer_k_pocket.clear()` están en
  `enviarCartasJugadoresRemotos` (dealing de mano nueva, **no** se ejecuta en recovery) y en
  el propio fix, **justo antes** de `parseCommitments`. `readyForNextHand` **no** toca
  `peer_k_pocket`. Por tanto el repoblado de recovery persiste intacto hasta
  `initHandStateChain` → `H_0` V2 reconstruido.
- **Coherencia `lock ↔ K` en los cuatro caminos** (host, bot, helper-testamento,
  helper-vivo): un mismatch lock/unlock sería **invisible** a los 80 tests (usan el lock de
  forma consistente) pero rompería toda verificación DLEQ en producción. Confirmado que cada
  `extend` recibe el escalar cuyo `commitment` es exactamente el `K` registrado en
  `peer_k_pocket` (host `local_sra_lock`; bot `getUnlockScalar(received_token)`; helper
  `getUnlockScalar(sra_unlock)`; `getUnlockScalar` es involutivo). Coinciden los cuatro.
- **Paridad con el batch viejo:** la política vivo/exit/testamento/fallback del caller pocket
  es **idéntica** a la del batch (diff `e6cc936e`); solo cambió el mecanismo (chain+DLEQ vs
  `applyCommutativeLock` directo), matemáticamente equivalente. El caso "vivo sin respuesta →
  `sra_unlock`" es heredado y ya validado, no un camino nuevo.

Observaciones menores nuevas (no bloqueantes): el handler chain no replica el anti-reuse de
tags del batch viejo (irrelevante con el binding: la cadena es determinista, no extrae info);
el cliente no limpia `peer_k_pocket` entre manos (inocuo: `parseCommitments` sobrescribe los
`K` de los nicks del ring; los residuos de nicks ausentes no se iteran).

## Tercera pasada (adversarial — host hostil) — 🔴 DOS cabos críticos

Pasada con sombrero de atacante sobre el flujo nuevo. Encontró **dos** caminos que
**reabrían el oráculo cegado** que todo este trabajo pretende cerrar. Los dos corregidos
y con test que documenta el ataque.

### 🔴 4 (crítico) — strip del propio pocket por `offsetBase` desacoplado
El host controla `offsetBase` (qué punto del megapacket pela el helper)
**independientemente** del `peerIdx` etiquetado. El guard del handler validaba `peerIdx`
(la etiqueta), no el punto. Un host hostil manda `peerIdx=<otro>` (pasa el guard) +
`offsetBase=<mi_slot*2>` (mi propio pocket). `DealChain.extend` ancla bien (es un punto real
del megapacket) y pela el lock → el helper devuelve **sus propias cartas en claro** (el host
ya tenía ese pocket single-locked tras el reparto normal).
**Fix:** el handler calcula `mySlot` y **rechaza pelar cualquier punto en
`[mySlot*2, mySlot*2+1]`** (solo POCKET). Test `PocketSelfStripAttackTest` prueba el leak y
fija la invariante.

### 🔴 5 (crítico) — bypass por el batch viejo (`REQ_SRA_UNLOCK_BATCH`)
El handler batch viejo **seguía aceptando `UNLOCK_PHASE_POCKET`**. Su única defensa es GATE 6
(genesis-check), que el cegado `r·P` **evade** — es exactamente la vuln original. Un host
hostil ignora el comando chain y pide el pocket por el batch viejo → todo el binding
bypasseado.
**Fix:** el batch **rechaza POCKET** (`triggerSecurityLockdown`); el pocket es chain-only.
Verificado que ningún caller legítimo usa batch con POCKET (el único, `cascadeAndDealCommunityPieces`,
usa `phaseForStreet` → siempre FLOP/TURN/RIVER). El batch solo sirve community hasta su
migración (4.3).

**Lección:** el binding cierra el cegado *del punto*, pero un oráculo tiene más puertas —
*qué* punto se pela (cabo 4) y *por qué comando* (cabo 5). Ambas ahora cerradas.

## Cuarta pasada — gate de orden POCKET (evaluado y DESCARTADO)

El batch viejo cerraba la fase POCKET tras revelar cualquier comunitaria
(`isSraUnlockRequestLegitimate`); el handler chain no lo replica (`isUnlockPhaseStateSafe`
→ POCKET siempre `true`). **Descartado a propósito:** con el self-strip guard (cabo 4) el
helper nunca pela su propio pocket, y todos los pockets se reparten al inicio, así que pedir
pocket-chains tarde no da información nueva. Añadir el gate sería defensa-en-profundidad
redundante con **riesgo de regresión por timing** (el handler es async; un `lockdown` que
dependa de `flop_revealed` puede dispararse en un camino honesto con lag). Sin valor de
seguridad real → no se toca. Verificado además que los fixes de la 3ª pasada son **guards
defensivos puros** (no-ops en tráfico honesto): el caller honesto nunca pide pelar el pocket
propio (excluye `i==h`), y `phaseForStreet` nunca devuelve POCKET (el batch sólo recibe
community). Sin regresión.

## Quinta pasada (adversarial) — 🔴 CRÍTICO: el oráculo sigue abierto por la rotación

**El hallazgo más importante de la auditoría.** El pocket binding de hoy (cabos 4/5) cierra
el oráculo por `REQ_SRA_UNLOCK_CHAIN` y `REQ_SRA_UNLOCK_BATCH`, pero **NO es suficiente por
sí solo**: hay una tercera puerta.

### 🔴 6 (crítico, VIVO, NO corregido — requiere fase 4.3) — oráculo por `DECK_ROTATION_REQ`
En la rotación dual-lock (FASE 1.5 del reparto, se ejecuta en cada mano), el cliente aplica
su **pocket-unlock** a `incomingPieces` que envía el host, validando sólo que sean puntos en
curva (`arePointsValid`) — **sin anclaje**. Ataque de un host hostil:
1. Envía `DECK_ROTATION_REQ` con `incomingPieces = r·P` (cegado; pasa la validación de curva).
   El helper devuelve `r·P·k_Hpocket⁻¹·k_Hcommunity`.
2. En el flop, envía ese resultado por `REQ_SRA_UNLOCK_BATCH` (community, sin binding — GATE 6
   lo evade el cegado). El helper quita `k_Hcommunity` → `r·P·k_Hpocket⁻¹`.
3. El host descega (`·r⁻¹`) → `P·k_Hpocket⁻¹`, para **cualquier** `P`.

Encadenando todos los helpers, el host pela todos los pocket-locks de un pocket y, con
`k_host`/`k_bots` que ya tiene, **lee las hole cards de un helper en claro**. Es la vuln
original reabierta por una tercera puerta. Afecta sólo a helpers humanos (los bots los rota
el host localmente, ya conoce sus secretos).

**Por qué no se corrige ahora:** el cliente **no tiene** el megapacket cuando rota (la
rotación precede a su ensamblado), así que el anclaje exige comprometer las community pieces
pre-rotación (commitment en H_0 / cascade) — es exactamente el **binding community de la fase
4.3**. Tocar el dual-lock a ciegas, sin smoke, es justo la regresión a evitar. **Decisión:
documentado y elevado de "mejora" a requisito.**

**Diseño del fix (4.3):** comprometer las community pieces pre-rotación (p. ej. su hash en
`H_0`, junto a los `K`), y que el handler de rotación verifique que `incomingPieces` coincide
byte-a-byte con ese compromiso antes de aplicar el pocket-unlock — análogo a cómo el pocket
chain ancla al MEGAPACKET. Cierra el cegado igual que en el pocket.

**Conclusión honesta:** hasta cerrar la 4.3, el objetivo "el host no ve ninguna carta antes
de tiempo" **no está cumplido para pockets**; el binding de hoy es un paso necesario pero el
pocket sigue extraíble vía rotación. Esto NO es un regreso del trabajo de hoy — es un flanco
preexistente del dual-lock que la auditoría adversarial ha sacado a la luz.

## Estado tras la auditoría

80–85 tests verdes, compila. Los 3 fixes están en `sra-phase4-2`. Smoke recomendado, por
rama y en orden: `sra-phase4-1b` (HAND_V2), luego `sra-phase4-2` con **énfasis en
recuperación de mano** (cabo 3) y reparto pocket multi-peer (cabos 1–2).
