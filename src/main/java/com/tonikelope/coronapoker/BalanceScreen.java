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

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * Full-screen end-of-session overlay mounted on {@link GameFrame}'s glassPane, on top of the
 * real felt (seen through via Swing component transparency, not OS compositor window
 * transparency like the old modal dialog). Layout top to bottom: button bar, title + date, the
 * local player's result in giant text with the net amount below, and a horizontal carousel of
 * player result cards. Fully responsive. GameFrame mounts/unmounts it and awaits the user's
 * choice (continue / main menu) via a CountDownLatch, replacing the old modal dialog without
 * using a window.
 *
 * @author tonikelope
 */
public class BalanceScreen extends JPanel {

    // Fill color for the giant result text, keyed to net balance (border is always black).
    private static final Color WIN = new Color(0, 200, 60);
    private static final Color LOSE = new Color(220, 30, 30);
    private static final Color NEUTRAL = new Color(140, 140, 140);

    // Light card colors on the dark felt, per design reference.
    private static final Color CARD_BG = new Color(248, 248, 248);
    private static final Color CARD_TEXT = new Color(25, 25, 25);
    private static final Color CARD_TEXT_DIM = new Color(110, 110, 110);

    private volatile boolean recover = false;

    // Mute speaker chip next to the nav bar. No settings gear on purpose: the session is over,
    // only the global mute matters here. Sized square to the buttons' height by
    // normalizeNavButtons(), and the icon size is derived from that height.
    private static final int SOUND_ICON_SZ = 36;
    private JLabel sound_icon;
    private JComponent sound_chip;
    private int sound_icon_sz = SOUND_ICON_SZ;

    // Callback GameFrame installs to release its CountDownLatch when the player exits (continue
    // / main menu), replacing the old modal dialog's close.
    private final Runnable on_close;

    // Sizes derived from screen height so everything scales with resolution.
    private int screen_w;
    private int screen_h;
    private int card_w;
    private int card_h;
    private int card_gap;
    private int avatar_sz;

    private JScrollPane cards_scroll;
    private ArrowButton left_arrow;
    private ArrowButton right_arrow;
    private final java.util.List<CardPanel> card_panels = new java.util.ArrayList<>();

    // The 4 top-bar buttons, kept here so normalizeNavButtons() can give them all an identical
    // size after the responsive auto-fit.
    private final java.util.List<JButton> nav_buttons = new java.util.ArrayList<>();

    // Local player's amount animation: a counter that rolls from total buyin to final stack
    // (video-game score-count style), then reveals as +/- net. Only when there's a win/loss (not
    // on a tie).
    private OutlinedLabel amount_label;

    // Swing timers for the amount animation (roll + reveal blink). Kept so they can be stopped
    // outright on exit: their per-frame repaints are expensive (OutlinedLabel recomputes the text
    // outline via TextLayout.getOutline at full-screen size on every tick), and if left running
    // after dispose they starve the EDT and delay teardown (RESET_GAME) until they finish on
    // their own. Stopping them makes exit instantaneous.
    private javax.swing.Timer amount_roll_timer;
    private javax.swing.Timer amount_blink_timer;

    // End-of-session screenshot (GameFrame.SCREENSHOT_FIN_TIMBA, on by default). Taken right when
    // the money counter finishes (end of the +/- reveal blink); if the player exits earlier - via
    // either button, or on a tie with no counter - it's taken on click instead. One shot per
    // session: this flag guards idempotency, starts false on every new BalanceScreen instance,
    // and is only touched on the EDT, so no state leaks across sessions and there's nothing to
    // synchronize.
    private boolean screenshot_done = false;

    // The three stacked pieces of the center strip, kept so finalizeCenterSizing() can fix their
    // min/max height once fonts are final: the title block stays rigid (so the date line never
    // gets clipped at low resolutions), while the hero message and amount absorb any vertical
    // deficit by shrinking (OutlinedLabel auto-rescales to whatever box it's given).
    private JComponent title_block;
    private JComponent hero_label;
    private JComponent amount_component;

    private double anim_buyin;
    private double anim_stack;
    private double anim_ganancia;

    /**
     * @return {@code true} if the player chose to continue/reconnect rather than go to the main
     * menu.
     */
    public boolean isRecover() {
        return recover;
    }

    /**
     * Builds the end-of-session overlay for the given parent frame.
     *
     * @param parent screen used to size the overlay (falls back to 1280x800 if unavailable)
     * @param on_close callback invoked once the player picks continue or main menu
     */
    public BalanceScreen(java.awt.Frame parent, Runnable on_close) {
        super();

        this.on_close = on_close;

        // Preload the counter SFX off-EDT so playback starts instantly on an already-open line
        // when startAmountAnimation() runs. Opening a fresh line at animation time can stall if
        // the audio device is busy (e.g. right after the previous table's audio teardown),
        // leaving the animation silent or the sound arriving late. Building the overlay gives the
        // line plenty of time to open first. Same pattern as shuffle.wav.
        Helpers.threadRun(() -> Audio.preloadWav("misc/balance_count.wav"));

        // Transparent: the background is the real felt, seen through the glassPane.
        setOpaque(false);
        setLayout(new BorderLayout());

        screen_w = (parent != null && parent.getWidth() > 0) ? parent.getWidth() : 1280;
        screen_h = (parent != null && parent.getHeight() > 0) ? parent.getHeight() : 800;

        card_h = Math.max(170, Math.min(320, Math.round(screen_h * 0.30f)));
        card_w = Math.round(card_h * 0.80f);
        avatar_sz = Math.round(card_h * 0.42f);
        card_gap = 18;

        // NORTH: button bar. CENTER: title + date over the giant result message. SOUTH: the
        // whole carousel (always gets its preferred height; CENTER absorbs any leftover space).
        add(buildNavBar(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildCardsRegion(), BorderLayout.SOUTH);

        Helpers.updateFonts(this, Helpers.GUI_FONT, null);

        Helpers.translateComponents(this, false);

        fitTaggedLabels(this);

        // Fonts are now final (updateFonts/fitTaggedLabels changed preferred sizes): fix the
        // center strip's vertical split so the date row never gets clipped at low resolutions.
        finalizeCenterSizing();

        normalizeNavButtons();

        normalizeCardHeights();

        SwingUtilities.invokeLater(this::updateArrows);
    }

    /**
     * Starts the amount roll animation. Call after mounting the overlay on the glassPane and
     * making it visible (previously done in windowOpened).
     */
    public void startAnimations() {
        startAmountAnimation();
    }

    /**
     * Releases overlay resources: stops the animation timers, releases the preloaded counter SFX
     * line, and closes the stats dialog if it was left open. Call once the user's choice is
     * resolved, before unmounting the glassPane (previously done in windowClosed).
     */
    public void cleanup() {
        stopAmountAnimation();
        Audio.closePreloadedWav("misc/balance_count.wav");
        StatsDialog.disposeIfOpen(true);
    }

    // -------------------------------------------------------------------------
    // Top button bar, spread across the width.
    // -------------------------------------------------------------------------
    private JComponent buildNavBar() {
        JButton log_button = navButton(Translator.translate("log.registro_de_la_timba"), scaledIcon("/images/menu/log2.png", 28));
        log_button.addActionListener((e) -> openLog());

        JButton stats_button = navButton(Translator.translate("ui.estadisticas"), scaledIcon("/images/stats.png", 28));
        stats_button.addActionListener((e) -> StatsDialog.showStats(this));

        JButton recover_button = navButton(GameFrame.getInstance().isPartida_local() ? Translator.translate("game.continuar_esta_timba") : Translator.translate("conn.reconectar_al_servidor"), scaledIcon("/images/continue.png", 28));
        recover_button.addActionListener((e) -> {
            // Screenshot fallback, same as the menu button: if the player exits before the money
            // counter finishes (or on a tie, which has no counter), take it here so the session
            // is still recorded (idempotent no-op if the counter already took it).
            takeBalanceScreenshot();
            stopAmountAnimation();
            recover = true;
            if (on_close != null) {
                on_close.run();
            }
        });

        JButton menu_button = navButton(Translator.translate("ui.menu_principal"), whiteScaledIcon("/images/exit2.png", 28));
        menu_button.addActionListener((e) -> {
            // Same screenshot fallback as the continue button. Render happens on the EDT while
            // the overlay is still mounted, before on_close; the file write happens on its own
            // thread.
            takeBalanceScreenshot();
            stopAmountAnimation();
            recover = false;
            if (on_close != null) {
                on_close.run();
            }
        });

        JPanel row = new JPanel(new GridLayout(1, 4, 24, 0));
        row.setOpaque(false);
        for (JButton b : new JButton[]{menu_button, log_button, stats_button, recover_button}) {
            nav_buttons.add(b);
            JPanel cell = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            cell.setOpaque(false);
            cell.add(b);
            row.add(cell);
        }

        // Mute speaker to the right of the nav buttons, same row and height: discreet, where
        // expected. No settings gear here, only mute.
        JPanel line = new JPanel(new BorderLayout(20, 0));
        line.setOpaque(false);
        line.add(row, BorderLayout.CENTER);
        line.add(buildSoundCorner(), BorderLayout.EAST);

        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(34, 28, 0, 28));
        bar.add(line, BorderLayout.NORTH);
        return bar;
    }

    // Quick-mute speaker, top-right, on a translucent (not fully transparent) rounded chip: gives
    // it a large hit area and a button affordance, and the white icon (sound.png/mute.png) stands
    // out against it. The listener is attached to both the icon and the chip, since a click on
    // the icon is delivered to the label, not the chip.
    private JComponent buildSoundCorner() {
        sound_icon = new JLabel();
        refreshBalanceSoundIcon();

        java.awt.event.MouseAdapter toggle = new java.awt.event.MouseAdapter() {
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                if (!Helpers.isRealClick(e)) {
                    return;
                }
                // setSonidos() flips + persists + mutes/unmutes (and refreshes any other speaker
                // icons); here we just refresh our own, which it doesn't know about.
                GameFrame.setSonidos(!GameFrame.SONIDOS);
                refreshBalanceSoundIcon();
            }
        };
        sound_icon.addMouseListener(toggle);

        // GridBagLayout centers the icon in the square chip (H and V). normalizeNavButtons() sets
        // the square size (= button height); built here with the same corner arc (24) as the
        // buttons so it reads as one of them.
        JPanel chip = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0, 0, 0, 90));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
                g2.dispose();
            }
        };
        chip.setOpaque(false);
        chip.setCursor(new Cursor(Cursor.HAND_CURSOR));
        chip.setToolTipText(Translator.translate("sound.click_para_activardesactivar_el_sonido"));
        chip.addMouseListener(toggle);
        chip.add(sound_icon);
        sound_chip = chip;

        // GridBagLayout centers the chip vertically within EAST (which spans the row's full
        // height), keeping it aligned with the buttons even if the row grows.
        JPanel corner = new JPanel(new GridBagLayout());
        corner.setOpaque(false);
        corner.add(chip);
        return corner;
    }

    // Reflects GameFrame.SONIDOS in the speaker icon (sound/mute), same as in-game. Size
    // (sound_icon_sz) is derived from button height by normalizeNavButtons().
    private void refreshBalanceSoundIcon() {
        Helpers.setScaledIconLabel(sound_icon, getClass().getResource(GameFrame.SONIDOS ? "/images/sound.png" : "/images/mute.png"), sound_icon_sz, sound_icon_sz);
    }

    private JButton navButton(String text, javax.swing.Icon icon) {
        // Glassmorphism style identical to the home-screen buttons (create/join): translucent
        // black glass with no color halo (accent = null) - each button used to pass its own
        // accent color, giving a different hover halo per button, which should read as uniform
        // instead. setUI before setBorder so our padding wins over the one installUI sets.
        JButton b = new JButton(text);
        b.setUI(new GlassButtonUI(null, false, false, 0.70f, 24));
        b.setForeground(Color.WHITE);
        b.setBorder(BorderFactory.createEmptyBorder(15, 26, 15, 26));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.setFont(new Font("Dialog", Font.BOLD, 22));
        if (icon != null) {
            b.setIcon(icon);
            b.setIconTextGap(9);
        }
        // Responsive auto-fit: text shrinks to fit its quarter of the bar (keeps buttons intact
        // on low resolutions).
        b.putClientProperty("fit.width", Math.max(40, screen_w / 4 - 118));
        return b;
    }

    // Scales an icon resource for the glass nav buttons.
    private static javax.swing.ImageIcon scaledIcon(String resource, int size) {
        try {
            java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(BalanceScreen.class.getResource(resource));
            return new javax.swing.ImageIcon(src.getScaledInstance(size, size, java.awt.Image.SCALE_SMOOTH));
        } catch (Exception ex) {
            return null;
        }
    }

    // Same, but tints the silhouette white (keeping alpha): for dark-line icons that would be
    // invisible on the dark glass (e.g. the main-menu exit icon).
    private static javax.swing.ImageIcon whiteScaledIcon(String resource, int size) {
        try {
            java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(BalanceScreen.class.getResource(resource));
            java.awt.image.BufferedImage w = new java.awt.image.BufferedImage(src.getWidth(), src.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < src.getHeight(); y++) {
                for (int x = 0; x < src.getWidth(); x++) {
                    int a = (src.getRGB(x, y) >>> 24) & 0xFF;
                    w.setRGB(x, y, (a << 24) | 0x00FFFFFF);
                }
            }
            return new javax.swing.ImageIcon(w.getScaledInstance(size, size, java.awt.Image.SCALE_SMOOTH));
        } catch (Exception ex) {
            return null;
        }
    }

    private void openLog() {
        GameLogDialog log = GameFrame.getInstance().getRegistro_dialog();

        // Size is only forced the first time the log opens this session, same as the table menu
        // (isDefaultBoundsApplied): it's the same dialog, and forcing it again would overwrite a
        // resize the player already made.
        if (!log.isDefaultBoundsApplied()) {
            log.setPreferredSize(new Dimension(Math.round(0.7f * GameFrame.getInstance().getWidth()), Math.round(0.7f * GameFrame.getInstance().getHeight())));
            log.pack();
            log.setLocationRelativeTo(this);
            log.setDefaultBoundsApplied(true);
        }

        // The log dialog is shared with the table menu and is non-modal by default. It needs to
        // be modal here (the end screen sits above everything), but must be restored afterwards:
        // the menu only rebuilds it on a parent-window change, so leaving it modal would block
        // the table if reopened from there later in the same session. Restored only after it's
        // closed, which is when setModal takes effect.
        log.setModal(true);

        try {
            log.setVisible(true);
        } finally {
            log.setModal(false);
        }
    }

    // -------------------------------------------------------------------------
    // Center strip (between the button bar and the carousel).
    // -------------------------------------------------------------------------
    // Vertical BoxLayout with glue: leftover space is absorbed by the glue (centers the title
    // block, leaves room below the amount), while any DEFICIT at low resolutions is absorbed only
    // by the hero message and amount shrinking (they auto-rescale to their box) - the title block
    // stays rigid (min = preferred, set in finalizeCenterSizing()). The previous GridBagLayout
    // with weighty 1/0/1 let the deficit fall on the title row and clip the date (a plain JLabel
    // doesn't rescale).
    private JComponent buildCenter() {
        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        title_block = buildTitleBlock();
        title_block.setAlignmentX(Component.CENTER_ALIGNMENT);

        hero_label = buildHeroMessage();
        hero_label.setAlignmentX(Component.CENTER_ALIGNMENT);

        // The amount, same giant font, right below the message.
        amount_component = buildAmount();
        amount_component.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Leftover space split like the old GridBagLayout weighty 1/0/1: 1/4 above the title, 1/4
        // between title and message, 1/2 below the amount (two glues at the bottom) - same block
        // position as before at high resolution; only the deficit/low-res behavior changes.
        center.add(Box.createVerticalGlue());
        center.add(title_block);
        center.add(Box.createVerticalGlue());
        center.add(hero_label);
        center.add(amount_component);
        center.add(Box.createVerticalGlue());
        center.add(Box.createVerticalGlue());

        return center;
    }

    // Fixes the center strip's vertical split once fonts are final (after updateFonts/
    // fitTaggedLabels, which change preferred sizes). The title block is rigid (min = preferred)
    // so its date row never gets clipped; the hero message and amount can shrink (small min) and
    // absorb the deficit at low resolutions by rescaling. All keep an unbounded max width.
    private void finalizeCenterSizing() {
        if (title_block != null) {
            int h = title_block.getPreferredSize().height;
            title_block.setMinimumSize(new Dimension(0, h));
            title_block.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        }
        makeVerticallyShrinkable(hero_label);
        makeVerticallyShrinkable(amount_component);
    }

    // Never grows vertically (leftover space goes to the glue) but can shrink down to a
    // comfortable minimum: OutlinedLabel auto-rescales to whatever height it's given.
    private static void makeVerticallyShrinkable(JComponent c) {
        if (c == null) {
            return;
        }
        int h = c.getPreferredSize().height;
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        c.setMinimumSize(new Dimension(0, Math.min(h, Math.max(24, h / 4))));
    }

    // Title + date/time/duration: plain font (no outline), colored to match the felt's chosen
    // counter color (wood/black felts -> white, others -> their own color). Stacked full-width
    // with auto-fit for any resolution.
    private JComponent buildTitleBlock() {
        Color tapete_color = Color.WHITE;
        try {
            Color c = GameFrame.getInstance().getTapete().getCommunityCards().getColor_contadores();
            if (c != null) {
                tapete_color = c;
            }
        } catch (Exception ex) {
        }

        float subtitle_size = Math.max(22f, Math.min(90f, screen_h * 0.060f));
        float date_size = Math.max(16f, Math.min(48f, screen_h * 0.034f));
        int fit_w = Math.max(60, screen_w - 60);

        JLabel subtitle = new JLabel(Translator.translate("game.la_timba_ha_terminado"), SwingConstants.CENTER);
        subtitle.setForeground(tapete_color);
        subtitle.setFont(new Font("Dialog", Font.BOLD, Math.round(subtitle_size)));
        subtitle.setBorder(BorderFactory.createEmptyBorder(6, 24, 2, 24));
        subtitle.putClientProperty("fit.width", fit_w);

        // Total hands played this session, shown in brackets after the duration: getMano() is the
        // current hand number, which at session end equals the total played. Singular/plural
        // avoids "1 hands". Format: "date   (duration)   [N hands]".
        int manos = 0;
        try {
            manos = GameFrame.getInstance().getCrupier().getMano();
        } catch (Exception ex) {
        }
        String manos_txt = manos + " " + Translator.translate(manos == 1 ? "balance.mano" : "balance.manos");

        JLabel date = new JLabel(Helpers.getFechaHoraActual() + "   (" + Helpers.seconds2FullTime(GameFrame.getInstance().getConta_tiempo_juego()) + ")   [" + manos_txt + "]", SwingConstants.CENTER);
        date.setForeground(tapete_color);
        date.setFont(new Font("Dialog", Font.PLAIN, Math.round(date_size)));
        date.setBorder(BorderFactory.createEmptyBorder(2, 16, 6, 16));
        date.putClientProperty("fit.width", fit_w);

        JPanel block = new JPanel(new GridBagLayout());
        block.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.weightx = 1.0;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.gridy = 0;
        block.add(subtitle, g);
        g.gridy = 1;
        block.add(date, g);
        return block;
    }

    // Local player's giant result message (no amount): won / lost / tied. Black border + colored
    // fill, auto-fit to the real width/height (see OutlinedLabel.paintComponent).
    private JComponent buildHeroMessage() {
        double ganancia = localGanancia();
        int cmp = Helpers.doubleSecureCompare(ganancia, 0f);

        String text;
        Color fill;
        if (cmp > 0) {
            text = Translator.translate("balance.has_ganado");
            fill = WIN;
        } else if (cmp < 0) {
            text = Translator.translate("balance.has_perdido");
            fill = LOSE;
        } else {
            text = Translator.translate("balance.empate");
            fill = NEUTRAL;
        }

        float hero_size = Math.max(40f, Math.min(300f, screen_h * 0.16f));

        OutlinedLabel hero = new OutlinedLabel(text, fill);
        hero.setFont(new Font("Dialog", Font.BOLD, Math.round(hero_size)));
        hero.setBorder(BorderFactory.createEmptyBorder(0, 30, 0, 30));
        return hero;
    }

    // Local player's amount. Starts showing the total buyin and animates (startAmountAnimation)
    // to the final stack; on landing it reveals as +/- net, same giant font and win/loss color.
    // Empty on a tie.
    private JComponent buildAmount() {
        double[] bs = localBuyinStack();
        double buyin = bs[0];
        double stack = bs[1];
        double ganancia = Helpers.doubleClean(stack - buyin);
        int cmp = Helpers.doubleSecureCompare(ganancia, 0f);

        float amount_size = Math.max(36f, Math.min(280f, screen_h * 0.15f));

        if (cmp == 0) {
            // Tie: nothing to show, and no animation.
            OutlinedLabel empty = new OutlinedLabel("", NEUTRAL);
            empty.setFont(new Font("Dialog", Font.BOLD, Math.round(amount_size)));
            empty.setBorder(BorderFactory.createEmptyBorder(0, 30, 0, 30));
            return empty;
        }

        // Rolls in the final win/loss color rather than orange; on landing only the number
        // changes to +/- net (same color), then blinks.
        OutlinedLabel amount = new OutlinedLabel(Helpers.money2String(buyin), cmp > 0 ? WIN : LOSE);
        amount.setFont(new Font("Dialog", Font.BOLD, Math.round(amount_size)));
        amount.setBorder(BorderFactory.createEmptyBorder(0, 30, 0, 30));

        amount_label = amount;
        anim_buyin = buyin;
        anim_stack = stack;
        anim_ganancia = ganancia;

        return amount;
    }

    // {total buyin, final stack} for the local player (auditor entry: [0]=stack, [1]=buyin).
    private double[] localBuyinStack() {
        try {
            String nick = GameFrame.getInstance().getLocalPlayer().getNickname();
            Double[] pasta = GameFrame.getInstance().getCrupier().getAuditor().get(nick);
            if (pasta == null) {
                return new double[]{0, 0};
            }
            return new double[]{Helpers.doubleClean(pasta[1]), Helpers.doubleClean(pasta[0])};
        } catch (Exception ex) {
            return new double[]{0, 0};
        }
    }

    // Animated local-amount count: rolls from buyin to stack with a cubic ease-out, holds the
    // stack briefly, then reveals the +/- net.
    private void startAmountAnimation() {
        if (amount_label == null) {
            return;
        }

        final double from = anim_buyin;
        final double to = anim_stack;
        // 1.5s, in sync with the stack-fill animation (Crupier.STACK_FILL_MS). The ease-out and
        // the blink are this screen's signature and stay as-is.
        final long duration_ms = 1500;
        final long start_ms = System.currentTimeMillis();

        final String reveal_text = anim_ganancia > 0
                ? "+" + Helpers.money2String(anim_ganancia)
                : "-" + Helpers.money2String(anim_ganancia * -1);

        // The end-of-session count animation is optional (Animation settings, on by default). If
        // it's off, skip straight to the +/- reveal - no roll, no blink, no SFX (the sound is
        // tied to the roll, see below) - and take the screenshot exactly as if the normal count
        // had finished.
        if (!GameFrame.contadorFinalAnimOn()) {
            amount_label.setFill(anim_ganancia > 0 ? WIN : LOSE);
            amount_label.setText(reveal_text);
            takeBalanceScreenshot();
            return;
        }

        // Uses the game's fixed tick (GameFrame.getTickMs, 2 ms) instead of 16 ms/60 Hz: since
        // interpolation is time-based (p = elapsed/duration_ms), the finer tick makes the roll
        // much smoother without changing the 1.5 s duration. OutlinedLabel's per-frame cost is
        // well under a 16 ms budget, so rendering isn't the bottleneck.
        final javax.swing.Timer roll = new javax.swing.Timer(GameFrame.getTickMs(), null);
        amount_roll_timer = roll;
        roll.addActionListener((e) -> {
            double p = Math.min(1.0, (System.currentTimeMillis() - start_ms) / (double) duration_ms);

            if (p >= 1.0) {
                ((javax.swing.Timer) e.getSource()).stop();
                // No pause: on reaching the stack, reveal the +/- net (color) and blink.
                amount_label.setFill(anim_ganancia > 0 ? WIN : LOSE);
                amount_label.setText(reveal_text);
                blinkAmount();
                return;
            }

            double eased = 1.0 - Math.pow(1.0 - p, 3.0);
            double value = from + (to - from) * eased;
            amount_label.setText(Helpers.money2String(Helpers.doubleClean(value)));
        });

        // Retro point-counting SFX, synced to the roll: its blips decelerate with the same
        // ease-out curve and the closing accent lands at ~1.5s, on the +/- reveal. Plays on the
        // clip preloaded in the constructor (line already open) to start instantly and stay in
        // lockstep with the roll, avoiding an on-the-fly open() that could stall on a busy device
        // and desync the sound. Off-EDT: if preload hasn't finished yet, playPreloadedWav
        // resolves it on this thread, never the EDT. It also rewinds and reapplies volume/mute
        // (setClipVolume), so re-animating this screen restarts it cleanly.
        if (GameFrame.conteoSonidoOn()) {
            Helpers.threadRun(() -> Audio.playPreloadedWav("misc/balance_count.wav"));
        }

        roll.start();
    }

    // Blinks only the amount on reveal: toggles a "don't paint" flag (repaints just this label,
    // no relayout of the rest of the screen) and ends up visible.
    private void blinkAmount() {
        if (amount_label == null) {
            return;
        }

        final int total = 6; // 3 on/off cycles
        final int[] count = {0};

        final javax.swing.Timer blink = new javax.swing.Timer(130, null);
        amount_blink_timer = blink;
        blink.addActionListener((e) -> {
            count[0]++;
            amount_label.setBlank(count[0] % 2 == 1);
            if (count[0] >= total) {
                ((javax.swing.Timer) e.getSource()).stop();
                amount_label.setBlank(false);
                // Money counter finished (net +/- revealed and stable): auto screenshot.
                // Idempotent no-op if the player already left.
                takeBalanceScreenshot();
            }
        });
        blink.start();
    }

    // Captures the full window (rootPane) with the end-of-session overlay mounted on the
    // glassPane, same mechanism as Ctrl+P (Helpers.renderComponentImage: Java2D printAll, no
    // Robot/OS capture): rendering runs on the EDT (Swing requirement), the PNG write runs on its
    // own thread so it doesn't block the UI. Idempotent via screenshot_done. Must be called on
    // the EDT.
    //
    // The write does NOT go through Helpers.threadRun (THREAD_POOL): on returning to the menu,
    // session teardown (finTransmision -> RESET_GAME) calls Helpers.SHUTDOWN_THREAD_POOL()
    // (shutdownNow), which would drop a queued task or interrupt ImageIO.write mid-flight -> lost
    // or corrupt PNG. A dedicated thread outside the pool survives teardown, writes the file
    // (short, bounded I/O, with its own try/catch/finally in saveScreenshot) and exits. It works
    // on an already-rasterized snapshot and the static SCREENSHOTS_DIR, sharing no mutable game
    // state, so neither frame teardown nor the next session can affect it.
    //
    // Best-effort: never propagates. A rasterization failure (e.g. OOM) must not block the menu
    // button handler from reaching stopAmountAnimation/on_close, or exit would hang
    // (finTransmision waits on the latch) - hence the catch-all Throwable. screenshot_done is
    // only set after successfully launching the write, so the menu-button fallback can retry if
    // auto-capture had failed.
    private void takeBalanceScreenshot() {
        if (screenshot_done || !GameFrame.SCREENSHOT_FIN_TIMBA) {
            return;
        }
        GameFrame gf = GameFrame.getInstance();
        if (gf == null) {
            return;
        }
        try {
            // Ensures the amount is visible in the capture: if triggered from the menu button
            // while the net is still blinking (blinkAmount toggles blank every 130 ms), the
            // current frame could be blank and the capture would miss the amount. printAll reads
            // the blank flag live, so clearing it here is enough (already false on the automatic
            // path).
            if (amount_label != null) {
                amount_label.setBlank(false);
            }
            BufferedImage image = Helpers.renderComponentImage(gf.getRootPane());
            if (image == null) {
                return;
            }
            new Thread(() -> Helpers.saveScreenshot(image), "balance-screenshot-saver").start();
            screenshot_done = true;
        } catch (Throwable t) {
            Logger.getLogger(BalanceScreen.class.getName()).log(Level.WARNING, "Balance screenshot failed", t);
        }
    }

    // Stops the amount animation (roll + blink) and its SFX outright. Called by the exit buttons
    // (main menu / continue) before dispose(): while the counter is rolling, its per-frame
    // repaints (TextLayout.getOutline recomputes the text outline at full-screen size, non
    // negligible cost, now even more frequent at the 2 ms tick) starve the EDT; if left running,
    // session teardown (RESET_GAME, which discards the table and opens the main menu via
    // invokeAndWait) is stuck behind them and the menu doesn't appear until the animation finishes
    // on its own. Stopping them makes exit instantaneous, same as if pressed after the count
    // already finished. EDT-only (button handlers / windowClosed); stop() on a Timer that never
    // started is a no-op.
    private void stopAmountAnimation() {
        if (amount_roll_timer != null) {
            amount_roll_timer.stop();
        }
        if (amount_blink_timer != null) {
            amount_blink_timer.stop();
        }
        Audio.stopPreloadedWav("misc/balance_count.wav");
    }

    private double localGanancia() {
        try {
            String nick = GameFrame.getInstance().getLocalPlayer().getNickname();
            Double[] pasta = GameFrame.getInstance().getCrupier().getAuditor().get(nick);
            if (pasta == null) {
                return 0;
            }
            return Helpers.doubleClean(Helpers.doubleClean(pasta[0]) - Helpers.doubleClean(pasta[1]));
        } catch (Exception ex) {
            return 0;
        }
    }

    // -------------------------------------------------------------------------
    // Player card carousel (bottom) + side arrows.
    // -------------------------------------------------------------------------
    private JComponent buildCardsRegion() {
        CardsRow row = new CardsRow();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));

        final String local_nick = GameFrame.getInstance().getLocalPlayer().getNickname();

        // Seat order (same order the auditor walks when settling accounts).
        final ArrayList<String> seat_order = new ArrayList<>();
        for (Player p : GameFrame.getInstance().getJugadores()) {
            seat_order.add(p.getNickname());
        }

        Map<String, Double[]> auditor = GameFrame.getInstance().getCrupier().getAuditor();

        // Local player first; the rest in seat order (anyone no longer seated goes last, in
        // iteration order).
        ArrayList<String> nicks = new ArrayList<>(auditor.keySet());
        nicks.sort(Comparator.comparingInt((String n) -> {
            if (n.equals(local_nick)) {
                return -1;
            }
            int idx = seat_order.indexOf(n);
            return idx >= 0 ? idx : Integer.MAX_VALUE;
        }));

        row.add(Box.createHorizontalGlue());
        boolean first = true;
        for (String nick : nicks) {
            Double[] pasta = auditor.get(nick);
            if (pasta == null) {
                continue;
            }
            if (!first) {
                row.add(Box.createHorizontalStrut(card_gap));
            }
            first = false;
            double stack = Helpers.doubleClean(pasta[0]);
            double buyin = Helpers.doubleClean(pasta[1]);
            double ganancia = Helpers.doubleClean(stack - buyin);
            row.add(buildCard(nick, stack, buyin, ganancia));
        }
        row.add(Box.createHorizontalGlue());

        cards_scroll = new JScrollPane(row, JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        cards_scroll.setOpaque(false);
        cards_scroll.getViewport().setOpaque(false);
        cards_scroll.setBorder(BorderFactory.createEmptyBorder());
        cards_scroll.setViewportBorder(null);

        left_arrow = new ArrowButton(true);
        left_arrow.addActionListener((e) -> scrollCards(-(card_w + card_gap)));

        right_arrow = new ArrowButton(false);
        right_arrow.addActionListener((e) -> scrollCards(card_w + card_gap));

        JPanel region = new JPanel(new BorderLayout());
        region.setOpaque(false);
        // Same bottom margin as the button bar's top one, for symmetry.
        region.setBorder(BorderFactory.createEmptyBorder(0, 0, 34, 0));
        region.add(left_arrow, BorderLayout.WEST);
        region.add(cards_scroll, BorderLayout.CENTER);
        region.add(right_arrow, BorderLayout.EAST);
        region.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateArrows();
            }
        });
        return region;
    }

    private JComponent buildCard(String nick, double stack, double buyin, double ganancia) {
        // Fixed width; height gets unified later (normalizeCardHeights) to the tallest card so
        // the carousel has identical boxes without clipping content.
        CardPanel card = new CardPanel(card_w);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        card.setAlignmentY(Component.CENTER_ALIGNMENT);
        card_panels.add(card);

        int inner_w = card_w - 36;

        JLabel avatar = new JLabel();
        avatar.setAlignmentX(Component.CENTER_ALIGNMENT);
        setRoundedAvatar(avatar, nick, avatar_sz);
        card.add(avatar);
        card.add(Box.createVerticalStrut(10));

        JLabel nick_lbl = new JLabel(nick);
        nick_lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        nick_lbl.setForeground(CARD_TEXT);
        nick_lbl.setFont(new Font("Dialog", Font.BOLD, Math.max(14, Math.round(card_h * 0.085f))));
        nick_lbl.putClientProperty("fit.width", inner_w);
        card.add(nick_lbl);
        card.add(Box.createVerticalStrut(8));

        JLabel result_lbl = new JLabel(resultText(ganancia));
        result_lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        result_lbl.setForeground(resultColor(ganancia));
        result_lbl.setFont(new Font("Dialog", Font.BOLD, Math.max(15, Math.round(card_h * 0.10f))));
        result_lbl.putClientProperty("fit.width", inner_w);
        card.add(result_lbl);
        card.add(Box.createVerticalStrut(10));

        int stat_size = Math.max(12, Math.round(card_h * 0.06f));
        card.add(statRow(Translator.translate("balance.fichas"), Helpers.money2String(stack), stat_size));
        card.add(Box.createVerticalStrut(3));
        card.add(statRow(Translator.translate("stats.buyin"), Helpers.money2String(buyin), stat_size));

        return card;
    }

    private JComponent statRow(String label, String value, int size) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        p.setOpaque(false);
        p.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel l = new JLabel(label);
        l.setForeground(CARD_TEXT_DIM);
        l.setFont(new Font("Dialog", Font.PLAIN, size));

        JLabel v = new JLabel(value);
        v.setForeground(CARD_TEXT);
        v.setFont(new Font("Dialog", Font.BOLD, size + 2));

        p.add(l);
        p.add(v);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, v.getPreferredSize().height + 4));
        return p;
    }

    private String resultText(double ganancia) {
        int cmp = Helpers.doubleSecureCompare(ganancia, 0f);
        if (cmp > 0) {
            return Translator.translate("ui.gana_4") + " " + Helpers.money2String(ganancia);
        } else if (cmp < 0) {
            return Translator.translate("ui.pierde_2") + " " + Helpers.money2String(ganancia * -1);
        } else {
            return Translator.translate("ui.ni_gana_ni_pierde");
        }
    }

    private Color resultColor(double ganancia) {
        int cmp = Helpers.doubleSecureCompare(ganancia, 0f);
        return cmp > 0 ? WIN : cmp < 0 ? LOSE : NEUTRAL;
    }

    private void setRoundedAvatar(JLabel label, String nick, int size) {
        String avatar_path = GameFrame.getInstance().getNick2avatar().get(nick);

        Image img;
        if (avatar_path != null && !"".equals(avatar_path) && !"*".equals(avatar_path)) {
            img = new ImageIcon(avatar_path).getImage();
        } else if ("*".equals(avatar_path)) {
            img = new ImageIcon(getClass().getResource("/images/avatar_bot.png")).getImage();
        } else {
            img = new ImageIcon(getClass().getResource("/images/avatar_default.png")).getImage();
        }

        ImageIcon icon = new ImageIcon(Helpers.makeImageRoundedCorner(highQualityScale(img, size), 20));
        label.setIcon(icon);
        label.setPreferredSize(new Dimension(size, size));
        label.setMaximumSize(new Dimension(size, size));
    }

    // High-quality avatar rescale (bicubic interpolation + quality hints), meant to upscale small
    // avatars without the aliasing of getScaledInstance(SCALE_SMOOTH). Source image is guaranteed
    // already loaded (ImageIcon uses MediaTracker) before drawing to the destination canvas.
    private static BufferedImage highQualityScale(Image src, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
            g2.drawImage(src, 0, 0, size, size, null);
        } finally {
            g2.dispose();
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // Side-arrow horizontal scrolling.
    // -------------------------------------------------------------------------
    private void scrollCards(int dx) {
        if (cards_scroll == null) {
            return;
        }
        JViewport vp = cards_scroll.getViewport();
        if (vp.getView() == null) {
            return;
        }
        int max_x = Math.max(0, vp.getView().getWidth() - vp.getWidth());
        int nx = Math.max(0, Math.min(max_x, vp.getViewPosition().x + dx));
        vp.setViewPosition(new Point(nx, vp.getViewPosition().y));
        updateArrows();
    }

    private void updateArrows() {
        if (cards_scroll == null || left_arrow == null || right_arrow == null) {
            return;
        }
        JViewport vp = cards_scroll.getViewport();
        if (vp.getView() == null) {
            return;
        }
        int view_w = vp.getView().getWidth();
        int port_w = vp.getWidth();
        boolean overflow = view_w > port_w + 1;
        left_arrow.setVisible(overflow);
        right_arrow.setVisible(overflow);
        if (overflow) {
            int x = vp.getViewPosition().x;
            int max_x = view_w - port_w;
            left_arrow.setEnabled(x > 0);
            right_arrow.setEnabled(x < max_x);
        }
    }

    // Gives the 4 nav-bar buttons an identical size (same width AND height), keeping the
    // responsive behavior:
    // 1) Common font = the smallest of the four after per-button auto-fit (fitTaggedLabels), so
    //    the longest label still fits its quarter and all four share one font size (hence one
    //    height).
    // 2) Common box = the max preferred width/height of the four, fixed on all three sizes
    //    (pref/min/max) so FlowLayout paints them identical.
    // Everything derives from screen size, so a different resolution changes all four together,
    // but always equal to each other.
    private void normalizeNavButtons() {
        if (nav_buttons.isEmpty()) {
            return;
        }
        float min_size = Float.MAX_VALUE;
        for (JButton b : nav_buttons) {
            min_size = Math.min(min_size, b.getFont().getSize2D());
        }
        for (JButton b : nav_buttons) {
            b.setFont(b.getFont().deriveFont(min_size));
        }
        int max_w = 0;
        int max_h = 0;
        for (JButton b : nav_buttons) {
            Dimension d = b.getPreferredSize();
            max_w = Math.max(max_w, d.width);
            max_h = Math.max(max_h, d.height);
        }
        Dimension uniform = new Dimension(max_w, max_h);
        for (JButton b : nav_buttons) {
            b.setPreferredSize(uniform);
            b.setMinimumSize(uniform);
            b.setMaximumSize(uniform);
        }

        // The speaker chip becomes square with the same height as the buttons (its width becomes
        // that height), and the icon is scaled to about half of that side. This keeps it aligned
        // with the row and reading as one more button, at any resolution.
        if (sound_chip != null) {
            Dimension square = new Dimension(max_h, max_h);
            sound_chip.setPreferredSize(square);
            sound_chip.setMinimumSize(square);
            sound_chip.setMaximumSize(square);
            sound_icon_sz = Math.max(16, Math.round(max_h * 0.5f));
            refreshBalanceSoundIcon();
            sound_chip.revalidate();
        }
    }

    // Equalizes the height of ALL cards to the tallest one (measured with fonts already finalized
    // after updateFonts/fitTaggedLabels), so the carousel is perfectly uniform without clipping
    // any card's content.
    private void normalizeCardHeights() {
        int max_h = 0;
        for (CardPanel c : card_panels) {
            max_h = Math.max(max_h, c.getPreferredSize().height);
        }
        for (CardPanel c : card_panels) {
            c.setUniformHeight(max_h);
        }
    }

    // -------------------------------------------------------------------------
    // Supporting components.
    // -------------------------------------------------------------------------
    // Row of cards that fills the viewport when everything fits (glue keeps the cards centered),
    // and only becomes scrollable when they overflow.
    private static final class CardsRow extends JPanel implements Scrollable {

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(24, visibleRect.width);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return getParent() instanceof JViewport && getParent().getWidth() >= getPreferredSize().width;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return true;
        }
    }

    // Label with centered text painted as an outline (black border) + fill so it reads over the
    // felt. Same technique as the table's call-cost overlay (TextLayout.getOutline: draw the
    // halo, fill the interior).
    private static final class OutlinedLabel extends JLabel {

        private static final float STROKE_RATIO = 0.06f;
        private final Color halo = new Color(0, 0, 0, 235);
        private Color fill;
        private boolean blank = false;

        OutlinedLabel(String text, Color fill) {
            super(text, SwingConstants.CENTER);
            this.fill = fill;
        }

        void setFill(Color c) {
            this.fill = c;
            repaint();
        }

        // Hides/shows the text without touching layout (repaint only): used for the blink.
        void setBlank(boolean b) {
            this.blank = b;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            String text = getText();
            if (blank || text == null || text.isEmpty()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                java.awt.Insets ins = getInsets();
                double avail_w = (getWidth() - ins.left - ins.right) * 0.98;
                double avail_h = getHeight() - ins.top - ins.bottom;

                Font base = getFont();
                java.awt.font.FontRenderContext frc = g2.getFontRenderContext();

                // Responsive auto-fit: shrinks the font so the text always fits the real width
                // (and height), whatever the resolution.
                TextLayout probe = new TextLayout(text, base, frc);
                double tw = probe.getAdvance();
                double th = probe.getAscent() + probe.getDescent();
                double scale = 1.0;
                if (tw > avail_w && tw > 0) {
                    scale = avail_w / tw;
                }
                if (th * scale > avail_h && th > 0) {
                    scale = Math.min(scale, avail_h / th);
                }
                Font font = scale < 1.0 ? base.deriveFont((float) Math.max(8.0, base.getSize2D() * scale)) : base;

                TextLayout tl = new TextLayout(text, font, frc);
                Rectangle2D b = tl.getBounds();
                double x = (getWidth() - b.getWidth()) / 2.0 - b.getX();
                double y = (getHeight() - b.getHeight()) / 2.0 - b.getY();
                java.awt.Shape outline = tl.getOutline(AffineTransform.getTranslateInstance(x, y));

                float stroke = Math.max(2f, font.getSize2D() * STROKE_RATIO);
                g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(halo);
                g2.draw(outline);
                g2.setColor(fill);
                g2.fill(outline);
            } finally {
                g2.dispose();
            }
        }
    }

    // Scroll arrow: hand-painted white filled triangle (the UI font doesn't guarantee arrow
    // glyphs) on a translucent dark disc. Points left or right.
    private static final class ArrowButton extends JButton {

        private final boolean left;

        ArrowButton(boolean left) {
            super();
            this.left = left;
            setPreferredSize(new Dimension(84, 84));
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                int d = Math.min(w, h) - 14;
                int cx = w / 2;
                int cy = h / 2;
                boolean en = isEnabled();

                g2.setColor(new Color(0, 0, 0, en ? 140 : 50));
                g2.fillOval(cx - d / 2, cy - d / 2, d, d);

                int tw = d / 4;
                int th = d / 3;
                int[] xs;
                int[] ys;
                if (left) {
                    xs = new int[]{cx + tw / 2, cx + tw / 2, cx - tw};
                    ys = new int[]{cy - th, cy + th, cy};
                } else {
                    xs = new int[]{cx - tw / 2, cx - tw / 2, cx + tw};
                    ys = new int[]{cy - th, cy + th, cy};
                }
                g2.setColor(new Color(255, 255, 255, en ? 240 : 90));
                g2.fillPolygon(xs, ys, 3);
            } finally {
                g2.dispose();
            }
        }
    }

    // Player card: clean rounded fill (transparent corners show the felt through), all identical
    // (no special border). Fixed width; height is set by normalizeCardHeights() to the tallest
    // card so the carousel is uniform.
    private static final class CardPanel extends JPanel {

        private final int fixed_width;
        private int uniform_height = 0;

        CardPanel(int fixed_width) {
            this.fixed_width = fixed_width;
            setOpaque(false);
        }

        void setUniformHeight(int h) {
            this.uniform_height = h;
        }

        @Override
        public Dimension getPreferredSize() {
            int h = uniform_height > 0 ? uniform_height : super.getPreferredSize().height;
            return new Dimension(fixed_width, h);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                int arc = 30;
                g2.setColor(CARD_BG);
                g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
            } finally {
                g2.dispose();
            }
        }
    }

    // Shrinks the font of a JLabel/JButton tagged with "fit.width" so its text fits that width.
    // Called after updateFonts (final font family) so the fit is exact at any resolution.
    private void fitTaggedLabels(Component c) {
        if (c instanceof JComponent) {
            Object w = ((JComponent) c).getClientProperty("fit.width");
            if (w instanceof Integer) {
                String text = (c instanceof JLabel) ? ((JLabel) c).getText()
                        : (c instanceof javax.swing.AbstractButton) ? ((javax.swing.AbstractButton) c).getText() : null;
                if (text != null) {
                    fitTextFont(c, text, (Integer) w);
                }
            }
        }
        if (c instanceof java.awt.Container) {
            for (Component ch : ((java.awt.Container) c).getComponents()) {
                fitTaggedLabels(ch);
            }
        }
    }

    private static void fitTextFont(Component c, String text, int max_width) {
        if (text.isEmpty() || max_width <= 0) {
            return;
        }
        Font f = c.getFont();
        int tw = c.getFontMetrics(f).stringWidth(text);
        if (tw > max_width) {
            c.setFont(f.deriveFont(Math.max(9f, f.getSize2D() * max_width / (float) tw)));
        }
    }
}
