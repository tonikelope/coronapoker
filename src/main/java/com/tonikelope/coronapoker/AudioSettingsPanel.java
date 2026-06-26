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
import java.awt.FlowLayout;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.util.List;
import javax.sound.sampled.Mixer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.ListSelectionModel;
import javax.swing.border.TitledBorder;

/**
 * Contenido de los ajustes de audio como JPanel reutilizable. Lo usan tanto el
 * diálogo independiente {@link AudioSettingsDialog} (acceso rápido desde el icono
 * del altavoz, también en lobby/sala de espera donde no hay partida) como la
 * pestaña "Audio" del diálogo unificado de ajustes. Cada cambio se aplica y
 * persiste al instante (el volumen maestro es el mismo valor que mueve el atajo
 * global Shift+Arriba/Abajo).
 *
 * El "host" (diálogo) debe: llamar a {@link #applyFontsAndSizing()} tras añadir el
 * panel, gestionar la pila de modales y llamar a {@link #cleanup()} al cerrarse
 * (para no filtrar el dispatcher de captura de tecla ni perder el volumen).
 *
 * @author tonikelope
 */
public class AudioSettingsPanel extends JPanel {

    // El panel vivo (en cualquier host): lo usa refreshVolume() para sincronizar el
    // slider cuando el volumen cambia por el atajo global mientras está abierto.
    private static volatile AudioSettingsPanel INSTANCE = null;

    private final JCheckBox sonidos_checkbox;
    private final JCheckBox sonidos_chorra_checkbox;
    private final JCheckBox musica_checkbox;
    private final JCheckBox tts_checkbox;
    private final JCheckBox voice_messages_checkbox;
    private final boolean global_rules_locked;
    private final JSlider volume_slider;
    private final JLabel volume_value_label;
    private final JList<String> output_list;
    private final JList<String> capture_list;
    private final JCheckBox mic_checkbox;
    private final JCheckBox play_own_checkbox;
    private final JCheckBox block_notes_checkbox;
    private final JCheckBox block_tts_local_checkbox;
    private final JButton voice_key_button;
    private final JComboBox<String> retention_combo;
    private final JButton purge_button;
    private final List<Mixer.Info> output_devices;
    private final List<Mixer.Info> capture_devices;

    // Paneles con TitledBorder + filas cuyo alto hay que fijar: referencias para
    // applyFontsAndSizing() (updateFonts no alcanza los títulos de borde).
    private final JPanel volume_panel;
    private final JPanel sound_music_panel;
    private final JPanel output_panel;
    private final JPanel mic_panel;
    private final JPanel notes_panel;
    private final JPanel tts_panel;
    private final JPanel global_panel;
    private final JPanel voice_key_panel;
    private final JPanel retention_panel;
    private final JPanel purge_panel;

    // Snapshot al ABRIR (diálogo transaccional): los cambios se aplican en vivo como
    // previsualización y revert() restaura estos valores si se cancela; GUARDAR los
    // conserva. (El diálogo independiente del altavoz commitea siempre.)
    private final float snap_master_volume;
    private final boolean snap_sonidos;
    private final boolean snap_sonidos_chorra;
    private final boolean snap_musica;
    private final boolean snap_tts_server;
    private final boolean snap_voice_messages;
    private final String snap_output_device;
    private final String snap_capture_device;
    private final boolean snap_mic_enabled;
    private final boolean snap_block_voice;
    private final boolean snap_play_own;
    private final int snap_retention_days;
    private final boolean snap_block_tts_local;
    private final int snap_voice_key;

    private volatile boolean loading = true;
    private volatile KeyEventDispatcher key_capture_dispatcher = null;

    // Sincroniza el slider cuando el volumen cambia por el atajo global mientras
    // hay un panel de audio abierto (en cualquier host).
    public static void refreshVolume() {

        AudioSettingsPanel panel = INSTANCE;

        if (panel != null) {
            Helpers.GUIRun(() -> {
                int val = Math.round(Audio.MASTER_VOLUME * 100);

                if (panel.volume_slider.getValue() != val) {
                    panel.volume_slider.setValue(val);
                }
            });
        }
    }

    public AudioSettingsPanel() {

        super(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        snap_master_volume = Audio.MASTER_VOLUME;
        snap_sonidos = GameFrame.SONIDOS;
        snap_sonidos_chorra = GameFrame.SONIDOS_CHORRA;
        snap_musica = GameFrame.MUSICA_AMBIENTAL;
        snap_tts_server = GameFrame.TTS_SERVER;
        snap_voice_messages = GameFrame.VOICE_MESSAGES;
        snap_output_device = AudioDeviceManager.getOutputDevice();
        snap_capture_device = AudioDeviceManager.getCaptureDevice();
        snap_mic_enabled = AudioDeviceManager.isMicEnabled();
        snap_block_voice = AudioDeviceManager.isBlockVoiceMessages();
        snap_play_own = AudioDeviceManager.isPlayOwnVoiceMessages();
        snap_retention_days = AudioDeviceManager.getVoiceNoteRetentionDays();
        snap_block_tts_local = AudioDeviceManager.isBlockTtsLocal();
        snap_voice_key = VoiceMessageManager.getVoiceKey();

        output_devices = AudioDeviceManager.getOutputDevices();

        capture_devices = AudioDeviceManager.getCaptureDevices();

        // --- Master volume (same value the Shift+Up/Down shortcut drives) ---
        volume_slider = new JSlider(0, 100, Math.round(Audio.MASTER_VOLUME * 100));

        volume_value_label = new JLabel(volume_slider.getValue() + "%");

        volume_slider.addChangeListener(e -> {
            int val = volume_slider.getValue();

            volume_value_label.setText(val + "%");

            if (!loading && Math.round(Audio.MASTER_VOLUME * 100) != val) {

                Audio.MASTER_VOLUME = Helpers.floatClean(val / 100f, 2);

                // Immediate effect; the debounced timer only adds the beep
                Audio.refreshALLVolumes(false);

                if (Audio.VOLUME_TIMER.isRunning()) {
                    Audio.VOLUME_TIMER.restart();
                } else {
                    Audio.VOLUME_TIMER.start();
                }
            }

            // Persist once the drag ends (not on every tick)
            if (!loading && !volume_slider.getValueIsAdjusting()) {
                Helpers.PROPERTIES.setProperty("master_volume", String.valueOf(Audio.MASTER_VOLUME));
                Helpers.savePropertiesFile();
            }
        });

        volume_panel = new JPanel(new BorderLayout(10, 0));
        volume_panel.setBorder(BorderFactory.createTitledBorder(Translator.translate("audio.volumen_general")));
        volume_panel.add(new JLabel(menuIcon("/images/menu/sound.png")), BorderLayout.WEST);
        volume_panel.add(volume_slider, BorderLayout.CENTER);
        volume_panel.add(volume_value_label, BorderLayout.EAST);

        // --- Sonido y música (lo que antes vivía en el menú y el popup) ---
        // TTS (global) y Notas de voz (global) son reglas de la timba: se pueden
        // preseleccionar antes de jugar, pero si eres CLIENTE en partida el
        // servidor manda y quedan en gris (su valor sobreescribe y se queda así).
        global_rules_locked = GameFrame.getInstance() != null && !GameFrame.getInstance().isPartida_local();

        sonidos_checkbox = new JCheckBox(Translator.translate("audio.sonidos"), GameFrame.SONIDOS);
        sonidos_checkbox.addActionListener(e -> {
            GameFrame.setSonidos(sonidos_checkbox.isSelected());
            refreshSoundControlsEnabled();
        });

        sonidos_chorra_checkbox = new JCheckBox(Translator.translate("menu.sonidos_de_cona"), GameFrame.SONIDOS_CHORRA);
        sonidos_chorra_checkbox.addActionListener(e -> GameFrame.setSonidosChorra(sonidos_chorra_checkbox.isSelected()));

        musica_checkbox = new JCheckBox(Translator.translate("audio.musica_ambiente"), GameFrame.MUSICA_AMBIENTAL);
        musica_checkbox.addActionListener(e -> GameFrame.setMusicaAmbiental(musica_checkbox.isSelected()));

        tts_checkbox = new JCheckBox(Translator.translate("menu.tts"), GameFrame.TTS_SERVER);
        tts_checkbox.addActionListener(e -> GameFrame.setTTSGlobal(tts_checkbox.isSelected()));

        voice_messages_checkbox = new JCheckBox(Translator.translate("menu.notas_de_voz"), GameFrame.VOICE_MESSAGES);
        voice_messages_checkbox.addActionListener(e -> GameFrame.setVoiceMessages(voice_messages_checkbox.isSelected()));

        sound_music_panel = new JPanel();
        sound_music_panel.setLayout(new BoxLayout(sound_music_panel, BoxLayout.Y_AXIS));
        sound_music_panel.setBorder(BorderFactory.createTitledBorder(Translator.translate("audio.sonido_musica")));
        sound_music_panel.add(iconRow(menuIcon("/images/menu/sound.png"), sonidos_checkbox));
        sound_music_panel.add(iconRow(menuIcon("/images/menu/joke.png"), sonidos_chorra_checkbox));
        sound_music_panel.add(iconRow(menuIcon("/images/menu/music.png"), musica_checkbox));

        // --- Output device ---
        DefaultListModel<String> output_model = new DefaultListModel<>();

        output_model.addElement(Translator.translate("audio.dispositivo_default"));

        for (Mixer.Info info : output_devices) {
            output_model.addElement(info.getName());
        }

        output_list = new JList<>(output_model);
        output_list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        output_list.setVisibleRowCount(4);
        output_list.setSelectedIndex(findDeviceIndex(output_devices, AudioDeviceManager.getOutputDevice()));

        output_list.addListSelectionListener(e -> {
            if (!loading && !e.getValueIsAdjusting()) {

                int index = output_list.getSelectedIndex();

                if (index >= 0) {

                    String device = index == 0 ? AudioDeviceManager.DEFAULT_DEVICE : output_devices.get(index - 1).getName();

                    if (!device.equals(AudioDeviceManager.getOutputDevice())) {

                        AudioDeviceManager.setOutputDevice(device);

                        Helpers.threadRun(() -> {
                            // Off the EDT: stopping a loop drains its line
                            Audio.restartCurrentLoopMp3Resources();

                            // Audible feedback on the freshly selected device
                            Audio.playWavResource("misc/volume_change.wav");
                        });
                    }
                }
            }
        });

        output_panel = new JPanel(new BorderLayout());
        output_panel.setBorder(BorderFactory.createTitledBorder(Translator.translate("audio.dispositivo_salida")));
        output_panel.add(new JScrollPane(output_list), BorderLayout.CENTER);

        // --- Input device: microphone ---
        mic_checkbox = new JCheckBox(Translator.translate("audio.microfono_activado"), AudioDeviceManager.isMicEnabled());

        mic_checkbox.addActionListener(e -> {
            AudioDeviceManager.setMicEnabled(mic_checkbox.isSelected());

            refreshVoiceControlsEnabled();
        });

        DefaultListModel<String> capture_model = new DefaultListModel<>();

        capture_model.addElement(Translator.translate("audio.dispositivo_default"));

        for (Mixer.Info info : capture_devices) {
            capture_model.addElement(info.getName());
        }

        capture_list = new JList<>(capture_model);
        capture_list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        capture_list.setVisibleRowCount(4);
        capture_list.setSelectedIndex(findDeviceIndex(capture_devices, AudioDeviceManager.getCaptureDevice()));

        capture_list.addListSelectionListener(e -> {
            if (!loading && !e.getValueIsAdjusting()) {

                int index = capture_list.getSelectedIndex();

                if (index >= 0) {
                    AudioDeviceManager.setCaptureDevice(index == 0 ? AudioDeviceManager.DEFAULT_DEVICE : capture_devices.get(index - 1).getName());
                }
            }
        });

        voice_key_button = new JButton(KeyEvent.getKeyText(VoiceMessageManager.getVoiceKey()));

        voice_key_button.addActionListener(e -> startVoiceKeyCapture());

        voice_key_panel = new JPanel(new BorderLayout(10, 0));
        voice_key_panel.add(new JLabel(Translator.translate("audio.tecla_nota_voz")), BorderLayout.CENTER);
        voice_key_panel.add(voice_key_button, BorderLayout.EAST);

        mic_panel = new JPanel(new BorderLayout(0, 5));
        mic_panel.setBorder(BorderFactory.createTitledBorder(Translator.translate("audio.dispositivo_entrada")));
        mic_panel.add(iconRow(scaledIcon("/images/microphone_black.png", 24), mic_checkbox), BorderLayout.NORTH);
        mic_panel.add(new JScrollPane(capture_list), BorderLayout.CENTER);

        // --- Voice note options ---
        block_notes_checkbox = new JCheckBox(Translator.translate("audio.bloquear_notas"), AudioDeviceManager.isBlockVoiceMessages());

        block_notes_checkbox.addActionListener(e -> {
            AudioDeviceManager.setBlockVoiceMessages(block_notes_checkbox.isSelected());

            refreshVoiceControlsEnabled();
        });

        play_own_checkbox = new JCheckBox(Translator.translate("audio.reproducir_mis_notas"), AudioDeviceManager.isPlayOwnVoiceMessages());

        play_own_checkbox.addActionListener(e -> AudioDeviceManager.setPlayOwnVoiceMessages(play_own_checkbox.isSelected()));

        // --- Retention: days a stored voice note survives before the startup
        // purge drops it (0 = forever). Parallel to VOICE_NOTE_RETENTION_OPTIONS.
        retention_combo = new JComboBox<>();

        int retention_index = 0;

        for (int i = 0; i < AudioDeviceManager.VOICE_NOTE_RETENTION_OPTIONS.length; i++) {

            int days = AudioDeviceManager.VOICE_NOTE_RETENTION_OPTIONS[i];

            retention_combo.addItem(days == AudioDeviceManager.VOICE_NOTE_RETENTION_KEEP_FOREVER
                    ? Translator.translate("audio.retencion_siempre")
                    : Translator.translate("audio.retencion_dias", days));

            if (days == AudioDeviceManager.getVoiceNoteRetentionDays()) {
                retention_index = i;
            }
        }

        retention_combo.setSelectedIndex(retention_index);

        retention_combo.addActionListener(e -> {
            int index = retention_combo.getSelectedIndex();

            if (index >= 0) {
                AudioDeviceManager.setVoiceNoteRetentionDays(AudioDeviceManager.VOICE_NOTE_RETENTION_OPTIONS[index]);
            }
        });

        retention_panel = new JPanel(new BorderLayout(10, 0));
        retention_panel.add(new JLabel(Translator.translate("audio.conservar_notas")), BorderLayout.CENTER);
        retention_panel.add(retention_combo, BorderLayout.EAST);

        // Manual wipe: drops every stored note now, independent of retention and
        // of the self-block toggle (you may want to clear disk even while blocking).
        purge_button = new JButton(Translator.translate("audio.purgar_notas"));

        purge_button.addActionListener(e -> {
            if (Helpers.mostrarMensajeInformativoSINO(this, Translator.translate("audio.purgar_notas_confirm")) == javax.swing.JOptionPane.YES_OPTION) {
                Helpers.threadRun(() -> {
                    int deleted = Helpers.purgeAllVoiceNotes();
                    Helpers.GUIRun(() -> Helpers.mostrarMensajeInformativo(this, Translator.translate("audio.purgar_notas_resultado", deleted)));
                });
            }
        });

        purge_panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        purge_panel.add(purge_button);

        refreshVoiceControlsEnabled();

        notes_panel = new JPanel();
        notes_panel.setLayout(new BoxLayout(notes_panel, BoxLayout.Y_AXIS));
        notes_panel.setBorder(BorderFactory.createTitledBorder(Translator.translate("audio.notas_de_voz")));

        voice_key_panel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        block_notes_checkbox.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        play_own_checkbox.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        retention_panel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        purge_panel.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        notes_panel.add(voice_key_panel);
        notes_panel.add(Box.createVerticalStrut(5));
        notes_panel.add(block_notes_checkbox);
        notes_panel.add(play_own_checkbox);
        notes_panel.add(Box.createVerticalStrut(5));
        notes_panel.add(retention_panel);
        notes_panel.add(purge_panel);

        // --- Voz (TTS): bloqueo local (la regla global vive más abajo) ---
        block_tts_local_checkbox = new JCheckBox(Translator.translate("audio.bloquear_tts_local"), AudioDeviceManager.isBlockTtsLocal());
        block_tts_local_checkbox.addActionListener(e -> AudioDeviceManager.setBlockTtsLocal(block_tts_local_checkbox.isSelected()));

        tts_panel = new JPanel();
        tts_panel.setLayout(new BoxLayout(tts_panel, BoxLayout.Y_AXIS));
        tts_panel.setBorder(BorderFactory.createTitledBorder(Translator.translate("audio.voz_tts")));
        tts_panel.add(iconRow(menuIcon("/images/menu/mute.png"), block_tts_local_checkbox));

        // --- Ajustes globales (solo server): reglas de la timba (TTS y notas de
        // voz). Preseleccionables; si eres cliente en partida mandan las del
        // servidor y quedan en gris (la nota lo avisa). ---
        // Ancho fijo en el HTML para que el texto ajuste a varias líneas dentro
        // de la columna y no se corte.
        JLabel global_note = new JLabel("<html><div style='width:240px'>" + Translator.translate("audio.ajustes_locales_ignorados") + "</div></html>");
        global_note.setForeground(java.awt.Color.GRAY);
        global_note.setVisible(global_rules_locked);

        global_note.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        global_panel = new JPanel();
        global_panel.setLayout(new BoxLayout(global_panel, BoxLayout.Y_AXIS));
        global_panel.setBorder(BorderFactory.createTitledBorder(Translator.translate("audio.ajustes_globales_server")));
        global_panel.add(iconRow(menuIcon("/images/menu/voice.png"), tts_checkbox));
        global_panel.add(iconRow(scaledIcon("/images/microphone_black.png", 24), voice_messages_checkbox));
        global_panel.add(global_note);

        refreshSoundControlsEnabled();

        // --- Dos columnas para que el diálogo sea apaisado y entre en
        // resoluciones bajas: izquierda sonido + dispositivos, derecha voz ---
        sound_music_panel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        output_panel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        mic_panel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        notes_panel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        tts_panel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        global_panel.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        JPanel left_col = new JPanel();
        left_col.setLayout(new BoxLayout(left_col, BoxLayout.Y_AXIS));
        left_col.setAlignmentY(JComponent.TOP_ALIGNMENT);
        left_col.add(sound_music_panel);
        left_col.add(Box.createVerticalStrut(8));
        left_col.add(output_panel);
        left_col.add(Box.createVerticalStrut(8));
        left_col.add(mic_panel);

        JPanel right_col = new JPanel();
        right_col.setLayout(new BoxLayout(right_col, BoxLayout.Y_AXIS));
        right_col.setAlignmentY(JComponent.TOP_ALIGNMENT);
        right_col.add(notes_panel);
        right_col.add(Box.createVerticalStrut(8));
        right_col.add(tts_panel);
        right_col.add(Box.createVerticalStrut(8));
        right_col.add(global_panel);

        JPanel center_panel = new JPanel();
        center_panel.setLayout(new BoxLayout(center_panel, BoxLayout.X_AXIS));
        center_panel.add(left_col);
        center_panel.add(Box.createHorizontalStrut(12));
        center_panel.add(right_col);

        add(volume_panel, BorderLayout.NORTH);
        add(center_panel, BorderLayout.CENTER);

        loading = false;

        INSTANCE = this;
    }

    // Aplica la fuente escalada del audio (1.2x, como el antiguo diálogo) y los
    // arreglos que dependen de ella: fuentes de los títulos de borde (updateFonts
    // no los alcanza) y altos máximos de las filas/paneles que no deben estirarse.
    // El host debe llamarlo DESPUÉS de su propio updateFonts general.
    public void applyFontsAndSizing() {

        // Nota: la fuente la aplica el HOST antes de llamar aquí (el diálogo
        // independiente del altavoz usa updateFonts 1.2x; el diálogo unificado usa su
        // tamaño homogéneo). Aquí solo se arreglan los títulos de borde y los altos.
        ((TitledBorder) volume_panel.getBorder()).setTitleFont(volume_value_label.getFont());
        ((TitledBorder) sound_music_panel.getBorder()).setTitleFont(volume_value_label.getFont());
        ((TitledBorder) output_panel.getBorder()).setTitleFont(volume_value_label.getFont());
        ((TitledBorder) mic_panel.getBorder()).setTitleFont(volume_value_label.getFont());
        ((TitledBorder) notes_panel.getBorder()).setTitleFont(volume_value_label.getFont());
        ((TitledBorder) tts_panel.getBorder()).setTitleFont(volume_value_label.getFont());
        ((TitledBorder) global_panel.getBorder()).setTitleFont(volume_value_label.getFont());

        // En el BoxLayout vertical, la fila de tecla y la de retención deben
        // conservar su alto natural en vez de estirarse hasta llenar el hueco.
        voice_key_panel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, voice_key_panel.getPreferredSize().height));
        retention_panel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, retention_panel.getPreferredSize().height));
        purge_panel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, purge_panel.getPreferredSize().height));

        // Estos paneles solo llevan checkboxes y son más estrechos que el título de
        // su borde. Se estiran al ancho de su columna (sin recortar alto) para que el
        // título quepa entero. DESPUÉS de updateFonts: el alto preferido ya refleja la
        // fuente escalada y el contenido no se corta por abajo.
        sound_music_panel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, sound_music_panel.getPreferredSize().height));
        tts_panel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, tts_panel.getPreferredSize().height));
        global_panel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, global_panel.getPreferredSize().height));
    }

    // El host DEBE llamarlo al cerrarse: no filtrar el dispatcher de captura de
    // tecla, persistir el volumen maestro y soltar la referencia viva.
    public void cleanup() {

        stopVoiceKeyCapture();

        Helpers.PROPERTIES.setProperty("master_volume", String.valueOf(Audio.MASTER_VOLUME));
        Helpers.savePropertiesFile();

        if (INSTANCE == this) {
            INSTANCE = null;
        }
    }

    // ¿Hay cambios de audio respecto al estado de apertura? Lo usa el host para
    // preguntar antes de descartar al cancelar.
    public boolean isDirty() {
        return Audio.MASTER_VOLUME != snap_master_volume
                || GameFrame.SONIDOS != snap_sonidos
                || GameFrame.SONIDOS_CHORRA != snap_sonidos_chorra
                || GameFrame.MUSICA_AMBIENTAL != snap_musica
                // Reglas globales (TTS/notas): si eres CLIENTE las manda el servidor (no
                // las posees); ignorarlas para no dar "¿descartar?" espurio ni revertir
                // sobre un broadcast del host.
                || (!global_rules_locked && GameFrame.TTS_SERVER != snap_tts_server)
                || (!global_rules_locked && GameFrame.VOICE_MESSAGES != snap_voice_messages)
                || !java.util.Objects.equals(snap_output_device, AudioDeviceManager.getOutputDevice())
                || !java.util.Objects.equals(snap_capture_device, AudioDeviceManager.getCaptureDevice())
                || AudioDeviceManager.isMicEnabled() != snap_mic_enabled
                || AudioDeviceManager.isBlockVoiceMessages() != snap_block_voice
                || AudioDeviceManager.isPlayOwnVoiceMessages() != snap_play_own
                || AudioDeviceManager.getVoiceNoteRetentionDays() != snap_retention_days
                || AudioDeviceManager.isBlockTtsLocal() != snap_block_tts_local
                || VoiceMessageManager.getVoiceKey() != snap_voice_key;
    }

    // Revierte (al CANCELAR el diálogo transaccional) los ajustes de audio al estado
    // capturado al abrir, re-aplicando cada uno por su setter normal (los cambios
    // globales re-emiten su broadcast, restaurando también a los clientes).
    public void revert() {

        if (Audio.MASTER_VOLUME != snap_master_volume) {
            Audio.MASTER_VOLUME = snap_master_volume;
            Audio.refreshALLVolumes(false);
            Helpers.PROPERTIES.setProperty("master_volume", String.valueOf(Audio.MASTER_VOLUME));
            Helpers.savePropertiesFile();
        }
        if (GameFrame.SONIDOS != snap_sonidos) {
            GameFrame.setSonidos(snap_sonidos);
        }
        if (GameFrame.SONIDOS_CHORRA != snap_sonidos_chorra) {
            GameFrame.setSonidosChorra(snap_sonidos_chorra);
        }
        if (GameFrame.MUSICA_AMBIENTAL != snap_musica) {
            GameFrame.setMusicaAmbiental(snap_musica);
        }
        // Reglas globales (TTS/notas): solo las revierte el HOST (que las posee). Para un
        // cliente las gobierna el servidor por broadcast; revertirlas lo desincronizaría.
        if (!global_rules_locked && GameFrame.TTS_SERVER != snap_tts_server) {
            GameFrame.setTTSGlobal(snap_tts_server);
        }
        if (!global_rules_locked && GameFrame.VOICE_MESSAGES != snap_voice_messages) {
            GameFrame.setVoiceMessages(snap_voice_messages);
        }
        if (!java.util.Objects.equals(snap_output_device, AudioDeviceManager.getOutputDevice())) {
            AudioDeviceManager.setOutputDevice(snap_output_device);
            Helpers.threadRun(Audio::restartCurrentLoopMp3Resources);
        }
        if (!java.util.Objects.equals(snap_capture_device, AudioDeviceManager.getCaptureDevice())) {
            AudioDeviceManager.setCaptureDevice(snap_capture_device);
        }
        if (AudioDeviceManager.isMicEnabled() != snap_mic_enabled) {
            AudioDeviceManager.setMicEnabled(snap_mic_enabled);
        }
        if (AudioDeviceManager.isBlockVoiceMessages() != snap_block_voice) {
            AudioDeviceManager.setBlockVoiceMessages(snap_block_voice);
        }
        if (AudioDeviceManager.isPlayOwnVoiceMessages() != snap_play_own) {
            AudioDeviceManager.setPlayOwnVoiceMessages(snap_play_own);
        }
        if (AudioDeviceManager.getVoiceNoteRetentionDays() != snap_retention_days) {
            AudioDeviceManager.setVoiceNoteRetentionDays(snap_retention_days);
        }
        if (AudioDeviceManager.isBlockTtsLocal() != snap_block_tts_local) {
            AudioDeviceManager.setBlockTtsLocal(snap_block_tts_local);
        }
        if (VoiceMessageManager.getVoiceKey() != snap_voice_key) {
            VoiceMessageManager.setVoiceKey(snap_voice_key);
        }
    }

    // El sonido maestro gobierna coña y música. Las reglas globales (TTS y notas de
    // voz) se pueden preseleccionar siempre, salvo cuando eres cliente en partida:
    // ahí mandan las del servidor y quedan en gris.
    private void refreshSoundControlsEnabled() {

        boolean on = sonidos_checkbox.isSelected();

        sonidos_chorra_checkbox.setEnabled(on);
        musica_checkbox.setEnabled(on);

        tts_checkbox.setEnabled(!global_rules_locked);
        voice_messages_checkbox.setEnabled(!global_rules_locked);
    }

    // Self-block governs every voice note control; the capture list also needs the
    // mic itself enabled
    private void refreshVoiceControlsEnabled() {

        boolean blocked = block_notes_checkbox.isSelected();

        mic_checkbox.setEnabled(!blocked);
        capture_list.setEnabled(!blocked && mic_checkbox.isSelected());
        play_own_checkbox.setEnabled(!blocked);
        voice_key_button.setEnabled(!blocked);

        if (blocked) {
            stopVoiceKeyCapture();
        }
    }

    private void startVoiceKeyCapture() {

        if (key_capture_dispatcher != null) {
            return;
        }

        // While capturing, the global hook must not react to the current key
        VoiceMessageManager.setCapturingKey(true);

        voice_key_button.setText(Translator.translate("audio.pulsa_una_tecla"));

        key_capture_dispatcher = (KeyEvent e) -> {

            if (e.getID() == KeyEvent.KEY_PRESSED) {

                // ESC cancels, anything else becomes the new key
                if (e.getKeyCode() != KeyEvent.VK_ESCAPE) {
                    VoiceMessageManager.setVoiceKey(e.getKeyCode());
                }

                stopVoiceKeyCapture();

                return true;
            }

            return false;
        };

        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(key_capture_dispatcher);
    }

    private void stopVoiceKeyCapture() {

        if (key_capture_dispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(key_capture_dispatcher);
            key_capture_dispatcher = null;
        }

        voice_key_button.setText(KeyEvent.getKeyText(VoiceMessageManager.getVoiceKey()));

        VoiceMessageManager.setCapturingKey(false);
    }

    // List index 0 is the system default entry; devices start at index 1.
    private static int findDeviceIndex(List<Mixer.Info> devices, String device) {

        if (device != null && !device.isEmpty()) {

            for (int i = 0; i < devices.size(); i++) {

                if (device.equals(devices.get(i).getName())) {
                    return i + 1;
                }
            }
        }

        return 0;
    }

    // Prepends an icon to a control (a checkbox) WITHOUT calling setIcon() on the
    // checkbox itself (that would replace its check indicator). Returns a left-aligned
    // horizontal [icon + control] row; the control keeps its identity, so its listeners
    // and enabled state still operate on the same object.
    private static JComponent iconRow(javax.swing.Icon icon, JComponent control) {

        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        JLabel icon_label = new JLabel(icon);
        icon_label.setAlignmentY(JComponent.CENTER_ALIGNMENT);
        control.setAlignmentY(JComponent.CENTER_ALIGNMENT);

        row.add(icon_label);
        row.add(Box.createHorizontalStrut(6));
        row.add(control);
        row.add(Box.createHorizontalGlue());

        return row;
    }

    // Loads a menu icon (already at the right 24px size) straight from resources.
    private static javax.swing.ImageIcon menuIcon(String resource) {
        return new javax.swing.ImageIcon(AudioSettingsPanel.class.getResource(resource));
    }

    // Loads and smooth-scales a larger resource to a square icon (e.g. the 256px
    // microphone down to menu-icon size). Null on a malformed URL (never happens for
    // bundled resources), which a JLabel renders as no icon.
    private static javax.swing.ImageIcon scaledIcon(String resource, int size) {
        try {
            return Helpers.scaleIcon(AudioSettingsPanel.class.getResource(resource), size, size);
        } catch (java.net.MalformedURLException ex) {
            return null;
        }
    }

}
