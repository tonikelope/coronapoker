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
- `R`: reiniciar la mano simulada.
- `I`: repetir la introducción.
- `Espacio`: saltar la intro o lanzar un efecto.
- Clic: lanzar un efecto en la mesa.

La esquina superior derecha muestra FPS, frame time p99, peor frame reciente y
la frecuencia detectada del monitor.

El tapete usa la baraja Goliat normal (la misma resolución adecuada para juego),
con mipmaps y filtrado trilineal para que la reducción sea limpia. Las esquinas
se recortan en GPU; las imágenes HQ quedan reservadas para un futuro visor de
cartas ampliadas.

El destape reproduce en GPU el giro de perspectiva de CoronaPoker: rotación
lineal de 180 grados en 620 ms, silueta trapezoidal y cambio de reverso a cara
al cruzar los 90 grados. Las apuestas vuelan como fichas individuales; los
montones se reservan para representar el bote y permanecen estables.

Después de la introducción se reproduce en bucle una mano visual de unos 40 segundos.
No se dibuja una mesa ovalada: el tapete verde ocupa toda la pantalla y los
jugadores quedan anclados a sus bordes como en CoronaPoker, con avatares
discretos y cartas grandes. El barajado Goliat aparece a casi 1.000 píxeles de
ancho y con su transparencia original.

El reparto lanza desde el mazo central estrictamente una carta cada vez, con un
vuelo curvo largo, sombra y rotación, en orden alrededor de los nueve asientos;
sólo después de completar la primera vuelta comienza la segunda.

Las acciones ya no reproducen los GIF originales de `check`, `bet`, `call` y
`fold`: son cinemáticas nativas en tiempo real, con foco del asiento, ondas,
partículas, fichas individuales, trayectorias curvas e impactos contra el bote.
La demo también actualiza stacks y bote y destapa flop, turn y river por separado.
Termina con un showdown completo: dos manos se giran con la animación de
CoronaPoker, se muestran sus jugadas, se ilumina al ganador y el bote vuela
hacia él. El fondo reutiliza `tapete_verde.jpg`, teselado como en CoronaPoker.
