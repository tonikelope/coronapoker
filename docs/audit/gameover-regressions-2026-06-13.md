# Informe — Regresiones del game-over / rebuy (2026-06-13)

Detectadas por el autor en smoke de la rama `fix-audit-gameframe-crupier`. Investigadas a fondo y corregidas. Ninguna venía del hardening de hoy; ambas del rework del game-over de **ayer (21.46, commits `9ab939f5` + `491cfb18`)**, destapadas ahora.

## Contexto: el rework de game-over (21.46)
- `9ab939f5` "game over local interactivo como overlay sobre la mesa viva": el game-over deja de ocultar la mesa; arranca los GIF de cuenta atrás de los arruinados **remotos** a la vez que el diálogo local (`startRebuyingVisuals` en `checkRebuyTime`), con `LOCAL_GAMEOVER_OWNS_AUDIO` para no doblar audio.
- `491cfb18` "no ocultar la mesa tampoco en el game over directo": quita el `hideALL`/`showALL` también del directo.
- Resultado: durante el game-over la mesa queda VIVA con visuales remotos asíncronos, que antes no existían.

## BUG 2 — el GIF de un remoto que recompra no pasa a RECOMPRA (determinista)
**Síntoma:** con el server (local) y un cliente ambos en game-over, el cliente recompra pero en el server su GIF de game-over sigue animando en vez de mostrar RECOMPRA (gafas). Bidireccional. El rebuy real funciona (mano siguiente OK); solo el visual falla. "Funcionaba hace unos días."

**Causa:** `9ab939f5` arranca los GIF remotos pronto (overlay, `checkRebuyTime`), pero el procesado del REBUY remoto —que llama `setRebuying(false, recompra)` → GIF→RECOMPRA (Crupier ~3452)— sigue **solo en `recibirRebuys`**, que corre **después** de que el `gameover_dialog` modal local se cierre (`setVisible(true)` bloquea el hilo del crupier en `GUIRunAndWait`). Mientras el local decide, el REBUY del remoto queda encolado sin procesar → su GIF (arrancado pronto) sigue. Antes de 21.46 los GIF remotos solo arrancaban en `recibirRebuys`, junto a su procesado → sin desfase.

**Decisión del autor:** mantener el overlay (la mesa viva se ve bien); cuando el LOCAL también se arruina, **no lanzar la cuenta atrás remota** (saldría desincronizada — los remotos ya cuentan en su máquina) y **mostrar solo el desenlace RECOMPRA** por cada remoto.

**Fix:**
- `RemotePlayer.showRebuyOutcome(recompro)`: muestra RECOMPRA (gafas) sin haber lanzado cuenta atrás (el caso "no recompra" lo pinta `setSpectator`).
- `Crupier.checkRebuyTime`: captura `local_ruined`; elimina el arranque temprano (`LOCAL_GAMEOVER_OWNS_AUDIO=true` + `startRebuyingVisuals`); pasa `local_ruined` a `recibirRebuys`.
- `Crupier.recibirRebuys(pending, skip_countdown)`: si `skip_countdown` (= local también arruinado) no lanza `startRebuyingVisuals` y en el REBUY usa `showRebuyOutcome` en vez de `setRebuying(false)`. Si el local NO se arruinó, la cuenta atrás clásica se mantiene (synced, sin diálogo que bloquee).
- `LOCAL_GAMEOVER_OWNS_AUDIO` queda inerte (nunca se pone true); gate y resets son no-ops benignos (se pueden limpiar más adelante).

## BUG 1 — al salir, los remotos reaparecen sobre el balance final (race, intermitente)
**Síntoma:** el server sale de la timba, sale el balance, y DESPUÉS se repintan varios/todos los remoteplayers sobre el balance. Intermitente. "No ha pasado en la puta vida."

**Causa (race confirmada con el autor):** un jugador se fue → el tablero se acortó (`Crupier:13833 if (exit>0) downgradeAndRefreshTapete()` → `TablePanelFactory.downgradePanel` crea un tablero NUEVO con paneles **visibles por defecto** y lo swapea en un `GUIRunAndWait`). El server salió en esa MISMA mano → `setFin_de_la_transmision(true)` + `hideALL` + balance. El swap del tablero nuevo, encolado en el EDT, salía **después** del `hideALL` → los paneles nuevos (visibles) aparecían sobre el balance. Es una **race entre la factoría del tablero acortado y la salida**. `showALL` es código muerto (cero callers) — descartado como vía; eran los paneles NUEVOS del rebuild. Latente desde siempre; destapada por cambios de timing recientes (21.46 y/o los fixes de notify que hacen despertar antes los hilos).

**Fix (principio del autor: "si la partida terminó, no debe mostrarse ningún panel"):**
- `GameFrame.downgradeAndRefreshTapete`: early-return si `isFin_de_la_transmision()` (no reconstruir una mesa que se está cerrando) + guard TOCTOU dentro del `GUIRunAndWait` que hace `nuevo_tapete.hideALL()` si la partida terminó durante el swap. Cierra la race por ambos lados.

## Commit
`2513ede3` en rama local `fix-audit-gameframe-crupier`. BUILD SUCCESS. NO pusheado. Smoke del autor pendiente (reproducir: cliente+server ambos game-over con recompra; y salir en una mano donde un jugador se fue y el tablero se acortó).

## Atribución
- NO son de los 14 commits de hardening de hoy (mi único toque a RemotePlayer fue borrado de código muerto; verificado por diff). Son del rework de game-over de ayer (9ab939f5/491cfb18), 21.46.
- 3 falsos positivos de agente cazados durante toda la campaña (markPlayerAsCheater, UPnP-en-EDT, server_history).
