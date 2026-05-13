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

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import javax.swing.JTextPane;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

/**
 * Single-line JTextPane that supports inline emoji icons. Each emoji is stored
 * as a single dummy character in the Document carrying an Icon attribute (so
 * Swing renders the image inline) plus an integer "emoji.id" attribute used by
 * {@link #getRawText()} to rebuild the legacy wire format " #N# " expected by
 * the chat HTML parser. Caret navigation, backspace and delete behave on the
 * emoji as if it were a normal single character.
 */
public class EmojiChatBox extends JTextPane {

    public static final String EMOJI_ID_ATTR = "emoji.id";

    public EmojiChatBox() {
        super();
        Document doc = getDocument();
        if (doc instanceof AbstractDocument) {
            ((AbstractDocument) doc).setDocumentFilter(new SingleLineFilter());
        }
    }

    /**
     * Inserts an emoji at the current caret position. Reuses the icon supplied
     * by EmojiPanel so we don't pay for a second load.
     */
    public void insertEmoji(int id, ImageIcon icon) {
        try {
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setIcon(attrs, icon);
            attrs.addAttribute(EMOJI_ID_ATTR, id);
            getDocument().insertString(getCaretPosition(), " ", attrs);
        } catch (BadLocationException ex) {
            Logger.getLogger(EmojiChatBox.class.getName()).log(Level.SEVERE, "insertEmoji failed", ex);
        }
    }

    /**
     * Returns the message text using the legacy " #N# " encoding for emojis so
     * the rest of the chat pipeline (network send + parseEmojiChat) keeps
     * working unchanged.
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

    /**
     * Convenience: getRawText().isBlank() for use in document listeners that
     * toggle UI based on whether the chat box has content. An emoji counts as
     * non-blank.
     */
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
}
