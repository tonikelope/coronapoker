# Smoke harness: invariantes rápidas para refactor

Este paquete contiene los **smoke tests de invariantes** que se ejecutan antes de
mergear cambios de código (ver la sección **Testing** del `README.md` para las
lanes y el orden de ejecución).

## Filosofía

Son tests que **NO miden calidad** (eso lo hacen los `Baseline*` / `Multiway_*` del paquete `bot/harness/` a 10.000 manos por matchup). Miden **que el código no se rompió**: chip conservation, ausencia de NaN/Inf, stack no negativo, contadores monotónicos, ausencia de excepciones.

Diseñados para responder UNA pregunta: *después de mi cambio, ¿el flujo básico del juego sigue funcionando?*

## Cuándo ejecutarlos

- **Después de cualquier cambio en `Crupier.java`, `Bot.java`, `bot/*` o cualquier código que afecte al flujo de mano.**
- Antes de mergear cualquier rama `sprint-*` a master.
- Los smoke rápidos SÍ se ejecutan automáticamente con el lane rápido: el `pom.xml` de la suite añade `**/*Smoke.java` a los `includes`. `GameFlowSmoke` está marcado `@Tag("slow")`: queda fuera del lane por defecto y sólo corre con `-P qa-bots` (nunca con los agregados `qa-heavy`/`qa-release`).

## Cómo ejecutar (solo los smoke, sin pisar la máquina)

```powershell
mvn -f tools/reactor/pom.xml -o verify -P qa-fast '-Dtest=*Smoke' '-Dsurefire.failIfNoSpecifiedTests=false'
```

El filtro anterior corre los smoke rápidos pero **SALTA `GameFlowSmoke`**: su `@Tag("slow")` lo excluye del lane por defecto. Para ejecutar sólo ese smoke opt-in usa `-P qa-bots '-Dtest=GameFlowSmoke' '-Dsurefire.failIfNoSpecifiedTests=false'`; `qa-heavy` y `qa-release` lo excluyen deliberadamente.

**Tiempo estimado:** los smoke rápidos, unos segundos; `GameFlowSmoke` añade hasta ~30 s (su objetivo declarado en el Javadoc de la clase).

## Qué NO está aquí (intencionalmente)

- Tests de calidad/equity del bot → `bot/harness/`.
- Tests de cripto SRA → `sra/`.
- Tests de protocolo de red real (sockets) → existen en el paquete `net/`: framing, stall/back-pressure y cola de envío (`SocketFramingIntegrationTest`, `SocketStallIntegrationTest`, `NetClientQueueTest`, `WireFrameTest`, …). Las partidas multijugador completas se ejecutan en JVM separadas mediante `tools/qa/real-game-e2e.cmd`; consulta `docs/TESTING.md`.
- Comprobaciones puramente visuales de pintura/layout Swing → inspección manual; las transiciones funcionales de Swing, sockets y `Crupier` sí están automatizadas por el simulador real.

## Estructura

El lane `qa-fast` ejecuta por defecto todos los smoke de la tabla mediante el reactor, salvo `GameFlowSmoke`, que pertenece a `qa-bots` y sólo corre con ese perfil.

| Clase | Qué valida | Lane |
|---|---|---|
| `GameFlowSmoke` | Bot engine + flujo de juego en 3/6/9 seats y las 3 difficulties (vía `MultiwaySimulator`): chip conservation, sin NaN/Inf, stack ≥ 0, contador de mano monotónico, winners válidos. 4 métodos `@Test` | qa-bots |
| `HandEvaluatorSmoke` | Evaluador `Hand.calcularMejorJugada`: los 10 rankings + edge cases (wheel A-5, kickers, full vs trío+pareja, escalera de color). Se salta si el JVM es headless | rápido |
| `RecoverSettingsSchemaSmoke` | Esquema único de recovery: round-trip ANTE/STRADDLE y rechazo de filas parciales | rápido |
| `GamePresetRoundTripSmoke` | Contrato `GamePreset`: round-trip de cada ajuste de nueva partida (incl. estructura de ciegas), el registro persiste renombrados/borrados, entradas corruptas se saltan | rápido |
| `I18nBundleIntegritySmoke` | Chequeos estructurales de los bundles de traducción (claves usadas sin bundle, forma de los ficheros) | rápido |
| `IdentityKeypairAclSmoke` | `IdentityManager.writeKeypair` deja el privkey con ACL owner-only (0600 POSIX / una sola ACE en Windows); el pubkey existe | rápido |
| `LatencyDotSmoke` | Mapping latencia→color de `LatencyDot`: umbrales exactos, latencia negativa → rojo, edad > stale → gris | rápido |
| `MisdealRefundOrderSmoke` | Conservación de dinero al anular una mano durante el settlement (modelo-documentación ejecutable, NO red de seguridad del código real) | rápido |
| `PropertiesResilienceSmoke` | Los dos fallos del fichero de propiedades que eran fatales al arranque (escape unicode roto → `IllegalArgumentException`; fichero ilegible); fija el comportamiento del JDK del que depende el fix | rápido |
| `ReadBoundedLineSmoke` | `Helpers.readBoundedLine`: recorte LF/CR-LF, semántica de EOF, cap por nº de chars, chars del wire format limpios | rápido |
| `RecoveryObjectFilterSmoke` | Whitelist `ObjectInputFilter` sobre RECOVERDATA: tipos permitidos deserializan; clases ajenas (`File`, `ArrayList`) y payload sobredimensionado se rechazan | rápido |
| `SafeNickForFilenameSmoke` | `Helpers.safeNickForFilename`: neutraliza path traversal / ADS / control chars, prefija nombres reservados de Windows, capa a 32 chars, null/"" → "user" | rápido |
| `SynthesizeFoldActionSmoke` | `Crupier.synthesizeExitFoldAction`: deja el `action[]` en FOLD canónico cuando un peer se va, sin absorb/broadcast; defensivo ante input inválido | rápido |
| `TelemetryWireFormatSmoke` | Wire format TELEMETRY: round-trip ts + mapa de peers, nicks con chars conflictivos, payload malformado tolerado | rápido |
| `TofuResolverOutcomeSmoke` | Integridad del outcome de `TOFUResolver`: `CHANGED` no se enmascara como `NEW` cuando el UPDATE falla (happy paths NEW/MATCH/CHANGED + fallos) | rápido |
| `WriteStringAtomicSmoke` | `Helpers.writeStringAtomic`: crear/sobrescribir, sin `.tmp` huérfano, null rechazado, UTF-8/saltos de línea byte-for-byte | rápido |

## Cómo añadir nuevos smoke

Cuando un Sprint introduce un cambio que el smoke actual NO cubre:

1. Crear `XxxSmoke.java` en este paquete (nombre acaba en `Smoke`).
2. Documentar arriba en la clase qué escenario valida.
3. Mantener tiempo por método **< 30 s**.
4. Asserts deben ser **observables y específicos** (no "no crashea", sino "después del flop la apuesta es < pot").
5. Añadirlo a la tabla de arriba.
