/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tonikelope.coronapoker;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import javax.swing.text.html.parser.ParserDelegator;
import javax.swing.text.html.parser.DTD;
import javax.swing.text.html.HTMLEditorKit.Parser;
import javax.swing.text.html.HTMLEditorKit.ParserCallback;
import javax.swing.text.html.parser.DocumentParser;

/**
 *
 * T-H-A-N-K Y-O-U!!!!! --> https://stackoverflow.com/a/35012241
 */
class CoronaParserDelegator extends Parser {

    private DTD _dtd;

    public CoronaParserDelegator() {
        String nm = "html32";
        try {
            _dtd = DTD.getDTD(nm);
            createDTD(_dtd, nm);

            javax.swing.text.html.parser.Element div = _dtd.getElement("div");
            _dtd.defineElement("tonimg", div.getType(), true, true, div.getContent(), null, null, div.getAttributes());
        } catch (IOException e) {
            // (PENDING) UGLY!
            System.out.println("Throw an exception: could not get default dtd: " + nm);
        }
    }

    protected static DTD createDTD(DTD dtd, String name) {
        InputStream in = null;
        try {
            String path = name + ".bdtd";
            in = ParserDelegator.class.getResourceAsStream(path);
            if (in != null) {
                dtd.read(new DataInputStream(new BufferedInputStream(in)));
                DTD.putDTDHash(name, dtd);
            }
        } catch (Exception e) {
            System.out.println(e);
        }
        return dtd;
    }

    @Override
    public void parse(Reader r, ParserCallback cb, boolean ignoreCharSet) throws IOException {
        new DocumentParser(_dtd).parse(r, cb, ignoreCharSet);
    }
}
