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

## Construcción elegida: CUT-AND-CHOOSE (Sako–Kilian), NO Bayer–Groth
Argumento de barajado por **cut-and-choose**, publicado y clásico. Por qué sobre Bayer–Groth,
dado el contexto (cripto + dinero + open source + sin revisión externa todavía):

- **Solidez trivial de razonar:** para probar `B = π(k·A)`, el prover publica un deck intermedio
  `C = π1(k1·A)` con `B = π2(k2·C)` (`π2∘π1 = π`, `k2·k1 = k`). El reto pide revelar **una** de las
  dos mitades: o `(π1,k1)` (se verifica `C = π1(k1·A)`) o `(π2,k2)` (se verifica `B = π2(k2·C)`).
  Si `B` **no** es un barajado honesto de `A` (p.ej. duplica una carta), entonces para CUALQUIER
  `C` al menos una mitad es inválida → se pilla con prob ≥ 1/2. K rondas → fallo ≤ **2⁻ᴷ**.
- **Zero-knowledge:** cada ronda usa `(π1,k1)` frescos; revelar una mitad no filtra `π` ni `k`.
- **Implementación SIMPLE = testeable:** solo permutar + multiplicar por escalar + comparar
  encodings Ristretto. Sin matemática de polinomios/commitments donde se cuela la unsoundness.
- **Coste:** K× cómputo (K≈128 para 2⁻¹²⁸). Es **setup de mano, no tiempo real** → asumible;
  K y batching se ajustan/optimizan tras tener correctitud. Prueba más grande, nos da igual aquí.

La solidez viene del argumento publicado (cut-and-choose); los tests verifican la implementación.

## Disciplina (cripto + dinero + open source)
1. Construcción publicada, no inventada.
2. Suite **adversaria** por ladrillo: completeness + soundness empírica (todo intento de forja
   falla) + ZK sanity + vectores del paper donde existan.
3. "Pasar mis tests" ≠ solidez formal. **Antes de cablear → revisión criptográfica externa.**
   El motor es aislado: se construye y testea ahora con cero riesgo de juego; la revisión es la
   última puerta antes del smoke de cableado.

## Escalera de ladrillos (cada uno con suite adversaria, TDD)
1. **PedersenCommit** — `C = m·B + r·H`, H nothing-up-my-sleeve. Hiding + binding + homomórfico.
   **(hecho — 9 tests verdes)**. Primitiva general reutilizable (commit de retos/decks).
2. **Transcript Fiat–Shamir** — dominio-separado, absorbe (length-prefixed) TODOS los valores
   públicos y pliega el reto en el estado. Tests: determinismo, que cambiar CUALQUIER entrada
   cambia el reto, separación de dominio, retos consecutivos independientes. **(siguiente)**
3. **DeckTransform** — aplica `(π, k)` a un deck de puntos Ristretto: `out[i] = k · in[π(i)]`.
   Permutación por Fisher–Yates con stream determinista (reusa el patrón del shuffle existente).
   Tests: es biyección, invertible, preserva el multiset bajo `k`, round-trip.
4. **CutChooseShuffleProof** — prove/verify no interactivo (retos por Fiat–Shamir sobre los `C_j`
   publicados). Suite adversaria: barajado honesto → verifica; no-biyección (duplicar/soltar
   carta), escalar no-uniforme (cegado por-punto), `C` manipulado, reto manipulado, mitad
   incoherente → TODOS rechazados con prob ≥ 1−2⁻ᴷ. ZK sanity: una mitad no fija `π`.

## Estado: MOTOR COMPLETO (ladrillos 1–4), aislado, 37 tests verdes
- PedersenCommit (9), Transcript (12), DeckTransform (7), CutChooseShuffleProof (8) + perf (1).
- **Coste medido** (52 cartas, K=128, paralelizado por rondas): prove ~1,1s / verify ~0,8s por
  paso de cascada. Viable en setup de mano (los proves de cada peer corren en paralelo en su
  máquina; la verificación de N pasos se solapa con la red).

## Lo que falta (ya con smoke, fuera de este motor aislado)
1. **Cableado:** una prueba de barajado por paso de cascada, anclada a los decks comprometidos
   (broadcast/commit de cada estado de cascada para que el consenso verifique), + `RotationChain`
   (DLEQ) para la rotación. Precursor aislado pendiente: exponer la permutación de
   `CryptoSRA.shuffleDeck` como `int[]` para poder probarla.
2. **Revisión externa** del motor (barata por lo simple del cut-and-choose) antes de master.
3. **Bump de protocolo** + recovery/fossil de las pruebas.

Con eso, el host modificado por un tramposo NO puede producir una mano válida con un smuggle: no
sabe forjar la prueba de barajado del paso, y todo cliente honesto la rechaza. Prevención, no
detección.
