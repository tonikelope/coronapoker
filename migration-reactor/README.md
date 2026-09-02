# Reactor transitorio de migración

Este reactor prepara la estructura de CoronaPoker 24.11 sin convertir todavía el `pom.xml` clásico de la raíz en agregador y sin cambiar lógica de juego.

## Fuentes únicas durante la transición

- `coronapoker-swing` compila directamente `../src/main/java`; no mantiene una copia del cliente clásico.
- `coronapoker-gdx` compila directamente `../prototype-gdx/src/main/java`. En particular utiliza íntegramente `CoronaPokerGdxDemo.java`, cuya referencia visual es el commit `627c71e4f`; no existe un renderer de mesa alternativo dentro del módulo.
- `coronapoker-assets` empaqueta directamente `../src/main/resources` y `../prototype-gdx/mod`; los frontends no mantienen copias propias.
- `coronapoker-core` está vacío salvo por su declaración de paquete. La extracción neutral empieza en la fase 2 y está protegida desde ahora contra imports Swing, AWT y libGDX.

Estas rutas externas son una excepción transitoria deliberada. Se retirarán cuando las fuentes se muevan a los módulos definitivos, registrando antes cada origen y destino como exige el plan maestro.

## Build

Desde la raíz del repositorio:

```powershell
& 'C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd' `
  '-Dmaven.repo.local=C:\Users\Antonio\.m2\repository' `
  -B -f migration-reactor\pom.xml clean verify
```

El reactor produce:

```text
dist/CoronaPoker-24.11-swing.jar
dist/CoronaPoker-24.11-gdx.jar
```

El JAR GDX de esta fase sigue siendo la demo ejecutable, no la aplicación completa. Debe conservar exactamente el renderer aprobado. La navegación completa GDX corresponde a la fase 7 y la sustitución del guion simulado por eventos reales corresponde a la fase 8.

## Validación

La suite `coronapoker-qa` comprueba la dirección de dependencias, los imports prohibidos del core y que el módulo GDX reutiliza la fuente canónica sin copiarla. Además del build del reactor se debe ejecutar la suite clásica de `tools/qa`.

Un `BUILD SUCCESS` no valida fidelidad visual, audio ni frame pacing. El arranque manual en ventana/pantalla completa y las capturas comparativas siguen siendo criterios independientes.
