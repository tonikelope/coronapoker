# Sprint 8 — Arquitectura: DEFERIDO

**Razón:** Los refactors de arquitectura del informe v2 son demasiado grandes para aplicar sin smoke manual del autor. Aplicarlos a ciegas tendría probabilidad alta de regresión silenciosa que ni el smoke automatizado ni la batería manual mínima detectarían a tiempo.

## Items deferidos y por qué cada uno necesita smoke manual

### 🟠-14 Extract HandEvaluator from Crupier (~470 LOC)
- Mover toda la lógica de `calcularJugadas`, `calcularGanadores`, `desempatar*` (5 métodos), `monteCarlo`, `ganaPorUltimaCarta`, `badbeat`.
- **Riesgo:** estos métodos tienen 470 líneas de tiebreakers de poker. Una sutileza de off-by-one en kickers o orden de comparación corrompe pagos al ganador y nadie lo detecta hasta showdowns reales.
- **Pre-requisito:** Tests AAA exhaustivos de `Hand`/`desempatar*` (🟢-20 del informe v2) ANTES del extract.

### 🟠-15 Helper `awaitCommand` para 33 bucles drain
- Refactor del patrón `do { synchronized(received_commands) { ... } if (!ok) wait(WAIT_QUEUES); } while...` que está repetido 33 veces en Crupier.
- **Riesgo:** un bug en el helper afecta a 33 sitios simultáneamente. Y los 33 sitios tienen variaciones sutiles (qué hace en isExit, qué hace con rejected, etc.) — un helper que no cubra una variación rompe el sitio correspondiente.

### 🟠-16 ClientCommandRouter — extract del switch 35 cases en cliente()
- Convertir el switch monolítico de WaitingRoomFrame.cliente() en handlers separados.
- **Riesgo:** cada case tiene nesting de 5-7 try/catch dependientes del lexical scope (variables outer-final, closures sobre partes_comando, etc.). Romper el lexical scope rompe el handler.

### 🟠-17 GameSettings extract (~30 static volatile)
- Sacar los ~30 `public static volatile` de GameFrame (CIEGA_PEQUEÑA, BUYIN, RABBIT_HUNTING, BARAJA, LANGUAGE, ...) a una clase GameSettings con delegating properties.
- **Riesgo:** estos campos se acceden desde cientos de sitios. Cualquier callsite que lea durante el reset (transición) puede ver estado inconsistente.

### 🟠-18 Crupier Demeter violations (35 cadenas `getTapete().getCommunityCards()...`)
- Añadir métodos públicos en GameFrame/CommunityCardsPanel para reducir las cadenas.
- **Riesgo:** Cada método nuevo tiene que replicar exactamente lo que la cadena hacía. Un orden invertido de calls (revalidate antes que repaint, etc.) cambia comportamiento UI sutil.

### 🟡-19 Constructor injection de gameFrame en Crupier
- Reemplazar `GameFrame.getInstance()` × 631 referencias por `this.gameFrame` dentro de Crupier.
- **Riesgo:** un solo callsite no migrado y aparece NPE en runtime. 631 puntos a verificar.

### 🟡-18 LocalPlayer/RemotePlayer → AbstractPlayerPanel
- Extract de ~30 métodos comunes.
- **Riesgo:** NetBeans Form Editor (.form XML) puede no soportar herencia limpia. Romper el .form rompe la UI silenciosa.

## Cuándo retomar

Sesión dedicada CON el autor disponible para:
1. Manual smoke después de CADA extract (no batch).
2. Validar visualmente UI antes/después.
3. Comparar logs de partidas largas para detectar regresiones sutiles.

Hasta entonces, Sprint 8 queda **explícitamente deferido**. El código actual sigue siendo god-classes pero funcional. La deuda arquitectónica documentada en `docs/refactoring-audit-2026-05-v2.md` no aumenta — simplemente no se reduce.

## Items SAFE de Sprint 8 que SÍ se podrían aplicar sin smoke manual

NINGUNO de los items 🟠 de Sprint 8 es seguro sin smoke. El único que sería casi-seguro es:

- **Test coverage gap en Hand.calcularMejorJugada** (🟢-20): añadir tests AAA. Esto NO modifica producción; sólo crea red de seguridad. Lo dejo aquí como TODO para una sesión futura donde el autor pueda revisar los casos de test antes de fiarse de ellos.
