# Smoke checklists manuales por sprint

Las checklists manuales se complementan con los smoke automatizados de `tools/qa/src/test/java/com/tonikelope/coronapoker/smoke/`.

## Filosofía

Cada checklist tiene **5-10 minutos máximo**. Pasos binarios (OK / no OK). NO "juega y a ver qué" — pasos focalizados a los cambios concretos del sprint.

Se ejecutan:
- **Antes** de mergear una rama `sprint-*` a master.
- **Solo** los pasos cuya área cubre el sprint en cuestión.
- El smoke automatizado cubre lo posible; esta checklist cubre lo que solo puede observar un humano (UI, audio, timing visual, identicons, animaciones, reconexión real).

## Índice

| Sprint | Checklist | Cuándo |
|---|---|---|
| 1 | `sprint-1-security.md` | Tras fixes de RCE/OOM/path traversal/handshake |
| 2 | `sprint-2-bugs.md` | Tras fixes de partial_raise_cum, TOFU, IdentityManager, ACTION |
| 3 | `sprint-3-concurrency.md` | Tras fixes de chapuzas concurrencia |
| 4 | `sprint-4-performance.md` | Tras fixes SQL/render/arraycopy |
| 5 | `sprint-5-memory-leaks.md` | Tras fixes de leaks UI/Image/Listener |
| 6 | `sprint-6-windows-hardening.md` | Tras fixes Windows-específicos |
| 7 | `sprint-7-telemetry.md` | Tras LatencyDot + counter reconexiones |

## Cómo usar una checklist

1. Abrir el fichero `.md` correspondiente al sprint que vas a mergear.
2. Tener la rama del sprint mergeada localmente en una compilación limpia (`mvn install -DskipTests` en raíz).
3. Lanzar el juego (NetBeans Run o JAR ejecutable).
4. Ir paso a paso. Marcar `[ ]` → `[x]` conforme valida cada uno.
5. Si CUALQUIER paso falla:
   - Anotar el síntoma exacto.
   - NO mergear a master.
   - Revertir el commit específico que introduce el problema (`git revert <sha>`).
   - Reportar al chat para diagnóstico.
6. Si TODOS los pasos pasan: aprobar el merge.

## NO ejecutar checklists de otros sprints sin necesidad

Cada checklist está pensada para validar EL sprint que cubre, no para regresión completa. Ejecutar la de Sprint 4 sobre un cambio del Sprint 1 es perder el tiempo.

Si un sprint cambia el área de otro (raro), la checklist del primer sprint lo indicará en su cabecera.
