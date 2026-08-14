# Selección de la rama experimental para 23.45

Base de comparación: CoronaPoker 23.44 (`204481c6c`).

## Rescatados

1. **P0 — All-in desconectado.** Un jugador que ya comprometió todo su stack no puede perder el derecho al bote por desconectarse antes del showdown. Se conserva como competidor en `HandPot` y en la lista que resuelve el showdown.
2. **P1 — REBUY remoto.** Un importe malformado, no positivo o superior al margen disponible se convierte en cero o se limita antes de tocar el flujo contable. El host reenvía exclusivamente el valor canónico aceptado.

Ambos cambios tienen regresiones nuevas en la suite QA.

## Validación ampliada obligatoria antes de publicar

La aceptación inicial no cierra estos cambios. Se debe seguir cada flujo hasta
sus límites de producción; si aparece una dependencia no cubierta, el cambio se
amplía o se retira, nunca se da por bueno solo por superar su prueba unitaria.

1. **All-in desconectado:** recorrer preflop, flop, turn y river; desconexión y
   reconexión antes/después de revelar; botes laterales, empate, fold de los
   demás jugadores, Run It Twice, cierre SQLite y recovery. Verificar en cada
   caso conservación exacta de fichas y que solo compite quien comprometió el
   all-in, no un jugador retirado.
2. **REBUY remoto:** recorrer importe válido, cero, negativo, texto inválido,
   overflow, límite de mesa, límite de rebuys, timeout, comando duplicado y
   reconexión. Confirmar en host y clientes que el valor aplicado, el valor
   retransmitido, el estado de espectador y el saldo persistido coinciden.

Estas comprobaciones son bloqueantes para la publicación de 23.45.

## Ya incluidos en 23.44

- La actualización del resaltado de mano tras Rabbit.
- El contador durable de mano durante recovery.
- La protección de recuperación RIT y recorrido acotado de asientos.

No se duplican en 23.45.

## Descartados

- **IWTSTH:** la autorización propuesta es correcta, pero el rechazo no completa un cierre dirigido de la solicitud. No se porta parcialmente.
- **QA, simuladores y refactors de tests:** no modifican producción.
- **Audio, UI, red, stats, SQL/recovery, identidad y seguridad no aislados:** fuera de una release de reglas/economía, o requieren rehacerse como flujos completos según su propia auditoría previa.

La rama experimental queda obsoleta tras esta selección y debe eliminarse localmente.
