package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class GdxFeltMaterialTest {
    @BeforeAll static void natives() { GdxNativesLoader.load(); }

    @ParameterizedTest
    @ValueSource(strings={"verde","rojo","azul","negro"})
    void bundledClothWrapHasNoExceptionalEdgeJump(String name) throws Exception {
        byte[] bytes;
        try(var stream=getClass().getResourceAsStream("/images/tapete_"+name+".jpg")) {
            assertNotNull(stream);
            bytes=stream.readAllBytes();
        }
        Pixmap source=new Pixmap(bytes,0,bytes.length);
        Pixmap tile=null;
        try {
            tile=GdxFeltMaterial.repeatingTile(source);
            for(boolean vertical:new boolean[]{false,true}) {
                double interior=0, boundary=0;
                int across=vertical?tile.getWidth():tile.getHeight();
                int length=vertical?tile.getHeight():tile.getWidth();
                for(int j=0;j<across;j++) {
                    boundary+=difference(pixel(tile,vertical,0,j),pixel(tile,vertical,length-1,j));
                    for(int i=1;i<length;i++) {
                        interior+=difference(pixel(tile,vertical,i,j),pixel(tile,vertical,i-1,j));
                    }
                }
                interior/=across*(length-1.0);
                boundary/=across;
                assertTrue(boundary<interior*1.18,
                        name+" exceptional repeat edge: "+boundary+" vs "+interior);
            }
            // Interior artwork stays byte-identical, preserving yarns and dye.
            assertEquals(source.getPixel(500,500),tile.getPixel(500,500));
        } finally {
            if(tile!=null)tile.dispose();
            source.dispose();
        }
    }

    private static int pixel(Pixmap p,boolean vertical,int i,int j) {
        return vertical?p.getPixel(j,i):p.getPixel(i,j);
    }
    private static double difference(int a,int b) {
        return (Math.abs((a>>>24)-(b>>>24))
                +Math.abs((a>>>16&255)-(b>>>16&255))
                +Math.abs((a>>>8&255)-(b>>>8&255)))/3.0;
    }
}
