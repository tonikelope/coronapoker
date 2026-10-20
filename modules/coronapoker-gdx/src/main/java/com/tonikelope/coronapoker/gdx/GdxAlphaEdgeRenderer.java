/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;

/**
 * Resolves the transparent silhouette of small UI artwork without filtering
 * its opaque interior a second time.
 *
 * <p>The texture sampler and its mip chain retain responsibility for artwork
 * detail. Four rotated subpixel samples are used only for alpha coverage, so
 * circular badges keep crisp lettering while their outside edge is resolved
 * at the physical framebuffer pixel grid.</p>
 */
final class GdxAlphaEdgeRenderer implements Disposable {

    static final int COVERAGE_SAMPLE_COUNT = 4;

    static final String FRAGMENT_SHADER = "#ifdef GL_ES\n"
            + "#extension GL_OES_standard_derivatives : enable\n"
            + "precision mediump float;\n"
            + "#endif\n"
            + "varying vec4 v_color;\n"
            + "varying vec2 v_texCoords;\n"
            + "uniform sampler2D u_texture;\n"
            + "void main() {\n"
            + "    vec2 uvDx = dFdx(v_texCoords);\n"
            + "    vec2 uvDy = dFdy(v_texCoords);\n"
            + "    vec4 center = texture2D(u_texture, v_texCoords);\n"
            + "    vec4 s0 = texture2D(u_texture, v_texCoords"
            + " - uvDx * 0.375 - uvDy * 0.125);\n"
            + "    vec4 s1 = texture2D(u_texture, v_texCoords"
            + " - uvDx * 0.125 + uvDy * 0.375);\n"
            + "    vec4 s2 = texture2D(u_texture, v_texCoords"
            + " + uvDx * 0.125 - uvDy * 0.375);\n"
            + "    vec4 s3 = texture2D(u_texture, v_texCoords"
            + " + uvDx * 0.375 + uvDy * 0.125);\n"
            + "    float alphaWeight = s0.a + s1.a + s2.a + s3.a;\n"
            + "    float coverage = alphaWeight * 0.25;\n"
            + "    if (coverage <= 0.001) discard;\n"
            // Only boundary pixels use the alpha-weighted edge colour. Fully
            // opaque pixels keep the centre lookup, so printed detail is not
            // averaged or sharpened by this pass.
            + "    vec3 edgeColor = (s0.rgb * s0.a + s1.rgb * s1.a"
            + " + s2.rgb * s2.a + s3.rgb * s3.a)"
            + " / max(alphaWeight, 0.0001);\n"
            + "    vec3 color = coverage < 0.999 ? edgeColor : center.rgb;\n"
            + "    gl_FragColor = vec4(color, coverage) * v_color;\n"
            + "}\n";

    private final ShaderProgram shader;

    GdxAlphaEdgeRenderer(String vertexShader) {
        shader = new ShaderProgram(vertexShader, FRAGMENT_SHADER);
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
