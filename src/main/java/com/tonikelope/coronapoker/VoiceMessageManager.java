/*
 * Copyright (C) 2026 tonikelope
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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

/**
 * Push-to-record voice messages: hold the configured key (F9 by default) in
 * game to record up to VoiceRecorder.MAX_SECONDS, release to send. While
 * recording, a translucent red dialog with a draining countdown bar floats
 * centered over the table. Key events arrive on the EDT from the global
 * KeyEventDispatcher installed by Init.
 *
 * @author tonikelope
 */
public class VoiceMessageManager {

    public static final int DEFAULT_KEY = KeyEvent.VK_F9;
    public static final float DIALOG_OPACITY = 0.95f;

    private static volatile int VOICE_KEY;
    private static volatile boolean CAPTURING_KEY = false;
    private static volatile VoiceRecorder RECORDER = null;
    private static volatile JDialog RECORD_DIALOG = null;
    private static volatile JProgressBar RECORD_BAR = null;
    private static volatile javax.swing.Timer AUTO_SEND_TIMER = null;
    private static volatile boolean WAIT_KEY_RELEASE = false;
    private static volatile long RECORD_START_NANOS = 0L;
    private static volatile long KEY_PRESS_NANOS = 0L;
    private static final AtomicBoolean WARNING_SHOWING = new AtomicBoolean(false);

    static {

        int key = DEFAULT_KEY;

        try {
            key = Integer.parseInt(Helpers.PROPERTIES.getProperty("voice_message_key", String.valueOf(DEFAULT_KEY)));
        } catch (NumberFormatException ex) {
        }

        VOICE_KEY = key;
    }

    public static int getVoiceKey() {
        return VOICE_KEY;
    }

    public static void setVoiceKey(int key_code) {

        VOICE_KEY = key_code;

        Helpers.PROPERTIES.setProperty("voice_message_key", String.valueOf(key_code));

        Helpers.savePropertiesFile();
    }

    // The audio settings dialog raises this while it captures a new key, so
    // pressing the current one does not start a recording.
    public static void setCapturingKey(boolean capturing) {
        CAPTURING_KEY = capturing;
    }

    /**
     * Global hook (EDT): reacts to the configured key only in game. Returns
     * true when the event has been consumed.
     */
    public static boolean handleKeyEvent(KeyEvent e) {

        if (CAPTURING_KEY || GameFrame.getInstance() == null || WaitingRoomFrame.getInstance() == null) {
            return false;
        }

        // WhatsApp-style cancel: while the voice key is still held and a note is
        // being recorded, BACKSPACE discards it instead of sending. Only acts
        // when a recorder is live, so it never swallows backspace otherwise.
        if (RECORDER != null && e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
            cancel();
            return true;
        }

        if (e.getKeyCode() != VOICE_KEY) {
            return false;
        }

        // A letter configured as voice key must not swallow chat typing
        if (e.getComponent() instanceof javax.swing.text.JTextComponent) {
            return false;
        }

        if (e.getID() == KeyEvent.KEY_PRESSED) {
            keyPressed();
            return true;
        }

        if (e.getID() == KeyEvent.KEY_RELEASED) {
            keyReleased();
            return true;
        }

        return false;
    }

    /**
     * Press-to-record entry point for the in-game mic button (left mouse button
     * held down). It behaves exactly like holding the voice key: starts a
     * recording under the same guards (voice messages enabled, not blocked, mic
     * ready) and the same on-screen dialog / auto-send safety net.
     */
    public static void buttonPressed() {

        if (CAPTURING_KEY || GameFrame.getInstance() == null || WaitingRoomFrame.getInstance() == null) {
            return;
        }

        keyPressed();
    }

    /**
     * Release entry point for the in-game mic button: stops and sends the note
     * exactly like releasing the voice key (or cancels it if the talk-now
     * dialog had not appeared yet).
     */
    public static void buttonReleased() {
        keyReleased();
    }

    private static void keyPressed() {

        // Key auto-repeat fires PRESSED again while held, and after an
        // auto-send we ignore the key until it is physically released.
        if (RECORDER != null || WAIT_KEY_RELEASE) {
            return;
        }

        if (!GameFrame.VOICE_MESSAGES) {
            warning("audio.notas_desactivadas");
            return;
        }

        // Self-block is symmetric: no incoming auto-play AND no sending
        if (AudioDeviceManager.isBlockVoiceMessages()) {
            warning("audio.notas_bloqueadas");
            return;
        }

        if (!AudioDeviceManager.isMicEnabled()) {
            warning("audio.microfono_no_configurado");
            return;
        }

        VoiceRecorder recorder = new VoiceRecorder();

        RECORDER = recorder;

        KEY_PRESS_NANOS = System.nanoTime();

        // Total local silence while recording: the mic must not pick up
        // music, effects or other voices.
        Audio.setVoiceRecording(true);

        // Teardown for a capture that ended on its own, either because the mic
        // was never alive or because it died mid-note. Everything runs on the
        // EDT, where keyPressed and keyReleased also live, so RECORDER is only
        // ever swapped from one thread and a fresh note on the held key cannot
        // be torn down by the previous one (RECORDER == recorder guard).
        Runnable capture_ended = () -> Helpers.GUIRun(() -> {

            if (RECORDER != recorder) {
                return;
            }

            // The key is still down (the recorder only calls this when nobody
            // asked it to stop), so it must be ignored until released.
            WAIT_KEY_RELEASE = true;

            // Once there is audio the user is committed, so whatever was
            // captured before the mic went away still gets its chance;
            // stopAndSend reports why if nothing usable comes back. It is asked
            // to the recorder and not to the dialog because the dialog can be
            // missing for reasons of its own (a game window closing under it),
            // and that must not throw away a good note.
            boolean committed = (recorder.getCapturedMillis() > 0);

            stopAndSend(!committed);

            // No dialog ever appeared and the capture is already over: the
            // device took the line and never delivered a single sample, which
            // is a mic to fix in the settings rather than a note that failed.
            if (!committed) {
                warning("audio.microfono_no_configurado");
            }
        });

        // A mic that fails before any audio never gets a dialog, so the reason
        // has to be shown from here
        java.util.function.Consumer<String> start_failed = (i18n_key) -> Helpers.GUIRun(() -> {

            if (RECORDER != recorder) {
                return;
            }

            WAIT_KEY_RELEASE = true;

            stopAndSend(true);

            warning(i18n_key);
        });

        try {

            Helpers.threadRun(() -> {

                // Opening the mic takes 100-400ms and the past cannot be
                // captured: the dialog only shows when the FIRST audio arrives
                // from the device (line.start() returns before the driver
                // really delivers), so it is an honest talk-now signal. The EDT
                // never blocks on the driver.
                VoiceRecorder.Outcome started = recorder.start(() -> showRecordDialogAndArmTimer(recorder), capture_ended);

                if (started == VoiceRecorder.Outcome.NO_LINE) {

                    start_failed.accept("audio.microfono_no_configurado");

                } else if (started == VoiceRecorder.Outcome.BUSY) {

                    start_failed.accept("audio.microfono_ocupado");
                }

                // Otherwise the dialog and the timer are armed by the on_live
                // callback, and capture_ended covers a capture that dies later.
            });

        } catch (Exception ex) {

            // The pool is gone (a hand ending right on the keystroke): without
            // this the note would stay half started forever, with the game
            // muted and the recorder never cleared.
            Logger.getLogger(VoiceMessageManager.class.getName()).log(Level.SEVERE, "Voice note could not be started: {0}", ex.getMessage());

            RECORDER = null;

            Audio.setVoiceRecording(false);
        }
    }

    private static void keyReleased() {

        WAIT_KEY_RELEASE = false;

        if (RECORDER != null) {
            // Releasing before the talk-now dialog appears means the mic was
            // still opening (or it was just an accidental tap): cancel like
            // WhatsApp instead of sending an empty or clipped note. Once the
            // dialog is up it is the honest commitment point, so the note ships.
            stopAndSend(RECORD_DIALOG == null);
        }
    }

    private static void cancel() {

        if (RECORDER == null) {
            return;
        }

        // Holding the voice key past the cancel must not start a fresh note:
        // it is ignored until the key is physically released.
        WAIT_KEY_RELEASE = true;

        stopAndSend(true);
    }

    private static void stopAndSend(boolean discard) {

        VoiceRecorder recorder = RECORDER;

        if (recorder == null) {
            return;
        }

        RECORDER = null;

        // How long the talk-now dialog was actually up, to be compared against
        // the audio that comes back
        long start_nanos = RECORD_START_NANOS;

        RECORD_START_NANOS = 0L;

        long press_nanos = KEY_PRESS_NANOS;

        KEY_PRESS_NANOS = 0L;

        // Both times are sampled here, on the EDT, at the instant the note
        // actually ends: measuring them after the pool task has run would count
        // the tail grace and the queue wait as if the user had been talking.
        long held_millis = press_nanos > 0L ? (System.nanoTime() - press_nanos) / 1000000L : 0L;
        long dialog_millis = start_nanos > 0L ? (System.nanoTime() - start_nanos) / 1000000L : 0L;

        // Dropping a note without ever showing the talk-now dialog is the one
        // failure the user cannot tell apart from a bug: no note, no warning
        // and, until now, no trace either.
        if (discard && start_nanos == 0L && press_nanos > 0L) {
            Logger.getLogger(VoiceMessageManager.class.getName()).log(Level.WARNING,
                    "Voice note discarded with no talk-now dialog: key held {0} ms, {1} ms of audio",
                    new Object[]{held_millis, recorder.getCapturedMillis()});
        }

        javax.swing.Timer auto_send = AUTO_SEND_TIMER;

        AUTO_SEND_TIMER = null;

        if (auto_send != null) {
            auto_send.stop();
        }

        closeRecordDialog();

        try {

            Helpers.threadRun(() -> stopAndSendTask(recorder, discard, dialog_millis));

        } catch (Exception ex) {

            // The pool died between starting the note and ending it (a hand
            // finishing right on the keystroke). Without lifting the recording
            // silence here, the whole game stays muted until the app restarts.
            Logger.getLogger(VoiceMessageManager.class.getName()).log(Level.SEVERE, "Voice note could not be finished: {0}", ex.getMessage());

            Audio.setVoiceRecording(false);
        }
    }

    private static void stopAndSendTask(VoiceRecorder recorder, boolean discard, long dialog_millis) {

        byte[] wav;

        try {
            wav = recorder.stop();
        } finally {
            // The tail grace is over: lift the local recording silence. In a
            // finally because leaving it raised mutes every sound in the game
            // for the rest of the session.
            Audio.setVoiceRecording(false);
        }

        // A note is as long as the talk-now dialog was up: anything much
        // shorter means the device stopped delivering behind our back. The
        // recorder already detects and reports the ways it knows about, so this
        // is the net for the ones it does not.
        if (dialog_millis > 0L) {

            long captured = recorder.getCapturedMillis();

            if (captured * 2 < dialog_millis) {
                Logger.getLogger(VoiceMessageManager.class.getName()).log(Level.WARNING,
                        "Voice note much shorter than the recording: {0} ms captured, {1} ms recording, outcome {2}",
                        new Object[]{captured, dialog_millis, recorder.getOutcome()});
            }
        }

        WaitingRoomFrame sala = WaitingRoomFrame.getInstance();

        // A note the user committed to (the talk-now dialog was up) that
        // comes back with nothing is a failure worth showing: staying quiet
        // is what made a dead mic look like a note nobody answered.
        if (!discard && wav == null) {

            VoiceRecorder.Outcome outcome = recorder.getOutcome();

            if (outcome == VoiceRecorder.Outcome.SILENT) {
                warning("audio.microfono_sin_audio");
            } else if (outcome == VoiceRecorder.Outcome.LOST) {
                warning("audio.microfono_perdido");
            } else if (outcome == VoiceRecorder.Outcome.ENCODE_ERROR) {
                warning("audio.nota_no_grabada");
            } else {
                // EMPTY: released the instant the dialog appeared, nothing
                // worth interrupting the game for
                Logger.getLogger(VoiceMessageManager.class.getName()).log(Level.WARNING, "Voice note produced nothing usable: {0}", outcome);
            }
        }

        // null = nothing meaningful captured
        if (!discard && wav != null && sala != null) {

            String nick = sala.getLocal_nick();

            // Local processing first (chat line + own playback, like the
            // local TTS when sending a text). On the host this also relays
            // to every client, so enviarNotaVoz is client-only to avoid a
            // double broadcast.
            sala.recibirNotaVoz(nick, wav);

            if (!sala.isServer()) {
                sala.enviarNotaVoz(nick, wav);
            }
        }
    }

    /**
     * Plays a stored voice note when its chat line is clicked. The line shows
     * [Reproduciendo...] while it plays and reverts afterwards; clicking
     * another note cuts the current one (whose label reverts on its own).
     */
    public static void playFromChat(String filename) {

        // The href travels inside chat HTML: never let it escape VOICE_DIR
        if (filename == null || !filename.matches("[A-Za-z0-9._-]+")) {
            return;
        }

        WaitingRoomFrame sala = WaitingRoomFrame.getInstance();

        if (sala == null) {
            return;
        }

        java.io.File file = new java.io.File(Init.VOICE_DIR + "/" + filename);

        if (!file.isFile()) {
            // Deleted from disk
            warning("audio.nota_no_encontrada");
            return;
        }

        CoronaMP3FilePlayer current = Audio.TTS_PLAYER;

        if (current != null) {
            current.stop();
        }

        Helpers.threadRun(() -> {

            byte[] wav;

            try {
                wav = java.nio.file.Files.readAllBytes(file.toPath());
            } catch (Exception ex) {
                warning("audio.nota_no_encontrada");
                return;
            }

            sala.setVoiceNoteChatLabel(filename, true);

            try {
                Audio.playVoiceMessage(wav, null);
            } finally {
                sala.setVoiceNoteChatLabel(filename, false);
            }
        });
    }

    private static void warning(String i18n_key) {

        // Key auto-repeat must not stack popups
        if (WARNING_SHOWING.compareAndSet(false, true)) {

            try {

                Helpers.threadRun(() -> {
                    try {
                        java.awt.Container parent = GameFrame.getInstance() != null ? GameFrame.getInstance().getContentPane()
                                : (WaitingRoomFrame.getInstance() != null ? WaitingRoomFrame.getInstance().getContentPane() : Init.VENTANA_INICIO);

                        Helpers.mostrarMensajeError(parent, Translator.translate(i18n_key));
                    } finally {
                        WARNING_SHOWING.set(false);
                    }
                });

            } catch (Exception ex) {
                // The flag lives in the task: a rejected submit (pool shut down
                // between hands) would latch it and silence every later warning
                WARNING_SHOWING.set(false);
                Logger.getLogger(VoiceMessageManager.class.getName()).log(Level.WARNING, "Cannot show the voice note warning {0}: {1}", new Object[]{i18n_key, ex.getMessage()});
            }
        }
    }

    private static void showRecordDialogAndArmTimer(VoiceRecorder recorder) {

        Helpers.GUIRun(() -> {

            GameFrame game_frame = GameFrame.getInstance();

            // The key may have been released while the mic was opening: the
            // EDT serializes this against keyReleased, so the check is race
            // free and no orphan dialog can appear.
            if (game_frame == null || RECORDER != recorder) {
                return;
            }

            JDialog dialog = new JDialog(game_frame);

            dialog.setUndecorated(true);

            dialog.setFocusableWindowState(false);

            JLabel title = new JLabel(Translator.translate("audio.grabando_nota_de_voz"), JLabel.CENTER);
            title.setForeground(new Color(255, 102, 0));
            // microphone_black.png is 256x256: used at native size, no scaling
            title.setIcon(new ImageIcon(VoiceMessageManager.class.getResource("/images/microphone_black.png")));
            title.setIconTextGap(15);
            title.setAlignmentX(JLabel.CENTER_ALIGNMENT);

            JLabel send_hint = new JLabel(Translator.translate("audio.suelta_para_enviar"), JLabel.CENTER);
            send_hint.setForeground(Color.BLACK);
            send_hint.setAlignmentX(JLabel.CENTER_ALIGNMENT);

            JLabel cancel_hint = new JLabel(Translator.translate("audio.borrar_para_cancelar"), JLabel.CENTER);
            cancel_hint.setForeground(Color.BLACK);
            cancel_hint.setAlignmentX(JLabel.CENTER_ALIGNMENT);

            JPanel center = new JPanel();
            center.setOpaque(false);
            center.setLayout(new javax.swing.BoxLayout(center, javax.swing.BoxLayout.Y_AXIS));
            center.add(title);
            center.add(javax.swing.Box.createVerticalStrut(15));
            center.add(send_hint);
            center.add(javax.swing.Box.createVerticalStrut(10));
            center.add(cancel_hint);

            JProgressBar bar = new JProgressBar();

            // Mismo estilo que el resto de diálogos in-game: fondo blanco con borde
            // naranja (línea exterior + relleno interior) y título en naranja.
            JPanel panel = new JPanel(new BorderLayout(15, 15));
            panel.setBackground(Color.WHITE);
            panel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(255, 102, 0), 10),
                    BorderFactory.createEmptyBorder(20, 30, 20, 30)));
            panel.add(center, BorderLayout.CENTER);
            panel.add(bar, BorderLayout.SOUTH);

            dialog.setContentPane(panel);

            Helpers.updateFonts(dialog, Helpers.GUI_FONT, null);

            title.setFont(title.getFont().deriveFont(Font.BOLD, 32f));

            send_hint.setFont(send_hint.getFont().deriveFont(Font.BOLD, 22f));

            cancel_hint.setFont(cancel_hint.getFont().deriveFont(Font.PLAIN, 18f));

            dialog.pack();

            dialog.setOpacity(DIALOG_OPACITY);

            dialog.setLocationRelativeTo(game_frame);

            RECORD_BAR = bar;

            RECORD_DIALOG = dialog;

            // The talk-now signal: from here on the note should be as long as
            // the user keeps talking
            RECORD_START_NANOS = System.nanoTime();

            dialog.setVisible(true);

            Helpers.smoothCountdown(bar, VoiceRecorder.MAX_SECONDS);

            // Safety net: keeping the key held past the cap sends automatically
            // (the recorder stops feeding at MAX_SECONDS anyway).
            javax.swing.Timer auto_send = new javax.swing.Timer(VoiceRecorder.MAX_SECONDS * 1000, e -> {
                WAIT_KEY_RELEASE = true;
                stopAndSend(false);
            });

            auto_send.setRepeats(false);

            AUTO_SEND_TIMER = auto_send;

            auto_send.start();
        });
    }

    private static void closeRecordDialog() {

        Helpers.GUIRun(() -> {

            JDialog dialog = RECORD_DIALOG;

            RECORD_DIALOG = null;

            JProgressBar bar = RECORD_BAR;

            RECORD_BAR = null;

            if (bar != null) {
                // Cancels the smooth countdown timer
                Helpers.smoothCountdown(bar, 0);
            }

            if (dialog != null) {
                dialog.setVisible(false);
                dialog.dispose();
            }
        });
    }

    private VoiceMessageManager() {
    }

}
