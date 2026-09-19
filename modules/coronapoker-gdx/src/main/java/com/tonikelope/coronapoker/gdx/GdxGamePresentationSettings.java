/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.PreferencesService;
import com.tonikelope.coronapoker.core.game.GamePresentationSettings;
import com.tonikelope.coronapoker.core.media.ModMediaCatalog;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Properties;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Reads the same persisted presentation contract used by the classic client. */
final class GdxGamePresentationSettings implements GamePresentationSettings {

    static final List<String> OFFICIAL_DECKS = List.of(
            "goliat", "coronapoker", "goliat4", "interstate60");
    static final List<String> FELTS = List.of(
            "verde", "azul", "rojo", "negro", "madera");
    private static final List<Integer> MSAA_LEVELS = List.of(0, 2, 4, 8);
    private final PreferencesService preferences;
    private final Properties properties;
    private final ModMediaCatalog modMedia;
    private final List<String> availableDecks;
    private final Map<String, String> normalizedDecks;
    private volatile boolean autoRebuyOnBroke;
    private volatile int actualMsaaSamples;

    GdxGamePresentationSettings(PreferencesService preferences) {
        this(preferences, discoverInstalledMod());
    }

    GdxGamePresentationSettings(PreferencesService preferences,
            ModMediaCatalog modMedia) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.modMedia = Objects.requireNonNull(modMedia, "modMedia");
        properties = preferences.properties();
        LinkedHashMap<String, String> decks = new LinkedHashMap<>();
        for (String deck : OFFICIAL_DECKS) {
            decks.put(deck.toLowerCase(Locale.ROOT), deck);
        }
        for (String deck : modMedia.decks()) {
            decks.putIfAbsent(deck.toLowerCase(Locale.ROOT), deck);
        }
        normalizedDecks = Map.copyOf(decks);
        availableDecks = List.copyOf(new ArrayList<>(decks.values()));
    }

    static ModMediaCatalog discoverInstalledMod() {
        String override = System.getProperty("coronapoker.install.dir", "");
        if (!override.isBlank()) {
            return ModMediaCatalog.discover(Path.of(override));
        }
        try {
            Path location = Path.of(GdxLauncher.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).toAbsolutePath()
                    .normalize();
            Path installation = Files.isRegularFile(location)
                    ? location.getParent()
                    : Path.of(System.getProperty("user.dir", "."));
            return ModMediaCatalog.discover(installation);
        } catch (Exception failure) {
            return ModMediaCatalog.discover(
                    Path.of(System.getProperty("user.dir", ".")));
        }
    }

    private boolean bool(String key, boolean fallback) {
        return Boolean.parseBoolean(properties.getProperty(key,
                Boolean.toString(fallback)));
    }

    private int integer(String key, int fallback) {
        try {
            return Integer.parseInt(properties.getProperty(key,
                    Integer.toString(fallback)));
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }

    private boolean effects() { return bool("sonido_efectos", true); }
    /**
     * Semantic GDX animation cannot be disabled globally. Individual
     * animation switches remain available and are applied below; this also
     * prevents a persisted Swing-only master value from degrading GDX.
     */
    private boolean animations() { return true; }

    @Override public String language() {
        return properties.getProperty("lenguaje", "es").toLowerCase(Locale.ROOT);
    }
    @Override public String defaultLanguage() { return "es"; }
    @Override public String deck() {
        String configured = properties.getProperty("baraja", "goliat")
                .toLowerCase(Locale.ROOT);
        return normalizedDecks.getOrDefault(configured, "goliat");
    }
    @Override public String selectNextDeck() {
        return selectNextDeck(true);
    }
    String selectNextDeck(boolean persist) {
        int current = availableDecks.indexOf(deck());
        return selectDeck(availableDecks.get(
                (current + 1) % availableDecks.size()), persist);
    }
    String selectPreviousDeck() {
        return selectPreviousDeck(true);
    }
    String selectPreviousDeck(boolean persist) {
        int current = availableDecks.indexOf(deck());
        return selectDeck(availableDecks.get(previousIndex(
                current, availableDecks.size())), persist);
    }
    @Override public String selectDeck(String deck) {
        return selectDeck(deck, true);
    }
    String selectDeck(String deck, boolean persist) {
        String normalized = deck == null ? "" : deck.toLowerCase(Locale.ROOT);
        String selected = normalizedDecks.getOrDefault(normalized, this.deck());
        properties.setProperty("baraja", selected);
        if (persist) preferences.saveDeferred();
        return selected;
    }
    String felt() {
        return normalizedFelt(properties.getProperty("color_tapete", "verde"));
    }
    String selectNextFelt() {
        return selectNextFelt(true);
    }
    String selectNextFelt(boolean persist) {
        String current = felt();
        int index = FELTS.indexOf(current);
        return selectFelt(FELTS.get((index + 1) % FELTS.size()), persist);
    }
    String selectPreviousFelt() {
        return selectPreviousFelt(true);
    }
    String selectPreviousFelt(boolean persist) {
        int index = FELTS.indexOf(felt());
        return selectFelt(FELTS.get(previousIndex(index, FELTS.size())),
                persist);
    }
    String selectFelt(String felt) {
        return selectFelt(felt, true);
    }
    String selectFelt(String felt, boolean persist) {
        String selected = normalizedFelt(felt);
        properties.setProperty("color_tapete", selected);
        if (persist) preferences.saveDeferred();
        return selected;
    }
    static String normalizedFelt(String felt) {
        String normalized = felt == null ? "" : felt.toLowerCase(Locale.ROOT);
        if (normalized.endsWith("*")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return FELTS.contains(normalized) ? normalized : "verde";
    }
    List<String> availableDecks() { return availableDecks; }
    List<String> availableCardBacks() {
        ArrayList<String> backs = new ArrayList<>();
        backs.add("default");
        backs.addAll(availableDecks);
        return List.copyOf(backs);
    }
    String cardBack() {
        String configured = properties.getProperty("trasera", "default")
                .toLowerCase(Locale.ROOT);
        return "default".equals(configured)
                ? "default" : normalizedDecks.getOrDefault(configured,
                        "default");
    }
    String cardBackDeck() {
        return "default".equals(cardBack()) ? deck() : cardBack();
    }
    String selectNextCardBack() {
        return selectNextCardBack(true);
    }
    String selectNextCardBack(boolean persist) {
        List<String> backs = availableCardBacks();
        int current = backs.indexOf(cardBack());
        return selectCardBack(backs.get((current + 1) % backs.size()), persist);
    }
    String selectPreviousCardBack() {
        return selectPreviousCardBack(true);
    }
    String selectPreviousCardBack(boolean persist) {
        List<String> backs = availableCardBacks();
        int current = backs.indexOf(cardBack());
        return selectCardBack(backs.get(previousIndex(current, backs.size())),
                persist);
    }
    String selectCardBack(String cardBack) {
        return selectCardBack(cardBack, true);
    }
    String selectCardBack(String cardBack, boolean persist) {
        String normalized = cardBack == null
                ? "" : cardBack.toLowerCase(Locale.ROOT);
        String selected = "default".equals(normalized)
                ? "default" : normalizedDecks.getOrDefault(normalized,
                        this.cardBack());
        properties.setProperty("trasera", selected);
        if (persist) preferences.saveDeferred();
        return selected;
    }
    private static int previousIndex(int current, int size) {
        if (size <= 0) throw new IllegalArgumentException("empty choices");
        return Math.floorMod(current - 1, size);
    }
    boolean modActive() { return modMedia.installed(); }
    boolean modDeck(String deck) {
        return deck != null && modMedia.decks().stream()
                .anyMatch(deck::equalsIgnoreCase);
    }
    Optional<Path> modAsset(String relativePath) {
        return modMedia.resolve(relativePath);
    }
    List<Path> modFiles(String relativeDirectory, String extension) {
        return modMedia.files(relativeDirectory, extension);
    }
    boolean fuseModSounds() { return modMedia.fuseSounds(); }
    boolean fuseModCinematics() { return modMedia.fuseCinematics(); }
    @Override public boolean testMode() {
        return Boolean.getBoolean("coronapoker.testMode");
    }
    @Override public boolean sillySounds() { return bool("sonidos_chorra", false); }
    @Override public boolean ambientMusic() { return bool("sonido_ascensor", true); }
    @Override public boolean showCallCost() { return bool("mostrar_coste_igualar", true); }
    @Override public boolean autoActionButtons() {
        return bool("auto_action_buttons", false) && !testMode();
    }
    @Override public boolean autoActionPersist() { return bool("auto_action_persist", true); }
    @Override public boolean autoRebuyOnBroke() { return autoRebuyOnBroke; }
    void setAutoRebuyOnBroke(boolean enabled) { autoRebuyOnBroke = enabled; }
    int actualMsaaSamples() { return actualMsaaSamples; }
    void setActualMsaaSamples(int samples) {
        actualMsaaSamples = Math.max(0, samples);
    }
    int requestedMsaaSamples() {
        int configured = integer("gdx_msaa_samples", 4);
        return MSAA_LEVELS.contains(configured) ? configured : 4;
    }
    int selectNextMsaaSamples() {
        return selectNextMsaaSamples(true);
    }
    int selectNextMsaaSamples(boolean persist) {
        return adjustMsaaSamples(1, persist);
    }
    int selectPreviousMsaaSamples() {
        return selectPreviousMsaaSamples(true);
    }
    int selectPreviousMsaaSamples(boolean persist) {
        return adjustMsaaSamples(-1, persist);
    }
    private int adjustMsaaSamples(int direction, boolean persist) {
        int current = MSAA_LEVELS.indexOf(requestedMsaaSamples());
        int selected = MSAA_LEVELS.get(Math.floorMod(current
                + Integer.signum(direction), MSAA_LEVELS.size()));
        properties.setProperty("gdx_msaa_samples", Integer.toString(selected));
        if (persist) preferences.saveDeferred();
        return selected;
    }
    @Override public boolean autoFullscreen() { return bool("auto_fullscreen", true); }
    @Override public boolean cinematics() {
        return animations() && bool("cinematicas", true);
    }
    @Override public boolean allInCinematics() {
        return cinematics() && bool("cinematicas_allin", true);
    }
    @Override public boolean gameOverCinematics() {
        return cinematics() && bool("cinematicas_gameover", true);
    }
    @Override public boolean blindDealerAnimation() {
        return animations() && bool("animacion_ciegas_dealer", true);
    }
    @Override public boolean betAnimation() {
        return animations() && bool("animacion_apuestas", true);
    }
    @Override public boolean counterAnimation() {
        return animations() && bool("animacion_contadores", true);
    }
    @Override public boolean shuffleAnimation() {
        return animations() && bool("animacion_barajado", true);
    }
    @Override public boolean dealAnimation() {
        return animations() && bool("animacion_reparto", true);
    }
    @Override public boolean flipAnimation() {
        return animations() && bool("animacion_destape", true);
    }
    @Override public boolean swapAnimation() {
        return animations() && bool("animacion_swap", true);
    }
    @Override public boolean callSound() { return effects() && bool("sonido_igualar", true); }
    @Override public boolean betSound() { return effects() && bool("sonido_apostar", true); }
    @Override public boolean blindSound() { return effects() && bool("sonido_ciegas", true); }
    @Override public boolean shuffleSound() { return effects() && bool("sonido_barajado", true); }
    @Override public boolean dealSound() { return effects() && bool("sonido_reparto", true); }
    @Override public boolean flipSound() { return effects() && bool("sonido_destape", true); }
    boolean ownHoleFlipSound() {
        return flipSound() && bool("sonido_destape_mis_cartas", false);
    }
    boolean checkSound() { return effects() && bool("sonido_pasar", true); }
    boolean foldSound() { return effects() && bool("sonido_fold", true); }
    boolean allInSound() { return effects() && bool("sonido_allin", true); }
    @Override public boolean cashSound() { return effects() && bool("sonido_caja", true); }
    @Override public boolean iwtsthSound() { return effects() && bool("sonido_iwtsth", true); }
    @Override public boolean startSound() { return effects() && bool("sonido_inicio", true); }
    @Override public boolean warningSound() { return effects() && bool("sonido_aviso", true); }
    @Override public boolean turnWarningSound() {
        return effects() && bool("sonido_aviso_tiempo", true);
    }
    @Override public boolean errorSound() { return effects() && bool("sonido_error", true); }
    @Override public String initialStackFillSound() {
        return effects() && bool("sonido_carga_stacks", true)
                ? "misc/balance_count.wav" : null;
    }
    @Override public String cashRegisterSound() {
        return cashSound() ? "misc/cash_register.wav" : null;
    }
    /** GDX owns one adaptive table layout; Swing's compact modes do not apply. */
    @Override public int compactView() { return 0; }
    @Override public int dealSpeed() { return integer("reparto_velocidad", 100); }
    @Override public int swapAnimationDuration() { return integer("swap_velocidad", 320); }
    @Override public boolean swapAnimationArc() { return bool("swap_arco", false); }
    /** The libGDX viewport, not Swing's persisted zoom, owns table scaling. */
    @Override public float zoomFactor() { return 1f; }
    /** GDX dialogs already scale with the viewport and must not inherit Swing zoom. */
    @Override public float dialogZoomFactor() { return 1f; }
}
