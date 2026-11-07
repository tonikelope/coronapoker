# Vídeos de póker con JCEF

El reproductor aparece en la pantalla principal y en la sala de espera.
Se configura en **Apariencia > Animaciones y efectos > Vídeos de póker**.
Los vídeos dependen del interruptor **Usar animaciones y efectos**.

## Ventana y controles

- El carrusel empieza en **Normales** por defecto. Puedes elegir **Shorts**
  en Ajustes o con el selector inferior del reproductor. Ambos controles
  guardan la misma preferencia. La búsqueda prioriza el idioma del juego y
  el carrusel baraja los resultados al empezar y al completar cada vuelta.
- La estrella añade o quita el vídeo actual de **Favoritos**. La lista reúne
  los dos formatos en el orden en que los guardaste y se conserva al salir.
  Las flechas recorren la lista y el contador muestra la posición y el total.
  La ventana adopta el formato de cada favorito. El selector de formato
  reaparece al volver al carrusel con el valor guardado en Ajustes.
- Arrastra la cabecera para mover la ventana o sus bordes y esquinas para
  cambiar el tamaño. El juego guarda posición y tamaño por separado para
  cada formato. La ventana escala con el área del juego y deja un margen
  antes del borde.
- Cerrar minimiza el reproductor al icono junto al volumen. **Abrir vídeo
  al arrancar** permite empezar con la ventana abierta o solo con el icono.
  **Animación al cerrar/abrir reproductor** controla la transición entre
  ambos estados.
- Una apertura desde el icono empieza silenciada y reanuda el vídeo desde
  la última posición conocida en esa sesión. Los controles de YouTube
  cambian el audio y los subtítulos. Los modales pausan el reproductor.
  El juego libera el navegador al entrar en una mesa y recupera la sesión
  de vídeo al salir si estaba abierta.

La ventana se renderiza a la escala real del juego, incluida la resolución
4K. YouTube decide la calidad según el tamaño, la conexión y el dispositivo.
La API IFrame dejó de admitir el cambio programático con
`setPlaybackQuality`. [Historial oficial de la API](https://developers.google.com/youtube/iframe_api_revision_history)

## Plataformas

Cada JAR contiene sus propios binarios de JCEF, libGDX y LWJGL. Hay paquetes
para Windows x64, Linux x64 y ARM64, y macOS Intel y Apple Silicon. El juego
habilita el reproductor cuando encuentra el recurso nativo que corresponde
al sistema y arquitectura. En Linux, el JCEF incluido requiere acceso a X11 o
XWayland mediante `DISPLAY`, incluso en una sesión Wayland. Si ese acceso
falla, el panel muestra el motivo.

Las comprobaciones de archivos multiplataforma verifican el contenido del
JAR. La reproducción y el audio requieren pruebas en el sistema de destino.
Consulta [Testing and certification](TESTING.md#poker-video-integration-checks)
para las comprobaciones automáticas y nativas.

## Carga y recuperación

Cada ejecución obtiene un catálogo nuevo de YouTube. El juego conserva los
resultados en memoria al minimizar o pasar por una mesa. La búsqueda usa el
documento público de resultados y limita la descarga a 12 MiB y 15 segundos.
Los resultados retirados, privados o incompatibles se saltan. Si la red o
el formato de búsqueda falla, el panel ofrece reintentar y el resto del
juego sigue disponible.

El documento local y la API de YouTube tienen plazos de carga de 30 segundos.
El intercambio de preparación tiene 10 segundos. El inicio de reproducción
tiene 20 segundos y un bloqueo posterior de buffering tiene 45. El
reproductor reintenta y salta vídeos con límites. Tras tres recuperaciones
ofrece un reintento manual. Una pausa voluntaria o un modal no cuenta como
fallo de carga.

## Integración

El servidor HTTP local escucha solo en loopback y usa sockets de `java.base`.
Atiende dos conexiones a la vez con una cola limitada y cierra las conexiones
al liberar la sesión. La página local aloja el iframe de YouTube. Las
ventanas emergentes y las navegaciones externas de nivel superior se bloquean.

JCEF renderiza en un búfer BGRA sin ventana. El callback de CEF copia los
píxeles y el hilo de libGDX actualiza la textura. La instancia nativa de CEF
permanece en el proceso hasta que CoronaPoker termina. Los navegadores y
texturas individuales se liberan al cerrar el reproductor o entrar en la mesa.

El documento carga el iframe con la reproducción automática desactivada y
aplica el mute antes de arrancar el vídeo. El primer `READY` en un proceso
frío prepara el renderer y recarga el documento una vez. Al cambiar un Short,
el puente vigila que el control visual de audio refleje el mute real del
reproductor. La página conserva las decisiones posteriores del usuario.

Para una prueba nativa silenciosa, `-Dcoronapoker.video.silentOutput=true`
suprime la salida de Chromium y conserva el estado del control y la API.
