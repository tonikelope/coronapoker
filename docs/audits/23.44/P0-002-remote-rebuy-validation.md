# P0-002 — una respuesta REBUY remota no validada puede consumir la espera

## Identificación

- Base auditada: `204481c6c` (CoronaPoker 23.44).
- Subsistema: recepción de `REBUY` en `Crupier`, aplicación y retransmisión del
  importe.
- Veredicto: aceptar el fix de validación; mantener abierta la matriz de
  protocolo y persistencia antes del cierre de 23.45.
- Commit: `fc82a14de fix(rebuy): validate remote amounts before applying`.

## Escenario reproducible

Un host recibe `REBUY#<nick>#<importe>` de un cliente remoto durante la ventana
de rebuys. El importe puede ser texto, cero, negativo o superar el headroom
disponible (tope de mesa menos stack actual).

En 23.44 el código quitaba el nick de `pending` antes de terminar de validar el
campo. `Integer.parseInt` podía lanzar una excepción después de consumir la
espera, sin registrar una decisión válida ni retransmitir un valor canónico. En
el caso de un importe sobredimensionado se calculaba un límite solo en una rama,
y el mensaje retransmitido podía conservar el valor que había enviado el cliente.

Invariantes:

- solo un entero estrictamente positivo puede solicitar rebuy;
- el importe aplicado y retransmitido está entre `1` y el headroom;
- texto inválido, cero, negativo o sin headroom equivale a no recomprar, sin
  crear fichas;
- host y cliente observan el mismo importe canónico.

## Evidencia TDD

Se añadió `RebuyAmountValidationTest` con tres casos: rechazar importes no
positivos/malformados, limitar al headroom y retransmitir el valor canónico.

Rojo aislado contra un artefacto limpio de 23.44:

```text
mvn -o -f tools/qa/pom-audit.xml -Dtest=RebuyAmountValidationTest test
Compilation failure
cannot find symbol: normalizeRequestedRebuy(String,int)
cannot find symbol: canonicalRemoteRebuyAmount(String,int)
```

La copia limpia se construyó desde `204481c6c`; el POM auxiliar y el worktree
rojo fueron temporales y no forman parte de la rama.

El parche introduce `parseRequestedRebuy`, `normalizeRequestedRebuy` y
`canonicalRemoteRebuyAmount`, y usa el resultado una sola vez para decidir la
animación, la retransmisión y `rebuy_now`. Así una entrada inválida no puede
propagar una excepción ni una cantidad negativa, y una cantidad grande queda
limitada antes de llegar a otro peer.

Verde dirigido contra el jar recompilado de la rama:

```text
mvn -o -f tools/qa/pom-audit.xml -Dtest=RebuyAmountValidationTest test
Tests run: 3, Failures: 0, Errors: 0
BUILD SUCCESS
```

La regresión se amplió con overflow de entero, espacios, `null` y headroom
negativo (`rejectsOverflowWhitespaceAndInvalidHeadroomWithoutCreatingChips`).
La ejecución actual queda en 4/4 verde; el rojo histórico de 23.44 sigue siendo
la compilación fallida por ausencia de los helpers, y no se abre otro cambio de
producción porque el normalizador ya mantiene el importe canónico en 0..headroom.

Vecinos verdes (48 tests): `HandPotCharacterizationTest`, `PotMathTest`,
`BetRulesTest`, `BuyinRulesTest` y `RebuyAmountValidationTest`.

## Riesgos explorados y pendientes

- La prueba es determinista y no necesita UI; cubre la política económica, no
  el cruce real de colas/socket.
- Pendiente smoke host/cliente: comando duplicado, nick desconocido, respuesta
  tardía o reordenada, desconexión durante la ventana, límite de rebuys,
  retransmisión canónica y persistencia en SQLite.
- Revisar aparte el camino `REBUYNOW` local/remoto y la carrera entre el cálculo
  de headroom y la aplicación en `nuevaMano`.
- Si una de esas rutas solo puede validarse ejecutando el juego, dejar la ficha
  manual con pasos exactos; no sustituirla por la prueba pura del normalizador.

No se acepta ampliar producción sin una regresión automatizada o un smoke
determinista que demuestre la diferencia.
