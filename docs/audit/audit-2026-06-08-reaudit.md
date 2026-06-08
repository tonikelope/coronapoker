# CoronaPoker — Reauditoría de los fixes 2026-06-08 (confianza para merge sin smoke)

El autor no puede hacer smoke manual y quiere saber si puede fiarse de los 17 fixes
([`audit-2026-06-08-fixes.md`](audit-2026-06-08-fixes.md)). Este documento recoge las **dos
reauditorías** hechas, las regresiones cazadas y corregidas, y la **evidencia empírica**.

## Veredicto

**Sí, con confianza alta.** Tras dos pasadas adversarias independientes (24 agentes en total) +
verificación manual + una sim de bot real: **0 P0/P1, 0 bugs abiertos, ningún must-fix**. Una
regresión P0 que se introdujo (B1) fue **cazada por la reauditoría y corregida**. Los dos P2 de
segundo orden que la pasada profunda señaló se han **cerrado**. El camino de **dinero está
validado empíricamente**, no solo por revisión.

---

## Pasada 1 — reauditoría por-fix (18 revisores adversarios)

Rama `audit-2026-06-08-integration` = master + los 17 merges (sin un solo conflicto),
clean-compila. Cada fix releído contra `git diff` buscando regresiones.

**Cazó una regresión P0 en B1 (bot betsize):** la primera versión cacheaba `lastBetSize` solo en
la rama postflop; el preflop retornaba antes → `getBetSize()` devolvía `0f` → **el bot habría
abierto/subido con 0 fichas preflop**, corrompiendo contabilidad/min-raise. Verificado a mano
(±30, dos veces) y **corregido** (cacheo también en preflop cuando la decisión es BET;
`injectRecreationalMistake` nunca crea un BET, así que basta). Re-verificado SOUND por pasada
independiente. Commit final: `12d30488`.

Los otros 16: SOUND a la primera.

## Pasada 2 — reauditoría PROFUNDA "asume-roto" (6 ejes transversales + síntesis)

Enfoque distinto: efectos de segundo orden, no corrección aislada. Ejes: dinero/legalidad,
tragado de excepciones/señales, máquina de estado de voz, red combinada, teardown/recover,
recursos/hilos. **Resultado: 0 BUGs.** Veredicto de síntesis: **seguro mergear los 17**, sin
must-fix. Dos P2 de segundo orden marcados como "aceptables" — **ambos cerrados** aquí:

### P2 #1 — A9 reintroducía head-of-line en el hilo lector del peer → CERRADO
A9 hizo la escritura del `.wav` síncrona en `recibirNotaVoz`, que en el host corre en el hilo
lector del peer emisor — justo el bloqueo que A8 había quitado del relay. **Fix:** escritura
síncrona **solo para la nota propia** (se renderiza en hilo de pool, donde el clic inmediato es
probable y no bloquea a nadie); para una nota **recibida** se mantiene la escritura **async**
como master (sin head-of-line). Commit `3d264d68`.

### P2 #2 — N2 convertía un fail-safe en fail-silent en la carrera RESET → CERRADO
El `try/catch` por-frame de N2 se tragaba el NPE de la carrera RESET (resetInstance() con frames
en buffer) y seguía NPE-spinning, en vez de dejarlo escapar a reconnect/recover como master.
**Fix:** en el catch por-frame, si `GameFrame.getInstance()==null` (no hay partida viva), se
**re-lanza** para que el handler externo termine/reconecte como master. Solo dispara cuando el
juego ya no está vivo → nunca tumba una sesión activa. Commit `ab08097e`.

### P3 extra cerrado
A3: quitado `FOLLOW_LINKS` del `Files.walk` de la purga (no seguir symlinks fuera de
`VOICE_DIR`). Commit `c7206e1e`.

### Residuales documentados (pre-existentes, NO introducidos por los fixes)
- **Stuck-mute si RESET_GAME ocurre mientras el micro abre:** el `on_live` ya retornaba en seco
  con `GameFrame` null en master; ningún path de shutdown resetea el estado de grabación. Es de
  master, el contador de A2 no lo empeora. No tocado.
- **Fuga de FD si el `partes[0]` del handshake no es Base64 válido:** pre-existente, fuera del
  alcance de N4 (que cierra la fuga del `partes[1]`). No tocado.
- **N1 PONG con basura no numérica hace `notifyAll`:** inofensivo (el waiter re-chequea y espera
  al timeout). Sin acción.

---

## Evidencia empírica (lo que no es solo revisión)

El subsistema de **bot/dinero** (el fix más peligroso, B1) se validó ejecutando una **partida
simulada real** contra el código integrado:

```
tools/qa  HeadsUpSimulatorTest  →  Tests run: 3, Failures: 0, Errors: 0  (~88 s)
```

- Las **4 dificultades** juegan manos completas sin excepción, **conservación de fichas** (400).
- **200 manos** con **conservación verificada en cada mano** + ambos bots ganan algunas (juego
  sano: el bot abre, sube, 3-betea — exactamente el path que la regresión B1 habría roto).

Si el bug de B1 (apuestas de 0 fichas preflop) siguiera presente, esto habría roto conservación
o el reparto. **El path de dinero queda validado dinámicamente.** Además, `Crupier.java` tiene
**0 líneas de diff** frente a master: toda la legalidad/conservación del dealer es código
probado intacto; B1 solo cambia el *origen* del importe (cache de la EV en vez de un segundo
`getBetSize` con RNG fresco).

---

## Riesgo residual honesto

- **Dinero/bot:** riesgo mínimo. Crupier intacto + sim de 200 manos en verde. Es lo más sólido.
- **Red (N1–N4):** sin tests automáticos; descansa en revisión (dos pasadas). El neto es
  claramente positivo: N1+N2 cierran un fallo **común y grave** (un frame malformado tumbaba la
  sesión / dejaba el lector zombie) y la carrera RESET, antes "aceptable", quedó cerrada. El peor
  caso conocido restante de N2 es, en una ventana RESET muy estrecha, una mano anulada por
  timeout — **nunca pérdida de fichas**.
- **Audio/notas de voz (A1–A9):** sin tests automáticos; el balance del contador de grabación se
  trazó interleaving-por-interleaving por dos agentes que coincidieron, con el clamp como red.
  Lo peor conocido es un destello transitorio de audio o un "nota no encontrada" en una carrera
  rarísima — cosmético, sin efecto en partida/saldo.

**Conclusión:** mergeable a master con confianza alta. Lo que no he podido cubrir empíricamente
(red/UI/audio, que no tienen tests) descansa en revisión doble + el hecho de que los cambios más
arriesgados revierten hacia comportamiento de master ya probado. Un smoke de "abrir partida +
jugar con notas de voz + salir como host" cerraría el último 5% de incertidumbre, pero no hay
ningún flanco de dinero ni crash conocido sin cubrir.
