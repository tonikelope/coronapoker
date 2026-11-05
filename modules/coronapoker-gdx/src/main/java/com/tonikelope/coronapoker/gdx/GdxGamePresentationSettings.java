/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.Crupier;
import com.tonikelope.coronapoker.core.ApplicationMetadata;
import com.tonikelope.coronapoker.core.PreferencesService;
import com.tonikelope.coronapoker.core.game.GamePresentationSettings;
import com.tonikelope.coronapoker.core.media.ModMediaCatalog;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
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
    private static final List<Integer> CARD_RENDERING_MODES = List.of(
            GdxCardSmaaRenderer.RENDERING_NATIVE,
            GdxCardSmaaRenderer.RENDERING_SMOOTH);
    private static final List<Integer> FRAME_RATE_LIMITS = List.of(
            60, 120, 144, 240, 0);
    private final PreferencesService preferences;
    private final Properties properties;
    private final ModMediaCatalog modMedia;
    private final List<String> availableDecks;
    private final Map<String, String> normalizedDecks;
    private volatile boolean autoRebuyOnBroke;
    private volatile int actualMsaaSamples;
    private volatile AnimationFlags animationFlags;
    private String secretFeltFirst = "";
    private String secretFeltSecond = "";
    private int secretFeltSwitches;

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
        autoRebuyOnBroke = bool(GdxSettingsContract.AUTO_REBUY_KEY, false);
        refreshRuntimePreferences();
    }

    private record AnimationFlags(boolean master, boolean halo,
            boolean allInCinematics, boolean gameOverCinematics,
            boolean blindDealer, boolean bets, boolean counters,
            boolean seatEntry, boolean shuffle, boolean shufflePreload,
            boolean deal, boolean flip, boolean swap, boolean allInFire,
            boolean winnerEffect) {
    }

    /** Refreshes the immutable flags read by the render loop after an edit. */
    void refreshRuntimePreferences() {
        boolean master = bool("animaciones", true);
        boolean shuffle = master && bool("animacion_barajado", true);
        animationFlags = new AnimationFlags(master,
                master && bool(GdxSettingsContract.HALO_EFFECT_KEY, true),
                master && bool("cinematicas_allin", true),
                master && bool("cinematicas_gameover", true),
                master && bool("animacion_ciegas_dealer", true),
                master && bool("animacion_apuestas", true),
                master && bool("animacion_contadores", true),
                master && bool("animacion_entrada_asientos", true),
                shuffle,
                shuffle && bool("animacion_barajado_precarga", true),
                master && bool("animacion_reparto", true),
                master && bool("animacion_destape", true),
                master && bool("animacion_swap", true),
                master && bool("animacion_fuego_allin", true),
                master && bool("animacion_efecto_ganador", true));
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
    /** Master switch shared by every semantic table animation. */
    boolean animations() {
        return animationFlags.master();
    }

    boolean haloEffects() {
        return animationFlags.halo();
    }

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
    boolean secretFelt() {
        String configured = properties.getProperty("color_tapete", "verde")
                .toLowerCase(Locale.ROOT);
        return configured.endsWith("*")
                && FELTS.contains(configured.substring(0,
                        configured.length() - 1));
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
        String previous = felt();
        boolean wasSecret = secretFelt();
        String selected = normalizedFelt(felt);
        properties.setProperty("color_tapete", selected);
        if (wasSecret || selected.equals(previous)) {
            resetSecretFeltSwitches();
        } else {
            registerSecretFeltSwitch(previous, selected);
        }
        if (persist) preferences.saveDeferred();
        return selected;
    }
    private void registerSecretFeltSwitch(String previous, String selected) {
        boolean continuesPair = secretFeltSwitches > 0
                && ((previous.equals(secretFeltFirst)
                        && selected.equals(secretFeltSecond))
                    || (previous.equals(secretFeltSecond)
                        && selected.equals(secretFeltFirst)));
        if (continuesPair) {
            secretFeltSwitches++;
        } else {
            secretFeltFirst = previous;
            secretFeltSecond = selected;
            secretFeltSwitches = 1;
        }
        if (secretFeltSwitches >= 5) {
            properties.setProperty("color_tapete", selected + "*");
            resetSecretFeltSwitches();
        }
    }
    private void resetSecretFeltSwitches() {
        secretFeltFirst = "";
        secretFeltSecond = "";
        secretFeltSwitches = 0;
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
    String modVersion() { return modMedia.version(); }
    boolean modAdultWarningRequired() {
        return modMedia.requiresAdultAcknowledgement();
    }
    void acknowledgeModAdultContent() throws IOException {
        modMedia.acknowledgeAdultContent();
    }
    String modDisplayName() {
        return modDisplayName(modMedia.name(), modMedia.version());
    }
    static String modDisplayName(String name, String version) {
        String normalizedName = name == null ? "" : name.trim();
        String normalizedVersion = version == null ? "" : version.trim();
        if (normalizedName.isEmpty()) return "";
        if (normalizedVersion.isEmpty()) return normalizedName;
        String prefix = normalizedVersion.regionMatches(true, 0, "v", 0, 1)
                ? " " : " v";
        return normalizedName + prefix + normalizedVersion;
    }
    String productVersionLabel() {
        String product = "CoronaPoker " + ApplicationMetadata.VERSION;
        String mod = modDisplayName();
        return modActive() && !mod.isEmpty()
                ? product + " (" + mod + ")" : product;
    }
    boolean modDeck(String deck) {
        return deck != null && modMedia.decks().stream()
                .anyMatch(deck::equalsIgnoreCase);
    }
    Optional<Path> modAsset(String relativePath) {
        return modMedia.resolve(relativePath);
    }
    Optional<URI> modUpdateUri() { return modMedia.updateUri(); }
    String modUpdatePassword() { return modMedia.updatePassword(); }
    List<Path> modFiles(String relativeDirectory, String extension) {
        return modMedia.files(relativeDirectory, extension);
    }
    boolean fuseModSounds() { return modMedia.fuseSounds(); }
    boolean fuseModCinematics() { return modMedia.fuseCinematics(); }
    @Override public boolean testMode() {
        return Boolean.getBoolean("coronapoker.testMode");
    }
    @Override public boolean sillySounds() {
        return effects() && bool("sonidos_chorra", false);
    }
    @Override public boolean sillySoundEnabled(String resource) {
        if (!sillySounds() || resource == null || resource.isBlank()) {
            return false;
        }
        String categoryPreference = GdxSoundFeedback.funnyPreferenceKey(
                resource);
        return (categoryPreference == null
                || bool(categoryPreference, true))
                && bool(GamePresentationSettings.jokeSoundPreferenceKey(
                        resource), true);
    }
    @Override public boolean cinematicAudio() {
        return effects() && bool("sonido_cinematicas", true);
    }

    /** Active language bank after applying the MOD replacement/fusion rules. */
    List<String> jokeSoundResources(String category) {
        String selectedLanguage = "en".equalsIgnoreCase(language())
                ? "en" : "es";
        Map.Entry<String, String[]> bundled = switch (category) {
            case "allin" -> Crupier.ALLIN_SOUNDS.get(selectedLanguage);
            case "fold" -> Crupier.FOLD_SOUNDS.get(selectedLanguage);
            case "showdown" -> Crupier.SHOWDOWN_SOUNDS.get(selectedLanguage);
            case "winner" -> Crupier.WINNER_SOUNDS.get(selectedLanguage);
            case "loser" -> Crupier.LOSER_SOUNDS.get(selectedLanguage);
            default -> null;
        };
        if (bundled == null) return List.of();
        Map.Entry<String, String[]> selected = modMedia.soundCategory(
                selectedLanguage, category, bundled);
        return java.util.Arrays.stream(selected.getValue())
                .filter(Objects::nonNull)
                .filter(filename -> !filename.isBlank())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .map(filename -> selected.getKey() + filename)
                .distinct()
                .toList();
    }
    @Override public boolean ambientMusic() { return bool("sonido_ascensor", true); }
    @Override public boolean recoveryMusic() {
        return bool("musica_recuperacion", true);
    }
    @Override public boolean showCallCost() { return bool("mostrar_coste_igualar", true); }
    @Override public boolean autoActionButtons() {
        return bool("auto_action_buttons", false) && !testMode();
    }
    @Override public boolean autoActionPersist() { return bool("auto_action_persist", true); }
    @Override public boolean autoRebuyOnBroke() { return autoRebuyOnBroke; }
    void setAutoRebuyOnBroke(boolean enabled) {
        autoRebuyOnBroke = enabled;
        properties.setProperty(GdxSettingsContract.AUTO_REBUY_KEY,
                Boolean.toString(enabled));
    }
    int actualMsaaSamples() { return actualMsaaSamples; }
    void setActualMsaaSamples(int samples) {
        actualMsaaSamples = Math.max(0, samples);
    }
    int requestedMsaaSamples() {
        int configured = integer("gdx_msaa_samples", 4);
        return MSAA_LEVELS.contains(configured) ? configured : 4;
    }
    List<Integer> msaaLevels() {
        return MSAA_LEVELS;
    }
    int selectMsaaSamples(int samples, boolean persist) {
        int selected = MSAA_LEVELS.contains(samples) ? samples
                : requestedMsaaSamples();
        properties.setProperty("gdx_msaa_samples", Integer.toString(selected));
        if (persist) preferences.saveDeferred();
        return selected;
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
        return selectMsaaSamples(selected, persist);
    }
    int requestedCardRenderingMode() {
        int configured = integer(GdxSettingsContract.CARD_RENDERING_KEY,
                GdxSettingsContract.DEFAULT_CARD_RENDERING_MODE);
        return CARD_RENDERING_MODES.contains(configured) ? configured
                : GdxSettingsContract.DEFAULT_CARD_RENDERING_MODE;
    }

    List<Integer> cardRenderingModes() {
        return CARD_RENDERING_MODES;
    }

    int selectCardRenderingMode(int mode, boolean persist) {
        int selected = CARD_RENDERING_MODES.contains(mode) ? mode
                : requestedCardRenderingMode();
        properties.setProperty(GdxSettingsContract.CARD_RENDERING_KEY,
                Integer.toString(selected));
        if (persist) preferences.saveDeferred();
        return selected;
    }
    int selectNextCardRenderingMode() {
        return selectNextCardRenderingMode(true);
    }
    int selectNextCardRenderingMode(boolean persist) {
        return adjustCardRenderingMode(1, persist);
    }
    int selectPreviousCardRenderingMode() {
        return selectPreviousCardRenderingMode(true);
    }
    int selectPreviousCardRenderingMode(boolean persist) {
        return adjustCardRenderingMode(-1, persist);
    }
    private int adjustCardRenderingMode(int direction, boolean persist) {
        int current = CARD_RENDERING_MODES.indexOf(requestedCardRenderingMode());
        int selected = CARD_RENDERING_MODES.get(Math.floorMod(current
                + Integer.signum(direction), CARD_RENDERING_MODES.size()));
        return selectCardRenderingMode(selected, persist);
    }
    int requestedFrameRateLimit() {
        int configured = integer("gdx_fps_limit",
                GdxSettingsContract.DEFAULT_FRAME_RATE_LIMIT);
        return FRAME_RATE_LIMITS.contains(configured) ? configured
                : GdxSettingsContract.DEFAULT_FRAME_RATE_LIMIT;
    }
    List<Integer> frameRateLimits() {
        return FRAME_RATE_LIMITS;
    }
    int selectFrameRateLimit(int limit, boolean persist) {
        int selected = FRAME_RATE_LIMITS.contains(limit) ? limit
                : requestedFrameRateLimit();
        properties.setProperty("gdx_fps_limit", Integer.toString(selected));
        if (persist) preferences.saveDeferred();
        return selected;
    }
    boolean vsyncEnabled() {
        return bool("gdx_vsync", GdxSettingsContract.DEFAULT_VSYNC);
    }
    String frameRateOverlayLabel(int framesPerSecond) {
        return Math.max(0, framesPerSecond) + " FPS (VS "
                + (vsyncEnabled() ? "ON" : "OFF") + ")";
    }
    boolean toggleVsync(boolean persist) {
        boolean selected = !vsyncEnabled();
        properties.setProperty("gdx_vsync", Boolean.toString(selected));
        if (persist) preferences.saveDeferred();
        return selected;
    }
    int effectiveFrameRateLimit(int monitorRefreshRate) {
        int monitorRate = monitorRefreshRate > 0 ? monitorRefreshRate : 60;
        int configured = requestedFrameRateLimit();
        if (!vsyncEnabled()) {
            return configured == 0 ? monitorRate : configured;
        }
        // GLFW's swap interval is the authoritative limiter at the display
        // refresh rate. Giving LibGDX's software pacer the exact same target
        // makes both clocks compete at the frame boundary. Keep an explicit
        // lower cap (for example 60 FPS on a 144 Hz monitor), otherwise place
        // the software ceiling one frame above VSync so only the swap controls
        // the refresh-rate boundary.
        if (configured > 0 && configured < monitorRate) return configured;
        return monitorRate + 1;
    }
    int selectNextFrameRateLimit() {
        return selectNextFrameRateLimit(true);
    }
    int selectNextFrameRateLimit(boolean persist) {
        return adjustFrameRateLimit(1, persist);
    }
    int selectPreviousFrameRateLimit() {
        return selectPreviousFrameRateLimit(true);
    }
    int selectPreviousFrameRateLimit(boolean persist) {
        return adjustFrameRateLimit(-1, persist);
    }
    private int adjustFrameRateLimit(int direction, boolean persist) {
        int current = FRAME_RATE_LIMITS.indexOf(requestedFrameRateLimit());
        int selected = FRAME_RATE_LIMITS.get(Math.floorMod(current
                + Integer.signum(direction), FRAME_RATE_LIMITS.size()));
        return selectFrameRateLimit(selected, persist);
    }
    @Override public boolean autoFullscreen() {
        return GdxWindowMode.allowsAutomaticFullscreen(properties)
                && bool("auto_fullscreen", true);
    }
    @Override public boolean cinematics() {
        return animations();
    }
    @Override public boolean allInCinematics() {
        return animationFlags.allInCinematics();
    }
    @Override public boolean gameOverCinematics() {
        return animationFlags.gameOverCinematics();
    }
    @Override public boolean blindDealerAnimation() {
        return animationFlags.blindDealer();
    }
    @Override public boolean betAnimation() {
        return animationFlags.bets();
    }
    @Override public boolean counterAnimation() {
        return animationFlags.counters();
    }
    boolean seatEntryAnimation() {
        return animationFlags.seatEntry();
    }
    @Override public boolean shuffleAnimation() {
        return animationFlags.shuffle();
    }
    boolean shufflePreload() {
        return animationFlags.shufflePreload();
    }
    @Override public boolean dealAnimation() {
        return animationFlags.deal();
    }
    @Override public boolean flipAnimation() {
        return animationFlags.flip();
    }
    @Override public boolean swapAnimation() {
        return animationFlags.swap();
    }
    boolean allInFireAnimation() {
        return animationFlags.allInFire();
    }
    boolean winnerEffectAnimation() {
        return animationFlags.winnerEffect();
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
