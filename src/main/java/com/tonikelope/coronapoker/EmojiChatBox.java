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

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import javax.swing.JTextPane;
import javax.swing.plaf.TextUI;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.BoxView;
import javax.swing.text.ComponentView;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.IconView;
import javax.swing.text.LabelView;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.ParagraphView;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;

/**
 * Single-line JTextPane that supports inline emoji icons rendered alongside
 * text. Each emoji is stored as a single dummy char in the Document carrying
 * an Icon attribute plus an integer "emoji.id" attribute. {@link #getRawText}
 * rebuilds the legacy " #N# " wire format from the document. Caret nav,
 * backspace and delete treat the emoji as one normal character.
 *
 * Visual layout: component height is fixed to {@link #FIXED_HEIGHT} to match
 * the surrounding chat row buttons. A custom {@code PaddedLabelView} reports
 * the text view's preferred Y span as the emoji height so the line height is
 * constant whether or not emojis are present — that keeps the text from
 * jumping when an emoji is inserted. Text is painted vertically centered
 * inside that padded allocation; the IconView is also center-aligned so the
 * icon sits next to the text rather than baseline-anchored below.
 */
public class EmojiChatBox extends JTextPane {

    public static final String EMOJI_ID_ATTR = "emoji.id";
    private static final String EMOJI_UID_ATTR = "emoji.uid";
    private static final AtomicLong EMOJI_UID_SEQ = new AtomicLong();

    // Matches the row height set by emoji_button/image_button in the .form so
    // the JTextPane lines up with the buttons on its left.
    private static final int FIXED_HEIGHT = 43;
    // Native emoji size (/images/emoji_chat/N.png are 32x32). The text line
    // is padded to this height so an emoji-bearing row is the same height as
    // a text-only row.
    static final int LINE_HEIGHT = 32;

    public EmojiChatBox() {
        super();
        setEditorKit(new CenteredIconEditorKit());
        Document doc = getDocument();
        if (doc instanceof AbstractDocument) {
            ((AbstractDocument) doc).setDocumentFilter(new SingleLineFilter());
        }
        int vpad = Math.max(0, (FIXED_HEIGHT - LINE_HEIGHT) / 2);
        setMargin(new Insets(vpad, 4, vpad, 4));
        // Caret with reduced height: by default the caret inherits the row
        // height (LINE_HEIGHT = 32) which looks oversized; this restricts it
        // to font height centered inside the row.
        setCaret(new ShortCaret());
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        d.height = FIXED_HEIGHT;
        return d;
    }

    @Override
    public Dimension getMaximumSize() {
        Dimension d = super.getMaximumSize();
        d.height = FIXED_HEIGHT;
        return d;
    }

    @Override
    public Dimension getMinimumSize() {
        Dimension d = super.getMinimumSize();
        d.height = FIXED_HEIGHT;
        return d;
    }

    /**
     * Inserts an emoji at the current caret position at native size. The
     * CenteredIconEditorKit renders it centered vertically with the text.
     */
    public void insertEmoji(int id, ImageIcon icon) {
        try {
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setIcon(attrs, icon);
            attrs.addAttribute(EMOJI_ID_ATTR, id);
            // Unique tag per insertion so DefaultStyledDocument doesn't merge
            // consecutive identical emojis into one Element (IconView paints
            // a single icon per Element, so a merged run would only render
            // the first one).
            attrs.addAttribute(EMOJI_UID_ATTR, EMOJI_UID_SEQ.incrementAndGet());
            getDocument().insertString(getCaretPosition(), " ", attrs);
            // CRITICAL: clear input attributes so chars the user types after
            // the emoji don't inherit the Icon / emoji.id attributes (which
            // would make getRawText emit #N# for each typed letter and lose
            // the real text on send).
            MutableAttributeSet input = getInputAttributes();
            input.removeAttributes(input);
        } catch (BadLocationException ex) {
            Logger.getLogger(EmojiChatBox.class.getName()).log(Level.SEVERE, "insertEmoji failed", ex);
        }
    }

    /**
     * Returns the message text with emojis encoded as " #N# " (legacy wire
     * format expected by parseEmojiChat on the receiving side).
     */
    public String getRawText() {
        Document doc = getDocument();
        int len = doc.getLength();
        if (len == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(len);
        try {
            for (int i = 0; i < len; i++) {
                Element charElem = getStyledDocument().getCharacterElement(i);
                Integer emojiId = (Integer) charElem.getAttributes().getAttribute(EMOJI_ID_ATTR);
                if (emojiId != null) {
                    sb.append(" #").append(emojiId).append("# ");
                } else {
                    sb.append(doc.getText(i, 1));
                }
            }
        } catch (BadLocationException ex) {
            Logger.getLogger(EmojiChatBox.class.getName()).log(Level.SEVERE, "getRawText failed", ex);
        }
        return sb.toString();
    }

    public boolean isRawBlank() {
        Document doc = getDocument();
        int len = doc.getLength();
        if (len == 0) {
            return true;
        }
        try {
            for (int i = 0; i < len; i++) {
                Element charElem = getStyledDocument().getCharacterElement(i);
                if (charElem.getAttributes().getAttribute(EMOJI_ID_ATTR) != null) {
                    return false;
                }
                char c = doc.getText(i, 1).charAt(0);
                if (!Character.isWhitespace(c)) {
                    return false;
                }
            }
        } catch (BadLocationException ex) {
            Logger.getLogger(EmojiChatBox.class.getName()).log(Level.SEVERE, "isRawBlank failed", ex);
            return false;
        }
        return true;
    }

    private static final class SingleLineFilter extends DocumentFilter {

        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
            if (string != null) {
                string = string.replace('\n', ' ').replace('\r', ' ');
            }
            super.insertString(fb, offset, string, attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
            if (text != null) {
                text = text.replace('\n', ' ').replace('\r', ' ');
            }
            super.replace(fb, offset, length, text, attrs);
        }
    }

    /**
     * StyledEditorKit returning a center-aligned IconView and a PaddedLabelView
     * for text so the row keeps a constant height with or without emojis.
     */
    private static final class CenteredIconEditorKit extends StyledEditorKit {

        private static final ViewFactory FACTORY = new ViewFactory() {
            @Override
            public View create(Element elem) {
                String kind = elem.getName();
                if (kind != null) {
                    if (kind.equals(StyleConstants.IconElementName)) {
                        return new IconView(elem) {
                            @Override
                            public float getAlignment(int axis) {
                                return axis == View.Y_AXIS ? 0.5f : super.getAlignment(axis);
                            }
                        };
                    }
                    if (kind.equals(AbstractDocument.ContentElementName)) {
                        return new PaddedLabelView(elem);
                    }
                    if (kind.equals(AbstractDocument.ParagraphElementName)) {
                        return new ParagraphView(elem);
                    }
                    if (kind.equals(AbstractDocument.SectionElementName)) {
                        return new BoxView(elem, View.Y_AXIS);
                    }
                    if (kind.equals(StyleConstants.ComponentElementName)) {
                        return new ComponentView(elem);
                    }
                }
                return new PaddedLabelView(elem);
            }
        };

        @Override
        public ViewFactory getViewFactory() {
            return FACTORY;
        }
    }

    /**
     * LabelView whose Y span is padded to LINE_HEIGHT so the row keeps the
     * same height regardless of whether it contains emojis. Text is painted
     * vertically centered inside the padded allocation. The natural text
     * height is computed from the font (not from super.getPreferredSpan,
     * which would return the already-padded value).
     */
    private static final class PaddedLabelView extends LabelView {

        PaddedLabelView(Element elem) {
            super(elem);
        }

        @Override
        public float getPreferredSpan(int axis) {
            float span = super.getPreferredSpan(axis);
            if (axis == View.Y_AXIS) {
                return Math.max(span, (float) LINE_HEIGHT);
            }
            return span;
        }

        @Override
        public float getAlignment(int axis) {
            return axis == View.Y_AXIS ? 0.5f : super.getAlignment(axis);
        }

        @Override
        public void paint(Graphics g, Shape allocation) {
            Rectangle r = allocation.getBounds();
            int natural = naturalTextHeight();
            if (r.height > natural) {
                int offset = (r.height - natural) / 2;
                Rectangle centered = new Rectangle(r.x, r.y + offset, r.width, natural);
                super.paint(g, centered);
            } else {
                super.paint(g, allocation);
            }
        }

        private int naturalTextHeight() {
            java.awt.Font font = getFont();
            java.awt.Container c = getContainer();
            if (font != null && c != null) {
                return c.getFontMetrics(font).getHeight();
            }
            return LINE_HEIGHT;
        }
    }

    /**
     * Caret painted at font height instead of inheriting the (taller) row
     * height, centered vertically within the cursor's natural rectangle.
     */
    private static final class ShortCaret extends DefaultCaret {

        @Override
        public void paint(Graphics g) {
            if (!isVisible() || g == null) {
                return;
            }
            JTextComponent c = getComponent();
            if (c == null) {
                return;
            }
            try {
                TextUI mapper = c.getUI();
                Rectangle r = mapper.modelToView(c, getDot());
                if (r == null || r.width < 0 || r.height < 0) {
                    return;
                }
                int natural = c.getFontMetrics(c.getFont()).getHeight();
                int height = Math.min(r.height, natural);
                int top = r.y + Math.max(0, (r.height - height) / 2);
                g.setColor(c.getCaretColor());
                g.fillRect(r.x, top, 1, height);
            } catch (BadLocationException ex) {
                // No caret position to paint at; nothing to do.
            }
        }
    }
}
