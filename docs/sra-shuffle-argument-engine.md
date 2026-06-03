# Motor de barajado verificable (proof-of-shuffle) — plan de construcción TDD

Estado: **construcción incremental, aislada, sin smoke.** Objetivo: blindar con tests
automatizados robustos el motor que permite probar, en zero-knowledge, que la cascada SRA es un
barajado honesto — para que el host no pueda colar el pocket de un jugador en una posición
community (ataque confirmado en `RotationSmuggleActivePlayerTest`, rama `rotation-anchor-phase0`).
El **cableado** al juego es posterior y sí necesita smoke; el **motor** no.

## El enunciado a probar (exacto)
Cada paso de cascada transforma el deck: `deck_out = π(k · deck_in)`, con `k` escalar **uniforme
secreto** y `π` permutación **secreta**, sobre puntos Ristretto255. Hay que probar en ZK que tal
`(π, k)` existe — sin revelar `π` (filtraría el orden de cartas) ni `k` (rompería el cifrado).
Esto es exactamente un **argumento de shuffle** (mix-net con re-cifrado = multiplicar por `k`).

## Construcción de referencia (NO inventar cripto)
**Bayer–Groth (2012)** como base (publicado, revisado, con implementaciones de referencia contra
las que contrastar). Adaptación: el "re-cifrado" no es ElGamal sino multiplicar por un escalar
común `k` (caso más simple). La solidez viene de la prueba académica; los tests verifican que la
**implementación** es correcta.

## Disciplina (cripto + dinero + open source)
1. Construcción publicada, no inventada.
2. Suite **adversaria** por ladrillo: completeness + soundness empírica (todo intento de forja
   falla) + ZK sanity + vectores del paper donde existan.
3. "Pasar mis tests" ≠ solidez formal. **Antes de cablear → revisión criptográfica externa.**
   El motor es aislado: se construye y testea ahora con cero riesgo de juego; la revisión es la
   última puerta antes del smoke de cableado.

## Escalera de ladrillos (cada uno con suite adversaria, TDD)
1. **PedersenCommit** — `C = m·B + r·H`, H nothing-up-my-sleeve. Hiding + binding + homomórfico.
   **(este commit)**
2. **Transcript Fiat–Shamir** — dominio-separado, absorbe TODOS los valores públicos (un valor
   omitido = forjable). Tests de que cambiar cualquier entrada cambia el reto.
3. **Sigma básico** — PoK de apertura de un commitment. Completeness + special-soundness
   (extraer el testigo de dos transcripts con el mismo commit) + forja falla.
4. **Argumento de producto** — `∏ a_i = b` sobre un vector comprometido.
5. **Argumento de multi-exponenciación / permutación** — el núcleo del shuffle.
6. **Shuffle argument completo** — ensambla 4+5. Suite adversaria: no-permutación, duplicación,
   escalar no-uniforme, cegado, carta añadida/quitada, claves cambiadas, transcript manipulado →
   TODOS rechazados; barajado honesto → verifica; el multiset se preserva.

Tras 6 + revisión externa, el motor queda listo para cablear (anclar la rotación / la cascada),
que es ya la fase con smoke.
