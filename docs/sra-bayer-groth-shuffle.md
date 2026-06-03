# Bayer-Groth — prueba de barajado verificable LIGERA (completa el mental poker)

Estado: **construcción incremental, aislada, sin smoke.** Sustituye al cut-and-choose (que era
seguro pero pesado, ~13.000 mults de curva por paso). Bayer-Groth (2012) es **~100× menos cálculo**
(prueba pequeña, verificación ~O(n)) y **la misma seguridad** (construcción publicada y demostrada).
Es la mitad que faltaba del mental poker: probar que el mazo es un **barajado honesto** (permutación
de 52 cartas distintas), no solo que está bien cifrado.

## Lo que hay que probar (igual que antes)
Cada paso de cascada: `B = π(k·A)`, con `k` escalar uniforme secreto y `π` permutación secreta, sin
revelar ni `π` ni `k`. Bayer-Groth lo hace eficiente.

## Disciplina (cripto + dinero) — innegociable
1. Construcción **publicada** (Bayer-Groth 2012), no inventada.
2. Suite **adversaria** por ladrillo: completeness + el **tramposo cae** (un no-barajado se rechaza
   con prob abrumadora) + ZK sanity. Fuzz donde aplique.
3. **Revisión criptográfica externa antes de producción.** Bayer-Groth es matematica intrincada;
   un bug sutil (transcript, grado de polinomio) puede abrir un hueco que los tests no cacen
   (clase "Frozen Heart"). Los tests dan alta confianza; la revisión es la última puerta.

## Escalera de ladrillos (cada uno con suite adversaria)
1. **PedersenVectorCommit** — `C = r·H + Σ a_i·G_i`, generadores nothing-up-my-sleeve. Binding,
   hiding, homomorfico. **(hecho — 8 tests verdes)**
2. **Transcript Fiat-Shamir** — ya existe (reutilizado del motor anterior); absorbe todos los
   commitments/retos.
3. **Argumento de producto** — prueba `∏ a_i = b` sobre un vector comprometido (Hadamard + zero
   argument). El nucleo combinatorio.
4. **Argumento de multi-exponenciacion** — prueba que un producto de potencias comprometido es
   correcto. La pieza que ata la permutacion a los puntos cifrados.
5. **Shuffle argument** — ensambla 3+4 (con el truco de Neff/Bayer-Groth: una permutacion queda
   caracterizada por `∏(x − i) = ∏(x − π(i))` para un reto `x`). Suite adversaria: barajado honesto
   verifica; no-permutacion / duplicado / escalar no-uniforme / cegado → rechazados.

Cuando 5 este verde + revision externa, se cablea (sustituye CutChooseShuffleProof en el motor de
cascada; el resto del cableado wire-1..3 ya esta hecho). El reparto sigue instantaneo y el calculo,
al ser ~100× mas ligero, ni pega el CPU.

## Por que la base ya construida sirve
Ristretto255 (grupo de orden primo), Fe25519 (campo, ya con mul por limbs), Dleq, Transcript y
ahora PedersenVectorCommit son **exactamente** los primitivos que Bayer-Groth necesita. No se tira
nada — se construye encima.
