# Ficha de no-cambio — all-in salido antes de revelar cartas

## Alcance

Familia `P0-001`: un jugador que ya está `ALLIN` puede marcarse como salido
antes de completar el intercambio criptográfico de cartas del showdown.

## Revisión realizada

Se siguieron los filtros de `Crupier` en
`solicitarYRecibirCartasVisuales`, `recibirCartasResistencia` y
`procesarCartasResistencia`. Las ramas que ven `isExit()` no solicitan ni
aplican la clave/carta de un peer que ya no puede demostrar su pocket. El método
`calcularJugadas` comprueba las dos cartas y deja fuera una mano no revelada,
manteniendo el bote como sobrante para la siguiente mano en vez de inventar un
ganador.

## Decisión

No se cambia producción en esta iteración. Hacer que un `ALLIN` salido saltase
los filtros permitiría aceptar plaintext o una clave no verificada justo en el
camino de showdown; no existe en la suite actual un smoke host/cliente que
reproduzca de forma determinista la carrera `ALLIN -> EXIT -> POTCARDS` y
verifique además la autenticidad SRA. La prueba pura de elegibilidad de bote no
demuestra esa propiedad criptográfica.

## Riesgo y seguimiento

- Riesgo funcional: la mano puede quedar sin ganador visible y el dinero se
  arrastra como `bote_sobrante` si el peer no revela sus cartas.
- Riesgo de seguridad si se fuerza la revelación: aceptar cartas no autenticadas
  o filtrar el pocket antes del momento autorizado.
- Prueba pendiente automatizable: smoke de dos procesos con una barrera justo
  después de `ALLIN`, cierre del socket del jugador y verificación de que el
  host no espera indefinidamente, no paga al salido sin prueba y conserva el
  balance.
- Mientras no exista ese smoke, mantener la política actual y repetir la
  comprobación en NetBeans bajo la cuenta real. No sustituirla por una prueba
  manual de UI.

Este caso queda explícitamente anotado como extremo no modificado por falta de
un test adecuado y por riesgo de regresión de seguridad.
