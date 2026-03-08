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

import java.io.IOException;
import java.io.OutputStream;

/**
 * A custom OutputStream that splits the output to two different streams. Acts
 * like the UNIX 'tee' command. Now includes an inline byte-filter to remove
 * <0xa0> (Non-Breaking Spaces) and replace them with standard spaces.
 */
public class TeeOutputStream extends OutputStream {

    private final OutputStream out1;
    private final OutputStream out2;

    // Estado para rastrear el primer byte del <0xa0> en UTF-8 (0xC2)
    private boolean pendingC2 = false;

    public TeeOutputStream(OutputStream out1, OutputStream out2) {
        this.out1 = out1;
        this.out2 = out2;
    }

    @Override
    public void write(int b) throws IOException {
        int unsignedByte = b & 0xFF;

        // El carácter <0xa0> en UTF-8 se compone de dos bytes: 0xC2 seguido de 0xA0.
        // Si detectamos el 0xC2, lo retenemos temporalmente.
        if (unsignedByte == 0xC2) {
            pendingC2 = true;
            return;
        }

        if (pendingC2) {
            pendingC2 = false;
            if (unsignedByte == 0xA0) {
                // ¡Cazado! Era la secuencia 0xC2 0xA0. Escribimos un espacio normal (0x20).
                out1.write(0x20);
                out2.write(0x20);
                return;
            } else {
                // Falsa alarma. Era un 0xC2 de otro carácter (como una letra acentuada).
                // Escribimos el 0xC2 que habíamos retenido y seguimos.
                out1.write(0xC2);
                out2.write(0xC2);
            }
        }

        // Por si la consola escupe el carácter en formato ANSI puro (un solo byte 0xA0)
        if (unsignedByte == 0xA0) {
            out1.write(0x20);
            out2.write(0x20);
            return;
        }

        // Cualquier otro byte normal se escribe por la tubería en T
        out1.write(b);
        out2.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        // Redirigimos los arrays de bytes a nuestro método write(int) para 
        // asegurarnos de que el filtro no falle si el carácter <0xa0> 
        // se queda cortado por la mitad entre dos paquetes.
        for (int i = off; i < off + len; i++) {
            this.write(b[i]);
        }
    }

    @Override
    public void flush() throws IOException {
        out1.flush();
        out2.flush();
    }

    @Override
    public void close() throws IOException {
        try {
            out1.close();
        } finally {
            out2.close();
        }
    }
}
