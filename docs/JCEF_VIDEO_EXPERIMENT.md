# Experimento de Shorts de póker (JCEF OSR)

Esta rama integra el prototipo JCEF sin modificar la lógica de la partida. El
reproductor y su icono aparecen únicamente en la pantalla principal y en la
sala de espera. Durante la partida no aparece ninguno de los dos.

## Comportamiento

- El grupo `Apariencia > Animaciones y efectos > Vídeos de póker` está
  activado por defecto e incluye `Tipos de vídeos`, con `Shorts` como valor
  inicial y `Normales` como alternativa. El interruptor maestro `Usar animaciones y
  efectos` domina todo el sistema: si está apagado no aparecen ventana ni
  icono y no se inician búsqueda, servidor ni navegador.
- La lista contiene Shorts o vídeos normales según el selector. La búsqueda
  añade español o inglés según el idioma del juego para priorizar contenido en ese idioma. El
  lote anónimo que entrega YouTube se baraja al iniciar y se vuelve a barajar
  al completar cada vuelta; al terminar un Short avanza automáticamente. La
  reproducción continúa recorriendo y reordenando los resultados únicos
  obtenidos en esa consulta.
- La lista no se cachea entre ejecuciones de CoronaPoker: cada arranque vuelve
  a consultar YouTube para poder incorporar resultados nuevos. Dentro de una
  misma ejecución sí se conserva en memoria al minimizar o pasar por la mesa.
- Los resultados retirados, privados, incompatibles o que prohíben la
  reproducción embebida se marcan y se saltan automáticamente. Los botones
  anterior y siguiente también evitan los resultados ya descartados.
- La barra de título muestra solo el título del vídeo, sin contador ni total
  de resultados.
- El Short arranca automáticamente silenciado y sin forzar subtítulos. Los
  controles propios de YouTube están ocultos; la navegación y el cierre se
  realizan desde la barra de CoronaPoker. El
  perfil anónimo del reproductor vive solo en memoria para no heredar una
  preferencia antigua de subtítulos. La
  barra propia de CoronaPoker muestra el título del resultado, anterior,
  siguiente y cerrar.
- La ventana conserva posición y tamaño entre Principal y Sala de espera. Es
  arrastrable y redimensionable manteniendo el formato del vídeo; los cuatro
  bordes y las cuatro esquinas aceptan el gesto y muestran el cursor adecuado,
  aunque las zonas son invisibles. No se dibujan marco, sombra ni tirador de
  esquina. Su tamaño inicial compacto corresponde al mínimo de la ventana
  y escala con el `FitViewport` 1920x1080, por lo que conserva la misma
  proporción visual en 1080p, 1440p y 2160p. En la sala el icono queda elevado
  por encima
  de los controles inferiores y su hover está recortado al propio botón. En
  principal y sala el icono tiene el mismo tamaño que el botón de sonido y se
  sitúa inmediatamente a su izquierda.
- Abrir un modal pausa y oculta el vídeo sin destruir la sesión. Al cerrar el
  modal continúa desde el mismo punto.
- Cerrar guarda periódicamente el vídeo, el segundo aproximado, el estado de
  silencio y el volumen; después cierra el navegador, libera la textura y
  persiste el ajuste como desactivado. Cuando el ajuste está desactivado solo
  queda el icono flotante de la esquina inferior derecha. Pulsarlo reactiva y
  guarda el ajuste, vuelve a crear la sesión desde ese punto y reanuda
  automáticamente la reproducción, reaplicando el estado de audio después de
  que el vídeo haya comenzado.
- Antes de crear la mesa se aplica el mismo cierre de sesión y liberación de
  textura. Durante la partida no se muestra ni se crea icono o reproductor. Al
  volver de la timba se restaura exactamente el estado de ventana o icono que
  correspondía.
- Los cambios rápidos con anterior/siguiente conservan el último estado de
  audio elegido. Tras cada carga, el reproductor reconcilia varias veces
  volumen y mute; si YouTube muestra sonido activo con la tubería todavía muda,
  fuerza una única secuencia mute/desmute para recuperar la salida real.
- Popups, menús contextuales y navegaciones de nivel superior se bloquean. La
  página de resultados nunca se presenta como navegador al usuario.

## Aislamiento

JCEF pinta en un búfer BGRA sin ventana (`OSR`). El callback de CEF solo copia
memoria; la subida de la textura OpenGL ocurre en el hilo de renderizado de
libGDX. El motor CEF permanece inicializado hasta salir de CoronaPoker porque
JCEF no admite de forma segura terminar y reinicializar el proceso nativo. Las
sesiones de navegador y sus texturas sí se destruyen al cerrar o entrar en
la mesa.

## Plataformas del artefacto experimental

Se genera un artefacto por plataforma, tal como recomienda JCEF Maven, porque
cada paquete nativo ocupa alrededor de 100 MB. Las combinaciones admitidas por
este experimento son Windows x86-64, Linux x86-64/ARM64 y macOS
x86-64/Apple Silicon. Windows ARM64 queda excluido porque JCEF Maven no admite
OSR en esa plataforma. Cada artefacto incluye además una sola familia de
nativos LWJGL y conserva únicamente los binarios libGDX/libGDX FreeType de su
sistema y arquitectura.

La disponibilidad se comprueba antes de exponer la función: deben coincidir el
sistema operativo, la arquitectura y el recurso nativo incluido en el JAR. Si
alguna condición falla, no aparece el ajuste, no aparece el icono y no se
inician la búsqueda, el servidor local ni JCEF. Esto también evita que la
dependencia base descargue automáticamente un paquete nativo en una plataforma
no admitida.

La extracción de resultados de búsqueda no usa una clave de API y depende del
documento público de resultados de YouTube. Es deliberadamente un componente
aislado y tolerante a fallos: si el formato cambia o la red falla, el menú y la
sala siguen operativos y el error queda limitado al panel del vídeo.
