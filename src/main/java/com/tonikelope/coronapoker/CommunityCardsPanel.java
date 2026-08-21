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
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.util.Base64;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Table panel for the community cards, pot/bet/blinds labels, the hand-limit
 * and last-hand controls, and the clock/lights/settings icon row. Also owns the
 * pot's flash-yellow and rolling-value animations and its self-fitting font.
 *
 * @author tonikelope
 */
// NetBeans form DISABLED: the matching .form was renamed to .form.bak on purpose.
// This class's initComponents (the generated //GEN block) is hand-edited (i18n keys via
// putClientProperty, DIALOG_ZOOM scaling, wrapped/translated tooltips and/or manual layout),
// none of which the .form carries. Opening this form in the NetBeans GUI designer and saving
// it would regenerate initComponents from the .form and silently wipe those edits. Maintain
// this class by hand and do NOT restore the .form (the original is kept in git history).
public class CommunityCardsPanel extends javax.swing.JPanel implements ZoomableInterface {

    public static final int SOUND_ICON_WIDTH = 30;

    private volatile Color color_contadores = null;
    // Base (unshrunk) pot font while auto-fit has it reduced for a long side-pot breakdown;
    // null when the pot is back to normal size.
    private volatile Font pot_orig_font = null;
    private volatile int hand_label_click_type = 0;
    private volatile boolean ready = false;
    private final CountDownLatch ready_latch = new CountDownLatch(1);
    private volatile Timer icon_zoom_timer = null;
    private final Object zoom_lock = new Object();
    // The two lights-switch icons, pre-scaled to the pot row's height, plus the height they
    // were scaled for: only rebuilt when that height changes, i.e. on zoom (the compact view
    // never rescaled these row icons either).
    private volatile ImageIcon lights_on_icon = null;
    private volatile ImageIcon lights_off_icon = null;
    private volatile int lights_icon_height = -1;

    public void lightsButtonClick() {

        Helpers.GUIRun(() -> {
            lights_labelMouseReleased(null);
        });

    }

    // Applies the lights' visual state: the switch icon for the current brightness, a refresh
    // of the dialogs that depend on brightness (fastchat/notify), and a global repaint. Does
    // NOT change the brightness or play a sound; that's up to the caller (user click, Alt+L
    // shortcut, or an automatic pause).
    public void applyLightsVisuals() {

        Helpers.GUIRun(() -> {

            refreshLightsIcon();

            if (GameFrame.getInstance().getFastchat_dialog() != null) {
                GameFrame.getInstance().getFastchat_dialog().refreshColors();
            }

            if (GameFrame.getInstance().getNotify_dialog() != null) {
                GameFrame.getInstance().getNotify_dialog().repaint();
            }

            GameFrame.getInstance().refresh();
        });

    }

    public JPanel getLast_hand_panel() {
        return last_hand_panel;
    }

    public JLabel getLast_hand_label() {
        return last_hand_label;
    }

    public JProgressBar getBarra_tiempo() {
        return barra_tiempo;
    }

    public JLabel getBet_label() {
        return bet_label;
    }

    public JPanel getCards_panel() {
        return cards_panel;
    }

    public JPanel getBlinds_panel() {
        return blinds_panel;
    }

    public JLabel getBlinds_label() {
        return blinds_label;
    }

    public Card getFlop1() {
        return flop1;
    }

    public Card getFlop2() {
        return flop2;
    }

    public Card getFlop3() {
        return flop3;
    }

    public JPanel getHand_panel() {
        return hand_panel;
    }

    public JLabel getHand_label() {
        return hand_label;
    }

    public JPanel getPot_panel() {
        return pot_panel;
    }

    public JLabel getPot_label() {
        return pot_label;
    }

    public Card getRiver() {
        return river;
    }

    public JLabel getSound_icon() {
        return sound_icon;
    }

    public JLabel getTiempo_partida() {
        return tiempo_partida;
    }

    public Card getTurn() {
        return turn;
    }

    public Card[] getCartasComunes() {

        return new Card[]{flop1, flop2, flop3, turn, river};
    }

    public Color getColor_contadores() {
        return color_contadores;
    }

    public void cambiarColorContadores(Color color) {

        this.color_contadores = color;

        Helpers.GUIRun(() -> {
            if (!((RoundedPanel) pot_panel).isRoundedFill()) {
                // Changing the felt color while the pot flashes yellow needs no special handling:
                // the flash respects whichever color was painted last, here or in any of the
                // other places that set it (see flashPotLabelYellow).
                pot_label.setForeground(color);
            }

            bet_label.setForeground(color);

            if (!((RoundedPanel) blinds_panel).isRoundedFill()) {
                blinds_label.setForeground(color);
            }

            tiempo_partida.setForeground(color);

            if (!((RoundedPanel) hand_panel).isRoundedFill()) {
                hand_label.setForeground(color);
            }
        });
    }

    public JLabel getLights_label() {
        return lights_label;
    }

    // Screen center of the pot_label's ICON (the chips), computed with layoutCompoundLabel to
    // respect alignment, insets and icon-text-gap, so flying chips land right on the pot icon
    // (not the text). Returns null if the pot isn't showing or has no icon.
    public java.awt.geom.Point2D getPotIconScreenCenter() {

        if (pot_label == null || !pot_label.isShowing() || pot_label.getIcon() == null) {
            return null;
        }

        java.awt.Insets insets = pot_label.getInsets();
        java.awt.Rectangle viewR = new java.awt.Rectangle(
                insets.left, insets.top,
                pot_label.getWidth() - insets.left - insets.right,
                pot_label.getHeight() - insets.top - insets.bottom);
        java.awt.Rectangle iconR = new java.awt.Rectangle();
        java.awt.Rectangle textR = new java.awt.Rectangle();

        javax.swing.SwingUtilities.layoutCompoundLabel(
                pot_label, pot_label.getFontMetrics(pot_label.getFont()),
                pot_label.getText(), pot_label.getIcon(),
                pot_label.getVerticalAlignment(), pot_label.getHorizontalAlignment(),
                pot_label.getVerticalTextPosition(), pot_label.getHorizontalTextPosition(),
                viewR, iconR, textR, pot_label.getIconTextGap());

        java.awt.Point p = new java.awt.Point(iconR.x + iconR.width / 2, iconR.y + iconR.height / 2);
        javax.swing.SwingUtilities.convertPointToScreen(p, pot_label);
        return new java.awt.geom.Point2D.Double(p.getX(), p.getY());
    }

    private javax.swing.Timer pot_flash_timer;
    private Color pot_flash_restore;
    private Runnable pot_flash_done_callback; // runs when the flash ends (the LAST one, after re-entries); lets callers await the EXACT end of the flash without arbitrary delays

    public void flashPotLabelYellow() {
        flashPotLabelYellow(null);
    }

    // Brief yellow flash on pot_label: triggered by a flying chip landing on the pot (a signal
    // that the pot "absorbed" the chips). Restores the color it had before the flash.
    // Reentrant: if another chip lands mid-flash, the original color is kept and the restore
    // is restarted.
    //
    // value_update (if not null) runs INSIDE the same EDT runnable, JUST BEFORE turning it
    // yellow: so the pot's value change and the flash paint in the SAME cycle (the number and
    // the color change together, the color never gets ahead of the number). The restore color
    // is captured AFTER the new value is applied.
    public void flashPotLabelYellow(Runnable value_update) {

        Helpers.GUIRun(() -> {
            if (value_update != null) {
                value_update.run();
            }
            if (pot_label == null) {
                return;
            }
            if (pot_flash_timer == null || !pot_flash_timer.isRunning()) {
                pot_flash_restore = pot_label.getForeground();
            } else {
                pot_flash_timer.stop();
                // Re-entry (another chip lands within the 170ms window, normal when several land
                // together): keep the pre-flash color, BUT if something else set a different pot
                // color in the meantime, that one wins and is the one to restore.
                if (!Color.YELLOW.equals(pot_label.getForeground())) {
                    pot_flash_restore = pot_label.getForeground();
                }
            }
            pot_label.setForeground(Color.YELLOW);
            pot_flash_timer = new javax.swing.Timer(170, e -> {
                ((javax.swing.Timer) e.getSource()).stop();
                // Only restore if the pot still has OUR yellow. The flying chip doesn't block
                // whoever launched it, so between takeoff and landing any of a dozen places that
                // paint the pot (the hand winner, showdown, a felt-color change) may have set
                // another color. That color is more recent than the one captured when the flash
                // started, and restoring ours over it could, e.g., leave it green on the pot's
                // green panel.
                if (pot_flash_restore != null && Color.YELLOW.equals(pot_label.getForeground())) {
                    pot_label.setForeground(pot_flash_restore);
                }
                Runnable cb = pot_flash_done_callback;
                pot_flash_done_callback = null;
                if (cb != null) {
                    cb.run();
                }
            });
            pot_flash_timer.setRepeats(false);
            pot_flash_timer.start();
        });
    }

    // Registers a callback that runs when the pot_label's CURRENT flash ends (or right away if
    // none is running). Lets callers await the EXACT end of the flash (170ms after the last
    // landing) without magic numbers: used by the forced-bets animation so it doesn't hide the
    // community cards until the flash has been seen.
    public void onPotFlashDone(Runnable cb) {
        Helpers.GUIRun(() -> {
            if (pot_flash_timer != null && pot_flash_timer.isRunning()) {
                pot_flash_done_callback = cb;
            } else if (cb != null) {
                cb.run();
            }
        });
    }

    /**
     * Sets {@code text} on the pot label, auto-shrinking the font (measured
     * with FontMetrics) so a long side-pot breakdown ("#1{..}+#2{..}+...")
     * never makes the pot label overflow the community-cards width, and
     * restoring the original size as soon as it fits again — the same
     * self-fitting the players' action label uses. The reference width is the
     * five board cards' span (fixed by the cards, never driven by the pot
     * text), NOT the label's own width: the pot row drives the panel's
     * preferred width, so fitting against the label/panel width would be
     * circular and the font would never shrink. Must run on the EDT.
     *
     * @param text the pot label's new text
     */
    public void setPotLabelTextFitted(String text) {

        Font base_font = (pot_orig_font != null) ? pot_orig_font : pot_label.getFont();

        // Width of the 5 community cards block (centered in cards_panel by flexible gaps):
        // fixed and NOT dependent on the pot text, so there's no feedback loop (measuring the
        // label itself, which drives the panel's width, would collapse the font). 0 before the
        // first layout -> fitFontToWidth just returns the base font.
        int available_width = (river.getX() + river.getWidth()) - flop1.getX();

        java.awt.Insets insets = pot_label.getInsets();

        if (insets != null) {
            available_width -= insets.left + insets.right;
        }

        javax.swing.Icon icon = pot_label.getIcon();

        if (icon != null) {
            available_width -= icon.getIconWidth() + pot_label.getIconTextGap();
        }

        // During betting, bet_label (the street) shares the row to the right of the pot; if
        // it's visible its width also counts, to avoid overflow. During showdown / run-out
        // (where the long breakdown appears) it's hidden and the pot gets the full width.
        if (bet_label.isVisible()) {
            available_width -= bet_label.getWidth();
        }

        Font fitted_font = Helpers.fitFontToWidth(pot_label, text, base_font, available_width, Math.max(9, Math.round(base_font.getSize() * 0.5f)));

        if (fitted_font.getSize() < base_font.getSize()) {
            pot_orig_font = base_font;
            pot_label.setFont(fitted_font);
        } else if (pot_orig_font != null) {
            pot_label.setFont(pot_orig_font);
            pot_orig_font = null;
        }

        pot_label.setText(text);
    }

    // Live roll of the main pot (EDT-confined). The numeric value rolls at constant speed
    // while the (localized) "pot"/RIT prefix and the profit suffix stay put; the render
    // callback rebuilds the full string and runs it through the font auto-fit.
    private RollingCounter pot_roller;
    private String pot_roll_prefix = "";
    private String pot_roll_suffix = "";

    private RollingCounter potRoller() {
        if (pot_roller == null) {
            pot_roller = new RollingCounter(
                    (v) -> setPotLabelTextFitted(pot_roll_prefix + " " + Helpers.money2String(v) + pot_roll_suffix),
                    GameFrame.COUNTER_ROLL_SPEED, GameFrame.COUNTER_ROLL_MIN_MS, GameFrame.COUNTER_ROLL_MAX_MS);
        }
        return pot_roller;
    }

    // Rolls the pot to 'value', keeping the prefix/suffix. With animate=false (rolling off /
    // recover) it jumps straight there. Called from GameFrame.setTapeteBote(double, Double)
    // and also when chips land (flashPotLabelYellow) -> the number rolls up alongside the
    // yellow flash (complements it, doesn't replace it).
    public void rollPotValue(String prefix, double value, String suffix, boolean animate) {
        this.pot_roll_prefix = prefix;
        this.pot_roll_suffix = suffix;
        potRoller().roll(value, animate);
    }

    // Sets a NON-numeric pot text ("---", RIT breakdown...): invalidates the roller so the
    // next roll jumps instead of animating from a value that no longer applies.
    public void setPotTextImmediate(String text) {
        potRoller().invalidate();
        setPotLabelTextFitted(text);
    }

    public void restoreBetLabelicon() {

        // bet_label no longer carries an icon (it shows only the street): make sure it stays
        // icon-less when the hand resets. To bring the pot icon back on bet_label, restore the
        // setScaledIconLabel(bet_label, "/images/pot.png", ...) call here (and in zoomIcons /
        // runWhenLaidOut).
        Helpers.GUIRun(() -> {
            bet_label.setIcon(null);
        });
    }

    // Straddle chip to the right of blinds_label (same panel), at the SAME height as the
    // blinds icon. Visible only while a straddle is active.
    private javax.swing.JLabel straddle_label;

    public void refreshStraddleIcon() {
        if (straddle_label == null) {
            return;
        }
        if (GameFrame.STRADDLE) {
            // Height taken from the blinds icon that's ALREADY placed (blinds_label), not
            // recomputed from pot_label.getHeight(): that height derives from pot_label's
            // icon->layout feedback loop and can grow between the initial setup and a later
            // call (e.g. refreshStraddleIcon when blinds are set after dealing), which would
            // make the straddle icon "grow" relative to the blinds one. Anchoring to the blinds
            // icon keeps them ALWAYS equal. Falls back to 0.8*pot_label only if the blinds icon
            // isn't set yet.
            int h = -1;
            javax.swing.Icon ciegas = blinds_label.getIcon();
            if (ciegas != null && ciegas.getIconHeight() > 0) {
                h = ciegas.getIconHeight();
            } else if (pot_label.getHeight() > 0) {
                h = Math.round(0.8f * pot_label.getHeight());
            }
            if (h > 0) {
                Helpers.setScaledIconLabel(straddle_label, getClass().getResource("/images/straddle_small.png"), h, h);
            }
        }
        straddle_label.setVisible(GameFrame.STRADDLE);
    }

    /**
     * Creates the panel: initializes the generated components, adds the
     * straddle icon (not part of the .form) and wires the hand-limit spinner
     * and the zoom-debounce timer.
     */
    public CommunityCardsPanel() {
        Helpers.GUIRunAndWait(() -> {
            initComponents();

            // straddle_label isn't in the .form: created here, rebuilding blinds_panel's
            // layout to place it right of blinds_label, vertically centered (same line as the
            // blinds icon and text).
            straddle_label = new javax.swing.JLabel();
            straddle_label.setFocusable(false);
            straddle_label.setVisible(false);
            javax.swing.GroupLayout bl = new javax.swing.GroupLayout(blinds_panel);
            blinds_panel.setLayout(bl);
            bl.setHorizontalGroup(bl.createSequentialGroup()
                    .addComponent(blinds_label)
                    .addGap(6)
                    .addComponent(straddle_label));
            bl.setVerticalGroup(bl.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(blinds_label)
                    .addComponent(straddle_label));

            // The panel's height must NOT change when hand_label hides during showdown / between
            // hands: it shares the GroupLayout's bottom row, and once invisible the layout
            // stopped reserving it space, shrinking the row a few pixels and causing a vertical
            // jump. honorsVisibility=false makes the layout ALWAYS reserve its space (its
            // preferred size, which scales with zoom — not a fixed height).
            // NOTE: NOT applied to bet_label. bet_label shares its horizontal row with pot_panel
            // (pot_panel grows to MAX, then a gap, then bet_label), and reserving its width while
            // hidden would stop pot_label from expanding to the FULL width during showdown. Since
            // bet_label and pot_label share the same font/icon/vertical border, hiding bet_label
            // doesn't change that row's height, so it needs no honorsVisibility.
            ((javax.swing.GroupLayout) hand_panel.getLayout()).setHonorsVisibility(hand_label, false);

            Helpers.translateComponents(this, false);
            last_hand_panel.setVisible(false);
            hand_limit_spinner.setVisible(false);
            hand_limit_spinner.addChangeListener(e -> {

                Component mySpinnerEditor = hand_limit_spinner.getEditor();
                JFormattedTextField jftf = ((JSpinner.DefaultEditor) mySpinnerEditor).getTextField();
                jftf.setColumns(String.valueOf((int) hand_limit_spinner.getValue()).length());

                revalidate();
                repaint();

            });
            max_hands_button.setVisible(false);
            icon_zoom_timer = new Timer(GameFrame.GUI_RENDER_WAIT, (ActionEvent ae) -> {
                icon_zoom_timer.stop();
                zoomIcons();
                flop1.updateImagePreloadCache();
                flop2.updateImagePreloadCache();
                flop3.updateImagePreloadCache();
                turn.updateImagePreloadCache();
                river.updateImagePreloadCache();
            });
            icon_zoom_timer.setRepeats(false);
            icon_zoom_timer.setCoalesce(false);
            setVisible(false);
        });

        Helpers.runWhenLaidOut(pot_label, () -> {
            // Guard: runWhenLaidOut's internal Timer (2s safety net) can still fire after
            // GameFrame.resetInstance() — getCapa_brillo() would then throw an NPE. During
            // cleanup there's simply nothing left to paint.
            if (GameFrame.getInstance() == null) {
                return;
            }
            sound_icon.setPreferredSize(new Dimension(blinds_label.getHeight(), blinds_label.getHeight()));
            Helpers.setScaledIconLabel(sound_icon, getClass().getResource(GameFrame.SONIDOS ? "/images/sound.png" : "/images/mute.png"), blinds_label.getHeight(), blinds_label.getHeight());
            settings_icon.setPreferredSize(new Dimension(blinds_label.getHeight(), blinds_label.getHeight()));
            Helpers.setScaledWhiteIconLabel(settings_icon, getClass().getResource("/images/menu/gear.png"), blinds_label.getHeight(), blinds_label.getHeight());
            panel_barra.setPreferredSize(new Dimension(-1, (int) Math.round((float) blinds_label.getHeight() * 0.7f)));
            Helpers.setScaledIconButton(pause_button, getClass().getResource("/images/pause.png"), Math.round(0.6f * pause_button.getHeight()), Math.round(0.6f * pause_button.getHeight()));
            Helpers.setScaledIconLabel(pot_label, getClass().getResource("/images/pot.png"), pot_label.getHeight(), pot_label.getHeight());
            // bet_label has no icon (shows only the street). To bring it back: setScaledIconLabel(bet_label, "/images/pot.png", ...).
            Helpers.setScaledIconLabel(blinds_label, getClass().getResource("/images/ciegas_big.png"), Math.round(0.8f * pot_label.getHeight() * (342f / 256)), Math.round(0.8f * pot_label.getHeight()));
            refreshStraddleIcon();
            refreshLightsIcon();

            ready = true;
            ready_latch.countDown();
        });

    }

    // The ONLY place that decides which icon the switch shows: on if there's no dimming veil,
    // off if there is. Called by the click handler, automatic dimming, and the table's icon
    // rescale. Both icons are scaled once per row height and cached: before, every on and off
    // (including pause, game-over and recover) paid for a PNG decode and a smoothed scale on
    // the EDT.
    public void refreshLightsIcon() {
        Helpers.GUIRun(() -> {

            if (GameFrame.getInstance() == null) {
                return;
            }

            final int height = Math.round(0.7f * pot_label.getHeight());

            if (height <= 0) {
                return;
            }

            if (lights_icon_height != height) {

                final int width = Math.round(0.7f * pot_label.getHeight() * (512f / 240));

                try {
                    lights_on_icon = Helpers.scaleIcon(getClass().getResource("/images/lights_on.png"), width, height);
                    lights_off_icon = Helpers.scaleIcon(getClass().getResource("/images/lights_off.png"), width, height);
                    lights_icon_height = height;
                } catch (MalformedURLException ex) {
                    Logger.getLogger(CommunityCardsPanel.class.getName()).log(Level.SEVERE, null, ex);
                    return;
                }
            }

            Helpers.setPreScaledIconLabel(lights_label, GameFrame.getInstance().getCapa_brillo().getBrightness() == 0f ? lights_on_icon : lights_off_icon);
        });
    }

    private void setHandBackground(Color color) {
        Helpers.GUIRun(() -> {
            getHand_panel().setBackground(color);

            getHand_panel().revalidate();
            getHand_panel().repaint();
        });
    }

    public void last_hand_on() {
        GameFrame.getInstance().getCrupier().setLast_hand(true);

        Helpers.GUIRun(() -> {
            last_hand_label.setText(Translator.translate(GameFrame.getInstance().getCrupier().isForce_recover() ? "game.parada_programada_al_terminar_esta" : "game.ultima_mano_3"));
            getHand_panel().setOpaque(true);
            setHandBackground(Color.YELLOW);
            getHand_label().setForeground(Color.BLACK);
            getHand_label().setToolTipText(Translator.translate(GameFrame.getInstance().getCrupier().isForce_recover() ? "game.parada_programada_al_terminar_esta" : "game.ultima_mano_3"));
            getLast_hand_panel().setVisible(true);
            GameFrame.getInstance().getLast_hand_menu().setSelected(true);
            Helpers.TapetePopupMenu.LAST_HAND_MENU.setSelected(true);
            revalidate();
            repaint();
        });

        if (GameFrame.ultimaManoSonidoOn()) {
            Audio.playWavResource("misc/last_hand_on.wav");
        }

    }

    public void last_hand_off() {

        GameFrame.getInstance().getCrupier().setLast_hand(false);

        Helpers.GUIRun(() -> {
            getHand_panel().setOpaque(false);
            getHand_label().setForeground(color_contadores);
            getHand_label().setToolTipText(null);
            getLast_hand_panel().setVisible(false);

            if (GameFrame.MANOS != -1 && GameFrame.getInstance().getCrupier().getMano() > GameFrame.MANOS) {
                setHandBackground(Color.red);
                getHand_label().setForeground(Color.WHITE);
            }
            GameFrame.getInstance().getLast_hand_menu().setSelected(false);
            Helpers.TapetePopupMenu.LAST_HAND_MENU.setSelected(false);

            revalidate();
            repaint();
        });

        if (GameFrame.ultimaManoSonidoOn()) {
            Audio.playWavResource("misc/last_hand_off.wav");
        }

    }

    public void hand_label_left_click() {
        Helpers.GUIRun(() -> {
            hand_label_click_type = 1;
            hand_labelMouseClicked(null);
        });
    }

    public void hand_label_right_click() {
        Helpers.GUIRun(() -> {
            hand_label_click_type = 2;
            hand_labelMouseClicked(null);
        });
    }

    public JSpinner getHand_limit_spinner() {
        return hand_limit_spinner;
    }

    public JButton getMax_hands_button() {
        return max_hands_button;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        bet_label = new javax.swing.JLabel();
        tiempo_partida = new javax.swing.JLabel();
        sound_icon = new javax.swing.JLabel();
        settings_icon = new javax.swing.JLabel();
        panel_barra = new javax.swing.JPanel();
        barra_tiempo = new javax.swing.JProgressBar();
        cards_panel = new javax.swing.JPanel();
        flop3 = new com.tonikelope.coronapoker.Card();
        river = new com.tonikelope.coronapoker.Card();
        flop2 = new com.tonikelope.coronapoker.Card();
        turn = new com.tonikelope.coronapoker.Card();
        flop1 = new com.tonikelope.coronapoker.Card();
        pause_button = new javax.swing.JButton();
        hand_limit_spinner = new javax.swing.JSpinner();
        max_hands_button = new javax.swing.JButton();
        lights_label = new javax.swing.JLabel();
        pot_panel = new RoundedPanel(20);
        pot_label = new javax.swing.JLabel();
        last_hand_panel = new RoundedPanel(20);
        last_hand_label = new javax.swing.JLabel();
        hand_panel = new RoundedPanel(20);
        hand_label = new javax.swing.JLabel();
        blinds_panel = new RoundedPanel(20);
        blinds_label = new javax.swing.JLabel();

        setFocusable(false);
        setOpaque(false);

        bet_label.setFont(new java.awt.Font("Dialog", 1, 30)); // NOI18N
        bet_label.setForeground(new java.awt.Color(153, 204, 0));
        bet_label.setText(" ");
        bet_label.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        bet_label.setFocusable(false);

        tiempo_partida.setFont(new java.awt.Font("Monospaced", 1, 28)); // NOI18N
        tiempo_partida.setForeground(new java.awt.Color(153, 204, 0));
        tiempo_partida.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        tiempo_partida.setText("00:00:00");
        tiempo_partida.setFocusable(false);

        sound_icon.putClientProperty("i18n.tooltip_key", "ui.click_para_activar_desactivar_sonido");
        sound_icon.putClientProperty("i18n.tooltip_key", "ui.click_para_activar_desactivar_sonido");
        sound_icon.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        sound_icon.setFocusable(false);
        sound_icon.setPreferredSize(new java.awt.Dimension(30, 30));
        sound_icon.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                sound_iconMouseClicked(evt);
            }
        });

        // Gear icon to the left of the speaker: opens the "Game settings" dialog
        // (twin of the Preferences menu item and the table popup).
        settings_icon.putClientProperty("i18n.tooltip_key", "settings.ajustes_partida");
        settings_icon.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        settings_icon.setFocusable(false);
        settings_icon.setPreferredSize(new java.awt.Dimension(30, 30));
        settings_icon.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                settings_iconMouseClicked(evt);
            }
        });

        panel_barra.setFocusable(false);
        panel_barra.setOpaque(false);

        barra_tiempo.setFocusable(false);
        barra_tiempo.setMinimumSize(new java.awt.Dimension(1, 1));
        barra_tiempo.setPreferredSize(new Dimension(-1, (int) Math.round((float) pot_label.getHeight() * 0.65)));

        javax.swing.GroupLayout panel_barraLayout = new javax.swing.GroupLayout(panel_barra);
        panel_barra.setLayout(panel_barraLayout);
        panel_barraLayout.setHorizontalGroup(
                panel_barraLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(barra_tiempo, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        panel_barraLayout.setVerticalGroup(
                panel_barraLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(barra_tiempo, javax.swing.GroupLayout.DEFAULT_SIZE, 2, Short.MAX_VALUE)
        );

        cards_panel.setFocusable(false);
        cards_panel.setOpaque(false);

        javax.swing.GroupLayout cards_panelLayout = new javax.swing.GroupLayout(cards_panel);
        cards_panel.setLayout(cards_panelLayout);
        cards_panelLayout.setHorizontalGroup(
                cards_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(cards_panelLayout.createSequentialGroup()
                                .addGap(0, 0, Short.MAX_VALUE)
                                .addComponent(flop1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(flop2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(flop3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(turn, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(river, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(0, 0, Short.MAX_VALUE))
        );
        cards_panelLayout.setVerticalGroup(
                cards_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(cards_panelLayout.createSequentialGroup()
                                .addGroup(cards_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(flop2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(flop3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(turn, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(river, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(flop1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGap(0, 0, 0))
        );

        pause_button.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        pause_button.setText("PAUSAR");
        pause_button.putClientProperty("i18n.key", "ui.pausar");
        pause_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        pause_button.setFocusable(false);
        pause_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pause_buttonActionPerformed(evt);
            }
        });

        hand_limit_spinner.setFont(new java.awt.Font("Dialog", 1, 22)); // NOI18N
        hand_limit_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        hand_limit_spinner.setFocusable(false);

        max_hands_button.setFont(new java.awt.Font("Dialog", 1, 20)); // NOI18N
        max_hands_button.setText("OK");
        max_hands_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        max_hands_button.setFocusable(false);
        max_hands_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                max_hands_buttonActionPerformed(evt);
            }
        });

        lights_label.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        lights_label.setText(" ");
        lights_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        lights_label.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                lights_labelMouseReleased(evt);
            }
        });

        pot_panel.setOpaque(false);

        pot_label.setFont(new java.awt.Font("Dialog", 1, 30)); // NOI18N
        pot_label.setForeground(new java.awt.Color(153, 204, 0));
        pot_label.setText(" ");
        pot_label.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 5, 1, 5));
        pot_label.setFocusable(false);

        javax.swing.GroupLayout pot_panelLayout = new javax.swing.GroupLayout(pot_panel);
        pot_panel.setLayout(pot_panelLayout);
        pot_panelLayout.setHorizontalGroup(
                pot_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(pot_panelLayout.createSequentialGroup()
                                .addGap(0, 0, 0)
                                .addComponent(pot_label, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addGap(0, 0, 0))
        );
        pot_panelLayout.setVerticalGroup(
                pot_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(pot_panelLayout.createSequentialGroup()
                                .addGap(0, 0, 0)
                                .addComponent(pot_label)
                                .addGap(0, 0, 0))
        );

        last_hand_panel.setBackground(new java.awt.Color(255, 255, 0));

        last_hand_label.setBackground(new java.awt.Color(255, 255, 0));
        last_hand_label.setFont(new java.awt.Font("Dialog", 1, 20)); // NOI18N
        last_hand_label.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        last_hand_label.setText(Translator.translate("game.ultima_mano_3"));
        last_hand_label.putClientProperty("i18n.key", "game.ultima_mano_3");
        last_hand_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        last_hand_label.setFocusable(false);
        last_hand_label.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                last_hand_labelMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout last_hand_panelLayout = new javax.swing.GroupLayout(last_hand_panel);
        last_hand_panel.setLayout(last_hand_panelLayout);
        last_hand_panelLayout.setHorizontalGroup(
                last_hand_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(last_hand_panelLayout.createSequentialGroup()
                                .addGap(0, 0, 0)
                                .addComponent(last_hand_label, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addGap(0, 0, 0))
        );
        last_hand_panelLayout.setVerticalGroup(
                last_hand_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(last_hand_panelLayout.createSequentialGroup()
                                .addGap(0, 0, 0)
                                .addComponent(last_hand_label)
                                .addGap(0, 0, 0))
        );

        hand_panel.setOpaque(false);

        hand_label.setFont(new java.awt.Font("Dialog", 1, 26)); // NOI18N
        hand_label.setForeground(new java.awt.Color(153, 204, 0));
        hand_label.setText(" ");
        hand_label.putClientProperty("i18n.tooltip_key", "tooltip.hand_label_actions");
        hand_label.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        hand_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        hand_label.setFocusable(false);
        hand_label.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                hand_labelMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout hand_panelLayout = new javax.swing.GroupLayout(hand_panel);
        hand_panel.setLayout(hand_panelLayout);
        hand_panelLayout.setHorizontalGroup(
                hand_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(hand_panelLayout.createSequentialGroup()
                                .addGap(0, 0, 0)
                                .addComponent(hand_label)
                                .addGap(0, 0, 0))
        );
        hand_panelLayout.setVerticalGroup(
                hand_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(hand_panelLayout.createSequentialGroup()
                                .addGap(0, 0, 0)
                                .addComponent(hand_label)
                                .addGap(0, 0, 0))
        );

        blinds_label.setFont(new java.awt.Font("Dialog", 1, 26)); // NOI18N
        blinds_label.setForeground(new java.awt.Color(153, 204, 0));
        blinds_label.setText(" ");
        blinds_label.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        blinds_label.setFocusable(false);
        blinds_label.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                blinds_labelMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout blinds_panelLayout = new javax.swing.GroupLayout(blinds_panel);
        blinds_panel.setLayout(blinds_panelLayout);
        blinds_panelLayout.setHorizontalGroup(
                blinds_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(blinds_panelLayout.createSequentialGroup()
                                .addGap(0, 0, 0)
                                .addComponent(blinds_label)
                                .addGap(0, 0, 0))
        );
        blinds_panelLayout.setVerticalGroup(
                blinds_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(blinds_panelLayout.createSequentialGroup()
                                .addGap(0, 0, 0)
                                .addComponent(blinds_label)
                                .addGap(0, 0, 0))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(pot_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(bet_label))
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(settings_icon, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(sound_icon, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(blinds_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(12, 12, 12)
                                .addComponent(pause_button)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(lights_label)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(tiempo_partida)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(hand_limit_spinner)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(max_hands_button)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(hand_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addContainerGap())
                        .addComponent(panel_barra, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(cards_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(last_hand_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(bet_label)
                                        .addComponent(pot_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(last_hand_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(12, 12, 12)
                                .addComponent(cards_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addGroup(layout.createSequentialGroup()
                                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                                                .addComponent(pause_button)
                                                                .addComponent(hand_limit_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                                .addComponent(max_hands_button)
                                                                .addComponent(lights_label))
                                                        .addComponent(hand_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                        .addComponent(blinds_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                        .addComponent(tiempo_partida, javax.swing.GroupLayout.Alignment.TRAILING))
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(panel_barra, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                        .addGroup(layout.createSequentialGroup()
                                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                                        .addComponent(settings_icon, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                        .addComponent(sound_icon, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                                .addGap(0, 0, Short.MAX_VALUE)))
                                .addGap(0, 0, 0))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void sound_iconMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_sound_iconMouseClicked
        // TODO add your handling code here:

        if (!Helpers.isRealClick(evt)) {
            return;
        }

        GameFrame.setSonidos(!GameFrame.SONIDOS);
    }//GEN-LAST:event_sound_iconMouseClicked

    private void settings_iconMouseClicked(java.awt.event.MouseEvent evt) {
        if (!Helpers.isRealClick(evt)) {
            return;
        }

        // Opens the unified "Settings" dialog (Appearance / Audio / Game tabs).
        GameFrame.getInstance().openSettingsDialog();
    }

    private void hand_labelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_hand_labelMouseClicked
        // TODO add your handling code here:

        // evt == null is the programmatic invocation (hand_label_left_click /
        // hand_label_right_click, which set hand_label_click_type): let it through. For a real
        // event we require the button to have been released INSIDE the component (migrated to
        // mouseReleased so a tiny drag doesn't drop the click).
        if (evt != null && !Helpers.isReleaseInsideComponent(evt)) {
            return;
        }

        if (GameFrame.getInstance().isPartida_local() && getHand_label().isEnabled()) {

            if ((evt == null && hand_label_click_type == 1) || (evt != null && SwingUtilities.isLeftMouseButton(evt))) {

                getHand_panel().setEnabled(false);

                boolean is_recover = (GameFrame.getInstance().getCrupier().isForce_recover() || (evt != null && (evt.getModifiersEx() & MouseEvent.SHIFT_DOWN_MASK) != 0));

                if (GameFrame.MANOS == GameFrame.getInstance().getCrupier().getMano() || GameFrame.getInstance().getCrupier().isLast_hand() || Helpers.mostrarMensajeInformativoSINO(GameFrame.getInstance(), Translator.translate(is_recover ? "game.programar_parada_al_terminar_esta" : "game.ultima_mano_2"), new ImageIcon(getClass().getResource(is_recover ? "/images/stop.png" : "/images/exit.png"))) == 0) {

                    Helpers.threadRun(() -> {
                        if (!GameFrame.getInstance().getCrupier().isLast_hand()) {

                            if (evt != null && (evt.getModifiersEx() & MouseEvent.SHIFT_DOWN_MASK) != 0) {
                                GameFrame.getInstance().getCrupier().setForce_recover(true);
                            }

                            try {
                                GameFrame.getInstance().getCrupier().broadcastGAMECommandFromServer("LASTHAND#" + (GameFrame.getInstance().getCrupier().isForce_recover() ? "2" : "1") + (WaitingRoomFrame.getInstance().getPassword() != null ? "#" + Base64.getEncoder().encodeToString(WaitingRoomFrame.getInstance().getPassword().getBytes("UTF-8")) : ""), null);
                            } catch (UnsupportedEncodingException ex) {
                                Logger.getLogger(CommunityCardsPanel.class.getName()).log(Level.SEVERE, null, ex);
                            }
                            last_hand_on();

                        } else {
                            GameFrame.getInstance().getCrupier().setForce_recover(false);
                            GameFrame.getInstance().getCrupier().broadcastGAMECommandFromServer("LASTHAND#0", null);
                            last_hand_off();
                        }
                        Helpers.GUIRun(() -> {
                            getHand_panel().setEnabled(true);
                        });
                    });

                } else {
                    GameFrame.getInstance().getCrupier().setForce_recover(false);
                    getHand_panel().setEnabled(true);
                    GameFrame.getInstance().getLast_hand_menu().setSelected(false);
                    Helpers.TapetePopupMenu.LAST_HAND_MENU.setSelected(false);
                }

            } else if ((evt == null && hand_label_click_type == 2) || (evt != null && SwingUtilities.isRightMouseButton(evt))) {

                click_max_hands();

            }

        }
    }//GEN-LAST:event_hand_labelMouseClicked

    private void click_max_hands() {

        if (getHand_limit_spinner().isVisible()) {

            getHand_limit_spinner().setVisible(false);

            max_hands_button.setVisible(false);

            tiempo_partida.setVisible(GameFrame.SHOW_CLOCK);

            getHand_panel().setEnabled(false);

            int manos = (int) (GameFrame.getInstance().getTapete().getCommunityCards().getHand_limit_spinner().getValue());

            int old_manos = GameFrame.MANOS;

            if (manos == 0) {
                GameFrame.MANOS = -1;

            } else if (GameFrame.getInstance().getCrupier().getMano() < manos) {

                GameFrame.MANOS = manos;
            }

            GameFrame.getInstance().getTapete().getCommunityCards().getHand_limit_spinner().setVisible(false);

            if (GameFrame.MANOS != old_manos) {

                Helpers.threadRun(() -> {
                    GameFrame.getInstance().getCrupier().broadcastGAMECommandFromServer("MAXHANDS#" + String.valueOf(GameFrame.MANOS), null);
                    GameFrame.getInstance().getCrupier().actualizarContadoresTapete();
                    Helpers.GUIRun(() -> {
                        getHand_panel().setEnabled(true);
                    });
                });

            } else {
                getHand_panel().setEnabled(true);
            }

        } else {

            tiempo_partida.setVisible(false);

            getHand_limit_spinner().setModel(new SpinnerNumberModel(GameFrame.MANOS != -1 ? GameFrame.MANOS : 0, 0, null, 1));

            Helpers.makeNumericSpinnerEditable(getHand_limit_spinner(), false);

            getHand_limit_spinner().setVisible(true);

            getMax_hands_button().setVisible(true);
        }
    }

    public JButton getPause_button() {
        return pause_button;
    }

    private void pause_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pause_buttonActionPerformed
        // TODO add your handling code here:

        int pause_now = -2;

        if (!GameFrame.getInstance().getCrupier().isIwtsthing() && !(GameFrame.getInstance().getCrupier().isLast_hand() && GameFrame.getInstance().getCrupier().isShow_time()) && (GameFrame.getInstance().isPartida_local()) && !GameFrame.getInstance().isTimba_pausada() && !GameFrame.getInstance().getLocalPlayer().isTurno() && !GameFrame.getInstance().getLocalPlayer().isAuto_pause() && !GameFrame.getInstance().getLocalPlayer().isSpectator()) {

            // The host can pause whenever they want during the game: no "pause right now?"
            // confirmation popup, it pauses IMMEDIATELY (as if "Yes" had been clicked). The
            // rest of the button's logic (clients auto-pausing on their own turn, etc.) is
            // unchanged; pause_now == 0 triggers the immediate-pause branch below.
            pause_now = 0;

        } else if (GameFrame.getInstance().getCrupier().isIwtsthing()) {

            pause_now = 0;
        }

        if (!(!GameFrame.getInstance().isPartida_local() && (GameFrame.getInstance().getCrupier().isIwtsthing() || GameFrame.getInstance().getCrupier().isIwtsthing_request())) && pause_now < 1 && !GameFrame.getInstance().getLocalPlayer().isAuto_pause() && ((!GameFrame.getInstance().isPartida_local() && pause_now == 0) || (GameFrame.getInstance().getLocalPlayer().isTurno() && pause_now == -2) || (GameFrame.getInstance().isPartida_local() && ((GameFrame.getInstance().getCrupier().isLast_hand() && GameFrame.getInstance().getCrupier().isShow_time()) || GameFrame.getInstance().isTimba_pausada() || pause_now == 0 || GameFrame.getInstance().getLocalPlayer().isSpectator())))) {

            getPause_button().setBackground(null);
            getPause_button().setForeground(null);

            if (!GameFrame.getInstance().isTimba_pausada() && !GameFrame.getInstance().isPartida_local()) {

                GameFrame.getInstance().getLocalPlayer().setPause_counter(GameFrame.getInstance().getLocalPlayer().getPause_counter() - 1);
                Helpers.setScaledIconButton(getPause_button(), getClass().getResource("/images/pause.png"), Math.round(0.6f * getPause_button().getHeight()), Math.round(0.6f * getPause_button().getHeight()));
                getPause_button().setText(Translator.translate("game.pausar") + " (" + GameFrame.getInstance().getLocalPlayer().getPause_counter() + ")");
            }

            getPause_button().setEnabled(false);

            Helpers.threadRun(() -> {
                GameFrame.getInstance().pauseTimba(GameFrame.getInstance().isPartida_local() ? null : GameFrame.getInstance().getLocalPlayer().getNickname());
            });

        } else if (!GameFrame.getInstance().getLocalPlayer().isSpectator()) {

            if (!GameFrame.getInstance().getLocalPlayer().isAuto_pause()) {

                getPause_button().setBackground(Color.WHITE);
                getPause_button().setForeground(new Color(255, 102, 0));
                GameFrame.getInstance().getLocalPlayer().setAuto_pause(true);
                if (GameFrame.interruptorSonidoOn()) {
                    Audio.playWavResource("misc/button_on.wav");
                }

                if (!GameFrame.getInstance().getLocalPlayer().isAuto_pause_warning()) {
                    GameFrame.getInstance().getLocalPlayer().setAuto_pause_warning(true);
                    Helpers.mostrarMensajeInformativo(GameFrame.getInstance(), Translator.translate("game.pausa_programada_para_tu_proximo"), new ImageIcon(getClass().getResource("/images/pause.png")));
                }

            } else {
                getPause_button().setBackground(null);
                getPause_button().setForeground(null);
                GameFrame.getInstance().getLocalPlayer().setAuto_pause(false);
                if (GameFrame.interruptorSonidoOn()) {
                    Audio.playWavResource("misc/button_off.wav");
                }
            }
        }

    }//GEN-LAST:event_pause_buttonActionPerformed

    private void last_hand_labelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_last_hand_labelMouseClicked
        // TODO add your handling code here:
        hand_labelMouseClicked(evt);
    }//GEN-LAST:event_last_hand_labelMouseClicked

    private void max_hands_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_max_hands_buttonActionPerformed
        // TODO add your handling code here:

        click_max_hands();

    }//GEN-LAST:event_max_hands_buttonActionPerformed

    private void lights_labelMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_lights_labelMouseReleased
        // TODO add your handling code here:

        // The lights button is disabled during a pause: ignore both the click (a disabled
        // JLabel shouldn't receive it, but just in case) and the Alt+L shortcut, which comes
        // in through lightsButtonClick with evt == null.
        if (!lights_label.isEnabled()) {
            return;
        }

        // The switch only occupies the left part of its label, so the click only counts inside
        // the drawing. It measures the icon that's ACTUALLY SET rather than recomputing the
        // formula it was scaled with (which lives in refreshLightsIcon), so they can't diverge.
        // With no icon yet there's no switch to click; the shortcut (evt == null) still applies.
        final javax.swing.Icon lights_icon = lights_label.getIcon();

        if (evt == null || (lights_icon != null && new Rectangle(lights_icon.getIconWidth(), lights_icon.getIconHeight()).contains(evt.getPoint()))) {
            // Toggles what the PLAYER has requested, not the veil currently shown: if the game
            // is forcing the veil on its own (a pause, a game-over), the switch keeps
            // representing the player's choice and doesn't end up inverted once that veil lifts.
            if (GameFrame.getInstance().getCapa_brillo().isUserLightsOff()) {

                if (GameFrame.interruptorSonidoOn()) {
                    Audio.playWavResource("misc/button_on.wav");
                }
                GameFrame.getInstance().getCapa_brillo().lightsON();

            } else {

                if (GameFrame.interruptorSonidoOn()) {
                    Audio.playWavResource("misc/button_off.wav");
                }
                GameFrame.getInstance().getCapa_brillo().lightsOFF();
            }

            applyLightsVisuals();
        }

    }//GEN-LAST:event_lights_labelMouseReleased

    private void blinds_labelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_blinds_labelMouseClicked
        if (!Helpers.isRealClick(evt)) {
            return;
        }

        // Clicking the blinds on the table should do nothing: blinds are now edited from the
        // "Game" tab of the unified settings dialog.
    }//GEN-LAST:event_blinds_labelMouseClicked

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JProgressBar barra_tiempo;
    private javax.swing.JLabel bet_label;
    private javax.swing.JLabel blinds_label;
    private javax.swing.JPanel blinds_panel;
    private javax.swing.JPanel cards_panel;
    private com.tonikelope.coronapoker.Card flop1;
    private com.tonikelope.coronapoker.Card flop2;
    private com.tonikelope.coronapoker.Card flop3;
    private javax.swing.JLabel hand_label;
    private javax.swing.JSpinner hand_limit_spinner;
    private javax.swing.JPanel hand_panel;
    private javax.swing.JLabel last_hand_label;
    private javax.swing.JPanel last_hand_panel;
    private javax.swing.JLabel lights_label;
    private javax.swing.JButton max_hands_button;
    private javax.swing.JPanel panel_barra;
    private javax.swing.JButton pause_button;
    private javax.swing.JLabel pot_label;
    private javax.swing.JPanel pot_panel;
    private com.tonikelope.coronapoker.Card river;
    private javax.swing.JLabel sound_icon;
    private javax.swing.JLabel settings_icon;
    private javax.swing.JLabel tiempo_partida;
    private com.tonikelope.coronapoker.Card turn;
    // End of variables declaration//GEN-END:variables

    private void zoomIcons() {

        Helpers.threadRun(() -> {

            // awaitFirstLayout (no while-pausar polling): blocks off-EDT with a one-shot
            // ComponentListener until pot_label's height is > 0. Keeps the threadRun + lock
            // pattern because zoom_lock is also taken by zoom() (below), which calls
            // GUIRunAndWait — taking the lock from the EDT here could deadlock with that path.
            try {
                Helpers.awaitFirstLayout(pot_label);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }

            synchronized (zoom_lock) {
                Helpers.GUIRunAndWait(() -> {
                    sound_icon.setPreferredSize(new Dimension(blinds_label.getHeight(), blinds_label.getHeight()));
                    Helpers.setScaledIconLabel(sound_icon, getClass().getResource(GameFrame.SONIDOS ? "/images/sound.png" : "/images/mute.png"), blinds_label.getHeight(), blinds_label.getHeight());
                    settings_icon.setPreferredSize(new Dimension(blinds_label.getHeight(), blinds_label.getHeight()));
                    Helpers.setScaledWhiteIconLabel(settings_icon, getClass().getResource("/images/menu/gear.png"), blinds_label.getHeight(), blinds_label.getHeight());
                    panel_barra.setPreferredSize(new Dimension(-1, (int) Math.round((float) blinds_label.getHeight() * 0.7f)));
                    sound_icon.setVisible(true);
                    settings_icon.setVisible(true);
                    panel_barra.setVisible(true);
                    Helpers.setScaledIconButton(pause_button, getClass().getResource("/images/pause.png"), Math.round(0.6f * pause_button.getHeight()), Math.round(0.6f * pause_button.getHeight()));
                    Helpers.setScaledIconLabel(pot_label, getClass().getResource("/images/pot.png"), pot_label.getHeight(), pot_label.getHeight());
                    // bet_label has no icon (shows only the street). To bring it back: setScaledIconLabel(bet_label, "/images/pot.png", ...).
                    refreshLightsIcon();
                    Helpers.setScaledIconLabel(blinds_label, getClass().getResource("/images/ciegas_big.png"), Math.round(0.8f * pot_label.getHeight() * (342f / 256)), Math.round(0.8f * pot_label.getHeight()));
                    refreshStraddleIcon();
                    // Re-fits the pot font to the new zoom: the icon is already set and the
                    // community-cards layout already redone, so if a long side-pot breakdown
                    // doesn't fit at base size, it shrinks.
                    setPotLabelTextFitted(pot_label.getText());
                });
            }
        });

    }

    @Override
    public void zoom(float zoom_factor, final ConcurrentLinkedQueue<Long> notifier) {

        if (!ready) {
            try {
                if (!ready_latch.await(2, TimeUnit.SECONDS)) {
                    Logger.getLogger(CommunityCardsPanel.class.getName()).log(Level.WARNING,
                            "Community cards panel was not laid out before zoom timeout");
                    notifyZoomCompletion(notifier);
                    return;
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                Helpers.logCooperativeCancellation(Logger.getLogger(CommunityCardsPanel.class.getName()),
                        "community cards layout wait", ex);
                notifyZoomCompletion(notifier);
                return;
            }
        }

        final ConcurrentLinkedQueue<Long> mynotifier = new ConcurrentLinkedQueue<>();

        ZoomableInterface[] zoomables = new ZoomableInterface[]{flop1, flop2, flop3, turn, river};

        for (ZoomableInterface zoomable : zoomables) {
            Helpers.threadRun(() -> {
                zoomable.zoom(zoom_factor, mynotifier);
            });
        }

        synchronized (zoom_lock) {

            Helpers.GUIRunAndWait(() -> {
                if (icon_zoom_timer.isRunning()) {
                    icon_zoom_timer.stop();
                }

                sound_icon.setVisible(false);
                settings_icon.setVisible(false);
                panel_barra.setVisible(false);
                pause_button.setIcon(null);
                pot_label.setIcon(null);
                bet_label.setIcon(null);
                lights_label.setIcon(null);
                blinds_label.setIcon(null);

                // If auto-fit had the pot font shrunk, restore its base font BEFORE zoomFonts
                // so it scales the real font, not the already-shrunk one. zoomIcons does the
                // re-fit for the new zoom once the community-cards layout is redone.
                if (pot_orig_font != null) {
                    pot_label.setFont(pot_orig_font);
                    pot_orig_font = null;
                }
            });

            Helpers.zoomFonts(this, zoom_factor, null);

            Helpers.GUIRun(() -> {
                if (icon_zoom_timer.isRunning()) {
                    icon_zoom_timer.restart();
                } else {
                    icon_zoom_timer.start();
                }

                revalidate();
                repaint();
            });
        }

        synchronized (mynotifier) {
            while (mynotifier.size() < zoomables.length) {
                try {
                    mynotifier.wait(1000);
                } catch (InterruptedException ex) {
                    Helpers.logCooperativeCancellation(Logger.getLogger(CommunityCardsPanel.class.getName()),
                            "community cards zoom notifier wait", ex);
                    break;
                }
            }
        }

        notifyZoomCompletion(notifier);
    }

    private void notifyZoomCompletion(ConcurrentLinkedQueue<Long> notifier) {
        if (notifier != null) {
            notifier.add(Thread.currentThread().threadId());

            synchronized (notifier) {
                notifier.notifyAll();
            }
        }
    }
}
