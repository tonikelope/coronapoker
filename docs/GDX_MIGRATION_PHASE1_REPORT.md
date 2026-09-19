# Fase 1 — Reactor Maven y builds reproducibles

> Documento histórico. La estructura actual usa `modules/` para el producto,
> `reference/gdx-demo/` para la demo archivada y `target/` para los ejecutables.

Fecha: 2026-09-02<br>
Rama: `feature/gdx-full-migration`<br>
Referencia visual: `627c71e4f`

## Resultado

Se ha creado `migration-reactor` sin convertir el `pom.xml` clásico de la raíz y sin modificar lógica de juego. El reactor contiene los módulos `coronapoker-core`, `coronapoker-assets`, `coronapoker-swing`, `coronapoker-gdx` y `coronapoker-qa`.

Durante esta fase:

- Swing compila directamente las fuentes clásicas de `src/main/java`;
- GDX compila directamente las tres fuentes de `prototype-gdx/src/main/java`;
- `CoronaPokerGdxDemo.java` no se ha copiado ni recreado dentro del módulo;
- `git diff --exit-code 627c71e4f -- prototype-gdx/src/main/java/com/tonikelope/coronapoker/gdxdemo` confirma que las fuentes actuales de la demo coinciden con la referencia aprobada;
- los recursos clásicos y los mods se empaquetan desde `src/main/resources` y `prototype-gdx/mod` mediante un único módulo de assets;
- el core permanece vacío hasta la extracción neutral de la fase 2;
- QA impide imports `java.awt`, `javax.swing` o `com.badlogic.gdx` en core y comprueba la dirección de dependencias y la reutilización de la demo canónica.

Las rutas de fuentes externas al módulo son una excepción transitoria. Se retirarán al mover cada fuente a su módulo final, documentando previamente origen y destino.

## Build reproducible

Comando ejecutado:

```powershell
& 'C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd' `
  '-Dmaven.repo.local=C:\Users\Antonio\.m2\repository' `
  -B -f migration-reactor\pom.xml clean verify
```

Resultado: `BUILD SUCCESS` en los seis proyectos del reactor.

Pruebas del módulo de arquitectura:

```text
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
```

La suite clásica se había ejecutado tras reparar el contrato `TableRendererProvider` y permanece como baseline de esta fase:

```text
Tests run: 1106, Failures: 0, Errors: 0, Skipped: 0
```

## Artefactos

| Artefacto | Tamaño | SHA-256 |
| --- | ---: | --- |
| `dist/CoronaPoker-24.11-swing.jar` | 410,13 MiB | `CBB9B223F8D16C456DFF2F2AED77DF91C48DD1618A7FD3B646E8D77D8B765D8E` |
| `dist/CoronaPoker-24.11-gdx.jar` | 398,69 MiB | `762FBE4CD6FD3287D3CE0F3C3C2CE41AB383ADF754BD6B2183FE870EC3D92EDB` |

Verificación estática:

| Comprobación | Swing | GDX |
| --- | --- | --- |
| `Main-Class` | `com.tonikelope.coronapoker.Init` | `com.tonikelope.coronapoker.gdxdemo.CoronaPokerGdxLauncher` |
| Clase principal incluida | `Init.class` | `CoronaPokerGdxDemo.class` |
| Referencia visual en manifest | no aplica | `627c71e4f` |
| Nativos LWJGL Windows | no | sí |
| PepsiMan, Pinup y cinematics | sí | sí |

## Smoke de arranque

### GDX

Se ejecutó el JAR primero con `--windowed` y después sin argumentos para usar el modo de pantalla completa de la demo. Ambos procesos abrieron la aplicación y confirmaron:

```text
CoronaPoker GPU: 2560x1440 @ 240 Hz
GIF GPU: images/decks/goliat/gif/shuffle.gif | 86 frames | 1720 ms | 960x540
GIF GPU: mod/decks/pepsiman/gif/shuffle.gif | 86 frames | 1720 ms | 960x540
Shuffle audio cutoff: frame 53 at 1040 ms
GIF GPU: cinematics/allin/rounders.gif | 24 frames | 3420 ms | 563x250
```

Después de cada arranque se cerró el proceso de forma controlada. Estos smokes demuestran carga real del JAR en ventana y pantalla completa, además de nativos y recursos; no sustituyen capturas comparativas ni validación manual de aspecto y frame pacing.

### Swing

Se ejecutó el JAR con un `user.home` temporal dentro de `migration-reactor/coronapoker-qa/target`, aislado de la configuración normal del usuario. Llegó a:

```text
Loading SQLITE DB...
CSPRNG OK
Loading GUI Window...
Initialization complete. Ready.
```

Después del arranque se cerró el proceso de forma controlada. No se ha realizado todavía una partida Swing desde este artefacto.

## Incidencias y limpieza

La primera versión de la propiedad `distribution.directory` escribió dos copias generadas un nivel por encima del repositorio. Tras corregir la ruta se reconstruyó el reactor y se eliminaron exclusivamente esas dos copias regenerables. Los artefactos válidos permanecen en `coronapoker/dist`.

Los warnings de recursos/clases solapados del shade plugin coinciden con los ya observados al empaquetar la demo independiente y no impidieron `BUILD SUCCESS`. Deben revisarse antes del empaquetado final de la fase 11.

## Alcance pendiente

Esta fase no declara todavía:

- aplicación GDX completa;
- bootstrap compartido;
- core con reglas extraídas;
- mesa controlada por eventos reales;
- fidelidad visual validada mediante capturas;
- audio o frame pacing validados manualmente;
- compatibilidad entre clientes Swing/GDX.

El siguiente bloque permitido por el plan es la extracción del bootstrap común de la fase 2. La demo visual no se refactorizará hasta la fase 8 y seguirá siendo la referencia ejecutable intacta.
