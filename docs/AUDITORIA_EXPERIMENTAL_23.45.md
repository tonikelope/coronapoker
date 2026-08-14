# Selección de la rama experimental para 23.45

Base de comparación: CoronaPoker 23.44 (`204481c6c`).

## Rescatados

1. **P0 — All-in desconectado.** Un jugador que ya comprometió todo su stack no puede perder el derecho al bote por desconectarse antes del showdown. Se conserva como competidor en `HandPot` y en la lista que resuelve el showdown.
2. **P1 — REBUY remoto.** Un importe malformado, no positivo o superior al margen disponible se convierte en cero o se limita antes de tocar el flujo contable. El host reenvía exclusivamente el valor canónico aceptado.

Ambos cambios tienen regresiones nuevas en la suite QA.

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
