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
 * Visor (galería) de las notas de voz guardadas en {@link Init#VOICE_DIR}. Cada
 * fila muestra el jugador (nick), la fecha/hora y la duración de la nota, con un
 * botón de audición play/stop que reutiliza el MISMO sistema que las audiciones
 * de la pestaña Audio ({@link Audio#previewResource}) y un botón de borrado con
 * confirmación.
 *
 * Los metadatos se derivan del propio fichero: el nombre sigue el patrón
 * {@code <millisEpoch>_<nickSaneado>_<random8>.wav} (ver
 * {@code WaitingRoomFrame.recibirNotaVoz}), de donde salen el timestamp y el
 * nick; la duración se lee de la cabecera del WAV.
 *
 * @author tonikelope
 */
public class VoiceNotesViewerDialog extends javax.swing.JDialog {

    // Ventana única: reabrir desde el mismo owner reutiliza la instancia y la refresca.
    private static volatile VoiceNotesViewerDialog INSTANCE = null;

    // Tope de audición: las notas duran como mucho 15 s, con margen para no cortar el final.
    private static final int MAX_PREVIEW_MS = 20000;

    // Una nota + sus metadatos resueltos al recargar la lista.
    private static final class Note {

        final File file;
        final long millis;      // timestamp de creación de la nota (prefijo del nombre)
        final String nick;      // nick saneado extraído del nombre
        final long duration_ms; // duración derivada del WAV (0 si no se pudo leer)

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
     * Abre el visor (o lo trae al frente y lo refresca si ya está abierto para
     * ese mismo owner). Debe llamarse en el EDT.
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

        super(owner); // JDialog(Window) => NO modal: no bloquea el diálogo de ajustes.

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

        // Corta la audición en curso al cerrar (coherente con AudioSettingsPanel.cleanup()).
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

    // Relee VOICE_DIR y reconstruye la lista (más nuevas arriba). Corta cualquier audición en curso:
    // los botones play se recrean, así que su estado no debe quedar colgando. El listado y la
    // lectura de cabeceras WAV (para la duración) se hacen FUERA del EDT; solo se puebla la UI en él.
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

    // Panel con scroll y una fila por nota, alineadas en columnas por GridBagLayout:
    // [ nick | fecha/hora | duración | ▶ play/stop | 🗑 borrar ].
    private JScrollPane buildListScroll() {

        JPanel list = new JPanel(new GridBagLayout());

        int row = 0;

        addHeaderRow(list, row++);

        for (Note note : notes) {
            addNoteRow(list, row++, note);
        }

        // Fila de relleno al fondo con todo el peso vertical: empuja las notas hacia arriba en vez
        // de repartirlas por el alto del scroll.
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

    // Botón de audición play/stop: mismo sistema que la pestaña Audio. previewResource corta
    // cualquier audición previa (solo suena una a la vez); el botón de la anterior vuelve solo a
    // "play" porque su propio on_stop se dispara (en el EDT) al cortarse su reproducción.
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

    // Botón papelera: borra la nota del disco tras confirmar. Corta la audición por si sonaba esta
    // (o cualquier otra) y recarga la lista.
    private JButton buildDeleteButton(Note note) {

        JButton b = new JButton(deleteGlyph());
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

    // Icono de borrado: una X roja dibujada (trazo redondeado) que escala con DIALOG_ZOOM, en el
    // mismo rojo que el glyph de stop para mantener la paleta coherente.
    private static javax.swing.Icon deleteGlyph() {
        final int size = Math.round(15 * Helpers.DIALOG_ZOOM);
        final Color color = new Color(0xC6, 0x28, 0x28);
        return new javax.swing.Icon() {
            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }

            @Override
            public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) {
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setStroke(new java.awt.BasicStroke(Math.max(2f, size * 0.16f), java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                int m = Math.round(size * 0.2f);
                g2.drawLine(x + m, y + m, x + size - m, y + size - m);
                g2.drawLine(x + size - m, y + m, x + m, y + size - m);
                g2.dispose();
            }
        };
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

    // Constraints de una celda de la rejilla: la columna del nick (weightx=1) absorbe el ancho
    // sobrante; el resto van pegadas a la derecha. Padding uniforme entre celdas.
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

    // mm:ss (las notas son cortas, pero el formato aguanta cualquier duración).
    private static String formatDuration(long ms) {
        long total_sec = Math.round(ms / 1000.0);
        return String.format("%d:%02d", total_sec / 60, total_sec % 60);
    }

    private static Font rowFont(float size, int style) {
        Font base = (Helpers.GUI_FONT != null ? Helpers.GUI_FONT : new Font("SansSerif", Font.PLAIN, 12));
        return base.deriveFont(style, size * Helpers.DIALOG_ZOOM);
    }

    // Lista los .wav de VOICE_DIR, resuelve sus metadatos y ordena de la más nueva a la más antigua.
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

    // Deriva (millis, nick) del nombre <millis>_<nick>_<random8>.wav y la duración del WAV. Tolera
    // nombres que no encajen: cae al lastModified del fichero y al nombre completo como nick.
    private static Note toNote(File f) {

        String name = f.getName();
        String base = name.substring(0, name.length() - 4); // sin ".wav"

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

    // Duración de la nota: se lee de la cabecera del WAV (sin decodificar el audio). Con caída a una
    // estimación por tamaño para mu-law 16 kHz mono (~16000 bytes/seg) si la cabecera no da frames.
    private static long durationMillis(File f) {
        try {
            javax.sound.sampled.AudioFileFormat aff = javax.sound.sampled.AudioSystem.getAudioFileFormat(f);
            long frames = aff.getFrameLength();
            float rate = aff.getFormat().getFrameRate();
            if (frames > 0 && rate > 0) {
                return (long) (frames / rate * 1000.0);
            }
        } catch (Exception ex) {
            // formato inesperado: se estima por tamaño abajo
        }
        long bytes = f.length() - 44; // cabecera WAV canónica
        return bytes > 0 ? bytes * 1000L / 16000L : 0;
    }
}
