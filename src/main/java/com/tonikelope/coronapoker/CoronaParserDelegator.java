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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * T-H-A-N-K Y-O-U!!!!! --> https://stackoverflow.com/a/35012241
 */
class CoronaParserDelegator extends Parser {

    private static final Logger LOGGER = Logger.getLogger(CoronaParserDelegator.class.getName());

    private DTD _dtd;

    public CoronaParserDelegator() {
        String nm = "html32";
        try {
            _dtd = DTD.getDTD(nm);
            createDTD(_dtd, nm);

            javax.swing.text.html.parser.Element div = _dtd.getElement("div");
            _dtd.defineElement("tonimg", div.getType(), true, true, div.getContent(), null, null, div.getAttributes());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not get default dtd: " + nm, e);
        }
    }

    protected static DTD createDTD(DTD dtd, String name) {
        String path = name + ".bdtd";
        // try-with-resources: el InputStream del JAR resource queda colgando
        // (handle a la entrada del JAR) si no se cierra explícito. Cada
        // CoronaHTMLEditorKit (chat de partida, log de timba) crea uno.
        try (InputStream in = ParserDelegator.class.getResourceAsStream(path)) {
            if (in != null) {
                dtd.read(new DataInputStream(new BufferedInputStream(in)));
                DTD.putDTDHash(name, dtd);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error specialized DTD creation", e);
        }
        return dtd;
    }

    @Override
    public void parse(Reader r, ParserCallback cb, boolean ignoreCharSet) throws IOException {
        new DocumentParser(_dtd).parse(r, cb, ignoreCharSet);
    }
}
