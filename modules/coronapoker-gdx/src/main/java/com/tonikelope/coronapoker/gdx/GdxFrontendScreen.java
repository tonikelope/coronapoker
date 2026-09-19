package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.tonikelope.coronapoker.core.LobbyChatMessage;
import com.tonikelope.coronapoker.core.LobbyCommand;
import com.tonikelope.coronapoker.core.LobbyParticipant;
import com.tonikelope.coronapoker.core.LobbySession;
import com.tonikelope.coronapoker.core.LobbySnapshot;
import com.tonikelope.coronapoker.core.NewGameConnectionDraft;
import com.tonikelope.coronapoker.core.NewGameSessionGateway;
import com.tonikelope.coronapoker.core.NewGameSubmissionCoordinator;
import com.tonikelope.coronapoker.core.NewGameTableDraft;
import com.tonikelope.coronapoker.core.PreferencesService;
import com.tonikelope.coronapoker.core.BlindStructureCatalog;
import com.tonikelope.coronapoker.core.BlindStructureRules;
import com.tonikelope.coronapoker.core.GamePresetCatalog;
import com.tonikelope.coronapoker.core.RecoverableGameRepository;
import com.tonikelope.coronapoker.DebugLog;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javax.imageio.ImageIO;

/** Native menu and staged NewGameDialog replacement. */
final class GdxFrontendScreen extends ApplicationAdapter implements InputProcessor {

    private static final Logger LOGGER = Logger.getLogger(
            GdxFrontendScreen.class.getName());

    private static final float WIDTH = 1920f;
    private static final float HEIGHT = 1080f;
    static final float MENU_LOGO_X = 42f;
    static final float MENU_LOGO_TOP = 32f;
    static final float MENU_LOGO_WIDTH = 320f;
    private static final float MENU_REVEAL_SECONDS = 0.78f;
    private static final String SPRITE_VERTEX_SHADER = "attribute vec4 a_position;\n"
            + "attribute vec4 a_color;\n"
            + "attribute vec2 a_texCoord0;\n"
            + "uniform mat4 u_projTrans;\n"
            + "varying vec4 v_color;\n"
            + "varying vec2 v_texCoords;\n"
            + "void main() {\n"
            + "    v_color = a_color;\n"
            + "    v_color.a = v_color.a * (255.0 / 254.0);\n"
            + "    v_texCoords = a_texCoord0;\n"
            + "    gl_Position = u_projTrans * a_position;\n"
            + "}\n";
    private static final String AVATAR_FRAGMENT_SHADER = "#ifdef GL_ES\n"
            + "precision mediump float;\n"
            + "#endif\n"
            + "varying vec4 v_color;\n"
            + "varying vec2 v_texCoords;\n"
            + "uniform sampler2D u_texture;\n"
            + "void main() {\n"
            + "    vec2 radial = (v_texCoords - vec2(0.5)) * 2.0;\n"
            + "    float mask = 1.0 - smoothstep(0.92, 1.0, length(radial));\n"
            + "    vec4 pixel = texture2D(u_texture, v_texCoords) * v_color;\n"
            + "    pixel.a *= mask;\n"
            + "    if (pixel.a <= 0.001) discard;\n"
            + "    gl_FragColor = pixel;\n"
            + "}\n";
    private static final Color BACKGROUND = new Color(0x031a14ff);
    private static final Color PANEL = new Color(0x101a2ecc);
    private static final Color PANEL_LIGHT = new Color(0x111a2add);
    private static final Color CYAN = new Color(0x36d9ffff);
    private static final Color CYAN_DARK = new Color(0x176b83ff);
    private static final Color GOLD = new Color(0xffe07aff);
    private static final Color ORANGE = new Color(0xff6b27ff);
    private static final Color LINE = new Color(0x31445fff);
    private static final Color MUTED = new Color(0xdbe5f3ff);
    private static final Color DISABLED = new Color(0x526078ff);
    private static final Color LATENCY_GREEN = new Color(0x4caf50ff);
    private static final Color LATENCY_YELLOW = new Color(0xffc107ff);
    private static final Color LATENCY_ORANGE = new Color(0xff9800ff);
    private static final Color LATENCY_RED = new Color(0xf44336ff);
    private static final Color LATENCY_STALE = new Color(0x9e9e9eff);
    private static final int SETTINGS_DEBUG_VISIBLE_LINES = 15;
    private static final int SETTINGS_SHORTCUT_ROWS_PER_PAGE = 5;
    private static final int EMOJI_COUNT = 1826;
    private static final int EMOJI_COLUMNS = 8;
    private static final int EMOJI_ROWS = 4;
    private static final int EMOJI_PAGE_SIZE = EMOJI_COLUMNS * EMOJI_ROWS;
    private static final Pattern EMOJI_TOKEN = Pattern.compile("#([0-9]{1,4})#");
    private static final float VOICE_RECORD_MAX_SECONDS = 15f;
    private static final float INPUT_CARET_HALF_PERIOD_SECONDS = 0.50f;
    private static final float COMPOSER_EMOJI_SIZE = 32f;
    private static final float COMPOSER_EMOJI_ADVANCE = 36f;
    private static final DateTimeFormatter CHAT_TIME = DateTimeFormatter
            .ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private static final float IMAGE_SEND_COOLDOWN_SECONDS = 2f;

    private final FitViewport viewport = new FitViewport(WIDTH, HEIGHT);
    private final List<TextItem> texts = new ArrayList<>();
    private final List<LobbyAvatarItem> lobbyAvatars = new ArrayList<>();
    private final List<UiImageItem> uiImages = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private final List<TextFieldHit> textFieldHits = new ArrayList<>();
    private final List<Hit> editMenuHits = new ArrayList<>();
    private final Map<String, Texture> lobbyAvatarTextures = new HashMap<>();
    private final Map<Integer, Texture> emojiTextures = new HashMap<>();
    private final Map<Long, LobbyMedia> lobbyMedia = new HashMap<>();
    private final Map<String, LobbyMedia> lobbyHistoryMedia = new HashMap<>();
    private final Map<String, Float> toggleAnimations = new HashMap<>();
    private final Map<String, Float> hoverAnimations = new HashMap<>();
    private final GlyphLayout glyph = new GlyphLayout();
    private final Vector2 pointer = new Vector2();
    private final GdxTextEditState textEdit = new GdxTextEditState();
    private final GdxKeyRepeat textDeleteRepeat = new GdxKeyRepeat();
    private final Properties initialProperties;
    private final PreferencesService preferences;
    private final GdxAudioControl audioControl;
    private final GdxShortcutBindings shortcutBindings;
    private final GdxGamePresentationSettings presentationSettings;
    private final GdxGameText gameText;
    private final Consumer<String> languageChanged;
    private final NewGameSubmissionCoordinator submissions;
    private final RecoverableGameRepository recoverableGames;
    private final ExecutorService recoveryExecutor;
    private final Consumer<NewGameSubmissionCoordinator.OpenedSession> sessionAccepted;
    private final Runnable sessionReturnedToMenu;
    private NewGameConnectionDraft connection;
    private NewGameTableDraft table = new NewGameTableDraft();
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private Texture feltTexture;
    private Texture logo;
    private Texture avatarDefault;
    private Texture avatarBot;
    private Texture selectedAvatarTexture;
    private Texture soundIcon;
    private Texture muteIcon;
    private Texture talkIcon;
    private Sound soundEnabledCue;
    private Sound soundDisabledCue;
    private Sound participantJoinedCue;
    private Sound participantLeftCue;
    private final Map<String, Sound> preferenceSoundCues = new HashMap<>();
    private Music backgroundMusic;
    private Music waitingRoomMusic;
    private ShaderProgram avatarShader;
    private BitmapFont titleFont;
    private BitmapFont headingFont;
    private BitmapFont actionFont;
    private BitmapFont uiFont;
    private BitmapFont smallFont;
    private BitmapFont tinyFont;
    private int page;
    private String activeField;
    private long toastUntil;
    private String toast = "";
    private float elapsed;
    private float frameDelta;
    private float menuRevealStartedAt = Float.NaN;
    private boolean startupAudioHeld;
    private Hit pressedHit;
    private EditMenu editMenu;
    private String pointerSelectionField;
    private Surface surface;
    private int historyIndex = -1;
    private boolean disposed;
    private boolean avatarSelectionPending;
    private LobbySession lobbySession;
    private LobbySnapshot lobby;
    private AutoCloseable lobbySubscription;
    private String lobbyChatDraft = "";
    private int lobbyChatScroll;
    private int lobbyChatMessageCount;
    private String lobbyImageDraft = "";
    private List<String> lobbyImageHistory = List.of();
    private float lobbyImageSendAllowedAt;
    private boolean lobbyEmojiPickerOpen;
    private boolean lobbyImageMode;
    private int lobbyEmojiPage;
    private volatile GdxVoiceRecorder lobbyVoiceRecorder;
    private boolean lobbyVoiceOpening;
    private boolean lobbyVoiceLive;
    private boolean lobbyVoiceStopping;
    private float lobbyVoiceLiveAt;
    private String lobbyVoiceStatus = "";
    private float lobbyVoiceStatusAt;
    private long lastLobbyMediaSequence = -1L;
    private String selectedParticipant;
    private boolean lobbyCommandPending;
    private boolean lobbyGameStarting;
    private LobbyConfirmation lobbyConfirmation;
    private PresetDialog presetDialog = PresetDialog.NONE;
    private final GdxBlindStructureEditor blindStructureEditor =
            new GdxBlindStructureEditor();
    private BlindStructureDialog blindStructureDialog =
            BlindStructureDialog.NONE;
    private String blindStructureNameDraft = "";
    private boolean blindStructureSettingsTarget;
    private List<GamePresetCatalog.Entry> gamePresets = List.of();
    private int selectedGamePreset = -1;
    private String presetNameDraft = "";
    private Surface settingsReturnSurface = Surface.MENU;
    private final GdxSettingsSession settingsSession =
            new GdxSettingsSession();
    private int settingsAppearancePage;
    private int settingsAudioPage;
    private int settingsGamePage;
    private int settingsShortcutPage;
    private int settingsDebugScroll;
    private String settingsShortcutCaptureId;
    private String settingsShortcutStatus = "";
    private GdxWindowMode settingsOpenedWindowMode = GdxWindowMode.BORDERLESS;
    private NewGameTableDraft settingsTable;
    private NewGameTableDraft.Settings settingsTableSnapshot;
    private boolean settingsDiscardConfirmation;
    private long recoveryLoadGeneration;
    private boolean autoSubmitRecovery;

    GdxFrontendScreen(PreferencesService preferences,
            NewGameSessionGateway gateway, RecoverableGameRepository recoverableGames,
            Consumer<NewGameSubmissionCoordinator.OpenedSession> sessionAccepted,
            Runnable sessionReturnedToMenu,
            GdxGamePresentationSettings presentationSettings,
            GdxGameText gameText, Consumer<String> languageChanged) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        initialProperties = this.preferences.properties();
        this.connection = defaultConnection(initialProperties,
                NewGameConnectionDraft.Mode.CREATE);
        audioControl = new GdxAudioControl(initialProperties, this.preferences);
        shortcutBindings = new GdxShortcutBindings(initialProperties);
        submissions = new NewGameSubmissionCoordinator(this.preferences,
                Objects.requireNonNull(gateway, "gateway"));
        this.recoverableGames = Objects.requireNonNull(recoverableGames,
                "recoverableGames");
        recoveryExecutor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task,
                    "CoronaPoker-GDX-recovery-loader");
            thread.setDaemon(true);
            return thread;
        });
        this.sessionAccepted = Objects.requireNonNull(sessionAccepted, "sessionAccepted");
        this.sessionReturnedToMenu = Objects.requireNonNull(
                sessionReturnedToMenu, "sessionReturnedToMenu");
        this.presentationSettings = Objects.requireNonNull(
                presentationSettings, "presentationSettings");
        this.gameText = Objects.requireNonNull(gameText, "gameText");
        this.languageChanged = Objects.requireNonNull(languageChanged,
                "languageChanged");
        surface = Surface.MENU;
    }

    private static NewGameConnectionDraft defaultConnection(Properties properties,
            NewGameConnectionDraft.Mode mode) {
        return NewGameConnectionDraft.from(properties, mode);
    }

    @Override
    public void create() {
        GdxAudioDevices.applyConfiguredOutput(initialProperties);
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        String felt = presentationSettings.felt();
        feltTexture = new Texture(Gdx.files.internal(
                "images/tapete_" + felt + ".jpg"));
        feltTexture.setFilter(TextureFilter.Linear, TextureFilter.Nearest);
        feltTexture.setWrap(TextureWrap.Repeat, TextureWrap.Repeat);
        logo = new Texture(Gdx.files.internal("images/corona_poker_splash.png"));
        logo.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        avatarDefault = filteredTexture("images/avatar_default.png");
        avatarBot = filteredTexture("images/avatar_bot.png");
        soundIcon = filteredTexture("images/sound.png");
        muteIcon = filteredTexture("images/mute.png");
        talkIcon = filteredTexture("images/talk.png");
        soundEnabledCue = Gdx.audio.newSound(
                Gdx.files.internal("sounds/misc/button_on.wav"));
        soundDisabledCue = Gdx.audio.newSound(
                Gdx.files.internal("sounds/misc/button_off.wav"));
        participantJoinedCue = Gdx.audio.newSound(
                Gdx.files.internal("sounds/misc/laser.wav"));
        participantLeftCue = Gdx.audio.newSound(
                Gdx.files.internal("sounds/misc/toilet.wav"));
        backgroundMusic = music("sounds/misc/background_music.mp3", 0.40f);
        waitingRoomMusic = music("sounds/misc/waiting_room.mp3", 0.90f);
        avatarShader = new ShaderProgram(SPRITE_VERTEX_SHADER,
                AVATAR_FRAGMENT_SHADER);
        if (!avatarShader.isCompiled()) {
            throw new IllegalStateException("Avatar shader: "
                    + avatarShader.getLog());
        }
        FreeTypeFontGenerator titleGenerator = new FreeTypeFontGenerator(
                Gdx.files.internal("fonts/McLaren-Regular.ttf"));
        titleFont = font(titleGenerator, 58, 0.35f);
        titleGenerator.dispose();
        FreeTypeFontGenerator displayGenerator = new FreeTypeFontGenerator(
                Gdx.files.internal("fonts/McLaren-Regular.ttf"));
        headingFont = font(displayGenerator, 30, 0.2f);
        actionFont = font(displayGenerator, 26, 0.2f);
        displayGenerator.dispose();
        FreeTypeFontGenerator bodyGenerator = new FreeTypeFontGenerator(
                Gdx.files.internal("fonts/McLaren-Regular.ttf"));
        uiFont = font(bodyGenerator, 24, 0f);
        smallFont = font(bodyGenerator, 18, 0f);
        tinyFont = font(bodyGenerator, 15, 0f);
        bodyGenerator.dispose();
        Gdx.input.setInputProcessor(this);
        Gdx.input.setCursorCatched(false);
        // The frontend owns the music while the startup intro is on screen too.
        // It is created first by the shell, so the same stream continues into
        // the menu instead of restarting when the intro renderer is disposed.
        syncMusicForSurface();
    }

    /** Keeps the menu-owned music silent until the visual intro turns on its lights. */
    void holdStartupAudio() {
        startupAudioHeld = true;
    }

    /** Starts the menu audio at the intro light-up boundary, exactly once. */
    void releaseStartupAudio() {
        if (!startupAudioHeld) return;
        startupAudioHeld = false;
        // Keep parity with the classic frontend: application startup has its
        // own cue and preference.  It is not a settings-switch interaction.
        playPreferenceSound("misc/init.wav", "sonido_arranque", 1f);
        syncMusicForSurface();
    }

    /** Reloads a felt changed from the live table before revealing this screen. */
    void refreshFeltFromSettings() {
        if (feltTexture == null) return;
        Texture replacement = new Texture(Gdx.files.internal(
                "images/tapete_" + presentationSettings.felt() + ".jpg"));
        replacement.setFilter(TextureFilter.Linear, TextureFilter.Nearest);
        replacement.setWrap(TextureWrap.Repeat, TextureWrap.Repeat);
        Texture previous = feltTexture;
        feltTexture = replacement;
        previous.dispose();
    }

    private static BitmapFont font(FreeTypeFontGenerator generator, int size,
            float border) {
        FreeTypeFontParameter p = new FreeTypeFontParameter();
        p.size = size;
        p.color = Color.WHITE;
        p.borderColor = new Color(0x02050ccc);
        p.borderWidth = border;
        p.hinting = FreeTypeFontGenerator.Hinting.Full;
        p.kerning = true;
        p.minFilter = TextureFilter.Linear;
        p.magFilter = TextureFilter.Linear;
        return generator.generateFont(p);
    }

    @Override
    public void render() {
        frameDelta = Math.min(Gdx.graphics.getDeltaTime(), 1f / 20f);
        elapsed += frameDelta;
        updateTextDeleteRepeat();
        updateLobbyVoiceRecording();
        syncMusicForSurface();
        ScreenUtils.clear(BACKGROUND);
        viewport.apply();
        viewport.getCamera().update();
        shapes.setProjectionMatrix(viewport.getCamera().combined);
        batch.setProjectionMatrix(viewport.getCamera().combined);
        texts.clear();
        lobbyAvatars.clear();
        uiImages.clear();
        hits.clear();
        textFieldHits.clear();
        editMenuHits.clear();

        drawFeltBackground();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (surface == Surface.MENU) {
            drawMainMenu();
        } else if (surface == Surface.LOBBY) {
            drawLobby();
        } else if (surface == Surface.SETTINGS) {
            drawSettingsScreen();
        } else {
            drawHeader();
            drawProgress();
            switch (page) {
                case 0 -> drawIdentityPage();
                case 1 -> drawBlindsPage();
                case 2 -> drawPurchasePage();
                case 3 -> drawGamePage();
                default -> drawBotsPage();
            }
            drawFooter();
        }
        if (System.currentTimeMillis() < toastUntil) {
            drawToast();
        }
        if (editMenu != null && lobbyConfirmation == null
                && presetDialog == PresetDialog.NONE
                && blindStructureDialog == BlindStructureDialog.NONE
                && !settingsDiscardConfirmation
                && !lobbyGameStarting) {
            drawEditMenu();
        }
        shapes.end();

        batch.begin();
        if (!lobbyAvatars.isEmpty()) {
            batch.setShader(avatarShader);
            batch.setColor(Color.WHITE);
            for (LobbyAvatarItem item : lobbyAvatars) {
                batch.draw(item.texture, item.x, item.y, item.size, item.size);
            }
            batch.flush();
            batch.setShader(null);
        }
        batch.setColor(Color.WHITE);
        for (UiImageItem item : uiImages) {
            batch.draw(item.texture, item.x, item.y, item.width, item.height);
        }
        for (TextItem item : texts) {
            item.font.setColor(item.color);
            glyph.setText(item.font, item.text);
            float x = item.centered ? item.x - glyph.width / 2f : item.x;
            item.font.draw(batch, item.text, x, item.y);
        }
        batch.end();

        drawStartupMenuReveal();

        // Modal surfaces must be composed after every underlying glyph. Texts
        // are batched separately from shapes, so drawing the modal inside
        // drawLobby would otherwise let the lobby chat glyphs bleed over it.
        if ((surface == Surface.LOBBY
                && (lobbyConfirmation != null || lobbyGameStarting))
                || (surface == Surface.SETTINGS
                && (settingsDiscardConfirmation
                        || blindStructureDialog
                                != BlindStructureDialog.NONE))
                || (surface == Surface.NEW_GAME
                && (presetDialog != PresetDialog.NONE
                        || blindStructureDialog != BlindStructureDialog.NONE))) {
            texts.clear();
            // SpriteBatch changes the current OpenGL pipeline. Restore alpha
            // blending before composing the modal shape pass; otherwise the
            // glass highlights/shadows (and the dimmer itself) become fully
            // opaque white/black bars on some drivers.
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA,
                    GL20.GL_ONE_MINUS_SRC_ALPHA);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            if (blindStructureDialog != BlindStructureDialog.NONE) {
                drawBlindStructureDialog();
            } else if (surface == Surface.SETTINGS) {
                drawSettingsDiscardConfirmation();
            } else if (surface == Surface.NEW_GAME) {
                if (blindStructureDialog != BlindStructureDialog.NONE) {
                    drawBlindStructureDialog();
                } else {
                    drawPresetDialog();
                }
            } else if (lobbyConfirmation != null) {
                drawLobbyConfirmation();
            } else {
                drawLobbyLoadingOverlay();
            }
            shapes.end();
            batch.begin();
            for (TextItem item : texts) {
                item.font.setColor(item.color);
                glyph.setText(item.font, item.text);
                float x = item.centered
                        ? item.x - glyph.width / 2f : item.x;
                item.font.draw(batch, item.text, x, item.y);
            }
            batch.end();
        }
    }

    void beginStartupReveal() {
        menuRevealStartedAt = elapsed;
        pressedHit = null;
    }

    void openLobby(LobbySession session) {
        closeLobbySubscription();
        lobbySession = Objects.requireNonNull(session, "session");
        lobby = session.snapshot();
        lobbyChatDraft = "";
        lobbyChatScroll = 0;
        lobbyChatMessageCount = lobby.chat().size();
        lobbyImageDraft = "";
        lobbyImageHistory = GdxChatImageHistory.read(initialProperties);
        lobbyImageSendAllowedAt = 0f;
        lobbyEmojiPickerOpen = false;
        lobbyImageMode = false;
        lobbyVoiceStatus = "";
        lastLobbyMediaSequence = lobby.chat().stream()
                .mapToLong(LobbyChatMessage::sequence).max().orElse(-1L);
        clearLobbyMedia();
        refreshLobbyHistoryMedia();
        for (LobbyChatMessage message : lobby.chat()) {
            if (message.type() == LobbyChatMessage.Type.IMAGE) {
                loadLobbyImage(message);
            }
        }
        selectedParticipant = null;
        lobbyCommandPending = false;
        lobbyGameStarting = false;
        lobbyConfirmation = null;
        clearActiveField();
        surface = Surface.LOBBY;
        if (connection != null
                && connection.mode() == NewGameConnectionDraft.Mode.JOIN) {
            playPreferenceSound("misc/yahoo.wav", "sonido_conexion", 0.82f);
        }
        lobbySubscription = session.subscribe(next -> Gdx.app.postRunnable(() -> {
            if (!disposed && lobbySession == session) {
                playLobbyRosterChange(lobby, next);
                lobby = next;
                if (surface == Surface.SETTINGS
                        && settingsReturnSurface == Surface.LOBBY
                        && !next.host() && next.tableSettings() != null) {
                    settingsTable = NewGameTableDraft.from(
                            next.tableSettings());
                    if (next.recovering()) settingsTable.setEconomyLocked(true);
                    // A client sees the host's live mirror and cannot edit it.
                    // Treat every received mirror as the new transactional
                    // baseline; otherwise closing this read-only page after a
                    // host update falsely reports unsaved local changes.
                    settingsTableSnapshot = settingsTable.snapshot();
                }
                handleLobbyMedia(next);
            }
        }));
    }

    private void playLobbyRosterChange(LobbySnapshot previous,
            LobbySnapshot next) {
        LobbyRosterChange change = lobbyRosterChange(previous, next);
        if (!audioControl.enabled()
                || !preferenceBoolean("sonido_efectos", true)) {
            return;
        }
        float volume = masterVolume();
        if (change.joined()
                && preferenceBoolean("sonido_entra", true)) {
            participantJoinedCue.play(volume);
        }
        if (change.left()
                && preferenceBoolean("sonido_sale", true)) {
            participantLeftCue.play(volume);
        }
    }

    static LobbyRosterChange lobbyRosterChange(LobbySnapshot previous,
            LobbySnapshot next) {
        if (previous == null || next == null) {
            return new LobbyRosterChange(false, false);
        }
        Set<String> before = new HashSet<>();
        for (LobbyParticipant participant : previous.participants()) {
            before.add(participant.nickname());
        }
        Set<String> after = new HashSet<>();
        for (LobbyParticipant participant : next.participants()) {
            after.add(participant.nickname());
        }
        boolean joined = after.stream().anyMatch(nick -> !before.contains(nick));
        boolean left = before.stream().anyMatch(nick -> !after.contains(nick));
        return new LobbyRosterChange(joined, left);
    }

    record LobbyRosterChange(boolean joined, boolean left) {
    }

    void showSessionError(String detail) {
        lobbyGameStarting = false;
        String language = presentationSettings.language();
        String localized = "en".equalsIgnoreCase(language) ? "en" : "es";
        playPreferenceSound("misc/network_error_" + localized + ".wav",
                "sonido_error_red", 0.88f);
        showToast(detail == null || detail.isBlank()
                ? "No se pudo abrir la mesa" : detail);
    }

    private void drawFeltBackground() {
        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(feltTexture, 0f, 0f, WIDTH, HEIGHT,
                0f, 0f, WIDTH / feltTexture.getWidth(),
                HEIGHT / feltTexture.getHeight());
        if (surface == Surface.MENU || surface == Surface.LOBBY
                || surface == Surface.SETTINGS) {
            float logoWidth = surface == Surface.MENU ? MENU_LOGO_WIDTH : 240f;
            float logoHeight = logoWidth * logo.getHeight() / logo.getWidth();
            float topInset = surface == Surface.MENU ? MENU_LOGO_TOP : 18f;
            batch.draw(logo, MENU_LOGO_X, HEIGHT - topInset - logoHeight,
                    logoWidth, logoHeight);
        }
        batch.setColor(Color.WHITE);
        batch.end();
    }

    private void drawMainMenu() {
        panel(515f, 155f, 890f, 650f, "");
        mainMenuButton(595f, 625f, 730f, 82f,
                gameText.translate("game.crear_timba"), 0, true,
                () -> openNewGame(NewGameConnectionDraft.Mode.CREATE));
        mainMenuButton(595f, 520f, 730f, 82f,
                gameText.translate("game.unirme_a_timba"), 1, false,
                () -> openNewGame(NewGameConnectionDraft.Mode.JOIN));
        mainMenuButton(595f, 415f, 730f, 82f,
                gameText.translate("stats.estadisticas_2"), 2, false,
                () -> showToast(gameText.translate("gdx.stats_pending")));
        mainMenuButton(595f, 310f, 350f, 82f,
                uppercase(gameText.translate("menu.ajustes")), 3, false,
                this::openSettings);
        choice(975f, 310f, 350f, 82f, "",
                gameText.translate("gdx.language_name"),
                this::toggleLanguage);
        mainMenuButton(595f, 205f, 350f, 82f,
                uppercase(gameText.translate("menu.acerca_de")), 4, false,
                () -> showToast(gameText.translate("about.titulo")));
        mainMenuButton(975f, 205f, 350f, 82f,
                gameText.translate("ui.salir"), 5, false,
                Gdx.app::exit);

        keyHint(535f, 75f, "F11",
                uppercase(gameText.translate("settings.modo_pantalla_completa")));
        drawSoundControl(1336f, 70f, 55f, 55f, false);
    }

    private String uppercase(String value) {
        Locale locale = Locale.forLanguageTag(gameText.language());
        return value == null ? "" : value.toUpperCase(locale);
    }

    private String settingsGameText(String suffix, Object... arguments) {
        return uppercase(gameText.translate("gdx.settings.game." + suffix,
                arguments));
    }

    private void toggleLanguage() {
        String next = "es".equalsIgnoreCase(presentationSettings.language())
                ? "en" : "es";
        initialProperties.setProperty("lenguaje", next);
        languageChanged.accept(next);
        if (preferences != null) preferences.saveDeferred();
        showToast(gameText.translate("gdx.language_changed"));
    }

    private void drawStartupMenuReveal() {
        float progress = menuRevealProgress();
        if (surface != Surface.MENU || progress >= 1f) {
            return;
        }
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.begin();
        batch.setColor(1f, 1f, 1f, 1f - progress);
        batch.draw(feltTexture, 0f, 0f, WIDTH, HEIGHT,
                0f, 0f, WIDTH / feltTexture.getWidth(),
                HEIGHT / feltTexture.getHeight());
        float logoHeight = MENU_LOGO_WIDTH * logo.getHeight() / logo.getWidth();
        batch.setColor(Color.WHITE);
        batch.draw(logo, MENU_LOGO_X, HEIGHT - MENU_LOGO_TOP - logoHeight,
                MENU_LOGO_WIDTH, logoHeight);
        batch.end();
    }

    private float menuRevealProgress() {
        if (Float.isNaN(menuRevealStartedAt)) {
            return 1f;
        }
        return com.badlogic.gdx.math.Interpolation.smoother.apply(
                MathUtils.clamp((elapsed - menuRevealStartedAt)
                        / MENU_REVEAL_SECONDS, 0f, 1f));
    }

    private void drawLobby() {
        LobbySnapshot state = lobby;
        if (state == null) {
            return;
        }
        String lobbyTitle = uppercase(gameText.translate("gdx.lobby.title"));
        text(titleFont, lobbyTitle, 59f, 890f,
                new Color(0x000000aa), false);
        text(titleFont, lobbyTitle, 55f, 894f, GOLD, false);

        panel(35f, 180f, 430f, 650f,
                uppercase(gameText.translate("game.timba")));
        text(tinyFont, uppercase(gameText.translate("ui.servidor")),
                70f, 748f, MUTED, false);
        textFit(smallFont, state.serverAddress(), 70f, 718f,
                Color.WHITE, false, 360f);
        drawLobbyGameInfo(state, 70f, 645f);
        if (state.host()) {
            button(70f, 392f, 360f, 64f,
                    uppercase(gameText.translate("ui.anadir_bot")), false,
                    () -> submitLobbyCommand(new LobbyCommand.AddBot(), null),
                    !lobbyCommandPending
                            && state.participants().size() < LobbySnapshot.MAX_PARTICIPANTS
                            && !state.startingOrStarted());
            boolean kickEnabled = selectedRemoteParticipant(state) != null
                    && !state.startingOrStarted();
            themedButton(70f, 312f, 360f, 64f,
                    uppercase(gameText.translate("ui.expulsar_jugador")),
                    ButtonTone.DANGER,
                    this::kickSelectedParticipant,
                    !lobbyCommandPending && kickEnabled);
            themedButton(70f, 215f, 360f, 76f,
                    uppercase(gameText.translate("ui.a_jugar")),
                    ButtonTone.POSITIVE,
                    () -> lobbyConfirmation = LobbyConfirmation.START,
                    !lobbyCommandPending && state.participants().size() >= 2
                            && !state.startingOrStarted());
        } else {
            textFit(uiFont, lobbyPhaseText(state), 250f, 306f,
                    MUTED, true, 340f);
        }

        panel(495f, 180f, 900f, 650f,
                uppercase(gameText.translate("chat.chat_de_la_timba")));
        if (lobbyImageMode) {
            drawLobbyImageGallery(525f, 315f, 840f, 430f);
        } else if (!lobbyEmojiPickerOpen) {
            drawLobbyMessages(state.chat(), 525f, 744f, 840f);
        }
        drawLobbyChatInput(525f, 215f, 440f);
        button(965f, 215f, 85f, 70f, "EMOJI", false,
                () -> {
                    lobbyImageMode = false;
                    lobbyEmojiPickerOpen = !lobbyEmojiPickerOpen;
                    activateField("lobbyChat");
                });
        button(1060f, 215f, 85f, 70f, "IMAGEN", false,
                () -> {
                    lobbyEmojiPickerOpen = false;
                    lobbyImageMode = !lobbyImageMode;
                    activateField(lobbyImageMode ? "lobbyImage" : "lobbyChat");
                    if (lobbyImageMode) refreshLobbyHistoryMedia();
                });
        button(1155f, 215f, 85f, 70f,
                lobbyVoiceLive || lobbyVoiceOpening ? "PARAR" : "VOZ", false,
                this::toggleLobbyVoiceRecording, canUseLobbyVoice()
                        && !lobbyCommandPending && !lobbyVoiceStopping);
        button(1250f, 215f, 115f, 70f, "ENVIAR", true,
                this::sendLobbyComposer,
                !lobbyCommandPending && !(lobbyImageMode
                        ? lobbyImageDraft : lobbyChatDraft).isBlank());
        if (lobbyEmojiPickerOpen) {
            drawLobbyEmojiPicker(525f, 315f, 840f, 390f);
        } else if (!lobbyVoiceStatus.isEmpty()) {
            drawLobbyVoiceStatus(535f, 312f, 820f);
        }

        panel(1425f, 180f, 460f, 650f,
                uppercase(gameText.translate("ui.participantes_conectados")));
        text(smallFont, state.participants().size() + "/"
                + LobbySnapshot.MAX_PARTICIPANTS, 1845f, 794f, CYAN, true);
        float participantY = 705f;
        for (LobbyParticipant participant : state.participants()) {
            drawLobbyParticipant(participant, 1450f, participantY, 410f, 54f);
            participantY -= 57f;
        }

        button(35f, 55f, 220f, 70f,
                uppercase(gameText.translate("ui.salir")), false,
                () -> lobbyConfirmation = LobbyConfirmation.LEAVE,
                !lobbyCommandPending);
        button(1640f, 55f, 245f, 70f,
                uppercase(gameText.translate("menu.ajustes")), false,
                this::openSettings);
        drawSoundControl(1430f, 62f, 175f, 58f, true);

    }

    private void drawLobbyParticipant(LobbyParticipant participant, float x,
            float y, float w, float h) {
        boolean selected = participant.nickname().equals(selectedParticipant);
        Color border = selected ? GOLD : participant.secure() ? LINE : ORANGE;
        outerBox(x, y, w, h, border,
                selected ? new Color(0x20324cee) : PANEL_LIGHT);
        lobbyAvatars.add(new LobbyAvatarItem(lobbyAvatar(participant),
                x + 8f, y + 6f, 42f));
        shapes.setColor(lobbyLatencyColor(participant));
        shapes.circle(x + 55f, y + h / 2f, 4f, 20);
        textFit(smallFont, participant.nickname(), x + 68f, y + 35f,
                participant.asyncWaiting() ? DISABLED : Color.WHITE,
                false, w - 190f);
        String role = participant.local() ? "TU"
                : participant.host() ? "HOST" : participant.bot() ? "BOT" : "";
        if (!role.isEmpty()) {
            textFit(tinyFont, role, x + w - 90f, y + 35f,
                    participant.local() ? GOLD : CYAN, true, 70f);
        }
        if (participant.latencyAvailable()) {
            text(tinyFont, (participant.latency() >= 0
                    ? participant.latency() : "-") + " ms",
                    x + w - 28f, y + 17f, MUTED, true);
        }
        hit(x, y, w, h, () -> selectedParticipant = participant.nickname());
    }

    private Texture lobbyAvatar(LobbyParticipant participant) {
        if (participant.avatar() == null) {
            return participant.bot() ? avatarBot : avatarDefault;
        }
        String key = participant.avatar().toString();
        Texture cached = lobbyAvatarTextures.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            Texture loaded = new Texture(Gdx.files.absolute(key));
            loaded.setFilter(TextureFilter.Linear, TextureFilter.Linear);
            lobbyAvatarTextures.put(key, loaded);
            return loaded;
        } catch (RuntimeException failure) {
            return participant.bot() ? avatarBot : avatarDefault;
        }
    }

    private static Color lobbyLatencyColor(LobbyParticipant participant) {
        if (!participant.connected()) return LATENCY_RED;
        if (!participant.latencyAvailable()) return LATENCY_STALE;
        int first = participant.latency();
        int second = participant.previousLatency();
        int best = first < 0 ? second : second < 0 ? first
                : Math.min(first, second);
        if (best < 0) return LATENCY_RED;
        if (best <= 100) return LATENCY_GREEN;
        if (best <= 250) return LATENCY_YELLOW;
        if (best <= 400) return LATENCY_ORANGE;
        return LATENCY_RED;
    }

    private static Texture filteredTexture(String asset) {
        Texture texture = new Texture(Gdx.files.internal(asset));
        texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        return texture;
    }

    private void drawLobbyMessages(List<LobbyChatMessage> messages, float x,
            float top, float width) {
        if (messages.size() > lobbyChatMessageCount && lobbyChatScroll > 0) {
            lobbyChatScroll += messages.size() - lobbyChatMessageCount;
        }
        lobbyChatMessageCount = messages.size();
        int maximumScroll = Math.max(0, messages.size() - 1);
        lobbyChatScroll = MathUtils.clamp(lobbyChatScroll, 0, maximumScroll);
        int end = Math.max(0, messages.size() - lobbyChatScroll);
        int first = lobbyMessageStartIndex(messages, 420f, end);
        float cursorTop = top;
        for (int i = first; i < end; i++) {
            LobbyChatMessage message = messages.get(i);
            float messageHeight = lobbyMessageHeight(message.type());
            float y = cursorTop - messageHeight;
            boolean local = message.nickname().equals(lobby.localNickname());
            if (message.type() == LobbyChatMessage.Type.PLAYER_JOINED
                    || message.type() == LobbyChatMessage.Type.PLAYER_LEFT) {
                drawLobbyPresenceMessage(message, x, y, width, messageHeight);
                cursorTop = y - 10f;
                continue;
            }
            String header = message.nickname() + "  ·  "
                    + CHAT_TIME.format(message.timestamp());
            float desiredWidth = lobbyDesiredBubbleWidth(message, header);
            float bubbleW = lobbyMessageWidth(message.type(), desiredWidth,
                    width - 48f);
            float bubbleX = local ? x + width - bubbleW : x + 48f;
            Color border = local ? CYAN_DARK : LINE;
            Color fill = local ? new Color(0x0d2638e8)
                    : new Color(0x09131fe8);
            outerBox(bubbleX, y, bubbleW, messageHeight, border, fill);
            float headerX = bubbleX + 14f;
            float bodyX = bubbleX + 14f;
            float bodyWidth = bubbleW - 28f;
            if (local) {
                lobbyAvatars.add(new LobbyAvatarItem(
                        lobbyMessageAvatar(message), bubbleX + 10f,
                        y + messageHeight - 42f, 30f));
                headerX += 38f;
            } else {
                lobbyAvatars.add(new LobbyAvatarItem(
                        lobbyMessageAvatar(message), x + 4f,
                        y + messageHeight - 39f, 34f));
            }
            textFit(tinyFont, header, headerX,
                    y + messageHeight - 12f,
                    local ? CYAN : GOLD, false,
                    bubbleX + bubbleW - 14f - headerX);
            if (message.type() == LobbyChatMessage.Type.TEXT) {
                drawLobbyEmojiText(message.content(), bodyX,
                        y + 22f, bodyWidth, Color.WHITE);
            } else if (message.type() == LobbyChatMessage.Type.IMAGE) {
                drawLobbyImageMessage(message, bodyX,
                        y + 12f, bodyWidth, messageHeight - 55f,
                        Color.WHITE);
            } else if (message.type() == LobbyChatMessage.Type.VOICE) {
                textFit(smallFont, "▶  NOTA DE VOZ", bodyX,
                        y + 23f, Color.WHITE, false, bodyWidth);
                hit(bubbleX, y, bubbleW, messageHeight,
                        () -> playLobbyVoice(message));
            }
            cursorTop = y - 10f;
        }
        drawLobbyChatScrollbar(x + width - 5f, top - 420f, 420f,
                messages.size(), first, end, maximumScroll);
    }

    private void drawLobbyChatScrollbar(float x, float y, float height,
            int total, int first, int end, int maximumScroll) {
        if (maximumScroll <= 0 || total <= 0) return;
        int visible = Math.max(1, end - first);
        float thumbHeight = Math.min(height,
                Math.max(34f, height * visible / total));
        float progress = lobbyChatScroll / (float) maximumScroll;
        float thumbY = y + progress * (height - thumbHeight);
        shapes.setColor(new Color(0x1a2c44cc));
        roundedRect(x, y, 5f, height, 2.5f);
        shapes.setColor(CYAN);
        roundedRect(x, thumbY, 5f, thumbHeight, 2.5f);
    }

    static float lobbyMessageHeight(LobbyChatMessage.Type type) {
        return switch (type) {
            case IMAGE -> 280f;
            case PLAYER_JOINED, PLAYER_LEFT -> 44f;
            default -> 70f;
        };
    }

    static float lobbyMessageWidth(LobbyChatMessage.Type type,
            float desiredWidth, float availableWidth) {
        float maximum = Math.max(0f, availableWidth);
        float minimum = Math.min(250f, maximum);
        float preferred = switch (type) {
            case IMAGE -> 430f;
            case VOICE -> 390f;
            default -> desiredWidth;
        };
        return Math.min(maximum, Math.max(minimum, preferred));
    }

    private float lobbyDesiredBubbleWidth(LobbyChatMessage message,
            String header) {
        float headerWidth = textWidth(tinyFont, header) + 42f;
        if (message.nickname().equals(lobby.localNickname())) {
            headerWidth += 38f;
        }
        float contentWidth = message.type() == LobbyChatMessage.Type.TEXT
                ? composerWidth(message.content()) + 34f : 0f;
        return Math.max(headerWidth, contentWidth);
    }

    private void drawLobbyPresenceMessage(LobbyChatMessage message, float x,
            float y, float width, float height) {
        String action = message.type() == LobbyChatMessage.Type.PLAYER_JOINED
                ? " SE UNE A LA TIMBA" : " ABANDONA LA TIMBA";
        String time = " (" + CHAT_TIME.format(message.timestamp()) + ")";
        float nickWidth = textWidth(tinyFont, message.nickname());
        float actionWidth = textWidth(tinyFont, action);
        float timeWidth = textWidth(tinyFont, time);
        float totalWidth = 34f + 8f + nickWidth + actionWidth + timeWidth;
        float startX = x + Math.max(0f, (width - totalWidth) / 2f);
        lobbyAvatars.add(new LobbyAvatarItem(lobbyMessageAvatar(message),
                startX, y + (height - 30f) / 2f, 30f));
        float textX = startX + 42f;
        text(tinyFont, message.nickname(), textX, y + 28f,
                Color.WHITE, false);
        textX += nickWidth;
        text(tinyFont, action, textX, y + 28f,
                message.type() == LobbyChatMessage.Type.PLAYER_JOINED
                        ? new Color(0x58d67aff) : new Color(0xf07b72ff),
                false);
        textX += actionWidth;
        text(tinyFont, time, textX, y + 28f, MUTED, false);
    }

    static int lobbyMessageStartIndex(List<LobbyChatMessage> messages,
            float availableHeight) {
        return lobbyMessageStartIndex(messages, availableHeight,
                messages.size());
    }

    static int lobbyMessageStartIndex(List<LobbyChatMessage> messages,
            float availableHeight, int endExclusive) {
        float used = 0f;
        int first = MathUtils.clamp(endExclusive, 0, messages.size());
        int end = first;
        while (first > 0) {
            float height = lobbyMessageHeight(messages.get(first - 1).type());
            float gap = first == end ? 0f : 10f;
            if (used + gap + height > availableHeight) break;
            used += gap + height;
            first--;
        }
        return first;
    }

    private Texture lobbyMessageAvatar(LobbyChatMessage message) {
        if (lobby != null) {
            for (LobbyParticipant participant : lobby.participants()) {
                if (participant.nickname().equals(message.nickname())) {
                    return lobbyAvatar(participant);
                }
            }
        }
        return message.nickname().contains("$") ? avatarBot : avatarDefault;
    }

    private void drawLobbyImageMessage(LobbyChatMessage message, float x,
            float y, float width, float height, Color color) {
        LobbyMedia media = lobbyMedia.get(message.sequence());
        Texture thumbnail = media == null ? null
                : media.gif != null ? media.gif.frameAt(elapsed, true) : media.image;
        if (thumbnail != null) {
            float scale = Math.min(width / Math.max(1f, thumbnail.getWidth()),
                    height / Math.max(1f, thumbnail.getHeight()));
            float drawnWidth = thumbnail.getWidth() * scale;
            float drawnHeight = thumbnail.getHeight() * scale;
            uiImages.add(new UiImageItem(thumbnail,
                    x + (width - drawnWidth) / 2f,
                    y + (height - drawnHeight) / 2f,
                    drawnWidth, drawnHeight));
        } else {
            String state = media != null && media.failed
                    ? "[Imagen no disponible]" : "[Cargando imagen...]";
            textFit(tinyFont, state, x + width / 2f,
                    y + height / 2f + 6f, color, true, width);
        }
    }

    private void drawLobbyEmojiText(String content, float x,
            float y, float width, Color color) {
        float cursor = x;
        Matcher matcher = EMOJI_TOKEN.matcher(content);
        int start = 0;
        while (matcher.find() && cursor < x + width - 24f) {
            String plain = content.substring(start, matcher.start());
            String visible = ellipsize(tinyFont, plain,
                    Math.max(0f, x + width - cursor));
            if (!visible.isEmpty()) {
                text(tinyFont, visible, cursor, y, color, false);
                glyph.setText(tinyFont, visible);
                cursor += glyph.width;
            }
            int number = Integer.parseInt(matcher.group(1));
            if (number >= 1 && number <= EMOJI_COUNT
                    && cursor + 27f <= x + width) {
                uiImages.add(new UiImageItem(lobbyEmojiTexture(number),
                        cursor, y - 21f, 27f, 27f));
                cursor += 31f;
            }
            start = matcher.end();
        }
        if (start < content.length() && cursor < x + width) {
            textFit(tinyFont, content.substring(start), cursor, y,
                    color, false, x + width - cursor);
        }
    }

    private static String lobbyMessageText(LobbyChatMessage message) {
        return switch (message.type()) {
            case PLAYER_JOINED -> message.nickname() + " se une a la timba";
            case PLAYER_LEFT -> message.nickname() + " abandona la timba";
            case VOICE -> message.nickname() + ": [Nota de voz]";
            case IMAGE -> message.nickname() + ": " + message.content();
            case TEXT -> message.nickname() + ": " + message.content();
        };
    }

    private void drawLobbyChatInput(float x, float y, float w) {
        String fieldId = lobbyImageMode ? "lobbyImage" : "lobbyChat";
        String draft = lobbyImageMode ? lobbyImageDraft : lobbyChatDraft;
        boolean focused = fieldId.equals(activeField);
        outerBox(x, y, w, 70f,
                focused || hovered(x, y, w, 70f) ? CYAN : LINE,
                pressed(x, y, w, 70f) ? new Color(0x0b1424ff) : PANEL_LIGHT);
        String placeholder = lobbyImageMode ? "Pega una URL de imagen o GIF"
                : "Escribe un mensaje";
        if (!lobbyImageMode && !draft.isEmpty()) {
            drawLobbyComposerValue(draft, x + 22f, y, w - 44f,
                    focused);
        } else {
            String raw = draft.isEmpty() ? placeholder : draft;
            FrontendInputWindow window = draft.isEmpty()
                    ? new FrontendInputWindow(raw, 0, 0, 0f, 0f, 0f)
                    : frontendInputWindow(uiFont, draft,
                            draft, w - 44f, focused);
            drawInputSelection(x + 22f, y + 17f, 36f, window, focused);
            text(uiFont, window.text(), x + 22f, y + 44f,
                    draft.isEmpty() ? DISABLED : Color.WHITE, false);
            drawInputCaret(x + 22f + window.caretOffset(), y + 17f, 36f,
                    focused);
        }
        textFieldHits.add(new TextFieldHit(fieldId,
                new Rectangle(x, y, w, 70f)));
        hit(x, y, w, 70f, () -> activateField(fieldId));
    }

    /**
     * Keeps the legacy #id# wire format while presenting inline images just as
     * Swing's EmojiChatBox does. The left edge scrolls away when necessary so
     * the insertion caret and the most recent text stay visible.
     */
    private void drawLobbyComposerValue(String raw, float x, float y,
            float maxWidth, boolean focused) {
        textEdit.focus("lobbyChat", raw);
        ComposerWindow window = lobbyComposerWindow(raw, maxWidth);
        List<ComposerRun> runs = lobbyComposerRuns(window.raw());
        if (window.selectionWidth() > 0f) {
            shapes.setColor(new Color(CYAN.r, CYAN.g, CYAN.b, 0.34f));
            shapes.rect(x + window.selectionOffset(), y + 17f,
                    window.selectionWidth(), 36f);
        }
        float cursor = x;
        for (ComposerRun run : runs) {
            if (run.emojiId() > 0) {
                uiImages.add(new UiImageItem(lobbyEmojiTexture(run.emojiId()),
                        cursor, y + 19f, COMPOSER_EMOJI_SIZE,
                        COMPOSER_EMOJI_SIZE));
            } else if (!run.text().isEmpty()) {
                text(uiFont, run.text(), cursor, y + 44f, Color.WHITE, false);
            }
            cursor += run.width();
        }
        drawInputCaret(x + window.caretOffset(), y + 17f, 36f, focused);
    }

    private ComposerWindow lobbyComposerWindow(String raw, float maxWidth) {
        int caret = textEdit.caret(raw);
        int start = caret;
        for (int scanned = 0; start > 0 && scanned < 512; scanned++) {
            int previous = previousComposerBoundary(raw, start);
            if (composerWidth(raw.substring(previous, caret)) > maxWidth) break;
            start = previous;
        }
        int end = caret;
        for (int scanned = 0; end < raw.length() && scanned < 512; scanned++) {
            int next = nextComposerBoundary(raw, end);
            if (composerWidth(raw.substring(start, next)) > maxWidth) break;
            end = next;
        }
        int selectionStart = Math.max(start,
                Math.min(end, textEdit.selectionStart(raw)));
        int selectionEnd = Math.max(start,
                Math.min(end, textEdit.selectionEnd(raw)));
        return new ComposerWindow(raw.substring(start, end), start, end,
                composerWidth(raw.substring(start, caret)),
                composerWidth(raw.substring(start, selectionStart)),
                composerWidth(raw.substring(selectionStart, selectionEnd)));
    }

    private float composerWidth(String raw) {
        float result = 0f;
        for (ComposerRun run : lobbyComposerRuns(raw)) result += run.width();
        return result;
    }

    private int previousComposerBoundary(String raw, int index) {
        Matcher matcher = EMOJI_TOKEN.matcher(raw);
        while (matcher.find()) {
            if (index == matcher.end() || (index > matcher.start()
                    && index < matcher.end())) return matcher.start();
        }
        return raw.offsetByCodePoints(index, -1);
    }

    private int nextComposerBoundary(String raw, int index) {
        Matcher matcher = EMOJI_TOKEN.matcher(raw);
        while (matcher.find()) {
            if (index == matcher.start() || (index > matcher.start()
                    && index < matcher.end())) return matcher.end();
        }
        return raw.offsetByCodePoints(index, 1);
    }

    private List<ComposerRun> lobbyComposerRuns(String raw) {
        List<ComposerRun> runs = new ArrayList<>();
        Matcher matcher = EMOJI_TOKEN.matcher(raw);
        int start = 0;
        while (matcher.find()) {
            addComposerTextRun(runs, raw.substring(start, matcher.start()));
            int emojiId = Integer.parseInt(matcher.group(1));
            if (emojiId >= 1 && emojiId <= EMOJI_COUNT) {
                runs.add(new ComposerRun("", emojiId,
                        COMPOSER_EMOJI_ADVANCE));
            } else {
                addComposerTextRun(runs, matcher.group());
            }
            start = matcher.end();
        }
        addComposerTextRun(runs, raw.substring(start));
        return runs;
    }

    private void addComposerTextRun(List<ComposerRun> runs, String value) {
        if (!value.isEmpty()) {
            runs.add(new ComposerRun(value, 0, textWidth(uiFont, value)));
        }
    }

    private void drawLobbyGameInfo(LobbySnapshot state, float x, float y) {
        NewGameTableDraft.Settings settings = state.tableSettings();
        if (settings == null) {
            textFit(smallFont, "Recibiendo información del servidor…",
                    x, y, MUTED, false, 390f);
            return;
        }
        NewGameTableDraft.BlindLevel blind = settings.blindLevels()
                .get(settings.blindLevelIndex());
        lobbyInfoRow(x, y, "Compra:", settings.fixedBuyin()
                ? Integer.toString(settings.buyin()) : "Variable");
        lobbyInfoRow(x, y - 70f, "Ciegas:", money(blind.smallBlind())
                + " / " + money(blind.bigBlind()));
        lobbyInfoRow(x, y - 140f, "Manos:", settings.handLimit()
                ? Integer.toString(settings.handLimitCount()) : "—");
        if (state.recovering()) {
            textFit(tinyFont, "CONTINUANDO TIMBA ANTERIOR", x, y - 205f,
                    ORANGE, false, 390f);
        }
    }

    private void lobbyInfoRow(float x, float y, String label, String value) {
        shapes.setColor(new Color(0x31445f77));
        shapes.rect(x, y - 18f, 390f, 1f);
        text(smallFont, label, x, y + 18f, MUTED, false);
        textFit(smallFont, value, x + 295f, y + 18f,
                Color.WHITE, true, 190f);
    }

    private static String money(double amount) {
        return String.format(Locale.ROOT, "%.2f", amount);
    }

    private static String lobbyPhaseText(LobbySnapshot state) {
        return switch (state.phase()) {
            case CONNECTING -> "Conectando…";
            case KEY_EXCHANGE -> "Intercambio de claves…";
            case RECEIVING_SERVER_INFO -> "Recibiendo información del servidor…";
            case CONNECTED -> "Conectado";
            case WAITING_FOR_PLAYERS -> "Esperando jugadores…";
            case INITIALIZING_GAME -> "Inicializando timba…";
            case RECONNECTING -> "Reconectando…";
            case IN_GAME -> "Timba en curso";
            case ERROR -> state.statusDetail().isBlank() ? "Error" : state.statusDetail();
            case CLOSED -> "Sala cerrada";
        };
    }

    private LobbyParticipant selectedRemoteParticipant(LobbySnapshot state) {
        if (selectedParticipant == null) {
            return null;
        }
        return state.participants().stream()
                .filter(participant -> participant.nickname().equals(selectedParticipant))
                .filter(participant -> !participant.local() && !participant.host())
                .findFirst().orElse(null);
    }

    private void drawLobbyConfirmation() {
        hits.clear();
        shapes.setColor(new Color(0x02050cbb));
        shapes.rect(0f, 0f, WIDTH, HEIGHT);
        String prompt = lobbyConfirmation == LobbyConfirmation.START
                ? "¿SEGURO QUE QUIERES EMPEZAR YA?"
                : "¿SEGURO QUE QUIERES SALIR AHORA?";
        // A confirmation is a true modal surface: underlying chat content must
        // never bleed through and compete with the decision text.
        shapes.setColor(new Color(0x00000099));
        roundedRect(570f, 340f, 800f, 330f, 18f);
        shapes.setColor(CYAN_DARK);
        roundedRect(558f, 348f, 804f, 334f, 18f);
        shapes.setColor(new Color(0x071321ff));
        roundedRect(560f, 350f, 800f, 330f, 16f);
        shapes.setColor(new Color(0x36d9ffb8));
        shapes.rect(586f, 665f, 748f, 3f);
        textFit(headingFont, prompt, 960f, 560f, Color.WHITE, true, 700f);
        button(635f, 405f, 300f, 75f, "CANCELAR", false,
                () -> lobbyConfirmation = null);
        themedButton(985f, 405f, 300f, 75f,
                lobbyConfirmation == LobbyConfirmation.START ? "¡A JUGAR!" : "SALIR",
                lobbyConfirmation == LobbyConfirmation.START
                        ? ButtonTone.POSITIVE : ButtonTone.DANGER,
                this::confirmLobbyAction, true);
    }

    private void drawSettingsDiscardConfirmation() {
        hits.clear();
        textFieldHits.clear();
        editMenuHits.clear();
        shapes.setColor(new Color(0x02050cdd));
        shapes.rect(0f, 0f, WIDTH, HEIGHT);
        shapes.setColor(new Color(0x000000aa));
        roundedRect(570f, 340f, 800f, 330f, 18f);
        shapes.setColor(CYAN_DARK);
        roundedRect(558f, 348f, 804f, 334f, 18f);
        shapes.setColor(new Color(0x071321ff));
        roundedRect(560f, 350f, 800f, 330f, 16f);
        shapes.setColor(new Color(0x36d9ffb8));
        shapes.rect(586f, 665f, 748f, 3f);
        textFit(headingFont, "CAMBIOS SIN GUARDAR", 960f, 578f,
                GOLD, true, 700f);
        textFit(actionFont,
                "¿DESCARTAR LOS CAMBIOS REALIZADOS EN AJUSTES?",
                960f, 526f, Color.WHITE, true, 700f);
        themedButton(635f, 405f, 300f, 75f, "SEGUIR EDITANDO",
                ButtonTone.NEUTRAL,
                () -> settingsDiscardConfirmation = false, true);
        themedButton(985f, 405f, 300f, 75f, "DESCARTAR",
                ButtonTone.DANGER, () -> finishClosingSettings(false), true);
    }

    private void confirmLobbyAction() {
        LobbyConfirmation action = lobbyConfirmation;
        lobbyConfirmation = null;
        if (action == LobbyConfirmation.START) {
            lobbyGameStarting = true;
            submitLobbyCommand(new LobbyCommand.StartGame(), null);
        } else {
            submitLobbyCommand(new LobbyCommand.Leave(), this::returnFromLobby);
        }
    }

    private void drawLobbyLoadingOverlay() {
        hits.clear();
        shapes.setColor(new Color(0x02050cdd));
        shapes.rect(0f, 0f, WIDTH, HEIGHT);
        shapes.setColor(new Color(0x00000099));
        roundedRect(566f, 376f, 808f, 270f, 18f);
        shapes.setColor(CYAN_DARK);
        roundedRect(558f, 384f, 804f, 274f, 18f);
        shapes.setColor(new Color(0x071321ff));
        roundedRect(560f, 386f, 800f, 270f, 16f);
        shapes.setColor(new Color(0x36d9ffb8));
        shapes.rect(586f, 641f, 748f, 3f);

        textFit(headingFont, "INICIALIZANDO TIMBA...",
                960f, 560f, GOLD, true, 680f);
        textFit(smallFont, "PREPARANDO LA MESA",
                960f, 508f, MUTED, true, 620f);

        float trackX = 660f;
        float trackY = 438f;
        float trackW = 600f;
        float trackH = 18f;
        shapes.setColor(new Color(0x020813ff));
        roundedRect(trackX, trackY, trackW, trackH, 9f);
        shapes.setColor(LINE);
        roundedRect(trackX + 2f, trackY + 2f, trackW - 4f,
                trackH - 4f, 7f);
        // Indeterminate motion: it represents real initialization without
        // claiming a percentage that the core cannot measure.
        float segmentW = 170f;
        float travel = trackW - segmentW - 8f;
        float phase = (elapsed * 0.52f) % 2f;
        float eased = phase <= 1f ? phase : 2f - phase;
        float segmentX = trackX + 4f + travel * eased;
        shapes.setColor(CYAN);
        roundedRect(segmentX, trackY + 4f, segmentW,
                trackH - 8f, 5f);
    }

    private void kickSelectedParticipant() {
        LobbyParticipant target = selectedRemoteParticipant(lobby);
        if (target == null) {
            showToast("Tienes que seleccionar algún participante");
            return;
        }
        submitLobbyCommand(new LobbyCommand.Kick(target.nickname()),
                () -> selectedParticipant = null);
    }

    private void sendLobbyComposer() {
        if (lobbyImageMode) {
            sendLobbyImage(lobbyImageDraft);
            return;
        }
        String message = lobbyChatDraft.trim();
        if (!message.isEmpty()) {
            submitLobbyCommand(new LobbyCommand.SendText(message), () -> {
                lobbyChatDraft = "";
            });
        }
    }

    private void sendLobbyImage(String rawUrl) {
        String url = rawUrl == null ? "" : rawUrl.trim();
        if (!GdxChatImageHistory.isHttpUrl(url)) {
            showToast("Introduce una URL HTTP o HTTPS de imagen o GIF");
            return;
        }
        if (elapsed < lobbyImageSendAllowedAt) {
            showToast("Espera un momento antes de enviar otra imagen");
            return;
        }
        submitLobbyCommand(new LobbyCommand.SendImage(url), () -> {
            lobbyImageDraft = "";
            lobbyImageSendAllowedAt = elapsed + IMAGE_SEND_COOLDOWN_SECONDS;
            lobbyImageHistory = GdxChatImageHistory.remember(
                    initialProperties, url, true);
            if (preferences != null) preferences.saveDeferred();
            refreshLobbyHistoryMedia();
        });
    }

    private void drawLobbyImageGallery(float x, float y, float w, float h) {
        outerBox(x, y, w, h, CYAN_DARK, new Color(0x061321f5));
        textFit(smallFont, "GALERÍA DE IMÁGENES Y GIF", x + 24f,
                y + h - 28f, GOLD, false, w - 410f);
        textFit(tinyFont, "Selecciona una miniatura para enviarla",
                x + 24f, y + h - 58f, MUTED, false, w - 410f);
        boolean autoReceive = GdxChatImageHistory.autoReceive(
                initialProperties);
        themedButton(x + w - 382f, y + h - 68f, 220f, 48f,
                autoReceive ? "RECIBIDAS: SÍ" : "RECIBIDAS: NO",
                autoReceive ? ButtonTone.POSITIVE : ButtonTone.NEUTRAL,
                () -> {
                    GdxChatImageHistory.setAutoReceive(initialProperties,
                            !autoReceive);
                    if (preferences != null) preferences.saveDeferred();
                }, true);
        themedButton(x + w - 152f, y + h - 68f, 128f, 48f,
                "VACIAR", ButtonTone.NEUTRAL, this::clearLobbyImageHistory,
                !lobbyImageHistory.isEmpty());

        if (lobbyImageHistory.isEmpty()) {
            textFit(headingFont, "TU GALERÍA ESTÁ VACÍA", x + w / 2f,
                    y + 225f, MUTED, true, w - 80f);
            textFit(smallFont,
                    "Pega una URL abajo. Las imágenes usadas aparecerán aquí.",
                    x + w / 2f, y + 175f, DISABLED, true, w - 100f);
            return;
        }

        int visible = Math.min(8, lobbyImageHistory.size());
        float cellW = 190f;
        float cellH = 132f;
        float gapX = (w - 48f - cellW * 4f) / 3f;
        float startX = x + 24f;
        float startY = y + h - 218f;
        for (int index = 0; index < visible; index++) {
            String url = lobbyImageHistory.get(index);
            int column = index % 4;
            int row = index / 4;
            float cellX = startX + column * (cellW + gapX);
            float cellY = startY - row * (cellH + 12f);
            boolean over = hovered(cellX, cellY, cellW, cellH);
            outerBox(cellX, cellY, cellW, cellH,
                    over ? CYAN : LINE, new Color(0x030911ff));
            LobbyMedia media = lobbyHistoryMedia.get(url);
            Texture thumbnail = media == null ? null
                    : media.gif != null ? media.gif.frameAt(elapsed, true)
                            : media.image;
            if (thumbnail != null) {
                float availableW = cellW - 14f;
                float availableH = cellH - 14f;
                float scale = Math.min(availableW / thumbnail.getWidth(),
                        availableH / thumbnail.getHeight());
                float imageW = Math.max(1f, thumbnail.getWidth() * scale);
                float imageH = Math.max(1f, thumbnail.getHeight() * scale);
                uiImages.add(new UiImageItem(thumbnail,
                        cellX + (cellW - imageW) / 2f,
                        cellY + (cellH - imageH) / 2f, imageW, imageH));
            } else {
                String state = media != null && media.failed
                        ? "NO DISPONIBLE" : "CARGANDO...";
                textFit(tinyFont, state, cellX + cellW / 2f,
                        cellY + cellH / 2f + 7f,
                        media != null && media.failed ? ORANGE : MUTED,
                        true, cellW - 24f);
            }
            hit(cellX, cellY, cellW, cellH, () -> sendLobbyImage(url));
        }
    }

    private void clearLobbyImageHistory() {
        GdxChatImageHistory.clear(initialProperties);
        if (preferences != null) preferences.saveDeferred();
        lobbyImageHistory = List.of();
        clearLobbyHistoryMedia();
    }

    private void drawLobbyEmojiPicker(float x, float y, float w, float h) {
        outerBox(x, y, w, h, CYAN_DARK, new Color(0x061321f5));
        int first = lobbyEmojiPage * EMOJI_PAGE_SIZE + 1;
        int last = Math.min(EMOJI_COUNT, first + EMOJI_PAGE_SIZE - 1);
        int pageCount = (EMOJI_COUNT + EMOJI_PAGE_SIZE - 1) / EMOJI_PAGE_SIZE;
        textFit(smallFont, "EMOJIS " + first + " - " + last,
                x + 28f, y + h - 30f, GOLD, false, 350f);
        textFit(tinyFont, "PAGINA " + (lobbyEmojiPage + 1) + " / " + pageCount,
                x + w - 330f, y + h - 30f, MUTED, false, 300f);
        float cellW = 82f;
        float cellH = 62f;
        float startX = x + 78f;
        float startY = y + h - 112f;
        for (int slot = 0; slot < EMOJI_PAGE_SIZE; slot++) {
            int number = first + slot;
            if (number > EMOJI_COUNT) break;
            int column = slot % EMOJI_COLUMNS;
            int row = slot / EMOJI_COLUMNS;
            float cellX = startX + column * cellW;
            float cellY = startY - row * cellH;
            outerBox(cellX, cellY, 54f, 54f,
                    hovered(cellX, cellY, 54f, 54f) ? CYAN : LINE,
                    new Color(0x0d1b2dcc));
            uiImages.add(new UiImageItem(lobbyEmojiTexture(number),
                    cellX + 7f, cellY + 7f, 40f, 40f));
            hit(cellX, cellY, 54f, 54f, () -> {
                activateField("lobbyChat");
                replaceActiveSelection(" #" + number + "# ");
            });
        }
        button(x + 30f, y + 18f, 155f, 52f, "ANTERIOR", false,
                () -> lobbyEmojiPage = Math.max(0, lobbyEmojiPage - 1),
                lobbyEmojiPage > 0);
        button(x + w - 185f, y + 18f, 155f, 52f, "SIGUIENTE", false,
                () -> lobbyEmojiPage = Math.min(pageCount - 1,
                        lobbyEmojiPage + 1), lobbyEmojiPage + 1 < pageCount);
        button(x + w / 2f - 80f, y + 18f, 160f, 52f, "CERRAR", false,
                () -> lobbyEmojiPickerOpen = false);
    }

    private Texture lobbyEmojiTexture(int number) {
        return emojiTextures.computeIfAbsent(number, value ->
                filteredTexture("images/emoji_chat/" + value + ".png"));
    }

    private void toggleLobbyVoiceRecording() {
        if (lobbyVoiceRecorder == null) {
            beginLobbyVoiceRecording();
        } else {
            finishLobbyVoiceRecording(false);
        }
    }

    private boolean canUseLobbyVoice() {
        return surface == Surface.LOBBY && lobbySession != null
                && preferenceBoolean("voice_messages", true)
                && preferenceBoolean("audio_mic_enabled", true)
                && !preferenceBoolean("audio_block_voice_messages", false);
    }

    private void beginLobbyVoiceRecording() {
        if (!canUseLobbyVoice() || lobbyVoiceRecorder != null
                || lobbyVoiceStopping) return;
        GdxVoiceRecorder recorder = new GdxVoiceRecorder(initialProperties);
        lobbyVoiceRecorder = recorder;
        lobbyVoiceOpening = true;
        lobbyVoiceLive = false;
        lobbyVoiceStatus = "ABRIENDO MICROFONO...";
        lobbyVoiceStatusAt = elapsed;
        CompletableFuture.supplyAsync(() -> recorder.start(
                () -> Gdx.app.postRunnable(() -> {
                    if (disposed || lobbyVoiceRecorder != recorder) return;
                    lobbyVoiceOpening = false;
                    lobbyVoiceLive = true;
                    lobbyVoiceLiveAt = elapsed;
                    lobbyVoiceStatus = "GRABANDO - PULSA PARAR PARA ENVIAR";
                    lobbyVoiceStatusAt = elapsed;
                }), () -> Gdx.app.postRunnable(() -> {
                    if (!disposed && lobbyVoiceRecorder == recorder) {
                        finishLobbyVoiceRecording(false);
                    }
                }))).whenComplete((outcome, failure) ->
                        Gdx.app.postRunnable(() -> {
                            if (disposed || lobbyVoiceRecorder != recorder) return;
                            if (failure != null
                                    || outcome != GdxVoiceRecorder.Outcome.RECORDING) {
                                lobbyVoiceOpening = false;
                                lobbyVoiceLive = false;
                                lobbyVoiceRecorder = null;
                                lobbyVoiceStatus = "MICROFONO NO DISPONIBLE";
                                lobbyVoiceStatusAt = elapsed;
                            }
                        }));
    }

    private void finishLobbyVoiceRecording(boolean discard) {
        GdxVoiceRecorder recorder = lobbyVoiceRecorder;
        if (recorder == null || lobbyVoiceStopping) return;
        lobbyVoiceStopping = true;
        lobbyVoiceOpening = false;
        lobbyVoiceLive = false;
        lobbyVoiceStatus = discard ? "NOTA CANCELADA" : "PROCESANDO NOTA...";
        lobbyVoiceStatusAt = elapsed;
        if (discard) recorder.abort();
        CompletableFuture.supplyAsync(() -> discard
                ? null : recorder.stopAndEncode()).whenComplete((wav, failure) ->
                        Gdx.app.postRunnable(() -> {
                            if (lobbyVoiceRecorder == recorder) {
                                lobbyVoiceRecorder = null;
                            }
                            lobbyVoiceStopping = false;
                            if (disposed || discard) return;
                            if (failure != null || wav == null) {
                                lobbyVoiceStatus = "NOTA DESCARTADA";
                                lobbyVoiceStatusAt = elapsed;
                                return;
                            }
                            lobbyVoiceStatus = "ENVIANDO NOTA...";
                            lobbyVoiceStatusAt = elapsed;
                            submitLobbyCommand(new LobbyCommand.SendVoice(wav), () -> {
                                lobbyVoiceStatus = "NOTA ENVIADA";
                                lobbyVoiceStatusAt = elapsed;
                            });
                        }));
    }

    private void cancelLobbyVoiceRecording() {
        GdxVoiceRecorder recorder = lobbyVoiceRecorder;
        if (recorder == null) return;
        recorder.abort();
        lobbyVoiceRecorder = null;
        lobbyVoiceOpening = false;
        lobbyVoiceLive = false;
        lobbyVoiceStopping = false;
        lobbyVoiceStatus = "";
    }

    private void updateLobbyVoiceRecording() {
        if (lobbyVoiceLive
                && elapsed - lobbyVoiceLiveAt >= VOICE_RECORD_MAX_SECONDS) {
            finishLobbyVoiceRecording(false);
        }
        if (!lobbyVoiceOpening && !lobbyVoiceLive && !lobbyVoiceStopping
                && !lobbyVoiceStatus.isEmpty()
                && elapsed - lobbyVoiceStatusAt > 2.8f) {
            lobbyVoiceStatus = "";
        }
    }

    private void drawLobbyVoiceStatus(float x, float y, float w) {
        outerBox(x, y, w, 74f, lobbyVoiceLive ? ORANGE : LINE,
                new Color(0x071321ee));
        uiImages.add(new UiImageItem(talkIcon, x + 18f, y + 13f, 48f, 48f));
        textFit(smallFont, lobbyVoiceStatus, x + 82f, y + 46f,
                lobbyVoiceLive ? GOLD : Color.WHITE, false, w - 170f);
        if (lobbyVoiceLive) {
            float remaining = Math.max(0f, VOICE_RECORD_MAX_SECONDS
                    - (elapsed - lobbyVoiceLiveAt));
            textFit(smallFont, String.format(Locale.ROOT, "%.1f s", remaining),
                    x + w - 32f, y + 46f, GOLD, true, 100f);
            shapes.setColor(ORANGE);
            roundedRect(x + 82f, y + 12f,
                    (w - 120f) * remaining / VOICE_RECORD_MAX_SECONDS, 5f, 2f);
        }
    }

    private void handleLobbyMedia(LobbySnapshot next) {
        Set<Long> retained = new HashSet<>();
        for (LobbyChatMessage message : next.chat()) {
            if (message.type() == LobbyChatMessage.Type.IMAGE) {
                retained.add(message.sequence());
            }
        }
        var mediaIterator = lobbyMedia.entrySet().iterator();
        while (mediaIterator.hasNext()) {
            Map.Entry<Long, LobbyMedia> entry = mediaIterator.next();
            if (!retained.contains(entry.getKey())) {
                entry.getValue().dispose();
                mediaIterator.remove();
            }
        }
        for (LobbyChatMessage message : next.chat()) {
            if (message.sequence() <= lastLobbyMediaSequence) continue;
            lastLobbyMediaSequence = Math.max(lastLobbyMediaSequence,
                    message.sequence());
            if (message.type() == LobbyChatMessage.Type.IMAGE) {
                if (!message.nickname().equals(next.localNickname())
                        && GdxChatImageHistory.autoReceive(initialProperties)) {
                    lobbyImageHistory = GdxChatImageHistory.remember(
                            initialProperties, message.content(), false);
                    if (preferences != null) preferences.saveDeferred();
                    refreshLobbyHistoryMedia();
                }
                loadLobbyImage(message);
                continue;
            }
            if (message.type() != LobbyChatMessage.Type.VOICE
                    || !audioControl.enabled()
                    || preferenceBoolean("audio_block_voice_messages", false)
                    || (message.nickname().equals(next.localNickname())
                    && !preferenceBoolean("audio_play_own_voice", true))) {
                continue;
            }
            try {
                byte[] wav = Base64.getDecoder().decode(message.content());
                if (com.tonikelope.coronapoker.core.audio.VoiceWavContract
                        .isValid(wav)) {
                    GdxVoicePlayback.play(wav);
                }
            } catch (IllegalArgumentException malformed) {
                // Invalid remote audio remains an inert history item.
            }
        }
    }

    private void loadLobbyImage(LobbyChatMessage message) {
        if (lobbyMedia.containsKey(message.sequence())) return;
        LobbyMedia media = new LobbyMedia();
        lobbyMedia.put(message.sequence(), media);
        CompletableFuture.supplyAsync(() ->
                GdxChatImageLoader.download(message.content()))
                .whenComplete((data, failure) -> Gdx.app.postRunnable(() -> {
                    if (disposed || lobbyMedia.get(message.sequence()) != media) {
                        return;
                    }
                    media.loading = false;
                    if (failure != null || data == null || data.length == 0) {
                        media.failed = true;
                        return;
                    }
                    try {
                        if (GdxChatImageLoader.isGif(data)) {
                            media.gif = GifTextureAnimation.load(data,
                                    "lobby-chat:" + message.sequence(), 240);
                        } else {
                            Pixmap pixmap = new Pixmap(data, 0, data.length);
                            try {
                                media.image = new Texture(pixmap, true);
                                media.image.setFilter(
                                        TextureFilter.MipMapLinearLinear,
                                        TextureFilter.Linear);
                            } finally {
                                pixmap.dispose();
                            }
                        }
                    } catch (RuntimeException | java.io.IOException invalid) {
                        media.failed = true;
                    }
                }));
    }

    private void playLobbyVoice(LobbyChatMessage message) {
        if (!audioControl.enabled()
                || preferenceBoolean("audio_block_voice_messages", false)) {
            showToast("La reproducción de notas de voz está desactivada");
            return;
        }
        try {
            byte[] wav = Base64.getDecoder().decode(message.content());
            if (!com.tonikelope.coronapoker.core.audio.VoiceWavContract
                    .isValid(wav)) {
                showToast("La nota de voz no es válida");
                return;
            }
            GdxVoicePlayback.play(wav);
        } catch (IllegalArgumentException malformed) {
            showToast("La nota de voz no se puede reproducir");
        }
    }

    private void refreshLobbyHistoryMedia() {
        Set<String> retained = new HashSet<>(lobbyImageHistory);
        var iterator = lobbyHistoryMedia.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, LobbyMedia> entry = iterator.next();
            if (!retained.contains(entry.getKey())) {
                entry.getValue().dispose();
                iterator.remove();
            }
        }
        for (int index = 0; index < Math.min(8,
                lobbyImageHistory.size()); index++) {
            loadLobbyHistoryImage(lobbyImageHistory.get(index));
        }
    }

    private void loadLobbyHistoryImage(String url) {
        if (lobbyHistoryMedia.containsKey(url)) return;
        LobbyMedia media = new LobbyMedia();
        lobbyHistoryMedia.put(url, media);
        CompletableFuture.supplyAsync(() -> GdxChatImageLoader.download(url))
                .whenComplete((data, failure) -> Gdx.app.postRunnable(() -> {
                    if (disposed || lobbyHistoryMedia.get(url) != media) return;
                    media.loading = false;
                    if (failure != null || data == null || data.length == 0) {
                        media.failed = true;
                        return;
                    }
                    try {
                        if (GdxChatImageLoader.isGif(data)) {
                            media.gif = GifTextureAnimation.load(data,
                                    "lobby-history:" + url.hashCode(), 320);
                        } else {
                            Pixmap pixmap = new Pixmap(data, 0, data.length);
                            try {
                                media.image = new Texture(pixmap, true);
                                media.image.setFilter(
                                        TextureFilter.MipMapLinearLinear,
                                        TextureFilter.Linear);
                            } finally {
                                pixmap.dispose();
                            }
                        }
                    } catch (RuntimeException | java.io.IOException invalid) {
                        media.failed = true;
                    }
                }));
    }

    private void clearLobbyMedia() {
        for (LobbyMedia media : lobbyMedia.values()) {
            media.dispose();
        }
        lobbyMedia.clear();
        clearLobbyHistoryMedia();
    }

    private void clearLobbyHistoryMedia() {
        for (LobbyMedia media : lobbyHistoryMedia.values()) {
            media.dispose();
        }
        lobbyHistoryMedia.clear();
    }

    private void submitLobbyCommand(LobbyCommand command, Runnable success) {
        if (lobbySession == null || lobbyCommandPending) {
            return;
        }
        lobbyCommandPending = true;
        try {
            lobbySession.submit(command).whenComplete((ignored, failure) ->
                    Gdx.app.postRunnable(() -> {
                        lobbyCommandPending = false;
                        Throwable cause = unwrap(failure);
                        if (cause == null) {
                            if (success != null) {
                                success.run();
                            }
                        } else {
                            if (command instanceof LobbyCommand.StartGame) {
                                lobbyGameStarting = false;
                            }
                            showToast(submissionError(cause));
                        }
                    }));
        } catch (RuntimeException failure) {
            lobbyCommandPending = false;
            if (command instanceof LobbyCommand.StartGame) {
                lobbyGameStarting = false;
            }
            showToast(submissionError(failure));
        }
    }

    private void returnFromLobby() {
        cancelLobbyVoiceRecording();
        clearLobbyMedia();
        closeLobbySubscription();
        if (lobbySession != null) {
            lobbySession.close();
        }
        lobbySession = null;
        lobby = null;
        selectedParticipant = null;
        clearActiveField();
        surface = Surface.MENU;
        sessionReturnedToMenu.run();
    }

    /** Returns only the lobby which owned the table that has just closed. */
    void returnFromTable(LobbySession expected) {
        if (lobbySession == Objects.requireNonNull(expected, "expected")) {
            returnFromLobby();
        }
    }

    /** Starts Swing's matching final action: recover locally or reconnect remotely. */
    void continueLastGameFromTable(LobbySession expected) {
        if (lobbySession != Objects.requireNonNull(expected, "expected")) {
            return;
        }
        boolean local = expected.snapshot().host();
        String nextNickname = connection == null
                ? expected.snapshot().localNickname() : connection.nickname();
        String nextPassword = connection == null
                ? "" : connection.password();
        String nextServer = connection == null ? "localhost"
                : connection.server();
        String nextPort = connection == null
                ? Integer.toString(NewGameConnectionDraft.DEFAULT_PORT)
                : connection.port();
        java.nio.file.Path nextAvatar = connection == null
                ? null : connection.avatar();
        returnFromLobby();
        if (local) {
            openNewGame(NewGameConnectionDraft.Mode.RECOVER);
            connection.setNickname(nextNickname);
            connection.setPassword(nextPassword);
            autoSubmitRecovery = true;
            startRecoverLoad();
        } else {
            openNewGame(NewGameConnectionDraft.Mode.JOIN);
            connection.setNickname(nextNickname);
            connection.setPassword(nextPassword);
            connection.setServer(nextServer);
            connection.setPort(nextPort);
            if (nextAvatar != null) connection.setAvatar(nextAvatar);
            submitNewGame();
        }
    }

    private void closeLobbySubscription() {
        if (lobbySubscription != null) {
            try {
                lobbySubscription.close();
            } catch (Exception ignored) {
                // Removing an in-process listener is best effort during scene teardown.
            }
            lobbySubscription = null;
        }
    }

    private void openSettings() {
        settingsReturnSurface = surface == Surface.LOBBY
                ? Surface.LOBBY : Surface.MENU;
        settingsSession.begin(settingsReturnSurface == Surface.LOBBY
                ? GdxSettingsSession.Context.WAITING_ROOM
                : GdxSettingsSession.Context.MENU, initialProperties);
        settingsAppearancePage = 0;
        settingsAudioPage = 0;
        settingsGamePage = 0;
        settingsShortcutPage = 0;
        settingsDebugScroll = 0;
        settingsShortcutCaptureId = null;
        settingsShortcutStatus = "";
        shortcutBindings.beginEdit();
        settingsOpenedWindowMode = GdxDisplayModeController.activeMode();
        settingsTable = lobby != null && settingsReturnSurface == Surface.LOBBY
                && lobby.tableSettings() != null
                        ? NewGameTableDraft.from(lobby.tableSettings()) : null;
        if (settingsTable != null && lobby.recovering()) {
            settingsTable.setEconomyLocked(true);
        }
        settingsTableSnapshot = settingsTable == null
                ? null : settingsTable.snapshot();
        settingsDiscardConfirmation = false;
        GdxAudioDevices.refreshCaptureDevicesAsync();
        clearActiveField();
        pressedHit = null;
        surface = Surface.SETTINGS;
    }

    private void closeSettings(boolean save) {
        settingsShortcutCaptureId = null;
        settingsShortcutStatus = "";
        if (save && settingsReturnSurface == Surface.LOBBY
                && lobby != null && lobby.host() && settingsTable != null) {
            NewGameTableDraft.Settings requested = settingsTable.snapshot();
            submitLobbyCommand(new LobbyCommand.UpdateTableSettings(requested),
                    () -> finishClosingSettings(true));
            return;
        }
        finishClosingSettings(save);
    }

    private void finishClosingSettings(boolean save) {
        if (save) {
            shortcutBindings.commitEdit();
            if (preferences != null) preferences.saveDeferred();
        } else {
            shortcutBindings.cancelEdit();
            String previewOutput = initialProperties.getProperty(
                    GdxAudioDevices.OUTPUT_KEY, "");
            settingsSession.restore(initialProperties);
            if (settingsOpenedWindowMode != null
                    && GdxDisplayModeController.activeMode()
                    != settingsOpenedWindowMode) {
                GdxDisplayModeController.apply(settingsOpenedWindowMode);
            }
            String restoredOutput = initialProperties.getProperty(
                    GdxAudioDevices.OUTPUT_KEY, "");
            if (GdxAudioDevices.outputSelectionChanged(previewOutput,
                    restoredOutput)) {
                GdxAudioDevices.applyConfiguredOutput(initialProperties);
            }
            audioControl.setEnabled(preferenceBoolean("sonidos", true),
                    false);
            refreshFeltFromSettings();
        }
        settingsSession.close();
        settingsTable = null;
        settingsTableSnapshot = null;
        settingsDiscardConfirmation = false;
        surface = settingsReturnSurface;
        syncMusicForSurface();
    }

    private boolean settingsHavePendingChanges() {
        if (shortcutBindings.hasPendingEdits()
                || settingsSession.propertiesChanged(initialProperties)) {
            return true;
        }
        NewGameTableDraft.Settings current = settingsTable == null
                ? null : settingsTable.snapshot();
        return !Objects.equals(current, settingsTableSnapshot);
    }

    private void requestCancelSettings() {
        if (!settingsHavePendingChanges()) {
            finishClosingSettings(false);
            return;
        }
        clearActiveField();
        editMenu = null;
        pressedHit = null;
        settingsDiscardConfirmation = true;
    }

    private void restoreFrontendAudioDefaults() {
        GdxSettingsContract.restoreAudioDefaults(initialProperties,
                frontendMayEditGlobalCommunicationRules(),
                GdxAudioDevices.hasCaptureDevices());
        GdxAudioDevices.applyConfiguredOutput(initialProperties);
        audioControl.setEnabled(true, false);
        syncMusicForSurface();
        playFrontendSwitchSound(true);
    }

    private void restoreFrontendAppearanceDefaults() {
        GdxSettingsContract.restoreAppearanceDefaults(initialProperties,
                true);
        GdxDisplayModeController.apply(GdxWindowMode.configured(
                initialProperties));
        refreshFeltFromSettings();
        playFrontendSwitchSound(true);
    }

    private boolean settingsSectionHasRestoreDefaults() {
        return GdxSettingsContract.hasRestoreDefaults(
                settingsSession.section());
    }

    private void restoreCurrentFrontendSettingsSectionDefaults() {
        switch (settingsSession.section()) {
            case APPEARANCE -> restoreFrontendAppearanceDefaults();
            case AUDIO -> restoreFrontendAudioDefaults();
            case SHORTCUTS -> {
                shortcutBindings.resetAllEdits();
                settingsShortcutCaptureId = null;
                settingsShortcutStatus =
                        "ATAJOS PREDETERMINADOS RESTAURADOS";
            }
            case GAME, DEBUG -> {
            }
        }
    }

    private List<GdxSettingsContract.Section> settingsSections() {
        return settingsSession.sections();
    }

    private List<String> settingsSubpageLabels() {
        return settingsSession.subpages(
                shortcutBindings.editableEntries().size(),
                SETTINGS_SHORTCUT_ROWS_PER_PAGE, gameText);
    }

    private int settingsSubpageIndex() {
        return switch (settingsSession.section()) {
            case APPEARANCE -> settingsAppearancePage;
            case AUDIO -> settingsAudioPage;
            case GAME -> settingsGamePage;
            case SHORTCUTS -> settingsShortcutPage;
            case DEBUG -> 0;
        };
    }

    private void selectSettingsSubpage(int index) {
        switch (settingsSession.section()) {
            case APPEARANCE -> settingsAppearancePage = index;
            case AUDIO -> settingsAudioPage = index;
            case GAME -> settingsGamePage = index;
            case SHORTCUTS -> {
                settingsShortcutPage = index;
                settingsShortcutCaptureId = null;
                settingsShortcutStatus = "";
            }
            case DEBUG -> {
            }
        }
    }

    private void drawSettingsScreen() {
        List<GdxSettingsContract.Section> sections = settingsSections();
        settingsSession.selectTab(settingsSession.tabIndex());
        float worldW = viewport.getWorldWidth();
        float worldH = viewport.getWorldHeight();
        List<String> subpages = settingsSubpageLabels();
        GdxSettingsLayout.Frame frame = GdxSettingsLayout.frame(
                worldW, worldH, sections.size(), subpages.size());
        Rectangle panel = frame.panel();
        Rectangle content = frame.content();
        float panelW = panel.width;
        float panelH = panel.height;
        float panelX = panel.x;
        float panelY = panel.y;
        int activeSubpage = MathUtils.clamp(settingsSubpageIndex(), 0,
                subpages.size() - 1);
        float contentX = content.x;
        float contentY = content.y;
        float contentW = content.width;
        float contentH = content.height;
        GdxSettingsContract.Section activeSection = settingsSession.section();
        boolean restoreVisible = settingsSectionHasRestoreDefaults();
        GdxSettingsChrome.draw(shapes, frame, sections.size(),
                subpages.size(), settingsSession.tabIndex(), activeSubpage,
                pointer, restoreVisible, 1f);

        // Keep title and subtitle in independent vertical bands. Their fonts
        // have different ascenders, so baseline-only spacing can overlap even
        // when the numeric coordinates appear separated.
        textFit(titleFont, uppercase(gameText.translate("settings.ajustes")),
                panelX + 34f,
                panelY + panelH - 48f, GOLD, false, panelW - 68f);
        textFit(smallFont, uppercase(gameText.translate("gdx.settings.subtitle")),
                panelX + 36f, panelY + panelH - 116f, CYAN, false,
                panelW - 72f);

        for (int i = 0; i < sections.size(); i++) {
            final int selected = i;
            Rectangle tab = frame.mainTab(i);
            boolean active = settingsSession.tabIndex() == i;
            textFit(actionFont, sections.get(i).label(gameText),
                    tab.x + tab.width / 2f, tab.y + 32f,
                    active ? Color.WHITE : MUTED, true, tab.width - 24f);
            hit(tab.x, tab.y, tab.width, tab.height, () -> {
                settingsSession.selectTab(selected);
                settingsAppearancePage = 0;
                settingsAudioPage = 0;
                settingsGamePage = 0;
                settingsShortcutPage = 0;
                settingsShortcutCaptureId = null;
                settingsShortcutStatus = "";
            });
        }

        for (int i = 0; i < subpages.size(); i++) {
            final int selected = i;
            Rectangle tab = frame.subTab(i);
            boolean active = i == activeSubpage;
            textFit(smallFont, subpages.get(i),
                    tab.x + tab.width / 2f, tab.y + 26f,
                    active ? GOLD : MUTED, true, tab.width - 17f);
            hit(tab.x, tab.y, tab.width, tab.height,
                    () -> selectSettingsSubpage(selected));
        }

        switch (settingsSession.section()) {
            case APPEARANCE -> drawAppearanceSettings(contentX, contentY,
                    contentW, contentH);
            case AUDIO -> drawAudioSettings(contentX, contentY, contentW,
                    contentH);
            case GAME -> drawLobbyGameSettings(contentX, contentY, contentW,
                    contentH);
            case SHORTCUTS -> drawShortcutSettings(contentX, contentY,
                    contentW, contentH);
            case DEBUG -> drawDebugSettings(contentX, contentY, contentW,
                    contentH);
        }

        Rectangle cancel = frame.cancelButton();
        textFit(actionFont, gameText.translate("ui.cancelar"),
                cancel.x + cancel.width / 2f,
                cancel.y + cancel.height / 2f + 8f, GOLD, true,
                cancel.width - 30f);
        if (!lobbyCommandPending) {
            hit(cancel.x, cancel.y, cancel.width, cancel.height,
                    this::requestCancelSettings);
        }
        if (restoreVisible) {
            Rectangle restore = frame.restoreButton();
            textFit(actionFont,
                    uppercase(gameText.translate("gdx.settings.restore_defaults")),
                    restore.x + restore.width / 2f,
                    restore.y + restore.height / 2f + 8f, GOLD, true,
                    restore.width - 30f);
            if (!lobbyCommandPending) {
                hit(restore.x, restore.y, restore.width, restore.height,
                        this::restoreCurrentFrontendSettingsSectionDefaults);
            }
        }
        Rectangle save = frame.saveButton();
        textFit(actionFont,
                lobbyCommandPending
                        ? uppercase(gameText.translate("gdx.saving"))
                        : gameText.translate("ui.guardar"),
                save.x + save.width / 2f,
                save.y + save.height / 2f + 8f, GOLD, true,
                save.width - 30f);
        if (!lobbyCommandPending) {
            hit(save.x, save.y, save.width, save.height,
                    () -> closeSettings(true));
        }
    }

    private void drawAudioSettings(float x, float y, float w, float h) {
        settingsAudioPage = MathUtils.clamp(settingsAudioPage, 0,
                GdxSettingsContract.AUDIO_PAGES.size() - 1);
        GdxSettingsContract.TogglePage page =
                GdxSettingsContract.AUDIO_PAGES.get(settingsAudioPage);
        settingsHeading(x, y, w, h,
                GdxSettingsContract.contentHeading(
                        GdxSettingsContract.Section.AUDIO,
                        page.title(gameText)),
                w - 68f);
        float rowY = y + h - 158f;
        if (settingsAudioPage == 0) {
            drawFrontendVolumeControl(x + 34f, rowY, w - 68f);
            rowY -= 74f;
        }
        for (GdxSettingsContract.ToggleOption option : page.options()) {
            boolean enabled = frontendAudioOptionEnabled(option);
            boolean value = GdxSettingsContract.displayedValue(option,
                    initialProperties, audioControl.enabled());
            Runnable action = "sonidos".equals(option.key())
                    ? this::toggleMasterSound
                    : () -> togglePreference(option.key(), option.fallback());
            toggle(x + 34f, rowY, w - 68f, option.label(gameText), value,
                    action, enabled);
            rowY -= 84f;
        }
        if (GdxSettingsContract.hasVoiceRetention(page)) {
            settingsStepper(x + 34f, rowY, w - 68f, 70f,
                    uppercase(gameText.translate(
                            "gdx.settings.row.keep_voice_notes")),
                    GdxSettingsContract.voiceRetentionLabel(
                            initialProperties, gameText),
                    () -> {
                        GdxSettingsContract.adjustVoiceRetention(
                                initialProperties, -1);
                    }, () -> {
                        GdxSettingsContract.adjustVoiceRetention(
                                initialProperties, 1);
                    });
        }
        if (GdxSettingsContract.hasAudioDevices(page)) {
            settingsStepper(x + 34f, rowY, w - 68f, 70f,
                    uppercase(gameText.translate(
                            "gdx.settings.row.game_output")),
                    GdxAudioDevices.outputLabel(initialProperties), () -> {
                        GdxAudioDevices.adjustOutput(initialProperties, -1);
                    }, () -> {
                        GdxAudioDevices.adjustOutput(initialProperties, 1);
                    });
            settingsStepper(x + 34f, rowY - 84f, w - 68f, 70f,
                    uppercase(gameText.translate(
                            "gdx.settings.row.microphone")),
                    GdxAudioDevices.captureLabel(initialProperties), () -> {
                        GdxAudioDevices.adjustCapture(initialProperties, -1);
                    }, () -> {
                        GdxAudioDevices.adjustCapture(initialProperties, 1);
                    });
        }
    }

    private boolean frontendAudioOptionEnabled(
            GdxSettingsContract.ToggleOption option) {
        return GdxSettingsContract.enabled(option, initialProperties,
                audioControl.enabled(),
                frontendMayEditGlobalCommunicationRules());
    }

    private boolean frontendMayEditGlobalCommunicationRules() {
        return settingsReturnSurface != Surface.LOBBY
                || lobby == null || lobby.host();
    }

    private void drawFrontendVolumeControl(float x, float y, float w) {
        GdxSettingsLayout.VolumeRow row = GdxSettingsLayout.volumeRow(x, y, w);
        Rectangle bounds = row.bounds();
        Rectangle label = row.label();
        Rectangle percentage = row.percentage();
        Rectangle slider = row.slider();
        Rectangle minus = row.minusButton();
        Rectangle plus = row.plusButton();
        outerBox(bounds.x, bounds.y, bounds.width, bounds.height,
                LINE, PANEL_LIGHT);
        textFit(smallFont, uppercase(gameText.translate(
                "gdx.settings.row.master_volume")), label.x,
                label.y + 31f, Color.WHITE, false, label.width);
        textFit(smallFont, Math.round(masterVolume() * 100f) + "%",
                percentage.x + percentage.width / 2f,
                percentage.y + 31f, GOLD, true, percentage.width - 8f);
        shapes.setColor(new Color(0x253248ff));
        roundedRect(slider.x, slider.y, slider.width, slider.height, 6f);
        shapes.setColor(CYAN);
        roundedRect(slider.x, slider.y, slider.width * masterVolume(),
                slider.height, 6f);
        themedButton(minus.x, minus.y, minus.width, minus.height, "−",
                ButtonTone.NEUTRAL,
                () -> adjustFrontendMasterVolume(-0.05f), true);
        themedButton(plus.x, plus.y, plus.width, plus.height, "+",
                ButtonTone.NEUTRAL,
                () -> adjustFrontendMasterVolume(0.05f), true);
    }

    private void drawAppearanceSettings(float x, float y, float w, float h) {
        int pages = GdxSettingsContract.APPEARANCE_PAGES.size() + 1;
        settingsAppearancePage = MathUtils.clamp(settingsAppearancePage,
                0, pages - 1);
        String pageTitle = settingsAppearancePage == 0
                ? uppercase(gameText.translate("gdx.settings.page.table"))
                : GdxSettingsContract.APPEARANCE_PAGES
                        .get(settingsAppearancePage - 1).title(gameText);
        settingsHeading(x, y, w, h,
                GdxSettingsContract.contentHeading(
                        GdxSettingsContract.Section.APPEARANCE, pageTitle),
                w - 68f);
        float rowY = y + h - 158f;
        if (settingsAppearancePage == 0) {
            settingsStepper(x + 34f, rowY, w - 68f, 70f,
                    uppercase(gameText.translate("gdx.settings.row.deck")),
                    GdxAppearanceOptions.deckLabel(configuredDeck(), gameText),
                    this::selectPreviousDeck, this::selectNextDeck);
            settingsStepper(x + 34f, rowY - 78f, w - 68f, 70f,
                    uppercase(gameText.translate(
                            "gdx.settings.row.card_back")),
                    GdxAppearanceOptions.cardBackLabel(configuredBack(),
                            gameText),
                    this::selectPreviousBack, this::selectNextBack);
            settingsStepper(x + 34f, rowY - 156f, w - 68f, 70f,
                    uppercase(gameText.translate("gdx.settings.row.felt")),
                    GdxAppearanceOptions.feltLabel(configuredFelt(), gameText),
                    this::selectPreviousFelt, this::selectNextFelt);
            settingsStepper(x + 34f, rowY - 234f, w - 68f, 70f,
                    uppercase(gameText.translate(
                            "gdx.settings.row.light_off")),
                    GdxAppearanceOptions.lightLevelLabel(initialProperties),
                    () -> adjustLightLevel(-1),
                    () -> adjustLightLevel(1));
            settingsStepper(x + 34f, rowY - 312f, w - 68f, 70f,
                    uppercase(gameText.translate(
                            "gdx.settings.row.window_mode")),
                    windowModeSettingLabel(), this::selectPreviousWindowMode,
                    this::selectNextWindowMode);
            settingsStepper(x + 34f, rowY - 390f, w - 68f, 70f,
                    uppercase(gameText.translate(
                            "gdx.settings.row.antialiasing")),
                    msaaSettingLabel(),
                    this::selectPreviousMsaa, this::selectNextMsaa);
            return;
        }
        GdxSettingsContract.TogglePage page =
                GdxSettingsContract.APPEARANCE_PAGES.get(
                        settingsAppearancePage - 1);
        float rowStride = GdxSettingsLayout.rowStride(h,
                page.options().size());
        if (GdxSettingsContract.hasAppearanceAnimationOptions(page)) {
            for (GdxAppearanceOptions.Choice option
                    : GdxAppearanceOptions.ANIMATION_CHOICES) {
                boolean enabled = GdxAppearanceOptions.enabled(option,
                        initialProperties);
                settingsStepper(x + 34f, rowY, w - 68f, 70f,
                        option.label(gameText),
                        GdxAppearanceOptions.selectedLabel(option,
                                initialProperties, gameText),
                        () -> {
                            GdxAppearanceOptions.adjust(option,
                                    initialProperties, -1);
                        }, () -> {
                            GdxAppearanceOptions.adjust(option,
                                    initialProperties, 1);
                        }, enabled);
                rowY -= rowStride;
            }
            return;
        }
        for (GdxSettingsContract.ToggleOption option : page.options()) {
            boolean enabled = GdxSettingsContract.enabled(option,
                    initialProperties, audioControl.enabled());
            toggle(x + 34f, rowY, w - 68f, option.label(gameText),
                    preferenceBoolean(option.key(), option.fallback()),
                    () -> togglePreference(option.key(), option.fallback()),
                    enabled);
            rowY -= rowStride;
        }
    }

    /** Compact settings row: label and value share one bounded surface. */
    private void settingsChoice(float x, float y, float w, float h,
            String label, String value, Runnable action) {
        settingsChoice(x, y, w, h, label, value, action, true);
    }

    private void settingsChoice(float x, float y, float w, float h,
            String label, String value, Runnable action, boolean enabled) {
        Color border = enabled && hovered(x, y, w, h) ? CYAN
                : enabled ? LINE : new Color(0x253044ff);
        Color fill = enabled && pressed(x, y, w, h)
                ? new Color(0x0b1424ff)
                : enabled ? PANEL_LIGHT : new Color(0x0b111ddd);
        outerBox(x, y, w, h, border, fill);
        float valueX = x + w * 0.71f;
        textFit(smallFont, label, x + 22f, y + h / 2f + 10f,
                enabled ? Color.WHITE : DISABLED, false, w * 0.48f);
        textFit(uiFont, value, valueX, y + h / 2f + 11f,
                enabled ? GOLD : DISABLED, true, w * 0.25f);
        shapes.setColor(enabled ? GOLD : DISABLED);
        float cy = y + h / 2f;
        shapes.rectLine(x + w - 35f, cy - 9f,
                x + w - 26f, cy, 3f);
        shapes.rectLine(x + w - 26f, cy,
                x + w - 35f, cy + 9f, 3f);
        if (enabled) hit(x, y, w, h, action);
    }

    private void settingsStepper(float x, float y, float w, float h,
            String label, String value, Runnable minus, Runnable plus) {
        settingsStepper(x, y, w, h, label, value, minus, plus, true);
    }

    private void settingsStepper(float x, float y, float w, float h,
            String label, String value, Runnable minus, Runnable plus,
            boolean enabled) {
        outerBox(x, y, w, h,
                enabled && hovered(x, y, w, h) ? CYAN
                        : enabled ? LINE : new Color(0x253044ff),
                enabled ? PANEL_LIGHT : new Color(0x0b111ddd));
        float buttonW = 62f;
        // Device names, window modes and animation styles are substantially
        // longer than numeric values. Keep one shared geometry that gives the
        // selected value enough room instead of letting it collide with the
        // decrement/increment controls.
        float valueW = 280f;
        float controlsX = x + w - buttonW * 2f - valueW;
        shapes.setColor(new Color(0x31445fff));
        shapes.rect(controlsX, y + 8f, 2f, h - 16f);
        shapes.rect(controlsX + buttonW, y + 8f, 2f, h - 16f);
        shapes.rect(controlsX + buttonW + valueW, y + 8f, 2f, h - 16f);
        textFit(smallFont, label, x + 22f, y + h / 2f + 10f,
                enabled ? Color.WHITE : DISABLED, false,
                controlsX - x - 38f);
        text(headingFont, "-", controlsX + buttonW / 2f,
                y + h / 2f + 14f, enabled ? Color.WHITE : DISABLED, true);
        textFit(uiFont, value, controlsX + buttonW + valueW / 2f,
                y + h / 2f + 11f, enabled ? GOLD : DISABLED, true,
                valueW - 12f);
        text(headingFont, "+", x + w - buttonW / 2f,
                y + h / 2f + 14f, enabled ? Color.WHITE : DISABLED, true);
        if (enabled) {
            hit(controlsX, y, buttonW, h, minus);
            hit(x + w - buttonW, y, buttonW, h, plus);
        }
    }

    private void adjustLightLevel(int direction) {
        GdxAppearanceOptions.adjustLightLevel(initialProperties, direction);
    }

    private void drawLobbyGameSettings(float x, float y, float w, float h) {
        if (settingsTable == null || lobby == null) {
            settingsHeading(x, y, w, h,
                    settingsGameText("unavailable"));
            return;
        }
        List<String> pages = settingsSession.gamePages();
        if (pages.isEmpty()) {
            settingsHeading(x, y, w, h,
                    settingsGameText("unavailable"));
            return;
        }
        settingsGamePage = MathUtils.clamp(settingsGamePage, 0,
                pages.size() - 1);
        String title = pages.get(settingsGamePage);
        settingsHeading(x, y, w, h,
                GdxSettingsContract.contentHeading(
                        GdxSettingsContract.Section.GAME, title),
                w - 68f);
        boolean editable = lobby.host();
        boolean economyEditable = editable && !settingsTable.economyLocked();
        float rowY = y + h - 158f;
        switch (settingsGamePage) {
            case 0 -> drawLobbyBlindSettings(x, w, rowY, economyEditable);
            case 1 -> drawLobbyPurchaseSettings(x, w, rowY, editable,
                    economyEditable);
            case 2 -> drawLobbyRebuySettings(x, w, rowY, editable);
            case 3 -> drawLobbyBotSettings(x, w, rowY, editable);
            case 4 -> drawLobbyRoundSettings(x, w, rowY, editable);
            default -> drawLobbyRuleSettings(x, w, rowY, editable);
        }
        if (!editable) {
            textFit(tinyFont, settingsGameText("host_only"),
                    x + w / 2f, y + 18f, MUTED, true, w - 68f);
        } else if (settingsTable.economyLocked()) {
            textFit(tinyFont, settingsGameText("economy_locked"),
                    x + w / 2f, y + 18f, GOLD, true, w - 68f);
        }
    }

    private void drawLobbyBlindSettings(float x, float w, float y,
            boolean enabled) {
        float columnGap = 24f;
        float columnWidth = (w - 68f - columnGap) / 2f;
        float leftX = x + 34f;
        float rightX = leftX + columnWidth + columnGap;
        settingsStepper(leftX, y, columnWidth, 70f,
                settingsGameText("row.structure"),
                settingsTable.structureName() == null
                        ? uppercase(gameText.translate(
                                "gdx.settings.value.default"))
                        : settingsTable.structureName(),
                () -> adjustSettingsBlindStructure(-1),
                () -> adjustSettingsBlindStructure(1), enabled);
        settingsStepper(leftX, y - 84f, columnWidth, 70f,
                settingsGameText("row.initial_blinds"), settingsBlindLevel(),
                () -> adjustSettingsBlindLevel(-1),
                () -> adjustSettingsBlindLevel(1), enabled);
        toggle(leftX, y - 168f, columnWidth,
                settingsGameText("row.ante"),
                settingsTable.ante(), () -> settingsTable
                        .setAnte(!settingsTable.ante()), enabled);
        toggle(leftX, y - 252f, columnWidth,
                settingsGameText("row.straddle"),
                settingsTable.straddle(), () -> settingsTable
                        .setStraddle(!settingsTable.straddle()), enabled);
        button(leftX, y - 336f, columnWidth, 70f,
                settingsGameText("row.manage_structures"), false,
                this::openSettingsBlindStructureEditor, enabled);
        toggle(rightX, y, columnWidth,
                settingsGameText("row.increase_blinds"),
                settingsTable.increaseBlinds(), () -> settingsTable
                        .setIncreaseBlinds(!settingsTable.increaseBlinds()),
                enabled);
        settingsStepper(rightX, y - 84f, columnWidth, 70f,
                settingsGameText("row.unit"),
                settingsBlindUnit(), this::toggleSettingsBlindIncreaseType,
                this::toggleSettingsBlindIncreaseType,
                enabled && settingsTable.increaseBlinds());
        settingsStepper(rightX, y - 168f, columnWidth, 70f,
                settingsGameText("row.interval"),
                settingsBlindInterval(),
                () -> adjustSettingsBlindInterval(-1),
                () -> adjustSettingsBlindInterval(1),
                enabled && settingsTable.increaseBlinds());
        toggle(rightX, y - 252f, columnWidth,
                settingsGameText("row.blind_cap"),
                settingsTable.blindCap(), () -> settingsTable
                        .setBlindCap(!settingsTable.blindCap()),
                enabled && settingsTable.blindCapControlEnabled());
        settingsStepper(rightX, y - 336f, columnWidth, 70f,
                settingsGameText("row.cap"), settingsBlindCap(),
                () -> adjustSettingsBlindCap(-1),
                () -> adjustSettingsBlindCap(1),
                enabled && settingsTable.blindCapRaisesEnabled());
    }

    private void drawLobbyPurchaseSettings(float x, float w, float y,
            boolean editable, boolean economyEditable) {
        toggle(x + 34f, y, w - 68f,
                settingsGameText("row.fixed_buyin"),
                settingsTable.fixedBuyin(), () -> settingsTable
                        .setFixedBuyin(!settingsTable.fixedBuyin()),
                economyEditable);
        settingsStepper(x + 34f, y - 84f, w - 68f,
                settingsGameText("row.initial_buyin"),
                settingsTable.buyin(),
                () -> settingsTable.setBuyin(settingsTable.buyin() - 1),
                () -> settingsTable.setBuyin(settingsTable.buyin() + 1),
                economyEditable && settingsTable.fixedBuyin());
        settingsStepper(x + 34f, y - 168f, w - 68f,
                settingsGameText("row.minimum_range_bb"),
                settingsTable.minBuyinBb(), () -> settingsTable
                        .setMinBuyinBb(settingsTable.minBuyinBb() - 5),
                () -> settingsTable.setMinBuyinBb(
                        settingsTable.minBuyinBb() + 5), economyEditable);
        settingsStepper(x + 34f, y - 252f, w - 68f,
                settingsGameText("row.maximum_range_bb"),
                settingsTable.maxBuyinBb(), () -> settingsTable
                        .setMaxBuyinBb(settingsTable.maxBuyinBb() - 5),
                () -> settingsTable.setMaxBuyinBb(
                        settingsTable.maxBuyinBb() + 5), economyEditable);
        settingsStepper(x + 34f, y - 336f, w - 68f, 70f,
                settingsGameText("row.rebuy_cap"),
                settingsTable.rebuyCapPolicy()
                        == NewGameTableDraft.RebuyCapPolicy.BUY_IN
                                ? "BUY-IN"
                                : settingsGameText("value.highest_stack"),
                this::cycleSettingsRebuyCap,
                this::cycleSettingsRebuyCap,
                editable && settingsTable.rebuy());
    }

    private void drawLobbyRebuySettings(float x, float w, float y,
            boolean editable) {
        toggle(x + 34f, y, w - 68f, settingsGameText("row.rebuy"),
                settingsTable.rebuy(),
                () -> settingsTable.setRebuy(!settingsTable.rebuy()),
                editable);
        toggle(x + 34f, y - 84f, w - 68f,
                settingsGameText("row.player_limit"),
                settingsTable.rebuyLimit(), () -> settingsTable
                        .setRebuyLimit(!settingsTable.rebuyLimit()),
                editable && settingsTable.rebuy());
        settingsStepper(x + 34f, y - 168f, w - 68f,
                settingsGameText("row.maximum_rebuys"),
                settingsTable.rebuyLimitCount(),
                () -> settingsTable.setRebuyLimitCount(
                        settingsTable.rebuyLimitCount() - 1),
                () -> settingsTable.setRebuyLimitCount(
                        settingsTable.rebuyLimitCount() + 1),
                editable && settingsTable.rebuyLimitCountEnabled());
    }

    private void drawLobbyBotSettings(float x, float w, float y,
            boolean editable) {
        settingsStepper(x + 34f, y, w - 68f, 70f,
                settingsGameText("row.bot_difficulty"),
                settingsBotDifficulty(),
                () -> adjustSettingsBotDifficulty(-1),
                () -> adjustSettingsBotDifficulty(1),
                editable);
        toggle(x + 34f, y - 84f, w - 68f,
                settingsGameText("row.bot_rebuy"),
                settingsTable.botRebuy(), () -> settingsTable
                        .setBotRebuy(!settingsTable.botRebuy()),
                editable && settingsTable.botRebuyEnabled());
        toggle(x + 34f, y - 168f, w - 68f,
                settingsGameText("row.bot_balance"),
                settingsTable.botBalanceToHumans(), () -> settingsTable
                        .setBotBalanceToHumans(
                                !settingsTable.botBalanceToHumans()), editable);
    }

    private void drawLobbyRoundSettings(float x, float w, float y,
            boolean editable) {
        toggle(x + 34f, y, w - 68f,
                settingsGameText("row.hand_limit"),
                settingsTable.handLimit(), () -> settingsTable
                        .setHandLimit(!settingsTable.handLimit()), editable);
        settingsStepper(x + 34f, y - 84f, w - 68f,
                settingsGameText("row.hand_count"),
                settingsTable.handLimitCount(), () -> settingsTable
                        .setHandLimitCount(settingsTable.handLimitCount() - 1),
                () -> settingsTable.setHandLimitCount(
                        settingsTable.handLimitCount() + 1),
                editable && settingsTable.handLimit());
        toggle(x + 34f, y - 168f, w - 68f,
                settingsGameText("row.think_time"),
                settingsTable.thinkTime(), () -> settingsTable
                        .setThinkTime(!settingsTable.thinkTime()), editable);
        settingsStepper(x + 34f, y - 252f, w - 68f,
                settingsGameText("row.think_seconds"),
                settingsTable.thinkSeconds(),
                () -> settingsTable.setThinkSeconds(
                        settingsTable.thinkSeconds() - 5),
                () -> settingsTable.setThinkSeconds(
                        settingsTable.thinkSeconds() + 5),
                editable && settingsTable.thinkTime());
        settingsStepper(x + 34f, y - 336f, w - 68f,
                settingsGameText("row.showdown_seconds"),
                settingsTable.showdownSeconds(),
                () -> settingsTable.setShowdownSeconds(
                        settingsTable.showdownSeconds() - 5),
                () -> settingsTable.setShowdownSeconds(
                        settingsTable.showdownSeconds() + 5), editable);
    }

    private void drawLobbyRuleSettings(float x, float w, float y,
            boolean editable) {
        toggle(x + 34f, y, w - 68f, "IWTSTH",
                settingsTable.iwtsth(), () -> settingsTable
                        .setIwtsth(!settingsTable.iwtsth()), editable);
        toggle(x + 34f, y - 84f, w - 68f, "RUN IT TWICE",
                settingsTable.runItTwice(), () -> settingsTable
                        .setRunItTwice(!settingsTable.runItTwice()), editable);
        settingsStepper(x + 34f, y - 168f, w - 68f, 70f,
                settingsGameText("row.rabbit_hunting"), settingsRabbitText(),
                () -> adjustSettingsRabbit(-1),
                () -> adjustSettingsRabbit(1), editable);
    }

    private void settingsStepper(float x, float y, float w, String label,
            int value, Runnable minus, Runnable plus, boolean enabled) {
        outerBox(x, y, w, 70f,
                enabled && hovered(x, y, w, 70f) ? CYAN
                        : enabled ? LINE : new Color(0x253044ff),
                enabled ? PANEL_LIGHT : new Color(0x0b111ddd));
        textFit(smallFont, label, x + 22f, y + 44f,
                enabled ? Color.WHITE : DISABLED, false, w * 0.50f);
        float controlsX = x + w * 0.62f;
        themedButton(controlsX, y + 10f, 48f, 48f, "−",
                ButtonTone.NEUTRAL, minus, enabled);
        textFit(uiFont, Integer.toString(value), controlsX + 100f, y + 44f,
                enabled ? GOLD : DISABLED, true, 82f);
        themedButton(x + w - 70f, y + 10f, 48f, 48f, "+",
                ButtonTone.NEUTRAL, plus, enabled);
    }

    private String settingsBlindLevel() {
        NewGameTableDraft.BlindLevel level = settingsTable.blindLevel();
        return formatBlind(level.smallBlind()) + " / "
                + formatBlind(level.bigBlind());
    }

    private void adjustSettingsBlindLevel(int direction) {
        if (direction == 0 || settingsTable.blindLevels().isEmpty()) return;
        settingsTable.setBlindLevelIndex(Math.floorMod(
                settingsTable.blindLevelIndex() + Integer.signum(direction),
                settingsTable.blindLevels().size()));
    }

    private void adjustSettingsBlindStructure(int direction) {
        if (direction == 0) return;
        List<BlindStructureCatalog.Entry> saved = BlindStructureCatalog.read(
                initialProperties);
        int current = 0;
        if (settingsTable.structureName() != null) {
            for (int index = 0; index < saved.size(); index++) {
                if (saved.get(index).name().equals(
                        settingsTable.structureName())) {
                    current = index + 1;
                    break;
                }
            }
        }
        int target = Math.floorMod(current + Integer.signum(direction),
                saved.size() + 1);
        if (target == 0) {
            settingsTable.setBlindStructure(null,
                    Arrays.stream(BlindStructureRules.defaultLevels())
                            .map(level -> new NewGameTableDraft.BlindLevel(
                                    level[0], level[1]))
                            .toList(), settingsTable.blindLevelIndex());
            return;
        }
        BlindStructureCatalog.Entry entry = saved.get(target - 1);
        settingsTable.setBlindStructure(entry.name(), entry.levels().stream()
                .map(level -> new NewGameTableDraft.BlindLevel(
                        level.smallBlind(), level.bigBlind()))
                .toList(), settingsTable.blindLevelIndex());
    }

    private String settingsBlindInterval() {
        return Integer.toString(settingsTable.blindInterval());
    }

    private String settingsBlindUnit() {
        return settingsTable.blindIncreaseType()
                == NewGameTableDraft.BlindIncreaseType.MINUTES
                        ? settingsGameText("value.minutes")
                        : settingsGameText("value.hands");
    }

    private void toggleSettingsBlindIncreaseType() {
        settingsTable.setBlindIncreaseType(settingsTable.blindIncreaseType()
                == NewGameTableDraft.BlindIncreaseType.MINUTES
                        ? NewGameTableDraft.BlindIncreaseType.HANDS
                        : NewGameTableDraft.BlindIncreaseType.MINUTES);
    }

    private void adjustSettingsBlindInterval(int direction) {
        if (direction == 0) return;
        settingsTable.setBlindInterval(settingsTable.blindInterval()
                + Integer.signum(direction));
    }

    private String settingsBlindCap() {
        return settingsGameText("value.raises",
                settingsTable.blindCapRaises(),
                formatBlind(settingsTable.blindCapLevel().smallBlind())
                        + " / "
                        + formatBlind(settingsTable.blindCapLevel().bigBlind()));
    }

    private void adjustSettingsBlindCap(int direction) {
        if (direction == 0) return;
        int maximum = settingsTable.maxBlindCapRaises();
        settingsTable.setBlindCapRaises(MathUtils.clamp(
                settingsTable.blindCapRaises() + Integer.signum(direction),
                1, maximum));
    }

    private void cycleSettingsRebuyCap() {
        settingsTable.setRebuyCapPolicy(settingsTable.rebuyCapPolicy()
                == NewGameTableDraft.RebuyCapPolicy.BUY_IN
                        ? NewGameTableDraft.RebuyCapPolicy.HIGHEST_STACK
                        : NewGameTableDraft.RebuyCapPolicy.BUY_IN);
    }

    private String settingsBotDifficulty() {
        return switch (settingsTable.botDifficulty()) {
            case EASY -> settingsGameText("value.easy");
            case MEDIUM -> settingsGameText("value.medium");
            case HARD -> settingsGameText("value.hard");
        };
    }

    private void adjustSettingsBotDifficulty(int direction) {
        if (direction == 0) return;
        NewGameTableDraft.BotDifficulty[] values =
                NewGameTableDraft.BotDifficulty.values();
        settingsTable.setBotDifficulty(values[Math.floorMod(
                settingsTable.botDifficulty().ordinal()
                        + Integer.signum(direction), values.length)]);
    }

    private String settingsRabbitText() {
        return GdxLiveSettingsSummary.rabbitHuntingLabel(
                settingsTable.rabbitHunting().ordinal());
    }

    private void adjustSettingsRabbit(int direction) {
        if (direction == 0) return;
        NewGameTableDraft.RabbitHunting[] values =
                NewGameTableDraft.RabbitHunting.values();
        settingsTable.setRabbitHunting(values[Math.floorMod(
                settingsTable.rabbitHunting().ordinal()
                        + Integer.signum(direction), values.length)]);
    }

    private void drawShortcutSettings(float x, float y, float w, float h) {
        List<GdxShortcutBindings.ShortcutEntry> entries =
                shortcutBindings.editableEntries();
        int pages = Math.max(1, (entries.size()
                + SETTINGS_SHORTCUT_ROWS_PER_PAGE - 1)
                / SETTINGS_SHORTCUT_ROWS_PER_PAGE);
        settingsShortcutPage = MathUtils.clamp(settingsShortcutPage,
                0, pages - 1);
        settingsHeading(x, y, w, h,
                GdxSettingsContract.Section.SHORTCUTS.label());
        int first = settingsShortcutPage * SETTINGS_SHORTCUT_ROWS_PER_PAGE;
        int visible = Math.min(SETTINGS_SHORTCUT_ROWS_PER_PAGE,
                entries.size() - first);
        float firstY = y + h - 158f;
        for (int row = 0; row < visible; row++) {
            GdxShortcutBindings.ShortcutEntry entry = entries.get(first + row);
            boolean capturing = entry.id().equals(settingsShortcutCaptureId);
            shortcutRow(x + 34f, firstY - row * 70f, w - 68f,
                    capturing ? "PULSA UNA TECLA" : entry.display(),
                    entry.description(), capturing);
            hit(x + 34f, firstY - row * 70f, w - 68f, 62f, () -> {
                settingsShortcutCaptureId = entry.id();
                settingsShortcutStatus = "PULSA LA NUEVA COMBINACIÓN";
            });
        }
        if (!settingsShortcutStatus.isBlank()) {
            Color color = settingsShortcutStatus.contains("USO")
                    || settingsShortcutStatus.contains("NO COMPATIBLE")
                            ? ORANGE : CYAN;
            textFit(tinyFont, settingsShortcutStatus, x + w / 2f,
                    y + 62f, color, true, w - 68f);
        }
    }

    private void drawDebugSettings(float x, float y, float w, float h) {
        settingsHeading(x, y, w, h, "DEBUG");
        float consoleX = x + 24f;
        float consoleY = y + 28f;
        float consoleW = w - 48f;
        float consoleH = h - 122f;
        outerBox(consoleX, consoleY, consoleW, consoleH, LINE,
                new Color(0x03070cff));

        List<String> lines = debugLines();
        int maximum = Math.max(0,
                lines.size() - SETTINGS_DEBUG_VISIBLE_LINES);
        settingsDebugScroll = MathUtils.clamp(settingsDebugScroll, 0, maximum);
        int first = Math.max(0, lines.size() - SETTINGS_DEBUG_VISIBLE_LINES
                - settingsDebugScroll);
        int last = Math.min(lines.size(),
                first + SETTINGS_DEBUG_VISIBLE_LINES);
        float lineY = consoleY + consoleH - 25f;
        for (int i = first; i < last; i++) {
            String line = lines.get(i);
            textFit(tinyFont, line, consoleX + 14f, lineY,
                    debugLineColor(line), false, consoleW - 42f);
            lineY -= 25f;
        }

        float trackX = consoleX + consoleW - 16f;
        shapes.setColor(new Color(0x26364dff));
        roundedRect(trackX, consoleY + 8f, 6f, consoleH - 16f, 3f);
        float thumbH = maximum == 0 ? consoleH - 16f
                : Math.max(32f, (consoleH - 16f)
                        * SETTINGS_DEBUG_VISIBLE_LINES / lines.size());
        float travel = Math.max(0f, consoleH - 16f - thumbH);
        float ratio = maximum == 0 ? 0f
                : (float) settingsDebugScroll / maximum;
        shapes.setColor(CYAN_DARK);
        roundedRect(trackX, consoleY + 8f + travel * ratio,
                6f, thumbH, 3f);
    }

    private static List<String> debugLines() {
        return DebugLog.snapshot().lines().toList();
    }

    private static Color debugLineColor(String line) {
        if (line.startsWith("SEVERE:")) return new Color(0xff5b68ff);
        if (line.startsWith("WARNING:")) return GOLD;
        if (line.startsWith("CONFIG:")) return CYAN;
        if (line.startsWith("FINE:") || line.startsWith("FINER:")
                || line.startsWith("FINEST:")) return DISABLED;
        if (line.startsWith("INFO:")) return new Color(0x6ee7a8ff);
        return MUTED;
    }

    private void settingsHeading(float x, float y, float w, float h,
            String title) {
        settingsHeading(x, y, w, h, title, w - 68f);
    }

    private void settingsHeading(float x, float y, float w, float h,
            String title, float titleWidth) {
        textFit(headingFont, title, x + 34f, y + h - 46f,
                GOLD, false, titleWidth);
        shapes.setColor(new Color(0x31445fbb));
        shapes.rect(x + 34f, y + h - 76f, w - 68f, 1f);
    }

    private void shortcutRow(float x, float y, float w, String key,
            String description, boolean capturing) {
        outerBox(x, y, w, 62f, capturing ? GOLD : LINE, PANEL_LIGHT);
        outerBox(x + 12f, y + 8f, 216f, 46f,
                capturing ? GOLD : CYAN_DARK, new Color(0x07111fff));
        textFit(smallFont, key, x + 120f, y + 38f,
                capturing ? GOLD : CYAN, true, 194f);
        textFit(smallFont, description, x + 252f, y + 38f,
                Color.WHITE, false, w - 276f);
    }

    private boolean preferenceBoolean(String key, boolean fallback) {
        return Boolean.parseBoolean(initialProperties.getProperty(key,
                Boolean.toString(fallback)));
    }

    private float masterVolume() {
        try {
            return MathUtils.clamp(Float.parseFloat(initialProperties
                    .getProperty("master_volume", "0.8")), 0f, 1f);
        } catch (NumberFormatException invalid) {
            return 0.8f;
        }
    }

    private void togglePreference(String key, boolean fallback) {
        boolean next = !preferenceBoolean(key, fallback);
        initialProperties.setProperty(key, Boolean.toString(next));
        if ("musica".equals(key) || "sonido_ascensor".equals(key)
                || "musica_sala_espera".equals(key)) {
            syncMusicForSurface();
        }
        if ("sonido_efectos".equals(key)
                && !preferenceBoolean(key, fallback)) {
            soundEnabledCue.stop();
            soundDisabledCue.stop();
            participantJoinedCue.stop();
            participantLeftCue.stop();
        }
        boolean displayed = key.equals("audio_block_voice_messages")
                || key.equals("audio_block_tts_local") ? !next : next;
        playFrontendSwitchSound(displayed);
    }

    private void playFrontendSwitchSound(boolean enabled) {
        if (!preferenceBoolean("sonido_interruptor", true)) return;
        playFrontendSound(enabled ? soundEnabledCue : soundDisabledCue, 0.60f);
    }

    private void playPreferenceSound(String resource, String preferenceKey,
            float volume) {
        if (!preferenceBoolean(preferenceKey, true)) return;
        Sound sound = preferenceSoundCues.computeIfAbsent(resource,
                path -> Gdx.audio.newSound(Gdx.files.internal("sounds/" + path)));
        playFrontendSound(sound, volume);
    }

    private void playFrontendSound(Sound sound, float volume) {
        if (!audioControl.enabled()
                || !preferenceBoolean("sonido_efectos", true)) return;
        sound.play(volume * masterVolume());
    }

    private String configuredDeck() {
        return presentationSettings.deck();
    }

    private void selectNextDeck() {
        presentationSettings.selectNextDeck(false);
        playPreferenceSound("misc/uncover.wav", "sonido_destape", 0.92f);
    }

    private void selectPreviousDeck() {
        presentationSettings.selectPreviousDeck(false);
        playPreferenceSound("misc/uncover.wav", "sonido_destape", 0.92f);
    }

    private String configuredBack() {
        return presentationSettings.cardBack();
    }

    private void selectNextBack() {
        presentationSettings.selectNextCardBack(false);
        playPreferenceSound("misc/uncover.wav", "sonido_destape", 0.92f);
    }

    private void selectPreviousBack() {
        presentationSettings.selectPreviousCardBack(false);
        playPreferenceSound("misc/uncover.wav", "sonido_destape", 0.92f);
    }

    private String configuredFelt() {
        return presentationSettings.felt();
    }

    private void selectNextFelt() {
        presentationSettings.selectNextFelt(false);
        refreshFeltFromSettings();
        playPreferenceSound("misc/mat.wav", "sonido_tapete", 0.92f);
    }

    private void selectPreviousFelt() {
        presentationSettings.selectPreviousFelt(false);
        refreshFeltFromSettings();
        playPreferenceSound("misc/mat.wav", "sonido_tapete", 0.92f);
    }

    private String msaaSettingLabel() {
        int requested = presentationSettings.requestedMsaaSamples();
        int actual = presentationSettings.actualMsaaSamples();
        String requestedText = requested == 0 ? "DESACTIVADO" : requested + "X";
        if (actual == requested) return requestedText + "  ·  ACTIVO";
        String actualText = actual == 0 ? "DESACTIVADO" : actual + "X";
        return requestedText + "  ·  REINICIAR (ACTUAL " + actualText + ")";
    }

    private void selectNextMsaa() {
        presentationSettings.selectNextMsaaSamples(false);
    }

    private void selectPreviousMsaa() {
        presentationSettings.selectPreviousMsaaSamples(false);
    }

    private String windowModeSettingLabel() {
        GdxWindowMode configured = GdxWindowMode.configured(initialProperties);
        return configured.label(gameText) + "  \u00b7  "
                + uppercase(gameText.translate("gdx.settings.value.active"));
    }

    private void selectNextWindowMode() {
        adjustWindowMode(1);
    }

    private void selectPreviousWindowMode() {
        adjustWindowMode(-1);
    }

    private void adjustWindowMode(int direction) {
        String previous = initialProperties.getProperty(
                GdxWindowMode.PREFERENCE_KEY);
        GdxWindowMode next = GdxWindowMode.adjust(initialProperties,
                direction);
        if (!GdxDisplayModeController.apply(next)) {
            if (previous == null) {
                initialProperties.remove(GdxWindowMode.PREFERENCE_KEY);
            } else {
                initialProperties.setProperty(
                        GdxWindowMode.PREFERENCE_KEY, previous);
            }
            return;
        }
    }

    private void drawSoundControl(float x, float y, float w, float h,
            boolean showLabel) {
        if (showLabel) {
            text(smallFont, "SONIDO", x, y + h * 0.66f, MUTED, false);
        }
        float iconSize = Math.min(52f, h);
        float iconX = showLabel ? x + w - iconSize
                : x + (w - iconSize) / 2f;
        uiImages.add(new UiImageItem(audioControl.enabled()
                ? soundIcon : muteIcon, iconX,
                y + (h - iconSize) / 2f, iconSize, iconSize));
        float hitX = showLabel ? x - 12f : x - 8f;
        float hitW = showLabel ? w + 24f : w + 16f;
        hit(hitX, y - 6f, hitW, h + 12f,
                this::toggleMasterSound);
    }

    private void toggleMasterSound() {
        boolean wasEnabled = audioControl.enabled();
        if (wasEnabled && preferenceBoolean("sonido_efectos", true)) {
            soundDisabledCue.play(0.72f);
        }
        boolean enabled = audioControl.toggle(surface != Surface.SETTINGS);
        if (enabled && preferenceBoolean("sonido_efectos", true)) {
            soundEnabledCue.play(0.72f);
        }
        syncMusicForSurface();
    }

    private static Music music(String path, float volume) {
        Music music = Gdx.audio.newMusic(Gdx.files.internal(path));
        music.setLooping(true);
        music.setVolume(volume);
        return music;
    }

    private boolean musicMasterEnabled() {
        return audioControl.enabled() && preferenceBoolean("musica", true);
    }

    private void syncMusicForSurface() {
        if (backgroundMusic == null || waitingRoomMusic == null) return;
        float volume = masterVolume();
        backgroundMusic.setVolume(0.40f * volume);
        waitingRoomMusic.setVolume(0.90f * volume);
        boolean lobbyMusic = surface == Surface.LOBBY
                || (surface == Surface.SETTINGS
                && settingsReturnSurface == Surface.LOBBY);
        boolean playBackground = !startupAudioHeld
                && musicMasterEnabled() && !lobbyMusic
                && preferenceBoolean("sonido_ascensor", true);
        boolean playWaitingRoom = musicMasterEnabled() && lobbyMusic
                && preferenceBoolean("musica_sala_espera", true);
        syncTrack(backgroundMusic, playBackground);
        syncTrack(waitingRoomMusic, playWaitingRoom);
    }

    private static void syncTrack(Music music, boolean play) {
        if (play) {
            if (!music.isPlaying()) music.play();
        } else if (music.isPlaying()) {
            music.pause();
        }
    }

    void pauseMusic() {
        if (backgroundMusic != null) backgroundMusic.pause();
        if (waitingRoomMusic != null) waitingRoomMusic.pause();
    }

    void resumeBackgroundMusicAt(float positionSeconds) {
        if (backgroundMusic == null) return;
        if (Float.isFinite(positionSeconds) && positionSeconds >= 0f) {
            backgroundMusic.setPosition(positionSeconds);
        }
        syncMusicForSurface();
    }

    private void openNewGame(NewGameConnectionDraft.Mode mode) {
        recoveryLoadGeneration++;
        autoSubmitRecovery = false;
        connection = defaultConnection(initialProperties, mode);
        refreshSelectedAvatarTexture();
        table = new NewGameTableDraft();
        refreshGamePresets(null);
        closePresetDialog();
        page = 0;
        clearActiveField();
        historyIndex = -1;
        surface = Surface.NEW_GAME;
    }

    private void drawHeader() {
        backButton(32f, 1006f, 64f, 54f, () -> {
            if (page > 0) {
                page--;
            } else {
                cancelOrReturnToMenu();
            }
        });
        text(smallFont, uppercase(gameText.translate("ui.menu_principal"))
                + "  /", 122f, 1041f, MUTED, false);
        text(smallFont, connection.mode() == NewGameConnectionDraft.Mode.JOIN
                ? gameText.translate("game.unirme_a_timba")
                : uppercase(gameText.translate("ui.nueva_timba")),
                315f, 1041f, GOLD, false);
        String title = connection.mode() == NewGameConnectionDraft.Mode.JOIN
                ? gameText.translate("game.unirme_a_timba")
                : uppercase(gameText.translate("ui.nueva_timba"));
        text(titleFont, title, 434f, 932f, new Color(0x000000aa), false);
        text(titleFont, title, 430f, 936f, GOLD, false);
        String guidance = connection.mode() == NewGameConnectionDraft.Mode.JOIN
                ? gameText.translate("gdx.newgame.guidance_join")
                : page == 0
                        ? gameText.translate("gdx.newgame.guidance_connection")
                        : gameText.translate("gdx.newgame.guidance_optional");
        textFit(smallFont, guidance, 434f, 850f,
                page == 0 ? MUTED : CYAN, false, 1380f);
    }

    private void drawProgress() {
        boolean joining = connection.mode() == NewGameConnectionDraft.Mode.JOIN;
        String[] names = joining
                ? new String[]{gameText.translate("game.unirme_a_timba")}
                : new String[]{uppercase(gameText.translate("gdx.connection")),
                    uppercase(gameText.translate("newgame.grupo_ciegas")),
                    uppercase(gameText.translate("newgame.grupo_compra")),
                    uppercase(gameText.translate("newgame.grupo_partida")),
                    uppercase(gameText.translate("newgame.grupo_bots"))};
        for (int i = 0; i < names.length; i++) {
            final int targetPage = i;
            float x = 35f;
            float y = 760f - i * 108f;
            boolean selected = i == page;
            outerBox(x, y, 320f, 78f, selected ? CYAN : LINE,
                    selected ? new Color(0x101a2ee0) : new Color(0x101a2ecc));
            if (selected) {
                shapes.setColor(GOLD);
                roundedRect(x + 5f, y + 15f, 4f, 48f, 2f);
            }
            drawNavIcon(joining ? 0 : i, x + 39f, y + 39f,
                    selected ? CYAN : DISABLED);
            textFit(actionFont, names[i], x + 74f, y + 49f,
                    selected ? Color.WHITE : MUTED, false, 230f);
            hit(x, y, 320f, 78f, () -> {
                clearActiveField();
                page = targetPage;
            });
        }
        keyHint(245f, 55f, "F11",
                uppercase(gameText.translate("settings.modo_pantalla_completa")));
    }

    private void drawIdentityPage() {
        panel(430f, 185f, 670f, 625f,
                uppercase(gameText.translate("gdx.newgame.your_profile")));
        panel(1130f, 185f, 725f, 625f,
                uppercase(gameText.translate("gdx.connection")));
        sectionTag(1644f, 754f, 176f,
                uppercase(gameText.translate("gdx.network_data")), CYAN);

        shapes.setColor(CYAN_DARK);
        shapes.circle(520f, 660f, 70f, 64);
        shapes.setColor(PANEL_LIGHT);
        shapes.circle(520f, 660f, 61f, 64);
        drawAvatarIcon(520f, 660f);
        hit(445f, 580f, 150f, 155f,
                this::selectAvatar);
        textFit(tinyFont, avatarSelectionPending
                ? uppercase(gameText.translate("gdx.opening"))
                : uppercase(gameText.translate("gdx.change")),
                520f, 570f, avatarSelectionPending ? DISABLED : CYAN,
                true, 145f);
        if (connection.avatar() != null) {
            button(455f, 515f, 130f, 42f,
                    uppercase(gameText.translate("gdx.remove")), false,
                    this::resetAvatar, !avatarSelectionPending);
        }

        field(610f, 630f, 450f, gameText.translate("gdx.newgame.nick_required"),
                connection.nickname(), "nick", false);
        field(610f, 475f, 450f, gameText.translate("gdx.newgame.password_optional"),
                connection.password(), "password", true);
        if (connection.mode() != NewGameConnectionDraft.Mode.JOIN) {
            toggle(610f, 325f, 450f,
                    uppercase(gameText.translate(
                            "gdx.newgame.recover_previous")),
                    connection.recoverRequested(), this::toggleRecover, true);
        }

        field(1170f, 630f, 430f,
                gameText.translate("gdx.newgame.server_required"),
                connection.server(), "server", false);
        field(1630f, 630f, 175f, gameText.translate("gdx.port"),
                connection.port(), "port", false);
        if (connection.mode() == NewGameConnectionDraft.Mode.JOIN) {
            String history = connection.serverHistory().isEmpty()
                    ? "Sin servidores anteriores"
                    : connection.serverHistory().get(historyIndex < 0
                            ? connection.serverHistory().size() - 1 : historyIndex);
            bidirectionalChoice(1170f, 475f, 635f,
                    gameText.translate("gdx.newgame.previous_servers"), history,
                    this::previousServerHistory, this::nextServerHistory,
                    !connection.serverHistory().isEmpty());
        } else {
            toggle(1170f, 475f, 635f, "UPnP", connection.upnp(),
                    () -> connection.setUpnp(!connection.upnp()), true);
            outerBox(1150f, 198f, 675f, 244f, new Color(0x31445fbb),
                    new Color(0x07132188));
            textFit(smallFont, uppercase(gameText.translate(
                    "gdx.newgame.profile_title")), 1174f, 416f, GOLD,
                    false, 620f);
            textFit(tinyFont, gameText.translate("gdx.newgame.profile_help"),
                    1174f, 386f, MUTED, false, 620f);
            boolean profileEditable = !connection.recoverRequested();
            bidirectionalChoice(1170f, 275f, 635f, "",
                    selectedPresetLabel(), this::previousGamePreset,
                    this::nextGamePreset, profileEditable);
            button(1170f, 202f, 300f, 58f,
                    gameText.translate("newgame.preset_guardar"), false,
                    this::openPresetNameDialog, profileEditable);
            button(1500f, 202f, 305f, 58f,
                    gameText.translate("newgame.preset_borrar"), false,
                    this::requestDeletePreset,
                    profileEditable && selectedGamePreset >= 0);
        }
    }

    private String selectedPresetLabel() {
        return selectedGamePreset >= 0 && selectedGamePreset < gamePresets.size()
                ? gamePresets.get(selectedGamePreset).name()
                : gameText.translate("newgame.preset_por_defecto");
    }

    private void refreshGamePresets(String selectName) {
        gamePresets = List.copyOf(GamePresetCatalog.readFrom(
                initialProperties).values());
        selectedGamePreset = -1;
        if (selectName != null) {
            for (int index = 0; index < gamePresets.size(); index++) {
                if (gamePresets.get(index).name().equals(selectName)) {
                    selectedGamePreset = index;
                    break;
                }
            }
        }
    }

    private void nextGamePreset() {
        selectGamePreset(1);
    }

    private void previousGamePreset() {
        selectGamePreset(-1);
    }

    private void selectGamePreset(int direction) {
        if (connection.recoverRequested()) return;
        selectedGamePreset = adjacentPresetIndex(selectedGamePreset,
                gamePresets.size(), direction);
        if (selectedGamePreset < 0) {
            table = new NewGameTableDraft();
            showToast("Perfil por defecto cargado");
            return;
        }
        GamePresetCatalog.Entry preset = gamePresets.get(selectedGamePreset);
        try {
            table = NewGameTableDraft.from(
                    NewGameTableDraft.Settings.parseWire(preset.settings()));
            showToast(gameText.translate("gdx.newgame.profile_loaded",
                    preset.name()));
        } catch (IllegalArgumentException invalid) {
            showToast("El perfil está dañado y no se puede cargar");
        }
    }

    static int adjacentPresetIndex(int selectedIndex, int presetCount,
            int direction) {
        int optionCount = Math.max(0, presetCount) + 1;
        int currentOption = selectedIndex >= 0 && selectedIndex < presetCount
                ? selectedIndex + 1 : 0;
        int step = direction < 0 ? -1 : 1;
        return Math.floorMod(currentOption + step, optionCount) - 1;
    }

    static int adjacentIndex(int selectedIndex, int optionCount,
            int direction) {
        if (optionCount <= 0) return -1;
        int current = Math.max(0, Math.min(selectedIndex, optionCount - 1));
        return Math.floorMod(current + (direction < 0 ? -1 : 1),
                optionCount);
    }

    private void openPresetNameDialog() {
        if (connection.recoverRequested()) return;
        presetNameDraft = "";
        presetDialog = PresetDialog.NAME;
        activateField("presetName");
    }

    private void requestDeletePreset() {
        if (selectedGamePreset < 0 || selectedGamePreset >= gamePresets.size()) {
            return;
        }
        clearActiveField();
        presetDialog = PresetDialog.DELETE;
    }

    private void submitPresetName() {
        final String name;
        try {
            name = GamePresetCatalog.normalizeName(presetNameDraft);
        } catch (IllegalArgumentException invalid) {
            showToast("Escribe un nombre para el perfil");
            return;
        }
        LinkedHashMap<String, GamePresetCatalog.Entry> all =
                GamePresetCatalog.readFrom(initialProperties);
        if (all.containsKey(name)) {
            presetNameDraft = name;
            clearActiveField();
            presetDialog = PresetDialog.OVERWRITE;
            return;
        }
        if (all.size() >= GamePresetCatalog.MAX_PRESETS) {
            showToast("No se pueden guardar más de "
                    + GamePresetCatalog.MAX_PRESETS + " perfiles");
            return;
        }
        saveCurrentPreset(name, all);
    }

    private void saveCurrentPreset(String name,
            LinkedHashMap<String, GamePresetCatalog.Entry> all) {
        all.put(name, new GamePresetCatalog.Entry(name,
                table.snapshot().serializeForWire()));
        GamePresetCatalog.writeTo(initialProperties, all.values());
        preferences.saveDeferred();
        refreshGamePresets(name);
        closePresetDialog();
        showToast(gameText.translate("gdx.newgame.profile_saved", name));
    }

    private void confirmOverwritePreset() {
        LinkedHashMap<String, GamePresetCatalog.Entry> all =
                GamePresetCatalog.readFrom(initialProperties);
        saveCurrentPreset(presetNameDraft, all);
    }

    private void confirmDeletePreset() {
        if (selectedGamePreset < 0 || selectedGamePreset >= gamePresets.size()) {
            closePresetDialog();
            return;
        }
        String name = gamePresets.get(selectedGamePreset).name();
        LinkedHashMap<String, GamePresetCatalog.Entry> all =
                GamePresetCatalog.readFrom(initialProperties);
        all.remove(name);
        GamePresetCatalog.writeTo(initialProperties, all.values());
        preferences.saveDeferred();
        refreshGamePresets(null);
        table = new NewGameTableDraft();
        closePresetDialog();
        showToast(gameText.translate("gdx.newgame.profile_deleted", name));
    }

    private void closePresetDialog() {
        presetDialog = PresetDialog.NONE;
        presetNameDraft = "";
        clearActiveField();
    }

    private void drawPresetDialog() {
        hits.clear();
        textFieldHits.clear();
        editMenuHits.clear();
        shapes.setColor(new Color(0x02050cdd));
        shapes.rect(0f, 0f, WIDTH, HEIGHT);
        outerBox(560f, 350f, 800f, 360f, CYAN_DARK,
                new Color(0x071321ff));
        shapes.setColor(new Color(0x36d9ffb8));
        shapes.rect(590f, 683f, 740f, 3f);
        if (presetDialog == PresetDialog.NAME) {
            textFit(headingFont, uppercase(gameText.translate(
                    "gdx.newgame.save_profile")), 960f, 635f, GOLD,
                    true, 700f);
            field(660f, 475f, 600f,
                    gameText.translate("gdx.newgame.profile_name"), presetNameDraft,
                    "presetName", false);
            themedButton(660f, 385f, 270f, 64f,
                    gameText.translate("ui.cancelar"),
                    ButtonTone.NEUTRAL, this::closePresetDialog, true);
            themedButton(990f, 385f, 270f, 64f,
                    gameText.translate("ui.guardar"),
                    ButtonTone.POSITIVE, this::submitPresetName,
                    !presetNameDraft.isBlank());
            return;
        }
        String name = presetDialog == PresetDialog.DELETE
                ? selectedPresetLabel() : presetNameDraft;
        textFit(headingFont,
                presetDialog == PresetDialog.DELETE
                        ? uppercase(gameText.translate(
                                "gdx.newgame.delete_profile"))
                        : uppercase(gameText.translate(
                                "gdx.newgame.overwrite_profile")),
                960f, 625f, GOLD, true, 700f);
        textFit(actionFont,
                presetDialog == PresetDialog.DELETE
                        ? uppercase(gameText.translate(
                                "gdx.newgame.delete_profile_question", name))
                        : uppercase(gameText.translate(
                                "gdx.newgame.overwrite_profile_question", name)),
                960f, 535f, Color.WHITE, true, 700f);
        themedButton(660f, 405f, 270f, 64f,
                gameText.translate("ui.cancelar"),
                ButtonTone.NEUTRAL, this::closePresetDialog, true);
        themedButton(990f, 405f, 270f, 64f,
                presetDialog == PresetDialog.DELETE
                        ? gameText.translate("newgame.preset_borrar")
                        : gameText.translate("gdx.newgame.overwrite_profile"),
                presetDialog == PresetDialog.DELETE
                        ? ButtonTone.DANGER : ButtonTone.POSITIVE,
                presetDialog == PresetDialog.DELETE
                        ? this::confirmDeletePreset : this::confirmOverwritePreset,
                true);
    }

    private void nextServerHistory() {
        selectServerHistory(1);
    }

    private void previousServerHistory() {
        selectServerHistory(-1);
    }

    private void selectServerHistory(int direction) {
        List<String> history = connection.serverHistory();
        if (history.isEmpty()) {
            return;
        }
        int current = historyIndex < 0 ? history.size() - 1 : historyIndex;
        historyIndex = adjacentIndex(current, history.size(), direction);
        String endpoint = history.get(historyIndex);
        int separator = endpoint.lastIndexOf(':');
        if (separator > 0 && separator < endpoint.length() - 1) {
            connection.setServer(endpoint.substring(0, separator));
            connection.setPort(endpoint.substring(separator + 1));
        }
    }

    private void toggleRecover() {
        if (connection.recoverRequested()) {
            recoveryLoadGeneration++;
            autoSubmitRecovery = false;
            connection.setRecoverRequested(false);
            table.setEconomyLocked(false);
            return;
        }
        connection.setRecoverRequested(true);
        table.setEconomyLocked(true);
        startRecoverLoad();
    }

    private void startRecoverLoad() {
        if (!connection.recoverRequested()) return;
        if (!connection.beginRecoverLoad()) return;
        long generation = ++recoveryLoadGeneration;
        showToast("Cargando la última timba recuperable…");
        CompletableFuture.supplyAsync(() -> {
            try {
                return recoverableGames.latestLocal();
            } catch (Exception failure) {
                throw new CompletionException(failure);
            }
        }, recoveryExecutor).whenComplete((recovered, failure) ->
                Gdx.app.postRunnable(() -> completeRecoverLoad(
                        generation, recovered, failure)));
    }

    private void completeRecoverLoad(long generation,
            java.util.Optional<RecoverableGameRepository.RecoverableGame> recovered,
            Throwable failure) {
        if (disposed || generation != recoveryLoadGeneration
                || !connection.recoverRequested()) return;
        Throwable cause = unwrap(failure);
        if (cause != null || recovered == null || recovered.isEmpty()) {
            autoSubmitRecovery = false;
            connection.failRecoverLoad();
            connection.setRecoverRequested(false);
            table.setEconomyLocked(false);
            showToast(cause == null ? "No hay ninguna timba recuperable"
                    : "No se pudo cargar la timba anterior: "
                            + submissionError(cause));
            return;
        }
        RecoverableGameRepository.RecoverableGame game = recovered.orElseThrow();
        // These two host-global rules live beside, rather than inside, the
        // immutable poker configuration. Restore them before the table factory
        // snapshots its authoritative initial communication state.
        initialProperties.setProperty("voice_messages",
                Boolean.toString(game.voiceMessages()));
        initialProperties.setProperty("tts_server",
                Boolean.toString(game.textToSpeech()));
        if (preferences != null) preferences.saveDeferred();
        table = NewGameTableDraft.from(game.settings());
        table.setEconomyLocked(true);
        connection.completeRecoverLoad(game.id());
        showToast("Timba anterior cargada · " + recoveredDescription(game));
        if (autoSubmitRecovery) {
            autoSubmitRecovery = false;
            submitNewGame();
        }
    }

    private static String recoveredDescription(
            RecoverableGameRepository.RecoverableGame game) {
        String server = game.server();
        return server.isBlank() ? "partida " + game.id() : server;
    }

    private void drawBlindsPage() {
        panel(430f, 185f, 670f, 625f,
                settingsGameText("blinds"));
        panel(1130f, 185f, 725f, 625f,
                settingsGameText("row.increase_blinds"));

        bidirectionalChoice(470f, 610f, 590f,
                gameText.translate("gdx.settings.game.row.blind_structure"),
                table.structureName() == null
                        ? gameText.translate("gdx.settings.value.default")
                        : table.structureName(),
                this::previousBlindStructure, this::nextBlindStructure,
                !table.economyLocked());
        bidirectionalChoice(470f, 460f, 590f,
                gameText.translate("gdx.settings.game.row.initial_blinds"),
                formatBlindLevel(), this::previousBlindLevel,
                this::nextBlindLevel, !table.economyLocked());
        button(470f, 320f, 285f, 70f,
                settingsGameText("row.default_structure"), false,
                this::selectDefaultBlindStructure, !table.economyLocked());
        button(775f, 320f, 285f, 70f,
                settingsGameText("row.manage"), false,
                this::openBlindStructureEditor, !table.economyLocked());
        toggle(470f, 205f, 280f, settingsGameText("row.ante"), table.ante(),
                () -> table.setAnte(!table.ante()), !table.economyLocked());
        toggle(780f, 205f, 280f, settingsGameText("row.straddle"),
                table.straddle(),
                () -> table.setStraddle(!table.straddle()),
                !table.economyLocked());

        toggle(1170f, 610f, 645f,
                gameText.translate("gdx.settings.game.row.increase_blinds"),
                table.increaseBlinds(),
                () -> table.setIncreaseBlinds(!table.increaseBlinds()), !table.economyLocked());
        bidirectionalChoice(1170f, 460f, 305f, "",
                table.blindIncreaseType() == NewGameTableDraft.BlindIncreaseType.MINUTES
                        ? gameText.translate("gdx.settings.game.value.minutes")
                        : gameText.translate("gdx.settings.game.value.hands"),
                () -> table.setBlindIncreaseType(
                        table.blindIncreaseType() == NewGameTableDraft.BlindIncreaseType.MINUTES
                                ? NewGameTableDraft.BlindIncreaseType.HANDS
                                : NewGameTableDraft.BlindIncreaseType.MINUTES),
                () -> table.setBlindIncreaseType(
                        table.blindIncreaseType() == NewGameTableDraft.BlindIncreaseType.MINUTES
                                ? NewGameTableDraft.BlindIncreaseType.HANDS
                                : NewGameTableDraft.BlindIncreaseType.MINUTES),
                table.increaseBlinds() && !table.economyLocked());
        stepper(1510f, 460f, 305f, "", table.blindInterval(), 1, Integer.MAX_VALUE,
                () -> table.setBlindInterval(table.blindInterval() - 1),
                () -> table.setBlindInterval(table.blindInterval() + 1),
                table.increaseBlinds() && !table.economyLocked());
        toggle(1170f, 325f, 645f, "Tope de aumentos", table.blindCap(),
                () -> table.setBlindCap(!table.blindCap()),
                table.blindCapControlEnabled() && !table.economyLocked());
        stepper(1170f, 205f, 645f, "", table.blindCapRaises(),
                1, table.maxBlindCapRaises(),
                () -> table.setBlindCapRaises(table.blindCapRaises() - 1),
                () -> table.setBlindCapRaises(table.blindCapRaises() + 1),
                table.blindCapRaisesEnabled() && !table.economyLocked());
    }

    private String formatBlindLevel() {
        NewGameTableDraft.BlindLevel level = table.blindLevel();
        return formatBlind(level.smallBlind()) + " / " + formatBlind(level.bigBlind());
    }

    private static String formatBlind(double value) {
        if (value < 1d) {
            return String.format(Locale.ROOT, "%.2f", value);
        }
        return value == Math.rint(value)
                ? Long.toString((long) value)
                : Double.toString(value);
    }

    private void nextBlindLevel() {
        selectBlindLevel(1);
    }

    private void previousBlindLevel() {
        selectBlindLevel(-1);
    }

    private void selectBlindLevel(int direction) {
        table.setBlindLevelIndex(adjacentIndex(table.blindLevelIndex(),
                table.blindLevels().size(), direction));
    }

    private void nextBlindStructure() {
        selectBlindStructure(1);
    }

    private void previousBlindStructure() {
        selectBlindStructure(-1);
    }

    private void selectBlindStructure(int direction) {
        List<BlindStructureCatalog.Entry> saved = BlindStructureCatalog.read(
                initialProperties);
        int selectedOption = 0;
        if (table.structureName() != null) {
            for (int index = 0; index < saved.size(); index++) {
                if (saved.get(index).name().equals(table.structureName())) {
                    selectedOption = index + 1;
                    break;
                }
            }
        }
        int nextOption = adjacentIndex(selectedOption, saved.size() + 1,
                direction);
        if (nextOption == 0) {
            selectDefaultBlindStructure();
            return;
        }
        BlindStructureCatalog.Entry entry = saved.get(nextOption - 1);
        table.setBlindStructure(entry.name(), entry.levels().stream()
                .map(level -> new NewGameTableDraft.BlindLevel(
                        level.smallBlind(), level.bigBlind()))
                .toList(), table.blindLevelIndex());
    }

    private void selectDefaultBlindStructure() {
        table.setBlindStructure(null,
                Arrays.stream(BlindStructureRules.defaultLevels())
                        .map(level -> new NewGameTableDraft.BlindLevel(
                                level[0], level[1]))
                        .toList(), table.blindLevelIndex());
    }

    private void openBlindStructureEditor() {
        if (table.economyLocked()) return;
        blindStructureSettingsTarget = false;
        blindStructureEditor.begin(initialProperties, table.structureName());
        blindStructureDialog = BlindStructureDialog.EDITOR;
        blindStructureNameDraft = "";
        clearActiveField();
    }

    private void openSettingsBlindStructureEditor() {
        if (settingsTable == null || settingsTable.economyLocked()
                || lobby == null || !lobby.host()) return;
        blindStructureSettingsTarget = true;
        blindStructureEditor.begin(initialProperties,
                settingsTable.structureName());
        blindStructureDialog = BlindStructureDialog.EDITOR;
        blindStructureNameDraft = "";
        clearActiveField();
    }

    private void closeBlindStructureEditor() {
        blindStructureDialog = BlindStructureDialog.NONE;
        blindStructureNameDraft = "";
        clearActiveField();
    }

    private void saveBlindStructures() {
        try {
            blindStructureEditor.save(initialProperties);
        } catch (IllegalArgumentException invalid) {
            showToast("Revisa los nombres y niveles de las estructuras");
            return;
        }
        BlindStructureCatalog.Entry selected = blindStructureEditor.selected();
        NewGameTableDraft target = blindStructureSettingsTarget
                ? settingsTable : table;
        if (target == null) {
            closeBlindStructureEditor();
            return;
        }
        if (selected == null) {
            target.setBlindStructure(null,
                    Arrays.stream(BlindStructureRules.defaultLevels())
                            .map(level -> new NewGameTableDraft.BlindLevel(
                                    level[0], level[1]))
                            .toList(), target.blindLevelIndex());
        } else {
            target.setBlindStructure(selected.name(), selected.levels().stream()
                    .map(level -> new NewGameTableDraft.BlindLevel(
                            level.smallBlind(), level.bigBlind()))
                    .toList(), target.blindLevelIndex());
        }
        if (!blindStructureSettingsTarget && preferences != null) {
            preferences.saveDeferred();
        }
        closeBlindStructureEditor();
        showToast("Estructuras de ciegas guardadas");
    }

    private void openBlindStructureNameDialog(
            BlindStructureDialog action) {
        if (action == BlindStructureDialog.NAME_RENAME
                && blindStructureEditor.selected() != null) {
            blindStructureNameDraft =
                    blindStructureEditor.selected().name();
        } else {
            blindStructureNameDraft = "";
        }
        blindStructureDialog = action;
        activateField("blindStructureName");
    }

    private void submitBlindStructureName() {
        boolean accepted = switch (blindStructureDialog) {
            case NAME_NEW -> blindStructureEditor.create(
                    blindStructureNameDraft);
            case NAME_DUPLICATE -> blindStructureEditor.duplicate(
                    blindStructureNameDraft);
            case NAME_RENAME -> blindStructureEditor.rename(
                    blindStructureNameDraft);
            default -> false;
        };
        if (!accepted) {
            showToast("Nombre vacío, repetido o no válido");
            return;
        }
        blindStructureDialog = BlindStructureDialog.EDITOR;
        blindStructureNameDraft = "";
        clearActiveField();
    }

    private void drawBlindStructureDialog() {
        hits.clear();
        textFieldHits.clear();
        editMenuHits.clear();
        shapes.setColor(new Color(0x02050ce8));
        shapes.rect(0f, 0f, WIDTH, HEIGHT);
        if (blindStructureDialog == BlindStructureDialog.DELETE) {
            drawBlindStructureDeleteConfirmation();
            return;
        }
        if (blindStructureDialog == BlindStructureDialog.NAME_NEW
                || blindStructureDialog == BlindStructureDialog.NAME_DUPLICATE
                || blindStructureDialog == BlindStructureDialog.NAME_RENAME) {
            drawBlindStructureNameDialog();
            return;
        }

        outerBox(220f, 105f, 1480f, 870f, CYAN_DARK,
                new Color(0x071321ff));
        shapes.setColor(new Color(0x36d9ffb8));
        shapes.rect(250f, 944f, 1420f, 3f);
        textFit(headingFont, "ESTRUCTURAS DE CIEGAS", 275f, 905f,
                GOLD, false, 1100f);
        textFit(smallFont,
                "Crea y ajusta las escaleras disponibles para esta timba",
                275f, 858f, MUTED, false, 1100f);

        BlindStructureCatalog.Entry selected = blindStructureEditor.selected();
        boolean hasSelection = selected != null;
        String structureLabel = hasSelection ? selected.name()
                : "No hay estructuras personalizadas";
        bidirectionalChoice(275f, 710f, 580f, "Estructura:",
                structureLabel, () -> blindStructureEditor.selectStructure(-1),
                () -> blindStructureEditor.selectStructure(1),
                !blindStructureEditor.entries().isEmpty());
        button(275f, 610f, 180f, 62f, "NUEVA", false,
                () -> openBlindStructureNameDialog(
                        BlindStructureDialog.NAME_NEW), true);
        button(475f, 610f, 180f, 62f, "DUPLICAR", false,
                () -> openBlindStructureNameDialog(
                        BlindStructureDialog.NAME_DUPLICATE), hasSelection);
        button(675f, 610f, 180f, 62f, "RENOMBRAR", false,
                () -> openBlindStructureNameDialog(
                        BlindStructureDialog.NAME_RENAME), hasSelection);
        button(275f, 515f, 580f, 62f, "BORRAR ESTRUCTURA", false,
                () -> {
                    blindStructureDialog = BlindStructureDialog.DELETE;
                    clearActiveField();
                }, hasSelection);

        if (hasSelection) {
            int levelIndex = blindStructureEditor.selectedLevelIndex();
            BlindStructureCatalog.BlindLevel level =
                    blindStructureEditor.selectedLevel();
            bidirectionalChoice(930f, 710f, 690f, "Nivel:",
                    (levelIndex + 1) + " de " + selected.levels().size(),
                    () -> blindStructureEditor.selectLevel(-1),
                    () -> blindStructureEditor.selectLevel(1), true);
            blindAmountStepper(930f, 565f, 330f, "Ciega pequeña:",
                    level.smallBlind(),
                    () -> adjustEditorBlind(true, -1),
                    () -> adjustEditorBlind(true, 1));
            blindAmountStepper(1290f, 565f, 330f, "Ciega grande:",
                    level.bigBlind(),
                    () -> adjustEditorBlind(false, -1),
                    () -> adjustEditorBlind(false, 1));
            button(930f, 455f, 330f, 62f, "AÑADIR NIVEL", false,
                    () -> {
                        if (!blindStructureEditor.addLevel()) {
                            showToast("No se puede añadir otro nivel");
                        }
                    }, selected.levels().size()
                            < BlindStructureRules.MAX_LEVELS);
            button(1290f, 455f, 330f, 62f, "QUITAR NIVEL", false,
                    () -> {
                        if (!blindStructureEditor.removeSelectedLevel()) {
                            showToast("La estructura necesita al menos un nivel");
                        }
                    }, selected.levels().size() > 1);
            textFit(tinyFont,
                    "Los cambios que rompan el orden de la escalera se rechazan",
                    1275f, 405f, MUTED, true, 690f);
        } else {
            textFit(actionFont, "CREA LA PRIMERA ESTRUCTURA",
                    1275f, 650f, MUTED, true, 650f);
        }

        button(275f, 155f, 300f, 66f, "CANCELAR", false,
                this::closeBlindStructureEditor, true);
        themedButton(1320f, 155f, 300f, 66f, "GUARDAR",
                ButtonTone.POSITIVE, this::saveBlindStructures,
                blindStructureEditor.dirty());
    }

    private void adjustEditorBlind(boolean small, int direction) {
        BlindStructureCatalog.BlindLevel level =
                blindStructureEditor.selectedLevel();
        if (level == null) return;
        double value = small ? level.smallBlind() : level.bigBlind();
        int stepCount = blindEditorStepCount(value) * direction;
        boolean accepted = small
                ? blindStructureEditor.adjustSmallBlind(stepCount)
                : blindStructureEditor.adjustBigBlind(stepCount);
        if (!accepted) {
            showToast("Ese valor rompe el orden de la estructura");
        }
    }

    static int blindEditorStepCount(double value) {
        if (value < 1d) return 1;
        if (value < 10d) return 2;
        if (value < 100d) return 20;
        if (value < 1_000d) return 200;
        if (value < 10_000d) return 2_000;
        if (value < 100_000d) return 20_000;
        return 200_000;
    }

    private void blindAmountStepper(float x, float y, float width,
            String label, double value, Runnable minus, Runnable plus) {
        textFit(smallFont, label, x, y + 99f, MUTED, false, width);
        outerBox(x, y, width, 72f,
                hovered(x, y, width, 72f) ? CYAN : LINE, PANEL_LIGHT);
        float side = 70f;
        shapes.setColor(new Color(0x20324cff));
        roundedRect(x + 3f, y + 3f, side, 66f, 10f);
        roundedRect(x + width - side - 3f, y + 3f, side, 66f, 10f);
        text(headingFont, "-", x + 38f, y + 48f, Color.WHITE, true);
        text(headingFont, "+", x + width - 38f, y + 48f,
                Color.WHITE, true);
        textFit(headingFont, formatBlind(value), x + width / 2f,
                y + 49f, GOLD, true, width - side * 2f - 24f);
        hit(x, y, side + 6f, 72f, minus);
        hit(x + width - side - 6f, y, side + 6f, 72f, plus);
    }

    private void drawBlindStructureNameDialog() {
        outerBox(560f, 350f, 800f, 360f, CYAN_DARK,
                new Color(0x071321ff));
        shapes.setColor(new Color(0x36d9ffb8));
        shapes.rect(590f, 683f, 740f, 3f);
        String title = switch (blindStructureDialog) {
            case NAME_NEW -> "NUEVA ESTRUCTURA";
            case NAME_DUPLICATE -> "DUPLICAR ESTRUCTURA";
            case NAME_RENAME -> "RENOMBRAR ESTRUCTURA";
            default -> "ESTRUCTURA DE CIEGAS";
        };
        textFit(headingFont, title, 960f, 635f, GOLD, true, 700f);
        field(660f, 475f, 600f, "Nombre:", blindStructureNameDraft,
                "blindStructureName", false);
        themedButton(660f, 385f, 270f, 64f, "VOLVER",
                ButtonTone.NEUTRAL, () -> {
                    blindStructureDialog = BlindStructureDialog.EDITOR;
                    clearActiveField();
                }, true);
        themedButton(990f, 385f, 270f, 64f, "ACEPTAR",
                ButtonTone.POSITIVE, this::submitBlindStructureName,
                !blindStructureNameDraft.isBlank());
    }

    private void drawBlindStructureDeleteConfirmation() {
        outerBox(560f, 350f, 800f, 360f, CYAN_DARK,
                new Color(0x071321ff));
        shapes.setColor(new Color(0x36d9ffb8));
        shapes.rect(590f, 683f, 740f, 3f);
        textFit(headingFont, "BORRAR ESTRUCTURA", 960f, 625f,
                GOLD, true, 700f);
        String name = blindStructureEditor.selected() == null ? ""
                : blindStructureEditor.selected().name();
        textFit(actionFont, "BORRAR '" + name + "'?", 960f, 535f,
                Color.WHITE, true, 700f);
        themedButton(660f, 405f, 270f, 64f, "VOLVER",
                ButtonTone.NEUTRAL,
                () -> blindStructureDialog = BlindStructureDialog.EDITOR,
                true);
        themedButton(990f, 405f, 270f, 64f, "BORRAR",
                ButtonTone.DANGER, () -> {
                    blindStructureEditor.deleteSelected();
                    blindStructureDialog = BlindStructureDialog.EDITOR;
                }, true);
    }

    private void drawPurchasePage() {
        panel(430f, 185f, 670f, 625f, settingsGameText("buyin"));
        panel(1130f, 185f, 725f, 625f, settingsGameText("rebuy"));

        toggle(470f, 625f, 590f, "Buy-in fijo", table.fixedBuyin(),
                () -> table.setFixedBuyin(!table.fixedBuyin()), !table.economyLocked());
        stepper(470f, 480f, 590f,
                gameText.translate("gdx.settings.game.row.initial_buyin"),
                table.buyin(),
                table.minimumBuyin(), table.maximumBuyin(),
                () -> table.setBuyin(table.buyin() - 1),
                () -> table.setBuyin(table.buyin() + 1),
                table.fixedBuyin() && !table.economyLocked());
        stepper(470f, 315f, 280f,
                gameText.translate("gdx.settings.game.row.buyin_range"),
                table.minBuyinBb(), 10, 500,
                () -> table.setMinBuyinBb(table.minBuyinBb() - 5),
                () -> table.setMinBuyinBb(table.minBuyinBb() + 5),
                !table.economyLocked());
        stepper(780f, 315f, 280f, "→", table.maxBuyinBb(), 10, 500,
                () -> table.setMaxBuyinBb(table.maxBuyinBb() - 5),
                () -> table.setMaxBuyinBb(table.maxBuyinBb() + 5),
                !table.economyLocked());

        toggle(1170f, 625f, 645f, "Recomprar", table.rebuy(),
                () -> table.setRebuy(!table.rebuy()), true);
        toggle(1170f, 505f, 645f, "Límite recompra por jugador", table.rebuyLimit(),
                () -> table.setRebuyLimit(!table.rebuyLimit()), table.rebuyLimitEnabled());
        stepper(1170f, 365f, 645f, "", table.rebuyLimitCount(), 1, Integer.MAX_VALUE,
                () -> table.setRebuyLimitCount(table.rebuyLimitCount() - 1),
                () -> table.setRebuyLimitCount(table.rebuyLimitCount() + 1),
                table.rebuyLimitCountEnabled());
        bidirectionalChoice(1170f, 225f, 645f,
                gameText.translate("gdx.settings.game.row.rebuy_cap"),
                table.rebuyCapPolicy() == NewGameTableDraft.RebuyCapPolicy.BUY_IN
                        ? "BUY-IN"
                        : gameText.translate(
                                "gdx.settings.game.value.highest_stack"),
                () -> table.setRebuyCapPolicy(
                        table.rebuyCapPolicy() == NewGameTableDraft.RebuyCapPolicy.BUY_IN
                                ? NewGameTableDraft.RebuyCapPolicy.HIGHEST_STACK
                                : NewGameTableDraft.RebuyCapPolicy.BUY_IN),
                () -> table.setRebuyCapPolicy(
                        table.rebuyCapPolicy() == NewGameTableDraft.RebuyCapPolicy.BUY_IN
                                ? NewGameTableDraft.RebuyCapPolicy.HIGHEST_STACK
                                : NewGameTableDraft.RebuyCapPolicy.BUY_IN),
                table.rebuy());
    }

    private void drawGamePage() {
        panel(430f, 185f, 670f, 625f, settingsGameText("game"));
        panel(1130f, 185f, 725f, 625f, settingsGameText("rules"));

        toggle(470f, 625f, 280f,
                gameText.translate("gdx.settings.game.row.hand_limit"),
                table.handLimit(),
                () -> table.setHandLimit(!table.handLimit()), true);
        stepper(780f, 625f, 280f, "", table.handLimitCount(), 1, Integer.MAX_VALUE,
                () -> table.setHandLimitCount(table.handLimitCount() - 1),
                () -> table.setHandLimitCount(table.handLimitCount() + 1),
                table.handLimit());
        toggle(470f, 495f, 280f,
                gameText.translate("gdx.settings.game.row.think_time"),
                table.thinkTime(),
                () -> table.setThinkTime(!table.thinkTime()), true);
        stepper(780f, 495f, 280f, "", table.thinkSeconds(), 10, 120,
                () -> table.setThinkSeconds(table.thinkSeconds() - 5),
                () -> table.setThinkSeconds(table.thinkSeconds() + 5), table.thinkTime());
        stepper(470f, 325f, 590f,
                gameText.translate("gdx.settings.game.row.showdown_seconds"),
                table.showdownSeconds(), 5, 30,
                () -> table.setShowdownSeconds(table.showdownSeconds() - 5),
                () -> table.setShowdownSeconds(table.showdownSeconds() + 5));

        toggle(1170f, 625f, 645f, "IWTSTH", table.iwtsth(),
                () -> table.setIwtsth(!table.iwtsth()), true);
        toggle(1170f, 495f, 645f, "RUN IT TWICE", table.runItTwice(),
                () -> table.setRunItTwice(!table.runItTwice()), true);
        bidirectionalChoice(1170f, 325f, 645f,
                settingsGameText("row.rabbit_hunting"),
                rabbitText(), this::previousRabbit, this::nextRabbit, true);
    }

    private String rabbitText() {
        return switch (table.rabbitHunting()) {
            case FREE -> "Gratis";
            case FREE_SMALL_BLIND -> "Gratis + SB";
            case FREE_SMALL_AND_BIG_BLIND -> "Gratis + SB + BB";
            case OFF -> "Desactivado";
        };
    }

    private void nextRabbit() {
        selectRabbit(1);
    }

    private void previousRabbit() {
        selectRabbit(-1);
    }

    private void selectRabbit(int direction) {
        NewGameTableDraft.RabbitHunting[] values = NewGameTableDraft.RabbitHunting.values();
        table.setRabbitHunting(values[adjacentIndex(
                table.rabbitHunting().ordinal(), values.length, direction)]);
    }

    private void drawBotsPage() {
        panel(520f, 185f, 1230f, 625f, settingsGameText("bots"));
        bidirectionalChoice(580f, 610f, 1110f,
                gameText.translate("gdx.settings.game.row.bot_difficulty"),
                table.botDifficulty() == NewGameTableDraft.BotDifficulty.EASY
                        ? gameText.translate("gdx.settings.game.value.easy")
                        : table.botDifficulty() == NewGameTableDraft.BotDifficulty.HARD
                                ? gameText.translate(
                                        "gdx.settings.game.value.hard")
                                : gameText.translate(
                                        "gdx.settings.game.value.medium"),
                this::previousBotDifficulty, this::nextBotDifficulty, true);
        toggle(580f, 455f, 1110f,
                gameText.translate("gdx.settings.game.row.bot_rebuy"),
                table.botRebuy(),
                () -> table.setBotRebuy(!table.botRebuy()), table.botRebuyEnabled());
        toggle(580f, 325f, 1110f,
                gameText.translate("gdx.settings.game.row.bot_balance"),
                table.botBalanceToHumans(),
                () -> table.setBotBalanceToHumans(!table.botBalanceToHumans()), true);
    }

    private void nextBotDifficulty() {
        selectBotDifficulty(1);
    }

    private void previousBotDifficulty() {
        selectBotDifficulty(-1);
    }

    private void selectBotDifficulty(int direction) {
        NewGameTableDraft.BotDifficulty[] values = NewGameTableDraft.BotDifficulty.values();
        table.setBotDifficulty(values[adjacentIndex(
                table.botDifficulty().ordinal(), values.length, direction)]);
    }

    private void drawFooter() {
        boolean submitting = submissions.submitting();
        if (submitting) {
            // The immutable request is already in flight. Keep only Cancel
            // interactive so the visible form cannot drift from that request.
            hits.clear();
            clearActiveField();
        }
        keyHint(58f, 55f, "ESC", gameText.translate("ui.cerrar"));
        button(1165f, 31f, 250f, 70f,
                gameText.translate("ui.cancelar"), false,
                this::cancelOrReturnToMenu);
        button(1445f, 31f, 410f, 70f,
                submitting ? uppercase(gameText.translate("gdx.connecting"))
                        : connection.mode() == NewGameConnectionDraft.Mode.JOIN
                                ? gameText.translate("game.unirme_a_timba")
                                : gameText.translate("game.crear_timba"),
                true, this::submitNewGame, !submitting);
    }

    private void submitNewGame() {
        if (!connection.canSubmit()) {
            showToast(gameText.translate("gdx.newgame.missing_required"));
            return;
        }
        try {
            submissions.submit(connection, table).whenComplete((request, failure) ->
                    Gdx.app.postRunnable(() -> completeSubmission(request, failure)));
            showToast(gameText.translate(
                    "gdx.newgame.connecting_waiting_room"));
        } catch (RuntimeException failure) {
            showToast(submissionError(failure));
        }
    }

    private void completeSubmission(NewGameSubmissionCoordinator.OpenedSession session,
            Throwable failure) {
        if (disposed) {
            if (session != null) {
                session.lobby().close();
            }
            return;
        }
        Throwable cause = unwrap(failure);
        if (cause == null) {
            sessionAccepted.accept(session);
        } else if (cause instanceof CancellationException) {
            surface = Surface.MENU;
            clearActiveField();
        } else {
            showToast(submissionError(cause));
        }
    }

    private void cancelOrReturnToMenu() {
        if (submissions.submitting()) {
            if (submissions.cancel()) {
                showToast("Cancelando conexión…");
            }
            return;
        }
        clearActiveField();
        recoveryLoadGeneration++;
        autoSubmitRecovery = false;
        connection.setRecoverRequested(false);
        table.setEconomyLocked(false);
        surface = Surface.MENU;
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof CompletionException completion
                && completion.getCause() != null ? completion.getCause() : failure;
    }

    private static String submissionError(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? "No se pudo abrir la sala de espera" : message;
    }

    private void drawToast() {
        float w = 820f;
        outerBox(WIDTH / 2f - w / 2f, 145f, w, 76f, ORANGE, PANEL_LIGHT);
        textFit(uiFont, toast, WIDTH / 2f, 193f, Color.WHITE, true, w - 52f);
    }

    private void panel(float x, float y, float w, float h, String title) {
        outerBox(x, y, w, h, LINE, PANEL);
        shapes.rect(x + 24f, y + h * 0.56f, w - 48f, h * 0.33f,
                new Color(CYAN.r, CYAN.g, CYAN.b, 0.008f),
                new Color(CYAN.r, CYAN.g, CYAN.b, 0.008f),
                new Color(CYAN.r, CYAN.g, CYAN.b, 0.052f),
                new Color(CYAN.r, CYAN.g, CYAN.b, 0.052f));
        shapes.rect(x + 24f, y + 18f, w - 48f, h * 0.18f,
                new Color(0f, 0f, 0f, 0.075f),
                new Color(0f, 0f, 0f, 0.075f),
                new Color(0f, 0f, 0f, 0f),
                new Color(0f, 0f, 0f, 0f));
        shapes.setColor(new Color(0x36d9ff70));
        shapes.rect(x + 18f, y + h - 8f, w - 36f, 2f);
        if (!title.isBlank()) {
            textFit(headingFont, title, x + 34f, y + h - 29f, GOLD,
                    false, Math.max(0f, w - 68f));
            shapes.setColor(new Color(0x31445f90));
            shapes.rect(x + 30f, y + h - 71f, w - 60f, 1f);
        }
    }

    private void sectionTag(float x, float y, float w, String label,
            Color color) {
        Color fill = new Color(color);
        fill.a = 0.10f;
        outerBox(x, y, w, 34f, new Color(color), fill);
        textFit(tinyFont, label, x + w / 2f, y + 23f,
                color, true, w - 18f);
    }

    private void field(float x, float y, float w, String label, String value,
            String id, boolean secret) {
        boolean focused = id.equals(activeField);
        textFit(smallFont, label, x, y + 98f, MUTED, false,
                Math.max(0f, w));
        outerBox(x, y, w, 70f, focused || hovered(x, y, w, 70f) ? CYAN : LINE,
                pressed(x, y, w, 70f) ? new Color(0x0b1424ff) : PANEL_LIGHT);
        String visible = secret && !value.isEmpty()
                ? "•".repeat(value.codePointCount(0, value.length())) : value;
        FrontendInputWindow window = visible.isEmpty()
                ? new FrontendInputWindow("—", 0, 0, 0f, 0f, 0f)
                : frontendInputWindow(uiFont, value, visible, w - 44f,
                        focused);
        drawInputSelection(x + 22f, y + 17f, 36f, window, focused);
        text(uiFont, window.text(), x + 22f, y + 44f,
                visible.isEmpty() ? DISABLED : Color.WHITE, false);
        drawInputCaret(x + 22f + window.caretOffset(), y + 17f, 36f,
                focused);
        textFieldHits.add(new TextFieldHit(id, new Rectangle(x, y, w, 70f)));
        hit(x, y, w, 70f, () -> activateField(id));
    }

    private FrontendInputWindow frontendInputWindow(BitmapFont font,
            String source, String display, float maxWidth, boolean focused) {
        if (focused) textEdit.focus(activeField, source);
        int sourceCaret = focused ? textEdit.caret(source) : source.length();
        int sourceSelectionStart = focused
                ? textEdit.selectionStart(source) : sourceCaret;
        int sourceSelectionEnd = focused
                ? textEdit.selectionEnd(source) : sourceCaret;
        boolean masked = !source.equals(display);
        int caret = masked ? source.codePointCount(0, sourceCaret) : sourceCaret;
        int selectionStart = masked
                ? source.codePointCount(0, sourceSelectionStart)
                : sourceSelectionStart;
        int selectionEnd = masked
                ? source.codePointCount(0, sourceSelectionEnd)
                : sourceSelectionEnd;
        int start = 0;
        while (start < caret
                && textWidth(font, display.substring(start, caret)) > maxWidth) {
            start = display.offsetByCodePoints(start, 1);
        }
        int end = caret;
        while (end < display.length()) {
            int next = display.offsetByCodePoints(end, 1);
            if (textWidth(font, display.substring(start, next)) > maxWidth) break;
            end = next;
        }
        selectionStart = Math.max(start, Math.min(end, selectionStart));
        selectionEnd = Math.max(start, Math.min(end, selectionEnd));
        int sourceStart = masked
                ? source.offsetByCodePoints(0,
                        display.codePointCount(0, start)) : start;
        int sourceEnd = masked
                ? source.offsetByCodePoints(0,
                        display.codePointCount(0, end)) : end;
        return new FrontendInputWindow(display.substring(start, end),
                sourceStart, sourceEnd,
                textWidth(font, display.substring(start, caret)),
                textWidth(font, display.substring(start, selectionStart)),
                textWidth(font, display.substring(selectionStart, selectionEnd)));
    }

    private void drawInputSelection(float x, float y, float height,
            FrontendInputWindow window, boolean focused) {
        if (!focused || window.selectionWidth() <= 0f) return;
        shapes.setColor(new Color(CYAN.r, CYAN.g, CYAN.b, 0.34f));
        shapes.rect(x + window.selectionOffset(), y,
                window.selectionWidth(), height);
    }

    private void drawInputCaret(float x, float y, float height,
            boolean focused) {
        if (!focused || ((int) (elapsed / INPUT_CARET_HALF_PERIOD_SECONDS) & 1) != 0) {
            return;
        }
        shapes.setColor(CYAN);
        shapes.rect(x + 1f, y, 2f, height);
    }

    private void openEditMenu(float pointerX, float pointerY) {
        float width = 270f;
        float height = 208f;
        editMenu = new EditMenu(
                MathUtils.clamp(pointerX, 12f, WIDTH - width - 12f),
                MathUtils.clamp(pointerY - height, 12f,
                        HEIGHT - height - 12f), width, height);
    }

    private void drawEditMenu() {
        String value = activeValue();
        textEdit.focus(activeField, value);
        boolean selected = textEdit.hasSelection(value);
        String clipboard = Gdx.app.getClipboard().getContents();
        float x = editMenu.bounds.x;
        float y = editMenu.bounds.y;
        float w = editMenu.bounds.width;
        float row = 48f;
        shapes.setColor(new Color(0x00000066));
        roundedRect(x + 7f, y - 7f, w, editMenu.bounds.height, 10f);
        outerBox(x, y, w, editMenu.bounds.height, CYAN_DARK,
                new Color(0x071221fc));
        editMenuItem(x + 8f, y + 152f, w - 16f, row,
                "CORTAR", selected, () -> {
                    String current = activeValue();
                    copyActiveSelection(current);
                    setActiveValue(textEdit.delete(current));
                });
        editMenuItem(x + 8f, y + 104f, w - 16f, row,
                "COPIAR", selected,
                () -> copyActiveSelection(activeValue()));
        editMenuItem(x + 8f, y + 56f, w - 16f, row,
                "PEGAR", clipboard != null && !clipboard.isEmpty(),
                () -> replaceActiveSelection(
                        Objects.requireNonNullElse(
                                Gdx.app.getClipboard().getContents(), "")));
        editMenuItem(x + 8f, y + 8f, w - 16f, row,
                "SELECCIONAR TODO", !value.isEmpty(),
                () -> textEdit.selectAll(activeValue()));
    }

    private void editMenuItem(float x, float y, float w, float h,
            String label, boolean enabled, Runnable action) {
        boolean over = enabled && hovered(x, y, w, h);
        shapes.setColor(over ? new Color(0x17304aee)
                : new Color(0x0b1828ee));
        roundedRect(x, y, w, h - 2f, 6f);
        textFit(smallFont, label, x + 18f, y + 30f,
                enabled ? Color.WHITE : DISABLED, false,
                Math.max(0f, w - 36f));
        if (enabled) {
            editMenuHits.add(new Hit(new Rectangle(x, y, w, h), () -> {
                action.run();
                editMenu = null;
            }));
        }
    }

    private float textWidth(BitmapFont font, String value) {
        glyph.setText(font, value);
        return glyph.width;
    }

    private void toggle(float x, float y, float w, String label,
            boolean value, Runnable action, boolean enabled) {
        Color border = enabled && hovered(x, y, w, 76f)
                ? CYAN : enabled ? LINE : new Color(0x253044ff);
        Color fill = enabled && pressed(x, y, w, 76f)
                ? new Color(0x0b1424ff) : enabled ? PANEL_LIGHT : new Color(0x0b111ddd);
        outerBox(x, y, w, 76f, border, fill);
        textFit(smallFont, label, x + 22f, y + 47f,
                enabled ? Color.WHITE : DISABLED, false, w - 132f);
        float tx = x + w - 88f;
        float target = value && enabled ? 1f : 0f;
        float animation = toggleAnimations.getOrDefault(label, target);
        animation += (target - animation) * Math.min(1f, frameDelta * 15f);
        toggleAnimations.put(label, animation);
        Color track = new Color(0x253248ff).lerp(
                new Color(0x20c765ff), animation);
        shapes.setColor(track);
        roundedRect(tx, y + 19f, 66f, 38f, 19f);
        shapes.setColor(new Color(0x8290a4ff).lerp(
                new Color(0xb8ffc5ff), animation));
        shapes.circle(tx + 19f + 28f * animation, y + 38f, 14f, 32);
        if (enabled) {
            hit(x, y, w, 76f, action);
        }
    }

    private void stepper(float x, float y, float w, String label, int value,
            int min, int max, Runnable minus, Runnable plus) {
        stepper(x, y, w, label, value, min, max, minus, plus, true);
    }

    private void stepper(float x, float y, float w, String label, int value,
            int min, int max, Runnable minus, Runnable plus, boolean enabled) {
        textFit(smallFont, label, x, y + 99f,
                enabled ? MUTED : DISABLED, false, Math.max(0f, w));
        Color border = enabled && hovered(x, y, w, 72f)
                ? CYAN : enabled ? LINE : new Color(0x253044ff);
        Color fill = enabled && pressed(x, y, w, 72f)
                ? new Color(0x0b1424ff) : enabled ? PANEL_LIGHT : new Color(0x0b111ddd);
        outerBox(x, y, w, 72f, border, fill);
        float side = 70f;
        shapes.setColor(enabled ? new Color(0x20324cff) : new Color(0x141c2aff));
        roundedRect(x + 3f, y + 3f, side, 66f, 10f);
        roundedRect(x + w - side - 3f, y + 3f, side, 66f, 10f);
        text(headingFont, "-", x + 38f, y + 48f, enabled ? Color.WHITE : DISABLED, true);
        text(headingFont, "+", x + w - 38f, y + 48f, enabled ? Color.WHITE : DISABLED, true);
        text(headingFont, Integer.toString(value), x + w / 2f, y + 49f,
                enabled ? GOLD : DISABLED, true);
        if (enabled) {
            hit(x, y, side + 6f, 72f, minus);
            hit(x + w - side - 6f, y, side + 6f, 72f, plus);
        }
    }

    private void choice(float x, float y, float w, String label, String value,
            Runnable action) {
        choice(x, y, w, label, value, action, true);
    }

    private void choice(float x, float y, float w, String label, String value,
            Runnable action, boolean enabled) {
        choice(x, y, w, 72f, label, value, action, enabled);
    }

    private void choice(float x, float y, float w, float h, String label,
            String value, Runnable action) {
        choice(x, y, w, h, label, value, action, true);
    }

    private void choice(float x, float y, float w, float h, String label,
            String value, Runnable action, boolean enabled) {
        textFit(smallFont, label, x, y + 99f,
                enabled ? MUTED : DISABLED, false, Math.max(0f, w));
        Color border = enabled && hovered(x, y, w, h)
                ? CYAN : enabled ? LINE : new Color(0x253044ff);
        Color fill = enabled && pressed(x, y, w, h)
                ? new Color(0x0b1424ff) : enabled ? PANEL_LIGHT : new Color(0x0b111ddd);
        outerBox(x, y, w, h, border, fill);
        textFit(uiFont, value, x + 22f, y + h / 2f + 10f,
                enabled ? Color.WHITE : DISABLED, false, w - 88f);
        Color arrowColor = enabled ? GOLD : DISABLED;
        shapes.setColor(arrowColor);
        float cy = y + h / 2f;
        shapes.rectLine(x + w - 42f, cy - 12f,
                x + w - 30f, cy, 3f);
        shapes.rectLine(x + w - 30f, cy,
                x + w - 42f, cy + 12f, 3f);
        if (enabled) {
            hit(x, y, w, h, action);
        }
    }

    private void bidirectionalChoice(float x, float y, float w, String label,
            String value, Runnable previous, Runnable next, boolean enabled) {
        float h = 72f;
        textFit(smallFont, label, x, y + 99f,
                enabled ? MUTED : DISABLED, false, Math.max(0f, w));
        Color border = enabled && hovered(x, y, w, h)
                ? CYAN : enabled ? LINE : new Color(0x253044ff);
        Color fill = enabled && pressed(x, y, w, h)
                ? new Color(0x0b1424ff)
                : enabled ? PANEL_LIGHT : new Color(0x0b111ddd);
        outerBox(x, y, w, h, border, fill);

        float side = 70f;
        shapes.setColor(enabled ? new Color(0x20324cff)
                : new Color(0x141c2aff));
        roundedRect(x + 3f, y + 3f, side, h - 6f, 10f);
        roundedRect(x + w - side - 3f, y + 3f, side, h - 6f, 10f);

        Color arrowColor = enabled ? GOLD : DISABLED;
        shapes.setColor(arrowColor);
        float cy = y + h / 2f;
        shapes.rectLine(x + 42f, cy - 12f, x + 30f, cy, 3f);
        shapes.rectLine(x + 30f, cy, x + 42f, cy + 12f, 3f);
        shapes.rectLine(x + w - 42f, cy - 12f,
                x + w - 30f, cy, 3f);
        shapes.rectLine(x + w - 30f, cy,
                x + w - 42f, cy + 12f, 3f);
        textFit(uiFont, value, x + w / 2f, y + h / 2f + 10f,
                enabled ? Color.WHITE : DISABLED, true,
                Math.max(0f, w - side * 2f - 28f));
        if (enabled) {
            hit(x, y, side + 6f, h, previous);
            hit(x + w - side - 6f, y, side + 6f, h, next);
        }
    }

    private void infoRow(float x, float y, String value) {
        shapes.setColor(CYAN_DARK);
        shapes.circle(x + 10f, y + 4f, 9f, 24);
        text(smallFont, "i", x + 10f, y + 10f, Color.WHITE, true);
        text(smallFont, value, x + 34f, y + 10f, MUTED, false);
    }

    private void summaryRow(float x, float y, String label, String value) {
        shapes.setColor(new Color(0x31445f77));
        shapes.rect(x, y - 18f, 810f, 1f);
        text(smallFont, label, x, y + 18f, MUTED, false);
        text(uiFont, value, x + 790f, y + 18f, Color.WHITE, true);
    }

    private void keyHint(float x, float y, String key, String action) {
        outerBox(x, y, 64f, 48f, CYAN_DARK, new Color(0x07111fff));
        text(smallFont, key, x + 32f, y + 32f, CYAN, true);
        text(smallFont, action, x + 78f, y + 32f, MUTED, false);
    }

    private void button(float x, float y, float w, float h, String label,
            boolean primary, Runnable action) {
        button(x, y, w, h, label, primary, action, true);
    }

    private void button(float x, float y, float w, float h, String label,
            boolean primary, Runnable action, boolean enabled) {
        themedButton(x, y, w, h, label,
                primary ? ButtonTone.FEATURED : ButtonTone.NEUTRAL,
                action, enabled);
    }

    private void themedButton(float x, float y, float w, float h,
            String label, ButtonTone tone, Runnable action, boolean enabled) {
        boolean hover = enabled && hovered(x, y, w, h);
        float hoverTarget = hover ? 1f : 0f;
        float hoverAmount = hoverAnimations.getOrDefault(label, hoverTarget);
        hoverAmount += (hoverTarget - hoverAmount)
                * Math.min(1f, frameDelta * 13f);
        hoverAnimations.put(label, hoverAmount);
        boolean down = enabled && pressed(x, y, w, h);
        GdxUiButtonStyle.Tone sharedTone = GdxUiButtonStyle.Tone.valueOf(
                tone.name());
        GdxUiButtonStyle.draw(shapes, x, y, w, h, sharedTone, enabled,
                hoverAmount, down, 1f);
        Color labelColor = GdxUiButtonStyle.labelColor(sharedTone, enabled);
        textFit(actionFont, label, x + w / 2f, y + h / 2f + 8f,
                labelColor, true, w - 30f);
        if (enabled) {
            hit(x, y, w, h, action);
        }
    }

    private void mainMenuButton(float x, float y, float w, float h,
            String label, int icon, boolean primary, Runnable action) {
        button(x, y, w, h, label, primary, action);
        boolean hover = hovered(x, y, w, h);
        Color color = hover ? CYAN : GOLD;
        float iconX = x + 43f;
        float iconY = y + h / 2f;
        shapes.setColor(color);
        drawMainMenuIcon(icon, iconX, iconY, color);
        Color divider = new Color(color);
        divider.a = primary ? 0.34f : 0.24f + (hover ? 0.20f : 0f);
        shapes.setColor(divider);
        shapes.rect(x + 82f, y + 17f, 2f, h - 34f);
    }

    private void drawMainMenuIcon(int icon, float cx, float cy, Color color) {
        switch (icon) {
            case 0 -> {
                // Poker table with two seated players.
                roundedRect(cx - 22f, cy - 10f, 44f, 20f, 10f);
                shapes.circle(cx - 16f, cy + 16f, 6f, 24);
                shapes.circle(cx + 16f, cy + 16f, 6f, 24);
            }
            case 1 -> {
                // Arrow entering a table/session.
                roundedRect(cx - 20f, cy - 19f, 9f, 38f, 4f);
                shapes.rect(cx - 11f, cy - 4f, 28f, 8f);
                shapes.triangle(cx + 24f, cy,
                        cx + 11f, cy + 13f, cx + 11f, cy - 13f);
            }
            case 2 -> {
                // Statistics bars.
                roundedRect(cx - 22f, cy - 20f, 10f, 25f, 3f);
                roundedRect(cx - 5f, cy - 20f, 10f, 40f, 3f);
                roundedRect(cx + 12f, cy - 20f, 10f, 31f, 3f);
            }
            case 3 -> {
                // Settings cog.
                shapes.circle(cx, cy, 19f, 40);
                Color cutout = new Color(0x0b1729ff);
                shapes.setColor(cutout);
                shapes.circle(cx, cy, 8f, 32);
                shapes.setColor(color);
                for (int i = 0; i < 8; i++) {
                    double angle = i * Math.PI / 4d;
                    shapes.circle(cx + MathUtils.cos((float) angle) * 22f,
                            cy + MathUtils.sin((float) angle) * 22f, 4f, 16);
                }
            }
            case 4 -> {
                // Information mark.
                shapes.circle(cx, cy, 20f, 40);
                Color cutout = new Color(0x0b1729ff);
                shapes.setColor(cutout);
                shapes.circle(cx, cy + 9f, 3f, 16);
                roundedRect(cx - 3f, cy - 13f, 6f, 17f, 3f);
            }
            case 5 -> {
                // Exit door and arrow.
                roundedRect(cx - 20f, cy - 22f, 10f, 44f, 4f);
                shapes.rect(cx - 10f, cy - 4f, 27f, 8f);
                shapes.triangle(cx + 24f, cy,
                        cx + 11f, cy + 13f, cx + 11f, cy - 13f);
            }
            default -> {
            }
        }
    }

    private void backButton(float x, float y, float w, float h,
            Runnable action) {
        outerBox(x, y, w, h, CYAN_DARK, PANEL_LIGHT);
        shapes.setColor(CYAN);
        shapes.triangle(x + 17f, y + h / 2f,
                x + 35f, y + h / 2f + 14f,
                x + 35f, y + h / 2f - 14f);
        shapes.rect(x + 31f, y + h / 2f - 3f, 18f, 6f);
        hit(x, y, w, h, action);
    }

    private void drawNavIcon(int index, float cx, float cy, Color color) {
        Color cutout = index == page ? new Color(0x123047f2)
                : new Color(0x0b1424dd);
        shapes.setColor(color);
        switch (index) {
            case 0 -> {
                shapes.circle(cx, cy, 17f, 40);
                shapes.setColor(cutout);
                shapes.circle(cx, cy, 11f, 32);
                shapes.setColor(color);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4d;
                    shapes.circle(cx + (float) Math.cos(a) * 14f,
                            cy + (float) Math.sin(a) * 14f, 2.2f, 12);
                }
            }
            case 1 -> {
                shapes.circle(cx - 7f, cy + 4f, 14f, 36);
                shapes.circle(cx + 8f, cy - 5f, 14f, 36);
                shapes.setColor(cutout);
                shapes.circle(cx - 7f, cy + 4f, 8f, 28);
                shapes.circle(cx + 8f, cy - 5f, 8f, 28);
            }
            case 2 -> {
                roundedRect(cx - 18f, cy - 15f, 36f, 9f, 4.5f);
                roundedRect(cx - 15f, cy - 3f, 33f, 9f, 4.5f);
                roundedRect(cx - 18f, cy + 9f, 36f, 9f, 4.5f);
            }
            case 3 -> {
                roundedRect(cx - 17f, cy - 13f, 23f, 31f, 4f);
                shapes.setColor(cutout);
                roundedRect(cx - 13f, cy - 9f, 15f, 23f, 2f);
                shapes.setColor(color);
                roundedRect(cx - 1f, cy - 18f, 23f, 31f, 4f);
                shapes.setColor(cutout);
                roundedRect(cx + 3f, cy - 14f, 15f, 23f, 2f);
            }
            default -> {
                roundedRect(cx - 18f, cy - 14f, 36f, 28f, 6f);
                shapes.rect(cx - 3f, cy + 14f, 6f, 8f);
                shapes.circle(cx, cy + 23f, 3.5f, 16);
                shapes.setColor(cutout);
                shapes.circle(cx - 8f, cy, 3.5f, 16);
                shapes.circle(cx + 8f, cy, 3.5f, 16);
                shapes.rect(cx - 9f, cy - 8f, 18f, 3f);
            }
        }
    }

    private void drawAvatarIcon(float cx, float cy) {
        if (selectedAvatarTexture != null) {
            lobbyAvatars.add(new LobbyAvatarItem(selectedAvatarTexture,
                    cx - 61f, cy - 61f, 122f));
            return;
        }
        shapes.setColor(new Color(0x36d9ffcc));
        shapes.circle(cx, cy + 18f, 20f, 40);
        roundedRect(cx - 37f, cy - 39f, 74f, 38f, 19f);
        shapes.setColor(GOLD);
        shapes.circle(cx + 31f, cy + 34f, 7f, 24);
    }

    private void selectAvatar() {
        if (avatarSelectionPending || connection == null) return;
        avatarSelectionPending = true;
        NewGameConnectionDraft target = connection;
        CompletableFuture.supplyAsync(GdxFrontendScreen::openAvatarFileDialog,
                recoveryExecutor).whenComplete((selected, failure) ->
                Gdx.app.postRunnable(() -> completeAvatarSelection(target,
                        selected, failure)));
    }

    private void completeAvatarSelection(NewGameConnectionDraft target,
            Path selected, Throwable failure) {
        avatarSelectionPending = false;
        if (disposed || connection != target) return;
        Throwable cause = unwrap(failure);
        if (cause != null) {
            showToast("No se pudo abrir el selector de avatar");
            LOGGER.log(Level.WARNING, "GDX avatar selector failed", cause);
            return;
        }
        if (selected == null) return;
        if (!isSupportedAvatar(selected) || !connection.setAvatar(selected)) {
            showToast("Avatar no valido o mayor de 256 KB");
            return;
        }
        refreshSelectedAvatarTexture();
    }

    private void resetAvatar() {
        if (connection == null || avatarSelectionPending) return;
        connection.resetAvatar();
        disposeSelectedAvatarTexture();
    }

    private void refreshSelectedAvatarTexture() {
        disposeSelectedAvatarTexture();
        if (connection == null || connection.avatar() == null) return;
        Path path = connection.avatar().toAbsolutePath().normalize();
        try {
            Texture texture = new Texture(Gdx.files.absolute(path.toString()));
            texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
            selectedAvatarTexture = texture;
        } catch (RuntimeException failure) {
            connection.resetAvatar();
            LOGGER.log(Level.WARNING,
                    "GDX rejected saved avatar texture " + path, failure);
        }
    }

    private void disposeSelectedAvatarTexture() {
        if (selectedAvatarTexture != null) selectedAvatarTexture.dispose();
        selectedAvatarTexture = null;
    }

    static boolean isSupportedAvatar(Path path) {
        if (path == null || !java.nio.file.Files.isRegularFile(path)
                || !java.nio.file.Files.isReadable(path)) return false;
        try {
            if (java.nio.file.Files.size(path)
                    > NewGameConnectionDraft.MAX_AVATAR_BYTES) return false;
            BufferedImage image = ImageIO.read(path.toFile());
            return image != null && image.getWidth() > 0 && image.getHeight() > 0;
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    private static Path openAvatarFileDialog() {
        FileDialog dialog = new FileDialog((Frame) null,
                "Seleccionar avatar", FileDialog.LOAD);
        dialog.setMultipleMode(false);
        dialog.setDirectory(System.getProperty("user.home"));
        dialog.setFilenameFilter((directory, name) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            return lower.endsWith(".png") || lower.endsWith(".jpg")
                    || lower.endsWith(".jpeg") || lower.endsWith(".gif")
                    || lower.endsWith(".bmp") || lower.endsWith(".webp");
        });
        try {
            dialog.setVisible(true);
            String file = dialog.getFile();
            String directory = dialog.getDirectory();
            return file == null || directory == null ? null
                    : new File(directory, file).toPath();
        } finally {
            dialog.dispose();
        }
    }

    private void outerBox(float x, float y, float w, float h, Color border,
            Color fill) {
        shapes.setColor(fill);
        roundedRect(x, y, w, h, 14f);
        shapes.setColor(border);
        roundedRectOutline(x + 1f, y + 1f, w - 2f, h - 2f,
                13f, 2f);
        if (h <= 90f && w > 90f) {
            float inset = 14f;
            float sheenBottom = y + h * 0.54f;
            float sheenTop = y + h - 9f;
            shapes.rect(x + inset, sheenBottom, w - inset * 2f,
                    sheenTop - sheenBottom,
                    new Color(1f, 1f, 1f, 0.018f),
                    new Color(1f, 1f, 1f, 0.018f),
                    new Color(1f, 1f, 1f, 0.115f),
                    new Color(1f, 1f, 1f, 0.115f));
            shapes.rect(x + inset, y + 8f, w - inset * 2f, h * 0.18f,
                    new Color(0f, 0f, 0f, 0.11f),
                    new Color(0f, 0f, 0f, 0.11f),
                    new Color(0f, 0f, 0f, 0.01f),
                    new Color(0f, 0f, 0f, 0.01f));
        }
        shapes.setColor(1f, 1f, 1f, 0.055f);
        shapes.rect(x + 16f, y + h - 7f, w - 32f, 1.5f);
    }

    private void roundedRectOutline(float x, float y, float w, float h,
            float radius, float thickness) {
        float r = Math.min(radius, Math.min(w, h) / 2f);
        shapes.rectLine(x + r, y, x + w - r, y, thickness);
        shapes.rectLine(x + r, y + h, x + w - r, y + h, thickness);
        shapes.rectLine(x, y + r, x, y + h - r, thickness);
        shapes.rectLine(x + w, y + r, x + w, y + h - r, thickness);
        quarterArc(x + r, y + r, r, 180f, thickness);
        quarterArc(x + w - r, y + r, r, 270f, thickness);
        quarterArc(x + w - r, y + h - r, r, 0f, thickness);
        quarterArc(x + r, y + h - r, r, 90f, thickness);
    }

    private void quarterArc(float cx, float cy, float radius,
            float startDegrees, float thickness) {
        final int segments = 10;
        float previousRadians = (float) Math.toRadians(startDegrees);
        float previousX = cx + (float) Math.cos(previousRadians) * radius;
        float previousY = cy + (float) Math.sin(previousRadians) * radius;
        for (int i = 1; i <= segments; i++) {
            float radians = (float) Math.toRadians(
                    startDegrees + 90f * i / segments);
            float nextX = cx + (float) Math.cos(radians) * radius;
            float nextY = cy + (float) Math.sin(radians) * radius;
            shapes.rectLine(previousX, previousY, nextX, nextY, thickness);
            previousX = nextX;
            previousY = nextY;
        }
    }

    private void roundedRect(float x, float y, float w, float h, float r) {
        float radius = Math.min(r, Math.min(w, h) / 2f);
        shapes.rect(x + radius, y, w - 2f * radius, h);
        shapes.rect(x, y + radius, radius, h - 2f * radius);
        shapes.rect(x + w - radius, y + radius, radius, h - 2f * radius);
        quarterCircle(x + radius, y + radius, radius, 180f);
        quarterCircle(x + w - radius, y + radius, radius, 270f);
        quarterCircle(x + w - radius, y + h - radius, radius, 0f);
        quarterCircle(x + radius, y + h - radius, radius, 90f);
    }

    private void quarterCircle(float cx, float cy, float radius,
            float startDegrees) {
        final int segments = 10;
        float previousRadians = (float) Math.toRadians(startDegrees);
        float previousX = cx + (float) Math.cos(previousRadians) * radius;
        float previousY = cy + (float) Math.sin(previousRadians) * radius;
        for (int i = 1; i <= segments; i++) {
            float radians = (float) Math.toRadians(
                    startDegrees + 90f * i / segments);
            float nextX = cx + (float) Math.cos(radians) * radius;
            float nextY = cy + (float) Math.sin(radians) * radius;
            shapes.triangle(cx, cy, previousX, previousY, nextX, nextY);
            previousX = nextX;
            previousY = nextY;
        }
    }

    private void text(BitmapFont font, String value, float x, float y,
            Color color, boolean centered) {
        texts.add(new TextItem(font, value, x, y, new Color(color), centered));
    }

    private void textFit(BitmapFont preferred, String value, float x, float y,
            Color color, boolean centered, float maxWidth) {
        BitmapFont selected = preferred;
        if (!fits(selected, value, maxWidth) && selected != smallFont) {
            selected = smallFont;
        }
        if (!fits(selected, value, maxWidth)) {
            selected = tinyFont;
        }
        text(selected, ellipsize(selected, value, maxWidth), x, y, color, centered);
    }

    private boolean fits(BitmapFont font, String value, float maxWidth) {
        glyph.setText(font, value);
        return glyph.width <= maxWidth;
    }

    private String ellipsize(BitmapFont font, String value, float maxWidth) {
        if (fits(font, value, maxWidth)) {
            return value;
        }
        String suffix = "…";
        int end = value.length();
        while (end > 0) {
            end = value.offsetByCodePoints(end, -1);
            String candidate = value.substring(0, end).stripTrailing() + suffix;
            if (fits(font, candidate, maxWidth)) {
                return candidate;
            }
        }
        return suffix;
    }

    private void hit(float x, float y, float w, float h, Runnable action) {
        if (surface == Surface.MENU && menuRevealProgress() < 0.98f) {
            return;
        }
        hits.add(new Hit(new Rectangle(x, y, w, h), action));
    }

    private boolean hovered(float x, float y, float w, float h) {
        if (surface == Surface.MENU && menuRevealProgress() < 0.98f) {
            return false;
        }
        return pointer.x >= x && pointer.x <= x + w
                && pointer.y >= y && pointer.y <= y + h;
    }

    private boolean pressed(float x, float y, float w, float h) {
        if (pressedHit == null) {
            return false;
        }
        Rectangle b = pressedHit.bounds;
        return Math.abs(b.x - x) < 0.5f && Math.abs(b.y - y) < 0.5f
                && Math.abs(b.width - w) < 0.5f && Math.abs(b.height - h) < 0.5f;
    }

    private void showToast(String value) {
        toast = value;
        toastUntil = System.currentTimeMillis() + 2800L;
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointerIndex, int button) {
        pointer.set(screenX, screenY);
        viewport.unproject(pointer);
        pressedHit = null;
        if (editMenu != null) {
            for (int i = editMenuHits.size() - 1; i >= 0; i--) {
                Hit hit = editMenuHits.get(i);
                if (hit.bounds.contains(pointer)) {
                    pressedHit = hit;
                    return true;
                }
            }
            editMenu = null;
            return true;
        }
        for (int i = textFieldHits.size() - 1; i >= 0; i--) {
            TextFieldHit field = textFieldHits.get(i);
            if (field.bounds.contains(pointer)) {
                activateField(field.id);
                if (button == Input.Buttons.RIGHT) {
                    openEditMenu(pointer.x, pointer.y);
                } else if (button == Input.Buttons.LEFT) {
                    placeFrontendCaret(field, pointer.x, shiftPressed());
                    pointerSelectionField = field.id;
                }
                return true;
            }
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.bounds.contains(pointer)) {
                pressedHit = hit;
                return true;
            }
        }
        clearActiveField();
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointerIndex) {
        if (pointerSelectionField == null) return false;
        pointer.set(screenX, screenY);
        viewport.unproject(pointer);
        for (int i = textFieldHits.size() - 1; i >= 0; i--) {
            TextFieldHit field = textFieldHits.get(i);
            if (field.id.equals(pointerSelectionField)) {
                placeFrontendCaret(field, pointer.x, true);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointerIndex, int button) {
        pointer.set(screenX, screenY);
        viewport.unproject(pointer);
        Hit released = pressedHit;
        pressedHit = null;
        pointerSelectionField = null;
        if (released != null && released.bounds.contains(pointer)) {
            released.action.run();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyDown(int keycode) {
        editMenu = null;
        boolean alt = Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT);
        boolean control = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
        boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
        if (surface == Surface.SETTINGS
                && settingsSession.section()
                        == GdxSettingsContract.Section.SHORTCUTS
                && settingsShortcutCaptureId != null) {
            GdxShortcutBindings.Assignment assignment = shortcutBindings.assign(
                    settingsShortcutCaptureId, keycode, alt, control, shift);
            if (assignment == GdxShortcutBindings.Assignment.ASSIGNED) {
                settingsShortcutCaptureId = null;
                settingsShortcutStatus =
                        "ATAJO ACTUALIZADO · GUARDA PARA CONFIRMAR";
            } else if (assignment == GdxShortcutBindings.Assignment.CONFLICT) {
                settingsShortcutStatus = "ESA COMBINACIÓN YA ESTÁ EN USO";
            } else {
                settingsShortcutStatus =
                        "TECLA NO COMPATIBLE · PRUEBA OTRA";
            }
            return true;
        }
        String shortcut = activeField == null
                ? shortcutBindings.actionFor(keycode, alt, control, shift)
                : null;
        // F11 remains a non-configurable desktop escape hatch while the
        // canonical Swing shortcut (ALT+F by default) stays editable.
        if (keycode == Input.Keys.F11
                || GdxShortcutBindings.FULLSCREEN.equals(shortcut)) {
            toggleFullscreenMode();
            return true;
        }
        if (keycode == Input.Keys.ESCAPE) {
            if (blindStructureDialog != BlindStructureDialog.NONE) {
                if (blindStructureDialog == BlindStructureDialog.EDITOR) {
                    closeBlindStructureEditor();
                } else {
                    blindStructureDialog = BlindStructureDialog.EDITOR;
                    blindStructureNameDraft = "";
                    clearActiveField();
                }
                return true;
            }
            if (presetDialog != PresetDialog.NONE) {
                closePresetDialog();
                return true;
            }
            if (settingsDiscardConfirmation) {
                settingsDiscardConfirmation = false;
                return true;
            }
            if (lobbyVoiceRecorder != null) {
                cancelLobbyVoiceRecording();
                return true;
            }
            if (surface == Surface.LOBBY
                    && (lobbyEmojiPickerOpen || lobbyImageMode)) {
                lobbyEmojiPickerOpen = false;
                lobbyImageMode = false;
                activateField("lobbyChat");
                return true;
            }
            if (surface == Surface.NEW_GAME) {
                cancelOrReturnToMenu();
            } else if (surface == Surface.LOBBY) {
                if (lobbyConfirmation != null) {
                    lobbyConfirmation = null;
                } else {
                    lobbyConfirmation = LobbyConfirmation.LEAVE;
                }
            } else if (surface == Surface.SETTINGS) {
                requestCancelSettings();
            } else {
                Gdx.app.exit();
            }
            return true;
        }
        if ((keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER)
                && ("lobbyChat".equals(activeField)
                        || "lobbyImage".equals(activeField))) {
            sendLobbyComposer();
            return true;
        }
        if ((keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER)
                && presetDialog == PresetDialog.NAME
                && "presetName".equals(activeField)) {
            submitPresetName();
            return true;
        }
        if ((keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER)
                && "blindStructureName".equals(activeField)) {
            submitBlindStructureName();
            return true;
        }
        if (activeField != null && handleActiveFieldKey(keycode)) {
            if (isTextDeletionKey(keycode)) {
                textDeleteRepeat.press(keycode);
            }
            return true;
        }
        if (GdxShortcutBindings.MUTE.equals(shortcut)) {
            toggleMasterSound();
            return true;
        }
        if (GdxShortcutBindings.VOLUME_UP.equals(shortcut)) {
            adjustFrontendMasterVolume(0.05f);
            return true;
        }
        if (GdxShortcutBindings.VOLUME_DOWN.equals(shortcut)) {
            adjustFrontendMasterVolume(-0.05f);
            return true;
        }
        if (GdxShortcutBindings.VOICE_RECORD.equals(shortcut)
                && surface == Surface.LOBBY) {
            if (lobbyVoiceRecorder == null) beginLobbyVoiceRecording();
            return true;
        }
        boolean hostPages = surface == Surface.NEW_GAME
                && connection.mode() != NewGameConnectionDraft.Mode.JOIN;
        if (hostPages && activeField == null && keycode == Input.Keys.LEFT && page > 0) {
            page--;
            return true;
        }
        if (hostPages && activeField == null && keycode == Input.Keys.RIGHT && page < 4) {
            page++;
            return true;
        }
        if (hostPages && keycode == Input.Keys.TAB) {
            clearActiveField();
            page = (page + 1) % 5;
            return true;
        }
        return false;
    }

    private void toggleFullscreenMode() {
        GdxDisplayModeController.toggle(
                GdxWindowMode.configured(initialProperties));
    }

    private static boolean isTextDeletionKey(int keycode) {
        return keycode == Input.Keys.BACKSPACE
                || keycode == Input.Keys.FORWARD_DEL;
    }

    private void updateTextDeleteRepeat() {
        int keycode = textDeleteRepeat.keycode();
        boolean pressed = activeField != null && keycode >= 0
                && Gdx.input.isKeyPressed(keycode);
        int repeats = textDeleteRepeat.update(frameDelta, pressed);
        for (int repeat = 0; repeat < repeats && activeField != null;
                repeat++) {
            handleActiveFieldKey(keycode);
        }
    }

    private void adjustFrontendMasterVolume(float delta) {
        float volume = MathUtils.clamp(
                Math.round((masterVolume() + delta) * 100f) / 100f,
                0f, 1f);
        initialProperties.setProperty("master_volume",
                Float.toString(volume));
        if (preferences != null && surface != Surface.SETTINGS) {
            preferences.saveDeferred();
        }
        syncMusicForSurface();
        playPreferenceSound("misc/volume_change.wav",
                "sonido_volumen", 0.72f);
    }

    @Override
    public boolean keyTyped(char character) {
        editMenu = null;
        if (activeField == null || Character.isISOControl(character)) {
            return false;
        }
        String value = activeValue();
        textEdit.focus(activeField, value);
        replaceActiveSelection(Character.toString(character));
        return true;
    }

    private boolean handleActiveFieldKey(int keycode) {
        String value = activeValue();
        textEdit.focus(activeField, value);
        boolean control = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
        boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
        if (control && keycode == Input.Keys.A) {
            textEdit.selectAll(value);
            return true;
        }
        if (control && keycode == Input.Keys.C) {
            copyActiveSelection(value);
            return true;
        }
        if (control && keycode == Input.Keys.X) {
            copyActiveSelection(value);
            if (textEdit.hasSelection(value)) {
                setActiveValue(textEdit.delete(value));
            }
            return true;
        }
        if (control && keycode == Input.Keys.V) {
            String clipboard = Gdx.app.getClipboard().getContents();
            replaceActiveSelection(clipboard == null ? "" : clipboard);
            return true;
        }
        switch (keycode) {
            case Input.Keys.LEFT -> {
                textEdit.left(value, shift);
                normalizeLobbyEmojiCaret(value, -1, shift);
                return true;
            }
            case Input.Keys.RIGHT -> {
                textEdit.right(value, shift);
                normalizeLobbyEmojiCaret(value, 1, shift);
                return true;
            }
            case Input.Keys.HOME -> {
                textEdit.home(value, shift);
                return true;
            }
            case Input.Keys.END -> {
                textEdit.end(value, shift);
                return true;
            }
            case Input.Keys.BACKSPACE -> {
                setActiveValue(textEdit.backspace(value));
                return true;
            }
            case Input.Keys.FORWARD_DEL -> {
                setActiveValue(textEdit.delete(value));
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void copyActiveSelection(String value) {
        if (textEdit.hasSelection(value)) {
            Gdx.app.getClipboard().setContents(textEdit.selectedText(value));
        }
    }

    private void replaceActiveSelection(String replacement) {
        String value = activeValue();
        textEdit.focus(activeField, value);
        String clean = sanitizeActiveInput(replacement);
        setActiveValue(textEdit.replaceSelection(value, clean,
                activeFieldLimit()));
    }

    private String sanitizeActiveInput(String value) {
        String clean = Objects.requireNonNullElse(value, "")
                .replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
        if ("port".equals(activeField)) {
            clean = clean.replaceAll("[^0-9]", "");
        } else if ("nick".equals(activeField)) {
            clean = clean.replace("$", "");
        }
        return clean;
    }

    private int activeFieldLimit() {
        return switch (activeField) {
            case "nick" -> 15;
            case "password" -> 30;
            case "port" -> 5;
            case "lobbyChat" -> 16 * 1024 * 1024;
            case "presetName" -> GamePresetCatalog.MAX_NAME_LENGTH;
            case "blindStructureName" ->
                BlindStructureCatalog.MAX_NAME_LENGTH;
            default -> 128;
        };
    }

    private void normalizeLobbyEmojiCaret(String value, int direction,
            boolean extend) {
        if (!"lobbyChat".equals(activeField) || lobbyImageMode) return;
        int caret = textEdit.caret(value);
        Matcher matcher = EMOJI_TOKEN.matcher(value);
        while (matcher.find()) {
            if (caret > matcher.start() && caret < matcher.end()) {
                textEdit.setCaret(value,
                        direction < 0 ? matcher.start() : matcher.end(), extend);
                return;
            }
        }
    }

    private String activeValue() {
        return switch (activeField) {
            case "nick" -> connection.nickname();
            case "password" -> connection.password();
            case "server" -> connection.server();
            case "port" -> connection.port();
            case "lobbyChat" -> lobbyChatDraft;
            case "lobbyImage" -> lobbyImageDraft;
            case "presetName" -> presetNameDraft;
            case "blindStructureName" -> blindStructureNameDraft;
            default -> "";
        };
    }

    private void setActiveValue(String value) {
        switch (activeField) {
            case "nick" -> connection.setNickname(value);
            case "password" -> connection.setPassword(value);
            case "server" -> connection.setServer(value);
            case "port" -> connection.setPort(value);
            case "lobbyChat" -> lobbyChatDraft = value;
            case "lobbyImage" -> lobbyImageDraft = value;
            case "presetName" -> presetNameDraft = value;
            case "blindStructureName" -> blindStructureNameDraft = value;
            default -> {
            }
        }
    }

    private void activateField(String id) {
        activeField = id;
        textEdit.focus(id, activeValue());
    }

    private void placeFrontendCaret(TextFieldHit field, float pointerX,
            boolean extend) {
        String value = activeValue();
        textEdit.focus(field.id, value);
        float localX = pointerX - field.bounds.x - 22f;
        int target;
        if ("lobbyChat".equals(field.id) && !lobbyImageMode
                && !value.isEmpty()) {
            ComposerWindow window = lobbyComposerWindow(value,
                    field.bounds.width - 44f);
            int start = window.sourceStart();
            target = GdxTextEditState.nearestBoundary(value, start,
                    window.sourceEnd(), localX,
                    index -> nextComposerBoundary(value, index),
                    index -> composerWidth(value.substring(start, index)));
        } else {
            boolean masked = "password".equals(field.id);
            String display = masked
                    ? "\u2022".repeat(value.codePointCount(0, value.length()))
                    : value;
            FrontendInputWindow window = frontendInputWindow(uiFont, value,
                    display, field.bounds.width - 44f, true);
            int start = window.sourceStart();
            target = GdxTextEditState.nearestBoundary(value, start,
                    window.sourceEnd(), localX,
                    index -> value.offsetByCodePoints(index, 1),
                    index -> inputPrefixWidth(value, display, start, index));
        }
        textEdit.setCaret(value, target, extend);
        normalizeLobbyEmojiCaret(value, pointerX < field.bounds.x
                + field.bounds.width / 2f ? -1 : 1, extend);
    }

    private float inputPrefixWidth(String source, String display,
            int sourceStart, int sourceEnd) {
        if (source.equals(display)) {
            return textWidth(uiFont, display.substring(sourceStart,
                    sourceEnd));
        }
        int displayStart = source.codePointCount(0, sourceStart);
        int displayEnd = source.codePointCount(0, sourceEnd);
        return textWidth(uiFont, display.substring(displayStart, displayEnd));
    }

    private static boolean shiftPressed() {
        return Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
    }

    private void clearActiveField() {
        activeField = null;
        textEdit.blur();
        textDeleteRepeat.clear();
    }

    @Override
    public void dispose() {
        disposed = true;
        recoveryLoadGeneration++;
        autoSubmitRecovery = false;
        recoveryExecutor.shutdownNow();
        cancelLobbyVoiceRecording();
        clearLobbyMedia();
        submissions.cancel();
        closeLobbySubscription();
        if (lobbySession != null) {
            lobbySession.close();
            lobbySession = null;
        }
        batch.dispose();
        shapes.dispose();
        feltTexture.dispose();
        logo.dispose();
        avatarDefault.dispose();
        avatarBot.dispose();
        disposeSelectedAvatarTexture();
        soundIcon.dispose();
        muteIcon.dispose();
        talkIcon.dispose();
        soundEnabledCue.dispose();
        soundDisabledCue.dispose();
        participantJoinedCue.dispose();
        participantLeftCue.dispose();
        for (Sound cue : preferenceSoundCues.values()) cue.dispose();
        preferenceSoundCues.clear();
        backgroundMusic.stop();
        backgroundMusic.dispose();
        waitingRoomMusic.stop();
        waitingRoomMusic.dispose();
        for (Texture texture : lobbyAvatarTextures.values()) {
            texture.dispose();
        }
        lobbyAvatarTextures.clear();
        for (Texture texture : emojiTextures.values()) {
            texture.dispose();
        }
        emojiTextures.clear();
        avatarShader.dispose();
        titleFont.dispose();
        headingFont.dispose();
        actionFont.dispose();
        uiFont.dispose();
        smallFont.dispose();
        tinyFont.dispose();
    }

    @Override public boolean keyUp(int keycode) {
        textDeleteRepeat.release(keycode);
        if (keycode == Input.Keys.F9 && lobbyVoiceRecorder != null) {
            finishLobbyVoiceRecording(false);
            return true;
        }
        return false;
    }
    @Override public boolean touchCancelled(int x, int y, int p, int b) { return false; }
    @Override public boolean mouseMoved(int x, int y) {
        pointer.set(x, y);
        viewport.unproject(pointer);
        return false;
    }
    @Override
    public boolean scrolled(float amountX, float amountY) {
        if (surface == Surface.LOBBY && amountY != 0f
                && !lobbyImageMode && !lobbyEmojiPickerOpen) {
            pointer.set(Gdx.input.getX(), Gdx.input.getY());
            viewport.unproject(pointer);
            if (pointer.x >= 525f && pointer.x <= 1365f
                    && pointer.y >= 315f && pointer.y <= 744f) {
                int maximum = lobby == null ? 0
                        : Math.max(0, lobby.chat().size() - 1);
                lobbyChatScroll = CoronaPokerGdxTable
                        .anchoredScrollAfterWheel(lobbyChatScroll, maximum,
                                amountY);
                return true;
            }
        }
        if (surface == Surface.SETTINGS && amountY != 0f
                && settingsSession.section()
                == GdxSettingsContract.Section.DEBUG) {
            int maximum = Math.max(0,
                    debugLines().size() - SETTINGS_DEBUG_VISIBLE_LINES);
            // The console is anchored at the newest line when its offset is
            // zero. Keep the same natural wheel direction as the in-table
            // Debug tab and the game log: wheel-up travels into older output,
            // wheel-down returns towards the newest output.
            settingsDebugScroll = CoronaPokerGdxTable
                    .anchoredScrollAfterWheel(settingsDebugScroll, maximum,
                            amountY);
            return true;
        }
        return false;
    }

    private record TextItem(BitmapFont font, String text, float x, float y,
            Color color, boolean centered) {
    }

    private record LobbyAvatarItem(Texture texture, float x, float y,
            float size) {
    }

    private record UiImageItem(Texture texture, float x, float y,
            float width, float height) {
    }

    private record ComposerRun(String text, int emojiId, float width) {
    }

    private record ComposerWindow(String raw, int sourceStart, int sourceEnd,
            float caretOffset,
            float selectionOffset, float selectionWidth) {
    }

    private record FrontendInputWindow(String text, int sourceStart,
            int sourceEnd, float caretOffset,
            float selectionOffset, float selectionWidth) {
    }

    private static final class LobbyMedia {
        private boolean loading = true;
        private boolean failed;
        private Texture image;
        private GifTextureAnimation gif;

        private void dispose() {
            if (image != null) image.dispose();
            if (gif != null) gif.dispose();
            image = null;
            gif = null;
        }
    }

    private enum Surface {
        MENU, NEW_GAME, LOBBY, SETTINGS
    }

    private enum LobbyConfirmation {
        START, LEAVE
    }

    private enum PresetDialog {
        NONE, NAME, OVERWRITE, DELETE
    }

    private enum BlindStructureDialog {
        NONE, EDITOR, NAME_NEW, NAME_DUPLICATE, NAME_RENAME, DELETE
    }

    private enum ButtonTone {
        NEUTRAL, FEATURED, POSITIVE, DANGER
    }

    private record Hit(Rectangle bounds, Runnable action) {
    }

    private record TextFieldHit(String id, Rectangle bounds) {
    }

    private static final class EditMenu {

        private final Rectangle bounds;

        private EditMenu(float x, float y, float width, float height) {
            bounds = new Rectangle(x, y, width, height);
        }
    }
}
