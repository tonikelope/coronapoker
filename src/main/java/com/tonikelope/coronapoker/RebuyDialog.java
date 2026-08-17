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

import javax.swing.JDialog;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/**
 * Rebuy / initial buy-in dialog: a spinner to pick a chip amount, an optional
 * Cancel button, and a countdown that auto-accepts (or auto-cancels) on
 * timeout. In "defer close" mode (used by the variable initial buy-in)
 * accepting doesn't close the dialog; it switches to an indeterminate "waiting
 * for the other players" state that the dealer closes once collection finishes.
 *
 * @author tonikelope
 */
// NetBeans form DISABLED: the matching .form was renamed to .form.bak on purpose.
// This class's initComponents (the generated //GEN block) is hand-edited (i18n keys via
// putClientProperty, DIALOG_ZOOM scaling, wrapped/translated tooltips and/or manual layout),
// none of which the .form carries. Opening this form in the NetBeans GUI designer and saving
// it would regenerate initComponents from the .form and silently wipe those edits. Maintain
// this class by hand and do NOT restore the .form (the original is kept in git history).
public class RebuyDialog extends JDialog {

    private volatile boolean rebuy = false;
    private volatile boolean cancelled = false;
    private volatile boolean cancelable = false;
    // "Defer close" mode: accepting (OK or timeout) does not close the dialog; it switches to
    // "waiting for the other players" and the dealer closes it once collection finishes. Only
    // used by the variable initial buy-in.
    private volatile boolean defer_close = false;

    public boolean isRebuy() {
        return rebuy;
    }

    public JSpinner getRebuy_spinner() {
        return rebuy_spinner;
    }

    public void setDeferClose(boolean v) {
        this.defer_close = v;
    }

    /**
     * After the initial buy-in is accepted: hides the spinner/buttons and shows
     * an indeterminate bar plus "waiting for the other players" until the
     * dealer closes the dialog once everyone is in.
     * {@code Helpers.barraIndeterminada} cancels the smoothCountdown timer, so
     * the countdown -> indeterminate handoff is clean.
     */
    public void enterWaitingMode() {
        Helpers.GUIRun(() -> {
            // Spinner: DISABLED (stays visible, greyed out) so the player still sees the
            // buy-in they picked. Buttons: HIDDEN.
            rebuy_spinner.setEnabled(false);
            ok_button.setVisible(false);
            cancel_button.setVisible(false);
            // Reveals the message (text already set in the constructor, in the background
            // color): only the color changes here. No re-pack, since that would rely on
            // resizing an already-shown non-resizable window.
            wait_label.setForeground(java.awt.Color.BLACK);
            barra.setVisible(true);
            Helpers.barraIndeterminada(barra);
            panel.repaint();
        });
    }

    private void pausaConBarra(int tiempo) {

        Helpers.GUIRun(() -> {
            barra.setVisible(true);
            Helpers.smoothCountdown(barra, tiempo);
            pack();
        });

        int t = tiempo;

        while (t > 0 && !rebuy && !cancelled) {

            Helpers.pausar(1000);

            if (!GameFrame.getInstance().isTimba_pausada() && !GameFrame.getInstance().getCrupier().isFin_de_la_transmision() && !rebuy) {

                --t;

                // No need to call setValue(t): smoothCountdown runs its own internal Timer
                // repainting the bar every 50ms in ms scale. We only decrement t so the loop
                // knows when to exit on timeout.
            }

        }

        // In defer mode, don't hide the bar here: enterWaitingMode switches it to
        // indeterminate (which already cancels the smoothCountdown timer). Hiding it here
        // would cause a flicker/race with that handoff.
        if (!defer_close) {
            Helpers.GUIRun(() -> {
                // Cancel smoothCountdown's internal Timer before hiding the bar, so it doesn't
                // keep running in the background after the dialog is disposed.
                Helpers.resetBarra(barra, 0);
                barra.setVisible(false);
            });
        }
    }

    /**
     * Same as
     * {@link #RebuyDialog(java.awt.Frame, boolean, boolean, int, int, int, int)}
     * with the legacy fixed range [1, BUYIN] and default value BUYIN.
     */
    public RebuyDialog(java.awt.Frame parent, boolean modal, boolean cancel, int timeout) {
        this(parent, modal, cancel, timeout, 1, GameFrame.BUYIN, GameFrame.BUYIN);
    }

    /**
     * Same as
     * {@link #RebuyDialog(java.awt.Frame, boolean, boolean, int, int, int, int, String)}
     * with the default header key for a plain rebuy ("rebuy.recomprar_3"). Used
     * by the variable buy-in flow (table-entry buy-in and rebuys), where the
     * range and default come from the configurable buy-in bounds
     * (getBuyinMin/getBuyinMax/getBuyinDefault) instead of the fixed buy-in.
     */
    public RebuyDialog(java.awt.Frame parent, boolean modal, boolean cancel, int timeout, int min, int max, int default_value) {
        this(parent, modal, cancel, timeout, min, max, default_value, "rebuy.recomprar_3");
    }

    /**
     * Builds the rebuy / buy-in dialog.
     *
     * @param parent owner frame
     * @param modal whether the dialog blocks input to the owner
     * @param cancel whether Cancel is shown; if false, timing out auto-accepts
     * the current spinner value instead of cancelling
     * @param timeout countdown length in seconds, or {@code <= 0} to disable
     * the countdown
     * @param min minimum spinner value
     * @param max maximum spinner value
     * @param default_value initial spinner value, clamped into [min, max]
     * @param header_key i18n key for the header/title: "rebuy.recomprar_3"
     * (RECOMPRAR) for a rebuy, "rebuy.compra_inicial" (COMPRA INICIAL) for the
     * table-entry buy-in in variable mode
     */
    public RebuyDialog(java.awt.Frame parent, boolean modal, boolean cancel, int timeout, int min, int max, int default_value, String header_key) {
        super(parent, modal);

        initComponents();

        // Header (and title) depend on the use case: rebuy vs initial buy-in.
        jLabel1.putClientProperty("i18n.key", header_key);
        Helpers.setTranslatedTitle(this, header_key);

        // initComponents installs a [1, BUYIN] model (form-generated). Override
        // it with the requested range, clamping the default into [min, max] so a
        // caller can never seed an out-of-range value.
        int safe_max = Math.max(min, max);
        int safe_default = Math.min(Math.max(default_value, min), safe_max);
        // Step derived from the range itself (~1% of the max, minimum 1). We don't reuse
        // NewGameDialog.BUYIN_SPINNER_STEP: that reflects NewGameDialog's local combobox and on
        // a CLIENT may not match the game's actual blinds (received via INIT). If the step
        // exceeded the range, getNextValue/getPreviousValue would return null and the arrows
        // would go dead (a "locked" spinner). Deriving it from the range guarantees it stays
        // usable and fine-grained on every peer (step <= range, always).
        int step = Math.max(1, safe_max / 100);
        rebuy_spinner.setModel(new SpinnerNumberModel(safe_default, min, safe_max, step));

        // Slightly thicker bar (for every use of this dialog). GroupLayout (max) drives the
        // actual width; only the height matters here.
        barra.setPreferredSize(new java.awt.Dimension(Math.round(300 * Helpers.DIALOG_ZOOM), Math.round(30 * Helpers.DIALOG_ZOOM)));

        barra.setVisible(false);

        Helpers.makeNumericSpinnerEditable(rebuy_spinner, false);

        this.cancelable = cancel;

        if (!cancel) {
            cancel_button.setVisible(false);
        }

        ok_button.requestFocus();

        Helpers.applyDialogZoom(this);

        Helpers.translateComponents(this, false);

        // The waiting-message text is set here already, so its space is reserved in THIS
        // pack: the window is non-resizable and a later pack() (after it's shown) won't grow
        // it, which is why the label wouldn't appear if revealed late. Left invisible (color =
        // background) until enterWaitingMode, which only changes the color to reveal it. The
        // font is already the game's (updateFonts -> GUI_FONT).
        wait_label.setText(Translator.translate("rebuy.esperando_jugadores"));
        wait_label.setForeground(panel.getBackground());

        pack();

        if (timeout > 0) {

            Helpers.threadRun(() -> {
                pausaConBarra(timeout);
                if (!rebuy && !cancelled) {
                    // Mandatory dialogs (game-over, joining the table): on timeout, accept the
                    // spinner's CURRENT value. Cancelable dialog (voluntary top-up): on
                    // timeout, discard without rebuying (same as pressing Cancel).
                    if (!cancelable) {
                        rebuy = true;
                    }
                    if (defer_close) {
                        // Initial buy-in: on timeout the default is accepted and the dialog
                        // switches to "waiting for the other players" (closed by the dealer).
                        if (rebuy) {
                            enterWaitingMode();
                        }
                    } else {
                        Helpers.GUIRun(this::dispose);
                    }
                }
            });
        }

    }

    /**
     * AUTO-mode variant: always shows the red Cancel button (even when
     * {@code cancel} is false, which keeps "timeout -> rebuy"), so the
     * automatic rebuy can still be aborted at the last moment. Used by the
     * dealer's automatic rebuy-on-bust when the game-over countdown runs out.
     *
     * @param auto_rebuy_mode when true, forces the Cancel button visible in red
     * regardless of {@code cancel}
     */
    public RebuyDialog(java.awt.Frame parent, boolean modal, boolean cancel, int timeout, int min, int max, int default_value, String header_key, boolean auto_rebuy_mode) {

        this(parent, modal, cancel, timeout, min, max, default_value, header_key);

        if (auto_rebuy_mode) {
            Helpers.GUIRun(() -> {
                cancel_button.setBackground(new java.awt.Color(200, 0, 0));
                cancel_button.setForeground(java.awt.Color.WHITE);
                cancel_button.setVisible(true);
                pack();
            });
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        panel = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();
        ok_button = new javax.swing.JButton();
        cancel_button = new javax.swing.JButton();
        barra = new javax.swing.JProgressBar();
        rebuy_spinner = new javax.swing.JSpinner();
        wait_label = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        Helpers.setTranslatedTitle(this, "rebuy.recomprar_3");
        setModal(true);
        setUndecorated(true);
        setResizable(false);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowActivated(java.awt.event.WindowEvent evt) {
                formWindowActivated(evt);
            }

            public void windowDeactivated(java.awt.event.WindowEvent evt) {
                formWindowDeactivated(evt);
            }
        });

        panel.setBackground(new java.awt.Color(255, 255, 255));
        panel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 102, 0), 10));

        jLabel2.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/chips.png"))); // NOI18N
        jLabel2.setFocusable(false);

        jLabel1.setFont(new java.awt.Font("Dialog", 1, 36)); // NOI18N
        jLabel1.setText("RECOMPRAR");
        jLabel1.putClientProperty("i18n.key", "rebuy.recomprar_3");
        jLabel1.setFocusable(false);

        ok_button.setBackground(new java.awt.Color(0, 130, 0));
        ok_button.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        ok_button.setForeground(new java.awt.Color(255, 255, 255));
        ok_button.setText("Aceptar");
        ok_button.putClientProperty("i18n.key", "ui.aceptar");
        ok_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        ok_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ok_buttonActionPerformed(evt);
            }
        });

        cancel_button.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        cancel_button.setText("Cancelar");
        cancel_button.putClientProperty("i18n.key", "ui.cancelar_2");
        cancel_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        cancel_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancel_buttonActionPerformed(evt);
            }
        });

        rebuy_spinner.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        rebuy_spinner.setModel(new SpinnerNumberModel(GameFrame.BUYIN, 1, GameFrame.BUYIN, NewGameDialog.BUYIN_SPINNER_STEP));
        rebuy_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        rebuy_spinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                rebuy_spinnerStateChanged(evt);
            }
        });

        wait_label.setFont(new java.awt.Font("Dialog", 1, 22)); // NOI18N
        wait_label.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        wait_label.setDoubleBuffered(true);
        wait_label.setFocusable(false);

        javax.swing.GroupLayout panelLayout = new javax.swing.GroupLayout(panel);
        panel.setLayout(panelLayout);
        panelLayout.setHorizontalGroup(
                panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(panelLayout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addGroup(panelLayout.createSequentialGroup()
                                                .addComponent(jLabel2)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addGroup(panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                        .addComponent(rebuy_spinner, javax.swing.GroupLayout.Alignment.TRAILING)
                                                        .addComponent(jLabel1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                                        .addComponent(ok_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                                        .addComponent(cancel_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                                        .addComponent(barra, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(wait_label, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                .addContainerGap())
        );
        panelLayout.setVerticalGroup(
                panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(panelLayout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addGroup(panelLayout.createSequentialGroup()
                                                .addComponent(jLabel1)
                                                .addGap(Math.round(18 * Helpers.DIALOG_ZOOM), Math.round(18 * Helpers.DIALOG_ZOOM), Math.round(18 * Helpers.DIALOG_ZOOM))
                                                .addComponent(rebuy_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                                .addComponent(ok_button)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                                .addComponent(cancel_button))
                                        .addComponent(jLabel2))
                                .addGap(Math.round(18 * Helpers.DIALOG_ZOOM), Math.round(18 * Helpers.DIALOG_ZOOM), Short.MAX_VALUE)
                                .addComponent(wait_label)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(barra, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addContainerGap())
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void cancel_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancel_buttonActionPerformed
        // Cancel: stops the countdown so the deadline doesn't auto-accept after closing
        // (rebuy stays false).
        cancelled = true;
        dispose();
    }//GEN-LAST:event_cancel_buttonActionPerformed

    private void ok_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ok_buttonActionPerformed
        rebuy = true;
        if (defer_close) {
            // Initial buy-in: don't close; switch to "waiting for the other players".
            enterWaitingMode();
        } else {
            dispose();
        }
    }//GEN-LAST:event_ok_buttonActionPerformed

    private void rebuy_spinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_rebuy_spinnerStateChanged
        // The non-editable spinner only changes via the arrows; the countdown is a hard
        // deadline that interacting does NOT cancel (on timeout the current value is
        // accepted). No action needed here.
    }//GEN-LAST:event_rebuy_spinnerStateChanged

    private void formWindowActivated(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowActivated
        if (isModal()) {
            Init.CURRENT_MODAL_DIALOG.add(this);
        }
    }//GEN-LAST:event_formWindowActivated

    private void formWindowDeactivated(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowDeactivated
        if (isModal()) {
            try {
                Init.CURRENT_MODAL_DIALOG.removeLast();
            } catch (Exception ex) {
            }
        }
    }//GEN-LAST:event_formWindowDeactivated

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JProgressBar barra;
    private javax.swing.JButton cancel_button;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JButton ok_button;
    private javax.swing.JPanel panel;
    private javax.swing.JSpinner rebuy_spinner;
    private javax.swing.JLabel wait_label;
    // End of variables declaration//GEN-END:variables
}
