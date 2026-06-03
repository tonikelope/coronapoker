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

## Escalera de ladrillos (cada uno con suite adversaria) — **motor COMPLETO, 45 tests verdes**
1. **PedersenVectorCommit** — `C = r·H + Σ a_i·G_i`, generadores nothing-up-my-sleeve. Binding,
   hiding, homomorfico. **(hecho — 8 tests)**
2. **Transcript Fiat-Shamir** — ya existe (reutilizado del motor anterior); absorbe todos los
   commitments/retos. **(reutilizado)**
3. **VectorOpeningProof** — PoK Schnorr de la apertura del commitment vectorial. **(hecho — 5 tests)**
4. **MultiExpArgument** — `Q = Σ x_i·A_i` para `x` en un commitment vectorial. **(hecho — 5 tests)**
5. **MultiplicationProof** — `c = a·b` sobre valores comprometidos (Comm de un valor). El atomo del
   producto; algebra de soundness verificada a mano. **(hecho — 5 tests)**
6. **ProductArgument** — `∏ a_i = b` (b publico) via grand-product (encadena 5) + apertura
   Schnorr-sobre-H del producto final. El nucleo combinatorio. **(hecho — 5 tests)**
7. **PermutationArgument** — `d'` comprometido es permutacion de `d` publico, via
   `∏(x − d'_i) = ∏(x − d_i)` (Neff/B-G + Schwartz-Zippel). **(hecho — 5 tests)**
8. **WeightedSumArgument** — `Q = Σ f_i·B_i` con `f` comprometido individualmente (mismo sustrato que
   7). Ata la permutacion oculta a los puntos cifrados del mazo. **(hecho — 5 tests)**
9. **ShuffleArgument** — ENSAMBLAJE `B[i] = k·A[π(i)]`: reto `e = H(A,B)`, compromete `f_i = e[π(i)]`,
   y prueba (1) `f` permutacion de `e` [7], (2) `Q = Σ f_i·B_i` [8], (3) `Q = k·P_A` con
   `P_A = Σ e_j·A_j` publico [Schnorr del escalar comun]. Combinando:
   `Σ_j e_j·(B_{σ⁻¹(j)} − k·A_j) = 0` con `e` aleatorio → `B_i = k·A_{σ(i)}`. Suite corona (7 tests):
   honesto e identidad verifican; **ATAQUE SMUGGLE rechazado** con mapeo real (cae en permutacion) Y
   mintiendo `π=identidad` (cae en `Q ≠ k·P_A`); escalar no-uniforme y manipulaciones → rechazados.

## Coste real (lo que motivo el pivote)
Mazo n=52, single-thread, con Fe25519 ya optimizado (limbs + ventana 4-bit): **~0.47s/paso prove,
~0.36s/paso verify** (`ShuffleArgumentPerfTest`). Una cascada de 3 pasos ≈ 1.4s prove + 1.1s verify
**en background tras repartir** = imperceptible, y **no pega el CPU** (el cut-and-choose lo clavaba al
100% con ~13.000 mults/paso). Optimizable mas (multi-scalar mult, batch de los checks) si hiciera falta.

## Honestidad sobre la construccion (cripto + dinero)
Esto es una construccion **estilo Neff/Bayer-Groth** ensamblada desde sus primitivas (cada Sigma con
su algebra de soundness verificada y su suite adversaria), **no un port linea-a-linea del paper de
Bayer-Groth 2012**. Es la forma O(n) limpia y obviamente solida, no la optimizacion batch logaritmica.
La **revision criptografica externa antes de produccion sigue siendo innegociable**: los tests dan
alta confianza (completeness + el tramposo cae en cada ladrillo), pero un bug sutil de transcript o de
binding entre ladrillos es justo lo que una revision independiente debe descartar (clase "Frozen Heart").

## Cableado (pendiente, despues de revision)
Cuando haya revision externa, se cablea `ShuffleArgument` en el motor de cascada (sustituye al
`CutChooseShuffleProof`; el cableado wire-1..3 ya esta hecho — grabar la cadena en el reparto y
verificar en background). El reparto sigue instantaneo.

## Por que la base ya construida sirve
Ristretto255 (grupo de orden primo), Fe25519 (campo, ya con mul por limbs), Dleq, Transcript y
ahora PedersenVectorCommit son **exactamente** los primitivos que Bayer-Groth necesita. No se tira
nada — se construye encima.
