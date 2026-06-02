# Fase 4.3 — cerrar los dos oráculos restantes (community + rotación)

Objetivo **no negociable** (palabras del autor): el host **no** puede obtener, antes de
tiempo, (1) las hole cards de otros jugadores ni (2) las comunitarias. El pocket binding
(4.2) cierra el oráculo por `REQ_SRA_UNLOCK_CHAIN`/`REQ_SRA_UNLOCK_BATCH(POCKET)`, pero la
auditoría (cabo 6) encontró dos puertas todavía abiertas. Este documento las diseña.

> **Estado:** diseño. NADA implementado aquí. La rotación es el dual-lock (delicado) y no se
> toca sin smoke. Rama `sra-phase4-3`, sin merge a master.

## Los dos ataques y qué cierra cada pieza

**Ataque 2 — comunitarias antes de tiempo.** El community-unlock (`REQ_SRA_UNLOCK_BATCH`,
phase FLOP/TURN/RIVER) aplica el `sra_unlock_community` del helper a bytes elegidos por el
host, sin anclaje. El state-machine gatea por calle, pero el **cegado `r·P` evade GATE 6 y el
gate de calle** (el host etiqueta como la calle actual). → el host descifra una comunitaria
futura.

**Ataque 1 — hole cards ajenas.** Vía `DECK_ROTATION_REQ` (oráculo de pocket-unlock) el host
obtiene `r·P·k_Hp⁻¹·k_Hc`; para quitar la cobertura `k_Hc` usa el community-unlock (ataque 2)
**o** el testamento community de H si sale.

| Pieza del fix | Cierra |
|---|---|
| **(A)** community dealing al chain (anclado al MEGAPACKET) + GATE 6 | Ataque 2 **completo**; Ataque 1 para helpers **vivos** (sin community-unlock cegado no hay cómo quitar `k_Hc`) |
| **(B)** anclar la rotación | Ataque 1 para el caso **"H sale con testamento"** (el host descifra el residual de rotación cegado con el `k_Hc` del testamento) |

(A) es la mayor parte del valor y reutiliza maquinaria probada. (B) es la parte difícil
(cambio de protocolo) y la de mayor riesgo.

## (A) Community dealing verificable — diseño

`cascadeAndDealCommunityPieces` (Crupier.java:7600) hoy es el patrón batch pre-4.2: por cada
recipient `X` una `copy` de las `numCards` comunitarias, single-locked por `X`; cada helper
`H` quita su `community-unlock` de las copias de `X≠H` vía `requestRemoteUnlockBatch`. Es
**isomorfo al pocket dealing de 4.2** — se migra igual:

- **Anclaje:** la copia de cada `X` y pieza `j` ancla a `local_mega_packet[(offset+j)*32]`
  (el punto community del MEGAPACKET, post-rotación). Todas las copias comparten el MISMO
  punto base; difieren en qué `community-locks` se han quitado.
- **Cadenas:** reutilizar `DealChain`/`VerifiableUnlock`/`UnlockChainWire` con
  `commitments = peer_k_community`. El host extiende localmente (su lock, los de bots, los
  testamentos de exits); los helpers vivos vía `REQ_SRA_UNLOCK_CHAIN` con
  `phase=UNLOCK_PHASE_FLOP/TURN/RIVER` (el handler ya elige `peer_k_community`/
  `sra_unlock_community` por phase — ver WaitingRoomFrame.java:2467/2475).
- **`offsetBase`:** en el REQ, `offsetBase = offset+j` (índice del punto community), no `i*2`.
- **Tail:** la copia de `X` = tail de su cadena (single-locked por `X`), idéntico en bytes al
  resultado del batch actual → el resto del flujo (broadcast por recipient, resolución local,
  rabbit, recovery) **no cambia de formato**.

### Guard nuevo imprescindible en el handler chain: GATE 6 (genesis)
El self-strip guard del pocket (pointIdx∈[mySlot*2,+1]) **no** aplica a community (las
comunitarias son compartidas, no hay "pocket propio"). El guard correcto es **GATE 6**: tras
pelar el lock, si el tail resuelve a **genesis**, es extracción (el host presentó la cadena
"todos los locks menos el mío" pidiéndome revelar la carta) → `triggerSecurityLockdown`.
Con el binding el cegado es imposible, así que **GATE 6 ya no se puede evadir** — chain +
GATE 6 cierran el desacople análogo al cabo 4. Añadir en WaitingRoomFrame.java tras el
`DealChain.extend` del handler (sólo para phases community): comprobar
`RistrettoSRA.resolveCardIndex(ext.residual_por_chunk) < 0`.

### Por qué (A) cierra el ataque 1 para vivos
Sin el community-unlock cegado (bloqueado por el anclaje), el host no puede quitar el `k_Hc`
de cobertura del residual de rotación `r·P·k_Hp⁻¹·k_Hc`. `k_Hc` sólo se obtiene por
community-unlock (cerrado) o testamento (requiere que H salga → caso (B)).

### Riesgos de regresión de (A) (a vigilar en smoke)
`cascadeAndDealCommunityPieces` sirve **flop/turn/river + rabbit + testamento + recovery**.
La migración debe preservar: (a) el orden secuencial de helpers; (b) el fallback testamento
(extend local con `sra_unlock_community`); (c) el formato de las copias broadcasteadas; (d) la
ruta rabbit (`abortOnFail=false`). Tests unitarios del flujo host↔helper (como
`UnlockChainWireTest`) + smoke de cada calle, rabbit, y un EXIT a media mano.

## (B) Anclar la rotación — diseño (NO trivial)

La raíz del ataque 1: en `DECK_ROTATION_REQ` el cliente aplica su **pocket-unlock** a
`incomingPieces` del host con sólo `arePointsValid` — sin anclaje. No puede anclar al
MEGAPACKET porque la rotación (FASE 1.5) **precede** al MEGAPACKET (Crupier.java:1149 vs 1226).

**Opción B1 (recomendada) — deck pre-rotación comprometido.** El host, tras la cascada y
antes de la rotación, difunde el deck post-cascada/pre-rotación (o sólo su sección community)
+ su hash; el cliente lo guarda y, al servir `DECK_ROTATION_REQ`, exige que `incomingPieces`
coincida **byte-a-byte** con la sección community de ese deck comprometido. El cegado `r·P` no
coincide → rechazo. Coste: un broadcast extra (o reutilizar el primer `MEGAPACKET` como
"pre-megapacket" y mandar un segundo "rotation delta"). Es un cambio de protocolo acotado.

**Opción B2 — rotación verificable con DLEQ.** Igual que el pocket: el cliente prueba (DLEQ)
que aplicó su `k_Hp⁻¹` committed, encadenando desde el punto pre-rotación comprometido. Más
trabajo; sólo aporta sobre B1 si se necesita probar la rotación a terceros (no es el caso).

**Opción B3 (rechazada) — GATE 6 en la rotación.** No cierra el cegado (igual que no lo
cerraba en el pocket): el residual cegado no es genesis. Inútil.

→ **B1**. Necesita smoke del reparto completo (toca la FASE 1.5 del dual-lock).

## A2 — detalle de implementación (estudiado, listo para ejecutar)

`cascadeAndDealCommunityPieces` (Crupier.java:7600). Matices confirmados leyendo el método:

- **Recipients** (tienen `copy` + reciben broadcast): `hostNick` + `remoteHumans`. Los **bots
  NO** son recipients (el host conoce el board; las pieces solo se difunden a humanos, 7728).
- **Signers** (tienen community-lock que hay que pelar): `host` + **bots** + `remoteHumans`
  (todo el ring).
- Hoy el setup (7618-7654) **aplica los unlocks de host+bots DIRECTAMENTE** sobre cada copy.
  Para el chain eso **debe convertirse en `extend`s** desde el punto del MEGAPACKET — si no,
  el punto base deja de ser el MEGAPACKET y el anclaje del helper se rompe. Por eso hay que
  reescribir el setup, no solo el bucle de helpers.

**Plan A2:**
1. `extendCommunityChainsForSigner(Map<String,String[]> chains, int offset, int numCards,
   String skipRecipient, String signerNick, byte[] signerLock)` — análogo a
   `extendPocketChainsForSigner` pero: itera recipients (claves del map), `pointIdx=offset+j`,
   `numCards` piezas, `peer_k_community`, salta `skipRecipient`.
2. `chains` = `Map<recipientNick, String[numCards]>` inicializado a `""` para host + cada
   humano remoto.
3. Host extiende (skip=host) con `local_sra_lock_community`; cada bot extiende (skip=null, los
   bots no son recipients) con su community-lock (derivado de `sra_unlock_community`).
4. Cada helper humano `H`: `requestRemoteUnlockChain(H, ph, unlockPhase, reqItems)` con un
   `ReqItem(peerIdx=idx(X), offsetBase=offset, chains=copyX)` por cada recipient `X≠H`.
   Fallback EXIT: extiende local con `getUnlockScalar(ph.getSra_unlock_community())` (skip=H).
   Cobertura: verificar que todos los `X≠H` quedaron cubiertos (paridad cabo 2).
5. Host verifica cada cadena final (`DealChain.verify` contra `peer_k_community`) y toma el
   `tail` = copyX single-locked por X. **Formato de salida idéntico** → broadcast y
   `recibirCartasComunitarias` NO cambian.
6. El handler ya soporta community (offsetBase arbitrario + GATE 6 de A1). El self-strip guard
   pocket no interfiere (pointIdx community ∉ rango pocket).

Cubre flop/turn/river **y rabbit** (mismo método vía `unlockPhase`/`pieceCommand`). Validado a
nivel cripto por `CommunityChainDealingTest`. Falta: integración (red) → smoke.

## Orden de implementación propuesto

1. (A1) Extender `REQ_SRA_UNLOCK_CHAIN` con GATE 6 para phases community. (test)
2. (A2) Reescribir `cascadeAndDealCommunityPieces` a chains, anclado al MEGAPACKET. (test
   host↔helper por calle)
3. (A3) Rechazar phases community en `REQ_SRA_UNLOCK_BATCH` (queda muerto) — paridad con el
   cabo 5 del pocket; el batch entero se elimina cuando (A2) esté validado.
4. **Smoke A**: flop/turn/river, rabbit, EXIT a media mano, recovery.
5. (B1) Deck pre-rotación comprometido + verificación byte-a-byte en `DECK_ROTATION_REQ`.
6. **Smoke B**: reparto completo con helpers, EXIT antes/después de rotar.

Tras 4 y 6, el objetivo no-negociable queda cumplido: el host no descifra ninguna carta
(pocket ni community) antes de tiempo, ni siquiera combinando rotación + testamento.

## Mapeo completo de puertas (6ª pasada — confirma cobertura)

El oráculo es UN patrón: *el cliente aplica su unlock a datos del host*. Enumeradas TODAS sus
apariciones (grep de `applyCommutativeLock` con un unlock de cliente, lado WaitingRoom):

| Puerta | Dónde | Estado |
|---|---|---|
| POCKET unlock | `REQ_SRA_UNLOCK_CHAIN` / batch(POCKET) | **cerrada** (4.2 + cabos 4/5) |
| COMMUNITY unlock (FLOP/TURN/RIVER) | `REQ_SRA_UNLOCK_BATCH` (2386) | diseño **A** |
| RABBIT_* unlock (helpers) | **mismo** handler batch, phases `RABBIT_*` | diseño **A** (mismas pieces) |
| ROTACIÓN pocket-unlock | `DECK_ROTATION_REQ` (2228) | diseño **B** (cabo 6) |

No-oráculos confirmados: la cascada (2138) aplica **lock** (no unlock); el target abriendo su
propia copia (pocket 1407, community/host 7712, rabbit 2815) es apertura legítima de lo que ya
es suyo; el showdown (`SHOWCARDS`) revela el unlock pero va **firmado** (Ed25519). **No hay
quinta puerta.** → A (community **+ rabbit**, todas las phases `community`/`RABBIT_*`) + B
(rotación) cubren el 100% del patrón. A2 debe migrar `cascadeAndDealCommunityPieces` para los
DOS caminos (community y rabbit comparten el método vía `unlockPhase`).

## Nota de honestidad

Hasta completar (A) **y** (B), el objetivo NO está cumplido para pockets en el caso "H sale".
(A) sola ya elimina el ataque 2 entero y el ataque 1 para vivos — el grueso del riesgo. El
esquema criptográfico (Ristretto255 + DLEQ + binding encadenado) es el correcto y **no cambia**:
4.3 es aplicar el MISMO anclaje a dos sitios más, no reimplementar cripto.
