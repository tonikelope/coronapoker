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

/**
 * Reusable audio settings panel: content of the "Audio" tab in the unified settings
 * dialog ({@link SettingsDialog}), reachable from the gear icon in the launcher, the
 * waiting room and the game itself. Every change is applied and persisted immediately
 * (the master volume is the same value driven by the global Shift+Up/Down shortcut).
 *
 * <p>The host dialog must call {@link #applyFontsAndSizing()} after adding the panel,
 * manage the modal stack, and call {@link #cleanup()} on close (otherwise the key
 * capture dispatcher leaks and the volume change is lost).
 *
 * @author tonikelope
 */
public class AudioSettingsPanel extends JPanel {

    // The live panel instance (whichever host owns it): refreshVolume() uses it to sync
    // the slider when the global shortcut changes the volume while the panel is open.
    private static volatile AudioSettingsPanel INSTANCE = null;

    private final JCheckBox sonidos_checkbox;
    private final JCheckBox sonidos_chorra_checkbox;
    // "Music" group: one master (MUSICA) that turns off all tracks + the four individual
    // toggles, same pattern as "Sound effects".
    private final JCheckBox musica_master_checkbox;
    private final JCheckBox musica_checkbox;
    private final JCheckBox musica_sala_checkbox;
    private final JCheckBox musica_about_checkbox;
    private final JCheckBox musica_stats_checkbox;
    // "Sound effects" group: one master (sonido_efectos) that turns them all off + the
    // individual effects. "my cards" depends on "reveal".
    private final JCheckBox sonido_efectos_checkbox;
    private final JCheckBox sonido_barajado_checkbox;
    private final JCheckBox sonido_reparto_checkbox;
    private final JCheckBox sonido_destape_checkbox;
    private final JCheckBox sonido_destape_mis_checkbox;
    private final JCheckBox sonido_apostar_checkbox;
    private final JCheckBox sonido_fold_checkbox;
    private final JCheckBox sonido_conteo_checkbox;
    private final JCheckBox sonido_entra_checkbox;
    private final JCheckBox sonido_sale_checkbox;
    private final JCheckBox sonido_interruptor_checkbox;
    private final JCheckBox sonido_caja_checkbox;
    private final JCheckBox sonido_igualar_checkbox;
    private final JCheckBox sonido_pasar_checkbox;
    private final JCheckBox sonido_allin_checkbox;
    private final JCheckBox sonido_ciegas_checkbox;
    private final JCheckBox sonido_ultima_mano_checkbox;
    private final JCheckBox sonido_pausa_checkbox;
    private final JCheckBox sonido_entrar_sala_checkbox;
    private final JCheckBox sonido_tu_turno_checkbox;
    private final JCheckBox sonido_aviso_tiempo_checkbox;
    private final JCheckBox sonido_fin_partida_checkbox;
    private final JCheckBox sonido_inicio_checkbox;
    private final JCheckBox sonido_conexion_checkbox;
    private final JCheckBox sonido_iwtsth_checkbox;
    private final JCheckBox sonido_zoom_checkbox;
    private final JCheckBox sonido_vista_compacta_checkbox;
    private final JCheckBox sonido_screenshot_checkbox;
    private final JCheckBox sonido_tapete_checkbox;
    private final JCheckBox sonido_visor_checkbox;
    private final JCheckBox sonido_volumen_checkbox;
    private final JCheckBox sonido_arranque_checkbox;
    private final JCheckBox sonido_aviso_checkbox;
    private final JCheckBox sonido_error_checkbox;
    private final JCheckBox sonido_error_red_checkbox;
    private final JCheckBox tts_checkbox;
    private final JCheckBox voice_messages_checkbox;
    // Category headers in the effects section ("Actions", "Cards"...): greyed out along
    // with the effects when disabled (refreshSoundControlsEnabled).
    private final List<JLabel> fx_type_headers = new java.util.ArrayList<>();
    private final boolean global_rules_locked;
    private final JSlider volume_slider;
    private final JLabel volume_value_label;
    private final JList<String> output_list;
    private final JList<String> capture_list;
    private final JCheckBox mic_checkbox;
    private final JCheckBox play_own_checkbox;
    private final JCheckBox notes_local_checkbox;
    private final JCheckBox tts_local_checkbox;
    private final JComboBox<String> retention_combo;
    private final JButton purge_button;
    private final List<Mixer.Info> output_devices;
    private final List<Mixer.Info> capture_devices;

    // Section content panels (each wrapped in a SettingsUI.card at assembly) + rows whose height
    // must be pinned: kept as references for applyFontsAndSizing().
    private final JPanel volume_panel;
    private final JPanel sound_music_panel;
    private final JPanel output_panel;
    private final JPanel mic_panel;
    private final JPanel notes_panel;
    private final JPanel tts_panel;
    private final JPanel retention_panel;
    private final JPanel purge_panel;

    // Snapshot taken on OPEN (transactional dialog): changes apply live as a preview and
    // revert() restores these values on cancel; SAVE keeps them. (The standalone speaker
    // dialog always commits.)
    private final float snap_master_volume;
    private final boolean snap_sonidos;
    private final boolean snap_sonidos_chorra;
    private final boolean snap_musica_master;
    private final boolean snap_musica;
    private final boolean snap_musica_sala;
    private final boolean snap_musica_about;
    private final boolean snap_musica_stats;
    private final boolean snap_sonido_efectos;
    private final boolean snap_sonido_barajado;
    private final boolean snap_sonido_reparto;
    private final boolean snap_sonido_destape;
    private final boolean snap_sonido_destape_mis;
    private final boolean snap_sonido_apostar;
    private final boolean snap_sonido_fold;
    private final boolean snap_sonido_conteo;
    private final boolean snap_sonido_entra;
    private final boolean snap_sonido_sale;
    private final boolean snap_sonido_interruptor;
    private final boolean snap_sonido_caja;
    private final boolean snap_sonido_igualar;
    private final boolean snap_sonido_pasar;
    private final boolean snap_sonido_allin;
    private final boolean snap_sonido_ciegas;
    private final boolean snap_sonido_ultima_mano;
    private final boolean snap_sonido_pausa;
    private final boolean snap_sonido_entrar_sala;
    private final boolean snap_sonido_tu_turno;
    private final boolean snap_sonido_aviso_tiempo;
    private final boolean snap_sonido_fin_partida;
    private final boolean snap_sonido_inicio;
    private final boolean snap_sonido_conexion;
    private final boolean snap_sonido_iwtsth;
    private final boolean snap_sonido_zoom;
    private final boolean snap_sonido_vista_compacta;
    private final boolean snap_sonido_screenshot;
    private final boolean snap_sonido_tapete;
    private final boolean snap_sonido_visor;
    private final boolean snap_sonido_volumen;
    private final boolean snap_sonido_arranque;
    private final boolean snap_sonido_aviso;
    private final boolean snap_sonido_error;
    private final boolean snap_sonido_error_red;
    private final boolean snap_tts_server;
    private final boolean snap_voice_messages;
    private final String snap_output_device;
    private final String snap_capture_device;
    private final boolean snap_mic_enabled;
    private final boolean snap_block_voice;
    private final boolean snap_play_own;
    private final int snap_retention_days;
    private final boolean snap_block_tts_local;

    private volatile boolean loading = true;

    /**
     * Syncs the volume slider when the global shortcut changes the volume while an audio
     * panel is open, whichever host owns it.
     */
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

    /**
     * Builds the panel: snapshots current audio settings, then builds and wires every
     * control, applying each change live as it happens.
     */
    public AudioSettingsPanel() {

        super(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(Math.round(10 * Helpers.DIALOG_ZOOM), Math.round(10 * Helpers.DIALOG_ZOOM), Math.round(10 * Helpers.DIALOG_ZOOM), Math.round(10 * Helpers.DIALOG_ZOOM)));

        snap_master_volume = Audio.MASTER_VOLUME;
        snap_sonidos = GameFrame.SONIDOS;
        snap_sonidos_chorra = GameFrame.SONIDOS_CHORRA;
        snap_musica_master = GameFrame.MUSICA;
        snap_musica = GameFrame.MUSICA_AMBIENTAL;
        snap_musica_sala = GameFrame.MUSICA_SALA;
        snap_musica_about = GameFrame.MUSICA_ABOUT;
        snap_musica_stats = GameFrame.MUSICA_STATS;
        snap_sonido_efectos = GameFrame.SONIDO_EFECTOS;
        snap_sonido_barajado = GameFrame.SONIDO_BARAJADO;
        snap_sonido_reparto = GameFrame.SONIDO_REPARTO;
        snap_sonido_destape = GameFrame.SONIDO_DESTAPE;
        snap_sonido_destape_mis = GameFrame.SONIDO_DESTAPE_MIS_CARTAS;
        snap_sonido_apostar = GameFrame.SONIDO_APOSTAR;
        snap_sonido_fold = GameFrame.SONIDO_FOLD;
        snap_sonido_conteo = GameFrame.SONIDO_CONTEO;
        snap_sonido_entra = GameFrame.SONIDO_ENTRA;
        snap_sonido_sale = GameFrame.SONIDO_SALE;
        snap_sonido_interruptor = GameFrame.SONIDO_INTERRUPTOR;
        snap_sonido_caja = GameFrame.SONIDO_CAJA;
        snap_sonido_igualar = GameFrame.SONIDO_IGUALAR;
        snap_sonido_pasar = GameFrame.SONIDO_PASAR;
        snap_sonido_allin = GameFrame.SONIDO_ALLIN;
        snap_sonido_ciegas = GameFrame.SONIDO_CIEGAS;
        snap_sonido_ultima_mano = GameFrame.SONIDO_ULTIMA_MANO;
        snap_sonido_pausa = GameFrame.SONIDO_PAUSA;
        snap_sonido_entrar_sala = GameFrame.SONIDO_ENTRAR_SALA;
        snap_sonido_tu_turno = GameFrame.SONIDO_TU_TURNO;
        snap_sonido_aviso_tiempo = GameFrame.SONIDO_AVISO_TIEMPO;
        snap_sonido_fin_partida = GameFrame.SONIDO_FIN_PARTIDA;
        snap_sonido_inicio = GameFrame.SONIDO_INICIO;
        snap_sonido_conexion = GameFrame.SONIDO_CONEXION;
        snap_sonido_iwtsth = GameFrame.SONIDO_IWTSTH;
        snap_sonido_zoom = GameFrame.SONIDO_ZOOM;
        snap_sonido_vista_compacta = GameFrame.SONIDO_VISTA_COMPACTA;
        snap_sonido_screenshot = GameFrame.SONIDO_SCREENSHOT;
        snap_sonido_tapete = GameFrame.SONIDO_TAPETE;
        snap_sonido_visor = GameFrame.SONIDO_VISOR;
        snap_sonido_volumen = GameFrame.SONIDO_VOLUMEN;
        snap_sonido_arranque = GameFrame.SONIDO_ARRANQUE;
        snap_sonido_aviso = GameFrame.SONIDO_AVISO;
        snap_sonido_error = GameFrame.SONIDO_ERROR;
        snap_sonido_error_red = GameFrame.SONIDO_ERROR_RED;
        snap_tts_server = GameFrame.TTS_SERVER;
        snap_voice_messages = GameFrame.VOICE_MESSAGES;
        snap_output_device = AudioDeviceManager.getOutputDevice();
        snap_capture_device = AudioDeviceManager.getCaptureDevice();
        snap_mic_enabled = AudioDeviceManager.isMicEnabled();
        snap_block_voice = AudioDeviceManager.isBlockVoiceMessages();
        snap_play_own = AudioDeviceManager.isPlayOwnVoiceMessages();
        snap_retention_days = AudioDeviceManager.getVoiceNoteRetentionDays();
        snap_block_tts_local = AudioDeviceManager.isBlockTtsLocal();

        // These return a cached snapshot (enumerating audio hardware on the EDT froze the dialog).
        output_devices = AudioDeviceManager.getOutputDevices();

        capture_devices = AudioDeviceManager.getCaptureDevices();

        // Refresh the cache in the background so the NEXT time Settings opens it reflects any device
        // that was plugged/unplugged since — without ever probing hardware on the EDT.
        AudioDeviceManager.refreshDeviceCacheAsync();

        // --- Master volume (same value the Shift+Up/Down shortcut drives) ---
        volume_slider = new JSlider(0, 100, Math.round(Audio.MASTER_VOLUME * 100));
        Helpers.setTranslatedToolTip(volume_slider, "tooltip.cfg.volume");

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
        volume_panel.setOpaque(false);
        volume_panel.add(new JLabel(menuIcon("/images/menu/sound.png")), BorderLayout.WEST);
        volume_panel.add(volume_slider, BorderLayout.CENTER);
        volume_panel.add(volume_value_label, BorderLayout.EAST);

        // --- Sound & music (previously lived in the menu and the popup) ---
        // TTS (global) and Voice notes (global) are table rules: they can be pre-set
        // before playing, but as a CLIENT in a running game the server's value wins and
        // they show greyed out.
        global_rules_locked = GameFrame.getInstance() != null && !GameFrame.getInstance().isPartida_local();

        sonidos_checkbox = togBold("audio.sonidos", GameFrame.SONIDOS);
        Helpers.setTranslatedToolTip(sonidos_checkbox, "tooltip.cfg.sound_master");
        sonidos_checkbox.addActionListener(e -> {
            GameFrame.setSonidos(sonidos_checkbox.isSelected());
            refreshSoundControlsEnabled();
        });

        sonidos_chorra_checkbox = tog("menu.sonidos_de_cona", GameFrame.SONIDOS_CHORRA);
        sonidos_chorra_checkbox.addActionListener(e -> GameFrame.setSonidosChorra(sonidos_chorra_checkbox.isSelected()));

        // Music master: turns off all four tracks at once and refreshes their enabled
        // state (same pattern as the effects master). Depends on "Sound".
        musica_master_checkbox = togBold("audio.musica_maestro", GameFrame.MUSICA);
        Helpers.setTranslatedToolTip(musica_master_checkbox, "tooltip.cfg.music_master");
        musica_master_checkbox.addActionListener(e -> {
            GameFrame.setMusica(musica_master_checkbox.isSelected());
            refreshSoundControlsEnabled();
        });

        musica_checkbox = tog("audio.musica_ambiente", GameFrame.MUSICA_AMBIENTAL);
        musica_checkbox.addActionListener(e -> GameFrame.setMusicaAmbiental(musica_checkbox.isSelected()));

        musica_sala_checkbox = tog("audio.musica_sala", GameFrame.MUSICA_SALA);
        musica_sala_checkbox.addActionListener(e -> GameFrame.setMusicaSala(musica_sala_checkbox.isSelected()));

        musica_about_checkbox = tog("audio.musica_about", GameFrame.MUSICA_ABOUT);
        musica_about_checkbox.addActionListener(e -> GameFrame.setMusicaAbout(musica_about_checkbox.isSelected()));

        musica_stats_checkbox = tog("audio.musica_stats", GameFrame.MUSICA_STATS);
        musica_stats_checkbox.addActionListener(e -> GameFrame.setMusicaStats(musica_stats_checkbox.isSelected()));

        // --- Sound effects (subpanel under "Ambient music") ---
        // Master that turns off ALL effects + individual toggles (all ON by default). The
        // master and "reveal" refresh their dependents' enabled state. The whole group depends
        // on the "Sound" master (disabled along with it, like jokes/music).
        sonido_efectos_checkbox = togBold("audio.efectos_sonido", GameFrame.SONIDO_EFECTOS);
        Helpers.setTranslatedToolTip(sonido_efectos_checkbox, "tooltip.cfg.fx_master");
        sonido_efectos_checkbox.addActionListener(e -> {
            GameFrame.setSonidoEfectos(sonido_efectos_checkbox.isSelected());
            refreshSoundControlsEnabled();
        });

        sonido_barajado_checkbox = tog("audio.sonido_barajar", GameFrame.SONIDO_BARAJADO);
        sonido_barajado_checkbox.addActionListener(e -> GameFrame.setSonidoBarajado(sonido_barajado_checkbox.isSelected()));

        sonido_reparto_checkbox = tog("audio.sonido_repartir", GameFrame.SONIDO_REPARTO);
        sonido_reparto_checkbox.addActionListener(e -> GameFrame.setSonidoReparto(sonido_reparto_checkbox.isSelected()));

        sonido_destape_checkbox = tog("audio.sonido_destapar", GameFrame.SONIDO_DESTAPE);
        sonido_destape_checkbox.addActionListener(e -> {
            GameFrame.setSonidoDestape(sonido_destape_checkbox.isSelected());
            refreshSoundControlsEnabled();
        });

        sonido_destape_mis_checkbox = tog("audio.sonido_destapar_mis_cartas", GameFrame.SONIDO_DESTAPE_MIS_CARTAS);
        sonido_destape_mis_checkbox.addActionListener(e -> GameFrame.setSonidoDestapeMisCartas(sonido_destape_mis_checkbox.isSelected()));

        sonido_apostar_checkbox = tog("audio.sonido_apostar", GameFrame.SONIDO_APOSTAR);
        sonido_apostar_checkbox.addActionListener(e -> GameFrame.setSonidoApostar(sonido_apostar_checkbox.isSelected()));

        sonido_fold_checkbox = tog("audio.sonido_foldear", GameFrame.SONIDO_FOLD);
        sonido_fold_checkbox.addActionListener(e -> GameFrame.setSonidoFold(sonido_fold_checkbox.isSelected()));

        sonido_conteo_checkbox = tog("audio.sonido_conteo", GameFrame.SONIDO_CONTEO);
        sonido_conteo_checkbox.addActionListener(e -> GameFrame.setSonidoConteo(sonido_conteo_checkbox.isSelected()));

        sonido_entra_checkbox = tog("audio.sonido_entra", GameFrame.SONIDO_ENTRA);
        sonido_entra_checkbox.addActionListener(e -> GameFrame.setSonidoEntra(sonido_entra_checkbox.isSelected()));

        sonido_sale_checkbox = tog("audio.sonido_sale", GameFrame.SONIDO_SALE);
        sonido_sale_checkbox.addActionListener(e -> GameFrame.setSonidoSale(sonido_sale_checkbox.isSelected()));

        sonido_interruptor_checkbox = tog("audio.sonido_interruptor", GameFrame.SONIDO_INTERRUPTOR);
        sonido_interruptor_checkbox.addActionListener(e -> GameFrame.setSonidoInterruptor(sonido_interruptor_checkbox.isSelected()));

        sonido_caja_checkbox = tog("audio.sonido_caja", GameFrame.SONIDO_CAJA);
        sonido_caja_checkbox.addActionListener(e -> GameFrame.setSonidoCaja(sonido_caja_checkbox.isSelected()));

        sonido_igualar_checkbox = tog("audio.sonido_igualar", GameFrame.SONIDO_IGUALAR);
        sonido_igualar_checkbox.addActionListener(e -> GameFrame.setSonidoIgualar(sonido_igualar_checkbox.isSelected()));

        sonido_pasar_checkbox = tog("audio.sonido_pasar", GameFrame.SONIDO_PASAR);
        sonido_pasar_checkbox.addActionListener(e -> GameFrame.setSonidoPasar(sonido_pasar_checkbox.isSelected()));

        sonido_allin_checkbox = tog("audio.sonido_allin", GameFrame.SONIDO_ALLIN);
        sonido_allin_checkbox.addActionListener(e -> GameFrame.setSonidoAllin(sonido_allin_checkbox.isSelected()));

        sonido_ciegas_checkbox = tog("audio.sonido_ciegas", GameFrame.SONIDO_CIEGAS);
        sonido_ciegas_checkbox.addActionListener(e -> GameFrame.setSonidoCiegas(sonido_ciegas_checkbox.isSelected()));

        sonido_ultima_mano_checkbox = tog("audio.sonido_ultima_mano", GameFrame.SONIDO_ULTIMA_MANO);
        sonido_ultima_mano_checkbox.addActionListener(e -> GameFrame.setSonidoUltimaMano(sonido_ultima_mano_checkbox.isSelected()));

        sonido_pausa_checkbox = tog("audio.sonido_pausa", GameFrame.SONIDO_PAUSA);
        sonido_pausa_checkbox.addActionListener(e -> GameFrame.setSonidoPausa(sonido_pausa_checkbox.isSelected()));

        sonido_entrar_sala_checkbox = tog("audio.sonido_entrar_sala", GameFrame.SONIDO_ENTRAR_SALA);
        sonido_entrar_sala_checkbox.addActionListener(e -> GameFrame.setSonidoEntrarSala(sonido_entrar_sala_checkbox.isSelected()));

        sonido_tu_turno_checkbox = tog("audio.sonido_tu_turno", GameFrame.SONIDO_TU_TURNO);
        sonido_tu_turno_checkbox.addActionListener(e -> GameFrame.setSonidoTuTurno(sonido_tu_turno_checkbox.isSelected()));

        sonido_aviso_tiempo_checkbox = tog("audio.sonido_aviso_tiempo", GameFrame.SONIDO_AVISO_TIEMPO);
        sonido_aviso_tiempo_checkbox.addActionListener(e -> GameFrame.setSonidoAvisoTiempo(sonido_aviso_tiempo_checkbox.isSelected()));

        sonido_fin_partida_checkbox = tog("audio.sonido_fin_partida", GameFrame.SONIDO_FIN_PARTIDA);
        sonido_fin_partida_checkbox.addActionListener(e -> GameFrame.setSonidoFinPartida(sonido_fin_partida_checkbox.isSelected()));

        sonido_inicio_checkbox = tog("audio.sonido_inicio", GameFrame.SONIDO_INICIO);
        sonido_inicio_checkbox.addActionListener(e -> GameFrame.setSonidoInicio(sonido_inicio_checkbox.isSelected()));

        sonido_conexion_checkbox = tog("audio.sonido_conexion", GameFrame.SONIDO_CONEXION);
        sonido_conexion_checkbox.addActionListener(e -> GameFrame.setSonidoConexion(sonido_conexion_checkbox.isSelected()));

        sonido_iwtsth_checkbox = tog("audio.sonido_iwtsth", GameFrame.SONIDO_IWTSTH);
        sonido_iwtsth_checkbox.addActionListener(e -> GameFrame.setSonidoIwtsth(sonido_iwtsth_checkbox.isSelected()));

        sonido_zoom_checkbox = tog("audio.sonido_zoom", GameFrame.SONIDO_ZOOM);
        sonido_zoom_checkbox.addActionListener(e -> GameFrame.setSonidoZoom(sonido_zoom_checkbox.isSelected()));

        sonido_vista_compacta_checkbox = tog("audio.sonido_vista_compacta", GameFrame.SONIDO_VISTA_COMPACTA);
        sonido_vista_compacta_checkbox.addActionListener(e -> GameFrame.setSonidoVistaCompacta(sonido_vista_compacta_checkbox.isSelected()));

        sonido_screenshot_checkbox = tog("audio.sonido_screenshot", GameFrame.SONIDO_SCREENSHOT);
        sonido_screenshot_checkbox.addActionListener(e -> GameFrame.setSonidoScreenshot(sonido_screenshot_checkbox.isSelected()));

        sonido_tapete_checkbox = tog("audio.sonido_tapete", GameFrame.SONIDO_TAPETE);
        sonido_tapete_checkbox.addActionListener(e -> GameFrame.setSonidoTapete(sonido_tapete_checkbox.isSelected()));

        sonido_visor_checkbox = tog("audio.sonido_visor", GameFrame.SONIDO_VISOR);
        sonido_visor_checkbox.addActionListener(e -> GameFrame.setSonidoVisor(sonido_visor_checkbox.isSelected()));

        sonido_volumen_checkbox = tog("audio.sonido_volumen", GameFrame.SONIDO_VOLUMEN);
        sonido_volumen_checkbox.addActionListener(e -> GameFrame.setSonidoVolumen(sonido_volumen_checkbox.isSelected()));

        sonido_arranque_checkbox = tog("audio.sonido_arranque", GameFrame.SONIDO_ARRANQUE);
        sonido_arranque_checkbox.addActionListener(e -> GameFrame.setSonidoArranque(sonido_arranque_checkbox.isSelected()));

        sonido_aviso_checkbox = tog("audio.sonido_aviso", GameFrame.SONIDO_AVISO);
        sonido_aviso_checkbox.addActionListener(e -> GameFrame.setSonidoAviso(sonido_aviso_checkbox.isSelected()));

        sonido_error_checkbox = tog("audio.sonido_error", GameFrame.SONIDO_ERROR);
        sonido_error_checkbox.addActionListener(e -> GameFrame.setSonidoError(sonido_error_checkbox.isSelected()));

        sonido_error_red_checkbox = tog("audio.sonido_error_red", GameFrame.SONIDO_ERROR_RED);
        sonido_error_red_checkbox.addActionListener(e -> GameFrame.setSonidoErrorRed(sonido_error_red_checkbox.isSelected()));

        tts_checkbox = new SettingsUI.ToggleSwitch(GameFrame.TTS_SERVER);
        Helpers.setTranslatedToolTip(tts_checkbox, "tooltip.cfg.tts");
        tts_checkbox.addActionListener(e -> GameFrame.setTTSGlobal(tts_checkbox.isSelected()));

        voice_messages_checkbox = new SettingsUI.ToggleSwitch(GameFrame.VOICE_MESSAGES);
        Helpers.setTranslatedToolTip(voice_messages_checkbox, "tooltip.cfg.voice_notes_rule");
        voice_messages_checkbox.addActionListener(e -> GameFrame.setVoiceMessages(voice_messages_checkbox.isSelected()));

        sound_music_panel = new JPanel();
        sound_music_panel.setOpaque(false);
        sound_music_panel.setLayout(new BoxLayout(sound_music_panel, BoxLayout.Y_AXIS));
        // "SOUND" master flush left; the rest indented to read as dependents. Extra spacing
        // between master rows so they don't feel cramped (this column has room to spare, being
        // the shortest, without growing the dialog's height).
        sound_music_panel.add(iconRow(menuIcon("/images/menu/sound.png"), sonidos_checkbox));
        sound_music_panel.add(Box.createVerticalStrut(Math.round(6 * Helpers.DIALOG_ZOOM)));
        sound_music_panel.add(indentFill(iconRow(menuIcon("/images/menu/joke.png"), sonidos_chorra_checkbox)));

        // A little breathing room before the music box.
        sound_music_panel.add(Box.createVerticalStrut(Math.round(8 * Helpers.DIALOG_ZOOM)));

        // "Music" subpanel (thin box), SAME pattern as "Sound effects": master on top and,
        // indented below, the four individual tracks (game, waiting room, About, stats). The
        // master turns them all off; each track keeps its own toggle.
        JPanel musica_group = groupBox();
        musica_group.add(iconRow(menuIcon("/images/menu/music.png"), musica_master_checkbox));
        musica_group.add(effectRow(menuIcon("/images/menu/music.png"), musica_checkbox, false, previewButton(Audio.ASCENSOR_VOLUME.getKey())));
        musica_group.add(effectRow(menuIcon("/images/menu/bell.png"), musica_sala_checkbox, false, previewButton(Audio.WAITING_ROOM_VOLUME.getKey())));
        musica_group.add(effectRow(menuIcon("/images/menu/info.png"), musica_about_checkbox, false, previewButton(Audio.ABOUT_VOLUME.getKey())));
        musica_group.add(effectRow(menuIcon("/images/menu/meter.png"), musica_stats_checkbox, false, previewButton(Audio.STATS_VOLUME.getKey())));
        sound_music_panel.add(indent(musica_group));

        // A bit more spacing so the effects box reads as a separate subgroup.
        sound_music_panel.add(Box.createVerticalStrut(Math.round(8 * Helpers.DIALOG_ZOOM)));

        // "Sound effects" subpanel (thin box): master on top, then the individual effects
        // GROUPED BY TYPE (bold header + indented checkboxes) in TWO columns so the list doesn't
        // blow up the dialog's height. "My cards" depends (deeper indent) on "Reveal". Each
        // checkbox is named after WHAT it's for, not the sound file.
        JPanel efectos_group = groupBox(true);
        efectos_group.add(iconRow(menuIcon("/images/menu/fx.png"), sonido_efectos_checkbox));

        // Left type column: actions + cards + room. Balanced to match the right column's
        // row count.
        JPanel fx_col_a = effectsColumn();
        fx_col_a.add(typeHeader("audio.grupo_acciones"));
        fx_col_a.add(effectRow(menuIcon("/images/menu/chips.png"), sonido_apostar_checkbox, false, previewButton("misc/bet.wav")));
        fx_col_a.add(effectRow(scaledIcon("/images/action/bet.png", 24), sonido_igualar_checkbox, false, previewButton("misc/call.wav")));
        fx_col_a.add(effectRow(menuIcon("/images/menu/confirmation.png"), sonido_pasar_checkbox, false, previewButton("misc/check.wav")));
        fx_col_a.add(effectRow(scaledIcon("/images/action/up.png", 24), sonido_allin_checkbox, false, previewButton("misc/allin.wav")));
        fx_col_a.add(effectRow(scaledIcon("/images/action/down.png", 24), sonido_fold_checkbox, false, previewButton("misc/fold.wav")));
        fx_col_a.add(typeHeader("audio.grupo_cartas"));
        fx_col_a.add(effectRow(menuIcon("/images/menu/baraja.png"), sonido_barajado_checkbox, false, previewButton("misc/shuffle.wav")));
        fx_col_a.add(effectRow(menuIcon("/images/menu/dealer.png"), sonido_reparto_checkbox, false, previewButton("misc/deal.wav")));
        // "Reveal" + its suboption "my cards" (deeper indent) as regular column rows, so their
        // toggles line up with the rest of the effects on the column's right edge.
        fx_col_a.add(effectRow(menuIcon("/images/menu/flip.png"), sonido_destape_checkbox, false, previewButton("misc/uncover.wav")));
        fx_col_a.add(effectRow(menuIcon("/images/menu/baraja.png"), sonido_destape_mis_checkbox, true, previewButton("misc/uncover.wav")));
        fx_col_a.add(typeHeader("audio.grupo_sala"));
        fx_col_a.add(effectRow(scaledIcon("/images/start.png", 24), sonido_entra_checkbox, false, previewButton("misc/laser.wav")));
        fx_col_a.add(effectRow(menuIcon("/images/menu/bell.png"), sonido_entrar_sala_checkbox, false, previewButton("misc/new_user.wav")));
        fx_col_a.add(effectRow(scaledIcon("/images/action/plug.png", 24), sonido_conexion_checkbox, false, previewButton("misc/yahoo.wav")));
        fx_col_a.add(effectRow(scaledIcon("/images/exit.png", 24), sonido_sale_checkbox, false, previewButton("misc/toilet.wav")));
        fx_col_a.add(typeHeader("audio.grupo_avisos"));
        fx_col_a.add(effectRow(menuIcon("/images/menu/info.png"), sonido_aviso_checkbox, false, previewButton("misc/warning.wav")));
        fx_col_a.add(effectRow(menuIcon("/images/menu/stop.png"), sonido_error_checkbox, false, previewButton("misc/danger_alert.wav")));
        fx_col_a.add(effectRow(menuIcon("/images/menu/close.png"), sonido_error_red_checkbox, false, previewButton("misc/network_error_" + GameFrame.LANGUAGE.toLowerCase() + ".wav")));
        // Pins the rows to the top: if this column ends up shorter, the glue absorbs the extra
        // space below it (otherwise BoxLayout could center them, misaligning the headers).
        fx_col_a.add(Box.createVerticalGlue());

        // Right type column: game state, turn/timer and UI. The old catch-all "Other" group
        // (lights switch, rebuy, IWTSTH) is folded into "Game", where it actually belongs: they
        // are all events of the game's progress.
        JPanel fx_col_b = effectsColumn();
        fx_col_b.add(typeHeader("audio.grupo_partida"));
        fx_col_b.add(effectRow(menuIcon("/images/menu/games.png"), sonido_inicio_checkbox, false, previewButton("misc/startplay.wav")));
        fx_col_b.add(effectRow(fitIcon("/images/ciegas.png", EFFECT_ICON_CELL_W, EFFECT_ICON_CELL_H), sonido_ciegas_checkbox, false, previewButton("misc/double_blinds.wav")));
        fx_col_b.add(effectRow(menuIcon("/images/menu/rebuy.png"), sonido_caja_checkbox, false, previewButton("misc/cash_register.wav")));
        fx_col_b.add(effectRow(menuIcon("/images/menu/last_hand.png"), sonido_ultima_mano_checkbox, false, previewButton("misc/last_hand_on.wav")));
        fx_col_b.add(effectRow(menuIcon("/images/menu/meter.png"), sonido_conteo_checkbox, false, previewButton("misc/balance_count.wav")));
        fx_col_b.add(effectRow(menuIcon("/images/menu/video.png"), sonido_iwtsth_checkbox, false, previewButton("misc/iwtsth.wav")));
        fx_col_b.add(effectRow(fitIcon("/images/lights_on.png", EFFECT_ICON_CELL_W, EFFECT_ICON_CELL_H), sonido_interruptor_checkbox, false, previewButton("misc/button_on.wav")));
        fx_col_b.add(effectRow(scaledIcon("/images/pause.png", 24), sonido_pausa_checkbox, false, previewButton("misc/pause.wav")));
        fx_col_b.add(effectRow(scaledIcon("/images/action/skull.png", 24), sonido_fin_partida_checkbox, false, previewButton("misc/game_over.wav")));
        fx_col_b.add(typeHeader("audio.grupo_turno_tiempo"));
        fx_col_b.add(effectRow(scaledIcon("/images/action/vamos.png", 24), sonido_tu_turno_checkbox, false, previewButton("misc/yourturn.wav")));
        fx_col_b.add(effectRow(scaledIcon("/images/action/timeout.png", 24), sonido_aviso_tiempo_checkbox, false, previewButton("misc/hurryup.wav")));
        fx_col_b.add(typeHeader("audio.grupo_interfaz"));
        fx_col_b.add(effectRow(menuIcon("/images/menu/zoom.png"), sonido_zoom_checkbox, false, previewButton("misc/zoom_in.wav")));
        fx_col_b.add(effectRow(menuIcon("/images/menu/tiny.png"), sonido_vista_compacta_checkbox, false, previewButton("misc/power_down.wav")));
        fx_col_b.add(effectRow(scaledIcon("/images/screenshot.png", 24), sonido_screenshot_checkbox, false, previewButton("misc/screenshot.wav")));
        fx_col_b.add(effectRow(menuIcon("/images/menu/tapetes.png"), sonido_tapete_checkbox, false, previewButton("misc/mat.wav")));
        fx_col_b.add(effectRow(menuIcon("/images/menu/eyes.png"), sonido_visor_checkbox, false, previewButton("misc/card_visor.wav")));
        fx_col_b.add(effectRow(menuIcon("/images/menu/sound.png"), sonido_volumen_checkbox, false, previewButton("misc/volume_change.wav")));
        fx_col_b.add(effectRow(menuIcon("/images/menu/corona.png"), sonido_arranque_checkbox, false, previewButton("misc/init.wav")));
        fx_col_b.add(Box.createVerticalGlue());

        // GridBagLayout, not GridLayout: each subcolumn takes its PREFERRED width (weightx=0);
        // GridLayout forced them to the SAME width, leaving dead space in the narrower one. Both
        // stretch to the SAME height (fill=BOTH, weighty=1) so their headers line up. An ELASTIC
        // cell (weightx=1) sits between them: widening the dialog opens up the gap between
        // columns instead of leaving dead space to the right of the box. Max width is unbounded
        // (so it can stretch) and height is capped at preferred (won't grow further); the
        // shorter left column absorbs the leftover space with its own glue.
        JPanel fx_cols = new JPanel(new java.awt.GridBagLayout()) {
            @Override
            public java.awt.Dimension getMaximumSize() {
                return new java.awt.Dimension(Short.MAX_VALUE, getPreferredSize().height);
            }
        };
        fx_cols.setOpaque(false);
        fx_cols.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        java.awt.GridBagConstraints fx_gbc = new java.awt.GridBagConstraints();
        fx_gbc.gridy = 0;
        fx_gbc.fill = java.awt.GridBagConstraints.BOTH;
        fx_gbc.weighty = 1.0;
        fx_gbc.weightx = 0.0;
        fx_gbc.anchor = java.awt.GridBagConstraints.NORTHWEST;
        fx_gbc.gridx = 0;
        // Minimum gap between columns at the dialog's natural width.
        fx_gbc.insets = new java.awt.Insets(0, 0, 0, Math.round(16 * Helpers.DIALOG_ZOOM));
        fx_cols.add(fx_col_a, fx_gbc);
        // Center spring: absorbs all the leftover width and pushes the 2nd column right.
        fx_gbc.gridx = 1;
        fx_gbc.weightx = 1.0;
        fx_gbc.insets = new java.awt.Insets(0, 0, 0, 0);
        fx_cols.add(Box.createHorizontalGlue(), fx_gbc);
        fx_gbc.gridx = 2;
        fx_gbc.weightx = 0.0;
        fx_cols.add(fx_col_b, fx_gbc);
        efectos_group.add(fx_cols);
        sound_music_panel.add(indentFill(efectos_group));

        // --- Output device ---
        DefaultListModel<String> output_model = new DefaultListModel<>();

        output_model.addElement(Translator.translate("audio.dispositivo_default"));

        for (Mixer.Info info : output_devices) {
            output_model.addElement(info.getName());
        }

        output_list = new JList<>(output_model);
        output_list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // Preferred height of 4 rows; the card keeps this natural height (the right column's
        // uniform elastic gaps absorb the leftover, see buildUI) and the JScrollPane handles any
        // extra devices.
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
                            if (GameFrame.volumenSonidoOn()) {
                                Audio.playWavResource("misc/volume_change.wav");
                            }
                        });
                    }
                }
            }
        });

        output_panel = new JPanel(new BorderLayout());
        output_panel.setOpaque(false);
        output_panel.add(new JScrollPane(output_list), BorderLayout.CENTER);

        // --- Input device: microphone ---
        mic_checkbox = new SettingsUI.ToggleSwitch(AudioDeviceManager.isMicEnabled());

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

        mic_panel = new JPanel(new BorderLayout(0, 5));
        mic_panel.setOpaque(false);
        JLabel mic_label = new JLabel(Translator.translate("audio.microfono_activado"));
        mic_label.setIcon(scaledIcon("/images/microphone_black.png", 24));
        mic_label.setIconTextGap(Math.round(8 * Helpers.DIALOG_ZOOM));
        mic_panel.add(SettingsUI.alignedRow(0, mic_label, mic_checkbox), BorderLayout.NORTH);
        mic_panel.add(new JScrollPane(capture_list), BorderLayout.CENTER);

        // --- Voice note options ---
        // LOCAL master toggle in POSITIVE logic: ON = local voice notes active (microphone,
        // record hotkey and playback). Under the hood it's still stored as a "block" flag
        // (AudioDeviceManager.setBlockVoiceMessages), so the rest of the code (note receiving,
        // waiting room) doesn't change; only the displayed value is inverted here.
        notes_local_checkbox = new SettingsUI.ToggleSwitch(!AudioDeviceManager.isBlockVoiceMessages());
        Helpers.setTranslatedToolTip(notes_local_checkbox, "tooltip.cfg.notes_local");

        notes_local_checkbox.addActionListener(e -> {
            AudioDeviceManager.setBlockVoiceMessages(!notes_local_checkbox.isSelected());

            refreshVoiceControlsEnabled();
        });

        play_own_checkbox = new SettingsUI.ToggleSwitch(AudioDeviceManager.isPlayOwnVoiceMessages());
        Helpers.setTranslatedToolTip(play_own_checkbox, "tooltip.cfg.play_own_notes");

        play_own_checkbox.addActionListener(e -> AudioDeviceManager.setPlayOwnVoiceMessages(play_own_checkbox.isSelected()));

        // --- Retention: days a stored voice note survives before the startup
        // purge drops it (0 = forever). Parallel to VOICE_NOTE_RETENTION_OPTIONS.
        retention_combo = new JComboBox<>();
        Helpers.setTranslatedToolTip(retention_combo, "tooltip.cfg.notes_retention");

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

        // A bit wider: prototype longer than any real item ("Always", "90 days") so the
        // combo (in BorderLayout.EAST) doesn't end up cramped. Uses the combo's own font, so
        // the width scales with the host's updateFonts.
        retention_combo.setPrototypeDisplayValue(Translator.translate("audio.retencion_dias", 999) + "  ");

        JLabel retention_label = new JLabel(Translator.translate("audio.conservar_notas"));
        retention_label.setIconTextGap(Math.round(8 * Helpers.DIALOG_ZOOM));
        retention_panel = SettingsUI.alignedRow(0, retention_label, retention_combo);

        // Manual wipe: drops every stored note now, independent of retention and
        // of the self-block toggle (you may want to clear disk even while blocking).
        purge_button = new JButton(Translator.translate("audio.purgar_notas"));
        Helpers.setTranslatedToolTip(purge_button, "tooltip.cfg.purge_notes");

        purge_button.addActionListener(e -> {
            if (Helpers.mostrarMensajeInformativoSINO(this, Translator.translate("audio.purgar_notas_confirm")) == javax.swing.JOptionPane.YES_OPTION) {
                Helpers.threadRun(() -> {
                    int deleted = Helpers.purgeAllVoiceNotes();
                    Helpers.GUIRun(() -> Helpers.mostrarMensajeInformativo(this, Translator.translate("audio.purgar_notas_resultado", deleted)));
                });
            }
        });

        // Opens the voice notes viewer (gallery of the .wav files saved under VOICE_DIR).
        // Always enabled: notes can be reviewed/played/deleted even while notes are disabled.
        JButton view_notes_button = new JButton(Translator.translate("audio.ver_notas"));
        Helpers.setTranslatedToolTip(view_notes_button, "tooltip.cfg.view_notes");
        view_notes_button.addActionListener(e -> VoiceNotesViewerDialog.open(javax.swing.SwingUtilities.getWindowAncestor(this)));

        // Both buttons equal-width, filling the row so the right edge lines up with the
        // "keep notes" combo above; natural height (doesn't stretch in the vertical BoxLayout).
        purge_panel = new JPanel(new java.awt.GridLayout(1, 2, Math.round(8 * Helpers.DIALOG_ZOOM), 0)) {
            @Override
            public java.awt.Dimension getMaximumSize() {
                return new java.awt.Dimension(Short.MAX_VALUE, getPreferredSize().height);
            }
        };
        purge_panel.setOpaque(false);
        purge_panel.add(view_notes_button);
        purge_panel.add(purge_button);

        refreshVoiceControlsEnabled();

        notes_panel = new JPanel();
        notes_panel.setOpaque(false);
        notes_panel.setLayout(new BoxLayout(notes_panel, BoxLayout.Y_AXIS));

        retention_panel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        purge_panel.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        // GLOBAL table rule (server) on top, then the LOCAL master toggle that gates the rest of
        // the voice-note controls (both in positive logic). "Play my own notes" hangs off the
        // LOCAL master under a guide. Retention and purge are NOT gated (they manage already-saved
        // notes), so they stay at the base level.
        JLabel voice_messages_label = new JLabel(Translator.translate("menu.notas_de_voz"));
        voice_messages_label.setIcon(scaledIcon("/images/microphone_black.png", 24));
        voice_messages_label.setIconTextGap(Math.round(8 * Helpers.DIALOG_ZOOM));
        JLabel notes_local_label = new JLabel(Translator.translate("audio.notas_de_voz_local"));
        notes_local_label.setIcon(scaledIcon("/images/microphone_black.png", 24));
        notes_local_label.setIconTextGap(Math.round(8 * Helpers.DIALOG_ZOOM));
        JLabel play_own_label = new JLabel(Translator.translate("audio.reproducir_mis_notas"));
        play_own_label.setIconTextGap(Math.round(8 * Helpers.DIALOG_ZOOM));
        notes_panel.add(SettingsUI.alignedRow(0, voice_messages_label, voice_messages_checkbox));
        notes_panel.add(Box.createVerticalStrut(Math.round(6 * Helpers.DIALOG_ZOOM)));
        notes_panel.add(SettingsUI.alignedRow(0, notes_local_label, notes_local_checkbox));
        JPanel play_own_group = SettingsUI.guideGroup();
        SettingsUI.addToGroup(play_own_group, SettingsUI.alignedRow(0, play_own_label, play_own_checkbox));
        notes_panel.add(play_own_group);
        notes_panel.add(Box.createVerticalStrut(Math.round(6 * Helpers.DIALOG_ZOOM)));
        notes_panel.add(retention_panel);
        notes_panel.add(Box.createVerticalStrut(Math.round(6 * Helpers.DIALOG_ZOOM)));
        notes_panel.add(purge_panel);

        // --- Voice (TTS): GLOBAL table rule (server) on top; LOCAL toggle below. Both in
        // positive logic: under the hood the local one is still stored as a "block" flag
        // (setBlockTtsLocal), so GameFrame doesn't change; only the displayed value is inverted.
        tts_local_checkbox = new SettingsUI.ToggleSwitch(!AudioDeviceManager.isBlockTtsLocal());
        tts_local_checkbox.addActionListener(e -> AudioDeviceManager.setBlockTtsLocal(!tts_local_checkbox.isSelected()));

        tts_panel = new JPanel();
        tts_panel.setOpaque(false);
        tts_panel.setLayout(new BoxLayout(tts_panel, BoxLayout.Y_AXIS));
        // GLOBAL table rule (server) on top; LOCAL below, both with the voice icon.
        JLabel tts_label = new JLabel(Translator.translate("menu.tts"));
        tts_label.setIcon(menuIcon("/images/menu/voice.png"));
        tts_label.setIconTextGap(Math.round(8 * Helpers.DIALOG_ZOOM));
        JLabel tts_local_label = new JLabel(Translator.translate("audio.tts_local"));
        tts_local_label.setIcon(menuIcon("/images/menu/voice.png"));
        tts_local_label.setIconTextGap(Math.round(8 * Helpers.DIALOG_ZOOM));
        tts_panel.add(SettingsUI.alignedRow(0, tts_label, tts_checkbox));
        tts_panel.add(Box.createVerticalStrut(Math.round(6 * Helpers.DIALOG_ZOOM)));
        tts_panel.add(SettingsUI.alignedRow(0, tts_local_label, tts_local_checkbox));

        // Note (CLIENT-in-game only): GLOBAL rules are sent by the server and shown greyed out
        // (local settings don't touch them). Both GLOBAL checkboxes (Voice notes and TTS) now
        // live in the right column; the note goes at the bottom of the dialog (SOUTH), under both
        // columns. Hidden unless you're a client. Fixed HTML width so it wraps instead of getting
        // cut off.
        JLabel global_note = new JLabel("<html><div style='width:240px'>" + Translator.translate("audio.ajustes_locales_ignorados") + "</div></html>");
        global_note.setForeground(java.awt.Color.GRAY);
        global_note.setVisible(global_rules_locked);

        refreshSoundControlsEnabled();

        // --- Two columns to minimize the dialog's HEIGHT: LEFT "Sound & music" (the tallest
        // panel, so it drives the height); RIGHT the stacked VOICE/input block — "Output device",
        // "Input device", "Voice notes" and "Voice (TTS)". The device lists (uncapped) stretch to
        // fill the column and LEVEL their bottom edge with the left column's; "Voice notes" and
        // "Voice (TTS)" (capped to their preferred height) fall below them, both being VOICE. ---
        // Each section becomes a rounded card (SettingsUI.card) wrapping its existing content.
        JPanel sound_music_card = SettingsUI.card("audio.sonido_musica");
        sound_music_card.add(sound_music_panel);
        sound_music_card.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        JPanel output_card = SettingsUI.card("audio.dispositivo_salida");
        output_card.add(output_panel);
        output_card.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        JPanel mic_card = SettingsUI.card("audio.dispositivo_entrada");
        mic_card.add(mic_panel);
        mic_card.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        JPanel notes_card = SettingsUI.card("audio.notas_de_voz");
        notes_card.add(notes_panel);
        notes_card.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        JPanel tts_card = SettingsUI.card("audio.voz_tts");
        tts_card.add(tts_panel);
        tts_card.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        JPanel left_col = new JPanel();
        left_col.setOpaque(false);
        left_col.setLayout(new BoxLayout(left_col, BoxLayout.Y_AXIS));
        left_col.setAlignmentY(JComponent.TOP_ALIGNMENT);
        left_col.add(sound_music_card);
        left_col.add(Box.createVerticalGlue());

        // Right column: the four cards stacked with UNIFORM elastic gaps between them (each at
        // least 8px, all growing equally). The cards keep their natural height (weighty 0); since
        // the column stretches to the taller left one (effects; fill BOTH below), the leftover is
        // split evenly among the gaps so the cards spread out uniformly and "Voice (TTS)" ends
        // flush at the bottom, level with the left column. No filler after the last card, so it
        // stays pinned to the bottom.
        JPanel right_col = new JPanel(new java.awt.GridBagLayout());
        right_col.setOpaque(false);
        right_col.setAlignmentY(JComponent.TOP_ALIGNMENT);
        JPanel[] right_boxes = {output_card, mic_card, notes_card, tts_card};
        java.awt.GridBagConstraints right_gbc = new java.awt.GridBagConstraints();
        right_gbc.gridx = 0;
        right_gbc.weightx = 1;
        int right_row = 0;
        for (int i = 0; i < right_boxes.length; i++) {
            if (i > 0) {
                right_gbc.gridy = right_row++;
                right_gbc.weighty = 1;
                right_gbc.fill = java.awt.GridBagConstraints.VERTICAL;
                right_col.add(new Box.Filler(
                        new java.awt.Dimension(0, Math.round(8 * Helpers.DIALOG_ZOOM)),
                        new java.awt.Dimension(0, Math.round(8 * Helpers.DIALOG_ZOOM)),
                        new java.awt.Dimension(0, Short.MAX_VALUE)), right_gbc);
            }
            right_gbc.gridy = right_row++;
            right_gbc.weighty = 0;
            right_gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
            right_gbc.anchor = java.awt.GridBagConstraints.NORTH;
            right_col.add(right_boxes[i], right_gbc);
        }

        // GridBagLayout, not GridLayout: each column takes its PREFERRED width — no longer
        // forced equal, which used to widen the dialog by needlessly stretching the right column
        // (device lists) — but BOTH stretch to the SAME height (fill=BOTH, weighty=1), so their
        // bottom edges stay aligned. On pack, the width is exactly what's needed; weightx=1
        // splits any leftover width evenly (no centered dead space).
        JPanel center_panel = new JPanel(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints center_gbc = new java.awt.GridBagConstraints();
        center_gbc.gridy = 0;
        center_gbc.fill = java.awt.GridBagConstraints.BOTH;
        center_gbc.weighty = 1.0;
        // Both columns at weightx=1: leftover width (from widening the dialog) is split evenly
        // between them, so they grow together instead of only the right one stretching.
        center_gbc.weightx = 1.0;
        center_gbc.gridx = 0;
        center_gbc.insets = new java.awt.Insets(0, 0, 0, Math.round(12 * Helpers.DIALOG_ZOOM));
        center_panel.add(left_col, center_gbc);
        center_gbc.gridx = 1;
        center_gbc.weightx = 1.0;
        center_gbc.insets = new java.awt.Insets(0, 0, 0, 0);
        center_panel.add(right_col, center_gbc);

        JPanel volume_card = SettingsUI.card("audio.volumen_general");
        volume_card.add(volume_panel);
        add(volume_card, BorderLayout.NORTH);
        add(center_panel, BorderLayout.CENTER);
        // Global-rules note at the bottom, under both columns: covers the GLOBAL checkboxes of
        // both (TTS on the left, Voice notes on the right). Only ADDED when you're a client in a
        // game; unlike BoxLayout, BorderLayout.SOUTH reserves the component's height even while
        // invisible, so outside of a game it's not added at all (to avoid an empty strip eating
        // into the height we saved).
        if (global_rules_locked) {
            add(global_note, BorderLayout.SOUTH);
        }

        loading = false;

        INSTANCE = this;
    }

    /**
     * Applies the scaled audio font (1.2x, like the old dialog) and the fixes that depend
     * on it: border-title fonts (updateFonts doesn't reach them) and max heights for
     * rows/panels that shouldn't stretch.
     *
     * <p>The host must call this AFTER its own general updateFonts.
     */
    public void applyFontsAndSizing() {

        // Note: the HOST applies the font before calling this. The section titles are painted by
        // SettingsUI.card (independent of the host font), so there are no border titles to restyle
        // here anymore — only the row/panel heights.

        // In the vertical BoxLayout, the retention and purge rows must keep their natural
        // height instead of stretching to fill the gap.
        retention_panel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, retention_panel.getPreferredSize().height));
        purge_panel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, purge_panel.getPreferredSize().height));

        // "Sound & music" (left column, vertical BoxLayout) is capped to its preferred height so
        // its inner rows stay compact. "Voice notes" and "Voice (TTS)" sit in the right column,
        // which spreads its cards with uniform elastic gaps (see buildUI), so they keep their
        // natural height there and the cap is harmless. The device lists keep their 4-row
        // preferred height too (no longer stretched); the JScrollPane covers any extra devices.
        // Done AFTER updateFonts, so the preferred height already reflects the scaled font.
        sound_music_panel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, sound_music_panel.getPreferredSize().height));
        tts_panel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, tts_panel.getPreferredSize().height));
        notes_panel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, notes_panel.getPreferredSize().height));
    }

    /**
     * The host MUST call this on close: persists the master volume and releases the live
     * instance reference.
     */
    public void cleanup() {

        // Stops any preview in progress (a track's or effect's play button) on close.
        Audio.stopPreview();

        Helpers.PROPERTIES.setProperty("master_volume", String.valueOf(Audio.MASTER_VOLUME));
        Helpers.savePropertiesFile();

        if (INSTANCE == this) {
            INSTANCE = null;
        }
    }

    /**
     * Whether any audio setting differs from the state captured on open. Used by the host
     * to confirm before discarding on cancel.
     *
     * @return {@code true} if something changed since the panel was opened
     */
    public boolean isDirty() {
        return Audio.MASTER_VOLUME != snap_master_volume
                || GameFrame.SONIDOS != snap_sonidos
                || GameFrame.SONIDOS_CHORRA != snap_sonidos_chorra
                || GameFrame.MUSICA != snap_musica_master
                || GameFrame.MUSICA_AMBIENTAL != snap_musica
                || GameFrame.MUSICA_SALA != snap_musica_sala
                || GameFrame.MUSICA_ABOUT != snap_musica_about
                || GameFrame.MUSICA_STATS != snap_musica_stats
                || GameFrame.SONIDO_EFECTOS != snap_sonido_efectos
                || GameFrame.SONIDO_BARAJADO != snap_sonido_barajado
                || GameFrame.SONIDO_REPARTO != snap_sonido_reparto
                || GameFrame.SONIDO_DESTAPE != snap_sonido_destape
                || GameFrame.SONIDO_DESTAPE_MIS_CARTAS != snap_sonido_destape_mis
                || GameFrame.SONIDO_APOSTAR != snap_sonido_apostar
                || GameFrame.SONIDO_FOLD != snap_sonido_fold
                || GameFrame.SONIDO_CONTEO != snap_sonido_conteo
                || GameFrame.SONIDO_ENTRA != snap_sonido_entra
                || GameFrame.SONIDO_SALE != snap_sonido_sale
                || GameFrame.SONIDO_INTERRUPTOR != snap_sonido_interruptor
                || GameFrame.SONIDO_CAJA != snap_sonido_caja
                || GameFrame.SONIDO_IGUALAR != snap_sonido_igualar
                || GameFrame.SONIDO_PASAR != snap_sonido_pasar
                || GameFrame.SONIDO_ALLIN != snap_sonido_allin
                || GameFrame.SONIDO_CIEGAS != snap_sonido_ciegas
                || GameFrame.SONIDO_ULTIMA_MANO != snap_sonido_ultima_mano
                || GameFrame.SONIDO_PAUSA != snap_sonido_pausa
                || GameFrame.SONIDO_ENTRAR_SALA != snap_sonido_entrar_sala
                || GameFrame.SONIDO_TU_TURNO != snap_sonido_tu_turno
                || GameFrame.SONIDO_AVISO_TIEMPO != snap_sonido_aviso_tiempo
                || GameFrame.SONIDO_FIN_PARTIDA != snap_sonido_fin_partida
                || GameFrame.SONIDO_INICIO != snap_sonido_inicio
                || GameFrame.SONIDO_CONEXION != snap_sonido_conexion
                || GameFrame.SONIDO_IWTSTH != snap_sonido_iwtsth
                || GameFrame.SONIDO_ZOOM != snap_sonido_zoom
                || GameFrame.SONIDO_VISTA_COMPACTA != snap_sonido_vista_compacta
                || GameFrame.SONIDO_SCREENSHOT != snap_sonido_screenshot
                || GameFrame.SONIDO_TAPETE != snap_sonido_tapete
                || GameFrame.SONIDO_VISOR != snap_sonido_visor
                || GameFrame.SONIDO_VOLUMEN != snap_sonido_volumen
                || GameFrame.SONIDO_ARRANQUE != snap_sonido_arranque
                || GameFrame.SONIDO_AVISO != snap_sonido_aviso
                || GameFrame.SONIDO_ERROR != snap_sonido_error
                || GameFrame.SONIDO_ERROR_RED != snap_sonido_error_red
                // Global rules (TTS/notes): as a CLIENT they're sent by the server (you don't
                // own them); ignored here to avoid a spurious "discard changes?" prompt or
                // reverting over a host broadcast.
                || (!global_rules_locked && GameFrame.TTS_SERVER != snap_tts_server)
                || (!global_rules_locked && GameFrame.VOICE_MESSAGES != snap_voice_messages)
                || !java.util.Objects.equals(snap_output_device, AudioDeviceManager.getOutputDevice())
                || !java.util.Objects.equals(snap_capture_device, AudioDeviceManager.getCaptureDevice())
                || AudioDeviceManager.isMicEnabled() != snap_mic_enabled
                || AudioDeviceManager.isBlockVoiceMessages() != snap_block_voice
                || AudioDeviceManager.isPlayOwnVoiceMessages() != snap_play_own
                || AudioDeviceManager.getVoiceNoteRetentionDays() != snap_retention_days
                || AudioDeviceManager.isBlockTtsLocal() != snap_block_tts_local;
    }

    /**
     * Reverts audio settings to the state captured on open (CANCEL of the transactional
     * dialog), re-applying each one through its normal setter — global changes re-emit
     * their broadcast, restoring clients too.
     */
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
        if (GameFrame.MUSICA != snap_musica_master) {
            GameFrame.setMusica(snap_musica_master);
        }
        if (GameFrame.MUSICA_AMBIENTAL != snap_musica) {
            GameFrame.setMusicaAmbiental(snap_musica);
        }
        if (GameFrame.MUSICA_SALA != snap_musica_sala) {
            GameFrame.setMusicaSala(snap_musica_sala);
        }
        if (GameFrame.MUSICA_ABOUT != snap_musica_about) {
            GameFrame.setMusicaAbout(snap_musica_about);
        }
        if (GameFrame.MUSICA_STATS != snap_musica_stats) {
            GameFrame.setMusicaStats(snap_musica_stats);
        }
        if (GameFrame.SONIDO_EFECTOS != snap_sonido_efectos) {
            GameFrame.setSonidoEfectos(snap_sonido_efectos);
        }
        if (GameFrame.SONIDO_BARAJADO != snap_sonido_barajado) {
            GameFrame.setSonidoBarajado(snap_sonido_barajado);
        }
        if (GameFrame.SONIDO_REPARTO != snap_sonido_reparto) {
            GameFrame.setSonidoReparto(snap_sonido_reparto);
        }
        if (GameFrame.SONIDO_DESTAPE != snap_sonido_destape) {
            GameFrame.setSonidoDestape(snap_sonido_destape);
        }
        if (GameFrame.SONIDO_DESTAPE_MIS_CARTAS != snap_sonido_destape_mis) {
            GameFrame.setSonidoDestapeMisCartas(snap_sonido_destape_mis);
        }
        if (GameFrame.SONIDO_APOSTAR != snap_sonido_apostar) {
            GameFrame.setSonidoApostar(snap_sonido_apostar);
        }
        if (GameFrame.SONIDO_FOLD != snap_sonido_fold) {
            GameFrame.setSonidoFold(snap_sonido_fold);
        }
        if (GameFrame.SONIDO_CONTEO != snap_sonido_conteo) {
            GameFrame.setSonidoConteo(snap_sonido_conteo);
        }
        if (GameFrame.SONIDO_ENTRA != snap_sonido_entra) {
            GameFrame.setSonidoEntra(snap_sonido_entra);
        }
        if (GameFrame.SONIDO_SALE != snap_sonido_sale) {
            GameFrame.setSonidoSale(snap_sonido_sale);
        }
        if (GameFrame.SONIDO_INTERRUPTOR != snap_sonido_interruptor) {
            GameFrame.setSonidoInterruptor(snap_sonido_interruptor);
        }
        if (GameFrame.SONIDO_CAJA != snap_sonido_caja) {
            GameFrame.setSonidoCaja(snap_sonido_caja);
        }
        if (GameFrame.SONIDO_IGUALAR != snap_sonido_igualar) {
            GameFrame.setSonidoIgualar(snap_sonido_igualar);
        }
        if (GameFrame.SONIDO_PASAR != snap_sonido_pasar) {
            GameFrame.setSonidoPasar(snap_sonido_pasar);
        }
        if (GameFrame.SONIDO_ALLIN != snap_sonido_allin) {
            GameFrame.setSonidoAllin(snap_sonido_allin);
        }
        if (GameFrame.SONIDO_CIEGAS != snap_sonido_ciegas) {
            GameFrame.setSonidoCiegas(snap_sonido_ciegas);
        }
        if (GameFrame.SONIDO_ULTIMA_MANO != snap_sonido_ultima_mano) {
            GameFrame.setSonidoUltimaMano(snap_sonido_ultima_mano);
        }
        if (GameFrame.SONIDO_PAUSA != snap_sonido_pausa) {
            GameFrame.setSonidoPausa(snap_sonido_pausa);
        }
        if (GameFrame.SONIDO_ENTRAR_SALA != snap_sonido_entrar_sala) {
            GameFrame.setSonidoEntrarSala(snap_sonido_entrar_sala);
        }
        if (GameFrame.SONIDO_TU_TURNO != snap_sonido_tu_turno) {
            GameFrame.setSonidoTuTurno(snap_sonido_tu_turno);
        }
        if (GameFrame.SONIDO_AVISO_TIEMPO != snap_sonido_aviso_tiempo) {
            GameFrame.setSonidoAvisoTiempo(snap_sonido_aviso_tiempo);
        }
        if (GameFrame.SONIDO_FIN_PARTIDA != snap_sonido_fin_partida) {
            GameFrame.setSonidoFinPartida(snap_sonido_fin_partida);
        }
        if (GameFrame.SONIDO_INICIO != snap_sonido_inicio) {
            GameFrame.setSonidoInicio(snap_sonido_inicio);
        }
        if (GameFrame.SONIDO_CONEXION != snap_sonido_conexion) {
            GameFrame.setSonidoConexion(snap_sonido_conexion);
        }
        if (GameFrame.SONIDO_IWTSTH != snap_sonido_iwtsth) {
            GameFrame.setSonidoIwtsth(snap_sonido_iwtsth);
        }
        if (GameFrame.SONIDO_ZOOM != snap_sonido_zoom) {
            GameFrame.setSonidoZoom(snap_sonido_zoom);
        }
        if (GameFrame.SONIDO_VISTA_COMPACTA != snap_sonido_vista_compacta) {
            GameFrame.setSonidoVistaCompacta(snap_sonido_vista_compacta);
        }
        if (GameFrame.SONIDO_SCREENSHOT != snap_sonido_screenshot) {
            GameFrame.setSonidoScreenshot(snap_sonido_screenshot);
        }
        if (GameFrame.SONIDO_TAPETE != snap_sonido_tapete) {
            GameFrame.setSonidoTapete(snap_sonido_tapete);
        }
        if (GameFrame.SONIDO_VISOR != snap_sonido_visor) {
            GameFrame.setSonidoVisor(snap_sonido_visor);
        }
        if (GameFrame.SONIDO_VOLUMEN != snap_sonido_volumen) {
            GameFrame.setSonidoVolumen(snap_sonido_volumen);
        }
        if (GameFrame.SONIDO_ARRANQUE != snap_sonido_arranque) {
            GameFrame.setSonidoArranque(snap_sonido_arranque);
        }
        if (GameFrame.SONIDO_AVISO != snap_sonido_aviso) {
            GameFrame.setSonidoAviso(snap_sonido_aviso);
        }
        if (GameFrame.SONIDO_ERROR != snap_sonido_error) {
            GameFrame.setSonidoError(snap_sonido_error);
        }
        if (GameFrame.SONIDO_ERROR_RED != snap_sonido_error_red) {
            GameFrame.setSonidoErrorRed(snap_sonido_error_red);
        }
        // Global rules (TTS/notes): only the HOST reverts them (it owns them). For a client
        // they're governed by the server's broadcast; reverting them here would desync it.
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
    }

    /**
     * Restores ALL audio settings to their factory values, applying them LIVE like any
     * other edit (transactional dialog: SAVE keeps them, Cancel reverts to the state on
     * open). Covers master volume, every sound/music/joke toggle, devices, microphone,
     * voice notes, retention and local TTS. GLOBAL table rules (TTS and voice notes) are
     * only reset when NOT a client (a client gets them from the server). Invoked by the
     * dialog's "Restore defaults" button.
     */
    public void restoreDefaults() {

        // Master volume (0.8 = factory value). setValue triggers the listener, which applies
        // and persists it (loading is false at this point).
        volume_slider.setValue(80);

        // Toggles: set the flag through its setter (applies live + persists) and sync the
        // checkbox (setSelected does NOT fire the listener, avoiding a double apply).
        applyDefault(GameFrame::setSonidos, sonidos_checkbox, true);
        applyDefault(GameFrame::setSonidosChorra, sonidos_chorra_checkbox, false);
        applyDefault(GameFrame::setMusica, musica_master_checkbox, true);
        applyDefault(GameFrame::setMusicaAmbiental, musica_checkbox, true);
        applyDefault(GameFrame::setMusicaSala, musica_sala_checkbox, true);
        applyDefault(GameFrame::setMusicaAbout, musica_about_checkbox, true);
        applyDefault(GameFrame::setMusicaStats, musica_stats_checkbox, true);
        applyDefault(GameFrame::setSonidoEfectos, sonido_efectos_checkbox, true);
        applyDefault(GameFrame::setSonidoBarajado, sonido_barajado_checkbox, true);
        applyDefault(GameFrame::setSonidoReparto, sonido_reparto_checkbox, true);
        applyDefault(GameFrame::setSonidoDestape, sonido_destape_checkbox, true);
        applyDefault(GameFrame::setSonidoDestapeMisCartas, sonido_destape_mis_checkbox, false);
        applyDefault(GameFrame::setSonidoApostar, sonido_apostar_checkbox, true);
        applyDefault(GameFrame::setSonidoFold, sonido_fold_checkbox, true);
        applyDefault(GameFrame::setSonidoConteo, sonido_conteo_checkbox, true);
        applyDefault(GameFrame::setSonidoEntra, sonido_entra_checkbox, true);
        applyDefault(GameFrame::setSonidoSale, sonido_sale_checkbox, true);
        applyDefault(GameFrame::setSonidoInterruptor, sonido_interruptor_checkbox, true);
        applyDefault(GameFrame::setSonidoCaja, sonido_caja_checkbox, true);
        applyDefault(GameFrame::setSonidoIgualar, sonido_igualar_checkbox, true);
        applyDefault(GameFrame::setSonidoPasar, sonido_pasar_checkbox, true);
        applyDefault(GameFrame::setSonidoAllin, sonido_allin_checkbox, true);
        applyDefault(GameFrame::setSonidoCiegas, sonido_ciegas_checkbox, true);
        applyDefault(GameFrame::setSonidoUltimaMano, sonido_ultima_mano_checkbox, true);
        applyDefault(GameFrame::setSonidoPausa, sonido_pausa_checkbox, true);
        applyDefault(GameFrame::setSonidoEntrarSala, sonido_entrar_sala_checkbox, true);
        applyDefault(GameFrame::setSonidoTuTurno, sonido_tu_turno_checkbox, true);
        applyDefault(GameFrame::setSonidoAvisoTiempo, sonido_aviso_tiempo_checkbox, true);
        applyDefault(GameFrame::setSonidoFinPartida, sonido_fin_partida_checkbox, true);
        applyDefault(GameFrame::setSonidoInicio, sonido_inicio_checkbox, true);
        applyDefault(GameFrame::setSonidoConexion, sonido_conexion_checkbox, true);
        applyDefault(GameFrame::setSonidoIwtsth, sonido_iwtsth_checkbox, true);
        applyDefault(GameFrame::setSonidoZoom, sonido_zoom_checkbox, true);
        applyDefault(GameFrame::setSonidoVistaCompacta, sonido_vista_compacta_checkbox, true);
        applyDefault(GameFrame::setSonidoScreenshot, sonido_screenshot_checkbox, true);
        applyDefault(GameFrame::setSonidoTapete, sonido_tapete_checkbox, true);
        applyDefault(GameFrame::setSonidoVisor, sonido_visor_checkbox, true);
        applyDefault(GameFrame::setSonidoVolumen, sonido_volumen_checkbox, true);
        applyDefault(GameFrame::setSonidoArranque, sonido_arranque_checkbox, true);
        applyDefault(GameFrame::setSonidoAviso, sonido_aviso_checkbox, true);
        applyDefault(GameFrame::setSonidoError, sonido_error_checkbox, true);
        applyDefault(GameFrame::setSonidoErrorRed, sonido_error_red_checkbox, true);

        // --- Devices and voice: reset to factory too ("include everything") ---
        // Output/input device -> system default (index 0). setSelectedIndex fires the listener
        // (applies + persists; output also restarts the music loops).
        output_list.setSelectedIndex(0);
        capture_list.setSelectedIndex(0);
        // Microphone: on by default if the system has any capture device.
        boolean mic_def = !AudioDeviceManager.getCaptureDevices().isEmpty();
        AudioDeviceManager.setMicEnabled(mic_def);
        mic_checkbox.setSelected(mic_def);
        // Local voice notes active (block=false -> checkbox checked) and play-my-own-notes (true).
        AudioDeviceManager.setBlockVoiceMessages(false);
        notes_local_checkbox.setSelected(true);
        AudioDeviceManager.setPlayOwnVoiceMessages(true);
        play_own_checkbox.setSelected(true);
        // Note retention: 90 days (index 3 of VOICE_NOTE_RETENTION_OPTIONS). setSelectedIndex
        // fires the listener, which persists it.
        retention_combo.setSelectedIndex(3);
        // Local TTS voice active (block=false -> checkbox checked).
        AudioDeviceManager.setBlockTtsLocal(false);
        tts_local_checkbox.setSelected(true);
        // GLOBAL table rules (TTS and voice notes): only if NOT a client (a client gets them
        // from the server, and resetting them here would desync it), same as in revert/isDirty.
        if (!global_rules_locked) {
            GameFrame.setTTSGlobal(true);
            tts_checkbox.setSelected(true);
            GameFrame.setVoiceMessages(true);
            voice_messages_checkbox.setSelected(true);
        }

        // Re-syncs the enabled states (masters end up ON; microphone/notes re-enable their
        // dependent controls).
        refreshVoiceControlsEnabled();
        refreshSoundControlsEnabled();
    }

    // Sets a boolean flag through its setter (applies live + persists) and syncs its
    // checkbox WITHOUT re-firing the listener (setSelected doesn't), avoiding a double apply.
    private static void applyDefault(java.util.function.Consumer<Boolean> setter, JCheckBox cb, boolean def) {
        setter.accept(def);
        cb.setSelected(def);
    }

    // The sound master governs jokes and music. Global rules (TTS and voice notes) can
    // always be pre-set, except as a client in a running game: there the server's values
    // win and they show greyed out.
    private void refreshSoundControlsEnabled() {

        boolean on = sonidos_checkbox.isSelected();

        sonidos_chorra_checkbox.setEnabled(on);
        // Music: the master depends on "Sound"; the four tracks depend on the music master
        // (like the effects).
        musica_master_checkbox.setEnabled(on);
        boolean music_on = on && musica_master_checkbox.isSelected();
        musica_checkbox.setEnabled(music_on);
        musica_sala_checkbox.setEnabled(music_on);
        musica_about_checkbox.setEnabled(music_on);
        musica_stats_checkbox.setEnabled(music_on);

        // Sound effects: the master depends on "Sound"; individual effects depend on the
        // effects master, and "my cards" also depends on "Reveal".
        sonido_efectos_checkbox.setEnabled(on);
        boolean fx_on = on && sonido_efectos_checkbox.isSelected();
        // Category headers ("Actions", "Cards"...): greyed out along with their effects.
        for (JLabel header : fx_type_headers) {
            header.setEnabled(fx_on);
        }
        sonido_barajado_checkbox.setEnabled(fx_on);
        sonido_reparto_checkbox.setEnabled(fx_on);
        sonido_destape_checkbox.setEnabled(fx_on);
        sonido_destape_mis_checkbox.setEnabled(fx_on && sonido_destape_checkbox.isSelected());
        sonido_apostar_checkbox.setEnabled(fx_on);
        sonido_fold_checkbox.setEnabled(fx_on);
        sonido_conteo_checkbox.setEnabled(fx_on);
        sonido_entra_checkbox.setEnabled(fx_on);
        sonido_sale_checkbox.setEnabled(fx_on);
        sonido_interruptor_checkbox.setEnabled(fx_on);
        sonido_caja_checkbox.setEnabled(fx_on);
        sonido_igualar_checkbox.setEnabled(fx_on);
        sonido_pasar_checkbox.setEnabled(fx_on);
        sonido_allin_checkbox.setEnabled(fx_on);
        sonido_ciegas_checkbox.setEnabled(fx_on);
        sonido_ultima_mano_checkbox.setEnabled(fx_on);
        sonido_pausa_checkbox.setEnabled(fx_on);
        sonido_entrar_sala_checkbox.setEnabled(fx_on);
        sonido_tu_turno_checkbox.setEnabled(fx_on);
        sonido_aviso_tiempo_checkbox.setEnabled(fx_on);
        sonido_fin_partida_checkbox.setEnabled(fx_on);
        sonido_inicio_checkbox.setEnabled(fx_on);
        sonido_conexion_checkbox.setEnabled(fx_on);
        sonido_iwtsth_checkbox.setEnabled(fx_on);
        sonido_zoom_checkbox.setEnabled(fx_on);
        sonido_vista_compacta_checkbox.setEnabled(fx_on);
        sonido_screenshot_checkbox.setEnabled(fx_on);
        sonido_tapete_checkbox.setEnabled(fx_on);
        sonido_visor_checkbox.setEnabled(fx_on);
        sonido_volumen_checkbox.setEnabled(fx_on);
        sonido_arranque_checkbox.setEnabled(fx_on);
        sonido_aviso_checkbox.setEnabled(fx_on);
        sonido_error_checkbox.setEnabled(fx_on);
        sonido_error_red_checkbox.setEnabled(fx_on);

        tts_checkbox.setEnabled(!global_rules_locked);
        voice_messages_checkbox.setEnabled(!global_rules_locked);
    }

    // The LOCAL master toggle for voice notes governs all its controls; the capture list
    // additionally needs the microphone enabled. (Retention and purge are independent: you
    // can manage the disk even while notes are disabled.)
    private void refreshVoiceControlsEnabled() {

        boolean local_on = notes_local_checkbox.isSelected();

        mic_checkbox.setEnabled(local_on);
        capture_list.setEnabled(local_on && mic_checkbox.isSelected());
        play_own_checkbox.setEnabled(local_on);
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
    // Creates a sliding toggle for a boolean setting, stashing its translated label so the row
    // builders (iconRow / effectRow) can render "icon label ........ toggle". Fields stay typed
    // JCheckBox (ToggleSwitch IS-A JCheckBox), so the transactional logic is untouched.
    private static JCheckBox tog(String key, boolean value) {
        return tog(key, value, false);
    }

    // Bold-label variant for the section masters (Sound / Music / Effects).
    private static JCheckBox togBold(String key, boolean value) {
        return tog(key, value, true);
    }

    private static JCheckBox tog(String key, boolean value, boolean bold) {
        SettingsUI.ToggleSwitch t = new SettingsUI.ToggleSwitch(value);
        t.putClientProperty("lbl", Translator.translate(key));
        if (bold) {
            t.putClientProperty("bold", Boolean.TRUE);
        }
        return t;
    }

    // Lays a toggle out as "icon label ........ toggle" (control on the card's right edge),
    // reading the toggle's stashed label + bold flag. A plain control (no stashed label) shows
    // just the icon + control.
    private static JComponent iconRow(javax.swing.Icon icon, JComponent control) {
        JLabel label = new JLabel();
        Object lbl = control.getClientProperty("lbl");
        if (lbl != null) {
            label.setText(lbl.toString());
        }
        if (Boolean.TRUE.equals(control.getClientProperty("bold"))) {
            label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD));
        }
        label.setIcon(icon);
        label.setIconTextGap(Math.round(8 * Helpers.DIALOG_ZOOM));
        return SettingsUI.alignedRow(0, label, control);
    }

    // Loads a menu icon (already at the right 24px size) straight from resources.
    private static javax.swing.ImageIcon menuIcon(String resource) {
        return new javax.swing.ImageIcon(AudioSettingsPanel.class.getResource(resource));
    }

    // Loads and smooth-scales a larger resource to a square icon (e.g. the 256px
    // microphone down to menu-icon size). Null on a malformed URL (never happens for
    // bundled resources), which a JLabel renders as no icon.
    private static javax.swing.ImageIcon scaledIcon(String resource, int size) {
        return scaledIcon(resource, size, size);
    }

    private static javax.swing.ImageIcon scaledIcon(String resource, int width, int height) {
        try {
            return Helpers.scaleIcon(AudioSettingsPanel.class.getResource(resource), width, height);
        } catch (java.net.MalformedURLException ex) {
            return null;
        }
    }

    // Scales an icon to FIT inside the given box without distorting it. For non-square
    // artwork: the lights switch (256x120) and the blinds icon (43x32) came out squashed
    // when forced into a 24px square, the former to less than half its width.
    private static javax.swing.ImageIcon fitIcon(String resource, int max_width, int max_height) {

        java.net.URL url = AudioSettingsPanel.class.getResource(resource);

        if (url == null) {
            // Same as scaledIcon with a bad path: no icon, not blowing up the whole panel.
            return null;
        }

        javax.swing.ImageIcon raw = new javax.swing.ImageIcon(url);

        if (raw.getIconWidth() <= 0 || raw.getIconHeight() <= 0) {
            return raw;
        }

        float scale = Math.min((float) max_width / raw.getIconWidth(), (float) max_height / raw.getIconHeight());

        return scaledIcon(resource, Math.max(1, Math.round(raw.getIconWidth() * scale)), Math.max(1, Math.round(raw.getIconHeight() * scale)));
    }

    // Thin rounded box (same style as the groups in the "Appearance" tab): groups the
    // "Sound effects" master with its individual toggles. Transparent so the panel's
    // background shows through; max height = preferred (doesn't stretch in the vertical
    // BoxLayout of "Sound & music").
    private static JPanel groupBox() {
        return groupBox(false);
    }

    // fill_width=true: the box OCCUPIES the column's available width (height capped to
    // preferred) instead of hugging its content — used by the effects box so that widening
    // the dialog turns into spacing between its two columns instead of dead space to its
    // right.
    private static JPanel groupBox(boolean fill_width) {
        JPanel p = new JPanel() {
            @Override
            protected void paintComponent(java.awt.Graphics g) {
                super.paintComponent(g);
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                int garc = Math.round(12 * Helpers.DIALOG_ZOOM);
                g2.setColor(new java.awt.Color(0, 0, 0, 10));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, garc, garc);
                g2.setColor(new java.awt.Color(0, 0, 0, 40));
                g2.setStroke(new java.awt.BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, garc, garc);
                g2.dispose();
            }

            // By default the box hugs its content (doesn't take the column's full width): that
            // way, indented under the master, it reads as a subgroup rather than a stripe. With
            // fill_width it's allowed to grow in width (height capped to preferred) to stretch
            // with the column. Computed live (getPreferredSize), not a value cached with a stale
            // font.
            @Override
            public java.awt.Dimension getMaximumSize() {
                java.awt.Dimension pref = getPreferredSize();
                return fill_width ? new java.awt.Dimension(Short.MAX_VALUE, pref.height) : pref;
            }
        };
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(Math.round(4 * Helpers.DIALOG_ZOOM), Math.round(6 * Helpers.DIALOG_ZOOM), Math.round(6 * Helpers.DIALOG_ZOOM), Math.round(6 * Helpers.DIALOG_ZOOM)));
        p.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return p;
    }

    // Icon cell for each effect row: EXACTLY the size the icons already occupied (all 24px
    // originally), so the checkboxes don't shift a single pixel and the dialog's width
    // doesn't change either. Non-square artwork is fitted inside keeping its aspect ratio, so
    // it comes out shorter than the square icons but never squashed.
    private static final int EFFECT_ICON_CELL_W = 24;
    private static final int EFFECT_ICON_CELL_H = 24;

    // Row for a single effect/track inside its box, indented under the master (deep = extra
    // indent, for the "my cards" suboption hanging off "Reveal"). trailing (or null): the
    // preview button, placed to the RIGHT of the label.
    private static JComponent effectRow(javax.swing.Icon icon, JCheckBox cb, boolean deep, JComponent trailing) {
        // Fixed-width icon cell (artwork centered) so every label starts at the same x even for a
        // wide icon.
        JLabel icon_label = new JLabel(icon);
        java.awt.Dimension icon_cell = new java.awt.Dimension(Math.round(EFFECT_ICON_CELL_W * Helpers.DIALOG_ZOOM), Math.round(EFFECT_ICON_CELL_H * Helpers.DIALOG_ZOOM));
        icon_label.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        icon_label.setPreferredSize(icon_cell);
        icon_label.setMinimumSize(icon_cell);
        icon_label.setMaximumSize(icon_cell);
        icon_label.setAlignmentY(JComponent.CENTER_ALIGNMENT);

        JLabel text = new JLabel();
        Object lbl = cb.getClientProperty("lbl");
        if (lbl != null) {
            text.setText(lbl.toString());
        }
        text.setAlignmentY(JComponent.CENTER_ALIGNMENT);

        // Left part: icon cell + label. Right part: preview button (if any) + the toggle,
        // pinned to the column's right edge by alignedRow's glue.
        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));
        left.add(icon_label);
        left.add(Box.createHorizontalStrut(Math.round(6 * Helpers.DIALOG_ZOOM)));
        left.add(text);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
        if (trailing != null) {
            trailing.setAlignmentY(JComponent.CENTER_ALIGNMENT);
            right.add(trailing);
            right.add(Box.createHorizontalStrut(Math.round(6 * Helpers.DIALOG_ZOOM)));
        }
        cb.setAlignmentY(JComponent.CENTER_ALIGNMENT);
        right.add(cb);

        // deep = the "my cards" suboption hanging off "Reveal" gets a deeper indent.
        JPanel row = SettingsUI.alignedRow(deep ? 34 : 18, left, right);
        // A little air between effect rows (they were touching), without growing the height.
        row.setBorder(BorderFactory.createEmptyBorder(Math.round(4 * Helpers.DIALOG_ZOOM), 0, 0, 0));
        return row;
    }

    // Preview button (play/stop) next to a sound checkbox: a small, borderless JButton with
    // the play triangle (or the stop square while it's playing). ALWAYS enabled even if the
    // checkbox is greyed out — the sound master being off doesn't prevent previewing.
    private static JButton previewButtonBase() {
        JButton b = new JButton(previewGlyph(false));
        b.setMargin(new java.awt.Insets(0, 0, 0, 0));
        b.setBorder(BorderFactory.createEmptyBorder());
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setFocusable(false);
        b.setOpaque(false);
        b.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        b.setToolTipText(Translator.translate("audio.preview_escuchar"));
        b.setAlignmentY(JComponent.CENTER_ALIGNMENT);
        return b;
    }

    // Unified preview button (music or effect, depending on the resource): pressing it
    // plays up to 10s and the button switches to "stop" to cut it short; it returns to "play"
    // when it ends (natural end, 10s timeout, or stop). Only one preview at a time: starting
    // another (here or on any other button) stops the previous one, whose button reverts to
    // "play" on its own via its on_stop callback.
    private static JButton previewButton(String sound) {
        JButton b = previewButtonBase();
        final boolean[] playing = {false};
        b.addActionListener(e -> {
            if (playing[0]) {
                Audio.stopPreview();
            } else {
                playing[0] = true;
                b.setIcon(previewGlyph(true));
                b.setToolTipText(Translator.translate("audio.preview_parar"));
                Audio.previewResource(sound, 10000, () -> {
                    playing[0] = false;
                    b.setIcon(previewGlyph(false));
                    b.setToolTipText(Translator.translate("audio.preview_escuchar"));
                });
            }
        });
        return b;
    }

    // Play/stop preview icon: delegates to the glyph shared in Helpers (also reused by the
    // voice notes viewer), so both dialogs use exactly the same pair of icons.
    private static javax.swing.Icon previewGlyph(boolean stop) {
        return Helpers.playStopGlyph(stop);
    }

    // Vertical (transparent) column that groups effect types inside the box; in a
    // GridLayout it stretches to its cell with its rows pinned to the top (top-aligned).
    private static JPanel effectsColumn() {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        col.setAlignmentY(JComponent.TOP_ALIGNMENT);
        return col;
    }

    // Header for an effects subgroup (Actions, Cards...): BOLD label flush with the
    // column's edge, with the group's checkboxes indented below (effectRow already indents
    // them). The BOLD style survives the host's updateFonts (deriveFont keeps the style). Max
    // height = preferred.
    private JComponent typeHeader(String key) {
        JPanel row = new JPanel() {
            @Override
            public java.awt.Dimension getMaximumSize() {
                return new java.awt.Dimension(Short.MAX_VALUE, getPreferredSize().height);
            }
        };
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        // Top spacing to separate this subgroup from the previous one.
        row.setBorder(BorderFactory.createEmptyBorder(Math.round(7 * Helpers.DIALOG_ZOOM), 0, Math.round(1 * Helpers.DIALOG_ZOOM), 0));
        JLabel lbl = new JLabel(Translator.translate(key));
        lbl.setFont(lbl.getFont().deriveFont(java.awt.Font.BOLD));
        // Registered so it greys out when effects are disabled (like its checkboxes).
        fx_type_headers.add(lbl);
        row.add(lbl);
        row.add(Box.createHorizontalGlue());
        return row;
    }

    // Indents a component to visually hang it off the "SOUND" master checkbox: shifts it
    // right by a fixed gap. Max height = preferred (doesn't stretch in the panel's vertical
    // BoxLayout); the trailing glue absorbs any leftover width on the right when the
    // component hugs its content (the effects box).
    private static JComponent indent(JComponent comp) {
        JPanel wrap = new JPanel() {
            @Override
            public java.awt.Dimension getMaximumSize() {
                return new java.awt.Dimension(Short.MAX_VALUE, getPreferredSize().height);
            }
        };
        wrap.setOpaque(false);
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.X_AXIS));
        wrap.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        wrap.add(Box.createHorizontalStrut(Math.round(22 * Helpers.DIALOG_ZOOM)));
        comp.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        wrap.add(comp);
        wrap.add(Box.createHorizontalGlue());
        return wrap;
    }

    // Like indent() but WITHOUT the trailing glue: the component OCCUPIES the column's
    // width instead of hugging its content (for the effects box, which must stretch with the
    // column and split the leftover width between its two subcolumns instead of leaving dead
    // space to its right). Keeps the 22px indent under the master; max height = preferred
    // (doesn't stretch in the panel's vertical BoxLayout).
    private static JComponent indentFill(JComponent comp) {
        JPanel wrap = new JPanel() {
            @Override
            public java.awt.Dimension getMaximumSize() {
                return new java.awt.Dimension(Short.MAX_VALUE, getPreferredSize().height);
            }
        };
        wrap.setOpaque(false);
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.X_AXIS));
        wrap.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        wrap.add(Box.createHorizontalStrut(Math.round(22 * Helpers.DIALOG_ZOOM)));
        comp.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        wrap.add(comp);
        return wrap;
    }

}
