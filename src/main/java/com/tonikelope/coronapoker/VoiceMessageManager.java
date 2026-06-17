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
    public static final float DIALOG_OPACITY = 0.9f;

    private static volatile int VOICE_KEY;
    private static volatile boolean CAPTURING_KEY = false;
    private static volatile VoiceRecorder RECORDER = null;
    private static volatile JDialog RECORD_DIALOG = null;
    private static volatile JProgressBar RECORD_BAR = null;
    private static volatile javax.swing.Timer AUTO_SEND_TIMER = null;
    private static volatile boolean WAIT_KEY_RELEASE = false;
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

        // Total local silence while recording: the mic must not pick up
        // music, effects or other voices.
        Audio.setVoiceRecording(true);

        // Shared teardown for a recording that produced nothing usable: the
        // line never opened, or it opened but stayed silent (catatonic device).
        // The RECORDER == recorder guard makes it safe against a fresh note
        // already started on the held key.
        Runnable dead_mic_cleanup = () -> {
            if (RECORDER == recorder) {
                RECORDER = null;
                WAIT_KEY_RELEASE = true;
                Audio.setVoiceRecording(false);
                warning("audio.microfono_no_configurado");
            }
        };

        Helpers.threadRun(() -> {

            // Opening the mic takes 100-400ms and the past cannot be captured:
            // the dialog only shows when the FIRST audio arrives from the
            // device (line.start() returns before the driver really delivers),
            // so it is an honest talk-now signal. The EDT never blocks on the
            // driver. on_no_data fires the same teardown if the line opened but
            // the device never delivered a sample.
            if (recorder.start(() -> showRecordDialogAndArmTimer(recorder), dead_mic_cleanup)) {

                // Dialog and timer are armed by the on_live callback

            } else {

                dead_mic_cleanup.run();
            }
        });
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

        javax.swing.Timer auto_send = AUTO_SEND_TIMER;

        AUTO_SEND_TIMER = null;

        if (auto_send != null) {
            auto_send.stop();
        }

        closeRecordDialog();

        Helpers.threadRun(() -> {

            byte[] wav = recorder.stop();

            // The tail grace is over: lift the local recording silence
            Audio.setVoiceRecording(false);

            WaitingRoomFrame sala = WaitingRoomFrame.getInstance();

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
        });
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

            Helpers.threadRun(() -> {
                try {
                    java.awt.Container parent = GameFrame.getInstance() != null ? GameFrame.getInstance().getContentPane()
                            : (WaitingRoomFrame.getInstance() != null ? WaitingRoomFrame.getInstance().getContentPane() : Init.VENTANA_INICIO);

                    Helpers.mostrarMensajeError(parent, Translator.translate(i18n_key));
                } finally {
                    WARNING_SHOWING.set(false);
                }
            });
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
