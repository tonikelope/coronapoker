# Experimento de Shorts de póker (JCEF OSR)

Esta rama integra el prototipo JCEF sin modificar la lógica de la partida. El
reproductor aparece únicamente en la pantalla principal y en la sala de espera.

## Comportamiento

- El ajuste `Apariencia > Animaciones y efectos > Mostrar Shorts de póker en
  principal y sala de espera` está activado por defecto.
- La lista contiene únicamente resultados Shorts y conserva el orden anónimo
  de relevancia devuelto por YouTube. La búsqueda añade español o inglés según
  el idioma del juego para priorizar contenido en ese idioma. Comienza por el
  primer Short reproducible; YouTube decide la combinación de popularidad y
  actualidad de ese orden.
- Los resultados retirados, privados, incompatibles o que prohíben la
  reproducción embebida se marcan y se saltan automáticamente. Los botones
  anterior y siguiente también evitan los resultados ya descartados.
- El Short arranca automáticamente silenciado y sin forzar subtítulos. Los
  controles propios de YouTube están ocultos; la navegación y el cierre se
  realizan desde la barra de CoronaPoker. El
  perfil anónimo del reproductor vive solo en memoria para no heredar una
  preferencia antigua de subtítulos. La
  barra propia de CoronaPoker muestra el título del resultado, anterior,
  siguiente y cerrar.
- La ventana conserva posición y tamaño entre Principal y Sala de espera. Es
  arrastrable y redimensionable en formato vertical 9:16; el tirador inferior
  se pinta por encima del vídeo y cambia el cursor para hacer visible esa
  posibilidad. Su tamaño inicial compacto corresponde al mínimo de la ventana
  y escala con el `FitViewport` 1920x1080, por lo que conserva la misma
  proporción visual en 1080p, 1440p y 2160p. El marco usa un borde oscuro
  discreto, sin la franja cian.
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
  textura. Al volver de la timba se restaura la ventana si estaba abierta.
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
OSR en esa plataforma.

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
