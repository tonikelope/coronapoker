/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class GdxGraphicsInfoTest {

    @Test
    void detectsSoftwareOpenGlRenderersWithoutMislabelingRealGpus() {
        assertTrue(GdxGraphicsInfo.softwareRenderer(
                "llvmpipe (LLVM 18.1.8, 256 bits)"));
        assertTrue(GdxGraphicsInfo.softwareRenderer("Google SwiftShader"));
        assertTrue(GdxGraphicsInfo.softwareRenderer(
                "Microsoft Basic Render Driver"));
        assertFalse(GdxGraphicsInfo.softwareRenderer(
                "NVIDIA GeForce RTX 4070/PCIe/SSE2"));
        assertFalse(GdxGraphicsInfo.softwareRenderer(
                "Intel(R) Iris(R) Xe Graphics"));
    }

    @Test
    void reportsTheActualRendererInTheSelectedLanguage() {
        GdxGameText spanish = new GdxGameText("es");
        GdxGameText english = new GdxGameText("en");

        assertTrue(GdxGraphicsInfo.displayValue("NVIDIA GeForce RTX", spanish)
                .equals("Hardware (NVIDIA GeForce RTX)"));
        assertTrue(GdxGraphicsInfo.displayValue("llvmpipe", english)
                .startsWith("Software"));
        assertTrue(GdxGraphicsInfo.displayValue("", spanish)
                .equals("No disponible"));
    }
}
