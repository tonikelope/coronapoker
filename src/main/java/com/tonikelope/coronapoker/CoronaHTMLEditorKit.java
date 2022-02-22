/*
 * Copyright (C) 2020 tonikelope
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
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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

    public static volatile boolean USE_GIF_CACHE = false;

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

                            label.setIcon(ChatImageURLDialog.STATIC_IMAGE_CACHE.containsKey(url) ? ChatImageURLDialog.STATIC_IMAGE_CACHE.get(url) : new ImageIcon(getClass().getResource("/images/loading.gif")));

                            float align = Float.parseFloat(text.split("@")[1]);

                            label.setAlignmentX(align);

                            if (!ChatImageURLDialog.STATIC_IMAGE_CACHE.containsKey(url)) {

                                Helpers.threadRun(new Runnable() {
                                    public void run() {

                                        try {

                                            ImageIcon image;

                                            boolean isgif = false;

                                            if (ChatImageURLDialog.STATIC_IMAGE_CACHE.containsKey(url)) {
                                                image = ChatImageURLDialog.STATIC_IMAGE_CACHE.get(url);
                                            } else if ((isgif = ChatImageURLDialog.GIF_CACHE.containsKey(url)) && USE_GIF_CACHE) {
                                                image = (ImageIcon) ChatImageURLDialog.GIF_CACHE.get(url)[0];
                                            } else {
                                                image = new ImageIcon(new URL(url + "#" + Helpers.genRandomString(20)));
                                            }

                                            if (ChatImageURLDialog.STATIC_IMAGE_CACHE.containsKey(url) || (USE_GIF_CACHE && ChatImageURLDialog.GIF_CACHE.containsKey(url)) || image.getImageLoadStatus() != MediaTracker.ERRORED) {

                                                if (image.getIconWidth() > ChatImageURLDialog.MAX_IMAGE_WIDTH) {
                                                    image = new ImageIcon(image.getImage().getScaledInstance(ChatImageURLDialog.MAX_IMAGE_WIDTH, (int) Math.round((image.getIconHeight() * ChatImageURLDialog.MAX_IMAGE_WIDTH) / image.getIconWidth()), (isgif || (isgif = Helpers.isImageGIF(new URL(url)))) ? Image.SCALE_DEFAULT : Image.SCALE_SMOOTH));
                                                }

                                                ImageIcon final_image = image;

                                                Helpers.GUIRun(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        label.setIcon(final_image);

                                                    }
                                                });

                                                if (!ChatImageURLDialog.GIF_CACHE.containsKey(url) && (isgif || Helpers.isImageGIF(new URL(url)))) {

                                                    ChatImageURLDialog.GIF_CACHE.put(url, new Object[]{image, Helpers.getGIFLength(new URL(url))});

                                                } else if (!ChatImageURLDialog.GIF_CACHE.containsKey(url)) {

                                                    ChatImageURLDialog.STATIC_IMAGE_CACHE.putIfAbsent(url, image);
                                                }

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

                                        } catch (Exception ex) {
                                            Logger.getLogger(CoronaHTMLEditorKit.class.getName()).log(Level.SEVERE, null, ex);
                                        }
                                    }
                                });

                            }

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
