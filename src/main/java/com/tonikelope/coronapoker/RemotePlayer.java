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

import static com.tonikelope.coronapoker.GameFrame.GUI_RENDER_WAIT;
import static com.tonikelope.coronapoker.GameFrame.NOTIFY_INGAME_GIF_REPEAT;
import static com.tonikelope.coronapoker.GameFrame.TTS_NO_SOUND_TIMEOUT;
import static com.tonikelope.coronapoker.GifLabel.GIF_BARRIER_TIMEOUT;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.net.URL;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.LineBorder;

/**
 *
 * @author tonikelope
 */
public class RemotePlayer extends JPanel implements ZoomableInterface, Player {

    public static String[][] getActionsLabels() {
        return new String[][]{
            new String[]{Translator.translate("action.label.fold2")},
            new String[]{Translator.translate("action.label.check2"), Translator.translate("action.label.call2")},
            new String[]{Translator.translate("action.label.bet2"), Translator.translate("action.label.raise2")},
            new String[]{Translator.translate("action.label.allin")}
        };
    }

    public static volatile String[][] ACTIONS_LABELS = getActionsLabels();
    public static final Color[][] ACTIONS_COLORS = new Color[][]{new Color[]{Color.GRAY, Color.WHITE}, new Color[]{Color.WHITE, Color.BLACK}, new Color[]{Color.YELLOW, Color.BLACK}, new Color[]{Color.BLACK, Color.WHITE}};
    public static final int MIN_ACTION_WIDTH = 200;
    public static final int MIN_ACTION_HEIGHT = 45;

    private volatile String nickname;
    private volatile double stack = 0;
    private volatile int buyin = GameFrame.BUYIN;
    private volatile double bet = 0;
    private volatile int decision = Player.NODEC;
    private volatile boolean utg = false;
    private volatile boolean spectator = false;
    private volatile double pagar = 0;
    // Línea base de 'pagar' al empezar la CARA actual del run-it-twice (0 en
    // CARA-A, el total de CARA-A al entrar en CARA-B). El dinero ganado en la
    // cara es 'pagar - pagar_face_base', derivado de la única contabilidad real
    // (pagar), así que no puede desincronizarse. Fuera de RIT no se usa.
    private volatile double pagar_face_base = 0;
    private volatile double bote = 0;
    private volatile Double last_bote = null;
    private volatile boolean exit = false;
    private volatile Timer auto_action = null;
    private volatile boolean timeout = false;
    private volatile boolean winner = false;
    private volatile boolean loser = false;
    // Showdown (RESALTAR_JUGADA_PERDEDOR): cartas de la jugada de ESTE perdedor (sin kickers) a
    // resaltar al pasar el ratón por su etiqueta; null si no mostró. Los tres snapshot_ guardan
    // el estado a restaurar al salir el ratón: el enfoque de cada carta de la mesa antes del
    // hover (el resaltado del ganador vuelve tal cual) y el color de fondo/texto de la etiqueta.
    // Se manipulan solo en el EDT (dentro de Helpers.GUIRun).
    private volatile java.util.List<Card> showdown_hand_cards = null;
    private java.util.Map<Card, Boolean> showdown_focus_snapshot = null;
    private Color showdown_action_bg_snapshot = null;
    private Color showdown_action_fg_snapshot = null;
    private volatile double call_required;
    private volatile boolean turno = false;
    private volatile Bot bot = null;
    private volatile int response_counter;
    private volatile boolean spectator_bb = false;
    private volatile Color border_color = null;
    private volatile boolean player_stack_click = false;
    private volatile String player_action_icon = null;
    private volatile Timer icon_zoom_timer = null;
    private volatile Timer iwtsth_blink_timer = null;
    private volatile Timer rebuy_countdown_timer = null;
    // Cinemática de bet/call: el hilo de la acción espera SOLO a que la ficha despegue
    // (frame 32 del GIF), no a que el GIF entero termine. Lo cuenta atrás el addAudio al
    // lanzar la ficha; awaitChipLaunch lo espera con tope.
    private volatile CountDownLatch chip_launch_latch = null;
    private volatile String rebuy_countdown_saved_text = null;
    // Overlay del GIF de barajado (pequeño, MUDO, en bucle) + borde blanco de resaltado sobre
    // este jugador mientras procesa SU paso de la cascada SRA. Sincronizado en TODOS los peers:
    // el host difunde SHUFFLE_TURN y el controlador de GameFrame (onShuffleTurn) invoca show/hide
    // sobre el jugador de turno. Sin audio y sin barrier: puro indicador visual. El controlador
    // serializa los turnos (un overlay a la vez, con duración mínima), así que aquí no hace falta
    // 'generation'. El ImageIcon se decodifica una vez por instancia (cache-busted) y se reutiliza
    // (setIcon lo rebobina); se recarga si cambia la baraja.
    private final GifLabel shuffle_cascade_gif_label = new GifLabel();
    private volatile ImageIcon shuffle_cascade_icon = null;
    private volatile int shuffle_cascade_frames = 0;
    private volatile String shuffle_cascade_icon_url = null;
    // Color del borde guardado antes de ponerlo blanco (turno de cascada), para restaurarlo.
    private volatile Color shuffle_border_saved = null;
    private volatile boolean shuffle_border_active = false;
    // GIF de game over sobre las cartas del arruinado mientras decide la
    // recompra (solo modo CINEMATICAS). Label dedicada (capa 1001, debajo del
    // chat_notify_label): un meme del chat se pinta encima y al ocultarse el
    // game over sigue debajo, sin pelear por el ownership del notify.
    private final GifLabel rebuy_gif_label = new GifLabel();
    // Generación del visual de rebuy: invalida el swap al GIF de cero si el
    // rebuy se resolvió mientras el de cuenta atrás aún corría. Solo se
    // escribe en el EDT.
    private volatile int rebuy_generation = 0;
    // Marcador de activación del visual de rebuy (EDT-confined): hace
    // idempotentes setRebuying(true)/setRebuying(false) en ambos modos.
    private boolean rebuying_visual = false;
    // Nº de arruinados mostrando el GIF de game over AHORA (EDT-confined,
    // compartido entre todos los RemotePlayer): con varios simultáneos suena
    // UN solo game_over.wav (lo engancha el primero) y se corta cuando el
    // último se resuelve.
    private static int REBUY_GIF_ACTIVOS = 0;
    // Cuando el JUGADOR LOCAL también está en game over con su propio
    // GameOverDialog interactivo (que ya reproduce el game_over.wav de cuenta
    // atrás "para él"), los GIF de rebuy de los arruinados remotos arrancan a
    // la vez sobre la mesa viva pero deben quedarse MUDOS: el diálogo local es
    // el dueño del audio y lo corta en cuanto el local decide. Lo gobierna el
    // Crupier alrededor del game over local interactivo. volatile: lo escribe
    // el hilo del crupier y lo lee el EDT en mostrarRebuyGameOverGif.
    public static volatile boolean LOCAL_GAMEOVER_OWNS_AUDIO = false;
    private volatile boolean notify_blocked = false;
    private volatile URL chat_notify_image_url = null;
    private volatile Long chat_notify_thread = null;
    private final Object zoom_lock = new Object();
    private final GifLabel chat_notify_label = new GifLabel();
    private final JLabel chip_label = new JLabel();
    private final JLabel sec_pot_win_label = new JLabel();
    private final ConcurrentLinkedQueue<Integer> botes_secundarios = new ConcurrentLinkedQueue<>();
    private volatile boolean raise;
    private volatile boolean reraise;
    private volatile boolean muestra = false;
    private volatile int conta_win = 0;

    private volatile Font orig_action_font = null;
    private volatile float border_size = Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP);
    private volatile float arc = Player.ARC * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP);
    // Cached BasicStroke for paintBorder; rebuilt only when border_size changes (zoom).
    private float cached_stroke_size = -1f;
    private BasicStroke cached_stroke = null;

    @Override
    public void stopActionTimer() {
        Helpers.GUIRun(() -> {
            if (auto_action != null && auto_action.isRunning()) {
                auto_action.stop();
            }
            // NO matar icon_zoom_timer aquí — entre manos / al inicio del
            // recover dejaba la siguiente mano sin setAvatar y el avatar
            // quedaba invisible. Revertido el cambio b173ccf9.
        });
    }

    public void updateLatency(String latency, boolean error) {
        Helpers.GUIRun(() -> {
            latency_label.setBackground(error ? Color.RED : Color.BLUE);
            latency_label.setText(latency);
        });
    }

    public JLabel getLatency_label() {
        return latency_label;
    }

    // Telemetría: el widget LatencyDot lo coloca el autor en el
    // .form (NetBeans visual editor) y lo enlaza llamando setLatencyDot
    // en el constructor tras initComponents(). Si null → applyTelemetry
    // es no-op silencioso (telemetría no afecta al game flow).
    private volatile LatencyDot latency_dot = null;

    public LatencyDot getLatencyDot() {
        return latency_dot;
    }

    public void setLatencyDot(LatencyDot dot) {
        this.latency_dot = dot;
    }

    /**
     * Telemetría: actualiza la bolita con la última snapshot recibida
     * del broadcast TELEMETRY del host. Si lat1 y lat2 ambos válidos, usa el
     * min. Si uno es -1, usa el otro. Si ambos -1, -1 → bolita roja.
     * No-op si latency_dot aún no se ha enlazado vía setLatencyDot.
     */
    public void applyTelemetry(int lat1, int lat2, int reconnectionCount) {
        LatencyDot dot = this.latency_dot;
        if (dot == null) {
            return;
        }
        int best;
        if (lat1 < 0 && lat2 < 0) {
            best = -1;
        } else if (lat1 < 0) {
            best = lat2;
        } else if (lat2 < 0) {
            best = lat1;
        } else {
            best = Math.min(lat1, lat2);
        }
        dot.setLatency(best, reconnectionCount);
    }

    // El asiento tiene esquinas REDONDEADAS: si fuese realmente opaco, Swing no
    // repintaría el fondo (tapete) detrás y las esquinas —fuera del arco— mostrarían
    // basura ("el fondo sale mal en las esquinas"). Por eso el asiento NUNCA es opaco
    // de verdad: interceptamos setOpaque y recordamos la INTENCIÓN de relleno, que
    // pintamos nosotros (rounded) en paintComponent; Swing repinta la madera detrás y
    // las esquinas quedan limpias. Solo afecta al estado resaltado (eliminado, rojo),
    // que es estático → sin coste de rendimiento.
    private volatile boolean rounded_fill = false;

    @Override
    public void setOpaque(boolean isOpaque) {
        this.rounded_fill = isOpaque;
        super.setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {

        if (rounded_fill) {
            Graphics2D g2d = (Graphics2D) g.create();
            try {
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(getBackground());
                g2d.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), arc, arc));
            } finally {
                g2d.dispose();
            }
            // super.paintComponent NO se llama: Swing ya ha repintado la madera detrás
            // (el asiento es no-opaco) y el relleno redondeado va encima; llamar a
            // super pintaría un fondo rectangular por debajo.
        } else {
            super.paintComponent(g);
        }
    }

    private BasicStroke borderStroke() {
        if (cached_stroke == null || cached_stroke_size != border_size) {
            cached_stroke = new BasicStroke(border_size);
            cached_stroke_size = border_size;
        }
        return cached_stroke;
    }

    @Override
    protected void paintBorder(Graphics g) {

        Graphics2D g2d = (Graphics2D) g.create();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(border_color);
            g2d.setStroke(borderStroke());
            g2d.draw(new RoundRectangle2D.Double(
                    border_size / 2.0,
                    border_size / 2.0,
                    getWidth() - border_size,
                    getHeight() - border_size,
                    arc,
                    arc
            ));
        } finally {
            g2d.dispose();
        }
    }

    public void refreshNotifyChatLabel() {

        Helpers.GUIRun(() -> {
            if (getChat_notify_label().isVisible()) {
                Helpers.threadRun(() -> {
                    if (chat_notify_image_url == null) {
                        setNotifyTTSChatLabel();
                    }
                });
            }
        });

    }

    // Recoloca/redimensiona el GIF de game over del rebuy si está visible.
    // Lo llaman vistaCompacta y el icon_zoom_timer tras un resize (vista
    // compacta, zoom), igual que refreshNotifyChatLabel para los GIFs del
    // chat: este GIF dura toda la decisión del arruinado y sin esto se
    // quedaría con la geometría del momento del show. GifLabel estira la
    // Image a los bounds en el paint (GPU), así que basta recalcular bounds
    // con el MISMO cálculo del show — sin recargar el icono ni tocar la
    // animación en curso.
    public void refreshRebuyGifLabel() {

        Helpers.GUIRun(() -> {
            if (!rebuy_gif_label.isVisible() || !(rebuy_gif_label.getIcon() instanceof ImageIcon)) {
                return;
            }

            ImageIcon icon = (ImageIcon) rebuy_gif_label.getIcon();

            if (icon.getIconWidth() <= 0 || icon.getIconHeight() <= 0) {
                return;
            }

            int max_width = panel_cartas.getWidth();
            int new_height = panel_cartas.getHeight();
            int new_width = (int) Math.round((icon.getIconWidth() * new_height) / icon.getIconHeight());

            if (new_width > max_width) {
                new_height = (int) Math.round((new_height * max_width) / new_width);
                new_width = max_width;
            }

            rebuy_gif_label.setSize(new_width, new_height);
            rebuy_gif_label.setPreferredSize(rebuy_gif_label.getSize());
            rebuy_gif_label.setLocation(Math.round((panel_cartas.getWidth() - new_width) / 2), Math.round((getHoleCard1().getHeight() - new_height) / 2));
            rebuy_gif_label.repaint();
        });

    }

    @Override
    public boolean isMuestra() {
        return muestra;
    }

    @Override
    public void setNotifyTTSChatLabel() {

        chat_notify_image_url = null;

        synchronized (getChat_notify_label()) {

            getChat_notify_label().notifyAll();
        }

        int sound_icon_size_h = getHoleCard1().getHeight();

        int sound_icon_size_w = Math.round((596 * sound_icon_size_h) / 460);

        ImageIcon image = new ImageIcon(new ImageIcon(getClass().getResource("/images/talk.png")).getImage().getScaledInstance(sound_icon_size_w, sound_icon_size_h, Image.SCALE_SMOOTH));

        Helpers.GUIRun(() -> {

            int pos_x = Math.round((panel_cartas.getWidth() - sound_icon_size_w) / 2);

            int pos_y = 0;

            getChat_notify_label().setIcon(image);

            getChat_notify_label().setSize(sound_icon_size_w, sound_icon_size_h);

            getChat_notify_label().setPreferredSize(getChat_notify_label().getSize());

            getChat_notify_label().setOpaque(false);

            getChat_notify_label().setLocation(pos_x, pos_y);

        });
    }

    public void setNotifyRabbitLabel() {

        chat_notify_image_url = null;

        synchronized (getChat_notify_label()) {

            getChat_notify_label().notifyAll();
        }

        int icon_size_h = getHoleCard1().getHeight();

        int icon_size_w = Math.round((484 * icon_size_h) / 556);

        ImageIcon image = new ImageIcon(new ImageIcon(getClass().getResource("/images/bugs_notify.png")).getImage().getScaledInstance(icon_size_w, icon_size_h, Image.SCALE_SMOOTH));

        Helpers.GUIRun(() -> {

            int pos_x = Math.round((panel_cartas.getWidth() - icon_size_w) / 2);

            int pos_y = 0;

            getChat_notify_label().setIcon(image);

            getChat_notify_label().setSize(icon_size_w, icon_size_h);

            getChat_notify_label().setPreferredSize(getChat_notify_label().getSize());

            getChat_notify_label().setOpaque(false);

            getChat_notify_label().setLocation(pos_x, pos_y);

        });
    }

    private boolean isActionGif(URL u) {

        String[] gif_actions = new String[]{"check", "fold1", "fold2", "fold3", "bet1", "bet2", "bet3", "bet4", "call1", "call2", "call3", "call4"};

        for (String gif : gif_actions) {
            if (getClass().getResource("/images/gif_actions/" + gif + ".gif").equals(u)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void setNotifyImageChatLabel(URL u) {
        setNotifyImageChatLabel(u, true);
    }

    // caller_awaits: si true (fold y pure-check) el hilo de la acción espera en la
    // barrera a que el GIF entero termine (3 partes). Si false (bet y call con dinero)
    // NO: la ficha vuela igual en su frame (addAudio) pero la acción solo espera a que
    // DESPEGUE (chip_launch_latch); el GIF se desmonta solo (2 partes: setup + fin-de-GIF)
    // y sus frames restantes se reproducen aparte.
    private void setNotifyImageChatLabel(URL u, boolean caller_awaits) {

        // Cualquier notify (este o uno que SUPERSEDE a un GIF de bet/call en vuelo) libera
        // el latch pendiente: si la cinemática anterior se desmonta antes de su frame 32, su
        // hilo de accion no se queda esperando (lo hacia hasta 5 s). El latch de ESTA llamada
        // se arma DESPUES (abajo), asi que esto solo afecta a una accion previa.
        signalChipLaunched();

        if (!this.isNotify_blocked()) {

            try {

                chat_notify_image_url = u;

                final boolean action_gif = isActionGif(u);

                // bet/call (caller_awaits=false): arma el latch que su hilo esperara hasta que
                // la ficha despegue en el frame 32 (lo cuenta atras el addAudio). Tras soltar
                // el anterior, evita la ventana de fuga al supersederse antes del frame 32.
                if (!caller_awaits && action_gif) {
                    chip_launch_latch = new CountDownLatch(1);
                }

                final boolean isgif = (action_gif || ChatImageDialog.GIF_CACHE.containsKey(u.toString()) || Helpers.isImageGIF(u));

                final CyclicBarrier gif_barrier = new CyclicBarrier((action_gif && caller_awaits) ? 3 : 2);

                getChat_notify_label().setBarrier(gif_barrier);

                Helpers.threadRun(() -> {

                    synchronized (getChat_notify_label()) {

                        chat_notify_thread = Thread.currentThread().threadId(); //Nos hacemos con la propiedad del icono de notificación y avisamos a algún hilo que estuviera manipulándolo

                        getChat_notify_label().notifyAll();

                        try {

                            final ImageIcon orig = action_gif ? new ImageIcon(u) : ImageCacheManager.getIcon(new URL(u.toString() + "#" + String.valueOf(System.currentTimeMillis())));

                            while (orig.getIconHeight() == 0 || orig.getIconWidth() == 0) {

                                Helpers.pausar(GUI_RENDER_WAIT);
                            }

                            int max_width = Math.max(panel_cartas.getWidth(), orig.getIconWidth());

                            int max_height = Math.max(panel_cartas.getHeight(), panel_cartas.getHeight());

                            int new_height = max_height;

                            int new_width = (int) Math.round((orig.getIconWidth() * max_height) / orig.getIconHeight());

                            if (new_width > max_width) {

                                new_height = (int) Math.round((new_height * max_width) / new_width);

                                new_width = max_width;
                            }

                            ImageIcon image = new ImageIcon(orig.getImage().getScaledInstance(new_width, new_height, isgif ? Image.SCALE_DEFAULT : Image.SCALE_SMOOTH));

                            int pos_x = Math.round((panel_cartas.getWidth() - image.getIconWidth()) / 2);

                            int pos_y = Math.round((getHoleCard1().getHeight() - image.getIconHeight()) / 2);

                            int gif_frames_count = isgif ? Helpers.getGIFFramesCount(u) : 0;

                            Helpers.GUIRun(() -> {

                                if (isgif) {
                                    getChat_notify_label().setIcon(image, gif_frames_count);
                                } else {
                                    getChat_notify_label().setIcon(image);
                                }

                                getChat_notify_label().setRepeat(action_gif ? 1 : NOTIFY_INGAME_GIF_REPEAT);

                                if (action_gif) {

                                    /*Estos audios no son obligatorios para todas las acciones 
                                        Se meten en la propia label con addaudio para sincronizar cuando es necesario que el 
                                        audio empiece y acabe en un determinado frame exacto del gif. (El hilo que reproducirá este audio NO espera en la barrera) */
                                    if (getDecision() == Player.BET) {
                                        // La ficha vuela en este frame (gesto + sonido sincronizados,
                                        // INTACTO). signalChipLaunched suelta el hilo de la acción:
                                        // cierra la acción y commitea el bote mientras la ficha vuela,
                                        // así al aterrizar pot+stack+bet ruedan juntos (true).
                                        // El sonido de apuesta se puede desactivar, pero el callback
                                        // (lanzar la ficha al bote + soltar el hilo de la acción) DEBE
                                        // seguir sincronizado al frame 32: por eso audio null si está off.
                                        getChat_notify_label().addAudio(GameFrame.apuestaSonidoOn() ? "misc/bet.wav" : null, 32, 60, () -> {
                                            GameFrame.getInstance().getCrupier().launchChipToPot(this);
                                            signalChipLaunched();
                                        });
                                    } else if (getDecision() == Player.CHECK && Helpers.doubleSecureCompare(0f, call_required) < 0) {
                                        // Sonido de igualar desactivable, pero el callback (ficha al bote +
                                        // soltar el hilo) DEBE seguir atado al frame 32: audio null si off.
                                        getChat_notify_label().addAudio(GameFrame.igualarSonidoOn() ? "misc/call.wav" : null, 32, 60, () -> {
                                            GameFrame.getInstance().getCrupier().launchChipToPot(this);
                                            signalChipLaunched();
                                        });
                                    } else if (getDecision() == Player.CHECK) {
                                        getChat_notify_label().addAudio(GameFrame.pasarSonidoOn() ? "misc/check.wav" : null, 5, 14);
                                    }
                                }

                                getChat_notify_label().setSize(image.getIconWidth(), image.getIconHeight());
                                getChat_notify_label().setPreferredSize(getChat_notify_label().getSize());
                                getChat_notify_label().setOpaque(false);
                                getChat_notify_label().setLocation(pos_x, pos_y);
                                getChat_notify_label().setVisible(true);
                            });

                        } catch (Exception ex) {
                            Logger.getLogger(RemotePlayer.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }

                    if (isgif) {

                        try {
                            gif_barrier.await(GIF_BARRIER_TIMEOUT, TimeUnit.SECONDS);
                        } catch (InterruptedException | java.util.concurrent.BrokenBarrierException ex) {
                            Thread.currentThread().interrupt();
                            // Expected during pool shutdown — chat-image GIF
                            // barrier cancelled cooperatively.
                            Logger.getLogger(GifAnimationDialog.class.getName()).log(Level.INFO,
                                    "GIF barrier cancelled (cooperative cancellation)");
                        } catch (java.util.concurrent.TimeoutException ex) {
                            // The notify was superseded (or its GIF torn down) before
                            // the rendezvous completed: non-fatal, the label is hidden
                            // by whoever owns it now. Not an interrupt.
                            Logger.getLogger(GifAnimationDialog.class.getName()).log(Level.INFO,
                                    "GIF barrier timed out (superseded notify — cooperative cancellation)");
                        } catch (Exception ex) {
                            Logger.getLogger(GifAnimationDialog.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    } else {
                        synchronized (getChat_notify_label()) {
                            if (Thread.currentThread().threadId() == chat_notify_thread) {
                                try {
                                    getChat_notify_label().wait(TTS_NO_SOUND_TIMEOUT);
                                } catch (InterruptedException ex) {
                                    Thread.currentThread().interrupt();
                                    // Expected during pool shutdown.
                                    Logger.getLogger(GifAnimationDialog.class.getName()).log(Level.INFO,
                                            "Chat notify wait interrupted (cooperative cancellation)");
                                }
                            }
                        }
                    }
                    synchronized (getChat_notify_label()) {
                        if (Thread.currentThread().threadId() == chat_notify_thread) {
                            Helpers.GUIRun(() -> {
                                getChat_notify_label().setVisible(false);
                            });
                        }
                    }
                });

            } catch (Exception ex) {
                Logger.getLogger(RemotePlayer.class.getName()).log(Level.SEVERE, null, ex);
            }

        }

    }

    public void refreshSecPotLabel() {

        // En run-it-twice la franja es POR CARA: cada cara reparte la MITAD del
        // bote, así que muestra el dinero ganado en ELLA (pagar - pagar_face_base)
        // y el beneficio contra la mitad del bote. Fuera de RIT (tag null) →
        // pagar y bote enteros, como siempre.
        final boolean is_rit = GameFrame.getInstance().getCrupier().getRitPotBoardTag() != null;

        final double fullbote = last_bote != null ? last_bote : bote;

        final double mibote = is_rit ? Crupier.splitPotForRunItTwice(fullbote)[0] : fullbote;

        final double dinero = is_rit ? Helpers.doubleClean(pagar - pagar_face_base) : pagar;

        if (Helpers.doubleSecureCompare(0f, dinero) < 0 && GameFrame.getInstance().getCrupier().getBote().getSide_pot_count() > 0) {

            Helpers.GUIRun(() -> {
                sec_pot_win_label.setBackground(Color.BLACK);

                sec_pot_win_label.setForeground(Color.WHITE);

                sec_pot_win_label.setSize(player_action.getSize());

                sec_pot_win_label.setPreferredSize(sec_pot_win_label.getSize());

                int pos_x = Math.round((panel_cartas.getWidth() - sec_pot_win_label.getWidth()) / 2);

                int pos_y = Math.round(GameFrame.VISTA_COMPACTA > 0 ? (getHoleCard1().getHeight() - sec_pot_win_label.getHeight()) : ((getHoleCard1().getHeight() - sec_pot_win_label.getHeight()) / 2));

                sec_pot_win_label.setLocation(pos_x, pos_y);

                String[] botes = new String[botes_secundarios.size()];

                int i = 0;

                for (Integer b : botes_secundarios) {
                    botes[i++] = "#" + String.valueOf(b);
                }

                sec_pot_win_label.setText(String.join("+", botes) + " = " + Helpers.money2String(dinero) + " (" + Helpers.money2String(dinero - mibote) + ")");

                sec_pot_win_label.setVisible(true);
            });

        }

    }

    public boolean isNotify_blocked() {
        return notify_blocked;
    }

    public JLabel getChip_label() {
        return chip_label;
    }

    @Override
    public GifLabel getChat_notify_label() {
        return chat_notify_label;
    }

    public JLayeredPane getPanel_cartas() {
        return panel_cartas;
    }

    // La ficha remota reposa en la esquina superior-izquierda de panel_cartas
    // (mismo anclaje que refreshPositionChipIcons): (0, 0). Devuelve su centro
    // en pantalla, o null si el asiento no está visible.
    @Override
    public java.awt.geom.Point2D getPositionChipScreenCenter(int chip_w, int chip_h) {
        if (panel_cartas == null || !panel_cartas.isShowing()) {
            return null;
        }
        java.awt.Point tl = new java.awt.Point(0, 0);
        javax.swing.SwingUtilities.convertPointToScreen(tl, panel_cartas);
        return new java.awt.geom.Point2D.Double(tl.getX() + chip_w / 2.0, tl.getY() + chip_h / 2.0);
    }

    @Override
    public boolean isTimeout() {
        return timeout;
    }

    private void setPlayerBorder(Color color) {

        if (!timeout) {
            border_color = color;
        }

        repaint();

    }

    @Override
    public int getResponseTime() {

        return GameFrame.THINK_TIME - response_counter;
    }

    public Bot getBot() {
        return bot;
    }

    @Override
    public boolean isTurno() {
        return turno;
    }

    @Override
    public void refreshPos() {

        if (this.isActivo()) {

            this.bote = 0f;

            if (Helpers.doubleSecureCompare(0f, this.bet) < 0) {
                setStack(this.stack + this.bet);
            }

            this.bet = 0f;

            if (this.nickname.equals(GameFrame.getInstance().getCrupier().getBb_nick())) {
                this.setPosition(BIG_BLIND);
            } else if (this.nickname.equals(GameFrame.getInstance().getCrupier().getSb_nick())) {
                this.setPosition(SMALL_BLIND);
            } else if (this.nickname.equals(GameFrame.getInstance().getCrupier().getDealer_nick())) {
                this.setPosition(DEALER);
            } else {
                this.setPosition(-1);
            }

            if (this.nickname.equals(GameFrame.getInstance().getCrupier().getUtg_nick())) {
                this.setUTG();
            } else {
                this.disableUTG();
            }
        }
    }

    @Override
    public boolean isWinner() {
        return winner;
    }

    public boolean isLoser() {
        return loser;
    }

    public JLabel getAvatar() {
        return avatar;
    }

    @Override
    public int getBuyin() {
        return buyin;
    }

    @Override
    public boolean isExit() {
        return exit;
    }

    @Override
    public void setExit() {

        if (!this.exit) {
            this.exit = true;
            this.timeout = false;

            Helpers.GUIRun(() -> {
                if (auto_action != null) {
                    auto_action.stop();
                }

                setPlayerBorder(new Color(204, 204, 204, 75));

                // Preserve the hole-card state the peer had at the moment of
                // leaving: if they were still active in the hand, the cards are
                // face-down (tapadas, visible_card=true) and stay that way as a
                // visual cue that they had a hand; if they had already folded,
                // fold() set visible_card=false and they remain hidden. A
                // resetearCarta() call here would flatten both cases to an empty
                // slot. The next-hand board reset purges everything anyway.

                setActionBackground(new Color(255, 102, 0));
                player_action.setForeground(Color.WHITE);
                setActionTextFitted(Translator.translate("ui.se_pira"));
                setPlayerActionIcon("exit.png");
                player_action.setVisible(true);

                chip_label.setVisible(false);
                sec_pot_win_label.setVisible(false);
            });

        }

    }

    @Override
    public double getPagar() {
        return pagar;
    }

    @Override
    public double getBote() {
        return bote;
    }

    // Rodaje vivo del label del stack (EDT-confined). El render solo escribe el
    // texto; el color lo siguen poniendo setStack/setStackDisplay. Creación perezosa
    // (player_stack ya existe en el primer uso, siempre en el EDT).
    private RollingCounter stack_roller;

    private RollingCounter stackRoller() {
        if (stack_roller == null) {
            stack_roller = new RollingCounter((v) -> player_stack.setText(Helpers.money2String(v)),
                    GameFrame.COUNTER_ROLL_SPEED, GameFrame.COUNTER_ROLL_MIN_MS, GameFrame.COUNTER_ROLL_MAX_MS);
        }
        return stack_roller;
    }

    @Override
    public synchronized void setStack(double stack) {
        this.stack = Helpers.doubleClean(stack);

        if (!player_stack_click) {
            Helpers.GUIRunAndWait(() -> {
                if (GameFrame.hasRebought(nickname)) {
                    setPlayerStackBackground(Color.CYAN);

                    player_stack.setForeground(Color.BLACK);
                } else {

                    setPlayerStackBackground(new Color(51, 153, 0));

                    player_stack.setForeground(Color.WHITE);
                }

                // Rueda el número hasta el nuevo stack (velocidad constante; off/recover
                // salta). Si la acción va a volar una ficha (defer_counter_rolls), NO se
                // rueda aquí: el label se queda en su valor previo y rollCountersToModel lo
                // rueda al aterrizar la ficha, a la vez que el bote y la apuesta.
                if (!defer_counter_rolls) {
                    stackRoller().roll(stack, GameFrame.isCounterRollEnabled());
                }
            });
        }
    }

    // Pinta SOLO el label del stack con 'value' (sin tocar el modelo ni el bote):
    // lo usa el contador animado de llenado de stacks (apertura/recompra) para
    // rodar el numero frame a frame. NO sincronizado a proposito: corre en el EDT
    // (lo invoca el Timer del contador) y el caller que difiere el modelo puede
    // tener tomado el monitor del jugador -> sincronizar aqui colgaria. Respeta el
    // override de "ver buy-in" (player_stack_click) igual que setStack.
    @Override
    public void setStackDisplay(double value) {
        if (player_stack_click) {
            return;
        }
        Helpers.GUIRun(() -> {
            if (GameFrame.hasRebought(nickname)) {
                setPlayerStackBackground(Color.CYAN);
                player_stack.setForeground(Color.BLACK);
            } else {
                setPlayerStackBackground(new Color(51, 153, 0));
                player_stack.setForeground(Color.WHITE);
            }
            // De golpe (la cortinilla ya anima frame a frame); mantiene sincronizado el
            // valor mostrado del roller para que el siguiente roll vivo arranque bien.
            stackRoller().set(value);
        });
    }

    // Rodaje vivo del label de la apuesta del jugador (player_pot = 'bote', su aporte
    // acumulado de la mano). El render muestra "----" cuando es 0. EDT-confined.
    private RollingCounter bet_roller;

    private RollingCounter betRoller() {
        if (bet_roller == null) {
            bet_roller = new RollingCounter(
                    (v) -> player_pot.setText(Helpers.doubleSecureCompare(0f, v) < 0 ? Helpers.money2String(v) : "----"),
                    GameFrame.COUNTER_ROLL_SPEED, GameFrame.COUNTER_ROLL_MIN_MS, GameFrame.COUNTER_ROLL_MAX_MS);
        }
        return bet_roller;
    }

    // Flag del aplazamiento del rodaje vivo: lo activa el handler de la acción ANTES de
    // setBet cuando va a volar una ficha, para que el stack/bet no se adelanten a ella.
    // volatile: lo escribe el hilo de la acción y lo leen setStack/setBet (en el EDT) y
    // rollCountersToModel (en el aterrizaje).
    private volatile boolean defer_counter_rolls = false;

    @Override
    public void setCounterRollDeferred(boolean deferred) {
        this.defer_counter_rolls = deferred;
    }

    @Override
    public void rollCountersToModel() {
        Helpers.GUIRun(() -> {
            this.defer_counter_rolls = false;
            stackRoller().roll(this.stack, GameFrame.isCounterRollEnabled());
            betRoller().roll(this.bote, GameFrame.isCounterRollEnabled());
        });
    }

    @Override
    public synchronized void setBet(double new_bet) {

        double old_bet = bet;

        bet = Helpers.doubleClean(new_bet);

        if (Helpers.doubleSecureCompare(old_bet, bet) < 0) {
            this.bote += Helpers.doubleClean(bet - old_bet);
            setStack(stack - (bet - old_bet));
        }

        GameFrame.getInstance().getCrupier().getBote().addPlayer(this);

        Helpers.GUIRunAndWait(() -> {
            // Si la acción va a volar ficha (defer), NO se rueda aquí: el bet se queda y
            // rollCountersToModel lo rueda al aterrizar, a la vez que el stack y el bote.
            if (!defer_counter_rolls) {
                betRoller().roll(bote, GameFrame.isCounterRollEnabled());
            }
        });

    }

    public synchronized double postAnte(double ante) {

        if (Helpers.doubleSecureCompare(0f, stack) >= 0) {
            return 0f; // ya all-in / sin fichas: nada que antear
        }

        double real;

        if (Helpers.doubleSecureCompare(ante, stack) < 0) {
            real = Helpers.doubleClean(ante);
        } else {
            // No cubre el ante completo: all-in por el ante.
            real = Helpers.doubleClean(stack);
            setDecision(Player.ALLIN);
        }

        this.bote += real;
        setStack(stack - real);

        GameFrame.getInstance().getCrupier().getBote().addPlayer(this);

        Helpers.GUIRunAndWait(() -> {
            // Si la ficha del ante volará al bote (defer), NO rueda aquí: se difiere y
            // rollCountersToModel lo rueda al aterrizar, a la vez que el stack y el bote.
            if (!defer_counter_rolls) {
                betRoller().roll(bote, GameFrame.isCounterRollEnabled());
            }
        });

        return real;
    }

    public synchronized double postStraddle(double amount) {

        double want = Helpers.doubleClean(amount);

        if (Helpers.doubleSecureCompare(want, stack) < 0) {
            setBet(want);
            return want;
        }

        // No cubre el straddle completo: all-in por el straddle.
        double all = Helpers.doubleClean(stack);
        setBet(all);
        setDecision(Player.ALLIN);
        return all;
    }

    @Override
    public void esTuTurno() {
        // Gate del llenado de stacks: si este jugador esta a medio llenar su stack (apertura
        // o recompra), NO activamos su turno (borde + botones) hasta que termine. El resto del
        // juego NO se ha frenado por la animacion; solo este turno espera.
        GameFrame.getInstance().getCrupier().awaitStackFillIfPending(this.nickname);
        turno = true;

        GameFrame.getInstance().getCrupier().disableAllPlayersTimeout();

        // Once setExit() has painted the orange "SE PIRA" badge, the slot must
        // stay that way until the next hand purges the board. esTuTurno fires
        // when rondaApuestas iterates to this player's slot — if a race makes
        // the peer's EXIT arrive at our lambda before main thread reaches the
        // iteration, setExit runs first and esTuTurno would then repaint the
        // orange "thinking" border + the "pensando" label over it. Bail before
        // touching any GUI so SE PIRA wins. The do-while at the caller still
        // exits cleanly because readActionFromRemotePlayer detects isExit and
        // returns a synth FOLD that triggers finTurno (no UI work needed).
        if (this.exit) {
            return;
        }

        if (this.getDecision() == Player.NODEC) {

            call_required = GameFrame.getInstance().getCrupier().getApuesta_actual() - bet;

            Helpers.GUIRun(() -> {
                setPlayerBorder(Color.ORANGE);

                setActionBackground(new Color(204, 204, 204, 75));

                player_action.setForeground(Color.LIGHT_GRAY);

                setActionTextFitted(Translator.translate("ui.pensando"));

                setPlayerActionIcon("action/thinking.png");

                // Tiempo de pensar configurable: desactivado => barra LLENA estatica (sin
                // cuenta atras). El auto-fold real lo hace el host via isExit(), no esta barra.
                if (GameFrame.THINK_TIME_ENABLED) {
                    Helpers.smoothCountdown(GameFrame.getInstance().getBarra_tiempo(), GameFrame.THINK_TIME);
                } else {
                    Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), GameFrame.THINK_TIME);
                }

            });

            if (!GameFrame.TEST_MODE) {

                //Tiempo máximo para pensar
                Helpers.GUIRun(() -> {
                    response_counter = GameFrame.THINK_TIME;
                    if (auto_action != null) {
                        auto_action.stop();
                    }

                    auto_action = new Timer(1000, new ActionListener() {
                        long t = GameFrame.getInstance().getCrupier().getTurno();

                        @Override
                        public void actionPerformed(ActionEvent ae) {

                            if (GameFrame.getInstance() != null && GameFrame.getInstance().getCrupier() != null && !GameFrame.getInstance().getCrupier().isFin_de_la_transmision() && !GameFrame.getInstance().getCrupier().isSomePlayerTimeout() && !GameFrame.getInstance().isTimba_pausada() && !WaitingRoomFrame.getInstance().isExit() && response_counter > 0 && t == GameFrame.getInstance().getCrupier().getTurno() && auto_action.isRunning() && getDecision() == Player.NODEC) {

                                // Desactivado => NO decrementa (contador congelado): la barra remota
                                // no cuenta atrás y el auto-stop por timeout no dispara; el host
                                // decide el turno del jugador remoto por su cuenta.
                                if (GameFrame.THINK_TIME_ENABLED) {
                                    response_counter--;
                                }

                                // setValue(response_counter) redundante: smoothCountdown
                                // ya repinta la barra en escala ms via Timer interno.
                                // Hacer setValue aqui en escala segundos generaba parpadeo.

                                if (GameFrame.THINK_TIME_ENABLED && response_counter == GameFrame.getHurryupThreshold() && Helpers.doubleSecureCompare(0f, call_required) < 0) {
                                    if (GameFrame.avisoTiempoSonidoOn()) {
                                        Audio.playWavResource("misc/hurryup.wav");
                                    }
                                }

                                if (GameFrame.THINK_TIME_ENABLED && response_counter == 0) {
                                    Helpers.threadRun(() -> {
                                        Audio.playWavResourceAndWait("misc/timeout.wav", true, false, !GameFrame.avisoTiempoSonidoOn());
                                        GameFrame.getInstance().checkPause();
                                        Helpers.GUIRun(() -> {
                                            if (auto_action.isRunning() && t == GameFrame.getInstance().getCrupier().getTurno()) {

                                                auto_action.stop();
                                            }
                                        });
                                    });
                                }

                            }

                            repaint();
                        }

                    });

                    auto_action.start();

                });
            }

        } else {

            finTurno();
        }

    }

    public void setDecisionFromRemotePlayer(int decision, double bet) {
        Helpers.threadRun(() -> {
            Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), GameFrame.THINK_TIME);
            Helpers.GUIRun(() -> {
                if (auto_action != null) {
                    auto_action.stop();
                }
            });

            this.decision = decision;

            switch (this.decision) {
                case Player.CHECK:
                    check();
                    break;
                case Player.FOLD:
                    fold();
                    break;
                case Player.BET:
                    bet(bet);
                    break;
                case Player.ALLIN:
                    allin();
                    break;
                default:
                    break;
            }
        });

    }

    private void setDecision(int dec) {

        this.decision = dec;

        raise = false;

        reraise = false;

        // If the peer has already left, setExit() has painted the slot orange
        // with "SE PIRA" — the player_action label, the colours, the icon, the
        // border, the chip label. The synthetic FOLD that rondaApuestas issues
        // to advance the betting loop must NOT overwrite that visual state with
        // a regular fold/check/bet decoration. Keep the internal decision in
        // sync with the betting logic (already done above) but stop here so the
        // GUI stays as setExit left it.
        if (this.exit) {
            return;
        }

        renderDecisionVisual(dec);
    }

    // Render visual de una decisión (etiqueta/borde/icono + fondos), sin mutar
    // estado. Extraído de setDecision para poder RE-PINTAR la última acción en el
    // rewind de run-it-twice (restaurar el all-in negro, etc.) sin efectos
    // colaterales (sonido, bet, finTurno) que sí tiene el flujo normal.
    private void renderDecisionVisual(int dec) {
        switch (dec) {
            case Player.CHECK:

                Helpers.GUIRun(() -> {
                    if (Helpers.doubleSecureCompare(0f, call_required) < 0) {
                        setActionTextFitted(ACTIONS_LABELS[dec - 1][1]);
                    } else {
                        setActionTextFitted(ACTIONS_LABELS[dec - 1][0]);
                    }

                    setPlayerActionIcon("action/up.png");
                });

                break;
            case Player.BET:
                Helpers.GUIRun(() -> {
                    final double apuesta_actual_snapshot = GameFrame.getInstance().getCrupier().getApuesta_actual();
                    final int conta_raise_snapshot = GameFrame.getInstance().getCrupier().getConta_raise();
                    // Lectura ÚNICA del volátil bet: guard y texto deben usar
                    // exactamente el mismo valor (ver nota en ALLIN).
                    final double bet_snapshot = bet;
                    if (Helpers.doubleSecureCompare(apuesta_actual_snapshot, bet_snapshot) < 0 && Helpers.doubleSecureCompare(0f, apuesta_actual_snapshot) < 0) {
                        setActionTextFitted((conta_raise_snapshot > 0 ? "RE" : "") + ACTIONS_LABELS[dec - 1][1] + " (+" + Helpers.money2String(bet_snapshot - apuesta_actual_snapshot) + ")");

                        raise = true;

                        reraise = (conta_raise_snapshot > 0);

                    } else {
                        setActionTextFitted(ACTIONS_LABELS[dec - 1][0] + " " + Helpers.money2String(bet_snapshot));
                    }
                    setPlayerActionIcon("action/bet.png");
                });
                break;
            case Player.ALLIN:
                Helpers.GUIRun(() -> {
                    setPlayerBorder(ACTIONS_COLORS[dec - 1][0]);

                    final double apuesta_actual_snapshot = GameFrame.getInstance().getCrupier().getApuesta_actual();
                    // Lectura ÚNICA de bet+stack para guard y texto: son
                    // volátiles y el dinero del all-in se mueve en dos pasos
                    // (bet sube, luego stack baja) en otro hilo. Con lecturas
                    // separadas el guard podía ver la suma inflada a mitad de
                    // setBet y el texto la ya asentada, colando un importe
                    // negativo en la etiqueta ("ALL IN (+-0.90)").
                    final double total_allin = bet + stack;
                    if (Helpers.doubleSecureCompare(apuesta_actual_snapshot, total_allin) < 0) {
                        setActionTextFitted(ACTIONS_LABELS[dec - 1][0] + " (+" + Helpers.money2String(total_allin - apuesta_actual_snapshot) + ")");
                    } else {
                        setActionTextFitted(ACTIONS_LABELS[dec - 1][0]);
                    }
                    setPlayerActionIcon("action/glasses.png");
                });
                break;
            default:
                Helpers.GUIRun(() -> {
                    setPlayerBorder(ACTIONS_COLORS[dec - 1][0]);

                    setActionTextFitted(ACTIONS_LABELS[dec - 1][0]);

                    setPlayerActionIcon("action/down.png");
                });
                break;
        }

        Helpers.GUIRun(() -> {
            if (!reraise) {

                if (dec == Player.CHECK && Helpers.doubleSecureCompare(0f, call_required) == 0) {
                    setActionBackground(new Color(0, 130, 0));
                    player_action.setForeground(Color.WHITE);

                } else {
                    setActionBackground(ACTIONS_COLORS[dec - 1][0]);
                    player_action.setForeground(ACTIONS_COLORS[dec - 1][1]);
                }

                setPlayerPotBackground(ACTIONS_COLORS[dec - 1][0]);
                player_pot.setForeground(ACTIONS_COLORS[dec - 1][1]);
            } else {
                setActionBackground(RERAISE_BACK_COLOR);
                player_action.setForeground(RERAISE_FORE_COLOR);

                setPlayerPotBackground(RERAISE_BACK_COLOR);
                player_pot.setForeground(RERAISE_FORE_COLOR);
            }

        });
    }

    // Run-it-twice rewind: re-aplica el render de la última acción guardada
    // (decision) y limpia el verde/rojo de ganador/perdedor de SIDE-A, dejando
    // las hole cards reveladas. No toca pots ni stacks (el bote persiste entre
    // sides). Si el peer salió, conserva su visual de exit.
    @Override
    public void repaintLastAction() {
        if (this.exit) {
            return;
        }
        this.winner = false;
        this.loser = false;
        // Run-it-twice: olvida el resaltado por hover de SIDE-A antes del rewind (idempotente
        // si no había hover activo). Se DESCARTA sin restaurar el color: renderDecisionVisual
        // (más abajo) re-pinta la etiqueta a la decisión (ALL IN); restaurar aquí el rojo del
        // perdedor de SIDE-A lo dejaría colgado sobre CARA-B. El re-enfoque de hole cards y el
        // settle de SIDE-B reconstruyen el resto.
        Helpers.GUIRun(this::discardLoserHandHighlight);
        this.showdown_hand_cards = null;
        // Limpia la franja de side pots de SIDE-A (se recalcula en SIDE-B).
        this.botes_secundarios.clear();
        // Línea base de CARA-B = lo acumulado en CARA-A: la franja de CARA-B
        // muestra 'pagar - base', es decir SOLO lo que se gane en CARA-B (pagar
        // sigue acumulando ambas caras para la contabilidad).
        this.pagar_face_base = this.pagar;
        // Re-enfoca las hole cards: el showdown de SIDE-A atenúa las de los
        // perdedores; en SIDE-B deben volver a verse brillantes (se reevalúan).
        Helpers.GUIRun(() -> {
            holeCard1.enfocar();
            holeCard2.enfocar();
            sec_pot_win_label.setVisible(false);
            // Borde neutro: en el flujo normal lo restaura finTurno (que el
            // rewind no llama) y renderDecisionVisual solo repinta borde en
            // ALLIN/FOLD; sin esto el verde/rojo de ganador/perdedor de SIDE-A
            // sobreviviría en CHECK/BET (p.ej. quien cubre el all-in).
            if (decision != Player.ALLIN && decision != Player.FOLD) {
                setPlayerBorder(new Color(204, 204, 204, 75));
            }
        });
        renderDecisionVisual(this.decision);
    }

    public void setActionBackground(Color color) {

        Helpers.GUIRun(() -> {
            player_action_panel.setBackground(color);
        });

    }

    public void setPlayerPotBackground(Color color) {

        Helpers.GUIRun(() -> {
            player_pot_panel.setBackground(color);
        });

    }

    public void setPlayerStackBackground(Color color) {
        Helpers.GUIRun(() -> {
            player_stack_panel.setBackground(color);
        });
    }

    public void finTurno() {

        stopActionTimer();

        Audio.stopWavResource("misc/hurryup.wav");

        turno = false;

        synchronized (GameFrame.getInstance().getCrupier().getLock_apuestas()) {
            GameFrame.getInstance().getCrupier().getLock_apuestas().notifyAll();
        }

        Helpers.GUIRun(() -> {
            if (decision != Player.ALLIN && decision != Player.FOLD) {
                setPlayerBorder(new Color(204, 204, 204, 75));
            }
        });

    }

    private void fold() {

        setDecision(Player.FOLD);

        if (GameFrame.foldSonidoOn()) {
            Audio.playWavResource("misc/fold.wav");
        }

        // Skip the GIF cinematic entirely when this is an autofold of a peer
        // that already left. The chat-notify label belongs to a player slot
        // that the UI has already torn down, the GIF never finishes painting,
        // its barrier never gets the "frames done" party, and the await below
        // blocks the game thread for GIF_BARRIER_TIMEOUT seconds before
        // throwing TimeoutException. Just play the sound and finish the turn.
        if (GameFrame.cinematicasOn() && !this.isNotify_blocked() && !this.isExit()) {
            int r = 1 + new Random().nextInt(3);

            setNotifyImageChatLabel(getClass().getResource("/images/gif_actions/fold" + String.valueOf(r) + ".gif"));

            if (getChat_notify_label().getGif_barrier() != null) {
                try {
                    getChat_notify_label().getGif_barrier().await(GIF_BARRIER_TIMEOUT, TimeUnit.SECONDS);
                } catch (InterruptedException | java.util.concurrent.BrokenBarrierException ex) {
                    Thread.currentThread().interrupt();
                    // Expected during pool shutdown — fold animation barrier
                    // cancelled cooperatively.
                    Logger.getLogger(RemotePlayer.class.getName()).log(Level.INFO,
                            "Fold animation barrier cancelled (cooperative cancellation)");
                } catch (java.util.concurrent.TimeoutException ex) {
                    // The notify was superseded (or its GIF torn down) before
                    // the rendezvous completed: non-fatal, the label is hidden
                    // by whoever owns it now. Not an interrupt.
                    Logger.getLogger(RemotePlayer.class.getName()).log(Level.INFO,
                            "Fold animation barrier timed out (superseded notify — cooperative cancellation)");
                } catch (Exception ex) {
                    Logger.getLogger(RemotePlayer.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        // Only hide the hole cards on a real fold. When fold() runs as part of
        // the exit synth flow (peer left mid-turn → readActionFromRemotePlayer
        // returns a local FOLD → setDecisionFromRemotePlayer → fold()), the
        // contract is: cards stay face-down (tapadas) as the visual cue that
        // the peer had a hand when they left. Hiding them here would flatten
        // that to an empty slot, indistinguishable from the "peer folded
        // before leaving" case which fold() handled BEFORE setExit was called
        // (and therefore actually wants the cards hidden).
        if (!this.exit) {
            holeCard1.setVisibleCard(false);
            holeCard2.setVisibleCard(false);
        }

        finTurno();
    }

    // Espera a que la ficha de la cinemática DESPEGUE (frame 32 del GIF, donde addAudio
    // la lanza), no a que el GIF entero acabe. Así la acción cierra en cuanto la ficha
    // está en vuelo: el bote se commitea y, al aterrizar, los contadores ruedan junto a
    // él (los tres a la vez, limpio como sin cinemática), mientras el GIF reproduce sus
    // frames restantes aparte. Tope = GIF_BARRIER_TIMEOUT: si la cinemática cae antes de
    // lanzar, la acción sigue igual (sin animación de ficha) sin colgarse.
    private void awaitChipLaunch() {
        CountDownLatch l = chip_launch_latch;
        if (l == null) {
            return;
        }
        try {
            l.await(GIF_BARRIER_TIMEOUT, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            Logger.getLogger(RemotePlayer.class.getName()).log(Level.INFO,
                    "Chip-launch wait interrupted (cooperative cancellation)");
        }
    }

    private void signalChipLaunched() {
        CountDownLatch l = chip_launch_latch;
        if (l != null) {
            l.countDown();
        }
    }

    private void check() {

        final boolean is_call = Helpers.doubleSecureCompare(0f, call_required) < 0;

        // CALL con dinero: va a volar ficha; NO rodamos stack/bet aquí, los rueda
        // launchChipToPot al ATERRIZAR junto al bote (los tres a la vez, como sin
        // cinemática). Pure check: sin dinero, nada que diferir.
        setCounterRollDeferred(is_call && GameFrame.getInstance().getCrupier().shouldDeferCountersToChip());

        setBet(GameFrame.getInstance().getCrupier().getApuesta_actual());

        setDecision(Player.CHECK);

        // See fold() comment: skip cinematic for exited players to avoid the
        // 10-second blocking await on a barrier the GIF callback never closes.
        if (GameFrame.cinematicasOn() && !this.isNotify_blocked() && !this.isExit()) {

            if (is_call) {
                // La ficha vuela en el frame 32 del GIF (sincronizada, INTACTO). Esperamos
                // SOLO a que despegue, no a que el GIF acabe: los frames que falten se
                // reproducen solos mientras la ronda continúa.
                int r = 1 + new Random().nextInt(4);
                setNotifyImageChatLabel(getClass().getResource("/images/gif_actions/call" + String.valueOf(r) + ".gif"), false);
                awaitChipLaunch();
            } else {
                // Pure check (sin dinero): cinemática BLOQUEANTE de siempre (no hay
                // contadores que sincronizar; el check.wav va atado a un frame del GIF).
                setNotifyImageChatLabel(getClass().getResource("/images/gif_actions/check.gif"));
                if (getChat_notify_label().getGif_barrier() != null) {
                    try {
                        getChat_notify_label().getGif_barrier().await();
                    } catch (InterruptedException | java.util.concurrent.BrokenBarrierException ex) {
                        Thread.currentThread().interrupt();
                        // Expected during pool shutdown — animation barrier
                        // cancelled cooperatively.
                        Logger.getLogger(RemotePlayer.class.getName()).log(Level.INFO,
                                "Animation barrier cancelled (cooperative cancellation)");
                    } catch (Exception ex) {
                        Logger.getLogger(RemotePlayer.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }

        } else if (is_call) {
            if (GameFrame.igualarSonidoOn()) {
                Audio.playWavResource("misc/call.wav");
            }
            GameFrame.getInstance().getCrupier().launchChipToPot(this);
        } else {
            if (GameFrame.pasarSonidoOn()) {
                Audio.playWavResource("misc/check.wav");
            }
        }

        finTurno();

    }

    public double getEffectiveStack() {

        return Helpers.doubleClean(this.stack) + Helpers.doubleClean(this.bote) + Helpers.doubleClean(this.pagar);

    }

    private void bet(double new_bet) {

        // La ficha vuela en el frame 32 del GIF (addAudio), sincronizada con el gesto y
        // el sonido — INTACTO. NO rodamos stack/bet aquí; launchChipToPot los rodará al
        // ATERRIZAR junto al bote, los tres a la vez (igual que SIN cinemática).
        setCounterRollDeferred(GameFrame.getInstance().getCrupier().shouldDeferCountersToChip());

        setBet(new_bet);

        setDecision(Player.BET);

        // See fold() comment: skip cinematic for exited players to avoid the
        // 10-second blocking await on a barrier the GIF callback never closes.
        if (GameFrame.cinematicasOn() && !this.isNotify_blocked() && !this.isExit()) {
            int r = 1 + new Random().nextInt(4);

            // Esperamos SOLO a que la ficha despegue (frame 32), no a que el GIF entero
            // termine: desde ahí la ronda cierra la acción y commitea el bote mientras la
            // ficha vuela -> al aterrizar, pot+stack+bet ruedan juntos y limpios; los
            // frames que falten del GIF se reproducen solos.
            setNotifyImageChatLabel(getClass().getResource("/images/gif_actions/bet" + String.valueOf(r) + ".gif"), false);
            awaitChipLaunch();

        } else {
            if (GameFrame.apuestaSonidoOn()) {
                Audio.playWavResource("misc/bet.wav");
            }
            GameFrame.getInstance().getCrupier().launchChipToPot(this);
        }

        if (GameFrame.SONIDOS_CHORRA && raise) {

            Audio.playWavResource("misc/raise.wav");

        }

        finTurno();

    }

    private void allin() {

        // Va a volar ficha (launchChipToPot justo abajo, ANTES de setBet): NO rodamos el
        // stack/bet en setBet; rollCountersToModel (en el aterrizaje) los rueda junto al
        // bote. setBet corre antes de que la ficha aterrice, así que el modelo ya está al
        // día cuando onLand lo lee. Los tres a la vez.
        setCounterRollDeferred(GameFrame.getInstance().getCrupier().shouldDeferCountersToChip());

        if (GameFrame.allinSonidoOn()) {
            Audio.playWavResource("misc/allin.wav");
        }
        GameFrame.getInstance().getCrupier().launchChipToPot(this);

        Init.PLAYING_CINEMATIC = true;

        Helpers.threadRun(() -> {
            if (!GameFrame.getInstance().getCrupier().remoteCinematicAllin()) {
                GameFrame.getInstance().getCrupier().soundAllin();
            }
        });

        // setBet ANTES de setDecision a propósito (mismo orden que bet() y
        // check()): el render del all-in que setDecision encola al EDT lee
        // bet+stack, y así los lee ya asentados en vez de competir con el
        // movimiento del dinero a mitad de setBet.
        setBet(this.stack + this.bet);

        setDecision(Player.ALLIN);

        finTurno();

    }

    public int getDecision() {
        return decision;
    }

    public double getBet() {
        return bet;
    }

    public void setTimeout(boolean val) {

        if (this.timeout != val) {

            this.timeout = val;

            Helpers.GUIRun(() -> {
                if (val) {
                    setPlayerBorder(Color.MAGENTA);
                    setPlayerActionIcon("action/timeout.png");
                } else {
                    setPlayerBorder(border_color != null ? border_color : new java.awt.Color(204, 204, 204, 75));
                    setPlayerActionIcon(player_action_icon);
                }
            });

            if (val && GameFrame.getInstance().isPartida_local() && !GameFrame.getInstance().getParticipantes().get(this.nickname).isForce_reset_socket() && GameFrame.errorRedSonidoOn()) {
                Audio.playWavResource("misc/network_error_" + GameFrame.LANGUAGE.toLowerCase() + ".wav");
            }

        }

    }

    /**
     * Creates new form JugadorInvitadoView
     */
    public RemotePlayer() {

        Helpers.GUIRunAndWait(() -> {
            initComponents();
            setOpaque(false);
            setBackground(null);
            installLoserHandHighlight();
            latency_label.setVisible(false);
            // Placeholder traducido hasta que llegue el primer PING (el texto del .form
            // es solo el default de diseño).
            latency_label.setText(Translator.translate("conn.latencia_format", "*", "*"));
            // Si el .form contiene un latency_dot_widget colocado
            // por el autor en NetBeans, lo enlazamos aquí. Si no, no-op.
            try {
                java.lang.reflect.Field f = getClass().getDeclaredField("latency_dot_widget");
                f.setAccessible(true);
                Object widget = f.get(this);
                if (widget instanceof LatencyDot) {
                    setLatencyDot((LatencyDot) widget);
                    ((LatencyDot) widget).applyZoom(1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP);
                }
            } catch (NoSuchFieldException nsfe) {
                // OK: aún no se ha añadido en el .form.
            } catch (Exception ex) {
                Logger.getLogger(RemotePlayer.class.getName()).log(Level.WARNING, "Could not wire latency_dot_widget", ex);
            }
            player_action.setMinimumSize(new Dimension(Math.round(RemotePlayer.MIN_ACTION_WIDTH * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)), Math.round(RemotePlayer.MIN_ACTION_HEIGHT * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP))));
            player_action.setPreferredSize(new Dimension(Math.round(RemotePlayer.MIN_ACTION_WIDTH * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)), Math.round(RemotePlayer.MIN_ACTION_HEIGHT * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP))));
            hands_win.setVisible(false);
            sec_pot_win_label.setVisible(false);
            sec_pot_win_label.setHorizontalAlignment(JLabel.CENTER);
            sec_pot_win_label.setOpaque(true);
            sec_pot_win_label.setFocusable(false);
            sec_pot_win_label.setFont(player_action.getFont().deriveFont(player_action.getFont().getStyle(), Math.round(player_action.getFont().getSize() * 0.7f)));
            panel_cartas.add(sec_pot_win_label, Integer.valueOf(1003));
            chat_notify_label.setVisible(false);
            chat_notify_label.setFocusable(false);
            chat_notify_label.setCursor(new Cursor(Cursor.HAND_CURSOR));
            chat_notify_label.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseReleased(MouseEvent e) {
                    if (!Helpers.isReleaseInsideComponent(e)) {
                        return;
                    }
                    chat_notify_label.setVisible(false);
                    if (SwingUtilities.isRightMouseButton(e)) {
                        notify_blocked = true;
                    }
                    Helpers.threadRun(() -> {
                        synchronized (chat_notify_label) {

                            chat_notify_label.notifyAll();
                        }
                    });
                }
            });
            panel_cartas.add(chat_notify_label, Integer.valueOf(1002));
            rebuy_gif_label.setVisible(false);
            rebuy_gif_label.setFocusable(false);
            // A diferencia del chat_notify_label, este GIF NO se oculta con
            // click: listener vacío que además consume el evento (sin él, el
            // click atravesaría la label y abriría el visor de la carta de
            // debajo). Lo retira solo setRebuying(false).
            rebuy_gif_label.addMouseListener(new MouseAdapter() {
            });
            panel_cartas.add(rebuy_gif_label, Integer.valueOf(1001));
            shuffle_cascade_gif_label.setVisible(false);
            shuffle_cascade_gif_label.setFocusable(false);
            // Igual que rebuy_gif_label: listener vacío que consume el clic para que no
            // atraviese la label y abra el visor de la carta de debajo. El listener es
            // permanente; hideShuffleCascadeOverlay solo oculta la label (setVisible(false) +
            // setIcon(null)). Capa 1002 (sobre chip/rebuy 1001): durante el barajado no hay
            // notify de chat activo con el que competir por la vista.
            shuffle_cascade_gif_label.addMouseListener(new MouseAdapter() {
            });
            panel_cartas.add(shuffle_cascade_gif_label, Integer.valueOf(1002));
            chip_label.setVisible(false);
            chip_label.setCursor(new Cursor(Cursor.HAND_CURSOR));
            chip_label.setOpaque(false);
            chip_label.setFocusable(false);
            chip_label.setSize(new Dimension(100, 100));
            panel_cartas.add(chip_label, Integer.valueOf(1001));
            border_color = ((LineBorder) getBorder()).getLineColor();
            danger.setVisible(false);
            player_pot.setText("----");
            disablePlayerAction();
            Helpers.setScaledIconLabel(utg_icon, getClass().getResource("/images/utg.png"), 41, 31);
            utg_icon.setVisible(false);
            icon_zoom_timer = new Timer(GameFrame.GUI_RENDER_WAIT, (ActionEvent ae) -> {
                icon_zoom_timer.stop();
                zoomIcons();
                holeCard1.updateImagePreloadCache();
                holeCard2.updateImagePreloadCache();
                refreshNotifyChatLabel();
                refreshRebuyGifLabel();
            });
            icon_zoom_timer.setRepeats(false);
            icon_zoom_timer.setCoalesce(false);
            iwtsth_blink_timer = new Timer(1500, (ActionEvent ae) -> {
                if (player_action.getBackground() == Color.RED) {
                    setActionBackground(Color.WHITE);
                    player_action.setForeground(Color.RED);
                } else {
                    setActionBackground(Color.RED);
                    player_action.setForeground(Color.WHITE);
                }

                setActionTextFitted(player_action.getText().equals(Translator.translate("ui.pierde_3")) ? Translator.translate("iwtsth.iwtsth") : Translator.translate("ui.pierde_3"));
            });
        });

    }

    public void playerActionClick() {
        Helpers.GUIRun(() -> {
            player_actionMouseClicked(null);
        });
    }

    public Card getHoleCard1() {
        return holeCard1;
    }

    public Card getHoleCard2() {
        return holeCard2;
    }

    public ArrayList<Card> getHoleCards() {
        ArrayList<Card> cartas = new ArrayList<>();

        cartas.add(getHoleCard1());

        cartas.add(getHoleCard2());
        return cartas;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;

        Helpers.GUIRun(() -> {

            if (GameFrame.getInstance().isPartida_local() && GameFrame.getInstance().getParticipantes().get(nickname).isUnsecure_player()) {
                danger.setText(Translator.translate("radar.posible_trampos"));
                danger.setVisible(true);
            } else if (!GameFrame.getInstance().isPartida_local() && GameFrame.getInstance().getSala_espera().isUnsecure_server()) {
                danger.setText(Translator.translate("radar.posible_trampos"));
                danger.setVisible(true);
            }

            player_name.setText(nickname);

            // Server's nick highlighted on the client view.
            if (!GameFrame.getInstance().isPartida_local()
                    && GameFrame.getInstance().getSala_espera().getServer_nick().equals(nickname)) {
                player_name.setForeground(Color.YELLOW);
            }

            // "$" marks a bot (no identity, nothing clickable) — same convention the
            // anticheat-log affordance below already uses, and null-safe (no lookup
            // in participantes, which may not hold this nick yet on the client).
            if (!nickname.contains("$")) {
                // Human peer: name opens the anticheat log; right-clicking the avatar
                // opens the identicon of this peer's Ed25519 public identity key.
                // Shown for both host and client views.
                Helpers.setTranslatedToolTip(player_name, "ui.click_anticheat_log");
                Helpers.setTranslatedToolTip(avatar, "ui.click_identity_identicon");
                avatar.setCursor(new Cursor(Cursor.HAND_CURSOR));
            } else {
                player_name.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                avatar.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
            }
        });

        if (GameFrame.getInstance().isPartida_local() && GameFrame.getInstance().getParticipantes().get(this.nickname).isCpu()) {
            this.bot = new Bot(this);
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        panel_cartas = new javax.swing.JLayeredPane();
        holeCard1 = new com.tonikelope.coronapoker.Card();
        holeCard2 = new com.tonikelope.coronapoker.Card();
        indicadores_arriba = new javax.swing.JPanel();
        avatar_panel = new javax.swing.JPanel();
        avatar = new javax.swing.JLabel();
        player_pot_panel = new RoundedPanel(20);
        player_pot = new javax.swing.JLabel();
        player_stack_panel = new RoundedPanel(20);
        player_stack = new javax.swing.JLabel();
        nick_panel = new javax.swing.JPanel();
        player_name = new javax.swing.JLabel();
        utg_icon = new javax.swing.JLabel();
        hands_win = new javax.swing.JLabel();
        latency_dot_widget = new com.tonikelope.coronapoker.LatencyDot();
        danger = new javax.swing.JLabel();
        player_action_panel = new RoundedPanel(20);
        player_action = new javax.swing.JLabel();
        latency_label = new javax.swing.JLabel();

        setBorder(javax.swing.BorderFactory.createLineBorder(new Color(204, 204, 204, 75), Math.round(com.tonikelope.coronapoker.Player.BORDER * (1f + com.tonikelope.coronapoker.GameFrame.ZOOM_LEVEL*com.tonikelope.coronapoker.GameFrame.ZOOM_STEP))));
        setFocusable(false);
        setOpaque(false);

        panel_cartas.setDoubleBuffered(true);

        panel_cartas.setLayer(holeCard1, javax.swing.JLayeredPane.DEFAULT_LAYER);
        panel_cartas.setLayer(holeCard2, javax.swing.JLayeredPane.DEFAULT_LAYER);

        javax.swing.GroupLayout panel_cartasLayout = new javax.swing.GroupLayout(panel_cartas);
        panel_cartas.setLayout(panel_cartasLayout);
        panel_cartasLayout.setHorizontalGroup(
            panel_cartasLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_cartasLayout.createSequentialGroup()
                .addGap(0, 0, Short.MAX_VALUE)
                .addComponent(holeCard1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(holeCard2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        panel_cartasLayout.setVerticalGroup(
            panel_cartasLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_cartasLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(panel_cartasLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(holeCard1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(holeCard2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        indicadores_arriba.setFocusable(false);
        indicadores_arriba.setOpaque(false);

        avatar_panel.setFocusable(false);
        avatar_panel.setOpaque(false);

        avatar.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/avatar_null.png"))); // NOI18N
        avatar.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        avatar.setDoubleBuffered(true);
        avatar.setFocusable(false);
        avatar.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                avatarMouseClicked(evt);
            }
        });

        player_pot.setBackground(new Color(204,204,204,75));
        player_pot.setFont(new java.awt.Font("Dialog", 1, 32)); // NOI18N
        player_pot.setForeground(new java.awt.Color(255, 255, 255));
        player_pot.setText("----");
        player_pot.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 5, 2, 5));
        player_pot.setDoubleBuffered(true);
        player_pot.setFocusable(false);

        javax.swing.GroupLayout player_pot_panelLayout = new javax.swing.GroupLayout(player_pot_panel);
        player_pot_panel.setLayout(player_pot_panelLayout);
        player_pot_panelLayout.setHorizontalGroup(
            player_pot_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(player_pot_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(player_pot)
                .addGap(0, 0, 0))
        );
        player_pot_panelLayout.setVerticalGroup(
            player_pot_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(player_pot_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(player_pot, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );

        player_stack.setBackground(new java.awt.Color(51, 153, 0));
        player_stack.setFont(new java.awt.Font("Dialog", 1, 26)); // NOI18N
        player_stack.setForeground(new java.awt.Color(255, 255, 255));
        player_stack.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        player_stack.setText("1000");
        player_stack.setToolTipText("CLICK PARA VER SU BUYIN");
        player_stack.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 5, 2, 5));
        player_stack.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        player_stack.setDoubleBuffered(true);
        player_stack.setFocusable(false);
        player_stack.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                player_stackMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout player_stack_panelLayout = new javax.swing.GroupLayout(player_stack_panel);
        player_stack_panel.setLayout(player_stack_panelLayout);
        player_stack_panelLayout.setHorizontalGroup(
            player_stack_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(player_stack_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(player_stack)
                .addGap(0, 0, 0))
        );
        player_stack_panelLayout.setVerticalGroup(
            player_stack_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(player_stack_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(player_stack)
                .addGap(0, 0, 0))
        );

        javax.swing.GroupLayout avatar_panelLayout = new javax.swing.GroupLayout(avatar_panel);
        avatar_panel.setLayout(avatar_panelLayout);
        avatar_panelLayout.setHorizontalGroup(
            avatar_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(avatar_panelLayout.createSequentialGroup()
                .addComponent(avatar)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(player_stack_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(player_pot_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        avatar_panelLayout.setVerticalGroup(
            avatar_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(avatar, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(player_pot_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(avatar_panelLayout.createSequentialGroup()
                .addComponent(player_stack_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );

        nick_panel.setFocusable(false);
        nick_panel.setOpaque(false);

        player_name.setFont(new java.awt.Font("Dialog", 1, 22)); // NOI18N
        player_name.setForeground(new java.awt.Color(255, 255, 255));
        player_name.setText("12345678901");
        player_name.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 5, 2, 5));
        player_name.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        player_name.setDoubleBuffered(true);
        player_name.setFocusable(false);
        player_name.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                player_nameMouseClicked(evt);
            }
        });

        utg_icon.setFont(new java.awt.Font("Dialog", 1, 22)); // NOI18N
        utg_icon.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        utg_icon.setDoubleBuffered(true);
        utg_icon.setFocusable(false);

        hands_win.setFont(new java.awt.Font("Dialog", 1, 22)); // NOI18N
        hands_win.setForeground(new java.awt.Color(255, 255, 255));
        hands_win.setText("(0)");
        hands_win.setToolTipText("MANOS GANADAS");
        hands_win.setDoubleBuffered(true);

        javax.swing.GroupLayout nick_panelLayout = new javax.swing.GroupLayout(nick_panel);
        nick_panel.setLayout(nick_panelLayout);
        nick_panelLayout.setHorizontalGroup(
            nick_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(nick_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(player_name)
                .addGroup(nick_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(nick_panelLayout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(utg_icon)
                        .addGap(5, 5, 5))
                    .addGroup(nick_panelLayout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(latency_dot_widget)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addComponent(hands_win))
        );
        nick_panelLayout.setVerticalGroup(
            nick_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(nick_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                .addComponent(player_name)
                .addComponent(utg_icon)
                .addComponent(hands_win))
            .addGroup(nick_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(latency_dot_widget)
                .addContainerGap())
        );

        javax.swing.GroupLayout indicadores_arribaLayout = new javax.swing.GroupLayout(indicadores_arriba);
        indicadores_arriba.setLayout(indicadores_arribaLayout);
        indicadores_arribaLayout.setHorizontalGroup(
            indicadores_arribaLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(indicadores_arribaLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(indicadores_arribaLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(avatar_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(nick_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(0, 0, 0))
        );
        indicadores_arribaLayout.setVerticalGroup(
            indicadores_arribaLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(indicadores_arribaLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(avatar_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(nick_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );

        danger.setBackground(new java.awt.Color(255, 0, 0));
        danger.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        danger.setForeground(new java.awt.Color(255, 255, 255));
        danger.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        danger.setText("POSIBLE TRAMPOS@");
        danger.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5));
        danger.setFocusable(false);
        danger.setOpaque(true);

        player_action.setFont(new java.awt.Font("Dialog", 1, 26)); // NOI18N
        player_action.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        player_action.setText("ESCALERA DE COLOR");
        player_action.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 5, 2, 5));
        player_action.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        player_action.setDoubleBuffered(true);
        player_action.setFocusable(false);
        player_action.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                player_actionMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout player_action_panelLayout = new javax.swing.GroupLayout(player_action_panel);
        player_action_panel.setLayout(player_action_panelLayout);
        player_action_panelLayout.setHorizontalGroup(
            player_action_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(player_action_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(player_action, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );
        player_action_panelLayout.setVerticalGroup(
            player_action_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(player_action_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(player_action)
                .addGap(0, 0, 0))
        );

        latency_label.setBackground(new java.awt.Color(0, 0, 255));
        latency_label.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        latency_label.setForeground(new java.awt.Color(255, 255, 255));
        latency_label.setText("Latencia: * ms | * ms");
        latency_label.setOpaque(true);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(danger, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(latency_label, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(indicadores_arriba, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(panel_cartas, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(player_action_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(latency_label)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(danger)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(indicadores_arriba, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(panel_cartas)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(player_action_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void player_stackMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_player_stackMouseClicked
        // TODO add your handling code here:
        if (!Helpers.isRealClick(evt)) {
            return;
        }
        if (!player_stack_click) {
            player_stack_click = true;

            // Muestra el buy-in fijo (no es el valor del stack): el roller queda
            // invalidado para que al restaurar salte al stack real sin animar desde aquí.
            stackRoller().invalidate();
            player_stack.setText(Helpers.money2String(this.buyin));
            setPlayerStackBackground(Color.GRAY);
            player_stack.setForeground(Color.WHITE);

            Helpers.threadRun(() -> {
                Helpers.pausar(1500);
                double s = getStack();
                Helpers.GUIRun(() -> {
                    if (GameFrame.hasRebought(nickname)) {
                        setPlayerStackBackground(Color.CYAN);

                        player_stack.setForeground(Color.BLACK);
                    } else {

                        setPlayerStackBackground(new Color(51, 153, 0));

                        player_stack.setForeground(Color.WHITE);
                    }

                    stackRoller().set(s);
                });
                player_stack_click = false;
            });
        }
    }//GEN-LAST:event_player_stackMouseClicked

    private void player_actionMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_player_actionMouseClicked

        // evt es null cuando se invoca por codigo desde playerActionClick(); en ese caso no hay
        // click real que validar. Para un click de usuario exigimos boton izquierdo soltado dentro.
        if (evt != null && !Helpers.isRealClick(evt)) {
            return;
        }

        if (GameFrame.getInstance().isPartida_local() && this.timeout) {

            if (!GameFrame.getInstance().getParticipantes().get(this.nickname).isCpu() && Helpers.mostrarMensajeInformativoSINO(GameFrame.getInstance(), Translator.translate("conn.este_usuario_tiene_problemas_de"), new ImageIcon(Init.class.getResource("/images/action/timeout.png"))) == 0) {
                GameFrame.getInstance().getCrupier().remotePlayerQuit(this.nickname);
            }

        } else if (GameFrame.IWTSTH_RULE && isIwtsthCandidate() && GameFrame.getInstance().getCrupier().isIWTSTH4LocalPlayerAuthorized() && !GameFrame.getInstance().getCrupier().isIwtsthing() && !GameFrame.getInstance().getCrupier().isIwtsthing_request() && !GameFrame.getInstance().getCrupier().isIwtsth() && GameFrame.getInstance().getCrupier().isShow_time()) {

            GameFrame.getInstance().getCrupier().IWTSTH_REQUEST(GameFrame.getInstance().getLocalPlayer().getNickname());
        }
    }//GEN-LAST:event_player_actionMouseClicked

    private void avatarMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_avatarMouseClicked
        if (!Helpers.isReleaseInsideComponent(evt)) {
            return;
        }
        if (!javax.swing.SwingUtilities.isRightMouseButton(evt)) {
            return;
        }
        // Identity: right-clicking a remote human avatar opens the identicon of that
        // peer's Ed25519 public identity. The dialog includes a "Verificar identidad"
        // button that marks (nick, pubkey) as verified_oob if the user has compared
        // the fingerprint with the peer through an external secure channel.
        //
        // The pubkey is normally cached on the Participant during the JOIN handshake
        // (host's pubkey rides the intro packet; the rest piggyback on USERSLIST /
        // NEWUSER atomically with their nick + avatar). If for any reason the
        // Participant has no pubkey, we fall back to the TOFU-pinned pubkey from
        // known_identities so the click still works.
        Participant par = GameFrame.getInstance().getParticipantes().get(this.nickname);
        if (par == null || par.isCpu()) {
            return;
        }
        byte[] pubkey = par.getIdentity_pubkey();
        if (pubkey == null) {
            pubkey = TOFUResolver.getPinnedPubkey(this.nickname);
            if (pubkey != null) {
                par.setIdentity_pubkey(pubkey);
            }
        }
        if (pubkey == null) {
            java.util.logging.Logger.getLogger(RemotePlayer.class.getName()).log(
                    java.util.logging.Level.WARNING,
                    "No identity pubkey recorded for {0}; cannot open identity identicon",
                    this.nickname);
            return;
        }
        IdenticonDialog identicon = new IdenticonDialog(
                GameFrame.getInstance(), true, this.nickname,
                pubkey, IdenticonDialog.Mode.IDENTITY, pubkey);
        identicon.setLocationRelativeTo(GameFrame.getInstance());
        identicon.setVisible(true);
    }//GEN-LAST:event_avatarMouseClicked

    private void player_nameMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_player_nameMouseClicked
        // TODO add your handling code here:

        if (!Helpers.isRealClick(evt)) {
            return;
        }

        if (GameFrame.getInstance().isPartida_local() && this.timeout) {

            if (!GameFrame.getInstance().getParticipantes().get(this.nickname).isCpu() && Helpers.mostrarMensajeInformativoSINO(GameFrame.getInstance(), Translator.translate("conn.este_usuario_tiene_problemas_de"), new ImageIcon(Init.class.getResource("/images/action/timeout.png"))) == 0) {
                GameFrame.getInstance().getCrupier().remotePlayerQuit(this.nickname);
            }

        }

    }//GEN-LAST:event_player_nameMouseClicked

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel avatar;
    private javax.swing.JPanel avatar_panel;
    private javax.swing.JLabel danger;
    private javax.swing.JLabel hands_win;
    private com.tonikelope.coronapoker.Card holeCard1;
    private com.tonikelope.coronapoker.Card holeCard2;
    private javax.swing.JPanel indicadores_arriba;
    private javax.swing.JLabel latency_dot_widget;
    private javax.swing.JLabel latency_label;
    private javax.swing.JPanel nick_panel;
    private javax.swing.JLayeredPane panel_cartas;
    private javax.swing.JLabel player_action;
    private javax.swing.JPanel player_action_panel;
    private javax.swing.JLabel player_name;
    private javax.swing.JLabel player_pot;
    private javax.swing.JPanel player_pot_panel;
    private javax.swing.JLabel player_stack;
    private javax.swing.JPanel player_stack_panel;
    private javax.swing.JLabel utg_icon;
    // End of variables declaration//GEN-END:variables

    public boolean isIwtsthCandidate() {
        return isLoser() && isActivo() && getHoleCard1().isVisible_card() && getHoleCard1().isTapada();
    }

    public void zoomIcons() {

        Helpers.threadRun(() -> {
            synchronized (zoom_lock) {
                Helpers.GUIRunAndWait(() -> {
                    setAvatar();
                    utgIconZoom();
                    actionIconZoom();
                    nickChipIconZoom();
                    refreshPositionChipIcons();
                    refreshSecPotLabel();
                });
            }
        });
    }

    @Override
    public void zoom(float zoom_factor, final ConcurrentLinkedQueue<Long> notifier) {

        border_size = Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP);

        arc = Player.ARC * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP);

        final ConcurrentLinkedQueue<Long> mynotifier = new ConcurrentLinkedQueue<>();

        if (Helpers.doubleSecureCompare(0f, zoom_factor) < 0) {

            holeCard1.zoom(zoom_factor, mynotifier);
            holeCard2.zoom(zoom_factor, mynotifier);

            synchronized (zoom_lock) {

                Helpers.GUIRunAndWait(() -> {
                    if (icon_zoom_timer.isRunning()) {
                        icon_zoom_timer.stop();
                    }

                    hidePlayerActionIcon();

                    player_action.setMinimumSize(new Dimension(Math.round(RemotePlayer.MIN_ACTION_WIDTH * zoom_factor), Math.round(RemotePlayer.MIN_ACTION_HEIGHT * zoom_factor)));

                    player_action.setPreferredSize(new Dimension(Math.round(RemotePlayer.MIN_ACTION_WIDTH * zoom_factor), Math.round(RemotePlayer.MIN_ACTION_HEIGHT * zoom_factor)));

                    setPlayerBorder(border_color);

                    getAvatar().setVisible(false);

                    utg_icon.setVisible(false);

                    player_name.setIcon(null);

                    chip_label.setVisible(false);

                    LatencyDot dot = latency_dot;
                    if (dot != null) {
                        dot.applyZoom(zoom_factor);
                    }
                });

                Helpers.zoomFonts(this, zoom_factor, null);

                Helpers.GUIRun(() -> {
                    if (icon_zoom_timer.isRunning()) {
                        icon_zoom_timer.restart();
                    } else {
                        icon_zoom_timer.start();
                    }
                });

            }

            synchronized (mynotifier) {
                while (mynotifier.size() < 2) {
                    try {
                        mynotifier.wait(1000);

                    } catch (InterruptedException ex) {
                        Logger.getLogger(RemotePlayer.class.getName()).log(Level.SEVERE, null, ex);
                    }
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

    @Override
    public void setWinner(String msg) {
        this.winner = true;
        this.conta_win++;

        Helpers.GUIRun(() -> {
            setPlayerBorder(Color.GREEN);
            setActionBackground(Color.GREEN);
            player_action.setForeground(Color.BLACK);

            setActionTextFitted(msg);

            setPlayerActionIcon("action/happy.png");

            if (conta_win > 0) {
                hands_win.setText(String.valueOf(conta_win));
                hands_win.setVisible(true);
            }

        });

    }

    public Timer getIwtsth_blink_timer() {
        return iwtsth_blink_timer;
    }

    public Timer getRebuy_countdown_timer() {
        return rebuy_countdown_timer;
    }

    @Override
    public void setLoser(String msg) {
        this.loser = true;

        Helpers.GUIRun(() -> {
            setPlayerBorder(Color.RED);

            if (!holeCard1.isTapada() || !GameFrame.getInstance().getCrupier().isIWTSTH4LocalPlayerAuthorized()) {

                setActionBackground(Color.RED);
                player_action.setForeground(Color.WHITE);
                holeCard1.desenfocar();
                holeCard2.desenfocar();

            } else {
                setActionBackground(Color.WHITE);
                player_action.setForeground(Color.RED);
                player_action.setCursor(new Cursor(Cursor.HAND_CURSOR));
                holeCard1.setIwtsth_candidate(this);
                holeCard2.setIwtsth_candidate(this);
            }

            setActionTextFitted(msg);

            setPlayerActionIcon("action/angry.png");

        });

    }

    @Override
    public void setShowdownLoserHand(java.util.List<Card> cartas) {
        this.showdown_hand_cards = cartas;
    }

    // Enter/exit sobre la etiqueta de jugada (instalado en el constructor): al entrar resalta la
    // jugada de este jugador con el MISMO mecanismo que el ganador (enfoca sus cartas, atenúa el
    // resto de la mesa) y pinta su etiqueta de amarillo/negro; al salir lo restaura. Aplica a
    // cualquiera cuya jugada sea visible: perdedor que mostró en el showdown, o perdedor/foldeado
    // que enseñó después (IWTSTH forzado o botón MOSTRAR voluntario) — en todos ellos el revelado
    // fija showdown_hand_cards. Convive con el listener de click IWTSTH ya presente en player_action
    // (mientras el candidato IWTSTH no muestra, showdown_hand_cards es null y el enter no hace nada).
    private void installLoserHandHighlight() {
        player_action.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                highlightLoserHand(true);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent evt) {
                highlightLoserHand(false);
            }
        });
    }

    // on=true: solo si la opción está activa, este jugador NO es ganador (su etiqueta muestra una
    // jugada perdedora/enseñada, no GANA), tiene jugada visible (showdown_hand_cards) y seguimos en
    // show_time. Enfoca SOLO las cartas de su jugada y desenfoca todas las demás de la mesa
    // (guardando antes el enfoque de cada una), y pinta la etiqueta de amarillo/negro. El gate es
    // por !winner y no por loser: en run-it-twice un jugador puede ganar un board y perder el otro
    // (queda winner=true Y loser=true con showdown_hand_cards del board perdido), y ahí su etiqueta
    // final es GANA — no debe resaltarse la jugada del board que perdió.
    // on=false: restauración incondicional (defensiva).
    private void highlightLoserHand(boolean on) {
        if (on) {
            final java.util.List<Card> cartas = showdown_hand_cards;
            Crupier crupier = GameFrame.getInstance() != null ? GameFrame.getInstance().getCrupier() : null;

            if (!GameFrame.RESALTAR_JUGADA_PERDEDOR || winner || cartas == null || crupier == null || !crupier.isShow_time()) {
                return;
            }

            Helpers.GUIRun(() -> {
                // Idempotencia: si quedó un resaltado colgado, deshazlo antes de re-snapshotear.
                restoreLoserHandHighlight();

                java.util.List<Card> mesa = GameFrame.getInstance().getShowdownVisibleCards();
                java.util.Map<Card, Boolean> snapshot = new java.util.HashMap<>();

                for (Card c : mesa) {
                    snapshot.put(c, c.isDesenfocada());
                }

                showdown_focus_snapshot = snapshot;

                for (Card c : mesa) {
                    if (cartas.contains(c)) {
                        c.enfocar();
                        c.marcarTintePerdedor();
                    } else {
                        c.desenfocar();
                    }
                }

                showdown_action_bg_snapshot = player_action_panel.getBackground();
                showdown_action_fg_snapshot = player_action.getForeground();
                setActionBackground(Color.YELLOW);
                player_action.setForeground(Color.BLACK);
            });
        } else {
            Helpers.GUIRun(this::restoreLoserHandHighlight);
        }
    }

    // Devuelve las cartas de la mesa al enfoque que tenían antes del hover (el resaltado del
    // ganador vuelve tal cual) y quita el tinte. NO toca el color de la etiqueta. Idempotente
    // (no-op si no hay snapshot). Debe llamarse en el EDT.
    private void restoreLoserHandFocus() {
        java.util.Map<Card, Boolean> snapshot = showdown_focus_snapshot;

        if (snapshot != null) {
            for (java.util.Map.Entry<Card, Boolean> e : snapshot.entrySet()) {
                if (e.getValue()) {
                    e.getKey().desenfocar();
                } else {
                    e.getKey().enfocar();
                }

                e.getKey().desmarcarTintePerdedor();
            }

            showdown_focus_snapshot = null;
        }
    }

    // Restauración completa (enfoque + color de la etiqueta) para el mouseExited y el reset
    // entre manos: la etiqueta vuelve al rojo del perdedor tal cual estaba.
    private void restoreLoserHandHighlight() {
        restoreLoserHandFocus();

        if (showdown_action_bg_snapshot != null) {
            setActionBackground(showdown_action_bg_snapshot);
            player_action.setForeground(showdown_action_fg_snapshot);
            showdown_action_bg_snapshot = null;
            showdown_action_fg_snapshot = null;
        }
    }

    // Descarta el hover SIN restaurar el color de la etiqueta: para el rewind de run-it-twice,
    // donde renderDecisionVisual re-pinta la etiqueta a la decisión (ALL IN) justo después;
    // restaurar aquí el rojo del perdedor de SIDE-A lo dejaría colgado sobre CARA-B.
    private void discardLoserHandHighlight() {
        restoreLoserHandFocus();
        showdown_action_bg_snapshot = null;
        showdown_action_fg_snapshot = null;
    }

    @Override
    public void pagar(double pasta, Integer sec_pot) {

        this.pagar += pasta;

        if (sec_pot != null) {
            botes_secundarios.add(sec_pot);

            refreshSecPotLabel();
        }

    }

    @Override
    public void marcarBotePot(int sec_pot) {
        if (!botes_secundarios.contains(sec_pot)) {
            botes_secundarios.add(sec_pot);
        }
        refreshSecPotLabel();
    }

    public void setPosition(int pos) {

        switch (pos) {
            case Player.DEALER:

                if (GameFrame.getInstance().getCrupier().getDealer_nick().equals(GameFrame.getInstance().getCrupier().getSb_nick())) {
                    if (Helpers.doubleSecureCompare(GameFrame.getInstance().getCrupier().getCiega_pequeña(), stack) < 0) {
                        setBet(GameFrame.getInstance().getCrupier().getCiega_pequeña());

                    } else {

                        //Vamos ALLIN (setBet antes: ver allin())
                        setBet(stack);

                        setDecision(Player.ALLIN);
                    }
                } else {
                    setBet(0f);
                }

                break;
            case Player.BIG_BLIND:

                if (Helpers.doubleSecureCompare(GameFrame.getInstance().getCrupier().getCiega_grande(), stack) < 0) {
                    setBet(GameFrame.getInstance().getCrupier().getCiega_grande());

                } else {

                    //Vamos ALLIN (setBet antes: ver allin())
                    setBet(stack);

                    setDecision(Player.ALLIN);
                }

                break;
            case Player.SMALL_BLIND:

                if (Helpers.doubleSecureCompare(GameFrame.getInstance().getCrupier().getCiega_pequeña(), stack) < 0) {
                    setBet(GameFrame.getInstance().getCrupier().getCiega_pequeña());

                } else {

                    //Vamos ALLIN (setBet antes: ver allin())
                    setBet(stack);

                    setDecision(Player.ALLIN);
                }

                break;
            default:

                setBet(0f);

                break;
        }

        refreshPositionChipIcons();

    }

    // silent: el contador animado de recompra (Crupier.animateRebuyStacks) ya
    // disparo la caja registradora para toda la tanda -> aqui NO se repite. En el
    // camino sin animacion (silent=false) suena como siempre, una por recompra.
    public synchronized void reComprar(int cantidad, boolean silent) {

        // Re-chequeo al aplicar (anti-stale / anti-trampa): nunca superar el techo
        // de mesa aunque la cantidad solicitada fuera mayor o el stack cambiara
        // entre la solicitud y el inicio de la mano. headroom 0 -> recompra anulada.
        int applied = Math.min(cantidad, GameFrame.rebuyHeadroom(this.stack));
        if (applied <= 0) {
            Logger.getLogger(RemotePlayer.class.getName()).log(Level.WARNING,
                    "Rebuy of {0} for {1} voided at apply time (already at table ceiling {2})",
                    new Object[]{cantidad, this.nickname, GameFrame.getBuyinCap()});
            return;
        }

        this.stack += applied;
        this.buyin += applied;

        GameFrame.getInstance().getRegistro().print(this.nickname + " " + Translator.translate("rebuy.recompra_2") + String.valueOf(applied) + ")");

        if (!silent && GameFrame.cajaSonidoOn()) {
            Audio.playWavResource("misc/cash_register.wav");
        }

        // Si la cortinilla anima la recompra (silent), ELLA pinta el texto+CYAN frame a frame
        // (setStackDisplay, que ya elige CYAN via hasRebought); pintarlo aqui tambien daria un
        // fogonazo al valor final a mitad del rodaje.
        if (!player_stack_click && !silent) {
            Helpers.GUIRun(() -> {
                player_stack.setText(Helpers.money2String(stack));
                setPlayerStackBackground(Color.CYAN);
                player_stack.setForeground(Color.BLACK);
            });
        }
    }

    // Accesores LOCK-FREE: stack/bote/pagar/exit son volatile, así que estos getters/setters
    // simples NO necesitan synchronized. CLAVE anti-deadlock: el EDT los lee (display de la
    // mesa, menú de recompra, setSpectator, etc.); si fueran synchronized cogerían el monitor
    // del jugador, y con el hilo de juego manteniéndolo a través de un GUIRunAndWait (setBet/
    // setStack postean ciegas a la vez que el llenado de stacks rueda en el EDT) -> deadlock
    // permanente EDT<->worker. Lock-free lo hace IMPOSIBLE en cualquier sitio. Solo los
    // mutadores COMPUESTOS del dinero (setStack/setBet/postAnte/postStraddle/reComprar) siguen
    // synchronized; el EDT no los invoca (los dispara el hilo de juego).
    @Override
    public double getStack() {
        return stack;
    }

    public JLabel getPlayer_action() {
        return player_action;
    }

    @Override
    public void resetGUI() {
        Helpers.GUIRunAndWait(() -> {
            if (orig_action_font != null && orig_action_font.getSize() != player_action.getFont().getSize()) {
                player_action.setFont(orig_action_font);
                orig_action_font = null;
            }

            sec_pot_win_label.setVisible(false);

            setOpaque(false);

            setBackground(null);

            setPlayerBorder(new java.awt.Color(204, 204, 204, 75));

            if (iwtsth_blink_timer.isRunning()) {

                iwtsth_blink_timer.stop();
            }

            player_name.setIcon(null);

            utg_icon.setVisible(false);

            // Nueva mano: sincroniza el roller del bet a 0 (muestra "----") para que la
            // primera apuesta de la mano ruede desde 0, no desde el aporte de la anterior.
            betRoller().set(0);

            setPlayerPotBackground(new Color(204, 204, 204, 75));

            player_pot.setForeground(Color.WHITE);

            player_action.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));

            if (conta_win > 0) {
                hands_win.setText(String.valueOf(conta_win));
                hands_win.setVisible(true);
            } else {
                hands_win.setVisible(false);
            }

            disablePlayerAction();

            if (!player_stack_click) {
                if (GameFrame.hasRebought(nickname)) {
                    setPlayerStackBackground(Color.CYAN);

                    player_stack.setForeground(Color.BLACK);
                } else {

                    setPlayerStackBackground(new Color(51, 153, 0));

                    player_stack.setForeground(Color.WHITE);
                }
            }

        });
    }

    @Override
    public void nuevaMano() {

        // Garantizar que el avatar esté pintado al inicio de CADA mano.
        // En el flow normal el zoom inicial dispara setAvatar vía
        // icon_zoom_timer, pero en RECOVER esa cadena no se ejecuta
        // (el SHUTDOWN_THREAD_POOL entre partidas mata el thread spawnado
        // del zoom inicial) → primera mano post-recover queda sin avatar.
        // Llamarlo aquí es idempotente y barato.
        setAvatar();

        this.decision = Player.NODEC;

        this.notify_blocked = false;

        this.botes_secundarios.clear();

        this.pagar_face_base = 0f;

        this.winner = false;

        this.loser = false;

        // Showdown highlight: deshace cualquier resaltado que hubiera quedado colgado si la mano
        // anterior acabó con el ratón sobre la etiqueta, y olvida la jugada resaltable.
        highlightLoserHand(false);
        this.showdown_hand_cards = null;

        this.bote = 0f;

        this.last_bote = null;

        this.bet = 0f;

        // Red de seguridad: limpia cualquier aplazamiento de rodaje de contador que se
        // hubiera quedado colgado de una mano anterior (p.ej. cinemática de acción
        // interrumpida antes de lanzar su ficha) ANTES de fijar el de la ciega de esta
        // mano. Sin esto, un flag pegado haría que setStack/setBet de este jugador no
        // rodaran hasta su siguiente ficha. Solo afecta al rodaje del contador.
        setCounterRollDeferred(false);

        resetGUI();

        if (GameFrame.getInstance().getCrupier().getRebuy_now().containsKey(nickname)) {

            int rebuy = GameFrame.getInstance().getCrupier().getRebuy_now().get(nickname);

            GameFrame.getInstance().getCrupier().getRebuy_now().remove(nickname);

            // Si la recompra se animo con la cortinilla (animateRebuyStacks ya rodo el
            // stack hasta el valor final y sono la caja), reComprar no repite el sonido.
            // Usa la decision CAPTURADA (isRebuyFillAnimated): si se apago "Contadores" a
            // mitad del conteo, sigue mudo (no suena la caja dos veces).
            reComprar(rebuy, GameFrame.getInstance().getCrupier().isRebuyFillAnimated());

        }

        setStack(stack + pagar);

        pagar = 0f;

        // Si va a postear ciega (BB/SB) cuya ficha volará al bote, NO rueda su stack/bet en
        // el posteo (setPosition->setBet(ciega), justo abajo): se difiere y, al ATERRIZAR su
        // ficha (flyForcedBetsToPot.onLand -> rollCountersToModel), rueda junto al bote. La
        // ganancia pendiente (setStack(stack+pagar) de arriba) ya rodó, NO se difiere. Mismo
        // gate que el vuelo (aquí game_recovered==0 siempre: el bloque recover corre después).
        if (GameFrame.getInstance().getCrupier().shouldDeferCountersToChip()
                && (this.nickname.equals(GameFrame.getInstance().getCrupier().getBb_nick())
                || this.nickname.equals(GameFrame.getInstance().getCrupier().getSb_nick()))) {
            setCounterRollDeferred(true);
        }

        if (this.nickname.equals(GameFrame.getInstance().getCrupier().getBb_nick())) {
            this.setPosition(BIG_BLIND);
        } else if (this.nickname.equals(GameFrame.getInstance().getCrupier().getSb_nick())) {
            this.setPosition(SMALL_BLIND);
        } else if (this.nickname.equals(GameFrame.getInstance().getCrupier().getDealer_nick())) {
            this.setPosition(DEALER);
        } else {
            this.setPosition(-1);
        }

        if (this.nickname.equals(GameFrame.getInstance().getCrupier().getUtg_nick())) {
            this.setUTG();
        } else {
            this.disableUTG();
        }

        if (this.spectator_bb) {

            this.spectator_bb = false;

            if (Helpers.doubleSecureCompare(GameFrame.getInstance().getCrupier().getCiega_grande(), stack + bet) < 0) {
                setBet(GameFrame.getInstance().getCrupier().getCiega_grande());
            } else {

                //Vamos ALLIN (setBet antes: ver allin())
                setBet(stack);
                setDecision(Player.ALLIN);

            }

        }

    }

    public void refreshPositionChipIcons() {

        ImageIcon chip_label_icon;

        if (this.nickname.equals(GameFrame.getInstance().getCrupier().getBb_nick())) {
            Helpers.setScaledIconLabel(player_name, getClass().getResource("/images/bb.png"), Math.round(0.7f * player_name.getHeight()), Math.round(0.7f * player_name.getHeight()));

            chip_label_icon = Helpers.IMAGEN_BB;
        } else if (this.nickname.equals(GameFrame.getInstance().getCrupier().getSb_nick())) {
            Helpers.setScaledIconLabel(player_name, getClass().getResource("/images/sb.png"), Math.round(0.7f * player_name.getHeight()), Math.round(0.7f * player_name.getHeight()));

            chip_label_icon = Helpers.IMAGEN_SB;
        } else if (this.nickname.equals(GameFrame.getInstance().getCrupier().getDealer_nick())) {
            // En 3-manos el dealer es el UTG; si straddlea, ficha combinada dealer+straddle
            // (la rama DEALER gana a la de straddle de abajo, así que se resuelve aquí).
            boolean dealer_straddle = GameFrame.getInstance().getCrupier().isStraddle_posted()
                    && this.nickname.equals(GameFrame.getInstance().getCrupier().getUtg_nick())
                    && !GameFrame.getInstance().getCrupier().isDead_dealer();
            String dealer_img = dealer_straddle ? "/images/dealer_straddle.png"
                    : (GameFrame.getInstance().getCrupier().isDead_dealer() ? "/images/dead_dealer.png" : "/images/dealer.png");
            Helpers.setScaledIconLabel(player_name, getClass().getResource(dealer_img), Math.round(0.7f * player_name.getHeight()), Math.round(0.7f * player_name.getHeight()));

            chip_label_icon = dealer_straddle ? Helpers.IMAGEN_DEALER_STRADDLE
                    : (GameFrame.getInstance().getCrupier().isDead_dealer() ? Helpers.IMAGEN_DEAD_DEALER : Helpers.IMAGEN_DEALER);
        } else if (GameFrame.getInstance().getCrupier().isStraddle_posted()
                && this.nickname.equals(GameFrame.getInstance().getCrupier().getUtg_nick())) {
            Helpers.setScaledIconLabel(player_name, getClass().getResource("/images/straddle.png"), Math.round(0.7f * player_name.getHeight()), Math.round(0.7f * player_name.getHeight()));

            chip_label_icon = Helpers.IMAGEN_STRADDLE;
        } else {
            chip_label_icon = null;
        }

        // Suprimida durante la rotación de fichas (hasta que la viajera aterriza): NO se
        // pinta la grande aunque nos llamen (p.ej. desde un re-layout de la mesa).
        final boolean suppressed = GameFrame.getInstance().getCrupier() != null
                && GameFrame.getInstance().getCrupier().isBigChipSuppressed(this);
        Helpers.GUIRun(() -> {
            if (isActivo() && !(holeCard1.isIniciada() && !holeCard1.isTapada()) && chip_label_icon != null && !suppressed) {
                chip_label.setIcon(chip_label_icon);
                chip_label.setSize(chip_label.getIcon().getIconWidth(), chip_label.getIcon().getIconHeight());
                chip_label.setLocation(0, 0);
                chip_label.setVisible(true);

                chip_label.repaint();

            } else {

                chip_label.setVisible(false);
            }
        });

    }

    @Override
    public void resetBetDecision() {
        int old_dec = this.decision;

        this.decision = Player.NODEC;

        Helpers.GUIRun(() -> {
            if (old_dec != Player.BET || Helpers.doubleSecureCompare(0f, GameFrame.getInstance().getCrupier().getApuesta_actual()) == 0) {
                setPlayerPotBackground(new Color(204, 204, 204, 75));
                player_pot.setForeground(Color.WHITE);
            }

            disablePlayerAction();
        });

    }

    @Override
    public void disableUTG() {

        if (this.utg) {
            this.utg = false;

            Helpers.GUIRun(() -> {
                utg_icon.setVisible(false);
            });
        }
    }

    public void setUTG() {

        this.utg = true;

        Helpers.GUIRun(() -> {
            utg_icon.setVisible(true);
        });
    }

    @Override
    public boolean isSpectator() {
        return this.spectator;
    }

    @Override
    public String getLastActionString() {

        // El texto de la accion se pinta en player_action via GUIRun (asincrono, en el
        // EDT) desde renderDecisionVisual; el hilo del juego llega aqui en cuanto finTurno
        // pone turno=false y notifica, y podia LEER la etiqueta ANTES de que el EDT la
        // repintara —cuando todavia decia "PENSANDO"— colando "PENSANDO (n)" en el registro
        // en vez de "RETIRARSE/PASO/...". Con las cinematicas OFF el fold ni siquiera espera
        // a la barrera del GIF (que antes daba tiempo de sobra al EDT), asi que la carrera la
        // ganaba el hilo del juego de forma sistematica. Leer la etiqueta EN el EDT respeta
        // el orden FIFO de la cola: el setActionTextFitted de ESTA accion ya se encolo antes
        // de que finTurno notificara, con lo que se aplica antes que esta lectura.
        final String[] label = new String[]{""};
        Helpers.GUIRunAndWait(() -> label[0] = player_action.getText());

        String action = nickname + " ";

        switch (this.getDecision()) {
            case Player.FOLD:
            case Player.CHECK:
            case Player.BET:
            case Player.ALLIN:
                action += label[0] + " (" + Helpers.money2String(this.bote) + ")";
                break;
            default:
                break;
        }

        return action;
    }

    @Override
    public void setBuyin(int buyin) {
        this.buyin = buyin;

    }

    @Override
    public void setSpectator(String msg) {
        if (!this.exit) {
            this.spectator = true;
            this.bote = 0f;

            Helpers.GUIRunAndWait(() -> {
                setOpaque(false);
                setBackground(null);
                setPlayerBorder(new Color(204, 204, 204, 75));

                player_pot.setText("----");
                player_pot.setForeground(Color.white);
                setPlayerPotBackground(new Color(204, 204, 204, 75));
                utg_icon.setVisible(false);
                holeCard1.resetearCarta();
                holeCard2.resetearCarta();

                player_name.setOpaque(false);
                player_name.setBackground(null);
                player_name.setIcon(null);

                chip_label.setVisible(false);

                sec_pot_win_label.setVisible(false);

                if (GameFrame.hasRebought(nickname)) {
                    setPlayerStackBackground(Color.CYAN);
                    player_stack.setForeground(Color.BLACK);
                } else {

                    setPlayerStackBackground(new Color(51, 153, 0));
                    player_stack.setForeground(Color.WHITE);
                }

                player_stack.setText(Helpers.money2String(stack));

                if (GameFrame.getInstance().getSala_espera().getServer_nick().equals(nickname)) {
                    player_name.setForeground(Color.YELLOW);
                } else {
                    player_name.setForeground(Color.WHITE);
                }

                disablePlayerAction();
            });

            Helpers.runWhenLaidOut(player_name, () -> {
                if (isSpectator()) {
                    setActionTextFitted(msg != null ? msg : Translator.translate("player.espectador"));
                    setPlayerActionIcon(Helpers.doubleSecureCompare(0f, getEffectiveStack()) == 0 ? "action/ghost.png" : "action/calentando.png");
                }
            });

        }
    }

    public void disablePlayerAction() {

        Helpers.GUIRun(() -> {
            setActionTextFitted(" ");
            player_action.setForeground(Color.LIGHT_GRAY);
            setActionBackground(new Color(204, 204, 204, 75));
            setPlayerActionIcon(null);
        });
    }

    // Straddle voluntario: mientras el UTG decide (a ciegas) si pone el straddle, los
    // demás peers pintan en su asiento el icono pensativo y "STRADDLE?" — mismo look que
    // el "PENSANDO" del turno normal, pero sin arrancar la cuenta atrás del turno.
    public void showStraddleThinking() {
        Helpers.GUIRun(() -> {
            setPlayerBorder(Color.ORANGE);
            setActionBackground(new Color(204, 204, 204, 75));
            player_action.setForeground(Color.LIGHT_GRAY);
            setActionTextFitted(Translator.translate("straddle.pensando"));
            setPlayerActionIcon("action/thinking.png");
        });
    }

    // Limpia el visual "pensando" del straddle: vuelve el asiento al estado neutro (sin
    // acción + borde neutro), igual que disablePlayerAction. Si el straddle se posteó, la
    // ficha roja la pinta refreshPositionChipIcons aparte (en applyStraddlePost).
    public void clearStraddleThinking() {
        disablePlayerAction();
        Helpers.GUIRun(() -> setPlayerBorder(new Color(204, 204, 204, 75)));
    }

    @Override
    public void unsetSpectator() {
        this.spectator = false;

        Helpers.GUIRun(() -> {
            setPlayerBorder(new Color(204, 204, 204, 75));
            player_name.setIcon(null);
            player_stack.setEnabled(true);
            disablePlayerAction();

        });

    }

    private void actionIconZoom() {

        if (player_action_icon != null) {

            setPlayerActionIcon(player_action_icon);

        }
    }

    private void nickChipIconZoom() {

        Helpers.GUIRun(() -> {
            if (isActivo()) {
                if (nickname.equals(GameFrame.getInstance().getCrupier().getBb_nick())) {
                    Helpers.setScaledIconLabel(player_name, getClass().getResource("/images/bb.png"), Math.round(0.7f * player_name.getHeight()), Math.round(0.7f * player_name.getHeight()));
                } else if (nickname.equals(GameFrame.getInstance().getCrupier().getSb_nick())) {
                    Helpers.setScaledIconLabel(player_name, getClass().getResource("/images/sb.png"), Math.round(0.7f * player_name.getHeight()), Math.round(0.7f * player_name.getHeight()));
                } else if (nickname.equals(GameFrame.getInstance().getCrupier().getDealer_nick())) {
                    Helpers.setScaledIconLabel(player_name, getClass().getResource("/images/dealer.png"), Math.round(0.7f * player_name.getHeight()), Math.round(0.7f * player_name.getHeight()));
                } else {
                    player_name.setIcon(null);
                }
            } else {
                player_name.setIcon(null);
            }

            player_name.revalidate();
            player_name.repaint();
        });
    }

    private void utgIconZoom() {

        ImageIcon icon = new ImageIcon(IMAGEN_UTG.getImage().getScaledInstance((int) Math.round(player_name.getHeight() * (480f / 360f)), player_name.getHeight(), Image.SCALE_SMOOTH));

        Helpers.GUIRun(() -> {
            utg_icon.setIcon(icon);

            utg_icon.setPreferredSize(new Dimension((int) Math.round(player_name.getHeight() * (480f / 360f)), player_name.getHeight()));

            utg_icon.setVisible(utg);
        });
    }

    @Override
    public void showCards(String jugada) {
        this.muestra = true;
        Helpers.GUIRun(() -> {
            if (GameFrame.getInstance().getCrupier().getRabbit_players().containsKey(nickname)) {
                setActionBackground(Color.BLUE);
                setPlayerActionIcon("action/rabbit_action.png");
            } else {
                setActionBackground(new Color(51, 153, 255));
            }

            player_action.setForeground(Color.WHITE);
            setActionTextFitted(jugada);
        });
    }

    @Override
    public void resetBote() {
        this.bet = 0f;
        this.last_bote = this.bote;
        this.bote = 0f;
    }

    @Override
    public void setAvatar() {

        // Fallback robusto si player_pot aún no está layouted: usa
        // preferredSize, luego iconHeight del avatar actual, finalmente
        // un default razonable. Evita BufferedImage(0,0) -> exception.
        int h = player_pot.getHeight();
        if (h <= 0) {
            java.awt.Dimension prefDim = player_pot.getPreferredSize();
            if (prefDim != null && prefDim.height > 0) {
                h = prefDim.height;
            }
        }
        if (h <= 0 && avatar.getIcon() != null) {
            int iconH = avatar.getIcon().getIconHeight();
            if (iconH > 0) {
                h = iconH;
            }
        }
        if (h <= 0) {
            h = 64;
        }

        ImageIcon avatar;

        String avatar_path = GameFrame.getInstance().getNick2avatar().get(nickname);

        if (!"".equals(avatar_path) && !"*".equals(avatar_path)) {

            avatar = new ImageIcon(Helpers.makeImageRoundedCorner(new ImageIcon(new ImageIcon(avatar_path).getImage().getScaledInstance(h, h, Image.SCALE_SMOOTH)).getImage(), 20));

        } else if ("*".equals(avatar_path)) {

            avatar = new ImageIcon(Helpers.makeImageRoundedCorner(new ImageIcon(new ImageIcon(getClass().getResource("/images/avatar_bot.png")).getImage().getScaledInstance(h, h, Image.SCALE_SMOOTH)).getImage(), 20));

        } else {

            avatar = new ImageIcon(Helpers.makeImageRoundedCorner(new ImageIcon(new ImageIcon(getClass().getResource("/images/avatar_default.png")).getImage().getScaledInstance(h, h, Image.SCALE_SMOOTH)).getImage(), 20));
        }

        final int finalH = h;
        Helpers.GUIRun(() -> {
            getAvatar().setPreferredSize(new Dimension(finalH, finalH));

            getAvatar().setIcon(avatar);

            getAvatar().setVisible(true);
        });
    }

    @Override
    public boolean isCalentando() {

        return (spectator && Helpers.doubleSecureCompare(0f, stack) < 0);
    }

    @Override
    public boolean isActivo() {
        return (!exit && !spectator);
    }

    @Override
    public void setPagar(double pagar) {
        this.pagar = pagar;
    }

    // Serializa destapes animados concurrentes del mismo jugador (p.ej. un
    // SHOWCARDS duplicado/echo procesado en dos workers): el clásico era
    // idempotente vía el isTapada() dentro del EDT, el animado re-chequea
    // bajo este lock. Lock dedicado a propósito: NO sincronizar sobre this
    // (los métodos synchronized del jugador, como setPagar, no deben esperar
    // una animación).
    private final Object destape_animado_lock = new Object();

    public Object getDestape_animado_lock() {
        return destape_animado_lock;
    }

    // Muestra la jugada en el action label con estilo NEUTRO (el gris
    // translúcido del label en reposo): la usa la pasada de destapes del
    // showdown para enseñar QUÉ lleva el jugador sin adelantar el veredicto.
    // El azul de showCards queda reservado al botón MOSTRAR voluntario de los
    // foldeados. Mismo ajuste de fuente para jugadas largas que
    // setWinner/setLoser (que la repintarán encima en la pasada de
    // veredictos).
    public void showJugadaNeutral(String jugada) {

        Helpers.GUIRun(() -> {
            setActionBackground(new Color(204, 204, 204, 75));
            player_action.setForeground(Color.WHITE);

            setActionTextFitted(jugada);
        });
    }

    // Efectos colaterales del destape clásico que deben ocurrir al ARRANCAR el
    // giro animado (Crupier.mostrarAnimacionDestaparCartasJugador): ocultar la
    // ficha de apuesta y, si el parpadeo IWTSTH estaba activo, pararlo con su
    // recoloreado de loser (la etiqueta PIERDE ya estaba puesta y parpadeando,
    // no revela nada por adelantado). Réplica exacta de destaparCartas(boolean)
    // sin el destape de las cartas, que lo pone el motor animado.
    public void prepararDestapeAnimado() {

        Helpers.GUIRunAndWait(() -> {

            chip_label.setVisible(false);

            if (iwtsth_blink_timer.isRunning()) {

                iwtsth_blink_timer.stop();

                if (isLoser()) {
                    setActionBackground(Color.RED);
                    player_action.setForeground(Color.WHITE);
                    player_action.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                }
            }
        });
    }

    @Override
    public void destaparCartas(boolean sound) {

        Helpers.GUIRun(() -> {

            if (getHoleCard1().isIniciada() && getHoleCard1().isTapada()) {

                if (sound && GameFrame.destapeSonidoOn()) {
                    Helpers.threadRun(() -> Audio.playPreloadedWav("misc/uncover.wav"));
                }

                chip_label.setVisible(false);

                getHoleCard1().destapar(false);

                getHoleCard2().destapar(false);

                if (iwtsth_blink_timer.isRunning()) {

                    iwtsth_blink_timer.stop();

                    if (isLoser()) {
                        setActionBackground(Color.RED);
                        player_action.setForeground(Color.WHITE);
                        player_action.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                    }
                }
            }
        });
    }

    // synchronized: el swap permuta los valores entre los dos componentes Card
    // en varios pasos y puede llamarse desde hilos distintos (crupier, worker
    // de SHOWCARDS, worker de IWTSTH). Dos swaps concurrentes podrían dejar
    // las dos cartas con el mismo valor; serializado es idempotente (el
    // segundo ve c1 >= c2 y no toca nada). Monitor de this a propósito: es un
    // intercambio de microsegundos, nunca se anima ni se bloquea aquí dentro.
    @Override
    public synchronized void ordenarCartas() {
        if (getHoleCard1().getValorNumerico() != -1 && getHoleCard2().getValorNumerico() != -1 && getHoleCard1().getValorNumerico() < getHoleCard2().getValorNumerico()) {

            //Ordenamos las cartas para mayor comodidad
            String valor1 = this.holeCard1.getValor();
            String palo1 = this.holeCard1.getPalo();
            boolean desenfocada1 = this.holeCard1.isDesenfocada();

            this.holeCard1.actualizarValorPaloEnfoque(this.holeCard2.getValor(), this.holeCard2.getPalo(), this.holeCard2.isDesenfocada());
            this.holeCard2.actualizarValorPaloEnfoque(valor1, palo1, desenfocada1);
        }
    }

    @Override
    public void setSpectatorBB(boolean bb) {
        this.spectator_bb = bb;
    }

    @Override
    public void checkGameOver() {
        if (isActivo() && Helpers.doubleSecureCompare(0f, getEffectiveStack()) == 0) {
            Helpers.GUIRun(() -> {
                setPlayerActionIcon("action/skull.png");
                setOpaque(true);
                setBackground(Color.RED);

            });

        }
    }

    // Cuenta atrás visual "¿RECOMPRA? (N)" en la action label mientras este
    // humano remoto decide EN SU máquina si recompra (GameOverDialog/
    // RebuyDialog locales). Puramente cosmética y solo para humanos: la
    // activa/apaga recibirRebuys (en el host los bots ni entran en la espera
    // y en los clientes su REBUY llega al instante desde el host). Mantiene
    // la calavera de checkGameOver (no toca el icono); temporizador LOCAL de
    // 1 seg, aproximado (sin sincronía con el diálogo real del remoto):
    // cuenta los mismos segundos que el RebuyDialog del game over y al agotar
    // se queda fijo en "¿RECOMPRA?" a secas (nunca muestra el cero) — para
    // entonces el remoto normalmente ya habrá pulsado y su REBUY estará al
    // llegar. Al apagarse restaura el texto previo (la jugada con la que
    // perdió); si la decisión fue quedarse de espectador, setSpectator ya
    // puso this.spectator y el restore se omite (su repaint manda). Todo
    // corre en el EDT (Timer de Swing).
    public void setRebuying(boolean rebuying) {
        setRebuying(rebuying, false);
    }

    // 'recompro' solo aplica al apagar: true si la decisión del arruinado fue
    // RECOMPRAR — la action label pasa a "¡RECOMPRA!" como feedback del
    // desenlace (con un solo arruinado la espera acaba al instante y sin esto
    // no daría tiempo a ver qué pasó) y ahí se queda hasta el repintado de la
    // mano siguiente. Con false (espectador/exit/timeout) se restaura el texto
    // previo, y si procede setSpectator repinta encima.
    public void setRebuying(boolean rebuying, boolean recompro) {
        Helpers.GUIRun(() -> {
            if (rebuying) {
                if (this.exit || this.spectator || rebuying_visual) {
                    return;
                }
                rebuying_visual = true;
                rebuy_countdown_saved_text = player_action.getText();
                // Snapshot LOCAL de CINEMATICAS al empezar: decide el modo para
                // toda la espera (toggles posteriores del menú no afectan).
                if (GameFrame.cinematicasOn()) {
                    // Modo GIF: la label queda FIJA en "¿RECOMPRA?" (sin número)
                    // y la cuenta atrás la pone el GIF de game over sobre las
                    // cartas — entero UNA vez (por frames, sin reloj) y con su
                    // audio; al terminar queda fijo el de cero hasta que el
                    // rebuy se resuelva.
                    setActionTextFitted(Translator.translate("rebuy.recompra_3"));
                    // repaint() del slot completo tras cada setText (mismo idiom
                    // que setPlayerActionIcon): el slot y el action panel son
                    // rounded rects opacos que NO pintan sus esquinas
                    // (RoundedPanel / paintComponent de esta clase) y un repaint
                    // parcial deja píxeles huérfanos en las 4 esquinas.
                    repaint();
                    mostrarRebuyGameOverGif(++rebuy_generation);
                } else {
                    // Modo sin cinemáticas: cuenta atrás numérica en la label.
                    final int[] count = {GameOverDialog.REBUY_DIALOG_COUNTDOWN};
                    setActionTextFitted(Translator.translate("rebuy.recompra_3") + " (" + count[0] + ")");
                    repaint();
                    rebuy_countdown_timer = new Timer(1000, (e) -> {
                        if (--count[0] > 0) {
                            setActionTextFitted(Translator.translate("rebuy.recompra_3") + " (" + count[0] + ")");
                        } else {
                            setActionTextFitted(Translator.translate("rebuy.recompra_3"));
                            ((Timer) e.getSource()).stop();
                        }
                        repaint();
                    });
                    rebuy_countdown_timer.start();
                }
            } else {
                if (!rebuying_visual) {
                    return;
                }
                rebuying_visual = false;
                rebuy_generation++;
                if (rebuy_countdown_timer != null) {
                    rebuy_countdown_timer.stop();
                    rebuy_countdown_timer = null;
                }
                if (rebuy_gif_label.isVisible()) {
                    rebuy_gif_label.setVisible(false);
                    // setIcon(null) resetea el audio pendiente de la GifLabel:
                    // sin esto, un REBUY que llegara entre el show y el PRIMER
                    // frame del GIF dejaría el wav huérfano (el stop de abajo
                    // correría antes de que el frame 1 lo disparase). Además
                    // suelta la referencia a la Image del GIF.
                    rebuy_gif_label.setIcon((javax.swing.Icon) null);
                    // Con varios arruinados a la vez suena UN solo game_over.wav:
                    // se corta cuando el último visual del grupo se retira.
                    if (--REBUY_GIF_ACTIVOS <= 0) {
                        REBUY_GIF_ACTIVOS = 0;
                        Audio.stopWavResource("misc/game_over.wav");
                    }
                }
                if (rebuy_countdown_saved_text != null) {
                    if (!this.exit && !this.spectator) {
                        if (recompro) {
                            // Feedback del desenlace: recompró — fuera la
                            // calavera, gafas de sol.
                            setActionTextFitted(Translator.translate("rebuy.recompra_4"));
                            setPlayerActionIcon("action/glasses.png");
                        } else {
                            setActionTextFitted(rebuy_countdown_saved_text);
                        }
                        repaint();
                    }
                    rebuy_countdown_saved_text = null;
                }
            }
        });
    }

    // Muestra SOLO el desenlace del rebuy (RECOMPRA con gafas) sin haber lanzado
    // antes la cuenta atrás. Se usa cuando el jugador LOCAL también estaba
    // arruinado: en ese caso recibirRebuys corre DESPUÉS de su game-over modal y
    // un GIF de cuenta atrás remoto saldría desincronizado, así que no se lanza;
    // basta con reflejar el resultado. El caso "no recompra" lo pinta setSpectator.
    public void showRebuyOutcome(boolean recompro) {
        Helpers.GUIRun(() -> {
            if (recompro && !this.exit && !this.spectator) {
                setActionTextFitted(Translator.translate("rebuy.recompra_4"));
                setPlayerActionIcon("action/glasses.png");
                repaint();
            }
        });
    }

    // GIF de game over sobre las cartas mientras este arruinado decide la
    // recompra (solo lo lanza setRebuying en modo CINEMATICAS). El de cuenta
    // atrás se reproduce entero UNA vez, gobernado por sus frames (sin reloj)
    // y con su audio (solo el PRIMER arruinado del grupo lo engancha); al
    // terminar se fija el de cero hasta que setRebuying(false) lo retire
    // (REBUY recibido, exit o timeout del crupier). Escalado/centrado como
    // las notificaciones del chat. URLs cache-busted con fragmento único:
    // Toolkit cachea las Image por URL y dos arruinados simultáneos
    // compartirían la animación pisándose los contadores de frames. 'gen'
    // invalida el show/swap si el rebuy se resolvió entre medias.
    private void mostrarRebuyGameOverGif(int gen) {
        Helpers.threadRun(() -> {
            try {
                URL countdown_url = getClass().getResource("/cinematics/misc/game_over.gif");
                ImageIcon gif = new ImageIcon(new URL(countdown_url.toString() + "#" + String.valueOf(System.nanoTime())));
                while (gif.getIconHeight() == 0 || gif.getIconWidth() == 0) {
                    Helpers.pausar(GUI_RENDER_WAIT);
                }

                int max_width = panel_cartas.getWidth();
                int new_height = panel_cartas.getHeight();
                int new_width = (int) Math.round((gif.getIconWidth() * new_height) / gif.getIconHeight());
                if (new_width > max_width) {
                    new_height = (int) Math.round((new_height * max_width) / new_width);
                    new_width = max_width;
                }

                final int width = new_width;
                final int height = new_height;
                final int frames = Helpers.getGIFFramesCount(countdown_url);
                final CyclicBarrier barrier = new CyclicBarrier(2);

                Helpers.GUIRun(() -> {
                    if (gen != rebuy_generation) {
                        return;
                    }
                    rebuy_gif_label.setBarrier(barrier);
                    rebuy_gif_label.setIcon(gif, frames);
                    rebuy_gif_label.setRepeat(1);
                    // El audio se engancha DESPUÉS de setIcon (setIcon lo
                    // resetea); end_frame -1 = el wav suena entero y lo corta
                    // setRebuying(false) si el rebuy se resuelve antes. Solo el
                    // primero del grupo: UN audio aunque haya varios GIFs. Si el
                    // local está en su propio game over interactivo, el diálogo
                    // local es el dueño del game_over.wav (y lo corta al decidir)
                    // → estos GIF remotos van mudos para no doblar el audio.
                    if (REBUY_GIF_ACTIVOS == 0 && !LOCAL_GAMEOVER_OWNS_AUDIO) {
                        rebuy_gif_label.addAudio(GameFrame.finPartidaSonidoOn() ? "misc/game_over.wav" : null, 1, -1);
                    }
                    REBUY_GIF_ACTIVOS++;
                    rebuy_gif_label.setSize(width, height);
                    rebuy_gif_label.setPreferredSize(rebuy_gif_label.getSize());
                    rebuy_gif_label.setOpaque(false);
                    rebuy_gif_label.setLocation(Math.round((panel_cartas.getWidth() - width) / 2), Math.round((getHoleCard1().getHeight() - height) / 2));
                    rebuy_gif_label.setVisible(true);
                });

                // GifLabel dispara la barrera al completar la única pasada del
                // GIF; cap defensivo generoso por si el recurso no llegara a
                // animar (el gen-check de abajo aborta el swap si ya no toca).
                try {
                    barrier.await(60, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception ex) {
                }

                URL zero_url = getClass().getResource("/cinematics/misc/game_over_zero.gif");
                ImageIcon zero = new ImageIcon(new URL(zero_url.toString() + "#" + String.valueOf(System.nanoTime())));
                while (zero.getIconHeight() == 0 || zero.getIconWidth() == 0) {
                    Helpers.pausar(GUI_RENDER_WAIT);
                }
                final int zero_frames = Helpers.getGIFFramesCount(zero_url);

                Helpers.GUIRun(() -> {
                    if (gen != rebuy_generation || !rebuy_gif_label.isVisible()) {
                        return;
                    }
                    // Cero FIJO: una pasada y GifLabel deja de pedir frames
                    // (se congela en el último); lo retira setRebuying(false).
                    rebuy_gif_label.setBarrier(null);
                    rebuy_gif_label.setIcon(zero, zero_frames);
                    rebuy_gif_label.setRepeat(1);
                });

            } catch (Exception ex) {
                Logger.getLogger(RemotePlayer.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
    }

    /**
     * Muestra el GIF de barajado (MUDO, en bucle) + borde blanco de resaltado sobre este jugador.
     * Lo invoca el controlador de GameFrame desde su hilo serializador (NO el EDT), que garantiza
     * un overlay a la vez y su duración mínima. Carga el GIF de forma SÍNCRONA (por eso NO debe
     * llamarse desde el EDT) y luego pinta en el EDT.
     */
    @Override
    public void showShuffleCascadeOverlay() {
        final ImageIcon icon;
        try {
            icon = ensureShuffleCascadeIcon();
        } catch (Exception ex) {
            Logger.getLogger(RemotePlayer.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }
        if (icon == null) {
            return;
        }
        final int frames = shuffle_cascade_frames;
        if (frames <= 0) {
            return; // GIF sin Graphic Control Extension (deck mod): el bucle de imageUpdate no se cortaría al ocultar
        }
        Helpers.GUIRun(() -> {
            int max_width = panel_cartas.getWidth();
            int new_height = panel_cartas.getHeight();
            if (icon.getIconHeight() <= 0 || new_height <= 0) {
                return;
            }
            // GifLabel estira la Image a los bounds por GPU, así que basta el tamaño del label.
            int new_width = (int) Math.round((icon.getIconWidth() * (double) new_height) / icon.getIconHeight());
            if (max_width > 0 && new_width > max_width) {
                new_height = (int) Math.round(((double) new_height * max_width) / new_width);
                new_width = max_width;
            }
            shuffle_cascade_gif_label.setBarrier(null);
            shuffle_cascade_gif_label.setIcon(icon, frames);
            shuffle_cascade_gif_label.setRepeat(Integer.MAX_VALUE); // bucle hasta hideShuffleCascadeOverlay
            shuffle_cascade_gif_label.setSize(new_width, new_height);
            shuffle_cascade_gif_label.setPreferredSize(shuffle_cascade_gif_label.getSize());
            shuffle_cascade_gif_label.setOpaque(false);
            shuffle_cascade_gif_label.setLocation(Math.round((panel_cartas.getWidth() - new_width) / 2f), Math.round((getHoleCard1().getHeight() - new_height) / 2f));
            shuffle_cascade_gif_label.setVisible(true);
            // Borde blanco de resaltado del turno (guarda el color previo para restaurarlo en hide).
            if (!shuffle_border_active) {
                shuffle_border_saved = border_color;
                shuffle_border_active = true;
            }
            border_color = java.awt.Color.WHITE;
            repaint();
        });
    }

    /**
     * Oculta el overlay de barajado y restaura el borde previo. Idempotente: seguro aunque no
     * haya overlay visible. setIcon(null) resetea a 1 el repeat de la GifLabel (corta el bucle).
     */
    @Override
    public void hideShuffleCascadeOverlay() {
        Helpers.GUIRun(() -> {
            shuffle_cascade_gif_label.setVisible(false);
            shuffle_cascade_gif_label.setIcon((javax.swing.Icon) null);
            if (shuffle_border_active) {
                // Solo restaurar si el borde sigue siendo el blanco que pusimos: si otro código lo
                // cambió mientras tanto (p.ej. el resaltado de turno de apuesta), respetarlo.
                if (border_color == java.awt.Color.WHITE) {
                    border_color = shuffle_border_saved;
                    repaint();
                }
                shuffle_border_active = false;
            }
        });
    }

    /**
     * Decodifica (una vez por instancia, cache-busted) el ImageIcon del shuffle.gif de la
     * baraja ACTUAL y cuenta sus frames; null si no hay GIF de barajado o no llegó a
     * dimensionarse. Se recarga si cambia la baraja. Bloquea el hilo (de fondo) hasta que la
     * Image reporta tamaño, con tope duro de 3 s. Cache-bust con fragmento único: el Toolkit
     * cachea las Image por URL para toda la vida de la JVM y compartir la del central_label del
     * barajado pisaría los contadores de frames.
     */
    private ImageIcon ensureShuffleCascadeIcon() throws Exception {
        URL url = Crupier.shuffleGifUrl();
        if (url == null) {
            return null;
        }
        String url_key = url.toString();
        ImageIcon cached = shuffle_cascade_icon;
        if (cached != null && url_key.equals(shuffle_cascade_icon_url)) {
            return cached;
        }
        ImageIcon icon = new ImageIcon(new URL(url.toString() + "#cascade" + System.nanoTime()));
        long t0 = System.nanoTime();
        while ((icon.getIconHeight() == 0 || icon.getIconWidth() == 0)
                && System.nanoTime() - t0 < 3_000_000_000L) {
            Helpers.pausar(GUI_RENDER_WAIT);
        }
        if (icon.getIconHeight() == 0 || icon.getIconWidth() == 0) {
            return null;
        }
        shuffle_cascade_frames = Helpers.getGIFFramesCount(url);
        shuffle_cascade_icon = icon;
        shuffle_cascade_icon_url = url_key;
        return icon;
    }

    @Override
    public void setPlayerActionIcon(String icon) {

        if (!isTimeout() || "action/timeout.png".equals(icon) || icon == null) {

            if (!"action/timeout.png".equals(icon)) {
                player_action_icon = icon;
            }

            Helpers.GUIRun(() -> {
                player_action.setIcon(icon != null ? new ImageIcon(new ImageIcon(getClass().getResource("/images/" + icon)).getImage().getScaledInstance(Math.round(0.7f * player_action.getHeight()), Math.round(0.7f * player_action.getHeight()), Image.SCALE_SMOOTH)) : null);

                repaint();
            });
        }
    }

    public void hidePlayerActionIcon() {

        Helpers.GUIRun(() -> {
            player_action.setIcon(null);
        });

    }

    /**
     * Sets {@code msg} on the action label, auto-shrinking the font (measured
     * with FontMetrics) so a long hand name fits the label width, and restoring
     * the original size when it fits again. Must run on the EDT.
     */
    private void setActionTextFitted(String msg) {
        // Cualquier texto de accion NORMAL (CALL/RAISE/pensando/se pira/reset...) invalida
        // el rodaje del % del all-in: el proximo % saltara en vez de rodar desde un valor
        // que ya no aplica (p.ej. el de un all-in anterior). El propio rodaje y el "(--%)"
        // usan setActionTextFittedRaw para NO auto-invalidarse.
        if (jugada_prob_roller != null) {
            jugada_prob_roller.invalidate();
        }
        setActionTextFittedRaw(msg);
    }

    private void setActionTextFittedRaw(String msg) {

        Font base_font = (orig_action_font != null) ? orig_action_font : player_action.getFont();

        Insets insets = player_action.getInsets();

        int available_width = (player_action.getWidth() > 0 ? player_action.getWidth() : player_action.getPreferredSize().width) - (insets != null ? insets.left + insets.right : 0);

        Font fitted_font = Helpers.fitFontToWidth(player_action, msg, base_font, available_width, Math.max(9, Math.round(base_font.getSize() * 0.5f)));

        if (fitted_font.getSize() < base_font.getSize()) {
            orig_action_font = base_font;
            player_action.setFont(fitted_font);

        } else if (orig_action_font != null) {
            player_action.setFont(orig_action_font);
            orig_action_font = null;
        }

        player_action.setText(msg);
    }

    // Rodaje vivo del % de probabilidad del all-in en la label de accion (JUGADA + PROB).
    // El numero rueda a velocidad constante conservando el nombre de la jugada como
    // prefijo; el render reconstruye "JUGADA (NN%)" via setActionTextFittedRaw (para no
    // auto-invalidarse) y lo pasa por el auto-fit de fuente. EDT-only (creacion perezosa).
    private RollingCounter jugada_prob_roller;
    private String jugada_prob_prefix = "";

    private RollingCounter jugadaProbRoller() {
        if (jugada_prob_roller == null) {
            jugada_prob_roller = new RollingCounter(
                    (v) -> setActionTextFittedRaw(jugada_prob_prefix + " (" + Helpers.floatClean((float) v) + "%)"),
                    GameFrame.PROB_ROLL_MS);
        }
        return jugada_prob_roller;
    }

    @Override
    public void setJugadaParcial(Hand jugada, boolean ganador, float win_per) {

        Helpers.GUIRun(() -> {
            setActionBackground(ganador ? new Color(120, 200, 0) : new Color(230, 70, 0));

            player_action.setForeground(ganador ? Color.BLACK : Color.WHITE);

            setPlayerActionIcon(null);

            jugada_prob_prefix = jugada.getName();

            if (win_per >= 0) {
                // Rueda solo el % conservando el nombre de la jugada. Gate por la opcion
                // "Contadores" de Apariencia (isCounterRollEnabled; salta en recover). Via el
                // roller -> render con setActionTextFittedRaw (no se auto-invalida).
                boolean animate = GameFrame.isCounterRollEnabled();
                RollingCounter roller = jugadaProbRoller();
                // Primer reveal del all-in: el roller no tiene valor (la accion previa lo
                // invalido), asi que roll() saltaria de golpe SOLO la primera calle y animaria
                // las siguientes. Sembramos 0 para que ruede 0->% en la misma duracion fija,
                // de modo que TODAS las calles tarden igual.
                if (animate && !roller.isValid()) {
                    roller.set(0);
                }
                roller.roll(win_per, animate);
            } else {
                // Aun sin simulacion: "(--%)" en crudo (sin invalidar) para que el valor del
                // roller sobreviva y el % de la calle siguiente ruede desde el actual.
                setActionTextFittedRaw(jugada_prob_prefix + " (--%)");
            }
        });
    }

    @Override
    public void setContaWin(int conta) {
        this.conta_win = conta;

        if (this.conta_win > 0) {
            Helpers.GUIRun(() -> {
                hands_win.setText(String.valueOf(conta_win));
                hands_win.setVisible(true);
            });
        }
    }

    @Override
    public int getContaWin() {
        return this.conta_win;
    }

}
