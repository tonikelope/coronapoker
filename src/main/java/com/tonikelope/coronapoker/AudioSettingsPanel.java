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
 * Contenido de los ajustes de audio como JPanel reutilizable: lo usa la pestaña "Audio"
 * del diálogo unificado de ajustes ({@link SettingsDialog}), accesible desde la rueda
 * dentada en el lanzador, la sala de espera y la partida. Cada cambio se aplica y
 * persiste al instante (el volumen maestro es el mismo valor que mueve el atajo global
 * Shift+Arriba/Abajo).
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
    private final JCheckBox musica_sala_checkbox;
    // Grupo "Efectos de sonido": un maestro (sonido_efectos) que los apaga todos + los
    // efectos individuales. "mis cartas" cuelga de "destapar".
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
    private final JCheckBox tts_checkbox;
    private final JCheckBox voice_messages_checkbox;
    private final boolean global_rules_locked;
    private final JSlider volume_slider;
    private final JLabel volume_value_label;
    private final JList<String> output_list;
    private final JList<String> capture_list;
    private final JCheckBox mic_checkbox;
    private final JCheckBox play_own_checkbox;
    private final JCheckBox notes_local_checkbox;
    private final JCheckBox tts_local_checkbox;
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
    private final boolean snap_musica_sala;
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
        setBorder(BorderFactory.createEmptyBorder(Math.round(10 * Helpers.DIALOG_ZOOM), Math.round(10 * Helpers.DIALOG_ZOOM), Math.round(10 * Helpers.DIALOG_ZOOM), Math.round(10 * Helpers.DIALOG_ZOOM)));

        snap_master_volume = Audio.MASTER_VOLUME;
        snap_sonidos = GameFrame.SONIDOS;
        snap_sonidos_chorra = GameFrame.SONIDOS_CHORRA;
        snap_musica = GameFrame.MUSICA_AMBIENTAL;
        snap_musica_sala = GameFrame.MUSICA_SALA;
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

        musica_sala_checkbox = new JCheckBox(Translator.translate("audio.musica_sala"), GameFrame.MUSICA_SALA);
        musica_sala_checkbox.addActionListener(e -> GameFrame.setMusicaSala(musica_sala_checkbox.isSelected()));

        // --- Efectos de sonido (subpanel bajo "Música ambiente") ---
        // Maestro que apaga TODOS los efectos + toggles individuales (todos ON por defecto).
        // El maestro y "destapar" refrescan el habilitado de sus dependientes. El grupo entero
        // cuelga del master "Sonidos" (se deshabilita si está off, como coña/música).
        sonido_efectos_checkbox = new JCheckBox(Translator.translate("audio.efectos_sonido"), GameFrame.SONIDO_EFECTOS);
        sonido_efectos_checkbox.addActionListener(e -> {
            GameFrame.setSonidoEfectos(sonido_efectos_checkbox.isSelected());
            refreshSoundControlsEnabled();
        });

        sonido_barajado_checkbox = new JCheckBox(Translator.translate("audio.sonido_barajar"), GameFrame.SONIDO_BARAJADO);
        sonido_barajado_checkbox.addActionListener(e -> GameFrame.setSonidoBarajado(sonido_barajado_checkbox.isSelected()));

        sonido_reparto_checkbox = new JCheckBox(Translator.translate("audio.sonido_repartir"), GameFrame.SONIDO_REPARTO);
        sonido_reparto_checkbox.addActionListener(e -> GameFrame.setSonidoReparto(sonido_reparto_checkbox.isSelected()));

        sonido_destape_checkbox = new JCheckBox(Translator.translate("audio.sonido_destapar"), GameFrame.SONIDO_DESTAPE);
        sonido_destape_checkbox.addActionListener(e -> {
            GameFrame.setSonidoDestape(sonido_destape_checkbox.isSelected());
            refreshSoundControlsEnabled();
        });

        sonido_destape_mis_checkbox = new JCheckBox(Translator.translate("audio.sonido_destapar_mis_cartas"), GameFrame.SONIDO_DESTAPE_MIS_CARTAS);
        sonido_destape_mis_checkbox.addActionListener(e -> GameFrame.setSonidoDestapeMisCartas(sonido_destape_mis_checkbox.isSelected()));

        sonido_apostar_checkbox = new JCheckBox(Translator.translate("audio.sonido_apostar"), GameFrame.SONIDO_APOSTAR);
        sonido_apostar_checkbox.addActionListener(e -> GameFrame.setSonidoApostar(sonido_apostar_checkbox.isSelected()));

        sonido_fold_checkbox = new JCheckBox(Translator.translate("audio.sonido_foldear"), GameFrame.SONIDO_FOLD);
        sonido_fold_checkbox.addActionListener(e -> GameFrame.setSonidoFold(sonido_fold_checkbox.isSelected()));

        sonido_conteo_checkbox = new JCheckBox(Translator.translate("audio.sonido_conteo"), GameFrame.SONIDO_CONTEO);
        sonido_conteo_checkbox.addActionListener(e -> GameFrame.setSonidoConteo(sonido_conteo_checkbox.isSelected()));

        sonido_entra_checkbox = new JCheckBox(Translator.translate("audio.sonido_entra"), GameFrame.SONIDO_ENTRA);
        sonido_entra_checkbox.addActionListener(e -> GameFrame.setSonidoEntra(sonido_entra_checkbox.isSelected()));

        sonido_sale_checkbox = new JCheckBox(Translator.translate("audio.sonido_sale"), GameFrame.SONIDO_SALE);
        sonido_sale_checkbox.addActionListener(e -> GameFrame.setSonidoSale(sonido_sale_checkbox.isSelected()));

        sonido_interruptor_checkbox = new JCheckBox(Translator.translate("audio.sonido_interruptor"), GameFrame.SONIDO_INTERRUPTOR);
        sonido_interruptor_checkbox.addActionListener(e -> GameFrame.setSonidoInterruptor(sonido_interruptor_checkbox.isSelected()));

        sonido_caja_checkbox = new JCheckBox(Translator.translate("audio.sonido_caja"), GameFrame.SONIDO_CAJA);
        sonido_caja_checkbox.addActionListener(e -> GameFrame.setSonidoCaja(sonido_caja_checkbox.isSelected()));

        sonido_igualar_checkbox = new JCheckBox(Translator.translate("audio.sonido_igualar"), GameFrame.SONIDO_IGUALAR);
        sonido_igualar_checkbox.addActionListener(e -> GameFrame.setSonidoIgualar(sonido_igualar_checkbox.isSelected()));

        sonido_pasar_checkbox = new JCheckBox(Translator.translate("audio.sonido_pasar"), GameFrame.SONIDO_PASAR);
        sonido_pasar_checkbox.addActionListener(e -> GameFrame.setSonidoPasar(sonido_pasar_checkbox.isSelected()));

        sonido_allin_checkbox = new JCheckBox(Translator.translate("audio.sonido_allin"), GameFrame.SONIDO_ALLIN);
        sonido_allin_checkbox.addActionListener(e -> GameFrame.setSonidoAllin(sonido_allin_checkbox.isSelected()));

        sonido_ciegas_checkbox = new JCheckBox(Translator.translate("audio.sonido_ciegas"), GameFrame.SONIDO_CIEGAS);
        sonido_ciegas_checkbox.addActionListener(e -> GameFrame.setSonidoCiegas(sonido_ciegas_checkbox.isSelected()));

        sonido_ultima_mano_checkbox = new JCheckBox(Translator.translate("audio.sonido_ultima_mano"), GameFrame.SONIDO_ULTIMA_MANO);
        sonido_ultima_mano_checkbox.addActionListener(e -> GameFrame.setSonidoUltimaMano(sonido_ultima_mano_checkbox.isSelected()));

        sonido_pausa_checkbox = new JCheckBox(Translator.translate("audio.sonido_pausa"), GameFrame.SONIDO_PAUSA);
        sonido_pausa_checkbox.addActionListener(e -> GameFrame.setSonidoPausa(sonido_pausa_checkbox.isSelected()));

        sonido_entrar_sala_checkbox = new JCheckBox(Translator.translate("audio.sonido_entrar_sala"), GameFrame.SONIDO_ENTRAR_SALA);
        sonido_entrar_sala_checkbox.addActionListener(e -> GameFrame.setSonidoEntrarSala(sonido_entrar_sala_checkbox.isSelected()));

        sonido_tu_turno_checkbox = new JCheckBox(Translator.translate("audio.sonido_tu_turno"), GameFrame.SONIDO_TU_TURNO);
        sonido_tu_turno_checkbox.addActionListener(e -> GameFrame.setSonidoTuTurno(sonido_tu_turno_checkbox.isSelected()));

        sonido_aviso_tiempo_checkbox = new JCheckBox(Translator.translate("audio.sonido_aviso_tiempo"), GameFrame.SONIDO_AVISO_TIEMPO);
        sonido_aviso_tiempo_checkbox.addActionListener(e -> GameFrame.setSonidoAvisoTiempo(sonido_aviso_tiempo_checkbox.isSelected()));

        sonido_fin_partida_checkbox = new JCheckBox(Translator.translate("audio.sonido_fin_partida"), GameFrame.SONIDO_FIN_PARTIDA);
        sonido_fin_partida_checkbox.addActionListener(e -> GameFrame.setSonidoFinPartida(sonido_fin_partida_checkbox.isSelected()));

        tts_checkbox = new JCheckBox(Translator.translate("menu.tts"), GameFrame.TTS_SERVER);
        tts_checkbox.addActionListener(e -> GameFrame.setTTSGlobal(tts_checkbox.isSelected()));

        voice_messages_checkbox = new JCheckBox(Translator.translate("menu.notas_de_voz"), GameFrame.VOICE_MESSAGES);
        voice_messages_checkbox.addActionListener(e -> GameFrame.setVoiceMessages(voice_messages_checkbox.isSelected()));

        sound_music_panel = new JPanel();
        sound_music_panel.setLayout(new BoxLayout(sound_music_panel, BoxLayout.Y_AXIS));
        sound_music_panel.setBorder(BorderFactory.createTitledBorder(Translator.translate("audio.sonido_musica")));
        // Maestro "SONIDO" al borde; el resto sangrado para que se lea que dependen de él.
        // Aire entre las filas maestras para que no queden apelmazadas (hay margen de sobra en
        // esta columna, la más corta, sin subir el alto del diálogo).
        sound_music_panel.add(iconRow(menuIcon("/images/menu/sound.png"), sonidos_checkbox));
        sound_music_panel.add(Box.createVerticalStrut(Math.round(6 * Helpers.DIALOG_ZOOM)));
        sound_music_panel.add(indent(iconRow(menuIcon("/images/menu/joke.png"), sonidos_chorra_checkbox)));
        sound_music_panel.add(Box.createVerticalStrut(Math.round(6 * Helpers.DIALOG_ZOOM)));
        sound_music_panel.add(indent(iconRow(menuIcon("/images/menu/music.png"), musica_checkbox)));
        sound_music_panel.add(Box.createVerticalStrut(Math.round(6 * Helpers.DIALOG_ZOOM)));
        sound_music_panel.add(indent(iconRow(menuIcon("/images/menu/music.png"), musica_sala_checkbox)));

        // Un poco más de aire para que el recuadro de efectos se lea como subgrupo aparte.
        sound_music_panel.add(Box.createVerticalStrut(Math.round(8 * Helpers.DIALOG_ZOOM)));

        // Subpanel "Efectos de sonido" (recuadro fino): maestro arriba y, debajo, los efectos
        // individuales AGRUPADOS POR TIPO (cabecera en negrita + sus casillas sangradas), en DOS
        // columnas para que la lista no dispare el alto. "Mis cartas" cuelga (más sangría) de
        // "Destapar". Cada casilla se nombra por PARA QUÉ se usa (no por el sonido).
        JPanel efectos_group = groupBox();
        efectos_group.add(iconRow(menuIcon("/images/menu/fx.png"), sonido_efectos_checkbox));

        // Columna izquierda de tipos: lo que ocurre DENTRO de la mano (acciones + cartas).
        JPanel fx_col_a = effectsColumn();
        fx_col_a.add(typeHeader("audio.grupo_acciones"));
        fx_col_a.add(effectRow(menuIcon("/images/menu/chips.png"), sonido_apostar_checkbox, false));
        fx_col_a.add(effectRow(scaledIcon("/images/action/bet.png", 24), sonido_igualar_checkbox, false));
        fx_col_a.add(effectRow(menuIcon("/images/menu/confirmation.png"), sonido_pasar_checkbox, false));
        fx_col_a.add(effectRow(scaledIcon("/images/action/up.png", 24), sonido_allin_checkbox, false));
        fx_col_a.add(effectRow(scaledIcon("/images/action/down.png", 24), sonido_fold_checkbox, false));
        fx_col_a.add(typeHeader("audio.grupo_cartas"));
        fx_col_a.add(effectRow(menuIcon("/images/menu/baraja.png"), sonido_barajado_checkbox, false));
        fx_col_a.add(effectRow(menuIcon("/images/menu/dealer.png"), sonido_reparto_checkbox, false));
        fx_col_a.add(effectRow(menuIcon("/images/menu/flip.png"), sonido_destape_checkbox, false));
        fx_col_a.add(effectRow(menuIcon("/images/menu/baraja.png"), sonido_destape_mis_checkbox, true));
        // Fija las filas arriba: si esta columna es la más corta, el glue absorbe el hueco abajo
        // (si no, BoxLayout podría centrarlas y desalinear las cabeceras respecto a la otra).
        fx_col_a.add(Box.createVerticalGlue());

        // Columna derecha de tipos: sala, estado de la partida, turno/tiempo y otros.
        JPanel fx_col_b = effectsColumn();
        fx_col_b.add(typeHeader("audio.grupo_sala"));
        fx_col_b.add(effectRow(scaledIcon("/images/start.png", 24), sonido_entra_checkbox, false));
        fx_col_b.add(effectRow(menuIcon("/images/menu/bell.png"), sonido_entrar_sala_checkbox, false));
        fx_col_b.add(effectRow(scaledIcon("/images/exit.png", 24), sonido_sale_checkbox, false));
        fx_col_b.add(typeHeader("audio.grupo_partida"));
        fx_col_b.add(effectRow(scaledIcon("/images/ciegas.png", 24), sonido_ciegas_checkbox, false));
        fx_col_b.add(effectRow(menuIcon("/images/menu/last_hand.png"), sonido_ultima_mano_checkbox, false));
        fx_col_b.add(effectRow(menuIcon("/images/menu/meter.png"), sonido_conteo_checkbox, false));
        fx_col_b.add(effectRow(scaledIcon("/images/pause.png", 24), sonido_pausa_checkbox, false));
        fx_col_b.add(effectRow(scaledIcon("/images/action/skull.png", 24), sonido_fin_partida_checkbox, false));
        fx_col_b.add(typeHeader("audio.grupo_turno_tiempo"));
        fx_col_b.add(effectRow(scaledIcon("/images/action/vamos.png", 24), sonido_tu_turno_checkbox, false));
        fx_col_b.add(effectRow(scaledIcon("/images/action/timeout.png", 24), sonido_aviso_tiempo_checkbox, false));
        fx_col_b.add(typeHeader("audio.grupo_otros"));
        fx_col_b.add(effectRow(scaledIcon("/images/lights_on.png", 24), sonido_interruptor_checkbox, false));
        fx_col_b.add(effectRow(menuIcon("/images/menu/rebuy.png"), sonido_caja_checkbox, false));
        fx_col_b.add(Box.createVerticalGlue());

        // GridBagLayout (no GridLayout): cada subcolumna toma su ANCHO PREFERIDO. GridLayout las
        // forzaba al MISMO ancho, dejando un hueco muerto en la más estrecha y ensanchando el
        // recuadro sin motivo. Ambas a la MISMA ALTURA (fill=BOTH, weighty=1) para alinear sus
        // cabeceras; getMaximumSize=preferido evita que el BoxLayout del recuadro las estire y
        // las centre. La izquierda (más corta) deja hueco abajo, absorbido por su glue.
        JPanel fx_cols = new JPanel(new java.awt.GridBagLayout()) {
            @Override
            public java.awt.Dimension getMaximumSize() {
                return getPreferredSize();
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
        fx_gbc.insets = new java.awt.Insets(0, 0, 0, Math.round(16 * Helpers.DIALOG_ZOOM));
        fx_cols.add(fx_col_a, fx_gbc);
        fx_gbc.gridx = 1;
        fx_gbc.insets = new java.awt.Insets(0, 0, 0, 0);
        fx_cols.add(fx_col_b, fx_gbc);
        efectos_group.add(fx_cols);
        sound_music_panel.add(indent(efectos_group));

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
        // Interruptor maestro LOCAL en lógica POSITIVA: ON = notas de voz locales activas
        // (micrófono, tecla de grabar y reproducción). Bajo el capó sigue viviendo como
        // "bloqueo" (AudioDeviceManager.setBlockVoiceMessages), así el resto del código
        // (recepción de notas, sala) no cambia; aquí solo se invierte la vista.
        notes_local_checkbox = new JCheckBox(Translator.translate("audio.notas_de_voz_local"), !AudioDeviceManager.isBlockVoiceMessages());

        notes_local_checkbox.addActionListener(e -> {
            AudioDeviceManager.setBlockVoiceMessages(!notes_local_checkbox.isSelected());

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

        // Un poco más ancho: prototipo más largo que cualquier ítem real ("Siempre",
        // "90 días") para que el combo (en BorderLayout.EAST) no quede justo. Va en la
        // fuente del combo, así que la anchura escala con el updateFonts del host.
        retention_combo.setPrototypeDisplayValue(Translator.translate("audio.retencion_dias", 999) + "  ");

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
        play_own_checkbox.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        retention_panel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        purge_panel.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        // Contenido compacto y pegado arriba (sin glue): va en la columna derecha, bajo el
        // "Dispositivo de entrada" (micrófono), con el que forma el bloque de VOZ/entrada. Se
        // topa a su alto preferido en applyFontsAndSizing para no estirarse. Arriba la regla
        // GLOBAL de la timba (server); debajo el interruptor maestro LOCAL que gobierna el resto
        // de controles de notas de voz (ambos en positivo, ambos con el icono de micrófono).
        notes_panel.add(iconRow(scaledIcon("/images/microphone_black.png", 24), voice_messages_checkbox));
        notes_panel.add(Box.createVerticalStrut(Math.round(6 * Helpers.DIALOG_ZOOM)));
        notes_panel.add(iconRow(scaledIcon("/images/microphone_black.png", 24), notes_local_checkbox));
        notes_panel.add(Box.createVerticalStrut(Math.round(8 * Helpers.DIALOG_ZOOM)));
        notes_panel.add(voice_key_panel);
        notes_panel.add(play_own_checkbox);
        notes_panel.add(Box.createVerticalStrut(Math.round(5 * Helpers.DIALOG_ZOOM)));
        notes_panel.add(retention_panel);
        notes_panel.add(purge_panel);

        // --- Voz (TTS): arriba la regla GLOBAL de la timba (server); debajo el
        // interruptor LOCAL. Ambos en lógica POSITIVA: el local sigue viviendo bajo el
        // capó como "bloqueo" (setBlockTtsLocal), así GameFrame no cambia; aquí se invierte.
        tts_local_checkbox = new JCheckBox(Translator.translate("audio.tts_local"), !AudioDeviceManager.isBlockTtsLocal());
        tts_local_checkbox.addActionListener(e -> AudioDeviceManager.setBlockTtsLocal(!tts_local_checkbox.isSelected()));

        tts_panel = new JPanel();
        tts_panel.setLayout(new BoxLayout(tts_panel, BoxLayout.Y_AXIS));
        tts_panel.setBorder(BorderFactory.createTitledBorder(Translator.translate("audio.voz_tts")));
        // Compacto y pegado arriba (sin glue): es el panel de menor tamaño, va bajo "Sonido y
        // música" en la columna izquierda y se topa a su alto preferido en applyFontsAndSizing.
        // Arriba la regla GLOBAL de la timba (server); debajo la LOCAL, ambas con icono de voz.
        tts_panel.add(iconRow(menuIcon("/images/menu/voice.png"), tts_checkbox));
        tts_panel.add(Box.createVerticalStrut(Math.round(6 * Helpers.DIALOG_ZOOM)));
        tts_panel.add(iconRow(menuIcon("/images/menu/voice.png"), tts_local_checkbox));

        // Nota (solo CLIENTE en partida): las reglas GLOBALES las manda el servidor y quedan en
        // gris (los ajustes locales no las tocan). Ahora esos checkboxes GLOBALES viven en
        // columnas distintas (TTS a la izquierda, Notas de voz a la derecha), así que la nota va
        // al pie del diálogo (SOUTH), bajo ambas columnas. Invisible si no eres cliente. Ancho
        // fijo en el HTML para que ajuste a varias líneas y no se corte.
        JLabel global_note = new JLabel("<html><div style='width:240px'>" + Translator.translate("audio.ajustes_locales_ignorados") + "</div></html>");
        global_note.setForeground(java.awt.Color.GRAY);
        global_note.setVisible(global_rules_locked);

        refreshSoundControlsEnabled();

        // --- Dos columnas EQUILIBRADAS para minimizar la ALTURA del diálogo (cada panel mide
        // distinto; se reparten para que ninguna columna sea mucho más alta que la otra):
        // IZQUIERDA "Sonido y música" (el panel más alto) + "Voz (TTS)" (el más bajo);
        // DERECHA "Dispositivo de salida" + "Dispositivo de entrada" + "Notas de voz" (que va
        // pegado al micrófono, con el que forma el bloque de VOZ/entrada). ---
        sound_music_panel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        output_panel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        mic_panel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        notes_panel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        tts_panel.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        JPanel left_col = new JPanel();
        left_col.setLayout(new BoxLayout(left_col, BoxLayout.Y_AXIS));
        left_col.setAlignmentY(JComponent.TOP_ALIGNMENT);
        left_col.add(sound_music_panel);
        left_col.add(Box.createVerticalStrut(Math.round(8 * Helpers.DIALOG_ZOOM)));
        left_col.add(tts_panel);
        // Ambos paneles de la izquierda son compactos (topados a su alto preferido); esta cola
        // absorbe el alto sobrante si la columna derecha resulta la más alta, dejándolos pegados
        // arriba en vez de estirarlos.
        left_col.add(Box.createVerticalGlue());

        JPanel right_col = new JPanel();
        right_col.setLayout(new BoxLayout(right_col, BoxLayout.Y_AXIS));
        right_col.setAlignmentY(JComponent.TOP_ALIGNMENT);
        right_col.add(output_panel);
        right_col.add(Box.createVerticalStrut(Math.round(8 * Helpers.DIALOG_ZOOM)));
        right_col.add(mic_panel);
        right_col.add(Box.createVerticalStrut(Math.round(8 * Helpers.DIALOG_ZOOM)));
        right_col.add(notes_panel);

        // GridBagLayout (no GridLayout): cada columna toma su ANCHO PREFERIDO —ya NO se fuerza a
        // que ambas midan lo mismo, que ensanchaba el diálogo estirando la derecha (listas de
        // dispositivos) en balde— pero AMBAS se estiran a la MISMA ALTURA (fill=BOTH, weighty=1),
        // así sus bordes inferiores siguen alineados. Al empaquetar, el ancho es exactamente el
        // necesario; weightx=1 reparte por igual cualquier sobre-ancho (no deja huecos centrados).
        JPanel center_panel = new JPanel(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints center_gbc = new java.awt.GridBagConstraints();
        center_gbc.gridy = 0;
        center_gbc.fill = java.awt.GridBagConstraints.BOTH;
        center_gbc.weighty = 1.0;
        // Columna izquierda (sonido/música + efectos) a su ancho preferido: weightx=0, así NUNCA
        // recibe sobre-ancho (era lo que dejaba hueco muerto a la derecha de los efectos). Todo
        // el sobre-ancho residual (si alguna otra pestaña fuerza el diálogo más ancho) va a la
        // derecha (weightx=1), donde las listas de dispositivos lo aprovechan sin verse mal.
        center_gbc.weightx = 0.0;
        center_gbc.gridx = 0;
        center_gbc.insets = new java.awt.Insets(0, 0, 0, Math.round(12 * Helpers.DIALOG_ZOOM));
        center_panel.add(left_col, center_gbc);
        center_gbc.gridx = 1;
        center_gbc.weightx = 1.0;
        center_gbc.insets = new java.awt.Insets(0, 0, 0, 0);
        center_panel.add(right_col, center_gbc);

        add(volume_panel, BorderLayout.NORTH);
        add(center_panel, BorderLayout.CENTER);
        // Nota de reglas globales al pie, bajo ambas columnas: afecta a los checkboxes GLOBALES
        // de las dos (Voz TTS a la izquierda, Notas de voz a la derecha). Solo se AÑADE si eres
        // cliente en partida; a diferencia de BoxLayout, BorderLayout.SOUTH reserva el alto del
        // componente aunque esté invisible, así que fuera de partida no lo colgamos (para no
        // dejar una franja vacía que se comería el alto ganado).
        if (global_rules_locked) {
            add(global_note, BorderLayout.SOUTH);
        }

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

        // En el BoxLayout vertical, la fila de tecla y la de retención deben
        // conservar su alto natural en vez de estirarse hasta llenar el hueco.
        voice_key_panel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, voice_key_panel.getPreferredSize().height));
        retention_panel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, retention_panel.getPreferredSize().height));
        purge_panel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, purge_panel.getPreferredSize().height));

        // "Sonido y música", "Voz (TTS)" y "Notas de voz" solo llevan checkboxes y filas de
        // alto natural: se estiran al ancho de su columna PERO con el alto topado al preferido
        // para NO estirarse en vertical (quedan compactos y pegados arriba; el glue del pie de
        // la columna izquierda y las listas de dispositivos —sin tope— absorben el alto sobrante).
        // DESPUÉS de updateFonts, para que el alto preferido ya refleje la fuente escalada y no
        // se corte por abajo. Las listas de "Dispositivo de salida/entrada" SÍ estiran en vertical
        // para llenar su columna.
        sound_music_panel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, sound_music_panel.getPreferredSize().height));
        tts_panel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, tts_panel.getPreferredSize().height));
        notes_panel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, notes_panel.getPreferredSize().height));
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
                || GameFrame.MUSICA_SALA != snap_musica_sala
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
        if (GameFrame.MUSICA_SALA != snap_musica_sala) {
            GameFrame.setMusicaSala(snap_musica_sala);
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
        musica_sala_checkbox.setEnabled(on);

        // Efectos de sonido: el maestro cuelga de "Sonidos"; los efectos individuales del
        // maestro, y "mis cartas" además de "Destapar".
        sonido_efectos_checkbox.setEnabled(on);
        boolean fx_on = on && sonido_efectos_checkbox.isSelected();
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

        tts_checkbox.setEnabled(!global_rules_locked);
        voice_messages_checkbox.setEnabled(!global_rules_locked);
    }

    // El interruptor maestro LOCAL de notas de voz gobierna todos sus controles; la lista
    // de captura además necesita el micrófono activado. (La retención y el vaciado son
    // independientes: puedes gestionar el disco aunque las notas estén desactivadas.)
    private void refreshVoiceControlsEnabled() {

        boolean local_on = notes_local_checkbox.isSelected();

        mic_checkbox.setEnabled(local_on);
        capture_list.setEnabled(local_on && mic_checkbox.isSelected());
        play_own_checkbox.setEnabled(local_on);
        voice_key_button.setEnabled(local_on);

        if (!local_on) {
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

        // Alto MÁXIMO = preferido: en un BoxLayout vertical la fila NUNCA se estira para
        // rellenar el hueco. El glue horizontal interno (empuja el control a la izquierda)
        // tiene alto máximo ilimitado y, sin este tope, arrastraría a la fila entera y
        // separaría los controles al repartirse el hueco sobrante (era el caso de los dos
        // checkboxes de "Ajustes globales", que quedaban muy espaciados).
        JPanel row = new JPanel() {
            @Override
            public java.awt.Dimension getMaximumSize() {
                return new java.awt.Dimension(Short.MAX_VALUE, getPreferredSize().height);
            }
        };
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        JLabel icon_label = new JLabel(icon);
        icon_label.setAlignmentY(JComponent.CENTER_ALIGNMENT);
        control.setAlignmentY(JComponent.CENTER_ALIGNMENT);

        row.add(icon_label);
        row.add(Box.createHorizontalStrut(Math.round(6 * Helpers.DIALOG_ZOOM)));
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

    // Recuadro fino redondeado (mismo estilo que los grupos de la pestaña "Apariencia"):
    // agrupa el maestro "Efectos de sonido" con sus toggles individuales. Transparente para
    // que el fondo del panel se vea a través; alto máximo = preferido (no se estira en el
    // BoxLayout vertical de "Sonido y música").
    private static JPanel groupBox() {
        JPanel p = new JPanel() {
            @Override
            protected void paintComponent(java.awt.Graphics g) {
                super.paintComponent(g);
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new java.awt.Color(0, 0, 0, 150));
                g2.setStroke(new java.awt.BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
            }

            // Ciñe el recuadro a su contenido (no ocupa todo el ancho de la columna): así,
            // sangrado bajo el maestro, se lee como un subgrupo y no como una franja. En vivo
            // (getPreferredSize), no un valor cacheado con la fuente vieja.
            @Override
            public java.awt.Dimension getMaximumSize() {
                return getPreferredSize();
            }
        };
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(Math.round(4 * Helpers.DIALOG_ZOOM), Math.round(6 * Helpers.DIALOG_ZOOM), Math.round(6 * Helpers.DIALOG_ZOOM), Math.round(6 * Helpers.DIALOG_ZOOM)));
        p.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return p;
    }

    // Fila de un efecto individual dentro del recuadro "Efectos de sonido", sangrada bajo el
    // maestro (deep = sangría mayor, para la subopción "mis cartas" que cuelga de "Destapar").
    private static JComponent effectRow(javax.swing.Icon icon, JCheckBox cb, boolean deep) {
        JPanel row = new JPanel() {
            @Override
            public java.awt.Dimension getMaximumSize() {
                return new java.awt.Dimension(Short.MAX_VALUE, getPreferredSize().height);
            }
        };
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        // Margen superior: da aire entre filas de efectos (iban pegadas) y bajo el maestro, sin
        // subir el alto del diálogo (esta columna es la más corta y le sobra vertical).
        row.setBorder(BorderFactory.createEmptyBorder(Math.round(4 * Helpers.DIALOG_ZOOM), 0, 0, 0));
        row.add(Box.createHorizontalStrut(Math.round((deep ? 34 : 18) * Helpers.DIALOG_ZOOM)));
        JLabel icon_label = new JLabel(icon);
        icon_label.setAlignmentY(JComponent.CENTER_ALIGNMENT);
        cb.setAlignmentY(JComponent.CENTER_ALIGNMENT);
        row.add(icon_label);
        row.add(Box.createHorizontalStrut(Math.round(6 * Helpers.DIALOG_ZOOM)));
        row.add(cb);
        row.add(Box.createHorizontalGlue());
        return row;
    }

    // Columna vertical (transparente) para agrupar tipos de efectos dentro del recuadro; en un
    // GridLayout se estira a su celda y sus filas quedan pegadas arriba (top-aligned).
    private static JPanel effectsColumn() {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        col.setAlignmentY(JComponent.TOP_ALIGNMENT);
        return col;
    }

    // Cabecera de un subgrupo de efectos (Acciones, Cartas...): etiqueta en NEGRITA al borde de
    // la columna, con las casillas del grupo sangradas debajo (effectRow ya las sangra). El BOLD
    // sobrevive al updateFonts del host (deriveFont conserva el estilo). Alto máximo = preferido.
    private static JComponent typeHeader(String key) {
        JPanel row = new JPanel() {
            @Override
            public java.awt.Dimension getMaximumSize() {
                return new java.awt.Dimension(Short.MAX_VALUE, getPreferredSize().height);
            }
        };
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        // Aire arriba para separar el subgrupo del anterior.
        row.setBorder(BorderFactory.createEmptyBorder(Math.round(7 * Helpers.DIALOG_ZOOM), 0, Math.round(1 * Helpers.DIALOG_ZOOM), 0));
        JLabel lbl = new JLabel(Translator.translate(key));
        lbl.setFont(lbl.getFont().deriveFont(java.awt.Font.BOLD));
        row.add(lbl);
        row.add(Box.createHorizontalGlue());
        return row;
    }

    // Sangra un componente para colgarlo visualmente del checkbox maestro "SONIDO": lo
    // desplaza a la derecha con un hueco fijo. Alto máximo = preferido (no se estira en el
    // BoxLayout Y del panel); el glue final absorbe el ancho sobrante a la derecha cuando el
    // componente ciñe su contenido (el recuadro de efectos).
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

}
