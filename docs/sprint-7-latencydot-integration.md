# LatencyDot — guía visual de integración (5-10 min en NetBeans)

El backend del LatencyDot está 100% wireado. La bolita ya recibe
actualizaciones de latencia + reconnection count cada 5 segundos
(via TELEMETRY broadcast del host). Solo te falta hacerla visible.

## Estado actual

Cada `Player` ya tiene:
- `private volatile LatencyDot latency_dot = null` (inyectable).
- `setLatencyDot(LatencyDot)` / `getLatencyDot()`.
- `applyTelemetry(int lat1, int lat2, int recon)` que delega al dot
  cuando no es null.

El handler `case "TELEMETRY"` en `WaitingRoomFrame.cliente()` ya itera
los peers del frame y llama `applyTelemetry` sobre cada uno
(RemotePlayer + LocalPlayer).

El ping cliente→server también actualiza el LocalPlayer dot tras
cada PONG con feedback inmediato.

**Sin tu intervención visual, todos los `applyTelemetry` son no-op
silencioso. El backend está ahí, esperando.**

## Pasos en NetBeans (5-10 min)

### 1. RemotePlayer.form

1. Abre `src/main/java/com/tonikelope/coronapoker/RemotePlayer.form`
   en NetBeans (vista visual).
2. Arrastra un `JComponent` al lado del avatar (esquina superior
   derecha del componente RemotePlayer recomendada).
3. Click derecho → "Customize Code" → cambia la clase a
   `com.tonikelope.coronapoker.LatencyDot`.
4. En el panel de propiedades, asígnale nombre `latency_dot_widget`.
5. Tamaño preferido recomendado: 16×16 px.
6. Guarda. NetBeans regenera `initComponents()` con la línea
   `latency_dot_widget = new com.tonikelope.coronapoker.LatencyDot()`.

### 2. RemotePlayer.java — wiring tras initComponents

Busca el constructor `public RemotePlayer()` y AÑADE la línea final:

```java
public RemotePlayer() {
    Helpers.GUIRunAndWait(() -> {
        initComponents();
        // ...todo lo existente...
        setLatencyDot(latency_dot_widget);   // ← ESTO ÚLTIMO
    });
}
```

### 3. LocalPlayer.form + LocalPlayer.java — repite los pasos 1 y 2

Mismo procedimiento: drag JComponent → clase LatencyDot →
nombre `latency_dot_widget` → en el constructor `setLatencyDot(latency_dot_widget)`.

### 4. Compilar y lanzar

```
mvn install -DskipTests
```

Lanza partida con host + cliente. A los 5 segundos del primer ping
verás las bolitas cambiando de color.

## Cómo se ve

- 🟢 verde: latencia ≤ 80 ms.
- 🟡 amarillo: 81-200 ms.
- 🟠 naranja: 201-400 ms.
- 🔴 rojo: > 400 ms o sin medir.
- ⚪ gris: sin datos hace más de 15 s (host caído).

Badge numérico (esquina inferior derecha) con el contador de
reconexiones del peer si > 0. "9+" si > 9.

Tooltip al hover: `"120 ms · ↻3 · hace 2s"` (latencia · reconexiones · edad del dato).

## Si algo va mal

- **Bolita siempre gris**: el TELEMETRY broadcast no llega. Verifica
  el log del host por `broadcastTelemetryFrame failed` o que el
  thread `telemetryBroadcasterWatchdog` está vivo.
- **Bolita queda en una latencia vieja**: el LocalPlayer del cliente
  actualiza por su propio ping (cada 5s); los RemotePlayers se
  actualizan por el TELEMETRY broadcast (también cada 5s).
- **No compila tras añadir el componente**: verifica que el nombre
  del campo en NetBeans es exactamente `latency_dot_widget`.

## Si prefieres una posición distinta del avatar

NetBeans Form Editor permite mover el componente libremente con
GroupLayout. Recomendaciones visuales:
- Superpuesto al avatar (esquina superior derecha) — más compacto.
- Al lado del nick del jugador — más legible.
- En la barra del nick — junto al icono de turno actual.

El tamaño 16×16 funciona bien para mostrar tanto la bolita
principal como el badge numérico de reconexiones.
