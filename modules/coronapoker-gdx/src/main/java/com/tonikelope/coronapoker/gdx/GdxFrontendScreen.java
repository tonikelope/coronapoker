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
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.tonikelope.coronapoker.core.ApplicationMetadata;
import com.tonikelope.coronapoker.core.IdenticonFingerprint;
import com.tonikelope.coronapoker.core.IdentityTrustStore;
import com.tonikelope.coronapoker.core.LobbyChatMessage;
import com.tonikelope.coronapoker.core.LobbyCommand;
import com.tonikelope.coronapoker.core.LobbyParticipant;
import com.tonikelope.coronapoker.core.LobbySession;
import com.tonikelope.coronapoker.core.LobbySnapshot;
import com.tonikelope.coronapoker.core.AvatarImageValidator;
import com.tonikelope.coronapoker.core.NewGameConnectionDraft;
import com.tonikelope.coronapoker.core.NewGameSessionGateway;
import com.tonikelope.coronapoker.core.NewGameSubmissionCoordinator;
import com.tonikelope.coronapoker.core.NewGameTableDraft;
import com.tonikelope.coronapoker.core.PreferencesService;
import com.tonikelope.coronapoker.core.BlindStructureCatalog;
import com.tonikelope.coronapoker.core.BlindStructureRules;
import com.tonikelope.coronapoker.core.GamePresetCatalog;
import com.tonikelope.coronapoker.core.RecoverableGameRepository;
import com.tonikelope.coronapoker.core.StatsRepository;
import com.tonikelope.coronapoker.core.UpdateService;
import com.tonikelope.coronapoker.core.UpdaterService;
import com.tonikelope.coronapoker.DebugLog;
import org.dosse.upnp.UPnP;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.ZoneId;
import java.time.Instant;
import java.time.Duration;
import java.time.format.DateTimeFormatter;

/** Native menu and staged NewGameDialog replacement. */
final class GdxFrontendScreen extends ApplicationAdapter implements InputProcessor {

    private static final Logger LOGGER = Logger.getLogger(
            GdxFrontendScreen.class.getName());

    private static final float WIDTH = 1920f;
    private static final float HEIGHT = 1080f;
    static final List<String> NEW_GAME_PAGE_LABEL_KEYS = List.of(
            "gdx.settings.page.general",
            "newgame.grupo_ciegas",
            "newgame.grupo_compra",
            "newgame.grupo_partida",
            "newgame.grupo_bots",
            "gdx.newgame.profile_title");
    static final String PASSWORD_MASK_GLYPH = "\u2022";
    static final String FRONTEND_EXTRA_FONT_CHARACTERS = "♥♦♠♣"
            + PASSWORD_MASK_GLYPH;
    static final float MENU_LOGO_X = 42f;
    static final float MENU_LOGO_TOP = 32f;
    static final float MENU_LOGO_WIDTH = 320f;
    static final float MENU_SOUND_RIGHT_MARGIN = 35f;
    static final float MENU_SOUND_BOTTOM_MARGIN = 20f;
    static final float MENU_SOUND_SIZE = 40f;
    static final float MENU_QUOTE_SIDE_MARGIN = 24f;
    static final float MENU_QUOTE_MAX_WIDTH = WIDTH
            - 2f * MENU_QUOTE_SIDE_MARGIN;
    static final float MENU_QUOTE_SINGLE_BASELINE = 88f;
    static final float MENU_QUOTE_TWO_LINE_TOP_BASELINE = 112f;
    static final float MENU_QUOTE_LINE_HEIGHT = 24f;
    static final int MENU_QUOTE_MAX_LINES = 2;
    static final float LOBBY_LEFT_ACTION_X = 70f;
    static final float LOBBY_LEFT_ACTION_WIDTH = 360f;
    static final float LOBBY_LEFT_CONTENT_X = LOBBY_LEFT_ACTION_X;
    static final float LOBBY_LEFT_CONTENT_WIDTH = LOBBY_LEFT_ACTION_WIDTH;
    static final float LOBBY_CONNECTION_Y = 674f;
    static final float LOBBY_CONNECTION_HEIGHT = 82f;
    static final float LOBBY_PASSWORD_Y = 608f;
    static final float LOBBY_PASSWORD_HEIGHT = 48f;
    static final float LOBBY_GAME_INFO_Y = 454f;
    static final float LOBBY_GAME_INFO_HEIGHT = 138f;
    static final float LOBBY_BOT_BUTTON_Y = 370f;
    static final float LOBBY_BOT_BUTTON_HEIGHT = 62f;
    static final float LOBBY_KICK_BUTTON_Y = 292f;
    static final float LOBBY_KICK_BUTTON_HEIGHT = 62f;
    static final float LOBBY_PLAY_BUTTON_Y = 198f;
    static final float LOBBY_PLAY_BUTTON_HEIGHT = 76f;
    static final float LOBBY_ROSTER_TITLE_BASELINE = 795f;
    static final float LOBBY_ROSTER_COUNT_X = 1828f;
    static final float LOBBY_ROSTER_COUNT_BASELINE = 799f;
    static final float LOBBY_ROSTER_COUNT_WIDTH = 72f;
    private static final float MENU_REVEAL_SECONDS = 0.78f;
    private static final URI PUBLIC_ADDRESS_URI = URI.create(
            "https://api.ipify.org");
    private static final Duration PUBLIC_ADDRESS_CONNECT_TIMEOUT =
            Duration.ofMillis(1_200L);
    private static final Duration PUBLIC_ADDRESS_REQUEST_TIMEOUT =
            Duration.ofMillis(1_800L);
    private static final long PUBLIC_ADDRESS_OVERALL_TIMEOUT_MILLIS = 3_500L;
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
    private static final String ROUNDED_TEXTURE_FRAGMENT_SHADER = "#ifdef GL_ES\n"
            + "precision mediump float;\n"
            + "#endif\n"
            + "varying vec4 v_color;\n"
            + "varying vec2 v_texCoords;\n"
            + "uniform sampler2D u_texture;\n"
            + "uniform float u_cornerRadius;\n"
            + "uniform float u_edgeSoftness;\n"
            + "void main() {\n"
            + "    vec2 centered = abs(v_texCoords - vec2(0.5));\n"
            + "    vec2 corner = centered - (vec2(0.5) - vec2(u_cornerRadius));\n"
            + "    float edge = length(max(corner, vec2(0.0))) - u_cornerRadius;\n"
            + "    float mask = 1.0 - smoothstep(-u_edgeSoftness, u_edgeSoftness, edge);\n"
            + "    vec4 pixel = texture2D(u_texture, v_texCoords) * v_color;\n"
            + "    pixel.a *= mask;\n"
            + "    if (pixel.a <= 0.001) discard;\n"
            + "    gl_FragColor = pixel;\n"
            + "}\n";
    private static final Color BACKGROUND = new Color(0x031a14ff);
    static final int SCREEN_PANEL_RGBA = GdxSettingsStyle.PANEL_RGBA;
    private static final Color PANEL = new Color(SCREEN_PANEL_RGBA);
    private static final Color PANEL_LIGHT = new Color(
            GdxSettingsStyle.PANEL_LIGHT_RGBA);
    private static final Color CYAN = new Color(GdxSettingsStyle.CYAN_RGBA);
    private static final Color CYAN_DARK = new Color(
            GdxSettingsStyle.CYAN_DARK_RGBA);
    private static final Color GOLD = new Color(GdxSettingsStyle.GOLD_RGBA);
    private static final Color ORANGE = new Color(0xff6b27ff);
    private static final Color LINE = new Color(GdxSettingsStyle.LINE_RGBA);
    private static final Color MUTED = new Color(GdxSettingsStyle.MUTED_RGBA);
    private static final Color DISABLED = new Color(
            GdxSettingsStyle.DISABLED_RGBA);
    private static final Color POSITIVE_ICON = new Color(0xd8ffe0ff);
    private static final Color LATENCY_GREEN = new Color(0x4caf50ff);
    private static final Color LATENCY_YELLOW = new Color(0xffc107ff);
    private static final Color LATENCY_ORANGE = new Color(0xff9800ff);
    private static final Color LATENCY_RED = new Color(0xf44336ff);
    private static final Color LATENCY_STALE = new Color(0x9e9e9eff);
    private static final Color BOX_SHEEN_BOTTOM = new Color(
            1f, 1f, 1f, 0.018f);
    private static final Color BOX_SHEEN_TOP = new Color(
            1f, 1f, 1f, 0.115f);
    private static final Color BOX_SHADE_BOTTOM = new Color(
            0f, 0f, 0f, 0.11f);
    private static final Color BOX_SHADE_TOP = new Color(
            0f, 0f, 0f, 0.01f);
    private static final int EMOJI_COUNT = 1826;
    private static final int EMOJI_COLUMNS = 8;
    private static final int EMOJI_ROWS = 4;
    private static final int EMOJI_PAGE_SIZE = EMOJI_COLUMNS * EMOJI_ROWS;
    static final float LOBBY_EMOJI_PICKER_CELL_SIZE = 54f;
    static final float LOBBY_EMOJI_PICKER_IMAGE_SIZE = 32f;
    static final int LOBBY_IMAGE_GALLERY_CONTENT_DELAY_FRAMES = 1;
    private static final Pattern EMOJI_TOKEN = Pattern.compile("#([0-9]{1,4})#");
    private static final Pattern CHAT_WRAP_TOKEN = Pattern.compile(
            "#[0-9]{1,4}#|\\s+|[^\\s#]+|#");
    private static final int CHAT_TEXT_MAX_LINES = 8;
    private static final float VOICE_RECORD_MAX_SECONDS = 15f;
    private static final float VOICE_STATUS_SECONDS = 2.8f;
    private static final float VOICE_SENT_STATUS_SECONDS = 1.15f;
    private static final float INPUT_CARET_HALF_PERIOD_SECONDS = 0.50f;
    private static final float COMPOSER_EMOJI_SIZE = 32f;
    private static final float COMPOSER_EMOJI_ADVANCE = 36f;
    private static final DateTimeFormatter CHAT_TIME = DateTimeFormatter
            .ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter LOBBY_CLOCK = DateTimeFormatter
            .ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter STATS_TIME = DateTimeFormatter
            .ofPattern("dd-MM-yyyy HH:mm").withZone(ZoneId.systemDefault());
    static final float STATS_VIEW_HEADER_SEPARATOR_Y = 709f;
    static final float STATS_MODE_SELECTOR_Y = 665f;
    static final float STATS_MODE_SELECTOR_HEIGHT = 40f;
    static final float STATS_CONTENT_TOP = 655f;
    static final float STATS_PICKER_VIEW_BOTTOM = 190f;
    static final float STATS_PICKER_VIEW_TOP = 838f;
    static final float STATS_PICKER_ROW_HEIGHT = 58f;
    static final float STATS_PICKER_ROW_STRIDE = 67f;
    private static final float STATS_PICKER_SCROLL_SPEED = 3f
            * STATS_PICKER_ROW_STRIDE;
    static final int STATS_RESULT_VISIBLE_ROWS = 10;
    private static final URI GOOGLE_IMAGES_URI = URI.create(
            "https://images.google.com/");
    private static final float IMAGE_SEND_COOLDOWN_SECONDS = 2f;
    private static final float TEXT_SEND_COOLDOWN_SECONDS = 0.5f;
    private static final float ABOUT_LOGO_WIDTH = 180f;
    private static final float ABOUT_LOGO_Y = 748f;
    // About is a dedicated reading surface, not translucent glass over the
    // main menu. Its opaque, lighter slate keeps both copy and the original
    // black mourning ribbon legible without changing every other dialog.
    static final int ABOUT_PANEL_RGBA = 0x2b415cff;
    static final float ABOUT_MEMORIAL_CENTER_Y = 542f;
    static final float ABOUT_MOURNING_ICON_SIZE = 68f;
    static final float ABOUT_MUSIC_FIRST_LINE_Y = 448f;
    static final float ABOUT_MUSIC_LINE_GAP = 29f;
    static final float ABOUT_COPYRIGHT_Y = 326f;
    static final float ABOUT_CONTENT_WIDTH = 1060f;
    static final float HAND_GENERATOR_PANEL_X = 410f;
    static final float HAND_GENERATOR_PANEL_Y = 178f;
    static final float HAND_GENERATOR_PANEL_WIDTH = 1100f;
    static final float HAND_GENERATOR_PANEL_HEIGHT = 700f;
    static final float HAND_GENERATOR_CARD_AREA_INSET = 64f;
    static final float HAND_GENERATOR_CARD_Y = 338f;
    static final float HAND_GENERATOR_CARD_GAP = 16f;
    static final float HAND_GENERATOR_CARD_MAX_WIDTH = 190f;
    static final float HAND_GENERATOR_CARD_ASPECT = 0.714f;
    static final URI ABOUT_PROJECT_URI = URI.create(
            "https://github.com/tonikelope/coronapoker");
    static final URI ABOUT_RULES_URI = URI.create(
            "https://github.com/tonikelope/coronapoker/raw/master/robert_rules.pdf");
    private static final Color AUDIO_UNAVAILABLE_RED =
            new Color(0xef3340ff);

    private final FitViewport viewport = new FitViewport(WIDTH, HEIGHT);
    private final List<TextItem> texts = new ArrayList<>();
    private final List<LobbyAvatarItem> lobbyAvatars = new ArrayList<>();
    private final List<UiImageItem> uiImages = new ArrayList<>();
    private final List<TextItem> lobbyChatTexts = new ArrayList<>();
    private final List<LobbyAvatarItem> lobbyChatAvatars = new ArrayList<>();
    private final List<UiImageItem> lobbyChatImages = new ArrayList<>();
    private final List<LobbyChatBubbleItem> lobbyChatBubbles =
            new ArrayList<>();
    private final List<LobbyVoiceControlItem> lobbyVoiceControls =
            new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private final List<Hit> secondaryHits = new ArrayList<>();
    private final List<TextFieldHit> textFieldHits = new ArrayList<>();
    private final List<PasswordRevealHit> passwordRevealHits =
            new ArrayList<>();
    private final List<Hit> editMenuHits = new ArrayList<>();
    private final List<TooltipHit> tooltipHits = new ArrayList<>();
    private final GdxTooltipDelay tooltipDelay = new GdxTooltipDelay();
    private final Map<String, Texture> lobbyAvatarTextures = new HashMap<>();
    private final Map<String, Texture> handGeneratorCardTextures =
            new HashMap<>();
    private final Map<Integer, Texture> emojiTextures = new HashMap<>();
    private final Map<Long, LobbyMedia> lobbyMedia = new HashMap<>();
    private final GdxChatGalleryMedia lobbyHistoryMedia =
            new GdxChatGalleryMedia();
    private final Map<String, Float> toggleAnimations = new HashMap<>();
    private final Map<String, Float> hoverAnimations = new HashMap<>();
    private final GlyphLayout glyph = new GlyphLayout();
    private final Vector2 pointer = new Vector2();
    private final Matrix4 pixelProjection = new Matrix4();
    private final Matrix4 identityTransform = new Matrix4();
    private final Matrix4 italicTransform = new Matrix4();
    private final GdxTextEditState textEdit = new GdxTextEditState();
    private final GdxKeyRepeat textDeleteRepeat = new GdxKeyRepeat();
    private final GdxKeyRepeat pointerRepeat = new GdxKeyRepeat();
    private final Properties initialProperties;
    private final PreferencesService preferences;
    private final IdentityTrustStore identityTrust;
    private final SecureRandom secureRandom;
    private final GdxMenuQuotes menuQuotes;
    private final GdxAudioControl audioControl;
    private final GdxShortcutBindings shortcutBindings;
    private final GdxGamePresentationSettings presentationSettings;
    private final GdxGameText gameText;
    private final Consumer<String> languageChanged;
    private final UpdateService updateService;
    private final UpdaterService updaterService;
    private final NewGameSubmissionCoordinator submissions;
    private final RecoverableGameRepository recoverableGames;
    private final StatsRepository statsRepository;
    private final ExecutorService recoveryExecutor;
    private final ExecutorService statsExecutor;
    private final ExecutorService networkInfoExecutor;
    private final Consumer<NewGameSubmissionCoordinator.OpenedSession> sessionAccepted;
    private final Runnable sessionReturnedToMenu;
    private Runnable statsReturnAction;
    private NewGameConnectionDraft connection;
    private NewGameTableDraft table = new NewGameTableDraft();
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private Texture feltTexture;
    private boolean secretFeltTexture;
    private Texture logo;
    private Texture avatarDefault;
    private Texture avatarBot;
    private Texture selectedAvatarTexture;
    private Texture soundIcon;
    private Texture muteIcon;
    private Texture talkIcon;
    private Texture aboutMourningIcon;
    private Texture aboutBookIcon;
    private Texture aboutCrossIcon;
    private Texture aboutModIcon;
    private Sound soundEnabledCue;
    private Sound soundDisabledCue;
    private Sound participantJoinedCue;
    private Sound participantLeftCue;
    private final Map<String, Sound> preferenceSoundCues = new HashMap<>();
    private final Set<String> failedPreferenceSoundCues = new HashSet<>();
    private Music backgroundMusic;
    private Music waitingRoomMusic;
    private Music aboutMusic;
    private Music statsMusic;
    private ShaderProgram avatarShader;
    private ShaderProgram roundedTextureShader;
    private BitmapFont titleFont;
    private BitmapFont headingFont;
    private BitmapFont actionFont;
    private BitmapFont uiFont;
    private BitmapFont volumeOverlayFont;
    private BitmapFont smallFont;
    private BitmapFont tinyFont;
    private BitmapFont versionFont;
    private int page;
    private String activeField;
    private long toastUntil;
    private String toast = "";
    private float volumeOverlayUntil;
    private float elapsed;
    private float frameDelta;
    private float menuRevealStartedAt = Float.NaN;
    private boolean startupAudioHeld;
    private boolean tableAudioSuspended;
    private Hit pressedHit;
    private Hit pointerRepeatHit;
    private Surface pointerRepeatSurface;
    private EditMenu editMenu;
    private String pointerSelectionField;
    private String revealedPasswordField;
    private Surface surface;
    private int historyIndex = -1;
    private boolean disposed;
    private boolean avatarSelectionPending;
    private LobbySession lobbySession;
    private LobbySnapshot lobby;
    private AutoCloseable lobbySubscription;
    private String lobbyChatDraft = "";
    private long selectedLobbyChatSequence = -1L;
    private float lobbyTextSendAllowedAt;
    private float lobbyChatScroll;
    private int lobbyChatMessageCount;
    private float lobbyChatContentHeight;
    private final Rectangle lobbyChatScrollTrack = new Rectangle();
    private final Rectangle lobbyChatViewport = new Rectangle();
    private float lobbyChatScrollThumbHeight;
    private float lobbyChatScrollMaximum;
    private String lobbyImageDraft = "";
    private List<String> lobbyImageHistory = List.of();
    private float lobbyImageSendAllowedAt;
    private boolean lobbyEmojiPickerOpen;
    private boolean lobbyImageMode;
    private boolean lobbyImageClearConfirmation;
    private int lobbyImageGalleryContentDelayFrames;
    private int lobbyEmojiPage;
    private volatile GdxVoiceRecorder lobbyVoiceRecorder;
    private boolean lobbyVoiceOpening;
    private boolean lobbyVoiceLive;
    private boolean lobbyVoiceStopping;
    private float lobbyVoiceLiveAt;
    private String lobbyVoiceStatus = "";
    private float lobbyVoiceStatusAt;
    private float lobbyVoiceStatusSeconds = VOICE_STATUS_SECONDS;
    private long lobbyChatVoiceSequence = -1L;
    private boolean lobbyChatVoicePaused;
    private long lastLobbyMediaSequence = -1L;
    private String selectedParticipant;
    private FingerprintDialog fingerprintDialog;
    private boolean lobbyCommandPending;
    private boolean lobbyGameStarting;
    private LobbyConfirmation lobbyConfirmation;
    private boolean lobbyPasswordDialog;
    private String lobbyPasswordDraft = "";
    private String lobbyPublicAddress = "";
    private String cachedLobbyPublicAddress = "";
    private boolean lobbyPublicAddressLoading;
    private long lobbyPublicAddressGeneration;
    private PresetDialog presetDialog = PresetDialog.NONE;
    private final GdxBlindStructureEditor blindStructureEditor =
            new GdxBlindStructureEditor();
    private BlindStructureDialog blindStructureDialog =
            BlindStructureDialog.NONE;
    private String blindStructureNameDraft = "";
    private boolean blindStructureSettingsTarget;
    private List<GamePresetCatalog.Entry> gamePresets = List.of();
    private int selectedGamePreset = -1;
    private Dropdown dropdown = Dropdown.NONE;
    private int dropdownScroll;
    private List<String> hostAddressOptions =
            List.of(GdxLocalServerAddresses.LOOPBACK_NAME);
    private int hostAddressLoadGeneration;
    private String presetNameDraft = "";
    private Surface settingsReturnSurface = Surface.MENU;
    private final GdxSettingsSession settingsSession =
            new GdxSettingsSession();
    private int settingsAppearancePage;
    private float settingsAppearanceScroll;
    private int settingsAudioPage;
    private float settingsAudioScroll;
    private int settingsGamePage;
    private float settingsShortcutScroll;
    private float settingsDebugScroll;
    private int settingsDebugLineCount;
    private List<String> settingsDebugSourceCache = List.of();
    private List<GdxDebugLogFormatter.Line> settingsDebugVisualCache =
            List.of();
    private float settingsDebugWrapWidth = -1f;
    private final Rectangle settingsDebugScrollTrack = new Rectangle();
    private float settingsDebugScrollThumbHeight;
    private float settingsDebugScrollMaximum;
    private final Rectangle settingsDebugViewport = new Rectangle();
    private final Rectangle settingsRowScrollTrack = new Rectangle();
    private float settingsRowScrollThumbHeight;
    private float settingsRowScrollMaximum;
    private final List<TextItem> settingsDebugTexts = new ArrayList<>();
    private final List<TextItem> settingsRowTexts = new ArrayList<>();
    private final List<TextItem> statsChartTexts = new ArrayList<>();
    private final Rectangle settingsRowsClip = new Rectangle();
    private boolean settingsRowsActive;
    private ScrollDrag scrollDrag = ScrollDrag.NONE;
    private String settingsShortcutCaptureId;
    private String settingsShortcutStatus = "";
    private GdxWindowMode settingsOpenedWindowMode = GdxWindowMode.BORDERLESS;
    private int settingsOpenedMsaaSamples;
    private NewGameTableDraft settingsTable;
    private NewGameTableDraft.Settings settingsTableSnapshot;
    private boolean settingsDiscardConfirmation;
    private boolean settingsRestartNotice;
    private final GdxVoiceNoteLibrary voiceNoteLibrary =
            new GdxVoiceNoteLibrary();
    private final GdxAudioPreview audioPreview = new GdxAudioPreview();
    private boolean voiceNotesOpen;
    private boolean voiceNotesLoading;
    private List<GdxVoiceNoteLibrary.Entry> voiceNotes = List.of();
    private int voiceNotesPage;
    private GdxVoiceNoteLibrary.Entry voiceNotePlaying;
    private GdxVoiceNoteLibrary.Entry voiceNoteDeleteConfirmation;
    private boolean voiceNotesPurgeConfirmation;
    private boolean aboutOpen;
    private boolean handGeneratorOpen;
    private int aboutEasterEggClicks;
    private Texture aboutEasterEggTexture;
    private final GdxHandGeneratorModel handGenerator =
            new GdxHandGeneratorModel();
    private boolean updateCheckInFlight;
    private boolean updateInstalling;
    private UpdateService.CheckResult updateResult;
    private boolean updatePromptOpen;
    private boolean updatePromptDismissed;
    private boolean updatePromptForMod;
    private boolean updateReturnToAbout;
    private boolean startupMenuWaitingForUpdate;
    private boolean startupMenuDeferredByUpdate;
    private boolean startupMenuWasSkipped;
    private boolean modUpdateCheckInFlight;
    private GdxModUpdateChecker.Result modUpdateResult;
    private String aboutModUpdateStatusKey = "";
    private long aboutModUpdateStatusUntil;
    private long recoveryLoadGeneration;
    private boolean autoSubmitRecovery;
    private List<StatsRepository.GameSummary> statsAllGames = List.of();
    private List<StatsRepository.GameSummary> statsGames = List.of();
    private List<String> statsPlayers = List.of();
    private String statsPlayerFilter = "";
    private List<StatsRepository.HandSummary> statsHands = List.of();
    private List<StatsRepository.BalanceRow> statsBalances = List.of();
    private List<StatsRepository.BalancePoint> statsBalanceHistory = List.of();
    private List<StatsRepository.ShowdownRow> statsShowdown = List.of();
    private List<StatsRepository.MetricRow> statsMetrics = List.of();
    private List<StatsRepository.PerformanceRow> statsPerformance = List.of();
    private List<StatsRepository.BestHandRow> statsBestHands = List.of();
    private int statsGameIndex = -1;
    private int statsHandIndex = -1;
    private StatsMode statsMode = StatsMode.BALANCE;
    private long statsLoadGeneration;
    private boolean statsLoading;
    private String statsError = "";
    private StatsConfirmation statsConfirmation = StatsConfirmation.NONE;
    private StatsPicker statsPicker = StatsPicker.NONE;
    private boolean statsSyncExclusionsOpen;
    private boolean statsExcludePrivateDraft;
    private boolean statsExcludeNicksEnabledDraft;
    private String statsExcludeNicksDraft = "";
    private float statsPickerScroll;
    private float statsPickerScrollTarget;
    private float statsPickerScrollMaximum;
    private final Rectangle statsPickerScrollTrack = new Rectangle();
    private float statsPickerScrollThumbHeight;
    private float statsResultScroll;
    private float statsResultScrollTarget;
    private float statsResultScrollMaximum;
    private final Rectangle statsResultScrollTrack = new Rectangle();
    private float statsResultScrollThumbHeight;
    private float statsSummaryPlayersScroll;
    private float statsSummaryPlayersScrollTarget;
    private float statsChartZoom = 1f;
    private float statsChartPanX = 0.5f;
    private float statsChartPanY = 0.5f;
    private float statsChartPanTargetX = 0.5f;
    private float statsChartPanTargetY = 0.5f;
    private final Rectangle statsChartViewport = new Rectangle();
    private final Rectangle statsChartHorizontalTrack = new Rectangle();
    private final Rectangle statsChartVerticalTrack = new Rectangle();
    private float statsChartHorizontalThumbWidth;
    private float statsChartVerticalThumbHeight;
    private boolean statsChartCanvasActive;
    private int statsSortColumn = -1;
    private boolean statsSortAscending = true;
    private StatsMode statsSortMode = StatsMode.BALANCE;
    private boolean statsSortHandScope;
    private Surface screenshotReturnSurface = Surface.MENU;
    private List<GdxScreenshotStore.Shot> screenshots = List.of();
    private int screenshotIndex;
    private Texture screenshotTexture;
    private String screenshotError = "";
    private String screenshotToast = "";
    private float screenshotToastUntil;
    private boolean screenshotOperationPending;
    private boolean screenshotDeleteConfirmation;
    private long screenshotGeneration;

    GdxFrontendScreen(PreferencesService preferences,
            NewGameSessionGateway gateway, RecoverableGameRepository recoverableGames,
            IdentityTrustStore identityTrust, SecureRandom secureRandom,
            Consumer<NewGameSubmissionCoordinator.OpenedSession> sessionAccepted,
            Runnable sessionReturnedToMenu,
            GdxGamePresentationSettings presentationSettings,
            GdxGameText gameText, Consumer<String> languageChanged,
            UpdateService updateService, UpdaterService updaterService,
            StatsRepository statsRepository) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.identityTrust = Objects.requireNonNull(identityTrust,
                "identityTrust");
        this.secureRandom = Objects.requireNonNull(secureRandom,
                "secureRandom");
        menuQuotes = new GdxMenuQuotes(this.secureRandom);
        initialProperties = this.preferences.properties();
        if (GdxSettingsContract.migrateLegacyChatNotificationPreference(
                initialProperties)) {
            this.preferences.saveDeferred();
        }
        this.connection = defaultConnection(initialProperties,
                NewGameConnectionDraft.Mode.CREATE);
        audioControl = new GdxAudioControl(initialProperties, this.preferences);
        shortcutBindings = new GdxShortcutBindings(initialProperties);
        submissions = new NewGameSubmissionCoordinator(this.preferences,
                Objects.requireNonNull(gateway, "gateway"));
        this.recoverableGames = Objects.requireNonNull(recoverableGames,
                "recoverableGames");
        this.statsRepository = Objects.requireNonNull(statsRepository,
                "statsRepository");
        recoveryExecutor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task,
                    "CoronaPoker-GDX-recovery-loader");
            thread.setDaemon(true);
            return thread;
        });
        statsExecutor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "CoronaPoker-GDX-stats-loader");
            thread.setDaemon(true);
            return thread;
        });
        networkInfoExecutor = Executors.newFixedThreadPool(2, task -> {
            Thread thread = new Thread(task,
                    "CoronaPoker-GDX-public-address-loader");
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
        this.updateService = Objects.requireNonNull(updateService,
                "updateService");
        this.updaterService = Objects.requireNonNull(updaterService,
                "updaterService");
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
        feltTexture = loadFeltTexture();
        logo = new Texture(Gdx.files.internal("images/corona_poker_splash.png"));
        logo.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        avatarDefault = filteredTexture("images/avatar_default.png");
        avatarBot = filteredTexture("images/avatar_bot.png");
        soundIcon = filteredTexture("images/sound.png");
        muteIcon = filteredTexture("images/mute.png");
        talkIcon = filteredTexture("images/talk.png");
        aboutMourningIcon = filteredTexture("images/luto.png");
        aboutBookIcon = silhouetteTexture("images/open-book.png",
                new Color(0xeaf8ffff));
        aboutCrossIcon = filteredTexture("images/cruz.png");
        aboutModIcon = externalTexture(
                presentationSettings.modAsset("mod.png").orElse(null));
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
        aboutMusic = music("sounds/misc/about_music.mp3", 0.90f);
        statsMusic = music("sounds/misc/stats_music.mp3", 0.30f);
        avatarShader = new ShaderProgram(SPRITE_VERTEX_SHADER,
                AVATAR_FRAGMENT_SHADER);
        if (!avatarShader.isCompiled()) {
            throw new IllegalStateException("Avatar shader: "
                    + avatarShader.getLog());
        }
        roundedTextureShader = new ShaderProgram(SPRITE_VERTEX_SHADER,
                ROUNDED_TEXTURE_FRAGMENT_SHADER);
        if (!roundedTextureShader.isCompiled()) {
            throw new IllegalStateException("Rounded texture shader: "
                    + roundedTextureShader.getLog());
        }
        checkForUpdates();
        purgeExpiredVoiceNotes();
        FreeTypeFontGenerator titleGenerator = new FreeTypeFontGenerator(
                Gdx.files.internal("fonts/McLaren-Regular.ttf"));
        titleFont = font(titleGenerator, GdxSettingsStyle.TITLE_FONT_SIZE,
                GdxSettingsStyle.TITLE_FONT_BORDER);
        titleGenerator.dispose();
        FreeTypeFontGenerator displayGenerator = new FreeTypeFontGenerator(
                Gdx.files.internal("fonts/McLaren-Regular.ttf"));
        headingFont = font(displayGenerator, GdxSettingsStyle.HEADING_FONT_SIZE,
                GdxSettingsStyle.HEADING_FONT_BORDER);
        actionFont = font(displayGenerator, GdxSettingsStyle.ACTION_FONT_SIZE,
                GdxSettingsStyle.ACTION_FONT_BORDER);
        displayGenerator.dispose();
        FreeTypeFontGenerator bodyGenerator = new FreeTypeFontGenerator(
                Gdx.files.internal("fonts/McLaren-Regular.ttf"));
        uiFont = font(bodyGenerator, GdxSettingsStyle.BODY_FONT_SIZE, 0f);
        // The volume feedback is a global control, so it must retain the same
        // high-contrast McLaren face used over the busy table felt on every
        // frontend surface too (menu, lobby, settings and statistics).
        volumeOverlayFont = font(bodyGenerator,
                GdxVolumeOverlayStyle.FONT_SIZE,
                GdxVolumeOverlayStyle.FONT_BORDER);
        smallFont = font(bodyGenerator, GdxSettingsStyle.SMALL_FONT_SIZE, 0f);
        tinyFont = font(bodyGenerator, GdxSettingsStyle.TINY_FONT_SIZE, 0f);
        versionFont = font(bodyGenerator, GdxProductVersionBrand.FONT_SIZE, 0f);
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
        Texture replacement = loadFeltTexture();
        Texture previous = feltTexture;
        feltTexture = replacement;
        previous.dispose();
    }

    private Texture loadFeltTexture() {
        if (presentationSettings.secretFelt()) {
            try {
                Texture secret = GdxSecretFelt.texture();
                secretFeltTexture = true;
                return secret;
            } catch (Exception failure) {
                LOGGER.log(Level.WARNING,
                        "Unable to decode the original secret felt", failure);
            }
        }
        secretFeltTexture = false;
        Texture normal = new Texture(Gdx.files.internal("images/tapete_"
                + presentationSettings.felt() + ".jpg"));
        normal.setFilter(TextureFilter.Linear, TextureFilter.Nearest);
        normal.setWrap(TextureWrap.Repeat, TextureWrap.Repeat);
        return normal;
    }

    private void drawFelt(float width, float height) {
        if (secretFeltTexture) {
            batch.draw(feltTexture, 0f, 0f, width, height);
        } else {
            batch.draw(feltTexture, 0f, 0f, width, height,
                    0f, 0f, width / feltTexture.getWidth(),
                    height / feltTexture.getHeight());
        }
    }

    private static BitmapFont font(FreeTypeFontGenerator generator, int size,
            float border) {
        FreeTypeFontParameter p = new FreeTypeFontParameter();
        p.size = size * 2;
        p.color = Color.WHITE;
        p.borderColor = new Color(0x02050ccc);
        p.borderWidth = border * 2f;
        p.hinting = FreeTypeFontGenerator.Hinting.Full;
        p.kerning = true;
        p.characters += FRONTEND_EXTRA_FONT_CHARACTERS;
        p.minFilter = TextureFilter.Linear;
        p.magFilter = TextureFilter.Linear;
        BitmapFont result = generator.generateFont(p);
        result.getData().setScale(0.5f);
        return result;
    }

    @Override
    public void render() {
        frameDelta = Math.min(Gdx.graphics.getDeltaTime(), 1f / 20f);
        elapsed += frameDelta;
        statsPickerScroll += (statsPickerScrollTarget - statsPickerScroll)
                * Math.min(1f, frameDelta * 14f);
        if (Math.abs(statsPickerScrollTarget - statsPickerScroll) < 0.1f) {
            statsPickerScroll = statsPickerScrollTarget;
        }
        statsResultScroll += (statsResultScrollTarget - statsResultScroll)
                * Math.min(1f, frameDelta * 14f);
        if (Math.abs(statsResultScrollTarget - statsResultScroll) < 0.1f) {
            statsResultScroll = statsResultScrollTarget;
        }
        statsSummaryPlayersScroll += (statsSummaryPlayersScrollTarget
                - statsSummaryPlayersScroll)
                * Math.min(1f, frameDelta * 14f);
        if (Math.abs(statsSummaryPlayersScrollTarget
                - statsSummaryPlayersScroll) < 0.1f) {
            statsSummaryPlayersScroll = statsSummaryPlayersScrollTarget;
        }
        statsChartPanX += (statsChartPanTargetX - statsChartPanX)
                * Math.min(1f, frameDelta * 14f);
        statsChartPanY += (statsChartPanTargetY - statsChartPanY)
                * Math.min(1f, frameDelta * 14f);
        resolveStartupUpdateGate();
        if (surface == Surface.MENU
                && !startupMenuWaitingForUpdate
                && !startupMenuDeferredByUpdate
                && !updatePromptDismissed && !updatePromptOpen
                && updateResult != null
                && updateResult.status()
                        == UpdateService.Status.UPDATE_AVAILABLE) {
            updatePromptForMod = false;
            updateReturnToAbout = false;
            updatePromptOpen = true;
            captureFrontendModalInput();
        }
        updateTextDeleteRepeat();
        updatePointerRepeat();
        updateLobbyVoiceRecording();
        audioPreview.update(frameDelta);
        syncMusicForSurface();
        ScreenUtils.clear(BACKGROUND);
        viewport.apply();
        viewport.getCamera().update();
        shapes.setProjectionMatrix(viewport.getCamera().combined);
        batch.setProjectionMatrix(viewport.getCamera().combined);
        texts.clear();
        lobbyAvatars.clear();
        uiImages.clear();
        lobbyChatTexts.clear();
        lobbyChatAvatars.clear();
        lobbyChatImages.clear();
        lobbyChatBubbles.clear();
        lobbyVoiceControls.clear();
        settingsDebugTexts.clear();
        settingsRowTexts.clear();
        statsChartTexts.clear();
        statsChartViewport.set(0f, 0f, 0f, 0f);
        statsChartHorizontalTrack.set(0f, 0f, 0f, 0f);
        statsChartVerticalTrack.set(0f, 0f, 0f, 0f);
        statsChartCanvasActive = false;
        settingsRowsClip.set(0f, 0f, 0f, 0f);
        settingsRowsActive = false;
        settingsDebugViewport.set(0f, 0f, 0f, 0f);
        // The clipped chat layer belongs only to the message history. Clear
        // its previous-frame viewport before drawing a gallery or emoji
        // picker, otherwise that stale layer is composed over the dialog.
        lobbyChatViewport.set(0f, 0f, 0f, 0f);
        hits.clear();
        secondaryHits.clear();
        textFieldHits.clear();
        passwordRevealHits.clear();
        editMenuHits.clear();
        tooltipHits.clear();

        // A modal owns pointer feedback as well as clicks. The interaction
        // maps were already replaced for the foreground pass, but the covered
        // page was still painted first with the live pointer and therefore
        // showed hover states through the dialog.
        boolean shieldBackgroundPointer = hasBlockingFrontendModal();
        float livePointerX = pointer.x;
        float livePointerY = pointer.y;
        if (shieldBackgroundPointer) pointer.set(-10_000f, -10_000f);

        drawFeltBackground();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (surface == Surface.MENU) {
            if (renderMainMenuContent(aboutOpen, updatePromptOpen)) {
                drawMainMenu();
            }
        } else if (surface == Surface.LOBBY) {
            drawLobby();
        } else if (surface == Surface.SETTINGS) {
            drawSettingsScreen();
        } else if (surface == Surface.STATS) {
            drawStatsScreen();
        } else if (surface == Surface.SCREENSHOTS) {
            drawScreenshotViewer();
        } else {
            drawNewGameDialogFrame();
            drawHeader();
            drawProgress();
            switch (page) {
                case 0 -> drawIdentityPage();
                case 1 -> drawBlindsPage();
                case 2 -> drawPurchasePage();
                case 3 -> drawGamePage();
                case 4 -> drawBotsPage();
                default -> drawProfilePage();
            }
            drawFooter();
        }
        drawFrontendVersionLabel();
        if (System.currentTimeMillis() < toastUntil) {
            drawToast();
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
            batch.setColor(item.tint);
            batch.draw(item.texture, item.x, item.y, item.width, item.height);
        }
        batch.setColor(Color.WHITE);
        for (TextItem item : texts) {
            drawTextItem(item);
        }
        batch.end();

        if (shieldBackgroundPointer) pointer.set(livePointerX, livePointerY);

        drawSettingsRowsTextLayer();
        drawStatsChartTextLayer();
        drawLobbyChatLayer();
        drawSettingsDebugLayer();

        drawStartupMenuReveal();

        if (dropdown == Dropdown.NONE && !hasBlockingFrontendModal()) {
            drawTooltipTopLayer();
        } else {
            tooltipDelay.clear();
        }

        // Modal surfaces must be composed after every underlying glyph. Texts
        // are batched separately from shapes, so drawing the modal inside
        // drawLobby would otherwise let the lobby chat glyphs bleed over it.
        if (settingsRestartNotice
                || (surface == Surface.MENU && (aboutOpen || updatePromptOpen))
                || (surface == Surface.LOBBY
                && (lobbyConfirmation != null || lobbyPasswordDialog
                        || lobbyImageClearConfirmation
                        || lobbyTableTransitionActive(lobbyGameStarting, lobby)
                        || fingerprintDialog != null))
                || (surface == Surface.SETTINGS
                && (settingsDiscardConfirmation
                        || voiceNotesOpen
                        || blindStructureDialog
                                != BlindStructureDialog.NONE))
                || (surface == Surface.NEW_GAME
                && (submissions.submitting()
                        || dropdown != Dropdown.NONE
                        || presetDialog != PresetDialog.NONE
                        || blindStructureDialog != BlindStructureDialog.NONE))
                || (surface == Surface.STATS
                && (statsConfirmation != StatsConfirmation.NONE
                        || statsPicker != StatsPicker.NONE
                        || statsSyncExclusionsOpen))
                || (surface == Surface.SCREENSHOTS
                        && screenshotDeleteConfirmation)) {
            texts.clear();
            // A visual modal must also own the complete interaction map.
            // Keeping the underlying page hits allowed invisible lobby/menu
            // controls to fire through confirmations and the table-loading
            // overlay.
            hits.clear();
            secondaryHits.clear();
            textFieldHits.clear();
            passwordRevealHits.clear();
            editMenuHits.clear();
            // SpriteBatch changes the current OpenGL pipeline. Restore alpha
            // blending before composing the modal shape pass; otherwise the
            // glass highlights/shadows (and the dimmer itself) become fully
            // opaque white/black bars on some drivers.
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA,
                    GL20.GL_ONE_MINUS_SRC_ALPHA);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            if (settingsRestartNotice) {
                drawSettingsRestartNotice();
            } else if (aboutOpen) {
                drawAboutDialog();
            } else if (updatePromptOpen) {
                drawUpdateDialog();
            } else if (blindStructureDialog != BlindStructureDialog.NONE) {
                drawBlindStructureDialog();
            } else if (voiceNotesOpen) {
                drawVoiceNotesDialog();
            } else if (surface == Surface.SETTINGS) {
                drawSettingsDiscardConfirmation();
            } else if (surface == Surface.NEW_GAME) {
                if (submissions.submitting()) {
                    drawNewGameSubmissionOverlay();
                } else if (dropdown != Dropdown.NONE) {
                    drawDropdownOverlay();
                } else if (blindStructureDialog != BlindStructureDialog.NONE) {
                    drawBlindStructureDialog();
                } else {
                    drawPresetDialog();
                }
            } else if (surface == Surface.STATS) {
                if (statsSyncExclusionsOpen) {
                    drawStatsSyncExclusions();
                } else if (statsConfirmation != StatsConfirmation.NONE) {
                    drawStatsConfirmation();
                } else {
                    drawStatsPicker();
                }
            } else if (surface == Surface.SCREENSHOTS) {
                drawScreenshotDeleteConfirmation();
            } else if (lobbyPasswordDialog) {
                drawLobbyPasswordDialog();
            } else if (fingerprintDialog != null) {
                drawFingerprintDialog();
            } else if (lobbyConfirmation != null) {
                drawLobbyConfirmation();
            } else if (lobbyImageClearConfirmation) {
                drawLobbyImageClearConfirmation();
            } else {
                drawLobbyLoadingOverlay();
            }
            shapes.end();
            batch.begin();
            if (aboutOpen && handGeneratorOpen) {
                drawHandGeneratorCards();
            } else if (aboutOpen && aboutEasterEggTexture == null) {
                float logoHeight = ABOUT_LOGO_WIDTH * logo.getHeight()
                        / logo.getWidth();
                float logoX = aboutModIcon == null
                        ? WIDTH / 2f - ABOUT_LOGO_WIDTH / 2f
                        : WIDTH / 2f - ABOUT_LOGO_WIDTH - 16f;
                batch.setColor(Color.WHITE);
                batch.draw(logo, logoX,
                        ABOUT_LOGO_Y, ABOUT_LOGO_WIDTH, logoHeight);
                if (aboutModIcon != null) {
                    Rectangle modBounds = aboutModIconBounds(logoHeight);
                    batch.draw(aboutModIcon, modBounds.x, modBounds.y,
                            modBounds.width, modBounds.height);
                }
                // Keep the mourning ribbon visually attached to the memorial
                // line, as in the original Swing composition. The previous
                // far-left placement looked like an unrelated control.
                batch.draw(aboutMourningIcon, WIDTH / 2f - 438f,
                        ABOUT_MEMORIAL_CENTER_Y
                                - ABOUT_MOURNING_ICON_SIZE / 2f,
                        ABOUT_MOURNING_ICON_SIZE,
                        ABOUT_MOURNING_ICON_SIZE);
                batch.draw(aboutBookIcon, WIDTH / 2f - 21f, 244f,
                        42f, 42f);
                batch.draw(aboutCrossIcon, WIDTH / 2f - 338f, 214f,
                        23f, 15f);
            } else if (aboutEasterEggTexture != null) {
                int backBufferWidth = Gdx.graphics.getBackBufferWidth();
                int backBufferHeight = Gdx.graphics.getBackBufferHeight();
                float scale = nativeImageScale(
                        aboutEasterEggTexture.getWidth(),
                        aboutEasterEggTexture.getHeight(),
                        backBufferWidth, backBufferHeight, 48);
                float imageWidth = aboutEasterEggTexture.getWidth() * scale;
                float imageHeight = aboutEasterEggTexture.getHeight() * scale;
                // Bypass FitViewport here: one source pixel must be one
                // physical back-buffer pixel whenever the image fits.
                batch.setProjectionMatrix(pixelProjection.setToOrtho2D(
                        0f, 0f, backBufferWidth, backBufferHeight));
                batch.setColor(Color.WHITE);
                batch.draw(aboutEasterEggTexture,
                        (backBufferWidth - imageWidth) / 2f,
                        (backBufferHeight - imageHeight) / 2f,
                        imageWidth, imageHeight);
                batch.setProjectionMatrix(viewport.getCamera().combined);
            }
            for (TextItem item : texts) {
                drawTextItem(item);
            }
            batch.end();
        }
        if (shouldDrawEditMenu()) drawEditMenuTopLayer();
        // Transient feedback must be the final composited layer. Drawing only
        // its shapes in the first pass lets queued page images cover the panel.
        if (surface == Surface.SCREENSHOTS
                && !screenshotDeleteConfirmation
                && !screenshotToast.isBlank()
                && elapsed < screenshotToastUntil) {
            drawScreenshotToastTopLayer();
        }
        if (elapsed < volumeOverlayUntil) drawVolumeOverlayTopLayer();
    }

    private boolean hasBlockingFrontendModal() {
        return startupMenuWaitingForUpdate
                || settingsRestartNotice || aboutOpen || updatePromptOpen
                || lobbyConfirmation != null || lobbyPasswordDialog
                || lobbyImageClearConfirmation
                || fingerprintDialog != null
                || lobbyTableTransitionActive(lobbyGameStarting, lobby)
                || submissions.submitting()
                || dropdown != Dropdown.NONE
                || presetDialog != PresetDialog.NONE
                || blindStructureDialog != BlindStructureDialog.NONE
                || settingsDiscardConfirmation || voiceNotesOpen
                || statsConfirmation != StatsConfirmation.NONE
                || statsPicker != StatsPicker.NONE
                || statsSyncExclusionsOpen
                || screenshotDeleteConfirmation;
    }

    void beginStartupReveal() {
        prepareInitialMenu(false);
    }

    void completeStartupReveal() {
        prepareInitialMenu(true);
    }

    private void prepareInitialMenu(boolean skipped) {
        startupMenuWasSkipped = skipped;
        if (updateCheckInFlight && updateResult == null) {
            startupMenuWaitingForUpdate = true;
            menuRevealStartedAt = Float.POSITIVE_INFINITY;
            captureFrontendModalInput();
            return;
        }
        finishStartupUpdateGate();
    }

    private void resolveStartupUpdateGate() {
        if (!startupMenuWaitingForUpdate
                || updateCheckInFlight && updateResult == null) return;
        finishStartupUpdateGate();
    }

    private void finishStartupUpdateGate() {
        startupMenuWaitingForUpdate = false;
        if (!updatePromptDismissed && updateResult != null
                && updateResult.status()
                        == UpdateService.Status.UPDATE_AVAILABLE) {
            startupMenuDeferredByUpdate = true;
            menuRevealStartedAt = Float.POSITIVE_INFINITY;
            updatePromptForMod = false;
            updateReturnToAbout = false;
            updatePromptOpen = true;
            captureFrontendModalInput();
            return;
        }
        startupMenuDeferredByUpdate = false;
        menuRevealStartedAt = startupMenuWasSkipped
                ? Float.NaN : elapsed;
        pressedHit = null;
    }

    void openLobby(LobbySession session) {
        stopLobbyTransientAudio();
        closeLobbySubscription();
        lobbySession = Objects.requireNonNull(session, "session");
        lobby = session.snapshot();
        lobbyChatDraft = "";
        selectedLobbyChatSequence = -1L;
        lobbyTextSendAllowedAt = 0f;
        lobbyChatScroll = 0;
        lobbyChatMessageCount = lobby.chat().size();
        lobbyChatContentHeight = 0f;
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
        fingerprintDialog = null;
        lobbyPasswordDialog = false;
        lobbyPasswordDraft = "";
        loadLobbyPublicAddress(lobby.host());
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

    private void loadLobbyPublicAddress(boolean host) {
        long generation = ++lobbyPublicAddressGeneration;
        lobbyPublicAddress = host ? cachedLobbyPublicAddress : "";
        lobbyPublicAddressLoading = host && lobbyPublicAddress.isBlank();
        if (!host) return;
        if (!lobbyPublicAddress.isBlank()) return;
        resolveLobbyPublicAddress().whenComplete((address, failure) ->
                Gdx.app.postRunnable(() -> {
                    if (disposed || generation != lobbyPublicAddressGeneration) {
                        return;
                    }
                    lobbyPublicAddressLoading = false;
                    lobbyPublicAddress = failure == null
                            ? Objects.requireNonNullElse(address, "").trim()
                            : "";
                    if (!lobbyPublicAddress.isBlank()) {
                        cachedLobbyPublicAddress = lobbyPublicAddress;
                    }
                }));
    }

    private CompletableFuture<String> resolveLobbyPublicAddress() {
        CompletableFuture<String> resolved = new CompletableFuture<>();
        AtomicInteger pending = new AtomicInteger(2);
        Consumer<String> candidateConsumer = candidate -> {
            String normalized = normalizePublicAddress(candidate);
            if (!normalized.isBlank()) {
                resolved.complete(normalized);
            } else if (pending.decrementAndGet() == 0) {
                resolved.complete("");
            }
        };
        CompletableFuture.supplyAsync(this::publicAddressViaHttps,
                networkInfoExecutor).whenComplete((candidate, failure) -> {
                    if (failure != null) {
                        LOGGER.log(Level.FINE,
                                "Unable to obtain the public address through HTTPS",
                                failure);
                    }
                    candidateConsumer.accept(failure == null ? candidate : "");
                });
        CompletableFuture.supplyAsync(this::publicAddressViaUpnp,
                networkInfoExecutor).whenComplete((candidate, failure) -> {
                    if (failure != null) {
                        LOGGER.log(Level.FINE,
                                "Unable to obtain the public address through UPnP",
                                failure);
                    }
                    candidateConsumer.accept(failure == null ? candidate : "");
                });
        return resolved.completeOnTimeout("",
                PUBLIC_ADDRESS_OVERALL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    private String publicAddressViaHttps() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(PUBLIC_ADDRESS_CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(PUBLIC_ADDRESS_URI)
                .timeout(PUBLIC_ADDRESS_REQUEST_TIMEOUT)
                .header("Accept", "text/plain")
                .header("User-Agent", "CoronaPoker/" + ApplicationMetadata.VERSION)
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 ? response.body() : "";
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return "";
        } catch (IOException failure) {
            return "";
        }
    }

    private String publicAddressViaUpnp() {
        return Objects.requireNonNullElse(UPnP.getExternalIP(), "");
    }

    static String normalizePublicAddress(String address) {
        String candidate = Objects.requireNonNullElse(address, "").trim();
        if (candidate.isBlank() || candidate.length() > 64
                || !candidate.matches("[0-9A-Fa-f:.]+")
                || (!candidate.contains(".") && !candidate.contains(":"))) {
            return "";
        }
        try {
            InetAddress parsed = InetAddress.getByName(candidate);
            if (parsed.isAnyLocalAddress() || parsed.isLoopbackAddress()
                    || parsed.isLinkLocalAddress() || parsed.isSiteLocalAddress()
                    || parsed.isMulticastAddress()) {
                return "";
            }
            return candidate;
        } catch (IOException invalidAddress) {
            return "";
        }
    }

    private void playLobbyRosterChange(LobbySnapshot previous,
            LobbySnapshot next) {
        LobbyRosterChange change = lobbyRosterChange(previous, next);
        if (!audioOutputAvailable() || !audioControl.enabled()
                || !preferenceBoolean("sonido_efectos", true)) {
            return;
        }
        float volume = masterVolume();
        try {
            if (change.joined()
                    && preferenceBoolean("sonido_entra", true)) {
                participantJoinedCue.play(volume);
            }
            if (change.left()
                    && preferenceBoolean("sonido_sale", true)) {
                participantLeftCue.play(volume);
            }
        } catch (RuntimeException unavailable) {
            // Output may disappear between the central poll and this event.
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

    static boolean lobbyTableTransitionActive(boolean locallyStarting,
            LobbySnapshot state) {
        return locallyStarting || state != null && state.startingOrStarted();
    }

    void showSessionError(String detail) {
        lobbyGameStarting = false;
        String language = presentationSettings.language();
        String localized = "en".equalsIgnoreCase(language) ? "en" : "es";
        playPreferenceSound("misc/network_error_" + localized + ".wav",
                "sonido_error_red", 0.88f);
        showToast(detail == null || detail.isBlank()
                ? gameText.translate("gdx.table.open_failed") : detail);
    }

    private void drawFeltBackground() {
        batch.begin();
        batch.setColor(Color.WHITE);
        drawFelt(WIDTH, HEIGHT);
        // The logo is part of the fixed frontend background. Keeping it here,
        // rather than in selected screens, prevents it from jumping or
        // disappearing while navigating to statistics or table setup.
        float logoWidth = MENU_LOGO_WIDTH;
        float logoHeight = logoWidth * logo.getHeight() / logo.getWidth();
        batch.draw(logo, MENU_LOGO_X,
                HEIGHT - MENU_LOGO_TOP - logoHeight,
                logoWidth, logoHeight);
        batch.setColor(Color.WHITE);
        batch.end();
    }

    private void drawMainMenu() {
        mainMenuButton(595f, 730f, 730f, 82f,
                gameText.translate("game.crear_timba"), 0,
                ButtonTone.POSITIVE,
                () -> openNewGame(NewGameConnectionDraft.Mode.CREATE));
        mainMenuButton(595f, 625f, 730f, 82f,
                gameText.translate("game.unirme_a_timba"), 1, false,
                () -> openNewGame(NewGameConnectionDraft.Mode.JOIN));
        mainMenuButton(595f, 520f, 730f, 82f,
                gameText.translate("stats.estadisticas_2"), 2, false,
                this::openStats);
        mainMenuButton(595f, 415f, 730f, 82f,
                gameText.translate("menu.visor_capturas"), 6, false,
                this::openScreenshotViewer);
        mainMenuButton(595f, 310f, 350f, 82f,
                uppercase(gameText.translate("menu.ajustes")), 3, false,
                this::openSettings);
        choice(975f, 310f, 350f, 82f, "",
                gameText.translate("gdx.language_name"),
                this::toggleLanguage);
        mainMenuButton(595f, 205f, 350f, 82f,
                uppercase(gameText.translate("menu.acerca_de")), 4, false,
                this::openAboutDialog);
        mainMenuButton(975f, 205f, 350f, 82f,
                gameText.translate("ui.salir"), 5, ButtonTone.DANGER,
                Gdx.app::exit);

        Rectangle sound = mainMenuSoundBounds();
        drawSoundControl(sound.x, sound.y, sound.width, sound.height, false);
        if (deferredUpdateAvailable(updatePromptDismissed,
                updatePromptOpen, updateResult)) {
            themedButton(1460f, 944f, 390f, 68f,
                    uppercase(gameText.translate("gdx.update.menu_action",
                            updateResult.version())),
                    ButtonTone.FEATURED, this::showUpdatePrompt, true);
        }
        String quote = menuQuotes.update(gameText.language(), frameDelta);
        drawMainMenuQuote(quote);
    }

    /**
     * Keeps every menu quote at the same 18 px size. Only exceptional long
     * quotes wrap to a second line; unlike fitted UI labels they never switch
     * to the smaller fallback font.
     */
    private void drawMainMenuQuote(String quote) {
        if (quote.isBlank()) return;
        List<String> lines = wrapText(smallFont, quote,
                MENU_QUOTE_MAX_WIDTH, MENU_QUOTE_MAX_LINES);
        float baseline = lines.size() > 1
                ? MENU_QUOTE_TWO_LINE_TOP_BASELINE
                : MENU_QUOTE_SINGLE_BASELINE;
        for (String line : lines) {
            texts.add(new TextItem(smallFont, line, WIDTH / 2f, baseline,
                    new Color(Color.WHITE), true, true));
            baseline -= MENU_QUOTE_LINE_HEIGHT;
        }
    }

    private void openStats() {
        captureFrontendModalInput();
        // Statistics is a single canonical surface.  Reclaim input here as
        // well as in the shell transition so opening it from the final table
        // summary cannot leave the retained table processor consuming wheel
        // events.
        Gdx.input.setInputProcessor(this);
        surface = Surface.STATS;
        statsGameIndex = -1;
        statsHandIndex = -1;
        statsAllGames = List.of();
        statsGames = List.of();
        statsPlayers = List.of();
        statsPlayerFilter = "";
        statsHands = List.of();
        statsBalances = List.of();
        statsBalanceHistory = List.of();
        statsShowdown = List.of();
        statsMetrics = List.of();
        statsPerformance = List.of();
        statsBestHands = List.of();
        statsConfirmation = StatsConfirmation.NONE;
        statsPicker = StatsPicker.NONE;
        statsSyncExclusionsOpen = false;
        statsPickerScroll = 0f;
        statsPickerScrollTarget = 0f;
        statsPickerScrollMaximum = 0f;
        statsChartPanX = statsChartPanTargetX = 0.5f;
        statsChartPanY = statsChartPanTargetY = 0.5f;
        statsSortColumn = -1;
        statsSortAscending = true;
        statsSortMode = statsMode;
        statsSortHandScope = false;
        resetStatsResultScroll();
        loadStatsGames();
        syncMusicForSurface();
    }

    void openStatsFromTable(Runnable returnAction) {
        statsReturnAction = Objects.requireNonNull(returnAction,
                "returnAction");
        openStats();
    }

    private void closeStats() {
        statsLoadGeneration++;
        statsLoading = false;
        statsError = "";
        statsConfirmation = StatsConfirmation.NONE;
        statsPicker = StatsPicker.NONE;
        statsSyncExclusionsOpen = false;
        Runnable returnAction = statsReturnAction;
        statsReturnAction = null;
        if (returnAction != null) {
            returnAction.run();
        } else {
            surface = Surface.MENU;
            syncMusicForSurface();
        }
    }

    private void openScreenshotViewer() {
        clearActiveField();
        screenshotReturnSurface = surface == Surface.LOBBY
                ? Surface.LOBBY : Surface.MENU;
        screenshotDeleteConfirmation = false;
        screenshotOperationPending = false;
        surface = Surface.SCREENSHOTS;
        refreshScreenshotFiles(0);
        syncMusicForSurface();
    }

    private void closeScreenshotViewer() {
        screenshotGeneration++;
        disposeScreenshotTexture();
        screenshots = List.of();
        screenshotError = "";
        screenshotToast = "";
        screenshotDeleteConfirmation = false;
        screenshotOperationPending = false;
        surface = screenshotReturnSurface;
        syncMusicForSurface();
    }

    private void refreshScreenshotFiles(int preferredIndex) {
        try {
            screenshots = GdxScreenshotStore.scan();
            screenshotError = "";
        } catch (IOException failure) {
            screenshots = List.of();
            screenshotError = uppercase(gameText.translate(
                    "gdx.screenshot.folder_failed"));
        }
        screenshotIndex = screenshots.isEmpty() ? 0
                : MathUtils.clamp(preferredIndex, 0,
                        screenshots.size() - 1);
        loadScreenshotTexture();
    }

    private void disposeScreenshotTexture() {
        if (screenshotTexture != null) {
            screenshotTexture.dispose();
            screenshotTexture = null;
        }
    }

    private void loadScreenshotTexture() {
        disposeScreenshotTexture();
        if (screenshots.isEmpty()) return;
        screenshotIndex = MathUtils.clamp(screenshotIndex, 0,
                screenshots.size() - 1);
        try {
            Path selected = screenshots.get(screenshotIndex).file();
            // Start the AWT decode before GDX performs its own PNG decode and
            // GPU upload. By the time the texture becomes visible, the
            // clipboard bitmap is normally ready even for an immediate click.
            GdxImageClipboard.prepare(selected);
            screenshotTexture = new Texture(Gdx.files.absolute(
                    selected.toString()), true);
            screenshotTexture.setFilter(TextureFilter.MipMapLinearLinear,
                    TextureFilter.Linear);
            screenshotError = "";
        } catch (RuntimeException failure) {
            screenshotError = uppercase(gameText.translate(
                    "gdx.screenshot.open_failed"));
        }
    }

    private void showRelativeScreenshot(int delta) {
        int target = screenshotIndex + delta;
        if (target < 0 || target >= screenshots.size()) return;
        screenshotIndex = target;
        loadScreenshotTexture();
    }

    private void copyCurrentScreenshot() {
        if (screenshots.isEmpty() || screenshotOperationPending) return;
        Path selected = screenshots.get(screenshotIndex).file();
        screenshotOperationPending = true;
        runScreenshotOperation("coronapoker-gdx-frontend-copy-screenshot",
                () -> {
                    if (!GdxImageClipboard.copy(selected)) {
                        throw new IOException("Clipboard rejected screenshot");
                    }
                }, "ui.imagen_copiada", "ui.copiar_imagen_error", null);
    }

    private void requestDeleteCurrentScreenshot() {
        if (screenshots.isEmpty() || screenshotOperationPending) return;
        screenshotDeleteConfirmation = true;
    }

    private void deleteCurrentScreenshot() {
        if (screenshots.isEmpty() || screenshotOperationPending) return;
        Path selected = screenshots.get(screenshotIndex).file();
        int selectedIndex = screenshotIndex;
        screenshotDeleteConfirmation = false;
        screenshotOperationPending = true;
        runScreenshotOperation("coronapoker-gdx-frontend-delete-screenshot",
                () -> {
                    if (!GdxScreenshotStore.isManaged(
                            GdxScreenshotStore.directory(), selected)) {
                        throw new IOException(
                                "Screenshot outside managed folder");
                    }
                    GdxImageClipboard.discard(selected);
                    Files.delete(selected);
                }, "", "ui.borrar_captura_error",
                () -> refreshScreenshotFiles(selectedIndex));
    }

    private void runScreenshotOperation(String threadName,
            ScreenshotOperation operation, String successKey,
            String failureKey, Runnable afterSuccess) {
        long generation = screenshotGeneration;
        Thread worker = new Thread(() -> {
            boolean success = false;
            try {
                operation.run();
                success = true;
            } catch (Exception failure) {
                LOGGER.log(Level.WARNING,
                        "GDX screenshot operation failed", failure);
            }
            boolean completed = success;
            if (Gdx.app != null) {
                Gdx.app.postRunnable(() -> {
                    if (disposed || generation != screenshotGeneration) {
                        return;
                    }
                    screenshotOperationPending = false;
                    if (completed && afterSuccess != null) afterSuccess.run();
                    String key = completed ? successKey : failureKey;
                    if (key != null && !key.isBlank()) {
                        screenshotToast = uppercase(gameText.translate(key));
                        screenshotToastUntil = elapsed + 1.8f;
                    }
                });
            }
        }, threadName);
        worker.setDaemon(true);
        worker.start();
    }

    @FunctionalInterface
    private interface ScreenshotOperation {
        void run() throws Exception;
    }

    private void drawScreenshotViewer() {
        shapes.setColor(0.005f, 0.012f, 0.022f, 0.99f);
        shapes.rect(0f, 0f, WIDTH, HEIGHT);
        shapes.setColor(CYAN.r, CYAN.g, CYAN.b, 0.84f);
        shapes.rect(40f, 995f, WIDTH - 80f, 3f);

        textFit(headingFont,
                uppercase(gameText.translate("menu.visor_capturas")),
                WIDTH / 2f, 1040f, GOLD, true, 1080f);
        if (!screenshots.isEmpty()) {
            textFit(smallFont, GdxScreenshotStore.displayTitle(
                    screenshots.get(screenshotIndex), screenshotIndex,
                    screenshots.size(), gameText.language()),
                    WIDTH / 2f, 965f, Color.WHITE, true, 1120f);
        }

        if (screenshotTexture != null) {
            Rectangle bounds = CoronaPokerGdxTable.fitInside(
                    screenshotTexture.getWidth(), screenshotTexture.getHeight(),
                    125f, 150f, WIDTH - 250f, 760f, true);
            uiImages.add(new UiImageItem(screenshotTexture, bounds.x, bounds.y,
                    bounds.width, bounds.height));
        }
        String message = !screenshotError.isBlank() ? screenshotError
                : screenshots.isEmpty()
                        ? uppercase(gameText.translate("ui.no_capturas")) : "";
        if (!message.isBlank()) {
            textFit(uiFont, message, WIDTH / 2f, 550f,
                    new Color(0xe8edf4ff), true, 920f);
        }

        boolean modal = screenshotDeleteConfirmation;
        themedButton(1812f, 1008f, 58f, 50f, "X", ButtonTone.NEUTRAL,
                this::closeScreenshotViewer, !modal);
        if (screenshotIndex > 0) {
            screenshotNavigationButton(35f, 470f, 64f, 110f, -1,
                    () -> showRelativeScreenshot(-1), !modal);
        }
        if (screenshotIndex + 1 < screenshots.size()) {
            screenshotNavigationButton(1821f, 470f, 64f, 110f, 1,
                    () -> showRelativeScreenshot(1), !modal);
        }
        if (screenshotTexture != null) {
            themedButton(660f, 48f, 280f, 70f,
                    uppercase(gameText.translate(
                            "ui.copiar_imagen_portapapeles")),
                    ButtonTone.FEATURED, this::copyCurrentScreenshot,
                    !modal && !screenshotOperationPending);
            themedButton(980f, 48f, 280f, 70f,
                    uppercase(gameText.translate("ui.borrar_captura")),
                    ButtonTone.DANGER, this::requestDeleteCurrentScreenshot,
                    !modal && !screenshotOperationPending);
        }

    }

    private void screenshotNavigationButton(float x, float y, float w,
            float h, int direction, Runnable action, boolean enabled) {
        themedButton(x, y, w, h, "", ButtonTone.NEUTRAL, action, enabled);
        boolean hover = enabled && hovered(x, y, w, h);
        Color color = enabled ? (hover ? Color.WHITE : GOLD) : DISABLED;
        float cx = x + w / 2f;
        float cy = y + h / 2f;
        shapes.setColor(color);
        if (direction < 0) {
            shapes.triangle(cx - 15f, cy,
                    cx + 9f, cy + 21f, cx + 9f, cy - 21f);
            shapes.rect(cx + 6f, cy - 4f, 13f, 8f);
        } else {
            shapes.triangle(cx + 15f, cy,
                    cx - 9f, cy + 21f, cx - 9f, cy - 21f);
            shapes.rect(cx - 19f, cy - 4f, 13f, 8f);
        }
    }

    private void drawScreenshotDeleteConfirmation() {
        GdxUiDialogStyle.drawBackdrop(shapes, WIDTH, HEIGHT, 1f);
        GdxUiDialogStyle.drawPanel(shapes, 560f, 365f, 800f, 320f,
                CYAN_DARK, 1f);
        textFit(headingFont,
                uppercase(gameText.translate("ui.borrar_captura")),
                600f, 650f, GOLD, false, 720f);
        centeredWrappedText(smallFont,
                gameText.translate("ui.borrar_captura_confirm"),
                WIDTH / 2f, 560f, 670f, 30f, 3, Color.WHITE);
        themedButton(635f, 415f, 300f, 75f,
                uppercase(gameText.translate("ui.cancelar")),
                ButtonTone.NEUTRAL,
                () -> screenshotDeleteConfirmation = false, true);
        themedButton(985f, 415f, 300f, 75f,
                uppercase(gameText.translate("ui.aceptar")),
                ButtonTone.DANGER, this::deleteCurrentScreenshot, true);
    }

    private void loadStatsGames() {
        long generation = ++statsLoadGeneration;
        statsLoading = true;
        statsError = "";
        CompletableFuture.runAsync(() -> {
            try {
                List<StatsRepository.GameSummary> games = statsRepository.games();
                List<StatsRepository.BalanceRow> balances =
                        statsRepository.balances(null, null);
                List<StatsRepository.MetricRow> metrics = loadStatsMetrics(
                        null, null);
                List<StatsRepository.PerformanceRow> performance =
                        statsMode == StatsMode.PERFORMANCE
                                ? statsRepository.performance(null) : List.of();
                List<StatsRepository.BestHandRow> bestHands =
                        statsMode == StatsMode.BEST_HANDS
                                ? statsRepository.bestHands(null) : List.of();
                Gdx.app.postRunnable(() -> {
                    if (disposed || generation != statsLoadGeneration) return;
                    statsAllGames = games;
                    statsPlayers = games.stream()
                            .flatMap(game -> game.players().stream())
                            .distinct()
                            .sorted(String.CASE_INSENSITIVE_ORDER)
                            .toList();
                    applyStatsPlayerFilter();
                    if (!statsPlayerFilter.isBlank()) {
                        statsGameIndex = statsGames.isEmpty() ? -1 : 0;
                        statsBalances = List.of();
                        statsBalanceHistory = List.of();
                        statsMetrics = List.of();
                        statsPerformance = List.of();
                        statsBestHands = List.of();
                        statsLoading = false;
                        // A player filter is a filter of concrete timbas, not
                        // an alias for the global aggregate.  Reload the first
                        // matching timba after maintenance so the left summary
                        // and every chart/table keep the same scope.
                        if (statsGameIndex >= 0) loadStatsScope();
                        return;
                    }
                    statsBalances = balances;
                    statsBalanceHistory = List.of();
                    statsMetrics = metrics;
                    statsPerformance = performance;
                    statsBestHands = bestHands;
                    statsLoading = false;
                });
            } catch (Exception failure) {
                postStatsFailure(generation, failure);
            }
        }, statsExecutor);
    }

    private void selectStatsGame(int direction) {
        if (statsLoading || statsGames.isEmpty()) return;
        int count = statsGames.size() + 1;
        statsGameIndex = Math.floorMod(statsGameIndex + 1 + direction,
                count) - 1;
        statsHandIndex = -1;
        statsHands = List.of();
        statsShowdown = List.of();
        loadStatsScope();
    }

    private void selectStatsGameAt(int index) {
        if (statsLoading || index < -1 || index >= statsGames.size()) return;
        statsGameIndex = index;
        statsSummaryPlayersScroll = 0f;
        statsSummaryPlayersScrollTarget = 0f;
        statsHandIndex = -1;
        statsHands = List.of();
        statsShowdown = List.of();
        statsPicker = StatsPicker.NONE;
        resetStatsResultScroll();
        loadStatsScope();
    }

    private void selectStatsHand(int direction) {
        if (statsLoading || statsGameIndex < 0 || statsHands.isEmpty()
                || !statsMode.supportsHandScope()) return;
        int count = statsHands.size() + 1;
        statsHandIndex = Math.floorMod(statsHandIndex + 1 + direction,
                count) - 1;
        loadStatsScope();
    }

    private void selectStatsHandAt(int index) {
        if (statsLoading || statsGameIndex < 0 || index < -1
                || index >= statsHands.size()) return;
        statsHandIndex = index;
        statsPicker = StatsPicker.NONE;
        resetStatsResultScroll();
        loadStatsScope();
    }

    private void selectStatsPlayerAt(int index) {
        if (statsLoading || index < -1 || index >= statsPlayers.size()) return;
        statsPlayerFilter = index < 0 ? "" : statsPlayers.get(index);
        statsGameIndex = -1;
        statsHandIndex = -1;
        statsHands = List.of();
        statsShowdown = List.of();
        applyStatsPlayerFilter();
        if (!statsPlayerFilter.isBlank() && !statsGames.isEmpty()) {
            statsGameIndex = 0;
        }
        statsPicker = StatsPicker.NONE;
        resetStatsResultScroll();
        loadStatsScope();
    }

    private void applyStatsPlayerFilter() {
        if (statsPlayerFilter.isBlank()) {
            statsGames = statsAllGames;
            return;
        }
        statsGames = statsAllGames.stream()
                .filter(game -> game.players().stream().anyMatch(player ->
                        player.equalsIgnoreCase(statsPlayerFilter)))
                .toList();
    }

    private void openStatsPicker(StatsPicker picker) {
        if (statsLoading || picker == StatsPicker.NONE) return;
        if (picker == StatsPicker.HAND
                && (statsGameIndex < 0 || statsHands.isEmpty()
                        || !statsMode.supportsHandScope())) return;
        statsPicker = picker;
        int selected = switch (picker) {
            case GAME -> statsGameIndex + 1;
            case HAND -> statsHandIndex + 1;
            case MODE -> statsMode.ordinal();
            case PLAYER -> statsPlayerFilter.isBlank() ? 0
                    : statsPlayers.indexOf(statsPlayerFilter) + 1;
            case NONE -> 0;
        };
        int itemCount = statsPickerItemCount(picker);
        statsPickerScrollMaximum = statsPickerMaximumScroll(itemCount);
        statsPickerScrollTarget = statsPickerScrollForSelection(selected,
                statsPickerScrollMaximum);
        statsPickerScroll = statsPickerScrollTarget;
    }

    private void selectStatsModeAt(int index) {
        StatsMode[] values = StatsMode.values();
        if (statsLoading || index < 0 || index >= values.length) return;
        statsMode = values[index];
        if (!statsMode.supportsHandScope()) statsHandIndex = -1;
        statsPicker = StatsPicker.NONE;
        resetStatsResultScroll();
        loadStatsScope();
    }

    private void selectStatsMode(int direction) {
        if (statsLoading) return;
        StatsMode[] values = StatsMode.values();
        statsMode = values[Math.floorMod(statsMode.ordinal() + direction,
                values.length)];
        if (!statsMode.supportsHandScope()) statsHandIndex = -1;
        loadStatsScope();
    }

    private void loadStatsScope() {
        long generation = ++statsLoadGeneration;
        statsLoading = true;
        statsError = "";
        Integer gameId = statsGameIndex < 0 ? null
                : statsGames.get(statsGameIndex).id();
        Integer handId = statsHandIndex < 0 ? null
                : statsHands.get(statsHandIndex).id();
        CompletableFuture.runAsync(() -> {
            try {
                List<StatsRepository.HandSummary> hands = gameId == null
                        ? List.of() : statsRepository.hands(gameId);
                List<StatsRepository.BalanceRow> balances =
                        statsRepository.balances(gameId, handId);
                List<StatsRepository.BalancePoint> balanceHistory =
                        gameId != null && handId == null
                                ? statsRepository.balanceHistory(gameId)
                                : List.of();
                List<StatsRepository.ShowdownRow> showdown = handId == null
                        ? List.of() : statsRepository.showdown(handId);
                List<StatsRepository.MetricRow> metrics = loadStatsMetrics(
                        gameId, handId);
                List<StatsRepository.PerformanceRow> performance =
                        statsMode == StatsMode.PERFORMANCE
                                ? statsRepository.performance(gameId) : List.of();
                List<StatsRepository.BestHandRow> bestHands =
                        statsMode == StatsMode.BEST_HANDS
                                ? statsRepository.bestHands(gameId) : List.of();
                Gdx.app.postRunnable(() -> {
                    if (disposed || generation != statsLoadGeneration) return;
                    statsHands = hands;
                    if (statsHandIndex >= hands.size()) statsHandIndex = -1;
                    statsBalances = balances;
                    statsBalanceHistory = balanceHistory;
                    statsShowdown = showdown;
                    statsMetrics = metrics;
                    statsPerformance = performance;
                    statsBestHands = bestHands;
                    statsLoading = false;
                });
            } catch (Exception failure) {
                postStatsFailure(generation, failure);
            }
        }, statsExecutor);
    }

    private List<StatsRepository.MetricRow> loadStatsMetrics(Integer gameId,
            Integer handId) throws Exception {
        return switch (statsMode) {
            case BALANCE -> List.of();
            case RESPONSE -> statsRepository.averageResponse(gameId, handId);
            case PERFORMANCE, BEST_HANDS -> List.of();
            case PREFLOP_RAISES -> statsRepository.raiseFrequency(gameId, 1);
            case FLOP_RAISES -> statsRepository.raiseFrequency(gameId, 2);
            case TURN_RAISES -> statsRepository.raiseFrequency(gameId, 3);
            case RIVER_RAISES -> statsRepository.raiseFrequency(gameId, 4);
        };
    }

    private void postStatsFailure(long generation, Exception failure) {
        LOGGER.log(Level.WARNING, "Could not load GDX statistics", failure);
        Gdx.app.postRunnable(() -> {
            if (disposed || generation != statsLoadGeneration) return;
            statsLoading = false;
            statsError = failure.getMessage() == null
                    ? failure.getClass().getSimpleName() : failure.getMessage();
        });
    }

    private void toggleStatsPrivate() {
        if (statsLoading || statsGameIndex < 0) return;
        int index = statsGameIndex;
        StatsRepository.GameSummary game = statsGames.get(index);
        boolean value = !game.privateGame();
        long generation = ++statsLoadGeneration;
        statsLoading = true;
        CompletableFuture.runAsync(() -> {
            try {
                statsRepository.setPrivate(game.id(), value);
                Gdx.app.postRunnable(() -> {
                    if (disposed || generation != statsLoadGeneration) return;
                    List<StatsRepository.GameSummary> updated =
                            new ArrayList<>(statsGames);
                    if (index < updated.size()
                            && updated.get(index).id() == game.id()) {
                        updated.set(index, game.withPrivateGame(value));
                        statsGames = List.copyOf(updated);
                    }
                    statsAllGames = statsAllGames.stream()
                            .map(candidate -> candidate.id() == game.id()
                                    ? candidate.withPrivateGame(value)
                                    : candidate)
                            .toList();
                    statsLoading = false;
                });
            } catch (Exception failure) {
                postStatsFailure(generation, failure);
            }
        }, statsExecutor);
    }

    private void confirmStatsDeletion() {
        StatsConfirmation action = statsConfirmation;
        statsConfirmation = StatsConfirmation.NONE;
        if (statsLoading || action == StatsConfirmation.NONE) return;
        int gameId = statsGameIndex < 0 ? -1
                : statsGames.get(statsGameIndex).id();
        List<Integer> filteredGameIds = statsGames.stream()
                .map(StatsRepository.GameSummary::id).toList();
        long generation = ++statsLoadGeneration;
        statsLoading = true;
        CompletableFuture.runAsync(() -> {
            try {
                switch (action) {
                    case IMPORTED -> statsRepository.deleteImportedGames();
                    case GAME -> {
                        if (gameId >= 0) statsRepository.deleteGame(gameId);
                    }
                    case PURGE_FILTERED -> statsRepository.deleteGames(
                            filteredGameIds);
                    case PRIVATE_FILTERED -> statsRepository.setPrivate(
                            filteredGameIds, true);
                    case PUBLIC_FILTERED -> statsRepository.setPrivate(
                            filteredGameIds, false);
                    case NONE -> { }
                }
                Gdx.app.postRunnable(() -> {
                    if (disposed || generation != statsLoadGeneration) return;
                    statsGameIndex = -1;
                    statsHandIndex = -1;
                    statsLoading = false;
                    loadStatsGames();
                });
            } catch (Exception failure) {
                postStatsFailure(generation, failure);
            }
        }, statsExecutor);
    }

    private void drawStatsScreen() {
        panel(20f, 20f, 1880f, 1040f, "");
        textFit(titleFont, gameText.translate("ui.estadisticas"), 62f,
                1015f, GOLD, false, 1250f);
        themedButton(1610f, 975f, 245f, 58f,
                gameText.translate("ui.volver"), ButtonTone.NEUTRAL,
                this::closeStats, true);

        String gameLabel = statsGameIndex < 0
                ? gameText.translate("gdx.stats.all_games")
                : statsGameLabel(statsGames.get(statsGameIndex));
        choice(60f, 840f, 580f,
                gameText.translate("gdx.stats.game"), gameLabel,
                () -> openStatsPicker(StatsPicker.GAME),
                !statsLoading && !statsGames.isEmpty());
        String handLabel = statsHandIndex < 0
                ? gameText.translate("gdx.stats.all_hands")
                : gameText.translate("game.mano") + " "
                        + statsHands.get(statsHandIndex).counter();
        choice(670f, 840f, 580f,
                gameText.translate("gdx.stats.hand"), handLabel,
                () -> openStatsPicker(StatsPicker.HAND),
                !statsLoading && statsGameIndex >= 0 && !statsHands.isEmpty()
                        && statsMode.supportsHandScope());
        String playerLabel = statsPlayerFilter.isBlank()
                ? gameText.translate("gdx.stats.all_players")
                : statsPlayerFilter;
        choice(1280f, 840f, 580f,
                gameText.translate("gdx.stats.player_filter"), playerLabel,
                () -> openStatsPicker(StatsPicker.PLAYER),
                !statsLoading && !statsPlayers.isEmpty());

        panel(60f, 70f, 400f, 710f,
                statsHandIndex >= 0
                        ? gameText.translate("gdx.stats.hand_detail")
                        : gameText.translate("gdx.stats.summary"));
        panel(490f, 70f, 1370f, 710f,
                statsMode == StatsMode.BALANCE && statsHandIndex >= 0
                        ? gameText.translate("gdx.stats.showdown")
                        : gameText.translate("gdx.stats.view"));
        choice(535f, STATS_MODE_SELECTOR_Y, 1280f,
                STATS_MODE_SELECTOR_HEIGHT, "", statsModeLabel(),
                () -> openStatsPicker(StatsPicker.MODE), !statsLoading);
        if (statsHandIndex < 0 && (statsMode == StatsMode.BALANCE
                || statsMode == StatsMode.PERFORMANCE
                || statsMode == StatsMode.BEST_HANDS)) {
            textExactFit(tinyFont, uppercase(gameText.translate(
                    "stats.global_chart_zoom")), 1370f, 743f, MUTED,
                    false, 210f);
            themedButton(1590f, 716f, 48f, 38f, "-", ButtonTone.NEUTRAL,
                    () -> adjustStatsChartZoom(-0.1f),
                    statsChartZoom > 0.8f && !statsLoading);
            textFit(tinyFont, Math.round(statsChartZoom * 100f) + "%",
                    1690f, 742f, GOLD, true, 90f);
            themedButton(1742f, 716f, 48f, 38f, "+", ButtonTone.NEUTRAL,
                    () -> adjustStatsChartZoom(0.1f),
                    statsChartZoom < 2f && !statsLoading);
        }
        if (statsLoading) {
            textFit(headingFont, gameText.translate("gdx.lobby.media_loading"),
                    1175f, 440f, CYAN, true, 1000f);
            return;
        }
        if (!statsError.isBlank()) {
            centeredWrappedText(smallFont, statsError, 1175f, 440f,
                    1180f, 24f, 4, ORANGE);
            return;
        }
        if (statsHandIndex >= 0) {
            drawStatsHandSummary();
        } else {
            drawStatsSummary();
        }
        updateStatsResultScrollBounds();
        if (statsMode == StatsMode.BALANCE && statsHandIndex >= 0) {
            drawStatsShowdown();
        } else if (statsMode == StatsMode.BALANCE) {
            drawStatsBalances();
        } else if (statsMode == StatsMode.PERFORMANCE) {
            drawStatsPerformance();
        } else if (statsMode == StatsMode.BEST_HANDS) {
            drawStatsBestHands();
        } else {
            drawStatsMetrics();
        }
        drawStatsResultScrollbar();
        if (!statsLoading && !statsPlayerFilter.isBlank()
                && !statsGames.isEmpty() && statsHandIndex < 0) {
            themedButton(86f, 222f, 348f, 48f,
                    gameText.translate("stats.hacer_privadas"),
                    ButtonTone.NEUTRAL,
                    () -> statsConfirmation = StatsConfirmation.PRIVATE_FILTERED,
                    true);
            themedButton(86f, 154f, 348f, 48f,
                    gameText.translate("stats.quitar_privadas"),
                    ButtonTone.NEUTRAL,
                    () -> statsConfirmation = StatsConfirmation.PUBLIC_FILTERED,
                    true);
            themedButton(86f, 86f, 348f, 48f,
                    gameText.translate("stats.purgar"), ButtonTone.DANGER,
                    () -> statsConfirmation = StatsConfirmation.PURGE_FILTERED,
                    true);
        } else if (!statsLoading && statsGameIndex >= 0
                && statsHandIndex < 0) {
            StatsRepository.GameSummary game = statsGames.get(statsGameIndex);
            themedButton(86f, 222f, 348f, 48f,
                    uppercase(gameText.translate(game.privateGame()
                            ? "stats.quitar_privada"
                            : "stats.hacer_privada")),
                    game.privateGame() ? ButtonTone.FEATURED
                            : ButtonTone.NEUTRAL,
                    this::toggleStatsPrivate, true);
            themedButton(86f, 154f, 348f, 48f,
                    gameText.translate("gdx.stats.delete_game"),
                    ButtonTone.DANGER,
                    () -> statsConfirmation = StatsConfirmation.GAME, true);
            themedButton(86f, 86f, 348f, 48f,
                    gameText.translate("stats.borrar_importadas"),
                    ButtonTone.DANGER,
                    () -> statsConfirmation = StatsConfirmation.IMPORTED,
                    statsGames.stream().anyMatch(
                            StatsRepository.GameSummary::imported));
        } else if (!statsLoading && statsGameIndex < 0
                && statsHandIndex < 0 && statsPlayerFilter.isBlank()) {
            compactToggle(86f, 222f, 348f,
                    gameText.translate("stats.sync_receive"),
                    preferenceBoolean("sync_stats_receive", true),
                    () -> toggleStatsSyncPreference("sync_stats_receive"),
                    true);
            compactToggle(86f, 144f, 348f,
                    gameText.translate("stats.sync_share"),
                    preferenceBoolean("sync_stats_share", true),
                    () -> toggleStatsSyncPreference("sync_stats_share"),
                    true);
            themedButton(86f, 86f, 348f, 46f,
                    gameText.translate("stats.sync_exclude"),
                    ButtonTone.NEUTRAL, this::openStatsSyncExclusions, true);
        }
    }

    private void toggleStatsSyncPreference(String key) {
        togglePreference(key, true);
        preferences.saveDeferred();
    }

    private void adjustStatsChartZoom(float delta) {
        statsChartZoom = MathUtils.clamp(
                Math.round((statsChartZoom + delta) * 10f) / 10f,
                0.8f, 2f);
        if (statsChartZoom <= 1f) {
            statsChartPanX = statsChartPanTargetX = 0.5f;
            statsChartPanY = statsChartPanTargetY = 0.5f;
        }
    }

    private void openStatsSyncExclusions() {
        statsExcludePrivateDraft = preferenceBoolean(
                "sync_stats_exclude_private", true);
        statsExcludeNicksDraft = initialProperties.getProperty(
                "sync_stats_exclude_nicks", "").trim();
        statsExcludeNicksEnabledDraft = preferenceBoolean(
                "sync_stats_exclude_nicks_enabled", false)
                && !statsExcludeNicksDraft.isBlank();
        statsSyncExclusionsOpen = true;
        clearActiveField();
    }

    private void saveStatsSyncExclusions() {
        String nicks = statsExcludeNicksDraft.trim();
        initialProperties.setProperty("sync_stats_exclude_private",
                Boolean.toString(statsExcludePrivateDraft));
        initialProperties.setProperty("sync_stats_exclude_nicks_enabled",
                Boolean.toString(statsExcludeNicksEnabledDraft
                        && !nicks.isBlank()));
        initialProperties.setProperty("sync_stats_exclude_nicks", nicks);
        preferences.saveDeferred();
        statsSyncExclusionsOpen = false;
        clearActiveField();
    }

    private void closeStatsSyncExclusions() {
        statsSyncExclusionsOpen = false;
        clearActiveField();
    }

    private void drawStatsSyncExclusions() {
        GdxUiDialogStyle.drawBackdrop(shapes, WIDTH, HEIGHT, 1f);
        GdxUiDialogStyle.drawPanel(shapes, 500f, 245f, 920f, 590f,
                CYAN_DARK, 1f);
        textFit(headingFont, uppercase(gameText.translate(
                "stats.sync_exclude_title")), 960f, 775f,
                GOLD, true, 820f);
        compactToggle(570f, 650f, 780f,
                gameText.translate("stats.sync_exclude_private"),
                statsExcludePrivateDraft,
                () -> statsExcludePrivateDraft = !statsExcludePrivateDraft,
                true);
        compactToggle(570f, 550f, 780f,
                gameText.translate("stats.sync_exclude_nicks"),
                statsExcludeNicksEnabledDraft,
                () -> statsExcludeNicksEnabledDraft =
                        !statsExcludeNicksEnabledDraft, true);
        field(570f, 415f, 780f,
                gameText.translate("stats.sync_exclude_nicks"),
                statsExcludeNicksDraft, "statsExcludeNicks", false);
        themedButton(570f, 290f, 350f, 72f,
                uppercase(gameText.translate("ui.cancelar")),
                ButtonTone.NEUTRAL, this::closeStatsSyncExclusions, true);
        themedButton(1000f, 290f, 350f, 72f,
                uppercase(gameText.translate("ui.aceptar")),
                ButtonTone.POSITIVE, this::saveStatsSyncExclusions, true);
    }

    private void drawStatsConfirmation() {
        GdxUiDialogStyle.drawBackdrop(shapes, WIDTH, HEIGHT, 1f);
        GdxUiDialogStyle.drawPanel(shapes, 560f, 350f, 800f, 330f,
                CYAN_DARK, 1f);
        textFit(headingFont, gameText.translate("ui.seguro"), 594f, 651f,
                GOLD, false, 732f);
        String prompt = switch (statsConfirmation) {
            case IMPORTED -> gameText.translate("stats.borrar_importadas_confirm");
            case PURGE_FILTERED -> gameText.translate(
                    "player.eliminar_todas_las_timbas_donde");
            case PRIVATE_FILTERED -> gameText.translate(
                    "player.marcar_privadas_todas_las_timbas");
            case PUBLIC_FILTERED -> gameText.translate(
                    "player.quitar_privadas_todas_las_timbas");
            case GAME, NONE -> gameText.translate(
                    "gdx.stats.delete_game_confirm");
        };
        centeredWrappedText(smallFont, prompt, 960f, 560f, 680f,
                28f, 4, Color.WHITE);
        themedButton(635f, 405f, 300f, 75f,
                uppercase(gameText.translate("ui.cancelar")),
                ButtonTone.NEUTRAL,
                () -> statsConfirmation = StatsConfirmation.NONE, true);
        boolean destructive = statsConfirmation == StatsConfirmation.IMPORTED
                || statsConfirmation == StatsConfirmation.GAME
                || statsConfirmation == StatsConfirmation.PURGE_FILTERED;
        themedButton(985f, 405f, 300f, 75f,
                uppercase(gameText.translate(destructive
                        ? "gdx.stats.delete" : "ui.aceptar")),
                destructive ? ButtonTone.DANGER : ButtonTone.POSITIVE,
                this::confirmStatsDeletion, true);
    }

    private void drawStatsPicker() {
        GdxUiDialogStyle.drawBackdrop(shapes, WIDTH, HEIGHT, 1f);
        boolean games = statsPicker == StatsPicker.GAME;
        boolean modes = statsPicker == StatsPicker.MODE;
        boolean players = statsPicker == StatsPicker.PLAYER;
        String title = modes ? gameText.translate("gdx.stats.view")
                : players ? gameText.translate("gdx.stats.player_filter")
                : gameText.translate(games
                        ? "gdx.stats.game" : "gdx.stats.hand");
        GdxUiDialogStyle.drawPanel(shapes, 480f, 145f, 960f, 790f,
                CYAN_DARK, 1f);
        textFit(headingFont, title, 514f, 906f, GOLD, false, 892f);
        themedButton(1350f, 865f, 54f, 48f, "X", ButtonTone.NEUTRAL,
                () -> statsPicker = StatsPicker.NONE, true);

        int itemCount = statsPickerItemCount(statsPicker);
        statsPickerScrollMaximum = statsPickerMaximumScroll(itemCount);
        statsPickerScrollTarget = MathUtils.clamp(statsPickerScrollTarget,
                0f, statsPickerScrollMaximum);
        statsPickerScroll = MathUtils.clamp(statsPickerScroll,
                0f, statsPickerScrollMaximum);
        outerBox(520f, STATS_PICKER_VIEW_BOTTOM - 10f, 884f,
                STATS_PICKER_VIEW_TOP - STATS_PICKER_VIEW_BOTTOM + 20f,
                LINE, new Color(0x06101dff));
        for (int item = 0; item < itemCount; item++) {
            float y = statsPickerRowY(item, statsPickerScroll);
            if (y < STATS_PICKER_VIEW_BOTTOM
                    || y + STATS_PICKER_ROW_HEIGHT > STATS_PICKER_VIEW_TOP) {
                continue;
            }
            int index = modes ? item : item - 1;
            String label;
            if (modes) {
                label = statsModeLabel(StatsMode.values()[index]);
            } else if (players) {
                label = index < 0
                        ? gameText.translate("gdx.stats.all_players")
                        : statsPlayers.get(index);
            } else if (index < 0) {
                label = gameText.translate(games
                        ? "gdx.stats.all_games" : "gdx.stats.all_hands");
            } else if (games) {
                label = statsGameLabel(statsGames.get(index));
            } else {
                label = gameText.translate("game.mano") + " "
                        + statsHands.get(index).counter();
            }
            boolean selected = index == (modes ? statsMode.ordinal()
                    : players ? statsPlayerFilter.isBlank() ? -1
                            : statsPlayers.indexOf(statsPlayerFilter)
                    : games ? statsGameIndex : statsHandIndex);
            final int selectedIndex = index;
            themedButton(535f, y, 830f, STATS_PICKER_ROW_HEIGHT, label,
                    selected ? ButtonTone.FEATURED : ButtonTone.NEUTRAL,
                    () -> {
                        if (modes) selectStatsModeAt(selectedIndex);
                        else if (players) selectStatsPlayerAt(selectedIndex);
                        else if (games) selectStatsGameAt(selectedIndex);
                        else selectStatsHandAt(selectedIndex);
                    }, true);
        }
        drawStatsPickerScrollbar();
    }

    private int statsPickerItemCount(StatsPicker picker) {
        return switch (picker) {
            case MODE -> StatsMode.values().length;
            case PLAYER -> statsPlayers.size() + 1;
            case GAME -> statsGames.size() + 1;
            case HAND -> statsHands.size() + 1;
            case NONE -> 0;
        };
    }

    static float statsPickerMaximumScroll(int itemCount) {
        float contentHeight = itemCount <= 0 ? 0f
                : (itemCount - 1) * STATS_PICKER_ROW_STRIDE
                        + STATS_PICKER_ROW_HEIGHT;
        return Math.max(0f, contentHeight
                - (STATS_PICKER_VIEW_TOP - STATS_PICKER_VIEW_BOTTOM));
    }

    static float statsPickerScrollForSelection(int selected,
            float maximum) {
        float viewportHeight = STATS_PICKER_VIEW_TOP
                - STATS_PICKER_VIEW_BOTTOM;
        return MathUtils.clamp(selected * STATS_PICKER_ROW_STRIDE
                - (viewportHeight - STATS_PICKER_ROW_HEIGHT) / 2f,
                0f, maximum);
    }

    static float statsPickerScrollAfterWheel(float current, float maximum,
            float amountY) {
        return MathUtils.clamp(current
                + amountY * STATS_PICKER_SCROLL_SPEED, 0f, maximum);
    }

    static float statsPickerRowY(int item, float scroll) {
        return STATS_PICKER_VIEW_TOP - STATS_PICKER_ROW_HEIGHT
                - item * STATS_PICKER_ROW_STRIDE + scroll;
    }

    private void drawStatsPickerScrollbar() {
        statsPickerScrollTrack.set(1380f, STATS_PICKER_VIEW_BOTTOM,
                10f, STATS_PICKER_VIEW_TOP - STATS_PICKER_VIEW_BOTTOM);
        shapes.setColor(new Color(0x18304cff));
        roundedRect(statsPickerScrollTrack.x, statsPickerScrollTrack.y,
                statsPickerScrollTrack.width, statsPickerScrollTrack.height,
                5f);
        if (statsPickerScrollMaximum <= 0f) {
            statsPickerScrollThumbHeight = statsPickerScrollTrack.height;
            return;
        }
        float viewportHeight = statsPickerScrollTrack.height;
        float contentHeight = viewportHeight + statsPickerScrollMaximum;
        statsPickerScrollThumbHeight = Math.max(54f,
                viewportHeight * viewportHeight / contentHeight);
        float travel = viewportHeight - statsPickerScrollThumbHeight;
        float progress = statsPickerScroll / statsPickerScrollMaximum;
        float thumbY = statsPickerScrollTrack.y + travel * (1f - progress);
        shapes.setColor(CYAN);
        roundedRect(statsPickerScrollTrack.x, thumbY,
                statsPickerScrollTrack.width, statsPickerScrollThumbHeight,
                5f);
    }

    private void resetStatsResultScroll() {
        statsResultScroll = 0f;
        statsResultScrollTarget = 0f;
        statsResultScrollMaximum = 0f;
    }

    private int statsResultRowCount() {
        if (statsMode == StatsMode.BALANCE) {
            return statsHandIndex >= 0
                    ? statsShowdown.size() : statsBalances.size();
        }
        if (statsMode == StatsMode.PERFORMANCE) {
            return statsPerformance.size();
        }
        if (statsMode == StatsMode.BEST_HANDS) {
            return statsBestHands.size();
        }
        return statsMetrics.size();
    }

    private float statsResultRowStride() {
        if (statsMode == StatsMode.BALANCE && statsHandIndex < 0) {
            return 41f;
        }
        return statsMode == StatsMode.RESPONSE
                || statsMode == StatsMode.PREFLOP_RAISES
                || statsMode == StatsMode.FLOP_RAISES
                || statsMode == StatsMode.TURN_RAISES
                || statsMode == StatsMode.RIVER_RAISES ? 52f : 48f;
    }

    private void updateStatsResultScrollBounds() {
        statsResultScrollMaximum = statsResultMaximumScroll(
                statsResultRowCount(), statsResultRowStride());
        statsResultScrollTarget = MathUtils.clamp(statsResultScrollTarget,
                0f, statsResultScrollMaximum);
        statsResultScroll = MathUtils.clamp(statsResultScroll,
                0f, statsResultScrollMaximum);
    }

    static float statsResultMaximumScroll(int rowCount, float rowStride) {
        return Math.max(0f, (rowCount - STATS_RESULT_VISIBLE_ROWS)
                * rowStride);
    }

    static float statsResultScrollAfterWheel(float current, float maximum,
            float rowStride, float amountY) {
        return MathUtils.clamp(current
                + amountY * rowStride * 3f, 0f, maximum);
    }

    static float statsResultRowY(float firstY, int row, float rowStride,
            float scroll) {
        return firstY - row * rowStride + scroll;
    }

    private boolean statsResultRowVisible(float y, float firstY,
            float rowStride) {
        return y <= firstY + 0.5f
                && y >= firstY
                        - (STATS_RESULT_VISIBLE_ROWS - 1) * rowStride - 0.5f;
    }

    private void drawStatsResultScrollbar() {
        if (statsResultScrollMaximum <= 0f) {
            statsResultScrollTrack.set(0f, 0f, 0f, 0f);
            statsResultScrollThumbHeight = 0f;
            return;
        }
        statsResultScrollTrack.set(1828f, 128f, 18f, 485f);
        shapes.setColor(new Color(0x18304cff));
        roundedRect(statsResultScrollTrack.x, statsResultScrollTrack.y,
                statsResultScrollTrack.width, statsResultScrollTrack.height,
                9f);
        float contentHeight = statsResultScrollTrack.height
                + statsResultScrollMaximum;
        statsResultScrollThumbHeight = Math.max(54f,
                statsResultScrollTrack.height * statsResultScrollTrack.height
                        / contentHeight);
        float travel = statsResultScrollTrack.height
                - statsResultScrollThumbHeight;
        float progress = statsResultScroll / statsResultScrollMaximum;
        float thumbY = statsResultScrollTrack.y
                + travel * (1f - progress);
        shapes.setColor(CYAN);
        roundedRect(statsResultScrollTrack.x, thumbY,
                statsResultScrollTrack.width, statsResultScrollThumbHeight,
                9f);
    }

    private String statsModeLabel() {
        return statsModeLabel(statsMode);
    }

    private String statsModeLabel(StatsMode mode) {
        return gameText.translate(switch (mode) {
            case BALANCE -> "ui.gananciasperdidas";
            case RESPONSE -> "ui.tiempo_medio_de_respuesta";
            case PERFORMANCE -> "stats.rendimiento_de_los_jugadores";
            case BEST_HANDS -> "ui.jugadas_ganadoras";
            case PREFLOP_RAISES -> "stats.apuestassubidas_en_el_preflop";
            case FLOP_RAISES -> "stats.apuestassubidas_en_el_flop";
            case TURN_RAISES -> "stats.apuestassubidas_en_el_turn";
            case RIVER_RAISES -> "stats.apuestassubidas_en_el_river";
        });
    }

    private void drawStatsMetrics() {
        float x = 540f;
        float y = 645f;
        boolean responseTime = statsMode == StatsMode.RESPONSE;
        String unit = responseTime
                ? statsUnitHeader(gameText.translate("ui.segundos")) : "%";
        statsSortableHeader(gameText.translate("player.jugador"), x, y,
                270f, 0);
        statsSortableHeader(unit, 1665f, y, 140f, 1);
        y -= 52f;
        float firstY = y;
        Comparator<StatsRepository.MetricRow> comparator = switch (
                statsSortColumn) {
            case 1 -> Comparator.comparingDouble(
                    StatsRepository.MetricRow::value);
            default -> Comparator.comparing(StatsRepository.MetricRow::player,
                    statsTextComparator());
        };
        List<StatsRepository.MetricRow> displayRows = sortedStatsRows(
                statsMetrics, comparator);
        int rows = displayRows.size();
        double maximum = statsMode == StatsMode.RESPONSE
                ? statsMetrics.stream().mapToDouble(
                        StatsRepository.MetricRow::value).max().orElse(1d)
                : 100d;
        maximum = Math.max(1d, maximum);
        for (int index = 0; index < rows; index++) {
            y = statsResultRowY(firstY, index, 52f, statsResultScroll);
            if (!statsResultRowVisible(y, firstY, 52f)) continue;
            StatsRepository.MetricRow row = displayRows.get(index);
            shapes.setColor(index % 2 == 0
                    ? new Color(0x16263bdd) : new Color(0x101d30dd));
            shapes.rect(x - 12f, y - 25f, 1275f, 48f);
            textFit(smallFont, row.player(), x, y + 7f, Color.WHITE,
                    false, 260f);
            float barX = 835f;
            float barWidth = 820f;
            shapes.setColor(new Color(0x253248ff));
            roundedRect(barX, y - 12f, barWidth, 24f, 6f);
            shapes.setColor(statsMode == StatsMode.RESPONSE
                    ? CYAN_DARK : ORANGE);
            roundedRect(barX, y - 12f,
                    Math.max(3f, Math.min(barWidth,
                            barWidth * (float) (row.value() / maximum))),
                    24f, 6f);
            String value = String.format(Locale.ROOT,
                    responseTime ? "%.1f" : "%.1f%%", row.value());
            textFit(smallFont, value, 1735f, y + 7f, GOLD,
                    true, 140f);
        }
        if (statsMetrics.isEmpty()) {
            textFit(smallFont, gameText.translate("gdx.stats.no_data"),
                    1175f, 430f, MUTED, true, 1100f);
        }
    }

    static String statsUnitHeader(String translatedUnit) {
        String value = Objects.requireNonNullElse(translatedUnit, "").trim();
        return value.length() >= 2 && value.startsWith("(")
                && value.endsWith(")")
                        ? value.substring(1, value.length() - 1).trim()
                        : value;
    }

    private void statsSortableHeader(String label, float x, float y,
            float width, int column) {
        boolean active = statsSortMode == statsMode
                && statsSortHandScope == (statsHandIndex >= 0)
                && statsSortColumn == column;
        float labelX = x + (active ? 18f : 0f);
        textExactFit(tinyFont, uppercase(label), labelX, y, CYAN, false,
                width - (active ? 18f : 0f));
        hit(x - 5f, y - 26f, width + 10f, 38f,
                () -> toggleStatsSort(column));
        if (!active) return;
        float arrowX = x + 6f;
        shapes.setColor(GOLD);
        if (statsSortAscending) {
            shapes.triangle(arrowX - 6f, y - 2f, arrowX + 6f, y - 2f,
                    arrowX, y + 7f);
        } else {
            shapes.triangle(arrowX - 6f, y + 7f, arrowX + 6f, y + 7f,
                    arrowX, y - 2f);
        }
    }

    private void toggleStatsSort(int column) {
        boolean handScope = statsHandIndex >= 0;
        if (statsSortMode == statsMode && statsSortHandScope == handScope
                && statsSortColumn == column) {
            statsSortAscending = !statsSortAscending;
        } else {
            statsSortMode = statsMode;
            statsSortHandScope = handScope;
            statsSortColumn = column;
            statsSortAscending = true;
        }
        resetStatsResultScroll();
    }

    private <T> List<T> sortedStatsRows(List<T> source,
            Comparator<T> comparator) {
        if (statsSortColumn < 0 || statsSortMode != statsMode
                || statsSortHandScope != (statsHandIndex >= 0)) return source;
        ArrayList<T> sorted = new ArrayList<>(source);
        sorted.sort(statsSortAscending ? comparator : comparator.reversed());
        return sorted;
    }

    private static Comparator<String> statsTextComparator() {
        return Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER);
    }

    private void drawStatsPerformance() {
        if (statsPerformance.isEmpty()) {
            drawStatsEmptyState();
            return;
        }
        drawStatsPerformanceRadar(525f, 155f, 500f, 500f);
        float x = 1055f;
        float y = 645f;
        String[] headers = { gameText.translate("player.jugador"),
            gameText.translate("stats.manos_jugadas"),
            gameText.translate("stats.manos_ganadas_2"),
            gameText.translate("stats.precision"),
            gameText.translate("stats.roi"),
            gameText.translate("stats.efectividad") };
        float[] columns = { x, x + 185f, x + 305f, x + 430f,
            x + 555f, x + 655f };
        float[] widths = { 170f, 105f, 110f, 110f, 85f, 120f };
        for (int index = 0; index < headers.length; index++) {
            statsSortableHeader(headers[index], columns[index], y,
                    widths[index], index);
        }
        y -= 52f;
        float firstY = y;
        Comparator<StatsRepository.PerformanceRow> comparator = switch (
                statsSortColumn) {
            case 1 -> Comparator.comparingDouble(
                    StatsRepository.PerformanceRow::playedPercent);
            case 2 -> Comparator.comparingDouble(
                    StatsRepository.PerformanceRow::wonPercent);
            case 3 -> Comparator.comparingDouble(
                    StatsRepository.PerformanceRow::precisionPercent);
            case 4 -> Comparator.comparingDouble(
                    StatsRepository.PerformanceRow::roiPercent);
            case 5 -> Comparator.comparingDouble(
                    StatsRepository.PerformanceRow::effectiveness);
            default -> Comparator.comparing(
                    StatsRepository.PerformanceRow::player,
                    statsTextComparator());
        };
        List<StatsRepository.PerformanceRow> displayRows = sortedStatsRows(
                statsPerformance, comparator);
        int rows = displayRows.size();
        for (int index = 0; index < rows; index++) {
            y = statsResultRowY(firstY, index, 48f, statsResultScroll);
            if (!statsResultRowVisible(y, firstY, 48f)) continue;
            StatsRepository.PerformanceRow row = displayRows.get(index);
            shapes.setColor(index % 2 == 0
                    ? new Color(0x16263bdd) : new Color(0x101d30dd));
            shapes.rect(x - 10f, y - 23f, 755f, 42f);
            String[] values = { row.player(), percent(row.playedPercent()),
                percent(row.wonPercent()), percent(row.precisionPercent()),
                percent(row.roiPercent()),
                String.format(Locale.ROOT, "%.2f", row.effectiveness()) };
            for (int column = 0; column < values.length; column++) {
                textFit(smallFont, values[column], columns[column], y + 7f,
                        column >= 4 && row.roiPercent() < 0d
                                ? new Color(0xff6b6bff) : Color.WHITE,
                        false, widths[column]);
            }
        }
    }

    private void drawStatsPerformanceRadar(float x, float y, float width,
            float height) {
        outerBox(x, y, width, height, new Color(0x31445fff),
                new Color(0x071221c8));
        chartTextFit(smallFont, uppercase(gameText.translate(
                "stats.chart_rendimiento")), x + 25f, y + height - 26f,
                GOLD, false, width - 50f);
        beginStatsChartCanvas(x + 8f, y + 12f, width - 24f,
                height - 58f);
        float cx = statsChartX(x + width / 2f);
        float cy = statsChartY(y + 230f);
        float radius = statsChartLength(165f);
        float[] angles = { 90f, 210f, 330f };
        for (int ring = 1; ring <= 4; ring++) {
            float r = radius * ring / 4f;
            float[] points = radarTriangle(cx, cy, r, r, r, angles);
            shapes.setColor(new Color(0x66758a55));
            shapes.rectLine(points[0], points[1], points[2], points[3], 1f);
            shapes.rectLine(points[2], points[3], points[4], points[5], 1f);
            shapes.rectLine(points[4], points[5], points[0], points[1], 1f);
        }
        String[] axes = { gameText.translate("stats.manos_jugadas"),
            gameText.translate("stats.manos_ganadas_2"),
            gameText.translate("stats.precision") };
        chartTextFit(tinyFont, axes[0], x + width / 2f,
                y + 230f + 165f + 28f,
                MUTED, true, 200f);
        // Axis captions are centered inside their half of the chart.  Using
        // the triangle vertices as their centres made long translations leak
        // through the chart border even though textFit ellipsized correctly.
        chartTextFit(tinyFont, axes[1], x + 92f,
                y + 230f - 165f * 0.57f,
                MUTED, true, 164f);
        chartTextFit(tinyFont, axes[2], x + width - 92f,
                y + 230f - 165f * 0.57f, MUTED, true, 164f);
        Color[] palette = { ORANGE, CYAN, new Color(0x64df86ff),
            new Color(0xbc7affff), GOLD, new Color(0x2ad1c9ff),
            new Color(0xff7f7fff), new Color(0x96c94cff) };
        int count = Math.min(8, statsPerformance.size());
        for (int index = 0; index < count; index++) {
            StatsRepository.PerformanceRow row = statsPerformance.get(index);
            float[] points = radarTriangle(cx, cy,
                    radius * MathUtils.clamp((float) row.playedPercent(), 0f,
                            100f) / 100f,
                    radius * MathUtils.clamp((float) row.wonPercent(), 0f,
                            100f) / 100f,
                    radius * MathUtils.clamp((float) row.precisionPercent(),
                            0f, 100f) / 100f,
                    angles);
            Color color = palette[index % palette.length];
            Color fill = new Color(color);
            fill.a = 0.08f;
            shapes.setColor(fill);
            shapes.triangle(points[0], points[1], points[2], points[3],
                    points[4], points[5]);
            shapes.setColor(color);
            shapes.rectLine(points[0], points[1], points[2], points[3], 2f);
            shapes.rectLine(points[2], points[3], points[4], points[5], 2f);
            shapes.rectLine(points[4], points[5], points[0], points[1], 2f);
            float legendX = x + 18f + (index % 4) * 120f;
            float legendY = y + 28f + (index / 4) * 24f;
            shapes.rect(statsChartX(legendX), statsChartY(legendY - 7f),
                    statsChartLength(16f), statsChartLength(4f));
            chartTextFit(tinyFont, row.player(), legendX + 23f, legendY,
                    color, false, 90f);
        }
        endStatsChartCanvas();
    }

    private static float[] radarTriangle(float cx, float cy, float first,
            float second, float third, float[] angles) {
        float[] radii = { first, second, third };
        float[] points = new float[6];
        for (int index = 0; index < 3; index++) {
            float radians = angles[index] * MathUtils.degreesToRadians;
            points[index * 2] = cx + MathUtils.cos(radians) * radii[index];
            points[index * 2 + 1] = cy + MathUtils.sin(radians) * radii[index];
        }
        return points;
    }

    private void drawStatsBestHands() {
        if (statsBestHands.isEmpty()) {
            drawStatsEmptyState();
            return;
        }
        drawStatsWinningHandDistribution(525f, 155f, 620f, 500f);
        float x = 1175f;
        float y = 645f;
        String[] headers = { gameText.translate("player.jugador"),
            gameText.translate("ui.cartas_recibidas"),
            gameText.translate("ui.jugada"),
            gameText.translate("game.mano_2"),
            gameText.translate("ui.beneficio") };
        float[] columns = { x, x + 135f, x + 295f, x + 450f, x + 525f };
        float[] widths = { 120f, 150f, 145f, 65f, 95f };
        for (int index = 0; index < headers.length; index++) {
            statsSortableHeader(headers[index], columns[index], y,
                    widths[index], index);
        }
        y -= 52f;
        float firstY = y;
        Comparator<StatsRepository.BestHandRow> comparator = switch (
                statsSortColumn) {
            case 1 -> Comparator.comparing(row -> statsCards(
                    row.holeCards(), ""), statsTextComparator());
            case 2 -> Comparator.comparingInt(
                    StatsRepository.BestHandRow::handValue);
            case 3 -> Comparator.comparingInt(
                    StatsRepository.BestHandRow::handCounter);
            case 4 -> Comparator.comparingDouble(
                    StatsRepository.BestHandRow::profit);
            default -> Comparator.comparing(StatsRepository.BestHandRow::player,
                    statsTextComparator());
        };
        List<StatsRepository.BestHandRow> displayRows = sortedStatsRows(
                statsBestHands, comparator);
        int rows = displayRows.size();
        for (int index = 0; index < rows; index++) {
            y = statsResultRowY(firstY, index, 48f, statsResultScroll);
            if (!statsResultRowVisible(y, firstY, 48f)) continue;
            StatsRepository.BestHandRow row = displayRows.get(index);
            shapes.setColor(index % 2 == 0
                    ? new Color(0x16263bdd) : new Color(0x101d30dd));
            shapes.rect(x - 10f, y - 23f, 630f, 42f);
            textFit(smallFont, row.player(), columns[0], y + 7f,
                    Color.WHITE, false, widths[0]);
            drawStatsCards(row.holeCards(), columns[1], y, widths[1],
                    "*****");
            textFit(smallFont, statsHandRank(row.handValue()), columns[2],
                    y + 7f, Color.WHITE, false, widths[2]);
            textFit(smallFont, Integer.toString(row.handCounter()),
                    columns[3], y + 7f, Color.WHITE, false, widths[3]);
            textFit(smallFont, money(row.profit()), columns[4], y + 7f,
                    new Color(0x64df86ff), false, widths[4]);
        }
    }

    private void drawStatsWinningHandDistribution(float x, float y,
            float width, float height) {
        outerBox(x, y, width, height, new Color(0x31445fff),
                new Color(0x071221c8));
        chartTextFit(smallFont, uppercase(gameText.translate("stats.chart_jugadas")),
                x + 25f, y + height - 26f, GOLD, false, width - 50f);
        beginStatsChartCanvas(x + 8f, y + 12f, width - 24f,
                height - 58f);
        int[] counts = new int[11];
        for (StatsRepository.BestHandRow row : statsBestHands) {
            if (row.handValue() >= 1 && row.handValue() <= 10) {
                counts[row.handValue()]++;
            }
        }
        int maximum = Arrays.stream(counts).max().orElse(1);
        int visible = 0;
        for (int rank = 1; rank <= 10; rank++) if (counts[rank] > 0) visible++;
        float rowHeight = Math.min(40f, 390f / Math.max(1, visible));
        float rowY = y + height - 92f;
        for (int rank = 1; rank <= 10; rank++) {
            if (counts[rank] == 0) continue;
            String label = statsHandRank(rank);
            chartTextFit(tinyFont, label, x + 25f, rowY + 7f, Color.WHITE,
                    false, 235f);
            float barX = x + 275f;
            float barWidth = width - 335f;
            shapes.setColor(new Color(0x253248ff));
            roundedRect(statsChartX(barX), statsChartY(rowY - 10f),
                    statsChartLength(barWidth), statsChartLength(22f), 5f);
            shapes.setColor(new Color(0xbc7affdd));
            roundedRect(statsChartX(barX), statsChartY(rowY - 10f),
                    Math.max(3f, statsChartLength(
                            barWidth * counts[rank] / maximum)),
                    statsChartLength(22f), 5f);
            chartTextFit(tinyFont, Integer.toString(counts[rank]),
                    x + width - 45f, rowY + 7f, GOLD, false, 30f);
            rowY -= rowHeight;
        }
        endStatsChartCanvas();
    }

    private void drawStatsEmptyState() {
        textFit(smallFont, gameText.translate("gdx.stats.no_data"),
                1175f, 430f, MUTED, true, 1100f);
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value);
    }

    private String statsGameLabel(StatsRepository.GameSummary game) {
        return game.server() + "  -  " + STATS_TIME.format(
                Instant.ofEpochMilli(game.startedAtMillis()));
    }

    private void drawStatsSummary() {
        if (statsGameIndex < 0) {
            textFit(headingFont, Integer.toString(statsGames.size()), 260f,
                    570f, GOLD, true, 330f);
            textFit(smallFont, gameText.translate("gdx.stats.saved_games"),
                    260f, 530f, MUTED, true, 330f);
            return;
        }
        StatsRepository.GameSummary game = statsGames.get(statsGameIndex);
        float y = 675f;
        statsSummaryLine(gameText.translate("gdx.stats.server"), game.server(), y);
        y -= 40f;
        String elapsedDuration = game.endedAtMillis() == null
                ? "--:--:--"
                : formatStatsDuration(Math.max(0L,
                        (game.endedAtMillis() - game.startedAtMillis()) / 1000L));
        statsSummaryLine(gameText.translate("stats.duracion"),
                elapsedDuration + " (" + formatStatsDuration(
                        game.playTimeSeconds()) + ")", y); y -= 40f;
        statsSummaryLine(gameText.translate("stats.manos"),
                Integer.toString(game.hands()), y); y -= 40f;
        statsSummaryLine(gameText.translate("stats.buyin"),
                Integer.toString(game.buyin()), y); y -= 40f;
        statsSummaryLine(gameText.translate("gdx.stats.blinds"),
                money(game.smallBlind()) + " / "
                        + money(game.smallBlind() * 2d), y); y -= 40f;
        statsSummaryLine(gameText.translate("stats.aumentar_ciegas"),
                statsBlindIncrease(game), y); y -= 40f;
        statsSummaryLine(gameText.translate("stats.recomprar"),
                gameText.translate(game.rebuy() ? "ui.si" : "gdx.stats.no"),
                y); y -= 40f;
        drawStatsSummaryPlayers(game.players(), y);
        y -= 100f;
        String origin = game.imported()
                ? gameText.translate("gdx.stats.imported")
                        + (game.importedFrom() == null
                                || game.importedFrom().isBlank() ? ""
                                        : " (" + game.importedFrom() + ")")
                : gameText.translate("gdx.stats.local");
        statsSummaryLine(gameText.translate("gdx.stats.origin"),
                origin, y);
    }

    private void drawStatsSummaryPlayers(List<String> players, float topY) {
        final float x = 86f;
        final float width = 348f;
        final float viewportHeight = 72f;
        final float rowHeight = 23f;
        final float bottom = topY - viewportHeight;
        outerBox(x, bottom, width, viewportHeight, new Color(0x31445fff),
                new Color(0x0b1728dd));
        textExactFit(tinyFont, uppercase(gameText.translate(
                "gdx.stats.players")), x + 10f, topY - 10f, MUTED,
                false, 110f);
        List<String> safePlayers = players == null ? List.of() : players;
        float visibleHeight = viewportHeight - 10f;
        float contentHeight = safePlayers.size() * rowHeight;
        float maximum = statsSummaryPlayersMaximum(safePlayers.size());
        statsSummaryPlayersScrollTarget = MathUtils.clamp(
                statsSummaryPlayersScrollTarget, 0f, maximum);
        statsSummaryPlayersScroll = MathUtils.clamp(statsSummaryPlayersScroll,
                0f, maximum);
        float firstBaseline = topY - 10f + statsSummaryPlayersScroll;
        for (int index = 0; index < safePlayers.size(); index++) {
            float baseline = firstBaseline - index * rowHeight;
            if (baseline > topY - 4f || baseline < bottom + 8f) continue;
            textExactFit(tinyFont, safePlayers.get(index), x + 126f,
                    baseline, Color.WHITE, false, width - 148f);
        }
        if (maximum > 0f) {
            float trackX = x + width - 10f;
            float trackY = bottom + 6f;
            float trackHeight = viewportHeight - 12f;
            shapes.setColor(new Color(0x18304cff));
            roundedRect(trackX, trackY, 5f, trackHeight, 2.5f);
            float thumb = Math.max(18f,
                    trackHeight * visibleHeight / contentHeight);
            float travel = trackHeight - thumb;
            float thumbY = trackY + travel
                    * (1f - statsSummaryPlayersScroll / maximum);
            shapes.setColor(CYAN);
            roundedRect(trackX, thumbY, 5f, thumb, 2.5f);
        }
    }

    static float statsSummaryPlayersMaximum(int playerCount) {
        return Math.max(0f, playerCount * 23f - 62f);
    }

    private void drawStatsHandSummary() {
        StatsRepository.HandSummary hand = statsHands.get(statsHandIndex);
        float y = 675f;
        statsSummaryLine(gameText.translate("game.mano"),
                Integer.toString(hand.counter()), y); y -= 43f;
        statsSummaryLine(gameText.translate("gdx.stats.blinds"),
                money(hand.smallBlind()) + " / "
                        + money(hand.smallBlind() * 2d), y); y -= 43f;
        statsSummaryLine(gameText.translate("stats.bote"), money(hand.pot()), y);
        y -= 43f;
        statsSummaryLine(gameText.translate("ui.cartas_comunitarias"),
                statsCards(hand.communityCards(), "-----"), y); y -= 43f;
        statsSummaryLine(gameText.translate("position.dealer"),
                hand.dealer(), y); y -= 43f;
        statsSummaryLine(gameText.translate("stats.ciega_pequena"),
                hand.smallBlindPlayer(), y); y -= 43f;
        statsSummaryLine(gameText.translate("stats.ciega_grande"),
                hand.bigBlindPlayer(), y); y -= 43f;
        long duration = Math.max(0L,
                (hand.endedAtMillis() - hand.startedAtMillis()) / 1000L);
        statsSummaryLine(gameText.translate("gdx.stats.duration"),
                formatStatsDuration(duration), y); y -= 43f;
        statsSummaryLine(gameText.translate("stats.jugadores_preflop"),
                statsPlayers(hand.preflopPlayers()), y); y -= 43f;
        statsSummaryLine(gameText.translate("stats.jugadores_flop"),
                statsPlayers(hand.flopPlayers()), y); y -= 43f;
        statsSummaryLine(gameText.translate("stats.jugadores_turn"),
                statsPlayers(hand.turnPlayers()), y); y -= 43f;
        statsSummaryLine(gameText.translate("stats.jugadores_river"),
                statsPlayers(hand.riverPlayers()), y);
    }

    private String statsBlindIncrease(StatsRepository.GameSummary game) {
        if (game.blindsTime() < 0) return gameText.translate("gdx.stats.no");
        return Integer.toString(game.blindsTime())
                + (game.blindsTimeType() <= 1 ? " min" : " ×");
    }

    private static String statsPlayers(List<String> players) {
        return players == null || players.isEmpty()
                ? "-----" : String.join(" - ", players);
    }

    private static String formatStatsDuration(long seconds) {
        long safe = Math.max(0L, seconds);
        long hours = safe / 3600L;
        long minutes = safe % 3600L / 60L;
        long remainder = safe % 60L;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes,
                remainder);
    }

    private void drawStatsShowdown() {
        float x = 535f;
        float y = 645f;
        String[] headers = { gameText.translate("player.jugador"),
            gameText.translate("ui.gana_3"),
            gameText.translate("ui.cartas_recibidas"),
            gameText.translate("ui.jugada"),
            gameText.translate("action.pagar"),
            gameText.translate("ui.beneficio") };
        float[] columns = { x, x + 285f, x + 390f, x + 625f,
            x + 930f, x + 1080f };
        float[] widths = { 260f, 80f, 210f, 280f, 125f, 170f };
        for (int index = 0; index < headers.length; index++) {
            statsSortableHeader(headers[index], columns[index], y,
                    widths[index], index);
        }
        y -= 48f;
        float firstY = y;
        Comparator<StatsRepository.ShowdownRow> comparator = switch (
                statsSortColumn) {
            case 1 -> Comparator.comparing(
                    StatsRepository.ShowdownRow::winner);
            case 2 -> Comparator.comparing(row -> statsCards(
                    row.holeCards(), ""), statsTextComparator());
            case 3 -> Comparator.comparingInt(
                    StatsRepository.ShowdownRow::handValue);
            case 4 -> Comparator.comparingDouble(
                    StatsRepository.ShowdownRow::pay);
            case 5 -> Comparator.comparingDouble(
                    StatsRepository.ShowdownRow::profit);
            default -> Comparator.comparing(
                    StatsRepository.ShowdownRow::player,
                    statsTextComparator());
        };
        List<StatsRepository.ShowdownRow> displayRows = sortedStatsRows(
                statsShowdown, comparator);
        int rows = displayRows.size();
        for (int index = 0; index < rows; index++) {
            y = statsResultRowY(firstY, index, 48f, statsResultScroll);
            if (!statsResultRowVisible(y, firstY, 48f)) continue;
            StatsRepository.ShowdownRow row = displayRows.get(index);
            Color result = row.profit() > 0d ? new Color(0x64df86ff)
                    : row.profit() < 0d ? new Color(0xff6b6bff) : MUTED;
            shapes.setColor(index % 2 == 0
                    ? new Color(0x16263bdd) : new Color(0x101d30dd));
            shapes.rect(x - 12f, y - 23f, 1275f, 42f);
            textFit(smallFont, row.player(), columns[0], y + 7f,
                    Color.WHITE, false, widths[0]);
            textFit(smallFont, row.winner()
                    ? gameText.translate("ui.si")
                            : gameText.translate("gdx.stats.no"),
                    columns[1], y + 7f, row.winner() ? GOLD : MUTED,
                    false, widths[1]);
            drawStatsCards(row.holeCards(), columns[2], y, widths[2],
                    "*****");
            textFit(smallFont, statsHandRank(row.handValue()), columns[3],
                    y + 7f, Color.WHITE, false, widths[3]);
            textFit(smallFont, money(row.pay()), columns[4], y + 7f,
                    Color.WHITE, false, widths[4]);
            textFit(smallFont, money(row.profit()), columns[5], y + 7f,
                    result, false, widths[5]);
        }
        if (statsShowdown.isEmpty()) {
            textFit(smallFont, gameText.translate("gdx.stats.no_showdown"),
                    1175f, 430f, MUTED, true, 1100f);
        }
    }

    private String statsHandRank(int value) {
        String[] keys = { "hand.high_card", "hand.one_pair",
            "hand.two_pair", "hand.three_of_a_kind", "hand.straight",
            "hand.flush", "hand.full_house", "hand.four_of_a_kind",
            "hand.straight_flush", "hand.royal_flush" };
        return value >= 1 && value <= keys.length
                ? gameText.translate(keys[value - 1]) : "-----";
    }

    private static String statsCards(List<String> cards, String empty) {
        if (cards == null || cards.isEmpty()) return empty;
        List<String> readable = new ArrayList<>(cards.size());
        for (String card : cards) {
            StatsCardGlyph decoded = decodeStatsCard(card);
            if (decoded != null) readable.add(decoded.rank() + decoded.suit());
        }
        return readable.isEmpty() ? empty : String.join("  ", readable);
    }

    private void drawStatsCards(List<String> cards, float x, float centerY,
            float maxWidth, String empty) {
        List<StatsCardGlyph> decoded = cards == null ? List.of()
                : cards.stream().map(GdxFrontendScreen::decodeStatsCard)
                        .filter(Objects::nonNull).limit(2).toList();
        if (decoded.isEmpty()) {
            textFit(smallFont, empty, x, centerY + 7f, MUTED, false,
                    maxWidth);
            return;
        }
        float gap = 6f;
        float cardWidth = Math.min(58f,
                (maxWidth - gap * (decoded.size() - 1)) / decoded.size());
        float cardHeight = 34f;
        for (int index = 0; index < decoded.size(); index++) {
            StatsCardGlyph card = decoded.get(index);
            float cardX = x + index * (cardWidth + gap);
            outerBox(cardX, centerY - cardHeight / 2f, cardWidth, cardHeight,
                    new Color(0x61718bff), new Color(0xf5f2eaff));
            Color ink = card.red() ? new Color(0xcf202fff)
                    : new Color(0x10131aff);
            textExactFit(tinyFont, card.rank(), cardX + cardWidth * 0.30f,
                    centerY + 6f, ink, true, cardWidth * 0.42f);
            drawStatsSuit(card.suit(), cardX + cardWidth * 0.73f,
                    centerY, Math.min(14f, cardWidth * 0.24f), ink);
        }
    }

    /** Draws suits as vectors so their quality never depends on font glyphs. */
    private void drawStatsSuit(String suit, float cx, float cy, float size,
            Color color) {
        float r = size * 0.27f;
        shapes.setColor(color);
        switch (suit) {
            case "♥" -> {
                shapes.circle(cx - r, cy + r * 0.55f, r, 18);
                shapes.circle(cx + r, cy + r * 0.55f, r, 18);
                shapes.triangle(cx - size * 0.54f, cy + r * 0.55f,
                        cx + size * 0.54f, cy + r * 0.55f,
                        cx, cy - size * 0.62f);
            }
            case "♦" -> {
                shapes.triangle(cx, cy + size * 0.66f,
                        cx - size * 0.48f, cy,
                        cx + size * 0.48f, cy);
                shapes.triangle(cx, cy - size * 0.66f,
                        cx - size * 0.48f, cy,
                        cx + size * 0.48f, cy);
            }
            case "♣" -> {
                shapes.circle(cx, cy + size * 0.34f, r, 18);
                shapes.circle(cx - size * 0.34f, cy - size * 0.02f,
                        r, 18);
                shapes.circle(cx + size * 0.34f, cy - size * 0.02f,
                        r, 18);
                shapes.rect(cx - size * 0.10f, cy - size * 0.62f,
                        size * 0.20f, size * 0.58f);
                shapes.triangle(cx - size * 0.30f, cy - size * 0.62f,
                        cx + size * 0.30f, cy - size * 0.62f,
                        cx, cy - size * 0.22f);
            }
            case "♠" -> {
                shapes.triangle(cx, cy + size * 0.68f,
                        cx - size * 0.52f, cy - size * 0.08f,
                        cx + size * 0.52f, cy - size * 0.08f);
                shapes.circle(cx - r, cy - size * 0.02f, r, 18);
                shapes.circle(cx + r, cy - size * 0.02f, r, 18);
                shapes.rect(cx - size * 0.10f, cy - size * 0.62f,
                        size * 0.20f, size * 0.55f);
                shapes.triangle(cx - size * 0.30f, cy - size * 0.62f,
                        cx + size * 0.30f, cy - size * 0.62f,
                        cx, cy - size * 0.22f);
            }
            default -> { }
        }
    }

    static StatsCardGlyph decodeStatsCard(String encoded) {
        if (encoded == null) return null;
        String value = encoded.trim();
        if (value.isEmpty() || value.equals("_")) return null;
        String rank;
        String rawSuit;
        int separator = value.lastIndexOf('_');
        if (separator > 0 && separator + 1 < value.length()) {
            rank = value.substring(0, separator);
            rawSuit = value.substring(separator + 1);
        } else if (value.length() >= 2) {
            rank = value.substring(0, value.length() - 1);
            rawSuit = value.substring(value.length() - 1);
        } else {
            return null;
        }
        String suit = switch (rawSuit.toUpperCase(Locale.ROOT)) {
            case "C", "♥" -> "♥";
            case "D", "♦" -> "♦";
            case "P", "♠" -> "♠";
            case "T", "♣" -> "♣";
            default -> null;
        };
        if (suit == null || rank.isBlank()) return null;
        return new StatsCardGlyph(rank, suit,
                suit.equals("♥") || suit.equals("♦"));
    }

    private void statsSummaryLine(String label, String value, float y) {
        shapes.setColor(new Color(0x31445f88));
        shapes.rect(86f, y - 23f, 348f, 1f);
        textFit(tinyFont, uppercase(label), 86f, y + 8f, MUTED, false, 132f);
        textFit(smallFont, value, 230f, y + 8f, Color.WHITE, false, 204f);
    }

    private void statsSummaryWrappedLine(String label, String value, float y) {
        shapes.setColor(new Color(0x31445f88));
        shapes.rect(86f, y - 49f, 348f, 1f);
        textFit(tinyFont, uppercase(label), 86f, y + 8f, MUTED, false, 132f);
        wrappedText(tinyFont, value, 230f, y + 8f, 204f, 21f, 3,
                Color.WHITE);
    }

    private void drawStatsBalances() {
        if (statsBalances.isEmpty()) {
            textFit(smallFont,
                    gameText.translate("stats.no_partidas_guardadas"),
                    1175f, 430f, MUTED, true, 1100f);
            return;
        }
        drawStatsBalanceChart(525f, 155f, 775f, 500f);

        float x = 1310f;
        float y = 635f;
        String[] headers = { gameText.translate("player.jugador"),
            gameText.translate("stats.stack"), gameText.translate("stats.buyin"),
            gameText.translate("ui.beneficio"), gameText.translate("stats.roi") };
        float[] columns = { x, x + 170f, x + 265f, x + 360f, x + 460f };
        float[] widths = { 155f, 80f, 80f, 90f, 65f };
        for (int index = 0; index < headers.length; index++) {
            statsSortableHeader(headers[index], columns[index], y,
                    widths[index], index);
        }
        y -= 42f;
        float firstY = y;
        Comparator<StatsRepository.BalanceRow> comparator = switch (
                statsSortColumn) {
            case 1 -> Comparator.comparingDouble(
                    StatsRepository.BalanceRow::stack);
            case 2 -> Comparator.comparingDouble(
                    StatsRepository.BalanceRow::buyin);
            case 3 -> Comparator.comparingDouble(
                    StatsRepository.BalanceRow::profit);
            case 4 -> Comparator.comparingDouble(
                    StatsRepository.BalanceRow::roiPercent);
            default -> Comparator.comparing(StatsRepository.BalanceRow::player,
                    statsTextComparator());
        };
        List<StatsRepository.BalanceRow> displayRows = sortedStatsRows(
                statsBalances, comparator);
        int rows = displayRows.size();
        for (int index = 0; index < rows; index++) {
            y = statsResultRowY(firstY, index, 41f, statsResultScroll);
            if (!statsResultRowVisible(y, firstY, 41f)) continue;
            StatsRepository.BalanceRow row = displayRows.get(index);
            Color profit = row.profit() > 0d ? new Color(0x64df86ff)
                    : row.profit() < 0d ? new Color(0xff6b6bff) : MUTED;
            shapes.setColor(index % 2 == 0
                    ? new Color(0x16263bdd) : new Color(0x101d30dd));
            shapes.rect(x - 10f, y - 20f, 535f, 37f);
            String[] values = { row.player(), money(row.stack()),
                money(row.buyin()), money(row.profit()),
                String.format(Locale.ROOT, "%.1f%%", row.roiPercent()) };
            for (int column = 0; column < values.length; column++) {
                textFit(tinyFont, values[column], columns[column], y + 6f,
                        column >= 3 ? profit : Color.WHITE,
                        false, widths[column]);
            }
        }
    }

    private void drawStatsBalanceChart(float x, float y, float width,
            float height) {
        outerBox(x, y, width, height, new Color(0x31445fff),
                new Color(0x071221c8));
        String title = gameText.translate(statsBalanceHistory.isEmpty()
                ? "stats.chart_beneficio" : "stats.chart_stack");
        chartTextFit(smallFont, uppercase(title), x + 28f, y + height - 26f,
                GOLD, false, width - 56f);
        beginStatsChartCanvas(x + 8f, y + 12f, width - 24f,
                height - 58f);
        if (statsBalanceHistory.isEmpty()) {
            drawStatsProfitBars(x + 25f, y + 35f, width - 50f,
                    height - 100f);
        } else {
            drawStatsStackLines(x + 25f, y + 35f, width - 50f,
                    height - 100f);
        }
        endStatsChartCanvas();
    }

    private void drawStatsProfitBars(float x, float y, float width,
            float height) {
        double maximum = statsBalances.stream().mapToDouble(row ->
                Math.abs(row.profit())).max().orElse(1d);
        maximum = Math.max(0.01d, maximum);
        float middle = x + width * 0.52f;
        shapes.setColor(new Color(0xa8b3c055));
        shapes.rect(statsChartX(middle), statsChartY(y),
                statsChartLength(2f), statsChartLength(height));
        int count = Math.min(10, statsBalances.size());
        float rowHeight = height / Math.max(1, count);
        for (int index = 0; index < count; index++) {
            StatsRepository.BalanceRow row = statsBalances.get(index);
            float cy = y + height - (index + 0.5f) * rowHeight;
            float available = width * 0.42f;
            float bar = Math.max(3f, available
                    * (float) (Math.abs(row.profit()) / maximum));
            boolean positive = row.profit() >= 0d;
            shapes.setColor(positive ? new Color(0x2ea043dd)
                    : new Color(0xc83737dd));
            float barX = positive ? middle + 2f : middle - bar;
            roundedRect(statsChartX(barX), statsChartY(cy
                            - Math.min(13f, rowHeight * 0.3f)),
                    statsChartLength(bar), statsChartLength(
                            Math.min(26f, rowHeight * 0.6f)), 5f);
            chartTextFit(tinyFont, row.player(), x, cy + 7f, Color.WHITE,
                    false, width * 0.42f - 12f);
            float valueX = positive
                    ? Math.min(middle + bar + 10f, x + width - 75f)
                    : Math.max(middle - bar - 80f, x);
            chartTextFit(tinyFont, money(row.profit()), valueX,
                    cy + 7f, positive ? new Color(0x64df86ff)
                            : new Color(0xff6b6bff),
                    false, 75f);
        }
    }

    private void drawStatsStackLines(float x, float y, float width,
            float height) {
        int minimumHand = statsBalanceHistory.stream().mapToInt(
                StatsRepository.BalancePoint::handCounter).min().orElse(0);
        int maximumHand = statsBalanceHistory.stream().mapToInt(
                StatsRepository.BalancePoint::handCounter).max().orElse(1);
        double minimumStack = statsBalanceHistory.stream().mapToDouble(
                StatsRepository.BalancePoint::stack).min().orElse(0d);
        double maximumStack = statsBalanceHistory.stream().mapToDouble(
                StatsRepository.BalancePoint::stack).max().orElse(1d);
        if (maximumHand == minimumHand) maximumHand++;
        if (Math.abs(maximumStack - minimumStack) < 0.001d) maximumStack++;
        float plotX = x + 52f;
        float plotY = y + 32f;
        float plotW = width - 70f;
        float plotH = height - 140f;
        for (int line = 0; line <= 4; line++) {
            float gy = plotY + plotH * line / 4f;
            shapes.setColor(new Color(0x66758a44));
            shapes.rect(statsChartX(plotX), statsChartY(gy),
                    statsChartLength(plotW), statsChartLength(1f));
            double value = minimumStack
                    + (maximumStack - minimumStack) * line / 4d;
            chartTextFit(tinyFont, money(value), x, gy + 6f, MUTED,
                    false, 45f);
        }
        Map<String, List<StatsRepository.BalancePoint>> series =
                new LinkedHashMap<>();
        for (StatsRepository.BalancePoint point : statsBalanceHistory) {
            series.computeIfAbsent(point.player(), ignored -> new ArrayList<>())
                    .add(point);
        }
        Color[] palette = { ORANGE, CYAN, new Color(0x64df86ff),
            new Color(0xbc7affff), GOLD, new Color(0x2ad1c9ff),
            new Color(0xff7f7fff), new Color(0x96c94cff) };
        int seriesIndex = 0;
        for (Map.Entry<String, List<StatsRepository.BalancePoint>> entry
                : series.entrySet()) {
            Color color = palette[seriesIndex % palette.length];
            StatsRepository.BalancePoint previous = null;
            for (StatsRepository.BalancePoint point : entry.getValue()) {
                float rawX = plotX + plotW
                        * (point.handCounter() - minimumHand)
                        / (maximumHand - minimumHand);
                float rawY = plotY + plotH
                        * (float) ((point.stack() - minimumStack)
                                / (maximumStack - minimumStack));
                float px = statsChartX(rawX);
                float py = statsChartY(rawY);
                shapes.setColor(color);
                shapes.circle(px, py, statsChartLength(3.5f), 18);
                if (previous != null) {
                    float previousX = statsChartX(plotX + plotW
                            * (previous.handCounter() - minimumHand)
                            / (maximumHand - minimumHand));
                    float previousY = statsChartY(plotY + plotH
                            * (float) ((previous.stack() - minimumStack)
                                    / (maximumStack - minimumStack)));
                    shapes.rectLine(previousX, previousY, px, py,
                            statsChartLength(2.5f));
                }
                previous = point;
            }
            float legendX = plotX + (seriesIndex % 4) * (plotW / 4f);
            // Keep a dedicated title row above the legend.  The previous
            // -28 baseline put both on the same line and made the chart look
            // as if its contents escaped their panel.
            float legendY = y + height - 66f
                    - (seriesIndex / 4) * 23f;
            shapes.setColor(color);
            shapes.rect(statsChartX(legendX), statsChartY(legendY - 7f),
                    statsChartLength(16f), statsChartLength(4f));
            chartTextFit(tinyFont, entry.getKey(), legendX + 23f,
                    legendY, color, false, plotW / 4f - 30f);
            seriesIndex++;
        }
        chartTextFit(tinyFont, Integer.toString(minimumHand), plotX,
                y + 23f, MUTED, false, 60f);
        chartTextFit(tinyFont, Integer.toString(maximumHand),
                plotX + plotW - 60f, y + 23f, MUTED, false, 60f);
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
    }

    private void openAboutDialog() {
        aboutOpen = true;
        handGeneratorOpen = false;
        aboutEasterEggClicks = 0;
        disposeAboutEasterEgg();
        clearActiveField();
        editMenu = null;
        syncMusicForSurface();
    }

    private void closeAboutDialog() {
        disposeAboutEasterEgg();
        handGeneratorOpen = false;
        aboutEasterEggClicks = 0;
        aboutOpen = false;
        syncMusicForSurface();
    }

    private void closeAboutEasterEgg() {
        disposeAboutEasterEgg();
        aboutEasterEggClicks = 0;
    }

    private void activateAboutEasterEgg(boolean alternate) {
        if (++aboutEasterEggClicks < 5) return;
        aboutEasterEggClicks = 0;
        disposeAboutEasterEgg();
        String resource = alternate ? "g" : "c";
        try (var splash = Gdx.files.internal("images/splash.gif").read();
                var encrypted = Gdx.files.internal("images/" + resource)
                        .read()) {
            byte[] decoded = GdxAboutEasterEgg.decode(splash, encrypted);
            Pixmap pixmap = new Pixmap(decoded, 0, decoded.length);
            try {
                aboutEasterEggTexture = new Texture(pixmap);
                // The originals are intentionally pixel-authored: no
                // smoothing, recolouring or gamma-correction is applied.
                aboutEasterEggTexture.setFilter(TextureFilter.Nearest,
                        TextureFilter.Nearest);
            } finally {
                pixmap.dispose();
            }
        } catch (Exception failure) {
            LOGGER.log(Level.WARNING, "Could not decode About easter egg",
                    failure);
        }
    }

    private void disposeAboutEasterEgg() {
        if (aboutEasterEggTexture != null) {
            aboutEasterEggTexture.dispose();
            aboutEasterEggTexture = null;
        }
    }

    private void checkForUpdates() {
        if (updateCheckInFlight || disposed) return;
        updateCheckInFlight = true;
        updateResult = null;
        try {
            updateService.checkLatest().whenComplete((result, failure) -> {
                if (disposed) return;
                Gdx.app.postRunnable(() -> {
                    if (disposed) return;
                    updateCheckInFlight = false;
                    if (failure != null) {
                        LOGGER.log(Level.WARNING,
                                "GDX update check failed", failure);
                        updateResult = new UpdateService.CheckResult(
                                UpdateService.Status.UNAVAILABLE, null);
                    } else {
                        updateResult = result;
                    }
                });
            });
        } catch (RuntimeException failure) {
            updateCheckInFlight = false;
            updateResult = new UpdateService.CheckResult(
                    UpdateService.Status.UNAVAILABLE, null);
            LOGGER.log(Level.WARNING, "GDX update check failed", failure);
        }
    }

    private void showUpdatePrompt() {
        if (updateResult == null || updateResult.status()
                != UpdateService.Status.UPDATE_AVAILABLE) return;
        aboutOpen = false;
        updatePromptForMod = false;
        updateReturnToAbout = false;
        updatePromptDismissed = false;
        updatePromptOpen = true;
        captureFrontendModalInput();
        syncMusicForSurface();
    }

    private void captureFrontendModalInput() {
        editMenu = null;
        pressedHit = null;
        pointerSelectionField = null;
        scrollDrag = ScrollDrag.NONE;
        clearPointerRepeat();
        clearActiveField();
    }

    static boolean deferredUpdateAvailable(boolean dismissed,
            boolean promptOpen, UpdateService.CheckResult result) {
        return dismissed && !promptOpen && result != null
                && result.status() == UpdateService.Status.UPDATE_AVAILABLE;
    }

    private void dismissUpdatePrompt() {
        if (updateInstalling) return;
        updatePromptOpen = false;
        if (updatePromptForMod) {
            updatePromptForMod = false;
            if (updateReturnToAbout) {
                aboutOpen = true;
            }
            updateReturnToAbout = false;
        } else {
            updatePromptDismissed = true;
            if (startupMenuDeferredByUpdate) {
                startupMenuDeferredByUpdate = false;
                menuRevealStartedAt = startupMenuWasSkipped
                        ? Float.NaN : elapsed;
            }
        }
        syncMusicForSurface();
    }

    private void startUpdateHandoff() {
        if (updatePromptForMod || updateInstalling || updateResult == null
                || updateResult.status()
                        != UpdateService.Status.UPDATE_AVAILABLE) return;
        String version = updateResult.version();
        var request = GdxUpdateHandoff.runtimeRequest(version,
                gameText.language());
        if (request.isEmpty()) {
            openLatestRelease();
            return;
        }
        updateInstalling = true;
        CompletableFuture.supplyAsync(() -> {
            try {
                return updaterService.handoff(request.orElseThrow());
            } catch (Exception failure) {
                throw new CompletionException(failure);
            }
        }, recoveryExecutor).whenComplete((handedOff, failure) ->
                Gdx.app.postRunnable(() -> {
                    if (disposed) return;
                    updateInstalling = false;
                    Throwable cause = unwrap(failure);
                    if (cause != null || !Boolean.TRUE.equals(handedOff)) {
                        LOGGER.log(Level.WARNING,
                                "GDX updater handoff failed", cause);
                        showToast(gameText.translate(
                                "gdx.update.install_failed"));
                        return;
                    }
                    Gdx.app.exit();
                }));
    }

    private void checkForModUpdates() {
        if (modUpdateCheckInFlight || disposed) return;
        var descriptor = presentationSettings.modUpdateUri();
        if (descriptor.isEmpty()) {
            showAboutModUpdateStatus("gdx.mod_update.not_configured");
            return;
        }
        modUpdateCheckInFlight = true;
        showAboutModUpdateStatus("gdx.mod_update.checking");
        CompletableFuture.supplyAsync(() -> GdxModUpdateChecker.check(
                        presentationSettings.modVersion(),
                        descriptor.orElseThrow(), Duration.ofSeconds(10)),
                recoveryExecutor).whenComplete((result, failure) ->
                Gdx.app.postRunnable(() -> {
                    if (disposed) return;
                    modUpdateCheckInFlight = false;
                    if (failure != null || result == null
                            || result.status()
                                    == GdxModUpdateChecker.Status.UNAVAILABLE) {
                        LOGGER.log(Level.WARNING,
                                "GDX MOD update check failed", failure);
                        showAboutModUpdateStatus(
                                "gdx.mod_update.unavailable");
                    } else if (result.status()
                            == GdxModUpdateChecker.Status.CURRENT) {
                        showAboutModUpdateStatus("gdx.mod_update.current");
                    } else {
                        modUpdateResult = result;
                        updatePromptForMod = true;
                        updateReturnToAbout = true;
                        updatePromptOpen = true;
                        aboutOpen = false;
                        syncMusicForSurface();
                    }
                }));
    }

    private void showAboutModUpdateStatus(String key) {
        aboutModUpdateStatusKey = key;
        aboutModUpdateStatusUntil = System.currentTimeMillis() + 3_500L;
    }

    private void startModUpdateHandoff() {
        if (!updatePromptForMod || updateInstalling
                || modUpdateResult == null
                || modUpdateResult.status()
                        != GdxModUpdateChecker.Status.UPDATE_AVAILABLE) return;
        String gameVersion = updateResult != null
                && updateResult.status()
                        == UpdateService.Status.UPDATE_AVAILABLE
                ? updateResult.version() : ApplicationMetadata.VERSION;
        URI downloadUri;
        try {
            downloadUri = URI.create(GdxModUpdateChecker.downloadUrl(
                    modUpdateResult, gameVersion));
        } catch (IllegalArgumentException invalid) {
            showToast(gameText.translate("gdx.mod_update.install_failed"));
            return;
        }
        var request = GdxUpdateHandoff.runtimeModRequest(
                modUpdateResult.version(), downloadUri,
                presentationSettings.modUpdatePassword(),
                gameText.language());
        if (request.isEmpty()) {
            openExternalUri(downloadUri, "gdx.update.open_failed");
            return;
        }
        updateInstalling = true;
        CompletableFuture.supplyAsync(() -> {
            try {
                return updaterService.handoff(request.orElseThrow());
            } catch (Exception failure) {
                throw new CompletionException(failure);
            }
        }, recoveryExecutor).whenComplete((handedOff, failure) ->
                Gdx.app.postRunnable(() -> {
                    if (disposed) return;
                    updateInstalling = false;
                    Throwable cause = unwrap(failure);
                    if (cause != null || !Boolean.TRUE.equals(handedOff)) {
                        LOGGER.log(Level.WARNING,
                                "GDX MOD updater handoff failed", cause);
                        showToast(gameText.translate(
                                "gdx.mod_update.install_failed"));
                        return;
                    }
                    Gdx.app.exit();
                }));
    }

    private void openLatestRelease() {
        dismissUpdatePrompt();
        openExternalUri(ApplicationMetadata.LATEST_RELEASE_URI,
                "gdx.update.open_failed");
    }

    private void openExternalUri(URI uri, String failureKey) {
        try {
            if (!Desktop.isDesktopSupported()
                    || !Desktop.getDesktop().isSupported(
                            Desktop.Action.BROWSE)) {
                throw new IOException("Desktop browsing is unavailable");
            }
            Desktop.getDesktop().browse(uri);
        } catch (IOException | RuntimeException failure) {
            LOGGER.log(Level.WARNING, "Could not open external URI " + uri,
                    failure);
            showToast(gameText.translate(failureKey));
        }
    }

    private void drawUpdateDialog() {
        hits.clear();
        GdxUiDialogStyle.drawBackdrop(shapes, WIDTH, HEIGHT, 1f);
        float x = 540f;
        float y = 345f;
        float w = 840f;
        float h = 390f;
        GdxUiDialogStyle.drawPanel(shapes, x, y, w, h, CYAN_DARK, 1f);
        String titleKey = updatePromptForMod
                ? "gdx.mod_update.title" : "gdx.update.title";
        textFit(headingFont, uppercase(gameText.translate(
                titleKey)), WIDTH / 2f, y + h - 76f,
                GOLD, true, w - 100f);
        String version = updatePromptForMod
                ? (modUpdateResult == null ? ""
                        : Objects.requireNonNullElse(
                                modUpdateResult.version(), ""))
                : (updateResult == null ? ""
                        : Objects.requireNonNullElse(
                                updateResult.version(), ""));
        String messageKey = updateInstalling
                ? (updatePromptForMod ? "gdx.mod_update.installing"
                        : "gdx.update.installing")
                : (updatePromptForMod ? "gdx.mod_update.message"
                        : "gdx.update.message");
        List<String> lines = wrapText(smallFont, gameText.translate(
                messageKey, version), w - 130f, 3);
        float lineY = y + 230f;
        for (String line : lines) {
            textFit(smallFont, line, WIDTH / 2f, lineY,
                    Color.WHITE, true, w - 130f);
            lineY -= 30f;
        }
        themedButton(x + 70f, y + 42f, 320f, 72f,
                uppercase(gameText.translate("gdx.update.later")),
                ButtonTone.NEUTRAL, this::dismissUpdatePrompt,
                !updateInstalling);
        themedButton(x + w - 390f, y + 42f, 320f, 72f,
                uppercase(gameText.translate("gdx.update.install")),
                ButtonTone.FEATURED, updatePromptForMod
                        ? this::startModUpdateHandoff
                        : this::startUpdateHandoff,
                !updateInstalling);
    }

    /** Native GDX counterpart of Swing's AboutDialog, including its music. */
    private void drawAboutDialog() {
        hits.clear();
        secondaryHits.clear();
        GdxUiDialogStyle.drawBackdrop(shapes, WIDTH, HEIGHT, 1f);

        if (handGeneratorOpen) {
            drawHandGeneratorDialog();
            return;
        }

        if (aboutEasterEggTexture != null) {
            GdxUiDialogStyle.drawPanel(shapes, 330f, 115f, 1260f, 850f,
                    CYAN_DARK, 1f);
            textFit(tinyFont, gameText.translate("ui.cerrar"), WIDTH / 2f,
                    142f, MUTED, true, 600f);
            hit(0f, 0f, WIDTH, HEIGHT, this::closeAboutEasterEgg);
            return;
        }

        float x = 310f;
        float y = 58f;
        float w = 1300f;
        float h = 964f;
        Color aboutPanel = new Color(ABOUT_PANEL_RGBA);
        GdxUiDialogStyle.drawPanel(shapes, x, y, w, h,
                aboutPanel, CYAN, 1f);
        float contentX = WIDTH / 2f - ABOUT_CONTENT_WIDTH / 2f;
        textFit(titleFont, "CORONAPOKER  " + ApplicationMetadata.VERSION,
                WIDTH / 2f, y + h - 52f, Color.WHITE, true, w - 120f);
        textFit(headingFont, gameText.translate("about.merecemos"),
                WIDTH / 2f, y + h - 108f, GOLD, true, w - 150f);

        float aboutLogoHeight = ABOUT_LOGO_WIDTH * logo.getHeight()
                / logo.getWidth();
        float aboutLogoX = aboutModIcon == null
                ? WIDTH / 2f - ABOUT_LOGO_WIDTH / 2f
                : WIDTH / 2f - ABOUT_LOGO_WIDTH - 16f;
        hit(aboutLogoX, ABOUT_LOGO_Y,
                ABOUT_LOGO_WIDTH, aboutLogoHeight,
                () -> openExternalUri(ABOUT_PROJECT_URI,
                        "gdx.about.open_failed"));

        if (presentationSettings.modActive()) {
            Rectangle modBounds = aboutModIconBounds(aboutLogoHeight);
            if (modBounds.width > 0f && modBounds.height > 0f) {
                hit(modBounds.x, modBounds.y, modBounds.width,
                        modBounds.height, this::checkForModUpdates);
            }
            boolean transientStatus = !aboutModUpdateStatusKey.isEmpty()
                    && System.currentTimeMillis() < aboutModUpdateStatusUntil;
            String modLabel = transientStatus
                    ? gameText.translate(aboutModUpdateStatusKey)
                    : gameText.translate("gdx.about.mod_loaded",
                            presentationSettings.modDisplayName());
            textFit(tinyFont, modLabel,
                    WIDTH / 2f, ABOUT_LOGO_Y - 14f,
                    Color.WHITE, true, 620f);
        }

        centeredWrappedText(tinyFont,
                gameText.translate("about.gracias_1"), WIDTH / 2f,
                682f, w - 130f, 21f, 2, MUTED);
        centeredWrappedText(tinyFont,
                gameText.translate("about.gracias_2"), WIDTH / 2f,
                653f, w - 130f, 21f, 2, MUTED);
        centeredWrappedText(tinyFont,
                gameText.translate("about.centimos"), WIDTH / 2f,
                624f, w - 130f, 21f, 2, MUTED);

        textFit(headingFont, gameText.translate("about.dedicado"),
                WIDTH / 2f, ABOUT_MEMORIAL_CENTER_Y,
                Color.WHITE, true, 830f);

        float musicY = ABOUT_MUSIC_FIRST_LINE_Y;
        String[] musicKeys = {
            "about.musica_juego", "about.musica_espera",
            "about.musica_stats", "about.musica_about"
        };
        for (String key : musicKeys) {
            centeredWrappedText(tinyFont, gameText.translate(key),
                    WIDTH / 2f, musicY, w - 150f, 20f, 2, MUTED);
            musicY -= ABOUT_MUSIC_LINE_GAP;
        }

        centeredWrappedText(tinyFont, gameText.translate("about.copyright"),
                WIDTH / 2f, ABOUT_COPYRIGHT_Y,
                w - 150f, 18f, 1, MUTED);
        hit(WIDTH / 2f - 30f, 235f, 60f, 60f,
                () -> openExternalUri(ABOUT_RULES_URI,
                        "gdx.about.open_failed"));
        textFit(smallFont, gameText.translate("about.hecho_a_mano"),
                WIDTH / 2f, 226f, Color.WHITE, true,
                ABOUT_CONTENT_WIDTH - 90f);
        float footerStart = contentX + 26f;
        float footerWidth = ABOUT_CONTENT_WIDTH - 52f;
        float buildWidth = footerWidth * 0.18f;
        float runtimeWidth = footerWidth * 0.24f;
        float systemWidth = footerWidth - buildWidth - runtimeWidth;
        textFit(tinyFont, "Jn 8:32",
                footerStart + buildWidth / 2f, 174f,
                MUTED, true, buildWidth - 30f);
        String runtime = aboutRuntimeText() + " "
                + gameText.translate("ui.hilos");
        textFit(tinyFont, runtime,
                footerStart + buildWidth + runtimeWidth / 2f, 174f,
                MUTED, true, runtimeWidth - 30f);
        String system = aboutSystemText();
        textFit(tinyFont, system,
                footerStart + buildWidth + runtimeWidth + systemWidth / 2f,
                174f, MUTED, true, systemWidth - 30f);
        hit(footerStart + buildWidth + runtimeWidth, 152f,
                systemWidth, 32f,
                () -> activateAboutEasterEgg(false));
        secondaryHit(footerStart + buildWidth + runtimeWidth, 152f,
                systemWidth, 32f,
                () -> activateAboutEasterEgg(true));
        themedButton(WIDTH / 2f - 325f, y + 18f, 310f, 58f,
                uppercase(gameText.translate("menu.generador_de_jugadas")),
                ButtonTone.FEATURED, this::openHandGenerator, true);
        themedButton(WIDTH / 2f + 15f, y + 18f, 310f, 58f,
                uppercase(gameText.translate("ui.cerrar")),
                ButtonTone.NEUTRAL, this::closeAboutDialog, true);
    }

    private void drawHandGeneratorDialog() {
        float x = HAND_GENERATOR_PANEL_X;
        float y = HAND_GENERATOR_PANEL_Y;
        float w = HAND_GENERATOR_PANEL_WIDTH;
        float h = HAND_GENERATOR_PANEL_HEIGHT;
        GdxHandGeneratorModel.Example example = handGenerator.current();

        GdxUiDialogStyle.drawPanel(shapes, x, y, w, h, CYAN_DARK, 1f);
        GdxUiDialogStyle.drawInset(shapes, x + 40f, y + 126f,
                w - 80f, 350f, 1f);

        textFit(headingFont,
                uppercase(gameText.translate("gdx.hand_generator.title")),
                WIDTH / 2f, y + h - 58f, GOLD, true, w - 120f);
        textFit(headingFont, gameText.translate(example.translationKey()),
                WIDTH / 2f, y + h - 115f, Color.WHITE, true, w - 160f);
        textFit(smallFont, uppercase(gameText.translate(
                        "gdx.hand_generator.probability",
                        example.probability())),
                WIDTH / 2f, y + h - 162f, GOLD, true, 440f);
        hit(WIDTH / 2f - 220f, y + h - 194f, 440f, 48f,
                () -> openExternalUri(
                        URI.create(GdxHandGeneratorModel.POKER_ODDS_URL),
                        "gdx.about.open_failed"));

        themedButton(x + 42f, y + 34f, 250f, 62f,
                "‹  " + uppercase(gameText.translate(
                        "gdx.hand_generator.previous")),
                ButtonTone.NEUTRAL, handGenerator::previous,
                handGenerator.canPrevious());
        textFit(smallFont,
                (handGenerator.index() + 1) + " / " + handGenerator.size(),
                WIDTH / 2f, y + 122f, MUTED, true, 180f);
        themedButton(x + w - 292f, y + 34f, 250f, 62f,
                uppercase(gameText.translate("gdx.hand_generator.next"))
                        + "  ›",
                ButtonTone.NEUTRAL, handGenerator::next,
                handGenerator.canNext());
        themedButton(WIDTH / 2f - 110f, y + 34f, 220f, 62f,
                uppercase(gameText.translate("ui.volver")),
                ButtonTone.FEATURED, this::closeHandGenerator, true);
    }

    private void drawHandGeneratorCards() {
        GdxHandGeneratorModel.Example example = handGenerator.current();
        batch.setColor(Color.WHITE);
        batch.setShader(roundedTextureShader);
        roundedTextureShader.setUniformf("u_cornerRadius", 0.045f);
        roundedTextureShader.setUniformf("u_edgeSoftness", 0.004f);
        for (int index = 0; index < example.cards().size(); index++) {
            Rectangle bounds = handGeneratorCardBounds(
                    example.cards().size(), index);
            batch.draw(handGeneratorCardTexture(example.cards().get(index)),
                    bounds.x, bounds.y, bounds.width, bounds.height);
        }
        batch.flush();
        batch.setShader(null);
    }

    static Rectangle handGeneratorCardBounds(int cardCount, int cardIndex) {
        if (cardCount < 1 || cardCount > 5
                || cardIndex < 0 || cardIndex >= cardCount) {
            throw new IllegalArgumentException("Invalid hand card index");
        }
        float areaWidth = HAND_GENERATOR_PANEL_WIDTH
                - 2f * HAND_GENERATOR_CARD_AREA_INSET;
        float cardWidth = Math.min(HAND_GENERATOR_CARD_MAX_WIDTH,
                (areaWidth - HAND_GENERATOR_CARD_GAP * (cardCount - 1))
                        / cardCount);
        float cardHeight = cardWidth / HAND_GENERATOR_CARD_ASPECT;
        float rowWidth = cardWidth * cardCount
                + HAND_GENERATOR_CARD_GAP * (cardCount - 1);
        float rowX = HAND_GENERATOR_PANEL_X
                + (HAND_GENERATOR_PANEL_WIDTH - rowWidth) / 2f;
        return new Rectangle(
                rowX + cardIndex * (cardWidth + HAND_GENERATOR_CARD_GAP),
                HAND_GENERATOR_CARD_Y, cardWidth, cardHeight);
    }

    private Texture handGeneratorCardTexture(String code) {
        String deck = configuredDeck();
        if (!GdxGamePresentationSettings.OFFICIAL_DECKS.contains(deck)) {
            deck = "goliat";
        }
        String key = deck + "/" + code;
        String selectedDeck = deck;
        return handGeneratorCardTextures.computeIfAbsent(key, ignored -> {
            Texture texture = new Texture(Gdx.files.internal(
                    "images/decks/" + selectedDeck + "/hq/" + code
                            + ".jpg"), true);
            texture.setFilter(TextureFilter.MipMapLinearNearest,
                    TextureFilter.Linear);
            return texture;
        });
    }

    private void openHandGenerator() {
        handGenerator.regenerate();
        handGeneratorOpen = true;
        aboutEasterEggClicks = 0;
        disposeAboutEasterEgg();
    }

    private void closeHandGenerator() {
        handGeneratorOpen = false;
    }

    private void drawFrontendVersionLabel() {
        textFit(versionFont, GdxProductVersionBrand.label(presentationSettings),
                GdxProductVersionBrand.X,
                GdxProductVersionBrand.BASELINE_Y,
                new Color((GdxProductVersionBrand.RGB << 8)
                        | Math.round(GdxProductVersionBrand.ALPHA * 255f)),
                false, GdxProductVersionBrand.MAX_WIDTH);
    }

    static boolean renderMainMenuContent(boolean aboutOpen,
            boolean updatePromptOpen) {
        return !aboutOpen && !updatePromptOpen;
    }

    static boolean menuRevealBlocksInteraction(boolean menuSurface,
            float revealProgress, boolean foregroundModal) {
        return menuSurface && revealProgress < 0.98f && !foregroundModal;
    }

    private boolean menuRevealBlocksInteraction() {
        return menuRevealBlocksInteraction(surface == Surface.MENU,
                menuRevealProgress(), aboutOpen || updatePromptOpen);
    }

    private Rectangle aboutModIconBounds(float coronaLogoHeight) {
        if (aboutModIcon == null) return new Rectangle();
        float scale = Math.min(180f / aboutModIcon.getWidth(),
                72f / aboutModIcon.getHeight());
        float width = aboutModIcon.getWidth() * scale;
        float height = aboutModIcon.getHeight() * scale;
        return new Rectangle(WIDTH / 2f + 16f,
                ABOUT_LOGO_Y + (coronaLogoHeight - height) / 2f,
                width, height);
    }

    private static Texture externalTexture(java.nio.file.Path path) {
        if (path == null) return null;
        try {
            Texture texture = new Texture(Gdx.files.absolute(path.toString()));
            texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
            return texture;
        } catch (RuntimeException failure) {
            LOGGER.log(Level.WARNING, "Could not load external image {0}",
                    path);
            return null;
        }
    }

    static String aboutSystemText() {
        return System.getProperty("os.name", "") + " "
                + System.getProperty("os.version", "") + " "
                + System.getProperty("os.arch", "") + " / "
                + System.getProperty("java.vm.name", "") + " "
                + System.getProperty("java.version", "");
    }

    static String aboutRuntimeText() {
        Runtime runtime = Runtime.getRuntime();
        long usedMiB = (runtime.totalMemory() - runtime.freeMemory())
                / (1024L * 1024L);
        return usedMiB + " MiB  -  " + Thread.activeCount();
    }

    static float nativeImageScale(int imageWidth, int imageHeight,
            int viewportWidth, int viewportHeight, int margin) {
        if (imageWidth <= 0 || imageHeight <= 0) return 1f;
        float availableWidth = Math.max(1, viewportWidth - 2 * margin);
        float availableHeight = Math.max(1, viewportHeight - 2 * margin);
        return Math.min(1f, Math.min(availableWidth / imageWidth,
                availableHeight / imageHeight));
    }

    private float wrappedText(BitmapFont font, String value, float x,
            float topY, float maxWidth, float lineHeight, int maxLines,
            Color color) {
        List<String> lines = wrapText(font, value, maxWidth, maxLines);
        float y = topY;
        for (String line : lines) {
            text(font, line, x, y, color, false);
            y -= lineHeight;
        }
        return y;
    }

    private float centeredWrappedText(BitmapFont font, String value,
            float centerX, float topY, float maxWidth, float lineHeight,
            int maxLines, Color color) {
        List<String> lines = wrapText(font, value, maxWidth, maxLines);
        float y = topY;
        for (String line : lines) {
            textFit(font, line, centerX, y, color, true, maxWidth);
            y -= lineHeight;
        }
        return y;
    }

    private List<String> wrapText(BitmapFont font, String value,
            float maxWidth, int maxLines) {
        String normalized = Objects.requireNonNullElse(value, "").trim();
        if (normalized.isEmpty() || maxLines <= 0) return List.of();
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String[] words = normalized.split("\\s+");
        for (int index = 0; index < words.length; index++) {
            String word = words[index];
            String candidate = current.isEmpty()
                    ? word : current + " " + word;
            if (fits(font, candidate, maxWidth) || current.isEmpty()) {
                current.setLength(0);
                current.append(candidate);
                continue;
            }
            if (lines.size() == maxLines - 1) {
                for (int rest = index; rest < words.length; rest++) {
                    current.append(' ').append(words[rest]);
                }
                lines.add(ellipsize(font, current.toString(), maxWidth));
                return lines;
            }
            lines.add(current.toString());
            current.setLength(0);
            current.append(word);
        }
        if (lines.size() < maxLines && !current.isEmpty()) {
            lines.add(ellipsize(font, current.toString(), maxWidth));
        }
        return lines;
    }

    private void drawStartupMenuReveal() {
        float progress = menuRevealProgress();
        if (surface != Surface.MENU || progress >= 1f
                || !renderMainMenuContent(aboutOpen, updatePromptOpen)) {
            return;
        }
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.begin();
        batch.setColor(1f, 1f, 1f, 1f - progress);
        drawFelt(WIDTH, HEIGHT);
        float logoHeight = MENU_LOGO_WIDTH * logo.getHeight() / logo.getWidth();
        batch.setColor(Color.WHITE);
        batch.draw(logo, MENU_LOGO_X, HEIGHT - MENU_LOGO_TOP - logoHeight,
                MENU_LOGO_WIDTH, logoHeight);
        batch.end();
    }

    private float menuRevealProgress() {
        if (startupMenuWaitingForUpdate || startupMenuDeferredByUpdate) {
            return 0f;
        }
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
        textFit(titleFont, lobbyTitle, WIDTH / 2f + 4f, 1000f,
                new Color(0x000000aa), true, 900f);
        textFit(titleFont, lobbyTitle, WIDTH / 2f, 1004f, GOLD, true, 900f);
        drawLobbyDigitalClock(LOBBY_CLOCK.format(Instant.now()));

        lobbyPanel(35f, 180f, 430f, 650f,
                uppercase(gameText.translate("game.timba")));
        float connectionY = state.host() ? LOBBY_CONNECTION_Y : 694f;
        float connectionHeight = state.host()
                ? LOBBY_CONNECTION_HEIGHT : 62f;
        outerBox(LOBBY_LEFT_CONTENT_X, connectionY,
                LOBBY_LEFT_CONTENT_WIDTH, connectionHeight,
                new Color(0x31445fcc), new Color(0x081525a8));
        float connectionLabelX = LOBBY_LEFT_CONTENT_X + 16f;
        float connectionValueX = LOBBY_LEFT_CONTENT_X + 164f;
        float serverBaseline = 733f;
        text(tinyFont, uppercase(gameText.translate("ui.servidor")),
                connectionLabelX, serverBaseline, MUTED, false);
        if (state.host()) {
            String networkStatus = lobbyNetworkStatusText(
                    state.statusDetail(), gameText);
            if (!networkStatus.isBlank()) {
                textFit(tinyFont, uppercase(networkStatus), 326f,
                        serverBaseline,
                        "UPNP_OK".equals(state.statusDetail())
                                ? new Color(0x65e89fff) : ORANGE,
                        true, 102f);
            }
        }
        float serverAddressWidth = state.host() ? 116f : 190f;
        textFit(smallFont, state.serverAddress(), connectionValueX,
                serverBaseline,
                Color.WHITE, false, serverAddressWidth);
        if (state.host()) {
            text(tinyFont, uppercase(gameText.translate(
                    "gdx.lobby.public_address")), connectionLabelX, 695f,
                    MUTED, false);
            String publicAddress = lobbyPublicAddressLoading
                    ? gameText.translate("gdx.loading")
                    : lobbyPublicAddress.isBlank()
                            ? gameText.translate(
                                    "gdx.lobby.public_address_unavailable")
                            : lobbyPublicAddress;
            textFit(smallFont, publicAddress, connectionValueX, 695f,
                    lobbyPublicAddress.isBlank() ? DISABLED : Color.WHITE,
                    false, LOBBY_LEFT_CONTENT_WIDTH - 180f);
            hit(LOBBY_LEFT_CONTENT_X, connectionY,
                    LOBBY_LEFT_CONTENT_WIDTH, connectionHeight,
                    this::copyLobbyConnectionData);
        }
        drawLobbyGameInfo(state, LOBBY_LEFT_CONTENT_X,
                state.host() ? LOBBY_GAME_INFO_Y : 516f,
                LOBBY_LEFT_CONTENT_WIDTH,
                state.host() ? LOBBY_GAME_INFO_HEIGHT : 138f);
        if (state.host()) {
            lobbyPasswordButton(LOBBY_LEFT_ACTION_X, LOBBY_PASSWORD_Y,
                    LOBBY_LEFT_ACTION_WIDTH, LOBBY_PASSWORD_HEIGHT,
                    uppercase(gameText.translate(lobbyPasswordActionKey(
                            connection == null ? "" : connection.password()))),
                    lobbyPasswordEnabled(connection == null
                            ? "" : connection.password()),
                    this::openLobbyPasswordDialog,
                    !lobbyCommandPending && !state.startingOrStarted());
            lobbyBotButton(LOBBY_LEFT_ACTION_X, LOBBY_BOT_BUTTON_Y,
                    LOBBY_LEFT_ACTION_WIDTH,
                    LOBBY_BOT_BUTTON_HEIGHT,
                    uppercase(gameText.translate("ui.anadir_bot")),
                    () -> submitLobbyCommand(new LobbyCommand.AddBot(), null),
                    !lobbyCommandPending
                            && state.participants().size() < LobbySnapshot.MAX_PARTICIPANTS
                            && !state.startingOrStarted());
            boolean kickEnabled = selectedRemoteParticipant(state) != null
                    && !state.startingOrStarted();
            themedButton(LOBBY_LEFT_ACTION_X, LOBBY_KICK_BUTTON_Y,
                    LOBBY_LEFT_ACTION_WIDTH,
                    LOBBY_KICK_BUTTON_HEIGHT,
                    uppercase(gameText.translate("ui.expulsar_jugador")),
                    ButtonTone.DANGER,
                    this::kickSelectedParticipant,
                    !lobbyCommandPending && kickEnabled);
            themedButton(LOBBY_LEFT_ACTION_X, LOBBY_PLAY_BUTTON_Y,
                    LOBBY_LEFT_ACTION_WIDTH,
                    LOBBY_PLAY_BUTTON_HEIGHT,
                    uppercase(gameText.translate("ui.a_jugar")),
                    ButtonTone.POSITIVE,
                    () -> lobbyConfirmation = LobbyConfirmation.START,
                    !lobbyCommandPending && state.participants().size() >= 2
                            && !state.startingOrStarted());
        } else {
            textFit(uiFont, lobbyPhaseText(state), 250f, 306f,
                    MUTED, true, 340f);
        }

        lobbyPanel(495f, 180f, 900f, 650f,
                uppercase(gameText.translate("chat.chat_de_la_timba")));
        if (lobbyImageMode) {
            drawLobbyImageGallery(525f, 315f, 840f, 430f);
        } else if (!lobbyEmojiPickerOpen) {
            drawLobbyMessages(state.chat(), 525f, 744f, 840f);
        }
        drawLobbyChatInput(525f, 215f, 345f);
        compactButton(880f, 215f, 95f, 70f,
                uppercase(gameText.translate(lobbyImageMode
                        ? "gdx.table.chat.text" : "gdx.lobby.emoji")), false,
                () -> {
                    boolean wasImageMode = lobbyImageMode;
                    lobbyImageMode = false;
                    lobbyImageClearConfirmation = false;
                    lobbyEmojiPickerOpen = !wasImageMode
                            && !lobbyEmojiPickerOpen;
                    activateField("lobbyChat");
                });
        compactButton(985f, 215f, 180f, 70f,
                uppercase(gameText.translate(lobbyImageMode
                        ? "gdx.lobby.search_images" : "gdx.lobby.image")),
                false,
                () -> {
                    if (lobbyImageMode) {
                        openGoogleImages();
                        return;
                    }
                    lobbyEmojiPickerOpen = false;
                    lobbyImageMode = !lobbyImageMode;
                    lobbyImageGalleryContentDelayFrames = lobbyImageMode
                            ? LOBBY_IMAGE_GALLERY_CONTENT_DELAY_FRAMES : 0;
                    activateField(lobbyImageMode ? "lobbyImage" : "lobbyChat");
                    if (lobbyImageMode) refreshLobbyHistoryMedia();
                });
        compactButton(1175f, 215f, 85f, 70f,
                uppercase(gameText.translate(lobbyVoiceLive || lobbyVoiceOpening
                        ? "audio.preview_parar" : "gdx.lobby.voice")), false,
                this::toggleLobbyVoiceRecording, canUseLobbyVoice()
                        && !lobbyCommandPending && !lobbyVoiceStopping);
        compactButton(1270f, 215f, 95f, 70f,
                uppercase(gameText.translate("ui.enviar")), true,
                this::sendLobbyComposer,
                !lobbyCommandPending && !(lobbyImageMode
                        ? lobbyImageDraft : lobbyChatDraft).isBlank()
                        && (lobbyImageMode || lobbyTextSendReady(
                                elapsed, lobbyTextSendAllowedAt,
                                lobbyChatDraft)));
        if (lobbyEmojiPickerOpen) {
            drawLobbyEmojiPicker(525f, 315f, 840f, 390f);
        } else if (!lobbyVoiceStatus.isEmpty()) {
            drawLobbyVoiceStatus(535f, 312f, 820f);
        }

        lobbyPanel(1425f, 180f, 460f, 650f, "");
        textFit(actionFont,
                uppercase(gameText.translate("ui.participantes_conectados")),
                1455f, LOBBY_ROSTER_TITLE_BASELINE, GOLD, false, 335f);
        textFit(actionFont, state.participants().size() + "/"
                + LobbySnapshot.MAX_PARTICIPANTS, LOBBY_ROSTER_COUNT_X,
                LOBBY_ROSTER_COUNT_BASELINE, CYAN, true,
                LOBBY_ROSTER_COUNT_WIDTH);
        shapes.setColor(new Color(0x31445f90));
        shapes.rect(1455f, 759f, 400f, 1f);
        float participantY = 705f;
        for (LobbyParticipant participant : state.participants()) {
            drawLobbyParticipant(participant, 1450f, participantY, 410f, 54f);
            participantY -= 57f;
        }

        themedButton(35f, 55f, 220f, 70f,
                uppercase(gameText.translate("ui.salir")), ButtonTone.DANGER,
                () -> lobbyConfirmation = LobbyConfirmation.LEAVE,
                !lobbyCommandPending);
        iconButton(285f, 55f, 360f, 70f,
                uppercase(gameText.translate("menu.visor_capturas")), 6,
                ButtonTone.NEUTRAL, this::openScreenshotViewer,
                !lobbyCommandPending);
        button(1640f, 55f, 245f, 70f,
                uppercase(gameText.translate("menu.ajustes")), false,
                this::openSettings);
        drawSoundControl(1553f, 62f, 55f, 58f, false);

    }

    private void drawLobbyDigitalClock(String time) {
        final float digitWidth = 74f;
        final float digitHeight = 126f;
        final float digitGap = 14f;
        final float colonWidth = 20f;
        final float displayWidth = digitWidth * 4f + digitGap * 4f
                + colonWidth;
        final float x = WIDTH - 48f - displayWidth;
        final float y = 912f;

        outerBox(x - 14f, y - 10f, displayWidth + 28f, digitHeight + 20f,
                new Color(0x785a20aa), new Color(0x080b0ecc));
        GdxSevenSegmentDisplay.draw(shapes, time, x, y,
                digitWidth, digitHeight, digitGap, colonWidth,
                new Color(0xffbd38ff), new Color(0x59461f40),
                new Color(0xffa51f44),
                GdxSevenSegmentDisplay.colonsVisible(
                        System.currentTimeMillis()));
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
        int effectiveLatency = lobbyEffectiveLatencyMillis(participant);
        tooltip(x + 43f, y + h / 2f - 12f, 24f, 24f,
                "gdx.table.latency_tooltip",
                effectiveLatency < 0 ? "—" : Integer.toString(effectiveLatency));
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
        if (!participant.bot() && (participant.identityPublicKey() != null
                || participant.sessionFingerprint() != null)) {
            secondaryHit(x, y, w, h, () -> openFingerprintDialog(participant));
        }
    }

    private void openFingerprintDialog(LobbyParticipant participant) {
        byte[] identity = participant.identityPublicKey();
        byte[] session = participant.sessionFingerprint();
        FingerprintMode mode = identity != null
                ? FingerprintMode.IDENTITY : FingerprintMode.SESSION;
        fingerprintDialog = new FingerprintDialog(participant.nickname(), identity,
                identity == null ? null : IdenticonFingerprint.fromSeed(identity),
                session == null ? null : IdenticonFingerprint.fromDigest(session),
                mode);
        clearActiveField();
    }

    private void drawFingerprintDialog() {
        FingerprintDialog dialog = fingerprintDialog;
        if (dialog == null) return;
        IdenticonFingerprint fingerprint = dialog.active();
        hits.clear();
        secondaryHits.clear();
        GdxUiDialogStyle.drawBackdrop(shapes, WIDTH, HEIGHT, 1f);
        GdxUiDialogStyle.drawPanel(shapes, 535f, 165f, 850f, 750f,
                CYAN, 1f);
        textFit(titleFont, dialog.nickname(), 960f, 842f, GOLD, true, 720f);

        if (dialog.identity() != null && dialog.session() != null) {
            themedButton(610f, 760f, 330f, 58f,
                    uppercase(gameText.translate("gdx.identicon.identity")),
                    dialog.mode() == FingerprintMode.IDENTITY
                            ? ButtonTone.FEATURED : ButtonTone.NEUTRAL,
                    () -> fingerprintDialog = dialog.withMode(
                            FingerprintMode.IDENTITY), true);
            themedButton(980f, 760f, 330f, 58f,
                    uppercase(gameText.translate("gdx.identicon.session")),
                    dialog.mode() == FingerprintMode.SESSION
                            ? ButtonTone.FEATURED : ButtonTone.NEUTRAL,
                    () -> fingerprintDialog = dialog.withMode(
                            FingerprintMode.SESSION), true);
        } else {
            textFit(headingFont, uppercase(gameText.translate(
                    dialog.mode() == FingerprintMode.IDENTITY
                            ? "gdx.identicon.identity"
                            : "gdx.identicon.session")),
                    960f, 800f, CYAN, true, 650f);
        }

        boolean mosaic = dialog.mode() == FingerprintMode.SESSION
                && lobby != null && lobby.host();
        if (mosaic) {
            drawSessionFingerprintMosaic();
        } else {
            float cell = 52f;
            float iconSize = cell * IdenticonFingerprint.GRID_SIZE;
            float iconX = WIDTH / 2f - iconSize / 2f;
            float iconY = 342f;
            drawIdenticon(fingerprint, iconX, iconY, cell, 8f);
            textFit(uiFont, fingerprint.formatted(), 960f, 305f,
                    Color.WHITE, true, 760f);
        }
        textFit(smallFont, gameText.translate(
                dialog.mode() == FingerprintMode.IDENTITY
                        ? "gdx.identicon.identity_help"
                        : "gdx.identicon.session_help"),
                960f, 255f, MUTED, true, 760f);
        if (dialog.mode() == FingerprintMode.IDENTITY
                && dialog.identityPublicKey() != null) {
            boolean verified = identityTrust.isVerified(dialog.nickname(),
                    dialog.identityPublicKey());
            themedButton(610f, 185f, 330f, 58f,
                    uppercase(gameText.translate(verified
                            ? "ui.identicon.ya_verificada"
                            : "ui.identicon.verificar_button")),
                    verified ? ButtonTone.POSITIVE : ButtonTone.FEATURED,
                    () -> verifyFingerprintIdentity(dialog), !verified);
            button(980f, 185f, 330f, 58f,
                    uppercase(gameText.translate("ui.cerrar")), false,
                    () -> fingerprintDialog = null);
        } else {
            button(780f, 185f, 360f, 58f,
                    uppercase(gameText.translate("ui.cerrar")), false,
                    () -> fingerprintDialog = null);
        }
    }

    private void drawSessionFingerprintMosaic() {
        List<LobbyParticipant> channels = lobby.participants().stream()
                .filter(participant -> participant.sessionFingerprint() != null)
                .limit(9).toList();
        if (channels.isEmpty()) {
            textFit(uiFont, gameText.translate("ui.identicon.mosaico_vacio"),
                    960f, 520f, MUTED, true, 700f);
            return;
        }
        for (int index = 0; index < channels.size(); index++) {
            LobbyParticipant participant = channels.get(index);
            int column = index % 3;
            int row = index / 3;
            float tileX = 605f + column * 245f;
            float tileY = 590f - row * 150f;
            IdenticonFingerprint fingerprint = IdenticonFingerprint.fromDigest(
                    participant.sessionFingerprint());
            textFit(smallFont, participant.nickname(), tileX + 105f,
                    tileY + 128f, GOLD, true, 210f);
            drawIdenticon(fingerprint, tileX + 59f, tileY + 22f, 13f, 4f);
            String formatted = fingerprint.formatted();
            textFit(tinyFont, formatted.substring(0, 19), tileX + 105f,
                    tileY + 15f, MUTED, true, 220f);
            textFit(tinyFont, formatted.substring(20), tileX + 105f,
                    tileY - 3f, MUTED, true, 220f);
        }
    }

    private void drawIdenticon(IdenticonFingerprint fingerprint, float x,
            float y, float cell, float padding) {
        float size = cell * IdenticonFingerprint.GRID_SIZE;
        shapes.setColor(Color.WHITE);
        shapes.rect(x - padding, y - padding,
                size + padding * 2f, size + padding * 2f);
        for (int column = 0; column < IdenticonFingerprint.GRID_SIZE; column++) {
            for (int row = 0; row < IdenticonFingerprint.GRID_SIZE; row++) {
                if (!fingerprint.filled(column, row)) continue;
                shapes.setColor(identiconColor(fingerprint.foregroundArgb(row)));
                shapes.rect(x + column * cell, y + row * cell, cell, cell);
            }
        }
    }

    private void verifyFingerprintIdentity(FingerprintDialog dialog) {
        if (dialog.identityPublicKey() != null
                && identityTrust.markVerified(dialog.nickname(),
                        dialog.identityPublicKey())) {
            showToast(gameText.translate("ui.identicon.ya_verificada"));
        } else {
            showToast(gameText.translate("gdx.identicon.verify_failed"));
        }
    }

    private static Color identiconColor(int argb) {
        return new Color((argb >> 16 & 0xff) / 255f,
                (argb >> 8 & 0xff) / 255f,
                (argb & 0xff) / 255f, 1f);
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
        if (!participant.latencyAvailable()
                && !participant.local() && !participant.bot()) {
            return LATENCY_STALE;
        }
        int best = lobbyEffectiveLatencyMillis(participant);
        if (best < 0) return LATENCY_RED;
        if (best <= 100) return LATENCY_GREEN;
        if (best <= 250) return LATENCY_YELLOW;
        if (best <= 400) return LATENCY_ORANGE;
        return LATENCY_RED;
    }

    static int lobbyEffectiveLatencyMillis(LobbyParticipant participant) {
        if (!participant.connected()) return -1;
        if (!participant.latencyAvailable()) {
            // The host and its bots have no network channel to sample. They
            // are local endpoints, so their actual transport latency is zero
            // rather than unknown/stale.
            return participant.local() || participant.bot() ? 0 : -1;
        }
        int first = participant.latency();
        int second = participant.previousLatency();
        return first < 0 ? second : second < 0 ? first
                : Math.min(first, second);
    }

    private static Texture filteredTexture(String asset) {
        Texture texture = new Texture(Gdx.files.internal(asset));
        texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        return texture;
    }

    private static Texture silhouetteTexture(String asset, Color tint) {
        Pixmap source = new Pixmap(Gdx.files.internal(asset));
        Pixmap tinted = new Pixmap(source.getWidth(), source.getHeight(),
                Pixmap.Format.RGBA8888);
        tinted.setColor(0f, 0f, 0f, 0f);
        tinted.fill();
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int alpha = source.getPixel(x, y) & 0xff;
                if (alpha == 0) continue;
                tinted.setColor(tint.r, tint.g, tint.b,
                        tint.a * alpha / 255f);
                tinted.drawPixel(x, y);
            }
        }
        Texture texture = new Texture(tinted, true);
        texture.setFilter(TextureFilter.MipMapLinearLinear,
                TextureFilter.Linear);
        source.dispose();
        tinted.dispose();
        return texture;
    }

    private void drawLobbyMessages(List<LobbyChatMessage> messages, float x,
            float top, float width) {
        final float viewportHeight = 420f;
        final float scrollbarGutter = 34f;
        float contentWidth = width - scrollbarGutter;
        List<LobbyMessageLayout> layouts = new ArrayList<>(messages.size());
        List<Float> heights = new ArrayList<>(messages.size());
        for (LobbyChatMessage message : messages) {
            LobbyMessageLayout layout = lobbyMessageLayout(message,
                    contentWidth - 48f);
            layouts.add(layout);
            heights.add(layout.height());
        }
        float contentHeight = lobbyChatContentHeight(heights);
        if (messages.size() > lobbyChatMessageCount && lobbyChatScroll > 0f) {
            lobbyChatScroll += Math.max(0f,
                    contentHeight - lobbyChatContentHeight);
        }
        lobbyChatMessageCount = messages.size();
        lobbyChatContentHeight = contentHeight;
        float maximumScroll = lobbyMaximumPixelScroll(contentHeight,
                viewportHeight);
        lobbyChatScroll = MathUtils.clamp(lobbyChatScroll, 0, maximumScroll);
        lobbyChatViewport.set(x - 2f, top - viewportHeight,
                contentWidth + 4f, viewportHeight);
        int textStart = texts.size();
        int avatarStart = lobbyAvatars.size();
        int imageStart = uiImages.size();
        float cursorTop = top + maximumScroll - lobbyChatScroll;
        for (int i = 0; i < messages.size(); i++) {
            LobbyChatMessage message = messages.get(i);
            LobbyMessageLayout layout = layouts.get(i);
            float messageHeight = layout.height();
            float y = cursorTop - messageHeight;
            cursorTop = y - 10f;
            if (y >= top || y + messageHeight <= top - viewportHeight) {
                continue;
            }
            boolean local = message.nickname().equals(lobby.localNickname());
            if (message.type() == LobbyChatMessage.Type.PLAYER_JOINED
                    || message.type() == LobbyChatMessage.Type.PLAYER_LEFT) {
                drawLobbyPresenceMessage(message, x, y, contentWidth,
                        messageHeight);
                continue;
            }
            String header = message.nickname() + "  -  "
                    + CHAT_TIME.format(message.timestamp());
            float bubbleW = layout.width();
            float bubbleX = local ? x + contentWidth - bubbleW : x + 48f;
            boolean selectedMessage = message.sequence()
                    == selectedLobbyChatSequence;
            Color border = selectedMessage ? GOLD : local ? CYAN_DARK : LINE;
            Color fill = local ? new Color(0x0d2638e8)
                    : new Color(0x09131fe8);
            lobbyChatBubbles.add(new LobbyChatBubbleItem(message.sequence(),
                    bubbleX, y, bubbleW, messageHeight, border, fill));
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
                float lineY = y + messageHeight - 52f;
                for (String line : layout.lines()) {
                    drawLobbyEmojiText(line, bodyX, lineY,
                            bodyWidth, Color.WHITE);
                    lineY -= 32f;
                }
                hit(bubbleX, y, bubbleW, messageHeight,
                        () -> {
                            clearActiveField();
                            selectedLobbyChatSequence = message.sequence();
                        });
                secondaryHit(bubbleX, y, bubbleW, messageHeight, () -> {
                    selectedLobbyChatSequence = message.sequence();
                    copySelectedLobbyChatMessage();
                });
            } else if (message.type() == LobbyChatMessage.Type.IMAGE) {
                drawLobbyImageMessage(message, bodyX,
                        y + 12f, bodyWidth, messageHeight - 55f,
                        Color.WHITE);
            } else if (message.type() == LobbyChatMessage.Type.VOICE) {
                boolean active = lobbyChatVoiceSequence
                        == message.sequence();
                float stopX = bubbleX + bubbleW - 44f;
                float playX = stopX - 46f;
                textFit(smallFont, uppercase(gameText.translate(
                        "audio.notas_de_voz")), bodyX,
                        y + 23f, Color.WHITE, false,
                        Math.max(0f, bodyWidth - 94f));
                lobbyVoiceControls.add(new LobbyVoiceControlItem(
                        playX, stopX, y, active, lobbyChatVoicePaused));
                hit(playX, y + 7f, 40f, messageHeight - 14f,
                        () -> toggleLobbyChatVoice(message));
                hit(stopX, y + 7f, 40f, messageHeight - 14f,
                        this::stopLobbyChatVoice);
            }
        }
        moveTail(texts, textStart, lobbyChatTexts);
        moveTail(lobbyAvatars, avatarStart, lobbyChatAvatars);
        moveTail(uiImages, imageStart, lobbyChatImages);
        drawLobbyChatScrollbar(x + width - 16f, top - viewportHeight,
                viewportHeight, contentHeight, maximumScroll);
    }

    private void drawLobbyChatScrollbar(float x, float y, float height,
            float contentHeight, float maximumScroll) {
        lobbyChatScrollTrack.set(x - 6f, y, 28f, height);
        lobbyChatScrollMaximum = maximumScroll;
        if (maximumScroll <= 0f || contentHeight <= 0f) {
            lobbyChatScrollThumbHeight = height;
            return;
        }
        float thumbHeight = Math.min(height,
                Math.max(38f, height * height / contentHeight));
        lobbyChatScrollThumbHeight = thumbHeight;
        float progress = lobbyChatScroll / maximumScroll;
        float thumbY = y + progress * (height - thumbHeight);
        shapes.setColor(new Color(0x1a2c44cc));
        roundedRect(x, y, 16f, height, 8f);
        shapes.setColor(CYAN);
        roundedRect(x, thumbY, 16f, thumbHeight, 8f);
    }

    private void copySelectedLobbyChatMessage() {
        LobbySnapshot state = lobby;
        if (state == null || selectedLobbyChatSequence < 0L) return;
        state.chat().stream()
                .filter(message -> message.sequence()
                        == selectedLobbyChatSequence)
                .filter(message -> message.type()
                        == LobbyChatMessage.Type.TEXT)
                .findFirst().ifPresent(message -> {
                    Gdx.app.getClipboard().setContents(message.content());
                    showToast(gameText.translate(
                            "gdx.lobby.message_copied"));
                });
    }

    private static <T> void moveTail(List<T> source, int start,
            List<T> destination) {
        if (start >= source.size()) return;
        List<T> tail = source.subList(start, source.size());
        destination.addAll(tail);
        tail.clear();
    }

    static float lobbyChatContentHeight(List<Float> heights) {
        if (heights == null || heights.isEmpty()) return 0f;
        float result = 10f * Math.max(0, heights.size() - 1);
        for (float height : heights) result += Math.max(0f, height);
        return result;
    }

    static float lobbyMaximumPixelScroll(float contentHeight,
            float viewportHeight) {
        return Math.max(0f, contentHeight - Math.max(0f, viewportHeight));
    }

    static float lobbyPixelScrollAfterWheel(float current, float maximum,
            float amountY) {
        return MathUtils.clamp(current - amountY * 48f, 0f,
                Math.max(0f, maximum));
    }

    private void drawLobbyChatLayer() {
        if (!shouldDrawLobbyChatLayer(surface == Surface.LOBBY,
                lobbyImageMode, lobbyEmojiPickerOpen,
                lobbyChatViewport.width, lobbyChatViewport.height)) return;
        int screenX = Math.round(viewport.getScreenX()
                + lobbyChatViewport.x * viewport.getScreenWidth() / WIDTH);
        int screenY = Math.round(viewport.getScreenY()
                + lobbyChatViewport.y * viewport.getScreenHeight() / HEIGHT);
        int screenWidth = Math.max(1, Math.round(lobbyChatViewport.width
                * viewport.getScreenWidth() / WIDTH));
        int screenHeight = Math.max(1, Math.round(lobbyChatViewport.height
                * viewport.getScreenHeight() / HEIGHT));
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glScissor(screenX, screenY, screenWidth, screenHeight);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (LobbyChatBubbleItem item : lobbyChatBubbles) {
            flatOuterBox(item.x, item.y, item.width, item.height,
                    item.border, item.fill);
        }
        for (LobbyVoiceControlItem item : lobbyVoiceControls) {
            shapes.setColor(item.active ? CYAN : Color.WHITE);
            if (item.active && !item.paused) {
                shapes.rect(item.playX + 13f, item.y + 18f, 5f, 19f);
                shapes.rect(item.playX + 23f, item.y + 18f, 5f, 19f);
            } else {
                shapes.triangle(item.playX + 12f, item.y + 17f,
                        item.playX + 12f, item.y + 38f,
                        item.playX + 30f, item.y + 27.5f);
            }
            shapes.setColor(item.active ? GOLD : DISABLED);
            shapes.rect(item.stopX + 12f, item.y + 19f, 17f, 17f);
        }
        shapes.end();
        batch.begin();
        if (!lobbyChatAvatars.isEmpty()) {
            batch.setColor(Color.WHITE);
            for (LobbyAvatarItem item : lobbyChatAvatars) {
                batch.draw(item.texture, item.x, item.y, item.size, item.size);
            }
        }
        batch.setColor(Color.WHITE);
        for (UiImageItem item : lobbyChatImages) {
            batch.draw(item.texture, item.x, item.y, item.width, item.height);
        }
        for (TextItem item : lobbyChatTexts) {
            item.font.setColor(item.color);
            glyph.setText(item.font, item.text);
            float x = item.centered ? item.x - glyph.width / 2f : item.x;
            item.font.draw(batch, item.text, x, item.y);
        }
        batch.end();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
    }

    private void drawSettingsDebugLayer() {
        if (surface != Surface.SETTINGS
                || settingsSession.section()
                        != GdxSettingsContract.Section.DEBUG
                || settingsDebugViewport.width <= 0f
                || settingsDebugViewport.height <= 0f) return;
        int screenX = Math.round(viewport.getScreenX()
                + settingsDebugViewport.x * viewport.getScreenWidth() / WIDTH);
        int screenY = Math.round(viewport.getScreenY()
                + settingsDebugViewport.y * viewport.getScreenHeight() / HEIGHT);
        int screenWidth = Math.max(1, Math.round(settingsDebugViewport.width
                * viewport.getScreenWidth() / WIDTH));
        int screenHeight = Math.max(1, Math.round(settingsDebugViewport.height
                * viewport.getScreenHeight() / HEIGHT));
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glScissor(screenX, screenY, screenWidth, screenHeight);
        batch.begin();
        for (TextItem item : settingsDebugTexts) {
            item.font.setColor(item.color);
            glyph.setText(item.font, item.text);
            float x = item.centered ? item.x - glyph.width / 2f : item.x;
            item.font.draw(batch, item.text, x, item.y);
        }
        batch.end();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
    }

    private void drawSettingsRowsTextLayer() {
        if (surface != Surface.SETTINGS || settingsRowTexts.isEmpty()
                || settingsRowsClip.width <= 0f
                || settingsRowsClip.height <= 0f) return;
        enableWorldScissor(settingsRowsClip);
        batch.begin();
        for (TextItem item : settingsRowTexts) drawTextItem(item);
        batch.end();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
    }

    private void drawStatsChartTextLayer() {
        if (surface != Surface.STATS || statsChartTexts.isEmpty()
                || statsChartViewport.width <= 0f
                || statsChartViewport.height <= 0f) return;
        enableWorldScissor(statsChartViewport);
        batch.begin();
        for (TextItem item : statsChartTexts) drawTextItem(item);
        batch.end();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
    }

    private void enableWorldScissor(Rectangle bounds) {
        int screenX = Math.round(viewport.getScreenX()
                + bounds.x * viewport.getScreenWidth() / WIDTH);
        int screenY = Math.round(viewport.getScreenY()
                + bounds.y * viewport.getScreenHeight() / HEIGHT);
        int screenWidth = Math.max(1, Math.round(bounds.width
                * viewport.getScreenWidth() / WIDTH));
        int screenHeight = Math.max(1, Math.round(bounds.height
                * viewport.getScreenHeight() / HEIGHT));
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glScissor(screenX, screenY, screenWidth, screenHeight);
    }

    static boolean shouldDrawLobbyChatLayer(boolean lobbySurface,
            boolean imageGalleryOpen, boolean emojiPickerOpen,
            float viewportWidth, float viewportHeight) {
        return lobbySurface && !imageGalleryOpen && !emojiPickerOpen
                && viewportWidth > 0f && viewportHeight > 0f;
    }

    static float lobbyMessageHeight(LobbyChatMessage.Type type) {
        return lobbyMessageHeight(type, 1);
    }

    static float lobbyMessageHeight(LobbyChatMessage.Type type,
            int textLineCount) {
        return switch (type) {
            case IMAGE -> 280f;
            case PLAYER_JOINED, PLAYER_LEFT -> 44f;
            case TEXT -> 82f + Math.max(0, textLineCount - 1) * 32f;
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
        headerWidth += 38f;
        float contentWidth = message.type() == LobbyChatMessage.Type.TEXT
                ? composerWidth(message.content()) + 34f : 0f;
        return Math.max(headerWidth, contentWidth);
    }

    private LobbyMessageLayout lobbyMessageLayout(LobbyChatMessage message,
            float availableWidth) {
        if (message.type() == LobbyChatMessage.Type.PLAYER_JOINED
                || message.type() == LobbyChatMessage.Type.PLAYER_LEFT) {
            return new LobbyMessageLayout(availableWidth,
                    lobbyMessageHeight(message.type()), List.of());
        }
        String header = message.nickname() + "  -  "
                + CHAT_TIME.format(message.timestamp());
        float width = lobbyMessageWidth(message.type(),
                lobbyDesiredBubbleWidth(message, header), availableWidth);
        List<String> lines = message.type() == LobbyChatMessage.Type.TEXT
                ? wrapLobbyEmojiText(message.content(), width - 28f)
                : List.of();
        return new LobbyMessageLayout(width,
                lobbyMessageHeight(message.type(), lines.size()), lines);
    }

    private List<String> wrapLobbyEmojiText(String content, float maxWidth) {
        String value = Objects.requireNonNullElse(content, "").strip();
        if (value.isEmpty()) return List.of("");
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        Matcher matcher = CHAT_WRAP_TOKEN.matcher(value);
        while (matcher.find()) {
            String token = matcher.group();
            if (token.isBlank()) token = " ";
            if (line.isEmpty() && token.isBlank()) continue;
            if (composerWidth(line + token) <= maxWidth) {
                line.append(token);
                continue;
            }
            if (!line.isEmpty()) {
                lines.add(line.toString().stripTrailing());
                line.setLength(0);
            }
            if (token.isBlank()) continue;
            if (composerWidth(token) <= maxWidth || EMOJI_TOKEN.matcher(token)
                    .matches()) {
                line.append(token);
                continue;
            }
            for (int offset = 0; offset < token.length();) {
                int next = token.offsetByCodePoints(offset, 1);
                String glyph = token.substring(offset, next);
                if (!line.isEmpty()
                        && composerWidth(line + glyph) > maxWidth) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                line.append(glyph);
                offset = next;
            }
        }
        if (!line.isEmpty()) lines.add(line.toString().stripTrailing());
        if (lines.isEmpty()) lines.add("");
        if (lines.size() <= CHAT_TEXT_MAX_LINES) return List.copyOf(lines);
        List<String> visible = new ArrayList<>(
                lines.subList(0, CHAT_TEXT_MAX_LINES));
        visible.set(CHAT_TEXT_MAX_LINES - 1,
                lobbyEllipsizedLine(visible.get(CHAT_TEXT_MAX_LINES - 1),
                        maxWidth));
        return List.copyOf(visible);
    }

    private String lobbyEllipsizedLine(String line, float maxWidth) {
        String value = line.stripTrailing();
        String suffix = " …";
        while (!value.isEmpty()
                && composerWidth(value + suffix) > maxWidth) {
            Matcher matcher = EMOJI_TOKEN.matcher(value);
            int emojiStart = -1;
            while (matcher.find()) {
                if (matcher.end() == value.length()) emojiStart = matcher.start();
            }
            value = emojiStart >= 0 ? value.substring(0, emojiStart)
                    : value.substring(0, value.offsetByCodePoints(
                            value.length(), -1));
            value = value.stripTrailing();
        }
        return value + suffix;
    }

    private void drawLobbyPresenceMessage(LobbyChatMessage message, float x,
            float y, float width, float height) {
        String action = " " + uppercase(gameText.translate(
                message.type() == LobbyChatMessage.Type.PLAYER_JOINED
                        ? "gdx.lobby.player_joined"
                        : "gdx.lobby.player_left"));
        String time = " (" + CHAT_TIME.format(message.timestamp()) + ")";
        float nickWidth = textWidth(tinyFont, message.nickname());
        float actionWidth = textWidth(tinyFont, action);
        float timeWidth = textWidth(tinyFont, time);
        float maxNickWidth = Math.max(0f,
                width - 42f - actionWidth - timeWidth);
        String visibleNickname = ellipsize(tinyFont, message.nickname(),
                maxNickWidth);
        nickWidth = textWidth(tinyFont, visibleNickname);
        float totalWidth = 34f + 8f + nickWidth + actionWidth + timeWidth;
        float startX = x + Math.max(0f, (width - totalWidth) / 2f);
        lobbyAvatars.add(new LobbyAvatarItem(lobbyMessageAvatar(message),
                startX, y + (height - 30f) / 2f, 30f));
        float textX = startX + 42f;
        textFit(tinyFont, visibleNickname, textX, y + 28f,
                Color.WHITE, false, maxNickWidth);
        textX += nickWidth;
        text(tinyFont, action, textX, y + 28f,
                message.type() == LobbyChatMessage.Type.PLAYER_JOINED
                        ? new Color(0x58d67aff) : new Color(0xf07b72ff),
                false);
        textX += actionWidth;
        text(tinyFont, time, textX, y + 28f, MUTED, false);
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
                    ? gameText.translate("gdx.lobby.image_unavailable")
                    : gameText.translate("gdx.lobby.image_loading");
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

    private void drawLobbyChatInput(float x, float y, float w) {
        String fieldId = lobbyImageMode ? "lobbyImage" : "lobbyChat";
        String draft = lobbyImageMode ? lobbyImageDraft : lobbyChatDraft;
        boolean focused = fieldId.equals(activeField);
        flatOuterBox(x, y, w, 70f,
                focused || hovered(x, y, w, 70f) ? CYAN : LINE,
                pressed(x, y, w, 70f) ? new Color(0x0b1424ff) : PANEL_LIGHT);
        String placeholder = gameText.translate(lobbyImageMode
                ? "gdx.lobby.image_url_placeholder"
                : "gdx.lobby.message_placeholder");
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
            if (draft.isEmpty()) {
                textFit(uiFont, window.text(), x + 22f, y + 44f,
                        DISABLED, false, w - 44f);
            } else {
                text(uiFont, window.text(), x + 22f, y + 44f,
                        Color.WHITE, false);
            }
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

    private void drawLobbyGameInfo(LobbySnapshot state, float x, float y,
            float width, float height) {
        outerBox(x, y, width, height, new Color(0x31445fcc),
                new Color(0x081525a8));
        NewGameTableDraft.Settings settings = state.tableSettings();
        if (settings == null) {
            textFit(smallFont, gameText.translate(
                    "status.recibiendo_info_servidor"),
                    x + 16f, y + height / 2f + 8f, MUTED, false,
                    width - 32f);
            return;
        }
        NewGameTableDraft.BlindLevel blind = settings.blindLevels()
                .get(settings.blindLevelIndex());
        List<LobbyInfoItem> rows = List.of(
                new LobbyInfoItem(gameText.translate("ui.compra"),
                        settings.fixedBuyin()
                                ? Integer.toString(settings.buyin())
                                : gameText.translate(
                                        "gdx.settings.game.summary.value.variable")),
                new LobbyInfoItem(gameText.translate("blinds.ciegas"),
                        money(blind.smallBlind()) + " / "
                                + money(blind.bigBlind())),
                new LobbyInfoItem(gameText.translate("game.manos"),
                        settings.handLimit()
                                ? Integer.toString(settings.handLimitCount())
                                : gameText.translate(
                                        "gdx.lobby.no_hand_limit")));
        for (int index = 0; index < rows.size(); index++) {
            LobbyInfoItem row = rows.get(index);
            lobbyInfoRow(x + 16f,
                    lobbyInfoRowBaseline(y, height, rows.size(), index),
                    width - 32f, row.label(), row.value(),
                    index < rows.size() - 1);
        }
    }

    private void lobbyInfoRow(float x, float baseline, float width,
            String label, String value, boolean separator) {
        if (separator) {
            shapes.setColor(new Color(0x31445f77));
            shapes.rect(x, baseline - 20f, width, 1f);
        }
        textFit(smallFont, label, x, baseline, MUTED, false, 132f);
        textFit(smallFont, value, x + 148f, baseline,
                Color.WHITE, false, width - 148f);
    }

    static float lobbyInfoRowBaseline(float y, float height, int rowCount,
            int rowIndex) {
        if (rowCount <= 0 || rowIndex < 0 || rowIndex >= rowCount) {
            throw new IllegalArgumentException("Invalid lobby information row");
        }
        float usableHeight = height - 28f;
        float rowHeight = usableHeight / rowCount;
        return y + height - 14f - rowHeight * (rowIndex + 0.5f);
    }

    private static String money(double amount) {
        return String.format(Locale.ROOT, "%.2f", amount);
    }

    private String lobbyPhaseText(LobbySnapshot state) {
        return switch (state.phase()) {
            case CONNECTING -> gameText.translate("status.conectando");
            case KEY_EXCHANGE -> gameText.translate("status.intercambio_claves");
            case RECEIVING_SERVER_INFO -> gameText.translate(
                    "status.recibiendo_info_servidor");
            case CONNECTED -> gameText.translate("status.conectado");
            case WAITING_FOR_PLAYERS -> gameText.translate(
                    "status.esperando_jugadores");
            case INITIALIZING_GAME -> gameText.translate(
                    "status.inicializando_juego");
            case RECONNECTING -> gameText.translate("conn.reconectando");
            case IN_GAME -> gameText.translate("game.timba_en_curso");
            case ERROR -> state.statusDetail().isBlank()
                    ? gameText.translate("gdx.lobby.error")
                    : state.statusDetail();
            case CLOSED -> gameText.translate("gdx.lobby.closed");
        };
    }

    static String lobbyNetworkStatusText(String statusDetail,
            GdxGameText text) {
        if (statusDetail == null || statusDetail.isBlank()) return "";
        return switch (statusDetail) {
            case "UPNP_OK" -> text.translate("gdx.lobby.upnp_ok");
            case "UPNP_ERROR" -> text.translate("gdx.lobby.upnp_error");
            default -> statusDetail;
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

    private void copyLobbyConnectionData() {
        LobbySnapshot state = lobby;
        String value = lobbyConnectionClipboardText(
                state != null && state.host(),
                state == null ? "" : state.serverAddress(),
                lobbyPublicAddress);
        if (value.isEmpty()) return;
        Gdx.app.getClipboard().setContents(value);
        showToast(gameText.translate("conn.datos_de_conexion_copiados_en"));
    }

    static String lobbyConnectionClipboardText(boolean host,
            String localEndpoint, String publicAddress) {
        String endpoint = Objects.requireNonNullElse(localEndpoint, "").trim();
        if (!host || endpoint.isEmpty()) return "";
        String publicIp = Objects.requireNonNullElse(publicAddress, "").trim();
        String port = endpointPort(endpoint);
        if (publicIp.isEmpty() || port.isEmpty()) {
            return "[CoronaPoker] " + endpoint;
        }
        String publicEndpoint = publicIp.indexOf(':') >= 0
                && !(publicIp.startsWith("[") && publicIp.endsWith("]"))
                        ? "[" + publicIp + "]:" + port
                        : publicIp + ":" + port;
        if (endpoint.equalsIgnoreCase(publicEndpoint)) {
            return "[CoronaPoker] " + endpoint;
        }
        return "[CoronaPoker] " + endpoint + System.lineSeparator()
                + "[CoronaPoker] " + publicEndpoint;
    }

    private static String endpointPort(String endpoint) {
        int separator = endpoint.lastIndexOf(':');
        if (separator < 0 || separator + 1 >= endpoint.length()) return "";
        String port = endpoint.substring(separator + 1).trim();
        if (!port.matches("\\d{1,5}")) return "";
        try {
            int value = Integer.parseInt(port);
            return value > 0 && value <= 65_535 ? port : "";
        } catch (NumberFormatException invalidPort) {
            return "";
        }
    }

    private void drawLobbyConfirmation() {
        hits.clear();
        GdxUiDialogStyle.drawBackdrop(shapes, WIDTH, HEIGHT, 1f);
        String prompt = uppercase(gameText.translate(
                lobbyConfirmation == LobbyConfirmation.START
                        ? "ui.seguro_que_quieres_empezar_ya"
                        : "ui.seguro_que_quieres_salir_ahora"));
        GdxUiDialogStyle.drawPanel(shapes, 560f, 350f, 800f, 330f,
                CYAN_DARK, 1f);
        textFit(headingFont, prompt, 960f, 560f, Color.WHITE, true, 700f);
        themedButton(635f, 405f, 300f, 75f,
                uppercase(gameText.translate("ui.cancelar")),
                ButtonTone.NEUTRAL,
                () -> lobbyConfirmation = null, true);
        themedButton(985f, 405f, 300f, 75f,
                uppercase(gameText.translate(
                        lobbyConfirmation == LobbyConfirmation.START
                                ? "ui.a_jugar" : "ui.salir")),
                lobbyConfirmation == LobbyConfirmation.START
                        ? ButtonTone.POSITIVE : ButtonTone.DANGER,
                this::confirmLobbyAction, true);
    }

    private void openLobbyPasswordDialog() {
        LobbySnapshot state = lobby;
        if (state == null || !state.host() || state.startingOrStarted()) return;
        lobbyPasswordDraft = connection == null ? "" : connection.password();
        lobbyPasswordDialog = true;
        activateField("lobbyPassword");
    }

    private void closeLobbyPasswordDialog() {
        lobbyPasswordDialog = false;
        lobbyPasswordDraft = "";
        clearActiveField();
    }

    private void drawLobbyPasswordDialog() {
        hits.clear();
        textFieldHits.clear();
        passwordRevealHits.clear();
        editMenuHits.clear();
        GdxUiDialogStyle.drawBackdrop(shapes, WIDTH, HEIGHT, 1f);
        GdxUiDialogStyle.drawPanel(shapes, 560f, 335f, 800f, 390f,
                CYAN_DARK, 1f);
        textFit(headingFont, uppercase(gameText.translate(
                lobbyPasswordActionKey(connection == null
                        ? "" : connection.password()))), 960f, 650f, GOLD,
                true, 700f);
        field(660f, 490f, 600f,
                gameText.translate("auth.input_nueva_password"),
                lobbyPasswordDraft, "lobbyPassword", true);
        textFit(tinyFont, gameText.translate("gdx.lobby.password_hint"),
                960f, 455f, MUTED, true, 650f);
        themedButton(590f, 370f, 165f, 64f,
                gameText.translate("gdx.lobby.generate_password"),
                ButtonTone.NEUTRAL, this::generateLobbyPassword,
                !lobbyCommandPending);
        themedButton(770f, 370f, 165f, 64f,
                gameText.translate("ui.copiar"), ButtonTone.NEUTRAL,
                this::copyLobbyPasswordDraft,
                !lobbyCommandPending && !lobbyPasswordDraft.isBlank());
        themedButton(950f, 370f, 165f, 64f,
                gameText.translate("ui.cancelar"), ButtonTone.NEUTRAL,
                this::closeLobbyPasswordDialog, !lobbyCommandPending);
        themedButton(1130f, 370f, 165f, 64f,
                gameText.translate("ui.guardar"), ButtonTone.POSITIVE,
                this::submitLobbyPassword, !lobbyCommandPending);
    }

    private void submitLobbyPassword() {
        submitLobbyPassword(null);
    }

    private void generateLobbyPassword() {
        lobbyPasswordDraft = strongLobbyPassword(secureRandom);
        Gdx.app.getClipboard().setContents(lobbyPasswordDraft);
        submitLobbyPassword("auth.nueva_password_copiada_en_el");
    }

    private void copyLobbyPasswordDraft() {
        String value = Objects.requireNonNullElse(lobbyPasswordDraft, "");
        if (value.isBlank()) return;
        Gdx.app.getClipboard().setContents(value);
        showToast(gameText.translate(
                "auth.password_copiada_en_el_portapapeles"));
    }

    static String strongLobbyPassword(SecureRandom random) {
        Objects.requireNonNull(random, "random");
        String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder password = new StringBuilder(14);
        for (int index = 0; index < 14; index++) {
            password.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return password.toString();
    }

    private void submitLobbyPassword(String successKey) {
        LobbySnapshot state = lobby;
        if (state == null || !state.host() || state.startingOrStarted()) {
            closeLobbyPasswordDialog();
            return;
        }
        String next = Objects.requireNonNullElse(lobbyPasswordDraft, "");
        submitLobbyCommand(new LobbyCommand.ChangePassword(next), () -> {
            if (connection != null) connection.setPassword(next);
            closeLobbyPasswordDialog();
            showToast(gameText.translate(successKey != null
                    ? successKey
                    : next.isEmpty() ? "auth.password_eliminada"
                            : "gdx.lobby.password_updated"));
        });
    }

    private void drawSettingsDiscardConfirmation() {
        hits.clear();
        textFieldHits.clear();
        passwordRevealHits.clear();
        editMenuHits.clear();
        GdxUiDialogStyle.drawBackdrop(shapes, WIDTH, HEIGHT, 1f);
        GdxUiDialogStyle.drawPanel(shapes, 560f, 350f, 800f, 330f,
                CYAN_DARK, 1f);
        textFit(headingFont, uppercase(gameText.translate(
                "gdx.settings.unsaved.title")), 960f, 578f,
                GOLD, true, 700f);
        textFit(actionFont,
                uppercase(gameText.translate("gdx.settings.unsaved.message")),
                960f, 526f, Color.WHITE, true, 700f);
        themedButton(635f, 405f, 300f, 75f, uppercase(gameText.translate(
                "gdx.settings.unsaved.continue")),
                ButtonTone.NEUTRAL,
                () -> settingsDiscardConfirmation = false, true);
        themedButton(985f, 405f, 300f, 75f, uppercase(gameText.translate(
                "gdx.settings.unsaved.discard")),
                ButtonTone.DANGER, () -> finishClosingSettings(false), true);
    }

    private void openVoiceNotes() {
        voiceNotesOpen = true;
        voiceNotesLoading = true;
        voiceNotesPage = 0;
        CompletableFuture.supplyAsync(() -> {
            try {
                return voiceNoteLibrary.list();
            } catch (IOException failure) {
                throw new CompletionException(failure);
            }
        }, recoveryExecutor).whenComplete((entries, failure) ->
                Gdx.app.postRunnable(() -> {
                    if (!voiceNotesOpen) return;
                    voiceNotesLoading = false;
                    if (failure == null) {
                        voiceNotes = entries;
                    } else {
                        voiceNotes = List.of();
                        showToast(gameText.translate(
                                "audio.borrar_nota_error"));
                    }
                }));
    }

    private void closeVoiceNotes() {
        GdxVoicePlayback.stop();
        voiceNotePlaying = null;
        voiceNoteDeleteConfirmation = null;
        voiceNotesPurgeConfirmation = false;
        voiceNotesOpen = false;
    }

    private void drawVoiceNotesDialog() {
        GdxUiDialogStyle.drawBackdrop(shapes, WIDTH, HEIGHT, 1f);
        float x = 310f;
        float y = 130f;
        float w = 1300f;
        float h = 820f;
        GdxUiDialogStyle.drawPanel(shapes, x, y, w, h, CYAN_DARK, 1f);
        textFit(headingFont, uppercase(gameText.translate("audio.ver_notas")),
                x + w / 2f, y + h - 70f, GOLD, true, w - 90f);

        if (voiceNoteDeleteConfirmation != null
                || voiceNotesPurgeConfirmation) {
            String message = voiceNotesPurgeConfirmation
                    ? gameText.translate("audio.purgar_notas_confirm")
                    : gameText.translate("audio.borrar_nota_confirm",
                            voiceNoteDeleteConfirmation.nickname());
            textFit(actionFont, message, x + w / 2f, y + 475f,
                    Color.WHITE, true, w - 150f);
            themedButton(x + 250f, y + 300f, 360f, 74f,
                    uppercase(gameText.translate("ui.cancelar")),
                    ButtonTone.NEUTRAL, () -> {
                        voiceNoteDeleteConfirmation = null;
                        voiceNotesPurgeConfirmation = false;
                    }, !voiceNotesLoading);
            themedButton(x + 690f, y + 300f, 360f, 74f,
                    uppercase(gameText.translate("audio.borrar_nota")),
                    ButtonTone.DANGER, this::confirmVoiceNoteDeletion,
                    !voiceNotesLoading);
            return;
        }

        if (voiceNotesLoading) {
            textFit(actionFont,
                    uppercase(gameText.translate("audio.ver_notas")) + "…",
                    x + w / 2f, y + 460f, MUTED, true, w - 140f);
        } else if (voiceNotes.isEmpty()) {
            textFit(actionFont, gameText.translate("audio.no_notas_voz"),
                    x + w / 2f, y + 460f, MUTED, true, w - 140f);
        } else {
            final int rowsPerPage = 5;
            int pageCount = Math.max(1,
                    (voiceNotes.size() + rowsPerPage - 1) / rowsPerPage);
            voiceNotesPage = MathUtils.clamp(voiceNotesPage, 0,
                    pageCount - 1);
            int start = voiceNotesPage * rowsPerPage;
            int end = Math.min(voiceNotes.size(), start + rowsPerPage);
            float rowY = y + h - 178f;
            for (int index = start; index < end; index++) {
                GdxVoiceNoteLibrary.Entry entry = voiceNotes.get(index);
                outerBox(x + 46f, rowY - 52f, w - 92f, 86f, LINE,
                        PANEL_LIGHT);
                String date = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                        .withZone(ZoneId.systemDefault())
                        .format(java.time.Instant.ofEpochMilli(
                                entry.timestampMillis()));
                textFit(uiFont, entry.nickname(), x + 70f, rowY,
                        Color.WHITE, false, 410f);
                textFit(smallFont, date + "  -  "
                        + formatVoiceDuration(entry.durationMillis()),
                        x + 500f, rowY, MUTED, false, 350f);
                boolean playing = entry.equals(voiceNotePlaying);
                themedButton(x + w - 420f, rowY - 42f, 160f, 64f,
                        uppercase(gameText.translate(playing
                                ? "audio.preview_parar"
                                : "audio.preview_escuchar")),
                        ButtonTone.NEUTRAL,
                        () -> toggleVoiceNotePreview(entry), true);
                themedButton(x + w - 240f, rowY - 42f, 160f, 64f,
                        uppercase(gameText.translate("audio.borrar_nota")),
                        ButtonTone.DANGER,
                        () -> voiceNoteDeleteConfirmation = entry, true);
                rowY -= 102f;
            }
            if (pageCount > 1) {
                themedButton(x + 48f, y + 42f, 80f, 58f, "‹",
                        ButtonTone.NEUTRAL,
                        () -> voiceNotesPage = Math.max(0,
                                voiceNotesPage - 1), voiceNotesPage > 0);
                textFit(smallFont, (voiceNotesPage + 1) + " / " + pageCount,
                        x + 180f, y + 78f, MUTED, true, 90f);
                themedButton(x + 232f, y + 42f, 80f, 58f, "›",
                        ButtonTone.NEUTRAL,
                        () -> voiceNotesPage = Math.min(pageCount - 1,
                                voiceNotesPage + 1),
                        voiceNotesPage + 1 < pageCount);
            }
        }
        themedButton(x + w - 260f, y + 36f, 210f, 68f,
                uppercase(gameText.translate("ui.cerrar")),
                ButtonTone.FEATURED, this::closeVoiceNotes, true);
    }

    private static String formatVoiceDuration(long millis) {
        long seconds = Math.max(0L, Math.round(millis / 1000d));
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60,
                seconds % 60);
    }

    private void toggleVoiceNotePreview(GdxVoiceNoteLibrary.Entry entry) {
        if (!audioOutputAvailable()) {
            showToast(gameText.translate("gdx.audio.no_output_device"));
            return;
        }
        if (entry.equals(voiceNotePlaying)) {
            GdxVoicePlayback.stop();
            voiceNotePlaying = null;
            return;
        }
        GdxVoicePlayback.stop();
        voiceNotePlaying = entry;
        CompletableFuture.supplyAsync(() -> {
            try {
                return voiceNoteLibrary.read(entry);
            } catch (IOException failure) {
                throw new CompletionException(failure);
            }
        }, recoveryExecutor).thenCompose(wav -> GdxVoicePlayback.play(wav,
                masterVolume(), null)).whenComplete((ignored, failure) ->
                Gdx.app.postRunnable(() -> {
                    if (entry.equals(voiceNotePlaying)) {
                        voiceNotePlaying = null;
                    }
                    if (failure != null && voiceNotesOpen) {
                        showToast(gameText.translate(
                                "gdx.lobby.voice_playback_failed"));
                    }
                }));
    }

    private void confirmVoiceNoteDeletion() {
        GdxVoiceNoteLibrary.Entry entry = voiceNoteDeleteConfirmation;
        boolean purge = voiceNotesPurgeConfirmation;
        voiceNoteDeleteConfirmation = null;
        voiceNotesPurgeConfirmation = false;
        voiceNotesLoading = true;
        GdxVoicePlayback.stop();
        voiceNotePlaying = null;
        CompletableFuture.supplyAsync(() -> {
            try {
                return purge ? voiceNoteLibrary.purge()
                        : voiceNoteLibrary.delete(entry) ? 1 : 0;
            } catch (IOException failure) {
                throw new CompletionException(failure);
            }
        }, recoveryExecutor).whenComplete((deleted, failure) ->
                Gdx.app.postRunnable(() -> {
                    if (!voiceNotesOpen) return;
                    if (failure != null) {
                        voiceNotesLoading = false;
                        showToast(gameText.translate(
                                "audio.borrar_nota_error"));
                    } else {
                        if (purge) showToast(gameText.translate(
                                "audio.purgar_notas_resultado", deleted));
                        openVoiceNotes();
                    }
                }));
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
        GdxUiDialogStyle.drawBackdrop(shapes, WIDTH, HEIGHT, 1f);
        GdxUiDialogStyle.drawPanel(shapes, 560f, 386f, 800f, 270f,
                CYAN_DARK, 1f);

        textFit(headingFont, uppercase(gameText.translate(
                "game.inicializando_timba")),
                960f, 560f, GOLD, true, 680f);
        textFit(smallFont, uppercase(gameText.translate(
                "gdx.lobby.preparing_table")),
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

    private void drawNewGameSubmissionOverlay() {
        GdxUiDialogStyle.drawBackdrop(shapes, WIDTH, HEIGHT, 1f);

        float panelX = 610f;
        float panelY = 384f;
        float panelW = 700f;
        float panelH = 270f;
        GdxUiDialogStyle.drawPanel(shapes, panelX, panelY, panelW, panelH,
                CYAN_DARK, 1f);

        String statusKey = connection.mode()
                == NewGameConnectionDraft.Mode.CREATE
                        ? "gdx.newgame.preparing_waiting_room"
                        : "gdx.newgame.connecting_waiting_room";
        textFit(headingFont, uppercase(gameText.translate(statusKey)),
                WIDTH / 2f, panelY + 190f, GOLD, true, panelW - 80f);

        float trackX = panelX + 80f;
        float trackY = panelY + 116f;
        float trackW = panelW - 160f;
        float trackH = 16f;
        shapes.setColor(new Color(0x020813ff));
        roundedRect(trackX, trackY, trackW, trackH, 8f);
        shapes.setColor(LINE);
        roundedRect(trackX + 2f, trackY + 2f, trackW - 4f,
                trackH - 4f, 6f);
        float segmentW = 150f;
        float travel = trackW - segmentW - 8f;
        float phase = (elapsed * 0.58f) % 2f;
        float eased = phase <= 1f ? phase : 2f - phase;
        shapes.setColor(CYAN);
        roundedRect(trackX + 4f + travel * eased, trackY + 4f,
                segmentW, trackH - 8f, 4f);

        themedButton(panelX + 200f, panelY + 28f, 300f, 62f,
                uppercase(gameText.translate("ui.cancelar")),
                ButtonTone.NEUTRAL, this::cancelOrReturnToMenu, true);
    }

    private void kickSelectedParticipant() {
        LobbyParticipant target = selectedRemoteParticipant(lobby);
        if (target == null) {
            showToast(gameText.translate(
                    "ui.tienes_que_seleccionar_algun_participante"));
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
        if (lobbyTextSendReady(elapsed, lobbyTextSendAllowedAt, message)) {
            lobbyTextSendAllowedAt = elapsed + TEXT_SEND_COOLDOWN_SECONDS;
            submitLobbyCommand(new LobbyCommand.SendText(message), () -> {
                lobbyChatDraft = "";
            });
        }
    }

    static boolean lobbyTextSendReady(float now, float allowedAt,
            String message) {
        return now >= allowedAt
                && !Objects.requireNonNullElse(message, "").trim().isEmpty();
    }

    private void sendLobbyImage(String rawUrl) {
        String url = rawUrl == null ? "" : rawUrl.trim();
        if (!GdxChatImageHistory.isHttpUrl(url)) {
            showToast(gameText.translate("gdx.lobby.invalid_image_url"));
            return;
        }
        if (elapsed < lobbyImageSendAllowedAt) {
            showToast(gameText.translate("gdx.lobby.image_cooldown"));
            return;
        }
        submitLobbyCommand(new LobbyCommand.SendImage(url), () -> {
            lobbyImageDraft = "";
            lobbyImageSendAllowedAt = elapsed + IMAGE_SEND_COOLDOWN_SECONDS;
            lobbyImageHistory = GdxChatImageHistory.remember(
                    initialProperties, url, true);
            if (preferences != null) preferences.saveDeferred();
            refreshLobbyHistoryMedia();
            lobbyImageMode = false;
            activateField("lobbyChat");
        });
    }

    private void drawLobbyImageGallery(float x, float y, float w, float h) {
        GdxUiDialogStyle.drawPanel(shapes, x, y, w, h, CYAN_DARK, 1f);
        textFit(smallFont, uppercase(gameText.translate(
                "gdx.lobby.image_gallery")), x + 24f,
                y + h - 28f, GOLD, false, w - 410f);
        textFit(tinyFont, gameText.translate("gdx.lobby.image_gallery_help"),
                x + 24f, y + h - 58f, MUTED, false, w - 410f);
        boolean autoReceive = GdxChatImageHistory.autoReceive(
                initialProperties);
        themedButton(x + w - 436f, y + h - 68f, 220f, 48f,
                uppercase(gameText.translate(autoReceive
                        ? "gdx.lobby.received_images_on"
                        : "gdx.lobby.received_images_off")),
                autoReceive ? ButtonTone.POSITIVE : ButtonTone.NEUTRAL,
                () -> {
                    GdxChatImageHistory.setAutoReceive(initialProperties,
                            !autoReceive);
                    if (preferences != null) preferences.saveDeferred();
                }, true);
        themedButton(x + w - 206f, y + h - 68f, 128f, 48f,
                uppercase(gameText.translate("gdx.lobby.clear")),
                ButtonTone.NEUTRAL,
                () -> lobbyImageClearConfirmation = true,
                !lobbyImageHistory.isEmpty());
        Rectangle close = lobbyImageGalleryCloseBounds(x, y, w, h);
        themedButton(close.x, close.y, close.width, close.height, "\u00d7",
                ButtonTone.DANGER, this::closeLobbyImageGallery, true);

        // Present the complete dialog chrome for one frame before adding the
        // potentially cold thumbnail grid. This avoids the first-open flash
        // of detached placeholder cells while media textures warm up.
        if (lobbyImageGalleryContentDelayFrames > 0) {
            lobbyImageGalleryContentDelayFrames--;
            return;
        }

        if (lobbyImageHistory.isEmpty()) {
            textFit(headingFont, uppercase(gameText.translate(
                    "gdx.lobby.image_gallery_empty")), x + w / 2f,
                    y + 225f, MUTED, true, w - 80f);
            textFit(smallFont,
                    gameText.translate("gdx.lobby.image_gallery_empty_help"),
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
            GdxChatGalleryMedia.Entry media = lobbyHistoryMedia.get(url);
            Texture thumbnail = media == null ? null : media.frameAt(elapsed);
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
                String state = media != null && media.failed()
                        ? uppercase(gameText.translate(
                                "gdx.lobby.media_unavailable"))
                        : uppercase(gameText.translate(
                                "gdx.lobby.media_loading"));
                textFit(tinyFont, state, cellX + cellW / 2f,
                        cellY + cellH / 2f + 7f,
                        media != null && media.failed() ? ORANGE : MUTED,
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
        lobbyImageClearConfirmation = false;
    }

    private void closeLobbyImageGallery() {
        lobbyImageMode = false;
        lobbyImageClearConfirmation = false;
        lobbyImageGalleryContentDelayFrames = 0;
        activateField("lobbyChat");
    }

    private void drawLobbyImageClearConfirmation() {
        GdxUiDialogStyle.drawBackdrop(shapes, WIDTH, HEIGHT, 1f);
        GdxUiDialogStyle.drawPanel(shapes, 560f, 350f, 800f, 330f,
                CYAN_DARK, 1f);
        textFit(headingFont, uppercase(gameText.translate("ui.seguro")),
                960f, 626f, GOLD, true, 700f);
        centeredWrappedText(smallFont, gameText.translate(
                "gdx.lobby.clear_images_confirm"), 960f, 550f,
                680f, 27f, 3, Color.WHITE);
        themedButton(635f, 405f, 300f, 75f,
                uppercase(gameText.translate("ui.cancelar")),
                ButtonTone.NEUTRAL,
                () -> lobbyImageClearConfirmation = false, true);
        themedButton(985f, 405f, 300f, 75f,
                uppercase(gameText.translate("gdx.lobby.clear")),
                ButtonTone.DANGER, this::clearLobbyImageHistory, true);
    }

    private void openGoogleImages() {
        openExternalUri(GOOGLE_IMAGES_URI,
                "gdx.lobby.search_images_failed");
    }

    static Rectangle lobbyImageGalleryCloseBounds(float x, float y,
            float width, float height) {
        return new Rectangle(x + width - 68f, y + height - 68f, 44f, 48f);
    }

    private void drawLobbyEmojiPicker(float x, float y, float w, float h) {
        GdxUiDialogStyle.drawPanel(shapes, x, y, w, h, CYAN_DARK, 1f);
        int first = lobbyEmojiPage * EMOJI_PAGE_SIZE + 1;
        int last = Math.min(EMOJI_COUNT, first + EMOJI_PAGE_SIZE - 1);
        int pageCount = (EMOJI_COUNT + EMOJI_PAGE_SIZE - 1) / EMOJI_PAGE_SIZE;
        textFit(smallFont, "EMOJIS " + first + " - " + last,
                x + 28f, y + h - 30f, GOLD, false, 350f);
        textFit(tinyFont, uppercase(gameText.translate("gdx.lobby.page",
                lobbyEmojiPage + 1, pageCount)),
                x + w - 330f, y + h - 30f, MUTED, false, 300f);
        float cellW = 82f;
        float cellH = 62f;
        float gridWidth = (EMOJI_COLUMNS - 1) * cellW
                + LOBBY_EMOJI_PICKER_CELL_SIZE;
        float startX = x + (w - gridWidth) / 2f;
        float startY = y + h - 112f;
        for (int slot = 0; slot < EMOJI_PAGE_SIZE; slot++) {
            int number = first + slot;
            if (number > EMOJI_COUNT) break;
            int column = slot % EMOJI_COLUMNS;
            int row = slot / EMOJI_COLUMNS;
            float cellX = startX + column * cellW;
            float cellY = startY - row * cellH;
            outerBox(cellX, cellY, LOBBY_EMOJI_PICKER_CELL_SIZE,
                    LOBBY_EMOJI_PICKER_CELL_SIZE,
                    hovered(cellX, cellY, LOBBY_EMOJI_PICKER_CELL_SIZE,
                            LOBBY_EMOJI_PICKER_CELL_SIZE) ? CYAN : LINE,
                    new Color(0x0d1b2dcc));
            float imageInset = (LOBBY_EMOJI_PICKER_CELL_SIZE
                    - LOBBY_EMOJI_PICKER_IMAGE_SIZE) / 2f;
            uiImages.add(new UiImageItem(lobbyEmojiTexture(number),
                    cellX + imageInset, cellY + imageInset,
                    LOBBY_EMOJI_PICKER_IMAGE_SIZE,
                    LOBBY_EMOJI_PICKER_IMAGE_SIZE));
            hit(cellX, cellY, LOBBY_EMOJI_PICKER_CELL_SIZE,
                    LOBBY_EMOJI_PICKER_CELL_SIZE, () -> {
                activateField("lobbyChat");
                replaceActiveSelection(" #" + number + "# ");
            });
        }
        compactButton(x + 30f, y + 18f, 155f, 52f,
                uppercase(gameText.translate("gdx.lobby.previous")), false,
                () -> lobbyEmojiPage = Math.max(0, lobbyEmojiPage - 1),
                lobbyEmojiPage > 0);
        compactButton(x + w - 185f, y + 18f, 155f, 52f,
                uppercase(gameText.translate("gdx.lobby.next")), false,
                () -> lobbyEmojiPage = Math.min(pageCount - 1,
                        lobbyEmojiPage + 1), lobbyEmojiPage + 1 < pageCount);
        compactButton(x + w / 2f - 80f, y + 18f, 160f, 52f,
                uppercase(gameText.translate("ui.cerrar")), false,
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
        setLobbyVoiceStatus("gdx.lobby.voice_opening");
        CompletableFuture.supplyAsync(() -> recorder.start(
                () -> Gdx.app.postRunnable(() -> {
                    if (disposed || lobbyVoiceRecorder != recorder) return;
                    lobbyVoiceOpening = false;
                    lobbyVoiceLive = true;
                    lobbyVoiceLiveAt = elapsed;
                    setLobbyVoiceStatus("gdx.lobby.voice_recording");
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
                                setLobbyVoiceStatus(
                                        "gdx.lobby.voice_unavailable");
                            }
                        }));
    }

    private void finishLobbyVoiceRecording(boolean discard) {
        GdxVoiceRecorder recorder = lobbyVoiceRecorder;
        if (recorder == null || lobbyVoiceStopping) return;
        lobbyVoiceStopping = true;
        lobbyVoiceOpening = false;
        lobbyVoiceLive = false;
        setLobbyVoiceStatus(discard
                ? "gdx.lobby.voice_cancelled"
                : "gdx.lobby.voice_processing");
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
                                setLobbyVoiceStatus(
                                        "gdx.lobby.voice_discarded");
                                return;
                            }
                            processOwnLobbyVoice(wav);
                            setLobbyVoiceStatus("gdx.lobby.voice_sending");
                            submitLobbyCommand(new LobbyCommand.SendVoice(wav), () -> {
                                setLobbyVoiceStatus("gdx.lobby.voice_sent",
                                        VOICE_SENT_STATUS_SECONDS);
                            });
                        }));
    }

    private void setLobbyVoiceStatus(String translationKey) {
        setLobbyVoiceStatus(translationKey, VOICE_STATUS_SECONDS);
    }

    private void setLobbyVoiceStatus(String translationKey, float seconds) {
        lobbyVoiceStatus = uppercase(gameText.translate(translationKey));
        lobbyVoiceStatusAt = elapsed;
        lobbyVoiceStatusSeconds = seconds;
    }

    private void processOwnLobbyVoice(byte[] wav) {
        LobbySnapshot current = lobby;
        if (current == null
                || !com.tonikelope.coronapoker.core.audio.VoiceWavContract
                        .isValid(wav)) return;
        persistLobbyVoiceNote(current.localNickname(), wav);
        if (preferenceBoolean("audio_play_own_voice", true)
                && preferenceBoolean("voice_messages", true)
                && !preferenceBoolean("audio_block_voice_messages", false)) {
            playLobbyVoice(wav);
        }
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
                && elapsed - lobbyVoiceStatusAt > lobbyVoiceStatusSeconds) {
            lobbyVoiceStatus = "";
        }
    }

    private void drawLobbyVoiceStatus(float x, float y, float w) {
        boolean active = lobbyVoiceOpening || lobbyVoiceLive
                || lobbyVoiceStopping;
        float panelW = Math.min(720f, w);
        float panelH = 92f;
        float panelX = x + (w - panelW) / 2f;
        shapes.setColor(0.008f, 0.035f, 0.055f, 0.95f);
        roundedRect(panelX, y, panelW, panelH, 15f);
        shapes.setColor(active ? new Color(0xe53935ff) : LINE);
        roundedRect(panelX + 16f, y + 15f, 7f, panelH - 30f, 3f);
        if (lobbyVoiceLive) {
            shapes.setColor(0.08f, 0.16f, 0.24f, 0.96f);
            roundedRect(panelX + panelW - 110f, y + 24f,
                    88f, 44f, 10f);
            float remaining = Math.max(0f, VOICE_RECORD_MAX_SECONDS
                    - (elapsed - lobbyVoiceLiveAt));
            shapes.setColor(0.17f, 0.92f, 0.62f, 0.92f);
            roundedRect(panelX + 84f, y + 12f,
                    (panelW - 216f) * remaining
                            / VOICE_RECORD_MAX_SECONDS,
                    7f, 3f);
            textFit(smallFont, (int) Math.ceil(remaining) + " s",
                    panelX + panelW - 66f, y + 54f,
                    Color.WHITE, true, 80f);
        }
        textFit(smallFont, lobbyVoiceStatus,
                panelX + 38f, y + 56f,
                active ? Color.WHITE : GOLD, false,
                panelW - (lobbyVoiceLive ? 174f : 76f));
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
            } else if (message.type() == LobbyChatMessage.Type.VOICE
                    && message.nickname().equals(next.localNickname())) {
                // Swing processes an own note synchronously before sending.
                // Its ordered echo exists only to place it in chat history.
                continue;
            } else if (message.type() == LobbyChatMessage.Type.VOICE) {
                persistLobbyVoiceNote(message.nickname(), message.content());
            }
        }
    }

    private void persistLobbyVoiceNote(String nickname, String base64Wav) {
        try {
            persistLobbyVoiceNote(nickname,
                    Base64.getDecoder().decode(base64Wav));
        } catch (IllegalArgumentException malformed) {
            LOGGER.log(Level.WARNING, "Dropped malformed lobby voice note",
                    malformed);
        }
    }

    private void persistLobbyVoiceNote(String nickname, byte[] wav) {
        if (!com.tonikelope.coronapoker.core.audio.VoiceWavContract
                .isValid(wav)) return;
        CompletableFuture.supplyAsync(() -> {
            try {
                return voiceNoteLibrary.store(nickname, wav);
            } catch (java.io.IOException failure) {
                throw new CompletionException(failure);
            }
        }).whenComplete((entry, failure) -> {
            if (failure != null) {
                LOGGER.log(Level.WARNING,
                        "Could not persist lobby voice note", failure);
            } else if (Gdx.app != null) {
                Gdx.app.postRunnable(() -> {
                    if (!disposed && voiceNotesOpen) openVoiceNotes();
                });
            }
        });
    }

    private void purgeExpiredVoiceNotes() {
        int retentionDays = GdxSettingsContract.voiceRetentionDays(
                initialProperties);
        CompletableFuture.runAsync(() -> {
            try {
                voiceNoteLibrary.purgeExpired(retentionDays,
                        System.currentTimeMillis());
            } catch (java.io.IOException failure) {
                LOGGER.log(Level.WARNING,
                        "Could not purge expired voice notes", failure);
            }
        });
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
        if (!audioOutputAvailable() || !audioControl.enabled()
                || preferenceBoolean("audio_block_voice_messages", false)) {
            showToast(gameText.translate(audioOutputAvailable()
                    ? "gdx.lobby.voice_playback_disabled"
                    : "gdx.audio.no_output_device"));
            return;
        }
        try {
            byte[] wav = Base64.getDecoder().decode(message.content());
            if (!com.tonikelope.coronapoker.core.audio.VoiceWavContract
                    .isValid(wav)) {
                showToast(gameText.translate("gdx.lobby.voice_invalid"));
                return;
            }
            playLobbyVoice(wav);
        } catch (IllegalArgumentException malformed) {
            showToast(gameText.translate("gdx.lobby.voice_playback_failed"));
        }
    }

    private void toggleLobbyChatVoice(LobbyChatMessage message) {
        if (lobbyChatVoiceSequence == message.sequence()) {
            lobbyChatVoicePaused = !lobbyChatVoicePaused;
            if (lobbyChatVoicePaused) GdxVoicePlayback.pause();
            else GdxVoicePlayback.resume();
            return;
        }
        stopLobbyChatVoice();
        lobbyChatVoiceSequence = message.sequence();
        lobbyChatVoicePaused = false;
        playLobbyVoice(message, message.sequence());
    }

    private void playLobbyVoice(LobbyChatMessage message, long sequence) {
        if (!audioOutputAvailable() || !audioControl.enabled()
                || preferenceBoolean("audio_block_voice_messages", false)) {
            showToast(gameText.translate(audioOutputAvailable()
                    ? "gdx.lobby.voice_playback_disabled"
                    : "gdx.audio.no_output_device"));
            lobbyChatVoiceSequence = -1L;
            return;
        }
        try {
            byte[] wav = Base64.getDecoder().decode(message.content());
            if (!com.tonikelope.coronapoker.core.audio.VoiceWavContract
                    .isValid(wav)) {
                showToast(gameText.translate("gdx.lobby.voice_invalid"));
                lobbyChatVoiceSequence = -1L;
                return;
            }
            GdxVoicePlayback.play(wav, masterVolume(), () -> {
                if (lobbyChatVoiceSequence == sequence
                        && lobbyChatVoicePaused) {
                    GdxVoicePlayback.pause();
                }
            }).whenComplete((ignored, failure) -> {
                if (Gdx.app == null) return;
                Gdx.app.postRunnable(() -> {
                    if (lobbyChatVoiceSequence == sequence) {
                        lobbyChatVoiceSequence = -1L;
                        lobbyChatVoicePaused = false;
                    }
                    if (!disposed && failure != null) {
                        showToast(gameText.translate(
                                "gdx.lobby.voice_playback_failed"));
                    }
                });
            });
        } catch (IllegalArgumentException malformed) {
            lobbyChatVoiceSequence = -1L;
            showToast(gameText.translate("gdx.lobby.voice_playback_failed"));
        }
    }

    private void stopLobbyChatVoice() {
        lobbyChatVoiceSequence = -1L;
        lobbyChatVoicePaused = false;
        GdxVoicePlayback.stop();
    }

    private void playLobbyVoice(byte[] wav) {
        if (!audioOutputAvailable() || !audioControl.enabled()
                || preferenceBoolean("audio_block_voice_messages", false)) {
            return;
        }
        GdxVoicePlayback.play(wav, masterVolume(), null)
                .whenComplete((ignored, failure) -> {
            if (failure == null || Gdx.app == null) return;
            Gdx.app.postRunnable(() -> {
                if (!disposed) {
                    showToast(gameText.translate(
                            "gdx.lobby.voice_playback_failed"));
                }
            });
        });
    }

    private void refreshLobbyHistoryMedia() {
        lobbyHistoryMedia.refresh(lobbyImageHistory, 8, "lobby-history");
    }

    private void clearLobbyMedia() {
        for (LobbyMedia media : lobbyMedia.values()) {
            media.dispose();
        }
        lobbyMedia.clear();
        clearLobbyHistoryMedia();
    }

    private void clearLobbyHistoryMedia() {
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
        stopLobbyTransientAudio();
        clearLobbyMedia();
        closeLobbySubscription();
        if (lobbySession != null) {
            lobbySession.close();
        }
        lobbySession = null;
        lobby = null;
        selectedParticipant = null;
        lobbyPasswordDialog = false;
        lobbyPasswordDraft = "";
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
        settingsAppearancePage = settingsSession.subpageIndex(
                GdxSettingsContract.Section.APPEARANCE);
        settingsAudioPage = settingsSession.subpageIndex(
                GdxSettingsContract.Section.AUDIO);
        settingsGamePage = settingsSession.subpageIndex(
                GdxSettingsContract.Section.GAME);
        settingsAppearanceScroll = 0f;
        settingsAudioScroll = 0f;
        settingsShortcutScroll = 0f;
        settingsDebugScroll = 0;
        settingsDebugLineCount = 0;
        settingsShortcutCaptureId = null;
        settingsShortcutStatus = "";
        shortcutBindings.beginEdit();
        settingsOpenedWindowMode = GdxDisplayModeController.activeMode();
        settingsOpenedMsaaSamples = presentationSettings
                .requestedMsaaSamples();
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
        if (save && settingsReturnSurface == Surface.LOBBY && lobby != null) {
            NewGameTableDraft.Settings requested = settingsTable == null
                    ? null : settingsTable.snapshot();
            List<LobbyCommand> commands = lobbySettingsCommands(lobby,
                    requested, GdxSettingsContract.chatNotificationsEnabled(
                            initialProperties, true));
            if (!commands.isEmpty()) {
                submitLobbySettingsCommands(commands, 0);
                return;
            }
        }
        finishClosingSettings(save);
    }

    private void submitLobbySettingsCommands(List<LobbyCommand> commands,
            int index) {
        if (index >= commands.size()) {
            finishClosingSettings(true);
            return;
        }
        submitLobbyCommand(commands.get(index),
                () -> submitLobbySettingsCommands(commands, index + 1));
    }

    static List<LobbyCommand> lobbySettingsCommands(LobbySnapshot state,
            NewGameTableDraft.Settings requested,
            boolean chatNotifications) {
        if (state == null) return List.of();
        List<LobbyCommand> commands = new ArrayList<>(2);
        if (state.host() && requested != null
                && !requested.equals(state.tableSettings())) {
            commands.add(new LobbyCommand.UpdateTableSettings(requested));
        }
        if (chatNotifications != state.chatNotifications()) {
            commands.add(new LobbyCommand.SetChatNotifications(
                    chatNotifications));
        }
        return List.copyOf(commands);
    }

    private void finishClosingSettings(boolean save) {
        audioPreview.stop();
        boolean restartNotice = save
                && GdxSettingsContract.requiresMsaaRestart(
                        settingsOpenedMsaaSamples,
                        presentationSettings.requestedMsaaSamples(),
                        presentationSettings.actualMsaaSamples());
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
        settingsRestartNotice = restartNotice;
    }

    private void drawSettingsRestartNotice() {
        hits.clear();
        secondaryHits.clear();
        textFieldHits.clear();
        passwordRevealHits.clear();
        editMenuHits.clear();
        GdxUiDialogStyle.drawBackdrop(shapes, WIDTH, HEIGHT, 1f);
        GdxUiDialogStyle.drawPanel(shapes, 505f, 345f, 910f, 350f,
                CYAN_DARK, 1f);
        textFit(headingFont, uppercase(gameText.translate(
                "gdx.settings.msaa_restart.title")), 960f, 585f,
                GOLD, true, 800f);
        textFit(actionFont, gameText.translate(
                "gdx.settings.msaa_restart.message"), 960f, 515f,
                Color.WHITE, true, 790f);
        themedButton(785f, 405f, 350f, 75f, uppercase(gameText.translate(
                "ui.aceptar")), ButtonTone.FEATURED,
                () -> settingsRestartNotice = false, true);
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
        settingsRowScrollMaximum = 0;
        settingsRowScrollTrack.set(0f, 0f, 0f, 0f);
        switch (settingsSession.section()) {
            case APPEARANCE -> restoreFrontendAppearanceDefaults();
            case AUDIO -> restoreFrontendAudioDefaults();
            case SHORTCUTS -> {
                shortcutBindings.resetAllEdits();
                settingsShortcutCaptureId = null;
                settingsShortcutStatus = "restored";
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
                1, gameText);
    }

    private int settingsSubpageIndex() {
        return switch (settingsSession.section()) {
            case APPEARANCE -> settingsAppearancePage;
            case AUDIO -> settingsAudioPage;
            case GAME -> settingsGamePage;
            case SHORTCUTS -> 0;
            case DEBUG -> 0;
        };
    }

    private void selectSettingsSubpage(int index) {
        audioPreview.stop();
        settingsSession.selectSubpage(index);
        switch (settingsSession.section()) {
            case APPEARANCE -> {
                settingsAppearancePage = index;
                settingsAppearanceScroll = 0f;
            }
            case AUDIO -> {
                settingsAudioPage = index;
                settingsAudioScroll = 0f;
            }
            case GAME -> settingsGamePage = index;
            case SHORTCUTS -> {
                settingsShortcutScroll = 0f;
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
        int activeSubpage = subpages.isEmpty() ? -1
                : MathUtils.clamp(settingsSubpageIndex(), 0,
                        subpages.size() - 1);
        float contentX = content.x;
        float contentY = content.y;
        float contentW = content.width;
        float contentH = content.height;
        boolean restoreVisible = settingsSectionHasRestoreDefaults();
        GdxSettingsChrome.draw(shapes, frame, sections.size(),
                subpages.size(), settingsSession.tabIndex(), activeSubpage,
                pointer, restoreVisible, 1f);

        // Keep title and subtitle in independent vertical bands. Their fonts
        // have different ascenders, so baseline-only spacing can overlap even
        // when the numeric coordinates appear separated.
        textFit(titleFont, uppercase(gameText.translate("settings.ajustes")),
                panelX + 34f,
                panelY + panelH - GdxSettingsLayout.TITLE_BASELINE_TOP_INSET,
                GOLD, false, panelW - 68f);
        for (int i = 0; i < sections.size(); i++) {
            final int selected = i;
            Rectangle tab = frame.mainTab(i);
            boolean active = settingsSession.tabIndex() == i;
            textFit(actionFont, sections.get(i).label(gameText),
                    tab.x + tab.width / 2f, tab.y + 32f,
                    active ? Color.WHITE : MUTED,
                    true, tab.width - 24f);
            hit(tab.x, tab.y, tab.width, tab.height, () -> {
                audioPreview.stop();
                settingsSession.selectTab(selected);
                settingsAppearanceScroll = 0f;
                settingsAudioScroll = 0f;
                settingsShortcutScroll = 0f;
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
                    active ? GOLD : MUTED,
                    true, tab.width - 17f);
            hit(tab.x, tab.y, tab.width, tab.height,
                    () -> selectSettingsSubpage(selected));
        }

        settingsRowsClip.set(contentX, contentY + 14f, contentW,
                Math.max(0f, contentH - 38f));
        settingsRowScrollMaximum = 0f;
        settingsRowScrollTrack.set(0f, 0f, 0f, 0f);
        shapes.flush();
        enableWorldScissor(settingsRowsClip);
        settingsRowsActive = true;
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
        settingsRowsActive = false;
        shapes.flush();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);

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
        float rowY = y + h - GdxSettingsLayout.CONTENT_ROW_TOP_INSET;
        if (settingsAudioPage == 0) {
            drawFrontendVolumeControl(x + 34f, rowY, w - 68f);
            rowY -= 74f;
        }
        float baseX = x + GdxSettingsLayout.CONTENT_HORIZONTAL_INSET;
        float baseWidth = w - 2f
                * GdxSettingsLayout.CONTENT_HORIZONTAL_INSET;
        GdxSettingsLayout.PixelRows rows = GdxSettingsLayout.pixelRows(
                rowY, y + 14f, rowY + GdxSettingsLayout.ROW_HEIGHT,
                GdxSettingsContract.audioRowCount(page),
                settingsAudioScroll);
        settingsAudioScroll = rows.offset();
        for (int optionIndex = 0; optionIndex < page.options().size();
                optionIndex++) {
            int rowIndex = GdxSettingsContract.audioOptionRow(page,
                    optionIndex);
            if (rowIndex < rows.firstIndex()
                    || rowIndex >= rows.lastExclusive()) continue;
            GdxSettingsContract.ToggleOption option = page.options().get(
                    optionIndex);
            boolean enabled = frontendAudioOptionEnabled(option);
            boolean value = GdxSettingsContract.displayedValue(option,
                    initialProperties, audioControl.enabled());
            Runnable action = "sonidos".equals(option.key())
                    ? this::toggleMasterSoundState
                    : () -> togglePreference(option.key(), option.fallback());
            Rectangle row = GdxSettingsLayout.optionRow(baseX,
                    rows.rowY(rowIndex),
                    baseWidth,
                    GdxSettingsContract.isChildOption(page, option));
            toggle(row.x, row.y, row.width,
                    GdxSettingsContract.markDefault(option.label(gameText),
                            value == option.fallback()), value,
                    action, enabled);
            GdxSettingsContract.AudioPreview preview =
                    GdxSettingsContract.audioPreview(option,
                            gameText.language());
            if (preview != null) {
                drawAudioPreviewControl(row, option.key(), preview);
            }
        }
        drawSettingsRowScrollbar(x + w - 24f, rows);
        if (GdxSettingsContract.hasVoiceRetention(page)) {
            int retentionRow = GdxSettingsContract.voiceRetentionRow(page);
            rowY = rows.rowY(retentionRow);
            settingsStepper(x + 34f, rowY, w - 68f,
                    GdxSettingsLayout.ROW_HEIGHT,
                    uppercase(gameText.translate(
                            "gdx.settings.row.keep_voice_notes")),
                    GdxSettingsContract.markDefault(
                            GdxSettingsContract.voiceRetentionLabel(
                                    initialProperties, gameText),
                            GdxSettingsContract.voiceRetentionDays(
                                    initialProperties) == 90),
                    () -> {
                        GdxSettingsContract.adjustVoiceRetention(
                                initialProperties, -1);
                    }, () -> {
                        GdxSettingsContract.adjustVoiceRetention(
                                initialProperties, 1);
                    });
            rowY = rows.rowY(GdxSettingsContract.voiceActionsRow(page));
            float half = (w - 82f) / 2f;
            themedButton(x + 34f, rowY, half, 62f,
                    uppercase(gameText.translate("audio.ver_notas")),
                    ButtonTone.NEUTRAL, this::openVoiceNotes, true);
            themedButton(x + 48f + half, rowY, half, 62f,
                    uppercase(gameText.translate("audio.purgar_notas")),
                    ButtonTone.DANGER, () -> {
                        voiceNotesPurgeConfirmation = true;
                        openVoiceNotes();
                    }, true);
        }
        if (GdxSettingsContract.hasAudioDevices(page)) {
            float outputY = rows.rowY(0);
            float microphoneY = rows.rowY(1);
            settingsStepper(x + 34f, outputY, w - 68f, 70f,
                    uppercase(gameText.translate(
                            "gdx.settings.row.game_output")),
                    GdxSettingsContract.markDefault(
                            GdxAudioDevices.outputLabel(initialProperties,
                                    gameText),
                            initialProperties.getProperty(
                                    GdxAudioDevices.OUTPUT_KEY, "").isBlank()), () -> {
                        GdxAudioDevices.adjustOutput(initialProperties, -1);
                    }, () -> {
                        GdxAudioDevices.adjustOutput(initialProperties, 1);
                    });
            settingsStepper(x + 34f, microphoneY, w - 68f, 70f,
                    uppercase(gameText.translate(
                            "gdx.settings.row.microphone")),
                    GdxSettingsContract.markDefault(
                            GdxAudioDevices.captureLabel(initialProperties,
                                    gameText),
                            initialProperties.getProperty(
                                    GdxAudioDevices.CAPTURE_KEY, "").isBlank()), () -> {
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

    private void drawAudioPreviewControl(Rectangle row, String key,
            GdxSettingsContract.AudioPreview preview) {
        float x = row.x + row.width - 190f;
        float y = row.y + 11f;
        float size = 46f;
        boolean active = audioPreview.active(key);
        shapes.setColor(hovered(x, y, size, size)
                ? new Color(0x18465fff) : new Color(0x10283cff));
        roundedRect(x, y, size, size, 8f);
        shapes.setColor(active ? GOLD : CYAN);
        if (active) {
            shapes.rect(x + 15f, y + 15f, 16f, 16f);
        } else {
            shapes.triangle(x + 16f, y + 12f,
                    x + 16f, y + 34f, x + 34f, y + 23f);
        }
        hit(x, y, size, size,
                () -> audioPreview.toggle(key, preview, masterVolume()));
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
        textFit(smallFont, GdxSettingsContract.markDefault(
                Math.round(masterVolume() * 100f) + "%",
                Float.compare(masterVolume(), 0.8f) == 0),
                percentage.x + percentage.width / 2f,
                percentage.y + 31f, GOLD, true, percentage.width - 8f);
        shapes.setColor(new Color(0x253248ff));
        roundedRect(slider.x, slider.y, slider.width, slider.height, 6f);
        shapes.setColor(CYAN);
        roundedRect(slider.x, slider.y, slider.width * masterVolume(),
                slider.height, 6f);
        themedButton(minus.x, minus.y, minus.width, minus.height, "-",
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
        float rowY = y + h - GdxSettingsLayout.CONTENT_ROW_TOP_INSET;
        if (settingsAppearancePage == 0) {
            float rowStride = GdxSettingsLayout.rowStride(h, 7);
            settingsStepper(x + 34f, rowY, w - 68f, 70f,
                    uppercase(gameText.translate("gdx.settings.row.deck")),
                    GdxSettingsContract.markDefault(
                            GdxAppearanceOptions.deckLabel(configuredDeck(),
                                    gameText),
                            "goliat".equalsIgnoreCase(configuredDeck())),
                    this::selectPreviousDeck, this::selectNextDeck);
            settingsStepper(x + 34f, rowY - rowStride, w - 68f,
                    GdxSettingsLayout.ROW_HEIGHT,
                    uppercase(gameText.translate(
                            "gdx.settings.row.card_back")),
                    GdxSettingsContract.markDefault(
                            GdxAppearanceOptions.cardBackLabel(configuredBack(),
                                    gameText),
                            "default".equalsIgnoreCase(configuredBack())),
                    this::selectPreviousBack, this::selectNextBack);
            settingsStepper(x + 34f, rowY - 2f * rowStride, w - 68f,
                    GdxSettingsLayout.ROW_HEIGHT,
                    uppercase(gameText.translate("gdx.settings.row.felt")),
                    GdxSettingsContract.markDefault(
                            GdxAppearanceOptions.feltLabel(configuredFelt(),
                                    gameText),
                            "verde".equalsIgnoreCase(configuredFelt())),
                    this::selectPreviousFelt, this::selectNextFelt);
            settingsStepper(x + 34f, rowY - 3f * rowStride, w - 68f,
                    GdxSettingsLayout.ROW_HEIGHT,
                    uppercase(gameText.translate(
                            "gdx.settings.row.light_off")),
                    GdxSettingsContract.markDefault(
                            GdxAppearanceOptions.lightLevelLabel(
                                    initialProperties),
                            "50".equals(initialProperties.getProperty(
                                    "nivel_luz", "50"))),
                    () -> adjustLightLevel(-1),
                    () -> adjustLightLevel(1));
            settingsStepper(x + 34f, rowY - 4f * rowStride, w - 68f,
                    GdxSettingsLayout.ROW_HEIGHT,
                    uppercase(gameText.translate(
                            "gdx.settings.row.window_mode")),
                    GdxSettingsContract.markDefault(windowModeSettingLabel(),
                            GdxWindowMode.configured(initialProperties)
                                    == GdxWindowMode.BORDERLESS),
                    this::selectPreviousWindowMode,
                    this::selectNextWindowMode);
            settingsStepper(x + 34f, rowY - 5f * rowStride, w - 68f,
                    GdxSettingsLayout.ROW_HEIGHT,
                    uppercase(gameText.translate(
                            "gdx.settings.row.antialiasing")),
                    GdxSettingsContract.markDefault(msaaSettingLabel(),
                            "4".equals(initialProperties.getProperty(
                                    "gdx_msaa_samples", "4"))),
                    this::selectPreviousMsaa, this::selectNextMsaa);
            performanceTooltip(new Rectangle(x + 34f,
                    rowY - 5f * rowStride, w - 68f, 70f),
                    "gdx_msaa_samples");
            settingsInfoRow(x + 34f, rowY - 6f * rowStride, w - 68f,
                    GdxSettingsLayout.ROW_HEIGHT,
                    uppercase(gameText.translate(
                            "gdx.settings.row.gpu_renderer")),
                    GdxGraphicsInfo.displayValue(gameText));
            return;
        }
        GdxSettingsContract.TogglePage page =
                GdxSettingsContract.APPEARANCE_PAGES.get(
                        settingsAppearancePage - 1);
        int rowCount = GdxSettingsContract.appearanceRowCount(page);
        GdxSettingsLayout.PixelRows rows = GdxSettingsLayout.pixelRows(
                rowY, y + 14f, rowY + GdxSettingsLayout.ROW_HEIGHT,
                rowCount, settingsAppearanceScroll);
        settingsAppearanceScroll = rows.offset();
        float baseX = x + GdxSettingsLayout.CONTENT_HORIZONTAL_INSET;
        float baseWidth = w - 2f
                * GdxSettingsLayout.CONTENT_HORIZONTAL_INSET;
        for (int row = rows.firstIndex(); row < rows.lastExclusive(); row++) {
            float currentY = rows.rowY(row);
            if (row < page.options().size()) {
                GdxSettingsContract.ToggleOption option =
                        page.options().get(row);
                boolean enabled = GdxSettingsContract.enabled(option,
                        initialProperties, audioControl.enabled());
                boolean value = preferenceBoolean(option.key(),
                        option.fallback());
                Rectangle rowBounds = GdxSettingsLayout.optionRow(baseX,
                        currentY, baseWidth,
                        GdxSettingsContract.isChildOption(page, option));
                toggle(rowBounds.x, rowBounds.y, rowBounds.width,
                        GdxSettingsContract.markDefault(option.label(gameText),
                                value == option.fallback()), value,
                        () -> togglePreference(option.key(),
                                option.fallback()), enabled);
                performanceTooltip(rowBounds, option.key());
            } else {
                GdxAppearanceOptions.Choice option =
                        GdxAppearanceOptions.ANIMATION_CHOICES.get(
                                row - page.options().size());
                boolean enabled = GdxAppearanceOptions.enabled(option,
                        initialProperties);
                Rectangle rowBounds = GdxSettingsLayout.optionRow(baseX,
                        currentY, baseWidth,
                        GdxAppearanceOptions.isChildChoice(option));
                settingsStepper(rowBounds.x, rowBounds.y, rowBounds.width,
                        GdxSettingsLayout.ROW_HEIGHT,
                        option.label(gameText),
                        GdxSettingsContract.markDefault(
                                GdxAppearanceOptions.selectedLabel(option,
                                        initialProperties, gameText),
                                option.fallback().equals(option.values().get(
                                        GdxAppearanceOptions.selectedIndex(
                                                option,
                                                initialProperties)))),
                        () -> {
                            GdxAppearanceOptions.adjust(option,
                                    initialProperties, -1);
                        }, () -> {
                            GdxAppearanceOptions.adjust(option,
                                    initialProperties, 1);
                        }, enabled);
                performanceTooltip(rowBounds, option.key());
            }
        }
        drawSettingsRowScrollbar(x + w - 24f, rows);
    }

    private void drawSettingsRowScrollbar(float x,
            GdxSettingsLayout.PixelRows rows) {
        settingsRowScrollMaximum = rows.maximum();
        if (!rows.scrollable()) {
            settingsRowScrollTrack.set(0f, 0f, 0f, 0f);
            return;
        }
        float y = rows.viewportBottom();
        float height = rows.viewportHeight();
        float trackWidth = GdxSettingsLayout.SCROLLBAR_WIDTH;
        float thumbHeight = GdxSettingsLayout.pixelScrollbarThumbHeight(
                height, rows.viewportHeight(), rows.contentHeight());
        settingsRowScrollTrack.set(x - (GdxSettingsLayout.SCROLLBAR_HIT_WIDTH
                - trackWidth) / 2f, y,
                GdxSettingsLayout.SCROLLBAR_HIT_WIDTH, height);
        settingsRowScrollThumbHeight = thumbHeight;
        float thumbY = y + (height - thumbHeight)
                * (1f - rows.offset() / rows.maximum());
        shapes.setColor(0.12f, 0.20f, 0.31f, 0.92f);
        roundedRect(x, y, trackWidth, height, trackWidth / 2f);
        shapes.setColor(CYAN.r, CYAN.g, CYAN.b, 0.96f);
        roundedRect(x, thumbY, trackWidth, thumbHeight, trackWidth / 2f);
    }

    /** Compact settings row: label and value share one bounded surface. */
    private void settingsChoice(float x, float y, float w, float h,
            String label, String value, Runnable action) {
        settingsChoice(x, y, w, h, label, value, action, true);
    }

    private void settingsChoice(float x, float y, float w, float h,
            String label, String value, Runnable action, boolean enabled) {
        Color border = GdxSettingsStyle.rowBorder(enabled,
                enabled && hovered(x, y, w, h));
        Color fill = GdxSettingsStyle.rowFill(enabled,
                enabled && pressed(x, y, w, h));
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
        settingsStepper(x, y, w, h, label, value, minus, plus, null, true);
    }

    private void settingsStepper(float x, float y, float w, float h,
            String label, String value, Runnable minus, Runnable plus,
            Runnable valueAction) {
        settingsStepper(x, y, w, h, label, value, minus, plus, valueAction,
                true);
    }

    private void settingsStepper(float x, float y, float w, float h,
            String label, String value, Runnable minus, Runnable plus,
            boolean enabled) {
        settingsStepper(x, y, w, h, label, value, minus, plus, null, enabled);
    }

    private void settingsStepper(float x, float y, float w, float h,
            String label, String value, Runnable minus, Runnable plus,
            Runnable valueAction, boolean enabled) {
        GdxSettingsLayout.StepperRow row = GdxSettingsLayout.stepperRow(
                x, y, w, h);
        outerBox(x, y, w, h,
                GdxSettingsStyle.rowBorder(enabled,
                        enabled && hovered(x, y, w, h)),
                GdxSettingsStyle.rowFill(enabled, false));
        Rectangle minusBounds = row.minusButton();
        Rectangle valueBounds = row.value();
        Rectangle plusBounds = row.plusButton();
        embeddedButtonSurface(minusBounds.x, minusBounds.y,
                minusBounds.width, minusBounds.height, enabled);
        embeddedButtonSurface(plusBounds.x, plusBounds.y,
                plusBounds.width, plusBounds.height, enabled);
        shapes.setColor(new Color(0x31445fff));
        shapes.rect(valueBounds.x, valueBounds.y + 8f, 2f,
                valueBounds.height - 16f);
        shapes.rect(valueBounds.x + valueBounds.width,
                valueBounds.y + 8f, 2f, valueBounds.height - 16f);
        textFit(smallFont, label, row.label().x,
                row.label().y + row.label().height / 2f + 10f,
                enabled ? Color.WHITE : DISABLED, false,
                row.label().width);
        text(headingFont, "-", minusBounds.x + minusBounds.width / 2f,
                minusBounds.y + minusBounds.height / 2f + 14f,
                enabled ? Color.WHITE : DISABLED, true);
        textFit(uiFont, value, valueBounds.x + valueBounds.width / 2f,
                valueBounds.y + valueBounds.height / 2f + 11f,
                enabled ? GOLD : DISABLED, true,
                valueBounds.width - 12f);
        text(headingFont, "+", plusBounds.x + plusBounds.width / 2f,
                plusBounds.y + plusBounds.height / 2f + 14f,
                enabled ? Color.WHITE : DISABLED, true);
        if (enabled) {
            repeatHit(minusBounds.x, minusBounds.y, minusBounds.width,
                    minusBounds.height, minus);
            if (valueAction != null) {
                hit(valueBounds.x, valueBounds.y, valueBounds.width,
                        valueBounds.height, valueAction);
            }
            repeatHit(plusBounds.x, plusBounds.y, plusBounds.width,
                    plusBounds.height, plus);
        }
    }

    private void settingsInfoRow(float x, float y, float w, float h,
            String label, String value) {
        outerBox(x, y, w, h, GdxSettingsStyle.rowBorder(true, false),
                GdxSettingsStyle.rowFill(true, false));
        float valueWidth = Math.min(660f, w * 0.58f);
        textFit(smallFont, label, x + 20f, y + h / 2f + 10f,
                Color.WHITE, false, w - valueWidth - 52f);
        textFit(uiFont, value, x + w - valueWidth / 2f - 18f,
                y + h / 2f + 11f, GOLD, true, valueWidth);
    }

    private void adjustLightLevel(int direction) {
        GdxAppearanceOptions.adjustLightLevel(initialProperties, direction);
    }

    private void drawLobbyGameSettings(float x, float y, float w, float h) {
        if (settingsTable == null || lobby == null) {
            textFit(headingFont, settingsGameText("unavailable"),
                    x + w / 2f, y + h / 2f, MUTED, true, w - 68f);
            return;
        }
        List<String> pages = settingsSession.gamePages();
        if (pages.isEmpty()) {
            textFit(headingFont, settingsGameText("unavailable"),
                    x + w / 2f, y + h / 2f, MUTED, true, w - 68f);
            return;
        }
        settingsGamePage = MathUtils.clamp(settingsGamePage, 0,
                pages.size() - 1);
        boolean editable = lobby.host();
        boolean economyEditable = editable && !settingsTable.economyLocked();
        String serverLockKey = "gdx.settings.game.server_only";
        String economyLockKey = editable
                ? "gdx.settings.game.economy_locked" : serverLockKey;
        float rowY = y + h - GdxSettingsLayout.CONTENT_ROW_TOP_INSET;
        switch (settingsGamePage) {
            case 0 -> drawLobbyBlindSettings(x, w, rowY, economyEditable,
                    economyLockKey);
            case 1 -> drawLobbyPurchaseSettings(x, w, rowY, editable,
                    economyEditable, serverLockKey, economyLockKey);
            case 2 -> drawLobbyRebuySettings(x, w, rowY, editable,
                    serverLockKey);
            case 3 -> drawLobbyBotSettings(x, w, rowY, editable,
                    serverLockKey);
            case 4 -> drawLobbyRoundSettings(x, w, rowY, editable,
                    serverLockKey);
            default -> drawLobbyRuleSettings(x, w, rowY, editable,
                    serverLockKey);
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
            boolean enabled, String lockKey) {
        GdxSettingsLayout.GameColumns columns =
                GdxSettingsLayout.gameColumns(x + 34f, w - 68f);
        float columnWidth = columns.width();
        float leftX = columns.leftX();
        float rightX = columns.rightX();
        settingsStepper(leftX, y, columnWidth, 70f,
                settingsGameText("row.structure"),
                settingsTable.structureName() == null
                        ? uppercase(gameText.translate(
                                "gdx.settings.value.default"))
                        : settingsTable.structureName(),
                () -> adjustSettingsBlindStructure(-1),
                () -> adjustSettingsBlindStructure(1), enabled);
        settingsStepper(leftX, y - GdxSettingsLayout.ROW_STRIDE, columnWidth, 70f,
                settingsGameText("row.initial_blinds"), settingsBlindLevel(),
                () -> adjustSettingsBlindLevel(-1),
                () -> adjustSettingsBlindLevel(1), enabled);
        toggle(leftX, y - 2f * GdxSettingsLayout.ROW_STRIDE, columnWidth,
                settingsGameText("row.ante"),
                settingsTable.ante(), () -> settingsTable
                        .setAnte(!settingsTable.ante()), enabled);
        toggle(leftX, y - 3f * GdxSettingsLayout.ROW_STRIDE, columnWidth,
                settingsGameText("row.straddle"),
                settingsTable.straddle(), () -> settingsTable
                        .setStraddle(!settingsTable.straddle()), enabled);
        button(leftX, y - 4f * GdxSettingsLayout.ROW_STRIDE, columnWidth, 70f,
                settingsGameText("row.manage_structures"), false,
                this::openSettingsBlindStructureEditor, enabled);
        toggle(rightX, y, columnWidth,
                settingsGameText("row.increase_blinds"),
                settingsTable.increaseBlinds(), () -> settingsTable
                        .setIncreaseBlinds(!settingsTable.increaseBlinds()),
                enabled);
        settingsStepper(rightX, y - GdxSettingsLayout.ROW_STRIDE, columnWidth, 70f,
                settingsGameText("row.unit"),
                settingsBlindUnit(), this::toggleSettingsBlindIncreaseType,
                this::toggleSettingsBlindIncreaseType,
                enabled && settingsTable.increaseBlinds());
        settingsStepper(rightX, y - 2f * GdxSettingsLayout.ROW_STRIDE, columnWidth, 70f,
                settingsGameText("row.interval"),
                settingsBlindInterval(),
                () -> adjustSettingsBlindInterval(-1),
                () -> adjustSettingsBlindInterval(1),
                enabled && settingsTable.increaseBlinds());
        toggle(rightX, y - 3f * GdxSettingsLayout.ROW_STRIDE, columnWidth,
                settingsGameText("row.blind_cap"),
                settingsTable.blindCap(), () -> settingsTable
                        .setBlindCap(!settingsTable.blindCap()),
                enabled && settingsTable.blindCapControlEnabled());
        settingsStepper(rightX, y - 4f * GdxSettingsLayout.ROW_STRIDE, columnWidth, 70f,
                settingsGameText("row.cap"), settingsBlindCap(),
                () -> adjustSettingsBlindCap(-1),
                () -> adjustSettingsBlindCap(1),
                enabled && settingsTable.blindCapRaisesEnabled());
        if (!enabled) {
            for (int row = 0; row < 5; row++) {
                tooltip(leftX, y - row * GdxSettingsLayout.ROW_STRIDE,
                        columnWidth, GdxSettingsLayout.ROW_HEIGHT, lockKey);
                tooltip(rightX, y - row * GdxSettingsLayout.ROW_STRIDE,
                        columnWidth, GdxSettingsLayout.ROW_HEIGHT, lockKey);
            }
        }
    }

    private void drawLobbyPurchaseSettings(float x, float w, float y,
            boolean editable, boolean economyEditable, String serverLockKey,
            String economyLockKey) {
        toggle(x + 34f, y, w - 68f,
                settingsGameText("row.fixed_buyin"),
                settingsTable.fixedBuyin(), () -> settingsTable
                        .setFixedBuyin(!settingsTable.fixedBuyin()),
                economyEditable);
        settingsStepper(x + 34f, y - GdxSettingsLayout.ROW_STRIDE, w - 68f,
                settingsGameText("row.initial_buyin"),
                settingsTable.buyin(),
                () -> settingsTable.setBuyin(settingsTable.buyin() - 1),
                () -> settingsTable.setBuyin(settingsTable.buyin() + 1),
                economyEditable && settingsTable.fixedBuyin());
        settingsStepper(x + 34f, y - 2f * GdxSettingsLayout.ROW_STRIDE, w - 68f,
                settingsGameText("row.minimum_range_bb"),
                settingsTable.minBuyinBb(), () -> settingsTable
                        .setMinBuyinBb(settingsTable.minBuyinBb() - 5),
                () -> settingsTable.setMinBuyinBb(
                        settingsTable.minBuyinBb() + 5), economyEditable);
        settingsStepper(x + 34f, y - 3f * GdxSettingsLayout.ROW_STRIDE, w - 68f,
                settingsGameText("row.maximum_range_bb"),
                settingsTable.maxBuyinBb(), () -> settingsTable
                        .setMaxBuyinBb(settingsTable.maxBuyinBb() - 5),
                () -> settingsTable.setMaxBuyinBb(
                        settingsTable.maxBuyinBb() + 5), economyEditable);
        settingsStepper(x + 34f, y - 4f * GdxSettingsLayout.ROW_STRIDE, w - 68f, 70f,
                settingsGameText("row.rebuy_cap"),
                settingsTable.rebuyCapPolicy()
                        == NewGameTableDraft.RebuyCapPolicy.BUY_IN
                                ? "BUY-IN"
                                : settingsGameText("value.highest_stack"),
                this::cycleSettingsRebuyCap,
                this::cycleSettingsRebuyCap,
                editable && settingsTable.rebuy());
        if (!economyEditable) {
            for (int row = 0; row < 4; row++) {
                tooltip(x + 34f,
                        y - row * GdxSettingsLayout.ROW_STRIDE, w - 68f,
                        GdxSettingsLayout.ROW_HEIGHT, economyLockKey);
            }
        }
        if (!editable) {
            tooltip(x + 34f, y - 4f * GdxSettingsLayout.ROW_STRIDE,
                    w - 68f, GdxSettingsLayout.ROW_HEIGHT, serverLockKey);
        }
    }

    private void drawLobbyRebuySettings(float x, float w, float y,
            boolean editable, String lockKey) {
        toggle(x + 34f, y, w - 68f, settingsGameText("row.rebuy"),
                settingsTable.rebuy(),
                () -> settingsTable.setRebuy(!settingsTable.rebuy()),
                editable);
        toggle(x + 34f, y - GdxSettingsLayout.ROW_STRIDE, w - 68f,
                settingsGameText("row.player_limit"),
                settingsTable.rebuyLimit(), () -> settingsTable
                        .setRebuyLimit(!settingsTable.rebuyLimit()),
                editable && settingsTable.rebuy());
        settingsStepper(x + 34f, y - 2f * GdxSettingsLayout.ROW_STRIDE, w - 68f,
                settingsGameText("row.maximum_rebuys"),
                settingsTable.rebuyLimitCount(),
                () -> settingsTable.setRebuyLimitCount(
                        settingsTable.rebuyLimitCount() - 1),
                () -> settingsTable.setRebuyLimitCount(
                        settingsTable.rebuyLimitCount() + 1),
                editable && settingsTable.rebuyLimitCountEnabled());
        if (!editable) addLobbySettingsLockTooltips(x + 34f, y, w - 68f,
                3, lockKey);
    }

    private void drawLobbyBotSettings(float x, float w, float y,
            boolean editable, String lockKey) {
        settingsStepper(x + 34f, y, w - 68f, 70f,
                settingsGameText("row.bot_difficulty"),
                settingsBotDifficulty(),
                () -> adjustSettingsBotDifficulty(-1),
                () -> adjustSettingsBotDifficulty(1),
                editable);
        toggle(x + 34f, y - GdxSettingsLayout.ROW_STRIDE, w - 68f,
                settingsGameText("row.bot_rebuy"),
                settingsTable.botRebuy(), () -> settingsTable
                        .setBotRebuy(!settingsTable.botRebuy()),
                editable && settingsTable.botRebuyEnabled());
        toggle(x + 34f, y - 2f * GdxSettingsLayout.ROW_STRIDE, w - 68f,
                settingsGameText("row.bot_balance"),
                settingsTable.botBalanceToHumans(), () -> settingsTable
                        .setBotBalanceToHumans(
                                !settingsTable.botBalanceToHumans()), editable);
        if (!editable) addLobbySettingsLockTooltips(x + 34f, y, w - 68f,
                3, lockKey);
    }

    private void drawLobbyRoundSettings(float x, float w, float y,
            boolean editable, String lockKey) {
        toggle(x + 34f, y, w - 68f,
                settingsGameText("row.hand_limit"),
                settingsTable.handLimit(), () -> settingsTable
                        .setHandLimit(!settingsTable.handLimit()), editable);
        settingsStepper(x + 34f, y - GdxSettingsLayout.ROW_STRIDE, w - 68f,
                settingsGameText("row.hand_count"),
                settingsTable.handLimitCount(), () -> settingsTable
                        .setHandLimitCount(settingsTable.handLimitCount() - 1),
                () -> settingsTable.setHandLimitCount(
                        settingsTable.handLimitCount() + 1),
                editable && settingsTable.handLimit());
        toggle(x + 34f, y - 2f * GdxSettingsLayout.ROW_STRIDE, w - 68f,
                settingsGameText("row.think_time"),
                settingsTable.thinkTime(), () -> settingsTable
                        .setThinkTime(!settingsTable.thinkTime()), editable);
        settingsStepper(x + 34f, y - 3f * GdxSettingsLayout.ROW_STRIDE, w - 68f,
                settingsGameText("row.think_seconds"),
                settingsTable.thinkSeconds(),
                () -> settingsTable.setThinkSeconds(
                        settingsTable.thinkSeconds() - 5),
                () -> settingsTable.setThinkSeconds(
                        settingsTable.thinkSeconds() + 5),
                editable && settingsTable.thinkTime());
        settingsStepper(x + 34f, y - 4f * GdxSettingsLayout.ROW_STRIDE, w - 68f,
                settingsGameText("row.showdown_seconds"),
                settingsTable.showdownSeconds(),
                () -> settingsTable.setShowdownSeconds(
                        settingsTable.showdownSeconds() - 5),
                () -> settingsTable.setShowdownSeconds(
                        settingsTable.showdownSeconds() + 5), editable);
        if (!editable) addLobbySettingsLockTooltips(x + 34f, y, w - 68f,
                5, lockKey);
    }

    private void drawLobbyRuleSettings(float x, float w, float y,
            boolean editable, String lockKey) {
        toggle(x + 34f, y, w - 68f, "IWTSTH",
                settingsTable.iwtsth(), () -> settingsTable
                        .setIwtsth(!settingsTable.iwtsth()), editable);
        toggle(x + 34f, y - GdxSettingsLayout.ROW_STRIDE, w - 68f, "RUN IT TWICE",
                settingsTable.runItTwice(), () -> settingsTable
                        .setRunItTwice(!settingsTable.runItTwice()), editable);
        settingsStepper(x + 34f, y - 2f * GdxSettingsLayout.ROW_STRIDE, w - 68f, 70f,
                settingsGameText("row.rabbit_hunting"), settingsRabbitText(),
                () -> adjustSettingsRabbit(-1),
                () -> adjustSettingsRabbit(1), editable);
        if (!editable) addLobbySettingsLockTooltips(x + 34f, y, w - 68f,
                3, lockKey);
    }

    private void addLobbySettingsLockTooltips(float x, float firstY,
            float width, int rows, String key) {
        for (int row = 0; row < rows; row++) {
            tooltip(x, firstY - row * GdxSettingsLayout.ROW_STRIDE, width,
                    GdxSettingsLayout.ROW_HEIGHT, key);
        }
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
        themedButton(controlsX, y + 10f, 48f, 48f, "-",
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
                settingsTable.rabbitHunting().ordinal(), gameText);
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
                shortcutBindings.editableEntries(gameText);
        float firstY = y + h - GdxSettingsLayout.CONTENT_ROW_TOP_INSET;
        GdxSettingsLayout.PixelRows rows = GdxSettingsLayout.pixelRows(
                firstY, y + 14f,
                firstY + GdxSettingsLayout.ROW_HEIGHT, entries.size(),
                settingsShortcutScroll);
        settingsShortcutScroll = rows.offset();
        for (int row = rows.firstIndex(); row < rows.lastExclusive(); row++) {
            GdxShortcutBindings.ShortcutEntry entry = entries.get(row);
            boolean capturing = entry.id().equals(settingsShortcutCaptureId);
            float rowY = rows.rowY(row);
            shortcutRow(x + 34f, rowY, w - 68f,
                    capturing ? uppercase(gameText.translate(
                            "gdx.settings.shortcut.press_key"))
                            : entry.display(),
                    uppercase(entry.markedDescription()), capturing);
            hit(x + 34f, rowY, w - 68f, 62f, () -> {
                settingsShortcutCaptureId = entry.id();
                settingsShortcutStatus = "prompt";
            });
        }
        drawSettingsRowScrollbar(x + w - 24f, rows);
        if (!settingsShortcutStatus.isBlank()) {
            Color color = settingsShortcutStatus.equals("conflict")
                    || settingsShortcutStatus.equals("unsupported")
                            ? ORANGE : CYAN;
            textFit(tinyFont, uppercase(gameText.translate(
                    "gdx.settings.shortcut.status."
                            + settingsShortcutStatus)), x + w / 2f,
                    y + 62f, color, true, w - 68f);
        }
    }

    private void drawDebugSettings(float x, float y, float w, float h) {
        Rectangle content = new Rectangle(x, y, w, h);
        Rectangle copy = GdxSettingsLayout.debugCopyButton(content);
        float consoleX = x + 24f;
        float consoleY = y + 82f;
        float consoleW = w - 48f;
        float consoleH = h - 110f;
        outerBox(consoleX, consoleY, consoleW, consoleH, LINE,
                new Color(0x03070cff));
        compactButton(copy.x, copy.y, copy.width, copy.height,
                uppercase(gameText.translate("gdx.settings.debug.copy")),
                false, this::copySettingsDebugLog);

        List<GdxDebugLogFormatter.Line> lines = settingsDebugVisualLines(
                consoleW - 50f);
        float viewportHeight = Math.max(0f, consoleH - 16f);
        float maximum = Math.max(0f, lines.size() * 25f - viewportHeight);
        settingsDebugScroll = CoronaPokerGdxTable
                .preservePixelScrollOnAppend(settingsDebugScroll,
                        settingsDebugLineCount, lines.size(), 25f);
        settingsDebugLineCount = lines.size();
        settingsDebugScroll = MathUtils.clamp(settingsDebugScroll, 0, maximum);
        for (int i = 0; i < lines.size(); i++) {
            float lineY = CoronaPokerGdxTable.anchoredPixelRowY(lines.size(),
                    i, 25f, consoleY + 8f, viewportHeight,
                    settingsDebugScroll, maximum) + 25f;
            if (lineY < consoleY || lineY > consoleY + consoleH + 25f) {
                continue;
            }
            float runX = consoleX + 14f;
            for (GdxDebugLogFormatter.Run run : lines.get(i).runs()) {
                settingsDebugTexts.add(new TextItem(tinyFont, run.text(),
                        runX, lineY, new Color(run.foreground()), false,
                        false));
                runX += textWidth(tinyFont, run.text());
            }
        }
        settingsDebugViewport.set(consoleX + 8f, consoleY + 8f,
                consoleW - 36f, viewportHeight);

        float trackX = consoleX + consoleW - 20f;
        settingsDebugScrollTrack.set(trackX
                - (GdxSettingsLayout.SCROLLBAR_HIT_WIDTH
                - GdxSettingsLayout.SCROLLBAR_WIDTH) / 2f,
                consoleY + 8f, GdxSettingsLayout.SCROLLBAR_HIT_WIDTH,
                consoleH - 16f);
        settingsDebugScrollMaximum = maximum;
        shapes.setColor(new Color(0x26364dff));
        roundedRect(trackX, consoleY + 8f,
                GdxSettingsLayout.SCROLLBAR_WIDTH, consoleH - 16f,
                GdxSettingsLayout.SCROLLBAR_WIDTH / 2f);
        float thumbH = maximum == 0 ? consoleH - 16f
                : Math.max(32f, (consoleH - 16f)
                        * viewportHeight
                        / Math.max(1f, lines.size() * 25f));
        settingsDebugScrollThumbHeight = thumbH;
        float travel = Math.max(0f, consoleH - 16f - thumbH);
        float ratio = maximum == 0 ? 0f
                : settingsDebugScroll / maximum;
        shapes.setColor(CYAN_DARK);
        roundedRect(trackX, consoleY + 8f + travel * ratio,
                GdxSettingsLayout.SCROLLBAR_WIDTH, thumbH,
                GdxSettingsLayout.SCROLLBAR_WIDTH / 2f);
    }

    private static List<String> debugLines() {
        return DebugLog.snapshot().lines().toList();
    }

    private List<GdxDebugLogFormatter.Line> settingsDebugVisualLines(
            float width) {
        List<String> source = debugLines();
        if (Math.abs(width - settingsDebugWrapWidth) > 0.5f
                || !source.equals(settingsDebugSourceCache)) {
            settingsDebugSourceCache = List.copyOf(source);
            settingsDebugWrapWidth = width;
            settingsDebugVisualCache = GdxDebugLogFormatter.wrap(
                    GdxDebugLogFormatter.format(source), width,
                    run -> textWidth(tinyFont, run.text()));
        }
        return settingsDebugVisualCache;
    }

    private void copySettingsDebugLog() {
        Gdx.app.getClipboard().setContents(GdxDebugLogFormatter.clipboardText(
                debugLines()));
        showToast(gameText.translate("gdx.settings.debug.copied"));
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
        return GdxSettingsContract.masterVolume(initialProperties);
    }

    private void togglePreference(String key, boolean fallback) {
        boolean next = !preferenceBoolean(key, fallback);
        initialProperties.setProperty(key, Boolean.toString(next));
        if ("musica".equals(key) || "sonido_ascensor".equals(key)
                || "musica_sala_espera".equals(key)
                || "musica_about".equals(key)) {
            syncMusicForSurface();
        }
        if ("sonido_efectos".equals(key)
                && !preferenceBoolean(key, fallback)) {
            soundEnabledCue.stop();
            // Keep the just-started OFF cue alive while the remaining effect
            // families are silenced.
            participantJoinedCue.stop();
            participantLeftCue.stop();
        }
    }

    private void playFrontendSwitchSound(boolean enabled) {
        if (!preferenceBoolean("sonido_interruptor", true)) return;
        playFrontendSound(enabled ? soundEnabledCue : soundDisabledCue, 0.60f);
    }

    private void playPreferenceSound(String resource, String preferenceKey,
            float volume) {
        if (!preferenceBoolean(preferenceKey, true)
                || failedPreferenceSoundCues.contains(resource)) return;
        try {
            Sound sound = preferenceSoundCues.computeIfAbsent(resource,
                    path -> Gdx.audio.newSound(
                            Gdx.files.internal("sounds/" + path)));
            playFrontendSound(sound, volume);
        } catch (RuntimeException failure) {
            failedPreferenceSoundCues.add(resource);
            LOGGER.log(Level.WARNING,
                    "GDX optional sound could not be loaded: " + resource,
                    failure);
        }
    }

    private void playFrontendSound(Sound sound, float volume) {
        if (!audioOutputAvailable() || !audioControl.enabled()
                || !preferenceBoolean("sonido_efectos", true)) return;
        try {
            sound.play(volume * masterVolume());
        } catch (RuntimeException unavailable) {
            // Device hotplug can race the one-second topology poll.
        }
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
        return GdxSettingsContract.msaaStatusLabel(
                presentationSettings.requestedMsaaSamples(),
                presentationSettings.actualMsaaSamples(), gameText);
    }

    private void selectNextMsaa() {
        presentationSettings.selectNextMsaaSamples(false);
    }

    private void selectPreviousMsaa() {
        presentationSettings.selectPreviousMsaaSamples(false);
    }

    private String windowModeSettingLabel() {
        return GdxWindowMode.configured(initialProperties).label(gameText);
    }

    private void selectNextWindowMode() {
        adjustWindowMode(1);
    }

    private void selectPreviousWindowMode() {
        adjustWindowMode(-1);
    }

    private void adjustWindowMode(int direction) {
        GdxWindowMode next = GdxWindowMode.adjusted(initialProperties,
                direction);
        GdxDisplayModeController.apply(initialProperties, next);
    }

    private void drawSoundControl(float x, float y, float w, float h,
            boolean showLabel) {
        if (showLabel) {
            text(smallFont, uppercase(gameText.translate("audio.sonidos")),
                    x, y + h * 0.66f, MUTED, false);
        }
        float iconSize = Math.min(52f, h);
        float iconX = showLabel ? x + w - iconSize
                : x + (w - iconSize) / 2f;
        boolean outputAvailable = audioOutputAvailable();
        uiImages.add(new UiImageItem(outputAvailable && audioControl.enabled()
                ? soundIcon : muteIcon, iconX,
                y + (h - iconSize) / 2f, iconSize, iconSize,
                outputAvailable ? Color.WHITE : AUDIO_UNAVAILABLE_RED));
        float hitX = showLabel ? x - 12f : x - 8f;
        float hitW = showLabel ? w + 24f : w + 16f;
        hit(hitX, y - 6f, hitW, h + 12f,
                this::toggleMasterSound);
        if (!outputAvailable) {
            tooltip(hitX, y - 6f, hitW, h + 12f,
                    "gdx.audio.no_output_device");
        }
    }

    private void toggleMasterSound() {
        if (!audioOutputAvailable()) return;
        GdxToggleSoundAction.run(audioControl.enabled(),
                this::toggleMasterSoundState,
                this::playFrontendSwitchSound);
    }

    private void toggleMasterSoundState() {
        boolean enabled = audioControl.toggle(surface != Surface.SETTINGS);
        if (!enabled) GdxVoicePlayback.stop();
        syncMusicForSurface();
    }

    private static Music music(String path, float volume) {
        Music music = Gdx.audio.newMusic(Gdx.files.internal(path));
        music.setLooping(true);
        music.setVolume(volume);
        return music;
    }

    private boolean musicMasterEnabled() {
        return audioOutputAvailable() && audioControl.enabled()
                && preferenceBoolean("musica", true);
    }

    private boolean audioOutputAvailable() {
        GdxApplicationShell shell = GdxApplicationShell.active();
        return shell == null || shell.audioOutputAvailable();
    }

    void audioOutputAvailabilityChanged(boolean available) {
        if (!available) GdxVoicePlayback.stop();
        syncMusicForSurface();
    }

    private void syncMusicForSurface() {
        if (backgroundMusic == null || waitingRoomMusic == null
                || aboutMusic == null || statsMusic == null) return;
        float volume = masterVolume();
        GdxVoicePlayback.refreshVolume(volume);
        setMusicVolume(backgroundMusic, 0.40f * volume);
        setMusicVolume(waitingRoomMusic, 0.90f * volume);
        setMusicVolume(aboutMusic, 0.90f * volume);
        setMusicVolume(statsMusic, 0.30f * volume);
        boolean lobbyMusic = surface == Surface.LOBBY
                || (surface == Surface.SCREENSHOTS
                        && screenshotReturnSurface == Surface.LOBBY)
                || (surface == Surface.STATS
                        && statsConfirmation != StatsConfirmation.NONE)
                || (surface == Surface.SETTINGS
                && settingsReturnSurface == Surface.LOBBY);
        boolean aboutDialogMusic = surface == Surface.MENU && aboutOpen;
        boolean statsDialogMusic = surface == Surface.STATS;
        boolean playBackground = frontendTrackAllowed(tableAudioSuspended,
                !startupAudioHeld
                && musicMasterEnabled() && !lobbyMusic && !aboutDialogMusic
                && !statsDialogMusic
                && preferenceBoolean("sonido_ascensor", true));
        boolean playWaitingRoom = frontendTrackAllowed(tableAudioSuspended,
                musicMasterEnabled() && lobbyMusic
                && preferenceBoolean("musica_sala_espera", true));
        boolean playAbout = frontendTrackAllowed(tableAudioSuspended,
                musicMasterEnabled() && aboutDialogMusic
                && preferenceBoolean("musica_about", true));
        boolean playStats = frontendTrackAllowed(tableAudioSuspended,
                musicMasterEnabled() && statsDialogMusic
                && preferenceBoolean("musica_stats", true));
        syncTrack(backgroundMusic, playBackground);
        syncTrack(waitingRoomMusic, playWaitingRoom);
        syncTrack(aboutMusic, playAbout);
        syncTrack(statsMusic, playStats);
    }

    private static void setMusicVolume(Music music, float volume) {
        try {
            music.setVolume(volume);
        } catch (RuntimeException unavailable) {
            // A physical endpoint may disappear during this render turn.
        }
    }

    private static void syncTrack(Music music, boolean play) {
        try {
            if (play) {
                if (!music.isPlaying()) music.play();
            } else if (music.isPlaying()) {
                music.pause();
            }
        } catch (RuntimeException unavailable) {
            // The physical endpoint can disappear between the topology poll
            // and this render turn. The next hotplug poll restores playback.
        }
    }

    static boolean frontendTrackAllowed(boolean tableAudioSuspended,
            boolean requestedBySurface) {
        return requestedBySurface && !tableAudioSuspended;
    }

    /**
     * Suspends the persistent frontend before the table takes input/audio.
     * Background tracks are only paused so the table can continue their
     * decoder position. Lobby voice notes and an unfinished recording are
     * transient, however, and must never leak into the active hand.
     */
    void suspendForTable() {
        suspendForTable(false);
    }

    void suspendForTable(boolean preserveBackgroundTrack) {
        tableAudioSuspended = true;
        stopLobbyTransientAudio();
        if (!preserveBackgroundTrack && backgroundMusic != null) {
            backgroundMusic.pause();
        }
        if (waitingRoomMusic != null) waitingRoomMusic.pause();
        if (aboutMusic != null) aboutMusic.pause();
        if (statsMusic != null) statsMusic.pause();
    }

    private void stopLobbyTransientAudio() {
        cancelLobbyVoiceRecording();
        stopLobbyChatVoice();
    }

    void resumeMusic() {
        tableAudioSuspended = false;
        syncMusicForSurface();
    }

    Music tableBackgroundMusic() {
        return backgroundMusic;
    }

    private void openNewGame(NewGameConnectionDraft.Mode mode) {
        recoveryLoadGeneration++;
        autoSubmitRecovery = false;
        connection = defaultConnection(initialProperties, mode);
        if (mode == NewGameConnectionDraft.Mode.CREATE) {
            refreshHostAddressOptions();
        } else {
            hostAddressLoadGeneration++;
            hostAddressOptions = List.of(GdxLocalServerAddresses.LOOPBACK_NAME);
        }
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
        boolean joining = connection.mode() == NewGameConnectionDraft.Mode.JOIN;
        String title = joining
                ? gameText.translate("game.unirme_a_timba")
                : uppercase(gameText.translate("ui.nueva_timba"));
        textFit(titleFont, title, joining ? 225f : 85f,
                joining ? 900f : 985f, GOLD, false,
                joining ? 1470f : 1750f);
    }

    private void drawNewGameDialogFrame() {
        // This is a full frontend screen, not a modal. Do not dim the complete
        // background: the shared felt and fixed logo must remain visible just
        // as they do in the menu, settings and statistics screens.
        boolean joining = connection.mode() == NewGameConnectionDraft.Mode.JOIN;
        float frameX = joining ? 190f : 50f;
        float frameY = joining ? 120f : 35f;
        float frameW = joining ? 1540f : 1820f;
        float frameH = joining ? 820f : 990f;
        outerBox(frameX - 10f, frameY + 10f, frameW + 20f, frameH,
                CYAN_DARK, PANEL);
        shapes.setColor(new Color(0x36d9ffcc));
        roundedRect(frameX + 12f, frameY + frameH - 3f,
                frameW - 24f, 3f, 1.5f);
    }

    private void drawProgress() {
        boolean joining = connection.mode() == NewGameConnectionDraft.Mode.JOIN;
        if (joining) {
            return;
        }
        List<String> names = NEW_GAME_PAGE_LABEL_KEYS.stream()
                .map(gameText::translate)
                .map(this::uppercase)
                .toList();
        for (int i = 0; i < names.size(); i++) {
            final int targetPage = i;
            float x = 55f;
            float y = 760f - i * 108f;
            boolean selected = i == page;
            boolean hover = hovered(x, y, 320f, 78f);
            Color fill = selected ? new Color(0x101a2ee0)
                    : new Color(0x101a2ecc);
            Color border = selected ? CYAN : hover ? CYAN_DARK : LINE;
            GdxUiButtonStyle.drawPalette(shapes, x, y, 320f, 78f,
                    border, fill.r, fill.g, fill.b, fill.a,
                    true, hover ? 1f : 0f,
                    pressed(x, y, 320f, 78f), 1f);
            if (selected) {
                shapes.setColor(GOLD);
                roundedRect(x + 5f, y + 15f, 4f, 48f, 2f);
            }
            drawNavIcon(i, x + 39f, y + 39f,
                    selected ? CYAN : DISABLED);
            textFit(actionFont, names.get(i), x + 74f, y + 49f,
                    selected ? Color.WHITE : MUTED, false, 230f);
            hit(x, y, 320f, 78f, () -> {
                clearActiveField();
                page = targetPage;
            });
        }
    }

    private void drawIdentityPage() {
        if (connection.mode() == NewGameConnectionDraft.Mode.JOIN) {
            drawJoinIdentityPage();
            return;
        }
        panel(430f, 185f, 670f, 625f,
                uppercase(gameText.translate("gdx.newgame.your_profile")));
        panel(1130f, 185f, 725f, 625f,
                uppercase(gameText.translate("gdx.connection")));

        shapes.setColor(CYAN_DARK);
        shapes.circle(520f, 660f, 70f, 64);
        shapes.setColor(PANEL_LIGHT);
        shapes.circle(520f, 660f, 61f, 64);
        drawAvatarIcon(520f, 660f);
        hit(445f, 580f, 150f, 155f, this::selectAvatar);
        tooltip(445f, 580f, 150f, 155f, "tooltip.change_avatar");
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

        field(610f, 630f, 450f,
                gameText.translate("gdx.newgame.nick_required"),
                connection.nickname(), "nick", false);
        field(610f, 475f, 450f,
                gameText.translate("gdx.newgame.password_optional"),
                connection.password(), "password", true);
        dropdownChoice(1170f, 630f, 430f,
                gameText.translate("gdx.newgame.server_required"),
                connection.server(), () -> openDropdown(
                        Dropdown.HOST_ADDRESS), true);
        field(1630f, 630f, 175f, gameText.translate("gdx.port"),
                connection.port(), "port", false);
        tooltip(1170f, 630f, 430f, 72f, "tooltip.cfg.server_ip");
        tooltip(1630f, 630f, 175f, 72f, "tooltip.cfg.server_port");
        toggle(1170f, 475f, 635f, "UPnP", connection.upnp(),
                () -> connection.setUpnp(!connection.upnp()), true);
        tooltip(1170f, 475f, 635f, GdxSettingsLayout.ROW_HEIGHT,
                "tooltip.cfg.upnp");
    }

    private void drawJoinIdentityPage() {
        panel(295f, 285f, 620f, 480f,
                uppercase(gameText.translate("gdx.newgame.your_profile")));
        panel(945f, 285f, 680f, 480f,
                uppercase(gameText.translate("gdx.connection")));

        shapes.setColor(CYAN_DARK);
        shapes.circle(380f, 630f, 60f, 64);
        shapes.setColor(PANEL_LIGHT);
        shapes.circle(380f, 630f, 52f, 64);
        drawAvatarIcon(380f, 630f, 104f);
        hit(315f, 565f, 130f, 135f, this::selectAvatar);
        tooltip(315f, 565f, 130f, 135f, "tooltip.change_avatar");
        textFit(tinyFont, avatarSelectionPending
                ? uppercase(gameText.translate("gdx.opening"))
                : uppercase(gameText.translate("gdx.change")),
                380f, 552f, avatarSelectionPending ? DISABLED : CYAN,
                true, 125f);
        if (connection.avatar() != null) {
            compactButton(320f, 500f, 120f, 42f,
                    uppercase(gameText.translate("gdx.remove")), false,
                    this::resetAvatar, !avatarSelectionPending);
        }

        field(470f, 595f, 400f,
                gameText.translate("gdx.newgame.nick_required"),
                connection.nickname(), "nick", false);
        field(470f, 435f, 400f,
                gameText.translate("gdx.newgame.password_optional"),
                connection.password(), "password", true);

        field(985f, 595f, 385f,
                gameText.translate("gdx.newgame.server_required"),
                connection.server(), "server", false);
        field(1395f, 595f, 185f, gameText.translate("gdx.port"),
                connection.port(), "port", false);
        tooltip(985f, 595f, 385f, 72f, "tooltip.cfg.server_ip");
        tooltip(1395f, 595f, 185f, 72f, "tooltip.cfg.server_port");
        String history = connection.serverHistory().isEmpty()
                ? gameText.translate("gdx.newgame.no_previous_servers")
                : connection.serverHistory().get(historyIndex < 0
                        ? connection.serverHistory().size() - 1 : historyIndex);
        bidirectionalChoice(985f, 435f, 595f,
                gameText.translate("gdx.newgame.previous_servers"), history,
                this::previousServerHistory, this::nextServerHistory,
                !connection.serverHistory().isEmpty());
    }

    /** Global presets cover the complete table setup, never just networking. */
    private void drawProfilePage() {
        panel(430f, 185f, 1425f, 625f,
                uppercase(gameText.translate("gdx.newgame.profile_title")));
        textFit(actionFont, gameText.translate("gdx.newgame.profile_help"),
                500f, 700f, MUTED, false, 1285f);
        boolean profileEditable = !connection.recoverRequested();
        dropdownChoice(500f, 500f, 1285f, "",
                selectedPresetLabel(), () -> openDropdown(Dropdown.PROFILE),
                profileEditable);
        tooltip(500f, 500f, 1285f, 72f, "tooltip.cfg.preset");
        button(500f, 350f, 615f, 70f,
                gameText.translate("newgame.preset_guardar"), false,
                this::openPresetNameDialog, profileEditable);
        tooltip(500f, 350f, 615f, 70f, "tooltip.cfg.preset_save");
        button(1170f, 350f, 615f, 70f,
                gameText.translate("newgame.preset_borrar"), false,
                this::requestDeletePreset,
                profileEditable && selectedGamePreset >= 0);
        tooltip(1170f, 350f, 615f, 70f, "tooltip.cfg.preset_delete");
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
            showToast(gameText.translate(
                    "gdx.newgame.profile_default_loaded"));
            return;
        }
        GamePresetCatalog.Entry preset = gamePresets.get(selectedGamePreset);
        try {
            table = NewGameTableDraft.from(
                    NewGameTableDraft.Settings.parseWire(preset.settings()));
            showToast(gameText.translate("gdx.newgame.profile_loaded",
                    preset.name()));
        } catch (IllegalArgumentException invalid) {
            showToast(gameText.translate("gdx.newgame.profile_invalid"));
        }
    }

    private void selectGamePresetOption(int option) {
        if (connection.recoverRequested()) return;
        selectedGamePreset = option - 1;
        dropdown = Dropdown.NONE;
        if (option <= 0) {
            table = new NewGameTableDraft();
            showToast(gameText.translate(
                    "gdx.newgame.profile_default_loaded"));
            return;
        }
        if (selectedGamePreset >= gamePresets.size()) return;
        GamePresetCatalog.Entry preset = gamePresets.get(selectedGamePreset);
        try {
            table = NewGameTableDraft.from(
                    NewGameTableDraft.Settings.parseWire(preset.settings()));
            showToast(gameText.translate("gdx.newgame.profile_loaded",
                    preset.name()));
        } catch (IllegalArgumentException invalid) {
            selectedGamePreset = -1;
            showToast(gameText.translate("gdx.newgame.profile_invalid"));
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
            showToast(gameText.translate(
                    "gdx.newgame.profile_name_required"));
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
            showToast(gameText.translate("gdx.newgame.profile_limit",
                    GamePresetCatalog.MAX_PRESETS));
            return;
        }
        saveCurrentPreset(name, all);
    }

    private void saveCurrentPreset(String name,
            LinkedHashMap<String, GamePresetCatalog.Entry> all) {
        List<GamePresetCatalog.Entry> previous = List.copyOf(all.values());
        all.put(name, new GamePresetCatalog.Entry(name,
                table.snapshot().serializeForWire()));
        if (!persistGamePresets(all.values(), previous)) return;
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
        List<GamePresetCatalog.Entry> previous = List.copyOf(all.values());
        all.remove(name);
        if (!persistGamePresets(all.values(), previous)) return;
        refreshGamePresets(null);
        table = new NewGameTableDraft();
        closePresetDialog();
        showToast(gameText.translate("gdx.newgame.profile_deleted", name));
    }

    private boolean persistGamePresets(
            Collection<GamePresetCatalog.Entry> entries,
            Collection<GamePresetCatalog.Entry> previous) {
        GamePresetCatalog.writeTo(initialProperties, entries);
        try {
            // Saving a named profile is an explicit durability boundary. Do
            // not announce success while a deferred write is still pending.
            preferences.save();
            return true;
        } catch (IOException failure) {
            GamePresetCatalog.writeTo(initialProperties, previous);
            LOGGER.log(Level.SEVERE, "Could not persist game presets",
                    failure);
            showToast(gameText.translate(
                    "gdx.newgame.profile_persist_failed"));
            return false;
        }
    }

    private void closePresetDialog() {
        presetDialog = PresetDialog.NONE;
        presetNameDraft = "";
        clearActiveField();
    }

    private void drawPresetDialog() {
        hits.clear();
        textFieldHits.clear();
        passwordRevealHits.clear();
        editMenuHits.clear();
        GdxUiDialogStyle.drawBackdrop(shapes, WIDTH, HEIGHT, 1f);
        GdxUiDialogStyle.drawPanel(shapes, 560f, 350f, 800f, 360f,
                CYAN_DARK, 1f);
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

    private void continuePreviousGame() {
        if (submissions.submitting() || connection.recoverLoading()) return;
        if (connection.recoverRequested()) {
            submitNewGame();
            return;
        }
        autoSubmitRecovery = true;
        toggleRecover();
    }

    private void startRecoverLoad() {
        if (!connection.recoverRequested()) return;
        if (!connection.beginRecoverLoad()) return;
        long generation = ++recoveryLoadGeneration;
        showToast(gameText.translate("gdx.newgame.recover_loading"));
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
            showToast(cause == null
                    ? gameText.translate("gdx.newgame.recover_none")
                    : gameText.translate("gdx.newgame.recover_failed",
                            submissionError(cause)));
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
        showToast(gameText.translate("gdx.newgame.recover_loaded",
                recoveredDescription(game)));
        if (autoSubmitRecovery) {
            autoSubmitRecovery = false;
            submitNewGame();
        }
    }

    private String recoveredDescription(
            RecoverableGameRepository.RecoverableGame game) {
        String server = game.server();
        return server.isBlank()
                ? gameText.translate("gdx.newgame.recovered_game", game.id())
                : server;
    }

    private void drawBlindsPage() {
        panel(430f, 185f, 670f, 625f,
                settingsGameText("blinds"));
        panel(1130f, 185f, 725f, 625f,
                settingsGameText("row.increase_blinds"));

        dropdownChoice(470f, 610f, 590f,
                gameText.translate("gdx.settings.game.row.blind_structure"),
                table.structureName() == null
                        ? gameText.translate("gdx.settings.value.default")
                        : table.structureName(),
                () -> openDropdown(Dropdown.BLIND_STRUCTURE),
                !table.economyLocked());
        tooltip(470f, 610f, 590f, 72f, "tooltip.cfg.structure");
        bidirectionalChoice(470f, 460f, 590f,
                gameText.translate("gdx.settings.game.row.initial_blinds"),
                formatBlindLevel(), this::previousBlindLevel,
                this::nextBlindLevel, !table.economyLocked());
        tooltip(470f, 460f, 590f, 72f, "tooltip.cfg.blinds_level");
        button(470f, 320f, 285f, 70f,
                settingsGameText("row.default_structure"), false,
                this::selectDefaultBlindStructure, !table.economyLocked());
        button(775f, 320f, 285f, 70f,
                settingsGameText("row.manage"), false,
                this::openBlindStructureEditor, !table.economyLocked());
        toggle(470f, 205f, 280f, settingsGameText("row.ante"), table.ante(),
                () -> table.setAnte(!table.ante()), !table.economyLocked());
        tooltip(470f, 205f, 280f, GdxSettingsLayout.ROW_HEIGHT,
                "tooltip.cfg.ante");
        toggle(780f, 205f, 280f, settingsGameText("row.straddle"),
                table.straddle(),
                () -> table.setStraddle(!table.straddle()),
                !table.economyLocked());
        tooltip(780f, 205f, 280f, GdxSettingsLayout.ROW_HEIGHT,
                "tooltip.cfg.straddle");

        toggle(1170f, 610f, 645f,
                gameText.translate("gdx.settings.game.row.increase_blinds"),
                table.increaseBlinds(),
                () -> table.setIncreaseBlinds(!table.increaseBlinds()), !table.economyLocked());
        tooltip(1170f, 610f, 645f, GdxSettingsLayout.ROW_HEIGHT,
                "tooltip.cfg.double_blinds");
        bidirectionalChoice(1170f, 460f, 305f,
                settingsGameText("row.unit"),
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
        stepper(1510f, 460f, 305f,
                settingsGameText("row.interval"),
                table.blindInterval(), 1, Integer.MAX_VALUE,
                () -> table.setBlindInterval(table.blindInterval() - 1),
                () -> table.setBlindInterval(table.blindInterval() + 1),
                table.increaseBlinds() && !table.economyLocked());
        toggle(1170f, 325f, 645f,
                settingsGameText("row.blind_cap"), table.blindCap(),
                () -> table.setBlindCap(!table.blindCap()),
                table.blindCapControlEnabled() && !table.economyLocked());
        tooltip(1170f, 325f, 645f, GdxSettingsLayout.ROW_HEIGHT,
                "tooltip.cfg.blind_cap");
        stepper(1170f, 205f, 645f,
                settingsGameText("row.cap"), table.blindCapRaises(),
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

    private void selectBlindStructureOption(int option) {
        dropdown = Dropdown.NONE;
        if (option <= 0) {
            selectDefaultBlindStructure();
            return;
        }
        List<BlindStructureCatalog.Entry> saved = BlindStructureCatalog.read(
                initialProperties);
        if (option > saved.size()) return;
        BlindStructureCatalog.Entry entry = saved.get(option - 1);
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
            showToast(gameText.translate(
                    "gdx.newgame.blind_editor.review"));
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
        showToast(gameText.translate("gdx.newgame.blind_editor.saved"));
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
            showToast(gameText.translate(
                    "gdx.newgame.blind_editor.name_invalid"));
            return;
        }
        blindStructureDialog = BlindStructureDialog.EDITOR;
        blindStructureNameDraft = "";
        clearActiveField();
    }

    private void drawBlindStructureDialog() {
        hits.clear();
        textFieldHits.clear();
        passwordRevealHits.clear();
        editMenuHits.clear();
        GdxUiDialogStyle.drawBackdrop(shapes, WIDTH, HEIGHT, 1f);
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

        GdxUiDialogStyle.drawPanel(shapes, 220f, 105f, 1480f, 870f,
                CYAN_DARK, 1f);
        textFit(headingFont, uppercase(gameText.translate(
                "blinds.gestionar_estructuras")), 275f, 905f,
                GOLD, false, 1100f);
        textFit(smallFont,
                gameText.translate("gdx.newgame.blind_editor.help"),
                275f, 858f, MUTED, false, 1100f);

        BlindStructureCatalog.Entry selected = blindStructureEditor.selected();
        boolean hasSelection = selected != null;
        String structureLabel = hasSelection ? selected.name()
                : gameText.translate("gdx.newgame.blind_editor.none");
        bidirectionalChoice(275f, 710f, 580f,
                gameText.translate("blinds.estructura"),
                structureLabel, () -> blindStructureEditor.selectStructure(-1),
                () -> blindStructureEditor.selectStructure(1),
                !blindStructureEditor.entries().isEmpty());
        button(275f, 610f, 180f, 62f,
                uppercase(gameText.translate("blinds.nueva")), false,
                () -> openBlindStructureNameDialog(
                        BlindStructureDialog.NAME_NEW), true);
        button(475f, 610f, 180f, 62f,
                uppercase(gameText.translate("blinds.duplicar")), false,
                () -> openBlindStructureNameDialog(
                        BlindStructureDialog.NAME_DUPLICATE), hasSelection);
        button(675f, 610f, 180f, 62f,
                uppercase(gameText.translate("blinds.renombrar")), false,
                () -> openBlindStructureNameDialog(
                        BlindStructureDialog.NAME_RENAME), hasSelection);
        button(275f, 515f, 580f, 62f, uppercase(gameText.translate(
                "gdx.newgame.blind_editor.delete_structure")), false,
                () -> {
                    blindStructureDialog = BlindStructureDialog.DELETE;
                    clearActiveField();
                }, hasSelection);

        if (hasSelection) {
            int levelIndex = blindStructureEditor.selectedLevelIndex();
            BlindStructureCatalog.BlindLevel level =
                    blindStructureEditor.selectedLevel();
            bidirectionalChoice(930f, 710f, 690f,
                    gameText.translate("gdx.newgame.blind_editor.level"),
                    gameText.translate(
                            "gdx.newgame.blind_editor.level_count",
                            levelIndex + 1, selected.levels().size()),
                    () -> blindStructureEditor.selectLevel(-1),
                    () -> blindStructureEditor.selectLevel(1), true);
            blindAmountStepper(930f, 565f, 330f,
                    gameText.translate("blinds.ciega_pequena"),
                    level.smallBlind(),
                    () -> adjustEditorBlind(true, -1),
                    () -> adjustEditorBlind(true, 1));
            blindAmountStepper(1290f, 565f, 330f,
                    gameText.translate("blinds.ciega_grande_col"),
                    level.bigBlind(),
                    () -> adjustEditorBlind(false, -1),
                    () -> adjustEditorBlind(false, 1));
            button(930f, 455f, 330f, 62f,
                    uppercase(gameText.translate("blinds.anadir_nivel")), false,
                    () -> {
                        if (!blindStructureEditor.addLevel()) {
                            showToast(gameText.translate(
                                    "gdx.newgame.blind_editor.max_levels"));
                        }
                    }, selected.levels().size()
                            < BlindStructureRules.MAX_LEVELS);
            button(1290f, 455f, 330f, 62f,
                    uppercase(gameText.translate("blinds.quitar_nivel")), false,
                    () -> {
                        if (!blindStructureEditor.removeSelectedLevel()) {
                            showToast(gameText.translate(
                                    "gdx.newgame.blind_editor.min_levels"));
                        }
                    }, selected.levels().size() > 1);
            textFit(tinyFont,
                    gameText.translate(
                            "gdx.newgame.blind_editor.order_help"),
                    1275f, 405f, MUTED, true, 690f);
        } else {
            textFit(actionFont, uppercase(gameText.translate(
                    "gdx.newgame.blind_editor.create_first")),
                    1275f, 650f, MUTED, true, 650f);
        }

        button(275f, 155f, 300f, 66f,
                gameText.translate("ui.cancelar"), false,
                this::closeBlindStructureEditor, true);
        themedButton(1320f, 155f, 300f, 66f,
                gameText.translate("ui.guardar"),
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
            showToast(gameText.translate(
                    "gdx.newgame.blind_editor.order_error"));
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
        embeddedButtonSurface(x, y, side + 3f, 72f, true);
        embeddedButtonSurface(x + width - side - 3f, y,
                side + 3f, 72f, true);
        text(headingFont, "-", x + 38f, y + 48f, Color.WHITE, true);
        text(headingFont, "+", x + width - 38f, y + 48f,
                Color.WHITE, true);
        textFit(headingFont, formatBlind(value), x + width / 2f,
                y + 49f, GOLD, true, width - side * 2f - 24f);
        repeatHit(x, y, side + 6f, 72f, minus);
        repeatHit(x + width - side - 6f, y, side + 6f, 72f, plus);
    }

    private void drawBlindStructureNameDialog() {
        GdxUiDialogStyle.drawPanel(shapes, 560f, 350f, 800f, 360f,
                CYAN_DARK, 1f);
        String title = switch (blindStructureDialog) {
            case NAME_NEW -> gameText.translate(
                    "gdx.newgame.blind_editor.new_title");
            case NAME_DUPLICATE -> gameText.translate(
                    "gdx.newgame.blind_editor.duplicate_title");
            case NAME_RENAME -> gameText.translate(
                    "gdx.newgame.blind_editor.rename_title");
            default -> gameText.translate("blinds.gestionar_estructuras");
        };
        textFit(headingFont, uppercase(title), 960f, 635f, GOLD, true, 700f);
        field(660f, 475f, 600f,
                gameText.translate("blinds.nombre_estructura"),
                blindStructureNameDraft,
                "blindStructureName", false);
        themedButton(660f, 385f, 270f, 64f,
                gameText.translate("ui.volver"),
                ButtonTone.NEUTRAL, () -> {
                    blindStructureDialog = BlindStructureDialog.EDITOR;
                    clearActiveField();
                }, true);
        themedButton(990f, 385f, 270f, 64f,
                gameText.translate("ui.aceptar"),
                ButtonTone.POSITIVE, this::submitBlindStructureName,
                !blindStructureNameDraft.isBlank());
    }

    private void drawBlindStructureDeleteConfirmation() {
        GdxUiDialogStyle.drawPanel(shapes, 560f, 350f, 800f, 360f,
                CYAN_DARK, 1f);
        textFit(headingFont, uppercase(gameText.translate(
                "gdx.newgame.blind_editor.delete_structure")), 960f, 625f,
                GOLD, true, 700f);
        String name = blindStructureEditor.selected() == null ? ""
                : blindStructureEditor.selected().name();
        textFit(actionFont, uppercase(gameText.translate(
                "blinds.confirmar_borrar", name)), 960f, 535f,
                Color.WHITE, true, 700f);
        themedButton(660f, 405f, 270f, 64f,
                gameText.translate("ui.volver"),
                ButtonTone.NEUTRAL,
                () -> blindStructureDialog = BlindStructureDialog.EDITOR,
                true);
        themedButton(990f, 405f, 270f, 64f,
                gameText.translate("blinds.borrar"),
                ButtonTone.DANGER, () -> {
                    blindStructureEditor.deleteSelected();
                    blindStructureDialog = BlindStructureDialog.EDITOR;
                }, true);
    }

    private void drawPurchasePage() {
        panel(430f, 185f, 670f, 625f, settingsGameText("buyin"));
        panel(1130f, 185f, 725f, 625f, settingsGameText("rebuy"));

        toggle(470f, 625f, 590f,
                settingsGameText("row.fixed_buyin"), table.fixedBuyin(),
                () -> table.setFixedBuyin(!table.fixedBuyin()), !table.economyLocked());
        tooltip(470f, 625f, 590f, GdxSettingsLayout.ROW_HEIGHT,
                "tooltip.cfg.buyin_fixed");
        stepper(470f, 480f, 590f,
                settingsGameText("row.initial_buyin"),
                table.buyin(),
                table.minimumBuyin(), table.maximumBuyin(),
                () -> table.setBuyin(table.buyin() - 1),
                () -> table.setBuyin(table.buyin() + 1),
                table.fixedBuyin() && !table.economyLocked());
        tooltip(470f, 480f, 590f, 102f, "tooltip.cfg.buyin");
        stepper(470f, 315f, 280f,
                settingsGameText("row.minimum_range_bb"),
                table.minBuyinBb(), NewGameTableDraft.MIN_BUYIN_BB,
                NewGameTableDraft.MAX_BUYIN_BB,
                () -> table.setMinBuyinBb(table.minBuyinBb() - 5),
                () -> table.setMinBuyinBb(table.minBuyinBb() + 5),
                !table.economyLocked());
        stepper(780f, 315f, 280f,
                settingsGameText("row.maximum_range_bb"),
                table.maxBuyinBb(), NewGameTableDraft.MIN_BUYIN_BB,
                NewGameTableDraft.MAX_BUYIN_BB,
                () -> table.setMaxBuyinBb(table.maxBuyinBb() - 5),
                () -> table.setMaxBuyinBb(table.maxBuyinBb() + 5),
                !table.economyLocked());
        tooltip(470f, 315f, 590f, 102f, "tooltip.cfg.buyin_range");

        toggle(1170f, 625f, 645f,
                settingsGameText("row.rebuy"), table.rebuy(),
                () -> table.setRebuy(!table.rebuy()), true);
        toggle(1170f, 505f, 645f,
                settingsGameText("row.player_limit"), table.rebuyLimit(),
                () -> table.setRebuyLimit(!table.rebuyLimit()), table.rebuyLimitEnabled());
        tooltip(1170f, 505f, 645f, GdxSettingsLayout.ROW_HEIGHT,
                "tooltip.cfg.rebuy_limit");
        stepper(1170f, 365f, 645f,
                settingsGameText("row.maximum_rebuys"),
                table.rebuyLimitCount(), 1, Integer.MAX_VALUE,
                () -> table.setRebuyLimitCount(table.rebuyLimitCount() - 1),
                () -> table.setRebuyLimitCount(table.rebuyLimitCount() + 1),
                table.rebuyLimitCountEnabled());
        bidirectionalChoice(1170f, 225f, 645f,
                settingsGameText("row.rebuy_cap"),
                table.rebuyCapPolicy() == NewGameTableDraft.RebuyCapPolicy.BUY_IN
                        ? "BUY-IN"
                        : settingsGameText("value.highest_stack"),
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
        tooltip(470f, 625f, 590f, GdxSettingsLayout.ROW_HEIGHT,
                "tooltip.cfg.hand_limit");
        inlineStepper(780f, 625f, 280f,
                table.handLimitCount(), 1, Integer.MAX_VALUE,
                () -> table.setHandLimitCount(table.handLimitCount() - 1),
                () -> table.setHandLimitCount(table.handLimitCount() + 1),
                table.handLimit());
        toggle(470f, 495f, 280f,
                gameText.translate("gdx.settings.game.row.think_time"),
                table.thinkTime(),
                () -> table.setThinkTime(!table.thinkTime()), true);
        tooltip(470f, 495f, 590f, GdxSettingsLayout.ROW_HEIGHT,
                "tooltip.cfg.think_time");
        inlineStepper(780f, 495f, 280f,
                table.thinkSeconds(), 10, 120,
                () -> table.setThinkSeconds(table.thinkSeconds() - 5),
                () -> table.setThinkSeconds(table.thinkSeconds() + 5), table.thinkTime());
        stepper(470f, 325f, 590f,
                gameText.translate("gdx.settings.game.row.showdown_seconds"),
                table.showdownSeconds(), 5, 30,
                () -> table.setShowdownSeconds(table.showdownSeconds() - 5),
                () -> table.setShowdownSeconds(table.showdownSeconds() + 5));
        tooltip(470f, 325f, 590f, 102f, "tooltip.cfg.showdown_time");

        toggle(1170f, 625f, 645f, "IWTSTH", table.iwtsth(),
                () -> table.setIwtsth(!table.iwtsth()), true);
        tooltip(1170f, 625f, 645f, GdxSettingsLayout.ROW_HEIGHT,
                "tooltip.cfg.iwtsth");
        toggle(1170f, 495f, 645f, "RUN IT TWICE", table.runItTwice(),
                () -> table.setRunItTwice(!table.runItTwice()), true);
        tooltip(1170f, 495f, 645f, GdxSettingsLayout.ROW_HEIGHT,
                "tooltip.cfg.rit");
        bidirectionalChoice(1170f, 325f, 645f,
                settingsGameText("row.rabbit_hunting"),
                rabbitText(), this::previousRabbit, this::nextRabbit, true);
        tooltip(1170f, 325f, 645f, 102f, "tooltip.cfg.rabbit");
    }

    private String rabbitText() {
        return switch (table.rabbitHunting()) {
            case FREE -> gameText.translate("menu.free");
            case FREE_SMALL_BLIND -> gameText.translate("menu.free_sb");
            case FREE_SMALL_AND_BIG_BLIND -> gameText.translate("menu.free_sb_bb");
            case OFF -> gameText.translate("menu.off");
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
        tooltip(580f, 610f, 1110f, 102f, "tooltip.cfg.bots");
        toggle(580f, 455f, 1110f,
                gameText.translate("gdx.settings.game.row.bot_rebuy"),
                table.botRebuy(),
                () -> table.setBotRebuy(!table.botRebuy()), table.botRebuyEnabled());
        tooltip(580f, 455f, 1110f, GdxSettingsLayout.ROW_HEIGHT,
                "tooltip.cfg.bot_rebuy");
        toggle(580f, 325f, 1110f,
                gameText.translate("gdx.settings.game.row.bot_balance"),
                table.botBalanceToHumans(),
                () -> table.setBotBalanceToHumans(!table.botBalanceToHumans()), true);
        tooltip(580f, 325f, 1110f, GdxSettingsLayout.ROW_HEIGHT,
                "tooltip.cfg.bot_balance");
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
        boolean joining = connection.mode() == NewGameConnectionDraft.Mode.JOIN;
        float createFooterY = 75f;
        if (!joining) {
            themedButton(55f, createFooterY, 500f, 70f,
                    uppercase(gameText.translate(
                            "gdx.newgame.recover_previous")),
                    ButtonTone.FEATURED,
                    this::continuePreviousGame,
                    !submitting && !connection.recoverLoading());
            tooltip(55f, createFooterY, 500f, 70f,
                    "tooltip.cfg.recover");
        }
        themedButton(joining ? 1040f : 1165f,
                joining ? 165f : createFooterY,
                joining ? 240f : 250f, 70f,
                gameText.translate("ui.cancelar"), ButtonTone.NEUTRAL,
                this::cancelOrReturnToMenu, true);
        themedButton(joining ? 1310f : 1445f,
                joining ? 165f : createFooterY,
                joining ? 315f : 410f, 70f,
                submitting ? uppercase(gameText.translate("gdx.connecting"))
                        : joining
                                ? gameText.translate("game.unirme_a_timba")
                                : gameText.translate("game.crear_timba"),
                ButtonTone.POSITIVE, this::submitNewGame,
                newGameSubmitEnabled(submitting, connection));
    }

    static boolean newGameSubmitEnabled(boolean submitting,
            NewGameConnectionDraft connection) {
        return !submitting && connection != null && connection.canSubmit();
    }

    private void submitNewGame() {
        if (!connection.canSubmit()) {
            showToast(gameText.translate("gdx.newgame.missing_required"));
            return;
        }
        try {
            submissions.submit(connection, table).whenComplete((request, failure) ->
                    Gdx.app.postRunnable(() -> completeSubmission(request, failure)));
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
                showToast(gameText.translate(
                        "gdx.newgame.canceling_connection"));
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

    private String submissionError(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? gameText.translate("gdx.lobby.open_failed") : message;
    }

    private void drawToast() {
        float w = 820f;
        outerBox(WIDTH / 2f - w / 2f, 145f, w, 76f, ORANGE, PANEL_LIGHT);
        textFit(uiFont, toast, WIDTH / 2f, 193f, Color.WHITE, true, w - 52f);
    }

    private void drawScreenshotToastTopLayer() {
        float alpha = MathUtils.clamp(
                (screenshotToastUntil - elapsed) / 0.25f, 0f, 1f);
        float x = 690f;
        float y = 875f;
        float w = 540f;
        float h = 64f;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(CYAN.r, CYAN.g, CYAN.b, 0.90f * alpha);
        roundedRect(x - 2f, y - 2f, w + 4f, h + 4f, 12f);
        shapes.setColor(0.01f, 0.03f, 0.05f, 0.96f * alpha);
        roundedRect(x, y, w, h, 10f);
        shapes.end();
        batch.begin();
        drawFittedCenteredInBox(actionFont, screenshotToast,
                x + 18f, y + 7f, w - 36f, h - 14f, Color.WHITE, alpha);
        batch.end();
    }

    private void drawVolumeOverlayTopLayer() {
        float width = GdxVolumeOverlayStyle.WIDTH;
        float height = GdxVolumeOverlayStyle.HEIGHT;
        float x = (WIDTH - width) / 2f;
        float y = (HEIGHT - height) / 2f;
        float volume = masterVolume();
        boolean outputAvailable = audioOutputAvailable();
        Color accent = outputAvailable && volume > 0f
                ? CYAN : LATENCY_RED;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        // Keep this composition pixel-for-pixel equivalent to the table
        // overlay, including its outlined text, without projecting a shadow.
        shapes.setColor(accent.r, accent.g, accent.b, 0.92f);
        roundedRect(x - 2f, y - 2f, width + 4f, height + 4f, 15f);
        shapes.setColor(0.012f, 0.027f, 0.047f, 0.98f);
        roundedRect(x, y, width, height, 13f);
        float barX = x + GdxVolumeOverlayStyle.BAR_X_OFFSET;
        float barY = y + GdxVolumeOverlayStyle.BAR_Y_OFFSET;
        float barW = width - GdxVolumeOverlayStyle.BAR_RIGHT_INSET;
        shapes.setColor(0.15f, 0.20f, 0.28f, 1f);
        roundedRect(barX, barY, barW, GdxVolumeOverlayStyle.BAR_HEIGHT, 7f);
        if (volume > 0f) {
            shapes.setColor(accent);
            roundedRect(barX, barY, barW * volume,
                    GdxVolumeOverlayStyle.BAR_HEIGHT, 7f);
        }
        shapes.end();

        batch.begin();
        batch.setColor(outputAvailable ? Color.WHITE : AUDIO_UNAVAILABLE_RED);
        batch.draw(outputAvailable && volume > 0f ? soundIcon : muteIcon,
                x + 22f, y + 21f, 58f, 58f);
        String label = Math.round(volume * 100f) + "%";
        drawFittedCenteredInBox(volumeOverlayFont, label, barX, barY, barW,
                GdxVolumeOverlayStyle.BAR_HEIGHT, Color.WHITE, 1f);
        batch.end();
    }

    private void drawFittedCenteredInBox(BitmapFont font, String text,
            float x, float y, float width, float height,
            Color color, float alpha) {
        BitmapFont.BitmapFontData data = font.getData();
        float originalScaleX = data.scaleX;
        float originalScaleY = data.scaleY;
        font.setColor(color.r, color.g, color.b, alpha);
        glyph.setText(font, text);
        float fit = CoronaPokerGdxTable.fittedSingleLineScale(glyph.width,
                glyph.height, width, height);
        if (fit < 1f) {
            data.setScale(originalScaleX * fit, originalScaleY * fit);
            glyph.setText(font, text);
        }
        font.draw(batch, glyph, x + (width - glyph.width) / 2f,
                y + (height + glyph.height) / 2f);
        font.setColor(Color.WHITE);
        data.setScale(originalScaleX, originalScaleY);
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

    /** Waiting-room columns use one uninterrupted glass fill. */
    private void lobbyPanel(float x, float y, float w, float h,
            String title) {
        outerBox(x, y, w, h, LINE, PANEL);
        shapes.setColor(new Color(0x36d9ff70));
        shapes.rect(x + 18f, y + h - 8f, w - 36f, 2f);
        if (!title.isBlank()) {
            textFit(headingFont, title, x + 34f, y + h - 29f, GOLD,
                    false, Math.max(0f, w - 68f));
            shapes.setColor(new Color(0x31445f90));
            shapes.rect(x + 30f, y + h - 71f, w - 60f, 1f);
        }
    }

    /** Shared glass panel without content bands or a title/header region. */
    private void plainPanel(float x, float y, float w, float h) {
        outerBox(x, y, w, h, LINE, PANEL);
        shapes.setColor(new Color(0x36d9ff70));
        shapes.rect(x + 18f, y + h - 8f, w - 36f, 2f);
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
        boolean revealed = secret && id.equals(revealedPasswordField);
        textFit(smallFont, label, x, y + 98f, MUTED, false,
                Math.max(0f, w));
        outerBox(x, y, w, 70f, focused || hovered(x, y, w, 70f) ? CYAN : LINE,
                pressed(x, y, w, 70f) ? new Color(0x0b1424ff) : PANEL_LIGHT);
        String visible = passwordDisplay(value, secret, revealed);
        float textWidth = w - (secret ? 102f : 44f);
        FrontendInputWindow window = visible.isEmpty()
                ? new FrontendInputWindow("—", 0, 0, 0f, 0f, 0f)
                : frontendInputWindow(uiFont, value, visible, textWidth,
                        focused);
        drawInputSelection(x + 22f, y + 17f, 36f, window, focused);
        text(uiFont, window.text(), x + 22f, y + 44f,
                visible.isEmpty() ? DISABLED : Color.WHITE, false);
        drawInputCaret(x + 22f + window.caretOffset(), y + 17f, 36f,
                focused);
        if (secret) {
            Rectangle revealBounds = passwordRevealBounds(x, y, w);
            drawPasswordRevealButton(revealBounds, revealed);
            passwordRevealHits.add(new PasswordRevealHit(id, revealBounds));
            tooltip(revealBounds.x, revealBounds.y, revealBounds.width,
                    revealBounds.height, "auth.mostrar_password_pulsar");
        }
        textFieldHits.add(new TextFieldHit(id, new Rectangle(x, y, w, 70f)));
        hit(x, y, w, 70f, () -> activateField(id));
    }

    private void drawPasswordRevealButton(Rectangle bounds,
            boolean revealed) {
        boolean over = hovered(bounds.x, bounds.y,
                bounds.width, bounds.height);
        Color color = revealed ? GOLD : over ? CYAN : MUTED;
        shapes.setColor(new Color(0x07111fff));
        roundedRect(bounds.x, bounds.y, bounds.width, bounds.height, 8f);
        shapes.setColor(color);
        shapes.ellipse(bounds.x + 7f, bounds.y + 14f,
                bounds.width - 14f, 18f);
        shapes.setColor(new Color(0x07111fff));
        shapes.ellipse(bounds.x + 10f, bounds.y + 17f,
                bounds.width - 20f, 12f);
        shapes.setColor(color);
        shapes.circle(bounds.x + bounds.width / 2f,
                bounds.y + bounds.height / 2f, revealed ? 6f : 5f, 24);
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
        outerBox(x, y, w, editMenu.bounds.height,
                GdxEditMenuStyle.BORDER, GdxEditMenuStyle.BACKGROUND);
        editMenuItem(x + 8f, y + 152f, w - 16f, row,
                uppercase(gameText.translate("ui.cortar")), selected, () -> {
                    String current = activeValue();
                    copyActiveSelection(current);
                    setActiveValue(textEdit.delete(current));
                });
        editMenuItem(x + 8f, y + 104f, w - 16f, row,
                uppercase(gameText.translate("ui.copiar")), selected,
                () -> copyActiveSelection(activeValue()));
        editMenuItem(x + 8f, y + 56f, w - 16f, row,
                uppercase(gameText.translate("ui.pegar")),
                clipboard != null && !clipboard.isEmpty(),
                () -> replaceActiveSelection(
                        Objects.requireNonNullElse(
                                Gdx.app.getClipboard().getContents(), "")));
        editMenuItem(x + 8f, y + 8f, w - 16f, row,
                uppercase(gameText.translate("ui.seleccionar_todo")),
                !value.isEmpty(),
                () -> textEdit.selectAll(activeValue()));
    }

    private void editMenuItem(float x, float y, float w, float h,
            String label, boolean enabled, Runnable action) {
        boolean over = enabled && hovered(x, y, w, h);
        shapes.setColor(over ? GdxEditMenuStyle.ROW_HOVER
                : GdxEditMenuStyle.ROW);
        roundedRect(x, y, w, h - 2f, 6f);
        textFit(smallFont, label, x + 18f, y + 30f,
                enabled ? Color.WHITE : DISABLED, false,
                Math.max(0f, w - 36f));
        if (enabled) {
            editMenuHits.add(new Hit(new Rectangle(x, y, w, h), () -> {
                action.run();
                editMenu = null;
            }, false));
        }
    }

    private boolean shouldDrawEditMenu() {
        return editMenu != null && (!hasBlockingFrontendModal()
                || surface == Surface.STATS && statsSyncExclusionsOpen);
    }

    private void drawEditMenuTopLayer() {
        int firstMenuText = texts.size();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA,
                GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawEditMenu();
        shapes.end();
        batch.begin();
        for (int index = firstMenuText; index < texts.size(); index++) {
            drawTextItem(texts.get(index));
        }
        batch.end();
    }

    private float textWidth(BitmapFont font, String value) {
        glyph.setText(font, value);
        return glyph.width;
    }

    private void tooltip(float x, float y, float w, float h, String key,
            Object... arguments) {
        tooltipHits.add(new TooltipHit(new Rectangle(x, y, w, h), key,
                arguments == null ? new Object[0] : arguments.clone(),
                GdxSettingsContract.PerformanceImpact.NONE));
    }

    private void performanceTooltip(Rectangle bounds, String settingKey) {
        GdxSettingsContract.PerformanceImpact impact =
                GdxSettingsContract.performanceImpact(settingKey,
                        initialProperties);
        if (impact == GdxSettingsContract.PerformanceImpact.NONE) return;
        tooltipHits.add(new TooltipHit(new Rectangle(bounds),
                "gdx.settings.performance_impact",
                new Object[]{impact.label(gameText)}, impact));
    }

    private void drawTooltipTopLayer() {
        TooltipHit hovered = null;
        for (int index = tooltipHits.size() - 1; index >= 0; index--) {
            if (tooltipHits.get(index).bounds.contains(pointer)) {
                hovered = tooltipHits.get(index);
                break;
            }
        }
        if (hovered == null) {
            tooltipDelay.clear();
            return;
        }
        if (!tooltipDelay.ready(hovered.identity())) return;
        String value = gameText.translate(hovered.key, hovered.arguments);
        List<String> lines = wrapText(tinyFont, value, 430f, 5);
        float widest = 0f;
        for (String line : lines) widest = Math.max(widest,
                textWidth(tinyFont, line));
        float boxW = Math.min(470f, Math.max(190f, widest + 34f));
        float boxH = 24f + lines.size() * 22f;
        float x = MathUtils.clamp(pointer.x + 18f, 12f, WIDTH - boxW - 12f);
        float y = pointer.y - boxH - 18f;
        if (y < 12f) y = Math.min(HEIGHT - boxH - 12f, pointer.y + 22f);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Color accent = performanceImpactColor(hovered.performanceImpact);
        outerBox(x, y, boxW, boxH,
                hovered.performanceImpact
                        == GdxSettingsContract.PerformanceImpact.NONE
                                ? CYAN_DARK : accent,
                new Color(0x071221f8));
        shapes.end();
        batch.begin();
        tinyFont.setColor(hovered.performanceImpact
                == GdxSettingsContract.PerformanceImpact.NONE
                        ? Color.WHITE : accent);
        float baseline = y + boxH - 16f;
        for (String line : lines) {
            tinyFont.draw(batch, line, x + 17f, baseline);
            baseline -= 22f;
        }
        batch.end();
    }

    private static Color performanceImpactColor(
            GdxSettingsContract.PerformanceImpact impact) {
        return switch (impact) {
            case LOW -> new Color(0x4caf50ff);
            case MEDIUM -> new Color(0xffc107ff);
            case HIGH -> new Color(0xf44336ff);
            case NONE -> CYAN_DARK;
        };
    }

    private void toggle(float x, float y, float w, String label,
            boolean value, Runnable action, boolean enabled) {
        toggle(x, y, w, label, value, action, enabled, smallFont);
    }

    private void compactToggle(float x, float y, float w, String label,
            boolean value, Runnable action, boolean enabled) {
        toggle(x, y, w, label, value, action, enabled, tinyFont, true);
    }

    private void toggle(float x, float y, float w, String label,
            boolean value, Runnable action, boolean enabled,
            BitmapFont labelFont) {
        toggle(x, y, w, label, value, action, enabled, labelFont, false);
    }

    private void toggle(float x, float y, float w, String label,
            boolean value, Runnable action, boolean enabled,
            BitmapFont labelFont, boolean wrapLabel) {
        GdxSettingsStyle.drawRow(shapes, x, y, w,
                GdxSettingsLayout.ROW_HEIGHT, enabled,
                enabled && hovered(x, y, w, GdxSettingsLayout.ROW_HEIGHT),
                enabled && pressed(x, y, w, GdxSettingsLayout.ROW_HEIGHT),
                1f);
        float labelWidth = Math.max(0f, w - 132f);
        if (wrapLabel) {
            List<String> lines = wrapText(labelFont, label, labelWidth, 2);
            float baseline = lines.size() > 1 ? y + 48f : y + 42f;
            for (String line : lines) {
                textFit(labelFont, line, x + 22f, baseline,
                        enabled ? Color.WHITE : DISABLED, false, labelWidth);
                baseline -= 24f;
            }
        } else {
            textFit(labelFont, label, x + 22f, y + 43f,
                    enabled ? Color.WHITE : DISABLED, false, labelWidth);
        }
        float target = value && enabled ? 1f : 0f;
        String animationKey = GdxToggleMotion.stableKey(
                toggleAnimationContext(), label, x, y, w, wrapLabel);
        float animation = toggleAnimations.getOrDefault(animationKey, target);
        animation = GdxToggleMotion.next(animation, target, frameDelta);
        toggleAnimations.put(animationKey, animation);
        GdxSettingsStyle.drawToggle(shapes, x, y, w, animation, 1f);
        if (enabled) hit(x, y, w, GdxSettingsLayout.ROW_HEIGHT,
                () -> GdxToggleSoundAction.run(value, action,
                        this::playFrontendSwitchSound));
    }

    private String toggleAnimationContext() {
        if (surface == Surface.SETTINGS) {
            return surface.name() + ':' + settingsReturnSurface.name() + ':'
                    + settingsSession.section().name() + ':'
                    + settingsSubpageIndex();
        }
        if (surface == Surface.NEW_GAME) {
            return surface.name() + ':' + page;
        }
        return surface.name();
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
        embeddedButtonSurface(x, y, side + 3f, 72f, enabled);
        embeddedButtonSurface(x + w - side - 3f, y, side + 3f, 72f,
                enabled);
        text(headingFont, "-", x + 38f, y + 48f, enabled ? Color.WHITE : DISABLED, true);
        text(headingFont, "+", x + w - 38f, y + 48f, enabled ? Color.WHITE : DISABLED, true);
        text(headingFont, Integer.toString(value), x + w / 2f, y + 49f,
                enabled ? GOLD : DISABLED, true);
        if (enabled) {
            repeatHit(x, y, side + 6f, 72f, minus);
            repeatHit(x + w - side - 6f, y, side + 6f, 72f, plus);
        }
    }

    /**
     * Counter paired horizontally with a labelled toggle. The toggle owns the
     * row label, so this control must not add a second label above itself or
     * use a different height. Keeping both halves on the shared settings row
     * geometry prevents the visibly staggered controls previously present in
     * Nueva Timba.
     */
    private void inlineStepper(float x, float y, float w, int value,
            int min, int max, Runnable minus, Runnable plus,
            boolean enabled) {
        float height = GdxSettingsLayout.ROW_HEIGHT;
        Color border = GdxSettingsStyle.rowBorder(enabled,
                enabled && hovered(x, y, w, height));
        Color fill = GdxSettingsStyle.rowFill(enabled,
                enabled && pressed(x, y, w, height));
        outerBox(x, y, w, height, border, fill);
        float side = 66f;
        embeddedButtonSurface(x, y, side + 3f, height, enabled);
        embeddedButtonSurface(x + w - side - 3f, y,
                side + 3f, height, enabled);
        textFit(headingFont, "-", x + 3f + side / 2f,
                y + 45f, enabled ? Color.WHITE : DISABLED,
                true, side - 18f);
        textFit(headingFont, "+", x + w - 3f - side / 2f,
                y + 45f, enabled ? Color.WHITE : DISABLED,
                true, side - 18f);
        textFit(headingFont, Integer.toString(value), x + w / 2f,
                y + 46f, enabled ? GOLD : DISABLED, true,
                Math.max(0f, w - side * 2f - 24f));
        if (enabled) {
            repeatHit(x, y, side + 6f, height, minus);
            repeatHit(x + w - side - 6f, y, side + 6f, height, plus);
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
        Color border = GdxSettingsStyle.rowBorder(enabled,
                enabled && hovered(x, y, w, h));
        Color fill = GdxSettingsStyle.rowFill(enabled,
                enabled && pressed(x, y, w, h));
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

    private void dropdownChoice(float x, float y, float w, String label,
            String value, Runnable action, boolean enabled) {
        float h = 72f;
        textFit(smallFont, label, x, y + 99f,
                enabled ? MUTED : DISABLED, false, Math.max(0f, w));
        Color border = GdxSettingsStyle.rowBorder(enabled,
                enabled && hovered(x, y, w, h));
        Color fill = GdxSettingsStyle.rowFill(enabled,
                enabled && pressed(x, y, w, h));
        outerBox(x, y, w, h, border, fill);
        textFit(uiFont, value, x + 22f, y + 46f,
                enabled ? Color.WHITE : DISABLED, false, w - 92f);
        shapes.setColor(enabled ? GOLD : DISABLED);
        float cx = x + w - 38f;
        float cy = y + 39f;
        shapes.rectLine(cx - 12f, cy + 6f, cx, cy - 6f, 3f);
        shapes.rectLine(cx, cy - 6f, cx + 12f, cy + 6f, 3f);
        if (enabled) hit(x, y, w, h, action);
    }

    private void openDropdown(Dropdown target) {
        dropdown = Objects.requireNonNull(target, "target");
        int selected = switch (target) {
            case PROFILE -> selectedGamePreset + 1;
            case BLIND_STRUCTURE -> selectedBlindStructureOption();
            case HOST_ADDRESS -> selectedHostAddressOption();
            default -> 0;
        };
        int count = dropdownOptions(target).size();
        dropdownScroll = MathUtils.clamp(selected - 2, 0,
                Math.max(0, count - 6));
        clearActiveField();
    }

    private int selectedBlindStructureOption() {
        if (table.structureName() == null) return 0;
        List<BlindStructureCatalog.Entry> saved = BlindStructureCatalog.read(
                initialProperties);
        for (int index = 0; index < saved.size(); index++) {
            if (saved.get(index).name().equals(table.structureName())) {
                return index + 1;
            }
        }
        return 0;
    }

    private int selectedHostAddressOption() {
        int selected = hostAddressOptions.indexOf(connection.server());
        return Math.max(0, selected);
    }

    private void refreshHostAddressOptions() {
        int generation = ++hostAddressLoadGeneration;
        NewGameConnectionDraft target = connection;
        hostAddressOptions = List.of(GdxLocalServerAddresses.LOOPBACK_NAME);
        target.setServer(GdxLocalServerAddresses.LOOPBACK_NAME);
        CompletableFuture.supplyAsync(GdxLocalServerAddresses::discover,
                networkInfoExecutor).whenComplete((addresses, failure) -> {
                    if (failure != null) {
                        LOGGER.log(Level.FINE,
                                "Unable to enumerate local server addresses",
                                failure);
                    }
                    Gdx.app.postRunnable(() -> {
                        if (disposed || generation != hostAddressLoadGeneration
                                || connection != target) {
                            return;
                        }
                        hostAddressOptions = failure == null
                                && addresses != null && !addresses.isEmpty()
                                ? addresses
                                : List.of(GdxLocalServerAddresses.LOOPBACK_NAME);
                    });
                });
    }

    private List<String> dropdownOptions(Dropdown target) {
        if (target == Dropdown.HOST_ADDRESS) {
            return hostAddressOptions;
        }
        List<String> values = new ArrayList<>();
        values.add(target == Dropdown.PROFILE
                ? gameText.translate("newgame.preset_por_defecto")
                : gameText.translate("gdx.settings.value.default"));
        if (target == Dropdown.PROFILE) {
            gamePresets.forEach(entry -> values.add(entry.name()));
        } else if (target == Dropdown.BLIND_STRUCTURE) {
            BlindStructureCatalog.read(initialProperties)
                    .forEach(entry -> values.add(entry.name()));
        }
        return List.copyOf(values);
    }

    private void drawDropdownOverlay() {
        List<String> options = dropdownOptions(dropdown);
        int visible = Math.min(6, options.size());
        dropdownScroll = MathUtils.clamp(dropdownScroll, 0,
                Math.max(0, options.size() - visible));
        float x = switch (dropdown) {
            case PROFILE -> 500f;
            case HOST_ADDRESS -> 1170f;
            default -> 470f;
        };
        float w = switch (dropdown) {
            case PROFILE -> 1285f;
            case HOST_ADDRESS -> 430f;
            default -> 590f;
        };
        float top = switch (dropdown) {
            case PROFILE -> 570f;
            case HOST_ADDRESS -> 700f;
            default -> 680f;
        };
        float rowH = 56f;
        float y = top - visible * rowH;
        hit(0f, 0f, WIDTH, HEIGHT, () -> dropdown = Dropdown.NONE);
        outerBox(x, y, w, visible * rowH, CYAN_DARK,
                new Color(0x071221ff));
        int selected = switch (dropdown) {
            case PROFILE -> selectedGamePreset + 1;
            case HOST_ADDRESS -> selectedHostAddressOption();
            default -> selectedBlindStructureOption();
        };
        for (int row = 0; row < visible; row++) {
            int option = dropdownScroll + row;
            float rowY = top - (row + 1) * rowH;
            boolean active = option == selected;
            boolean over = hovered(x + 6f, rowY + 3f, w - 12f, rowH - 6f);
            shapes.setColor(active ? new Color(0x153a52ff)
                    : over ? new Color(0x10283cff)
                            : new Color(0x091624ff));
            roundedRect(x + 6f, rowY + 3f, w - 12f, rowH - 6f, 6f);
            if (active) {
                shapes.setColor(GOLD);
                roundedRect(x + 12f, rowY + 12f, 4f, rowH - 24f, 2f);
            }
            textFit(smallFont, options.get(option), x + 28f, rowY + 36f,
                    active ? GOLD : Color.WHITE, false, w - 56f);
            int selectedOption = option;
            hit(x + 6f, rowY + 3f, w - 12f, rowH - 6f, () -> {
                switch (dropdown) {
                    case PROFILE -> selectGamePresetOption(selectedOption);
                    case HOST_ADDRESS -> {
                        connection.setServer(options.get(selectedOption));
                        dropdown = Dropdown.NONE;
                    }
                    default -> selectBlindStructureOption(selectedOption);
                }
            });
        }
        if (options.size() > visible) {
            textFit(tinyFont, (dropdownScroll + 1) + "–"
                    + (dropdownScroll + visible) + " / " + options.size(),
                    x + w - 18f, y - 12f, MUTED, true, 130f);
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
        embeddedButtonSurface(x, y, side + 3f, h, enabled);
        embeddedButtonSurface(x + w - side - 3f, y,
                side + 3f, h, enabled);

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
        textFit(smallFont, value, x + 34f, y + 10f, MUTED, false, 776f);
    }

    private void summaryRow(float x, float y, String label, String value) {
        shapes.setColor(new Color(0x31445f77));
        shapes.rect(x, y - 18f, 810f, 1f);
        textFit(smallFont, label, x, y + 18f, MUTED, false, 500f);
        textFit(uiFont, value, x + 790f, y + 18f, Color.WHITE, true, 280f);
    }

    private void embeddedButtonSurface(float x, float y, float w, float h,
            boolean enabled) {
        boolean hover = enabled && hovered(x, y, w, h);
        GdxUiButtonStyle.draw(shapes, x, y, w, h,
                GdxUiButtonStyle.Tone.NEUTRAL, enabled,
                hover ? 1f : 0f, enabled && pressed(x, y, w, h),
                1f);
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

    private void compactButton(float x, float y, float w, float h,
            String label, boolean primary, Runnable action) {
        compactButton(x, y, w, h, label, primary, action, true);
    }

    private void compactButton(float x, float y, float w, float h,
            String label, boolean primary, Runnable action, boolean enabled) {
        themedButton(x, y, w, h, label,
                primary ? ButtonTone.FEATURED : ButtonTone.NEUTRAL,
                action, enabled, smallFont);
    }

    private void lobbyBotButton(float x, float y, float w, float h,
            String label, Runnable action, boolean enabled) {
        themedButton(x, y, w, h, label, ButtonTone.NEUTRAL,
                action, enabled);
        boolean hover = enabled && hovered(x, y, w, h);
        Color iconColor = enabled ? (hover ? CYAN : GOLD) : DISABLED;
        drawBotIcon(x + 38f, y + h / 2f, iconColor,
                new Color(0x0b1424ff));
        Color divider = new Color(iconColor);
        divider.a = enabled ? 0.28f : 0.18f;
        shapes.setColor(divider);
        shapes.rect(x + 76f, y + 14f, 2f, h - 28f);
    }

    private void lobbyPasswordButton(float x, float y, float w, float h,
            String label, boolean passwordEnabled, Runnable action,
            boolean enabled) {
        if (!passwordEnabled) {
            button(x, y, w, h, label, false, action, enabled);
            return;
        }
        themedButton(x, y, w, h, "", ButtonTone.NEUTRAL, action, enabled);
        boolean hover = enabled && hovered(x, y, w, h);
        Color iconColor = enabled ? (hover ? CYAN : GOLD) : DISABLED;
        float centerX = x + 31f;
        float centerY = y + h / 2f;
        drawPasswordLock(centerX, centerY, iconColor);
        Color divider = new Color(iconColor);
        divider.a = enabled ? 0.28f : 0.18f;
        shapes.setColor(divider);
        shapes.rect(x + 58f, y + 10f, 2f, h - 20f);
        textFit(actionFont, label, x + 72f, centerY + 8f,
                GdxUiButtonStyle.labelColor(
                        GdxUiButtonStyle.Tone.NEUTRAL, enabled),
                false, w - 84f);
    }

    private void drawPasswordLock(float centerX, float centerY,
            Color color) {
        shapes.setColor(color);
        shapes.rect(centerX - 8f, centerY + 2f, 3f, 9f);
        shapes.rect(centerX + 5f, centerY + 2f, 3f, 9f);
        roundedRect(centerX - 8f, centerY + 8f, 16f, 5f, 2f);
        roundedRect(centerX - 11f, centerY - 10f, 22f, 15f, 3f);
        shapes.setColor(new Color(0x07111fff));
        shapes.circle(centerX, centerY - 3f, 2.5f, 16);
        shapes.rect(centerX - 1.25f, centerY - 8f, 2.5f, 5f);
    }

    private void themedButton(float x, float y, float w, float h,
            String label, ButtonTone tone, Runnable action, boolean enabled) {
        themedButton(x, y, w, h, label, tone, action, enabled, actionFont);
    }

    private void themedButton(float x, float y, float w, float h,
            String label, ButtonTone tone, Runnable action, boolean enabled,
            BitmapFont labelFont) {
        boolean hover = enabled && hovered(x, y, w, h);
        float hoverTarget = hover ? 1f : 0f;
        String hoverKey = buttonHoverKey(label, x, y, w, h);
        float hoverAmount = hoverAnimations.getOrDefault(hoverKey, hoverTarget);
        hoverAmount += (hoverTarget - hoverAmount)
                * Math.min(1f, frameDelta * 13f);
        hoverAnimations.put(hoverKey, hoverAmount);
        boolean down = enabled && pressed(x, y, w, h);
        GdxUiButtonStyle.Tone sharedTone = GdxUiButtonStyle.Tone.valueOf(
                tone.name());
        GdxUiButtonStyle.draw(shapes, x, y, w, h, sharedTone, enabled,
                hoverAmount, down, 1f);
        Color labelColor = GdxUiButtonStyle.labelColor(sharedTone, enabled);
        textFit(labelFont, label, x + w / 2f, y + h / 2f + 8f,
                labelColor, true, w - 30f);
        if (enabled) {
            if ("-".equals(label) || "+".equals(label)) {
                repeatHit(x, y, w, h, action);
            } else {
                hit(x, y, w, h, action);
            }
        }
    }

    static String buttonHoverKey(String label, float x, float y, float w,
            float h) {
        return Objects.requireNonNullElse(label, "") + '@'
                + Float.floatToIntBits(x) + ':' + Float.floatToIntBits(y)
                + ':' + Float.floatToIntBits(w) + ':'
                + Float.floatToIntBits(h);
    }

    private void mainMenuButton(float x, float y, float w, float h,
            String label, int icon, boolean primary, Runnable action) {
        mainMenuButton(x, y, w, h, label, icon,
                primary ? ButtonTone.FEATURED : ButtonTone.NEUTRAL, action);
    }

    private void mainMenuButton(float x, float y, float w, float h,
            String label, int icon, ButtonTone tone, Runnable action) {
        themedButton(x, y, w, h, label, tone, action, true);
        drawButtonIconColumn(x, y, w, h, icon, tone, true);
    }

    private void iconButton(float x, float y, float w, float h,
            String label, int icon, ButtonTone tone, Runnable action,
            boolean enabled) {
        // The icon owns a fixed leading column. Drawing the normal centered
        // label underneath it made long lobby labels collide with the camera.
        themedButton(x, y, w, h, "", tone, action, enabled);
        drawButtonIconColumn(x, y, w, h, icon, tone, enabled);
        float labelX = x + 84f;
        float labelW = w - 84f;
        Color labelColor = GdxUiButtonStyle.labelColor(
                GdxUiButtonStyle.Tone.valueOf(tone.name()), enabled);
        textFit(actionFont, label, labelX + labelW / 2f,
                y + h / 2f + 8f, labelColor, true,
                Math.max(0f, labelW - 30f));
    }

    private void drawButtonIconColumn(float x, float y, float w, float h,
            int icon, ButtonTone tone, boolean enabled) {
        boolean hover = enabled && hovered(x, y, w, h);
        Color color;
        if (!enabled) {
            color = DISABLED;
        } else if (tone == ButtonTone.POSITIVE) {
            color = hover ? Color.WHITE : POSITIVE_ICON;
        } else if (tone == ButtonTone.DANGER) {
            color = Color.WHITE;
        } else {
            color = hover ? CYAN : GOLD;
        }
        float iconX = x + 43f;
        float iconY = y + h / 2f;
        shapes.setColor(color);
        drawMainMenuIcon(icon, iconX, iconY, color);
        Color divider = new Color(color);
        divider.a = !enabled ? 0.18f : tone == ButtonTone.NEUTRAL
                ? 0.24f + (hover ? 0.20f : 0f) : 0.34f;
        shapes.setColor(divider);
        shapes.rect(x + 82f, y + 17f, 2f, h - 34f);
    }

    private void drawMainMenuIcon(int icon, float cx, float cy, Color color) {
        switch (icon) {
            case 0 -> {
                // New game.
                roundedRect(cx - 5.5f, cy - 26f, 11f, 52f, 4.5f);
                roundedRect(cx - 26f, cy - 5.5f, 52f, 11f, 4.5f);
            }
            case 1 -> {
                // Join a table: a distinct group of players. Keep it
                // geometric like the rest of the menu glyphs so it remains
                // crisp at every viewport scale.
                shapes.circle(cx, cy + 11f, 8f, 28);
                shapes.circle(cx - 17f, cy + 7f, 6.5f, 24);
                shapes.circle(cx + 17f, cy + 7f, 6.5f, 24);
                roundedRect(cx - 12f, cy - 20f, 24f, 24f, 8f);
                roundedRect(cx - 27f, cy - 18f, 18f, 19f, 7f);
                roundedRect(cx + 9f, cy - 18f, 18f, 19f, 7f);
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
            case 6 -> {
                // Screenshot viewer camera.
                roundedRect(cx - 25f, cy - 17f, 50f, 34f, 7f);
                roundedRect(cx - 11f, cy + 16f, 22f, 7f, 3f);
                Color cutout = new Color(0x0b1729ff);
                shapes.setColor(cutout);
                shapes.circle(cx, cy, 10f, 32);
                shapes.setColor(color);
                shapes.circle(cx, cy, 4.5f, 24);
            }
            default -> {
            }
        }
    }

    private void backButton(float x, float y, float w, float h,
            Runnable action) {
        boolean hover = hovered(x, y, w, h);
        GdxUiButtonStyle.draw(shapes, x, y, w, h,
                GdxUiButtonStyle.Tone.NEUTRAL, true,
                hover ? 1f : 0f, pressed(x, y, w, h), 1f);
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
            case 4 -> {
                drawBotIcon(cx, cy, color, cutout);
            }
            default -> {
                // Complete-table preset: a compact stack of saved sheets.
                roundedRect(cx - 16f, cy - 17f, 30f, 34f, 3f);
                shapes.setColor(cutout);
                roundedRect(cx - 11f, cy - 12f, 20f, 24f, 2f);
                shapes.setColor(color);
                shapes.rect(cx - 6f, cy + 5f, 12f, 2.5f);
                shapes.rect(cx - 6f, cy, 12f, 2.5f);
                shapes.rect(cx - 6f, cy - 5f, 9f, 2.5f);
            }
        }
    }

    private void drawBotIcon(float cx, float cy, Color color, Color cutout) {
        shapes.setColor(color);
        roundedRect(cx - 18f, cy - 14f, 36f, 28f, 6f);
        shapes.rect(cx - 3f, cy + 14f, 6f, 8f);
        shapes.circle(cx, cy + 23f, 3.5f, 16);
        shapes.setColor(cutout);
        shapes.circle(cx - 8f, cy, 3.5f, 16);
        shapes.circle(cx + 8f, cy, 3.5f, 16);
        shapes.rect(cx - 9f, cy - 8f, 18f, 3f);
    }

    private void drawAvatarIcon(float cx, float cy) {
        drawAvatarIcon(cx, cy, 122f);
    }

    private void drawAvatarIcon(float cx, float cy, float size) {
        Texture avatar = selectedAvatarTexture == null
                ? avatarDefault : selectedAvatarTexture;
        lobbyAvatars.add(new LobbyAvatarItem(avatar,
                cx - size / 2f, cy - size / 2f, size));
    }

    private void selectAvatar() {
        if (avatarSelectionPending || connection == null) return;
        avatarSelectionPending = true;
        NewGameConnectionDraft target = connection;
        String title = gameText.translate("gdx.avatar.select");
        CompletableFuture.supplyAsync(() -> openAvatarFileDialog(title),
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
            showToast(gameText.translate("gdx.avatar.select_failed"));
            LOGGER.log(Level.WARNING, "GDX avatar selector failed", cause);
            return;
        }
        if (selected == null) return;
        if (!isSupportedAvatar(selected) || !connection.setAvatar(selected)) {
            showToast(gameText.translate("gdx.avatar.invalid"));
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
        try {
            AvatarImageValidator.validate(path);
            return true;
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    private static Path openAvatarFileDialog(String title) {
        FileDialog dialog = new FileDialog((Frame) null,
                title, FileDialog.LOAD);
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
        outerBox(x, y, w, h, border, fill, true);
    }

    private void flatOuterBox(float x, float y, float w, float h,
            Color border, Color fill) {
        outerBox(x, y, w, h, border, fill, false);
    }

    private void outerBox(float x, float y, float w, float h, Color border,
            Color fill, boolean sheen) {
        shapes.setColor(fill);
        roundedRect(x, y, w, h, 14f);
        shapes.setColor(border);
        roundedRectOutline(x + 1f, y + 1f, w - 2f, h - 2f,
                13f, 2f);
        if (sheen && h <= 90f && w > 90f) {
            float inset = 14f;
            float sheenBottom = y + h * 0.54f;
            float sheenTop = y + h - 9f;
            shapes.rect(x + inset, sheenBottom, w - inset * 2f,
                    sheenTop - sheenBottom,
                    BOX_SHEEN_BOTTOM, BOX_SHEEN_BOTTOM,
                    BOX_SHEEN_TOP, BOX_SHEEN_TOP);
            shapes.rect(x + inset, y + 8f, w - inset * 2f, h * 0.18f,
                    BOX_SHADE_BOTTOM, BOX_SHADE_BOTTOM,
                    BOX_SHADE_TOP, BOX_SHADE_TOP);
        }
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
        (settingsRowsActive ? settingsRowTexts : texts).add(new TextItem(font,
                value, x, y, new Color(color), centered, false));
    }

    private void textFit(BitmapFont preferred, String value, float x, float y,
            Color color, boolean centered, float maxWidth) {
        (settingsRowsActive ? settingsRowTexts : texts).add(fittedTextItem(
                preferred, value, x, y, color, centered, maxWidth, false));
    }

    /** Fits table headers by scaling them down, never by truncating them. */
    private void textExactFit(BitmapFont font, String value, float x, float y,
            Color color, boolean centered, float maxWidth) {
        float width = Math.max(1f, textWidth(font, value));
        float scale = Math.min(1f, maxWidth / width);
        (settingsRowsActive ? settingsRowTexts : texts).add(new TextItem(font,
                value, x, y, new Color(color), centered, false, scale));
    }

    private void beginStatsChartCanvas(float x, float y, float width,
            float height) {
        statsChartViewport.set(x, y, width, height);
        shapes.flush();
        enableWorldScissor(statsChartViewport);
        statsChartCanvasActive = true;
    }

    private void endStatsChartCanvas() {
        shapes.flush();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        statsChartCanvasActive = false;
        drawStatsChartScrollbars();
    }

    private void drawStatsChartScrollbars() {
        if (statsChartZoom <= 1f || statsChartViewport.width <= 0f
                || statsChartViewport.height <= 0f) {
            statsChartHorizontalTrack.set(0f, 0f, 0f, 0f);
            statsChartVerticalTrack.set(0f, 0f, 0f, 0f);
            return;
        }
        statsChartHorizontalTrack.set(statsChartViewport.x + 8f,
                statsChartViewport.y + 3f,
                statsChartViewport.width - 27f, 8f);
        statsChartVerticalTrack.set(statsChartViewport.x
                + statsChartViewport.width - 11f,
                statsChartViewport.y + 16f, 8f,
                statsChartViewport.height - 24f);
        shapes.setColor(new Color(0x18304cff));
        roundedRect(statsChartHorizontalTrack.x,
                statsChartHorizontalTrack.y,
                statsChartHorizontalTrack.width,
                statsChartHorizontalTrack.height, 4f);
        roundedRect(statsChartVerticalTrack.x, statsChartVerticalTrack.y,
                statsChartVerticalTrack.width,
                statsChartVerticalTrack.height, 4f);
        statsChartHorizontalThumbWidth = Math.max(34f,
                statsChartHorizontalTrack.width / statsChartZoom);
        statsChartVerticalThumbHeight = Math.max(34f,
                statsChartVerticalTrack.height / statsChartZoom);
        float horizontalTravel = statsChartHorizontalTrack.width
                - statsChartHorizontalThumbWidth;
        float verticalTravel = statsChartVerticalTrack.height
                - statsChartVerticalThumbHeight;
        shapes.setColor(CYAN);
        roundedRect(statsChartHorizontalTrack.x
                + horizontalTravel * statsChartPanX,
                statsChartHorizontalTrack.y,
                statsChartHorizontalThumbWidth,
                statsChartHorizontalTrack.height, 4f);
        roundedRect(statsChartVerticalTrack.x,
                statsChartVerticalTrack.y
                        + verticalTravel * (1f - statsChartPanY),
                statsChartVerticalTrack.width,
                statsChartVerticalThumbHeight, 4f);
    }

    private float statsChartX(float x) {
        return statsChartViewport.x + statsChartCanvasCoordinate(
                x - statsChartViewport.x, statsChartViewport.width,
                statsChartZoom, statsChartPanX);
    }

    private float statsChartY(float y) {
        return statsChartViewport.y + statsChartCanvasVerticalCoordinate(
                y - statsChartViewport.y, statsChartViewport.height,
                statsChartZoom, statsChartPanY);
    }

    private float statsChartLength(float value) {
        return value * statsChartZoom;
    }

    static float statsChartCanvasCoordinate(float local, float extent,
            float zoom, float pan) {
        float safeZoom = MathUtils.clamp(zoom, 0.1f, 4f);
        if (safeZoom <= 1f) {
            return local * safeZoom + extent * (1f - safeZoom) / 2f;
        }
        return local * safeZoom
                - extent * (safeZoom - 1f) * MathUtils.clamp(pan, 0f, 1f);
    }

    static float statsChartCanvasVerticalCoordinate(float local, float extent,
            float zoom, float scroll) {
        return statsChartCanvasCoordinate(local, extent, zoom,
                1f - MathUtils.clamp(scroll, 0f, 1f));
    }

    private void chartTextFit(BitmapFont font, String value, float x, float y,
            Color color, boolean centered, float maxWidth) {
        float width = Math.max(1f, textWidth(font, value));
        float scale = Math.min(1f, maxWidth / width);
        List<TextItem> target = statsChartCanvasActive
                ? statsChartTexts : texts;
        target.add(new TextItem(font, value,
                statsChartCanvasActive ? statsChartX(x) : x,
                statsChartCanvasActive ? statsChartY(y) : y,
                new Color(color), centered, false, scale));
    }

    private void italicTextFit(BitmapFont preferred, String value, float x,
            float y, Color color, boolean centered, float maxWidth) {
        (settingsRowsActive ? settingsRowTexts : texts).add(fittedTextItem(
                preferred, value, x, y, color, centered, maxWidth, true));
    }

    private TextItem fittedTextItem(BitmapFont preferred, String value,
            float x, float y, Color color, boolean centered, float maxWidth,
            boolean italic) {
        BitmapFont selected = preferred;
        if (!fits(selected, value, maxWidth) && selected != smallFont) {
            selected = smallFont;
        }
        if (!fits(selected, value, maxWidth)) {
            selected = tinyFont;
        }
        return new TextItem(selected, ellipsize(selected, value, maxWidth),
                x, y, new Color(color), centered, italic);
    }

    private void drawTextItem(TextItem item) {
        float previousScaleX = item.font.getData().scaleX;
        float previousScaleY = item.font.getData().scaleY;
        item.font.getData().setScale(previousScaleX * item.scale,
                previousScaleY * item.scale);
        item.font.setColor(item.color);
        glyph.setText(item.font, item.text);
        float x = item.centered ? item.x - glyph.width / 2f : item.x;
        if (item.italic) {
            float shear = 0.18f;
            italicTransform.idt();
            italicTransform.val[Matrix4.M01] = shear;
            italicTransform.val[Matrix4.M03] = -shear * item.y;
            batch.setTransformMatrix(italicTransform);
        }
        item.font.draw(batch, item.text, x, item.y);
        if (item.italic) batch.setTransformMatrix(identityTransform);
        item.font.getData().setScale(previousScaleX, previousScaleY);
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
        if (menuRevealBlocksInteraction()) {
            return;
        }
        Rectangle bounds = clippedSettingsHit(x, y, w, h);
        if (bounds != null) hits.add(new Hit(bounds, action, false));
    }

    private void repeatHit(float x, float y, float w, float h,
            Runnable action) {
        if (menuRevealBlocksInteraction()) {
            return;
        }
        Rectangle bounds = clippedSettingsHit(x, y, w, h);
        if (bounds != null) hits.add(new Hit(bounds, action, true));
    }

    private Rectangle clippedSettingsHit(float x, float y, float w,
            float h) {
        Rectangle bounds = new Rectangle(x, y, w, h);
        if (!settingsRowsActive) return bounds;
        float left = Math.max(bounds.x, settingsRowsClip.x);
        float bottom = Math.max(bounds.y, settingsRowsClip.y);
        float right = Math.min(bounds.x + bounds.width,
                settingsRowsClip.x + settingsRowsClip.width);
        float top = Math.min(bounds.y + bounds.height,
                settingsRowsClip.y + settingsRowsClip.height);
        return right > left && top > bottom
                ? new Rectangle(left, bottom, right - left, top - bottom)
                : null;
    }

    private void secondaryHit(float x, float y, float w, float h,
            Runnable action) {
        secondaryHits.add(new Hit(new Rectangle(x, y, w, h), action, false));
    }

    private boolean hovered(float x, float y, float w, float h) {
        if (menuRevealBlocksInteraction()) {
            return false;
        }
        return (!settingsRowsActive || settingsRowsClip.contains(pointer))
                && pointer.x >= x && pointer.x <= x + w
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

    private void updatePointerRepeat() {
        if (pointerRepeatHit == null) return;
        pointer.set(Gdx.input.getX(), Gdx.input.getY());
        viewport.unproject(pointer);
        Hit current = matchingRepeatHit(pointerRepeatHit.bounds);
        boolean pressed = pointerRepeatSurface == surface
                && Gdx.input.isButtonPressed(Input.Buttons.LEFT)
                && pointerRepeatHit.bounds.contains(pointer)
                && current != null;
        int repeats = pointerRepeat.update(frameDelta, pressed);
        if (!pressed) {
            pointerRepeatHit = null;
            pointerRepeatSurface = null;
            return;
        }
        pointerRepeatHit = current;
        for (int repeat = 0; repeat < repeats; repeat++) {
            current.action.run();
        }
    }

    private Hit matchingRepeatHit(Rectangle bounds) {
        for (int index = hits.size() - 1; index >= 0; index--) {
            Hit hit = hits.get(index);
            if (hit.repeatable && sameBounds(hit.bounds, bounds)) return hit;
        }
        return null;
    }

    private static boolean sameBounds(Rectangle first, Rectangle second) {
        return Math.abs(first.x - second.x) < 0.5f
                && Math.abs(first.y - second.y) < 0.5f
                && Math.abs(first.width - second.width) < 0.5f
                && Math.abs(first.height - second.height) < 0.5f;
    }

    private void clearPointerRepeat() {
        pointerRepeat.clear();
        pointerRepeatHit = null;
        pointerRepeatSurface = null;
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
        boolean blockingModal = hasBlockingFrontendModal();
        if (surface == Surface.LOBBY
                && lobbyTableTransitionActive(lobbyGameStarting, lobby)) {
            return true;
        }
        if ((!blockingModal || statsPicker != StatsPicker.NONE)
                && button == Input.Buttons.LEFT
                && beginScrollDrag(pointer.x, pointer.y)) {
            return true;
        }
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
        if (button == Input.Buttons.LEFT) {
            for (int i = passwordRevealHits.size() - 1; i >= 0; i--) {
                PasswordRevealHit reveal = passwordRevealHits.get(i);
                if (reveal.bounds.contains(pointer)) {
                    activateField(reveal.id);
                    revealedPasswordField = reveal.id;
                    return true;
                }
            }
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
        if (button == Input.Buttons.RIGHT) {
            for (int i = secondaryHits.size() - 1; i >= 0; i--) {
                Hit hit = secondaryHits.get(i);
                if (hit.bounds.contains(pointer)) {
                    pressedHit = hit;
                    return true;
                }
            }
            return false;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.bounds.contains(pointer)) {
                pressedHit = hit;
                if (hit.repeatable && button == Input.Buttons.LEFT) {
                    hit.action.run();
                    pointerRepeatHit = hit;
                    pointerRepeatSurface = surface;
                    pointerRepeat.press(1);
                }
                return true;
            }
        }
        clearActiveField();
        return blockingModal;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointerIndex) {
        pointer.set(screenX, screenY);
        viewport.unproject(pointer);
        if (scrollDrag != ScrollDrag.NONE) {
            updateScrollDrag(pointer.x, pointer.y);
            return true;
        }
        if (pointerSelectionField == null) return hasBlockingFrontendModal();
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
        boolean passwordWasRevealed = revealedPasswordField != null;
        revealedPasswordField = null;
        Hit released = pressedHit;
        pressedHit = null;
        clearPointerRepeat();
        pointerSelectionField = null;
        if (surface == Surface.LOBBY
                && lobbyTableTransitionActive(lobbyGameStarting, lobby)) {
            return true;
        }
        if (scrollDrag != ScrollDrag.NONE) {
            scrollDrag = ScrollDrag.NONE;
            return true;
        }
        if (released != null && !released.repeatable
                && released.bounds.contains(pointer)) {
            released.action.run();
            return true;
        }
        return passwordWasRevealed || hasBlockingFrontendModal();
    }

    private boolean beginScrollDrag(float x, float y) {
        if (surface == Surface.STATS && statsPicker != StatsPicker.NONE
                && statsPickerScrollMaximum > 0f
                && statsPickerScrollTrack.contains(x, y)) {
            scrollDrag = ScrollDrag.STATS_PICKER;
            updateScrollDrag(x, y);
            return true;
        }
        if (surface == Surface.STATS && statsPicker == StatsPicker.NONE
                && statsChartZoom > 1f
                && statsChartHorizontalTrack.contains(x, y)) {
            scrollDrag = ScrollDrag.STATS_CHART_HORIZONTAL;
            updateScrollDrag(x, y);
            return true;
        }
        if (surface == Surface.STATS && statsPicker == StatsPicker.NONE
                && statsChartZoom > 1f
                && statsChartVerticalTrack.contains(x, y)) {
            scrollDrag = ScrollDrag.STATS_CHART_VERTICAL;
            updateScrollDrag(x, y);
            return true;
        }
        if (surface == Surface.STATS && statsPicker == StatsPicker.NONE
                && statsResultScrollMaximum > 0f
                && statsResultScrollTrack.contains(x, y)) {
            scrollDrag = ScrollDrag.STATS_RESULTS;
            updateScrollDrag(x, y);
            return true;
        }
        if (surface == Surface.LOBBY && !lobbyImageMode
                && !lobbyEmojiPickerOpen && lobbyChatScrollMaximum > 0
                && lobbyChatScrollTrack.contains(x, y)) {
            scrollDrag = ScrollDrag.LOBBY_CHAT;
            updateScrollDrag(x, y);
            return true;
        }
        if (surface == Surface.SETTINGS
                && settingsRowScrollMaximum > 0
                && settingsRowScrollTrack.contains(x, y)
                && (settingsSession.section()
                        == GdxSettingsContract.Section.APPEARANCE
                    || settingsSession.section()
                        == GdxSettingsContract.Section.AUDIO
                    || settingsSession.section()
                        == GdxSettingsContract.Section.SHORTCUTS)) {
            scrollDrag = ScrollDrag.SETTINGS_ROWS;
            updateScrollDrag(x, y);
            return true;
        }
        if (surface == Surface.SETTINGS
                && settingsSession.section()
                        == GdxSettingsContract.Section.DEBUG
                && settingsDebugScrollMaximum > 0
                && settingsDebugScrollTrack.contains(x, y)) {
            scrollDrag = ScrollDrag.SETTINGS_DEBUG;
            updateScrollDrag(x, y);
            return true;
        }
        return false;
    }

    private void updateScrollDrag(float x, float y) {
        if (scrollDrag == ScrollDrag.STATS_PICKER) {
            float travel = Math.max(1f, statsPickerScrollTrack.height
                    - statsPickerScrollThumbHeight);
            float progress = MathUtils.clamp((statsPickerScrollTrack.y
                    + statsPickerScrollTrack.height
                    - statsPickerScrollThumbHeight / 2f - y) / travel,
                    0f, 1f);
            statsPickerScrollTarget = progress * statsPickerScrollMaximum;
            statsPickerScroll = statsPickerScrollTarget;
        } else if (scrollDrag == ScrollDrag.STATS_CHART_HORIZONTAL) {
            float travel = Math.max(1f, statsChartHorizontalTrack.width
                    - statsChartHorizontalThumbWidth);
            float progress = MathUtils.clamp((x
                    - statsChartHorizontalTrack.x
                    - statsChartHorizontalThumbWidth / 2f) / travel,
                    0f, 1f);
            statsChartPanX = statsChartPanTargetX = progress;
        } else if (scrollDrag == ScrollDrag.STATS_CHART_VERTICAL) {
            float travel = Math.max(1f, statsChartVerticalTrack.height
                    - statsChartVerticalThumbHeight);
            float progress = MathUtils.clamp((statsChartVerticalTrack.y
                    + statsChartVerticalTrack.height
                    - statsChartVerticalThumbHeight / 2f - y) / travel,
                    0f, 1f);
            statsChartPanY = statsChartPanTargetY = progress;
        } else if (scrollDrag == ScrollDrag.STATS_RESULTS) {
            float travel = Math.max(1f, statsResultScrollTrack.height
                    - statsResultScrollThumbHeight);
            float progress = MathUtils.clamp((statsResultScrollTrack.y
                    + statsResultScrollTrack.height
                    - statsResultScrollThumbHeight / 2f - y) / travel,
                    0f, 1f);
            statsResultScrollTarget = progress * statsResultScrollMaximum;
            statsResultScroll = statsResultScrollTarget;
        } else if (scrollDrag == ScrollDrag.LOBBY_CHAT) {
            float travel = Math.max(1f, lobbyChatScrollTrack.height
                    - lobbyChatScrollThumbHeight);
            float progress = MathUtils.clamp((y - lobbyChatScrollTrack.y
                    - lobbyChatScrollThumbHeight / 2f) / travel, 0f, 1f);
            lobbyChatScroll = progress * lobbyChatScrollMaximum;
        } else if (scrollDrag == ScrollDrag.SETTINGS_DEBUG) {
            settingsDebugScroll = CoronaPokerGdxTable
                    .quickChatPixelScrollFromTrack(
                    y, settingsDebugScrollTrack.y,
                    settingsDebugScrollTrack.height,
                    settingsDebugScrollThumbHeight,
                    settingsDebugScrollMaximum);
        } else if (scrollDrag == ScrollDrag.SETTINGS_ROWS) {
            float offset = GdxSettingsLayout.pixelScrollFromScrollbar(y,
                    settingsRowScrollTrack.y,
                    settingsRowScrollTrack.height,
                    settingsRowScrollThumbHeight,
                    settingsRowScrollMaximum);
            if (settingsSession.section()
                    == GdxSettingsContract.Section.APPEARANCE) {
                settingsAppearanceScroll = offset;
            } else if (settingsSession.section()
                    == GdxSettingsContract.Section.AUDIO) {
                settingsAudioScroll = offset;
            } else if (settingsSession.section()
                    == GdxSettingsContract.Section.SHORTCUTS) {
                settingsShortcutScroll = offset;
            }
        }
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
        if (surface == Surface.LOBBY && activeField == null && control
                && keycode == Input.Keys.C
                && selectedLobbyChatSequence >= 0L) {
            copySelectedLobbyChatMessage();
            return true;
        }
        if (surface == Surface.SETTINGS
                && settingsSession.section()
                        == GdxSettingsContract.Section.SHORTCUTS
                && settingsShortcutCaptureId != null) {
            GdxShortcutBindings.Assignment assignment = shortcutBindings.assign(
                    settingsShortcutCaptureId, keycode, alt, control, shift);
            if (assignment == GdxShortcutBindings.Assignment.ASSIGNED) {
                settingsShortcutCaptureId = null;
                settingsShortcutStatus = "updated";
            } else if (assignment == GdxShortcutBindings.Assignment.CONFLICT) {
                settingsShortcutStatus = "conflict";
            } else {
                settingsShortcutStatus = "unsupported";
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
        // Table creation owns this frontend until it either succeeds or
        // restores the lobby.  F11 above remains available, but no hidden
        // lobby field/action may be triggered through the loading overlay.
        if (surface == Surface.LOBBY
                && lobbyTableTransitionActive(lobbyGameStarting, lobby)) {
            return true;
        }
        if (aboutOpen && handGeneratorOpen) {
            if (keycode == Input.Keys.LEFT || keycode == Input.Keys.DOWN) {
                handGenerator.previous();
                return true;
            }
            if (keycode == Input.Keys.RIGHT || keycode == Input.Keys.UP
                    || keycode == Input.Keys.ENTER
                    || keycode == Input.Keys.NUMPAD_ENTER) {
                handGenerator.next();
                return true;
            }
        }
        if (keycode == Input.Keys.ESCAPE) {
            if (dropdown != Dropdown.NONE) {
                dropdown = Dropdown.NONE;
                return true;
            }
            if (settingsRestartNotice) {
                settingsRestartNotice = false;
                return true;
            }
            if (surface == Surface.SCREENSHOTS) {
                if (screenshotDeleteConfirmation) {
                    screenshotDeleteConfirmation = false;
                } else {
                    closeScreenshotViewer();
                }
                return true;
            }
            if (surface == Surface.STATS) {
                if (statsSyncExclusionsOpen) {
                    closeStatsSyncExclusions();
                } else if (statsPicker != StatsPicker.NONE) {
                    statsPicker = StatsPicker.NONE;
                } else if (statsConfirmation != StatsConfirmation.NONE) {
                    statsConfirmation = StatsConfirmation.NONE;
                } else {
                    closeStats();
                }
                return true;
            } else if (aboutOpen) {
                if (aboutEasterEggTexture != null) {
                    closeAboutEasterEgg();
                } else if (handGeneratorOpen) {
                    closeHandGenerator();
                } else {
                    closeAboutDialog();
                }
                return true;
            }
            if (updatePromptOpen) {
                if (!updateInstalling) dismissUpdatePrompt();
                return true;
            }
            if (fingerprintDialog != null) {
                fingerprintDialog = null;
                return true;
            }
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
            if (voiceNotesOpen) {
                if (voiceNoteDeleteConfirmation != null
                        || voiceNotesPurgeConfirmation) {
                    voiceNoteDeleteConfirmation = null;
                    voiceNotesPurgeConfirmation = false;
                } else {
                    closeVoiceNotes();
                }
                return true;
            }
            if (settingsDiscardConfirmation) {
                settingsDiscardConfirmation = false;
                return true;
            }
            if (statsConfirmation != StatsConfirmation.NONE) {
                statsConfirmation = StatsConfirmation.NONE;
                return true;
            }
            if (lobbyVoiceRecorder != null) {
                cancelLobbyVoiceRecording();
                return true;
            }
            if (lobbyImageClearConfirmation) {
                lobbyImageClearConfirmation = false;
                return true;
            }
            if (surface == Surface.LOBBY
                    && (lobbyEmojiPickerOpen || lobbyImageMode)) {
                lobbyEmojiPickerOpen = false;
                lobbyImageMode = false;
                activateField("lobbyChat");
                return true;
            }
            if (surface == Surface.LOBBY && lobbyPasswordDialog) {
                closeLobbyPasswordDialog();
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
            } else if (surface == Surface.STATS) {
                closeStats();
            }
            // The root menu is already the navigation endpoint.  ESC must not
            // terminate the process there: exiting is an explicit, confirmed
            // action owned by the SALIR button (and by the window-close flow).
            return true;
        }
        if ((keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER)
                && ("lobbyChat".equals(activeField)
                        || "lobbyImage".equals(activeField))) {
            sendLobbyComposer();
            return true;
        }
        if ((keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER)
                && lobbyPasswordDialog
                && "lobbyPassword".equals(activeField)) {
            submitLobbyPassword();
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
        if ((keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER)
                && statsSyncExclusionsOpen
                && "statsExcludeNicks".equals(activeField)) {
            saveStatsSyncExclusions();
            return true;
        }
        if (surface == Surface.STATS && statsPicker == StatsPicker.NONE
                && statsConfirmation == StatsConfirmation.NONE
                && !statsSyncExclusionsOpen && activeField == null
                && statsResultScrollMaximum > 0f) {
            float rowStride = statsResultRowStride();
            if (keycode == Input.Keys.DOWN) {
                statsResultScrollTarget = Math.min(
                        statsResultScrollMaximum,
                        statsResultScrollTarget + rowStride);
                return true;
            }
            if (keycode == Input.Keys.UP) {
                statsResultScrollTarget = Math.max(0f,
                        statsResultScrollTarget - rowStride);
                return true;
            }
            if (keycode == Input.Keys.PAGE_DOWN) {
                statsResultScrollTarget = Math.min(
                        statsResultScrollMaximum,
                        statsResultScrollTarget + rowStride
                                * STATS_RESULT_VISIBLE_ROWS);
                return true;
            }
            if (keycode == Input.Keys.PAGE_UP) {
                statsResultScrollTarget = Math.max(0f,
                        statsResultScrollTarget - rowStride
                                * STATS_RESULT_VISIBLE_ROWS);
                return true;
            }
            if (keycode == Input.Keys.HOME) {
                statsResultScrollTarget = 0f;
                return true;
            }
            if (keycode == Input.Keys.END) {
                statsResultScrollTarget = statsResultScrollMaximum;
                return true;
            }
        }
        if (surface == Surface.SCREENSHOTS) {
            if (!screenshotDeleteConfirmation && keycode == Input.Keys.LEFT) {
                showRelativeScreenshot(-1);
            } else if (!screenshotDeleteConfirmation
                    && keycode == Input.Keys.RIGHT) {
                showRelativeScreenshot(1);
            }
            return true;
        }
        if (frontendModalTextFieldActive() && activeField != null
                && handleActiveFieldKey(keycode)) {
            if (isTextDeletionKey(keycode)) {
                textDeleteRepeat.press(keycode);
            }
            return true;
        }
        if (hasBlockingFrontendModal()) {
            // Every modal owns the keyboard as well as pointer feedback. The
            // editable modal fields above are the only deliberate exception.
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
        if (hostPages && activeField == null && keycode == Input.Keys.RIGHT && page < 5) {
            page++;
            return true;
        }
        if (hostPages && keycode == Input.Keys.TAB) {
            clearActiveField();
            page = (page + 1) % 6;
            return true;
        }
        return false;
    }

    private void toggleFullscreenMode() {
        if (GdxDisplayModeController.toggle(initialProperties)
                && surface != Surface.SETTINGS) {
            preferences.saveDeferred();
        }
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
        volumeOverlayUntil = elapsed + 1f;
        playPreferenceSound("misc/volume_change.wav",
                "sonido_volumen", 0.72f);
    }

    @Override
    public boolean keyTyped(char character) {
        editMenu = null;
        if (hasBlockingFrontendModal()
                && !frontendModalTextFieldActive()) {
            return true;
        }
        if (activeField == null || Character.isISOControl(character)) {
            return false;
        }
        String value = activeValue();
        textEdit.focus(activeField, value);
        replaceActiveSelection(Character.toString(character));
        return true;
    }

    private boolean frontendModalTextFieldActive() {
        return (lobbyPasswordDialog
                        && "lobbyPassword".equals(activeField))
                || (presetDialog == PresetDialog.NAME
                        && "presetName".equals(activeField))
                || ((blindStructureDialog == BlindStructureDialog.NAME_NEW
                        || blindStructureDialog
                                == BlindStructureDialog.NAME_DUPLICATE
                        || blindStructureDialog
                                == BlindStructureDialog.NAME_RENAME)
                        && "blindStructureName".equals(activeField))
                || (statsSyncExclusionsOpen
                        && "statsExcludeNicks".equals(activeField));
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
            case "password", "lobbyPassword" -> 30;
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
            case "lobbyPassword" -> lobbyPasswordDraft;
            case "presetName" -> presetNameDraft;
            case "blindStructureName" -> blindStructureNameDraft;
            case "statsExcludeNicks" -> statsExcludeNicksDraft;
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
            case "lobbyPassword" -> lobbyPasswordDraft = value;
            case "presetName" -> presetNameDraft = value;
            case "blindStructureName" -> blindStructureNameDraft = value;
            case "statsExcludeNicks" -> statsExcludeNicksDraft = value;
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
            boolean masked = isPasswordField(field.id)
                    && !field.id.equals(revealedPasswordField);
            String display = masked ? maskedPassword(value) : value;
            FrontendInputWindow window = frontendInputWindow(uiFont, value,
                    display, field.bounds.width
                            - (isPasswordField(field.id) ? 102f : 44f), true);
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

    static String maskedPassword(String value) {
        String safe = Objects.requireNonNullElse(value, "");
        return PASSWORD_MASK_GLYPH.repeat(
                safe.codePointCount(0, safe.length()));
    }

    static String passwordDisplay(String value, boolean secret,
            boolean revealed) {
        String safe = Objects.requireNonNullElse(value, "");
        return secret && !revealed && !safe.isEmpty()
                ? maskedPassword(safe) : safe;
    }

    static String lobbyPasswordActionKey(String password) {
        return !lobbyPasswordEnabled(password)
                ? "auth.menu_poner_password"
                : "auth.menu_cambiar_password";
    }

    static boolean lobbyPasswordEnabled(String password) {
        return password != null && !password.isBlank();
    }

    static Rectangle passwordRevealBounds(float x, float y, float width) {
        return new Rectangle(x + width - 56f, y + 12f, 44f, 46f);
    }

    private static boolean isPasswordField(String id) {
        return "password".equals(id) || "lobbyPassword".equals(id);
    }

    static Rectangle mainMenuSoundBounds() {
        return new Rectangle(WIDTH - MENU_SOUND_RIGHT_MARGIN
                        - MENU_SOUND_SIZE,
                MENU_SOUND_BOTTOM_MARGIN, MENU_SOUND_SIZE, MENU_SOUND_SIZE);
    }

    private static boolean shiftPressed() {
        return Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
    }

    private void clearActiveField() {
        activeField = null;
        revealedPasswordField = null;
        textEdit.blur();
        textDeleteRepeat.clear();
    }

    @Override
    public void dispose() {
        disposed = true;
        recoveryLoadGeneration++;
        autoSubmitRecovery = false;
        recoveryExecutor.shutdownNow();
        statsExecutor.shutdownNow();
        networkInfoExecutor.shutdownNow();
        cancelLobbyVoiceRecording();
        audioPreview.stop();
        GdxVoicePlayback.stop();
        clearLobbyMedia();
        disposeAboutEasterEgg();
        disposeScreenshotTexture();
        lobbyHistoryMedia.dispose();
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
        aboutMourningIcon.dispose();
        aboutBookIcon.dispose();
        aboutCrossIcon.dispose();
        if (aboutModIcon != null) aboutModIcon.dispose();
        soundEnabledCue.dispose();
        soundDisabledCue.dispose();
        participantJoinedCue.dispose();
        participantLeftCue.dispose();
        for (Sound cue : preferenceSoundCues.values()) cue.dispose();
        preferenceSoundCues.clear();
        failedPreferenceSoundCues.clear();
        backgroundMusic.stop();
        backgroundMusic.dispose();
        waitingRoomMusic.stop();
        waitingRoomMusic.dispose();
        aboutMusic.stop();
        aboutMusic.dispose();
        statsMusic.stop();
        statsMusic.dispose();
        for (Texture texture : lobbyAvatarTextures.values()) {
            texture.dispose();
        }
        lobbyAvatarTextures.clear();
        for (Texture texture : handGeneratorCardTextures.values()) {
            texture.dispose();
        }
        handGeneratorCardTextures.clear();
        for (Texture texture : emojiTextures.values()) {
            texture.dispose();
        }
        emojiTextures.clear();
        avatarShader.dispose();
        roundedTextureShader.dispose();
        titleFont.dispose();
        headingFont.dispose();
        actionFont.dispose();
        uiFont.dispose();
        volumeOverlayFont.dispose();
        smallFont.dispose();
        tinyFont.dispose();
        versionFont.dispose();
    }

    @Override public boolean keyUp(int keycode) {
        textDeleteRepeat.release(keycode);
        if (keycode == Input.Keys.F9 && lobbyVoiceRecorder != null) {
            finishLobbyVoiceRecording(false);
            return true;
        }
        return false;
    }
    @Override public boolean touchCancelled(int x, int y, int p, int b) {
        pressedHit = null;
        clearPointerRepeat();
        return hasBlockingFrontendModal();
    }
    @Override public boolean mouseMoved(int x, int y) {
        pointer.set(x, y);
        viewport.unproject(pointer);
        return false;
    }
    @Override
    public boolean scrolled(float amountX, float amountY) {
        if (statsPicker != StatsPicker.NONE && amountY != 0f) {
            statsPickerScrollTarget = statsPickerScrollAfterWheel(
                    statsPickerScrollTarget, statsPickerScrollMaximum,
                    amountY);
            return true;
        }
        if (hasBlockingFrontendModal() && dropdown == Dropdown.NONE) {
            return true;
        }
        if (dropdown != Dropdown.NONE && amountY != 0f) {
            int maximum = Math.max(0, dropdownOptions(dropdown).size() - 6);
            dropdownScroll = MathUtils.clamp(dropdownScroll
                    + (amountY > 0f ? 1 : -1), 0, maximum);
            return true;
        }
        if (surface == Surface.STATS && (amountX != 0f || amountY != 0f)) {
            pointer.set(Gdx.input.getX(), Gdx.input.getY());
            viewport.unproject(pointer);
            if (statsChartZoom > 1f
                    && statsChartViewport.contains(pointer)) {
                if (amountX != 0f || shiftPressed()) {
                    float direction = amountX != 0f ? amountX : amountY;
                    statsChartPanTargetX = MathUtils.clamp(
                            statsChartPanTargetX + direction * 0.08f,
                            0f, 1f);
                } else {
                    statsChartPanTargetY = MathUtils.clamp(
                            statsChartPanTargetY + amountY * 0.08f,
                            0f, 1f);
                }
                return true;
            }
            if (statsGameIndex >= 0 && statsHandIndex < 0
                    && amountY != 0f
                    && pointer.x >= 86f && pointer.x <= 434f
                    && pointer.y >= 323f && pointer.y <= 395f) {
                float maximum = statsSummaryPlayersMaximum(
                        statsGames.get(statsGameIndex).players().size());
                statsSummaryPlayersScrollTarget = MathUtils.clamp(
                        statsSummaryPlayersScrollTarget
                                + amountY * 46f, 0f, maximum);
                return true;
            }
            // Recompute here as well as during rendering. Results arrive
            // asynchronously, so the first wheel event must work even when it
            // lands between the data callback and the next painted frame.
            updateStatsResultScrollBounds();
            if (statsResultScrollMaximum > 0f && amountY != 0f) {
                statsResultScrollTarget = statsResultScrollAfterWheel(
                        statsResultScrollTarget, statsResultScrollMaximum,
                        statsResultRowStride(), amountY);
                return true;
            }
        }
        if (surface == Surface.LOBBY && amountY != 0f
                && !lobbyImageMode && !lobbyEmojiPickerOpen) {
            pointer.set(Gdx.input.getX(), Gdx.input.getY());
            viewport.unproject(pointer);
            if (pointer.x >= 525f && pointer.x <= 1365f
                    && pointer.y >= 315f && pointer.y <= 744f) {
                lobbyChatScroll = lobbyPixelScrollAfterWheel(lobbyChatScroll,
                        lobbyChatScrollMaximum, amountY);
                return true;
            }
        }
        if (surface == Surface.SETTINGS && amountY != 0f
                && settingsSession.section()
                == GdxSettingsContract.Section.SHORTCUTS) {
            settingsShortcutScroll = GdxSettingsLayout.pixelScrollAfterWheel(
                    settingsShortcutScroll, settingsRowScrollMaximum,
                    amountY);
            return true;
        }
        if (surface == Surface.SETTINGS && amountY != 0f
                && settingsSession.section()
                == GdxSettingsContract.Section.AUDIO) {
            settingsAudioScroll = GdxSettingsLayout.pixelScrollAfterWheel(
                    settingsAudioScroll, settingsRowScrollMaximum, amountY);
            return true;
        }
        if (surface == Surface.SETTINGS && amountY != 0f
                && settingsSession.section()
                == GdxSettingsContract.Section.APPEARANCE
                && settingsAppearancePage > 0) {
            settingsAppearanceScroll = GdxSettingsLayout
                    .pixelScrollAfterWheel(settingsAppearanceScroll,
                            settingsRowScrollMaximum, amountY);
            return true;
        }
        if (surface == Surface.SETTINGS && amountY != 0f
                && settingsSession.section()
                == GdxSettingsContract.Section.DEBUG) {
            pointer.set(Gdx.input.getX(), Gdx.input.getY());
            viewport.unproject(pointer);
            if (settingsDebugViewport.contains(pointer)) {
                settingsDebugScroll = lobbyPixelScrollAfterWheel(
                        settingsDebugScroll, settingsDebugScrollMaximum,
                        amountY);
                return true;
            }
        }
        return false;
    }

    private record TextItem(BitmapFont font, String text, float x, float y,
            Color color, boolean centered, boolean italic, float scale) {

        private TextItem(BitmapFont font, String text, float x, float y,
                Color color, boolean centered, boolean italic) {
            this(font, text, x, y, color, centered, italic, 1f);
        }
    }

    record StatsCardGlyph(String rank, String suit, boolean red) {
    }

    private record LobbyAvatarItem(Texture texture, float x, float y,
            float size) {
    }

    private record LobbyInfoItem(String label, String value) {
    }

    private record LobbyMessageLayout(float width, float height,
            List<String> lines) {
    }

    private record LobbyChatBubbleItem(long sequence, float x, float y, float width,
            float height, Color border, Color fill) {
    }

    private record LobbyVoiceControlItem(float playX, float stopX, float y,
            boolean active, boolean paused) {
    }

    private record UiImageItem(Texture texture, float x, float y,
            float width, float height, Color tint) {

        private UiImageItem(Texture texture, float x, float y,
                float width, float height) {
            this(texture, x, y, width, height, Color.WHITE);
        }
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
        MENU, NEW_GAME, LOBBY, SETTINGS, STATS, SCREENSHOTS
    }

    private enum ScrollDrag {
        NONE, LOBBY_CHAT, SETTINGS_ROWS, SETTINGS_DEBUG, STATS_PICKER,
        STATS_RESULTS, STATS_CHART_HORIZONTAL, STATS_CHART_VERTICAL
    }

    private enum StatsMode {
        BALANCE(true), RESPONSE(true), BEST_HANDS(false), PERFORMANCE(false),
        PREFLOP_RAISES(false),
        FLOP_RAISES(false), TURN_RAISES(false), RIVER_RAISES(false);

        private final boolean handScope;

        StatsMode(boolean handScope) {
            this.handScope = handScope;
        }

        boolean supportsHandScope() {
            return handScope;
        }
    }

    private enum StatsConfirmation {
        NONE, GAME, IMPORTED, PURGE_FILTERED, PRIVATE_FILTERED,
        PUBLIC_FILTERED
    }

    private enum StatsPicker {
        NONE, GAME, HAND, MODE, PLAYER
    }

    private enum LobbyConfirmation {
        START, LEAVE
    }

    private enum FingerprintMode {
        IDENTITY, SESSION
    }

    private record FingerprintDialog(String nickname, byte[] identityPublicKey,
            IdenticonFingerprint identity, IdenticonFingerprint session,
            FingerprintMode mode) {

        private FingerprintDialog {
            identityPublicKey = identityPublicKey == null
                    ? null : identityPublicKey.clone();
        }

        @Override public byte[] identityPublicKey() {
            return identityPublicKey == null ? null : identityPublicKey.clone();
        }

        private IdenticonFingerprint active() {
            return mode == FingerprintMode.IDENTITY ? identity : session;
        }

        private FingerprintDialog withMode(FingerprintMode next) {
            return new FingerprintDialog(nickname, identityPublicKey,
                    identity, session, next);
        }
    }

    private enum PresetDialog {
        NONE, NAME, OVERWRITE, DELETE
    }

    private enum BlindStructureDialog {
        NONE, EDITOR, NAME_NEW, NAME_DUPLICATE, NAME_RENAME, DELETE
    }

    private enum Dropdown {
        NONE, PROFILE, BLIND_STRUCTURE, HOST_ADDRESS
    }

    private enum ButtonTone {
        NEUTRAL, FEATURED, POSITIVE, DANGER
    }

    private record Hit(Rectangle bounds, Runnable action, boolean repeatable) {
    }

    private record TextFieldHit(String id, Rectangle bounds) {
    }

    private record PasswordRevealHit(String id, Rectangle bounds) {
    }

    private record TooltipHit(Rectangle bounds, String key,
            Object[] arguments,
            GdxSettingsContract.PerformanceImpact performanceImpact) {

        String identity() {
            return key + '@' + Float.floatToIntBits(bounds.x) + ':'
                    + Float.floatToIntBits(bounds.y) + ':'
                    + Float.floatToIntBits(bounds.width) + ':'
                    + Float.floatToIntBits(bounds.height);
        }
    }

    private static final class EditMenu {

        private final Rectangle bounds;

        private EditMenu(float x, float y, float width, float height) {
            bounds = new Rectangle(x, y, width, height);
        }
    }
}
