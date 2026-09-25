# Módulos del producto CoronaPoker 24.11

Este es el reactor Maven del producto. Los ejecutables Swing y GDX se generan
desde aquí y se depositan exclusivamente en `../target`. El único JAR que queda
en la raíz es `../coronaupdater.jar`, porque el actualizador lo requiere allí.

## Propiedad física de las fuentes

- `coronapoker-core` contiene la lógica de juego, red, persistencia y contratos de
  presentación neutrales en `coronapoker-core/src/main/java`.
- `coronapoker-swing` contiene exclusivamente el frontend clásico Swing en
  `coronapoker-swing/src/main/java`.
- `coronapoker-gdx` contiene todo el frontend GDX activo. No compila ni ejecuta
  código de la demo archivada.
- `coronapoker-assets` empaqueta directamente `../src/main/resources`; los mods instalables permanecen externos y no se incrustan en el JAR oficial.

El antiguo árbol `../src/main/java` queda vacío. La suite de arquitectura impide
que vuelvan a aparecer fuentes allí, que un archivo Java exista en dos módulos o
que el core importe Swing, AWT o libGDX.

La demo aprobada se conserva únicamente como referencia visual en
`../reference/gdx-demo` (commit de referencia `627c71e4f`) y está fuera del build.

Los recursos compartidos permanecen deliberadamente en `../src/main/resources`:
son datos del producto, no una tercera copia de código Java.

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

La fase `clean` del reactor elimina primero cualquier JAR versionado o log de
smoke antiguo de ese directorio. Debe conservarse en el comando: ejecutar sólo
un módulo con `package` actualiza su JAR, pero no constituye un build limpio de
la distribución completa.

El JAR GDX se construye exclusivamente desde el frontend real. La demo archivada
es un contrato visual y de animaciones, no código del producto.

## Validación

La suite `coronapoker-qa` comprueba la dirección de dependencias, los imports
prohibidos del core y que GDX no depende de la demo. Además del build del reactor
se debe ejecutar la suite clásica de `tools/qa`.

Un `BUILD SUCCESS` no valida fidelidad visual, audio ni frame pacing. El arranque manual en ventana/pantalla completa y las capturas comparativas siguen siendo criterios independientes.
