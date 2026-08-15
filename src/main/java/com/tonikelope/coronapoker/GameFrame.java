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
// NetBeans form DISABLED: the matching .form was renamed to .form.bak on purpose.
// This class's initComponents (the generated //GEN block) is hand-edited (i18n keys via
// putClientProperty, DIALOG_ZOOM scaling, wrapped/translated tooltips and/or manual layout),
// none of which the .form carries. Opening this form in the NetBeans GUI designer and saving
// it would regenerate initComponents from the .form and silently wipe those edits. Maintain
// this class by hand and do NOT restore the .form (the original is kept in git history).
public final class GameFrame extends javax.swing.JFrame implements ZoomableInterface, MouseWheelListener {

    public static final int TEST_MODE_PAUSE = 250;
    // Factory zoom level: 0 = 100%, the size the board is designed for; also the
    // target of the zoom reset (Ctrl+0).
    public static final int DEFAULT_ZOOM_LEVEL = 0;
    public static final float ZOOM_STEP = 0.05f;

    public static final int WAIT_QUEUES = 250;
    public static final int WAIT_PAUSE = 1000;
    public static final int CLIENT_RECEPTION_TIMEOUT = 10000;
    public static final int CONFIRMATION_TIMEOUT = 10000;
    // P2P reconnection grace window (ms), independent of think time: disconnects are
    // detected via PING/PONG and socket timeouts (Participant.RECIBIDO_TIMEOUT = 45s =
    // MAX_CONSECUTIVE_PING_FAILURES * (PING_INTERVAL_MS + PING_PONG_TIMEOUT)), never by the
    // action bar. 80s gives slack over that ~45s detection window so a peer already
    // reconnecting can finish socket+handshake+HMAC before being given up on; also the
    // Reconnect2ServerDialog threshold on the client side.
    public static final int CLIENT_RECON_TIMEOUT = 80000; // 80 s
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
    public static volatile boolean FIXED_BUYIN = true; //true = everyone starts with BUYIN (rebuy cap = BUYIN); false = each player picks their buy-in when joining, in [BUYIN_MIN_BB, BUYIN_MAX_BB] BB (rebuy cap = BUYIN_MAX_BB BB)
    // Editable buy-in range (in big blinds). Defaults to 10-100 BB (historical range). The
    // host can widen it for deep-stack tables (up to BuyinRules.CEIL_MAX_BB). Bounds the
    // variable buy-in choice and, via the cap, the rebuy ceiling too. Travels to clients in
    // the INIT and is persisted on recover (see Crupier/WaitingRoomFrame and serializeRecoverSettings).
    public static volatile int BUYIN_MIN_BB = BuyinRules.DEFAULT_MIN_BB;
    public static volatile int BUYIN_MAX_BB = BuyinRules.DEFAULT_MAX_BB;
    // Rebuy/top-up cap policy:
    //  - BUYIN: the buy-in (fixed = BUYIN; variable = upper limit BUYIN_MAX_BB BB).
    //  - HIGHEST_STACK: the table's highest stack (rebuy up to match the chip leader,
    //    typical of deep-stack play).
    // Defaults to BUYIN (historical behaviour). Only affects rebuys, not the initial buy-in.
    // Travels in the INIT and is persisted on recover.
    public static final int REBUY_CAP_BUYIN = 0;
    public static final int REBUY_CAP_HIGHEST_STACK = 1;
    public static volatile int REBUY_CAP_POLICY = REBUY_CAP_BUYIN;
    public static volatile int CIEGAS_DOUBLE = 60;
    public static volatile int CIEGAS_DOUBLE_TYPE = 1; //1 MINUTES, 2 HANDS
    public static volatile double BLIND_CAP = 0; //0 = no cap; otherwise blinds don't double if the next level would push the big blind past this
    public static volatile boolean ANTE = false; //true = every active player posts an ante (= small blind) as dead money before the blinds (option A: traditional symmetric ante)
    public static volatile boolean STRADDLE = false; //true = UTG posts a mandatory live straddle (= 2x big blind), optional; disabled heads-up
    // null = default 1-2-3 x10^n ladder (legacy path in Crupier, infinite across decades).
    // non-null = custom blind structure (explicit {sb,bb} list); the ladder walks it by index
    // and caps at the last level. Chosen by the host when creating the game, travels to
    // clients and is persisted/restored on recover. See BlindStructure and
    // Crupier.doblarCiegas/simulateNextBlinds.
    public static volatile double[][] ACTIVE_BLIND_STRUCTURE = null;
    public static volatile boolean REBUY = true;
    public static volatile int REBUY_LIMIT = 0; //0 = no per-player rebuy limit; otherwise max times a player can rebuy in the game
    public static volatile boolean BOT_REBUY = true; //true = bots can rebuy (subject to the limit if > 0); false = bots sit out as spectators without asking the host
    // true = when the game ENDS, the bots' combined (signed) balance is dissolved out of the real
    // money settlement: all bots become neutral (stack := buyin) and that balance is split evenly
    // among the human players (the indivisible odd cent goes to a human chosen DETERMINISTICALLY,
    // identical on every peer), so real money only ever settles between people. Preserves total
    // money (the auditor stays balanced). Only affects the final settlement (ledger table +
    // balance screen), not the per-hand history. Off by default. See
    // Crupier.redistributeBotBalanceToHumans and GameFrame.finTransmision.
    public static volatile boolean BOT_BALANCE_TO_HUMANS = false;
    public static volatile boolean AUTO_REBUY_ON_BROKE = false; //true = the local human rebuys automatically when going broke (default amount, no dialog); LOCAL session preference, false by default
    public static volatile int MANOS = -1;
    public static volatile boolean IWTSTH_RULE = false;
    public static volatile int RABBIT_HUNTING = 0;
    // Think time (seconds) a player has to act on their turn, plus whether it's active.
    // Configurable per game (10-120) from creation and the waiting room; LOCKED once the
    // game has started. THINK_TIME_ENABLED=false => no time limit (static full bar, no
    // auto-fold on timeout). This governs ONLY the action timer: connection health
    // (PING/PONG, socket timeouts, RECIBIDO_TIMEOUT, CLIENT_RECON_TIMEOUT) is handled
    // separately (see Participant) and is unaffected by disabling think time; the host
    // still folds a player who genuinely DISCONNECTS via isExit().
    public static final int DEFAULT_THINK_TIME = 40; // seconds (initial value of THINK_TIME)
    public static volatile int THINK_TIME = DEFAULT_THINK_TIME;
    public static volatile boolean THINK_TIME_ENABLED = true;
    public static final int THINK_TIME_MIN = 10;  // seconds (spinner lower bound + clamp)
    public static final int THINK_TIME_MAX = 120; // seconds (spinner upper bound + clamp)
    // Showdown PAUSE duration (seconds): how long the hand result is shown, with its
    // countdown bar, before dealing the next hand (formerly Crupier.PAUSA_ENTRE_MANOS, which
    // also scales x0.5/x1.5 depending on side pots). Configurable per game (5-30) from
    // creation and the waiting room; LOCKED once the game has started. Unlike think time it
    // can NOT be disabled: there's always a pause (minimum 5s; default stays 10s).
    public static final int DEFAULT_SHOWDOWN_TIME = 10; // seconds (initial value of SHOWDOWN_TIME)
    public static volatile int SHOWDOWN_TIME = DEFAULT_SHOWDOWN_TIME;
    public static final int SHOWDOWN_TIME_MIN = 5;  // seconds (spinner lower bound + clamp)
    public static final int SHOWDOWN_TIME_MAX = 30;  // seconds (spinner upper bound + clamp)
    // Duration (ms) of the card flip animation (Swing render, CardFlipAnimator).
    public static final int DEFAULT_CARD_FLIP_DURATION = 620; // ~ the old GIF's duration (31 frames x 20 ms)
    public static final int CARD_FLIP_DURATION_MIN = 150;
    public static final int CARD_FLIP_DURATION_MAX = 1500;
    // Clamped to MIN/MAX, which until now were two declared-but-unused constants.
    public static volatile int CARD_FLIP_DURATION = Helpers.propInt("card_flip_duration", DEFAULT_CARD_FLIP_DURATION, CARD_FLIP_DURATION_MIN, CARD_FLIP_DURATION_MAX);
    // "Zoom in" effect: the flip animation renders at this percentage of the static card's
    // size (100 = off, pixel-perfect alignment; >100 gives the sense of the card approaching
    // the screen and settling to its real size as the flip finishes).
    public static final int DEFAULT_CARD_FLIP_ZOOM = 100; // off by default
    public static volatile int CARD_FLIP_ZOOM = Helpers.propInt("card_flip_zoom", DEFAULT_CARD_FLIP_ZOOM);
    public static final int HURRYUP_WARNING_SECONDS = 10; // "hurry up" warning (horn + blink) when this many seconds remain

    // Effective hurryup threshold, in seconds remaining. The action counter starts at
    // THINK_TIME and counts down by 1 each second, firing the warning exactly when it hits
    // this value, so it must ALWAYS be < THINK_TIME (a fixed threshold of 10 with
    // THINK_TIME=10 would start the counter already below it and the warning would never
    // fire). Formula: 25% of the remaining time rounded to an integer, capped at
    // HURRYUP_WARNING_SECONDS (10) — i.e. "at 40s or less it scales at 25% (40=>the classic
    // 10s; 20=>5; 10=>3), above 40 it stays fixed at 10", without hard-coding the cutoff: it
    // falls out of the cap and THINK_TIME alone, so it stays correct even if the spinner's
    // step, range or default change (THINK_TIME_MIN=10 guarantees threshold >= round(2.5)=3,
    // always < THINK_TIME).
    public static int getHurryupThreshold() {
        return Math.min(HURRYUP_WARNING_SECONDS, (int) Math.round(THINK_TIME * 0.25));
    }
    public static volatile boolean VOICE_MESSAGES = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("voice_messages", "true"));
    public static volatile boolean RUN_IT_TWICE = false;
    // Freezes changes to RUN_IT_TWICE during the all-in run-out (from the moment it starts
    // until NUEVA_MANO): the vote is decided by reading the flag without a lock, so it must
    // not change in that window. It used to be guaranteed by graying out the menu; now the
    // setter is a no-op and the "Game settings" dialog disables the control while it's active.
    public static volatile boolean RUN_IT_TWICE_LOCKED = false;
    public static volatile boolean SONIDOS = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonidos", "true")) && !TEST_MODE;
    public static volatile boolean SONIDOS_CHORRA = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonidos_chorra", "false"));
    // Music MASTER switch: turns off ALL background tracks at once (game, waiting room,
    // About, stats), same as SONIDO_EFECTOS for sound effects. Each track also keeps its own
    // toggle; a track plays only if MUSICA and its individual flag are both on (gated by
    // Audio.effectiveLoopVolume). On by default.
    public static volatile boolean MUSICA = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("musica", "true"));
    public static volatile boolean MUSICA_AMBIENTAL = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_ascensor", "true"));
    // Background track for the WAITING ROOM, with its own toggle (independent of the
    // in-game one, which is governed by MUSICA_AMBIENTAL). On by default.
    public static volatile boolean MUSICA_SALA = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("musica_sala_espera", "true"));
    // Background tracks for the "About" (about_music.mp3) and "Stats" (stats_music.mp3)
    // dialogs, each with its own toggle (independent of the rest of the music). On by
    // default; gated by Audio.effectiveLoopVolume just like MUSICA_AMBIENTAL/SALA.
    public static volatile boolean MUSICA_ABOUT = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("musica_about", "true"));
    public static volatile boolean MUSICA_STATS = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("musica_stats", "true"));
    // Configurable table sound effects (local, separately gated by the SONIDOS master in
    // the Audio layer). SONIDO_EFECTOS is this group's master switch: turns them all off at
    // once. Individually: shuffle (shuffle.wav), deal (deal.wav), uncover (uncover.wav) with
    // a "my cards" sub-option (only YOUR hole cards being dealt, depends on the general
    // uncover setting), bet (bet.wav), fold (fold.wav, the fold effect, NOT the SONIDOS_CHORRA
    // joke clips), final count (balance_count.wav from the end screen) and initial stack fill
    // (the same coin-counting clip). All on by default.
    public static volatile boolean SONIDO_EFECTOS = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_efectos", "true"));
    public static volatile boolean SONIDO_BARAJADO = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_barajado", "true"));
    public static volatile boolean SONIDO_REPARTO = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_reparto", "true"));
    public static volatile boolean SONIDO_DESTAPE = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_destape", "true"));
    public static volatile boolean SONIDO_DESTAPE_MIS_CARTAS = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_destape_mis_cartas", "false"));
    public static volatile boolean SONIDO_APOSTAR = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_apostar", "true"));
    public static volatile boolean SONIDO_FOLD = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_fold", "true"));
    public static volatile boolean SONIDO_CONTEO = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_conteo", "true"));
    public static volatile boolean SONIDO_CARGA_STACKS = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_carga_stacks", "true"));
    // Enter (laser.wav: create/join game, new participant, add bot) and leave (toilet.wav:
    // kicked/leaves the waiting room). Same "sound effects" group.
    public static volatile boolean SONIDO_ENTRA = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_entra", "true"));
    public static volatile boolean SONIDO_SALE = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_sale", "true"));
    // Switch click (button_on/off.wav): used by several UI toggles (table lights, scheduled
    // pause, pre-select action, position chip, arm rebuy). Cash register (cash_register.wav):
    // rebuy (stack top-up, animated or not) and dumping the indivisible leftover pot. Both in
    // the "sound effects" group.
    public static volatile boolean SONIDO_INTERRUPTOR = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_interruptor", "true"));
    public static volatile boolean SONIDO_CAJA = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_caja", "true"));
    // Remaining configurable table effects (same group): call (call.wav), check (check.wav),
    // all-in (allin.wav). Game events: blinds go up (double_blinds.wav), mark/unmark last hand
    // (last_hand_on/off.wav), pause (pause.wav). Room: someone requests to join the game
    // (new_user.wav). Turn/time: your turn (yourturn.wav) and "hurry up" warning
    // (hurryup.wav). All on by default.
    public static volatile boolean SONIDO_IGUALAR = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_igualar", "true"));
    public static volatile boolean SONIDO_PASAR = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_pasar", "true"));
    public static volatile boolean SONIDO_ALLIN = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_allin", "true"));
    public static volatile boolean SONIDO_CIEGAS = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_ciegas", "true"));
    public static volatile boolean SONIDO_ULTIMA_MANO = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_ultima_mano", "true"));
    public static volatile boolean SONIDO_PAUSA = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_pausa", "true"));
    public static volatile boolean SONIDO_ENTRAR_SALA = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_entrar_sala", "true"));
    public static volatile boolean SONIDO_TU_TURNO = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_tu_turno", "true"));
    public static volatile boolean SONIDO_AVISO_TIEMPO = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_aviso_tiempo", "true"));
    // Game over (game_over/nocontinue/rebuy). The first two are BLOCKING (they pace the final
    // screen): disabling them plays silently but still waits (Audio.playWavResourceAndWait
    // force_silent), it isn't skipped. On by default.
    public static volatile boolean SONIDO_FIN_PARTIDA = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_fin_partida", "true"));
    // Game start (startplay.wav: fanfare when starting/recovering a hand, Spanish-only),
    // server connection (yahoo.wav: connecting/reconnecting as a client) and the IWTSTH rule
    // (iwtsth.wav: BLOCKING cinematic when requesting to see an opponent's hand; silenced it
    // still waits via force_silent). On by default.
    public static volatile boolean SONIDO_INICIO = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_inicio", "true"));
    public static volatile boolean SONIDO_CONEXION = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_conexion", "true"));
    public static volatile boolean SONIDO_IWTSTH = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_iwtsth", "true"));
    // Interface/notification effects: zoom (zoom_in/out/reset.wav), change mat (mat.wav), card
    // viewer (card_visor.wav), change volume/device (volume_change.wav), app startup (init.wav,
    // BLOCKING -> force_silent), dialog warning (warning.wav), error (error.wav) and network
    // error (network_error_XX.wav). On by default.
    public static volatile boolean SONIDO_ZOOM = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_zoom", "true"));
    public static volatile boolean SONIDO_VISTA_COMPACTA = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_vista_compacta", "true"));
    public static volatile boolean SONIDO_SCREENSHOT = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_screenshot", "true"));
    public static volatile boolean SONIDO_TAPETE = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_tapete", "true"));
    public static volatile boolean SONIDO_VISOR = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_visor", "true"));
    public static volatile boolean SONIDO_VOLUMEN = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_volumen", "true"));
    public static volatile boolean SONIDO_ARRANQUE = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_arranque", "true"));
    public static volatile boolean SONIDO_AVISO = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_aviso", "true"));
    public static volatile boolean SONIDO_ERROR = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_error", "true"));
    public static volatile boolean SONIDO_ERROR_RED = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_error_red", "true"));
    public static volatile boolean AUTO_FULLSCREEN = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("auto_fullscreen", "true"));
    public static volatile boolean SHOW_CLOCK = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("show_time", "false"));
    public static volatile boolean CONFIRM_ACTIONS = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("confirmar_todo", "false")) && !TEST_MODE;
    // Unbounded: the zoom engine has no ceiling and the level can go negative (zoom out).
    public static volatile int ZOOM_LEVEL = Helpers.propInt("zoom_level", GameFrame.DEFAULT_ZOOM_LEVEL);
    public static volatile String BARAJA = Helpers.PROPERTIES.getProperty("baraja", BARAJA_DEFAULT);
    // Card back: "default" (follows the current deck) or the name of another deck whose
    // back is used instead. Since "default" tracks the deck, nothing needs resetting when
    // the deck changes.
    public static volatile String TRASERA = Helpers.PROPERTIES.getProperty("trasera", "default");
    // Its previous guard was isNumeric, which validates via Double.parseDouble: "1.5", "1e3"
    // or a value larger than an int would pass the filter and blow up later in Integer.parseInt.
    public static volatile int VISTA_COMPACTA = Helpers.propInt("vista_compacta", 0) % 4;
    // Animation effects, broken down by kind: dealing/uncovering cards, position chips
    // (blinds+dealer), pot chip (bets) and the rolling counters (stack/pot/bet plus the
    // fill wipe and rebuy). These 5 *_PREF flags hold each effect's RAW PREFERENCE (not the
    // effective value): the ANIMACIONES master gates them on every check via the *On()
    // helpers. Independently combinable; all four on by default. "animacion_reparto" keeps
    // its historical key.
    public static volatile boolean ANIMACION_REPARTO_PREF = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("animacion_reparto", "true"));
    public static volatile boolean ANIMACION_CIEGAS_DEALER_PREF = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("animacion_ciegas_dealer", "true"));
    public static volatile boolean ANIMACION_APUESTAS_PREF = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("animacion_apuestas", "true"));
    // Animated rolling of the LIVE in-game numeric counters (stack / pot). The final screen
    // (BalanceScreen) does NOT depend on this flag: it has its own (ANIMACION_CONTADOR_FINAL_PREF).
    public static volatile boolean ANIMACION_CONTADORES_PREF = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("animacion_contadores", "true"));
    // Animated count-up on the END-OF-GAME screen (BalanceScreen). Optional, on by default.
    // Its SFX (balance_count.wav) also depends on this flag, so turning off the count-up
    // silences it too.
    public static volatile boolean ANIMACION_CONTADOR_FINAL_PREF = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("animacion_contador_final", "true"));
    // Small, MUTED, looping shuffle-GIF overlay on each human RemotePlayer while it processes
    // ITS step of the SRA cascade shuffle — disappears once it moves to the next one. Since
    // the cascade is sequential and its remote step is a blocking call, the overlay lasts
    // EXACTLY as long as that client takes: useful for spotting the slowest PC at the table
    // at a glance. Purely visual: doesn't touch the cascade or the consensus. Off by default.
    public static volatile boolean ANIMACION_CASCADA_OVERLAY_PREF = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("animacion_cascada_overlay", "false"));
    // In the showdown, hovering the hand-strength label of a LOSER who showed highlights
    // (focuses) ONLY the cards that make up their hand (no kickers) and dims (unfocuses) ALL
    // other cards on the table, including the winner's; their label turns orange with white
    // text. On mouse-out the winner's highlight is restored as it was. Same focus/unfocus
    // mechanism the winner already uses. Purely visual and LOCAL per client (not broadcast).
    // Off by default.
    // Key migration: this used to be stored as "resaltar_jugada_perdedor" (losers only); that
    // old key is read as a fallback so existing users don't lose their setting.
    public static volatile boolean RESALTAR_JUGADA_SHOWDOWN = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("resaltar_jugada_showdown", Helpers.PROPERTIES.getProperty("resaltar_jugada_perdedor", "true")));
    // Hovering a seat's avatar shows it enlarged over the table next to that player's nick and
    // stack (AvatarZoomOverlay), and removes it on mouse-out. Purely visual and LOCAL per
    // client (not broadcast). OFF by default (covers part of the table).
    public static volatile boolean RESALTAR_AVATARES = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("resaltar_avatares", "false"));
    // Brightness left on the table with the lights OFF, in % (100 = no darkening). The black
    // veil BrightnessOverlay paints is its complement: 50% light = veil alpha 0.50. It used to
    // be hardcoded at 0.40 (equivalent to 60%), so the default "off" state is now a notch
    // darker. Purely visual and LOCAL per client (not broadcast). Applied uniformly by the
    // table's light switch, the shortcut and the automatic light-outs (pause, game over,
    // recover, initial buy-in).
    // The ceiling deliberately stops short of 100: "lights off" is recognized everywhere by
    // the veil being > 0 (the switch icon, the pause, the quick chat...), so a 100% light level
    // would make the off state indistinguishable from on.
    public static final int DEFAULT_NIVEL_LUZ = 50;
    public static final int NIVEL_LUZ_MIN = 10;
    public static final int NIVEL_LUZ_MAX = 90;
    // Clamped ON LOAD (not just when painting the veil), so the settings spinner, the value
    // re-persisted when discarding changes, and the veil always agree on the same number, even
    // if the key was hand-edited out of range.
    public static volatile int NIVEL_LUZ = Helpers.propInt("nivel_luz", DEFAULT_NIVEL_LUZ, NIVEL_LUZ_MIN, NIVEL_LUZ_MAX);
    // The final screen (BalanceScreen) automatically saves a screenshot (same mechanism as
    // Ctrl+P: printAll on the rootPane, no Robot or OS-level capture) RIGHT when the money
    // counter finishes; and if the player LEAVES the final screen BEFORE it finishes (via
    // either button, the menu, or continuing), it's taken at that point instead. One
    // screenshot per game (whichever happens first). LOCAL preference, OFF by default (can
    // pile up many screenshots); enabled in Appearance > Table.
    public static volatile boolean SCREENSHOT_FIN_TIMBA = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("screenshot_fin_timba", "false"));
    // The old "Cards" checkbox (animacion_reparto) used to govern SHUFFLE, DEAL and UNCOVER
    // all at once. It's now split into three independent preferences: "animacion_reparto"
    // keeps its key and now only governs the deal; "animacion_barajado" and "animacion_destape"
    // are new and MIGRATE from the historical "animacion_reparto" value the first time (to
    // honor the user's prior choice on upgrade).
    public static volatile boolean ANIMACION_BARAJADO_PREF = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("animacion_barajado", Helpers.PROPERTIES.getProperty("animacion_reparto", "true")));
    public static volatile boolean ANIMACION_DESTAPE_PREF = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("animacion_destape", Helpers.PROPERTIES.getProperty("animacion_reparto", "true")));
    // Animated swap of the local player's two hole cards when sorting the hand (high card to
    // the left): each card slides into the other's position (they cross). Off = plain swap, as
    // before. On by default.
    public static volatile boolean ANIMACION_SWAP_PREF = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("animacion_swap", "true"));
    // Duration (ms) of the swap crossing. 320 = normal (default).
    public static final int DEFAULT_SWAP_ANIM_DURATION = 320;
    public static volatile int SWAP_ANIM_DURATION = Helpers.propInt("swap_velocidad", DEFAULT_SWAP_ANIM_DURATION);
    // Crossing style: true = arced ("hop", one card arcs up and the other down); false =
    // straight horizontal slide (one passes in front of the other, default).
    public static volatile boolean SWAP_ANIM_ARC = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("swap_arco", "false"));
    // Animated table reflow when players leave (DynamicTablePanel): those who leave fade out
    // and survivors slide into their new seat on M's table. On by default. If off, hard cut
    // (direct board rebuild, as always).
    public static volatile boolean ANIMACION_DOWNGRADE_PREF = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("animacion_downgrade", "true"));
    // Duration (ms) of the reflow animation. 500 = normal (default).
    public static final int DEFAULT_DOWNGRADE_VELOCIDAD = 500;
    public static volatile int DOWNGRADE_VELOCIDAD = Helpers.propInt("downgrade_velocidad", DEFAULT_DOWNGRADE_VELOCIDAD);
    // Deal speed as a % of the base pause (Crupier.REPARTIR_PAUSA): 100 = normal (default,
    // historical speed), >100 slower, <100 faster.
    public static final int DEFAULT_REPARTO_VELOCIDAD = 100;
    public static volatile int REPARTO_VELOCIDAD = Helpers.propInt("reparto_velocidad", DEFAULT_REPARTO_VELOCIDAD);

    // Period (ms) of the Timer driving pre-rendered animations (shuffle, deal, chips, uncover),
    // FIXED at 2ms (~500 repaints/s). The real pace is set by the clock (System.nanoTime); this
    // only sets how often it re-evaluates which frame is due (the repaints/s ceiling). 2ms
    // oversamples WITH MARGIN (>=2x) any common refresh rate (60/75/144/240Hz): Swing's Timer
    // has NO vsync and a tick close to the refresh rate creates BEATING (a frame repeats while
    // another is skipped = stutter), so it deliberately runs FASTER than the refresh. No longer
    // configurable: the cost on slow PCs is trimmed by disabling animations or via the
    // Performance profile, not by lowering this frequency. If the OS scheduler can't deliver an
    // exact 2ms, the Timer runs at the finest tick it can (graceful degradation, never below any
    // refresh rate).
    public static final int ANIM_TICK_MS = 2;
    // Animation quality profile: Quality (default) vs Performance. Quality = the CURRENT
    // behaviour unchanged (rotation of flying cards/chips and SS=2 supersampling on uncover).
    // Performance (for weaker PCs) trims cost per frame: flights WITHOUT rotation and uncover
    // warp WITHOUT supersampling (SS=1). SAME frame count as Quality on uncover: reducing it
    // made the flip choppy, so Performance only lowers sharpness, not fluidity. Each lever is
    // gated so that with ANIM_CALIDAD=true the path is EXACTLY the historical one: zero
    // regression for anyone who doesn't touch it.
    public static final boolean DEFAULT_ANIM_CALIDAD = true;
    public static volatile boolean ANIM_CALIDAD = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("anim_calidad", String.valueOf(DEFAULT_ANIM_CALIDAD)));

    // Period (ms) of the pre-rendered animations' Timer. Read when each Timer is BUILT, not per
    // frame. A method (rather than the constant directly) to avoid touching TablePanel's 7 call sites.
    public static int getTickMs() {
        return ANIM_TICK_MS;
    }

    // P2P stats sync: two independent global preferences, both on by default. RECEIVE =
    // import games I'm missing when connecting to a server. SHARE = send my games the other
    // side is missing.
    public static volatile boolean SYNC_STATS_RECEIVE_PREF = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sync_stats_receive", "true"));
    public static volatile boolean SYNC_STATS_SHARE_PREF = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sync_stats_share", "true"));

    // SHARE exclusions: a subset of MY games left out of what I propagate, even with SHARE
    // on (applied in StatsSync.listShareableUgis). Private games on by default (historical
    // behaviour: private games were never shared). By-nick off by default: a comma-separated
    // nick list that excludes any game involving ANY of them.
    public static volatile boolean SYNC_STATS_EXCLUDE_PRIVATE_PREF = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sync_stats_exclude_private", "true"));
    public static volatile boolean SYNC_STATS_EXCLUDE_NICKS_ENABLED_PREF = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sync_stats_exclude_nicks_enabled", "false"));
    public static volatile String SYNC_STATS_EXCLUDE_NICKS_PREF = Helpers.PROPERTIES.getProperty("sync_stats_exclude_nicks", "");

    // Animation master: global GATE. The 5 *_PREF flags (CINEMATICAS_PREF and the 4
    // ANIMACION_*_PREF) hold each effect's raw preference, as before the master existed. This
    // flag gates them on every check via the *On() helpers (= ANIMACIONES && preference):
    // turning it off disables ALL animations at once WITHOUT touching the preferences.
    // applyAnimationMaster no longer recomputes the flags (there's no stored "effective"
    // value); it only enables/disables (visually) the individual toggles when off. On by default.
    public static volatile boolean ANIMACIONES = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("animaciones", "true"));

    // Per-effect gate: an individual animation applies ONLY if the master is on AND its own
    // preference is on. Every read site deciding whether to ANIMATE uses these helpers, never
    // the raw *_PREF flag (which is just the preference, ungated).
    public static boolean cinematicasOn() {
        return ANIMACIONES && CINEMATICAS_PREF;
    }

    // Cinematic subtypes hanging off the "Cinematics" master (same way the cascade hangs off
    // "Shuffle"): ACTION ones (opponents' fold/call/check/bet/raise GIFs), ALL-IN ones
    // (fullscreen sequence) and GAME OVER (the busted-out GIFs while deciding a rebuy). Each
    // one applies only if the animations master, the cinematics master AND its own preference
    // are all on.
    public static boolean cinematicasAccionOn() {
        return cinematicasOn() && CINEMATICAS_ACCION_PREF;
    }

    public static boolean cinematicasAllinOn() {
        return cinematicasOn() && CINEMATICAS_ALLIN_PREF;
    }

    // GAME OVER: governs the THREE rebuy-cycle visuals, which must always stay in sync — the
    // local GameOverDialog GIF, the GIF over busted remote players' cards
    // (RemotePlayer.setRebuying) and the dealer's time bar while waiting for REBUYS
    // (Crupier.recibirRebuys). Off: static "GAME OVER" banner with countdown, numeric "REBUY?
    // (N)" countdown in the action label, and a smooth bar.
    public static boolean cinematicasGameOverOn() {
        return cinematicasOn() && CINEMATICAS_GAMEOVER_PREF;
    }

    public static boolean repartoAnimOn() {
        return ANIMACIONES && ANIMACION_REPARTO_PREF;
    }

    public static boolean barajadoAnimOn() {
        return ANIMACIONES && ANIMACION_BARAJADO_PREF;
    }

    public static boolean destapeAnimOn() {
        return ANIMACIONES && ANIMACION_DESTAPE_PREF;
    }

    public static boolean swapAnimOn() {
        return ANIMACIONES && ANIMACION_SWAP_PREF;
    }

    public static boolean downgradeAnimOn() {
        return ANIMACIONES && ANIMACION_DOWNGRADE_PREF;
    }

    // One-shot migration of the old "Cards" checkbox (animacion_reparto), split into
    // shuffle/deal/uncover. ANIMACION_BARAJADO_PREF and ANIMACION_DESTAPE_PREF inherit its value
    // the first time by reading animacion_reparto as a fallback; but that fallback STAYS LIVE as
    // long as their own keys don't exist in the file, so a later change to animacion_reparto
    // (e.g. from the Deal menu item) would drag them along again on the next startup. This
    // method persists the new keys with their already-migrated value ONCE, breaking the link.
    // Idempotent: doesn't touch a key that already exists. Called by startup (Init) before warm-up.
    public static void migrateSplitAnimationPrefs() {
        boolean changed = false;
        if (Helpers.PROPERTIES.getProperty("animacion_barajado") == null) {
            Helpers.PROPERTIES.setProperty("animacion_barajado", String.valueOf(ANIMACION_BARAJADO_PREF));
            changed = true;
        }
        if (Helpers.PROPERTIES.getProperty("animacion_destape") == null) {
            Helpers.PROPERTIES.setProperty("animacion_destape", String.valueOf(ANIMACION_DESTAPE_PREF));
            changed = true;
        }
        if (changed) {
            Helpers.savePropertiesFile();
        }
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

    // Animated count-up on the end-of-game screen. Its SFX (balance_count.wav) depends on this
    // gate IN ADDITION TO conteoSonidoOn, so turning off the count-up also silences its sound.
    public static boolean contadorFinalAnimOn() {
        return ANIMACIONES && ANIMACION_CONTADOR_FINAL_PREF;
    }

    // The SRA cascade is a sub-setting of SHUFFLE: only applies if the shuffle animation is on.
    public static boolean cascadaOverlayAnimOn() {
        return ANIMACIONES && ANIMACION_BARAJADO_PREF && ANIMACION_CASCADA_OVERLAY_PREF;
    }

    // Boolean gates for the table sound effects: each depends on the SONIDO_EFECTOS master
    // (separate from the global SONIDOS master, which turns them all off in the Audio layer).
    // "My cards" also depends on the general uncover setting.
    public static boolean barajadoSonidoOn() {
        return SONIDO_EFECTOS && SONIDO_BARAJADO;
    }

    public static boolean repartoSonidoOn() {
        return SONIDO_EFECTOS && SONIDO_REPARTO;
    }

    public static boolean destapeSonidoOn() {
        return SONIDO_EFECTOS && SONIDO_DESTAPE;
    }

    public static boolean destapeMisCartasSonidoOn() {
        return SONIDO_EFECTOS && SONIDO_DESTAPE && SONIDO_DESTAPE_MIS_CARTAS;
    }

    public static boolean apuestaSonidoOn() {
        return SONIDO_EFECTOS && SONIDO_APOSTAR;
    }

    public static boolean foldSonidoOn() {
        return SONIDO_EFECTOS && SONIDO_FOLD;
    }

    public static boolean conteoSonidoOn() {
        return SONIDO_EFECTOS && SONIDO_CONTEO;
    }

    public static boolean cargaStacksSonidoOn() {
        return SONIDO_EFECTOS && SONIDO_CARGA_STACKS;
    }

    public static boolean entraSonidoOn() {
        return SONIDO_EFECTOS && SONIDO_ENTRA;
    }

    public static boolean saleSonidoOn() {
        return SONIDO_EFECTOS && SONIDO_SALE;
    }

    public static boolean interruptorSonidoOn() {
        return SONIDO_EFECTOS && SONIDO_INTERRUPTOR;
    }

    public static boolean cajaSonidoOn() {
        return SONIDO_EFECTOS && SONIDO_CAJA;
    }

    public static boolean igualarSonidoOn() {
        return SONIDO_EFECTOS && SONIDO_IGUALAR;
    }

    public static boolean pasarSonidoOn() {
        return SONIDO_EFECTOS && SONIDO_PASAR;
    }

    public static boolean allinSonidoOn() {
        return SONIDO_EFECTOS && SONIDO_ALLIN;
    }

    public static boolean ciegasSonidoOn() {
        return SONIDO_EFECTOS && SONIDO_CIEGAS;
    }

    public static boolean ultimaManoSonidoOn() {
        return SONIDO_EFECTOS && SONIDO_ULTIMA_MANO;
    }

    public static boolean pausaSonidoOn() {
        return SONIDO_EFECTOS && SONIDO_PAUSA;
    }

    public static boolean entrarSalaSonidoOn() {
        return SONIDO_EFECTOS && SONIDO_ENTRAR_SALA;
    }

    public static boolean tuTurnoSonidoOn() {
        return SONIDO_EFECTOS && SONIDO_TU_TURNO;
    }

    public static boolean avisoTiempoSonidoOn() {
        return SONIDO_EFECTOS && SONIDO_AVISO_TIEMPO;
    }

    public static boolean finPartidaSonidoOn() {
        return SONIDO_EFECTOS && SONIDO_FIN_PARTIDA;
    }

    public static boolean inicioSonidoOn() {
        return SONIDO_EFECTOS && SONIDO_INICIO;
    }

    public static boolean conexionSonidoOn() {
        return SONIDO_EFECTOS && SONIDO_CONEXION;
    }

    public static boolean iwtsthSonidoOn() {
        return SONIDO_EFECTOS && SONIDO_IWTSTH;
    }

    public static boolean zoomSonidoOn() {
        return SONIDO_EFECTOS && SONIDO_ZOOM;
    }

    public static boolean vistaCompactaSonidoOn() {
        return SONIDO_EFECTOS && SONIDO_VISTA_COMPACTA;
    }

    public static boolean screenshotSonidoOn() {
        return SONIDO_EFECTOS && SONIDO_SCREENSHOT;
    }

    public static boolean tapeteSonidoOn() {
        return SONIDO_EFECTOS && SONIDO_TAPETE;
    }

    public static boolean visorSonidoOn() {
        return SONIDO_EFECTOS && SONIDO_VISOR;
    }

    public static boolean volumenSonidoOn() {
        return SONIDO_EFECTOS && SONIDO_VOLUMEN;
    }

    public static boolean arranqueSonidoOn() {
        return SONIDO_EFECTOS && SONIDO_ARRANQUE;
    }

    public static boolean avisoSonidoOn() {
        return SONIDO_EFECTOS && SONIDO_AVISO;
    }

    public static boolean errorSonidoOn() {
        return SONIDO_EFECTOS && SONIDO_ERROR;
    }

    public static boolean errorRedSonidoOn() {
        return SONIDO_EFECTOS && SONIDO_ERROR_RED;
    }

    // Table wav paths gated by their preference (null = no sound, which
    // flyCardToSeat/playCardFlipOverlays/showCentralFrames/showCentralFramesLoop already
    // treat as "silent").
    public static String dealSound() {
        return repartoSonidoOn() ? "misc/deal.wav" : null;
    }

    public static String uncoverSound() {
        return destapeSonidoOn() ? "misc/uncover.wav" : null;
    }

    public static String uncoverMyCardsSound() {
        return destapeMisCartasSonidoOn() ? "misc/uncover.wav" : null;
    }

    public static String shuffleSound() {
        return barajadoSonidoOn() ? "misc/shuffle.wav" : null;
    }

    // Gated cash-register path (null = no sound, which animateStackFill treats as "silent"):
    // used by the animated stack fill on rebuy.
    public static String cashRegisterSound() {
        return cajaSonidoOn() ? "misc/cash_register.wav" : null;
    }

    // Coin-counting clip used by the opening stack-fill animation. It is deliberately a
    // separate preference from the identical clip used by the end-of-game count.
    public static String initialStackFillSound() {
        return cargaStacksSonidoOn() ? "misc/balance_count.wav" : null;
    }

    // ---- Per-player shuffle overlay controller (synchronized via the SHUFFLE_TURN command)
    // The host broadcasts SHUFFLE_TURN#nick for each player in the ring as the cascade
    // shuffle advances; every peer (including the host, which self-applies on emit) delivers
    // it here. This controller paints the overlay+border over THAT nick's player (local or
    // remote), one turn at a time, with a MINIMUM DURATION: on a LAN each step takes ~100ms and
    // without the minimum the flip would be an imperceptible blink; a slow client keeps its
    // overlay for the real duration of its step (making it easy to spot). Gated by each peer's
    // LOCAL preference (cascadaOverlayAnimOn).
    public static final long SHUFFLE_OVERLAY_MIN_MS = 150;    // visible floor per turn DURING the shuffle (real steps > 150ms run at their EXACT duration)
    public static final long SHUFFLE_OVERLAY_DRAIN_MS = 60;   // once FINISHED, drain whatever's left quickly (don't overlap with the deal)
    public static final long SHUFFLE_OVERLAY_WATCHDOG_MS = 60000; // safety net if SHUFFLE_TURN_END never arrives (host down)
    private final java.util.concurrent.ConcurrentLinkedQueue<String> shuffle_turn_queue = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final Object shuffle_turn_lock = new Object();
    private volatile boolean shuffle_turn_ended = false;
    private volatile boolean shuffle_turn_playing = false;
    private volatile String shuffle_overlay_current_nick = null;

    /**
     * Cascade turn for {@code nick}: queues its overlay. Invoked by the host (locally when
     * emitting) and by each client (on receiving SHUFFLE_TURN). Gated by this peer's LOCAL
     * preference, so each user decides whether to see the animation even though the host
     * always broadcasts it.
     */
    public void onShuffleTurn(String nick) {
        if (nick == null || nick.isEmpty() || !cascadaOverlayAnimOn()) {
            return;
        }
        shuffle_turn_ended = false;
        shuffle_turn_queue.add(nick);
        startShuffleTurnPlayer();
    }

    /**
     * End of the shuffle: the player thread drains the pending queue and hides the overlay.
     */
    public void onShuffleTurnEnd() {
        shuffle_turn_ended = true;
    }

    // Starts (if not already running) the thread that plays the turn queue at MINIMUM
    // DURATION. Only one player at a time (guarded by shuffle_turn_lock). Restarts if a turn
    // arrives right in the window between deciding to exit and setting playing=false.
    private void startShuffleTurnPlayer() {
        synchronized (shuffle_turn_lock) {
            if (shuffle_turn_playing) {
                return;
            }
            shuffle_turn_playing = true;
        }
        Helpers.threadRun(() -> {
            long last_activity = System.currentTimeMillis();
            try {
                while (true) {
                    String nick = shuffle_turn_queue.poll();
                    if (nick != null) {
                        setShuffleOverlayOn(nick);
                        last_activity = System.currentTimeMillis();
                        // During the shuffle, each turn lasts the visible minimum. Once the
                        // shuffle has ENDED, whatever's left is drained quickly so it doesn't
                        // overlap with dealing/betting (GIF over already-dealt cards / stomping
                        // on the turn border).
                        Helpers.pausar(shuffle_turn_ended ? SHUFFLE_OVERLAY_DRAIN_MS : SHUFFLE_OVERLAY_MIN_MS);
                    } else if (shuffle_turn_ended) {
                        break; // empty queue + shuffle finished
                    } else if (System.currentTimeMillis() - last_activity > SHUFFLE_OVERLAY_WATCHDOG_MS) {
                        break; // safety net: END never arrived (host down?) -> don't spin forever
                    } else {
                        // Empty queue but the shuffle is still going: the current player
                        // (typically a slow remote) keeps its overlay until the next turn arrives.
                        Helpers.pausar(40);
                    }
                }
            } finally {
                hideShuffleOverlayAll();
                shuffle_overlay_current_nick = null;
                synchronized (shuffle_turn_lock) {
                    shuffle_turn_playing = false;
                }
                if (!shuffle_turn_queue.isEmpty() && !shuffle_turn_ended) {
                    startShuffleTurnPlayer();
                }
            }
        });
    }

    // Hides the previous player's overlay and shows it on the new one (both on its own thread,
    // which does GUIRun internally). Runs on the player thread, NOT on the EDT.
    private void setShuffleOverlayOn(String nick) {
        String prev = shuffle_overlay_current_nick;
        if (prev != null && !prev.equals(nick)) {
            Player pp = findPlayerByNick(prev);
            if (pp != null) {
                pp.hideShuffleCascadeOverlay();
            }
        }
        shuffle_overlay_current_nick = nick;
        Player np = findPlayerByNick(nick);
        if (np != null) {
            np.showShuffleCascadeOverlay();
        }
    }

    private void hideShuffleOverlayAll() {
        // Snapshot: iterated from the player thread while the list can be rebuilt (clear+addAll)
        // when the table shrinks between hands -> avoid ConcurrentModificationException.
        for (Player j : new java.util.ArrayList<>(getJugadores())) {
            if (j != null) {
                j.hideShuffleCascadeOverlay();
            }
        }
    }

    private Player findPlayerByNick(String nick) {
        if (nick == null) {
            return null;
        }
        for (Player j : new java.util.ArrayList<>(getJugadores())) {
            if (j != null && nick.equals(j.getNickname())) {
                return j;
            }
        }
        return null;
    }

    // Speed/limits for rolling the LIVE counters (player stack/pot, main pot) during play.
    // CONSTANT SPEED (linear): each segment's duration = distance/speed, clamped to
    // [min, max]. Levers to tame the extremes (huge changes don't take forever, tiny ones
    // aren't instantaneous).
    public static final double COUNTER_ROLL_SPEED = 3000.0; // money/second
    public static final long COUNTER_ROLL_MIN_MS = 120;
    public static final long COUNTER_ROLL_MAX_MS = 900;

    // Rolling the all-in probability % (HAND + PROB in the action label). Unlike the live
    // counters (constant speed), this runs at CONSTANT TIME: each street change rolls over a
    // fixed PROB_ROLL_MS regardless of how much the % changes, so ALL probabilities reach
    // their value at the same time (start and finish together).
    public static final long PROB_ROLL_MS = 150;

    // Gate for rolling the LIVE counters: respects the Settings option and is skipped on
    // recover (recovered values snap in at once, unanimated). The fill-wipe/rebuy gate is
    // Crupier.isStackFillAnimated (adds end-of-transmission).
    public static boolean isCounterRollEnabled() {
        if (!contadoresAnimOn() || RECOVER) {
            return false;
        }
        // !RECOVER covers the recover itself; game_recovered==0 covers the REPLAY of a
        // recovered hand (RECOVER is already false but the hand re-runs), so counters SNAP
        // instead of animating during that startup replay.
        GameFrame gf = getInstance();
        return gf == null || gf.getCrupier() == null || gf.getCrupier().getGame_recovered() == 0;
    }
    // Optional overlay on the community cards showing the local player's call cost (how
    // much they'll have to put in on their turn). On by default.
    public static volatile boolean MOSTRAR_COSTE_IGUALAR = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("mostrar_coste_igualar", "true"));
    public static volatile boolean AUTO_ACTION_BUTTONS = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("auto_action_buttons", "false")) && !TEST_MODE;
    // If on, a pressed AUTO button survives across hands instead of resetting (only applies
    // with AUTO_ACTION_BUTTONS on). On by default.
    public static volatile boolean AUTO_ACTION_PERSIST = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("auto_action_persist", "true"));
    // If on, a modal countdown dialog (AUTO MODE) is shown before executing a pre-selected
    // automatic action, allowing it to be vetoed.
    public static volatile boolean MODO_AUTO_CONFIRM = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("modo_auto_confirm", "true"));
    // Seconds for the AUTO MODE dialog's bar.
    public static final int AUTO_CONFIRM_SECONDS = 5;
    // Automatic auto-call. With AUTO_CALL_ENABLED, the pre-selected check/call calls any bet
    // whose REAL cost (what you actually put in = the stack when calling forces an all-in) is
    // <= AUTO_CALL_MAX, on any street (generalizes the old "+BB"). AUTO_CALL_MAX == 0 = NO
    // LIMIT (calls any amount). Only applies with AUTO_ACTION_BUTTONS on. In chips (the
    // engine's smallest chip is the cent, 0.01).
    public static volatile boolean AUTO_CALL_ENABLED = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("auto_call_enabled", "false"));
    public static volatile double AUTO_CALL_MAX = Helpers.propDouble("auto_call_max", 0.0);
    public static volatile String COLOR_TAPETE = Helpers.PROPERTIES.getProperty("color_tapete", "verde");
    public static volatile String LANGUAGE = Helpers.PROPERTIES.getProperty("lenguaje", "es").toLowerCase();
    public static volatile boolean CINEMATICAS_PREF = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("cinematicas", "true"));
    // "Cinematics" sub-settings: each subtype's raw preference (gated by CINEMATICAS_PREF via
    // cinematicasAccionOn()/cinematicasAllinOn()/cinematicasGameOverOn()). On by default (no
    // behaviour change).
    public static volatile boolean CINEMATICAS_ACCION_PREF = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("cinematicas_accion", "true"));
    public static volatile boolean CINEMATICAS_ALLIN_PREF = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("cinematicas_allin", "true"));
    public static volatile boolean CINEMATICAS_GAMEOVER_PREF = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("cinematicas_gameover", "true"));
    public static volatile boolean CHAT_IMAGES_INGAME = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("chat_images_ingame", "true"));
    public static volatile boolean AUTO_ZOOM = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("auto_zoom", "false"));
    // Local player's position chip over their cards: 3 states cycled by click (persisted):
    // 0=normal, 1=70% opacity, 2=hidden. parseLocalPosChipState migrates the old boolean
    // "true"/"false" value from previous versions (true->normal, false->hidden).
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
    // Anti-double-action guard: when a keyboard overlay (straddle / AUTO MODE) is resolved with
    // ESC/SPACE, repeats of the SAME held key (OS auto-repeat) and immediate re-presses are
    // swallowed, so ESC doesn't end up FOLDING right after canceling AUTO MODE (its cancel
    // re-enables the action buttons and removes the overlay just in time for the next event to
    // land on a normal fold). Cleared on key RELEASE (KEY_RELEASED in the dispatcher).
    private volatile boolean kbd_overlay_swallow_esc = false;
    private volatile boolean kbd_overlay_swallow_space = false;
    private static final Object ZOOM_LOCK = new Object();

    private static volatile GameFrame THIS = null;

    // Shutdown hook triggered when the JVM terminates via SIGINT (Ctrl+C), SIGTERM, console
    // close (Windows CTRL_CLOSE_EVENT, ~5s before TerminateProcess), or any abrupt exit other
    // than closing the game window.
    //
    // - Client: sends "EXIT#<testament>" to the host. Without this, an abrupt client crash
    //   mid-SRA-cascade triggered a MISDEAL on the table (sra_unlock never arrived). With the
    //   hook, the host applies the testament and the hand ends without a MISDEAL.
    // - Host: broadcasts SERVEREXIT to all clients. The game can't continue without a host, so
    //   we don't use SERVEREXITRECOVER (that path would open the client's recover dialog,
    //   waiting to reconnect to a host that no longer exists). SERVEREXIT sends the client back
    //   to the normal lobby: a clean game over. If the user wants to recover they do it
    //   manually from the menu. Without the hook, clients would see the host as down and enter
    //   reconectarCliente, retrying a nonexistent server until the modal dialog appeared at 80s.
    private static volatile Thread SHUTDOWN_HOOK_THREAD = null;

    /**
     * Registers the shutdown hook if not already registered. The hook is idempotent,
     * self-checking (does nothing if the game already ended), and internally distinguishes
     * host from client.
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
                    return; // Game already ended cleanly.
                }
                if (!wrf.isPartida_empezada()) {
                    return; // Nothing to send (we're in the lobby/waiting room).
                }

                if (gf.isPartida_local()) {
                    // --- HOST: broadcast SERVEREXIT to all clients ---
                    // The game dies with the host (there's no one to reconnect to), so we send
                    // SERVEREXIT (clean game over), NOT SERVEREXITRECOVER (that path opens the
                    // client's recover dialog waiting to reconnect to a nonexistent host).
                    // Confirmation=false: fire-and-forget. We can't wait for a CONF during
                    // shutdown (Windows kills us after 5s).
                    try {
                        c.broadcastGAMECommandFromServer("SERVEREXIT", null, false);
                    } catch (Throwable ignored) {
                    }
                    return;
                }

                // --- CLIENT: send EXIT#testament to the host ---
                NetClient nc = wrf.getNet_client();
                if (nc == null || nc.isReconnecting()) {
                    return; // If we were already reconnecting, the server ALREADY knows we're down.
                }
                java.net.Socket s = nc.getLocal_client_socket();
                if (s == null || s.isClosed()) {
                    return;
                }
                // Builds the command directly to avoid re-entering sendGAMECommandToServer
                // (a do-while with waits that during shutdown could hang us past the 5s
                // timeout Windows gives on console close).
                String testamento;
                try {
                    testamento = c.getTestamentoCriptografico();
                } catch (Throwable ex) {
                    testamento = "*"; // No valid testament: better a bare EXIT than nothing.
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
                // Silent hook: if anything fails during shutdown, we fall back to the
                // no-hook behaviour (clients detect the host going down via a null read and
                // enter reconectarCliente until the modal dialog at 80s; a client dying
                // without a testament -> possible MISDEAL if we were mid-SRA-cascade). Not a
                // regression.
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
     * Unregisters the shutdown hook once the game ends cleanly (finTransmision), so no hook
     * is left dangling trying to send EXIT over a socket already closed after returning to
     * the lobby.
     */
    public static void unregisterShutdownHook() {
        Thread h = SHUTDOWN_HOOK_THREAD;
        if (h != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(h);
            } catch (IllegalStateException ignored) {
                // JVM already shutting down: can't unregister, doesn't matter anymore.
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

    // Clears ONLY the singleton reference, without the full resetInstance() teardown (which assumes
    // a fully-built frame and would NPE against a half-built one). The constructor publishes
    // THIS = this as its first statement, so a construction that throws partway leaves a half-built
    // instance visible to getInstance(). Call this on that failure so getInstance() honestly reports
    // "no game" (getInstance()!=null checks elsewhere then treat it as fresh-start) until the next
    // new GameFrame(...) attempt overwrites THIS.
    public static void clearFailedInstance() {
        THIS = null;
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
                + "#BOTBAL=" + (BOT_BALANCE_TO_HUMANS ? "1" : "0")
                + "#RUNITWICE=" + (runittwice ? "1" : "0")
                + "#VOICEMSG=" + (voicemsg ? "1" : "0")
                + "#TTS=" + (tts ? "1" : "0")
                + "#FIXED_BUYIN=" + (FIXED_BUYIN ? "1" : "0")
                // Custom blind structure (CSV sb/bb, no '#'/'='; empty = default ladder).
                // Essential so the ladder and the post-recover INIT re-broadcast use the same list.
                + "#BLINDS=" + (ACTIVE_BLIND_STRUCTURE != null ? BlindStructure.levelsToString(ACTIVE_BLIND_STRUCTURE) : "")
                // Editable buy-in range (in big blinds).
                + "#BMINBB=" + BUYIN_MIN_BB
                + "#BMAXBB=" + BUYIN_MAX_BB
                // Rebuy cap policy (0=BUYIN, 1=highest stack).
                + "#RBCAP=" + REBUY_CAP_POLICY
                + "#ANTE=" + (ANTE ? "1" : "0")
                + "#STRADDLE=" + (STRADDLE ? "1" : "0")
                // Hand limit + think time: "Game" settings, EDITABLE on recover (persisted so
                // the control starts at the recovered game's value).
                + "#MANOS=" + MANOS
                + "#THINKT=" + THINK_TIME
                + "#THINKON=" + (THINK_TIME_ENABLED ? "1" : "0")
                + "#SHOWDOWN=" + SHOWDOWN_TIME;
    }

    public static void applyRecoverSettings(String serialized) {
        // Clean slate: recovery ALWAYS starts from the default ladder. If the recovered row
        // doesn't carry a BLINDS key (a game from before this feature), ACTIVE deterministically
        // stays null instead of carrying over a custom structure left active by another game
        // in this session.
        ACTIVE_BLIND_STRUCTURE = null;
        // Same rule for the buy-in range and the rebuy cap policy: a row from before this
        // feature carries no BMINBB/BMAXBB/RBCAP, so we ALWAYS start from the defaults and
        // never carry over a stale range/policy from another game open in this same session.
        BUYIN_MIN_BB = BuyinRules.DEFAULT_MIN_BB;
        BUYIN_MAX_BB = BuyinRules.DEFAULT_MAX_BB;
        REBUY_CAP_POLICY = REBUY_CAP_BUYIN;
        // Same rule for ante/straddle: a row from before this feature carries no such keys, so
        // we ALWAYS start off instead of carrying over stale state from another game open in
        // this same session.
        ANTE = false;
        STRADDLE = false;
        // Hand limit + think time: a row from before this feature carries no such keys, so we
        // start from their defaults (no limit / enabled at DEFAULT_THINK_TIME).
        MANOS = -1;
        THINK_TIME = DEFAULT_THINK_TIME;
        THINK_TIME_ENABLED = true;
        SHOWDOWN_TIME = DEFAULT_SHOWDOWN_TIME;
        // Splitting bot balance among humans: a row from before this feature carries no such
        // key, so we ALWAYS start off instead of carrying over stale state from another game
        // open in this same session.
        BOT_BALANCE_TO_HUMANS = false;
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
                case "BOTBAL":
                    BOT_BALANCE_TO_HUMANS = "1".equals(val);
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
                case "MANOS":
                    try {
                        MANOS = Integer.parseInt(val);
                    } catch (NumberFormatException ignore) {
                    }
                    break;
                case "THINKT":
                    try {
                        THINK_TIME = Integer.parseInt(val);
                    } catch (NumberFormatException ignore) {
                    }
                    break;
                case "THINKON":
                    THINK_TIME_ENABLED = "1".equals(val);
                    break;
                case "SHOWDOWN":
                    try {
                        SHOWDOWN_TIME = Integer.parseInt(val);
                    } catch (NumberFormatException ignore) {
                    }
                    break;
                case "BLINDS":
                    // Empty = default ladder (null). Defensive parse: if the stored list were
                    // corrupt, fall back to default instead of aborting the recover (the
                    // engine would just continue with the 1-2-3-5 ladder).
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
            // Iterate over a COPY: the dealer clears and refills the player list while
            // rebuilding the table (a player leaves, a new board), and iterating it live while
            // that happens breaks the iteration. This feeds the rebuy cap, so the failure
            // surfaced right when requesting chips.
            for (Player p : new java.util.ArrayList<>(gf.getJugadores())) {
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
            // Headroom = table cap (greater of the standard buy-in and the highest stack,
            // see getBuyinCap) minus current stack (in whole units).
            return Math.max(0, getBuyinCap() - (int) Math.ceil(current_stack));
        }
        return BuyinRules.headroom(FIXED_BUYIN, BUYIN, CIEGA_GRANDE, BUYIN_MAX_BB, current_stack);
    }

    // CYAN stack marker = the player has made at least one RE-buy (not the initial buy).
    // Counts actual rebuys via the dealer's per-nick counter, so a player who in variable mode
    // simply chose a deeper initial buy-in is NOT marked. Null-safe (green if there's no dealer
    // yet, e.g. while the table is being set up).
    public static boolean hasRebought(String nick) {
        // nick can be null while the table is being set up (setStack in GameFrame's
        // constructor runs before nicknames are assigned in sentarParticipantes);
        // ConcurrentHashMap doesn't accept a null key.
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

    // Persists ONLY the game.rebuy column (allow rebuying). Needed because that flag is
    // EDITABLE on recover and does NOT travel in recover_settings (unlike the rebuy
    // limit/bots/cap): without this, resuming the game would have the dealer re-read
    // game.rebuy (the original value) and overwrite the user's edit.
    public static void persistRecoverRebuy(int gameId, boolean rebuy) {
        if (gameId <= 0) {
            return;
        }
        synchronized (GameFrame.SQL_LOCK) {
            try (PreparedStatement st = Helpers.getSQLITE().prepareStatement("UPDATE game SET rebuy=? WHERE id=?")) {
                st.setQueryTimeout(30);
                st.setBoolean(1, rebuy);
                st.setInt(2, gameId);
                st.executeUpdate();
            } catch (SQLException ex) {
                Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, "Failed to persist recover rebuy", ex);
            }
        }
    }

    // Issue#9: in recover mode, NewGameDialog leaves BUYIN/CIEGAS at the spinner's default
    // value (BUYIN=10, blinds 0.10/0.20) because the form controls get disabled but are never
    // loaded from SQL — the form keeps its constructor's initial values. Later,
    // recuperarDatosClavePartida fixes GameFrame.BUYIN/CIEGAS from the game/hand row, but
    // GameFrame's constructor already ran before that and the Player slots (field initializer
    // = GameFrame.BUYIN, plus the matching setStack/setBuyin loop) captured the stale BUYIN =
    // 10. For the original participants, recuperarDatosClavePartida overwrites their
    // stack/buyin from the SQL balance row, but for a late-joiner with no prior row the stale
    // value sticks: they appear seated with stack=10 and buyin=10 at a table configured for
    // 100. This helper fixes the root cause by loading BUYIN/CIEGAS from the game row before
    // GameFrame gets constructed.
    public static void applyRecoveredGameStats(int gameId) {
        if (gameId <= 0) {
            return;
        }
        synchronized (GameFrame.SQL_LOCK) {
            String sql = "SELECT buyin, round(sb,2) AS sb, blinds_time, blinds_time_type FROM game WHERE id=?";
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
                        // REBUY (allow rebuying) is no longer restored here: it's EDITABLE on
                        // recover (loadLastGame reads it from game.rebuy into the control and
                        // applies it from there). Restoring it here would overwrite the user's edit.
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
    private final BrightnessOverlay capa_brillo = new BrightnessOverlay();

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
    // Access to the screenshot viewer. Built by hand after initComponents (OUTSIDE NetBeans'
    // //GEN-* blocks, which get regenerated from the .form) and inserted into file_menu.
    private javax.swing.JMenuItem screenshots_menu = null;
    private volatile FastChatDialog fastchat_dialog = null;
    private volatile RebuyDialog rebuy_dialog = null;
    private volatile GifAnimationDialog gif_dialog = null;
    public volatile VolumeControlDialog volume_dialog = null;
    private volatile TablePanel tapete = null;
    private volatile Timer tiempo_juego;
    private volatile int tapete_counter = 0;
    private volatile int i60_c = 0;
    private volatile boolean recover = false;
    // The final screen (BalanceScreen) is an overlay mounted on THIS frame's glassPane (no
    // longer a separate modal JDialog). While it's active, the KeyEventDispatcher ignores
    // frame.isActive() so board shortcuts don't fire underneath the final screen: this
    // replicates how the old modal dialog left the frame INACTIVE. The visible glassPane
    // already intercepts mouse input to the board.
    private volatile boolean balance_overlay_active = false;
    // The table's popup menu (right-click context menu), saved when showing the final screen
    // so it can be restored once it's dismissed: during the balance screen the board is inert
    // and shouldn't open its context menu.
    private volatile javax.swing.JPopupMenu balance_saved_tapete_popup = null;
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

        GameFrame frame = THIS;
        if (frame == null) {
            return;
        }

        frame.getFull_screen_menu().setEnabled(false);

        // The frame is being discarded, so do not toggle fullscreen here. Toggling would
        // dispose/recreate the native peer, show and maximize it, and resetInstance would
        // immediately hide and dispose it again. Release an exclusive fullscreen device
        // directly where necessary, then dispose the frame exactly once.
        if (!Helpers.OSValidator.isWindows() && frame.device != null
                && frame.device.getFullScreenWindow() == frame) {
            frame.device.setFullScreenWindow(null);
        }

        GameFrame.IWTSTH_RULE = false;

        GameFrame.RABBIT_HUNTING = 0;

        GameFrame.RUN_IT_TWICE = false;

        // Global rules (TTS / voice notes) are NOT reset. If the server overwrote them
        // during the game they stay that way; the value is persisted as a property and is
        // the preselection for the next game.

        // Defensive: without resetting these statics, a game that ends with
        // force_recover=true would contaminate the next fresh game (the host's INIT
        // replicates RECOVER=true to the client, starting a recovery with nothing to
        // recover). Set here to the same initial state as a clean JVM startup.
        GameFrame.RECOVER = false;
        GameFrame.RECOVER_ID = -1;
        GameFrame.UGI = null;

        frame.setVisible(false);

        // Stop THIS game's dealer shuffle-verify queue before discarding the GameFrame: its
        // daemon worker sits blocked in take(), holding the whole Crupier via the Sink,
        // leaking a thread plus its object graph for every game played in the same app session.
        if (frame.getCrupier() != null) {
            frame.getCrupier().shutdownShuffleVerifyQueue();
        }

        frame.dispose();

        THIS = null;
    }

    public BrightnessOverlay getCapa_brillo() {
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
        if (zoomSonidoOn()) {
            Audio.playWavResource(zoom_accumulator > 0 ? "misc/zoom_in.wav" : "misc/zoom_out.wav");
        }

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
            InGameNotifyDialog.notifyZoom();

            if (jugadas_dialog != null && jugadas_dialog.isVisible()) {
                for (Card carta : jugadas_dialog.getCartas()) {
                    carta.invalidateImagePrecache();
                    carta.refreshCard();
                }
                Helpers.GUIRun(jugadas_dialog::pack);
            }

            if (shortcuts_dialog != null && shortcuts_dialog.isVisible()) {
                shortcuts_dialog.zoom(Helpers.DIALOG_ZOOM, null);
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
     * Repositions the GameFrame on the monitor where the WaitingRoomFrame currently sits, so
     * (auto)fullscreen / MAXIMIZED_BOTH lands on that screen instead of the default monitor.
     * Needed on Windows because setExtendedState(MAXIMIZED_BOTH) honors the monitor the window
     * is currently on; also useful on Mac before the native setVisible. toggleFullScreen's X11
     * branch already uses the waiting room's device explicitly.
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
                // Direct call to the unified toggle; this used to be full_screen_menu.doClick(),
                // which fired the JMenuItem's listener as a synthetic event — a Swing
                // antipattern coupling startup to UI behaviour.
                triggerFullScreenToggle();
            } else {
                GameFrame.getInstance().setExtendedState(JFrame.MAXIMIZED_BOTH);
                GameFrame.getInstance().setVisible(true);
            }

            GameFrame.getInstance().setEnabled(false);
        });

        if (!Init.DEV_MODE && fullscreen) {
            // Wall-clock deadline to avoid drift from spurious wait wakeups. The counter
            // used to be blindly incremented by 1000ms per iteration, assuming wait(1000) had
            // waited out the full period; a notify (or a spurious wakeup) broke that assumption.
            long deadline = System.currentTimeMillis() + AUTO_ZOOM_TIMEOUT;
            // full_screen check INSIDE the synchronized block: outside it, the toggle could
            // set full_screen=true + notifyAll between the check and the wait, losing the
            // notification and sleeping until the timeout.
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

        // The waiting room just hid itself and this frame is the game's new foreground. The
        // grab is deferred to a later EDT cycle so it runs AFTER the OS has dispatched the
        // asynchronous activation events from setVisible and the native peer recreation
        // (switch to borderless); this way the foreground push doesn't race a late WM_ACTIVATE.
        // Not an arbitrary wait: it orders the grab after the window's realization on the EDT queue.
        forceForegroundDeferred();

    }

    /**
     * Reliably grabs the foreground. toFront()/requestFocus() are subject to Windows'
     * foreground lock (SPI_GETFOREGROUNDLOCKTIMEOUT) and activate the window
     * non-deterministically — sometimes the OS just flashes the taskbar button without
     * granting focus. A pulse of alwaysOnTop emits a SetWindowPos(HWND_TOPMOST), which is NOT
     * subject to that restriction: it forces activation and drags focus along. The previous
     * state (usually false) is restored immediately so the window isn't left pinned above
     * everything else — which is why it doesn't affect later dialogs (chat GIFs, etc.): the
     * pulse is instantaneous and the window does NOT stay topmost. Must be called on the EDT.
     */
    private void forceForeground() {
        boolean was_on_top = isAlwaysOnTop();
        setAlwaysOnTop(true);
        toFront();
        requestFocus();
        setAlwaysOnTop(was_on_top);
    }

    /**
     * Schedules {@link #forceForeground()} on a later EDT cycle, so it runs after the
     * asynchronous activation events from showing/recreating the window have been dispatched
     * (the switch to borderless recreates the native peer and OS activation arrives deferred).
     * Safe to call from any thread.
     */
    private void forceForegroundDeferred() {
        SwingUtilities.invokeLater(() -> {
            // A fullscreen toggle may have queued this callback just before the game ended.
            // Do not focus a disposed frame or steal focus from the newly shown main menu.
            if (THIS == this && isDisplayable() && isVisible()) {
                forceForeground();
            }
        });
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

    public JMenuItem getScreenshots_menu() {
        return screenshots_menu;
    }

    // Creates and inserts the screenshot viewer entry into the menu, right after "View log".
    // Manual (not in the .form): this way it survives NetBeans regenerating the //GEN-* block.
    private void setupScreenshotsMenu() {

        screenshots_menu = new javax.swing.JMenuItem();
        screenshots_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        screenshots_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/camera.png"))); // NOI18N
        screenshots_menu.setText(Translator.translate("menu.visor_capturas"));
        screenshots_menu.putClientProperty("i18n.key", "menu.visor_capturas");
        screenshots_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ScreenshotViewerDialog.open(GameFrame.this);
            }
        });

        int idx = file_menu.getPopupMenu().getComponentIndex(registro_menu);

        if (idx >= 0) {
            file_menu.insert(screenshots_menu, idx + 1);
        } else {
            file_menu.add(screenshots_menu);
        }
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

    /**
     * Applies one zoom level synchronously for the auto-fit worker.
     *
     * The menu listeners intentionally dispatch their heavy work to another worker. That is
     * correct for a user click, but it means that invoking a menu item with doClick() cannot be
     * used as a completion barrier: the listener returns before its worker has changed the
     * table. Auto-fit calls this method directly so its next bounds measurement observes the
     * completed zoom operation. The caller must not be the EDT.
     *
     * @return true when a valid, different level was applied
     */
    boolean applyZoomLevelSynchronouslyForAutoZoom(int target) {
        if (java.awt.EventQueue.isDispatchThread()) {
            throw new IllegalStateException("Auto-zoom must not run on the EDT");
        }

        if (Helpers.doubleSecureCompare(0f, 1f + (target * ZOOM_STEP)) >= 0) {
            return false;
        }

        synchronized (ZOOM_LOCK) {
            if (target == ZOOM_LEVEL) {
                return false;
            }
            ZOOM_LEVEL = target;
        }

        Helpers.PROPERTIES.setProperty("zoom_level", String.valueOf(ZOOM_LEVEL));
        Card.updateCachedImages(1f + ZOOM_LEVEL * ZOOM_STEP, false);
        zoom(1f + ZOOM_LEVEL * ZOOM_STEP, null);
        InGameNotifyDialog.notifyZoom();

        if (jugadas_dialog != null && jugadas_dialog.isVisible()) {
            for (Card carta : jugadas_dialog.getCartas()) {
                carta.invalidateImagePrecache();
                carta.refreshCard();
            }
            Helpers.GUIRun(jugadas_dialog::pack);
        }

        if (shortcuts_dialog != null && shortcuts_dialog.isVisible()) {
            shortcuts_dialog.zoom(Helpers.DIALOG_ZOOM, null);
        }

        return true;
    }

    public void refresh() {
        Helpers.GUIRun(() -> {

            revalidate();
            repaint();

        });
    }

    public void toggleFullScreen() {

        Helpers.GUIRun(() -> {
            // Compute the target locally first. The flag is committed at the end, so that if
            // any step of the change (setUndecorated, setFullScreenWindow, etc.) throws, the
            // flag doesn't end up diverging from the window's real state.
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

            // Commit the flag AFTER the switch operations have finished, so a failure midway
            // (exception in setUndecorated, setVisible, setFullScreenWindow) doesn't leave
            // full_screen diverging from the window's real state.
            full_screen = entering_full_screen;

            full_screen_menu.setEnabled(true);
            Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(true);

            synchronized (full_screen_lock) {
                full_screen_lock.notifyAll();
            }

            // Deferred: the switch's dispose()/setVisible() recreates the native peer, and a
            // synchronous requestFocus() here would race the OS's WM_ACTIVATE.
            forceForegroundDeferred();
        });

    }

    public void cambiarBaraja() {

        Card.updateCachedImages(1f + GameFrame.ZOOM_LEVEL * GameFrame.getZOOM_STEP(), true);

        Helpers.threadRun(() -> Audio.playPreloadedWav("misc/uncover.wav"));

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

        // Pre-decodes the new deck's shuffle.gif in the background (the cache holds a single
        // entry: it replaces and frees the previous one)
        Crupier.warmShuffleAnimCache();

    }

    // Applies a new card back live (a GLOBAL setting, independent of the deck): persists it,
    // invalidates the flip cache, and refreshes the back + face-down cards on the table (same
    // card refresh as cambiarBaraja).
    public void setTrasera(String t) {

        GameFrame.TRASERA = t;
        Helpers.PROPERTIES.setProperty("trasera", t);
        Helpers.savePropertiesFile();
        CardFlipAnimator.clearCache();

        Helpers.threadRun(() -> {

            Card.updateCachedImages(1f + GameFrame.ZOOM_LEVEL * GameFrame.getZOOM_STEP(), true);

            for (Player jugador : tapete.getPlayers()) {
                jugador.getHoleCard1().invalidateImagePrecache();
                jugador.getHoleCard1().refreshCard();
                jugador.getHoleCard2().invalidateImagePrecache();
                jugador.getHoleCard2().refreshCard();
            }

            for (Card carta : this.tapete.getCommunityCards().getCartasComunes()) {
                carta.invalidateImagePrecache();
                carta.refreshCard();
            }
        });
    }

    // Applies the compaction state derived from VISTA_COMPACTA:
    //   - community cards: shrink from level 2 onward (compact+cards).
    //   - local player's hole cards: shrink ONLY at level 3 (compact+local).
    //   - local player's action buttons: 2x2 grid ONLY at level 3.
    // The single source of truth; used by the constructor, the menu cycle and the Settings
    // combo (via vistaCompacta), and replicated by TablePanelFactory when rebuilding the panel.
    public void applyCompactableFlags() {

        LocalPlayer local = tapete.getLocalPlayer();

        boolean local_compact = (GameFrame.VISTA_COMPACTA == 3);

        local.getHoleCard1().setCompactable(local_compact);
        local.getHoleCard2().setCompactable(local_compact);

        boolean community_compact = (GameFrame.VISTA_COMPACTA >= 2);

        for (Card carta : tapete.getCommunityCards().getCartasComunes()) {
            carta.setCompactable(community_compact);
        }

        local.setBotoneraCompact(local_compact);
    }

    public void vistaCompacta() {

        applyCompactableFlags();

        RemotePlayer[] players = tapete.getRemotePlayers();

        final ConcurrentLinkedQueue<Long> notifier = new ConcurrentLinkedQueue<>();

        for (RemotePlayer jugador : players) {

            jugador.getHoleCard1().refreshCard(true, notifier);
            jugador.getHoleCard2().refreshCard(true, notifier);
        }

        for (Card carta : this.getTapete().getCommunityCards().getCartasComunes()) {
            carta.refreshCard();
        }

        // The local player's hole cards also change size (level 3 <-> the rest).
        tapete.getLocalPlayer().getHoleCard1().refreshCard();
        tapete.getLocalPlayer().getHoleCard2().refreshCard();

        // Check INSIDE the synchronized block: outside it, the last card could
        // add()+notifyAll between the size() check and the wait, losing the notification
        // (a ~1s stall per barrier). Same fix applied to every zoom/refresh notifier wait.
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
            // The rebuy game-over GIF lasts through the whole busted-player decision:
            // reposition it to the new compact/normal geometry.
            jugador.refreshRebuyGifLabel();
        }

        // The call-cost overlay is absolutely positioned over the community cards: when the
        // compact view changes (which can move/shrink the community panel) it must be
        // repositioned/rescaled to the new geometry.
        if (getCrupier() != null) {
            getCrupier().refreshCallCostOverlay();

            // The large position chip (dealer/blind/straddle) hides over the card at
            // half-height in level 3 and repaints on exit. Only here (in-game toggle), NEVER
            // from the constructor: there the nickname is still null.
            tapete.getLocalPlayer().refreshPositionChipIcons();
        }

        // Reposition the in-frame overlays anchored to the geometry (which changes with the
        // compact view) if they're OPEN when the view changes: AUTO MODE (anchored to the seat
        // + action buttons) and voluntary straddle (anchored to the hole cards). Only if one is
        // alive (volatile fields), and after a settle so the seat/buttons/cards already have
        // their new size; re-invoking showOn recomputes their bounds.
        final AutoActionDialog auto_dlg = tapete.getLocalPlayer().getAuto_action_dialog();
        final VoluntaryStraddleDialog straddle_dlg = getCrupier() != null ? getCrupier().getStraddle_local_dialog() : null;
        if (auto_dlg != null || straddle_dlg != null) {
            Helpers.pausar(GameFrame.GUI_RENDER_WAIT);
            Helpers.GUIRun(() -> {
                if (auto_dlg != null && auto_dlg.isShowing()) {
                    auto_dlg.showOn(tapete);
                }
                if (straddle_dlg != null && straddle_dlg.isShowing()) {
                    straddle_dlg.showOn(tapete);
                }
            });
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

                // On PAUSE the nick of whoever starts the pause travels; on RESUME the
                // original pauser (nick_pause) must travel instead, since that's the nick
                // clients recorded when pausing and validate the resume against. If the host
                // resumed another player's pause and sent its own nick, clients would reject
                // it and stay stuck with the pause overlay up.
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

                if (!GameFrame.getInstance().getCrupier().isIwtsthing() && pausaSonidoOn()) {
                    Audio.playWavResource("misc/pause.wav");
                }
            } else {

                this.nick_pause = null;
            }

            this.lock_pause.notifyAll();

            // The block below goes to the EDT ASYNCHRONOUSLY, so the state is captured here at
            // enqueue time: if two changes arrive back to back (pause then resume, or two
            // players at once), reading the field inside the lambda would make both see the
            // final value and run the SAME branch twice. The table's light-out tracks how many
            // reasons are keeping it dark, and a repeated branch throws that count off: too
            // many leaves the table black with a dead switch; too few lights it up under a dialog.
            final boolean pausada = this.timba_pausada;

            Helpers.GUIRun(() -> {

                if (pausa_dialog == null) {
                    pausa_dialog = new PauseDialog(this, false);
                }

                if (pausada) {

                    if (isPartida_local() || getNick_local().equals(user)) {
                        Helpers.setScaledIconButton(GameFrame.getInstance().getTapete().getCommunityCards().getPause_button(), getClass().getResource("/images/continue.png"), Math.round(0.6f * GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().getHeight()), Math.round(0.6f * GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().getHeight()));
                        GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().setText(Translator.translate("ui.continuar_2"));
                        GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().setEnabled(true);

                    } else {
                        GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().setEnabled(false);
                    }

                    pausa_dialog.setLocationRelativeTo(pausa_dialog.getParent());
                    pausa_dialog.setVisible(true);

                    // On pause, the table darkens and the switch is disabled for the pause's
                    // duration. It's just another TEMPORARY light-out: if the player already had
                    // the lights off on their own, this changes nothing they see, and resuming
                    // respects whatever they had chosen.
                    capa_brillo.pushForcedLightsOFF();
                    getTapete().getCommunityCards().applyLightsVisuals();

                    getTapete().getCommunityCards().getLights_label().setEnabled(false);

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

                    // On resume that temporary light-out is lifted and the switch is back in
                    // control: the table lights back up on its own unless the player has them off.
                    capa_brillo.popForcedLightsOFF();
                    getTapete().getCommunityCards().applyLightsVisuals();

                    getTapete().getCommunityCards().getLights_label().setEnabled(true);

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

        // Action bodies indexed by the STABLE id from the shortcut registry. The dispatcher
        // resolves the id of the pressed combination (KeyboardShortcuts.idFor) and runs the
        // body, so reassigning a key takes effect LIVE without rebuilding this map.
        HashMap<String, Action> gameActions = new HashMap<>();

        gameActions.put(KeyboardShortcuts.LATENCY, new AbstractAction("LATENCY_STATS") {
            @Override
            public void actionPerformed(ActionEvent e) {

                if (GameFrame.getInstance().isPartida_local()) {

                    latency_stats = !latency_stats;

                    latencyStats(latency_stats);

                }
            }

        });

        gameActions.put(KeyboardShortcuts.REFRESH, new AbstractAction("REFRESH") {
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

        gameActions.put(KeyboardShortcuts.QUIT, new AbstractAction("QUIT") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {
                GameFrame.getInstance().getExit_menu().doClick();
            }
        }
        );

        gameActions.put(KeyboardShortcuts.BUYIN, new AbstractAction("BUYIN") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {
                GameFrame.getInstance().getLocalPlayer().player_stack_click();
            }
        }
        );

        gameActions.put(KeyboardShortcuts.HALT, new AbstractAction("HALT") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {
                GameFrame.getInstance().getHalt_game_menu().doClick();
            }
        }
        );

        gameActions.put(KeyboardShortcuts.PAUSE, new AbstractAction("PAUSE") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {
                GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().doClick();
            }
        }
        );

        gameActions.put(KeyboardShortcuts.LIGHTS, new AbstractAction("LIGHTS") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {
                GameFrame.getInstance().getTapete().getCommunityCards().lightsButtonClick();
            }
        }
        );

        gameActions.put(KeyboardShortcuts.FULLSCREEN, new AbstractAction("FULL-SCREEN") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {
                full_screen_menuActionPerformed(e);
            }
        }
        );

        gameActions.put(KeyboardShortcuts.COMPACT, new AbstractAction("COMPACT-CARDS") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {
                compact_menu.doClick();
            }
        }
        );

        gameActions.put(KeyboardShortcuts.ZOOM_IN, new AbstractAction("ZOOM-IN") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {

                zoom_menu_inActionPerformed(e);

            }
        }
        );

        gameActions.put(KeyboardShortcuts.ZOOM_OUT, new AbstractAction("ZOOM-OUT") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {

                zoom_menu_outActionPerformed(e);

            }
        }
        );

        gameActions.put(KeyboardShortcuts.ZOOM_RESET, new AbstractAction("ZOOM-RESET") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {

                zoom_menu_resetActionPerformed(e);

            }
        }
        );

        gameActions.put(KeyboardShortcuts.CHAT, new AbstractAction("CHAT") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {
                chat_menuActionPerformed(e);
            }
        }
        );

        // Quick chat 'º' is a "typed" key (dead-key), fragile across keyboard layouts, and
        // besides, reassigning it to a normal key would clash with typing in the chat itself
        // (closing it would get messy): it's NOT reassignable, stays fixed and is resolved by
        // its KeyStroke, not through the registry.
        final KeyStroke fastchat_keystroke = KeyStroke.getKeyStroke('º');
        final Action fastchat_action = new AbstractAction("FASTCHAT") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {

                showFastChatDialog();

            }
        };

        gameActions.put(KeyboardShortcuts.FASTCHAT_IMAGE, new AbstractAction("FASTCHAT-IMAGE") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {

                showFastChatImage();

            }
        }
        );

        gameActions.put(KeyboardShortcuts.LOG_REGISTRO, new AbstractAction("REGISTRO") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {
                registro_menuActionPerformed(e);
            }
        }
        );

        gameActions.put(KeyboardShortcuts.CLOCK, new AbstractAction("RELOJ") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {
                time_menu.doClick();
            }
        }
        );

        gameActions.put(KeyboardShortcuts.FOLD, new AbstractAction("FOLD-BUTTON") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {
                // With the voluntary straddle dialog or AUTO MODE dialog open, ESC = CANCEL
                // (straddle: DON'T post; auto: cancel the automatic action) instead of folding.
                VoluntaryStraddleDialog sd = getCrupier() != null ? getCrupier().getStraddle_local_dialog() : null;
                if (sd != null && sd.isShowing()) {
                    sd.cancel();
                    kbd_overlay_swallow_esc = true;
                    return;
                }
                AutoActionDialog ad = getLocalPlayer() != null ? getLocalPlayer().getAuto_action_dialog() : null;
                if (ad != null && ad.isShowing()) {
                    ad.cancel();
                    kbd_overlay_swallow_esc = true;
                    return;
                }
                // Canceling AUTO MODE re-enables the action buttons and removes the overlay; if
                // ESC is still HELD (auto-repeat) or repeats instantly, that event must not land
                // on a normal fold (accidental fold). Cleared when ESC is released (dispatcher).
                if (kbd_overlay_swallow_esc) {
                    return;
                }
                if (!getCrupier().isSincronizando_mano()) {
                    getLocalPlayer().getPlayer_fold().doClick();
                }
            }
        }
        );

        gameActions.put(KeyboardShortcuts.CHECK, new AbstractAction("CHECK-BUTTON") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {
                // With the voluntary straddle dialog or AUTO MODE dialog open, SPACE = ACCEPT
                // (straddle: POST; auto: run the automatic action now) instead of checking.
                VoluntaryStraddleDialog sd = getCrupier() != null ? getCrupier().getStraddle_local_dialog() : null;
                if (sd != null && sd.isShowing()) {
                    sd.accept();
                    kbd_overlay_swallow_space = true;
                    return;
                }
                AutoActionDialog ad = getLocalPlayer() != null ? getLocalPlayer().getAuto_action_dialog() : null;
                if (ad != null && ad.isShowing()) {
                    ad.accept();
                    kbd_overlay_swallow_space = true;
                    return;
                }
                // Same as ESC: after accepting an overlay with SPACE held/repeating, don't let
                // the next event land on a normal check/show. Cleared when SPACE is released.
                if (kbd_overlay_swallow_space) {
                    return;
                }
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

        gameActions.put(KeyboardShortcuts.BET, new AbstractAction("BET-BUTTON") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {
                if (!getCrupier().isSincronizando_mano()) {
                    getLocalPlayer().getPlayer_bet_button().doClick();
                }
            }
        }
        );

        gameActions.put(KeyboardShortcuts.ALLIN, new AbstractAction("ALLIN-BUTTON") {
            @Override
            public void actionPerformed(ActionEvent e
            ) {
                if (!getCrupier().isSincronizando_mano() && !GameFrame.getInstance().getLocalPlayer().isBoton_mostrar()) {
                    getLocalPlayer().getPlayer_allin().doClick();
                }
            }
        }
        );

        // BET_DOWN covers LOWER: its primary key (down arrow) plus the fixed LEFT alias
        // (left arrow), defined in the registry. There used to be two twin actions (BET-DOWN and
        // BET-LEFT) sharing the same body.
        gameActions.put(KeyboardShortcuts.BET_DOWN, new AbstractAction("BET-DOWN") {
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

        // BET_UP covers RAISE: its primary key (up arrow) plus the fixed RIGHT alias (right
        // arrow). There used to be two twin actions (BET-UP and BET-RIGHT) sharing the same body.
        gameActions.put(KeyboardShortcuts.BET_UP, new AbstractAction("BET-UP") {
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
            // While the Shortcuts tab is capturing a key, this dispatcher steps aside so the
            // pressed combination reaches the capturer instead of firing whatever shortcut it
            // was assigned to.
            if (KeyboardShortcuts.isCapturing()) {
                return false;
            }
            // On RELEASING the fold/check key, the overlays' anti-double-action guard is cleared
            // (see the FOLD/CHECK actions): so a fresh press (not a repeat of the held key)
            // behaves normally again. Compared by keyCode against those actions' CURRENT
            // assignment (the user may have reassigned them).
            if (e.getID() == KeyEvent.KEY_RELEASED) {
                if (e.getKeyCode() == KeyboardShortcuts.keyCode(KeyboardShortcuts.FOLD)) {
                    kbd_overlay_swallow_esc = false;
                } else if (e.getKeyCode() == KeyboardShortcuts.keyCode(KeyboardShortcuts.CHECK)) {
                    kbd_overlay_swallow_space = false;
                }
            }
            KeyStroke keyStroke = KeyStroke.getKeyStrokeForEvent(e);
            JFrame frame = GameFrame.getInstance();
            // Resolve the action by the registry's id (reassignable shortcuts) or, failing
            // that, by quick chat's fixed 'º' key. Combinations that belong to Init (mute,
            // volume, screenshot, force quit) resolve to an id that's NOT in gameActions -> a =
            // null -> this dispatcher lets them through and Init's handles them.
            String id = KeyboardShortcuts.idFor(keyStroke);
            final Action a = id != null ? gameActions.get(id)
                    : (keyStroke.equals(fastchat_keystroke) ? fastchat_action : null);
            if (a != null && !file_menu.isSelected() && !apariencia_menu.isSelected() && !opciones_menu.isSelected() && !help_menu.isSelected() && ((frame.isActive() && !balance_overlay_active) || (pausa_dialog != null && pausa_dialog.hasFocus()) || (crupier.isFin_de_la_transmision() && KeyboardShortcuts.MUTE.equals(id)))) {
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

    // All FACE-UP cards on the table (community + hole cards players have shown), for
    // highlighting a loser's hand in the showdown (RESALTAR_JUGADA_SHOWDOWN). Face-down cards
    // are excluded so a card back isn't dimmed (that would tip off it doesn't count before
    // seeing it).
    public java.util.List<Card> getShowdownVisibleCards() {
        java.util.List<Card> cartas = new java.util.ArrayList<>();

        for (Card c : getCartas_comunes()) {
            if (c != null && !c.isTapada()) {
                cartas.add(c);
            }
        }

        for (Player p : getJugadores()) {
            Card h1 = p.getHoleCard1();
            Card h2 = p.getHoleCard2();

            if (h1 != null && !h1.isTapada()) {
                cartas.add(h1);
            }

            if (h2 != null && !h2.isTapada()) {
                cartas.add(h2);
            }
        }

        return cartas;
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

        // Run-it-twice: marks which board (BOARD-A/BOARD-B) the display corresponds to while
        // running the two boards (null outside run-it-twice). The tag goes in the PREFIX
        // ("POT (BOARD-A): X") instead of a suffix at the end.
        final String rit_tag = getCrupier() != null ? getCrupier().getRitPotBoardTag() : null;
        final String prefix = rit_tag != null
                ? Translator.translate("runittwice.pot_label_full", rit_tag)
                : Translator.translate("game.bote_2");

        final String suffix = beneficio != null ? " (" + Helpers.money2String(beneficio) + ")" : "";

        Helpers.GUIRun(() -> {
            // The pot number rolls at constant speed (prefix/suffix untouched); with rolling
            // off or during recover it snaps instantly.
            tapete.getCommunityCards().rollPotValue(prefix, bote, suffix, isCounterRollEnabled());
        });
    }

    public void setTapeteBote(String bote) {

        // Same RIT-aware prefix as the (float, Float) overload: run-it-twice's per-board
        // breakdown carries the tag ("POT (BOARD-A): #1{..}+#2{..}"). Outside RIT (null tag)
        // -> "POT:", identical to before.
        final String rit_tag = getCrupier() != null ? getCrupier().getRitPotBoardTag() : null;
        final String prefix = rit_tag != null
                ? Translator.translate("runittwice.pot_label_full", rit_tag)
                : Translator.translate("game.bote_2");

        Helpers.GUIRun(() -> {
            // Textual pot state ("---", RIT breakdown): set instantly and invalidates the
            // roller so the next numeric value doesn't animate from a stale one.
            tapete.getCommunityCards().setPotTextImmediate(prefix + " " + bote);
        });
    }

    public void setTapeteApuestas(double apuestas) {

        // bet_label shows ONLY the current street (no amount or icon), to reduce noise. The
        // street's pot (Crupier.apuestas) is still computed and passed in here: to show it
        // again, just re-add it to setText.
        Helpers.GUIRun(() -> {
            String street = STREETS[getCrupier().getStreet() - 1];

            tapete.getCommunityCards().getBet_label().setText(street);

            tapete.getCommunityCards().getBet_label().setVisible(true);
        });

    }

    public void downgradeAndRefreshTapete() {

        // If the game already ended (e.g. the server decides to exit on the same hand a
        // player left and the board shrinks), do NOT rebuild the table: TablePanelFactory
        // creates a new board with panels VISIBLE by default that, queued on the EDT, would
        // land AFTER the final balance screen's hideALL (a factory-vs-exit race -> players
        // would reappear over the balance screen). With no next hand, there's nothing to rebuild.
        if (getCrupier() != null && getCrupier().isFin_de_la_transmision()) {
            return;
        }

        // Downgrade animation: BEFORE the swap, on the current board, players who leave fade
        // out and survivors slide into their spot on the M-player table. Blocks this thread
        // (dealer) until it finishes. The subsequent swap mounts the new board with copies in
        // those same positions, so the transition is continuous. Purely visual: doesn't touch
        // the logic. Configurable (checkbox + speed in Settings -> Appearance); if off, the
        // usual hard cut.
        if (tapete instanceof DynamicTablePanel && GameFrame.downgradeAnimOn()) {
            ((DynamicTablePanel) tapete).animateDowngrade(GameFrame.DOWNGRADE_VELOCIDAD);
        }

        TablePanel nuevo_tapete = TablePanelFactory.downgradePanel(tapete);

        if (nuevo_tapete != null) {

            GameFrame.getInstance().getJugadores().clear();

            GameFrame.getInstance().getJugadores().addAll(Arrays.asList(nuevo_tapete.getPlayers()));

            Helpers.GUIRunAndWait(() -> {
                GameFrame.getInstance().getContentPane().remove(tapete);
                tapete = nuevo_tapete;
                zoomables = new ZoomableInterface[]{tapete};
                GameFrame.getInstance().getContentPane().add(tapete);

                // TOCTOU: if the game ended while the shrunk board was being built/swapped in,
                // its panels (visible by default) must NOT appear over the final balance
                // screen (the other half of the race).
                if (getCrupier() != null && getCrupier().isFin_de_la_transmision()) {
                    nuevo_tapete.hideALL();
                }

                Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), GameFrame.THINK_TIME);

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

                // DIALOG zoom also scales the game's menu bar (the GameFrame's textual chrome
                // grows/shrinks with the same setting; the table's popup does it in Helpers). It
                // doesn't touch the TABLE's zoom. At 100% it changes nothing.
                if (Helpers.isDialogZoomActive()) {
                    Helpers.updateFonts(menu_bar, Helpers.GUI_FONT, Helpers.DIALOG_ZOOM);
                    Helpers.scaleIcons(menu_bar, Helpers.DIALOG_ZOOM);
                }

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

        // End of the hand's betting (showdown / run-out): the call-cost overlay no longer
        // makes sense either.
        tapete.hideCallCostOverlay();

    }

    public void setTapeteCiegas(double pequeña, double grande) {

        Helpers.GUIRun(() -> {
            if (crupier.getCiegas_update() != null || crupier.isAnteStraddleUpdate()) {
                tapete.getCommunityCards().getBlinds_panel().setOpaque(true);
                tapete.getCommunityCards().getBlinds_panel().setBackground(Color.YELLOW);
                tapete.getCommunityCards().getBlinds_label().setForeground(Color.BLACK);
            } else {
                tapete.getCommunityCards().getBlinds_panel().setOpaque(false);
                tapete.getCommunityCards().getBlinds_panel().setBackground(null);
                // The blinds color follows the STABLE counters-color variable, NOT the
                // pot_label's foreground: that one flashes yellow when a chip lands
                // (flashPotLabelYellow) and switches to orange/white/black at showdown. Since
                // actualizarContadoresTapete is called very often, reading it from there left the
                // blinds "stuck" to that transient color (the ghost yellow).
                Color counters_color = tapete.getCommunityCards().getColor_contadores();
                if (counters_color != null) {
                    tapete.getCommunityCards().getBlinds_label().setForeground(counters_color);
                }
            }

            tapete.getCommunityCards().getBlinds_label().setText((GameFrame.ANTE ? "(A) " : "") + Helpers.money2String(pequeña) + " / " + Helpers.money2String(grande) + (GameFrame.CIEGAS_DOUBLE > 0 ? " @ " + String.valueOf(GameFrame.CIEGAS_DOUBLE) + (GameFrame.CIEGAS_DOUBLE_TYPE <= 1 ? "'" : "*") + (crupier.getCiegas_double() > 0 ? " (" + String.valueOf(crupier.getCiegas_double()) + ")" : "") : ""));
            tapete.getCommunityCards().refreshStraddleIcon();
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

    // === Audio controls: single source of truth ===
    // All the apply/persist/broadcast logic lives here. The
    // audio settings dialog and the call sites (speaker icon, recover, keyboard shortcuts)
    // call these methods. There are no longer audio controls in the menu or the popup, so
    // nothing needs syncing with them. The three controls that are NOT host rules (sound,
    // joke sounds, music) are static: they also work from the start window, where there's no
    // GameFrame yet.
    public static void setSonidos(boolean on) {

        GameFrame.SONIDOS = on;

        Helpers.PROPERTIES.setProperty("sonidos", String.valueOf(on));
        Helpers.savePropertiesFile();

        if (!on) {
            Audio.muteAll();
        } else {
            Audio.unmuteAll();
        }

        // Refresh the speaker icon wherever it exists (in-game table, start window at startup).
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

    public static void setMusica(boolean on) {

        GameFrame.MUSICA = on;

        Helpers.PROPERTIES.setProperty("musica", String.valueOf(on));
        Helpers.savePropertiesFile();

        // Master: refresh the FOUR background tracks that might be playing so the change is
        // heard instantly (effectiveLoopVolume already combines this flag with the individual one).
        Audio.refreshLoopVolume(Audio.ASCENSOR_VOLUME.getKey());
        Audio.refreshLoopVolume(Audio.WAITING_ROOM_VOLUME.getKey());
        Audio.refreshLoopVolume(Audio.ABOUT_VOLUME.getKey());
        Audio.refreshLoopVolume(Audio.STATS_VOLUME.getKey());
    }

    public static void setMusicaAmbiental(boolean on) {

        GameFrame.MUSICA_AMBIENTAL = on;

        Helpers.PROPERTIES.setProperty("sonido_ascensor", String.valueOf(on));
        Helpers.savePropertiesFile();

        // Governs only the IN-GAME music (the waiting room has its own toggle, MUSICA_SALA).
        // The flag is read by effectiveLoopVolume; here we just refresh the in-game loop's
        // volume if it's playing so the change is heard instantly.
        Audio.refreshLoopVolume(Audio.ASCENSOR_VOLUME.getKey());
    }

    public static void setMusicaSala(boolean on) {

        GameFrame.MUSICA_SALA = on;

        Helpers.PROPERTIES.setProperty("musica_sala_espera", String.valueOf(on));
        Helpers.savePropertiesFile();

        // Only the waiting room track; refresh its loop if it's playing.
        Audio.refreshLoopVolume(Audio.WAITING_ROOM_VOLUME.getKey());
    }

    public static void setMusicaAbout(boolean on) {

        GameFrame.MUSICA_ABOUT = on;

        Helpers.PROPERTIES.setProperty("musica_about", String.valueOf(on));
        Helpers.savePropertiesFile();

        // Only the "About" dialog's track; refresh its loop if it's playing.
        Audio.refreshLoopVolume(Audio.ABOUT_VOLUME.getKey());
    }

    public static void setMusicaStats(boolean on) {

        GameFrame.MUSICA_STATS = on;

        Helpers.PROPERTIES.setProperty("musica_stats", String.valueOf(on));
        Helpers.savePropertiesFile();

        // Only the stats dialog's track; refresh its loop if it's playing.
        Audio.refreshLoopVolume(Audio.STATS_VOLUME.getKey());
    }

    public static void setSonidoEfectos(boolean on) {

        GameFrame.SONIDO_EFECTOS = on;

        Helpers.PROPERTIES.setProperty("sonido_efectos", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    public static void setSonidoBarajado(boolean on) {

        GameFrame.SONIDO_BARAJADO = on;

        Helpers.PROPERTIES.setProperty("sonido_barajado", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    public static void setSonidoReparto(boolean on) {

        GameFrame.SONIDO_REPARTO = on;

        Helpers.PROPERTIES.setProperty("sonido_reparto", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    public static void setSonidoDestape(boolean on) {

        GameFrame.SONIDO_DESTAPE = on;

        Helpers.PROPERTIES.setProperty("sonido_destape", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    public static void setSonidoDestapeMisCartas(boolean on) {

        GameFrame.SONIDO_DESTAPE_MIS_CARTAS = on;

        Helpers.PROPERTIES.setProperty("sonido_destape_mis_cartas", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    public static void setSonidoApostar(boolean on) {

        GameFrame.SONIDO_APOSTAR = on;

        Helpers.PROPERTIES.setProperty("sonido_apostar", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    public static void setSonidoFold(boolean on) {

        GameFrame.SONIDO_FOLD = on;

        Helpers.PROPERTIES.setProperty("sonido_fold", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    public static void setSonidoConteo(boolean on) {

        GameFrame.SONIDO_CONTEO = on;

        Helpers.PROPERTIES.setProperty("sonido_conteo", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    public static void setSonidoCargaStacks(boolean on) {

        GameFrame.SONIDO_CARGA_STACKS = on;

        Helpers.PROPERTIES.setProperty("sonido_carga_stacks", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    public static void setSonidoEntra(boolean on) {

        GameFrame.SONIDO_ENTRA = on;

        Helpers.PROPERTIES.setProperty("sonido_entra", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    public static void setSonidoSale(boolean on) {

        GameFrame.SONIDO_SALE = on;

        Helpers.PROPERTIES.setProperty("sonido_sale", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    public static void setSonidoInterruptor(boolean on) {

        GameFrame.SONIDO_INTERRUPTOR = on;

        Helpers.PROPERTIES.setProperty("sonido_interruptor", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    public static void setSonidoCaja(boolean on) {

        GameFrame.SONIDO_CAJA = on;

        Helpers.PROPERTIES.setProperty("sonido_caja", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    public static void setSonidoIgualar(boolean on) {

        GameFrame.SONIDO_IGUALAR = on;

        Helpers.PROPERTIES.setProperty("sonido_igualar", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    public static void setSonidoPasar(boolean on) {

        GameFrame.SONIDO_PASAR = on;

        Helpers.PROPERTIES.setProperty("sonido_pasar", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    public static void setSonidoAllin(boolean on) {

        GameFrame.SONIDO_ALLIN = on;

        Helpers.PROPERTIES.setProperty("sonido_allin", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    public static void setSonidoCiegas(boolean on) {

        GameFrame.SONIDO_CIEGAS = on;

        Helpers.PROPERTIES.setProperty("sonido_ciegas", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    public static void setSonidoUltimaMano(boolean on) {

        GameFrame.SONIDO_ULTIMA_MANO = on;

        Helpers.PROPERTIES.setProperty("sonido_ultima_mano", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    public static void setSonidoPausa(boolean on) {

        GameFrame.SONIDO_PAUSA = on;

        Helpers.PROPERTIES.setProperty("sonido_pausa", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    public static void setSonidoEntrarSala(boolean on) {

        GameFrame.SONIDO_ENTRAR_SALA = on;

        Helpers.PROPERTIES.setProperty("sonido_entrar_sala", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    public static void setSonidoTuTurno(boolean on) {

        GameFrame.SONIDO_TU_TURNO = on;

        Helpers.PROPERTIES.setProperty("sonido_tu_turno", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    public static void setSonidoAvisoTiempo(boolean on) {

        GameFrame.SONIDO_AVISO_TIEMPO = on;

        Helpers.PROPERTIES.setProperty("sonido_aviso_tiempo", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    public static void setSonidoFinPartida(boolean on) {

        GameFrame.SONIDO_FIN_PARTIDA = on;

        Helpers.PROPERTIES.setProperty("sonido_fin_partida", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    public static void setSonidoInicio(boolean on) {

        GameFrame.SONIDO_INICIO = on;

        Helpers.PROPERTIES.setProperty("sonido_inicio", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    public static void setSonidoConexion(boolean on) {

        GameFrame.SONIDO_CONEXION = on;

        Helpers.PROPERTIES.setProperty("sonido_conexion", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    public static void setSonidoIwtsth(boolean on) {

        GameFrame.SONIDO_IWTSTH = on;

        Helpers.PROPERTIES.setProperty("sonido_iwtsth", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    public static void setSonidoZoom(boolean on) {

        GameFrame.SONIDO_ZOOM = on;

        Helpers.PROPERTIES.setProperty("sonido_zoom", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    public static void setSonidoVistaCompacta(boolean on) {

        GameFrame.SONIDO_VISTA_COMPACTA = on;

        Helpers.PROPERTIES.setProperty("sonido_vista_compacta", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    public static void setSonidoScreenshot(boolean on) {

        GameFrame.SONIDO_SCREENSHOT = on;

        Helpers.PROPERTIES.setProperty("sonido_screenshot", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    public static void setSonidoTapete(boolean on) {

        GameFrame.SONIDO_TAPETE = on;

        Helpers.PROPERTIES.setProperty("sonido_tapete", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    public static void setSonidoVisor(boolean on) {

        GameFrame.SONIDO_VISOR = on;

        Helpers.PROPERTIES.setProperty("sonido_visor", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    public static void setSonidoVolumen(boolean on) {

        GameFrame.SONIDO_VOLUMEN = on;

        Helpers.PROPERTIES.setProperty("sonido_volumen", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    public static void setSonidoArranque(boolean on) {

        GameFrame.SONIDO_ARRANQUE = on;

        Helpers.PROPERTIES.setProperty("sonido_arranque", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    public static void setSonidoAviso(boolean on) {

        GameFrame.SONIDO_AVISO = on;

        Helpers.PROPERTIES.setProperty("sonido_aviso", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    public static void setSonidoError(boolean on) {

        GameFrame.SONIDO_ERROR = on;

        Helpers.PROPERTIES.setProperty("sonido_error", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    public static void setSonidoErrorRed(boolean on) {

        GameFrame.SONIDO_ERROR_RED = on;

        Helpers.PROPERTIES.setProperty("sonido_error_red", String.valueOf(on));
        Helpers.savePropertiesFile();
    }

    // Global host rule: enables/disables TTS for everyone. The "just for me" block lives
    // separately (AudioDeviceManager.isBlockTtsLocal). Static, and persists the local
    // preference so it can be preselected before the game; only broadcasts to clients if
    // you're the host.
    public static void setTTSGlobal(boolean on) {

        GameFrame.TTS_SERVER = on;
        // Clears the value inherited from the recovered game: while it stayed set, what got
        // persisted was the OLD value, so editing the rule would revert it and also persist
        // that reversion. Same fix the settings panel already applies on recover for the
        // other three rules.
        GameFrame.TTS_SERVER_RECOVER = null;

        Helpers.PROPERTIES.setProperty("tts_server", String.valueOf(on));
        Helpers.savePropertiesFile();

        GameFrame gf = getInstance();

        if (gf != null && gf.isPartida_local()) {
            Helpers.threadRun(() -> {
                gf.getCrupier().broadcastGAMECommandFromServer("TTS#" + (on ? "1" : "0"), null);
                // Persists the rule so it survives a stop+recover.
                GameFrame.persistRecoverSettings(gf.getCrupier().getSqlite_game_id());
            });
        }
    }

    // Global host rule: enables/disables voice notes for everyone.
    public static void setVoiceMessages(boolean on) {

        GameFrame.VOICE_MESSAGES = on;
        // Same reason as the TTS rule: with the inherited value still set, editing this rule
        // would persist the old one and the edit would be lost.
        GameFrame.VOICE_MESSAGES_RECOVER = null;

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

    // Host game rules (IWTSTH / Run It Twice / Rabbit Hunting). These used to live as
    // toggles in the Preferences menu + popup; the logic is now centralized here and
    // triggered by the "Game settings" dialog (and by the re-apply on recover). Only
    // broadcasts and persists if you're the host; on the client the flag is updated by the
    // incoming *RULE command. The broadcast happens under lock_fin_mano (as the original
    // handlers did) so the rule doesn't change mid-hand-resolution.
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

    // Splitting bot balance among humans: EDITABLE in-game (harmless, only affects the log's
    // 2nd table at the end; doesn't touch the audit). Same pattern as setIwtsthRule: on the
    // host it's broadcast to clients (BOTBALRULE) so their final settlement matches, and
    // persisted on recover.
    public static void setBotBalanceToHumans(boolean on) {

        GameFrame gf = getInstance();

        if (gf != null && gf.isPartida_local()) {
            Helpers.threadRun(() -> {
                synchronized (gf.getCrupier().getLock_fin_mano()) {
                    GameFrame.BOT_BALANCE_TO_HUMANS = on;
                    gf.getCrupier().broadcastGAMECommandFromServer("BOTBALRULE#" + (on ? "1" : "0"), null);
                    GameFrame.persistRecoverSettings(gf.getCrupier().getSqlite_game_id());
                }
            });
        } else {
            GameFrame.BOT_BALANCE_TO_HUMANS = on;
        }
    }

    // Bot rebuy: EDITABLE in-game (only read when a bot busts). On the host it's broadcast
    // (BOTREBUYRULE) to keep state consistent and persisted on recover. Same pattern as the
    // other live rules.
    public static void setBotRebuy(boolean on) {

        GameFrame gf = getInstance();

        if (gf != null && gf.isPartida_local()) {
            Helpers.threadRun(() -> {
                synchronized (gf.getCrupier().getLock_fin_mano()) {
                    GameFrame.BOT_REBUY = on;
                    gf.getCrupier().broadcastGAMECommandFromServer("BOTREBUYRULE#" + (on ? "1" : "0"), null);
                    GameFrame.persistRecoverSettings(gf.getCrupier().getSqlite_game_id());
                }
            });
        } else {
            GameFrame.BOT_REBUY = on;
        }
    }

    public static void setRunItTwiceRule(boolean on) {

        // Frozen during the all-in run-out: the vote is already being decided with the
        // current value, so it can't be changed until NUEVA_MANO.
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

        // Register the shutdown hook as early as possible to also cover early crashes
        // (Ctrl+C / closing the console during setup). The hook internally distinguishes
        // host from client:
        //   - Host: broadcasts SERVEREXITRECOVER (with password) to the peers.
        //   - Client: sends EXIT#testament to the host.
        registerShutdownHook();

        sala_espera = salaespera; //Up here so getParticipantes() doesn't blow up

        nick_local = nicklocal;

        partida_local = partidalocal;

        // The card/chip/back image cache (Card.updateCachedImages) is DERIVED from the zoom,
        // but the launcher's zoom spinner (Settings outside a game) only sets ZOOM_LEVEL
        // without rewinding it, since the start screen shows no cards to preview against.
        // Without this, changing the zoom at startup and starting a game in the SAME session
        // would mount the table with cards at the previous cache's scale (until now only Init
        // rebuilt it, at app startup or an in-game zoom change). Synced here, where the cache
        // is consumed, BEFORE mounting the table so cards are born at their real size for any
        // ZOOM_LEVEL (including 0, where startup's zoom() isn't applied). force=false leaves
        // it untouched if it's already correct.
        Card.updateCachedImages(1f + ZOOM_LEVEL * ZOOM_STEP, false);

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

        getContentPane().add(tapete);

        force_reconnect_menu.setEnabled(isPartida_local());

        setupScreenshotsMenu();

        compact_menu.setSelected(GameFrame.VISTA_COMPACTA > 0);

        menu_cinematicas.setSelected(GameFrame.CINEMATICAS_PREF);

        auto_fullscreen_menu.setSelected(GameFrame.AUTO_FULLSCREEN);

        // Defensive: if a previous game ended mid-run-out, the flag could have stayed on
        // (only NUEVA_MANO clears it). Reset when mounting the table so it doesn't start with
        // Run It Twice frozen in the settings dialog.
        GameFrame.RUN_IT_TWICE_LOCKED = false;

        last_hand_menu.setSelected(false);

        rebuy_now_menu.setSelected(false);

        chat_image_menu.setSelected(GameFrame.CHAT_IMAGES_INGAME);

        confirmar_menu.setSelected(GameFrame.CONFIRM_ACTIONS);

        auto_action_menu.setSelected(GameFrame.AUTO_ACTION_BUTTONS);

        auto_fit_zoom_menu.setSelected(GameFrame.AUTO_ZOOM);

        // "Settings": unified dialog with Appearance / Audio / Game tabs. The only entry
        // point to settings from the Preferences menu (replaces both the old audio entry and
        // "Game settings"). Hand-built field (initComponents is generated). Has a twin in the
        // table's popup menu and in the CommunityCardsPanel's gear icon.
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

        // Auto-rebuy on going broke: checkbox in Preferences right after "REBUY (next
        // hand)". Hand-built field (initComponents is generated); LOCAL preference with the
        // same menu<->popup sync pattern as the rest.
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

        // === "Appearance" menu on the menu bar, as close as possible to the table's popup:
        // groups fullscreen, zoom, compact view, clock, cinematics, animation and chat
        // images + confirm + decks and table mats. Existing items are re-parented (adding
        // them to a new menu removes them from their previous one), without touching
        // generated code. The old Zoom menu disappears from the bar (its controls move
        // inside). ===
        apariencia_menu = new javax.swing.JMenu(Translator.translate("menu.apariencia"));
        apariencia_menu.setFont(new java.awt.Font("Dialog", 0, 14));
        apariencia_menu.putClientProperty("i18n.key", "menu.apariencia");
        apariencia_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/gear.png")));

        apariencia_menu.add(full_screen_menu);
        apariencia_menu.add(auto_fullscreen_menu);
        apariencia_menu.add(compact_menu);

        // zoom_menu is left with only its zoom controls (the rest has already moved);
        // remove the separators that used to drag those items along.
        zoom_menu.remove(jSeparator5);
        zoom_menu.remove(jSeparator6);
        apariencia_menu.add(zoom_menu);

        apariencia_menu.add(time_menu);
        apariencia_menu.add(menu_cinematicas);

        // "Animation effects" submenu with three combinable effects (deal, blinds+dealer,
        // bets). Replaces the old single checkbox.
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
        efectos_anim_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/fx.png")));
        efectos_anim_menu.add(anim_reparto_menu);
        efectos_anim_menu.add(anim_ciegas_dealer_menu);
        efectos_anim_menu.add(anim_apuestas_menu);
        efectos_anim_menu.add(anim_contadores_menu);
        apariencia_menu.add(efectos_anim_menu);

        apariencia_menu.add(chat_image_menu);

        // "Call cost" toggle: overlay on the community cards showing what the local player
        // will have to put in to call. On by default.
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

        // Preferences loses the appearance items; clean up now-orphaned separators.
        opciones_menu.remove(jSeparator1);
        opciones_menu.remove(jSeparator7);
        opciones_menu.remove(jSeparator8);
        opciones_menu.remove(decks_separator);

        // IWTSTH/RIT/Rabbit moved to the "Game settings" dialog: their two separators in
        // Preferences are now orphaned, remove them.
        opciones_menu.remove(jSeparator2);
        opciones_menu.remove(jSeparator10);

        // "Confirm all actions" right below "AUTO buttons".
        opciones_menu.remove(confirmar_menu);
        int auto_action_index = java.util.Arrays.asList(opciones_menu.getMenuComponents()).indexOf(auto_action_menu);
        opciones_menu.insert(confirmar_menu, auto_action_index >= 0 ? auto_action_index + 1 : opciones_menu.getMenuComponentCount());

        // "AUTO call" — opens the auto-call dialog (Enabled + limit). Right below
        // "AUTO mode". Grayed out if "AUTO mode" is off.
        auto_call_menu = new javax.swing.JMenuItem();
        auto_call_menu.setFont(new java.awt.Font("Dialog", 0, 14));
        auto_call_menu.putClientProperty("i18n.key", "menu.auto_call");
        auto_call_menu.setText(Translator.translate("menu.auto_call"));
        auto_call_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/auto.png")));
        auto_call_menu.setEnabled(GameFrame.AUTO_ACTION_BUTTONS);
        auto_call_menu.addActionListener(e -> openAutoCallMaxDialog());
        int auto_call_index = java.util.Arrays.asList(opciones_menu.getMenuComponents()).indexOf(auto_action_menu);
        opciones_menu.insert(auto_call_menu, auto_call_index >= 0 ? auto_call_index + 1 : opciones_menu.getMenuComponentCount());

        // "Persist AUTO mode across hands" below "AUTO call". Grayed-out sibling: only
        // enabled with "AUTO mode" on.
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

        // "Confirm AUTO action (5s)" — AUTO MODE dialog toggle, in the same group,
        // below Persist. Grayed-out sibling: only with "AUTO mode" on.
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

        // Isolates the "AUTO buttons + children" group with separators above and below
        // (without duplicating one if there's already an adjacent separator).
        int auto_group_start = java.util.Arrays.asList(opciones_menu.getMenuComponents()).indexOf(auto_action_menu);
        if (auto_group_start > 0 && !(opciones_menu.getMenuComponent(auto_group_start - 1) instanceof javax.swing.JPopupMenu.Separator)) {
            opciones_menu.insertSeparator(auto_group_start);
        }
        int auto_group_end = java.util.Arrays.asList(opciones_menu.getMenuComponents()).indexOf(modo_auto_confirm_menu);
        if (auto_group_end >= 0 && (auto_group_end + 1 >= opciones_menu.getMenuComponentCount() || !(opciones_menu.getMenuComponent(auto_group_end + 1) instanceof javax.swing.JPopupMenu.Separator))) {
            opciones_menu.insertSeparator(auto_group_end + 1);
        }

        // "Stop the game" and "Exit" together, no separator between them.
        file_menu.remove(jSeparator11);

        // "Hand limit" leaves the menu (lives in the Game tab of the Settings dialog);
        // "Last hand" moves next to Stop game / Exit, as the FIRST item of that group.
        // Both items are still built (state stays in sync).
        file_menu.remove(max_hands_menu);
        file_menu.remove(last_hand_menu);
        file_menu.remove(jSeparator3);
        int last_hand_target = java.util.Arrays.asList(file_menu.getMenuComponents()).indexOf(halt_game_menu);
        if (last_hand_target >= 0) {
            file_menu.insert(last_hand_menu, last_hand_target);
        } else {
            file_menu.add(last_hand_menu);
        }

        // The "Appearance" submenu is built (re-parenting its items OUTSIDE the menu bar, so
        // they don't get duplicated) but is NO LONGER shown: all appearance settings now live
        // in the "Appearance" tab of the "Settings" dialog. Its items stay alive so the tab and
        // the table's popup can delegate to them via doClick().

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

        Helpers.resetBarra(tapete.getCommunityCards().getBarra_tiempo(), GameFrame.THINK_TIME);

        server_separator_menu.setVisible(partida_local);

        tapete.getCommunityCards().getTiempo_partida().setVisible(GameFrame.SHOW_CLOCK);

        time_menu.setSelected(GameFrame.SHOW_CLOCK);

        applyCompactableFlags();

        //Give everyone their stack (BUY IN could be parameterized)
        // Issue#9: RemotePlayer/LocalPlayer's buyin field is initialized with a field
        // initializer (= GameFrame.BUYIN) at the moment the slot is instantiated, which can
        // capture a stale value in hot-join or recovery scenarios. Here — where the table is
        // initialized with the game's current BUYIN (the source of truth) — both stack and
        // buyin are set for each slot so both reflect the configured value. On RECOVER this
        // later gets overwritten by recuperarDatosClavePartida for players with a balance row
        // in SQL (preserving legitimate rebuys); late-joiners with no row keep the buyin
        // assigned here.
        for (Player jugador : jugadores) {
            jugador.setStack(GameFrame.BUYIN);
            jugador.setBuyin(GameFrame.BUYIN);
        }

        // Opening fill wipe (Crupier.animateInitialStacks): if there's going to be a 0 ->
        // buy-in count-up, paint the label to 0 RIGHT NOW so the table appears with "empty"
        // seats and there's no flash of the full buy-in before the count-up starts. The MODEL
        // (stack) is still the buy-in set above; this is just the label. Same gate as
        // animateInitialStacks (counter animations on + not recover) so both always stay in sync.
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

        // Animation master (Settings), now that both menu bar AND popup are built: if
        // "animaciones" was saved off, DISABLES the 5 toggles WITHOUT unchecking them (the
        // gate is applied by the *On() helpers at each read site; the *_PREF flags aren't
        // touched). With the master on (default) nothing changes.
        applyAnimationMaster();

        addMouseWheelListener(this);

        Helpers.preserveOriginalFontSizes(THIS);

        Helpers.updateFonts(THIS, Helpers.GUI_FONT, null);

        tapete.getCommunityCards().getTiempo_partida().setFont(new Font("Monospaced", Font.BOLD, 28));

        Helpers.translateComponents(THIS, false);

        Helpers.translateComponents(Helpers.TapetePopupMenu.popup, false);

        // The "AUTO call" label carries the current state (ON/OFF) in parentheses; set here,
        // after translation, so it doesn't get overwritten.
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

    // Builds the final NICK / RESULT table (bordered grid, same style as the accounts table)
    // from an auditor snapshot ({stack, buyin}). The "(  )" token leaves the marker's gutter
    // blank (no role icon), aligned with the "(##)" header. Called once with the real result
    // and, if the bot balance was distributed, again with the adapted snapshot.
    private static String buildFinalResultTable(Map<String, Double[]> auditor) {
        ArrayList<String[]> fin_rows = new ArrayList<>();

        int fin_nick_w = "NICK".length();
        int fin_res_w = Translator.translate("ui.resultado").length();

        for (Map.Entry<String, Double[]> entry : auditor.entrySet()) {

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

        return fin_table.toString();
    }

    public void finTransmision(boolean partida_terminada) {

        // Tell the crupier's community-card network waits to bail NOW -- BEFORE we grab
        // lock_contabilidad for the auditor snapshot below. A run-it-twice SIDE-B deal in flight
        // holds that lock while blocking on the peers' unlock chains; those waits only watch
        // fin_de_la_transmision, which we can't set until we get the lock they're holding. This
        // early flag breaks that deadlock: the SIDE-B deal aborts, the hand is left in progress
        // (end=0) for the recover, the crupier releases the lock, and we proceed.
        if (crupier != null) {
            crupier.setTerminationPending();
        }

        // Unregister the shutdown hook: the game is ending through the normal path (host
        // abort, natural end, voluntary exit) and any EXIT the hook might send would already
        // be over a closed socket.
        unregisterShutdownHook();

        // Snapshot the auditor under lock_contabilidad BEFORE entering SQL_LOCK, to preserve
        // the global lock_contabilidad -> SQL_LOCK ordering (same order Crupier.run uses when
        // closing a hand via sqlUpdateHandEnd). Without the snapshot, nesting
        // synchronized(lock_contabilidad) inside SQL_LOCK inverts the order and produces an
        // AB-BA deadlock with Crupier.run.
        // The OFFICIAL result of the game (balance screen + stats + history) is ALWAYS the
        // REAL one: the live auditor is NEVER touched. auditor_snapshot carries that real
        // result (the usual final table). If BOT_BALANCE_TO_HUMANS is on, the redistribution
        // is computed on a SEPARATE copy (auditor_snapshot_adapted) that only feeds the log's
        // SECOND table: it's an "after the fact" real-money settlement between humans, not the
        // official result.
        HashMap<String, Double[]> auditor_snapshot = null;
        HashMap<String, Double[]> auditor_snapshot_adapted = null;
        boolean bot_balance_applied = false;
        if (partida_terminada && crupier != null) {
            synchronized (crupier.getLock_contabilidad()) {
                // print=false: refresh the auditor map for the snapshot WITHOUT dumping the
                // stacks table (NICK/STACK/BUYIN) to the log. That table is only printed when
                // each hand starts; the close is already summarized by the final NICK/RESULT
                // marker further below. Printing it here (from this finTransmision thread)
                // would also interleave it with the actions the Crupier thread kept logging.
                crupier.auditorCuentas(false);
                auditor_snapshot = new HashMap<>(crupier.getAuditor());
                if (GameFrame.BOT_BALANCE_TO_HUMANS) {
                    // Independent copy: redistributeBotBalanceToHumans REPLACES entries with new
                    // Double[]s (doesn't mutate existing ones), so the live auditor and
                    // auditor_snapshot keep the real values; only 'adapted' carries the split.
                    HashMap<String, Double[]> adapted = new HashMap<>(crupier.getAuditor());
                    bot_balance_applied = Crupier.redistributeBotBalanceToHumans(adapted);
                    if (bot_balance_applied) {
                        auditor_snapshot_adapted = adapted;
                    }
                }
            }
        }

        java.util.concurrent.CountDownLatch balance_latch = null;
        BalanceScreen[] balance_ref = new BalanceScreen[1];
        boolean run_cleanup = false;

        synchronized (GameFrame.SQL_LOCK) {
            if (!fin) {

                run_cleanup = true;

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
                        // dispose() (not just setVisible(false)): a fresh PauseDialog is created per
                        // pause, and its 1 Hz blink Timer + the ComponentListener it added to this
                        // long-lived GameFrame are released ONLY in formWindowClosed, which
                        // setVisible(false) never fires. Ending a game while paused would otherwise
                        // strand the timer forever and pin the whole dead GameFrame graph. Mirrors
                        // the resume path.
                        pausa_dialog.dispose();
                        pausa_dialog = null;
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

                    // We iterate the snapshot taken under lock_contabilidad OUTSIDE SQL_LOCK at
                    // the start of the method (see the comment there). Without retaking the
                    // lock here there's no SQL -> CONTAB nesting and thus no deadlock with
                    // Crupier.run.
                    if (auditor_snapshot != null) {

                        // The USUAL final table (real result, bots included).
                        getRegistro().print(buildFinalResultTable(auditor_snapshot));

                        // If the bots' combined balance was distributed: an explanatory note +
                        // ADAPTED table (bots neutral, balance split evenly among humans) BELOW
                        // the usual one. This is also what the balance screen shows.
                        if (bot_balance_applied && auditor_snapshot_adapted != null) {
                            getRegistro().print("($$) " + Translator.translate("balance.saldo_bots_repartido"));
                            getRegistro().print(buildFinalResultTable(auditor_snapshot_adapted));
                        }

                        getRegistro().setFin_transmision(true);
                    }

                }

                Timestamp ts = new Timestamp(GAME_START_TIMESTAMP);
                DateFormat timeZoneFormat = new SimpleDateFormat("dd_MM_yyyy__HH_mm_ss");
                Date date = new Date(ts.getTime());
                String fecha = timeZoneFormat.format(date);
                // Deferred sprint 🟠-24: nick sanitized for use as a filename segment. It used
                // to only replace spaces; nicks like CON, NUL, AUX, or containing :/*? broke
                // FileOutputStream silently and the game's log was lost. The reader
                // (StatsDialog) uses the same sanitizing to find the file — critical coordination.
                String log_file = Init.LOGS_DIR + "/CORONAPOKER_TIMBA_" + Helpers.safeNickForFilename(sala_espera.getServer_nick()) + "_" + fecha + ".log";

                // Drain the log queue: print() is asynchronous (LOG_POOL), so the footer +
                // final marker just enqueued might not yet be in LOG_TEXT when getText()
                // builds the .log. logFlush waits for them to apply so the file comes out
                // complete and in order.
                Helpers.logFlush();

                try {

                    String previous_log_data = "";

                    // ATOMIC write. The file accumulates the log of ALL games for the day: it's
                    // read in full, the new content appended, and rewritten, so truncating
                    // first would mean a mid-write crash wipes out the entire history, not just
                    // what was being appended.
                    if (Files.exists(Paths.get(log_file))) {

                        previous_log_data = "\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>" + log_file + "\n" + Files.readString(Paths.get(log_file)) + "\n<<<<<<<<<<<<<<<<<<<<<<<<<<<<" + log_file + "\n";
                        Helpers.writeStringAtomic(Paths.get(log_file), previous_log_data + getRegistro().getText());
                    } else {
                        Helpers.writeStringAtomic(Paths.get(log_file), getRegistro().getText());
                    }

                } catch (IOException ex1) {
                    Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex1);
                }

                if (!this.getSala_espera().getChat_text().toString().isEmpty()) {

                    // Deferred sprint 🟠-24: nick sanitized the same way as log_file above.
                    String chat_file = Init.LOGS_DIR + "/CORONAPOKER_CHAT_" + Helpers.safeNickForFilename(sala_espera.getServer_nick()) + "_" + fecha + ".html";

                    try {

                        String previous_chat_data = "";

                        final String chat_html_head = "<head><style>"
                                + ".bubble-mine,.bubble-other{padding:5px;border-radius:12px;}"
                                + ".bubble-mine{background-color:#d9fdd3;}"
                                + ".bubble-other{background-color:white;}"
                                + "</style></head>";

                        // Atomic for the same reason as the log: the whole day's history gets
                        // rewritten here too.
                        if (Files.exists(Paths.get(chat_file))) {

                            previous_chat_data = Files.readString(Paths.get(chat_file)).replaceAll("<html>(?:<head>.*?</head>)?<body.*?>(.*?)</body></html>", "$1");
                            Helpers.writeStringAtomic(Paths.get(chat_file), "<html>" + chat_html_head + "<body style='background-image: url(" + this.sala_espera.getBackground_chat_src() + ")'>" + previous_chat_data + this.sala_espera.txtChat2HTML(this.sala_espera.getChat_text().toString()) + "</body></html>");

                        } else {
                            Helpers.writeStringAtomic(Paths.get(chat_file), "<html>" + chat_html_head + "<body style='background-image: url(" + this.sala_espera.getBackground_chat_src() + ")'>" + this.sala_espera.txtChat2HTML(this.sala_espera.getChat_text().toString()) + "</body></html>");

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
                        // Final screen as an overlay on the glassPane (see showBalanceOverlay).
                        // Replaces the old modal balance dialog: this thread (end of
                        // transmission, outside the EDT) waits on the latch for the player to
                        // choose continue/menu, the same way it used to wait for the modal
                        // dialog to close, without freezing the EDT.
                        final java.util.concurrent.CountDownLatch screen_latch = new java.util.concurrent.CountDownLatch(1);
                        balance_latch = screen_latch;

                        Helpers.GUIRun(() -> {
                            // The button ONLY counts down the latch. It does NOT touch the
                            // database lock: this runs on the EDT, and requesting it here would
                            // leave it waiting for whoever holds it. The transmission thread
                            // releases SQL_LOCK before awaiting this signal, so the direct latch
                            // notification cannot deadlock with a settings save.
                            BalanceScreen balance = new BalanceScreen(GameFrame.getInstance(), screen_latch::countDown);
                            balance_ref[0] = balance;
                            showBalanceOverlay(balance);
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

            }

        }

        if (!run_cleanup) {
            return;
        }

        // Do not wait while holding SQL_LOCK. The final-screen button signals this latch
        // directly on the EDT, and other database users remain free while the player reads
        // the result. This replaces the old SQL_LOCK.wait(250 ms) polling loop.
        if (balance_latch != null) {
            awaitLatch(balance_latch);
            recover = balance_ref[0] != null && balance_ref[0].isRecover();

            final java.util.concurrent.CountDownLatch hide_latch = new java.util.concurrent.CountDownLatch(1);
            Helpers.GUIRun(() -> {
                try {
                    hideBalanceOverlay(balance_ref[0]);
                } finally {
                    hide_latch.countDown();
                }
            });
            awaitLatch(hide_latch);
        }

        synchronized (GameFrame.SQL_LOCK) {
            Helpers.SQLITEVAC();

            Helpers.closeSQLITE();

            KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();

            if (GameFrame.key_event_dispatcher != null) {
                kfm.removeKeyEventDispatcher(GameFrame.key_event_dispatcher);
                GameFrame.key_event_dispatcher = null;
            }
        }

        RESET_GAME(recover);

    }

    // Mounts the final screen (BalanceScreen) as an overlay on this frame's glassPane, OVER
    // the real table (visible through it: Swing COMPONENT transparency, not relying on the
    // OS compositor like the old dialog with per-pixel window transparency, which came out
    // with a gray background on some Linux setups). Leaves the board INERT under the final
    // screen (like the old modal dialog): the visible glassPane intercepts the mouse toward
    // the board, balance_overlay_active blocks the KeyEventDispatcher's shortcuts (all game
    // shortcuts go through it, not menu accelerators), and the table's context menu
    // (right-click) is removed. The frame is re-enabled because end-of-transmission cleanup
    // left it disabled, and without this the overlay's buttons (children of the frame)
    // wouldn't respond.
    private void showBalanceOverlay(BalanceScreen balance) {
        setEnabled(true);

        balance_overlay_active = true;

        TablePanel t = getTapete();
        if (t != null) {
            balance_saved_tapete_popup = t.getComponentPopupMenu();
            t.setComponentPopupMenu(null);
        }

        java.awt.Component glass = getGlassPane();

        if (glass instanceof JComponent) {
            JComponent gp = (JComponent) glass;
            gp.removeAll();
            gp.setOpaque(false);
            gp.setLayout(new java.awt.BorderLayout());
            gp.add(balance, java.awt.BorderLayout.CENTER);
            gp.revalidate();
        }

        glass.setVisible(true);

        balance.startAnimations();
    }

    // Dismounts the final screen overlay: releases its resources (cleanup), hides the
    // glassPane and empties it. After this the flow continues with RESET_GAME (which
    // discards this frame anyway).
    private void hideBalanceOverlay(BalanceScreen balance) {
        balance_overlay_active = false;

        if (balance != null) {
            balance.cleanup();
        }

        TablePanel t = getTapete();
        if (t != null) {
            t.setComponentPopupMenu(balance_saved_tapete_popup);
        }
        balance_saved_tapete_popup = null;

        java.awt.Component glass = getGlassPane();

        glass.setVisible(false);

        if (glass instanceof JComponent) {
            ((JComponent) glass).removeAll();
        }
    }

    private static void awaitLatch(java.util.concurrent.CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void RESET_GAME(boolean recover) {

        // Monitor the board (and final screen) were on: the start window is created
        // maximized on the primary display and is only hidden between games, so without this
        // it would reappear on the primary display even if the game was on a secondary
        // monitor. Captured BEFORE resetInstance.
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

            // Pending chat notifications (a voice note, an image) die with the game: they were
            // never cleared, so whatever was left unplayed would sound off at the start of the
            // NEXT game, from someone who might not even be there anymore.
            GameFrame.NOTIFY_CHAT_QUEUE.clear();

            // SHUTDOWN before resetLOG (not the other way around): shutdownNow() discards the
            // log tasks queued in LOG_POOL BEFORE LOG_TEXT is cleared, so no straggler from
            // the previous game can do a ghost append onto the next game's already-reset log.
            // logFlush() in finTransmision already drains the queue much earlier; this is
            // defense in depth (resetLOG is just a String assignment, doesn't use the pool, so
            // moving it after the shutdown is harmless).
            Helpers.SHUTDOWN_THREAD_POOL();

            GameLogDialog.resetLOG();

            // Quick chat's history (browsed with the arrow keys) wasn't cleared either:
            // messages typed in one game kept showing up in the next.
            FastChatDialog.resetHistorial();

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

        // LOG_TEXT's header ("[CoronaPoker X.Y - LOG...]") is built in a static initializer
        // that evaluates when the GameLogDialog class LOADS, at which point
        // GameFrame.LANGUAGE might not yet be the user's chosen language (it gets "baked in"
        // at the default language). Here, when the game starts, the language is already set
        // and there can't be any prints yet (registro_dialog was null), so we regenerate the
        // header in the correct language without losing any of the log.
        GameLogDialog.resetLOG();

        Helpers.GUIRunAndWait(() -> {
            registro_dialog = new GameLogDialog(this, false);
        });

        TTSWatchdog();

        // Telemetry: periodic server-side broadcaster (1 thread). Only active on the host
        // (isPartida_local). The loop exits at the end of transmission (same signal as
        // TTSWatchdog and the rest of the Crupier's threads — SHUTDOWN_THREAD_POOL also cuts
        // them all when the game closes). Best-effort: any failure is logged but does NOT
        // affect the game flow.
        if (isPartida_local()) {
            telemetryBroadcasterWatchdog();
        }

        // Defensive DB-state reset before the Crupier starts writing. A StatsSync import runs a
        // setAutoCommit(false) transaction on the SHARED SQLite connection off the network thread;
        // if it was interrupted mid-transaction (SHUTDOWN_THREAD_POOL at teardown does not await
        // its tasks) and its autoCommit restore also failed, the connection could be left stuck in
        // autoCommit=false — which would silently swallow this game's writes. Taking SQL_LOCK first
        // waits out any still-running import (its transaction has then already committed/rolled
        // back), so this only ever confirms — or repairs — a clean state. Off the EDT (AJUGAR runs
        // on a worker: it uses GUIRunAndWait below), so requesting SQL_LOCK here is invariant-safe.
        synchronized (GameFrame.SQL_LOCK) {
            try {
                java.sql.Connection sqlite = Helpers.getSQLITE();
                if (sqlite != null && !sqlite.getAutoCommit()) {
                    sqlite.setAutoCommit(true);
                    java.util.logging.Logger.getLogger(GameFrame.class.getName()).log(java.util.logging.Level.WARNING,
                            "AJUGAR: shared SQLite connection was left in autoCommit=false — reset defensively before starting the Crupier.");
                }
            } catch (Exception ex) {
                java.util.logging.Logger.getLogger(GameFrame.class.getName()).log(java.util.logging.Level.WARNING,
                        "AJUGAR: could not verify/reset SQLite autoCommit before starting the Crupier", ex);
            }
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
     * Telemetry: server-side thread that fires Crupier.broadcastTelemetryFrame() every
     * PING_INTERVAL_MS so clients keep their latest_telemetry fresh.
     *
     * Cycle:
     *   1. pause PING_INTERVAL_MS at the start (latency data needs at least ONE ping/pong
     *      round before there's anything to report).
     *   2. broadcast.
     *   3. loop until crupier.isFin_de_la_transmision().
     *
     * The thread lives in Helpers.THREAD_POOL — when the game closes, SHUTDOWN_THREAD_POOL
     * cuts it along with TTSWatchdog and the rest. If broadcast throws, log + continue
     * (telemetry is best-effort, must not abort the chain).
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
                                        // Dispose + null before dropping the reference: the
                                        // setVisible(false) above does NOT release the dialog's
                                        // native peer or anything else. Without this, TTS
                                        // notifications piled up zombie dialogs in long games (🟠-22 v2).
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
                                //Clients need to be told the game has ended
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

        if (zoomSonidoOn()) {
            Audio.playWavResource("misc/zoom_in.wav");
        }

        Helpers.threadRun(() -> {
            incrementZoom();
            Helpers.PROPERTIES.setProperty("zoom_level", String.valueOf(ZOOM_LEVEL));
            Card.updateCachedImages(1f + ZOOM_LEVEL * ZOOM_STEP, false);
            zoom(1f + ZOOM_LEVEL * ZOOM_STEP, null);
            InGameNotifyDialog.notifyZoom();
            if (jugadas_dialog != null && jugadas_dialog.isVisible()) {
                for (Card carta : jugadas_dialog.getCartas()) {
                    carta.invalidateImagePrecache();
                    carta.refreshCard();
                }
                Helpers.GUIRun(jugadas_dialog::pack);
            }
            if (shortcuts_dialog != null && shortcuts_dialog.isVisible()) {

                shortcuts_dialog.zoom(Helpers.DIALOG_ZOOM, null);

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

        if (zoomSonidoOn()) {
            Audio.playWavResource("misc/zoom_out.wav");
        }

        if (Helpers.doubleSecureCompare(0f, 1f + ((ZOOM_LEVEL - 1) * ZOOM_STEP)) < 0) {

            Helpers.threadRun(() -> {
                decrementZoom();
                Helpers.PROPERTIES.setProperty("zoom_level", String.valueOf(ZOOM_LEVEL));
                Card.updateCachedImages(1f + ZOOM_LEVEL * ZOOM_STEP, false);
                zoom(1f + ZOOM_LEVEL * ZOOM_STEP, null);
                InGameNotifyDialog.notifyZoom();
                if (jugadas_dialog != null && jugadas_dialog.isVisible()) {
                    for (Card carta : jugadas_dialog.getCartas()) {
                        carta.invalidateImagePrecache();
                        carta.refreshCard();
                    }
                    Helpers.GUIRun(jugadas_dialog::pack);
                }
                if (shortcuts_dialog != null && shortcuts_dialog.isVisible()) {

                    shortcuts_dialog.zoom(Helpers.DIALOG_ZOOM, null);

                }
                Helpers.savePropertiesFile();
            });

        }

    }//GEN-LAST:event_zoom_menu_outActionPerformed

    private void zoom_menu_resetActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoom_menu_resetActionPerformed
        // TODO add your handling code here:

        if (ZOOM_LEVEL != DEFAULT_ZOOM_LEVEL) {

            if (zoomSonidoOn()) {
                Audio.playWavResource("misc/zoom_reset.wav");
            }

            Helpers.threadRun(() -> {
                ZOOM_LEVEL = DEFAULT_ZOOM_LEVEL;
                Helpers.PROPERTIES.setProperty("zoom_level", String.valueOf(ZOOM_LEVEL));
                Card.updateCachedImages(1f + ZOOM_LEVEL * ZOOM_STEP, false);
                zoom(1f + ZOOM_LEVEL * ZOOM_STEP, null);
                InGameNotifyDialog.notifyZoom();
                if (jugadas_dialog != null && jugadas_dialog.isVisible()) {
                    for (Card carta : jugadas_dialog.getCartas()) {
                        carta.invalidateImagePrecache();
                        carta.refreshCard();
                    }
                    Helpers.GUIRun(jugadas_dialog::pack);
                }
                if (shortcuts_dialog != null && shortcuts_dialog.isVisible()) {

                    shortcuts_dialog.zoom(Helpers.DIALOG_ZOOM, null);

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

            // The default size/position (1280x720 centered console, not 0.8x the window)
            // applies only the FIRST time this dialog is opened; later reopenings keep
            // whatever the user resized/moved it to (closing the log only hides it).
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
     * Unified entry point to toggle fullscreen, from the menu listener or from
     * initialization paths (autoZoomFullScreen). autoZoomFullScreen used to call
     * full_screen_menu.doClick() to trigger this flow through the JMenuItem's listener; that
     * doClick was a Swing antipattern because it coupled initialization to the UI and
     * simulated synthetic events. Both paths now call directly here.
     */
    public void triggerFullScreenToggle() {
        if (full_screen_menu.isEnabled() && !isGame_over_dialog()) {
            full_screen_menu.setEnabled(false);
            Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(false);

            // Toggling ALWAYS flips the current state, so the target is !full_screen.
            // Persisted as the auto_fullscreen preference so the next game remembers the
            // mode, same as zoom and compact view. (When leaving the game, resetInstance calls
            // toggleFullScreen() directly, not this method, so exiting does NOT alter the preference.)
            persistFullScreenPreference(!full_screen);

            if (!Helpers.OSValidator.isMac() || !GameFrame.MAC_NATIVE_FULLSCREEN) {
                Helpers.TapetePopupMenu.FULLSCREEN_MENU.setSelected(!full_screen);
                toggleFullScreen();
            } else {
                toggleMacNativeFullScreen(GameFrame.getInstance());
            }
        }
    }

    // Saves the fullscreen preference (auto_fullscreen) and syncs the checkboxes in the
    // appearance menu and the table's popup. Does NOT change the window's state: the
    // corresponding toggle does that.
    private void persistFullScreenPreference(boolean fullscreen) {
        GameFrame.AUTO_FULLSCREEN = fullscreen;
        Helpers.PROPERTIES.setProperty("auto_fullscreen", String.valueOf(fullscreen));
        Helpers.savePropertiesFile();
        if (auto_fullscreen_menu != null) {
            auto_fullscreen_menu.setSelected(fullscreen);
        }
        if (Helpers.TapetePopupMenu.AUTO_FULLSCREEN_MENU != null) {
            Helpers.TapetePopupMenu.AUTO_FULLSCREEN_MENU.setSelected(fullscreen);
        }
    }

    // Display mode chosen in Settings > Appearance (windowed / fullscreen list). SAVES the
    // preference (also applied when the game STARTS via autoZoomFullScreen(AUTO_FULLSCREEN))
    // and APPLIES it right away if the current state differs.
    public void setDisplayModeFullScreen(boolean fullscreen) {
        persistFullScreenPreference(fullscreen);
        if (fullscreen != full_screen) {
            // The toggle disposes and recreates the frame's native peer, which corrupts a
            // modal dialog open on top of it. That's why the Settings combo does NOT apply
            // live: the dialog invokes this when SAVE is clicked (applyPendingDisplayMode),
            // right before closing. Deferred to the EDT to run after the close is drained.
            SwingUtilities.invokeLater(this::triggerFullScreenToggle);
        }
    }

    // Sets the compact view to a SPECIFIC value (0=off, 1=compact, 2=compact+cards,
    // 3=compact+cards+local), for the Settings > Appearance dropdown. Same logic as the
    // menu's cycle (compact_menuActionPerformed) but to a given target instead of (n+1)%4.
    public void setCompactView(int target) {
        target = ((target % 4) + 4) % 4;
        if (target == GameFrame.VISTA_COMPACTA) {
            return;
        }
        GameFrame.VISTA_COMPACTA = target;
        compact_menu.setSelected(target > 0);
        if (vistaCompactaSonidoOn()) {
            Audio.playWavResource("misc/power_" + (target > 0 ? "down" : "up") + ".wav");
        }
        Helpers.PROPERTIES.setProperty("vista_compacta", String.valueOf(target));
        Helpers.savePropertiesFile();
        Helpers.threadRun(this::vistaCompacta);
        Helpers.TapetePopupMenu.COMPACTA_MENU.setSelected(target > 0);
    }

    // Sets the zoom level to a SPECIFIC value, for the Settings > Appearance spinner.
    // Applies it in one shot (same work as zoom_menu_in/out/reset but to the target level).
    // The zoom factor must stay > 0 (same guard as the menu's zoom-out).
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
            InGameNotifyDialog.notifyZoom();
            if (jugadas_dialog != null && jugadas_dialog.isVisible()) {
                for (Card carta : jugadas_dialog.getCartas()) {
                    carta.invalidateImagePrecache();
                    carta.refreshCard();
                }
                Helpers.GUIRun(jugadas_dialog::pack);
            }
            if (shortcuts_dialog != null && shortcuts_dialog.isVisible()) {
                shortcuts_dialog.zoom(Helpers.DIALOG_ZOOM, null);
            }
            if (GameFrame.AUTO_ZOOM) {
                Helpers.threadRun(() -> {
                    Helpers.pausar(GameFrame.GUI_RENDER_WAIT);
                    tapete.autoZoom(false);
                });
            }
            // Coalesced flush: this path is triggered by the Settings spinner, which chains
            // one change per repeat while the arrow is held (the out-of-game zoom already does this).
            Helpers.savePropertiesFileDeferred();
        });
    }

    private void compact_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_compact_menuActionPerformed
        // TODO add your handling code here:

        GameFrame.VISTA_COMPACTA = (GameFrame.VISTA_COMPACTA + 1) % 4;

        this.compact_menu.setSelected(GameFrame.VISTA_COMPACTA > 0);

        if (vistaCompactaSonidoOn()) {
            Audio.playWavResource("misc/power_" + (GameFrame.VISTA_COMPACTA > 0 ? "down" : "up") + ".wav");
        }

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

        // "Persist AUTO" is only usable with "AUTO buttons" on: gray out/enable its
        // checkbox in both menus (menu bar and table popup).
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
                Helpers.zoomFonts(shortcuts_dialog, Helpers.DIALOG_ZOOM, null);
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
                if (interruptorSonidoOn()) {
                    Audio.playWavResource("misc/button_off.wav");
                }
            });

        } else if (crupier.atRebuyLimit(player.getNickname())) {

            rebuy_now_menu.setEnabled(true);
            Helpers.TapetePopupMenu.REBUY_NOW_MENU.setEnabled(true);
            rebuy_now_menu.setSelected(false);
            Helpers.TapetePopupMenu.REBUY_NOW_MENU.setSelected(false);

            Helpers.mostrarMensajeError(GameFrame.getInstance(), Translator.translate("rebuy.limite_alcanzado", String.valueOf(GameFrame.REBUY_LIMIT)));

        } else if (GameFrame.rebuyHeadroom(player.getStack()) < (GameFrame.FIXED_BUYIN ? 1 : GameFrame.getBuyinMin())) {

            // Already at the table cap: no headroom to rebuy.
            rebuy_now_menu.setEnabled(true);
            Helpers.TapetePopupMenu.REBUY_NOW_MENU.setEnabled(true);
            rebuy_now_menu.setSelected(false);
            Helpers.TapetePopupMenu.REBUY_NOW_MENU.setSelected(false);

            Helpers.mostrarMensajeError(GameFrame.getInstance(), Translator.translate("rebuy.sin_margen"));

        } else {

            // Live top-up: max = headroom (cap - stack), min and default depend on the mode
            // (fixed: [1, BUYIN]; variable: configured range [getBuyinMin, getBuyinDefault]),
            // clamped to the headroom so as not to exceed the cap. NO countdown (timeout -1):
            // an intra-hand rebuy is voluntary; the timer only applies at startup (initial
            // buy-in) and at game-over.
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
                    if (interruptorSonidoOn()) {
                        Audio.playWavResource("misc/button_on.wav");
                    }
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

            // Block modifications to the map while iterating
            synchronized (getParticipantes()) {
                for (Map.Entry<String, Participant> entry : getParticipantes().entrySet()) {

                    if (entry.getValue() != null && !entry.getValue().isCpu()) {
                        // With a watchdog: if the forced peer doesn't return within the grace
                        // period, force_reset_socket is released and it's given up on (without
                        // this, its transport would stay blocked forever).
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
        persistFullScreenPreference(auto_fullscreen_menu.isSelected());
    }//GEN-LAST:event_auto_fullscreen_menuActionPerformed

    private void halt_game_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_halt_game_menuActionPerformed
        // TODO add your handling code here:
        getCrupier().setForce_recover(true);
        exit_menuActionPerformed(evt);
    }//GEN-LAST:event_halt_game_menuActionPerformed

    // Auto-rebuy on going broke: checkbox in the Preferences menu and the popup.
    // Hand-built field (outside the generated block). LOCAL session preference synced menu<->popup.
    private javax.swing.JCheckBoxMenuItem auto_rebuy_menu;

    // "Game settings": entry in the Preferences menu (and its twin in the popup +
    // table icon) that opens the consolidated rules dialog. Hand-built field
    // (outside the editor-generated block).
    private javax.swing.JMenuItem ajustes_partida_menu;

    // "Appearance" menu on the menu bar (hand-built by re-parenting items); it's one of the
    // top-level menus, so the key dispatcher checks it to avoid stealing shortcuts while it's open.
    private javax.swing.JMenu apariencia_menu;

    // "Animation effects" submenu (hand-built): three combinable effects — dealing/uncovering
    // cards, position chips (blinds+dealer), and the pot chip (bets). Hand-built fields to sync
    // with the popup.
    private javax.swing.JCheckBoxMenuItem anim_reparto_menu;
    private javax.swing.JCheckBoxMenuItem anim_ciegas_dealer_menu;
    private javax.swing.JCheckBoxMenuItem anim_apuestas_menu;
    private javax.swing.JCheckBoxMenuItem anim_contadores_menu;

    public static final int ANIM_REPARTO = 0;
    public static final int ANIM_CIEGAS_DEALER = 1;
    public static final int ANIM_APUESTAS = 2;
    public static final int ANIM_CONTADORES = 3;

    // Applies an animation effect's change (flag + persistence) and reflects the state in
    // BOTH menus (menu bar and table popup). The shuffle.gif cache warm-up no longer depends
    // on this: it depends on SHUFFLE (triggered by its own checkbox in Settings and the
    // master), not on the deal.
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
    }

    // Reflects the four PREFERENCES in the eight checkboxes (menu bar + table popup).
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

    // Enables/disables (WITHOUT unchecking) the individual toggles -menu bar + popup-
    // according to the master: with the master off they're grayed out but keep their
    // preference. No longer recomputes any flag (the *_PREF flags are the raw preference; the
    // gate is applied by the *On() helpers at each read site). Called by startup and setAnimacionesMaster.
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

    // Master (Settings): turns ALL animations on/off at once (global gate; does NOT touch
    // individual preferences) and persists it. applyAnimationMaster only enables/disables the
    // toggles; the effect is applied by the *On() helpers.
    public void setAnimacionesMaster(boolean value) {
        GameFrame.ANIMACIONES = value;
        Helpers.PROPERTIES.setProperty("animaciones", String.valueOf(value));
        Helpers.savePropertiesFile();
        applyAnimationMaster();
        if (value && GameFrame.ANIMACION_BARAJADO_PREF) {
            Crupier.warmShuffleAnimCache();
        }
    }

    // Appearance toggle: call-cost overlay on the community cards.
    private javax.swing.JCheckBoxMenuItem coste_igualar_menu;

    public javax.swing.JCheckBoxMenuItem getCoste_igualar_menu() {
        return coste_igualar_menu;
    }

    // Applies the toggle's change (flag + persistence), reflects it in both menus (menu bar
    // and popup) and shows/hides the overlay immediately.
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

    // "Persist AUTO across hands": when on, a pressed AUTO button survives from one hand to
    // the next instead of resetting. Only makes sense (and is only enabled) with "AUTO
    // buttons" on. Hand-built field + menu<->popup sync, like the other toggles.
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

    // Toggle for the AUTO MODE confirmation dialog (vetoable countdown before each automatic
    // action). Only enabled with "AUTO buttons" on.
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

    // Menu item that opens the auto-call max selector. Only enabled with "AUTO buttons" on.
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

    // Refreshes the "AUTO call" label in the menu bar and the table popup so it shows in
    // parentheses whether it's ON or OFF (reuses the auto-call dialog's keys). Called when
    // building the menu and every time AUTO_CALL_ENABLED changes.
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

    // Opens the modal AUTO CALL dialog: an Enabled checkbox (on/off), a No limit checkbox
    // (maps to AUTO_CALL_MAX = 0), and an editable spinner for the max amount.
    public void openAutoCallMaxDialog() {
        // As a safety measure, opening this setting disarms any pre-selected AUTO button: the
        // threshold might be about to change, and we don't want an already-armed check/call to
        // fire with the old limit if our turn arrives while the dialog is open.
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

    // Opens the unified "Settings" dialog (Appearance / Audio / Game tabs). The single entry
    // point for all three access paths (Preferences menu, table popup, and the
    // CommunityCardsPanel icon).
    public void openSettingsDialog() {
        SettingsDialog dialog = new SettingsDialog(this, true);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    // Auto-rebuy on going broke: LOCAL preference (not broadcast to the host, doesn't change
    // the game's rules). Just toggles the flag and reflects the state in the popup's twin checkbox.
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
