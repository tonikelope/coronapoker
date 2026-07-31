# Informe final de la auditoría

**Rama**: `audit-r3-fixes` → **master**: `32432508` (intacto, sin tocar en toda la sesión)
**Trece rondas de auditoría adversaria** · **~40 auditores independientes**

---

## Veredicto

**El código está listo para mergear.** La última ronda no obligó a tocar código, que era el
criterio de parada acordado.

Con una condición: **falta el smoke manual**. Hay cosas de red que sólo se ven jugando y
ningún revisor puede firmarlas leyendo. Checklist aparte, unos 45 minutos.

### Los cuatro revisores de aceptación

Miraron el resultado final completo, sin conocer cómo se llegó a él, con una sola pregunta:
¿es seguro mergear?

| Revisor | Veredicto |
|---|---|
| Dinero y reglas de póker | **SEGURO DE MERGEAR** |
| Red, hilos y concurrencia | **NO MERGEAR** → el fallo se arregló y se reverificó |
| Barrido mecánico | **SEGURO DE MERGEAR** (8 defectos, todos de forma, corregidos) |
| Atacante | **NO ENCONTRÓ NADA** que rompa una partida ni que mueva dinero mal |

---

## Qué se arregló

**128 fallos**: 64 de un inventario previo y 64 encontrados durante la auditoría.

### Lo que venía de master y ahora está arreglado

| Fallo | Qué pasaba |
|---|---|
| **Se creaban fichas de la nada** | En cada mano que acababa sin nadie en pie, el pico heredado se contaba dos veces y el ganador de la mano siguiente cobraba de más |
| **Botes laterales mal repartidos** | Un all-in corto podía llevarse 45 fichas habiendo igualado sólo 15 |
| **Cara a cara postflop invertido** | Hablaba primero quien no debía, invirtiendo la ventaja de posición toda la fase final |
| **El run-it-twice destruía fichas** | Con la segunda cara abortada, faltaban ~15 fichas y el auditor de cuentas cantaba descuadre |
| **Se acusaba de tramposo a un inocente** | A quien se le iba la red durante el barajado se le nombraba en rojo y se le expulsaba |
| **Se echaba al que reconectaba** | Si se caía otra vez antes de enviar nada, se le expulsaba sin darle su ventana |
| **Un byte inyectado echaba a un jugador** | La guarda que decía evitarlo se capturaba a sí misma y no funcionaba |
| **El arranque moría con las preferencias corruptas** | Y al arrancar con lo que se pudiera leer, el primer guardado se llevaba las estructuras de ciegas |

Más los bucles infinitos, los dos abrazos mortales, el bote huérfano que destruía dinero y el
resto del inventario inicial.

### Cobertura

**640 métodos de prueba**, seis ficheros nuevos:

- **Tres con sockets reales** que fijan las premisas del manejo de caídas: que escribir a quien
  no lee bloquea, que cerrar despierta esa escritura y que despierta también una lectura.
  Ninguna estaba verificada, y el diseño entero se apoya en ellas.
- **Cinco de estructura de los ficheros de idioma**, verificados a la contra.
- **Los de botes laterales**, que fallan contra master: prueban el arreglo de verdad.

Aviso honesto: `MisdealRefundOrderSmoke` **no toca código de producción**. Es documentación
ejecutable del razonamiento, no una red de seguridad, y así lo dice en su primera línea.

---

## Lo que NO se arregló

**Siete fallos preexistentes**, documentados en `docs/PENDIENTES-RED.md` con su análisis y, lo
más importante, **por dónde no atacarlos**:

1. Carrera al reconectar que difunde una caída falsa.
2. El cliente no tiene tope de cola.
3. El aviso de salida no llega a quien está reconectando.
4. El latido del cliente escribe sin plazo reteniendo su propio candado.
5. La congelación de plazos hace inexpulsable al que retiene la mesa.
6. Tres esperas del cliente sin plazo.
7. Un alta con la identidad mal formada se acepta con un simple aviso.

Y uno de contabilidad: los cuatro sitios que suman al bote no cogen el candado de
contabilidad.

Los siete tocan el hilo lector o la sincronización de red, y ninguno se puede validar sin
jugar con varias máquinas.

---

## Lo que se intentó y se revirtió

Por honestidad, porque es la parte más instructiva:

- **El aviso de salida**, tres intentos. Los dos primeros colgaban al anfitrión tres minutos y
  acusaban a un inocente. El tercero funcionó al atacar lo correcto: quitar la espera de
  confirmación, no mover el envío.
- **Apagar la cadena de firmas** si falta una identidad: parecía proteger y dejaba a la
  víctima sin verificar nada, con sus propias acciones foldeadas por todos en silencio.
- **Sumar la tarifa del conejo al bote**: metía la primera operación leer-modificar-escribir
  desde otro hilo y el peor caso perdía las ciegas enteras.
- **Dos guardas en la liquidación** que, en vez de proteger, **creaban fichas**.
- El marcado del segundo autocierre, que dejaba al peer problemático inexpulsable.

En todos, ante la imposibilidad de demostrar que el arreglo era seguro, **se volvió a master**.

---

## El dato que sostiene la confianza

Durante la sesión se cometieron **doce regresiones**. **Ninguna llegó a master**: todas se
cazaron dentro de la rama.

Y ninguna la cazaron los 640 tests. Las cazó la auditoría adversaria leyendo código con
escenarios numerados. Por eso el criterio para mergear nunca fue "los tests están verdes".

---

## Antes de mergear

1. **Smoke manual** (checklist aparte). El bloque importante es el de dos instancias en el
   mismo equipo por `localhost`: cambiar la contraseña dos veces seguidas, matar una a lo
   bruto y reconectar, y que alguien se vaya a mitad de mano.
2. **Decidir qué hacer con las partidas guardadas.** El arreglo del orden postflop en cara a
   cara cambia quién habla primero, así que una partida grabada con la versión anterior puede
   recuperarse con las acciones desplazadas. Salta un aviso en el registro, no aborta. O se
   documenta en las notas, o se invalidan las partidas guardadas al cambiar de versión.
