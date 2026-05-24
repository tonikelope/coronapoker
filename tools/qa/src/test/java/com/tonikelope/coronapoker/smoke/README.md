# Smoke harness — invariantes rápidas para refactor

Este paquete contiene los **smoke tests de invariantes** que se ejecutan antes de mergear cualquier sprint de refactor (ver `docs/refactoring-audit-2026-05-v2.md`).

## Filosofía

Son tests que **NO miden calidad** (eso lo hacen los `Baseline*` / `Multiway_*` del paquete `bot/harness/` a 25,000 manos por matchup). Miden **que el código no se rompió**: chip conservation, ausencia de NaN/Inf, stack no negativo, contadores monotónicos, ausencia de excepciones.

Diseñados para responder UNA pregunta: *después de mi cambio, ¿el flujo básico del juego sigue funcionando?*

## Cuándo ejecutarlos

- **Después de cualquier cambio en `Crupier.java`, `Bot.java`, `bot/*` o cualquier código que afecte al flujo de mano.**
- Antes de mergear cualquier rama `sprint-*` a master.
- NO se ejecutan automáticamente con `mvn test` — hay que pedirlo explícito.

## Cómo ejecutar (solo los smoke, sin pisar la máquina)

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.1.8-hotspot"
cd tools\qa
& "C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd" -o test -Dtest='*Smoke'
```

**Tiempo estimado:** ~90 segundos en una máquina de desarrollo razonable.

## Qué NO está aquí (intencionalmente)

- Tests de calidad/equity del bot → `bot/harness/`.
- Tests de cripto SRA → `sra/`.
- Tests de protocolo de red real (NetServer/NetClient con sockets) → no existen aún; smoke manual por checklist en `docs/smoke-checklist/`.
- Tests UI (Swing) → no automatizables sin Robot framework / AWT headless; smoke manual.

## Estructura

| Clase | Qué valida | Tiempo |
|---|---|---|
| `GameFlowSmoke` | Bot engine + game flow en 3/6/9 seats + las 4 difficulties. Chip conservation, NaN/Inf, stack ≥ 0, monotonicidad, winners válidos | ~90 s (4 métodos) |

## Cómo añadir nuevos smoke

Cuando un Sprint introduce un cambio que el smoke actual NO cubre:

1. Crear `XxxSmoke.java` en este paquete (nombre acaba en `Smoke`).
2. Documentar arriba en la clase qué escenario valida.
3. Mantener tiempo por método **< 30 s**.
4. Asserts deben ser **observables y específicos** (no "no crashea", sino "después del flop la apuesta es < pot").
5. Añadirlo a la tabla de arriba.
