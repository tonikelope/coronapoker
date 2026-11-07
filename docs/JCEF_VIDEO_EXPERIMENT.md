# Vídeos de póker con JCEF OSR

La integración usa JCEF sin modificar la lógica de la partida. El
reproductor y su icono aparecen únicamente en la pantalla principal y en la
sala de espera. Durante la partida no aparece ninguno de los dos.

## Comportamiento

- El grupo `Apariencia > Animaciones y efectos > Vídeos de póker` está
  activado por defecto e incluye `Tipos de vídeos`, con `Shorts` como valor
  inicial y `Normales` como alternativa. El interruptor maestro `Usar animaciones y
  efectos` domina todo el sistema: si está apagado no aparecen ventana ni
  icono y no se inician búsqueda, servidor ni navegador. Lo mismo ocurre si
  se desactiva específicamente `Vídeos de póker`.
- `Abrir vídeo al arrancar` está activado por defecto. Al apagarlo, el próximo
  arranque muestra solo el icono y no inicia búsqueda ni navegador hasta pulsarlo.
  Necesita que los vídeos y las animaciones estén activados; no puede habilitar
  por sí solo una función desactivada. Se conserva el estado de ventana/icono
  durante los cambios de pantalla de la sesión. Activar manualmente los vídeos
  más tarde sí abre la ventana, independientemente de la opción de arranque.
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
- La cabecera tiene un fondo opaco uniforme, con anterior, siguiente y cerrar.
  Una barra inferior separada contiene la estrella de favorito, el botón para
  ver favoritos o volver al «Modo carrusel», y el selector rápido Shorts/Normales.
  Este selector guarda el mismo ajuste de formato que el menú de apariencia.
  Estrella y favoritos se alinean a la izquierda; el selector de formato queda
  a la derecha y muestra el destino del cambio, tanto en su texto como en su icono.
- La estrella hueca guarda el vídeo y pasa a estar rellena; pulsarla otra vez
  lo quita de favoritos. La lista se guarda en
  `~/.coronapoker/poker-video-favorites.properties` y sobrevive a reinicios.
  Los favoritos se separan por formato para mantener la proporción correcta.
  El modo favoritos usa esa lista en el carrusel; al volver a normal recupera
  el catálogo aleatorio de la sesión. Si no hay favoritos del tipo elegido,
  se pausa el vídeo y se muestra un mensaje que permite volver al modo carrusel.
- El Short arranca automáticamente silenciado y sin forzar subtítulos. Los
  controles nativos de YouTube están habilitados para mantener el icono de
  audio enlazado con el mute real; la navegación y el cierre se
  realizan desde la barra de CoronaPoker. El
  perfil anónimo del reproductor vive solo en memoria para no heredar una
  preferencia antigua de subtítulos. La
  barra propia de CoronaPoker muestra el título del resultado, anterior,
  siguiente y cerrar.
- La ventana conserva posición y tamaño entre Principal y Sala de espera. Es
  arrastrable y redimensionable manteniendo el formato del vídeo; los cuatro
  bordes y las cuatro esquinas aceptan el gesto y muestran el cursor adecuado,
  aunque las zonas son invisibles. No se dibujan marco, sombra ni tirador de
  esquina; la cabecera y las dos esquinas inferiores de la barra de controles quedan
  redondeadas. Su tamaño inicial compacto corresponde al mínimo de la ventana
  y escala con el `FitViewport` 1920x1080, por lo que conserva la misma
  proporción visual en 1080p, 1440p y 2160p. En la sala el icono queda elevado
  por encima
  de los controles inferiores y su hover está recortado al propio botón. En
  principal y sala el icono tiene el mismo tamaño que el botón de sonido y se
  sitúa inmediatamente a su izquierda.
- El tamaño máximo solo depende del espacio disponible en pantalla, incluidos
  la cabecera, el pie de controles y un margen de ocho unidades por lado. No existe un ancho máximo
  fijo; al cambiar de formato se ajusta el tamaño para que siga cabiendo.
  La superficie JCEF recibe la escala real del viewport, también en 4K y al
  redimensionar la ventana del juego. YouTube mantiene su selección automática
  de calidad según tamaño y conexión; la API no permite forzar una resolución
  máxima (`setPlaybackQuality` ya no tiene efecto).
- Abrir un modal pausa y oculta el vídeo sin destruir la sesión. Al cerrar el
  modal continúa desde el mismo punto.
- Cerrar minimiza la ventana al icono sin modificar el ajuste. Conserva en
  memoria el vídeo y el segundo aproximado; después cierra
  la sesión de navegador y libera la textura. Pulsar el icono recrea la sesión
  desde ese punto, siempre silenciada al abrir desde el icono. Desactivar el ajuste oculta tanto
  ventana como icono y cancela cualquier inicialización pendiente.
- Antes de crear la mesa se aplica el mismo cierre de sesión y liberación de
  textura. Durante la partida no se muestra ni se crea icono o reproductor. Al
  volver de la timba se restaura exactamente el estado de ventana o icono que
  correspondía.
- Una apertura nueva empieza realmente silenciada. Los cambios
  entre principal, modal y sala de espera conservan la misma sesión, posición
  y audio. Si la sesión debe liberarse al entrar en la mesa, su último estado
  estable se restaura al volver. El iframe se crea con reproducción automática
  desactivada; al recibir `onReady`, CoronaPoker aplica primero el mute real y
  después inicia la reproducción. No se fuerzan ciclos de mute/desmute ni
  cambios de volumen para intentar repintar el altavoz. Con `controls: 0`, el
  reproductor de Shorts dejaba un botón visible cuyo icono no seguía los
  cambios de la API; el diagnóstico con CEF real reprodujo la contradicción.
  El mute se suministra también en la URL del iframe. La primera inicialización
  de Shorts en un proceso CEF frío podía dejar incoherente el modelo visual
  incluso con ese valor: el diagnóstico real reprodujo el fallo y comprobó que
  desaparecía al recargar el documento. El primer `READY` prepara el renderer
  sin permitir reproducción y pide una única recarga; solo el siguiente
  `READY` permite reproducir. Esta preparación no se repite al minimizar,
  restaurar ni cambiar de pantalla. Se conservan los controles nativos para que
  sus cambios de mute se reflejen en la API. Tras restaurar el estado inicial,
  CoronaPoker solo observa el audio elegido en los controles nativos y lo
  conserva entre vídeos y transiciones; nunca sobreescribe un clic de usuario
  con una lectura antigua.
- Cambiar el idioma del juego sustituye el catálogo y recrea la sesión con
  el idioma correspondiente, conservando el audio. Entrar en la mesa desde un
  modal también conserva el estado de la ventana que estaba oculta debajo.
- Los cambios de vídeo siguen usando `loadVideoById`, sin recrear iframe ni
  navegador. Shorts puede reconstruir su botón de audio con el icono audible
  aunque el elemento de vídeo siga muteado. Un observador de solo lectura
  detecta esa discrepancia concreta al cambiar el control y avisa al documento
  principal; este vuelve a emitir `mute()` únicamente si la API sigue muteada.
  No se inyectan clics ni se modifica el icono: el propio reproductor lo
  sincroniza. La detección responde a cambios del control y del audio, sin
  temporizadores adicionales ni recargas por vídeo.
- Popups, menús contextuales y navegaciones de nivel superior se bloquean. La
  página de resultados nunca se presenta como navegador al usuario.

## Aislamiento

JCEF pinta en un búfer BGRA sin ventana (`OSR`). El callback de CEF solo copia
memoria; la subida de la textura OpenGL ocurre en el hilo de renderizado de
libGDX. El motor CEF permanece inicializado hasta salir de CoronaPoker porque
JCEF no admite de forma segura terminar y reinicializar el proceso nativo. Las
sesiones de navegador y sus texturas sí se destruyen al cerrar o entrar en
la mesa.

## Plataformas de distribución

Se genera un artefacto por plataforma, tal como recomienda JCEF Maven, porque
cada paquete nativo ocupa alrededor de 100 MB. Las combinaciones admitidas por
esta integración son Windows x86-64, Linux x86-64/ARM64 y macOS
x86-64/Apple Silicon. Windows ARM64 queda excluido porque JCEF Maven no admite
OSR en esa plataforma. Cada artefacto incluye además una sola familia de
nativos LWJGL y conserva únicamente los binarios libGDX/libGDX FreeType de su
sistema y arquitectura.

`tools/build/build-platform-jars.ps1` fabrica y comprueba los cinco JAR de
release. Desde 27.17 el actualizador selecciona el artefacto del sistema y
arquitectura actuales; mantiene el nombre histórico para releases anteriores.
Desde 27.18, el servidor del reproductor usa únicamente sockets de `java.base`,
sin depender del módulo opcional `jdk.httpserver`. Esto permite actualizar el
JAR sobre instalaciones Windows anteriores conservando su Java privado, como
hace el actualizador; no es necesario reinstalar para añadir módulos al runtime.
El servidor escucha solo en loopback, sirve un documento fijo, limita tamaño y
tiempo de las cabeceras y cierra conexiones al liberar la sesión.

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
La respuesta se limita a 12 MiB durante la recepción y tiene un plazo total de
15 segundos, incluido el cuerpo; interrumpir la carga cancela la petición.

La validación nativa de esta integración se ha realizado en Windows x64.
Los paquetes Linux y macOS se comprueban estructuralmente al fabricarlos;
esto no sustituye una prueba gráfica real en esos sistemas.
