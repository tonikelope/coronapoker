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
 *
 * @author tonikelope
 */
public class RebuyDialog extends JDialog {

    private volatile boolean rebuy = false;
    private volatile boolean cancelled = false;
    private volatile boolean cancelable = false;
    // Modo "diferir cierre": al aceptar (OK o timeout) el dialogo NO se cierra;
    // pasa a "esperando a los demas jugadores" y lo cierra el crupier al terminar
    // la recoleccion. Solo lo usa la compra inicial variable.
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

    // Tras aceptar la compra inicial: oculta spinner/botones y muestra barra
    // indeterminada + "esperando a los demas jugadores" hasta que el crupier
    // cierra el dialogo cuando estan todos. barraIndeterminada cancela el timer
    // del smoothCountdown, asi que el relevo countdown->indeterminada es limpio.
    public void enterWaitingMode() {
        Helpers.GUIRun(() -> {
            // Spinner: se DESACTIVA (sigue visible, en gris) -> el jugador ve el
            // buy-in que eligio. Botones: DESAPARECEN.
            rebuy_spinner.setEnabled(false);
            ok_button.setVisible(false);
            cancel_button.setVisible(false);
            // Revela el mensaje (texto ya puesto en el constructor, en color de
            // fondo): solo cambiamos el color. SIN re-pack -> no dependemos de
            // agrandar una ventana no-resizable ya mostrada.
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

                // setValue(t) redundante: smoothCountdown tiene su Timer interno
                // repintando la barra cada 50ms en escala ms. Solo decrementamos
                // t para que el loop sepa cuando salir por timeout.
            }

        }

        // En modo defer NO ocultamos la barra: enterWaitingMode la pasa a
        // indeterminada (que ya cancela el timer del smoothCountdown). Ocultarla
        // aqui generaria un parpadeo/condicion de carrera con el relevo.
        if (!defer_close) {
            Helpers.GUIRun(() -> {
                // Cancela el Timer interno del smoothCountdown antes de ocultar la
                // barra — evita que el Timer siga corriendo en background tras
                // dispose del dialog.
                Helpers.resetBarra(barra, 0);
                barra.setVisible(false);
            });
        }
    }

    /**
     * Creates new form RebuyNowDialog with the legacy range [1, BUYIN] and
     * default value BUYIN.
     */
    public RebuyDialog(java.awt.Frame parent, boolean modal, boolean cancel, int timeout) {
        this(parent, modal, cancel, timeout, 1, GameFrame.BUYIN, GameFrame.BUYIN);
    }

    /**
     * Creates new form RebuyNowDialog with an explicit spinner range and
     * default value. Used by the variable buy-in flow (table-entry buy-in and
     * rebuys), where the range and default come from the configurable buy-in
     * bounds (getBuyinMin/getBuyinMax/getBuyinDefault) instead of the fixed
     * buy-in.
     */
    public RebuyDialog(java.awt.Frame parent, boolean modal, boolean cancel, int timeout, int min, int max, int default_value) {
        this(parent, modal, cancel, timeout, min, max, default_value, "rebuy.recomprar_3");
    }

    /**
     * As above but with an explicit header i18n key: "rebuy.recomprar_3"
     * (RECOMPRAR) for rebuys, "rebuy.compra_inicial" (COMPRA INICIAL) for the
     * table-entry buy-in in variable mode.
     */
    public RebuyDialog(java.awt.Frame parent, boolean modal, boolean cancel, int timeout, int min, int max, int default_value, String header_key) {
        super(parent, modal);

        initComponents();

        // Cabecera (y titulo) segun el caso de uso: recompra vs compra inicial.
        jLabel1.putClientProperty("i18n.key", header_key);
        Helpers.setTranslatedTitle(this, header_key);

        // initComponents installs a [1, BUYIN] model (form-generated). Override
        // it with the requested range, clamping the default into [min, max] so a
        // caller can never seed an out-of-range value.
        int safe_max = Math.max(min, max);
        int safe_default = Math.min(Math.max(default_value, min), safe_max);
        // Paso derivado del propio rango (~1% del maximo, minimo 1). NO usamos
        // NewGameDialog.BUYIN_SPINNER_STEP: ese refleja el combobox local del
        // NewGameDialog y en un CLIENTE puede no casar con las ciegas reales de la
        // partida (recibidas por INIT). Si el paso fuera mayor que el rango,
        // getNextValue/getPreviousValue devuelven null y las flechas quedan muertas
        // -> spinner "bloqueado". Derivarlo del rango lo garantiza usable y fino en
        // todos los peers (paso <= rango siempre).
        int step = Math.max(1, safe_max / 100);
        rebuy_spinner.setModel(new SpinnerNumberModel(safe_default, min, safe_max, step));

        // Barra un poco mas gruesa (en todos los usos del dialogo). El ancho real
        // lo da el GroupLayout (max), aqui solo importa el alto.
        barra.setPreferredSize(new java.awt.Dimension(300, 30));

        barra.setVisible(false);

        Helpers.makeNumericSpinnerEditable(rebuy_spinner, false);

        this.cancelable = cancel;

        if (!cancel) {
            cancel_button.setVisible(false);
        }

        ok_button.requestFocus();

        Helpers.applyDialogZoom(this);

        Helpers.translateComponents(this, false);

        // El mensaje de espera se fija YA aqui para que su espacio se reserve en
        // ESTE pack: la ventana es no-resizable y un pack posterior a mostrarse no
        // la agranda (por eso el label no aparecia al revelarlo tarde). Lo dejamos
        // invisible (color = fondo) hasta enterWaitingMode, que solo cambia el
        // color para revelarlo. La fuente ya es la del juego (updateFonts -> GUI_FONT).
        wait_label.setText(Translator.translate("rebuy.esperando_jugadores"));
        wait_label.setForeground(panel.getBackground());

        pack();

        if (timeout > 0) {

            Helpers.threadRun(() -> {
                pausaConBarra(timeout);
                if (!rebuy && !cancelled) {
                    // Dialogos obligatorios (game-over, entrada al tablero): al
                    // expirar se acepta el valor ACTUAL del spinner. Dialogo
                    // cancelable (top-up voluntario): al expirar se descarta sin
                    // recomprar (equivale a Cancelar).
                    if (!cancelable) {
                        rebuy = true;
                    }
                    if (defer_close) {
                        // Compra inicial: al expirar se acepta el default y se pasa
                        // a "esperando a los demas" (lo cierra el crupier).
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
     * Variante con modo (AUTO): muestra SIEMPRE el botón de cancelar en rojo
     * (aunque cancel sea false, lo que mantiene "al expirar la cuenta →
     * recompra"), para poder abortar la recompra automática en el último
     * momento. La usa la recompra automática al arruinarse (Crupier) cuando se
     * agota el game over.
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
        rebuy_spinner.setModel(new SpinnerNumberModel(GameFrame.BUYIN, 1, GameFrame.BUYIN, NewGameDialog.BUYIN_SPINNER_STEP) );
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
                        .addGap(18, 18, 18)
                        .addComponent(rebuy_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(ok_button)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(cancel_button))
                    .addComponent(jLabel2))
                .addGap(18, 18, Short.MAX_VALUE)
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
        // Cancela: detiene la cuenta atras para que el deadline no auto-acepte
        // despues de cerrar (rebuy queda false).
        cancelled = true;
        dispose();
    }//GEN-LAST:event_cancel_buttonActionPerformed

    private void ok_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ok_buttonActionPerformed
        rebuy = true;
        if (defer_close) {
            // Compra inicial: no cerramos; pasamos a "esperando a los demas".
            enterWaitingMode();
        } else {
            dispose();
        }
    }//GEN-LAST:event_ok_buttonActionPerformed

    private void rebuy_spinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_rebuy_spinnerStateChanged
        // El spinner no editable solo cambia por flechas; la cuenta atras es un
        // deadline duro que NO se cancela al interactuar (al expirar se acepta el
        // valor actual). Sin accion en este evento.
    }//GEN-LAST:event_rebuy_spinnerStateChanged

    private void formWindowActivated(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowActivated
        // TODO add your handling code here:
        if (isModal()) {
            Init.CURRENT_MODAL_DIALOG.add(this);
        }
    }//GEN-LAST:event_formWindowActivated

    private void formWindowDeactivated(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowDeactivated
        // TODO add your handling code here:
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
