# CoronaPoker libGDX GPU prototype

Prueba visual aislada: no modifica ni sustituye todavía el cliente Swing, el
protocolo, `Crupier`, la criptografía o SQLite.

El JAR de esta prueba está empaquetado para Windows x64 y usa libGDX 1.14.2 con
LWJGL 3.4.1 (backend moderno compatible con el JDK 25 instalado).

## Ejecutar

La forma más rápida es hacer doble clic en `PROBAR_DEMO.cmd`. El lanzador usa el
JAR ya generado y, si no existe, lo compila antes. La demo se abre directamente
a pantalla completa en el monitor que anuncie la frecuencia más alta.

Desde PowerShell, en la raíz del repositorio:

```powershell
& 'C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd' -f prototype-gdx\pom.xml compile exec:java
```

Arranca a pantalla completa y con V-Sync a la frecuencia detectada, sin un
limitador de FPS adicional. Para abrir en ventana:

```powershell
& 'C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd' -f prototype-gdx\pom.xml compile exec:java '-Dexec.args=--windowed'
```

También se genera un JAR autónomo con:

```powershell
& 'C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd' -f prototype-gdx\pom.xml package
java -jar prototype-gdx\target\CoronaPoker-GDX-Prototype.jar
```

## Controles

- `Escape`: salir.
- `F11` o `Alt+Enter`: alternar pantalla completa.
- `R`: repetir la introducción.
- `Espacio`: saltar la intro o lanzar un efecto.
- Clic: lanzar un efecto en la mesa.

La esquina superior derecha muestra FPS, frame time p99, peor frame reciente y
la frecuencia detectada del monitor.
