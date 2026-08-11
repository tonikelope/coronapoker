/*
 * Copyright (C) 2026 tonikelope
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

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Shared visual building blocks for the tabs of the settings dialog: rounded section cards,
 * right-aligned rows ("label ......... control"), a sliding on/off {@link ToggleSwitch} and a
 * guide-line sub-group for options that hang off a parent toggle. Centralizes the modern look so
 * every tab (Appearance, Audio, ...) renders consistently. Presentation only, holds no state.
 *
 * @author tonikelope
 */
public final class SettingsUI {

    private SettingsUI() {
    }

    static final java.awt.Color CARD_BG = new java.awt.Color(250, 251, 250);
    static final java.awt.Color CARD_BORDER = new java.awt.Color(0, 0, 0, 28);
    static final java.awt.Color CARD_TITLE = new java.awt.Color(74, 118, 92);
    static final java.awt.Color GROUP_GUIDE = new java.awt.Color(0x1F, 0x9D, 0x5F, 90);

    // Rounded, near-white section card with its title painted on top (like a TitledBorder, but
    // drawn in paintComponent so the dialog's setUniformFont pass can't restyle it). Y-axis stack.
    public static JPanel card(String titleKey) {
        final String title = Translator.translate(titleKey).toUpperCase();
        JPanel p = new JPanel() {
            @Override
            protected void paintComponent(java.awt.Graphics g) {
                super.paintComponent(g);
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                int arc = Math.round(16 * Helpers.DIALOG_ZOOM);
                g2.setColor(CARD_BG);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
                g2.setColor(CARD_BORDER);
                g2.setStroke(new java.awt.BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
                java.awt.Font base = getFont();
                if (base != null) {
                    g2.setFont(base.deriveFont(java.awt.Font.BOLD, Math.round(12 * Helpers.DIALOG_ZOOM)));
                }
                g2.setColor(CARD_TITLE);
                g2.drawString(title, Math.round(15 * Helpers.DIALOG_ZOOM), Math.round(20 * Helpers.DIALOG_ZOOM));
                g2.dispose();
            }

            // Fills the column width but never stretches vertically (so cards stacked in a
            // BoxLayout Y keep their natural height instead of spreading apart).
            @Override
            public java.awt.Dimension getMaximumSize() {
                return new java.awt.Dimension(Short.MAX_VALUE, getPreferredSize().height);
            }
        };
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        // Top inset leaves room for the painted title; the rest is the card's inner padding.
        int pad = Math.round(14 * Helpers.DIALOG_ZOOM);
        p.setBorder(BorderFactory.createEmptyBorder(Math.round(34 * Helpers.DIALOG_ZOOM), pad, pad, pad));
        return p;
    }

    // Adds a full-width row + a constant vertical gap (12px) to a card column. Rows cap their max
    // height to preferred, so they don't stretch; the leftover is absorbed by the glue closing it.
    public static void addLeft(JPanel column, JComponent comp) {
        comp.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        column.add(comp);
        column.add(Box.createVerticalStrut(Math.round(12 * Helpers.DIALOG_ZOOM)));
    }

    // Closes a column with glue that pushes rows up and leaves the leftover space at the bottom.
    public static void closeColumn(JPanel column) {
        column.add(Box.createVerticalGlue());
    }

    // Full-width sub-group for options that hang off a parent toggle, marked by a left margin +
    // a thin green guide, so its rows' controls still line up on the card's right edge. Transparent.
    public static JPanel guideGroup() {
        JPanel p = new JPanel() {
            @Override
            public java.awt.Dimension getMaximumSize() {
                return new java.awt.Dimension(Short.MAX_VALUE, getPreferredSize().height);
            }
        };
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        // Left margin BEFORE the guide indents the whole sub-group clearly to the right of its
        // parent; then the guide + its own padding.
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, Math.round(22 * Helpers.DIALOG_ZOOM), 0, 0),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, Math.round(2 * Helpers.DIALOG_ZOOM), 0, 0, GROUP_GUIDE),
                        BorderFactory.createEmptyBorder(Math.round(2 * Helpers.DIALOG_ZOOM), Math.round(14 * Helpers.DIALOG_ZOOM), Math.round(2 * Helpers.DIALOG_ZOOM), 0))));
        p.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return p;
    }

    // Adds a row to a guideGroup, with a thin gap between rows so the group reads compact.
    public static void addToGroup(JPanel group, JComponent row) {
        row.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        if (group.getComponentCount() > 0) {
            group.add(Box.createVerticalStrut(Math.round(4 * Helpers.DIALOG_ZOOM)));
        }
        group.add(row);
    }

    // Full-width row: LEFT hugs the left edge, RIGHT is pushed to the card's right edge by a glue
    // between them. Optional left indent (scaled). Transparent; natural height. The modern
    // "label ......... control" layout (toggles / dropdowns / spinners all align on the right).
    public static JPanel alignedRow(int indentPx, JComponent left, JComponent right) {
        JPanel row = new JPanel() {
            @Override
            public java.awt.Dimension getMaximumSize() {
                return new java.awt.Dimension(Short.MAX_VALUE, getPreferredSize().height);
            }
        };
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        if (indentPx > 0) {
            row.add(Box.createHorizontalStrut(Math.round(indentPx * Helpers.DIALOG_ZOOM)));
        }
        left.setAlignmentY(JComponent.CENTER_ALIGNMENT);
        right.setAlignmentY(JComponent.CENTER_ALIGNMENT);
        // Keep the control at its natural size (hugging the right edge) instead of letting the
        // BoxLayout stretch it across the row.
        right.setMaximumSize(right.getPreferredSize());
        row.add(left);
        row.add(Box.createHorizontalStrut(Math.round(10 * Helpers.DIALOG_ZOOM)));
        row.add(Box.createHorizontalGlue());
        row.add(right);
        // A label + switch pair: the switch greys its label out when it becomes disabled.
        if (left instanceof JLabel && right instanceof ToggleSwitch) {
            ((ToggleSwitch) right).pairLabel((JLabel) left);
        }
        return row;
    }

    // Vertical stack for sub-settings (speed, effect, style...): each row is added via
    // addAlignedSubRow, its control pushed to the card's right edge. Transparent, full width.
    public static JPanel subGrid() {
        JPanel grid = new JPanel() {
            @Override
            public java.awt.Dimension getMaximumSize() {
                return new java.awt.Dimension(Short.MAX_VALUE, getPreferredSize().height);
            }
        };
        grid.setOpaque(false);
        grid.setLayout(new BoxLayout(grid, BoxLayout.Y_AXIS));
        grid.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return grid;
    }

    // Adds an "icon label ......... control" row to a subGrid; gridy > 0 gets a small top gap.
    public static void addAlignedSubRow(JPanel grid, int gridy, String iconPath, JLabel label, JComponent control) {
        label.setIcon(icon(iconPath));
        label.setIconTextGap(Math.round(8 * Helpers.DIALOG_ZOOM));
        if (gridy > 0) {
            grid.add(Box.createVerticalStrut(Math.round(6 * Helpers.DIALOG_ZOOM)));
        }
        grid.add(alignedRow(0, label, control));
    }

    // "icon label:" on the left, control aligned on the card's right edge.
    public static JPanel labeledRow(String iconPath, String labelKey, JComponent control) {
        JLabel label = new JLabel(Translator.translate(labelKey) + ":");
        label.setIcon(icon(iconPath));
        label.setIconTextGap(Math.round(8 * Helpers.DIALOG_ZOOM));
        return alignedRow(0, label, control);
    }

    // Classpath icon at its native size (the /images/menu ones already come sized for these rows).
    public static javax.swing.ImageIcon icon(String path) {
        return new javax.swing.ImageIcon(SettingsUI.class.getResource(path));
    }

    // Icon from outside /images/menu scaled down to a square of the given size (row height).
    public static javax.swing.ImageIcon scaledIcon(String path, int size) {
        return scaledIcon(path, size, size);
    }

    public static javax.swing.ImageIcon scaledIcon(String path, int width, int height) {
        try {
            return Helpers.scaleIcon(SettingsUI.class.getResource(path), width, height);
        } catch (java.net.MalformedURLException ex) {
            return null;
        }
    }

    // Fits an icon inside the given box WITHOUT distorting it, for artwork that isn't square.
    public static javax.swing.ImageIcon fitIcon(String path, int max_width, int max_height) {

        java.net.URL url = SettingsUI.class.getResource(path);

        if (url == null) {
            return null;
        }

        javax.swing.ImageIcon raw = new javax.swing.ImageIcon(url);

        if (raw.getIconWidth() <= 0 || raw.getIconHeight() <= 0) {
            return raw;
        }

        float scale = Math.min((float) max_width / raw.getIconWidth(), (float) max_height / raw.getIconHeight());

        return scaledIcon(path, Math.max(1, Math.round(raw.getIconWidth() * scale)), Math.max(1, Math.round(raw.getIconHeight() * scale)));
    }

    // A sliding on/off switch that behaves exactly like a JCheckBox (same model, doClick,
    // isSelected, listeners, enabled state) but paints a pill + thumb instead of the square.
    // Carries no text of its own; the row's label holds it. Scales with DIALOG_ZOOM.
    public static final class ToggleSwitch extends JCheckBox {

        private static final java.awt.Color TRACK_ON = new java.awt.Color(0x1F, 0x9D, 0x5F);
        private static final java.awt.Color TRACK_OFF = new java.awt.Color(0xC7, 0xD0, 0xCB);
        private static final java.awt.Color TRACK_DISABLED = new java.awt.Color(0xB8, 0xC2, 0xBC);

        public ToggleSwitch(boolean selected) {
            super("", selected);
            setOpaque(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setRolloverEnabled(false);
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            int w = Math.round(42 * Helpers.DIALOG_ZOOM);
            int h = Math.round(23 * Helpers.DIALOG_ZOOM);
            java.awt.Dimension d = new java.awt.Dimension(w, h);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
        }

        // The switch carries no text; its row label lives in a separate JLabel. Pairing it means a
        // disabled switch greys its label too (Swing dims a disabled JLabel), restoring the old
        // JCheckBox-with-text behaviour that was lost when the text moved to a separate label.
        private JLabel pairedLabel;

        public void pairLabel(JLabel label) {
            pairedLabel = label;
            if (label != null) {
                label.setEnabled(isEnabled());
            }
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            if (pairedLabel != null) {
                pairedLabel.setEnabled(enabled);
            }
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(!isEnabled() ? TRACK_DISABLED : (isSelected() ? TRACK_ON : TRACK_OFF));
            g2.fillRoundRect(0, 0, w - 1, h - 1, h, h);
            int pad = Math.max(2, Math.round(3 * Helpers.DIALOG_ZOOM));
            int d = h - 2 * pad;
            int x = isSelected() ? w - d - pad : pad;
            // Soft drop shadow under the thumb, then the white thumb on top.
            g2.setColor(new java.awt.Color(0, 0, 0, 45));
            g2.fillOval(x, pad + 1, d, d);
            g2.setColor(java.awt.Color.WHITE);
            g2.fillOval(x, pad, d, d);
            g2.dispose();
        }
    }

    // Paints the SAME sliding switch as ToggleSwitch, but as an Icon in a checkbox's icon slot,
    // reading the checkbox's own selected/enabled state. Lets a plain JCheckBox look like the switch
    // WITHOUT moving its text — a drop-in for dialogs outside the Settings tabs (Exit/Pause/Rebuy...).
    private static final class SwitchIcon implements javax.swing.Icon {

        private final int w;
        private final int h;

        SwitchIcon(int w, int h) {
            this.w = w;
            this.h = h;
        }

        @Override
        public int getIconWidth() {
            return w;
        }

        @Override
        public int getIconHeight() {
            return h;
        }

        @Override
        public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) {
            boolean sel = (c instanceof javax.swing.AbstractButton) && ((javax.swing.AbstractButton) c).isSelected();
            boolean en = c.isEnabled();
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(!en ? ToggleSwitch.TRACK_DISABLED : (sel ? ToggleSwitch.TRACK_ON : ToggleSwitch.TRACK_OFF));
            g2.fillRoundRect(x, y, w - 1, h - 1, h, h);
            int pad = Math.max(2, Math.round(h * 0.14f));
            int d = h - 2 * pad;
            int tx = sel ? x + w - d - pad : x + pad;
            g2.setColor(new java.awt.Color(0, 0, 0, 45));
            g2.fillOval(tx, y + pad + 1, d, d);
            g2.setColor(java.awt.Color.WHITE);
            g2.fillOval(tx, y + pad, d, d);
            g2.dispose();
        }
    }

    // Repaints a checkbox as the sliding switch IN PLACE (icon slot), SIZED TO ITS OWN FONT so it
    // fits dialogs with big table fonts and stays compact enough not to push a long label off the
    // edge. Keeps text, state, listeners and layout untouched. Skips menu items only. Same icon in
    // every slot so Swing never grey-filters it; the text dims on its own when disabled.
    public static void toggleize(javax.swing.AbstractButton cb) {
        if (cb == null || cb instanceof javax.swing.JCheckBoxMenuItem) {
            return;
        }
        int fs = (cb.getFont() != null) ? cb.getFont().getSize() : Math.round(14 * Helpers.DIALOG_ZOOM);
        int h = Math.max(Math.round(15 * Helpers.DIALOG_ZOOM), Math.round(fs * 1.0f));
        int w = Math.round(h * 1.6f);
        javax.swing.Icon icon = new SwitchIcon(w, h);
        cb.setIcon(icon);
        cb.setSelectedIcon(icon);
        cb.setDisabledIcon(icon);
        cb.setDisabledSelectedIcon(icon);
        cb.setPressedIcon(icon);
        cb.setRolloverIcon(icon);
        cb.setRolloverSelectedIcon(icon);
        cb.setOpaque(false);
        cb.setFocusPainted(false);
        cb.setIconTextGap(Math.round(fs * 0.3f));
    }

    // Walks a container tree and toggleizes every JCheckBox (not menu items). Call once after
    // initComponents() in a dialog's constructor: converts a whole dialog to switches with one line,
    // without touching generated //GEN code, so a NetBeans .form regenerate can't wipe it.
    public static void toggleizeAll(java.awt.Container root) {
        for (java.awt.Component c : root.getComponents()) {
            if (c instanceof javax.swing.JCheckBox && !(c instanceof javax.swing.JCheckBoxMenuItem)) {
                toggleize((javax.swing.AbstractButton) c);
            } else if (c instanceof java.awt.Container) {
                toggleizeAll((java.awt.Container) c);
            }
        }
    }
}
