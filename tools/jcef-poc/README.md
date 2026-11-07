# CoronaPoker JCEF / libGDX OSR PoC

Prueba de concepto independiente para decidir si un reproductor oficial de YouTube puede convivir
con el renderizado de CoronaPoker. No depende de los módulos del juego y no modifica ninguna
pantalla, recurso o preferencia de CoronaPoker.

La [guía del reproductor actual](../../docs/JCEF_VIDEO_EXPERIMENT.md) describe
la integración distribuida con el juego.

## Qué demuestra

- Ventana libGDX/LWJGL3 de 1280 × 720 con un panel flotante Scene2D.
- Chromium/JCEF renderizado sin ventana nativa mediante Off-Screen Rendering (OSR).
- Copia de los callbacks BGRA de CEF a un búfer directo persistente y subida de regiones sucias a
  una textura OpenGL exclusivamente desde el hilo de render de libGDX.
- Reproductor real mediante la API IFrame oficial de YouTube, con controles y audio de Chromium.
- Ratón, rueda, teclado, foco, redimensionado 16:9, minimizar, cerrar/reabrir y pausa al ocultar.
- FPS, tiempo de frame, heap Java, resolución, actualizaciones de textura por segundo, coste de
  subida, estado del reproductor y versiones JCEF/CEF/Chromium.
- F8 o el botón `CEF: ON/OFF` detienen/reactivan el navegador para comparar el coste manteniendo
  activo el render de fondo.

## Versiones fijadas

- Java de destino: 17, igual que CoronaPoker.
- libGDX: 1.14.2.
- LWJGL: 3.4.1.
- jcefmaven: 152.0.6.
- JCEF: 17e805a.
- CEF: 152.0.6+g708dc14.
- Chromium: 152.0.7977.83.
- Gradle Wrapper: 9.8.2.

`me.friwi:jcefmaven:152.0.6` y sus paquetes nativos están publicados en Maven Central. El JAR de
Windows generado por defecto incluye el paquete nativo Windows x64. La primera ejecución lo extrae
en `%LOCALAPPDATA%\CoronaPoker\jcef-poc` y no necesita descargar CEF.

## Compilar y ejecutar en Windows x64

```powershell
cd tools/jcef-poc
.\gradlew.bat clean check jar
java -jar .\build\libs\coronapoker-jcef-poc-0.1.0-windows-x86_64.jar
```

También puede utilizarse `scripts\run-windows.cmd`. El vídeo de ejemplo es `M7lc1UVf-VE`.

```powershell
java -jar .\build\libs\coronapoker-jcef-poc-0.1.0-windows-x86_64.jar `
  --video "https://www.youtube.com/watch?v=M7lc1UVf-VE"
```

Se acepta un identificador o una URL de `youtube.com`, `youtu.be`, `embed`, `shorts` o `live`.
También se lee `jcef-poc.properties` del directorio de ejecución. Consulta
`jcef-poc.properties.example`.

## Origen, Referer y error 153

El modo normal sirve `player.html` desde un servidor HTTP de loopback ligado exclusivamente a
`127.0.0.1` con un puerto aleatorio. La navegación principal tiene así un origen HTTP real en vez
de `file://`. La página usa `strict-origin-when-cross-origin`, pasa `origin` y `widget_referrer` a
la API IFrame y JCEF añade el mismo `Referer` únicamente si una petición a YouTube llegase sin él.
No se suplanta un sitio externo.

Para experimentar con un alojamiento estable puede indicarse `--page-url https://...`. Esa página
debe aceptar `?video=ID` e implementar la API IFrame. En ese modo la identidad es el origen HTTPS
proporcionado.

La página muestra y notifica errores 2, 5, 100, 101, 150 y 153, además de bloqueo de autoplay. El
autoplay inicial se solicita silenciado. El usuario puede activar el audio con el control oficial.
No se descarga ni se extrae ningún stream de YouTube.

## Diseño del puente OSR

JCEF incluye un navegador OSR pensado para presentar en un `GLCanvas` de JOGL. Usarlo tal cual
crearía otro componente gráfico y otro contexto OpenGL. Esta PoC define un navegador mínimo dentro
del paquete `org.cef.browser` para reutilizar las llamadas nativas protegidas de JCEF sin crear una
ventana Swing/AWT:

1. CEF entrega un `ByteBuffer` BGRA temporal, con origen superior izquierdo.
2. El callback copia solo las filas modificadas a un único búfer directo persistente. El búfer se
   vuelve a asignar únicamente al cambiar la resolución.
3. El hilo OpenGL de libGDX consume las regiones acumuladas con `glTexSubImage2D` y `GL_BGRA`.
4. Un bloqueo corto protege el búfer para que CEF nunca lo reescriba durante la subida.
5. Scene2D dibuja la textura y convierte sus coordenadas de entrada al espacio superior-izquierdo
   de Chromium.

Esta dependencia de miembros de paquete de JCEF es apropiada para una PoC pero es una señal de
coste de mantenimiento: una integración de producto tendría que fijar y verificar cada actualización
de JCEF, o mantener un pequeño adaptador parcheado aguas arriba.

No se pasa `--disable-gpu`. Chromium conserva su configuración gráfica normal. Sin embargo, la API
Java de JCEF 152 expone el callback OSR de memoria BGRA y no expone el callback de textura compartida
acelerada de CEF. Por ello la presentación final Chromium → libGDX incluye necesariamente una copia
de CPU y una subida de textura. Las métricas permiten cuantificar ese coste real.

## Otras plataformas

Cada JAR contiene binarios nativos de una sola plataforma. Se puede generar la estructura para:

```text
-PtargetPlatform=windows-x86_64
-PtargetPlatform=linux-x86_64
-PtargetPlatform=macos-x86_64
-PtargetPlatform=macos-aarch64
```

Ejemplo:

```bash
./gradlew clean check jar -PtargetPlatform=linux-x86_64
```

En macOS debe usarse `scripts/run-macos.sh`, que añade `-XstartOnFirstThread` para GLFW y las
aperturas de módulos AWT que requiere JCEF. Linux usa `scripts/run-linux.sh`. Las variantes no se
consideran probadas hasta ejecutarlas gráficamente en sus respectivos sistemas.

## Qué miden y qué no miden las cifras

El overlay mide el bucle Java/libGDX y el coste de las llamadas de subida a OpenGL. El heap mostrado
es solo el heap de la JVM. Chromium crea procesos nativos separados y puede consumir memoria, CPU,
decodificación de vídeo y GPU que no aparecen en ese heap ni quedan totalmente atribuidos por estas
métricas. Para decidir una integración real conviene acompañar la PoC con Administrador de tareas,
Process Explorer, GPUView o las herramientas equivalentes del sistema.

## Fuentes primarias consultadas

- [jcefmaven](https://github.com/jcefmaven/jcefmaven)
- [CEF: Off-Screen Rendering](https://github.com/chromiumembedded/cef/blob/master/docs/general_usage.md#off-screen-rendering)
- [JCEF](https://github.com/chromiumembedded/java-cef)
- [YouTube IFrame Player API](https://developers.google.com/youtube/iframe_api_reference)
- [Identidad y Referer del cliente de YouTube](https://developers.google.com/youtube/terms/required-minimum-functionality#api-client-identity-and-credentials)
