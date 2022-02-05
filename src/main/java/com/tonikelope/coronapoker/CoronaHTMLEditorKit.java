/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tonikelope.coronapoker;

import static com.tonikelope.coronapoker.ChatImageURLDialog.IMAGE_ICON_CACHE;
import java.awt.Color;
import java.awt.Component;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
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

                        label.setIcon(new ImageIcon(getClass().getResource("/images/loading.gif")));

                        try {
                            int start = getElement().getStartOffset();

                            int end = getElement().getEndOffset();

                            String text = getElement().getDocument().getText(start, end - start);

                            String url = new String(Base64.decodeBase64(text.split("@")[0]), "UTF-8");

                            float align = Float.parseFloat(text.split("@")[1]);

                            label.setAlignmentX(align);

                            Helpers.threadRun(new Runnable() {
                                public void run() {

                                    try {
                                        ImageIcon image = (ChatImageURLDialog.IMAGE_ICON_CACHE.containsKey(url)) ? ChatImageURLDialog.IMAGE_ICON_CACHE.get(url) : new ImageIcon(new URL(url));

                                        if (ChatImageURLDialog.IMAGE_ICON_CACHE.containsKey(url) || image.getImageLoadStatus() != MediaTracker.ERRORED) {

                                            if (image.getIconWidth() > ChatImageURLDialog.MAX_IMAGE_WIDTH) {

                                                image = new ImageIcon(image.getImage().getScaledInstance(ChatImageURLDialog.MAX_IMAGE_WIDTH, (int) Math.round((image.getIconHeight() * ChatImageURLDialog.MAX_IMAGE_WIDTH) / image.getIconWidth()), Image.SCALE_DEFAULT));
                                            }

                                            IMAGE_ICON_CACHE.putIfAbsent(url, image);

                                            ImageIcon final_image = image;

                                            Helpers.GUIRun(new Runnable() {
                                                @Override
                                                public void run() {
                                                    label.setIcon(final_image);

                                                }
                                            });
                                        } else {

                                            Helpers.GUIRun(new Runnable() {
                                                @Override
                                                public void run() {
                                                    label.setText(Translator.translate("IMAGEN NO INSERTABLE"));
                                                    label.setIcon(new ImageIcon(getClass().getResource("/images/emoji_chat/95.png")));
                                                    label.setBackground(Color.RED);
                                                    label.setForeground(Color.white);
                                                    label.setOpaque(true);
                                                    label.addMouseListener(new MouseAdapter() {
                                                        @Override
                                                        public void mouseClicked(MouseEvent e) {

                                                            if (SwingUtilities.isLeftMouseButton(e)) {
                                                                Helpers.openBrowserURL(url);

                                                                if (WaitingRoomFrame.getInstance() != null) {
                                                                    WaitingRoomFrame.getInstance().getChat_box().requestFocus();
                                                                }
                                                            }

                                                        }
                                                    });
                                                }
                                            });
                                        }

                                    } catch (MalformedURLException ex) {
                                        Logger.getLogger(CoronaHTMLEditorKit.class.getName()).log(Level.SEVERE, null, ex);
                                    }
                                }
                            });

                        } catch (Exception ex) {

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
