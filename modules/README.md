# Módulos del producto CoronaPoker 24.11

Este es el reactor Maven del producto. Los ejecutables Swing y GDX se generan
desde aquí y se depositan exclusivamente en `../target`. El único JAR que queda
en la raíz es `../coronaupdater.jar`, porque el actualizador lo requiere allí.

## Fuentes únicas durante la transición

- `coronapoker-swing` compila directamente `../src/main/java`; no mantiene una copia del cliente clásico.
- `coronapoker-gdx` contiene todo el frontend GDX activo. No compila ni ejecuta
  código de la demo archivada.
- `coronapoker-assets` empaqueta directamente `../src/main/resources`; los mods instalables permanecen externos y no se incrustan en el JAR oficial.
- `coronapoker-core` compila las fuentes neutrales compartidas y está protegido
  contra imports Swing, AWT y libGDX.

La demo aprobada se conserva únicamente como referencia visual en
`../reference/gdx-demo` (commit de referencia `627c71e4f`) y está fuera del build.

Estas rutas externas son una excepción transitoria deliberada. Se retirarán cuando las fuentes se muevan a los módulos definitivos, registrando antes cada origen y destino como exige el plan maestro.

## Build

Desde la raíz del repositorio:

```powershell
& 'C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd' `
  '-Dmaven.repo.local=C:\Users\Antonio\.m2\repository' `
  -B -f modules\pom.xml clean verify
```

El reactor produce los dos ejecutables en el único directorio de artefactos de
la raíz del repositorio:

```text
target/CoronaPoker-24.11-swing.jar
target/CoronaPoker-24.11-gdx.jar
```

El JAR GDX se construye exclusivamente desde el frontend real. La demo archivada
es un contrato visual y de animaciones, no código del producto.

## Validación

La suite `coronapoker-qa` comprueba la dirección de dependencias, los imports
prohibidos del core y que GDX no depende de la demo. Además del build del reactor
se debe ejecutar la suite clásica de `tools/qa`.

Un `BUILD SUCCESS` no valida fidelidad visual, audio ni frame pacing. El arranque manual en ventana/pantalla completa y las capturas comparativas siguen siendo criterios independientes.
