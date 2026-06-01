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

## Estado tras la auditoría

80–85 tests verdes, compila. Los 3 fixes están en `sra-phase4-2`. Smoke recomendado, por
rama y en orden: `sra-phase4-1b` (HAND_V2), luego `sra-phase4-2` con **énfasis en
recuperación de mano** (cabo 3) y reparto pocket multi-peer (cabos 1–2).
