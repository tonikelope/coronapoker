/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;

/**
 * Resolves transparent silhouettes at the framebuffer pixel grid.
 *
 * <p>The texture sampler and its mip chain retain responsibility for artwork
 * detail by default. Small badges can also resolve their artwork with the
 * four rotated subpixel samples, using a finer mip level for each sample.</p>
 */
final class GdxAlphaEdgeRenderer implements Disposable {

    static final int COVERAGE_SAMPLE_COUNT = 4;

    static final String FRAGMENT_SHADER = fragmentShader(false);
    private static final String ARTWORK_FRAGMENT_SHADER = fragmentShader(true);

    private static String fragmentShader(boolean sampleArtwork) {
        // Each subpixel tap represents a smaller area than the output pixel.
        String lodBias = sampleArtwork ? ", -1.0" : "";
        return "#ifdef GL_ES\n"
            + "#extension GL_OES_standard_derivatives : enable\n"
            + "precision mediump float;\n"
            + "#endif\n"
            + "varying vec4 v_color;\n"
            + "varying vec2 v_texCoords;\n"
            + "uniform sampler2D u_texture;\n"
            + "void main() {\n"
            + "    vec2 uvDx = dFdx(v_texCoords);\n"
            + "    vec2 uvDy = dFdy(v_texCoords);\n"
            + (sampleArtwork ? "" : "    vec4 center = texture2D(u_texture, v_texCoords);\n")
            + "    vec4 s0 = texture2D(u_texture, v_texCoords"
            + " - uvDx * 0.375 - uvDy * 0.125" + lodBias + ");\n"
            + "    vec4 s1 = texture2D(u_texture, v_texCoords"
            + " - uvDx * 0.125 + uvDy * 0.375" + lodBias + ");\n"
            + "    vec4 s2 = texture2D(u_texture, v_texCoords"
            + " + uvDx * 0.125 - uvDy * 0.375" + lodBias + ");\n"
            + "    vec4 s3 = texture2D(u_texture, v_texCoords"
            + " + uvDx * 0.375 + uvDy * 0.125" + lodBias + ");\n"
            + "    float alphaWeight = s0.a + s1.a + s2.a + s3.a;\n"
            + "    float coverage = alphaWeight * 0.25;\n"
            + "    if (coverage <= 0.001) discard;\n"
            + "    vec3 edgeColor = (s0.rgb * s0.a + s1.rgb * s1.a"
            + " + s2.rgb * s2.a + s3.rgb * s3.a)"
            + " / max(alphaWeight, 0.0001);\n"
            + "    vec3 color = " + (sampleArtwork ? "edgeColor"
                    : "coverage < 0.999 ? edgeColor : center.rgb") + ";\n"
            + "    gl_FragColor = vec4(color, coverage) * v_color;\n"
            + "}\n";
    }

    private final ShaderProgram shader;

    GdxAlphaEdgeRenderer(String vertexShader) {
        this(vertexShader, FRAGMENT_SHADER);
    }

    static GdxAlphaEdgeRenderer forDetailedArtwork(String vertexShader) {
        return new GdxAlphaEdgeRenderer(vertexShader, ARTWORK_FRAGMENT_SHADER);
    }

    private GdxAlphaEdgeRenderer(String vertexShader, String fragmentShader) {
        shader = new ShaderProgram(vertexShader, fragmentShader);
        if (!shader.isCompiled()) {
            String log = shader.getLog();
            shader.dispose();
            throw new IllegalStateException("Alpha-edge shader: " + log);
        }
    }

    void begin(SpriteBatch batch) {
        if (batch.getShader() != shader) {
            batch.setShader(shader);
        }
        shader.setUniformi("u_texture", 0);
    }

    void end(SpriteBatch batch) {
        if (batch.getShader() == shader) {
            batch.setShader(null);
        }
    }

    @Override
    public void dispose() {
        shader.dispose();
    }
}
