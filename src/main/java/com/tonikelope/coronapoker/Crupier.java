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

import com.tonikelope.coronapoker.crypto.RistrettoSRA;
import com.tonikelope.coronapoker.crypto.UnlockChainWire;
import com.tonikelope.coronapoker.crypto.DealChain;

import com.drew.imaging.ImageProcessingException;
import static com.tonikelope.coronapoker.Card.BARAJAS;
import static com.tonikelope.coronapoker.GameFrame.WAIT_QUEUES;
import java.awt.Color;
import java.awt.Image;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.text.MessageFormat;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import javax.swing.JLabel;

/**
 *
 * @author tonikelope This croupier does too many things, but at least it does
 * them well.
 */
public class Crupier implements Runnable, com.tonikelope.coronapoker.bot.context.DealerView {

    private static final Logger LOGGER = Logger.getLogger(Crupier.class.getName());

    /**
     * Whitelist de tipos permitidos en la deserialización Java del payload
     * RECOVERDATA recibido del host. Sin este filtro, ObjectInputStream.readObject
     * acepta cualquier clase Serializable del classpath, lo que permite a un host
     * hostil enviar un gadget chain (jna, jaxb, sqlite-jdbc, soundlibs…) y obtener
     * RCE en cualquier cliente que pida recovery.
     *
     * Tipos legítimos del map (ver Crupier.sqlRecoverGameInfo* y sqlRecoverHand*):
     *   HashMap (root) → String keys → values de tipo
     *   String, Integer, Long, Float, Double, Boolean.
     *
     * Limits añadidos: 10 MB total, profundidad 20, 10k refs/array. El payload
     * legítimo más grande observado ronda decenas de KB (balances + metadatos).
     *
     * Cualquier clase no listada — incluyendo java.io.File, java.lang.Runtime,
     * gadgets de Apache Commons, jaxb, etc. — provoca rechazo del payload.
     */
    static final ObjectInputFilter RECOVERY_OBJECT_FILTER = ObjectInputFilter.Config.createFilter(
            "maxbytes=" + (10 * 1024 * 1024) + ";"
            + "maxdepth=20;maxrefs=10000;maxarray=10000;"
            // HashMap root + nested classes internas (Node, Set views, etc.)
            + "java.util.HashMap$**;"
            + "java.util.HashMap;"
            // Map.Entry (y su array) lo emite HashMap.writeObject como bucket internamente.
            + "java.util.Map$Entry;"
            + "java.util.Map$**;"
            // Wrappers numéricos y String — los únicos values legítimos del map.
            + "java.lang.String;"
            + "java.lang.Number;"
            + "java.lang.Integer;"
            + "java.lang.Long;"
            + "java.lang.Float;"
            + "java.lang.Double;"
            + "java.lang.Boolean;"
            // Tipos primitivos (cuando se deserializan campos `int` etc. dentro de
            // los wrappers — Java los referencia por nombre primitivo).
            + "int;long;float;double;boolean;byte;char;short;void;"
            // DENY everything else (gadgets en jna, jaxb, sqlite-jdbc, soundlibs...).
            + "!*"
    );

    /**
     * Exposed for the AAA test that verifies the filter accepts legitimate
     * RECOVERY payloads and rejects everything else (RecoveryObjectFilterSmoke).
     */
    public static ObjectInputFilter getRecoveryObjectFilter() {
        return RECOVERY_OBJECT_FILTER;
    }

    /**
     * Sprint deferred 🟠-2: muta action[] in-place a FOLD voluntario sintético.
     * Usado en DOS situaciones simétricas:
     *   1. El peer (sender de la ACTION) ya está marcado isExit() — su decisión
     *      no llegó; sintetizamos fold para que la rueda de apuestas avance.
     *      (caso existente desde antes, líneas 6926-6931)
     *   2. La firma Ed25519 de la ACTION recibida del wire es inválida — un
     *      peer (o host hostil con clave robada) intentó falsificar la
     *      decisión. NO debemos aplicar la decisión/bet falsificados al state
     *      del juego — se desplaza al peer perdiendo posiblemente fichas.
     *      Sintetizamos fold para que el peor caso sea "ese peer pierde su
     *      turno". (NUEVO en este commit)
     *
     * action[] post-call (mismo contrato que el exit-synth existente):
     *   action[0] = Player.FOLD     // decision sintética
     *   action[1] = 0f              // bet=0
     *   action[2] = null            // sin cinematic
     *   action[3] = null            // sin record para absorber en el chain
     *   action[4] = null            // sin sig
     *   action[5] = Boolean.FALSE   // NO voluntario (rondaApuestas keys off
     *                                 esto para saltar absorbActionIntoChain
     *                                 y broadcast — la cadena converge por
     *                                 omisión mutua: si todos los peers
     *                                 detectan el mismo bad sig, todos
     *                                 absorben nothing y H_t avanza igual)
     *
     * El array debe tener length >= 6 (contrato del action[] del Sprint
     * Identity, comentado en readActionFromRemotePlayer).
     */
    static void synthesizeFoldAction(Object[] action) {
        if (action == null || action.length < 6) {
            throw new IllegalArgumentException(
                    "action must have length >= 6 (slots [decision, bet, cinematic, record, sig, voluntary])");
        }
        action[0] = Player.FOLD;
        action[1] = 0d;
        action[2] = null;
        action[3] = null;
        action[4] = null;
        action[5] = Boolean.FALSE;
    }

    public static final boolean ALLIN_BOT_TEST = false; // TRUE FOR TESTING (Init.DEV_MODE MUST BE TRUE)

    public static final String[] STREETS = new String[]{"Preflop", "Flop", "Turn", "River"};

    public static final Map.Entry<String, Object[][]> ALLIN_CINEMATICS = new HashMap.SimpleEntry<>("allin/",
            new Object[][]{
                {"rounders.gif"},
                {"hulk.gif"},
                {"nicolas_cage.gif"},
                {"nicolas_cage2.gif"},
                {"training_day.gif"},
                {"wallstreet.gif"},
                {"casinoroyale.gif"},
                {"joker.gif"},
                {"terminator2.gif"}
            });

    public static volatile Map.Entry<String, Object[][]> ALLIN_CINEMATICS_MOD = null;

    public static final Map.Entry<String, String[]> ALLIN_SOUNDS_ES = new HashMap.SimpleEntry<>("joke/es/allin/",
            new String[]{
                "dolor.wav",
                "final_flash.wav",
                "follon.wav",
                "kbill_allin.wav",
                "maximo.wav",
                "mclane.wav",
                "montoya.wav",
                "pecho.wav",
                "puteado.wav",
                "sonrie.wav",
                "vanidoso.wav",
                "vietnam.wav"});

    public static final Map.Entry<String, String[]> ALLIN_SOUNDS_EN = new HashMap.SimpleEntry<>("joke/en/allin/",
            new String[]{});

    public static final Map<String, Map.Entry<String, String[]>> ALLIN_SOUNDS = new HashMap<>();

    public static volatile Map.Entry<String, String[]> ALLIN_SOUNDS_MOD = null;

    public static final Map.Entry<String, String[]> FOLD_SOUNDS_ES = new HashMap.SimpleEntry<>("joke/es/fold/",
            new String[]{
                "fary.wav",
                "mamar_pollas.wav",
                "maricon.wav",
                "marines.wav",
                "mcfly.wav",
                "mierda_alta.wav",
                "percibo_miedo.wav"});

    public static final Map.Entry<String, String[]> FOLD_SOUNDS_EN = new HashMap.SimpleEntry<>("joke/en/fold/",
            new String[]{});

    public static final Map<String, Map.Entry<String, String[]>> FOLD_SOUNDS = new HashMap<>();

    public static volatile Map.Entry<String, String[]> FOLD_SOUNDS_MOD = null;

    public static final Map.Entry<String, String[]> SHOWDOWN_SOUNDS_ES = new HashMap.SimpleEntry<>("joke/es/showdown/",
            new String[]{
                "berto.wav",
                "bond.wav",
                "kbill_show.wav"});

    public static final Map.Entry<String, String[]> SHOWDOWN_SOUNDS_EN = new HashMap.SimpleEntry<>("joke/en/showdown/",
            new String[]{
                "berto.wav",
                "bond.wav",
                "kbill_show.wav"});

    public static final Map<String, Map.Entry<String, String[]>> SHOWDOWN_SOUNDS = new HashMap<>();

    public static volatile Map.Entry<String, String[]> SHOWDOWN_SOUNDS_MOD = null;

    public static final Map.Entry<String, String[]> WINNER_SOUNDS_ES = new HashMap.SimpleEntry<>("joke/es/winner/",
            new String[]{
                "ateam.wav",
                "divertido.wav",
                "dura.wav",
                "fisuras.wav",
                "lacasitos.wav",
                "nadie_te_aguanta.wav",
                "planesbien.wav",
                "reymundo.wav",
                "vivarey.wav"});

    public static final Map.Entry<String, String[]> WINNER_SOUNDS_EN = new HashMap.SimpleEntry<>("joke/en/winner/",
            new String[]{});

    public static final Map<String, Map.Entry<String, String[]>> WINNER_SOUNDS = new HashMap<>();

    public static volatile Map.Entry<String, String[]> WINNER_SOUNDS_MOD = null;

    public static final Map.Entry<String, String[]> LOSER_SOUNDS_ES = new HashMap.SimpleEntry<>("joke/es/loser/",
            new String[]{
                "afregar.wav",
                "bambi.wav",
                "elgolpe.wav",
                "encargado.wav",
                "hammond.wav",
                "matias.wav",
                "mierda.wav",
                "nomejodas.wav",
                "pasta.wav",
                "presion.wav",
                "primo.wav",
                "quecabron.wav",
                "vamos_no_me_jodas.wav"});

    public static final Map.Entry<String, String[]> LOSER_SOUNDS_EN = new HashMap.SimpleEntry<>("joke/en/loser/",
            new String[]{});

    public static final Map<String, Map.Entry<String, String[]>> LOSER_SOUNDS = new HashMap<>();

    public static volatile Map.Entry<String, String[]> LOSER_SOUNDS_MOD = null;

    static {

        ALLIN_SOUNDS.put("es", ALLIN_SOUNDS_ES);
        FOLD_SOUNDS.put("es", FOLD_SOUNDS_ES);
        WINNER_SOUNDS.put("es", WINNER_SOUNDS_ES);
        LOSER_SOUNDS.put("es", LOSER_SOUNDS_ES);
        SHOWDOWN_SOUNDS.put("es", SHOWDOWN_SOUNDS_ES);

        ALLIN_SOUNDS.put("en", ALLIN_SOUNDS_EN);
        FOLD_SOUNDS.put("en", FOLD_SOUNDS_EN);
        WINNER_SOUNDS.put("en", WINNER_SOUNDS_EN);
        LOSER_SOUNDS.put("en", LOSER_SOUNDS_EN);
        SHOWDOWN_SOUNDS.put("en", SHOWDOWN_SOUNDS_EN);
    }

    public static final int CARTAS_MAX = 5;
    public static final int CARTAS_ESCALERA = 5;
    public static final int CARTAS_COLOR = 5;
    public static final int CARTAS_POKER = 4;
    public static final int CARTAS_TRIO = 3;
    public static final int CARTAS_PAREJA = 2;
    public static final int PREFLOP = 1;
    public static final int FLOP = 2;
    public static final int TURN = 3;
    public static final int RIVER = 4;
    public static final int SHOWDOWN = 5;
    public static final int REPARTIR_PAUSA = 250; // 2 players
    public static final int CARD_ANIMATION_DELAY = 100;
    // Confirmación diagnóstica (una vez por sesión) de qué motor reproduce los giros de carta
    private static volatile boolean PRE_RENDERED_ENGINE_LOGGED = false;
    // Confirmación diagnóstica (una vez por sesión) de qué motor reproduce el barajado
    private static volatile boolean PRE_RENDERED_SHUFFLE_LOGGED = false;
    // Tope de memoria para pre-decodificar shuffle.gif (las barajas integradas
    // rondan los 43 MB gracias al fast path indexado; un shuffle.gif de mod que
    // estime por encima cae a la ruta legacy Toolkit en vez de tragarse la RAM)
    public static final long PRE_RENDERED_SHUFFLE_MAX_BYTES = 64L * 1024 * 1024;
    // Frame (1-based) de cada vuelta del gif en el que se CORTA shuffle.wav, antes
    // del ultimo frame a proposito: deja margen para que el buffer de salida del
    // dispositivo (que va por detras) termine de drenar ANTES de que la vuelta
    // acabe visualmente. Si se corta justo al final, el sonido se oye un pelin
    // despues de que la animacion desaparece.
    public static final int SHUFFLE_AUDIO_STOP_FRAME = 53;
    // Caché del shuffle.gif pre-decodificado de la baraja ACTUAL (una sola
    // entrada, keyed por URL): el decode de ~0,5 s se paga una única vez por
    // baraja (normalmente en el warm-up de arranque/cambio de baraja, fuera de
    // la partida) y la animación queda residente (~43 MB las barajas
    // integradas) hasta que se cambie de baraja, que la reemplaza y libera la
    // anterior. Un value null cachea también el fallo (GIF de mod
    // indecodificable o sobre el tope): un único intento y un único WARNING
    // por baraja.
    private static volatile Map.Entry<String, PreRenderedGif> SHUFFLE_ANIM_CACHE = null;

    private static final Object SHUFFLE_ANIM_LOCK = new Object();

    // URL del shuffle.gif de la baraja actual (fichero del mod o recurso
    // integrado), null si la baraja no tiene
    public static URL shuffleGifUrl() {

        String baraja = GameFrame.BARAJA;

        boolean baraja_mod = (boolean) ((Object[]) BARAJAS.get(baraja))[1];

        if (baraja_mod && Files.exists(
                Paths.get(Helpers.getCurrentJarParentPath() + "/mod/decks/" + baraja + "/gif/shuffle.gif"))) {
            try {
                return Paths
                        .get(Helpers.getCurrentJarParentPath() + "/mod/decks/" + baraja + "/gif/shuffle.gif")
                        .toUri().toURL();
            } catch (MalformedURLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

        } else if (Crupier.class.getResource("/images/decks/" + baraja + "/gif/shuffle.gif") != null) {
            return Crupier.class.getResource("/images/decks/" + baraja + "/gif/shuffle.gif");
        }

        return null;
    }

    // Shuffle.gif pre-decodificado de la caché, decodificándolo si no está
    // (null si no se deja o excede el tope: ruta legacy). Sincronizado para
    // que el warm-up y el hilo del barajado nunca paguen el decode dos veces.
    public static PreRenderedGif getShuffleAnim(URL url_icon) {

        String url_key = url_icon.toString();

        synchronized (SHUFFLE_ANIM_LOCK) {

            Map.Entry<String, PreRenderedGif> cache = SHUFFLE_ANIM_CACHE;

            if (cache != null && url_key.equals(cache.getKey())) {
                return cache.getValue();
            }

            PreRenderedGif anim = null;

            try {
                anim = PreRenderedGif.decode(url_icon, PRE_RENDERED_SHUFFLE_MAX_BYTES);
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Shuffle GIF pre-decode failed (legacy Toolkit animation fallback)", ex);
            }

            if (anim != null && !PRE_RENDERED_SHUFFLE_LOGGED) {
                PRE_RENDERED_SHUFFLE_LOGGED = true;
                LOGGER.log(Level.INFO, "Shuffle animation: pre-rendered catch-up engine active ({0} frames / {1} ms)",
                        new Object[]{anim.getFrameCount(), anim.getTotalMs()});
            }

            SHUFFLE_ANIM_CACHE = new HashMap.SimpleEntry<>(url_key, anim);

            return anim;
        }
    }

    // Calienta la caché en background (arranque, cambio de baraja, animaciones
    // reactivadas) para que la primera mano no pague el decode de ~0,5 s
    public static void warmShuffleAnimCache() {

        if (!GameFrame.repartoAnimOn()) {
            return;
        }

        Helpers.threadRun(() -> {

            URL url_icon = shuffleGifUrl();

            if (url_icon != null) {
                getShuffleAnim(url_icon);
            }
        });
    }
    public static final int MIN_ULTIMA_CARTA_JUGADA = Hand.TRIO;
    public static final double[][] CIEGAS = new double[][]{new double[]{0.1, 0.2}, new double[]{0.2, 0.4},
    new double[]{0.3, 0.6}, new double[]{0.5, 1.0}};
    public static volatile boolean FUSION_MOD_SOUNDS = true;
    public static volatile boolean FUSION_MOD_CINEMATICS = true;
    public static final int NEW_HAND_READY_WAIT = 1000;
    // Timeout duro para las fases SRA peer-a-peer en las que el host espera la
    // respuesta de un cliente concreto (DECK_ROTATION_REQ y REQ_SRA_UNLOCK_CHAIN).
    // Reproducido en issue#9: un peer reconectado mid-cascade no tiene los scalars
    // SRA generados (los crea solo en el handler DECK_CASCADE_REQ); su Crupier
    // rechaza silenciosamente -> el host esperaba hasta que el socket del peer
    // moria naturalmente (60-90s), provocando shuffling animation infinito y
    // un loop de MISDEAL+recover. Con timeout el host aborta la mano y vuelve a un
    // estado consistente. Ampliado de 30s a 60s para dar mas margen a un peer lento
    // (la rotacion es barata, un solo scalarMul), y ademas ahora queda POR ENCIMA de
    // RECIBIDO_TIMEOUT (45s): a un peer realmente muerto lo caza antes el ping-pong
    // (lo marca exit) que este timeout, y sigue por debajo de la muerte natural del
    // socket (60-90s), asi que no cuelga. Un caido/reconectando aqui NO se acusa
    // (ver el llamador de requestRemoteRotation, fix D).
    public static final int REMOTE_SRA_PEER_TIMEOUT_MS = 60000;
    // Deadline de PROGRESO para la cascada (DECK_CASCADE_RESP). Un peer que contesta PING/PONG
    // (sigue "vivo") pero CALLA su respuesta de cascada dejaría al host esperando para siempre
    // -> mesa congelada (p.isExit() no se activa porque el PING lo mantiene vivo). MUCHO más
    // generoso que REMOTE_SRA_PEER_TIMEOUT_MS porque el paso de cascada puede incluir la
    // generación de la prueba de barajado en un PC lento: err generoso para NUNCA abortar a un
    // cliente legítimo lento; solo acota la congelación de infinito a este tope.
    public static final int REMOTE_CASCADE_RESP_TIMEOUT_MS = 120000;
    public static final int PAUSA_DESTAPAR_CARTA = 1000;
    public static final int PAUSA_DESTAPAR_CARTA_ALLIN = 2000;
    public static final int PAUSA_ENTRE_DESTAPES_SHOWDOWN = 1000;
    public static final int PAUSA_ENTRE_MANOS = 10; // Segundos
    public static final int PAUSA_ENTRE_MANOS_TEST = 1;
    public static final int PAUSA_ANTES_DE_SHOWDOWN = 1; // Segundos
    public static final int NEW_HAND_READY_WAIT_TIMEOUT = 30000;
    // Deadline de PROGRESO para HAND_READY (arranque de la mano siguiente). PAUSE-AWARE: mientras la timba
    // esté PAUSADA el reloj no corre (un peer legítimo puede callar HAND_READY porque el juego está en
    // pausa), así que solo cuenta el tiempo con la timba EN MARCHA. Muy generoso para cubrir clientes lentos
    // en CPU o red y las animaciones de fin de mano. Al vencer se expulsa SOLO al peer que retiene (la mesa
    // sigue). Antes NO había timeout y un peer que contesta PING pero no manda HAND_READY congelaba la mesa.
    public static final int HAND_READY_PROGRESS_TIMEOUT_MS = 120000;
    // Deadline de PROGRESO para un broadcast SÍNCRONO del host (espera de ACK de todos los peers).
    // PAUSE-AWARE. Un peer que contesta PING pero NO confirma el broadcast hacía reintentar al host para
    // siempre (congelaba MEGAPACKET / POCKET_CARDS / START_SRA_CASCADE / MISDEAL / HANDVERIFY). Muy generoso
    // para cubrir clientes lentos procesando una baraja SRA grande. Al vencer se expulsa SOLO al que retiene.
    public static final int BROADCAST_PROGRESS_TIMEOUT_MS = 180000;
    public static final int IWTSTH_ANTI_FLOOD_TIME = 15 * 60 * 1000; // 15 minutes BAN
    public static final int IWTSTH_TIMEOUT = 15000;
    public static final int RIT_VOTE_TIMEOUT = 15; // Segundos que dura la votación run-it-twice (timeout = NORMAL)
    public static final int STRADDLE_DECISION_TIMEOUT = 10; // Segundos que el UTG tiene para decidir el straddle voluntario (timeout = NO straddle). Al declinar, como no hay straddle el UTG es el primero en hablar -> rondaApuestas le arranca su turno normal (esTuTurno: think-time + HABLAS TU / PENSANDO)
    public static final int STRADDLE_RESULT_WAIT_TIMEOUT = 20; // Tope (s) que el cliente espera el STRADDLE_RESULT del host antes de asumir NO (cubre el peor caso del host ~9s + holgura de red; evita cuelgue si el host hizo early-return sin difundir)
    private static final double BOT_STRADDLE_PROBABILITY = 0.12; // Probabilidad de que un bot UTG ponga un straddle voluntario (calibrable)
    public static final int MONTECARLO_ITERATIONS = 1000;// Suficiente para tener un compromiso entre
    // velocidad/precisión
    public static final int RABBIT_LABEL_TIMEOUT = 3000;

    public static volatile boolean SECURITY_LOCKDOWN = false;

    // Aviso suave UNA vez por anomalia DISTINTA y por partida (no one-shot global: si no, un aviso
    // temprano se tragaba todos los siguientes). Llave = el reason (mensaje distinto por anomalia).
    private final java.util.Set<String> suspicious_host_warned_reasons = java.util.concurrent.ConcurrentHashMap.newKeySet();

    // Simetrico host-side: una alerta por (peer + anomalia) por partida, para no spamear popups bajo un
    // ataque activo. Llave = peerNick + "|" + reason.
    private final java.util.Set<String> malicious_peer_warned_reasons = java.util.concurrent.ConcurrentHashMap.newKeySet();

    // Anti-DoS: un peer que rechaza el barajado (cascada) o la rotacion del mazo (crypto invalida o
    // withhold) fuerza un MISDEAL en ESA mano; sin mas, podria repetirlo cada mano y dejar la timba sin
    // progreso (loop de MISDEAL, quema CPU). Un cliente de la MISMA version JAMAS produce estos fallos, asi
    // que se cuentan por peer y por partida; alcanzado el tope se AUTO-EXPULSA (la mesa sigue sin el). Tope
    // pequeno pero > 1 para presumir buena fe ante un fallo puntual.
    private static final int MAX_DEAL_REFUSAL_STRIKES = 3;
    private final java.util.concurrent.ConcurrentHashMap<String, Integer> deal_refusal_strikes = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Aviso SUAVE de comportamiento anómalo del host del que el juego PUEDE recuperarse y
     * que NO da certeza de manipulación (siempre presumimos buena fe: podría ser un bug del
     * software). A diferencia de {@link #triggerSecurityLockdown}, NO congela el saldo, NO
     * cierra el socket ni termina la partida: solo informa al usuario UNA vez por sesión y
     * recomienda ENCARECIDAMENTE abandonar la mesa, dejando que el juego siga si puede. Se usa
     * cuando ya hemos neutralizado la anomalía rechazando la operación (p.ej. una segunda
     * rotación) y congelar sería desproporcionado si resultara ser un bug.
     */
    // RECOVER anti-chip-theft: lee el saldo VERAZ del PROPIO SQLite del cliente (misma query que usa el
    // host para construir el RECOVERDATA, pero sobre MI BD — cada peer persiste balance por mano). Devuelve
    // player -> {stack, buyin, rebuy_count}. Robusto: ante cualquier fallo devuelve mapa VACÍO (el balance
    // se fía entonces del host, como antes).
    private java.util.Map<String, double[]> readLocalRecoverBalances() {
        java.util.Map<String, double[]> out = new java.util.HashMap<>();
        String sql = "select balance.player as PLAYER, round(balance.stack,2) as STACK, balance.buyin as BUYIN, balance.rebuy_count as REBUY_COUNT from balance,hand,game where balance.id_hand=hand.id and game.id=? and hand.id=(SELECT max(hand.id) from hand,balance where hand.id=balance.id_hand and hand.id_game=?)";
        try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
            statement.setQueryTimeout(30);
            statement.setInt(1, this.sqlite_id_game);
            statement.setInt(2, this.sqlite_id_game);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString("PLAYER"),
                            new double[]{rs.getDouble("STACK"), rs.getInt("BUYIN"), rs.getInt("REBUY_COUNT")});
                }
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "RECOVER: could not read local balances for reconciliation (falling back to host)", ex);
        }
        return out;
    }

    // Antepone "SOSPECHOSO: host «X»" al mensaje cuando somos CLIENTE (la anomalía zero-trust la causa
    // el host, que reparte/coordina). En el HOST (auto-detección, posible bug propio) devuelve el
    // mensaje tal cual, sin señalar a nadie.
    private String withSuspectHostPrefix(String reason) {
        try {
            if (GameFrame.getInstance().isPartida_local()) {
                return reason;
            }
            String host = WaitingRoomFrame.getInstance().getServer_nick();
            String hostNick = (host != null && !host.isEmpty()) ? host : "?";
            return MessageFormat.format(Translator.translate("zero_trust.suspect_host_prefix"), hostNick) + " " + reason;
        } catch (Exception ex) {
            return reason;
        }
    }

    public void warnSuspiciousHost(String reason) {
        if (!suspicious_host_warned_reasons.add(reason)) {
            return; // ya avisado de ESTA anomalia en esta partida
        }
        // SOSPECHOSO: en un CLIENTE, la anomalía la causa el HOST (reparte/coordina) -> se antepone
        // "SOSPECHOSO: host «X»" al mensaje (registro rojo + popup). En el HOST es auto-detección
        // (posible bug propio), no se señala a nadie.
        final String fullReason = withSuspectHostPrefix(reason);
        try {
            GameFrame.getInstance().getRegistro().print(Translator.translate("zero_trust.suspicious_alert") + " " + fullReason);
        } catch (Exception ignored) {
        }
        Helpers.threadRun(() -> {
            // Modal: desde este hilo de fondo bloquea hasta que el usuario pulsa OK.
            Helpers.mostrarMensajeError(GameFrame.getInstance(),
                    Translator.translate("zero_trust.suspicious_header")
                    + fullReason + "\n\n"
                    + Translator.translate("zero_trust.suspicious_body"));
            // El aviso recomienda abandonar la mesa: tras cerrarlo se lo ponemos a un click
            // abriendo el flujo de salida ya existente (mismo camino que el menu Salir /
            // Ctrl+Q). Si prefiere seguir jugando, cancela el dialogo y no pasa nada.
            // SOLO en cliente: en el host el aviso es auto-deteccion (posible bug propio) y
            // ademas su flujo de salida en partida local con un unico humano NO pregunta
            // (saldria de la timba sin confirmacion). Si mientras tanto salto un lockdown
            // duro, ese flujo ya esta gestionando la salida y no abrimos nada encima.
            if (!Crupier.SECURITY_LOCKDOWN && !GameFrame.getInstance().isPartida_local()) {
                try {
                    Helpers.GUIRun(() -> GameFrame.getInstance().getExit_menu().doClick());
                } catch (Exception ignored) {
                }
            }
        });
    }

    // Simetrico HOST-side de warnSuspiciousHost: el host ha detectado comportamiento anomalo o abusivo de
    // UN peer concreto (flood de mensajes, negativa a la cascada, forfeit por datos que no resuelven a una
    // carta genesis, etc.). Da la MISMA visibilidad que en el lado cliente pero nombrando al JUGADOR
    // sospechoso: registro EN ROJO (prefijo zero_trust.peer_alert -> ST_ALERT) + popup, dedup por
    // (peer + anomalia) por partida. La ACCION que proceda segun gravedad (descartar / expulsar / misdeal /
    // forfeit) la aplica el LLAMADOR; esto solo informa. Fail-safe total: se invoca desde caminos criticos
    // (incluido el hilo lector de un Participant o en pleno reparto) y NUNCA debe lanzar.
    public void warnMaliciousPeer(String peerNick, String reasonKey) {
        try {
            String dedupKey = peerNick + "|" + reasonKey;
            if (!malicious_peer_warned_reasons.add(dedupKey)) {
                return; // ya avisado de ESTA anomalia de ESTE peer en esta partida
            }
            String suspect = MessageFormat.format(Translator.translate("zero_trust.suspect_peer_prefix"),
                    (peerNick != null && !peerNick.isEmpty()) ? peerNick : "?");
            final String line = suspect + " " + Translator.translate(reasonKey);
            try {
                if (GameFrame.getInstance() != null && GameFrame.getInstance().getRegistro() != null) {
                    GameFrame.getInstance().getRegistro().print(
                            Translator.translate("zero_trust.peer_alert") + " " + line);
                }
            } catch (Exception ignored) {
            }
            Helpers.threadRun(() -> {
                try {
                    Helpers.mostrarMensajeError(GameFrame.getInstance(),
                            Translator.translate("zero_trust.peer_suspicious_header")
                            + line + "\n\n"
                            + Translator.translate("zero_trust.peer_suspicious_body"));
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    // Anti-DoS: registra una negativa al reparto (cascada o rotacion) de ESTE peer. Alcanzado
    // MAX_DEAL_REFUSAL_STRIKES en la partida, lo AUTO-EXPULSA (markExitAndNotify + socketClose) para que no
    // pueda forzar un MISDEAL cada mano indefinidamente. El MISDEAL de la mano actual lo hace el llamador;
    // esto solo escala tras varias. Fail-safe: nunca lanza.
    private void registerDealRefusal(String nick) {
        try {
            int strikes = deal_refusal_strikes.merge(nick, 1, Integer::sum);
            LOGGER.log(Level.WARNING, "ZERO-TRUST: deal refusal strike {0}/{1} for peer {2}",
                    new Object[]{strikes, MAX_DEAL_REFUSAL_STRIKES, nick});
            if (strikes >= MAX_DEAL_REFUSAL_STRIKES) {
                Participant pp = GameFrame.getInstance().getParticipantes().get(nick);
                if (pp != null && !pp.isExit() && !pp.isCpu()) {
                    LOGGER.log(Level.SEVERE,
                            "ZERO-TRUST DoS: peer {0} forced {1} deal refusals (cascade/rotation) — AUTO-EXPEL, table continues",
                            new Object[]{nick, strikes});
                    warnMaliciousPeer(nick, "zero_trust.peer_deal_refusal_expelled");
                    pp.markExitAndNotify("repeated deal refusal (cascade/rotation)");
                    try {
                        pp.socketClose();
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    // Torneo/anti-trampa: esta mano el mazo NO se pudo verificar como barajado honesto (host no mandó
    // el bundle, o llegó mal, o se probó deshonesto). NO se bloquea la mano (cartas ya repartidas) ni
    // se fuerza salida: se REGISTRA en el log del juego EN ROJO (el prefijo zero_trust.suspicious_alert
    // lo colorea vía ST_ALERT, como los demás errores zero-trust) y se avisa con un popup UNA vez por
    // partida. La evidencia firmada ya va en el recibo + disputed_hands; el jugador decide si sigue.
    private volatile byte[] deck_unverified_warned_megapacket = null;

    public void warnDeckUnverified() {
        // Dedup POR MANO (megapacket), NO por partida: esto NO debería pasar NUNCA en una partida
        // legítima; si pasa es GRAVE, así que se avisa (popup + registro rojo) en CADA mano afectada.
        // El dedup por megapacket evita duplicar dentro de la MISMA mano si saltan el gate y onDishonest.
        byte[] mp = this.local_mega_packet;
        if (mp != null && java.util.Arrays.equals(mp, this.deck_unverified_warned_megapacket)) {
            return;
        }
        this.deck_unverified_warned_megapacket = mp;

        // SOSPECHOSO: el host (reparte y difunde la prueba de barajado). Se nombra por su nick.
        String host;
        try {
            host = WaitingRoomFrame.getInstance().getServer_nick();
        } catch (Exception ex) {
            host = null;
        }
        final String hostNick = (host != null && !host.isEmpty()) ? host : "?";

        try {
            GameFrame.getInstance().getRegistro().print(
                    Translator.translate("zero_trust.suspicious_alert") + " "
                    + MessageFormat.format(Translator.translate("zero_trust.deck_unverified"), hostNick));
        } catch (Exception ignored) {
        }
        Helpers.threadRun(() -> {
            Helpers.mostrarMensajeError(GameFrame.getInstance(),
                    Translator.translate("zero_trust.suspicious_header")
                    + MessageFormat.format(Translator.translate("zero_trust.deck_unverified"), hostNick) + "\n\n"
                    + Translator.translate("zero_trust.deck_unverified_body"));
        });
    }

    /**
     * Decisión PURA del gate "exigir prueba de barajado": avisar al ir a revelar community SII es fase
     * community (la ventana de lectura del smuggle), el mazo viene de un reparto FRESCO ({@code expect}),
     * NO se verificó un bundle honesto para él ({@code verified}) y no se avisó ya ({@code warned}).
     * Aislada para ser testeable sin un juego completo. Recover no marca {@code expect} ⇒ no avisa.
     */
    public static boolean shouldWarnMissingShuffleProof(int phase, byte[] megapacket,
                                                        byte[] expect, byte[] verified, byte[] warned) {
        return phase != UNLOCK_PHASE_POCKET
                && megapacket != null
                && java.util.Arrays.equals(megapacket, expect)
                && !java.util.Arrays.equals(megapacket, verified)
                && !java.util.Arrays.equals(megapacket, warned);
    }

    /**
     * The peer-side shuffle-verification queue (one serial daemon worker per Crupier), created on first
     * use. The {@link ShuffleVerificationQueue.Sink} maps each verdict onto the existing policy:
     * <ul>
     *   <li><b>verified</b> — mark the deck as verified for the live unlock gate, but only if this
     *       snapshot is STILL the current deck (a late verdict for a past hand must not touch the live
     *       hand's gate); otherwise the verdict is purely historical.</li>
     *   <li><b>dishonest</b> — the bundle parsed but the honest-shuffle proof FAILED: a proven-dishonest
     *       deck (host cheating or a bug). SOFT-WARN per §8.2 (warn + recommend leaving, keep playing).
     *       This now fires reliably even for a past hand on slow hardware, instead of being lost.</li>
     *   <li><b>malformed</b> — the bundle could not be evaluated (ambiguous, e.g. version mismatch):
     *       SOFT-WARN, never treated as proof of cheating.</li>
     * </ul>
     */
    public ShuffleVerificationQueue getShuffleVerifyQueue() {
        ShuffleVerificationQueue q = shuffle_verify_queue;
        if (q == null) {
            synchronized (this) {
                q = shuffle_verify_queue;
                if (q == null) {
                    q = new ShuffleVerificationQueue(new ShuffleVerificationQueue.Sink() {
                        @Override
                        public void onVerified(byte[] megapacket, int handId) {
                            if (java.util.Arrays.equals(megapacket, local_mega_packet)) {
                                dual_lock_verified_megapacket = megapacket;
                            }
                            LOGGER.log(Level.INFO, "SHUFFLE-VERIFY: deck verified OK (hand {0})", handId);
                            // Registro: barajado verificado (posiblemente TARDE, incluso de una mano ya
                            // pasada — la cola es persistente). Usa el ordinal del Job (handId), no getMano(),
                            // que ya podría apuntar a otra mano. Guarda por correr en el hilo de la cola.
                            GameFrame gf = GameFrame.getInstance();
                            if (gf != null && gf.getRegistro() != null) {
                                gf.getRegistro().print(
                                        MessageFormat.format(Translator.translate("game.barajado_verificado"), String.valueOf(handId)));
                            }
                        }

                        @Override
                        public void onDishonest(byte[] megapacket, int handId) {
                            LOGGER.log(Level.SEVERE,
                                    "SHUFFLE-VERIFY: deck PROVEN DISHONEST (hand {0}) — host cheating or bug, warning + red log entry",
                                    handId);
                            // Mazo PROBADO deshonesto: se juega la mano (cartas repartidas), se registra
                            // en rojo + popup. Evidencia firmada ya en recibo + disputed_hands.
                            warnDeckUnverified();
                        }

                        @Override
                        public void onMalformed(byte[] megapacket, int handId, Exception error) {
                            LOGGER.log(Level.SEVERE,
                                    "SHUFFLE-VERIFY: bundle not evaluable (hand " + handId + ") — warning", error);
                            warnSuspiciousHost(Translator.translate("zero_trust.host_shuffle_proof_failed"));
                        }
                    });
                    q.start();
                    shuffle_verify_queue = q;
                }
            }
        }
        return q;
    }

    /** Detiene la cola de verificacion de barajado de este Crupier (si llego a crearse), para no fugar
     *  su worker daemon ni el grafo del Crupier (retenido via la Sink) al arrancar una partida nueva.
     *  Idempotente y fail-safe. */
    public void shutdownShuffleVerifyQueue() {
        ShuffleVerificationQueue q = this.shuffle_verify_queue;
        if (q != null) {
            q.shutdown();
        }
    }

    public void triggerSecurityLockdown(String reason) {
        if (!Crupier.SECURITY_LOCKDOWN) {
            Crupier.SECURITY_LOCKDOWN = true;
            // Despierta a cualquier handler bloqueado en
            // awaitStreetForUnlockPhase para que vea el lockdown y aborte
            // sin esperar al timeout.
            synchronized (protocol_state_lock) {
                protocol_state_lock.notifyAll();
            }
            // SOSPECHOSO: en cliente, la anomalía la causa el host -> se nombra en el aviso rojo + popup.
            final String fullReason = withSuspectHostPrefix(reason);
            GameFrame.getInstance().getRegistro().print(Translator.translate("zero_trust.security_alert") + " " + fullReason);
            GameFrame.getInstance().getRegistro().print(Translator.translate("zero_trust.lockdown_activated"));

            // Si somos cliente, cerramos el socket con el host inmediatamente.
            // En lockdown el cliente refusa cualquier REQ_SRA_UNLOCK_CHAIN siguiente
            // y la cascade SRA no tiene timeout artificial, así que sin cierre
            // el host queda esperando respuesta indefinidamente. Cerrar el
            // socket fuerza al host a detectar peer caído por SocketException
            // → exit=true → cascade falla limpio → MISDEAL → abortToRecover →
            // SERVEREXITRECOVER al resto del ring.
            if (!GameFrame.getInstance().isPartida_local()) {
                WaitingRoomFrame wrf = WaitingRoomFrame.getInstance();
                if (wrf != null) {
                    try {
                        wrf.closeClientSocket();
                    } catch (Exception ignored) {
                    }
                }
            }

            Helpers.threadRun(() -> {
                Helpers.mostrarMensajeError(GameFrame.getInstance(),
                        Translator.translate("zero_trust.critical_alert_header")
                        + fullReason + "\n\n"
                        + Translator.translate("zero_trust.critical_alert_body"));
                // Tras el popup del lockdown la timba se da por acabada para
                // este peer. El HOST se entera por socket caido + cascade
                // fail -> abortAndExit broadcast SERVEREXIT al resto. Pero
                // ESTE peer (con socket ya cerrado) no recibira SERVEREXIT,
                // asi que dispara local su propio finTransmision sin
                // force_recover -> BalanceDialog final. Sin esto, el peer
                // que detecta el ataque quedaria con GameFrame abierto
                // esperando algo que no llega.
                try {
                    GameFrame inst = GameFrame.getInstance();
                    if (inst != null) {
                        Crupier c = inst.getCrupier();
                        if (c != null) {
                            c.setForce_recover(false);
                            c.setFin_de_la_transmision(true);
                        }
                        inst.finTransmision(true);
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Failed to dispatch local finTransmision after zero-trust lockdown", ex);
                }
            });
        }
    }

    // VARIABLES CRIPTOGRÁFICAS DE ESTADO
    private volatile byte[] local_hand_seed = null;

    // Añadir esto debajo de local_hand_seed
    public byte[] getLocal_hand_seed() {
        return local_hand_seed;
    }

    // --- LLAVES SRA DEL JUGADOR LOCAL ---
    // Dual-lock: cada peer genera DOS pares de scalars por mano.
    //   - local_sra_lock / local_sra_unlock se usan en la cascade principal y
    //     siguen siendo la clave de las pocket pieces.
    //   - local_sra_lock_community / local_sra_unlock_community se usan en la
    //     fase de rotación que transforma las community pieces y luego para
    //     desbloquearlas en cada calle. El testamento criptográfico al hacer
    //     EXIT entrega SOLO la mitad community; las pocket cards del peer
    //     que sale permanecen ininteligibles para el host.
    public volatile byte[] local_sra_lock = null;
    public volatile byte[] local_sra_unlock = null;
    public volatile byte[] local_sra_lock_community = null;
    public volatile byte[] local_sra_unlock_community = null;
    // Anti-replay de la rotación: el cliente sirve UNA sola rotación por
    // cascada. Se pone a false al generar los scalars en el handler DECK_CASCADE_REQ
    // (cada cascada/reintento legítimo permite una rotación) y a true tras servir la
    // rotación. Una segunda DECK_ROTATION_REQ sin nueva cascada = host hostil intentando
    // usar la rotación como oráculo de pocket-unlock encubierto → lockdown. Cierra el
    // sigilo del único resquicio que queda (cartas de un peer que sale): sin rotación
    // extra, el host tendría que corromper la rotación legítima y eso rompe el board
    // (misdeal injustificado, detectable).
    public volatile boolean rotation_served_this_cascade = false;
    // Locks community de los bots que orquesta este host. La mitad UNLOCK la
    // guarda el Participant (sra_unlock_community); el LOCK solo es necesario
    // localmente en la fase de rotación, así que vive en este Map keyed por
    // nick del bot. Limpiado en los mismos sitios que el resto de scalars.
    public final java.util.concurrent.ConcurrentHashMap<String, byte[]> bot_community_locks = new java.util.concurrent.ConcurrentHashMap<>();

    // Verifiable dealing: commitments publicos K=k*B de cada peer del ring
    // (nick -> encoding Ristretto 32B), para K_pocket y K_community. Se recolectan
    // durante la cascade (propios + bots localmente, remotos via DECK_CASCADE_RESP),
    // se difunden en el MEGAPACKET y se anclan en H_0 para que la cadena
    // DLEQ del dealing se verifique contra claves que nadie puede falsificar.
    public final java.util.concurrent.ConcurrentHashMap<String, byte[]> peer_k_pocket = new java.util.concurrent.ConcurrentHashMap<>();
    public final java.util.concurrent.ConcurrentHashMap<String, byte[]> peer_k_community = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Serializa los commitments del ring para el MEGAPACKET, en orden del ring:
     * "nickB64:KpocketB64:KcommunityB64;...". Base64 no usa ':' ni ';' ni '#',
     * asi que son separadores seguros frente al split('#') del wire.
     */
    private String serializeCommitments() {
        StringBuilder sb = new StringBuilder();
        if (this.active_crypto_ring == null) {
            return "";
        }
        for (String nick : this.active_crypto_ring) {
            byte[] kp = peer_k_pocket.get(nick);
            byte[] kc = peer_k_community.get(nick);
            if (kp == null || kc == null) {
                continue;
            }
            try {
                if (sb.length() > 0) {
                    sb.append(';');
                }
                sb.append(Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")))
                        .append(':').append(Base64.getEncoder().encodeToString(kp))
                        .append(':').append(Base64.getEncoder().encodeToString(kc));
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error serializing commitment for " + nick, e);
            }
        }
        return sb.toString();
    }

    /** Parsea el campo de commitments del MEGAPACKET y puebla los mapas locales. */
    public void parseCommitments(String field) {
        if (field == null || field.isEmpty()) {
            return;
        }
        for (String entry : field.split(";")) {
            if (entry.isEmpty()) {
                continue;
            }
            String[] parts = entry.split(":");
            if (parts.length != 3) {
                continue;
            }
            try {
                String nick = new String(Base64.getDecoder().decode(parts[0]), "UTF-8");
                byte[] kp = Base64.getDecoder().decode(parts[1]);
                byte[] kc = Base64.getDecoder().decode(parts[2]);
                if (kp.length == 32 && kc.length == 32) {
                    peer_k_pocket.put(nick, kp);
                    peer_k_community.put(nick, kc);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error parsing commitment entry", e);
            }
        }
    }
    public volatile byte[] local_mega_packet = null;

    // REGISTRO de la cadena de cascada (sin generar pruebas -> cero CPU en el barajado).
    // cascade_chain_decks = genesis + un deck por paso. Por paso: para host/bots se guarda (perm, k)
    // para generar la prueba luego en background; para remotos, la prueba que ya mando el cliente.
    // Sirve para que TODOS verifiquen que la cascada es un barajado honesto (un host modificado no
    // puede colar cartas) — la generacion+verificacion corre en background tras el reparto.
    public volatile java.util.List<byte[]> cascade_chain_decks = null;
    public volatile java.util.List<int[]> cascade_step_perm = null;          // host/bot: perm; remoto: null
    public volatile java.util.List<byte[]> cascade_step_k = null;            // host/bot: k; remoto: null
    // Cierre del flanco ROTACION (dual-lock): estados community tras cada paso de rotacion + un
    // RotationProof (batch-DLEQ) por paso. Junto a la cascada cierra genesis->MEGAPACKET
    // (DualLockCascade). host/bot: prueba generada inline (batch-DLEQ es barato, ~ms); remoto: la
    // prueba que mande el cliente en DECK_ROTATION_RESP (pendiente wire-rotation-2 -> null por ahora).
    public volatile java.util.List<byte[]> cascade_rotation_states = null;
    // CERO crypto en el path de reparto: el bucle solo registra estados + el escalar combinado
    // (s=s1*s2, multiplicacion BigInteger barata) de host/bot; las pruebas batch-DLEQ se generan en
    // BACKGROUND. Para pasos remotos guardamos la prueba que ya mando el cliente (su generacion es
    // inline pero de UN solo paso, trivial).
    public volatile java.util.List<java.math.BigInteger> cascade_rotation_scalars = null; // host/bot: s; remoto: null
    public volatile java.util.List<byte[]> cascade_rotation_remote_proofs = null;          // remoto: prueba; host/bot: null
    public volatile java.util.List<byte[]> cascade_rotation_proofs = null;                  // construido en background
    // Prueba del último paso de rotación remoto, parseada en requestRemoteRotation y leída por el bucle.
    private volatile byte[] last_remote_rotation_proof = null;
    // Gate "exigir prueba": el cliente EXIGE un bundle de barajado honesto para el mazo de cada reparto
    // FRESCO. expect = mazo de un reparto fresco (se marca al procesar el MEGAPACKET, NO en recover, que
    // restaura por otra ruta -> no avisa tras recover, el barajado ya se verifico pre-crash). verified =
    // mazo para el que un bundle verifico OK. warned = guard de aviso unico por mazo. Llave = el propio
    // megapacket (cambia cada mano -> sin reset). Lo lee el handler de unlock community (la ventana de
    // lectura): si va a revelar community sin haber verificado el barajado de este mazo, avisa.
    public volatile byte[] dual_lock_expect_bundle_for = null;
    public volatile byte[] dual_lock_verified_megapacket = null;
    public volatile byte[] dual_lock_warned_megapacket = null;
    // Marca "un DUALLOCK_BUNDLE para este mazo LLEGO del host" (se pone al recibir el comando, antes de
    // parsear/verificar). Distingue en el recibo el peer LENTO (bundle recibido pero la cola aun no acabo
    // -> benigno) del host que NO mando la prueba (recibido != mazo vivo -> sospechoso). Llave = megapacket.
    public volatile byte[] dual_lock_bundle_received_for = null;

    // Cola serial de verificacion del barajado honesto (peer-side). Sustituye al threadRun-por-mano:
    // cada job lleva SU snapshot de mazo+bundle y un unico worker daemon los drena en FIFO, asi un equipo
    // muy lento termina de verificar manos pasadas (y caza un smuggle pasado) aunque la mano viva avance,
    // sin acumular hilos peleando por la CPU. Creada perezosamente al primer DUALLOCK_BUNDLE (ver
    // getShuffleVerifyQueue). El worker es daemon -> no bloquea el cierre de la JVM.
    private volatile ShuffleVerificationQueue shuffle_verify_queue = null;

    // --- TOKENS DEL HOST ---
    public volatile byte[] local_token_flop = null;
    public volatile byte[] local_token_turn = null;
    public volatile byte[] local_token_river = null;

    public volatile byte[] pure_local_cards = new byte[2];

    public final ConcurrentHashMap<String, byte[]> single_locked_pocket_cards = new ConcurrentHashMap<>();

    private final ConcurrentLinkedQueue<String> received_commands = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> acciones_locales_recuperadas = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, Integer> rebuy_now = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> rebuy_counts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> iwtsth_requests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> rabbit_players = new ConcurrentHashMap<>();
    // ConcurrentHashMap (no HashMap): se escribe bajo lock_contabilidad
    // (auditorCuentas, updateExitPlayers) pero se ITERA fuera de ese lock,
    // bajo SQL_LOCK, en sqlNewHand/sqlUpdateHandEnd. Como el orden global es
    // lock_contabilidad → SQL_LOCK, no se puede tomar lock_contabilidad dentro
    // del SQL_LOCK para proteger la iteración sin invertir el orden y arriesgar
    // un AB-BA con el snapshot de finTransmision. Con un HashMap llano, un exit
    // en otro hilo (finTransmision → auditorCuentas.put) concurrente con la
    // iteración del crupier reventaba con ConcurrentModificationException (o
    // peor, corrupción de la tabla en un resize). El CHM da iteración
    // débilmente consistente y sin excepción, que es justo lo que necesita un
    // volcado de balances best-effort durante el cierre.
    private final ConcurrentHashMap<String, Double[]> auditor = new ConcurrentHashMap<>();
    private final Object lock_ciegas = new Object();
    private final Object lock_apuestas = new Object();
    private final Object lock_contabilidad = new Object();
    private final Object lock_mostrar = new Object();
    private final Object lock_iwtsth = new Object();
    private final Object lock_last_hand = new Object();
    private final Object lock_nueva_mano = new Object();
    private final Object lock_rabbit = new Object();
    private final Object lock_rebuynow = new Object();
    private final Object lock_pausa_barra = new Object();
    private final Object lock_fin_mano = new Object();
    // Publica las transiciones de calle (street) y de showdown (show_time)
    // hacia los hilos que deben esperar a esos estados antes de servir una
    // REQ_SRA_UNLOCK_CHAIN. Toda escritura de street/show_time pasa por
    // setStreetLocal/setShowTime y dispara notifyAll bajo este lock, así
    // ningún waiter pierde una transición.
    private final Object protocol_state_lock = new Object();
    private final ConcurrentHashMap<String, Player> nick2player = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Player, Hand> perdedores = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Player> flop_players = new ConcurrentLinkedQueue<>();

    private byte[] activeHandId;
    private volatile int conta_mano = 0;
    private volatile int conta_accion = 0;
    private volatile double bote_total = 0;
    private volatile double apuestas = 0;
    private volatile double ciega_grande = GameFrame.CIEGA_GRANDE;
    private volatile double ciega_pequeña = GameFrame.CIEGA_PEQUEÑA;
    private volatile double apuesta_actual = 0;
    private volatile double ultimo_raise = 0;
    private volatile double partial_raise_cum = 0;
    private volatile int conta_raise = 0;
    private volatile int conta_bet = 0;
    private volatile boolean straddle_posted = false; // true si en esta mano el UTG decidió poner el straddle (voluntario)
    private volatile String straddle_utg_nick = null; // con straddle, el "under the gun" REAL (primero en hablar) = siguiente activo tras el straddler; null sin straddle
    private volatile boolean straddle_recovered_posted = false; // recovery (host): si la mano replayada tenía el straddle posteado (del fósil); el host rebroadcasta esta decisión, no vuelve a preguntar
    private volatile VoluntaryStraddleDialog straddle_local_dialog = null; // diálogo de straddle voluntario abierto en el peer del UTG (para cerrarlo desde fuera)
    private volatile boolean straddle_bar_active = false; // true mientras la barra del community cuenta los 5s de decisión del straddle (luego indeterminada hasta el resultado)
    private volatile boolean straddle_local_cards_deferred = false; // repartir dejó las hole cards del UTG local boca abajo a la espera de su decisión de straddle; resolveVoluntaryStraddle garantiza revelarlas (incluso si sale por early-return)
    private volatile java.util.List<Player> forced_bet_chip_contributors = null; // jugadores cuyas fichas de forzadas (ciegas/straddle/ante) vuelan al bote al arrancar la mano
    private volatile double bote_sobrante = 0;
    private volatile String[] nicks_permutados;
    private volatile boolean fin_de_la_transmision = false;
    private volatile int street = PREFLOP;
    // Zero-trust state machine: el cliente sólo responde REQ_SRA_UNLOCK_CHAIN
    // si la longitud de cada item encaja con la calle/fase en que está. Para
    // POCKET el guard verdadero es el anti-genesis cripto-check del cliente —
    // no hay un flag de "ya terminó Phase 2 para mí" porque cada peer sigue
    // siendo unlocker para los pockets de los OTROS targets aún cuando ya
    // recibió los suyos.
    private volatile boolean flop_revealed = false;
    private volatile boolean turn_revealed = false;
    private volatile boolean river_revealed = false;
    private volatile HandPot bote = null;
    private volatile boolean cartas_resistencia = false;
    private volatile int ciegas_double = 0;
    private volatile long turno = System.currentTimeMillis();
    private volatile boolean fold_sound_playing = false;
    private volatile int tiempo_pausa = 0;
    private volatile boolean barajando = false;
    private volatile ArrayList<String> cartas_locales_recibidas = null;
    private volatile Player last_aggressor = null;
    private volatile boolean destapar_resistencia = false;
    private volatile boolean show_time = false;
    // True desde que repartir() ha colocado las 5 comunitarias tapadas (y las hole
    // cards) hasta el reset de la siguiente mano. Gatea el overlay de coste de
    // igualar para que NO aparezca antes del reparto ni entre manos (al transicionar
    // el tablero), solo durante las apuestas con las cartas ya en la mesa.
    private volatile boolean community_cards_dealt = false;
    // Diálogo de votación run-it-twice activo en el CLIENTE (host-driven via RIT_VOTE_*).
    private volatile RunItTwiceDialog rit_client_dialog = null;
    // True mientras se reparte el segundo board (SIDE-B) de un run-it-twice. Abre
    // las fases UNLOCK_PHASE_RIT2_* en el gate; fuera de SIDE-B está a false y esas
    // fases se rechazan siempre. Se fija localmente (no por el host) en host y
    // clientes al entrar/salir del reparto de SIDE-B, preservando el anti-early-cascade.
    private volatile boolean run_it_twice_side_b = false;
    // Run-it-twice: etiqueta de cara ya traducida (CARA-A/CARA-B) que la
    // pot_label del tapete añade entre corchetes mientras se corren los dos
    // boards (null fuera de run-it-twice). Se enciende con el voto afirmativo
    // (run-out de SIDE-A), pasa a CARA-B en el rewind y se apaga en NUEVA_MANO.
    private volatile String rit_pot_board_tag = null;
    // Resultado del voto run-it-twice de la mano actual (host: de runRitVote;
    // cliente: del RIT_VOTE_CLOSE). Ambos lo conocen para que sus bucles run()
    // tomen la misma rama "correr SIDE-B" en lockstep. Reset por mano.
    private volatile boolean rit_agreed = false;
    // True una vez la votación ha terminado esta mano. Persistido al fósil: en un
    // recovery con el voto ya hecho, el host NO re-vota (usa rit_agreed restaurado);
    // si el crash fue antes del voto, queda false y la votación corre normal.
    private volatile boolean rit_vote_done = false;
    // Calle en la que se cerró la acción (all-in run-out). Las comunitarias de
    // calles POSTERIORES son las "corridas" (se rebobinan para SIDE-B); las de
    // esta calle y anteriores son compartidas. -1 = no hubo all-in run-out.
    private volatile int rit_allin_street = -1;
    // Cartas comunitarias que salieron en CARA-A en las calles posteriores al
    // all-in (las "corridas"). El reparto real de CARA-B NO las repone, así que
    // la simulacion de Montecarlo de CARA-B debe excluirlas de la baraja. Se
    // capturan antes de que rebobinarComunitariasSideB sobrescriba esas cartas
    // con los valores de CARA-B. Vacia fuera de run-it-twice.
    private final ArrayList<Integer> rit_side_a_runout_cards = new ArrayList<>();
    private volatile boolean badbeat = false;
    private volatile int jugada_ganadora = 0;
    private volatile boolean sincronizando_mano = false;
    // True between starting recovering.mp3 (background_music stopped) and the
    // recovery-completion path that swaps it back. Tracking the actual swap state
    // instead of re-reading GameFrame.MUSICA_AMBIENTAL at each end makes the
    // start/stop symmetric even if that flag were toggled mid-recovery: recovering
    // never outlives the recovery and background_music is never duplicated.
    private volatile boolean recovering_music_active = false;
    private volatile RecoverDialog recover_dialog = null;
    private volatile String current_local_cinematic_b64 = null;
    private volatile String current_remote_cinematic_b64 = null;
    private volatile boolean rebuy_time = false;
    private volatile boolean last_hand = false;
    private volatile int sqlite_id_game = -1;
    private volatile int sqlite_id_hand = -1;
    // Run-it-twice: silencia los INSERT/UPDATE del showdown SQL mientras se
    // liquidan los DOS boards. La tabla showdown lleva UNA fila por jugador/mano;
    // sin esto cada board insertaría su propia fila y se duplicarían las stats y
    // (peor) el COUNT(winner) de sqlGetPlayerContaWins, que se recarga la mano
    // siguiente y desharía la corrección en memoria del conta_win. Tras los dos
    // boards se escribe UNA fila consolidada (pay total + winner = ganó algún side).
    private volatile boolean rit_suppress_showdown_sql = false;
    // Mano anulada por MISDEAL (cancelarManoYDevolverApuestas ya devolvió las
    // apuestas, rollbackAbortedHand cerró la mano en SQL e izó
    // fin_de_la_transmision). Señal para los caminos que liquidaron dinero
    // ANTES del aborto — el settle de CARA-A del run-it-twice — de que deben
    // revertir su parte (el refund fue ÍNTEGRO) y no escribir SQL de showdown
    // ni re-estampar la mano que el rollback dejó cerrada con pot=0.
    private volatile boolean mano_anulada = false;
    private volatile GameOverDialog gameover_dialog = null;
    private volatile String dealer_nick = null;
    private volatile String big_blind_nick = null;
    private volatile String small_blind_nick = null;
    private volatile String utg_nick = null;
    private volatile boolean saltar_primera_mano = false;
    private volatile boolean update_game_seats = false;
    private volatile int tot_acciones_recuperadas = 0;
    private volatile Double beneficio_bote_principal = null;
    private volatile boolean iwtsth = false;
    private volatile boolean iwtsthing = false;
    private volatile boolean iwtsthing_request = false;
    private volatile Long last_iwtsth_rejected = null;
    private volatile int limpers;
    private volatile int game_recovered = 0;
    // Recovery: true cuando recuperarDatosClavePartida ya rotó posiciones
    // (setPositions en la rama de mano-fresh, saltar=true sin mano-en-curso).
    // Lo lee el finally de NUEVA_MANO para NO volver a llamar setPositions y
    // evitar una segunda rotación (calcularPosiciones no es idempotente: cada
    // llamada avanza las ciegas/dealer un asiento). Sin esto, el dealer/ciegas
    // saltaban un asiento y quedaba un botón de dealer fantasma del primer
    // reparto sin limpiar.
    private volatile boolean recovery_positions_set = false;
    private volatile Object[] ciegas_update = null;
    private volatile boolean dead_dealer = false;
    private volatile boolean force_recover = false;
    public volatile String[] active_crypto_ring = null;

    // Identity: hand-state chain (H_t ratchet) for the current hand. Initialized
    // after the MEGAPACKET is processed on both host and clients; absorbs every canonical
    // action record produced during the hand. Cleared to null between hands by
    // readyForNextHand.
    public volatile byte[] current_hand_id = null;
    public volatile HandStateChain hand_state_chain = null;

    // Identity: per-hand flag — set to true the first time this
    // peer rejects an Ed25519 signature on an incoming ACTION or COMM_REVEAL
    // wire during the hand. The flag is embedded into this peer's receipt (under
    // the issuer's own sig, so the host relay cannot strip it). runConsensusCheck
    // then refuses to report OK if ANY collected receipt has the flag set, even
    // when H_finals coincide — closes the "all peers see the same invalid sig
    // and consensus passes anyway" hole. Cleared in readyForNextHand.
    private volatile boolean saw_invalid_action_sig = false;

    // Ventana de espera (fuera del reparto) para recoger las pruebas de barajado ASYNC de los
    // pasos remotos (B1). MUY generosa: el prove del cliente puede tardar segundos en frío (hasta
    // ~9s medidos en un PC lento), y esto corre durante las apuestas (que suelen durar más).
    // Ampliada de 15s a 30s para dar margen de sobra a PCs lentos y evitar avisos "sin verificar"
    // espurios. Si aun así no llega, el paso degrada a "sin prueba" (el bundle no se difunde, igual
    // que un peer proofless): peor caso un aviso, nunca un reparto incorrecto ni una trampa.
    private static final long CASCADE_ASYNC_PROOF_TIMEOUT_MS = 30000;

    // Base64(SHA-256(deck)): identificador content-addressed del deckOut de un paso, para emparejar
    // la prueba async del cliente (DECK_CASCADE_PROOF) con su paso en la cadena. Único por mano, así
    // que una prueba de una mano vieja no casa con ningún deckOut actual y se descarta sola.
    private static String cascadeDeckHash(byte[] deck) {
        try {
            return Base64.getEncoder().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(deck));
        } catch (Exception e) {
            return null;
        }
    }

    // Recoge (fuera del path de reparto) las pruebas de barajado ASYNC de los pasos REMOTOS cuyo
    // cliente las mandó aparte (deck ya, prueba después). Sondea received_commands por mensajes
    // DECK_CASCADE_PROOF#hash(deckOut)#proof, con espera acotada. Devuelve mapa hash(deckOut) ->
    // prueba; los que no lleguen a tiempo quedan fuera (su paso queda null -> el bundle no se
    // difunde, igual que hoy con un peer proofless).
    //
    // Dos blindajes clave:
    //  - RE-ENCOLA todo lo que NO sea un DECK_CASCADE_PROOF de NUESTROS hashes (incluidas pruebas
    //    de otro builder de una mano solapada): si no, un builder se comería las pruebas de otro y
    //    lo degradaría a falso "host sin prueba" en toda la mesa.
    //  - VERIFICA cada prueba (verifyStepWire) contra (deckIn, deckOut) del paso antes de aceptarla,
    //    FUERA del lock (es cara). El deckOut de un peer lo conoce el siguiente en la cascada (es su
    //    input), así que sin esto un peer podría PISAR la prueba honesta de otro con una basura
    //    (first-wins) -> mazo sin verificar / falso "host deshonesto". Una basura se descarta y se
    //    sigue esperando la buena.
    private java.util.Map<String, byte[]> collectAsyncCascadeProofs(
            java.util.List<byte[]> decks, java.util.List<int[]> perms) {
        java.util.Map<String, byte[]> collected = new java.util.HashMap<>();
        java.util.Map<String, Integer> hashToStep = new java.util.HashMap<>();
        for (int s = 0; s < perms.size(); s++) {
            // Paso remoto (perm null): su prueba de barajado viene async.
            if (perms.get(s) == null) {
                String h = cascadeDeckHash(decks.get(s + 1));
                if (h != null) {
                    hashToStep.put(h, s);
                }
            }
        }
        if (hashToStep.isEmpty()) {
            return collected;
        }
        long deadline = System.currentTimeMillis() + CASCADE_ASYNC_PROOF_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline
                && collected.size() < hashToStep.size()
                && !isFin_de_la_transmision()) {
            // Bajo el lock: SOLO sacar los DECK_CASCADE_PROOF de NUESTROS hashes aún no aceptados
            // (a un buffer); re-encolar TODO lo demás. NO verificar aquí (verifyStepWire es caro y
            // bloquearía al reader que mete comandos).
            java.util.List<String[]> candidates = new java.util.ArrayList<>();
            synchronized (this.getReceived_commands()) {
                java.util.ArrayList<String> rejected = new java.util.ArrayList<>();
                while (!this.getReceived_commands().isEmpty()) {
                    String cmd = this.received_commands.poll();
                    String[] partes = cmd.split("#");
                    if (partes.length >= 5 && "DECK_CASCADE_PROOF".equals(partes[2])
                            && hashToStep.containsKey(partes[3]) && !collected.containsKey(partes[3])) {
                        candidates.add(new String[]{partes[3], partes[4]});
                    } else {
                        rejected.add(cmd); // no es NUESTRO proof -> re-encolar (otro builder / otro comando)
                    }
                }
                if (!rejected.isEmpty()) {
                    this.getReceived_commands().addAll(rejected);
                }
            }
            // Fuera del lock: verificar cada candidato y aceptar SOLO los válidos.
            for (String[] c : candidates) {
                if (collected.containsKey(c[0])) {
                    continue;
                }
                try {
                    byte[] proof = Base64.getDecoder().decode(c[1]);
                    int step = hashToStep.get(c[0]);
                    if (com.tonikelope.coronapoker.crypto.ShuffleCascade.verifyStepWire(
                            decks.get(step), decks.get(step + 1), proof)) {
                        collected.put(c[0], proof);
                    }
                } catch (Exception ex) {
                    // Prueba mal formada o inválida: se descarta (se sigue esperando la buena).
                }
            }
            if (collected.size() < hashToStep.size()) {
                // Espera acotada INCONDICIONAL (250 ms) como el resto de bucles de espera del host:
                // el throttle es el timeout del wait. NO guardar con isEmpty(): como re-encolamos todo
                // lo que no es NUESTRO proof, la cola casi nunca está vacía durante las apuestas -> con
                // el guard el while giraría sin dormir (busy-spin, 100% núcleo).
                synchronized (this.getReceived_commands()) {
                    try {
                        this.received_commands.wait(WAIT_QUEUES);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        return collected;
    }

    private byte[] requestRemoteCascade(String nick, byte[] currentDeck, Participant p) {
        int id = Helpers.CSPRNG_GENERATOR.nextInt();
        byte[] iv = new byte[16];
        Helpers.CSPRNG_GENERATOR.nextBytes(iv);
        String deckB64 = Base64.getEncoder().encodeToString(currentDeck);
        try {
            p.writeCommandFromServer(Helpers.encryptCommand("GAME#" + id + "#DECK_CASCADE_REQ#" + deckB64, p.getAes_key(), iv, p.getHmac_key()));
        } catch (Exception e) {
            return null;
        }

        // Deadline de PROGRESO host-side (anti-DoS por congelación). Antes NO había
        // timeout: la única señal de "este peer no responderá" era que su propio thread
        // de Participant lo marcase exit por inactividad de PING/PONG. Pero un peer
        // MALICIOSO puede contestar los PING (seguir "vivo") y CALLAR su DECK_CASCADE_RESP
        // -> este bucle giraba para siempre y CONGELABA la mesa (p.isExit() nunca se
        // activaba). El deadline es por-peer (no lo multiplica el tamaño de la mesa) y MUY
        // generoso (REMOTE_CASCADE_RESP_TIMEOUT_MS) para no abortar jamás a un cliente
        // legítimo lento; al vencer devolvemos null (el peer sigue vivo) y el llamador lo
        // trata como REFUSAL zero-trust -> MISDEAL, igual que la rotación.
        long deadlineMs = System.currentTimeMillis() + REMOTE_CASCADE_RESP_TIMEOUT_MS;
        boolean ok = false;
        boolean fatalError = false;
        byte[] newDeck = null;
        do {
            synchronized (this.getReceived_commands()) {
                java.util.ArrayList<String> rejected = new java.util.ArrayList<>();
                while (!ok && !fatalError && !this.getReceived_commands().isEmpty()) {
                    String cmd = this.received_commands.poll();
                    String[] partes = cmd.split("#");
                    if (partes.length >= 5 && partes[2].equals("DECK_CASCADE_RESP")) {
                        try {
                            String senderNick = new String(Base64.getDecoder().decode(partes[3]), "UTF-8");
                            if (senderNick.equals(nick)) {
                                byte[] candidate = Base64.getDecoder().decode(partes[4]);
                                // ZERO-TRUST host-side: el peer responde con un deck que
                                // vamos a propagar al siguiente en la cascada y, al final,
                                // a TODOS via MEGAPACKET. Si el peer (malicioso o comprometido)
                                // devuelve bytes que no son puntos de Curve25519, no podemos
                                // contaminar la cascada — aborta la mano antes de propagar.
                                if (java.util.Arrays.equals(candidate, currentDeck)) {
                                    // ZERO-TRUST: un barajado honesto (lock con k!=1 + shuffle) JAMAS devuelve
                                    // el deck de entrada intacto. Un "identity echo" es un peer manipulado y,
                                    // ademas, aliasaria el hash(deckOut) del paso previo (colision -> su prueba
                                    // se aceptaria por el paso EQUIVOCADO y el host difundiria un bundle que falla
                                    // -> framearia al host honesto). Se rechaza antes de propagar.
                                    LOGGER.log(Level.SEVERE,
                                            "ZERO-TRUST: DECK_CASCADE_RESP from {0} echoed its input deck unchanged (no shuffle/lock) — refusing cascade",
                                            nick);
                                    fatalError = true;
                                } else if (candidate.length == 1664 && RistrettoSRA.arePointsValid(candidate)) {
                                    newDeck = candidate;
                                    ok = true;
                                    // Capturar los commitments K del peer (partes[5]=K_pocket,
                                    // partes[6]=K_community) para anclarlos en H_0. Son obligatorios:
                                    // un peer que no los manda es un peer manipulado (todos corren la
                                    // misma versión), así que se rechaza igual que un commitment inválido.
                                    if (partes.length >= 7) {
                                        try {
                                            byte[] kp = Base64.getDecoder().decode(partes[5]);
                                            byte[] kc = Base64.getDecoder().decode(partes[6]);
                                            if (kp.length == 32 && kc.length == 32
                                                    && RistrettoSRA.arePointsValid(kp) && RistrettoSRA.arePointsValid(kc)) {
                                                peer_k_pocket.put(nick, kp);
                                                peer_k_community.put(nick, kc);
                                            } else {
                                                LOGGER.log(Level.SEVERE, "ZERO-TRUST: DECK_CASCADE_RESP from {0} carries invalid commitments — refusing", nick);
                                                fatalError = true;
                                                ok = false;
                                            }
                                        } catch (Exception ex) {
                                            LOGGER.log(Level.SEVERE, "ZERO-TRUST: DECK_CASCADE_RESP from {0} commitment parse failed — refusing", nick);
                                            fatalError = true;
                                            ok = false;
                                        }
                                    } else {
                                        LOGGER.log(Level.SEVERE, "ZERO-TRUST: DECK_CASCADE_RESP from {0} carries no commitments — refusing", nick);
                                        fatalError = true;
                                        ok = false;
                                    }
                                } else {
                                    LOGGER.log(Level.SEVERE,
                                            "ZERO-TRUST: DECK_CASCADE_RESP from {0} carries invalid deck (len={1}, on_curve={2}) — refusing cascade",
                                            new Object[]{nick, candidate.length,
                                                candidate.length == 1664 ? RistrettoSRA.arePointsValid(candidate) : false});
                                    fatalError = true;
                                }
                            } else {
                                rejected.add(cmd);
                            }
                        } catch (Exception e) {
                            rejected.add(cmd);
                        }
                    } else {
                        rejected.add(cmd);
                    }
                }
                if (!rejected.isEmpty()) {
                    this.getReceived_commands().addAll(rejected);
                }
            }
            if (fatalError) {
                // Violacion zero-trust del peer (identity echo / deck off-curve / commitments invalidos):
                // NO es un simple drop de red, es un peer MANIPULADO. Primero se le NOMBRA como sospechoso
                // (registro rojo + popup, visibilidad §8) y se le anota un strike de negativa al reparto
                // (registerDealRefusal, que AUTO-EXPULSA tras MAX_DEAL_REFUSAL_STRIKES), igual que la rotacion.
                // Esto DEBE ir aqui: el markExitAndNotify de abajo lo marca isExit, asi que el llamador lo
                // encamina a la rama "restart sin el" (silenciosa) en vez de a la rama MISDEAL que emitia
                // estos avisos. Sin esto, la cripto-violacion mas flagrante quedaba SIN visibilidad ni
                // escalado a expulsion. Un drop de red honesto NO llega aqui (no activa fatalError), asi que
                // no se acusa a nadie por caerse.
                warnMaliciousPeer(nick, "zero_trust.cascade_refused");
                registerDealRefusal(nick);
                // Se le trata como CAIDO (markExitAndNotify marca Participant.exit Y Player.exit y despierta
                // los waits) para que el reintento de la cascada lo SALTE (guard !p.isExit() abajo) en vez de
                // re-pedirle el paso y volver a fallar en bucle infinito (livelock del hilo de reparto). Reusa
                // la misma maquinaria de "peer se fue a mitad de cascada"; la mano se reparte sin el.
                p.markExitAndNotify("zero-trust cascade violation (manipulated peer)");
                return null;
            }
            if (!ok) {
                // Congela el deadline mientras la timba esté PAUSADA o haya CUALQUIER peer en timeout
                // (reconexión): ese tiempo no cuenta contra el peer (evita un MISDEAL espurio).
                if (GameFrame.getInstance().isTimba_pausada() || isSomePlayerTimeout()) {
                    deadlineMs = System.currentTimeMillis() + REMOTE_CASCADE_RESP_TIMEOUT_MS;
                }
                long remainingMs = deadlineMs - System.currentTimeMillis();
                if (remainingMs <= 0) {
                    LOGGER.log(Level.SEVERE,
                            "ZERO-TRUST DoS: peer {0} withheld DECK_CASCADE_RESP past {1}ms (answering PING but stalling the deal) — treating as refusal",
                            new Object[]{nick, REMOTE_CASCADE_RESP_TIMEOUT_MS});
                    return null;
                }
                synchronized (this.getReceived_commands()) {
                    try {
                        this.getReceived_commands().wait(Math.min(WAIT_QUEUES, remainingMs));
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        } while (!ok && !isFin_de_la_transmision() && !p.isExit() && System.currentTimeMillis() < deadlineMs);
        if (!ok) {
            return null;
        }
        return newDeck;
    }

    // Dual-lock: pide a un peer remoto que aplique sobre el bloque
    // de community pieces la rotación uPocket + kCommunity (en ese orden).
    // Tras la rotación, el peer pierde su lock de pocket sobre las community
    // pieces y añade su lock de community — la mitad que sí se entrega vía
    // testamento al hacer EXIT. La fase de rotación es secuencial peer-a-peer
    // igual que la cascade principal; este método maneja un solo peer.
    /** Une una lista de byte[] como CSV de base64 (el alfabeto base64 no usa ',' ni '#', sin ambigüedad). */
    private static String joinB64(java.util.List<byte[]> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(java.util.Base64.getEncoder().encodeToString(items.get(i)));
        }
        return sb.toString();
    }

    private byte[] requestRemoteRotation(String nick, byte[] communityPieces, Participant p) {
        int id = Helpers.CSPRNG_GENERATOR.nextInt();
        byte[] iv = new byte[16];
        Helpers.CSPRNG_GENERATOR.nextBytes(iv);
        String piecesB64 = Base64.getEncoder().encodeToString(communityPieces);
        try {
            p.writeCommandFromServer(Helpers.encryptCommand("GAME#" + id + "#DECK_ROTATION_REQ#" + piecesB64, p.getAes_key(), iv, p.getHmac_key()));
        } catch (Exception e) {
            return null;
        }
        int expectedLength = communityPieces.length;
        boolean ok = false;
        boolean fatalError = false;
        byte[] newPieces = null;
        this.last_remote_rotation_proof = null; // se rellena si el cliente manda su RotationProof
        // Timeout duro: si el peer no responde en REMOTE_SRA_PEER_TIMEOUT_MS, abortamos
        // la rotacion. Sin esto un peer reconectado mid-cascade (sin scalars SRA) deja
        // al host esperando hasta que el socket muera naturalmente (60-90s) — issue#9.
        long deadlineMs = System.currentTimeMillis() + REMOTE_SRA_PEER_TIMEOUT_MS;
        do {
            synchronized (this.getReceived_commands()) {
                java.util.ArrayList<String> rejected = new java.util.ArrayList<>();
                while (!ok && !fatalError && !this.getReceived_commands().isEmpty()) {
                    String cmd = this.received_commands.poll();
                    String[] partes = cmd.split("#");
                    if (partes.length >= 5 && partes[2].equals("DECK_ROTATION_RESP")) {
                        try {
                            String senderNick = new String(Base64.getDecoder().decode(partes[3]), "UTF-8");
                            if (senderNick.equals(nick)) {
                                byte[] candidate = Base64.getDecoder().decode(partes[4]);
                                // El bloque rotado debe conservar exactamente la misma longitud
                                // (mismas N posiciones) y seguir siendo puntos de la curva.
                                if (candidate.length == expectedLength && RistrettoSRA.arePointsValid(candidate)) {
                                    newPieces = candidate;
                                    // Prueba de rotacion del paso del cliente (opcional; sin ella el paso
                                    // queda remoto-pendiente y el full-chain verify se salta, no rompe nada).
                                    this.last_remote_rotation_proof = (partes.length >= 6 && partes[5] != null && !partes[5].isEmpty())
                                            ? Base64.getDecoder().decode(partes[5]) : null;
                                    ok = true;
                                } else {
                                    LOGGER.log(Level.SEVERE,
                                            "ZERO-TRUST: DECK_ROTATION_RESP from {0} carries invalid pieces (len={1}, on_curve={2}) — refusing rotation",
                                            new Object[]{nick, candidate.length,
                                                candidate.length == expectedLength ? RistrettoSRA.arePointsValid(candidate) : false});
                                    fatalError = true;
                                }
                            } else {
                                rejected.add(cmd);
                            }
                        } catch (Exception e) {
                            rejected.add(cmd);
                        }
                    } else {
                        rejected.add(cmd);
                    }
                }
                if (!rejected.isEmpty()) {
                    this.getReceived_commands().addAll(rejected);
                }
            }
            if (fatalError) {
                return null;
            }
            if (!ok) {
                long remainingMs = deadlineMs - System.currentTimeMillis();
                if (remainingMs <= 0) {
                    LOGGER.log(Level.WARNING,
                            "DECK_ROTATION_REQ to {0} timed out after {1}ms — peer alive but unresponsive (likely reconnected mid-cascade without SRA scalars)",
                            new Object[]{nick, REMOTE_SRA_PEER_TIMEOUT_MS});
                    return null;
                }
                synchronized (this.getReceived_commands()) {
                    try {
                        this.getReceived_commands().wait(Math.min(WAIT_QUEUES, remainingMs));
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        } while (!ok && !isFin_de_la_transmision() && !p.isExit() && System.currentTimeMillis() < deadlineMs);
        if (!ok) {
            return null;
        }
        return newPieces;
    }

    /**
     * Variante verificable del unlock batch. Envia, por item, las cadenas
     * DealChain por punto (no el residuo desnudo); el peer las verifica contra su
     * MEGAPACKET comprometido y devuelve las cadenas extendidas con su prueba. El
     * host nunca le manda el punto a descifrar — solo el offset y las pruebas previas
     * — asi que el cegado es imposible. Devuelve los RespItem por nick o null.
     */
    private java.util.List<UnlockChainWire.RespItem> requestRemoteUnlockChain(
            String nick, Participant p, int phase, java.util.List<UnlockChainWire.ReqItem> items) {
        int id = Helpers.CSPRNG_GENERATOR.nextInt();
        byte[] iv = new byte[16];
        Helpers.CSPRNG_GENERATOR.nextBytes(iv);
        String payload = UnlockChainWire.serializeReq(items);
        try {
            String cmd = "GAME#" + id + "#REQ_SRA_UNLOCK_CHAIN#" + phase + "#" + this.conta_mano + "#" + payload;
            p.writeCommandFromServer(Helpers.encryptCommand(cmd, p.getAes_key(), iv, p.getHmac_key()));
        } catch (Exception e) {
            return null;
        }

        boolean ok = false;
        java.util.List<UnlockChainWire.RespItem> result = null;
        long deadlineMs = System.currentTimeMillis() + REMOTE_SRA_PEER_TIMEOUT_MS;
        do {
            synchronized (this.getReceived_commands()) {
                java.util.ArrayList<String> rejected = new java.util.ArrayList<>();
                while (!ok && !this.getReceived_commands().isEmpty()) {
                    String cmd = this.received_commands.poll();
                    String[] partes = cmd.split("#");
                    if (partes.length >= 5 && partes[2].equals("RESP_SRA_UNLOCK_CHAIN")) {
                        try {
                            String senderNick = new String(Base64.getDecoder().decode(partes[3]), "UTF-8");
                            if (senderNick.equals(nick)) {
                                java.util.List<UnlockChainWire.RespItem> parsed = UnlockChainWire.parseResp(partes[4]);
                                if (parsed != null) {
                                    result = parsed;
                                    ok = true;
                                } else {
                                    rejected.add(cmd);
                                }
                            } else {
                                rejected.add(cmd);
                            }
                        } catch (Exception e) {
                            rejected.add(cmd);
                        }
                    } else {
                        rejected.add(cmd);
                    }
                }
                if (!rejected.isEmpty()) {
                    this.getReceived_commands().addAll(rejected);
                }
            }
            if (!ok) {
                long remainingMs = deadlineMs - System.currentTimeMillis();
                if (remainingMs <= 0) {
                    LOGGER.log(Level.WARNING,
                            "REQ_SRA_UNLOCK_CHAIN to {0} (phase {1}) timed out after {2}ms",
                            new Object[]{nick, phase, REMOTE_SRA_PEER_TIMEOUT_MS});
                    return null;
                }
                synchronized (this.getReceived_commands()) {
                    try {
                        this.getReceived_commands().wait(Math.min(WAIT_QUEUES, remainingMs));
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        } while (!ok && !isFin_de_la_transmision() && !p.isExit() && System.currentTimeMillis() < deadlineMs);
        return ok ? result : null;
    }

    /**
     * Extiende localmente (en nombre de signerNick, con su lock) la cadena
     * DealChain de cada slot del pocket salvo el del propio signer (skipSlot). Usado
     * por el host para su propio paso, el de los bots y el testamento de un peer que
     * salió (el host deriva el lock del unlock entregado). Devuelve false si algún
     * extend falla (no debería con datos honestos).
     */
    private boolean extendPocketChainsForSigner(String[][] chains, String[] ring, int skipSlot,
            String signerNick, byte[] signerLock) {
        for (int i = 0; i < ring.length; i++) {
            if (i == skipSlot) {
                continue;
            }
            for (int j = 0; j < 2; j++) {
                int pointIdx = i * 2 + j;
                byte[] point = Arrays.copyOfRange(local_mega_packet, pointIdx * 32, (pointIdx + 1) * 32);
                DealChain.Extended ext = DealChain.extend(point, chains[i][j], peer_k_pocket, signerNick, signerLock);
                if (ext == null) {
                    return false;
                }
                chains[i][j] = ext.wire;
            }
        }
        return true;
    }

    /**
     * Análogo community de {@link #extendPocketChainsForSigner}. Extiende, en
     * nombre de signerNick con su community-lock, la cadena de cada recipient (clave del map)
     * salvo skipRecipient. A diferencia del pocket, TODAS las copies anclan al MISMO punto
     * base del MEGAPACKET (offset+j, las community pieces post-rotación), no a slots por
     * jugador. Usa peer_k_community. skipRecipient==null cuando el signer no es recipient
     * (los bots: tienen community-lock que pelar pero no reciben pieza propia).
     */
    private boolean extendCommunityChainsForSigner(java.util.Map<String, String[]> chains,
            int offset, int numCards, String skipRecipient, String signerNick, byte[] signerLock) {
        for (java.util.Map.Entry<String, String[]> e : chains.entrySet()) {
            if (e.getKey().equals(skipRecipient)) {
                continue;
            }
            String[] recipientChains = e.getValue();
            for (int j = 0; j < numCards; j++) {
                int pointIdx = offset + j;
                byte[] point = Arrays.copyOfRange(local_mega_packet, pointIdx * 32, (pointIdx + 1) * 32);
                DealChain.Extended ext = DealChain.extend(point, recipientChains[j], peer_k_community, signerNick, signerLock);
                if (ext == null) {
                    return false;
                }
                recipientChains[j] = ext.wire;
            }
        }
        return true;
    }

    private boolean enviarCartasJugadoresRemotos() {
        for (Participant p : GameFrame.getInstance().getParticipantes().values()) {
            if (p != null) {
                p.setReceived_token(null); // Usado para guardar la llave de los Bots
                p.setSra_unlock_community(null); // Dual-lock: par community del bot
            }
        }
        this.bot_community_locks.clear();
            this.peer_k_pocket.clear();
            this.peer_k_community.clear();

        // Identity: fresh per-hand 16-byte HAND_ID that the host broadcasts to
        // every peer inside the MEGAPACKET. Every peer seeds its HandStateChain with
        // this id + the sorted player ids of the crypto-ring + the cascaded deck, so
        // H_0 is byte-identical across the table.
        this.current_hand_id = new byte[CanonicalActionRecord.HAND_ID_BYTES];
        if (Helpers.CSPRNG_GENERATOR != null) {
            Helpers.CSPRNG_GENERATOR.nextBytes(this.current_hand_id);
        }

        // CASCADA DE CIFRADO Y BARAJADO
        //
        // Si un peer humano cae DURANTE su pase de cascade (entre el DECK_CASCADE_REQ
        // y nuestra recepción de su RESP), aún no se han enviado las pocket cards,
        // así que no es un misdeal: rehacemos la cascada desde el genesis con un
        // ring nuevo SIN el peer caído. Sólo damos por perdida la mano si se ha
        // ido tanta gente que ya no quedan ≥2 activos para jugar.
        StringBuilder orderBuilder;
        String[] currentRing;
        byte[] workingDeck;

        while (true) {
            // Resetea cualquier estado parcial de un intento anterior abortado.
            for (Participant p : GameFrame.getInstance().getParticipantes().values()) {
                if (p != null) {
                    p.setReceived_token(null);
                    p.setSra_unlock_community(null);
                }
            }
            this.bot_community_locks.clear();
            this.peer_k_pocket.clear();
            this.peer_k_community.clear();

            java.util.ArrayList<Player> ringCriptografico = getAnilloCriptografico();
            int numPlayers = ringCriptografico.size();
            if (numPlayers < 2) {
                // Sin jugadores suficientes la mano no puede jugarse; sí es misdeal.
                cancelarManoYDevolverApuestas("peer.not_enough_players");
                return false;
            }

            orderBuilder = new StringBuilder();
            currentRing = new String[numPlayers];

            for (int i = 0; i < numPlayers; i++) {
                Player j = ringCriptografico.get(i);
                currentRing[i] = j.getNickname();
                try {
                    orderBuilder.append(Base64.getEncoder().encodeToString(j.getNickname().getBytes("UTF-8"))).append(",");
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error encoding nick in orderBuilder", e);
                }
            }
            this.active_crypto_ring = currentRing;

            // Candado fresco del Host por intento.
            this.local_sra_lock = RistrettoSRA.generateLockScalar();
            this.local_sra_unlock = RistrettoSRA.getUnlockScalar(this.local_sra_lock);
            // Dual-lock: segundo par para community. Generado por
            // adelantado para que la fase de rotación que vendrá después de la
            // cascade tenga el scalar listo. Hasta que se cablee la rotación
            // este par queda inerte (no se aplica a nada).
            this.local_sra_lock_community = RistrettoSRA.generateLockScalar();
            this.local_sra_unlock_community = RistrettoSRA.getUnlockScalar(this.local_sra_lock_community);

            // Commitments K=k*B del host para H_0.
            String hostNickForCommit = GameFrame.getInstance().getNick_local();
            peer_k_pocket.put(hostNickForCommit, RistrettoSRA.commitment(this.local_sra_lock));
            peer_k_community.put(hostNickForCommit, RistrettoSRA.commitment(this.local_sra_lock_community));

            // Registrar la cadena de cascada (reset por intento). cascadeGenesis es el
            // anclaje publico que todos derivan. Las pruebas NO se generan aqui (bloquearia el
            // reparto -> la animacion de barajado se alarga); se generan en background tras el bucle.
            byte[] cascadeGenesis = RistrettoSRA.getGenesisDeck();
            java.util.List<byte[]> chainDecks = new java.util.ArrayList<>();
            java.util.List<int[]> chainStepPerm = new java.util.ArrayList<>();   // host/bot: perm; remoto: null
            java.util.List<byte[]> chainStepK = new java.util.ArrayList<>();      // host/bot: k; remoto: null
            chainDecks.add(cascadeGenesis);

            workingDeck = RistrettoSRA.applyCommutativeLock(cascadeGenesis, this.local_sra_lock);
            workingDeck = DeterministicShuffle.shuffleDeck(workingDeck, this.local_hand_seed);
            // Paso del host: registrar (perm, k) para generar su prueba luego en background.
            chainStepPerm.add(DeterministicShuffle.shufflePermutation(cascadeGenesis.length / 32, this.local_hand_seed));
            chainStepK.add(this.local_sra_lock);
            chainDecks.add(workingDeck);

            boolean restart = false;
            for (int i = 0; i < numPlayers && !restart; i++) {
                String currNick = currentRing[i];
                if (!currNick.equals(GameFrame.getInstance().getNick_local())) {
                    Participant p = GameFrame.getInstance().getParticipantes().get(currNick);
                    if (p != null && p.isCpu()) {
                        byte[] botLock = RistrettoSRA.generateLockScalar();
                        byte[] botUnlock = RistrettoSRA.getUnlockScalar(botLock);
                        byte[] botSeed = new byte[48];
                        if (Helpers.CSPRNG_GENERATOR != null) {
                            Helpers.CSPRNG_GENERATOR.nextBytes(botSeed);
                        }
                        p.setReceived_token(botUnlock);
                        // Dual-lock: scalars community del bot. El lock se usará
                        // durante la rotación; el unlock se guarda en el Participant
                        // para que cascadeAndDealCommunityPieces pueda aplicarlo.
                        byte[] botCommunityLock = RistrettoSRA.generateLockScalar();
                        byte[] botCommunityUnlock = RistrettoSRA.getUnlockScalar(botCommunityLock);
                        this.bot_community_locks.put(currNick, botCommunityLock);
                        p.setSra_unlock_community(botCommunityUnlock);
                        // Commitments K del bot para H_0.
                        peer_k_pocket.put(currNick, RistrettoSRA.commitment(botLock));
                        peer_k_community.put(currNick, RistrettoSRA.commitment(botCommunityLock));
                        workingDeck = RistrettoSRA.applyCommutativeLock(workingDeck, botLock);
                        workingDeck = DeterministicShuffle.shuffleDeck(workingDeck, botSeed);
                        // Registrar el paso del bot (perm, k); su prueba va en background.
                        chainStepPerm.add(DeterministicShuffle.shufflePermutation(workingDeck.length / 32, botSeed));
                        chainStepK.add(botLock);
                        chainDecks.add(workingDeck);
                    } else if (p != null && !p.isExit()) {
                        byte[] cascaded = requestRemoteCascade(currNick, workingDeck, p);
                        if (cascaded != null) {
                            workingDeck = cascaded;
                            // Paso remoto: sin perm/k local; su prueba de barajado llega ASYNC
                            // (DECK_CASCADE_PROOF) y se empareja por hash(deckOut) en background.
                            chainStepPerm.add(null);
                            chainStepK.add(null);
                            chainDecks.add(workingDeck);
                        } else if (p.isExit()) {
                            // El peer quedó marcado exit durante el cascade. Dos causas, mismo
                            // tratamiento: o su socket murió (su Participant lo marcó exit), o cometió
                            // una violación zero-trust y requestRemoteCascade lo marcó exit
                            // (markExitAndNotify) para no reintentar con él en bucle. Reiniciamos la
                            // cascada SIN él (el guard !p.isExit() de arriba lo salta en el reintento).
                            // Aún no hemos repartido nada.
                            LOGGER.log(Level.WARNING,
                                    "Peer {0} left during cascade (drop or zero-trust violation) — restarting shuffle without them",
                                    currNick);
                            restart = true;
                        } else {
                            // El peer sigue VIVO (contesta PING) pero no entregó una
                            // DECK_CASCADE_RESP válida a tiempo: CALLÓ hasta vencer el deadline de
                            // progreso (withhold). Igual que la rotación (requestRemoteRotation):
                            // MISDEAL, cancela la mano, devuelve las apuestas y la timba sigue. Una
                            // crypto inválida (fatalError) NO llega aquí: requestRemoteCascade ya marcó
                            // exit a ese peer y cae en la rama de arriba (restart sin él). Antes esto
                            // CONGELABA la mesa (withhold, sin deadline).
                            LOGGER.log(Level.SEVERE,
                                    "ZERO-TRUST: peer {0} refused the cascade (alive but no valid DECK_CASCADE_RESP) — aborting hand, game continues",
                                    currNick);
                            warnMaliciousPeer(currNick, "zero_trust.cascade_refused");
                            registerDealRefusal(currNick);
                            cancelarManoYDevolverApuestas("zero_trust.cascade_refused");
                            return false;
                        }
                    }
                }
            }
            if (!restart) {
                // SOLO REGISTRAR la cadena (decks + datos de cada paso: perm/k del host
                // y bots; los pasos remotos sin perm/k, su prueba llega async). NO se genera ni
                // verifica nada aqui -> CERO CPU extra en el barajado (antes bloqueaba el hilo del
                // juego y se congelaba la barra de tiempo). La generacion+verificacion corre luego
                // en background.
                this.cascade_chain_decks = chainDecks;
                this.cascade_step_perm = chainStepPerm;
                this.cascade_step_k = chainStepK;
                break;
            }
        }

        // ROTACIÓN dual-lock de community pieces.
        //
        // Tras la cascade, workingDeck tiene 52 cartas * 32 bytes = 1664 bytes.
        // Las primeras N*2 cartas (N*64 bytes) son pocket pieces para los N
        // jugadores; el resto son community pieces (flop+turn+river+burns).
        // Cada peer aplica uPocket + kCommunity sobre el subarray community
        // para rotar los locks de la mitad pocket a la mitad community. Tras
        // este pase, las community pieces solo tienen locks de k_community y
        // su unlock se distribuye separadamente del de pocket — esa separación
        // es lo que permite que el testamento al hacer EXIT entregue solo la
        // mitad community sin filtrar pocket. Las pocket pieces no se tocan,
        // siguen con todos los locks de k_pocket de la cascade.
        int numPlayersFinal = currentRing.length;
        int pocketBytesEnd = numPlayersFinal * 64;
        int communityBytesLen = 1664 - pocketBytesEnd;
        byte[] communityPieces = new byte[communityBytesLen];
        System.arraycopy(workingDeck, pocketBytesEnd, communityPieces, 0, communityBytesLen);

        boolean rotationOk = true;
        String rotationFailMotivo = null;
        // Cierre del flanco rotacion: el bucle solo REGISTRA (cero crypto en el path): estado community
        // tras cada paso + el escalar combinado s=s1*s2 de host/bot (mult BigInteger barata) o la prueba
        // que ya mando el cliente. Las pruebas batch-DLEQ de host/bot se generan en BACKGROUND.
        java.util.List<byte[]> rotStates = new java.util.ArrayList<>();
        java.util.List<java.math.BigInteger> rotScalars = new java.util.ArrayList<>();
        java.util.List<byte[]> rotRemoteProofs = new java.util.ArrayList<>();
        for (int i = 0; i < numPlayersFinal && rotationOk; i++) {
            String currNick = currentRing[i];
            java.math.BigInteger stepScalar = null;
            byte[] stepRemoteProof = null;
            if (currNick.equals(GameFrame.getInstance().getNick_local())) {
                // Rotación en UN solo lock: aplicar uPocket y luego kCommunity equivale a
                // multiplicar cada punto por s = uPocket*kCommunity (mod L). Ese escalar producto
                // es el MISMO que ya necesita la prueba de rotación (stepScalar), así que un solo
                // pase da bytes idénticos pero hace la MITAD de scalarMul (no re-decodifica ni
                // re-cifra el estado community intermedio).
                stepScalar = RistrettoSRA.bytesToScalar(this.local_sra_unlock)
                        .multiply(RistrettoSRA.bytesToScalar(this.local_sra_lock_community))
                        .mod(com.tonikelope.coronapoker.crypto.EdwardsPoint.L);
                communityPieces = RistrettoSRA.applyCommutativeLock(communityPieces, RistrettoSRA.scalarToBytes(stepScalar));
            } else {
                Participant p = GameFrame.getInstance().getParticipantes().get(currNick);
                if (p != null && p.isCpu()) {
                    byte[] botUnlock = p.getReceived_token();
                    byte[] botCommunityLock = this.bot_community_locks.get(currNick);
                    if (botUnlock == null || botCommunityLock == null) {
                        LOGGER.log(Level.SEVERE,
                                "Bot {0} missing pocket-unlock or community-lock during rotation — aborting hand",
                                currNick);
                        rotationOk = false;
                        break;
                    }
                    // Rotación en UN solo lock (ver nota en la rama local): s = uPocket*kCommunity.
                    stepScalar = RistrettoSRA.bytesToScalar(botUnlock)
                            .multiply(RistrettoSRA.bytesToScalar(botCommunityLock))
                            .mod(com.tonikelope.coronapoker.crypto.EdwardsPoint.L);
                    communityPieces = RistrettoSRA.applyCommutativeLock(communityPieces, RistrettoSRA.scalarToBytes(stepScalar));
                } else if (p != null && !p.isExit()) {
                    byte[] rotated = requestRemoteRotation(currNick, communityPieces, p);
                    if (rotated != null) {
                        communityPieces = rotated;
                        stepRemoteProof = this.last_remote_rotation_proof; // prueba del cliente (DECK_ROTATION_RESP partes[5])
                    } else if (p.isExit() || p.isSocketDownOrReconnecting()) {
                        // El peer se CAYO o esta RECONECTANDO durante la rotacion (al reconectar mid-cascade
                        // pierde sus scalars SRA efimeros y no puede responder, issue#9). NO es una negativa
                        // maliciosa: MISDEAL SIN acusar ni dar strike, igual que la rama de peer-exit de abajo.
                        // Un abuso REPETIDO de reconexion lo caza el strike de tormenta de reconexion, aparte.
                        LOGGER.log(Level.WARNING,
                                "Peer {0} unavailable for rotation (drop/reconnect), aborting hand without strike, game continues",
                                currNick);
                        rotationOk = false;
                        rotationFailMotivo = "peer.dropped_during_rotation";
                        break;
                    } else {
                        // Peer VIVO y conectado que no entrego una rotacion valida: crypto invalida (fatalError)
                        // o withhold hasta vencer el deadline. REFUSAL zero-trust: nombrar (rojo + popup) mas
                        // strike hacia AUTO-EXPEL, y MISDEAL. Paridad con la cascada.
                        LOGGER.log(Level.WARNING,
                                "Peer {0} refused rotation (alive, connected, no valid response), aborting hand, game continues",
                                currNick);
                        warnMaliciousPeer(currNick, "zero_trust.rotation_refused");
                        registerDealRefusal(currNick);
                        rotationOk = false;
                        rotationFailMotivo = "zero_trust.rotation_refused";
                        break;
                    }
                } else {
                    // Peer está exit (se fue). Trata como "left without testament":
                    // misdeal pero la timba continúa (excepción del user).
                    LOGGER.log(Level.WARNING,
                            "Peer {0} not available for rotation (null or exit) — aborting hand, game continues",
                            currNick);
                    rotationOk = false;
                    rotationFailMotivo = "peer.dropped_during_rotation";
                    break;
                }
            }
            rotStates.add(communityPieces);   // estado community tras este paso (state[i+1])
            rotScalars.add(stepScalar);        // host/bot: s=s1*s2; remoto: null
            rotRemoteProofs.add(stepRemoteProof); // remoto: prueba del cliente; host/bot: null
        }

        if (!rotationOk) {
            cancelarManoYDevolverApuestas(rotationFailMotivo != null ? rotationFailMotivo : "peer.dropped_during_rotation");
            return false;
        }
        this.cascade_rotation_states = rotStates;
        this.cascade_rotation_scalars = rotScalars;
        this.cascade_rotation_remote_proofs = rotRemoteProofs;

        // Reensamblar el deck: pocket pieces intactos + community pieces rotados.
        byte[] dualLockDeck = new byte[1664];
        System.arraycopy(workingDeck, 0, dualLockDeck, 0, pocketBytesEnd);
        System.arraycopy(communityPieces, 0, dualLockDeck, pocketBytesEnd, communityBytesLen);
        workingDeck = dualLockDeck;

        this.local_mega_packet = workingDeck;
        String megaPacketB64 = Base64.getEncoder().encodeToString(this.local_mega_packet);
        String orderB64 = "";
        try {
            orderB64 = Base64.getEncoder().encodeToString(orderBuilder.toString().getBytes("UTF-8"));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error encoding orderB64 for MEGAPACKET", e);
        }

        // Enviamos el MEGAPACKET final a todos. El HAND_ID viaja como cuarto campo;
        // los clientes lo recogen para sembrar su HandStateChain.
        String handIdB64 = Base64.getEncoder().encodeToString(this.current_hand_id);
        // 5º campo = commitments K del ring (nick:Kp:Kc;...) para H_0.
        String commitmentsField = serializeCommitments();
        broadcastGAMECommandFromServer("MEGAPACKET#" + orderB64 + "#" + megaPacketB64 + "#" + handIdB64 + "#" + commitmentsField, null, true);

        // Identity: now that MEGAPACKET is finalised and every peer (in theory)
        // sees the same active_crypto_ring + cascadedDeck + handId, seed our own
        // HandStateChain. Subsequent actions in rondaApuestas ratchet H_t through this
        // chain on every peer in parallel.
        initHandStateChain();

        // Cascade POCKET en un único batch por helper humano.
        //
        // Por cada slot i del ring se construye pockets[i] = mega_packet[i*64:(i+1)*64].
        // El host quita su lock localmente salvo si el target del slot ES el
        // host (su lock se queda hasta el resolveCardIndex final). Igual con
        // bots: si target del slot es un bot, su lock se queda. Tras eso,
        // por cada humano remoto H pedimos en UN solo REQ_SRA_UNLOCK_CHAIN
        // que H quite su lock de todos los slots i cuyo target no sea H
        // (target=H mantiene el lock del propio H para que su client lo abra).
        // Si un humano H ha hecho EXIT con testamento, aplicamos su unlock
        // localmente para esos mismos slots; sin testamento abortamos la mano.
        // Dealing pocket VERIFICABLE. Cada peer que quita su lock adjunta una
        // prueba DLEQ encadenada desde el MEGAPACKET comprometido; el host (sus propios
        // locks, los de bots y los testamentos de exits) extiende localmente, y los
        // helpers vivos vía REQ_SRA_UNLOCK_CHAIN. El residuo single-locked de cada slot
        // es el tail de su cadena. Ningún peer descifra bytes sin probar su procedencia.
        String hostNick = GameFrame.getInstance().getNick_local();
        int ringLen = currentRing.length;
        String[][] pocketChains = new String[ringLen][2];
        for (int i = 0; i < ringLen; i++) {
            pocketChains[i][0] = "";
            pocketChains[i][1] = "";
        }

        // Paso 1: el host quita su lock (con prueba) de cada slot salvo el suyo.
        int hostSlot = -1;
        for (int i = 0; i < ringLen; i++) {
            if (currentRing[i].equals(hostNick)) {
                hostSlot = i;
                break;
            }
        }
        if (!extendPocketChainsForSigner(pocketChains, currentRing, hostSlot, hostNick, this.local_sra_lock)) {
            cancelarManoYDevolverApuestas("zero_trust.card_resolve_failed");
            return false;
        }
        // Bots: el host quita el lock de cada bot (con prueba) de cada slot salvo el del bot.
        for (String bNick : currentRing) {
            Participant pb = GameFrame.getInstance().getParticipantes().get(bNick);
            if (pb != null && pb.isCpu() && pb.getReceived_token() != null) {
                int botSlot = -1;
                for (int i = 0; i < ringLen; i++) {
                    if (currentRing[i].equals(bNick)) {
                        botSlot = i;
                        break;
                    }
                }
                byte[] botLock = RistrettoSRA.getUnlockScalar(pb.getReceived_token());
                if (!extendPocketChainsForSigner(pocketChains, currentRing, botSlot, bNick, botLock)) {
                    cancelarManoYDevolverApuestas("zero_trust.card_resolve_failed");
                    return false;
                }
            }
        }

        // Paso 2: helpers humanos remotos en orden del ring (vivos vía comando; exit
        // con testamento extendido localmente por el host derivando el lock del unlock).
        for (int h = 0; h < ringLen; h++) {
            String hNick = currentRing[h];
            if (hNick.equals(hostNick)) {
                continue;
            }
            Participant ph = GameFrame.getInstance().getParticipantes().get(hNick);
            if (ph == null || ph.isCpu()) {
                continue;
            }

            if (!ph.isExit()) {
                java.util.List<UnlockChainWire.ReqItem> reqItems = new java.util.ArrayList<>();
                for (int i = 0; i < ringLen; i++) {
                    if (i != h) {
                        reqItems.add(new UnlockChainWire.ReqItem(i, i * 2,
                                java.util.Arrays.asList(pocketChains[i][0], pocketChains[i][1])));
                    }
                }
                java.util.List<UnlockChainWire.RespItem> resp =
                        requestRemoteUnlockChain(hNick, ph, UNLOCK_PHASE_POCKET, reqItems);
                if (resp != null) {
                    java.util.Set<Integer> covered = new java.util.HashSet<>();
                    for (UnlockChainWire.RespItem ri : resp) {
                        if (ri.peerIdx >= 0 && ri.peerIdx < ringLen && ri.peerIdx != h && ri.chains.size() == 2) {
                            pocketChains[ri.peerIdx][0] = ri.chains.get(0);
                            pocketChains[ri.peerIdx][1] = ri.chains.get(1);
                            covered.add(ri.peerIdx);
                        }
                    }
                    // El helper debe haber extendido TODOS los slots != h. Un RESP incompleto
                    // dejaria un residuo aun lockeado por el helper (paridad con el batch viejo
                    // que validaba count). Tratarlo como rechazo.
                    boolean allCovered = true;
                    for (int i = 0; i < ringLen && allCovered; i++) {
                        if (i != h && !covered.contains(i)) {
                            allCovered = false;
                        }
                    }
                    if (!allCovered) {
                        warnMaliciousPeer(hNick, "zero_trust.pocket_unlock_refused");
                        cancelarManoYDevolverApuestas("zero_trust.pocket_unlock_refused");
                        return false;
                    }
                } else if (ph.getSra_unlock() != null) {
                    byte[] hLock = RistrettoSRA.getUnlockScalar(ph.getSra_unlock());
                    if (!extendPocketChainsForSigner(pocketChains, currentRing, h, hNick, hLock)) {
                        cancelarManoYDevolverApuestas("zero_trust.card_resolve_failed");
                        return false;
                    }
                } else {
                    warnMaliciousPeer(hNick, "zero_trust.pocket_unlock_refused");
                    cancelarManoYDevolverApuestas("zero_trust.pocket_unlock_refused");
                    return false;
                }
            } else if (ph.getSra_unlock() != null) {
                byte[] hLock = RistrettoSRA.getUnlockScalar(ph.getSra_unlock());
                if (!extendPocketChainsForSigner(pocketChains, currentRing, h, hNick, hLock)) {
                    cancelarManoYDevolverApuestas("zero_trust.card_resolve_failed");
                    return false;
                }
            } else {
                cancelarManoYDevolverApuestas("peer.unlock_no_testament");
                return false;
            }
        }

        // Paso 3: el host verifica cada cadena final y toma el tail (residuo single-locked
        // por el target). Una cadena que no verifica = un peer devolvió algo no probado.
        byte[][] pockets = new byte[ringLen][];
        for (int i = 0; i < ringLen; i++) {
            byte[] pc = new byte[64];
            for (int j = 0; j < 2; j++) {
                int pointIdx = i * 2 + j;
                byte[] point = Arrays.copyOfRange(local_mega_packet, pointIdx * 32, (pointIdx + 1) * 32);
                java.util.List<DealChain.Entry> ch = DealChain.parse(pocketChains[i][j]);
                if (ch == null || !DealChain.verify(point, ch, peer_k_pocket)) {
                    LOGGER.log(Level.SEVERE, "ZERO-TRUST: pocket chain for slot {0} point {1} failed verification", new Object[]{i, j});
                    cancelarManoYDevolverApuestas("zero_trust.card_resolve_failed");
                    return false;
                }
                byte[] tail = DealChain.tail(point, ch);
                System.arraycopy(tail, 0, pc, j * 32, 32);
            }
            pockets[i] = pc;
        }

        // Broadcast y resolución local por target.
        for (int i = 0; i < currentRing.length; i++) {
            String targetNick = currentRing[i];
            byte[] pocketCards = pockets[i];
            this.single_locked_pocket_cards.put(targetNick, pocketCards);

            try {
                String pcB64 = Base64.getEncoder().encodeToString(pocketCards);
                String nickB64 = Base64.getEncoder().encodeToString(targetNick.getBytes("UTF-8"));
                broadcastGAMECommandFromServer("POCKET_CARDS#" + nickB64 + "#" + pcB64, null, true);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error broadcasting POCKET_CARDS for " + targetNick, e);
                cancelarManoYDevolverApuestas("peer.broadcast_failed");
                return false;
            }

            if (targetNick.equals(hostNick)) {
                byte[] myPocket = RistrettoSRA.applyCommutativeLock(pocketCards, this.local_sra_unlock);
                byte[] c1 = Arrays.copyOfRange(myPocket, 0, 32);
                byte[] c2 = Arrays.copyOfRange(myPocket, 32, 64);
                this.local_original_cards[0] = (byte) RistrettoSRA.resolveCardIndex(c1);
                this.local_original_cards[1] = (byte) RistrettoSRA.resolveCardIndex(c2);
            } else {
                Participant pTarget = GameFrame.getInstance().getParticipantes().get(targetNick);
                if (pTarget != null && pTarget.isCpu()) {
                    byte[] botPocket = RistrettoSRA.applyCommutativeLock(pocketCards, pTarget.getReceived_token());
                    byte[] c1 = Arrays.copyOfRange(botPocket, 0, 32);
                    byte[] c2 = Arrays.copyOfRange(botPocket, 32, 64);
                    int id1 = RistrettoSRA.resolveCardIndex(c1);
                    int id2 = RistrettoSRA.resolveCardIndex(c2);
                    if (id1 >= 0 && id2 >= 0) {
                        Player botPlayer = nick2player.get(targetNick);
                        // Un espectador entra al anillo criptográfico (contribuye su lock)
                        // pero NO juega la mano: jamás se le reparten cartas, conserva su
                        // JOKER. Sin este guard se le tapaban las cartas y se persistían en
                        // BOTVISUAL@, reapareciendo boca abajo al recuperar.
                        if (botPlayer != null && botPlayer.isActivo()) {
                            botPlayer.getHoleCard1().iniciarConValorNumerico(id1 + 1);
                            botPlayer.getHoleCard2().iniciarConValorNumerico(id2 + 1);
                        }
                    }
                }
            }
        }

        // GUARDAMOS EL FÓSIL DESPUÉS DE REPARTIR (Obligatorio en SRA)
        this.guardarFosilSRA();

        // Tras repartir lanzamos en un hilo la generacion (cascada) + verificacion de la cadena
        // COMPLETA genesis->MEGAPACKET (cascada Bayer-Groth + rotacion batch-DLEQ). Corre durante las
        // apuestas, sin tocar la animacion.
        // ESTADO (wire-rotation-1): host-side SELF-verify. Cubre ya la rotacion (antes quedaba fuera).
        // Pendiente para proteccion efectiva: (a) difundir el bundle a los peers, (b) que cada peer lo
        // verifique, (c) gatear el unlock en el veredicto verificado-por-peers ANTES de la ventana de
        // lectura (avisar+permitir-seguir si falla, no abort duro). El anti-peek de una carta de jugador
        // VIVO ya lo da la cadena DLEQ en tiempo real (self-strip + anclaje + GATE 6 en WaitingRoomFrame).
        final byte[] bgGenesis = RistrettoSRA.getGenesisDeck();
        final int bgHandOrdinal = getMano(); // ordinal de ESTA mano, para el "barajado verificado" del registro
        final java.util.List<byte[]> bgDecks = this.cascade_chain_decks;
        final java.util.List<int[]> bgPerm = this.cascade_step_perm;
        final java.util.List<byte[]> bgK = this.cascade_step_k;
        final int bgPocketCount = numPlayersFinal * 2;
        final byte[] bgMega = this.local_mega_packet;
        final java.util.List<byte[]> bgRotStates = this.cascade_rotation_states;
        final java.util.List<java.math.BigInteger> bgRotScalars = this.cascade_rotation_scalars;
        final java.util.List<byte[]> bgRotRemoteProofs = this.cascade_rotation_remote_proofs;
        if (bgDecks != null && bgPerm != null) {
            Helpers.threadRun(() -> {
                final Thread bgVerifyThread = Thread.currentThread();
                final int bgVerifyPrio = bgVerifyThread.getPriority();
                // Prioridad rebajada mientras corre el prove/verify de fondo: no debe competir
                // de tú a tú con el EDT durante la ráfaga de apuestas de las primeras manos en
                // PCs de 1-2 núcleos. Restaurada en finally porque el hilo es del pool cacheado
                // (Helpers.threadRun) y se reutiliza para otras tareas.
                bgVerifyThread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 2));
                try {
                    // B1: recoger primero las pruebas de barajado ASYNC de los pasos remotos. Un
                    // cliente manda su deck+commitments YA y la prueba APARTE (DECK_CASCADE_PROOF),
                    // para no bloquear el reparto con su prove (132/377/8900 ms). Aquí, fuera del path de
                    // reparto, se esperan (acotado) emparejadas por hash del deckOut.
                    java.util.Map<String, byte[]> asyncProofs = collectAsyncCascadeProofs(bgDecks, bgPerm);
                    java.util.List<byte[]> proofs = new java.util.ArrayList<>();
                    for (int s = 0; s < bgPerm.size(); s++) {
                        byte[] stepProof;
                        if (bgPerm.get(s) != null) {
                            // Paso del host o de un bot: la prueba se genera localmente aquí.
                            stepProof = com.tonikelope.coronapoker.crypto.ShuffleCascade.proveStepWire(
                                    bgDecks.get(s), bgDecks.get(s + 1), bgPerm.get(s), bgK.get(s));
                        } else {
                            // Paso remoto: prueba ASYNC emparejada por hash(deckOut).
                            // null si no llegó en la ventana -> degradación = peer proofless de hoy.
                            String h = cascadeDeckHash(bgDecks.get(s + 1));
                            stepProof = (h != null) ? asyncProofs.get(h) : null;
                        }
                        proofs.add(stepProof);
                    }
                    boolean ok = com.tonikelope.coronapoker.crypto.ShuffleCascade
                            .verifyChainWire(bgGenesis, bgDecks, proofs);
                    LOGGER.log(ok ? Level.INFO : Level.SEVERE,
                            "SHUFFLE-VERIFY: background cascade-chain self-check = {0} ({1} steps)",
                            new Object[]{ok, proofs.size()});
                    // Cadena COMPLETA (cascada + rotacion). Generamos AQUI (background) las pruebas
                    // batch-DLEQ de host/bot a partir del escalar registrado; las remotas ya vienen del
                    // cliente. Cero crypto de esto en el path de reparto.
                    java.util.List<byte[]> rotProofsBg = new java.util.ArrayList<>();
                    boolean rotComplete = bgRotStates != null && bgRotScalars != null
                            && bgRotRemoteProofs != null && bgMega != null && !bgRotStates.isEmpty()
                            && bgRotScalars.size() == bgRotStates.size()
                            && bgRotRemoteProofs.size() == bgRotStates.size();
                    if (rotComplete) {
                        byte[] preRotDeck = bgDecks.get(bgDecks.size() - 1);
                        byte[] before = java.util.Arrays.copyOfRange(preRotDeck, bgPocketCount * 32, preRotDeck.length);
                        for (int r = 0; r < bgRotStates.size() && rotComplete; r++) {
                            byte[] after = bgRotStates.get(r);
                            byte[] stepP;
                            if (bgRotRemoteProofs.get(r) != null) {
                                // Paso remoto: VERIFICAR la prueba del peer contra (before, after) ANTES
                                // de aceptarla. Un peer puede rotar bien las piezas (pasan el on-curve) pero
                                // mandar una prueba basura bien formada: el full-chain self-check (fullOk)
                                // fallaria pero el bundle se difunde igual -> falso "host deshonesto" en toda
                                // la mesa. Si no verifica, se trata como paso SIN prueba (rotComplete=false ->
                                // no se difunde el bundle -> degradacion identica a un peer proofless de hoy).
                                byte[] cand = bgRotRemoteProofs.get(r);
                                stepP = com.tonikelope.coronapoker.crypto.DualLockWire.verifyRotationStepWire(before, after, cand)
                                        ? cand : null;
                            } else if (bgRotScalars.get(r) != null) {
                                com.tonikelope.coronapoker.crypto.EdwardsPoint[] inR = com.tonikelope.coronapoker.crypto.ShuffleCascade.decodeDeck(before);
                                com.tonikelope.coronapoker.crypto.EdwardsPoint[] outR = com.tonikelope.coronapoker.crypto.ShuffleCascade.decodeDeck(after);
                                stepP = (inR != null && outR != null)
                                        ? com.tonikelope.coronapoker.crypto.DualLockWire.encodeRotationProof(
                                                com.tonikelope.coronapoker.crypto.RotationProof.prove(bgRotScalars.get(r), inR, outR))
                                        : null;
                            } else {
                                stepP = null;
                            }
                            if (stepP == null) {
                                rotComplete = false;
                            } else {
                                rotProofsBg.add(stepP);
                            }
                            before = after;
                        }
                    }
                    // Solo seguimos si TODAS las pruebas de cascada estan (un peer legacy/proofless deja
                    // un null -> NO difundir un bundle con null, que todos los peers rechazarian en falso).
                    if (rotComplete && !rotProofsBg.isEmpty() && !proofs.contains(null)) {
                        this.cascade_rotation_proofs = rotProofsBg;
                        boolean fullOk = com.tonikelope.coronapoker.crypto.DualLockWire.verifyFullChainWire(
                                bgGenesis, bgDecks.subList(1, bgDecks.size()), proofs, bgPocketCount,
                                bgMega, bgRotStates, rotProofsBg);
                        LOGGER.log(fullOk ? Level.INFO : Level.SEVERE,
                                "SHUFFLE-VERIFY: background dual-lock full-chain self-check (cascade+rotation) = {0} ({1} rotation steps)",
                                new Object[]{fullOk, rotProofsBg.size()});
                        if (fullOk) {
                            // El host tambien firma "mazo verificado" en su receipt (su auto-verify).
                            this.dual_lock_verified_megapacket = bgMega;
                            // Registro: barajado (honestidad del mazo) verificado localmente por el host.
                            GameFrame gfBg = GameFrame.getInstance();
                            if (gfBg != null && gfBg.getRegistro() != null) {
                                gfBg.getRegistro().print(
                                        MessageFormat.format(Translator.translate("game.barajado_verificado"), String.valueOf(bgHandOrdinal)));
                            }
                            // Difundir el bundle a los peers para que CADA UNO verifique por su cuenta (el
                            // host verificandose a si mismo no protege). El peer deriva pocketCount LOCAL y
                            // recomputa el genesis. NO mandamos pocketCount (no fiarse del host). Fire-and-forget.
                            // SOLO si el auto-chequeo (fullOk) pasa: difundir un bundle que falla localmente haria
                            // que TODOS los peers lo rechazasen y, como quien lo difunde es el host, se leeria como
                            // "host deshonesto" en toda la mesa (un peer malicioso podria forzar ese fallo). Si no
                            // pasa NO se difunde -> los peers avisan "missing proof" en el reveal (proteccion intacta).
                            try {
                                String bundle = "DUALLOCK_BUNDLE#"
                                        + joinB64(bgDecks.subList(1, bgDecks.size())) + "#"
                                        + joinB64(proofs) + "#"
                                        + joinB64(bgRotStates) + "#"
                                        + joinB64(rotProofsBg);
                                broadcastGAMECommandFromServer(bundle, null);
                            } catch (Exception bcEx) {
                                LOGGER.log(Level.WARNING, "DUALLOCK_BUNDLE broadcast failed", bcEx);
                            }
                        } else {
                            // Auto-chequeo FALLIDO: NO difundir (evita framear al host). Los peers no reciben
                            // bundle -> avisan "missing proof" al revelar community. Degradacion = proofless.
                            LOGGER.log(Level.SEVERE,
                                    "SHUFFLE-VERIFY: full-chain self-check FAILED — NOT broadcasting bundle (peers will warn 'missing proof'); likely a manipulated peer");
                        }
                    } else {
                        LOGGER.log(Level.INFO,
                                "SHUFFLE-VERIFY: background full-chain self-check skipped (rotation incomplete or remote step without proof)");
                    }
                } catch (Exception bgEx) {
                    LOGGER.log(Level.SEVERE, "SHUFFLE-VERIFY: background cascade self-check threw", bgEx);
                } finally {
                    bgVerifyThread.setPriority(bgVerifyPrio);
                }
            });
        }

        return true;
    }

    private ArrayList<String> recibirMisCartas() {
        long start_time = System.currentTimeMillis();
        boolean ok = false;
        String[] cartas = new String[2];

        do {
            synchronized (this.getReceived_commands()) {
                java.util.ArrayList<String> rejected = new java.util.ArrayList<>();
                while (!ok && !this.getReceived_commands().isEmpty()) {
                    String comando = this.received_commands.poll();
                    try {
                        String[] partes = comando.split("#");
                        if (partes.length < 3) {
                            LOGGER.log(Level.WARNING, "Malformed command dropped (receiveMyCards): {0}", comando);
                            continue;
                        }

                        if (partes[2].equals("MEGAPACKET") && partes.length >= 5) {
                            String orderB64 = partes[3];
                            this.local_mega_packet = java.util.Base64.getDecoder().decode(partes[4]);
                            // Reparto FRESCO: a partir de ahora exijo un bundle de barajado honesto para
                            // este mazo (el handler de unlock community avisa si no llega). El recover NO
                            // pasa por aqui, asi que no exige bundle (el barajado ya se verifico pre-crash).
                            this.dual_lock_expect_bundle_for = this.local_mega_packet;
                            try {
                                String orderStr = new String(java.util.Base64.getDecoder().decode(orderB64), "UTF-8");
                                String[] orderTokens = orderStr.split(",");
                                java.util.ArrayList<String> ringList = new java.util.ArrayList<>();
                                for (String token : orderTokens) {
                                    if (!token.isEmpty()) {
                                        ringList.add(new String(java.util.Base64.getDecoder().decode(token), "UTF-8"));
                                    }
                                }
                                this.active_crypto_ring = ringList.toArray(new String[0]);
                            } catch (Exception e) {
                                LOGGER.log(Level.WARNING, "Error parsing ORDER of MEGAPACKET", e);
                            }
                            // Identity: the host appends a 16-byte HAND_ID as a fourth
                            // payload field. If present and well formed, seed our HandStateChain
                            // so subsequent actions ratchet on every peer in parallel.
                            if (partes.length >= 6) {
                                try {
                                    byte[] hid = java.util.Base64.getDecoder().decode(partes[5]);
                                    if (hid.length == CanonicalActionRecord.HAND_ID_BYTES) {
                                        this.current_hand_id = hid;
                                    } else {
                                        LOGGER.log(Level.WARNING, "MEGAPACKET HAND_ID has wrong length: {0}", hid.length);
                                        this.current_hand_id = null;
                                    }
                                } catch (Exception e) {
                                    LOGGER.log(Level.WARNING, "Error parsing HAND_ID of MEGAPACKET", e);
                                    this.current_hand_id = null;
                                }
                            }
                            // Parsear los commitments K (5º campo) antes de sembrar H_0.
                            if (partes.length >= 7) {
                                parseCommitments(partes[6]);
                            }
                            initHandStateChain();
                        } else if (partes[2].equals("POCKET_CARDS") && partes.length >= 5) {
                            try {
                                String targetNick = new String(java.util.Base64.getDecoder().decode(partes[3]), "UTF-8");
                                byte[] unlockedByOthers = java.util.Base64.getDecoder().decode(partes[4]);
                                if (unlockedByOthers != null) {
                                    this.single_locked_pocket_cards.put(targetNick, unlockedByOthers);
                                }

                                if (targetNick.equals(GameFrame.getInstance().getNick_local())) {

                                    // El MEGAPACKET tiene que haberse procesado antes.
                                    if (this.local_mega_packet != null) {
                                        this.local_sra_unlock = GameFrame.getInstance().getParticipantes().get(GameFrame.getInstance().getNick_local()).getSra_unlock();

                                        // Quitamos nuestro propio candado (El último de la capa de cifrado)
                                        byte[] myPocket = RistrettoSRA.applyCommutativeLock(unlockedByOthers, this.local_sra_unlock);
                                        byte[] c1 = java.util.Arrays.copyOfRange(myPocket, 0, 32);
                                        byte[] c2 = java.util.Arrays.copyOfRange(myPocket, 32, 64);

                                        int id1 = RistrettoSRA.resolveCardIndex(c1);
                                        int id2 = RistrettoSRA.resolveCardIndex(c2);

                                        if (id1 >= 0 && id2 >= 0) {
                                            this.local_original_cards[0] = (byte) id1;
                                            this.local_original_cards[1] = (byte) id2;
                                            cartas[0] = Card.VALORES[id1 % 13] + "_" + Card.PALOS[id1 / 13];
                                            cartas[1] = Card.VALORES[id2 % 13] + "_" + Card.PALOS[id2 / 13];
                                            ok = true;
                                        }
                                    } else {
                                        // Si ha llegado antes que la baraja, lo devolvemos a la cola y esperamos
                                        rejected.add(comando);
                                    }
                                }
                            } catch (Exception e) {
                                LOGGER.log(Level.WARNING, "Error processing POCKET_CARDS", e);
                            }
                        } else if (partes[2].equals("MISDEAL") && partes.length >= 4) {
                            // El host aborta la mano: salimos del consumer sin las cartas.
                            // El cancelar ya lo hace el case top-level en WaitingRoomFrame.
                            return null;
                        } else {
                            rejected.add(comando);
                        }
                    } catch (Exception ex) {
                        LOGGER.log(Level.WARNING, "Exception while processing command in receiveMyCards: " + comando, ex);
                    }
                }
                if (!rejected.isEmpty()) {
                    this.getReceived_commands().addAll(rejected);
                }
            }
            if (!ok) {
                if (isFin_de_la_transmision()) {
                    break;
                }
                // Patrón estándar del Crupier (15+ receive* loops lo usan):
                // espera sobre received_commands para que un notifyAll de
                // los productores (Participant reader, WaitingRoomFrame.cliente)
                // nos despierte inmediatamente al llegar el próximo comando.
                // El timeout WAIT_QUEUES es safety net consistente con el resto
                // del fichero — sustituye el Helpers.pausar(100) anterior que
                // polleaba sin escuchar al notifier real.
                synchronized (this.getReceived_commands()) {
                    try {
                        this.getReceived_commands().wait(WAIT_QUEUES);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } while (!ok && !isFin_de_la_transmision());

        if (!ok) {
            return null;
        }

        // ¡ESENCIAL! El cliente guarda sus cartas en su Fósil local
        this.guardarFosilSRA();

        return new ArrayList<>(java.util.Arrays.asList(cartas));
    }


    public String getTestamentoCriptografico() {
        return getTestamentoCriptografico(GameFrame.getInstance().getNick_local());
    }

    public String getTestamentoCriptografico(String nick) {
        // ZERO-TRUST: si el cliente entró en lockdown nunca compartimos
        // nuestra propia sra_unlock con el servidor — eso permitiría al
        // host descifrar nuestras pocket cards de la mano congelada. El
        // testamento de OTROS peers sí se devuelve normal (uso local del
        // host honesto para destapar a un peer que se marchó).
        if (Crupier.SECURITY_LOCKDOWN && nick.equals(GameFrame.getInstance().getNick_local())) {
            return "*";
        }
        try {
            // Dual-lock: el testamento entrega EXCLUSIVAMENTE la mitad
            // community. La mitad pocket (sra_unlock / received_token / local_sra_unlock)
            // NUNCA se comparte vía EXIT — es la propiedad de seguridad que justifica
            // el refactor. Con solo community, el host puede continuar revelando las
            // comunitarias pero no descifrar las cartas privadas del peer que sale.
            byte[] testament = null;
            if (nick.equals(GameFrame.getInstance().getNick_local())) {
                testament = this.local_sra_unlock_community;
            } else {
                Participant p = GameFrame.getInstance().getParticipantes().get(nick);
                if (p != null) {
                    // Para bots y humanos: el unlock community vive en
                    // sra_unlock_community. Para bots lo puso el host en la cascade;
                    // para humanos llega vía testamento previo.
                    testament = p.getSra_unlock_community();
                }
            }

            // Fallback para cliente remoto que no ha seteado local_sra_unlock_community
            // pero lo tiene en su Participant local.
            if (testament == null && nick.equals(GameFrame.getInstance().getNick_local())) {
                Participant p = GameFrame.getInstance().getParticipantes().get(GameFrame.getInstance().getNick_local());
                if (p != null) {
                    testament = p.getSra_unlock_community();
                }
            }

            if (testament != null && testament.length == 32) {
                return Base64.getEncoder().encodeToString(testament);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error generating the SRA exit testament for " + nick, e);
        }
        return "*";
    }

    /**
     * Dual-lock (Opción G): la clave que viaja en SHOWCARDS al revelar
     * voluntariamente las cartas privadas de un jugador al showdown. Es la
     * mitad POCKET — el receptor la usa con applyCommutativeLock sobre el
     * pocket piece cifrado para destapar las cartas. NUNCA debe confundirse
     * con getTestamentoCriptografico (que es la mitad community para EXIT):
     * mezclar las dos rompe el showdown (pocket pieces no se descifran con
     * community) o filtra cartas privadas del peer que sale (community
     * pieces no son sensibles a la pocket).
     *
     * Self vs others: el host tiene p.getReceived_token() para sus bots; para
     * un humano remoto solo se tiene su pocket key si ya envió un SHOWCARDS
     * propio antes en esta mano y se guardó en p.sra_unlock (línea 2757).
     */
    public String getShowdownPocketKey(String nick) {
        if (Crupier.SECURITY_LOCKDOWN && nick.equals(GameFrame.getInstance().getNick_local())) {
            return "*";
        }
        try {
            byte[] pocketKey = null;
            if (nick.equals(GameFrame.getInstance().getNick_local())) {
                pocketKey = this.local_sra_unlock;
                if (pocketKey == null) {
                    // Fallback para cliente remoto: el Crupier puede no haber
                    // copiado todavía el unlock desde el Participant (el copy
                    // ocurre al procesar POCKET_CARDS, línea ~1084).
                    Participant p = GameFrame.getInstance().getParticipantes().get(nick);
                    if (p != null) {
                        pocketKey = p.getSra_unlock();
                    }
                }
            } else {
                Participant p = GameFrame.getInstance().getParticipantes().get(nick);
                if (p != null) {
                    if (p.isCpu()) {
                        pocketKey = p.getReceived_token();
                    } else {
                        // Humano remoto: solo conocemos su pocket key si ya hizo
                        // SHOWCARDS propio antes en esta mano y la guardamos en
                        // sra_unlock al procesar la respuesta (línea 2757).
                        pocketKey = p.getSra_unlock();
                    }
                }
            }
            if (pocketKey != null && pocketKey.length == 32) {
                return Base64.getEncoder().encodeToString(pocketKey);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error generating showdown pocket key for " + nick, e);
        }
        return "*";
    }

    /**
     * PHASE A.1: firma (HAND_ID || nick || pocketKey) con la privkey LOCAL bajo
     * el dominio SHOWDOWN, para acompañar la pocketKey en el wire SHOWCARDS
     * / RESP_SHOWDOWN_KEY. La sig demuestra que la clave fue autorizada por
     * quien la posee:
     *
     *   - Si el local nick es el revelador (humano local): firma con su Ed25519.
     *   - Si revelaNick es un bot que el host orquesta: el host firma con SU
     *     propia Ed25519. Los bots no tienen identity y los receptores los
     *     verifican con la pubkey del host (ver resolveShowdownSignerPubkey).
     *
     * Devuelve "*" si pocketKey="*" (lockdown) o si la identity no está lista
     * (TOFU race / no signer); los receptores rechazan ese caso.
     */
    public String signShowdownRevealForBroadcast(String revealNick, String pocketKeyB64) {
        if (pocketKeyB64 == null || pocketKeyB64.equals("*")) {
            return "*";
        }
        if (this.current_hand_id == null) {
            LOGGER.log(Level.WARNING, "signShowdownRevealForBroadcast: current_hand_id is null — cannot sign");
            return "*";
        }
        try {
            byte[] pocketKey = Base64.getDecoder().decode(pocketKeyB64);
            if (pocketKey.length != 32) {
                return "*";
            }
            IdentityManager im = IdentityManager.getInstance();
            if (!im.isReady()) {
                return "*";
            }
            byte[] sig = im.signShowdownReveal(this.current_hand_id, revealNick, pocketKey);
            if (sig == null) {
                return "*";
            }
            return Base64.getEncoder().encodeToString(sig);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "signShowdownRevealForBroadcast failed for " + revealNick, e);
            return "*";
        }
    }

    public boolean unlockPlayerCardsWithSRAKey(Player target) {
        Participant p = GameFrame.getInstance().getParticipantes().get(target.getNickname());
        if (p != null && p.getSra_unlock() != null) {
            byte[] pocketCards = this.single_locked_pocket_cards.get(target.getNickname());
            if (pocketCards != null && pocketCards.length == 64) {
                try {
                    byte[] unlocked = RistrettoSRA.applyCommutativeLock(pocketCards, p.getSra_unlock());
                    byte[] c1 = Arrays.copyOfRange(unlocked, 0, 32);
                    byte[] c2 = Arrays.copyOfRange(unlocked, 32, 64);

                    int id1 = RistrettoSRA.resolveCardIndex(c1);
                    int id2 = RistrettoSRA.resolveCardIndex(c2);
                    if (id1 >= 0 && id2 >= 0) {
                        target.getHoleCard1().actualizarConValorNumerico(id1 + 1);
                        target.getHoleCard2().actualizarConValorNumerico(id2 + 1);
                        return true;
                    }
                } catch (Exception e) {
                    return false;
                }
            }
        }
        return false;
    }

    public int calcularPosicionEnPaquete(String nick) {

        // 1. Strict explicit packet layout (Prevents ZERO-TRUST index shifting)
        if (this.active_crypto_ring != null) {
            for (int i = 0; i < this.active_crypto_ring.length; i++) {
                if (this.active_crypto_ring[i].equals(nick)) {
                    return i;
                }
            }
            return -1;
        }

        // 2. Fallback to dynamic UI ring (Only used in edge cases/recoveries before MEGAPACKET)
        java.util.ArrayList<Player> ring = getAnilloCriptografico();
        for (int i = 0; i < ring.size(); i++) {
            if (ring.get(i).getNickname().equals(nick)) {
                return i;
            }
        }
        return -1;
    }

    // Variables puras para auditoría (Inmunes a la GUI)
    public volatile byte[] local_envelope = null;
    public volatile byte[] local_original_cards = new byte[2];

    public byte[] getLocal_original_cards() {
        return local_original_cards;
    }

    public byte[] getActiveHandId() {
        return activeHandId;
    }

    public boolean isForce_recover() {
        return force_recover;
    }

    public void setForce_recover(boolean force_recover) {
        this.force_recover = force_recover;
    }

    public Object getLock_rabbit() {
        return lock_rabbit;
    }

    public ConcurrentHashMap<String, Boolean> getRabbit_players() {
        return rabbit_players;
    }

    public boolean isDead_dealer() {
        return dead_dealer;
    }

    public Object[] getCiegas_update() {
        return ciegas_update;
    }

    public Object getLock_ciegas() {
        return lock_ciegas;
    }

    public void setCurrent_local_cinematic_b64(String current_local_cinematic_b64) {
        this.current_local_cinematic_b64 = current_local_cinematic_b64;
    }

    public int getLimpersCount() {
        return limpers;
    }

    public boolean isIwtsthing() {
        return iwtsthing;
    }

    public boolean isIwtsth() {
        return iwtsth;
    }

    public boolean isSomePlayerTimeout() {

        for (Player j : GameFrame.getInstance().getJugadores()) {
            if (j.isTimeout()) {

                return true;
            }
        }

        return false;
    }

    public Object getLock_fin_mano() {
        return lock_fin_mano;
    }

    public String getDealer_nick() {
        return dealer_nick;
    }

    // Carta-ancla del asiento del dealer: origen del vuelo de reparto (las
    // cartas salen de las manos del dealer hacia el resto de asientos y las
    // posiciones comunitarias). Devuelve su primera hole card como punto fijo,
    // o null si el dealer no se puede resolver (el vuelo parte entonces del
    // centro de la mesa).
    private Card getDealerSeatAnchor() {
        if (this.dealer_nick == null) {
            return null;
        }
        for (Player p : GameFrame.getInstance().getJugadores()) {
            if (this.dealer_nick.equals(p.getNickname())) {
                return p.getHoleCard1();
            }
        }
        return null;
    }

    // Anima el deslizamiento de las fichas de posición (dealer/ciegas) del asiento
    // de su portador anterior al del nuevo, en paralelo y justo antes del barajado.
    // Reproduce lo que muestra refreshPositionChipIcons: por prioridad BB > SB >
    // DEALER hay como mucho un portador visible por ficha (en heads-up el dealer
    // coincide con la ciega → no hay botón). Bloquea hasta el aterrizaje. Respeta
    // la opción de animación y se salta en RECOVER/fin de transmisión.
    private void animateChipRotation(String prev_dealer_nick, String prev_sb_nick, String prev_bb_nick) {

        if (!GameFrame.ciegasDealerAnimOn() || GameFrame.RECOVER || isFin_de_la_transmision()) {
            return;
        }

        final java.util.Map<String, Player> n2p = getNick2player();
        final java.util.List<TablePanel.ChipFlight> flights = new java.util.ArrayList<>();
        // Asientos cuya ficha estática se oculta durante el vuelo: SOLO los
        // destinos de vuelos reales, para que una ficha que NO se mueve no
        // parpadee (caso dead dealer sin retroceso, etc.).
        final java.util.List<Player> to_hide = new java.util.ArrayList<>();

        // Ciega grande: siempre se pinta.
        addChipFlight(flights, to_hide, n2p, prev_bb_nick, this.big_blind_nick, Helpers.IMAGEN_BB, false);

        // Ciega pequeña: solo si su asiento es distinto del de la ciega grande.
        String new_sb_holder = chipHolder(this.small_blind_nick, this.big_blind_nick, null);
        String old_sb_holder = chipHolder(prev_sb_nick, prev_bb_nick, null);
        addChipFlight(flights, to_hide, n2p, old_sb_holder, new_sb_holder, Helpers.IMAGEN_SB, false);

        // Botón del dealer: solo si su asiento es distinto del de ambas ciegas
        // (en heads-up dealer == ciega pequeña → sin botón visible). Si es un
        // DEAD DEALER (el jugador que debía recibir el botón se fue y este
        // retrocede al dealer anterior) el botón sale del CENTRO de la mesa —su
        // asiento de origen ya no existe— con la imagen dead_dealer.
        final boolean dead = isDead_dealer();
        String new_dealer_holder = chipHolder(this.dealer_nick, this.big_blind_nick, this.small_blind_nick);
        String old_dealer_holder = chipHolder(prev_dealer_nick, prev_bb_nick, prev_sb_nick);
        addChipFlight(flights, to_hide, n2p, old_dealer_holder, new_dealer_holder,
                dead ? Helpers.IMAGEN_DEAD_DEALER : Helpers.IMAGEN_DEALER, dead);

        if (flights.isEmpty()) {
            return;
        }

        // Misma duración (velocidad) que el vuelo de cada carta del reparto.
        int pausa = Math.max(100, Math.round(REPARTIR_PAUSA * (2f / getJugadoresActivos())));
        final int flight_dur = Math.max(150, pausa);

        // Oculta las fichas estáticas de los asientos destino para que durante el
        // deslizamiento solo se vean las viajeras.
        Helpers.GUIRunAndWait(() -> {
            for (Player p : to_hide) {
                p.getChip_label().setVisible(false);
            }
        });

        // Al aterrizar repone cada ficha con su icono/visibilidad correctos (bajo
        // la viajera → relevo sin hueco).
        GameFrame.getInstance().getTapete().flyChipsToSeats(flights, flight_dur, () -> {
            for (Player p : to_hide) {
                p.refreshPositionChipIcons();
            }
        });
    }

    // Devuelve el nick que MUESTRA su ficha, o null si ese rol no se pinta por
    // compartir asiento con uno de mayor prioridad (BB > SB > DEALER), replicando
    // refreshPositionChipIcons.
    private static String chipHolder(String nick, String higher1, String higher2) {
        if (nick == null) {
            return null;
        }
        if (nick.equals(higher1) || (higher2 != null && nick.equals(higher2))) {
            return null;
        }
        return nick;
    }

    // Añade un vuelo de ficha del portador anterior al nuevo y registra su asiento
    // destino en to_hide. Se salta si el rol no se pinta (newNick null) o el nuevo
    // portador no se resuelve. Con fromCenter la ficha sale del CENTRO de la mesa
    // (primera mano o dead dealer: el asiento de origen ya no existe) y NO se aplica
    // el filtro de "sin movimiento"; en caso normal, origen = asiento anterior y se
    // omite si no hay movimiento (mismo asiento).
    private void addChipFlight(java.util.List<TablePanel.ChipFlight> flights, java.util.List<Player> to_hide,
            java.util.Map<String, Player> n2p, String oldNick, String newNick, ImageIcon sprite, boolean fromCenter) {
        if (newNick == null || sprite == null) {
            return;
        }
        Player to = n2p.get(newNick);
        if (to == null) {
            return;
        }
        Player from = fromCenter ? null : ((oldNick != null) ? n2p.get(oldNick) : null);
        if (!fromCenter && from == to) {
            return;
        }
        flights.add(new TablePanel.ChipFlight(from, to, sprite));
        to_hide.add(to);
    }

    // Duración (ms) del encogido-y-desvanecido de la ficha al aterrizar en el bote.
    private static final int POT_CHIP_SHRINK_MS = 320;

    // Fichas voladoras al bote en vuelo. Mientras haya alguna, actualizarContadoresTapete
    // NO refresca el VALOR del pot_label: ese refresco se difiere al aterrizaje de la
    // ficha (su onLand), para que el valor del bote suba justo cuando la ficha lo toca
    // y a la vez que el parpadeo amarillo (no antes, con la ficha aún en el aire).
    private final java.util.concurrent.atomic.AtomicInteger pot_chips_in_flight = new java.util.concurrent.atomic.AtomicInteger(0);

    public boolean isPotLabelValueDeferred() {
        return pot_chips_in_flight.get() > 0;
    }

    // Lanza una ficha del bote volando desde el asiento del jugador que acaba de
    // meter dinero (call/bet/all-in) hasta el icono del pot_label, con la misma
    // velocidad/estilo que el vuelo de reparto y un encogido al aterrizar (que
    // hace parpadear el pot_label en amarillo). La disparan los jugadores en el
    // MISMO instante en que suena el sonido de fichas (call/bet/allin.wav), para
    // que animación y sonido vayan sincronizados. Respeta la opción de animación y
    // se salta en RECOVER/fin de transmisión. NO bloquea: la animación corre sola
    // en el EDT y el llamante continúa de inmediato.
    public void launchChipToPot(Player player) {

        if (!GameFrame.apuestasAnimOn() || GameFrame.RECOVER || isFin_de_la_transmision()) {
            // Sin animación de ficha: el handler pudo aplazar el rodaje del stack/bet
            // esperando esta ficha que no vuela -> los rodamos ya (al instante de la
            // acción). No-op si no había aplazamiento.
            player.rollCountersToModel();
            return;
        }

        // Difiere el VALOR del pot_label hasta que la ficha aterrice: incrementa el contador
        // antes de lanzar (sincrónico, así actualizarContadoresTapete ya lo ve diferido) y,
        // al aterrizar (onLand, a la vez que el flash), lo decrementa, refresca el valor con
        // el bote ya commiteado y rueda el stack (baja) y la apuesta (sube) del jugador hasta
        // su modelo -> los TRES a la vez. El handler dejó el stack/bet sin rodar (defer) para
        // esto. Garantizado-una-vez por flyChipToPot.
        pot_chips_in_flight.incrementAndGet();
        GameFrame.getInstance().getTapete().flyChipToPot(player, Helpers.IMAGEN_POT_CHIP, POT_CHIP_SHRINK_MS, () -> {
            pot_chips_in_flight.decrementAndGet();
            // Solo el VALOR del bote (no actualizarContadoresTapete completo): así el
            // aterrizaje no re-muestra la bet_label si el showdown ya la ocultó.
            refreshTapeteBoteValue();
            player.rollCountersToModel();
        });
    }

    public int getGame_recovered() {
        return game_recovered;
    }

    // Prepara la animación de fichas-al-bote de las apuestas FORZADAS del arranque de
    // mano (ciega grande, ciega pequeña y straddle si lo hay; y si hay ante, TODOS los
    // jugadores, en UNA sola tanda). DIFIERE el valor del bote: incrementa
    // pot_chips_in_flight ANTES de actualizarContadoresTapete para que el bote no
    // salte de golpe y se vea INCREMENTAR al aterrizar las fichas (idéntico a
    // bet/call). Las fichas vuelan luego en flyForcedBetsToPot (tras rotar las fichas
    // de posición, justo antes del barajado). Respeta la opción de animación y se
    // salta en RECOVER/fin de transmisión/mano recuperada (camino sin animación intacto).
    private void prepareForcedBetsToPot() {
        this.forced_bet_chip_contributors = null;

        if (!GameFrame.apuestasAnimOn() || GameFrame.RECOVER || isFin_de_la_transmision() || this.game_recovered != 0) {
            return;
        }

        java.util.List<Player> contributors = new java.util.ArrayList<>();

        if (GameFrame.ANTE) {
            // Todos antearon (+ ciegas/straddle): una ficha por cada jugador activo.
            for (Player p : GameFrame.getInstance().getJugadores()) {
                if (p.isActivo()) {
                    contributors.add(p);
                }
            }
        } else {
            // Solo las ciegas: el straddle es VOLUNTARIO y se decide tras repartir
            // (resolveVoluntaryStraddle), así que en esta tanda pre-reparto nunca está
            // posteado — su ficha roja y su dinero vuelan aparte, tras la decisión.
            addForcedBetContributor(contributors, this.big_blind_nick);
            addForcedBetContributor(contributors, this.small_blind_nick);
        }

        if (contributors.isEmpty()) {
            return;
        }

        for (int i = 0; i < contributors.size(); i++) {
            pot_chips_in_flight.incrementAndGet();
        }
        this.forced_bet_chip_contributors = contributors;

        // Muestra el valor INICIAL del bote (antes de las forzadas = el sobrante que
        // arrastra la mano, normalmente 0) para que se vea SUBIR al aterrizar las
        // fichas. El diferido (pot_chips_in_flight, ya incrementado arriba) evita que
        // actualizarContadoresTapete lo pise con el total antes de tiempo.
        GameFrame.getInstance().setTapeteBote(Math.max(0f, this.bote_sobrante), null);
    }

    private void addForcedBetContributor(java.util.List<Player> list, String nick) {
        if (nick == null) {
            return;
        }
        for (Player p : GameFrame.getInstance().getJugadores()) {
            if (p.getNickname().equals(nick) && p.isActivo() && !list.contains(p)) {
                list.add(p);
                return;
            }
        }
    }

    // Siguiente jugador ACTIVO en orden de asiento tras 'nick' (envolvente). Lo usa
    // el straddle para situar el "under the gun" real (primero en hablar) en el
    // asiento siguiente al straddler. Devuelve null si 'nick' no esta o no hay otro.
    private Player nextActivePlayerAfter(String nick) {
        java.util.List<Player> jugadores = GameFrame.getInstance().getJugadores();
        int n = jugadores.size();
        int start = -1;
        for (int i = 0; i < n; i++) {
            if (jugadores.get(i).getNickname().equals(nick)) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return null;
        }
        for (int step = 1; step <= n; step++) {
            Player p = jugadores.get((start + step) % n);
            if (p.isActivo()) {
                return p;
            }
        }
        return null;
    }

    // Vuela las fichas de las forzadas al bote (preparadas en prepareForcedBetsToPot),
    // IDÉNTICO a la animación de bet/call: cada ficha vuela al pot_label, lo incrementa
    // y lo flasea amarillo al aterrizar. Una sola tanda (ante+ciegas+straddle juntos).
    // BLOQUEA hasta el aterrizaje (como la rotación de fichas de posición), justo
    // antes del barajado. onLand de flyChipToPot está garantizado-una-vez, así que
    // pot_chips_in_flight siempre se equilibra aunque se aborte.
    private void flyForcedBetsToPot() {
        final java.util.List<Player> contributors = this.forced_bet_chip_contributors;
        this.forced_bet_chip_contributors = null;

        if (contributors == null || contributors.isEmpty()) {
            // No vuelan fichas forzadas (animacion de apuestas off / recover / etc.). Red de
            // seguridad (A3-2): si una carrera del toggle de apuestas (apuestasAnimOn) dejo a un poster de
            // forzada (ciega/ante) con el rodaje APLAZADO esperando una ficha que ya no vuela,
            // su label quedaria congelado en el valor pre-ciega hasta su proxima accion. Rodar
            // al modelo a todos los activos lo resuelve (no-op para los no aplazados: target ==
            // mostrado -> sin animacion). Aqui no hay otras fichas en vuelo (inicio de mano).
            for (Player jugador : GameFrame.getInstance().getJugadores()) {
                if (jugador != null && jugador.isActivo()) {
                    jugador.rollCountersToModel();
                }
            }
            return;
        }

        // El community panel (y su pot_label) debe estar VISIBLE y posicionado al
        // volar: si no, getPotIconScreenCenter() devuelve null y flyChipToPot manda
        // las fichas al CENTRO de la mesa (fallback). Lo mostramos aqui (aparece el
        // community); el barajado lo vuelve a ocultar despues (setVisible(false) +
        // showCentralFramesLoop), asi la secuencia es: aparece community -> vuelan
        // las forzadas al bote + flaseo + incremento -> se oculta para el barajado.
        Helpers.GUIRunAndWait(() -> {
            GameFrame.getInstance().getTapete().getCommunityCards().setVisible(true);
            GameFrame.getInstance().getTapete().getCommunityCards().revalidate();
            GameFrame.getInstance().getTapete().getCommunityCards().repaint();
        });

        Audio.playWavResource("misc/bet.wav");

        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(contributors.size());

        for (Player p : contributors) {
            GameFrame.getInstance().getTapete().flyChipToPot(p, Helpers.IMAGEN_POT_CHIP, POT_CHIP_SHRINK_MS, () -> {
                pot_chips_in_flight.decrementAndGet();
                refreshTapeteBoteValue();
                // A LA VEZ que el bote sube y flasea: rueda el stack (baja) y la apuesta
                // (sube) de ESTE contribuyente hasta su modelo. Estaba congelado en su valor
                // previo desde prepareForcedBetsToPot -> los tres arrancan juntos al aterrizar.
                p.rollCountersToModel();
                latch.countDown();
            });
        }

        try {
            latch.await(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }

        // Espera al fin EXACTO del parpadeo amarillo del bote (NO un tiempo arbitrario):
        // onPotFlashDone dispara su callback cuando el timer del flash (170ms tras el
        // ultimo aterrizaje) termina, o de inmediato si ya no hay flash. Asi el barajado
        // no oculta el community hasta que se haya visto el incremento + el flaseo.
        if (!isFin_de_la_transmision()) {
            final java.util.concurrent.CountDownLatch flash_done = new java.util.concurrent.CountDownLatch(1);
            GameFrame.getInstance().getTapete().getCommunityCards().onPotFlashDone(flash_done::countDown);
            try {
                flash_done.await(2, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // Conteo animado de stacks a TIEMPO CONSTANTE: duracion fija STACK_FILL_MS para
    // TODOS, asi acaban a la vez aunque tengan stacks distintos (buy-in variable) —
    // a velocidad constante se escalonarian. Interpolacion LINEAL, SIN frenadita: la
    // frenadita (ease-out) y el parpadeo son sello del contador final (BalanceDialog),
    // no de esta cortinilla. Palanca facil si el autor lo quiere mas rapido/lento.
    private static final long STACK_FILL_MS = 1000;

    // Llenado de stacks (apertura + recompra) NO bloqueante: NUNCA frena el juego (ciegas/
    // dealer/reparto animan en paralelo). Publica latch + nicks de la tanda en curso para el
    // gate POR JUGADOR (awaitStackFillIfPending), que solo bloquea EL TURNO del jugador cuyo
    // stack aun sube. null/liberado = sin llenado pendiente.
    private volatile java.util.concurrent.CountDownLatch stack_fill_latch = null;
    private volatile java.util.Set<String> stack_fill_nicks = null;

    // Gate unico del conteo animado de stacks (apertura + recompra): respeta la
    // opcion de animacion de CONTADORES (Ajustes) y se salta en recover / fin de
    // transmision (camino sin animacion byte-identico al de antes). Lo consultan
    // animateInitialStacks/animateRebuyStacks; esta ultima CAPTURA su decision en
    // rebuy_fill_animated, que es lo que lee reComprar (asi un toggle a mitad del
    // conteo no duplica la caja registradora).
    public boolean isStackFillAnimated() {
        return GameFrame.contadoresAnimOn() && !GameFrame.RECOVER && !isFin_de_la_transmision();
    }

    // ¿La recompra de la tanda en curso se animo con la cortinilla de stacks (que ya
    // toco la caja registradora)? animateRebuyStacks lo CAPTURA una vez; reComprar lo
    // lee para su 'silent'. Capturarlo —en vez de releer isStackFillAnimated en
    // reComprar— evita que apagar "Contadores" a mitad del conteo (~1 s) haga que
    // reComprar crea que no se animo y suene la caja una SEGUNDA vez.
    private volatile boolean rebuy_fill_animated = false;

    public boolean isRebuyFillAnimated() {
        return rebuy_fill_animated;
    }

    // ¿Hay que APLAZAR el rodaje vivo del stack/apuesta del jugador hasta que su ficha
    // aterrice en el bote? Sí cuando va a volar ficha (apuestasAnimOn) y los
    // contadores ruedan (contadoresAnimOn). Lo consultan los handlers de acción
    // antes de mover el dinero: si es true, setBet/setStack no ruedan (el label se queda)
    // y launchChipToPot los rueda en el aterrizaje, junto al bote (los tres a la vez).
    public boolean shouldDeferCountersToChip() {
        return GameFrame.apuestasAnimOn() && GameFrame.contadoresAnimOn()
                && !GameFrame.RECOVER && !isFin_de_la_transmision();
    }

    // Contador animado de stacks: cada label sube LINEAL de from[i] a to[i] sobre la
    // MISMA duracion fija (STACK_FILL_MS) -> TODOS acaban a la vez aunque tengan
    // stacks distintos. Progreso por reloj de pared (robusto a frame drops). Sin
    // frenadita (eso es del contador final). PURO VISUAL (setStackDisplay, no toca el
    // modelo). BLOQUEA el hilo llamante hasta el ultimo frame mediante una BARRERA
    // (CountDownLatch liberado EXACTO al terminar, no un sleep): nada se postea/paga
    // antes de que esten llenos. Si start_sound != null, suena al arrancar.
    private void animateStackFill(java.util.List<Player> players, double[] from, double[] to, String start_sound) {
        if (players == null || players.isEmpty()) {
            return;
        }

        if (start_sound != null) {
            Audio.playWavResource(start_sound);
        }

        final int n = players.size();

        // Frame 0: todos a su valor inicial antes de arrancar el rodaje.
        Helpers.GUIRunAndWait(() -> {
            for (int i = 0; i < n; i++) {
                players.get(i).setStackDisplay(from[i]);
            }
        });

        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final long start_ms = System.currentTimeMillis();

        // Publica latch + nicks ANTES de arrancar el Timer: el juego NO espera (esto retorna
        // ya), solo esTuTurno (awaitStackFillIfPending) bloquea el turno de cada jugador hasta
        // que SU stack acabe de subir. El Timer libera el latch en el ultimo frame.
        java.util.Set<String> nicks = new java.util.HashSet<>();
        for (Player pl : players) {
            nicks.add(pl.getNickname());
        }
        this.stack_fill_nicks = nicks;
        this.stack_fill_latch = latch;

        // El Timer vive y corre en el EDT (createlo alli). Cada tick avanza el mismo progreso
        // lineal para todos; al cumplirse la duracion fija los valores finales y libera el
        // latch (el gate por jugador). NO se bloquea aqui: la mano sigue su curso.
        Helpers.GUIRun(() -> {
            javax.swing.Timer roll = new javax.swing.Timer(16, null);
            roll.addActionListener((e) -> {
                double p = Math.min(1.0, (System.currentTimeMillis() - start_ms) / (double) STACK_FILL_MS);

                if (p >= 1.0) {
                    ((javax.swing.Timer) e.getSource()).stop();
                    for (int i = 0; i < n; i++) {
                        // Aterriza en el MODELO actual, no en to[i] (precalculado pre-ciega):
                        // como el llenado NO bloquea, la ciega/ante puede haberse posteado a
                        // mitad (el stack ya esta descontado). Usar to[i] dejaria el label
                        // demasiado alto (por el importe de la ciega) hasta la siguiente
                        // actualizacion. getStack() = valor real, lock-free: el campo es
                        // volatile y getStack ya NO es synchronized (como el resto de
                        // accesores), asi que leerlo en el EDT NO coge el monitor del jugador
                        // -> imposible el deadlock EDT<->worker (ver setStack/setBet).
                        players.get(i).setStackDisplay(players.get(i).getStack());
                    }
                    latch.countDown();
                    return;
                }

                for (int i = 0; i < n; i++) {
                    double value = from[i] + (to[i] - from[i]) * p; // lineal, sin frenadita
                    players.get(i).setStackDisplay(Helpers.doubleClean(value));
                }
            });
            roll.start();
        });
    }

    // Gate POR JUGADOR del llenado de stacks: si 'nick' esta en la tanda en curso y su stack
    // aun no ha terminado de subir, BLOQUEA hasta que termine (lo llama esTuTurno antes de
    // activar el turno: borde + botones). Asi el juego NO se frena por las animaciones, solo
    // el turno del jugador a medio llenar. Tope DEFENSIVO (muerte del EDT). Sin llenado
    // pendiente para ese nick -> retorna al instante (latch ya liberado o nick no esta).
    public void awaitStackFillIfPending(String nick) {
        java.util.concurrent.CountDownLatch l = this.stack_fill_latch;
        java.util.Set<String> nicks = this.stack_fill_nicks;
        if (l == null || nicks == null || !nicks.contains(nick)) {
            return;
        }
        try {
            l.await(STACK_FILL_MS + 1500, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    // Apertura de timba: el stack de cada jugador sentado sube de 0 a su buy-in a
    // la vez, como cortinilla, ANTES de la primera mano (y por tanto antes de
    // postear/volar las ciegas). La barrera de animateStackFill garantiza que la
    // mano no arranca hasta que los stacks esten llenos. El fogonazo del buy-in se
    // evita pintando ya el label a 0 en el constructor de GameFrame (mismo gate).
    private void animateInitialStacks() {
        if (!isStackFillAnimated()) {
            return;
        }

        java.util.List<Player> players = new java.util.ArrayList<>();
        for (Player p : GameFrame.getInstance().getJugadores()) {
            if (p != null && !p.isExit() && !p.isCalentando()
                    && Helpers.doubleSecureCompare(0f, p.getStack()) < 0) {
                players.add(p);
            }
        }
        if (players.isEmpty()) {
            return;
        }

        double[] from = new double[players.size()];
        double[] to = new double[players.size()];
        for (int i = 0; i < players.size(); i++) {
            from[i] = 0f;
            to[i] = players.get(i).getStack();
        }

        animateStackFill(players, from, to, null);
    }

    // Recompra animada: ANTES de aplicar el rebuy al modelo (nuevaMano ->
    // reComprar), el stack de cada recomprador sube de su valor actual al valor
    // tras recomprar, a la vez, con la caja registradora. La barrera deja terminar
    // el conteo antes de que la mano avance. reComprar pone el modelo justo despues
    // aterrizando en el MISMO valor (sin fogonazo) y, por el gate compartido, NO
    // repite el sonido. 'applied' se recalcula igual que reComprar (headroom) para
    // que el destino coincida exactamente con el que fijara el modelo.
    private void animateRebuyStacks(java.util.Set<String> rebuy_nicks) {
        // Por defecto NO animada: cada reComprar pondra su propio sonido. Se marca true
        // solo si de verdad lanzamos la cortinilla (que toca la caja) -> reComprar mudo.
        this.rebuy_fill_animated = false;
        if (!isStackFillAnimated() || rebuy_nicks == null || rebuy_nicks.isEmpty()) {
            return;
        }

        java.util.List<Player> players = new java.util.ArrayList<>();
        java.util.List<Double> froms = new java.util.ArrayList<>();
        java.util.List<Double> tos = new java.util.ArrayList<>();

        for (String nick : rebuy_nicks) {
            Player p = nick2player.get(nick);
            Integer amount = rebuy_now.get(nick);
            if (p == null || amount == null) {
                continue;
            }
            double old_stack = p.getStack();
            int applied = Math.min(amount, GameFrame.rebuyHeadroom(old_stack));
            if (applied <= 0) {
                continue;
            }
            players.add(p);
            froms.add(old_stack);
            tos.add(old_stack + applied);
        }

        if (players.isEmpty()) {
            return;
        }

        double[] from = new double[players.size()];
        double[] to = new double[players.size()];
        for (int i = 0; i < players.size(); i++) {
            from[i] = froms.get(i);
            to[i] = tos.get(i);
        }

        this.rebuy_fill_animated = true;
        animateStackFill(players, from, to, "misc/cash_register.wav");
    }

    public String getBb_nick() {
        return big_blind_nick;
    }

    public String getSb_nick() {
        return small_blind_nick;
    }

    public String getUtg_nick() {
        return utg_nick;
    }

    public String getStraddleUtgNick() {
        return straddle_utg_nick;
    }

    public boolean isStraddle_posted() {
        return straddle_posted;
    }

    public ConcurrentHashMap<String, Player> getNick2player() {
        return nick2player;
    }

    public ConcurrentHashMap<String, Integer> getRebuy_now() {
        return rebuy_now;
    }

    public ConcurrentHashMap<String, Integer> getRebuy_counts() {
        return rebuy_counts;
    }

    public int getRebuyCount(String nick) {
        return rebuy_counts.getOrDefault(nick, 0);
    }

    public boolean atRebuyLimit(String nick) {
        return GameFrame.REBUY_LIMIT > 0 && getRebuyCount(nick) >= GameFrame.REBUY_LIMIT;
    }

    public boolean isLast_hand() {

        synchronized (lock_last_hand) {
            return last_hand;
        }
    }

    public void setLast_hand(boolean last_hand) {
        synchronized (lock_last_hand) {
            this.last_hand = last_hand;
        }
    }

    public boolean isRebuy_time() {
        return rebuy_time;
    }

    public Object getLock_nueva_mano() {
        return lock_nueva_mano;
    }

    public double getApuestas() {
        return apuestas;
    }

    public int getConta_bet() {
        return conta_bet;
    }

    public String getCurrent_remote_cinematic_b64() {
        return current_remote_cinematic_b64;
    }

    public void setCurrent_remote_cinematic_b64(String current_remote_cinematic_b64) {
        this.current_remote_cinematic_b64 = current_remote_cinematic_b64;
    }

    public void rebuyNow(String nick, int buyin) {

        synchronized (lock_rebuynow) {
            boolean denied_by_limit = false;
            boolean broadcast_now = true;
            if (!rebuy_now.containsKey(nick)) {
                if (atRebuyLimit(nick)) {
                    denied_by_limit = true;
                    broadcast_now = false;
                } else {
                    // El host es el banco: acotamos el importe al headroom (techo de
                    // mesa - stack actual) para que un cliente manipulado no fabrique
                    // fichas ni supere el techo via REBUYNOW#nick#<entero arbitrario>.
                    // El spinner del RebuyDialog ya lo acota; esto es la defensa. El
                    // centinela -1 del toggle-off local no llega aquí (rama remove).
                    Player jp = nick2player.get(nick);
                    int headroom = GameFrame.rebuyHeadroom(jp != null ? jp.getStack() : 0f);
                    int safe_buyin = Math.min(buyin, headroom);
                    if (safe_buyin <= 0) {
                        // Sin margen (ya en el techo): se ignora la solicitud. La UI
                        // ya deberia haberlo impedido; aqui no izamos toggle ni
                        // difundimos nada.
                        LOGGER.log(Level.WARNING, "Rebuy request from {0} ignored: stack at table ceiling {1}",
                                new Object[]{nick, GameFrame.getBuyinCap()});
                        broadcast_now = false;
                    } else {
                        if (safe_buyin != buyin) {
                            LOGGER.log(Level.WARNING, "Rebuy amount {0} from {1} exceeds headroom {2} — clamped to {3}",
                                    new Object[]{buyin, nick, headroom, safe_buyin});
                        }
                        this.rebuy_now.put(nick, safe_buyin);
                    }
                }
            } else {
                this.rebuy_now.remove(nick);
            }

            if (GameFrame.getInstance().isPartida_local()) {

                try {
                    if (denied_by_limit) {
                        this.broadcastGAMECommandFromServer(
                                "REBUYDENIED#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")) + "#"
                                + String.valueOf(GameFrame.REBUY_LIMIT),
                                null, false);
                    } else if (broadcast_now) {
                        this.broadcastGAMECommandFromServer(
                                "REBUYNOW#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")) + "#"
                                + String.valueOf(buyin),
                                nick.equals(GameFrame.getInstance().getLocalPlayer().getNickname()) ? null : nick);
                    }
                } catch (UnsupportedEncodingException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }

            } else if (broadcast_now && nick.equals(GameFrame.getInstance().getLocalPlayer().getNickname())) {

                this.sendGAMECommandToServer("REBUYNOW#" + String.valueOf(buyin));
            }
        }
    }

    public static void loadMODCinematicsAllin() {

        if (Init.MOD != null) {

            ArrayList<Object[]> cinematics = new ArrayList<>();

            File cinematics_folder = new File(Helpers.getCurrentJarParentPath() + "/mod/cinematics/allin");

            // ALLIN CINEMATICS
            if (cinematics_folder.isDirectory() && cinematics_folder.canRead()
                    && cinematics_folder.listFiles(File::isFile).length > 0) {

                for (final File fileEntry : cinematics_folder.listFiles(File::isFile)) {
                    if (fileEntry.getName().toLowerCase().endsWith(".gif")) {
                        cinematics.add(new Object[]{fileEntry.getName()});
                    }
                }

                if (FUSION_MOD_CINEMATICS) {

                    cinematics.addAll(Arrays.asList(Crupier.ALLIN_CINEMATICS.getValue()));
                }
                Crupier.ALLIN_CINEMATICS_MOD = new HashMap.SimpleEntry<>("allin/", cinematics.toArray(new Object[0][]));

            } else {

                Crupier.ALLIN_CINEMATICS_MOD = Crupier.ALLIN_CINEMATICS;
            }

        }
    }

    public static void loadMODSounds() {

        if (Init.MOD != null) {

            if (Files.exists(Paths
                    .get(Helpers.getCurrentJarParentPath() + "/mod/sounds/joke/" + GameFrame.LANGUAGE + "/allin/"))) {
                File[] archivos = new File(
                        Helpers.getCurrentJarParentPath() + "/mod/sounds/joke/" + GameFrame.LANGUAGE + "/allin/")
                        .listFiles(File::isFile);

                ArrayList<String> filenames = new ArrayList<>();

                for (var f : archivos) {

                    filenames.add(f.getName());
                }

                if (!FUSION_MOD_SOUNDS) {
                    Crupier.ALLIN_SOUNDS_MOD = new HashMap.SimpleEntry<>("joke/" + GameFrame.LANGUAGE + "/allin/",
                            filenames.toArray(new String[0]));
                } else {

                    ArrayList<String> sounds = new ArrayList<>();

                    sounds.addAll(Arrays.asList(Crupier.ALLIN_SOUNDS.get(GameFrame.LANGUAGE).getValue()));

                    sounds.addAll(filenames);

                    Crupier.ALLIN_SOUNDS_MOD = new HashMap.SimpleEntry<>("joke/" + GameFrame.LANGUAGE + "/allin/",
                            sounds.toArray(new String[0]));
                }

            } else {

                Crupier.ALLIN_SOUNDS_MOD = Crupier.ALLIN_SOUNDS.get(GameFrame.LANGUAGE);
            }

            if (Files.exists(Paths
                    .get(Helpers.getCurrentJarParentPath() + "/mod/sounds/joke/" + GameFrame.LANGUAGE + "/fold/"))) {
                File[] archivos = new File(
                        Helpers.getCurrentJarParentPath() + "/mod/sounds/joke/" + GameFrame.LANGUAGE + "/fold/")
                        .listFiles(File::isFile);

                ArrayList<String> filenames = new ArrayList<>();

                for (var f : archivos) {

                    filenames.add(f.getName());
                }

                if (!FUSION_MOD_SOUNDS) {
                    Crupier.FOLD_SOUNDS_MOD = new HashMap.SimpleEntry<>("joke/" + GameFrame.LANGUAGE + "/fold/",
                            filenames.toArray(new String[0]));
                } else {

                    ArrayList<String> sounds = new ArrayList<>();

                    sounds.addAll(Arrays.asList(Crupier.FOLD_SOUNDS.get(GameFrame.LANGUAGE).getValue()));

                    sounds.addAll(filenames);

                    Crupier.FOLD_SOUNDS_MOD = new HashMap.SimpleEntry<>("joke/" + GameFrame.LANGUAGE + "/fold/",
                            sounds.toArray(new String[0]));
                }

            } else {
                Crupier.FOLD_SOUNDS_MOD = Crupier.FOLD_SOUNDS.get(GameFrame.LANGUAGE);
            }

            if (Files.exists(Paths.get(
                    Helpers.getCurrentJarParentPath() + "/mod/sounds/joke/" + GameFrame.LANGUAGE + "/showdown/"))) {
                File[] archivos = new File(
                        Helpers.getCurrentJarParentPath() + "/mod/sounds/joke/" + GameFrame.LANGUAGE + "/showdown/")
                        .listFiles(File::isFile);

                ArrayList<String> filenames = new ArrayList<>();

                for (var f : archivos) {

                    filenames.add(f.getName());
                }

                if (!FUSION_MOD_SOUNDS) {
                    Crupier.SHOWDOWN_SOUNDS_MOD = new HashMap.SimpleEntry<>("joke/" + GameFrame.LANGUAGE + "/showdown/",
                            filenames.toArray(new String[0]));
                } else {

                    ArrayList<String> sounds = new ArrayList<>();

                    sounds.addAll(Arrays.asList(Crupier.SHOWDOWN_SOUNDS.get(GameFrame.LANGUAGE).getValue()));

                    sounds.addAll(filenames);

                    Crupier.SHOWDOWN_SOUNDS_MOD = new HashMap.SimpleEntry<>("joke/" + GameFrame.LANGUAGE + "/showdown/",
                            sounds.toArray(new String[0]));
                }

            } else {

                Crupier.SHOWDOWN_SOUNDS_MOD = Crupier.SHOWDOWN_SOUNDS.get(GameFrame.LANGUAGE);
            }

            if (Files.exists(Paths
                    .get(Helpers.getCurrentJarParentPath() + "/mod/sounds/joke/" + GameFrame.LANGUAGE + "/loser/"))) {
                File[] archivos = new File(
                        Helpers.getCurrentJarParentPath() + "/mod/sounds/joke/" + GameFrame.LANGUAGE + "/loser/")
                        .listFiles(File::isFile);

                ArrayList<String> filenames = new ArrayList<>();

                for (var f : archivos) {

                    filenames.add(f.getName());
                }

                if (!FUSION_MOD_SOUNDS) {
                    Crupier.LOSER_SOUNDS_MOD = new HashMap.SimpleEntry<>("joke/" + GameFrame.LANGUAGE + "/loser/",
                            filenames.toArray(new String[0]));
                } else {

                    ArrayList<String> sounds = new ArrayList<>();

                    sounds.addAll(Arrays.asList(Crupier.LOSER_SOUNDS.get(GameFrame.LANGUAGE).getValue()));

                    sounds.addAll(filenames);

                    Crupier.LOSER_SOUNDS_MOD = new HashMap.SimpleEntry<>("joke/" + GameFrame.LANGUAGE + "/loser/",
                            sounds.toArray(new String[0]));
                }

            } else {

                Crupier.LOSER_SOUNDS_MOD = Crupier.LOSER_SOUNDS.get(GameFrame.LANGUAGE);
            }

            if (Files.exists(Paths
                    .get(Helpers.getCurrentJarParentPath() + "/mod/sounds/joke/" + GameFrame.LANGUAGE + "/winner/"))) {
                File[] archivos = new File(
                        Helpers.getCurrentJarParentPath() + "/mod/sounds/joke/" + GameFrame.LANGUAGE + "/winner/")
                        .listFiles(File::isFile);

                ArrayList<String> filenames = new ArrayList<>();

                for (var f : archivos) {

                    filenames.add(f.getName());
                }

                if (!FUSION_MOD_SOUNDS) {
                    Crupier.WINNER_SOUNDS_MOD = new HashMap.SimpleEntry<>("joke/" + GameFrame.LANGUAGE + "/winner/",
                            filenames.toArray(new String[0]));
                } else {

                    ArrayList<String> sounds = new ArrayList<>();

                    sounds.addAll(Arrays.asList(Crupier.WINNER_SOUNDS.get(GameFrame.LANGUAGE).getValue()));

                    sounds.addAll(filenames);

                    Crupier.WINNER_SOUNDS_MOD = new HashMap.SimpleEntry<>("joke/" + GameFrame.LANGUAGE + "/winner/",
                            sounds.toArray(new String[0]));
                }

            } else {
                Crupier.WINNER_SOUNDS_MOD = Crupier.WINNER_SOUNDS.get(GameFrame.LANGUAGE);
            }

        }

    }

    public boolean isFin_de_la_transmision() {
        return fin_de_la_transmision;
    }

    public void setFin_de_la_transmision(boolean fin) {
        fin_de_la_transmision = fin;
    }

    public boolean isSincronizando_mano() {
        return sincronizando_mano;
    }

    public void setSincronizando_mano(boolean sincronizando_mano) {
        this.sincronizando_mano = sincronizando_mano;
    }

    public Object getLock_contabilidad() {
        return lock_contabilidad;
    }

    public void setTiempo_pausa(int tiempo) {

        synchronized (lock_pausa_barra) {
            this.tiempo_pausa = tiempo;

            Helpers.smoothCountdown(GameFrame.getInstance().getBarra_tiempo(), tiempo);
        }

    }

    public int getTiempoPausa() {

        synchronized (lock_pausa_barra) {

            return tiempo_pausa;
        }
    }

    public long getTurno() {
        return turno;
    }

    // Apaga el flag de cinemática en curso y despierta a los hilos que esperan
    // su final en LOCK_CINEMATICS (watcher de _cinematicAllin, turno de los
    // bots). Para los early-outs que NO van a reproducir animación: el caller
    // (botón all-in local / RemotePlayer.allin) ya puso PLAYING_CINEMATIC=true.
    private void cinematicOff() {
        Init.PLAYING_CINEMATIC = false;

        synchronized (Init.LOCK_CINEMATICS) {
            Init.LOCK_CINEMATICS.notifyAll();
        }
    }

    // Bolsa de cinemáticas de all-in: índices de allin_cinematics barajados
    // con el CSPRNG. En vez de tirar un dado por all-in (que por azar repite
    // la misma varias veces seguidas), cada jugador baraja localmente TODAS
    // las animaciones en su primer all-in de la timba (Crupier nuevo por
    // partida = bolsa nueva) y las va consumiendo en orden; al agotarse se
    // rebaraja. El guard de la frontera evita además repetir entre el final
    // de una bolsa y el comienzo de la siguiente. La elección sigue siendo
    // local del que actúa (viaja a los demás dentro del ACTION), así que la
    // bolsa no necesita sincronía con nadie.
    private final ArrayList<Integer> allin_cinematic_bag = new ArrayList<>();
    private int last_allin_cinematic = -1;

    private int nextAllinCinematic(int total) {
        if (this.allin_cinematic_bag.isEmpty()) {
            for (int i = 0; i < total; i++) {
                this.allin_cinematic_bag.add(i);
            }
            Collections.shuffle(this.allin_cinematic_bag, Helpers.CSPRNG_GENERATOR);
            // Se consume desde el final (remove O(1)): si el primero en salir
            // del rebarajado repitiera el último mostrado, se permuta con otra
            // posición al azar (solo posible con 2+ animaciones).
            if (total > 1 && this.allin_cinematic_bag.get(total - 1) == this.last_allin_cinematic) {
                Collections.swap(this.allin_cinematic_bag, total - 1, Helpers.CSPRNG_GENERATOR.nextInt(total - 1));
            }
        }
        this.last_allin_cinematic = this.allin_cinematic_bag.remove(this.allin_cinematic_bag.size() - 1);
        return this.last_allin_cinematic;
    }

    // Duración de una cinemática del catálogo local: la declarada en la tabla
    // o, si la entrada no la trae, la del propio GIF (mod primero, bundled
    // después). 0 si no se pudo determinar.
    private long allinCinematicPausa(Object[] cinematic) {

        String filename = (String) cinematic[0];

        long pausa = 0L;

        if (cinematic.length > 1) {

            pausa = (long) cinematic[1];

        } else if (Files
                .exists(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/cinematics/allin/" + filename))) {

            try {
                pausa = Helpers.getGIFLength(
                        Paths.get(Helpers.getCurrentJarParentPath() + "/mod/cinematics/allin/" + filename).toUri()
                                .toURL());

            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        } else if (getClass().getResource("/cinematics/allin/" + filename) != null) {
            try {
                pausa = Helpers
                        .getGIFLength(getClass().getResource("/cinematics/allin/" + filename).toURI().toURL());

            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }

        return pausa;
    }

    // URL local de un GIF de cinemática de all-in: mod primero (si el fichero
    // del mod existe, NO se cae al bundled aunque su URL falle — mismo
    // comportamiento de siempre), bundled después, null si no existe en esta
    // máquina.
    private URL resolveAllinCinematicURL(String filename) {

        if (Init.MOD != null && Files
                .exists(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/cinematics/allin/" + filename))) {
            try {
                return Paths.get(Helpers.getCurrentJarParentPath() + "/mod/cinematics/allin/" + filename)
                        .toUri().toURL();
            } catch (MalformedURLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
                return null;
            }
        }

        return getClass().getResource("/cinematics/allin/" + filename);
    }

    public boolean localCinematicAllin() {

        Map<String, Object[][]> map = Init.MOD != null ? Map.ofEntries(Crupier.ALLIN_CINEMATICS_MOD)
                : Map.ofEntries(Crupier.ALLIN_CINEMATICS);

        if (!this.sincronizando_mano && GameFrame.cinematicasOn() && map.containsKey("allin/")
                && map.get("allin/").length > 0) {

            Object[][] allin_cinematics = map.get("allin/");

            int r = nextAllinCinematic(allin_cinematics.length);

            String filename = (String) allin_cinematics[r][0];

            long pausa = allinCinematicPausa(allin_cinematics[r]);

            if (pausa != 0L) {

                try {

                    this.current_local_cinematic_b64 = Base64.getEncoder().encodeToString(
                            (Base64.getEncoder().encodeToString(filename.getBytes("UTF-8")) + "#"
                                    + String.valueOf(pausa))
                                    .getBytes("UTF-8"));

                    synchronized (getLock_apuestas()) {
                        getLock_apuestas().notifyAll();
                    }

                    if (Files
                            .exists(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/cinematics/allin/"
                                    + filename.replaceAll("\\.gif$", ".wav")))
                            || getClass().getResource(
                                    "/cinematics/allin/" + filename.replaceAll("\\.gif$", ".wav")) != null) {

                        Audio.playWavResource("allin/" + filename.replaceAll("\\.gif$", ".wav"));
                    }

                    return _cinematicAllin(filename, pausa);

                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                    cinematicOff();
                    this.current_local_cinematic_b64 = null;
                }
            } else {
                cinematicOff();
                this.current_local_cinematic_b64 = null;
            }
        } else {
            cinematicOff();
            this.current_local_cinematic_b64 = null;
        }

        return false;

    }

    public boolean remoteCinematicAllin() {

        if (getCurrent_remote_cinematic_b64() != null) {

            try {

                String animationb64 = new String(Base64.getDecoder().decode(getCurrent_remote_cinematic_b64()),
                        "UTF-8");

                String[] partes = animationb64.split("#");

                return _cinematicAllin(new String(Base64.getDecoder().decode(partes[0]), "UTF-8"),
                        Long.parseLong(partes[1]));

            } catch (UnsupportedEncodingException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
                cinematicOff();
                setCurrent_remote_cinematic_b64(null);
            }

        } else {
            // Camino de TODOS los all-in de bot (no llevan cinemática): el
            // notify de cinematicOff evita que el siguiente bot en turno se
            // coma el timed-wait entero por ver el flag aún encendido.
            cinematicOff();
        }

        return false;

    }

    private boolean _cinematicAllin(String announced_filename, long announced_pausa) {

        // MODs: el GIF anunciado en el ACTION sale del catálogo de QUIEN actúa,
        // y esta máquina puede no tenerlo. Si no se resuelve localmente y hay
        // catálogo propio, se sustituye por la siguiente cinemática de la
        // bolsa local (mismo sistema barajado que para los all-in propios),
        // con SU duración (si no se pudo determinar, se conserva la anunciada:
        // el diálogo cierra solo al acabar los frames y la duración solo pauta
        // el remanente del skip).
        String chosen_filename = announced_filename;
        long chosen_pausa = announced_pausa;

        if (!this.sincronizando_mano && GameFrame.cinematicasOn()
                && resolveAllinCinematicURL(announced_filename) == null) {

            Map<String, Object[][]> map = Init.MOD != null ? Map.ofEntries(Crupier.ALLIN_CINEMATICS_MOD)
                    : Map.ofEntries(Crupier.ALLIN_CINEMATICS);

            if (map.containsKey("allin/") && map.get("allin/").length > 0) {

                Object[][] allin_cinematics = map.get("allin/");

                Object[] sustituta = allin_cinematics[nextAllinCinematic(allin_cinematics.length)];

                chosen_filename = (String) sustituta[0];

                long sub_pausa = allinCinematicPausa(sustituta);

                if (sub_pausa != 0L) {
                    chosen_pausa = sub_pausa;
                }

                LOGGER.log(Level.INFO, "All-in cinematic {0} not available locally — substituting {1}", new Object[]{announced_filename, chosen_filename});
            }
        }

        final String filename = chosen_filename;
        final long pausa = chosen_pausa;

        if (this.sincronizando_mano) {
            // Replay de recovery: la cinemática se omite, pero el caller
            // (RemotePlayer.allin / botón local) ya puso PLAYING_CINEMATIC=true.
            // Hay que apagarlo aquí o el flag quedaría atascado en true y la
            // espera de los bots al fin de la cinemática se bloquearía tras
            // la recuperación.
            cinematicOff();
            this.current_remote_cinematic_b64 = null;

        } else {

            Helpers.barraIndeterminada(GameFrame.getInstance().getBarra_tiempo());

            if (GameFrame.cinematicasOn()) {

                final ImageIcon icon;
                URL url_icon = resolveAllinCinematicURL(filename);

                if (url_icon != null) {

                    icon = new ImageIcon(url_icon);

                    final URL f_url_icon = url_icon;

                    Helpers.threadRun(new Runnable() {
                        private volatile GifAnimationDialog gif_dialog;

                        public void run() {

                            if (pausa != 0L) {

                                long now = System.currentTimeMillis();

                                Helpers.GUIRun(() -> {
                                    try {
                                        gif_dialog = new GifAnimationDialog(GameFrame.getInstance(), true, icon,
                                                Helpers.getGIFFramesCount(f_url_icon));
                                        gif_dialog.setLocationRelativeTo(gif_dialog.getParent());
                                        gif_dialog.setVisible(true);
                                    } catch (IOException | ImageProcessingException ex) {
                                        LOGGER.log(Level.SEVERE, null, ex);
                                    }
                                });

                                synchronized (Init.LOCK_CINEMATICS) {
                                    while (Init.PLAYING_CINEMATIC && !gif_dialog.isForce_exit()) {

                                        try {
                                            Init.LOCK_CINEMATICS.wait(1000);

                                        } catch (InterruptedException ex) {
                                            Helpers.logCooperativeCancellation(LOGGER, "cinematic playback wait", ex);
                                            break;
                                        }
                                    }
                                }

                                if (gif_dialog.isForce_exit()) {

                                    long pause = now + pausa - System.currentTimeMillis();

                                    if (pause > 0) {
                                        Helpers.pausar(pause);
                                    }

                                    Init.PLAYING_CINEMATIC = false;

                                    synchronized (Init.LOCK_CINEMATICS) {

                                        Init.LOCK_CINEMATICS.notifyAll();

                                    }
                                }
                            }

                            current_remote_cinematic_b64 = null;

                            Helpers.GUIRun(() -> {
                                if (gif_dialog.isVisible()) {
                                    gif_dialog.dispose();
                                }

                                Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), GameFrame.THINK_TIME);
                            });

                            synchronized (GameFrame.getInstance().getCrupier().getLock_apuestas()) {
                                GameFrame.getInstance().getCrupier().getLock_apuestas().notifyAll();
                            }
                        }
                    });

                } else {

                    Helpers.threadRun(() -> {
                        if (current_remote_cinematic_b64 != null && pausa != 0L) {

                            Helpers.pausar(pausa);
                            Init.PLAYING_CINEMATIC = false;

                            synchronized (Init.LOCK_CINEMATICS) {

                                Init.LOCK_CINEMATICS.notifyAll();

                            }
                        }

                        current_remote_cinematic_b64 = null;

                        Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), GameFrame.THINK_TIME);

                        synchronized (GameFrame.getInstance().getCrupier().getLock_apuestas()) {
                            GameFrame.getInstance().getCrupier().getLock_apuestas().notifyAll();
                        }
                    });
                }

            } else {

                Helpers.threadRun(() -> {
                    if (pausa != 0L) {
                        Helpers.pausar(pausa);
                        Init.PLAYING_CINEMATIC = false;

                        synchronized (Init.LOCK_CINEMATICS) {

                            Init.LOCK_CINEMATICS.notifyAll();

                        }
                    }

                    current_remote_cinematic_b64 = null;

                    Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), GameFrame.THINK_TIME);

                    synchronized (GameFrame.getInstance().getCrupier().getLock_apuestas()) {
                        GameFrame.getInstance().getCrupier().getLock_apuestas().notifyAll();
                    }
                });

            }

        }

        return (Files
                .exists(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/cinematics/allin/"
                        + filename.replaceAll("\\.gif$", ".wav")))
                || getClass().getResource("/cinematics/allin/" + filename.replaceAll("\\.gif$", ".wav")) != null);

    }

    public void soundAllin() {

        if (!this.sincronizando_mano && GameFrame.SONIDOS_CHORRA && !fold_sound_playing) {

            Audio.playRandomWavResource(Init.MOD != null ? Map.ofEntries(Crupier.ALLIN_SOUNDS_MOD)
                    : Map.ofEntries(Crupier.ALLIN_SOUNDS.get(GameFrame.LANGUAGE)));

        }

    }

    public void soundFold() {
        if (!this.sincronizando_mano && GameFrame.SONIDOS_CHORRA && !fold_sound_playing) {
            this.fold_sound_playing = true;
            Helpers.threadRun(() -> {
                Audio.playRandomWavResourceAndWait(Init.MOD != null ? Map.ofEntries(Crupier.FOLD_SOUNDS_MOD)
                        : Map.ofEntries(Crupier.FOLD_SOUNDS.get(GameFrame.LANGUAGE)));
                fold_sound_playing = false;
            });
        }
    }

    public void soundShowdown() {
        if (!this.sincronizando_mano && GameFrame.SONIDOS_CHORRA && !fold_sound_playing) {

            if (badbeat) {
                Helpers.threadRun(() -> {
                    Audio.muteAllLoopMp3();
                    Audio.playWavResourceAndWait("misc/badbeat.wav");
                    Audio.unmuteAllLoopMp3();
                });
            } else if (jugada_ganadora >= Hand.POKER && GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {

                Helpers.threadRun(() -> {
                    Audio.muteAllLoopMp3();
                    Audio.playWavResourceAndWait("misc/youarelucky.wav");
                    Audio.unmuteAllLoopMp3();
                });

            } else {
                Audio.playRandomWavResource(Init.MOD != null
                        ? Map.ofEntries(Crupier.SHOWDOWN_SOUNDS_MOD, Crupier.WINNER_SOUNDS_MOD,
                                Crupier.LOSER_SOUNDS_MOD)
                        : Map.ofEntries(Crupier.SHOWDOWN_SOUNDS.get(GameFrame.LANGUAGE),
                                Crupier.WINNER_SOUNDS.get(GameFrame.LANGUAGE),
                                Crupier.LOSER_SOUNDS.get(GameFrame.LANGUAGE)));
            }
        }
    }

    public void soundWinner(int jugada, boolean ultima_carta) {
        if (!this.sincronizando_mano && GameFrame.SONIDOS_CHORRA && !fold_sound_playing) {

            if ((jugada >= Hand.POKER || badbeat) && GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {

                Helpers.threadRun(() -> {
                    Audio.muteAllLoopMp3();
                    Audio.playWavResourceAndWait("misc/youarelucky.wav");
                    Audio.unmuteAllLoopMp3();
                });

            } else {

                Map<String, String[]> sonidos;

                if (ultima_carta && GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {

                    sonidos = Init.MOD != null
                            ? Map.ofEntries(Crupier.WINNER_SOUNDS_MOD,
                                    new HashMap.SimpleEntry<>("misc/", new String[]{"lastcard.wav"}))
                            : Map.ofEntries(Crupier.WINNER_SOUNDS.get(GameFrame.LANGUAGE),
                                    new HashMap.SimpleEntry<>("misc/", new String[]{"lastcard.wav"}));

                } else {

                    sonidos = Init.MOD != null ? Map.ofEntries(Crupier.WINNER_SOUNDS_MOD)
                            : Map.ofEntries(Crupier.WINNER_SOUNDS.get(GameFrame.LANGUAGE));
                }

                Audio.playRandomWavResource(sonidos);
            }
        }
    }

    public void soundLoser(int jugada) {
        if (!this.sincronizando_mano && GameFrame.SONIDOS_CHORRA && !fold_sound_playing) {

            if (badbeat) {
                Helpers.threadRun(() -> {
                    Audio.muteAllLoopMp3();
                    Audio.playWavResourceAndWait("misc/badbeat.wav");
                    Audio.unmuteAllLoopMp3();
                });
            } else if (jugada >= Hand.FULL && GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {

                Map.Entry<String, String[]> WTF_SOUNDS = new HashMap.SimpleEntry<>("joke/es/loser/", new String[]{
                    "encargado.wav",
                    "matias.wav"});

                Audio.playRandomWavResource(Map.ofEntries(WTF_SOUNDS));

            } else {
                Audio.playRandomWavResource(Init.MOD != null ? Map.ofEntries(Crupier.LOSER_SOUNDS_MOD)
                        : Map.ofEntries(Crupier.LOSER_SOUNDS.get(GameFrame.LANGUAGE)));
            }
        }
    }

    public int getStreet() {
        return street;
    }

    public HandPot getBote() {
        return bote;
    }

    public Map<String, Double[]> getAuditor() {
        return auditor;
    }

    // Linea de borde de una tabla con caracteres de caja Unicode (box-drawing). El
    // GameLogDialog pinta esos caracteres atenuados (la rejilla). 'cols' son los
    // anchos de CONTENIDO de cada columna; cada tramo abarca col+2 (un espacio de
    // padding a cada lado dentro de la celda).
    public static String gridBorderLine(char left, char mid, char right, int[] cols) {
        StringBuilder sb = new StringBuilder().append(left);
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) {
                sb.append(mid);
            }
            sb.append("─".repeat(cols[i] + 2));
        }
        return sb.append(right).toString();
    }

    // Fila de tabla con separadores verticales: "│ celda0 │ celda1 │ ... │". Las
    // celdas ya vienen alineadas a su ancho de columna por quien llama.
    public static String gridRowLine(String... cells) {
        StringBuilder sb = new StringBuilder("│");
        for (String c : cells) {
            sb.append(' ').append(c).append(" │");
        }
        return sb.toString();
    }

    public void auditorCuentas() {
        auditorCuentas(true);
    }

    // 'print': si es false, refresca el mapa del auditor (y reconcilia el bote
    // sobrante / detecta descuadres igual que siempre) pero NO vuelca la tabla
    // de cuentas (NICK/STACK/BUYIN) al registro. finTransmision lo llama así:
    // solo necesita el mapa fresco para el snapshot del marcador final
    // (NICK/RESULTADO), que ya es el resumen de cierre — la tabla de stacks debe
    // salir SOLO al arrancar cada mano. Esto evita además que esa tabla, impresa
    // desde el hilo de finTransmision (threadRun), se cuele entre las acciones que
    // el hilo del Crupier sigue logueando (el print del registro es asíncrono y
    // no garantiza orden entre hilos).
    public void auditorCuentas(boolean print) {

        synchronized (this.getLock_contabilidad()) {

            for (Player jugador : GameFrame.getInstance().getJugadores()) {
                this.auditor.put(jugador.getNickname(),
                        new Double[]{jugador.getStack()
                            + (Helpers.doubleSecureCompare(0f, jugador.getPagar()) < 0 ? jugador.getPagar()
                            : jugador.getBote()),
                            (double) jugador.getBuyin()});
            }

            double stack_sum = 0;

            double buyin_sum = 0;

            // Tabla de balance del registro: una fila por jugador (orden de
            // asiento) con un icono pequeno del rol (dealer/ciega) al lado del
            // nick. El rol se captura AQUI, al imprimir, para que el scrollback de
            // manos antiguas conserve los roles correctos (mirarlo al renderizar
            // mostraria los de la mano en curso). Las columnas van alineadas con
            // padding: la fuente del registro es monospace.
            String dealer_nick = this.getDealer_nick();
            String sb_nick = this.getSb_nick();
            String bb_nick = this.getBb_nick();

            ArrayList<String[]> balance_rows = new ArrayList<>();

            int nick_w = 1, stack_w = 1, buyin_w = 1;

            for (Player jugador : GameFrame.getInstance().getJugadores()) {

                String nick = jugador.getNickname();

                Double[] pasta = this.auditor.get(nick);

                if (pasta == null) {
                    continue;
                }

                stack_sum += pasta[0];

                buyin_sum += pasta[1];

                String stack_s = Helpers.money2String(pasta[0]);

                String buyin_s = Helpers.money2String(pasta[1]);

                // Mismo orden de prioridad que el icono del tapete (BB > SB >
                // dealer): en heads-up el dealer es tambien la ciega pequena.
                String role = nick.equals(bb_nick) ? "(BB)"
                        : nick.equals(sb_nick) ? "(SB)"
                        : (this.straddle_posted && nick.equals(dealer_nick) && nick.equals(this.utg_nick)) ? "(DS)"
                        : nick.equals(dealer_nick) ? "(D )"
                        : (this.straddle_posted && nick.equals(this.utg_nick)) ? "(ST)" : "(  )";

                nick_w = Math.max(nick_w, nick.length());
                stack_w = Math.max(stack_w, stack_s.length());
                buyin_w = Math.max(buyin_w, buyin_s.length());

                balance_rows.add(new String[]{role, nick, stack_s, buyin_s});
            }

            // Las cabeceras NICK/STACK/BUYIN se alinean sobre sus columnas: el
            // ancho de cada columna incluye el de su propio rotulo. El token "(##)"
            // hace que el renderer ponga un marcador en blanco (mismo offset que
            // los iconos de rol) para que la cabecera quede alineada con las filas.
            nick_w = Math.max(nick_w, "NICK".length());
            stack_w = Math.max(stack_w, "STACK".length());
            buyin_w = Math.max(buyin_w, "BUYIN".length());

            // Tabla de cuentas con bordes (rejilla). Los iconos de rol van en el
            // margen izquierdo (gutter del marcador), fuera del marco. Columnas
            // NICK / STACK / BUYIN. El borde y la cabecera usan el token "(##)"
            // (marcador en blanco + atenuado); las filas, el token de rol.
            int[] bal_cols = {nick_w, stack_w, buyin_w};

            StringBuilder status = new StringBuilder("(##) ").append(gridBorderLine('┌', '┬', '┐', bal_cols))
                    .append("\n(##) ").append(gridRowLine(
                            String.format("%-" + nick_w + "s", "NICK"),
                            String.format("%" + stack_w + "s", "STACK"),
                            String.format("%" + buyin_w + "s", "BUYIN")))
                    .append("\n(##) ").append(gridBorderLine('├', '┼', '┤', bal_cols));

            for (String[] r : balance_rows) {
                status.append("\n").append(r[0]).append(" ").append(gridRowLine(
                        String.format("%-" + nick_w + "s", r[1]),
                        String.format("%" + stack_w + "s", r[2]),
                        String.format("%" + buyin_w + "s", r[3])));
            }

            // Reconciliacion del bote sobrante ANTES de imprimir los totales:
            // normalmente stack_sum + bote_sobrante == buyin_sum. En una partida
            // recuperada el bote sobrante puede haberse perdido, asi que lo
            // reconstruimos a partir de los totales para que las cuentas cuadren.
            // error_dialog marca el unico caso que ademas abre un dialogo modal
            // (recuperacion que sigue sin cuadrar tras reconstruir), igual que antes.
            boolean error_auditor = false, error_dialog = false;

            if (Helpers.doubleSecureCompare(Helpers.doubleClean(stack_sum) + Helpers.doubleClean(this.bote_sobrante),
                    buyin_sum) != 0) {

                if (this.game_recovered == 1 && Helpers.doubleSecureCompare(0f, this.bote_sobrante) <= 0) {

                    this.game_recovered = 2;

                    // CORREGIMOS EL BOTE SOBRANTE DESAPARECIDO AL RECUPERAR LA PARTIDA
                    this.bote_sobrante = Helpers
                            .doubleClean(Helpers.doubleClean(buyin_sum) - Helpers.doubleClean(stack_sum));

                    if (Helpers.doubleSecureCompare(0f, this.bote_sobrante) <= 0) {

                        this.bote_total = this.bote_sobrante;

                    } else {
                        // No debería llegar aqui nunca (bote sobrante negativo) si no ha habido algún
                        // error jodido (Si ocurriese, ponemos el sobrante a cero aunque el auditor dará
                        // aviso en el registro)
                        this.bote_sobrante = 0f;
                    }

                    if (Helpers.doubleSecureCompare(
                            Helpers.doubleClean(stack_sum) + Helpers.doubleClean(this.bote_sobrante), buyin_sum) != 0) {
                        error_auditor = true;
                        error_dialog = true;
                    }

                } else {
                    error_auditor = true;
                }
            }

            // Pie de la tabla: separador + fila de totales (suma de stacks bajo
            // STACK, suma de buyins bajo BUYIN; el bote sobrante entre parentesis a
            // la DERECHA del marco si lo hay, asi se ve de un vistazo que (stacks +
            // sobrante) == buyins) + borde inferior. Todo en el mismo bloque que la
            // tabla para que lea como su pie. Los totales usan el token "($$)"
            // (icono de fichas). La antigua linea "AUDITOR DE CUENTAS" solo se
            // imprime ya cuando el auditor detecta un descuadre.
            status.append("\n(##) ").append(gridBorderLine('├', '┼', '┤', bal_cols));

            status.append("\n($$) ").append(gridRowLine(
                    " ".repeat(nick_w),
                    String.format("%" + stack_w + "s", Helpers.money2String(stack_sum)),
                    String.format("%" + buyin_w + "s", Helpers.money2String(buyin_sum))));

            if (Helpers.doubleSecureCompare(0f, this.bote_sobrante) < 0) {
                status.append(" (").append(Helpers.money2String(this.bote_sobrante)).append(")");
            }

            status.append("\n(##) ").append(gridBorderLine('└', '┴', '┘', bal_cols));

            if (print) {
                GameFrame.getInstance().getRegistro().print(status.toString());

                // Indicador de antes activos: el ante es simetrico (todos antean), no es
                // un rol por-nick como el straddle, asi que va en una linea propia con el
                // icono de fichas (token "(A )").
                if (GameFrame.ANTE) {
                    GameFrame.getInstance().getRegistro().print("(A ) " + Translator.translate("game.antes_activos", Helpers.money2String(this.ciega_pequeña)));
                }
            }

            if (error_auditor) {

                GameFrame.getInstance().getRegistro()
                        .print("($$) " + Translator.translate("ui.auditor_de_cuentas") + " -> STACKS: "
                                + Helpers.money2String(stack_sum) + " / BUYIN: " + Helpers.money2String(buyin_sum)
                                + " " + Translator.translate("ui.sobrante") + " " + Helpers.money2String(this.bote_sobrante));

                if (error_dialog) {
                    Helpers.mostrarMensajeError(GameFrame.getInstance(),
                            Translator.translate("ui.ojo_a_esto_no_salen"));
                }

                GameFrame.getInstance().getRegistro()
                        .print(Translator.translate("ui.ojo_a_esto_no_salen"));
            }
        }
    }

    // Arranca el visual de cuenta atrás de game over (GIF sobre las cartas en
    // modo CINEMATICAS, o cuenta atrás numérica en la action label) de los
    // arruinados que SON humanos remotos. Idempotente (setRebuying ignora si ya
    // está activo o si el jugador salió/es espectador), así que es seguro
    // llamarlo dos veces: una pronto (a la vez que el game over local) y otra
    // dentro de recibirRebuys para el caso en que el local no esté arruinado.
    private void startRebuyingVisuals(ArrayList<String> nicks) {
        for (String nick : nicks) {
            Player jugador = nick2player.get(nick);
            Participant participante = GameFrame.getInstance().getParticipantes().get(nick);
            if (jugador instanceof RemotePlayer && !jugador.isExit()
                    && participante != null && !participante.isCpu()) {
                ((RemotePlayer) jugador).setRebuying(true);
            }
        }
    }

    private void recibirRebuys(ArrayList<String> pending, boolean skip_countdown) {

        // Barra de tiempo según el modo local (mismo snapshot de CINEMATICAS
        // que decide el visual de los arruinados en setRebuying):
        // - CINEMATICAS ON: el GIF de game over sobre las cartas YA es la
        //   cuenta atrás → barra indeterminada desde el principio.
        // - CINEMATICAS OFF: la barra se llena y baja en smooth los segundos
        //   de decisión del game over (los mismos que marca la cuenta atrás
        //   "¿RECOMPRA? (N)" de la action label) y al agotarse pasa a
        //   indeterminada (en el bucle de abajo) hasta que lleguen los REBUY
        //   o salten los timeouts de seguridad del crupier.
        final boolean barra_smooth = !GameFrame.cinematicasOn();
        if (barra_smooth) {
            Helpers.smoothCountdown(GameFrame.getInstance().getBarra_tiempo(), GameOverDialog.REBUY_DIALOG_COUNTDOWN);
        } else {
            Helpers.barraIndeterminada(GameFrame.getInstance().getBarra_tiempo());
        }

        // Visual "¿RECOMPRA? (N)": cuenta atrás LOCAL en la action label de los
        // humanos arruinados mientras deciden en su máquina (sin sincronía con
        // su GameOverDialog real — cosmético). Bots fuera: en el host ni
        // entran en pending y en los clientes su REBUY llega al instante.
        // skip_countdown: el LOCAL también se arruinó, así que este recibirRebuys
        // corre DESPUÉS de su game-over modal y una cuenta atrás remota saldría
        // desincronizada → no se lanza; abajo solo se refleja el desenlace (RECOMPRA).
        if (!skip_countdown) {
            startRebuyingVisuals(pending);
        }

        long start_time = System.currentTimeMillis();
        long barra_start = start_time;
        boolean barra_indeterminada = false;
        boolean timeout = false;

        while (!pending.isEmpty() && !timeout) {

            synchronized (this.getReceived_commands()) {
                ArrayList<String> rejected = new ArrayList<>();
                while (!this.getReceived_commands().isEmpty()) {
                    String comando = this.received_commands.poll();
                    try {
                        String[] partes = comando.split("#");
                        if (partes.length < 3) {
                            LOGGER.log(Level.WARNING, "Malformed command dropped (REBUY wait): {0}", comando);
                            continue;
                        }

                        if (partes[2].equals("REBUY") && partes.length >= 4) {
                            String nick = null;
                            try {
                                nick = new String(Base64.getDecoder().decode(partes[3]), "UTF-8");
                            } catch (UnsupportedEncodingException ex) {
                                LOGGER.log(Level.WARNING, "Badly-encoded nick in REBUY", ex);
                                continue;
                            }
                            pending.remove(nick);
                            Player jugador = nick2player.get(nick);
                            if (jugador == null) {
                                LOGGER.log(Level.WARNING, "REBUY from unknown nick: {0}", nick);
                                continue;
                            }
                            jugador.setTimeout(false);
                            // Decisión recibida: retira la cuenta atrás/GIF y pinta
                            // el desenlace — "¡RECOMPRA!" si recompró (con un solo
                            // arruinado la espera acaba al instante y sin esto no
                            // daría tiempo a ver qué pasó); si no, restaura y el
                            // setSpectator de abajo repinta encima.
                            boolean recompra = (partes.length > 4)
                                    ? (!partes[4].equals("0") && !atRebuyLimit(nick))
                                    : !atRebuyLimit(nick);
                            if (jugador instanceof RemotePlayer) {
                                if (skip_countdown) {
                                    // No se lanzó cuenta atrás remota (local también
                                    // arruinado): solo reflejamos el desenlace.
                                    ((RemotePlayer) jugador).showRebuyOutcome(recompra);
                                } else {
                                    ((RemotePlayer) jugador).setRebuying(false, recompra);
                                }
                            }

                            if (GameFrame.getInstance().isPartida_local()) {
                                broadcastGAMECommandFromServer("REBUY#" + partes[3] + (partes.length > 4 ? "#" + partes[4] : ""), nick);
                            }

                            if (partes.length > 4) {
                                if (partes[4].equals("0")) {
                                    // Pulsó ESPECTADOR en su game over: feedback
                                    // explícito en el visual de espectador.
                                    jugador.setSpectator(Translator.translate("rebuy.no_recompra"));
                                } else if (atRebuyLimit(nick)) {
                                    jugador.setSpectator(null);
                                } else {
                                    // Mismo blindaje que rebuyNow: el host acota el importe
                                    // al headroom (techo de mesa - stack) para que un cliente
                                    // manipulado no fabrique fichas ni supere el techo via
                                    // REBUY#...#<entero arbitrario>.
                                    int raw_rebuy = Integer.parseInt(partes[4]);
                                    int headroom = GameFrame.rebuyHeadroom(jugador.getStack());
                                    int safe_rebuy = Math.min(raw_rebuy, headroom);
                                    if (safe_rebuy != raw_rebuy) {
                                        LOGGER.log(Level.WARNING, "Rebuy amount {0} from {1} exceeds headroom {2} — clamped to {3}",
                                                new Object[]{raw_rebuy, nick, headroom, safe_rebuy});
                                    }
                                    rebuy_now.put(nick, safe_rebuy);
                                }
                            } else if (atRebuyLimit(nick)) {
                                jugador.setSpectator(null);
                            } else {
                                rebuy_now.put(nick, GameFrame.rebuyHeadroom(jugador.getStack()));
                            }
                        } else {
                            rejected.add(comando);
                        }
                    } catch (Exception ex) {
                        LOGGER.log(Level.WARNING, "Exception while processing command in REBUY wait: " + comando, ex);
                    }
                }
                if (!rejected.isEmpty()) {
                    this.getReceived_commands().addAll(rejected);
                    rejected.clear();
                }
            }

            if (!pending.isEmpty()) {
                // Solo en modo barra smooth (CINEMATICAS off): decisión agotada
                // (los segundos del smoothCountdown de arriba) → barra a
                // indeterminada hasta que lleguen los REBUY que faltan o salte
                // el timeout de seguridad. Si llegan antes, el bucle sale solo
                // y el resetBarra final cancela el smooth — la mano siguiente
                // arranca sin esperar a que la barra termine.
                if (barra_smooth && !barra_indeterminada
                        && System.currentTimeMillis() - barra_start > GameOverDialog.REBUY_DIALOG_COUNTDOWN * 1000L) {
                    barra_indeterminada = true;
                    Helpers.barraIndeterminada(GameFrame.getInstance().getBarra_tiempo());
                }

                Iterator<String> iterator = pending.iterator();
                while (iterator.hasNext()) {
                    String nick = iterator.next();
                    Player jp = nick2player.get(nick);
                    if (jp != null && jp.isExit()) {
                        // Se fue en pleno rebuy (cierre/desconexión): fuera de
                        // la espera y fuera la cuenta atrás visual (el guard de
                        // exit en setRebuying no toca el visual de SE PIRA).
                        if (jp instanceof RemotePlayer) {
                            ((RemotePlayer) jp).setRebuying(false);
                        }
                        iterator.remove();
                    }
                }

                if (GameFrame.getInstance().checkPause()) {
                    start_time = System.currentTimeMillis();
                    // La barra smooth se congela durante la pausa (deadline
                    // empujado en smoothCountdown): empujamos también el
                    // instante del flip a indeterminada para no cortarla.
                    barra_start = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - start_time > 2 * GameFrame.REBUY_TIMEOUT) {
                    if (GameFrame.getInstance().isPartida_local()) {
                        // Jugador no respondió al rebuy en el tiempo esperado:
                        // se asume "no rebuy" (= spectator), MISMO comportamiento
                        // que si hubiera contestado "0" expresamente. Antes este
                        // path llamaba remotePlayerQuit y los expulsaba de la
                        // mesa, lo cual es un kick injustificado: el jugador
                        // sigue en la partida como spectator y volverá a jugar
                        // si recarga manualmente en la siguiente mano.
                        LOGGER.log(Level.INFO, "REBUY timeout — pending players default to spectator (no kick)");
                        for (String nick : pending) {
                            Player jpk = nick2player.get(nick);
                            if (jpk != null && !jpk.isExit()) {
                                jpk.setSpectator(null);
                                jpk.setTimeout(false);
                            }
                            // Para la cuenta atrás visual; con spectator ya
                            // puesto, el restore se omite y manda el repaint
                            // de setSpectator.
                            if (jpk instanceof RemotePlayer) {
                                ((RemotePlayer) jpk).setRebuying(false);
                            }
                        }
                        timeout = true;
                    } else {
                        start_time = System.currentTimeMillis();
                    }
                } else {
                    synchronized (this.getReceived_commands()) {
                        try {
                            this.getReceived_commands().wait(WAIT_QUEUES);
                        } catch (InterruptedException ex) {
                        }
                    }
                }
            }

        }

        Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), GameFrame.THINK_TIME);
    }

    // Fija el buy-in INICIAL de un jugador (modo variable). NO es una recompra:
    // setea stack y buyin directamente (no usa reComprar -> sin CYAN, sin contar
    // recompra, sin tocar rebuy_now). El auditor se siembra luego en
    // auditorCuentas() al arrancar la mano 1 desde el getBuyin() ya fijado aqui.
    private void aplicarBuyinInicial(String nick, int amount) {
        Player jugador = nick2player.get(nick);
        if (jugador == null) {
            LOGGER.log(Level.WARNING, "Initial buy-in for unknown nick: {0}", nick);
            return;
        }
        int safe = Math.max(GameFrame.getBuyinMin(), Math.min(amount, GameFrame.getBuyinMax()));
        jugador.setStack(safe);
        jugador.setBuyin(safe);
    }

    // Barrera de arranque en modo buy-in variable: al entrar al tablero (luces
    // apagadas, antes de las animaciones de la mano 1) cada humano elige su
    // buy-in en el rango configurado [getBuyinMin, getBuyinMax] (default
    // getBuyinDefault) con la misma cuenta atras que la recompra. Reutiliza el
    // mensaje GAME y la mecanica de recibirRebuys. NO se
    // ejecuta en modo fijo (camino antiguo directo) ni en recover (los stacks
    // vienen de balance).
    private void solicitarBuyinsIniciales() {

        if (GameFrame.FIXED_BUYIN || GameFrame.RECOVER) {
            return;
        }

        final LocalPlayer local = GameFrame.getInstance().getLocalPlayer();
        final float old_brightness = GameFrame.getInstance().getCapa_brillo().getBrightness();

        Helpers.GUIRunAndWait(() -> {
            if (old_brightness != BrightnessLayerUI.LIGHTS_OFF_BRIGHTNESS) {
                GameFrame.getInstance().getCapa_brillo().setBrightness(BrightnessLayerUI.LIGHTS_OFF_BRIGHTNESS);
                GameFrame.getInstance().getTapete().repaint();
            }
        });

        final RebuyDialog[] dlg = new RebuyDialog[1];
        Helpers.GUIRunAndWait(() -> {
            dlg[0] = new RebuyDialog(GameFrame.getInstance(), true, false,
                    GameOverDialog.REBUY_DIALOG_COUNTDOWN,
                    GameFrame.getBuyinMin(), GameFrame.getBuyinMax(), GameFrame.getBuyinDefault(),
                    "rebuy.compra_inicial");
            dlg[0].setDeferClose(true);
            dlg[0].setLocationRelativeTo(dlg[0].getParent());
        });

        // Mostrar SIN bloquear este hilo: el crupier debe seguir para recolectar al
        // resto. El dialogo es modal y, al aceptar (defer_close), pasa a "esperando
        // a los demas jugadores" en vez de cerrarse; lo cierra el crupier al
        // terminar la recoleccion.
        Helpers.GUIRun(() -> dlg[0].setVisible(true));

        // Espera la eleccion local (OK o auto-aceptar a los 15s).
        while (!dlg[0].isRebuy() && !fin_de_la_transmision) {
            Helpers.pausar(GameFrame.WAIT_QUEUES);
        }

        // El spinner ya acota al rango configurado; el clamp es defensivo.
        int chosen = Math.max(GameFrame.getBuyinMin(),
                Math.min((int) dlg[0].getRebuy_spinner().getValue(), GameFrame.getBuyinMax()));

        ArrayList<String> pending = new ArrayList<>();

        try {
            // Cada peer auto-aplica su propio buy-in y NO se mete en su pending
            // (igual que el arruinado local en el game-over): asi el host puede
            // rebroadcast con skip=remitente sin que nadie espere su propio eco.
            aplicarBuyinInicial(local.getNickname(), chosen);
            String localCmd = "BUYIN#"
                    + Base64.getEncoder().encodeToString(local.getNickname().getBytes("UTF-8"))
                    + "#" + chosen;

            if (GameFrame.getInstance().isPartida_local()) {
                broadcastGAMECommandFromServer(localCmd, local.getNickname());
                for (Player jugador : GameFrame.getInstance().getJugadores()) {
                    if (jugador == local || jugador.isExit()) {
                        continue;
                    }
                    Participant p = GameFrame.getInstance().getParticipantes().get(jugador.getNickname());
                    if (p != null && p.isCpu()) {
                        int botbuy = GameFrame.getBuyinDefault();
                        aplicarBuyinInicial(jugador.getNickname(), botbuy);
                        broadcastGAMECommandFromServer("BUYIN#"
                                + Base64.getEncoder().encodeToString(jugador.getNickname().getBytes("UTF-8"))
                                + "#" + botbuy, jugador.getNickname());
                    } else {
                        pending.add(jugador.getNickname());
                    }
                }
            } else {
                sendGAMECommandToServer(localCmd);
                for (Player jugador : GameFrame.getInstance().getJugadores()) {
                    if (jugador != local && !jugador.isExit()) {
                        pending.add(jugador.getNickname());
                    }
                }
            }
        } catch (UnsupportedEncodingException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }

        recibirBuyinsIniciales(pending);

        Helpers.GUIRunAndWait(() -> {
            // Cierra el dialogo de "esperando a los demas" (ya estan todos).
            dlg[0].dispose();
            if (old_brightness != BrightnessLayerUI.LIGHTS_OFF_BRIGHTNESS) {
                GameFrame.getInstance().getCapa_brillo().setBrightness(old_brightness);
                GameFrame.getInstance().getTapete().repaint();
            }
            // Re-sincroniza el icono de luces con el brillo restaurado (igual que el
            // dialogo de recover, Crupier.java): este dialogo corre durante el
            // montaje de la mesa, donde un render puede dejar el icono en "off"
            // mientras las luces vuelven a "on" -> icono bloqueado/desincronizado.
            GameFrame.getInstance().getTapete().getCommunityCards().refreshLightsIcon();
        });
    }

    // Espera (host) los BUYIN de los clientes humanos o (cliente) los broadcasts
    // del host, aplicandolos a medida que llegan. Mismo esqueleto que
    // recibirRebuys: drena received_commands, reencola lo no-BUYIN, y el host
    // tiene la misma ventana amplia (2*REBUY_TIMEOUT) para absorber desync; al
    // expirar los pendientes (host) caen al default 50BB. El cliente no fuerza
    // timeout: sigue al host hasta recibir todos los broadcasts.
    private void recibirBuyinsIniciales(ArrayList<String> pending) {

        Helpers.barraIndeterminada(GameFrame.getInstance().getBarra_tiempo());

        long start_time = System.currentTimeMillis();
        boolean timeout = false;

        while (!pending.isEmpty() && !timeout) {

            synchronized (this.getReceived_commands()) {
                ArrayList<String> rejected = new ArrayList<>();
                while (!this.getReceived_commands().isEmpty()) {
                    String comando = this.received_commands.poll();
                    try {
                        String[] partes = comando.split("#");
                        if (partes.length >= 5 && partes[2].equals("BUYIN")) {
                            String nick;
                            try {
                                nick = new String(Base64.getDecoder().decode(partes[3]), "UTF-8");
                            } catch (UnsupportedEncodingException ex) {
                                LOGGER.log(Level.WARNING, "Badly-encoded nick in BUYIN", ex);
                                continue;
                            }
                            int raw_buyin = Integer.parseInt(partes[4]);
                            int safe_buyin = Math.max(GameFrame.getBuyinMin(), Math.min(raw_buyin, GameFrame.getBuyinMax()));
                            if (safe_buyin != raw_buyin) {
                                LOGGER.log(Level.WARNING, "Initial buy-in {0} from {1} out of range [{2},{3}] — clamped to {4}",
                                        new Object[]{raw_buyin, nick, GameFrame.getBuyinMin(), GameFrame.getBuyinMax(), safe_buyin});
                            }
                            aplicarBuyinInicial(nick, safe_buyin);
                            if (GameFrame.getInstance().isPartida_local()) {
                                broadcastGAMECommandFromServer("BUYIN#" + partes[3] + "#" + safe_buyin, nick);
                            }
                            pending.remove(nick);
                        } else {
                            rejected.add(comando);
                        }
                    } catch (Exception ex) {
                        LOGGER.log(Level.WARNING, "Exception while processing command in BUYIN wait: " + comando, ex);
                    }
                }
                if (!rejected.isEmpty()) {
                    this.getReceived_commands().addAll(rejected);
                    rejected.clear();
                }
            }

            if (!pending.isEmpty()) {

                Iterator<String> iterator = pending.iterator();
                while (iterator.hasNext()) {
                    String nick = iterator.next();
                    Player jp = nick2player.get(nick);
                    if (jp != null && jp.isExit()) {
                        iterator.remove();
                    }
                }

                if (GameFrame.getInstance().checkPause()) {
                    start_time = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - start_time > 2 * GameFrame.REBUY_TIMEOUT) {
                    if (GameFrame.getInstance().isPartida_local()) {
                        LOGGER.log(Level.INFO, "Initial buy-in timeout — pending players default to {0}", GameFrame.getBuyinDefault());
                        for (String nick : pending) {
                            Player jp = nick2player.get(nick);
                            if (jp != null && !jp.isExit()) {
                                int def = GameFrame.getBuyinDefault();
                                aplicarBuyinInicial(nick, def);
                                try {
                                    broadcastGAMECommandFromServer("BUYIN#"
                                            + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8"))
                                            + "#" + def, null);
                                } catch (UnsupportedEncodingException ex) {
                                    LOGGER.log(Level.SEVERE, null, ex);
                                }
                            }
                        }
                        timeout = true;
                    } else {
                        start_time = System.currentTimeMillis();
                    }
                } else {
                    synchronized (this.getReceived_commands()) {
                        try {
                            this.getReceived_commands().wait(WAIT_QUEUES);
                        } catch (InterruptedException ex) {
                        }
                    }
                }
            }
        }

        Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), GameFrame.THINK_TIME);
    }

    public synchronized void remotePlayerQuit(String nick, String testamento) {
        Player jugador = nick2player.get(nick);
        if (jugador != null && !jugador.isExit()) {
            jugador.setExit();
            if (GameFrame.getInstance().isPartida_local()) {
                Participant participante = GameFrame.getInstance().getParticipantes().get(nick);
                if (participante != null) {
                    participante.exitAndCloseSocket();
                }
                try {
                    String cmd = "EXIT#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8"));
                    // V60: Propagamos el testamento en bloque (si existe)
                    if (testamento != null && !testamento.isEmpty() && !testamento.equals("*")) {
                        cmd += "#" + testamento;
                    }
                    // EXIT goes out NOW. The previous "defer until autofold ACTION
                    // is broadcast" pattern existed to keep wire order ACTION→EXIT
                    // so receivers could absorb a host-signed autofold record into
                    // their chain before marking the peer as out. That whole
                    // mechanism is gone: a departed peer contributes no record to
                    // the slot it would have played, every receiver synths the
                    // local FOLD on EXIT, and the chain converges by mutual
                    // omission. So no buffering, no flush — just send.
                    broadcastGAMECommandFromServer(cmd, nick);
                } catch (UnsupportedEncodingException ex) {
                }

                if (this.isFin_de_la_transmision() || !WaitingRoomFrame.getInstance().isPartida_empezada()) {
                    if (participante != null && participante.isCpu()) {
                        GameFrame.getInstance().getSala_espera().borrarParticipante(nick);
                    }
                }
            } else {
                // Consensus: on the client side, the Participant
                // for the exiting peer is a shell with no socket, so exitAndCloseSocket
                // is host-only. But computeExpectedConsensusSigners checks
                // Participant.isExit() — without flipping the flag here, the client
                // keeps expecting a receipt from a peer it knows has left, hits the
                // CLIENT_RECEPTION_TIMEOUT, and surfaces MISSING + popup at hand
                // close even though the peer's exit was clean.
                Participant participante = GameFrame.getInstance().getParticipantes().get(nick);
                if (participante != null) {
                    participante.setExit(true);
                }
                if (this.isFin_de_la_transmision() || !WaitingRoomFrame.getInstance().isPartida_empezada()) {
                    GameFrame.getInstance().getSala_espera().borrarParticipante(nick);
                }
            }
            synchronized (this.getReceived_commands()) {
                this.getReceived_commands().notifyAll();
            }
            synchronized (WaitingRoomFrame.getInstance().getReceived_confirmations()) {
                WaitingRoomFrame.getInstance().getReceived_confirmations().notifyAll();
            }
        }
    }

    // Sobrecarga de compatibilidad
    public synchronized void remotePlayerQuit(String nick) {
        remotePlayerQuit(nick, null);
    }

    public Object getLock_apuestas() {
        return lock_apuestas;
    }

    public ConcurrentLinkedQueue<String> getReceived_commands() {
        return received_commands;
    }

    public double getApuesta_actual() {
        return apuesta_actual;
    }

    public int getCiegas_double() {
        return ciegas_double;
    }

    public int getMano() {
        return conta_mano;
    }

    public void actualizarContadoresTapete() {

        // Con fichas voladoras en vuelo el valor del bote se difiere a su aterrizaje
        // (lo refresca el onLand de la ficha, a la vez que el parpadeo). El resto de
        // contadores (apuesta de calle, ciegas, mano) sí se actualizan ya.
        if (!isPotLabelValueDeferred()) {
            refreshTapeteBoteValue();
        }

        if (this.destapar_resistencia || this.show_time) {
            // Run-out all-in (normal o run-it-twice) o SHOWDOWN: ya no hay apuestas.
            // Se oculta la bet_label de calle y el bote se centra ocupando todo el
            // ancho — exactamente el estado al que llega el showdown, que solo le
            // añade el fondo verde. Así la label no salta de sitio al cerrarse la
            // mano. El gate por show_time evita además que un refresco tardío
            // (editar ciegas, aterrizaje de una ficha, etc.) vuelva a mostrar la
            // bet_label una vez empezado el showdown.
            GameFrame.getInstance().hideTapeteApuestas();
            Helpers.GUIRun(() -> GameFrame.getInstance().getTapete().getCommunityCards()
                    .getPot_label().setHorizontalAlignment(JLabel.CENTER));
        } else {
            GameFrame.getInstance().setTapeteApuestas(this.apuestas);
        }

        GameFrame.getInstance().setTapeteCiegas(this.ciega_pequeña, this.ciega_grande);
        GameFrame.getInstance().setTapeteMano(this.conta_mano);

        refreshCallCostOverlay();
    }

    // Overlay opcional sobre las comunitarias: cuánto tendría que poner el jugador
    // local para IGUALAR la apuesta actual (lo que le tocará cuando le llegue el
    // turno). Se actualiza tras cada acción, así sube en vivo cuando alguien sube.
    // Solo se muestra en fase de apuestas, con el local activo y algo por igualar;
    // en otro caso se oculta. El importe se trunca al stack (no puedes poner más de
    // lo que tienes). Respeta el toggle de Apariencia.
    public void refreshCallCostOverlay() {
        TablePanel tapete = GameFrame.getInstance().getTapete();
        if (tapete == null) {
            return;
        }

        LocalPlayer lp = GameFrame.getInstance().getLocalPlayer();

        if (!GameFrame.MOSTRAR_COSTE_IGUALAR || !this.community_cards_dealt
                || this.show_time || this.destapar_resistencia
                || lp == null || !lp.isActivo() || lp.isExit()
                || lp.getDecision() == Player.FOLD || lp.getDecision() == Player.ALLIN) {
            tapete.hideCallCostOverlay();
            return;
        }

        double cost = Helpers.doubleClean(this.apuesta_actual - lp.getBet());

        if (Helpers.doubleSecureCompare(0f, cost) >= 0) {
            tapete.hideCallCostOverlay();
            return;
        }

        double shown = Math.min(cost, Helpers.doubleClean(lp.getStack()));
        tapete.updateCallCostOverlay("+" + Helpers.money2String(shown));
    }

    // Refresca SOLO el valor del pot_label (sin tocar bet_label/ciegas/mano). Lo usa
    // el aterrizaje de la ficha voladora para aplicar el valor del bote diferido sin
    // re-mostrar el bet_label (que el showdown pudo ocultar). RIT: durante el run-out
    // de cada cara muestra la MITAD que ESA cara juega; fuera de RIT, el total.
    public void refreshTapeteBoteValue() {
        double pot_show = this.rit_pot_board_tag != null
                ? splitPotForRunItTwice(this.bote_total)[0]
                : this.bote_total;
        GameFrame.getInstance().setTapeteBote(pot_show, this.beneficio_bote_principal);
    }

    private void resetBetPlayerDecisions(ArrayList<Player> jugadores, String nick, boolean partial_raise) {

        if (nick == null) {
            this.last_aggressor = null;
        } else {
            this.last_aggressor = nick2player.get(nick);
        }

        for (Player jugador : jugadores) {

            if (jugador.isActivo() && jugador.getDecision() != Player.FOLD && jugador.getDecision() != Player.ALLIN
                    && (nick == null || partial_raise || !jugador.getNickname().equals(nick))) {
                jugador.resetBetDecision();
            }
        }
    }

    public Object getLock_mostrar() {
        return lock_mostrar;
    }

    public void showAndBroadcastPlayerCards(String nick) {

        Player jugador;
        boolean isLocal;

        synchronized (lock_mostrar) {
            // Nota: ya no gateamos con show_time. Si el comando proviene del flujo IWTSTH, la temporización
            // del wait/pausaConBarra puede haber cerrado show_time antes de que llegue el SHOWCARDS de un
            // candidato remoto. La función es idempotente (chequea isTapada() abajo) y los callers
            // ya verifican show_time donde corresponde para el flujo voluntario (LocalPlayer.player_allin_buttonActionPerformed).
            jugador = nick2player.get(nick);
            if (jugador == null) {
                return;
            }
            isLocal = jugador.equals(GameFrame.getInstance().getLocalPlayer());

            // Solo desciframos si es remoto y faltan los valores
            if (!isLocal && (jugador.getHoleCard1().getValor() == null || jugador.getHoleCard1().getValor().isEmpty())) {
                // ZERO-TRUST: Si el jugador ha enviado su testamento (sra_unlock), desciframos su mano
                Participant p = GameFrame.getInstance().getParticipantes().get(jugador.getNickname());
                if (p != null && p.getSra_unlock() != null && p.getSra_unlock().length == 32) {
                    unlockPlayerCardsWithSRAKey(jugador);
                    jugador.ordenarCartas();
                }
            }

            // ZERO-TRUST SRA: Enviamos la clave de desbloqueo en vez de las cartas en texto plano.
            // Cada receptor descifra localmente desde su propia copia del mega_packet.
            //
            // Dual-lock: SHOWCARDS revela cartas PRIVADAS, así que la clave que viaja
            // es la mitad POCKET (no la community que entrega EXIT). El receptor la guarda en
            // Participant.sra_unlock y unlockPlayerCardsWithSRAKey la aplica sobre el pocket
            // piece almacenado en single_locked_pocket_cards.
            //
            // Sig Ed25519 sobre (HAND_ID || nick || pocketKey): el host no puede substituir
            // la clave porque no tiene la privkey del nick (humano = suya, bot = host).
            try {
                String sraKeyB64 = getShowdownPocketKey(nick);
                String sigB64 = signShowdownRevealForBroadcast(nick, sraKeyB64);
                boolean canSend = !"*".equals(sraKeyB64) && !"*".equals(sigB64);
                if (canSend) {
                    String comando = "SHOWCARDS#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")) + "#" + sraKeyB64 + "#" + sigB64;
                    if (GameFrame.getInstance().isPartida_local()) {
                        broadcastGAMECommandFromServer(comando, nick);
                    } else if (isLocal) {
                        if (Crupier.SECURITY_LOCKDOWN) {
                            LOGGER.log(Level.SEVERE, "ZERO-TRUST: SHOWCARDS suppressed — security lockdown active");
                        } else {
                            sendGAMECommandToServer(comando, true);
                        }
                    }
                } else {
                    LOGGER.log(Level.WARNING,
                            "showAndBroadcastPlayerCards: cannot send SHOWCARDS for {0} — key=\"{1}\", sig=\"{2}\". Cards stay local-only.",
                            new Object[]{nick, sraKeyB64, sigB64});
                }
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Error sending SHOWCARDS for " + nick, ex);
            }
        }

        // Destape + etiqueta FUERA de lock_mostrar: el giro animado bloquea
        // ~1s a este worker y el lock lo comparten el procesador de comandos
        // (showPlayerCards) y el cierre de show_time del crupier — retenerlo
        // durante la animación los atascaría. La idempotencia frente a
        // destapes concurrentes del mismo jugador la da el
        // destape_animado_lock dentro del método animado.
        if (jugador.getHoleCard1().isTapada()) {
            // Bloquea hasta el fin del giro (los callers son workers): la
            // etiqueta de jugada de abajo no aparece hasta destapar del todo.
            mostrarAnimacionDestaparCartasJugador(jugador, true);

            // CLONACIÓN DEFENSIVA: Pasamos una copia a la clase Hand para que no desordene la UI
            ArrayList<Card> evalList = new ArrayList<>();
            evalList.addAll(jugador.getHoleCards());
            for (Card c : GameFrame.getInstance().getCartas_comunes()) {
                if (!c.isTapada()) {
                    evalList.add(c);
                }
            }

            try {
                Hand jugada = new Hand(evalList);
                jugador.showCards(jugada.getName());
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error evaluating Hand while showing cards of " + nick, e);
            }

            setTiempo_pausa(GameFrame.TEST_MODE ? PAUSA_ENTRE_MANOS_TEST : PAUSA_ENTRE_MANOS);
        } else if (isLocal) {
            setTiempo_pausa(GameFrame.TEST_MODE ? PAUSA_ENTRE_MANOS_TEST : PAUSA_ENTRE_MANOS);
        }
    }

    public ConcurrentHashMap<Player, Hand> getPerdedores() {
        return perdedores;
    }

    public boolean isShow_time() {
        return show_time;
    }

    public boolean isFlop_revealed() {
        return flop_revealed;
    }

    public boolean isTurn_revealed() {
        return turn_revealed;
    }

    public boolean isRiver_revealed() {
        return river_revealed;
    }

    public void setFlop_revealed(boolean v) {
        this.flop_revealed = v;
    }

    public void setTurn_revealed(boolean v) {
        this.turn_revealed = v;
    }

    public void setRiver_revealed(boolean v) {
        this.river_revealed = v;
    }

    // Phase enum para REQ_SRA_UNLOCK_CHAIN. Cada item del batch lleva un
    // (phase, peer_idx); el cliente valida que (phase, peer_idx) encaja con
    // su estado local y aún no se ha servido en esta mano (anti-reuse, ver
    // isUnlockPhaseAllowedForStreet + communitySlotRange, el gate vivo del batch).
    public static final int UNLOCK_PHASE_POCKET = 0;
    public static final int UNLOCK_PHASE_FLOP = 1;
    public static final int UNLOCK_PHASE_TURN = 2;
    public static final int UNLOCK_PHASE_RIVER = 3;
    public static final int UNLOCK_PHASE_RABBIT_FLOP = 4;
    public static final int UNLOCK_PHASE_RABBIT_TURN = 5;
    public static final int UNLOCK_PHASE_RABBIT_RIVER = 6;
    // Run-it-twice SIDE-B: fases dedicadas para repartir el segundo board desde
    // offsets frescos del MEGAPACKET. Tags propias (disjuntas de las de SIDE-A)
    // para no chocar con el single-serve del reparto vivo.
    public static final int UNLOCK_PHASE_RIT2_FLOP = 7;
    public static final int UNLOCK_PHASE_RIT2_TURN = 8;
    public static final int UNLOCK_PHASE_RIT2_RIVER = 9;

    // ANTI "leer el board futuro": el host controla offsetBase en REQ_SRA_UNLOCK_CHAIN, así que un
    // cliente que va a ayudar a revelar community DEBE exigir que el slot REALMENTE pelado caiga en los
    // slots que ESA fase puede tocar (derivados LOCAL del layout + ring.length, NUNCA fiados del host).
    // Sin esto el host pide, durante el flop, los slots de turn/river y lee el board antes de tiempo
    // (GATE-6 no lo pilla: el host se quita su lock el último, en local). Layout IDÉNTICO al de
    // enviarCartasComunitarias / enviarRabbitComunitarias / SIDE-B: pocketCount=2N; FLOP off=2N+1 (3
    // cartas), TURN 2N+5, RIVER 2N+7; RIT2 = +RIT2_BOARD_SPAN. Devuelve {primerSlot, numCartas} o null si
    // la fase NO es community (POCKET: espacio de escalar disjunto + self-strip ya lo cubren).
    public static int[] communitySlotRange(int phase, int numPlayers) {
        int streetKind; // 0=flop, 1=turn, 2=river
        boolean rit2;
        switch (phase) {
            case UNLOCK_PHASE_FLOP:
            case UNLOCK_PHASE_RABBIT_FLOP:
                streetKind = 0;
                rit2 = false;
                break;
            case UNLOCK_PHASE_TURN:
            case UNLOCK_PHASE_RABBIT_TURN:
                streetKind = 1;
                rit2 = false;
                break;
            case UNLOCK_PHASE_RIVER:
            case UNLOCK_PHASE_RABBIT_RIVER:
                streetKind = 2;
                rit2 = false;
                break;
            case UNLOCK_PHASE_RIT2_FLOP:
                streetKind = 0;
                rit2 = true;
                break;
            case UNLOCK_PHASE_RIT2_TURN:
                streetKind = 1;
                rit2 = true;
                break;
            case UNLOCK_PHASE_RIT2_RIVER:
                streetKind = 2;
                rit2 = true;
                break;
            default:
                return null; // POCKET u otra: sin binding community aquí
        }
        int offset = numPlayers * 2 + (rit2 ? RIT2_BOARD_SPAN : 0);
        int numCards;
        if (streetKind == 0) {
            offset += 1;                 // flop: burn + 3 cartas
            numCards = 3;
        } else if (streetKind == 1) {
            offset += 1 + 3 + 1;         // turn: burn + 3 + burn + 1
            numCards = 1;
        } else {
            offset += 1 + 3 + 1 + 1 + 1; // river: burn + 3 + burn + turn + burn + 1
            numCards = 1;
        }
        return new int[]{offset, numCards};
    }

    // Tags ya servidas esta mano (clave compuesta phase:peer_idx para todas
    // las phases en v3 — comunitaria también es per-recipient). Bloquea que
    // el host pida el mismo (phase, peer_idx) dos veces engañando con bytes
    // distintos.
    private final Set<String> sra_unlock_tags_served = ConcurrentHashMap.newKeySet();

    public Set<String> getSra_unlock_tags_served() {
        return sra_unlock_tags_served;
    }

    public static String sraUnlockTagKey(int phase, int peer_idx) {
        return phase + ":" + peer_idx;
    }

    public static int phaseForStreet(int street, boolean isRabbit) {
        if (street == Crupier.FLOP) {
            return isRabbit ? UNLOCK_PHASE_RABBIT_FLOP : UNLOCK_PHASE_FLOP;
        }
        if (street == Crupier.TURN) {
            return isRabbit ? UNLOCK_PHASE_RABBIT_TURN : UNLOCK_PHASE_TURN;
        }
        return isRabbit ? UNLOCK_PHASE_RABBIT_RIVER : UNLOCK_PHASE_RIVER;
    }

    public boolean hasMegaPacket() {
        return local_mega_packet != null;
    }

    // Tiempo máximo que el handler de REQ_SRA_UNLOCK_CHAIN espera a que el
    // Crupier local avance hasta la calle exigida por la phase del batch
    // antes de tratarlo como maniobra del host (early-cascade attack).
    // Generoso: cubre clientes lentos, redes con jitter alto y manos con
    // muchos jugadores remotos. Un host honesto nunca rebasa este margen
    // porque su propia cascade se dispara justo después de su rondaApuestas
    // local cerrar, y eso espera al último ACTION que el cliente también
    // recibió por el mismo socket.
    public static final long UNLOCK_WAIT_TIMEOUT_MS = 60000L;

    // Único punto desde el que se modifica street; publica la transición
    // bajo protocol_state_lock para que awaitStreetForUnlockPhase no se
    // pierda el cambio. Llamar siempre por aquí en lugar de tocar el
    // campo directamente.
    private void setStreetLocal(int s) {
        synchronized (protocol_state_lock) {
            this.street = s;
            protocol_state_lock.notifyAll();
        }
    }

    // Idem para show_time (cubre las phases RABBIT_*).
    public void setShowTime(boolean v) {
        synchronized (protocol_state_lock) {
            this.show_time = v;
            protocol_state_lock.notifyAll();
        }
    }

    // Idem para conta_mano. Necesario para que el wait con hand_id pueda
    // distinguir un comando de mano antigua que llega retrasado (drop
    // silencioso) de un timeout genuino (ataque, lockdown). Todas las
    // mutaciones de conta_mano pasan por aquí.
    private void setContaManoLocal(int value) {
        synchronized (protocol_state_lock) {
            this.conta_mano = value;
            protocol_state_lock.notifyAll();
        }
    }

    /**
     * Espera bloqueante hasta que el Crupier local haya progresado lo
     * suficiente para que sea seguro servir un REQ_SRA_UNLOCK_CHAIN de la phase
     * pedida, o hasta agotar el timeout.
     *
     * Gateo zero-trust contra el "early-cascade attack": un host malicioso
     * (con el código modificado, y dado que CoronaPoker es 100% open source
     * cualquiera puede compilar una versión hostil) podría adelantar la
     * cascade de FLOP/TURN/RIVER antes de jugar la calle previa, leer las
     * cartas comunitarias y jugar el pre-flop con conocimiento del board.
     * El state machine por sí solo no lo detecta (la cascade está bien
     * formada, sólo va prematura). Esta espera fuerza que el cliente sólo
     * sirva la clave cuando su propia ronda local ha cerrado la calle
     * anterior — el cliente es la fuente de verdad de "hemos jugado el
     * pre-flop".
     *
     * Devuelve un código discreto para que el caller distinga las tres
     * salidas posibles (READY, STALE_HAND, TIMEOUT) y aplique distinta
     * política de seguridad — un comando residual de una mano ya pasada
     * NO es trampa (drop silencioso), pero un timeout legítimo sí lo es.
     */
    public UnlockWaitResult awaitStreetForUnlockPhase(int phase, int hand_id, long timeoutMs) {
        synchronized (protocol_state_lock) {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (true) {
                if (Crupier.SECURITY_LOCKDOWN) {
                    return UnlockWaitResult.LOCKDOWN;
                }
                if (isFin_de_la_transmision()) {
                    return UnlockWaitResult.STALE_HAND;
                }
                if (hand_id != this.conta_mano) {
                    // Mano de la request no coincide con la actual: o
                    // viene de una mano ya cerrada (drop silencioso, no
                    // es ataque) o el host nos está adelantando (raro y
                    // tampoco sirve para nada; lo tratamos igual).
                    return UnlockWaitResult.STALE_HAND;
                }
                if (isUnlockPhaseStateSafe(phase)) {
                    return UnlockWaitResult.READY;
                }
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return UnlockWaitResult.TIMEOUT;
                }
                try {
                    protocol_state_lock.wait(remaining);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return UnlockWaitResult.TIMEOUT;
                }
            }
        }
    }

    public enum UnlockWaitResult {
        READY,        // listo para servir, las gates posteriores deciden
        STALE_HAND,   // hand_id no coincide con la mano actual o se canceló: drop silencioso
        TIMEOUT,      // expiró el deadline esperando a la calle: ataque
        LOCKDOWN      // lockdown ya activo, no servir nada más
    }

    // Predicado interno: ¿el estado local del Crupier admite ya servir
    // esta phase? POCKET es siempre seguro (es el primer paso de la mano);
    // FLOP/TURN/RIVER exigen que el propio rondaApuestas haya avanzado la
    // calle correspondiente; los RABBIT_* exigen show_time. Llamado bajo
    // protocol_state_lock.
    private boolean isUnlockPhaseStateSafe(int phase) {
        return isUnlockPhaseAllowedForStreet(phase, this.street, this.show_time, this.run_it_twice_side_b);
    }

    public void setRunItTwiceSideB(boolean v) {
        synchronized (protocol_state_lock) {
            this.run_it_twice_side_b = v;
            // Despierta a awaitStreetForUnlockPhase para que reevalúe el gate al
            // entrar/salir de SIDE-B (igual que setStreetLocal con la calle).
            protocol_state_lock.notifyAll();
        }
    }

    public boolean isRunItTwiceSideB() {
        return this.run_it_twice_side_b;
    }

    public String getRitPotBoardTag() {
        return this.rit_pot_board_tag;
    }

    /**
     * Pure gating predicate (no Crupier state → unit-testable): may a given unlock phase be
     * served when the local street machine is at {@code street} and show_time is {@code showTime}?
     * POCKET is always safe (hand start); FLOP/TURN/RIVER require the LOCAL rondaApuestas to have
     * reached that street — this is the anti early-cascade gate: a hostile host cannot make us
     * reveal a future street because our street only advances in lockstep with betting, not by
     * the host's broadcast. RABBIT_* require show_time. Anything else is refused.
     */
    static boolean isUnlockPhaseAllowedForStreet(int phase, int street, boolean showTime) {
        return isUnlockPhaseAllowedForStreet(phase, street, showTime, false);
    }

    /**
     * Overload con el flag de reparto run-it-twice SIDE-B. Las fases RIT2_* solo
     * se sirven MIENTRAS SIDE-B se está repartiendo ({@code ritSideB}), y con el
     * mismo gate anti early-cascade que el board vivo (la calle local re-avanza
     * durante SIDE-B). Fuera de SIDE-B se rechazan siempre: de-lockear esos
     * offsets frescos en una mano normal filtraría cartas aún vivas en el mazo.
     */
    static boolean isUnlockPhaseAllowedForStreet(int phase, int street, boolean showTime, boolean ritSideB) {
        switch (phase) {
            case UNLOCK_PHASE_POCKET:
                return true;
            case UNLOCK_PHASE_FLOP:
                return street >= FLOP;
            case UNLOCK_PHASE_TURN:
                return street >= TURN;
            case UNLOCK_PHASE_RIVER:
                return street >= RIVER;
            case UNLOCK_PHASE_RABBIT_FLOP:
            case UNLOCK_PHASE_RABBIT_TURN:
            case UNLOCK_PHASE_RABBIT_RIVER:
                return showTime;
            case UNLOCK_PHASE_RIT2_FLOP:
                return ritSideB && street >= FLOP;
            case UNLOCK_PHASE_RIT2_TURN:
                return ritSideB && street >= TURN;
            case UNLOCK_PHASE_RIT2_RIVER:
                return ritSideB && street >= RIVER;
            default:
                return false;
        }
    }

    public boolean showPlayerCards(String nick, String sraKeyB64, String sigB64) {
        synchronized (lock_mostrar) {
            // SHOWCARDS = botón MOSTRAR voluntario mid-hand. NO es el showdown
            // (eso es POTCARDS). Aún así llevamos sig Ed25519 para que el host
            // no pueda substituir la sraKey y hacer ver cartas falsas a
            // espectadores/stream/UI mid-hand.
            //
            // Es idempotente (chequea isTapada()).
            {
                Player jugador = nick2player.get(nick);
                if (jugador == null) {
                    return false;
                }

                // BLINDAJE V61: Si el server nos hace un echo de nuestro propio paquete en un cliente remoto, LO IGNORAMOS.
                if (!GameFrame.getInstance().isPartida_local() && jugador.equals(GameFrame.getInstance().getLocalPlayer())) {
                    setTiempo_pausa(GameFrame.TEST_MODE ? PAUSA_ENTRE_MANOS_TEST : PAUSA_ENTRE_MANOS);
                    return false;
                }

                boolean decrypted = false;
                if (jugador.getHoleCard1().isTapada()) {
                    boolean keyProvided = (sraKeyB64 != null && !sraKeyB64.equals("*"));
                    boolean sigProvided = (sigB64 != null && !sigB64.equals("*"));
                    if (keyProvided && !sigProvided) {
                        // Sig ausente. En un CLIENTE = el host stripeó/forjó la sig -> host hostil -> LOCKDOWN
                        // (§8.2: cliente detecta ataque del host -> LEAVE). En el HOST procesando el SHOWCARDS
                        // de un peer = dato malformado AISLADO a ese peer -> SILENT-REFUSE (no destapar, SIN
                        // lockdown). Un peer NO puede matar la timba de todos con un SHOWCARDS sin firma.
                        if (!GameFrame.getInstance().isPartida_local()) {
                            LOGGER.log(Level.SEVERE,
                                    "ZERO-TRUST: SHOWCARDS for {0} arrived WITHOUT sig — host stripped or legacy. Host hostile, lockdown.",
                                    nick);
                            triggerSecurityLockdown(Translator.translate("zero_trust.host_showdown_sig_missing"));
                        } else {
                            LOGGER.log(Level.SEVERE,
                                    "ZERO-TRUST: peer {0} sent SHOWCARDS without sig — refusing (card stays face-down, no lockdown).",
                                    nick);
                        }
                    } else if (keyProvided && sigProvided) {
                        try {
                            byte[] sraKey = Base64.getDecoder().decode(sraKeyB64);
                            byte[] sig = Base64.getDecoder().decode(sigB64);
                            if (sraKey.length != 32 || sig.length != 64) {
                                if (!GameFrame.getInstance().isPartida_local()) {
                                    LOGGER.log(Level.SEVERE,
                                            "ZERO-TRUST: SHOWCARDS for {0} has bad lengths (key={1}, sig={2}) — malformed host wire, lockdown.",
                                            new Object[]{nick, sraKey.length, sig.length});
                                    triggerSecurityLockdown(Translator.translate("zero_trust.host_showdown_sig_missing"));
                                } else {
                                    LOGGER.log(Level.SEVERE,
                                            "ZERO-TRUST: peer {0} sent SHOWCARDS with bad lengths (key={1}, sig={2}) — refusing (no lockdown).",
                                            new Object[]{nick, sraKey.length, sig.length});
                                }
                            } else {
                                byte[] signerPubkey = resolveShowdownSignerPubkey(nick);
                                if (signerPubkey == null || this.current_hand_id == null) {
                                    LOGGER.log(Level.WARNING,
                                            "SHOWCARDS for {0}: signer pubkey or hand_id not resolved yet — card stays face-down (no lockdown, possible TOFU race)",
                                            nick);
                                } else if (!IdentityManager.verifyShowdownReveal(signerPubkey, this.current_hand_id, nick, sraKey, sig)) {
                                    if (!GameFrame.getInstance().isPartida_local()) {
                                        LOGGER.log(Level.SEVERE,
                                                "ZERO-TRUST: SHOWCARDS sig verify FAILED for {0} — host substituting key. Host hostile, lockdown.",
                                                nick);
                                        triggerSecurityLockdown(Translator.translate("zero_trust.host_showdown_sig_invalid"));
                                    } else {
                                        LOGGER.log(Level.SEVERE,
                                                "ZERO-TRUST: peer {0} SHOWCARDS sig verify FAILED — refusing (card stays face-down, no lockdown).",
                                                nick);
                                    }
                                } else {
                                    Participant p = GameFrame.getInstance().getParticipantes().get(nick);
                                    if (p != null) {
                                        p.setSra_unlock(sraKey);
                                    }
                                    decrypted = unlockPlayerCardsWithSRAKey(jugador);
                                    if (!decrypted && this.single_locked_pocket_cards.containsKey(nick)) {
                                        // Politica §8: sig OK pero SRA no resuelve = anomalia aislada a UN peer
                                        // -> FORFEIT (decrypted=false -> sus cartas no se revelan, el showdown las
                                        // muckea, ya manejado), NO terminamos la partida de todos. Avisamos.
                                        // Coherente con el caso gemelo en RESP_SHOWDOWN_KEY.
                                        LOGGER.log(Level.SEVERE,
                                                "ZERO-TRUST: SHOWCARDS for {0} — sig OK but SRA does not resolve. Malicious peer or bug -> FORFEIT (cards not revealed) + warning.",
                                                nick);
                                        // Visibilidad §8 con SOSPECHOSO correcto: en el HOST la culpa es del
                                        // PEER (warnMaliciousPeer lo nombra en rojo + popup); en un cliente es
                                        // el host quien relaya la clave mala (warnSuspiciousHost nombra al host).
                                        if (GameFrame.getInstance().isPartida_local()) {
                                            warnMaliciousPeer(nick, "zero_trust.peer_sra_corrupt");
                                        } else {
                                            warnSuspiciousHost(Translator.translate("zero_trust.peer_sra_corrupt"));
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.log(Level.WARNING, "Error decrypting SRA SHOWCARDS for " + nick, e);
                        }
                    }

                    if (decrypted) {
                        // Destape + etiqueta de jugada a un worker: este método
                        // corre en el hilo procesador de comandos y el giro
                        // animado bloquea ~1s — los comandos siguientes no
                        // pueden esperar eso. El orden etiqueta-tras-animación
                        // se conserva dentro del worker (la animación bloquea
                        // al worker, no al procesador). El destape lógico está
                        // garantizado por el finally del método animado aunque
                        // la animación falle.
                        final Player fjugador = jugador;

                        Helpers.threadRun(() -> {
                            // Serializado bajo el destape_animado_lock del jugador
                            // (reentrante para la animación interior) con re-check
                            // de tapada: el isTapada() de la entrada del método deja
                            // pasar un SHOWCARDS duplicado mientras el primer worker
                            // aún anima (~1s), y sin esto el duplicado repetiría
                            // etiqueta y sqlNewShowcards. Con el destape clásico la
                            // ventana era de milisegundos; con la animación no.
                            Object destape_lock = (fjugador instanceof RemotePlayer)
                                    ? ((RemotePlayer) fjugador).getDestape_animado_lock() : new Object();

                            synchronized (destape_lock) {

                                if (!fjugador.getHoleCard1().isTapada()) {
                                    return;
                                }

                                fjugador.ordenarCartas();
                                mostrarAnimacionDestaparCartasJugador(fjugador, true);

                                ArrayList<Card> evaluationList = new ArrayList<>();
                                evaluationList.addAll(fjugador.getHoleCards());
                                for (Card c : GameFrame.getInstance().getCartas_comunes()) {
                                    if (!c.isTapada()) {
                                        evaluationList.add(c);
                                    }
                                }

                                Hand jugada = null;
                                try {
                                    jugada = new Hand(evaluationList);
                                    fjugador.showCards(jugada.getName());
                                } catch (Exception e) {
                                }

                                if (GameFrame.SONIDOS_CHORRA && fjugador.getDecision() == Player.FOLD) {
                                    Audio.playWavResource("misc/showyourcards.wav");
                                }

                                if (!perdedores.containsKey(fjugador)) {
                                    GameFrame.getInstance().getRegistro().print(nick + " " + Translator.translate("ui.muestra_2") + Card.collection2String(fjugador.getHoleCards()) + ")" + (jugada != null ? " -> " + jugada : ""));
                                }

                                sqlNewShowcards(fjugador.getNickname(), fjugador.getDecision() == Player.FOLD);
                                sqlUpdateShowdownHand(fjugador, jugada);
                            }
                        });
                    }
                    setTiempo_pausa(GameFrame.TEST_MODE ? PAUSA_ENTRE_MANOS_TEST : PAUSA_ENTRE_MANOS);
                } else {
                    setTiempo_pausa(GameFrame.TEST_MODE ? PAUSA_ENTRE_MANOS_TEST : PAUSA_ENTRE_MANOS);
                }
                return decrypted;
            }
        }
    }

    public int getJugadoresCalentando() {

        int t = 0;

        for (Player j : GameFrame.getInstance().getJugadores()) {
            if (j.isCalentando()) {
                t++;
            }
        }

        return t;
    }

    public int getJugadoresActivos() {

        int t = 0;

        for (Player j : GameFrame.getInstance().getJugadores()) {
            if (j.isActivo()) {
                t++;
            }
        }

        return t;
    }

    private void killAllPlayerTimers() {
        for (Player p : GameFrame.getInstance().getJugadores()) {
            if (p != null) {
                p.stopActionTimer();
            }
        }
    }

    private void sqlSyncRecoveryShells(java.util.HashMap<String, Object> map) {
        if (map == null) {
            return;
        }

        synchronized (GameFrame.SQL_LOCK) {
            try {
                // 1. Ensure the Game record exists locally
                String sqlGame = "INSERT OR IGNORE INTO game(id, start, players, buyin, sb, blinds_time, rebuy, server, blinds_time_type, ugi, local) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (java.sql.PreparedStatement stG = Helpers.getSQLITE().prepareStatement(sqlGame)) {
                    stG.setInt(1, this.sqlite_id_game);
                    stG.setLong(2, map.get("start") != null ? (long) map.get("start") : System.currentTimeMillis());
                    stG.setString(3, (String) map.get("preflop_players"));
                    stG.setInt(4, map.get("buyin") != null ? (int) map.get("buyin") : 100);
                    stG.setDouble(5, map.get("sbval") != null ? (double) map.get("sbval") : 0.1);
                    stG.setInt(6, map.get("blinds_time") != null ? (int) map.get("blinds_time") : 0);
                    stG.setBoolean(7, map.get("rebuy") != null ? (boolean) map.get("rebuy") : true);
                    stG.setString(8, (String) map.get("server"));
                    stG.setInt(9, map.get("blinds_time_type") != null ? (int) map.get("blinds_time_type") : 0);
                    stG.setString(10, GameFrame.UGI);
                    stG.setInt(11, 0);
                    stG.executeUpdate();
                }

                // 2. Ensure the Hand record exists locally
                String sqlHand = "INSERT OR IGNORE INTO hand(id, id_game, counter, sbval, blinds_double, dealer, sb, bb, start, preflop_players) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (java.sql.PreparedStatement stH = Helpers.getSQLITE().prepareStatement(sqlHand)) {
                    stH.setInt(1, this.sqlite_id_hand);
                    stH.setInt(2, this.sqlite_id_game);
                    stH.setInt(3, map.get("conta_mano") != null ? (int) map.get("conta_mano") : 1);
                    stH.setDouble(4, map.get("sbval") != null ? (double) map.get("sbval") : 0.1);
                    stH.setInt(5, map.get("blinds_double") != null ? (int) map.get("blinds_double") : 0);
                    stH.setString(6, (String) map.get("dealer"));
                    stH.setString(7, (String) map.get("sb"));
                    stH.setString(8, (String) map.get("bb"));
                    stH.setLong(9, System.currentTimeMillis());
                    stH.setString(10, (String) map.get("preflop_players"));
                    stH.executeUpdate();
                }

                LOGGER.log(Level.INFO, "Local database shells synchronized with server IDs.");
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Failed to sync local SQL shell records", ex);
            }
        }
    }

    private void recuperarDatosClavePartida() {
        LOGGER.log(Level.INFO, "ZERO-TRUST: starting recuperarDatosClavePartida");

        this.recovery_positions_set = false;

        for (Player j : GameFrame.getInstance().getJugadores()) {
            if (j.getNickname().startsWith("CoronaBot$") && !GameFrame.getInstance().getParticipantes().containsKey(j.getNickname())) {
                Participant dummy = new Participant(GameFrame.getInstance().getSala_espera(), j.getNickname(), null, null, null, null, true);
                GameFrame.getInstance().getParticipantes().put(j.getNickname(), dummy);
            }
        }

        for (Player p : GameFrame.getInstance().getJugadores()) {
            nick2player.putIfAbsent(p.getNickname(), p);
        }

        killAllPlayerTimers();
        turno = System.currentTimeMillis();

        java.util.HashMap<String, Object> map;
        saltar_primera_mano = false;

        if (GameFrame.getInstance().isPartida_local()) {
            map = sqlRecoverServerLocalGameKeyData(true);
            if (map == null) {
                // Sin fila de recovery (juego sin manos commiteadas, p.ej. el host
                // murió tras sqlNewGame y antes de la 1ª mano; o la DB no respondió).
                // Un return "a secas" dejaría saltar_primera_mano=false y el finally de
                // NUEVA_MANO se saltaría setPositions -> dealer/sb/bb null -> repartir()
                // peta o la mesa queda congelada (getJugadoresActivos()<2). Marcamos
                // fresh-start: el finally hará setPositions + rescate de spectators y
                // arrancará una mano nueva limpia. (El cliente ya degradaba así; el host
                // era el único que abandonaba la recuperación de forma muda.)
                saltar_primera_mano = true;
                return;
            }

            if (map.get("start") != null) {
                GameFrame.GAME_START_TIMESTAMP = (long) map.get("start");
            }

            java.util.ArrayList<String> pendientes = new java.util.ArrayList<>();
            for (Player jugador : GameFrame.getInstance().getJugadores()) {
                if (!jugador.equals(GameFrame.getInstance().getLocalPlayer()) && !GameFrame.getInstance().getParticipantes().get(jugador.getNickname()).isCpu()) {
                    pendientes.add(jugador.getNickname());
                }
            }

            if (map.get("preflop_players") != null) {
                String[] preflop = ((String) map.get("preflop_players")).split("#");
                for (String b64 : preflop) {
                    if (b64.isEmpty()) {
                        continue;
                    }
                    try {
                        String n = new String(Base64.getDecoder().decode(b64), "UTF-8");
                        Player p = nick2player.get(n);
                        if (p == null || !p.isActivo()) {
                            saltar_primera_mano = true;
                            break;
                        }
                    } catch (Exception e) {
                    }
                }
            }

            if (!saltar_primera_mano && map.get("hand_end") != null && (Long) map.get("hand_end") == 0L) {
                try {
                    String fosil = Helpers.loadHandFossil(this.sqlite_id_game);

                    if (fosil != null && fosil.contains("#")) {
                        String orderMap = null;
                        String[] sraFossilParts = fosil.split("#");
                        byte[] megaPacket = null;

                        for (String part : sraFossilParts) {
                            if (part.startsWith("ORDER@")) {
                                orderMap = part.substring("ORDER@".length());
                            } else if (part.startsWith("FULLMEGAPACKET@")) {
                                megaPacket = Base64.getDecoder().decode(part.substring("FULLMEGAPACKET@".length()));
                            } else if (part.startsWith("SRAKEYS_COMMUNITY@")) {
                                // Dual-lock: la mitad community se guardó en SRAKEYS_COMMUNITY@
                                // por guardarFosilSRA. Recuperarla es lo que permite que
                                // cascadeAndDealCommunityPieces siga funcionando post-recovery.
                                this.local_sra_unlock_community = Base64.getDecoder().decode(part.substring("SRAKEYS_COMMUNITY@".length()));
                                Participant myP = GameFrame.getInstance().getParticipantes().get(GameFrame.getInstance().getNick_local());
                                if (myP != null) {
                                    myP.setSra_unlock_community(this.local_sra_unlock_community);
                                }
                            } else if (part.startsWith("SRAKEYS@")) {
                                this.local_sra_unlock = Base64.getDecoder().decode(part.substring("SRAKEYS@".length()));
                                Participant myP = GameFrame.getInstance().getParticipantes().get(GameFrame.getInstance().getNick_local());
                                if (myP != null) {
                                    myP.setSra_unlock(this.local_sra_unlock);
                                }
                            } else if (part.startsWith("COMMITMENTS@")) {
                                // Recovery: repoblar los K del ring para que
                                // initHandStateChain reconstruya el MISMO H_0 que la mano original.
                                this.peer_k_pocket.clear();
                                this.peer_k_community.clear();
                                parseCommitments(part.substring("COMMITMENTS@".length()));
                            } else if (part.startsWith("BOTKEYS_COMMUNITY@")) {
                                // Dual-lock: unlocks community de cada bot. Sin esto, la
                                // contribución community del bot quedaría sin invertir y
                                // las community pieces no se podrían descifrar tras recovery.
                                String[] bKeys = part.substring("BOTKEYS_COMMUNITY@".length()).split(",");
                                for (String bk : bKeys) {
                                    if (bk.isEmpty()) {
                                        continue;
                                    }
                                    String[] pair = bk.split(":");
                                    try {
                                        String bNick = new String(Base64.getDecoder().decode(pair[0]), "UTF-8");
                                        byte[] bUnlockCommunity = Base64.getDecoder().decode(pair[1]);
                                        Participant pBot = GameFrame.getInstance().getParticipantes().get(bNick);
                                        if (pBot != null) {
                                            pBot.setSra_unlock_community(bUnlockCommunity);
                                        }
                                    } catch (Exception e) {
                                    }
                                }
                            } else if (part.startsWith("BOTKEYS@")) {
                                String[] bKeys = part.substring("BOTKEYS@".length()).split(",");
                                for (String bk : bKeys) {
                                    if (bk.isEmpty()) {
                                        continue;
                                    }
                                    String[] pair = bk.split(":");
                                    try {
                                        String bNick = new String(Base64.getDecoder().decode(pair[0]), "UTF-8");
                                        byte[] bUnlock = Base64.getDecoder().decode(pair[1]);
                                        Participant pBot = GameFrame.getInstance().getParticipantes().get(bNick);
                                        if (pBot != null) {
                                            pBot.setReceived_token(bUnlock);
                                        }
                                    } catch (Exception e) {
                                    }
                                }
                            } else if (part.startsWith("BOTVISUAL@")) {
                                String[] bVisuals = part.substring("BOTVISUAL@".length()).split("@");
                                for (String bv : bVisuals) {
                                    if (bv.isEmpty()) {
                                        continue;
                                    }
                                    String[] pair = bv.split(":");
                                    try {
                                        String bNick = new String(Base64.getDecoder().decode(pair[0]), "UTF-8");
                                        String[] cards = pair[1].split(",");
                                        Player botPlayer = nick2player.get(bNick);
                                        if (botPlayer != null) {
                                            // BOTVISUAL almacena getCartaComoEntero() (rango 1..52) — no sumar +1 al restaurar.
                                            botPlayer.getHoleCard1().iniciarConValorNumerico(Integer.parseInt(cards[0]));
                                            botPlayer.getHoleCard2().iniciarConValorNumerico(Integer.parseInt(cards[1]));

                                        }
                                    } catch (Exception e) {
                                    }
                                }
                            } else if (part.startsWith("POCKETS@")) {
                                // Repoblar single_locked_pocket_cards: imprescindible para que
                                // verifyAndStoreShowdownKey pueda aplicar el sraKey de cada
                                // peer al residuo single-locked y validar el plaintext de
                                // POTCARDS. Sin esto, todos los humanos remotos caen al
                                // path "no single_locked_pocket_cards ... skipping" y
                                // calcularJugadas los muckea.
                                String[] entries = part.substring("POCKETS@".length()).split(",");
                                for (String entry : entries) {
                                    if (entry.isEmpty()) {
                                        continue;
                                    }
                                    String[] pair = entry.split(":");
                                    if (pair.length != 2) {
                                        continue;
                                    }
                                    try {
                                        String pNick = new String(Base64.getDecoder().decode(pair[0]), "UTF-8");
                                        byte[] pBytes = Base64.getDecoder().decode(pair[1]);
                                        if (pBytes.length == 64) {
                                            this.single_locked_pocket_cards.put(pNick, pBytes);
                                        }
                                    } catch (Exception e) {
                                    }
                                }
                            } else if (part.startsWith("VISUAL@")) {
                                String[] vis = part.substring("VISUAL@".length()).split(",");
                                try {
                                    byte v0 = Byte.parseByte(vis[0]);
                                    byte v1 = Byte.parseByte(vis[1]);
                                    if (v0 >= 0 && v0 < 52 && v1 >= 0 && v1 < 52) {
                                        this.local_original_cards[0] = v0;
                                        this.local_original_cards[1] = v1;
                                    } else {
                                        LOGGER.log(Level.WARNING, "VISUAL@ skipped — out-of-range values ({0},{1}) from poisoned fossil (likely MISDEAL before card decryption)", new Object[]{v0, v1});
                                    }
                                } catch (NumberFormatException nfe) {
                                    LOGGER.log(Level.WARNING, "VISUAL@ unparseable: {0}", part);
                                }
                            } else if (part.startsWith("RIT@")) {
                                // Run-it-twice: restaura el estado del voto para que
                                // la mano recuperada corra los dos boards (o uno).
                                String[] rit = part.substring("RIT@".length()).split(",");
                                try {
                                    this.rit_vote_done = Boolean.parseBoolean(rit[0]);
                                    this.rit_agreed = Boolean.parseBoolean(rit[1]);
                                    this.rit_allin_street = Integer.parseInt(rit[2]);
                                } catch (Exception ex) {
                                    LOGGER.log(Level.WARNING, "RIT@ unparseable: {0}", part);
                                }
                            } else if (part.startsWith("STRADDLE@")) {
                                // Straddle voluntario: si la mano replayada lo tenía
                                // posteado, el host lo repone en resolveVoluntaryStraddle
                                // (post-reparto) y rebroadcasta la decisión a los clientes.
                                this.straddle_recovered_posted = Boolean.parseBoolean(part.substring("STRADDLE@".length()));
                            }
                        }

                        if (orderMap != null && megaPacket != null) {
                            this.local_mega_packet = megaPacket;
                            // Recover: el barajado se verifico pre-crash y el mazo viene del fosil propio
                            // (confiable) -> marco verificado para el receipt (no falsa alarma de "no verificado").
                            this.dual_lock_verified_megapacket = megaPacket;
                            String[] orderTokens = orderMap.split(",");
                            java.util.ArrayList<String> ringList = new java.util.ArrayList<>();
                            for (String token : orderTokens) {
                                if (!token.isEmpty()) {
                                    ringList.add(new String(java.util.Base64.getDecoder().decode(token), "UTF-8"));
                                }
                            }
                            this.active_crypto_ring = ringList.toArray(new String[0]);

                            // Las hole cards del jugador local NO se destapan aqui:
                            // se quedan reseteadas (resetearCarta(false) en NUEVA_MANO,
                            // igual que las de los remotos) para que repartir() las
                            // revele al aterrizar el vuelo de reparto, idéntico a una
                            // mano normal. local_original_cards ya lleva su valor para
                            // el onLand del vuelo. Destaparlas aqui las mostraba boca
                            // arriba antes de la animacion (solo afectaba al local).

                            // Recovery: restore HAND_ID from SQL
                            // and re-init HandStateChain. Without this the chain
                            // stays null through the recovered hand, action absorbs
                            // become no-ops and the consensus phase skips silently.
                            // Replay then re-absorbs every action via the persisted
                            // record/sig in the action table, advancing H_t exactly
                            // as the pre-crash chain did.
                            try {
                                Object handIdObj = (map != null) ? map.get("hand_id_b64") : null;
                                if (handIdObj instanceof String) {
                                    byte[] hid = Base64.getDecoder().decode((String) handIdObj);
                                    if (hid.length == CanonicalActionRecord.HAND_ID_BYTES) {
                                        this.current_hand_id = hid;
                                        initHandStateChain();
                                    }
                                }
                            } catch (Exception chainEx) {
                                LOGGER.log(Level.WARNING, "Failed to restore HandStateChain on recovery (host)", chainEx);
                            }
                        }
                    }
                } catch (Exception e) {
                    saltar_primera_mano = true;
                }
            } else {
                saltar_primera_mano = true;
            }
            // Si el fosil existia pero no llego a entregar mega_packet+ring
            // utilizables (corrupto o vacio por MISDEAL+rollback previo), no
            // hay material para replay. Forzar saltar=true asegura simetria
            // con el branch cliente (recibira map sin megapacket y tambien
            // saltara).
            if (!saltar_primera_mano && (this.local_mega_packet == null || this.active_crypto_ring == null)) {
                saltar_primera_mano = true;
            }
            enviarDatosClaveRecuperados(pendientes, map);
        } else {
            map = recibirDatosClaveRecuperados();
            if (map != null && map.get("hand_id") != null) {
                this.sqlite_id_hand = (int) map.get("hand_id");
            }
            sqlSyncRecoveryShells(map);
            // Solo cargar fosil si la mano realmente estaba en-curso
            // (hand_end == 0L). Si la mano esta cerrada (post-MISDEAL la
            // marca con end!=0 via rollbackAbortedHand) NO se carga
            // local_mega_packet/ring desde el fosil viejo — sino el
            // DECK_CASCADE_REQ de la mano FRESH del host disparaba
            // hasMegaPacket()=true en cliente y triggerSecurityLockdown
            // como falso positivo "MEGAPACKET already locked".
            // El HOST ya tiene este mismo check arriba (linea ~2671);
            // sin el equivalente aqui en CLIENTE, race condition garantizada.
            boolean handInProgress = map != null
                    && map.get("hand_end") != null
                    && (Long) map.get("hand_end") == 0L;

            // Issue#9: si el local NO esta en preflop_players de la mano en
            // curso del host, somos observer pasivo — la mano que el host
            // esta replayando NO es nuestra (p.ej. salimos limpios en una
            // mano anterior y el host nos reinvita mid-N+1 sin "wait for
            // hand end"). Sin este guard, loadHandFossil devuelve el fosil
            // STALE de la ultima mano en la que SI participamos (hand N),
            // poblando local_mega_packet+active_crypto_ring con datos
            // viejos. Luego el guard observer de mas abajo ve ambos != null
            // y NO entra en el branch que marca calentando — repartir() nos
            // dealea con local_original_cards del fosil viejo (cartas de
            // hand N reveladas como nuestras, o AA mismo palo del init
            // {0,0} si VISUAL@ no estaba). cleanHandCrupierTempFiles se
            // skipea en NUEVA_MANO cuando GameFrame.RECOVER, asi que el
            // fosil stale sobrevive entre sesiones del cliente cuando este
            // se va con Leave Game antes de que arranque la mano siguiente.
            //
            // preflop_players es String "b64nick#b64nick#..." (ver
            // sqlNewHand+sqlUpdateHandResistencia). split+contains evita
            // falso positivo si un b64 fuera substring de otro.
            //
            // Si preflop_players viene null (no deberia: sqlNewHand siempre
            // lo escribe en PREFLOP) caemos al path legacy con fosil para
            // no romper recoveries en estados intermedios desconocidos.
            boolean shouldLoadFossil = handInProgress;
            if (handInProgress && map.get("preflop_players") instanceof String) {
                String preflopStr = (String) map.get("preflop_players");
                try {
                    String myNickB64 = Base64.getEncoder().encodeToString(
                            GameFrame.getInstance().getNick_local().getBytes("UTF-8"));
                    shouldLoadFossil = java.util.Arrays.asList(preflopStr.split("#")).contains(myNickB64);
                } catch (Exception e) {
                    shouldLoadFossil = false;
                }
            }
            try {
                String fosil = shouldLoadFossil ? Helpers.loadHandFossil(this.sqlite_id_game) : null;
                if (fosil != null && fosil.contains("#")) {
                    String orderMap = null;
                    String[] sraFossilParts = fosil.split("#");
                    byte[] megaPacket = null;

                    for (String part : sraFossilParts) {
                        if (part.startsWith("ORDER@")) {
                            orderMap = part.substring("ORDER@".length());
                        } else if (part.startsWith("FULLMEGAPACKET@")) {
                            megaPacket = Base64.getDecoder().decode(part.substring("FULLMEGAPACKET@".length()));
                        } else if (part.startsWith("SRAKEYS_COMMUNITY@")) {
                            // Dual-lock: la mitad community persistida por guardarFosilSRA.
                            this.local_sra_unlock_community = Base64.getDecoder().decode(part.substring("SRAKEYS_COMMUNITY@".length()));
                            Participant myP = GameFrame.getInstance().getParticipantes().get(GameFrame.getInstance().getNick_local());
                            if (myP != null) {
                                myP.setSra_unlock_community(this.local_sra_unlock_community);
                            }
                        } else if (part.startsWith("SRAKEYS@")) {
                            this.local_sra_unlock = Base64.getDecoder().decode(part.substring("SRAKEYS@".length()));
                            Participant myP = GameFrame.getInstance().getParticipantes().get(GameFrame.getInstance().getNick_local());
                            if (myP != null) {
                                myP.setSra_unlock(this.local_sra_unlock);
                            }
                        } else if (part.startsWith("COMMITMENTS@")) {
                            // Recovery: repoblar los K del ring para que
                            // initHandStateChain reconstruya el MISMO H_0 que la mano original.
                            this.peer_k_pocket.clear();
                            this.peer_k_community.clear();
                            parseCommitments(part.substring("COMMITMENTS@".length()));
                        } else if (part.startsWith("BOTKEYS_COMMUNITY@")) {
                            String[] bKeys = part.substring("BOTKEYS_COMMUNITY@".length()).split(",");
                            for (String bk : bKeys) {
                                if (bk.isEmpty()) {
                                    continue;
                                }
                                String[] pair = bk.split(":");
                                try {
                                    String bNick = new String(Base64.getDecoder().decode(pair[0]), "UTF-8");
                                    byte[] bUnlockCommunity = Base64.getDecoder().decode(pair[1]);
                                    Participant pBot = GameFrame.getInstance().getParticipantes().get(bNick);
                                    if (pBot != null) {
                                        pBot.setSra_unlock_community(bUnlockCommunity);
                                    }
                                } catch (Exception e) {
                                }
                            }
                        } else if (part.startsWith("BOTKEYS@")) {
                            String[] bKeys = part.substring("BOTKEYS@".length()).split(",");
                            for (String bk : bKeys) {
                                if (bk.isEmpty()) {
                                    continue;
                                }
                                String[] pair = bk.split(":");
                                try {
                                    String bNick = new String(Base64.getDecoder().decode(pair[0]), "UTF-8");
                                    byte[] bUnlock = Base64.getDecoder().decode(pair[1]);
                                    Participant pBot = GameFrame.getInstance().getParticipantes().get(bNick);
                                    if (pBot != null) {
                                        pBot.setReceived_token(bUnlock);
                                    }
                                } catch (Exception e) {
                                }
                            }
                        } else if (part.startsWith("BOTVISUAL@")) {
                            String[] bVisuals = part.substring("BOTVISUAL@".length()).split("@");
                            for (String bv : bVisuals) {
                                if (bv.isEmpty()) {
                                    continue;
                                }
                                String[] pair = bv.split(":");
                                try {
                                    String bNick = new String(Base64.getDecoder().decode(pair[0]), "UTF-8");
                                    String[] cards = pair[1].split(",");
                                    Player botPlayer = nick2player.get(bNick);
                                    if (botPlayer != null) {
                                        // BOTVISUAL almacena getCartaComoEntero() (rango 1..52) — no sumar +1 al restaurar.
                                        botPlayer.getHoleCard1().iniciarConValorNumerico(Integer.parseInt(cards[0]));
                                        botPlayer.getHoleCard2().iniciarConValorNumerico(Integer.parseInt(cards[1]));

                                    }
                                } catch (Exception e) {
                                }
                            }
                        } else if (part.startsWith("POCKETS@")) {
                            // Repoblar single_locked_pocket_cards del cliente: imprescindible
                            // para que recibirCartasResistencia pueda descifrar el residuo
                            // single-locked con el sraKey que llega en POTCARDS y verificar
                            // contra el plaintext del host. Sin esto el cliente cae al
                            // fallback de espectador y acepta plaintext sin verificación SRA.
                            String[] entries = part.substring("POCKETS@".length()).split(",");
                            for (String entry : entries) {
                                if (entry.isEmpty()) {
                                    continue;
                                }
                                String[] pair = entry.split(":");
                                if (pair.length != 2) {
                                    continue;
                                }
                                try {
                                    String pNick = new String(Base64.getDecoder().decode(pair[0]), "UTF-8");
                                    byte[] pBytes = Base64.getDecoder().decode(pair[1]);
                                    if (pBytes.length == 64) {
                                        this.single_locked_pocket_cards.put(pNick, pBytes);
                                    }
                                } catch (Exception e) {
                                }
                            }
                        } else if (part.startsWith("VISUAL@")) {
                            String[] vis = part.substring("VISUAL@".length()).split(",");
                            try {
                                byte v0 = Byte.parseByte(vis[0]);
                                byte v1 = Byte.parseByte(vis[1]);
                                if (v0 >= 0 && v0 < 52 && v1 >= 0 && v1 < 52) {
                                    this.local_original_cards[0] = v0;
                                    this.local_original_cards[1] = v1;
                                } else {
                                    LOGGER.log(Level.WARNING, "VISUAL@ skipped — out-of-range values ({0},{1}) from poisoned fossil (likely MISDEAL before card decryption)", new Object[]{v0, v1});
                                }
                            } catch (NumberFormatException nfe) {
                                LOGGER.log(Level.WARNING, "VISUAL@ unparseable: {0}", part);
                            }
                        }
                    }

                    if (orderMap != null && megaPacket != null) {
                        this.local_mega_packet = megaPacket;
                        // Recover: el barajado se verifico pre-crash y el mazo viene del fosil propio
                        // (confiable) -> marco verificado para el receipt (no falsa alarma de "no verificado").
                        this.dual_lock_verified_megapacket = megaPacket;
                        String[] orderTokens = orderMap.split(",");
                        java.util.ArrayList<String> ringList = new java.util.ArrayList<>();
                        for (String token : orderTokens) {
                            if (!token.isEmpty()) {
                                ringList.add(new String(java.util.Base64.getDecoder().decode(token), "UTF-8"));
                            }
                        }
                        this.active_crypto_ring = ringList.toArray(new String[0]);

                        // Las hole cards del jugador local NO se destapan aqui:
                        // se quedan reseteadas (resetearCarta(false) en NUEVA_MANO,
                        // igual que las de los remotos) para que repartir() las
                        // revele al aterrizar el vuelo de reparto, idéntico a una
                        // mano normal. local_original_cards ya lleva su valor para
                        // el onLand del vuelo. Destaparlas aqui las mostraba boca
                        // arriba antes de la animacion (solo afectaba al local).

                        // Recovery: symmetric with host branch —
                        // restore HAND_ID from the map (sent by host) and re-init
                        // HandStateChain so replay re-absorbs actions with the
                        // persisted record/sig from the wire.
                        try {
                            Object handIdObj = (map != null) ? map.get("hand_id_b64") : null;
                            if (handIdObj instanceof String) {
                                byte[] hid = Base64.getDecoder().decode((String) handIdObj);
                                if (hid.length == CanonicalActionRecord.HAND_ID_BYTES) {
                                    this.current_hand_id = hid;
                                    initHandStateChain();
                                }
                            }
                        } catch (Exception chainEx) {
                            LOGGER.log(Level.WARNING, "Failed to restore HandStateChain on recovery (client)", chainEx);
                        }
                    }
                }
            } catch (Exception e) {
            }

            // Simetrico con la decision del host (rama partida_local de arriba,
            // checks de map.get("hand_end") y del fosil): solo se intenta replay
            // de la mano interrumpida cuando hand_end existe explicitamente y
            // vale 0 (mano-en-curso real) Y el fosil entrega un megapacket
            // utilizable. Si la mano fue rolled-back tras MISDEAL, hand_end
            // viene null o sin row, y el fosil queda vacio: ahi NO hay nada
            // que replay, hay que arrancar mano fresh. Sin este branch el
            // cliente quedaba esperando datos de una mano inexistente mientras
            // el host ya habia llamado sqlNewHand() -> cuelgue indefinido y
            // stack 0 en GUI (de ahi el "ESPECTADOR no calentando").
            //
            // Joiner pasivo (humano nuevo que entra antes de un recover de timba):
            // no tiene fosil local (su SQLite es virgen) -> local_mega_packet/
            // active_crypto_ring quedan null -> cae en saltar=true. Pero si el
            // host SI esta replayando (hand_end == 0L en el map), el joiner debe
            // observar pasivamente: game_recovered=1 codifica "no replay local
            // mio, pero el host esta jugando una mano recuperada en la que yo
            // no estaba". El observer queda calentando (su setSpectator se hizo
            // arriba en el loop balance) y el rescate posterior en NUEVA_MANO
            // se gatea con game_recovered==0 para no sacarlo del calentando.
            // Sin esto, el unsetSpectator y la rama !isCalentando del repartir
            // forzaban al joiner a llamar recibirMisCartas esperando POCKET_CARDS
            // que el host no envia para manos en game_recovered=1 -> cuelgue
            // indefinido del hilo principal y bloqueo global tras la mano
            // recuperada (el host pide ACTION del joiner para la mano siguiente
            // y el joiner sigue stuck en recibirMisCartas de la anterior).
            boolean hostReplayingHand = map != null
                    && map.get("hand_end") != null
                    && (Long) map.get("hand_end") == 0L;
            if (map == null
                    || map.get("hand_end") == null
                    || (Long) map.get("hand_end") != 0L
                    || this.local_mega_packet == null
                    || this.active_crypto_ring == null) {
                saltar_primera_mano = true;
                if (hostReplayingHand) {
                    this.game_recovered = 1;
                    // Issue#9: el cliente local es observer pasivo de la mano
                    // en curso (sin fosil propio porque no estaba en ella —
                    // p.ej. dejo el juego limpio en una mano anterior y el
                    // host le invita a rejoin mientras esta mid-hand de una
                    // mano siguiente sin "wait for hand end"). En la rama del
                    // host (cryptoRingList != null) este caso se marca calentando
                    // via !inRing + stack>0 -> setSpectator. En el cliente
                    // cryptoRingList es null (no hay fosil -> active_crypto_ring
                    // null -> cryptoRingList null por el ternario de arriba), el
                    // loop cae en el else que solo maneja !inBalance + stack>0.
                    // Pero el local player SI esta in balance (su row de la
                    // ultima mano cerrada anterior), asi que ese else lo
                    // skippea -> spectator=false -> isActivo()=true.
                    // Resultado visible: repartir() lo dealea con
                    // local_original_cards={0,0} (init por defecto del byte[])
                    // -> ambas hole cards iniciarConValorNumerico(1) -> AA del
                    // mismo palo phantom; ademas el host no le manda accion
                    // (lo tiene como spectator) pero la GUI del cliente cree
                    // que juega. Espejamos aqui el calentando del host. El
                    // rescate de NUEVA_MANO (gateado por !exit + isSpectator +
                    // stack>0) le saca del calentando al arrancar la siguiente
                    // mano, asi que juega normal sin pasos extra.
                    Player myPlayer = GameFrame.getInstance().getLocalPlayer();
                    if (myPlayer != null && !myPlayer.isExit() && !myPlayer.isSpectator()) {
                        myPlayer.setSpectator(Translator.translate("game.calentando"));
                    }
                }
            } else {
                this.game_recovered = 1;
            }
        }

        if (map != null) {
            // rs.getInt/getLong devuelven 0 cuando la columna SQL es NULL (no
            // null) y map.put usa primitivos autoboxed por lo que !=null
            // siempre es true. Sin estos guards extra, un recovery sobre SQL
            // sin row (post-MISDEAL con la mano ya cerrada por
            // rollbackAbortedHand) sobreescribia GameFrame.BUYIN/CIEGAS a 0
            // -> players sin dinero ni blinds correctos.
            int recoveredHandId = map.get("hand_id") != null ? (int) map.get("hand_id") : -1;
            this.sqlite_id_hand = recoveredHandId > 0 ? recoveredHandId : -1;
            int recoveredBuyin = map.get("buyin") != null ? (int) map.get("buyin") : 0;
            if (recoveredBuyin > 0) {
                GameFrame.BUYIN = recoveredBuyin;
            }
            if (map.get("rebuy") != null) {
                GameFrame.REBUY = (boolean) map.get("rebuy");
            }
            int recoveredContaMano = map.get("conta_mano") != null ? (int) map.get("conta_mano") : 0;
            if (recoveredContaMano > 0) {
                setContaManoLocal(recoveredContaMano);
            }
            double recoveredSb = map.get("sbval") != null ? (double) map.get("sbval") : 0;
            if (recoveredSb > 0) {
                this.ciega_pequeña = recoveredSb;
            }
            double recoveredBb = map.get("bbval") != null ? (double) map.get("bbval") : 0;
            // La tabla hand solo guarda sbval; el SQL calcula bbval como sbval*2. Con
            // una estructura personalizada la BB puede no ser 2*SB, así que se deriva
            // del nivel correspondiente de la estructura activa (ya restaurada por
            // applyRecoverSettings). Sin estructura, bigBlindForSmallBlind devuelve
            // sb*2 -> idéntico al valor del SQL (timbas por defecto sin cambios).
            if (recoveredSb > 0f && GameFrame.ACTIVE_BLIND_STRUCTURE != null) {
                recoveredBb = GameFrame.bigBlindForSmallBlind(recoveredSb);
            }
            if (recoveredBb > 0f) {
                this.ciega_grande = recoveredBb;
            }
            this.ciegas_double = map.get("blinds_double") != null ? (int) map.get("blinds_double") : 0;
            if (map.get("play_time") != null) {
                GameFrame.getInstance().setConta_tiempo_juego((long) map.get("play_time"));
            }

            if (map.get("balance") != null) {
                String[] bal = ((String) map.get("balance")).split("@");
                java.util.ArrayList<String> nicksRec = new java.util.ArrayList<>();
                // ANTI robo de fichas en RECOVER: mi saldo VERAZ está persistido en MI PROPIO SQLite (cada
                // peer guarda balance por mano). El balance del host es solo una PISTA; abajo se PREFIERE el
                // local y se detecta si el host lo manipula (chip theft en la recuperación).
                final java.util.Map<String, double[]> localBal = readLocalRecoverBalances();
                for (String d : bal) {
                    if (d.isEmpty()) {
                        continue;
                    }
                    String[] p = d.split("\\|");
                    try {
                        String name = new String(Base64.getDecoder().decode(p[0]), "UTF-8");
                        nicksRec.add(name);
                        double hostStack = Double.parseDouble(p[1]);
                        int hostBuyin = Integer.parseInt(p[2]);
                        int hostRebuy = (p.length > 3) ? Integer.parseInt(p[3]) : 0;
                        // Preferir el saldo LOCAL (mi verdad); el del host SOLO si no tengo dato local (join
                        // nuevo, etc.). Si el host difiere de lo que YO persistí -> está manipulando el ledger
                        // en la recuperación -> uso el local + aviso (sospechoso host, rojo, queda registrado).
                        double stack = hostStack;
                        int buyin = hostBuyin;
                        int rebuy = hostRebuy;
                        double[] local = localBal.get(name);
                        if (local != null) {
                            stack = local[0];
                            buyin = (int) local[1];
                            rebuy = (int) local[2];
                            if (Helpers.doubleSecureCompare(stack, hostStack) != 0 || buyin != hostBuyin || rebuy != hostRebuy) {
                                LOGGER.log(Level.SEVERE,
                                        "ZERO-TRUST RECOVER: host balance for {0} (stack={1}, buyin={2}, rebuy={3}) != local (stack={4}, buyin={5}, rebuy={6}) — using LOCAL + warning",
                                        new Object[]{name, hostStack, hostBuyin, hostRebuy, stack, buyin, rebuy});
                                warnSuspiciousHost(Translator.translate("zero_trust.host_recover_balance_mismatch"));
                            }
                        }
                        Player jug = nick2player.get(name);
                        if (jug != null) {
                            jug.setStack(stack);
                            jug.setBuyin(buyin);
                            jug.setBet(0f);
                            this.auditor.put(name, new Double[]{stack, (double) buyin});
                            if (Helpers.doubleSecureCompare(0f, jug.getStack()) == 0) {
                                jug.setSpectator(null);
                            }
                        } else {
                            this.auditor.put(name, new Double[]{stack, (double) buyin});
                        }
                        if (rebuy > 0) {
                            rebuy_counts.put(name, rebuy);
                        }
                    } catch (Exception e) {
                    }
                }

                java.util.List<String> cryptoRingList = this.active_crypto_ring != null ? java.util.Arrays.asList(this.active_crypto_ring) : null;

                for (Player j : GameFrame.getInstance().getJugadores()) {
                    boolean inBalance = nicksRec.contains(j.getNickname());
                    boolean inRing = cryptoRingList != null && cryptoRingList.contains(j.getNickname());

                    if (Helpers.doubleSecureCompare(0f, j.getStack()) == 0) {
                        j.setSpectator(null);
                    }

                    if (cryptoRingList != null) {
                        if (inRing) {
                            if (!inBalance) {
                                j.setStack(0f);
                                j.setBet(0f);
                                j.setSpectator(null);
                                this.auditor.put(j.getNickname(), new Double[]{0d, (double) j.getBuyin()});
                            } else if (j.isCalentando() && Helpers.doubleSecureCompare(0f, j.getStack()) < 0) {
                                j.setSpectator(Translator.translate("game.calentando"));
                            }
                        } else {
                            if (Helpers.doubleSecureCompare(0f, j.getStack()) < 0) {
                                j.setSpectator(Translator.translate("game.calentando"));
                            }
                            this.auditor.put(j.getNickname(), new Double[]{j.getStack(), (double) j.getBuyin()});
                        }
                    } else {
                        if (!inBalance) {
                            if (Helpers.doubleSecureCompare(0f, j.getStack()) < 0) {
                                j.setSpectator(Translator.translate("game.calentando"));
                            }
                            this.auditor.put(j.getNickname(), new Double[]{j.getStack(), (double) j.getBuyin()});
                        }
                    }
                }
            }

            if (this.getJugadoresActivos() < 2) {
                saltar_primera_mano = true;
            }
            this.dealer_nick = (String) map.get("dealer");
            this.small_blind_nick = (String) map.get("sb");
            this.big_blind_nick = (String) map.get("bb");

            // Calcular utg_nick localmente cuando hay datos de recovery utiles:
            // cubre tanto el caso clasico (saltar=false, peer con replay completo)
            // como el caso del joiner pasivo observer (saltar=true pero el host
            // SI tiene una mano en curso en el map -> map.hand_end == 0L).
            // En ambos casos dealer/sb/bb se setearon arriba desde el map de
            // recovery y nicks_permutados contiene la lista completa de
            // participantes; basta el calculo local desde bb_pos.
            //
            // El else (no hay mano-en-curso en el map) es el fresh-start
            // genuino donde el host calcula y broadcastea POSITIONS via
            // setPositions y los clientes esperan a recibirlo.
            //
            // Antes este if se gateaba con !saltar_primera_mano, lo cual
            // metia al joiner observer en la rama else -> recibirPosiciones
            // -> timeout porque el host (en saltar=false) jamas envia
            // POSITIONS en este punto. Sin utg_nick, la siguiente
            // rondaApuestas iteraba infinitamente comparando nicks contra
            // null hasta IndexOutOfBoundsException en getJugadores().get().
            //
            // No uso this.game_recovered como discriminador porque su
            // asignacion final ocurre mas abajo (linea ~3296) y aqui aun no
            // refleja el estado de recovery del peer. map.hand_end == 0L es
            // el invariante real "hay una mano-en-curso en el snapshot de
            // recovery" y esta accesible directamente.
            boolean hostHasInProgressHand = (map != null
                    && map.get("hand_end") != null
                    && (Long) map.get("hand_end") == 0L);
            if (!saltar_primera_mano || hostHasInProgressHand) {
                int bb_pos = permutadoNick2Pos(this.big_blind_nick);
                if (bb_pos != -1) {
                    if (getJugadoresActivos() == 2) {
                        this.utg_nick = this.dealer_nick;
                    } else {
                        int utg_pos = bb_pos + 1;
                        String new_utg = permutadoPos2Nick(utg_pos);
                        while (!this.nick2player.containsKey(new_utg) || !this.nick2player.get(new_utg).isActivo()) {
                            new_utg = permutadoPos2Nick(++utg_pos);
                        }
                        this.utg_nick = new_utg;
                    }
                    for (Player jugador : GameFrame.getInstance().getJugadores()) {
                        jugador.refreshPos();
                    }
                }
            } else {
                setPositions();
                // Posiciones ya rotadas y repintadas aquí: el finally de
                // NUEVA_MANO NO debe volver a llamar setPositions (doble rotación).
                this.recovery_positions_set = true;
                for (Player jugador : GameFrame.getInstance().getJugadores()) {
                    jugador.refreshPos();
                }
            }
            actualizarContadoresTapete();
        }

        if (getJugadoresActivos() > 1 && !saltar_primera_mano) {
            if (GameFrame.MUSICA_AMBIENTAL) {
                Audio.stopLoopMp3("misc/background_music.mp3");
                Audio.playLoopMp3Resource("misc/recovering.mp3");
                recovering_music_active = true;
            }

            final float old_brightness = GameFrame.getInstance().getCapa_brillo().getBrightness();

            Helpers.GUIRun(() -> {
                if (old_brightness != BrightnessLayerUI.LIGHTS_OFF_BRIGHTNESS) {
                    GameFrame.getInstance().getCapa_brillo().setBrightness(BrightnessLayerUI.LIGHTS_OFF_BRIGHTNESS);
                    GameFrame.getInstance().getTapete().repaint();
                }

                recover_dialog = new RecoverDialog(GameFrame.getInstance(), true);
                recover_dialog.setLocationRelativeTo(recover_dialog.getParent());
                recover_dialog.setVisible(true);

                if (old_brightness != BrightnessLayerUI.LIGHTS_OFF_BRIGHTNESS) {
                    GameFrame.getInstance().getCapa_brillo().setBrightness(old_brightness);
                    GameFrame.getInstance().getTapete().repaint();
                }

                GameFrame.getInstance().getTapete().getCommunityCards().refreshLightsIcon();
            });

            if (GameFrame.getInstance().isPartida_local() || GameFrame.getInstance().getLocalPlayer().isActivo()) {
                recuperarAccionesLocales();
            }

            // sincronizando_mano debe activarse solo si TENGO acciones MIAS
            // (o de bots locales) que replicar visualmente via
            // siguienteAccionLocalRecuperada — esa es la unica ruta que
            // cierra el dragon dialog cuando la queue se vacia. Antes el
            // check era sobre tot_acciones_recuperadas (TODAS las acciones
            // de TODOS los peers de la mano): si la mano tenia acciones de
            // otros pero ninguna mia (caso clasico: me desconecte antes de
            // actuar), sincronizando_mano quedaba latched y el dragon
            // nunca se cerraba. Quien NO tiene acciones que replicar es un
            // observador del replay y no necesita el dialog.
            if (!this.acciones_locales_recuperadas.isEmpty()) {
                this.sincronizando_mano = true;
            } else {
                GameFrame.getInstance().getRegistro().print(Translator.translate("game.timba_recuperada"));

                if (GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {
                    Audio.playWavResource("misc/startplay.wav");
                }

                if (recovering_music_active) {
                    Audio.stopLoopMp3("misc/recovering.mp3");
                    Audio.playLoopMp3Resource("misc/background_music.mp3");
                    recovering_music_active = false;
                }
                Helpers.GUIRun(() -> {
                    if (recover_dialog != null) {
                        recover_dialog.setVisible(false);
                        recover_dialog.dispose();
                        recover_dialog = null;
                    }
                    GameFrame.getInstance().getFull_screen_menu().setEnabled(true);
                    Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(true);
                    GameFrame.getInstance().refresh();
                });
            }
        } else {
            GameFrame.getInstance().getRegistro().print(Translator.translate("game.timba_recuperada"));
            if (GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {
                Audio.playWavResource("misc/startplay.wav");
            }
            Helpers.GUIRun(() -> {
                GameFrame.getInstance().getFull_screen_menu().setEnabled(true);
                Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(true);
            });
        }

        for (Player jugador : GameFrame.getInstance().getJugadores()) {
            jugador.setContaWin(this.sqlGetPlayerContaWins(jugador.getNickname(), this.sqlite_id_game));
        }

        this.update_game_seats = true;
        GameFrame.setRECOVER(false);

        if (getJugadoresActivos() > 1 && !saltar_primera_mano) {
            this.game_recovered = 1;
            GameFrame.getInstance().refresh();
        }
        // NO else { game_recovered = 0 }: NUEVA_MANO ya resetea
        // game_recovered=0 al inicio (linea ~4172). Si la rama "joiner
        // pasivo observer" mas arriba (saltar=true + hostReplayingHand)
        // ya seteo game_recovered=1, ese valor debe sobrevivir. Un else
        // que lo bajaba a 0 piso ese fix y reintroducia el bug del joiner
        // colgado en recibirMisCartas durante la mano recuperada.
    }

    private void cancelarManoYDevolverApuestas(String motivo) {
        cancelarManoYDevolverApuestas(motivo, true);
    }

    public void cancelarManoYDevolverApuestas(String motivo, boolean broadcast) {
        // Idempotency: MISDEAL is intentionally double-fed on the
        // client side. WaitingRoomFrame.java:2015 invokes us directly from
        // the reader thread for an immediate refund + popup, then queues
        // the same MISDEAL command on received_commands so the Crupier
        // run() consumers (Crupier.java:4405/5091/7163) can break out of
        // their consensus/wait loops. Those consumers call us again with
        // the same motivo. Without an early return here, the second call
        // would re-log, re-print the registro, re-play the error sound and
        // re-queue another popup on the EDT (it would appear after the user
        // dismisses the first one). The first invocation already performed
        // the visible side effects; the second one is solely a signaling
        // breakout so just return.
        if (isFin_de_la_transmision()) {
            return;
        }
        // Antes del refund: los caminos con dinero ya liquidado en esta mano
        // (settle de CARA-A del run-it-twice) leen este flag al volver del
        // reparto abortado para revertir su parte. volatile + mismo hilo (host)
        // o publicación vía el breakout de la cola (cliente) lo hacen visible.
        this.mano_anulada = true;
        LOGGER.log(Level.WARNING, "MISDEAL triggered: {0}", motivo);
        // Defense in depth: if a recovery dragon was left open (e.g. a
        // ZERO_TRUST cascade failure aborted the hand mid-replay) close it now so it
        // does not stay on screen and does not keep sincronizando_mano latched.
        cerrarRecoverDialogYSync();
        GameFrame.getInstance().getRegistro().print(Translator.translate("game.mano_anulada") + " " + Translator.translate(motivo));
        GameFrame.getInstance().getRegistro().print(Translator.translate("game.mano_anulada_footer"));

        if (broadcast && GameFrame.getInstance().isPartida_local()) {
            try {
                String motivoB64 = Base64.getEncoder().encodeToString(motivo.getBytes("UTF-8"));
                broadcastGAMECommandFromServer("MISDEAL#" + motivoB64, null, true);
            } catch (UnsupportedEncodingException ex) {
                LOGGER.log(Level.SEVERE, "Failed to broadcast MISDEAL to clients", ex);
            }
        }

        synchronized (getLock_contabilidad()) {
            for (Player jugador : GameFrame.getInstance().getJugadores()) {

                // getBote() es el total invertido por el jugador en la mano actual,
                // sumando todas las streets.
                double refund = Helpers.doubleClean(jugador.getBote());

                if (Helpers.doubleSecureCompare(refund, 0f) > 0) {
                    jugador.setStack(Helpers.doubleClean(jugador.getStack()) + refund);
                    jugador.setBet(0f);
                    jugador.resetBote(); // Purge the financial memory for this aborted hand
                }
            }

            this.apuestas = 0f;
            this.bote_total = 0f;

            // Note: bote_sobrante is kept intact by design (it belongs to the global game, not the aborted hand)
            this.bote = new HandPot(0f);
        }

        Audio.playWavResource("misc/error.wav");

        // Coherent MISDEAL halt across host AND client.
        //
        // Both peers MUST:
        //   - Show the MISDEAL popup synchronously so the calling thread
        //     (Crupier thread on host, network reader thread on client)
        //     blocks until the user acknowledges. While the modal is up,
        //     nothing in that thread's callstack can advance towards a
        //     fresh NUEVA_MANO / cascade.
        //   - rollbackAbortedHand(): undo the conta_mano++ (NUEVA_MANO:3460)
        //     and the sqlNewHand insert (NUEVA_MANO:3547). Both ran on
        //     EVERY peer before the cascade failed. If we skipped this on
        //     the client, the client's local SQLite would still hold a
        //     hand row with end=0, and a future "Recover" from its main
        //     menu would try to replay a hand that was never really dealt.
        //   - setFin_de_la_transmision(true): halt the local run() loop
        //     before any new hand can start. ANY isFin_de_la_transmision()
        //     check downstream (NUEVA_MANO, repartir, rondaApuestas, SRA
        //     helpers, etc) bails out cleanly.
        //
        // Only the HOST then fires abortToRecover() which broadcasts
        // SERVEREXITRECOVER and triggers finTransmision -> RESET_GAME ->
        // Init.VENTANA_INICIO with the recover dialog auto-opened.
        //
        //
        // Clients do NOT broadcast — they are driven by receiving the
        // host's SERVEREXITRECOVER right after the reader thread resumes
        // from the popup. TCP guarantees the command arrives in order;
        // the reader thread blocking during the modal just delays delivery
        // by however long the user takes to click OK, which is harmless.
        //
        Helpers.mostrarMensajeError(GameFrame.getInstance(), Translator.translate("game.mano_anulada") + " " + Translator.translate(motivo) + "<b>" + Translator.translate("game.mano_anulada_footer") + "</b>");
        rollbackAbortedHand();
        setFin_de_la_transmision(true);

        if (broadcast && GameFrame.getInstance().isPartida_local()) {
            // Violacion zero-trust = ataque o protocolo roto: la timba acaba,
            // BalanceDialog final, NO se vuelve a sala de espera. Cualquier otro
            // motivo (peer caido normal, etc.) sigue por el flujo de recover.
            if (motivo != null && motivo.startsWith("zero_trust.")) {
                abortAndExit();
            } else {
                abortAndRecover();
            }
        }
    }

    /**
     * Undo the conta_mano increment and the sqlNewHand insert that NUEVA_MANO
     * performed before the cascade/deal failed. After this method, the in-
     * memory counter and the SQLite state reflect the last successfully
     * completed hand, which is what recuperarDatosClavePartida should see on
     * the recover path. balance/action/showdown/showcards rows for this hand
     * are wiped automatically via ON DELETE CASCADE on the hand foreign key.
     */
    private void rollbackAbortedHand() {
        // La mano abortada se MARCA como terminada (end != 0, pot=0) +
        // balance row con stacks post-refund, en lugar de borrarse. Asi el
        // recovery encuentra una "ultima mano cerrada limpiamente" igual
        // que tras un exit con wait-for-hand-end. La siguiente NUEVA_MANO
        // arranca fresh con calcularPosiciones limpio. conta_mano y
        // sqlite_id_hand se preservan (la mano cuenta como completada).
        // Sin este UPDATE+INSERT (DELETE original), recovery encontraba
        // SQL vacio -> dealer_nick=null -> repartir() petaba +
        // balance vacio -> players quedaban spectator sin cartas.
        if (sqlite_id_hand > 0) {
            // Cierre de la mano anulada (marcar end + pot=0 y escribir los
            // balances post-refund) en UNA transaccion. Con autocommit por
            // defecto cada statement se commiteaba por separado, asi que un
            // crash entre el UPDATE hand y los INSERT de balance dejaba la mano
            // marcada como cerrada pero con balances PARCIALES, y el recovery
            // (que lee balances de MAX(hand.id)) hacia volver a algun jugador
            // con stack/buyin por defecto -> auditor mismatch. Agrupar en
            // transaccion garantiza todo-o-nada: si el proceso muere antes del
            // commit, SQLite (journal clasico + synchronous=FULL) revierte y la
            // mano queda SIN cerrar (estado consistente que el recovery sabe
            // manejar), nunca a medias.
            synchronized (GameFrame.SQL_LOCK) {
                Connection con = null;
                boolean prev_autocommit = true;
                boolean tx = false;
                try {
                    con = Helpers.getSQLITE();
                    prev_autocommit = con.getAutoCommit();
                    con.setAutoCommit(false);
                    tx = true;

                    try (PreparedStatement statement = con.prepareStatement("UPDATE hand SET end=?, pot=0 WHERE id=?")) {
                        statement.setQueryTimeout(30);
                        statement.setLong(1, System.currentTimeMillis());
                        statement.setInt(2, sqlite_id_hand);
                        statement.executeUpdate();
                    }

                    for (Player j : GameFrame.getInstance().getJugadores()) {
                        if (j != null && !j.isExit()) {
                            sqlNewHandBalance(j.getNickname(), Helpers.doubleClean(j.getStack()), j.getBuyin());
                        }
                    }

                    con.commit();
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "Failed to persist aborted-hand close — rolling back", ex);
                    if (tx && con != null) {
                        try {
                            con.rollback();
                        } catch (SQLException e2) {
                            LOGGER.log(Level.SEVERE, "Aborted-hand close rollback failed", e2);
                        }
                    }
                } finally {
                    if (tx && con != null) {
                        try {
                            con.setAutoCommit(prev_autocommit);
                        } catch (SQLException e3) {
                            LOGGER.log(Level.SEVERE, "Aborted-hand close autocommit restore failed", e3);
                        }
                    }
                }
            }
        }
        // Refs in-memory: el Crupier sera destruido por RESET_GAME tras
        // abortAndRecover/abortAndExit. Limpieza por higiene.
        this.local_mega_packet = null;
        this.active_crypto_ring = null;
        this.local_sra_unlock = null;
        this.local_sra_lock_community = null;
        this.local_sra_unlock_community = null;
        this.local_original_cards = new byte[2];
    }

    /**
     * Auto-trigger the exit-with-recover flow from the MISDEAL handler. Mirrors
     * what GameFrame.exit_menuActionPerformed does when the user clicks "wait
     * for hand end and exit" — except the hand is already aborted by MISDEAL,
     * so there is nothing to wait for. Runs in its own thread because
     * finTransmision -> RESET_GAME shuts down the very thread pool we are on.
     */
    private void abortAndRecover() {
        // Aborto controlado: host fuerza recover -> todos a sala de espera
        // con dialog recover auto-abierto. Para MISDEAL no-zero-trust (peer
        // caido, etc.) la timba puede continuar fresh.
        LOGGER.log(Level.WARNING, "RECOVERY: abortAndRecover engaged — broadcasting SERVEREXITRECOVER and routing everyone to main menu with recover dialog");
        setForce_recover(true);
        Helpers.threadRun(() -> {
            try {
                String passSuffix = "";
                if (WaitingRoomFrame.getInstance() != null && WaitingRoomFrame.getInstance().getPassword() != null) {
                    passSuffix = "#" + Base64.getEncoder().encodeToString(
                            WaitingRoomFrame.getInstance().getPassword().getBytes("UTF-8"));
                }
                broadcastGAMECommandFromServer("SERVEREXITRECOVER" + passSuffix, null, false);
            } catch (UnsupportedEncodingException ex) {
                LOGGER.log(Level.SEVERE, "Failed to broadcast SERVEREXITRECOVER from MISDEAL", ex);
            }
            GameFrame.getInstance().finTransmision(true);
        });
    }

    private void abortAndExit() {
        // Violacion zero-trust detectada: la timba acaba. Broadcast SERVEREXIT
        // (no RECOVER) y finTransmision sin force_recover -> BalanceDialog
        // final con el balance al momento del ultimo refund. No reconexion,
        // no sala de espera. Fin punto.
        LOGGER.log(Level.WARNING, "ZERO-TRUST: abortAndExit engaged — broadcasting SERVEREXIT and routing everyone to BalanceDialog (game over)");
        // NO setForce_recover(true) — queremos que finTransmision detecte
        // force_recover=false y vaya a la rama BalanceDialog.
        Helpers.threadRun(() -> {
            try {
                broadcastGAMECommandFromServer("SERVEREXIT", null, false);
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Failed to broadcast SERVEREXIT from zero-trust MISDEAL", ex);
            }
            GameFrame.getInstance().finTransmision(true);
        });
    }

    private void recibirPosiciones() {

        // Guard de salida: si la transmisión termina (host caído, cliente
        // saliendo por SERVEREXITRECOVER, etc.) salimos sin haber recibido
        // POSITIONS. El caller (Crupier.run) ya tiene checks posteriores
        // que abortan limpiamente al ver isFin_de_la_transmision. Sin este
        // guard el cliente queda colgado para siempre esperando un
        // comando que nunca va a llegar.
        boolean ok;

        long start_time = System.currentTimeMillis();

        do {

            ok = false;

            synchronized (this.getReceived_commands()) {

                ArrayList<String> rejected = new ArrayList<>();

                while (!ok && !this.getReceived_commands().isEmpty()) {

                    String comando = this.received_commands.poll();
                    try {
                        String[] partes = comando.split("#");

                        if (partes.length >= 9 && partes[2].equals("POSITIONS")) {

                            ok = true;

                            try {
                                this.utg_nick = new String(Base64.getDecoder().decode(partes[3]), "UTF-8");

                                this.big_blind_nick = new String(Base64.getDecoder().decode(partes[4]), "UTF-8");

                                this.small_blind_nick = new String(Base64.getDecoder().decode(partes[5]), "UTF-8");

                                this.dealer_nick = new String(Base64.getDecoder().decode(partes[6]), "UTF-8");

                            } catch (UnsupportedEncodingException ex) {
                                LOGGER.log(Level.SEVERE, null, ex);
                            }

                            GameFrame.getInstance().setConta_tiempo_juego(Long.parseLong(partes[7]));

                            if (partes[8].equals("1")) {

                                this.doblarCiegas();
                            }

                        } else if (partes.length >= 3 && partes[2].equals("POSITIONS")) {
                            LOGGER.log(Level.WARNING, "POSITIONS malformed dropped: {0}", comando);
                        } else {
                            rejected.add(comando);
                        }
                    } catch (Exception ex) {
                        LOGGER.log(Level.WARNING, "Exception while processing command in receivePositions: " + comando, ex);
                    }

                }

                if (!rejected.isEmpty()) {
                    this.getReceived_commands().addAll(rejected);
                    rejected.clear();
                }

            }

            if (!ok) {

                if (GameFrame.getInstance().checkPause()) {
                    start_time = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - start_time > GameFrame.CLIENT_RECEPTION_TIMEOUT) {

                    LOGGER.log(Level.WARNING, "recibirPosiciones timeout — POSITIONS never arrived from host. Breaking wait to avoid indefinite block.");
                    break;
                } else {

                    synchronized (this.getReceived_commands()) {

                        try {
                            this.received_commands.wait(WAIT_QUEUES);
                        } catch (InterruptedException ex) {
                            Helpers.logCooperativeCancellation(LOGGER, "received commands wait", ex);
                            break;
                        }
                    }
                }
            }

        } while (!ok && !isFin_de_la_transmision());

    }

    public double getUltimo_raise() {
        return ultimo_raise;
    }

    public void actualizarCiegasManualmente(double sb, double bb, int double_val, int double_type) {
        synchronized (lock_ciegas) {

            if (this.ciega_pequeña != sb || this.ciega_grande != bb || GameFrame.CIEGAS_DOUBLE != double_val
                    || GameFrame.CIEGAS_DOUBLE_TYPE != double_type) {

                this.ciegas_update = new Object[]{sb, bb, double_val, double_type};

            } else {

                this.ciegas_update = null;
            }
        }
    }

    private double[] simulateNextBlinds() {
        if (GameFrame.ACTIVE_BLIND_STRUCTURE != null) {
            double[] next = BlindStructure.nextLevel(GameFrame.ACTIVE_BLIND_STRUCTURE, this.ciega_pequeña);
            return next != null ? next : new double[]{this.ciega_pequeña, this.ciega_grande};
        }
        int i = 0, j = 0;
        while (Helpers.doubleSecureCompare(this.ciega_pequeña / Math.pow(10, j), CIEGAS[i][0]) != 0) {
            i = (i + 1) % CIEGAS.length;
            if (i == 0) {
                j++;
            }
        }
        i = (i + 1) % CIEGAS.length;
        if (i == 0) {
            j++;
        }
        return new double[]{CIEGAS[i][0] * Math.pow(10, j), CIEGAS[i][1] * Math.pow(10, j)};
    }

    private boolean checkDoblarCiegas() {

        synchronized (lock_ciegas) {
            if (GameFrame.ACTIVE_BLIND_STRUCTURE != null
                    && BlindStructure.nextLevel(GameFrame.ACTIVE_BLIND_STRUCTURE, this.ciega_pequeña) == null) {
                // Estructura personalizada agotada (ultimo nivel alcanzado) o ciega
                // actual fuera de la escalera: no se sube mas y nunca se reanuncia.
                return false;
            }
            if (GameFrame.BLIND_CAP > 0f && Helpers.doubleSecureCompare(simulateNextBlinds()[1], GameFrame.BLIND_CAP) > 0) {
                return false;
            }
            if (GameFrame.CIEGAS_DOUBLE_TYPE <= 1) {
                return (GameFrame.CIEGAS_DOUBLE > 0
                        && (int) Math.floor((float) GameFrame.getInstance().getConta_tiempo_juego()
                                / (GameFrame.CIEGAS_DOUBLE * 60)) > this.ciegas_double);
            } else {
                return (GameFrame.CIEGAS_DOUBLE > 0 && this.conta_mano > 1
                        && ((int) Math.floor((float) (this.conta_mano - 1))
                        / GameFrame.CIEGAS_DOUBLE) > this.ciegas_double);
            }
        }
    }

    private void doblarCiegas() {

        if (GameFrame.ACTIVE_BLIND_STRUCTURE != null) {
            double[] next = BlindStructure.nextLevel(GameFrame.ACTIVE_BLIND_STRUCTURE, this.ciega_pequeña);
            if (next == null) {
                // checkDoblarCiegas ya lo veta; defensa: nunca atascar ni anunciar
                // una subida fantasma en el ultimo nivel de la escalera.
                LOGGER.log(Level.WARNING, "doblarCiegas with no next level on custom structure (sb={0})", Helpers.doubleClean(this.ciega_pequeña));
                return;
            }
            this.ciegas_double++;
            this.ciega_pequeña = next[0];
            this.ciega_grande = next[1];
            Audio.playWavResource("misc/double_blinds.wav");
            GameFrame.getInstance().getRegistro().print(Translator.translate("blinds.se_doblan_las_ciegas"));
            return;
        }

        int i, j;

        i = 0;

        j = 0;

        while (Helpers.doubleSecureCompare(ciega_pequeña / Math.pow(10, j), CIEGAS[i][0]) != 0) {

            i = (i + 1) % CIEGAS.length;

            if (i == 0) {
                j++;
            }
        }

        i = (i + 1) % CIEGAS.length;

        if (i == 0) {
            j++;
        }

        this.ciegas_double++;

        this.ciega_pequeña = CIEGAS[i][0] * Math.pow(10, j);

        this.ciega_grande = CIEGAS[i][1] * Math.pow(10, j);

        Audio.playWavResource("misc/double_blinds.wav");

        GameFrame.getInstance().getRegistro().print(Translator.translate("blinds.se_doblan_las_ciegas"));

    }

    public double getBote_total() {
        return bote_total;
    }

    private void readyForNextHand() {
        // Limpieza entre manos del cache de pocket cards y de la cola de comandos
        // pendientes. La cola tiene que limpiarse bajo su propio monitor porque
        // el thread del Participant puede estar polleando entradas al mismo
        // tiempo (sync sobre received_commands en cada acceso). Sin synchronized
        // aquí podía aparecer ConcurrentModificationException o mensajes legítimos
        // de la mano siguiente quedarse en estado inconsistente.
        single_locked_pocket_cards.clear();

        synchronized (received_commands) {
            received_commands.clear();
        }

        // Identity: the per-hand chain belongs to the hand that just ended. The
        // new hand seeds a fresh chain after its MEGAPACKET arrives.
        this.current_hand_id = null;
        this.hand_state_chain = null;

        // Identity: reset the invalid-sig flag for the new hand.
        this.saw_invalid_action_sig = false;

        // Local entropy for our SRA shuffle (never leaves this process). 48 bytes:
        // first 32 feed the AES-256 key, last 16 feed the CTR IV.
        byte[] jvm_entropy = new byte[48];

        if (Helpers.CSPRNG_GENERATOR != null) {
            Helpers.CSPRNG_GENERATOR.nextBytes(jvm_entropy);
        }

        this.local_hand_seed = jvm_entropy;

        if (GameFrame.getInstance().isPartida_local()) {
            // Espera a que todos los humanos conectados envíen HAND_READY. Deadline de PROGRESO
            // PAUSE-AWARE (HAND_READY_PROGRESS_TIMEOUT_MS): antes NO había timeout y un peer que contesta
            // PING pero no manda HAND_READY congelaba el arranque de la mano para SIEMPRE. Ahora, si un peer
            // retiene HAND_READY con la timba EN MARCHA más allá del tope, se le expulsa y la mesa sigue. El
            // reloj NO corre mientras la timba está PAUSADA (un peer que calla porque el juego está en pausa
            // nunca se expulsa) y el tope es enorme, así que un cliente simplemente lento tampoco. Un peer
            // caído de verdad sale por isExit() (socket muerto), no por este deadline.
            //
            // Se espera a TODOS los humanos no exitados, incluidos joiners pasivos
            // (calentando) y espectadores (bust). Aunque no participen de la
            // cascada SRA ni de la cadena de hash, su Crupier sigue corriendo
            // NUEVA_MANO y la rama cliente de este mismo readyForNextHand envia
            // HAND_READY incondicionalmente, asi que el HAND_READY llega siempre.
            // No filtrarlos garantiza que el host nunca arranque la mano siguiente
            // antes de que un peer pasivo haya cerrado su mano previa.
            // La comprobación de "todos listos" va DENTRO del synchronized: si
            // quedara fuera, un HAND_READY podía llegar (setNew_hand_ready +
            // notifyAll) justo entre el chequeo y el wait y se perdía la
            // notificación, durmiendo el NEW_HAND_READY_WAIT completo (~1s) aunque
            // el peer ya estuviera listo. La expulsión al vencer el deadline se hace FUERA del synchronized
            // (markExitAndNotify anida otros monitores; se evita anidarlos bajo lock_nueva_mano).
            boolean allReady = false;
            while (!allReady && !isFin_de_la_transmision()) {
                Participant expel = null;
                synchronized (lock_nueva_mano) {
                    long deadlineMs = System.currentTimeMillis() + HAND_READY_PROGRESS_TIMEOUT_MS;
                    while (!isFin_de_la_transmision()) {
                        boolean holdDeadline = false;
                        try {
                            // Congela el deadline mientras la timba esté PAUSADA o haya CUALQUIER peer en
                            // timeout (reconexión). Un peer legítimo puede tardar en confirmar HAND_READY
                            // porque está reconectando o rehaciendo un RECOVER, no por retener nada.
                            holdDeadline = GameFrame.getInstance().isTimba_pausada() || isSomePlayerTimeout();
                        } catch (Exception ignored) {
                        }
                        if (holdDeadline) {
                            deadlineMs = System.currentTimeMillis() + HAND_READY_PROGRESS_TIMEOUT_MS;
                        }
                        Participant stalling = null;
                        for (Map.Entry<String, Participant> entry : GameFrame.getInstance().getParticipantes().entrySet()) {
                            Participant p = entry.getValue();
                            if (p != null && !p.getNick().equals(GameFrame.getInstance().getNick_local())
                                    && !p.isCpu() && !p.isExit() && p.getNew_hand_ready() <= this.conta_mano) {
                                stalling = p;
                                break;
                            }
                        }
                        if (stalling == null) {
                            allReady = true;
                            break;
                        }
                        long remaining = deadlineMs - System.currentTimeMillis();
                        if (remaining <= 0) {
                            expel = stalling; // se expulsa FUERA del lock
                            break;
                        }
                        try {
                            lock_nueva_mano.wait(Math.min(NEW_HAND_READY_WAIT, Math.max(1, remaining)));
                        } catch (InterruptedException ex) {
                        }
                    }
                }
                if (expel != null) {
                    LOGGER.log(Level.SEVERE,
                            "ZERO-TRUST DoS: peer {0} withheld HAND_READY past {1}ms (game running, answering PING) — expelling, table continues",
                            new Object[]{expel.getNick(), HAND_READY_PROGRESS_TIMEOUT_MS});
                    warnMaliciousPeer(expel.getNick(), "zero_trust.peer_hand_ready_withheld");
                    expel.markExitAndNotify("withheld HAND_READY (progress deadline)");
                    try {
                        expel.socketClose();
                    } catch (Exception ignored) {
                    }
                    // El outer while re-entra: el expulsado ya cuenta como exit y se re-evalúa el resto.
                }
            }

            // Host ordena a todos que pueden empezar a procesar la cascada SRA.
            broadcastGAMECommandFromServer("START_SRA_CASCADE", null, true);

        } else {
            // Cliente avisa de que está listo para la mano actual y espera la
            // señal del host SIN timeout. Si el host se cae, isFin_de_la_transmision
            // o el socket reader lo detectarán por su cuenta.
            this.sendGAMECommandToServer("HAND_READY#" + String.valueOf(this.conta_mano + 1));

            boolean serverCommitted = false;
            do {
                synchronized (this.getReceived_commands()) {
                    java.util.ArrayList<String> rejected = new java.util.ArrayList<>();
                    while (!serverCommitted && !this.getReceived_commands().isEmpty()) {
                        String comando = this.received_commands.poll();
                        String[] partes = comando.split("#");
                        if (partes.length >= 3 && partes[2].equals("START_SRA_CASCADE")) {
                            serverCommitted = true;
                        } else {
                            rejected.add(comando);
                        }
                    }
                    if (!rejected.isEmpty()) {
                        this.getReceived_commands().addAll(rejected);
                    }
                }
                if (!serverCommitted && !isFin_de_la_transmision()) {
                    synchronized (this.getReceived_commands()) {
                        try {
                            this.getReceived_commands().wait(WAIT_QUEUES);
                        } catch (InterruptedException ex) {
                        }
                    }
                }
            } while (!serverCommitted && !isFin_de_la_transmision());
        }
    }

    public void RABBIT_HANDLER(String nick, int conta_rabbit) {

        Helpers.threadRun(() -> {

            synchronized (lock_rabbit) {

                rabbit_players.put(nick, true); // Lo ponemos en PENDING (para que el server pueda llevar la cuenta de
                // todos los que fueron procesados).

                if (nick.equals(GameFrame.getInstance().getLocalPlayer().getNickname())) {
                    destaparRabbitCards();
                }

                Player jugador = nick2player.get(nick);

                if (jugador != null) {

                    if (jugador instanceof RemotePlayer) {
                        RemotePlayer rp = (RemotePlayer) jugador;

                        Helpers.threadRun(() -> {

                            Helpers.GUIRunAndWait(() -> {

                                rp.setNotifyRabbitLabel();
                                rp.getChat_notify_label().setVisible(true);

                            });

                            synchronized (rp.getChat_notify_label()) {
                                Helpers.pausar(RABBIT_LABEL_TIMEOUT);

                                Helpers.GUIRun(() -> {

                                    rp.getChat_notify_label().setVisible(false);

                                });

                                rp.getChat_notify_label().notifyAll();
                            }

                        });

                    }

                    double stack = jugador.getStack();
                    double coste_rabbit = 0;

                    synchronized (getLock_contabilidad()) {
                        if (GameFrame.RABBIT_HUNTING == 2 && conta_rabbit > 1) {
                            coste_rabbit = ciega_pequeña;
                            if (Helpers.doubleSecureCompare(stack, coste_rabbit) >= 0) {
                                bote_sobrante += coste_rabbit;
                                jugador.setStack(stack - coste_rabbit);
                            } else {
                                coste_rabbit = 0f;
                            }
                        } else if (GameFrame.RABBIT_HUNTING == 3) {
                            if (conta_rabbit == 2) {
                                coste_rabbit = ciega_pequeña;
                                if (Helpers.doubleSecureCompare(stack, coste_rabbit) >= 0) {
                                    bote_sobrante += coste_rabbit;
                                    jugador.setStack(stack - coste_rabbit);
                                } else {
                                    coste_rabbit = 0f;
                                }
                            } else if (conta_rabbit > 2) {
                                coste_rabbit = ciega_grande;
                                if (Helpers.doubleSecureCompare(stack, coste_rabbit) >= 0) {
                                    bote_sobrante += coste_rabbit;
                                    jugador.setStack(stack - coste_rabbit);
                                } else {
                                    coste_rabbit = 0f;
                                }
                            }
                        }
                    }

                    GameFrame.getInstance().getRegistro().print(nick + " " + Translator.translate("rabbit.solicito_rabbit_hunting")
                            + " (" + Helpers.money2String(coste_rabbit) + ")");

                    if (nick.equals(GameFrame.getInstance().getLocalPlayer().getNickname())) {
                        // Si es una petición local calculamos mejor mano hipotética
                        ArrayList<Card> cartas = new ArrayList<>();

                        for (Card carta_comun : GameFrame.getInstance().getCartas_comunes()) {

                            if (!carta_comun.isTapada()) {
                                cartas.add(carta_comun);
                            }
                        }

                        GameFrame.getInstance().getRegistro()
                                .print(Translator.translate("rabbit.rabbit_hunting_cartas_comunitarias")
                                        + Card.collection2String(cartas));

                        cartas = GameFrame.getInstance().getLocalPlayer().getHoleCards();

                        GameFrame.getInstance().getRegistro()
                                .print(Translator.translate("rabbit.rabbit_hunting_tu_mano_repartida")
                                        + Card.collection2String(cartas));

                        for (Card carta_comun : GameFrame.getInstance().getCartas_comunes()) {

                            if (!carta_comun.isTapada()) {
                                cartas.add(carta_comun);
                            }
                        }

                        Hand jugada = new Hand(cartas);

                        GameFrame.getInstance().getLocalPlayer().setRabbitJugada(jugada.getName());

                        GameFrame.getInstance().getRegistro()
                                .print(Translator.translate("rabbit.rabbit_hunting_mejor_hipotetica_jugada")
                                        + Card.collection2String(jugada.getWinners()) + " (" + jugada.getName() + ")");

                    }

                    // Avisamos al server o al resto de jugadores si procede
                    String comando;
                    try {
                        comando = "RABBIT#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")) + "#"
                                + String.valueOf(conta_rabbit);

                        if (GameFrame.getInstance().isPartida_local()) {
                            // Si somos el sevidor re-enviamos el comando a todo el mundo.
                            broadcastGAMECommandFromServer(comando, nick);

                        } else if (nick.equals(GameFrame.getInstance().getLocalPlayer().getNickname())) {
                            // Si somos cliente enviamos comando al server en caso de que fuéramos nosotros
                            // los que pedimos las RABIT.
                            sendGAMECommandToServer(comando);
                        }

                    } catch (Exception ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    }
                }

                rabbit_players.put(nick, false); // Petición Rabbit procesada

                lock_rabbit.notifyAll();

            }

        });

    }

    public Object getIwtsth_lock() {
        return lock_iwtsth;
    }

    public void IWTSTH_HANDLER(String iwtsther) {
        Helpers.threadRun(() -> {
            synchronized (lock_iwtsth) {
                if (iwtsthing || iwtsth) {
                    return;
                }
                iwtsthing = true;
                iwtsth = true;

                if (iwtsth_requests.containsKey(iwtsther)) {
                    iwtsth_requests.put(iwtsther, (int) iwtsth_requests.get(iwtsther) + 1);
                } else {
                    iwtsth_requests.put(iwtsther, 1);
                }

                int conta_iwtsth = (int) iwtsth_requests.get(iwtsther);

                GameFrame.getInstance().getRegistro().print(
                        iwtsther + " " + Translator.translate("iwtsth.solicita_iwtsth") + String.valueOf(conta_iwtsth) + ")");

                Helpers.GUIRunAndWait(() -> {
                    if (GameFrame.getInstance().getLocalPlayer().isBotonMostrarActivado()) {
                        GameFrame.getInstance().getLocalPlayer().getPlayer_allin_button().setEnabled(false);
                    }
                    Helpers.barraIndeterminada(GameFrame.getInstance().getBarra_tiempo());
                });

                if (GameFrame.getInstance().isPartida_local()) {
                    try {
                        broadcastGAMECommandFromServer(
                                "IWTSTH#" + Base64.getEncoder().encodeToString(iwtsther.getBytes("UTF-8")), null);
                    } catch (UnsupportedEncodingException ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    }
                }

                if (GameFrame.cinematicasOn()) {
                    Helpers.GUIRun(() -> {
                        try {
                            GifAnimationDialog gif_dialog = new GifAnimationDialog(GameFrame.getInstance(), true,
                                    new ImageIcon(getClass().getResource("/cinematics/misc/iwtsth.gif")),
                                    Helpers.getGIFFramesCount(
                                            getClass().getResource("/cinematics/misc/iwtsth.gif").toURI().toURL()));
                            gif_dialog.setLocationRelativeTo(gif_dialog.getParent());
                            gif_dialog.setVisible(true);
                        } catch (URISyntaxException | IOException | ImageProcessingException ex) {
                            LOGGER.log(Level.SEVERE, null, ex);
                        }
                    });
                    Helpers.pausar(500);
                    Audio.playWavResourceAndWait("misc/iwtsth.wav");
                } else {
                    Audio.playWavResourceAndWait("misc/iwtsth.wav");
                }

                if (GameFrame.getInstance().isPartida_local()) {
                    if (GameFrame.getInstance().getLocalPlayer().getNickname().equals(iwtsther)
                            || Helpers.mostrarMensajeInformativoSINO(GameFrame.getInstance(),
                                    iwtsther + Translator.translate("iwtsth.solicita_iwtsth")
                                    + String.valueOf(conta_iwtsth)
                                    + Translator.translate("ui.autorizamos"),
                                    new ImageIcon(Init.class.getResource("/images/action/robot.png"))) == 0) {
                        IWTSTH_SHOW(iwtsther, true);
                    } else {
                        IWTSTH_SHOW(iwtsther, false);
                    }
                }
            }
        });
    }

    public void IWTSTH_SHOW(String iwtsther, boolean authorized) {
        // NOTA: ya no gateamos con iwtsthing. El guard original silenciaba el flujo si iwtsthing era false
        // (p.ej. cuando IWTSTH_HANDLER no llegó a correr en un candidato remoto por race con show_time/pausaConBarra).
        // El caller (IWTSTH_HANDLER del host tras autorizar, o WaitingRoomFrame.case "IWTSTHSHOW" en clientes) ya
        // ha decidido que esto debe ejecutarse — no debe depender de un flag que pudo no setearse por timing.
        Helpers.threadRun(() -> {
            synchronized (lock_iwtsth) {
                {

                        // 1. If we are the Server Host, broadcast the verdict to all clients
                        if (GameFrame.getInstance().isPartida_local()) {
                            try {
                                broadcastGAMECommandFromServer("IWTSTHSHOW#" + Base64.getEncoder().encodeToString(iwtsther.getBytes("UTF-8")) + "#" + String.valueOf(authorized), null, true);
                            } catch (Exception ex) {
                            }
                        }

                        if (authorized) {
                            // El servidor no puede descifrar las cartas de los remotos (no tiene sus llaves SRA),
                            // así que solo autoriza la petición y cada cliente confiesa sus propias cartas.

                            // A) Local Player: si soy candidato (perdedor que no ha mostrado en el showdown) confieso.
                            // NOTAS:
                            //   - NO chequeamos isBoton_mostrar / isBotonMostrarActivado: esos flags reflejan el
                            //     estado del botón "Mostrar" voluntario y dependen de que IWTSTH_HANDLER haya
                            //     deshabilitado el botón (race con timing).
                            //   - NO chequeamos getHoleCard1().isTapada(): el LocalPlayer SIEMPRE ve sus propias
                            //     cartas (destapar() se llama en el reparto, línea 4320, y Card.tapar() nunca se
                            //     invoca en el código), así que isTapada() es false en el propio jugador desde el
                            //     reparto. Ese check es la perspectiva externa (otros viendo al candidato), no la
                            //     del propio jugador, y si lo dejamos aquí el SHOWCARDS NUNCA se envía.
                            //   - isMuestra() distingue "auto-show en showdown" (true, no hay que confesar nada)
                            //     vs "auto-muck/IWTSTH candidate" (false, hay que confesar).
                            LocalPlayer local = GameFrame.getInstance().getLocalPlayer();
                            if (local.isLoser() && !local.isMuestra()) {
                                showAndBroadcastPlayerCards(local.getNickname());
                                // Marcamos al jugador como "ya mostrado" tras la confesión forzada del IWTSTH.
                                // Sin esto, el cleanup de abajo vería isBoton_mostrar()=true && !isBotonMostrarActivado()
                                // && !isMuestra() y re-habilitaría el botón "Mostrar" voluntario, lo cual no tiene
                                // sentido — las cartas YA se mostraron forzosamente.
                                local.setMuestra(true);
                                local.desactivar_boton_mostrar();
                            }

                            // B) Bots: Since they live in the Host's memory, the Server Host forces them to show
                            if (GameFrame.getInstance().isPartida_local()) {
                                for (RemotePlayer rp : GameFrame.getInstance().getTapete().getRemotePlayers()) {
                                    Participant p = GameFrame.getInstance().getParticipantes().get(rp.getNickname());
                                    if (p != null && p.isCpu() && rp.isIwtsthCandidate() && rp.getHoleCard1().isTapada()) {
                                        showAndBroadcastPlayerCards(rp.getNickname());
                                    }
                                }
                            }
                        } else {
                            // If denied, inform the UI and register rejection timestamp for anti-flood
                            GameFrame.getInstance().getRegistro().print(Translator.translate("iwtsth.el_servidor_ha_denegado_la") + " " + iwtsther);
                            if (GameFrame.cinematicasOn()) {
                                Helpers.GUIRun(() -> {
                                    try {
                                        GifAnimationDialog gif_dialog = new GifAnimationDialog(GameFrame.getInstance(), true, new ImageIcon(getClass().getResource("/cinematics/misc/iwtsth_no.gif")), Helpers.getGIFFramesCount(getClass().getResource("/cinematics/misc/iwtsth_no.gif").toURI().toURL()));
                                        gif_dialog.setLocationRelativeTo(gif_dialog.getParent());
                                        gif_dialog.setVisible(true);
                                    } catch (URISyntaxException | IOException | ImageProcessingException ex) {
                                    }
                                });
                            }
                            if (GameFrame.getInstance().getLocalPlayer().getNickname().equals(iwtsther)) {
                                this.last_iwtsth_rejected = System.currentTimeMillis();
                            }
                        }
                    }
                }

                // Cleanup UI:
                // - Restauramos el botón de mostrar si procede
                // - Restauramos la barra al estado correcto de pausaConBarra (no solo setIndeterminate(false),
                //   que dejaría la barra con max=1/val=1 visualmente "al máximo, sin moverse").
                Helpers.GUIRunAndWait(() -> {
                    if (GameFrame.getInstance().getLocalPlayer().isBoton_mostrar() && !GameFrame.getInstance().getLocalPlayer().isBotonMostrarActivado() && !GameFrame.getInstance().getLocalPlayer().isMuestra()) {
                        GameFrame.getInstance().getLocalPlayer().getPlayer_allin_button().setEnabled(true);
                    }
                });
                // Restaurar la barra al valor restante de pausaConBarra para que el bucle pueda
                // seguir decrementando correctamente sin verse "atascada al máximo".
                setTiempo_pausa(getTiempoPausa());

                // Release the lock to let the game proceed
                synchronized (lock_iwtsth) {
                    iwtsth = true;
                    iwtsthing = false;
                    iwtsthing_request = false;
                    lock_iwtsth.notifyAll();
                }
                // Despertamos también al loop de pausaConBarra que está dormido en lock_pausa_barra.wait(1000)
                // para que reanude su decremento sin tener que esperar el timeout.
                synchronized (lock_pausa_barra) {
                    lock_pausa_barra.notifyAll();
                }
            });
    }

    public boolean isIwtsthing_request() {
        return iwtsthing_request;
    }

    public void IWTSTH_REQUEST(String iwtsther) {

        if (this.show_time && (this.last_iwtsth_rejected == null
                || System.currentTimeMillis() - this.last_iwtsth_rejected > IWTSTH_ANTI_FLOOD_TIME)) {

            iwtsthing_request = true;

            Helpers.barraIndeterminada(GameFrame.getInstance().getBarra_tiempo());

            if (!GameFrame.getInstance().isPartida_local()) {
                this.sendGAMECommandToServer("IWTSTH");
            } else {
                IWTSTH_HANDLER(iwtsther);
            }

        } else {
            Helpers.mostrarMensajeError(GameFrame.getInstance(),
                    Translator.translate("ui.tienes_que_esperar")
                    + Helpers.seconds2FullTime(Math.round(((float) (IWTSTH_ANTI_FLOOD_TIME
                            - (System.currentTimeMillis() - this.last_iwtsth_rejected))) / 1000))
                    + Translator.translate("iwtsth.para_volver_a_solicitar_iwtsth"));
        }
    }

    public boolean isIWTSTH4LocalPlayerAuthorized() {

        return flop_players.contains(GameFrame.getInstance().getLocalPlayer());
    }

    public void destaparRabbitCards() {
        synchronized (lock_rabbit) {
            boolean hay_rabbits_tapadas = false;

            for (Card carta : GameFrame.getInstance().getCartas_comunes()) {
                if (carta.isRabbitTapada()) {
                    hay_rabbits_tapadas = true;
                    break;
                }
            }

            if (hay_rabbits_tapadas) {
                Audio.playWavResource("misc/uncover.wav", false);
                for (Card carta : GameFrame.getInstance().getCartas_comunes()) {
                    carta.destaparRabbit();
                    // Hay que destapar la carta subyacente cuando el rabbit desaparece.
                    if (carta.getCartaComoEntero() >= 0) {
                        carta.destapar(false);
                    }
                }
            }
        }
    }

    private void setPotBackground(Color color) {
        Helpers.GUIRun(() -> {
            GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setOpaque(false);
            GameFrame.getInstance().getTapete().getCommunityCards().getPot_panel().setOpaque(true);
            GameFrame.getInstance().getTapete().getCommunityCards().getPot_panel().setBackground(color);
        });
    }

    private boolean NUEVA_MANO() {

        // Aún no se han repartido las comunitarias de esta mano: el overlay de coste
        // de igualar permanece oculto hasta que repartir() las coloque.
        this.community_cards_dealt = false;

        this.local_sra_lock = null;
        this.local_sra_unlock = null;
        this.local_sra_lock_community = null;
        this.local_sra_unlock_community = null;
        this.local_mega_packet = null;

        this.active_crypto_ring = null;
        this.game_recovered = 0;

        Helpers.GUIRun(() -> {

            GameFrame.getInstance().getTapete().getCommunityCards().getPot_panel().setOpaque(false);
            GameFrame.getInstance().getTapete().getCommunityCards().getPot_label()
                    .setHorizontalAlignment(JLabel.LEADING);
            GameFrame.getInstance().getTapete().getCommunityCards().restoreBetLabelicon();
            GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setForeground(
                    GameFrame.getInstance().getTapete().getCommunityCards().getBet_label().getForeground());
            GameFrame.getInstance().getTapete().getCommunityCards().setPotTextImmediate("---");
            GameFrame.getInstance().getTapete().getCommunityCards().getHand_label().setVisible(false);
            GameFrame.getInstance().getTapete().getCommunityCards().getBet_label().setVisible(false);

            Helpers.barraIndeterminada(GameFrame.getInstance().getBarra_tiempo());

            if (!GameFrame.getInstance().isPartida_local()) {
                GameFrame.getInstance().getExit_menu().setEnabled(false);
            }
        });

        if (!GameFrame.RECOVER) {
            Helpers.cleanHandCrupierTempFiles(this.sqlite_id_game);
        }

        readyForNextHand();

        if (this.ciegas_update != null) {
            synchronized (lock_ciegas) {
                GameFrame.CIEGAS_DOUBLE = (int) ciegas_update[2];
                GameFrame.CIEGAS_DOUBLE_TYPE = (int) ciegas_update[3];
                this.ciega_pequeña = (double) ciegas_update[0];
                this.ciega_grande = (double) ciegas_update[1];
                this.ciegas_update = null;
                sqlUpdateGameDoubleBlinds();
                GameFrame.getInstance().getCrupier().actualizarContadoresTapete();
                GameFrame.getInstance().getRegistro()
                        .print(Translator.translate("blinds.la_configuracion_de_las_ciegas"));
                Helpers.threadRun(() -> {
                    Helpers.mostrarMensajeInformativo(GameFrame.getInstance(),
                            Translator.translate("blinds.la_configuracion_de_las_ciegas"),
                            new ImageIcon(Init.class.getResource("/images/ciegas_big.png")));
                });
            }
        }

        rabbit_players.clear();
        this.iwtsth = false;
        this.iwtsthing = false;
        this.iwtsthing_request = false;
        this.sqlite_id_hand = -1;
        this.conta_accion = 0;
        this.tot_acciones_recuperadas = 0;
        this.acciones_locales_recuperadas.clear();

        for (Player jugador : GameFrame.getInstance().getJugadores()) {
            if (!jugador.isExit() && jugador.isSpectator() && (Helpers.doubleSecureCompare(0f, jugador.getStack()) < 0
                    || rebuy_now.containsKey(jugador.getNickname()))) {
                jugador.unsetSpectator();
                if (rebuy_now.containsKey(jugador.getNickname())) {
                    jugador.setSpectatorBB(true);
                }
            }
        }

        // nicks_permutados tiene que reflejar a los jugadores recién integrados.
        ArrayList<String> nicksList = new ArrayList<>(Arrays.asList(this.nicks_permutados));
        boolean nicksChanged = false;
        for (Player jugador : GameFrame.getInstance().getJugadores()) {
            if (!jugador.isCalentando() && !nicksList.contains(jugador.getNickname())) {
                nicksList.add(jugador.getNickname());
                nicksChanged = true;
            }
        }
        if (nicksChanged) {
            this.nicks_permutados = nicksList.toArray(new String[0]);
            this.update_game_seats = true;
            LOGGER.log(Level.INFO, "Updated seating permutations to inject warm-up players into the crypto ring.");
        }

        for (Player jugador : GameFrame.getInstance().getJugadores()) {
            if (jugador.isActivo()) {
                jugador.getHoleCard1().resetearCarta(false);
                jugador.getHoleCard2().resetearCarta(false);
                jugador.resetGUI();
            }
        }

        for (Card carta : GameFrame.getInstance().getCartas_comunes()) {
            carta.resetearCarta(false);
        }

        setContaManoLocal(this.conta_mano + 1);

        if (GameFrame.MANOS == conta_mano && GameFrame.getInstance().isPartida_local()) {
            Helpers.GUIRun(GameFrame.getInstance().getTapete().getCommunityCards()::hand_label_left_click);
        }

        Bot.BOT_COMMUNITY_CARDS.makeEmpty();
        // Cabecera de mano enmarcada (en vez de la antigua tira de asteriscos).
        GameFrame.getInstance().getRegistro().print(
                Helpers.framedTitle(Translator.translate("game.mano_2") + " (" + this.conta_mano + ")"));

        // Snapshot de los portadores ANTES de rotar, para animar el deslizamiento
        // de las fichas (dealer/ciegas) del asiento anterior al nuevo. En la
        // primera mano son null → las fichas salen del centro de la mesa.
        final String prev_dealer_nick = this.dealer_nick;
        final String prev_sb_nick = this.small_blind_nick;
        final String prev_bb_nick = this.big_blind_nick;

        if (!GameFrame.RECOVER) {
            this.setPositions();
        }

        if (GameFrame.isRECOVER() && GameFrame.getInstance().isPartida_local()) {
            resyncRECOVERGLOBALS();
        }

        this.badbeat = false;
        this.jugada_ganadora = 0;
        this.perdedores.clear();
        setStreetLocal(PREFLOP);
        this.flop_revealed = false;
        this.turn_revealed = false;
        this.river_revealed = false;
        this.sra_unlock_tags_served.clear();
        this.cartas_resistencia = false;
        this.destapar_resistencia = false;

        // El run-out terminó: vuelve a permitir cambiar RUN_IT_TWICE (lo congeló el
        // arranque del run-out del all-in).
        GameFrame.RUN_IT_TWICE_LOCKED = false;

        this.run_it_twice_side_b = false;

        this.rit_agreed = false;

        this.rit_vote_done = false;

        this.rit_allin_street = -1;

        this.rit_side_a_runout_cards.clear();

        this.rit_pot_board_tag = null;

        // Defensivo: si una mano anterior abortó entre los dos boards con el SQL
        // del showdown silenciado, lo reactivamos al empezar la mano nueva.
        this.rit_suppress_showdown_sql = false;
        // Defensivo por el mismo motivo: tras un MISDEAL no hay más manos en
        // este Crupier (fin_de_la_transmision queda izado), pero si alguna vez
        // lo hubiera, una mano nueva nunca debe arrancar marcada como anulada.
        this.mano_anulada = false;
        this.ultimo_raise = 0f;
        this.partial_raise_cum = 0f;
        this.conta_raise = 0;
        this.conta_bet = 0;
        this.straddle_posted = false;
        this.straddle_utg_nick = null;
        // Se repone del fósil en la mano recuperada (recuperarDatosClavePartida, más
        // abajo); en mano fresca queda false (la decisión la toma resolveVoluntaryStraddle).
        this.straddle_recovered_posted = false;
        this.straddle_local_cards_deferred = false;

        synchronized (getLock_contabilidad()) {
            if (Helpers.doubleSecureCompare(0f, this.bote_sobrante) < 0) {
                if (GameFrame.SONIDOS_CHORRA && GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {
                    Audio.playWavResource("misc/indivisible.wav");
                }
                Audio.playWavResource("misc/cash_register.wav");
                GameFrame.getInstance().getRegistro()
                        .print(Translator.translate("game.bote_sobrante") + " -> " + Helpers.money2String(bote_sobrante));
            }

            this.bote_total = Math.max(0f, this.bote_sobrante);
        }
        this.bote = new HandPot(0f);
        this.beneficio_bote_principal = null;

        HashSet<String> rebuys_about_to_apply = new HashSet<>();
        for (Map.Entry<String, Integer> e : rebuy_now.entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) {
                rebuys_about_to_apply.add(e.getKey());
            }
        }

        // Recompra animada (con caja registradora) ANTES de aplicarla al modelo en
        // el bucle de abajo: el contador rueda el stack hasta el valor final y la
        // barrera deja terminar el conteo antes de que la mano avance. reComprar
        // aterriza luego en ese mismo valor sin repetir el sonido (gate compartido).
        animateRebuyStacks(rebuys_about_to_apply);

        for (Player jugador : GameFrame.getInstance().getJugadores()) {
            if (jugador.isActivo()) {
                jugador.nuevaMano();
            }
        }

        for (String nick : rebuys_about_to_apply) {
            if (!rebuy_now.containsKey(nick)) {
                rebuy_counts.merge(nick, 1, Integer::sum);
            }
        }

        this.rebuy_now.clear();

        Helpers.GUIRun(() -> {
            if (GameFrame.getInstance().getRebuy_now_menu().isEnabled()) {
                GameFrame.getInstance().getRebuy_now_menu().setSelected(false);
                GameFrame.getInstance().getRebuy_now_menu().setBackground(null);
                GameFrame.getInstance().getRebuy_now_menu().setOpaque(false);
                Helpers.TapetePopupMenu.REBUY_NOW_MENU.setSelected(false);
                Helpers.TapetePopupMenu.REBUY_NOW_MENU.setBackground(null);
                Helpers.TapetePopupMenu.REBUY_NOW_MENU.setOpaque(false);
            }
        });

        saltar_primera_mano = false;

        if (GameFrame.isRECOVER()) {
            GameFrame.getInstance().getRegistro().print(Translator.translate("game.recuperando_timba"));
            try {
                recuperarDatosClavePartida();
            } finally {
                // Garantizar reset incluso si recuperarDatosClavePartida lanza
                // o sale temprano por algún return interno. De lo contrario
                // GameFrame.RECOVER queda stale y la siguiente partida fresh
                // entra de nuevo en modo recovery — el host sin datos válidos
                // por SQL y el cliente esperando RECOVERDATA del host (que no
                // los va a enviar porque para él NO es recovery). Cuelgue
                // garantizado. setRECOVER es idempotente.
                if (GameFrame.RECOVER) {
                    GameFrame.setRECOVER(false);
                }
                // Tras recovery con saltar=true (mano fresh, sin replay), el
                // setPositions de NUEVA_MANO mas arriba se skipeo (estaba bajo
                // !GameFrame.RECOVER y RECOVER aun era true en ese punto).
                // Sin esta llamada explicita aqui, dealer/sb/bb quedan null
                // y repartir() peta. En HOST calcularPosiciones asigna nicks
                // desde nicks_permutados y broadcast POSITIONS; en CLIENTE
                // recibirPosiciones lee POSITIONS de la queue del Crupier.
                if (saltar_primera_mano) {
                    // Limpiar refs cripto que recuperarDatosClavePartida pudo
                    // haber repoblado del fosil viejo. Sin esto, la cascade
                    // NUEVA del host disparaba DECK_CASCADE_REQ en cliente y
                    // el handler veia hasMegaPacket()=true (de la mano vieja)
                    // -> lockdown falso positivo "MEGAPACKET already locked".
                    //
                    // Issue#9 root cause: el handler DECK_CASCADE_REQ corre en
                    // threadRun async (WaitingRoomFrame:2110-2122) y setea
                    // local_sra_lock_community + local_sra_unlock_community
                    // DIRECTAMENTE en el Crupier. Cuando el host avanza rapido
                    // tras su propio recovery y envia DECK_CASCADE_REQ antes de
                    // que este bloque corra, el handler async genera los scalars
                    // validos para la nueva mano — pero este cleanup defensivo
                    // los SOBREESCRIBIA a null. Luego el siguiente
                    // DECK_ROTATION_REQ rechazaba silencioso (community lock
                    // null) y el host se quedaba 60-90s sin respuesta -> MISDEAL.
                    //
                    // Solo limpiamos local_mega_packet y active_crypto_ring (los
                    // que el handler chequea via hasMegaPacket()). Los scalars
                    // pocket (local_sra_lock / local_sra_unlock) ya estan null
                    // desde NUEVA_MANO arriba y el handler nunca los pone en el
                    // Crupier (vive en Participant.sra_unlock); los scalars
                    // community NO se tocan aqui — si el handler ya los genero
                    // para la nueva mano, no los pisamos; si no, siguen null
                    // desde NUEVA_MANO y el handler los rellenara cuando llegue.
                    this.local_mega_packet = null;
                    this.active_crypto_ring = null;
                    this.local_original_cards = new byte[2];
                    // setPositions solo en el fresh-start genuino (game_recovered==0):
                    // host calcula y broadcastea POSITIONS, clientes esperan POSITIONS.
                    // El joiner pasivo observer (saltar=true + game_recovered=1) ya
                    // tiene dealer/sb/bb/utg desde el map de recovery procesado en
                    // recuperarDatosClavePartida (donde el if ahora gateado por
                    // game_recovered==1 calcula utg localmente sin esperar POSITIONS).
                    // Sin este gate, el observer caia en recibirPosiciones a timeout.
                    //
                    // recovery_positions_set evita la DOBLE rotación: si
                    // recuperarDatosClavePartida ya llamó setPositions (rama
                    // mano-fresh con saltar=true y sin mano-en-curso del host),
                    // volver a llamarlo aquí avanzaba ciegas/dealer un segundo
                    // asiento y dejaba un botón de dealer fantasma (el del primer
                    // reparto, pintado por refreshPos y nunca limpiado). Solo
                    // hace falta cuando recuperar NO rotó (host con mano-en-curso
                    // pero un jugador del preflop se fue -> rama if sin setPositions).
                    if (this.game_recovered == 0 && !this.recovery_positions_set) {
                        this.setPositions();
                    }
                }
                // Rescate de spectator: tras saltar=true y balance vacio del
                // recovery, los players quedan marcados spectator desde el
                // INIT (warming-up). Sin este unsetSpectator, isActivo()=false
                // -> getJugadoresActivos()=0 -> NUEVA_MANO no arranca, timba
                // muere. Solo rescatamos players con stack > 0.
                //
                // Gateado por game_recovered==0: el rescate sirve para el caso
                // genuino "no hay replay en ningun peer" (fosil corrupto del
                // host o hand_end!=0). Pero un joiner pasivo que llega antes
                // de un recover ve saltar=true porque no tiene fosil propio,
                // aunque el host SI esta replayando — recuperarDatosClavePartida
                // marca game_recovered=1 en ese sub-caso. Sacarlo del calentando
                // aqui le forzaba a entrar como jugador activo en la mano
                // recuperada, llamar recibirMisCartas esperando POCKET_CARDS
                // que nunca llegan (el host no los reparte en game_recovered=1)
                // y bloquear el hilo principal. La siguiente NUEVA_MANO
                // (game_recovered=0 ya, mano fresca) le rescata en linea 4214
                // de forma natural y entra normal.
                if (saltar_primera_mano && this.game_recovered == 0) {
                    try {
                        for (Player j : GameFrame.getInstance().getJugadores()) {
                            if (j != null && !j.isExit() && j.isSpectator()
                                    && Helpers.doubleSecureCompare(0f, j.getStack()) < 0) {
                                j.unsetSpectator();
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        this.apuesta_actual = this.ciega_grande;

        // Straddle VOLUNTARIO (opción fija de 2x la ciega grande): el UTG decide a
        // ciegas, tras repartir, si lo pone (ver resolveVoluntaryStraddle, llamado
        // post-reparto). NO se postea aquí: es una decisión que viaja por la red
        // (host-driven, como el voto run-it-twice) y debe converger en todos los peers
        // antes del replay/ronda. En la mano RECUPERADA tampoco se pregunta: el host
        // repone la decisión original desde el fósil y la rebroadcasta — todo eso
        // también ocurre en resolveVoluntaryStraddle, así que el camino es uniforme.
        // Con STRADDLE off, getJugadoresActivos()<=2 o heads-up, resolveVoluntaryStraddle
        // es un no-op y el camino por defecto queda byte-idéntico.

        for (Player p : GameFrame.getInstance().getJugadores()) {
            if (p.isActivo()) {
                Bot.TRACKER_MEMORY.computeIfAbsent(p.getNickname(), k -> new Bot.OpponentTracker()).recordHandPlayed();
            }
        }

        if (getJugadoresActivos() > 1) {
            if (saltar_primera_mano) {
                // Recovery decided not to replay the previous hand (clean exit
                // with hand_end!=0, fossil corrupt, or a preflop player missing).
                // Force a brand-new hand record so actions don't get written
                // into the previous (already-closed) hand.
                this.sqlite_id_hand = -1;
            }
            if (this.sqlite_id_hand == -1) {
                sqlNewHand();
            }

            this.apuestas = 0f;

            for (Player jugador : GameFrame.getInstance().getJugadores()) {
                if (Helpers.doubleSecureCompare(0f, jugador.getBet()) < 0) {
                    this.apuestas += jugador.getBet();
                }
            }

            this.bote_total += this.apuestas;

            // Antes (opcion A: tradicional simetrico). Cada jugador activo postea el
            // ante (= ciega pequena) al bote como DINERO MUERTO. No cuenta como apuesta
            // a igualar (apuesta_actual sigue siendo la ciega grande): va por postAnte
            // (NO setBet), asi que no entra en la suma de getBet() y se anade directo a
            // bote_total. genSidePots/getTotal lo reparten bien porque keyan en getBote()
            // (el ante esta dentro). TAMBIEN en la mano RECUPERADA: el ante es dinero
            // muerto que el replay NO reproduce (no es accion), asi que se repone aqui
            // (corre tras refreshPos, antes del replay). Si ANTE esta off, no toca nada.
            if (GameFrame.ANTE) {
                double total_antes = 0f;
                for (Player jugador : GameFrame.getInstance().getJugadores()) {
                    if (jugador.isActivo()) {
                        // El ante vuela al bote: NO rueda su stack/bet en postAnte; se difiere
                        // y rollCountersToModel (al aterrizar su ficha) lo rueda junto al bote.
                        // Gate del vuelo (incluye game_recovered==0: el bloque recover ya corrió).
                        if (shouldDeferCountersToChip() && this.game_recovered == 0) {
                            jugador.setCounterRollDeferred(true);
                        }
                        total_antes += jugador.postAnte(this.ciega_pequeña);
                    }
                }
                this.bote_total += total_antes;
            }

            // Prepara la animacion de fichas-al-bote de las forzadas ANTES de mostrar
            // el bote: difiere su valor para que se vea incrementar al aterrizar (no
            // de golpe). Las fichas vuelan luego, tras rotar las fichas de posicion.
            prepareForcedBetsToPot();

            Helpers.GUIRun(() -> {
                GameFrame.getInstance().getTapete().getCommunityCards().getHand_label().setVisible(true);
                GameFrame.getInstance().getTapete().getCommunityCards().getBet_label().setVisible(true);
            });

            actualizarContadoresTapete();

            // Desliza las fichas de posición (dealer/ciegas) de su portador
            // anterior al nuevo, justo antes del barajado central. Bloquea hasta
            // que aterrizan. Respeta la opción de animación (camino sin animación
            // intacto) y se salta en RECOVER/fin de transmisión.
            animateChipRotation(prev_dealer_nick, prev_sb_nick, prev_bb_nick);

            // Tras rotar las fichas de posicion y ANTES del barajado: las forzadas
            // (ciegas/straddle, y si hay ante TODOS) vuelan al bote en una sola tanda,
            // identico a bet/call (vuelan + bote sube + flaseo amarillo). Bloquea hasta
            // aterrizar, como la rotacion de fichas de posicion.
            flyForcedBetsToPot();

            Object shuffle_lock = new Object();
            // barajando here means "SRA cascade still running, keep looping the shuffle
            // animation". The thread polls it after each complete GIF cycle (and after
            // each audio-only cycle in the fallback path).
            barajando = true;

            final boolean[] gif_thread_done = {false};

            Helpers.threadRun(() -> {
                // Abre (una vez) y reutiliza la linea de audio del barajado ANTES
                // de animar y fuera del EDT: arrancar el sonido en cada vuelta del
                // gif sera instantaneo, sin un open por ciclo que pueda llegar
                // tarde y quedarse mudo.
                if (!isFin_de_la_transmision()) {
                    Audio.preloadWav("misc/shuffle.wav");
                }

                URL url_icon = shuffleGifUrl();
                if (url_icon != null && GameFrame.repartoAnimOn()) {

                    // Motor pre-decodificado con catch-up también para el barajado:
                    // un único decode por baraja (normalmente ya caliente por el
                    // warm-up de arranque/cambio de baraja; el animador Toolkit
                    // re-decodificaba el GIF entero en CADA ciclo del bucle por el
                    // flush) y ciclos siempre de duración nominal aunque el timer
                    // de Windows vaya grueso. Si el GIF no se deja pre-decodificar
                    // (o excede el tope de RAM), la ruta legacy sigue intacta.
                    PreRenderedGif shuffle_anim = getShuffleAnim(url_icon);

                    Helpers.GUIRunAndWait(() -> {
                        GameFrame.getInstance().getTapete().getCommunityCards().setVisible(false);
                    });

                    if (shuffle_anim != null) {
                        // Bucle hasta que la cascada SRA termine, con mínimo 1 ciclo
                        // completo (el predicado solo se consulta al fin de ciclo) y
                        // audio re-disparado en cada ciclo, como el do-while legacy.
                        GameFrame.getInstance().getTapete().showCentralFramesLoop(shuffle_anim,
                                shuffle_anim.getWidth(), shuffle_anim.getHeight(),
                                "misc/shuffle.wav", SHUFFLE_AUDIO_STOP_FRAME, () -> barajando);
                    } else {
                        ImageIcon icon = new ImageIcon(url_icon);
                        // Loop the shuffle GIF until the SRA cascade finishes (min 1
                        // full pass thanks to the do-while). The audio is synced to
                        // each GIF pass with the pre-opened clip: start at the pass
                        // start, stop when the pass (showCentralImage) returns.
                        do {
                            Audio.playPreloadedWav("misc/shuffle.wav");
                            GameFrame.getInstance().getTapete().showCentralImage(icon, 0, 0, true, null, 0, 0);
                            Audio.stopPreloadedWav("misc/shuffle.wav");
                        } while (barajando && !isFin_de_la_transmision());
                    }

                    if (!isFin_de_la_transmision()) {
                        Helpers.GUIRunAndWait(() -> {
                            GameFrame.getInstance().getTapete().getCommunityCards().setVisible(true);
                        });
                    }
                } else if (!isFin_de_la_transmision()) {
                    // Sin gif de barajado (la baraja no trae shuffle.gif, o las animaciones
                    // estan desactivadas): ocultamos las comunitarias —igual que el camino
                    // del gif— y mostramos "BARAJANDO" centrado donde iria el gif, con el
                    // ancho del panel de comunitarias, mientras suena el barajado en bucle.
                    Helpers.GUIRunAndWait(() -> {
                        GameFrame.getInstance().getTapete().getCommunityCards().setVisible(false);
                        GameFrame.getInstance().getTapete().showShufflingText();
                    });
                    // playWavResourceAndWait blocks for the natural duration of the clip, so
                    // the do-while replays it back-to-back with no silence in between, while
                    // the "BARAJANDO" label stays up. Minimum 1 play guaranteed.
                    do {
                        Audio.playWavResourceAndWait("misc/shuffle.wav");
                    } while (barajando && !isFin_de_la_transmision());

                    // Ocultar el rotulo SIEMPRE es seguro (el barajado terminó); restaurar
                    // las comunitarias solo si no hay fin de transmisión (igual que el gif).
                    Helpers.GUIRunAndWait(() -> {
                        GameFrame.getInstance().getTapete().hideShufflingText();
                        if (!isFin_de_la_transmision()) {
                            GameFrame.getInstance().getTapete().getCommunityCards().setVisible(true);
                        }
                    });
                }
                synchronized (shuffle_lock) {
                    gif_thread_done[0] = true;
                    shuffle_lock.notifyAll();
                }
            });

            if (GameFrame.getInstance().isPartida_local() && this.game_recovered == 0) {

                // Si la cascada falla (alguien no responde), abortamos la inicialización
                if (!enviarCartasJugadoresRemotos()) {
                    barajando = false;
                    synchronized (shuffle_lock) {
                        while (!gif_thread_done[0]) {
                            try {
                                shuffle_lock.wait(1000);
                            } catch (InterruptedException ex) {
                                Helpers.logCooperativeCancellation(LOGGER, "shuffle wait (abort path)", ex);
                                break;
                            }
                        }
                    }

                    Audio.stopPreloadedWav("misc/shuffle.wav");

                    return false;
                }

                for (Player j : GameFrame.getInstance().getJugadores()) {
                    if (j != GameFrame.getInstance().getLocalPlayer()) {
                        j.ordenarCartas();
                    }
                }
            } else if (!GameFrame.getInstance().isPartida_local()
                    && !GameFrame.getInstance().getLocalPlayer().isCalentando() && this.game_recovered == 0) {
                cartas_locales_recibidas = recibirMisCartas();
            }

            // Cascade done: signal the loop to exit at the next cycle boundary.
            barajando = false;

            synchronized (shuffle_lock) {
                while (!gif_thread_done[0]) {
                    try {
                        shuffle_lock.wait(1000);
                    } catch (InterruptedException ex) {
                        Helpers.logCooperativeCancellation(LOGGER, "shuffle wait", ex);
                        break;
                    }
                }
            }

            // El barajado termino: corta el audio antes de repartir. Las rutas de
            // animacion ya lo paran al salir; esto cubre cualquier borde.
            Audio.stopPreloadedWav("misc/shuffle.wav");

            repartir();
            // Straddle voluntario: tras repartir (cartas del UTG local boca abajo a la
            // espera), el UTG decide a ciegas si lo pone. host-driven + broadcast canónico
            // -> converge en todos los peers antes de la ronda preflop. No-op si STRADDLE
            // off / heads-up / <=2 activos. Revela por fin las cartas tapadas del UTG local.
            resolveVoluntaryStraddle();
            Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), GameFrame.THINK_TIME);
            Helpers.GUIRun(() -> {
                GameFrame.getInstance().getExit_menu().setEnabled(true);
            });
            disableAllPlayersTimeout();
            return true;

        } else {

            for (Player jugador : GameFrame.getInstance().getJugadores()) {
                if (jugador.isActivo() && Helpers.doubleSecureCompare(0f, jugador.getBet()) < 0) {
                    jugador.pagar(jugador.getBet(), null);
                }
            }
            Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), GameFrame.THINK_TIME);
            Helpers.GUIRun(() -> {
                GameFrame.getInstance().getExit_menu().setEnabled(true);
            });
            disableAllPlayersTimeout();
            return false;
        }
    }

    private void sqlNewHand() {
        synchronized (GameFrame.SQL_LOCK) {

            ArrayList<String> jugadores = new ArrayList<>();
            for (Player jugador : GameFrame.getInstance().getJugadores()) {
                if (jugador.isActivo()) {
                    try {
                        jugadores.add(Base64.getEncoder().encodeToString(jugador.getNickname().getBytes("UTF-8")));
                    } catch (UnsupportedEncodingException ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    }
                }
            }

            String sql = "INSERT INTO hand(id_game, counter, sbval, blinds_double, dealer, sb, bb, start, preflop_players) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setQueryTimeout(30);
                statement.setInt(1, this.sqlite_id_game);
                statement.setInt(2, this.conta_mano);
                statement.setDouble(3, Helpers.doubleClean(this.ciega_pequeña));
                statement.setInt(4, this.ciegas_double);
                statement.setString(5, this.dealer_nick);
                statement.setString(6, this.small_blind_nick);
                statement.setString(7, this.big_blind_nick);
                statement.setLong(8, System.currentTimeMillis());
                statement.setString(9, String.join("#", jugadores.toArray(new String[0])));
                statement.executeUpdate();

                sqlite_id_hand = statement.getGeneratedKeys().getInt(1);
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

            // Auditor invariant must hold across "exited cleanly + come back
            // via recovery". A human player who sends EXIT mid-game
            // stays in jugadores with isExit()=true and their stack+buyin
            // preserved in memory. Before this fix the condition below skipped
            // them (isActivo() is false for exited players and their stack is
            // typically >0), so no balance row got written for them in any
            // subsequent hand. The recovery loader (sqlRecoverServerLocalGameKeyData
            // around line 6575) only reads balances from MAX(hand.id), so the
            // exited player ended up with no row to restore from and came back
            // with default stack/buyin — instant auditor mismatch.
            //
            // Including isExit() here writes one balance row per (exited
            // player, hand) with their unchanged stack and buyin. The row is
            // idempotent for sqlUpdateHandEnd (their pagar is 0 because they
            // aren't betting) and is exactly what recovery needs to find their
            // state on the LATEST hand of the game. Warming-up players
            // (spectator && stack>0 && !exit) still get no row — they should
            // not contribute to balance until they enter a hand for real.
            if (this.conta_mano == 1) {
                for (Player jugador : GameFrame.getInstance().getJugadores()) {
                    if (jugador.isActivo() || Helpers.doubleSecureCompare(0f, jugador.getStack()) == 0 || jugador.isExit()) {
                        this.sqlNewHandBalance(jugador.getNickname(), jugador.getStack() + jugador.getBet(), jugador.getBuyin());
                    }
                }
            } else {
                for (Map.Entry<String, Double[]> entry : auditor.entrySet()) {
                    Player jugador = nick2player.get(entry.getKey());
                    if (jugador != null) {
                        if (jugador.isActivo() || Helpers.doubleSecureCompare(0f, jugador.getStack()) == 0 || jugador.isExit()) {
                            this.sqlNewHandBalance(jugador.getNickname(), jugador.getStack() + jugador.getBet(), jugador.getBuyin());
                        }
                    } else {
                        Double[] pasta = entry.getValue();
                        this.sqlNewHandBalance(entry.getKey(), pasta[0], (int) Math.round(pasta[1]));
                    }
                }
            }
        }
    }

    private void sqlNewAction(Player current_player, byte[] actionRecord, byte[] actionSig) {
        synchronized (GameFrame.SQL_LOCK) {
            // Recovery: persist the canonical record + Ed25519
            // signature bytes alongside the action so a post-crash recovery can
            // replay them into HandStateChain. Both are nullable for legacy
            // interop and for paths where the chain wasn't initialised (recovery
            // mid-hand, identity not ready, etc.).
            String sql = "INSERT INTO action(id_hand, player, counter, round, action, bet, conta_raise, response_time, record_b64, sig_b64) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (java.sql.PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setQueryTimeout(30);
                statement.setInt(1, this.sqlite_id_hand);
                statement.setString(2, current_player.getNickname());
                statement.setInt(3, this.conta_accion);
                statement.setInt(4, this.street);
                statement.setInt(5, current_player.getDecision());
                statement.setDouble(6, Helpers.doubleClean(current_player.getBet()));
                statement.setInt(7, this.getConta_raise());
                statement.setInt(8, current_player.getResponseTime());
                if (actionRecord != null) {
                    statement.setString(9, Base64.getEncoder().encodeToString(actionRecord));
                } else {
                    statement.setNull(9, java.sql.Types.VARCHAR);
                }
                if (actionSig != null) {
                    statement.setString(10, Base64.getEncoder().encodeToString(actionSig));
                } else {
                    statement.setNull(10, java.sql.Types.VARCHAR);
                }
                statement.executeUpdate();
            } catch (java.sql.SQLException ex) {
                java.util.logging.Logger.getLogger(Crupier.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
            }
        }
    }

    private boolean sqlCheckGenuineRecoverAction(Player current_player) {
        synchronized (GameFrame.SQL_LOCK) {
            boolean ret = false;

            try {

                String sql = "SELECT player FROM action WHERE id_hand=? and player=? and counter=?";

                boolean exists;

                try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {

                    statement.setQueryTimeout(30);
                    statement.setInt(1, this.sqlite_id_hand);
                    statement.setString(2, current_player.getNickname());
                    statement.setInt(3, this.conta_accion);

                    try (ResultSet rs = statement.executeQuery()) {
                        exists = rs.next();
                    }
                }

                if (exists) {
                    // Existe la acción de ese jugador en esa mano, ahora vamos a ver si coincide lo
                    // que tenemos guardado con lo que ha enviado el servidor/jugador

                    sql = "SELECT player FROM action WHERE id_hand=? and player=? and counter=? and action=?"
                            + (current_player.getDecision() >= Player.BET ? " and bet=?" : "");

                    try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {

                        statement.setQueryTimeout(30);
                        statement.setInt(1, this.sqlite_id_hand);
                        statement.setString(2, current_player.getNickname());
                        statement.setInt(3, this.conta_accion);
                        statement.setInt(4, current_player.getDecision());

                        if (current_player.getDecision() >= Player.BET) {
                            statement.setDouble(5, Helpers.doubleClean(current_player.getBet()));
                        }

                        try (ResultSet rs = statement.executeQuery()) {
                            ret = rs.next();
                        }
                    }

                } else {
                    // No existe esa acción para ese jugador, por lo que no podemos comparar y por
                    // tanto nos fiamos de lo que envía el servidor/jugador
                    ret = true;
                }

            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

            return ret;
        }
    }

    private void sqlNewShowcards(String jugador, boolean parguela) {

        synchronized (GameFrame.SQL_LOCK) {

            String sql = "INSERT INTO showcards(id_hand, player, parguela) VALUES(?,?,?)";

            try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setInt(1, this.sqlite_id_hand);

                statement.setString(2, jugador);

                statement.setBoolean(3, parguela);

                statement.executeUpdate();
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

        }

    }

    private void sqlUpdateShowdownHand(Player jugador, Hand jugada) {

        synchronized (GameFrame.SQL_LOCK) {

            String sql = "UPDATE showdown SET hole_cards=?, hand_cards=?, hand_val=? WHERE id_hand=? AND player=?";

            try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setString(1, jugador.getHoleCard1().isTapada() ? null
                        : jugador.getHoleCard1().toShortString() + "#" + jugador.getHoleCard2().toShortString());

                statement.setString(2, (jugador.getHoleCard1().isTapada() || jugada == null) ? null
                        : Card.collection2ShortString(jugada.getMano()));

                statement.setInt(3, (jugador.getHoleCard1().isTapada() || jugada == null) ? -1 : jugada.getValue());

                statement.setInt(4, this.sqlite_id_hand);

                statement.setString(5, jugador.getNickname());

                statement.executeUpdate();
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

        }
    }

    private void sqlUpdateShowdownPay(Player jugador) {

        // Run-it-twice: durante los dos boards no hay fila que actualizar (el
        // INSERT está suprimido); la fila consolidada se escribe al final.
        if (this.rit_suppress_showdown_sql) {
            return;
        }

        synchronized (GameFrame.SQL_LOCK) {

            String sql = "UPDATE showdown SET pay=?, profit=? WHERE id_hand=? AND player=?";

            try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setDouble(1, Helpers.doubleClean(jugador.getPagar()));

                statement.setDouble(2, Helpers.doubleClean(jugador.getPagar() - jugador.getBote()));

                statement.setInt(3, this.sqlite_id_hand);

                statement.setString(4, jugador.getNickname());

                statement.executeUpdate();
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

        }

    }

    private int sqlGetPlayerContaWins(String nick, int game_id) {
        synchronized (GameFrame.SQL_LOCK) {

            int tot = 0;

            String sql = "SELECT COUNT(*) as total FROM showdown,hand WHERE showdown.player=? AND showdown.winner=? AND showdown.id_hand=hand.id AND hand.id_game=?";

            try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setString(1, nick);

                statement.setBoolean(2, true);

                statement.setInt(3, game_id);

                ResultSet rs = statement.executeQuery();

                if (rs.next()) {
                    tot = rs.getInt("total");
                }
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

            return tot;
        }
    }

    private void sqlNewShowdown(Player jugador, Hand jugada, boolean win, boolean tapadas) {

        // Run-it-twice: el showdown() de cada board está suprimido; la fila
        // consolidada (una por jugador/mano) la escribe resolverRunItTwiceShowdown
        // al final llamando a este método con el flag ya desactivado.
        if (this.rit_suppress_showdown_sql) {
            return;
        }

        synchronized (GameFrame.SQL_LOCK) {

            String sql = "INSERT INTO showdown(id_hand, player, hole_cards, hand_cards, hand_val, winner, pay, profit) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setInt(1, this.sqlite_id_hand);

                statement.setString(2, jugador != null ? jugador.getNickname() : "-----");

                statement.setString(3, (jugador == null || tapadas) ? null
                        : jugador.getHoleCard1().toShortString() + "#" + jugador.getHoleCard2().toShortString());

                statement.setString(4, (jugador == null || tapadas || jugada == null) ? null
                        : Card.collection2ShortString(jugada.getMano()));

                statement.setInt(5, (jugador == null || tapadas || jugada == null) ? -1 : jugada.getValue());

                statement.setBoolean(6, win);

                statement.setDouble(7, Helpers.doubleClean(jugador != null ? jugador.getPagar() : 0));

                statement.setDouble(8,
                        Helpers.doubleClean(jugador != null ? jugador.getPagar() - jugador.getBote() : 0));

                statement.executeUpdate();
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

        }
    }

    private void sqlUpdateHandEnd(double bote_tot) {
        synchronized (GameFrame.SQL_LOCK) {

            // ArrayList (no String[auditor.size()] indexado): con auditor ya
            // ConcurrentHashMap, size() y entrySet() pueden quedar desalineados
            // si otro hilo (finTransmision -> auditorCuentas) inserta durante la
            // iteración → un array predimensionado por size() petaría con
            // ArrayIndexOutOfBounds. Acumular en lista crece sin ese supuesto.
            ArrayList<String> balance_float = new ArrayList<>();

            // UPDATE hand + los balances de la mano en UNA transaccion: con
            // autocommit por defecto cada statement se commiteaba aparte, asi
            // que un crash entre el UPDATE hand y los UPDATE de balance dejaba
            // la mano cerrada con balances PARCIALES y el recovery (lee balances
            // de MAX(hand.id)) hacia volver a algun jugador con stack por
            // defecto. Agrupar en transaccion da todo-o-nada (mismo criterio que
            // rollbackAbortedHand). El backup forense en disco y el play_time
            // van DESPUES del commit: no son parte del estado atomico de la mano.
            Connection con = null;
            boolean prev_autocommit = true;
            boolean tx = false;
            try {
                con = Helpers.getSQLITE();
                prev_autocommit = con.getAutoCommit();
                con.setAutoCommit(false);
                tx = true;

                try (PreparedStatement statement = con.prepareStatement("UPDATE hand SET end=?, pot=? WHERE id=?")) {
                    statement.setQueryTimeout(30);
                    statement.setLong(1, System.currentTimeMillis());
                    statement.setDouble(2, Helpers.doubleClean(bote_tot));
                    statement.setInt(3, this.sqlite_id_hand);
                    statement.executeUpdate();
                }

                for (Map.Entry<String, Double[]> entry : auditor.entrySet()) {

                    Player jugador = nick2player.get(entry.getKey());

                    try {
                        if (jugador != null) {

                            sqlUpdateHandBalance(jugador.getNickname(), jugador.getStack()
                                    + (Helpers.doubleSecureCompare(0f, jugador.getPagar()) < 0 ? jugador.getPagar() : 0f),
                                    jugador.getBuyin());
                            balance_float.add(Base64.getEncoder().encodeToString(jugador.getNickname().getBytes("UTF-8"))
                                    + "|"
                                    + String.valueOf(Helpers.doubleClean(jugador.getStack()
                                            + (Helpers.doubleSecureCompare(0f, jugador.getPagar()) < 0 ? jugador.getPagar()
                                            : 0f)))
                                    + "|" + String.valueOf(jugador.getBuyin())
                                    + "|" + String.valueOf(getRebuyCount(jugador.getNickname())));
                        } else {

                            Double[] pasta = entry.getValue();
                            sqlUpdateHandBalance(entry.getKey(), pasta[0], (int) Math.round(pasta[1]));
                            balance_float.add(Base64.getEncoder().encodeToString(entry.getKey().getBytes("UTF-8")) + "|"
                                    + String.valueOf(Helpers.doubleClean(pasta[0])) + "|" + String.valueOf(Math.round(pasta[1]))
                                    + "|" + String.valueOf(getRebuyCount(entry.getKey())));
                        }
                    } catch (Exception ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    }
                }

                con.commit();
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Failed to persist hand-end close — rolling back", ex);
                if (tx && con != null) {
                    try {
                        con.rollback();
                    } catch (SQLException e2) {
                        LOGGER.log(Level.SEVERE, "Hand-end close rollback failed", e2);
                    }
                }
            } finally {
                if (tx && con != null) {
                    try {
                        con.setAutoCommit(prev_autocommit);
                    } catch (SQLException e3) {
                        LOGGER.log(Level.SEVERE, "Hand-end close autocommit restore failed", e3);
                    }
                }
            }

            LOGGER.log(Level.INFO, () -> "Balance after hand " + String.valueOf(conta_mano) + " -> " + String.join("@", balance_float));

            String balanceFileName = Init.DEV_MODE ? "/balance_backup_" + GameFrame.getInstance().getNick_local().replaceAll("[^a-zA-Z0-9.-]", "_") + ".txt" : "/balance_backup.txt";

            try {
                // writeStringAtomic en lugar del Files.writeString directo
                // (Sprint deferred 🟠-26): garantiza que tras un crash mid-write
                // el balance_backup.txt queda CON SU VALOR ANTERIOR INTACTO en
                // lugar de un fichero vacío. Backup forense por mano: la
                // pérdida de UN backup parcial era recuperable pero la pérdida
                // de TODA la historia (writeString truncate+crash) no.
                Helpers.writeStringAtomic(Paths.get(Init.CORONA_DIR + balanceFileName), String.join("@", balance_float));
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

            try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement("UPDATE game SET play_time=? WHERE id=?")) {
                statement.setQueryTimeout(30);
                statement.setLong(1, GameFrame.getInstance().getConta_tiempo_juego());
                statement.setInt(2, this.sqlite_id_game);
                statement.executeUpdate();

            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

        }
    }

    private void sqlUpdateHandPlayers(ArrayList<Player> resistencia) {

        synchronized (GameFrame.SQL_LOCK) {

            ArrayList<String> jugadores = new ArrayList<>();

            for (Player jugador : resistencia) {

                try {
                    jugadores.add(Base64.getEncoder().encodeToString(jugador.getNickname().getBytes("UTF-8")));
                } catch (UnsupportedEncodingException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }

            }

            String cards = null;

            String sql = "";

            switch (this.street) {

                case PREFLOP:
                    sql = "UPDATE hand SET preflop_players=?, com_cards=? WHERE id=?";
                    break;

                case FLOP:
                    sql = "UPDATE hand SET flop_players=?, com_cards=? WHERE id=?";
                    cards = Card.collection2ShortString(
                            new ArrayList<>(Arrays.asList(GameFrame.getInstance().getCartas_comunes())).subList(0, 3));
                    break;

                case TURN:
                    sql = "UPDATE hand SET turn_players=?, com_cards=? WHERE id=?";
                    cards = Card.collection2ShortString(
                            new ArrayList<>(Arrays.asList(GameFrame.getInstance().getCartas_comunes())).subList(0, 4));
                    break;

                case RIVER:
                    sql = "UPDATE hand SET river_players=?, com_cards=? WHERE id=?";
                    cards = Card.collection2ShortString(
                            new ArrayList<>(Arrays.asList(GameFrame.getInstance().getCartas_comunes())));
                    break;
            }

            try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setQueryTimeout(30);
                statement.setString(1, String.join("#", jugadores.toArray(new String[0])));
                statement.setString(2, cards);
                statement.setInt(3, this.sqlite_id_hand);
                statement.executeUpdate();

            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

        }

    }

    public int getSqlite_game_id() {
        return sqlite_id_game;
    }

    public int getSqlite_hand_id() {
        return sqlite_id_hand;
    }

    private void sqlNewHandBalance(String nick, double stack, int buyin) {

        synchronized (GameFrame.SQL_LOCK) {

            try (PreparedStatement statement = Helpers.getSQLITE()
                    .prepareStatement("INSERT INTO balance(id_hand, player, stack, buyin, rebuy_count) VALUES (?,?,?,?,?)")) {
                statement.setQueryTimeout(30);
                statement.setInt(1, this.sqlite_id_hand);
                statement.setString(2, nick);
                statement.setDouble(3, Helpers.doubleClean(stack));
                statement.setInt(4, buyin);
                statement.setInt(5, getRebuyCount(nick));
                statement.executeUpdate();

            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

        }
    }

    private void sqlUpdateHandBalance(String nick, double stack, int buyin) {
        synchronized (GameFrame.SQL_LOCK) {
            try (PreparedStatement statement = Helpers.getSQLITE()
                    .prepareStatement("UPDATE balance SET stack=?, buyin=?, rebuy_count=? WHERE id_hand=? and player=?")) {
                statement.setQueryTimeout(30);
                statement.setDouble(1, Helpers.doubleClean(stack));
                statement.setInt(2, buyin);
                statement.setInt(3, getRebuyCount(nick));
                statement.setInt(4, this.sqlite_id_hand);
                statement.setString(5, nick);
                statement.executeUpdate();

            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }
    }

    private void sqlNewGame() {

        synchronized (GameFrame.SQL_LOCK) {

            String sql = "INSERT INTO game(start, players, buyin, sb, blinds_time, rebuy, server, blinds_time_type, ugi, local) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setQueryTimeout(30);

                GameFrame.GAME_START_TIMESTAMP = System.currentTimeMillis();

                statement.setLong(1, GameFrame.GAME_START_TIMESTAMP);

                ArrayList<String> players = new ArrayList<>();

                for (String nick : nicks_permutados) {

                    if (nick2player.get(nick).isActivo()) {
                        players.add(Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")));
                    }
                }

                statement.setString(2, String.join("#", players.toArray(new String[0])));

                statement.setInt(3, GameFrame.BUYIN);

                statement.setDouble(4, Helpers.doubleClean(GameFrame.CIEGA_PEQUEÑA));

                statement.setInt(5, GameFrame.CIEGAS_DOUBLE);

                statement.setBoolean(6, GameFrame.REBUY);

                statement.setString(7, GameFrame.getInstance().getSala_espera().getServer_nick());

                statement.setInt(8, GameFrame.CIEGAS_DOUBLE_TYPE);

                statement.setString(9, GameFrame.UGI);

                statement.setInt(10, GameFrame.getInstance().isPartida_local() ? 1 : 0);

                statement.executeUpdate();

                sqlite_id_game = statement.getGeneratedKeys().getInt(1);
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            } catch (UnsupportedEncodingException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

        }

        GameFrame.persistRecoverSettings(sqlite_id_game);
    }

    private void repartir() {

        boolean animacion = GameFrame.repartoAnimOn();

        int pausa = Math.max(100, Math.round(REPARTIR_PAUSA * (2f / this.getJugadoresActivos())));

        // Duración del vuelo de cada carta tapada (centro→asiento). Escala con
        // el nº de jugadores como la pausa pero con un suelo más alto para que
        // el arco se lea aún en mesa llena. El vuelo es bloqueante, así que en
        // modo animación sustituye a la pausa entre cartas.
        int flight_dur = Math.max(150, pausa);

        // Straddle voluntario: si el jugador LOCAL es el UTG en una mano fresca con
        // straddle activo, sus dos hole cards se reparten BOCA ABAJO y NO se revelan
        // aquí — decide el straddle a ciegas y resolveVoluntaryStraddle las destapa
        // tras la decisión. Solo afecta al local que es UTG (el resto, idéntico).
        final boolean defer_straddle_reveal = GameFrame.STRADDLE && this.game_recovered == 0
                && getJugadoresActivos() > 2 && this.utg_nick != null
                && this.utg_nick.equals(GameFrame.getInstance().getLocalPlayer().getNickname())
                && GameFrame.getInstance().getLocalPlayer().isActivo();
        // resolveVoluntaryStraddle revela estas cartas garantizado (incluso si sale por
        // early-return, p.ej. si un jugador se va y quedan <=2 antes de la decisión).
        this.straddle_local_cards_deferred = defer_straddle_reveal;

        if (!animacion) {

            for (Card carta : GameFrame.getInstance().getCartas_comunes()) {
                carta.iniciarCarta();
            }

            for (Player jugador : GameFrame.getInstance().getJugadores()) {

                if (jugador.isActivo()) {

                    jugador.getHoleCard1().iniciarCarta();
                    jugador.getHoleCard2().iniciarCarta();
                }
            }
        }

        int i = 0;

        // Guard: si dealer_nick es null o no esta en la lista de jugadores,
        // el while desborda con IndexOutOfBoundsException matando el thread
        // del Crupier silenciosamente. Abortamos la mano controladamente.
        if (this.dealer_nick == null) {
            LOGGER.log(Level.SEVERE, "repartir() dealer_nick is null — aborting hand");
            cancelarManoYDevolverApuestas("peer.state_inconsistent", true);
            return;
        }
        int jugSize = GameFrame.getInstance().getJugadores().size();
        boolean found = false;
        while (i < jugSize) {
            if (this.dealer_nick.equals(GameFrame.getInstance().getJugadores().get(i).getNickname())) {
                found = true;
                break;
            }
            i++;
        }
        if (!found) {
            LOGGER.log(Level.SEVERE, "repartir() dealer_nick={0} not found in jugadores — aborting hand", this.dealer_nick);
            cancelarManoYDevolverApuestas("peer.state_inconsistent", true);
            return;
        }

        // Asiento del dealer: ancla de origen del vuelo de reparto.
        final Card deal_origin = getDealerSeatAnchor();

        int j, pivote = (i + 1) % GameFrame.getInstance().getJugadores().size();

        j = pivote;

        do {
            GameFrame.getInstance().checkPause();

            Player jugador = GameFrame.getInstance().getJugadores().get(j);

            if (jugador.isActivo() && animacion) {

                final Card hc1 = jugador.getHoleCard1();
                final boolean es_local = (jugador == GameFrame.getInstance().getLocalPlayer());

                // La carta tapada vuela del dealer al asiento; al aterrizar la
                // sienta (deal.wav lo dispara el propio vuelo al lanzar). Para
                // el jugador local, además, revela su valor real (que ya se
                // extrajo de la bóveda C) — salvo que sea el UTG decidiendo el
                // straddle a ciegas (defer_straddle_reveal): se sienta boca abajo.
                Runnable seat = (es_local && !defer_straddle_reveal)
                        ? () -> {
                            hc1.iniciarConValorNumerico((this.local_original_cards[0] & 0xFF) + 1);
                            hc1.destapar(false);
                        }
                        : () -> hc1.iniciarCarta();

                GameFrame.getInstance().getTapete().flyCardToSeat(hc1, deal_origin, flight_dur, "misc/deal.wav", seat);

            } else if (jugador.isActivo() && jugador == GameFrame.getInstance().getLocalPlayer()) {

                Audio.playWavResource("misc/deal.wav", false);

                if (defer_straddle_reveal) {
                    jugador.getHoleCard1().iniciarCarta();
                } else {
                    jugador.getHoleCard1().iniciarConValorNumerico((this.local_original_cards[0] & 0xFF) + 1);
                    jugador.getHoleCard1().destapar(false);
                }

            }

            // En modo animación el vuelo ya consumió el tiempo (bloqueante).
            if (jugador.isActivo() && !animacion) {
                Helpers.pausar(pausa);
            }

            j = (j + 1) % GameFrame.getInstance().getJugadores().size();

        } while (j != pivote);

        do {
            GameFrame.getInstance().checkPause();

            Player jugador = GameFrame.getInstance().getJugadores().get(j);

            if (jugador.isActivo() && animacion) {

                final Card hc2 = jugador.getHoleCard2();
                final boolean es_local = (jugador == GameFrame.getInstance().getLocalPlayer());

                Runnable seat = (es_local && !defer_straddle_reveal)
                        ? () -> {
                            hc2.iniciarConValorNumerico((this.local_original_cards[1] & 0xFF) + 1);
                            hc2.destapar(false);
                        }
                        : () -> hc2.iniciarCarta();

                GameFrame.getInstance().getTapete().flyCardToSeat(hc2, deal_origin, flight_dur, "misc/deal.wav", seat);

            } else if (jugador.isActivo() && jugador == GameFrame.getInstance().getLocalPlayer()) {

                Audio.playWavResource("misc/deal.wav", false);

                if (defer_straddle_reveal) {
                    jugador.getHoleCard2().iniciarCarta();
                } else {
                    jugador.getHoleCard2().iniciarConValorNumerico((this.local_original_cards[1] & 0xFF) + 1);
                    jugador.getHoleCard2().destapar(false);
                }

            }

            // En modo animación el vuelo ya consumió el tiempo (bloqueante).
            if (jugador.isActivo() && !animacion) {
                Helpers.pausar(pausa);
            }

            j = (j + 1) % GameFrame.getInstance().getJugadores().size();

        } while (j != pivote);

        for (Card carta : GameFrame.getInstance().getCartas_comunes()) {

            GameFrame.getInstance().checkPause();

            if (animacion) {
                // Cada comunitaria vuela tapada desde el dealer a su posición y
                // se sienta al aterrizar (misma mecánica/velocidad que las hole
                // cards). El vuelo es bloqueante, así que consume el tiempo entre
                // cartas y dispara deal.wav al lanzar.
                final Card cc = carta;
                GameFrame.getInstance().getTapete().flyCardToSeat(cc, deal_origin, flight_dur, "misc/deal.wav", () -> cc.iniciarCarta());
            } else {
                Helpers.pausar(pausa);
            }
        }

        GameFrame.getInstance().getLocalPlayer().ordenarCartas();

        // Las 5 comunitarias tapadas (y las hole cards) ya están en la mesa: a
        // partir de aquí el overlay de coste de igualar puede mostrarse.
        this.community_cards_dealt = true;
    }

    /**
     * Consensus: on the client, waits for the host's bare
     * {@code HANDVERIFY} trigger (no payload). Returns true when the trigger
     * arrived (or the per-call deadline elapsed and we give up); returns false
     * only if a MISDEAL command was polled instead — in that case the hand was
     * already cancelled here and the caller must NOT continue with the
     * consensus phase. Other {@code HANDVERIFY} commands (with payload) and
     * unrelated commands are left in {@code received_commands} so the
     * consensus phase loop can re-poll them after the trigger settles.
     */
    private boolean waitForHandverifyTrigger() {
        boolean trigger_seen = false;
        long start_time = System.currentTimeMillis();

        do {
            synchronized (this.getReceived_commands()) {
                ArrayList<String> rejected = new ArrayList<>();
                while (!trigger_seen && !this.getReceived_commands().isEmpty()) {
                    String comando = this.received_commands.poll();
                    String[] partes = comando.split("#", -1);

                    if (partes.length == 3 && "HANDVERIFY".equals(partes[2])) {
                        trigger_seen = true;
                    } else if (partes.length >= 4 && "MISDEAL".equals(partes[2])) {
                        String motivo = "";
                        try {
                            motivo = new String(Base64.getDecoder().decode(partes[3]), "UTF-8");
                        } catch (Exception e) {
                        }
                        cancelarManoYDevolverApuestas(motivo, false);
                        return false;
                    } else {
                        rejected.add(comando);
                    }
                }
                if (!rejected.isEmpty()) {
                    this.getReceived_commands().addAll(rejected);
                }
            }

            if (!trigger_seen) {
                GameFrame.getInstance().checkPause();
                synchronized (this.getReceived_commands()) {
                    try {
                        this.received_commands.wait(WAIT_QUEUES);
                    } catch (InterruptedException ex) {
                    }
                }
            }
        } while (!trigger_seen && !isFin_de_la_transmision());

        return true;
    }

    /**
     * Receipt blob layout: {@code HAND_ID(16) || H_final(32) ||
     * flags(1) || sig(64)} = 113 bytes. The flags byte sits BETWEEN H_final and
     * sig so the sig (over RECEIPT || HAND_ID || H_final || flags) covers it.
     *
     * <p>Flag bits:
     * <ul>
     *   <li>bit0 = the issuer observed an invalid Ed25519 signature on at least
     *       one ACTION wire during the hand. {@code runConsensusCheck} refuses
     *       to report OK if any received receipt has this bit set, even when
     *       H_finals match — closes the "all peers see the same invalid sig"
     *       hole where consensus would otherwise pass silently.</li>
     * </ul>
     */
    private static final int RECEIPT_HANDID_LEN = CanonicalActionRecord.HAND_ID_BYTES;
    private static final int RECEIPT_HFINAL_LEN = 32;
    private static final int RECEIPT_FLAGS_LEN = 1;
    private static final int RECEIPT_SIG_LEN = HandStateChain.SIG_BYTES;
    private static final int RECEIPT_TOTAL_LEN = RECEIPT_HANDID_LEN + RECEIPT_HFINAL_LEN + RECEIPT_FLAGS_LEN + RECEIPT_SIG_LEN;
    private static final int RECEIPT_FLAG_BIT_INVALID_SIG_SEEN = 0;
    // Bit 1: el firmante NO confirmo el barajado honesto (DUALLOCK_BUNDLE) de esta mano. Paraguas:
    // puede ser que el bundle llegara y la cola aun no acabara (peer lento, benigno) o que NO llegara.
    // Va firmado en el receipt -> el host no puede quitarlo, y el consenso atestigua "mazo honesto
    // verificado por todos", no solo "acciones OK".
    private static final int RECEIPT_FLAG_BIT_DECK_UNVERIFIED = 1;
    // Bit 2: cualifica al bit 1 cuando NINGUN bundle de barajado llego para este mazo (host no mando la
    // prueba, incluso de forma selectiva a un peer). Distingue "host no prueba" (sospechoso, avisa a la
    // mesa) de "peer lento que aun no termino" (benigno, solo evidencia forense). Tambien va firmado.
    private static final int RECEIPT_FLAG_BIT_NO_SHUFFLE_PROOF = 2;

    /**
     * Consensus barrier — runs BEFORE the payout, once per hand in each showdown
     * branch. The host fires the bare {@code HANDVERIFY} trigger and every client
     * waits for it. This is also the sole point where a late MISDEAL aborts the
     * hand cleanly, before any chip moves: {@code waitForHandverifyTrigger} runs
     * {@code cancelarManoYDevolverApuestas} (which sets {@code mano_anulada} and
     * {@code fin_de_la_transmision}), so the downstream settlement short-circuits
     * and {@link #runSettlementConsensus} is skipped under its {@code !mano_anulada}
     * gate — exactly the swallow-the-bail behaviour of the old single function.
     *
     * <p>The receipt exchange used to live here too; it now runs AFTER settlement
     * (see {@link #runSettlementConsensus}) so the receipt's {@code H_final}
     * commits the payout as well — the single-consensus design documented in
     * {@code docs/audit/settlement-digest-design.md}.
     */
    private void awaitHandverifyBarrier() {
        try {
            if (GameFrame.getInstance().isPartida_local()) {
                // HOST: fire the trigger (sync, confirmed) so every connected client wakes
                // up its own consensus phase before we start emitting receipts.
                broadcastGAMECommandFromServer("HANDVERIFY", null, true);
            } else {
                // Client waits for the trigger. A MISDEAL polled here cancels the hand
                // inside waitForHandverifyTrigger (sets mano_anulada + fin_de_la_transmision);
                // we just return and the gated settlement-consensus never runs.
                waitForHandverifyTrigger();
            }
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE, "awaitHandverifyBarrier failed", ex);
        }
    }

    /**
     * Consensus — runs AFTER the payout, once per hand at hand close. Absorbs this
     * peer's independently-computed {@link SettlementRecord} table (who put in how
     * much, who was paid how much, plus the odd-chip remainder) into the chain as
     * the terminal record, so {@code H_final} commits the money movement — not just
     * the actions and board — then builds, exchanges and cross-checks the closing
     * receipt over that {@code H_final}. Two peers that computed a different payout
     * from the same verified inputs diverge on {@code H_final} and
     * {@link #runConsensusCheck} reports it.
     *
     * <p>Called under {@code !mano_anulada} and BEFORE {@code resetBote()} purges
     * the per-hand contributions, so {@code getBote()/getPagar()} are final. Hands
     * without a chain (legacy interop / chain init failure) skip silently. The
     * outcome is signaletic only — the hand always settles.
     */
    private void runSettlementConsensus() {
        try {
            // Snapshot the hand state. Even if a follow-up readyForNextHand resets the
            // chain or generates a new HAND_ID, we keep working with the values that
            // existed at hand-close. Hands that never built a chain (legacy interop or
            // chain init failure) skip the consensus phase silently.
            final HandStateChain chainSnap = this.hand_state_chain;
            if (chainSnap == null) {
                return;
            }

            // Absorb this peer's settlement table as the chain's terminal record, fixing
            // H_final to also commit the payout. Every peer runs the same deterministic
            // settlement, so honest peers converge; a divergent payout diverges H_final.
            // Defensive: never absorb twice for the same hand.
            if (!chainSnap.isSettlementAbsorbed()) {
                java.util.List<SettlementRecord.Entry> entries = collectSettlementEntries();
                if (entries.isEmpty()) {
                    return;
                }
                long sobranteCents = CanonicalActionRecord.amountToCents(
                        Math.max(0f, Helpers.doubleClean(this.bote_sobrante)));
                byte[] table = SettlementRecord.encode(chainSnap.getHandId(), entries, sobranteCents);
                chainSnap.absorbSettlement(table);
            }

            final byte[] handIdSnap = chainSnap.getHandId();
            final byte[] hFinalSnap = chainSnap.getCurrentHash();
            // Ordinal de la mano (HAND X) para los mensajes del registro; se fija aquí, al cerrar la
            // mano, para que un readyForNextHand posterior no lo desplace.
            final int handOrdinalSnap = getMano();

            // Build our own receipt and emit it. Identity-not-ready is logged but does
            // not prevent the loop below from collecting others' receipts (we will appear
            // as MISSING in their consensus check, which is the correct outcome).
            byte[] localReceipt = buildLocalReceipt(handIdSnap, hFinalSnap);
            if (localReceipt != null) {
                emitOwnReceipt(localReceipt);
            }

            Set<String> expected = computeExpectedConsensusSigners();
            java.util.Map<String, byte[]> receipts = new HashMap<>();
            String localNick = GameFrame.getInstance().getNick_local();
            if (localReceipt != null) {
                receipts.put(localNick, localReceipt);
            }

            long deadline = System.currentTimeMillis() + GameFrame.CLIENT_RECEPTION_TIMEOUT;
            boolean isHost = GameFrame.getInstance().isPartida_local();

            // Identity: relays are collected inside the
            // synchronized block and dispatched OUTSIDE it. The relay broadcast
            // waits for ACKs (sync, confirmation=true) and can block tens of
            // seconds; doing it while holding the received_commands monitor
            // freezes every server-side Participant.run() that has a HANDVERIFY
            // default-case waiting to add to received_commands. With those
            // threads stuck mid-message, their socket_reader_queue stops draining
            // and any CONF for our broadcast queued behind never gets processed
            // — host waits for an ACK that cannot arrive, peer never advances,
            // deadlock holds across reconnects.
            java.util.ArrayList<String[]> pendingRelays = new java.util.ArrayList<>();
            while (System.currentTimeMillis() < deadline
                    && !receipts.keySet().containsAll(expected)
                    && !isFin_de_la_transmision()) {
                // Re-compute expected on each iteration so peers that exit DURING the
                // consensus wait drop out of the awaited set. remotePlayerQuit broadcasts
                // the EXIT immediately, every receiver's WaitingRoomFrame processes it
                // inline (setting Participant.isExit), and the next refresh trims them
                // from the expected set so the loop exits as soon as the surviving
                // signers' receipts arrive.
                expected = computeExpectedConsensusSigners();
                if (receipts.keySet().containsAll(expected)) {
                    break;
                }
                pendingRelays.clear();
                boolean misdealAbort = false;
                String misdealMotivo = "";
                synchronized (this.getReceived_commands()) {
                    ArrayList<String> rejected = new ArrayList<>();
                    while (!this.getReceived_commands().isEmpty()) {
                        String comando = this.received_commands.poll();
                        String[] partes = comando.split("#", -1);
                        if (partes.length == 5 && "HANDVERIFY".equals(partes[2])) {
                            try {
                                String senderNick = new String(Base64.getDecoder().decode(partes[3]), "UTF-8");
                                byte[] receipt = Base64.getDecoder().decode(partes[4]);
                                // First receipt per nick wins — a later frame cannot
                                // overwrite (and thus cannot invalidate) an already
                                // collected receipt, not even the local self-receipt.
                                if (!receipts.containsKey(senderNick)) {
                                    receipts.put(senderNick, receipt);
                                    if (isHost) {
                                        pendingRelays.add(new String[]{partes[3], partes[4], senderNick});
                                    }
                                }
                            } catch (Exception ex) {
                                LOGGER.log(Level.SEVERE, "Failed to parse HANDVERIFY receipt", ex);
                            }
                        } else if (partes.length >= 4 && "MISDEAL".equals(partes[2])) {
                            misdealAbort = true;
                            try {
                                misdealMotivo = new String(Base64.getDecoder().decode(partes[3]), "UTF-8");
                            } catch (Exception e) {
                            }
                            break;
                        } else {
                            rejected.add(comando);
                        }
                    }
                    if (!rejected.isEmpty()) {
                        this.getReceived_commands().addAll(rejected);
                    }
                }

                if (misdealAbort) {
                    // Post-payout: this consensus runs AFTER the chips moved, so a MISDEAL
                    // here is anomalous — the barrier (awaitHandverifyBarrier) already
                    // committed to closing the hand, and §8.2 uses FORFEIT, never MISDEAL,
                    // at showdown. Refunding now would double-pay (bote returns to the stack
                    // without clawing back the pagar). We therefore do NOT cancel: log loudly
                    // and stop the attestation; the hand stays settled.
                    LOGGER.log(Level.SEVERE,
                            "Unexpected MISDEAL during post-payout settlement consensus (motivo={0}) — ignored, hand already settled",
                            misdealMotivo);
                    return;
                }

                if (isHost && !pendingRelays.isEmpty()) {
                    for (String[] r : pendingRelays) {
                        String subcommand = "HANDVERIFY#" + r[0] + "#" + r[1];
                        try {
                            broadcastGAMECommandFromServer(subcommand, r[2], true);
                        } catch (RuntimeException relayEx) {
                            LOGGER.log(Level.WARNING,
                                    "Failed to relay HANDVERIFY receipt from " + r[2], relayEx);
                        }
                    }
                    pendingRelays.clear();
                }

                if (System.currentTimeMillis() < deadline
                        && !receipts.keySet().containsAll(expected)) {
                    synchronized (this.getReceived_commands()) {
                        if (this.getReceived_commands().isEmpty()) {
                            try {
                                this.received_commands.wait(WAIT_QUEUES);
                            } catch (InterruptedException ex) {
                            }
                        }
                    }
                }
            }

            // Final refresh: a peer might have exited between the last loop check
            // and now, especially after the wait wake-up that broke the loop.
            expected = computeExpectedConsensusSigners();
            runConsensusCheck(receipts, expected, handIdSnap, hFinalSnap, handOrdinalSnap);
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE, "runSettlementConsensus failed", ex);
        }
    }

    /**
     * Builds this peer's settlement-table entries from the final per-player
     * accounting at hand close — one {@link SettlementRecord.Entry} per
     * participant that contributed to the pot or was paid from it, using
     * canonical player ids and integer cents (host-independent). MUST be called
     * before {@code resetBote()} purges the per-hand contributions. Players who
     * neither contributed nor were paid are skipped. Bots are included under
     * their own nick; the host signs the receipt that commits the whole table.
     *
     * <p>{@code getBote()} (total invested across all streets) and
     * {@code getPagar()} (chips awarded) are both non-negative; the conversion to
     * cents goes through {@link CanonicalActionRecord#amountToCents(double)} so
     * every peer derives byte-identical amounts from the same double arithmetic.
     */
    private java.util.List<SettlementRecord.Entry> collectSettlementEntries() {
        java.util.List<SettlementRecord.Entry> entries = new java.util.ArrayList<>();
        for (Player jugador : GameFrame.getInstance().getJugadores()) {
            if (jugador == null) {
                continue;
            }
            long boteCents = CanonicalActionRecord.amountToCents(
                    Math.max(0f, Helpers.doubleClean(jugador.getBote())));
            long pagarCents = CanonicalActionRecord.amountToCents(
                    Math.max(0f, Helpers.doubleClean(jugador.getPagar())));
            if (boteCents == 0L && pagarCents == 0L) {
                continue;
            }
            byte[] pid = CanonicalActionRecord.playerIdFromNick(jugador.getNickname());
            entries.add(new SettlementRecord.Entry(pid, boteCents, pagarCents));
        }
        return entries;
    }

    /**
     * Consensus: builds the encoded
     * receipt {@code HAND_ID || H_final || flags || sig} for this peer's local
     * view of the just-finished hand. Returns null if the local identity is
     * not usable. The flags byte encodes the {@code saw_invalid_action_sig}
     * bit so other peers' consensus check knows we observed at least one
     * malformed signature this hand.
     */
    private byte[] buildLocalReceipt(byte[] handId, byte[] hFinal) {
        IdentityManager im = IdentityManager.getInstance();
        if (!im.isReady()) {
            LOGGER.log(Level.SEVERE,
                    "Cannot build local receipt: identity not ready ({0})", im.getLoadError());
            return null;
        }
        try {
            byte flags = 0;
            if (this.saw_invalid_action_sig) {
                flags |= (byte) (1 << RECEIPT_FLAG_BIT_INVALID_SIG_SEEN);
            }
            // Bit 1: no confirme el barajado honesto de ESTE mazo esta mano. dual_lock_verified_megapacket
            // se pone al verificar el bundle OK (cliente), el full-chain en background (host) o al restaurar
            // en recover. Bit 2 (cualificador): EXIGIA prueba (reparto fresco) y NO me llego ningun bundle
            // para este mazo -> host no la mando. Si el bundle SI llego (received==mp) pero la cola aun no
            // acabo, queda solo el bit 1 (peer lento, benigno). Recover pone verified==mp -> ni entra aqui.
            byte[] mp = this.local_mega_packet;
            byte[] verified = this.dual_lock_verified_megapacket;
            if (mp == null || verified == null || !Arrays.equals(mp, verified)) {
                flags |= (byte) (1 << RECEIPT_FLAG_BIT_DECK_UNVERIFIED);
                byte[] expect = this.dual_lock_expect_bundle_for;
                byte[] received = this.dual_lock_bundle_received_for;
                if (mp != null && Arrays.equals(mp, expect) && !Arrays.equals(mp, received)) {
                    flags |= (byte) (1 << RECEIPT_FLAG_BIT_NO_SHUFFLE_PROOF);
                }
            }
            byte[] sig = im.signReceipt(handId, hFinal, flags);
            return encodeReceiptBlob(handId, hFinal, flags, sig);
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE, "signReceipt failed", ex);
            return null;
        }
    }

    private static byte[] encodeReceiptBlob(byte[] handId, byte[] hFinal, byte flags, byte[] sig) {
        if (handId.length != RECEIPT_HANDID_LEN || hFinal.length != RECEIPT_HFINAL_LEN || sig.length != RECEIPT_SIG_LEN) {
            throw new IllegalArgumentException("Receipt component length mismatch");
        }
        byte[] out = new byte[RECEIPT_TOTAL_LEN];
        System.arraycopy(handId, 0, out, 0, RECEIPT_HANDID_LEN);
        System.arraycopy(hFinal, 0, out, RECEIPT_HANDID_LEN, RECEIPT_HFINAL_LEN);
        out[RECEIPT_HANDID_LEN + RECEIPT_HFINAL_LEN] = flags;
        System.arraycopy(sig, 0, out,
                RECEIPT_HANDID_LEN + RECEIPT_HFINAL_LEN + RECEIPT_FLAGS_LEN, RECEIPT_SIG_LEN);
        return out;
    }

    /**
     * Emits this peer's own receipt to everyone else. Hosts broadcast to all
     * connected clients; clients send to the host, which relays the receipt
     * to each of the other clients inside the consensus loop.
     */
    private void emitOwnReceipt(byte[] localReceipt) {
        try {
            String myNick = GameFrame.getInstance().getNick_local();
            String myNickB64 = Base64.getEncoder().encodeToString(myNick.getBytes("UTF-8"));
            String receiptB64 = Base64.getEncoder().encodeToString(localReceipt);
            String cmd = "HANDVERIFY#" + myNickB64 + "#" + receiptB64;
            if (GameFrame.getInstance().isPartida_local()) {
                broadcastGAMECommandFromServer(cmd, null, true);
            } else {
                sendGAMECommandToServer(cmd);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Failed to emit own consensus receipt", ex);
        }
    }

    /**
     * Returns the set of nicks this peer expects to receive a receipt from
     * (spec §6.3): humans who actually played this hand and are still active
     * at the moment the HANDVERIFY trigger fires. Excluded:
     *
     * <ul>
     *   <li>Bots — their actions are signed by the host inside H_t (§10),
     *       no separate receipt.</li>
     *   <li>Players who left mid-hand (Participant.isExit()).</li>
     *   <li>Warming-up players (Player.isCalentando()) — joined a game
     *       already in progress, no hole cards, no chain state.</li>
     *   <li>Spectators (Player.isSpectator()) — bust or no buy-in for this
     *       hand, did not participate in the SRA cascade.</li>
     * </ul>
     *
     * <p>The local nick is added unconditionally only when this peer DOES
     * have a chain (the caller has already snapshotted hand_state_chain and
     * skips the consensus phase entirely when it's null), so reaching this
     * method already implies localNick was a real participant.
     */
    private Set<String> computeExpectedConsensusSigners() {
        Set<String> out = new LinkedHashSet<>();
        java.util.Map<String, Participant> participantes = GameFrame.getInstance().getParticipantes();
        if (participantes == null) {
            return out;
        }
        String localNick = GameFrame.getInstance().getNick_local();
        if (localNick != null) {
            out.add(localNick);
        }
        for (java.util.Map.Entry<String, Participant> entry : participantes.entrySet()) {
            String nick = entry.getKey();
            Participant par = entry.getValue();
            if (nick == null || nick.equals(localNick)) {
                continue;
            }
            if (par == null) {
                continue;
            }
            if (par.isCpu()) {
                continue;
            }
            if (par.isExit()) {
                continue;
            }
            Player jugador = nick2player.get(nick);
            if (jugador != null) {
                if (jugador.isCalentando()) {
                    continue;
                }
                if (jugador.isSpectator()) {
                    continue;
                }
            }
            out.add(nick);
        }
        return out;
    }

    /**
     * Consensus: compares collected
     * receipts against this peer's own (HAND_ID, H_final) and writes the
     * outcome to the Crupier log (i18n), the JUL log, the in-game popup, and
     * the disputed_hands SQLite table. Four outcomes (priority top-down):
     *
     * <ul>
     *   <li>DIVERGENT: some receipt arrived with a different H_final, or a sig
     *       that fails verification (we cannot trust the receipt). Hard
     *       cryptographic evidence: SEVERE.</li>
     *   <li>MISSING: at least one expected peer did not respond inside the
     *       timeout (network outage, client crash, etc.). Ambiguous: WARNING.</li>
     *   <li>INVALID_SIG_SEEN (new): every H_final matches and every receipt is
     *       present, but at least one peer set the bit0 flag in its receipt
     *       indicating it observed an invalid Ed25519 signature on an ACTION
     *       wire during the hand. Bytes were consistent across peers (host
     *       relayed faithfully) but the original signature was broken —
     *       suggests a peer running modified software or a software bug.
     *       Reported as a notice, not an alert.</li>
     *   <li>OK: every expected peer's receipt present, sig valid, H_final
     *       matches, no flags set.</li>
     * </ul>
     */
    private void runConsensusCheck(java.util.Map<String, byte[]> receipts, Set<String> expected,
            byte[] handIdLocal, byte[] hFinalLocal, int handOrdinal) {
        Set<String> missing = new LinkedHashSet<>();
        Set<String> divergent = new LinkedHashSet<>();
        Set<String> invalidSigReporters = new LinkedHashSet<>();
        Set<String> deckUnverifiedReporters = new LinkedHashSet<>();
        Set<String> noShuffleProofReporters = new LinkedHashSet<>();

        for (String nick : expected) {
            byte[] r = receipts.get(nick);
            if (r == null || r.length != RECEIPT_TOTAL_LEN) {
                missing.add(nick);
                continue;
            }
            byte[] handId = Arrays.copyOfRange(r, 0, RECEIPT_HANDID_LEN);
            byte[] hFinal = Arrays.copyOfRange(r, RECEIPT_HANDID_LEN,
                    RECEIPT_HANDID_LEN + RECEIPT_HFINAL_LEN);
            byte flags = r[RECEIPT_HANDID_LEN + RECEIPT_HFINAL_LEN];
            byte[] sig = Arrays.copyOfRange(r,
                    RECEIPT_HANDID_LEN + RECEIPT_HFINAL_LEN + RECEIPT_FLAGS_LEN,
                    RECEIPT_TOTAL_LEN);

            byte[] pubkey = resolveReceiptSignerPubkey(nick);
            if (pubkey == null) {
                // Local TOFU never got this peer's pubkey (handshake glitch, late
                // join). We cannot decide whether tampering happened — fall back
                // to MISSING (lenient bucket) so we don't accuse a benign peer.
                LOGGER.log(Level.SEVERE,
                        "Cannot verify receipt — pubkey unavailable for nick={0}", nick);
                missing.add(nick);
                continue;
            }
            if (!IdentityManager.verifyReceipt(pubkey, handId, hFinal, flags, sig)) {
                // Receipt does NOT verify against the peer's known pubkey: forgery
                // or corrupted key. Stronger signal than absence → DIVERGENT.
                LOGGER.log(Level.SEVERE, "Receipt signature INVALID for nick={0}", nick);
                divergent.add(nick);
                continue;
            }
            if (!Arrays.equals(handId, handIdLocal)) {
                // Receipt for a different hand (late receipt from the previous
                // hand). Treat as not received for THIS hand: MISSING, not DIVERGENT.
                LOGGER.log(Level.SEVERE,
                        "Receipt HAND_ID mismatch for nick={0} — ignoring", nick);
                missing.add(nick);
                continue;
            }
            if (!Arrays.equals(hFinal, hFinalLocal)) {
                divergent.add(nick);
                continue;
            }
            // Receipt valid + H_final matches: still check the flags bit. The
            // bit is part of what the issuer signed, so a host relay cannot
            // strip it. If a peer (including this one) saw an invalid action
            // sig during the hand, the bit propagates and consensus refuses
            // to report a clean OK.
            if (((flags >> RECEIPT_FLAG_BIT_INVALID_SIG_SEEN) & 1) != 0) {
                invalidSigReporters.add(nick);
            }
            // Bit 1: el firmante no confirmo el barajado honesto (DUALLOCK_BUNDLE) de esta
            // mano. El bit 2 lo cualifica: si esta puesto, NINGUN bundle llego (host no mando
            // la prueba -> sospechoso, avisa a la mesa); si no, el bundle llego pero la cola
            // aun no acabo (peer lento -> benigno, solo evidencia forense). Ambos van firmados,
            // asi que el host relay no los puede quitar.
            if (((flags >> RECEIPT_FLAG_BIT_DECK_UNVERIFIED) & 1) != 0) {
                deckUnverifiedReporters.add(nick);
                if (((flags >> RECEIPT_FLAG_BIT_NO_SHUFFLE_PROOF) & 1) != 0) {
                    noShuffleProofReporters.add(nick);
                }
            }
        }

        String localHB64 = Base64.getEncoder().encodeToString(hFinalLocal);

        if (!divergent.isEmpty()) {
            String divergentList = String.join(", ", divergent);
            LOGGER.log(Level.SEVERE,
                    "Hand {0} signature divergence: divergent=[{1}], local_H={2}",
                    new Object[]{sqlite_id_hand, divergentList, localHB64});
            GameFrame.getInstance().getRegistro().print(
                    Translator.translate("game.mano_verificacion_divergente"));
            insertDisputedHandRow(receipts, hFinalLocal, "DIVERGENT");
            showConsensusPopup(
                    Translator.translate("game.popup_verificacion_titulo_alerta"),
                    Translator.translate("game.popup_verificacion_divergente"));
        } else if (!missing.isEmpty()) {
            String missingList = String.join(", ", missing);
            LOGGER.log(Level.WARNING,
                    "Hand {0} verification incomplete: missing=[{1}]",
                    new Object[]{sqlite_id_hand, missingList});
            GameFrame.getInstance().getRegistro().print(
                    MessageFormat.format(
                            Translator.translate("game.mano_verificacion_jugador_ausente"),
                            missingList));
            insertDisputedHandRow(receipts, hFinalLocal, "MISSING");
            showConsensusPopup(
                    Translator.translate("game.popup_verificacion_titulo_aviso"),
                    MessageFormat.format(
                            Translator.translate("game.popup_verificacion_ausente"),
                            missingList));
        } else if (!invalidSigReporters.isEmpty()) {
            String reportersList = String.join(", ", invalidSigReporters);
            LOGGER.log(Level.WARNING,
                    "Hand {0} verified with invalid-sig flag from: [{1}], local_H={2}",
                    new Object[]{sqlite_id_hand, reportersList, localHB64});
            GameFrame.getInstance().getRegistro().print(
                    MessageFormat.format(
                            Translator.translate("game.mano_verificacion_firma_invalida"),
                            reportersList));
            insertDisputedHandRow(receipts, hFinalLocal, "INVALID_SIG_SEEN");
            showConsensusPopup(
                    Translator.translate("game.popup_verificacion_titulo_aviso"),
                    MessageFormat.format(
                            Translator.translate("game.popup_verificacion_firma_invalida"),
                            reportersList));
        } else if (!noShuffleProofReporters.isEmpty()) {
            // Consenso por lo demas limpio, pero a uno o mas firmantes NO les llego ningun bundle
            // de barajado para este mazo. Un host correcto siempre lo manda, asi que su ausencia
            // (sobre todo si se repite, o si es selectiva a un peer) es una degradacion real: se
            // avisa a toda la mesa. NO bloquea ni acusa de trampa (podria ser version modificada o bug).
            String noProofList = String.join(", ", noShuffleProofReporters);
            LOGGER.log(Level.WARNING,
                    "Hand {0} verified but NO shuffle proof reached: [{1}] (host withholding?), local_H={2}",
                    new Object[]{sqlite_id_hand, noProofList, localHB64});
            GameFrame.getInstance().getRegistro().print(
                    MessageFormat.format(
                            Translator.translate("game.mano_verificacion_host_sin_prueba"),
                            noProofList));
            insertDisputedHandRow(receipts, hFinalLocal, "DECK_NO_PROOF");
            showConsensusPopup(
                    Translator.translate("game.popup_verificacion_titulo_aviso"),
                    MessageFormat.format(
                            Translator.translate("game.popup_verificacion_host_sin_prueba"),
                            noProofList));
        } else if (!deckUnverifiedReporters.isEmpty()) {
            // Consenso limpio salvo que uno o mas firmantes aun no TERMINARON de verificar el
            // barajado (el bundle SI les llego, pero su cola va lenta). Es benigno y se autocorrige
            // (la verificacion diferida sigue viva y disparara su propio aviso si algun dia PRUEBA
            // deshonestidad). No merece molestar a la mesa con un popup: solo evidencia forense
            // silenciosa (JUL + disputed_hands), misma politica que el caso TOFU-NEW de abajo.
            String deckList = String.join(", ", deckUnverifiedReporters);
            LOGGER.log(Level.WARNING,
                    "Hand {0} verified; shuffle proof still pending (slow peer) for: [{1}], local_H={2}",
                    new Object[]{sqlite_id_hand, deckList, localHB64});
            // Aviso AMARILLO en el registro (NO popup, no acusa de nada): el barajado de esta mano
            // aún no se pudo confirmar por cliente(s) lento(s); la verificación diferida sigue viva y
            // emitirá su "barajado verificado" cuando termine.
            GameFrame.getInstance().getRegistro().print(
                    MessageFormat.format(Translator.translate("game.barajado_pendiente"), String.valueOf(handOrdinal)));
            insertDisputedHandRow(receipts, hFinalLocal, "DECK_UNVERIFIED");
        } else {
            LOGGER.log(Level.INFO,
                    "Hand {0} verified: {1} receipts unanimous, H={2}",
                    new Object[]{sqlite_id_hand, expected.size(), localHB64});
            // Identity: even on a clean consensus OK, if any of the
            // peers whose receipts we just verified are still on TOFU-NEW
            // (pubkey pinned but not yet confirmed out-of-band by the user),
            // emit a debug-log WARNING. This goes to JUL only — never to the
            // in-game registro and never to a popup — so it stays as forensic
            // info for the user reviewing the debug log later. The OK verdict
            // is still cryptographically sound under the pinned key; the
            // warning just notes that a malicious peer with a stolen key
            // could have produced the same OK without the user catching it
            // via OOB verification.
            String localNick = GameFrame.getInstance().getNick_local();
            java.util.List<String> unverifiedTofu = new java.util.ArrayList<>();
            for (String nick : expected) {
                if (nick == null || nick.equals(localNick)) {
                    continue;
                }
                Participant par = GameFrame.getInstance().getParticipantes().get(nick);
                if (par == null) {
                    continue;
                }
                byte[] pubkey = par.getIdentity_pubkey();
                if (pubkey == null) {
                    unverifiedTofu.add(nick + "(no_pubkey)");
                    continue;
                }
                if (!TOFUResolver.isVerified(nick, pubkey)) {
                    unverifiedTofu.add(nick);
                }
            }
            if (!unverifiedTofu.isEmpty()) {
                LOGGER.log(Level.WARNING,
                        "Hand {0} consensus OK but the following peers have UNVERIFIED Ed25519 pubkeys (TOFU pinned but not confirmed out-of-band): [{1}]",
                        new Object[]{sqlite_id_hand, String.join(", ", unverifiedTofu)});
            }
            GameFrame.getInstance().getRegistro().print(
                    MessageFormat.format(Translator.translate("game.mano_verificada_consenso"), String.valueOf(handOrdinal)));
        }
    }

    /**
     * Resolves the pubkey that should validate {@code nick}'s receipt sig. The
     * host's own pubkey comes from IdentityManager when {@code nick} is the
     * local user; everyone else's comes from Participant.getIdentity_pubkey().
     */
    private byte[] resolveReceiptSignerPubkey(String nick) {
        if (nick == null) {
            return null;
        }
        if (nick.equals(GameFrame.getInstance().getNick_local())) {
            return IdentityManager.getInstance().getPublicKey();
        }
        Participant par = GameFrame.getInstance().getParticipantes().get(nick);
        return par != null ? par.getIdentity_pubkey() : null;
    }

    /**
     * Writes a disputed_hands row with the concatenation of every collected
     * receipt blob, this peer's local H_final and the outcome reason. Failing
     * to insert is logged but does not propagate — the popup and JUL log are
     * still served, the user is informed regardless.
     */
    private void insertDisputedHandRow(java.util.Map<String, byte[]> receipts, byte[] localHFinal, String reason) {
        if (this.sqlite_id_hand < 0) {
            LOGGER.log(Level.WARNING,
                    "No sqlite_id_hand available, skipping disputed_hands insert (reason={0})", reason);
            return;
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (java.util.Map.Entry<String, byte[]> e : receipts.entrySet()) {
            if (e.getValue() != null && e.getValue().length == RECEIPT_TOTAL_LEN) {
                try {
                    out.write(e.getValue());
                } catch (java.io.IOException ioex) {
                    LOGGER.log(Level.SEVERE, "Failed to concat receipt for " + e.getKey(), ioex);
                }
            }
        }
        byte[] receiptsBlob = out.toByteArray();

        try (java.sql.PreparedStatement st = Helpers.getSQLITE().prepareStatement(
                "INSERT INTO disputed_hands(id_hand, timestamp, receipts, local_h, reason) VALUES(?,?,?,?,?)")) {
            st.setInt(1, this.sqlite_id_hand);
            st.setLong(2, System.currentTimeMillis() / 1000L);
            st.setBytes(3, receiptsBlob);
            st.setBytes(4, localHFinal);
            st.setString(5, reason);
            st.executeUpdate();
            LOGGER.log(Level.INFO,
                    "disputed_hands row inserted: id_hand={0}, reason={1}, receipts_collected={2}",
                    new Object[]{this.sqlite_id_hand, reason, receipts.size()});
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Failed to insert disputed_hands row", ex);
        }
    }

    /**
     * Fires an informative popup on the EDT so the user sees the consensus
     * outcome without blocking the game loop.
     */
    private void showConsensusPopup(String title, String body) {
        Helpers.GUIRun(() -> {
            try {
                java.awt.Container container = GameFrame.getInstance();
                String composed = (title != null ? title + "\n\n" : "") + body;
                Helpers.mostrarMensajeInformativo(container, composed);
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Failed to show consensus popup", ex);
            }
        });
    }

    private HashMap<String, Object> recibirDatosClaveRecuperados() {

        // Guard de salida (ver nota en recibirPosiciones). Retorno null
        // si la transmisión muere antes de recibir RECOVERDATA.
        HashMap<String, Object> map = null;

        boolean ok;

        long start_time = System.currentTimeMillis();

        do {

            ok = false;

            synchronized (this.getReceived_commands()) {

                ArrayList<String> rejected = new ArrayList<>();

                while (!ok && !this.getReceived_commands().isEmpty()) {

                    String comando = this.received_commands.poll();
                    try {
                        String[] partes = comando.split("#");

                        if (partes.length >= 4 && partes[2].equals("RECOVERDATA")) {

                            ObjectInputStream in = null;
                            try {
                                ok = true;
                                ByteArrayInputStream byteIn = new ByteArrayInputStream(
                                        Base64.getDecoder().decode(partes[3]));
                                in = new ObjectInputStream(byteIn);
                                // ANTI-RCE: instalar whitelist ANTES de readObject. Sin esto,
                                // un host hostil podría enviar un gadget chain en el classpath
                                // y ejecutar código arbitrario en el cliente que pide recovery.
                                in.setObjectInputFilter(RECOVERY_OBJECT_FILTER);
                                map = (HashMap<String, Object>) in.readObject();

                                Integer hand_id = this.getHandIdFromUGI(GameFrame.UGI);

                                map.put("hand_id", hand_id != null ? hand_id : -1);

                            } catch (IOException ex) {
                                LOGGER.log(Level.SEVERE, null, ex);
                            } catch (ClassNotFoundException ex) {
                                LOGGER.log(Level.SEVERE, null, ex);
                            } finally {
                                if (in != null) {
                                    try {
                                        in.close();
                                    } catch (IOException ex) {
                                        LOGGER.log(Level.SEVERE, null, ex);
                                    }
                                }
                            }

                        } else if (partes.length >= 3 && partes[2].equals("RECOVERDATA")) {
                            LOGGER.log(Level.WARNING, "RECOVERDATA malformed dropped: {0}", comando);
                        } else {
                            rejected.add(comando);
                        }
                    } catch (Exception ex) {
                        LOGGER.log(Level.WARNING, "Exception while processing command in receiveRecoveryKeyData: " + comando, ex);
                    }

                }

                if (!rejected.isEmpty()) {
                    this.getReceived_commands().addAll(rejected);
                    rejected.clear();
                }

            }

            if (!ok) {

                if (GameFrame.getInstance().checkPause()) {
                    start_time = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - start_time > GameFrame.CLIENT_RECEPTION_TIMEOUT) {

                    LOGGER.log(Level.WARNING, "recibirDatosClaveRecuperados timeout — RECOVERDATA never arrived from host (host may not be in recovery mode). Breaking wait, returning null so caller can fall back to saltar_primera_mano.");
                    break;
                } else {
                    synchronized (this.getReceived_commands()) {
                        try {
                            this.received_commands.wait(WAIT_QUEUES);
                        } catch (InterruptedException ex) {
                            Helpers.logCooperativeCancellation(LOGGER, "received commands wait", ex);
                            break;
                        }
                    }
                }
            }

        } while (!ok && !isFin_de_la_transmision());

        return map;
    }

    private String recibirAccionesRecuperadas() {

        // Guard de salida (ver nota en recibirPosiciones). Sin él, si la
        // transmisión muere durante el wait el cliente queda colgado para
        // siempre esperando ACTIONDATA. El retorno null en ese caso es
        // OK: el caller ya está en flujo de terminación.
        String actions = null;

        boolean ok;

        long start_time = System.currentTimeMillis();

        do {

            ok = false;

            synchronized (this.getReceived_commands()) {

                ArrayList<String> rejected = new ArrayList<>();

                while (!ok && !this.getReceived_commands().isEmpty()) {

                    String comando = this.received_commands.poll();
                    try {
                        String[] partes = comando.split("#");

                        if (partes.length >= 4 && partes[2].equals("ACTIONDATA")) {

                            ok = true;

                            try {
                                actions = !"*".equals(partes[3])
                                        ? new String(Base64.getDecoder().decode(partes[3]), "UTF-8")
                                        : "";
                            } catch (UnsupportedEncodingException ex) {
                                LOGGER.log(Level.SEVERE, null, ex);
                            }

                        } else if (partes.length >= 3 && partes[2].equals("ACTIONDATA")) {
                            LOGGER.log(Level.WARNING, "ACTIONDATA malformed dropped: {0}", comando);
                        } else {
                            rejected.add(comando);
                        }
                    } catch (Exception ex) {
                        LOGGER.log(Level.WARNING, "Exception while processing command in ACTIONDATA wait: " + comando, ex);
                    }
                }

                if (!rejected.isEmpty()) {
                    this.getReceived_commands().addAll(rejected);
                    rejected.clear();
                }

            }

            if (!ok) {

                if (GameFrame.getInstance().checkPause()) {
                    start_time = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - start_time > GameFrame.CLIENT_RECEPTION_TIMEOUT) {

                    LOGGER.log(Level.WARNING, "recibirAccionesRecuperadas timeout — ACTIONDATA never arrived from host. Breaking wait so the recovery dialog closes via the empty-queue branch in recuperarDatosClavePartida.");
                    break;
                } else {
                    synchronized (this.getReceived_commands()) {
                        try {
                            this.received_commands.wait(WAIT_QUEUES);
                        } catch (InterruptedException ex) {
                            Helpers.logCooperativeCancellation(LOGGER, "received commands wait", ex);
                            break;
                        }
                    }
                }
            }

        } while (!ok && !isFin_de_la_transmision());

        return actions;
    }

    // Deadline de progreso compartido por los dos broadcasts SÍNCRONOS de recuperación
    // (enviarDatosClaveRecuperados / enviarAccionesRecuperadas, ambos en el hilo del dealer). Congela el
    // reloj mientras la timba esté pausada o mientras algún peer PENDIENTE esté genuinamente reconectando
    // (socket caído/en reset, isSocketDownOrReconnecting) — ese tiempo no cuenta; si no, y venció el tope,
    // expulsa a los peers VIVOS que siguen sin confirmar (markExitAndNotify + socketClose) para que
    // waitSyncConfirmations los saque de pendientes y el reparto de recuperación avance. La mesa sigue.
    // NO usa isSomePlayerTimeout(): los llamadores marcan timeout=true a los pendientes justo antes, así que
    // estaría siempre true y el deadline no vencería nunca (mismo bug de interacción que el broadcast).
    private long expelStalledRecoveryPeers(ArrayList<String> pendientes, long recoverDeadlineMs) {
        boolean hold;
        try {
            boolean anyPendingReconnecting = false;
            for (String pnick : pendientes) {
                Participant pep = GameFrame.getInstance().getParticipantes().get(pnick);
                if (pep != null && pep.isSocketDownOrReconnecting()) {
                    anyPendingReconnecting = true;
                    break;
                }
            }
            hold = GameFrame.getInstance().isTimba_pausada() || anyPendingReconnecting;
        } catch (Exception ignored) {
            hold = true;
        }
        if (hold) {
            return System.currentTimeMillis() + BROADCAST_PROGRESS_TIMEOUT_MS;
        }
        if (System.currentTimeMillis() >= recoverDeadlineMs) {
            for (String nick : new ArrayList<>(pendientes)) {
                Participant pp = GameFrame.getInstance().getParticipantes().get(nick);
                if (pp != null && !pp.isExit() && !pp.isCpu()) {
                    LOGGER.log(Level.SEVERE,
                            "ZERO-TRUST DoS: peer {0} withheld recovery ACK past {1}ms (game running, answering PING) — expelling, table continues",
                            new Object[]{nick, BROADCAST_PROGRESS_TIMEOUT_MS});
                    warnMaliciousPeer(nick, "zero_trust.peer_conf_withheld");
                    pp.markExitAndNotify("withheld recovery ACK (progress deadline)");
                    try {
                        pp.socketClose();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return recoverDeadlineMs;
    }

    public void enviarDatosClaveRecuperados(ArrayList<String> pendientes, HashMap<String, Object> datos) {

        int id = Helpers.CSPRNG_GENERATOR.nextInt();
        byte[] iv = new byte[16];
        Helpers.CSPRNG_GENERATOR.nextBytes(iv);

        long recoverDeadlineMs = System.currentTimeMillis() + BROADCAST_PROGRESS_TIMEOUT_MS;
        do {
            ObjectOutputStream out = null;
            try {
                ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                out = new ObjectOutputStream(byteOut);
                out.writeObject(datos);
                String command = "GAME#" + String.valueOf(id) + "#RECOVERDATA#"
                        + Base64.getEncoder().encodeToString(byteOut.toByteArray());

                for (Player jugador : GameFrame.getInstance().getJugadores()) {
                    if (pendientes.contains(jugador.getNickname())) {
                        Participant p = GameFrame.getInstance().getParticipantes().get(jugador.getNickname());
                        if (p != null && !p.isCpu()) {
                            p.writeCommandFromServer(Helpers.encryptCommand(command, p.getAes_key(), iv, p.getHmac_key()));
                        }
                    }
                }

                this.waitSyncConfirmations(id, pendientes);

                // Deadline de progreso PAUSE/TIMEOUT-aware (igual que broadcastGAMECommandFromServer). Antes
                // no había timeout: un peer que contesta PING pero NO confirma la recuperación bloqueaba el
                // hilo del dealer para siempre. El reloj no corre en pausa ni mientras haya algún peer
                // reconectando. Al vencer con la timba en marcha se expulsa al que retiene y la mesa sigue
                // (al quedar exit, waitSyncConfirmations lo saca de pendientes en la vuelta siguiente).
                if (!pendientes.isEmpty()) {
                    for (String nick : pendientes) {
                        nick2player.get(nick).setTimeout(true);
                        if (!GameFrame.getInstance().getParticipantes().get(nick).isForce_reset_socket()) {
                            try {
                                this.broadcastGAMECommandFromServer("TIMEOUT#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")), nick, false);
                            } catch (UnsupportedEncodingException ex) {
                            }
                        }
                    }
                    recoverDeadlineMs = expelStalledRecoveryPeers(pendientes, recoverDeadlineMs);
                }
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            } finally {
                try {
                    if (out != null) {
                        out.close();
                    }
                } catch (IOException ex) {
                }
            }

        } while (!pendientes.isEmpty());
    }

    public void enviarAccionesRecuperadas(ArrayList<String> pendientes, String datos) {

        int id = Helpers.CSPRNG_GENERATOR.nextInt();
        byte[] iv = new byte[16];
        Helpers.CSPRNG_GENERATOR.nextBytes(iv);

        long recoverDeadlineMs = System.currentTimeMillis() + BROADCAST_PROGRESS_TIMEOUT_MS;
        do {
            try {
                String command = "GAME#" + String.valueOf(id) + "#ACTIONDATA#"
                        + ((datos == null || datos.isEmpty()) ? "*"
                        : Base64.getEncoder().encodeToString(datos.getBytes("UTF-8")));

                for (Player jugador : GameFrame.getInstance().getJugadores()) {
                    if (pendientes.contains(jugador.getNickname())) {
                        Participant p = GameFrame.getInstance().getParticipantes().get(jugador.getNickname());
                        if (p != null && !p.isCpu()) {
                            p.writeCommandFromServer(Helpers.encryptCommand(command, p.getAes_key(), iv, p.getHmac_key()));
                        }
                    }
                }

                this.waitSyncConfirmations(id, pendientes);

                // Deadline de progreso PAUSE/TIMEOUT-aware: ver enviarDatosClaveRecuperados y el helper
                // expelStalledRecoveryPeers. Antes no había timeout y un peer que no confirmaba congelaba
                // el hilo del dealer para siempre.
                if (!pendientes.isEmpty()) {
                    for (String nick : pendientes) {
                        nick2player.get(nick).setTimeout(true);
                        if (!GameFrame.getInstance().getParticipantes().get(nick).isForce_reset_socket()) {
                            try {
                                this.broadcastGAMECommandFromServer("TIMEOUT#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")), nick, false);
                            } catch (UnsupportedEncodingException ex) {
                            }
                        }
                    }
                    recoverDeadlineMs = expelStalledRecoveryPeers(pendientes, recoverDeadlineMs);
                }
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

        } while (!pendientes.isEmpty());
    }

    private double[] calcularBoteParaGanador(double cantidad, int tot_ganadores) {
        // Reparto en céntimos enteros (aritmética exacta, conserva el dinero): cada
        // ganador recibe el floor y el resto indivisible vuelve al bote_sobrante.
        return PotMath.splitAmongWinners(cantidad, tot_ganadores);
    }

    public void sendGAMECommandToServer(String command, boolean confirmation) {

        // Si el menú intenta mandar un EXIT desnudo por otra vía, le pegamos las llaves
        if (command != null && command.equals("EXIT")) {
            command = "EXIT#" + getTestamentoCriptografico();
        }

        ArrayList<String> pendientes = new ArrayList<>();

        pendientes.add(GameFrame.getInstance().getSala_espera().getServer_nick());

        int id = Helpers.CSPRNG_GENERATOR.nextInt();

        String full_command = "GAME#" + String.valueOf(id) + "#" + command;

        do {

            GameFrame.getInstance().getSala_espera()
                    .writeCommandToServer(Helpers.encryptCommand(full_command,
                            GameFrame.getInstance().getSala_espera().getLocal_client_aes_key(),
                            GameFrame.getInstance().getSala_espera().getLocal_client_hmac_key()));

            if (confirmation) {
                this.waitSyncConfirmations(id, pendientes);
            }

            if (confirmation) {
                if (!pendientes.isEmpty()) {
                    GameFrame.getInstance().getLocalPlayer().setTimeout(true);
                } else {
                    GameFrame.getInstance().getLocalPlayer().setTimeout(false);
                }
            }

        } while (!pendientes.isEmpty() && confirmation);
    }

    public void sendGAMECommandToServer(String command) {

        this.sendGAMECommandToServer(command, true);

    }

    private boolean waitSyncConfirmations(int id, ArrayList<String> pending) {

        // Esperamos confirmación
        long start_time = System.currentTimeMillis();

        boolean timeout = false;

        ArrayList<Object[]> rejected = new ArrayList<>();

        while (!pending.isEmpty() && !timeout) {

            Object[] confirmation;

            synchronized (WaitingRoomFrame.getInstance().getReceived_confirmations()) {

                while (!WaitingRoomFrame.getInstance().getReceived_confirmations().isEmpty()) {

                    confirmation = WaitingRoomFrame.getInstance().getReceived_confirmations().poll();

                    if (confirmation != null && confirmation[0] != null && confirmation[1] != null
                            && (int) confirmation[1] == id + 1) {

                        pending.remove((String) confirmation[0]);

                        if (nick2player.containsKey((String) confirmation[0])) {

                            nick2player.get((String) confirmation[0]).setTimeout(false);

                        }

                    } else if (confirmation != null && confirmation[0] != null && confirmation[1] != null) {
                        rejected.add(confirmation);
                    }
                }

                if (!rejected.isEmpty()) {
                    WaitingRoomFrame.getInstance().getReceived_confirmations().addAll(rejected);
                    rejected.clear();
                }

                if (System.currentTimeMillis() - start_time > GameFrame.CONFIRMATION_TIMEOUT) {
                    timeout = true;
                } else if (!pending.isEmpty()) {

                    try {
                        WaitingRoomFrame.getInstance().getReceived_confirmations().wait(WAIT_QUEUES);

                        for (Player jugador : GameFrame.getInstance().getJugadores()) {

                            if (jugador.isExit() && pending.contains(jugador.getNickname())) {

                                pending.remove(jugador.getNickname());
                            }
                        }

                    } catch (InterruptedException ex) {
                        Helpers.logCooperativeCancellation(LOGGER, "received confirmations wait", ex);
                        break;
                    }

                }
            }

        }

        return !pending.isEmpty();
    }

    public Object[] readActionFromRemotePlayer(Player jugador) {
        boolean ok = false;
        // Identity: action[] grows to 6 slots so the wire's
        // record + sig and the voluntary flag survive the trip through Crupier:
        //   [0] decision        (Integer)
        //   [1] bet             (Float; on ALLIN overloaded to cinematic String —
        //                        kept for backward-compat with rondaApuestas)
        //   [2] cinematic       (String|null — separate slot; [1] is overloaded)
        //   [3] record          (byte[92]|null — canonical record from the wire)
        //   [4] sig             (byte[64]|null — Ed25519 signature from the wire)
        //   [5] isVoluntary     (Boolean — false only for host-issued auto-fold §4.5)
        Object[] action = new Object[6];

        // Deadline de PROGRESO gated en el think-time del cliente (Cluster B anti-DoS). Con
        // THINK_TIME_ENABLED cada cliente tiene su contador de tiempo de pensar
        // (LocalPlayer.response_counter) que al llegar a cero hace auto-click en CHECK o FOLD y envía la
        // ACTION, así que un peer legítimo SIEMPRE responde dentro de THINK_TIME + un margen amplio (red, GC,
        // skew de reloj). Un peer que contesta PING pero RETIENE su ACTION más allá de eso, con la timba EN
        // MARCHA, congelaría la ronda: se le expulsa y la mesa sigue (al quedar isExit el bucle sale y
        // synthesizeFoldAction lo foldea; el broadcast de EXIT hace converger al resto por omisión mutua).
        // PAUSE-AWARE: el tiempo en pausa NO cuenta (checkPause bloquea y refrescamos el deadline al volver).
        // Con THINK_TIME_ENABLED=false NO hay deadline: el juego permite pensar indefinidamente por diseño y
        // ahí el kick es MANUAL. Antes NO había timeout de ningún tipo y un peer podía congelar la ronda para
        // siempre. Forzar el FOLD desde el host se evita a propósito: con think-time ON solo EXPULSAMOS al que
        // se pasa del tope (no pisamos su decisión, que ya habría llegado dentro del tope si fuera legítimo).
        Participant actor = GameFrame.getInstance().getParticipantes().get(jugador.getNickname());
        boolean thinkTimeEnforced = GameFrame.THINK_TIME_ENABLED && actor != null && !actor.isCpu();
        long actionBudgetMs = (long) GameFrame.THINK_TIME * 1000L + 60000L;
        long actionDeadlineMs = System.currentTimeMillis() + actionBudgetMs;
        do {
            ok = false;

            // Identity: always drain the queue,
            // even when jugador.isExit() is already true at the start. Reason:
            // the host's §4.5 autofold ACTION for this peer may already be sitting
            // in the queue when their EXIT was processed inline by WaitingRoomFrame
            // earlier (TCP order on receivers is ACTION → EXIT thanks to
            // remotePlayerQuit's deferred EXIT broadcast). Without this drain, the
            // client's main thread would skip the wire and the chain would fork.
            {
                synchronized (this.getReceived_commands()) {
                    java.util.ArrayList<String> rejected = new java.util.ArrayList<>();
                    while (!ok && !this.getReceived_commands().isEmpty()) {
                        String comando = this.received_commands.poll();
                        String[] partes = comando.split("#");

                        {
                            try {
                                /* Action wire:
                                 *   GAME#ID#ACTION#NICK_B64#DECISION#BET#CINEMATIC_OR_*#RECORD_B64#SIG_B64
                                 * Every ACTION carries the record + sig as the last two fields; the
                                 * chain absorbs them and the receiver verifies the signature.
                                 */
                                if (partes.length >= 6 && partes[2].equals("ACTION")) {
                                    String senderNick = new String(java.util.Base64.getDecoder().decode(partes[3]), "UTF-8");

                                    if (senderNick.equals(jugador.getNickname())) {
                                        ok = true;
                                        action[0] = Integer.valueOf(partes[4]);
                                        action[1] = Double.valueOf(partes[5]);
                                        action[2] = null;

                                        /* Cinematic extraction on ALLIN */
                                        if ((Integer) action[0] == Player.ALLIN && partes.length > 6 && !partes[6].isEmpty() && !partes[6].equals("*")) {
                                            action[1] = partes[6];
                                            action[2] = partes[6];
                                        }

                                        // Identity: record + sig from the wire.
                                        // partes[7] / partes[8] arrive as base64; absent (or "*") only on
                                        // a malformed wire, in which case verification is skipped.
                                        action[3] = null;
                                        action[4] = null;
                                        action[5] = Boolean.TRUE; // genuine player decision
                                        if (partes.length >= 9
                                                && !"*".equals(partes[7]) && !"*".equals(partes[8])) {
                                            try {
                                                byte[] wireRecord = java.util.Base64.getDecoder().decode(partes[7]);
                                                byte[] wireSig = java.util.Base64.getDecoder().decode(partes[8]);
                                                action[3] = wireRecord;
                                                action[4] = wireSig;

                                                // Identity: decode the FLAGS.is_voluntary bit from the
                                                // record so action[5] reflects what the sender claimed (§4.5
                                                // host auto-folds use voluntary=0). The §10 receiver rule
                                                // picks the signer pubkey from this bit + Participant.isCpu().
                                                if (wireRecord != null
                                                        && wireRecord.length == CanonicalActionRecord.RECORD_BYTES) {
                                                    int flags = ((wireRecord[CanonicalActionRecord.OFFSET_FLAGS] & 0xff) << 8)
                                                            | (wireRecord[CanonicalActionRecord.OFFSET_FLAGS + 1] & 0xff);
                                                    boolean wireVoluntary = ((flags >> CanonicalActionRecord.FLAG_BIT_VOLUNTARY) & 1) != 0;
                                                    action[5] = wireVoluntary;

                                                    byte[] signerPubkey = resolveActionSignerPubkey(jugador.getNickname(), wireVoluntary);
                                                    if (signerPubkey == null) {
                                                        LOGGER.log(Level.WARNING,
                                                                "Cannot resolve signer pubkey for action by {0} (voluntary={1}) — verification skipped",
                                                                new Object[]{jugador.getNickname(), wireVoluntary});
                                                    } else if (!IdentityManager.verifyAction(signerPubkey, wireRecord, wireSig)) {
                                                        LOGGER.log(Level.SEVERE,
                                                                "ZERO-TRUST: invalid Ed25519 signature on action by {0} (voluntary={1}) — SYNTHESIZING FOLD instead of applying falsified decision",
                                                                new Object[]{jugador.getNickname(), wireVoluntary});
                                                        printInvalidActionSigToRegistro(jugador.getNickname());
                                                        // Identity: mark the hand as having seen
                                                        // an invalid signature so the receipt carries the bit y
                                                        // consensus refuses to report OK at hand close.
                                                        this.saw_invalid_action_sig = true;
                                                        // Sprint deferred 🟠-2: NO aplicar la decision/bet
                                                        // falsificados al state del juego (cambio vs
                                                        // comportamiento anterior "absorbed anyway"). En su lugar
                                                        // sintetizamos fold voluntary=FALSE simétrico al exit-synth
                                                        // existente más abajo. Todos los peers que reciben este
                                                        // mismo ACTION wire verán el mismo sig inválido (Ed25519
                                                        // es determinista) y harán el mismo synth → chain converge
                                                        // por omisión mutua. El receipt mantiene el bit
                                                        // saw_invalid_action_sig, así que el consensus de cierre
                                                        // de mano DIVERGENT detectará el incidente offline para
                                                        // forenses, pero el dinero NO se mueve por la falsificación.
                                                        synthesizeFoldAction(action);
                                                    } else {
                                                        // Firma OK. ZERO-TRUST (🟠-HIGH cerrado): ATAR el record
                                                        // FIRMADO (TIPO de accion + IMPORTE) a la accion realmente
                                                        // jugada (signedRecordBindsToAction). El ACTION_TYPE y el
                                                        // AMOUNT_CENTS del record deben coincidir con lo que la
                                                        // decision + el estado PRE-accion implican (la MISMA
                                                        // formula del firmante en buildLocalActionRecordAndSig).
                                                        // Sin esto, un cliente modificado podia firmar un tipo o un
                                                        // importe (lo que va al historial firmado y a H_t) DISTINTO
                                                        // del que juega en claro (partes[4]/partes[5]) y que mueve
                                                        // el bote en vivo. Cada peer lo recomputa con su propio
                                                        // estado replicado, identico al del firmante (o la cadena
                                                        // ya habria divergido), asi que una accion honesta SIEMPRE
                                                        // coincide y esto NUNCA salta en juego sano. Ante
                                                        // discrepancia (o un importe irrepresentable NaN/Inf, o una
                                                        // decision no mapeable, que una accion legitima jamas
                                                        // produce) se trata IGUAL que una firma invalida: synth-fold
                                                        // simetrico en todo receptor + flag saw_invalid_action_sig
                                                        // -> el consenso de cierre marca el incidente.
                                                        boolean recordForged;
                                                        try {
                                                            byte[] expectedPid = CanonicalActionRecord.playerIdFromNick(jugador.getNickname());
                                                            byte[] expectedHid = (this.hand_state_chain != null)
                                                                    ? this.hand_state_chain.getHandId() : null;
                                                            recordForged = !signedRecordBindsToAction(wireRecord,
                                                                    (int) action[0], action[1],
                                                                    jugador.getBet(), jugador.getStack(), this.apuesta_actual,
                                                                    expectedPid, expectedHid);
                                                            if (recordForged) {
                                                                LOGGER.log(Level.SEVERE,
                                                                        "ZERO-TRUST: signed record for action by {0} does not bind to the played (type/amount) — SYNTHESIZING FOLD",
                                                                        jugador.getNickname());
                                                            }
                                                        } catch (RuntimeException recEx) {
                                                            // Decision no mapeable o importe irrepresentable
                                                            // (NaN/Inf): una accion legitima jamas lo produce.
                                                            recordForged = true;
                                                            LOGGER.log(Level.SEVERE,
                                                                    "ZERO-TRUST: action by {0} has an unmappable decision or unrepresentable amount — SYNTHESIZING FOLD",
                                                                    jugador.getNickname());
                                                        }
                                                        if (recordForged) {
                                                            printInvalidActionSigToRegistro(jugador.getNickname());
                                                            this.saw_invalid_action_sig = true;
                                                            synthesizeFoldAction(action);
                                                        }
                                                    }
                                                }
                                            } catch (Exception parseEx) {
                                                LOGGER.log(Level.WARNING, "Failed to decode/verify record/sig from ACTION wire", parseEx);
                                                action[3] = null;
                                                action[4] = null;
                                            }
                                        } else if (this.hand_state_chain != null) {
                                            // ZERO-TRUST: with the hand-state chain active EVERY action must
                                            // carry a verifiable record+sig. A stripped (*/*) or absent one
                                            // means a malicious host (or peer) is trying to apply a decision
                                            // nobody signed — without this guard the plaintext decision/bet
                                            // (action[0]/[1], already read above) would move money while the
                                            // action stays out of the chain, so a uniform strip would pass
                                            // consensus undetected. Handle it exactly like an invalid sig:
                                            // synthesize a fold (symmetric on every receiver → chains stay in
                                            // lockstep by omission) and flag the hand so the closing consensus
                                            // records the incident. Genuine v1 actions always carry record+sig
                                            // (see the broadcast path) and exit-synths never hit the wire, so
                                            // this never fires on a healthy hand.
                                            LOGGER.log(Level.SEVERE,
                                                    "ZERO-TRUST: ACTION by {0} carries no record/sig while the chain is active — SYNTHESIZING FOLD instead of applying an unsigned decision",
                                                    jugador.getNickname());
                                            printInvalidActionSigToRegistro(jugador.getNickname());
                                            this.saw_invalid_action_sig = true;
                                            synthesizeFoldAction(action);
                                        }
                                    }
                                }
                            } catch (Exception ex) {
                                LOGGER.log(Level.SEVERE, "Error parsing remote action", ex);
                            }
                        }
                        if (!ok) {
                            rejected.add(comando);
                        }
                    }
                    if (!rejected.isEmpty()) {
                        this.getReceived_commands().addAll(rejected);
                    }
                }

                if (!ok && !jugador.isExit()) {
                    boolean pausedNow = GameFrame.getInstance().checkPause();
                    // El cliente CONGELA el contador de think-time del que actúa mientras haya CUALQUIER peer
                    // en timeout (reconexión) o la timba pausada (LocalPlayer.auto_action usa exactamente
                    // !isSomePlayerTimeout() && !isTimba_pausada()). El host DEBE congelar su deadline en las
                    // MISMAS condiciones o expulsaría a un jugador HONESTO que legítimamente espera con su
                    // barra parada (p.ej. otro peer sufre un corte de red durante su turno). Ese tiempo no
                    // cuenta contra el think-time.
                    if (pausedNow || isSomePlayerTimeout()) {
                        actionDeadlineMs = System.currentTimeMillis() + actionBudgetMs;
                    }
                    if (thinkTimeEnforced && System.currentTimeMillis() >= actionDeadlineMs) {
                        // Peer vivo (contesta PING) que retiene su ACTION con la timba EN MARCHA más allá del
                        // think-time + margen amplio. Se le expulsa: al quedar isExit, el bucle sale y
                        // synthesizeFoldAction lo foldea; el broadcast de EXIT hace converger al resto.
                        LOGGER.log(Level.SEVERE,
                                "ZERO-TRUST DoS: peer {0} withheld ACTION past think-time + margin ({1}ms, game running, answering PING) — expelling, table continues",
                                new Object[]{jugador.getNickname(), actionBudgetMs});
                        warnMaliciousPeer(jugador.getNickname(), "zero_trust.peer_action_withheld");
                        actor.markExitAndNotify("withheld ACTION (progress deadline)");
                        try {
                            actor.socketClose();
                        } catch (Exception ignored) {
                        }
                        continue; // el while re-evalúa: jugador.isExit() ya es true -> sale del bucle
                    }
                    synchronized (this.getReceived_commands()) {
                        try {
                            // Con think-time ON el wait se acota al deadline para despertar justo a tiempo de
                            // expulsar (arriba). Con think-time OFF (pensar ilimitado por diseno) NO hay expulsion
                            // por deadline, asi que NO dejamos que el deadline ya vencido encoja el wait a 1ms:
                            // eso degeneraba en busy-spin (poll cada 1ms) mientras se retiene la ACTION >100s.
                            long waitMs = thinkTimeEnforced
                                    ? Math.max(1L, Math.min(WAIT_QUEUES, actionDeadlineMs - System.currentTimeMillis()))
                                    : WAIT_QUEUES;
                            this.received_commands.wait(waitMs);
                        } catch (InterruptedException ex) {
                        }
                    }
                }
            }
        } while (!ok && !jugador.isExit());

        if (jugador.isExit()) {
            // Peer left. Local synth FOLD for UI + finTurno (so the betting loop
            // advances), but record/sig stay null on EVERY peer — host included.
            // The chain rule: an exited peer contributes NO record to the slot
            // it would have played. All peers absorb the same nothing → chain
            // converges without needing a host-signed synth absorbed in order.
            // rondaApuestas keys off action[5]==FALSE to skip both the wire
            // broadcast and the absorb call.
            //
            // Sprint deferred 🟠-2: extraído al helper synthesizeFoldAction
            // (mismo patrón que el branch invalid-sig en el switch ACTION arriba).
            synthesizeFoldAction(action);
        } else {
            jugador.setTimeout(false);
        }
        return action;
    }

    public int puedenApostar(ArrayList<Player> jugadores) {

        int tot = 0;

        for (Player jugador : jugadores) {

            if (jugador.isActivo() && jugador.getDecision() != Player.ALLIN && jugador.getDecision() != Player.FOLD) {
                tot++;
            }
        }

        return tot;
    }

    // ---- Run-it-twice: votación host-driven --------------------------------
    //
    // Se ejecuta SOLO en el host (isPartida_local) cuando la acción se cierra
    // con 2+ implicados (puedenApostar(resisten) <= 1) y la regla está activa.
    // Votan únicamente los humanos implicados (resisten); los bots no votan.
    // Unanimidad: run-it-twice solo si TODOS los humanos implicados votan RIT;
    // un solo NORMAL (o timeout) → board único. Devuelve el booleano acordado.
    //
    // El host muestra su propio diálogo localmente (si está implicado) y pide a
    // los humanos remotos vía RIT_VOTE_REQ; recoge respuestas drenando la cola
    // received_commands (mismo patrón que requestRemoteCascade) y rebroadcasta
    // RIT_VOTE_TALLY en vivo; al cerrar manda RIT_VOTE_CLOSE.

    private void sendRitVoteReq(Participant p, int timeout, int totalVoters) {
        try {
            int id = Helpers.CSPRNG_GENERATOR.nextInt();
            byte[] iv = new byte[16];
            Helpers.CSPRNG_GENERATOR.nextBytes(iv);
            // El bote viaja como double crudo: cada cliente lo formatea con su
            // propio locale (money2String depende de GameFrame.LANGUAGE).
            p.writeCommandFromServer(Helpers.encryptCommand("GAME#" + id + "#RIT_VOTE_REQ#" + timeout + "#" + totalVoters + "#" + this.bote_total, p.getAes_key(), iv, p.getHmac_key()));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to send RIT_VOTE_REQ", e);
        }
    }

    private void broadcastRitTally(int normal, int rit, RunItTwiceDialog hostDialog) {
        // confirmation=false: fire-and-forget. No espera ACKs (los tally en vivo
        // deben ser rápidos) y, crucialmente, NO drena received_commands — si
        // esperara confirmación robaría los RIT_VOTE_RESP que estamos recogiendo.
        try {
            broadcastGAMECommandFromServer("RIT_VOTE_TALLY#" + normal + "#" + rit, null, false);
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Failed to broadcast RIT_VOTE_TALLY", e);
        }
        if (hostDialog != null) {
            hostDialog.setTally(normal, rit);
        }
    }

    private void broadcastRitClose(int result) {
        // confirmation=false por la misma razón; una CLOSE perdida la cubre el
        // safety self-dispose del propio diálogo cliente.
        try {
            broadcastGAMECommandFromServer("RIT_VOTE_CLOSE#" + result, null, false);
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Failed to broadcast RIT_VOTE_CLOSE", e);
        }
    }

    private boolean runRitVote(ArrayList<Player> resisten) {
        String localNick = GameFrame.getInstance().getNick_local();

        boolean localIsVoter = false;
        ArrayList<String> remoteVoterNicks = new ArrayList<>();
        HashMap<String, Participant> remoteVoterParts = new HashMap<>();

        for (Player pl : resisten) {
            String nick = pl.getNickname();
            if (nick == null) {
                continue;
            }
            if (nick.equals(localNick)) {
                // Asiento del host: humano (el host nunca es bot).
                localIsVoter = true;
            } else {
                Participant p = GameFrame.getInstance().getParticipantes().get(nick);
                if (p != null && !p.isCpu() && !p.isExit()) {
                    remoteVoterNicks.add(nick);
                    remoteVoterParts.put(nick, p);
                }
                // Bots y remotos caídos/exit no votan.
            }
        }

        int totalVoters = (localIsVoter ? 1 : 0) + remoteVoterNicks.size();
        if (totalVoters == 0) {
            // Todos los implicados son bots: no se ofrece RIT.
            return false;
        }

        final int totalVotersFinal = totalVoters;
        final String potText = Helpers.money2String(this.bote_total);
        final RunItTwiceDialog[] hd = new RunItTwiceDialog[1];
        if (localIsVoter) {
            Helpers.GUIRunAndWait(() -> hd[0] = new RunItTwiceDialog(GameFrame.getInstance(), RIT_VOTE_TIMEOUT, totalVotersFinal, potText));
            Helpers.GUIRun(() -> hd[0].setVisible(true));
        }
        final RunItTwiceDialog hostDialog = hd[0];

        for (String nick : remoteVoterNicks) {
            sendRitVoteReq(remoteVoterParts.get(nick), RIT_VOTE_TIMEOUT, totalVoters);
        }

        HashMap<String, Integer> votes = new HashMap<>();
        long deadlineMs = System.currentTimeMillis() + (RIT_VOTE_TIMEOUT + 3) * 1000L;

        broadcastRitTally(0, 0, hostDialog);

        // Unanimidad: run-it-twice solo si TODOS los implicados votan RIT. En
        // cuanto alguien vota NORMAL se cierra la votación de inmediato (un solo
        // NO basta para descartar el reparto doble) y se juega board único; no
        // se espera a los votos que falten.
        boolean any_normal = false;

        while (votes.size() < totalVoters && !any_normal
                && !isFin_de_la_transmision() && System.currentTimeMillis() < deadlineMs) {

            boolean changed = false;

            if (localIsVoter && !votes.containsKey(localNick)
                    && hostDialog != null && hostDialog.getVote() != RunItTwiceDialog.VOTE_PENDING) {
                votes.put(localNick, hostDialog.getVote());
                changed = true;
            }

            synchronized (this.getReceived_commands()) {
                ArrayList<String> rejected = new ArrayList<>();
                while (!this.getReceived_commands().isEmpty()) {
                    String cmd = this.received_commands.poll();
                    String[] partes = cmd.split("#");
                    if (partes.length >= 5 && partes[2].equals("RIT_VOTE_RESP")) {
                        try {
                            String voterNick = new String(Base64.getDecoder().decode(partes[3]), "UTF-8");
                            int v = Integer.parseInt(partes[4]);
                            if (remoteVoterNicks.contains(voterNick) && !votes.containsKey(voterNick)
                                    && (v == RunItTwiceDialog.VOTE_NORMAL || v == RunItTwiceDialog.VOTE_RUN_IT_TWICE)) {
                                votes.put(voterNick, v);
                                changed = true;
                            }
                        } catch (Exception e) {
                            // Voto malformado: se ignora.
                        }
                    } else {
                        rejected.add(cmd);
                    }
                }
                if (!rejected.isEmpty()) {
                    this.getReceived_commands().addAll(rejected);
                }
            }

            if (changed) {
                int n = 0, r = 0;
                for (int v : votes.values()) {
                    if (v == RunItTwiceDialog.VOTE_NORMAL) {
                        n++;
                    } else {
                        r++;
                    }
                }
                broadcastRitTally(n, r, hostDialog);
                if (n > 0) {
                    // Unanimidad rota: cerramos ya, sin esperar al resto.
                    any_normal = true;
                }
            }

            if (!any_normal && votes.size() < totalVoters) {
                synchronized (this.getReceived_commands()) {
                    try {
                        this.getReceived_commands().wait(200);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // Unanimidad: RIT solo si nadie votó NORMAL y todos llegaron a votar
        // RIT. Cualquier NORMAL, o votos que no llegaron a tiempo (timeout/caída,
        // que cuentan como NORMAL), -> board único.
        boolean agreed = !any_normal && votes.size() == totalVoters;

        int n = 0, r = 0;
        for (int v : votes.values()) {
            if (v == RunItTwiceDialog.VOTE_NORMAL) {
                n++;
            } else {
                r++;
            }
        }
        n += (totalVoters - votes.size());

        broadcastRitTally(n, r, hostDialog);
        broadcastRitClose(agreed ? 1 : 0);
        if (hostDialog != null) {
            hostDialog.closeDialog();
        }

        return agreed;
    }

    // ---- Run-it-twice: lado CLIENTE (reacciona a RIT_VOTE_* del host) -------

    public void showRitClientVoteDialog(int timeout, int totalVoters, double pot) {
        Helpers.GUIRun(() -> {
            RunItTwiceDialog d = new RunItTwiceDialog(GameFrame.getInstance(), timeout, totalVoters, Helpers.money2String(pot));
            d.setVoteListener((v) -> Helpers.threadRun(() -> {
                try {
                    String myNickB64 = Base64.getEncoder().encodeToString(GameFrame.getInstance().getNick_local().getBytes("UTF-8"));
                    sendGAMECommandToServer("RIT_VOTE_RESP#" + myNickB64 + "#" + v, false);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to send RIT_VOTE_RESP", e);
                }
            }));
            this.rit_client_dialog = d;
            d.setVisible(true);
        });
    }

    public void updateRitClientTally(int normal, int rit) {
        RunItTwiceDialog d = this.rit_client_dialog;
        if (d != null) {
            d.setTally(normal, rit);
        }
    }

    public void closeRitClientDialog(boolean agreed) {
        // El cliente guarda el resultado para que su bucle run() tome la misma
        // rama SIDE-B que el host (checkpoint 3).
        this.rit_agreed = agreed;
        RunItTwiceDialog d = this.rit_client_dialog;
        if (d != null) {
            d.closeDialog();
            this.rit_client_dialog = null;
        }
        printRitVoteResult(agreed);
    }

    public void printRitVoteResult(boolean agreed) {
        if (agreed) {
            // Punto común host (runRitVote / rebroadcast de recovery) y cliente
            // (RIT_VOTE_CLOSE): desde aquí el run-out es ya el de SIDE-A y la
            // pot_label lo marca hasta que el rewind cambie a CARA-B.
            this.rit_pot_board_tag = Translator.translate("runittwice.pot_label_a");
        }
        GameFrame.getInstance().getRegistro().print(Translator.translate(agreed ? "runittwice.log_accepted" : "runittwice.log_rejected"));
    }

    // ====================== Straddle voluntario (post-reparto) ======================
    //
    // El straddle dejó de ser obligatorio: tras repartir (cartas del UTG local boca
    // abajo), el UTG decide A CIEGAS si pone un straddle de 2x la ciega grande. Es una
    // decisión host-driven, como el voto run-it-twice: el host la determina —diálogo
    // local si el host es el UTG, heurística si es un bot, o esperando STRADDLE_RESP del
    // cliente UTG con deadline (timeout = NO)— y difunde el resultado canónico
    // STRADDLE_RESULT; TODOS los peers lo aplican idénticamente, así apuesta_actual /
    // bote_total / orden de acción convergen igual que con el viejo straddle obligatorio.
    // En la mano RECUPERADA no se pregunta: el host repone la decisión original del
    // fósil (straddle_recovered_posted) y la rebroadcasta -> camino uniforme. Con STRADDLE
    // off, heads-up o <=2 activos es un no-op total (camino por defecto byte-idéntico).

    private void resolveVoluntaryStraddle() {
        try {
            if (!GameFrame.STRADDLE || getJugadoresActivos() <= 2 || isFin_de_la_transmision()) {
                return;
            }
            Player straddler = null;
            for (Player j : GameFrame.getInstance().getJugadores()) {
                if (j.getNickname().equals(this.utg_nick)) {
                    straddler = j;
                    break;
                }
            }
            if (straddler == null || !straddler.isActivo()) {
                return;
            }

            final Player straddler_f = straddler;
            final boolean fresh = (this.game_recovered == 0);
            final boolean local_is_straddler = straddler == GameFrame.getInstance().getLocalPlayer();

            // Mano fresca: feedback visual mientras se decide (icono pensativo en el asiento
            // del UTG para los demás + barra del community contando los 5 s). En recover no
            // se pregunta (el host repone del fósil y rebroadcasta), así que no hay espera.
            if (fresh && !local_is_straddler && straddler instanceof RemotePlayer) {
                ((RemotePlayer) straddler_f).showStraddleThinking();
            }
            if (fresh) {
                startStraddleCountdownBar();
            }

            int decision;
            if (GameFrame.getInstance().isPartida_local()) {
                // HOST: autoridad. En fresca decide (diálogo / bot / RESP remoto); en recover
                // repone la decisión original del fósil. En ambos casos difunde el resultado.
                decision = fresh ? hostDecideStraddle(straddler_f, local_is_straddler)
                        : (this.straddle_recovered_posted ? VoluntaryStraddleDialog.POST_STRADDLE : VoluntaryStraddleDialog.NO_STRADDLE);
                broadcastStraddleResult(decision);
            } else {
                // CLIENTE: en fresca, si soy el UTG muestro el diálogo y mando mi respuesta;
                // en todo caso (y siempre en recover) espero el resultado canónico del host.
                if (fresh && local_is_straddler) {
                    sendStraddleResp(promptStraddleLocal(straddler_f));
                }
                decision = waitStraddleResult();
            }

            if (fresh) {
                stopStraddleCountdownBar();
                if (!local_is_straddler && straddler instanceof RemotePlayer) {
                    ((RemotePlayer) straddler_f).clearStraddleThinking();
                }
            }

            if (decision == VoluntaryStraddleDialog.POST_STRADDLE && !isFin_de_la_transmision()) {
                // applyStraddlePost vuela primero la ficha ROJA al asiento (bloquea hasta
                // aterrizar); luego, solo en fresca, vuelan las fichas de dinero al bote
                // (flaseo amarillo típico). El straddle viejo entraba en apuestas Y bote_total
                // por la suma de forzadas de NUEVA_MANO (corría antes que ella); aquí, al ser
                // post-reparto, se suma explícito a AMBOS (apuestas = apuestas de la calle que
                // se muestran en el tapete; bote_total = bote acumulado). El delta del replay/
                // ronda usa getBet()-old_bet, así que el check del straddler da 0 (sin doble).
                // El straddler NO rueda su stack/bet en postStraddle (dentro de
                // applyStraddlePost): lo difiere hasta que su ficha de DINERO aterrice en
                // el bote (launchChipToPot abajo, solo fresca) -> los tres a la vez. En no
                // fresca (recover) no vuela esa ficha, así que no se difiere (rueda al post).
                if (fresh) {
                    straddler_f.setCounterRollDeferred(shouldDeferCountersToChip());
                }
                double posted = applyStraddlePost(straddler_f);
                this.apuestas += posted;
                this.bote_total += posted;
                if (fresh) {
                    // El straddle es una apuesta forzada (2x CG): suena como las ciegas
                    // (bet.wav) y, como ellas (flyForcedBetsToPot), SOLO si la animacion de
                    // fichas esta activa. Sincronizado con la ficha de dinero al bote.
                    if (GameFrame.apuestasAnimOn()) {
                        Audio.playWavResource("misc/bet.wav");
                    }
                    launchChipToPot(straddler_f);
                    if (GameFrame.getInstance().isPartida_local()) {
                        // Persiste la decisión para que una mano recuperada la reponga sin
                        // volver a preguntar (el host la rebroadcasta en el replay).
                        this.straddle_recovered_posted = true;
                        guardarFosilSRA();
                    }
                }
            }
        } finally {
            // Cierra el diálogo local si seguía abierto (idempotente).
            VoluntaryStraddleDialog d = this.straddle_local_dialog;
            if (d != null) {
                d.cancel();
                this.straddle_local_dialog = null;
            }

            // Revela GARANTIZADO las cartas tapadas del UTG local que repartir dejó boca
            // abajo. En finally para cubrir CUALQUIER salida (early-return si un jugador se
            // va y quedan <=2 antes de decidir, excepción, etc.): el UTG local nunca se
            // queda jugando a ciegas. En recover el flag es false (repartir reveló normal).
            if (this.straddle_local_cards_deferred) {
                this.straddle_local_cards_deferred = false;
                revealLocalStraddlerCards();
            }
        }
    }

    // El host determina la decisión de straddle del UTG en la mano fresca: diálogo local
    // si el host es el UTG, heurística si es un bot, o espera del STRADDLE_RESP remoto.
    private int hostDecideStraddle(Player straddler, boolean local_is_straddler) {
        if (local_is_straddler) {
            return promptStraddleLocal(straddler);
        }
        if (straddler instanceof RemotePlayer && ((RemotePlayer) straddler).getBot() != null) {
            return botStraddleDecision(straddler);
        }
        return waitStraddleRespFromRemote(straddler.getNickname());
    }

    // Heurística del bot UTG: pone el straddle con probabilidad baja (BOT_STRADDLE_PROBABILITY),
    // y solo con stack holgado (no se autolesiona). Corre SOLO en el host (decide por el bot).
    private int botStraddleDecision(Player bot) {
        double amount = Helpers.doubleClean(2 * this.ciega_grande);
        if (Helpers.doubleSecureCompare(bot.getStack(), 5 * amount) < 0) {
            return VoluntaryStraddleDialog.NO_STRADDLE;
        }
        return (Helpers.CSPRNG_GENERATOR.nextDouble() < BOT_STRADDLE_PROBABILITY)
                ? VoluntaryStraddleDialog.POST_STRADDLE : VoluntaryStraddleDialog.NO_STRADDLE;
    }

    // Muestra el diálogo de straddle voluntario sobre las hole cards (tapadas) del UTG
    // local y BLOQUEA hasta que el jugador decide (botón) o expira la cuenta atrás
    // (5 s -> NO). Devuelve 1 = pone, 0 = no.
    private int promptStraddleLocal(Player straddler) {
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final int[] result = {VoluntaryStraddleDialog.NO_STRADDLE};
        final String amount_text = Helpers.money2String(straddleAmountFor(straddler));
        final java.awt.Component c1 = straddler.getHoleCard1();
        final java.awt.Component c2 = straddler.getHoleCard2();
        Helpers.GUIRun(() -> {
            VoluntaryStraddleDialog dlg = new VoluntaryStraddleDialog(GameFrame.getInstance(), c1, c2,
                    STRADDLE_DECISION_TIMEOUT, amount_text, (ans) -> {
                        result[0] = ans;
                        latch.countDown();
                    });
            this.straddle_local_dialog = dlg;
            dlg.setVisible(true);
        });
        try {
            // El diálogo se auto-resuelve a los 5 s (o antes por botón); +3 s de margen.
            latch.await(STRADDLE_DECISION_TIMEOUT + 3, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result[0];
    }

    // Importe del straddle a MOSTRAR: 2x la ciega grande, o el stack del UTG si no lo
    // cubre (all-in por menos). El UTG no tiene ciega previa, así que su disponible es
    // stack + bet (bet = 0 en la práctica).
    private double straddleAmountFor(Player straddler) {
        double full = Helpers.doubleClean(2 * this.ciega_grande);
        double available = Helpers.doubleClean(straddler.getStack() + straddler.getBet());
        return Helpers.doubleSecureCompare(available, full) < 0 ? available : full;
    }

    // Host: espera el STRADDLE_RESP del cliente UTG drenando received_commands (re-encola
    // lo que no toca, como runRitVote). Deadline = 5 s + margen; timeout/caída -> NO.
    private int waitStraddleRespFromRemote(String nick) {
        long deadline = System.currentTimeMillis() + (STRADDLE_DECISION_TIMEOUT + 4) * 1000L;
        while (!isFin_de_la_transmision() && System.currentTimeMillis() < deadline) {
            synchronized (this.getReceived_commands()) {
                java.util.ArrayList<String> rejected = new java.util.ArrayList<>();
                Integer answer = null;
                while (!this.getReceived_commands().isEmpty()) {
                    String cmd = this.received_commands.poll();
                    String[] partes = cmd.split("#");
                    if (partes.length >= 5 && partes[2].equals("STRADDLE_RESP")) {
                        try {
                            String voter = new String(Base64.getDecoder().decode(partes[3]), "UTF-8");
                            int v = Integer.parseInt(partes[4]);
                            if (voter.equals(nick) && (v == VoluntaryStraddleDialog.NO_STRADDLE || v == VoluntaryStraddleDialog.POST_STRADDLE)) {
                                answer = v;
                            } else {
                                rejected.add(cmd);
                            }
                        } catch (Exception e) {
                            // RESP malformado: se ignora.
                        }
                    } else {
                        rejected.add(cmd);
                    }
                }
                if (!rejected.isEmpty()) {
                    this.getReceived_commands().addAll(rejected);
                }
                if (answer != null) {
                    return answer;
                }
                try {
                    this.getReceived_commands().wait(200);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return VoluntaryStraddleDialog.NO_STRADDLE;
    }

    // Cliente: espera el resultado canónico STRADDLE_RESULT del host drenando
    // received_commands (re-encola lo que no toca). Con tope STRADDLE_RESULT_WAIT_TIMEOUT:
    // si host y cliente discrepan transitoriamente en getJugadoresActivos() (un jugador se
    // va justo en la ventana de decisión), el host puede hacer early-return sin difundir y
    // el cliente colgaría aquí indefinidamente. Al expirar se asume NO straddle — que es
    // EXACTAMENTE lo que aplicó el host en ese early-return (no posteó) -> peers convergen.
    // El tope (20s) supera con holgura el peor caso del host (~9s) para no cortar un POST
    // legítimo lento; un broadcast real sobre TCP llega mucho antes.
    private int waitStraddleResult() {
        long deadline = System.currentTimeMillis() + STRADDLE_RESULT_WAIT_TIMEOUT * 1000L;
        while (!isFin_de_la_transmision() && System.currentTimeMillis() < deadline) {
            synchronized (this.getReceived_commands()) {
                java.util.ArrayList<String> rejected = new java.util.ArrayList<>();
                Integer result = null;
                while (!this.getReceived_commands().isEmpty()) {
                    String cmd = this.received_commands.poll();
                    String[] partes = cmd.split("#");
                    if (partes.length >= 4 && partes[2].equals("STRADDLE_RESULT")) {
                        try {
                            int v = Integer.parseInt(partes[3]);
                            if (v == VoluntaryStraddleDialog.NO_STRADDLE || v == VoluntaryStraddleDialog.POST_STRADDLE) {
                                result = v;
                            } else {
                                rejected.add(cmd);
                            }
                        } catch (Exception e) {
                            // RESULT malformado: se ignora.
                        }
                    } else {
                        rejected.add(cmd);
                    }
                }
                if (!rejected.isEmpty()) {
                    this.getReceived_commands().addAll(rejected);
                }
                if (result != null) {
                    return result;
                }
                try {
                    this.getReceived_commands().wait(200);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return VoluntaryStraddleDialog.NO_STRADDLE;
    }

    private void sendStraddleResp(int v) {
        try {
            String myNickB64 = Base64.getEncoder().encodeToString(GameFrame.getInstance().getNick_local().getBytes("UTF-8"));
            sendGAMECommandToServer("STRADDLE_RESP#" + myNickB64 + "#" + v, false);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to send STRADDLE_RESP", e);
        }
    }

    private void broadcastStraddleResult(int v) {
        // confirmation=false (fire-and-forget) como RIT_VOTE_CLOSE: TCP ya garantiza
        // la entrega y el cliente lo drena en waitStraddleResult; evita que el handshake
        // de confirmación se trague comandos pendientes.
        try {
            broadcastGAMECommandFromServer("STRADDLE_RESULT#" + v, null, false);
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Failed to broadcast STRADDLE_RESULT", e);
        }
    }

    // Postea el straddle del UTG: mueve 2x la ciega grande (o all-in por menos) de su
    // stack a la apuesta VIVA, sube apuesta_actual al straddle, fija ultimo_raise=CG si
    // es completo (siguiente raise mínimo = 3xCG), marca straddle_posted y mueve la
    // "pistola" al primero-en-hablar real (utg+1; el straddler habla el último, opción).
    // Vuela la ficha ROJA de straddle a su asiento (bloquea hasta aterrizar). Devuelve el
    // importe posteado. NO suma a bote_total (lo hace el llamante). Replica el camino del
    // antiguo straddle obligatorio para que el consenso converja en todos los peers.
    private double applyStraddlePost(Player straddler) {
        double straddle_amount = Helpers.doubleClean(2 * this.ciega_grande);
        double posted = straddler.postStraddle(straddle_amount);
        if (Helpers.doubleSecureCompare(this.apuesta_actual, posted) < 0) {
            this.apuesta_actual = posted;
        }
        if (Helpers.doubleSecureCompare(posted, straddle_amount) >= 0) {
            this.ultimo_raise = this.ciega_grande;
        }
        this.straddle_posted = true;

        straddler.disableUTG();
        Player utg_real = nextActivePlayerAfter(this.utg_nick);
        if (utg_real != null) {
            utg_real.setUTG();
            this.straddle_utg_nick = utg_real.getNickname();
        }

        flyStraddleChipToSeat(straddler);

        return posted;
    }

    // Vuela la ficha ROJA de straddle desde el CENTRO de la mesa al asiento del straddler
    // (mismo motor que la rotación de fichas de posición) y, al aterrizar, pinta su ficha
    // estática. BLOQUEA hasta el aterrizaje. Sin animación / en recover / fin de
    // transmisión: solo pinta la ficha estática (idéntico al straddle viejo).
    private void flyStraddleChipToSeat(Player straddler) {
        if (!GameFrame.ciegasDealerAnimOn() || GameFrame.RECOVER || this.game_recovered != 0 || isFin_de_la_transmision()) {
            straddler.refreshPositionChipIcons();
            return;
        }
        final java.util.List<TablePanel.ChipFlight> flights = new java.util.ArrayList<>();
        flights.add(new TablePanel.ChipFlight(null, straddler, Helpers.IMAGEN_STRADDLE)); // null = desde el centro
        int pausa = Math.max(100, Math.round(REPARTIR_PAUSA * (2f / getJugadoresActivos())));
        final int flight_dur = Math.max(150, pausa);
        Helpers.GUIRunAndWait(() -> straddler.getChip_label().setVisible(false));
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        GameFrame.getInstance().getTapete().flyChipsToSeats(flights, flight_dur, () -> {
            straddler.refreshPositionChipIcons();
            latch.countDown();
        });
        try {
            latch.await(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    // Barra del community durante la decisión: cuenta los 5 s y, al agotarse sin
    // resultado aún, pasa a indeterminada hasta que stopStraddleCountdownBar la pare
    // (cubre el retardo de red entre el fin de los 5 s y el STRADDLE_RESULT del host).
    private void startStraddleCountdownBar() {
        this.straddle_bar_active = true;
        Helpers.threadRun(() -> {
            Helpers.GUIRun(() -> Helpers.smoothCountdown(GameFrame.getInstance().getBarra_tiempo(), STRADDLE_DECISION_TIMEOUT));
            int t = STRADDLE_DECISION_TIMEOUT;
            while (t > 0 && this.straddle_bar_active && !isFin_de_la_transmision()) {
                Helpers.pausar(1000);
                if (!GameFrame.getInstance().isTimba_pausada()) {
                    --t;
                }
            }
            if (this.straddle_bar_active && !isFin_de_la_transmision()) {
                Helpers.GUIRun(() -> Helpers.barraIndeterminada(GameFrame.getInstance().getBarra_tiempo()));
            }
        });
    }

    private void stopStraddleCountdownBar() {
        this.straddle_bar_active = false;
        Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), 0);
    }

    // Revela las dos hole cards del UTG local que repartir dejó boca abajo a la espera
    // de la decisión de straddle a ciegas.
    private void revealLocalStraddlerCards() {
        final Player local = GameFrame.getInstance().getLocalPlayer();
        Helpers.GUIRun(() -> {
            local.getHoleCard1().iniciarConValorNumerico((this.local_original_cards[0] & 0xFF) + 1);
            local.getHoleCard1().destapar(false);
            local.getHoleCard2().iniciarConValorNumerico((this.local_original_cards[1] & 0xFF) + 1);
            local.getHoleCard2().destapar(false);
        });
    }

    private void destaparCartaComunitaria(int street, ArrayList<Player> resisten) {

        GameFrame.getInstance().checkPause();

        switch (street) {
            case FLOP:
                destaparFlop(resisten);
                break;
            case TURN:
                destaparTurn(resisten);
                break;
            case RIVER:
                destaparRiver(resisten);
                break;
            default:
                break;
        }
    }

    public int getConta_raise() {
        return conta_raise;
    }

    public Player getLast_aggressor() {
        return last_aggressor;
    }

    private boolean enviarCartasComunitarias(java.util.ArrayList<Player> resisten) {
        java.util.logging.Logger.getLogger(Crupier.class.getName()).log(java.util.logging.Level.INFO, "Initiating SRA street unlock: {0}", street);

        // Dual-lock: ambas mitades son load-bearing. La pocket sigue siendo
        // necesaria para resolver el resto de la mano y la community es la
        // que descifra los pieces post-rotación; faltar cualquiera es un
        // estado inconsistente (típicamente recovery de un fósil legacy
        // sin SRAKEYS_COMMUNITY@).
        if (this.local_sra_unlock == null || this.local_sra_unlock_community == null || this.active_crypto_ring == null) {
            cancelarManoYDevolverApuestas("peer.state_inconsistent");
            return false;
        }

        int numPlayers = this.active_crypto_ring.length;
        int numCards = (street == Crupier.FLOP) ? 3 : 1;

        int offset = numPlayers * 2;
        if (street == Crupier.FLOP) {
            offset += 1;
        } else if (street == Crupier.TURN) {
            offset += 1 + 3 + 1;
        } else if (street == Crupier.RIVER) {
            offset += 1 + 3 + 1 + 1 + 1;
        }

        int unlockPhase = phaseForStreet(street, false);
        String pieceCommand = (street == Crupier.FLOP) ? "FLOP_PIECE"
                : (street == Crupier.TURN) ? "TURN_PIECE" : "RIVER_PIECE";

        int[] hostIndices = cascadeAndDealCommunityPieces(offset, numCards, unlockPhase, pieceCommand, true);
        if (hostIndices == null) {
            return false;
        }

        if (street == Crupier.FLOP) {
            GameFrame.getInstance().getFlop1().actualizarConValorNumerico(hostIndices[0] + 1);
            GameFrame.getInstance().getFlop2().actualizarConValorNumerico(hostIndices[1] + 1);
            GameFrame.getInstance().getFlop3().actualizarConValorNumerico(hostIndices[2] + 1);
        } else if (street == Crupier.TURN) {
            GameFrame.getInstance().getTurn().actualizarConValorNumerico(hostIndices[0] + 1);
        } else if (street == Crupier.RIVER) {
            GameFrame.getInstance().getRiver().actualizarConValorNumerico(hostIndices[0] + 1);
        }

        // Identity: broadcast a host-signed announce of these
        // indices and absorb into H_t. Every recipient cross-checks their
        // PIECE-decoded indices against the announce; mismatch triggers
        // lockdown (cross-recipient fork attack closed). Skipped when the
        // chain isn't initialised (legacy interop / recovery-degraded) — the
        // hand still plays out via the SRA piece resolution alone.
        //
        // Order matters: absorb ONLY if the broadcast succeeded. If the
        // broadcast throws (clients didn't receive the announce) but we
        // absorb anyway, our chain advances while their chains stay put →
        // guaranteed DIVERGENT at consensus close. Skipping the absorb on
        // broadcast failure keeps host and clients in lockstep (all
        // skipped) and surfaces the failure as a normal SRA / connection
        // issue rather than a phantom cryptographic divergence.
        Object[] recsig = buildCommunityRevealRecordAndSig(mapJavaStreetToWire(street), hostIndices);
        if (recsig != null) {
            byte[] record = (byte[]) recsig[0];
            byte[] sig = (byte[]) recsig[1];
            boolean broadcastOk = false;
            try {
                String comando = "COMM_REVEAL#"
                        + Base64.getEncoder().encodeToString(record)
                        + "#" + Base64.getEncoder().encodeToString(sig);
                broadcastGAMECommandFromServer(comando, null);
                broadcastOk = true;
            } catch (RuntimeException ex) {
                LOGGER.log(Level.SEVERE, "Failed to broadcast COMM_REVEAL for street " + street, ex);
            }
            if (broadcastOk) {
                // Host absorbs with its own nick (always in active_crypto_ring, so
                // the isInActiveCryptoRing guard passes).
                absorbActionIntoChain(GameFrame.getInstance().getNick_local(), record, sig);
            }
        }

        return true;
    }

    // Span completo del board de SIDE-A en el MEGAPACKET: burn + flop(3) + burn +
    // turn + burn + river = 8 cartas. SIDE-B arranca justo después.
    private static final int RIT2_BOARD_SPAN = 8;

    static int rit2PhaseForStreet(int street) {
        if (street == FLOP) {
            return UNLOCK_PHASE_RIT2_FLOP;
        }
        if (street == TURN) {
            return UNLOCK_PHASE_RIT2_TURN;
        }
        return UNLOCK_PHASE_RIT2_RIVER;
    }

    static int mapJavaStreetToRit2Wire(int javaStreet) {
        if (javaStreet == FLOP) {
            return CanonicalActionRecord.STREET_RIT2_FLOP;
        }
        if (javaStreet == TURN) {
            return CanonicalActionRecord.STREET_RIT2_TURN;
        }
        return CanonicalActionRecord.STREET_RIT2_RIVER;
    }

    /**
     * Run-it-twice: divide un (side)pot en las mitades de SIDE-A y SIDE-B.
     * Trabaja en céntimos enteros (la ficha mínima del juego es 0.01, toda la
     * economía redondea al céntimo vía floatClean/setStack). Regla de la casa: si
     * el total en céntimos es impar, AMBAS caras reciben el floor y el céntimo
     * indivisible NO se reparte — queda sin pagar y el recálculo de bote_sobrante
     * tras los dos boards lo arrastra a la mano siguiente. Invariante:
     * sideA + sideB + pico == pot exacto (ni se crea ni se pierde dinero).
     *
     * @return {@code [sideA_chips, sideB_chips]}
     */
    static double[] splitPotForRunItTwice(double pot) {
        // Mitades en céntimos (ficha 0.01): el céntimo indivisible si el total es
        // impar NO se reparte y el recálculo de bote_sobrante tras los dos boards
        // lo arrastra. Invariante: sideA + sideB + pico == pot.
        return PotMath.splitForRunItTwice(pot);
    }

    // Run-it-twice rewind (parte comunitaria): deja las cartas comunitarias
    // "corridas" (calles posteriores al all-in run-out) boca abajo
    // (iniciada+tapada+visible, mostrando el dorso) y sin el desenfoque del
    // showdown de SIDE-A, que es justo el estado del que el reparto vivo revela
    // una comunitaria (actualizarConValorNumerico + destapar). Con animaciones,
    // las corridas se RETIRAN de la mesa (hueco vacío) y se vuelven a repartir
    // una a una con la animación de reparto del juego (deal.wav + dorso + pausa,
    // mismo ritmo que repartir()); sin animaciones, dorso directo con
    // iniciarCarta(true). resetearCarta(false) a secas NO vale (quedaban
    // invisibles y destapar no hacía nada): aquí es seguro SOLO porque el
    // re-reparto las re-inicia antes de que repartirSideB las destape. Las
    // compartidas (calle del all-in y anteriores) quedan fijas.
    private void rebobinarComunitariasSideB() {

        ArrayList<Card> corridas = new ArrayList<>();
        ArrayList<Card> compartidas = new ArrayList<>();

        if (rit_allin_street < FLOP) {
            corridas.add(GameFrame.getInstance().getFlop1());
            corridas.add(GameFrame.getInstance().getFlop2());
            corridas.add(GameFrame.getInstance().getFlop3());
        } else {
            compartidas.add(GameFrame.getInstance().getFlop1());
            compartidas.add(GameFrame.getInstance().getFlop2());
            compartidas.add(GameFrame.getInstance().getFlop3());
        }

        if (rit_allin_street < TURN) {
            corridas.add(GameFrame.getInstance().getTurn());
        } else {
            compartidas.add(GameFrame.getInstance().getTurn());
        }

        if (rit_allin_street < RIVER) {
            corridas.add(GameFrame.getInstance().getRiver());
        } else {
            compartidas.add(GameFrame.getInstance().getRiver());
        }

        // Con el jugador local ya fuera de la timba el rewind pasa a la ruta
        // seca (dorso directo, sin beats ni deal.wav): el reparto de SIDE-B
        // tiene que completarse igualmente (lleva medio bote), pero corre bajo
        // lock_contabilidad y cada pausa cosmética retrasa la salida real
        // (finTransmision espera ese lock para el snapshot del auditor).
        boolean animacion = GameFrame.repartoAnimOn() && !GameFrame.getInstance().getLocalPlayer().isExit();

        Helpers.GUIRunAndWait(() -> {
            // Corridas → fuera de la mesa (resetearCarta invisible) si hay
            // animaciones, o boca abajo directo si no las hay.
            // Compartidas → enfocar() para deshacer el atenuado del showdown de
            // SIDE-A, de modo que el board de SIDE-B se vea entero y brillante.
            for (Card carta : corridas) {
                if (animacion) {
                    carta.resetearCarta(false);
                } else {
                    carta.iniciarCarta(true);
                }
            }
            for (Card carta : compartidas) {
                carta.enfocar();
            }
        });

        if (!animacion) {
            return;
        }

        // Re-reparto animado: un beat con el hueco vacío para que el rewind se
        // lea, y cada corrida vuelve boca abajo VOLANDO desde el dealer (mismo
        // sistema/velocidad que repartir()).
        int pausa = Math.max(100, Math.round(REPARTIR_PAUSA * (2f / this.getJugadoresActivos())));
        int flight_dur = Math.max(150, pausa);
        final Card deal_origin = getDealerSeatAnchor();

        Helpers.pausar(pausa);

        for (Card carta : corridas) {
            GameFrame.getInstance().checkPause();
            final Card cc = carta;
            GameFrame.getInstance().getTapete().flyCardToSeat(cc, deal_origin, flight_dur, "misc/deal.wav", () -> cc.iniciarCarta());
        }
    }

    // Run-it-twice: reparte el SEGUNDO board (SIDE-B). Re-corre el run-out de las
    // calles posteriores al all-in (rit_allin_street+1 .. RIVER) avanzando la
    // calle local en lockstep, repartiendo con las fases RIT2 (host enviarRit2 /
    // cliente recibirCartas con run_it_twice_side_b ya puesto) y revelando cada
    // calle igual que el board vivo. Devuelve false si el reparto abortó
    // (lockdown / misdeal / desconexión). Llamar SOLO con setRunItTwiceSideB(true).
    private boolean repartirSideB(ArrayList<Player> resisten) {
        for (int s = rit_allin_street + 1; s <= RIVER && !isFin_de_la_transmision(); s++) {
            setStreetLocal(s);

            // CLON EXACTO del reparto de comunitarias de rondaApuestas (run-out de
            // CARA-A): label "decrypting" naranja + barra indeterminada tras 500ms,
            // y en el finally restaura el foreground de la pot_label y quita la
            // indeterminada. Así CARA-B se reparte visualmente igual que CARA-A.
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            ScheduledFuture<?> loadingTask = scheduler.schedule(() -> {
                Helpers.GUIRunAndWait(() -> {
                    GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setForeground(Color.ORANGE);
                    GameFrame.getInstance().getTapete().getCommunityCards().setPotTextImmediate(Translator.translate("zero_trust.decrypting_street"));
                    GameFrame.getInstance().getBarra_tiempo().setIndeterminate(true);
                });
            }, 500, TimeUnit.MILLISECONDS);

            boolean ok = false;
            try {
                ok = GameFrame.getInstance().isPartida_local()
                        ? enviarRit2Comunitarias(resisten)
                        : recibirCartasComunitarias();
            } finally {
                loadingTask.cancel(false);
                scheduler.shutdown();
                Helpers.GUIRunAndWait(() -> {
                    GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setForeground(
                            GameFrame.getInstance().getTapete().getCommunityCards().getBet_label().getForeground());
                    GameFrame.getInstance().getBarra_tiempo().setIndeterminate(false);
                });
            }

            if (!ok) {
                return false;
            }
            // Igual que el run-out normal: actualiza el bote (centrado, sin
            // bet_label, que actualizarContadoresTapete oculta en el run-out).
            actualizarContadoresTapete();
            destaparCartaComunitaria(s, resisten);
        }
        return true;
    }

    // Run-it-twice: liquidación de los DOS boards (ruta dedicada; el showdown
    // normal NO se toca). SIDE-A ya está en la mesa (rondaApuestas la repartió):
    // se paga la mitad-A de cada (side)pot, pausa para asimilar, rewind, se
    // reparte SIDE-B y se paga la mitad-B. Cada bote se parte con
    // splitPotForRunItTwice (mitades en céntimos 0.01; el pico indivisible no
    // se reparte y acaba en bote_sobrante al recalcularlo tras los dos boards)
    // y dentro de cada board se reparte entre los ganadores de ESE board
    // con calcularBoteParaGanador (mismo helper de producción). conta_win cuenta
    // UNA vez por jugador que gane ≥1 side (snapshot + corrección final, porque
    // showdown() incrementa por board vía setWinner). 'perdedores' refleja SIDE-B
    // (el board final en pantalla) para la cola común (actualizarCartasPerdedores).
    private void resolverRunItTwiceShowdown(ArrayList<Player> resisten) {
        // Cartas ya reveladas en el all-in; mirror del showdown normal (idempotente).
        awaitHandverifyBarrier();
        procesarCartasResistencia(resisten, false);

        // conta_win: snapshot para corregir el doble incremento de showdown().
        // pagar: snapshot para poder REVERTIR el settle de CARA-A si el reparto
        // de CARA-B aborta en MISDEAL (cancelarManoYDevolverApuestas anula la
        // mano ENTERA con refund íntegro de apuestas; dejar el medio bote de
        // CARA-A pendiente en pagar duplicaría dinero en todo lo que lee
        // stack+getPagar(): auditor, balance_backup.txt y filas de balance).
        //
        // Snapshot sobre TODOS los jugadores, no solo resisten: el pot principal
        // paga a resisten, pero los side pots pagan a HandPot.getPlayers(), y un
        // jugador que hizo all-in y luego SALIÓ (isExit) queda fuera de resisten
        // —el run loop lo filtra antes de genSidePots— pero sigue siendo
        // elegible en su side pot y puede cobrar en CARA-A. Snapshotear/revertir
        // solo resisten dejaría su medio bote sin revertir. Para los jugadores
        // que el settle no toca, el snapshot == valor actual y restaurar es no-op.
        HashMap<Player, Integer> contaWinSnapshot = new HashMap<>();
        HashMap<Player, Double> pagarSnapshot = new HashMap<>();
        for (Player p : GameFrame.getInstance().getJugadores()) {
            contaWinSnapshot.put(p, p.getContaWin());
            pagarSnapshot.put(p, p.getPagar());
        }
        java.util.HashSet<Player> wonAnySide = new java.util.HashSet<>();

        // Snapshot de las jugadas de SIDE-A (board en mesa): la tabla showdown
        // lleva UNA fila por jugador/mano, así que la jugada registrada
        // (hand_cards/hand_val) es la del primer board. A partir de aquí
        // silenciamos el SQL del showdown de los dos boards; la fila consolidada
        // se escribe al final con el pay total y winner = ganó algún side.
        HashMap<Player, Hand> ritShowdownHands = this.calcularJugadas(resisten);
        this.rit_suppress_showdown_sql = true;

        // Conservación del dinero: bote_sobrante (el resto indivisible heredado de
        // manos anteriores) se CONSUME en el split de los pots (8221). Hay que
        // recalcularlo tras los dos boards (= lo que no se pudo repartir), no
        // dejarlo stale: si no, la siguiente NUEVA_MANO lo resembraría en
        // bote_total (creación de dinero). Mirror del showdown normal, que SIEMPRE
        // reescribe bote_sobrante (12727 case-1 / 12887 default). Capturamos el
        // total de TODOS los pots (principal + sobrante + laterales) antes de pagar.
        double ritPotTotal = this.bote.getTotal() + this.bote_sobrante;
        for (HandPot sp = this.bote.getSidePot(); sp != null; sp = sp.getSidePot()) {
            ritPotTotal += sp.getTotal();
        }

        // ---- SIDE-A (board ya en mesa) ----
        double paidA = settleRunItTwiceBoard(resisten, 0, wonAnySide);
        GameFrame.getInstance().getRegistro().print(Translator.translate("runittwice.log_fin_a"));

        if (!GameFrame.TEST_MODE && !isFin_de_la_transmision()
                && !GameFrame.getInstance().getLocalPlayer().isExit()) {
            // Pausa para asimilar SIDE-A = la MISMA que la pausa de la cola tras
            // SIDE-B (1.5x con side pots), para que ambas caras esperen igual.
            // El guard de isExit() (mismo criterio que la pausa entre manos del
            // bucle principal) es crítico aquí: esta pausa corre BAJO
            // lock_contabilidad y finTransmision se queda esperando ese lock,
            // así que isFin_de_la_transmision() jamás puede izarse a tiempo.
            this.pausaConBarra(this.bote.getSide_pot_count() == 0 ? PAUSA_ENTRE_MANOS : Math.round(1.5f * PAUSA_ENTRE_MANOS));
        }

        // ---- SIDE-B: rewind + reparto ----
        // Solo deshacemos el coloreado del showdown de SIDE-A (pot_panel opaco
        // verde) para volver al estado de REPARTO. A partir de ahí CARA-B se
        // comporta IGUAL que CARA-A: el run-out muestra SOLO el bote (centrado,
        // sin bet_label) vía actualizarContadoresTapete. La alineación se mantiene
        // CENTER (el run-out ya centra), igual que el showdown que viene después.
        Helpers.GUIRun(() -> {
            CommunityCardsPanel cc = GameFrame.getInstance().getTapete().getCommunityCards();
            cc.getPot_panel().setOpaque(false);
            cc.getPot_label().setHorizontalAlignment(JLabel.CENTER);
            cc.getPot_label().setForeground(cc.getBet_label().getForeground());
            // El contador de manos sigue siendo el de ESTA mano (CARA-B es el mismo
            // reparto): debe quedar VISIBLE durante el rewind, igual que en el run-out
            // de CARA-A y que el reparto normal (~6288). Ocultarlo dejaba el hand_panel
            // amarillo de la última mano SIN número (parecía "texto en blanco" sobre
            // amarillo); el foreground negro ya lo fijó last_hand_on y nadie lo cambia.
            cc.getHand_label().setVisible(true);
        });
        // La barra arranca llena para CARA-B (tras la pausa quedó vacía).
        Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), 100);
        // Durante el reparto de SIDE-B (rewind animado + repartirSideB →
        // actualizarContadoresTapete) la label del bote NO debe arrastrar el
        // beneficio de SIDE-A: se limpia para volver al estado de REPARTO (sin
        // número de beneficio, igual que el run-out de SIDE-A).
        // settleRunItTwiceBoard(.,1,.) lo recalcula para el showdown de SIDE-B.
        this.beneficio_bote_principal = null;
        // La pot_label pasa a marcar CARA-B y se repinta YA, ANTES del rewind
        // animado, en estado de reparto (la MITAD de CARA-B, igual que durante el
        // run-out de CARA-A): sin el repintado arrastraría el texto del settle
        // de CARA-A (con su etiqueta CARA-A) durante toda la animación del
        // re-reparto y hasta que se complete la primera calle de SIDE-B.
        this.rit_pot_board_tag = Translator.translate("runittwice.pot_label_b");
        GameFrame.getInstance().setTapeteBote(splitPotForRunItTwice(this.bote_total)[0], this.beneficio_bote_principal);
        for (Player p : resisten) {
            p.repaintLastAction();
        }
        // Captura de las comunitarias de CARA-A (las "corridas": calles
        // posteriores al all-in) ANTES de que rebobinarComunitariasSideB las
        // sobrescriba con los valores de CARA-B. El reparto real de CARA-B no
        // repone estas cartas, así que la simulación de Montecarlo de CARA-B
        // tiene que excluirlas de la baraja (mismo criterio de calle que el
        // rewind). Las compartidas (calle del all-in y anteriores) ya están en
        // el board que monteCarlo retira por su cuenta.
        this.rit_side_a_runout_cards.clear();
        CommunityCardsPanel ccA = GameFrame.getInstance().getTapete().getCommunityCards();
        if (this.rit_allin_street < FLOP) {
            this.rit_side_a_runout_cards.add(ccA.getFlop1().getCartaComoEntero());
            this.rit_side_a_runout_cards.add(ccA.getFlop2().getCartaComoEntero());
            this.rit_side_a_runout_cards.add(ccA.getFlop3().getCartaComoEntero());
        }
        if (this.rit_allin_street < TURN) {
            this.rit_side_a_runout_cards.add(ccA.getTurn().getCartaComoEntero());
        }
        if (this.rit_allin_street < RIVER) {
            this.rit_side_a_runout_cards.add(ccA.getRiver().getCartaComoEntero());
        }
        // Rewind: retirar las comunitarias corridas y re-repartirlas boca abajo
        // con la animación de reparto del juego.
        rebobinarComunitariasSideB();
        setRunItTwiceSideB(true);
        boolean dealt = repartirSideB(resisten);
        setRunItTwiceSideB(false);

        // Igual que tras el run-out normal (Crupier ~12232): oculta la bet_label
        // de calle antes del showdown de CARA-B.
        GameFrame.getInstance().hideTapeteApuestas();

        // CARA-B abortó en MISDEAL: la mano entera está ANULADA
        // (cancelarManoYDevolverApuestas devolvió todas las apuestas y
        // rollbackAbortedHand la cerró en SQL con pot=0 y balances
        // post-refund). El settle de CARA-A debe revertirse — quedó pendiente
        // en pagar y nadie lo va a consolidar, pero el auditor y el
        // balance_backup.txt leen stack+getPagar() y le abonarían al ganador
        // de A un medio bote que los demás ya recuperaron con el refund.
        // conta_win vuelve al snapshot (una mano anulada no cuenta victorias)
        // y NO se escriben filas de showdown de una mano anulada. El abort
        // sin MISDEAL (p.ej. fin por salida del propio jugador local) sigue
        // el camino de siempre: CARA-A liquidada se queda como está.
        if (!dealt && this.mano_anulada) {
            for (Player p : GameFrame.getInstance().getJugadores()) {
                p.setPagar(pagarSnapshot.get(p));
                p.setContaWin(contaWinSnapshot.get(p));
            }
            this.rit_suppress_showdown_sql = false;
            return;
        }

        double paidB = 0;
        if (dealt && !isFin_de_la_transmision()) {
            paidB = settleRunItTwiceBoard(resisten, 1, wonAnySide);
            GameFrame.getInstance().getRegistro().print(Translator.translate("runittwice.log_fin_b"));
        }

        // Resto indivisible no repartido en ninguno de los dos boards → se arrastra
        // como bote_sobrante a la mano siguiente (conservación exacta del dinero).
        // Solo si SIDE-B se repartió (si abortó, cancelarManoYDevolverApuestas ya
        // gestionó el dinero y no debemos tocar el sobrante).
        if (dealt) {
            this.bote_sobrante = Math.max(0, Helpers.doubleClean(ritPotTotal - paidA - paidB));
        }

        // conta_win final: +1 solo si ganó algún side (override del doble conteo).
        for (Player p : resisten) {
            p.setContaWin(contaWinSnapshot.get(p) + (wonAnySide.contains(p) ? 1 : 0));
        }

        // SQL del showdown: UNA fila consolidada por jugador (no una por board),
        // mismo invariante que el showdown normal. pay/profit salen de getPagar()
        // final, que acumula lo ganado en ambos boards y todos los (side)pots;
        // winner = ganó algún side (igual semántica que conta_win). En un all-in
        // todas las cartas se muestran (destapar_resistencia), por eso tapadas=false.
        this.rit_suppress_showdown_sql = false;
        for (Player p : resisten) {
            this.sqlNewShowdown(p, ritShowdownHands.get(p), wonAnySide.contains(p), false);
        }
    }

    // Liquida UN board (el que está en la mesa) para run-it-twice: paga la mitad
    // (board: 0=SIDE-A, 1=SIDE-B) de cada (side)pot a los ganadores de ESE board.
    private double settleRunItTwiceBoard(ArrayList<Player> resisten, int board,
            java.util.HashSet<Player> wonAnySide) {
        boolean isSideB = (board == 1);
        double paidThisBoard = 0;

        // ---- Pot principal (elegibles = resisten); incluye bote_sobrante ----
        HashMap<Player, Hand> jugadas = this.calcularJugadas(resisten);
        HashMap<Player, Hand> ganadores = this.calcularGanadores(new HashMap<>(jugadas));
        double mainHalf = splitPotForRunItTwice(this.bote.getTotal() + this.bote_sobrante)[board];
        double[] cantidad = this.calcularBoteParaGanador(mainHalf, ganadores.size());
        // Beneficio del ganador del pot principal en ESTE board (cosmético, número
        // verde de la label del tapete): su parte (medio bote / nº ganadores) menos
        // su mitad de la apuesta de referencia. La apuesta se parte igual que el
        // bote (mismo split, floor a céntimo 0.01) → la suma de ambos boards =
        // beneficio real total salvo el pico no repartido (que va a bote_sobrante).
        // Mismo significado que beneficio_bote_principal del showdown normal
        // (cantidad - bote.getBet()), pero por board.
        this.beneficio_bote_principal = cantidad[0] - splitPotForRunItTwice(this.bote.getBet())[board];
        ArrayList<Card> cartas_usadas_jugadas = new ArrayList<>();
        Player unganador = null;

        for (Map.Entry<Player, Hand> e : ganadores.entrySet()) {
            Player ganador = e.getKey();
            Hand jugada = e.getValue();
            wonAnySide.add(ganador);
            unganador = ganador;
            // Highlight de la jugada ganadora (igual que el showdown normal):
            // recoge las cartas usadas y atenúa las hole cards NO usadas del ganador.
            ArrayList<Card> cartas = ganadores.size() == 1 ? jugada.getWinners() : jugada.getMano();
            for (Card carta : cartas) {
                if (!cartas_usadas_jugadas.contains(carta)) {
                    cartas_usadas_jugadas.add(carta);
                }
            }
            if (!cartas.contains(ganador.getHoleCard1())) {
                ganador.getHoleCard1().desenfocar();
            }
            if (!cartas.contains(ganador.getHoleCard2())) {
                ganador.getHoleCard2().desenfocar();
            }
            jugadas.remove(ganador);
            ganador.pagar(cantidad[0], null);
            // La franja "#1" del bote principal NO se pinta aquí: se difiere a
            // DESPUÉS de this.showdown() (más abajo) para no adelantarse a los
            // veredictos GANA/PIERDE. Igual que en el showdown normal y que las
            // marcas de los botes laterales (que ya van tras el showdown).
            // NO decrementamos bote_total: es UN bote corrido dos veces y la
            // variable conserva el total en ambas caras (la cola lo pone a 0 al
            // cerrar la mano). La LABEL sí muestra la mitad de cada cara, pero eso
            // es solo presentación (splitPotForRunItTwice en el run-out / paidShow
            // en el showdown), no toca la contabilidad.
            paidThisBoard += cantidad[0];
            GameFrame.getInstance().getRegistro().print(ganador.getNickname() + " (" + Card.collection2String(ganador.getHoleCards()) + Translator.translate("game.gana_bote_2") + Helpers.money2String(cantidad[0]) + ") -> " + jugada);
        }

        // Atenúa las comunitarias que NO forman parte de ninguna jugada ganadora
        // (mismo highlight que el showdown normal).
        for (Card carta : GameFrame.getInstance().getCartas_comunes()) {
            if (!cartas_usadas_jugadas.contains(carta)) {
                carta.desenfocar();
            }
        }

        // Bad beat de ESTE board (river propio): el perdedor iba ganando en el
        // turn con trío+ y el river le dio la vuelta. Se fija el campo ANTES del
        // showdown porque soundWinner/soundLoser (dentro de showdown) lo leen para
        // sonar badbeat.wav. Reset por board: SIDE-A no contamina SIDE-B.
        this.badbeat = false;
        for (Map.Entry<Player, Hand> e : jugadas.entrySet()) {
            if (badbeat(e.getKey(), unganador)) {
                this.badbeat = true;
            }
        }

        // Visual del board (revelar/destacar) con los ganadores+perdedores del pot
        // principal, ANTES de vaciar 'jugadas'. null en el diferido: run-it-twice
        // conserva su flujo de atenuado propio (ya desenfocó arriba en este
        // settleRunItTwiceBoard), no se difiere a la pasada 2.
        this.showdown(new HashMap<>(jugadas), ganadores, null);

        // Franja "#1" del bote principal: tras el showdown (no antes), y solo si
        // hay side pots. marcarBotePot deduplica entre CARA-A y CARA-B.
        if (this.bote.getSidePot() != null) {
            for (Player ganador_principal : ganadores.keySet()) {
                ganador_principal.marcarBotePot(1);
            }
        }

        for (Map.Entry<Player, Hand> e : jugadas.entrySet()) {
            GameFrame.getInstance().getRegistro().print(e.getKey().getNickname() + " " + Translator.translate("game.pierde_bote") + Helpers.money2String(cantidad[0]) + ")");
            if (isSideB) {
                this.perdedores.put(e.getKey(), e.getValue());
            }
        }

        // Desglose por bote para la pot_label cuando hay side pots (como el
        // showdown normal): #1{mitad principal} + #2{mitad lateral} ... con la
        // MITAD que ESTA cara juega. El beneficio NO va aquí (es ambiguo con
        // varios botes); el beneficio por jugador ya está en la franja negra.
        // El bote principal se muestra sin sobrante (igual que el modo normal).
        String bote_tapete = "#1{" + Helpers.money2String(splitPotForRunItTwice(this.bote.getTotal())[board]) + "}";

        // ---- Side pots ----
        HandPot current_pot = this.bote.getSidePot();
        int sec = 2;
        while (current_pot != null) {
            if (current_pot.getPlayers().size() == 1) {
                // Pot lateral no disputado: refund íntegro, UNA sola vez (SIDE-A);
                // no se parte entre boards (no hay competición).
                if (board == 0) {
                    // Solo aparece en el desglose de CARA-A (en CARA-B no se paga).
                    bote_tapete = bote_tapete + " + #" + String.valueOf(sec) + "{" + Helpers.money2String(current_pot.getTotal()) + "}";
                    Player only = current_pot.getPlayers().get(0);
                    only.pagar(current_pot.getTotal(), null);
                    only.marcarBotePot(sec);
                    paidThisBoard += current_pot.getTotal();
                    GameFrame.getInstance().getRegistro().print(only.getNickname() + " " + Translator.translate("game.recupera_bote_sobrante_secundario") + String.valueOf(sec) + " (" + Helpers.money2String(current_pot.getTotal()) + ")");
                    this.sqlUpdateShowdownPay(only);
                }
            } else {
                HashMap<Player, Hand> sjugadas = this.calcularJugadas(current_pot.getPlayers());
                HashMap<Player, Hand> sganadores = this.calcularGanadores(new HashMap<>(sjugadas));
                double sHalf = splitPotForRunItTwice(current_pot.getTotal())[board];
                double[] sCantidad = this.calcularBoteParaGanador(sHalf, sganadores.size());
                bote_tapete = bote_tapete + " + #" + String.valueOf(sec) + "{" + Helpers.money2String(sHalf) + "}";
                for (Map.Entry<Player, Hand> e : sganadores.entrySet()) {
                    Player ganador = e.getKey();
                    Hand jugada = e.getValue();
                    wonAnySide.add(ganador);
                    sjugadas.remove(ganador);
                    ganador.pagar(sCantidad[0], null);
                    ganador.marcarBotePot(sec);
                    paidThisBoard += sCantidad[0];
                    GameFrame.getInstance().getRegistro().print(ganador.getNickname() + " (" + Card.collection2String(ganador.getHoleCards()) + Translator.translate("game.gana_bote_secundario") + String.valueOf(sec) + " (" + Helpers.money2String(sCantidad[0]) + ") -> " + jugada);
                    this.sqlUpdateShowdownPay(ganador);
                }
                for (Map.Entry<Player, Hand> e : sjugadas.entrySet()) {
                    GameFrame.getInstance().getRegistro().print(e.getKey().getNickname() + " " + Translator.translate("game.pierde_bote_secundario") + String.valueOf(sec) + " (" + Helpers.money2String(sCantidad[0]) + ")");
                    if (isSideB) {
                        perdedores.put(e.getKey(), e.getValue());
                    }
                }
            }
            current_pot = current_pot.getSidePot();
            sec++;
        }

        // Label del bote de ESTE board, centrada (como el showdown normal):
        // - con side pots: desglose por bote (fondo negro/blanco), SIN beneficio
        //   en paréntesis (ambiguo con varios botes; el beneficio por jugador va
        //   en la franja negra), igual que el showdown normal con side pots.
        // - un único bote: número repartido + beneficio (fondo verde).
        if (this.bote.getSidePot() != null) {
            setPotBackground(Color.BLACK);
            final String bote_tapete_final = bote_tapete;
            Helpers.GUIRun(() -> {
                GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setForeground(Color.WHITE);
                GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setHorizontalAlignment(JLabel.CENTER);
            });
            GameFrame.getInstance().setTapeteBote(bote_tapete_final);
        } else {
            final double paidShow = paidThisBoard;
            setPotBackground(Color.GREEN);
            Helpers.GUIRun(() -> {
                GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setForeground(Color.BLACK);
                GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setHorizontalAlignment(JLabel.CENTER);
            });
            GameFrame.getInstance().setTapeteBote(paidShow, this.beneficio_bote_principal);
        }

        return paidThisBoard;
    }

    // Run-it-twice SIDE-B (verificable como el board vivo): reparte la
    // calle actual (this.street) del SEGUNDO board desde offsets FRESCOS del
    // MEGAPACKET (las posiciones libres tras el river de SIDE-A: offset vivo +
    // RIT2_BOARD_SPAN), bajo fases RIT2_* y comandos RIT2_*_PIECE, y emite/absorbe
    // un COMM_REVEAL con código de calle SIDE-B (STREET_RIT2_*) para cerrar el
    // cross-recipient fork igual que el board vivo. abortOnFail=true: SIDE-B lleva
    // dinero (medio bote), no es cosmético como rabbit.
    private boolean enviarRit2Comunitarias(java.util.ArrayList<Player> resisten) {
        java.util.logging.Logger.getLogger(Crupier.class.getName()).log(java.util.logging.Level.INFO, "Initiating SRA SIDE-B street unlock: {0}", street);

        if (this.local_sra_unlock == null || this.local_sra_unlock_community == null || this.active_crypto_ring == null) {
            cancelarManoYDevolverApuestas("peer.state_inconsistent");
            return false;
        }

        int numPlayers = this.active_crypto_ring.length;
        int numCards = (street == Crupier.FLOP) ? 3 : 1;

        int offset = numPlayers * 2;
        if (street == Crupier.FLOP) {
            offset += 1;
        } else if (street == Crupier.TURN) {
            offset += 1 + 3 + 1;
        } else if (street == Crupier.RIVER) {
            offset += 1 + 3 + 1 + 1 + 1;
        }
        offset += RIT2_BOARD_SPAN;

        // El segundo board debe caber en el mazo (52 cartas * 32 bytes). Con el
        // cap de jugadores del juego siempre cabe, pero abortamos limpio si no.
        if (this.local_mega_packet == null || (offset + numCards) * 32 > this.local_mega_packet.length) {
            LOGGER.log(Level.SEVERE, "SIDE-B offset {0}+{1} exceeds deck — aborting hand", new Object[]{offset, numCards});
            cancelarManoYDevolverApuestas("rit.sideb_offset_overflow");
            return false;
        }

        int unlockPhase = rit2PhaseForStreet(street);
        String pieceCommand = (street == Crupier.FLOP) ? "RIT2_FLOP_PIECE"
                : (street == Crupier.TURN) ? "RIT2_TURN_PIECE" : "RIT2_RIVER_PIECE";

        int[] hostIndices = cascadeAndDealCommunityPieces(offset, numCards, unlockPhase, pieceCommand, true);
        if (hostIndices == null) {
            return false;
        }

        if (street == Crupier.FLOP) {
            GameFrame.getInstance().getFlop1().actualizarConValorNumerico(hostIndices[0] + 1);
            GameFrame.getInstance().getFlop2().actualizarConValorNumerico(hostIndices[1] + 1);
            GameFrame.getInstance().getFlop3().actualizarConValorNumerico(hostIndices[2] + 1);
        } else if (street == Crupier.TURN) {
            GameFrame.getInstance().getTurn().actualizarConValorNumerico(hostIndices[0] + 1);
        } else if (street == Crupier.RIVER) {
            GameFrame.getInstance().getRiver().actualizarConValorNumerico(hostIndices[0] + 1);
        }

        // COMM_REVEAL con código de calle SIDE-B — idéntico patrón al board vivo
        // (absorber SOLO si el broadcast tuvo éxito, o host y clientes divergen).
        Object[] recsig = buildCommunityRevealRecordAndSig(mapJavaStreetToRit2Wire(street), hostIndices);
        if (recsig != null) {
            byte[] record = (byte[]) recsig[0];
            byte[] sig = (byte[]) recsig[1];
            boolean broadcastOk = false;
            try {
                String comando = "COMM_REVEAL#"
                        + Base64.getEncoder().encodeToString(record)
                        + "#" + Base64.getEncoder().encodeToString(sig);
                broadcastGAMECommandFromServer(comando, null);
                broadcastOk = true;
            } catch (RuntimeException ex) {
                LOGGER.log(Level.SEVERE, "Failed to broadcast SIDE-B COMM_REVEAL for street " + street, ex);
            }
            if (broadcastOk) {
                absorbActionIntoChain(GameFrame.getInstance().getNick_local(), record, sig);
            }
        }

        return true;
    }

    // POCKET-like cascade comunitario: construimos una copia per-recipient
    // (host + cada humano remoto), cada una con todos los locks salvo el del
    // destinatario, y la broadcastamos como *_PIECE. El destinatario aplica
    // su propio unlock localmente y verifica via resolveCardIndex (-1 ⇒
    // lockdown). El host es destinatario también, pero su copia NO le aplica
    // localmente su propio unlock antes de la cascade: así garantizamos que
    // ningún helper vea bytes en genesis durante el unlock por red (siempre
    // queda al menos el host_lock encima). Tras la cascade, el host aplica
    // su unlock local sobre SU copia para resolver los chunks y devolverlos
    // al caller, que actualiza su UI.
    //
    // Si abortOnFail==true, los fallos disparan cancelarManoYDevolverApuestas
    // (path vivo). Si false, simplemente devolvemos null (path rabbit: una
    // pieza que no llega no aborta la mano, sólo deja la carta tapada).
    private int[] cascadeAndDealCommunityPieces(int offset, int numCards, int unlockPhase, String pieceCommand, boolean abortOnFail) {
        String hostNick = GameFrame.getInstance().getNick_local();
        HashMap<String, Integer> nick2idx = new HashMap<>();
        for (int i = 0; i < this.active_crypto_ring.length; i++) {
            nick2idx.put(this.active_crypto_ring[i], i);
        }

        ArrayList<String> remoteHumans = new ArrayList<>();
        for (String nick : this.active_crypto_ring) {
            if (nick.equals(hostNick)) {
                continue;
            }
            Participant p = GameFrame.getInstance().getParticipantes().get(nick);
            if (p != null && !p.isCpu()) {
                remoteHumans.add(nick);
            }
        }

        // Community dealing VERIFICABLE (chain anclado al MEGAPACKET).
        // Cada copy de recipient X ancla a megapacket[offset+j]; el host y los bots
        // extienden localmente con prueba DLEQ, los helpers vivos vía REQ_SRA_UNLOCK_CHAIN.
        // El tail de cada copy es la pieza single-locked por X (formato idéntico al batch
        // viejo → el cliente y el broadcast no cambian). Cierra el oráculo community: el
        // helper ancla a SU megapacket por índice (cegado imposible) y GATE 6 rechaza
        // cualquier strip que revele genesis (extracción de una comunitaria antes de tiempo).
        java.util.Map<String, String[]> commChains = new java.util.LinkedHashMap<>();
        String[] hostCommChain = new String[numCards];
        java.util.Arrays.fill(hostCommChain, "");
        commChains.put(hostNick, hostCommChain);
        for (String r : remoteHumans) {
            String[] arr = new String[numCards];
            java.util.Arrays.fill(arr, "");
            commChains.put(r, arr);
        }

        // El host quita su community-lock de cada copy salvo la suya (la abre localmente).
        // El lock se deriva del unlock (getUnlockScalar es involutivo): local_sra_lock_community
        // es null tras una recuperación de mano (solo se restaura el unlock vía
        // SRAKEYS_COMMUNITY@), mientras que local_sra_unlock_community siempre está disponible
        // aquí (lo garantiza el guard de enviarCartasComunitarias). commitment(lock derivado)
        // == peer_k_community[host], así que la prueba DLEQ verifica igual.
        byte[] hostCommunityLock = RistrettoSRA.getUnlockScalar(this.local_sra_unlock_community);
        if (!extendCommunityChainsForSigner(commChains, offset, numCards, hostNick, hostNick, hostCommunityLock)) {
            if (abortOnFail) {
                cancelarManoYDevolverApuestas("zero_trust.card_resolve_failed");
            }
            return null;
        }
        // Los bots quitan su community-lock de TODAS las copies (no son recipients).
        for (String nick : this.active_crypto_ring) {
            Participant pp = GameFrame.getInstance().getParticipantes().get(nick);
            if (pp != null && pp.isCpu()) {
                if (pp.getSra_unlock_community() == null) {
                    if (abortOnFail) {
                        cancelarManoYDevolverApuestas("peer.bot_no_token");
                    }
                    return null;
                }
                byte[] botCommunityLock = RistrettoSRA.getUnlockScalar(pp.getSra_unlock_community());
                if (!extendCommunityChainsForSigner(commChains, offset, numCards, null, nick, botCommunityLock)) {
                    if (abortOnFail) {
                        cancelarManoYDevolverApuestas("zero_trust.card_resolve_failed");
                    }
                    return null;
                }
            }
        }

        // Cada helper humano H quita su community-lock de las copies de X != H, con prueba,
        // vía REQ_SRA_UNLOCK_CHAIN (vivo) o extendido localmente con su testamento (exit).
        for (String h : remoteHumans) {
            Participant ph = GameFrame.getInstance().getParticipantes().get(h);
            if (ph == null) {
                if (abortOnFail) {
                    cancelarManoYDevolverApuestas("peer.community_unlock_no_testament");
                }
                return null;
            }

            if (!ph.isExit()) {
                java.util.List<UnlockChainWire.ReqItem> reqItems = new java.util.ArrayList<>();
                for (java.util.Map.Entry<String, String[]> e : commChains.entrySet()) {
                    if (!e.getKey().equals(h)) {
                        reqItems.add(new UnlockChainWire.ReqItem(nick2idx.get(e.getKey()), offset,
                                java.util.Arrays.asList(e.getValue())));
                    }
                }
                java.util.List<UnlockChainWire.RespItem> resp =
                        requestRemoteUnlockChain(h, ph, unlockPhase, reqItems);
                if (resp != null) {
                    java.util.Set<String> covered = new java.util.HashSet<>();
                    for (UnlockChainWire.RespItem ri : resp) {
                        String recip = (ri.peerIdx >= 0 && ri.peerIdx < this.active_crypto_ring.length)
                                ? this.active_crypto_ring[ri.peerIdx] : null;
                        if (recip != null && !recip.equals(h) && commChains.containsKey(recip)
                                && ri.chains.size() == numCards) {
                            commChains.put(recip, ri.chains.toArray(new String[0]));
                            covered.add(recip);
                        }
                    }
                    // El helper debe haber cubierto TODOS los recipients != h (paridad cabo 2).
                    boolean allCovered = true;
                    for (String recip : commChains.keySet()) {
                        if (!recip.equals(h) && !covered.contains(recip)) {
                            allCovered = false;
                            break;
                        }
                    }
                    if (!allCovered) {
                        if (abortOnFail) {
                            warnMaliciousPeer(h, "zero_trust.community_unlock_refused");
                            cancelarManoYDevolverApuestas("zero_trust.community_unlock_refused");
                        }
                        return null;
                    }
                } else if (ph.getSra_unlock_community() != null) {
                    // Testamento: entrega SOLO la mitad community; el host extiende local.
                    byte[] hCommunityLock = RistrettoSRA.getUnlockScalar(ph.getSra_unlock_community());
                    if (!extendCommunityChainsForSigner(commChains, offset, numCards, h, h, hCommunityLock)) {
                        if (abortOnFail) {
                            cancelarManoYDevolverApuestas("zero_trust.card_resolve_failed");
                        }
                        return null;
                    }
                } else {
                    // Peer vivo, NO respondió, no testament. REFUSAL → para la timba.
                    if (abortOnFail) {
                        warnMaliciousPeer(h, "zero_trust.community_unlock_refused");
                        cancelarManoYDevolverApuestas("zero_trust.community_unlock_refused");
                    }
                    return null;
                }
            } else if (ph.getSra_unlock_community() != null) {
                byte[] hCommunityLock = RistrettoSRA.getUnlockScalar(ph.getSra_unlock_community());
                if (!extendCommunityChainsForSigner(commChains, offset, numCards, h, h, hCommunityLock)) {
                    if (abortOnFail) {
                        cancelarManoYDevolverApuestas("zero_trust.card_resolve_failed");
                    }
                    return null;
                }
            } else {
                // Peer ya salió sin dejar testament community. "Se fue sin dar testamento"
                // — excepción del usuario: misdeal pero la timba continúa.
                if (abortOnFail) {
                    cancelarManoYDevolverApuestas("peer.community_unlock_no_testament");
                }
                return null;
            }
        }

        // El host verifica cada cadena final contra peer_k_community y toma el tail
        // (la pieza single-locked por su recipient). Una cadena que no verifica = un peer
        // devolvió algo no probado.
        HashMap<String, byte[]> copies = new HashMap<>();
        for (java.util.Map.Entry<String, String[]> e : commChains.entrySet()) {
            byte[] copy = new byte[numCards * 32];
            for (int j = 0; j < numCards; j++) {
                int pointIdx = offset + j;
                byte[] point = Arrays.copyOfRange(this.local_mega_packet, pointIdx * 32, (pointIdx + 1) * 32);
                java.util.List<DealChain.Entry> ch = DealChain.parse(e.getValue()[j]);
                if (ch == null || !DealChain.verify(point, ch, peer_k_community)) {
                    LOGGER.log(Level.SEVERE, "ZERO-TRUST: community chain for recipient {0} piece {1} failed verification", new Object[]{e.getKey(), j});
                    if (abortOnFail) {
                        cancelarManoYDevolverApuestas("zero_trust.card_resolve_failed");
                    }
                    return null;
                }
                byte[] tail = DealChain.tail(point, ch);
                System.arraycopy(tail, 0, copy, j * 32, 32);
            }
            copies.put(e.getKey(), copy);
        }

        // Resolver la copia del host (queda sólo el lock community propio del host).
        byte[] hostFinal = RistrettoSRA.applyCommutativeLock(copies.get(hostNick), this.local_sra_unlock_community);
        int[] hostIndices = new int[numCards];
        for (int i = 0; i < numCards; i++) {
            byte[] chunk = Arrays.copyOfRange(hostFinal, i * 32, (i + 1) * 32);
            int idx = RistrettoSRA.resolveCardIndex(chunk);
            if (idx < 0) {
                if (abortOnFail) {
                    cancelarManoYDevolverApuestas("zero_trust.card_resolve_failed");
                }
                return null;
            }
            hostIndices[i] = idx;
        }

        // Difundir las piezas a cada humano remoto. broadcastGAMECommandFromServer
        // manda a todos los clientes; sólo el destinatario podrá descifrar su pieza.
        for (String r : remoteHumans) {
            try {
                String nickB64 = Base64.getEncoder().encodeToString(r.getBytes("UTF-8"));
                String payloadB64 = Base64.getEncoder().encodeToString(copies.get(r));
                broadcastGAMECommandFromServer(pieceCommand + "#" + nickB64 + "#" + payloadB64, null);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error broadcasting community piece for " + r, e);
                if (abortOnFail) {
                    cancelarManoYDevolverApuestas("peer.broadcast_failed");
                }
                return null;
            }
        }

        return hostIndices;
    }

    private boolean recibirCartasComunitarias() {
        boolean piece_ok = false;
        boolean reveal_ok = false;

        // Observer mode: peers que no estan en active_crypto_ring de esta
        // mano (joiner pasivo durante recover-timba, calentando o spectator)
        // no tienen sra_unlock para ella. El host NO les direcciona
        // FLOP_PIECE/TURN_PIECE/RIVER_PIECE (cascadeAndDealCommunityPieces
        // itera solo el ring para los recipients), asi que esperar la
        // pieza colgaria el hilo principal indefinidamente. El observer
        // pinta el board en cleartext leyendo los indices del propio
        // COMM_REVEAL firmado por el host (mismo patron que
        // recibirCartasResistencia ya usa para POTCARDS cuando isCalentando
        // /isSpectator). Sin dinero en la mano, fiarse de la palabra del
        // host es seguro — el observer no participa de la criptografia.
        String localNick = GameFrame.getInstance().getNick_local();
        boolean inRing = false;
        if (this.active_crypto_ring != null && localNick != null) {
            for (String ringNick : this.active_crypto_ring) {
                if (localNick.equals(ringNick)) {
                    inRing = true;
                    break;
                }
            }
        }
        Player myPlayer = GameFrame.getInstance().getLocalPlayer();
        final boolean iAmObserver = !inRing
                || this.local_sra_unlock == null
                || this.local_sra_unlock_community == null
                || (myPlayer != null && (myPlayer.isCalentando() || myPlayer.isSpectator()));
        if (iAmObserver) {
            piece_ok = true;
        }

        // Identity: client also waits for the host's signed
        // COMM_REVEAL announcement of these community cards, verifies the sig
        // with the host's pubkey, compares the announced indices against the
        // locally-decoded PIECE indices (mismatch ⇒ cross-recipient fork,
        // lockdown) and absorbs the record+sig into H_t. Reveal is only
        // required when the local chain is initialised — when chain==null
        // (legacy interop or recovery without restore) the client falls back
        // to PIECE-only verification (pre-Phase-3 behaviour) so the hand
        // still plays out.
        //
        // Observer: necesita el COMM_REVEAL aunque su chain sea null porque
        // de ahi extrae los indices del board para el UI (no tiene piece
        // que decodificar).
        final boolean chainRequiresReveal = (this.hand_state_chain != null);
        if (!chainRequiresReveal && !iAmObserver) {
            reveal_ok = true;
        }

        // Cada cliente humano recibe SU PROPIA pieza cifrada (FLOP_PIECE
        // /TURN_PIECE/RIVER_PIECE) con los locks de los demás ya quitados; el
        // cliente aplica su propio sra_unlock y resuelve mediante
        // resolveCardIndex. Un -1 significa que el host envió bytes que no son
        // genesis tras mi unlock — basura o smuggling cross-slot — y disparamos
        // lockdown.
        //
        // Sin timeout: la cascade del host puede tardar minutos con un ring grande
        // y clientes lentos. El cliente espera indefinidamente — TCP nos garantiza
        // que la pieza llega o el socket muere. MISDEAL del host es la única señal
        // de que la mano se cancela legítimamente.
        int[] pieceIndices = null;
        byte[] revealRecord = null;
        byte[] revealSig = null;
        int expectedNumCards = (street == Crupier.FLOP) ? 3 : 1;
        // Run-it-twice SIDE-B: durante el reparto del segundo board esperamos los
        // comandos RIT2_*_PIECE y el COMM_REVEAL con código de calle SIDE-B
        // (STREET_RIT2_*), no los del board vivo.
        final boolean rit2 = this.run_it_twice_side_b;
        final int expectedWireStreet = rit2 ? mapJavaStreetToRit2Wire(street) : mapJavaStreetToWire(street);
        do {
            synchronized (this.getReceived_commands()) {
                java.util.ArrayList<String> rejected = new java.util.ArrayList<>();
                while ((!piece_ok || !reveal_ok) && !this.getReceived_commands().isEmpty()) {
                    String comando = this.received_commands.poll();
                    String[] partes = comando.split("#");

                    try {
                        String expectedCmd;
                        if (rit2) {
                            expectedCmd = (street == Crupier.FLOP) ? "RIT2_FLOP_PIECE"
                                    : (street == Crupier.TURN) ? "RIT2_TURN_PIECE"
                                            : (street == Crupier.RIVER) ? "RIT2_RIVER_PIECE" : null;
                        } else {
                            expectedCmd = (street == Crupier.FLOP) ? "FLOP_PIECE"
                                    : (street == Crupier.TURN) ? "TURN_PIECE"
                                            : (street == Crupier.RIVER) ? "RIVER_PIECE" : null;
                        }
                        if (iAmObserver && expectedCmd != null && partes.length >= 5
                                && partes[2].equals(expectedCmd)) {
                            // Observer no esta en el ring: ninguna pieza es para
                            // el. Drop silencioso para no contaminar la cola de
                            // rejected con piezas inservibles del resto de streets.
                            continue;
                        }
                        if (expectedCmd != null && partes.length >= 5 && partes[2].equals(expectedCmd) && !piece_ok) {
                            String targetNick = new String(java.util.Base64.getDecoder().decode(partes[3]), "UTF-8");
                            if (!targetNick.equals(localNick)) {
                                // Pieza de otro destinatario; no la puedo
                                // descifrar (los demás siguen con su lock).
                                // Drop silencioso.
                                continue;
                            }
                            byte[] piece = java.util.Base64.getDecoder().decode(partes[4]);
                            int expectedLen = (street == Crupier.FLOP) ? 96 : 32;
                            if (piece == null || piece.length != expectedLen) {
                                LOGGER.log(Level.SEVERE,
                                        "ZERO-TRUST: community piece for street {0} has bad length {1} — host sent garbage, lockdown",
                                        new Object[]{street, (piece == null ? -1 : piece.length)});
                                triggerSecurityLockdown(Translator.translate("zero_trust.host_community_garbage"));
                                return false;
                            }
                            // Dual-lock: tras la rotación, las community pieces están cifradas
                            // con scalars de community. El recipient aplica SU unlock_community
                            // para descifrar.
                            byte[] unlocked = RistrettoSRA.applyCommutativeLock(piece, this.local_sra_unlock_community);
                            int[] indices = new int[expectedNumCards];
                            for (int k = 0; k < expectedNumCards; k++) {
                                byte[] chunk = Arrays.copyOfRange(unlocked, k * 32, (k + 1) * 32);
                                int idx = RistrettoSRA.resolveCardIndex(chunk);
                                if (idx < 0) {
                                    LOGGER.log(Level.SEVERE,
                                            "ZERO-TRUST: community piece for street {0} chunk {1} does NOT resolve to genesis — host sent wrong-slot bytes, lockdown",
                                            new Object[]{street, k});
                                    triggerSecurityLockdown(Translator.translate("zero_trust.host_community_garbage"));
                                    return false;
                                }
                                indices[k] = idx;
                            }
                            pieceIndices = indices;
                            piece_ok = true;
                        } else if (partes.length >= 5 && partes[2].equals("COMM_REVEAL") && !reveal_ok) {
                            try {
                                byte[] candidateRecord = java.util.Base64.getDecoder().decode(partes[3]);
                                byte[] candidateSig = java.util.Base64.getDecoder().decode(partes[4]);
                                // Identity: reject silently if the
                                // reveal is for a different street than the one we
                                // are processing right now. Avoids lockdown on a
                                // duplicate/stale COMM_REVEAL left over from the
                                // previous street (TCP order should prevent this
                                // in normal operation, but a buggy or malicious
                                // host shouldn't be able to wedge us into lockdown
                                // by sending the wrong reveal early).
                                if (candidateRecord.length != CanonicalActionRecord.RECORD_BYTES
                                        || CanonicalActionRecord.readActionType(candidateRecord) != CanonicalActionRecord.ACTION_COMMUNITY
                                        || CanonicalActionRecord.readStreet(candidateRecord) != expectedWireStreet) {
                                    LOGGER.log(Level.WARNING,
                                            "Dropping stale/foreign COMM_REVEAL during street {0} drain", street);
                                    continue;
                                }
                                revealRecord = candidateRecord;
                                revealSig = candidateSig;
                                reveal_ok = true;
                            } catch (Exception decodeEx) {
                                LOGGER.log(Level.SEVERE,
                                        "ZERO-TRUST: COMM_REVEAL wire malformed for street {0} — lockdown",
                                        street);
                                triggerSecurityLockdown(Translator.translate("zero_trust.host_community_garbage"));
                                return false;
                            }
                        } else if (partes.length >= 4 && partes[2].equals("MISDEAL")) {
                            String motivo = new String(java.util.Base64.getDecoder().decode(partes[3]), "UTF-8");
                            cancelarManoYDevolverApuestas(motivo, false);
                            return false;
                        } else {
                            rejected.add(comando);
                        }
                    } catch (Exception ex) {
                        rejected.add(comando);
                    }
                }
                if (!rejected.isEmpty()) {
                    this.getReceived_commands().addAll(rejected);
                }
            }

            if ((!piece_ok || !reveal_ok) && !isFin_de_la_transmision()) {
                synchronized (this.getReceived_commands()) {
                    try {
                        this.received_commands.wait(WAIT_QUEUES);
                    } catch (InterruptedException ex) {
                    }
                }
            }
        } while ((!piece_ok || !reveal_ok) && !isFin_de_la_transmision());

        if (!piece_ok) {
            return false;
        }

        // Observer mode: el board se pinta a partir de los indices anunciados
        // en el COMM_REVEAL firmado por el host. No hay PIECE que decodificar
        // ni chain que absorber. Validacion minima de la forma del record
        // (longitud, action_type, street) — si esta corrupto, log warning y
        // se sigue observando sin pintar este street (no abortamos la mano,
        // que el observer no tiene poder para decidir misdeal). No se aplica
        // lockdown porque el observer no tiene state critico que proteger;
        // los peers reales del ring tienen su propia verificacion en el
        // bloque chainRequiresReveal mas abajo.
        if (iAmObserver) {
            if (revealRecord == null) {
                return true;
            }
            try {
                if (revealRecord.length != CanonicalActionRecord.RECORD_BYTES
                        || CanonicalActionRecord.readActionType(revealRecord) != CanonicalActionRecord.ACTION_COMMUNITY
                        || CanonicalActionRecord.readStreet(revealRecord) != expectedWireStreet) {
                    LOGGER.log(Level.WARNING,
                            "Observer: COMM_REVEAL for street {0} malformed — board not painted, hand continues",
                            street);
                    return true;
                }
                long packed = CanonicalActionRecord.readAmountCents(revealRecord);
                pieceIndices = CanonicalActionRecord.unpackCommunityCards(packed, expectedNumCards);
            } catch (RuntimeException ex) {
                LOGGER.log(Level.WARNING,
                        "Observer: COMM_REVEAL unpack failed for street " + street
                        + " — board not painted, hand continues",
                        ex);
                return true;
            }
        }

        // Identity: cross-check the announce against the piece.
        // chainRequiresReveal=false means legacy/recovery-degraded mode and the
        // reveal was never expected — skip the cross-check entirely.
        if (chainRequiresReveal && revealRecord != null && revealSig != null) {
            try {
                if (revealRecord.length != CanonicalActionRecord.RECORD_BYTES) {
                    LOGGER.log(Level.SEVERE,
                            "ZERO-TRUST: COMM_REVEAL record wrong length ({0} != {1}) — lockdown",
                            new Object[]{revealRecord.length, CanonicalActionRecord.RECORD_BYTES});
                    triggerSecurityLockdown(Translator.translate("zero_trust.host_community_garbage"));
                    return false;
                }
                if (CanonicalActionRecord.readActionType(revealRecord) != CanonicalActionRecord.ACTION_COMMUNITY) {
                    LOGGER.log(Level.SEVERE,
                            "ZERO-TRUST: COMM_REVEAL record action_type != ACTION_COMMUNITY — lockdown");
                    triggerSecurityLockdown(Translator.translate("zero_trust.host_community_garbage"));
                    return false;
                }
                int recordStreet = CanonicalActionRecord.readStreet(revealRecord);
                if (recordStreet != expectedWireStreet) {
                    LOGGER.log(Level.SEVERE,
                            "ZERO-TRUST: COMM_REVEAL street {0} != current street {1} — lockdown",
                            new Object[]{recordStreet, expectedWireStreet});
                    triggerSecurityLockdown(Translator.translate("zero_trust.host_community_garbage"));
                    return false;
                }
                byte[] hostPubkey = null;
                String hostNick = GameFrame.getInstance().getSala_espera() != null
                        ? GameFrame.getInstance().getSala_espera().getServer_nick() : null;
                if (hostNick != null) {
                    Participant hostPar = GameFrame.getInstance().getParticipantes().get(hostNick);
                    if (hostPar != null) {
                        hostPubkey = hostPar.getIdentity_pubkey();
                    }
                }
                if (hostPubkey == null || !IdentityManager.verifyAction(hostPubkey, revealRecord, revealSig)) {
                    LOGGER.log(Level.SEVERE,
                            "ZERO-TRUST: COMM_REVEAL signature invalid for street {0} — lockdown",
                            street);
                    triggerSecurityLockdown(Translator.translate("zero_trust.host_community_garbage"));
                    return false;
                }
                long packed = CanonicalActionRecord.readAmountCents(revealRecord);
                int[] announceIndices = CanonicalActionRecord.unpackCommunityCards(packed, expectedNumCards);
                if (!Arrays.equals(pieceIndices, announceIndices)) {
                    LOGGER.log(Level.SEVERE,
                            "ZERO-TRUST: COMM_REVEAL announced cards differ from PIECE-decoded indices for street {0} (announce={1}, piece={2}) — cross-recipient fork, lockdown",
                            new Object[]{street, java.util.Arrays.toString(announceIndices), java.util.Arrays.toString(pieceIndices)});
                    triggerSecurityLockdown(Translator.translate("zero_trust.host_community_garbage"));
                    return false;
                }
                // All checks passed: absorb into H_t with the host's nick as the
                // "actor" (host is always in active_crypto_ring, so the isInActiveCryptoRing
                // guard in absorbActionIntoChain passes).
                if (hostNick != null) {
                    absorbActionIntoChain(hostNick, revealRecord, revealSig);
                }
            } catch (RuntimeException ex) {
                LOGGER.log(Level.SEVERE,
                        "ZERO-TRUST: COMM_REVEAL processing failed for street " + street + " — lockdown", ex);
                triggerSecurityLockdown(Translator.translate("zero_trust.host_community_garbage"));
                return false;
            }
        }

        // Finally update UI with the verified indices.
        if (street == Crupier.FLOP) {
            GameFrame.getInstance().getFlop1().actualizarConValorNumerico(pieceIndices[0] + 1);
            GameFrame.getInstance().getFlop2().actualizarConValorNumerico(pieceIndices[1] + 1);
            GameFrame.getInstance().getFlop3().actualizarConValorNumerico(pieceIndices[2] + 1);
            this.flop_revealed = true;
        } else if (street == Crupier.TURN) {
            GameFrame.getInstance().getTurn().actualizarConValorNumerico(pieceIndices[0] + 1);
            this.turn_revealed = true;
        } else if (street == Crupier.RIVER) {
            GameFrame.getInstance().getRiver().actualizarConValorNumerico(pieceIndices[0] + 1);
            this.river_revealed = true;
        }

        return true;
    }

    private ArrayList<Player> rondaApuestas(int street, ArrayList<Player> resisten) {

        LOGGER.log(Level.INFO, "HAND {0}: betting round {1}",
                new Object[]{String.valueOf(getMano()), STREETS[street - 1]});

        disableAllPlayersTimeout();

        java.util.Iterator<Player> iterator = resisten.iterator();

        while (iterator.hasNext()) {
            Player jugador = iterator.next();
            if (!jugador.isActivo()) {
                iterator.remove();
                continue;
            }
            if (street == PREFLOP && GameFrame.getInstance().getLocalPlayer() != jugador && ((RemotePlayer) jugador).getBot() != null) {
                ((RemotePlayer) jugador).getBot().resetBot();
            }
        }

        setStreetLocal(street);

        if (this.street == Crupier.FLOP) {
            this.flop_players.clear();
            this.flop_players.addAll(resisten);
        }

        if (street > PREFLOP) {
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            ScheduledFuture<?> loadingTask = scheduler.schedule(() -> {
                Helpers.GUIRunAndWait(() -> {
                    GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setForeground(Color.ORANGE);
                    GameFrame.getInstance().getTapete().getCommunityCards().setPotTextImmediate(Translator.translate("zero_trust.decrypting_street"));
                    GameFrame.getInstance().getBarra_tiempo().setIndeterminate(true);
                });
            }, 500, TimeUnit.MILLISECONDS);

            boolean success = false;
            try {
                if (GameFrame.getInstance().isPartida_local()) {
                    success = enviarCartasComunitarias(resisten);
                } else {
                    success = recibirCartasComunitarias();
                }
            } finally {
                loadingTask.cancel(false);
                scheduler.shutdown();
                Helpers.GUIRunAndWait(() -> {
                    GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setForeground(
                            GameFrame.getInstance().getTapete().getCommunityCards().getBet_label().getForeground()
                    );
                    GameFrame.getInstance().getBarra_tiempo().setIndeterminate(false);
                });
            }

            if (!success) {
                resisten.clear();
                return resisten;
            }

            actualizarContadoresTapete();
            LOGGER.log(Level.INFO, "Uncovering community cards");
            destaparCartaComunitaria(street, resisten);
        }

        sqlUpdateHandPlayers(resisten);

        if (puedenApostar(resisten) > 0 && !this.cartas_resistencia) {

            if (street > PREFLOP) {
                this.apuesta_actual = 0f;
                this.ultimo_raise = 0f;
                this.conta_raise = 0;
                this.conta_bet = 0;
                for (Player jugador : resisten) {
                    jugador.setBet(0f);
                }
            }

            int conta_pos = 0;
            if (street == PREFLOP) {
                limpers = 0;
                while (!GameFrame.getInstance().getJugadores().get(conta_pos).getNickname().equals(this.utg_nick)) {
                    conta_pos++;
                }
                if (this.straddle_posted) {
                    // El straddler (UTG) actua el ULTIMO preflop (opcion): la accion
                    // arranca en el asiento SIGUIENTE. El do-while salta asientos
                    // inactivos, y end_pos = este conta_pos hace que el straddler sea
                    // el ultimo en hablar antes de cerrar la ronda.
                    conta_pos++;
                    if (conta_pos >= GameFrame.getInstance().getJugadores().size()) {
                        conta_pos %= GameFrame.getInstance().getJugadores().size();
                    }
                }
            } else {
                if (nick2player.containsKey(this.small_blind_nick) && nick2player.get(this.small_blind_nick).isActivo()) {
                    while (!GameFrame.getInstance().getJugadores().get(conta_pos).getNickname().equals(this.small_blind_nick)) {
                        conta_pos++;
                    }
                } else {
                    while (!GameFrame.getInstance().getJugadores().get(conta_pos).getNickname().equals(this.big_blind_nick)) {
                        conta_pos++;
                    }
                }
            }

            int end_pos = conta_pos;
            int decision = -1;

            resetBetPlayerDecisions(GameFrame.getInstance().getJugadores(), null, false);
            actualizarContadoresTapete();

            do {
                GameFrame.getInstance().checkPause();
                turno = System.currentTimeMillis();
                Object[] accion_recuperada = null;
                Object[] action = null;

                Player current_player = GameFrame.getInstance().getJugadores().get(conta_pos);

                if (!resisten.contains(current_player)
                        || current_player.getDecision() == Player.ALLIN
                        || current_player.getDecision() == Player.FOLD) {
                    conta_pos++;
                    if (conta_pos >= GameFrame.getInstance().getJugadores().size()) {
                        conta_pos %= GameFrame.getInstance().getJugadores().size();
                    }
                    continue;
                }

                boolean isCryptoReplay = this.conta_accion < this.tot_acciones_recuperadas;
                boolean eraSincronizacion = this.isSincronizando_mano();

                // Dead branch: the two reads above are consecutive and never disagree
                // in single-threaded execution, so (!era && now) cannot fire. Kept here
                // for archaeology only; the real dragon-close after the replay ends
                // happens inside siguienteAccionLocalRecuperada (path #3) and as a
                // safety net after rondaApuestas(PREFLOP) (path #4).
                if (!eraSincronizacion && this.isSincronizando_mano()) {
                    this.setSincronizando_mano(false);
                    Helpers.GUIRun(() -> {
                        if (recover_dialog != null) {
                            recover_dialog.setVisible(false);
                            recover_dialog.dispose();
                            recover_dialog = null;
                        }
                        GameFrame.getInstance().getFull_screen_menu().setEnabled(true);
                        Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(true);
                        GameFrame.getInstance().refresh();
                    });
                    GameFrame.getInstance().getRegistro().print(Translator.translate("game.mano_recuperada"));
                    if (GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {
                        Audio.playWavResource("misc/startplay.wav");
                    }
                    if (recovering_music_active) {
                        Audio.stopLoopMp3("misc/recovering.mp3");
                        Audio.playLoopMp3Resource("misc/background_music.mp3");
                        recovering_music_active = false;
                    }
                }

                double old_player_bet = current_player.getBet();
                LOGGER.log(Level.INFO, "Read DECISION from {0}", current_player.getNickname());

                if (GameFrame.AUTO_ACTION_BUTTONS && current_player != GameFrame.getInstance().getLocalPlayer()
                        && GameFrame.getInstance().getLocalPlayer().getDecision() != Player.FOLD
                        && GameFrame.getInstance().getLocalPlayer().getDecision() != Player.ALLIN) {
                    GameFrame.getInstance().getLocalPlayer().activarPreBotones();
                }

                if (current_player == GameFrame.getInstance().getLocalPlayer()) {
                    current_player.esTuTurno();
                    if (eraSincronizacion && (accion_recuperada = siguienteAccionLocalRecuperada(current_player.getNickname())) != null) {
                        LocalPlayer localplayer = (LocalPlayer) current_player;
                        localplayer.setClick_recuperacion(true);
                        switch ((int) accion_recuperada[0]) {
                            case Player.FOLD:
                                Helpers.GUIRun(() -> {
                                    localplayer.getPlayer_fold_button().doClick();
                                    localplayer.setClick_recuperacion(false);
                                });
                                break;
                            case Player.CHECK:
                                Helpers.GUIRun(() -> {
                                    localplayer.getPlayer_check_button().doClick();
                                    localplayer.setClick_recuperacion(false);
                                });
                                break;
                            case Player.ALLIN:
                                Helpers.GUIRun(() -> {
                                    localplayer.getPlayer_allin_button().doClick();
                                    localplayer.setClick_recuperacion(false);
                                });
                                break;
                            case Player.BET:
                                localplayer.setApuesta_recuperada((Double) accion_recuperada[1]);
                                Helpers.GUIRun(() -> {
                                    localplayer.getPlayer_bet_button().doClick();
                                    localplayer.setClick_recuperacion(false);
                                });
                                break;
                        }
                    }

                    synchronized (getLock_apuestas()) {
                        while (current_player.isTurno()) {
                            try {
                                getLock_apuestas().wait(WAIT_QUEUES);
                            } catch (InterruptedException ex) {
                            }
                        }
                    }

                    decision = current_player.getDecision();
                    action = new Object[]{decision, current_player.getBet(), null};

                } else {
                    // Misma condición de siempre del branch del bot, izada para poder
                    // consultarla ANTES de esTuTurno sin cambiar su semántica.
                    final boolean bot_del_host = GameFrame.getInstance().isPartida_local()
                            && GameFrame.getInstance().getParticipantes().get(current_player.getNickname()) != null
                            && GameFrame.getInstance().getParticipantes().get(current_player.getNickname()).isCpu();

                    // Cinemática de all-in en curso (la del jugador local o la de un
                    // humano remoto): a un BOT no se le activa el turno (esTuTurno:
                    // borde naranja, barra, decisión) hasta que la animación termine
                    // en esta máquina. La acción del all-in ya viajó/se pintó en el
                    // momento del botón (eso no se toca); en red el freno de los
                    // humanos lo pone su propio modal, pero el bot corre en este
                    // hilo y se adelantaba: recibía el turno y actuaba detrás de la
                    // animación.
                    if (bot_del_host) {
                        esperarFinCinematicaAllin();
                    }

                    current_player.esTuTurno();
                    // Identity: no longer skip
                    // readActionFromRemotePlayer when current_player.isExit() at the
                    // start of the iteration. The previous early-out left action=null,
                    // the 3-slot fallback synthesised a record + signature locally on
                    // every peer, and the chain forked because each peer signed with
                    // its own privkey. The host's §4.5 autofold ACTION now flows
                    // through readActionFromRemotePlayer's existing isExit handler
                    // (host) and the queue drain (clients receive the host's wire).
                    if (bot_del_host) {
                        if (!eraSincronizacion || (accion_recuperada = siguienteAccionLocalRecuperada(current_player.getNickname())) == null) {
                            long start = System.currentTimeMillis();
                            double call_required = getApuesta_actual() - current_player.getBet();
                            int decision_loki = ((RemotePlayer) current_player).getBot().calculateBotDecision(resisten.size() - 1);
                            action = new Object[]{decision_loki, 0d, null};

                            switch (decision_loki) {
                                case Player.FOLD:
                                    if (Helpers.doubleSecureCompare(0f, this.getApuesta_actual()) == 0 || Helpers.doubleSecureCompare(current_player.getBet(), this.getApuesta_actual()) == 0) {
                                        action = new Object[]{Player.CHECK, 0d, null};
                                    }
                                    break;
                                case Player.CHECK:
                                    if (Helpers.doubleSecureCompare(current_player.getStack(), call_required) <= 0) {
                                        action = new Object[]{Player.ALLIN, "", null};
                                    }
                                    break;
                                case Player.BET:
                                    if (Helpers.doubleSecureCompare(current_player.getStack(), call_required) <= 0) {
                                        action = new Object[]{Player.ALLIN, "", null};
                                    } else {
                                        double b = ((RemotePlayer) current_player).getBot().getBetSize();
                                        if (Helpers.doubleSecureCompare(current_player.getStack() * 0.75f, b - current_player.getBet()) <= 0) {
                                            action = new Object[]{Player.ALLIN, "", null};
                                        } else if (puedenApostar(GameFrame.getInstance().getJugadores()) <= 1) {
                                            action = new Object[]{Player.CHECK, 0d, null};
                                        } else {
                                            action = new Object[]{Player.BET, b, null};
                                        }
                                    }
                                    break;
                            }

                            if (Init.DEV_MODE && ALLIN_BOT_TEST) {
                                action = new Object[]{Player.ALLIN, "", null};
                            }

                            long bot_elapsed_time = System.currentTimeMillis() - start;
                            if (Bot.BOT_THINK_TIME - bot_elapsed_time > 0L) {
                                Helpers.pausar(Bot.BOT_THINK_TIME - bot_elapsed_time);
                            }
                        } else {
                            action = accion_recuperada;
                        }
                    } else {
                        action = this.readActionFromRemotePlayer(current_player);
                    }
                }

                if (action == null || action.length < 2) {
                    action = new Object[]{Player.FOLD, 0d, null};
                } else if (action.length < 3) {
                    action = new Object[]{action[0], action[1], null};
                }

                decision = (int) action[0];

                if (decision == Player.ALLIN) {
                    if ((action[1] instanceof String) && !"".equals((String) action[1])) {
                        this.current_remote_cinematic_b64 = (String) action[1];
                    }
                    action[1] = 0d;
                } else {
                    this.current_remote_cinematic_b64 = null;
                }

                // Identity: resolve record + sig for this action.
                // Three sources cover the cases:
                //   1) RemotePlayer human, action came from the wire via
                //      readActionFromRemotePlayer → action[3]/[4] already populated.
                //   2) RemotePlayer that left (action[5] == FALSE) → no record absorbed,
                //      no wire broadcast. All peers locally synth FOLD and skip the
                //      slot; the chain converges by mutual omission.
                //   3) RemotePlayer that is a bot, or LocalPlayer's own turn →
                //      action[] from the UI/bot path is 3-slot, so fall through to
                //      the local build below.
                byte[] localRecord = null;
                byte[] localSig = null;
                boolean isVoluntary = true;
                if (action.length >= 6) {
                    if (action[3] instanceof byte[]) {
                        localRecord = (byte[]) action[3];
                    }
                    if (action[4] instanceof byte[]) {
                        localSig = (byte[]) action[4];
                    }
                    if (action[5] instanceof Boolean) {
                        isVoluntary = (Boolean) action[5];
                    }
                }
                // Exit synth: action[5]==FALSE marks a local FOLD synthesised because
                // the peer left. No fallback record build (host doesn't speak for
                // departed peers anymore), no wire broadcast, no chain absorb.
                final boolean exitSynth = !isVoluntary;
                if (!exitSynth && (localRecord == null || localSig == null)) {
                    // Only the §10-correct signer should build:
                    //   - LocalPlayer's own turn → we sign with our own privkey.
                    //   - Host (partida_local) → bot actions (§10: bot ⇒ host key).
                    // On a client receiving a remote player's wire that arrived empty
                    // (late / lost), we are NOT the §10 signer. Leaving null makes
                    // absorb a no-op on the client.
                    boolean canBuild = (current_player == GameFrame.getInstance().getLocalPlayer())
                            || GameFrame.getInstance().isPartida_local();
                    Object[] recsig = canBuild
                            ? buildLocalActionRecordAndSig(
                                    current_player.getNickname(), decision, action[1], current_player, isVoluntary)
                            : null;
                    if (recsig != null) {
                        localRecord = (byte[]) recsig[0];
                        localSig = (byte[]) recsig[1];
                    }
                }

                // Cinematic_or_* field is now always present in the wire (fixed slot).
                String cinematicField = "*";
                if (decision == Player.ALLIN) {
                    if (current_player == GameFrame.getInstance().getLocalPlayer()) {
                        // Acción PROPIA (host o cliente): adjunta la cinemática que
                        // eligió localCinematicAllin. El host quedaba fuera de esta
                        // rama (la condición exigía !isPartida_local()) y caía al
                        // else-if con current_remote_cinematic_b64 — null para una
                        // acción propia — así que sus all-in difundían "*" y los
                        // clientes no reproducían GIF ni limbo para ellos (asimetría
                        // heredada del código pre-identity). El campo no forma parte
                        // del record firmado: cero impacto en firmas/chain.
                        if (this.current_local_cinematic_b64 != null) {
                            cinematicField = this.current_local_cinematic_b64;
                        }
                    } else if (this.current_remote_cinematic_b64 != null) {
                        cinematicField = this.current_remote_cinematic_b64;
                    }
                }

                // Action wire:
                //   ACTION#nickB64#decision#bet#cinematic_or_*#record_or_*#sig_or_*
                String comando = "ACTION#"
                        + java.util.Base64.getEncoder().encodeToString(current_player.getNickname().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        + "#" + decision
                        + "#" + (decision == Player.BET ? String.valueOf((double) action[1]) : "0")
                        + "#" + cinematicField
                        + "#" + (localRecord != null ? java.util.Base64.getEncoder().encodeToString(localRecord) : "*")
                        + "#" + (localSig != null ? java.util.Base64.getEncoder().encodeToString(localSig) : "*");

                if (current_player == GameFrame.getInstance().getLocalPlayer()) {
                    if (GameFrame.getInstance().isPartida_local()) {
                        broadcastGAMECommandFromServer(comando, current_player.getNickname());
                    } else {
                        this.sendGAMECommandToServer(comando);
                    }
                } else {
                    ((RemotePlayer) current_player).setDecisionFromRemotePlayer(decision, (double) action[1]);
                    // No wire broadcast for exit-synth — the EXIT command already went
                    // out immediately when the peer left, and no peer has a record to
                    // absorb for this slot. Every receiver hits its own readActionFromRemotePlayer's
                    // isExit branch and synthesises the same local FOLD without absorb.
                    if (GameFrame.getInstance().isPartida_local() && !exitSynth) {
                        broadcastGAMECommandFromServer(comando, current_player.getNickname());
                    }
                }

                synchronized (getLock_apuestas()) {
                    while (current_player.isTurno()) {
                        try {
                            getLock_apuestas().wait(WAIT_QUEUES);
                        } catch (InterruptedException ex) {
                        }
                    }
                }

                // Identity: absorb the (record || sig) bytes into H_t.
                // Same call on host and every client, so the chain stays byte-identical
                // across the table. Failed-verify sigs are absorbed too so divergence
                // remains detectable at hand close (§4.5). Exit-synth skips: localRecord
                // is null anyway and absorbActionIntoChain is a no-op for null, but the
                // explicit guard documents the rule.
                if (!exitSynth) {
                    absorbActionIntoChain(current_player.getNickname(), localRecord, localSig);
                }

                Bot.OpponentTracker stats = Bot.TRACKER_MEMORY.computeIfAbsent(current_player.getNickname(), k -> new Bot.OpponentTracker());

                if (this.street == Crupier.PREFLOP) {
                    boolean isBBCheck = current_player.getNickname().equals(this.big_blind_nick)
                            && decision == Player.CHECK
                            && Helpers.doubleSecureCompare(this.apuesta_actual, this.getCiega_grande()) == 0;
                    // Con straddle, el "check gratis" lo tiene el straddler (UTG) sobre
                    // su propio straddle (apuesta_actual == 2x CG), no la ciega grande:
                    // ese check forzado tampoco cuenta como VPIP.
                    boolean isStraddleCheck = this.straddle_posted
                            && current_player.getNickname().equals(this.utg_nick)
                            && decision == Player.CHECK
                            && Helpers.doubleSecureCompare(this.apuesta_actual, Helpers.doubleClean(2 * this.getCiega_grande())) == 0;
                    if (!isBBCheck && !isStraddleCheck && (decision == Player.CHECK || decision == Player.BET || decision == Player.ALLIN)) {
                        stats.recordVPIP(this.conta_mano);
                    }
                    if (decision == Player.BET || decision == Player.ALLIN) {
                        stats.recordPFR(this.conta_mano);
                    }
                } else {
                    if (decision == Player.BET || decision == Player.ALLIN) {
                        stats.recordPostFlopBetOrRaise();
                    } else if (decision == Player.CHECK && this.apuesta_actual > old_player_bet) {
                        stats.recordPostFlopCall();
                    }
                }

                GameFrame.getInstance().getRegistro().print(current_player.getLastActionString());

                if (decision != Player.FOLD) {
                    this.apuestas += current_player.getBet() - old_player_bet;
                    this.bote_total += current_player.getBet() - old_player_bet;

                    if (decision == Player.BET || (decision == Player.ALLIN && Helpers.doubleSecureCompare(this.apuesta_actual, current_player.getBet()) < 0)) {
                        boolean partial_raise = false;
                        double min_raise = BetRules.minRaiseIncrement(getUltimo_raise(), getCiega_grande());
                        double current_raise = current_player.getBet() - this.apuesta_actual + this.partial_raise_cum;

                        if (BetRules.isFullRaise(current_raise, min_raise)) {
                            this.ultimo_raise = current_raise;
                            this.partial_raise_cum = 0f;
                            this.conta_raise++;
                        } else if (decision == Player.ALLIN) {
                            partial_raise = true;
                            this.partial_raise_cum += current_player.getBet() - this.apuesta_actual;
                        }

                        this.conta_bet++;
                        this.apuesta_actual = current_player.getBet();
                        resetBetPlayerDecisions(GameFrame.getInstance().getJugadores(), partial_raise ? (this.last_aggressor != null ? this.last_aggressor.getNickname() : null) : current_player.getNickname(), partial_raise);

                        if (street == PREFLOP) {
                            limpers = 0;
                        }
                        end_pos = conta_pos;

                    } else if (street == PREFLOP && Helpers.doubleSecureCompare(this.apuesta_actual, this.getCiega_grande()) == 0 && !current_player.getNickname().equals(this.getBb_nick()) && !current_player.getNickname().equals(this.getSb_nick())) {
                        limpers++;
                    }
                } else {
                    resisten.remove(current_player);
                }

                this.conta_accion++;

                if (!isCryptoReplay) {
                    this.sqlNewAction(current_player, localRecord, localSig);
                } else if (GameFrame.getInstance().isPartida_local()) {
                    if (this.sqlCheckGenuineRecoverAction(current_player)) {
                        LOGGER.log(Level.INFO, "Recover action OK");
                    }
                }

                // El bote (bote_total) se acaba de commitear arriba. Si la ficha del jugador
                // YA aterrizó (cinemáticas: vuela a mitad del GIF, mucho antes de cerrarse la
                // acción) o no había ficha, rueda AHORA su stack/bet aplazado, a la vez que el
                // bote (actualizarContadoresTapete justo debajo) -> los tres juntos. Si su
                // ficha SIGUE en vuelo (caso normal sin cinemática), NO: su rodaje ya va atado
                // al aterrizaje (launchChipToPot.onLand), donde el bote también sube. No-op si
                // no había aplazamiento. Solo afecta al rodaje del contador, no al bote.
                if (!isPotLabelValueDeferred()) {
                    current_player.rollCountersToModel();
                }
                actualizarContadoresTapete();
                conta_pos++;
                if (conta_pos >= GameFrame.getInstance().getJugadores().size()) {
                    conta_pos %= GameFrame.getInstance().getJugadores().size();
                }

            } while (conta_pos != end_pos && resisten.size() > 1 && !isFin_de_la_transmision());

            this.apuestas = 0f;
            actualizarContadoresTapete();
            // Entre calles de APUESTAS la bet_label queda visible mostrando
            // "CALLE: ---" para que su icono de pot permanezca fijo (sin el
            // parpadeo de ocultarse y reaparecer en cada calle). En el RUN-OUT
            // all-in (destapar_resistencia) actualizarContadoresTapete ya la
            // oculta y centra el bote, así que solo se ve la pot_label.

            if (resisten.size() > 1 && puedenApostar(resisten) <= 1) {
                boolean firstResistencia = !this.destapar_resistencia;
                if (firstResistencia) {
                    // Calle del all-in run-out: las comunitarias posteriores son
                    // las "corridas" que SIDE-B rebobina y re-reparte. Host y
                    // cliente lo registran (ambos corren rondaApuestas en lockstep).
                    this.rit_allin_street = this.street;
                }
                // PRIMERO bloqueamos el toggle (síncrono, justo antes de empezar a
                // destapar) y LUEGO leemos el flag: así GameFrame.RUN_IT_TWICE ya no
                // puede cambiar cuando decidimos el voto (race-free). El menú se
                // reactiva al empezar la siguiente mano (ver NUEVA_MANO en run()).
                if (firstResistencia && GameFrame.getInstance().isPartida_local()) {
                    // Congela RUN_IT_TWICE durante el run-out: a continuación se decide
                    // el voto leyendo el flag (sin lock), así que no debe cambiar hasta
                    // NUEVA_MANO. Antes se greyaba el menú; ahora lo gestiona el flag.
                    GameFrame.RUN_IT_TWICE_LOCKED = true;
                }
                this.destapar_resistencia = true;
                // Arranca el run-out: oculta ya la bet_label y centra el bote
                // (actualizarContadoresTapete lo hace al ver destapar_resistencia),
                // antes del giro de cartas de los rivales, no solo al destapar la
                // siguiente calle.
                actualizarContadoresTapete();
                if (resisten.contains(GameFrame.getInstance().getLocalPlayer())) {
                    GameFrame.getInstance().getLocalPlayer().desactivarControles();
                }
                procesarCartasResistencia(resisten, true);
                checkJugadasParciales(resisten);
                // Run-it-twice: votación host-driven al cerrarse la acción con 2+
                // implicados. Solo si quedan calles por correr (rit_allin_street <
                // RIVER): un all-in EN el river no tiene nada que correr dos veces,
                // así que ni se ofrece el voto. En RECOVERY (rit_vote_done restaurado
                // del fósil) NO se re-vota: se rebroadcasta el resultado para
                // sincronizar a los clientes y se corre directo. El gate de recovery
                // es independiente del toggle (el voto pudo hacerse aunque ahora off).
                if (firstResistencia && GameFrame.getInstance().isPartida_local()
                        && this.rit_allin_street < Crupier.RIVER) {
                    if (this.rit_vote_done) {
                        broadcastRitClose(this.rit_agreed ? 1 : 0);
                        printRitVoteResult(this.rit_agreed);
                    } else if (GameFrame.RUN_IT_TWICE) {
                        boolean agreed = runRitVote(resisten);
                        this.rit_agreed = agreed;
                        this.rit_vote_done = true;
                        // Persiste el voto al fósil: una mano recuperada tras el voto
                        // corre los dos boards en vez de uno.
                        this.guardarFosilSRA();
                        LOGGER.log(Level.INFO, "RUN-IT-TWICE vote result: {0}", agreed);
                        printRitVoteResult(agreed);
                    }
                }
            }

            if (this.street == Crupier.PREFLOP) {
                // Con straddle la pistola la tiene el primero en hablar (no el
                // straddler), asi que se la quitamos a ese mismo jugador.
                String utg_to_disable = (this.straddle_posted && this.straddle_utg_nick != null) ? this.straddle_utg_nick : this.utg_nick;
                Player utg_to_disable_player = nick2player.get(utg_to_disable);
                if (utg_to_disable_player != null) {
                    utg_to_disable_player.disableUTG();
                }
            }

        }

        if (isFin_de_la_transmision()) {
            // Game cancelled mid-round: park the crupier thread here so the
            // recursive rondaApuestas(street + 1, ...) below is never reached
            // once fin_de_la_transmision is set. Loop with a timeout to be
            // resilient to spurious wakeups and to unrelated notifyAll() calls
            // on lock_apuestas (incoming bets, queue drains). Exit only on
            // thread interruption.
            synchronized (getLock_apuestas()) {
                while (isFin_de_la_transmision()) {
                    try {
                        getLock_apuestas().wait(1000);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        return (resisten.size() > 1 && street < RIVER && getJugadoresActivos() > 1)
                ? rondaApuestas(street + 1, resisten)
                : resisten;
    }

    public void guardarFosilSRA() {
        if (this.local_mega_packet == null || this.active_crypto_ring == null) {
            return;
        }
        try {
            StringBuilder fosil = new StringBuilder();
            fosil.append("ORDER@");
            for (String nickRing : this.active_crypto_ring) {
                fosil.append(java.util.Base64.getEncoder().encodeToString(nickRing.getBytes("UTF-8"))).append(",");
            }
            fosil.append("#FULLMEGAPACKET@").append(java.util.Base64.getEncoder().encodeToString(this.local_mega_packet));

            byte[] unlockToSave = this.local_sra_unlock;
            if (unlockToSave == null) {
                Participant p = GameFrame.getInstance().getParticipantes().get(GameFrame.getInstance().getNick_local());
                if (p != null) {
                    unlockToSave = p.getSra_unlock();
                }
            }

            if (unlockToSave != null) {
                fosil.append("#SRAKEYS@").append(java.util.Base64.getEncoder().encodeToString(unlockToSave));
            }

            // Dual-lock: además del unlock pocket guardamos el unlock
            // community. Sin esto, una recuperación post-rotación dejaría las
            // community pieces sin descifrar y la mano se atascaría en FLOP.
            byte[] unlockCommunityToSave = this.local_sra_unlock_community;
            if (unlockCommunityToSave == null) {
                Participant p = GameFrame.getInstance().getParticipantes().get(GameFrame.getInstance().getNick_local());
                if (p != null) {
                    unlockCommunityToSave = p.getSra_unlock_community();
                }
            }
            if (unlockCommunityToSave != null) {
                fosil.append("#SRAKEYS_COMMUNITY@").append(java.util.Base64.getEncoder().encodeToString(unlockCommunityToSave));
            }

            // Persistir los commitments K del ring para que el recovery
            // reconstruya el MISMO H_0. Sin esto, initHandStateChain en
            // recovery queda sin los K (peer_k_pocket vacío) y diverge del H_0 original,
            // rompiendo la verificación de la cadena de acciones recuperadas.
            String commitmentsFossil = serializeCommitments();
            if (!commitmentsFossil.isEmpty()) {
                fosil.append("#COMMITMENTS@").append(commitmentsFossil);
            }

            StringBuilder botKeys = new StringBuilder();
            StringBuilder botKeysCommunity = new StringBuilder();
            StringBuilder botVisuals = new StringBuilder();
            for (String nick : this.active_crypto_ring) {
                Participant p = GameFrame.getInstance().getParticipantes().get(nick);
                Player botPlayer = nick2player.get(nick);

                if (p != null && p.isCpu()) {
                    if (p.getReceived_token() != null) {
                        botKeys.append(java.util.Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")))
                                .append(":")
                                .append(java.util.Base64.getEncoder().encodeToString(p.getReceived_token()))
                                .append(",");
                    }
                    if (p.getSra_unlock_community() != null) {
                        botKeysCommunity.append(java.util.Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")))
                                .append(":")
                                .append(java.util.Base64.getEncoder().encodeToString(p.getSra_unlock_community()))
                                .append(",");
                    }
                    if (botPlayer != null && botPlayer.getHoleCard1().getCartaComoEntero() >= 0) {
                        botVisuals.append(java.util.Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")))
                                .append(":")
                                .append(botPlayer.getHoleCard1().getCartaComoEntero()).append(",")
                                .append(botPlayer.getHoleCard2().getCartaComoEntero()).append("@");
                    }
                }
            }
            if (botKeys.length() > 0) {
                fosil.append("#BOTKEYS@").append(botKeys.toString());
            }
            if (botKeysCommunity.length() > 0) {
                fosil.append("#BOTKEYS_COMMUNITY@").append(botKeysCommunity.toString());
            }
            if (botVisuals.length() > 0) {
                fosil.append("#BOTVISUAL@").append(botVisuals.toString());
            }

            // POCKETS@ persiste la cache single_locked_pocket_cards. Sin esto,
            // un RESET_GAME+recover deja al host sin el residuo cifrado por target,
            // verifyAndStoreShowdownKey rechaza cada RESP_SHOWDOWN_KEY de humanos
            // remotos y calcularJugadas los muckea por "disconnection".
            // Cada entry está single-locked (solo el lock del target queda); el
            // host no posee los scalars de humanos remotos, ergo no puede
            // descifrarlos aunque lea el fosil — no introduce leakeo nuevo.
            StringBuilder pockets = new StringBuilder();
            for (java.util.Map.Entry<String, byte[]> e : this.single_locked_pocket_cards.entrySet()) {
                byte[] pc = e.getValue();
                if (pc != null && pc.length == 64) {
                    try {
                        pockets.append(java.util.Base64.getEncoder().encodeToString(e.getKey().getBytes("UTF-8")))
                                .append(":")
                                .append(java.util.Base64.getEncoder().encodeToString(pc))
                                .append(",");
                    } catch (Exception ignored) {
                    }
                }
            }
            if (pockets.length() > 0) {
                fosil.append("#POCKETS@").append(pockets.toString());
            }

            // VISUAL@ guarda los índices 0..51 que resuelven a una carta
            // del genesis deck. Si la mano abortó por MISDEAL antes de
            // descifrar correctamente las hole cards, resolveCardIndex
            // devolvió -1 y guardar ese valor envenena el fósil: en
            // recovery se lee como byte=-1 → (byte&0xFF)+1 = 256 →
            // PALOS[19] OOB → error fatal del Crupier. Sólo persistimos si
            // ambos índices son válidos.
            if (this.local_original_cards != null
                    && this.local_original_cards[0] >= 0 && this.local_original_cards[0] < 52
                    && this.local_original_cards[1] >= 0 && this.local_original_cards[1] < 52) {
                fosil.append("#VISUAL@").append(this.local_original_cards[0]).append(",").append(this.local_original_cards[1]);
            }

            // Run-it-twice: estado del voto (vote_done, agreed, allin_street) para
            // que una mano recuperada tras el voto corra los DOS boards en vez de uno.
            fosil.append("#RIT@").append(this.rit_vote_done).append(",").append(this.rit_agreed).append(",").append(this.rit_allin_street);

            // Straddle voluntario: si el UTG puso el straddle en esta mano, para que la
            // mano recuperada lo reponga (el host rebroadcasta la decisión, no pregunta).
            fosil.append("#STRADDLE@").append(this.straddle_posted);

            Helpers.saveHandFossil(this.sqlite_id_game, fosil.toString());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error saving SRA fossil to disk", e);
        }
    }

    private void procesarCartasComunesRestantes() {
        // Sin rabbit que destapar (caso tipico: showdown llego al river con todo
        // ya revelado) no tocamos la barra — antes meter barraIndeterminada +
        // resetBarra(0) en sucesion microscopica provocaba un parpadeo visible
        // en el final del showdown.
        boolean willRabbit = (street <= PREFLOP && GameFrame.getInstance().getFlop1().isTapada())
                || (street <= FLOP && GameFrame.getInstance().getTurn().isTapada())
                || (street <= TURN && GameFrame.getInstance().getRiver().isTapada());
        if (!willRabbit) {
            return;
        }
        Helpers.barraIndeterminada(GameFrame.getInstance().getBarra_tiempo());
        try {
            if (street <= PREFLOP && GameFrame.getInstance().getFlop1().isTapada()) {
                enviarRabbitComunitarias(FLOP);
            }
            if (street <= FLOP && GameFrame.getInstance().getTurn().isTapada()) {
                enviarRabbitComunitarias(TURN);
            }
            if (street <= TURN && GameFrame.getInstance().getRiver().isTapada()) {
                enviarRabbitComunitarias(RIVER);
            }
        } catch (Exception e) {
        }
        Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), 0);
    }

    private boolean enviarRabbitComunitarias(int targetStreet) {
        // Dual-lock: rabbit hunting usa cascadeAndDealCommunityPieces que aplica
        // el unlock community local. Sin community-half no podemos revelar las
        // cartas no jugadas; salida silenciosa (el rabbit es no crítico, no
        // disparamos abort de mano).
        if (this.local_sra_unlock == null || this.local_sra_unlock_community == null || this.active_crypto_ring == null) {
            return false;
        }

        int numPlayers = this.active_crypto_ring.length;
        int numCards = (targetStreet == Crupier.FLOP) ? 3 : 1;
        int offset = numPlayers * 2;
        if (targetStreet == Crupier.FLOP) {
            offset += 1;
        } else if (targetStreet == Crupier.TURN) {
            offset += 1 + 3 + 1;
        } else if (targetStreet == Crupier.RIVER) {
            offset += 1 + 3 + 1 + 1 + 1;
        }

        int rabbitPhase = phaseForStreet(targetStreet, true);
        String pieceCommand = (targetStreet == Crupier.FLOP) ? "RABBIT_FLOP_PIECE"
                : (targetStreet == Crupier.TURN) ? "RABBIT_TURN_PIECE" : "RABBIT_RIVER_PIECE";

        int[] hostIndices = cascadeAndDealCommunityPieces(offset, numCards, rabbitPhase, pieceCommand, false);
        if (hostIndices == null) {
            return false;
        }

        if (targetStreet == Crupier.FLOP) {
            GameFrame.getInstance().getFlop1().actualizarConValorNumerico(hostIndices[0] + 1);
            GameFrame.getInstance().getFlop2().actualizarConValorNumerico(hostIndices[1] + 1);
            GameFrame.getInstance().getFlop3().actualizarConValorNumerico(hostIndices[2] + 1);
            GameFrame.getInstance().getFlop1().taparRabbit();
            GameFrame.getInstance().getFlop2().taparRabbit();
            GameFrame.getInstance().getFlop3().taparRabbit();
        } else if (targetStreet == Crupier.TURN) {
            GameFrame.getInstance().getTurn().actualizarConValorNumerico(hostIndices[0] + 1);
            GameFrame.getInstance().getTurn().taparRabbit();
        } else if (targetStreet == Crupier.RIVER) {
            GameFrame.getInstance().getRiver().actualizarConValorNumerico(hostIndices[0] + 1);
            GameFrame.getInstance().getRiver().taparRabbit();
        }
        return true;
    }

    // PHASE A.1 (showdown zero-trust): el host orquesta el reveal de cartas al
    // showdown vía SRA verificable. Cada peer envía su k_pocket_unlock; el host
    // verifica aplicándolo a single_locked_pocket_cards[nick] y comprobando que
    // resolveCardIndex devuelve 0-51 para ambas cartas. Si el peer mintió
    // (resolveCardIndex == -1), triggerSecurityLockdown — la mano no settles
    // con datos falsos. Reemplaza el path plaintext REQ_VISUAL_CARDS/POTCARDS
    // que existía antes y aceptaba texto en bruto sin verificación criptográfica.
    private void solicitarYRecibirCartasVisuales(ArrayList<Player> resisten) {
        if (!GameFrame.getInstance().isPartida_local()) {
            return;
        }

        // El showdown YA NO emite SHOWCARDS (eso es solo botón MOSTRAR mid-hand).
        // El único broadcast del showdown es POTCARDS, atómico, llevando para
        // cada jugador: nick + plaintext c1/c2 + sraKey + sig Ed25519.
        //
        // Verificación per-recipient:
        //   - Espectadores/calentando: aplican plaintext directo (no tienen
        //     single_locked_pocket_cards para descifrar SRA).
        //   - Crypto-ring: verifica sig (lockdown si fail) + descifra SRA y
        //     compara con plaintext (peer FORFEIT si mismatch — intento de
        //     trampa). El host también es cazado si modifica el plaintext
        //     pero deja las sigs intactas: el descifrado no cuadra.

        String hostNick = GameFrame.getInstance().getNick_local();

        // Recopilar (sraKey, sig) per nick. Para self+bots los firma el host;
        // para remotos los recibimos via REQ/RESP_SHOWDOWN_KEY.
        HashMap<String, String> nick2key = new HashMap<>();
        HashMap<String, String> nick2sig = new HashMap<>();

        for (Player p : resisten) {
            if (p.isExit()) continue;
            String nick = p.getNickname();
            boolean isHost = nick.equals(hostNick);
            Participant part = GameFrame.getInstance().getParticipantes().get(nick);
            boolean isBot = part != null && part.isCpu();
            if (isHost || isBot) {
                String localKey = getShowdownPocketKey(nick);
                if (!"*".equals(localKey)) {
                    String sigB64 = signShowdownRevealForBroadcast(nick, localKey);
                    if (!"*".equals(sigB64)) {
                        nick2key.put(nick, localKey);
                        nick2sig.put(nick, sigB64);
                    } else {
                        LOGGER.log(Level.WARNING,
                                "solicitarYRecibirCartasVisuales: cannot sign POTCARDS entry for {0} — entry will be plaintext-only",
                                nick);
                    }
                }
            }
        }

        // Pedir su sraKey + sig a humanos remotos.
        ArrayList<String> pendientes = new ArrayList<>();
        for (Player p : resisten) {
            if (!p.getNickname().equals(hostNick) && !p.isExit()) {
                Participant part = GameFrame.getInstance().getParticipantes().get(p.getNickname());
                if (part != null && !part.isCpu()) {
                    pendientes.add(p.getNickname());
                }
            }
        }

        if (!pendientes.isEmpty()) {
            int id = Helpers.CSPRNG_GENERATOR.nextInt();
            byte[] iv = new byte[16];
            Helpers.CSPRNG_GENERATOR.nextBytes(iv);
            String reqCmd = "GAME#" + id + "#REQ_SHOWDOWN_KEY";
            for (String nick : pendientes) {
                Participant p = GameFrame.getInstance().getParticipantes().get(nick);
                if (p != null) {
                    p.writeCommandFromServer(Helpers.encryptCommand(reqCmd, p.getAes_key(), iv, p.getHmac_key()));
                }
            }

            long start_time = System.currentTimeMillis();

            do {
                synchronized (this.getReceived_commands()) {
                    ArrayList<String> rejected = new ArrayList<>();
                    while (!pendientes.isEmpty() && !this.getReceived_commands().isEmpty()) {
                        String comando = this.received_commands.poll();
                        String[] partes = comando.split("#");

                        if (partes.length >= 5 && partes[2].equals("RESP_SHOWDOWN_KEY")) {
                            try {
                                String nick = new String(Base64.getDecoder().decode(partes[3]), "UTF-8");
                                if (pendientes.contains(nick)) {
                                    String keyB64 = partes[4];
                                    String sigB64 = (partes.length >= 6) ? partes[5] : "*";
                                    if (verifyAndStoreShowdownKey(nick, keyB64, sigB64)) {
                                        nick2key.put(nick, keyB64);
                                        nick2sig.put(nick, sigB64);
                                        // Solo se quita de pendientes al VERIFICAR: una RESP_SHOWDOWN_KEY con
                                        // firma inválida NO consume el slot del peer (antes lo muckeaba). Sigue
                                        // esperando una RESP válida o el timeout. Con el nick-binding (F1) la RESP
                                        // solo puede venir del propio peer -> a lo sumo auto-daño.
                                        pendientes.remove(nick);
                                    }
                                } else {
                                    rejected.add(comando);
                                }
                            } catch (Exception e) {
                                LOGGER.log(Level.WARNING, "Error processing RESP_SHOWDOWN_KEY", e);
                            }
                        } else {
                            rejected.add(comando);
                        }
                    }
                    if (!rejected.isEmpty()) {
                        this.getReceived_commands().addAll(rejected);
                    }
                }

                if (Crupier.SECURITY_LOCKDOWN) {
                    return;
                }

                if (!pendientes.isEmpty()) {
                    GameFrame.getInstance().checkPause();
                    if (System.currentTimeMillis() - start_time > GameFrame.CLIENT_RECEPTION_TIMEOUT) {
                        LOGGER.log(Level.WARNING,
                                "REQ_SHOWDOWN_KEY timeout — {0} peers did not respond. Their cards stay face-down; the host resolves the pot from the action log.",
                                pendientes);
                        break;
                    }
                    synchronized (this.getReceived_commands()) {
                        try {
                            this.received_commands.wait(WAIT_QUEUES);
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            } while (!pendientes.isEmpty() && !isFin_de_la_transmision());
        }

        // Construir POTCARDS atómico. Formato per-entry (5 fields):
        //   #nickB64#c1#c2#sraKeyB64#sigB64
        // Si no hay (sraKey, sig) verificada para un nick, se omite — sus
        // cartas no aparecerán en POTCARDS y showdown las dejará tapadas.
        if (!Crupier.SECURITY_LOCKDOWN) {
            StringBuilder potcards = new StringBuilder("POTCARDS");
            boolean anyCard = false;
            for (Player jugador : resisten) {
                if (jugador.isExit()) {
                    continue;
                }
                String nick = jugador.getNickname();
                String keyB64 = nick2key.get(nick);
                String sigB64 = nick2sig.get(nick);
                if (keyB64 == null || sigB64 == null) {
                    continue;
                }
                try {
                    String c1 = jugador.getHoleCard1().toShortString();
                    String c2 = jugador.getHoleCard2().toShortString();
                    if (c1 == null || c1.isEmpty() || c2 == null || c2.isEmpty()) {
                        continue;
                    }
                    potcards.append("#")
                            .append(Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")))
                            .append("#").append(c1)
                            .append("#").append(c2)
                            .append("#").append(keyB64)
                            .append("#").append(sigB64);
                    anyCard = true;
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Error encoding POTCARDS entry for " + nick, ex);
                }
            }
            if (anyCard) {
                broadcastGAMECommandFromServer(potcards.toString(), null);
            }
        }
    }

    // Verifica firma Ed25519 + SRA y actualiza HoleCard local del host.
    // YA NO emite SHOWCARDS — el showdown propaga las claves dentro del
    // POTCARDS atómico (un solo broadcast con plaintext + sraKey + sig).
    //
    //   1. Sig Ed25519: la clave debe estar firmada por la privkey del nick
    //      (resolveShowdownSignerPubkey: humano → suya; bot → host).
    //   2. SRA matemática: aplicar la clave al single_locked_pocket_cards[nick]
    //      y verificar resolveCardIndex 0-51 en ambas cartas.
    //
    // Retorno true sólo si verificación completa OK (la entrada se incluirá
    // en POTCARDS). Cualquier fail → false (sus cartas se omitirán → tapada
    // en la UI; el pot se resuelve por acciones).
    private boolean verifyAndStoreShowdownKey(String nick, String keyB64, String sigB64) {
        if (keyB64 == null || keyB64.equals("*")) {
            return false;
        }
        if (sigB64 == null || sigB64.equals("*")) {
            LOGGER.log(Level.SEVERE,
                    "verifyAndStoreShowdownKey: RESP_SHOWDOWN_KEY from {0} arrived WITHOUT sig — refusing",
                    nick);
            return false;
        }
        try {
            byte[] key = Base64.getDecoder().decode(keyB64);
            if (key.length != 32) {
                LOGGER.log(Level.WARNING,
                        "verifyAndStoreShowdownKey: RESP_SHOWDOWN_KEY from {0} has wrong length {1} — their cards are not revealed",
                        new Object[]{nick, key.length});
                return false;
            }
            byte[] sig = Base64.getDecoder().decode(sigB64);
            if (sig.length != 64) {
                LOGGER.log(Level.SEVERE,
                        "verifyAndStoreShowdownKey: RESP_SHOWDOWN_KEY sig from {0} has wrong length {1} — refusing",
                        new Object[]{nick, sig.length});
                return false;
            }
            byte[] signerPubkey = resolveShowdownSignerPubkey(nick);
            if (signerPubkey == null || this.current_hand_id == null) {
                LOGGER.log(Level.SEVERE,
                        "verifyAndStoreShowdownKey: no signer pubkey or hand_id for {0} — refusing",
                        nick);
                return false;
            }
            if (!IdentityManager.verifyShowdownReveal(signerPubkey, this.current_hand_id, nick, key, sig)) {
                LOGGER.log(Level.SEVERE,
                        "verifyAndStoreShowdownKey: Ed25519 sig FAILED for {0} — host may be substituting key or peer fabricated sig.",
                        nick);
                return false;
            }
            byte[] pocketCards = this.single_locked_pocket_cards.get(nick);
            if (pocketCards == null || pocketCards.length != 64) {
                LOGGER.log(Level.WARNING,
                        "verifyAndStoreShowdownKey: no single_locked_pocket_cards for {0} — skipping",
                        nick);
                return false;
            }
            byte[] unlocked = RistrettoSRA.applyCommutativeLock(pocketCards, key);
            byte[] c1 = Arrays.copyOfRange(unlocked, 0, 32);
            byte[] c2 = Arrays.copyOfRange(unlocked, 32, 64);
            int id1 = RistrettoSRA.resolveCardIndex(c1);
            int id2 = RistrettoSRA.resolveCardIndex(c2);
            if (id1 < 0 || id2 < 0) {
                // Sig OK pero la clave firmada NO resuelve a una carta genesis: el peer firmo una
                // clave mala (bug/cliente corrupto) o intenta algo — no podemos distinguir. Esto es el
                // HOST detectando a UN peer -> FORFEIT (return false -> sus cartas no se revelan, el
                // showdown las muckea), NO terminamos la partida de todos. Visibilidad §8: registro EN ROJO
                // + popup nombrando al JUGADOR sospechoso (warnMaliciousPeer, el simetrico host-side; antes
                // se dejaba silencioso porque warnSuspiciousHost habria mis-frameado al host, no al peer).
                LOGGER.log(Level.SEVERE,
                        "ZERO-TRUST: RESP_SHOWDOWN_KEY from {0} — sig OK but SRA does not resolve (ids={1},{2}). Malicious peer or bug -> FORFEIT (their cards are not revealed).",
                        new Object[]{nick, id1, id2});
                warnMaliciousPeer(nick, "zero_trust.peer_sra_corrupt");
                return false;
            }

            // Persistir la key verificada en el Participant + actualizar UI local del host.
            Participant p = GameFrame.getInstance().getParticipantes().get(nick);
            if (p != null) {
                p.setSra_unlock(key);
            }
            Player jugador = nick2player.get(nick);
            if (jugador != null) {
                jugador.getHoleCard1().iniciarConValorNumerico(id1 + 1);
                jugador.getHoleCard2().iniciarConValorNumerico(id2 + 1);
            }
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error verifying showdown key for " + nick, e);
            return false;
        }
    }

    private void checkJugadasParciales(ArrayList<Player> resisten) {

        if (this.destapar_resistencia && this.street != Crupier.RIVER) {

            HashMap<Player, Hand> jugadas = calcularJugadas(resisten);
            HashMap<Player, Hand> ganadores = calcularGanadores(new HashMap<>(jugadas));

            // Blindaje contra nulls
            for (Player p : resisten) {
                if (jugadas.containsKey(p)) {
                    p.setJugadaParcial(jugadas.get(p), ganadores.containsKey(p), -1);
                }
            }

            HashMap<Player, Integer[]> multiverse = monteCarlo(resisten, MONTECARLO_ITERATIONS);

            for (Player p : resisten) {
                // Solo simulamos los que confesaron sus cartas
                if (!jugadas.containsKey(p)) {
                    continue;
                }

                org.alberta.poker.Card card1 = Bot.coronaCard2LokiCard(p.getHoleCard1());
                org.alberta.poker.Card card2 = Bot.coronaCard2LokiCard(p.getHoleCard2());

                if (card1 == null || card2 == null) {
                    continue;
                }

                double strength = Bot.HANDEVALUATOR.handRank(card1, card2, Bot.BOT_COMMUNITY_CARDS,
                        resisten.size() - 1);
                double ppot = Bot.HANDPOTENTIAL.ppot_raw(card1, card2, Bot.BOT_COMMUNITY_CARDS, false);
                double npot = Bot.HANDPOTENTIAL.getLastNPot();
                double effectiveStrength = strength + (1 - strength) * ppot - strength * npot;

                jugadas.get(p).setFuerza(effectiveStrength * 100);
            }

            ArrayList<Object[]> stats_ordenadas = new ArrayList<>();

            for (Player p : resisten) {
                if (multiverse.containsKey(p) && jugadas.containsKey(p)) {
                    stats_ordenadas.add(new Object[]{p, multiverse.get(p)});
                }
            }

            stats_ordenadas.sort((a, b) -> ((Integer[]) b[1])[1] - ((Integer[]) a[1])[1]);

            // Tabla MULTIVERSO del registro: cabecera + una fila por jugador,
            // alineadas en columnas monospace (NICK | GANA | PIERDE | EMPATA | LOKI |
            // MANO). Cada linea lleva el token "(MV)" para que el GameLogDialog la
            // pinte atenuada y con las cartas como fichas (mismo offset que la tabla
            // de cuentas). Las cartas van en la ULTIMA columna porque se renderizan
            // como fichas de ancho variable y desalinearian cualquier columna
            // posterior. Primera pasada: formatear porcentajes y medir anchos.
            String gana_lbl = Translator.translate("ui.gana");
            String pierde_lbl = Translator.translate("ui.pierde");
            String empata_lbl = Translator.translate("ui.empata");
            String loki_lbl = "LOKI";

            ArrayList<String[]> mv_filas = new ArrayList<>();

            int mv_nick_w = "NICK".length();
            int mv_pct_w = Math.max(Math.max(gana_lbl.length(), pierde_lbl.length()),
                    Math.max(empata_lbl.length(), loki_lbl.length()));

            for (Object[] s : stats_ordenadas) {
                Player p = (Player) s[0];
                Integer[] stats = (Integer[]) s[1];
                Hand manoParcial = jugadas.get(p);

                p.setJugadaParcial(manoParcial, ganadores.containsKey(p),
                        Helpers.floatClean(((float) (stats[1] + stats[3]) / stats[0]) * 100));

                String gana_s = multiversePct(((float) stats[1] / stats[0]) * 100);
                String pierde_s = multiversePct(((float) stats[2] / stats[0]) * 100);
                String empata_s = multiversePct(((float) stats[3] / stats[0]) * 100);
                String loki_s = multiversePct(manoParcial.getFuerza());

                mv_nick_w = Math.max(mv_nick_w, p.getNickname().length());
                mv_pct_w = Math.max(mv_pct_w, Math.max(Math.max(gana_s.length(), pierde_s.length()),
                        Math.max(empata_s.length(), loki_s.length())));

                mv_filas.add(new String[]{p.getNickname(), gana_s, pierde_s, empata_s, loki_s,
                    Card.collection2String(p.getHoleCards())});
            }

            if (!mv_filas.isEmpty()) {

                int[] mv_cols = {mv_nick_w, mv_pct_w, mv_pct_w, mv_pct_w, mv_pct_w};

                // Titulo (una vez, encima del marco) + tabla con bordes. Las cartas
                // van a la DERECHA del marco (son fichas de ancho variable, no
                // monoespaciadas). Entre fila y fila va un separador de rejilla, que
                // ademas evita que las fichas (altas) se toquen verticalmente.
                StringBuilder mv_tabla = new StringBuilder(
                        "(MV) " + Translator.translate("ui.multiverso") + " (" + MONTECARLO_ITERATIONS + ")");

                mv_tabla.append("\n(MV) ").append(gridBorderLine('┌', '┬', '┐', mv_cols))
                        .append("\n(MV) ").append(gridRowLine(
                                String.format("%-" + mv_nick_w + "s", "NICK"),
                                String.format("%" + mv_pct_w + "s", gana_lbl),
                                String.format("%" + mv_pct_w + "s", pierde_lbl),
                                String.format("%" + mv_pct_w + "s", empata_lbl),
                                String.format("%" + mv_pct_w + "s", loki_lbl)))
                        .append("\n(MV) ").append(gridBorderLine('├', '┼', '┤', mv_cols));

                String mv_inner_sep = "\n(MV) " + gridBorderLine('├', '┼', '┤', mv_cols);
                boolean mv_first = true;

                for (String[] f : mv_filas) {
                    if (!mv_first) {
                        mv_tabla.append(mv_inner_sep);
                    }
                    mv_first = false;
                    mv_tabla.append("\n(MV) ").append(gridRowLine(
                            String.format("%-" + mv_nick_w + "s", f[0]),
                            String.format("%" + mv_pct_w + "s", f[1]),
                            String.format("%" + mv_pct_w + "s", f[2]),
                            String.format("%" + mv_pct_w + "s", f[3]),
                            String.format("%" + mv_pct_w + "s", f[4])))
                            .append(" ").append(f[5]);
                }

                mv_tabla.append("\n(MV) ").append(gridBorderLine('└', '┴', '┘', mv_cols));

                GameFrame.getInstance().getRegistro().print(mv_tabla.toString());
            }
        }
    }

    // Formatea un porcentaje [0..100] a 2 decimales con punto y sufijo "%" para la
    // tabla MULTIVERSO del registro (p. ej. "86.50%"). Locale fijo (punto) para no
    // mezclar separadores de decimales dentro de la misma linea.
    private static String multiversePct(double value) {
        return String.format(java.util.Locale.US, "%.2f%%", value);
    }

    private void waitRabbitProcessing() {

        synchronized (lock_rabbit) {

            boolean pending = true;

            while (pending) {

                pending = false;

                for (Map.Entry<String, Boolean> entry : rabbit_players.entrySet()) {
                    if (entry.getValue()) {
                        pending = true;
                        break;
                    }
                }

                if (pending) {
                    try {
                        lock_rabbit.wait(1000);
                    } catch (InterruptedException ex) {
                        Helpers.logCooperativeCancellation(LOGGER, "rabbit hunting wait", ex);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Identity: seed the per-hand H_t chain from the three pieces every peer
     * already has after MEGAPACKET — the HAND_ID from the host, the active_crypto_ring
     * nicks and the cascaded deck. Idempotent: if any of them is missing the chain is
     * set to null so downstream absorb calls become no-ops (no consensus this hand).
     */
    private void initHandStateChain() {
        if (this.current_hand_id == null
                || this.local_mega_packet == null
                || this.active_crypto_ring == null
                || this.active_crypto_ring.length == 0) {
            this.hand_state_chain = null;
            return;
        }
        java.util.List<byte[]> playerIds = new java.util.ArrayList<>();
        for (String nick : this.active_crypto_ring) {
            playerIds.add(CanonicalActionRecord.playerIdFromNick(nick));
        }
        // H_0 ancla el commitment K (pocket + community) de cada miembro del ring; los K llegan
        // del mismo MEGAPACKET, así que host y cliente derivan el mismo H_0. Si falta alguno la
        // cadena no se inicializa (sin consenso esta mano): no debería ocurrir — todos los peers
        // corren la misma versión y mandan sus K — así que es un estado anómalo, no un degradado.
        java.util.List<byte[]> kPockets = new java.util.ArrayList<>();
        java.util.List<byte[]> kCommunities = new java.util.ArrayList<>();
        boolean allCommitmentsPresent = true;
        for (String nick : this.active_crypto_ring) {
            byte[] kp = this.peer_k_pocket.get(nick);
            byte[] kc = this.peer_k_community.get(nick);
            if (kp == null || kc == null) {
                allCommitmentsPresent = false;
                break;
            }
            kPockets.add(kp);
            kCommunities.add(kc);
        }
        if (!allCommitmentsPresent) {
            LOGGER.log(Level.SEVERE, "Missing K commitments — hand state chain not initialized (no consensus this hand)");
            this.hand_state_chain = null;
            return;
        }
        try {
            this.hand_state_chain = HandStateChain.start(
                    this.current_hand_id, playerIds, kPockets, kCommunities, this.local_mega_packet);
            LOGGER.log(Level.INFO, "Hand state chain initialized: H_0={0}",
                    Base64.getEncoder().encodeToString(this.hand_state_chain.getCurrentHash()));
            // Recovery: persist HAND_ID on the SQL hand row so
            // recovery can re-init the chain with the same handId after a crash.
            // Skipped during recovery replay itself (the row was already populated
            // by the original hand; the value being restored is what we want).
            sqlUpdateHandHandId(this.current_hand_id);
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "HandStateChain.start failed: " + ex.getMessage(), ex);
            this.hand_state_chain = null;
        }
    }

    /**
     * Recovery: writes the 16-byte HAND_ID (base64) onto the
     * current hand's SQL row. Best-effort; failures are logged but never fatal.
     * No-op when sqlite_id_hand isn't set yet (NUEVA_MANO ordering edge case).
     */
    private void sqlUpdateHandHandId(byte[] handId) {
        if (handId == null || this.sqlite_id_hand <= 0) {
            return;
        }
        synchronized (GameFrame.SQL_LOCK) {
            String sql = "UPDATE hand SET hand_id_b64=? WHERE id=?";
            try (java.sql.PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setQueryTimeout(30);
                statement.setString(1, Base64.getEncoder().encodeToString(handId));
                statement.setInt(2, this.sqlite_id_hand);
                statement.executeUpdate();
            } catch (java.sql.SQLException ex) {
                LOGGER.log(Level.SEVERE, "Failed to persist hand_id_b64 for hand " + this.sqlite_id_hand, ex);
            }
        }
    }

    /**
     * Translate the in-memory Java action enum into the wire enum the spec defines
     * (§4.3). Java collapses CALL into CHECK and RAISE into BET (the bet amount
     * differentiates them); we keep that collapse on the wire side too. What matters
     * is that every peer maps the same Java decision to the same wire value, so H_t
     * converges across the table.
     */
    private static int mapJavaActionToWire(int javaDecision) {
        switch (javaDecision) {
            case Player.FOLD:  return CanonicalActionRecord.ACTION_FOLD;
            case Player.CHECK: return CanonicalActionRecord.ACTION_CHECK;
            case Player.BET:   return CanonicalActionRecord.ACTION_BET;
            case Player.ALLIN: return CanonicalActionRecord.ACTION_ALLIN;
            default:
                throw new IllegalArgumentException("Unmappable Java decision: " + javaDecision);
        }
    }

    /** Java streets are 1-based (PREFLOP=1..SHOWDOWN=5), wire is 0-based. */
    private static int mapJavaStreetToWire(int javaStreet) {
        return javaStreet - 1;
    }

    /**
     * Returns {@code true} iff {@code nick} sits in the current hand's crypto-ring.
     * Players who joined or warmed up after the deal (and therefore are not in
     * {@code active_crypto_ring}) are excluded so their stray actions don't pollute
     * the chain.
     */
    private boolean isInActiveCryptoRing(String nick) {
        String[] ring = this.active_crypto_ring;
        if (ring == null) {
            return false;
        }
        for (String n : ring) {
            if (n.equals(nick)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Identity: absorbs the wire (record, sig) bytes directly into the
     * peer's H_t chain. Used by the action loop once the canonical record +
     * signature are in hand. Invalid signatures are absorbed too (the receipt
     * protocol catches divergence at hand close, §4.5). No-op when the chain
     * is uninitialised (older host without HAND_ID, recovery paths, etc).
     */
    private void absorbActionIntoChain(String playerNick, byte[] record, byte[] sig) {
        HandStateChain chain = this.hand_state_chain;
        if (chain == null) {
            return;
        }
        if (record == null || sig == null) {
            return;
        }
        if (!isInActiveCryptoRing(playerNick)) {
            return;
        }
        try {
            byte[] newHash = chain.absorb(record, sig);
            if (HandStateChain.DEBUG_HANDCHAIN) {
                try {
                    String hCheckCmd = "H_CHECK#"
                            + Base64.getEncoder().encodeToString(playerNick.getBytes("UTF-8"))
                            + "#" + Base64.getEncoder().encodeToString(newHash);
                    if (GameFrame.getInstance().isPartida_local()) {
                        broadcastGAMECommandFromServer(hCheckCmd, null, false);
                    }
                    LOGGER.log(Level.INFO, "H_CHECK after {0}'s signed action: {1}",
                            new Object[]{playerNick, Base64.getEncoder().encodeToString(newHash)});
                } catch (Exception broadcastEx) {
                    LOGGER.log(Level.WARNING, "H_CHECK broadcast failed: " + broadcastEx.getMessage());
                }
            }
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE,
                    "Failed to absorb signed action into hand state chain (nick=" + playerNick + ")", ex);
        }
    }

    /**
     * Identity: predicts the post-action total bet (in cents) for the
     * player about to act, WITHOUT waiting for async setDecision side-effects.
     *
     * <ul>
     *   <li>FOLD → 0 (no money moves)</li>
     *   <li>CHECK → {@code apuesta_actual} (covers true check and call)</li>
     *   <li>BET → absolute target supplied by the local UI / bot logic</li>
     *   <li>ALLIN → {@code player.bet + player.stack} (allin moves stack into bet,
     *       so the sum is invariant pre/post-action)</li>
     * </ul>
     */
    private long predictPostActionBetCents(int javaDecision, Object originalActionBet, Player player) {
        double betAbsoluteTarget = 0;
        if (originalActionBet instanceof Number) {
            betAbsoluteTarget = ((Number) originalActionBet).doubleValue();
        }
        return expectedActionAmountCents(javaDecision, betAbsoluteTarget,
                player.getBet(), player.getStack(), this.apuesta_actual);
    }

    /**
     * Identity / anti-forgery (PURA y testeable): el importe en centimos que el AMOUNT_CENTS de un action
     * record DEBE llevar, derivado SOLO de la decision y del estado PRE-accion del jugador. Es exactamente
     * la formula que usa el FIRMANTE (buildLocalActionRecordAndSig via predictPostActionBetCents), asi que
     * todo peer que replica la misma secuencia de acciones tiene identico estado pre-accion y obtiene
     * identico resultado. El receptor la recomputa para ATAR el importe firmado a la accion realmente jugada
     * (partes[4]/partes[5]): un cliente modificado que firme un importe distinto del que juega no cuela.
     *
     * FOLD -> 0; CHECK/CALL -> apuesta_actual; BET -> objetivo absoluto (== partes[5] del wire);
     * ALLIN -> bet + stack (invariante pre/post: el all-in mueve TODO el stack al bote).
     */
    static long expectedActionAmountCents(int javaDecision, double betAbsoluteTarget,
            double playerBet, double playerStack, double apuestaActual) {
        switch (javaDecision) {
            case Player.FOLD:
                return 0L;
            case Player.CHECK:
                return CanonicalActionRecord.amountToCents(Helpers.doubleClean(apuestaActual));
            case Player.BET:
                return CanonicalActionRecord.amountToCents(Helpers.doubleClean(betAbsoluteTarget));
            case Player.ALLIN:
                return CanonicalActionRecord.amountToCents(Helpers.doubleClean(playerBet + playerStack));
            default:
                return 0L;
        }
    }

    /**
     * Identity / anti-forgery (PURA y testeable): true SII el record firmado representa FIELMENTE la accion
     * realmente jugada, atando a la vez su TIPO (ACTION_TYPE) y su IMPORTE (AMOUNT_CENTS). El tipo se ata via
     * {@link #mapJavaActionToWire} (puro valor de wire, cero dependencia de estado), asi que dos decisiones
     * con el mismo importe (p.ej. FOLD y un CHECK con apuesta_actual=0) no se confunden; el importe via
     * {@link #expectedActionAmountCents}. Puede lanzar RuntimeException ante una decision no mapeable o un
     * importe irrepresentable (NaN/Inf) del bet: una accion legitima jamas lo produce, y el llamador lo trata
     * como forja (synth-fold). {@code record} debe tener ya longitud RECORD_BYTES (garantizado por el
     * llamador). {@code betObj} es el bet en claro del wire (Double para BET, ignorado en el resto).
     */
    static boolean signedRecordBindsToAction(byte[] record, int javaDecision, Object betObj,
            double playerBet, double playerStack, double apuestaActual,
            byte[] expectedPlayerId, byte[] expectedHandId) {
        // TIPO de accion (puro valor de wire).
        int signedType = CanonicalActionRecord.readActionType(record);
        int expectedType = mapJavaActionToWire(javaDecision);
        if (signedType != expectedType) {
            return false;
        }
        // JUGADOR: el record no puede atribuir la accion a otro jugador (aunque la firma sea del actor,
        // el PLAYER_ID debe ser el suyo). MANO: no puede reutilizar un record de otra mano. Ambos son
        // identificadores ESTABLES durante toda la mano (cero timing). Tolerante a expected nulo
        // (p.ej. cadena aun no sembrada): si no se puede derivar el esperado, no se exige.
        if (expectedPlayerId != null
                && !java.util.Arrays.equals(CanonicalActionRecord.readPlayerId(record), expectedPlayerId)) {
            return false;
        }
        if (expectedHandId != null
                && !java.util.Arrays.equals(CanonicalActionRecord.readHandId(record), expectedHandId)) {
            return false;
        }
        // IMPORTE.
        double betAbsoluteTarget = (betObj instanceof Number) ? ((Number) betObj).doubleValue() : 0;
        long signedCents = CanonicalActionRecord.readAmountCents(record);
        long expectedCents = expectedActionAmountCents(javaDecision, betAbsoluteTarget,
                playerBet, playerStack, apuestaActual);
        return signedCents == expectedCents;
    }

    /**
     * Identity: resolves the Ed25519 raw pubkey to verify an action's
     * signature against, applying the §10 consolidated receiver rule:
     *
     * <ul>
     *   <li>voluntary=0 (host auto-fold per §4.5) → host's pubkey</li>
     *   <li>actor is a bot (Participant.isCpu()) → host's pubkey (§10)</li>
     *   <li>otherwise (voluntary human action) → actor's pubkey</li>
     * </ul>
     *
     * Host's pubkey resolution depends on which side we are on:
     *   - partida_local=true (this process IS the host): IdentityManager.getInstance().getPublicKey().
     *   - partida_local=false (this process is a client): participantes.get(server_nick).getIdentity_pubkey().
     *
     * Returns null when the requested pubkey is not yet available (TOFU race during
     * a fresh JOIN). Caller treats null as "verification skipped" and logs.
     */
    private byte[] resolveActionSignerPubkey(String actorNick, boolean isVoluntary) {
        boolean useHostKey = !isVoluntary;
        if (!useHostKey) {
            Participant actorPar = GameFrame.getInstance().getParticipantes().get(actorNick);
            if (actorPar != null && actorPar.isCpu()) {
                useHostKey = true;
            } else if (actorPar != null) {
                return actorPar.getIdentity_pubkey();
            } else {
                return null;
            }
        }
        if (GameFrame.getInstance().isPartida_local()) {
            IdentityManager im = IdentityManager.getInstance();
            return im.isReady() ? im.getPublicKey() : null;
        }
        String hostNick = GameFrame.getInstance().getSala_espera().getServer_nick();
        if (hostNick == null) {
            return null;
        }
        Participant hostPar = GameFrame.getInstance().getParticipantes().get(hostNick);
        return hostPar != null ? hostPar.getIdentity_pubkey() : null;
    }

    /**
     * PHASE A.1 (showdown sig): resolver la pubkey del firmante esperado de un
     * SHOWCARDS / RESP_SHOWDOWN_KEY para nick. La identidad del firmante depende
     * de si el nick es un humano o un bot:
     *
     *   - Humano (local o remoto): firma con su propia Ed25519 privkey. Su
     *     pubkey vive en Participant.identity_pubkey (poblado vía TOFU/JOIN).
     *   - Bot: el host firma en su nombre (los bots viven en el proceso host,
     *     no tienen identity propia). La pubkey del verificador es entonces
     *     la del host.
     *
     * Devuelve null si la pubkey no está disponible (TOFU race / hand without
     * identity). El caller trata null como "no se puede verificar" — rechaza.
     */
    private byte[] resolveShowdownSignerPubkey(String revealNick) {
        Participant par = GameFrame.getInstance().getParticipantes().get(revealNick);
        if (par == null) {
            return null;
        }
        if (par.isCpu()) {
            // Bot — el firmante es el host.
            if (GameFrame.getInstance().isPartida_local()) {
                IdentityManager im = IdentityManager.getInstance();
                return im.isReady() ? im.getPublicKey() : null;
            }
            String hostNick = GameFrame.getInstance().getSala_espera().getServer_nick();
            if (hostNick == null) {
                return null;
            }
            Participant hostPar = GameFrame.getInstance().getParticipantes().get(hostNick);
            return hostPar != null ? hostPar.getIdentity_pubkey() : null;
        }
        return par.getIdentity_pubkey();
    }

    /**
     * Consensus: writes the in-game registro line for a
     * per-action signature failure so the user notices in real time. JUL has the
     * full SEVERE detail already; this line is the human-visible warning. Runs
     * on the GUI thread to keep the registro's append model consistent with master.
     */
    private void printInvalidActionSigToRegistro(String actorNick) {
        final String nick = actorNick;
        Helpers.GUIRun(() -> {
            try {
                GameFrame.getInstance().getRegistro().print(
                        MessageFormat.format(
                                Translator.translate("game.firma_accion_invalida"),
                                nick));
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Failed to print invalid-action-sig registro line", ex);
            }
        });
    }

    /**
     * Identity: builds and signs a canonical action record for an
     * action originating LOCALLY in this process — host's own UI, a bot (host drives
     * it), an auto-fold (host fabricates on behalf of a timed-out / EXITed peer), or
     * a client's own UI when partida_local is false. Returns null when the chain is
     * uninitialised or IdentityManager isn't ready (the wire then carries "*"
     * placeholders so the receiver skips verification gracefully).
     *
     * <p>{@code isVoluntary} is true for genuine decisions, false for host-fabricated
     * auto-folds (§4.5). Bot actions are voluntary=true but signed by the host's
     * privkey — receivers identify them by Participant.isCpu() (§10).
     */
    private Object[] buildLocalActionRecordAndSig(String playerNick, int javaDecision,
            Object originalActionBet, Player player, boolean isVoluntary) {
        HandStateChain chain = this.hand_state_chain;
        if (chain == null) {
            return null;
        }
        IdentityManager im = IdentityManager.getInstance();
        if (!im.isReady()) {
            return null;
        }
        try {
            int wireAction = mapJavaActionToWire(javaDecision);
            int wireStreet = mapJavaStreetToWire(this.street);
            long cents = predictPostActionBetCents(javaDecision, originalActionBet, player);
            byte[] pid = CanonicalActionRecord.playerIdFromNick(playerNick);
            byte[] record = CanonicalActionRecord.encode(
                    chain.getCurrentHash(),
                    chain.getHandId(),
                    pid,
                    wireStreet,
                    wireAction,
                    cents,
                    javaDecision == Player.ALLIN,
                    isVoluntary);
            byte[] sig = im.signAction(record);
            return new Object[]{record, sig};
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE,
                    "Failed to build local action record/sig (nick=" + playerNick
                    + ", decision=" + javaDecision + ")", ex);
            return null;
        }
    }

    /**
     * Identity: builds the canonical 92-byte community-reveal
     * record and signs it with the host's privkey. Called only on the host
     * (partida_local) right after the per-recipient PIECE cascade completes,
     * before broadcasting the announce wire. Returns null when the chain isn't
     * ready (legacy interop, recovery without chain restore, etc.) — the caller
     * skips broadcasting the announce in that case and the chain stays in
     * degraded mode for this hand.
     */
    private Object[] buildCommunityRevealRecordAndSig(int wireStreet, int[] cards) {
        HandStateChain chain = this.hand_state_chain;
        if (chain == null) {
            return null;
        }
        IdentityManager im = IdentityManager.getInstance();
        if (!im.isReady()) {
            return null;
        }
        try {
            byte[] pid = CanonicalActionRecord.playerIdFromNick(GameFrame.getInstance().getNick_local());
            long packed = CanonicalActionRecord.packCommunityCards(cards);
            byte[] record = CanonicalActionRecord.encode(
                    chain.getCurrentHash(),
                    chain.getHandId(),
                    pid,
                    wireStreet,
                    CanonicalActionRecord.ACTION_COMMUNITY,
                    packed,
                    false,
                    false);
            byte[] sig = im.signAction(record);
            return new Object[]{record, sig};
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE,
                    "Failed to build community-reveal record/sig (street=" + wireStreet + ")", ex);
            return null;
        }
    }

    private void sentarParticipantes() {

        String pivote = GameFrame.getInstance().getNick_local();

        int i = 0;

        while (!this.nicks_permutados[i].equals(pivote)) {
            i++;
        }

        String sentados_msg = "\n*********************\n";

        for (int j = 0; j < this.nicks_permutados.length; j++) {

            GameFrame.getInstance().getJugadores().get(j)
                    .setNickname(this.nicks_permutados[(j + i) % this.nicks_permutados.length]);
            try {
                sentados_msg += Base64.getEncoder().encodeToString(
                        GameFrame.getInstance().getJugadores().get(j).getNickname().getBytes("UTF-8")) + "|"
                        + GameFrame.getInstance().getJugadores().get(j).getNickname() + "\n";
            } catch (UnsupportedEncodingException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }

        Logger
                .getLogger(Crupier.class
                        .getName())
                .log(Level.INFO, "{0}*********************\n", new Object[]{sentados_msg
        });

    }

    public void disableAllPlayersTimeout() {

        for (Player j : GameFrame.getInstance().getJugadores()) {

            j.setTimeout(false);
        }

    }

    public void broadcastGAMECommandFromServer(String command, String skip_nick, boolean confirmation) {

        ArrayList<String> pendientes = new ArrayList<>();
        ArrayList<Participant> targets = new ArrayList<>();

        Map<String, Participant> participantes_map = GameFrame.getInstance().getParticipantes();
        synchronized (participantes_map) {
            for (Map.Entry<String, Participant> entry : participantes_map.entrySet()) {
                Participant p = entry.getValue();
                if (p != null && !p.isCpu() && !p.getNick().equals(skip_nick) && !p.isExit()) {
                    pendientes.add(p.getNick());
                    targets.add(p);
                }
            }
        }

        if (!pendientes.isEmpty()) {

            int id = Helpers.CSPRNG_GENERATOR.nextInt();
            byte[] iv = new byte[16];
            Helpers.CSPRNG_GENERATOR.nextBytes(iv);

            // Sin timeout artificial: si un cliente tarda mucho en confirmar (red lenta,
            // CPU saturada, baraja SRA grande), seguimos esperando indefinidamente.
            // TCP nos garantiza que el mensaje llegará o el socket morirá; nunca se
            // pierde "en el medio". La única salida del bucle es que pendientes se
            // vacíe (todos confirmaron o todos se marcaron exit porque su socket murió
            // de verdad). Antes este loop forzaba remotePlayerQuit() tras
            // CLIENT_RECON_TIMEOUT, lo que provocaba kicks injustos a clientes
            // simplemente lentos durante cascadas SRA.
            //
            // Para que la espera "infinita" no sea invisible al autor revisando el
            // debug log, llevamos un contador de iteraciones del do-while. Cada
            // iteración representa un período entero de CONFIRMATION_TIMEOUT
            // (~10 s) en el que waitSyncConfirmations no recibió todos los ACK.
            // A partir de la segunda iteración consecutiva sin progreso, emitimos
            // un Level.WARNING en JUL con los nicks que siguen pendientes y el
            // tiempo acumulado. Es solo info forense — no acelera el kick ni
            // dispara timeouts: el flujo sigue dependiendo de TCP/isExit.
            long broadcastStartMs = System.currentTimeMillis();
            long broadcastDeadlineMs = broadcastStartMs + BROADCAST_PROGRESS_TIMEOUT_MS;
            int slowIterCount = 0;
            do {
                String full_command = "GAME#" + String.valueOf(id) + "#" + command;

                for (Participant p : targets) {
                    if (pendientes.contains(p.getNick())) {
                        p.writeCommandFromServer(Helpers.encryptCommand(full_command, p.getAes_key(), iv, p.getHmac_key()));
                    }
                }

                if (confirmation) {
                    this.waitSyncConfirmations(id, pendientes);

                    for (Participant p : targets) {
                        if (!p.getNick().equals(skip_nick) && p.isExit()) {
                            pendientes.remove(p.getNick());
                            if (nick2player.containsKey(p.getNick())) {
                                nick2player.get(p.getNick()).setTimeout(false);
                            }
                        }
                    }

                    if (!pendientes.isEmpty() && !nick2player.isEmpty()) {
                        slowIterCount++;
                        if (slowIterCount >= 2) {
                            long elapsedMs = System.currentTimeMillis() - broadcastStartMs;
                            String cmdHead = command.length() > 40
                                    ? command.substring(0, 40) + "..."
                                    : command;
                            LOGGER.log(Level.WARNING,
                                    "Still waiting for ACK from {0} after {1} ms on broadcast \"{2}\" (TCP alive — no kick, will keep retrying until peers ACK or their sockets die)",
                                    new Object[]{
                                        String.join(", ", pendientes),
                                        elapsedMs,
                                        cmdHead});
                        }
                        for (String nick : pendientes) {
                            nick2player.get(nick).setTimeout(true);
                            if (!GameFrame.getInstance().getParticipantes().get(nick).isForce_reset_socket()) {
                                try {
                                    this.broadcastGAMECommandFromServer(
                                            "TIMEOUT#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")),
                                            nick, false);
                                } catch (UnsupportedEncodingException ex) {
                                    LOGGER.log(Level.SEVERE, null, ex);
                                }
                            }
                        }
                        // Deadline de progreso PAUSE-AWARE: el tiempo en pausa NO cuenta (un peer no confirma
                        // porque el juego está pausado). Con la timba EN MARCHA, si algún peer sigue sin ACK
                        // más allá del tope se le EXPULSA (la mesa sigue: al quedar exit se le saca de
                        // pendientes en la siguiente vuelta y el broadcast completa). Un peer caído de verdad
                        // ya sale por isExit (socket muerto). Antes el host reintentaba para siempre.
                        boolean holdDeadline = false;
                        try {
                            // Congela el deadline mientras la timba esté PAUSADA o mientras ALGÚN peer
                            // PENDIENTE esté genuinamente reconectando (socket caído/en reset). NO se usa
                            // isSomePlayerTimeout(): este mismo bucle acaba de marcar timeout=true a TODOS los
                            // pendientes (para la UI de "esperando"), así que isSomePlayerTimeout() estaría
                            // SIEMPRE true y el deadline no vencería jamás (bug de interacción con f2db6f7c). El
                            // socket VIVO distingue al que RETIENE (contesta PING, se le expulsa) del que
                            // reconecta (socket muerto, se le respeta su grace).
                            boolean anyPendingReconnecting = false;
                            for (String pnick : pendientes) {
                                Participant pep = GameFrame.getInstance().getParticipantes().get(pnick);
                                if (pep != null && pep.isSocketDownOrReconnecting()) {
                                    anyPendingReconnecting = true;
                                    break;
                                }
                            }
                            holdDeadline = GameFrame.getInstance().isTimba_pausada() || anyPendingReconnecting;
                        } catch (Exception ignored) {
                        }
                        if (holdDeadline) {
                            broadcastDeadlineMs = System.currentTimeMillis() + BROADCAST_PROGRESS_TIMEOUT_MS;
                        } else if (System.currentTimeMillis() >= broadcastDeadlineMs) {
                            for (String nick : new ArrayList<>(pendientes)) {
                                Participant pp = GameFrame.getInstance().getParticipantes().get(nick);
                                if (pp != null && !pp.isExit() && !pp.isCpu()) {
                                    LOGGER.log(Level.SEVERE,
                                            "ZERO-TRUST DoS: peer {0} withheld broadcast ACK past {1}ms (game running, answering PING) — expelling, table continues",
                                            new Object[]{nick, BROADCAST_PROGRESS_TIMEOUT_MS});
                                    warnMaliciousPeer(nick, "zero_trust.peer_conf_withheld");
                                    pp.markExitAndNotify("withheld broadcast ACK (progress deadline)");
                                    try {
                                        pp.socketClose();
                                    } catch (Exception ignored) {
                                    }
                                }
                            }
                        }
                    } else {
                        slowIterCount = 0;
                    }
                }

            } while (confirmation && !pendientes.isEmpty());
        }
    }

    public void broadcastGAMECommandFromServer(String command, String skip_nick) {

        broadcastGAMECommandFromServer(command, skip_nick, true);
    }

    /**
     * Telemetría: construye una TelemetryFrame con las métricas
     * actuales de TODOS los Participants (latencias del ping/pong del
     * servidor + reconnection_count acumulado) y la broadcasta a todos
     * los clientes vía "GAME#id#TELEMETRY#payload".
     *
     * Sólo callable en partida iniciada. Si Crupier o GameFrame no están
     * listos, no-op silencioso. NO inicia ningún thread propio — el caller
     * decide la frecuencia (típicamente cada PING_INTERVAL_MS desde un
     * Timer o thread externo).
     *
     * Llamada idempotente: el payload de salida es fresco snapshot del
     * estado actual; no muta estado.
     */
    public void broadcastTelemetryFrame() {
        try {
            if (GameFrame.getInstance() == null) {
                return;
            }
            java.util.Map<String, com.tonikelope.coronapoker.Participant> parts =
                    GameFrame.getInstance().getParticipantes();
            if (parts == null || parts.isEmpty()) {
                return;
            }
            java.util.Map<String, int[]> perPeer = new java.util.HashMap<>(parts.size() + 1);
            // Iteración protegida — participantes es synchronizedMap.
            synchronized (parts) {
                for (java.util.Map.Entry<String, com.tonikelope.coronapoker.Participant> e : parts.entrySet()) {
                    com.tonikelope.coronapoker.Participant p = e.getValue();
                    if (p == null) {
                        continue;
                    }
                    if (p.isCpu()) {
                        // Bots: locales al host, sin RTT → entrada verde 0/0/0.
                        perPeer.put(e.getKey(), new int[]{0, 0, 0});
                    } else {
                        perPeer.put(e.getKey(),
                                new int[]{p.getLatency(), p.getLatency2(), p.getReconnectionCount()});
                    }
                }
            }
            // El propio host se incluye con latencia 0 (es su propia perspectiva
            // — no se hace ping a sí mismo). recon=0 también para el host.
            String localNick = GameFrame.getInstance().getNick_local();
            if (localNick != null && !localNick.isEmpty()) {
                perPeer.put(localNick, new int[]{0, 0, 0});
            }
            Helpers.TelemetryFrame frame = new Helpers.TelemetryFrame(
                    System.currentTimeMillis(), perPeer);
            String payload = Helpers.encodeTelemetry(frame);
            // OJO: broadcastGAMECommandFromServer YA envuelve el comando como
            // "GAME#<id>#" + command — solo pasamos "TELEMETRY#<payload>".
            // Antes el comando salía doblemente envuelto
            // (GAME#id#GAME#id#TELEMETRY#payload) y el cliente lo veía como
            // un GAME#GAME — partes[2]="GAME" en lugar de "TELEMETRY" → no
            // entraba al case y la telemetría nunca se aplicaba en el cliente.
            String cmd = "TELEMETRY#" + payload;
            // skip_nick=null y confirmation=false: broadcast fire-and-forget
            // sin esperar ACK; la telemetría es best-effort, no necesita confirm.
            broadcastGAMECommandFromServer(cmd, null, false);
            // El host NO recibe su propio broadcast (broadcastGAMECommandFromServer
            // envía a clientes remotos, no se procesa a sí mismo). Aplicamos el
            // frame localmente para que el host vea las bolitas de sus clientes
            // con su latencia real + las suyas propias en verde.
            applyTelemetryFrameLocally(frame);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "broadcastTelemetryFrame failed (telemetry is best-effort, no game impact)", ex);
        }
    }

    /**
     * Telemetría: aplica un TelemetryFrame a los Player locales.
     * Usado por:
     *   - broadcastTelemetryFrame() del host (auto-aplicación, ya que el host
     *     no recibe su propio broadcast).
     *   - case "TELEMETRY" del cliente (al recibir broadcast del host).
     */
    public void applyTelemetryFrameLocally(Helpers.TelemetryFrame frame) {
        if (frame == null || frame.perPeer == null) {
            return;
        }
        java.util.Map<String, Player> n2p = getNick2player();
        if (n2p == null) {
            return;
        }
        for (java.util.Map.Entry<String, int[]> en : frame.perPeer.entrySet()) {
            Player p = n2p.get(en.getKey());
            int[] v = en.getValue();
            if (p == null || v == null || v.length < 3) {
                continue;
            }
            if (p instanceof RemotePlayer) {
                ((RemotePlayer) p).applyTelemetry(v[0], v[1], v[2]);
            } else if (p instanceof LocalPlayer) {
                ((LocalPlayer) p).applyTelemetry(v[0], v[1], v[2]);
            }
        }
    }

    private int permutadoNick2Pos(String nick) {

        int i = 0;

        while (i < this.nicks_permutados.length && !this.nicks_permutados[i].equals(nick)) {
            i++;
        }

        if (i == this.nicks_permutados.length) {
            return -1;
        }

        return i;
    }

    private String permutadoPos2Nick(int i) {
        // Modulo positivo, robusto contra Integer.MIN_VALUE. El bucle anterior
        // "while (i < 0) i += length" entraría en loop infinito si i == MIN_VALUE
        // porque sumar length nunca cruza el 0 antes del overflow (los valores
        // negativos rebotan). Fórmula equivalente para inputs típicos pero safe
        // en todos los rangos.
        int n = nicks_permutados.length;
        int mod = ((i % n) + n) % n;
        return nicks_permutados[mod];
    }

    // DEAD BUTTON STRATEGY
    private void calcularPosiciones() {

        String old_dealer_nick = this.dealer_nick;

        String old_big_blind = this.big_blind_nick;

        int big_blind_pos = 0;

        if (this.big_blind_nick == null) {

            // FIRST GAME HAND
            this.big_blind_nick = this.nicks_permutados[0];

            big_blind_pos = 0;

        } else {

            int old_bb_pos = permutadoNick2Pos(this.big_blind_nick);

            String new_big_blind = null;

            if (old_bb_pos == -1) {

                try {
                    // BIG BLIND LEFT THE GAME

                    String[] asientos = this.sqlRecoverGameSeats().split("#");

                    String grande_b64 = Base64.getEncoder().encodeToString(this.big_blind_nick.getBytes("UTF-8"));

                    int i = 0;

                    while (i < asientos.length && !asientos[i].equals(grande_b64)) {

                        i++;
                    }

                    int j = i;

                    i = (i + 1) % asientos.length;

                    while (j != i
                            && this.permutadoNick2Pos(
                                    new String(Base64.getDecoder().decode(asientos[i]), "UTF-8")) == -1) {

                        i = (i + 1) % asientos.length;
                    }

                    new_big_blind = new String(Base64.getDecoder().decode(asientos[i]), "UTF-8");

                    big_blind_pos = permutadoNick2Pos(new_big_blind);

                } catch (UnsupportedEncodingException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }

            } else {

                big_blind_pos = old_bb_pos + 1;

                new_big_blind = permutadoPos2Nick(big_blind_pos);
            }

            while (!this.nick2player.containsKey(new_big_blind) || !this.nick2player.get(new_big_blind).isActivo()) {

                new_big_blind = permutadoPos2Nick(++big_blind_pos);

            }

            this.big_blind_nick = new_big_blind;
        }

        if (getJugadoresActivos() == 2) {

            for (Player j : GameFrame.getInstance().getJugadores()) {

                if (j.isActivo() && !j.getNickname().equals(this.big_blind_nick)) {
                    this.small_blind_nick = j.getNickname();
                    break;
                }

            }

            this.dealer_nick = this.small_blind_nick;

            this.utg_nick = this.dealer_nick;

        } else {

            // UTG
            int utg_pos = big_blind_pos + 1;

            String new_utg = permutadoPos2Nick(utg_pos);

            while (!this.nick2player.containsKey(new_utg) || !this.nick2player.get(new_utg).isActivo()) {

                new_utg = permutadoPos2Nick(++utg_pos);

            }

            this.utg_nick = new_utg;

            // DEALER
            int dealer_pos;

            String new_dealer;

            String old_small_blind = this.small_blind_nick;

            if (old_small_blind != null) {

                new_dealer = old_small_blind;

                dealer_pos = permutadoNick2Pos(new_dealer);

                if (dealer_pos == -1) {

                    try {

                        String[] asientos = this.sqlRecoverGameSeats().split("#");

                        String dealer_b64 = Base64.getEncoder().encodeToString(new_dealer.getBytes("UTF-8"));

                        int i = 0;

                        while (i < asientos.length && !asientos[i].equals(dealer_b64)) {

                            i++;
                        }

                        int j = i;

                        i--;

                        if (i < 0) {
                            i += asientos.length;
                        }

                        while (j != i && this
                                .permutadoNick2Pos(
                                        new String(Base64.getDecoder().decode(asientos[i]), "UTF-8")) == -1) {

                            i--;

                            if (i < 0) {
                                i += asientos.length;
                            }
                        }

                        new_dealer = new String(Base64.getDecoder().decode(asientos[i]), "UTF-8");

                        dealer_pos = permutadoNick2Pos(new_dealer);

                    } catch (UnsupportedEncodingException ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    }

                }

            } else {

                dealer_pos = big_blind_pos - 2;
                new_dealer = permutadoPos2Nick(dealer_pos);

            }

            while (!this.nick2player.containsKey(new_dealer) || !this.nick2player.get(new_dealer).isActivo()) {

                new_dealer = permutadoPos2Nick(--dealer_pos);

            }

            this.dealer_nick = new_dealer;

            // SMALL BLIND
            if (old_big_blind != null) {

                this.small_blind_nick = old_big_blind;

            } else {

                this.small_blind_nick = permutadoPos2Nick(big_blind_pos - 1);
            }

        }

        this.dead_dealer = (this.dealer_nick.equals(old_dealer_nick));
    }

    private void setPositions() {

        if (GameFrame.getInstance().isPartida_local()) {

            this.calcularPosiciones();

            String comando = null;

            boolean doblar_ciegas = this.checkDoblarCiegas();

            try {

                comando = "POSITIONS#" + Base64.getEncoder().encodeToString(this.utg_nick.getBytes("UTF-8")) + "#"
                        + Base64.getEncoder().encodeToString(this.big_blind_nick.getBytes("UTF-8")) + "#"
                        + Base64.getEncoder().encodeToString(this.small_blind_nick.getBytes("UTF-8")) + "#"
                        + Base64.getEncoder().encodeToString(
                                this.dealer_nick != null ? this.dealer_nick.getBytes("UTF-8") : "".getBytes("UTF-8"))
                        + "#" + String.valueOf(GameFrame.getInstance().getConta_tiempo_juego())
                        + (doblar_ciegas ? "#1" : "#0");

            } catch (UnsupportedEncodingException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

            broadcastGAMECommandFromServer(comando, null);

            if (doblar_ciegas) {
                this.doblarCiegas();
            }

        } else {

            // Recibimos las posiciones utg, bb, sb, dealer calculados por el servidor
            this.recibirPosiciones();

        }
    }

    private ArrayList<String> recuperarSorteoSitios() {

        try {

            ArrayList<String> actuales = new ArrayList<>();

            for (Map.Entry<String, Participant> entry : GameFrame.getInstance().getParticipantes().entrySet()) {

                actuales.add(entry.getKey());
            }

            String[] sitiosb64 = this.sqlRecoverGameSeats().split("#");

            // sqlRecoverServerLocalGameKeyData ahora puede devolver null (sin fila:
            // juego sin manos / DB no disponible). Sin este null-check el .get() hacía
            // NPE y, como el catch de este método solo coge IOException, el NPE escapaba
            // y mataba el hilo del Crupier en sortearSitios -> ANTES incluso de llegar a
            // recuperarDatosClavePartida (cliente colgado en "sorteando sitios"). Mismo
            // fallback que preflop_players==null de abajo: null -> fresh shuffle.
            HashMap<String, Object> key_data = this.sqlRecoverServerLocalGameKeyData(false);
            if (key_data == null) {
                LOGGER.log(Level.WARNING, "recuperarSorteoSitios: no key-data row in SQL — falling back to fresh shuffle");
                return null;
            }
            String preflop_players = (String) key_data.get("preflop_players");

            // Tras un MISDEAL que aborta antes de que la mano tenga el row
            // preflop_players guardado en SQL, esta lectura devuelve null y el
            // .contains(b64) de abajo lanzaba NullPointerException. El catch
            // sólo coge IOException, así que el NPE escapaba y mataba el
            // thread del Crupier silenciosamente — el cliente quedaba
            // esperando un SEATS que nunca llegaba ("sorteando sitios"
            // colgado). Devolviendo null aquí, el caller (sortearSitios) cae
            // en su rama `else` y hace un shuffle fresh, comportamiento de
            // "no hay nada que recuperar".
            if (preflop_players == null) {
                LOGGER.log(Level.WARNING, "recuperarSorteoSitios: no preflop_players row in SQL — falling back to fresh shuffle");
                return null;
            }

            ArrayList<String> permutados = new ArrayList<>();

            for (String b64 : sitiosb64) {

                String nick = new String(Base64.getDecoder().decode(b64), "UTF-8");

                if (actuales.contains(nick) && preflop_players.contains(b64)) {
                    permutados.add(nick);
                    actuales.remove(nick);
                }
            }

            if (!actuales.isEmpty() && !permutados.isEmpty()) {

                HashMap<String, Object> map = this.sqlRecoverGamePositions();

                // map==null (sin fila de posiciones / error SQL): mismo fallback que arriba
                // con preflop_players -> fresh shuffle. Antes map.get("bb") aquí hacía NPE y,
                // como el catch sólo coge IOException, el NPE escapaba y mataba el hilo del
                // Crupier (cliente colgado esperando un SEATS que no llegaba).
                if (map == null) {
                    LOGGER.log(Level.WARNING, "recuperarSorteoSitios: no positions row in SQL — falling back to fresh shuffle");
                    return null;
                }

                String grande = (String) map.get("bb");

                ArrayList<String> permutados_aux = new ArrayList<>();

                if (!permutados.contains(grande)) {

                    String[] asientos = this.sqlRecoverGameSeats().split("#");

                    String grande_b64 = Base64.getEncoder().encodeToString(grande.getBytes("UTF-8"));

                    int i = 0;

                    while (i < asientos.length && !asientos[i].equals(grande_b64)) {

                        i++;
                    }

                    int j = i;

                    i = (i + 1) % asientos.length;

                    while (j != i
                            && !permutados.contains(new String(Base64.getDecoder().decode(asientos[i]), "UTF-8"))) {

                        i = (i + 1) % asientos.length;
                    }

                    grande = new String(Base64.getDecoder().decode(asientos[i]), "UTF-8");
                }

                for (String nick : permutados) {

                    permutados_aux.add(nick);

                    if (nick.equals(grande)) {

                        // Los jugadores nuevos los colocamos después de la CIEGA GRANDE ACTUAL
                        Collections.shuffle(actuales, Helpers.CSPRNG_GENERATOR);
                        permutados_aux.addAll(actuales);
                        actuales.clear();
                    }
                }

                permutados = permutados_aux;
            }

            return permutados.isEmpty() ? actuales : permutados;

        } catch (IOException ex) {
            Logger.getLogger(Crupier.class
                    .getName()).log(Level.SEVERE, null, ex);
        }

        return null;
    }

    private void sqlUpdateGameDoubleBlinds() {

        synchronized (GameFrame.SQL_LOCK) {

            String sql = "UPDATE game SET blinds_time_type=?, blinds_time=? WHERE id=?";

            try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setQueryTimeout(30);

                statement.setInt(1, GameFrame.CIEGAS_DOUBLE_TYPE);
                statement.setInt(2, GameFrame.CIEGAS_DOUBLE);
                statement.setInt(3, this.sqlite_id_game);
                statement.executeUpdate();
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

        }

    }

    private String sqlRecoverGameSeats() {
        synchronized (GameFrame.SQL_LOCK) {

            String ret = null;

            String sql = "SELECT players from game WHERE id=?";

            try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setQueryTimeout(30);

                statement.setInt(1, this.sqlite_id_game);

                ResultSet rs = statement.executeQuery();

                rs.next();

                ret = rs.getString("players");
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

            return ret;

        }
    }

    private void sqlUpdateGameSeats(String players) {

        synchronized (GameFrame.SQL_LOCK) {

            String sql = "UPDATE game SET players=? WHERE id=?";

            try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setQueryTimeout(30);

                statement.setString(1, players);

                statement.setInt(2, this.sqlite_id_game);

                statement.executeUpdate();
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

        }
    }

    private String sqlRecoverHandActions() {
        synchronized (GameFrame.SQL_LOCK) {
            String ret = null;
            // Recovery: pull record_b64 / sig_b64 alongside the
            // legacy fields so recovery replays each action with the exact bytes
            // that were absorbed into H_t pre-crash. Both columns are nullable;
            // missing values map to "*" on the wire so the receiver falls back to
            // a no-op absorb for that step (chain stays at the previous H_t).
            String sql = "SELECT player, action, round(bet,2) as bet, record_b64, sig_b64 FROM action WHERE action.id_hand=? ORDER BY counter ASC";
            String actions = null;
            try (java.sql.PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setQueryTimeout(30);
                statement.setInt(1, this.sqlite_id_hand);
                java.sql.ResultSet rs = statement.executeQuery();
                actions = "";
                while (rs.next()) {
                    String recordB64 = rs.getString("record_b64");
                    String sigB64 = rs.getString("sig_b64");
                    actions += java.util.Base64.getEncoder().encodeToString(rs.getString("player").getBytes("UTF-8")) + "#"
                            + String.valueOf(rs.getInt("action")) + "#"
                            + String.valueOf(rs.getDouble("bet")) + "#"
                            + (recordB64 != null && !recordB64.isEmpty() ? recordB64 : "*") + "#"
                            + (sigB64 != null && !sigB64.isEmpty() ? sigB64 : "*") + "@";
                }
                ret = actions;
            } catch (java.sql.SQLException ex) {
                java.util.logging.Logger.getLogger(Crupier.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
            } catch (java.io.UnsupportedEncodingException ex) {
                java.util.logging.Logger.getLogger(Crupier.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
            }
            java.util.logging.Logger.getLogger(Crupier.class.getName()).log(java.util.logging.Level.INFO, actions);
            return ret;
        }
    }

    public HashMap<String, Object> sqlRecoverServerLocalGameKeyData(boolean include_balance) {

        synchronized (GameFrame.SQL_LOCK) {

            HashMap<String, Object> map = null;

            try {

                String sql = "select hand.id as hand_id, hand.end as hand_end, hand.preflop_players as preflop_players, hand.hand_id_b64 as hand_id_b64, server, game.start, buyin, rebuy, play_time, (SELECT count(hand.id) from hand where hand.id_game=?) as conta_mano, round(hand.sbval,2) as sbval, round((hand.sbval*2),2) as bbval, blinds_time, blinds_time_type, hand.blinds_double as blinds_double, hand.dealer as dealer, hand.sb as sb, hand.bb as bb from game,hand where hand.id=(SELECT max(hand.id) from hand,game where hand.id_game=game.id and hand.id_game=?) and game.id=hand.id_game and hand.id_game=?";

                try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {

                    statement.setQueryTimeout(30);
                    statement.setInt(1, this.sqlite_id_game);
                    statement.setInt(2, this.sqlite_id_game);
                    statement.setInt(3, this.sqlite_id_game);

                    try (ResultSet rs = statement.executeQuery()) {

                        // Sin fila (juego sin manos / no encontrado): devolvemos null
                        // limpio en vez de dejar que el primer rs.getX lance SQLException.
                        // El caller (recuperarDatosClavePartida) ya trata null como
                        // fresh-start. Antes el rs.next() se ignoraba y "no hay fila" se
                        // colaba como excepción.
                        if (!rs.next()) {
                            return null;
                        }

                        map = new HashMap<>();
                        map.put("start", rs.getLong("start"));
                        map.put("hand_id", rs.getInt("hand_id"));
                        map.put("hand_end", rs.getLong("hand_end"));
                        map.put("server", rs.getString("server"));
                        map.put("preflop_players", rs.getString("preflop_players"));
                        // Recovery: cryptographic HAND_ID (16 bytes,
                        // base64) needed to re-seed HandStateChain.start with the same
                        // value the original hand used. Nullable — recovery falls back
                        // to "chain stays null" (legacy degraded mode) when missing.
                        String handIdB64 = rs.getString("hand_id_b64");
                        if (handIdB64 != null) {
                            map.put("hand_id_b64", handIdB64);
                        }
                        map.put("buyin", rs.getInt("buyin"));
                        map.put("rebuy", rs.getBoolean("rebuy"));
                        map.put("play_time", rs.getLong("play_time"));
                        map.put("conta_mano", rs.getInt("conta_mano"));
                        map.put("sbval", rs.getDouble("sbval"));
                        map.put("bbval", rs.getDouble("bbval"));
                        map.put("blinds_time", rs.getInt("blinds_time"));
                        map.put("blinds_time_type", rs.getInt("blinds_time_type"));
                        map.put("blinds_double", rs.getInt("blinds_double"));
                        map.put("dealer", rs.getString("dealer"));
                        map.put("sb", rs.getString("sb"));
                        map.put("bb", rs.getString("bb"));
                    }
                }

                if (include_balance) {

                    // Recuperamos el balance
                    if (Files.exists(Paths.get(Init.CORONA_DIR + "/balance")) && Helpers.mostrarMensajeInformativoSINO(
                            GameFrame.getInstance(),
                            Translator.translate("ui.se_ha_encontrado_un_fichero"),
                            new ImageIcon(Init.class.getResource("/images/mantenimiento.png"))) == 0) {

                        try {
                            String balance = Files.readString(Paths.get(Init.CORONA_DIR + "/balance"));

                            // REPLACE_EXISTING: si una recuperación previa dejó un
                            // balance_used huérfano, el move fallaba
                            // con FileAlreadyExistsException, el catch lo logueaba SEVERE
                            // pero el balance original quedaba en disco para una eventual
                            // tercera recuperación con datos viejos. REPLACE_EXISTING
                            // garantiza marca-como-usado idempotente.
                            Files.move(Paths.get(Init.CORONA_DIR + "/balance"),
                                    Paths.get(Init.CORONA_DIR + "/balance_used"),
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                            map.put("balance", balance.trim());

                            LOGGER.log(Level.WARNING, "Forced recovered balance applied");

                            LOGGER.log(Level.WARNING, balance);

                        } catch (Exception ex) {
                            LOGGER.log(Level.SEVERE, null, ex);
                        }

                    } else {

                        sql = "select balance.player as PLAYER, round(balance.stack,2) as STACK, balance.buyin as BUYIN, balance.rebuy_count as REBUY_COUNT from balance,hand,game where balance.id_hand=hand.id and game.id=? and hand.id=(SELECT max(hand.id) from hand,balance where hand.id=balance.id_hand and hand.id_game=?)";

                        try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {

                            statement.setQueryTimeout(30);
                            statement.setInt(1, this.sqlite_id_game);
                            statement.setInt(2, this.sqlite_id_game);

                            try (ResultSet rs = statement.executeQuery()) {

                                ArrayList<String> balance = new ArrayList<>();

                                while (rs.next()) {

                                    balance.add(
                                            Base64.getEncoder().encodeToString(rs.getString("PLAYER").getBytes("UTF-8")) + "|"
                                            + rs.getDouble("STACK") + "|" + rs.getInt("BUYIN") + "|" + rs.getInt("REBUY_COUNT"));
                                }

                                map.put("balance", String.join("@", balance));
                            }
                        }
                    }
                }

            } catch (SQLException | UnsupportedEncodingException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

            return map;

        }
    }

    private HashMap<String, Object> sqlRecoverGamePositions() {

        synchronized (GameFrame.SQL_LOCK) {

            HashMap<String, Object> map = null;

            String sql = "select hand.dealer as dealer, hand.sb as sb, hand.bb as bb from game,hand where hand.id=(SELECT max(hand.id) from hand,game where hand.id_game=game.id and hand.id_game=?) and game.id=hand.id_game and hand.id_game=?";

            try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setQueryTimeout(30);

                statement.setInt(1, this.sqlite_id_game);

                statement.setInt(2, this.sqlite_id_game);

                ResultSet rs = statement.executeQuery();

                // Sin fila (juego sin manos / no encontrado): null limpio en vez de colar
                // la ausencia como SQLException (gemelo del guard de
                // sqlRecoverServerLocalGameKeyData). El caller trata null como fresh shuffle.
                if (!rs.next()) {
                    return null;
                }

                map = new HashMap<>();

                map.put("dealer", rs.getString("dealer"));
                map.put("sb", rs.getString("sb"));
                map.put("bb", rs.getString("bb"));
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

            return map;

        }
    }

    /**
     * ZERO-TRUST RECOVER (PURA y testeable): en el replay de recuperación el cliente reproduce sus PROPIAS
     * acciones a partir de la copia que sirve el HOST y, si no llevan record, las RE-FIRMA con su clave. Un
     * host hostil podía por tanto forjar la decisión/importe de la víctima (firmados luego por ella, limpios
     * en H_t). Esta función ata el plaintext reproducido (decisión + importe) al record FIRMADO que lo
     * acompaña, sin depender del estado reconstruido: TIPO (siempre), IMPORTE de BET (partes[2]; el de
     * CHECK/ALLIN lo fija la regla del juego, no el record, así que no es forjable por aquí), PLAYER_ID y
     * HAND_ID. Devuelve false (= forja) ante cualquier discrepancia o record/decisión no representables. El
     * llamador, ante false, sintetiza FOLD en vez de reproducir/re-firmar lo forjado.
     */
    static boolean recoveredActionBindsToRecord(byte[] record, int decision, Object betObj, String nick,
            byte[] expectedHandId) {
        try {
            if (CanonicalActionRecord.readActionType(record) != mapJavaActionToWire(decision)) {
                return false;
            }
            if (decision == Player.BET) {
                double bet = (betObj instanceof Number) ? ((Number) betObj).doubleValue() : 0;
                if (CanonicalActionRecord.readAmountCents(record)
                        != CanonicalActionRecord.amountToCents(Helpers.doubleClean(bet))) {
                    return false;
                }
            }
            if (!java.util.Arrays.equals(CanonicalActionRecord.readPlayerId(record),
                    CanonicalActionRecord.playerIdFromNick(nick))) {
                return false;
            }
            if (expectedHandId != null
                    && !java.util.Arrays.equals(CanonicalActionRecord.readHandId(record), expectedHandId)) {
                return false;
            }
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private Object[] siguienteAccionLocalRecuperada(String nick) {

        Object[] res = null;

        while (!this.acciones_locales_recuperadas.isEmpty()) {
            try {
                String accion = this.acciones_locales_recuperadas.poll();

                String[] accion_partes = accion.split("#");

                String name = new String(Base64.getDecoder().decode(accion_partes[0]), "UTF-8");

                if (name.equals(nick)) {

                    // Recovery: return a 6-slot action so the
                    // rondaApuestas absorb path picks up the persisted record + sig
                    // bytes and ratchets H_t exactly as before the crash. Shorter
                    // recovery data (3 fields) falls back to "*" placeholders — those
                    // leave record/sig null, the canBuild gate takes over (host
                    // re-builds with its privkey for its own actions, client no-ops
                    // for others), so the chain ends up null for the recovered hand
                    // only when the shorter data is fed in.
                    res = new Object[6];

                    res[0] = Integer.parseInt(accion_partes[1]);

                    if ((int) res[0] == Player.BET) {
                        res[1] = Helpers.doubleClean(Double.parseDouble(accion_partes[2]));
                    } else {
                        res[1] = 0d;
                    }
                    res[2] = null;
                    res[3] = null;
                    res[4] = null;
                    res[5] = Boolean.TRUE;
                    if (accion_partes.length >= 5
                            && !"*".equals(accion_partes[3]) && !"*".equals(accion_partes[4])) {
                        boolean recoverForged = false;
                        try {
                            byte[] recordBytes = Base64.getDecoder().decode(accion_partes[3]);
                            byte[] sigBytes = Base64.getDecoder().decode(accion_partes[4]);
                            res[3] = recordBytes;
                            res[4] = sigBytes;
                            if (recordBytes != null && recordBytes.length == CanonicalActionRecord.RECORD_BYTES) {
                                int flags = ((recordBytes[CanonicalActionRecord.OFFSET_FLAGS] & 0xff) << 8)
                                        | (recordBytes[CanonicalActionRecord.OFFSET_FLAGS + 1] & 0xff);
                                res[5] = ((flags >> CanonicalActionRecord.FLAG_BIT_VOLUNTARY) & 1) != 0;
                            }
                            // ZERO-TRUST RECOVER (HIGH cerrado): (1) verificar la FIRMA Ed25519 (el host sirve el
                            // record por wire; pubkey null en carrera TOFU -> se salta la firma, no es evidencia de
                            // ataque). (2) ATAR la decision/importe reproducidos (res[0]/res[1], que MUEVEN dinero y
                            // que el cliente RE-FIRMA con SU clave aguas abajo) al record firmado (recoveredActionBinds
                            // ToRecord) -> un host no puede servir un record valido con un plaintext distinto. Ambas
                            // fallando = forja.
                            byte[] recoverSignerPubkey = resolveActionSignerPubkey(name, Boolean.TRUE.equals(res[5]));
                            if (recoverSignerPubkey != null
                                    && !IdentityManager.verifyAction(recoverSignerPubkey, recordBytes, sigBytes)) {
                                LOGGER.log(Level.SEVERE,
                                        "ZERO-TRUST RECOVER: recovered action for {0} FAILED signature verify — host forging",
                                        name);
                                recoverForged = true;
                            } else if (!recoveredActionBindsToRecord(recordBytes, (int) res[0], res[1], name,
                                    this.hand_state_chain != null ? this.hand_state_chain.getHandId() : null)) {
                                LOGGER.log(Level.SEVERE,
                                        "ZERO-TRUST RECOVER: recovered action for {0} does not bind to its signed record (type/amount/player/hand) — host forging",
                                        name);
                                recoverForged = true;
                            }
                        } catch (Exception decodeEx) {
                            LOGGER.log(Level.WARNING, "Failed to decode/verify persisted record/sig on recovery replay", decodeEx);
                            recoverForged = true;
                        }
                        if (recoverForged) {
                            // NO reproducir la decision/importe forjados NI re-firmarlos con la clave de la
                            // victima: se sintetiza un FOLD (simetrico en todo receptor via el re-broadcast del
                            // replay -> converge por omision mutua) y se marca el incidente para el consenso de
                            // cierre. Cierra el HIGH: antes se anulaba record+sig pero se mantenia res[0]/res[1],
                            // que el cliente re-firmaba con su clave -> forja LIMPIA en H_t.
                            warnSuspiciousHost(Translator.translate("zero_trust.host_recover_action_forged"));
                            res[0] = Player.FOLD;
                            res[1] = 0d;
                            res[3] = null;
                            res[4] = null;
                            res[5] = Boolean.FALSE;
                            this.saw_invalid_action_sig = true;
                        }
                    } else if (this.hand_state_chain != null) {
                        // Record ausente/"*" con la CADENA ACTIVA. En una mano identity-mode TODAS las acciones
                        // propias llevan record firmado (chain!=null <=> la mano tenia records), asi que un "*"
                        // aqui = el host quito la firma para forjar la decision/importe (que el cliente re-firmaria
                        // con SU clave). Mismo trato que el path en vivo (no-record + chain activa -> synth-fold).
                        // En modo legacy sin cadena (chain==null) se deja pasar (recovery de manos viejas).
                        LOGGER.log(Level.SEVERE,
                                "ZERO-TRUST RECOVER: recovered action for {0} carries no signed record while the chain is active — host forging",
                                name);
                        warnSuspiciousHost(Translator.translate("zero_trust.host_recover_action_forged"));
                        res[0] = Player.FOLD;
                        res[1] = 0d;
                        res[5] = Boolean.FALSE;
                        this.saw_invalid_action_sig = true;
                    }

                    break;
                }

            } catch (UnsupportedEncodingException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }

        if (this.acciones_locales_recuperadas.isEmpty()) {

            Helpers.GUIRun(() -> {
                if (recover_dialog != null) {
                    recover_dialog.setVisible(false);
                    recover_dialog.dispose();
                    recover_dialog = null;
                }

                GameFrame.getInstance().getFull_screen_menu().setEnabled(true);
                Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(true);
                GameFrame.getInstance().refresh();
            });

            this.setSincronizando_mano(false);

            GameFrame.getInstance().getRegistro().print(Translator.translate("game.timba_recuperada"));

            if (GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {

                Audio.playWavResource("misc/startplay.wav");
            }

            if (recovering_music_active) {
                Audio.stopLoopMp3("misc/recovering.mp3");
                Audio.playLoopMp3Resource("misc/background_music.mp3");
                recovering_music_active = false;
            }

        }

        return res;

    }

    private void recuperarAccionesLocales() {
        try {
            String datos;

            if (GameFrame.getInstance().isPartida_local()) {
                datos = sqlRecoverHandActions();
                ArrayList<String> pendientes = new ArrayList<>();
                for (Player jugador : GameFrame.getInstance().getJugadores()) {
                    if (jugador != GameFrame.getInstance().getLocalPlayer() && jugador.isActivo()
                            && !GameFrame.getInstance().getParticipantes().get(jugador.getNickname()).isCpu()) {
                        pendientes.add(jugador.getNickname());
                    }
                }
                enviarAccionesRecuperadas(pendientes, datos);
            } else {
                datos = this.recibirAccionesRecuperadas();
            }

            this.tot_acciones_recuperadas = 0;
            if (datos != null && !datos.isEmpty() && !datos.equals("*")) {
                String[] rec = datos.split("@");
                for (String r : rec) {
                    if (!"".equals(r)) {
                        this.tot_acciones_recuperadas++;
                        String[] parts = r.split("#");
                        String nick = new String(Base64.getDecoder().decode(parts[0]), "UTF-8");

                        if (GameFrame.getInstance().getLocalPlayer().getNickname().equals(nick)
                                || (GameFrame.getInstance().isPartida_local()
                                && GameFrame.getInstance().getParticipantes().containsKey(nick)
                                && GameFrame.getInstance().getParticipantes().get(nick).isCpu())) {
                            acciones_locales_recuperadas.add(r);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Defensive cleanup: dispose the recovery dragon dialog (if open) and clear the
     * sincronizando_mano flag. Idempotent — calling it when nothing is pending is a
     * no-op. Used by early-exit paths (cancelarManoYDevolverApuestas, GAME OVER) so a
     * dragon left behind by an aborted recovery does not stay on screen forever and
     * does not keep suppressing cinematics/sounds via the sincronizando_mano gate.
     */
    private void cerrarRecoverDialogYSync() {
        boolean wasSyncing = this.isSincronizando_mano();
        boolean hadDialog = (this.recover_dialog != null);

        if (hadDialog) {
            Helpers.GUIRun(() -> {
                if (recover_dialog != null) {
                    recover_dialog.setVisible(false);
                    recover_dialog.dispose();
                    recover_dialog = null;
                }
                GameFrame.getInstance().getFull_screen_menu().setEnabled(true);
                Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(true);
                GameFrame.getInstance().refresh();
            });
        }

        if (wasSyncing) {
            this.setSincronizando_mano(false);
            this.acciones_locales_recuperadas.clear();
            if (recovering_music_active) {
                Audio.stopLoopMp3("misc/recovering.mp3");
                Audio.playLoopMp3Resource("misc/background_music.mp3");
                recovering_music_active = false;
            }
        }
    }

    private String[] sortearSitios() {

        ArrayList<String> nicks = null;

        String[] permutados = null;

        if (GameFrame.getInstance().isPartida_local()) {

            if (!GameFrame.isRECOVER() || (nicks = this.recuperarSorteoSitios()) == null) {

                nicks = new ArrayList<>();

                // Safe iteration over map keys to avoid CME during player drop
                synchronized (GameFrame.getInstance().getParticipantes()) {
                    for (String key : GameFrame.getInstance().getParticipantes().keySet()) {
                        nicks.add(key);
                    }
                }

                Collections.shuffle(nicks, Helpers.CSPRNG_GENERATOR);
            }

            // Comunicamos a todos los participantes el sorteo
            String command = "SEATS#" + String.valueOf(nicks.size());

            for (String nick : nicks) {

                try {
                    command += "#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8"));
                } catch (UnsupportedEncodingException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }

            this.broadcastGAMECommandFromServer(command, null);

            permutados = nicks.toArray(new String[0]);

        } else {

            boolean ok;

            long start_time = System.currentTimeMillis();

            do {

                ok = false;

                synchronized (this.getReceived_commands()) {

                    ArrayList<String> rejected = new ArrayList<>();

                    while (!ok && !this.getReceived_commands().isEmpty()) {

                        String comando = this.received_commands.poll();
                        try {
                            String[] partes = comando.split("#");

                            if (partes.length >= 4 && partes[2].equals("SEATS")) {

                                int tot = Integer.valueOf(partes[3]);

                                if (partes.length < 4 + tot) {
                                    LOGGER.log(Level.WARNING, "SEATS malformed (tot={0} but len={1}): {2}",
                                            new Object[]{tot, partes.length, comando});
                                    continue;
                                }

                                ok = true;
                                permutados = new String[tot];

                                for (int i = 0; i < tot; i++) {

                                    try {
                                        permutados[i] = new String(Base64.getDecoder().decode(partes[i + 4]), "UTF-8");
                                    } catch (UnsupportedEncodingException ex) {
                                        LOGGER.log(Level.SEVERE, null, ex);
                                    }
                                }
                            } else if (partes.length >= 3 && partes[2].equals("SEATS")) {
                                LOGGER.log(Level.WARNING, "SEATS malformed dropped: {0}", comando);
                            } else {
                                rejected.add(comando);
                            }
                        } catch (Exception ex) {
                            LOGGER.log(Level.WARNING, "Exception while processing command in receiveSEATS: " + comando, ex);
                        }

                    }

                    if (!rejected.isEmpty()) {
                        this.getReceived_commands().addAll(rejected);
                        rejected.clear();
                    }

                }

                if (!ok) {

                    if (GameFrame.getInstance().checkPause()) {
                        start_time = System.currentTimeMillis();
                    } else if (System.currentTimeMillis() - start_time > GameFrame.CLIENT_RECEPTION_TIMEOUT) {

                        LOGGER.log(Level.WARNING, "sortearSitios timeout — SEATS never arrived from host. Breaking wait; permutados stays null and the caller's iteration will fail fast via the existing fatal-error catch in Crupier.run() instead of hanging indefinitely.");
                        break;
                    } else {
                        synchronized (this.getReceived_commands()) {

                            try {
                                this.received_commands.wait(WAIT_QUEUES);
                            } catch (InterruptedException ex) {
                                Helpers.logCooperativeCancellation(LOGGER, "received commands wait", ex);
                                break;
                            }
                        }
                    }
                }

            // Guard de salida (ver nota en recibirPosiciones). Si la
            // transmisión muere el cliente abandona el wait de SEATS
            // y permutados queda null — el caller debe ser robusto a eso
            // o el flujo terminar por isFin_de_la_transmision al mirar
            // el retorno.
            } while (!ok && !isFin_de_la_transmision());

        }

        return permutados;
    }

    public double getCiega_grande() {
        return ciega_grande;
    }

    public double getCiega_pequeña() {
        return ciega_pequeña;
    }

    // Pre-decode en vuelo de los GIFs de giro (una Future por carta, ver
    // prefetchAnimacionDestaparCarta). Acotado por diseño: las claves son las
    // instancias fijas de las comunitarias del GameFrame, cada recogida vacía
    // su entrada y cada prefetch reemplaza la anterior de esa carta.
    private final ConcurrentHashMap<Card, Future<?>> flip_anim_prefetch = new ConcurrentHashMap<>();

    // Resultado del pre-decode del GIF de giro de una carta: el anim con sus
    // dimensiones de pintado y las condiciones bajo las que se decodificó
    // (carta y zoom), para descartarlo si cambian entre el prefetch y el
    // destape (p.ej. el re-reparto de CARA-B reutiliza las mismas instancias
    // de Card con otro valor).
    private static final class FlipAnim {

        private final PreRenderedGif anim;
        private final int display_w;
        private final int display_h;
        private final float zoom_factor;
        private final String card;

        private FlipAnim(PreRenderedGif anim, int display_w, int display_h, float zoom_factor, String card) {
            this.anim = anim;
            this.display_w = display_w;
            this.display_h = display_h;
            this.zoom_factor = zoom_factor;
            this.card = card;
        }
    }

    // URL del GIF de giro de una carta (fichero del mod o recurso integrado),
    // o null si la baraja actual no trae GIF para esa carta.
    private URL cardFlipGifUrl(Card carta) {

        String baraja = GameFrame.BARAJA;
        boolean baraja_mod = (boolean) ((Object[]) BARAJAS.get(GameFrame.BARAJA))[1];

        try {
            if (baraja_mod) {
                String mod_gif = Helpers.getCurrentJarParentPath() + "/mod/decks/" + baraja + "/gif/"
                        + carta.getValor() + "_" + carta.getPalo() + ".gif";

                return Files.exists(Paths.get(mod_gif)) ? Paths.get(mod_gif).toUri().toURL() : null;
            }

            return getClass().getResource(
                    "/images/decks/" + baraja + "/gif/" + carta.getValor() + "_" + carta.getPalo() + ".gif");

        } catch (MalformedURLException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            return null;
        }
    }

    // Pre-decodifica el GIF de giro de una carta al zoom actual para el motor
    // catch-up. Devuelve null si el GIF no se deja pre-decodificar: el
    // llamante cae a la ruta legacy con el animador Toolkit, que sigue
    // intacta.
    private FlipAnim decodeCardFlipAnim(URL url_icon, Card carta) {

        float zoom_factor = (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP);

        try {
            PreRenderedGif anim;
            int display_w;
            int display_h;

            if (GameFrame.ZOOM_LEVEL != GameFrame.DEFAULT_ZOOM_LEVEL) {

                String gifsicle_path = Helpers.genGifsicleCardAnimationPath(url_icon, zoom_factor,
                        GameFrame.BARAJA + "_" + carta.getValor() + "_" + carta.getPalo());

                if (gifsicle_path != null) {
                    anim = PreRenderedGif.decode(Paths.get(gifsicle_path).toUri().toURL());
                    display_w = anim.getWidth();
                    display_h = anim.getHeight();
                } else {
                    // Sin caché gifsicle todavía: frames a tamaño base estirados
                    // al zoom en el paint (GifLabel pinta a bounds con bilinear).
                    anim = PreRenderedGif.decode(url_icon);
                    display_w = Math.round(anim.getWidth() * zoom_factor);
                    display_h = Math.round(anim.getHeight() * zoom_factor);
                }
            } else {
                anim = PreRenderedGif.decode(url_icon);
                display_w = anim.getWidth();
                display_h = anim.getHeight();
            }

            return new FlipAnim(anim, display_w, display_h, zoom_factor, carta.toShortString());

        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Card flip GIF pre-decode failed (legacy Toolkit animation fallback)", ex);
            return null;
        }
    }

    // Lanza en background el pre-decode del GIF de giro de una carta. Lo usa
    // destaparFlop con la segunda y la tercera carta: sus destapes encadenan
    // sin pausa larga (solo la primera la tiene, y ahí el decode se absorbe
    // vía lapsed), así que sin esto cada una pagaba su decode en crudo entre
    // flip y flip y la cadencia del flop variaba de mano en mano.
    private void prefetchAnimacionDestaparCarta(Card carta) {

        if (GameFrame.repartoAnimOn()) {

            URL url_icon = cardFlipGifUrl(carta);

            if (url_icon != null) {
                flip_anim_prefetch.put(carta, Helpers.futureRun(() -> decodeCardFlipAnim(url_icon, carta)));
            }
        }
    }

    // Recoge el pre-decode lanzado por prefetchAnimacionDestaparCarta. Espera
    // si aún está en curso (siempre mucho menos que la animación del flip
    // anterior) y lo descarta si la carta o el zoom cambiaron entre medias.
    private FlipAnim takePrefetchedFlipAnim(Card carta) {

        Future<?> prefetched = flip_anim_prefetch.remove(carta);

        if (prefetched == null) {
            return null;
        }

        try {
            FlipAnim decoded = (FlipAnim) prefetched.get();

            if (decoded != null && decoded.card.equals(carta.toShortString())
                    && decoded.zoom_factor == (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)) {
                return decoded;
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Prefetched card flip GIF unavailable (decoding inline)", ex);
        }

        return null;
    }

    // Destape sin animación de un rival, espejo del camino animado: las dos
    // hole cards aparecen de golpe A LA VEZ con UN solo uncover.wav (la
    // izquierda con audio, la derecha sin), en vez de girar. Sin pausa
    // intermedia — ambas se voltean en el mismo instante para aligerar el
    // showdown, igual criterio que el giro animado. Mismo lock y re-check
    // anti-duplicados que el camino animado (reentrante si se llega desde sus
    // fallbacks internos).
    private void destaparCartasJugadorSeco(RemotePlayer jugador) {

        synchronized (jugador.getDestape_animado_lock()) {

            Card c1 = jugador.getHoleCard1();
            Card c2 = jugador.getHoleCard2();

            if (!c1.isTapada() || !c2.isTapada()) {
                return;
            }

            jugador.prepararDestapeAnimado();

            c1.destapar();
            c2.destapar(false);
        }
    }

    // Destape ANIMADO de las dos hole cards de un rival, con el mismo gate y
    // el mismo motor que las comunitarias (repartoAnimOn + GIF de giro por
    // carta, pre-decodificado catch-up, relevos sin hueco). Las dos cartas
    // giran A LA VEZ (overlays en paralelo, un solo uncover.wav) sobre
    // overlays efímeros, y el método BLOQUEA hasta que el giro termina (nunca
    // llamar desde el EDT): así el llamante no actualiza las etiquetas de
    // gana/pierde/jugada hasta que el destape se ha visto entero. El orden
    // ENTRE jugadores lo marca el llamante, un rival tras otro.
    //
    // Escalera de fallbacks:
    //   1) animaciones ON + GIF por carta → giro animado (uncover por carta).
    //   2) rival destapable pero sin animación posible (animaciones OFF,
    //      baraja sin GIFs, decode fallido, valores cambiados) →
    //      destaparCartasJugadorSeco: las dos a la vez con un solo uncover.wav,
    //      como las comunitarias sin animaciones.
    //   3) resto (LocalPlayer — sus cartas nunca están tapadas en su propia
    //      pantalla —, cartas ya destapadas, sin valor) → destape clásico
    //      destaparCartas(sound); el flag sound aplica SOLO aquí.
    public void mostrarAnimacionDestaparCartasJugador(Player jugador, boolean sound) {

        Card c1 = jugador.getHoleCard1();
        Card c2 = jugador.getHoleCard2();

        boolean destapable = jugador instanceof RemotePlayer
                && c1.isIniciadaConValor() && c1.isTapada()
                && c2.isIniciadaConValor() && c2.isTapada();

        if (!destapable) {
            jugador.destaparCartas(sound);
            return;
        }

        RemotePlayer rp = (RemotePlayer) jugador;

        URL url1 = GameFrame.repartoAnimOn() ? cardFlipGifUrl(c1) : null;
        URL url2 = GameFrame.repartoAnimOn() ? cardFlipGifUrl(c2) : null;

        if (url1 == null || url2 == null) {
            destaparCartasJugadorSeco(rp);
            return;
        }

        synchronized (rp.getDestape_animado_lock()) {

            // Re-chequeo bajo el lock: un destape concurrente del mismo
            // jugador (SHOWCARDS duplicado/echo) pudo ganarnos mientras
            // esperábamos. Cartas ya boca arriba = nada que hacer.
            if (!c1.isTapada() || !c2.isTapada()) {
                return;
            }

            try {
                // Los dos GIFs en paralelo: el de la segunda carta se decodifica
                // en background mientras el de la primera se decodifica inline.
                final URL furl2 = url2;
                final Card fc2 = c2;

                Future<?> decode2 = Helpers.futureRun(() -> decodeCardFlipAnim(furl2, fc2));

                FlipAnim anim1 = decodeCardFlipAnim(url1, c1);

                FlipAnim anim2 = null;

                try {
                    anim2 = (FlipAnim) decode2.get();
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Card flip GIF pre-decode failed (classic uncover fallback)", ex);
                }

                if (anim1 == null || anim2 == null) {
                    destaparCartasJugadorSeco(rp);
                    return;
                }

                // Cinturón y tirantes: las pockets llegan desordenadas y
                // ordenarCartas PERMUTA los valores entre los dos componentes
                // Card. Todos los flujos ordenan ANTES de destapar en el mismo
                // hilo, pero si algún valor cambiase entre el decode y el giro
                // mejor el destape seco que animar un GIF que no casa con la
                // carta que aterriza debajo.
                if (!anim1.card.equals(c1.toShortString()) || !anim2.card.equals(c2.toShortString())) {
                    LOGGER.log(Level.WARNING, "Card values changed between flip GIF decode and playback (plain uncover fallback)");
                    destaparCartasJugadorSeco(rp);
                    return;
                }

                rp.prepararDestapeAnimado();

                // Las DOS hole cards del jugador giran A LA VEZ en una sola
                // llamada (playCardFlipOverlays ya anima varias cartas en
                // paralelo, cada overlay centrado sobre la suya) con UN único
                // uncover.wav para las dos, para aligerar el showdown. El orden
                // ENTRE jugadores no cambia: el llamante sigue invocando este
                // método un rival tras otro. delay_end=0 para que lo que siga
                // (la jugada en etiqueta neutra del showdown) entre JUSTO al
                // terminar el giro — destaparSync deja la estática debajo de
                // cada carta y los overlays se retiran sin parpadeo.
                // El overlay gira a proporción natural (display_w x display_h),
                // como las comunitarias en showCentralFrames: en vista compacta
                // achatar solo la altura (la anchura seguía completa) estiraba el
                // frame a una caja ancha-y-baja y la carta salía deformada. La
                // estática de debajo sí va achatada, pero destaparSync la coloca
                // bajo el último frame, así que el cambio de tamaño se hace sin
                // que se pinte ningún hueco — igual que en las comunitarias.
                GameFrame.getInstance().getTapete().playCardFlipOverlays(
                        new Card[]{c1, c2},
                        new PreRenderedGif[]{anim1.anim, anim2.anim},
                        new int[]{anim1.display_w, anim2.display_w},
                        new int[]{anim1.display_h, anim2.display_h},
                        0,
                        "misc/uncover.wav");

            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            } finally {
                // Pase lo que pase con la animación, el destape lógico es
                // obligatorio (no-op si destaparSync ya volteó las cartas).
                jugador.destaparCartas(false);
            }
        }
    }

    public void mostrarAnimacionDestaparCartaComunitaria(Card carta) {

        // Jugador local fuera de la timba (o transmisión ya terminada): nadie
        // mira la mesa y el hilo de salida puede estar esperando a que este
        // destape (y el resto del showdown, bajo lock_contabilidad) termine.
        // Volteo lógico seco — calcularJugadas necesita la carta destapada —
        // sin GIF, sin pausa y sin checkPause.
        if (GameFrame.getInstance().getLocalPlayer().isExit() || isFin_de_la_transmision()) {
            carta.destapar(false);
            return;
        }

        URL url_icon = GameFrame.repartoAnimOn() ? cardFlipGifUrl(carta) : null;

        if (url_icon != null) {

            long start = System.currentTimeMillis();

            try {
                // Motor pre-decodificado con catch-up: los frames se decodifican AQUÍ
                // (el coste lo absorbe la pausa de abajo vía lapsed; para la 2ª y 3ª
                // del flop viene ya hecho del prefetch) y la reproducción elige el
                // frame por tiempo transcurrido, así la animación dura siempre
                // lo nominal aunque el timer de Windows vaya grueso. Si el GIF no se
                // deja pre-decodificar, la ruta legacy (animador Toolkit) sigue intacta.
                FlipAnim decoded = takePrefetchedFlipAnim(carta);

                if (decoded == null) {
                    decoded = decodeCardFlipAnim(url_icon, carta);
                }

                final PreRenderedGif anim = (decoded != null) ? decoded.anim : null;

                if (anim != null && !PRE_RENDERED_ENGINE_LOGGED) {
                    PRE_RENDERED_ENGINE_LOGGED = true;
                    LOGGER.log(Level.INFO, "Card flip animations: pre-rendered catch-up engine active ({0} frames / {1} ms)",
                            new Object[]{anim.getFrameCount(), anim.getTotalMs()});
                }

                int display_w = (anim != null) ? decoded.display_w : 0;
                int display_h = (anim != null) ? decoded.display_h : 0;

                ImageIcon icon = null;

                if (anim == null) {

                    icon = new ImageIcon(url_icon);

                    if (GameFrame.ZOOM_LEVEL != GameFrame.DEFAULT_ZOOM_LEVEL) {

                        float zoom_factor = (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP);

                        ImageIcon icon_gifsicle = Helpers.genGifsicleCardAnimation(url_icon, zoom_factor,
                                GameFrame.BARAJA + "_" + carta.getValor() + "_" + carta.getPalo());

                        if (icon_gifsicle != null) {
                            icon = icon_gifsicle;
                        } else {
                            int w = icon.getIconWidth();
                            int h = icon.getIconHeight();
                            icon = new ImageIcon(icon.getImage().getScaledInstance(
                                    Math.round(w * zoom_factor),
                                    Math.round(h * zoom_factor),
                                    Image.SCALE_DEFAULT));
                        }
                    }

                    display_w = icon.getIconWidth();
                    display_h = icon.getIconHeight();
                }

                long lapsed = System.currentTimeMillis() - start;

                Helpers.pausar(
                        (carta == GameFrame.getInstance().getFlop2() || carta == GameFrame.getInstance().getFlop3()) ? 0
                        : (this.destapar_resistencia ? PAUSA_DESTAPAR_CARTA_ALLIN - lapsed
                                : PAUSA_DESTAPAR_CARTA - lapsed));

                final ImageIcon ficon = icon;
                final int fdw = display_w;
                final int fdh = display_h;

                Helpers.GUIRunAndWait(() -> {
                    int x = (int) ((int) ((carta.getLocationOnScreen().getX() + Math.round(carta.getWidth() / 2))
                            - Math.round(fdw / 2))
                            - GameFrame.getInstance().getTapete().getLocationOnScreen().getX());

                    int y = (int) ((int) ((carta.getLocationOnScreen().getY() + Math.round(carta.getHeight() / 2))
                            - Math.round(fdh / 2))
                            - GameFrame.getInstance().getTapete().getLocationOnScreen().getY());

                    GameFrame.getInstance().getTapete().getCentral_label().setLocation(x, y);

                    if (anim == null) {
                        // Ruta legacy: la tapada se oculta aquí, en un evento EDT
                        // anterior al que muestra el GIF (puede llegar a pintarse un
                        // frame con el hueco vacío, como siempre). La ruta
                        // pre-decodificada lo hace atómico vía on_show.
                        carta.setVisibleCard(false);
                    }
                });

                if (anim != null) {
                    // on_show oculta la tapada en el MISMO evento EDT que muestra el
                    // primer frame (relevo carta→GIF en un solo paint) y before_hide
                    // destapa la estática DEBAJO del último frame antes de ocultar el
                    // GIF (relevo GIF→carta sin pintar nunca el hueco vacío). Sin los
                    // hooks, ambos relevos pasaban por un estado intermedio sin carta
                    // que a veces llegaba a pintarse: parpadeo sutil de duración
                    // variable.
                    GameFrame.getInstance().getTapete().showCentralFrames(anim, fdw, fdh, CARD_ANIMATION_DELAY,
                            "misc/uncover.wav",
                            () -> carta.setVisibleCard(false),
                            () -> carta.destaparSync());
                } else {
                    GameFrame.getInstance().getTapete().showCentralImage(ficon, 0, CARD_ANIMATION_DELAY, false,
                            "misc/uncover.wav", 1, -1);
                }

            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            } finally {
                // Even if the animation crashes, we MUST logically flip the card
                // otherwise Hand evaluation crashes later. (En el camino feliz
                // pre-decodificado es un no-op: destaparSync ya volteó la carta
                // bajo el último frame del GIF.)
                carta.destapar(false);
            }

        } else {
            Helpers.pausar(this.destapar_resistencia ? PAUSA_DESTAPAR_CARTA_ALLIN : PAUSA_DESTAPAR_CARTA);
            carta.destapar();
        }

        carta.checkSpecialCardSound();
        GameFrame.getInstance().checkPause();
    }

    public void destaparFlop(ArrayList<Player> resisten) {

        // La 2ª y la 3ª carta encadenan sin pausa larga: su GIF de giro se
        // pre-decodifica en background mientras la primera paga su pausa y su
        // animación, para que la cadencia del flop no dependa del decode.
        prefetchAnimacionDestaparCarta(GameFrame.getInstance().getFlop2());
        prefetchAnimacionDestaparCarta(GameFrame.getInstance().getFlop3());

        mostrarAnimacionDestaparCartaComunitaria(GameFrame.getInstance().getFlop1());
        mostrarAnimacionDestaparCartaComunitaria(GameFrame.getInstance().getFlop2());
        mostrarAnimacionDestaparCartaComunitaria(GameFrame.getInstance().getFlop3());

        if (GameFrame.getInstance().isPartida_local()) {
            Bot.BOT_COMMUNITY_CARDS.addCard(Bot.coronaIntegerCard2LokiCard(GameFrame.getInstance().getFlop1().getCartaComoEntero()));
            Bot.BOT_COMMUNITY_CARDS.addCard(Bot.coronaIntegerCard2LokiCard(GameFrame.getInstance().getFlop2().getCartaComoEntero()));
            Bot.BOT_COMMUNITY_CARDS.addCard(Bot.coronaIntegerCard2LokiCard(GameFrame.getInstance().getFlop3().getCartaComoEntero()));
        }

        ArrayList<Card> flop = new ArrayList<>();
        flop.add(GameFrame.getInstance().getFlop1());
        flop.add(GameFrame.getInstance().getFlop2());
        flop.add(GameFrame.getInstance().getFlop3());

        GameFrame.getInstance().getRegistro().print("FLOP -> " + Card.collection2String(flop));

        checkJugadasParciales(resisten);
    }

    public void destaparTurn(ArrayList<Player> resisten) {

        mostrarAnimacionDestaparCartaComunitaria(GameFrame.getInstance().getTurn());

        if (GameFrame.getInstance().isPartida_local()) {
            Bot.BOT_COMMUNITY_CARDS.addCard(Bot.coronaIntegerCard2LokiCard(GameFrame.getInstance().getTurn().getCartaComoEntero()));
        }

        ArrayList<Card> com = new ArrayList<>();
        com.add(GameFrame.getInstance().getFlop1());
        com.add(GameFrame.getInstance().getFlop2());
        com.add(GameFrame.getInstance().getFlop3());
        com.add(GameFrame.getInstance().getTurn());

        GameFrame.getInstance().getRegistro().print("TURN -> " + Card.collection2String(com));

        checkJugadasParciales(resisten);
    }

    public void destaparRiver(ArrayList<Player> resisten) {

        mostrarAnimacionDestaparCartaComunitaria(GameFrame.getInstance().getRiver());

        if (GameFrame.getInstance().isPartida_local()) {
            Bot.BOT_COMMUNITY_CARDS.addCard(Bot.coronaIntegerCard2LokiCard(GameFrame.getInstance().getRiver().getCartaComoEntero()));
        }

        ArrayList<Card> com = new ArrayList<>();
        com.add(GameFrame.getInstance().getFlop1());
        com.add(GameFrame.getInstance().getFlop2());
        com.add(GameFrame.getInstance().getFlop3());
        com.add(GameFrame.getInstance().getTurn());
        com.add(GameFrame.getInstance().getRiver());

        GameFrame.getInstance().getRegistro().print("RIVER -> " + Card.collection2String(com));
    }

    // PHASE A.1 (showdown zero-trust): comportamiento del cliente al showdown.
    //
    // Crypto-ring clients reciben SHOWCARDS#nick#key del host por cada peer al
    // showdown — el handler async de WaitingRoomFrame:SHOWCARDS llama
    // showPlayerCards que aplica la clave a single_locked_pocket_cards[nick]
    // y verifica resolveCardIndex (lockdown si resolveCardIndex==-1).
    //
    // ESPECTADORES (isCalentando / isSpectator) NO están en el crypto-ring y
    // NO tienen single_locked_pocket_cards. Para ellos el host emite también
    // POTCARDS plaintext, que solo se envía tras haber SRA-verificado a todos
    // los peers (si alguien mintió, lockdown disparó y POTCARDS nunca sale).
    //
    // Esta función:
    //   (a) responde a REQ_SHOWDOWN_KEY enviando local_sra_unlock.
    //   (b) procesa POTCARDS (plaintext fallback para espectadores y para
    //       crypto-ring clients cuyas cartas SHOWCARDS aún están tapadas).
    //   (c) bloquea hasta que se cumpla la condición de exit según rol:
    //         - calentando: POTCARDS ha llegado (única forma de ver cartas).
    //         - crypto-ring: todos los peers no-self tienen HoleCard destapada
    //           (vía SHOWCARDS) o se aplicó plaintext POTCARDS como fallback.
    private void recibirCartasResistencia(ArrayList<Player> resistencia) {
        long start_time = System.currentTimeMillis();
        String localNick = GameFrame.getInstance().getNick_local();
        boolean iAmCalentando = GameFrame.getInstance().getLocalPlayer().isCalentando()
                || GameFrame.getInstance().getLocalPlayer().isSpectator();
        boolean potcardsApplied = false;

        do {
            synchronized (this.getReceived_commands()) {
                ArrayList<String> rejected = new ArrayList<>();
                while (!this.getReceived_commands().isEmpty()) {
                    String comando = this.received_commands.poll();
                    String[] partes = comando.split("#", -1);

                    if (partes.length >= 3) {
                        switch (partes[2]) {
                            case "REQ_SHOWDOWN_KEY":
                                // ZERO-TRUST confidencialidad: solo revelo mi k_pocket si SOY CONTENDIENTE
                                // del showdown (estoy en `resistencia`, computada LOCALMENTE). Un jugador
                                // RETIRADO entra aquí solo para MIRAR el reveal; si respondiera, un host
                                // malicioso podría pedirle la clave (REQ_SHOWDOWN_KEY no solicitado) y
                                // desenmascarar sus cartas MUCKED. Verificado contra MI estado, no el del host.
                                if (!resistencia.contains(GameFrame.getInstance().getLocalPlayer())) {
                                    LOGGER.log(Level.WARNING,
                                            "Ignoring REQ_SHOWDOWN_KEY: local player is not a showdown contender (folded/watcher) — not revealing pocket key");
                                    break;
                                }
                                // PHASE A.1: respondemos con nuestra pocket-unlock + sig Ed25519.
                                // La sig demuestra al host (y al resto via rebroadcast) que la
                                // clave fue autorizada por NUESTRA privkey — el host no la
                                // puede substituir. Asíncrono para no bloquear el polling.
                                Helpers.threadRun(() -> {
                                    try {
                                        byte[] myKey = this.local_sra_unlock;
                                        if (myKey == null) {
                                            Participant me = GameFrame.getInstance().getParticipantes().get(localNick);
                                            if (me != null) {
                                                myKey = me.getSra_unlock();
                                            }
                                        }
                                        String myNickB64 = Base64.getEncoder().encodeToString(localNick.getBytes("UTF-8"));
                                        String keyB64 = (myKey != null && myKey.length == 32)
                                                ? Base64.getEncoder().encodeToString(myKey)
                                                : "*";
                                        String sigB64 = signShowdownRevealForBroadcast(localNick, keyB64);
                                        sendGAMECommandToServer("RESP_SHOWDOWN_KEY#" + myNickB64 + "#" + keyB64 + "#" + sigB64, false);
                                    } catch (Exception e) {
                                        LOGGER.log(Level.SEVERE, "Error responding to REQ_SHOWDOWN_KEY", e);
                                    }
                                });
                                break;

                            case "POTCARDS":
                                // Showdown atómico. Formato per-entry (5 fields):
                                //   #nickB64#c1#c2#sraKeyB64#sigB64
                                //
                                // Espectadores/calentando: aplican plaintext (c1/c2) sin
                                // verificar — no tienen single_locked_pocket_cards. Si
                                // un crypto-ring detecta cheating dispara lockdown y
                                // todos paran.
                                //
                                // Crypto-ring: verifica sig Ed25519 (lockdown si fail →
                                // host hostile), luego descifra single_locked_pocket_cards
                                // con la sraKey y compara con plaintext. Si match: aplica.
                                // Si mismatch: ese peer FORFEIT (sus cartas no se aplican
                                // → showdown las dejará tapadas).
                                {
                                    int total = (partes.length - 3) / 5;
                                    boolean lockdownTriggered = false;
                                    for (int i = 0; i < total && !lockdownTriggered; i++) {
                                        try {
                                            int base = 3 + 5 * i;
                                            String nick = new String(Base64.getDecoder().decode(partes[base]), "UTF-8");
                                            Player jugador = nick2player.get(nick);
                                            if (jugador == null
                                                    || jugador.getNickname().equals(localNick)
                                                    || jugador.isExit()) {
                                                continue;
                                            }
                                            String c1_str = partes[base + 1];
                                            String c2_str = partes[base + 2];
                                            String sraKeyB64 = partes[base + 3];
                                            String sigB64 = partes[base + 4];
                                            if (c1_str == null || c1_str.length() < 3 || !c1_str.contains("_")
                                                    || c2_str == null || c2_str.length() < 3 || !c2_str.contains("_")) {
                                                continue;
                                            }

                                            if (iAmCalentando) {
                                                // Espectador: aplica plaintext directo.
                                                String[] carta1 = c1_str.split("_");
                                                String[] carta2 = c2_str.split("_");
                                                jugador.getHoleCard1().actualizarValorPalo(carta1[0], carta1[1]);
                                                jugador.getHoleCard2().actualizarValorPalo(carta2[0], carta2[1]);
                                                continue;
                                            }

                                            // Mapping zero-trust de violaciones:
                                            //   sig/key missing, bad lengths, sig verify FAILED,
                                            //   Set mismatch (cartas distintas) → 🔴 LOCKDOWN (terminamos).
                                            //   sig OK pero SRA no resuelve → 🟢 FORFEIT del peer + popup.
                                            //   Orden swap (mismas cartas) → silencioso (bug benigno).
                                            boolean keyProvided = (sraKeyB64 != null && !sraKeyB64.equals("*"));
                                            boolean sigProvided = (sigB64 != null && !sigB64.equals("*"));
                                            if (!keyProvided || !sigProvided) {
                                                LOGGER.log(Level.SEVERE,
                                                        "ZERO-TRUST: POTCARDS for {0} arrived WITHOUT sig/key — host stripped. Lockdown.",
                                                        nick);
                                                triggerSecurityLockdown(Translator.translate("zero_trust.host_showdown_sig_missing"));
                                                lockdownTriggered = true;
                                                break;
                                            }

                                            byte[] sraKey = Base64.getDecoder().decode(sraKeyB64);
                                            byte[] sig = Base64.getDecoder().decode(sigB64);
                                            if (sraKey.length != 32 || sig.length != 64) {
                                                LOGGER.log(Level.SEVERE,
                                                        "ZERO-TRUST: POTCARDS for {0} has bad lengths — malformed host wire. Lockdown.",
                                                        nick);
                                                triggerSecurityLockdown(Translator.translate("zero_trust.host_showdown_sig_missing"));
                                                lockdownTriggered = true;
                                                break;
                                            }
                                            byte[] signerPubkey = resolveShowdownSignerPubkey(nick);
                                            if (signerPubkey == null || this.current_hand_id == null) {
                                                LOGGER.log(Level.WARNING,
                                                        "POTCARDS for {0}: signer pubkey or hand_id not resolved yet — entry skipped (no lockdown, possible TOFU race)",
                                                        nick);
                                                continue;
                                            }
                                            if (!IdentityManager.verifyShowdownReveal(signerPubkey, this.current_hand_id, nick, sraKey, sig)) {
                                                LOGGER.log(Level.SEVERE,
                                                        "ZERO-TRUST: POTCARDS sig verify FAILED for {0} — host substituting key. Lockdown.",
                                                        nick);
                                                triggerSecurityLockdown(Translator.translate("zero_trust.host_showdown_sig_invalid"));
                                                lockdownTriggered = true;
                                                break;
                                            }

                                            // Sig OK. Descifrar y poblar holeCards desde el SRA.
                                            // El SRA-decrypt es la ÚNICA fuente de verdad para peers
                                            // con cipher local (activos). El plaintext se compara como
                                            // Set (no tupla) para tolerar reordenamiento UI del host.
                                            Participant pp = GameFrame.getInstance().getParticipantes().get(nick);
                                            if (pp != null) {
                                                pp.setSra_unlock(sraKey);
                                            }
                                            byte[] pocketCards = this.single_locked_pocket_cards.get(nick);
                                            if (pocketCards == null || pocketCards.length != 64) {
                                                // Espectador (calentando, sin cipher local) → confiamos
                                                // en el plaintext firmado del host.
                                                String[] carta1 = c1_str.split("_");
                                                String[] carta2 = c2_str.split("_");
                                                jugador.getHoleCard1().actualizarValorPalo(carta1[0], carta1[1]);
                                                jugador.getHoleCard2().actualizarValorPalo(carta2[0], carta2[1]);
                                                continue;
                                            }
                                            byte[] unlocked = RistrettoSRA.applyCommutativeLock(pocketCards, sraKey);
                                            byte[] cb1 = Arrays.copyOfRange(unlocked, 0, 32);
                                            byte[] cb2 = Arrays.copyOfRange(unlocked, 32, 64);
                                            int id1 = RistrettoSRA.resolveCardIndex(cb1);
                                            int id2 = RistrettoSRA.resolveCardIndex(cb2);
                                            if (id1 < 0 || id2 < 0) {
                                                // Politica §8 + la intencion documentada arriba ("FORFEIT del peer"):
                                                // sig OK pero la SRA no resuelve = anomalia aislada a UN peer (firmo una
                                                // clave mala: bug/cliente corrupto, no distinguible de malicia). NO
                                                // aplicamos sus cartas (continue -> el showdown las deja tapadas =
                                                // forfeit) y avisamos, en vez de terminar la partida de TODOS. (El
                                                // set-mismatch de abajo, host MINTIENDO sobre las cartas, si es lockdown.)
                                                LOGGER.log(Level.SEVERE,
                                                        "ZERO-TRUST: POTCARDS for {0} — sig OK but SRA does not resolve (ids={1},{2}). Malicious peer or bug -> FORFEIT (cards not applied) + warning.",
                                                        new Object[]{nick, id1, id2});
                                                try {
                                                    GameFrame.getInstance().getRegistro().print(
                                                            nick + " " + Translator.translate("zero_trust.peer_sra_corrupt_registro"));
                                                } catch (Exception ignored) {
                                                }
                                                // Visibilidad §8 con SOSPECHOSO correcto: en el HOST nombra al
                                                // PEER (rojo + popup); en un cliente el host relaya la clave mala.
                                                if (GameFrame.getInstance().isPartida_local()) {
                                                    warnMaliciousPeer(nick, "zero_trust.peer_sra_corrupt");
                                                } else {
                                                    warnSuspiciousHost(Translator.translate("zero_trust.peer_sra_corrupt"));
                                                }
                                                continue;
                                            }

                                            // Validar plaintext vs SRA-decrypt como SET (no tupla):
                                            // pockets son intercambiables en Hold'em; el host puede
                                            // legítimamente reordenarlas para visualización. Pero si
                                            // las CARTAS son DISTINTAS → cheat real → LOCKDOWN.
                                            String expected1 = Card.shortStringFromIndex(id1);
                                            String expected2 = Card.shortStringFromIndex(id2);
                                            java.util.Set<String> received = new java.util.HashSet<>(
                                                    java.util.Arrays.asList(c1_str, c2_str));
                                            java.util.Set<String> expected = new java.util.HashSet<>(
                                                    java.util.Arrays.asList(expected1, expected2));
                                            if (!received.equals(expected)) {
                                                LOGGER.log(Level.SEVERE,
                                                        "ZERO-TRUST: POTCARDS plaintext for {0} ({1},{2}) disagrees with SRA-decrypt ({3},{4}). Host is lying. Lockdown.",
                                                        new Object[]{nick, c1_str, c2_str, expected1, expected2});
                                                triggerSecurityLockdown(Translator.translate("zero_trust.host_potcards_mismatch"));
                                                lockdownTriggered = true;
                                                break;
                                            }

                                            jugador.getHoleCard1().iniciarConValorNumerico(id1 + 1);
                                            jugador.getHoleCard2().iniciarConValorNumerico(id2 + 1);
                                            // Mismo reordenamiento que el host (carta mayor a la
                                            // izquierda) para visualización consistente entre peers.
                                            jugador.ordenarCartas();
                                        } catch (Exception ex) {
                                            LOGGER.log(Level.WARNING, "Error processing POTCARDS entry " + i, ex);
                                        }
                                    }
                                }
                                potcardsApplied = true;
                                break;

                            case "MISDEAL":
                                String motivo = "";
                                try {
                                    motivo = new String(Base64.getDecoder().decode(partes[3]), "UTF-8");
                                } catch (Exception e) {
                                }
                                cancelarManoYDevolverApuestas(motivo, false);
                                return;

                            default:
                                rejected.add(comando);
                                break;
                        }
                    } else {
                        rejected.add(comando);
                    }
                }
                if (!rejected.isEmpty()) {
                    this.getReceived_commands().addAll(rejected);
                }
            }

            // Condiciones de exit:
            //   - POTCARDS aplicado: showdown propiamente dicho, ya tenemos
            //     valores de cartas.
            //   - resistencia no contiene a NADIE remoto/no-exit: caso
            //     fold-to-win (resisten=[] o solo localPlayer). No hay nada
            //     que esperar — el server NO emite POTCARDS porque no hay
            //     entries; salimos inmediato igual que el código antiguo
            //     (cuyo for sobre resistencia salía con doneWaiting=true si
            //     la lista no iteraba).
            if (potcardsApplied) {
                break;
            }
            boolean hasRemoteToWaitFor = false;
            for (Player jugador : resistencia) {
                if (!jugador.getNickname().equals(localNick) && !jugador.isExit()) {
                    hasRemoteToWaitFor = true;
                    break;
                }
            }
            if (!hasRemoteToWaitFor) {
                break;
            }

            if (Crupier.SECURITY_LOCKDOWN) {
                break;
            }

            if (GameFrame.getInstance().checkPause()) {
                start_time = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - start_time > GameFrame.CLIENT_RECEPTION_TIMEOUT) {
                LOGGER.log(Level.WARNING, "recibirCartasResistencia timeout — showdown reveals incomplete. UI shows face-down cards; the host resolves the pot from the action log.");
                break;
            } else {
                synchronized (this.getReceived_commands()) {
                    try {
                        this.received_commands.wait(WAIT_QUEUES);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        } while (!isFin_de_la_transmision());
    }

    // Espera (timed-wait robusto a notify perdidos, mismo patrón que el
    // watcher de _cinematicAllin) a que termine la cinemática de all-in en
    // curso en ESTA máquina. TODOS los finales de la cinemática (frames
    // completos, skip por click, sin GIF, CINEMATICAS off, replay de
    // recovery) apagan el flag y notifican LOCK_CINEMATICS; durante el
    // replay de recovery el flag está apagado (espera inerte). Lo usan el
    // turno del bot tras un all-in y los destapes animados (resistencia y
    // showdown), que no deben pisar la animación central.
    private void esperarFinCinematicaAllin() {
        // isExit(): si el jugador local ha salido no hay cinemática que
        // respetar, y esta espera puede estar corriendo bajo lock_contabilidad
        // (showdown) reteniendo a finTransmision — el flag de fin no puede
        // izarse hasta soltar el lock, así que no basta con chequearlo.
        while (Init.PLAYING_CINEMATIC && !isFin_de_la_transmision()
                && !GameFrame.getInstance().getLocalPlayer().isExit()) {
            synchronized (Init.LOCK_CINEMATICS) {
                if (!Init.PLAYING_CINEMATIC || isFin_de_la_transmision()
                        || GameFrame.getInstance().getLocalPlayer().isExit()) {
                    break;
                }
                try {
                    Init.LOCK_CINEMATICS.wait(1000);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    public void procesarCartasResistencia(ArrayList<Player> resisten, boolean destapar) {

        if (destapar) {
            // El destape (animado o seco) de las cartas de la resistencia no
            // debe arrancar con la cinemática del all-in aún en pantalla.
            esperarFinCinematicaAllin();
        }

        if (!this.cartas_resistencia) {

            if (GameFrame.getInstance().isPartida_local()) {

                // --- NUEVO: OPTIMISTIC UI - Pedimos las cartas a los clientes remotos ---
                solicitarYRecibirCartasVisuales(resisten);
                // ------------------------------------------------------------------------

                // Enviamos a cada jugador las cartas de los jugadores que han llegado al final
                // PHASE A.1 (showdown zero-trust): el broadcast plaintext POTCARDS
                // queda eliminado. La distribución de cartas al showdown va por
                // SHOWCARDS#nick#k_pocket_unlock dentro de solicitarYRecibirCartas
                // Visuales (arriba), con verificación criptográfica vía
                // resolveCardIndex en el receptor. Un peer que mienta su pocket
                // key dispara lockdown — no plaintext que aceptar a ciegas.

                if (destapar) {
                    // Sin uncover.wav de batch aquí: cada giro por jugador ya
                    // reproduce su propio uncover (caminos animado y seco lo
                    // suenan SIEMPRE, ignorando el flag sound, igual que en
                    // showdown). Un uncover explícito previo se duplicaba con el
                    // del primer giro y, por la carrera entre su force_close=false
                    // y el force_close=true del giro, unas veces se cortaba a
                    // media muestra y otras se solapaba/repetía con el giro y la
                    // primera comunitaria del run-out.

                    // Destapamos las cartas de los jugadores involucrados,
                    // SECUENCIAL por jugador: cada giro animado bloquea (hilo
                    // del crupier) y el siguiente rival no gira hasta que el
                    // anterior termina, como un dealer real.
                    for (Player jugador : resisten) {

                        if (jugador != GameFrame.getInstance().getLocalPlayer() && !jugador.isExit()) {

                            mostrarAnimacionDestaparCartasJugador(jugador, false);
                        }
                    }
                }

                this.cartas_resistencia = true;

            } else {

                // Recibimos las cartas de los jugadores involucrados en el bote_total
                // (ignoramos las nuestras que ya las sabemos)
                recibirCartasResistencia(resisten);

                if (destapar) {
                    // Sin uncover.wav de batch aquí: cada giro por jugador ya
                    // reproduce su propio uncover (caminos animado y seco lo
                    // suenan SIEMPRE, ignorando el flag sound, igual que en
                    // showdown). Un uncover explícito previo se duplicaba con el
                    // del primer giro y, por la carrera entre su force_close=false
                    // y el force_close=true del giro, unas veces se cortaba a
                    // media muestra y otras se solapaba/repetía con el giro y la
                    // primera comunitaria del run-out.

                    // Destapamos las cartas de los jugadores involucrados,
                    // SECUENCIAL por jugador (ver rama del host).
                    for (Player jugador : resisten) {

                        if (jugador != GameFrame.getInstance().getLocalPlayer() && !jugador.isExit()) {

                            mostrarAnimacionDestaparCartasJugador(jugador, false);
                        }
                    }
                }

                this.cartas_resistencia = true;
            }
        }
    }

    private synchronized void exitSpectatorBots() {

        if (GameFrame.getInstance().isPartida_local()) {

            for (Player jugador : GameFrame.getInstance().getJugadores()) {

                if (jugador != GameFrame.getInstance().getLocalPlayer() && !jugador.isExit() && jugador.isSpectator()
                        && Helpers.doubleSecureCompare(0f, jugador.getStack()) == 0
                        && GameFrame.getInstance().getParticipantes().get(jugador.getNickname()).isCpu()) {

                    this.remotePlayerQuit(jugador.getNickname());

                }
            }
        }
    }

    private synchronized void updateExitPlayers() {
        int exit = 0;
        for (Player jugador : GameFrame.getInstance().getJugadores()) {
            if (jugador.isExit()) {
                this.auditor.put(jugador.getNickname(), new Double[]{jugador.getStack() + jugador.getPagar(), (double) jugador.getBuyin()});
                double ganancia = Helpers.doubleClean(Helpers.doubleClean(jugador.getStack()) + Helpers.doubleClean(jugador.getPagar())) - Helpers.doubleClean(jugador.getBuyin());
                String ganancia_msg = "";
                if (Helpers.doubleSecureCompare(ganancia, 0f) < 0) {
                    ganancia_msg += Translator.translate("ui.pierde_2") + " " + Helpers.money2String(ganancia * -1);
                } else if (Helpers.doubleSecureCompare(ganancia, 0f) > 0) {
                    ganancia_msg += Translator.translate("ui.gana_4") + " " + Helpers.money2String(ganancia);
                } else {
                    ganancia_msg += Translator.translate("ui.ni_gana_ni_pierde");
                }
                GameFrame.getInstance().getRegistro().print(jugador.getNickname() + " " + Translator.translate("game.abandona_la_timba_2") + " -> " + ganancia_msg);
                exit++;
            }
        }

        if (exit > 0) {
            GameFrame.getInstance().downgradeAndRefreshTapete();
            nick2player.clear();
            for (Player jugador : GameFrame.getInstance().getJugadores()) {
                nick2player.put(jugador.getNickname(), jugador);
                // Baja de sala solo para participantes remotos: si el que sale
                // es el propio host (exit a mitad de mano, con finTransmision
                // ya en marcha en paralelo) no hay baja que procesar — su
                // entrada en participantes es un placeholder null por diseño.
                if (jugador.isExit() && GameFrame.getInstance().isPartida_local()
                        && jugador != GameFrame.getInstance().getLocalPlayer()) {
                    GameFrame.getInstance().getSala_espera().borrarParticipante(jugador.getNickname());
                }
            }
        }
    }

    public boolean ganaPorUltimaCarta(Player jugador, Hand jugada, int MIN) {

        if (!GameFrame.getInstance().getRiver().isTapada()
                && jugada.getWinners().contains(GameFrame.getInstance().getRiver()) && jugada.getValue() >= MIN
                && (jugada.getWinners().contains(jugador.getHoleCard1())
                || jugada.getWinners().contains(jugador.getHoleCard2()))) {

            ArrayList<Card> cartas = new ArrayList<>(Arrays.asList(GameFrame.getInstance().getCartas_comunes()));
            cartas.add(jugador.getHoleCard1());
            cartas.add(jugador.getHoleCard2());
            cartas.remove(GameFrame.getInstance().getRiver());

            Hand nueva_jugada = new Hand(cartas);

            return (nueva_jugada.getValue() != jugada.getValue());
        }

        return false;
    }

    public boolean badbeat(Player perdedor, Player ganador) {

        if (ganador != null) {

            ArrayList<Card> cartas = new ArrayList<>(Arrays.asList(GameFrame.getInstance().getCartas_comunes()));

            cartas.add(perdedor.getHoleCard1());

            cartas.add(perdedor.getHoleCard2());

            cartas.remove(GameFrame.getInstance().getRiver());

            Hand jugada_perdedor_turn = new Hand(cartas);

            cartas.remove(perdedor.getHoleCard1());

            cartas.remove(perdedor.getHoleCard2());

            cartas.add(ganador.getHoleCard1());

            cartas.add(ganador.getHoleCard2());

            Hand jugada_ganador_turn = new Hand(cartas);

            return (jugada_perdedor_turn.getValue() >= Hand.TRIO
                    && (jugada_perdedor_turn.getValue() > jugada_ganador_turn.getValue()));
        } else {
            return false;
        }

    }

    public void pausaConBarra(int tiempo) {

        this.setTiempo_pausa(tiempo);

        while (getTiempoPausa() > 0) {

            // El jugador local ha salido de la timba: la pausa es puro tiempo
            // para que los humanos asimilen lo ocurrido y aquí ya no queda
            // humano que mire. Cortarla importa especialmente cuando corre
            // bajo lock_contabilidad (pausa entre caras del run-it-twice):
            // finTransmision necesita ese lock ANTES de poder izar
            // fin_de_la_transmision, así que el flag de fin no puede ser
            // quien la aborte — isExit() sí, porque se pone sin locks.
            if (GameFrame.getInstance().getLocalPlayer().isExit()) {
                break;
            }

            synchronized (lock_pausa_barra) {
                try {
                    lock_pausa_barra.wait(1000);

                    if (!GameFrame.getInstance().isTimba_pausada() && !isFin_de_la_transmision() && !isIwtsthing()) {

                        tiempo_pausa--;

                        // setValue(tiempo_pausa) redundante: el Timer interno de
                        // smoothCountdown (lanzado por setTiempo_pausa) ya repinta
                        // la barra cada 50ms en escala ms. Sin esta nota, el setValue
                        // en escala segundos pisaba la barra (max=tiempo*1000) y
                        // generaba parpadeo entre los dos repaints.
                    }

                } catch (InterruptedException ex) {
                    Helpers.logCooperativeCancellation(LOGGER, "pause progress bar loop", ex);
                    break;
                }
            }
        }

        synchronized (lock_iwtsth) {
            lock_iwtsth.notifyAll();
        }
    }

    public void showdown(HashMap<Player, Hand> perdedores, HashMap<Player, Hand> ganadores, java.util.List<Card> diferir_desenfoque) {
        int pivote;

        // 1. Determinar quién es el primero en enseñar (último agresor o el siguiente al dealer)
        if (this.last_aggressor != null) {
            pivote = 0;
            for (Player jugador : GameFrame.getInstance().getJugadores()) {
                if (jugador == this.last_aggressor) {
                    break;
                }
                pivote++;
            }
        } else {
            int i = 0;
            while (!GameFrame.getInstance().getJugadores().get(i).getNickname().equals(this.dealer_nick)) {
                i++;
            }
            pivote = (i + 1) % GameFrame.getInstance().getJugadores().size();
        }

        // 2. PASADA 1 — destapes: en orden de palabra, cada jugador que debe
        // mostrar gira sus cartas (animado, bloqueante), SIN tocar su action
        // label (la etiqueta azul de jugada queda reservada al botón MOSTRAR
        // voluntario de los foldeados entre manos). Los veredictos
        // GANA/PIERDE + jugada de TODOS se revelan de golpe en la pasada 2,
        // cuando ya no queda nadie por destapar — como en una mesa real,
        // donde no hay ganador hasta que todas las manos están boca arriba.
        // mustShow se decide AQUÍ una sola vez por jugador (first_to_show es
        // estado del recorrido) y la pasada 2 lo reutiliza tal cual:
        // recalcularlo podría divergir.
        // Un all-in en el river llega aquí sin run-out: la cinemática del
        // all-in puede seguir en pantalla y los giros de la pasada 1 no
        // deben pisarla.
        esperarFinCinematicaAllin();

        HashMap<Player, Boolean> must_show = new HashMap<>();

        int pos = pivote;
        boolean first_to_show = true; // Control para obligar a mostrar al primero en hablar
        boolean alguno_destapado = false;

        do {
            Player jugador_actual = GameFrame.getInstance().getJugadores().get(pos);

            if (perdedores.containsKey(jugador_actual) || ganadores.containsKey(jugador_actual)) {

                boolean isWinner = ganadores.containsKey(jugador_actual);
                Hand jugada = isWinner ? ganadores.get(jugador_actual) : perdedores.get(jugador_actual);

                // LÓGICA AUTO-MUCK (IWTSTH):
                // Se destapa la carta SÓLO si:
                // - IWTSTH está desactivado (todos muestran)
                // - Es un All-in (destapar_resistencia = true)
                // - El jugador es uno de los ganadores del bote
                // - Es el primer jugador en actuar en el showdown (pivote)
                boolean mustShow = !GameFrame.IWTSTH_RULE ||
                                   this.destapar_resistencia ||
                                   isWinner ||
                                   first_to_show;

                must_show.put(jugador_actual, mustShow);

                if (mustShow) {
                    // ¿El destape ocurre AHORA? En all-in y run-it-twice las
                    // cartas ya giraron antes del showdown: ni animación, ni
                    // etiqueta neutra, ni pausa — directo al veredicto de la
                    // pasada 2 (comportamiento de siempre).
                    boolean estaba_tapada = jugador_actual.getHoleCard1().isTapada();

                    // Pausa dramática ENTRE destapes, nunca tras el último:
                    // corre antes de este giro solo si ya hubo otro antes
                    // (visualmente cae tras la etiqueta neutra del jugador
                    // anterior). Así los veredictos de la pasada 2 saltan
                    // JUSTO al terminar el último giro, igual que en el
                    // all-in multiway.
                    if (estaba_tapada && alguno_destapado && !GameFrame.TEST_MODE) {
                        Helpers.pausar(PAUSA_ENTRE_DESTAPES_SHOWDOWN);
                    }

                    // Bloquea hasta el fin del giro (hilo del crupier, como las
                    // comunitarias).
                    mostrarAnimacionDestaparCartasJugador(jugador_actual, false);

                    if (estaba_tapada) {
                        // Jugada en etiqueta NEUTRA (gris del label en reposo,
                        // no el azul del botón MOSTRAR): enseña QUÉ lleva sin
                        // adelantar si gana.
                        if (jugador_actual instanceof RemotePlayer) {
                            ((RemotePlayer) jugador_actual).showJugadaNeutral(jugada.getName());
                        }

                        alguno_destapado = true;
                    } else if (jugador_actual == GameFrame.getInstance().getLocalPlayer()) {
                        // El LocalPlayer ya ve sus cartas boca arriba (no hubo giro
                        // que animar), pero su jugada debe pintarse en la etiqueta
                        // NEUTRA durante el destape secuencial igual que al resto
                        // (antes solo aparecía en la pasada 2, con ganadores/perdedores).
                        ((LocalPlayer) jugador_actual).showJugadaNeutral(jugada.getName());
                    }
                }

                // A partir de ahora, los siguientes perdedores podrán ocultar sus cartas
                first_to_show = false;
            }

            pos = (pos + 1) % GameFrame.getInstance().getJugadores().size();

        } while (pos != pivote);

        // 3. PASADA 2 — veredictos: GANA/PIERDE + jugada + sonidos + SQL para
        // todos de golpe, con todas las manos ya boca arriba.
        pos = pivote;

        do {
            Player jugador_actual = GameFrame.getInstance().getJugadores().get(pos);

            if (perdedores.containsKey(jugador_actual) || ganadores.containsKey(jugador_actual)) {

                boolean isLocal = jugador_actual.equals(GameFrame.getInstance().getLocalPlayer());
                boolean isWinner = ganadores.containsKey(jugador_actual);
                Hand jugada = isWinner ? ganadores.get(jugador_actual) : perdedores.get(jugador_actual);
                boolean mustShow = must_show.get(jugador_actual);

                if (isWinner) {
                    jugador_actual.setWinner(jugada.getName());
                    this.sqlNewShowdown(jugador_actual, jugada, true, !mustShow);

                    if (GameFrame.SONIDOS_CHORRA && isLocal) {
                        if (jugador_actual.getDecision() == Player.ALLIN) {
                            Audio.playWavResource("joke/" + GameFrame.LANGUAGE + "/winner/applause.wav");
                        } else {
                            this.soundWinner(jugada.getValue(), ganaPorUltimaCarta(jugador_actual, jugada, Crupier.MIN_ULTIMA_CARTA_JUGADA));
                        }
                    }

                } else {
                    // Actualización de UI para perdedores
                    if (isLocal) {
                        jugador_actual.setLoser(jugada.getName());
                        GameFrame.getInstance().getLocalPlayer().setMuestra(mustShow);

                        // Si el jugador local hizo "muck" (cartas tapadas), habilitamos el botón voluntario
                        if (!mustShow) {
                            GameFrame.getInstance().getLocalPlayer().activar_boton_mostrar(true);
                        }
                    } else {
                        // El destape de la pasada 1 puede ser asíncrono en el
                        // fallback clásico, así que no leemos isTapada() aquí
                        // para decidir la etiqueta: usamos el mustShow que
                        // gobernó el destape. Un remoto que NO mostró conserva
                        // el "PIERDE" genérico; si mostró, se expone la jugada.
                        if (!mustShow) {
                            jugador_actual.setLoser(Translator.translate("ui.pierde_3"));
                        } else {
                            jugador_actual.setLoser(jugada.getName());
                        }
                    }

                    this.sqlNewShowdown(jugador_actual, jugada, false, !mustShow);

                    if (GameFrame.SONIDOS_CHORRA && isLocal) {
                        if (jugador_actual.getDecision() == Player.ALLIN && GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {
                            java.util.Map.Entry<String, String[]> WTF_SOUNDS = new java.util.HashMap.SimpleEntry<>("joke/es/loser/", new String[]{
                                "encargado.wav",
                                "matias.wav"});
                            Audio.playRandomWavResource(java.util.Map.ofEntries(WTF_SOUNDS));
                        } else {
                            this.soundLoser(jugada.getValue());
                        }
                    }
                }
            }

            // Registro de historial para simular "Tilt" en los bots
            if (GameFrame.getInstance().isPartida_local()
                    && jugador_actual != GameFrame.getInstance().getLocalPlayer()
                    && jugador_actual instanceof RemotePlayer) {
                Bot bot = ((RemotePlayer) jugador_actual).getBot();
                if (bot != null) {
                    bot.recordHandResult(ganadores.containsKey(jugador_actual));
                }
            }

            pos = (pos + 1) % GameFrame.getInstance().getJugadores().size();

        } while (pos != pivote);

        // El atenuado (desenfocar) de las cartas que NO entran en la jugada
        // ganadora se aplica AQUÍ, tras la pasada 2, no en el cálculo del
        // settle: si se hiciera antes, una hole card aún TAPADA (un rival que
        // todavía no ha destapado en la pasada 1) mostraría su dorso atenuado
        // (IMAGEN_TRASERA_B) y filtraría que esa carta no cuenta antes de
        // verla. Diferirlo mantiene todas las cartas brillantes durante los
        // destapes secuenciales y solo las atenúa al revelar los veredictos,
        // como en una mesa real. null = el caller atenúa por su cuenta
        // (run-it-twice conserva su flujo propio).
        if (diferir_desenfoque != null) {
            for (Card carta : diferir_desenfoque) {
                carta.desenfocar();
            }
        }
    }

    public void startIWTSTHPlayersBlinking() {

        if (GameFrame.IWTSTH_RULE && isIWTSTH4LocalPlayerAuthorized()) {

            Helpers.GUIRun(() -> {
                for (RemotePlayer rp : GameFrame.getInstance().getTapete().getRemotePlayers()) {
                    if (rp.isActivo() && rp.isLoser() && rp.getHoleCard1().isTapada()) {
                        rp.getIwtsth_blink_timer().start();
                    }
                }
            });

        }
    }

    public Integer sqlUGI2GID(String ugi) {
        synchronized (GameFrame.SQL_LOCK) {

            Integer ret = null;

            String sql = "SELECT id from game WHERE ugi=?";

            try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setQueryTimeout(30);

                statement.setString(1, ugi);

                ResultSet rs = statement.executeQuery();

                rs.next();

                ret = rs.getInt("id");
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

            return ret;

        }

    }

    public Integer getHandIdFromUGI(String ugi) {

        synchronized (GameFrame.SQL_LOCK) {

            Integer ret = null;

            String sql = "SELECT max(hand.id) as hand_id from game,hand WHERE game.ugi=? AND hand.id_game=game.id";

            try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setQueryTimeout(30);

                statement.setString(1, ugi);

                ResultSet rs = statement.executeQuery();

                rs.next();

                ret = rs.getInt("hand_id");
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

            return ret;

        }

    }

    public String getUGI() {
        synchronized (GameFrame.SQL_LOCK) {
            if (GameFrame.isRECOVER()) {
                String ret = null;

                String sql = "SELECT ugi from game WHERE id=?";

                try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                    statement.setQueryTimeout(30);

                    statement.setInt(1, GameFrame.RECOVER_ID);

                    ResultSet rs = statement.executeQuery();

                    rs.next();

                    ret = rs.getString("ugi");
                } catch (SQLException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }

                return ret;
            } else {
                return Helpers.genRandomString(GameFrame.UGI_LENGTH);
            }
        }
    }

    private void resyncRECOVERGLOBALS() {
        // Reglas globales recuperadas (IWTSTH / Rabbit / Run It Twice): se re-aplican
        // con los setters estáticos (set campo + broadcast *RULE a los clientes que
        // reconectan + persistRecoverSettings), solo si difieren del valor por defecto
        // con el que arranca la recuperación. Idempotente y default-agnóstico.
        if (GameFrame.IWTSTH_RULE_RECOVER != null) {
            boolean v = GameFrame.IWTSTH_RULE_RECOVER;
            GameFrame.IWTSTH_RULE_RECOVER = null;
            if (v != GameFrame.IWTSTH_RULE) {
                GameFrame.setIwtsthRule(v);
            }
        }

        if (GameFrame.RABBIT_HUNTING_RECOVER != null) {
            int v = GameFrame.RABBIT_HUNTING_RECOVER;
            GameFrame.RABBIT_HUNTING_RECOVER = null;
            if (v != GameFrame.RABBIT_HUNTING) {
                GameFrame.setRabbitHunting(v);
            }
        }

        if (GameFrame.RUN_IT_TWICE_RECOVER != null) {
            boolean v = GameFrame.RUN_IT_TWICE_RECOVER;
            GameFrame.RUN_IT_TWICE_RECOVER = null;
            if (v != GameFrame.RUN_IT_TWICE) {
                GameFrame.setRunItTwiceRule(v);
            }
        }

        // Notas de voz en recover: mismo patrón default-agnóstico que RIT
        if (GameFrame.VOICE_MESSAGES_RECOVER != null) {
            final boolean recovered_voice = GameFrame.VOICE_MESSAGES_RECOVER;
            GameFrame.VOICE_MESSAGES_RECOVER = null;
            if (recovered_voice != GameFrame.VOICE_MESSAGES) {
                GameFrame.setVoiceMessages(recovered_voice);
            }
        }

        // TTS global en recover: mismo patrón default-agnóstico que las notas de voz
        if (GameFrame.TTS_SERVER_RECOVER != null) {
            final boolean recovered_tts = GameFrame.TTS_SERVER_RECOVER;
            GameFrame.TTS_SERVER_RECOVER = null;
            if (recovered_tts != GameFrame.TTS_SERVER) {
                GameFrame.setTTSGlobal(recovered_tts);
            }
        }
    }

    @Override
    public void run() {
        Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), GameFrame.THINK_TIME);

        if (GameFrame.getInstance().isPartida_local()) {
            GameFrame.UGI = this.getUGI();
            broadcastGAMECommandFromServer("INIT#" + String.valueOf(GameFrame.BUYIN) + "#" + String.valueOf(GameFrame.CIEGA_PEQUEÑA) + "#" + String.valueOf(GameFrame.CIEGA_GRANDE) + "#" + String.valueOf(GameFrame.CIEGAS_DOUBLE) + "@" + String.valueOf(GameFrame.CIEGAS_DOUBLE_TYPE) + "#" + String.valueOf(GameFrame.isRECOVER()) + "@" + GameFrame.UGI + "#" + String.valueOf(GameFrame.REBUY) + "#" + String.valueOf(GameFrame.MANOS) + "#" + String.valueOf(GameFrame.BLIND_CAP) + "#" + String.valueOf(GameFrame.REBUY_LIMIT) + "#" + String.valueOf(GameFrame.BOT_REBUY) + "#" + String.valueOf(GameFrame.FIXED_BUYIN)
                    // Rango de buy-in editable y política de tope de recompra (campos
                    // fijos; van ANTES del campo opcional de estructura).
                    + "#" + String.valueOf(GameFrame.BUYIN_MIN_BB) + "#" + String.valueOf(GameFrame.BUYIN_MAX_BB) + "#" + String.valueOf(GameFrame.REBUY_CAP_POLICY)
                    // Ante y straddle (campos fijos; van ANTES del campo opcional de estructura).
                    + "#" + String.valueOf(GameFrame.ANTE) + "#" + String.valueOf(GameFrame.STRADDLE)
                    // Reglas de juego elegibles al crear la timba (IWTSTH / Run It Twice /
                    // Rabbit Hunting): campos fijos, ANTES del campo opcional de estructura,
                    // para que un cliente que se une conozca las reglas de salida.
                    + "#" + (GameFrame.IWTSTH_RULE ? "1" : "0") + "#" + (GameFrame.RUN_IT_TWICE ? "1" : "0") + "#" + String.valueOf(GameFrame.RABBIT_HUNTING)
                    // Tiempo de pensar (segundos) + si esta activo: campos FIJOS, ANTES del
                    // campo opcional de estructura, para que el cliente arranque con el mismo
                    // tiempo de pensar (o sin limite) que fijo el host al crear/configurar.
                    + "#" + String.valueOf(GameFrame.THINK_TIME) + "#" + (GameFrame.THINK_TIME_ENABLED ? "1" : "0")
                    // Estructura de ciegas personalizada (campo opcional al final): los
                    // clientes recomputan la escalada por su cuenta, así que TODOS deben
                    // caminar la misma lista o desincronizan al subir las ciegas. Solo se
                    // añade cuando hay estructura custom; ausente = escalera por defecto.
                    + (GameFrame.ACTIVE_BLIND_STRUCTURE != null ? "#" + BlindStructure.levelsToString(GameFrame.ACTIVE_BLIND_STRUCTURE) : ""), null);
        }

        if (GameFrame.RECOVER) {
            if (GameFrame.getInstance().isPartida_local()) {
                this.sqlite_id_game = GameFrame.RECOVER_ID;
                GameFrame.persistRecoverSettings(this.sqlite_id_game);
            } else {
                Integer gid = sqlUGI2GID(GameFrame.UGI);
                if (gid == null) {
                    this.sqlNewGame();
                } else {
                    this.sqlite_id_game = gid;
                }
            }
        }

        Helpers.GUIRun(() -> {
            GameFrame.getInstance().getSala_espera().getStatus().setText(Translator.translate("ui.sorteando_sitios"));
        });

        this.nicks_permutados = sortearSitios();
        sentarParticipantes();

        for (Player jugador : GameFrame.getInstance().getJugadores()) {
            nick2player.put(jugador.getNickname(), jugador);
        }

        if (!GameFrame.RECOVER) {
            sqlNewGame();
        }

        Helpers.GUIRunAndWait(() -> {
            GameFrame.getInstance().getSala_espera().setVisible(false);
        });

        Helpers.GUIRun(() -> {
            GameFrame.getInstance().getSala_espera().getStatus().setText(Translator.translate("game.timba_en_curso"));
            GameFrame.getInstance().getSala_espera().getTts_warning().setVisible(true);
            GameFrame.getInstance().getSala_espera().getChat_notifications().setVisible(true);
            GameFrame.getInstance().getSala_espera().getBarra().setVisible(false);
            GameFrame.getInstance().getSala_espera().getStatus().setIcon(null);
            GameFrame.getInstance().getSala_espera().pack();
        });

        Audio.stopLoopMp3("misc/waiting_room.mp3");

        if (!GameFrame.RECOVER && GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {
            Audio.playWavResource("misc/startplay.wav");
        }

        // Desmuteo SIEMPRE el loop de fondo al entrar al juego (la sala de espera lo
        // dejó muteado por contexto). Quién decide si suena es MUSICA_AMBIENTAL vía
        // effectiveLoopVolume (volumen 0 si está off). Si lo dejáramos muteado cuando
        // el flag arranca en off, activarlo a mitad de partida no lo despertaría (el
        // mute por contexto seguiría a 0) hasta salir a la pantalla principal.
        Audio.unmuteLoopMp3("misc/background_music.mp3");

        GameFrame.getInstance().autoZoomFullScreen(GameFrame.AUTO_FULLSCREEN);

        // Modo buy-in variable: cada humano elige su buy-in al entrar al tablero
        // (luces apagadas) antes de la mano 1. No-op en modo fijo y en recover.
        solicitarBuyinsIniciales();

        // Cortinilla de apertura: los stacks suben de 0 a su buy-in a la vez, ANTES
        // de la primera mano (-> antes de postear/volar las ciegas). Barrera dentro:
        // bloquea hasta que esten llenos. No-op si animaciones off / recover.
        animateInitialStacks();

        while (!fin_de_la_transmision) {
            try {
                if ((getJugadoresActivos() + getJugadoresCalentando()) > 1 && !GameFrame.getInstance().getLocalPlayer().isExit()) {
                    if (this.NUEVA_MANO()) {
                        // El reparto de ciegas/dealer ya queda reflejado en la
                        // tabla de balance (iconos de rol junto a cada nick), asi
                        // que la antigua linea "X es la CIEGA GRANDE / ..." sobra.
                        auditorCuentas();

                        ArrayList<Player> resisten = this.rondaApuestas(PREFLOP, new ArrayList<>(GameFrame.getInstance().getJugadores()));

                        GameFrame.getInstance().hideTapeteApuestas();
                        GameFrame.getInstance().getLocalPlayer().desactivarControles();

                        if (GameFrame.AUTO_ACTION_BUTTONS) {
                            // Persist mode keeps the queued pre-press across the hand
                            // boundary (hides the buttons but does not clear pre_pulsado).
                            GameFrame.getInstance().getLocalPlayer().desActivarPreBotones(!GameFrame.AUTO_ACTION_PERSIST);
                        }

                        // The dragon must close after the preflop replay even
                        // when the local queue was empty from the start. That happens to
                        // a CLIENT who reconnects during a recovery where they were not
                        // part of the interrupted hand's crypto-ring (CALENTANDO): the
                        // queue filter at line 6611 leaves it empty, current_player ==
                        // localPlayer never fires for them in this round, so the path #3
                        // close inside siguienteAccionLocalRecuperada is never invoked.
                        // Guard on the sync flag instead of on the queue contents so the
                        // dragon closes here as a final safety net.
                        if (this.isSincronizando_mano()) {
                            this.acciones_locales_recuperadas.clear();
                            Helpers.GUIRun(() -> {
                                if (recover_dialog != null) {
                                    recover_dialog.setVisible(false);
                                    recover_dialog.dispose();
                                    recover_dialog = null;
                                }
                                GameFrame.getInstance().getFull_screen_menu().setEnabled(true);
                                Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(true);
                                GameFrame.getInstance().refresh();
                            });
                            this.setSincronizando_mano(false);
                            GameFrame.getInstance().getRegistro().print(Translator.translate("game.timba_recuperada"));

                            if (GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {
                                Audio.playWavResource("misc/startplay.wav");
                            }

                            if (recovering_music_active) {
                                Audio.stopLoopMp3("misc/recovering.mp3");
                                Audio.playLoopMp3Resource("misc/background_music.mp3");
                                recovering_music_active = false;
                            }
                        }

                        setShowTime(true);

                        synchronized (lock_mostrar) {
                            lock_mostrar.notifyAll();
                        }

                        HashMap<Player, Hand> jugadas;
                        HashMap<Player, Hand> ganadores;

                        synchronized (getLock_contabilidad()) {
                            java.util.Iterator<Player> iterator = resisten.iterator();
                            while (iterator.hasNext()) {
                                Player jugador = iterator.next();
                                if (jugador.isExit()) {
                                    iterator.remove();
                                }
                            }

                            this.bote.genSidePots();
                            badbeat = false;
                            double sql_bote_total = this.bote_total;

                            // Run-it-twice: si la mano se acordó correr dos veces y
                            // hubo run-out (calles pendientes tras el all-in) con 2+
                            // implicados, ruta dedicada que liquida los dos boards
                            // (cada bote ÷2). El showdown normal de un board queda
                            // intacto en el else.
                            if (this.rit_agreed && this.rit_allin_street >= Crupier.PREFLOP
                                    && this.rit_allin_street < Crupier.RIVER && resisten.size() >= 2) {
                                resolverRunItTwiceShowdown(resisten);
                            } else {
                            // Defensivo: si el voto RIT se acordó pero los exits
                            // dejaron <2 implicados, resuelve el showdown normal
                            // y la pot_label no debe marcar cara alguna.
                            this.rit_pot_board_tag = null;
                            switch (resisten.size()) {
                                case 0:
                                    // Math yes, GUI no.
                                    awaitHandverifyBarrier();
                                    procesarCartasResistencia(new ArrayList<Player>(), false);

                                    GameFrame.getInstance().getRegistro().print("-----" + Translator.translate("game.gana_bote") + Helpers.money2String(this.bote.getTotal() + this.bote_sobrante) + Translator.translate("action.sin_tener_que_mostrar"));
                                    Helpers.GUIRun(() -> {
                                        setPotBackground(Color.RED);
                                        GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setForeground(Color.WHITE);
                                    });
                                    GameFrame.getInstance().setTapeteBote(this.bote.getTotal() + this.bote_sobrante, 0d);
                                    if (Helpers.doubleSecureCompare(0f, this.bote_total) < 0) {
                                        this.bote_sobrante += this.bote_total;
                                    }
                                    ganadores = new HashMap<>();
                                    for (Card carta : GameFrame.getInstance().getCartas_comunes()) {
                                        carta.desenfocar();
                                    }
                                    break;
                                case 1:
                                    awaitHandverifyBarrier();
                                    procesarCartasResistencia(new ArrayList<Player>(), false);

                                    resisten.get(0).setWinner(resisten.contains(GameFrame.getInstance().getLocalPlayer()) ? Translator.translate("ui.ganas_3") : Translator.translate("ui.gana_3"));
                                    if (resisten.get(0) != GameFrame.getInstance().getLocalPlayer()) {
                                        resisten.get(0).getHoleCard1().desenfocar();
                                        resisten.get(0).getHoleCard2().desenfocar();
                                    }
                                    resisten.get(0).pagar(this.bote.getTotal() + this.bote_sobrante, null);
                                    this.beneficio_bote_principal = this.bote.getTotal() + this.bote_sobrante - this.bote.getBet();
                                    GameFrame.getInstance().getRegistro().print(resisten.get(0).getNickname() + " " + Translator.translate("game.gana_bote") + Helpers.money2String(this.bote.getTotal() + this.bote_sobrante) + Translator.translate("action.sin_tener_que_mostrar"));
                                    Helpers.GUIRun(() -> {
                                        setPotBackground(Color.GREEN);
                                        GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setForeground(Color.BLACK);
                                    });
                                    GameFrame.getInstance().setTapeteBote(this.bote.getTotal() + this.bote_sobrante, this.beneficio_bote_principal);
                                    this.bote_total = 0f;
                                    this.bote_sobrante = 0f;
                                    for (Card carta : GameFrame.getInstance().getCartas_comunes()) {
                                        carta.desenfocar();
                                    }
                                    if (resisten.get(0) == GameFrame.getInstance().getLocalPlayer()) {
                                        GameFrame.getInstance().getLocalPlayer().activar_boton_mostrar(false);
                                    }
                                    if (resisten.get(0) == GameFrame.getInstance().getLocalPlayer()) {
                                        this.soundWinner(0, false);
                                    }
                                    ganadores = new HashMap<>();
                                    ganadores.put(resisten.get(0), null);
                                    this.sqlNewShowdown(resisten.get(0), null, true, true);
                                    break;
                                default:
                                    // Everyone shows their cards and GUI updates
                                    awaitHandverifyBarrier();
                                    procesarCartasResistencia(resisten, false);
                                    if (!this.destapar_resistencia) {
                                        Helpers.pausar(Crupier.PAUSA_ANTES_DE_SHOWDOWN * 1000);
                                    }

                                    if (this.bote.getSidePot() == null) {
                                        jugadas = this.calcularJugadas(resisten);
                                        ganadores = this.calcularGanadores(new HashMap<>(jugadas));
                                        double[] cantidad_pagar_ganador = this.calcularBoteParaGanador(this.bote.getTotal() + this.bote_sobrante, ganadores.size());
                                        this.beneficio_bote_principal = cantidad_pagar_ganador[0] - this.bote.getBet();
                                        ArrayList<Card> cartas_usadas_jugadas = new ArrayList<>();
                                        // Cartas a atenuar: NO se desenfocan aquí (settle), se
                                        // difieren a tras la pasada 2 del showdown para no filtrar
                                        // el dorso atenuado de cartas aún tapadas (ver showdown()).
                                        ArrayList<Card> diferir_dim = new ArrayList<>();
                                        Player unganador = null;

                                        for (Map.Entry<Player, Hand> entry : ganadores.entrySet()) {
                                            Player ganador = entry.getKey();
                                            Hand jugada = entry.getValue();
                                            ArrayList<Card> cartas = ganadores.size() == 1 ? jugada.getWinners() : jugada.getMano();
                                            for (Card carta : cartas) {
                                                if (!cartas_usadas_jugadas.contains(carta)) {
                                                    cartas_usadas_jugadas.add(carta);
                                                }
                                            }
                                            if (!cartas.contains(ganador.getHoleCard1())) {
                                                diferir_dim.add(ganador.getHoleCard1());
                                            }
                                            if (!cartas.contains(ganador.getHoleCard2())) {
                                                diferir_dim.add(ganador.getHoleCard2());
                                            }
                                            jugadas.remove(ganador);
                                            ganador.pagar(cantidad_pagar_ganador[0], null);
                                            this.bote_total -= cantidad_pagar_ganador[0];
                                            GameFrame.getInstance().getRegistro().print(ganador.getNickname() + " (" + Card.collection2String(ganador.getHoleCards()) + Translator.translate("game.gana_bote_2") + Helpers.money2String(cantidad_pagar_ganador[0]) + ") -> " + jugada);
                                            unganador = ganador;
                                            jugada_ganadora = jugada.getValue();
                                        }

                                        for (Card carta : GameFrame.getInstance().getCartas_comunes()) {
                                            if (!cartas_usadas_jugadas.contains(carta)) {
                                                diferir_dim.add(carta);
                                            }
                                        }

                                        for (Map.Entry<Player, Hand> entry : jugadas.entrySet()) {
                                            Player perdedor = entry.getKey();
                                            badbeat = badbeat(perdedor, unganador);
                                            perdedores.put(perdedor, entry.getValue());
                                            GameFrame.getInstance().getRegistro().print(perdedor.getNickname() + " " + Translator.translate("game.pierde_bote") + Helpers.money2String(cantidad_pagar_ganador[0]) + ")");
                                        }

                                        this.showdown(jugadas, ganadores, diferir_dim);
                                        Helpers.GUIRun(() -> {
                                            setPotBackground(Color.GREEN);
                                            GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setForeground(Color.BLACK);
                                        });
                                        GameFrame.getInstance().setTapeteBote(cantidad_pagar_ganador[0], this.beneficio_bote_principal);

                                    } else {
                                        jugadas = this.calcularJugadas(resisten);
                                        ganadores = this.calcularGanadores(new HashMap<>(jugadas));
                                        double[] cantidad_pagar_ganador = this.calcularBoteParaGanador(this.bote.getTotal() + this.bote_sobrante, ganadores.size());
                                        this.beneficio_bote_principal = cantidad_pagar_ganador[0] - this.bote.getBet();
                                        String bote_tapete = "#1{" + Helpers.money2String(this.bote.getTotal()) + "}";
                                        ArrayList<Card> cartas_usadas_jugadas = new ArrayList<>();
                                        // Cartas a atenuar: NO se desenfocan aquí (settle), se
                                        // difieren a tras la pasada 2 del showdown para no filtrar
                                        // el dorso atenuado de cartas aún tapadas (ver showdown()).
                                        ArrayList<Card> diferir_dim = new ArrayList<>();
                                        Player unganador = null;

                                        for (Map.Entry<Player, Hand> entry : ganadores.entrySet()) {
                                            Player ganador = entry.getKey();
                                            Hand jugada = entry.getValue();
                                            ArrayList<Card> cartas = ganadores.size() == 1 ? jugada.getWinners() : jugada.getMano();
                                            for (Card carta : cartas) {
                                                if (!cartas_usadas_jugadas.contains(carta)) {
                                                    cartas_usadas_jugadas.add(carta);
                                                }
                                            }
                                            if (!cartas.contains(ganador.getHoleCard1())) {
                                                diferir_dim.add(ganador.getHoleCard1());
                                            }
                                            if (!cartas.contains(ganador.getHoleCard2())) {
                                                diferir_dim.add(ganador.getHoleCard2());
                                            }
                                            jugadas.remove(entry.getKey());
                                            // sec_pot=null: paga sin pintar la franja "#1" todavía.
                                            // La marca se difiere a DESPUÉS de this.showdown() (más
                                            // abajo), para no adelantar el resultado del bote
                                            // principal a los veredictos GANA/PIERDE (pasada 2 del
                                            // showdown). Las marcas de los botes laterales ya se
                                            // pintan tras el showdown, en su bucle.
                                            ganador.pagar(cantidad_pagar_ganador[0], null);
                                            this.bote_total -= cantidad_pagar_ganador[0];
                                            jugada = entry.getValue();
                                            GameFrame.getInstance().getRegistro().print(ganador.getNickname() + " (" + Card.collection2String(ganador.getHoleCards()) + Translator.translate("game.gana_bote_principal") + Helpers.money2String(cantidad_pagar_ganador[0]) + ") -> " + jugada);
                                            unganador = ganador;
                                            jugada_ganadora = jugada.getValue();
                                        }

                                        for (Card carta : GameFrame.getInstance().getCartas_comunes()) {
                                            if (!cartas_usadas_jugadas.contains(carta)) {
                                                diferir_dim.add(carta);
                                            }
                                        }

                                        for (Map.Entry<Player, Hand> entry : jugadas.entrySet()) {
                                            Player perdedor = entry.getKey();
                                            badbeat = badbeat(perdedor, unganador);
                                            perdedores.put(perdedor, entry.getValue());
                                            GameFrame.getInstance().getRegistro().print(perdedor.getNickname() + " " + Translator.translate("game.pierde_bote_principal") + Helpers.money2String(cantidad_pagar_ganador[0]) + ")");
                                        }

                                        this.showdown(jugadas, ganadores, diferir_dim);

                                        // Franja "#1" del bote principal: se pinta AHORA, tras el
                                        // showdown (veredictos de la pasada 2), no en el pagar de
                                        // arriba. Estamos en la rama con side pots, así que la marca
                                        // procede; las de los botes laterales se añaden en el bucle.
                                        for (Player ganador_principal : ganadores.keySet()) {
                                            ganador_principal.marcarBotePot(1);
                                        }

                                        HandPot current_pot = this.bote.getSidePot();
                                        int conta_bote_secundario = 2;

                                        while (current_pot != null) {
                                            if (current_pot.getPlayers().size() == 1) {
                                                bote_tapete = bote_tapete + " + #" + String.valueOf(conta_bote_secundario) + "{" + Helpers.money2String(current_pot.getTotal()) + "}";
                                                current_pot.getPlayers().get(0).pagar(current_pot.getTotal(), conta_bote_secundario);
                                                this.bote_total -= current_pot.getTotal();
                                                GameFrame.getInstance().getRegistro().print(current_pot.getPlayers().get(0).getNickname() + " " + Translator.translate("game.recupera_bote_sobrante_secundario") + String.valueOf(conta_bote_secundario) + " (" + Helpers.money2String(current_pot.getTotal()) + ")");
                                                this.sqlUpdateShowdownPay(current_pot.getPlayers().get(0));
                                            } else {
                                                jugadas = this.calcularJugadas(current_pot.getPlayers());
                                                ganadores = this.calcularGanadores(new HashMap<>(jugadas));
                                                cantidad_pagar_ganador = this.calcularBoteParaGanador(current_pot.getTotal(), ganadores.size());
                                                bote_tapete = bote_tapete + " + #" + String.valueOf(conta_bote_secundario) + "{" + Helpers.money2String(current_pot.getTotal()) + "}";
                                                for (Map.Entry<Player, Hand> entry : ganadores.entrySet()) {
                                                    Player ganador = entry.getKey();
                                                    jugadas.remove(entry.getKey());
                                                    ganador.pagar(cantidad_pagar_ganador[0], conta_bote_secundario);
                                                    this.bote_total -= cantidad_pagar_ganador[0];
                                                    Hand jugada = entry.getValue();
                                                    GameFrame.getInstance().getRegistro().print(ganador.getNickname() + " (" + Card.collection2String(ganador.getHoleCards()) + Translator.translate("game.gana_bote_secundario") + String.valueOf(conta_bote_secundario) + " (" + Helpers.money2String(cantidad_pagar_ganador[0]) + ") -> " + jugada);
                                                    this.sqlUpdateShowdownPay(ganador);
                                                }
                                                for (Map.Entry<Player, Hand> entry : jugadas.entrySet()) {
                                                    Player perdedor = entry.getKey();
                                                    perdedores.put(perdedor, entry.getValue());
                                                    GameFrame.getInstance().getRegistro().print(perdedor.getNickname() + " " + Translator.translate("game.pierde_bote_secundario") + String.valueOf(conta_bote_secundario) + " (" + Helpers.money2String(cantidad_pagar_ganador[0]) + ")");
                                                }
                                            }
                                            current_pot = current_pot.getSidePot();
                                            conta_bote_secundario++;
                                        }
                                        Helpers.GUIRun(() -> {
                                            setPotBackground(Color.BLACK);
                                            GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setForeground(Color.WHITE);
                                        });
                                        GameFrame.getInstance().setTapeteBote(bote_tapete);
                                    }
                                    this.bote_sobrante = this.bote_total;
                                    break;
                            }
                            }

                            Helpers.GUIRun(() -> {
                                GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setHorizontalAlignment(JLabel.CENTER);
                            });

                            this.bote_total = 0f;

                            if (!GameFrame.TEST_MODE && !resisten.contains(GameFrame.getInstance().getLocalPlayer())) {
                                if (GameFrame.getInstance().getLocalPlayer().isActivo() && GameFrame.getInstance().getLocalPlayer().getParguela_counter() > 0) {
                                    GameFrame.getInstance().getLocalPlayer().activar_boton_mostrar(true);
                                }
                                this.soundShowdown();
                            }

                            // Mano anulada por MISDEAL: rollbackAbortedHand ya
                            // la cerró (end estampado, pot=0, balances
                            // post-refund). Re-estamparla aquí escribiría
                            // pot=sql_bote_total (capturado ANTES del aborto,
                            // ≠0 si CARA-B del run-it-twice abortó tras
                            // capturarlo) sobre el pot=0 explícito del
                            // rollback, y el backup de balances de una mano
                            // anulada es el de la mano anterior (el refund
                            // íntegro restaura los stacks pre-mano).
                            if (!this.mano_anulada) {
                                // Settlement attestation: absorb who-won-how-much into H_final and
                                // run the closing receipt consensus over it. Here getBote()/getPagar()
                                // are final (the payout above is done) and not yet purged by the
                                // resetBote() loop below, and the hand was not voided. Once per hand,
                                // common to every branch (showdown, fold, run-it-twice).
                                runSettlementConsensus();
                                sqlUpdateHandEnd(sql_bote_total);
                            }

                            if (this.update_game_seats) {
                                String players = "";
                                for (String nick : this.nicks_permutados) {
                                    try {
                                        players += "#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8"));
                                    } catch (UnsupportedEncodingException ex) {
                                    }
                                }
                                sqlUpdateGameSeats(players.substring(1));
                                this.update_game_seats = false;
                            }

                            this.bote_total = 0f;

                            for (Player jugador : GameFrame.getInstance().getJugadores()) {
                                jugador.resetBote();
                                jugador.checkGameOver();
                            }
                        }

                        disableAllPlayersTimeout();

                        synchronized (lock_fin_mano) {

                            // Las reglas (IWTSTH/RIT/Rabbit) ya no se greyan aquí: sus
                            // setters sincronizan sobre lock_fin_mano, así que cualquier
                            // cambio durante el fin de mano queda en cola y se aplica
                            // limpio tras resolverla (a la mano siguiente). Solo queda el
                            // repaint del tapete.
                            Helpers.GUIRun(() -> {
                                GameFrame.getInstance().getTapete().getCommunityCards().repaint();
                            });

                            synchronized (lock_rabbit) {

                                if (GameFrame.RABBIT_HUNTING != 0 && !GameFrame.getInstance().getLocalPlayer().isCalentando() && !GameFrame.getInstance().getLocalPlayer().isSpectator()) {
                                    procesarCartasComunesRestantes();
                                }

                                for (Card carta : GameFrame.getInstance().getCartas_comunes()) {
                                    if (carta.isTapada()) {
                                        if (GameFrame.RABBIT_HUNTING != 0 && !GameFrame.getInstance().getLocalPlayer().isCalentando() && !GameFrame.getInstance().getLocalPlayer().isSpectator()) {
                                            carta.taparRabbit();
                                        } else {
                                            carta.desenfocar();
                                        }
                                    }
                                }

                            }

                            startIWTSTHPlayersBlinking();

                            if (!GameFrame.TEST_MODE) {
                                if (getJugadoresActivos() > 1 && !GameFrame.getInstance().getLocalPlayer().isExit()) {
                                    this.pausaConBarra(this.bote.getSide_pot_count() == 0 ? ((resisten.size() > 1 || GameFrame.RABBIT_HUNTING != 0) ? PAUSA_ENTRE_MANOS : Math.round(0.5f * PAUSA_ENTRE_MANOS)) : Math.round(1.5f * PAUSA_ENTRE_MANOS));
                                }

                                if (this.iwtsthing) {
                                    synchronized (lock_iwtsth) {
                                        if (this.iwtsthing) {
                                            try {
                                                lock_iwtsth.wait(IWTSTH_TIMEOUT);
                                            } catch (InterruptedException ex) {
                                            }
                                            this.iwtsthing = false;
                                        }
                                    }
                                }

                                synchronized (lock_mostrar) {
                                    setShowTime(false);
                                }
                                GameFrame.getInstance().getLocalPlayer().desactivar_boton_mostrar();
                                GameFrame.getInstance().getRegistro().actualizarCartasPerdedores(perdedores);

                                if (!this.isLast_hand()) {
                                    checkRebuyTime();
                                    exitSpectatorBots();
                                    updateExitPlayers();
                                    if (GameFrame.RABBIT_HUNTING != 0) {
                                        waitRabbitProcessing();
                                    }
                                } else {

                                    fin_de_la_transmision = true;
                                }
                            } else {
                                this.pausaConBarra(Crupier.PAUSA_ENTRE_MANOS_TEST);
                                synchronized (lock_mostrar) {
                                    setShowTime(false);
                                }
                                GameFrame.getInstance().getLocalPlayer().desactivar_boton_mostrar();
                                GameFrame.getInstance().getRegistro().actualizarCartasPerdedores(perdedores);
                                fin_de_la_transmision = this.isLast_hand();
                            }

                            // (Las reglas IWTSTH/RIT/Rabbit ya no se re-habilitan aquí:
                            // dejaron de greyarse al resolver la mano — ver nota arriba.)
                        }
                    } else {

                        if (!GameFrame.getInstance().getLocalPlayer().isSpectator() && Helpers.doubleSecureCompare(0f, this.bote_sobrante) < 0) {
                            GameFrame.getInstance().getLocalPlayer().pagar(this.bote_sobrante, null);
                            this.bote_sobrante = 0f;
                        }

                        for (Card carta : GameFrame.getInstance().getCartas_comunes()) {
                            carta.iniciarCarta();
                        }

                        GameFrame.getInstance().getTiempo_juego().stop();

                        // Defense in depth: if we reach GAME OVER while a
                        // recovery dragon was still open (NUEVA_MANO returned false
                        // before the replay loop got a chance to close it), tear it
                        // down so the user is not stuck looking at the GIF.
                        cerrarRecoverDialogYSync();

                        GameFrame.getInstance().getRegistro().print(Translator.translate("player.la_timba_ha_terminado_no"));

                        Helpers.mostrarMensajeInformativo(GameFrame.getInstance(), Translator.translate("player.la_timba_ha_terminado_no"), new ImageIcon(Init.class.getResource("/images/exit.png")));

                        fin_de_la_transmision = true;

                    }
                } else {

                    GameFrame.getInstance().getTiempo_juego().stop();

                    // Defense in depth: see comment above.
                    cerrarRecoverDialogYSync();

                    GameFrame.getInstance().getRegistro().print(Translator.translate("player.la_timba_ha_terminado_no"));

                    Helpers.mostrarMensajeInformativo(GameFrame.getInstance(), Translator.translate("player.la_timba_ha_terminado_no"), new ImageIcon(Init.class.getResource("/images/exit.png")));

                    fin_de_la_transmision = true;
                }
            } catch (Exception ex) {
                if (!fin_de_la_transmision) {
                    LOGGER.log(Level.SEVERE, "Crupier fatal error", ex);
                    Helpers.mostrarMensajeError(GameFrame.getInstance(), Translator.translate("error.crupier_fatal"));
                    System.exit(1);
                }
            }
        }

        if (!GameFrame.getInstance().isPartida_local()) {
            String exitCmd = "EXIT#" + getTestamentoCriptografico();
            sendGAMECommandToServer(exitCmd, false);
        }

        GameFrame.getInstance().finTransmision(fin_de_la_transmision);
    }

    public void checkRebuyTime() {

        // Reset defensivo: solo el game over local interactivo lo pone a true
        // (más abajo) para silenciar los GIF remotos mientras su diálogo es el
        // dueño del audio. Limpiarlo al entrar evita que un flag colgado de una
        // mano anterior deje mudos los GIF remotos de la siguiente.
        RemotePlayer.LOCAL_GAMEOVER_OWNS_AUDIO = false;

        ArrayList<String> rebuy_players = new ArrayList<>();

        for (Player jugador : GameFrame.getInstance().getJugadores()) {

            if (jugador != GameFrame.getInstance().getLocalPlayer() && jugador.isActivo()
                    && Helpers.doubleSecureCompare(0f,
                            Helpers.doubleClean(jugador.getStack()) + Helpers.doubleClean(jugador.getPagar())) == 0) {

                String nick = jugador.getNickname();
                Participant participante = GameFrame.getInstance().getParticipantes().get(nick);
                boolean isBot = participante != null && participante.isCpu();

                if (!GameFrame.REBUY || (isBot && !GameFrame.BOT_REBUY) || atRebuyLimit(nick)) {
                    jugador.setSpectator(null);
                } else {
                    rebuy_players.add(nick);
                }

            }
        }

        this.rebuy_time = !rebuy_players.isEmpty();

        // El local también se arruinó: su game-over modal corre ANTES de
        // recibirRebuys, así que una cuenta atrás remota saldría desincronizada.
        // En ese caso recibirRebuys NO la lanza y solo muestra el desenlace
        // (RECOMPRA) de cada remoto.
        boolean local_ruined = GameFrame.getInstance().getLocalPlayer().isActivo()
                && Helpers.doubleSecureCompare(Helpers.doubleClean(GameFrame.getInstance().getLocalPlayer().getStack())
                        + Helpers.doubleClean(GameFrame.getInstance().getLocalPlayer().getPagar()), 0f) == 0;

        if (local_ruined) {

            this.rebuy_time = true;

            final float old_brightness = GameFrame.getInstance().getCapa_brillo().getBrightness();

            if (GameFrame.REBUY && !atRebuyLimit(GameFrame.getInstance().getLocalPlayer().getNickname()) && GameFrame.AUTO_REBUY_ON_BROKE) {

                // Recompra automática al arruinarse: NO se muestra la animación de
                // game-over; directamente el RebuyDialog (AUTO) —misma barra de
                // tiempo e importe por defecto, más un botón rojo de cancelar—. Al
                // expirar -> recompra; al cancelar -> espectador.
                int rebuy_min = GameFrame.FIXED_BUYIN ? 1 : GameFrame.getBuyinMin();
                int rebuy_max = GameFrame.getBuyinCap();
                int rebuy_def = GameFrame.FIXED_BUYIN ? GameFrame.BUYIN : GameFrame.getBuyinDefault();

                final RebuyDialog[] auto_rebuy_dialog = new RebuyDialog[1];

                Helpers.GUIRunAndWait(() -> {
                    auto_rebuy_dialog[0] = new RebuyDialog(GameFrame.getInstance(), true, false, GameOverDialog.REBUY_DIALOG_COUNTDOWN, rebuy_min, rebuy_max, rebuy_def, "rebuy.recomprar_auto", true);
                    auto_rebuy_dialog[0].setLocationRelativeTo(auto_rebuy_dialog[0].getParent());
                    auto_rebuy_dialog[0].setVisible(true);
                });

                if (auto_rebuy_dialog[0].isRebuy()) {

                    try {

                        rebuy_players.remove(GameFrame.getInstance().getLocalPlayer().getNickname());

                        rebuy_now.put(GameFrame.getInstance().getLocalPlayer().getNickname(),
                                (int) auto_rebuy_dialog[0].getRebuy_spinner().getValue());

                        String comando = "REBUY#"
                                + Base64.getEncoder().encodeToString(
                                        GameFrame.getInstance().getLocalPlayer().getNickname().getBytes("UTF-8"))
                                + "#"
                                + String.valueOf((int) auto_rebuy_dialog[0].getRebuy_spinner().getValue());

                        if (GameFrame.getInstance().isPartida_local()) {
                            this.broadcastGAMECommandFromServer(comando, null);
                        } else {
                            this.sendGAMECommandToServer(comando);
                        }
                    } catch (UnsupportedEncodingException ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    }

                } else {

                    try {

                        rebuy_players.remove(GameFrame.getInstance().getLocalPlayer().getNickname());

                        String comando = "REBUY#"
                                + Base64.getEncoder().encodeToString(
                                        GameFrame.getInstance().getLocalPlayer().getNickname().getBytes("UTF-8"))
                                + "#0";

                        if (GameFrame.getInstance().isPartida_local()) {
                            this.broadcastGAMECommandFromServer(comando, null);
                        } else {
                            this.sendGAMECommandToServer(comando);
                        }
                    } catch (UnsupportedEncodingException ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    }

                    if (rebuy_now.containsKey(GameFrame.getInstance().getLocalPlayer().getNickname())) {

                        rebuy_now.remove(GameFrame.getInstance().getLocalPlayer().getNickname());

                    }

                    GameFrame.getInstance().getLocalPlayer().setSpectator(null);

                    GameFrame.getInstance().getRegistro().print(GameFrame.getInstance().getLocalPlayer().getNickname() + " "
                            + Translator.translate("player.te_quedas_de_espectador"));
                }

            } else if (GameFrame.REBUY && !atRebuyLimit(GameFrame.getInstance().getLocalPlayer().getNickname())) {

                Helpers.GUIRunAndWait(() -> {
                    if (old_brightness != BrightnessLayerUI.LIGHTS_OFF_BRIGHTNESS) {
                        GameFrame.getInstance().getCapa_brillo().setBrightness(BrightnessLayerUI.LIGHTS_OFF_BRIGHTNESS);

                        GameFrame.getInstance().getTapete().repaint();
                    }

                    gameover_dialog = new GameOverDialog(GameFrame.getInstance(), true);

                    GameFrame.getInstance().setGame_over_dialog(true);

                    gameover_dialog.setLocationRelativeTo(gameover_dialog.getParent());

                    gameover_dialog.setVisible(true);

                    if (old_brightness != BrightnessLayerUI.LIGHTS_OFF_BRIGHTNESS) {
                        GameFrame.getInstance().getCapa_brillo().setBrightness(old_brightness);

                        GameFrame.getInstance().getTapete().repaint();
                    }
                });

                GameFrame.getInstance().setGame_over_dialog(false);

                if (gameover_dialog.isContinua()) {

                    try {

                        rebuy_players.remove(GameFrame.getInstance().getLocalPlayer().getNickname());

                        rebuy_now.put(GameFrame.getInstance().getLocalPlayer().getNickname(),
                                (int) gameover_dialog.getBuyin_dialog().getRebuy_spinner().getValue());

                        String comando = "REBUY#"
                                + Base64.getEncoder().encodeToString(
                                        GameFrame.getInstance().getLocalPlayer().getNickname().getBytes("UTF-8"))
                                + "#"
                                + String.valueOf((int) gameover_dialog.getBuyin_dialog().getRebuy_spinner().getValue());

                        if (GameFrame.getInstance().isPartida_local()) {
                            this.broadcastGAMECommandFromServer(comando, null);
                        } else {
                            this.sendGAMECommandToServer(comando);
                        }
                    } catch (UnsupportedEncodingException ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    }
                } else {
                    try {

                        rebuy_players.remove(GameFrame.getInstance().getLocalPlayer().getNickname());

                        String comando = "REBUY#"
                                + Base64.getEncoder().encodeToString(
                                        GameFrame.getInstance().getLocalPlayer().getNickname().getBytes("UTF-8"))
                                + "#0";

                        if (GameFrame.getInstance().isPartida_local()) {
                            this.broadcastGAMECommandFromServer(comando, null);
                        } else {
                            this.sendGAMECommandToServer(comando);
                        }
                    } catch (UnsupportedEncodingException ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    }

                    if (rebuy_now.containsKey(GameFrame.getInstance().getLocalPlayer().getNickname())) {

                        rebuy_now.remove(GameFrame.getInstance().getLocalPlayer().getNickname());

                    }

                    GameFrame.getInstance().getLocalPlayer().setSpectator(null);

                    GameFrame.getInstance().getRegistro().print(GameFrame.getInstance().getLocalPlayer().getNickname() + " "
                            + Translator.translate("player.te_quedas_de_espectador"));
                }

            } else {

                Helpers.GUIRunAndWait(() -> {
                    if (old_brightness != BrightnessLayerUI.LIGHTS_OFF_BRIGHTNESS) {
                        GameFrame.getInstance().getCapa_brillo().setBrightness(BrightnessLayerUI.LIGHTS_OFF_BRIGHTNESS);

                        GameFrame.getInstance().getTapete().repaint();
                    }

                    gameover_dialog = new GameOverDialog(GameFrame.getInstance(), true, true);

                    GameFrame.getInstance().setGame_over_dialog(true);

                    gameover_dialog.setLocationRelativeTo(gameover_dialog.getParent());

                    gameover_dialog.setVisible(true);

                    if (old_brightness != BrightnessLayerUI.LIGHTS_OFF_BRIGHTNESS) {

                        GameFrame.getInstance().getCapa_brillo().setBrightness(old_brightness);

                        GameFrame.getInstance().getTapete().repaint();
                    }
                });

                GameFrame.getInstance().setGame_over_dialog(false);

                GameFrame.getInstance().getLocalPlayer().setSpectator(null);

                GameFrame.getInstance().getRegistro().print(GameFrame.getInstance().getLocalPlayer().getNickname() + " "
                        + Translator.translate("player.te_quedas_de_espectador"));
            }

        }

        if (!rebuy_players.isEmpty()) {

            // Enviamos los REBUYS de los bots
            if (GameFrame.getInstance().isPartida_local()) {

                for (Player jugador : GameFrame.getInstance().getJugadores()) {

                    if (rebuy_players.contains(jugador.getNickname())
                            && GameFrame.getInstance().getParticipantes().get(jugador.getNickname()) != null
                            && GameFrame.getInstance().getParticipantes().get(jugador.getNickname()).isCpu()) {

                        rebuy_players.remove(jugador.getNickname());

                        // Recompra automatica de bot arruinado: en fijo recompra el
                        // buy-in completo (como siempre); en variable, 50BB por
                        // defecto (consistente con su buy-in de entrada).
                        int botbuy = GameFrame.FIXED_BUYIN ? GameFrame.BUYIN : GameFrame.getBuyinDefault();
                        rebuy_now.put(jugador.getNickname(), botbuy);

                        try {
                            String comando = "REBUY#"
                                    + Base64.getEncoder().encodeToString(jugador.getNickname().getBytes("UTF-8"))
                                    + "#" + String.valueOf(botbuy);

                            this.broadcastGAMECommandFromServer(comando, null);

                        } catch (UnsupportedEncodingException ex) {
                            LOGGER.log(Level.SEVERE, null, ex);
                        }

                    }
                }
            }

            this.recibirRebuys(rebuy_players, local_ruined);
        }

        // Cerrado el ciclo de rebuy: el diálogo local ya no es dueño de ningún
        // audio (además del reset defensivo al entrar la próxima mano).
        RemotePlayer.LOCAL_GAMEOVER_OWNS_AUDIO = false;

        this.rebuy_time = false;

    }

    public HashMap<Player, Hand> calcularJugadas(ArrayList<Player> jugadores) {
        HashMap<Player, Hand> jugadas = new HashMap<>();

        for (Player jugador : jugadores) {

            // If the player reaches winner calculation with null cards, it means
            // they disconnected before reaching showdown. Their hand is mucked.
            if (jugador.getHoleCard1().getValor() == null || jugador.getHoleCard1().getValor().isEmpty() || jugador.getHoleCard1().getValor().equals("null")) {
                LOGGER.log(Level.WARNING, "MUCK: {0} could not reveal cards due to disconnection — pot lost", jugador.getNickname());
                continue; // Al no meterlo en el mapa 'jugadas', el motor lo ignora para el premio.
            }

            ArrayList<Card> cartas_utilizables = new ArrayList<>();
            for (Card c : GameFrame.getInstance().getCartas_comunes()) {
                if (c != null && !c.isTapada() && c.getValor() != null && !c.getValor().isEmpty()) {
                    cartas_utilizables.add(c);
                }
            }

            cartas_utilizables.add(jugador.getHoleCard1());
            cartas_utilizables.add(jugador.getHoleCard2());
            try {
                jugadas.put(jugador, new Hand(cartas_utilizables));
            } catch (Exception e) {
            }
        }

        return jugadas;
    }

    public HashMap<Player, Hand> calcularGanadores(HashMap<Player, Hand> candidatos) {

        int jugada_max = Hand.CARTA_ALTA;

        // Averiguamos la jugada máxima entre todos los jugadores
        for (Map.Entry<Player, Hand> entry : candidatos.entrySet()) {

            if (entry.getValue().getValue() > jugada_max) {
                jugada_max = entry.getValue().getValue();
            }
        }

        // Eliminamos a los jugadores con jugadas por debajo de la jugada máxima
        for (Iterator<Map.Entry<Player, Hand>> it = candidatos.entrySet().iterator(); it.hasNext();) {

            Map.Entry<Player, Hand> entry = it.next();

            if (entry.getValue().getValue() < jugada_max) {
                it.remove();
            }
        }

        if (candidatos.size() == 1 || jugada_max == Hand.ESCALERA_COLOR_REAL) {

            return candidatos;

        } else {

            // Si hay varios con la jugada máxima intentamos desempatar
            switch (jugada_max) {
                case Hand.ESCALERA_COLOR:
                    return desempatarEscalera(candidatos);
                case Hand.POKER:
                    return desempatarRepetidas(candidatos, CARTAS_POKER);
                case Hand.FULL:
                    return desempatarFull(candidatos);
                case Hand.COLOR:
                    return desempatarCartaAlta(candidatos, 0);
                case Hand.ESCALERA:
                    return desempatarEscalera(candidatos);
                case Hand.TRIO:
                    return desempatarRepetidas(candidatos, CARTAS_TRIO);
                case Hand.DOBLE_PAREJA:
                    return desempatarDoblePareja(candidatos);
                case Hand.PAREJA:
                    return desempatarRepetidas(candidatos, CARTAS_PAREJA);
                default:
                    return desempatarCartaAlta(candidatos, 0);
            }
        }
    }

    private HashMap<Player, Hand> desempatarDoblePareja(HashMap<Player, Hand> jugadores) {

        int carta_alta = 1;

        // Averiguamos la carta más alta de la primera pareja (la pareja grande)
        for (Map.Entry<Player, Hand> entry : jugadores.entrySet()) {
            Hand jugada = entry.getValue();
            if (jugada.getMano().get(0).getValorNumerico() > carta_alta) {
                carta_alta = jugada.getMano().get(0).getValorNumerico();
            }
        }

        // Nos cargamos todos los que tengan una pareja grande menor
        for (Iterator<Map.Entry<Player, Hand>> it = jugadores.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Player, Hand> entry = it.next();
            Hand jugada = entry.getValue();
            if (jugada.getMano().get(0).getValorNumerico() < carta_alta) {
                it.remove();
            }
        }

        if (jugadores.size() == 1) {

            return jugadores;

        } else {

            carta_alta = 1;

            // Averiguamos la carta más alta de la segunda pareja
            for (Map.Entry<Player, Hand> entry : jugadores.entrySet()) {
                Hand jugada = entry.getValue();
                if (jugada.getMano().get(2).getValorNumerico() > carta_alta) {
                    carta_alta = jugada.getMano().get(2).getValorNumerico();
                }
            }

            // Nos cargamos todos los que tengan una pareja secundaria menor
            for (Iterator<Map.Entry<Player, Hand>> it = jugadores.entrySet().iterator(); it.hasNext();) {
                Map.Entry<Player, Hand> entry = it.next();
                Hand jugada = entry.getValue();
                if (jugada.getMano().get(2).getValorNumerico() < carta_alta) {
                    it.remove();
                }
            }

            if (jugadores.size() == 1) {

                return jugadores;
            } else {

                return desempatarCartaAlta(jugadores, CARTAS_PAREJA * 2);
            }

        }

    }

    private HashMap<Player, Hand> desempatarFull(HashMap<Player, Hand> jugadores) {

        int carta_alta = 1;

        // Averiguamos la carta más alta del trío del FULL
        for (Map.Entry<Player, Hand> entry : jugadores.entrySet()) {
            Hand jugada = entry.getValue();
            if (jugada.getMano().get(0).getValorNumerico() > carta_alta) {
                carta_alta = jugada.getMano().get(0).getValorNumerico();
            }
        }

        // Nos cargamos todos los que tengan un trío con carta más pequeña
        for (Iterator<Map.Entry<Player, Hand>> it = jugadores.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Player, Hand> entry = it.next();
            Hand jugada = entry.getValue();
            if (jugada.getMano().get(0).getValorNumerico() < carta_alta) {
                it.remove();
            }
        }

        if (jugadores.size() == 1) {

            return jugadores;

        } else {

            carta_alta = 1;

            // Averiguamos la carta más alta de la pareja del FULL
            for (Map.Entry<Player, Hand> entry : jugadores.entrySet()) {
                Hand jugada = entry.getValue();
                if (jugada.getMano().get(3).getValorNumerico() > carta_alta) {
                    carta_alta = jugada.getMano().get(3).getValorNumerico();
                }
            }

            // Nos cargamos todos los que tengan una pareja con carta más pequeña
            for (Iterator<Map.Entry<Player, Hand>> it = jugadores.entrySet().iterator(); it.hasNext();) {
                Map.Entry<Player, Hand> entry = it.next();
                Hand jugada = entry.getValue();
                if (jugada.getMano().get(3).getValorNumerico() < carta_alta) {
                    it.remove();
                }
            }

            return jugadores;

        }

    }

    private HashMap<Player, Hand> desempatarRepetidas(HashMap<Player, Hand> jugadores, int repetidas) {

        int carta_alta = 1;

        // Averiguamos la carta más alta del POKER/TRIO/PAREJA
        for (Map.Entry<Player, Hand> entry : jugadores.entrySet()) {
            Hand jugada = entry.getValue();
            if (jugada.getMano().get(0).getValorNumerico() > carta_alta) {
                carta_alta = jugada.getMano().get(0).getValorNumerico();
            }
        }

        // Nos cargamos todos los que tengan un POKER/TRIO/PAREJA con carta más pequeña
        for (Iterator<Map.Entry<Player, Hand>> it = jugadores.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Player, Hand> entry = it.next();
            Hand jugada = entry.getValue();
            if (jugada.getMano().get(0).getValorNumerico() < carta_alta) {
                it.remove();
            }
        }

        if (jugadores.size() == 1) {

            return jugadores;

        } else {

            return desempatarCartaAlta(jugadores, repetidas);
        }

    }

    private HashMap<Player, Hand> desempatarEscalera(HashMap<Player, Hand> jugadores) {

        int carta_alta = 0;

        for (Map.Entry<Player, Hand> entry : jugadores.entrySet()) {

            Hand jugada = entry.getValue();

            if (jugada.getMano().get(0).getValorNumerico() > carta_alta) {
                carta_alta = jugada.getMano().get(0).getValorNumerico();
            }
        }

        // Nos cargamos todos los que tengan una escalera menor que la máxima
        for (Iterator<Map.Entry<Player, Hand>> it = jugadores.entrySet().iterator(); it.hasNext();) {

            Map.Entry<Player, Hand> entry = it.next();

            Hand jugada = entry.getValue();

            if (jugada.getMano().get(0).getValorNumerico() < carta_alta) {
                it.remove();
            }
        }

        return jugadores;
    }

    private HashMap<Player, Hand> desempatarCartaAlta(HashMap<Player, Hand> jugadores, int start_card) {

        int cartas_max = CARTAS_MAX;

        for (Map.Entry<Player, Hand> entry : jugadores.entrySet()) {

            Hand jugada = entry.getValue();

            cartas_max = Math.min(cartas_max, jugada.getMano().size());
        }

        for (int i = start_card; i < cartas_max; i++) {

            int carta_alta = 1;

            // Averiguamos la carta más alta
            for (Map.Entry<Player, Hand> entry : jugadores.entrySet()) {
                Hand jugada = entry.getValue();
                if (jugada.getMano().get(i).getValorNumerico() > carta_alta) {
                    carta_alta = jugada.getMano().get(i).getValorNumerico();
                }
            }

            // Nos cargamos todos los que tengan una carta menor
            for (Iterator<Map.Entry<Player, Hand>> it = jugadores.entrySet().iterator(); it.hasNext();) {
                Map.Entry<Player, Hand> entry = it.next();
                Hand jugada = entry.getValue();
                if (jugada.getMano().get(i).getValorNumerico() < carta_alta) {
                    it.remove();
                }
            }

            if (jugadores.size() == 1) {

                return jugadores;
            }
        }

        return jugadores;

    }

    private HashMap<Player, Integer[]> monteCarlo(ArrayList<Player> resisten, int iterations) {

        HashMap<Player, Integer[]> stats = new HashMap<>();

        // Defensa: jugadores que llegan a monteCarlo sin hole cards
        // reveladas (disconnection mid-hand antes del showdown) construyen
        // una org.alberta.poker.Hand con suit/rank invalido, y
        // HandEvaluator.Find_Flush devuelve -1 al iterar el array de
        // palos -> OOBE -> error fatal del Crupier. calcularJugadas ya filtra el
        // mismo caso (logea MUCK y los excluye del calculo de
        // ganadores); aqui replicamos el filtro para que monteCarlo no
        // pete cuando se evalua la ronda siguiente a un peer recien caido.
        ArrayList<Player> resistenSafe = new ArrayList<>(resisten.size());
        for (Player p : resisten) {
            if (p == null) {
                continue;
            }
            Card hc1 = p.getHoleCard1();
            Card hc2 = p.getHoleCard2();
            if (hc1 == null || hc2 == null) {
                continue;
            }
            String v1 = hc1.getValor();
            String v2 = hc2.getValor();
            if (v1 == null || v1.isEmpty() || v1.equals("null")
                    || v2 == null || v2.isEmpty() || v2.equals("null")) {
                continue;
            }
            resistenSafe.add(p);
        }
        resisten = resistenSafe;

        if (resisten.size() <= 1) {
            return stats;
        }

        ArrayList<Integer> deck = new ArrayList<>();

        for (int i = 1; i <= 52; i++) {
            deck.add(i);
        }

        org.alberta.poker.Hand board = new org.alberta.poker.Hand();

        if (this.street == Crupier.FLOP) {
            board.addCard(Bot.coronaIntegerCard2LokiCard(
                    GameFrame.getInstance().getTapete().getCommunityCards().getFlop1().getCartaComoEntero()));
            board.addCard(Bot.coronaIntegerCard2LokiCard(
                    GameFrame.getInstance().getTapete().getCommunityCards().getFlop2().getCartaComoEntero()));
            board.addCard(Bot.coronaIntegerCard2LokiCard(
                    GameFrame.getInstance().getTapete().getCommunityCards().getFlop3().getCartaComoEntero()));

            deck.remove(
                    (Integer) GameFrame.getInstance().getTapete().getCommunityCards().getFlop1().getCartaComoEntero());
            deck.remove(
                    (Integer) GameFrame.getInstance().getTapete().getCommunityCards().getFlop2().getCartaComoEntero());
            deck.remove(
                    (Integer) GameFrame.getInstance().getTapete().getCommunityCards().getFlop3().getCartaComoEntero());
        } else if (this.street == Crupier.TURN) {
            board.addCard(Bot.coronaIntegerCard2LokiCard(
                    GameFrame.getInstance().getTapete().getCommunityCards().getFlop1().getCartaComoEntero()));
            board.addCard(Bot.coronaIntegerCard2LokiCard(
                    GameFrame.getInstance().getTapete().getCommunityCards().getFlop2().getCartaComoEntero()));
            board.addCard(Bot.coronaIntegerCard2LokiCard(
                    GameFrame.getInstance().getTapete().getCommunityCards().getFlop3().getCartaComoEntero()));
            board.addCard(Bot.coronaIntegerCard2LokiCard(
                    GameFrame.getInstance().getTapete().getCommunityCards().getTurn().getCartaComoEntero()));

            deck.remove(
                    (Integer) GameFrame.getInstance().getTapete().getCommunityCards().getFlop1().getCartaComoEntero());
            deck.remove(
                    (Integer) GameFrame.getInstance().getTapete().getCommunityCards().getFlop2().getCartaComoEntero());
            deck.remove(
                    (Integer) GameFrame.getInstance().getTapete().getCommunityCards().getFlop3().getCartaComoEntero());
            deck.remove(
                    (Integer) GameFrame.getInstance().getTapete().getCommunityCards().getTurn().getCartaComoEntero());
        }

        HashMap<Player, org.alberta.poker.Hand> hole_cards = new HashMap<>();

        for (Player p : resisten) {

            org.alberta.poker.Hand hole = new org.alberta.poker.Hand();
            hole.addCard(Bot.coronaIntegerCard2LokiCard(p.getHoleCard1().getCartaComoEntero()));
            hole.addCard(Bot.coronaIntegerCard2LokiCard(p.getHoleCard2().getCartaComoEntero()));
            hole_cards.put(p, hole);

            deck.remove((Integer) p.getHoleCard1().getCartaComoEntero());
            deck.remove((Integer) p.getHoleCard2().getCartaComoEntero());

            stats.put(p, new Integer[]{iterations, 0, 0, 0});
        }

        // Run-it-twice CARA-B: las comunitarias que salieron en CARA-A NO se
        // reponen en el reparto real de CARA-B (la baraja sigue cascadeando sin
        // repetir), así que la simulación debe excluirlas. No son board actual
        // ni hole cards, de modo que aún están en deck; removerlas evita tratar
        // como disponibles hasta 5 cartas (all-in pre-flop) que CARA-B nunca
        // puede recibir.
        if (this.run_it_twice_side_b) {
            for (Integer cartaCaraA : this.rit_side_a_runout_cards) {
                deck.remove(cartaCaraA);
            }
        }

        for (int m = 0; m < iterations; m++) {

            ArrayList<Integer> deck_iteration = new ArrayList<>(deck);

            // Barajado del Montecarlo de probabilidades (SOLO display): PRNG rápido en lugar
            // del DRBG criptográfico (Helpers.CSPRNG_GENERATOR). El % es una estimación de N
            // muestras; la calidad criptográfica del azar es estadísticamente irrelevante y el
            // DRBG costaba ~45k draws por reparto en el hilo de juego (tirón en all-ins de PCs
            // lentos). NO afecta al reparto justo verificable, que va por la cascada cripto.
            Collections.shuffle(deck_iteration, java.util.concurrent.ThreadLocalRandom.current());

            org.alberta.poker.Hand board_iteration = new org.alberta.poker.Hand(board);

            switch (board_iteration.size()) {
                case 0:
                    board_iteration.addCard(Bot.coronaIntegerCard2LokiCard(deck_iteration.get(0)));
                    board_iteration.addCard(Bot.coronaIntegerCard2LokiCard(deck_iteration.get(1)));
                    board_iteration.addCard(Bot.coronaIntegerCard2LokiCard(deck_iteration.get(2)));
                    board_iteration.addCard(Bot.coronaIntegerCard2LokiCard(deck_iteration.get(3)));
                    board_iteration.addCard(Bot.coronaIntegerCard2LokiCard(deck_iteration.get(4)));
                    break;
                case 3:
                    board_iteration.addCard(Bot.coronaIntegerCard2LokiCard(deck_iteration.get(0)));
                    board_iteration.addCard(Bot.coronaIntegerCard2LokiCard(deck_iteration.get(1)));
                    break;
                case 4:
                    board_iteration.addCard(Bot.coronaIntegerCard2LokiCard(deck_iteration.get(0)));
                    break;
                default:
                    break;
            }

            HashMap<Player, org.alberta.poker.Hand> cards7_iteration = new HashMap<>();

            for (Player p : resisten) {
                org.alberta.poker.Hand player_c7_iteration = new org.alberta.poker.Hand(board_iteration);

                player_c7_iteration.addCard(hole_cards.get(p).getCard(1));
                player_c7_iteration.addCard(hole_cards.get(p).getCard(2));

                cards7_iteration.put(p, player_c7_iteration);
            }

            org.alberta.poker.Hand best = cards7_iteration.get(resisten.get(0));

            boolean tie = false;

            for (Player p : resisten) {

                if (cards7_iteration.get(p) != best) {
                    int compare = Bot.HANDEVALUATOR.compareHands(cards7_iteration.get(p), best);

                    if (compare == 1) {
                        // Nuevo mejor: el empate previo era contra una mano ya
                        // superada, así que deja de contar. Sin este reset el
                        // flag (global a la iteración) quedaría pegado a true y
                        // el ganador único final se registraría como empate.
                        best = cards7_iteration.get(p);
                        tie = false;
                    } else if (compare == 0) {
                        tie = true;
                    }
                }
            }

            for (Player p : resisten) {

                Integer[] stats_jugador = stats.get(p);

                if (Bot.HANDEVALUATOR.compareHands(cards7_iteration.get(p), best) == 0) {
                    if (tie) {
                        stats_jugador[3]++;
                        stats.put(p, stats_jugador);
                    } else {
                        stats_jugador[1]++;
                        stats.put(p, stats_jugador);
                    }
                } else {
                    stats_jugador[2]++;
                    stats.put(p, stats_jugador);
                }
            }

        }

        return stats;

    }

    public java.util.ArrayList<Player> getAnilloCriptografico() {
        java.util.ArrayList<Player> ring = new java.util.ArrayList<>();
        for (Player jugador : GameFrame.getInstance().getJugadores()) {
            // Incluimos a todos (activos, espectadores, server, desconectados).
            // Excluimos solo a los que entran a la mesa en caliente (calentando) durante un recover.
            if (!jugador.isCalentando()) {
                ring.add(jugador);
            }
        }
        // Sort alphabetically to guarantee identical mathematical positions across all clients
        java.util.Collections.sort(ring, (p1, p2) -> p1.getNickname().compareTo(p2.getNickname()));
        return ring;
    }

    // --- DealerView contract additions (read-only views over GameFrame and BOT_COMMUNITY_CARDS) ---

    @Override
    public java.util.List<? extends Player> getPlayersInSeatingOrder() {
        return GameFrame.getInstance().getJugadores();
    }

    @Override
    public int getBoardSize() {
        return Bot.BOT_COMMUNITY_CARDS.size();
    }

    @Override
    public int getBoardCardIndex(int i) {
        if (i < 0 || i >= Bot.BOT_COMMUNITY_CARDS.size()) {
            return -1;
        }
        return Bot.BOT_COMMUNITY_CARDS.getCard(i + 1).getIndex();
    }

}
