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
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import javax.sound.sampled.Mixer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.ListSelectionModel;
import javax.swing.WindowConstants;
import javax.swing.border.TitledBorder;

/**
 * Audio settings, opened by right-clicking any of the speaker icons. Every
 * change applies and persists immediately (the master volume is the same
 * value the global Shift+Up/Down shortcut drives).
 *
 * @author tonikelope
 */
public class AudioSettingsDialog extends javax.swing.JDialog {

    private static volatile AudioSettingsDialog INSTANCE = null;

    private final JSlider volume_slider;
    private final JLabel volume_value_label;
    private final JList<String> output_list;
    private final JList<String> capture_list;
    private final JCheckBox mic_checkbox;
    private final JCheckBox play_own_checkbox;
    private final JCheckBox block_notes_checkbox;
    private final JButton voice_key_button;
    private final List<Mixer.Info> output_devices;
    private final List<Mixer.Info> capture_devices;
    private volatile boolean loading = true;
    private volatile KeyEventDispatcher key_capture_dispatcher = null;

    // Right-click menu shared by every speaker icon
    public static void showSpeakerPopup(java.awt.Component invoker, java.awt.Frame parent, int x, int y) {

        JPopupMenu popup = new JPopupMenu();

        JMenuItem settings_item = new JMenuItem(Translator.translate("audio.ajustes"));

        settings_item.addActionListener(e -> open(parent));

        popup.add(settings_item);

        Helpers.updateFonts(popup, Helpers.GUI_FONT, null);

        popup.show(invoker, x, y);
    }

    public static void open(java.awt.Frame parent) {

        Helpers.GUIRun(() -> {
            AudioSettingsDialog dialog = new AudioSettingsDialog(parent, true);
            dialog.setLocationRelativeTo(parent);
            dialog.setVisible(true);
        });
    }

    // Keeps the slider in sync when the volume changes via the global shortcut
    // while this dialog is open.
    public static void refreshVolume() {

        AudioSettingsDialog dialog = INSTANCE;

        if (dialog != null) {
            Helpers.GUIRun(() -> {
                int val = Math.round(Audio.MASTER_VOLUME * 100);

                if (dialog.volume_slider.getValue() != val) {
                    dialog.volume_slider.setValue(val);
                }
            });
        }
    }

    public AudioSettingsDialog(java.awt.Frame parent, boolean modal) {
        super(parent, modal);

        setTitle(Translator.translate("audio.ajustes"));

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

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

        JPanel volume_panel = new JPanel(new BorderLayout(10, 0));
        volume_panel.setBorder(BorderFactory.createTitledBorder(Translator.translate("audio.volumen_general")));
        volume_panel.add(volume_slider, BorderLayout.CENTER);
        volume_panel.add(volume_value_label, BorderLayout.EAST);

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

        JPanel output_panel = new JPanel(new BorderLayout());
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

        JPanel voice_key_panel = new JPanel(new BorderLayout(10, 0));
        voice_key_panel.add(new JLabel(Translator.translate("audio.tecla_nota_voz")), BorderLayout.CENTER);
        voice_key_panel.add(voice_key_button, BorderLayout.EAST);

        JPanel mic_panel = new JPanel(new BorderLayout(0, 5));
        mic_panel.setBorder(BorderFactory.createTitledBorder(Translator.translate("audio.dispositivo_entrada")));
        mic_panel.add(mic_checkbox, BorderLayout.NORTH);
        mic_panel.add(new JScrollPane(capture_list), BorderLayout.CENTER);

        // --- Voice note options ---
        block_notes_checkbox = new JCheckBox(Translator.translate("audio.bloquear_notas"), AudioDeviceManager.isBlockVoiceMessages());

        block_notes_checkbox.addActionListener(e -> {
            AudioDeviceManager.setBlockVoiceMessages(block_notes_checkbox.isSelected());

            refreshVoiceControlsEnabled();
        });

        play_own_checkbox = new JCheckBox(Translator.translate("audio.reproducir_mis_notas"), AudioDeviceManager.isPlayOwnVoiceMessages());

        play_own_checkbox.addActionListener(e -> AudioDeviceManager.setPlayOwnVoiceMessages(play_own_checkbox.isSelected()));

        refreshVoiceControlsEnabled();

        JPanel notes_panel = new JPanel();
        notes_panel.setLayout(new BoxLayout(notes_panel, BoxLayout.Y_AXIS));
        notes_panel.setBorder(BorderFactory.createTitledBorder(Translator.translate("audio.notas_de_voz")));

        voice_key_panel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        block_notes_checkbox.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        play_own_checkbox.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        notes_panel.add(voice_key_panel);
        notes_panel.add(Box.createVerticalStrut(5));
        notes_panel.add(block_notes_checkbox);
        notes_panel.add(play_own_checkbox);

        // --- Vertical stack: output, input, voice notes ---
        JPanel center_panel = new JPanel();
        center_panel.setLayout(new BoxLayout(center_panel, BoxLayout.Y_AXIS));

        output_panel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        mic_panel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        notes_panel.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        center_panel.add(output_panel);
        center_panel.add(Box.createVerticalStrut(10));
        center_panel.add(mic_panel);
        center_panel.add(Box.createVerticalStrut(10));
        center_panel.add(notes_panel);

        // --- Buttons ---
        JButton ok_button = new JButton(Translator.translate("ui.aceptar"));

        ok_button.addActionListener(e -> dispose());

        JPanel button_panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        button_panel.add(ok_button);

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        content.add(volume_panel, BorderLayout.NORTH);
        content.add(center_panel, BorderLayout.CENTER);
        content.add(button_panel, BorderLayout.SOUTH);

        setContentPane(content);

        getRootPane().setDefaultButton(ok_button);

        addWindowListener(new WindowAdapter() {

            @Override
            public void windowActivated(WindowEvent e) {
                if (isModal()) {
                    Init.CURRENT_MODAL_DIALOG.add(AudioSettingsDialog.this);
                }
            }

            @Override
            public void windowDeactivated(WindowEvent e) {
                if (isModal()) {
                    try {
                        Init.CURRENT_MODAL_DIALOG.removeLast();
                    } catch (Exception ex) {
                    }
                }
            }

            @Override
            public void windowClosed(WindowEvent e) {

                INSTANCE = null;

                // Closing mid-capture must not leak the dispatcher
                stopVoiceKeyCapture();

                // The master volume persists across sessions
                Helpers.PROPERTIES.setProperty("master_volume", String.valueOf(Audio.MASTER_VOLUME));

                Helpers.savePropertiesFile();
            }
        });

        Helpers.updateFonts(this, Helpers.GUI_FONT, 1.2f);

        // TitledBorder is not a component: updateFonts cannot reach the frame
        // titles, so they are matched to the scaled GUI font by hand.
        ((TitledBorder) volume_panel.getBorder()).setTitleFont(volume_value_label.getFont());
        ((TitledBorder) output_panel.getBorder()).setTitleFont(volume_value_label.getFont());
        ((TitledBorder) mic_panel.getBorder()).setTitleFont(volume_value_label.getFont());
        ((TitledBorder) notes_panel.getBorder()).setTitleFont(volume_value_label.getFont());

        // In the vertical BoxLayout the key row must keep its natural height
        // instead of stretching to fill the leftover space.
        voice_key_panel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, voice_key_panel.getPreferredSize().height));

        pack();

        loading = false;

        INSTANCE = this;
    }

    // Self-block governs every voice note control; the capture list also
    // needs the mic itself enabled
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

}
