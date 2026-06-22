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

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

/**
 * Straddle voluntario: tras repartir, el UTG decide a ciegas (cartas boca abajo)
 * si pone el straddle. Diálogo NO modal anclado sobre sus dos cartas tapadas, con
 * la anchura de ambas, borde naranja como los diálogos del juego, botón verde
 * PONER y rojo NO y una cuenta atrás. Al expirar la barra (o al cerrarse la mesa)
 * se resuelve como NO (no pone el straddle). La resolución se entrega por callback
 * en el EDT con 1 = pone, 0 = no pone. Resolución de un solo disparo.
 *
 * Modelado sobre {@link AutoActionDialog} (mismo borde/cuenta atrás/no-modal),
 * pero con dos botones y anclaje sobre dos componentes (las dos hole cards).
 *
 * @author tonikelope
 */
public class VoluntaryStraddleDialog extends JDialog {

    public static final int NO_STRADDLE = 0;
    public static final int POST_STRADDLE = 1;

    private volatile boolean resolved = false;

    private final JProgressBar barra = new JProgressBar();

    private final IntConsumer on_resolve;

    // Resolución de un solo disparo. Cierra el diálogo e invoca el callback en el
    // EDT con el resultado (1 = pone straddle, 0 = no).
    private synchronized void resolve(int result) {
        if (resolved) {
            return;
        }
        resolved = true;
        Helpers.GUIRun(() -> {
            Helpers.resetBarra(barra, 0);
            dispose();
            if (on_resolve != null) {
                on_resolve.accept(result);
            }
        });
    }

    // Cierra el diálogo desde fuera (p.ej. al recibir el resultado canónico del
    // host antes de que el jugador conteste, o al caerse la partida): lo resuelve
    // como NO. Idempotente (resolve es de un solo disparo), así que es inofensivo
    // aunque ya se hubiera resuelto por su cuenta atrás o por el botón.
    public void cancel() {
        resolve(NO_STRADDLE);
    }

    public VoluntaryStraddleDialog(Frame parent, Component card1, Component card2, int seconds, String amount_text, IntConsumer on_resolve) {

        super(parent, false);

        this.on_resolve = on_resolve;

        setUndecorated(true);
        setResizable(false);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createLineBorder(new Color(255, 102, 0), 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(6, 14, 6, 14);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel(Translator.translate("straddle.dialog_titulo"));
        title.putClientProperty("i18n.key", "straddle.dialog_titulo");
        title.setFont(new Font("Dialog", Font.BOLD, 28));
        title.setForeground(new Color(255, 102, 0));
        title.setHorizontalAlignment(SwingConstants.CENTER);
        title.setFocusable(false);
        panel.add(title, gbc);

        JLabel amount = null;

        if (amount_text != null && !amount_text.isEmpty()) {
            gbc.gridy++;
            amount = new JLabel(amount_text);
            amount.setFont(new Font("Dialog", Font.BOLD, 22));
            amount.setForeground(Color.BLACK);
            amount.setHorizontalAlignment(SwingConstants.CENTER);
            amount.setFocusable(false);
            panel.add(amount, gbc);
        }

        gbc.gridy++;
        barra.setPreferredSize(new Dimension(220, 22));
        panel.add(barra, gbc);

        // Botones lado a lado: PONER (verde) a la izquierda, NO (rojo) a la derecha.
        gbc.gridy++;
        gbc.gridwidth = 1;
        gbc.weightx = 0.5;

        JButton post = new JButton(Translator.translate("straddle.dialog_poner"));
        post.putClientProperty("i18n.key", "straddle.dialog_poner");
        post.setBackground(new Color(0, 130, 0));
        post.setForeground(Color.WHITE);
        post.setFont(new Font("Dialog", Font.BOLD, 16));
        post.setCursor(new Cursor(Cursor.HAND_CURSOR));
        post.setFocusable(false);
        post.addActionListener((java.awt.event.ActionEvent e) -> resolve(POST_STRADDLE));
        gbc.gridx = 0;
        panel.add(post, gbc);

        JButton no = new JButton(Translator.translate("straddle.dialog_no"));
        no.putClientProperty("i18n.key", "straddle.dialog_no");
        no.setBackground(new Color(200, 0, 0));
        no.setForeground(Color.WHITE);
        no.setFont(new Font("Dialog", Font.BOLD, 16));
        no.setCursor(new Cursor(Cursor.HAND_CURSOR));
        no.setFocusable(false);
        no.addActionListener((java.awt.event.ActionEvent e) -> resolve(NO_STRADDLE));
        gbc.gridx = 1;
        panel.add(no, gbc);

        setContentPane(panel);

        // No-modal: que no robe el foco del teclado al tablero. Los botones siguen
        // respondiendo al ratón aunque la ventana no sea focusable.
        setFocusableWindowState(false);

        Helpers.updateFonts(this, Helpers.GUI_FONT, null);
        Helpers.translateComponents(this, false);

        // Anclaje sobre las DOS hole cards tapadas: misma anchura que abarcan ambas
        // (borde izq. de la primera → borde dcho. de la segunda) y centrado vertical
        // respecto a su altura. Los rótulos se reajustan a esa anchura ANTES de
        // empaquetar para que la altura salga correcta. Si las cartas no se están
        // mostrando, cae al centro del owner con el tamaño natural.
        boolean anchored = card1 != null && card1.isShowing() && card2 != null && card2.isShowing();

        int span = 0;
        int left = 0;
        int top = 0;
        int cards_h = 0;

        if (anchored) {
            Point a1 = card1.getLocationOnScreen();
            Point a2 = card2.getLocationOnScreen();
            left = Math.min(a1.x, a2.x);
            int right = Math.max(a1.x + card1.getWidth(), a2.x + card2.getWidth());
            span = right - left;
            top = Math.min(a1.y, a2.y);
            cards_h = Math.max(card1.getHeight(), card2.getHeight());

            // Ancho útil = vano − borde (10 px a cada lado) − insets (14 px a cada lado).
            int avail = span - 2 * 10 - 2 * 14;
            if (avail > 20) {
                title.setFont(Helpers.fitFontToWidth(title, title.getText(), title.getFont(), avail, 12));
                if (amount != null) {
                    amount.setFont(Helpers.fitFontToWidth(amount, amount.getText(), amount.getFont(), avail, 11));
                }
            }
        }

        pack();

        if (anchored) {
            setSize(Math.max(span, getWidth()), getHeight());
            setLocation(left, top + (cards_h - getHeight()) / 2);
        } else {
            setLocationRelativeTo(parent);
        }

        // Cuenta atrás en background. Resuelve por callback: timeout o fin de partida
        // -> NO straddle. El host (o el resultado canónico) puede cerrarlo antes via
        // cancel()/resolve.
        Helpers.threadRun(() -> {

            Helpers.GUIRun(() -> Helpers.smoothCountdown(barra, seconds));

            int t = seconds;

            while (t > 0 && !resolved) {

                Helpers.pausar(1000);

                if (resolved) {
                    return;
                }

                if (GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {
                    resolve(NO_STRADDLE);
                    return;
                }

                if (!GameFrame.getInstance().isTimba_pausada()) {
                    --t;
                }
            }

            if (!resolved) {
                resolve(NO_STRADDLE);
            }
        });
    }
}
