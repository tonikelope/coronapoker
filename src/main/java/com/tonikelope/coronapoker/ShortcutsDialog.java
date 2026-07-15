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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;

/**
 * Diálogo de ATAJOS DE TECLADO, hand-coded (sin .form): tema oscuro, atajos agrupados por secciones
 * y cada tecla dibujada como una "tecla" física (KeyCapLabel, recuadro redondeado). El contenido se
 * genera a partir de la tabla SECTIONS, con textos i18n (shortcuts.*), así que añadir/quitar un atajo
 * es tocar una fila. Sigue implementando ZoomableInterface para escalar con el zoom del juego.
 *
 * @author tonikelope
 */
public class ShortcutsDialog extends JDialog implements ZoomableInterface {

    // Paleta oscura (consola) coherente con el registro / la estética reciente.
    private static final Color BG = new Color(22, 22, 26);
    private static final Color HEADER_BG = new Color(38, 38, 45);
    private static final Color SECTION_FG = new Color(120, 205, 235);
    private static final Color ACTION_FG = new Color(222, 224, 230);
    private static final Color SEP_FG = new Color(120, 122, 132);
    private static final Color KEY_BG = new Color(52, 54, 63);
    private static final Color KEY_BG2 = new Color(38, 40, 48);
    private static final Color KEY_BORDER = new Color(96, 99, 112);
    private static final Color KEY_FG = new Color(238, 240, 245);

    private static final Font HEADER_FONT = new Font("Dialog", Font.BOLD, 22);
    private static final Font SECTION_FONT = new Font("Dialog", Font.BOLD, 15);
    private static final Font ACTION_FONT = new Font("Dialog", Font.PLAIN, 16);
    private static final Font KEY_FONT = new Font("Dialog", Font.BOLD, 13);
    private static final Font SEP_FONT = new Font("Dialog", Font.BOLD, 15);

    // Contenido: {claveSección, filas[]}, y cada fila {claveAcción, gruposDeTeclas[][]}. Dentro de un
    // grupo las teclas se unen con "+"; grupos distintos (p. ej. TAB / SHIFT+TAB) se separan con aire.
    private static final Object[][] SECTIONS = {
        {"shortcuts.sec_game", new Object[][]{
            {"shortcuts.act_pause", new String[][]{{"ALT", "P"}}},
            {"shortcuts.act_fullscreen", new String[][]{{"ALT", "F"}}},
            {"shortcuts.act_compact", new String[][]{{"ALT", "X"}}},
            {"shortcuts.act_lights", new String[][]{{"ALT", "L"}}},
            {"shortcuts.act_halt", new String[][]{{"ALT", "H"}}},
            {"shortcuts.act_refresh", new String[][]{{"F5"}}},
            {"shortcuts.act_latency", new String[][]{{"F7"}}},
            {"shortcuts.act_clock", new String[][]{{"ALT", "W"}}},
            {"shortcuts.act_log", new String[][]{{"ALT", "R"}}},
            {"shortcuts.act_chat", new String[][]{{"ALT", "C"}}},
            {"shortcuts.act_buyin", new String[][]{{"S"}}},
            {"shortcuts.act_screenshot", new String[][]{{"CTRL", "P"}}},
            {"shortcuts.act_quit", new String[][]{{"CTRL", "Q"}}},
            {"shortcuts.act_force", new String[][]{{"CTRL", "ALT", "ESC"}}}
        }},
        {"shortcuts.sec_bet", new Object[][]{
            {"shortcuts.act_check", new String[][]{{"SPACE"}}},
            {"shortcuts.act_fold", new String[][]{{"ESC"}}},
            {"shortcuts.act_bet", new String[][]{{"↑"}, {"↓"}, {"←"}, {"→"}}},
            {"shortcuts.act_confirm", new String[][]{{"ENTER"}}},
            {"shortcuts.act_allin", new String[][]{{"SHIFT", "ENTER"}}}
        }},
        {"shortcuts.sec_view", new Object[][]{
            {"shortcuts.act_zoomin", new String[][]{{"CTRL", "+"}}},
            {"shortcuts.act_zoomout", new String[][]{{"CTRL", "-"}}},
            {"shortcuts.act_zoomreset", new String[][]{{"CTRL", "0"}}},
            {"shortcuts.act_zoomwheel", new String[][]{{"CTRL", "↕"}}}
        }},
        {"shortcuts.sec_audio", new Object[][]{
            {"shortcuts.act_mute", new String[][]{{"ALT", "S"}}},
            {"shortcuts.act_volup", new String[][]{{"SHIFT", "↑"}}},
            {"shortcuts.act_voldown", new String[][]{{"SHIFT", "↓"}}},
            {"shortcuts.act_voice", new String[][]{{"F9"}}}
        }},
        {"shortcuts.sec_chat", new Object[][]{
            {"shortcuts.act_fastchat", new String[][]{{"º"}}},
            {"shortcuts.act_history", new String[][]{{"↑"}, {"↓"}}}
        }},
        {"shortcuts.sec_img", new Object[][]{
            {"shortcuts.act_images", new String[][]{{"1"}}},
            {"shortcuts.act_imgnav", new String[][]{{"TAB"}, {"SHIFT", "TAB"}}},
            {"shortcuts.act_imgsend", new String[][]{{"S"}}},
            {"shortcuts.act_imgdel", new String[][]{{"BACK"}}}
        }}
    };

    public ShortcutsDialog(java.awt.Frame parent, boolean modal) {
        super(parent, modal);
        buildUI();
        Helpers.setTranslatedTitle(this, "ui.atajos");
        Helpers.preserveOriginalFontSizes(this);
        Helpers.updateFonts(this, Helpers.GUI_FONT, Helpers.DIALOG_ZOOM);
        Helpers.translateComponents(this, false);
        pack();
        capSize();
    }

    private void buildUI() {
        getContentPane().removeAll();
        getContentPane().setBackground(BG);
        getContentPane().setLayout(new BorderLayout());

        JLabel header = new JLabel("", SwingConstants.CENTER);
        Helpers.setTranslatedText(header, "shortcuts.title");
        header.setForeground(Color.WHITE);
        header.setFont(HEADER_FONT);
        header.setOpaque(true);
        header.setBackground(HEADER_BG);
        header.setBorder(BorderFactory.createEmptyBorder(14, 20, 12, 20));
        getContentPane().add(header, BorderLayout.NORTH);

        JPanel content = new JPanel(new GridBagLayout());
        content.setBackground(BG);
        GridBagConstraints gbc = new GridBagConstraints();
        int row = 0;
        boolean first = true;

        for (Object[] section : SECTIONS) {
            JLabel sec = new JLabel();
            Helpers.setTranslatedText(sec, (String) section[0]);
            sec.setForeground(SECTION_FG);
            sec.setFont(SECTION_FONT);
            gbc.gridx = 0;
            gbc.gridy = row++;
            gbc.gridwidth = 2;
            gbc.weightx = 1;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(first ? 12 : 22, 18, 6, 18);
            content.add(sec, gbc);
            first = false;

            for (Object[] r : (Object[][]) section[1]) {
                JLabel act = new JLabel();
                Helpers.setTranslatedText(act, (String) r[0]);
                act.setForeground(ACTION_FG);
                act.setFont(ACTION_FONT);
                gbc.gridx = 0;
                gbc.gridy = row;
                gbc.gridwidth = 1;
                gbc.weightx = 1;
                gbc.anchor = GridBagConstraints.WEST;
                gbc.fill = GridBagConstraints.HORIZONTAL;
                gbc.insets = new Insets(3, 32, 3, 26);
                content.add(act, gbc);

                gbc.gridx = 1;
                gbc.weightx = 0;
                gbc.anchor = GridBagConstraints.EAST;
                gbc.fill = GridBagConstraints.NONE;
                gbc.insets = new Insets(3, 0, 3, 18);
                content.add(keysPanel((String[][]) r[1]), gbc);
                row++;
            }
        }

        JScrollPane sp = new JScrollPane(content, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null);
        sp.getViewport().setBackground(BG);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        getContentPane().add(sp, BorderLayout.CENTER);
    }

    // Panel de teclas alineado a la derecha: teclas del mismo grupo separadas por "+"; grupos
    // distintos, por el hueco del FlowLayout.
    private JComponent keysPanel(String[][] groups) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        p.setOpaque(false);
        for (String[] group : groups) {
            for (int i = 0; i < group.length; i++) {
                if (i > 0) {
                    p.add(sep());
                }
                p.add(new KeyCapLabel(group[i]));
            }
        }
        return p;
    }

    private JLabel sep() {
        JLabel l = new JLabel("+");
        l.setForeground(SEP_FG);
        l.setFont(SEP_FONT);
        return l;
    }

    // Tras pack(), limita la altura a una fracción de la pantalla (el resto lo cubre el scroll) para
    // que con muchas secciones el diálogo no se salga por abajo.
    private void capSize() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int maxH = (int) (screen.height * 0.85);
        if (getHeight() > maxH) {
            setSize(getWidth() + 24, maxH);
        }
    }

    @Override
    public void zoom(float factor, ConcurrentLinkedQueue<Long> notifier) {
        Helpers.zoomFonts(this, factor, null);

        Helpers.GUIRun(() -> setVisible(false));

        Helpers.pausar(250);

        Helpers.GUIRun(() -> {
            pack();
            capSize();
            setLocation(getParent().getX() + getParent().getWidth() - getWidth(),
                    getParent().getY() + getParent().getHeight() - getHeight());
            setVisible(true);
        });
    }

    // Una tecla dibujada como recuadro redondeado (gradiente + borde). getPreferredSize se calcula
    // sobre la fuente ACTUAL, así el zoom del juego (que reescala la fuente) la agranda/encoge y un
    // pack() posterior recoloca todo. Las de un solo carácter salen cuadradas.
    private static final class KeyCapLabel extends JLabel {

        KeyCapLabel(String txt) {
            super(txt, SwingConstants.CENTER);
            setForeground(KEY_FG);
            setFont(KEY_FONT);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(3, 9, 3, 9));
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            Insets in = getInsets();
            int h = fm.getHeight() + in.top + in.bottom;
            int w = Math.max(fm.stringWidth(getText()) + in.left + in.right, h);
            return new Dimension(w, h);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setPaint(new GradientPaint(0, 0, KEY_BG, 0, h, KEY_BG2));
            g2.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);
            g2.setColor(KEY_BORDER);
            g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
