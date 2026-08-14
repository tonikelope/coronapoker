# Residuo manual mínimo de la auditoría 23.45

Este documento no sustituye a TDD. Enumera únicamente los cruces que no tienen
hoy un harness determinista de dos instancias Swing/cliente o que dependen de
la identidad ACL del Windows real. Todo lo demás está cubierto por las lanes
fast, `slow-crypto`, `slow-integration` y las pruebas puras de pots/rebuys.

## Preparación común

- Usar el JAR de `release/23.45-game-fixes`, la misma versión en las dos
  instancias, JDK 25 y el Windows del propietario de la identidad.
- Crear una partida local con registro visible y conservar el log de cada
  instancia. Anotar commit, fecha/hora, SO, Java, configuración de buy-in,
  límite de rebuys y si se usó cinematic.
- Antes y después de cada caso comprobar `sum(stacks) + bote_sobrante` y que
  todos los peers muestran el mismo número de mano.

## Casos que sí deben ejecutarse en dos clientes

### 1. All-in desconectado en cada calle

1. Preparar cuatro jugadores con compromisos 2, 5, 10 y 10; los dos primeros
   hacen `ALLIN` y cierran/desconectan uno a uno antes del flop, turn y river.
2. Dejar que los otros dos completen el run-out y el showdown.
3. Repetir una vez haciendo `FOLD` y saliendo en lugar de `ALLIN`.

Esperado: dos side pots (8, 9 y 10 en el caso 2/5/10/10), conservación de 27,
los all-ins desconectados siguen elegibles para las capas que igualaron y el
fold desconectado nunca gana. No debe quedar la mesa esperando su turno ni
caer el showdown por una carta no revelada.

### 2. RIT/Rabbit/IWTSTH después de una salida

Con dos o más jugadores all-in, activar RIT y, en partidas separadas, Rabbit e
IWTSTH. Desconectar un all-in justo antes de `POTCARDS`; esperar ambas ramas y
cerrar el showdown.

Esperado: las barreras terminan, los dos boards/fees conservan el bote y el
jugador `FOLD` sigue fuera. Si aparece una espera o divergencia, conservar los
logs y no cambiar la liquidación sin un test de barrera reproducible.

### 3. Ventana de `REBUY` remota

Con un host y un cliente humano agotado, probar aceptar, cancelar, respuesta
duplicada, respuesta tardía, desconexión durante la ventana y límite alcanzado.
Para el importe sobredimensionado se puede usar un cliente de prueba o una
captura/reinyección autorizada del comando autenticado; nunca desactivar HMAC.

Esperado: host y cliente reciben el mismo importe canónico `0..headroom`, una
denegación elimina cualquier entrada optimista y la siguiente mano no crea
fichas. Un nick desconocido o un comando fuera de ventana no debe modificar
`rebuy_now` ni SQLite.

### 4. `REBUYNOW` inmediato

Con dos clientes cuyos stacks tengan headroom distinto, solicitar un importe
mayor que el headroom del host; después repetir con segundo click (toggle-off),
límite de rebuys y stack ya en el tope.

Esperado: todos reciben el importe canónico del host, incluido el cliente que
originó la solicitud; el toggle-off y `REBUYDENIED` eliminan la entrada en todos
los peers. Al comenzar la mano siguiente solo se aplica ese importe común.

### 5. Recuperación/SQLite

Interrumpir la conexión durante la ventana de rebuy y durante el cierre de
mano; reconectar el cliente y esperar la recuperación. Revisar que no se
duplica el contador de rebuy, que el `hand_id` y el settlement coinciden y que
no aparece un `disputed_hands` espurio.

## Residuo de ACL del runner

Ejecutar bajo la cuenta propietaria real (no el sandbox) la clase
`IdentityKeypairAclSmoke` y los seis casos de `ReceiptSignatureTest`. En este
runner fallan solo por ACL (`PEPINAZO\\Antonio` frente a la identidad del
proceso); no se debe tocar producción para “arreglar” ese resultado.

## Criterio de cierre

Registrar por caso pasos, esperado, observado y log. Si un caso manual falla,
abrir primero un test de protocolo/barrera/SQLite que lo reproduzca. Si no se
puede construir sin introducir una regresión o relajar la seguridad, dejarlo
como no-cambio explícito; no convertir una prueba de UI en un falso verde
automático.
