# REFERENCIA VISUAL ARCHIVADA — NO ES EL PROGRAMA

Esta carpeta conserva la demo GDX aprobada como referencia histórica de diseño,
geometría y animaciones. Está fuera del reactor Maven del producto, no se compila
en los JAR oficiales y no debe usarse para probar la aplicación actual.

El JAR de esta prueba está empaquetado para Windows x64 y usa libGDX 1.14.2 con
LWJGL 3.4.1 (backend moderno compatible con el JDK 25 instalado).

## Comportamiento conservado como referencia

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

Después de la introducción se reproduce en bucle una mano visual de unos 44 segundos.
No se dibuja una mesa ovalada: el tapete verde ocupa toda la pantalla y los
jugadores quedan anclados a sus bordes como en CoronaPoker, con avatares
discretos y cartas grandes. El barajado Goliat se dibuja a su tamaño nativo
exacto de 960x540 píxeles físicos, independientemente de la resolución del
monitor, sin ampliación ni reducción y con su transparencia original.

El mazo está junto al dealer. Desde allí se lanza estrictamente una carta cada
vez, con un vuelo curvo largo, sombra y rotación, en orden alrededor de los nueve
asientos; sólo después de completar la primera vuelta comienza la segunda. Al
terminar las dos vueltas, el dealer coloca también las cinco cartas comunitarias
boca abajo, una a una y con vuelo propio.

Las acciones ya no reproducen los GIF originales de `check`, `bet`, `call` y
`fold`: son cinemáticas nativas en tiempo real, con foco del asiento, ondas,
partículas, fichas individuales, trayectorias curvas e impactos contra el bote.
Cada jugador tiene un stack de fichas separado de sus cartas y las apuestas
salen visualmente desde ese stack. Las cinemáticas especiales de ALL-IN se
conservan como GIF y usan exactamente la geometría de `GifAnimationDialog` de
CoronaPoker.

La parte inferior es el HUD del jugador local: estado del turno, stack, tamaños
rápidos, `FOLD`, `CHECK`, `CALL` y `BET`; durante su acción se activa y resalta
la opción correspondiente.
La demo también actualiza stacks y bote y destapa flop, turn y river por separado.
Termina con un showdown completo: dos manos se giran con la animación de
CoronaPoker, se muestran sus jugadas, se ilumina al ganador y el bote vuela
hacia él. El fondo reutiliza `tapete_verde.jpg`, teselado como en CoronaPoker.
