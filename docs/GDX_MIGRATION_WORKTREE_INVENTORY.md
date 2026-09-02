# Inventario inicial de la migración completa a libGDX

Fecha del inventario: 2026-09-02<br>
Rama protegida de producto: `master` en `9497f1e25974fc9da349cc5ec2c1dab626918cab`<br>
Referencia visual aprobada: `feature/libgdx-prototype` en `627c71e4fff2c223ab594e880ab6fa8551703c48`<br>
Rama de integración preservada: `feature/gdx-demo-live` en `6fca4da3e363fbe8740a519f18d9cbb0e7b970ff`<br>
Rama hija de arquitectura: `feature/gdx-full-migration`

Este inventario cierra la clasificación previa a la reorganización. No se ha borrado, movido ni restaurado ningún archivo de usuario ni ningún stash.

## Referencias protegidas

| Referencia | Estado verificado | Decisión |
| --- | --- | --- |
| `master` / `origin/master` | Ambos apuntan a `9497f1e25` | No trabajar ni integrar directamente aquí. |
| `feature/libgdx-prototype` | Apunta a `627c71e4f` | Conservar como contrato visual ejecutable; no reescribir su historial. |
| `feature/gdx-demo-live` | Apunta a `6fca4da3e` | Conservar como punto de integración experimental. |
| `archive/gdx-experimental-bridge-20260902` | Apunta a `6fca4da3e` | Copia nominal adicional del último puente confirmado. |
| `feature/gdx-full-migration` | Creada desde `6fca4da3e` | Continuar aquí el reactor y la extracción arquitectónica. |

## Respaldo de assets

Se ha creado un respaldo local, sin sustituir las fuentes, de todo `prototype-gdx/mod`:

- archivo: `local-audit/gdx-assets-backup-20260902.zip`;
- contenido: 374 entradas, 169,78 MiB sin comprimir;
- tamaño del ZIP: 170.098.447 bytes (162,22 MiB);
- SHA-256: `D0F6E132154321492075CEBC2BA8062A173A124BEDC9961620CBA796A9EBAAF9`.

El ZIP queda deliberadamente fuera de Git por la regla global `*.zip`. Es una copia local de seguridad, no la fuente canónica futura. Antes de centralizar assets se deberá conservar el directorio original y verificar otra vez el hash del respaldo.

## Working tree inicial

### Conservar

| Ruta o grupo | Evidencia | Destino previsto |
| --- | --- | --- |
| `docs/CORONAPOKER_GDX_FULL_MIGRATION_PLAN.md` | Plan maestro presente y aún no rastreado al iniciar este inventario. | Versionar como especificación de la migración. |
| `prototype-gdx/mod/decks/pepsiman` | 107 archivos no rastreados (26,50 MiB), además de 61 recursos HQ ya rastreados en el prototipo. | Fuente de assets comunes tras revisar licencia, estructura y duplicados. |
| `prototype-gdx/mod/decks/pinup` | 163 archivos no rastreados (52,38 MiB). | Preservar como mod entregado por el usuario. |
| `prototype-gdx/mod/cinematics/allin` | 20 archivos no rastreados (65,42 MiB). | Preservar y llevar a la fuente única de assets. |
| `prototype-gdx/mod/sounds` | 19 archivos no rastreados (4,86 MiB). | Preservar y catalogar por evento de audio. |
| `prototype-gdx/mod/init.png`, `mod.png`, `mod.xml` | 3 archivos no rastreados (2,14 MiB). | Preservar como metadatos y presentación del mod. |
| `src/main/java/.../table/*` | Contratos neutrales y puente ya confirmados en `619c77065` y `6fca4da3e`. | Mover gradualmente a `coronapoker-core`, manteniendo sus pruebas. |
| Pruebas de contratos bajo `tools/qa` | Cubren modo, routing, eventos, bridge y registro. | Conservar; adaptar al reactor y ampliar con reglas de arquitectura. |
| `TableRendererProvider` | El build reveló que `6fca4da3e` lo referenciaba pero no lo había versionado; la definición neutral exacta seguía preservada en `stash@{0}`. | Recuperado selectivamente, sin restaurar el stash ni sus adaptadores experimentales. |

Los assets no rastreados suman 313 archivos y 151,29 MiB. La diferencia hasta las 374 entradas del respaldo corresponde principalmente a los 61 archivos ya rastreados dentro de `prototype-gdx/mod`.

### Reescribir o retirar tras disponer del reemplazo

| Archivo o concepto | Motivo | Condición de retirada |
| --- | --- | --- |
| `TableRendererMode` y selector en `AppearanceSettingsPanel`/`SettingsDialog` | El destino son dos ejecutables, no un renderer intercambiable dentro de la misma aplicación. | Existen `SwingLauncher` y `GdxLauncher` funcionales. |
| Integración temporal de `GameFrame` con `TableEventBridge` | `GameFrame` no puede ser el controlador oculto de GDX. | La sesión y el presenter neutrales controlan ambos frontends. |
| `TableRendererRegistry` dinámico | Puede dejar de ser necesario al separar los dos JAR. | El wiring de cada launcher está fijado y probado. |
| Cualquier snapshot/event source del stash que lea widgets Swing o haga polling | Infiere estado visual en vez de recibir eventos causales. | El core emite directamente cada transición equivalente. |

`src/main/java/com/tonikelope/coronapoker/GameFrame.java` aparecía modificado en el working tree, pero `git diff`, `git diff --raw` y `git diff --numstat` no mostraron cambios semánticos. No se ha añadido al índice ni normalizado: se preserva tal cual hasta determinar el origen de su marca de fin de línea.

### Regenerable; no borrar todavía

| Ruta | Archivos | Tamaño | Clasificación |
| --- | ---: | ---: | --- |
| `prototype-gdx/.m2` | 676 | 32,10 MiB | Caché Maven local. Ignorada de forma específica. |
| `renderer-gdx/.m2` | 210 | 12,49 MiB | Caché Maven local. Ignorada de forma específica. |
| `renderer-gdx/target` | 3.028 | 776,11 MiB | Salida de compilación ya ignorada por `target/`. |
| `prototype-gdx/mod/.pegi18_warning` | 1 | despreciable | Marcador de ejecución local; ignorado de forma específica. |

Al inventariar, `renderer-gdx` no contenía `pom.xml` ni `src`: solamente `.m2` y `target`. Sus clases compiladas y recursos copiados no se consideran una fuente recuperable. No se eliminarán hasta demostrar que el build de reemplazo reproduce lo necesario.

## Clasificación de los commits de frontera

### `619c77065` — `feat(gdx): establish renderer-neutral table boundary`

- Conservar como base conceptual: `TableCommand`, `TableCommandSink`, `TablePresentation`, `TableRenderer`, `TableSnapshot`, `TableVisualEvent` y sus pruebas.
- Reubicar: los contratos que sobrevivan a la auditoría de imports pasarán al módulo core.
- Reescribir después: el selector de renderer y el wiring dentro de `Init`, `GameFrame`, `AppearanceSettingsPanel` y `SettingsDialog`.
- Conservar durante la transición: traducciones y controles Swing, hasta que dos launchers hagan innecesario el selector.

### `6fca4da3e` — `feat(gdx): define complete table event bridge`

- Conservar como base conceptual: `TableEventBridge`, la taxonomía ampliada de `TableVisualEvent` y las pruebas de orden/registro.
- Revisar la semántica de barrera evento por evento antes de conectar `Crupier`.
- Reescribir después: `TableRendererRegistry` si el ensamblado de dos aplicaciones elimina el registro dinámico.
- Retirar después: el attachment temporal desde `GameFrame`, una vez exista una sesión neutral propietaria del lifecycle.

## Stashes

| Stash | Decisión |
| --- | --- |
| `stash@{0}` — `archive experimental GDX bridge before 24.11 clean branch` | Preservar; no restaurar masivamente. Extraer solo piezas revisadas y con procedencia documentada. |
| `stash@{1}` — `wip failed gdx integration before demo-live restart` | Preservar como integración fallida; no restaurar ni borrar durante la migración. |
| Los demás stashes históricos | Fuera del alcance GDX; no tocar. |

## Criterio para pasar al reactor

Se puede empezar el reactor transitorio cuando:

1. este inventario y el plan maestro estén versionados en la rama hija;
2. el respaldo siga presente y su SHA-256 coincida;
3. las cachés estén separadas de fuentes y assets sin haber sido borradas;
4. el JAR clásico se pueda reproducir desde la raíz;
5. la demo se pueda reproducir desde `prototype-gdx` sin afirmar validación visual si no se ha ejecutado y observado.

## Verificación de salida

Ejecutada el 2026-09-02 en Windows con Java 17+ y el Maven de NetBeans, usando `C:\Users\Antonio\.m2\repository`:

| Verificación | Resultado |
| --- | --- |
| SHA-256 del respaldo de assets | Coincide con `D0F6E132154321492075CEBC2BA8062A173A124BEDC9961620CBA796A9EBAAF9`. |
| `mvn -B -DskipTests clean install` en la raíz | `BUILD SUCCESS`; compila 230 fuentes y genera los JAR clásico y con dependencias. |
| `mvn -B clean package` en `prototype-gdx` | `BUILD SUCCESS`; genera `prototype-gdx/target/coronapoker-gdx-prototype-0.1-SNAPSHOT.jar` con nativos LWJGL. |
| `mvn -B -o -f tools/qa/pom.xml test` | `BUILD SUCCESS`; 1.106 pruebas, 0 fallos, 0 errores, 0 omitidas. |

La primera ejecución restringida de QA tuvo un único `AccessDeniedException` al intentar que `CardFlipAnimatorModDeckTest` crease su fixture junto al JAR instalado en `.m2`. La repetición con acceso normal al repositorio local pasó completa; no se clasificó como defecto de producto.

No se ha abierto la demo ni se ha realizado validación visual, auditiva o de frame pacing en monitor de alta frecuencia. El resultado de esta fase prueba reproducibilidad de build y regresión automatizada, no fidelidad visual en ejecución.
