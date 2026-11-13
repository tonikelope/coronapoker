package com.tonikelope.coronapoker.gdx;

import static com.tonikelope.coronapoker.gdx.GdxSettingsContract.BLIND_ROW_COUNT;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.glutils.HdpiUtils;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.tonikelope.coronapoker.core.ApplicationMetadata;
import com.tonikelope.coronapoker.core.IdenticonFingerprint;
import com.tonikelope.coronapoker.core.IdentityTrustStore;
import com.tonikelope.coronapoker.core.LobbyChatMessage;
import com.tonikelope.coronapoker.core.LobbyCommand;
import com.tonikelope.coronapoker.core.LobbyParticipant;
import com.tonikelope.coronapoker.core.LobbySession;
import com.tonikelope.coronapoker.core.LobbySnapshot;
import com.tonikelope.coronapoker.core.NetworkBlock;
import com.tonikelope.coronapoker.core.AvatarImageValidator;
import com.tonikelope.coronapoker.core.NewGameConnectionDraft;
import com.tonikelope.coronapoker.core.NewGameSessionGateway;
import com.tonikelope.coronapoker.core.NewGameSubmissionCoordinator;
import com.tonikelope.coronapoker.core.NewGameTableDraft;
import com.tonikelope.coronapoker.core.PreferencesService;
import com.tonikelope.coronapoker.core.BlindStructureCatalog;
import com.tonikelope.coronapoker.core.AnteMode;
import com.tonikelope.coronapoker.core.BlindStructureRules;
import com.tonikelope.coronapoker.core.BotNickname;
import com.tonikelope.coronapoker.core.CustomBotProfile;
import com.tonikelope.coronapoker.core.CustomBotProfileCatalog;
import com.tonikelope.coronapoker.core.GamePresetCatalog;
import com.tonikelope.coronapoker.core.RecoverableGameRepository;
import com.tonikelope.coronapoker.core.StatsRepository;
import com.tonikelope.coronapoker.core.UpdateService;
import com.tonikelope.coronapoker.core.UpdaterService;
import com.tonikelope.coronapoker.core.network.UpnpPortReservation;
import com.tonikelope.coronapoker.core.network.LobbyConnectionRefusedException;
import com.tonikelope.coronapoker.BuyinRules;
import com.tonikelope.coronapoker.DebugLog;
import org.dosse.upnp.UPnP;
import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.BindException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.ByteBuffer;
import java.time.ZoneId;
import java.time.Instant;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import javax.imageio.ImageIO;

/** Native menu and staged NewGameDialog replacement. */
final class GdxFrontendScreen extends ApplicationAdapter implements InputProcessor {

    private static final String DEFAULT_TABLE_SETTINGS_KEY =
            "gdx_default_table_settings";


    private static final Logger LOGGER = Logger.getLogger(
            GdxFrontendScreen.class.getName());

    private static final float WIDTH = 1920f;
    private static final float HEIGHT = 1080f;
    private static final float BLINDS_PANEL_BOTTOM = 166f;
    static final float NEW_GAME_GAME_TOP_ROW_Y = 625f;
    static final float NEW_GAME_GAME_SECOND_ROW_Y = 505f;
    static final float NEW_GAME_GAME_LABELLED_ROW_Y = 345f;
    static final float NEW_GAME_GAME_BOTTOM_ROW_Y = 225f;
    private static final int AVATAR_CROP_OUTPUT_SIZE = 384;
    private static final float AVATAR_CROP_VIEW_SIZE = 650f;
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
    static final float MENU_QUOTE_LINE_HEIGHT = 24f;
    static final float MENU_QUOTE_SINGLE_BASELINE = 88f + MENU_QUOTE_LINE_HEIGHT;
    static final float MENU_QUOTE_TWO_LINE_TOP_BASELINE =
            MENU_QUOTE_SINGLE_BASELINE + MENU_QUOTE_LINE_HEIGHT;
    static final int MENU_QUOTE_MAX_LINES = 2;
    static final float LOBBY_LEFT_ACTION_X = 55f;
    static final float LOBBY_LEFT_ACTION_WIDTH = 390f;
    static final float LOBBY_LEFT_CONTENT_X = LOBBY_LEFT_ACTION_X;
    static final float LOBBY_LEFT_CONTENT_WIDTH = LOBBY_LEFT_ACTION_WIDTH;
    static final float LOBBY_CONNECTION_Y = 674f;
    static final float LOBBY_PUBLIC_ADDRESS_BASELINE = 702f;
    static final float LOBBY_CONNECTION_HEIGHT = 82f;
    static final float LOBBY_CONNECTION_VALUE_OFFSET = 120f;
    static final float LOBBY_CONNECTION_RIGHT_PADDING = 16f;
    static final float LOBBY_CONNECTION_STATUS_WIDTH = 98f;
    static final float LOBBY_CONNECTION_STATUS_GAP = 12f;
    static final float LOBBY_PASSWORD_Y = 622f;
    static final float LOBBY_PASSWORD_HEIGHT = 40f;
    static final float LOBBY_PASSWORD_DIALOG_HINT_Y = 470f;
    static final float LOBBY_GAME_INFO_Y = 320f;
    static final float LOBBY_GAME_INFO_HEADER_HEIGHT = 42f;
    static final float LOBBY_GAME_INFO_MIN_ROW_HEIGHT = 22f;
    static final float LOBBY_GAME_INFO_BODY_PADDING = 5f;
    static final float LOBBY_GAME_INFO_HEIGHT = LOBBY_GAME_INFO_HEADER_HEIGHT
            + GdxLobbyGameSummary.MAX_ROWS * LOBBY_GAME_INFO_MIN_ROW_HEIGHT
            + 2f * LOBBY_GAME_INFO_BODY_PADDING;
    static final float LOBBY_ADD_BOT_Y = 265f;
    static final float LOBBY_ADD_BOT_HEIGHT = 44f;
    static final float LOBBY_PLAY_BUTTON_Y = 198f;
    static final float LOBBY_PLAY_BUTTON_HEIGHT = 55f;
    static final float LOBBY_CLOCK_Y = 888f;
    static final float LOBBY_ROSTER_TITLE_BASELINE = 795f;
    static final float LOBBY_ROSTER_COUNT_X = 1828f;
    static final float LOBBY_ROSTER_COUNT_BASELINE = 799f;
    static final float LOBBY_ROSTER_COUNT_WIDTH = 72f;
    private static final float LOBBY_COMPOSER_X = 525f;
    private static final float LOBBY_COMPOSER_Y = LOBBY_PLAY_BUTTON_Y;
    private static final float LOBBY_COMPOSER_HEIGHT = LOBBY_PLAY_BUTTON_HEIGHT;
    private static final float LOBBY_COMPOSER_GAP = 10f;
    private static final float LOBBY_COMPOSER_ICON_WIDTH = LOBBY_COMPOSER_HEIGHT;
    private static final float LOBBY_COMPOSER_SEND_X = 1270f;
    private static final float LOBBY_COMPOSER_SEND_WIDTH = 95f;
    private static final float LOBBY_TEXT_INPUT_WIDTH = LOBBY_COMPOSER_SEND_X
            - LOBBY_COMPOSER_X - 3f * LOBBY_COMPOSER_ICON_WIDTH
            - 4f * LOBBY_COMPOSER_GAP;
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
    private static final Color MOD_OVERRIDE = new Color(
            GdxSettingsStyle.MOD_OVERRIDE_RGBA);
    private static final Color MOD_OVERRIDE_DISABLED = new Color(
            GdxSettingsStyle.MOD_OVERRIDE_DISABLED_RGBA);
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
    private static final int EMOJI_COUNT = GdxEmojiAtlas.COUNT;
    private static final int EMOJI_COLUMNS = 12;
    private static final int EMOJI_ROWS = 6;
    static final int EMOJI_PAGE_SIZE = EMOJI_COLUMNS * EMOJI_ROWS;
    static final float LOBBY_EMOJI_PICKER_CELL_SIZE = 40f;
    static final float LOBBY_EMOJI_PICKER_IMAGE_SIZE = 38f;
    private static final Pattern EMOJI_TOKEN = Pattern.compile("#([0-9]{1,4})#");
    private static final Pattern CHAT_WRAP_TOKEN = Pattern.compile(
            "#[0-9]{1,4}#|\\s+|[^\\s#]+|#");
    private static final int CHAT_TEXT_MAX_LINES = 8;
    private static final float VOICE_RECORD_MAX_SECONDS = 15f;
    private static final float VOICE_STATUS_SECONDS = 2.8f;
    private static final float VOICE_SENT_STATUS_SECONDS = 1.15f;
    private static final float INPUT_CARET_HALF_PERIOD_SECONDS = 0.50f;
    private static final int CUSTOM_BOT_VISIBLE_ROWS = 5;
    private static final float CUSTOM_BOT_ROW_HEIGHT = 94f;
    private static final float CUSTOM_BOT_ROW_STRIDE = 105f;
    private static final float CUSTOM_BOT_ROWS_BOTTOM = 278f;
    private static final float CUSTOM_BOT_ROWS_TOP = 792f;
    private static final float COMPOSER_EMOJI_SIZE = 32f;
    private static final float COMPOSER_EMOJI_ADVANCE = 36f;
    private static final float LOBBY_CHAT_EMOJI_SIZE = 27f;
    private static final float LOBBY_CHAT_EMOJI_ADVANCE = 31f;
    private static final float LOBBY_CHAT_BUBBLE_PADDING = 14f;
    private static final float LOBBY_IMAGE_BUBBLE_WIDTH = 430f;
    private static final float LOBBY_IMAGE_BUBBLE_HEIGHT = 280f;
    private static final float LOBBY_IMAGE_VERTICAL_INSET = 55f;
    private static final float LOBBY_CHAT_HEADER_AVATAR_OFFSET = 38f;
    private static final float LOBBY_VIDEO_LINK_TITLE_INSET = 37f;
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
    static final float STATS_SYNC_ROW_Y = 965f;
    static final float STATS_SYNC_RECEIVE_X = 560f;
    static final float STATS_SYNC_RECEIVE_WIDTH = 375f;
    static final float STATS_SYNC_SHARE_GROUP_X = 950f;
    static final float STATS_SYNC_SHARE_GROUP_WIDTH = 630f;
    private static final float STATS_EVIDENCE_LINE_HEIGHT = 28f;
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
    static final float ABOUT_MUSIC_LINE_GAP = 22f;
    static final int ABOUT_MUSIC_CREDIT_COUNT = 6;
    static final float ABOUT_COPYRIGHT_Y = 306f;
    static final float ABOUT_COPYRIGHT_LINE_HEIGHT = 22f;
    static final int ABOUT_COPYRIGHT_MAX_LINES = 2;
    static final float ABOUT_CONTENT_WIDTH = 1060f;
    static final float HAND_GENERATOR_PANEL_X = 410f;
    static final float HAND_GENERATOR_PANEL_Y = 178f;
    static final float HAND_GENERATOR_PANEL_WIDTH = 1100f;
    static final float HAND_GENERATOR_PANEL_HEIGHT = 700f;
    static final float HAND_GENERATOR_CARD_AREA_INSET = 64f;
    static final float HAND_GENERATOR_CARD_GAP = 16f;
    static final float HAND_GENERATOR_CARD_WIDTH = 180f;
    static final float HAND_GENERATOR_CARD_ASPECT = 0.714f;
    static final float HAND_GENERATOR_CARD_HEIGHT =
            HAND_GENERATOR_CARD_WIDTH / HAND_GENERATOR_CARD_ASPECT;
    static final float HAND_GENERATOR_CARD_REGION_BOTTOM =
            HAND_GENERATOR_PANEL_Y + 114f;
    static final float HAND_GENERATOR_CARD_REGION_TOP =
            HAND_GENERATOR_PANEL_Y + HAND_GENERATOR_PANEL_HEIGHT - 266f;
    static final float HAND_GENERATOR_CARD_Y =
            (HAND_GENERATOR_CARD_REGION_BOTTOM
                    + HAND_GENERATOR_CARD_REGION_TOP
                    - HAND_GENERATOR_CARD_HEIGHT) / 2f;
    static final float HAND_GENERATOR_TITLE_BASELINE =
            HAND_GENERATOR_PANEL_Y + HAND_GENERATOR_PANEL_HEIGHT - 50f;
    static final float HAND_GENERATOR_NAME_Y =
            HAND_GENERATOR_PANEL_Y + HAND_GENERATOR_PANEL_HEIGHT - 218f;
    static final float HAND_GENERATOR_NAME_HEIGHT = 64f;
    static final URI ABOUT_PROJECT_URI = URI.create(
            "https://github.com/tonikelope/coronapoker");
    private static final Color AUDIO_UNAVAILABLE_RED =
            new Color(0xef3340ff);

    private final FitViewport viewport = new FitViewport(WIDTH, HEIGHT);
    private final Rectangle footerSound = mainMenuSoundBounds();
    private final Rectangle footerSoundHit = new Rectangle(footerSound.x - 8f,
            footerSound.y - 6f, footerSound.width + 16f, footerSound.height + 12f);
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
    private TooltipHit dropdownOptionTooltip;
    private final GdxTooltipDelay tooltipDelay = new GdxTooltipDelay();
    private final Map<String, Texture> lobbyAvatarTextures = new HashMap<>();
    private final Map<String, Texture> handGeneratorCardTextures =
            new HashMap<>();
    private final Map<String, Texture> customBotAvatarTextures =
            new HashMap<>();
    private final List<UiImageItem> customBotManagerImages =
            new ArrayList<>();
    private final List<UiImageItem> customBotManagerListImages =
            new ArrayList<>();
    private final List<TextItem> customBotManagerListTexts =
            new ArrayList<>();
    private final List<CustomBotProfile> customBotProfiles =
            new ArrayList<>();
    private final Color customBotSelectionBorder = new Color();
    private GdxEmojiAtlas emojiAtlas;
    private final Map<Long, LobbyMedia> lobbyMedia = new HashMap<>();
    private StreamingGifTextureAnimation.FrameSize lobbyGifBounds;
    private StreamingGifTextureAnimation.FrameSize pendingLobbyGifBounds;
    private long lobbyGifResizeAtNanos;
    private final GdxImageGalleryWindow lobbyGallery = new GdxImageGalleryWindow();
    private final GdxRobertRulesWindow rulesWindow = new GdxRobertRulesWindow();
    private final Map<String, Float> toggleAnimations = new HashMap<>();
    private final GdxToggleTransitionStore settingsToggleTransitions =
            new GdxToggleTransitionStore();
    private final Map<String, Float> hoverAnimations = new HashMap<>();
    private final GlyphLayout glyph = new GlyphLayout();
    private final Vector2 pointer = new Vector2();
    private final Matrix4 pixelProjection = new Matrix4();
    private final GdxTextEditState textEdit = new GdxTextEditState();
    private final GdxTextKeyRepeat textKeyRepeat = new GdxTextKeyRepeat();
    private final GdxVolumeKeyRepeat volumeKeyRepeat = new GdxVolumeKeyRepeat();
    private final GdxKeyRepeat pointerRepeat = new GdxKeyRepeat();
    private final Properties initialProperties;
    private final PreferencesService preferences;
    private final IdentityTrustStore identityTrust;
    private final SecureRandom secureRandom;
    private final GdxMenuQuotes menuQuotes;
    private final GdxAudioControl audioControl;
    private final GdxShortcutBindings shortcutBindings;
    private final GdxGamePresentationSettings presentationSettings;
    private final PokerVideoOverlay pokerVideoOverlay;
    private final GdxGameText gameText;
    private final Consumer<String> languageChanged;
    private final UpdateService updateService;
    private final UpdaterService updaterService;
    private final NewGameSubmissionCoordinator submissions;
    private final RecoverableGameRepository recoverableGames;
    private final StatsRepository statsRepository;
    private static final Rectangle STATS_WEB_BOUNDS =
            new Rectangle(76f, 60f, 1768f, 875f);
    private PokerStatsBrowser statsBrowser;
    private boolean statsBrowserFocused;
    private final GdxFloatingWindowPointer statsBrowserPointer = new GdxFloatingWindowPointer();
    private final ExecutorService recoveryExecutor;
    private final ExecutorService statsExecutor;
    private final ExecutorService networkInfoExecutor;
    private final ExecutorService upnpExecutor;
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
    private TextureRegion lobbyConnectionCopyIcon;
    private TextureRegion lobbyImageIcon;
    private TextureRegion lobbyVoiceIcon;
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
    private Music customBotManagerMusic;
    private ShaderProgram avatarShader;
    private ShaderProgram roundedTextureShader;
    private GdxModalBackdropBlur modalBackdropBlur;
    private GdxModalBackdropBlur nestedModalBackdropBlur;
    private boolean lobbyLoadingBackdropCached;
    private BitmapFont titleFont;
    private BitmapFont headingFont;
    private BitmapFont settingsStepperSignFont;
    private BitmapFont actionFont;
    private BitmapFont buttonMediumFont;
    private BitmapFont buttonCompactFont;
    private BitmapFont buttonTinyFont;
    private BitmapFont uiFont;
    private BitmapFont volumeOverlayFont;
    private BitmapFont fpsFont;
    private final GdxProcessPerformanceSampler performanceSampler =
            new GdxProcessPerformanceSampler();
    private BitmapFont debugLogFont;
    private BitmapFont debugLogBoldFont;
    private BitmapFont smallFont;
    private BitmapFont lobbyComposerFont;
    private BitmapFont tinyFont;
    private BitmapFont versionFont;
    private GdxCrispButtonText.FontTiers actionButtonFontTiers;
    private GdxCrispButtonText.FontTiers compactButtonFontTiers;
    private final Map<BitmapFont, GdxCrispButtonText.FontTiers>
            nativeTextTiers = new IdentityHashMap<>();
    private final GdxCrispButtonText.Result crispButtonText =
            new GdxCrispButtonText.Result();
    private GdxAdaptiveFontQuality.Profile fontQualityProfile;
    private GdxAdaptiveFontQuality.Profile pendingFontQualityProfile;
    private long fontQualityRefreshAfterNanos;
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
    private boolean upnpOpenInFlight;
    private boolean submitAfterUpnpOpen;
    private long upnpOpenGeneration;
    private int verifiedUpnpPort = -1;
    private UpnpPortReservation upnpReservation;
    private String frontendNoticeTitle = "";
    private String frontendNoticeMessage = "";
    private boolean frontendNoticeError;
    private boolean modAdultWarning;
    private boolean avatarSelectionPending;
    private AvatarCropSession avatarCropSession;
    private final Rectangle avatarCropViewport = new Rectangle();
    private final Rectangle avatarCropImageBounds = new Rectangle();
    private final Rectangle avatarCropSelectionBounds = new Rectangle();
    private final Vector2 avatarCropSource = new Vector2();
    private GdxAvatarCropModel.DragTarget avatarCropDrag
            = GdxAvatarCropModel.DragTarget.NONE;
    private Cursor.SystemCursor appliedSystemCursor;
    private int avatarCropPointer = -1;
    private float avatarCropLastX;
    private float avatarCropLastY;
    private GdxAvatarCropModel.Crop avatarCropResizeStart;
    private float avatarCropResizeStartSourceX;
    private float avatarCropResizeStartSourceY;
    private boolean customBotManagerOpen;
    private boolean customBotAvatarSelectionPending;
    private boolean customBotDeleteConfirmation;
    private boolean customBotRandomSelection;
    private boolean customBotRowsActive;
    private int customBotSelected = -1;
    private int customBotNewSlotSelected = -1;
    private float customBotScroll;
    private float customBotScrollTarget;
    private float customBotScrollMaximum;
    private final Rectangle customBotScrollTrack = new Rectangle();
    private final Rectangle customBotRowsViewport = new Rectangle();
    private float customBotScrollThumbHeight;
    private String customBotNameDraft = "";
    private Path customBotAvatarDraft;
    private NewGameTableDraft.BotDifficulty customBotDifficultyDraft
            = NewGameTableDraft.BotDifficulty.MEDIUM;
    private CustomBotProfile.Personality customBotPersonalityDraft
            = CustomBotProfile.Personality.RANDOM;
    private boolean customBotAutomaticRebuyDraft = true;
    private String customBotValidationError = "";
    private LobbySession lobbySession;
    private LobbySnapshot lobby;
    private GdxNetworkBlockCache networkBlockCache;
    private AutoCloseable lobbySubscription;
    private String lobbyChatDraft = "";
    private final GdxSentChatHistory lobbyChatHistory = new GdxSentChatHistory();
    private long selectedLobbyChatSequence = -1L;
    private float lobbyTextSendAllowedAt;
    private float lobbyChatScroll;
    private int lobbyChatMessageCount;
    private float lobbyChatContentHeight;
    private LobbyChatLayout lobbyChatLayout;
    private final Rectangle lobbyChatScrollTrack = new Rectangle();
    private final Rectangle lobbyChatViewport = new Rectangle();
    private float lobbyChatScrollThumbHeight;
    private float lobbyChatScrollMaximum;
    private float lobbyImageSendAllowedAt;
    private boolean lobbyEmojiPickerOpen;
    private int lobbyEmojiPage;
    private volatile GdxVoiceRecorder lobbyVoiceRecorder;
    private boolean lobbyVoiceOpening;
    private boolean lobbyVoiceLive;
    private boolean lobbyVoiceStopping;
    private final GdxPointerHold lobbyVoicePointer = new GdxPointerHold();
    private float lobbyVoiceLiveAt;
    private String lobbyVoiceStatus = "";
    private float lobbyVoiceStatusAt;
    private float lobbyVoiceStatusSeconds = VOICE_STATUS_SECONDS;
    private long lobbyChatVoiceSequence = -1L;
    private boolean lobbyChatVoicePaused;
    private long lastLobbyMediaSequence = -1L;
    private FingerprintDialog fingerprintDialog;
    private boolean lobbyCommandPending;
    private boolean lobbyGameStarting;
    private LobbyConfirmation lobbyConfirmation;
    private LobbyParticipant lobbyKickTarget;
    private final ArrayDeque<String> identityChangeNotices =
            new ArrayDeque<>();
    private boolean lobbyPasswordDialog;
    private String lobbyPasswordDraft = "";
    private boolean networkBlocksOpen;
    private float networkBlocksScroll;
    private NetworkBlock networkBlockUnblockConfirmation;
    private String networkBlockPendingAddress;
    private String networkBlockError = "";
    private String lobbyLocalAddress = GdxLocalServerAddresses.LOOPBACK_NAME;
    private String lobbyPublicAddress = "";
    private String cachedLobbyPublicAddress = "";
    private boolean lobbyPublicAddressLoading;
    private long lobbyPublicAddressGeneration;
    private String lastLobbyNoticeIdentity = "";
    private PresetDialog presetDialog = PresetDialog.NONE;
    private final GdxBlindStructureEditor blindStructureEditor =
            new GdxBlindStructureEditor();
    private BlindStructureDialog blindStructureDialog =
            BlindStructureDialog.NONE;
    private String blindStructureNameDraft = "";
    private String initialBuyinInput;
    private boolean blindStructureSettingsTarget;
    private List<GamePresetCatalog.Entry> gamePresets = List.of();
    private int selectedGamePreset = -1;
    private Dropdown dropdown = Dropdown.NONE;
    private int dropdownScroll;
    private final Rectangle dropdownAnchor = new Rectangle();
    private final Rectangle dropdownClip = new Rectangle();
    private final Rectangle dropdownScrollTrack = new Rectangle();
    private float dropdownScrollThumbHeight;
    private int dropdownScrollMaximum;
    private boolean dropdownAnchored;
    private GdxAppearanceOptions.Choice dropdownAppearanceChoice;
    private List<String> hostAddressOptions =
            List.of(GdxLocalServerAddresses.LOOPBACK_NAME);
    private int hostAddressLoadGeneration;
    private String presetNameDraft = "";
    private Surface settingsReturnSurface = Surface.MENU;
    private boolean settingsBackupPending;
    private ProfileBackupSelection profileBackupSelection;

    private record ProfileBackupSelection(boolean importing, Path archive,
            Set<GdxProfileBackup.Category> available, EnumSet<GdxProfileBackup.Category> selected) {

        ProfileBackupSelection(boolean importing, Path archive,
                Set<GdxProfileBackup.Category> categories) {
            this(importing, archive, EnumSet.copyOf(categories), EnumSet.copyOf(categories));
        }

        String actionKey() {
            return importing ? "gdx.backup.import" : "gdx.backup.export";
        }
    }
    private final GdxSettingsSession settingsSession =
            new GdxSettingsSession();
    private int settingsAppearancePage;
    private float settingsAppearanceScroll;
    private int settingsAudioPage;
    private float settingsAudioScroll;
    private int settingsGamePage;
    private float settingsGameScroll;
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
    private final GdxPointerHold scrollPointer = new GdxPointerHold();
    private float scrollDragGrabOffset;
    private String settingsShortcutCaptureId;
    private GdxTableDialog settingsAutoCallDialog;
    private boolean settingsAutoRebuySnapshot;
    private boolean settingsStraddleTimeoutDraft;
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
    private CompletableFuture<UpdateService.CheckResult> updateCheck;
    private boolean updateInstalling;
    private UpdateService.CheckResult updateResult;
    private boolean updatePromptOpen;
    private boolean updatePromptDismissed;
    private boolean updatePromptForMod;
    private boolean updateReturnToAbout;
    private boolean applicationExitConfirmation;
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
    private float statsSummaryPlayersScrollMaximum;
    private final Rectangle statsSummaryPlayersViewport = new Rectangle();
    private final Rectangle statsSummaryPlayersScrollTrack = new Rectangle();
    private float statsSummaryPlayersScrollThumbHeight;
    private boolean statsEvidenceOpen;
    private boolean statsEvidenceLoading;
    private String statsEvidenceError = "";
    private List<StatsRepository.ZeroTrustEvidence> statsEvidence = List.of();
    private long statsEvidenceGeneration;
    private final Rectangle statsEvidencePanelBounds = new Rectangle();
    private final Rectangle statsEvidenceDragStartBounds = new Rectangle();
    private final GdxPointerHold statsEvidenceWindowPointer =
            new GdxPointerHold();
    private StatsEvidenceWindowDrag statsEvidenceWindowDrag =
            StatsEvidenceWindowDrag.NONE;
    private GdxFloatingGameLogLayout.ResizeHandle statsEvidenceResizeHandle =
            GdxFloatingGameLogLayout.ResizeHandle.NONE;
    private float statsEvidenceDragStartX;
    private float statsEvidenceDragStartY;
    private float statsEvidenceScroll;
    private float statsEvidenceScrollTarget;
    private float statsEvidenceScrollMaximum;
    private float statsEvidenceScrollThumbHeight;
    private List<String> statsEvidenceLines = List.of();
    private String statsEvidenceSourceCache = "";
    private float statsEvidenceWrapWidth = -1f;
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
        pokerVideoOverlay = new PokerVideoOverlay(
                preferenceBoolean(GdxSettingsContract.POKER_VIDEO_AUTO_OPEN_KEY,
                        GdxSettingsContract.DEFAULT_POKER_VIDEO_AUTO_OPEN), type -> {
            initialProperties.setProperty(GdxSettingsContract.POKER_VIDEO_TYPE_KEY, type);
            this.preferences.saveDeferred();
        }, initialProperties, this.preferences::saveDeferred);
        customBotProfiles.addAll(
                CustomBotProfileCatalog.readFrom(initialProperties));
        customBotRandomSelection = CustomBotProfileCatalog
                .randomSelection(initialProperties);
        table = defaultTableDraft();
        if (forgetPersistedUpnp(initialProperties)) {
            // UPnP is explicit authority for one lobby, not a preference.
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
        upnpExecutor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task,
                    "CoronaPoker-GDX-UPnP-mapping");
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
        PokerChromium.useLanguage(gameText.language());
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
        lobbyConnectionCopyIcon = new TextureRegion(
                silhouetteTexture("images/menu/copy.png", Color.WHITE),
                3, 3, 18, 18);
        lobbyImageIcon = new TextureRegion(
                filteredTexture("images/fast_panel/image.png"));
        lobbyVoiceIcon = new TextureRegion(
                filteredTexture("images/fast_panel/mic.png"));
        emojiAtlas = GdxEmojiAtlas.retain();
        aboutMourningIcon = filteredTexture("images/luto.png");
        aboutBookIcon = silhouetteTexture(GdxRobertRulesWindow.ICON,
                new Color(0xeaf8ffff));
        aboutCrossIcon = filteredTexture("images/cruz.png");
        aboutModIcon = externalTexture(
                presentationSettings.modAsset("mod.png").orElse(null));
        soundEnabledCue = loadModAwareSound("misc/button_on.wav");
        soundDisabledCue = loadModAwareSound("misc/button_off.wav");
        participantJoinedCue = loadModAwareSound("misc/laser.wav");
        participantLeftCue = loadModAwareSound("misc/toilet.wav");
        backgroundMusic = music("sounds/misc/background_music.mp3", 0.40f);
        waitingRoomMusic = music("sounds/misc/waiting_room.mp3", 0.90f);
        aboutMusic = music("sounds/misc/about_music.mp3", 0.90f);
        statsMusic = music("sounds/misc/stats_music.mp3", 0.30f);
        customBotManagerMusic = music(
                "sounds/misc/bots_manager.mp3", 0.65f);
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
        modalBackdropBlur = new GdxModalBackdropBlur();
        nestedModalBackdropBlur = new GdxModalBackdropBlur();
        modAdultWarning = presentationSettings.modAdultWarningRequired();
        checkForUpdates();
        purgeExpiredVoiceNotes();
        rebuildFonts(GdxAdaptiveFontQuality.current(WIDTH, HEIGHT));
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
        return font(generator, size, border,
                GdxAdaptiveFontQuality.current(WIDTH, HEIGHT));
    }

    private static BitmapFont font(FreeTypeFontGenerator generator, int size,
            float border, GdxAdaptiveFontQuality.Profile profile) {
        return GdxAdaptiveFontQuality.generate(generator, size, border,
                Color.WHITE, new Color(0x02050ccc),
                FRONTEND_EXTRA_FONT_CHARACTERS,
                profile);
    }

    private void rebuildFonts(GdxAdaptiveFontQuality.Profile profile) {
        BitmapFont nextTitle;
        BitmapFont nextHeading;
        BitmapFont nextStepperSign;
        BitmapFont nextAction;
        BitmapFont nextButtonMedium;
        BitmapFont nextButtonCompact;
        BitmapFont nextButtonTiny;
        BitmapFont nextUi;
        BitmapFont nextVolume;
        BitmapFont nextSmall;
        BitmapFont nextLobbyComposer;
        BitmapFont nextTiny;
        BitmapFont nextVersion;
        BitmapFont nextFps;
        BitmapFont nextDebugLog;
        BitmapFont nextDebugLogBold;
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(
                Gdx.files.internal("fonts/McLaren-Regular.ttf"));
        try {
            nextTitle = font(generator, GdxSettingsStyle.TITLE_FONT_SIZE,
                    GdxSettingsStyle.TITLE_FONT_BORDER, profile);
            nextHeading = font(generator, GdxSettingsStyle.HEADING_FONT_SIZE,
                    GdxSettingsStyle.HEADING_FONT_BORDER, profile);
            nextStepperSign = font(generator,
                    GdxSettingsStyle.STEPPER_SIGN_FONT_SIZE,
                    GdxSettingsStyle.STEPPER_SIGN_FONT_BORDER, profile);
            nextAction = font(generator, GdxSettingsStyle.ACTION_FONT_SIZE,
                    GdxSettingsStyle.ACTION_FONT_BORDER, profile);
            // Native two-pixel steps keep long button labels sharp. They are
            // selected instead of scaling one atlas to an arbitrary fraction.
            nextButtonMedium = font(generator, 22,
                    GdxSettingsStyle.ACTION_FONT_BORDER, profile);
            nextButtonCompact = font(generator, 20,
                    GdxSettingsStyle.ACTION_FONT_BORDER, profile);
            nextButtonTiny = font(generator, 16, 0f, profile);
            nextUi = font(generator, GdxSettingsStyle.BODY_FONT_SIZE, 0f,
                    profile);
            // The volume feedback remains the same high-contrast McLaren face;
            // only its backing atlas density follows the physical framebuffer.
            nextVolume = font(generator, GdxVolumeOverlayStyle.FONT_SIZE,
                    GdxVolumeOverlayStyle.FONT_BORDER, profile);
            nextSmall = font(generator, GdxSettingsStyle.SMALL_FONT_SIZE, 0f,
                    profile);
            nextLobbyComposer = font(generator,
                    CoronaPokerGdxTable.QUICK_CHAT_INPUT_FONT_SIZE,
                    CoronaPokerGdxTable.QUICK_CHAT_INPUT_FONT_BORDER, profile);
            nextTiny = font(generator, GdxSettingsStyle.TINY_FONT_SIZE, 0f,
                    profile);
            nextVersion = font(generator, GdxProductVersionBrand.FONT_SIZE,
                    0f, profile);
        } finally {
            generator.dispose();
        }
        nextFps = GdxFrameRateOverlayStyle.font(profile);
        generator = GdxMonospaceFonts.generator(false);
        try {
            nextDebugLog = font(generator, 20, 0f, profile);
        } finally {
            generator.dispose();
        }
        generator = GdxMonospaceFonts.generator(true);
        try {
            nextDebugLogBold = font(generator, 20, 0f, profile);
        } finally {
            generator.dispose();
        }
        disposeFont(titleFont);
        disposeFont(headingFont);
        disposeFont(settingsStepperSignFont);
        disposeFont(actionFont);
        disposeFont(buttonMediumFont);
        disposeFont(buttonCompactFont);
        disposeFont(buttonTinyFont);
        disposeFont(uiFont);
        disposeFont(volumeOverlayFont);
        disposeFont(smallFont);
        disposeFont(lobbyComposerFont);
        disposeFont(tinyFont);
        disposeFont(versionFont);
        disposeFont(fpsFont);
        disposeFont(debugLogFont);
        disposeFont(debugLogBoldFont);
        titleFont = nextTitle;
        headingFont = nextHeading;
        settingsStepperSignFont = nextStepperSign;
        actionFont = nextAction;
        buttonMediumFont = nextButtonMedium;
        buttonCompactFont = nextButtonCompact;
        buttonTinyFont = nextButtonTiny;
        uiFont = nextUi;
        volumeOverlayFont = nextVolume;
        smallFont = nextSmall;
        lobbyComposerFont = nextLobbyComposer;
        tinyFont = nextTiny;
        lobbyChatLayout = null;
        versionFont = nextVersion;
        fpsFont = nextFps;
        debugLogFont = nextDebugLog;
        debugLogBoldFont = nextDebugLogBold;
        actionButtonFontTiers = new GdxCrispButtonText.FontTiers(
                actionFont, uiFont, buttonMediumFont, buttonCompactFont,
                smallFont, buttonTinyFont, tinyFont, versionFont);
        compactButtonFontTiers = new GdxCrispButtonText.FontTiers(
                smallFont, buttonTinyFont, tinyFont, versionFont);
        rebuildNativeTextTiers();
        fontQualityProfile = profile;
        pendingFontQualityProfile = null;
    }

    private static void disposeFont(BitmapFont font) {
        if (font != null) font.dispose();
    }

    /** Native FreeType sizes shared by every non-table surface. */
    private void rebuildNativeTextTiers() {
        nativeTextTiers.clear();
        BitmapFont[] standard = {
            titleFont, settingsStepperSignFont, volumeOverlayFont,
            headingFont, actionFont, uiFont, buttonMediumFont,
            buttonCompactFont, smallFont, buttonTinyFont, tinyFont,
            versionFont
        };
        nativeTextTiers.put(lobbyComposerFont,
                new GdxCrispButtonText.FontTiers(lobbyComposerFont, smallFont,
                        buttonTinyFont, tinyFont, versionFont));
        for (BitmapFont font : standard) {
            if (font != null) {
                nativeTextTiers.put(font,
                        GdxCrispButtonText.FontTiers.atOrBelow(font,
                                standard));
            }
        }
        registerNativeTextFamily(fpsFont);
        registerNativeTextFamily(debugLogFont);
        registerNativeTextFamily(debugLogBoldFont);
    }

    private void registerNativeTextFamily(BitmapFont preferred,
            BitmapFont... smaller) {
        if (preferred == null) return;
        nativeTextTiers.put(preferred,
                GdxCrispButtonText.FontTiers.atOrBelow(preferred, smaller));
    }

    private GdxCrispButtonText.FontTiers nativeTextTiers(BitmapFont font) {
        GdxCrispButtonText.FontTiers tiers = nativeTextTiers.get(font);
        return tiers != null ? tiers
                : new GdxCrispButtonText.FontTiers(font);
    }

    private void refreshFontQualityIfDue() {
        if (pendingFontQualityProfile == null
                || System.nanoTime() < fontQualityRefreshAfterNanos) return;
        GdxAdaptiveFontQuality.Profile current =
                GdxAdaptiveFontQuality.current(WIDTH, HEIGHT);
        pendingFontQualityProfile = null;
        if (!current.equals(fontQualityProfile)) rebuildFonts(current);
    }

    /**
     * Pointer events belong to the currently installed input processor. When
     * the table hands input back to the persistent frontend, a stationary
     * cursor does not emit a new mouseMoved event. Reading the device every
     * frame keeps all hover materials correct across scene hand-offs too.
     */
    private void refreshPointerFromDevice() {
        pointer.set(Gdx.input.getX(), Gdx.input.getY());
        viewport.unproject(pointer);
    }

    /** Establishes the frontend's OpenGL baseline after another scene. */
    private static void prepareFrontendGraphicsState() {
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    /** Restores every shared renderer after a framebuffer blur pass. */
    private void restoreDefaultFrontendRenderState() {
        prepareFrontendGraphicsState();
        viewport.apply();
        viewport.getCamera().update();
        shapes.setProjectionMatrix(viewport.getCamera().combined);
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.setShader(null);
        batch.setColor(Color.WHITE);
    }

    /**
     * Surface-local hover animation must not leak into the next page even if
     * two buttons happen to share a label and geometry.
     */
    private void activateSurface(Surface next) {
        Objects.requireNonNull(next, "next");
        if (surface != next) {
            if (surface == Surface.LOBBY) lobbyGallery.close();
            rulesWindow.close();
            hoverAnimations.clear();
            if (initialBuyinInput != null) closeInitialBuyinEditor();
        }
        surface = next;
        pressedHit = null;
        pointerSelectionField = null;
        clearScrollDrag();
        clearPointerRepeat();
        refreshPointerFromDevice();
    }

    /**
     * Reclaims the persistent frontend after a different scene owned input and
     * OpenGL state.  Every table/startup/final-summary hand-off enters through
     * this boundary so no pressed, hover or clipping state can survive merely
     * because the pointer remained stationary while the scene changed.
     */
    void reclaimAfterExternalScene() {
        captureFrontendModalInput();
        resetMediaInput();
        hoverAnimations.clear();
        refreshPointerFromDevice();
        appliedSystemCursor = null;
        prepareFrontendGraphicsState();
    }

    @Override
    public void render() {
        GdxPixelText.beginFrame();
        refreshFontQualityIfDue();
        frameDelta = Math.min(Gdx.graphics.getDeltaTime(), 1f / 20f);
        refreshPointerFromDevice();
        prepareFrontendGraphicsState();
        elapsed += frameDelta;
        if (networkBlockCache != null) {
            networkBlockCache.update(frameDelta,
                    lobby != null && lobby.host());
        }
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
        statsEvidenceScroll += (statsEvidenceScrollTarget
                - statsEvidenceScroll) * Math.min(1f, frameDelta * 14f);
        if (Math.abs(statsEvidenceScrollTarget
                - statsEvidenceScroll) < 0.1f) {
            statsEvidenceScroll = statsEvidenceScrollTarget;
        }
        customBotScroll += (customBotScrollTarget - customBotScroll)
                * Math.min(1f, frameDelta * 14f);
        if (Math.abs(customBotScrollTarget - customBotScroll) < 0.1f) {
            customBotScroll = customBotScrollTarget;
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
        int volumeSteps = volumeKeyRepeat.update(frameDelta, shortcutBindings,
                fileChooserPending() || settingsShortcutCaptureId != null);
        if (volumeSteps != 0) {
            adjustFrontendMasterVolume(GdxSettingsContract.MASTER_VOLUME_STEP * volumeSteps, false);
        }
        updateTextKeyRepeat();
        updatePointerRepeat();
        updateLobbyVoiceRecording();
        audioPreview.update(frameDelta);
        syncMusicForSurface();
        boolean lobbyLoadingOverlay = lobbyLoadingOverlayVisible();
        updatePokerVideo((surface == Surface.MENU || surface == Surface.LOBBY)
                && !lobbyLoadingOverlay && !hasBlockingFrontendModal(), frameDelta,
                surface == Surface.LOBBY && lobbySession != null
                        ? this::shareLobbyVideo : null);
        updateAvatarCropSystemCursor();
        boolean backdropBlurActive = GdxVisualEffects.blurActive();
        if (!lobbyLoadingOverlay || !backdropBlurActive) {
            lobbyLoadingBackdropCached = false;
        } else if (backdropBlurActive && lobbyLoadingBackdropCached
                && modalBackdropBlur.hasCachedFrame(
                        Gdx.graphics.getBackBufferWidth(),
                        Gdx.graphics.getBackBufferHeight())) {
            renderCachedLobbyLoadingFrame();
            return;
        }
        boolean settingsRootModal = isSettingsRootModal();
        boolean rootSurfaceModal = isFrontendRootModal();
        boolean lobbyRootBackdrop = rootSurfaceModal && lobby != null
                && (settingsRootModal
                        && settingsReturnSurface == Surface.LOBBY
                    || surface == Surface.SCREENSHOTS
                        && screenshotReturnSurface == Surface.LOBBY);
        boolean wantsModalBackdrop = hasVisibleFrontendModal();
        boolean blurModalBackdrop = wantsModalBackdrop
                && backdropBlurActive;
        boolean nestedFrontendModal = hasNestedFrontendModal();
        float rulesPointerX = pointer.x;
        float rulesPointerY = pointer.y;
        if (rulesWindow.isOpen()) pointer.set(-10_000f, -10_000f);
        if (blurModalBackdrop) {
            modalBackdropBlur.beginCapture(Gdx.graphics.getBackBufferWidth(),
                    Gdx.graphics.getBackBufferHeight());
        }
        ScreenUtils.clear(BACKGROUND);
        viewport.apply();
        if (blurModalBackdrop) {
            Gdx.gl.glViewport(0, 0, modalBackdropBlur.captureWidth(),
                    modalBackdropBlur.captureHeight());
        }
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
        customBotManagerListTexts.clear();
        customBotManagerListImages.clear();
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
        boolean galleryInteractive = lobbyGalleryInteractive();
        boolean galleryOwnsHover = galleryInteractive && lobbyGallery.ownsPointer(pointer.x, pointer.y);
        boolean videoOwnsHover = !galleryOwnsHover && pokerVideoOverlay.ownsHover(pointer.x, pointer.y);
        boolean shieldBackgroundPointer = rootSurfaceModal
                || hasBlockingFrontendModal() || videoOwnsHover || galleryOwnsHover;
        float livePointerX = pointer.x;
        float livePointerY = pointer.y;
        if (shieldBackgroundPointer) pointer.set(-10_000f, -10_000f);

        drawFeltBackground();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (rootSurfaceModal) {
            drawRootModalBackdropSurface();
        } else if (surface == Surface.MENU) {
            if (applicationExitConfirmation
                    || renderMainMenuContent(aboutOpen, updatePromptOpen)) {
                drawMainMenu();
            }
        } else if (surface == Surface.LOBBY) {
            drawLobby();
        } else if (surface == Surface.SETTINGS) {
            // A child confirmation belongs above Settings, so Settings itself
            // is the scene captured and blurred in this case.
            drawSettingsScreen();
        } else if (surface == Surface.STATS) {
            drawStatsScreen();
        } else if (surface == Surface.SCREENSHOTS) {
            drawScreenshotViewer();
        } else {
            drawNewGameScreen();
        }
        shapes.end();

        batch.begin();
        drawLobbyAvatarsInCurrentBatch();
        drawUiImagesInCurrentBatch();
        for (TextItem item : texts) {
            drawTextItem(item);
        }
        batch.end();

        if (shieldBackgroundPointer && !videoOwnsHover && !galleryOwnsHover) {
            pointer.set(livePointerX, livePointerY);
        }

        drawSettingsRowsTextLayer();
        drawStatsChartTextLayer();
        drawLobbyChatLayer(lobbyRootBackdrop);
        drawSettingsDebugLayer();
        if (videoOwnsHover) {
            tooltipHits.clear();
            pointer.set(livePointerX, livePointerY);
        }
        if (galleryOwnsHover) pokerVideoOverlay.mouseMoved(-10_000f, -10_000f);
        drawPokerVideoTopLayer();

        if (surface == Surface.LOBBY && lobbyGallery.isOpen()) {
            if (galleryOwnsHover) {
                tooltipHits.clear();
                pointer.set(livePointerX, livePointerY);
            }
            if (!galleryInteractive) lobbyGallery.mouseMoved(-10_000f, -10_000f);
            lobbyGallery.draw(shapes, batch, volumeOverlayFont, smallFont, gameText,
                    galleryInteractive ? pointer.x : -10_000f,
                    galleryInteractive ? pointer.y : -10_000f);
        }
        drawStartupMenuReveal();

        if (blurModalBackdrop) {
            try {
                if (lobbyLoadingOverlay) {
                    modalBackdropBlur.endCaptureAndCache(viewport, batch,
                            viewport.getCamera().combined, BACKGROUND);
                    lobbyLoadingBackdropCached = true;
                } else if (nestedFrontendModal) {
                    // Keep the first modal level as a reusable
                    // native-resolution image. The nested pass adds the
                    // parent dialog and applies a second Gaussian blur before
                    // the child popup is composited.
                    modalBackdropBlur.endCaptureAndCache(viewport, batch,
                            viewport.getCamera().combined, BACKGROUND);
                } else {
                    modalBackdropBlur.endCaptureAndDraw(viewport, batch,
                            viewport.getCamera().combined, BACKGROUND);
                }
            } finally {
                // Keep the live backdrop, but never inherit framebuffer,
                // viewport, shader or tint state from its blur passes. The
                // table already applies this same restoration contract.
                restoreDefaultFrontendRenderState();
            }
            drawUnifiedModalBackdrop();
        } else if (wantsModalBackdrop) {
            drawUnifiedModalBackdrop();
        }

        // Modal surfaces must be composed after every underlying glyph. Texts
        // are batched separately from shapes, so drawing the modal inside
        // drawLobby would otherwise let the lobby chat glyphs bleed over it.
        if (nestedFrontendModal) {
            renderNestedFrontendModal();
        } else if (hasVisibleFrontendModal()) {
            texts.clear();
            if (rootSurfaceModal) {
                lobbyAvatars.clear();
                uiImages.clear();
                settingsRowTexts.clear();
                settingsDebugTexts.clear();
                statsChartTexts.clear();
                settingsRowsClip.set(0f, 0f, 0f, 0f);
                settingsDebugViewport.set(0f, 0f, 0f, 0f);
                statsChartViewport.set(0f, 0f, 0f, 0f);
                statsChartHorizontalTrack.set(0f, 0f, 0f, 0f);
                statsChartVerticalTrack.set(0f, 0f, 0f, 0f);
                statsChartCanvasActive = false;
            }
            // A visual modal must also own the complete interaction map.
            // Keeping the underlying page hits allowed invisible lobby/menu
            // controls to fire through confirmations and the table-loading
            // overlay.
            hits.clear();
            secondaryHits.clear();
            textFieldHits.clear();
            passwordRevealHits.clear();
            editMenuHits.clear();
            tooltipHits.clear();
            // Dropdowns are painted in a final top layer. The covered sharp
            // modal must therefore be rendered with an off-screen pointer as
            // well as an empty interaction map; otherwise its hover/pressed
            // material remains visible around the opaque popup.
            boolean shieldDropdownPointer = dropdown != Dropdown.NONE;
            float dropdownPointerX = pointer.x;
            float dropdownPointerY = pointer.y;
            if (shieldDropdownPointer) {
                pointer.set(-10_000f, -10_000f);
            }
            // SpriteBatch changes the current OpenGL pipeline. Restore alpha
            // blending before composing the modal shape pass; otherwise the
            // glass highlights/shadows (and the dimmer itself) become fully
            // opaque white/black bars on some drivers.
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA,
                    GL20.GL_ONE_MINUS_SRC_ALPHA);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            if (applicationExitConfirmation) {
                drawApplicationExitConfirmation();
            } else if (avatarCropSession != null) {
                drawAvatarCropDialog();
            } else if (modAdultWarning) {
                drawModAdultWarning();
            } else if (!frontendNoticeMessage.isBlank()) {
                drawFrontendNotice();
            } else if (settingsRestartNotice) {
                drawSettingsRestartNotice();
            } else if (updateCheckingModalVisible()) {
                drawUpdateCheckingDialog();
            } else if (aboutOpen) {
                drawAboutDialog();
            } else if (updatePromptOpen) {
                drawUpdateDialog();
            } else if (blindStructureDialog != BlindStructureDialog.NONE) {
                drawBlindStructureDialog();
            } else if (initialBuyinInput != null) {
                drawInitialBuyinEditor();
            } else if (voiceNotesOpen) {
                drawVoiceNotesDialog();
            } else if (rootSurfaceModal) {
                drawRootModalSurface();
            } else if (customBotManagerOpen) {
                // This child confirmation is composed later so it stays sharp.
                // Paint the covered manager with an off-screen pointer too:
                // clearing its hit map blocks clicks, but cannot undo hover
                // feedback that was already drawn in the same frame.
                float managerPointerX = pointer.x;
                float managerPointerY = pointer.y;
                if (customBotDeleteConfirmation) {
                    pointer.set(-10_000f, -10_000f);
                }
                drawCustomBotManagerDialog();
                if (customBotDeleteConfirmation) {
                    pointer.set(managerPointerX, managerPointerY);
                }
            } else if (surface == Surface.SETTINGS) {
                if (profileBackupSelection != null) {
                    drawProfileBackupSelection(profileBackupSelection);
                } else if (settingsAutoCallDialog != null) {
                    drawSettingsAutoCallDialog();
                } else {
                    drawSettingsDiscardConfirmation();
                }
            } else if (surface == Surface.NEW_GAME) {
                if (upnpOpenInFlight) {
                    drawUpnpOpeningDialog();
                } else if (submissions.submitting()) {
                    drawNewGameSubmissionOverlay();
                } else if (dropdown != Dropdown.NONE) {
                    drawDropdownOverlay(false);
                } else if (blindStructureDialog != BlindStructureDialog.NONE) {
                    drawBlindStructureDialog();
                } else {
                    drawPresetDialog();
                }
            } else if (surface == Surface.STATS) {
                if (statsEvidenceOpen) {
                    drawStatsEvidenceDialog();
                } else if (statsSyncExclusionsOpen) {
                    drawStatsSyncExclusions();
                } else if (statsConfirmation != StatsConfirmation.NONE) {
                    drawStatsConfirmation();
                } else {
                    drawStatsPicker();
                }
            } else if (surface == Surface.SCREENSHOTS) {
                drawScreenshotDeleteConfirmation();
            } else if (networkBlocksOpen) {
                if (networkBlockUnblockConfirmation != null) {
                    drawNetworkBlockUnblockConfirmation();
                } else {
                    drawNetworkBlocksDialog();
                }
            } else if (lobbyPasswordDialog) {
                drawLobbyPasswordDialog();
            } else if (fingerprintDialog != null) {
                drawFingerprintDialog();
            } else if (!identityChangeNotices.isEmpty()) {
                drawIdentityChangeNotice();
            } else if (lobbyConfirmation != null) {
                drawLobbyConfirmation();
            } else {
                drawLobbyLoadingOverlay();
            }
            if (dropdown != Dropdown.NONE
                    && (isSettingsRootModal()
                            || blindStructureDialog
                                    == BlindStructureDialog.EDITOR)) {
                drawDropdownOverlay(false);
            }
            shapes.end();
            if (shieldDropdownPointer) {
                pointer.set(dropdownPointerX, dropdownPointerY);
            }
            batch.begin();
            if (avatarCropSession != null) {
                drawAvatarCropPreview();
            } else if (customBotManagerOpen) {
                drawCustomBotManagerImages();
            } else if (rootSurfaceModal) {
                drawLobbyAvatarsInCurrentBatch();
                drawUiImagesInCurrentBatch();
            } else if (aboutOpen && handGeneratorOpen) {
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
            if (statsEvidenceOpen) {
                drawStatsEvidenceTextLayer();
            }
            if (customBotManagerOpen && avatarCropSession == null) {
                drawCustomBotManagerListLayer();
                drawCustomBotDeleteConfirmationTopLayer();
            }
            if (rootSurfaceModal && surface == Surface.SETTINGS) {
                drawSettingsRowsTextLayer();
                drawSettingsDebugLayer();
            }
            if (rootSurfaceModal && surface == Surface.STATS) {
                drawStatsChartTextLayer();
            }
        }
        // Dropdowns are sharp popup layers, not modals. Compose them after
        // either path above so the main-menu language selector is visible too;
        // its full-screen dismiss hit also prevents clicks reaching the menu.
        if (dropdown != Dropdown.NONE) {
            drawDropdownTopLayer();
        }
        if (shouldDrawEditMenu()) drawEditMenuTopLayer();
        drawFrontendTopLayers();
        if (rulesWindow.isOpen()) {
            pointer.set(rulesPointerX, rulesPointerY);
            rulesWindow.draw(shapes, batch, volumeOverlayFont, smallFont, gameText, pointer.x, pointer.y);
            tooltipDelay.clear();
        } else if (shouldDrawEditMenu()
                || surface == Surface.MENU && startupMenuWaitingForUpdate
                || surface == Surface.LOBBY && !hasBlockingFrontendModal()
                        && lobbyGallery.ownsPointer(pointer.x, pointer.y)) {
            tooltipDelay.clear();
        } else {
            drawTooltipTopLayer(dropdown == Dropdown.NONE ? null : dropdownClip);
        }
        drawCenteredFeedbackTopLayer();
    }

    /**
     * Reuses the waiting room image captured on the first preparation frame.
     * Only the indeterminate bar and sharp top layers change while resources
     * are created, avoiding a full lobby draw plus two blur passes every turn.
     */
    private void renderCachedLobbyLoadingFrame() {
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
        customBotManagerListTexts.clear();
        customBotManagerListImages.clear();
        statsChartTexts.clear();
        statsChartViewport.set(0f, 0f, 0f, 0f);
        statsChartHorizontalTrack.set(0f, 0f, 0f, 0f);
        statsChartVerticalTrack.set(0f, 0f, 0f, 0f);
        statsChartCanvasActive = false;
        settingsRowsClip.set(0f, 0f, 0f, 0f);
        settingsRowsActive = false;
        settingsDebugViewport.set(0f, 0f, 0f, 0f);
        lobbyChatViewport.set(0f, 0f, 0f, 0f);
        hits.clear();
        secondaryHits.clear();
        textFieldHits.clear();
        passwordRevealHits.clear();
        editMenuHits.clear();
        tooltipHits.clear();
        tooltipDelay.clear();

        modalBackdropBlur.drawCached(viewport, batch,
                viewport.getCamera().combined, BACKGROUND);
        drawUnifiedModalBackdrop();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA,
                GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawLobbyLoadingOverlay();
        shapes.end();
        batch.begin();
        for (TextItem item : texts) drawTextItem(item);
        batch.end();
        drawFrontendTopLayers();
        drawCenteredFeedbackTopLayer();
    }

    /**
     * Composes a popup opened above another modal as a true two-level stack.
     *
     * <p>The already blurred root is combined with the parent dialog in a
     * second native-resolution framebuffer. That complete parent level is
     * blurred again, dimmed, and only then is the child popup drawn sharply.
     * Parent hit maps are discarded before the child is built, so pointer,
     * wheel and click input cannot leak through the popup.</p>
     */
    private void renderNestedFrontendModal() {
        if (!GdxVisualEffects.blurActive()) {
            clearModalCompositionState();
            float parentPointerX = pointer.x;
            float parentPointerY = pointer.y;
            pointer.set(-10_000f, -10_000f);
            try {
                Gdx.gl.glEnable(GL20.GL_BLEND);
                Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA,
                        GL20.GL_ONE_MINUS_SRC_ALPHA);
                shapes.begin(ShapeRenderer.ShapeType.Filled);
                drawNestedModalParent();
                shapes.end();
                batch.begin();
                if (customBotManagerOpen) drawCustomBotManagerImages();
                for (TextItem item : texts) drawTextItem(item);
                batch.end();
                if (customBotManagerOpen) drawCustomBotManagerListLayer();
            } finally {
                pointer.set(parentPointerX, parentPointerY);
            }
            drawUnifiedModalBackdrop();
            drawNestedModalChild();
            return;
        }
        nestedModalBackdropBlur.beginCapture(
                Gdx.graphics.getBackBufferWidth(),
                Gdx.graphics.getBackBufferHeight());
        modalBackdropBlur.drawCached(viewport, batch,
                viewport.getCamera().combined, BACKGROUND);
        drawUnifiedModalBackdrop();

        clearModalCompositionState();
        float parentPointerX = pointer.x;
        float parentPointerY = pointer.y;
        pointer.set(-10_000f, -10_000f);
        try {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA,
                    GL20.GL_ONE_MINUS_SRC_ALPHA);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            drawNestedModalParent();
            shapes.end();

            batch.begin();
            if (customBotManagerOpen) drawCustomBotManagerImages();
            for (TextItem item : texts) drawTextItem(item);
            batch.end();
            if (customBotManagerOpen) drawCustomBotManagerListLayer();
        } finally {
            pointer.set(parentPointerX, parentPointerY);
        }

        try {
            nestedModalBackdropBlur.endCaptureAndDraw(viewport, batch,
                    viewport.getCamera().combined, BACKGROUND);
        } finally {
            restoreDefaultFrontendRenderState();
        }
        drawUnifiedModalBackdrop();
        drawNestedModalChild();
    }

    private void drawNestedModalParent() {
        if (customBotManagerOpen && customBotDeleteConfirmation) {
            drawCustomBotManagerDialog();
            return;
        }
        if (networkBlocksOpen && networkBlockUnblockConfirmation != null) {
            drawNetworkBlocksDialog();
            return;
        }
        if (voiceNotesOpen && (voiceNoteDeleteConfirmation != null
                || voiceNotesPurgeConfirmation)) {
            GdxVoiceNoteLibrary.Entry savedEntry = voiceNoteDeleteConfirmation;
            boolean savedPurge = voiceNotesPurgeConfirmation;
            voiceNoteDeleteConfirmation = null;
            voiceNotesPurgeConfirmation = false;
            try {
                drawVoiceNotesDialog();
            } finally {
                voiceNoteDeleteConfirmation = savedEntry;
                voiceNotesPurgeConfirmation = savedPurge;
            }
            return;
        }
        if (isBlindStructureChildDialog()) {
            BlindStructureDialog savedDialog = blindStructureDialog;
            blindStructureDialog = BlindStructureDialog.EDITOR;
            try {
                drawBlindStructureDialog();
            } finally {
                blindStructureDialog = savedDialog;
            }
        }
    }

    private void drawNestedModalChild() {
        clearModalCompositionState();
        if (customBotManagerOpen && customBotDeleteConfirmation) {
            drawCustomBotDeleteConfirmationTopLayer();
            return;
        }
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA,
                GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (networkBlocksOpen && networkBlockUnblockConfirmation != null) {
            drawNetworkBlockUnblockConfirmation();
        } else if (voiceNotesOpen && (voiceNoteDeleteConfirmation != null
                || voiceNotesPurgeConfirmation)) {
            drawVoiceNotesDialog();
        } else if (isBlindStructureChildDialog()) {
            drawBlindStructureDialog();
        }
        shapes.end();
        batch.begin();
        for (TextItem item : texts) drawTextItem(item);
        batch.end();
    }

    private void clearModalCompositionState() {
        texts.clear();
        lobbyAvatars.clear();
        uiImages.clear();
        customBotManagerListTexts.clear();
        customBotManagerListImages.clear();
        hits.clear();
        secondaryHits.clear();
        textFieldHits.clear();
        passwordRevealHits.clear();
        editMenuHits.clear();
        tooltipHits.clear();
    }

    private void drawCenteredFeedbackTopLayer() {
        if (elapsed < volumeOverlayUntil) {
            drawVolumeOverlayTopLayer();
        } else if (System.currentTimeMillis() < toastUntil) {
            drawToastTopLayer();
        } else if (surface == Surface.SCREENSHOTS
                && !screenshotDeleteConfirmation
                && !screenshotToast.isBlank() && elapsed < screenshotToastUntil) {
            drawScreenshotToastTopLayer();
        }
    }

    private void drawFrontendTopLayers() {
        drawFrontendVersionLabelTopLayer();
        if (preferenceBoolean("gdx_show_fps", false)) {
            drawFrontendFpsCounter();
        }
        if (preferenceBoolean("gdx_show_cpu_memory", false)) {
            drawFrontendPerformanceCounter();
        }
    }

    private void drawPokerVideoTopLayer() {
        if (!pokerVideoOverlay.hostVisible()) return;
        if (pokerVideoOverlay.ownsHover(pointer.x, pointer.y)) {
            tooltipHits.clear();
        }
        pokerVideoOverlay.addTooltips((bounds, key) ->
                tooltip(bounds.x, bounds.y, bounds.width, bounds.height, key));
        pokerVideoOverlay.prepareFrame();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        pokerVideoOverlay.drawShapes(shapes, pointer);
        shapes.end();
        batch.begin();
        pokerVideoOverlay.drawContent(batch, buttonTinyFont, tinyFont,
                viewport, pointer);
        batch.end();
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        pokerVideoOverlay.drawForegroundShapes(shapes, pointer, viewport);
        shapes.end();
    }

    void updatePokerVideo(boolean visible, float delta,
            Consumer<PokerVideoSearch.Video> shareAction) {
        boolean pokerVideosAvailable = PokerVideoIcon.systemEnabled(
                PokerVideoPlatformSupport.isAvailable(),
                preferenceBoolean("animaciones", true));
        pokerVideoOverlay.updateWindowAnimation(preferenceBoolean(
                GdxSettingsContract.POKER_VIDEO_WINDOW_ANIMATION_KEY, true),
                delta);
        pokerVideoOverlay.setShareAction(shareAction);
        pokerVideoOverlay.update(pokerVideosAvailable,
                pokerVideosAvailable && preferenceBoolean(
                        GdxSettingsContract.POKER_VIDEOS_KEY, true),
                visible,
                gameText.language(), initialProperties.getProperty(
                        GdxSettingsContract.POKER_VIDEO_TYPE_KEY,
                        PokerVideoSearch.TYPE_NORMAL), WIDTH, HEIGHT);
    }

    void openChatVideo(PokerVideoSearch.Video video, Consumer<String> notice) {
        if (!preferenceBoolean("animaciones", true) || !preferenceBoolean(
                GdxSettingsContract.POKER_VIDEOS_KEY, true)) {
            notice.accept(gameText.translate("gdx.video.share.disabled"));
        } else if (!PokerVideoPlatformSupport.isAvailable()) {
            notice.accept(gameText.translate("gdx.video.share.unavailable"));
        } else {
            pokerVideoOverlay.openSharedVideo(video);
        }
    }

    void resetMediaInput() {
        pokerVideoOverlay.resetHostInput();
        tooltipDelay.clear();
    }

    PokerVideoOverlay pokerVideoOverlay() {
        return pokerVideoOverlay;
    }

    Vector2 tableMediaPointer(int screenX, int screenY) {
        return viewport.unproject(pointer.set(screenX, screenY));
    }

    boolean footerSoundContains(Vector2 position) {
        return footerSoundHit.contains(position);
    }

    /** Draw the same footer in the same viewport over the live table. */
    void drawTableMedia(boolean soundPressed, boolean shieldPointer) {
        refreshFontQualityIfDue();
        refreshPointerFromDevice();
        if (shieldPointer) {
            pointer.set(-10_000f, -10_000f);
            pokerVideoOverlay.mouseMoved(pointer.x, pointer.y);
        }
        prepareFrontendGraphicsState();
        viewport.apply();
        viewport.getCamera().update();
        shapes.setProjectionMatrix(viewport.getCamera().combined);
        batch.setProjectionMatrix(viewport.getCamera().combined);
        tooltipHits.clear();

        boolean outputAvailable = audioOutputAvailable();
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        boolean videoOwnsHover = pokerVideoOverlay.ownsHover(pointer.x, pointer.y);
        drawFooterSoundSurface(outputAvailable,
                !videoOwnsHover && footerSoundContains(pointer), soundPressed);
        shapes.end();
        batch.begin();
        UiImageItem image = footerSoundImage(outputAvailable);
        batch.setColor(image.tint);
        batch.draw(image.texture, image.x, image.y, image.width, image.height);
        batch.setColor(Color.WHITE);
        batch.end();
        if (!videoOwnsHover) addFooterSoundTooltip(outputAvailable);
        drawPokerVideoTopLayer();
    }

    void drawTableMediaTooltip(boolean covered) {
        if (covered) {
            tooltipDelay.clear();
        } else {
            viewport.apply();
            drawTooltipTopLayer(null);
        }
    }

    /** Draws deferred circular avatars in whichever composition pass owns them. */
    private void drawLobbyAvatarsInCurrentBatch() {
        if (lobbyAvatars.isEmpty()) return;
        batch.setShader(avatarShader);
        batch.setColor(Color.WHITE);
        for (LobbyAvatarItem item : lobbyAvatars) {
            batch.draw(item.texture, item.x, item.y, item.size, item.size);
        }
        batch.flush();
        batch.setShader(null);
    }

    private void drawUiImagesInCurrentBatch() {
        boolean pickerSampling = false;
        for (UiImageItem item : uiImages) {
            if (pickerSampling != item.pickerEmoji()) {
                pickerSampling = item.pickerEmoji();
                if (pickerSampling) {
                    emojiAtlas.beginPickerSampling(batch, SPRITE_VERTEX_SHADER,
                            LOBBY_EMOJI_PICKER_IMAGE_SIZE, viewport);
                } else {
                    batch.setShader(null);
                }
            }
            item.drawImage(batch, viewport);
        }
        if (pickerSampling) batch.setShader(null);
        batch.setColor(Color.WHITE);
    }

    private boolean hasVisibleFrontendModal() {
        return applicationExitConfirmation
                || avatarCropSession != null
                || modAdultWarning
                || !frontendNoticeMessage.isBlank()
                || settingsRestartNotice
                || (surface == Surface.MENU
                        && (aboutOpen || updatePromptOpen
                                || updateCheckingModalVisible()))
                || (surface == Surface.LOBBY
                && (lobbyConfirmation != null || lobbyPasswordDialog
                        || networkBlocksOpen
                        || !identityChangeNotices.isEmpty()
                        || lobbyPreparationUsesBackdropBlur(
                                lobbyGameStarting, lobby)
                        || fingerprintDialog != null))
                || surface == Surface.SETTINGS
                || (surface == Surface.NEW_GAME
                && (customBotManagerOpen || upnpOpenInFlight
                        || submissions.submitting()
                        || presetDialog != PresetDialog.NONE
                        || initialBuyinInput != null
                        || blindStructureDialog != BlindStructureDialog.NONE))
                || (surface == Surface.STATS
                && (statsEvidenceOpen
                        || statsConfirmation != StatsConfirmation.NONE
                        || statsPicker != StatsPicker.NONE
                        || statsSyncExclusionsOpen))
                || (surface == Surface.SCREENSHOTS
                        && screenshotDeleteConfirmation)
                || isFrontendRootModal();
    }

    private boolean hasNestedFrontendModal() {
        return !applicationExitConfirmation
                && ((surface == Surface.NEW_GAME || surface == Surface.SETTINGS)
                        && customBotManagerOpen
                        && customBotDeleteConfirmation
                || surface == Surface.LOBBY && networkBlocksOpen
                        && networkBlockUnblockConfirmation != null
                || surface == Surface.SETTINGS && voiceNotesOpen
                        && (voiceNoteDeleteConfirmation != null
                        || voiceNotesPurgeConfirmation)
                || (surface == Surface.NEW_GAME || surface == Surface.SETTINGS)
                        && isBlindStructureChildDialog());
    }

    private boolean isBlindStructureChildDialog() {
        return blindStructureDialog != BlindStructureDialog.NONE
                && blindStructureDialog != BlindStructureDialog.EDITOR;
    }

    /** True only when PREPARANDO LA MESA owns the lobby modal foreground. */
    private boolean lobbyLoadingOverlayVisible() {
        return surface == Surface.LOBBY
                && !applicationExitConfirmation
                && lobbyPreparationUsesBackdropBlur(lobbyGameStarting, lobby)
                && !modAdultWarning
                && frontendNoticeMessage.isBlank()
                && !settingsRestartNotice
                && !updateCheckingModalVisible()
                && !aboutOpen
                && !updatePromptOpen
                && blindStructureDialog == BlindStructureDialog.NONE
                && !voiceNotesOpen
                && !isFrontendRootModal()
                && !networkBlocksOpen
                && !lobbyPasswordDialog
                && fingerprintDialog == null
                && identityChangeNotices.isEmpty()
                && lobbyConfirmation == null;
    }

    /**
     * Full frontend workflows opened from the main menu behave as modal
     * surfaces: the menu remains visible but blurred below them. A nested
     * confirmation deliberately returns false so the active workflow becomes
     * the blurred scene and only that child dialog is composed sharp.
     */
    private boolean isFrontendRootModal() {
        if (frontendNoticeOwnsPointer() || avatarCropSession != null) return false;
        if (isSettingsRootModal()) return true;
        if (surface == Surface.NEW_GAME) {
            return !customBotManagerOpen
                    && !upnpOpenInFlight
                    && frontendNoticeMessage.isBlank()
                    && !submissions.submitting()
                    && presetDialog == PresetDialog.NONE
                    && initialBuyinInput == null
                    && blindStructureDialog == BlindStructureDialog.NONE;
        }
        if (surface == Surface.STATS && statsReturnAction == null) {
            return !statsEvidenceOpen
                    && statsConfirmation == StatsConfirmation.NONE
                    && statsPicker == StatsPicker.NONE
                    && !statsSyncExclusionsOpen;
        }
        return surface == Surface.SCREENSHOTS
                && !screenshotDeleteConfirmation;
    }

    private boolean frontendNoticeOwnsPointer() {
        return applicationExitConfirmation || modAdultWarning
                || !frontendNoticeMessage.isBlank() || settingsRestartNotice
                || updateCheckingModalVisible();
    }

    private boolean isSettingsRootModal() {
        return surface == Surface.SETTINGS
                && !customBotManagerOpen
                && frontendNoticeMessage.isBlank()
                && !settingsDiscardConfirmation
                && profileBackupSelection == null
                && settingsAutoCallDialog == null
                && initialBuyinInput == null
                && !voiceNotesOpen
                && blindStructureDialog == BlindStructureDialog.NONE;
    }

    private void drawSettingsBackdropSurface() {
        if (settingsReturnSurface == Surface.LOBBY && lobby != null) {
            drawLobby();
        } else if (renderMainMenuContent(false, false)) {
            drawMainMenu();
        }
    }

    private void drawRootModalBackdropSurface() {
        if (surface == Surface.SETTINGS) {
            drawSettingsBackdropSurface();
        } else if (surface == Surface.SCREENSHOTS
                && screenshotReturnSurface == Surface.LOBBY && lobby != null) {
            drawLobby();
        } else if (renderMainMenuContent(false, false)) {
            drawMainMenu();
        }
    }

    private void drawRootModalSurface() {
        switch (surface) {
            case SETTINGS -> drawSettingsScreen();
            case STATS -> drawStatsScreen();
            case SCREENSHOTS -> drawScreenshotViewer();
            case NEW_GAME -> drawNewGameScreen();
            default -> { }
        }
    }

    private boolean hasBlockingFrontendModal() {
        // Visibility is the ownership contract: a lobby/new-game/settings
        // flag must never shield a different surface merely because its
        // teardown was delayed. The startup gate is the sole non-visual
        // blocker and belongs exclusively to the main menu.
        return (surface == Surface.MENU && startupMenuWaitingForUpdate)
                || hasVisibleFrontendModal();
    }

    /**
     * A root workflow is itself the foreground surface and must keep its own
     * keyboard, wheel and scrollbar input. Only a child dialog placed above
     * that workflow consumes those events.
     */
    private boolean childModalOwnsInput() {
        return childModalOwnsInput(hasBlockingFrontendModal(),
                isFrontendRootModal());
    }

    static boolean childModalOwnsInput(boolean visibleModal,
            boolean rootSurfaceModal) {
        return visibleModal && !rootSurfaceModal;
    }

    /**
     * Settings is itself a modal surface, but that must only shield the page
     * behind it.  Its rows and scrollbars remain interactive until a child
     * dialog is actually placed above them.
     */
    private boolean settingsScrollBlockedByChildDialog() {
        return customBotManagerOpen || settingsRestartNotice
                || settingsDiscardConfirmation
                || profileBackupSelection != null || settingsBackupPending
                || settingsAutoCallDialog != null
                || initialBuyinInput != null
                || voiceNotesOpen
                || blindStructureDialog != BlindStructureDialog.NONE;
    }

    void beginStartupReveal() {
        prepareInitialMenu(false);
    }

    void completeStartupReveal() {
        prepareInitialMenu(true);
    }

    private void prepareInitialMenu(boolean skipped) {
        startupMenuWasSkipped = skipped;
        if (updateCheck != null) {
            startupMenuWaitingForUpdate = true;
            menuRevealStartedAt = Float.POSITIVE_INFINITY;
            captureFrontendModalInput();
            return;
        }
        finishStartupUpdateGate();
    }

    private void resolveStartupUpdateGate() {
        if (!startupMenuWaitingForUpdate || updateCheck != null) return;
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
        clearLobbyScreenState();
        lobbySession = Objects.requireNonNull(session, "session");
        lobby = session.snapshot();
        networkBlockCache = new GdxNetworkBlockCache(session::networkBlocks);
        networkBlockCache.refreshNow(lobby.host());
        lobbyChatDraft = "";
        selectedLobbyChatSequence = -1L;
        lobbyTextSendAllowedAt = 0f;
        lobbyChatScroll = 0;
        lobbyChatMessageCount = lobby.chat().size();
        lobbyChatContentHeight = 0f;
        lobbyImageSendAllowedAt = 0f;
        lobbyVoiceStatus = "";
        lastLobbyMediaSequence = lobby.chat().stream()
                .mapToLong(LobbyChatMessage::sequence).max().orElse(-1L);
        clearLobbyMedia();
        for (LobbyChatMessage message : lobby.chat()) {
            if (message.type() == LobbyChatMessage.Type.IMAGE) {
                loadLobbyImage(message);
            }
        }
        lastLobbyNoticeIdentity = "";
        if (lobby.host()) {
            List<String> addresses = hostAddressOptions.size() > 1
                    ? hostAddressOptions : GdxLocalServerAddresses.discover();
            lobbyLocalAddress = GdxLocalServerAddresses.preferredAddress(addresses);
        }
        loadLobbyPublicAddress(lobby.host());
        clearActiveField();
        activateSurface(Surface.LOBBY);
        presentLobbyNotice(null, lobby);
        if (shouldPlayLobbyConnectionSound(connection, lobby)) {
            playPreferenceSound("misc/yahoo.wav", "sonido_conexion", 0.82f);
        }
        lobbySubscription = session.subscribe(next -> Gdx.app.postRunnable(() -> {
            if (!disposed && lobbySession == session) {
                LobbySnapshot previous = lobby;
                playLobbyRosterChange(previous, next);
                lobby = next;
                if (next.startingOrStarted()) lobbyGallery.close();
                if (lobbyConfirmation == LobbyConfirmation.KICK
                        && !lobbyHasKickTarget(next, lobbyKickTarget)) {
                    closeLobbyConfirmation();
                }
                presentLobbyNotice(previous, next);
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

    static boolean shouldPlayLobbyConnectionSound(
            NewGameConnectionDraft connection, LobbySnapshot state) {
        return connection != null && state != null
                && shouldPlayLobbyConnectionSound(connection.mode(),
                        state.phase());
    }

    static boolean shouldPlayLobbyConnectionSound(
            NewGameConnectionDraft.Mode mode, LobbySnapshot.Phase phase) {
        return mode == NewGameConnectionDraft.Mode.JOIN
                && phase != LobbySnapshot.Phase.INITIALIZING_GAME
                && phase != LobbySnapshot.Phase.IN_GAME;
    }

    private void presentLobbyNotice(LobbySnapshot previous,
            LobbySnapshot next) {
        if (next == null) return;
        String detail = Objects.requireNonNullElse(next.statusDetail(), "")
                .trim();
        boolean upnpFailure = "UPNP_UNAVAILABLE".equals(detail)
                || "UPNP_ALREADY_MAPPED".equals(detail)
                || "UPNP_FAILED".equals(detail)
                || "UPNP_ERROR".equals(detail);
        boolean fatal = next.phase() == LobbySnapshot.Phase.ERROR;
        boolean terminalNotice = next.phase() == LobbySnapshot.Phase.CLOSED
                && !detail.isBlank();
        if (!upnpFailure && !fatal && !terminalNotice) return;
        String identity = next.phase() + "\n" + detail;
        if (identity.equals(lastLobbyNoticeIdentity)) return;
        if (previous != null && previous.phase() == next.phase()
                && Objects.equals(previous.statusDetail(), next.statusDetail())) {
            return;
        }
        lastLobbyNoticeIdentity = identity;
        if (upnpFailure) {
            if (connection != null) connection.setUpnp(false);
            verifiedUpnpPort = -1;
        }
        detail = lobbyNetworkStatusText(detail, gameText);
        showFrontendNotice(gameText.translate(fatal || upnpFailure
                ? "gdx.dialog.error" : "gdx.dialog.information"), detail,
                fatal || upnpFailure);
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

    /**
     * Creating, joining and joining an already-running table all enter the
     * same lobby before the native table is attached. Keep that waiting room
     * as the captured modal backdrop while PREPARANDO TIMBA is visible.
     */
    static boolean lobbyPreparationUsesBackdropBlur(boolean locallyStarting,
            LobbySnapshot state) {
        return lobbyTableTransitionActive(locallyStarting, state);
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
                uppercase(gameText.translate("menu.visor_capturas")), 6, false,
                this::openScreenshotViewer);
        mainMenuButton(595f, 310f, 350f, 82f,
                uppercase(gameText.translate("menu.ajustes")), 3, false,
                this::openSettings);
        mainMenuChoice(975f, 310f, 350f, 82f,
                gameText.translate("gdx.language_name"),
                () -> openDropdown(Dropdown.LANGUAGE,
                        975f, 310f, 350f, 82f));
        mainMenuButton(595f, 205f, 350f, 82f,
                uppercase(gameText.translate("menu.acerca_de")), 4, false,
                this::openAboutDialog);
        exitButton(975f, 205f, 350f, 82f,
                gameText.translate("ui.salir"),
                this::requestApplicationExit, true);

        drawFooterSoundControl();
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
                    new Color(Color.WHITE), true));
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
        activateSurface(Surface.STATS);
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
        openStatsDashboard();
        if (statsBrowser == null) loadStatsGames();
        syncMusicForSurface();
    }

    void openStatsFromTable(Runnable returnAction) {
        statsReturnAction = Objects.requireNonNull(returnAction,
                "returnAction");
        openStats();
    }

    private void closeStats() {
        closeStatsDashboard();
        closeStatsEvidence();
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
            activateSurface(Surface.MENU);
            syncMusicForSurface();
        }
    }

    private void openScreenshotViewer() {
        clearActiveField();
        screenshotReturnSurface = surface == Surface.LOBBY
                ? Surface.LOBBY : Surface.MENU;
        screenshotDeleteConfirmation = false;
        screenshotOperationPending = false;
        activateSurface(Surface.SCREENSHOTS);
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
        activateSurface(screenshotReturnSurface);
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

        textFit(headingFont,
                uppercase(gameText.translate("menu.visor_capturas")),
                WIDTH / 2f, 1040f, GOLD, true, 1080f);
        if (!screenshots.isEmpty()) {
            textFit(smallFont, GdxScreenshotStore.displayTitle(
                    screenshots.get(screenshotIndex), screenshotIndex,
                    screenshots.size(), gameText.language()),
                    WIDTH / 2f, 980f, Color.WHITE, true, 1120f);
        }

        if (screenshotTexture != null) {
            Rectangle area = CoronaPokerGdxTable
                    .screenshotViewerImageArea(0f, 0f);
            Rectangle bounds = CoronaPokerGdxTable.fitInside(
                    screenshotTexture.getWidth(), screenshotTexture.getHeight(),
                    area.x, area.y, area.width, area.height, true);
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
        themedButton(1812f, 1008f, 58f, 50f,
                GdxUiDialogStyle.CLOSE_GLYPH, ButtonTone.DANGER,
                this::closeScreenshotViewer, !modal,
                settingsStepperSignFont);
        if (screenshotIndex > 0) {
            screenshotNavigationButton(35f, 470f, 64f, 110f, -1,
                    () -> showRelativeScreenshot(-1), !modal);
        }
        if (screenshotIndex + 1 < screenshots.size()) {
            screenshotNavigationButton(1821f, 470f, 64f, 110f, 1,
                    () -> showRelativeScreenshot(1), !modal);
        }
        if (screenshotTexture != null) {
            themedButton(660f, 18f, 280f, 64f,
                    uppercase(gameText.translate(
                            "ui.copiar_imagen_portapapeles")),
                    ButtonTone.FEATURED, this::copyCurrentScreenshot,
                    !modal && !screenshotOperationPending);
            themedButton(980f, 18f, 280f, 64f,
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

    private GdxUiDialogStyle.ConfirmationLayout drawConfirmationChrome(
            String title, String message) {
        GdxUiDialogStyle.ConfirmationLayout layout =
                GdxUiDialogStyle.confirmationLayout(WIDTH, HEIGHT);
        Rectangle panel = layout.panel();
        Rectangle titleBounds = layout.title();
        Rectangle messageBounds = layout.message();
        GdxUiDialogStyle.drawPanel(shapes, panel.x, panel.y,
                panel.width, panel.height, CYAN_DARK, 1f);
        textFit(headingFont, title, titleBounds.x,
                titleBounds.y + titleBounds.height * 0.76f,
                GOLD, false, titleBounds.width);

        List<String> lines = wrapText(actionFont,
                GdxUiDialogStyle.formatNoticeMessage(message),
                messageBounds.width, 3);
        float lineHeight = 34f;
        float firstBaseline = messageBounds.y + messageBounds.height / 2f
                + 12f + Math.max(0, lines.size() - 1) * lineHeight / 2f;
        for (int index = 0; index < lines.size(); index++) {
            textFit(actionFont, lines.get(index),
                    messageBounds.x + messageBounds.width / 2f,
                    firstBaseline - index * lineHeight,
                    Color.WHITE, true, messageBounds.width);
        }
        return layout;
    }

    private GdxUiDialogStyle.NoticeLayout drawNoticeChrome(String title,
            String message, boolean error) {
        GdxUiDialogStyle.NoticeLayout layout =
                GdxUiDialogStyle.noticeLayout(WIDTH, HEIGHT);
        Rectangle panel = layout.panel();
        Rectangle titleBounds = layout.title();
        Rectangle messageBounds = layout.message();
        GdxUiDialogStyle.drawNoticePanel(shapes, panel.x, panel.y,
                panel.width, panel.height, error ? ORANGE : CYAN_DARK);
        textFit(headingFont, uppercase(title),
                titleBounds.x + titleBounds.width / 2f,
                titleBounds.y + titleBounds.height * 0.76f,
                error ? ORANGE : GOLD, true, titleBounds.width);

        List<String> lines = wrapText(smallFont,
                GdxUiDialogStyle.formatNoticeMessage(message),
                messageBounds.width, 5);
        float lineHeight = 34f;
        float firstBaseline = messageBounds.y + messageBounds.height / 2f
                + 12f + Math.max(0, lines.size() - 1) * lineHeight / 2f;
        for (int index = 0; index < lines.size(); index++) {
            textFit(smallFont, lines.get(index),
                    messageBounds.x + messageBounds.width / 2f,
                    firstBaseline - index * lineHeight,
                    Color.WHITE, true, messageBounds.width);
        }
        return layout;
    }

    private void drawScreenshotDeleteConfirmation() {
        GdxUiDialogStyle.ConfirmationLayout layout = drawConfirmationChrome(
                uppercase(gameText.translate("ui.borrar_captura")),
                gameText.translate("ui.borrar_captura_confirm"));
        Rectangle cancel = layout.negativeButton();
        Rectangle accept = layout.positiveButton();
        themedButton(cancel.x, cancel.y, cancel.width, cancel.height,
                uppercase(gameText.translate("ui.cancelar")),
                ButtonTone.NEUTRAL,
                () -> screenshotDeleteConfirmation = false, true);
        themedButton(accept.x, accept.y, accept.width, accept.height,
                uppercase(gameText.translate("ui.aceptar")),
                ButtonTone.DANGER, this::deleteCurrentScreenshot, true);
    }

    private void loadStatsGames() {
        loadStatsGames(null);
    }

    private void loadStatsGames(PokerStatsBrowser.Selection restore) {
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
                    if (restore != null) {
                        String player = restore.player();
                        boolean playerInGame = restore.gameId() == 0 || games.stream()
                                .anyMatch(game -> game.id() == restore.gameId()
                                        && game.players().contains(player));
                        statsPlayerFilter = playerInGame && statsPlayers.contains(player)
                                ? player : "";
                    }
                    applyStatsPlayerFilter();
                    if (restore != null && restore.gameId() > 0) {
                        for (int index = 0; index < statsGames.size(); index++) {
                            if (statsGames.get(index).id() != restore.gameId()) continue;
                            statsGameIndex = index;
                            if (restore.handId() > 0) statsMode = StatsMode.BALANCE;
                            loadStatsScope(restore.handId() > 0 ? restore.handId() : null);
                            return;
                        }
                    } else if (restore == null && !statsPlayerFilter.isBlank()) {
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
        selectStatsHandAt(Math.floorMod(statsHandIndex + 1 + direction,
                count) - 1);
    }

    private void selectStatsHandAt(int index) {
        if (statsLoading || statsGameIndex < 0 || index < -1
                || index >= statsHands.size()) return;
        statsHandIndex = index;
        statsSummaryPlayersScroll = statsSummaryPlayersScrollTarget = 0f;
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
                        || (statsBrowser == null && !statsMode.supportsHandScope()))) return;
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
        loadStatsScope(statsHandIndex < 0 ? null : statsHands.get(statsHandIndex).id());
    }

    private void loadStatsScope(Integer requestedHandId) {
        long generation = ++statsLoadGeneration;
        statsLoading = true;
        statsError = "";
        Integer gameId = statsGameIndex < 0 ? null
                : statsGames.get(statsGameIndex).id();
        CompletableFuture.runAsync(() -> {
            try {
                List<StatsRepository.HandSummary> hands = gameId == null
                        ? List.of() : statsRepository.hands(gameId);
                int matchingIndex = -1;
                if (requestedHandId != null) {
                    for (int index = 0; index < hands.size(); index++) {
                        if (hands.get(index).id() == requestedHandId) {
                            matchingIndex = index;
                            break;
                        }
                    }
                }
                int handIndex = matchingIndex;
                Integer handId = handIndex < 0 ? null : hands.get(handIndex).id();
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
                    statsHandIndex = handIndex;
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

    private void openStatsDashboard() {
        if (!PokerVideoPlatformSupport.isAvailable() || statsBrowser != null) return;
        statsBrowserFocused = false;
        statsBrowser = new PokerStatsBrowser(statsRepository,
                connection.nickname(),
                "en".equalsIgnoreCase(gameText.language()),
                preferenceBoolean("animaciones", true), statsBrowserWidth(), statsBrowserHeight());
    }

    private int statsBrowserWidth() {
        return Math.max(1, Math.round(STATS_WEB_BOUNDS.width * viewport.getScreenWidth() / WIDTH));
    }

    private int statsBrowserHeight() {
        return Math.max(1, Math.round(STATS_WEB_BOUNDS.height * viewport.getScreenHeight() / HEIGHT));
    }

    private void closeStatsDashboard() {
        if (statsBrowser == null) return;
        statsBrowser.close();
        statsBrowser = null;
        statsBrowserFocused = false;
        statsBrowserPointer.clear();
    }

    private void drawStatsDashboard() {
        Rectangle bounds = STATS_WEB_BOUNDS;
        Texture rendered = statsBrowser.texture(statsBrowserWidth(), statsBrowserHeight());
        if (rendered != null) {
            // Snap the OSR texture to physical pixels to preserve Chromium's
            // glyph and chart edges inside the letterboxed game viewport.
            float pixelsPerWorldX = viewport.getScreenWidth() / WIDTH
                    * Gdx.graphics.getBackBufferWidth() / Gdx.graphics.getWidth();
            float pixelsPerWorldY = viewport.getScreenHeight() / HEIGHT
                    * Gdx.graphics.getBackBufferHeight() / Gdx.graphics.getHeight();
            if (pixelsPerWorldX > 0f && pixelsPerWorldY > 0f) {
                float x = Math.round(bounds.x * pixelsPerWorldX) / pixelsPerWorldX;
                float y = Math.round(bounds.y * pixelsPerWorldY) / pixelsPerWorldY;
                float width = Math.round(bounds.width * pixelsPerWorldX) / pixelsPerWorldX;
                float height = Math.round(bounds.height * pixelsPerWorldY) / pixelsPerWorldY;
                uiImages.add(new UiImageItem(rendered, x, y, width, height, Color.WHITE));
            }
            return;
        }
        Texture spade = pokerVideoOverlay.loadingSpade();
        float cx = bounds.x + bounds.width / 2f;
        float cy = bounds.y + bounds.height / 2f;
        uiImages.add(new UiImageItem(spade, cx - 34f, cy + 27f,
                68f, 68f, Color.WHITE));
        textFit(headingFont, gameText.translate("gdx.stats.dashboard.loading"),
                cx, cy + 3f, GOLD, true, 800f);
    }

    private boolean statsBrowserInput() {
        return statsBrowserVisible() && statsBrowser.interactive();
    }

    private boolean statsBrowserVisible() {
        return surface == Surface.STATS && statsBrowser != null && !childModalOwnsInput();
    }

    private boolean statsOwnsPointer() {
        return statsBrowserVisible() && (statsBrowserPointer.active() || STATS_WEB_BOUNDS.contains(pointer));
    }

    private void statsBrowserMouse(int event) {
        Rectangle bounds = STATS_WEB_BOUNDS;
        statsBrowser.mouse(event, (pointer.x - bounds.x) / bounds.width,
                (bounds.y + bounds.height - pointer.y) / bounds.height);
    }

    private void drawStatsScreen() {
        statsSummaryPlayersViewport.set(0f, 0f, 0f, 0f);
        statsSummaryPlayersScrollTrack.set(0f, 0f, 0f, 0f);
        if (statsBrowser != null && statsBrowser.failed()) {
            PokerStatsBrowser.Selection selection = statsBrowser.selection();
            closeStatsDashboard();
            loadStatsGames(selection);
        }
        panel(20f, 20f, 1880f, 1040f, "");
        textFit(titleFont, gameText.translate("ui.estadisticas"), 62f,
                1015f, GOLD, false, 470f);
        themedButton(1610f, 975f, 245f, 58f,
                gameText.translate("ui.volver"), ButtonTone.NEUTRAL,
                this::closeStats, true);
        drawStatsSyncControls();

        if (statsBrowser != null) {
            panel(60f, 44f, 1800f, 907f, "");
            drawStatsDashboard();
            return;
        }

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
                ? gameText.translate("gdx.stats.all_players") : statsPlayerFilter;
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
                    statsChartZoom > 0.8f && !statsLoading,
                    settingsStepperSignFont);
            textFit(tinyFont, Math.round(statsChartZoom * 100f) + "%",
                    1690f, 742f, GOLD, true, 90f);
            themedButton(1742f, 716f, 48f, 38f, "+", ButtonTone.NEUTRAL,
                    () -> adjustStatsChartZoom(0.1f),
                    statsChartZoom < 2f && !statsLoading,
                    settingsStepperSignFont);
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
        }
    }

    private void drawStatsSyncControls() {
        boolean receive = GdxStatsSyncSettings.receive(initialProperties);
        boolean share = GdxStatsSyncSettings.share(initialProperties);
        compactToggle(STATS_SYNC_RECEIVE_X, STATS_SYNC_ROW_Y,
                STATS_SYNC_RECEIVE_WIDTH,
                gameText.translate("stats.sync_receive"), receive,
                () -> toggleStatsSyncPreference(
                        GdxStatsSyncSettings.RECEIVE_KEY,
                        GdxStatsSyncSettings.RECEIVE_DEFAULT), true);

        outerBox(STATS_SYNC_SHARE_GROUP_X, STATS_SYNC_ROW_Y - 9f,
                STATS_SYNC_SHARE_GROUP_WIDTH, 86f, CYAN_DARK, PANEL_LIGHT);
        compactToggle(STATS_SYNC_SHARE_GROUP_X + 10f, STATS_SYNC_ROW_Y,
                360f, gameText.translate("stats.sync_share"), share,
                () -> toggleStatsSyncPreference(
                        GdxStatsSyncSettings.SHARE_KEY,
                        GdxStatsSyncSettings.SHARE_DEFAULT), true);
        themedButton(STATS_SYNC_SHARE_GROUP_X + 382f,
                STATS_SYNC_ROW_Y + 11f, 236f, 46f,
                gameText.translate("stats.sync_exclude"),
                ButtonTone.NEUTRAL, this::openStatsSyncExclusions, share);
    }

    private void toggleStatsSyncPreference(String key, boolean fallback) {
        togglePreference(key, fallback);
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
        GdxUiDialogStyle.ConfirmationLayout layout = drawConfirmationChrome(
                uppercase(gameText.translate("ui.seguro")), prompt);
        Rectangle cancel = layout.negativeButton();
        Rectangle accept = layout.positiveButton();
        themedButton(cancel.x, cancel.y, cancel.width, cancel.height,
                uppercase(gameText.translate("ui.cancelar")),
                ButtonTone.NEUTRAL,
                () -> statsConfirmation = StatsConfirmation.NONE, true);
        boolean destructive = statsConfirmation == StatsConfirmation.IMPORTED
                || statsConfirmation == StatsConfirmation.GAME
                || statsConfirmation == StatsConfirmation.PURGE_FILTERED;
        themedButton(accept.x, accept.y, accept.width, accept.height,
                uppercase(gameText.translate(destructive
                        ? "gdx.stats.delete" : "ui.aceptar")),
                destructive ? ButtonTone.DANGER : ButtonTone.POSITIVE,
                this::confirmStatsDeletion, true);
    }

    private void openStatsEvidence() {
        if (statsLoading || statsGameIndex < 0
                || !statsGames.get(statsGameIndex).hasZeroTrustIncident()) {
            return;
        }
        StatsRepository.GameSummary game = statsGames.get(statsGameIndex);
        long generation = ++statsEvidenceGeneration;
        statsEvidenceOpen = true;
        statsEvidenceLoading = true;
        statsEvidenceError = "";
        statsEvidence = List.of();
        statsEvidenceSourceCache = "";
        statsEvidenceLines = List.of();
        statsEvidenceWrapWidth = -1f;
        statsEvidenceScroll = 0f;
        statsEvidenceScrollTarget = 0f;
        ensureStatsEvidencePanelBounds();
        captureFrontendModalInput();
        CompletableFuture.runAsync(() -> {
            try {
                List<StatsRepository.ZeroTrustEvidence> evidence =
                        statsRepository.zeroTrustEvidence(game.id());
                Gdx.app.postRunnable(() -> {
                    if (disposed || generation != statsEvidenceGeneration
                            || !statsEvidenceOpen) return;
                    statsEvidence = evidence;
                    statsEvidenceLoading = false;
                    statsEvidenceSourceCache = "";
                    statsEvidenceLines = List.of();
                    statsEvidenceWrapWidth = -1f;
                });
            } catch (Exception failure) {
                LOGGER.log(Level.WARNING,
                        "Could not load Zero Trust evidence", failure);
                Gdx.app.postRunnable(() -> {
                    if (disposed || generation != statsEvidenceGeneration
                            || !statsEvidenceOpen) return;
                    statsEvidenceLoading = false;
                    statsEvidenceError = failure.getMessage() == null
                            ? failure.getClass().getSimpleName()
                            : failure.getMessage();
                });
            }
        }, statsExecutor);
    }

    private void closeStatsEvidence() {
        statsEvidenceOpen = false;
        statsEvidenceLoading = false;
        statsEvidenceError = "";
        statsEvidence = List.of();
        statsEvidenceSourceCache = "";
        statsEvidenceLines = List.of();
        statsEvidenceWrapWidth = -1f;
        statsEvidenceScroll = 0f;
        statsEvidenceScrollTarget = 0f;
        statsEvidenceGeneration++;
        clearStatsEvidenceWindowDrag();
        if (scrollDrag == ScrollDrag.STATS_EVIDENCE) clearScrollDrag();
    }

    private Rectangle ensureStatsEvidencePanelBounds() {
        float worldWidth = viewport.getWorldWidth();
        float worldHeight = viewport.getWorldHeight();
        Rectangle normalized = statsEvidencePanelBounds.width <= 0f
                || statsEvidencePanelBounds.height <= 0f
                        ? GdxFloatingGameLogLayout.initial(worldWidth,
                                worldHeight)
                        : GdxFloatingGameLogLayout.normalize(
                                statsEvidencePanelBounds, worldWidth,
                                worldHeight);
        statsEvidencePanelBounds.set(normalized);
        return statsEvidencePanelBounds;
    }

    private GdxFloatingGameLogLayout.Layout statsEvidenceLayout() {
        return GdxFloatingGameLogLayout.layout(
                ensureStatsEvidencePanelBounds());
    }

    private Rectangle statsEvidenceHeaderDragBounds() {
        GdxFloatingGameLogLayout.Layout layout = statsEvidenceLayout();
        Rectangle header = layout.headerDrag();
        return new Rectangle(header.x, header.y,
                Math.max(1f, header.width - 190f), header.height);
    }

    private void drawStatsEvidenceDialog() {
        GdxFloatingGameLogLayout.Layout layout = statsEvidenceLayout();
        Rectangle panel = layout.panel();
        Rectangle frame = layout.logFrame();
        Rectangle content = layout.content();
        Rectangle track = layout.scrollbarTrack();
        Rectangle close = layout.closeButton();

        GdxUiDialogStyle.drawPanel(shapes, panel.x, panel.y,
                panel.width, panel.height, GOLD, 1f);
        GdxUiDialogStyle.drawInset(shapes, frame.x, frame.y,
                frame.width, frame.height, 1f);
        textFit(uiFont, uppercase(gameText.translate(
                "gdx.stats.zero_trust_evidence_title")),
                layout.headerDrag().x,
                layout.headerDrag().y + 37f, Color.WHITE, false,
                Math.max(1f, layout.headerDrag().width - 190f));

        themedButton(close.x - 158f, close.y, 146f, close.height,
                uppercase(gameText.translate("ui.copiar")),
                ButtonTone.NEUTRAL, this::copyStatsEvidence,
                !statsEvidenceLoading && !statsEvidence.isEmpty(),
                tinyFont);
        themedButton(close.x, close.y, close.width, close.height,
                GdxUiDialogStyle.CLOSE_GLYPH, ButtonTone.DANGER,
                this::closeStatsEvidence, true, tinyFont);

        if (statsEvidenceLoading) {
            textFit(uiFont, gameText.translate("gdx.lobby.media_loading"),
                    content.x + content.width / 2f,
                    content.y + content.height / 2f,
                    CYAN, true, content.width - 60f);
            statsEvidenceScrollMaximum = 0f;
            statsEvidenceScrollThumbHeight = track.height;
            return;
        }
        if (!statsEvidenceError.isBlank()) {
            textFit(smallFont, statsEvidenceError,
                    content.x + content.width / 2f,
                    content.y + content.height / 2f,
                    ORANGE, true, content.width - 60f);
            statsEvidenceScrollMaximum = 0f;
            statsEvidenceScrollThumbHeight = track.height;
            return;
        }

        List<String> lines = statsEvidenceDisplayLines(content.width);
        statsEvidenceScrollMaximum = Math.max(0f,
                lines.size() * STATS_EVIDENCE_LINE_HEIGHT - content.height);
        statsEvidenceScrollTarget = MathUtils.clamp(
                statsEvidenceScrollTarget, 0f,
                statsEvidenceScrollMaximum);
        statsEvidenceScroll = MathUtils.clamp(statsEvidenceScroll, 0f,
                statsEvidenceScrollMaximum);
        float thumb = statsEvidenceScrollMaximum <= 0f
                ? track.height
                : Math.max(44f, track.height * content.height
                        / Math.max(content.height,
                                lines.size() * STATS_EVIDENCE_LINE_HEIGHT));
        statsEvidenceScrollThumbHeight = thumb;
        float travel = Math.max(0f, track.height - thumb);
        float ratio = statsEvidenceScrollMaximum <= 0f ? 0f
                : statsEvidenceScroll / statsEvidenceScrollMaximum;
        shapes.setColor(new Color(0x18304cff));
        roundedRect(track.x, track.y, track.width, track.height, 7f);
        shapes.setColor(CYAN);
        roundedRect(track.x, track.y + travel * (1f - ratio),
                track.width, thumb, 7f);
    }

    private void drawStatsEvidenceTextLayer() {
        if (!statsEvidenceOpen || statsEvidenceLoading
                || !statsEvidenceError.isBlank()) return;
        GdxFloatingGameLogLayout.Layout layout = statsEvidenceLayout();
        Rectangle content = layout.content();
        List<String> lines = statsEvidenceDisplayLines(content.width);
        enableWorldScissor(content);
        batch.begin();
        for (int index = 0; index < lines.size(); index++) {
            float baseline = content.y + content.height - 8f
                    - index * STATS_EVIDENCE_LINE_HEIGHT
                    + statsEvidenceScroll;
            if (baseline < content.y - STATS_EVIDENCE_LINE_HEIGHT
                    || baseline > content.y + content.height
                            + STATS_EVIDENCE_LINE_HEIGHT) {
                continue;
            }
            String line = lines.get(index);
            Color color = line.startsWith("=====") ? GOLD
                    : line.equals("ZERO_TRUST_EVIDENCE_V1")
                            || line.equals("RECEIPTS")
                            || line.equals("DATABASE_SNAPSHOT")
                                    ? CYAN : Color.WHITE;
            debugLogFont.setColor(color);
            GdxPixelText.draw(debugLogFont, batch, line,
                    content.x, baseline, viewport);
        }
        debugLogFont.setColor(Color.WHITE);
        batch.end();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
    }

    private List<String> statsEvidenceDisplayLines(float contentWidth) {
        String source = statsEvidenceSource();
        float wrapWidth = Math.max(1f, contentWidth - 6f);
        if (source.equals(statsEvidenceSourceCache)
                && Math.abs(wrapWidth - statsEvidenceWrapWidth) <= 0.5f) {
            return statsEvidenceLines;
        }
        glyph.setText(debugLogFont, "M");
        int maximumCharacters = Math.max(1,
                (int) Math.floor(wrapWidth / Math.max(1f, glyph.width)));
        List<String> wrapped = new ArrayList<>();
        for (String line : source.split("\\R", -1)) {
            if (line.isEmpty()) {
                wrapped.add("");
                continue;
            }
            for (int start = 0; start < line.length();
                    start += maximumCharacters) {
                wrapped.add(line.substring(start,
                        Math.min(line.length(), start + maximumCharacters)));
            }
        }
        statsEvidenceSourceCache = source;
        statsEvidenceWrapWidth = wrapWidth;
        statsEvidenceLines = List.copyOf(wrapped);
        return statsEvidenceLines;
    }

    private String statsEvidenceSource() {
        if (statsEvidence.isEmpty()) {
            return gameText.translate("gdx.stats.zero_trust_no_evidence");
        }
        StringBuilder value = new StringBuilder();
        for (int index = 0; index < statsEvidence.size(); index++) {
            StatsRepository.ZeroTrustEvidence evidence =
                    statsEvidence.get(index);
            if (index > 0) value.append("\n\n");
            value.append("===== INCIDENT ").append(index + 1)
                    .append(" | HAND ").append(evidence.handCounter())
                    .append(" | ")
                    .append(Instant.ofEpochSecond(
                            evidence.timestampSeconds()))
                    .append(" =====\n");
            String report = Objects.requireNonNullElse(evidence.report(), "");
            if (report.isBlank()) {
                value.append("reason=")
                        .append(Objects.requireNonNullElse(
                                evidence.reason(), "unknown"))
                        .append('\n')
                        .append("evidence_file=")
                        .append(Objects.requireNonNullElse(
                                evidence.path(), ""))
                        .append('\n')
                        .append(gameText.translate(
                                "gdx.stats.zero_trust_legacy_evidence"));
            } else {
                value.append(report.stripTrailing());
            }
        }
        return value.toString();
    }

    private void copyStatsEvidence() {
        Gdx.app.getClipboard().setContents(statsEvidenceSource());
        showToast(gameText.translate("gdx.settings.debug.copied"));
    }

    private boolean beginStatsEvidenceWindowDrag(
            StatsEvidenceWindowDrag drag,
            GdxFloatingGameLogLayout.ResizeHandle resizeHandle,
            float x, float y, int pointerIndex, int button) {
        if (!statsEvidenceWindowPointer.capture(pointerIndex, button)) {
            return false;
        }
        statsEvidenceWindowDrag = drag;
        statsEvidenceResizeHandle = resizeHandle;
        statsEvidenceDragStartX = x;
        statsEvidenceDragStartY = y;
        statsEvidenceDragStartBounds.set(ensureStatsEvidencePanelBounds());
        return true;
    }

    private void updateStatsEvidenceWindowDrag(float x, float y) {
        Rectangle updated = switch (statsEvidenceWindowDrag) {
            case MOVE -> GdxFloatingGameLogLayout.moved(
                    statsEvidenceDragStartBounds,
                    x - statsEvidenceDragStartX,
                    y - statsEvidenceDragStartY,
                    viewport.getWorldWidth(), viewport.getWorldHeight());
            case RESIZE -> GdxFloatingGameLogLayout.resized(
                    statsEvidenceDragStartBounds,
                    statsEvidenceResizeHandle,
                    x - statsEvidenceDragStartX,
                    y - statsEvidenceDragStartY,
                    viewport.getWorldWidth(), viewport.getWorldHeight());
            case NONE -> null;
        };
        if (updated != null) {
            statsEvidencePanelBounds.set(updated);
            statsEvidenceWrapWidth = -1f;
            statsEvidenceScrollTarget = MathUtils.clamp(
                    statsEvidenceScrollTarget, 0f,
                    statsEvidenceScrollMaximum);
            statsEvidenceScroll = MathUtils.clamp(statsEvidenceScroll, 0f,
                    statsEvidenceScrollMaximum);
        }
    }

    private void clearStatsEvidenceWindowDrag() {
        statsEvidenceWindowPointer.clear();
        statsEvidenceWindowDrag = StatsEvidenceWindowDrag.NONE;
        statsEvidenceResizeHandle =
                GdxFloatingGameLogLayout.ResizeHandle.NONE;
    }

    private void drawStatsPicker() {
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
        themedButton(1350f, 865f, 54f, 48f,
                GdxUiDialogStyle.CLOSE_GLYPH, ButtonTone.DANGER,
                () -> statsPicker = StatsPicker.NONE, true,
                settingsStepperSignFont);

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
            statsTextFit(smallFont, row.player(), x, y + 7f, Color.WHITE,
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
            statsTextFit(smallFont, value, 1735f, y + 7f, GOLD,
                    true, 140f);
        }
        if (statsMetrics.isEmpty()) {
            statsTextFit(smallFont, gameText.translate("gdx.stats.no_data"),
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
        statsTextFit(tinyFont, uppercase(label), labelX, y, CYAN, false,
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
                statsTextFit(smallFont, values[column], columns[column], y + 7f,
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
            statsTextFit(smallFont, row.player(), columns[0], y + 7f,
                    Color.WHITE, false, widths[0]);
            drawStatsCards(row.holeCards(), columns[1], y, widths[1],
                    "*****");
            statsTextFit(smallFont, statsHandRank(row.handValue()), columns[2],
                    y + 7f, Color.WHITE, false, widths[2]);
            statsTextFit(smallFont, Integer.toString(row.handCounter()),
                    columns[3], y + 7f, Color.WHITE, false, widths[3]);
            statsTextFit(smallFont, money(row.profit()), columns[4], y + 7f,
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
        if (game.hasZeroTrustIncident()) {
            themedButton(86f, 112f, 348f, 56f,
                    uppercase(gameText.translate(
                            "gdx.stats.zero_trust_evidence"))
                            + " (" + game.zeroTrustIncidents() + ")",
                    ButtonTone.DANGER, this::openStatsEvidence,
                    !statsLoading, smallFont);
        }
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
        drawStatsSummaryScrollbar(topY, bottom, visibleHeight, contentHeight);
        float firstBaseline = topY - 10f + statsSummaryPlayersScroll;
        for (int index = 0; index < safePlayers.size(); index++) {
            float baseline = firstBaseline - index * rowHeight;
            if (baseline > topY - 4f || baseline < bottom + 8f) continue;
            text(tinyFont, ellipsize(tinyFont, safePlayers.get(index), width - 148f),
                    x + 126f, baseline, Color.WHITE, false);
        }
    }

    private void drawStatsSummaryScrollbar(float topY, float bottom,
            float visibleHeight, float contentHeight) {
        statsSummaryPlayersViewport.set(86f, bottom, 348f, topY - bottom);
        float maximum = Math.max(0f, contentHeight - visibleHeight);
        statsSummaryPlayersScrollMaximum = maximum;
        statsSummaryPlayersScrollTarget = MathUtils.clamp(
                statsSummaryPlayersScrollTarget, 0f, maximum);
        statsSummaryPlayersScroll = MathUtils.clamp(statsSummaryPlayersScroll, 0f, maximum);
        if (maximum <= 0f) return;
        float trackX = 424f;
        float trackY = bottom + 6f;
        float trackHeight = topY - bottom - 12f;
        statsSummaryPlayersScrollTrack.set(trackX, trackY, 5f, trackHeight);
        shapes.setColor(new Color(0x18304cff));
        roundedRect(trackX, trackY, 5f, trackHeight, 2.5f);
        float thumb = Math.max(18f, trackHeight * visibleHeight / contentHeight);
        statsSummaryPlayersScrollThumbHeight = thumb;
        float thumbY = trackY + (trackHeight - thumb)
                * (1f - statsSummaryPlayersScroll / maximum);
        shapes.setColor(CYAN);
        roundedRect(trackX, thumbY, 5f, thumb, 2.5f);
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
        text(tinyFont, uppercase(gameText.translate("ui.cartas_comunitarias")),
                86f, y + 8f, MUTED, false);
        drawStatsCards(hand.communityCards(), 86f, y - 25f, 348f, "-----", 5);
        shapes.setColor(new Color(0x31445f88));
        shapes.rect(86f, y - 50f, 348f, 1f);
        y -= 71f;
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
        drawStatsStreetRosters(hand, y + 17f);
    }

    private void drawStatsStreetRosters(StatsRepository.HandSummary hand, float topY) {
        String[] labels = {"stats.jugadores_preflop", "stats.jugadores_flop",
            "stats.jugadores_turn", "stats.jugadores_river"};
        List<List<String>> rosters = List.of(hand.preflopPlayers(), hand.flopPlayers(),
                hand.turnPlayers(), hand.riverPlayers());
        float bottom = 89f;
        float lineHeight = 20f;
        float groupGap = 7f;
        float contentHeight = 0f;
        List<List<String>> wrapped = new ArrayList<>(rosters.size());
        for (List<String> players : rosters) {
            List<String> lines = wrapText(tinyFont, statsPlayers(players), 327f, Integer.MAX_VALUE);
            wrapped.add(lines);
            contentHeight += (lines.size() + 1) * lineHeight + groupGap;
        }
        drawStatsSummaryScrollbar(topY, bottom, topY - bottom, contentHeight);
        float baseline = topY - 13f + statsSummaryPlayersScroll;
        for (int i = 0; i < labels.length; i++) {
            if (baseline <= topY - 4f && baseline >= bottom + 9f) {
                text(tinyFont, uppercase(gameText.translate(labels[i])),
                        86f, baseline, MUTED, false);
            }
            baseline -= lineHeight;
            for (String line : wrapped.get(i)) {
                if (baseline <= topY - 4f && baseline >= bottom + 9f) {
                    text(tinyFont, ellipsize(tinyFont, line, 327f),
                            86f, baseline, Color.WHITE, false);
                }
                baseline -= lineHeight;
            }
            baseline -= groupGap;
        }
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
            statsTextFit(smallFont, row.player(), columns[0], y + 7f,
                    Color.WHITE, false, widths[0]);
            statsTextFit(smallFont, row.winner()
                    ? gameText.translate("ui.si")
                            : gameText.translate("gdx.stats.no"),
                    columns[1], y + 7f, row.winner() ? GOLD : MUTED,
                    false, widths[1]);
            drawStatsCards(row.holeCards(), columns[2], y, widths[2],
                    "*****");
            statsTextFit(smallFont, statsHandRank(row.handValue()), columns[3],
                    y + 7f, Color.WHITE, false, widths[3]);
            statsTextFit(smallFont, money(row.pay()), columns[4], y + 7f,
                    Color.WHITE, false, widths[4]);
            statsTextFit(smallFont, money(row.profit()), columns[5], y + 7f,
                    result, false, widths[5]);
        }
        if (statsShowdown.isEmpty()) {
            statsTextFit(smallFont, gameText.translate("gdx.stats.no_showdown"),
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
        drawStatsCards(cards, x, centerY, maxWidth, empty, 2);
    }

    private void drawStatsCards(List<String> cards, float x, float centerY,
            float maxWidth, String empty, int maxCards) {
        List<StatsCardGlyph> decoded = cards == null ? List.of()
                : cards.stream().map(GdxFrontendScreen::decodeStatsCard)
                        .filter(Objects::nonNull).limit(maxCards).toList();
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
        float labelWidth = 132f;
        shapes.setColor(new Color(0x31445f88));
        shapes.rect(86f, y - 23f, 348f, 1f);
        text(tinyFont, ellipsize(tinyFont, uppercase(label), labelWidth),
                86f, y + 8f, MUTED, false);
        float valueWidth = 348f - labelWidth - 12f;
        String safe = Objects.requireNonNullElse(value, "-----");
        BitmapFont font = fits(smallFont, safe, valueWidth) ? smallFont : tinyFont;
        text(font, ellipsize(font, safe, valueWidth),
                98f + labelWidth, y + 8f, Color.WHITE, false);
    }

    private void drawStatsBalances() {
        if (statsBalances.isEmpty()) {
            statsTextFit(smallFont,
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
                statsTextFit(tinyFont, values[column], columns[column], y + 6f,
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
        PokerChromium.useLanguage(next);
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
        rulesWindow.close();
        disposeAboutEasterEgg();
        handGeneratorOpen = false;
        aboutEasterEggClicks = 0;
        aboutOpen = false;
        syncMusicForSurface();
    }

    private void openRobertRules() {
        captureFrontendModalInput();
        if (!rulesWindow.open(viewport, initialProperties, preferences::saveDeferred,
                gameText.language())) {
            showFrontendNotice(gameText.translate(GdxRobertRulesWindow.TITLE),
                    gameText.translate("gdx.rules.unavailable"), false);
        }
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
        if (updateCheck != null || disposed) return;
        updateResult = null;
        try {
            CompletableFuture<UpdateService.CheckResult> check = updateService.checkLatest();
            updateCheck = check;
            check.whenComplete((result, failure) -> {
                if (disposed) return;
                Gdx.app.postRunnable(() -> {
                    if (disposed || updateCheck != check) return;
                    updateCheck = null;
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
            updateCheck = null;
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
        clearScrollDrag();
        clearPointerRepeat();
        clearActiveField();
        hits.clear();
        secondaryHits.clear();
        textFieldHits.clear();
        passwordRevealHits.clear();
        editMenuHits.clear();
        tooltipHits.clear();
        statsEvidenceWindowPointer.clear();
        statsEvidenceWindowDrag = StatsEvidenceWindowDrag.NONE;
        lobbyGallery.suspendPointer();
        if (statsBrowser != null) {
            if (statsBrowser.pressed()) statsBrowser.mouse(MouseEvent.MOUSE_RELEASED, -1f, -1f);
            statsBrowser.mouse(MouseEvent.MOUSE_EXITED, -1f, -1f);
            statsBrowser.unfocus();
            statsBrowserFocused = false;
            statsBrowserPointer.clear();
        }
    }

    static boolean deferredUpdateAvailable(boolean dismissed,
            boolean promptOpen, UpdateService.CheckResult result) {
        return dismissed && !promptOpen && result != null
                && result.status() == UpdateService.Status.UPDATE_AVAILABLE;
    }

    static boolean updateCheckingModalVisible(boolean checkInFlight,
            UpdateService.CheckResult result) {
        return checkInFlight && result == null;
    }

    private boolean updateCheckingModalVisible() {
        return surface == Surface.MENU && updateCheckingModalVisible(
                updateCheck != null, updateResult);
    }

    private void cancelUpdateCheck() {
        CompletableFuture<UpdateService.CheckResult> check = updateCheck;
        if (check == null) return;
        updateCheck = null;
        check.cancel(true);
        LOGGER.info("Startup update check cancelled");
        hits.clear();
        resolveStartupUpdateGate();
        syncMusicForSurface();
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

    private void drawUpdateCheckingDialog() {
        hits.clear();
        float panelX = 560f;
        float panelY = 366f;
        float panelW = 800f;
        float panelH = 310f;
        GdxUiDialogStyle.drawPanel(shapes, panelX, panelY, panelW, panelH,
                CYAN_DARK, 1f);
        textFit(headingFont, uppercase(gameText.translate(
                "ui.comprobando_actualizacion")),
                WIDTH / 2f, panelY + 222f, GOLD, true, panelW - 100f);
        drawIndeterminateProgressBar(panelX + 100f, panelY + 135f,
                panelW - 200f, 18f, 170f, 0.52f, 9f);
        themedButton(WIDTH / 2f - 140f, panelY + 35f, 280f, 62f,
                uppercase(gameText.translate("ui.cancelar")),
                ButtonTone.NEUTRAL, this::cancelUpdateCheck, true);
    }

    private void drawUpnpOpeningDialog() {
        hits.clear();
        float panelX = 510f;
        float panelY = 376f;
        float panelW = 900f;
        float panelH = 290f;
        GdxUiDialogStyle.drawPanel(shapes, panelX, panelY, panelW, panelH,
                CYAN_DARK, 1f);
        textFit(headingFont, uppercase(gameText.translate(
                "gdx.newgame.upnp_checking")), WIDTH / 2f,
                panelY + 190f, GOLD, true, panelW - 100f);
        textFit(smallFont, gameText.translate("gdx.newgame.upnp_port",
                connection == null ? "" : connection.port()), WIDTH / 2f,
                panelY + 135f, Color.WHITE, true, panelW - 140f);
        drawIndeterminateProgressBar(panelX + 110f, panelY + 65f,
                panelW - 220f, 18f, 170f, 0.52f, 9f);
    }

    private void drawFrontendNotice() {
        hits.clear();
        GdxUiDialogStyle.NoticeLayout layout = drawNoticeChrome(
                frontendNoticeTitle, frontendNoticeMessage,
                frontendNoticeError);
        Rectangle button = layout.button();
        themedButton(button.x, button.y, button.width, button.height,
                uppercase(gameText.translate("ui.aceptar")),
                frontendNoticeError ? ButtonTone.DANGER : ButtonTone.POSITIVE,
                this::dismissFrontendNotice, true);
    }

    private void drawModAdultWarning() {
        hits.clear();
        GdxUiDialogStyle.ConfirmationLayout layout = drawConfirmationChrome(
                uppercase(gameText.translate("gdx.dialog.warning")),
                gameText.translate("mod.el_mod_cargado_contiene_material"));
        Rectangle cancel = layout.negativeButton();
        Rectangle accept = layout.positiveButton();
        themedButton(cancel.x, cancel.y, cancel.width, cancel.height,
                uppercase(gameText.translate("ui.no")), ButtonTone.DANGER,
                this::declineModAdultContent, true);
        themedButton(accept.x, accept.y, accept.width, accept.height,
                uppercase(gameText.translate("ui.si")), ButtonTone.POSITIVE,
                this::acceptModAdultContent, true);
    }

    private boolean fileChooserPending() {
        return settingsBackupPending || avatarSelectionPending || customBotAvatarSelectionPending;
    }

    void requestApplicationExit() {
        if (applicationExitConfirmation || fileChooserPending()) return;
        rulesWindow.close();
        if (profileBackupSelection != null) {
            closeProfileBackupSelection();
            return;
        }
        dropdown = Dropdown.NONE;
        editMenu = null;
        clearActiveField();
        applicationExitConfirmation = true;
    }

    private void drawApplicationExitConfirmation() {
        hits.clear();
        secondaryHits.clear();
        textFieldHits.clear();
        passwordRevealHits.clear();
        editMenuHits.clear();
        GdxUiDialogStyle.ConfirmationLayout layout = drawConfirmationChrome(
                uppercase(gameText.translate("gdx.dialog.confirmation")),
                uppercase(gameText.translate(
                        "exit.salir_del_juego_pregunta")));
        Rectangle cancel = layout.negativeButton();
        Rectangle exit = layout.positiveButton();
        themedButton(cancel.x, cancel.y, cancel.width, cancel.height,
                uppercase(gameText.translate("ui.cancelar")),
                ButtonTone.NEUTRAL,
                () -> applicationExitConfirmation = false, true);
        themedButton(exit.x, exit.y, exit.width, exit.height,
                uppercase(gameText.translate("ui.salir")),
                ButtonTone.DANGER, this::confirmApplicationExit, true);
    }

    private void confirmApplicationExit() {
        applicationExitConfirmation = false;
        Gdx.app.exit();
    }

    private void acceptModAdultContent() {
        try {
            presentationSettings.acknowledgeModAdultContent();
            modAdultWarning = false;
        } catch (IOException failure) {
            LOGGER.log(Level.WARNING,
                    "Could not persist adult-mod acknowledgement", failure);
            modAdultWarning = false;
            showFrontendNotice(gameText.translate("gdx.dialog.error"),
                    gameText.translate("gdx.mod.adult_ack_failed"), true);
        }
    }

    private void declineModAdultContent() {
        modAdultWarning = false;
        Gdx.app.exit();
    }

    /** Native GDX counterpart of Swing's AboutDialog, including its music. */
    private void drawAboutDialog() {
        hits.clear();
        secondaryHits.clear();

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
            textFit(smallFont, modLabel,
                    WIDTH / 2f, ABOUT_LOGO_Y - 14f,
                    Color.WHITE, true, 620f);
        }

        centeredWrappedText(smallFont,
                gameText.translate("about.gracias_1"), WIDTH / 2f,
                682f, w - 130f, 24f, 2, MUTED);
        centeredWrappedText(smallFont,
                gameText.translate("about.gracias_2"), WIDTH / 2f,
                653f, w - 130f, 24f, 2, MUTED);
        centeredWrappedText(smallFont,
                gameText.translate("about.centimos"), WIDTH / 2f,
                624f, w - 130f, 24f, 2, MUTED);

        textFit(headingFont, gameText.translate("about.dedicado"),
                WIDTH / 2f, ABOUT_MEMORIAL_CENTER_Y,
                Color.WHITE, true, 830f);

        float musicY = ABOUT_MUSIC_FIRST_LINE_Y;
        String[] musicKeys = {
            "about.musica_juego", "about.musica_espera",
            "about.musica_stats", "about.musica_gestor_bots",
            "about.musica_recuperacion", "about.musica_about"
        };
        for (String key : musicKeys) {
            centeredWrappedText(smallFont, gameText.translate(key),
                    WIDTH / 2f, musicY, w - 150f, 24f, 2, MUTED);
            musicY -= ABOUT_MUSIC_LINE_GAP;
        }

        centeredWrappedText(smallFont, gameText.translate("about.copyright"),
                WIDTH / 2f, ABOUT_COPYRIGHT_Y,
                w - 150f, ABOUT_COPYRIGHT_LINE_HEIGHT,
                ABOUT_COPYRIGHT_MAX_LINES, MUTED);
        hit(WIDTH / 2f - 30f, 235f, 60f, 60f, this::openRobertRules);
        tooltip(WIDTH / 2f - 30f, 235f, 60f, 60f, "tooltip.robert_rules");
        textFit(smallFont, gameText.translate("about.hecho_a_mano"),
                WIDTH / 2f, 226f, Color.WHITE, true,
                ABOUT_CONTENT_WIDTH - 90f);
        float footerStart = contentX + 26f;
        float footerWidth = ABOUT_CONTENT_WIDTH - 52f;
        float buildWidth = footerWidth * 0.18f;
        float runtimeWidth = footerWidth * 0.24f;
        float systemWidth = footerWidth - buildWidth - runtimeWidth;
        textFit(smallFont, "Jn 8:32",
                footerStart + buildWidth / 2f, 174f,
                MUTED, true, buildWidth - 30f);
        String runtime = aboutRuntimeText() + " "
                + gameText.translate("ui.hilos");
        textFit(smallFont, runtime,
                footerStart + buildWidth + runtimeWidth / 2f, 174f,
                MUTED, true, runtimeWidth - 30f);
        String system = aboutSystemText();
        textFit(smallFont, system,
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

        textFit(titleFont,
                uppercase(gameText.translate("gdx.hand_generator.title")),
                WIDTH / 2f, HAND_GENERATOR_TITLE_BASELINE, GOLD, true,
                w - 160f);
        float nameWidth = 680f;
        float nameX = WIDTH / 2f - nameWidth / 2f;
        float nameY = HAND_GENERATOR_NAME_Y;
        outerBox(nameX, nameY, nameWidth, HAND_GENERATOR_NAME_HEIGHT,
                CYAN_DARK, PANEL_LIGHT);
        textFit(settingsStepperSignFont,
                gameText.translate(example.translationKey()),
                WIDTH / 2f, nameY + 45f, Color.WHITE, true,
                nameWidth - 48f);
        textFit(uiFont, uppercase(gameText.translate(
                        "gdx.hand_generator.probability",
                        example.probability())),
                WIDTH / 2f, nameY - 23f, CYAN, true, 520f);
        hit(WIDTH / 2f - 260f, nameY - 45f, 520f, 36f,
                () -> openExternalUri(
                        URI.create(GdxHandGeneratorModel.POKER_ODDS_URL),
                        "gdx.about.open_failed"));

        themedButton(x + 42f, y + 34f, 250f, 62f,
                "‹  " + uppercase(gameText.translate(
                        "gdx.hand_generator.previous")),
                ButtonTone.NEUTRAL, handGenerator::previous,
                handGenerator.canPrevious());
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
        float rowWidth = HAND_GENERATOR_CARD_WIDTH * cardCount
                + HAND_GENERATOR_CARD_GAP * (cardCount - 1);
        float rowX = HAND_GENERATOR_PANEL_X
                + (HAND_GENERATOR_PANEL_WIDTH - rowWidth) / 2f;
        return new Rectangle(
                rowX + cardIndex * (HAND_GENERATOR_CARD_WIDTH
                        + HAND_GENERATOR_CARD_GAP),
                HAND_GENERATOR_CARD_Y, HAND_GENERATOR_CARD_WIDTH,
                HAND_GENERATOR_CARD_HEIGHT);
    }

    private Texture handGeneratorCardTexture(String code) {
        GdxHandGeneratorCardSource source =
                GdxHandGeneratorCardSource.resolve(presentationSettings, code);
        return handGeneratorCardTextures.computeIfAbsent(source.cacheKey(),
                ignored -> {
            Texture texture = new Texture(source.external()
                    ? Gdx.files.absolute(source.externalPath().toString())
                    : Gdx.files.internal(source.internalPath()), true);
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

    /** Product identity is never part of a modal's blurred scene. */
    private void drawFrontendVersionLabelTopLayer() {
        TextItem label = fittedTextItem(versionFont,
                GdxProductVersionBrand.label(presentationSettings),
                GdxProductVersionBrand.X,
                GdxProductVersionBrand.BASELINE_Y,
                new Color((GdxProductVersionBrand.RGB << 8)
                        | Math.round(GdxProductVersionBrand.ALPHA * 255f)),
                false, GdxProductVersionBrand.MAX_WIDTH);
        batch.begin();
        drawTextItem(label);
        batch.end();
    }

    private void drawUnifiedModalBackdrop() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA,
                GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        GdxUiDialogStyle.drawBackdrop(shapes, WIDTH, HEIGHT);
        shapes.end();
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
                menuRevealProgress(), aboutOpen || updatePromptOpen || updateCheckingModalVisible());
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
        String normalized = Objects.requireNonNullElse(value, "")
                .replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.isEmpty() || maxLines <= 0) return List.of();
        List<String> lines = new ArrayList<>();
        String[] paragraphs = normalized.split("\n", -1);
        for (int paragraph = 0; paragraph < paragraphs.length
                && lines.size() < maxLines; paragraph++) {
            appendWrappedParagraph(font, paragraphs[paragraph], maxWidth,
                    maxLines, lines);
            if (paragraph + 1 < paragraphs.length
                    && lines.size() == maxLines) {
                int last = lines.size() - 1;
                lines.set(last, ellipsize(font,
                        lines.get(last) + " …", maxWidth));
            }
        }
        return lines;
    }

    private void appendWrappedParagraph(BitmapFont font, String paragraph,
            float maxWidth, int maxLines, List<String> lines) {
        String normalized = paragraph.strip();
        if (normalized.isEmpty()) {
            if (lines.size() < maxLines) lines.add("");
            return;
        }
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
                return;
            }
            lines.add(current.toString());
            current.setLength(0);
            current.append(word);
        }
        if (lines.size() < maxLines && !current.isEmpty()) {
            lines.add(ellipsize(font, current.toString(), maxWidth));
        }
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
        LobbyConnectionRowLayout connectionLayout = lobbyConnectionRowLayout(
                LOBBY_LEFT_CONTENT_X, LOBBY_LEFT_CONTENT_WIDTH,
                state.host() && "UPNP_OK".equals(state.statusDetail()));
        float connectionLabelX = LOBBY_LEFT_CONTENT_X + 16f;
        float connectionValueX = connectionLayout.valueX();
        Rectangle copyBounds = state.host() ? lobbyConnectionCopyBounds() : null;
        float serverWidth = copyBounds == null ? connectionLayout.fullValueWidth()
                : copyBounds.x - LOBBY_CONNECTION_STATUS_GAP - connectionValueX;
        float serverBaseline = 733f;
        text(tinyFont, uppercase(gameText.translate(
                state.host() ? "gdx.lobby.local_address" : "ui.servidor")),
                connectionLabelX, serverBaseline, MUTED, false);
        if (connectionLayout.statusVisible()) {
            String networkStatus = lobbyNetworkStatusText(
                    state.statusDetail(), gameText);
            outerBox(connectionLayout.statusX(), LOBBY_PUBLIC_ADDRESS_BASELINE - 16f,
                    connectionLayout.statusWidth(), 22f,
                    new Color(0x46d78dff), new Color(0x103a2dd9));
            textFit(tinyFont, uppercase(networkStatus),
                    connectionLayout.statusX()
                            + connectionLayout.statusWidth() / 2f,
                    LOBBY_PUBLIC_ADDRESS_BASELINE - 1f, new Color(0x76f0adff), true,
                    connectionLayout.statusWidth() - 12f);
        }
        String serverEndpoint = displayedLobbyEndpoint(state);
        textFit(smallFont, serverEndpoint, connectionValueX,
                serverBaseline,
                Color.WHITE, false, serverWidth);
        if (copyBounds != null) {
            text(tinyFont, uppercase(gameText.translate(
                    "gdx.lobby.public_address")), connectionLabelX,
                    LOBBY_PUBLIC_ADDRESS_BASELINE,
                    MUTED, false);
            String publicEndpoint = lobbyPublicAddressEndpoint(
                    serverEndpoint, lobbyPublicAddress);
            String publicAddress = lobbyPublicAddressLoading
                    ? gameText.translate("gdx.loading")
                    : publicEndpoint.isBlank()
                            ? gameText.translate(
                                    "gdx.lobby.public_address_unavailable")
                            : publicEndpoint;
            textFit(smallFont, publicAddress, connectionValueX,
                    LOBBY_PUBLIC_ADDRESS_BASELINE,
                    publicEndpoint.isBlank() ? DISABLED : Color.WHITE,
                    false, connectionLayout.valueWidth());
            compactIconButton(copyBounds, lobbyConnectionCopyIcon,
                    "conn.copiar_datos_de_conexion", this::copyLobbyConnectionData,
                    true, 18f);
        }
        drawLobbyGameInfo(state, state.host() ? LOBBY_GAME_INFO_Y
                : connectionY - 12f - LOBBY_GAME_INFO_HEIGHT);
        if (state.host()) {
            lobbyPasswordButton(LOBBY_LEFT_ACTION_X, LOBBY_PASSWORD_Y,
                    LOBBY_LEFT_ACTION_WIDTH, LOBBY_PASSWORD_HEIGHT,
                    uppercase(gameText.translate(lobbyPasswordActionKey(
                            connection == null ? "" : connection.password()))),
                    lobbyPasswordEnabled(connection == null
                            ? "" : connection.password()),
                    this::openLobbyPasswordDialog,
                    !lobbyCommandPending && !state.startingOrStarted());
            lobbyBotButton(LOBBY_LEFT_ACTION_X, LOBBY_ADD_BOT_Y,
                    LOBBY_LEFT_ACTION_WIDTH, LOBBY_ADD_BOT_HEIGHT,
                    uppercase(gameText.translate("ui.anadir_bot")),
                    () -> submitLobbyCommand(new LobbyCommand.AddBot(), null),
                    !lobbyCommandPending && lobbyCanAddBot(state));
            themedButton(LOBBY_LEFT_ACTION_X, LOBBY_PLAY_BUTTON_Y,
                    LOBBY_LEFT_ACTION_WIDTH,
                    LOBBY_PLAY_BUTTON_HEIGHT,
                    uppercase(gameText.translate("ui.a_jugar")),
                    ButtonTone.POSITIVE,
                    () -> lobbyConfirmation = LobbyConfirmation.START,
                    !lobbyCommandPending && state.participants().size() >= 2
                            && !state.startingOrStarted());
        } else {
            textFit(uiFont, lobbyPhaseText(state), 250f, 350f,
                    MUTED, true, 340f);
        }

        lobbyPanel(495f, 180f, 900f, 650f,
                uppercase(gameText.translate("chat.chat_de_la_timba")));
        if (!lobbyEmojiPickerOpen) {
            drawLobbyMessages(state.chat());
        }
        Rectangle emojiBounds = lobbyComposerEmojiBounds();
        Rectangle imageBounds = lobbyComposerImageBounds();
        Rectangle voiceBounds = lobbyVoiceButtonBounds();
        drawLobbyComposer(lobbyComposerInputBounds());
        Runnable toggleEmoji = () -> {
            lobbyEmojiPickerOpen = !lobbyEmojiPickerOpen;
            activateField("lobbyChat");
        };
        compactIconButton(emojiBounds, emojiAtlas.emoji(1),
                lobbyEmojiPickerOpen ? "tooltip.chat.emoji_close"
                        : "tooltip.chat.emoji_open", toggleEmoji, true, 44f);
        compactIconButton(imageBounds, lobbyImageIcon,
                "tooltip.chat.images", this::toggleLobbyImageGallery, true, 44f);
        boolean voiceAllowed = canUseLobbyVoice();
        boolean voiceEnabled = voiceAllowed && !lobbyCommandPending
                && !lobbyVoiceStopping;
        String voiceTooltip;
        if (lobbyVoiceLive || lobbyVoiceOpening) {
            voiceTooltip = "tooltip.chat.voice_release";
        } else if (!voiceAllowed) {
            voiceTooltip = "tooltip.chat.voice_disabled";
        } else {
            voiceTooltip = voiceEnabled ? "tooltip.chat.voice_hold"
                    : "tooltip.chat.voice_busy";
        }
        compactIconButton(voiceBounds, lobbyVoiceIcon,
                voiceTooltip, () -> { }, voiceEnabled, 46f);
        compactButton(LOBBY_COMPOSER_SEND_X, LOBBY_COMPOSER_Y,
                LOBBY_COMPOSER_SEND_WIDTH, LOBBY_COMPOSER_HEIGHT,
                uppercase(gameText.translate("ui.enviar")), true,
                this::sendLobbyText,
                !lobbyCommandPending && lobbyTextSendReady(
                        elapsed, lobbyTextSendAllowedAt, lobbyChatDraft));
        if (lobbyEmojiPickerOpen) {
            drawLobbyEmojiPicker(525f, 315f, 840f, 390f);
        } else if (!lobbyVoiceStatus.isEmpty()) {
            drawLobbyVoiceStatus(535f, lobbyGallery.isOpen() ? 132f : 312f,
                    820f, lobbyGallery.isOpen());
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
            drawLobbyParticipant(state, participant, 1450f, participantY,
                    410f, 54f);
            participantY -= 57f;
        }

        iconButton(35f, 55f, 220f, 70f,
                uppercase(gameText.translate("ui.salir")), 5,
                ButtonTone.DANGER, () -> lobbyConfirmation = LobbyConfirmation.LEAVE,
                !lobbyCommandPending, 28f);
        iconButton(285f, 55f, 360f, 70f,
                uppercase(gameText.translate("menu.visor_capturas")), 6,
                ButtonTone.NEUTRAL, this::openScreenshotViewer,
                !lobbyCommandPending);
        iconButton(675f, 55f, 245f, 70f,
                uppercase(gameText.translate("menu.ajustes")), 3,
                ButtonTone.NEUTRAL, this::openSettings,
                !lobbyCommandPending);
        if (state.host() && !activeNetworkBlocks().isEmpty()) {
            iconButton(950f, 55f, 330f, 70f,
                    uppercase(gameText.translate(
                            "gdx.network_blocks.button")), 7,
                    ButtonTone.DANGER, this::openNetworkBlocks,
                    !lobbyCommandPending);
        }
        drawFooterSoundControl();

    }

    private boolean lobbyCanAddBot(LobbySnapshot state) {
        if (state == null || state.startingOrStarted()
                || state.participants().size()
                        >= LobbySnapshot.MAX_PARTICIPANTS) {
            return false;
        }
        NewGameTableDraft.Settings settings = state.tableSettings();
        if (settings == null || !settings.customBots()
                || !settings.customBotsOnly()) {
            return true;
        }
        return customBotProfiles.stream().anyMatch(profile ->
                state.participants().stream().noneMatch(participant ->
                        participant.nickname().equals(profile.nickname())));
    }

    private void drawLobbyDigitalClock(String time) {
        final float digitWidth = 74f;
        final float digitHeight = 126f;
        final float digitGap = 14f;
        final float colonWidth = 20f;
        final float displayWidth = digitWidth * 4f + digitGap * 4f
                + colonWidth;
        final float x = WIDTH - 48f - displayWidth;
        final float y = LOBBY_CLOCK_Y;

        outerBox(x - 14f, y - 10f, displayWidth + 28f, digitHeight + 20f,
                new Color(0x785a20aa), new Color(0x080b0ecc));
        GdxSevenSegmentDisplay.draw(shapes, time, x, y,
                digitWidth, digitHeight, digitGap, colonWidth,
                new Color(0xffbd38ff), new Color(0x59461f40),
                new Color(0xffa51f44),
                GdxSevenSegmentDisplay.colonsVisible(
                        System.currentTimeMillis()));
    }

    private void drawLobbyParticipant(LobbySnapshot state,
            LobbyParticipant participant, float x, float y, float w, float h) {
        outerBox(x, y, w, h, participant.secure() ? LINE : ORANGE,
                PANEL_LIGHT);
        lobbyAvatars.add(new LobbyAvatarItem(lobbyAvatar(participant),
                x + 8f, y + 6f, 42f));
        shapes.setColor(lobbyLatencyColor(participant));
        GdxAntialiasedRoundedRect.fillCircle(shapes,
                x + 55f, y + h / 2f, 4f);
        int effectiveLatency = lobbyEffectiveLatencyMillis(participant);
        tooltip(x + 43f, y + h / 2f - 12f, 24f, 24f,
                "gdx.table.latency_tooltip",
                effectiveLatency < 0 ? "-" : Integer.toString(effectiveLatency));
        float right = x + w - 10f;
        if (lobbyShowsKickButton(state, participant)) {
            float buttonSize = 34f;
            float buttonX = right - buttonSize;
            float buttonY = y + (h - buttonSize) / 2f;
            themedButton(buttonX, buttonY, buttonSize, buttonSize,
                    GdxUiDialogStyle.CLOSE_GLYPH, ButtonTone.DANGER,
                    () -> requestLobbyKick(participant),
                    !lobbyCommandPending && !state.startingOrStarted());
            tooltip(buttonX, buttonY, buttonSize, buttonSize,
                    "ui.expulsar_jugador");
            right = buttonX - 12f;
        }
        String role = participant.local() ? gameText.translate("gdx.lobby.you")
                : participant.host() ? "HOST" : participant.bot() ? "BOT" : "";
        String latency = participant.latencyAvailable()
                ? (participant.latency() >= 0 ? participant.latency() : "-") + " ms"
                : "";
        float statusWidth = Math.max(textWidth(tinyFont, role),
                textWidth(tinyFont, latency));
        float nameX = x + 68f;
        textFit(smallFont, participant.nickname(), nameX, y + 35f,
                participant.asyncWaiting() ? DISABLED : Color.WHITE, false,
                right - statusWidth - 12f - nameX);
        float statusBaseline = y + (h + tinyFont.getCapHeight()) / 2f;
        boolean stacked = !role.isEmpty() && !latency.isEmpty();
        if (!role.isEmpty()) {
            text(tinyFont, role, right - textWidth(tinyFont, role),
                    statusBaseline + (stacked ? 9f : 0f),
                    participant.local() ? GOLD : CYAN, false);
        }
        if (!latency.isEmpty()) {
            text(tinyFont, latency, right - textWidth(tinyFont, latency),
                    statusBaseline - (stacked ? 9f : 0f), MUTED, false);
        }
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
            Texture loaded = GdxTextureQuality.loadUi(
                    Gdx.files.absolute(key));
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
        return GdxTextureQuality.loadUi(Gdx.files.internal(asset));
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

    private void drawLobbyMessages(List<LobbyChatMessage> messages) {
        float x = LOBBY_COMPOSER_X;
        float right = LOBBY_COMPOSER_SEND_X + LOBBY_COMPOSER_SEND_WIDTH;
        float top = 744f;
        float viewportHeight = 420f;
        float contentWidth = right - x;
        if (lobbyChatLayout == null || lobbyChatLayout.messages() != messages) {
            List<LobbyMessageLayout> layouts = new ArrayList<>(messages.size());
            List<Float> heights = new ArrayList<>(messages.size());
            for (LobbyChatMessage message : messages) {
                LobbyMessageLayout layout = lobbyMessageLayout(message,
                        contentWidth - 48f);
                layouts.add(layout);
                heights.add(layout.height());
            }
            lobbyChatLayout = new LobbyChatLayout(messages, layouts,
                    lobbyChatContentHeight(heights));
        }
        float contentHeight = lobbyChatLayout.contentHeight();
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
        if (!lobbyMedia.isEmpty()) updateLobbyGifBounds(contentWidth - 48f);
        int textStart = texts.size();
        int avatarStart = lobbyAvatars.size();
        int imageStart = uiImages.size();
        float cursorTop = top + maximumScroll - lobbyChatScroll;
        for (int i = 0; i < messages.size(); i++) {
            LobbyChatMessage message = messages.get(i);
            LobbyMessageLayout layout = lobbyChatLayout.layouts().get(i);
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
            float bubbleW = layout.width();
            float bubbleX = local ? right - bubbleW : x;
            boolean selectedMessage = message.sequence()
                    == selectedLobbyChatSequence;
            Color border = selectedMessage ? GOLD : local ? CYAN_DARK : LINE;
            Color fill = local ? new Color(0x0d2638e8)
                    : new Color(0x09131fe8);
            lobbyChatBubbles.add(new LobbyChatBubbleItem(message.sequence(),
                    bubbleX, y, bubbleW, messageHeight, border, fill));
            float bodyX = bubbleX + LOBBY_CHAT_BUBBLE_PADDING;
            float bodyWidth = bubbleW - 2f * LOBBY_CHAT_BUBBLE_PADDING;
            float headerX = bodyX + LOBBY_CHAT_HEADER_AVATAR_OFFSET;
            lobbyAvatars.add(new LobbyAvatarItem(lobbyMessageAvatar(message),
                    bubbleX + 10f, y + messageHeight - 42f, 30f));
            textFit(tinyFont, layout.header(), headerX,
                    y + messageHeight - 12f,
                    local ? CYAN : GOLD, false,
                    bodyWidth - LOBBY_CHAT_HEADER_AVATAR_OFFSET);
            if (layout.video() != null) {
                drawLobbyVideoLink(layout.video(), bodyX, y + 25f, bodyWidth,
                        new Rectangle(bubbleX, Math.max(y, top - viewportHeight),
                                bubbleW, Math.min(y + messageHeight, top)
                                        - Math.max(y, top - viewportHeight)));
            } else if (message.type() == LobbyChatMessage.Type.TEXT) {
                float lineY = y + messageHeight - 52f;
                for (List<LobbyTextRun> line : layout.lines()) {
                    drawLobbyEmojiText(line, bodyX, lineY);
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
                        y + 12f, bodyWidth,
                        messageHeight - LOBBY_IMAGE_VERTICAL_INSET,
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
        drawLobbyChatScrollbar(right + 8f, top - viewportHeight,
                viewportHeight, contentHeight, maximumScroll);
    }

    private void drawLobbyChatScrollbar(float x, float y, float height,
            float contentHeight, float maximumScroll) {
        lobbyChatScrollTrack.set(x, y, 16f, height);
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

    private void drawLobbyChatLayer(boolean settingsLobbyBackdrop) {
        if (!shouldDrawLobbyChatLayer(surface == Surface.LOBBY
                || settingsLobbyBackdrop,
                lobbyEmojiPickerOpen,
                lobbyChatViewport.width, lobbyChatViewport.height)) return;
        enableWorldScissor(lobbyChatViewport);
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
            batch.setShader(roundedTextureShader);
            roundedTextureShader.setUniformf("u_cornerRadius", 0.20f);
            roundedTextureShader.setUniformf("u_edgeSoftness", 0.015f);
            for (LobbyAvatarItem item : lobbyChatAvatars) {
                batch.draw(item.texture, item.x, item.y, item.size, item.size);
            }
            batch.setShader(null);
        }
        for (UiImageItem item : lobbyChatImages) {
            item.drawImage(batch, viewport);
        }
        batch.setColor(Color.WHITE);
        for (TextItem item : lobbyChatTexts) {
            drawTextItem(item);
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
        enableWorldScissor(settingsDebugViewport);
        batch.begin();
        for (TextItem item : settingsDebugTexts) {
            drawTextItem(item);
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
        HdpiUtils.glScissor(screenX, screenY, screenWidth, screenHeight);
    }

    static boolean shouldDrawLobbyChatLayer(boolean lobbySurface,
            boolean emojiPickerOpen,
            float viewportWidth, float viewportHeight) {
        return lobbySurface && !emojiPickerOpen
                && viewportWidth > 0f && viewportHeight > 0f;
    }

    static float lobbyMessageHeight(LobbyChatMessage.Type type) {
        return lobbyMessageHeight(type, 1);
    }

    static float lobbyMessageHeight(LobbyChatMessage.Type type,
            int textLineCount) {
        return switch (type) {
            case IMAGE -> LOBBY_IMAGE_BUBBLE_HEIGHT;
            case PLAYER_JOINED, PLAYER_LEFT -> 44f;
            case TEXT -> 82f + Math.max(0, textLineCount - 1) * 32f;
            default -> 70f;
        };
    }

    static float lobbyMessageWidth(LobbyChatMessage.Type type,
            float desiredWidth, float availableWidth) {
        float maximum = Math.max(0f, availableWidth);
        float preferred = switch (type) {
            case IMAGE -> LOBBY_IMAGE_BUBBLE_WIDTH;
            case VOICE -> 390f;
            default -> desiredWidth;
        };
        return Math.min(maximum, preferred);
    }

    private LobbyMessageLayout lobbyMessageLayout(LobbyChatMessage message,
            float availableWidth) {
        if (message.type() == LobbyChatMessage.Type.PLAYER_JOINED
                || message.type() == LobbyChatMessage.Type.PLAYER_LEFT) {
            return new LobbyMessageLayout("", availableWidth,
                    lobbyMessageHeight(message.type()), List.of(), null);
        }
        String header = message.nickname() + "  -  "
                + CHAT_TIME.format(message.timestamp());
        float padding = 2f * LOBBY_CHAT_BUBBLE_PADDING;
        List<List<LobbyTextRun>> lines = List.of();
        PokerVideoSearch.Video video = null;
        float contentWidth = textWidth(tinyFont, header)
                + LOBBY_CHAT_HEADER_AVATAR_OFFSET;
        if (message.type() == LobbyChatMessage.Type.TEXT) {
            video = PokerVideoChatLink.parse(message.content());
            if (video != null) {
                contentWidth = Math.max(contentWidth, LOBBY_VIDEO_LINK_TITLE_INSET
                        + textWidth(smallFont, video.title()));
            } else {
                lines = wrapLobbyEmojiText(message.content(), availableWidth - padding)
                        .stream().map(this::lobbyHistoryRuns).toList();
                for (List<LobbyTextRun> line : lines) {
                    contentWidth = Math.max(contentWidth, lobbyHistoryLineWidth(line));
                }
            }
        }
        float width = lobbyMessageWidth(message.type(), contentWidth + padding,
                availableWidth);
        return new LobbyMessageLayout(header, width,
                lobbyMessageHeight(message.type(), lines.size()), lines, video);
    }

    private void drawLobbyVideoLink(PokerVideoSearch.Video video, float x,
            float centerY, float width, Rectangle hitBounds) {
        Texture logo = pokerVideoOverlay.minimizedLogo();
        float logoWidth = 28f;
        float logoHeight = logoWidth * logo.getHeight() / logo.getWidth();
        uiImages.add(new UiImageItem(logo, x, centerY - logoHeight / 2f,
                logoWidth, logoHeight));
        boolean over = hitBounds.contains(pointer);
        text(smallFont, ellipsize(smallFont, video.title(),
                width - LOBBY_VIDEO_LINK_TITLE_INSET),
                x + LOBBY_VIDEO_LINK_TITLE_INSET,
                centerY + smallFont.getCapHeight() / 2f,
                over ? Color.WHITE : CYAN, false);
        hit(hitBounds.x, hitBounds.y, hitBounds.width, hitBounds.height,
                () -> openChatVideo(video, message -> showFrontendNotice(
                        gameText.translate("gdx.settings.option.mostrar_videos_poker"),
                        message, false)));
        tooltip(hitBounds.x, hitBounds.y, hitBounds.width, hitBounds.height,
                "gdx.video.share.open");
    }

    private List<LobbyTextRun> lobbyHistoryRuns(String value) {
        return lobbyTextRuns(value, tinyFont, LOBBY_CHAT_EMOJI_ADVANCE);
    }

    private static float lobbyHistoryLineWidth(List<LobbyTextRun> runs) {
        float width = lobbyTextRunWidth(runs);
        if (!runs.isEmpty() && runs.get(runs.size() - 1).emojiId() > 0) {
            width -= LOBBY_CHAT_EMOJI_ADVANCE - LOBBY_CHAT_EMOJI_SIZE;
        }
        return width;
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
            if (lobbyHistoryLineWidth(line + token) <= maxWidth) {
                line.append(token);
                continue;
            }
            if (!line.isEmpty()) {
                lines.add(line.toString().stripTrailing());
                line.setLength(0);
            }
            if (token.isBlank()) continue;
            if (lobbyHistoryLineWidth(token) <= maxWidth
                    || EMOJI_TOKEN.matcher(token)
                    .matches()) {
                line.append(token);
                continue;
            }
            for (int offset = 0; offset < token.length();) {
                int next = token.offsetByCodePoints(offset, 1);
                String glyph = token.substring(offset, next);
                if (!line.isEmpty()
                        && lobbyHistoryLineWidth(line + glyph) > maxWidth) {
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
                && lobbyHistoryLineWidth(value + suffix) > maxWidth) {
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
        return BotNickname.isBot(message.nickname()) ? avatarBot
                : avatarDefault;
    }

    private void updateLobbyGifBounds(float availableWidth) {
        float width = lobbyMessageWidth(LobbyChatMessage.Type.IMAGE, 0f,
                availableWidth) - 2f * LOBBY_CHAT_BUBBLE_PADDING;
        float height = LOBBY_IMAGE_BUBBLE_HEIGHT - LOBBY_IMAGE_VERTICAL_INSET;
        int pixelWidth = Math.max(1, Math.round(width * viewport.getScreenWidth()
                / WIDTH * Gdx.graphics.getBackBufferWidth()
                / Math.max(1, Gdx.graphics.getWidth())));
        int pixelHeight = Math.max(1, Math.round(height * viewport.getScreenHeight()
                / HEIGHT * Gdx.graphics.getBackBufferHeight()
                / Math.max(1, Gdx.graphics.getHeight())));
        if (lobbyGifBounds != null && lobbyGifBounds.width() == pixelWidth
                && lobbyGifBounds.height() == pixelHeight) {
            pendingLobbyGifBounds = null;
            return;
        }
        long now = System.nanoTime();
        if (lobbyGifBounds == null) {
            lobbyGifBounds = new StreamingGifTextureAnimation.FrameSize(
                    pixelWidth, pixelHeight);
        } else if (pendingLobbyGifBounds == null
                || pendingLobbyGifBounds.width() != pixelWidth
                || pendingLobbyGifBounds.height() != pixelHeight) {
            pendingLobbyGifBounds = new StreamingGifTextureAnimation.FrameSize(
                    pixelWidth, pixelHeight);
            lobbyGifResizeAtNanos = now + 150_000_000L;
        } else if (now - lobbyGifResizeAtNanos >= 0L) {
            lobbyGifBounds = pendingLobbyGifBounds;
            pendingLobbyGifBounds = null;
        }
    }

    private void drawLobbyImageMessage(LobbyChatMessage message, float x,
            float y, float width, float height, Color color) {
        if (y >= lobbyChatViewport.y + lobbyChatViewport.height
                || y + height <= lobbyChatViewport.y) return;
        LobbyMedia media = lobbyMedia.get(message.sequence());
        Texture thumbnail = media == null ? null : media.frameAt(
                lobbyGifBounds, Gdx.graphics.getFrameId(), frameDelta);
        if (thumbnail != null) {
            // Keep the on-screen aspect ratio stable as frame sizes round
            // to different whole pixels on different displays.
            var original = media.gifOriginalSize;
            int imageWidth = original == null ? thumbnail.getWidth()
                    : original.width();
            int imageHeight = original == null ? thumbnail.getHeight()
                    : original.height();
            float scale = Math.min(width / imageWidth, height / imageHeight);
            float drawnWidth = imageWidth * scale;
            float drawnHeight = imageHeight * scale;
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

    private float lobbyHistoryLineWidth(String value) {
        return lobbyHistoryLineWidth(lobbyHistoryRuns(value));
    }

    private void drawLobbyEmojiText(List<LobbyTextRun> runs, float x, float y) {
        float cursor = x;
        for (LobbyTextRun run : runs) {
            if (run.emojiId() > 0) {
                uiImages.add(new UiImageItem(emojiAtlas.emoji(run.emojiId()),
                        cursor, y - 21f, LOBBY_CHAT_EMOJI_SIZE,
                        LOBBY_CHAT_EMOJI_SIZE));
            } else {
                text(tinyFont, run.text(), cursor, y, Color.WHITE, false);
            }
            cursor += run.width();
        }
    }

    private void drawLobbyComposer(Rectangle bounds) {
        float x = bounds.x;
        float y = bounds.y;
        float w = bounds.width;
        float h = bounds.height;
        float centerY = y + h / 2f;
        boolean focused = "lobbyChat".equals(activeField);
        flatOuterBox(x, y, w, h,
                focused || hovered(x, y, w, h) ? CYAN : LINE,
                pressed(x, y, w, h) ? new Color(0x0b1424ff) : PANEL_LIGHT);
        if (lobbyChatDraft.isEmpty()) {
            textFit(lobbyComposerFont, gameText.translate("gdx.lobby.message_placeholder"),
                    x + 22f, centerY + lobbyComposerFont.getCapHeight() / 2f,
                    DISABLED, false, w - 44f);
            drawInputCaret(x + 22f, centerY - 18f, 36f, focused);
        } else {
            drawLobbyComposerValue(lobbyChatDraft, x + 22f, centerY, w - 44f,
                    focused);
        }
        textFieldHits.add(new TextFieldHit("lobbyChat", bounds));
        hit(x, y, w, h, () -> activateField("lobbyChat"));
    }

    /**
     * Keeps the legacy #id# wire format while presenting inline images just as
     * Swing's EmojiChatBox does. The left edge scrolls away when necessary so
     * the insertion caret and the most recent text stay visible.
     */
    private void drawLobbyComposerValue(String raw, float x, float centerY,
            float maxWidth, boolean focused) {
        if (focused) textEdit.focus("lobbyChat", raw);
        ComposerWindow window = lobbyComposerWindow(raw, maxWidth, focused);
        List<LobbyTextRun> runs = lobbyComposerRuns(window.raw());
        if (window.selectionWidth() > 0f) {
            shapes.setColor(new Color(CYAN.r, CYAN.g, CYAN.b, 0.34f));
            shapes.rect(x + window.selectionOffset(), centerY - 18f,
                    window.selectionWidth(), 36f);
        }
        float cursor = x;
        for (LobbyTextRun run : runs) {
            if (run.emojiId() > 0) {
                uiImages.add(new UiImageItem(emojiAtlas.emoji(run.emojiId()),
                        cursor, centerY - COMPOSER_EMOJI_SIZE / 2f,
                        COMPOSER_EMOJI_SIZE,
                        COMPOSER_EMOJI_SIZE));
            } else if (!run.text().isEmpty()) {
                text(lobbyComposerFont, run.text(), cursor,
                        centerY + lobbyComposerFont.getCapHeight() / 2f,
                        Color.WHITE, false);
            }
            cursor += run.width();
        }
        drawInputCaret(x + window.caretOffset(), centerY - 18f, 36f, focused);
    }

    private ComposerWindow lobbyComposerWindow(String raw, float maxWidth,
            boolean focused) {
        int caret = focused ? textEdit.caret(raw) : raw.length();
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
                Math.min(end, focused ? textEdit.selectionStart(raw) : caret));
        int selectionEnd = Math.max(start,
                Math.min(end, focused ? textEdit.selectionEnd(raw) : caret));
        return new ComposerWindow(raw.substring(start, end), start, end,
                composerWidth(raw.substring(start, caret)),
                composerWidth(raw.substring(start, selectionStart)),
                composerWidth(raw.substring(selectionStart, selectionEnd)));
    }

    private float composerWidth(String raw) {
        return lobbyTextRunWidth(lobbyComposerRuns(raw));
    }

    private static float lobbyTextRunWidth(List<LobbyTextRun> runs) {
        float result = 0f;
        for (LobbyTextRun run : runs) result += run.width();
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

    private List<LobbyTextRun> lobbyComposerRuns(String raw) {
        return lobbyTextRuns(raw, lobbyComposerFont, COMPOSER_EMOJI_ADVANCE);
    }

    private List<LobbyTextRun> lobbyTextRuns(String raw, BitmapFont font,
            float emojiAdvance) {
        List<LobbyTextRun> runs = new ArrayList<>();
        Matcher matcher = EMOJI_TOKEN.matcher(raw);
        int start = 0;
        while (matcher.find()) {
            addLobbyTextRun(runs, raw.substring(start, matcher.start()), font);
            int emojiId = Integer.parseInt(matcher.group(1));
            if (emojiId >= 1 && emojiId <= EMOJI_COUNT) {
                runs.add(new LobbyTextRun("", emojiId, emojiAdvance));
            } else {
                addLobbyTextRun(runs, matcher.group(), font);
            }
            start = matcher.end();
        }
        addLobbyTextRun(runs, raw.substring(start), font);
        return runs;
    }

    private void addLobbyTextRun(List<LobbyTextRun> runs, String value,
            BitmapFont font) {
        if (!value.isEmpty()) {
            runs.add(new LobbyTextRun(value, 0, textWidth(font, value)));
        }
    }

    private void drawLobbyGameInfo(LobbySnapshot state, float y) {
        float x = LOBBY_LEFT_CONTENT_X;
        float width = LOBBY_LEFT_CONTENT_WIDTH;
        float top = y + LOBBY_GAME_INFO_HEIGHT;
        float headerBottom = top - LOBBY_GAME_INFO_HEADER_HEIGHT;
        float headerCenter = top - LOBBY_GAME_INFO_HEADER_HEIGHT / 2f;
        float contentX = x + 16f;
        float contentRight = x + width - 16f;
        float contentWidth = contentRight - contentX;
        outerBox(x, y, width, LOBBY_GAME_INFO_HEIGHT, new Color(0x31445fcc),
                new Color(0x081525a8));
        textFit(smallFont, uppercase(gameText.translate(
                "settings.tab_current_game")), contentX,
                headerCenter + smallFont.getCapHeight() / 2f,
                GOLD, false, contentWidth - 44f);
        shapes.setColor(new Color(0x31445f90));
        shapes.rect(contentX, headerBottom, contentWidth, 1f);
        if (state.host()) {
            float buttonSize = 32f;
            float buttonX = contentRight - buttonSize;
            float buttonY = headerCenter - buttonSize / 2f;
            boolean enabled = !lobbyCommandPending && !state.startingOrStarted();
            themedButton(buttonX, buttonY, buttonSize, buttonSize, "",
                    ButtonTone.NEUTRAL,
                    () -> openSettings(GdxSettingsContract.Section.GAME),
                    enabled, smallFont);
            boolean hover = enabled && hovered(
                    buttonX, buttonY, buttonSize, buttonSize);
            GdxUiIconRenderer.draw(shapes, GdxUiIconRenderer.Icon.SETTINGS,
                    buttonX + buttonSize / 2f, buttonY + buttonSize / 2f,
                    26f, enabled ? (hover ? CYAN : GOLD) : DISABLED, 1f);
            tooltip(buttonX, buttonY, buttonSize, buttonSize,
                    "gdx.lobby.edit_game_settings");
        }
        NewGameTableDraft.Settings settings = state.tableSettings();
        if (settings == null) {
            textFit(smallFont, gameText.translate(
                    "status.recibiendo_info_servidor"),
                    contentX, headerBottom - LOBBY_GAME_INFO_BODY_PADDING,
                    MUTED, false, contentWidth);
            return;
        }
        List<GdxLobbyGameSummary.Row> rows = GdxLobbyGameSummary.rows(
                settings, gameText);
        float rowHeight = lobbyInfoRowHeight(rows.size());
        String[] rowContents = rows.stream()
                .map(row -> row.label() + "    " + row.value())
                .toArray(String[]::new);
        BitmapFont rowFont = GdxCrispButtonText.resolveAllWithinBox(glyph,
                rowContents, contentWidth, rowHeight - 4f,
                nativeTextTiers(smallFont));
        float rowsTop = headerBottom - LOBBY_GAME_INFO_BODY_PADDING;
        for (int index = 0; index < rows.size(); index++) {
            GdxLobbyGameSummary.Row row = rows.get(index);
            float rowBottom = rowsTop - (index + 1) * rowHeight;
            float baseline = rowBottom + (rowHeight + rowFont.getCapHeight()) / 2f;
            text(rowFont, row.label(), contentX, baseline, MUTED, false);
            text(rowFont, row.value(), contentRight - textWidth(rowFont, row.value()),
                    baseline, Color.WHITE, false);
            if (index < rows.size() - 1) {
                shapes.setColor(new Color(0x31445f77));
                shapes.rect(contentX, rowBottom - 0.5f, contentWidth, 1f);
            }
        }
    }

    static float lobbyInfoRowHeight(int rowCount) {
        return (LOBBY_GAME_INFO_HEIGHT - LOBBY_GAME_INFO_HEADER_HEIGHT
                - 2f * LOBBY_GAME_INFO_BODY_PADDING) / rowCount;
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
            case "UPNP_UNAVAILABLE" ->
                text.translate("gdx.lobby.upnp_unavailable");
            case "UPNP_ALREADY_MAPPED" ->
                text.translate("gdx.lobby.upnp_already_mapped");
            case "UPNP_FAILED" -> text.translate("gdx.lobby.upnp_failed");
            case "UPNP_ERROR" -> text.translate("gdx.lobby.upnp_error");
            case "ANTI_DOS_CONNECTION_BLOCKED",
                    "ANTI_DOS_CONNECTION_BLOCKED_SESSION" ->
                text.translate("gdx.lobby.anti_dos_connection_blocked");
            default -> statusDetail;
        };
    }

    static Rectangle lobbyConnectionCopyBounds() {
        float size = 36f;
        return new Rectangle(LOBBY_LEFT_CONTENT_X + LOBBY_LEFT_CONTENT_WIDTH
                - LOBBY_CONNECTION_RIGHT_PADDING - size,
                LOBBY_CONNECTION_Y + LOBBY_CONNECTION_HEIGHT - 8f - size,
                size, size);
    }

    private void copyLobbyConnectionData() {
        LobbySnapshot state = lobby;
        String value = lobbyConnectionClipboardText(
                state != null && state.host(),
                state == null ? "" : displayedLobbyEndpoint(state),
                lobbyPublicAddress);
        if (value.isEmpty()) return;
        Gdx.app.getClipboard().setContents(value);
        showToast(gameText.translate("conn.datos_de_conexion_copiados_en"));
    }

    String displayedLobbyEndpoint(LobbySnapshot state) {
        return GdxLocalServerAddresses.displayEndpoint(
                state.host(), state.serverAddress(), lobbyLocalAddress);
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

    static String lobbyPublicAddressEndpoint(String localEndpoint,
            String publicAddress) {
        String publicIp = Objects.requireNonNullElse(publicAddress, "").trim();
        String port = endpointPort(Objects.requireNonNullElse(
                localEndpoint, "").trim());
        if (publicIp.isEmpty() || port.isEmpty()) return "";
        if (publicIp.indexOf(':') >= 0
                && !(publicIp.startsWith("[") && publicIp.endsWith("]"))) {
            publicIp = "[" + publicIp + "]";
        }
        return publicIp + ":" + port;
    }

    static LobbyConnectionRowLayout lobbyConnectionRowLayout(float panelX,
            float panelWidth, boolean statusVisible) {
        float valueX = panelX + LOBBY_CONNECTION_VALUE_OFFSET;
        float fullValueWidth = panelX + panelWidth
                - LOBBY_CONNECTION_RIGHT_PADDING - valueX;
        if (!statusVisible) {
            return new LobbyConnectionRowLayout(valueX, fullValueWidth,
                    0f, 0f, fullValueWidth, false);
        }
        float statusX = panelX + panelWidth
                - LOBBY_CONNECTION_RIGHT_PADDING
                - LOBBY_CONNECTION_STATUS_WIDTH;
        float valueWidth = Math.max(1f, statusX
                - LOBBY_CONNECTION_STATUS_GAP - valueX);
        return new LobbyConnectionRowLayout(valueX, valueWidth, statusX,
                LOBBY_CONNECTION_STATUS_WIDTH, fullValueWidth, true);
    }

    record LobbyConnectionRowLayout(float valueX, float valueWidth,
            float statusX, float statusWidth, float fullValueWidth,
            boolean statusVisible) {
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
        String prompt = switch (lobbyConfirmation) {
            case START -> uppercase(gameText.translate(
                    "ui.seguro_que_quieres_empezar_ya"));
            case LEAVE -> uppercase(gameText.translate(
                    "ui.seguro_que_quieres_salir_ahora"));
            case KICK -> gameText.translate("ui.seguro_que_quieres_expulsar_a",
                    lobbyKickTarget.nickname());
        };
        GdxUiDialogStyle.ConfirmationLayout layout = drawConfirmationChrome(
                uppercase(gameText.translate("ui.seguro")), prompt);
        Rectangle cancel = layout.negativeButton();
        Rectangle accept = layout.positiveButton();
        themedButton(cancel.x, cancel.y, cancel.width, cancel.height,
                uppercase(gameText.translate("ui.cancelar")),
                ButtonTone.NEUTRAL,
                this::closeLobbyConfirmation, true);
        if (lobbyConfirmation == LobbyConfirmation.LEAVE) {
            exitButton(accept.x, accept.y, accept.width, accept.height,
                    uppercase(gameText.translate("ui.salir")),
                    this::confirmLobbyAction, true);
        } else if (lobbyConfirmation == LobbyConfirmation.KICK) {
            themedButton(accept.x, accept.y, accept.width, accept.height,
                    uppercase(gameText.translate("ui.expulsar_jugador")),
                    ButtonTone.DANGER, this::confirmLobbyAction, true);
        } else {
            themedButton(accept.x, accept.y, accept.width, accept.height,
                    uppercase(gameText.translate("ui.a_jugar")),
                    ButtonTone.POSITIVE, this::confirmLobbyAction, true);
        }
    }

    void showIdentityChangeNotice(String nickname) {
        if (nickname == null || nickname.isBlank()) return;
        identityChangeNotices.add(gameText.translate(
                "gdx.identity_change.message", nickname.trim()));
        if (surface == Surface.LOBBY) captureFrontendModalInput();
    }

    private void drawIdentityChangeNotice() {
        hits.clear();
        GdxUiDialogStyle.NoticeLayout layout = drawNoticeChrome(
                uppercase(gameText.translate("gdx.identity_change.title")),
                identityChangeNotices.element(), false);
        Rectangle button = layout.button();
        themedButton(button.x, button.y, button.width, button.height,
                uppercase(gameText.translate("ui.cerrar")),
                ButtonTone.NEUTRAL, this::closeIdentityChangeNotice, true);
    }

    private void closeIdentityChangeNotice() {
        identityChangeNotices.poll();
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
        GdxUiDialogStyle.drawPanel(shapes, 560f, 335f, 800f, 390f,
                CYAN_DARK, 1f);
        textFit(headingFont, uppercase(gameText.translate(
                lobbyPasswordActionKey(connection == null
                        ? "" : connection.password()))), 960f, 650f, GOLD,
                true, 700f);
        field(660f, 490f, 600f,
                gameText.translate("gdx.lobby.password_input"),
                lobbyPasswordDraft, "lobbyPassword", true);
        textFit(tinyFont, gameText.translate("gdx.lobby.password_hint"),
                960f, LOBBY_PASSWORD_DIALOG_HINT_Y, MUTED, true, 650f);
        themedButton(590f, 370f, 165f, 64f,
                gameText.translate("gdx.lobby.generate_password"),
                ButtonTone.NEUTRAL, this::generateLobbyPassword,
                !lobbyCommandPending);
        themedButton(770f, 370f, 165f, 64f,
                gameText.translate("ui.copiar"), ButtonTone.NEUTRAL,
                this::copyLobbyPasswordDraft,
                !lobbyCommandPending && !lobbyPasswordDraft.isBlank());
        themedButton(950f, 370f, 165f, 64f,
                uppercase(gameText.translate("ui.cancelar")),
                ButtonTone.NEUTRAL,
                this::closeLobbyPasswordDialog, !lobbyCommandPending);
        themedButton(1130f, 370f, 165f, 64f,
                gameText.translate("ui.guardar"), ButtonTone.POSITIVE,
                this::submitLobbyPassword, !lobbyCommandPending);
    }

    private List<NetworkBlock> activeNetworkBlocks() {
        return networkBlockCache == null || lobby == null || !lobby.host()
                ? List.of() : networkBlockCache.current();
    }

    private void openNetworkBlocks() {
        if (activeNetworkBlocks().isEmpty()) return;
        networkBlocksScroll = 0f;
        networkBlockUnblockConfirmation = null;
        networkBlockPendingAddress = null;
        networkBlockError = "";
        networkBlocksOpen = true;
        captureFrontendModalInput();
    }

    private void closeNetworkBlocks() {
        if (networkBlockPendingAddress != null) return;
        if (networkBlockUnblockConfirmation != null) {
            networkBlockUnblockConfirmation = null;
            return;
        }
        if (scrollDrag == ScrollDrag.NETWORK_BLOCKS) clearScrollDrag();
        networkBlocksOpen = false;
        networkBlocksScroll = 0f;
        networkBlockError = "";
    }

    private void requestNetworkAddressUnblock(NetworkBlock block) {
        if (block == null || networkBlockPendingAddress != null) return;
        networkBlockUnblockConfirmation = block;
    }

    private void confirmNetworkAddressUnblock() {
        NetworkBlock block = networkBlockUnblockConfirmation;
        networkBlockUnblockConfirmation = null;
        if (block != null) unblockNetworkAddress(block.address());
    }

    private void drawNetworkBlockUnblockConfirmation() {
        NetworkBlock block = networkBlockUnblockConfirmation;
        if (block == null) return;
        GdxUiDialogStyle.ConfirmationLayout layout = drawConfirmationChrome(
                uppercase(gameText.translate(
                        "gdx.network_blocks.confirm_title")),
                gameText.translate("gdx.network_blocks.confirm_remove",
                        block.address(),
                        GdxNetworkBlockDialogLayout.identity(block,
                                gameText)));
        themedButton(layout.negativeButton().x, layout.negativeButton().y,
                layout.negativeButton().width,
                layout.negativeButton().height,
                uppercase(gameText.translate("ui.cancelar")),
                ButtonTone.NEUTRAL,
                () -> networkBlockUnblockConfirmation = null, true);
        themedButton(layout.positiveButton().x, layout.positiveButton().y,
                layout.positiveButton().width,
                layout.positiveButton().height,
                uppercase(gameText.translate(
                        "gdx.network_blocks.remove")),
                ButtonTone.DANGER, this::confirmNetworkAddressUnblock, true);
    }

    private void unblockNetworkAddress(String address) {
        LobbySession session = lobbySession;
        if (session == null || networkBlockPendingAddress != null) return;
        networkBlockPendingAddress = address;
        networkBlockError = "";
        session.unblockAddress(address).whenComplete((ignored, failure) ->
                Gdx.app.postRunnable(() -> {
                    if (lobbySession != session) return;
                    networkBlockPendingAddress = null;
                    if (failure != null) {
                        networkBlockError = gameText.translate(
                                "gdx.network_blocks.remove_failed");
                    } else {
                        if (networkBlockCache != null) {
                            networkBlockCache.removeLocally(address);
                            networkBlockCache.refreshNow(lobby != null
                                    && lobby.host());
                        }
                        if (activeNetworkBlocks().isEmpty()) {
                            networkBlocksOpen = false;
                            networkBlocksScroll = 0f;
                        }
                    }
                }));
    }

    private void drawNetworkBlocksDialog() {
        List<NetworkBlock> blocks = activeNetworkBlocks();
        if (blocks.isEmpty() && networkBlockPendingAddress == null) {
            closeNetworkBlocks();
            return;
        }
        GdxNetworkBlockDialogLayout.Layout layout
                = GdxNetworkBlockDialogLayout.layout(WIDTH, HEIGHT);
        Rectangle panel = layout.panel();
        Rectangle rows = layout.rows();
        networkBlocksScroll = GdxNetworkBlockDialogLayout.clampScroll(
                networkBlocksScroll, blocks.size(), rows);
        long now = System.currentTimeMillis();

        GdxUiDialogStyle.drawPanel(shapes, panel.x, panel.y,
                panel.width, panel.height, CYAN_DARK, 1f);
        shapes.setColor(new Color(0x321217ff));
        roundedRect(panel.x + 42f, panel.y + panel.height - 132f,
                76f, 76f, 16f);
        drawProhibitionIcon(panel.x + 80f,
                panel.y + panel.height - 94f, 25f, LATENCY_RED);

        for (int index = 0; index < blocks.size(); index++) {
            Rectangle row = GdxNetworkBlockDialogLayout.row(rows, index,
                    networkBlocksScroll);
            if (row.y < rows.y || row.y + row.height > rows.y + rows.height) {
                continue;
            }
            NetworkBlock block = blocks.get(index);
            outerBox(row.x, row.y, row.width, row.height,
                    new Color(0x31445fcc), new Color(0x081525f2));
            themedButton(row.x + row.width - 84f, row.y + 10f,
                    64f, row.height - 20f,
                    GdxUiDialogStyle.CLOSE_GLYPH,
                    ButtonTone.DANGER,
                    () -> requestNetworkAddressUnblock(block),
                    networkBlockPendingAddress == null);
            textFit(uiFont, block.address(), row.x + 22f,
                    row.y + 49f, Color.WHITE, false, 290f);
            textFit(tinyFont, GdxNetworkBlockDialogLayout.identity(
                    block, gameText), row.x + 330f, row.y + 47f,
                    ORANGE, false, 255f);
            textFit(tinyFont, uppercase(
                    GdxNetworkBlockDialogLayout.duration(block, now,
                            gameText)),
                    row.x + 600f, row.y + 47f, GOLD, false,
                    row.width - 704f);
        }
        drawNetworkBlocksScrollbar(layout, blocks.size());
        themedButton(layout.closeButton().x, layout.closeButton().y,
                layout.closeButton().width, layout.closeButton().height,
                GdxUiDialogStyle.CLOSE_GLYPH,
                ButtonTone.DANGER, this::closeNetworkBlocks,
                networkBlockPendingAddress == null,
                settingsStepperSignFont);

        textFit(headingFont, uppercase(gameText.translate(
                "gdx.network_blocks.title")), panel.x + 138f,
                panel.y + panel.height - 76f, GOLD, false,
                panel.width - 190f);
        textFit(tinyFont, gameText.translate("gdx.network_blocks.help"),
                panel.x + 48f, panel.y + panel.height - 158f,
                MUTED, false, panel.width - 96f);
        if (!networkBlockError.isBlank()) {
            textFit(tinyFont, networkBlockError, panel.x + 48f,
                    panel.y + 78f, LATENCY_RED, false,
                    panel.width - 340f);
        }
    }

    private void drawNetworkBlocksScrollbar(
            GdxNetworkBlockDialogLayout.Layout layout, int count) {
        float maximum = GdxNetworkBlockDialogLayout.maximumScroll(count,
                layout.rows());
        if (maximum <= 0f) return;
        Rectangle track = layout.scrollbar();
        float thumb = networkBlocksThumbHeight(layout, count);
        float progress = networkBlocksScroll / maximum;
        float thumbY = track.y + track.height - thumb
                - progress * (track.height - thumb);
        shapes.setColor(new Color(0x22354dff));
        roundedRect(track.x, track.y, track.width, track.height, 5f);
        shapes.setColor(CYAN);
        roundedRect(track.x, thumbY, track.width, thumb, 5f);
    }

    private void drawProhibitionIcon(float cx, float cy, float radius,
            Color color) {
        shapes.setColor(color);
        shapes.circle(cx, cy, radius, 48);
        shapes.setColor(new Color(0x321217ff));
        shapes.circle(cx, cy, radius - 7f, 48);
        shapes.setColor(color);
        shapes.rectLine(cx - radius * 0.64f, cy + radius * 0.64f,
                cx + radius * 0.64f, cy - radius * 0.64f, 7f);
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
        GdxUiDialogStyle.ConfirmationLayout layout = drawConfirmationChrome(
                uppercase(gameText.translate("gdx.settings.unsaved.title")),
                uppercase(gameText.translate(
                        "gdx.settings.unsaved.message")));
        Rectangle cancel = layout.negativeButton();
        Rectangle accept = layout.positiveButton();
        themedButton(cancel.x, cancel.y, cancel.width, cancel.height,
                uppercase(gameText.translate(
                "gdx.settings.unsaved.continue")),
                ButtonTone.NEUTRAL,
                () -> settingsDiscardConfirmation = false, true);
        themedButton(accept.x, accept.y, accept.width, accept.height,
                uppercase(gameText.translate(
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
        if (action == null) return;
        LobbyParticipant target = lobbyKickTarget;
        closeLobbyConfirmation();
        switch (action) {
            case START -> {
                lobbyGameStarting = true;
                submitLobbyCommand(new LobbyCommand.StartGame(), null);
            }
            case LEAVE -> submitLobbyCommand(new LobbyCommand.Leave(),
                    this::returnFromLobby);
            case KICK -> kickLobbyParticipant(target);
        }
    }

    private void closeLobbyConfirmation() {
        lobbyConfirmation = null;
        lobbyKickTarget = null;
        pressedHit = null;
    }

    private void drawLobbyLoadingOverlay() {
        hits.clear();
        var layout = GdxUiDialogStyle.preparationLayout(WIDTH, HEIGHT);
        GdxUiDialogStyle.drawPreparationDialog(shapes, layout, elapsed);
        textFit(headingFont, uppercase(gameText.translate(
                "gdx.lobby.preparing_table")),
                layout.centerX(), layout.titleY(), GdxUiDialogStyle.PREPARATION_TITLE,
                true, layout.labelWidth());
        textFit(smallFont, gameText.translate("game.inicializando_timba"),
                layout.centerX(), layout.statusY(), GdxUiDialogStyle.PREPARATION_STATUS,
                true, layout.labelWidth());
    }

    private void drawNewGameSubmissionOverlay() {

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
        drawIndeterminateProgressBar(trackX, trackY, trackW, trackH,
                150f, 0.58f, 8f);

        themedButton(panelX + 200f, panelY + 28f, 300f, 62f,
                uppercase(gameText.translate("ui.cancelar")),
                ButtonTone.NEUTRAL, this::cancelOrReturnToMenu, true);
    }

    /**
     * Shared real-work indicator. It deliberately reports no percentage when
     * the underlying operation cannot provide reliable progress.
     */
    private void drawIndeterminateProgressBar(float trackX, float trackY,
            float trackW, float trackH, float segmentW, float speed,
            float radius) {
        shapes.setColor(new Color(0x020813ff));
        roundedRect(trackX, trackY, trackW, trackH, radius);
        shapes.setColor(LINE);
        roundedRect(trackX + 2f, trackY + 2f, trackW - 4f,
                trackH - 4f, radius - 2f);
        float travel = trackW - segmentW - 8f;
        float phase = (elapsed * speed) % 2f;
        float eased = phase <= 1f ? phase : 2f - phase;
        shapes.setColor(CYAN);
        roundedRect(trackX + 4f + travel * eased, trackY + 4f,
                segmentW, trackH - 8f, radius - 4f);
    }

    static boolean lobbyShowsKickButton(LobbySnapshot state,
            LobbyParticipant participant) {
        return state.host() && !participant.local() && !participant.host();
    }

    static boolean lobbyHasKickTarget(LobbySnapshot state,
            LobbyParticipant target) {
        return target != null && lobbyShowsKickButton(state, target)
                && !state.startingOrStarted()
                && state.participants().stream().anyMatch(participant ->
                        participant.nickname().equals(target.nickname())
                        && participant.bot() == target.bot()
                        && Arrays.equals(participant.identityPublicKey(),
                                target.identityPublicKey())
                        && Arrays.equals(participant.sessionFingerprint(),
                                target.sessionFingerprint()));
    }

    private boolean canKickLobbyParticipant(LobbyParticipant target) {
        return lobbySession != null && !lobbyCommandPending
                && lobbyHasKickTarget(lobbySession.snapshot(), target);
    }

    private void requestLobbyKick(LobbyParticipant target) {
        if (!canKickLobbyParticipant(target)) return;
        if (target.bot()) {
            kickLobbyParticipant(target);
        } else {
            lobbyKickTarget = target;
            lobbyConfirmation = LobbyConfirmation.KICK;
            captureFrontendModalInput();
        }
    }

    private void kickLobbyParticipant(LobbyParticipant target) {
        if (canKickLobbyParticipant(target)) {
            submitLobbyCommand(new LobbyCommand.Kick(target.nickname()), null);
        }
    }

    private void sendLobbyText() {
        String submittedDraft = lobbyChatDraft;
        String message = submittedDraft.trim();
        submitLobbyTextMessage(message, () -> {
            lobbyChatHistory.remember(message);
            if (lobbyChatDraft.equals(submittedDraft)) lobbyChatDraft = "";
        });
    }

    private void shareLobbyVideo(PokerVideoSearch.Video video) {
        if (surface == Surface.LOBBY) {
            submitLobbyTextMessage(PokerVideoChatLink.format(video), null);
        }
    }

    private void submitLobbyTextMessage(String message, Runnable success) {
        if (lobbySession == null || lobbyCommandPending) return;
        if (lobbyTextSendReady(elapsed, lobbyTextSendAllowedAt, message)) {
            lobbyTextSendAllowedAt = elapsed + TEXT_SEND_COOLDOWN_SECONDS;
            submitLobbyCommand(new LobbyCommand.SendText(message), success);
        }
    }

    private void recallLobbyChat(int direction) {
        lobbyChatDraft = lobbyChatHistory.recall(lobbyChatDraft, direction);
        activateField("lobbyChat");
        textEdit.end(lobbyChatDraft, false);
    }

    static boolean lobbyTextSendReady(float now, float allowedAt,
            String message) {
        return now >= allowedAt
                && !Objects.requireNonNullElse(message, "").trim().isEmpty();
    }

    private boolean sendLobbyImage(String url, boolean fromFavorites) {
        if (lobbySession == null || lobbyCommandPending) return false;
        if (elapsed < lobbyImageSendAllowedAt) {
            showToast(gameText.translate("gdx.lobby.image_cooldown"));
            return false;
        }
        submitLobbyCommand(new LobbyCommand.SendImage(url), () -> {
            lobbyImageSendAllowedAt = elapsed + IMAGE_SEND_COOLDOWN_SECONDS;
            if (fromFavorites) {
                GdxChatImageHistory.remember(initialProperties, url, true);
                saveLobbyImagePreferences();
            }
            lobbyGallery.close();
        });
        return true;
    }

    private void saveLobbyImagePreferences() {
        if (preferences != null) preferences.saveDeferred();
    }

    private void toggleLobbyImageGallery() {
        if (lobbyGallery.isOpen()) {
            lobbyGallery.close();
        } else if (!lobbyGallery.open(viewport, gameText, initialProperties,
                this::saveLobbyImagePreferences, this::sendLobbyImage)) {
            showToast(gameText.translate("gdx.lobby.image_search_unavailable"));
        } else {
            lobbyEmojiPickerOpen = false;
            activateField("lobbyChat");
        }
    }

    private boolean lobbyGalleryInteractive() {
        return surface == Surface.LOBBY && lobbyGallery.isOpen() && !hasBlockingFrontendModal();
    }

    private void drawLobbyEmojiPicker(float x, float y, float w, float h) {
        GdxUiDialogStyle.drawPanel(shapes, x, y, w, h, CYAN_DARK, 1f);
        int first = lobbyEmojiPage * EMOJI_PAGE_SIZE + 1;
        int last = Math.min(EMOJI_COUNT, first + EMOJI_PAGE_SIZE - 1);
        int pageCount = (EMOJI_COUNT + EMOJI_PAGE_SIZE - 1) / EMOJI_PAGE_SIZE;
        textFit(smallFont, "EMOJIS " + first + " - " + last,
                x + 28f, y + h - 30f, GOLD, false, 350f);
        textFitRight(tinyFont, uppercase(gameText.translate("gdx.lobby.page",
                lobbyEmojiPage + 1, pageCount)),
                x + w - 28f, y + h - 30f, MUTED, 300f);
        float cellW = 62f;
        float cellH = 43f;
        float gridWidth = (EMOJI_COLUMNS - 1) * cellW
                + LOBBY_EMOJI_PICKER_CELL_SIZE;
        float startX = x + (w - gridWidth) / 2f;
        float startY = y + h - 100f;
        for (int slot = 0; slot < EMOJI_PAGE_SIZE; slot++) {
            int number = first + slot;
            if (number > EMOJI_COUNT) break;
            int column = slot % EMOJI_COLUMNS;
            int row = slot / EMOJI_COLUMNS;
            float cellX = startX + column * cellW;
            float cellY = startY - row * cellH;
            if (hovered(cellX, cellY, LOBBY_EMOJI_PICKER_CELL_SIZE,
                    LOBBY_EMOJI_PICKER_CELL_SIZE)) {
                shapes.setColor(new Color(0x36d9ff28));
                roundedRect(cellX, cellY, LOBBY_EMOJI_PICKER_CELL_SIZE,
                        LOBBY_EMOJI_PICKER_CELL_SIZE, 9f);
            }
            float imageInset = (LOBBY_EMOJI_PICKER_CELL_SIZE
                    - LOBBY_EMOJI_PICKER_IMAGE_SIZE) / 2f;
            uiImages.add(UiImageItem.pickerEmoji(emojiAtlas.emoji(number),
                    cellX + imageInset, cellY + imageInset,
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

    static Rectangle lobbyComposerInputBounds() {
        return new Rectangle(LOBBY_COMPOSER_X, LOBBY_COMPOSER_Y,
                LOBBY_TEXT_INPUT_WIDTH,
                LOBBY_COMPOSER_HEIGHT);
    }

    static Rectangle lobbyComposerEmojiBounds() {
        Rectangle input = lobbyComposerInputBounds();
        return new Rectangle(input.x + input.width + LOBBY_COMPOSER_GAP,
                LOBBY_COMPOSER_Y,
                LOBBY_COMPOSER_ICON_WIDTH,
                LOBBY_COMPOSER_HEIGHT);
    }

    static Rectangle lobbyComposerImageBounds() {
        Rectangle emoji = lobbyComposerEmojiBounds();
        return new Rectangle(emoji.x + emoji.width + LOBBY_COMPOSER_GAP,
                LOBBY_COMPOSER_Y,
                LOBBY_COMPOSER_ICON_WIDTH,
                LOBBY_COMPOSER_HEIGHT);
    }

    static Rectangle lobbyVoiceButtonBounds() {
        Rectangle image = lobbyComposerImageBounds();
        return new Rectangle(image.x + image.width + LOBBY_COMPOSER_GAP,
                LOBBY_COMPOSER_Y,
                LOBBY_COMPOSER_ICON_WIDTH,
                LOBBY_COMPOSER_HEIGHT);
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
        lobbyVoicePointer.clear();
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

    private void drawLobbyVoiceStatus(float x, float y, float w, boolean compact) {
        boolean active = lobbyVoiceOpening || lobbyVoiceLive
                || lobbyVoiceStopping;
        float panelW = Math.min(720f, w);
        float panelH = compact ? 44f : 92f;
        float inset = compact ? 7f : 15f;
        float panelX = x + (w - panelW) / 2f;
        shapes.setColor(0.008f, 0.035f, 0.055f, 0.95f);
        roundedRect(panelX, y, panelW, panelH, 15f);
        shapes.setColor(active ? new Color(0xe53935ff) : LINE);
        roundedRect(panelX + 16f, y + inset, 7f, panelH - 2f * inset, 3f);
        if (lobbyVoiceLive) {
            shapes.setColor(0.08f, 0.16f, 0.24f, 0.96f);
            float timerHeight = compact ? 28f : 44f;
            roundedRect(panelX + panelW - 110f,
                    y + (panelH - timerHeight) / 2f, 88f, timerHeight, 10f);
            float remaining = Math.max(0f, VOICE_RECORD_MAX_SECONDS
                    - (elapsed - lobbyVoiceLiveAt));
            shapes.setColor(0.17f, 0.92f, 0.62f, 0.92f);
            roundedRect(panelX + 84f, y + (compact ? 4f : 12f),
                    (panelW - 216f) * remaining
                            / VOICE_RECORD_MAX_SECONDS,
                    compact ? 3f : 7f, 2f);
            textFit(smallFont, (int) Math.ceil(remaining) + " s",
                    panelX + panelW - 66f, y + panelH / 2f + 8f,
                    Color.WHITE, true, 80f);
        }
        textFit(smallFont, lobbyVoiceStatus,
                panelX + 38f, y + panelH / 2f + 10f,
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
                    GdxChatImageHistory.remember(
                            initialProperties, message.content(), false);
                    if (preferences != null) preferences.saveDeferred();
                    lobbyGallery.sync(initialProperties);
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
        GdxPreparedChatMedia.prepareAsync(message.content(),
                "lobby-chat:" + message.sequence())
                .whenComplete((prepared, failure) -> {
                    if (Gdx.app == null) {
                        if (prepared != null) prepared.dispose();
                        return;
                    }
                    Gdx.app.postRunnable(() -> {
                        if (disposed || lobbyMedia.get(message.sequence())
                                != media) {
                            if (prepared != null) prepared.dispose();
                            return;
                        }
                        if (failure != null || prepared == null) {
                            media.failed = true;
                            return;
                        }
                        try {
                            if (prepared.animated()) {
                                media.gifSource = prepared.takeGif();
                                media.gifOriginalSize = media.gifSource
                                        .timing().size();
                            } else {
                                media.image = prepared.uploadImage();
                            }
                        } catch (RuntimeException invalid) {
                            media.failed = true;
                        } finally {
                            prepared.dispose();
                        }
                    });
                });
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

    private void clearLobbyMedia() {
        for (LobbyMedia media : lobbyMedia.values()) {
            media.dispose();
        }
        lobbyMedia.clear();
        lobbyGifBounds = null;
        pendingLobbyGifBounds = null;
    }

    private void submitLobbyCommand(LobbyCommand command, Runnable success) {
        LobbySession session = lobbySession;
        if (session == null || lobbyCommandPending) {
            return;
        }
        lobbyCommandPending = true;
        try {
            session.submit(command).whenComplete((ignored, failure) ->
                    Gdx.app.postRunnable(() -> {
                        if (disposed || lobbySession != session) return;
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
        clearLobbyScreenState();
        clearActiveField();
        // Startup reveal is a one-shot lifecycle. It must never gate a menu
        // reached after an actual session.
        menuRevealStartedAt = Float.NaN;
        activateSurface(Surface.MENU);
        sessionReturnedToMenu.run();
    }

    /** Clears every transient whose ownership ends with the lobby screen. */
    private void clearLobbyScreenState() {
        lobbyChatHistory.clear();
        lobbyChatLayout = null;
        lobbyCommandPending = false;
        lobbyGameStarting = false;
        closeLobbyConfirmation();
        identityChangeNotices.clear();
        fingerprintDialog = null;
        lobbyPasswordDialog = false;
        lobbyPasswordDraft = "";
        networkBlocksOpen = false;
        networkBlocksScroll = 0f;
        networkBlockUnblockConfirmation = null;
        networkBlockPendingAddress = null;
        networkBlockError = "";
        if (networkBlockCache != null) networkBlockCache.clear();
        networkBlockCache = null;
        lobbyEmojiPickerOpen = false;
        lobbyGallery.close();
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
        openSettings(null);
    }

    private void openSettings(GdxSettingsContract.Section initialSection) {
        clearSettingsToggleAnimations(toggleAnimations);
        settingsToggleTransitions.clear();
        settingsReturnSurface = surface == Surface.LOBBY
                ? Surface.LOBBY : Surface.MENU;
        settingsSession.begin(settingsReturnSurface == Surface.LOBBY
                ? GdxSettingsSession.Context.WAITING_ROOM
                : GdxSettingsSession.Context.MENU, initialProperties);
        if (initialSection != null) {
            settingsSession.selectTab(settingsSession.sections()
                    .indexOf(initialSection));
        }
        settingsAppearancePage = settingsSession.subpageIndex(
                GdxSettingsContract.Section.APPEARANCE);
        settingsAudioPage = settingsSession.subpageIndex(
                GdxSettingsContract.Section.AUDIO);
        settingsGamePage = settingsSession.subpageIndex(
                GdxSettingsContract.Section.GAME);
        settingsAppearanceScroll = 0f;
        settingsAudioScroll = 0f;
        settingsGameScroll = 0f;
        settingsShortcutScroll = 0f;
        settingsDebugScroll = 0;
        settingsDebugLineCount = 0;
        settingsShortcutCaptureId = null;
        settingsAutoCallDialog = null;
        settingsAutoRebuySnapshot = presentationSettings.autoRebuyOnBroke();
        settingsStraddleTimeoutDraft = GdxSettingsContract.straddleOnTimeout(
                initialProperties);
        shortcutBindings.beginEdit();
        settingsOpenedWindowMode = GdxDisplayModeController.activeMode();
        settingsOpenedMsaaSamples = presentationSettings
                .requestedMsaaSamples();
        settingsTable = lobby != null && settingsReturnSurface == Surface.LOBBY
                && lobby.tableSettings() != null
                        ? NewGameTableDraft.from(lobby.tableSettings())
                        : defaultTableDraft();
        if (settingsTable != null && lobby != null && lobby.recovering()) {
            settingsTable.setEconomyLocked(true);
        }
        settingsTableSnapshot = settingsTable == null
                ? null : settingsTable.snapshot();
        settingsDiscardConfirmation = false;
        GdxAudioDevices.refreshCaptureDevicesAsync();
        clearActiveField();
        pressedHit = null;
        activateSurface(Surface.SETTINGS);
    }

    private void closeSettings(boolean save) {
        settingsShortcutCaptureId = null;
        if (save && settingsReturnSurface == Surface.LOBBY) {
            NewGameTableDraft.Settings requested = settingsTable == null
                    ? null : settingsTable.snapshot();
            if (shouldSubmitLobbyTableSettings(lobby, requested)) {
                submitLobbyCommand(new LobbyCommand.UpdateTableSettings(requested),
                        () -> finishClosingSettings(true));
                return;
            }
        }
        finishClosingSettings(save);
    }

    static boolean shouldSubmitLobbyTableSettings(LobbySnapshot state,
            NewGameTableDraft.Settings requested) {
        return state != null && state.host() && requested != null
                && !requested.equals(state.tableSettings());
    }

    private void finishClosingSettings(boolean save) {
        audioPreview.stop();
        boolean restartNotice = save
                && GdxSettingsContract.requiresMsaaRestart(
                        settingsOpenedMsaaSamples,
                        presentationSettings.requestedMsaaSamples(),
                        presentationSettings.actualMsaaSamples());
        if (save) {
            presentationSettings.commitCommunityDealStyle();
            if (settingsReturnSurface == Surface.MENU
                    && settingsTable != null) {
                initialProperties.setProperty(DEFAULT_TABLE_SETTINGS_KEY,
                        settingsTable.snapshot().serializeForWire());
                CustomBotProfileCatalog.writeEnabled(initialProperties,
                        settingsTable.customBots());
                CustomBotProfileCatalog.writeOnlyCustom(initialProperties,
                        settingsTable.customBotsOnly());
            }
            shortcutBindings.commitEdit();
            if (settingsStraddleTimeoutDraft
                    != GdxSettingsContract.straddleOnTimeout(initialProperties)) {
                initialProperties.setProperty(
                        GdxSettingsContract.STRADDLE_ON_TIMEOUT_KEY,
                        Boolean.toString(settingsStraddleTimeoutDraft));
            }
            if (preferences != null) preferences.saveDeferred();
        } else {
            shortcutBindings.cancelEdit();
            String previewOutput = initialProperties.getProperty(
                    GdxAudioDevices.OUTPUT_KEY, "");
            settingsSession.restore(initialProperties);
            GdxVisualEffects.synchronize(initialProperties);
            presentationSettings.refreshRuntimePreferences();
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
            presentationSettings.setAutoRebuyOnBroke(
                    settingsAutoRebuySnapshot);
            refreshFeltFromSettings();
        }
        settingsSession.close();
        settingsTable = null;
        settingsTableSnapshot = null;
        settingsDiscardConfirmation = false;
        settingsAutoCallDialog = null;
        activateSurface(settingsReturnSurface);
        syncMusicForSurface();
        settingsRestartNotice = restartNotice;
    }

    private void drawSettingsRestartNotice() {
        hits.clear();
        secondaryHits.clear();
        textFieldHits.clear();
        passwordRevealHits.clear();
        editMenuHits.clear();
        GdxUiDialogStyle.NoticeLayout layout = drawNoticeChrome(
                uppercase(gameText.translate(
                        "gdx.settings.msaa_restart.title")),
                gameText.translate("gdx.settings.msaa_restart.message"),
                false);
        Rectangle accept = layout.button();
        themedButton(accept.x, accept.y, accept.width, accept.height,
                uppercase(gameText.translate("ui.aceptar")),
                ButtonTone.FEATURED,
                () -> settingsRestartNotice = false, true);
    }

    private boolean settingsHavePendingChanges() {
        if (shortcutBindings.hasPendingEdits()
                || settingsSession.propertiesChanged(initialProperties)
                || settingsStraddleTimeoutDraft
                        != GdxSettingsContract.straddleOnTimeout(initialProperties)
                || presentationSettings.autoRebuyOnBroke()
                        != settingsAutoRebuySnapshot) {
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
        GdxSettingsContract.restoreAudioPageDefaults(initialProperties,
                settingsAudioPage,
                frontendMayEditGlobalCommunicationRules(),
                GdxAudioDevices.hasCaptureDevices());
        GdxAudioDevices.applyConfiguredOutput(initialProperties);
        audioControl.setEnabled(preferenceBoolean("sonidos", true), false);
        syncMusicForSurface();
        playFrontendSwitchSound(true);
    }

    private void restoreFrontendAppearanceDefaults() {
        GdxSettingsContract.restoreAppearancePageDefaults(initialProperties,
                settingsAppearancePage, true);
        GdxVisualEffects.synchronize(initialProperties);
        presentationSettings.refreshRuntimePreferences();
        GdxDisplayModeController.apply(GdxWindowMode.configured(
                initialProperties));
        refreshFeltFromSettings();
        playFrontendSwitchSound(true);
    }

    private boolean settingsSectionHasRestoreDefaults() {
        return GdxSettingsContract.hasRestoreDefaults(
                settingsSession.section());
    }

    private boolean currentSettingsSubsectionAtDefaults() {
        return switch (settingsSession.section()) {
            case APPEARANCE -> GdxSettingsContract.appearancePageAtDefaults(
                    initialProperties, settingsAppearancePage, true);
            case AUDIO -> GdxSettingsContract.audioPageAtDefaults(
                    initialProperties, settingsAudioPage,
                    frontendMayEditGlobalCommunicationRules(),
                    GdxAudioDevices.hasCaptureDevices(),
                    audioControl.enabled());
            case SHORTCUTS -> shortcutBindings.allDefaults()
                    && frontendControlSettingsAtDefaults();
            case GAME, DEBUG -> true;
        };
    }

    private void restoreCurrentFrontendSettingsSectionDefaults() {
        settingsRowScrollMaximum = 0;
        settingsRowScrollTrack.set(0f, 0f, 0f, 0f);
        switch (settingsSession.section()) {
            case APPEARANCE -> restoreFrontendAppearanceDefaults();
            case AUDIO -> restoreFrontendAudioDefaults();
            case SHORTCUTS -> {
                shortcutBindings.resetAllEdits();
                restoreFrontendControlDefaults();
                settingsShortcutCaptureId = null;
            }
            case GAME, DEBUG -> {
                return;
            }
        }
        showSettingsDefaultsRestoredToast();
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
            case GAME -> {
                settingsGamePage = index;
                settingsGameScroll = 0f;
            }
            case SHORTCUTS -> {
                settingsShortcutScroll = 0f;
                settingsShortcutCaptureId = null;
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
        boolean restoreEnabled = restoreVisible
                && !currentSettingsSubsectionAtDefaults();
        boolean saveEnabled = !lobbyCommandPending
                && settingsHavePendingChanges();
        GdxSettingsChrome.draw(shapes, frame, sections,
                subpages.size(), settingsSession.tabIndex(), activeSubpage,
                pointer, restoreVisible, restoreEnabled, saveEnabled, 1f);
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
            Rectangle label = GdxSettingsLayout.mainTabLabel(tab);
            boolean active = settingsSession.tabIndex() == i;
            String sectionLabel = sections.get(i)
                    == GdxSettingsContract.Section.GAME
                            ? uppercase(gameText.translate(
                                    settingsSession.context()
                                            == GdxSettingsSession.Context.MENU
                                                    ? "settings.tab_games"
                                                    : "settings.tab_current_game"))
                            : sections.get(i).label(gameText);
            crispButtonTextInBox(actionFont,
                    sectionLabel,
                    label.x, label.y, label.width, label.height,
                    active ? Color.WHITE : MUTED);
            hit(tab.x, tab.y, tab.width, tab.height, () -> {
                audioPreview.stop();
                settingsSession.selectTab(selected);
                settingsAppearanceScroll = 0f;
                settingsAudioScroll = 0f;
                settingsGameScroll = 0f;
                settingsShortcutScroll = 0f;
                settingsShortcutCaptureId = null;
            });
        }

        for (int i = 0; i < subpages.size(); i++) {
            final int selected = i;
            Rectangle tab = frame.subTab(i);
            boolean active = i == activeSubpage;
            crispButtonTextInBox(smallFont, subpages.get(i),
                    tab.x, tab.y, tab.width, tab.height,
                    active ? GOLD : MUTED);
            hit(tab.x, tab.y, tab.width, tab.height,
                    () -> selectSettingsSubpage(selected));
        }

        GdxSettingsLayout.contentClip(content, settingsRowsClip);
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
        crispButtonTextInBox(actionFont,
                uppercase(gameText.translate("ui.cancelar")),
                cancel.x, cancel.y,
                cancel.width, cancel.height, GOLD);
        if (!lobbyCommandPending) {
            hit(cancel.x, cancel.y, cancel.width, cancel.height,
                    this::requestCancelSettings);
        }
        if (restoreVisible) {
            Rectangle restore = frame.restoreButton();
            crispButtonTextInBox(actionFont,
                    uppercase(gameText.translate("gdx.settings.restore_defaults")),
                    restore.x, restore.y, restore.width, restore.height,
                    restoreEnabled ? GOLD : DISABLED);
            if (!lobbyCommandPending && restoreEnabled) {
                hit(restore.x, restore.y, restore.width, restore.height,
                        this::restoreCurrentFrontendSettingsSectionDefaults);
            }
        }
        if (settingsReturnSurface == Surface.MENU) {
            boolean backupEnabled = !saveEnabled && !settingsBackupPending;
            drawProfileBackupButton(frame, false, backupEnabled);
            drawProfileBackupButton(frame, true, backupEnabled);
        }
        Rectangle save = frame.saveButton();
        crispButtonTextInBox(actionFont,
                lobbyCommandPending
                        ? uppercase(gameText.translate("gdx.saving"))
                        : gameText.translate("ui.guardar"),
                save.x, save.y, save.width, save.height,
                saveEnabled ? Color.WHITE : DISABLED);
        if (saveEnabled) {
            hit(save.x, save.y, save.width, save.height,
                    () -> closeSettings(true));
        }
    }

    private void drawProfileBackupButton(GdxSettingsLayout.Frame frame,
            boolean importing, boolean enabled) {
        Rectangle bounds = frame.profileBackupButton(importing);
        GdxSettingsChrome.drawFooterButton(shapes, bounds,
                GdxUiButtonStyle.Tone.NEUTRAL, pointer, enabled, 1f);
        String key = importing ? "gdx.backup.import" : "gdx.backup.export";
        pixelSnappedTextInBox(actionFont, uppercase(gameText.translate(key)),
                bounds.x, bounds.y, bounds.width, bounds.height, enabled ? GOLD : DISABLED);
        if (enabled) {
            hit(bounds.x, bounds.y, bounds.width, bounds.height,
                    () -> chooseProfileBackup(importing));
        }
    }

    private void chooseProfileBackup(boolean importing) {
        if (settingsBackupPending || profileBackupSelection != null
                || settingsHavePendingChanges()) return;
        if (!importing) {
            Path archive = GdxProfileBackupTransfer.exportPath(GdxProfileBackup.profile(),
                    java.time.LocalDateTime.now());
            profileBackupSelection = new ProfileBackupSelection(false, archive,
                    EnumSet.allOf(GdxProfileBackup.Category.class));
            captureFrontendModalInput();
            return;
        }

        settingsBackupPending = true;
        captureFrontendModalInput();
        var application = Gdx.app;
        String title = gameText.translate("gdx.backup.import");
        String select = gameText.translate("ui.aceptar");
        GdxFileChooser.chooseFile(GdxProfileBackup.profile().getParent(),
                title, select, "ZIP", "zip").thenApplyAsync(archive -> {
            if (archive == null) return null;
            try {
                var categories = GdxProfileBackup.categories(archive);
                if (categories.isEmpty()) throw new IOException(gameText.translate("gdx.backup.empty"));
                return new ProfileBackupSelection(true, archive, categories);
            } catch (Exception failure) {
                throw new CompletionException(failure);
            }
        }).whenComplete((selection, failure) -> application.postRunnable(() -> {
            settingsBackupPending = false;
            if (disposed || surface != Surface.SETTINGS || settingsReturnSurface != Surface.MENU
                    || settingsHavePendingChanges() || applicationExitConfirmation) return;
            if (failure != null) {
                Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                showProfileBackupResult(new GdxProfileBackupTransfer.Result(
                        "gdx.backup.failed", Objects.toString(cause.getMessage(), cause.toString())));
            } else if (selection != null) {
                profileBackupSelection = selection;
                captureFrontendModalInput();
            }
        }));
    }

    private void chooseProfileBackupDirectory(ProfileBackupSelection selection) {
        if (settingsBackupPending || selection.importing()) return;
        settingsBackupPending = true;
        captureFrontendModalInput();
        var application = Gdx.app;
        String title = gameText.translate("gdx.backup.change_folder");
        String select = gameText.translate("ui.aceptar");
        GdxFileChooser.chooseDirectory(selection.archive().getParent(), title, select)
                .thenApplyAsync(folder -> {
            if (folder == null) return null;
            try {
                Path directory = GdxProfileBackup.exportDirectory(GdxProfileBackup.profile(), folder);
                if (!Files.isWritable(directory)) throw new IOException(directory.toString());
                return GdxProfileBackupTransfer.availableExportPath(
                        directory.resolve(selection.archive().getFileName()));
            } catch (Exception failure) {
                throw new CompletionException(failure);
            }
        }).whenComplete((archive, failure) -> application.postRunnable(() -> {
            settingsBackupPending = false;
            if (disposed || profileBackupSelection != selection) return;
            captureFrontendModalInput();
            if (failure != null) {
                LOGGER.log(Level.WARNING, "Cannot select the backup export folder", failure);
                showFrontendNotice(gameText.translate("gdx.dialog.error"),
                        gameText.translate("gdx.backup.invalid_folder"), true);
            } else if (archive != null) {
                profileBackupSelection = new ProfileBackupSelection(false, archive,
                        selection.available(), selection.selected());
            }
        }));
    }

    private void drawProfileBackupSelection(ProfileBackupSelection selection) {
        float width = 1140f;
        List<String> pathLines = selection.importing() ? List.of()
                : profileBackupPathLines(selection.archive().toString(), width - 88f, 5);
        float height = (selection.importing() ? 324f : 462f)
                + selection.available().size() * 62f
                + Math.max(0, pathLines.size() - 1) * 26f;
        float x = (WIDTH - width) / 2f;
        float y = (HEIGHT - height) / 2f;
        float top = y + height;
        float center = x + width / 2f;
        GdxUiDialogStyle.drawPanel(shapes, x, y, width, height, CYAN_DARK, 1f);
        String action = uppercase(gameText.translate(selection.actionKey()));
        textFit(headingFont, action, center, top - 56f, GOLD, true, width - 88f);
        textFit(actionFont, gameText.translate(selection.importing()
                ? "gdx.backup.import_select" : "gdx.backup.export_select"),
                center, top - 92f, Color.WHITE, true, width - 88f);

        float rowY = top - 178f;
        float rowWidth = width - 88f;
        for (var category : selection.available()) {
            boolean checked = selection.selected().contains(category);
            float rowX = x + 44f;
            GdxUiButtonStyle.draw(shapes, rowX, rowY, rowWidth, 52f,
                    GdxUiButtonStyle.Tone.NEUTRAL, true,
                    hovered(rowX, rowY, rowWidth, 52f) ? 1f : 0f,
                    pressed(rowX, rowY, rowWidth, 52f), 1f);
            GdxUiButtonStyle.draw(shapes, rowX + 16f, rowY + 10f, 32f, 32f,
                    checked ? GdxUiButtonStyle.Tone.POSITIVE : GdxUiButtonStyle.Tone.NEUTRAL,
                    true, 0f, false, 1f);
            if (checked) {
                shapes.setColor(GOLD);
                shapes.rectLine(rowX + 23f, rowY + 25f, rowX + 29f, rowY + 19f, 3f);
                shapes.rectLine(rowX + 29f, rowY + 19f, rowX + 41f, rowY + 33f, 3f);
            }
            textFit(actionFont, gameText.translate(category.textKey()),
                    rowX + 66f, rowY + 38f, Color.WHITE, false, rowWidth - 84f);
            hit(rowX, rowY, rowWidth, 52f, () -> {
                if (checked) selection.selected().remove(category);
                else selection.selected().add(category);
                playFrontendSwitchSound(!checked);
            });
            rowY -= 62f;
        }

        float notesY = rowY + 28f;
        textFit(smallFont, gameText.translate("gdx.backup.restarts_game"),
                center, notesY, Color.WHITE, true, width - 88f);
        textFit(smallFont, gameText.translate(selection.importing()
                ? "gdx.backup.keeps_profile" : "gdx.backup.private_zip"),
                center, notesY - 32f, MUTED, true, width - 88f);
        if (!selection.importing()) {
            textFit(smallFont, gameText.translate("gdx.backup.destination"),
                    center, notesY - 70f, GOLD, true, width - 88f);
            for (int index = 0; index < pathLines.size(); index++) {
                text(smallFont, pathLines.get(index),
                        center, notesY - 102f - index * 26f, Color.WHITE, true);
            }
            themedButton(center - 145f, y + 116f, 290f, 48f,
                    uppercase(gameText.translate("gdx.backup.change_folder")),
                    ButtonTone.NEUTRAL, () -> chooseProfileBackupDirectory(selection),
                    !settingsBackupPending, smallFont);
        }
        themedButton(center - 316f, y + 32f, 290f, 62f,
                uppercase(gameText.translate("ui.cancelar")), ButtonTone.NEUTRAL,
                this::closeProfileBackupSelection, !settingsBackupPending);
        themedButton(center + 26f, y + 32f, 290f, 62f,
                action, ButtonTone.POSITIVE, () -> confirmProfileBackup(selection),
                !settingsBackupPending && !selection.selected().isEmpty());
    }

    private void closeProfileBackupSelection() {
        profileBackupSelection = null;
        captureFrontendModalInput();
    }

    private void confirmProfileBackup(ProfileBackupSelection selection) {
        GdxApplicationShell shell = GdxApplicationShell.active();
        if (shell == null || selection.selected().isEmpty() || settingsHavePendingChanges()) return;
        shell.exitForProfileBackup(new GdxProfileBackupTransfer.Request(
                selection.importing(), selection.archive(), selection.selected(),
                gameText.language()));
    }

    private List<String> profileBackupPathLines(String path, float width, int maxLines) {
        List<String> lines = new ArrayList<>();
        while (lines.size() < maxLines - 1 && !fits(smallFont, path, width)) {
            int end = 0;
            while (end < path.length()) {
                int next = path.offsetByCodePoints(end, 1);
                if (!fits(smallFont, path.substring(0, next), width)) break;
                end = next;
            }
            if (end == 0) break;
            int separator = Math.max(path.lastIndexOf('/', end - 1),
                    path.lastIndexOf('\\', end - 1));
            if (separator >= 0 && separator + 1 >= end * 0.8f) end = separator + 1;
            lines.add(path.substring(0, end));
            path = path.substring(end);
        }
        lines.add(ellipsize(smallFont, path, width));
        return lines;
    }

    void showProfileBackupResult(GdxProfileBackupTransfer.Result result) {
        String message = gameText.translate(result.messageKey());
        if (!result.detail().isEmpty()) {
            String detail = result.error() ? result.detail()
                    : String.join("\n", profileBackupPathLines(result.detail(),
                            GdxUiDialogStyle.noticeLayout(WIDTH, HEIGHT).message().width, 4));
            message += "\n" + detail;
        }
        showFrontendNotice(gameText.translate(result.error() ? "gdx.dialog.error" : "settings.ajustes"),
                message, result.error());
    }

    private void drawAudioSettings(float x, float y, float w, float h) {
        settingsAudioPage = MathUtils.clamp(settingsAudioPage, 0,
                GdxSettingsContract.AUDIO_PAGES.size() - 1);
        GdxSettingsContract.TogglePage page =
                GdxSettingsContract.audioPage(settingsAudioPage,
                        presentationSettings, gameText.language());
        float rowY = y + h - GdxSettingsLayout.CONTENT_ROW_TOP_INSET;
        float baseX = x + GdxSettingsLayout.CONTENT_HORIZONTAL_INSET;
        float baseWidth = w - 2f
                * GdxSettingsLayout.CONTENT_HORIZONTAL_INSET;
        String modStatus = GdxGameAudioResources.isJokeSoundsPage(page)
                ? GdxGameAudioResources.jokeModStatus(presentationSettings,
                        gameText.language(), gameText)
                : "";
        GdxSettingsLayout.StatusBand statusBand =
                GdxSettingsLayout.statusBand(rowY,
                        rowY + GdxSettingsLayout.ROW_HEIGHT,
                        !modStatus.isBlank());
        rowY = statusBand.firstRowY();
        int totalRows = GdxSettingsContract.audioRowCount(page);
        List<GdxSettingsContract.RowGroup> groups =
                GdxSettingsContract.audioGroups(page);
        GdxSettingsLayout.PixelRows rows = GdxSettingsLayout.pixelRows(
                rowY, y + GdxSettingsLayout.CONTENT_VERTICAL_INSET,
                statusBand.viewportTop(),
                totalRows, settingsAudioScroll, groups);
        settingsAudioScroll = rows.offset();
        if (!modStatus.isBlank()) {
            textFit(smallFont, uppercase(modStatus),
                    baseX + baseWidth / 2f,
                    statusBand.scrolledTextCenterY(rows.offset()),
                    MOD_OVERRIDE, true,
                    baseWidth - 24f);
        }
        GdxSettingsLayout.drawGroups(shapes, baseX, baseWidth, rows, groups,
                1f);
        if (GdxSettingsContract.hasMasterVolume(page)) {
            float volumeY = rows.rowY(
                    GdxSettingsContract.masterVolumeRow(page));
            drawFrontendVolumeControl(baseX, volumeY, baseWidth,
                    audioControl.enabled());
            settingsTooltip(GdxSettingsLayout.volumeRow(
                    baseX, volumeY, baseWidth).bounds(), "master_volume");
        }
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
            Rectangle row = GdxSettingsLayout.groupedRow(baseX,
                    rows.rowY(rowIndex), baseWidth, rowIndex, groups,
                    totalRows);
            GdxSettingsContract.AudioPreview preview =
                    GdxGameAudioResources.availablePreview(
                            presentationSettings, option,
                            gameText.language());
            boolean modOverride = GdxGameAudioResources.modOverridesSetting(
                    presentationSettings, option, gameText.language());
            String optionLabel = GdxModSettingsIndicator.label(
                    option.label(gameText), modOverride);
            settingsBooleanToggle(row.x, row.y, row.width,
                    optionLabel, value,
                    GdxSettingsContract.displayedFallback(option),
                    () -> runSettingsOptionMutation(page, action),
                    enabled, preview != null,
                    settingsOptionAnimationKey(option.key()));
            settingsTooltip(row, option.key(), modOverride,
                    GdxGameAudioResources.jokeBankMode(
                            presentationSettings, option.key(),
                            gameText.language()));
            if (preview != null) {
                drawAudioPreviewControl(row, option.key(), preview);
            }
        }
        drawSettingsRowScrollbar(GdxSettingsLayout.rowScrollbarX(x, w), rows);
        if (GdxSettingsContract.hasVoiceRetention(page)) {
            int retentionRow = GdxSettingsContract.voiceRetentionRow(page);
            rowY = rows.rowY(retentionRow);
            Rectangle retention = GdxSettingsLayout.groupedRow(baseX, rowY,
                    baseWidth, retentionRow, groups, totalRows);
            settingsDropdownChoice(retention.x, retention.y, retention.width,
                    GdxSettingsLayout.ROW_HEIGHT,
                    uppercase(gameText.translate(
                            "gdx.settings.row.keep_voice_notes")),
                    GdxSettingsContract.markDefault(
                            GdxSettingsContract.voiceRetentionLabel(
                                    initialProperties, gameText),
                            GdxSettingsContract.voiceRetentionDays(
                                    initialProperties) == 90),
                    Dropdown.VOICE_RETENTION, true);
            settingsTooltip(retention,
                    "audio_voice_note_retention_days");
            int actionsRowIndex = GdxSettingsContract.voiceActionsRow(page);
            rowY = rows.rowY(actionsRowIndex);
            Rectangle actionsBounds = GdxSettingsLayout.groupedRow(baseX,
                    rowY, baseWidth, actionsRowIndex, groups, totalRows);
            GdxSettingsLayout.SplitRow actions =
                    GdxSettingsLayout.splitActionRow(actionsBounds.x,
                            actionsBounds.y, actionsBounds.width);
            Rectangle view = actions.left();
            Rectangle purge = actions.right();
            themedButton(view.x, view.y, view.width, view.height,
                    uppercase(gameText.translate("audio.ver_notas")),
                    ButtonTone.NEUTRAL, this::openVoiceNotes, true);
            themedButton(purge.x, purge.y, purge.width, purge.height,
                    uppercase(gameText.translate("audio.purgar_notas")),
                    ButtonTone.DANGER, () -> {
                        voiceNotesPurgeConfirmation = true;
                        openVoiceNotes();
                    }, true);
            settingsTooltip(view, "voice_notes_view");
            settingsTooltip(purge, "voice_notes_purge");
        }
        if (GdxSettingsContract.hasAudioDevices(page)) {
            boolean microphoneEnabled = GdxSettingsContract.microphoneEnabled(
                    initialProperties);
            int outputRow = GdxSettingsContract.audioOutputDeviceRow(page);
            int captureRow = GdxSettingsContract.audioCaptureDeviceRow(page);
            Rectangle output = GdxSettingsLayout.groupedRow(baseX,
                    rows.rowY(outputRow), baseWidth, outputRow, groups,
                    totalRows);
            Rectangle capture = GdxSettingsLayout.groupedRow(baseX,
                    rows.rowY(captureRow), baseWidth, captureRow, groups,
                    totalRows);
            settingsAudioDeviceDropdownChoice(output.x, output.y,
                    output.width,
                    GdxSettingsLayout.ROW_HEIGHT,
                    uppercase(gameText.translate(
                            "gdx.settings.row.output_device")),
                    GdxSettingsContract.markDefault(
                            GdxAudioDevices.outputLabel(initialProperties,
                                    gameText),
                            initialProperties.getProperty(
                                    GdxAudioDevices.OUTPUT_KEY, "").isBlank()),
                    Dropdown.AUDIO_OUTPUT, true);
            settingsAudioDeviceDropdownChoice(capture.x, capture.y,
                    capture.width,
                    GdxSettingsLayout.ROW_HEIGHT,
                    uppercase(gameText.translate(
                            "gdx.settings.row.input_device")),
                    GdxSettingsContract.markDefault(
                            GdxAudioDevices.captureLabel(initialProperties,
                                    gameText),
                            initialProperties.getProperty(
                                    GdxAudioDevices.CAPTURE_KEY, "").isBlank()),
                    Dropdown.AUDIO_CAPTURE, microphoneEnabled);
            settingsTooltip(output, "gdx_audio_output_device");
            settingsTooltip(capture, "audio_capture_device");
        }
    }

    private boolean frontendAudioOptionEnabled(
            GdxSettingsContract.ToggleOption option) {
        return GdxSettingsContract.enabled(option, initialProperties,
                audioControl.enabled(),
                frontendMayEditGlobalCommunicationRules());
    }

    private float frontendSettingsOptionTarget(
            GdxSettingsContract.ToggleOption option) {
        boolean value = GdxSettingsContract.displayedValue(option,
                initialProperties, audioControl.enabled());
        return value && frontendAudioOptionEnabled(option) ? 1f : 0f;
    }

    private String settingsOptionAnimationKey(String optionKey) {
        return Surface.SETTINGS.name() + ':' + settingsReturnSurface.name()
                + ":option:" + optionKey;
    }

    private void runSettingsOptionMutation(
            GdxSettingsContract.TogglePage page, Runnable action) {
        Map<String, Float> before = new LinkedHashMap<>();
        for (GdxSettingsContract.ToggleOption option : page.options()) {
            before.put(option.key(), frontendSettingsOptionTarget(option));
        }
        long changedAt = System.nanoTime();
        action.run();
        for (GdxSettingsContract.ToggleOption option : page.options()) {
            float oldTarget = before.get(option.key());
            float newTarget = frontendSettingsOptionTarget(option);
            if (Float.compare(oldTarget, newTarget) != 0) {
                String key = settingsOptionAnimationKey(option.key());
                settingsToggleTransitions.begin(key, oldTarget,
                        newTarget, changedAt);
            }
        }
    }

    private void drawAudioPreviewControl(Rectangle row, String key,
            GdxSettingsContract.AudioPreview preview) {
        float x = row.x + row.width - 190f;
        float y = row.y + 11f;
        float size = 46f;
        boolean active = audioPreview.active(key);
        boolean hover = hovered(x, y, size, size);
        GdxUiButtonStyle.draw(shapes, x, y, size, size,
                active ? GdxUiButtonStyle.Tone.FEATURED
                        : GdxUiButtonStyle.Tone.NEUTRAL,
                true, hover ? 1f : 0f,
                pressed(x, y, size, size), 1f);
        shapes.setColor(active ? GOLD : CYAN);
        if (active) {
            shapes.rect(x + 15f, y + 15f, 16f, 16f);
        } else {
            shapes.triangle(x + 16f, y + 12f,
                    x + 16f, y + 34f, x + 34f, y + 23f);
        }
        hit(x, y, size, size,
                () -> audioPreview.toggle(key, preview, masterVolume(),
                        GdxGameAudioResources.resolve(
                                presentationSettings, preview.resource())));
    }

    private boolean frontendMayEditGlobalCommunicationRules() {
        return settingsReturnSurface != Surface.LOBBY
                || lobby == null || lobby.host();
    }

    private void drawFrontendVolumeControl(float x, float y, float w,
            boolean enabled) {
        GdxSettingsLayout.VolumeRow row = GdxSettingsLayout.volumeRow(x, y, w);
        Rectangle label = row.label();
        Rectangle percentage = row.percentage();
        Rectangle minus = row.minusButton();
        Rectangle plus = row.plusButton();
        GdxSettingsStyle.drawVolumeRow(shapes, row, masterVolume(),
                enabled ? 1f : 0.42f);
        textFit(smallFont, uppercase(gameText.translate(
                "gdx.settings.row.master_volume")), label.x,
                GdxSettingsLayout.volumeTextBaseline(row),
                enabled ? Color.WHITE : DISABLED,
                false, label.width);
        textFit(smallFont, GdxSettingsContract.markDefault(
                Math.round(masterVolume() * 100f) + "%",
                Float.compare(masterVolume(), 0.8f) == 0),
                percentage.x + percentage.width / 2f,
                GdxSettingsLayout.volumeTextBaseline(row),
                enabled ? GOLD : DISABLED, true,
                percentage.width - 8f);
        themedButton(minus.x, minus.y, minus.width, minus.height, "-",
                ButtonTone.NEUTRAL,
                () -> adjustFrontendMasterVolume(-GdxSettingsContract.MASTER_VOLUME_STEP), enabled,
                settingsStepperSignFont);
        themedButton(plus.x, plus.y, plus.width, plus.height, "+",
                ButtonTone.NEUTRAL,
                () -> adjustFrontendMasterVolume(GdxSettingsContract.MASTER_VOLUME_STEP), enabled,
                settingsStepperSignFont);
    }

    private void drawAppearanceSettings(float x, float y, float w, float h) {
        int pages = GdxSettingsContract.APPEARANCE_PAGES.size() + 2;
        settingsAppearancePage = MathUtils.clamp(settingsAppearancePage,
                0, pages - 1);
        float rowY = y + h - GdxSettingsLayout.CONTENT_ROW_TOP_INSET;
        if (settingsAppearancePage == 0) {
            var page = GdxSettingsContract.appearanceTablePage();
            int rowCount = GdxSettingsContract.appearanceTableRowCount();
            var groups = GdxSettingsContract.appearanceTableGroups();
            GdxSettingsLayout.PixelRows rows = GdxSettingsLayout.pixelRows(
                    rowY, y + GdxSettingsLayout.CONTENT_VERTICAL_INSET,
                    rowY + GdxSettingsLayout.ROW_HEIGHT,
                    rowCount, settingsAppearanceScroll, groups);
            settingsAppearanceScroll = rows.offset();
            float baseX = x + GdxSettingsLayout.CONTENT_HORIZONTAL_INSET;
            float baseWidth = w - 2f
                    * GdxSettingsLayout.CONTENT_HORIZONTAL_INSET;
            GdxSettingsLayout.drawGroups(shapes, baseX, baseWidth, rows,
                    groups, 1f);
            String[] keys = {"baraja", "trasera", "color_tapete",
                "nivel_luz"};
            for (int row = rows.firstIndex(); row < rows.lastExclusive(); row++) {
                float currentY = rows.rowY(row);
                Rectangle bounds = GdxSettingsLayout.groupedRow(baseX,
                        currentY, baseWidth, row, groups, rowCount);
                if (row == 0) {
                    settingsDropdownChoice(baseX, currentY, baseWidth,
                            GdxSettingsLayout.ROW_HEIGHT,
                            uppercase(gameText.translate(
                                    "gdx.settings.row.deck")),
                            GdxSettingsContract.markDefault(
                                    GdxModSettingsIndicator.label(
                                            GdxAppearanceOptions.deckLabel(
                                                    configuredDeck(), gameText),
                                            GdxModSettingsIndicator.deck(
                                                    presentationSettings,
                                                    configuredDeck())),
                                    "goliat".equalsIgnoreCase(
                                            configuredDeck())),
                            Dropdown.DECK, true);
                } else if (row == 1) {
                    settingsDropdownChoice(baseX, currentY, baseWidth,
                            GdxSettingsLayout.ROW_HEIGHT,
                            uppercase(gameText.translate(
                                    "gdx.settings.row.card_back")),
                            GdxSettingsContract.markDefault(
                                    GdxModSettingsIndicator.label(
                                            GdxAppearanceOptions.cardBackLabel(
                                                    configuredBack(), gameText),
                                            GdxModSettingsIndicator.cardBack(
                                                    presentationSettings,
                                                    configuredBack())),
                                    "default".equalsIgnoreCase(
                                            configuredBack())),
                            Dropdown.CARD_BACK, true);
                } else if (row == 2) {
                    settingsDropdownChoice(baseX, currentY, baseWidth,
                            GdxSettingsLayout.ROW_HEIGHT,
                            uppercase(gameText.translate(
                                    "gdx.settings.row.felt")),
                            GdxSettingsContract.markDefault(
                                    GdxAppearanceOptions.feltLabel(
                                            configuredFelt(), gameText),
                                    "verde".equalsIgnoreCase(
                                            configuredFelt())),
                            Dropdown.FELT, true);
                } else if (row == 3) {
                    settingsStepper(baseX, currentY, baseWidth,
                            GdxSettingsLayout.ROW_HEIGHT,
                            uppercase(gameText.translate(
                                    "gdx.settings.row.light_off")),
                            GdxSettingsContract.markDefault(
                                    GdxAppearanceOptions.lightLevelLabel(
                                            initialProperties),
                                    Integer.toString(GdxAppearanceOptions
                                            .DEFAULT_LIGHT_LEVEL).equals(
                                            initialProperties.getProperty(
                                                    "nivel_luz",
                                                    Integer.toString(
                                                            GdxAppearanceOptions
                                                                    .DEFAULT_LIGHT_LEVEL)))),
                            () -> adjustLightLevel(-1),
                            () -> adjustLightLevel(1));
                } else {
                    var option = page.options().get(row
                            - GdxSettingsContract.APPEARANCE_PRIMARY_ROW_COUNT);
                    boolean enabled = GdxSettingsContract.enabled(option,
                            initialProperties, audioControl.enabled());
                    settingsBooleanToggle(bounds.x, bounds.y, bounds.width,
                            option.label(gameText),
                            preferenceBoolean(option.key(), option.fallback()),
                            GdxSettingsContract.displayedFallback(option),
                            () -> runSettingsOptionMutation(page,
                                    () -> togglePreference(option.key(),
                                            option.fallback())),
                            enabled, false,
                            settingsOptionAnimationKey(option.key()));
                }
                String key = row < keys.length ? keys[row]
                        : page.options()
                                .get(row - GdxSettingsContract
                                        .APPEARANCE_PRIMARY_ROW_COUNT).key();
                settingsTooltip(bounds, key);
            }
            drawSettingsRowScrollbar(GdxSettingsLayout.rowScrollbarX(x, w),
                    rows);
            return;
        }
        if (GdxSettingsContract.isAppearanceGraphicsPage(
                settingsAppearancePage)) {
            float baseX = x + GdxSettingsLayout.CONTENT_HORIZONTAL_INSET;
            float baseWidth = w - 2f
                    * GdxSettingsLayout.CONTENT_HORIZONTAL_INSET;
            List<GdxSettingsContract.RowGroup> groups =
                    GdxSettingsContract.appearanceGraphicsGroups();
            GdxSettingsLayout.PixelRows rows = GdxSettingsLayout.pixelRows(
                    rowY, y + GdxSettingsLayout.CONTENT_VERTICAL_INSET,
                    rowY + GdxSettingsLayout.ROW_HEIGHT,
                    GdxSettingsContract.APPEARANCE_GRAPHICS_ROW_COUNT,
                    settingsAppearanceScroll, groups);
            settingsAppearanceScroll = rows.offset();
            GdxSettingsLayout.drawGroups(shapes, baseX, baseWidth, rows,
                    groups, 1f);
            String[] keys = {"gdx_window_mode", "auto_fullscreen",
                "gdx_vsync", "gdx_fps_limit", "gdx_msaa_samples",
                GdxSettingsContract.CARD_RENDERING_KEY, "gdx_show_fps",
                "gpu_renderer"};
            for (int row = rows.firstIndex();
                    row < rows.lastExclusive(); row++) {
                Rectangle bounds = GdxSettingsLayout.groupedRow(baseX,
                        rows.rowY(row), baseWidth, row, groups,
                        GdxSettingsContract.APPEARANCE_GRAPHICS_ROW_COUNT);
                if (row == 0) {
                    settingsDropdownChoice(bounds.x, bounds.y, bounds.width,
                            bounds.height,
                            uppercase(gameText.translate(
                                    "gdx.settings.row.window_mode")),
                            GdxSettingsContract.markDefault(
                                    windowModeSettingLabel(),
                                    GdxWindowMode.configured(initialProperties)
                                            == GdxWindowMode.BORDERLESS),
                            Dropdown.WINDOW_MODE, true);
                } else if (row == 1) {
                    boolean enabled = GdxWindowMode
                            .allowsAutomaticFullscreen(initialProperties);
                    settingsBooleanToggle(bounds.x, bounds.y, bounds.width,
                            uppercase(gameText.translate(
                                    "gdx.settings.option.auto_fullscreen")),
                            preferenceBoolean("auto_fullscreen", true), true,
                            () -> togglePreference("auto_fullscreen", true),
                            enabled);
                } else if (row == 2) {
                    settingsValueToggle(bounds.x, bounds.y, bounds.width,
                            uppercase(gameText.translate(
                                    "gdx.settings.row.vsync")),
                            GdxSettingsContract.vsyncStatusLabel(
                                    presentationSettings.vsyncEnabled(),
                                    gameText),
                            presentationSettings.vsyncEnabled(),
                            () -> presentationSettings.toggleVsync(false),
                            true);
                } else if (row == 3) {
                    settingsDropdownChoice(bounds.x, bounds.y, bounds.width,
                            bounds.height,
                            uppercase(gameText.translate(
                                    "gdx.settings.row.fps_limit")),
                            GdxSettingsContract.markDefault(
                                    frameRateLimitSettingLabel(),
                                    GdxSettingsContract.isDefaultFrameRateLimit(
                                            initialProperties)),
                            Dropdown.FPS_LIMIT, true);
                } else if (row == 4) {
                    settingsDropdownChoice(bounds.x, bounds.y,
                            bounds.width, bounds.height,
                            uppercase(gameText.translate(
                                    "gdx.settings.row.antialiasing_scene")),
                            GdxSettingsContract.markDefault(msaaSettingLabel(),
                                    "4".equals(initialProperties.getProperty(
                                            "gdx_msaa_samples", "4"))),
                            msaaRestartStatus(), Dropdown.MSAA, true);
                } else if (row == 5) {
                    settingsDropdownChoice(bounds.x, bounds.y, bounds.width,
                            bounds.height,
                            uppercase(gameText.translate(
                                    "gdx.settings.row.antialiasing_cards")),
                            GdxSettingsContract.markDefault(
                                    cardRenderingSettingLabel(),
                                    Integer.toString(GdxSettingsContract
                                            .DEFAULT_CARD_RENDERING_MODE)
                                            .equals(initialProperties
                                                    .getProperty(
                                                            GdxSettingsContract
                                                                    .CARD_RENDERING_KEY,
                                                            Integer.toString(
                                                                    GdxSettingsContract
                                                                            .DEFAULT_CARD_RENDERING_MODE)))),
                            Dropdown.CARD_RENDERING, true);
                } else if (row == 6) {
                    settingsBooleanToggle(bounds.x, bounds.y, bounds.width,
                            uppercase(gameText.translate(
                                    "gdx.settings.option.gdx_show_fps")),
                            preferenceBoolean("gdx_show_fps", false), false,
                            () -> togglePreference("gdx_show_fps", false),
                            true);
                } else {
                    settingsInfoRow(bounds.x, bounds.y, bounds.width,
                            bounds.height,
                            uppercase(gameText.translate(
                                    "gdx.settings.row.gpu_renderer")),
                            GdxGraphicsInfo.displayValue(gameText));
                }
                settingsTooltip(bounds, keys[row]);
            }
            drawSettingsRowScrollbar(GdxSettingsLayout.rowScrollbarX(x, w),
                    rows);
            return;
        }
        GdxSettingsContract.TogglePage page =
                GdxSettingsContract.appearanceTogglePage(
                        settingsAppearancePage);
        int rowCount = GdxSettingsContract.appearanceRowCount(page);
        List<GdxSettingsContract.RowGroup> groups =
                GdxSettingsContract.appearanceGroups(page);
        GdxSettingsLayout.PixelRows rows = GdxSettingsLayout.pixelRows(
                rowY, y + GdxSettingsLayout.CONTENT_VERTICAL_INSET,
                rowY + GdxSettingsLayout.ROW_HEIGHT,
                rowCount, settingsAppearanceScroll, groups);
        settingsAppearanceScroll = rows.offset();
        float baseX = x + GdxSettingsLayout.CONTENT_HORIZONTAL_INSET;
        float baseWidth = w - 2f
                * GdxSettingsLayout.CONTENT_HORIZONTAL_INSET;
        GdxSettingsLayout.drawGroups(shapes, baseX, baseWidth, rows, groups,
                1f);
        List<GdxSettingsContract.AppearanceAnimationRow> animationRows =
                GdxSettingsContract.appearanceRows(page);
        for (int row = rows.firstIndex(); row < rows.lastExclusive(); row++) {
            float currentY = rows.rowY(row);
            GdxSettingsContract.AppearanceAnimationRow animationRow =
                    animationRows.get(row);
            if (animationRow.toggle() != null) {
                GdxSettingsContract.ToggleOption option =
                        animationRow.toggle();
                boolean enabled = GdxSettingsContract.enabled(option,
                        initialProperties, audioControl.enabled());
                boolean value = preferenceBoolean(option.key(),
                        option.fallback());
                Rectangle rowBounds = GdxSettingsLayout.groupedRow(baseX,
                        currentY, baseWidth, row, groups, rowCount);
                settingsBooleanToggle(rowBounds.x, rowBounds.y,
                        rowBounds.width, GdxModSettingsIndicator.label(
                                option.label(gameText),
                                GdxModSettingsIndicator.option(
                                        presentationSettings, option.key())),
                        value,
                        GdxSettingsContract.displayedFallback(option),
                        () -> runSettingsOptionMutation(page,
                                () -> togglePreference(option.key(),
                                        option.fallback())),
                        enabled, false,
                        settingsOptionAnimationKey(option.key()));
                settingsTooltip(rowBounds, option.key());
            } else {
                GdxAppearanceOptions.Choice option =
                        animationRow.choice();
                boolean enabled = GdxAppearanceOptions.enabled(option,
                        initialProperties);
                Rectangle rowBounds = GdxSettingsLayout.groupedRow(baseX,
                        currentY, baseWidth, row, groups, rowCount);
                Rectangle dropdownBounds = settingsDropdownAnchor(
                        rowBounds.x, rowBounds.y, rowBounds.width,
                        GdxSettingsLayout.ROW_HEIGHT);
                settingsDropdownChoice(rowBounds.x, rowBounds.y,
                        rowBounds.width, GdxSettingsLayout.ROW_HEIGHT,
                        option.label(gameText),
                        GdxSettingsContract.markDefault(
                                GdxAppearanceOptions.selectedLabel(option,
                                        initialProperties, gameText),
                                option.fallback().equals(option.values().get(
                                        GdxAppearanceOptions.selectedIndex(
                                                option,
                                                initialProperties)))),
                        () -> openAppearanceChoiceDropdown(option,
                                dropdownBounds), enabled);
                settingsTooltip(rowBounds, option.key());
            }
        }
        drawSettingsRowScrollbar(GdxSettingsLayout.rowScrollbarX(x, w), rows);
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
        settingsRowScrollTrack.set(x, y, trackWidth, height);
        settingsRowScrollThumbHeight = thumbHeight;
        float thumbY = y + (height - thumbHeight)
                * (1f - rows.offset() / rows.maximum());
        GdxSettingsStyle.drawRowScrollbar(shapes, x, y, height, thumbY,
                thumbHeight, 1f);
    }

    /** Compact settings row: label and value share one bounded surface. */
    private void settingsChoice(float x, float y, float w, float h,
            String label, String value, Runnable action) {
        settingsChoice(x, y, w, h, label, value, action, true);
    }

    private void settingsChoice(float x, float y, float w, float h,
            String label, String value, Runnable action, boolean enabled) {
        GdxSettingsLayout.ChoiceRow row = settingsChoiceTextRow(x, y, w, h,
                value);
        GdxSettingsStyle.drawRow(shapes, x, y, w, h, enabled,
                enabled && hovered(x, y, w, h),
                enabled && pressed(x, y, w, h), 1f);
        embeddedButtonSurface(row.arrow().x, row.arrow().y,
                row.arrow().width, row.arrow().height, enabled);
        textFit(smallFont, uppercase(label), row.label().x,
                y + h / 2f + 10f,
                enabled ? Color.WHITE : DISABLED, false, row.label().width);
        textFit(uiFont, value,
                row.value().x + row.value().width / 2f,
                y + h / 2f + 11f, enabled ? GOLD : DISABLED, true,
                row.value().width);
        shapes.setColor(enabled ? GOLD : DISABLED);
        float cy = y + h / 2f;
        float arrowX = row.arrow().x + row.arrow().width / 2f;
        shapes.rectLine(arrowX - 5f, cy - 9f, arrowX + 4f, cy, 3f);
        shapes.rectLine(arrowX + 4f, cy, arrowX - 5f, cy + 9f, 3f);
        if (enabled) hit(x, y, w, h, action);
    }

    private void settingsDropdownChoice(float x, float y, float w, float h,
            String label, String value, Dropdown target, boolean enabled) {
        settingsDropdownChoice(x, y, w, h, label, value, "",
                () -> openDropdown(target, settingsDropdownAnchor(x, y, w, h)),
                enabled);
    }

    private void settingsDropdownChoice(float x, float y, float w, float h,
            String label, String value, String status, Dropdown target,
            boolean enabled) {
        settingsDropdownChoice(x, y, w, h, label, value, status,
                () -> openDropdown(target, settingsDropdownAnchor(x, y, w, h)),
                enabled);
    }

    private void settingsDropdownChoice(float x, float y, float w, float h,
            String label, String value, Runnable action, boolean enabled) {
        settingsDropdownChoice(x, y, w, h, label, value, "", action,
                enabled);
    }

    private void settingsDropdownChoice(float x, float y, float w, float h,
            String label, String value, String status, Runnable action,
            boolean enabled) {
        GdxSettingsLayout.ChoiceRow row = settingsChoiceTextRow(x, y, w, h,
                value);
        GdxSettingsStyle.drawRow(shapes, x, y, w, h, enabled,
                enabled && hovered(x, y, w, h),
                enabled && pressed(x, y, w, h), 1f);
        boolean hasStatus = status != null && !status.isBlank();
        String caption = uppercase(label);
        float labelWidth = hasStatus
                ? GdxSettingsLayout.labelWidthWithStatus(row.label().width,
                        textWidth(smallFont, caption)) : row.label().width;
        textFit(smallFont, caption, row.label().x,
                GdxSettingsLayout.rowTextBaseline(y),
                enabled ? Color.WHITE : DISABLED, false, labelWidth);
        boolean modOverrideValue = GdxModSettingsIndicator.marked(value)
                || GdxModSettingsIndicator.marked(status);
        Color valueColor = modOverrideValue
                ? enabled ? MOD_OVERRIDE : MOD_OVERRIDE_DISABLED
                : enabled ? GOLD : DISABLED;
        if (hasStatus) {
            float statusWidth = row.label().width - labelWidth - 18f;
            textFit(smallFont, status,
                    row.label().x + labelWidth + 18f + statusWidth / 2f,
                    GdxSettingsLayout.rowTextBaseline(y), valueColor, true,
                    statusWidth);
        }
        textFit(uiFont, value,
                row.value().x + row.value().width / 2f,
                GdxSettingsLayout.rowTextBaseline(y),
                valueColor, true, row.value().width - 12f);
        shapes.setColor(enabled ? GOLD : DISABLED);
        float cx = row.arrow().x + row.arrow().width / 2f;
        float cy = row.arrow().y + row.arrow().height / 2f;
        shapes.rectLine(cx - 11f, cy + 6f, cx, cy - 5f, 3f);
        shapes.rectLine(cx, cy - 5f, cx + 11f, cy + 6f, 3f);
        if (enabled) hit(x, y, w, h, action);
    }

    private GdxSettingsLayout.ChoiceRow settingsChoiceTextRow(float x, float y,
            float w, float h, String value) {
        return GdxSettingsLayout.fitChoiceValue(
                GdxSettingsLayout.choiceRow(x, y, w, h),
                textWidth(uiFont, value) + 12f);
    }

    private void settingsAudioDeviceDropdownChoice(float x, float y, float w,
            float h, String label, String value, Dropdown target,
            boolean enabled) {
        GdxSettingsLayout.ChoiceRow row =
                GdxSettingsLayout.audioDeviceChoiceRow(x, y, w, h);
        GdxSettingsStyle.drawRow(shapes, x, y, w, h, enabled,
                enabled && hovered(x, y, w, h),
                enabled && pressed(x, y, w, h), 1f);
        textFit(smallFont, uppercase(label), row.label().x,
                GdxSettingsLayout.rowTextBaseline(y),
                enabled ? Color.WHITE : DISABLED, false, row.label().width);
        textFit(uiFont, value,
                row.value().x + row.value().width / 2f,
                GdxSettingsLayout.rowTextBaseline(y),
                enabled ? GOLD : DISABLED, true, row.value().width - 12f);
        shapes.setColor(enabled ? GOLD : DISABLED);
        float cx = row.arrow().x + row.arrow().width / 2f;
        float cy = row.arrow().y + row.arrow().height / 2f;
        shapes.rectLine(cx - 11f, cy + 6f, cx, cy - 5f, 3f);
        shapes.rectLine(cx, cy - 5f, cx + 11f, cy + 6f, 3f);
        if (enabled) {
            Rectangle anchor = settingsDropdownAnchor(row);
            hit(x, y, w, h, () -> openDropdown(target, anchor));
        }
    }

    private Rectangle settingsDropdownAnchor(float x, float y, float w,
            float h) {
        GdxSettingsLayout.ChoiceRow row = GdxSettingsLayout.choiceRow(
                x, y, w, h);
        return settingsDropdownAnchor(row);
    }

    private Rectangle settingsDropdownAnchor(
            GdxSettingsLayout.ChoiceRow row) {
        return new Rectangle(row.value().x, row.bounds().y,
                row.arrow().x + row.arrow().width - row.value().x,
                row.bounds().height);
    }

    private void openDropdown(Dropdown target, Rectangle anchor) {
        openDropdown(target, anchor.x, anchor.y, anchor.width, anchor.height);
    }

    private void openAppearanceChoiceDropdown(
            GdxAppearanceOptions.Choice choice, Rectangle anchor) {
        dropdownAppearanceChoice = choice;
        openDropdown(Dropdown.APPEARANCE_CHOICE, anchor);
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
        settingsStepper(x, y, w, h, label, value, "", minus, plus,
                valueAction, enabled);
    }

    private void settingsStepperWithStatus(float x, float y, float w, float h,
            String label, String value, String status, Runnable minus,
            Runnable plus) {
        settingsStepper(x, y, w, h, label, value, status, minus, plus,
                null, true);
    }

    private void settingsAudioDeviceStepper(float x, float y, float w,
            float h, String label, String value, Runnable minus,
            Runnable plus, boolean enabled) {
        settingsStepper(x, y, w, h, label, value, "", minus, plus,
                null, enabled, true);
    }

    private void settingsStepper(float x, float y, float w, float h,
            String label, String value, String status, Runnable minus,
            Runnable plus, Runnable valueAction, boolean enabled) {
        settingsStepper(x, y, w, h, label, value, status, minus, plus,
                valueAction, enabled, false);
    }

    private void settingsStepper(float x, float y, float w, float h,
            String label, String value, String status, Runnable minus,
            Runnable plus, Runnable valueAction, boolean enabled,
            boolean audioDevice) {
        GdxSettingsLayout.StepperRow row = audioDevice
                ? GdxSettingsLayout.audioDeviceStepperRow(x, y, w, h)
                : GdxSettingsLayout.stepperRow(x, y, w, h);
        GdxSettingsStyle.drawRow(shapes, x, y, w, h, enabled,
                enabled && hovered(x, y, w, h),
                enabled && pressed(x, y, w, h), 1f);
        Rectangle minusBounds = row.minusButton();
        Rectangle valueBounds = row.value();
        Rectangle plusBounds = row.plusButton();
        embeddedButtonSurface(minusBounds.x, minusBounds.y,
                minusBounds.width, minusBounds.height, enabled);
        embeddedButtonSurface(plusBounds.x, plusBounds.y,
                plusBounds.width, plusBounds.height, enabled);
        boolean hasStatus = status != null && !status.isBlank();
        float labelWidth = hasStatus
                ? row.label().width * 0.38f : row.label().width;
        textFit(smallFont, uppercase(label), row.label().x,
                GdxSettingsLayout.stepperLabelBaseline(y),
                enabled ? Color.WHITE : DISABLED, false,
                labelWidth);
        boolean modOverrideValue = GdxModSettingsIndicator.marked(value)
                || GdxModSettingsIndicator.marked(status);
        Color valueColor = modOverrideValue
                ? enabled ? MOD_OVERRIDE : MOD_OVERRIDE_DISABLED
                : enabled ? GOLD : DISABLED;
        if (hasStatus) {
            float statusWidth = row.label().width - labelWidth - 18f;
            textFit(smallFont, status,
                    row.label().x + labelWidth + 18f + statusWidth / 2f,
                    GdxSettingsLayout.stepperLabelBaseline(y),
                    valueColor, true, statusWidth);
        }
        text(settingsStepperSignFont, "-",
                minusBounds.x + minusBounds.width / 2f,
                GdxSettingsLayout.stepperSignBaseline(y),
                enabled ? Color.WHITE : DISABLED, true);
        textFit(uiFont, value, valueBounds.x + valueBounds.width / 2f,
                GdxSettingsLayout.stepperValueBaseline(y),
                valueColor, true,
                valueBounds.width - 12f);
        text(settingsStepperSignFont, "+",
                plusBounds.x + plusBounds.width / 2f,
                GdxSettingsLayout.stepperSignBaseline(y),
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
        GdxSettingsStyle.drawRow(shapes, x, y, w, h,
                true, false, false, 1f);
        float valueWidth = GdxSettingsLayout.infoValueWidth(w);
        textFit(smallFont, uppercase(label), x + 20f,
                GdxSettingsLayout.infoLabelBaseline(y),
                Color.WHITE, false, w - valueWidth - 52f);
        textFitRight(uiFont, value,
                GdxSettingsLayout.infoValueRight(x, w),
                GdxSettingsLayout.infoValueBaseline(y), GOLD, valueWidth);
    }

    private void adjustLightLevel(int direction) {
        GdxAppearanceOptions.adjustLightLevel(initialProperties, direction);
    }

    private void drawLobbyGameSettings(float x, float y, float w, float h) {
        if (settingsTable == null) {
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
        boolean menuDefaults = settingsSession.context()
                == GdxSettingsSession.Context.MENU;
        boolean editable = menuDefaults || lobby != null && lobby.host();
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
            case 2 -> drawLobbyBotSettings(x, w, rowY, editable,
                    serverLockKey);
            default -> drawLobbyRuleSettings(x, w, rowY, editable,
                    serverLockKey);
        }
        if (!editable) {
            textFit(tinyFont, settingsGameText("host_only"),
                    x + w / 2f, y + 18f, MUTED, true, w - 68f);
        } else if (!menuDefaults && settingsTable.economyLocked()) {
            textFit(tinyFont, settingsGameText("economy_locked"),
                    x + w / 2f, y + 18f, GOLD, true, w - 68f);
        }
    }

    private void drawLobbyBlindSettings(float x, float w, float y,
            boolean enabled, String lockKey) {
        float rowX = x + GdxSettingsLayout.CONTENT_HORIZONTAL_INSET;
        float rowWidth = w - 2f * GdxSettingsLayout.CONTENT_HORIZONTAL_INSET;
        List<GdxSettingsContract.RowGroup> groups =
                GdxSettingsContract.gameGroups(0);
        GdxSettingsLayout.PixelRows rows = GdxSettingsLayout.pixelRows(
                y, settingsRowsClip.y, y + GdxSettingsLayout.ROW_HEIGHT,
                BLIND_ROW_COUNT, settingsGameScroll, groups);
        settingsGameScroll = rows.offset();
        GdxSettingsLayout.drawGroups(shapes, rowX, rowWidth, rows, groups, 1f);
        Rectangle row = GdxSettingsLayout.groupedRow(rowX, rows.rowY(0),
                rowWidth, 0, groups, BLIND_ROW_COUNT);
        settingsDropdownChoice(row.x, row.y, row.width,
                GdxSettingsLayout.ROW_HEIGHT,
                settingsGameText("row.structure"),
                settingsTable.structureName() == null
                        ? uppercase(gameText.translate(
                                "gdx.settings.value.default"))
                        : settingsTable.structureName(),
                Dropdown.BLIND_STRUCTURE, enabled);
        row = GdxSettingsLayout.groupedRow(rowX, rows.rowY(1), rowWidth, 1,
                groups, BLIND_ROW_COUNT);
        settingsDropdownChoice(row.x, row.y, row.width, row.height,
                settingsGameText("row.initial_blinds"),
                blindLevelLabel(settingsTable.blindLevel()),
                Dropdown.BLIND_LEVEL, enabled);
        row = GdxSettingsLayout.groupedRow(rowX, rows.rowY(2), rowWidth, 2,
                groups, BLIND_ROW_COUNT);
        button(row.x, row.y, row.width, GdxSettingsLayout.ROW_HEIGHT,
                settingsGameText("row.manage_structures"), false,
                this::openSettingsBlindStructureEditor, enabled);
        row = GdxSettingsLayout.groupedRow(rowX,
                rows.rowY(GdxSettingsContract.BLIND_INCREASE_ROW), rowWidth,
                GdxSettingsContract.BLIND_INCREASE_ROW, groups, BLIND_ROW_COUNT);
        settingsBooleanToggle(row.x, row.y, row.width,
                settingsGameText("row.increase_blinds"),
                settingsTable.increaseBlinds(), false, () -> settingsTable
                        .setIncreaseBlinds(!settingsTable.increaseBlinds()),
                enabled);
        row = GdxSettingsLayout.groupedRow(rowX,
                rows.rowY(GdxSettingsContract.BLIND_UNIT_ROW), rowWidth,
                GdxSettingsContract.BLIND_UNIT_ROW, groups, BLIND_ROW_COUNT);
        settingsDropdownChoice(row.x, row.y, row.width,
                GdxSettingsLayout.ROW_HEIGHT,
                settingsGameText("row.unit"), settingsBlindUnit(),
                Dropdown.BLIND_UNIT,
                enabled && settingsTable.increaseBlinds());
        row = GdxSettingsLayout.groupedRow(rowX,
                rows.rowY(GdxSettingsContract.BLIND_INTERVAL_ROW), rowWidth,
                GdxSettingsContract.BLIND_INTERVAL_ROW, groups, BLIND_ROW_COUNT);
        settingsStepper(row.x, row.y, row.width,
                GdxSettingsLayout.ROW_HEIGHT,
                settingsGameText("row.interval"),
                settingsBlindInterval(),
                () -> adjustSettingsBlindInterval(-1),
                () -> adjustSettingsBlindInterval(1),
                enabled && settingsTable.increaseBlinds());
        row = GdxSettingsLayout.groupedRow(rowX,
                rows.rowY(GdxSettingsContract.BLIND_CAP_TOGGLE_ROW), rowWidth,
                GdxSettingsContract.BLIND_CAP_TOGGLE_ROW, groups, BLIND_ROW_COUNT);
        settingsBooleanToggle(row.x, row.y, row.width,
                settingsGameText("row.blind_cap"),
                settingsTable.blindCap(), false, () -> settingsTable
                        .setBlindCap(!settingsTable.blindCap()),
                enabled && settingsTable.blindCapControlEnabled());
        row = GdxSettingsLayout.groupedRow(rowX,
                rows.rowY(GdxSettingsContract.BLIND_CAP_ROW), rowWidth,
                GdxSettingsContract.BLIND_CAP_ROW, groups, BLIND_ROW_COUNT);
        settingsStepper(row.x, row.y, row.width,
                GdxSettingsLayout.ROW_HEIGHT,
                settingsGameText("row.cap"), settingsBlindCap(),
                () -> adjustSettingsBlindCap(-1),
                () -> adjustSettingsBlindCap(1),
                enabled && settingsTable.blindCapRaisesEnabled());
        row = GdxSettingsLayout.groupedRow(rowX,
                rows.rowY(GdxSettingsContract.BLIND_ANTE_ROW), rowWidth,
                GdxSettingsContract.BLIND_ANTE_ROW, groups, BLIND_ROW_COUNT);
        settingsBooleanToggle(row.x, row.y, row.width,
                settingsGameText("row.ante"), settingsTable.ante(), false,
                () -> settingsTable.setAnte(!settingsTable.ante()), enabled);
        int anteModeIndex = GdxSettingsContract.BLIND_ANTE_MODE_ROW;
        row = GdxSettingsLayout.groupedRow(rowX, rows.rowY(anteModeIndex),
                rowWidth, anteModeIndex, groups, BLIND_ROW_COUNT);
        settingsDropdownChoice(row.x, row.y, row.width, row.height,
                settingsGameText("row.ante_mode"),
                gameText.translate(settingsTable.anteMode().labelKey()),
                Dropdown.ANTE_MODE, enabled && settingsTable.ante());
        row = GdxSettingsLayout.groupedRow(rowX,
                rows.rowY(GdxSettingsContract.BLIND_STRADDLE_ROW), rowWidth,
                GdxSettingsContract.BLIND_STRADDLE_ROW, groups, BLIND_ROW_COUNT);
        settingsBooleanToggle(row.x, row.y, row.width,
                settingsGameText("row.straddle"), settingsTable.straddle(),
                false, () -> settingsTable.setStraddle(
                        !settingsTable.straddle()), enabled);
        int timeoutIndex = GdxSettingsContract.BLIND_STRADDLE_TIMEOUT_ROW;
        Rectangle timeoutRow = GdxSettingsLayout.groupedRow(rowX,
                rows.rowY(timeoutIndex), rowWidth, timeoutIndex, groups,
                BLIND_ROW_COUNT);
        settingsBooleanToggle(timeoutRow.x, timeoutRow.y, timeoutRow.width,
                settingsGameText("row.straddle_on_timeout"),
                settingsStraddleTimeoutDraft, false,
                () -> settingsStraddleTimeoutDraft =
                        !settingsStraddleTimeoutDraft, settingsTable.straddle());
        addGameSettingsTooltips(rowX, rowWidth, rows, 0,
                index -> !enabled && index != timeoutIndex ? lockKey : null);
        drawSettingsRowScrollbar(GdxSettingsLayout.rowScrollbarX(x, w), rows);
    }

    private void drawLobbyPurchaseSettings(float x, float w, float y,
            boolean editable, boolean economyEditable, String serverLockKey,
            String economyLockKey) {
        float rowX = x + GdxSettingsLayout.CONTENT_HORIZONTAL_INSET;
        float rowWidth = w - 2f * GdxSettingsLayout.CONTENT_HORIZONTAL_INSET;
        List<GdxSettingsContract.RowGroup> groups =
                GdxSettingsContract.gameGroups(1);
        GdxSettingsLayout.PixelRows rows = GdxSettingsLayout.pixelRows(
                y, settingsRowsClip.y, y + GdxSettingsLayout.ROW_HEIGHT,
                8, settingsGameScroll, groups);
        settingsGameScroll = rows.offset();
        GdxSettingsLayout.drawGroups(shapes, rowX, rowWidth, rows, groups, 1f);
        Rectangle row = GdxSettingsLayout.groupedRow(rowX,
                rows.rowY(GdxSettingsContract.BUYIN_MINIMUM_ROW), rowWidth,
                GdxSettingsContract.BUYIN_MINIMUM_ROW, groups, 8);
        settingsStepper(row.x, row.y, row.width,
                settingsGameText("row.minimum_range_bb"),
                settingsTable.minBuyinBb(), () -> settingsTable
                        .setMinBuyinBb(settingsTable.minBuyinBb() - 5),
                () -> settingsTable.setMinBuyinBb(
                        settingsTable.minBuyinBb() + 5), economyEditable);
        row = GdxSettingsLayout.groupedRow(rowX,
                rows.rowY(GdxSettingsContract.BUYIN_MAXIMUM_ROW), rowWidth,
                GdxSettingsContract.BUYIN_MAXIMUM_ROW, groups, 8);
        settingsStepper(row.x, row.y, row.width,
                settingsGameText("row.maximum_range_bb"),
                settingsTable.maxBuyinBb(), () -> settingsTable
                        .setMaxBuyinBb(settingsTable.maxBuyinBb() - 5),
                () -> settingsTable.setMaxBuyinBb(
                        settingsTable.maxBuyinBb() + 5), economyEditable);
        row = GdxSettingsLayout.groupedRow(rowX,
                rows.rowY(GdxSettingsContract.BUYIN_FIXED_ROW), rowWidth,
                GdxSettingsContract.BUYIN_FIXED_ROW, groups, 8);
        settingsBooleanToggle(row.x, row.y, row.width,
                settingsGameText("row.fixed_buyin"),
                settingsTable.fixedBuyin(), true, () -> settingsTable
                        .setFixedBuyin(!settingsTable.fixedBuyin()),
                economyEditable);
        row = GdxSettingsLayout.groupedRow(rowX,
                rows.rowY(GdxSettingsContract.BUYIN_INITIAL_ROW), rowWidth,
                GdxSettingsContract.BUYIN_INITIAL_ROW, groups, 8);
        initialBuyinChoice(row.x, row.y, row.width, row.height,
                settingsTable, economyEditable);
        row = GdxSettingsLayout.groupedRow(rowX,
                rows.rowY(GdxSettingsContract.BUYIN_REBUY_ROW), rowWidth,
                GdxSettingsContract.BUYIN_REBUY_ROW, groups, 8);
        settingsBooleanToggle(row.x, row.y, row.width,
                settingsGameText("row.rebuy"), settingsTable.rebuy(), true,
                () -> settingsTable.setRebuy(!settingsTable.rebuy()),
                editable);
        row = GdxSettingsLayout.groupedRow(rowX,
                rows.rowY(GdxSettingsContract.BUYIN_PLAYER_LIMIT_ROW),
                rowWidth, GdxSettingsContract.BUYIN_PLAYER_LIMIT_ROW,
                groups, 8);
        settingsBooleanToggle(row.x, row.y, row.width,
                settingsGameText("row.player_limit"),
                settingsTable.rebuyLimit(), false, () -> settingsTable
                        .setRebuyLimit(!settingsTable.rebuyLimit()),
                editable && settingsTable.rebuy());
        row = GdxSettingsLayout.groupedRow(rowX,
                rows.rowY(GdxSettingsContract.BUYIN_MAXIMUM_REBUYS_ROW),
                rowWidth, GdxSettingsContract.BUYIN_MAXIMUM_REBUYS_ROW,
                groups, 8);
        settingsStepper(row.x, row.y, row.width,
                settingsGameText("row.maximum_rebuys"),
                settingsTable.rebuyLimitCount(),
                () -> settingsTable.setRebuyLimitCount(
                        settingsTable.rebuyLimitCount() - 1),
                () -> settingsTable.setRebuyLimitCount(
                        settingsTable.rebuyLimitCount() + 1),
                editable && settingsTable.rebuyLimitCountEnabled());
        row = GdxSettingsLayout.groupedRow(rowX,
                rows.rowY(GdxSettingsContract.BUYIN_REBUY_CAP_ROW), rowWidth,
                GdxSettingsContract.BUYIN_REBUY_CAP_ROW, groups, 8);
        settingsDropdownChoice(row.x, row.y, row.width,
                GdxSettingsLayout.ROW_HEIGHT,
                settingsGameText("row.rebuy_cap"),
                settingsTable.rebuyCapPolicy()
                        == NewGameTableDraft.RebuyCapPolicy.BUY_IN
                                ? "BUY-IN"
                                : settingsGameText("value.highest_stack"),
                Dropdown.REBUY_CAP,
                editable && settingsTable.rebuy());
        String purchaseLock = economyEditable ? null : economyLockKey;
        String rebuyLock = editable ? null : serverLockKey;
        addGameSettingsTooltips(rowX, rowWidth, rows, 1,
                index -> index < GdxSettingsContract.BUYIN_REBUY_ROW
                        ? purchaseLock : rebuyLock);
        drawSettingsRowScrollbar(GdxSettingsLayout.rowScrollbarX(x, w), rows);
    }

    private void drawLobbyBotSettings(float x, float w, float y,
            boolean editable, String lockKey) {
        float rowX = x + GdxSettingsLayout.CONTENT_HORIZONTAL_INSET;
        float rowWidth = w - 2f * GdxSettingsLayout.CONTENT_HORIZONTAL_INSET;
        java.util.List<GdxSettingsContract.RowGroup> groups =
                GdxSettingsContract.gameGroups(2);
        GdxSettingsLayout.PixelRows rows = GdxSettingsLayout.pixelRows(
                y, settingsRowsClip.y, y + GdxSettingsLayout.ROW_HEIGHT,
                6, settingsGameScroll, groups);
        settingsGameScroll = rows.offset();
        GdxSettingsLayout.drawGroups(shapes, rowX, rowWidth, rows, groups,
                1f);
        if (rows.firstIndex() <= 0 && rows.lastExclusive() > 0) {
            Rectangle bounds = GdxSettingsLayout.groupedRow(rowX,
                    rows.rowY(0), rowWidth, 0, groups, 6);
            settingsDropdownChoice(bounds.x, bounds.y, bounds.width,
                    GdxSettingsLayout.ROW_HEIGHT,
                    settingsGameText("row.bot_difficulty"),
                    settingsBotDifficulty(), Dropdown.BOT_DIFFICULTY,
                    editable);
        }
        if (rows.firstIndex() <= 1 && rows.lastExclusive() > 1) {
            Rectangle bounds = GdxSettingsLayout.groupedRow(rowX,
                    rows.rowY(1), rowWidth, 1, groups, 6);
            settingsBooleanToggle(bounds.x, bounds.y, bounds.width,
                    settingsGameText("row.bot_rebuy"),
                    settingsTable.botRebuy(), true, () -> settingsTable
                            .setBotRebuy(!settingsTable.botRebuy()),
                    editable && settingsTable.botRebuyEnabled());
        }
        if (rows.firstIndex() <= 2 && rows.lastExclusive() > 2) {
            Rectangle bounds = GdxSettingsLayout.groupedRow(rowX,
                    rows.rowY(2), rowWidth, 2, groups, 6);
            settingsBooleanToggle(bounds.x, bounds.y, bounds.width,
                    gameText.translate("gdx.custom_bots.use"),
                    settingsTable.customBots(), false, () -> settingsTable
                            .setCustomBots(!settingsTable.customBots()),
                    editable);
        }
        if (rows.firstIndex() <= 3 && rows.lastExclusive() > 3) {
            Rectangle bounds = GdxSettingsLayout.groupedRow(rowX,
                    rows.rowY(3), rowWidth, 3, groups, 6);
            settingsBooleanToggle(bounds.x, bounds.y, bounds.width,
                    gameText.translate("gdx.custom_bots.only"),
                    settingsTable.customBots()
                            && settingsTable.customBotsOnly(),
                    false, () -> settingsTable.setCustomBotsOnly(
                            !settingsTable.customBotsOnly()),
                    editable && settingsTable.customBots());
        }
        if (rows.firstIndex() <= 4 && rows.lastExclusive() > 4) {
            Rectangle bounds = GdxSettingsLayout.groupedRow(rowX,
                    rows.rowY(4), rowWidth, 4, groups, 6);
            button(bounds.x, bounds.y, bounds.width,
                    GdxSettingsLayout.ROW_HEIGHT,
                    uppercase(gameText.translate(
                            "gdx.custom_bots.manage")),
                    false, this::openCustomBotManager, settingsTable.customBots());
        }
        if (rows.firstIndex() <= 5 && rows.lastExclusive() > 5) {
            settingsBooleanToggle(rowX, rows.rowY(5), rowWidth,
                    settingsGameText("row.bot_balance"),
                    settingsTable.botBalanceToHumans(), false,
                    () -> settingsTable.setBotBalanceToHumans(
                            !settingsTable.botBalanceToHumans()), editable);
        }
        addGameSettingsTooltips(rowX, rowWidth, rows, 2,
                index -> !editable && index != 4 ? lockKey : null);
        drawSettingsRowScrollbar(GdxSettingsLayout.rowScrollbarX(x, w), rows);
    }

    private void drawLobbyRuleSettings(float x, float w, float y,
            boolean editable, String lockKey) {
        float rowX = x + GdxSettingsLayout.CONTENT_HORIZONTAL_INSET;
        float rowWidth = w - 2f * GdxSettingsLayout.CONTENT_HORIZONTAL_INSET;
        java.util.List<GdxSettingsContract.RowGroup> groups =
                GdxSettingsContract.gameGroups(3);
        GdxSettingsLayout.PixelRows rows = GdxSettingsLayout.pixelRows(
                y, settingsRowsClip.y, y + GdxSettingsLayout.ROW_HEIGHT,
                9, settingsGameScroll, groups);
        settingsGameScroll = rows.offset();
        GdxSettingsLayout.drawGroups(shapes, rowX, rowWidth, rows, groups,
                1f);
        if (rows.firstIndex() <= 0 && rows.lastExclusive() > 0) {
            Rectangle bounds = GdxSettingsLayout.groupedRow(rowX,
                    rows.rowY(0), rowWidth, 0, groups, 9);
            settingsBooleanToggle(bounds.x, bounds.y, bounds.width,
                    settingsGameText("row.hand_limit"),
                    settingsTable.handLimit(), false, () -> settingsTable
                            .setHandLimit(!settingsTable.handLimit()), editable);
        }
        if (rows.firstIndex() <= 1 && rows.lastExclusive() > 1) {
            Rectangle bounds = GdxSettingsLayout.groupedRow(rowX,
                    rows.rowY(1), rowWidth, 1, groups, 9);
            settingsStepper(bounds.x, bounds.y, bounds.width,
                    settingsGameText("row.hand_count"),
                    settingsTable.handLimitCount(), () -> settingsTable
                            .setHandLimitCount(
                                    settingsTable.handLimitCount() - 1),
                    () -> settingsTable.setHandLimitCount(
                            settingsTable.handLimitCount() + 1),
                    editable && settingsTable.handLimit());
        }
        if (rows.firstIndex() <= 2 && rows.lastExclusive() > 2) {
            Rectangle bounds = GdxSettingsLayout.groupedRow(rowX,
                    rows.rowY(2), rowWidth, 2, groups, 9);
            settingsBooleanToggle(bounds.x, bounds.y, bounds.width,
                    settingsGameText("row.think_time"),
                    settingsTable.thinkTime(), true, () -> settingsTable
                            .setThinkTime(!settingsTable.thinkTime()), editable);
        }
        if (rows.firstIndex() <= 3 && rows.lastExclusive() > 3) {
            Rectangle bounds = GdxSettingsLayout.groupedRow(rowX,
                    rows.rowY(3), rowWidth, 3, groups, 9);
            settingsStepper(bounds.x, bounds.y, bounds.width,
                    settingsGameText("row.think_seconds"),
                    settingsTable.thinkSeconds(),
                    () -> settingsTable.setThinkSeconds(
                            settingsTable.thinkSeconds() - 5),
                    () -> settingsTable.setThinkSeconds(
                            settingsTable.thinkSeconds() + 5),
                    editable && settingsTable.thinkTime());
        }
        if (rows.firstIndex() <= 4 && rows.lastExclusive() > 4) {
            settingsStepper(rowX, rows.rowY(4), rowWidth,
                    settingsGameText("row.showdown_seconds"),
                    settingsTable.showdownSeconds(),
                    () -> settingsTable.setShowdownSeconds(
                            settingsTable.showdownSeconds() - 5),
                    () -> settingsTable.setShowdownSeconds(
                            settingsTable.showdownSeconds() + 5), editable);
        }
        if (rows.firstIndex() <= 5 && rows.lastExclusive() > 5) {
            settingsBooleanToggle(rowX, rows.rowY(5), rowWidth,
                    settingsGameText("row.allow_hot_join"),
                    settingsTable.allowHotJoin(), true, () -> settingsTable
                            .setAllowHotJoin(!settingsTable.allowHotJoin()),
                    editable);
        }
        if (rows.firstIndex() <= 6 && rows.lastExclusive() > 6) {
            settingsBooleanToggle(rowX, rows.rowY(6), rowWidth, "IWTSTH",
                    settingsTable.iwtsth(), false, () -> settingsTable
                            .setIwtsth(!settingsTable.iwtsth()), editable);
        }
        if (rows.firstIndex() <= 7 && rows.lastExclusive() > 7) {
            settingsBooleanToggle(rowX, rows.rowY(7), rowWidth,
                    "RUN IT TWICE", settingsTable.runItTwice(), false,
                    () -> settingsTable
                            .setRunItTwice(!settingsTable.runItTwice()),
                    editable);
        }
        if (rows.firstIndex() <= 8 && rows.lastExclusive() > 8) {
            settingsDropdownChoice(rowX, rows.rowY(8), rowWidth,
                    GdxSettingsLayout.ROW_HEIGHT,
                    settingsGameText("row.rabbit_hunting"),
                    settingsRabbitText(), Dropdown.RABBIT, editable);
        }
        addGameSettingsTooltips(rowX, rowWidth, rows, 3,
                index -> editable ? null : lockKey);
        drawSettingsRowScrollbar(GdxSettingsLayout.rowScrollbarX(x, w), rows);
    }

    private void addGameSettingsTooltips(float x, float width,
            GdxSettingsLayout.PixelRows rows, int gamePage,
            java.util.function.IntFunction<String> lockForRow) {
        List<String> keys = GdxSettingsTooltips.gameRowKeys(gamePage);
        List<GdxSettingsContract.RowGroup> groups =
                GdxSettingsContract.gameGroups(gamePage);
        for (int row = rows.firstIndex(); row < rows.lastExclusive(); row++) {
            Rectangle bounds = GdxSettingsLayout.groupedRow(x, rows.rowY(row),
                    width, row, groups, keys.size());
            Rectangle clipped = clippedSettingsHit(bounds.x, bounds.y,
                    bounds.width, bounds.height);
            if (clipped == null) continue;
            String description = gameText.translate(
                    GdxSettingsTooltips.descriptionKey(keys.get(row)));
            tooltip(clipped.x, clipped.y, clipped.width, clipped.height,
                    "gdx.settings.tooltip.text", GdxSettingsTooltips.withLock(
                            description, lockForRow.apply(row), gameText));
        }
    }

    private void settingsStepper(float x, float y, float w, String label,
            int value, Runnable minus, Runnable plus, boolean enabled) {
        settingsStepper(x, y, w, GdxSettingsLayout.ROW_HEIGHT,
                label, Integer.toString(value), minus, plus, enabled);
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
        int totalRows = GdxSettingsContract.CONTROL_ROWS + entries.size();
        List<GdxSettingsContract.RowGroup> groups =
                GdxSettingsContract.controlGroups();
        GdxSettingsLayout.PixelRows rows = GdxSettingsLayout.pixelRows(
                firstY, y + GdxSettingsLayout.CONTENT_VERTICAL_INSET,
                firstY + GdxSettingsLayout.ROW_HEIGHT,
                totalRows, settingsShortcutScroll, groups);
        settingsShortcutScroll = rows.offset();
        GdxSettingsLayout.drawGroups(shapes, x + 34f, w - 68f, rows,
                groups, 1f);
        for (int row = rows.firstIndex(); row < rows.lastExclusive(); row++) {
            float rowY = rows.rowY(row);
            float rowX = x + 34f;
            float rowW = w - 68f;
            if (row < GdxSettingsContract.CONTROL_ROWS) {
                Rectangle bounds = GdxSettingsLayout.groupedRow(rowX, rowY,
                        rowW, row, groups, totalRows);
                drawFrontendControlSetting(row, bounds.x, bounds.y,
                        bounds.width);
                settingsTooltip(bounds,
                        GdxSettingsContract.controlSettingKey(row));
                continue;
            }
            GdxShortcutBindings.ShortcutEntry entry = entries.get(
                    row - GdxSettingsContract.CONTROL_ROWS);
            boolean capturing = entry.id().equals(settingsShortcutCaptureId);
            GdxSettingsLayout.ShortcutRow shortcut =
                    GdxSettingsLayout.shortcutRow(rowX, rowY, rowW);
            shortcutRow(shortcut,
                    capturing ? uppercase(gameText.translate(
                            "gdx.settings.shortcut.press_key"))
                            : entry.display(),
                    uppercase(entry.markedDescription()), capturing);
            Rectangle hitBounds = shortcut.bounds();
            settingsTooltip(hitBounds, "shortcut");
            hit(hitBounds.x, hitBounds.y, hitBounds.width, hitBounds.height,
                    () -> settingsShortcutCaptureId = entry.id());
        }
        drawSettingsRowScrollbar(GdxSettingsLayout.rowScrollbarX(x, w), rows);
    }

    private void drawFrontendControlSetting(int row, float x, float y,
            float width) {
        switch (row) {
            case 0 -> settingsBooleanToggle(x, y, width,
                    settingsGameText("row.auto_buttons"),
                    preferenceBoolean("auto_action_buttons", false), false,
                    () -> togglePreference("auto_action_buttons", false),
                    true);
            case 1 -> settingsChoice(x, y, width,
                    GdxSettingsLayout.ROW_HEIGHT,
                    settingsGameText("row.auto_call"),
                    frontendAutoCallValue(), this::openSettingsAutoCallDialog,
                    preferenceBoolean("auto_action_buttons", false));
            case 2 -> settingsBooleanToggle(x, y, width,
                    settingsGameText("row.persist_auto"),
                    preferenceBoolean("auto_action_persist", true), true,
                    () -> togglePreference("auto_action_persist", true),
                    preferenceBoolean("auto_action_buttons", false));
            case 3 -> settingsBooleanToggle(x, y, width,
                    settingsGameText("row.confirm_auto"),
                    preferenceBoolean("modo_auto_confirm", true), true,
                    () -> togglePreference("modo_auto_confirm", true),
                    preferenceBoolean("auto_action_buttons", false));
            case 4 -> settingsBooleanToggle(x, y, width,
                    settingsGameText("row.confirm_actions"),
                    preferenceBoolean("confirmar_todo", false), false,
                    () -> togglePreference("confirmar_todo", false), true);
            case 5 -> settingsBooleanToggle(x, y, width,
                    settingsGameText("row.auto_rebuy"),
                    presentationSettings.autoRebuyOnBroke(), false, () ->
                            presentationSettings.setAutoRebuyOnBroke(
                                    !presentationSettings.autoRebuyOnBroke()),
                    settingsTable != null && settingsTable.rebuy());
            default -> { }
        }
    }

    private String frontendAutoCallValue() {
        boolean enabled = preferenceBoolean("auto_call_enabled", false);
        double maximum = preferenceDouble("auto_call_max", 0d);
        return GdxSettingsContract.autoCallStatusLabel(enabled, maximum,
                formatBlind(maximum), gameText);
    }

    private boolean frontendControlSettingsAtDefaults() {
        return !preferenceBoolean("auto_action_buttons", false)
                && preferenceBoolean("auto_action_persist", true)
                && preferenceBoolean("modo_auto_confirm", true)
                && !preferenceBoolean("confirmar_todo", false)
                && !preferenceBoolean("auto_call_enabled", false)
                && Math.abs(preferenceDouble("auto_call_max", 0d)) < 0.0001d
                && !presentationSettings.autoRebuyOnBroke();
    }

    private void restoreFrontendControlDefaults() {
        initialProperties.setProperty("auto_action_buttons", "false");
        initialProperties.setProperty("auto_action_persist", "true");
        initialProperties.setProperty("modo_auto_confirm", "true");
        initialProperties.setProperty("confirmar_todo", "false");
        initialProperties.setProperty("auto_call_enabled", "false");
        initialProperties.setProperty("auto_call_max", "0");
        presentationSettings.setAutoRebuyOnBroke(false);
    }

    private void openSettingsAutoCallDialog() {
        settingsAutoCallDialog = GdxTableDialog.autoCall(
                preferenceBoolean("auto_call_enabled", false),
                preferenceDouble("auto_call_max", 0d), gameText);
        clearActiveField();
    }

    private void closeSettingsAutoCallDialog(boolean save) {
        GdxTableDialog dialog = settingsAutoCallDialog;
        if (dialog == null) return;
        if (save) {
            initialProperties.setProperty("auto_call_enabled",
                    Boolean.toString(dialog.optionEnabled()));
            initialProperties.setProperty("auto_call_max",
                    Double.toString(dialog.autoCallAmount()));
        }
        settingsAutoCallDialog = null;
    }

    private void drawSettingsAutoCallDialog() {
        GdxTableDialog dialog = settingsAutoCallDialog;
        if (dialog == null) return;
        hits.clear();
        secondaryHits.clear();
        float panelW = 1040f;
        float panelH = 580f;
        float panelX = (WIDTH - panelW) / 2f;
        float panelY = (HEIGHT - panelH) / 2f;
        GdxUiDialogStyle.drawPanel(shapes, panelX, panelY, panelW, panelH,
                GOLD, 1f);
        GdxTableDialogLayout.AutoCall layout =
                GdxTableDialogLayout.autoCall(panelX, panelY, panelW);
        Rectangle title = GdxTableDialogLayout.title(panelX, panelY,
                panelW, panelH);
        textFit(headingFont, uppercase(dialog.title()), title.x,
                title.y + 33f, GOLD, false, title.width);
        textFit(smallFont, dialog.message(), layout.detail().x
                + layout.detail().width / 2f, layout.detail().y + 30f,
                Color.WHITE, true, layout.detail().width);
        settingsBooleanToggle(layout.enabled().x, layout.enabled().y,
                layout.enabled().width,
                settingsGameText("row.auto_call"),
                dialog.optionEnabled(), false,
                dialog::toggleOptionEnabled, true);
        settingsBooleanToggle(layout.noLimit().x, layout.noLimit().y,
                layout.noLimit().width,
                uppercase(gameText.translate("auto_call.sin_limite")),
                dialog.noLimit(), true, dialog::toggleNoLimit,
                dialog.optionEnabled());
        GdxSettingsLayout.StepperRow amount = layout.amount();
        settingsStepper(amount.bounds().x, amount.bounds().y,
                amount.bounds().width, amount.bounds().height,
                uppercase(settingsGameText("value.maximum")),
                GdxChipAmountText.compactLabel(dialog.amountText()), () -> dialog.changeAmount(-1),
                () -> dialog.changeAmount(1),
                dialog.optionEnabled() && !dialog.noLimit());
        float buttonY = panelY + 34f;
        themedButton(panelX + 42f, buttonY, 230f, 64f,
                uppercase(gameText.translate("ui.cancelar")),
                ButtonTone.NEUTRAL,
                () -> closeSettingsAutoCallDialog(false), true);
        themedButton(panelX + panelW - 272f, buttonY, 230f, 64f,
                uppercase(gameText.translate("ui.aceptar")),
                ButtonTone.FEATURED,
                () -> closeSettingsAutoCallDialog(true), true);
    }

    private void drawDebugSettings(float x, float y, float w, float h) {
        Rectangle content = new Rectangle(x, y, w, h);
        Rectangle performance = GdxSettingsLayout.debugPerformanceRow(
                content);
        settingsBooleanToggle(performance.x, performance.y,
                performance.width,
                uppercase(gameText.translate(
                        "gdx.settings.option.gdx_show_cpu_memory")),
                preferenceBoolean("gdx_show_cpu_memory", false), false,
                () -> togglePreference("gdx_show_cpu_memory", false), true);
        settingsTooltip(performance, "gdx_show_cpu_memory");
        Rectangle copy = GdxSettingsLayout.debugCopyButton(content);
        GdxSettingsLayout.DebugArea debug =
                GdxSettingsLayout.debugArea(content);
        Rectangle console = debug.console();
        Rectangle viewportBounds = debug.viewport();
        GdxSettingsStyle.drawDebugConsole(shapes, console, 1f);
        compactButton(copy.x, copy.y, copy.width, copy.height,
                uppercase(gameText.translate("gdx.settings.debug.copy")),
                false, this::copySettingsDebugLog);

        List<GdxDebugLogFormatter.Line> lines = settingsDebugVisualLines(
                GdxSettingsLayout.debugTextWidth(console));
        float viewportHeight = viewportBounds.height;
        float maximum = Math.max(0f, lines.size()
                * GdxSettingsLayout.DEBUG_LINE_HEIGHT - viewportHeight);
        settingsDebugScroll = CoronaPokerGdxTable
                .preservePixelScrollOnAppend(settingsDebugScroll,
                        settingsDebugLineCount, lines.size(),
                        GdxSettingsLayout.DEBUG_LINE_HEIGHT);
        settingsDebugLineCount = lines.size();
        settingsDebugScroll = MathUtils.clamp(settingsDebugScroll, 0, maximum);
        for (int i = 0; i < lines.size(); i++) {
            float lineY = CoronaPokerGdxTable.anchoredPixelRowY(lines.size(),
                    i, GdxSettingsLayout.DEBUG_LINE_HEIGHT,
                    viewportBounds.y, viewportHeight,
                    settingsDebugScroll, maximum)
                    + GdxSettingsLayout.DEBUG_TEXT_BASELINE;
            if (lineY < viewportBounds.y
                    || lineY > viewportBounds.y + viewportHeight
                    + GdxSettingsLayout.DEBUG_LINE_HEIGHT) {
                continue;
            }
            float runX = console.x + GdxSettingsLayout.DEBUG_TEXT_LEFT_INSET;
            for (GdxDebugLogFormatter.Run run : lines.get(i).runs()) {
                BitmapFont font = run.bold()
                        ? debugLogBoldFont : debugLogFont;
                settingsDebugTexts.add(new TextItem(font, run.text(),
                        runX, lineY, new Color(run.foreground()), false));
                runX += textWidth(font, run.text());
            }
        }
        settingsDebugViewport.set(viewportBounds);

        float trackX = debug.scrollbarX();
        settingsDebugScrollTrack.set(trackX, viewportBounds.y,
                GdxSettingsLayout.SCROLLBAR_WIDTH, viewportHeight);
        settingsDebugScrollMaximum = maximum;
        float thumbH = maximum == 0 ? viewportHeight
                : Math.max(32f, viewportHeight
                        * viewportHeight
                        / Math.max(1f, lines.size()
                                * GdxSettingsLayout.DEBUG_LINE_HEIGHT));
        settingsDebugScrollThumbHeight = thumbH;
        float travel = Math.max(0f, viewportHeight - thumbH);
        float ratio = maximum == 0 ? 0f
                : settingsDebugScroll / maximum;
        GdxSettingsStyle.drawDebugScrollbar(shapes, trackX,
                viewportBounds.y, viewportHeight,
                viewportBounds.y + travel * ratio, thumbH, 1f);
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
                    run -> textWidth(run.bold()
                            ? debugLogBoldFont : debugLogFont, run.text()));
        }
        return settingsDebugVisualCache;
    }

    private void copySettingsDebugLog() {
        Gdx.app.getClipboard().setContents(DebugLog.snapshot());
        showToast(gameText.translate("gdx.settings.debug.copied"));
    }

    private void shortcutRow(GdxSettingsLayout.ShortcutRow row, String key,
            String description, boolean capturing) {
        Rectangle bounds = row.bounds();
        Rectangle keyBounds = row.key();
        Rectangle descriptionBounds = row.description();
        GdxSettingsStyle.drawShortcutRow(shapes, row, capturing, 1f);
        textFit(smallFont, key, keyBounds.x + keyBounds.width / 2f,
                GdxSettingsLayout.shortcutKeyBaseline(row),
                GdxSettingsStyle.shortcutKeyText(capturing), true,
                keyBounds.width - 22f);
        textFit(smallFont, description, descriptionBounds.x,
                GdxSettingsLayout.shortcutDescriptionBaseline(row),
                GdxSettingsStyle.shortcutDescriptionText(), false,
                descriptionBounds.width);
    }

    private boolean preferenceBoolean(String key, boolean fallback) {
        return Boolean.parseBoolean(initialProperties.getProperty(key,
                Boolean.toString(fallback)));
    }

    private double preferenceDouble(String key, double fallback) {
        try {
            return Double.parseDouble(initialProperties.getProperty(key,
                    Double.toString(fallback)));
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }

    private float masterVolume() {
        return GdxSettingsContract.masterVolume(initialProperties);
    }

    private void togglePreference(String key, boolean fallback) {
        boolean next = !preferenceBoolean(key, fallback);
        initialProperties.setProperty(key, Boolean.toString(next));
        GdxVisualEffects.synchronize(initialProperties);
        presentationSettings.refreshRuntimePreferences();
        if ("musica".equals(key) || "sonido_ascensor".equals(key)
                || "musica_sala_espera".equals(key)
                || "musica_about".equals(key)
                || "musica_stats".equals(key)
                || "musica_gestor_bots".equals(key)) {
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
                    this::loadModAwareSound);
            playFrontendSound(sound, volume);
        } catch (RuntimeException failure) {
            failedPreferenceSoundCues.add(resource);
            LOGGER.log(Level.WARNING,
                    "GDX optional sound could not be loaded: " + resource,
                    failure);
        }
    }

    private Sound loadModAwareSound(String resource) {
        FileHandle resolved = GdxGameAudioResources.resolve(
                presentationSettings, resource);
        if (resolved == null) {
            throw new IllegalArgumentException(
                    "Missing audio resource: " + resource);
        }
        return Gdx.audio.newSound(resolved);
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

    private void selectDeckOption(int option) {
        List<String> decks = presentationSettings.availableDecks();
        if (option < 0 || option >= decks.size()) return;
        String previous = presentationSettings.deck();
        String selected = presentationSettings.selectDeck(
                decks.get(option), false);
        dropdown = Dropdown.NONE;
        if (Objects.equals(previous, selected)) return;
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

    private void selectFeltOption(int option) {
        if (option < 0 || option >= GdxGamePresentationSettings.FELTS.size()) {
            return;
        }
        String previous = presentationSettings.felt();
        String selected = presentationSettings.selectFelt(
                GdxGamePresentationSettings.FELTS.get(option), false);
        dropdown = Dropdown.NONE;
        if (Objects.equals(previous, selected)) return;
        refreshFeltFromSettings();
        playPreferenceSound("misc/mat.wav", "sonido_tapete", 0.92f);
    }

    private String msaaSettingLabel() {
        return GdxSettingsContract.msaaStatusLabel(
                presentationSettings.requestedMsaaSamples(),
                presentationSettings.actualMsaaSamples(), gameText);
    }

    private String msaaRestartStatus() {
        return GdxSettingsContract.msaaRestartStatus(
                presentationSettings.requestedMsaaSamples(),
                presentationSettings.actualMsaaSamples(), gameText);
    }

    private String cardRenderingSettingLabel() {
        return GdxSettingsContract.cardRenderingStatusLabel(
                presentationSettings.requestedCardRenderingMode(), gameText);
    }

    private String frameRateLimitSettingLabel() {
        return GdxSettingsContract.frameRateLimitLabel(
                presentationSettings.requestedFrameRateLimit(), gameText);
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

    private void drawFooterSoundControl() {
        boolean outputAvailable = audioOutputAvailable();
        drawFooterSoundSurface(outputAvailable, hovered(footerSoundHit.x,
                footerSoundHit.y, footerSoundHit.width, footerSoundHit.height),
                pressed(footerSoundHit.x, footerSoundHit.y,
                        footerSoundHit.width, footerSoundHit.height));
        uiImages.add(footerSoundImage(outputAvailable));
        hit(footerSoundHit.x, footerSoundHit.y, footerSoundHit.width,
                footerSoundHit.height, this::toggleMasterSound);
        addFooterSoundTooltip(outputAvailable);
    }

    private void drawFooterSoundSurface(boolean outputAvailable, boolean hover,
            boolean pressed) {
        GdxUiButtonStyle.draw(shapes, footerSound.x - 4f, footerSound.y - 4f,
                footerSound.width + 8f, footerSound.height + 8f,
                GdxUiButtonStyle.Tone.NEUTRAL, outputAvailable,
                outputAvailable && hover ? 1f : 0f,
                outputAvailable && hover && pressed, 1f);
    }

    private UiImageItem footerSoundImage(boolean outputAvailable) {
        return new UiImageItem(outputAvailable && audioControl.enabled()
                ? soundIcon : muteIcon, footerSound.x, footerSound.y,
                footerSound.width, footerSound.height,
                outputAvailable ? Color.WHITE : AUDIO_UNAVAILABLE_RED);
    }

    private void addFooterSoundTooltip(boolean outputAvailable) {
        if (outputAvailable) {
            tooltip(footerSoundHit.x, footerSoundHit.y, footerSoundHit.width,
                    footerSoundHit.height, "tooltip.sound_control",
                    shortcutBindings.displayFor(GdxShortcutBindings.VOLUME_UP),
                    shortcutBindings.displayFor(GdxShortcutBindings.VOLUME_DOWN));
        } else {
            tooltip(footerSoundHit.x, footerSoundHit.y, footerSoundHit.width,
                    footerSoundHit.height, "gdx.audio.no_output_device");
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

    private Music music(String path, float volume) {
        FileHandle resource = GdxGameAudioResources.resolve(
                presentationSettings, path);
        if (resource == null) {
            throw new IllegalStateException("Missing CoronaPoker music "
                    + path);
        }
        Music music = Gdx.audio.newMusic(resource);
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
                || aboutMusic == null || statsMusic == null
                || customBotManagerMusic == null) return;
        float volume = masterVolume();
        GdxVoicePlayback.refreshVolume(volume);
        setMusicVolume(backgroundMusic, 0.40f * volume);
        setMusicVolume(waitingRoomMusic, 0.90f * volume);
        setMusicVolume(aboutMusic, 0.90f * volume);
        setMusicVolume(statsMusic, 0.30f * volume);
        setMusicVolume(customBotManagerMusic, 0.65f * volume);
        boolean lobbyMusic = surface == Surface.LOBBY
                || (surface == Surface.SCREENSHOTS
                        && screenshotReturnSurface == Surface.LOBBY)
                || (surface == Surface.STATS
                        && statsConfirmation != StatsConfirmation.NONE)
                || (surface == Surface.SETTINGS
                && settingsReturnSurface == Surface.LOBBY);
        boolean aboutDialogMusic = surface == Surface.MENU && aboutOpen;
        boolean statsDialogMusic = surface == Surface.STATS;
        boolean customBotDialogMusic = customBotManagerOpen;
        boolean playBackground = frontendTrackAllowed(tableAudioSuspended,
                !startupAudioHeld
                && musicMasterEnabled() && !lobbyMusic && !aboutDialogMusic
                && !statsDialogMusic && !customBotDialogMusic
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
        boolean playCustomBotManager = frontendTrackAllowed(
                tableAudioSuspended,
                musicMasterEnabled() && customBotDialogMusic
                && preferenceBoolean("musica_gestor_bots", true));
        syncTrack(backgroundMusic, playBackground);
        syncTrack(waitingRoomMusic, playWaitingRoom);
        syncTrack(aboutMusic, playAbout);
        syncTrack(statsMusic, playStats);
        syncTrack(customBotManagerMusic, playCustomBotManager);
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
        volumeKeyRepeat.clear();
        tableAudioSuspended = true;
        stopLobbyTransientAudio();
        if (!preserveBackgroundTrack && backgroundMusic != null) {
            backgroundMusic.pause();
        }
        if (waitingRoomMusic != null) waitingRoomMusic.pause();
        if (aboutMusic != null) aboutMusic.pause();
        if (statsMusic != null) statsMusic.pause();
        if (customBotManagerMusic != null) customBotManagerMusic.pause();
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

    String tablePublicAddress() {
        return lobbyPublicAddress;
    }

    private void openNewGame(NewGameConnectionDraft.Mode mode) {
        recoveryLoadGeneration++;
        autoSubmitRecovery = false;
        cancelAndReleaseUpnp();
        dismissFrontendNotice();
        connection = defaultConnection(initialProperties, mode);
        if (mode == NewGameConnectionDraft.Mode.CREATE) {
            refreshHostAddressOptions();
        } else {
            hostAddressLoadGeneration++;
            hostAddressOptions = List.of(GdxLocalServerAddresses.LOOPBACK_NAME);
        }
        refreshSelectedAvatarTexture();
        table = defaultTableDraft();
        refreshGamePresets(null);
        closePresetDialog();
        page = 0;
        clearActiveField();
        historyIndex = -1;
        clearNewGameToggleAnimations(toggleAnimations);
        activateSurface(Surface.NEW_GAME);
    }

    private void toggleUpnp() {
        if (!upnpToggleEnabled(connection, upnpOpenInFlight)) return;
        if (connection.upnp()) {
            cancelAndReleaseUpnp();
        } else {
            startUpnpPortOpen(false);
        }
    }

    private void startUpnpPortOpen(boolean continueSubmission) {
        if (connection == null || !connection.portValid()) return;
        int port = Integer.parseInt(connection.port());
        if (connection.upnp() && verifiedUpnpPort == port) {
            if (continueSubmission) submitNewGameAfterChecks();
            return;
        }
        cancelAndReleaseUpnp();
        submitAfterUpnpOpen = continueSubmission;
        upnpOpenInFlight = true;
        long generation = ++upnpOpenGeneration;
        captureFrontendModalInput();
        CompletableFuture.supplyAsync(
                () -> UpnpPortReservation.openSystemTcp(port), upnpExecutor)
                .whenComplete((attempt, failure) -> Gdx.app.postRunnable(() ->
                        completeUpnpPortOpen(generation, port, attempt,
                                failure)));
        CompletableFuture.delayedExecutor(12L, TimeUnit.SECONDS).execute(() ->
                Gdx.app.postRunnable(() -> timeoutUpnpPortOpen(generation)));
    }

    private void completeUpnpPortOpen(long generation, int port,
            UpnpPortReservation.Attempt attempt, Throwable failure) {
        if (disposed || generation != upnpOpenGeneration
                || surface != Surface.NEW_GAME || connection == null) {
            if (attempt != null) closeUpnpAttemptAsync(attempt);
            return;
        }
        boolean continueSubmission = submitAfterUpnpOpen;
        if (failure == null && attempt != null && attempt.opened()) {
            upnpOpenInFlight = false;
            submitAfterUpnpOpen = false;
            verifiedUpnpPort = port;
            upnpReservation = attempt.reservation();
            connection.setUpnp(true);
            if (continueSubmission) submitNewGameAfterChecks();
            return;
        }
        cancelUpnpOpen();
        if (attempt != null) closeUpnpAttemptAsync(attempt);
        connection.setUpnp(false);
        UpnpPortReservation.Status status = failure == null && attempt != null
                ? attempt.status() : UpnpPortReservation.Status.FAILED;
        if (failure != null) {
            LOGGER.log(Level.WARNING,
                    "Unable to open UPnP TCP port " + port, failure);
        }
        showFrontendNotice(gameText.translate("gdx.dialog.error"),
                gameText.translate(upnpOpenErrorKey(status)), true);
    }

    private void timeoutUpnpPortOpen(long generation) {
        if (disposed || generation != upnpOpenGeneration
                || !upnpOpenInFlight) return;
        cancelUpnpOpen();
        if (connection != null) connection.setUpnp(false);
        showFrontendNotice(gameText.translate("gdx.dialog.error"),
                gameText.translate(upnpOpenErrorKey(
                        UpnpPortReservation.Status.FAILED)), true);
    }

    static String upnpOpenErrorKey(UpnpPortReservation.Status status) {
        return switch (Objects.requireNonNull(status, "status")) {
            case UNAVAILABLE -> "gdx.lobby.upnp_unavailable";
            case ALREADY_MAPPED -> "gdx.lobby.upnp_already_mapped";
            case FAILED -> "gdx.lobby.upnp_failed";
            case OPEN -> "";
        };
    }

    private void cancelUpnpOpen() {
        upnpOpenGeneration++;
        upnpOpenInFlight = false;
        submitAfterUpnpOpen = false;
        verifiedUpnpPort = -1;
    }

    private void cancelAndReleaseUpnp() {
        cancelUpnpOpen();
        UpnpPortReservation owned = upnpReservation;
        upnpReservation = null;
        if (connection != null) connection.setUpnp(false);
        closeUpnpReservationAsync(owned);
    }

    private void closeUpnpAttemptAsync(UpnpPortReservation.Attempt attempt) {
        closeUpnpReservationAsync(attempt.reservation());
    }

    private void closeUpnpReservationAsync(UpnpPortReservation reservation) {
        if (reservation == null) return;
        try {
            upnpExecutor.execute(reservation::close);
        } catch (RejectedExecutionException rejected) {
            Thread cleanup = new Thread(reservation::close,
                    "CoronaPoker-UPnP-form-close");
            cleanup.setDaemon(true);
            cleanup.start();
        }
    }

    static boolean upnpToggleEnabled(NewGameConnectionDraft connection,
            boolean checkInFlight) {
        return connection != null && connection.portValid() && !checkInFlight;
    }

    static boolean forgetPersistedUpnp(Properties properties) {
        return Objects.requireNonNull(properties, "properties")
                .remove("upnp") != null;
    }

    static void clearNewGameToggleAnimations(Map<String, Float> animations) {
        Objects.requireNonNull(animations, "animations").keySet()
                .removeIf(key -> key.startsWith(Surface.NEW_GAME.name() + ':'));
    }

    static void clearSettingsToggleAnimations(Map<String, Float> animations) {
        Objects.requireNonNull(animations, "animations").keySet()
                .removeIf(key -> key.startsWith(Surface.SETTINGS.name() + ':'));
    }

    private void showFrontendNotice(String title, String message,
            boolean error) {
        frontendNoticeTitle = Objects.requireNonNullElse(title, "");
        frontendNoticeMessage = Objects.requireNonNullElse(message, "");
        frontendNoticeError = error;
        captureFrontendModalInput();
    }

    private void dismissFrontendNotice() {
        frontendNoticeTitle = "";
        frontendNoticeMessage = "";
        frontendNoticeError = false;
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
        // The setup workflow is composed sharp over the shared blurred menu.
        boolean joining = connection.mode() == NewGameConnectionDraft.Mode.JOIN;
        float frameX = joining ? 190f : 50f;
        float frameY = joining ? 120f : 35f;
        float frameW = joining ? 1540f : 1820f;
        float frameH = joining ? 820f : 990f;
        outerBox(frameX - 10f, frameY + 10f, frameW + 20f, frameH,
                CYAN_DARK, PANEL);
    }

    private void drawNewGameScreen() {
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

        boolean avatarHover = !avatarSelectionPending
                && hovered(445f, 385f, 150f, 155f);
        shapes.setColor(avatarSelectionPending ? DISABLED
                : avatarHover ? CYAN : CYAN_DARK);
        GdxAntialiasedRoundedRect.fillCircle(shapes, 520f, 465f, 70f);
        shapes.setColor(PANEL_LIGHT);
        GdxAntialiasedRoundedRect.fillCircle(shapes, 520f, 465f, 61f);
        drawAvatarIcon(520f, 465f);
        hit(445f, 385f, 150f, 155f, this::selectAvatar);
        tooltip(445f, 385f, 150f, 155f, "tooltip.change_avatar");

        field(610f, 435f, 450f,
                gameText.translate("gdx.newgame.nick_required"),
                connection.nickname(), "nick", false);
        dropdownChoice(1170f, 560f, 430f,
                gameText.translate("gdx.newgame.server_required"),
                connection.server(), () -> openDropdown(
                        Dropdown.HOST_ADDRESS,
                        1170f, 560f, 430f, 72f), true);
        field(1630f, 560f, 175f, gameText.translate("gdx.port"),
                connection.port(), "port", false, connection.portValid());
        tooltip(1170f, 560f, 430f, 72f, "tooltip.cfg.server_ip");
        tooltip(1630f, 560f, 175f, 72f, "tooltip.cfg.server_port");
        toggle(1170f, 432f, 635f, "UPnP", connection.upnp(),
                this::toggleUpnp,
                upnpToggleEnabled(connection, upnpOpenInFlight));
        tooltip(1170f, 432f, 635f, GdxSettingsLayout.ROW_HEIGHT,
                "tooltip.cfg.upnp");
        field(1170f, 275f, 635f,
                gameText.translate("gdx.newgame.password_optional"),
                connection.password(), "password", true);
    }

    private void drawJoinIdentityPage() {
        panel(295f, 285f, 620f, 480f,
                uppercase(gameText.translate("gdx.newgame.your_profile")));
        panel(945f, 285f, 680f, 480f,
                uppercase(gameText.translate("gdx.connection")));

        boolean avatarHover = !avatarSelectionPending
                && hovered(315f, 422f, 130f, 135f);
        shapes.setColor(avatarSelectionPending ? DISABLED
                : avatarHover ? CYAN : CYAN_DARK);
        GdxAntialiasedRoundedRect.fillCircle(shapes, 380f, 487f, 60f);
        shapes.setColor(PANEL_LIGHT);
        GdxAntialiasedRoundedRect.fillCircle(shapes, 380f, 487f, 52f);
        drawAvatarIcon(380f, 487f, 104f);
        hit(315f, 422f, 130f, 135f, this::selectAvatar);
        tooltip(315f, 422f, 130f, 135f, "tooltip.change_avatar");

        field(470f, 452f, 400f,
                gameText.translate("gdx.newgame.nick_required"),
                connection.nickname(), "nick", false);

        field(985f, 575f, 385f,
                gameText.translate("gdx.newgame.server_required"),
                connection.server(), "server", false);
        field(1395f, 575f, 185f, gameText.translate("gdx.port"),
                connection.port(), "port", false, connection.portValid());
        tooltip(985f, 575f, 385f, 72f, "tooltip.cfg.server_ip");
        tooltip(1395f, 575f, 185f, 72f, "tooltip.cfg.server_port");
        String history = connection.serverHistory().isEmpty()
                ? gameText.translate("gdx.newgame.no_previous_servers")
                : connection.serverHistory().get(historyIndex < 0
                        ? connection.serverHistory().size() - 1 : historyIndex);
        dropdownChoice(985f, 445f, 595f,
                gameText.translate("gdx.newgame.previous_servers"), history,
                () -> openDropdown(Dropdown.SERVER_HISTORY,
                        985f, 445f, 595f, 72f),
                !connection.serverHistory().isEmpty());
        field(985f, 315f, 595f,
                gameText.translate("gdx.newgame.password_optional"),
                connection.password(), "password", true);
    }

    /** Global presets cover the complete table setup, never just networking. */
    private void drawProfilePage() {
        panel(520f, 185f, 1230f, 625f,
                uppercase(gameText.translate("gdx.newgame.profile_title")));
        textFit(actionFont, gameText.translate("gdx.newgame.profile_help"),
                580f, 700f, MUTED, false, 1110f);
        boolean profileEditable = !connection.recoverRequested();
        dropdownChoice(580f, 500f, 1110f, "",
                selectedPresetLabel(), () -> openDropdown(Dropdown.PROFILE,
                        580f, 500f, 1110f, 72f),
                profileEditable);
        tooltip(580f, 500f, 1110f, 72f, "tooltip.cfg.preset");
        button(580f, 350f, 530f, 70f,
                gameText.translate("newgame.preset_guardar"), false,
                this::openPresetNameDialog, profileEditable);
        tooltip(580f, 350f, 530f, 70f, "tooltip.cfg.preset_save");
        button(1160f, 350f, 530f, 70f,
                gameText.translate("newgame.preset_borrar"), false,
                this::requestDeletePreset,
                profileEditable && selectedGamePreset >= 0);
        tooltip(1160f, 350f, 530f, 70f, "tooltip.cfg.preset_delete");
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
            table = defaultTableDraft();
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
            selectedGamePreset = -1;
            showToast(gameText.translate("gdx.newgame.profile_invalid"));
        }
    }

    private void selectGamePresetOption(int option) {
        if (connection.recoverRequested()) return;
        selectedGamePreset = option - 1;
        dropdown = Dropdown.NONE;
        if (option <= 0) {
            table = defaultTableDraft();
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
                    uppercase(gameText.translate("ui.cancelar")),
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
                uppercase(gameText.translate("ui.cancelar")),
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
        float leftPanelX = 430f;
        float panelY = BLINDS_PANEL_BOTTOM;
        float panelWidth = 697f;
        float panelHeight = 660f;
        float rightPanelX = leftPanelX + panelWidth + 31f;
        panel(leftPanelX, panelY, panelWidth, panelHeight,
                settingsGameText("blinds"));
        panel(rightPanelX, panelY, panelWidth, panelHeight,
                settingsGameText("row.increase_blinds"));

        float leftX = leftPanelX + 40f;
        float rightX = rightPanelX + 40f;
        float rowWidth = panelWidth - 80f;
        float rowHeight = GdxSettingsLayout.ROW_HEIGHT;
        boolean editable = !table.economyLocked();

        float structureY = 641f;
        dropdownChoice(leftX, structureY, rowWidth,
                gameText.translate("gdx.settings.game.row.blind_structure"),
                table.structureName() == null
                        ? gameText.translate("gdx.settings.value.default")
                        : table.structureName(),
                () -> openDropdown(Dropdown.BLIND_STRUCTURE,
                        leftX, structureY, rowWidth, 72f), editable);
        gameSettingTooltip(leftX, structureY, rowWidth, 72f, "structure");

        float initialY = 522f;
        dropdownChoice(leftX, initialY, rowWidth,
                gameText.translate("gdx.settings.game.row.initial_blinds"),
                blindLevelLabel(table.blindLevel()),
                () -> openDropdown(Dropdown.BLIND_LEVEL,
                        leftX, initialY, rowWidth, 72f), editable);
        gameSettingTooltip(leftX, initialY, rowWidth, 72f, "initial_blinds");
        button(leftX, 435f, rowWidth, 70f,
                settingsGameText("row.manage_structures"), false,
                this::openBlindStructureEditor, editable);
        gameSettingTooltip(leftX, 435f, rowWidth, 70f, "blind_structures");

        float modeY = 266f;
        float anteY = modeY + rowHeight + 8f;
        plainPanel(leftX, modeY - 8f, rowWidth, rowHeight * 2f + 24f);
        float anteInset = 14f;
        float anteX = leftX + anteInset;
        float anteWidth = rowWidth - 2f * anteInset;
        toggle(anteX, anteY, anteWidth, settingsGameText("row.ante"), table.ante(),
                () -> table.setAnte(!table.ante()), editable);
        gameSettingTooltip(anteX, anteY, anteWidth, rowHeight, "ante");
        settingsDropdownChoice(anteX, modeY, anteWidth, rowHeight,
                settingsGameText("row.ante_mode"),
                gameText.translate(table.anteMode().labelKey()),
                () -> openDropdown(Dropdown.ANTE_MODE,
                        anteX, modeY, anteWidth, rowHeight),
                editable && table.ante());
        gameSettingTooltip(anteX, modeY, anteWidth, rowHeight, "ante_mode");

        float straddleY = 177f;
        toggle(leftX, straddleY, rowWidth, settingsGameText("row.straddle"),
                table.straddle(), () -> table.setStraddle(!table.straddle()), editable);
        gameSettingTooltip(leftX, straddleY, rowWidth, rowHeight, "straddle");

        float increaseY = 645f;
        toggle(rightX, increaseY, rowWidth,
                settingsGameText("row.increase_blinds"), table.increaseBlinds(),
                () -> table.setIncreaseBlinds(!table.increaseBlinds()), editable);
        gameSettingTooltip(rightX, increaseY, rowWidth, rowHeight,
                "increase_blinds");

        float intervalY = 503f;
        float halfWidth = (rowWidth - 30f) / 2f;
        dropdownChoice(rightX, intervalY, halfWidth,
                settingsGameText("row.unit"),
                table.blindIncreaseType() == NewGameTableDraft.BlindIncreaseType.MINUTES
                        ? gameText.translate("gdx.settings.game.value.minutes")
                        : gameText.translate("gdx.settings.game.value.hands"),
                () -> openDropdown(Dropdown.BLIND_UNIT,
                        rightX, intervalY, halfWidth, 72f),
                editable && table.increaseBlinds());
        gameSettingTooltip(rightX, intervalY, halfWidth, 72f, "blind_unit");
        stepper(rightX + halfWidth + 30f, intervalY, halfWidth,
                settingsGameText("row.interval"),
                table.blindInterval(), 1, Integer.MAX_VALUE,
                () -> table.setBlindInterval(table.blindInterval() - 1),
                () -> table.setBlindInterval(table.blindInterval() + 1),
                editable && table.increaseBlinds());
        gameSettingTooltip(rightX + halfWidth + 30f, intervalY, halfWidth,
                102f, "blind_interval");

        float capToggleY = 345f;
        toggle(rightX, capToggleY, rowWidth,
                settingsGameText("row.blind_cap"), table.blindCap(),
                () -> table.setBlindCap(!table.blindCap()),
                editable && table.blindCapControlEnabled());
        gameSettingTooltip(rightX, capToggleY, rowWidth, rowHeight, "blind_cap");
        stepper(rightX, 194f, rowWidth,
                settingsGameText("row.cap"), table.blindCapRaises(),
                1, table.maxBlindCapRaises(),
                () -> table.setBlindCapRaises(table.blindCapRaises() - 1),
                () -> table.setBlindCapRaises(table.blindCapRaises() + 1),
                editable && table.blindCapRaisesEnabled());
        gameSettingTooltip(rightX, 194f, rowWidth, 102f, "blind_cap_level");
    }

    private static String blindLevelLabel(NewGameTableDraft.BlindLevel level) {
        return GdxSettingsContract.blindLevelLabel(level.smallBlind(),
                level.bigBlind());
    }

    private static String formatBlind(double value) {
        return GdxSettingsContract.blindAmountLabel(value);
    }

    private void selectBlindLevelOption(int option) {
        NewGameTableDraft target = dropdownTableTarget();
        if (!canEditTableEconomy(target) || option == target.blindLevelIndex()
                || option < 0 || option >= target.blindLevels().size()) {
            return;
        }
        double bigBlind = target.blindLevels().get(option).bigBlind();
        if (BuyinRules.min(bigBlind, target.minBuyinBb())
                > BuyinRules.max(bigBlind, target.maxBuyinBb())) {
            showToast(gameText.translate("gdx.settings.game.blind_level.range"));
            return;
        }
        target.setBlindLevelIndex(option);
    }

    private void selectBlindStructureOption(int option) {
        NewGameTableDraft target = blindStructureDropdownTarget();
        dropdown = Dropdown.NONE;
        if (target == null) return;
        if (option <= 0) {
            selectDefaultBlindStructure(target);
            return;
        }
        List<BlindStructureCatalog.Entry> saved = BlindStructureCatalog.read(
                initialProperties);
        if (option > saved.size()) return;
        BlindStructureCatalog.Entry entry = saved.get(option - 1);
        target.setBlindStructure(entry.name(), entry.levels().stream()
                .map(level -> new NewGameTableDraft.BlindLevel(
                        level.smallBlind(), level.bigBlind()))
                .toList(), target.blindLevelIndex());
    }

    private NewGameTableDraft blindStructureDropdownTarget() {
        return dropdownTableTarget();
    }

    private NewGameTableDraft dropdownTableTarget() {
        return surface == Surface.SETTINGS ? settingsTable : table;
    }

    private boolean canEditTableEconomy(NewGameTableDraft target) {
        return target != null && !target.economyLocked()
                && (surface == Surface.NEW_GAME || surface == Surface.SETTINGS
                        && (settingsSession.context() == GdxSettingsSession.Context.MENU
                                || lobby != null && lobby.host()));
    }

    private void selectDefaultBlindStructure(NewGameTableDraft target) {
        target.setBlindStructure(null,
                Arrays.stream(BlindStructureRules.defaultLevels())
                        .map(level -> new NewGameTableDraft.BlindLevel(
                                level[0], level[1]))
                        .toList(), target.blindLevelIndex());
    }

    private void selectBlindStructureEditorOption(int option) {
        if (option >= 0
                && option < blindStructureEditor.entries().size()) {
            blindStructureEditor.selectStructureIndex(option);
        }
        dropdown = Dropdown.NONE;
    }

    private void openBlindStructureEditor() {
        if (table.economyLocked()) return;
        dropdown = Dropdown.NONE;
        blindStructureSettingsTarget = false;
        blindStructureEditor.begin(initialProperties, table.structureName());
        blindStructureDialog = BlindStructureDialog.EDITOR;
        blindStructureNameDraft = "";
        clearActiveField();
    }

    private void openSettingsBlindStructureEditor() {
        if (settingsTable == null || settingsTable.economyLocked()) return;
        boolean menuDefaults = settingsSession.context()
                == GdxSettingsSession.Context.MENU;
        if (!menuDefaults && (lobby == null || !lobby.host())) return;
        dropdown = Dropdown.NONE;
        blindStructureSettingsTarget = true;
        blindStructureEditor.begin(initialProperties,
                settingsTable.structureName());
        blindStructureDialog = BlindStructureDialog.EDITOR;
        blindStructureNameDraft = "";
        clearActiveField();
    }

    private void closeBlindStructureEditor() {
        dropdown = Dropdown.NONE;
        blindStructureDialog = BlindStructureDialog.NONE;
        blindStructureNameDraft = "";
        clearActiveField();
    }

    private void saveBlindStructures() {
        NewGameTableDraft target = blindStructureSettingsTarget
                ? settingsTable : table;
        if (target == null) {
            closeBlindStructureEditor();
            return;
        }
        List<BlindStructureCatalog.Entry> previous =
                BlindStructureCatalog.read(initialProperties);
        try {
            blindStructureEditor.writeTo(initialProperties);
        } catch (IllegalArgumentException invalid) {
            showToast(gameText.translate(
                    "gdx.newgame.blind_editor.review"));
            return;
        }
        if (!blindStructureSettingsTarget && preferences != null) {
            try {
                // The Save button is a durability boundary: keep the editor
                // open and dirty until the catalogue is safely on disk.
                preferences.save();
            } catch (IOException failure) {
                BlindStructureCatalog.writeTo(initialProperties, previous);
                LOGGER.log(Level.SEVERE,
                        "Could not persist blind structures", failure);
                showToast(gameText.translate(
                        "gdx.newgame.blind_editor.persist_failed"));
                return;
            }
        }
        blindStructureEditor.markPersisted();
        BlindStructureCatalog.Entry selected = blindStructureEditor.selected();
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
        dropdownChoice(275f, 710f, 580f,
                gameText.translate("blinds.estructura"),
                structureLabel, () -> openDropdown(
                        Dropdown.BLIND_STRUCTURE_EDITOR,
                        275f, 710f, 580f, 72f),
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
                uppercase(gameText.translate("ui.cancelar")), false,
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
            showToast(gameText.translate(BlindStructureRules.ERR_VALUE_RANGE));
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
                uppercase(gameText.translate("ui.aceptar")),
                ButtonTone.POSITIVE, this::submitBlindStructureName,
                !blindStructureNameDraft.isBlank());
    }

    private void drawBlindStructureDeleteConfirmation() {
        String name = blindStructureEditor.selected() == null ? ""
                : blindStructureEditor.selected().name();
        GdxUiDialogStyle.ConfirmationLayout layout = drawConfirmationChrome(
                uppercase(gameText.translate(
                        "gdx.newgame.blind_editor.delete_structure")),
                gameText.translate("blinds.confirmar_borrar", name));
        Rectangle cancel = layout.negativeButton();
        Rectangle accept = layout.positiveButton();
        themedButton(cancel.x, cancel.y, cancel.width, cancel.height,
                uppercase(gameText.translate("ui.volver")),
                ButtonTone.NEUTRAL,
                () -> blindStructureDialog = BlindStructureDialog.EDITOR,
                true);
        themedButton(accept.x, accept.y, accept.width, accept.height,
                uppercase(gameText.translate("blinds.borrar")),
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
        gameSettingTooltip(470f, 625f, 590f, GdxSettingsLayout.ROW_HEIGHT,
                "fixed_buyin");
        initialBuyinChoice(470f, 480f, 590f, GdxSettingsLayout.ROW_HEIGHT,
                table, !table.economyLocked());
        gameSettingTooltip(470f, 480f, 590f, GdxSettingsLayout.ROW_HEIGHT,
                "initial_buyin");
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
        gameSettingTooltip(470f, 315f, 280f, 102f, "minimum_buyin");
        gameSettingTooltip(780f, 315f, 280f, 102f, "maximum_buyin");

        toggle(1170f, 625f, 645f,
                settingsGameText("row.rebuy"), table.rebuy(),
                () -> table.setRebuy(!table.rebuy()), true);
        gameSettingTooltip(1170f, 625f, 645f, GdxSettingsLayout.ROW_HEIGHT,
                "rebuy");
        toggle(1170f, 505f, 645f,
                settingsGameText("row.player_limit"), table.rebuyLimit(),
                () -> table.setRebuyLimit(!table.rebuyLimit()), table.rebuyLimitEnabled());
        gameSettingTooltip(1170f, 505f, 645f, GdxSettingsLayout.ROW_HEIGHT,
                "player_rebuy_limit");
        stepper(1170f, 365f, 645f,
                settingsGameText("row.maximum_rebuys"),
                table.rebuyLimitCount(), 1, Integer.MAX_VALUE,
                () -> table.setRebuyLimitCount(table.rebuyLimitCount() - 1),
                () -> table.setRebuyLimitCount(table.rebuyLimitCount() + 1),
                table.rebuyLimitCountEnabled());
        gameSettingTooltip(1170f, 365f, 645f, 102f, "maximum_rebuys");
        dropdownChoice(1170f, 225f, 645f,
                settingsGameText("row.rebuy_cap"),
                table.rebuyCapPolicy() == NewGameTableDraft.RebuyCapPolicy.BUY_IN
                        ? "BUY-IN"
                        : settingsGameText("value.highest_stack"),
                () -> openDropdown(Dropdown.REBUY_CAP,
                        1170f, 225f, 645f, 72f),
                table.rebuy());
        gameSettingTooltip(1170f, 225f, 645f, 72f, "rebuy_cap");
    }

    private void initialBuyinChoice(float x, float y, float width, float height,
            NewGameTableDraft draft, boolean economyEditable) {
        settingsChoice(x, y, width, height, settingsGameText("row.initial_buyin"),
                GdxChipAmountText.buyinLabel(draft.buyin(), gameText.language()),
                this::openInitialBuyinEditor, economyEditable && draft.fixedBuyin());
    }

    private void openInitialBuyinEditor() {
        NewGameTableDraft target = dropdownTableTarget();
        if (!canEditTableEconomy(target) || !target.fixedBuyin()) return;
        clearModalCompositionState();
        initialBuyinInput = GdxChipAmountText.buyinLabel(
                target.buyin(), gameText.language());
        activateField("initialBuyin");
        textEdit.selectAll(initialBuyinInput);
    }

    private void closeInitialBuyinEditor() {
        initialBuyinInput = null;
        editMenu = null;
        clearActiveField();
    }

    private void submitInitialBuyin() {
        NewGameTableDraft target = dropdownTableTarget();
        if (!canEditTableEconomy(target) || !target.fixedBuyin()) return;
        OptionalInt amount = GdxChipAmountText.parseWholeChips(
                initialBuyinInput, gameText.language());
        if (initialBuyinError(target, amount).isEmpty()) {
            target.setBuyin(amount.getAsInt());
            closeInitialBuyinEditor();
        }
    }

    private static String initialBuyinError(NewGameTableDraft target, OptionalInt amount) {
        if (amount.isEmpty()) return "gdx.settings.game.buyin_editor.invalid";
        if (amount.getAsInt() < target.minimumBuyin()
                || amount.getAsInt() > target.maximumBuyin()) {
            return "gdx.settings.game.buyin_editor.out_of_range";
        }
        return "";
    }

    private void drawInitialBuyinEditor() {
        NewGameTableDraft target = dropdownTableTarget();
        String language = gameText.language();
        OptionalInt amount = GdxChipAmountText.parseWholeChips(initialBuyinInput, language);
        String error = initialBuyinError(target, amount);
        boolean showError = !initialBuyinInput.isBlank() && !error.isEmpty();
        GdxUiDialogStyle.drawPanel(shapes, 560f, 330f, 800f, 404f,
                CYAN_DARK, 1f);
        textFit(headingFont, settingsGameText("row.initial_buyin"),
                960f, 681f, GOLD, true, 700f);
        field(660f, 498f, 600f,
                gameText.translate("gdx.settings.game.buyin_editor.amount"),
                initialBuyinInput, "initialBuyin", false, !showError);
        String range = gameText.translate("gdx.settings.game.buyin_editor.range",
                GdxChipAmountText.buyinLabel(target.minimumBuyin(), language),
                GdxChipAmountText.buyinLabel(target.maximumBuyin(), language));
        textFit(smallFont, range, 660f, 479f, MUTED, false, 600f);
        if (showError) {
            textFit(smallFont, gameText.translate(error),
                    660f, 444f, ORANGE, false, 600f);
        }
        themedButton(660f, 350f, 270f, 64f, gameText.translate("ui.cancelar"),
                ButtonTone.NEUTRAL, this::closeInitialBuyinEditor, true);
        themedButton(990f, 350f, 270f, 64f, uppercase(gameText.translate("ui.aceptar")),
                ButtonTone.POSITIVE, this::submitInitialBuyin,
                canEditTableEconomy(target) && target.fixedBuyin() && error.isEmpty());
    }

    private void drawGamePage() {
        panel(430f, 185f, 670f, 625f, settingsGameText("game"));
        panel(1130f, 185f, 725f, 625f, settingsGameText("rules"));

        toggle(470f, NEW_GAME_GAME_TOP_ROW_Y, 280f,
                gameText.translate("gdx.settings.game.row.hand_limit"),
                table.handLimit(),
                () -> table.setHandLimit(!table.handLimit()), true);
        gameSettingTooltip(470f, NEW_GAME_GAME_TOP_ROW_Y, 590f,
                GdxSettingsLayout.ROW_HEIGHT,
                "hand_limit");
        inlineStepper(780f, NEW_GAME_GAME_TOP_ROW_Y, 280f,
                table.handLimitCount(), 1, Integer.MAX_VALUE,
                () -> table.setHandLimitCount(table.handLimitCount() - 1),
                () -> table.setHandLimitCount(table.handLimitCount() + 1),
                table.handLimit());
        toggle(470f, NEW_GAME_GAME_SECOND_ROW_Y, 280f,
                gameText.translate("gdx.settings.game.row.think_time"),
                table.thinkTime(),
                () -> table.setThinkTime(!table.thinkTime()), true);
        gameSettingTooltip(470f, NEW_GAME_GAME_SECOND_ROW_Y, 590f,
                GdxSettingsLayout.ROW_HEIGHT,
                "think_time");
        inlineStepper(780f, NEW_GAME_GAME_SECOND_ROW_Y, 280f,
                table.thinkSeconds(), 10, 120,
                () -> table.setThinkSeconds(table.thinkSeconds() - 5),
                () -> table.setThinkSeconds(table.thinkSeconds() + 5), table.thinkTime());
        stepper(470f, NEW_GAME_GAME_LABELLED_ROW_Y, 590f,
                gameText.translate("gdx.settings.game.row.showdown_seconds"),
                table.showdownSeconds(), 5, 30,
                () -> table.setShowdownSeconds(table.showdownSeconds() - 5),
                () -> table.setShowdownSeconds(table.showdownSeconds() + 5));
        gameSettingTooltip(470f, NEW_GAME_GAME_LABELLED_ROW_Y, 590f, 102f,
                "showdown_time");
        toggle(470f, NEW_GAME_GAME_BOTTOM_ROW_Y, 590f,
                gameText.translate("gdx.settings.game.row.allow_hot_join"),
                table.allowHotJoin(),
                () -> table.setAllowHotJoin(!table.allowHotJoin()), true);
        gameSettingTooltip(470f, NEW_GAME_GAME_BOTTOM_ROW_Y, 590f,
                GdxSettingsLayout.ROW_HEIGHT,
                "allow_hot_join");

        toggle(1170f, NEW_GAME_GAME_TOP_ROW_Y, 645f, "IWTSTH", table.iwtsth(),
                () -> table.setIwtsth(!table.iwtsth()), true);
        gameSettingTooltip(1170f, NEW_GAME_GAME_TOP_ROW_Y, 645f,
                GdxSettingsLayout.ROW_HEIGHT,
                "iwtsth");
        toggle(1170f, NEW_GAME_GAME_SECOND_ROW_Y, 645f, "RUN IT TWICE",
                table.runItTwice(),
                () -> table.setRunItTwice(!table.runItTwice()), true);
        gameSettingTooltip(1170f, NEW_GAME_GAME_SECOND_ROW_Y, 645f,
                GdxSettingsLayout.ROW_HEIGHT,
                "run_it_twice");
        dropdownChoice(1170f, NEW_GAME_GAME_LABELLED_ROW_Y, 645f,
                settingsGameText("row.rabbit_hunting"),
                rabbitText(), () -> openDropdown(Dropdown.RABBIT,
                        1170f, NEW_GAME_GAME_LABELLED_ROW_Y,
                        645f, 72f), true);
        gameSettingTooltip(1170f, NEW_GAME_GAME_LABELLED_ROW_Y, 645f, 102f,
                "rabbit_hunting");
    }

    private String rabbitText() {
        return rabbitLabel(table.rabbitHunting());
    }

    private String rabbitLabel(NewGameTableDraft.RabbitHunting value) {
        return switch (value) {
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
        // Keep the first control entirely below the panel heading and divider.
        // The generic choice label is drawn 99 px above its origin, so using
        // the same origin as a title-less panel makes it invade the heading.
        dropdownChoice(580f, 610f, 1110f,
                gameText.translate("gdx.settings.game.row.bot_difficulty"),
                table.botDifficulty() == NewGameTableDraft.BotDifficulty.EASY
                        ? gameText.translate("gdx.settings.game.value.easy")
                        : table.botDifficulty() == NewGameTableDraft.BotDifficulty.HARD
                                ? gameText.translate(
                                        "gdx.settings.game.value.hard")
                                : gameText.translate(
                                        "gdx.settings.game.value.medium"),
                () -> openDropdown(Dropdown.BOT_DIFFICULTY,
                        580f, 610f, 1110f, 72f), true);
        gameSettingTooltip(580f, 610f, 1110f, 102f, "bot_difficulty");
        // Four equal rows share the space below difficulty with comparable
        // top and bottom breathing room inside the panel.
        float botOptionTopY = 505f;
        float botOptionStride = 97f;
        float customBotsY = botOptionTopY;
        float customBotsOnlyY = botOptionTopY - botOptionStride;
        float botRebuyY = botOptionTopY - botOptionStride * 2f;
        float botBalanceY = botOptionTopY - botOptionStride * 3f;
        toggle(580f, customBotsY, 675f,
                gameText.translate("gdx.custom_bots.use"),
                table.customBots(),
                this::toggleCustomBots, true);
        gameSettingTooltip(580f, customBotsY, 675f, GdxSettingsLayout.ROW_HEIGHT,
                "custom_bots");
        button(1280f, customBotsY, 410f, GdxSettingsLayout.ROW_HEIGHT,
                uppercase(gameText.translate("gdx.custom_bots.manage")),
                false, this::openCustomBotManager, table.customBots());
        gameSettingTooltip(1280f, customBotsY, 410f, GdxSettingsLayout.ROW_HEIGHT,
                "custom_bots_manage");
        toggle(580f, customBotsOnlyY, 1110f,
                gameText.translate("gdx.custom_bots.only"),
                table.customBots() && table.customBotsOnly(),
                this::toggleCustomBotsOnly, table.customBots());
        gameSettingTooltip(580f, customBotsOnlyY, 1110f, GdxSettingsLayout.ROW_HEIGHT,
                "custom_bots_only");
        toggle(580f, botRebuyY, 1110f,
                gameText.translate("gdx.settings.game.row.bot_rebuy"),
                table.botRebuy(),
                () -> table.setBotRebuy(!table.botRebuy()), table.botRebuyEnabled());
        gameSettingTooltip(580f, botRebuyY, 1110f, GdxSettingsLayout.ROW_HEIGHT,
                "bot_rebuy");
        toggle(580f, botBalanceY, 1110f,
                gameText.translate("gdx.settings.game.row.bot_balance"),
                table.botBalanceToHumans(),
                () -> table.setBotBalanceToHumans(!table.botBalanceToHumans()), true);
        gameSettingTooltip(580f, botBalanceY, 1110f, GdxSettingsLayout.ROW_HEIGHT,
                "bot_balance");
    }

    private void nextBotDifficulty() {
        selectBotDifficulty(1);
    }

    private void previousBotDifficulty() {
        selectBotDifficulty(-1);
    }

    private NewGameTableDraft defaultTableDraft() {
        NewGameTableDraft draft;
        String stored = initialProperties.getProperty(
                DEFAULT_TABLE_SETTINGS_KEY, "");
        try {
            draft = stored.isBlank() ? new NewGameTableDraft()
                    : NewGameTableDraft.from(
                            NewGameTableDraft.Settings.parseWire(stored));
        } catch (IllegalArgumentException invalid) {
            draft = new NewGameTableDraft();
        }
        draft.setCustomBots(CustomBotProfileCatalog.enabled(
                initialProperties));
        draft.setCustomBotsOnly(CustomBotProfileCatalog.onlyCustom(
                initialProperties));
        return draft;
    }

    private void toggleCustomBots() {
        table.setCustomBots(!table.customBots());
    }

    private void toggleCustomBotsOnly() {
        if (!table.customBots()) return;
        table.setCustomBotsOnly(!table.customBotsOnly());
    }

    private void selectBotDifficulty(int direction) {
        NewGameTableDraft.BotDifficulty[] values = NewGameTableDraft.BotDifficulty.values();
        table.setBotDifficulty(values[adjacentIndex(
                table.botDifficulty().ordinal(), values.length, direction)]);
    }

    private void openCustomBotManager() {
        customBotManagerOpen = true;
        customBotDeleteConfirmation = false;
        customBotValidationError = "";
        if (customBotProfiles.isEmpty()) newCustomBot();
        else selectCustomBot(Math.min(Math.max(customBotSelected, 0),
                customBotProfiles.size() - 1));
        captureFrontendModalInput();
        syncMusicForSurface();
    }

    private void closeCustomBotManager() {
        customBotManagerOpen = false;
        customBotDeleteConfirmation = false;
        customBotAvatarSelectionPending = false;
        customBotValidationError = "";
        clearActiveField();
        syncMusicForSurface();
    }

    private void newCustomBot() {
        newCustomBot(customBotProfiles.size());
    }

    private void newCustomBot(int emptyRow) {
        if (emptyRow < customBotProfiles.size()
                || emptyRow >= CustomBotProfileCatalog.MAX_PROFILES) return;
        customBotSelected = -1;
        customBotNewSlotSelected = emptyRow;
        customBotNameDraft = "";
        customBotAvatarDraft = null;
        customBotDifficultyDraft = NewGameTableDraft.BotDifficulty.MEDIUM;
        customBotPersonalityDraft = CustomBotProfile.Personality.RANDOM;
        customBotAutomaticRebuyDraft = true;
        customBotValidationError = "";
        ensureCustomBotVisible(emptyRow);
        activateField("customBotName");
    }

    private void selectCustomBot(int index) {
        if (index < 0 || index >= customBotProfiles.size()) return;
        customBotSelected = index;
        customBotNewSlotSelected = -1;
        ensureCustomBotVisible(index);
        CustomBotProfile profile = customBotProfiles.get(index);
        customBotNameDraft = profile.displayName();
        customBotAvatarDraft = profile.avatar();
        customBotDifficultyDraft = profile.difficulty();
        customBotPersonalityDraft = profile.personality();
        customBotAutomaticRebuyDraft = profile.automaticRebuy();
        customBotValidationError = "";
        clearActiveField();
    }

    private void adjustCustomBotDifficulty(int direction) {
        NewGameTableDraft.BotDifficulty[] values
                = NewGameTableDraft.BotDifficulty.values();
        customBotDifficultyDraft = values[adjacentIndex(
                customBotDifficultyDraft.ordinal(), values.length,
                direction)];
    }

    private void adjustCustomBotPersonality(int direction) {
        CustomBotProfile.Personality[] values
                = CustomBotProfile.Personality.values();
        customBotPersonalityDraft = values[adjacentIndex(
                customBotPersonalityDraft.ordinal(), values.length,
                direction)];
    }

    private String customBotDifficultyLabel() {
        return switch (customBotDifficultyDraft) {
            case EASY -> gameText.translate("gdx.settings.game.value.easy");
            case HARD -> gameText.translate("gdx.settings.game.value.hard");
            case MEDIUM -> gameText.translate(
                    "gdx.custom_bots.difficulty.normal");
        };
    }

    private String customBotPersonalityLabel(
            CustomBotProfile.Personality personality) {
        return gameText.translate(switch (personality) {
            case RANDOM -> "gdx.custom_bots.personality.random";
            case NIT -> "gdx.custom_bots.personality.nit";
            case STATION -> "gdx.custom_bots.personality.station";
            case TAG -> "gdx.custom_bots.personality.tag";
            case LAG -> "gdx.custom_bots.personality.lag";
        });
    }

    private void moveCustomBot(int direction) {
        int target = customBotSelected + direction;
        if (customBotSelected < 0 || target < 0
                || target >= customBotProfiles.size()) return;
        Collections.swap(customBotProfiles, customBotSelected, target);
        customBotSelected = target;
        ensureCustomBotVisible(target);
        persistCustomBotProfiles();
    }

    private void toggleCustomBotRandomSelection() {
        customBotRandomSelection = !customBotRandomSelection;
        CustomBotProfileCatalog.writeRandomSelection(initialProperties,
                customBotRandomSelection);
        if (preferences != null) preferences.saveDeferred();
    }

    private void saveCustomBot() {
        try {
            Path avatar = customBotAvatarDraft == null ? null
                    : CustomBotProfileCatalog.importAvatar(
                            customBotAvatarDraft, Path.of(
                                    System.getProperty("user.home"),
                                    ".coronapoker"));
            CustomBotProfile candidate = CustomBotProfile.create(
                    customBotNameDraft, avatar, customBotDifficultyDraft,
                    customBotPersonalityDraft,
                    customBotAutomaticRebuyDraft);
            for (int index = 0; index < customBotProfiles.size(); index++) {
                if (index == customBotSelected) continue;
                CustomBotProfile existing = customBotProfiles.get(index);
                if (existing.nickname().equals(candidate.nickname())) {
                    throw new IllegalArgumentException(gameText.translate(
                            "gdx.custom_bots.error.duplicate_name"));
                }
            }
            if (customBotSelected < 0) {
                if (customBotProfiles.size()
                        >= CustomBotProfileCatalog.MAX_PROFILES) {
                    throw new IllegalArgumentException(gameText.translate(
                            "gdx.custom_bots.error.limit"));
                }
                customBotProfiles.add(candidate);
                customBotSelected = customBotProfiles.size() - 1;
                customBotNewSlotSelected = -1;
            } else {
                customBotProfiles.set(customBotSelected, candidate);
            }
            customBotAvatarDraft = avatar;
            ensureCustomBotVisible(customBotSelected);
            customBotValidationError = "";
            disposeCustomBotAvatarTextures();
            persistCustomBotProfiles();
            showToast(gameText.translate("gdx.custom_bots.saved"));
        } catch (IllegalArgumentException invalid) {
            String message = invalid.getMessage();
            customBotValidationError = message == null || message.isBlank()
                    ? gameText.translate("gdx.custom_bots.error.invalid")
                    : message;
        } catch (IOException failure) {
            customBotValidationError = gameText.translate(
                    "gdx.custom_bots.error.avatar");
            LOGGER.log(Level.WARNING, "Could not import custom bot avatar",
                    failure);
        }
    }

    private boolean customBotSaveEnabled() {
        CustomBotProfile selected = customBotSelected >= 0
                && customBotSelected < customBotProfiles.size()
                ? customBotProfiles.get(customBotSelected) : null;
        return !customBotAvatarSelectionPending
                && !customBotNameDraft.isBlank()
                && customBotDraftHasChanges(selected, customBotNameDraft,
                        customBotAvatarDraft, customBotDifficultyDraft,
                        customBotPersonalityDraft,
                        customBotAutomaticRebuyDraft);
    }

    static boolean customBotDraftHasChanges(CustomBotProfile selected,
            String name, Path avatar,
            NewGameTableDraft.BotDifficulty difficulty,
            CustomBotProfile.Personality personality,
            boolean automaticRebuy) {
        String normalizedName = Normalizer.normalize(
                Objects.requireNonNullElse(name, "").trim(),
                Normalizer.Form.NFC);
        Path normalizedAvatar = avatar == null ? null
                : avatar.toAbsolutePath().normalize();
        NewGameTableDraft.BotDifficulty normalizedDifficulty
                = Objects.requireNonNullElse(difficulty,
                        NewGameTableDraft.BotDifficulty.MEDIUM);
        CustomBotProfile.Personality normalizedPersonality
                = Objects.requireNonNullElse(personality,
                        CustomBotProfile.Personality.RANDOM);
        if (selected == null) {
            return !normalizedName.isEmpty() || normalizedAvatar != null
                    || normalizedDifficulty
                            != NewGameTableDraft.BotDifficulty.MEDIUM
                    || normalizedPersonality
                            != CustomBotProfile.Personality.RANDOM
                    || !automaticRebuy;
        }
        return !selected.displayName().equals(normalizedName)
                || !Objects.equals(selected.avatar(), normalizedAvatar)
                || selected.difficulty() != normalizedDifficulty
                || selected.personality() != normalizedPersonality
                || selected.automaticRebuy() != automaticRebuy;
    }

    private boolean customBotGlobalRebuyEnabled() {
        return settingsTable != null && surface == Surface.SETTINGS
                ? settingsTable.botRebuy() : table.botRebuy();
    }

    private void toggleCustomBotAutomaticRebuy() {
        if (customBotGlobalRebuyEnabled()) {
            customBotAutomaticRebuyDraft = !customBotAutomaticRebuyDraft;
        }
    }

    private void deleteCustomBot() {
        if (customBotSelected < 0
                || customBotSelected >= customBotProfiles.size()) return;
        customBotProfiles.remove(customBotSelected);
        disposeCustomBotAvatarTextures();
        persistCustomBotProfiles();
        customBotDeleteConfirmation = false;
        if (customBotProfiles.isEmpty()) newCustomBot();
        else selectCustomBot(Math.min(customBotSelected,
                customBotProfiles.size() - 1));
        customBotScrollMaximum = customBotMaximumPixelScroll(
                customBotDisplayRowCount());
        customBotScrollTarget = MathUtils.clamp(customBotScrollTarget,
                0f, customBotScrollMaximum);
        customBotScroll = MathUtils.clamp(customBotScroll,
                0f, customBotScrollMaximum);
    }

    static float customBotContentHeight(int profileCount) {
        int count = Math.max(0, profileCount);
        return count == 0 ? 0f : CUSTOM_BOT_ROW_HEIGHT
                + Math.max(0, count - 1) * CUSTOM_BOT_ROW_STRIDE;
    }

    static float customBotMaximumPixelScroll(int profileCount) {
        return Math.max(0f, customBotContentHeight(profileCount)
                - (CUSTOM_BOT_ROWS_TOP - CUSTOM_BOT_ROWS_BOTTOM));
    }

    static float customBotScrollAfterWheel(float current, float maximum,
            float amountY) {
        return GdxSettingsLayout.pixelScrollAfterWheel(current, maximum,
                amountY);
    }

    static float customBotScrollForSelection(int index, float current,
            float maximum) {
        if (index < 0) return MathUtils.clamp(current, 0f, maximum);
        float top = index * CUSTOM_BOT_ROW_STRIDE;
        float bottom = top + CUSTOM_BOT_ROW_HEIGHT;
        float viewport = CUSTOM_BOT_ROWS_TOP - CUSTOM_BOT_ROWS_BOTTOM;
        if (top < current) current = top;
        else if (bottom > current + viewport) current = bottom - viewport;
        return MathUtils.clamp(current, 0f, maximum);
    }

    static float customBotSelectionPulse(float elapsedSeconds) {
        float cycle = elapsedSeconds * MathUtils.PI2 / 1.8f
                - MathUtils.PI / 2f;
        return 0.5f + 0.5f * MathUtils.sin(cycle);
    }

    private int customBotDisplayRowCount() {
        return Math.max(customBotProfiles.size(),
                customBotNewSlotSelected + 1);
    }

    private void ensureCustomBotVisible(int index) {
        if (index < 0) return;
        customBotScrollMaximum = customBotMaximumPixelScroll(
                customBotDisplayRowCount());
        customBotScrollTarget = customBotScrollForSelection(index,
                customBotScrollTarget, customBotScrollMaximum);
    }

    private void drawCustomBotRowBox(float x, float y, float w, float h,
            boolean selected, boolean hovered, Color fill) {
        if (!selected) {
            outerBox(x, y, w, h, hovered ? CYAN : LINE, fill);
            return;
        }
        float pulse = customBotSelectionPulse(elapsed);
        customBotSelectionBorder.set(GOLD).lerp(CYAN, pulse);
        // Keep the canonical two-pixel rounded outline. Only its colour
        // breathes, so the corners never change radius or apparent weight.
        outerBox(x, y, w, h, customBotSelectionBorder, fill);
    }

    private void persistCustomBotProfiles() {
        CustomBotProfileCatalog.writeTo(initialProperties,
                customBotProfiles);
        if (preferences != null) preferences.saveDeferred();
    }

    private void selectCustomBotAvatar() {
        if (customBotAvatarSelectionPending) return;
        customBotAvatarSelectionPending = true;
        chooseAvatarFile("gdx.custom_bots.avatar.select").whenComplete((selected, failure) ->
                Gdx.app.postRunnable(() -> {
                    customBotAvatarSelectionPending = false;
                    if (disposed || !customBotManagerOpen) return;
                    Throwable cause = unwrap(failure);
                    if (cause != null) {
                        customBotValidationError = gameText.translate(
                                "gdx.avatar.select_failed");
                        LOGGER.log(Level.WARNING,
                                "Custom bot avatar selector failed", cause);
                    } else if (selected != null) {
                        if (openAvatarCrop(selected,
                                AvatarCropTarget.CUSTOM_BOT, null)) {
                            customBotValidationError = "";
                        } else {
                            customBotValidationError = gameText.translate(
                                    "gdx.avatar.invalid");
                        }
                    }
                }));
    }

    private void resetCustomBotAvatar() {
        if (customBotAvatarSelectionPending) return;
        customBotAvatarDraft = null;
        customBotValidationError = "";
    }

    private Texture customBotAvatarTexture(Path path) {
        if (path == null) return avatarBot;
        String key = path.toAbsolutePath().normalize().toString();
        Texture cached = customBotAvatarTextures.get(key);
        if (cached != null) return cached;
        try {
            Texture texture = GdxTextureQuality.loadUi(
                    Gdx.files.absolute(key));
            customBotAvatarTextures.put(key, texture);
            return texture;
        } catch (RuntimeException invalid) {
            return avatarBot;
        }
    }

    private void disposeCustomBotAvatarTextures() {
        customBotAvatarTextures.values().forEach(Texture::dispose);
        customBotAvatarTextures.clear();
    }

    private void drawCustomBotManagerDialog() {
        customBotManagerImages.clear();
        float x = 190f;
        float y = 55f;
        float w = 1540f;
        float h = 970f;
        outerBox(x, y, w, h, CYAN, PANEL);
        textFit(titleFont, gameText.translate("gdx.custom_bots.title"),
                WIDTH / 2f, 974f, GOLD, true, 1260f);
        textFit(smallFont, gameText.translate("gdx.custom_bots.subtitle"),
                WIDTH / 2f, 912f, MUTED, true, 1320f);
        float listX = 235f;
        float listY = 145f;
        float listW = 875f;
        float listH = 745f;
        outerBox(listX, listY, listW, listH, LINE,
                new Color(0x081525f2));
        textFit(uiFont, gameText.translate("gdx.custom_bots.catalog"),
                listX + 32f, 852f, GOLD, false, listW - 300f);

        float rowX = listX + 32f;
        float rowW = listW - 100f;
        float newButtonX = rowX + rowW - 220f;
        customBotAddButton(newButtonX, 820f, 220f, 54f,
                customBotProfiles.size()
                        < CustomBotProfileCatalog.MAX_PROFILES);
        tooltip(newButtonX, 820f, 220f, 54f,
                "tooltip.cfg.custom_bots.new");

        customBotScrollMaximum = customBotMaximumPixelScroll(
                customBotDisplayRowCount());
        customBotScrollTarget = MathUtils.clamp(customBotScrollTarget,
                0f, customBotScrollMaximum);
        customBotScroll = MathUtils.clamp(customBotScroll,
                0f, customBotScrollMaximum);
        customBotRowsViewport.set(rowX, CUSTOM_BOT_ROWS_BOTTOM, rowW,
                CUSTOM_BOT_ROWS_TOP - CUSTOM_BOT_ROWS_BOTTOM);
        int first = Math.max(0, (int) Math.floor(customBotScroll
                / CUSTOM_BOT_ROW_STRIDE));
        int last = Math.min(customBotProfiles.size(), (int) Math.ceil(
                (customBotScroll + customBotRowsViewport.height)
                        / CUSTOM_BOT_ROW_STRIDE) + 1);
        shapes.flush();
        enableWorldScissor(customBotRowsViewport);
        customBotRowsActive = true;
        int paintedRows = Math.max(customBotDisplayRowCount(),
                CUSTOM_BOT_VISIBLE_ROWS);
        for (int index = first; index < paintedRows; index++) {
            float rowY = 698f - index * CUSTOM_BOT_ROW_STRIDE
                    + customBotScroll;
            if (rowY >= CUSTOM_BOT_ROWS_TOP
                    || rowY + CUSTOM_BOT_ROW_HEIGHT
                            <= CUSTOM_BOT_ROWS_BOTTOM) continue;
            float hitBottom = Math.max(rowY, CUSTOM_BOT_ROWS_BOTTOM);
            float hitTop = Math.min(rowY + CUSTOM_BOT_ROW_HEIGHT,
                    CUSTOM_BOT_ROWS_TOP);
            if (index >= customBotProfiles.size()) {
                boolean selected = index == customBotNewSlotSelected;
                boolean over = hitTop > hitBottom
                        && hovered(rowX, hitBottom, rowW,
                                hitTop - hitBottom);
                if (selected || over) {
                    drawCustomBotRowBox(rowX, rowY, rowW,
                            CUSTOM_BOT_ROW_HEIGHT, selected, over,
                            new Color(0x17253a70));
                } else {
                    shapes.setColor(new Color(0x17253a70));
                    roundedRect(rowX, rowY, rowW,
                            CUSTOM_BOT_ROW_HEIGHT, 10f);
                }
                if (hitTop > hitBottom) {
                    int emptyRow = index;
                    hit(rowX, hitBottom, rowW, hitTop - hitBottom,
                            () -> newCustomBot(emptyRow));
                    tooltip(rowX, hitBottom, rowW, hitTop - hitBottom,
                            "tooltip.cfg.custom_bots.new");
                }
                continue;
            }
            if (index >= last) break;
            CustomBotProfile profile = customBotProfiles.get(index);
            boolean selected = index == customBotSelected;
            boolean over = hitTop > hitBottom
                    && hovered(rowX, hitBottom, rowW, hitTop - hitBottom);
            drawCustomBotRowBox(rowX, rowY, rowW, CUSTOM_BOT_ROW_HEIGHT,
                    selected, over,
                    selected ? new Color(0x17314af4) : PANEL_LIGHT);
            Texture avatar = customBotAvatarTexture(profile.avatar());
            customBotManagerListImages.add(new UiImageItem(avatar,
                    rowX + 12f, rowY + 9f, 76f, 76f));
            textFit(headingFont, profile.displayName(), rowX + 108f,
                    rowY + 56f, Color.WHITE, false, 340f);
            textFit(uiFont, switch (profile.difficulty()) {
                case EASY -> gameText.translate(
                        "gdx.settings.game.value.easy");
                case MEDIUM -> gameText.translate(
                        "gdx.custom_bots.difficulty.normal");
                case HARD -> gameText.translate(
                        "gdx.settings.game.value.hard");
            }, rowX + 532f, rowY + 72f, GOLD, true, 220f);
            textFit(smallFont,
                    customBotPersonalityLabel(profile.personality()),
                    rowX + 532f, rowY + 44f, CYAN, true, 245f);
            float orderX = rowX + rowW - 64f;
            outerBox(orderX, rowY + 22f, 52f, 52f,
                    selected ? GOLD : LINE,
                    selected ? new Color(0x2b3e5cff) : PANEL);
            textFit(smallFont, "#" + (index + 1),
                    orderX + 26f, rowY + 54f,
                    selected ? GOLD : MUTED, true, 42f);
            if (hitTop > hitBottom) {
                int selectedIndex = index;
                hit(rowX, hitBottom, rowW, hitTop - hitBottom,
                        () -> selectCustomBot(selectedIndex));
                tooltip(rowX, hitBottom, rowW, hitTop - hitBottom,
                        "tooltip.cfg.custom_bots.row");
            }
        }
        customBotRowsActive = false;
        shapes.flush();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);

        customBotScrollTrack.set(listX + listW - 30f,
                CUSTOM_BOT_ROWS_BOTTOM, GdxSettingsLayout.SCROLLBAR_WIDTH,
                customBotRowsViewport.height);
        customBotScrollThumbHeight = GdxSettingsLayout
                .pixelScrollbarThumbHeight(customBotScrollTrack.height,
                        customBotRowsViewport.height,
                        customBotContentHeight(customBotDisplayRowCount()));
        if (customBotScrollMaximum > 0f) {
            float ratio = customBotScroll / customBotScrollMaximum;
            float thumbY = customBotScrollTrack.y
                    + (customBotScrollTrack.height
                            - customBotScrollThumbHeight) * (1f - ratio);
            GdxSettingsStyle.drawRowScrollbar(shapes,
                    customBotScrollTrack.x, customBotScrollTrack.y,
                    customBotScrollTrack.height, thumbY,
                    customBotScrollThumbHeight, 1f);
        }

        compactToggle(rowX, 157f, 360f,
                gameText.translate("gdx.custom_bots.random_selection"),
                customBotRandomSelection,
                this::toggleCustomBotRandomSelection, true);
        tooltip(rowX, 157f, 360f,
                GdxSettingsLayout.ROW_HEIGHT,
                "tooltip.cfg.custom_bots.random_selection");
        float moveUpX = rowX + rowW - 330f;
        float moveDownX = rowX + rowW - 155f;
        compactVerticalArrowButton(moveUpX, 164f, 155f, 54f, true,
                () -> moveCustomBot(-1), customBotSelected > 0);
        compactVerticalArrowButton(moveDownX, 164f, 155f, 54f, false,
                () -> moveCustomBot(1), customBotSelected >= 0
                        && customBotSelected < customBotProfiles.size() - 1);
        tooltip(moveUpX, 164f, 155f, 54f,
                "tooltip.cfg.custom_bots.order_up");
        tooltip(moveDownX, 164f, 155f, 54f,
                "tooltip.cfg.custom_bots.order_down");

        float editX = 1140f;
        float editW = 545f;
        outerBox(editX, listY, editW, listH, LINE,
                new Color(0x081525f2));
        textFit(uiFont, customBotSelected < 0
                        ? gameText.translate("gdx.custom_bots.new")
                        : gameText.translate("gdx.custom_bots.edit"),
                editX + 24f, 852f, GOLD, false, editW - 48f);
        Texture editorAvatar = customBotAvatarTexture(customBotAvatarDraft);
        float editorAvatarCenterX = editX + editW / 2f;
        float editorAvatarCenterY = 790f;
        float editorAvatarHitX = editorAvatarCenterX - 75f;
        float editorAvatarHitY = editorAvatarCenterY - 77.5f;
        boolean editorAvatarHover = !customBotAvatarSelectionPending
                && hovered(editorAvatarHitX, editorAvatarHitY, 150f, 155f);
        shapes.setColor(customBotAvatarSelectionPending ? DISABLED
                : editorAvatarHover ? CYAN : CYAN_DARK);
        GdxAntialiasedRoundedRect.fillCircle(shapes,
                editorAvatarCenterX, editorAvatarCenterY, 70f);
        shapes.setColor(PANEL_LIGHT);
        GdxAntialiasedRoundedRect.fillCircle(shapes,
                editorAvatarCenterX, editorAvatarCenterY, 61f);
        customBotManagerImages.add(new UiImageItem(editorAvatar,
                editorAvatarCenterX - 61f, editorAvatarCenterY - 61f,
                122f, 122f));
        hit(editorAvatarHitX, editorAvatarHitY, 150f, 155f,
                this::selectCustomBotAvatar);
        secondaryHit(editorAvatarHitX, editorAvatarHitY, 150f, 155f,
                this::resetCustomBotAvatar);
        tooltip(editorAvatarHitX, editorAvatarHitY, 150f, 155f,
                "tooltip.change_avatar");

        field(editX + 42f, 610f, editW - 84f,
                gameText.translate("gdx.custom_bots.name"),
                customBotNameDraft, "customBotName", false,
                customBotValidationError.isBlank());
        if (!customBotValidationError.isBlank()) {
            textFit(tinyFont, customBotValidationError,
                    editX + 42f, 580f, ORANGE, false, editW - 112f);
        }
        tooltip(editX + 42f, 610f, editW - 84f, 70f,
                "tooltip.cfg.custom_bots.name");

        dropdownChoice(editX + 42f, 465f, editW - 84f,
                gameText.translate("gdx.custom_bots.difficulty"),
                customBotDifficultyLabel(),
                () -> openDropdown(Dropdown.CUSTOM_BOT_DIFFICULTY,
                        editX + 42f, 465f, editW - 84f, 72f), true);
        tooltip(editX + 42f, 465f, editW - 84f, 102f,
                "tooltip.cfg.custom_bots.difficulty");
        dropdownChoice(editX + 42f, 340f, editW - 84f,
                gameText.translate("gdx.custom_bots.personality"),
                customBotPersonalityLabel(customBotPersonalityDraft),
                () -> openDropdown(Dropdown.CUSTOM_BOT_PERSONALITY,
                        editX + 42f, 340f, editW - 84f, 72f), true);
        tooltip(editX + 42f, 340f, editW - 84f, 102f,
                "tooltip.cfg.custom_bots.personality");

        boolean globalBotRebuy = customBotGlobalRebuyEnabled();
        compactToggle(editX + 42f, 235f, editW - 84f,
                gameText.translate("gdx.custom_bots.automatic_rebuy"),
                globalBotRebuy && customBotAutomaticRebuyDraft,
                this::toggleCustomBotAutomaticRebuy, globalBotRebuy);
        tooltip(editX + 42f, 235f, editW - 84f,
                GdxSettingsLayout.ROW_HEIGHT,
                "tooltip.cfg.custom_bots.automatic_rebuy");

        float saveButtonX = editX + 42f;
        float saveButtonW = 220f;
        themedButton(saveButtonX, 158f, saveButtonW, 54f,
                gameText.translate("gdx.custom_bots.save"),
                ButtonTone.POSITIVE, this::saveCustomBot,
                customBotSaveEnabled(), smallFont);
        float deleteButtonX = saveButtonX + saveButtonW + 16f;
        float deleteButtonW = 225f;
        themedButton(deleteButtonX, 158f, deleteButtonW, 54f,
                gameText.translate("gdx.custom_bots.delete"),
                ButtonTone.DANGER, this::openCustomBotDeleteConfirmation,
                customBotSelected >= 0, smallFont);
        tooltip(saveButtonX, 158f, saveButtonW, 54f,
                "tooltip.cfg.custom_bots.save");
        tooltip(deleteButtonX, 158f, deleteButtonW, 54f,
                "tooltip.cfg.custom_bots.delete");

        button(785f, 72f, 350f, 60f,
                gameText.translate("gdx.custom_bots.close"), true,
                this::closeCustomBotManager);
        tooltip(785f, 72f, 350f, 60f,
                "tooltip.cfg.custom_bots.close");
    }

    private void customBotAddButton(float x, float y, float w, float h,
            boolean enabled) {
        themedButton(x, y, w, h, "", ButtonTone.NEUTRAL,
                this::newCustomBot, enabled, smallFont);
        GdxUiButtonStyle.Tone tone = GdxUiButtonStyle.Tone.NEUTRAL;
        Color color = GdxUiButtonStyle.labelColor(tone, enabled);
        crispButtonTextInBox(smallFont,
                gameText.translate("gdx.custom_bots.new_button"),
                x + 10f, y, w - 58f, h, color);
        GdxUiIconRenderer.draw(shapes, GdxUiIconRenderer.Icon.ADD,
                x + w - 28f, y + h / 2f, 19f, color, 1f);
    }

    private void compactVerticalArrowButton(float x, float y, float w,
            float h, boolean up, Runnable action, boolean enabled) {
        themedButton(x, y, w, h, "", ButtonTone.NEUTRAL, action,
                enabled, smallFont);
        Color color = GdxUiButtonStyle.labelColor(
                GdxUiButtonStyle.Tone.NEUTRAL, enabled);
        float cx = x + w / 2f;
        float cy = y + h / 2f;
        shapes.setColor(color);
        if (up) {
            shapes.triangle(cx, cy + 10f, cx - 11f, cy - 7f,
                    cx + 11f, cy - 7f);
        } else {
            shapes.triangle(cx, cy - 10f, cx - 11f, cy + 7f,
                    cx + 11f, cy + 7f);
        }
    }

    private void openCustomBotDeleteConfirmation() {
        if (customBotSelected < 0
                || customBotSelected >= customBotProfiles.size()) return;
        customBotDeleteConfirmation = true;
        clearActiveField();
        clearScrollDrag();
    }

    private void drawCustomBotManagerImages() {
        if (customBotManagerImages.isEmpty()) return;
        batch.setShader(avatarShader);
        for (UiImageItem item : customBotManagerImages) {
            batch.setColor(item.tint);
            batch.draw(item.texture, item.x, item.y,
                    item.width, item.height);
        }
        batch.flush();
        batch.setShader(null);
        batch.setColor(Color.WHITE);
    }

    private void drawCustomBotManagerListLayer() {
        if (customBotManagerListImages.isEmpty()
                && customBotManagerListTexts.isEmpty()) return;
        enableWorldScissor(customBotRowsViewport);
        batch.begin();
        if (!customBotManagerListImages.isEmpty()) {
            batch.setShader(avatarShader);
            for (UiImageItem item : customBotManagerListImages) {
                batch.setColor(item.tint);
                batch.draw(item.texture, item.x, item.y,
                        item.width, item.height);
            }
            batch.flush();
            batch.setShader(null);
        }
        batch.setColor(Color.WHITE);
        for (TextItem item : customBotManagerListTexts) drawTextItem(item);
        batch.end();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
    }

    private void drawCustomBotDeleteConfirmationTopLayer() {
        if (!customBotDeleteConfirmation) return;
        if (customBotSelected < 0
                || customBotSelected >= customBotProfiles.size()) {
            customBotDeleteConfirmation = false;
            return;
        }
        hits.clear();
        secondaryHits.clear();
        textFieldHits.clear();
        passwordRevealHits.clear();
        editMenuHits.clear();
        tooltipHits.clear();
        texts.clear();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA,
                GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        GdxUiDialogStyle.ConfirmationLayout layout = drawConfirmationChrome(
                gameText.translate("gdx.custom_bots.delete_title"),
                gameText.translate("gdx.custom_bots.delete_confirm",
                        customBotProfiles.get(customBotSelected)
                                .displayName()));
        Rectangle cancel = layout.negativeButton();
        Rectangle accept = layout.positiveButton();
        themedButton(cancel.x, cancel.y, cancel.width, cancel.height,
                gameText.translate("ui.cancelar"), ButtonTone.NEUTRAL,
                () -> customBotDeleteConfirmation = false, true);
        themedButton(accept.x, accept.y, accept.width, accept.height,
                gameText.translate("gdx.custom_bots.delete"),
                ButtonTone.DANGER, this::deleteCustomBot, true);
        shapes.end();
        batch.begin();
        for (TextItem item : texts) drawTextItem(item);
        batch.end();
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
                uppercase(gameText.translate("ui.cancelar")),
                ButtonTone.NEUTRAL,
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
        if (connection.mode() == NewGameConnectionDraft.Mode.CREATE
                && connection.upnp()
                && (verifiedUpnpPort != Integer.parseInt(connection.port())
                        || upnpReservation == null
                        || !upnpReservation.active())) {
            startUpnpPortOpen(true);
            return;
        }
        submitNewGameAfterChecks();
    }

    private void submitNewGameAfterChecks() {
        UpnpPortReservation reservation = connection != null
                && connection.upnp() ? upnpReservation : null;
        try {
            List<CustomBotProfile> profiles = connection.recoverRequested()
                    || table.customBots() ? List.copyOf(customBotProfiles)
                            : List.of();
            if (!connection.recoverRequested() && table.customBots()) {
                profiles = CustomBotProfileCatalog.profilesForSession(
                        profiles, customBotRandomSelection, secureRandom);
            }
            CompletableFuture<NewGameSubmissionCoordinator.OpenedSession> opening
                    = submissions.submit(connection, table, reservation,
                            profiles);
            if (reservation != null) upnpReservation = null;
            opening.whenComplete((request, failure) ->
                    Gdx.app.postRunnable(() -> completeSubmission(request, failure)));
        } catch (RuntimeException failure) {
            if (reservation != null) {
                upnpReservation = null;
                connection.setUpnp(false);
                verifiedUpnpPort = -1;
            }
            showFrontendNotice(gameText.translate("gdx.dialog.error"),
                    submissionError(failure), true);
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
            if (connection != null) connection.setUpnp(false);
            verifiedUpnpPort = -1;
            activateSurface(Surface.MENU);
            clearActiveField();
        } else {
            if (connection != null) connection.setUpnp(false);
            verifiedUpnpPort = -1;
            showFrontendNotice(gameText.translate("gdx.dialog.error"),
                    submissionError(cause), true);
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
        cancelAndReleaseUpnp();
        dismissFrontendNotice();
        recoveryLoadGeneration++;
        autoSubmitRecovery = false;
        connection.setRecoverRequested(false);
        table.setEconomyLocked(false);
        activateSurface(Surface.MENU);
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof CompletionException completion
                && completion.getCause() != null ? completion.getCause() : failure;
    }

    private String submissionError(Throwable failure) {
        LobbyConnectionRefusedException refusal
                = lobbyConnectionRefusal(failure);
        if (refusal != null) {
            return switch (refusal.reason()) {
                case BAD_VERSION -> gameText.translate(
                    "gdx.lobby.reject.bad_version");
                case TABLE_STARTED -> gameText.translate(
                    "gdx.lobby.reject.table_started");
                case HOT_JOIN_DISABLED -> gameText.translate(
                    "gdx.lobby.reject.hot_join_disabled");
                case TABLE_FULL -> gameText.translate(
                    "gdx.lobby.reject.table_full");
                case NICK_TAKEN -> gameText.translate(
                    "gdx.lobby.reject.nick_taken");
                case NICK_UNAUTHORIZED -> gameText.translate(
                    "gdx.lobby.reject.nick_unauthorized");
                case IDENTITY_MISMATCH -> gameText.translate(
                    "gdx.lobby.reject.identity_mismatch");
                case HOT_JOIN_THROTTLED -> gameText.translate(
                    "gdx.lobby.reject.hot_join_throttled",
                    Math.max(1L, refusal.retryAfterSeconds()));
                case CONNECTION_ABUSE_BLOCKED -> gameText.translate(
                    "gdx.lobby.reject.connection_blocked",
                    Math.max(1L, refusal.retryAfterSeconds()));
                case SERVER_REJECTED -> gameText.translate(
                    "gdx.lobby.reject.server_rejected");
            };
        }
        String translatedKey = submissionErrorKey(failure);
        if (!translatedKey.isEmpty()) return gameText.translate(translatedKey);
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? gameText.translate("gdx.lobby.open_failed") : message;
    }

    static String submissionErrorKey(Throwable failure) {
        Throwable cause = failure;
        while (cause != null) {
            if (cause instanceof BindException) {
                return "gdx.lobby.port_in_use";
            }
            if (cause instanceof UnknownHostException) {
                return "gdx.lobby.unknown_host";
            }
            if (cause instanceof NoRouteToHostException) {
                return "gdx.lobby.no_route";
            }
            if (cause instanceof SocketTimeoutException) {
                return "gdx.lobby.connection_timeout";
            }
            if (cause instanceof ConnectException) {
                return "gdx.lobby.connection_refused";
            }
            cause = cause.getCause();
        }
        return "";
    }

    static LobbyConnectionRefusedException lobbyConnectionRefusal(
            Throwable failure) {
        Throwable cause = failure;
        while (cause != null) {
            if (cause instanceof LobbyConnectionRefusedException refusal) {
                return refusal;
            }
            cause = cause.getCause();
        }
        return null;
    }

    private void drawToastTopLayer() {
        GdxTransientNoticeLayout.drawCentered(shapes, batch, viewport, uiFont,
                glyph, nativeTextTiers(uiFont), crispButtonText, toast, ORANGE, 1f);
    }

    private void drawScreenshotToastTopLayer() {
        float alpha = MathUtils.clamp((screenshotToastUntil - elapsed)
                / GdxTransientNoticeLayout.FADE_SECONDS, 0f, 1f);
        GdxTransientNoticeLayout.drawCentered(shapes, batch, viewport, uiFont,
                glyph, nativeTextTiers(uiFont), crispButtonText, screenshotToast, ORANGE, alpha);
    }

    private void drawVolumeOverlayTopLayer() {
        GdxVolumeOverlayStyle.draw(shapes, batch, viewport, volumeOverlayFont,
                glyph, soundIcon, muteIcon, masterVolume(), audioOutputAvailable());
    }

    /** Global counter shared by every non-table GDX surface and modal. */
    private void drawFrontendFpsCounter() {
        GdxFrameRateOverlayStyle.draw(shapes, batch, fpsFont, glyph,
                presentationSettings.frameRateOverlayLabel(
                        Gdx.graphics.getFramesPerSecond()),
                WIDTH, HEIGHT, viewport);
    }

    private void drawFrontendPerformanceCounter() {
        GdxFrameRateOverlayStyle.drawPerformance(shapes, batch, fpsFont,
                glyph, performanceSampler.label(gameText, System.nanoTime()),
                WIDTH, HEIGHT, viewport);
    }

    private void drawFittedCenteredInBox(BitmapFont font, String text,
            float x, float y, float width, float height,
            Color color, float alpha) {
        GdxCrispButtonText.resolveWithinBox(glyph, text, width, height,
                nativeTextTiers(font), crispButtonText);
        BitmapFont selected = crispButtonText.font();
        selected.setColor(color.r, color.g, color.b, alpha);
        glyph.setText(selected, crispButtonText.text());
        GdxPixelText.draw(selected, batch, glyph,
                x + (width - glyph.width) / 2f,
                y + (height + glyph.height) / 2f, viewport);
        selected.setColor(Color.WHITE);
    }

    private void panel(float x, float y, float w, float h, String title) {
        outerBox(x, y, w, h, LINE, PANEL);
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
        field(x, y, w, label, value, id, secret, true);
    }

    private void field(float x, float y, float w, String label, String value,
            String id, boolean secret, boolean valid) {
        boolean focused = id.equals(activeField);
        boolean revealed = secret && id.equals(revealedPasswordField);
        textFit(smallFont, label, x, y + 98f, MUTED, false,
                Math.max(0f, w));
        Color border = !valid ? ORANGE
                : focused || hovered(x, y, w, 70f) ? CYAN : LINE;
        outerBox(x, y, w, 70f, border,
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
        GdxUiButtonStyle.draw(shapes, bounds.x, bounds.y,
                bounds.width, bounds.height,
                revealed ? GdxUiButtonStyle.Tone.FEATURED
                        : GdxUiButtonStyle.Tone.NEUTRAL,
                true, over ? 1f : 0f,
                pressed(bounds.x, bounds.y, bounds.width, bounds.height),
                1f);
        Color color = revealed ? GOLD : over ? CYAN : MUTED;
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
        return editMenu != null && (!childModalOwnsInput()
                || frontendModalTextFieldActive());
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

    private void gameSettingTooltip(float x, float y, float w, float h,
            String settingKey) {
        tooltip(x, y, w, h, GdxSettingsTooltips.descriptionKey(settingKey));
    }

    private void settingsTooltip(Rectangle bounds, String settingKey) {
        settingsTooltip(bounds, settingKey, false);
    }

    private void settingsTooltip(Rectangle bounds, String settingKey,
            boolean modOverride) {
        settingsTooltip(bounds, settingKey, modOverride,
                GdxGameAudioResources.JokeBankMode.OFFICIAL);
    }

    private void settingsTooltip(Rectangle bounds, String settingKey,
            boolean modOverride,
            GdxGameAudioResources.JokeBankMode jokeBankMode) {
        Rectangle visible = clippedSettingsHit(bounds.x, bounds.y,
                bounds.width, bounds.height);
        if (visible == null) return;
        GdxSettingsTooltips.Content content = GdxSettingsTooltips.content(
                settingKey, initialProperties, gameText, modOverride,
                jokeBankMode);
        tooltipHits.add(new TooltipHit(visible,
                "gdx.settings.tooltip.text",
                new Object[]{content.text()}, content.performanceImpact()));
    }

    private void drawTooltipTopLayer(Rectangle popup) {
        TooltipHit hovered = popup == null ? null : dropdownOptionTooltip;
        for (int index = tooltipHits.size() - 1; hovered == null && index >= 0;
                index--) {
            TooltipHit hit = tooltipHits.get(index);
            if (hit.bounds.contains(pointer) && (popup == null
                    || dropdownTooltipOwnsPointer(hit.bounds, dropdownAnchor,
                            popup, pointer))) {
                hovered = hit;
                break;
            }
        }
        if (hovered == null) {
            tooltipDelay.clear();
            return;
        }
        if (!tooltipDelay.ready(hovered.identity())) return;
        String value = gameText.translate(hovered.key, hovered.arguments);
        int maxLines = (int) ((HEIGHT - 48f) / 22f);
        List<String> lines = wrapText(tinyFont, value, 430f, maxLines);
        float widest = 0f;
        for (String line : lines) widest = Math.max(widest,
                textWidth(tinyFont, line));
        float boxW = Math.min(470f, Math.max(190f, widest + 34f));
        float boxH = 24f + lines.size() * 22f;
        Rectangle bounds = GdxSettingsTooltips.bounds(pointer, boxW, boxH,
                WIDTH, HEIGHT, popup);
        float x = bounds.x;
        float y = bounds.y;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Color accent = performanceImpactColor(hovered.performanceImpact);
        outerBox(x, y, boxW, boxH,
                hovered.performanceImpact
                        == GdxSettingsContract.PerformanceImpact.NONE
                                ? CYAN_DARK : accent,
                GdxUiDialogStyle.TOOLTIP_FILL);
        shapes.end();
        batch.begin();
        tinyFont.setColor(hovered.performanceImpact
                == GdxSettingsContract.PerformanceImpact.NONE
                        ? Color.WHITE : accent);
        float baseline = y + boxH - 16f;
        for (String line : lines) {
            GdxPixelText.draw(tinyFont, batch, line, x + 17f, baseline,
                    viewport);
            baseline -= 22f;
        }
        batch.end();
    }

    static boolean dropdownTooltipOwnsPointer(Rectangle hit, Rectangle anchor,
            Rectangle popup, Vector2 pointer) {
        return hit.contains(anchor.x + anchor.width / 2f,
                anchor.y + anchor.height / 2f) && !popup.contains(pointer);
    }

    private static Color performanceImpactColor(
            GdxSettingsContract.PerformanceImpact impact) {
        return switch (impact) {
            case LOW -> LATENCY_GREEN;
            case MEDIUM -> LATENCY_YELLOW;
            case HIGH -> LATENCY_RED;
            case NONE -> CYAN_DARK;
        };
    }

    private void toggle(float x, float y, float w, String label,
            boolean value, Runnable action, boolean enabled) {
        toggle(x, y, w, label, value, action, enabled, smallFont);
    }

    private void settingsValueToggle(float x, float y, float w, String label,
            String valueLabel, boolean value, Runnable action,
            boolean enabled) {
        toggle(x, y, w, uppercase(label), valueLabel, value, action, enabled,
                smallFont, false, 0f);
    }

    private void settingsBooleanToggle(float x, float y, float w,
            String label, boolean value, boolean factoryDefault,
            Runnable action, boolean enabled) {
        settingsBooleanToggle(x, y, w, label, value, factoryDefault,
                action, enabled, false);
    }

    private void settingsBooleanToggle(float x, float y, float w,
            String label, boolean value, boolean factoryDefault,
            Runnable action, boolean enabled, boolean reserveAudioPreview) {
        settingsBooleanToggle(x, y, w, label, value, factoryDefault, action,
                enabled, reserveAudioPreview, null);
    }

    private void settingsBooleanToggle(float x, float y, float w,
            String label, boolean value, boolean factoryDefault,
            Runnable action, boolean enabled, boolean reserveAudioPreview,
            String semanticAnimationKey) {
        toggle(x, y, w, uppercase(label),
                GdxSettingsContract.booleanStatusLabel(value,
                        factoryDefault, gameText),
                value, action, enabled, smallFont, false,
                reserveAudioPreview ? 94f : 0f, semanticAnimationKey);
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
        toggle(x, y, w, label, null, value, action, enabled, labelFont,
                wrapLabel);
    }

    private void toggle(float x, float y, float w, String label,
            String valueLabel, boolean value, Runnable action,
            boolean enabled, BitmapFont labelFont, boolean wrapLabel) {
        toggle(x, y, w, label, valueLabel, value, action, enabled,
                labelFont, wrapLabel, 0f);
    }

    private void toggle(float x, float y, float w, String label,
            String valueLabel, boolean value, Runnable action,
            boolean enabled, BitmapFont labelFont, boolean wrapLabel,
            float trailingValueReserve) {
        toggle(x, y, w, label, valueLabel, value, action, enabled, labelFont,
                wrapLabel, trailingValueReserve, null);
    }

    private void toggle(float x, float y, float w, String label,
            String valueLabel, boolean value, Runnable action,
            boolean enabled, BitmapFont labelFont, boolean wrapLabel,
            float trailingValueReserve, String semanticAnimationKey) {
        GdxSettingsStyle.drawRow(shapes, x, y, w,
                GdxSettingsLayout.ROW_HEIGHT, enabled,
                enabled && hovered(x, y, w, GdxSettingsLayout.ROW_HEIGHT),
                enabled && pressed(x, y, w, GdxSettingsLayout.ROW_HEIGHT),
                1f);
        GdxSettingsLayout.ToggleRow layout = valueLabel == null ? null
                : GdxSettingsLayout.toggleRow(x, y, w,
                        GdxSettingsLayout.ROW_HEIGHT,
                        trailingValueReserve);
        float labelWidth = layout == null ? Math.max(0f, w - 132f)
                : layout.label().width;
        boolean modOverrideLabel = GdxModSettingsIndicator.marked(label);
        Color rowLabelColor = modOverrideLabel
                ? enabled ? MOD_OVERRIDE : MOD_OVERRIDE_DISABLED
                : enabled ? Color.WHITE : DISABLED;
        if (wrapLabel) {
            List<String> lines = wrapText(labelFont, label, labelWidth, 2);
            float baseline = lines.size() > 1 ? y + 48f : y + 42f;
            for (String line : lines) {
                textFit(labelFont, line, x + 22f, baseline,
                        rowLabelColor, false, labelWidth);
                baseline -= 24f;
            }
        } else {
            textFit(labelFont, label, x + 22f,
                    GdxSettingsLayout.rowTextBaseline(y),
                    rowLabelColor, false, labelWidth);
        }
        if (valueLabel != null) {
            textFit(uiFont, valueLabel, layout.value().x
                    + layout.value().width / 2f,
                    GdxSettingsLayout.rowTextBaseline(y),
                    enabled ? GOLD : DISABLED, true,
                    layout.value().width - 12f);
        }
        float target = value && enabled ? 1f : 0f;
        float animation;
        if (semanticAnimationKey != null) {
            animation = settingsToggleTransitions.sample(
                    semanticAnimationKey, target, System.nanoTime());
        } else {
            String animationKey = GdxToggleMotion.stableKey(
                    toggleAnimationContext(), label, x, y, w, wrapLabel);
            animation = toggleAnimations.getOrDefault(animationKey, target);
            animation = GdxToggleMotion.nextForToggle(animation, value,
                    enabled, frameDelta);
            toggleAnimations.put(animationKey, animation);
        }
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
        stepper(x, y, w, label, Integer.toString(value), minus, plus, enabled);
    }

    private void stepper(float x, float y, float w, String label, String value,
            Runnable minus, Runnable plus, boolean enabled) {
        textFit(smallFont, label, x, y + 99f,
                enabled ? MUTED : DISABLED, false, Math.max(0f, w));
        GdxSettingsStyle.drawRow(shapes, x, y, w, 72f, enabled,
                enabled && hovered(x, y, w, 72f),
                enabled && pressed(x, y, w, 72f), 1f);
        float side = 70f;
        embeddedButtonSurface(x, y, side + 3f, 72f, enabled);
        embeddedButtonSurface(x + w - side - 3f, y, side + 3f, 72f,
                enabled);
        text(headingFont, "-", x + 38f, y + 48f, enabled ? Color.WHITE : DISABLED, true);
        text(headingFont, "+", x + w - 38f, y + 48f, enabled ? Color.WHITE : DISABLED, true);
        textFit(headingFont, value, x + w / 2f, y + 49f,
                enabled ? GOLD : DISABLED, true,
                Math.max(0f, w - 2f * side - 24f));
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
        GdxSettingsStyle.drawRow(shapes, x, y, w, height, enabled,
                enabled && hovered(x, y, w, height),
                enabled && pressed(x, y, w, height), 1f);
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
        GdxSettingsStyle.drawRow(shapes, x, y, w, h, enabled,
                enabled && hovered(x, y, w, h),
                enabled && pressed(x, y, w, h), 1f);
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
        GdxSettingsStyle.drawRow(shapes, x, y, w, h, enabled,
                enabled && hovered(x, y, w, h),
                enabled && pressed(x, y, w, h), 1f);
        textFit(uiFont, value, x + 22f, y + 46f,
                enabled ? Color.WHITE : DISABLED, false, w - 92f);
        shapes.setColor(enabled ? GOLD : DISABLED);
        float cx = x + w - 38f;
        float cy = y + 39f;
        shapes.rectLine(cx - 12f, cy + 6f, cx, cy - 6f, 3f);
        shapes.rectLine(cx, cy - 6f, cx + 12f, cy + 6f, 3f);
        if (enabled) hit(x, y, w, h, action);
    }

    private void openDropdown(Dropdown target, float x, float y,
            float width, float height) {
        dropdownAnchor.set(x, y, width, height);
        dropdownAnchored = true;
        prepareDropdown(target);
    }

    private void prepareDropdown(Dropdown target) {
        dropdown = Objects.requireNonNull(target, "target");
        int selected = selectedDropdownOption(target);
        int count = dropdownOptions(target).size();
        dropdownScroll = MathUtils.clamp(selected - 2, 0,
                Math.max(0, count
                        - GdxSettingsLayout.POPUP_MAX_VISIBLE_ROWS));
        clearActiveField();
    }

    private int selectedDropdownOption(Dropdown target) {
        return switch (target) {
            case PROFILE -> selectedGamePreset + 1;
            case BLIND_STRUCTURE -> selectedBlindStructureOption();
            case BLIND_LEVEL -> dropdownTableTarget().blindLevelIndex();
            case BLIND_STRUCTURE_EDITOR ->
                blindStructureEditor.selectedStructureIndex();
            case DECK -> presentationSettings.availableDecks().indexOf(
                    presentationSettings.deck());
            case CARD_BACK -> presentationSettings.availableCardBacks()
                    .indexOf(presentationSettings.cardBack());
            case FELT -> GdxGamePresentationSettings.FELTS.indexOf(
                    presentationSettings.felt());
            case HOST_ADDRESS -> selectedHostAddressOption();
            case SERVER_HISTORY -> selectedServerHistoryOption();
            case LANGUAGE -> "en".equalsIgnoreCase(
                    presentationSettings.language()) ? 1 : 0;
            case AUDIO_OUTPUT -> GdxAudioDevices.selectedOutputIndex(
                    initialProperties);
            case AUDIO_CAPTURE -> GdxAudioDevices.selectedCaptureIndex(
                    initialProperties);
            case VOICE_RETENTION -> GdxSettingsContract.voiceRetentionIndex(
                    initialProperties);
            case WINDOW_MODE -> GdxWindowMode.configured(initialProperties)
                    .ordinal();
            case FPS_LIMIT -> presentationSettings.frameRateLimits().indexOf(
                    presentationSettings.requestedFrameRateLimit());
            case MSAA -> presentationSettings.msaaLevels().indexOf(
                    presentationSettings.requestedMsaaSamples());
            case CARD_RENDERING -> presentationSettings.cardRenderingModes()
                    .indexOf(presentationSettings.requestedCardRenderingMode());
            case APPEARANCE_CHOICE -> dropdownAppearanceChoice == null ? -1
                    : GdxAppearanceOptions.selectedIndex(
                            dropdownAppearanceChoice, initialProperties);
            case ANTE_MODE -> dropdownTableTarget().anteMode().ordinal();
            case BLIND_UNIT -> dropdownTableTarget().blindIncreaseType()
                    .ordinal();
            case REBUY_CAP -> dropdownTableTarget().rebuyCapPolicy().ordinal();
            case BOT_DIFFICULTY -> dropdownTableTarget().botDifficulty()
                    .ordinal();
            case RABBIT -> dropdownTableTarget().rabbitHunting().ordinal();
            case CUSTOM_BOT_DIFFICULTY ->
                customBotDifficultyDraft.ordinal();
            case CUSTOM_BOT_PERSONALITY ->
                customBotPersonalityDraft.ordinal();
            case NONE -> -1;
        };
    }

    private int selectedBlindStructureOption() {
        NewGameTableDraft target = blindStructureDropdownTarget();
        if (target == null || target.structureName() == null) return 0;
        List<BlindStructureCatalog.Entry> saved = BlindStructureCatalog.read(
                initialProperties);
        for (int index = 0; index < saved.size(); index++) {
            if (saved.get(index).name().equals(target.structureName())) {
                return index + 1;
            }
        }
        return 0;
    }

    private int selectedHostAddressOption() {
        int selected = hostAddressOptions.indexOf(connection.server());
        return Math.max(0, selected);
    }

    private int selectedServerHistoryOption() {
        List<String> history = connection == null
                ? List.of() : connection.serverHistory();
        if (history.isEmpty()) return -1;
        return historyIndex < 0 ? history.size() - 1
                : MathUtils.clamp(historyIndex, 0, history.size() - 1);
    }

    private void refreshHostAddressOptions() {
        int generation = ++hostAddressLoadGeneration;
        NewGameConnectionDraft target = connection;
        String preferred = target.server();
        hostAddressOptions = List.of(GdxLocalServerAddresses.LOOPBACK_NAME);
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
                        if (Objects.equals(target.server(), preferred)
                                && !hostAddressOptions.contains(preferred)) {
                            target.setServer(GdxLocalServerAddresses.LOOPBACK_NAME);
                        }
                    });
                });
    }

    private List<String> dropdownOptions(Dropdown target) {
        return switch (target) {
            case HOST_ADDRESS -> hostAddressOptions;
            case SERVER_HISTORY -> connection == null ? List.of()
                    : connection.serverHistory();
            case BLIND_LEVEL -> dropdownTableTarget().blindLevels().stream()
                    .map(GdxFrontendScreen::blindLevelLabel).toList();
            case BLIND_STRUCTURE_EDITOR -> blindStructureEditor.entries()
                    .stream().map(BlindStructureCatalog.Entry::name).toList();
            case DECK -> presentationSettings.availableDecks().stream()
                    .map(deck -> GdxAppearanceOptions.deckLabel(deck,
                            gameText)).toList();
            case CARD_BACK -> presentationSettings.availableCardBacks().stream()
                    .map(back -> GdxModSettingsIndicator.label(
                            GdxAppearanceOptions.cardBackLabel(back, gameText),
                            GdxModSettingsIndicator.cardBack(
                                    presentationSettings, back))).toList();
            case FELT -> GdxGamePresentationSettings.FELTS.stream()
                    .map(felt -> GdxAppearanceOptions.feltLabel(felt,
                            gameText)).toList();
            case LANGUAGE -> List.of("Español", "English");
            case AUDIO_OUTPUT -> GdxAudioDevices.outputChoices().stream()
                    .map(device -> GdxAudioDevices.deviceLabel(device,
                            gameText)).toList();
            case AUDIO_CAPTURE -> GdxAudioDevices.captureChoices().stream()
                    .map(device -> GdxAudioDevices.deviceLabel(device,
                            gameText)).toList();
            case VOICE_RETENTION -> GdxSettingsContract
                    .voiceRetentionOptions().stream()
                    .map(days -> GdxSettingsContract
                            .voiceRetentionOptionLabel(days, gameText))
                    .toList();
            case WINDOW_MODE -> Arrays.stream(GdxWindowMode.values())
                    .map(mode -> mode.label(gameText)).toList();
            case FPS_LIMIT -> presentationSettings.frameRateLimits().stream()
                    .map(limit -> GdxSettingsContract.frameRateLimitLabel(
                            limit, gameText)).toList();
            case MSAA -> presentationSettings.msaaLevels().stream()
                    .map(samples -> GdxSettingsContract.msaaStatusLabel(
                            samples, samples, gameText)).toList();
            case CARD_RENDERING -> presentationSettings.cardRenderingModes()
                    .stream().map(mode -> GdxSettingsContract
                            .cardRenderingStatusLabel(mode, gameText)).toList();
            case APPEARANCE_CHOICE -> dropdownAppearanceChoice == null
                    ? List.of() : java.util.stream.IntStream.range(0,
                            dropdownAppearanceChoice.values().size())
                            .mapToObj(index -> GdxAppearanceOptions.labelAt(
                                    dropdownAppearanceChoice, index, gameText))
                            .toList();
            case ANTE_MODE -> GdxSettingsContract.anteModeLabels(gameText);
            case BLIND_UNIT -> List.of(
                    gameText.translate("gdx.settings.game.value.minutes"),
                    gameText.translate("gdx.settings.game.value.hands"));
            case REBUY_CAP -> List.of("BUY-IN",
                    settingsGameText("value.highest_stack"));
            case BOT_DIFFICULTY, CUSTOM_BOT_DIFFICULTY -> List.of(
                    gameText.translate("gdx.settings.game.value.easy"),
                    target == Dropdown.CUSTOM_BOT_DIFFICULTY
                            ? gameText.translate(
                                    "gdx.custom_bots.difficulty.normal")
                            : gameText.translate(
                                    "gdx.settings.game.value.medium"),
                    gameText.translate("gdx.settings.game.value.hard"));
            case RABBIT -> Arrays.stream(
                    NewGameTableDraft.RabbitHunting.values())
                    .map(this::rabbitLabel).toList();
            case CUSTOM_BOT_PERSONALITY -> Arrays.stream(
                    CustomBotProfile.Personality.values())
                    .map(this::customBotPersonalityLabel).toList();
            case PROFILE -> {
                ArrayList<String> values = new ArrayList<>();
                values.add(gameText.translate("newgame.preset_por_defecto"));
                gamePresets.forEach(entry -> values.add(entry.name()));
                yield List.copyOf(values);
            }
            case BLIND_STRUCTURE -> {
                ArrayList<String> values = new ArrayList<>();
                values.add(gameText.translate("gdx.settings.value.default"));
                BlindStructureCatalog.read(initialProperties)
                        .forEach(entry -> values.add(entry.name()));
                yield List.copyOf(values);
            }
            case NONE -> List.of();
        };
    }

    private void selectDropdownOption(Dropdown target, int option) {
        switch (target) {
            case PROFILE -> selectGamePresetOption(option);
            case HOST_ADDRESS -> {
                List<String> values = hostAddressOptions;
                if (option >= 0 && option < values.size()) {
                    connection.setServer(values.get(option));
                    connection.persistLocalHostEndpoint(preferences);
                }
            }
            case SERVER_HISTORY -> selectServerHistoryOption(option);
            case BLIND_STRUCTURE -> selectBlindStructureOption(option);
            case BLIND_LEVEL -> selectBlindLevelOption(option);
            case BLIND_STRUCTURE_EDITOR ->
                selectBlindStructureEditorOption(option);
            case DECK -> selectDeckOption(option);
            case CARD_BACK -> selectCardBackOption(option);
            case FELT -> selectFeltOption(option);
            case LANGUAGE -> selectLanguageOption(option);
            case AUDIO_OUTPUT -> {
                List<String> values = GdxAudioDevices.outputChoices();
                if (option >= 0 && option < values.size()) {
                    GdxAudioDevices.selectOutput(initialProperties,
                            values.get(option));
                }
            }
            case AUDIO_CAPTURE -> {
                List<String> values = GdxAudioDevices.captureChoices();
                if (option >= 0 && option < values.size()) {
                    GdxAudioDevices.selectCapture(initialProperties,
                            values.get(option));
                }
            }
            case VOICE_RETENTION -> GdxSettingsContract
                    .selectVoiceRetention(initialProperties, option);
            case WINDOW_MODE -> {
                GdxWindowMode[] values = GdxWindowMode.values();
                if (option >= 0 && option < values.length) {
                    GdxDisplayModeController.apply(initialProperties,
                            values[option]);
                }
            }
            case FPS_LIMIT -> {
                List<Integer> values = presentationSettings.frameRateLimits();
                if (option >= 0 && option < values.size()) {
                    presentationSettings.selectFrameRateLimit(
                            values.get(option), false);
                }
            }
            case MSAA -> {
                List<Integer> values = presentationSettings.msaaLevels();
                if (option >= 0 && option < values.size()) {
                    presentationSettings.selectMsaaSamples(
                            values.get(option), false);
                }
            }
            case CARD_RENDERING -> {
                List<Integer> values = presentationSettings.cardRenderingModes();
                if (option >= 0 && option < values.size()) {
                    presentationSettings.selectCardRenderingMode(
                            values.get(option), false);
                }
            }
            case APPEARANCE_CHOICE -> {
                if (dropdownAppearanceChoice != null) {
                    GdxAppearanceOptions.select(dropdownAppearanceChoice,
                            initialProperties, option);
                }
            }
            case ANTE_MODE -> {
                NewGameTableDraft draft = dropdownTableTarget();
                AnteMode[] values = AnteMode.values();
                if (!draft.economyLocked() && draft.ante()
                        && option >= 0 && option < values.length) {
                    draft.setAnteMode(values[option]);
                }
            }
            case BLIND_UNIT -> {
                NewGameTableDraft.BlindIncreaseType[] values =
                        NewGameTableDraft.BlindIncreaseType.values();
                if (option >= 0 && option < values.length) {
                    dropdownTableTarget().setBlindIncreaseType(values[option]);
                }
            }
            case REBUY_CAP -> {
                NewGameTableDraft.RebuyCapPolicy[] values =
                        NewGameTableDraft.RebuyCapPolicy.values();
                if (option >= 0 && option < values.length) {
                    dropdownTableTarget().setRebuyCapPolicy(values[option]);
                }
            }
            case BOT_DIFFICULTY -> {
                NewGameTableDraft.BotDifficulty[] values =
                        NewGameTableDraft.BotDifficulty.values();
                if (option >= 0 && option < values.length) {
                    dropdownTableTarget().setBotDifficulty(values[option]);
                }
            }
            case RABBIT -> {
                NewGameTableDraft.RabbitHunting[] values =
                        NewGameTableDraft.RabbitHunting.values();
                if (option >= 0 && option < values.length) {
                    dropdownTableTarget().setRabbitHunting(values[option]);
                }
            }
            case CUSTOM_BOT_DIFFICULTY -> {
                NewGameTableDraft.BotDifficulty[] values =
                        NewGameTableDraft.BotDifficulty.values();
                if (option >= 0 && option < values.length) {
                    customBotDifficultyDraft = values[option];
                }
            }
            case CUSTOM_BOT_PERSONALITY -> {
                CustomBotProfile.Personality[] values =
                        CustomBotProfile.Personality.values();
                if (option >= 0 && option < values.length) {
                    customBotPersonalityDraft = values[option];
                }
            }
            case NONE -> { }
        }
        dropdown = Dropdown.NONE;
        dropdownAppearanceChoice = null;
    }

    private void selectCardBackOption(int option) {
        List<String> backs = presentationSettings.availableCardBacks();
        if (option < 0 || option >= backs.size()) return;
        String previous = presentationSettings.cardBack();
        String selected = presentationSettings.selectCardBack(
                backs.get(option), false);
        if (!Objects.equals(previous, selected)) {
            playPreferenceSound("misc/uncover.wav", "sonido_destape", 0.92f);
        }
    }

    private void selectLanguageOption(int option) {
        if (option < 0 || option > 1) return;
        String next = option == 0 ? "es" : "en";
        if (next.equalsIgnoreCase(presentationSettings.language())) return;
        initialProperties.setProperty("lenguaje", next);
        languageChanged.accept(next);
        if (preferences != null) preferences.saveDeferred();
    }

    private void selectServerHistoryOption(int option) {
        List<String> history = connection == null
                ? List.of() : connection.serverHistory();
        if (option < 0 || option >= history.size()) return;
        historyIndex = option;
        String endpoint = history.get(option);
        int separator = endpoint.lastIndexOf(':');
        if (separator > 0 && separator < endpoint.length() - 1) {
            connection.setServer(endpoint.substring(0, separator));
            connection.setPort(endpoint.substring(separator + 1));
            connection.persistLocalHostEndpoint(preferences);
        }
    }

    private void drawDropdownOverlay(boolean interactive) {
        List<String> options = dropdownOptions(dropdown);
        if (options.isEmpty()) {
            dropdown = Dropdown.NONE;
            return;
        }
        int visible = Math.min(GdxSettingsLayout.POPUP_MAX_VISIBLE_ROWS,
                options.size());
        int maximum = Math.max(0, options.size() - visible);
        boolean scrollable = maximum > 0;
        dropdownScroll = MathUtils.clamp(dropdownScroll, 0, maximum);
        dropdownScrollMaximum = maximum;
        if (!scrollable) {
            dropdownScrollTrack.set(0f, 0f, 0f, 0f);
            dropdownScrollThumbHeight = 0f;
        }
        float x = dropdownAnchored ? dropdownAnchor.x : switch (dropdown) {
            case PROFILE -> 500f;
            case HOST_ADDRESS -> 1170f;
            default -> 470f;
        };
        float w = dropdownAnchored ? dropdownAnchor.width : switch (dropdown) {
            case PROFILE -> 1285f;
            case HOST_ADDRESS -> 430f;
            default -> 590f;
        };
        if (dropdownAnchored) {
            float contentWidth = 0f;
            for (String option : options) {
                contentWidth = Math.max(contentWidth,
                        textWidth(smallFont, option));
            }
            float desiredWidth = contentWidth + 40f
                    + (scrollable ? 24f : 6f);
            w = Math.min(WIDTH - 40f, Math.max(w, desiredWidth));
            x = MathUtils.clamp(x, 20f, WIDTH - 20f - w);
        }
        float top = dropdownAnchored ? dropdownAnchor.y : switch (dropdown) {
            case PROFILE -> 570f;
            case HOST_ADDRESS -> 700f;
            default -> 680f;
        };
        float rowH = GdxSettingsLayout.POPUP_ROW_HEIGHT;
        float listHeight = visible * rowH;
        if (dropdownAnchored) {
            float lowerLimit = surface == Surface.NEW_GAME && dropdown == Dropdown.ANTE_MODE
                    ? BLINDS_PANEL_BOTTOM : 20f;
            top = GdxSettingsLayout.popupListTop(dropdownAnchor, listHeight,
                    lowerLimit, HEIGHT - 20f);
        }
        float y = top - visible * rowH;
        dropdownClip.set(x, y, w, visible * rowH);
        float rowRightInset = scrollable ? 24f : 6f;
        if (interactive) {
            hit(0f, 0f, WIDTH, HEIGHT, () -> dropdown = Dropdown.NONE);
        }
        outerBox(x, y, w, visible * rowH, CYAN_DARK,
                new Color(0x071221ff));
        int selected = selectedDropdownOption(dropdown);
        for (int row = 0; row < visible; row++) {
            int option = dropdownScroll + row;
            float rowY = top - (row + 1) * rowH;
            boolean active = option == selected;
            boolean over = hovered(x + 6f, rowY + 3f,
                    w - 6f - rowRightInset, rowH - 6f);
            shapes.setColor(active ? new Color(0x153a52ff)
                    : over ? new Color(0x10283cff)
                            : new Color(0x091624ff));
            roundedRect(x + 6f, rowY + 3f,
                    w - 6f - rowRightInset, rowH - 6f, 6f);
            if (active) {
                shapes.setColor(GOLD);
                roundedRect(x + 12f, rowY + 12f, 4f, rowH - 24f, 2f);
            }
            if (interactive) {
                textFit(smallFont, options.get(option), x + 28f,
                        rowY + GdxSettingsLayout.POPUP_TEXT_BASELINE,
                        active ? GOLD : Color.WHITE, false,
                        w - 34f - rowRightInset);
                int selectedOption = option;
                hit(x + 6f, rowY + 3f, w - 6f - rowRightInset,
                        rowH - 6f, () -> {
                    selectDropdownOption(dropdown, selectedOption);
                });
                if (over) {
                    String key = GdxSettingsTooltips.optionKey(dropdown.name(),
                            option, presentationSettings);
                    if (key != null) {
                        dropdownOptionTooltip = new TooltipHit(
                                new Rectangle(x + 6f, rowY + 3f,
                                        w - 6f - rowRightInset, rowH - 6f),
                                key, new Object[0],
                                GdxSettingsContract.PerformanceImpact.NONE);
                    }
                }
            }
        }
        if (scrollable) {
            float trackX = x + w - 15f;
            float trackY = y + 8f;
            float trackH = visible * rowH - 16f;
            float thumbH = Math.max(34f,
                    trackH * visible / (float) options.size());
            dropdownScrollTrack.set(trackX, trackY, 7f, trackH);
            dropdownScrollThumbHeight = thumbH;
            float thumbY = trackY + (trackH - thumbH)
                    * (1f - dropdownScroll / (float) maximum);
            shapes.setColor(new Color(0x1c3450ff));
            roundedRect(trackX, trackY, 7f, trackH, 3.5f);
            shapes.setColor(CYAN);
            roundedRect(trackX, thumbY, 7f, thumbH, 3.5f);
            if (interactive) {
                textFit(tinyFont, (dropdownScroll + 1) + " - "
                        + (dropdownScroll + visible) + " / "
                        + options.size(), x + w - 18f, y - 12f,
                        MUTED, true, 130f);
            }
        }
    }

    /** Composes popup shapes and labels after all covered page text. */
    private void drawDropdownTopLayer() {
        dropdownOptionTooltip = null;
        boolean previousSettingsRowsActive = settingsRowsActive;
        settingsRowsActive = false;
        int firstText = texts.size();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA,
                GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawDropdownOverlay(true);
        shapes.end();
        batch.begin();
        enableWorldScissor(dropdownClip);
        for (int index = firstText; index < texts.size(); index++) {
            drawTextItem(texts.get(index));
        }
        batch.flush();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        batch.end();
        settingsRowsActive = previousSettingsRowsActive;
    }

    static int dropdownScrollFromTrack(float pointerY, float trackY,
            float trackHeight, float thumbHeight, int maximum) {
        if (maximum <= 0) return 0;
        float travel = Math.max(1f, trackHeight - thumbHeight);
        float bottomRatio = MathUtils.clamp(
                (pointerY - trackY - thumbHeight / 2f) / travel, 0f, 1f);
        return Math.round((1f - bottomRatio) * maximum);
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

    private void compactRepeatButton(float x, float y, float w, float h,
            String label, boolean primary, Runnable action, boolean enabled) {
        themedButton(x, y, w, h, label,
                primary ? ButtonTone.FEATURED : ButtonTone.NEUTRAL,
                action, enabled, smallFont, true);
    }

    private void compactIconButton(Rectangle bounds, TextureRegion icon,
            String tooltipKey, Runnable action, boolean enabled,
            float iconSize) {
        compactButton(bounds.x, bounds.y, bounds.width, bounds.height,
                "", false, action, enabled);
        float size = Math.min(iconSize,
                Math.min(bounds.width - 18f, bounds.height - 18f));
        float tint = enabled ? 1f : 0.38f;
        uiImages.add(new UiImageItem(icon,
                bounds.x + (bounds.width - size) / 2f,
                bounds.y + (bounds.height - size) / 2f,
                size, size, new Color(tint, tint, tint, 1f)));
        tooltip(bounds.x, bounds.y, bounds.width, bounds.height,
                tooltipKey);
    }

    private void lobbyBotButton(float x, float y, float w, float h,
            String label, Runnable action, boolean enabled) {
        themedButton(x, y, w, h, "", ButtonTone.NEUTRAL,
                action, enabled);
        boolean hover = enabled && hovered(x, y, w, h);
        Color iconColor = enabled ? (hover ? CYAN : GOLD) : DISABLED;
        drawBotIcon(x + 34f, y + h / 2f - 4f, iconColor,
                new Color(0x0b1424ff), 0.75f);
        crispButtonTextInBox(actionFont, label, x + 56f, y, w - 112f, h,
                GdxUiButtonStyle.labelColor(GdxUiButtonStyle.Tone.NEUTRAL,
                        enabled));
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
        crispButtonTextInBox(actionFont, label, x + 72f, y,
                w - 84f, h, GdxUiButtonStyle.labelColor(
                        GdxUiButtonStyle.Tone.NEUTRAL, enabled));
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
        themedButton(x, y, w, h, label, tone, action, enabled, labelFont,
                false);
    }

    private void themedButton(float x, float y, float w, float h,
            String label, ButtonTone tone, Runnable action, boolean enabled,
            BitmapFont labelFont, boolean repeatable) {
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
        if (GdxUiDialogStyle.CLOSE_GLYPH.equals(label)) {
            GdxUiDialogStyle.drawWindowClose(shapes, x, y, w, h, enabled,
                    hoverAmount, down, 1f);
        } else {
            GdxUiButtonStyle.draw(shapes, x, y, w, h, sharedTone, enabled,
                    hoverAmount, down, 1f);
            Color labelColor = GdxUiButtonStyle.labelColor(sharedTone, enabled);
            if ("-".equals(label) || "+".equals(label)) {
                pixelSnappedTextInBox(labelFont, label, x, y, w, h, labelColor);
            } else {
                crispButtonTextInBox(labelFont, label, x, y, w, h, labelColor);
            }
        }
        if (enabled) {
            if (repeatable || "-".equals(label) || "+".equals(label)) {
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

    /**
     * Every frontend exit action keeps its label centered on the complete
     * button. The exit glyph remains in the leading icon column but never
     * changes the label's centering bounds.
     */
    private void exitButton(float x, float y, float w, float h,
            String label, Runnable action, boolean enabled) {
        themedButton(x, y, w, h, label, ButtonTone.DANGER, action, enabled);
        drawButtonIconColumn(x, y, w, h, 5, ButtonTone.DANGER, enabled);
    }

    /** Language is a menu action, so it uses the same glass material too. */
    private void mainMenuChoice(float x, float y, float w, float h,
            String value, Runnable action) {
        themedButton(x, y, w, h, "", ButtonTone.NEUTRAL, action, true);
        glyph.setText(uiFont, value);
        float baseline = y + (h + glyph.height) / 2f;
        texts.add(new TextItem(uiFont, value, x + 22f, baseline,
                new Color(Color.WHITE), false));
        shapes.setColor(GOLD);
        float cx = x + w - 36f;
        float cy = y + h / 2f;
        shapes.rectLine(cx - 11f, cy + 6f, cx, cy - 5f, 3f);
        shapes.rectLine(cx, cy - 5f, cx + 11f, cy + 6f, 3f);
    }

    private void iconButton(float x, float y, float w, float h,
            String label, int icon, ButtonTone tone, Runnable action,
            boolean enabled) {
        iconButton(x, y, w, h, label, icon, tone, action, enabled, 84f);
    }

    private void iconButton(float x, float y, float w, float h,
            String label, int icon, ButtonTone tone, Runnable action,
            boolean enabled, float labelInset) {
        // The icon owns a fixed leading column. Drawing the normal centered
        // label underneath it made long lobby labels collide with the camera.
        themedButton(x, y, w, h, "", tone, action, enabled);
        drawButtonIconColumn(x, y, w, h, icon, tone, enabled);
        float labelX = x + labelInset;
        float labelW = w - labelInset;
        Color labelColor = GdxUiButtonStyle.labelColor(
                GdxUiButtonStyle.Tone.valueOf(tone.name()), enabled);
        crispButtonTextInBox(actionFont, label, labelX, y, labelW, h,
                labelColor);
    }

    private void crispButtonTextInBox(BitmapFont preferred, String value,
            float x, float y, float width, float height, Color color) {
        GdxCrispButtonText.FontTiers tiers = preferred == smallFont
                ? compactButtonFontTiers : actionButtonFontTiers;
        GdxCrispButtonText.resolve(glyph, value, width, tiers,
                crispButtonText);
        pixelSnappedTextInBox(crispButtonText.font(), crispButtonText.text(),
                x, y, width, height, color);
    }

    private void pixelSnappedTextInBox(BitmapFont font, String value,
            float x, float y, float width, float height, Color color) {
        glyph.setText(font, value);
        float drawX = x + (width - glyph.width) / 2f;
        float baselineY = y + (height + glyph.height) / 2f;
        activeTextItems().add(new TextItem(font,
                value, drawX, baselineY, new Color(color), false));
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
    }

    private void drawMainMenuIcon(int icon, float cx, float cy, Color color) {
        switch (icon) {
            case 0 -> GdxUiIconRenderer.draw(shapes,
                    GdxUiIconRenderer.Icon.ADD, cx, cy, 56f, color, 1f);
            case 1 -> GdxUiIconRenderer.draw(shapes,
                    GdxUiIconRenderer.Icon.PLAYERS, cx, cy, 56f, color, 1f);
            case 2 -> GdxUiIconRenderer.draw(shapes,
                    GdxUiIconRenderer.Icon.STATISTICS,
                    cx, cy, 56f, color, 1f);
            case 3 -> GdxUiIconRenderer.draw(shapes,
                    GdxUiIconRenderer.Icon.SETTINGS,
                    cx, cy, 56f, color, 1f);
            case 4 -> GdxUiIconRenderer.draw(shapes,
                    GdxUiIconRenderer.Icon.INFO, cx, cy, 56f, color, 1f);
            case 5 -> GdxUiIconRenderer.draw(shapes,
                    GdxUiIconRenderer.Icon.EXIT, cx, cy, 56f, color, 1f);
            case 6 -> GdxUiIconRenderer.draw(shapes,
                    GdxUiIconRenderer.Icon.CAMERA, cx, cy, 56f, color, 1f);
            case 7 -> {
                // Temporary connection blocks.
                shapes.circle(cx, cy, 21f, 48);
                Color cutout = new Color(0x0b1729ff);
                shapes.setColor(cutout);
                shapes.circle(cx, cy, 14f, 40);
                shapes.setColor(color);
                shapes.rectLine(cx - 14f, cy + 14f,
                        cx + 14f, cy - 14f, 7f);
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
                drawBotIcon(cx, cy, color, cutout, 1f);
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

    private void drawBotIcon(float cx, float cy, Color color, Color cutout,
            float scale) {
        shapes.setColor(color);
        roundedRect(cx - 18f * scale, cy - 14f * scale,
                36f * scale, 28f * scale, 6f * scale);
        shapes.rect(cx - 3f * scale, cy + 14f * scale, 6f * scale, 8f * scale);
        shapes.circle(cx, cy + 23f * scale, 3.5f * scale, 16);
        shapes.setColor(cutout);
        shapes.circle(cx - 8f * scale, cy, 3.5f * scale, 16);
        shapes.circle(cx + 8f * scale, cy, 3.5f * scale, 16);
        shapes.rect(cx - 9f * scale, cy - 8f * scale, 18f * scale, 3f * scale);
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

    private void drawAvatarCropDialog() {
        AvatarCropSession session = avatarCropSession;
        if (session == null) return;
        float dialogX = 285f;
        float dialogY = 42f;
        float dialogW = 1350f;
        float dialogH = 996f;
        float viewX = WIDTH / 2f - AVATAR_CROP_VIEW_SIZE / 2f;
        float viewY = 236f;
        avatarCropViewport.set(viewX, viewY, AVATAR_CROP_VIEW_SIZE,
                AVATAR_CROP_VIEW_SIZE);

        outerBox(dialogX, dialogY, dialogW, dialogH, CYAN, PANEL);
        text(titleFont, uppercase(gameText.translate(
                        "gdx.avatar.crop.title")),
                WIDTH / 2f, 985f, GOLD, true);
        textFit(uiFont, gameText.translate("gdx.avatar.crop.hint"),
                WIDTH / 2f, 924f, MUTED, true, 1140f);
        shapes.setColor(new Color(0x07111fff));
        roundedRect(viewX - 12f, viewY - 12f,
                AVATAR_CROP_VIEW_SIZE + 24f,
                AVATAR_CROP_VIEW_SIZE + 24f, 18f);
        shapes.setColor(GOLD);
        roundedRectOutline(viewX - 3f, viewY - 3f,
                AVATAR_CROP_VIEW_SIZE + 6f,
                AVATAR_CROP_VIEW_SIZE + 6f, 10f, 3f);

        float controlX = viewX + AVATAR_CROP_VIEW_SIZE + 24f;
        float controlWidth = 260f;
        float controlHeight = 64f;
        float controlGap = 18f;
        float controlsY = viewY + (AVATAR_CROP_VIEW_SIZE
                - controlHeight * 3f - controlGap * 2f) / 2f;
        themedButton(controlX,
                controlsY + (controlHeight + controlGap) * 2f,
                controlWidth, controlHeight,
                uppercase(gameText.translate("gdx.avatar.crop.reset")),
                ButtonTone.NEUTRAL, this::resetAvatarCrop, true,
                smallFont);
        themedButton(controlX, controlsY + controlHeight + controlGap,
                controlWidth, controlHeight,
                uppercase(gameText.translate(
                        "gdx.avatar.crop.center_horizontal")),
                ButtonTone.NEUTRAL,
                this::centerAvatarCropHorizontally, true, smallFont);
        themedButton(controlX, controlsY, controlWidth, controlHeight,
                uppercase(gameText.translate(
                        "gdx.avatar.crop.center_vertical")),
                ButtonTone.NEUTRAL,
                this::centerAvatarCropVertically, true, smallFont);

        float cancelX = WIDTH / 2f - 330f;
        float useX = WIDTH / 2f + 30f;
        themedButton(cancelX, 92f, 300f, 86f,
                uppercase(gameText.translate("ui.cancelar")),
                ButtonTone.NEUTRAL, this::closeAvatarCrop, true);
        themedButton(useX, 92f, 300f, 86f,
                uppercase(gameText.translate("gdx.avatar.crop.use")),
                ButtonTone.FEATURED, this::applyAvatarCrop, true);
    }

    private void drawAvatarCropPreview() {
        AvatarCropSession session = avatarCropSession;
        if (session == null) return;
        updateAvatarCropScreenGeometry(session);
        batch.setColor(Color.WHITE);
        batch.draw(session.texture, avatarCropImageBounds.x,
                avatarCropImageBounds.y, avatarCropImageBounds.width,
                avatarCropImageBounds.height);
        batch.end();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA,
                GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        float left = avatarCropSelectionBounds.x;
        float right = left + avatarCropSelectionBounds.width;
        float bottom = avatarCropSelectionBounds.y;
        float top = bottom + avatarCropSelectionBounds.height;
        shapes.setColor(new Color(0x020710b8));
        shapes.rect(avatarCropImageBounds.x, avatarCropImageBounds.y,
                Math.max(0f, left - avatarCropImageBounds.x),
                avatarCropImageBounds.height);
        shapes.rect(right, avatarCropImageBounds.y,
                Math.max(0f, avatarCropImageBounds.x
                        + avatarCropImageBounds.width - right),
                avatarCropImageBounds.height);
        shapes.rect(left, avatarCropImageBounds.y,
                avatarCropSelectionBounds.width,
                Math.max(0f, bottom - avatarCropImageBounds.y));
        shapes.rect(left, top, avatarCropSelectionBounds.width,
                Math.max(0f, avatarCropImageBounds.y
                        + avatarCropImageBounds.height - top));
        shapes.setColor(GOLD);
        float line = 4f;
        shapes.rect(left - line / 2f, bottom - line / 2f,
                avatarCropSelectionBounds.width + line, line);
        shapes.rect(left - line / 2f, top - line / 2f,
                avatarCropSelectionBounds.width + line, line);
        shapes.rect(left - line / 2f, bottom - line / 2f,
                line, avatarCropSelectionBounds.height + line);
        shapes.rect(right - line / 2f, bottom - line / 2f,
                line, avatarCropSelectionBounds.height + line);
        float handle = Math.min(14f, Math.max(7f,
                avatarCropSelectionBounds.width * 0.08f));
        shapes.setColor(Color.WHITE);
        GdxAntialiasedRoundedRect.fillCircle(shapes,
                left, bottom, handle);
        GdxAntialiasedRoundedRect.fillCircle(shapes,
                right, bottom, handle);
        GdxAntialiasedRoundedRect.fillCircle(shapes,
                left, top, handle);
        GdxAntialiasedRoundedRect.fillCircle(shapes,
                right, top, handle);
        shapes.end();
        batch.begin();
    }

    private void updateAvatarCropScreenGeometry(AvatarCropSession session) {
        float scale = Math.min(avatarCropViewport.width
                        / session.pixmap.getWidth(),
                avatarCropViewport.height / session.pixmap.getHeight());
        float imageWidth = session.pixmap.getWidth() * scale;
        float imageHeight = session.pixmap.getHeight() * scale;
        avatarCropImageBounds.set(
                avatarCropViewport.x
                        + (avatarCropViewport.width - imageWidth) / 2f,
                avatarCropViewport.y
                        + (avatarCropViewport.height - imageHeight) / 2f,
                imageWidth, imageHeight);
        GdxAvatarCropModel.Crop crop = session.crop;
        avatarCropSelectionBounds.set(
                avatarCropImageBounds.x + crop.x() * scale,
                avatarCropImageBounds.y
                        + (session.pixmap.getHeight() - crop.y()
                                - crop.size()) * scale,
                crop.size() * scale, crop.size() * scale);
    }

    private void resizeAvatarCrop(float factor) {
        AvatarCropSession session = avatarCropSession;
        if (session == null) return;
        session.crop = GdxAvatarCropModel.resizeCentered(session.crop,
                session.pixmap.getWidth(), session.pixmap.getHeight(),
                factor);
    }

    private void resetAvatarCrop() {
        AvatarCropSession session = avatarCropSession;
        if (session == null) return;
        session.crop = GdxAvatarCropModel.centered(
                session.pixmap.getWidth(), session.pixmap.getHeight());
    }

    private void centerAvatarCropHorizontally() {
        AvatarCropSession session = avatarCropSession;
        if (session == null) return;
        session.crop = GdxAvatarCropModel.centerHorizontally(session.crop,
                session.pixmap.getWidth(), session.pixmap.getHeight());
    }

    private void centerAvatarCropVertically() {
        AvatarCropSession session = avatarCropSession;
        if (session == null) return;
        session.crop = GdxAvatarCropModel.centerVertically(session.crop,
                session.pixmap.getWidth(), session.pixmap.getHeight());
    }

    private GdxAvatarCropModel.DragTarget avatarCropDragAt(
            float worldX, float worldY) {
        float radius = Math.min(30f, Math.max(18f,
                avatarCropSelectionBounds.width * 0.12f));
        return GdxAvatarCropModel.dragTargetAt(
                avatarCropSelectionBounds.x,
                avatarCropSelectionBounds.y,
                avatarCropSelectionBounds.width,
                worldX, worldY, radius);
    }

    private void updateAvatarCropSystemCursor() {
        Cursor.SystemCursor desired = Cursor.SystemCursor.Arrow;
        AvatarCropSession session = avatarCropSession;
        if (session != null) {
            updateAvatarCropScreenGeometry(session);
            GdxAvatarCropModel.DragTarget target = avatarCropDrag
                    != GdxAvatarCropModel.DragTarget.NONE
                    ? avatarCropDrag
                    : avatarCropDragAt(pointer.x, pointer.y);
            desired = switch (target) {
                case TOP_LEFT, BOTTOM_RIGHT ->
                    Cursor.SystemCursor.NWSEResize;
                case TOP_RIGHT, BOTTOM_LEFT ->
                    Cursor.SystemCursor.NESWResize;
                default -> Cursor.SystemCursor.Arrow;
            };
        } else if (rulesWindow.isOpen()) {
            desired = rulesWindow.cursor(pointer.x, pointer.y);
        } else if (lobbyGalleryInteractive()) {
            desired = lobbyGallery.cursor(pointer.x, pointer.y);
        } else if (statsEvidenceOpen) {
            GdxFloatingGameLogLayout.ResizeHandle handle =
                    statsEvidenceWindowDrag == StatsEvidenceWindowDrag.RESIZE
                            ? statsEvidenceResizeHandle
                            : GdxFloatingGameLogLayout.resizeHandleAt(
                                    statsEvidenceLayout().panel(),
                                    pointer.x, pointer.y);
            desired = switch (handle) {
                case LEFT, RIGHT -> Cursor.SystemCursor.HorizontalResize;
                case TOP, BOTTOM -> Cursor.SystemCursor.VerticalResize;
                case TOP_LEFT, BOTTOM_RIGHT ->
                    Cursor.SystemCursor.NWSEResize;
                case TOP_RIGHT, BOTTOM_LEFT ->
                    Cursor.SystemCursor.NESWResize;
                case NONE -> Cursor.SystemCursor.Arrow;
            };
        } else {
            desired = pokerVideoOverlay.systemCursor(pointer.x, pointer.y);
        }
        if (desired != appliedSystemCursor) {
            Gdx.graphics.setSystemCursor(desired);
            appliedSystemCursor = desired;
        }
    }

    private Vector2 avatarCropSourceAt(AvatarCropSession session,
            float worldX, float worldY) {
        return avatarCropSourceAt(session, worldX, worldY, true);
    }

    private Vector2 avatarCropUnclampedSourceAt(AvatarCropSession session,
            float worldX, float worldY) {
        return avatarCropSourceAt(session, worldX, worldY, false);
    }

    private Vector2 avatarCropSourceAt(AvatarCropSession session,
            float worldX, float worldY, boolean clampToImage) {
        float scale = avatarCropImageBounds.width
                / session.pixmap.getWidth();
        float sourceX = (worldX - avatarCropImageBounds.x) / scale;
        float sourceY = session.pixmap.getHeight()
                - (worldY - avatarCropImageBounds.y) / scale;
        if (clampToImage) {
            sourceX = MathUtils.clamp(sourceX, 0f,
                    session.pixmap.getWidth());
            sourceY = MathUtils.clamp(sourceY, 0f,
                    session.pixmap.getHeight());
        }
        return avatarCropSource.set(sourceX, sourceY);
    }

    private void selectAvatar() {
        if (avatarSelectionPending || connection == null) return;
        avatarSelectionPending = true;
        NewGameConnectionDraft target = connection;
        chooseAvatarFile("gdx.avatar.select").whenComplete((selected, failure) ->
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
        if (!openAvatarCrop(selected, AvatarCropTarget.LOCAL, target)) {
            showToast(gameText.translate("gdx.avatar.invalid"));
        }
    }

    private boolean openAvatarCrop(Path selected, AvatarCropTarget target,
            NewGameConnectionDraft connectionTarget) {
        if (!isSupportedAvatar(selected)) return false;
        Pixmap pixmap = null;
        Texture texture = null;
        try {
            pixmap = decodeAvatarCropPixmap(selected);
            if (pixmap.getWidth() <= 0 || pixmap.getHeight() <= 0) {
                pixmap.dispose();
                return false;
            }
            texture = new Texture(pixmap);
            texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
            GdxAvatarCropModel.Crop crop = GdxAvatarCropModel.centered(
                    pixmap.getWidth(), pixmap.getHeight());
            closeAvatarCrop();
            avatarCropSession = new AvatarCropSession(target,
                    connectionTarget, pixmap, texture, crop);
            captureFrontendModalInput();
            return true;
        } catch (IOException | RuntimeException invalid) {
            if (texture != null) texture.dispose();
            if (pixmap != null) pixmap.dispose();
            LOGGER.log(Level.FINE, "Avatar crop source rejected", invalid);
            return false;
        }
    }

    private static Pixmap decodeAvatarCropPixmap(Path selected)
            throws IOException {
        Path source = selected.toAbsolutePath().normalize();
        try {
            return new Pixmap(Gdx.files.absolute(source.toString()));
        } catch (RuntimeException unsupportedByStb) {
            BufferedImage image = ImageIO.read(source.toFile());
            if (image == null) throw new IOException(
                    "Unsupported avatar image", unsupportedByStb);
            Pixmap pixmap = new Pixmap(image.getWidth(), image.getHeight(),
                    Pixmap.Format.RGBA8888);
            ByteBuffer pixels = pixmap.getPixels();
            pixels.clear();
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getRGB(x, y);
                    pixels.put((byte) (argb >>> 16));
                    pixels.put((byte) (argb >>> 8));
                    pixels.put((byte) argb);
                    pixels.put((byte) (argb >>> 24));
                }
            }
            pixels.position(0);
            return pixmap;
        }
    }

    private void applyAvatarCrop() {
        AvatarCropSession session = avatarCropSession;
        if (session == null) return;
        try {
            Path cropped = writeAvatarCrop(session);
            if (session.target == AvatarCropTarget.LOCAL) {
                if (connection != session.connectionTarget
                        || !connection.setAvatar(cropped)) {
                    showToast(gameText.translate("gdx.avatar.invalid"));
                    return;
                }
                connection.persistLocalIdentity(preferences);
                refreshSelectedAvatarTexture();
            } else {
                customBotAvatarDraft = cropped;
                customBotValidationError = "";
            }
            closeAvatarCrop();
        } catch (IOException | RuntimeException failure) {
            showToast(gameText.translate("gdx.avatar.select_failed"));
            LOGGER.log(Level.WARNING, "Could not save cropped avatar", failure);
        }
    }

    private Path writeAvatarCrop(AvatarCropSession session)
            throws IOException {
        GdxAvatarCropModel.Crop crop = session.crop;
        int sourceSize = Math.max(1, Math.min(
                Math.round(crop.size()),
                Math.min(session.pixmap.getWidth(),
                        session.pixmap.getHeight())));
        int sourceX = MathUtils.clamp(Math.round(crop.centerX()
                - sourceSize / 2f), 0,
                session.pixmap.getWidth() - sourceSize);
        int sourceY = MathUtils.clamp(Math.round(crop.centerY()
                - sourceSize / 2f), 0,
                session.pixmap.getHeight() - sourceSize);
        Pixmap output = new Pixmap(AVATAR_CROP_OUTPUT_SIZE,
                AVATAR_CROP_OUTPUT_SIZE, Pixmap.Format.RGBA8888);
        Path temporary = null;
        try {
            output.setFilter(Pixmap.Filter.BiLinear);
            output.drawPixmap(session.pixmap, sourceX, sourceY,
                    sourceSize, sourceSize, 0, 0,
                    AVATAR_CROP_OUTPUT_SIZE, AVATAR_CROP_OUTPUT_SIZE);
            Path directory = Path.of(System.getProperty("user.home"),
                    ".coronapoker", "avatars");
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory,
                    "avatar-crop-", ".png");
            PixmapIO.writePNG(new FileHandle(temporary.toFile()), output);
            AvatarImageValidator.validate(temporary);
            byte[] bytes = Files.readAllBytes(temporary);
            Path durable = directory.resolve(avatarDigest(bytes) + ".png");
            if (Files.isRegularFile(durable)) {
                Files.deleteIfExists(temporary);
            } else {
                moveAvatarCrop(temporary, durable);
            }
            return durable;
        } finally {
            output.dispose();
            if (temporary != null) Files.deleteIfExists(temporary);
        }
    }

    private static void moveAvatarCrop(Path temporary, Path durable)
            throws IOException {
        try {
            Files.move(temporary, durable, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            try {
                Files.move(temporary, durable);
            } catch (FileAlreadyExistsException alreadyCreated) {
                Files.deleteIfExists(temporary);
            }
        } catch (FileAlreadyExistsException alreadyCreated) {
            Files.deleteIfExists(temporary);
        }
    }

    private static String avatarDigest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable",
                    impossible);
        }
    }

    private void closeAvatarCrop() {
        AvatarCropSession session = avatarCropSession;
        avatarCropSession = null;
        avatarCropDrag = GdxAvatarCropModel.DragTarget.NONE;
        avatarCropPointer = -1;
        avatarCropResizeStart = null;
        if (session != null) {
            session.texture.dispose();
            session.pixmap.dispose();
        }
        captureFrontendModalInput();
    }

    private void refreshSelectedAvatarTexture() {
        disposeSelectedAvatarTexture();
        if (connection == null || connection.avatar() == null) return;
        Path path = connection.avatar().toAbsolutePath().normalize();
        try {
            Texture texture = GdxTextureQuality.loadUi(
                    Gdx.files.absolute(path.toString()));
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

    private CompletableFuture<Path> chooseAvatarFile(String titleKey) {
        String title = gameText.translate(titleKey);
        String select = gameText.translate("ui.aceptar");
        String images = gameText.translate("gdx.avatar.image_files");
        return GdxFileChooser.chooseFile(Path.of(System.getProperty("user.home")),
                title, select, images, "png", "jpg", "jpeg", "gif", "bmp", "webp");
    }

    private void outerBox(float x, float y, float w, float h, Color border,
            Color fill) {
        shapes.setColor(fill);
        roundedRect(x, y, w, h, 14f);
        // Containers, dialog frames and input fields must remain a single
        // uninterrupted surface. Applying the button glass field here makes
        // its fine sampling visible as wide colour bands on tall panels.
        shapes.setColor(border);
        roundedRectOutline(x + 1f, y + 1f, w - 2f, h - 2f,
                13f, 2f);
    }

    private void flatOuterBox(float x, float y, float w, float h,
            Color border, Color fill) {
        outerBox(x, y, w, h, border, fill);
    }

    private void roundedRectOutline(float x, float y, float w, float h,
            float radius, float thickness) {
        GdxAntialiasedRoundedRect.outline(shapes, x, y, w, h, radius,
                thickness);
    }

    private void roundedRect(float x, float y, float w, float h, float r) {
        GdxAntialiasedRoundedRect.fill(shapes, x, y, w, h, r);
    }

    private void text(BitmapFont font, String value, float x, float y,
            Color color, boolean centered) {
        activeTextItems().add(new TextItem(font,
                value, x, y, new Color(color), centered));
    }

    private void textFit(BitmapFont preferred, String value, float x, float y,
            Color color, boolean centered, float maxWidth) {
        activeTextItems().add(fittedTextItem(
                preferred, value, x, y, color, centered, maxWidth));
    }

    private void textFitRight(BitmapFont preferred, String value,
            float rightX, float y, Color color, float maxWidth) {
        TextItem fitted = fittedTextItem(preferred, value, 0f, y, color,
                false, maxWidth);
        float renderedWidth = textWidth(fitted.font(), fitted.text());
        activeTextItems().add(new TextItem(
                fitted.font(), fitted.text(), rightX - renderedWidth, y,
                fitted.color(), false));
    }

    /** Fits table headers by selecting a smaller native atlas. */
    private void textExactFit(BitmapFont font, String value, float x, float y,
            Color color, boolean centered, float maxWidth) {
        activeTextItems().add(fittedTextItem(
                font, value, x, y, color, centered, maxWidth));
    }

    private List<TextItem> activeTextItems() {
        if (customBotRowsActive) return customBotManagerListTexts;
        return settingsRowsActive ? settingsRowTexts : texts;
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

    private void statsTextFit(BitmapFont font, String value, float x, float y,
            Color color, boolean centered, float maxWidth) {
        activeTextItems().add(statsFittedTextItem(font, value, x, y, color, centered, maxWidth));
    }

    private TextItem statsFittedTextItem(BitmapFont font, String value, float x, float y,
            Color color, boolean centered, float maxWidth) {
        TextItem item = fittedTextItem(font, value, x, y, color, centered, maxWidth);
        String clipped = ellipsize(item.font(), item.text(), maxWidth);
        return clipped.equals(item.text()) ? item
                : new TextItem(item.font(), clipped, x, y, item.color(), centered);
    }

    private void chartTextFit(BitmapFont font, String value, float x, float y,
            Color color, boolean centered, float maxWidth) {
        List<TextItem> target = statsChartCanvasActive
                ? statsChartTexts : texts;
        target.add(statsFittedTextItem(font, value,
                statsChartCanvasActive ? statsChartX(x) : x,
                statsChartCanvasActive ? statsChartY(y) : y,
                color, centered, maxWidth));
    }

    private void italicTextFit(BitmapFont preferred, String value, float x,
            float y, Color color, boolean centered, float maxWidth) {
        // Synthetic shear distorts raster glyphs. Quotes keep the native face
        // until a genuine italic font asset is available.
        activeTextItems().add(fittedTextItem(
                preferred, value, x, y, color, centered, maxWidth));
    }

    private TextItem fittedTextItem(BitmapFont preferred, String value,
            float x, float y, Color color, boolean centered, float maxWidth) {
        GdxCrispButtonText.resolveWithinContentWidth(glyph, value, maxWidth,
                nativeTextTiers(preferred), crispButtonText);
        return new TextItem(crispButtonText.font(), crispButtonText.text(),
                x, y, new Color(color), centered);
    }

    private void drawTextItem(TextItem item) {
        item.font.setColor(item.color);
        glyph.setText(item.font, item.text);
        float x = item.centered ? item.x - glyph.width / 2f : item.x;
        GdxPixelText.draw(item.font, batch, item.text, x, item.y, viewport);
    }

    private boolean fits(BitmapFont font, String value, float maxWidth) {
        glyph.setText(font, value);
        return glyph.width <= maxWidth;
    }

    private String ellipsize(BitmapFont font, String value, float maxWidth) {
        if (maxWidth <= 0f || value == null || value.isEmpty()) return "";
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
                && !(lobbyGalleryInteractive() && lobbyGallery.ownsPointer(pointer.x, pointer.y))
                && !statsOwnsPointer()
                && (hasBlockingFrontendModal() || !pokerVideoOverlay.ownsHover(pointer.x, pointer.y))
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

    private void showShortcutToast(String status) {
        String key = switch (status) {
            case "updated" -> "gdx.settings.shortcut.status.updated";
            case "conflict" -> "gdx.settings.shortcut.status.conflict";
            default -> "gdx.settings.shortcut.status.unsupported";
        };
        showToast(gameText.translate(key));
    }

    private void showSettingsDefaultsRestoredToast() {
        showToast(gameText.translate("gdx.settings.defaults_restored"));
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        lobbyGallery.resize(viewport);
        rulesWindow.resize();
        if (titleFont == null) return;
        GdxAdaptiveFontQuality.Profile current =
                GdxAdaptiveFontQuality.current(WIDTH, HEIGHT);
        if (current.equals(fontQualityProfile)) {
            pendingFontQualityProfile = null;
            return;
        }
        pendingFontQualityProfile = current;
        // Window drags emit many resize events. Rebuild once after they settle
        // so changing monitor or display mode remains hitch-free while moving.
        fontQualityRefreshAfterNanos = System.nanoTime() + 300_000_000L;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointerIndex, int button) {
        if (fileChooserPending()) return true;
        pointer.set(screenX, screenY);
        viewport.unproject(pointer);
        pressedHit = null;
        if (rulesWindow.isOpen()) {
            rulesWindow.touchDown(pointer.x, pointer.y, pointerIndex, button);
            return true;
        }
        if (lobbyGalleryInteractive() && lobbyGallery.touchDown(pointer.x, pointer.y, pointerIndex, button)) {
            return true;
        }
        if (statsOwnsPointer()) {
            if (!STATS_WEB_BOUNDS.contains(pointer)) {
                statsBrowserPointer.captureSecondary(pointerIndex, button);
            } else if (statsBrowserPointer.capture(pointerIndex, button) && statsBrowserInput()) {
                statsBrowserFocused = true;
                statsBrowserMouse(MouseEvent.MOUSE_PRESSED);
            }
            return true;
        }
        if (statsBrowser != null) {
            statsBrowserFocused = false;
            statsBrowser.unfocus();
        }
        boolean blockingModal = hasBlockingFrontendModal();
        if (!blockingModal && pokerVideoOverlay.touchDown(pointer.x,
                pointer.y, pointerIndex, button)) {
            return true;
        }
        if (avatarCropSession != null && button == Input.Buttons.LEFT) {
            AvatarCropSession session = avatarCropSession;
            updateAvatarCropScreenGeometry(session);
            GdxAvatarCropModel.DragTarget target = avatarCropDragAt(
                    pointer.x, pointer.y);
            boolean insideImage = avatarCropImageBounds.contains(pointer);
            if (target != GdxAvatarCropModel.DragTarget.NONE || insideImage) {
                if (target == GdxAvatarCropModel.DragTarget.NONE) {
                    Vector2 source = avatarCropSourceAt(session,
                            pointer.x, pointer.y);
                    session.crop = GdxAvatarCropModel.crop(
                            session.pixmap.getWidth(),
                            session.pixmap.getHeight(), source.x, source.y,
                            session.crop.size());
                    updateAvatarCropScreenGeometry(session);
                    target = GdxAvatarCropModel.DragTarget.MOVE;
                }
                avatarCropDrag = target;
                if (target == GdxAvatarCropModel.DragTarget.MOVE) {
                    avatarCropResizeStart = null;
                } else {
                    avatarCropResizeStart = session.crop;
                    Vector2 source = avatarCropUnclampedSourceAt(session,
                            pointer.x, pointer.y);
                    avatarCropResizeStartSourceX = source.x;
                    avatarCropResizeStartSourceY = source.y;
                }
                avatarCropPointer = pointerIndex;
                avatarCropLastX = pointer.x;
                avatarCropLastY = pointer.y;
                return true;
            }
        }
        if (statsEvidenceOpen && !frontendNoticeOwnsPointer()
                && avatarCropSession == null && button == Input.Buttons.LEFT) {
            GdxFloatingGameLogLayout.ResizeHandle resizeHandle =
                    GdxFloatingGameLogLayout.resizeHandleAt(
                            statsEvidenceLayout().panel(),
                            pointer.x, pointer.y);
            if (resizeHandle
                    != GdxFloatingGameLogLayout.ResizeHandle.NONE) {
                return beginStatsEvidenceWindowDrag(
                        StatsEvidenceWindowDrag.RESIZE, resizeHandle,
                        pointer.x, pointer.y, pointerIndex, button);
            }
            if (statsEvidenceHeaderDragBounds().contains(pointer)) {
                return beginStatsEvidenceWindowDrag(
                        StatsEvidenceWindowDrag.MOVE,
                        GdxFloatingGameLogLayout.ResizeHandle.NONE,
                        pointer.x, pointer.y, pointerIndex, button);
            }
        }
        if (surface == Surface.LOBBY
                && lobbyTableTransitionActive(lobbyGameStarting, lobby)) {
            return true;
        }
        if (scrollPointer.active()) return true;
        Rectangle voiceBounds = lobbyVoiceButtonBounds();
        if (!blockingModal && surface == Surface.LOBBY
                && button == Input.Buttons.LEFT
                && voiceBounds.contains(pointer)) {
            boolean enabled = canUseLobbyVoice() && !lobbyCommandPending
                    && !lobbyVoiceStopping && lobbyVoiceRecorder == null;
            if (enabled && lobbyVoicePointer.capture(pointerIndex, button)) {
                pressedHit = new Hit(voiceBounds, () -> { }, false);
                beginLobbyVoiceRecording();
            }
            return true;
        }
        boolean rootSurfaceOwnsPointer = isFrontendRootModal();
        if (avatarCropSession == null && dropdown != Dropdown.NONE
                && button == Input.Buttons.LEFT
                && beginScrollDrag(pointer.x, pointer.y,
                        pointerIndex, button)) {
            return true;
        }
        if (avatarCropSession == null
                && !frontendNoticeOwnsPointer()
                && !hasNestedFrontendModal()
                && dropdown == Dropdown.NONE
                && (!blockingModal || rootSurfaceOwnsPointer
                || statsPicker != StatsPicker.NONE || networkBlocksOpen
                || customBotManagerOpen || statsEvidenceOpen)
                && button == Input.Buttons.LEFT
                && beginScrollDrag(pointer.x, pointer.y,
                        pointerIndex, button)) {
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
            return blockingModal;
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
        if (rulesWindow.isOpen()) {
            rulesWindow.touchDragged(pointer.x, pointer.y, pointerIndex);
            return true;
        }
        if (lobbyGalleryInteractive() && lobbyGallery.touchDragged(pointer.x, pointer.y, pointerIndex)) {
            return true;
        }
        if (statsOwnsPointer()) {
            if (statsBrowserPointer.ownsPrimary(pointerIndex) && statsBrowser.pressed()) {
                statsBrowserMouse(MouseEvent.MOUSE_DRAGGED);
            }
            return true;
        }
        if (pokerVideoOverlay.touchDragged(pointer.x, pointer.y,
                pointerIndex)) return true;
        if (avatarCropSession != null
                && avatarCropDrag != GdxAvatarCropModel.DragTarget.NONE
                && avatarCropPointer == pointerIndex) {
            AvatarCropSession session = avatarCropSession;
            updateAvatarCropScreenGeometry(session);
            if (avatarCropDrag == GdxAvatarCropModel.DragTarget.MOVE) {
                float sourcePerWorld = session.pixmap.getWidth()
                        / avatarCropImageBounds.width;
                session.crop = GdxAvatarCropModel.move(session.crop,
                        session.pixmap.getWidth(), session.pixmap.getHeight(),
                        (pointer.x - avatarCropLastX) * sourcePerWorld,
                        -(pointer.y - avatarCropLastY) * sourcePerWorld);
            } else {
                Vector2 source = avatarCropUnclampedSourceAt(session,
                        pointer.x, pointer.y);
                session.crop = GdxAvatarCropModel.resizeFromCornerDelta(
                        Objects.requireNonNull(avatarCropResizeStart,
                                "Missing crop resize origin"),
                        session.pixmap.getWidth(),
                        session.pixmap.getHeight(), avatarCropDrag.corner(),
                        source.x - avatarCropResizeStartSourceX,
                        source.y - avatarCropResizeStartSourceY);
            }
            avatarCropLastX = pointer.x;
            avatarCropLastY = pointer.y;
            return true;
        }
        if (hasNestedFrontendModal()) return true;
        if (statsEvidenceWindowPointer.owns(pointerIndex,
                Input.Buttons.LEFT)) {
            updateStatsEvidenceWindowDrag(pointer.x, pointer.y);
            return true;
        }
        if (lobbyVoicePointer.owns(pointerIndex, Input.Buttons.LEFT)) {
            return true;
        }
        if (scrollDrag != ScrollDrag.NONE
                && scrollPointer.owns(pointerIndex, Input.Buttons.LEFT)) {
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
        return hasBlockingFrontendModal();
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointerIndex, int button) {
        pointer.set(screenX, screenY);
        viewport.unproject(pointer);
        if (rulesWindow.isOpen()) {
            rulesWindow.touchUp(pointer.x, pointer.y, pointerIndex, button);
            return true;
        }
        if (lobbyGalleryInteractive()
                ? lobbyGallery.touchUp(pointer.x, pointer.y, pointerIndex, button)
                : lobbyGallery.cancel(pointerIndex, button)) {
            pokerVideoOverlay.cancelTouch(pointerIndex, button);
            cancelCoveredFrontendGesture(pointerIndex, button);
            return true;
        }
        boolean statsRelease = statsBrowserPointer.releasePrimary(pointerIndex, button);
        if (statsRelease && statsBrowserInput() && statsBrowser.pressed()) statsBrowserMouse(MouseEvent.MOUSE_RELEASED);
        if (statsRelease || statsBrowserPointer.releaseSecondary(pointerIndex, button) || statsOwnsPointer()) {
            cancelCoveredFrontendGesture(pointerIndex, button);
            return true;
        }
        if (pokerVideoOverlay.touchUp(pointer.x, pointer.y, pointerIndex,
                button)) {
            cancelCoveredFrontendGesture(pointerIndex, button);
            return true;
        }
        if (avatarCropSession != null
                && avatarCropDrag != GdxAvatarCropModel.DragTarget.NONE
                && avatarCropPointer == pointerIndex
                && button == Input.Buttons.LEFT) {
            avatarCropDrag = GdxAvatarCropModel.DragTarget.NONE;
            avatarCropPointer = -1;
            avatarCropResizeStart = null;
            return true;
        }
        if (statsEvidenceWindowPointer.release(pointerIndex, button)) {
            statsEvidenceWindowDrag = StatsEvidenceWindowDrag.NONE;
            statsEvidenceResizeHandle =
                    GdxFloatingGameLogLayout.ResizeHandle.NONE;
            return true;
        }
        if (lobbyVoicePointer.release(pointerIndex, button)) {
            pressedHit = null;
            clearPointerRepeat();
            pointerSelectionField = null;
            finishLobbyVoiceRecording(false);
            return true;
        }
        boolean passwordWasRevealed = revealedPasswordField != null;
        revealedPasswordField = null;
        Hit released = pressedHit;
        pressedHit = null;
        clearPointerRepeat();
        pointerSelectionField = null;
        if (scrollPointer.release(pointerIndex, button)) {
            scrollDrag = ScrollDrag.NONE;
            scrollDragGrabOffset = 0f;
            return true;
        }
        if (surface == Surface.LOBBY
                && lobbyTableTransitionActive(lobbyGameStarting, lobby)) {
            return true;
        }
        if (released != null && !released.repeatable
                && released.bounds.contains(pointer)) {
            released.action.run();
            return true;
        }
        return passwordWasRevealed || hasBlockingFrontendModal();
    }

    private void cancelCoveredFrontendGesture(int pointerIndex, int button) {
        if (scrollPointer.owns(pointerIndex, button)) clearScrollDrag();
        if (lobbyVoicePointer.release(pointerIndex, button)) finishLobbyVoiceRecording(true);
        if (button == Input.Buttons.LEFT) {
            pressedHit = null;
            pointerSelectionField = null;
            revealedPasswordField = null;
            clearPointerRepeat();
        }
    }

    private boolean beginScrollDrag(float x, float y, int pointerIndex,
            int button) {
        if (dropdown != Dropdown.NONE) {
            if (dropdownScrollMaximum > 0
                    && GdxSettingsLayout.verticalScrollbarContains(
                            dropdownScrollTrack, x, y)) {
                return captureScrollDrag(ScrollDrag.DROPDOWN,
                        pointerIndex, button, y, dropdownScrollTrack.y,
                        dropdownScrollTrack.height,
                        dropdownScrollThumbHeight,
                        dropdownScroll / (float) dropdownScrollMaximum, true);
            }
            return false;
        }
        if (statsEvidenceOpen) {
            GdxFloatingGameLogLayout.Layout layout = statsEvidenceLayout();
            Rectangle track = layout.scrollbarTrack();
            if (statsEvidenceScrollMaximum > 0f
                    && GdxSettingsLayout.verticalScrollbarContains(
                            track, x, y)) {
                return captureScrollDrag(ScrollDrag.STATS_EVIDENCE,
                        pointerIndex, button, y, track.y, track.height,
                        statsEvidenceScrollThumbHeight,
                        statsEvidenceScroll / statsEvidenceScrollMaximum,
                        true);
            }
            return false;
        }
        if (customBotManagerOpen) {
            if (customBotDeleteConfirmation) return false;
            float maximum = customBotMaximumPixelScroll(
                    customBotDisplayRowCount());
            if (maximum > 0f
                    && GdxSettingsLayout.verticalScrollbarContains(
                            customBotScrollTrack, x, y)) {
                float thumb = GdxSettingsLayout.pixelScrollbarThumbHeight(
                        customBotScrollTrack.height,
                        customBotRowsViewport.height,
                        customBotContentHeight(customBotDisplayRowCount()));
                return captureScrollDrag(ScrollDrag.CUSTOM_BOTS,
                        pointerIndex, button, y, customBotScrollTrack.y,
                        customBotScrollTrack.height, thumb,
                        customBotScroll / maximum, true);
            }
            return false;
        }
        if (networkBlocksOpen) {
            if (networkBlockUnblockConfirmation != null) return false;
            GdxNetworkBlockDialogLayout.Layout layout
                    = GdxNetworkBlockDialogLayout.layout(WIDTH, HEIGHT);
            Rectangle track = layout.scrollbar();
            float maximum = GdxNetworkBlockDialogLayout.maximumScroll(
                    activeNetworkBlocks().size(), layout.rows());
            float thumb = networkBlocksThumbHeight(layout,
                    activeNetworkBlocks().size());
            if (maximum > 0f
                    && GdxSettingsLayout.verticalScrollbarContains(
                            track, x, y)) {
                return captureScrollDrag(ScrollDrag.NETWORK_BLOCKS,
                        pointerIndex, button, y, track.y, track.height, thumb,
                        networkBlocksScroll / maximum, true);
            }
            return false;
        }
        if (surface == Surface.STATS && statsPicker != StatsPicker.NONE
                && statsPickerScrollMaximum > 0f
                && GdxSettingsLayout.verticalScrollbarContains(
                        statsPickerScrollTrack, x, y)) {
            return captureScrollDrag(ScrollDrag.STATS_PICKER,
                    pointerIndex, button, y, statsPickerScrollTrack.y,
                    statsPickerScrollTrack.height,
                    statsPickerScrollThumbHeight,
                    statsPickerScroll / statsPickerScrollMaximum, true);
        }
        if (surface == Surface.STATS && statsPicker == StatsPicker.NONE
                && statsChartZoom > 1f
                && GdxSettingsLayout.horizontalScrollbarContains(
                        statsChartHorizontalTrack, x, y)) {
            return captureScrollDrag(ScrollDrag.STATS_CHART_HORIZONTAL,
                    pointerIndex, button, x, statsChartHorizontalTrack.x,
                    statsChartHorizontalTrack.width,
                    statsChartHorizontalThumbWidth, statsChartPanX, false);
        }
        if (surface == Surface.STATS && statsPicker == StatsPicker.NONE
                && statsChartZoom > 1f
                && GdxSettingsLayout.verticalScrollbarContains(
                        statsChartVerticalTrack, x, y)) {
            return captureScrollDrag(ScrollDrag.STATS_CHART_VERTICAL,
                    pointerIndex, button, y, statsChartVerticalTrack.y,
                    statsChartVerticalTrack.height,
                    statsChartVerticalThumbHeight, statsChartPanY, true);
        }
        if (surface == Surface.STATS && statsPicker == StatsPicker.NONE
                && statsResultScrollMaximum > 0f
                && GdxSettingsLayout.verticalScrollbarContains(
                        statsResultScrollTrack, x, y)) {
            return captureScrollDrag(ScrollDrag.STATS_RESULTS,
                    pointerIndex, button, y, statsResultScrollTrack.y,
                    statsResultScrollTrack.height,
                    statsResultScrollThumbHeight,
                    statsResultScroll / statsResultScrollMaximum, true);
        }
        if (surface == Surface.STATS && statsPicker == StatsPicker.NONE
                && statsSummaryPlayersScrollMaximum > 0f
                && GdxSettingsLayout.verticalScrollbarContains(
                        statsSummaryPlayersScrollTrack, x, y)) {
            return captureScrollDrag(ScrollDrag.STATS_SUMMARY_PLAYERS,
                    pointerIndex, button, y,
                    statsSummaryPlayersScrollTrack.y,
                    statsSummaryPlayersScrollTrack.height,
                    statsSummaryPlayersScrollThumbHeight,
                    statsSummaryPlayersScroll
                            / statsSummaryPlayersScrollMaximum, true);
        }
        if (surface == Surface.LOBBY
                && !lobbyEmojiPickerOpen && lobbyChatScrollMaximum > 0
                && GdxSettingsLayout.verticalScrollbarContains(
                        lobbyChatScrollTrack, x, y)) {
            return captureScrollDrag(ScrollDrag.LOBBY_CHAT,
                    pointerIndex, button, y, lobbyChatScrollTrack.y,
                    lobbyChatScrollTrack.height, lobbyChatScrollThumbHeight,
                    lobbyChatScroll / lobbyChatScrollMaximum, false);
        }
        if (surface == Surface.SETTINGS
                && settingsRowScrollMaximum > 0
                && GdxSettingsLayout.verticalScrollbarContains(
                        settingsRowScrollTrack, x, y)
                && (settingsSession.section()
                        == GdxSettingsContract.Section.APPEARANCE
                    || settingsSession.section()
                        == GdxSettingsContract.Section.AUDIO
                    || settingsSession.section()
                        == GdxSettingsContract.Section.GAME
                    || settingsSession.section()
                        == GdxSettingsContract.Section.SHORTCUTS)) {
            return captureScrollDrag(ScrollDrag.SETTINGS_ROWS,
                    pointerIndex, button, y, settingsRowScrollTrack.y,
                    settingsRowScrollTrack.height,
                    settingsRowScrollThumbHeight,
                    currentSettingsRowScroll() / settingsRowScrollMaximum,
                    true);
        }
        if (surface == Surface.SETTINGS
                && settingsSession.section()
                        == GdxSettingsContract.Section.DEBUG
                && settingsDebugScrollMaximum > 0
                && GdxSettingsLayout.verticalScrollbarContains(
                        settingsDebugScrollTrack, x, y)) {
            return captureScrollDrag(ScrollDrag.SETTINGS_DEBUG,
                    pointerIndex, button, y, settingsDebugScrollTrack.y,
                    settingsDebugScrollTrack.height,
                    settingsDebugScrollThumbHeight,
                    settingsDebugScroll / settingsDebugScrollMaximum, false);
        }
        return false;
    }

    private boolean captureScrollDrag(ScrollDrag type, int pointerIndex,
            int button, float coordinate, float trackStart, float trackLength,
            float thumbLength, float currentRatio, boolean inverted) {
        if (!scrollPointer.capture(pointerIndex, button)) return false;
        GdxScrollbarDrag.Capture capture = GdxScrollbarDrag.begin(coordinate,
                trackStart, trackLength, thumbLength, currentRatio, inverted);
        scrollDrag = type;
        scrollDragGrabOffset = capture.grabOffset();
        applyScrollRatio(capture.ratio());
        return true;
    }

    private void updateScrollDrag(float x, float y) {
        float progress;
        if (scrollDrag == ScrollDrag.DROPDOWN) {
            progress = GdxScrollbarDrag.ratio(y,
                    dropdownScrollTrack.y, dropdownScrollTrack.height,
                    dropdownScrollThumbHeight,
                    scrollDragGrabOffset, true);
        } else if (scrollDrag == ScrollDrag.STATS_EVIDENCE) {
            Rectangle track = statsEvidenceLayout().scrollbarTrack();
            progress = GdxScrollbarDrag.ratio(y, track.y, track.height,
                    statsEvidenceScrollThumbHeight,
                    scrollDragGrabOffset, true);
        } else if (scrollDrag == ScrollDrag.STATS_PICKER) {
            progress = GdxScrollbarDrag.ratio(y,
                    statsPickerScrollTrack.y,
                    statsPickerScrollTrack.height,
                    statsPickerScrollThumbHeight,
                    scrollDragGrabOffset, true);
        } else if (scrollDrag == ScrollDrag.STATS_CHART_HORIZONTAL) {
            progress = GdxScrollbarDrag.ratio(x,
                    statsChartHorizontalTrack.x,
                    statsChartHorizontalTrack.width,
                    statsChartHorizontalThumbWidth,
                    scrollDragGrabOffset, false);
        } else if (scrollDrag == ScrollDrag.STATS_CHART_VERTICAL) {
            progress = GdxScrollbarDrag.ratio(y,
                    statsChartVerticalTrack.y,
                    statsChartVerticalTrack.height,
                    statsChartVerticalThumbHeight,
                    scrollDragGrabOffset, true);
        } else if (scrollDrag == ScrollDrag.STATS_RESULTS) {
            progress = GdxScrollbarDrag.ratio(y,
                    statsResultScrollTrack.y,
                    statsResultScrollTrack.height,
                    statsResultScrollThumbHeight,
                    scrollDragGrabOffset, true);
        } else if (scrollDrag == ScrollDrag.STATS_SUMMARY_PLAYERS) {
            progress = GdxScrollbarDrag.ratio(y,
                    statsSummaryPlayersScrollTrack.y,
                    statsSummaryPlayersScrollTrack.height,
                    statsSummaryPlayersScrollThumbHeight,
                    scrollDragGrabOffset, true);
        } else if (scrollDrag == ScrollDrag.LOBBY_CHAT) {
            progress = GdxScrollbarDrag.ratio(y, lobbyChatScrollTrack.y,
                    lobbyChatScrollTrack.height, lobbyChatScrollThumbHeight,
                    scrollDragGrabOffset, false);
        } else if (scrollDrag == ScrollDrag.SETTINGS_DEBUG) {
            progress = GdxScrollbarDrag.ratio(y,
                    settingsDebugScrollTrack.y,
                    settingsDebugScrollTrack.height,
                    settingsDebugScrollThumbHeight,
                    scrollDragGrabOffset, false);
        } else if (scrollDrag == ScrollDrag.SETTINGS_ROWS) {
            progress = GdxScrollbarDrag.ratio(y,
                    settingsRowScrollTrack.y,
                    settingsRowScrollTrack.height,
                    settingsRowScrollThumbHeight,
                    scrollDragGrabOffset, true);
        } else if (scrollDrag == ScrollDrag.CUSTOM_BOTS) {
            progress = GdxScrollbarDrag.ratio(y,
                    customBotScrollTrack.y,
                    customBotScrollTrack.height,
                    customBotScrollThumbHeight,
                    scrollDragGrabOffset, true);
        } else if (scrollDrag == ScrollDrag.NETWORK_BLOCKS) {
            GdxNetworkBlockDialogLayout.Layout layout
                    = GdxNetworkBlockDialogLayout.layout(WIDTH, HEIGHT);
            progress = GdxScrollbarDrag.ratio(y, layout.scrollbar().y,
                    layout.scrollbar().height,
                    networkBlocksThumbHeight(layout,
                            activeNetworkBlocks().size()),
                    scrollDragGrabOffset, true);
        } else {
            return;
        }
        applyScrollRatio(progress);
    }

    private void applyScrollRatio(float progress) {
        if (scrollDrag == ScrollDrag.DROPDOWN) {
            dropdownScroll = MathUtils.clamp(Math.round(progress
                    * dropdownScrollMaximum), 0, dropdownScrollMaximum);
        } else if (scrollDrag == ScrollDrag.STATS_EVIDENCE) {
            statsEvidenceScrollTarget = progress
                    * statsEvidenceScrollMaximum;
            statsEvidenceScroll = statsEvidenceScrollTarget;
        } else if (scrollDrag == ScrollDrag.STATS_PICKER) {
            statsPickerScrollTarget = progress * statsPickerScrollMaximum;
            statsPickerScroll = statsPickerScrollTarget;
        } else if (scrollDrag == ScrollDrag.STATS_CHART_HORIZONTAL) {
            statsChartPanX = statsChartPanTargetX = progress;
        } else if (scrollDrag == ScrollDrag.STATS_CHART_VERTICAL) {
            statsChartPanY = statsChartPanTargetY = progress;
        } else if (scrollDrag == ScrollDrag.STATS_RESULTS) {
            statsResultScrollTarget = progress * statsResultScrollMaximum;
            statsResultScroll = statsResultScrollTarget;
        } else if (scrollDrag == ScrollDrag.STATS_SUMMARY_PLAYERS) {
            statsSummaryPlayersScrollTarget = progress
                    * statsSummaryPlayersScrollMaximum;
            statsSummaryPlayersScroll = statsSummaryPlayersScrollTarget;
        } else if (scrollDrag == ScrollDrag.LOBBY_CHAT) {
            lobbyChatScroll = progress * lobbyChatScrollMaximum;
        } else if (scrollDrag == ScrollDrag.SETTINGS_DEBUG) {
            settingsDebugScroll = progress * settingsDebugScrollMaximum;
        } else if (scrollDrag == ScrollDrag.SETTINGS_ROWS) {
            float offset = progress * settingsRowScrollMaximum;
            if (settingsSession.section()
                    == GdxSettingsContract.Section.APPEARANCE) {
                settingsAppearanceScroll = offset;
            } else if (settingsSession.section()
                    == GdxSettingsContract.Section.AUDIO) {
                settingsAudioScroll = offset;
            } else if (settingsSession.section()
                    == GdxSettingsContract.Section.SHORTCUTS) {
                settingsShortcutScroll = offset;
            } else if (settingsSession.section()
                    == GdxSettingsContract.Section.GAME) {
                settingsGameScroll = offset;
            }
        } else if (scrollDrag == ScrollDrag.CUSTOM_BOTS) {
            customBotScrollMaximum = customBotMaximumPixelScroll(
                    customBotDisplayRowCount());
            customBotScroll = customBotScrollTarget = MathUtils.clamp(
                    progress * customBotScrollMaximum, 0f,
                    customBotScrollMaximum);
        } else if (scrollDrag == ScrollDrag.NETWORK_BLOCKS) {
            GdxNetworkBlockDialogLayout.Layout layout
                    = GdxNetworkBlockDialogLayout.layout(WIDTH, HEIGHT);
            networkBlocksScroll = progress
                    * GdxNetworkBlockDialogLayout.maximumScroll(
                            activeNetworkBlocks().size(), layout.rows());
        }
    }

    private float currentSettingsRowScroll() {
        return switch (settingsSession.section()) {
            case APPEARANCE -> settingsAppearanceScroll;
            case AUDIO -> settingsAudioScroll;
            case GAME -> settingsGameScroll;
            case SHORTCUTS -> settingsShortcutScroll;
            default -> 0f;
        };
    }

    private static float networkBlocksThumbHeight(
            GdxNetworkBlockDialogLayout.Layout layout, int count) {
        float maximum = GdxNetworkBlockDialogLayout.maximumScroll(count,
                layout.rows());
        if (maximum <= 0f) return layout.scrollbar().height;
        return Math.max(46f, layout.scrollbar().height
                * layout.rows().height / (layout.rows().height + maximum));
    }

    private void clearScrollDrag() {
        scrollDrag = ScrollDrag.NONE;
        scrollDragGrabOffset = 0f;
        scrollPointer.clear();
    }

    @Override
    public boolean keyDown(int keycode) {
        if (fileChooserPending()) return true;
        boolean alt = Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT);
        boolean control = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
        boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
        boolean capturingShortcut = surface == Surface.SETTINGS
                && settingsSession.section() == GdxSettingsContract.Section.SHORTCUTS
                && settingsShortcutCaptureId != null;
        String action = shortcutBindings.actionFor(keycode, alt, control, shift);
        int volumeDirection = GdxVolumeOverlayStyle.shortcutDirection(action);
        if (!capturingShortcut && volumeDirection != 0) {
            if (volumeKeyRepeat.press(keycode, volumeDirection)) {
                adjustFrontendMasterVolume(GdxSettingsContract.MASTER_VOLUME_STEP * volumeDirection);
            }
            return true;
        }
        if (rulesWindow.isOpen()) {
            rulesWindow.key(KeyEvent.KEY_PRESSED, keycode, KeyEvent.CHAR_UNDEFINED);
            return true;
        }
        if (profileBackupSelection != null && frontendNoticeMessage.isBlank()) {
            if (keycode == Input.Keys.ESCAPE) closeProfileBackupSelection();
            return true;
        }
        if (lobbyGalleryInteractive()
                && lobbyGallery.key(KeyEvent.KEY_PRESSED, keycode, KeyEvent.CHAR_UNDEFINED)) return true;
        if (statsBrowserInput() && statsBrowserFocused && keycode != Input.Keys.ESCAPE) {
            statsBrowser.key(KeyEvent.KEY_PRESSED, keycode, KeyEvent.CHAR_UNDEFINED);
            return true;
        }
        editMenu = null;
        if (surface == Surface.LOBBY && activeField == null && control
                && keycode == Input.Keys.C
                && selectedLobbyChatSequence >= 0L) {
            copySelectedLobbyChatMessage();
            return true;
        }
        if (capturingShortcut) {
            GdxShortcutBindings.Assignment assignment = shortcutBindings.assign(
                    settingsShortcutCaptureId, keycode, alt, control, shift);
            if (assignment == GdxShortcutBindings.Assignment.ASSIGNED) {
                settingsShortcutCaptureId = null;
                showShortcutToast("updated");
            } else if (assignment == GdxShortcutBindings.Assignment.CONFLICT) {
                showShortcutToast("conflict");
            } else {
                showShortcutToast("unsupported");
            }
            return true;
        }
        String shortcut = activeField == null ? action : null;
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
        if (applicationExitConfirmation) {
            if (GdxDialogKeyboard.isCancel(keycode)) {
                applicationExitConfirmation = false;
            } else if (GdxDialogKeyboard.isConfirm(keycode)) {
                confirmApplicationExit();
            }
            return true;
        }
        if (updateCheckingModalVisible()) {
            if (GdxDialogKeyboard.isCancel(keycode)) cancelUpdateCheck();
            return true;
        }
        if (surface == Surface.LOBBY
                && lobbyTableTransitionActive(lobbyGameStarting, lobby)) {
            return true;
        }
        if (avatarCropSession != null) {
            if (GdxDialogKeyboard.isCancel(keycode)) {
                closeAvatarCrop();
            } else if (GdxDialogKeyboard.isConfirm(keycode)) {
                applyAvatarCrop();
            }
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
        if (GdxDialogKeyboard.isConfirm(keycode)
                && acceptActiveFrontendDialog()) {
            return true;
        }
        if (GdxDialogKeyboard.isCancel(keycode)) {
            if (modAdultWarning) {
                declineModAdultContent();
                return true;
            }
            if (!frontendNoticeMessage.isBlank()) {
                dismissFrontendNotice();
                return true;
            }
            if (upnpOpenInFlight) {
                return true;
            }
            if (dropdown != Dropdown.NONE) {
                dropdown = Dropdown.NONE;
                return true;
            }
            if (settingsRestartNotice) {
                settingsRestartNotice = false;
                return true;
            }
            if (initialBuyinInput != null) {
                closeInitialBuyinEditor();
                return true;
            }
            if (surface == Surface.SETTINGS
                    && settingsAutoCallDialog != null) {
                closeSettingsAutoCallDialog(false);
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
            if (surface == Surface.LOBBY && networkBlocksOpen) {
                closeNetworkBlocks();
                return true;
            }
            if (surface == Surface.LOBBY
                    && !identityChangeNotices.isEmpty()
                    && !lobbyPasswordDialog && fingerprintDialog == null) {
                closeIdentityChangeNotice();
                return true;
            }
            if (surface == Surface.STATS) {
                if (statsEvidenceOpen) {
                    closeStatsEvidence();
                } else if (statsSyncExclusionsOpen) {
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
            if (customBotManagerOpen) {
                if (customBotDeleteConfirmation) {
                    customBotDeleteConfirmation = false;
                } else {
                    closeCustomBotManager();
                }
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
            if (surface == Surface.LOBBY && lobbyConfirmation != null) {
                closeLobbyConfirmation();
                return true;
            }
            if (surface == Surface.LOBBY
                    && (lobbyEmojiPickerOpen || lobbyGallery.isOpen())) {
                lobbyEmojiPickerOpen = false;
                lobbyGallery.close();
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
                lobbyConfirmation = LobbyConfirmation.LEAVE;
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
            textKeyRepeat.press(keycode, activeField);
            return true;
        }
        if (childModalOwnsInput()) {
            // Child dialogs own the keyboard. Root workflows are themselves
            // the active foreground and continue to receive navigation.
            return true;
        }
        if (surface == Surface.LOBBY
                && (activeField == null || "lobbyChat".equals(activeField))
                && !lobbyChatHistory.isEmpty()
                && (keycode == Input.Keys.UP || keycode == Input.Keys.DOWN)) {
            recallLobbyChat(keycode == Input.Keys.UP ? -1 : 1);
            return true;
        }
        if (activeField != null && handleActiveFieldKey(keycode)) {
            textKeyRepeat.press(keycode, activeField);
            return true;
        }
        if (GdxShortcutBindings.MUTE.equals(shortcut)) {
            toggleMasterSound();
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

    /**
     * Routes Enter to the visible positive action before any surface-level
     * shortcut can see it. This mirrors pointer priority and prevents a modal
     * confirmation from also activating the table or screen below it.
     */
    private boolean acceptActiveFrontendDialog() {
        if (modAdultWarning) {
            acceptModAdultContent();
            return true;
        }
        if (!frontendNoticeMessage.isBlank()) {
            dismissFrontendNotice();
            return true;
        }
        if (surface == Surface.LOBBY && networkBlocksOpen
                && networkBlockUnblockConfirmation != null) {
            confirmNetworkAddressUnblock();
            return true;
        }
        if (surface == Surface.LOBBY && !identityChangeNotices.isEmpty()
                && !networkBlocksOpen && !lobbyPasswordDialog
                && fingerprintDialog == null) {
            closeIdentityChangeNotice();
            return true;
        }
        if (settingsRestartNotice) {
            settingsRestartNotice = false;
            return true;
        }
        if (surface == Surface.SCREENSHOTS && screenshotDeleteConfirmation) {
            if (!screenshotOperationPending) deleteCurrentScreenshot();
            return true;
        }
        if (surface == Surface.STATS
                && statsConfirmation != StatsConfirmation.NONE) {
            if (!statsLoading) confirmStatsDeletion();
            return true;
        }
        if (surface == Surface.MENU && updatePromptOpen) {
            if (!updateInstalling) {
                if (updatePromptForMod) startModUpdateHandoff();
                else startUpdateHandoff();
            }
            return true;
        }
        if (surface == Surface.SETTINGS && voiceNotesOpen
                && (voiceNoteDeleteConfirmation != null
                || voiceNotesPurgeConfirmation)) {
            if (!voiceNotesLoading) confirmVoiceNoteDeletion();
            return true;
        }
        if (initialBuyinInput != null) {
            submitInitialBuyin();
            return true;
        }
        if (surface == Surface.SETTINGS && settingsAutoCallDialog != null) {
            closeSettingsAutoCallDialog(true);
            return true;
        }
        if (surface == Surface.SETTINGS && settingsDiscardConfirmation) {
            finishClosingSettings(false);
            return true;
        }
        if (surface == Surface.LOBBY && lobbyConfirmation != null) {
            confirmLobbyAction();
            return true;
        }
        if (surface == Surface.LOBBY && lobbyPasswordDialog) {
            submitLobbyPassword();
            return true;
        }
        if (customBotManagerOpen) {
            if (customBotDeleteConfirmation) deleteCustomBot();
            else if (customBotSaveEnabled()) saveCustomBot();
            return true;
        }
        if (surface == Surface.NEW_GAME
                && presetDialog == PresetDialog.NAME) {
            submitPresetName();
            return true;
        }
        if (surface == Surface.NEW_GAME
                && presetDialog == PresetDialog.OVERWRITE) {
            confirmOverwritePreset();
            return true;
        }
        if (surface == Surface.NEW_GAME
                && presetDialog == PresetDialog.DELETE) {
            confirmDeletePreset();
            return true;
        }
        if ((surface == Surface.NEW_GAME || surface == Surface.SETTINGS)
                && (blindStructureDialog == BlindStructureDialog.NAME_NEW
                || blindStructureDialog == BlindStructureDialog.NAME_DUPLICATE
                || blindStructureDialog == BlindStructureDialog.NAME_RENAME)) {
            submitBlindStructureName();
            return true;
        }
        if ((surface == Surface.NEW_GAME || surface == Surface.SETTINGS)
                && blindStructureDialog == BlindStructureDialog.DELETE) {
            blindStructureEditor.deleteSelected();
            blindStructureDialog = BlindStructureDialog.EDITOR;
            return true;
        }
        if ((surface == Surface.NEW_GAME || surface == Surface.SETTINGS)
                && blindStructureDialog == BlindStructureDialog.EDITOR) {
            if (blindStructureEditor.dirty()) saveBlindStructures();
            return true;
        }
        if (surface == Surface.STATS && statsSyncExclusionsOpen) {
            saveStatsSyncExclusions();
            return true;
        }
        if (surface == Surface.LOBBY && "lobbyChat".equals(activeField)) {
            sendLobbyText();
            return true;
        }
        if (surface == Surface.SETTINGS) {
            closeSettings(true);
            return true;
        }
        return false;
    }

    private void updateTextKeyRepeat() {
        rulesWindow.updateKeyboard(frameDelta);
        boolean galleryInteractive = lobbyGalleryInteractive();
        lobbyGallery.updateKeyboard(frameDelta, galleryInteractive);
        boolean statsEditing = statsBrowserInput() && statsBrowserFocused;
        if (statsBrowser != null) statsBrowser.updateKeyboard(frameDelta, statsEditing);
        boolean webEditing = rulesWindow.isOpen()
                || (galleryInteractive && lobbyGallery.focused()) || statsEditing;
        boolean nativeEditing = !webEditing
                && (!hasBlockingFrontendModal() || frontendModalTextFieldActive());
        textKeyRepeat.update(frameDelta, nativeEditing ? activeField : null, this::handleActiveFieldKey);
    }

    private void adjustFrontendMasterVolume(float delta) {
        adjustFrontendMasterVolume(delta, true);
    }

    private void adjustFrontendMasterVolume(float delta, boolean playCue) {
        float volume = MathUtils.clamp(
                Math.round((masterVolume() + delta) * 100f) / 100f,
                0f, 1f);
        initialProperties.setProperty("master_volume",
                Float.toString(volume));
        if (preferences != null && surface != Surface.SETTINGS) {
            preferences.saveDeferred();
        }
        syncMusicForSurface();
        volumeOverlayUntil = elapsed + GdxVolumeOverlayStyle.DURATION_SECONDS;
        if (playCue) {
            playPreferenceSound("misc/volume_change.wav", "sonido_volumen", 0.72f);
        }
    }

    @Override
    public boolean keyTyped(char character) {
        if (rulesWindow.isOpen()) {
            rulesWindow.key(KeyEvent.KEY_TYPED, 0, character);
            return true;
        }
        if (lobbyGalleryInteractive() && lobbyGallery.key(KeyEvent.KEY_TYPED, 0, character)) return true;
        if (statsBrowserInput() && statsBrowserFocused) {
            statsBrowser.key(KeyEvent.KEY_TYPED, 0, character);
            return true;
        }
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
        return (surface == Surface.NEW_GAME
                        && newGameTextField(activeField))
                || (lobbyPasswordDialog
                        && "lobbyPassword".equals(activeField))
                || (initialBuyinInput != null && "initialBuyin".equals(activeField))
                || (presetDialog == PresetDialog.NAME
                        && "presetName".equals(activeField))
                || ((blindStructureDialog == BlindStructureDialog.NAME_NEW
                        || blindStructureDialog
                                == BlindStructureDialog.NAME_DUPLICATE
                        || blindStructureDialog
                                == BlindStructureDialog.NAME_RENAME)
                        && "blindStructureName".equals(activeField))
                || (customBotManagerOpen
                        && "customBotName".equals(activeField))
                || (statsSyncExclusionsOpen
                        && "statsExcludeNicks".equals(activeField));
    }

    static boolean newGameTextField(String fieldId) {
        return "nick".equals(fieldId)
                || "password".equals(fieldId)
                || "server".equals(fieldId)
                || "port".equals(fieldId);
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
        } else if ("customBotName".equals(activeField)) {
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
            case "customBotName" -> BotNickname.MAX_CUSTOM_DISPLAY_LENGTH;
            default -> 128;
        };
    }

    private void normalizeLobbyEmojiCaret(String value, int direction,
            boolean extend) {
        if (!"lobbyChat".equals(activeField)) return;
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
            case "lobbyPassword" -> lobbyPasswordDraft;
            case "presetName" -> presetNameDraft;
            case "blindStructureName" -> blindStructureNameDraft;
            case "initialBuyin" -> initialBuyinInput;
            case "customBotName" -> customBotNameDraft;
            case "statsExcludeNicks" -> statsExcludeNicksDraft;
            default -> "";
        };
    }

    private void setActiveValue(String value) {
        switch (activeField) {
            case "nick" -> {
                connection.setNickname(value);
                connection.persistLocalIdentity(preferences);
            }
            case "password" -> connection.setPassword(value);
            case "server" -> {
                connection.setServer(value);
                connection.persistLocalHostEndpoint(preferences);
            }
            case "port" -> {
                if (!Objects.equals(connection.port(), value)
                        && (upnpOpenInFlight || connection.upnp()
                                || upnpReservation != null)) {
                    cancelAndReleaseUpnp();
                }
                connection.setPort(value);
                connection.persistLocalHostEndpoint(preferences);
                verifiedUpnpPort = -1;
            }
            case "lobbyChat" -> lobbyChatDraft = value;
            case "lobbyPassword" -> lobbyPasswordDraft = value;
            case "presetName" -> presetNameDraft = value;
            case "blindStructureName" -> blindStructureNameDraft = value;
            case "initialBuyin" -> initialBuyinInput = value;
            case "customBotName" -> {
                customBotNameDraft = value;
                customBotValidationError = "";
            }
            case "statsExcludeNicks" -> statsExcludeNicksDraft = value;
            default -> {
            }
        }
    }

    private void activateField(String id) {
        if (!Objects.equals(activeField, id)) textKeyRepeat.clear();
        activeField = id;
        textEdit.focus(id, activeValue());
    }

    private void placeFrontendCaret(TextFieldHit field, float pointerX,
            boolean extend) {
        String value = activeValue();
        textEdit.focus(field.id, value);
        float localX = pointerX - field.bounds.x - 22f;
        int target;
        if ("lobbyChat".equals(field.id) && !value.isEmpty()) {
            ComposerWindow window = lobbyComposerWindow(value,
                    field.bounds.width - 44f, true);
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
        textKeyRepeat.clear();
    }

    @Override
    public void dispose() {
        rulesWindow.close();
        disposed = true;
        if (updateCheck != null) updateCheck.cancel(true);
        lobbyGallery.close();
        closeStatsDashboard();
        pokerVideoOverlay.close();
        PokerChromium.shutdown();
        cancelAndReleaseUpnp();
        recoveryLoadGeneration++;
        autoSubmitRecovery = false;
        recoveryExecutor.shutdownNow();
        statsExecutor.shutdownNow();
        networkInfoExecutor.shutdownNow();
        upnpExecutor.shutdown();
        cancelLobbyVoiceRecording();
        audioPreview.stop();
        GdxVoicePlayback.stop();
        clearLobbyMedia();
        disposeAboutEasterEgg();
        disposeScreenshotTexture();
        submissions.cancel();
        closeLobbySubscription();
        closeAvatarCrop();
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
        disposeCustomBotAvatarTextures();
        disposeSelectedAvatarTexture();
        soundIcon.dispose();
        muteIcon.dispose();
        talkIcon.dispose();
        lobbyConnectionCopyIcon.getTexture().dispose();
        lobbyImageIcon.getTexture().dispose();
        lobbyVoiceIcon.getTexture().dispose();
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
        customBotManagerMusic.stop();
        customBotManagerMusic.dispose();
        for (Texture texture : lobbyAvatarTextures.values()) {
            texture.dispose();
        }
        lobbyAvatarTextures.clear();
        for (Texture texture : handGeneratorCardTextures.values()) {
            texture.dispose();
        }
        handGeneratorCardTextures.clear();
        emojiAtlas.release();
        avatarShader.dispose();
        roundedTextureShader.dispose();
        modalBackdropBlur.dispose();
        nestedModalBackdropBlur.dispose();
        titleFont.dispose();
        headingFont.dispose();
        settingsStepperSignFont.dispose();
        actionFont.dispose();
        buttonMediumFont.dispose();
        buttonCompactFont.dispose();
        buttonTinyFont.dispose();
        uiFont.dispose();
        volumeOverlayFont.dispose();
        smallFont.dispose();
        lobbyComposerFont.dispose();
        fpsFont.dispose();
        debugLogFont.dispose();
        debugLogBoldFont.dispose();
        tinyFont.dispose();
        versionFont.dispose();
    }

    @Override public boolean keyUp(int keycode) {
        volumeKeyRepeat.release(keycode);
        textKeyRepeat.release(keycode);
        if (rulesWindow.isOpen()) {
            rulesWindow.key(KeyEvent.KEY_RELEASED, keycode, KeyEvent.CHAR_UNDEFINED);
            return true;
        }
        if (lobbyGalleryInteractive()
                && lobbyGallery.key(KeyEvent.KEY_RELEASED, keycode, KeyEvent.CHAR_UNDEFINED)) return true;
        if (statsBrowserInput() && statsBrowserFocused) {
            statsBrowser.key(KeyEvent.KEY_RELEASED, keycode, KeyEvent.CHAR_UNDEFINED);
            return true;
        }
        if (keycode == Input.Keys.F9 && lobbyVoiceRecorder != null) {
            finishLobbyVoiceRecording(false);
            return true;
        }
        return false;
    }
    @Override public boolean touchCancelled(int x, int y, int p, int b) {
        if (rulesWindow.cancel(p, b)) return true;
        if (lobbyGallery.cancel(p, b)) return true;
        if (statsBrowserPointer.releasePrimary(p, b)) {
            if (statsBrowser != null) statsBrowser.unfocus();
            return true;
        }
        if (statsBrowserPointer.releaseSecondary(p, b)) return true;
        if (pokerVideoOverlay.cancelTouch(p, b)) return true;
        if (avatarCropDrag != GdxAvatarCropModel.DragTarget.NONE
                && avatarCropPointer == p) {
            avatarCropDrag = GdxAvatarCropModel.DragTarget.NONE;
            avatarCropPointer = -1;
            avatarCropResizeStart = null;
            pressedHit = null;
            return true;
        }
        if (statsEvidenceWindowPointer.release(p, b)) {
            statsEvidenceWindowDrag = StatsEvidenceWindowDrag.NONE;
            statsEvidenceResizeHandle =
                    GdxFloatingGameLogLayout.ResizeHandle.NONE;
            pressedHit = null;
            return true;
        }
        if (lobbyVoicePointer.release(p, b)) {
            pressedHit = null;
            clearPointerRepeat();
            finishLobbyVoiceRecording(true);
            return true;
        }
        if (scrollPointer.release(p, b)) {
            scrollDrag = ScrollDrag.NONE;
            scrollDragGrabOffset = 0f;
            pressedHit = null;
            clearPointerRepeat();
            return true;
        }
        pressedHit = null;
        clearPointerRepeat();
        return hasBlockingFrontendModal();
    }
    @Override public boolean mouseMoved(int x, int y) {
        pointer.set(x, y);
        viewport.unproject(pointer);
        if (rulesWindow.isOpen()) {
            rulesWindow.mouseMoved(pointer.x, pointer.y);
            return true;
        }
        if (lobbyGalleryInteractive() && lobbyGallery.mouseMoved(pointer.x, pointer.y)) return true;
        if (statsBrowserInput()) {
            if (STATS_WEB_BOUNDS.contains(pointer)) {
                statsBrowserMouse(MouseEvent.MOUSE_MOVED);
                return true;
            }
            statsBrowserMouse(MouseEvent.MOUSE_EXITED);
        }
        if (statsOwnsPointer()) return true;
        if (pokerVideoOverlay.mouseMoved(pointer.x, pointer.y)) return true;
        return hasBlockingFrontendModal();
    }
    @Override
    public boolean scrolled(float amountX, float amountY) {
        if (fileChooserPending()) return true;
        pointer.set(Gdx.input.getX(), Gdx.input.getY());
        viewport.unproject(pointer);
        if (rulesWindow.isOpen()) {
            rulesWindow.scrolled(pointer.x, pointer.y, amountY);
            return true;
        }
        if (lobbyGalleryInteractive() && lobbyGallery.scrolled(pointer.x, pointer.y, amountY)) return true;
        if (statsOwnsPointer()) {
            Rectangle bounds = STATS_WEB_BOUNDS;
            if (statsBrowserInput() && bounds.contains(pointer)) {
                statsBrowser.wheel((pointer.x - bounds.x) / bounds.width,
                        (bounds.y + bounds.height - pointer.y) / bounds.height, amountY);
            }
            return true;
        }
        if (!hasBlockingFrontendModal()
                && pokerVideoOverlay.scrolled(pointer.x, pointer.y, amountY)) {
            return true;
        }
        if (frontendNoticeOwnsPointer()) return true;
        if (avatarCropSession != null) {
            pointer.set(Gdx.input.getX(), Gdx.input.getY());
            viewport.unproject(pointer);
            if (amountY != 0f && avatarCropViewport.contains(pointer)) {
                resizeAvatarCrop((float) Math.pow(1.12d, amountY));
            }
            return true;
        }
        if (statsEvidenceOpen && amountY != 0f) {
            pointer.set(Gdx.input.getX(), Gdx.input.getY());
            viewport.unproject(pointer);
            if (statsEvidenceLayout().content().contains(pointer)) {
                statsEvidenceScrollTarget = MathUtils.clamp(
                        statsEvidenceScrollTarget
                                + amountY * STATS_EVIDENCE_LINE_HEIGHT * 3f,
                        0f, statsEvidenceScrollMaximum);
            }
            return true;
        }
        if (networkBlocksOpen && amountY != 0f) {
            if (networkBlockUnblockConfirmation != null) return true;
            GdxNetworkBlockDialogLayout.Layout layout
                    = GdxNetworkBlockDialogLayout.layout(WIDTH, HEIGHT);
            networkBlocksScroll = GdxSettingsLayout.pixelScrollAfterWheel(
                    networkBlocksScroll,
                    GdxNetworkBlockDialogLayout.maximumScroll(
                            activeNetworkBlocks().size(), layout.rows()),
                    amountY);
            return true;
        }
        if (statsPicker != StatsPicker.NONE && amountY != 0f) {
            statsPickerScrollTarget = statsPickerScrollAfterWheel(
                    statsPickerScrollTarget, statsPickerScrollMaximum,
                    amountY);
            return true;
        }
        if (customBotManagerOpen
                && !customBotDeleteConfirmation && amountY != 0f) {
            pointer.set(Gdx.input.getX(), Gdx.input.getY());
            viewport.unproject(pointer);
            if (customBotRowsViewport.contains(pointer)) {
                customBotScrollMaximum = customBotMaximumPixelScroll(
                        customBotDisplayRowCount());
                customBotScrollTarget = customBotScrollAfterWheel(
                        customBotScrollTarget, customBotScrollMaximum,
                        amountY);
            }
            return true;
        }
        if (dropdown != Dropdown.NONE && amountY != 0f) {
            int maximum = Math.max(0, dropdownOptions(dropdown).size()
                    - GdxSettingsLayout.POPUP_MAX_VISIBLE_ROWS);
            dropdownScroll = MathUtils.clamp(dropdownScroll
                    + (amountY > 0f ? 1 : -1), 0, maximum);
            return true;
        }
        if (surface == Surface.SETTINGS && amountY != 0f
                && !settingsScrollBlockedByChildDialog()) {
            scrollActiveSettingsSection(amountY);
            return true;
        }
        if (childModalOwnsInput() && dropdown == Dropdown.NONE) {
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
            if (statsGameIndex >= 0 && amountY != 0f
                    && statsSummaryPlayersViewport.width > 0f
                    && statsSummaryPlayersViewport.contains(pointer)) {
                float maximum = statsHandIndex >= 0 ? statsSummaryPlayersScrollMaximum
                        : statsSummaryPlayersMaximum(statsGames.get(statsGameIndex).players().size());
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
                && !lobbyEmojiPickerOpen) {
            lobbyChatScroll = lobbyPixelScrollAfterWheel(lobbyChatScroll,
                    lobbyChatScrollMaximum, amountY);
            return true;
        }
        return hasBlockingFrontendModal();
    }

    private void scrollActiveSettingsSection(float amountY) {
        GdxSettingsContract.Section section = settingsSession.section();
        if (section == GdxSettingsContract.Section.DEBUG) {
            settingsDebugScroll = lobbyPixelScrollAfterWheel(
                    settingsDebugScroll, settingsDebugScrollMaximum,
                    amountY);
            return;
        }
        GdxSettingsLayout.PixelRows rows = currentSettingsPixelRows();
        if (rows == null) return;
        if (section == GdxSettingsContract.Section.SHORTCUTS) {
            settingsShortcutScroll = GdxSettingsLayout.pixelScrollAfterWheel(
                    settingsShortcutScroll, rows.maximum(), amountY);
        } else if (section == GdxSettingsContract.Section.AUDIO) {
            settingsAudioScroll = GdxSettingsLayout.pixelScrollAfterWheel(
                    settingsAudioScroll, rows.maximum(), amountY);
        } else if (section == GdxSettingsContract.Section.APPEARANCE) {
            settingsAppearanceScroll = GdxSettingsLayout
                    .pixelScrollAfterWheel(settingsAppearanceScroll,
                            rows.maximum(), amountY);
        } else if (section == GdxSettingsContract.Section.GAME) {
            settingsGameScroll = GdxSettingsLayout.pixelScrollAfterWheel(
                    settingsGameScroll, rows.maximum(), amountY);
        }
    }

    /** Computes wheel limits from the active subsection, never from a stale frame. */
    private GdxSettingsLayout.PixelRows currentSettingsPixelRows() {
        int totalRows;
        float offset;
        List<GdxSettingsContract.RowGroup> groups = List.of();
        GdxSettingsContract.Section section = settingsSession.section();
        if (section == GdxSettingsContract.Section.SHORTCUTS) {
            totalRows = GdxSettingsContract.CONTROL_ROWS
                    + shortcutBindings.editableEntries(gameText).size();
            groups = GdxSettingsContract.controlGroups();
            offset = settingsShortcutScroll;
        } else if (section == GdxSettingsContract.Section.AUDIO) {
            int pageIndex = MathUtils.clamp(settingsAudioPage, 0,
                    GdxSettingsContract.AUDIO_PAGES.size() - 1);
            GdxSettingsContract.TogglePage page =
                    GdxSettingsContract.audioPage(pageIndex,
                            presentationSettings, gameText.language());
            totalRows = GdxSettingsContract.audioRowCount(page);
            groups = GdxSettingsContract.audioGroups(page);
            offset = settingsAudioScroll;
        } else if (section == GdxSettingsContract.Section.APPEARANCE) {
            if (settingsAppearancePage == 0) {
                totalRows = GdxSettingsContract.appearanceTableRowCount();
                groups = GdxSettingsContract.appearanceTableGroups();
            } else if (GdxSettingsContract.isAppearanceGraphicsPage(
                    settingsAppearancePage)) {
                totalRows = GdxSettingsContract
                        .APPEARANCE_GRAPHICS_ROW_COUNT;
                groups = GdxSettingsContract.appearanceGraphicsGroups();
            } else {
                GdxSettingsContract.TogglePage page =
                        GdxSettingsContract.appearanceTogglePage(
                                settingsAppearancePage);
                totalRows = GdxSettingsContract.appearanceRowCount(page);
                groups = GdxSettingsContract.appearanceGroups(page);
            }
            offset = settingsAppearanceScroll;
        } else if (section == GdxSettingsContract.Section.GAME) {
            totalRows = switch (settingsGamePage) {
                case 0 -> BLIND_ROW_COUNT;
                case 1 -> 8;
                case 2 -> 6;
                default -> 9;
            };
            groups = GdxSettingsContract.gameGroups(settingsGamePage);
            offset = settingsGameScroll;
        } else {
            return null;
        }
        GdxSettingsLayout.Frame frame = GdxSettingsLayout.frame(
                viewport.getWorldWidth(), viewport.getWorldHeight(),
                settingsSections().size(), settingsSubpageLabels().size());
        float firstRowY = frame.firstRowY();
        if (section == GdxSettingsContract.Section.AUDIO) {
            int pageIndex = MathUtils.clamp(settingsAudioPage, 0,
                    GdxSettingsContract.AUDIO_PAGES.size() - 1);
            GdxSettingsContract.TogglePage page =
                    GdxSettingsContract.audioPage(pageIndex,
                            presentationSettings, gameText.language());
            String modStatus = GdxGameAudioResources.isJokeSoundsPage(page)
                    ? GdxGameAudioResources.jokeModStatus(
                            presentationSettings, gameText.language(),
                            gameText)
                    : "";
            firstRowY = GdxSettingsLayout.statusBand(firstRowY,
                    firstRowY + GdxSettingsLayout.ROW_HEIGHT,
                    !modStatus.isBlank()).firstRowY();
        }
        return GdxSettingsLayout.pixelRows(firstRowY,
                frame.content().y + GdxSettingsLayout.CONTENT_VERTICAL_INSET,
                firstRowY + GdxSettingsLayout.ROW_HEIGHT,
                totalRows, offset, groups);
    }

    private record TextItem(BitmapFont font, String text, float x, float y,
            Color color, boolean centered) {
    }

    record StatsCardGlyph(String rank, String suit, boolean red) {
    }

    private record LobbyAvatarItem(Texture texture, float x, float y,
            float size) {
    }

    private record LobbyChatLayout(List<LobbyChatMessage> messages,
            List<LobbyMessageLayout> layouts, float contentHeight) {
    }

    private record LobbyMessageLayout(String header, float width, float height,
            List<List<LobbyTextRun>> lines, PokerVideoSearch.Video video) {
    }

    private record LobbyChatBubbleItem(long sequence, float x, float y, float width,
            float height, Color border, Color fill) {
    }

    private record LobbyVoiceControlItem(float playX, float stopX, float y,
            boolean active, boolean paused) {
    }

    private record UiImageItem(Texture texture, TextureRegion region,
            float x, float y, float width, float height, Color tint,
            boolean pickerEmoji) {

        private UiImageItem(Texture texture, float x, float y,
                float width, float height) {
            this(texture, null, x, y, width, height, Color.WHITE, false);
        }

        private UiImageItem(Texture texture, float x, float y,
                float width, float height, Color tint) {
            this(texture, null, x, y, width, height, tint, false);
        }

        private UiImageItem(TextureRegion region, float x, float y,
                float width, float height) {
            this(null, region, x, y, width, height, Color.WHITE, false);
        }

        private UiImageItem(TextureRegion region, float x, float y,
                float width, float height, Color tint) {
            this(null, region, x, y, width, height, tint, false);
        }

        private static UiImageItem pickerEmoji(TextureRegion region,
                float x, float y, float size) {
            return new UiImageItem(null, region, x, y, size, size,
                    Color.WHITE, true);
        }

        private void drawImage(SpriteBatch batch, Viewport viewport) {
            batch.setColor(tint);
            if (region == null) {
                batch.draw(texture, x, y, width, height);
            } else if (pickerEmoji) {
                GdxEmojiAtlas.drawAligned(batch, region, x, y, width, height,
                        viewport);
            } else {
                GdxEmojiAtlas.drawNative(batch, region, x, y, width, height,
                        viewport);
            }
        }
    }

    private record LobbyTextRun(String text, int emojiId, float width) {
    }

    private record ComposerWindow(String raw, int sourceStart, int sourceEnd,
            float caretOffset,
            float selectionOffset, float selectionWidth) {
    }

    private record FrontendInputWindow(String text, int sourceStart,
            int sourceEnd, float caretOffset,
            float selectionOffset, float selectionWidth) {
    }

    static final class LobbyGifClock {
        private long lastVisibleFrame = Long.MIN_VALUE;
        private double seconds;

        float update(long frame, float delta) {
            // The decoder queue stops advancing when the image is not drawn.
            // Resume from the previous position after a gap in visibility.
            if (frame == lastVisibleFrame + 1L) seconds += delta;
            lastVisibleFrame = frame;
            return (float) seconds;
        }
    }

    private static final class LobbyMedia {
        private LobbyGifClock gifClock;
        private boolean failed;
        private Texture image;
        private StreamingGifTextureAnimation.PreparedLooping gifSource;
        private StreamingGifTextureAnimation.FrameSize gifOriginalSize;
        private StreamingGifTextureAnimation.FrameSize gifBounds;
        private StreamingGifTextureAnimation gif;

        private Texture frameAt(StreamingGifTextureAnimation.FrameSize bounds,
                long frame, float delta) {
            if (gifSource == null || failed) return image;
            if (!bounds.equals(gifBounds)) {
                gifBounds = bounds;
                var size = gifOriginalSize.fitWithin(
                        bounds.width(), bounds.height());
                if (gif == null || gif.width() != size.width()
                        || gif.height() != size.height()) {
                    if (gif != null) gif.dispose();
                    gif = StreamingGifTextureAnimation.loadLoopingMipmapped(
                            gifSource, size);
                    gifClock = new LobbyGifClock();
                }
            }
            if (gif.failed()) {
                failed = true;
                return null;
            }
            return gif.frameAt(gifClock.update(frame, delta));
        }

        private void dispose() {
            if (image != null) image.dispose();
            if (gif != null) gif.dispose();
            image = null;
            gifSource = null;
            gif = null;
        }
    }

    private enum Surface {
        MENU, NEW_GAME, LOBBY, SETTINGS, STATS, SCREENSHOTS
    }

    private enum ScrollDrag {
        NONE, DROPDOWN, LOBBY_CHAT, SETTINGS_ROWS, SETTINGS_DEBUG, STATS_PICKER,
        STATS_EVIDENCE, STATS_RESULTS, STATS_SUMMARY_PLAYERS, STATS_CHART_HORIZONTAL,
        STATS_CHART_VERTICAL, NETWORK_BLOCKS, CUSTOM_BOTS
    }

    private enum StatsEvidenceWindowDrag {
        NONE, MOVE, RESIZE
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
        START, LEAVE, KICK
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
        NONE, PROFILE, BLIND_STRUCTURE, BLIND_LEVEL, BLIND_STRUCTURE_EDITOR,
        DECK, CARD_BACK, FELT, HOST_ADDRESS, SERVER_HISTORY, LANGUAGE,
        AUDIO_OUTPUT, AUDIO_CAPTURE, VOICE_RETENTION, WINDOW_MODE, FPS_LIMIT,
        MSAA, CARD_RENDERING,
        APPEARANCE_CHOICE, BLIND_UNIT, ANTE_MODE, REBUY_CAP, BOT_DIFFICULTY, RABBIT,
        CUSTOM_BOT_DIFFICULTY, CUSTOM_BOT_PERSONALITY
    }

    private enum ButtonTone {
        NEUTRAL, FEATURED, POSITIVE, DANGER
    }

    private enum AvatarCropTarget {
        LOCAL, CUSTOM_BOT
    }

    private static final class AvatarCropSession {
        private final AvatarCropTarget target;
        private final NewGameConnectionDraft connectionTarget;
        private final Pixmap pixmap;
        private final Texture texture;
        private GdxAvatarCropModel.Crop crop;

        private AvatarCropSession(AvatarCropTarget target,
                NewGameConnectionDraft connectionTarget, Pixmap pixmap,
                Texture texture, GdxAvatarCropModel.Crop crop) {
            this.target = Objects.requireNonNull(target, "target");
            this.connectionTarget = connectionTarget;
            this.pixmap = Objects.requireNonNull(pixmap, "pixmap");
            this.texture = Objects.requireNonNull(texture, "texture");
            this.crop = Objects.requireNonNull(crop, "crop");
        }
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
