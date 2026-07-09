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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

/**
 * Heads-up / multi-way all-in "run it twice" vote popup.
 *
 * Two buttons (NORMAL / RUN IT TWICE) each showing the live vote tally, plus a
 * countdown progress bar. When the timer runs out without a local vote, NORMAL
 * is cast automatically. The vote is irrevocable once cast (both buttons get
 * disabled). The dialog is host-driven: it stays open showing live tallies
 * until the host closes it via {@link #closeDialog()}; a hard safety cap
 * disposes it anyway if a close ever gets lost.
 *
 * The dialog pops up centered over the table, right on top of the community
 * cards, so when the board already shows at least one revealed card it is
 * shown slightly translucent: the voter can read the board before deciding.
 * On a preflop all-in there is nothing to see behind and it stays opaque.
 *
 * @author tonikelope
 */
public class RunItTwiceDialog extends JDialog {

    public static final int VOTE_PENDING = -1;
    public static final int VOTE_NORMAL = 0;
    public static final int VOTE_RUN_IT_TWICE = 1;

    // Grace window (ms) after the countdown ends to still receive the host's
    // CLOSE before force-disposing — covers network latency on the final tally.
    private static final int SAFETY_GRACE_MS = 8000;

    // Translucent enough to read the community cards behind the dialog while
    // keeping the buttons and tallies perfectly legible.
    private static final float DIALOG_OPACITY = 0.95f;

    private volatile int vote = VOTE_PENDING;
    // Gate so only the FIRST cast wins: the EDT button click and the countdown
    // timeout's automatic NORMAL can race; without this both could pass the
    // check-then-act and fire the listener twice.
    private final AtomicBoolean vote_cast = new AtomicBoolean(false);
    private volatile boolean disposing = false;
    private volatile IntConsumer vote_listener = null;

    private final JProgressBar barra = new JProgressBar();
    private final JButton normal_button = new JButton();
    private final JButton rit_button = new JButton();
    private final JLabel pending_label = new JLabel();
    private final String normal_label;
    private final String rit_label;
    private final String pending_label_text;
    private final String pot_text;
    private final int total_voters;

    private volatile int tally_normal = 0;
    private volatile int tally_rit = 0;

    public RunItTwiceDialog(java.awt.Frame parent, int timeout, int totalVoters, String potText) {
        super(parent, true);

        total_voters = totalVoters;
        pot_text = potText;
        normal_label = Translator.translate("runittwice.btn_normal");
        rit_label = Translator.translate("runittwice.btn_run_it_twice");
        pending_label_text = Translator.translate("runittwice.pending_players");

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setUndecorated(true);
        setResizable(false);
        Helpers.setTranslatedTitle(this, "runittwice.dialog_title");

        JPanel panel = new JPanel();
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 102, 0), 10),
                BorderFactory.createEmptyBorder(25, 45, 25, 45)));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        pending_label.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 24));
        pending_label.setForeground(new Color(200, 60, 0));
        pending_label.setAlignmentX(Component.CENTER_ALIGNMENT);
        pending_label.setHorizontalAlignment(SwingConstants.CENTER);

        JLabel title = new JLabel(Translator.translate("runittwice.dialog_title"));
        title.putClientProperty("i18n.key", "runittwice.dialog_title");
        title.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 30));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setHorizontalAlignment(SwingConstants.CENTER);

        JLabel question = new JLabel(Translator.translate("runittwice.dialog_question"));
        question.putClientProperty("i18n.key", "runittwice.dialog_question");
        question.setFont(new java.awt.Font("Dialog", java.awt.Font.PLAIN, 24));
        question.setAlignmentX(Component.CENTER_ALIGNMENT);
        question.setHorizontalAlignment(SwingConstants.CENTER);

        // Bote total en juego: la cifra que se reparte una o dos veces. Se
        // muestra para que cada votante decida con el dinero a la vista (el
        // diálogo tapa la pot_label del tapete que normalmente lo enseña).
        JLabel pot_label = new JLabel(Translator.translate("game.bote_2") + " " + pot_text);
        pot_label.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 28));
        pot_label.setForeground(new Color(0, 110, 0));
        pot_label.setAlignmentX(Component.CENTER_ALIGNMENT);
        pot_label.setHorizontalAlignment(SwingConstants.CENTER);

        normal_button.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 36));
        normal_button.setBackground(new Color(120, 120, 120));
        normal_button.setForeground(Color.WHITE);
        normal_button.setFocusable(false);
        normal_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        normal_button.setMargin(new java.awt.Insets(14, 28, 14, 28));
        normal_button.addActionListener((e) -> castVote(VOTE_NORMAL));

        rit_button.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 36));
        rit_button.setBackground(new Color(0, 130, 0));
        rit_button.setForeground(Color.WHITE);
        rit_button.setFocusable(false);
        rit_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        rit_button.setMargin(new java.awt.Insets(14, 28, 14, 28));
        rit_button.addActionListener((e) -> castVote(VOTE_RUN_IT_TWICE));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 35, 15));
        buttons.setBackground(Color.WHITE);
        buttons.add(normal_button);
        buttons.add(rit_button);
        buttons.setAlignmentX(Component.CENTER_ALIGNMENT);

        barra.setPreferredSize(new Dimension(620, 38));
        barra.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        barra.setAlignmentX(Component.CENTER_ALIGNMENT);

        panel.add(javax.swing.Box.createVerticalStrut(8));
        panel.add(pending_label);
        panel.add(javax.swing.Box.createVerticalStrut(6));
        panel.add(title);
        panel.add(javax.swing.Box.createVerticalStrut(6));
        panel.add(question);
        panel.add(javax.swing.Box.createVerticalStrut(6));
        panel.add(pot_label);
        panel.add(javax.swing.Box.createVerticalStrut(6));
        panel.add(buttons);
        panel.add(javax.swing.Box.createVerticalStrut(8));
        panel.add(barra);
        panel.add(javax.swing.Box.createVerticalStrut(8));

        refreshButtonLabels();
        refreshPendingLabel();

        setContentPane(panel);

        Helpers.updateFonts(this, Helpers.GUI_FONT, null);
        Helpers.translateComponents(this, false);

        pack();
        setLocationRelativeTo(parent);

        setOpacity(DIALOG_OPACITY);

        Helpers.threadRun(() -> countdownLoop(timeout));
    }

    public int getVote() {
        return vote;
    }

    public void setVoteListener(IntConsumer listener) {
        this.vote_listener = listener;
    }

    /**
     * Updates the live tally shown on the two buttons. Safe to call from any
     * thread.
     */
    public void setTally(int normal, int run_it_twice) {
        this.tally_normal = normal;
        this.tally_rit = run_it_twice;
        Helpers.GUIRun(() -> {
            refreshButtonLabels();
            refreshPendingLabel();
        });
    }

    public void closeDialog() {
        disposing = true;
        Helpers.GUIRun(() -> {
            Helpers.resetBarra(barra, 0);
            dispose();
        });
    }

    private void refreshButtonLabels() {
        normal_button.setText(normal_label + " [" + tally_normal + "]");
        rit_button.setText(rit_label + " [" + tally_rit + "]");
    }

    private void refreshPendingLabel() {
        int pending = total_voters - tally_normal - tally_rit;
        if (pending < 0) {
            pending = 0;
        }
        pending_label.setText(pending_label_text + " [" + pending + "]");
    }

    private void castVote(int v) {
        if (!vote_cast.compareAndSet(false, true)) {
            return;
        }
        vote = v;
        Helpers.GUIRun(() -> {
            normal_button.setEnabled(false);
            rit_button.setEnabled(false);
        });
        IntConsumer listener = this.vote_listener;
        if (listener != null) {
            listener.accept(v);
        }
    }

    private void countdownLoop(int timeout) {
        Helpers.GUIRun(() -> Helpers.smoothCountdown(barra, timeout));

        int t = timeout;
        while (t > 0 && vote == VOTE_PENDING && !disposing) {
            Helpers.pausar(1000);
            if (!GameFrame.getInstance().isTimba_pausada()
                    && !GameFrame.getInstance().getCrupier().isFin_de_la_transmision()
                    && vote == VOTE_PENDING && !disposing) {
                --t;
            }
        }

        // Timeout with no local vote -> automatic NORMAL.
        if (vote == VOTE_PENDING && !disposing) {
            castVote(VOTE_NORMAL);
        }

        // Wait for the host's CLOSE; hard cap so a lost CLOSE never hangs the UI.
        int grace = SAFETY_GRACE_MS;
        while (!disposing && grace > 0 && !GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {
            Helpers.pausar(100);
            grace -= 100;
        }

        if (!disposing) {
            closeDialog();
        }
    }
}
