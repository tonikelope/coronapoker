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
- El Short arranca automáticamente silenciado y sin forzar subtítulos. El
  perfil anónimo del reproductor vive solo en memoria para no heredar una
  preferencia antigua de subtítulos. La
  barra propia de CoronaPoker muestra el título del resultado, anterior,
  siguiente y cerrar/minimizar.
- La ventana conserva posición y tamaño entre Principal y Sala de espera. Es
  arrastrable y redimensionable en formato vertical 9:16; el tirador inferior
  se pinta por encima del vídeo y cambia el cursor para hacer visible esa
  posibilidad. El marco usa un borde oscuro discreto, sin la franja cian.
- Abrir un modal pausa y oculta el vídeo sin destruir la sesión. Al cerrar el
  modal continúa desde el mismo punto.
- Minimizar guarda periódicamente el vídeo, el segundo aproximado, el estado de
  silencio y el volumen; después cierra el navegador y libera la textura. El
  icono flotante de la esquina inferior derecha vuelve a crear la sesión desde
  ese punto y reaplica el estado de audio cuando el reproductor empieza.
- Antes de crear la mesa se aplica el mismo cierre de sesión y liberación de
  textura. Al volver de la timba se restaura la ventana si estaba abierta.
- Popups, menús contextuales y navegaciones de nivel superior se bloquean. La
  página de resultados nunca se presenta como navegador al usuario.

## Aislamiento

JCEF pinta en un búfer BGRA sin ventana (`OSR`). El callback de CEF solo copia
memoria; la subida de la textura OpenGL ocurre en el hilo de renderizado de
libGDX. El motor CEF permanece inicializado hasta salir de CoronaPoker porque
JCEF no admite de forma segura terminar y reinicializar el proceso nativo. Las
sesiones de navegador y sus texturas sí se destruyen al minimizar o entrar en
la mesa.

La extracción de resultados de búsqueda no usa una clave de API y depende del
documento público de resultados de YouTube. Es deliberadamente un componente
aislado y tolerante a fallos: si el formato cambia o la red falla, el menú y la
sala siguen operativos y el error queda limitado al panel del vídeo.
