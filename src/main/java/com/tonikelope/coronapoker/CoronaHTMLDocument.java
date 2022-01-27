/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tonikelope.coronapoker;

import java.net.URL;
import javax.swing.text.Document;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

/**
 *
 * T-H-A-N-K Y-O-U!!!!! --> https://stackoverflow.com/a/35012241
 */
class CoronaHTMLDocument extends HTMLDocument {

    public CoronaHTMLDocument(StyleSheet styles) {
        super(styles);
    }

    @Override
    public HTMLEditorKit.ParserCallback getReader(int pos) {
        Object desc = getProperty(Document.StreamDescriptionProperty);
        if (desc instanceof URL) {
            setBase((URL) desc);
        }
        return new MyHTMLReader(pos);
    }

    class MyHTMLReader extends HTMLDocument.HTMLReader {

        public MyHTMLReader(int offset) {
            super(offset);
        }

        @Override
        public void handleStartTag(HTML.Tag t, MutableAttributeSet a, int pos) {
            if (t.toString().equals("tonimg")) {
                registerTag(t, new BlockAction());
            }
            super.handleStartTag(t, a, pos);
        }
    }
}
