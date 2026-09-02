package com.tonikelope.coronapoker.core.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WireFrameCodecTest {
    @Test void preservesTextAndBinaryFramingOnOneStream() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write("PING#7\r\n".getBytes(StandardCharsets.ISO_8859_1));
        WireFrameCodec.writeBinary(output, new byte[]{0, 1, 2, (byte) 255});
        ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
        assertEquals("PING#7", WireFrameCodec.read(input, 32).text());
        assertArrayEquals(new byte[]{0, 1, 2, (byte) 255}, WireFrameCodec.read(input, 32).binary());
    }

    @Test void rejectsOversizedFramesBeforeAllocatingTheirBodies() {
        byte[] forged = {0, 0x7f, (byte) 0xff, (byte) 0xff, (byte) 0xff};
        assertThrows(IOException.class,
                () -> WireFrameCodec.read(new ByteArrayInputStream(forged), 1024));
        assertThrows(IOException.class,
                () -> WireFrameCodec.read(new ByteArrayInputStream("12345".getBytes()), 4));
    }
}
