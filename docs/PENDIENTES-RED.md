# Pendientes de red

Seis fallos **preexistentes** encontrados durante la auditoría de julio de 2026. Ninguno lo
introdujo esa sesión: todos estaban ya en el juego. Se dejaron fuera a propósito porque los
seis tocan el hilo lector o la sincronización de red, y esa zona necesita trabajarse en frío
y validarse jugando, no sólo leyendo.

El análisis está hecho. Lo que falta es implementarlo con calma.

---

## 1. La congelación de plazos hace inexpulsable al que retiene la mesa

**Dónde**: `Crupier`, los cinco plazos de progreso (arranque de mano, acción, cascada,
confirmación de reparto, confirmación de recuperación).

**Qué pasa**: mientras un jugador está en su ventana de reconexión, esos plazos se congelan
para no expulsar a quien simplemente está volviendo. Pero **no pausan el reloj: lo reinician
entero** en cada vuelta. Y la condición que los congela es global (`isSomePlayerTimeout()`),
así que basta un jugador en esa situación para congelarlos todos.

**Consecuencia**: un peer que se cae y reconecta cada pocos minutos mantiene los plazos
permanentemente reiniciados. Nunca vencen, así que nunca se le expulsa, y si además retiene
un reparto la mesa se queda esperándole indefinidamente.

**Por dónde ir**: que la congelación **pause** el reloj (guardar lo consumido y reanudar) en
vez de reiniciarlo, y que sea **por jugador** en vez de global. Hace falta también algún tope
de reconexiones: **no existe ninguno**. El contador que se lleva por peer es sólo
telemetría (alimenta el indicador de enlace) y no echa a nadie.

---

## 2. Tres esperas del cliente sin plazo

**Dónde**: `Crupier`, esperas de recibir las cartas, del disparo de verificación y del
arranque de la mano siguiente.

**Qué pasa**: si el anfitrión se cae del todo, el socket muere y el cliente sale. Pero si su
proceso sigue vivo y lo que se atasca es el hilo que reparte, el cliente espera para siempre.

**Ya se intentó y salió MAL**: se les puso plazo y era peor, porque al vencer el cliente
seguía la mano **a ciegas** y se saltaba el único punto donde una anulación tardía la corta.
Se revirtió.

**Por dónde ir**: lo difícil no es el plazo, es decidir qué hacer al vencer. Probablemente
anular la mano y recuperar. Eso cambia el comportamiento de las partidas y necesita pruebas
con varias máquinas.

**Mitigación ya aplicada**: pasados diez minutos se avisa al jugador de que la mesa lleva
parada, con el reloj respetando las pausas. Ya no es un cuelgue mudo.

---

## 3. El latido del cliente escribe sin plazo y reteniendo su propio candado

**Dónde**: el latido del cliente y la escritura de `NetClient`.

**Qué pasa**: es el mismo fallo que se arregló en el anfitrión, sin arreglar en el cliente, y
**peor**: el cliente mantiene su candado de socket durante toda la escritura bloqueante, y su
propia red de seguridad (cerrar el socket) necesita ese mismo candado. Un anfitrión que deje
de leer cuelga el transporte de salida del cliente, que **no puede autocurarse**: ni cerrar,
ni reconectar, ni escribir.

**Por dónde ir**: replicar lo que se hizo en el anfitrión (envolver la escritura del latido en
un plazo y cerrar el socket al agotarse), pero el cliente no tiene esa infraestructura y hay
que construirla. Y el candado hay que soltarlo antes de escribir.

---

## 4. El cliente no tiene tope de cola

**Dónde**: la cola del lector de `NetClient`.

**Qué pasa**: el anfitrión limita a diez mil mensajes por peer; el cliente no tiene límite. Un
anfitrión hostil puede tumbar a sus clientes por memoria.

**Cuidado al hacerlo**: los cinco sitios que encolan usan una operación que **bloquea** si la
cola está llena, y dos de ellos encolan la **señal de cierre**. Poner el tope sin más
reproduce el fallo que costó tres rondas arreglar en el anfitrión: la señal no entra, el
consumidor se queda dormido y la mesa espera a alguien que ya se fue. Hay que replicar el
mecanismo del anfitrión (encolado con reintento para los mensajes, y encolado que hace hueco
para la señal de cierre).

---

## 5. Carrera al reconectar: se difunde una caída falsa

**Dónde**: hilo lector frente al que instala el socket nuevo.

**Qué pasa**: el que reconecta baja la marca de caída bajo candado; el lector la escribe **sin
candado**. Si el lector estaba a mitad de procesar el fin del socket viejo, vuelve a marcar
como caído a un peer que **ya ha reconectado** y difunde el aviso a toda la mesa: borde
magenta y sonido de error para alguien que está perfectamente.

**Alcance**: ventana de microsegundos y se cura sola en cinco segundos, al primer latido.
Molesto, no grave.

**Por dónde ir**: que el lector escriba esa marca bajo el mismo candado, o que vuelva a
comprobar si ha entrado una reconexión antes de marcar.

---

## 6. El aviso de salida no llega a quien está reconectando

**Dónde**: la difusión del aviso de que un jugador se ha ido.

**Qué pasa**: ese aviso se manda **sin esperar confirmación**, y por tanto sin reintento (fue
deliberado: esperarla colgaba al anfitrión tres minutos y acusaba a un inocente). A quien
tenga la red caída en ese momento el aviso se le escribe a un socket condenado y se pierde. Al
reconectar sólo se le manda el acuse, **no el estado de la mesa**, así que vuelve sin saber
que el otro se fue y espera su turno.

**Estado**: mejor que antes. Antes **nadie** se enteraba de ninguna expulsión; ahora se entera
todo el mundo menos ese caso.

**Por dónde ir**: reenviar el estado de la mesa al aceptar una reconexión (quién está fuera).
Es el arreglo limpio y además cierra otros huecos parecidos.

---

## Cómo abordarlos

1. Empezar por el **5** (el más acotado) y el **4** (autocontenido, con el mecanismo del
   anfitrión ya escrito como referencia).
2. Luego el **6**, que es aditivo: reenviar estado no rompe nada existente.
3. El **3** requiere construir infraestructura en el cliente.
4. El **1** y el **2** son los que cambian comportamiento de partida. Al final, y con smoke
   real por delante.

Y una regla aprendida a base de nueve rondas: **auditar cada arreglo antes de pasar al
siguiente**. Todas las regresiones de aquella sesión salieron de tandas sin revisar, y ninguna
la cazaron los tests automáticos.
