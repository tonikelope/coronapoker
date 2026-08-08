/*
 * Copyright (C) 2020 tonikelope
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
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;

/**
 * Viewer (gallery) for the voice notes saved under {@link Init#VOICE_DIR}. Each row
 * shows the player nick, timestamp and duration, with a play/stop preview button that
 * reuses the SAME playback system as the Audio tab ({@link Audio#previewResource}), plus
 * a delete button with confirmation.
 *
 * Metadata is derived from the file itself: the name follows the pattern
 * {@code <millisEpoch>_<sanitizedNick>_<random8>.wav} (see
 * {@code WaitingRoomFrame.recibirNotaVoz}), which yields the timestamp and nick; the
 * duration is read from the WAV header.
 *
 * @author tonikelope
 */
public class VoiceNotesViewerDialog extends javax.swing.JDialog {

    // Singleton window: reopening from the same owner reuses the instance and refreshes it.
    private static volatile VoiceNotesViewerDialog INSTANCE = null;

    // Preview cap: notes are at most 15s long, with margin to avoid cutting off the tail.
    private static final int MAX_PREVIEW_MS = 20000;

    // A note plus the metadata resolved when the list is reloaded.
    private static final class Note {

        final File file;
        final long millis;      // note creation timestamp (filename prefix)
        final String nick;      // sanitized nick extracted from the filename
        final long duration_ms; // duration derived from the WAV (0 if it couldn't be read)

        Note(File file, long millis, String nick, long duration_ms) {
            this.file = file;
            this.millis = millis;
            this.nick = nick;
            this.duration_ms = duration_ms;
        }
    }

    private final JLabel title_label = new JLabel("", SwingConstants.CENTER);
    private final JPanel body = new JPanel(new BorderLayout());

    private List<Note> notes = new ArrayList<>();

    /**
     * Opens the viewer (or brings it to front and refreshes it if already open for
     * the same owner). Must be called on the EDT.
     *
     * @param owner window that will own the dialog
     */
    public static void open(Window owner) {

        if (INSTANCE != null && INSTANCE.isDisplayable() && INSTANCE.getOwner() == owner) {
            INSTANCE.reload();
            INSTANCE.setVisible(true);
            INSTANCE.toFront();
            INSTANCE.requestFocus();
            return;
        }

        if (INSTANCE != null) {
            INSTANCE.dispose();
        }

        INSTANCE = new VoiceNotesViewerDialog(owner);
        INSTANCE.setLocationRelativeTo(owner);
        INSTANCE.setVisible(true);
        INSTANCE.requestFocus();
    }

    private VoiceNotesViewerDialog(Window owner) {

        super(owner); // JDialog(Window) => non-modal: does not block the settings dialog.

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        Helpers.setTranslatedTitle(this, "audio.notas_de_voz");

        try {
            setIconImage(new ImageIcon(getClass().getResource("/images/menu/voice.png")).getImage());
        } catch (Exception ex) {
            Logger.getLogger(VoiceNotesViewerDialog.class.getName()).log(Level.WARNING, null, ex);
        }

        buildUI();

        setMinimumSize(new Dimension(Math.round(560 * Helpers.DIALOG_ZOOM), Math.round(360 * Helpers.DIALOG_ZOOM)));
        setSize(Math.round(720 * Helpers.DIALOG_ZOOM), Math.round(520 * Helpers.DIALOG_ZOOM));

        // Stop any ongoing preview on close (consistent with AudioSettingsPanel.cleanup()).
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                Audio.stopPreview();
            }
        });

        reload();
    }

    private void buildUI() {

        JPanel content = new JPanel(new BorderLayout());

        title_label.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        title_label.setFont(rowFont(22f, Font.BOLD));
        content.add(title_label, BorderLayout.NORTH);

        body.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        content.add(body, BorderLayout.CENTER);

        setContentPane(content);
    }

    // Re-reads VOICE_DIR and rebuilds the list (newest first). Stops any ongoing preview: the play
    // buttons get recreated, so their state must not be left dangling. Listing files and reading
    // WAV headers (for duration) run OFF the EDT; only the UI population happens on it.
    private void reload() {

        Audio.stopPreview();

        Helpers.threadRun(() -> {
            final List<Note> loaded = loadNotes();
            Helpers.GUIRun(() -> populate(loaded));
        });
    }

    private void populate(List<Note> loaded) {

        notes = loaded;

        title_label.setText(Translator.translate("audio.notas_de_voz") + "   ( " + notes.size() + " )");

        body.removeAll();

        if (notes.isEmpty()) {
            JLabel empty = new JLabel(Translator.translate("audio.no_notas_voz"), SwingConstants.CENTER);
            empty.setFont(rowFont(16f, Font.PLAIN));
            empty.setForeground(new Color(0x77, 0x77, 0x77));
            body.add(empty, BorderLayout.CENTER);
        } else {
            body.add(buildListScroll(), BorderLayout.CENTER);
        }

        body.revalidate();
        body.repaint();
    }

    // Scrollable panel with one row per note, columns aligned via GridBagLayout:
    // [ nick | date/time | duration | play/stop | delete ].
    private JScrollPane buildListScroll() {

        JPanel list = new JPanel(new GridBagLayout());

        int row = 0;

        addHeaderRow(list, row++);

        for (Note note : notes) {
            addNoteRow(list, row++, note);
        }

        // Filler row at the bottom with all the vertical weight: pushes the notes to the top instead
        // of spreading them across the scroll area's height.
        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 0;
        filler.gridy = row;
        filler.gridwidth = 5;
        filler.weighty = 1.0;
        filler.fill = GridBagConstraints.BOTH;
        list.add(Box.createGlue(), filler);

        JScrollPane scroll = new JScrollPane(list);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(0xCC, 0xCC, 0xCC)));
        return scroll;
    }

    private void addHeaderRow(JPanel list, int row) {

        Font header_font = rowFont(13f, Font.BOLD);
        Color header_color = new Color(0x55, 0x55, 0x55);

        JLabel nick = new JLabel(Translator.translate("audio.notas_col_jugador"));
        JLabel date = new JLabel(Translator.translate("audio.notas_col_fecha"));
        JLabel dur = new JLabel(Translator.translate("audio.notas_col_duracion"));
        for (JLabel l : new JLabel[]{nick, date, dur}) {
            l.setFont(header_font);
            l.setForeground(header_color);
        }

        list.add(nick, cell(0, row, 1.0, GridBagConstraints.WEST));
        list.add(date, cell(1, row, 0.0, GridBagConstraints.WEST));
        list.add(dur, cell(2, row, 0.0, GridBagConstraints.WEST));
    }

    private void addNoteRow(JPanel list, int row, Note note) {

        Locale locale = new Locale(GameFrame.LANGUAGE);
        String when = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(new Date(note.millis));

        JLabel nick_label = new JLabel(note.nick.isEmpty() ? "?" : note.nick);
        nick_label.setFont(rowFont(15f, Font.BOLD));

        JLabel date_label = new JLabel(when);
        date_label.setFont(rowFont(13f, Font.PLAIN));
        date_label.setForeground(new Color(0x44, 0x44, 0x44));

        JLabel dur_label = new JLabel(formatDuration(note.duration_ms));
        dur_label.setFont(rowFont(13f, Font.PLAIN));
        dur_label.setForeground(new Color(0x44, 0x44, 0x44));

        list.add(nick_label, cell(0, row, 1.0, GridBagConstraints.WEST));
        list.add(date_label, cell(1, row, 0.0, GridBagConstraints.WEST));
        list.add(dur_label, cell(2, row, 0.0, GridBagConstraints.WEST));
        list.add(buildPlayButton(note), cell(3, row, 0.0, GridBagConstraints.CENTER));
        list.add(buildDeleteButton(note), cell(4, row, 0.0, GridBagConstraints.CENTER));
    }

    // Play/stop preview button: same system as the Audio tab. previewResource stops any previous
    // preview (only one plays at a time); the previous button reverts to "play" on its own because
    // its own on_stop callback fires (on the EDT) when its playback gets cut off.
    private JButton buildPlayButton(Note note) {

        JButton b = new JButton(Helpers.playStopGlyph(false));
        styleIconButton(b, "audio.preview_escuchar");

        final boolean[] playing = {false};

        b.addActionListener(e -> {
            if (playing[0]) {
                Audio.stopPreview();
            } else {
                playing[0] = true;
                b.setIcon(Helpers.playStopGlyph(true));
                b.setToolTipText(Translator.translate("audio.preview_parar"));
                Audio.previewResource(note.file.getAbsolutePath(), MAX_PREVIEW_MS, () -> {
                    playing[0] = false;
                    b.setIcon(Helpers.playStopGlyph(false));
                    b.setToolTipText(Translator.translate("audio.preview_escuchar"));
                });
            }
        });

        return b;
    }

    // Trash button: deletes the note from disk after confirmation. Stops the preview in case this
    // (or any other) note was playing, then reloads the list.
    private JButton buildDeleteButton(Note note) {

        JButton b = new JButton(Helpers.deleteGlyph(Math.round(15 * Helpers.DIALOG_ZOOM)));
        styleIconButton(b, "audio.borrar_nota");

        b.addActionListener(e -> {
            String display_nick = note.nick.isEmpty() ? "?" : note.nick;
            if (Helpers.mostrarMensajeInformativoSINO(this, Translator.translate("audio.borrar_nota_confirm", display_nick)) == javax.swing.JOptionPane.YES_OPTION) {
                Audio.stopPreview();
                if (!note.file.delete() && note.file.exists()) {
                    Helpers.mostrarMensajeError(this, Translator.translate("audio.borrar_nota_error"));
                }
                reload();
            }
        });

        return b;
    }

    private static void styleIconButton(JButton b, String tooltip_key) {
        b.setMargin(new Insets(2, 6, 2, 6));
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setFocusable(false);
        b.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        b.setToolTipText(Translator.translate(tooltip_key));
    }

    // Grid cell constraints: the nick column (weightx=1) absorbs the leftover width; the rest
    // stay right-aligned. Uniform padding between cells.
    private static GridBagConstraints cell(int gridx, int gridy, double weightx, int anchor) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = gridx;
        gbc.gridy = gridy;
        gbc.weightx = weightx;
        gbc.anchor = anchor;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(6, 8, 6, 8);
        return gbc;
    }

    // mm:ss (notes are short, but the format handles any duration).
    private static String formatDuration(long ms) {
        long total_sec = Math.round(ms / 1000.0);
        return String.format("%d:%02d", total_sec / 60, total_sec % 60);
    }

    private static Font rowFont(float size, int style) {
        Font base = (Helpers.GUI_FONT != null ? Helpers.GUI_FONT : new Font("SansSerif", Font.PLAIN, 12));
        return base.deriveFont(style, size * Helpers.DIALOG_ZOOM);
    }

    // Lists the .wav files in VOICE_DIR, resolves their metadata, and sorts newest first.
    private static List<Note> loadNotes() {

        List<Note> result = new ArrayList<>();

        File dir = new File(Init.VOICE_DIR);
        File[] arr = dir.listFiles((File d, String name) -> name.toLowerCase().endsWith(".wav"));

        if (arr != null) {
            for (File f : arr) {
                result.add(toNote(f));
            }
        }

        result.sort((Note a, Note b) -> Long.compare(b.millis, a.millis));

        return result;
    }

    // Derives (millis, nick) from the <millis>_<nick>_<random8>.wav filename, plus the WAV duration.
    // Tolerates names that don't fit the pattern: falls back to the file's lastModified and to the
    // full name as nick.
    private static Note toNote(File f) {

        String name = f.getName();
        String base = name.substring(0, name.length() - 4); // strip ".wav"

        long millis;
        String nick;

        int first = base.indexOf('_');
        int last = base.lastIndexOf('_');

        if (first > 0 && last > first) {
            long parsed;
            try {
                parsed = Long.parseLong(base.substring(0, first));
            } catch (NumberFormatException ex) {
                parsed = f.lastModified();
            }
            millis = parsed;
            nick = base.substring(first + 1, last);
        } else {
            millis = f.lastModified();
            nick = base;
        }

        return new Note(f, millis, nick, durationMillis(f));
    }

    // Note duration: read from the WAV header (without decoding the audio). Falls back to a
    // size-based estimate for mu-law 16 kHz mono (~16000 bytes/sec) if the header gives no frames.
    private static long durationMillis(File f) {
        try {
            javax.sound.sampled.AudioFileFormat aff = javax.sound.sampled.AudioSystem.getAudioFileFormat(f);
            long frames = aff.getFrameLength();
            float rate = aff.getFormat().getFrameRate();
            if (frames > 0 && rate > 0) {
                return (long) (frames / rate * 1000.0);
            }
        } catch (Exception ex) {
            // unexpected format: estimate by size below
        }
        long bytes = f.length() - 44; // canonical WAV header size
        return bytes > 0 ? bytes * 1000L / 16000L : 0;
    }
}
