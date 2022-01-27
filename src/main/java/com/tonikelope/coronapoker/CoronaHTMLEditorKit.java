/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tonikelope.coronapoker;

import java.awt.Component;
import java.awt.MediaTracker;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.text.Document;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import javax.swing.text.*;
import javax.swing.text.html.HTML;
import org.apache.commons.codec.binary.Base64;

/**
 *
 * T-H-A-N-K Y-O-U!!!!! --> https://stackoverflow.com/a/35012241
 */
class CoronaHTMLEditorKit extends HTMLEditorKit {

    public CoronaHTMLEditorKit() {
        super();
    }

    @Override
    public Document createDefaultDocument() {
        StyleSheet styles = getStyleSheet();
        StyleSheet ss = new StyleSheet();

        ss.addStyleSheet(styles);

        CoronaHTMLDocument doc = new CoronaHTMLDocument(ss);
        doc.setParser(getParser());
        doc.setAsynchronousLoadPriority(4);
        doc.setTokenThreshold(100);
        return doc;
    }

    @Override
    public ViewFactory getViewFactory() {
        return new MyHTMLFactory();
    }

    Parser defaultParser;

    @Override
    protected Parser getParser() {
        if (defaultParser == null) {
            defaultParser = new CoronaParserDelegator();
        }
        return defaultParser;
    }

    class MyHTMLFactory extends HTMLFactory implements ViewFactory {

        public MyHTMLFactory() {
            super();
        }

        @Override
        public View create(Element element) {
            HTML.Tag kind = (HTML.Tag) (element.getAttributes().getAttribute(javax.swing.text.StyleConstants.NameAttribute));

            if (kind instanceof HTML.UnknownTag && element.getName().equals("tonimg")) {

                return new ComponentView(element) {
                    @Override
                    protected Component createComponent() {
                        JLabel label = new JLabel();

                        try {
                            int start = getElement().getStartOffset();
                            int end = getElement().getEndOffset();
                            String text = getElement().getDocument().getText(start, end - start);

                            String url = new String(Base64.decodeBase64(text.split("@")[0]), "UTF-8");

                            float align = Float.parseFloat(text.split("@")[1]);

                            label.setAlignmentX(align);

                            ImageIcon image = new ImageIcon(new URL(url));

                            if (image.getImageLoadStatus() != MediaTracker.ERRORED) {

                                label.setIcon(new ImageIcon(new URL(url)));

                            } else {

                                label.setText("IMAGE ERROR -> " + url);
                                label.setIcon(new ImageIcon(getClass().getResource("/images/emoji_chat/95.png")));
                            }

                        } catch (BadLocationException e) {
                            e.printStackTrace();
                        } catch (MalformedURLException ex) {
                            Logger.getLogger(CoronaHTMLEditorKit.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (UnsupportedEncodingException ex) {
                            Logger.getLogger(CoronaHTMLEditorKit.class.getName()).log(Level.SEVERE, null, ex);
                        }

                        return label;
                    }
                };

            }
            return super.create(element);
        }
    }
}
