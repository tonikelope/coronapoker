# Smoke checklist — Fase 3: migración del grupo SRA a Ristretto255

Cabecera: cubre el commit `5f56d482` (group swap CryptoSRA → RistrettoSRA en
Crupier/WaitingRoom). **NO** cubre el binding verificable (Fase 4, aún no cableada):
esta fase solo cambia el grupo criptográfico, el comportamiento del juego debe ser
**idéntico** al de antes. Objetivo del smoke: detectar cualquier regresión introducida
por el cambio de grupo.

Lo automatizado ya cubre (no hace falta repetir a mano): aritmética del grupo, encode/
decode, hash-to-group, lock/unlock, conmutatividad, y el **flujo dual-lock completo**
(cascade + rotación + dealing pocket/community + testamento + showdown) —
`RistrettoSRADualLockTest`, `RistrettoSRACascadeTest`, 58 tests verdes. Lo de abajo es
**solo** lo que requiere el juego real (red, GUI, timing, persistencia).

Compilar limpio antes: `mvn install -DskipTests` en raíz, lanzar el JAR/NetBeans.

5-10 min, pasos binarios OK / no-OK. Si alguno falla: anotar síntoma, NO mergear, reportar.

- [ ] **1. Mano local completa (host solo + bots).** Abrir mesa, repartir. Tus 2 cartas
      se ven correctas; el flop/turn/river salen; al showdown las cartas de los bots se
      revelan y el bote se reparte sin error. *(cascade + dealing + community + showdown
      con Ristretto)*
- [ ] **2. Mano con 1 humano remoto (2 instancias).** Host + 1 cliente. Cada uno ve SUS
      pockets (y no las del otro hasta showdown); board coherente en ambos; showdown OK.
      *(unlock batch por red con Ristretto)*
- [ ] **3. Varias manos seguidas (3-4) en la mesa del paso 2.** Sin cuelgues en el
      barajado, sin "shuffling" infinito, botón rota normalmente. *(estabilidad cascade
      multi-mano)*
- [ ] **4. Recuperación de mano interrumpida (CRÍTICO).** A mitad de una mano (tras el
      flop), cerrar host + cliente y hacer "continue-last-game". La mano se reanuda, las
      cartas propias siguen siendo las mismas y el showdown resuelve. *(el fósil ahora
      guarda encodings Ristretto — verifica que persistencia + reinyección funcionan)*
- [ ] **5. Espectador en la mesa.** Un jugador arruinado (sin fichas) que sigue mirando:
      ve el board correctamente y no provoca cuelgue ni misdeal en los que sí juegan.
      *(path espectador-en-ring con Ristretto)*
- [ ] **6. Mesa con bot + humano remoto mezclados.** Una mano. Las cartas del bot se
      resuelven en el host; el humano ve las suyas. *(path de bots con Ristretto)*

Nota de compatibilidad: una partida guardada con la versión ANTERIOR (Montgomery) **no**
es recuperable con esta build (el grupo cambió). Empezar partidas nuevas para el smoke.

Si TODO pasa: la Fase 3 es sólida y se puede proceder a cablear la Fase 4 (binding
verificable que cierra el oráculo cegado) sobre base validada.
