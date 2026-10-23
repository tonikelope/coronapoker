/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.Texture.TextureFilter;
import java.util.List;

/** Mutable, OpenGL-free state for the standalone card-rendering laboratory. */
final class CardRenderLabModel {

    enum Setting {
        DECK("BARAJA",
                "Selecciona el diseño de baraja usado para la carta y su reverso."),
        CARD("CARTA",
                "Elige el valor y el palo. También puedes mostrar el comodín o el reverso."),
        MODE("MODO",
                "Compara el muestreo nativo o suave 16X, con SMAA activado o desactivado."),
        FILTER("FILTRO",
                "Cambia el filtro de minificación: Linear o una de las dos variantes mipmap."),
        LOD_BIAS("LOD BIAS",
                "Ajusta el nivel de detalle de los mipmaps. Los valores negativos conservan más detalle."),
        ANGLE("ÁNGULO",
                "Rota la carta para evaluar la nitidez de diagonales, esquinas y bordes."),
        SIZE("TAMAÑO",
                "Cambia la anchura física de la carta en píxeles del framebuffer, sin reescalar la captura.");

        private final String label;
        private final String tooltip;

        Setting(String label, String tooltip) {
            this.label = label;
            this.tooltip = tooltip;
        }

        String label() {
            return label;
        }

        String tooltip() {
            return tooltip;
        }
    }

    record RenderMode(String label, boolean smoothArtwork, boolean smaa) { }

    record FilterOption(String label, TextureFilter minification) { }

    private static final List<String> DECKS = List.of(
            "goliat", "coronapoker", "goliat4", "interstate60");
    private static final List<String> CARDS = List.of(
            "A_P", "K_P", "Q_P", "J_P", "10_P", "9_P", "8_P",
            "7_P", "6_P", "5_P", "4_P", "3_P", "2_P",
            "A_T", "K_T", "Q_T", "J_T", "10_T", "9_T", "8_T",
            "7_T", "6_T", "5_T", "4_T", "3_T", "2_T",
            "A_D", "K_D", "Q_D", "J_D", "10_D", "9_D", "8_D",
            "7_D", "6_D", "5_D", "4_D", "3_D", "2_D",
            "A_C", "K_C", "Q_C", "J_C", "10_C", "9_C", "8_C",
            "7_C", "6_C", "5_C", "4_C", "3_C", "2_C",
            "joker", "trasera");
    private static final List<RenderMode> MODES = List.of(
            new RenderMode("NATIVO", false, false),
            new RenderMode("SUAVE 16X", true, false),
            new RenderMode("NATIVO + SMAA", false, true),
            new RenderMode("SUAVE 16X + SMAA", true, true));
    private static final List<FilterOption> FILTERS = List.of(
            new FilterOption("LINEAR", TextureFilter.Linear),
            new FilterOption("MIP LINEAR / NEAREST",
                    TextureFilter.MipMapLinearNearest),
            new FilterOption("MIP LINEAR / LINEAR",
                    TextureFilter.MipMapLinearLinear));
    private static final List<Float> LOD_BIASES = List.of(
            -1f, -0.75f, -0.5f, -0.25f, 0f, 0.25f, 0.5f);
    private static final List<Float> ANGLES = List.of(
            0f, 5f, 7f, 8.5f, 10f, 15f, 20f, 30f);
    private static final List<Integer> SIZES = List.of(
            100, 125, 140, 160, 184, 220, 260, 300, 360, 420, 500);

    private int selectedSetting;
    private int deck;
    private int card = 2;
    private int mode = 2;
    private int filter = 1;
    private int lodBias = 2;
    private int angle = 5;
    private int size = 2;

    void reset() {
        selectedSetting = 0;
        deck = 0;
        card = 2;
        mode = 2;
        filter = 1;
        lodBias = 2;
        angle = 5;
        size = 2;
    }

    Setting selectedSetting() {
        return Setting.values()[selectedSetting];
    }

    void select(int delta) {
        selectedSetting = wrap(selectedSetting + delta,
                Setting.values().length);
    }

    void select(Setting setting) {
        selectedSetting = setting.ordinal();
    }

    void changeSelected(int delta) {
        change(selectedSetting(), delta);
    }

    void change(Setting setting, int delta) {
        switch (setting) {
            case DECK -> deck = wrap(deck + delta, DECKS.size());
            case CARD -> card = wrap(card + delta, CARDS.size());
            case MODE -> mode = wrap(mode + delta, MODES.size());
            case FILTER -> filter = wrap(filter + delta, FILTERS.size());
            case LOD_BIAS -> lodBias = wrap(lodBias + delta,
                    LOD_BIASES.size());
            case ANGLE -> angle = wrap(angle + delta, ANGLES.size());
            case SIZE -> size = wrap(size + delta, SIZES.size());
        }
    }

    String deck() {
        return DECKS.get(deck);
    }

    String card() {
        return CARDS.get(card);
    }

    RenderMode mode() {
        return MODES.get(mode);
    }

    FilterOption filter() {
        return FILTERS.get(filter);
    }

    float lodBias() {
        return LOD_BIASES.get(lodBias);
    }

    float angleDegrees() {
        return ANGLES.get(angle);
    }

    int cardWidth() {
        return SIZES.get(size);
    }

    String value(Setting setting) {
        return switch (setting) {
            case DECK -> deck();
            case CARD -> card();
            case MODE -> mode().label();
            case FILTER -> filter().label();
            case LOD_BIAS -> String.format(java.util.Locale.ROOT, "%+.2f",
                    lodBias());
            case ANGLE -> String.format(java.util.Locale.ROOT, "%.1f grados",
                    angleDegrees());
            case SIZE -> cardWidth() + " px";
        };
    }

    String cardResource() {
        return "images/decks/" + deck() + "/hq/" + card() + ".jpg";
    }

    String backResource() {
        return "images/decks/" + deck() + "/hq/trasera.jpg";
    }

    static List<String> decks() {
        return DECKS;
    }

    static List<String> cards() {
        return CARDS;
    }

    static List<RenderMode> modes() {
        return MODES;
    }

    static List<FilterOption> filters() {
        return FILTERS;
    }

    static List<Float> lodBiases() {
        return LOD_BIASES;
    }

    static List<Float> angles() {
        return ANGLES;
    }

    static List<Integer> sizes() {
        return SIZES;
    }

    private static int wrap(int value, int size) {
        return Math.floorMod(value, size);
    }
}
