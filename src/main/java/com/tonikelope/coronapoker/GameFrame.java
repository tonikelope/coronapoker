/*
 * Copyright (C) 2020 tonikelope
 _              _ _        _                  
| |_ ___  _ __ (_) | _____| | ___  _ __   ___ 
| __/ _ \| '_ \| | |/ / _ \ |/ _ \| '_ \ / _ \
| || (_) | | | | |   <  __/ | (_) | |_) |  __/
 \__\___/|_| |_|_|_|\_\___|_|\___/| .__/ \___|
 ____    ___  ____    ___  
|___ \  / _ \|___ \  / _ \ 
  __) || | | | __) || | | |
 / __/ | |_| |/ __/ | |_| |
|_____| \___/|_____| \___/ 

https://github.com/tonikelope/coronapoker
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tonikelope.coronapoker;

import com.drew.imaging.ImageProcessingException;
import static com.tonikelope.coronapoker.Crupier.STREETS;
import static com.tonikelope.coronapoker.Helpers.TapetePopupMenu.BARAJAS_MENU;
import static com.tonikelope.coronapoker.Init.M2;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsDevice;
import java.awt.Rectangle;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLayer;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JProgressBar;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import static com.tonikelope.coronapoker.InGameNotifyDialog.NOTIFICATION_TIMEOUT;
import java.io.UnsupportedEncodingException;
import java.util.Base64;

/**
 *
 * @author tonikelope
 */
public final class GameFrame extends javax.swing.JFrame implements ZoomableInterface, MouseWheelListener {

    public static final int TEST_MODE_PAUSE = 250;
    public static final int DEFAULT_ZOOM_LEVEL = -2;
    public static final float ZOOM_STEP = 0.05f;

    public static final int WAIT_QUEUES = 250;
    public static final int WAIT_PAUSE = 1000;
    public static final int CLIENT_RECEPTION_TIMEOUT = 10000;
    public static final int CONFIRMATION_TIMEOUT = 10000;
    public static final int CLIENT_RECON_TIMEOUT = 2 * Crupier.TIEMPO_PENSAR * 1000; // 2 * TIEMPO_PENSAR: grace extendido cuando el peer ya esta intentando reconectar activamente, y umbral del Reconnect2ServerDialog en el cliente.
    public static final int CLIENT_RECON_ERROR_PAUSE = 5000;
    public static final int REBUY_TIMEOUT = 25000;
    public static final String BARAJA_DEFAULT = "coronapoker";
    public static final String DEFAULT_LANGUAGE = "es";
    public static final int PEPILLO_COUNTER_MAX = 5;
    public static final int PAUSE_COUNTER_MAX = 3;
    public static final int AUTO_ZOOM_TIMEOUT = 3000;
    public static final int GUI_RENDER_WAIT = 125;
    public static final boolean TEST_MODE = false;
    public static final int TTS_NO_SOUND_TIMEOUT = 3000;
    public static final int NOTIFY_INGAME_GIF_REPEAT = 2;
    public static final int HALT_PAUSE = 5000;
    public static final ConcurrentLinkedQueue<Object[]> NOTIFY_CHAT_QUEUE = new ConcurrentLinkedQueue<>();
    public static final Object SQL_LOCK = new Object();

    public static volatile double CIEGA_PEQUEÑA = 0.10;
    public static volatile double CIEGA_GRANDE = 0.20;
    public static volatile int BUYIN = 10;
    public static volatile boolean FIXED_BUYIN = true; //true = todos arrancan con BUYIN (techo de recompra = BUYIN); false = cada jugador elige su buy-in al entrar al tablero en [BUYIN_MIN_BB, BUYIN_MAX_BB] CG (techo de recompra = BUYIN_MAX_BB CG)
    // Rango de buy-in editable (en ciegas grandes). Por defecto 10-100 CG (rango
    // histórico). El host puede ampliarlo para mesas deep-stack (superior hasta
    // BuyinRules.CEIL_MAX_BB). Acota la elección de buy-in en variable y, vía el
    // cap, el techo de recompra. Viaja a los clientes en el INIT y se persiste en
    // recover (ver Crupier/WaitingRoomFrame y serializeRecoverSettings).
    public static volatile int BUYIN_MIN_BB = BuyinRules.DEFAULT_MIN_BB;
    public static volatile int BUYIN_MAX_BB = BuyinRules.DEFAULT_MAX_BB;
    // Política del tope máximo de recompra / top-up:
    //  - BUYIN: el buy-in (fijo = BUYIN; variable = límite superior BUYIN_MAX_BB CG).
    //  - HIGHEST_STACK: el stack más alto de la mesa (recomprar hasta igualar al
    //    líder de fichas, típico de deep-stack).
    // Por defecto BUYIN (comportamiento histórico). Solo afecta a la recompra, no
    // a la compra inicial. Viaja en el INIT y se persiste en recover.
    public static final int REBUY_CAP_BUYIN = 0;
    public static final int REBUY_CAP_HIGHEST_STACK = 1;
    public static volatile int REBUY_CAP_POLICY = REBUY_CAP_BUYIN;
    public static volatile int CIEGAS_DOUBLE = 60;
    public static volatile int CIEGAS_DOUBLE_TYPE = 1; //1 MINUTES, 2 HANDS
    public static volatile double BLIND_CAP = 0; //0 = sin tope; en otro caso, no se dobla si el siguiente nivel haria que la ciega grande la superase
    public static volatile boolean ANTE = false; //true = cada jugador activo postea un ante (= ciega pequena) como dinero muerto al bote antes de las ciegas (opcion A: ante tradicional simetrico)
    public static volatile boolean STRADDLE = false; //true = UTG postea un straddle obligatorio (= 2x ciega grande) live, con opcion; deshabilitado en heads-up
    // null = escalera por defecto 1-2-3-5 x10^n (camino legacy en Crupier, infinito por decadas).
    // non-null = estructura de ciegas personalizada (lista explicita {sb,bb}); la escalada la camina
    // por indice y topa en el ultimo nivel. La elige el host al crear timba, viaja a los clientes y se
    // persiste/restaura en recover. Ver BlindStructure y Crupier.doblarCiegas/simulateNextBlinds.
    public static volatile double[][] ACTIVE_BLIND_STRUCTURE = null;
    public static volatile boolean REBUY = true;
    public static volatile int REBUY_LIMIT = 0; //0 = sin limite de rebuys por jugador; en otro caso, max veces que un jugador puede rebuyar en la partida
    public static volatile boolean BOT_REBUY = true; //true = bots pueden rebuyar (sujetos al limite si > 0); false = bots se quedan de espectador sin preguntar al host
    public static volatile boolean AUTO_REBUY_ON_BROKE = false; //true = el humano local recompra automaticamente al arruinarse (importe por defecto, sin dialogo); preferencia LOCAL de sesion, por defecto false
    public static volatile int MANOS = -1;
    public static volatile boolean IWTSTH_RULE = false;
    public static volatile int RABBIT_HUNTING = 0;
    public static volatile boolean VOICE_MESSAGES = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("voice_messages", "true"));
    public static volatile boolean RUN_IT_TWICE = false;
    // Congela el cambio de RUN_IT_TWICE durante el run-out del all-in (desde que
    // arranca hasta NUEVA_MANO): el voto se decide leyendo el flag sin lock, así que
    // no debe cambiar en esa ventana. Antes se garantizaba greyando el menú; ahora
    // el setter no-opea y el diálogo "Ajustes de partida" deshabilita el control
    // mientras está activo.
    public static volatile boolean RUN_IT_TWICE_LOCKED = false;
    public static volatile boolean SONIDOS = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonidos", "true")) && !TEST_MODE;
    public static volatile boolean SONIDOS_CHORRA = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonidos_chorra", "false"));
    public static volatile boolean MUSICA_AMBIENTAL = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_ascensor", "true"));
    public static volatile boolean AUTO_FULLSCREEN = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("auto_fullscreen", "false"));
    public static volatile boolean SHOW_CLOCK = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("show_time", "false"));
    public static volatile boolean CONFIRM_ACTIONS = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("confirmar_todo", "false")) && !TEST_MODE;
    public static volatile int ZOOM_LEVEL = Integer.parseInt(Helpers.PROPERTIES.getProperty("zoom_level", String.valueOf(GameFrame.DEFAULT_ZOOM_LEVEL)));
    public static volatile String BARAJA = Helpers.PROPERTIES.getProperty("baraja", BARAJA_DEFAULT);
    public static volatile int VISTA_COMPACTA = Integer.parseInt(Helpers.isNumeric(Helpers.PROPERTIES.getProperty("vista_compacta", "0")) ? Helpers.PROPERTIES.getProperty("vista_compacta", "0") : "0") % 3;
    // Efectos de animación, con granularidad: reparto/destapes de cartas, fichas de
    // posición (ciegas+dealer), ficha al bote (apuestas) y el rodaje de los contadores
    // (stack/bote/apuesta + cortinilla de llenado y recompra). Estos 5 flags *_PREF
    // guardan la PREFERENCIA CRUDA de cada efecto (NO el valor efectivo): el maestro
    // ANIMACIONES los gatea en cada comprobación vía los helpers *On(). Combinables
    // independientemente; por defecto los cuatro activados. "animacion_reparto" conserva
    // la clave histórica.
    public static volatile boolean ANIMACION_REPARTO_PREF = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("animacion_reparto", "true"));
    public static volatile boolean ANIMACION_CIEGAS_DEALER_PREF = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("animacion_ciegas_dealer", "true"));
    public static volatile boolean ANIMACION_APUESTAS_PREF = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("animacion_apuestas", "true"));
    // Rodaje animado de los contadores numéricos. La pantalla final (BalanceDialog)
    // NO depende de este flag: su contador se da SIEMPRE.
    public static volatile boolean ANIMACION_CONTADORES_PREF = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("animacion_contadores", "true"));

    // Sincronización P2P de estadísticas: dos preferencias globales independientes,
    // ambas ON por defecto. RECIBIR = importar las partidas que me faltan al conectar
    // a un servidor. COMPARTIR = enviar mis partidas que al otro le faltan.
    public static volatile boolean SYNC_STATS_RECEIVE_PREF = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sync_stats_receive", "true"));
    public static volatile boolean SYNC_STATS_SHARE_PREF = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sync_stats_share", "true"));

    // Exclusiones de COMPARTIR: subconjunto de MIS partidas que quedan fuera de lo que
    // propago, aunque COMPARTIR esté activo (se aplican en StatsSync.listShareableUgis).
    // Privadas ON por defecto (comportamiento histórico: las timbas privadas nunca se
    // compartían). Por nick OFF por defecto: la lista es de nicks separados por comas y
    // excluye toda partida donde haya participado ALGUNO de ellos.
    public static volatile boolean SYNC_STATS_EXCLUDE_PRIVATE_PREF = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sync_stats_exclude_private", "true"));
    public static volatile boolean SYNC_STATS_EXCLUDE_NICKS_ENABLED_PREF = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sync_stats_exclude_nicks_enabled", "false"));
    public static volatile String SYNC_STATS_EXCLUDE_NICKS_PREF = Helpers.PROPERTIES.getProperty("sync_stats_exclude_nicks", "");

    // Maestro de animaciones: GATE global. Los 5 flags *_PREF (CINEMATICAS_PREF y los 4
    // ANIMACION_*_PREF) guardan la PREFERENCIA cruda de cada efecto, como antes de existir
    // el maestro. Este flag los GATEA en cada comprobación vía los helpers *On() (=
    // ANIMACIONES && preferencia): apagarlo desactiva TODAS las animaciones de un plumazo
    // SIN tocar las preferencias. applyAnimationMaster ya NO recalcula los flags (no hay
    // valor "efectivo" almacenado); solo habilita/deshabilita (visual) los toggles
    // individuales cuando está off. Por defecto on.
    public static volatile boolean ANIMACIONES = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("animaciones", "true"));

    // Gate por efecto: una animación individual SOLO aplica si el maestro está on Y su
    // preferencia está on. TODO read-site del código que decida ANIMAR usa estos helpers,
    // nunca el flag *_PREF directo (que es solo la preferencia, sin gatear).
    public static boolean cinematicasOn() {
        return ANIMACIONES && CINEMATICAS_PREF;
    }

    public static boolean repartoAnimOn() {
        return ANIMACIONES && ANIMACION_REPARTO_PREF;
    }

    public static boolean ciegasDealerAnimOn() {
        return ANIMACIONES && ANIMACION_CIEGAS_DEALER_PREF;
    }

    public static boolean apuestasAnimOn() {
        return ANIMACIONES && ANIMACION_APUESTAS_PREF;
    }

    public static boolean contadoresAnimOn() {
        return ANIMACIONES && ANIMACION_CONTADORES_PREF;
    }

    // Velocidad/topes del rodaje de los contadores VIVOS (stack/bote del jugador/
    // bote general) durante el juego. VELOCIDAD CONSTANTE (lineal): duración de cada
    // tramo = distancia/velocidad, acotada a [min, max]. Palancas para afinar los
    // casos extremos (cambios enormes no se eternizan, minúsculos no son instantáneos).
    public static final double COUNTER_ROLL_SPEED = 3000.0; // dinero/segundo
    public static final long COUNTER_ROLL_MIN_MS = 120;
    public static final long COUNTER_ROLL_MAX_MS = 900;

    // Rodaje del % de probabilidad del all-in (JUGADA + PROB en la label de accion).
    // A diferencia de los contadores vivos (velocidad constante), este va a TIEMPO
    // CONSTANTE: cada cambio de calle rueda en PROB_ROLL_MS fijo sin importar cuanto
    // cambie el %, asi TODAS las probabilidades alcanzan su valor en el mismo tiempo
    // (arrancan y terminan a la vez).
    public static final long PROB_ROLL_MS = 200;

    // Gate del rodaje de contadores VIVOS: respeta la opción de Ajustes y se salta en
    // recover (los valores recuperados se fijan de golpe, sin animar). El gate de la
    // cortinilla/recompra es Crupier.isStackFillAnimated (añade fin de transmisión).
    public static boolean isCounterRollEnabled() {
        if (!contadoresAnimOn() || RECOVER) {
            return false;
        }
        // !RECOVER cubre el recover en si; game_recovered==0 cubre la REPLICA de una mano
        // recuperada (RECOVER ya es false pero la mano se re-ejecuta), para que los
        // contadores SALTEN en vez de animar durante esa repeticion de arranque.
        GameFrame gf = getInstance();
        return gf == null || gf.getCrupier() == null || gf.getCrupier().getGame_recovered() == 0;
    }
    // Overlay opcional sobre las comunitarias con el coste de igualar del jugador
    // local (cuánto tendrá que poner cuando le toque). Por defecto activado.
    public static volatile boolean MOSTRAR_COSTE_IGUALAR = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("mostrar_coste_igualar", "true"));
    public static volatile boolean AUTO_ACTION_BUTTONS = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("auto_action_buttons", "false")) && !TEST_MODE;
    // Si está activo, una pulsación de un botón AUTO sobrevive entre manos en vez
    // de resetearse (solo aplica con AUTO_ACTION_BUTTONS activo). Activado por defecto.
    public static volatile boolean AUTO_ACTION_PERSIST = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("auto_action_persist", "true"));
    // Si está activo, antes de ejecutar una acción automática del pre-pulsado se
    // muestra un diálogo modal de cuenta atrás (MODO AUTO) que permite vetarla.
    public static volatile boolean MODO_AUTO_CONFIRM = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("modo_auto_confirm", "true"));
    // Segundos de la barra del diálogo MODO AUTO.
    public static final int AUTO_CONFIRM_SECONDS = 5;
    // Auto-call automático. Con AUTO_CALL_ENABLED el pre-pulsado de check/call
    // iguala cualquier apuesta cuyo coste REAL (lo que de verdad pones = el stack
    // cuando igualar exige all-in) sea <= AUTO_CALL_MAX, en cualquier calle
    // (generaliza el "+BB"). AUTO_CALL_MAX == 0 = SIN LÍMITE (iguala cualquier
    // importe). Solo aplica con AUTO_ACTION_BUTTONS activo. En fichas (la ficha
    // mínima del motor es el céntimo, 0,01).
    public static volatile boolean AUTO_CALL_ENABLED = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("auto_call_enabled", "false"));
    public static volatile double AUTO_CALL_MAX = Double.parseDouble(Helpers.PROPERTIES.getProperty("auto_call_max", "0.0"));
    public static volatile String COLOR_TAPETE = Helpers.PROPERTIES.getProperty("color_tapete", "verde");
    public static volatile String LANGUAGE = Helpers.PROPERTIES.getProperty("lenguaje", "es").toLowerCase();
    public static volatile boolean CINEMATICAS_PREF = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("cinematicas", "true"));
    public static volatile boolean CHAT_IMAGES_INGAME = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("chat_images_ingame", "true"));
    public static volatile boolean AUTO_ZOOM = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("auto_zoom", "false"));
    // Ficha de posición del jugador local sobre sus cartas: 3 estados cíclicos por click
    // (persistidos): 0=normal, 1=70% de opacidad, 2=oculta. parseLocalPosChipState migra
    // el valor booleano antiguo "true"/"false" de versiones previas (true->normal, false->oculta).
    public static final int LOCAL_POS_CHIP_NORMAL = 0;
    public static final int LOCAL_POS_CHIP_DIM = 1;
    public static final int LOCAL_POS_CHIP_HIDDEN = 2;
    public static volatile int LOCAL_POSITION_CHIP = parseLocalPosChipState(Helpers.PROPERTIES.getProperty("local_pos_chip", "0"));

    private static int parseLocalPosChipState(String v) {
        if (v == null) {
            return LOCAL_POS_CHIP_NORMAL;
        }
        switch (v.trim()) {
            case "true":
                return LOCAL_POS_CHIP_NORMAL;
            case "false":
                return LOCAL_POS_CHIP_HIDDEN;
            default:
                try {
                    int s = Integer.parseInt(v.trim());
                    return (s >= LOCAL_POS_CHIP_NORMAL && s <= LOCAL_POS_CHIP_HIDDEN) ? s : LOCAL_POS_CHIP_NORMAL;
                } catch (NumberFormatException e) {
                    return LOCAL_POS_CHIP_NORMAL;
                }
        }
    }
    public static volatile String SERVER_HISTORY = Helpers.PROPERTIES.getProperty("server_history", "");
    public static volatile boolean RECOVER = false;
    public static volatile Boolean MAC_NATIVE_FULLSCREEN = null;
    public static volatile boolean TTS_SERVER = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("tts_server", "true"));
    public static volatile int RECOVER_ID = -1;
    public static volatile String UGI = null;
    public final static int UGI_LENGTH = 50;
    public static volatile long GAME_START_TIMESTAMP;
    public static volatile KeyEventDispatcher key_event_dispatcher = null;
    private static final Object ZOOM_LOCK = new Object();

    private static volatile GameFrame THIS = null;

    // Shutdown hook que se activa cuando la JVM termina por SIGINT (Ctrl+C),
    // SIGTERM, cierre de consola (Windows CTRL_CLOSE_EVENT, ~5s antes de
    // TerminateProcess) o cualquier salida brusca distinta a cerrar la
    // ventana del juego.
    //
    // - Cliente: envia "EXIT#<testamento>" al host. Sin esto, una caida abrupta
    //   del cliente en mitad de cascade SRA disparaba MISDEAL en la mesa
    //   (sra_unlock no llegaba). Con el hook el host aplica el testamento y
    //   la mano termina sin MISDEAL.
    // - Host: broadcast SERVEREXIT a todos los clientes. La partida no puede
    //   continuar sin host, asi que no usamos SERVEREXITRECOVER (eso abriria
    //   el recover dialog en el cliente esperando a re-conectarse a un host
    //   que ya no existe). SERVEREXIT lleva al cliente al lobby normal:
    //   game over limpio. Si el usuario quiere recover lo hace a mano desde
    //   el menu. Sin el hook, los clientes veian al host caido y entraban
    //   a reconectarCliente reintentando un servidor inexistente hasta que
    //   el dialog modal aparecia a los 80s.
    private static volatile Thread SHUTDOWN_HOOK_THREAD = null;

    /**
     * Registra el shutdown hook si no esta ya registrado. El hook es
     * idempotente, se auto-comprueba (si la partida ya termino no hace nada)
     * y discrimina internamente entre host y cliente.
     */
    private static void registerShutdownHook() {
        if (SHUTDOWN_HOOK_THREAD != null) {
            return;
        }
        Thread hook = new Thread(() -> {
            try {
                GameFrame gf = GameFrame.getInstance();
                WaitingRoomFrame wrf = WaitingRoomFrame.getInstance();
                if (gf == null || wrf == null) {
                    return;
                }
                Crupier c = gf.getCrupier();
                if (c == null || c.isFin_de_la_transmision()) {
                    return; // Partida ya cerrada limpiamente.
                }
                if (!wrf.isPartida_empezada()) {
                    return; // No hay nada que enviar (estamos en lobby/sala-espera).
                }

                if (gf.isPartida_local()) {
                    // --- HOST: broadcast SERVEREXIT a todos los clientes ---
                    // La partida muere con el host (no hay nadie a quien re-conectarse),
                    // asi que mandamos SERVEREXIT (game over limpio), NO SERVEREXITRECOVER
                    // (ese path abre el recover dialog del cliente esperando reconexion
                    // a un host inexistente). Confirmation=false: fire-and-forget. No
                    // podemos esperar CONF durante shutdown (Windows mata a los 5s).
                    try {
                        c.broadcastGAMECommandFromServer("SERVEREXIT", null, false);
                    } catch (Throwable ignored) {
                    }
                    return;
                }

                // --- CLIENTE: envia EXIT#testamento al host ---
                NetClient nc = wrf.getNet_client();
                if (nc == null || nc.isReconnecting()) {
                    return; // Si ya estabamos reconectando, el server YA sabe que estamos caidos.
                }
                java.net.Socket s = nc.getLocal_client_socket();
                if (s == null || s.isClosed()) {
                    return;
                }
                // Construye el comando directamente para evitar reentrar en
                // sendGAMECommandToServer (do-while con waits que durante
                // shutdown pueden colgarnos por encima del timeout de 5s
                // que da Windows al cerrar la consola).
                String testamento;
                try {
                    testamento = c.getTestamentoCriptografico();
                } catch (Throwable ex) {
                    testamento = "*"; // Sin testamento valido: mejor mandar EXIT desnudo que nada.
                }
                String body = "GAME#" + Helpers.CSPRNG_GENERATOR.nextInt() + "#EXIT#" + testamento;
                javax.crypto.spec.SecretKeySpec aes = nc.getLocal_client_aes_key();
                javax.crypto.spec.SecretKeySpec hmac = nc.getLocal_client_hmac_key();
                if (aes == null || hmac == null) {
                    return;
                }
                String encrypted = Helpers.encryptCommand(body, aes, hmac);
                if (encrypted == null) {
                    return;
                }
                synchronized (s.getOutputStream()) {
                    s.getOutputStream().write((encrypted + "\n").getBytes("UTF-8"));
                    s.getOutputStream().flush();
                }
            } catch (Throwable ignored) {
                // Hook silencioso: si algo falla durante el shutdown, volvemos
                // al comportamiento sin hook (clientes detectan caida del host
                // por null read y entran a reconectarCliente hasta el dialog
                // modal a los 80s; cliente caido sin testamento -> posible
                // MISDEAL si estabamos en cascade SRA). No es regresion.
            }
        }, "CoronaPoker-Exit-Hook");
        hook.setDaemon(false);
        try {
            Runtime.getRuntime().addShutdownHook(hook);
            SHUTDOWN_HOOK_THREAD = hook;
        } catch (Throwable ignored) {
        }
    }

    /**
     * Desregistra el shutdown hook cuando la partida termina limpiamente
     * (finTransmision). Asi no queda colgando un hook que intentaria
     * enviar EXIT por un socket ya cerrado tras volver al lobby.
     */
    public static void unregisterShutdownHook() {
        Thread h = SHUTDOWN_HOOK_THREAD;
        if (h != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(h);
            } catch (IllegalStateException ignored) {
                // JVM ya en shutdown: no se puede desregistrar, ya da igual.
            } catch (Throwable ignored) {
            }
            SHUTDOWN_HOOK_THREAD = null;
        }
    }
    public static volatile Boolean IWTSTH_RULE_RECOVER = null;
    public static volatile Integer RABBIT_HUNTING_RECOVER = null;
    public static volatile Boolean RUN_IT_TWICE_RECOVER = null;
    public static volatile Boolean VOICE_MESSAGES_RECOVER = null;
    public static volatile Boolean TTS_SERVER_RECOVER = null;
    public static volatile String PASSWORD_RECOVER = null;

    public static GameFrame getInstance() {
        return THIS;
    }

    public static String serializeRecoverSettings() {
        boolean iwtsth = (IWTSTH_RULE_RECOVER != null ? IWTSTH_RULE_RECOVER : IWTSTH_RULE);
        int rabbit = (RABBIT_HUNTING_RECOVER != null ? RABBIT_HUNTING_RECOVER : RABBIT_HUNTING);
        boolean runittwice = (RUN_IT_TWICE_RECOVER != null ? RUN_IT_TWICE_RECOVER : RUN_IT_TWICE);
        boolean voicemsg = (VOICE_MESSAGES_RECOVER != null ? VOICE_MESSAGES_RECOVER : VOICE_MESSAGES);
        boolean tts = (TTS_SERVER_RECOVER != null ? TTS_SERVER_RECOVER : TTS_SERVER);
        return "IWTSTH=" + (iwtsth ? "1" : "0")
                + "#RABBIT=" + rabbit
                + "#DIFFICULTY=" + Bot.DIFFICULTY.name()
                + "#BLIND_CAP=" + BLIND_CAP
                + "#REBUY_LIMIT=" + REBUY_LIMIT
                + "#BOT_REBUY=" + (BOT_REBUY ? "1" : "0")
                + "#RUNITWICE=" + (runittwice ? "1" : "0")
                + "#VOICEMSG=" + (voicemsg ? "1" : "0")
                + "#TTS=" + (tts ? "1" : "0")
                + "#FIXED_BUYIN=" + (FIXED_BUYIN ? "1" : "0")
                // Estructura de ciegas personalizada (CSV sb/bb, sin '#'/'='; vacío =
                // escalera por defecto). Imprescindible para que la escalada y el
                // re-broadcast INIT tras recuperar usen la misma lista.
                + "#BLINDS=" + (ACTIVE_BLIND_STRUCTURE != null ? BlindStructure.levelsToString(ACTIVE_BLIND_STRUCTURE) : "")
                // Rango de buy-in editable (en ciegas grandes).
                + "#BMINBB=" + BUYIN_MIN_BB
                + "#BMAXBB=" + BUYIN_MAX_BB
                // Política de tope de recompra (0=BUYIN, 1=stack más alto).
                + "#RBCAP=" + REBUY_CAP_POLICY
                + "#ANTE=" + (ANTE ? "1" : "0")
                + "#STRADDLE=" + (STRADDLE ? "1" : "0");
    }

    public static void applyRecoverSettings(String serialized) {
        // Borrón y cuenta nueva: recuperar parte SIEMPRE de la escalera por defecto.
        // Si la fila recuperada no trae clave BLINDS (timba de una versión anterior
        // a la feature), ACTIVE queda null deterministamente y no se arrastra una
        // estructura personalizada que quedara activa de otra timba de esta sesión.
        ACTIVE_BLIND_STRUCTURE = null;
        // Mismo criterio para el rango de buy-in y la política de tope de recompra:
        // una fila de una versión anterior a esta feature no trae BMINBB/BMAXBB/RBCAP,
        // así que se parte SIEMPRE de los valores por defecto y nunca se arrastra un
        // rango/política obsoleto de otra timba abierta en esta misma sesión.
        BUYIN_MIN_BB = BuyinRules.DEFAULT_MIN_BB;
        BUYIN_MAX_BB = BuyinRules.DEFAULT_MAX_BB;
        REBUY_CAP_POLICY = REBUY_CAP_BUYIN;
        // Mismo criterio para ante/straddle: una fila anterior a esta feature no trae
        // las claves, asi que se parte SIEMPRE de off y no se arrastra un estado stale
        // de otra timba abierta en esta misma sesion.
        ANTE = false;
        STRADDLE = false;
        if (serialized == null || serialized.isEmpty()) {
            return;
        }
        for (String pair : serialized.split("#")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = pair.substring(0, eq);
            String val = pair.substring(eq + 1);
            switch (key) {
                case "IWTSTH":
                    IWTSTH_RULE_RECOVER = "1".equals(val);
                    break;
                case "RABBIT":
                    try {
                        RABBIT_HUNTING_RECOVER = Integer.parseInt(val);
                    } catch (NumberFormatException ignore) {
                    }
                    break;
                case "DIFFICULTY":
                    try {
                        // "EXPERT" is a legacy value from the old 4-level scheme;
                        // it maps to the current top level HARD.
                        Bot.DIFFICULTY = "EXPERT".equals(val)
                                ? Bot.Difficulty.HARD
                                : Bot.Difficulty.valueOf(val);
                    } catch (IllegalArgumentException ignore) {
                    }
                    break;
                case "BLIND_CAP":
                    try {
                        BLIND_CAP = Double.parseDouble(val);
                    } catch (NumberFormatException ignore) {
                    }
                    break;
                case "REBUY_LIMIT":
                    try {
                        REBUY_LIMIT = Integer.parseInt(val);
                    } catch (NumberFormatException ignore) {
                    }
                    break;
                case "BOT_REBUY":
                    BOT_REBUY = "1".equals(val);
                    break;
                case "RUNITWICE":
                    RUN_IT_TWICE_RECOVER = "1".equals(val);
                    break;
                case "VOICEMSG":
                    VOICE_MESSAGES_RECOVER = "1".equals(val);
                    break;
                case "TTS":
                    TTS_SERVER_RECOVER = "1".equals(val);
                    break;
                case "FIXED_BUYIN":
                    FIXED_BUYIN = "1".equals(val);
                    break;
                case "BMINBB":
                    try {
                        BUYIN_MIN_BB = Integer.parseInt(val);
                    } catch (NumberFormatException ignore) {
                    }
                    break;
                case "BMAXBB":
                    try {
                        BUYIN_MAX_BB = Integer.parseInt(val);
                    } catch (NumberFormatException ignore) {
                    }
                    break;
                case "RBCAP":
                    try {
                        REBUY_CAP_POLICY = Integer.parseInt(val);
                    } catch (NumberFormatException ignore) {
                    }
                    break;
                case "ANTE":
                    ANTE = "1".equals(val);
                    break;
                case "STRADDLE":
                    STRADDLE = "1".equals(val);
                    break;
                case "BLINDS":
                    // Vacío = escalera por defecto (null). Parse defensivo: si la lista
                    // guardada estuviese corrupta, caer a por defecto sin abortar el
                    // recover (el motor seguiría con la escalera 1-2-3-5).
                    if (val == null || val.isEmpty()) {
                        ACTIVE_BLIND_STRUCTURE = null;
                    } else {
                        try {
                            ACTIVE_BLIND_STRUCTURE = BlindStructure.parseValidatedLevels(val);
                        } catch (IllegalArgumentException ignore) {
                            Logger.getLogger(GameFrame.class.getName()).log(Level.WARNING,
                                    "Recovered custom blind structure is corrupt or invalid; falling back to default");
                            ACTIVE_BLIND_STRUCTURE = null;
                        }
                    }
                    break;
            }
        }
    }

    // Big blind for a given small blind: taken from the active custom structure
    // when it contains that level (so a non-2x big blind survives recover), else
    // the universal 2x default. The active structure is restored before recovered
    // game stats are applied, so it is available here.
    public static double bigBlindForSmallBlind(double sb) {
        if (ACTIVE_BLIND_STRUCTURE != null) {
            int idx = BlindStructure.indexOfLevel(ACTIVE_BLIND_STRUCTURE, sb);
            if (idx >= 0) {
                return ACTIVE_BLIND_STRUCTURE[idx][1];
            }
        }
        return sb * 2;
    }

    // Buy-in range/ceiling helpers. The arithmetic lives in BuyinRules (pure,
    // unit-tested); these bind it to the live game config (CIEGA_GRANDE, BUYIN,
    // FIXED_BUYIN).
    public static int getBuyinMin() {
        return BuyinRules.min(CIEGA_GRANDE, BUYIN_MIN_BB);
    }

    public static int getBuyinDefault() {
        return BuyinRules.defaultBuyin(CIEGA_GRANDE, BUYIN_MIN_BB, BUYIN_MAX_BB);
    }

    public static int getBuyinMax() {
        return BuyinRules.max(CIEGA_GRANDE, BUYIN_MAX_BB);
    }

    // Per-table stack ceiling for rebuys/top-ups, per REBUY_CAP_POLICY:
    //  - BUYIN: fixed mode = the single shared buy-in; variable mode = BUYIN_MAX_BB
    //    big blinds (the deepest anybody could have bought in for).
    //  - HIGHEST_STACK: the greater of the standard buy-in and the biggest stack
    //    at the table. A bust-out can ALWAYS rebuy at least a full standard buy-in
    //    (BUYIN in fixed mode, the default buy-in in variable mode), and may match
    //    the chip leader when somebody is deeper. Before, the cap was the bare
    //    floor of the leader's stack, so a leader sitting at 9.90 capped rebuys at
    //    9 — below the buy-in and dropping the cents. No player may ever hold more
    //    than this.
    public static int getBuyinCap() {
        if (REBUY_CAP_POLICY == REBUY_CAP_HIGHEST_STACK) {
            int standard_buyin = FIXED_BUYIN ? BUYIN : getBuyinDefault();
            return Math.max(standard_buyin, (int) Math.floor(highestPlayerStack()));
        }
        return BuyinRules.cap(FIXED_BUYIN, BUYIN, CIEGA_GRANDE, BUYIN_MAX_BB);
    }

    // Highest stack among players in play (neither exited nor spectators). The
    // basis of the HIGHEST_STACK rebuy cap; floored to whole units by getBuyinCap.
    private static double highestPlayerStack() {
        GameFrame gf = THIS;
        double highest = 0;
        if (gf != null && gf.getJugadores() != null) {
            for (Player p : gf.getJugadores()) {
                if (p != null && !p.isExit() && !p.isSpectator() && p.getStack() > highest) {
                    highest = p.getStack();
                }
            }
        }
        return highest;
    }

    // Maximum a player may ADD to their stack via a rebuy/top-up without exceeding
    // the table ceiling; 0 if already at (or over) it. Single source of truth for
    // both the request-time clamp (host) and the apply-time re-check in reComprar
    // (anti-stale / anti-cheat).
    public static int rebuyHeadroom(double current_stack) {
        if (REBUY_CAP_POLICY == REBUY_CAP_HIGHEST_STACK) {
            // Margen = tope de mesa (mayor entre el buy-in estándar y el stack más
            // alto, ver getBuyinCap) − stack actual (en unidades enteras).
            return Math.max(0, getBuyinCap() - (int) Math.ceil(current_stack));
        }
        return BuyinRules.headroom(FIXED_BUYIN, BUYIN, CIEGA_GRANDE, BUYIN_MAX_BB, current_stack);
    }

    // Marca CYAN del stack = el jugador ha hecho al menos una RE-compra (no la
    // compra inicial). Cuenta recompras reales via el contador per-nick del
    // crupier, asi que un jugador que en modo variable simplemente eligio un
    // buy-in inicial mas profundo NO se marca. Null-safe (verde si aun no hay
    // crupier, p.ej. durante el montaje de la mesa).
    public static boolean hasRebought(String nick) {
        // nick puede ser null durante el montaje de la mesa (setStack en el
        // constructor de GameFrame corre antes de asignar nicknames en
        // sentarParticipantes); ConcurrentHashMap no admite clave null.
        return nick != null && getInstance() != null && getInstance().getCrupier() != null
                && getInstance().getCrupier().getRebuyCount(nick) > 0;
    }

    public static void persistRecoverSettings(int gameId) {
        if (gameId <= 0) {
            return;
        }
        synchronized (GameFrame.SQL_LOCK) {
            try (PreparedStatement st = Helpers.getSQLITE().prepareStatement("UPDATE game SET recover_settings=? WHERE id=?")) {
                st.setQueryTimeout(30);
                st.setString(1, serializeRecoverSettings());
                st.setInt(2, gameId);
                st.executeUpdate();
            } catch (SQLException ex) {
                Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, "Failed to persist recover_settings", ex);
            }
        }
    }

    // Issue#9: en modo recover, NewGameDialog deja BUYIN/CIEGAS al valor por
    // defecto del spinner (BUYIN=10, ciegas 0.10/0.20) porque los controles del
    // form se deshabilitan pero nunca se cargan desde SQL — el form mantiene
    // sus valores iniciales del constructor. Mas tarde recuperarDatosClavePartida
    // arregla GameFrame.BUYIN/CIEGAS desde la fila game/hand, pero el
    // constructor de GameFrame ya corrio antes y los slots de los Player
    // (field initializer = GameFrame.BUYIN y el loop simetrico de setStack/
    // setBuyin) capturaron el BUYIN stale = 10. Para los participantes
    // originales recuperarDatosClavePartida machaca su stack/buyin desde la
    // fila de balance en SQL, pero para un late-joiner sin fila previa el
    // valor stale persiste: aparece sentado con stack=10 y buyin=10 en una
    // mesa configurada a 100. Este helper resuelve la causa raiz cargando
    // BUYIN/CIEGAS desde la fila game antes de que GameFrame se construya.
    public static void applyRecoveredGameStats(int gameId) {
        if (gameId <= 0) {
            return;
        }
        synchronized (GameFrame.SQL_LOCK) {
            String sql = "SELECT buyin, round(sb,2) AS sb, blinds_time, blinds_time_type, rebuy FROM game WHERE id=?";
            try (PreparedStatement st = Helpers.getSQLITE().prepareStatement(sql)) {
                st.setQueryTimeout(30);
                st.setInt(1, gameId);
                try (java.sql.ResultSet rs = st.executeQuery()) {
                    if (rs.next()) {
                        int b = rs.getInt("buyin");
                        if (b > 0) {
                            BUYIN = b;
                        }
                        double sb = rs.getDouble("sb");
                        if (sb > 0) {
                            CIEGA_PEQUEÑA = sb;
                            CIEGA_GRANDE = bigBlindForSmallBlind(sb);
                        }
                        CIEGAS_DOUBLE = rs.getInt("blinds_time");
                        int bt = rs.getInt("blinds_time_type");
                        CIEGAS_DOUBLE_TYPE = bt > 0 ? bt : 1;
                        REBUY = rs.getBoolean("rebuy");
                    }
                }
            } catch (SQLException ex) {
                Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, "Failed to load recovered game stats", ex);
            }
        }
    }

    private final Object full_screen_lock = new Object();
    private final Object lock_pause = new Object();
    private final ArrayList<Player> jugadores;
    private final ConcurrentHashMap<String, String> nick2avatar = new ConcurrentHashMap<>();
    private final Crupier crupier;
    private final boolean partida_local;
    private final String nick_local;
    private final BrightnessLayerUI capa_brillo = new BrightnessLayerUI();

    private volatile ZoomableInterface[] zoomables;
    private volatile long conta_tiempo_juego = 0L;
    private volatile boolean full_screen = false;
    private volatile boolean timba_pausada = false;
    private volatile String nick_pause = null;
    private volatile PauseDialog pausa_dialog = null;
    private volatile boolean game_over_dialog = false;
    private volatile AboutDialog about_dialog = null;
    private volatile HandGeneratorDialog jugadas_dialog = null;
    private volatile GameLogDialog registro_dialog = null;
    private volatile ShortcutsDialog shortcuts_dialog = null;
    private volatile FastChatDialog fastchat_dialog = null;
    private volatile RebuyDialog rebuy_dialog = null;
    private volatile GifAnimationDialog gif_dialog = null;
    public volatile VolumeControlDialog volume_dialog = null;
    private volatile TablePanel tapete = null;
    private volatile Timer tiempo_juego;
    private volatile int tapete_counter = 0;
    private volatile int i60_c = 0;
    private volatile JLayer<JComponent> frame_layer = null;
    private volatile boolean recover = false;
    private volatile boolean fin = false;
    private volatile InGameNotifyDialog notify_dialog = null;
    private volatile GraphicsDevice device = null;
    private volatile boolean latency_stats = false;

    // Accumulates mouse wheel clicks to process them all at once
    private volatile int zoom_accumulator = 0;
    private javax.swing.Timer zoom_debounce_timer;

    public JCheckBoxMenuItem getAuto_fullscreen_menu() {
        return auto_fullscreen_menu;
    }

    public JCheckBoxMenuItem getAuto_fit_zoom_menu() {
        return auto_fit_zoom_menu;
    }

    public JCheckBoxMenuItem getChat_image_menu() {
        return chat_image_menu;
    }

    public InGameNotifyDialog getNotify_dialog() {
        return notify_dialog;
    }

    public static void resetInstance() {

        GameFrame.getInstance().getFull_screen_menu().setEnabled(false);

        if (GameFrame.getInstance().isFull_screen()) {
            GameFrame.getInstance().toggleFullScreen();
        }

        GameFrame.IWTSTH_RULE = false;

        GameFrame.RABBIT_HUNTING = 0;

        GameFrame.RUN_IT_TWICE = false;

        // Reglas globales (TTS / notas de voz): NO se resetean. Si el servidor
        // las sobreescribió durante la timba se quedan así; su valor se persiste
        // como propiedad y es la preselección para la próxima partida.

        // Defensivo: sin resetear estos statics, una partida que acaba con
        // force_recover=true deja contaminada la siguiente partida fresh
        // (el INIT del host replica RECOVER=true al cliente arrancando un
        // recovery sin nada que recuperar). Aqui se setean al estado inicial
        // identico a una arranque limpio del JVM.
        GameFrame.RECOVER = false;
        GameFrame.RECOVER_ID = -1;
        GameFrame.UGI = null;

        THIS.setVisible(false);

        THIS.dispose();

        THIS = null;
    }

    public BrightnessLayerUI getCapa_brillo() {
        return capa_brillo;
    }

    public JMenuItem getRobert_rules_menu() {
        return robert_rules_menu;
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        e.consume();

        if (e.isControlDown()) {
            // Negative rotation means scroll up (zoom in), positive means scroll down (zoom out)
            zoom_accumulator -= e.getWheelRotation();

            // Restart the timer. It will only execute applyAccumulatedZoom() 
            // if 250ms pass without another wheel movement.
            zoom_debounce_timer.restart();

        } else if (getParent() != null) {
            getParent().dispatchEvent(e);
        }
    }

    /**
     * Executes the heavy zoom logic only once after the user has finished
     * scrolling the mouse wheel, applying the total accumulated zoom.
     */
    private void applyAccumulatedZoom() {
        if (zoom_accumulator == 0) {
            return;
        }

        // Play the sound just once based on the overall scroll direction
        Audio.playWavResource(zoom_accumulator > 0 ? "misc/zoom_in.wav" : "misc/zoom_out.wav");

        Helpers.threadRun(() -> {
            synchronized (ZOOM_LOCK) {
                ZOOM_LEVEL += zoom_accumulator;
                zoom_accumulator = 0; // Reset for the next scroll action

                // Safety check: Prevent zooming out too much (scale dropping to 0 or negative)
                if (Helpers.doubleSecureCompare(0f, 1f + ((ZOOM_LEVEL - 1) * ZOOM_STEP)) >= 0) {
                    ZOOM_LEVEL = (int) Math.ceil(-1f / ZOOM_STEP) + 1;
                }
            }

            // --- THE HEAVY LIFTING HAPPENS ONLY ONCE HERE ---
            Helpers.PROPERTIES.setProperty("zoom_level", String.valueOf(ZOOM_LEVEL));
            Card.updateCachedImages(1f + ZOOM_LEVEL * ZOOM_STEP, false);
            zoom(1f + ZOOM_LEVEL * ZOOM_STEP, null);

            if (jugadas_dialog != null && jugadas_dialog.isVisible()) {
                for (Card carta : jugadas_dialog.getCartas()) {
                    carta.invalidateImagePrecache();
                    carta.refreshCard();
                }
                Helpers.GUIRun(jugadas_dialog::pack);
            }

            if (shortcuts_dialog != null && shortcuts_dialog.isVisible()) {
                shortcuts_dialog.zoom(1f + ZOOM_LEVEL * ZOOM_STEP, null);
            }

            if (GameFrame.AUTO_ZOOM) {
                Helpers.threadRun(() -> {
                    Helpers.pausar(GameFrame.GUI_RENDER_WAIT);
                    tapete.autoZoom(false);
                });
            }

            Helpers.savePropertiesFile();
        });
    }

    public JCheckBoxMenuItem getAuto_adjust_zoom_menu() {
        return auto_fit_zoom_menu;
    }

    public JCheckBoxMenuItem getRebuy_now_menu() {
        return rebuy_now_menu;
    }

    public String getNick_pause() {
        return nick_pause;
    }

    public Object getLock_pause() {
        return lock_pause;
    }

    //--illegal-access=permit
    public void toggleMacNativeFullScreen(Window window) {

        if (Helpers.OSValidator.isMac()) {
            try {

                Method getApplication = Class.forName("com.apple.eawt.Application").getMethod("getApplication", (Class<?>[]) null);

                Object app = getApplication.invoke(null);

                Method requestToggleFullScreen = Class.forName("com.apple.eawt.Application").getMethod("requestToggleFullScreen", new Class<?>[]{Window.class});

                requestToggleFullScreen.invoke(Class.forName("com.apple.eawt.Application").cast(app), window);

            } catch (Exception ex) {
                Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    //--illegal-access=permit
    public void enableMacNativeFullScreen(Window window) {

        if (Helpers.OSValidator.isMac() && GameFrame.MAC_NATIVE_FULLSCREEN == null) {

            try {

                Method setWindowCanFullScreen = Class.forName("com.apple.eawt.FullScreenUtilities").getMethod("setWindowCanFullScreen", new Class<?>[]{Window.class, boolean.class});

                setWindowCanFullScreen.invoke(null, window, true);

                Method addFullScreenListenerTo = Class.forName("com.apple.eawt.FullScreenUtilities").getMethod("addFullScreenListenerTo", new Class<?>[]{Window.class, Class.forName("com.apple.eawt.FullScreenListener")});

                Object proxyFullScreenListener = Proxy.newProxyInstance(Class.forName("com.apple.eawt.FullScreenListener").getClassLoader(), new Class[]{Class.forName("com.apple.eawt.FullScreenListener")}, (Object proxy, Method method, Object[] args) -> {
                    if (method.getName().equals("windowEnteredFullScreen")) {
                        Helpers.GUIRun(() -> {
                            menu_bar.setVisible(false);
                            full_screen_menu.setEnabled(true);
                            Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(true);
                            full_screen_menu.setSelected(true);
                            Helpers.TapetePopupMenu.FULLSCREEN_MENU.setSelected(true);
                            full_screen = true;

                            synchronized (full_screen_lock) {
                                full_screen_lock.notifyAll();
                            }

                            GameFrame.getInstance().requestFocus();
                        });
                    } else if (method.getName().equals("windowExitedFullScreen")) {
                        Helpers.GUIRun(() -> {
                            menu_bar.setVisible(true);
                            full_screen_menu.setEnabled(true);
                            Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(true);
                            full_screen_menu.setSelected(false);
                            Helpers.TapetePopupMenu.FULLSCREEN_MENU.setSelected(false);
                            full_screen = false;

                            synchronized (full_screen_lock) {
                                full_screen_lock.notifyAll();
                            }

                            GameFrame.getInstance().requestFocus();
                        });
                    }
                    return true;
                });

                addFullScreenListenerTo.invoke(null, window, Class.forName("com.apple.eawt.FullScreenListener").cast(proxyFullScreenListener));
                GameFrame.MAC_NATIVE_FULLSCREEN = true;

            } catch (Exception e) {
                Logger.getLogger(GameFrame.class.getName()).log(Level.WARNING, null, e);
                GameFrame.MAC_NATIVE_FULLSCREEN = false;
            }
        }
    }

    /**
     * Reposiciona el GameFrame en el monitor donde reside actualmente la
     * WaitingRoomFrame, para que el (auto)fullscreen / MAXIMIZED_BOTH aterrice
     * en esa pantalla en vez del monitor por defecto. Necesario en Windows
     * porque setExtendedState(MAXIMIZED_BOTH) honra el monitor donde esta la
     * ventana; tambien util en Mac antes del setVisible nativo. La rama X11 de
     * toggleFullScreen ya usa el device de la sala de espera explicitamente.
     */
    private void placeOnWaitingRoomMonitor() {
        if (sala_espera == null) {
            return;
        }
        Helpers.GUIRunAndWait(() -> {
            Rectangle r = sala_espera.getGraphicsConfiguration().getBounds();
            int w = getWidth() > 0 ? getWidth() : Math.min(r.width, 1024);
            int h = getHeight() > 0 ? getHeight() : Math.min(r.height, 768);
            int x = r.x + Math.max(0, (r.width - w) / 2);
            int y = r.y + Math.max(0, (r.height - h) / 2);
            setLocation(x, y);
        });
    }

    public void autoZoomFullScreen(boolean fullscreen) {

        placeOnWaitingRoomMonitor();

        if (Helpers.OSValidator.isMac()) {

            GameFrame.getInstance().enableMacNativeFullScreen(GameFrame.getInstance());

            Helpers.GUIRunAndWait(() -> {
                setVisible(true);
                GameFrame.getInstance().setEnabled(false);
            });

            Helpers.pausar(1000);
        }

        Helpers.GUIRunAndWait(() -> {
            GameFrame.getInstance().setEnabled(true);

            if (!Init.DEV_MODE && fullscreen) {
                // Llamada directa al toggle unificado; antes era full_screen_menu.doClick()
                // que disparaba el listener del JMenuItem como evento sintetico —
                // antipatron Swing que acoplaba init al comportamiento del UI.
                triggerFullScreenToggle();
            } else {
                GameFrame.getInstance().setExtendedState(JFrame.MAXIMIZED_BOTH);
                GameFrame.getInstance().setVisible(true);
            }

            GameFrame.getInstance().setEnabled(false);
        });

        if (!Init.DEV_MODE && fullscreen) {
            // Deadline wall-clock para evitar drift por wakeups espurios del wait.
            // Antes el contador t se incrementaba ciegamente en 1000 ms por iteracion
            // asumiendo que wait(1000) habia esperado el periodo completo; un notify
            // (o un spurious wakeup) violaba esa premisa.
            long deadline = System.currentTimeMillis() + AUTO_ZOOM_TIMEOUT;
            // Comprobación de full_screen DENTRO del synchronized: fuera, el
            // toggle podía poner full_screen=true + notifyAll entre el chequeo y
            // el wait, perdiéndose la notificación y durmiendo hasta el timeout.
            synchronized (full_screen_lock) {
                while (!full_screen) {
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) {
                        break;
                    }
                    try {
                        full_screen_lock.wait(remaining);
                    } catch (InterruptedException ex) {
                        Helpers.logCooperativeCancellation(Logger.getLogger(GameFrame.class.getName()),
                                "fullscreen wait", ex);
                        break;
                    }
                }
            }
        }

        if (GameFrame.ZOOM_LEVEL != 0) {
            GameFrame.getInstance().zoom(1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP, null);
        }

        if (GameFrame.AUTO_ZOOM) {
            tapete.autoZoom(false);
        }

        Helpers.GUIRun(() -> {
            GameFrame.getInstance().setEnabled(true);
            full_screen_menu.setEnabled(!GameFrame.isRECOVER());
            Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(!GameFrame.isRECOVER());
        });

        // La sala de espera acaba de ocultarse y este frame es el nuevo foreground
        // del juego. La captura se difiere a un ciclo posterior del EDT para que
        // corra DESPUES de que el SO haya despachado los eventos de activacion
        // asincronos del setVisible y la recreacion del peer nativo (switch a
        // borderless); asi el pulso de foreground no compite con un WM_ACTIVATE
        // tardio. No es una espera arbitraria: es ordenar el grab tras la
        // realizacion de la ventana en la cola del EDT.
        forceForegroundDeferred();

    }

    /**
     * Captura el foreground de forma fiable. toFront()/requestFocus() estan
     * sujetos al foreground-lock de Windows (SPI_GETFOREGROUNDLOCKTIMEOUT) y
     * activan la ventana de forma no determinista — a veces el SO solo parpadea
     * el boton de la taskbar y no concede el foco. Un pulso a alwaysOnTop emite
     * un SetWindowPos(HWND_TOPMOST) que NO esta sujeto a esa restriccion: fuerza
     * la activacion y arrastra el foco. Se restaura de inmediato el estado previo
     * (normalmente false) para no dejar la ventana clavada por encima del resto
     * — por eso no afecta a dialogos posteriores (GIFs de chat, etc.): el pulso
     * es instantaneo y la ventana NO queda topmost. Debe invocarse en el EDT.
     */
    private void forceForeground() {
        boolean was_on_top = isAlwaysOnTop();
        setAlwaysOnTop(true);
        toFront();
        requestFocus();
        setAlwaysOnTop(was_on_top);
    }

    /**
     * Programa {@link #forceForeground()} en un ciclo posterior del EDT, para que
     * corra despues de que se hayan despachado los eventos de activacion
     * asincronos derivados de mostrar/recrear la ventana (el switch a borderless
     * recrea el peer nativo y la activacion del SO llega de forma diferida).
     * Seguro de llamar desde cualquier hilo.
     */
    private void forceForegroundDeferred() {
        SwingUtilities.invokeLater(this::forceForeground);
    }

    public ConcurrentHashMap<String, String> getNick2avatar() {
        return nick2avatar;
    }

    public JCheckBoxMenuItem getMenu_cinematicas() {
        return menu_cinematicas;
    }

    public void cambiarColorContadoresTapete(Color color) {

        tapete.getCommunityCards().cambiarColorContadores(color);

    }

    public JRadioButtonMenuItem getMenu_tapete_madera() {
        return menu_tapete_madera;
    }

    public JRadioButtonMenuItem getMenu_tapete_rojo() {
        return menu_tapete_rojo;
    }

    public JRadioButtonMenuItem getMenu_tapete_azul() {
        return menu_tapete_azul;
    }

    public JRadioButtonMenuItem getMenu_tapete_verde() {
        return menu_tapete_verde;
    }

    public JRadioButtonMenuItem getMenu_tapete_negro() {
        return menu_tapete_negro;
    }

    public JCheckBoxMenuItem getAuto_action_menu() {
        return auto_action_menu;
    }

    public JMenuItem getChat_menu() {
        return chat_menu;
    }

    public JMenuItem getRegistro_menu() {
        return registro_menu;
    }

    public JCheckBoxMenuItem getTime_menu() {
        return time_menu;
    }

    public JMenuItem getZoom_menu_reset() {
        return zoom_menu_reset;
    }

    public void setConta_tiempo_juego(long tiempo_juego) {
        this.conta_tiempo_juego = tiempo_juego;
    }

    public JMenuItem getJugadas_menu() {
        return jugadas_menu;
    }

    public JMenuItem getExit_menu() {
        return exit_menu;
    }

    public void closeWindow() {

        formWindowClosing(null);
    }

    public boolean isFull_screen() {
        return full_screen;
    }

    public JCheckBoxMenuItem getConfirmar_menu() {
        return confirmar_menu;
    }

    private void incrementZoom() {

        synchronized (ZOOM_LOCK) {
            ZOOM_LEVEL++;
        }
    }

    private void decrementZoom() {
        synchronized (ZOOM_LOCK) {
            ZOOM_LEVEL--;
        }
    }

    public void refresh() {
        Helpers.GUIRun(() -> {

            revalidate();
            repaint();

        });
    }

    public void toggleFullScreen() {

        Helpers.GUIRun(() -> {
            // Calcular el target en local primero. El commit del flag se hace al
            // final, de forma que si alguna operacion del cambio (setUndecorated,
            // setFullScreenWindow, etc.) lanza, el flag no queda divergente del
            // estado real de la ventana.
            boolean entering_full_screen = !full_screen;

            if (entering_full_screen) {

                if (Helpers.OSValidator.isWindows()) {
                    setVisible(false);
                    dispose();
                    menu_bar.setVisible(false);
                    setUndecorated(true);
                    setExtendedState(JFrame.MAXIMIZED_BOTH);
                    setVisible(true);

                } else {

                    device = GameFrame.getInstance().isVisible() ? GameFrame.getInstance().getGraphicsConfiguration().getDevice() : WaitingRoomFrame.getInstance().getGraphicsConfiguration().getDevice();
                    GameFrame.getInstance().setVisible(false);
                    GameFrame.getInstance().dispose();
                    GameFrame.getInstance().menu_bar.setVisible(false);
                    GameFrame.getInstance().setUndecorated(true);
                    device.setFullScreenWindow(GameFrame.getInstance());
                }

                if (timba_pausada && pausa_dialog != null) {

                    pausa_dialog.setVisible(false);
                    pausa_dialog.dispose();
                    pausa_dialog = new PauseDialog(this, false);
                    pausa_dialog.setLocationRelativeTo(pausa_dialog.getParent());
                    pausa_dialog.setVisible(true);
                }

            } else {

                if (Helpers.OSValidator.isWindows()) {

                    setVisible(false);
                    dispose();
                    menu_bar.setVisible(true);
                    setUndecorated(false);
                    setExtendedState(JFrame.MAXIMIZED_BOTH);
                    setVisible(true);

                } else {

                    device.setFullScreenWindow(null);
                    GameFrame.getInstance().dispose();
                    GameFrame.getInstance().setExtendedState(JFrame.MAXIMIZED_BOTH);
                    GameFrame.getInstance().setUndecorated(false);
                    GameFrame.getInstance().menu_bar.setVisible(true);
                    GameFrame.getInstance().setVisible(true);
                }

                if (timba_pausada && pausa_dialog != null) {

                    pausa_dialog.setVisible(false);
                    pausa_dialog.dispose();
                    pausa_dialog = new PauseDialog(GameFrame.getInstance(), false);
                    pausa_dialog.setLocationRelativeTo(pausa_dialog.getParent());
                    pausa_dialog.setVisible(true);
                }
            }

            // Commit del flag DESPUES de que las operaciones de cambio hayan
            // terminado, para que un fallo a mitad (excepcion en setUndecorated,
            // setVisible, setFullScreenWindow) no deje full_screen divergente
            // del estado real de la ventana.
            full_screen = entering_full_screen;

            full_screen_menu.setEnabled(true);
            Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(true);

            synchronized (full_screen_lock) {
                full_screen_lock.notifyAll();
            }

            // Diferido: el dispose()/setVisible() del switch recrea el peer nativo
            // y un requestFocus() sincrono aqui compite con el WM_ACTIVATE del SO.
            forceForegroundDeferred();
        });

    }

    public void cambiarBaraja() {

        Card.updateCachedImages(1f + GameFrame.ZOOM_LEVEL * GameFrame.getZOOM_STEP(), true);

        Audio.playWavResource("misc/uncover.wav", false);

        Player[] players = tapete.getPlayers();

        for (Player jugador : players) {

            jugador.getHoleCard1().invalidateImagePrecache();
            jugador.getHoleCard1().refreshCard();

            jugador.getHoleCard2().invalidateImagePrecache();
            jugador.getHoleCard2().refreshCard();
        }

        for (Card carta : this.tapete.getCommunityCards().getCartasComunes()) {
            carta.invalidateImagePrecache();
            carta.refreshCard();
        }

        if (this.jugadas_dialog != null && this.jugadas_dialog.isVisible()) {
            for (Card carta : this.jugadas_dialog.getCartas()) {
                carta.invalidateImagePrecache();
                carta.refreshCard();
            }

            Helpers.GUIRun(jugadas_dialog::pack);
        }

        // Pre-decodifica el shuffle.gif de la nueva baraja en background (la
        // caché es de una sola entrada: reemplaza y libera la anterior)
        Crupier.warmShuffleAnimCache();

    }

    public void vistaCompacta() {

        RemotePlayer[] players = tapete.getRemotePlayers();

        final ConcurrentLinkedQueue<Long> notifier = new ConcurrentLinkedQueue<>();

        for (RemotePlayer jugador : players) {

            jugador.getHoleCard1().refreshCard(true, notifier);
            jugador.getHoleCard2().refreshCard(true, notifier);
        }

        for (Card carta : this.getTapete().getCommunityCards().getCartasComunes()) {
            carta.refreshCard();
        }

        // Comprobación DENTRO del synchronized: fuera, la última carta podía
        // add()+notifyAll entre el size() y el wait y se perdía la notificación
        // (atasco de ~1s por barrera). Mismo arreglo en todas las esperas de
        // notifier de zoom/refresco.
        synchronized (notifier) {
            while (notifier.size() < players.length * 2) {
                try {
                    notifier.wait(1000);
                } catch (InterruptedException ex) {
                    Helpers.logCooperativeCancellation(Logger.getLogger(GameFrame.class.getName()),
                            "refresh card notifier wait", ex);
                    break;
                }
            }
        }

        for (RemotePlayer jugador : players) {

            synchronized (jugador.getChat_notify_label()) {
                jugador.refreshSecPotLabel();
                jugador.refreshNotifyChatLabel();
            }
            // El GIF de game over del rebuy dura toda la decisión del
            // arruinado: recolócalo a la geometría compacta/normal nueva.
            jugador.refreshRebuyGifLabel();
        }

        // El overlay de coste de igualar está posicionado en absoluto sobre las
        // comunitarias: al cambiar la vista compacta (que puede mover/encoger el
        // community panel) hay que recolocarlo/reescalarlo con la geometría nueva.
        if (getCrupier() != null) {
            getCrupier().refreshCallCostOverlay();
        }
    }

    public boolean isGame_over_dialog() {
        return game_over_dialog;
    }

    public boolean isTimba_pausada() {
        return timba_pausada;
    }

    public void pauseTimba(String user) {

        synchronized (lock_pause) {

            if (isPartida_local()) {

                // Al PAUSAR viaja el nick de quien inicia la pausa; al REANUDAR
                // debe viajar el pausador original (nick_pause), que es el nick
                // que los clientes registraron al pausar y contra el que validan
                // el resume. Si el host reanudara la pausa de otro jugador y
                // enviara su propio nick, los clientes lo rechazarian y se
                // quedarian colgados con el overlay de pausa.
                String pause_owner = this.timba_pausada ? this.nick_pause : (user != null ? user : getNick_local());

                String userB64 = "";
                try {
                    userB64 = java.util.Base64.getEncoder().encodeToString(pause_owner.getBytes("UTF-8"));
                } catch (java.io.UnsupportedEncodingException ex) {
                    Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex);
                }
                getCrupier().broadcastGAMECommandFromServer("PAUSE#" + (this.timba_pausada ? "0" : "1") + "#" + userB64, user);

            } else if (getNick_local().equals(user)) {

                getCrupier().sendGAMECommandToServer("PAUSE#" + (this.timba_pausada ? "0" : "1"));

            }

            this.timba_pausada = !this.timba_pausada;

            if (this.timba_pausada) {

                this.nick_pause = user != null ? user : this.getNick_local();

                if (!GameFrame.getInstance().getCrupier().isIwtsthing()) {
                    Audio.playWavResource("misc/pause.wav");
                }
            } else {

                this.nick_pause = null;
            }

            this.lock_pause.notifyAll();

            Helpers.GUIRun(() -> {

                if (pausa_dialog == null) {
                    pausa_dialog = new PauseDialog(this, false);
                }

                if (timba_pausada) {

                    if (isPartida_local() || getNick_local().equals(user)) {
                        Helpers.setScaledIconButton(GameFrame.getInstance().getTapete().getCommunityCards().getPause_button(), getClass().getResource("/images/continue.png"), Math.round(0.6f * GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().getHeight()), Math.round(0.6f * GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().getHeight()));
                        GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().setText(Translator.translate("ui.continuar_2"));
                        GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().setEnabled(true);

                    } else {
                        GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().setEnabled(false);
                    }

                    pausa_dialog.setLocationRelativeTo(pausa_dialog.getParent());
                    pausa_dialog.setVisible(true);

                } else {
                    Helpers.setScaledIconButton(GameFrame.getInstance().getTapete().getCommunityCards().getPause_button(), getClass().getResource("/images/pause.png"), Math.round(0.6f * GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().getHeight()), Math.round(0.6f * GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().getHeight()));

                    if (isPartida_local()) {
                        GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().setText(Translator.translate("game.pausar"));
                    } else {
                        GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().setText(Translator.translate("game.pausar") + " (" + getLocalPlayer().getPause_counter() + ")");
                    }

                    GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().setEnabled((isPartida_local() || getLocalPlayer().getPause_counter() > 0));

                    pausa_dialog.setVisible(false);
                    pausa_dialog.dispose();
                    pausa_dialog = null;

                }
            });

        }

    }

    public FastChatDialog getFastchat_dialog() {
        return fastchat_dialog;
    }

    public void setGame_over_dialog(boolean game_over_dialog) {
        this.game_over_dialog = game_over_dialog;
    }

    public boolean checkPause() {

        boolean paused = false;

        synchronized (lock_pause) {
            while (GameFrame.getInstance() != null && (timba_pausada || GameFrame.getInstance().getCrupier().isFin_de_la_transmision())) {

                paused = true;

                try {
                    lock_pause.wait(GameFrame.WAIT_PAUSE);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    // Expected during pool shutdown — Crupier pause wait
                    // was interrupted cooperatively. Break out so we don't
                    // spin re-entering wait() with the interrupt flag still
                    // raised (which would throw immediately every iteration).
                    Logger.getLogger(GameFrame.class.getName()).log(Level.INFO,
                            "checkPause wait interrupted (cooperative cancellation)");
                    break;
                }
            }
        }

        return paused;

    }

    public JMenuItem getFull_screen_menu() {
        return full_screen_menu;
    }

    public static boolean isRECOVER() {
        return RECOVER;
    }

    public static void setRECOVER(boolean RECOVER) {
        GameFrame.RECOVER = RECOVER;
    }

    public JMenuItem getShortcuts_menu() {
        return shortcuts_menu;
    }

    public JMenu getFile_menu() {
        return file_menu;
    }

    public JMenu getHelp_menu() {
        return help_menu;
    }

    public JMenu getOpciones_menu() {
        return opciones_menu;
    }

    public JMenu getZoom_menu() {
        return zoom_menu;
    }

    public void showFastChatImage() {
        Helpers.GUIRunAndWait(() -> {
            if (GameFrame.CHAT_IMAGES_INGAME) {

                ChatImageDialog chat_image_dialog = new ChatImageDialog(this, true, this.getHeight());
                chat_image_dialog.setLocation((int) (this.getLocation().getX() + this.getWidth()) - chat_image_dialog.getWidth(), (int) this.getLocation().getY());
                chat_image_dialog.setVisible(true);
            }
        });
    }

    public void showFastChatDialog() {
        Helpers.GUIRun(() -> {
            if (fastchat_dialog != null) {

                FastChatDialog old_dialog = fastchat_dialog;

                fastchat_dialog = new FastChatDialog(this, false, fastchat_dialog.getChat_box(), old_dialog.isAuto_close());

                old_dialog.dispose();

            } else {
                fastchat_dialog = new FastChatDialog(this, false, null, true);
            }

            fastchat_dialog.setLocation(this.getX(), this.getY() + this.getHeight() - fastchat_dialog.getHeight());

            fastchat_dialog.setVisible(true);
        });
    }

    public JMenuItem getHalt_game_menu() {
        return halt_game_menu;
    }

    public void latencyStats(boolean enable) {

        RemotePlayer[] remote_players = GameFrame.getInstance().getTapete().getRemotePlayers();

        Helpers.GUIRun(() -> {
            for (RemotePlayer player : remote_players) {
                player.getLatency_label().setVisible(enable);
            }
        });
    }

    private void setupGlobalShortcuts() {

        HashMap<KeyStroke, Action> actionMap = new HashMap<>();

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0), new AbstractAction("LATENCY_STATS") {
            @Override
            public void actionPerformed(ActionEvent e) {

                if (GameFrame.getInstance().isPartida_local()) {

                    latency_stats = !latency_stats;

                    latencyStats(latency_stats);

                }
            }

        });

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), new AbstractAction("REFRESH") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {
                refresh();

                InGameNotifyDialog dialog = new InGameNotifyDialog(GameFrame.getInstance(), false, Translator.translate("ui.tapete_refrescado"), Color.YELLOW, Color.BLACK, null, NOTIFICATION_TIMEOUT);
                dialog.setOpacity(0.5f);
                dialog.setLocation(dialog.getParent().getLocation());
                dialog.setVisible(true);

            }
        }
        );

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Q, KeyEvent.CTRL_DOWN_MASK), new AbstractAction("QUIT") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {
                GameFrame.getInstance().getExit_menu().doClick();
            }
        }
        );

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0), new AbstractAction("BUYIN") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {
                GameFrame.getInstance().getLocalPlayer().player_stack_click();
            }
        }
        );

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_H, KeyEvent.ALT_DOWN_MASK), new AbstractAction("HALT") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {
                GameFrame.getInstance().getHalt_game_menu().doClick();
            }
        }
        );

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_P, KeyEvent.ALT_DOWN_MASK), new AbstractAction("PAUSE") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {
                GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().doClick();
            }
        }
        );

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.ALT_DOWN_MASK), new AbstractAction("LIGHTS") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {
                GameFrame.getInstance().getTapete().getCommunityCards().lightsButtonClick();
            }
        }
        );

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.ALT_DOWN_MASK), new AbstractAction("FULL-SCREEN") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {
                full_screen_menuActionPerformed(e);
            }
        }
        );

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, KeyEvent.ALT_DOWN_MASK), new AbstractAction("COMPACT-CARDS") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {
                compact_menu.doClick();
            }
        }
        );

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, KeyEvent.CTRL_DOWN_MASK), new AbstractAction("ZOOM-IN") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {

                zoom_menu_inActionPerformed(e);

            }
        }
        );

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, KeyEvent.CTRL_DOWN_MASK), new AbstractAction("ZOOM-OUT") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {

                zoom_menu_outActionPerformed(e);

            }
        }
        );

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_0, KeyEvent.CTRL_DOWN_MASK), new AbstractAction("ZOOM-RESET") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {

                zoom_menu_resetActionPerformed(e);

            }
        }
        );

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, KeyEvent.ALT_DOWN_MASK), new AbstractAction("CHAT") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {
                chat_menuActionPerformed(e);
            }
        }
        );

        actionMap.put(KeyStroke.getKeyStroke('º'), new AbstractAction("FASTCHAT") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {

                showFastChatDialog();

            }
        }
        );

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_1, 0), new AbstractAction("FASTCHAT-IMAGE") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {

                showFastChatImage();

            }
        }
        );

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.ALT_DOWN_MASK), new AbstractAction("REGISTRO") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {
                registro_menuActionPerformed(e);
            }
        }
        );

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, KeyEvent.ALT_DOWN_MASK), new AbstractAction("RELOJ") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {
                time_menu.doClick();
            }
        }
        );

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), new AbstractAction("FOLD-BUTTON") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {
                if (!getCrupier().isSincronizando_mano()) {
                    getLocalPlayer().getPlayer_fold().doClick();
                }
            }
        }
        );

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), new AbstractAction("CHECK-BUTTON") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {
                if (!getCrupier().isSincronizando_mano()) {
                    if (GameFrame.getInstance().getLocalPlayer().isBoton_mostrar()) {
                        getLocalPlayer().getPlayer_allin().doClick();

                    } else {
                        getLocalPlayer().getPlayer_check().doClick();
                    }
                }
            }
        }
        );

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), new AbstractAction("BET-BUTTON") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {
                if (!getCrupier().isSincronizando_mano()) {
                    getLocalPlayer().getPlayer_bet_button().doClick();
                }
            }
        }
        );

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK), new AbstractAction("ALLIN-BUTTON") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {
                if (!getCrupier().isSincronizando_mano() && !GameFrame.getInstance().getLocalPlayer().isBoton_mostrar()) {
                    getLocalPlayer().getPlayer_allin().doClick();
                }
            }
        }
        );

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), new AbstractAction("BET-LEFT") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {
                if (!getCrupier().isSincronizando_mano()) {
                    if (getLocalPlayer().getBet_spinner().isEnabled()) {

                        SpinnerNumberModel model = (SpinnerNumberModel) getLocalPlayer().getBet_spinner().getModel();

                        if (model.getPreviousValue() != null) {

                            getLocalPlayer().getBet_spinner().setValue(model.getPreviousValue());
                        }
                    }
                }
            }
        }
        );

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), new AbstractAction("BET-DOWN") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {
                if (!getCrupier().isSincronizando_mano()) {

                    if (getLocalPlayer().getBet_spinner().isEnabled()) {
                        SpinnerNumberModel model = (SpinnerNumberModel) getLocalPlayer().getBet_spinner().getModel();
                        if (model.getPreviousValue() != null) {
                            getLocalPlayer().getBet_spinner().setValue(model.getPreviousValue());
                        }
                    }

                }
            }
        }
        );

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), new AbstractAction("BET-RIGHT") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {
                if (!getCrupier().isSincronizando_mano()) {
                    if (getLocalPlayer().getBet_spinner().isEnabled()) {
                        SpinnerNumberModel model = (SpinnerNumberModel) getLocalPlayer().getBet_spinner().getModel();
                        if (model.getNextValue() != null) {
                            getLocalPlayer().getBet_spinner().setValue(model.getNextValue());
                        }
                    }
                }
            }
        }
        );

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), new AbstractAction("BET-UP") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {
                if (!getCrupier().isSincronizando_mano()) {

                    if (getLocalPlayer().getBet_spinner().isEnabled()) {
                        SpinnerNumberModel model = (SpinnerNumberModel) getLocalPlayer().getBet_spinner().getModel();
                        if (model.getNextValue() != null) {
                            getLocalPlayer().getBet_spinner().setValue(model.getNextValue());
                        }
                    }

                }
            }
        }
        );

        KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();

        if (GameFrame.key_event_dispatcher != null) {
            kfm.removeKeyEventDispatcher(GameFrame.key_event_dispatcher);
        }

        GameFrame.key_event_dispatcher = (KeyEvent e) -> {
            KeyStroke keyStroke = KeyStroke.getKeyStrokeForEvent(e);
            JFrame frame = GameFrame.getInstance();
            if (actionMap.containsKey(keyStroke) && !file_menu.isSelected() && !apariencia_menu.isSelected() && !opciones_menu.isSelected() && !help_menu.isSelected() && (frame.isActive() || (pausa_dialog != null && pausa_dialog.hasFocus()) || (crupier.isFin_de_la_transmision() && keyStroke.equals(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.ALT_DOWN_MASK))))) {
                final Action a = actionMap.get(keyStroke);
                final ActionEvent ae = new ActionEvent(e.getSource(), e.getID(), null);
                Helpers.GUIRun(() -> {
                    a.actionPerformed(ae);
                });
                return true;
            }
            return false;
        };

        kfm.addKeyEventDispatcher(GameFrame.key_event_dispatcher);
    }

    private WaitingRoomFrame sala_espera;

    public Crupier getCrupier() {
        return crupier;
    }

    public boolean isPartida_local() {
        return partida_local;
    }

    public String getNick_local() {
        return nick_local;
    }

    public Map<String, Participant> getParticipantes() {
        return this.sala_espera.getParticipantes();
    }

    public static float getZOOM_STEP() {
        return ZOOM_STEP;
    }

    public ArrayList<Player> getJugadores() {
        return jugadores;
    }

    public GameLogDialog getRegistro() {
        return registro_dialog;

    }

    public Card getFlop1() {
        return tapete.getCommunityCards().getFlop1();
    }

    public Card getFlop2() {
        return tapete.getCommunityCards().getFlop2();
    }

    public JProgressBar getBarra_tiempo() {
        return tapete.getCommunityCards().getBarra_tiempo();
    }

    public Card getFlop3() {
        return tapete.getCommunityCards().getFlop3();
    }

    public LocalPlayer getLocalPlayer() {
        return tapete.getLocalPlayer();
    }

    public Card getRiver() {
        return tapete.getCommunityCards().getRiver();
    }

    public Card getTurn() {
        return tapete.getCommunityCards().getTurn();
    }

    public JMenuItem getZoom_menu_in() {
        return zoom_menu_in;
    }

    public JMenuItem getZoom_menu_out() {
        return zoom_menu_out;
    }

    public TablePanel getTapete() {
        return tapete;
    }

    public Card[] getCartas_comunes() {
        return tapete.getCommunityCards().getCartasComunes();
    }

    private void setHandBackground(Color color) {
        Helpers.GUIRun(() -> {
            tapete.getCommunityCards().getHand_label().setOpaque(false);
            tapete.getCommunityCards().getHand_panel().setOpaque(true);
            tapete.getCommunityCards().getHand_panel().setBackground(color);
        });
    }

    public void setTapeteMano(int mano) {

        Helpers.GUIRun(() -> {
            tapete.getCommunityCards().getHand_label().setText("#" + String.valueOf(mano) + (GameFrame.MANOS != -1 ? "/" + String.valueOf(GameFrame.MANOS) : ""));

            if (GameFrame.MANOS != -1 && crupier.getMano() > GameFrame.MANOS) {
                setHandBackground(Color.red);
                tapete.getCommunityCards().getHand_label().setForeground(Color.WHITE);
                tapete.getCommunityCards().getHand_label().setOpaque(true);
            } else if (GameFrame.MANOS == -1 && tapete.getCommunityCards().getHand_label().getBackground() == Color.RED) {
                tapete.getCommunityCards().getHand_label().setOpaque(false);
                tapete.getCommunityCards().getHand_label().setForeground(tapete.getCommunityCards().getColor_contadores());
            }
        });
    }

    public void zoom(float factor, final ConcurrentLinkedQueue<Long> notifier) {

        final ConcurrentLinkedQueue<Long> mynotifier = new ConcurrentLinkedQueue<>();

        for (ZoomableInterface zoomable : zoomables) {
            Helpers.threadRun(() -> {
                zoomable.zoom(factor, mynotifier);
            });
        }

        synchronized (mynotifier) {
            while (mynotifier.size() < zoomables.length) {
                try {
                    mynotifier.wait(1000);
                } catch (InterruptedException ex) {
                    Helpers.logCooperativeCancellation(Logger.getLogger(GameFrame.class.getName()),
                            "zoom notifier wait", ex);
                    break;
                }
            }
        }

        if (notifier != null) {

            notifier.add(Thread.currentThread().threadId());

            synchronized (notifier) {

                notifier.notifyAll();

            }
        }
    }

    public void setTapeteBote(double bote, Double beneficio) {

        // Run-it-twice: marca a qué cara (CARA-A/CARA-B) corresponde lo mostrado
        // mientras se corren los dos boards (null fuera de run-it-twice). La cara
        // va en el PREFIJO ("BOTE (CARA-A): X") en vez de un sufijo al final.
        final String rit_tag = getCrupier() != null ? getCrupier().getRitPotBoardTag() : null;
        final String prefix = rit_tag != null
                ? Translator.translate("runittwice.pot_label_full", rit_tag)
                : Translator.translate("game.bote_2");

        final String suffix = beneficio != null ? " (" + Helpers.money2String(beneficio) + ")" : "";

        Helpers.GUIRun(() -> {
            // El número del bote rueda a velocidad constante (prefijo/sufijo intactos);
            // con el rodaje off o en recover salta de golpe.
            tapete.getCommunityCards().rollPotValue(prefix, bote, suffix, isCounterRollEnabled());
        });
    }

    public void setTapeteBote(String bote) {

        // Mismo prefijo RIT-aware que la sobrecarga (float, Float): el desglose
        // por bote del run-it-twice lleva la cara ("BOTE (CARA-A): #1{..}+#2{..}").
        // Fuera de RIT (tag null) → "BOTE:", idéntico a antes.
        final String rit_tag = getCrupier() != null ? getCrupier().getRitPotBoardTag() : null;
        final String prefix = rit_tag != null
                ? Translator.translate("runittwice.pot_label_full", rit_tag)
                : Translator.translate("game.bote_2");

        Helpers.GUIRun(() -> {
            // Estado textual del bote ("---", desglose RIT): se fija de golpe e invalida
            // el roller para que el siguiente valor numérico no anime desde un valor viejo.
            tapete.getCommunityCards().setPotTextImmediate(prefix + " " + bote);
        });
    }

    public void setTapeteApuestas(double apuestas) {

        // El bet_label muestra SOLO la calle actual (sin importe ni icono), para
        // reducir ruido. El bote de la calle (Crupier.apuestas) se sigue calculando
        // y pasando aquí: para volver a mostrarlo basta reañadirlo al setText.
        Helpers.GUIRun(() -> {
            String street = STREETS[getCrupier().getStreet() - 1];

            tapete.getCommunityCards().getBet_label().setText(street);

            tapete.getCommunityCards().getBet_label().setVisible(true);
        });

    }

    public void downgradeAndRefreshTapete() {

        // Si la partida ya terminó (p.ej. el server decide salir en la misma mano
        // en que un jugador se fue y el tablero se acorta), NO reconstruir la mesa:
        // el TablePanelFactory crea un tablero nuevo con paneles VISIBLES por
        // defecto que, encolado en el EDT, saldría DESPUÉS del hideALL del balance
        // final (race factoría-vs-salida → los jugadores reaparecían sobre el
        // balance). Sin next hand, no hay nada que reconstruir.
        if (getCrupier() != null && getCrupier().isFin_de_la_transmision()) {
            return;
        }

        TablePanel nuevo_tapete = TablePanelFactory.downgradePanel(tapete);

        if (nuevo_tapete != null) {

            GameFrame.getInstance().getJugadores().clear();

            GameFrame.getInstance().getJugadores().addAll(Arrays.asList(nuevo_tapete.getPlayers()));

            Helpers.GUIRunAndWait(() -> {
                GameFrame.getInstance().getContentPane().remove(frame_layer);
                tapete = nuevo_tapete;
                zoomables = new ZoomableInterface[]{tapete};
                frame_layer = new JLayer<>(tapete, capa_brillo);
                GameFrame.getInstance().getContentPane().add(frame_layer);

                // TOCTOU: si la partida terminó mientras se construía/swapeaba el
                // tablero acortado, sus paneles (visibles por defecto) NO deben
                // aparecer sobre el balance final (la otra mitad de la race).
                if (getCrupier() != null && getCrupier().isFin_de_la_transmision()) {
                    nuevo_tapete.hideALL();
                }

                Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), Crupier.TIEMPO_PENSAR);

                updateSoundIcon();

                switch (GameFrame.COLOR_TAPETE) {

                    case "verde":
                        cambiarColorContadoresTapete(new Color(153, 204, 0));
                        break;

                    case "azul":
                        cambiarColorContadoresTapete(new Color(102, 204, 255));
                        break;

                    case "rojo":
                        cambiarColorContadoresTapete(new Color(255, 204, 51));
                        break;

                    case "negro":
                        cambiarColorContadoresTapete(Color.LIGHT_GRAY);
                        break;

                    case "madera":
                        cambiarColorContadoresTapete(Color.WHITE);
                        break;

                    default:
                        cambiarColorContadoresTapete(Color.WHITE);
                        break;
                }

                Helpers.TapetePopupMenu.addTo(tapete, true);

                setupGlobalShortcuts();

                Helpers.preserveOriginalFontSizes(GameFrame.getInstance());

                Helpers.updateFonts(GameFrame.getInstance(), Helpers.GUI_FONT, null);

                tapete.getCommunityCards().getTiempo_partida().setFont(new Font("Monospaced", Font.BOLD, 28));

                Helpers.translateComponents(GameFrame.getInstance(), false);

                if (GameFrame.getInstance() != null && GameFrame.getInstance().isFull_screen()) {
                    GameFrame.getInstance().setExtendedState(JFrame.MAXIMIZED_BOTH);
                }

                if (GameFrame.ZOOM_LEVEL != 0) {
                    Helpers.threadRun(() -> {
                        GameFrame.getInstance().zoom(1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP, null);
                    });
                }
            });

            crupier.actualizarContadoresTapete();
        }
    }

    public void hideTapeteApuestas() {

        Helpers.GUIRun(() -> {
            // Making it invisible is enough. No need to revalidate/repaint an invisible component.
            tapete.getCommunityCards().getBet_label().setVisible(false);
        });

        // Fin de las apuestas de la mano (showdown / run-out): el overlay de coste
        // de igualar también deja de tener sentido.
        tapete.hideCallCostOverlay();

    }

    public void setTapeteCiegas(double pequeña, double grande) {

        Helpers.GUIRun(() -> {
            if (crupier.getCiegas_update() != null) {
                tapete.getCommunityCards().getBlinds_panel().setOpaque(true);
                tapete.getCommunityCards().getBlinds_panel().setBackground(Color.YELLOW);
                tapete.getCommunityCards().getBlinds_label().setForeground(Color.BLACK);
            } else {
                tapete.getCommunityCards().getBlinds_panel().setOpaque(false);
                tapete.getCommunityCards().getBlinds_panel().setBackground(null);
                // El color de las ciegas sigue la variable ESTABLE del color de los
                // contadores, NO el foreground del pot_label: ese parpadea en amarillo
                // al aterrizar una ficha (flashPotLabelYellow) y cambia a naranja/
                // blanco/negro en el showdown. Como actualizarContadoresTapete se
                // invoca muy a menudo, leerlo de ahí dejaba las ciegas "pegadas" a ese
                // color transitorio (el amarillo fantasma).
                Color counters_color = tapete.getCommunityCards().getColor_contadores();
                if (counters_color != null) {
                    tapete.getCommunityCards().getBlinds_label().setForeground(counters_color);
                }
            }

            tapete.getCommunityCards().getBlinds_label().setText((GameFrame.ANTE ? "(A) " : "") + Helpers.money2String(pequeña) + " / " + Helpers.money2String(grande) + (GameFrame.STRADDLE ? " / ST" : "") + (GameFrame.CIEGAS_DOUBLE > 0 ? " @ " + String.valueOf(GameFrame.CIEGAS_DOUBLE) + (GameFrame.CIEGAS_DOUBLE_TYPE <= 1 ? "'" : "*") + (crupier.getCiegas_double() > 0 ? " (" + String.valueOf(crupier.getCiegas_double()) + ")" : "") : ""));
        });

    }

    public WaitingRoomFrame getSala_espera() {
        return sala_espera;
    }

    public void updateSoundIcon() {

        if (tapete.getCommunityCards().getBlinds_label().getHeight() > 0) {

            Helpers.GUIRun(() -> {
                tapete.getCommunityCards().getSound_icon().setPreferredSize(new Dimension(tapete.getCommunityCards().getBlinds_label().getHeight(), tapete.getCommunityCards().getBlinds_label().getHeight()));
                Helpers.setScaledIconLabel(tapete.getCommunityCards().getSound_icon(), getClass().getResource(GameFrame.SONIDOS ? "/images/sound.png" : "/images/mute.png"), tapete.getCommunityCards().getBlinds_label().getHeight(), tapete.getCommunityCards().getBlinds_label().getHeight());
            });
        } else {
            Helpers.GUIRun(() -> {
                tapete.getCommunityCards().getSound_icon().setPreferredSize(new Dimension(CommunityCardsPanel.SOUND_ICON_WIDTH, CommunityCardsPanel.SOUND_ICON_WIDTH));
                Helpers.setScaledIconLabel(tapete.getCommunityCards().getSound_icon(), getClass().getResource(GameFrame.SONIDOS ? "/images/sound.png" : "/images/mute.png"), CommunityCardsPanel.SOUND_ICON_WIDTH, CommunityCardsPanel.SOUND_ICON_WIDTH);
            });
        }
    }

    // === Controles de audio: fuente única de verdad ===
    // Toda la lógica de aplicar/persistir/difundir vive aquí. El diálogo de
    // ajustes de audio y los callsites (icono de altavoz, recover, atajos de
    // teclado) llaman a estos métodos. Ya no hay controles de audio en el menú
    // ni en el popup, así que no se sincroniza nada con ellos. Los tres
    // controles que NO son reglas de host (sonido, coña, música) son estáticos:
    // valen también desde la ventana de inicio, donde aún no hay GameFrame.
    public static void setSonidos(boolean on) {

        GameFrame.SONIDOS = on;

        Helpers.PROPERTIES.setProperty("sonidos", String.valueOf(on));
        Helpers.savePropertiesFile();

        if (!on) {
            Audio.muteAll();
        } else {
            Audio.unmuteAll();
        }

        // Refresca el icono de altavoz en el contexto que exista (tapete en
        // juego, ventana de inicio en el arranque).
        if (getInstance() != null) {
            getInstance().updateSoundIcon();
        }

        Init.refreshSoundIcon();

        WaitingRoomFrame.refreshSoundIcon();
    }

    public static void setSonidosChorra(boolean on) {

        GameFrame.SONIDOS_CHORRA = on;

        Helpers.PROPERTIES.setProperty("sonidos_chorra", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    public static void setMusicaAmbiental(boolean on) {

        GameFrame.MUSICA_AMBIENTAL = on;

        Helpers.PROPERTIES.setProperty("sonido_ascensor", String.valueOf(on));
        Helpers.savePropertiesFile();

        // Un único toggle para la música de juego y la de la sala de espera:
        // el flag lo gobierna effectiveLoopVolume; aquí solo refrescamos el
        // volumen del loop que esté sonando para que se oiga al instante.
        Audio.refreshLoopVolume(Audio.ASCENSOR_VOLUME.getKey());
        Audio.refreshLoopVolume(Audio.WAITING_ROOM_VOLUME.getKey());
    }

    // Regla global del host: habilita/deshabilita el TTS para todos. El bloqueo
    // "solo para mí" vive aparte (AudioDeviceManager.isBlockTtsLocal). Es
    // estático y persiste la preferencia local para poder preseleccionarla
    // antes de la partida; solo difunde a los clientes si eres anfitrión.
    public static void setTTSGlobal(boolean on) {

        GameFrame.TTS_SERVER = on;

        Helpers.PROPERTIES.setProperty("tts_server", String.valueOf(on));
        Helpers.savePropertiesFile();

        GameFrame gf = getInstance();

        if (gf != null && gf.isPartida_local()) {
            Helpers.threadRun(() -> {
                gf.getCrupier().broadcastGAMECommandFromServer("TTS#" + (on ? "1" : "0"), null);
                // Persiste la regla para que sobreviva a un detener+recuperar.
                GameFrame.persistRecoverSettings(gf.getCrupier().getSqlite_game_id());
            });
        }
    }

    // Regla global del host: habilita/deshabilita las notas de voz para todos.
    public static void setVoiceMessages(boolean on) {

        GameFrame.VOICE_MESSAGES = on;

        Helpers.PROPERTIES.setProperty("voice_messages", String.valueOf(on));
        Helpers.savePropertiesFile();

        GameFrame gf = getInstance();

        if (gf != null && gf.isPartida_local()) {
            Helpers.threadRun(() -> {
                gf.getCrupier().broadcastGAMECommandFromServer("VOICEMSGRULE#" + (on ? "1" : "0"), null);
                GameFrame.persistRecoverSettings(gf.getCrupier().getSqlite_game_id());
            });
        }
    }

    // Reglas de juego del host (IWTSTH / Run It Twice / Rabbit Hunting). Antes
    // vivían como toggles del menú Preferencias + popup; ahora la lógica está
    // centralizada aquí y la dispara el diálogo "Ajustes de partida" (y el
    // re-aplicado en recover). Solo difunde y persiste si eres anfitrión; en el
    // cliente el flag lo actualiza el comando *RULE entrante. El broadcast va bajo
    // lock_fin_mano (como hacían los handlers originales) para no cambiar la regla
    // en mitad de la resolución de una mano.
    public static void setIwtsthRule(boolean on) {

        GameFrame gf = getInstance();

        if (gf != null && gf.isPartida_local()) {
            Helpers.threadRun(() -> {
                synchronized (gf.getCrupier().getLock_fin_mano()) {
                    GameFrame.IWTSTH_RULE = on;
                    gf.getCrupier().broadcastGAMECommandFromServer("IWTSTHRULE#" + (on ? "1" : "0"), null);
                    GameFrame.persistRecoverSettings(gf.getCrupier().getSqlite_game_id());
                }
            });
        } else {
            GameFrame.IWTSTH_RULE = on;
        }
    }

    public static void setRunItTwiceRule(boolean on) {

        // Congelado durante el run-out del all-in: el voto ya se está decidiendo con
        // el valor actual, no se permite cambiarlo hasta NUEVA_MANO.
        if (RUN_IT_TWICE_LOCKED) {
            return;
        }

        GameFrame gf = getInstance();

        if (gf != null && gf.isPartida_local()) {
            Helpers.threadRun(() -> {
                synchronized (gf.getCrupier().getLock_fin_mano()) {
                    GameFrame.RUN_IT_TWICE = on;
                    gf.getCrupier().broadcastGAMECommandFromServer("RUNITWICERULE#" + (on ? "1" : "0"), null);
                    GameFrame.persistRecoverSettings(gf.getCrupier().getSqlite_game_id());
                }
            });
        } else {
            GameFrame.RUN_IT_TWICE = on;
        }
    }

    public static void setRabbitHunting(int mode) {

        GameFrame gf = getInstance();

        if (gf != null && gf.isPartida_local()) {
            Helpers.threadRun(() -> {
                synchronized (gf.getCrupier().getLock_fin_mano()) {
                    GameFrame.RABBIT_HUNTING = mode;
                    gf.getCrupier().broadcastGAMECommandFromServer("RABBITRULE#" + String.valueOf(mode), null);
                    GameFrame.persistRecoverSettings(gf.getCrupier().getSqlite_game_id());
                }
            });
        } else {
            GameFrame.RABBIT_HUNTING = mode;
        }
    }

    public JCheckBoxMenuItem getCompact_menu() {
        return compact_menu;
    }

    public JMenu getMenu_barajas() {
        return menu_barajas;
    }

    private void generarBarajasMenu() {

        HashMap hm = new HashMap<String, Object[]>();

        hm.putAll(Card.BARAJAS);

        TreeMap<String, Object[]> sorted_hm = new TreeMap<>();

        sorted_hm.putAll(hm);

        for (Map.Entry<String, Object[]> entry : sorted_hm.entrySet()) {

            javax.swing.JRadioButtonMenuItem menu_item = new javax.swing.JRadioButtonMenuItem(entry.getKey());

            menu_item.setFont(new java.awt.Font("Dialog", 0, 14));

            menu_item.addActionListener((ActionEvent e) -> {
                if (GameFrame.BARAJA.equals("interstate60") && menu_item.getText().equals("interstate60")) {
                    i60_c++;
                } else {
                    i60_c = 1;
                }
                GameFrame.BARAJA = menu_item.getText();
                Helpers.PROPERTIES.setProperty("baraja", menu_item.getText());
                Helpers.savePropertiesFile();
                for (Component menu : menu_barajas.getMenuComponents()) {
                    ((javax.swing.JRadioButtonMenuItem) menu).setSelected(false);
                }
                menu_item.setSelected(true);
                for (Component menu : Helpers.TapetePopupMenu.BARAJAS_MENU.getMenuComponents()) {

                    ((javax.swing.JRadioButtonMenuItem) menu).setSelected(((javax.swing.JRadioButtonMenuItem) menu).getText().equals(menu_item.getText()));
                }
                Helpers.threadRun(() -> {
                    cambiarBaraja();
                    if (Init.M2 != null && GameFrame.BARAJA.equals("interstate60") && i60_c == 5) {

                        try {
                            Files.write(Paths.get(System.getProperty("java.io.tmpdir") + "/M2e.gif"), (byte[]) M2.invoke(null, "e"));
                        } catch (Exception ex) {
                            Logger.getLogger(Init.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        i60_c = 0;

                        Helpers.GUIRunAndWait(() -> {
                            try {
                                gif_dialog = new GifAnimationDialog(this, true, new ImageIcon(Files.readAllBytes(Paths.get(System.getProperty("java.io.tmpdir") + "/M2e.gif"))), Helpers.getGIFFramesCount(Paths.get(System.getProperty("java.io.tmpdir") + "/M2e.gif").toUri().toURL()));
                                gif_dialog.setLocationRelativeTo(gif_dialog.getParent());
                                gif_dialog.setVisible(true);
                            } catch (IOException | ImageProcessingException ex) {
                                Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex);
                            }

                        });
                        try {
                            Files.deleteIfExists(Paths.get(System.getProperty("java.io.tmpdir") + "/M2e.gif"));
                        } catch (IOException ex) {
                            Logger.getLogger(Init.class.getName()).log(Level.SEVERE, null, ex);
                        }

                    }
                });
            });

            if (((javax.swing.JRadioButtonMenuItem) menu_item).getText().equals(GameFrame.BARAJA)) {
                ((javax.swing.JRadioButtonMenuItem) menu_item).setSelected(true);
            } else {
                ((javax.swing.JRadioButtonMenuItem) menu_item).setSelected(false);
            }

            menu_barajas.add(menu_item);

        }
    }

    /**
     * Creates new form CoronaMainView
     */
    public GameFrame(WaitingRoomFrame salaespera, String nicklocal, boolean partidalocal) {

        THIS = this;

        // Registrar shutdown hook lo antes posible para cubrir tambien
        // caidas tempranas (Ctrl+C / cerrar consola durante el AJUGAR).
        // El hook discrimina internamente entre host y cliente:
        //   - Host: broadcast SERVEREXITRECOVER (con password) a los peers.
        //   - Cliente: envia EXIT#testamento al host.
        registerShutdownHook();

        sala_espera = salaespera; //Esto aquí arriba para que no pete getParticipantes()

        nick_local = nicklocal;

        partida_local = partidalocal;

        tapete = TablePanelFactory.getPanel(getParticipantes().size());

        Player[] players = tapete.getPlayers();

        zoomables = new ZoomableInterface[]{tapete};

        jugadores = new ArrayList<>();

        for (int j = 0; j < getParticipantes().size(); j++) {
            jugadores.add(players[j]);
        }

        for (Map.Entry<String, Participant> entry : getParticipantes().entrySet()) {

            Participant p = entry.getValue();

            if (p != null) {

                if (p.getAvatar() != null) {
                    nick2avatar.put(entry.getKey(), p.getAvatar().getAbsolutePath());
                } else if (partidalocal && p.isCpu()) {
                    nick2avatar.put(entry.getKey(), "*");
                } else {
                    nick2avatar.put(entry.getKey(), "");
                }

            } else {

                nick2avatar.put(entry.getKey(), sala_espera.getLocal_avatar() != null ? sala_espera.getLocal_avatar().getAbsolutePath() : "");
            }
        }

        Bot.TRACKER_MEMORY.clear();
        // Reset the static security lockdown flag — it never clears itself,
        // so a previous session that ended in lockdown would otherwise leak
        // into this fresh game.
        Crupier.SECURITY_LOCKDOWN = false;
        crupier = new Crupier();

        initComponents();

        setTitle(Init.WINDOW_TITLE + Translator.translate("game.timba_en_curso_2") + nicklocal + ")");

        frame_layer = new JLayer<>(tapete, capa_brillo);

        getContentPane().add(frame_layer);

        force_reconnect_menu.setEnabled(isPartida_local());

        compact_menu.setSelected(GameFrame.VISTA_COMPACTA > 0);

        menu_cinematicas.setSelected(GameFrame.CINEMATICAS_PREF);

        auto_fullscreen_menu.setSelected(GameFrame.AUTO_FULLSCREEN);

        // Defensa: si una partida anterior terminó en mitad de un run-out, el flag
        // pudo quedar activo (solo lo limpia NUEVA_MANO). Se resetea al montar la mesa
        // para no arrancar con Run It Twice congelado en el diálogo de ajustes.
        GameFrame.RUN_IT_TWICE_LOCKED = false;

        last_hand_menu.setSelected(false);

        rebuy_now_menu.setSelected(false);

        chat_image_menu.setSelected(GameFrame.CHAT_IMAGES_INGAME);

        confirmar_menu.setSelected(GameFrame.CONFIRM_ACTIONS);

        auto_action_menu.setSelected(GameFrame.AUTO_ACTION_BUTTONS);

        auto_fit_zoom_menu.setSelected(GameFrame.AUTO_ZOOM);

        // "Ajustes": diálogo unificado con pestañas Apariencia / Audio / Partida.
        // Único acceso a los ajustes desde el menú Preferencias (sustituye tanto a la
        // antigua entrada de audio como al "Ajustes de partida"). Campo a mano
        // (initComponents es generado). Tiene gemelo en el popup del tapete y en el
        // icono de engranaje del CommunityCardsPanel.
        ajustes_partida_menu = new javax.swing.JMenuItem();
        ajustes_partida_menu.setFont(new java.awt.Font("Dialog", 0, 14));
        ajustes_partida_menu.putClientProperty("i18n.key", "settings.ajustes");
        ajustes_partida_menu.setText(Translator.translate("settings.ajustes"));
        ajustes_partida_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/gear.png")));
        ajustes_partida_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openSettingsDialog();
            }
        });
        opciones_menu.insert(ajustes_partida_menu, 0);

        // Recompra automática al arruinarse: checkbox en Preferencias justo tras
        // "RECOMPRAR (siguiente mano)". Campo a mano (initComponents es generado);
        // preferencia LOCAL con el mismo patrón de sincronización menú↔popup que
        // el resto.
        auto_rebuy_menu = new javax.swing.JCheckBoxMenuItem();
        auto_rebuy_menu.setFont(new java.awt.Font("Dialog", 0, 14));
        auto_rebuy_menu.putClientProperty("i18n.key", "menu.recomprar_auto_arruinarse");
        auto_rebuy_menu.setText(Translator.translate("menu.recomprar_auto_arruinarse"));
        auto_rebuy_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/rebuy.png")));
        auto_rebuy_menu.setSelected(GameFrame.AUTO_REBUY_ON_BROKE);
        auto_rebuy_menu.setEnabled(GameFrame.REBUY);
        auto_rebuy_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                auto_rebuy_menuActionPerformed(evt);
            }
        });
        int rebuy_index = java.util.Arrays.asList(opciones_menu.getMenuComponents()).indexOf(rebuy_now_menu);
        opciones_menu.insert(auto_rebuy_menu, rebuy_index >= 0 ? rebuy_index + 1 : opciones_menu.getMenuComponentCount());

        generarBarajasMenu();

        // === Menú "Apariencia" en el menú-bar, lo más parecido al popup del
        // tapete: agrupa pantalla completa, zoom, vista compacta, reloj,
        // cinemáticas, animación e imágenes del chat + confirmar + barajas y
        // tapetes. Se re-parentan los ítems ya existentes (añadirlos a un menú
        // nuevo los quita de su menú anterior), sin tocar el código generado. El
        // antiguo menú Zoom desaparece del bar (sus controles van dentro). ===
        apariencia_menu = new javax.swing.JMenu(Translator.translate("menu.apariencia"));
        apariencia_menu.setFont(new java.awt.Font("Dialog", 0, 14));
        apariencia_menu.putClientProperty("i18n.key", "menu.apariencia");
        apariencia_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/gear.png")));

        apariencia_menu.add(full_screen_menu);
        apariencia_menu.add(auto_fullscreen_menu);
        apariencia_menu.add(compact_menu);

        // zoom_menu queda solo con los controles de zoom (lo demás ya se movió);
        // quitar los separadores que arrastraban a esos ítems.
        zoom_menu.remove(jSeparator5);
        zoom_menu.remove(jSeparator6);
        apariencia_menu.add(zoom_menu);

        apariencia_menu.add(time_menu);
        apariencia_menu.add(menu_cinematicas);

        // Submenú "Efectos de animación" con tres efectos combinables (reparto,
        // ciegas+dealer, apuestas). Sustituye al antiguo checkbox único.
        anim_reparto_menu = new javax.swing.JCheckBoxMenuItem();
        anim_reparto_menu.setFont(new java.awt.Font("Dialog", 0, 14));
        anim_reparto_menu.putClientProperty("i18n.key", "menu.efectos_animacion_reparto");
        anim_reparto_menu.setText(Translator.translate("menu.efectos_animacion_reparto"));
        anim_reparto_menu.setSelected(GameFrame.ANIMACION_REPARTO_PREF);
        anim_reparto_menu.addActionListener(e -> setAnimEffect(ANIM_REPARTO, anim_reparto_menu.isSelected()));

        anim_ciegas_dealer_menu = new javax.swing.JCheckBoxMenuItem();
        anim_ciegas_dealer_menu.setFont(new java.awt.Font("Dialog", 0, 14));
        anim_ciegas_dealer_menu.putClientProperty("i18n.key", "menu.efectos_animacion_ciegas_dealer");
        anim_ciegas_dealer_menu.setText(Translator.translate("menu.efectos_animacion_ciegas_dealer"));
        anim_ciegas_dealer_menu.setSelected(GameFrame.ANIMACION_CIEGAS_DEALER_PREF);
        anim_ciegas_dealer_menu.addActionListener(e -> setAnimEffect(ANIM_CIEGAS_DEALER, anim_ciegas_dealer_menu.isSelected()));

        anim_apuestas_menu = new javax.swing.JCheckBoxMenuItem();
        anim_apuestas_menu.setFont(new java.awt.Font("Dialog", 0, 14));
        anim_apuestas_menu.putClientProperty("i18n.key", "menu.efectos_animacion_apuestas");
        anim_apuestas_menu.setText(Translator.translate("menu.efectos_animacion_apuestas"));
        anim_apuestas_menu.setSelected(GameFrame.ANIMACION_APUESTAS_PREF);
        anim_apuestas_menu.addActionListener(e -> setAnimEffect(ANIM_APUESTAS, anim_apuestas_menu.isSelected()));

        anim_contadores_menu = new javax.swing.JCheckBoxMenuItem();
        anim_contadores_menu.setFont(new java.awt.Font("Dialog", 0, 14));
        anim_contadores_menu.putClientProperty("i18n.key", "menu.efectos_animacion_contadores");
        anim_contadores_menu.setText(Translator.translate("menu.efectos_animacion_contadores"));
        anim_contadores_menu.setSelected(GameFrame.ANIMACION_CONTADORES_PREF);
        anim_contadores_menu.addActionListener(e -> setAnimEffect(ANIM_CONTADORES, anim_contadores_menu.isSelected()));

        javax.swing.JMenu efectos_anim_menu = new javax.swing.JMenu(Translator.translate("menu.animacion_de_cartas"));
        efectos_anim_menu.setFont(new java.awt.Font("Dialog", 0, 14));
        efectos_anim_menu.putClientProperty("i18n.key", "menu.animacion_de_cartas");
        efectos_anim_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/dealer.png")));
        efectos_anim_menu.add(anim_reparto_menu);
        efectos_anim_menu.add(anim_ciegas_dealer_menu);
        efectos_anim_menu.add(anim_apuestas_menu);
        efectos_anim_menu.add(anim_contadores_menu);
        apariencia_menu.add(efectos_anim_menu);

        apariencia_menu.add(chat_image_menu);

        // Toggle "Coste de igualar": overlay sobre las comunitarias con lo que el
        // jugador local tendrá que poner para igualar. Por defecto activado.
        coste_igualar_menu = new javax.swing.JCheckBoxMenuItem();
        coste_igualar_menu.setFont(new java.awt.Font("Dialog", 0, 14));
        coste_igualar_menu.putClientProperty("i18n.key", "menu.coste_igualar");
        coste_igualar_menu.setText(Translator.translate("menu.coste_igualar"));
        coste_igualar_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/eyes.png")));
        coste_igualar_menu.setSelected(GameFrame.MOSTRAR_COSTE_IGUALAR);
        coste_igualar_menu.addActionListener(e -> setCosteIgualar(coste_igualar_menu.isSelected()));
        apariencia_menu.add(coste_igualar_menu);

        apariencia_menu.addSeparator();
        apariencia_menu.add(menu_barajas);
        apariencia_menu.add(menu_tapetes);

        // Preferencias pierde los ítems de apariencia; limpiar separadores sueltos.
        opciones_menu.remove(jSeparator1);
        opciones_menu.remove(jSeparator7);
        opciones_menu.remove(jSeparator8);
        opciones_menu.remove(decks_separator);

        // IWTSTH/RIT/Rabbit se movieron al diálogo "Ajustes de partida": sus dos
        // separadores en Preferencias quedan huérfanos, se quitan.
        opciones_menu.remove(jSeparator2);
        opciones_menu.remove(jSeparator10);

        // "Confirmar todas las acciones" justo debajo de "Botones AUTO".
        opciones_menu.remove(confirmar_menu);
        int auto_action_index = java.util.Arrays.asList(opciones_menu.getMenuComponents()).indexOf(auto_action_menu);
        opciones_menu.insert(confirmar_menu, auto_action_index >= 0 ? auto_action_index + 1 : opciones_menu.getMenuComponentCount());

        // "AUTO igualar" — abre el diálogo de auto-call (Activado + límite). Justo
        // debajo de "Modo AUTO". Gris si "Modo AUTO" está off.
        auto_call_menu = new javax.swing.JMenuItem();
        auto_call_menu.setFont(new java.awt.Font("Dialog", 0, 14));
        auto_call_menu.putClientProperty("i18n.key", "menu.auto_call");
        auto_call_menu.setText(Translator.translate("menu.auto_call"));
        auto_call_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/auto.png")));
        auto_call_menu.setEnabled(GameFrame.AUTO_ACTION_BUTTONS);
        auto_call_menu.addActionListener(e -> openAutoCallMaxDialog());
        int auto_call_index = java.util.Arrays.asList(opciones_menu.getMenuComponents()).indexOf(auto_action_menu);
        opciones_menu.insert(auto_call_menu, auto_call_index >= 0 ? auto_call_index + 1 : opciones_menu.getMenuComponentCount());

        // "Persistir modo AUTO entre manos" debajo de "AUTO igualar". Hermano gris:
        // solo se habilita con "Modo AUTO" activo.
        auto_action_persist_menu = new javax.swing.JCheckBoxMenuItem();
        auto_action_persist_menu.setFont(new java.awt.Font("Dialog", 0, 14));
        auto_action_persist_menu.putClientProperty("i18n.key", "menu.persistir_auto");
        auto_action_persist_menu.setText(Translator.translate("menu.persistir_auto"));
        auto_action_persist_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/auto.png")));
        auto_action_persist_menu.setSelected(GameFrame.AUTO_ACTION_PERSIST);
        auto_action_persist_menu.setEnabled(GameFrame.AUTO_ACTION_BUTTONS);
        auto_action_persist_menu.addActionListener(e -> setAutoActionPersist(auto_action_persist_menu.isSelected()));
        int auto_action_persist_index = java.util.Arrays.asList(opciones_menu.getMenuComponents()).indexOf(auto_call_menu);
        opciones_menu.insert(auto_action_persist_menu, auto_action_persist_index >= 0 ? auto_action_persist_index + 1 : opciones_menu.getMenuComponentCount());

        // "Confirmar acción AUTO (5s)" — toggle del diálogo MODO AUTO, en el mismo
        // grupo, debajo de Persistir. Hermano gris: solo con "Modo AUTO" activo.
        modo_auto_confirm_menu = new javax.swing.JCheckBoxMenuItem();
        modo_auto_confirm_menu.setFont(new java.awt.Font("Dialog", 0, 14));
        modo_auto_confirm_menu.putClientProperty("i18n.key", "menu.modo_auto_confirm");
        modo_auto_confirm_menu.setText(Translator.translate("menu.modo_auto_confirm"));
        modo_auto_confirm_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/auto.png")));
        modo_auto_confirm_menu.setSelected(GameFrame.MODO_AUTO_CONFIRM);
        modo_auto_confirm_menu.setEnabled(GameFrame.AUTO_ACTION_BUTTONS);
        modo_auto_confirm_menu.addActionListener(e -> setModoAutoConfirm(modo_auto_confirm_menu.isSelected()));
        int modo_auto_index = java.util.Arrays.asList(opciones_menu.getMenuComponents()).indexOf(auto_action_persist_menu);
        opciones_menu.insert(modo_auto_confirm_menu, modo_auto_index >= 0 ? modo_auto_index + 1 : opciones_menu.getMenuComponentCount());

        // Aísla el grupo "Botones AUTO + hijos" con separadores arriba y abajo
        // (sin duplicar si ya hay un separador adyacente).
        int auto_group_start = java.util.Arrays.asList(opciones_menu.getMenuComponents()).indexOf(auto_action_menu);
        if (auto_group_start > 0 && !(opciones_menu.getMenuComponent(auto_group_start - 1) instanceof javax.swing.JPopupMenu.Separator)) {
            opciones_menu.insertSeparator(auto_group_start);
        }
        int auto_group_end = java.util.Arrays.asList(opciones_menu.getMenuComponents()).indexOf(modo_auto_confirm_menu);
        if (auto_group_end >= 0 && (auto_group_end + 1 >= opciones_menu.getMenuComponentCount() || !(opciones_menu.getMenuComponent(auto_group_end + 1) instanceof javax.swing.JPopupMenu.Separator))) {
            opciones_menu.insertSeparator(auto_group_end + 1);
        }

        // "Detener la timba" y "Salir" juntos, sin separador entre ambos.
        file_menu.remove(jSeparator11);

        // "Límite de manos" sale del menú (vive en la pestaña Partida del diálogo
        // Ajustes); "Última mano" se mueve junto a Detener la timba / Salir, como la
        // PRIMERA de ese grupo. Ambos ítems siguen construidos (estado sincronizado).
        file_menu.remove(max_hands_menu);
        file_menu.remove(last_hand_menu);
        file_menu.remove(jSeparator3);
        int last_hand_target = java.util.Arrays.asList(file_menu.getMenuComponents()).indexOf(halt_game_menu);
        if (last_hand_target >= 0) {
            file_menu.insert(last_hand_menu, last_hand_target);
        } else {
            file_menu.add(last_hand_menu);
        }

        // El submenú "Apariencia" se construye (re-parenta sus ítems FUERA del
        // menú-bar, para que no se dupliquen) pero YA NO se muestra: todos los
        // ajustes de apariencia viven ahora en la pestaña "Apariencia" del diálogo
        // "Ajustes". Sus ítems siguen vivos para que la pestaña y el popup del tapete
        // deleguen en ellos vía doClick().

        menu_tapete_verde.setSelected(false);
        menu_tapete_azul.setSelected(false);
        menu_tapete_rojo.setSelected(false);
        menu_tapete_madera.setSelected(false);

        if (GameFrame.COLOR_TAPETE.startsWith("verde")) {

            menu_tapete_verde.setSelected(true);

            cambiarColorContadoresTapete(GameFrame.COLOR_TAPETE.endsWith("*") ? Color.WHITE : new Color(153, 204, 0));

        } else if (GameFrame.COLOR_TAPETE.startsWith("azul")) {

            menu_tapete_azul.setSelected(true);

            cambiarColorContadoresTapete(GameFrame.COLOR_TAPETE.endsWith("*") ? Color.WHITE : new Color(102, 204, 255));

        } else if (GameFrame.COLOR_TAPETE.startsWith("rojo")) {

            menu_tapete_rojo.setSelected(true);

            cambiarColorContadoresTapete(GameFrame.COLOR_TAPETE.endsWith("*") ? Color.WHITE : new Color(255, 204, 51));

        } else if (GameFrame.COLOR_TAPETE.startsWith("negro")) {

            menu_tapete_negro.setSelected(true);

            cambiarColorContadoresTapete(GameFrame.COLOR_TAPETE.endsWith("*") ? Color.WHITE : Color.LIGHT_GRAY);

        } else if (GameFrame.COLOR_TAPETE.startsWith("madera")) {

            menu_tapete_madera.setSelected(true);

            cambiarColorContadoresTapete(Color.WHITE);
        }

        if (!isPartida_local()) {
            tapete.getCommunityCards().getPause_button().setText(Translator.translate("game.pausar") + " (" + getLocalPlayer().getPause_counter() + ")");
        } else {
            tapete.getCommunityCards().getPause_button().setText(Translator.translate("game.pausar"));
        }

        full_screen_menu.setEnabled(true);

        updateSoundIcon();

        Helpers.resetBarra(tapete.getCommunityCards().getBarra_tiempo(), Crupier.TIEMPO_PENSAR);

        server_separator_menu.setVisible(partida_local);

        tapete.getCommunityCards().getTiempo_partida().setVisible(GameFrame.SHOW_CLOCK);

        time_menu.setSelected(GameFrame.SHOW_CLOCK);

        tapete.getLocalPlayer().getHoleCard1().setCompactable(false);
        tapete.getLocalPlayer().getHoleCard2().setCompactable(false);

        if (GameFrame.VISTA_COMPACTA != 2) {
            for (Card carta : this.getTapete().getCommunityCards().getCartasComunes()) {
                carta.setCompactable(false);
            }
        }

        //Metemos la pasta a todos (el BUY IN se podría parametrizar)
        // Issue#9: el campo buyin de RemotePlayer/LocalPlayer se inicializa con
        // un field initializer (= GameFrame.BUYIN) en el momento de instanciar
        // el slot, lo que puede capturar un valor stale en escenarios de hot-join
        // o recovery. Aqui — donde la mesa se inicializa con el BUYIN actual de
        // la partida (fuente de verdad) — seteamos tanto stack como buyin de cada
        // slot para que ambos reflejen el valor configurado. En RECOVER esto
        // queda machacado luego por recuperarDatosClavePartida para los jugadores
        // que tengan row de balance en SQL (preserva rebuys legitimos); los
        // late-joiners sin row mantienen el buyin que aqui se asigna.
        for (Player jugador : jugadores) {
            jugador.setStack(GameFrame.BUYIN);
            jugador.setBuyin(GameFrame.BUYIN);
        }

        // Cortinilla de apertura (Crupier.animateInitialStacks): si va a haber
        // conteo 0 -> buy-in, pintamos YA el label a 0 para que la mesa aparezca
        // con los asientos "vacios" y no se vea un fogonazo del buy-in completo
        // antes de que arranque la cuenta. El MODELO (stack) sigue siendo el buy-in
        // de arriba; esto es solo el label. Mismo gate que animateInitialStacks
        // (animaciones de contadores on + no recover) para que ambos vayan SIEMPRE
        // emparejados.
        if (GameFrame.contadoresAnimOn() && !GameFrame.RECOVER) {
            for (Player jugador : jugadores) {
                jugador.setStackDisplay(0f);
            }
        }

        // Initialize the debounce timer for mouse wheel zooming
        zoom_debounce_timer = new javax.swing.Timer(250, (java.awt.event.ActionEvent e) -> {
            applyAccumulatedZoom();
        });
        // VERY IMPORTANT: It must only fire once after the scrolling stops
        zoom_debounce_timer.setRepeats(false);

        setupGlobalShortcuts();

        Helpers.TapetePopupMenu.addTo(tapete, true);

        rebuy_now_menu.setEnabled(GameFrame.REBUY);

        Helpers.TapetePopupMenu.REBUY_NOW_MENU.setEnabled(GameFrame.REBUY);

        auto_rebuy_menu.setEnabled(GameFrame.REBUY);

        Helpers.TapetePopupMenu.AUTO_REBUY_MENU.setEnabled(GameFrame.REBUY);

        Helpers.TapetePopupMenu.AUTO_FULLSCREEN_MENU.setSelected(GameFrame.AUTO_FULLSCREEN);

        for (Component menu : BARAJAS_MENU.getMenuComponents()) {

            if (((javax.swing.JRadioButtonMenuItem) menu).getText().equals(GameFrame.BARAJA)) {
                ((javax.swing.JRadioButtonMenuItem) menu).setSelected(true);
            } else {
                ((javax.swing.JRadioButtonMenuItem) menu).setSelected(false);
            }
        }

        if (!partida_local) {
            halt_game_menu.setEnabled(false);
            Helpers.TapetePopupMenu.HALT_GAME_MENU.setEnabled(false);
            last_hand_menu.setEnabled(false);
            Helpers.TapetePopupMenu.LAST_HAND_MENU.setEnabled(false);
            max_hands_menu.setEnabled(false);
            Helpers.TapetePopupMenu.MAX_HANDS_MENU.setEnabled(false);
        }

        if (!menu_cinematicas.isEnabled()) {
            Helpers.TapetePopupMenu.CINEMATICAS_MENU.setEnabled(false);
            Helpers.TapetePopupMenu.CINEMATICAS_MENU.setSelected(false);
        }

        // Maestro de animaciones (Ajustes), ya con menu-bar Y popup construidos: si
        // "animaciones" se guardo en off, DESHABILITA los 5 toggles SIN desmarcarlos (el gate
        // lo aplican los helpers *On() en cada read-site; los flags *_PREF no se tocan). Con el
        // maestro on (por defecto) no cambia nada.
        applyAnimationMaster();

        addMouseWheelListener(this);

        Helpers.preserveOriginalFontSizes(THIS);

        Helpers.updateFonts(THIS, Helpers.GUI_FONT, null);

        tapete.getCommunityCards().getTiempo_partida().setFont(new Font("Monospaced", Font.BOLD, 28));

        Helpers.translateComponents(THIS, false);

        Helpers.translateComponents(Helpers.TapetePopupMenu.popup, false);

        // El rótulo de "AUTO igualar" lleva el estado actual (ACTIVADO/DESACTIVADO)
        // entre paréntesis; se fija aquí, tras la traducción, para que no lo pise.
        refreshAutoCallMenuText();

    }

    public JMenuItem getMax_hands_menu() {
        return max_hands_menu;
    }

    public long getConta_tiempo_juego() {
        return conta_tiempo_juego;
    }

    public GameLogDialog getRegistro_dialog() {
        return registro_dialog;
    }

    public HandGeneratorDialog getJugadas_dialog() {
        return jugadas_dialog;
    }

    public ShortcutsDialog getShortcuts_dialog() {
        return shortcuts_dialog;
    }

    public void finTransmision(boolean partida_terminada) {

        // Desregistrar el shutdown hook: la partida termina por la via
        // normal (host abort, fin natural, salida voluntaria) y el EXIT
        // que pudiera enviar el hook ya seria sobre un socket cerrado.
        unregisterShutdownHook();

        // Snapshot del auditor bajo lock_contabilidad ANTES de entrar al
        // SQL_LOCK para preservar el orden global lock_contabilidad → SQL_LOCK
        // (mismo orden que Crupier.run al cerrar una mano vía sqlUpdateHandEnd).
        // Sin el snapshot, anidar synchronized(lock_contabilidad) dentro del
        // SQL_LOCK invierte el orden y produce deadlock AB-BA con Crupier.run.
        HashMap<String, Double[]> auditor_snapshot = null;
        if (partida_terminada && crupier != null) {
            synchronized (crupier.getLock_contabilidad()) {
                // print=false: refrescamos el mapa del auditor para el snapshot SIN
                // volcar la tabla de stacks (NICK/STACK/BUYIN) al registro. Esa tabla
                // sale SOLO al arrancar cada mano; el cierre ya lo resume el marcador
                // final NICK/RESULTADO de más abajo. Imprimirla aquí (desde este hilo
                // de finTransmision) la metía además en medio de las acciones que el
                // hilo del Crupier seguía logueando.
                crupier.auditorCuentas(false);
                auditor_snapshot = new HashMap<>(crupier.getAuditor());
            }
        }

        synchronized (GameFrame.SQL_LOCK) {
            if (!fin) {

                fin = true;

                getCrupier().setFin_de_la_transmision(true);

                CoronaMP3FilePlayer tts_player = Audio.TTS_PLAYER;

                if (tts_player != null) {
                    try {
                        tts_player.stop();
                    } catch (Exception ex) {
                        Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

                Audio.stopAllWavResources();

                Audio.closeAllPreloadedWavs();

                Helpers.GUIRun(() -> {
                    GameFrame.getInstance().getTapete().hideALL();

                    GameFrame.getInstance().getTapete().getFastbuttons().setVisible(false);

                    if (getLocalPlayer().getAuto_action() != null) {
                        getLocalPlayer().getAuto_action().stop();
                    }

                    if (getLocalPlayer().getHurryup_timer() != null) {
                        getLocalPlayer().getHurryup_timer().stop();
                    }

                    // Stop GameFrame-owned Swing Timers so they don't keep
                    // firing on stale UI references after the frame is
                    // disposed. These live outside Helpers.THREAD_POOL and
                    // therefore survive SHUTDOWN_THREAD_POOL.
                    if (tiempo_juego != null) {
                        tiempo_juego.stop();
                    }

                    if (zoom_debounce_timer != null) {
                        zoom_debounce_timer.stop();
                    }

                    // Stop per-player Swing Timers on all remote players
                    // (LocalPlayer was already handled above). Same reason:
                    // Swing Timers are not in the thread pool.
                    for (Player p : jugadores) {
                        if (p instanceof RemotePlayer) {
                            RemotePlayer rp = (RemotePlayer) p;
                            rp.stopActionTimer();
                            if (rp.getIwtsth_blink_timer() != null) {
                                rp.getIwtsth_blink_timer().stop();
                            }
                            if (rp.getRebuy_countdown_timer() != null) {
                                rp.getRebuy_countdown_timer().stop();
                            }
                        }
                    }

                    if (jugadas_dialog != null) {
                        jugadas_dialog.setVisible(false);
                    }

                    if (shortcuts_dialog != null) {
                        shortcuts_dialog.setVisible(false);
                    }

                    if (registro_dialog.isVisible()) {
                        registro_dialog.setVisible(false);
                    }

                    if (pausa_dialog != null) {
                        pausa_dialog.setVisible(false);
                    }

                    if (GameFrame.getInstance().getFastchat_dialog() != null) {
                        GameFrame.getInstance().getFastchat_dialog().setVisible(false);
                    }

                    exit_menu.setEnabled(false);

                    menu_bar.setVisible(false);

                    setEnabled(false);
                });

                if (partida_terminada) {

                    getRegistro().print(Helpers.framedTitle(Translator.translate("game.la_timba_ha_terminado_2") + " -> " + Helpers.getFechaHoraActual() + " (" + Helpers.seconds2FullTime(conta_tiempo_juego) + ")"));

                    if (this.getCrupier().isForce_recover()) {
                        getRegistro().print(Helpers.framedTitleAlert(Translator.translate("game.el_server_ha_parado")));
                    }

                    try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement("UPDATE game SET end=? WHERE id=?")) {
                        statement.setQueryTimeout(30);
                        statement.setLong(1, System.currentTimeMillis());
                        statement.setLong(2, crupier.getSqlite_game_id());
                        statement.executeUpdate();
                    } catch (SQLException ex) {
                        Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex);
                    }

                    // Iteramos el snapshot tomado bajo lock_contabilidad FUERA
                    // del SQL_LOCK al inicio del método (ver comentario allí).
                    // Sin retomar el lock aquí no hay anidación SQL → CONTAB y
                    // por tanto no hay deadlock con Crupier.run.
                    if (auditor_snapshot != null) {

                        // Resultados finales en tabla con bordes (rejilla), mismo
                        // estilo que la tabla de cuentas: columnas NICK / RESULTADO.
                        // El token "(  )" deja el gutter del marcador en blanco (sin
                        // icono de rol) y alineado con la cabecera "(##)".
                        ArrayList<String[]> fin_rows = new ArrayList<>();

                        int fin_nick_w = "NICK".length();
                        int fin_res_w = Translator.translate("ui.resultado").length();

                        for (Map.Entry<String, Double[]> entry : auditor_snapshot.entrySet()) {

                            Double[] pasta = entry.getValue();

                            double ganancia = Helpers.doubleClean(Helpers.doubleClean(pasta[0]) - Helpers.doubleClean(pasta[1]));

                            String ganancia_msg;

                            if (Helpers.doubleSecureCompare(ganancia, 0f) < 0) {
                                ganancia_msg = Translator.translate("ui.pierde_2") + " " + Helpers.money2String(ganancia * -1);
                            } else if (Helpers.doubleSecureCompare(ganancia, 0f) > 0) {
                                ganancia_msg = Translator.translate("ui.gana_4") + " " + Helpers.money2String(ganancia);
                            } else {
                                ganancia_msg = Translator.translate("ui.ni_gana_ni_pierde");
                            }

                            fin_nick_w = Math.max(fin_nick_w, entry.getKey().length());
                            fin_res_w = Math.max(fin_res_w, ganancia_msg.length());

                            fin_rows.add(new String[]{entry.getKey(), ganancia_msg});
                        }

                        int[] fin_cols = {fin_nick_w, fin_res_w};

                        StringBuilder fin_table = new StringBuilder("(##) ").append(Crupier.gridBorderLine('┌', '┬', '┐', fin_cols))
                                .append("\n(##) ").append(Crupier.gridRowLine(
                                        String.format("%-" + fin_nick_w + "s", "NICK"),
                                        String.format("%-" + fin_res_w + "s", Translator.translate("ui.resultado"))))
                                .append("\n(##) ").append(Crupier.gridBorderLine('├', '┼', '┤', fin_cols));

                        for (String[] r : fin_rows) {
                            fin_table.append("\n(  ) ").append(Crupier.gridRowLine(
                                    String.format("%-" + fin_nick_w + "s", r[0]),
                                    String.format("%-" + fin_res_w + "s", r[1])));
                        }

                        fin_table.append("\n(##) ").append(Crupier.gridBorderLine('└', '┴', '┘', fin_cols));

                        getRegistro().print(fin_table.toString());

                        getRegistro().setFin_transmision(true);
                    }

                }

                Timestamp ts = new Timestamp(GAME_START_TIMESTAMP);
                DateFormat timeZoneFormat = new SimpleDateFormat("dd_MM_yyyy__HH_mm_ss");
                Date date = new Date(ts.getTime());
                String fecha = timeZoneFormat.format(date);
                // Sprint deferred 🟠-24: nick saneado para uso como segmento de
                // filename. Antes solo se reemplazaba el espacio; nicks como CON,
                // NUL, AUX o con caracteres :/*? rompían FileOutputStream silenciosamente
                // y el log de la timba se perdía. Reader (StatsDialog) usa el mismo
                // saneo para encontrar el fichero — coordinación crítica.
                String log_file = Init.LOGS_DIR + "/CORONAPOKER_TIMBA_" + Helpers.safeNickForFilename(sala_espera.getServer_nick()) + "_" + fecha + ".log";

                // Drenamos la cola del registro: print() es asincrono (LOG_POOL), asi
                // que el footer + el marcador final recien encolados pueden no estar
                // todavia en LOG_TEXT cuando getText() construye el .log. logFlush espera
                // a que se apliquen para que el fichero quede completo y en orden.
                Helpers.logFlush();

                try {

                    String previous_log_data = "";

                    if (Files.exists(Paths.get(log_file))) {

                        previous_log_data = "\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>" + log_file + "\n" + Files.readString(Paths.get(log_file)) + "\n<<<<<<<<<<<<<<<<<<<<<<<<<<<<" + log_file + "\n";
                        Files.writeString(Paths.get(log_file), previous_log_data + getRegistro().getText(), StandardOpenOption.TRUNCATE_EXISTING);
                    } else {
                        Files.writeString(Paths.get(log_file), getRegistro().getText());
                    }

                } catch (IOException ex1) {
                    Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex1);
                }

                if (!this.getSala_espera().getChat_text().toString().isEmpty()) {

                    // Sprint deferred 🟠-24: nick saneado igual que log_file arriba.
                    String chat_file = Init.LOGS_DIR + "/CORONAPOKER_CHAT_" + Helpers.safeNickForFilename(sala_espera.getServer_nick()) + "_" + fecha + ".html";

                    try {

                        String previous_chat_data = "";

                        final String chat_html_head = "<head><style>"
                                + ".bubble-mine,.bubble-other{padding:5px;border-radius:12px;}"
                                + ".bubble-mine{background-color:#d9fdd3;}"
                                + ".bubble-other{background-color:white;}"
                                + "</style></head>";

                        if (Files.exists(Paths.get(chat_file))) {

                            previous_chat_data = Files.readString(Paths.get(chat_file)).replaceAll("<html>(?:<head>.*?</head>)?<body.*?>(.*?)</body></html>", "$1");
                            Files.writeString(Paths.get(chat_file), "<html>" + chat_html_head + "<body style='background-image: url(" + this.sala_espera.getBackground_chat_src() + ")'>" + previous_chat_data + this.sala_espera.txtChat2HTML(this.sala_espera.getChat_text().toString()) + "</body></html>", StandardOpenOption.TRUNCATE_EXISTING);

                        } else {
                            Files.writeString(Paths.get(chat_file), "<html>" + chat_html_head + "<body style='background-image: url(" + this.sala_espera.getBackground_chat_src() + ")'>" + this.sala_espera.txtChat2HTML(this.sala_espera.getChat_text().toString()) + "</body></html>");

                        }

                    } catch (IOException ex1) {
                        Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex1);
                    }
                }

                if (partida_terminada) {

                    WaitingRoomFrame.getInstance().setExit(true);

                    if (WaitingRoomFrame.getInstance().isServer()) {
                        WaitingRoomFrame.getInstance().closeServerSocket();
                    } else {
                        WaitingRoomFrame.getInstance().closeClientSocket();
                    }

                    if (isPartida_local() && getSala_espera().isUpnp()) {
                        Helpers.UPnPClose(getSala_espera().getServer_port());
                    }

                    recover = getCrupier().isForce_recover();

                    if (!recover) {
                        Helpers.GUIRunAndWait(() -> {
                            BalanceDialog balance = new BalanceDialog(GameFrame.getInstance(), true);

                            balance.setLocationRelativeTo(balance.getParent());

                            balance.setVisible(true);

                            recover = balance.isRecover();
                        });
                    } else if (!isPartida_local()) {
                        Helpers.GUIRun(() -> {
                            InGameNotifyDialog dialog = new InGameNotifyDialog(GameFrame.getInstance(), false, Translator.translate("conn.el_servidor_ha_detenido_la"), Color.WHITE, Color.BLACK, getClass().getResource("/images/stop.png"), HALT_PAUSE, true);
                            dialog.setLocationRelativeTo(dialog.getParent());
                            dialog.setVisible(true);
                        });

                        Helpers.pausar(HALT_PAUSE);
                    }
                }

                Helpers.SQLITEVAC();

                Helpers.closeSQLITE();

                Helpers.cleanGifsicleFiles();

                KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();

                if (GameFrame.key_event_dispatcher != null) {
                    kfm.removeKeyEventDispatcher(GameFrame.key_event_dispatcher);
                    GameFrame.key_event_dispatcher = null;
                }

                RESET_GAME(recover);
            }

        }

    }

    private void RESET_GAME(boolean recover) {

        // Monitor donde estaba el tablero (y la pantalla final): la ventana de
        // inicio se creo maximizada en el primario y solo se oculta entre
        // timbas, asi que sin esto reaparece en el primario aunque la partida
        // estuviera en un monitor secundario. Se captura ANTES del resetInstance.
        final java.awt.GraphicsConfiguration return_screen = this.getGraphicsConfiguration();

        new Thread(() -> {

            boolean local = GameFrame.getInstance().isPartida_local();

            if (GameFrame.getInstance().isPartida_local()) {
                GameFrame.IWTSTH_RULE_RECOVER = recover ? GameFrame.IWTSTH_RULE : null;
                GameFrame.RABBIT_HUNTING_RECOVER = recover ? GameFrame.RABBIT_HUNTING : null;
                GameFrame.RUN_IT_TWICE_RECOVER = recover ? GameFrame.RUN_IT_TWICE : null;
                GameFrame.VOICE_MESSAGES_RECOVER = recover ? GameFrame.VOICE_MESSAGES : null;
                GameFrame.TTS_SERVER_RECOVER = recover ? GameFrame.TTS_SERVER : null;
            }

            GameFrame.PASSWORD_RECOVER = recover ? WaitingRoomFrame.getInstance().getPassword() : null;

            Audio.stopAllCurrentLoopMp3Resource();

            Audio.stopAllWavResources();

            Audio.closeAllPreloadedWavs();

            // SHUTDOWN antes de resetLOG (no al reves): el shutdownNow() descarta las
            // tareas de log encoladas en LOG_POOL ANTES de vaciar LOG_TEXT, asi ninguna
            // straggler de la timba anterior puede hacer un append fantasma sobre el log
            // ya reseteado de la siguiente. logFlush() en finTransmision ya drena la cola
            // mucho antes; esto es defensa en profundidad (resetLOG es solo una asignacion
            // de String, no usa el pool, asi que moverla detras del shutdown es inocuo).
            Helpers.SHUTDOWN_THREAD_POOL();

            GameLogDialog.resetLOG();

            //Reiniciamos
            Helpers.GUIRunAndWait(() -> {
                WaitingRoomFrame.resetInstance();
                GameFrame.resetInstance();
            });

            Helpers.CREATE_THREAD_POOL();

            // Re-submit the deadlock detector to the fresh pool — the previous
            // instance died with the old pool's shutdownNow.
            Init.startDeadlockDetector();

            if (!GameFrame.SONIDOS) {

                Audio.muteAll();

            } else {

                Audio.unmuteAll();

            }

            Audio.playLoopMp3Resource("misc/background_music.mp3");

            Helpers.GUIRunAndWait(() -> {
                Init.VENTANA_INICIO.getTapete().refresh();
                Helpers.showFrameOnScreen(Init.VENTANA_INICIO, return_screen);

                if (recover) {
                    Init.VENTANA_INICIO.setEnabled(false);
                    Init.VENTANA_INICIO.continueLastGame(local);
                }
            });

        }).start();
    }

    public Timer getTiempo_juego() {
        return tiempo_juego;
    }

    public void AJUGAR() {

        Helpers.GUIRunAndWait(() -> {
            registro_dialog = new GameLogDialog(this, false);
        });

        TTSWatchdog();

        // Telemetría: broadcaster periódico server-side (1 thread).
        // Solo activo en el host (isPartida_local). Loop sale al final de la
        // transmisión (mismo signal que TTSWatchdog y el resto de threads
        // del Crupier — SHUTDOWN_THREAD_POOL al cerrar el juego también
        // los corta de raíz). Best-effort: cualquier fallo se loguea
        // pero NO afecta al game flow.
        if (isPartida_local()) {
            telemetryBroadcasterWatchdog();
        }

        Helpers.threadRun(crupier);

        // javax.swing.Timer already executes in the EDT. Removed redundant GUIRun context switch.
        tiempo_juego = new Timer(1000, (ActionEvent ae) -> {
            if (!crupier.isFin_de_la_transmision() && !isTimba_pausada()) {
                String tiempo_juego1 = Helpers.seconds2FullTime(++conta_tiempo_juego);
                tapete.getCommunityCards().getTiempo_partida().setText(tiempo_juego1);
            } else {
                tapete.getCommunityCards().getTiempo_partida().setText("--:--:--");
            }
        });

        tiempo_juego.start();

        getRegistro().print(Translator.translate("game.comienza_la_timba") + " " + Helpers.getFechaHoraActual());
    }

    /**
     * Telemetría: thread server-side que dispara
     * Crupier.broadcastTelemetryFrame() cada PING_INTERVAL_MS para que los
     * clientes mantengan su latest_telemetry fresco.
     *
     * Cycle:
     *   1. pausar PING_INTERVAL_MS al inicio (los datos de latency necesitan
     *      al menos UNA ronda de ping/pong antes de tener algo que reportar).
     *   2. broadcast.
     *   3. loop hasta crupier.isFin_de_la_transmision().
     *
     * El thread vive en Helpers.THREAD_POOL — al cerrar el juego,
     * SHUTDOWN_THREAD_POOL lo corta junto con TTSWatchdog y los demás.
     * Si broadcast lanza, log + continuar (telemetría es best-effort,
     * no debe abortar la cadena).
     */
    private void telemetryBroadcasterWatchdog() {
        Helpers.threadRun(() -> {
            while (crupier != null && !crupier.isFin_de_la_transmision()) {
                try {
                    Helpers.pausar(WaitingRoomFrame.PING_INTERVAL_MS);
                    if (crupier != null && !crupier.isFin_de_la_transmision()) {
                        crupier.broadcastTelemetryFrame();
                    }
                } catch (Exception ex) {
                    Logger.getLogger(GameFrame.class.getName()).log(
                            Level.WARNING,
                            "TelemetryBroadcasterWatchdog iteration failed (telemetry is best-effort)",
                            ex);
                }
            }
        });
    }

    private void TTSWatchdog() {

        Helpers.threadRun(new Runnable() {
            private volatile boolean temp_notify_blocked;

            @Override
            public void run() {

                while (!crupier.isFin_de_la_transmision()) {

                    while (!GameFrame.NOTIFY_CHAT_QUEUE.isEmpty()) {

                        Object[] tts = GameFrame.NOTIFY_CHAT_QUEUE.poll();

                        String nick = (String) tts[0];

                        Player jugador = GameFrame.getInstance().getCrupier().getNick2player().get(nick);

                        if (jugador != null) {
                            if (tts[1] instanceof URL) {

                                if (GameFrame.CHAT_IMAGES_INGAME) {
                                    jugador.setNotifyImageChatLabel((URL) tts[1]);
                                }

                            } else if (tts[1] instanceof byte[]) {

                                temp_notify_blocked = (GameFrame.getInstance().getLocalPlayer() != jugador && ((RemotePlayer) jugador).isNotify_blocked());

                                // Muted or blocked sender: nothing at all (no dialog,
                                // no avatar emoji) — the chat line is the notification
                                if (GameFrame.SONIDOS && !temp_notify_blocked) {

                                    jugador.setNotifyTTSChatLabel();

                                    Audio.playVoiceMessage((byte[]) tts[1], jugador.getChat_notify_label());
                                }

                            } else {

                                temp_notify_blocked = (GameFrame.getInstance().getLocalPlayer() != jugador && ((RemotePlayer) jugador).isNotify_blocked());

                                jugador.setNotifyTTSChatLabel();

                                if (GameFrame.SONIDOS && GameFrame.TTS_SERVER && !AudioDeviceManager.isBlockTtsLocal() && !temp_notify_blocked) {
                                    Audio.TTS((String) tts[1], jugador.getChat_notify_label());
                                } else {

                                    Helpers.GUIRun(() -> {
                                        if (temp_notify_blocked) {
                                            notify_dialog = new InGameNotifyDialog(GameFrame.getInstance(), false, "[" + nick + "]: " + WaitingRoomFrame.getInstance().cleanTTSChatMessage((String) tts[1]), Color.YELLOW, Color.BLACK, getClass().getResource("/images/sound_b.png"), null);
                                        } else {
                                            notify_dialog = new InGameNotifyDialog(GameFrame.getInstance(), false, "[" + nick + "]: " + WaitingRoomFrame.getInstance().cleanTTSChatMessage((String) tts[1]), Color.RED, Color.WHITE, getClass().getResource("/images/mute.png"), null);
                                        }

                                        notify_dialog.setLocation(notify_dialog.getParent().getLocation());

                                        notify_dialog.setVisible(true);
                                    });

                                    Helpers.pausar(Math.max((long) Math.ceil((double) WaitingRoomFrame.getInstance().cleanTTSChatMessage((String) tts[1]).length() / 25) * 1000, TTS_NO_SOUND_TIMEOUT));

                                    Helpers.GUIRun(() -> {
                                        // Dispose + null antes de soltar la referencia: el
                                        // setVisible(false) anterior NO libera el peer nativo del
                                        // dialog ni nada más. Sin esto, las notificaciones TTS
                                        // acumulaban dialogs zombi en partidas largas (🟠-22 v2).
                                        if (notify_dialog != null) {
                                            notify_dialog.setVisible(false);
                                            notify_dialog.dispose();
                                            notify_dialog = null;
                                        }
                                    });

                                }

                            }

                        }

                    }

                    synchronized (GameFrame.NOTIFY_CHAT_QUEUE) {

                        // Re-check inside the monitor before parking: a producer
                        // that enqueued + notified between the drain loop above and
                        // this synchronized block would otherwise have its notify
                        // lost, delaying the message up to the full timeout.
                        if (GameFrame.NOTIFY_CHAT_QUEUE.isEmpty() && !crupier.isFin_de_la_transmision()) {
                            try {
                                GameFrame.NOTIFY_CHAT_QUEUE.wait(1000);
                            } catch (InterruptedException ex) {
                                Thread.currentThread().interrupt();
                                // Expected during pool shutdown — TTS watchdog
                                // task is being cancelled cooperatively. Bail
                                // out of the outer while loop so we don't spin
                                // re-entering wait() with the interrupt flag.
                                Logger.getLogger(GameFrame.class.getName()).log(Level.INFO,
                                        "TTS watchdog wait interrupted (cooperative cancellation)");
                                return;
                            }
                        }
                    }

                }
            }
        });

    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        menu_bar = new javax.swing.JMenuBar();
        file_menu = new javax.swing.JMenu();
        chat_menu = new javax.swing.JMenuItem();
        registro_menu = new javax.swing.JMenuItem();
        jugadas_menu = new javax.swing.JMenuItem();
        server_separator_menu = new javax.swing.JPopupMenu.Separator();
        last_hand_menu = new javax.swing.JCheckBoxMenuItem();
        max_hands_menu = new javax.swing.JMenuItem();
        jSeparator3 = new javax.swing.JPopupMenu.Separator();
        force_reconnect_menu = new javax.swing.JMenuItem();
        jSeparator9 = new javax.swing.JPopupMenu.Separator();
        halt_game_menu = new javax.swing.JMenuItem();
        jSeparator11 = new javax.swing.JPopupMenu.Separator();
        exit_menu = new javax.swing.JMenuItem();
        zoom_menu = new javax.swing.JMenu();
        zoom_menu_in = new javax.swing.JMenuItem();
        zoom_menu_out = new javax.swing.JMenuItem();
        zoom_menu_reset = new javax.swing.JMenuItem();
        auto_fit_zoom_menu = new javax.swing.JCheckBoxMenuItem();
        jSeparator6 = new javax.swing.JPopupMenu.Separator();
        compact_menu = new javax.swing.JCheckBoxMenuItem();
        jSeparator5 = new javax.swing.JPopupMenu.Separator();
        auto_fullscreen_menu = new javax.swing.JCheckBoxMenuItem();
        full_screen_menu = new javax.swing.JMenuItem();
        opciones_menu = new javax.swing.JMenu();
        jSeparator1 = new javax.swing.JPopupMenu.Separator();
        confirmar_menu = new javax.swing.JCheckBoxMenuItem();
        auto_action_menu = new javax.swing.JCheckBoxMenuItem();
        jSeparator7 = new javax.swing.JPopupMenu.Separator();
        menu_cinematicas = new javax.swing.JCheckBoxMenuItem();
        chat_image_menu = new javax.swing.JCheckBoxMenuItem();
        jSeparator8 = new javax.swing.JPopupMenu.Separator();
        time_menu = new javax.swing.JCheckBoxMenuItem();
        decks_separator = new javax.swing.JPopupMenu.Separator();
        menu_barajas = new javax.swing.JMenu();
        menu_tapetes = new javax.swing.JMenu();
        menu_tapete_verde = new javax.swing.JRadioButtonMenuItem();
        menu_tapete_azul = new javax.swing.JRadioButtonMenuItem();
        menu_tapete_rojo = new javax.swing.JRadioButtonMenuItem();
        menu_tapete_negro = new javax.swing.JRadioButtonMenuItem();
        menu_tapete_madera = new javax.swing.JRadioButtonMenuItem();
        jSeparator4 = new javax.swing.JPopupMenu.Separator();
        rebuy_now_menu = new javax.swing.JCheckBoxMenuItem();
        jSeparator2 = new javax.swing.JPopupMenu.Separator();
        jSeparator10 = new javax.swing.JPopupMenu.Separator();
        help_menu = new javax.swing.JMenu();
        shortcuts_menu = new javax.swing.JMenuItem();
        robert_rules_menu = new javax.swing.JMenuItem();
        acerca_menu = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle("CoronaPoker");
        setIconImage(new javax.swing.ImageIcon(getClass().getResource("/images/avatar_default.png")).getImage());
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        menu_bar.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N

        file_menu.setMnemonic('i');
        file_menu.setText("Archivo");
        file_menu.putClientProperty("i18n.key", "menu.archivo");
        file_menu.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        file_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N

        chat_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        chat_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/chat.png"))); // NOI18N
        chat_menu.setText("Ver chat (ALT+C)");
        chat_menu.putClientProperty("i18n.key", "menu.ver_chat");
        chat_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chat_menuActionPerformed(evt);
            }
        });
        file_menu.add(chat_menu);

        registro_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        registro_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/log.png"))); // NOI18N
        registro_menu.setText("Ver registro (ALT+R)");
        registro_menu.putClientProperty("i18n.key", "menu.ver_registro");
        registro_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                registro_menuActionPerformed(evt);
            }
        });
        file_menu.add(registro_menu);

        jugadas_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        jugadas_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/games.png"))); // NOI18N
        jugadas_menu.setText("Generador de jugadas");
        jugadas_menu.putClientProperty("i18n.key", "menu.generador_de_jugadas");
        jugadas_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jugadas_menuActionPerformed(evt);
            }
        });
        file_menu.add(jugadas_menu);
        file_menu.add(server_separator_menu);

        last_hand_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        last_hand_menu.setSelected(true);
        last_hand_menu.setText("Última mano");
        last_hand_menu.putClientProperty("i18n.key", "menu.ultima_mano");
        last_hand_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/last_hand.png"))); // NOI18N
        last_hand_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                last_hand_menuActionPerformed(evt);
            }
        });
        file_menu.add(last_hand_menu);

        max_hands_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        max_hands_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/meter.png"))); // NOI18N
        max_hands_menu.setText("Límite de manos");
        max_hands_menu.putClientProperty("i18n.key", "menu.limite_de_manos");
        max_hands_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                max_hands_menuActionPerformed(evt);
            }
        });
        file_menu.add(max_hands_menu);
        file_menu.add(jSeparator3);

        force_reconnect_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        force_reconnect_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/timeout.png"))); // NOI18N
        force_reconnect_menu.setText("FORZAR RECONEXIÓN JUGADORES");
        force_reconnect_menu.putClientProperty("i18n.key", "menu.forzar_reconexion_jugadores");
        force_reconnect_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                force_reconnect_menuActionPerformed(evt);
            }
        });
        file_menu.add(force_reconnect_menu);
        file_menu.add(jSeparator9);

        halt_game_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        halt_game_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/stop.png"))); // NOI18N
        halt_game_menu.setText("DETENER LA TIMBA (ALT+H)");
        halt_game_menu.putClientProperty("i18n.key", "menu.detener_la_timba");
        halt_game_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                halt_game_menuActionPerformed(evt);
            }
        });
        file_menu.add(halt_game_menu);
        file_menu.add(jSeparator11);

        exit_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        exit_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/close.png"))); // NOI18N
        exit_menu.setText("SALIR (ALT+F4)");
        exit_menu.putClientProperty("i18n.key", "menu.salir");
        exit_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exit_menuActionPerformed(evt);
            }
        });
        file_menu.add(exit_menu);

        menu_bar.add(file_menu);

        zoom_menu.setText("Zoom");
        zoom_menu.putClientProperty("i18n.key", "menu.zoom");
        zoom_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N

        zoom_menu_in.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        zoom_menu_in.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/zoom_in.png"))); // NOI18N
        zoom_menu_in.setText("Aumentar (CTRL++)");
        zoom_menu_in.putClientProperty("i18n.key", "menu.aumentar");
        zoom_menu_in.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zoom_menu_inActionPerformed(evt);
            }
        });
        zoom_menu.add(zoom_menu_in);

        zoom_menu_out.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        zoom_menu_out.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/zoom_out.png"))); // NOI18N
        zoom_menu_out.setText("Reducir (CTRL+-)");
        zoom_menu_out.putClientProperty("i18n.key", "menu.reducir");
        zoom_menu_out.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zoom_menu_outActionPerformed(evt);
            }
        });
        zoom_menu.add(zoom_menu_out);

        zoom_menu_reset.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        zoom_menu_reset.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/zoom_reset.png"))); // NOI18N
        zoom_menu_reset.setText("Reset (CTRL+0)");
        zoom_menu_reset.putClientProperty("i18n.key", "menu.reset");
        zoom_menu_reset.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zoom_menu_resetActionPerformed(evt);
            }
        });
        zoom_menu.add(zoom_menu_reset);

        auto_fit_zoom_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        auto_fit_zoom_menu.setSelected(true);
        auto_fit_zoom_menu.setText("Auto ajustar");
        auto_fit_zoom_menu.putClientProperty("i18n.key", "menu.auto_ajustar");
        auto_fit_zoom_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/zoom_auto.png"))); // NOI18N
        auto_fit_zoom_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                auto_fit_zoom_menuActionPerformed(evt);
            }
        });
        zoom_menu.add(auto_fit_zoom_menu);

        zoom_menu.add(jSeparator6);

        compact_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        compact_menu.setSelected(true);
        compact_menu.setText("VISTA COMPACTA (ALT+X)");
        compact_menu.putClientProperty("i18n.key", "menu.vista_compacta");
        compact_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/tiny.png"))); // NOI18N
        compact_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                compact_menuActionPerformed(evt);
            }
        });
        zoom_menu.add(compact_menu);

        zoom_menu.add(jSeparator5);

        auto_fullscreen_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        auto_fullscreen_menu.setSelected(true);
        auto_fullscreen_menu.setText("Activar pantalla completa al empezar");
        auto_fullscreen_menu.putClientProperty("i18n.key", "menu.activar_pantalla_completa_al_empezar");
        auto_fullscreen_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/full_screen_auto.png"))); // NOI18N
        auto_fullscreen_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                auto_fullscreen_menuActionPerformed(evt);
            }
        });
        zoom_menu.add(auto_fullscreen_menu);

        full_screen_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        full_screen_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/full_screen.png"))); // NOI18N
        full_screen_menu.setText("PANTALLA COMPLETA (ALT+F)");
        full_screen_menu.putClientProperty("i18n.key", "menu.pantalla_completa");
        full_screen_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                full_screen_menuActionPerformed(evt);
            }
        });
        zoom_menu.add(full_screen_menu);

        menu_bar.add(zoom_menu);

        opciones_menu.setText("Preferencias");
        opciones_menu.putClientProperty("i18n.key", "menu.preferencias");
        opciones_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N


        opciones_menu.add(jSeparator1);

        confirmar_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        confirmar_menu.setSelected(true);
        confirmar_menu.setText("Confirmar todas las acciones");
        confirmar_menu.putClientProperty("i18n.key", "menu.confirmar_todas_las_acciones");
        confirmar_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/confirmation.png"))); // NOI18N
        confirmar_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                confirmar_menuActionPerformed(evt);
            }
        });
        opciones_menu.add(confirmar_menu);

        auto_action_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        auto_action_menu.setSelected(true);
        auto_action_menu.setText("Modo AUTO");
        auto_action_menu.putClientProperty("i18n.key", "menu.botones_auto");
        auto_action_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/auto.png"))); // NOI18N
        auto_action_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                auto_action_menuActionPerformed(evt);
            }
        });
        opciones_menu.add(auto_action_menu);

        opciones_menu.add(jSeparator7);

        menu_cinematicas.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        menu_cinematicas.setSelected(true);
        menu_cinematicas.setText("Cinemáticas");
        menu_cinematicas.putClientProperty("i18n.key", "menu.cinematicas");
        menu_cinematicas.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/video.png"))); // NOI18N
        menu_cinematicas.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menu_cinematicasActionPerformed(evt);
            }
        });
        opciones_menu.add(menu_cinematicas);

        chat_image_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        chat_image_menu.setSelected(true);
        chat_image_menu.setText("Imágenes del chat en el juego");
        chat_image_menu.putClientProperty("i18n.key", "menu.imagenes_del_chat_en_el_juego");
        chat_image_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/chat_image.png"))); // NOI18N
        chat_image_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chat_image_menuActionPerformed(evt);
            }
        });
        opciones_menu.add(chat_image_menu);

        opciones_menu.add(jSeparator8);

        time_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        time_menu.setSelected(true);
        time_menu.setText("Mostrar reloj (ALT+W)");
        time_menu.putClientProperty("i18n.key", "menu.mostrar_reloj");
        time_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/clock.png"))); // NOI18N
        time_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                time_menuActionPerformed(evt);
            }
        });
        opciones_menu.add(time_menu);

        opciones_menu.add(decks_separator);

        menu_barajas.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/baraja.png"))); // NOI18N
        menu_barajas.setText("Barajas");
        menu_barajas.putClientProperty("i18n.key", "menu.barajas");
        menu_barajas.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        opciones_menu.add(menu_barajas);

        menu_tapetes.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/tapetes.png"))); // NOI18N
        menu_tapetes.setText("Tapetes");
        menu_tapetes.putClientProperty("i18n.key", "menu.tapetes");
        menu_tapetes.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N

        menu_tapete_verde.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        menu_tapete_verde.setSelected(true);
        menu_tapete_verde.setText("Verde");
        menu_tapete_verde.putClientProperty("i18n.key", "menu.verde");
        menu_tapete_verde.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menu_tapete_verdeActionPerformed(evt);
            }
        });
        menu_tapetes.add(menu_tapete_verde);

        menu_tapete_azul.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        menu_tapete_azul.setSelected(true);
        menu_tapete_azul.setText("Azul");
        menu_tapete_azul.putClientProperty("i18n.key", "menu.azul");
        menu_tapete_azul.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menu_tapete_azulActionPerformed(evt);
            }
        });
        menu_tapetes.add(menu_tapete_azul);

        menu_tapete_rojo.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        menu_tapete_rojo.setSelected(true);
        menu_tapete_rojo.setText("Rojo");
        menu_tapete_rojo.putClientProperty("i18n.key", "menu.rojo");
        menu_tapete_rojo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menu_tapete_rojoActionPerformed(evt);
            }
        });
        menu_tapetes.add(menu_tapete_rojo);

        menu_tapete_negro.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        menu_tapete_negro.setSelected(true);
        menu_tapete_negro.setText("Negro");
        menu_tapete_negro.putClientProperty("i18n.key", "menu.negro");
        menu_tapete_negro.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menu_tapete_negroActionPerformed(evt);
            }
        });
        menu_tapetes.add(menu_tapete_negro);

        menu_tapete_madera.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        menu_tapete_madera.setSelected(true);
        menu_tapete_madera.setText("Sin tapete");
        menu_tapete_madera.putClientProperty("i18n.key", "menu.sin_tapete");
        menu_tapete_madera.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menu_tapete_maderaActionPerformed(evt);
            }
        });
        menu_tapetes.add(menu_tapete_madera);

        opciones_menu.add(menu_tapetes);

        opciones_menu.add(jSeparator4);

        rebuy_now_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        rebuy_now_menu.setSelected(true);
        rebuy_now_menu.setText("RECOMPRAR (siguiente mano)");
        rebuy_now_menu.putClientProperty("i18n.key", "menu.recomprar_siguiente_mano");
        rebuy_now_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/rebuy.png"))); // NOI18N
        rebuy_now_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rebuy_now_menuActionPerformed(evt);
            }
        });
        opciones_menu.add(rebuy_now_menu);
        opciones_menu.add(jSeparator2);
        opciones_menu.add(jSeparator10);

        menu_bar.add(opciones_menu);

        help_menu.setText("Ayuda");
        help_menu.putClientProperty("i18n.key", "menu.ayuda");
        help_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N

        shortcuts_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        shortcuts_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/keyboard.png"))); // NOI18N
        shortcuts_menu.setText("Ver atajos");
        shortcuts_menu.putClientProperty("i18n.key", "menu.ver_atajos");
        shortcuts_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                shortcuts_menuActionPerformed(evt);
            }
        });
        help_menu.add(shortcuts_menu);

        robert_rules_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        robert_rules_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/book.png"))); // NOI18N
        robert_rules_menu.setText("Reglas de Robert");
        robert_rules_menu.putClientProperty("i18n.key", "menu.reglas_de_robert");
        robert_rules_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                robert_rules_menuActionPerformed(evt);
            }
        });
        help_menu.add(robert_rules_menu);

        acerca_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        acerca_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/corona.png"))); // NOI18N
        acerca_menu.setText("Acerca de");
        acerca_menu.putClientProperty("i18n.key", "menu.acerca_de");
        acerca_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                acerca_menuActionPerformed(evt);
            }
        });
        help_menu.add(acerca_menu);

        menu_bar.add(help_menu);

        setJMenuBar(menu_bar);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void exit_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exit_menuActionPerformed
        // TODO add your handling code here:

        if (getLocalPlayer().isExit() && Helpers.mostrarMensajeInformativoSINO(this, Translator.translate("ui.forzar_cierre"), new ImageIcon(Init.class.getResource("/images/exit.png"))) == 0) {

            System.exit(1);
        }

        if (this.isPartida_local()) {

            if (jugadores.size() > 1) {

                ExitDialog exit_dialog = new ExitDialog(this, true, Translator.translate("exit.salir_de_la_timba_pregunta"));
                exit_dialog.setLocationRelativeTo(this);
                exit_dialog.setVisible(true);

                // 0=yes, 1=no, 2=cancel
                if (exit_dialog.isExit()) {

                    if (exit_dialog.getProgramar_parada_checkbox().isSelected()) {
                        GameFrame.getInstance().getLast_hand_menu().doClick();
                    } else {

                        getLocalPlayer().setExit();

                        Helpers.threadRun(() -> {
                            try {
                                //Hay que avisar a los clientes de que la timba ha terminado
                                crupier.broadcastGAMECommandFromServer(getCrupier().isForce_recover() ? "SERVEREXITRECOVER" + (WaitingRoomFrame.getInstance().getPassword() != null ? "#" + Base64.getEncoder().encodeToString(WaitingRoomFrame.getInstance().getPassword().getBytes("UTF-8")) : "") : "SERVEREXIT", null, false);
                            } catch (UnsupportedEncodingException ex) {
                                Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex);
                            }

                            finTransmision(true);
                        });
                    }

                } else {
                    getCrupier().setForce_recover(false);
                }

            } else {

                Helpers.threadRun(() -> {
                    getLocalPlayer().setExit();

                    finTransmision(true);
                });
            }

        } else {

            ExitDialog exit_dialog = new ExitDialog(this, true, Translator.translate("exit.salir_de_la_timba_pregunta"));
            exit_dialog.setLocationRelativeTo(this);
            exit_dialog.setVisible(true);

            // 0=yes, 1=no, 2=cancel
            if (exit_dialog.isExit()) {

                getLocalPlayer().setExit();

                Helpers.threadRun(() -> {
                    if (!getSala_espera().isReconnecting()) {
                        crupier.sendGAMECommandToServer("EXIT", false);
                    }

                    finTransmision(false);
                });

            }
        }

    }//GEN-LAST:event_exit_menuActionPerformed

    private void acerca_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_acerca_menuActionPerformed
        // TODO add your handling code here:
        this.about_dialog = new AboutDialog(this, true);

        this.about_dialog.setLocationRelativeTo(about_dialog.getParent());

        this.about_dialog.setVisible(true);
    }//GEN-LAST:event_acerca_menuActionPerformed

    private void zoom_menu_inActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoom_menu_inActionPerformed
        // TODO add your handling code here:

        Audio.playWavResource("misc/zoom_in.wav");

        Helpers.threadRun(() -> {
            incrementZoom();
            Helpers.PROPERTIES.setProperty("zoom_level", String.valueOf(ZOOM_LEVEL));
            Card.updateCachedImages(1f + ZOOM_LEVEL * ZOOM_STEP, false);
            zoom(1f + ZOOM_LEVEL * ZOOM_STEP, null);
            if (jugadas_dialog != null && jugadas_dialog.isVisible()) {
                for (Card carta : jugadas_dialog.getCartas()) {
                    carta.invalidateImagePrecache();
                    carta.refreshCard();
                }
                Helpers.GUIRun(jugadas_dialog::pack);
            }
            if (shortcuts_dialog != null && shortcuts_dialog.isVisible()) {

                shortcuts_dialog.zoom(1f + ZOOM_LEVEL * ZOOM_STEP, null);

            }
            if (GameFrame.AUTO_ZOOM) {
                Helpers.threadRun(() -> {
                    Helpers.pausar(GameFrame.GUI_RENDER_WAIT);
                    tapete.autoZoom(false);
                });
            }
            Helpers.savePropertiesFile();
        });

    }//GEN-LAST:event_zoom_menu_inActionPerformed

    private void zoom_menu_outActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoom_menu_outActionPerformed
        // TODO add your handling code here:

        Audio.playWavResource("misc/zoom_out.wav");

        if (Helpers.doubleSecureCompare(0f, 1f + ((ZOOM_LEVEL - 1) * ZOOM_STEP)) < 0) {

            Helpers.threadRun(() -> {
                decrementZoom();
                Helpers.PROPERTIES.setProperty("zoom_level", String.valueOf(ZOOM_LEVEL));
                Card.updateCachedImages(1f + ZOOM_LEVEL * ZOOM_STEP, false);
                zoom(1f + ZOOM_LEVEL * ZOOM_STEP, null);
                if (jugadas_dialog != null && jugadas_dialog.isVisible()) {
                    for (Card carta : jugadas_dialog.getCartas()) {
                        carta.invalidateImagePrecache();
                        carta.refreshCard();
                    }
                    Helpers.GUIRun(jugadas_dialog::pack);
                }
                if (shortcuts_dialog != null && shortcuts_dialog.isVisible()) {

                    shortcuts_dialog.zoom(1f + ZOOM_LEVEL * ZOOM_STEP, null);

                }
                Helpers.savePropertiesFile();
            });

        }

    }//GEN-LAST:event_zoom_menu_outActionPerformed

    private void zoom_menu_resetActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoom_menu_resetActionPerformed
        // TODO add your handling code here:

        if (ZOOM_LEVEL != DEFAULT_ZOOM_LEVEL) {

            Audio.playWavResource("misc/zoom_reset.wav");

            Helpers.threadRun(() -> {
                ZOOM_LEVEL = DEFAULT_ZOOM_LEVEL;
                Helpers.PROPERTIES.setProperty("zoom_level", String.valueOf(ZOOM_LEVEL));
                Card.updateCachedImages(1f + ZOOM_LEVEL * ZOOM_STEP, false);
                zoom(1f + ZOOM_LEVEL * ZOOM_STEP, null);
                if (jugadas_dialog != null && jugadas_dialog.isVisible()) {
                    for (Card carta : jugadas_dialog.getCartas()) {
                        carta.invalidateImagePrecache();
                        carta.refreshCard();
                    }
                    Helpers.GUIRun(jugadas_dialog::pack);
                }
                if (shortcuts_dialog != null && shortcuts_dialog.isVisible()) {

                    shortcuts_dialog.zoom(1f + ZOOM_LEVEL * ZOOM_STEP, null);

                }
                if (GameFrame.AUTO_ZOOM) {
                    Helpers.threadRun(() -> {
                        Helpers.pausar(GameFrame.GUI_RENDER_WAIT);
                        tapete.autoZoom(false);
                    });
                }
                Helpers.savePropertiesFile();
            });

        }
    }//GEN-LAST:event_zoom_menu_resetActionPerformed

    private void registro_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_registro_menuActionPerformed
        // TODO add your handling code here:

        if (registro_dialog.getParent() != this) {
            registro_dialog.setVisible(false);
            registro_dialog.dispose();
            registro_dialog = new GameLogDialog(this, false);
        }

        if (!registro_dialog.isVisible()) {

            // El tamaño/posición por defecto (consola 1280x720 centrada, no 0.8x la
            // ventana) solo se aplica la PRIMERA vez que se abre este diálogo;
            // reaperturas posteriores conservan lo que el usuario haya
            // redimensionado/movido (cerrar el registro solo lo oculta).
            if (!registro_dialog.isDefaultBoundsApplied()) {

                registro_dialog.setPreferredSize(new java.awt.Dimension(1280, 720));

                registro_dialog.pack();

                registro_dialog.setLocationRelativeTo(this);

                registro_dialog.setDefaultBoundsApplied(true);
            }

            registro_dialog.setVisible(true);
        }

    }//GEN-LAST:event_registro_menuActionPerformed

    private void chat_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chat_menuActionPerformed
        // TODO add your handling code here:

        if (fastchat_dialog != null && fastchat_dialog.isVisible()) {
            fastchat_dialog.setVisible(false);
        }

        if (!this.sala_espera.isActive()) {
            this.sala_espera.setVisible(false);
        }

        this.sala_espera.setLocationRelativeTo(this);
        this.sala_espera.setExtendedState(JFrame.NORMAL);
        this.sala_espera.setVisible(true);

    }//GEN-LAST:event_chat_menuActionPerformed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        // TODO add your handling code here:
        this.exit_menu.doClick();
    }//GEN-LAST:event_formWindowClosing

    private void time_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_time_menuActionPerformed
        // TODO add your handling code here:

        GameFrame.SHOW_CLOCK = time_menu.isSelected();

        tapete.getCommunityCards().getTiempo_partida().setVisible(time_menu.isSelected());

        Helpers.PROPERTIES.setProperty("show_time", String.valueOf(this.time_menu.isSelected()));

        Helpers.savePropertiesFile();

        Helpers.TapetePopupMenu.RELOJ_MENU.setSelected(GameFrame.SHOW_CLOCK);
    }//GEN-LAST:event_time_menuActionPerformed

    private void jugadas_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jugadas_menuActionPerformed
        // TODO add your handling code here:

        jugadas_menu.setEnabled(false);

        if (jugadas_dialog == null) {
            jugadas_dialog = new HandGeneratorDialog(this, false);
        } else if (jugadas_dialog.getParent() != this) {
            jugadas_dialog.setVisible(false);
            jugadas_dialog.dispose();
            jugadas_dialog = new HandGeneratorDialog(this, false);
        }

        if (!jugadas_dialog.isVisible()) {
            Helpers.threadRun(() -> {
                jugadas_dialog.pintarJugada();
                Helpers.GUIRun(() -> {
                    jugadas_dialog.pack();
                    jugadas_dialog.setLocationRelativeTo(this);
                    jugadas_dialog.setVisible(true);
                    jugadas_menu.setEnabled(true);
                });
            });
        } else {
            jugadas_menu.setEnabled(true);
        }
    }//GEN-LAST:event_jugadas_menuActionPerformed

    private void full_screen_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_full_screen_menuActionPerformed
        triggerFullScreenToggle();
    }//GEN-LAST:event_full_screen_menuActionPerformed

    /**
     * Punto de entrada unificado para alternar pantalla completa, desde el
     * listener del menu o desde rutas de inicializacion (autoZoomFullScreen).
     * Antes autoZoomFullScreen llamaba a full_screen_menu.doClick() para
     * disparar este flujo via el listener del JMenuItem; el doClick era un
     * antipatron de Swing porque acoplaba la inicializacion al UI y simulaba
     * eventos sinteticos. Ahora ambas rutas llaman directamente aqui.
     */
    public void triggerFullScreenToggle() {
        if (full_screen_menu.isEnabled() && !isGame_over_dialog()) {
            full_screen_menu.setEnabled(false);
            Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(false);
            if (!Helpers.OSValidator.isMac() || !GameFrame.MAC_NATIVE_FULLSCREEN) {
                Helpers.TapetePopupMenu.FULLSCREEN_MENU.setSelected(!full_screen);
                toggleFullScreen();
            } else {
                toggleMacNativeFullScreen(GameFrame.getInstance());
            }
        }
    }

    // Modo de pantalla elegido en Ajustes > Apariencia (lista ventana / pantalla
    // completa). GUARDA la preferencia (que se aplica también al ARRANCAR partida vía
    // autoZoomFullScreen(AUTO_FULLSCREEN)) y la APLICA ya si el estado actual difiere.
    public void setDisplayModeFullScreen(boolean fullscreen) {
        GameFrame.AUTO_FULLSCREEN = fullscreen;
        Helpers.PROPERTIES.setProperty("auto_fullscreen", String.valueOf(fullscreen));
        Helpers.savePropertiesFile();
        if (auto_fullscreen_menu != null) {
            auto_fullscreen_menu.setSelected(fullscreen);
        }
        if (Helpers.TapetePopupMenu.AUTO_FULLSCREEN_MENU != null) {
            Helpers.TapetePopupMenu.AUTO_FULLSCREEN_MENU.setSelected(fullscreen);
        }
        if (fullscreen != full_screen) {
            // El toggle dispone y recrea el peer nativo del frame, lo que corrompe un
            // diálogo modal abierto encima. Por eso el combo de Ajustes NO aplica en
            // vivo: el diálogo invoca esto al pulsar GUARDAR (applyPendingDisplayMode),
            // justo antes de cerrarse. Se difiere al EDT para correr tras drenar el cierre.
            SwingUtilities.invokeLater(this::triggerFullScreenToggle);
        }
    }

    // Fija la vista compacta a un valor CONCRETO (0=off, 1=compacta, 2=compacta+cartas),
    // para el desplegable de Ajustes > Apariencia. Misma lógica que el ciclo del menú
    // (compact_menuActionPerformed) pero a un destino dado en vez de (n+1)%3.
    public void setCompactView(int target) {
        target = ((target % 3) + 3) % 3;
        if (target == GameFrame.VISTA_COMPACTA) {
            return;
        }
        GameFrame.VISTA_COMPACTA = target;
        compact_menu.setSelected(target > 0);
        for (Card carta : getTapete().getCommunityCards().getCartasComunes()) {
            carta.setCompactable(target == 2);
        }
        Audio.playWavResource("misc/power_" + (target > 0 ? "down" : "up") + ".wav");
        Helpers.PROPERTIES.setProperty("vista_compacta", String.valueOf(target));
        Helpers.savePropertiesFile();
        Helpers.threadRun(this::vistaCompacta);
        Helpers.TapetePopupMenu.COMPACTA_MENU.setSelected(target > 0);
    }

    // Fija el nivel de zoom a un valor CONCRETO, para el spinner de Ajustes >
    // Apariencia. Aplica de una sola vez (mismo trabajo que zoom_menu_in/out/reset
    // pero al nivel destino). El factor de zoom debe quedar > 0 (misma guarda que el
    // zoom-out del menú).
    public void setZoomLevel(int target) {
        if (Helpers.doubleSecureCompare(0f, 1f + (target * ZOOM_STEP)) >= 0) {
            return;
        }
        final int old = ZOOM_LEVEL;
        if (target == old) {
            return;
        }
        Audio.playWavResource("misc/zoom_" + (target > old ? "in" : "out") + ".wav");
        Helpers.threadRun(() -> {
            ZOOM_LEVEL = target;
            Helpers.PROPERTIES.setProperty("zoom_level", String.valueOf(ZOOM_LEVEL));
            Card.updateCachedImages(1f + ZOOM_LEVEL * ZOOM_STEP, false);
            zoom(1f + ZOOM_LEVEL * ZOOM_STEP, null);
            if (jugadas_dialog != null && jugadas_dialog.isVisible()) {
                for (Card carta : jugadas_dialog.getCartas()) {
                    carta.invalidateImagePrecache();
                    carta.refreshCard();
                }
                Helpers.GUIRun(jugadas_dialog::pack);
            }
            if (shortcuts_dialog != null && shortcuts_dialog.isVisible()) {
                shortcuts_dialog.zoom(1f + ZOOM_LEVEL * ZOOM_STEP, null);
            }
            if (GameFrame.AUTO_ZOOM) {
                Helpers.threadRun(() -> {
                    Helpers.pausar(GameFrame.GUI_RENDER_WAIT);
                    tapete.autoZoom(false);
                });
            }
            Helpers.savePropertiesFile();
        });
    }

    private void compact_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_compact_menuActionPerformed
        // TODO add your handling code here:

        GameFrame.VISTA_COMPACTA = (GameFrame.VISTA_COMPACTA + 1) % 3;

        if (GameFrame.VISTA_COMPACTA > 0) {

            this.compact_menu.setSelected(true);

            if (GameFrame.VISTA_COMPACTA == 2) {
                for (Card carta : this.getTapete().getCommunityCards().getCartasComunes()) {
                    carta.setCompactable(true);
                }
            }

        } else {

            this.compact_menu.setSelected(false);

            for (Card carta : this.getTapete().getCommunityCards().getCartasComunes()) {
                carta.setCompactable(false);
            }
        }

        Audio.playWavResource("misc/power_" + (GameFrame.VISTA_COMPACTA > 0 ? "down" : "up") + ".wav");

        Helpers.PROPERTIES.setProperty("vista_compacta", String.valueOf(GameFrame.VISTA_COMPACTA));

        Helpers.savePropertiesFile();

        Helpers.threadRun(this::vistaCompacta);

        Helpers.TapetePopupMenu.COMPACTA_MENU.setSelected(GameFrame.VISTA_COMPACTA > 0);
    }//GEN-LAST:event_compact_menuActionPerformed

    private void confirmar_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_confirmar_menuActionPerformed
        // TODO add your handling code here:
        GameFrame.CONFIRM_ACTIONS = this.confirmar_menu.isSelected();

        Helpers.PROPERTIES.setProperty("confirmar_todo", String.valueOf(GameFrame.CONFIRM_ACTIONS));

        Helpers.savePropertiesFile();

        Helpers.TapetePopupMenu.CONFIRM_MENU.setSelected(GameFrame.CONFIRM_ACTIONS);

        if (!GameFrame.CONFIRM_ACTIONS) {
            this.getLocalPlayer().desarmarBotonesAccion();
        }

    }//GEN-LAST:event_confirmar_menuActionPerformed

    private void auto_action_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_auto_action_menuActionPerformed
        // TODO add your handling code here:
        GameFrame.AUTO_ACTION_BUTTONS = this.auto_action_menu.isSelected();

        Helpers.PROPERTIES.setProperty("auto_action_buttons", String.valueOf(GameFrame.AUTO_ACTION_BUTTONS));

        Helpers.savePropertiesFile();

        Helpers.TapetePopupMenu.AUTO_ACTION_MENU.setSelected(GameFrame.AUTO_ACTION_BUTTONS);

        // "Persistir AUTO" solo es operable con "Botones AUTO" activo: grisar/activar
        // su checkbox en ambos menús (menú-bar y popup del tapete).
        if (auto_action_persist_menu != null) {
            auto_action_persist_menu.setEnabled(GameFrame.AUTO_ACTION_BUTTONS);
        }
        if (Helpers.TapetePopupMenu.AUTO_ACTION_PERSIST_MENU != null) {
            Helpers.TapetePopupMenu.AUTO_ACTION_PERSIST_MENU.setEnabled(GameFrame.AUTO_ACTION_BUTTONS);
        }
        if (modo_auto_confirm_menu != null) {
            modo_auto_confirm_menu.setEnabled(GameFrame.AUTO_ACTION_BUTTONS);
        }
        if (Helpers.TapetePopupMenu.MODO_AUTO_CONFIRM_MENU != null) {
            Helpers.TapetePopupMenu.MODO_AUTO_CONFIRM_MENU.setEnabled(GameFrame.AUTO_ACTION_BUTTONS);
        }
        if (auto_call_menu != null) {
            auto_call_menu.setEnabled(GameFrame.AUTO_ACTION_BUTTONS);
        }
        if (Helpers.TapetePopupMenu.AUTO_CALL_MENU != null) {
            Helpers.TapetePopupMenu.AUTO_CALL_MENU.setEnabled(GameFrame.AUTO_ACTION_BUTTONS);
        }

        if (GameFrame.AUTO_ACTION_BUTTONS) {
            this.getLocalPlayer().activarPreBotones();
        } else {
            this.getLocalPlayer().desActivarPreBotones();
        }
    }//GEN-LAST:event_auto_action_menuActionPerformed

    private void menu_tapete_verdeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menu_tapete_verdeActionPerformed
        // TODO add your handling code here:

        if (Init.M2 != null && tapete_counter == 4 && GameFrame.COLOR_TAPETE.equals("verde")) {
            GameFrame.COLOR_TAPETE = "verde*";

            Helpers.PROPERTIES.setProperty("color_tapete", GameFrame.COLOR_TAPETE);

            Helpers.savePropertiesFile();

            for (Component c : menu_tapetes.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setEnabled(false);
            }

            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setEnabled(false);
            }

            Helpers.threadRun(() -> {
                Helpers.GUIRun(() -> {
                    for (Component c : menu_tapetes.getMenuComponents()) {
                        ((JRadioButtonMenuItem) c).setSelected(false);
                    }

                    menu_tapete_verde.setSelected(true);

                    for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                        ((JRadioButtonMenuItem) c).setSelected(false);
                    }

                    Helpers.TapetePopupMenu.TAPETE_VERDE.setSelected(true);

                    for (Component c : menu_tapetes.getMenuComponents()) {
                        ((JRadioButtonMenuItem) c).setEnabled(true);
                    }

                    for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                        ((JRadioButtonMenuItem) c).setEnabled(true);
                    }
                    tapete.refresh();

                    cambiarColorContadoresTapete(Color.WHITE);
                });
                tapete_counter = 0;
            });

        } else if (!GameFrame.COLOR_TAPETE.equals("verde*")) {

            if (GameFrame.COLOR_TAPETE.equals("verde")) {
                tapete_counter++;
            } else {
                tapete_counter = 1;
            }

            GameFrame.COLOR_TAPETE = "verde";

            Helpers.PROPERTIES.setProperty("color_tapete", GameFrame.COLOR_TAPETE);

            Helpers.savePropertiesFile();

            for (Component c : this.menu_tapetes.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            this.menu_tapete_verde.setSelected(true);

            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            Helpers.TapetePopupMenu.TAPETE_VERDE.setSelected(true);

            tapete.refresh();

            cambiarColorContadoresTapete(new Color(153, 204, 0));

        } else {
            for (Component c : this.menu_tapetes.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            this.menu_tapete_verde.setSelected(true);

            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            Helpers.TapetePopupMenu.TAPETE_VERDE.setSelected(true);
        }

    }//GEN-LAST:event_menu_tapete_verdeActionPerformed

    private void menu_tapete_azulActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menu_tapete_azulActionPerformed
        // TODO add your handling code here:

        if (Init.M2 != null && tapete_counter == 4 && GameFrame.COLOR_TAPETE.equals("azul")) {
            GameFrame.COLOR_TAPETE = "azul*";

            Helpers.PROPERTIES.setProperty("color_tapete", GameFrame.COLOR_TAPETE);

            Helpers.savePropertiesFile();

            for (Component c : menu_tapetes.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setEnabled(false);
            }

            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setEnabled(false);
            }

            Helpers.threadRun(() -> {
                Helpers.GUIRun(() -> {
                    for (Component c : menu_tapetes.getMenuComponents()) {
                        ((JRadioButtonMenuItem) c).setSelected(false);
                    }

                    menu_tapete_azul.setSelected(true);

                    for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                        ((JRadioButtonMenuItem) c).setSelected(false);
                    }

                    Helpers.TapetePopupMenu.TAPETE_AZUL.setSelected(true);

                    for (Component c : menu_tapetes.getMenuComponents()) {
                        ((JRadioButtonMenuItem) c).setEnabled(true);
                    }

                    for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                        ((JRadioButtonMenuItem) c).setEnabled(true);
                    }

                    tapete.refresh();

                    cambiarColorContadoresTapete(Color.WHITE);
                });
                tapete_counter = 0;
            });

        } else if (!GameFrame.COLOR_TAPETE.equals("azul*")) {

            if (GameFrame.COLOR_TAPETE.equals("azul")) {
                tapete_counter++;
            } else {
                tapete_counter = 1;
            }

            GameFrame.COLOR_TAPETE = "azul";

            Helpers.PROPERTIES.setProperty("color_tapete", GameFrame.COLOR_TAPETE);

            Helpers.savePropertiesFile();

            for (Component c : this.menu_tapetes.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            this.menu_tapete_azul.setSelected(true);

            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            Helpers.TapetePopupMenu.TAPETE_AZUL.setSelected(true);

            tapete.refresh();

            cambiarColorContadoresTapete(new Color(102, 204, 255));

        } else {
            for (Component c : this.menu_tapetes.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            this.menu_tapete_azul.setSelected(true);

            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            Helpers.TapetePopupMenu.TAPETE_AZUL.setSelected(true);
        }
    }//GEN-LAST:event_menu_tapete_azulActionPerformed

    private void menu_tapete_rojoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menu_tapete_rojoActionPerformed
        // TODO add your handling code here:

        if (Init.M2 != null && tapete_counter == 4 && GameFrame.COLOR_TAPETE.equals("rojo")) {
            GameFrame.COLOR_TAPETE = "rojo*";

            Helpers.PROPERTIES.setProperty("color_tapete", GameFrame.COLOR_TAPETE);

            Helpers.savePropertiesFile();

            for (Component c : menu_tapetes.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setEnabled(false);
            }

            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setEnabled(false);
            }

            Helpers.threadRun(() -> {
                Helpers.GUIRun(() -> {
                    for (Component c : menu_tapetes.getMenuComponents()) {
                        ((JRadioButtonMenuItem) c).setSelected(false);
                    }

                    menu_tapete_rojo.setSelected(true);

                    for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                        ((JRadioButtonMenuItem) c).setSelected(false);
                    }

                    Helpers.TapetePopupMenu.TAPETE_ROJO.setSelected(true);

                    for (Component c : menu_tapetes.getMenuComponents()) {
                        ((JRadioButtonMenuItem) c).setEnabled(true);
                    }

                    for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                        ((JRadioButtonMenuItem) c).setEnabled(true);
                    }

                    tapete.refresh();

                    cambiarColorContadoresTapete(Color.WHITE);
                });
                tapete_counter = 0;
            });

        } else if (!GameFrame.COLOR_TAPETE.equals("rojo*")) {

            if (GameFrame.COLOR_TAPETE.equals("rojo")) {
                tapete_counter++;
            } else {
                tapete_counter = 1;
            }

            GameFrame.COLOR_TAPETE = "rojo";

            Helpers.PROPERTIES.setProperty("color_tapete", GameFrame.COLOR_TAPETE);

            Helpers.savePropertiesFile();

            for (Component c : this.menu_tapetes.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            this.menu_tapete_rojo.setSelected(true);

            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            Helpers.TapetePopupMenu.TAPETE_ROJO.setSelected(true);

            tapete.refresh();

            cambiarColorContadoresTapete(new Color(255, 204, 51));

        } else {
            for (Component c : this.menu_tapetes.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            this.menu_tapete_rojo.setSelected(true);

            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            Helpers.TapetePopupMenu.TAPETE_ROJO.setSelected(true);
        }

    }//GEN-LAST:event_menu_tapete_rojoActionPerformed

    private void menu_tapete_maderaActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menu_tapete_maderaActionPerformed
        // TODO add your handling code here:

        if (Init.M2 != null && tapete_counter == 4 && GameFrame.COLOR_TAPETE.equals("madera")) {
            GameFrame.COLOR_TAPETE = "madera*";

            Helpers.PROPERTIES.setProperty("color_tapete", GameFrame.COLOR_TAPETE);

            Helpers.savePropertiesFile();

            for (Component c : menu_tapetes.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setEnabled(false);
            }

            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setEnabled(false);
            }

            Helpers.threadRun(() -> {
                Helpers.GUIRun(() -> {
                    tapete.refresh();

                    cambiarColorContadoresTapete(Color.WHITE);

                    for (Component c : menu_tapetes.getMenuComponents()) {
                        ((JRadioButtonMenuItem) c).setSelected(false);
                    }

                    menu_tapete_madera.setSelected(true);

                    for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                        ((JRadioButtonMenuItem) c).setSelected(false);
                    }

                    Helpers.TapetePopupMenu.TAPETE_MADERA.setSelected(true);

                    for (Component c : menu_tapetes.getMenuComponents()) {
                        ((JRadioButtonMenuItem) c).setEnabled(true);
                    }

                    for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                        ((JRadioButtonMenuItem) c).setEnabled(true);
                    }
                });
                tapete_counter = 0;
            });

        } else if (!GameFrame.COLOR_TAPETE.equals("madera*")) {

            if (GameFrame.COLOR_TAPETE.equals("madera")) {
                tapete_counter++;
            } else {
                tapete_counter = 1;
            }

            GameFrame.COLOR_TAPETE = "madera";

            Helpers.PROPERTIES.setProperty("color_tapete", GameFrame.COLOR_TAPETE);

            Helpers.savePropertiesFile();

            for (Component c : this.menu_tapetes.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            this.menu_tapete_madera.setSelected(true);

            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            Helpers.TapetePopupMenu.TAPETE_MADERA.setSelected(true);

            tapete.refresh();

            cambiarColorContadoresTapete(Color.WHITE);

        } else {
            for (Component c : this.menu_tapetes.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            this.menu_tapete_madera.setSelected(true);

            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            Helpers.TapetePopupMenu.TAPETE_MADERA.setSelected(true);
        }

    }//GEN-LAST:event_menu_tapete_maderaActionPerformed

    private void menu_cinematicasActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menu_cinematicasActionPerformed
        // TODO add your handling code here:

        GameFrame.CINEMATICAS_PREF = this.menu_cinematicas.isSelected();

        Helpers.PROPERTIES.setProperty("cinematicas", String.valueOf(GameFrame.CINEMATICAS_PREF));

        Helpers.savePropertiesFile();

        Helpers.TapetePopupMenu.CINEMATICAS_MENU.setSelected(GameFrame.CINEMATICAS_PREF);
    }//GEN-LAST:event_menu_cinematicasActionPerformed

    private void shortcuts_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_shortcuts_menuActionPerformed
        // TODO add your handling code here:

        if (shortcuts_dialog == null) {

            shortcuts_dialog = new ShortcutsDialog(this, false);

        }

        if (!shortcuts_dialog.isVisible()) {

            shortcuts_menu.setEnabled(false);

            Helpers.threadRun(() -> {
                Helpers.zoomFonts(shortcuts_dialog, 1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP, null);
                Helpers.GUIRun(() -> {
                    shortcuts_dialog.setLocation(this.getX() + this.getWidth() - shortcuts_dialog.getWidth(), this.getY() + this.getHeight() - shortcuts_dialog.getHeight());

                    shortcuts_dialog.setVisible(true);

                    shortcuts_menu.setEnabled(true);
                });
            });

        } else {
            shortcuts_dialog.setVisible(false);
        }

    }//GEN-LAST:event_shortcuts_menuActionPerformed

    public RebuyDialog getRebuy_dialog() {
        return rebuy_dialog;
    }

    public JCheckBoxMenuItem getLast_hand_menu() {
        return last_hand_menu;
    }

    private void rebuy_now_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rebuy_now_menuActionPerformed
        // TODO add your handling code here:

        Helpers.TapetePopupMenu.REBUY_NOW_MENU.setSelected(this.rebuy_now_menu.isSelected());

        LocalPlayer player = GameFrame.getInstance().getLocalPlayer();

        this.rebuy_now_menu.setEnabled(false);

        Helpers.TapetePopupMenu.REBUY_NOW_MENU.setEnabled(false);

        if (crupier.getRebuy_now().containsKey(player.getNickname())) {

            Helpers.threadRun(() -> {
                crupier.rebuyNow(player.getNickname(), -1);
                Helpers.GUIRun(() -> {
                    if (GameFrame.hasRebought(player.getNickname())) {
                        player.setPlayerStackBackground(Color.CYAN);
                        player.getPlayer_stack().setForeground(Color.BLACK);
                    } else {
                        player.setPlayerStackBackground(new Color(51, 153, 0));
                        player.getPlayer_stack().setForeground(Color.WHITE);
                    }

                    player.getPlayer_stack().setText(Helpers.money2String(player.getStack()));
                    rebuy_now_menu.setEnabled(true);
                    Helpers.TapetePopupMenu.REBUY_NOW_MENU.setEnabled(true);
                    rebuy_now_menu.setBackground(null);
                    rebuy_now_menu.setOpaque(false);
                    Helpers.TapetePopupMenu.REBUY_NOW_MENU.setBackground(null);
                    Helpers.TapetePopupMenu.REBUY_NOW_MENU.setOpaque(false);
                    // Removed forceRepaintComponentNow
                });
                Audio.playWavResource("misc/button_off.wav");
            });

        } else if (crupier.atRebuyLimit(player.getNickname())) {

            rebuy_now_menu.setEnabled(true);
            Helpers.TapetePopupMenu.REBUY_NOW_MENU.setEnabled(true);
            rebuy_now_menu.setSelected(false);
            Helpers.TapetePopupMenu.REBUY_NOW_MENU.setSelected(false);

            Helpers.mostrarMensajeError(GameFrame.getInstance(), Translator.translate("rebuy.limite_alcanzado", String.valueOf(GameFrame.REBUY_LIMIT)));

        } else if (GameFrame.rebuyHeadroom(player.getStack()) < (GameFrame.FIXED_BUYIN ? 1 : GameFrame.getBuyinMin())) {

            // Ya en el techo de mesa: no hay margen para recomprar.
            rebuy_now_menu.setEnabled(true);
            Helpers.TapetePopupMenu.REBUY_NOW_MENU.setEnabled(true);
            rebuy_now_menu.setSelected(false);
            Helpers.TapetePopupMenu.REBUY_NOW_MENU.setSelected(false);

            Helpers.mostrarMensajeError(GameFrame.getInstance(), Translator.translate("rebuy.sin_margen"));

        } else {

            // Top-up en vivo: max = headroom (techo - stack), min y default segun
            // modo (fijo: [1, BUYIN]; variable: rango configurado
            // [getBuyinMin, getBuyinDefault]), recortados al headroom para no
            // superar el techo. SIN cuenta atras
            // (timeout -1): la recompra intra-mano es voluntaria; el tiempo solo
            // aplica al arranque (compra inicial) y al game-over.
            int headroom = GameFrame.rebuyHeadroom(player.getStack());
            int rebuy_min = GameFrame.FIXED_BUYIN ? 1 : GameFrame.getBuyinMin();
            int rebuy_def = Math.min(GameFrame.FIXED_BUYIN ? GameFrame.BUYIN : GameFrame.getBuyinDefault(), headroom);

            rebuy_dialog = new RebuyDialog(GameFrame.getInstance(), true, true, -1, rebuy_min, headroom, rebuy_def);

            rebuy_dialog.setLocationRelativeTo(rebuy_dialog.getParent());

            rebuy_dialog.setVisible(true);

            if (rebuy_dialog.isRebuy()) {
                player.setPlayerStackBackground(Color.YELLOW);
                player.getPlayer_stack().setForeground(Color.BLACK);
                player.getPlayer_stack().setText(Helpers.money2String(player.getStack()) + " + " + Helpers.money2String((int) rebuy_dialog.getRebuy_spinner().getValue()));
                this.rebuy_now_menu.setBackground(Color.YELLOW);
                this.rebuy_now_menu.setOpaque(true);
                Helpers.TapetePopupMenu.REBUY_NOW_MENU.setBackground(Color.YELLOW);
                Helpers.TapetePopupMenu.REBUY_NOW_MENU.setOpaque(true);

                Helpers.threadRun(() -> {
                    crupier.rebuyNow(player.getNickname(), (int) rebuy_dialog.getRebuy_spinner().getValue());
                    Helpers.GUIRun(() -> {
                        rebuy_now_menu.setEnabled(true);
                        Helpers.TapetePopupMenu.REBUY_NOW_MENU.setEnabled(true);
                        rebuy_dialog = null;
                        // Removed forceRepaintComponentNow
                    });
                    Audio.playWavResource("misc/button_on.wav");
                });
            } else {
                rebuy_now_menu.setEnabled(true);
                rebuy_now_menu.setSelected(false);
                Helpers.TapetePopupMenu.REBUY_NOW_MENU.setEnabled(true);
                Helpers.TapetePopupMenu.REBUY_NOW_MENU.setSelected(false);
                this.rebuy_now_menu.setBackground(null);
                this.rebuy_now_menu.setOpaque(false);
                Helpers.TapetePopupMenu.REBUY_NOW_MENU.setBackground(null);
                Helpers.TapetePopupMenu.REBUY_NOW_MENU.setOpaque(false);
                rebuy_dialog = null;
                // Removed forceRepaintComponentNow
            }

        }

    }//GEN-LAST:event_rebuy_now_menuActionPerformed

    private void last_hand_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_last_hand_menuActionPerformed
        // TODO add your handling code here:
        GameFrame.getInstance().getTapete().getCommunityCards().hand_label_left_click();
    }//GEN-LAST:event_last_hand_menuActionPerformed

    private void max_hands_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_max_hands_menuActionPerformed
        // TODO add your handling code here:
        GameFrame.getInstance().getTapete().getCommunityCards().hand_label_right_click();
    }//GEN-LAST:event_max_hands_menuActionPerformed

    private void auto_fit_zoom_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_auto_fit_zoom_menuActionPerformed
        // TODO add your handling code here:
        GameFrame.AUTO_ZOOM = auto_fit_zoom_menu.isSelected();

        if (auto_fit_zoom_menu.isSelected()) {

            auto_fit_zoom_menu.setEnabled(false);

            Helpers.threadRun(() -> {
                tapete.autoZoom(false);
                Helpers.GUIRun(() -> {
                    auto_fit_zoom_menu.setEnabled(true);
                });
            });
        }

        Helpers.PROPERTIES.setProperty("auto_zoom", String.valueOf(auto_fit_zoom_menu.isSelected()));

        Helpers.savePropertiesFile();

        Helpers.TapetePopupMenu.AUTO_ZOOM_MENU.setSelected(GameFrame.AUTO_ZOOM);
    }//GEN-LAST:event_auto_fit_zoom_menuActionPerformed

    private void robert_rules_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_robert_rules_menuActionPerformed
        // TODO add your handling code here:
        Helpers.openBrowserURL("https://github.com/tonikelope/coronapoker/raw/master/robert_rules.pdf");
    }//GEN-LAST:event_robert_rules_menuActionPerformed

    private void menu_tapete_negroActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menu_tapete_negroActionPerformed
        // TODO add your handling code here:
        if (Init.M2 != null && tapete_counter == 4 && GameFrame.COLOR_TAPETE.equals("negro")) {
            GameFrame.COLOR_TAPETE = "negro*";

            Helpers.PROPERTIES.setProperty("color_tapete", GameFrame.COLOR_TAPETE);

            Helpers.savePropertiesFile();

            for (Component c : menu_tapetes.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setEnabled(false);
            }

            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setEnabled(false);
            }

            Helpers.threadRun(() -> {
                Helpers.GUIRun(() -> {
                    for (Component c : menu_tapetes.getMenuComponents()) {
                        ((JRadioButtonMenuItem) c).setSelected(false);
                    }

                    menu_tapete_negro.setSelected(true);

                    for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                        ((JRadioButtonMenuItem) c).setSelected(false);
                    }

                    Helpers.TapetePopupMenu.TAPETE_NEGRO.setSelected(true);

                    for (Component c : menu_tapetes.getMenuComponents()) {
                        ((JRadioButtonMenuItem) c).setEnabled(true);
                    }

                    for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                        ((JRadioButtonMenuItem) c).setEnabled(true);
                    }

                    tapete.refresh();

                    cambiarColorContadoresTapete(Color.WHITE);
                });
                tapete_counter = 0;
            });

        } else if (!GameFrame.COLOR_TAPETE.equals("negro*")) {

            if (GameFrame.COLOR_TAPETE.equals("negro")) {
                tapete_counter++;
            } else {
                tapete_counter = 1;
            }

            GameFrame.COLOR_TAPETE = "negro";

            Helpers.PROPERTIES.setProperty("color_tapete", GameFrame.COLOR_TAPETE);

            Helpers.savePropertiesFile();

            for (Component c : this.menu_tapetes.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            this.menu_tapete_negro.setSelected(true);

            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            Helpers.TapetePopupMenu.TAPETE_NEGRO.setSelected(true);

            tapete.refresh();

            cambiarColorContadoresTapete(Color.LIGHT_GRAY);

        } else {
            for (Component c : this.menu_tapetes.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            this.menu_tapete_negro.setSelected(true);

            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            Helpers.TapetePopupMenu.TAPETE_NEGRO.setSelected(true);
        }
    }//GEN-LAST:event_menu_tapete_negroActionPerformed

    private void chat_image_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chat_image_menuActionPerformed
        // TODO add your handling code here:
        GameFrame.CHAT_IMAGES_INGAME = chat_image_menu.isSelected();

        Helpers.TapetePopupMenu.CHAT_IMAGE_MENU.setSelected(chat_image_menu.isSelected());

        Helpers.PROPERTIES.setProperty("chat_images_ingame", String.valueOf(GameFrame.CHAT_IMAGES_INGAME));

        Helpers.savePropertiesFile();
    }//GEN-LAST:event_chat_image_menuActionPerformed

    private void force_reconnect_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_force_reconnect_menuActionPerformed
        // TODO add your handling code here:

        if (isPartida_local() && Helpers.mostrarMensajeInformativoSINO(GameFrame.getInstance(), Translator.translate("conn.forzar_reconexion_de_todos_los"), new ImageIcon(getClass().getResource("/images/action/timeout.png"))) == 0) {

            boolean ok = false;

            // Bloqueamos modificaciones al mapa mientras iteramos
            synchronized (getParticipantes()) {
                for (Map.Entry<String, Participant> entry : getParticipantes().entrySet()) {

                    if (entry.getValue() != null && !entry.getValue().isCpu()) {
                        // Con watchdog: si el peer forzado no vuelve dentro del grace, se
                        // libera force_reset_socket y se da por perdido (sin esto, su
                        // transporte quedaba bloqueado para siempre).
                        entry.getValue().forceSocketReconnectWithWatchdog();
                        ok = true;
                    }
                }
            }

            if (!ok) {
                Helpers.mostrarMensajeError(GameFrame.getInstance(), Translator.translate("conn.no_hay_jugadores_humanos_conectados"));
            }
        }
    }//GEN-LAST:event_force_reconnect_menuActionPerformed

    private void auto_fullscreen_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_auto_fullscreen_menuActionPerformed
        // TODO add your handling code here:
        GameFrame.AUTO_FULLSCREEN = auto_fullscreen_menu.isSelected();

        Helpers.TapetePopupMenu.AUTO_FULLSCREEN_MENU.setSelected(GameFrame.AUTO_FULLSCREEN);

        Helpers.PROPERTIES.setProperty("auto_fullscreen", String.valueOf(GameFrame.AUTO_FULLSCREEN));

        Helpers.savePropertiesFile();
    }//GEN-LAST:event_auto_fullscreen_menuActionPerformed

    private void halt_game_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_halt_game_menuActionPerformed
        // TODO add your handling code here:
        getCrupier().setForce_recover(true);
        exit_menuActionPerformed(evt);
    }//GEN-LAST:event_halt_game_menuActionPerformed

    // Recompra automática al arruinarse: checkbox del menú Preferencias y del
    // popup. Campo a mano (fuera del bloque generado). Preferencia LOCAL de
    // sesión sincronizada menú↔popup.
    private javax.swing.JCheckBoxMenuItem auto_rebuy_menu;

    // "Ajustes de partida": entrada del menú Preferencias (y gemelo del popup +
    // icono del tapete) que abre el diálogo consolidado de reglas. Campo a mano
    // (fuera del bloque generado por el editor).
    private javax.swing.JMenuItem ajustes_partida_menu;

    // Menú "Apariencia" del menú-bar (construido a mano re-parentando ítems);
    // es uno de los menús de nivel superior, así que el dispatcher de teclas lo
    // consulta para no robar atajos mientras está abierto.
    private javax.swing.JMenu apariencia_menu;

    // Submenú "Efectos de animación" (construido a mano): tres efectos
    // combinables — reparto/destapes de cartas, fichas de posición (ciegas+dealer)
    // y ficha al bote (apuestas). Campos a mano para sincronizar con el popup.
    private javax.swing.JCheckBoxMenuItem anim_reparto_menu;
    private javax.swing.JCheckBoxMenuItem anim_ciegas_dealer_menu;
    private javax.swing.JCheckBoxMenuItem anim_apuestas_menu;
    private javax.swing.JCheckBoxMenuItem anim_contadores_menu;

    public static final int ANIM_REPARTO = 0;
    public static final int ANIM_CIEGAS_DEALER = 1;
    public static final int ANIM_APUESTAS = 2;
    public static final int ANIM_CONTADORES = 3;

    // Aplica el cambio de un efecto de animación (flag + persistencia) y refleja
    // el estado en AMBOS menús (menú-bar y popup del tapete). Al activar el
    // reparto re-calienta la caché del shuffle.gif (el warm-up de arranque pudo
    // saltárselo si estaba desactivado).
    public void setAnimEffect(int which, boolean value) {
        switch (which) {
            case ANIM_REPARTO:
                GameFrame.ANIMACION_REPARTO_PREF = value;
                Helpers.PROPERTIES.setProperty("animacion_reparto", String.valueOf(value));
                break;
            case ANIM_CIEGAS_DEALER:
                GameFrame.ANIMACION_CIEGAS_DEALER_PREF = value;
                Helpers.PROPERTIES.setProperty("animacion_ciegas_dealer", String.valueOf(value));
                break;
            case ANIM_APUESTAS:
                GameFrame.ANIMACION_APUESTAS_PREF = value;
                Helpers.PROPERTIES.setProperty("animacion_apuestas", String.valueOf(value));
                break;
            case ANIM_CONTADORES:
                GameFrame.ANIMACION_CONTADORES_PREF = value;
                Helpers.PROPERTIES.setProperty("animacion_contadores", String.valueOf(value));
                break;
        }
        Helpers.savePropertiesFile();
        syncAnimationMenus();
        if (which == ANIM_REPARTO && value) {
            Crupier.warmShuffleAnimCache();
        }
    }

    // Refleja las cuatro PREFERENCIAS en los ocho checkboxes (menú-bar + popup del tapete).
    public void syncAnimationMenus() {
        if (anim_reparto_menu != null) {
            anim_reparto_menu.setSelected(GameFrame.ANIMACION_REPARTO_PREF);
        }
        if (anim_ciegas_dealer_menu != null) {
            anim_ciegas_dealer_menu.setSelected(GameFrame.ANIMACION_CIEGAS_DEALER_PREF);
        }
        if (anim_apuestas_menu != null) {
            anim_apuestas_menu.setSelected(GameFrame.ANIMACION_APUESTAS_PREF);
        }
        if (anim_contadores_menu != null) {
            anim_contadores_menu.setSelected(GameFrame.ANIMACION_CONTADORES_PREF);
        }
        if (Helpers.TapetePopupMenu.ANIM_REPARTO_MENU != null) {
            Helpers.TapetePopupMenu.ANIM_REPARTO_MENU.setSelected(GameFrame.ANIMACION_REPARTO_PREF);
        }
        if (Helpers.TapetePopupMenu.ANIM_CIEGAS_DEALER_MENU != null) {
            Helpers.TapetePopupMenu.ANIM_CIEGAS_DEALER_MENU.setSelected(GameFrame.ANIMACION_CIEGAS_DEALER_PREF);
        }
        if (Helpers.TapetePopupMenu.ANIM_APUESTAS_MENU != null) {
            Helpers.TapetePopupMenu.ANIM_APUESTAS_MENU.setSelected(GameFrame.ANIMACION_APUESTAS_PREF);
        }
        if (Helpers.TapetePopupMenu.ANIM_CONTADORES_MENU != null) {
            Helpers.TapetePopupMenu.ANIM_CONTADORES_MENU.setSelected(GameFrame.ANIMACION_CONTADORES_PREF);
        }
    }

    // Habilita/deshabilita (SIN desmarcar) los toggles individuales -menu-bar + popup-
    // según el maestro: con el maestro off quedan en gris pero conservan su preferencia.
    // Ya NO recalcula ningún flag (los *_PREF son la preferencia cruda; el gate lo aplican
    // los helpers *On() en cada read-site). Lo llaman el arranque y setAnimacionesMaster.
    public void applyAnimationMaster() {
        boolean on = GameFrame.ANIMACIONES;

        anim_reparto_menu.setEnabled(on);
        anim_ciegas_dealer_menu.setEnabled(on);
        anim_apuestas_menu.setEnabled(on);
        anim_contadores_menu.setEnabled(on);
        menu_cinematicas.setEnabled(on);
        if (Helpers.TapetePopupMenu.ANIM_REPARTO_MENU != null) {
            Helpers.TapetePopupMenu.ANIM_REPARTO_MENU.setEnabled(on);
        }
        if (Helpers.TapetePopupMenu.ANIM_CIEGAS_DEALER_MENU != null) {
            Helpers.TapetePopupMenu.ANIM_CIEGAS_DEALER_MENU.setEnabled(on);
        }
        if (Helpers.TapetePopupMenu.ANIM_APUESTAS_MENU != null) {
            Helpers.TapetePopupMenu.ANIM_APUESTAS_MENU.setEnabled(on);
        }
        if (Helpers.TapetePopupMenu.ANIM_CONTADORES_MENU != null) {
            Helpers.TapetePopupMenu.ANIM_CONTADORES_MENU.setEnabled(on);
        }
        if (Helpers.TapetePopupMenu.CINEMATICAS_MENU != null) {
            Helpers.TapetePopupMenu.CINEMATICAS_MENU.setEnabled(on);
        }
    }

    // Maestro (Ajustes): enciende/apaga TODAS las animaciones de un plumazo (gate global; NO
    // toca las preferencias individuales) y lo persiste. applyAnimationMaster solo habilita/
    // deshabilita los toggles; el efecto lo aplican los helpers *On().
    public void setAnimacionesMaster(boolean value) {
        GameFrame.ANIMACIONES = value;
        Helpers.PROPERTIES.setProperty("animaciones", String.valueOf(value));
        Helpers.savePropertiesFile();
        applyAnimationMaster();
        if (value && GameFrame.ANIMACION_REPARTO_PREF) {
            Crupier.warmShuffleAnimCache();
        }
    }

    // Toggle de Apariencia: overlay de coste de igualar sobre las comunitarias.
    private javax.swing.JCheckBoxMenuItem coste_igualar_menu;

    public javax.swing.JCheckBoxMenuItem getCoste_igualar_menu() {
        return coste_igualar_menu;
    }

    // Aplica el cambio del toggle (flag + persistencia), refleja en ambos menús
    // (menú-bar y popup) y muestra/oculta el overlay de inmediato.
    public void setCosteIgualar(boolean value) {
        GameFrame.MOSTRAR_COSTE_IGUALAR = value;
        Helpers.PROPERTIES.setProperty("mostrar_coste_igualar", String.valueOf(value));
        Helpers.savePropertiesFile();
        if (coste_igualar_menu != null) {
            coste_igualar_menu.setSelected(value);
        }
        if (Helpers.TapetePopupMenu.COSTE_IGUALAR_MENU != null) {
            Helpers.TapetePopupMenu.COSTE_IGUALAR_MENU.setSelected(value);
        }
        if (getCrupier() != null) {
            getCrupier().refreshCallCostOverlay();
        }
    }

    // "Persistir AUTO entre manos": cuando está activo, la pulsación de un botón
    // AUTO sobrevive de una mano a la siguiente en vez de resetearse. Solo tiene
    // sentido (y solo se habilita) con "Botones AUTO" activo. Campo a mano +
    // sincronización menú↔popup, como los demás toggles.
    private javax.swing.JCheckBoxMenuItem auto_action_persist_menu;

    public javax.swing.JCheckBoxMenuItem getAuto_action_persist_menu() {
        return auto_action_persist_menu;
    }

    public void setAutoActionPersist(boolean value) {
        GameFrame.AUTO_ACTION_PERSIST = value;
        Helpers.PROPERTIES.setProperty("auto_action_persist", String.valueOf(value));
        Helpers.savePropertiesFile();
        if (auto_action_persist_menu != null) {
            auto_action_persist_menu.setSelected(value);
        }
        if (Helpers.TapetePopupMenu.AUTO_ACTION_PERSIST_MENU != null) {
            Helpers.TapetePopupMenu.AUTO_ACTION_PERSIST_MENU.setSelected(value);
        }
    }

    // Toggle del diálogo de confirmación MODO AUTO (cuenta atrás vetable antes de
    // cada acción automática). Solo se habilita con "Botones AUTO" activo.
    private javax.swing.JCheckBoxMenuItem modo_auto_confirm_menu;

    public javax.swing.JCheckBoxMenuItem getModo_auto_confirm_menu() {
        return modo_auto_confirm_menu;
    }

    public void setModoAutoConfirm(boolean value) {
        GameFrame.MODO_AUTO_CONFIRM = value;
        Helpers.PROPERTIES.setProperty("modo_auto_confirm", String.valueOf(value));
        Helpers.savePropertiesFile();
        if (modo_auto_confirm_menu != null) {
            modo_auto_confirm_menu.setSelected(value);
        }
        if (Helpers.TapetePopupMenu.MODO_AUTO_CONFIRM_MENU != null) {
            Helpers.TapetePopupMenu.MODO_AUTO_CONFIRM_MENU.setSelected(value);
        }
    }

    // Ítem de menú que abre el selector del máximo de auto-call. Solo se habilita
    // con "Botones AUTO" activo.
    private javax.swing.JMenuItem auto_call_menu;

    public javax.swing.JMenuItem getAuto_call_menu() {
        return auto_call_menu;
    }

    public void setAutoCall(boolean enabled, double value) {
        GameFrame.AUTO_CALL_ENABLED = enabled;
        GameFrame.AUTO_CALL_MAX = Math.max(0, value);
        Helpers.PROPERTIES.setProperty("auto_call_enabled", String.valueOf(enabled));
        Helpers.PROPERTIES.setProperty("auto_call_max", String.valueOf(GameFrame.AUTO_CALL_MAX));
        Helpers.savePropertiesFile();
        refreshAutoCallMenuText();
    }

    // Refresca el rótulo de "AUTO igualar" en la barra de menú y en el popup del
    // tapete para que muestre entre paréntesis si está ACTIVADO o DESACTIVADO
    // (reutiliza las claves del diálogo de auto-call). Se llama al construir el
    // menú y cada vez que cambia AUTO_CALL_ENABLED.
    public void refreshAutoCallMenuText() {
        String text = Translator.translate("menu.auto_call") + " ("
                + Translator.translate(GameFrame.AUTO_CALL_ENABLED ? "auto_call.activado" : "auto_call.desactivado") + ")";

        if (auto_call_menu != null) {
            auto_call_menu.setText(text);
        }

        if (Helpers.TapetePopupMenu.AUTO_CALL_MENU != null) {
            Helpers.TapetePopupMenu.AUTO_CALL_MENU.setText(text);
        }
    }

    // Abre el diálogo modal de AUTO CALL: checkbox Activado (on/off) + spinner del
    // límite (0 = sin límite, sin tope por arriba).
    public void openAutoCallMaxDialog() {
        // Por seguridad, al abrir el ajuste desarmamos cualquier pre-pulsado de los
        // botones AUTO: el umbral puede estar a punto de cambiar y no queremos que un
        // check/call ya armado dispare con el límite viejo si nos llega el turno con el
        // diálogo abierto.
        LocalPlayer lp = getLocalPlayer();
        if (lp != null) {
            lp.desPrePulsarAutoTodo();
        }

        AutoCallMaxDialog dlg = new AutoCallMaxDialog(this, GameFrame.AUTO_CALL_ENABLED, GameFrame.AUTO_CALL_MAX);
        dlg.setVisible(true);
        if (dlg.isAccepted()) {
            setAutoCall(dlg.isAutoCallEnabled(), dlg.getValue());
        }
    }

    public javax.swing.JCheckBoxMenuItem getAnim_reparto_menu() {
        return anim_reparto_menu;
    }

    public javax.swing.JCheckBoxMenuItem getAnim_ciegas_dealer_menu() {
        return anim_ciegas_dealer_menu;
    }

    public javax.swing.JCheckBoxMenuItem getAnim_apuestas_menu() {
        return anim_apuestas_menu;
    }

    public javax.swing.JCheckBoxMenuItem getAnim_contadores_menu() {
        return anim_contadores_menu;
    }

    public javax.swing.JCheckBoxMenuItem getAuto_rebuy_menu() {
        return auto_rebuy_menu;
    }

    public javax.swing.JMenuItem getAjustes_partida_menu() {
        return ajustes_partida_menu;
    }

    // Abre el diálogo unificado "Ajustes" (pestañas Apariencia / Audio / Partida).
    // Único punto de apertura para los tres accesos (menú Preferencias, popup del
    // tapete e icono del CommunityCardsPanel).
    public void openSettingsDialog() {
        SettingsDialog dialog = new SettingsDialog(this, true);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    // Recompra automática al arruinarse: preferencia LOCAL (no se difunde al host
    // ni cambia las reglas de la timba). Solo conmuta el flag y refleja el estado
    // en el checkbox gemelo del popup.
    private void auto_rebuy_menuActionPerformed(java.awt.event.ActionEvent evt) {
        GameFrame.AUTO_REBUY_ON_BROKE = auto_rebuy_menu.isSelected();
        Helpers.TapetePopupMenu.AUTO_REBUY_MENU.setSelected(auto_rebuy_menu.isSelected());
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem acerca_menu;
    private javax.swing.JCheckBoxMenuItem auto_action_menu;
    private javax.swing.JCheckBoxMenuItem auto_fit_zoom_menu;
    private javax.swing.JCheckBoxMenuItem auto_fullscreen_menu;
    private javax.swing.JCheckBoxMenuItem chat_image_menu;
    private javax.swing.JMenuItem chat_menu;
    private javax.swing.JCheckBoxMenuItem compact_menu;
    private javax.swing.JCheckBoxMenuItem confirmar_menu;
    private javax.swing.JPopupMenu.Separator decks_separator;
    private javax.swing.JMenuItem exit_menu;
    private javax.swing.JMenu file_menu;
    private javax.swing.JMenuItem force_reconnect_menu;
    private javax.swing.JMenuItem full_screen_menu;
    private javax.swing.JMenuItem halt_game_menu;
    private javax.swing.JMenu help_menu;
    private javax.swing.JPopupMenu.Separator jSeparator1;
    private javax.swing.JPopupMenu.Separator jSeparator10;
    private javax.swing.JPopupMenu.Separator jSeparator11;
    private javax.swing.JPopupMenu.Separator jSeparator2;
    private javax.swing.JPopupMenu.Separator jSeparator3;
    private javax.swing.JPopupMenu.Separator jSeparator4;
    private javax.swing.JPopupMenu.Separator jSeparator5;
    private javax.swing.JPopupMenu.Separator jSeparator6;
    private javax.swing.JPopupMenu.Separator jSeparator7;
    private javax.swing.JPopupMenu.Separator jSeparator8;
    private javax.swing.JPopupMenu.Separator jSeparator9;
    private javax.swing.JMenuItem jugadas_menu;
    private javax.swing.JCheckBoxMenuItem last_hand_menu;
    private javax.swing.JMenuItem max_hands_menu;
    private javax.swing.JMenuBar menu_bar;
    private javax.swing.JMenu menu_barajas;
    private javax.swing.JCheckBoxMenuItem menu_cinematicas;
    private javax.swing.JRadioButtonMenuItem menu_tapete_azul;
    private javax.swing.JRadioButtonMenuItem menu_tapete_madera;
    private javax.swing.JRadioButtonMenuItem menu_tapete_negro;
    private javax.swing.JRadioButtonMenuItem menu_tapete_rojo;
    private javax.swing.JRadioButtonMenuItem menu_tapete_verde;
    private javax.swing.JMenu menu_tapetes;
    private javax.swing.JMenu opciones_menu;
    private javax.swing.JCheckBoxMenuItem rebuy_now_menu;
    private javax.swing.JMenuItem registro_menu;
    private javax.swing.JMenuItem robert_rules_menu;
    private javax.swing.JPopupMenu.Separator server_separator_menu;
    private javax.swing.JMenuItem shortcuts_menu;
    private javax.swing.JCheckBoxMenuItem time_menu;
    private javax.swing.JMenu zoom_menu;
    private javax.swing.JMenuItem zoom_menu_in;
    private javax.swing.JMenuItem zoom_menu_out;
    private javax.swing.JMenuItem zoom_menu_reset;
    // End of variables declaration//GEN-END:variables
}
