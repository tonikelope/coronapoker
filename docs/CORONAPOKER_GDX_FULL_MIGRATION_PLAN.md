# Plan maestro de migración completa de CoronaPoker a libGDX

**Documento autocontenido y portable**<br>
**Fecha:** 2 de septiembre de 2026<br>
**Repositorio:** `tonikelope/coronapoker`<br>
**Versión clásica protegida:** CoronaPoker 24.10<br>
**Rama estable:** `master`<br>
**Rama de trabajo actual:** `feature/gdx-demo-live`<br>
**Referencia visual GDX aprobada:** commit `627c71e4f` (`feat: add immersive GDX table context menu`)

**Regla de preservación:** la rama y el historial de la demo son un activo de producto. No se reescribirán, sustituirán ni eliminarán. Constituyen la base exacta del aspecto del cliente GDX; la evolución se realizará conservando ese renderer y reemplazando progresivamente el guion simulado por datos, comandos y eventos reales del núcleo.

---

## 1. Decisión ejecutiva

CoronaPoker se convertirá en dos aplicaciones completas y separadas, construidas desde un único núcleo compartido:

- `CoronaPoker-24.11-swing.jar`: aplicación clásica y estable, con la interfaz Swing actual.
- `CoronaPoker-24.11-gdx.jar`: aplicación completa con libGDX desde el arranque hasta el final de la timba.

Ambas aplicaciones compartirán exactamente la misma lógica de negocio, reglas, red, protocolo, criptografía, persistencia y estado de partida. No habrá dos crupieres, dos implementaciones de reglas ni lógica de póquer dentro del código GDX.

La aplicación GDX será una interfaz real, no una capa superpuesta sobre una mesa Swing oculta. El objetivo final prohíbe:

- mantener `GameFrame`, `JPanel`, `JLabel` u otros componentes Swing ocultos para controlar GDX;
- observar o sondear periódicamente componentes Swing;
- inferir animaciones comparando capturas de estado cada cierto tiempo;
- duplicar reglas del juego dentro del renderer;
- hacer que el núcleo dependa de Swing o de libGDX.

La demo GDX aprobada es el contrato visual y de animación de la mesa. No se recreará “parecida”: su código de dibujo, geometría, shaders, temporizaciones visuales y composición será la base directa de la mesa GDX real. Se sustituirán únicamente su guion y sus datos inventados por eventos y estado procedentes del motor real. Las mejoras futuras partirán de esa base y se validarán visualmente contra ella; no habrá un segundo renderer de mesa paralelo.

---

## 2. Estado protegido de `master` y GitHub

Comprobación realizada el 2 de septiembre de 2026 después de ejecutar `git fetch --prune origin`:

| Referencia | Commit |
|---|---|
| `master` local | `9497f1e25974fc9da349cc5ec2c1dab626918cab` |
| `origin/master` | `9497f1e25974fc9da349cc5ec2c1dab626918cab` |
| Divergencia | `0` commits locales / `0` commits remotos |
| Descripción | `merge: CoronaPoker 24.10 release` |

Por tanto:

- CoronaPoker 24.10 permanece intacto y disponible en `master` y en GitHub.
- El trabajo GDX está aislado en `feature/gdx-demo-live`.
- Ningún commit GDX forma parte de `master`.
- No se integrará nada en `master` hasta que los dos productos sean verificables y exista autorización expresa.

Hay cambios sin commit y recursos sin seguimiento en el directorio de trabajo actual. No modifican el commit de `master`, pero deben inventariarse y protegerse antes de limpiar, cambiar de rama o reorganizar módulos. En particular, no se ejecutará `git clean`, un reset destructivo ni un borrado masivo.

---

## 3. Objetivos no negociables

### 3.1 Un solo motor

El núcleo común será la única autoridad sobre:

- turnos y orden de actuación;
- apuestas, botes, side pots y stacks;
- ciegas, dealer y straddle;
- reparto, calles y showdown;
- pausas, locks y barreras temporales;
- red, protocolo y sincronización;
- recuperación y reconexión;
- criptografía e identidad;
- configuración persistente;
- lógica de bots;
- registro canónico de la partida.

Swing y GDX solamente presentan el estado, reproducen los efectos ordenados y entregan al núcleo las acciones del usuario.

### 3.2 Dos frontends completos

No se limitará GDX a `GameFrame`. La versión GDX deberá cubrir:

1. splash y arranque;
2. pantalla principal;
3. crear timba y unirse;
4. sala de espera;
5. chat, emoticonos, GIF, imágenes y voz cuando corresponda;
6. mesa completa;
7. menús contextuales;
8. ajustes accesibles durante la timba;
9. registro de juego;
10. diálogos modales y confirmaciones;
11. reconexión y recuperación;
12. pantalla de fin de timba;
13. cierre ordenado de la aplicación.

### 3.3 Fidelidad visual total a la demo

La mesa real GDX deberá conservar la calidad de la demo aprobada y sus posteriores correcciones solicitadas. No se aceptará una mesa funcional que visualmente sea una aproximación inferior.

### 3.4 Swing seguirá siendo estable

Durante toda la migración:

- `master` seguirá representando la versión 24.10 estable;
- el JAR Swing seguirá compilando y ejecutando la suite existente;
- cualquier extracción al núcleo se validará primero contra el comportamiento clásico;
- no se alterará el protocolo para diferenciar clientes Swing y GDX;
- una mesa podrá incluir clientes construidos con ambos frontends.

---

## 4. Diagnóstico del código actual

El problema principal no es libGDX. Es que la lógica, el estado y la interfaz Swing llevan años entrelazados.

### 4.1 Clases de gran tamaño y alto acoplamiento

Inventario aproximado actual:

| Clase | Líneas | Responsabilidades mezcladas |
|---|---:|---|
| `Crupier` | 24.698 | reglas, flujo, sincronización y acceso directo a UI |
| `GameFrame` | 7.266 | ventana, configuración global, estado compartido y servicios |
| `WaitingRoomFrame` | 7.367 | lobby, red, participantes, chat y Swing |
| `LocalPlayer` | 4.557 | jugador, controles, estado y `JPanel` |
| `RemotePlayer` | 3.838 | jugador remoto, estado y `JPanel` |
| `TablePanel` | 2.836 | composición de mesa y comportamiento visual |
| `Audio` | 1.911 | reproducción, estado y sincronización audiovisual |
| `Init` | 2.192 | arranque, configuración y `JFrame` |
| `Card` | 1.410 | valor de carta, estado y render Swing |
| `CommunityCardsPanel` | 1.431 | estado de board y visualización |

Existen alrededor de 160 clases Java en el paquete principal y más de 370 archivos de prueba/QA.

### 4.2 Dependencias directas de la lógica sobre la UI

`Crupier` contiene cientos de accesos a `GameFrame.getInstance()`. Entre los usos más frecuentes están:

- obtener jugador local, jugadores y participantes;
- acceder a tapete, registro y sala de espera;
- leer o modificar la barra de tiempo;
- consultar cartas comunitarias y controles;
- comprobar pausas;
- actualizar botes y etiquetas;
- llamar métodos visuales que también actúan como barreras.

`GameFrame` expone asimismo una gran cantidad de configuración global mediante campos estáticos. `LocalPlayer`, `RemotePlayer` y `Card` son al mismo tiempo objetos de estado y componentes Swing.

Esto impide reemplazar Swing limpiamente cambiando unas pocas llamadas. Primero hay que separar las responsabilidades sin cambiar la semántica.

### 4.3 Acoplamientos que deben eliminarse del núcleo

El núcleo final no podrá importar:

- `java.awt.*`;
- `javax.swing.*`;
- `com.badlogic.gdx.*`.

Algunos ejemplos de separación obligatoria:

| Situación actual | Diseño final |
|---|---|
| `Card extends JLayeredPane` | `CardCode`/`CardState` en core + `SwingCardView` + `GdxCardView` |
| `LocalPlayer extends JPanel` | `LocalPlayerState`/controlador en core + vistas Swing/GDX |
| `RemotePlayer extends JPanel` | `RemotePlayerState` en core + vistas Swing/GDX |
| `Player` devuelve `JLabel` y puntos AWT | contrato de jugador sin tipos gráficos |
| `WaitingRoomFrame` guarda la sesión | `LobbySession` en core + dos vistas |
| `GameFrame` guarda preferencias globales | servicios tipados de configuración |
| lógica lee `JSpinner`/`JButton` | comando tipado con el valor elegido |

### 4.4 Estado de la experimentación GDX actual

La rama contiene dos commits de frontera neutral posteriores a la demo:

- `619c77065`: base de renderer neutral;
- `6fca4da3e`: puente de eventos de mesa.

También hay trabajo no confirmado en `renderer-gdx` y adaptadores `LiveTable*`. Este material es una prueba de concepto, no la arquitectura final. En particular, los adaptadores que leen `GameFrame`, componentes Swing o capturan estado periódicamente no se trasladarán al producto.

Existe un stash preservado con la integración fallida anterior. Se conservará únicamente como evidencia y para rescatar, de forma selectiva, recursos o pruebas útiles. No se restaurará ni se hará cherry-pick masivo.

---

## 5. Arquitectura objetivo

```text
                         +---------------------------+
                         |     coronapoker-core      |
                         |---------------------------|
                         | reglas / Crupier          |
                         | estado de partida         |
                         | red y protocolo           |
                         | crypto e identidad        |
                         | persistencia              |
                         | lobby y chat (modelo)      |
                         | configuración compartida  |
                         | eventos y comandos        |
                         +-------------+-------------+
                                       |
                         contratos Java sin UI
                         eventos ->     <- comandos
                          /                           \
             +-----------+----------+     +-----------+----------+
             | coronapoker-swing    |     | coronapoker-gdx      |
             |----------------------|     |----------------------|
             | SwingLauncher        |     | GdxLauncher          |
             | ventanas y paneles   |     | scenes y stages      |
             | SwingTableView       |     | mesa exacta demo     |
             | diálogos Swing       |     | overlays GDX         |
             | JavaSound backend    |     | GDX audio backend    |
             +----------------------+     +----------------------+
```

### 5.1 Dirección de dependencias

Las dependencias serán unidireccionales:

```text
coronapoker-swing  ---> coronapoker-core
coronapoker-gdx    ---> coronapoker-core
coronapoker-core   -X-> Swing
coronapoker-core   -X-> libGDX
```

El núcleo no sabrá qué frontend está activo. Cada JAR tendrá su propio launcher y ensamblará el mismo núcleo con su frontend correspondiente.

### 5.2 Puertos principales

El núcleo expondrá contratos pequeños y tipados. Los nombres exactos podrán ajustarse, pero la separación será esta:

```java
interface ApplicationFrontend {
    CompletionStage<Void> openMainMenu(MainMenuState state);
    CompletionStage<CreateGameResult> requestCreateGame(CreateGameModel model);
    CompletionStage<JoinGameResult> requestJoinGame(JoinGameModel model);
    LobbyFrontend lobby();
    TableFrontend table();
    DialogService dialogs();
}

interface TableFrontend {
    CompletionStage<Void> open(TableState initialState);
    CompletionStage<Void> present(TableVisualEvent event);
    void update(TableProjection projection);
    CompletionStage<Void> close();
}

interface TableCommandSink {
    void submit(TableCommand command);
}
```

Las acciones contendrán ya sus datos:

```text
FoldRequested
CheckRequested
CallRequested(amount)
BetRequested(amount)
RaiseRequested(amount)
AllInRequested
CardSwapRequested(from, to)
DeckChangeRequested(deckId)
ChatSendRequested(message)
ExitGameRequested
```

El núcleo no leerá un `JSpinner` ni un `TextField` GDX. El frontend extraerá el valor del control y enviará `BetRequested(amount)`. Si alguna consulta de valor debe conservarse por compatibilidad durante la transición, se hará a través de una interfaz neutral temporal, nunca accediendo a widgets concretos.

### 5.3 Estado frente a eventos

Se usarán dos mecanismos complementarios:

- **Proyección de estado:** fotografía inmutable de lo que debe verse ahora: jugadores, stacks, aportaciones, cartas, board, bote, turno, configuración.
- **Evento visual:** algo que debe suceder una vez y con un orden causal: mover dealer, publicar ciega, barajar, repartir, voltear, transportar fichas, mostrar all-in, resolver showdown.

La proyección permite reconstruir la pantalla al abrir o reconectar. Los eventos conservan la secuencia y las animaciones. No se inferirán eventos comparando fotografías.

### 5.4 Barreras y temporización

Las llamadas actuales `GUIRun` y `GUIRunAndWait` contienen información valiosa sobre el orden del juego. Se traducirán explícitamente:

| Semántica clásica | Contrato neutral | Swing | GDX |
|---|---|---|---|
| actualización asíncrona | `presentAsync(event)` | `SwingUtilities.invokeLater` | `Gdx.app.postRunnable` |
| barrera visual | `present(event)` devuelve `CompletionStage` | completa al terminar efecto Swing | completa al terminar animación GDX |
| diálogo modal | futuro con resultado | diálogo Swing | overlay/ventana GDX |

El crupier esperará únicamente en los puntos donde el juego clásico ya exige una barrera. El hilo de render GDX nunca será bloqueado. Tampoco se introducirán pausas artificiales para “hacer que se vea bien”: la finalización real de la animación liberará la barrera.

### 5.5 Eventos directos, sin observadores

Flujo correcto:

```text
Crupier decide publicar BB
        |
        +--> actualiza estado canónico
        |
        +--> TableFrontend.present(BlindPosted(...))
                    |
                    +--> GDX anima las fichas
                    +--> las fichas impactan en el bote
                    +--> completa el futuro
        |
Crupier continúa con el siguiente paso
```

Flujo prohibido:

```text
Timer cada 33 ms -> lee JLabels Swing -> detecta diferencia -> adivina animación
```

---

## 6. Organización del repositorio y Maven

### 6.1 Estructura final limpia

```text
coronapoker/
├─ pom.xml                         # parent y reactor
├─ coronapoker-core/
│  ├─ pom.xml
│  └─ src/main/java/.../core/
├─ coronapoker-assets/
│  ├─ pom.xml
│  └─ src/main/resources/
├─ coronapoker-swing/
│  ├─ pom.xml
│  └─ src/main/java/.../swing/
├─ coronapoker-gdx/
│  ├─ pom.xml
│  └─ src/main/java/.../gdx/
├─ coronapoker-qa/
│  ├─ pom.xml
│  └─ src/test/java/
├─ docs/
└─ dist/
```

`coronapoker-assets` evita mantener copias divergentes de cartas, sonidos, música, GIF y cinematics. Los ensamblados finales podrán incluir físicamente los recursos que necesiten, pero habrá una sola fuente en el repositorio.

### 6.2 Transición sin romper el build clásico

No se convertirá el `pom.xml` raíz en agregador de golpe. El orden seguro será:

1. Crear un reactor de migración separado.
2. Extraer `coronapoker-core` manteniendo compatible el build clásico actual.
3. Construir `coronapoker-swing` desde el mismo código y verificar paridad.
4. Convertir el prototipo en `coronapoker-gdx`.
5. Migrar QA al reactor nuevo.
6. Solo cuando ambos JAR funcionen, convertir la raíz en el parent definitivo.

Así se evita dejar el proyecto sin un JAR clásico compilable durante la reorganización.

### 6.3 Artefactos finales

```text
dist/CoronaPoker-24.11-swing.jar
dist/CoronaPoker-24.11-gdx.jar
dist/chilean_mod.zip
```

Ambos JAR formarán la versión CoronaPoker 24.11 y compartirán:

- versión de reglas;
- versión de protocolo;
- formato de persistencia;
- identidad criptográfica;
- recursos comunes que correspondan.

No será necesario un selector de renderer. El usuario elegirá directamente qué aplicación ejecutar. Swing será la opción para equipos modestos o para quien prefiera la interfaz clásica; GDX mantendrá la máxima calidad configurada.

---

## 7. Inventario completo de migración de UI

Cada elemento Swing debe tener una contraparte GDX antes de declarar completa la aplicación.

### 7.1 Arranque y pantalla principal

- splash;
- comprobación de actualización;
- carga de base de datos, identidad y CSPRNG;
- música de fondo;
- crear timba;
- unirse a timba;
- recuperar/reconectar;
- ajustes generales;
- acerca de;
- salida.

El arranque técnico se moverá fuera de `Init extends JFrame` a un bootstrap compartido. `SwingLauncher` y `GdxLauncher` decidirán únicamente cómo presentar las pantallas.

### 7.2 Creación y unión a partida

- todos los campos de `NewGameDialog`;
- presets y validaciones;
- ciegas, buy-in, límites, reloj, RIT y demás reglas;
- selección y validación de servidor/partida;
- errores de red;
- progreso de conexión;
- cancelación segura.

Los formularios devolverán modelos tipados. Ninguna regla se duplicará en los controles GDX.

### 7.3 Sala de espera

- participantes y estados;
- host, permisos y expulsiones;
- listo/no listo;
- configuración de la partida;
- chat y scroll;
- emoticonos y chat rápido;
- GIF, imágenes y voz;
- identicons;
- reconexión y usuarios que entran/salen;
- inicio y cancelación de la timba.

El modelo de lobby, el historial y los mensajes de red se extraerán de `WaitingRoomFrame`. Swing y GDX renderizarán la misma sesión.

### 7.4 Mesa

- jugadores locales y remotos;
- board, bote y apuestas individuales;
- acciones y turno;
- cartas y barajas/mods;
- dealer, SB, BB y straddle;
- HUD local y atajos;
- tiempo;
- chat rápido;
- pausa;
- RIT;
- rebuy;
- all-in y cinematics;
- showdown y resaltado manual;
- registro;
- menú contextual;
- ajustes disponibles durante partida;
- salida, reconexión y fin de partida.

### 7.5 Diálogos y overlays

Se portarán, según proceda, los equivalentes actuales de:

- ajustes y paneles de apariencia/audio/juego;
- registro de juego;
- pausa;
- salida;
- rebuy;
- reconnect/recover;
- run it twice;
- voluntary straddle;
- fast chat y emoji;
- auto-action y auto-call;
- notificaciones dentro de partida;
- estadísticas;
- atajos;
- imágenes, GIF e identicons;
- fin de timba.

Los diálogos GDX se implementarán como overlays o ventanas dentro del Stage, con foco, teclado, cancelación y resultado asíncrono. No abrirán ventanas Swing.

---

## 8. Contrato visual exacto de la mesa GDX

La fuente de referencia es:

```text
commit 627c71e4f
prototype-gdx/src/main/java/com/tonikelope/coronapoker/gdxdemo/CoronaPokerGdxDemo.java
```

La rama de la demo se conservará íntegra como referencia ejecutable. Las correcciones posteriores que mejoran la demo también se conservarán cuando estén verificadas. La estrategia no será copiar fragmentos a una clase nueva ni reinterpretar su diseño: el renderer aprobado se refactorizará internamente para recibir estado real sin alterar primero su resultado visual.

Antes y después de cada cambio estructural se compararán capturas y secuencias contra la demo. Si una extracción cambia el aspecto o la fluidez sin que exista una mejora visual expresamente aprobada, el cambio no supera la fase.

### 8.1 Render y rendimiento

- aceleración por GPU mediante libGDX/LWJGL3;
- pantalla completa real;
- VSync disponible y correctamente configurado;
- soporte de tasas altas de refresco;
- foreground FPS sin limitación artificial cuando corresponda;
- idle FPS reducido;
- RGBA8, depth 24, stencil 8 y MSAA 4 como base aprobada;
- mipmaps y filtrado adecuados;
- frame pacing fluido, no solo promedio alto de FPS;
- sin degradar calidad automáticamente para equipos modestos; para ellos permanece Swing.

### 8.2 Cartas

- recursos HQ;
- esquinas redondeadas mediante el tratamiento aprobado;
- sin sombras;
- comunitarias y cartas locales como máximo un 10 % menores que la versión inicial de la demo;
- avatar siempre a la izquierda del HUD;
- las dos cartas de cada rival en V, a la derecha del avatar;
- mismo anclaje y posición estén tapadas o destapadas;
- conservar el ángulo aprobado;
- desplazar la carta izquierda lo suficiente a la derecha para que valor y palo se vean completos;
- la animación de destape termina exactamente en la posición ocupada por las cartas tapadas;
- swap local sin salto, solapamiento extraño ni cambio final de orden;
- clic derecho sobre cualquier carta cambia la baraja, igual que en Swing;
- soporte íntegro de barajas mod, HQ y GIF de barajado, incluida PepsiMan.

### 8.3 Asientos

- distribución uniforme y segura para 2 a 10 jugadores;
- reflow calculado para cada número de jugadores;
- los asientos superiores se compactan hacia el centro al disminuir jugadores;
- los laterales mantienen separación vertical uniforme;
- recolocación solo entre manos, sin cartas repartidas;
- nick centrado;
- sin número de jugador;
- sin texto `STACK`: solo la cantidad;
- aportación actual visible y contenida;
- última acción fijada en el asiento;
- acción legible de un vistazo;
- ningún texto puede salir de su panel;
- ningún asiento, carta o HUD puede quedar fuera de la zona visible.

### 8.4 Colores originales

Se conservará la semántica visual a la que están acostumbrados los jugadores de CoronaPoker. La fuente de verdad será el código Swing existente, no una paleta inventada.

Base solicitada:

| Estado/acción | Color |
|---|---|
| no va / fold | gris |
| pasa / check | verde |
| iguala / call | blanco |
| apuesta / sube | amarillo |
| resube, cuando proceda | morado original |
| all-in | negro |
| ganador | verde puro y llamativo |
| perdedor | rojo puro y llamativo |

No se usarán colores apagados. El HUD local y las etiquetas de acción remotas utilizarán los colores originales exactos extraídos del modo clásico.

### 8.5 Bote y fichas

- etiqueta estable `BOTE: cantidad`;
- icono de fichas a la izquierda;
- texto de alto contraste;
- sin temblor ni saltos al cambiar las cifras;
- situado debajo del jugador superior y sin taparlo;
- bote inicial a cero;
- SB y BB publican antes del barajado;
- el stack y el bote cambian al impactar las fichas, no al iniciar el vuelo;
- no comenzar la siguiente etapa hasta que todas las fichas hayan llegado;
- dealer, SB, BB y straddle usan el mismo tamaño físico proporcional;
- tamaño basado en el clásico, no en una proporción exagerada respecto a la carta;
- ficha de posición sobre la esquina superior derecha del HUD, sin tapar texto.

### 8.6 Tiempo y turno

- una barra de tiempo común debajo de las cartas comunitarias;
- verde al comenzar, transición a amarillo y finalmente rojo;
- progreso en la dirección intuitiva de tiempo restante;
- sin barras ambiguas dentro de cada acción;
- resaltado inequívoco del jugador que está pensando;
- incluye al jugador local;
- en la demo se pueden usar tiempos aleatorios, pero en el juego real provienen del crupier.

### 8.7 Secuencia causal obligatoria

Entre manos:

```text
limpiar mano anterior
-> mover dealer/SB/BB en sentido horario
-> publicar ciegas desde bote cero
-> esperar impacto de fichas
-> barajado completo con audio sincronizado
-> repartir cartas
-> iniciar acción preflop
```

Durante una calle:

```text
acción decidida por el motor
-> mostrar acción fija
-> animar fichas
-> esperar impacto si es barrera
-> continuar turno/calle
```

Cambio de calle:

```text
recoger aportaciones
-> esperar llegada al bote
-> revelar flop/turn/river con la animación de la demo
-> habilitar siguiente ronda
```

Showdown:

```text
revelar cada mano en su anclaje
-> mostrar solo nombre de jugada
-> ganador verde puro / perdedores rojo puro
-> permitir resaltado manual clásico
-> pagar bote con impacto sincronizado
```

---

## 9. Plan de ejecución por fases

Cada fase tiene una salida verificable. No se avanzará acumulando una integración grande e imposible de probar.

### Fase 0 — Congelar referencias y limpiar con seguridad

Objetivo: disponer de un punto de partida reproducible sin perder trabajo ni assets.

Tareas:

- mantener `master` intacto en `9497f1e25`;
- preservar la rama `feature/gdx-demo-live`;
- etiquetar documentalmente `627c71e4f` como referencia visual;
- no rebasar, aplastar ni reescribir el historial que contiene la demo;
- crear cualquier reorganización arquitectónica en una rama hija o separada, manteniendo siempre recuperable y ejecutable la demo original;
- inventariar todos los cambios sin commit;
- separar código experimental, caches y recursos de usuario;
- respaldar los mods PepsiMan/Pinup, cinematics y sonidos;
- clasificar los commits `619c77065` y `6fca4da3e` por archivo;
- mantener el stash fallido sin restaurarlo;
- crear la rama de arquitectura definitiva desde la referencia elegida una vez protegido todo.

Criterio de salida:

- `master` limpio y sincronizado;
- todos los assets útiles protegidos;
- cero archivos de usuario borrados;
- inventario “conservar / reescribir / descartar” firmado en el repositorio;
- demo reproducible de forma independiente.

### Fase 1 — Reactor Maven y builds reproducibles

Objetivo: preparar dos aplicaciones sin romper la clásica.

Tareas:

- crear el reactor de migración;
- definir BOM/versiones comunes;
- crear módulos vacíos `core`, `swing`, `gdx`, `assets` y `qa`;
- reproducir desde el módulo Swing el JAR clásico;
- empaquetar el runtime nativo GDX dentro del JAR GDX;
- integrar los assets sin duplicar fuentes;
- documentar comandos de build y estructura de `dist`.

Criterio de salida:

- el JAR clásico del reactor arranca y pasa las pruebas existentes;
- un JAR GDX mínimo arranca en ventana y pantalla completa;
- no se ha cambiado lógica del juego.

### Fase 2 — Bootstrap y servicios compartidos

Objetivo: eliminar la dependencia del arranque respecto a `Init extends JFrame`.

Tareas:

- extraer `CoronaPokerApplication`/bootstrap neutral;
- separar carga de DB, crypto, identidad, actualización y configuración;
- crear servicios tipados para reglas, audio, apariencia y preferencias;
- eliminar gradualmente configuración global de `GameFrame`;
- crear `SwingLauncher` y `GdxLauncher`;
- definir lifecycle: inicio, entrada a partida, salida de partida y cierre.

Criterio de salida:

- ambos launchers inicializan los mismos servicios;
- la música y el audio arrancan una sola vez;
- el frontend no controla red, DB ni crypto;
- la aplicación puede cerrar sin matar el proceso.

### Fase 3 — Modelo de dominio independiente de UI

Objetivo: separar el estado real de sus widgets.

Tareas:

- crear `CardCode` y `CardState`;
- crear `PlayerState`, `LocalPlayerState` y `RemotePlayerState`;
- crear `TableState`, `HandState`, `PotState` y `TurnState`;
- eliminar tipos AWT/Swing de las interfaces de dominio;
- separar estado y controlador de `LocalPlayer`/`RemotePlayer`;
- proporcionar adaptadores Swing para mantener comportamiento.

Criterio de salida:

- los modelos de core compilan sin imports gráficos;
- Swing sigue pasando escenarios de partida;
- no existe una segunda implementación de reglas.

### Fase 4 — Crupier directo a contratos neutrales

Objetivo: sustituir accesos visuales de `Crupier` de forma sistemática.

Tareas:

- inventariar los accesos `GameFrame.getInstance()` por categoría;
- reemplazarlos por `GameSession`, repositorios de estado y puertos de presentación;
- mapear cada `GUIRun` y `GUIRunAndWait` a una operación neutral;
- convertir cada animación causal en un evento explícito;
- conservar locks, orden y puntos de espera clásicos;
- añadir pruebas de orden de eventos;
- prohibir polling y lectura de widgets.

Criterio de salida:

- `Crupier` no importa Swing/AWT/GDX;
- todas las barreras relevantes tienen pruebas;
- los escenarios clásicos producen los mismos resultados y mensajes de red;
- la UI Swing funciona a través del nuevo contrato.

### Fase 5 — Lobby, chat y sesión compartidos

Objetivo: extraer la lógica no visual de `WaitingRoomFrame`.

Tareas:

- crear `LobbySession` y estado de participantes;
- separar comandos del host y estados ready;
- extraer modelo/historial de chat;
- modelar mensajes de texto, emoji, GIF, imagen y voz;
- separar reconexión y recuperación;
- mantener render HTML solo en Swing;
- definir render rico equivalente para GDX.

Criterio de salida:

- `WaitingRoomFrame` queda como vista/adaptador Swing;
- la sesión puede probarse sin abrir ventanas;
- no cambia el protocolo ni el comportamiento de red.

### Fase 6 — Swing como oráculo de regresión

Objetivo: estabilizar el frontend clásico sobre el nuevo core antes de depender de GDX.

Tareas:

- conectar todas las pantallas Swing al core;
- ejecutar la suite completa;
- comparar escenarios, pausas, bots y registros;
- validar dos clientes Swing reales;
- verificar música, barajas, mods y salida.

Criterio de salida:

- `CoronaPoker-Swing.jar` ofrece paridad funcional con 24.10;
- no hay regresiones de protocolo, dinero ni recuperación;
- el core ya no necesita widgets.

### Fase 7 — Aplicación GDX: shell, menús y lobby

Objetivo: disponer de la aplicación GDX completa antes de conectar la mesa.

Tareas:

- implementar navegación de escenas;
- portar splash y menú principal;
- portar crear/unirse;
- portar ajustes generales;
- portar sala de espera y chat;
- implementar diálogos/overlays con futuros;
- implementar foco, teclado, clipboard y scroll;
- implementar lifecycle de audio y ventana.

Criterio de salida:

- se puede arrancar, crear/unirse, chatear, entrar/salir del lobby y cerrar usando solo GDX;
- no se crea ninguna ventana Swing;
- los errores y cancelaciones se muestran y resuelven correctamente.

### Fase 8 — Convertir la demo exacta en mesa real

Objetivo: que el motor real controle la mesa que ya se ve bien.

Procedimiento obligatorio:

1. Copiar/conservar íntegramente el renderer visual aprobado.
2. Congelar capturas de referencia.
3. Extraer del código únicamente el guion simulado y los datos hardcodeados.
4. Introducir `TableProjection` y la cola de `TableVisualEvent`.
5. Conectar un evento real cada vez, manteniendo la misma función de dibujo/animación.
6. Validar visualmente tras cada familia de eventos.

Orden de conexión:

- apertura y layout;
- jugadores/avatares/stacks;
- posiciones y ciegas;
- barajado y audio;
- reparto;
- turno y barra común;
- acciones y fichas;
- flop/turn/river;
- cartas locales y swap;
- fold/desactivación;
- all-in;
- showdown/resaltado;
- pago y limpieza entre manos;
- entrada/salida de jugadores y reflow.

Criterio de salida:

- mismo aspecto que la demo en capturas equivalentes;
- eventos procedentes directamente del core;
- ningún timer que observe Swing;
- ningún dato simulado en una partida real;
- ninguna regla dentro del renderer.

### Fase 9 — Funcionalidad completa durante la timba

Objetivo: eliminar cualquier hueco que obligue a abrir Swing.

Tareas:

- HUD local y todos los atajos;
- menú contextual completo y funcional;
- cambio de baraja con clic derecho;
- registro GDX;
- ajustes GDX permitidos durante la partida;
- pausa, salida, rebuy, RIT y straddle;
- quick chat, emoji, imágenes y voz;
- reconexión, recuperación y fin de timba;
- accesibilidad básica y navegación por teclado.

Criterio de salida:

- todas las acciones disponibles en Swing durante la partida tienen contraparte GDX;
- no hay botones decorativos ni menús sin cablear;
- salir de la timba devuelve al flujo correcto sin bloquear el proceso.

### Fase 10 — Compatibilidad, rendimiento y robustez

Objetivo: demostrar que GDX es un frontend del mismo juego.

Tareas:

- partidas Swing host / GDX cliente;
- partidas GDX host / Swing cliente;
- varios clientes mixtos;
- pérdida de red y reconexión;
- recuperación de mano;
- RIT, all-in, side pots y abandono;
- layouts de 10 a 2 jugadores;
- 60, 120, 144 y 240 Hz;
- distintas resoluciones y escalado de Windows;
- audio con y sin dispositivo disponible;
- mods de baraja y cinematics;
- perfil de CPU, GPU, memoria y pausas de GC.

Criterio de salida:

- protocolo idéntico;
- resultados económicos idénticos;
- sin carreras nuevas;
- frame pacing estable;
- uso de GPU correcto sin forzar carga inútil;
- Swing continúa operativo en equipos modestos.

### Fase 11 — Empaquetado, documentación y entrega

Objetivo: producir artefactos transportables y mantenibles.

Tareas:

- fat JAR `CoronaPoker-24.11-swing.jar`;
- fat JAR `CoronaPoker-24.11-gdx.jar` con nativos;
- scripts de lanzamiento Windows;
- versionado y manifests;
- mods y assets verificables;
- manual de build limpio;
- matriz de características;
- notas de compatibilidad;
- procedimiento de rollback;
- actualización de este documento con estado final.

Criterio de salida:

- ambos JAR arrancan desde una carpeta limpia;
- hashes y contenido comprobados;
- QA automatizada en verde;
- validación visual/manual registrada;
- nada se integra en `master` sin autorización.

---

## 10. Estrategia de limpieza y orden

La limpieza se hará por clasificación, nunca por borrado impulsivo.

### 10.1 Conservar

- todo el historial de la demo aprobada;
- assets mod entregados por el usuario;
- pruebas y matrices útiles;
- contratos neutrales que sobrevivan a una revisión de dependencias;
- música, sonidos, GIF, HQ, shaders y recursos visuales validados;
- stash fallido hasta terminar la extracción selectiva.

### 10.2 Reescribir

- adaptadores que dependan de `GameFrame`;
- `LiveTableSnapshotFactory` y `LiveTableEventSource` si leen Swing;
- routing de comandos que acceda a botones/spinners;
- provider dinámico de renderer si deja de tener sentido con dos JAR separados;
- snapshots que mezclen estado real con presentación.

### 10.3 Descartar solo después de verificar

- polling cada 33 ms;
- inferencia de transiciones;
- GameFrame oculto como controlador de GDX;
- renderer GDX alternativo que no use la demo;
- menús o diálogos GDX de prueba sin funcionalidad;
- caches como `.m2` locales únicamente cuando se confirme que son regenerables.

### 10.4 Reglas operativas

- no trabajar directamente en `master`;
- una responsabilidad por commit;
- commits pequeños y reversibles;
- ningún cherry-pick masivo de la integración fallida;
- antes de mover archivos, registrar su origen y destino;
- no mezclar extracción de lógica con rediseño visual en el mismo commit;
- no almacenar binarios generados junto a fuentes salvo assets intencionados;
- documentar cualquier excepción temporal y su fecha de retirada.

---

## 11. Matriz de pruebas

### 11.1 Pruebas automatizadas

- reglas y cálculo de botes;
- secuencia de calles;
- acciones válidas e inválidas;
- locks, pausas y barreras;
- serialización y protocolo;
- crypto e identidad;
- persistencia y migraciones;
- reconexión y recuperación;
- comandos de UI;
- orden exacto de eventos visuales;
- finalización/cancelación de futuros de diálogo;
- lifecycle de frontend.

### 11.2 Pruebas de contrato

El mismo escenario de core se ejecutará con:

- frontend falso determinista;
- adaptador Swing;
- adaptador GDX headless cuando sea posible.

Se comprobará que ambos reciben el mismo estado y generan los mismos comandos.

### 11.3 Pruebas visuales

Capturas obligatorias para:

- 10, 9, 8, 7, 6, 5, 4, 3 y 2 jugadores;
- cartas tapadas y showdown completo;
- todas las acciones;
- ganador/perdedores;
- distintos tamaños de bote;
- HUD local en todas sus variantes;
- menús y diálogos;
- resoluciones y escalados soportados.

Compilar no demuestra fidelidad visual. Las capturas y una sesión manual son criterios independientes.

### 11.4 Pruebas audiovisuales y temporales

- audio de barajado alineado con frames;
- música sin duplicación ni interrupción;
- fichas impactan antes de actualizar cantidades;
- barajado termina antes de repartir;
- fichas llegan al bote antes de revelar calle;
- swap termina sin corrección visual posterior;
- tiempo de pensar no altera la velocidad de bots;
- animaciones no cambian la lógica ni los timeouts de red.

### 11.5 Pruebas de compatibilidad real

- dos instancias locales;
- host Swing y cliente GDX;
- host GDX y cliente Swing;
- mezcla de versiones durante transición cuando el protocolo lo permita;
- red lenta, desconexión y reentrada;
- cierre voluntario y cierre inesperado.

---

## 12. Riesgos y mitigaciones

| Riesgo | Impacto | Mitigación |
|---|---|---|
| alterar la lógica al extraer UI | crítico | pruebas de caracterización antes de cada extracción; Swing como oráculo |
| bloquear hilo de render GDX | alto | futuros asíncronos; prohibir waits en render thread |
| romper locks clásicos | crítico | mapa explícito de cada `GUIRunAndWait` y pruebas de orden |
| duplicar estado entre frontend y core | crítico | core como única autoridad; proyecciones inmutables |
| degradar la demo al conectarla | alto | congelar renderer/capturas; sustituir solo fuentes de datos |
| olvidar una función Swing | alto | inventario pantalla por pantalla y matriz de paridad |
| romper protocolo | crítico | cero campos de UI en mensajes; pruebas mixtas Swing/GDX |
| audio duplicado/desfasado | alto | un único `AudioPort`; eventos causales y pruebas temporales |
| perder mods/assets sin seguimiento | alto | inventario y respaldo antes de limpiar |
| build Maven inmanejable | medio | reactor transitorio; mover la raíz solo al final |
| UI GDX lenta en equipos concretos | medio | perfil y frame pacing; Swing permanece disponible |

---

## 13. Enfoques expresamente prohibidos

1. Mantener Swing oculto y usarlo como modelo de GDX.
2. Leer periódicamente `GameFrame`, `JLabel`, `JSpinner` o `JButton`.
3. Inferir que hubo una apuesta porque cambió una cifra.
4. Reescribir las reglas dentro de `LocalPlayerGdx` o `RemotePlayerGdx`.
5. Crear clases de jugador GDX con lógica paralela.
6. Hacer dormir al crupier para compensar una animación.
7. Lanzar una animación sin que su futuro represente el final real.
8. Abrir diálogos Swing desde la aplicación GDX.
9. Introducir un `GameFrame` alternativo que vuelva a ser un contenedor de estado global.
10. Declarar paridad solo porque compila o porque una mano sencilla termina.
11. Cambiar la velocidad de bots para que encaje con una presentación.
12. Limpiar archivos no rastreados sin confirmar si son assets del usuario.

---

## 14. Definición de terminado

La migración se considerará completa únicamente si se cumplen todos estos puntos:

- [ ] `master` continúa protegido hasta la integración autorizada.
- [ ] existen dos JAR independientes y ejecutables;
- [ ] ambos usan un único `coronapoker-core`;
- [ ] core no importa Swing, AWT ni libGDX;
- [ ] GDX no crea ni mantiene componentes Swing;
- [ ] no existe polling de UI ni inferencia de eventos;
- [ ] `Crupier` conserva reglas, locks, pausas y orden clásicos;
- [ ] el protocolo es común y los clientes mixtos funcionan;
- [ ] todas las pantallas y diálogos tienen versión GDX;
- [ ] todos los controles GDX están realmente cableados;
- [ ] la mesa es visualmente fiel a la demo aprobada;
- [ ] los colores de acciones y showdown son los originales;
- [ ] la barra de tiempo común está bajo las comunitarias;
- [ ] las cartas, asientos y reflow son correctos de 10 a 2 jugadores;
- [ ] barajado, reparto, fichas, calles y showdown respetan las barreras;
- [ ] música, sonidos, mods, HQ, GIF y cinematics funcionan;
- [ ] salida, reconexión, recuperación y fin de timba no bloquean;
- [ ] Swing mantiene paridad con 24.10;
- [ ] la suite automatizada está en verde;
- [ ] existe validación manual y visual registrada;
- [ ] los JAR se prueban desde una carpeta limpia de distribución.

---

## 15. Entrega y continuidad en otra máquina o equipo

Este documento debe viajar junto con:

1. clon del repositorio;
2. referencia a `master` en `9497f1e25`;
3. rama `feature/gdx-demo-live`;
4. commit visual `627c71e4f` y su historial posterior de correcciones;
5. assets no versionados previamente inventariados y empaquetados;
6. matriz de paridad actualizada;
7. comandos de build de los dos JAR;
8. resultados de QA y capturas visuales;
9. registro de decisiones y excepciones temporales.

La primera acción en otra máquina será verificar hashes y ejecutar la demo aislada. La segunda será compilar CoronaPoker 24.10 desde `master`. No se debe continuar la migración si cualquiera de esas dos referencias no puede reproducirse.

---

## 16. Próximo bloque de trabajo recomendado

El siguiente bloque no debe empezar escribiendo más renderer. Debe dejar una base limpia y demostrable:

1. crear el inventario formal del working tree actual;
2. proteger assets no rastreados;
3. generar la tabla `conservar / reescribir / descartar` por archivo;
4. crear el reactor transitorio;
5. construir el JAR Swing sin cambios funcionales;
6. añadir reglas automáticas que impidan imports Swing/GDX en core;
7. extraer el bootstrap común;
8. entregar dos ejecutables mínimos antes de tocar el crupier;
9. extraer un primer flujo vertical completo y pequeño, con prueba de paridad;
10. continuar por eventos causales, uno a uno, hasta controlar la demo desde el motor real.

El principio rector será siempre el mismo:

> El motor decide. El frontend informa y representa. La demo define cómo debe verse GDX. Swing define la compatibilidad funcional que no se puede perder.
