/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tonikelope.coronapoker;

import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.UnsupportedEncodingException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.text.BadLocationException;
import org.apache.commons.codec.binary.Base64;

/**
 *
 * @author tonikelope
 */
public class EmojiPanel extends javax.swing.JPanel {

    public static final int MAX_HIST = 15;
    public static ArrayList<String> EMOJI_SRC;
    private static ArrayList<ImageIcon> EMOJI_ICON;
    private static ArrayDeque<Integer> HISTORIAL;
    private static final int EMOJI_COUNT = 1826;
    private static boolean INIT = false;

    /**
     * Creates new form EmojiPanel
     */
    public EmojiPanel() {
        initComponents();
        populateEmojis();
    }

    public static void initClass() {

        if (!INIT) {

            INIT = true;

            Helpers.threadRun(new Runnable() {
                public void run() {
                    synchronized (EmojiPanel.class) {
                        EMOJI_SRC = crearEmojisImageSrcs();
                        EMOJI_ICON = crearEmojisImageIcons();
                        HISTORIAL = crearHistorial();
                    }
                }
            });

        }

    }

    private void populateEmojis() {

        Helpers.threadRun(new Runnable() {
            public void run() {
                synchronized (EmojiPanel.class) {

                    Helpers.GUIRun(new Runnable() {
                        public void run() {
                            history_panel.removeAll();

                            for (Integer i : HISTORIAL) {
                                createEmoji(history_panel, i);
                            }

                            emoji_panel.removeAll();

                            for (int i = 1; i <= EMOJI_COUNT; i++) {
                                createEmoji(emoji_panel, i);
                            }

                            revalidate();

                            repaint();

                            WaitingRoomFrame.getInstance().getEmoji_button().setEnabled(WaitingRoomFrame.getInstance().getChat_box().isEnabled());
                        }
                    });
                }
            }
        });
    }

    public void refreshEmojiHistory() {

        history_panel.removeAll();

        for (Integer i : HISTORIAL) {
            createEmoji(history_panel, i);
        }

        revalidate();

        repaint();
    }

    private void createEmoji(JPanel panel, int i) {

        if (i >= 0 && i < EMOJI_ICON.size()) {
            JLabel label = new JLabel();

            label.setCursor(new Cursor(Cursor.HAND_CURSOR));

            label.setIcon(EMOJI_ICON.get(i - 1));

            final int j = i;

            label.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    try {

                        WaitingRoomFrame.getInstance().getChat_box().getDocument().insertString(WaitingRoomFrame.getInstance().getChat_box().getCaretPosition(), " #" + j + "# ", null);
                        WaitingRoomFrame.getInstance().getChat_box().requestFocus();

                        Helpers.threadRun(new Runnable() {
                            @Override
                            public void run() {
                                updateHistorial(j);
                            }
                        });

                    } catch (BadLocationException ex) {
                        Logger.getLogger(EmojiPanel.class.getName()).log(Level.SEVERE, null, ex);
                    }

                }
            });

            panel.add(label);
        }
    }

    private void updateHistorial(int j) {

        if (HISTORIAL.isEmpty() || !HISTORIAL.peekFirst().equals(j)) {

            if (HISTORIAL.contains(j)) {
                HISTORIAL.remove(j);
            }

            HISTORIAL.push(j);

            if (HISTORIAL.size() > MAX_HIST) {
                HISTORIAL.removeLast();
            }

            guardarHistorial();
        }

    }

    private void guardarHistorial() {

        Integer[] historial = HISTORIAL.toArray(new Integer[0]);

        String[] hist = new String[historial.length];

        for (int i = 0; i < historial.length; i++) {

            hist[i] = String.valueOf(historial[i]);
        }

        try {
            Helpers.PROPERTIES.setProperty("chat_emoji_hist", Base64.encodeBase64String(String.join(",", hist).getBytes("UTF-8")));
            Helpers.savePropertiesFile();
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(EmojiPanel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private static ArrayDeque<Integer> crearHistorial() {

        ArrayDeque<Integer> historial = new ArrayDeque<>();

        String hist_b64 = Helpers.PROPERTIES.getProperty("chat_emoji_hist", "");

        if (!hist_b64.isBlank()) {

            String hist;
            try {
                hist = new String(Base64.decodeBase64(hist_b64), "UTF-8");

                String[] hist_numbers = hist.split(",");

                for (String s : hist_numbers) {

                    int i = Integer.parseInt(s);

                    if (i >= 0 && i < EMOJI_ICON.size()) {
                        historial.addLast(Integer.parseInt(s));
                    }
                }

                while (historial.size() > MAX_HIST) {
                    historial.removeFirst();
                }

            } catch (UnsupportedEncodingException ex) {
                Logger.getLogger(EmojiPanel.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        return historial;

    }

    private static ArrayList<String> crearEmojisImageSrcs() {

        ArrayList<String> image_src_list = new ArrayList<>();

        for (int i = 1; i <= EMOJI_COUNT; i++) {

            image_src_list.add(EmojiPanel.class.getResource("/images/emoji_chat/" + i + ".png").toExternalForm());
        }

        return image_src_list;
    }

    private static ArrayList<ImageIcon> crearEmojisImageIcons() {

        ArrayList<ImageIcon> image_icon_list = new ArrayList<>();

        for (int i = 1; i <= EMOJI_COUNT; i++) {

            image_icon_list.add(new ImageIcon(EmojiPanel.class.getResource("/images/emoji_chat/" + i + ".png")));
        }

        return image_icon_list;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        history_panel = new javax.swing.JPanel();
        emoji_panel = new javax.swing.JPanel();

        setFocusable(false);
        setRequestFocusEnabled(false);

        history_panel.setBackground(new java.awt.Color(153, 153, 153));

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(history_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0)
                .addComponent(emoji_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(0, 0, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(history_panel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(emoji_panel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE)))
        );
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel emoji_panel;
    private javax.swing.JPanel history_panel;
    // End of variables declaration//GEN-END:variables
}
