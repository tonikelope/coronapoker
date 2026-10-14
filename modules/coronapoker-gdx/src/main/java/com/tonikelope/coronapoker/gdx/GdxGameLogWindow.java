/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/** Independent, non-modal desktop window for the live game log. */
final class GdxGameLogWindow {

    static final int BASE_FONT_SIZE = 20;
    private static final Logger LOGGER = Logger.getLogger(
            GdxGameLogWindow.class.getName());
    private static final Color BACKGROUND = new Color(5, 12, 22);
    private static final Color GUTTER = new Color(120, 225, 235);
    private static final Color SELECTION = new Color(28, 91, 116);
    private static final Color CARD_BACKGROUND = new Color(244, 246, 248);
    private static final int REFRESH_MILLIS = 200;

    private final GdxGameLogSink sink;
    private final String title;
    private JFrame frame;
    private NoWrapTextPane textPane;
    private JScrollPane scrollPane;
    private Timer refreshTimer;
    private List<String> renderedLines = List.of();
    private String fontFamily;

    GdxGameLogWindow(GdxGameLogSink sink, String title) {
        this.sink = Objects.requireNonNull(sink, "sink");
        this.title = Objects.requireNonNull(title, "title");
    }

    /**
     * Opens without blocking the game. The native GDX overlay remains a
     * fallback for headless/test environments.
     */
    boolean open() {
        if (GraphicsEnvironment.isHeadless()) return false;
        SwingUtilities.invokeLater(() -> {
            try {
                ensureWindow();
                refresh();
                frame.setVisible(true);
                frame.toFront();
                refreshTimer.start();
            } catch (RuntimeException ex) {
                LOGGER.log(Level.WARNING,
                        "Unable to open the non-modal game log", ex);
            }
        });
        return true;
    }

    void dispose() {
        if (GraphicsEnvironment.isHeadless()) return;
        SwingUtilities.invokeLater(() -> {
            if (refreshTimer != null) refreshTimer.stop();
            if (frame != null) frame.dispose();
            frame = null;
            textPane = null;
            scrollPane = null;
            renderedLines = List.of();
        });
    }

    private void ensureWindow() {
        if (frame != null) return;
        textPane = new NoWrapTextPane();
        fontFamily = monospaceFamily();
        textPane.setEditable(false);
        textPane.setBackground(BACKGROUND);
        textPane.setForeground(Color.WHITE);
        textPane.setCaretColor(Color.WHITE);
        textPane.setSelectionColor(SELECTION);
        textPane.setSelectedTextColor(Color.WHITE);
        textPane.setMargin(new Insets(18, 20, 18, 20));
        textPane.setBorder(BorderFactory.createEmptyBorder());

        scrollPane = new JScrollPane(textPane,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(BorderFactory.createLineBorder(
                new Color(42, 71, 103), 2));
        scrollPane.getViewport().setBackground(BACKGROUND);
        scrollPane.getVerticalScrollBar().setUnitIncrement(24);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(24);

        frame = new JFrame(title);
        frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        frame.setContentPane(scrollPane);
        frame.setMinimumSize(new Dimension(560, 340));
        frame.setSize(980, 720);
        frame.setLocationByPlatform(true);
        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentHidden(ComponentEvent event) {
                if (refreshTimer != null) refreshTimer.stop();
            }

            @Override
            public void componentShown(ComponentEvent event) {
                if (refreshTimer != null) refreshTimer.start();
            }
        });
        refreshTimer = new Timer(REFRESH_MILLIS, event -> refresh());
        refreshTimer.setCoalesce(true);
    }

    private void refresh() {
        GdxGameLogSink.Snapshot snapshot = sink.snapshot();
        if (snapshot.lines().equals(renderedLines)) return;
        JScrollBar vertical = scrollPane.getVerticalScrollBar();
        boolean followEnd = vertical.getValue() + vertical.getVisibleAmount()
                >= vertical.getMaximum() - 12;
        int previousValue = vertical.getValue();
        int selectionStart = textPane.getSelectionStart();
        int selectionEnd = textPane.getSelectionEnd();
        render(snapshot.lines());
        renderedLines = snapshot.lines();
        SwingUtilities.invokeLater(() -> {
            if (followEnd) {
                vertical.setValue(vertical.getMaximum());
            } else {
                vertical.setValue(Math.min(previousValue,
                        vertical.getMaximum()));
            }
            int length = textPane.getDocument().getLength();
            if (selectionStart != selectionEnd) {
                textPane.select(Math.min(selectionStart, length),
                        Math.min(selectionEnd, length));
            }
        });
    }

    private void render(List<String> lines) {
        StyledDocument document = textPane.getStyledDocument();
        try {
            document.remove(0, document.getLength());
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                GdxGameLogFormatter.Marker marker =
                        GdxGameLogFormatter.marker(line);
                String prefix = markerPrefix(marker);
                if (!prefix.isEmpty()) {
                    document.insertString(document.getLength(), prefix,
                            attributes(GUTTER, false, false));
                }
                for (GdxGameLogFormatter.Run run
                        : GdxGameLogFormatter.runs(line)) {
                    document.insertString(document.getLength(), run.text(),
                            attributes(awtColor(run.color()), run.bold(),
                                    run.card()));
                }
                if (index + 1 < lines.size()) {
                    document.insertString(document.getLength(), "\n",
                            attributes(Color.WHITE, false, false));
                }
            }
        } catch (BadLocationException impossible) {
            throw new IllegalStateException("Unable to render the game log",
                    impossible);
        }
    }

    private SimpleAttributeSet attributes(Color foreground,
            boolean bold, boolean card) {
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setFontFamily(attributes, fontFamily);
        StyleConstants.setFontSize(attributes, BASE_FONT_SIZE);
        StyleConstants.setBold(attributes, bold);
        StyleConstants.setForeground(attributes, foreground);
        if (card) {
            StyleConstants.setBackground(attributes, CARD_BACKGROUND);
        }
        return attributes;
    }

    static String markerPrefix(GdxGameLogFormatter.Marker marker) {
        return switch (marker) {
            case DEALER -> "D  ";
            case SMALL_BLIND -> "SB ";
            case BIG_BLIND -> "BB ";
            case MONEY -> "$  ";
            case STRADDLE -> "ST ";
            case DEALER_STRADDLE -> "DS ";
            case MULTIVERSE -> "MV ";
            case BLANK, GRID -> "   ";
            case NONE -> "";
        };
    }

    static String monospaceFamily() {
        String os = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT);
        String preferred = os.contains("win") ? "Consolas"
                : os.contains("mac") ? "Menlo" : "DejaVu Sans Mono";
        for (String family : GraphicsEnvironment
                .getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
            if (family.equalsIgnoreCase(preferred)) return family;
        }
        return Font.MONOSPACED;
    }

    private static Color awtColor(com.badlogic.gdx.graphics.Color color) {
        return new Color(clamp(color.r), clamp(color.g), clamp(color.b),
                clamp(color.a));
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static final class NoWrapTextPane extends JTextPane {

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return false;
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension preferred = super.getPreferredSize();
            if (getParent() != null) {
                preferred.width = Math.max(preferred.width,
                        getParent().getWidth());
            }
            return preferred;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void paintComponent(Graphics graphics) {
            Graphics2D quality = (Graphics2D) graphics.create();
            Object desktopHints = Toolkit.getDefaultToolkit()
                    .getDesktopProperty("awt.font.desktophints");
            if (desktopHints instanceof Map<?, ?> hints) {
                quality.addRenderingHints((Map<RenderingHints.Key, ?>) hints);
            } else {
                quality.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            }
            quality.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                    RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
            super.paintComponent(quality);
            quality.dispose();
        }
    }
}
