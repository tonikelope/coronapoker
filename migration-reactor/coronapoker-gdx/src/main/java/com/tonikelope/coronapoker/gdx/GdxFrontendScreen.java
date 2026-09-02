package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/**
 * Native menu and staged NewGameDialog replacement. The preview launcher uses
 * the same screen without a session gateway; production injects the real
 * asynchronous handoff owned by the application shell.
 */
final class GdxFrontendScreen extends ApplicationAdapter implements InputProcessor {

    private static final float WIDTH = 1920f;
    private static final float HEIGHT = 1080f;
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

    private final FitViewport viewport = new FitViewport(WIDTH, HEIGHT);
    private final List<TextItem> texts = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, Float> toggleAnimations = new HashMap<>();
    private final Map<String, Float> hoverAnimations = new HashMap<>();
    private final GlyphLayout glyph = new GlyphLayout();
    private final Vector2 pointer = new Vector2();
    private final Properties initialProperties;
    private final NewGameSubmissionCoordinator submissions;
    private final Consumer<NewGameSubmissionCoordinator.OpenedSession> sessionAccepted;
    private final Runnable sessionReturnedToMenu;
    private NewGameConnectionDraft connection;
    private NewGameTableDraft table = new NewGameTableDraft();
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private Texture feltTexture;
    private BitmapFont titleFont;
    private BitmapFont headingFont;
    private BitmapFont actionFont;
    private BitmapFont uiFont;
    private BitmapFont smallFont;
    private BitmapFont tinyFont;
    private int page;
    private String activeField;
    private boolean fullscreen;
    private long toastUntil;
    private String toast = "";
    private float elapsed;
    private float frameDelta;
    private Hit pressedHit;
    private Surface surface;
    private int historyIndex = -1;
    private boolean disposed;
    private LobbySession lobbySession;
    private LobbySnapshot lobby;
    private AutoCloseable lobbySubscription;
    private String lobbyChatDraft = "";
    private String selectedParticipant;
    private boolean lobbyCommandPending;
    private LobbyConfirmation lobbyConfirmation;

    GdxFrontendScreen() {
        this(previewProperties(), null, null, false);
    }

    GdxFrontendScreen(NewGameConnectionDraft connection) {
        this(connection, previewProperties(), null, request -> { }, () -> { }, false);
    }

    GdxFrontendScreen(boolean startAtMenu) {
        this(previewProperties(), null, null, startAtMenu);
    }

    GdxFrontendScreen(PreferencesService preferences,
            NewGameSessionGateway gateway,
            Consumer<NewGameSubmissionCoordinator.OpenedSession> sessionAccepted,
            Runnable sessionReturnedToMenu) {
        this(Objects.requireNonNull(preferences, "preferences").properties(),
                preferences, Objects.requireNonNull(gateway, "gateway"), true,
                Objects.requireNonNull(sessionAccepted, "sessionAccepted"),
                Objects.requireNonNull(sessionReturnedToMenu, "sessionReturnedToMenu"));
    }

    private GdxFrontendScreen(Properties properties,
            PreferencesService preferences, NewGameSessionGateway gateway,
            boolean startAtMenu) {
        this(properties, preferences, gateway, startAtMenu, request -> { }, () -> { });
    }

    private GdxFrontendScreen(Properties properties,
            PreferencesService preferences, NewGameSessionGateway gateway,
            boolean startAtMenu,
            Consumer<NewGameSubmissionCoordinator.OpenedSession> sessionAccepted,
            Runnable sessionReturnedToMenu) {
        this(defaultConnection(properties, NewGameConnectionDraft.Mode.CREATE),
                properties,
                preferences == null ? null
                        : new NewGameSubmissionCoordinator(preferences, gateway),
                sessionAccepted, sessionReturnedToMenu, startAtMenu);
    }

    private GdxFrontendScreen(NewGameConnectionDraft connection,
            Properties properties, NewGameSubmissionCoordinator submissions,
            Consumer<NewGameSubmissionCoordinator.OpenedSession> sessionAccepted,
            Runnable sessionReturnedToMenu,
            boolean startAtMenu) {
        this.connection = Objects.requireNonNull(connection, "connection");
        initialProperties = Objects.requireNonNull(properties, "properties");
        this.submissions = submissions;
        this.sessionAccepted = Objects.requireNonNull(sessionAccepted, "sessionAccepted");
        this.sessionReturnedToMenu = Objects.requireNonNull(
                sessionReturnedToMenu, "sessionReturnedToMenu");
        surface = startAtMenu ? Surface.MENU : Surface.NEW_GAME;
    }

    private static Properties previewProperties() {
        Properties defaults = new Properties();
        defaults.setProperty("nick", "Jugador");
        return defaults;
    }

    private static NewGameConnectionDraft defaultConnection(Properties properties,
            NewGameConnectionDraft.Mode mode) {
        return NewGameConnectionDraft.from(properties, mode);
    }

    @Override
    public void create() {
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        feltTexture = new Texture(Gdx.files.internal("images/tapete_verde.jpg"));
        feltTexture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        feltTexture.setWrap(TextureWrap.Repeat, TextureWrap.Repeat);
        FreeTypeFontGenerator titleGenerator = new FreeTypeFontGenerator(
                Gdx.files.internal("fonts/Montserrat-Bold.ttf"));
        titleFont = font(titleGenerator, 58, 0.35f);
        titleGenerator.dispose();
        FreeTypeFontGenerator displayGenerator = new FreeTypeFontGenerator(
                Gdx.files.internal("fonts/Montserrat-SemiBold.ttf"));
        headingFont = font(displayGenerator, 30, 0.2f);
        actionFont = font(displayGenerator, 21, 0f);
        displayGenerator.dispose();
        FreeTypeFontGenerator bodyGenerator = new FreeTypeFontGenerator(
                Gdx.files.internal("fonts/Inter-Medium.ttf"));
        uiFont = font(bodyGenerator, 24, 0f);
        smallFont = font(bodyGenerator, 18, 0f);
        tinyFont = font(bodyGenerator, 15, 0f);
        bodyGenerator.dispose();
        Gdx.input.setInputProcessor(this);
        Gdx.input.setCursorCatched(false);
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
        ScreenUtils.clear(BACKGROUND);
        viewport.apply();
        viewport.getCamera().update();
        shapes.setProjectionMatrix(viewport.getCamera().combined);
        batch.setProjectionMatrix(viewport.getCamera().combined);
        texts.clear();
        hits.clear();

        drawFeltBackground();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (surface == Surface.MENU) {
            drawMainMenu();
        } else if (surface == Surface.LOBBY) {
            drawLobby();
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
        shapes.end();

        batch.begin();
        for (TextItem item : texts) {
            item.font.setColor(item.color);
            glyph.setText(item.font, item.text);
            float x = item.centered ? item.x - glyph.width / 2f : item.x;
            item.font.draw(batch, item.text, x, item.y);
        }
        batch.end();
    }

    void openLobby(LobbySession session) {
        closeLobbySubscription();
        lobbySession = Objects.requireNonNull(session, "session");
        lobby = session.snapshot();
        lobbyChatDraft = "";
        selectedParticipant = null;
        lobbyCommandPending = false;
        lobbyConfirmation = null;
        activeField = null;
        surface = Surface.LOBBY;
        lobbySubscription = session.subscribe(next -> Gdx.app.postRunnable(() -> {
            if (!disposed && lobbySession == session) {
                lobby = next;
            }
        }));
    }

    void showSessionError(String detail) {
        showToast(detail == null || detail.isBlank()
                ? "No se pudo abrir la mesa" : detail);
    }

    private void drawFeltBackground() {
        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(feltTexture, 0f, 0f, WIDTH, HEIGHT,
                0f, 0f, WIDTH / feltTexture.getWidth(),
                HEIGHT / feltTexture.getHeight());
        batch.setColor(Color.WHITE);
        batch.end();
    }

    private void drawMainMenu() {
        shapes.setColor(new Color(0x31445f99));
        shapes.rect(0f, 989f, WIDTH, 1f);
        text(tinyFont, "PREVIEW · NAVEGACIÓN GDX · SIN SESIÓN", 1725f,
                1039f, ORANGE, true);

        panel(515f, 155f, 890f, 755f, "");
        text(titleFont, "CORONAPOKER", 964f, 843f,
                new Color(0x000000aa), true);
        text(titleFont, "CORONAPOKER", 960f, 847f, GOLD, true);
        button(595f, 625f, 730f, 82f, "CREAR TIMBA", true,
                () -> openNewGame(NewGameConnectionDraft.Mode.CREATE));
        button(595f, 520f, 730f, 82f, "UNIRME A TIMBA", false,
                () -> openNewGame(NewGameConnectionDraft.Mode.JOIN));
        button(595f, 415f, 730f, 82f, "ESTADÍSTICAS", false,
                () -> showToast("Estadísticas · pantalla GDX pendiente de conexión"));
        button(595f, 310f, 350f, 82f, "AJUSTES", false,
                () -> showToast("Ajustes · pantalla GDX pendiente de conexión"));
        choice(975f, 310f, 350f, "", "Español",
                () -> showToast("Español / English"));
        button(595f, 205f, 350f, 82f, "ACERCA DE", false,
                () -> showToast("Acerca de CoronaPoker"));
        button(975f, 205f, 350f, 82f, "SALIR", false, Gdx.app::exit);

        keyHint(535f, 75f, "F11", "PANTALLA COMPLETA");
        text(smallFont, "SONIDO", 1285f, 107f, MUTED, false);
        drawSpeakerIcon(1370f, 100f, CYAN);
        hit(1250f, 70f, 150f, 55f,
                () -> showToast("Sonido · control GDX pendiente de conexión"));
    }

    private void drawLobby() {
        LobbySnapshot state = lobby;
        if (state == null) {
            return;
        }
        shapes.setColor(new Color(0x31445f99));
        shapes.rect(0f, 989f, WIDTH, 1f);
        text(smallFont, "CORONAPOKER  /", 55f, 1041f, MUTED, false);
        text(smallFont, "SALA DE ESPERA", 225f, 1041f, GOLD, false);
        text(tinyFont, lobbyPhaseText(state), 1860f, 1039f,
                state.phase() == LobbySnapshot.Phase.ERROR ? ORANGE : CYAN, true);
        text(titleFont, "SALA DE ESPERA", 59f, 935f,
                new Color(0x000000aa), false);
        text(titleFont, "SALA DE ESPERA", 55f, 939f, GOLD, false);

        panel(35f, 180f, 430f, 650f, "PARTICIPANTES");
        text(smallFont, state.participants().size() + "/"
                + LobbySnapshot.MAX_PARTICIPANTS, 425f, 794f, CYAN, true);
        float participantY = 720f;
        for (LobbyParticipant participant : state.participants()) {
            drawLobbyParticipant(participant, 65f, participantY, 370f, 48f);
            participantY -= 52f;
        }

        panel(495f, 180f, 900f, 650f, "CHAT DE LA TIMBA");
        drawLobbyMessages(state.chat(), 535f, 748f, 820f);
        drawLobbyChatInput(525f, 215f, 500f);
        button(1040f, 215f, 100f, 70f, "EMOJI", false,
                () -> showToast("Selector de emoji GDX pendiente"));
        button(1150f, 215f, 100f, 70f, "IMAGEN", false,
                () -> showToast("Enviar imagen GDX pendiente"));
        button(1260f, 215f, 105f, 70f, "ENVIAR", true,
                this::sendLobbyChat, !lobbyCommandPending && !lobbyChatDraft.isBlank());

        panel(1425f, 180f, 460f, 650f, "TIMBA");
        text(tinyFont, "SERVIDOR", 1460f, 748f, MUTED, false);
        textFit(smallFont, state.serverAddress(), 1460f, 718f,
                Color.WHITE, false, 390f);
        drawLobbyGameInfo(state, 1460f, 645f);
        if (state.host()) {
            button(1460f, 392f, 390f, 64f, "AÑADIR BOT", false,
                    () -> submitLobbyCommand(new LobbyCommand.AddBot(), null),
                    !lobbyCommandPending
                            && state.participants().size() < LobbySnapshot.MAX_PARTICIPANTS
                            && !state.startingOrStarted());
            boolean kickEnabled = selectedRemoteParticipant(state) != null
                    && !state.startingOrStarted();
            button(1460f, 312f, 390f, 64f, "EXPULSAR JUGADOR", false,
                    this::kickSelectedParticipant,
                    !lobbyCommandPending && kickEnabled);
            button(1460f, 215f, 390f, 76f, "¡A JUGAR!", true,
                    () -> lobbyConfirmation = LobbyConfirmation.START,
                    !lobbyCommandPending && state.participants().size() >= 2
                            && !state.startingOrStarted());
        } else {
            textFit(uiFont, lobbyPhaseText(state), 1655f, 306f,
                    MUTED, true, 360f);
        }

        button(35f, 55f, 220f, 70f, "SALIR", false,
                () -> lobbyConfirmation = LobbyConfirmation.LEAVE,
                !lobbyCommandPending);
        toggle(495f, 52f, 620f, "NOTIFICACIONES DEL CHAT DURANTE EL JUEGO",
                state.chatNotifications(), () -> submitLobbyCommand(
                        new LobbyCommand.SetChatNotifications(
                                !state.chatNotifications()), null),
                !lobbyCommandPending);
        button(1640f, 55f, 245f, 70f, "AJUSTES", false,
                () -> showToast("Ajustes GDX pendientes de conexión"));

        if (lobbyConfirmation != null) {
            drawLobbyConfirmation();
        }
    }

    private void drawLobbyParticipant(LobbyParticipant participant, float x,
            float y, float w, float h) {
        boolean selected = participant.nickname().equals(selectedParticipant);
        Color border = selected ? GOLD : participant.secure() ? LINE : ORANGE;
        outerBox(x, y, w, h, border,
                selected ? new Color(0x20324cee) : PANEL_LIGHT);
        shapes.setColor(participant.connected() ? CYAN : ORANGE);
        shapes.circle(x + 23f, y + h / 2f, 7f, 20);
        textFit(smallFont, participant.nickname(), x + 43f, y + 31f,
                participant.asyncWaiting() ? DISABLED : Color.WHITE,
                false, 230f);
        if (participant.latencyAvailable()) {
            text(tinyFont, (participant.latency() >= 0
                    ? participant.latency() : "-") + " ms",
                    x + w - 22f, y + 30f, MUTED, true);
        }
        hit(x, y, w, h, () -> selectedParticipant = participant.nickname());
    }

    private void drawLobbyMessages(List<LobbyChatMessage> messages, float x,
            float top, float width) {
        int first = Math.max(0, messages.size() - 8);
        float y = top;
        for (int i = first; i < messages.size(); i++) {
            LobbyChatMessage message = messages.get(i);
            Color color = message.nickname().equals(lobby.localNickname())
                    ? CYAN : Color.WHITE;
            textFit(tinyFont, lobbyMessageText(message), x, y, color,
                    false, width);
            shapes.setColor(new Color(0x31445f55));
            shapes.rect(x, y - 26f, width, 1f);
            y -= 58f;
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
        boolean focused = "lobbyChat".equals(activeField);
        outerBox(x, y, w, 70f,
                focused || hovered(x, y, w, 70f) ? CYAN : LINE,
                pressed(x, y, w, 70f) ? new Color(0x0b1424ff) : PANEL_LIGHT);
        textFit(uiFont, lobbyChatDraft.isEmpty() ? "—" : lobbyChatDraft,
                x + 22f, y + 44f,
                lobbyChatDraft.isEmpty() ? DISABLED : Color.WHITE,
                false, w - 44f);
        hit(x, y, w, 70f, () -> activeField = "lobbyChat");
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
        panel(560f, 350f, 800f, 330f, "");
        textFit(headingFont, prompt, 960f, 560f, Color.WHITE, true, 700f);
        button(635f, 405f, 300f, 75f, "CANCELAR", false,
                () -> lobbyConfirmation = null);
        button(985f, 405f, 300f, 75f,
                lobbyConfirmation == LobbyConfirmation.START ? "¡A JUGAR!" : "SALIR",
                true, this::confirmLobbyAction);
    }

    private void confirmLobbyAction() {
        LobbyConfirmation action = lobbyConfirmation;
        lobbyConfirmation = null;
        if (action == LobbyConfirmation.START) {
            submitLobbyCommand(new LobbyCommand.StartGame(), null);
        } else {
            submitLobbyCommand(new LobbyCommand.Leave(), this::returnFromLobby);
        }
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

    private void sendLobbyChat() {
        String message = lobbyChatDraft.trim();
        if (!message.isEmpty()) {
            submitLobbyCommand(new LobbyCommand.SendText(message),
                    () -> lobbyChatDraft = "");
        }
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
                            showToast(submissionError(cause));
                        }
                    }));
        } catch (RuntimeException failure) {
            lobbyCommandPending = false;
            showToast(submissionError(failure));
        }
    }

    private void returnFromLobby() {
        closeLobbySubscription();
        if (lobbySession != null) {
            lobbySession.close();
        }
        lobbySession = null;
        lobby = null;
        selectedParticipant = null;
        activeField = null;
        surface = Surface.MENU;
        sessionReturnedToMenu.run();
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

    private void drawSpeakerIcon(float x, float y, Color color) {
        shapes.setColor(color);
        shapes.rect(x - 22f, y - 10f, 15f, 20f);
        shapes.triangle(x - 7f, y - 10f, x + 8f, y - 22f, x + 8f, y + 22f);
        shapes.rectLine(x + 15f, y - 14f, x + 15f, y + 14f, 3f);
        shapes.rectLine(x + 24f, y - 21f, x + 24f, y + 21f, 3f);
    }

    private void openNewGame(NewGameConnectionDraft.Mode mode) {
        connection = defaultConnection(initialProperties, mode);
        table = new NewGameTableDraft();
        page = 0;
        activeField = null;
        historyIndex = -1;
        surface = Surface.NEW_GAME;
    }

    private void drawHeader() {
        shapes.setColor(LINE);
        shapes.rect(0f, 989f, WIDTH, 1f);
        backButton(32f, 1006f, 64f, 54f, () -> {
            if (page > 0) {
                page--;
            } else {
                surface = Surface.MENU;
            }
        });
        text(smallFont, "MENÚ PRINCIPAL  /", 122f, 1041f, MUTED, false);
        text(smallFont, connection.mode() == NewGameConnectionDraft.Mode.JOIN
                ? "UNIRME A TIMBA" : "NUEVA TIMBA", 315f, 1041f, GOLD, false);
        text(tinyFont, "PREVIEW · ESTADO CORE · SIN SESIÓN", 1725f,
                1039f, ORANGE, true);
        String title = connection.mode() == NewGameConnectionDraft.Mode.JOIN
                ? "UNIRME A TIMBA" : "NUEVA TIMBA";
        text(titleFont, title, 434f, 932f, new Color(0x000000aa), false);
        text(titleFont, title, 430f, 936f, GOLD, false);
    }

    private void drawProgress() {
        boolean joining = connection.mode() == NewGameConnectionDraft.Mode.JOIN;
        String[] names = joining
                ? new String[]{"UNIRME A TIMBA"}
                : new String[]{"NUEVA TIMBA", "CIEGAS", "COMPRA", "PARTIDA", "BOTS"};
        shapes.setColor(new Color(0x31445fbb));
        shapes.rect(389f, 130f, 1f, 860f);
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
                activeField = null;
                page = targetPage;
            });
        }
        text(tinyFont, "F11  PANTALLA COMPLETA", 195f, 190f, MUTED, true);
    }

    private void drawIdentityPage() {
        panel(430f, 185f, 670f, 625f, "");
        panel(1130f, 185f, 725f, 625f, "");

        shapes.setColor(CYAN_DARK);
        shapes.circle(520f, 660f, 70f, 64);
        shapes.setColor(PANEL_LIGHT);
        shapes.circle(520f, 660f, 61f, 64);
        drawAvatarIcon(520f, 660f);
        hit(445f, 580f, 150f, 155f,
                () -> showToast("Haz click para cambiar el avatar"));

        field(610f, 630f, 450f, "Nick:", connection.nickname(), "nick", false);
        field(610f, 475f, 450f, "Contraseña:", connection.password(), "password", true);
        if (connection.mode() != NewGameConnectionDraft.Mode.JOIN) {
            toggle(610f, 325f, 450f, "CONTINUAR TIMBA ANTERIOR:",
                    connection.recoverRequested(), this::toggleRecover, true);
        }

        field(1170f, 630f, 430f, "Servidor:", connection.server(), "server", false);
        field(1630f, 630f, 175f, "", connection.port(), "port", false);
        if (connection.mode() == NewGameConnectionDraft.Mode.JOIN) {
            String history = connection.serverHistory().isEmpty()
                    ? "Sin servidores anteriores"
                    : connection.serverHistory().get(historyIndex < 0
                            ? connection.serverHistory().size() - 1 : historyIndex);
            choice(1170f, 475f, 635f, "Servidores anteriores:", history,
                    this::nextServerHistory, !connection.serverHistory().isEmpty());
        } else {
            toggle(1170f, 475f, 635f, "UPnP", connection.upnp(),
                    () -> connection.setUpnp(!connection.upnp()), true);
            choice(1170f, 320f, 635f, "Perfil de ajustes:", "Por defecto",
                    () -> showToast("Perfil de ajustes: Por defecto"));
            button(1170f, 215f, 300f, 58f, "GUARDAR…", false,
                    () -> showToast("Guardar…"));
            button(1500f, 215f, 305f, 58f, "BORRAR", false,
                    () -> showToast("Borrar"));
        }
    }

    private void nextServerHistory() {
        List<String> history = connection.serverHistory();
        if (history.isEmpty()) {
            return;
        }
        historyIndex = (historyIndex + 1) % history.size();
        String endpoint = history.get(historyIndex);
        int separator = endpoint.lastIndexOf(':');
        if (separator > 0 && separator < endpoint.length() - 1) {
            connection.setServer(endpoint.substring(0, separator));
            connection.setPort(endpoint.substring(separator + 1));
        }
    }

    private void toggleRecover() {
        connection.setRecoverRequested(!connection.recoverRequested());
        table.setEconomyLocked(connection.recoverRequested());
    }

    private void drawBlindsPage() {
        panel(430f, 185f, 670f, 625f, "CIEGAS");
        panel(1130f, 185f, 725f, 625f, "AUMENTAR CIEGAS");

        choice(470f, 610f, 590f, "Estructura de ciegas:",
                table.structureName() == null ? "Por defecto" : table.structureName(),
                () -> showToast("Estructuras de ciegas"));
        choice(470f, 460f, 590f, "Ciegas iniciales:", formatBlindLevel(),
                this::nextBlindLevel, !table.economyLocked());
        button(470f, 300f, 590f, 70f, "ESTRUCTURAS DE CIEGAS", false,
                () -> showToast("Estructuras de ciegas"));

        toggle(1170f, 610f, 645f, "Aumentar ciegas:", table.increaseBlinds(),
                () -> table.setIncreaseBlinds(!table.increaseBlinds()), !table.economyLocked());
        choice(1170f, 460f, 305f, "",
                table.blindIncreaseType() == NewGameTableDraft.BlindIncreaseType.MINUTES
                        ? "Minutos" : "Manos",
                () -> table.setBlindIncreaseType(
                        table.blindIncreaseType() == NewGameTableDraft.BlindIncreaseType.MINUTES
                                ? NewGameTableDraft.BlindIncreaseType.HANDS
                                : NewGameTableDraft.BlindIncreaseType.MINUTES),
                table.increaseBlinds() && !table.economyLocked());
        stepper(1510f, 460f, 305f, "", table.blindInterval(), 1, Integer.MAX_VALUE,
                () -> table.setBlindInterval(table.blindInterval() - 5),
                () -> table.setBlindInterval(table.blindInterval() + 5),
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
        table.setBlindLevelIndex((table.blindLevelIndex() + 1) % table.blindLevels().size());
    }

    private void drawPurchasePage() {
        panel(430f, 185f, 670f, 625f, "COMPRA");
        panel(1130f, 185f, 725f, 625f, "RECOMPRA");

        toggle(470f, 625f, 590f, "Buy-in fijo", table.fixedBuyin(),
                () -> table.setFixedBuyin(!table.fixedBuyin()), !table.economyLocked());
        stepper(470f, 480f, 590f, "Compra inicial:", table.buyin(),
                table.minimumBuyin(), table.maximumBuyin(),
                () -> table.setBuyin(table.buyin() - 1),
                () -> table.setBuyin(table.buyin() + 1),
                table.fixedBuyin() && !table.economyLocked());
        stepper(470f, 315f, 280f, "Rango compra (CG):", table.minBuyinBb(), 10, 500,
                () -> table.setMinBuyinBb(table.minBuyinBb() - 10),
                () -> table.setMinBuyinBb(table.minBuyinBb() + 10),
                !table.economyLocked());
        stepper(780f, 315f, 280f, "→", table.maxBuyinBb(), 10, 500,
                () -> table.setMaxBuyinBb(table.maxBuyinBb() - 10),
                () -> table.setMaxBuyinBb(table.maxBuyinBb() + 10),
                !table.economyLocked());

        toggle(1170f, 625f, 645f, "Recomprar", table.rebuy(),
                () -> table.setRebuy(!table.rebuy()), true);
        toggle(1170f, 505f, 645f, "Límite recompra por jugador", table.rebuyLimit(),
                () -> table.setRebuyLimit(!table.rebuyLimit()), table.rebuyLimitEnabled());
        stepper(1170f, 365f, 645f, "", table.rebuyLimitCount(), 1, Integer.MAX_VALUE,
                () -> table.setRebuyLimitCount(table.rebuyLimitCount() - 1),
                () -> table.setRebuyLimitCount(table.rebuyLimitCount() + 1),
                table.rebuyLimitCountEnabled());
        choice(1170f, 225f, 645f, "Tope recompra:",
                table.rebuyCapPolicy() == NewGameTableDraft.RebuyCapPolicy.BUY_IN
                        ? "BUYIN" : "Stack del jugador más alto",
                () -> table.setRebuyCapPolicy(
                        table.rebuyCapPolicy() == NewGameTableDraft.RebuyCapPolicy.BUY_IN
                                ? NewGameTableDraft.RebuyCapPolicy.HIGHEST_STACK
                                : NewGameTableDraft.RebuyCapPolicy.BUY_IN), table.rebuy());
    }

    private void drawGamePage() {
        panel(430f, 185f, 670f, 625f, "PARTIDA");
        panel(1130f, 185f, 725f, 625f, "");

        toggle(470f, 625f, 280f, "Límite de manos:", table.handLimit(),
                () -> table.setHandLimit(!table.handLimit()), true);
        stepper(780f, 615f, 280f, "", table.handLimitCount(), 1, Integer.MAX_VALUE,
                () -> table.setHandLimitCount(table.handLimitCount() - 10),
                () -> table.setHandLimitCount(table.handLimitCount() + 10),
                table.handLimit());
        toggle(470f, 495f, 280f, "Tiempo de pensar:", table.thinkTime(),
                () -> table.setThinkTime(!table.thinkTime()), true);
        stepper(780f, 485f, 280f, "", table.thinkSeconds(), 10, 120,
                () -> table.setThinkSeconds(table.thinkSeconds() - 5),
                () -> table.setThinkSeconds(table.thinkSeconds() + 5), table.thinkTime());
        stepper(470f, 325f, 590f, "Tiempo de showdown:",
                table.showdownSeconds(), 5, 30,
                () -> table.setShowdownSeconds(table.showdownSeconds() - 5),
                () -> table.setShowdownSeconds(table.showdownSeconds() + 5));

        toggle(1170f, 635f, 305f, "ANTE", table.ante(),
                () -> table.setAnte(!table.ante()), !table.economyLocked());
        toggle(1510f, 635f, 305f, "STRADDLE", table.straddle(),
                () -> table.setStraddle(!table.straddle()), !table.economyLocked());
        toggle(1170f, 525f, 305f, "IWTSTH", table.iwtsth(),
                () -> table.setIwtsth(!table.iwtsth()), true);
        toggle(1510f, 525f, 305f, "RUN IT TWICE", table.runItTwice(),
                () -> table.setRunItTwice(!table.runItTwice()), true);
        choice(1170f, 385f, 645f, "RABBIT HUNTING", rabbitText(),
                this::nextRabbit);
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
        NewGameTableDraft.RabbitHunting[] values = NewGameTableDraft.RabbitHunting.values();
        table.setRabbitHunting(values[(table.rabbitHunting().ordinal() + 1) % values.length]);
    }

    private void drawBotsPage() {
        panel(520f, 185f, 1230f, 625f, "BOTS");
        choice(580f, 610f, 1110f, "Dificultad bots:",
                table.botDifficulty() == NewGameTableDraft.BotDifficulty.EASY ? "Fácil"
                        : table.botDifficulty() == NewGameTableDraft.BotDifficulty.HARD
                                ? "Difícil" : "Media",
                this::nextBotDifficulty);
        toggle(580f, 455f, 1110f, "Recomprar bots", table.botRebuy(),
                () -> table.setBotRebuy(!table.botRebuy()), table.botRebuyEnabled());
        toggle(580f, 325f, 1110f, "Repartir saldo de bots entre humanos",
                table.botBalanceToHumans(),
                () -> table.setBotBalanceToHumans(!table.botBalanceToHumans()), true);
    }

    private void nextBotDifficulty() {
        NewGameTableDraft.BotDifficulty[] values = NewGameTableDraft.BotDifficulty.values();
        table.setBotDifficulty(values[(table.botDifficulty().ordinal() + 1) % values.length]);
    }

    private void drawFooter() {
        boolean submitting = submissions != null && submissions.submitting();
        if (submitting) {
            // The immutable request is already in flight. Keep only Cancel
            // interactive so the visible form cannot drift from that request.
            hits.clear();
            activeField = null;
        }
        shapes.setColor(LINE);
        shapes.rect(0f, 129f, WIDTH, 1f);
        keyHint(430f, 55f, "ESC", "CERRAR");
        button(1165f, 31f, 250f, 70f, "CANCELAR", false,
                this::cancelOrReturnToMenu);
        button(1445f, 31f, 410f, 70f,
                submitting ? "CONECTANDO…"
                        : connection.mode() == NewGameConnectionDraft.Mode.JOIN
                                ? "UNIRME A TIMBA" : "CREAR TIMBA",
                true, this::submitNewGame, !submitting);
    }

    private void submitNewGame() {
        if (!connection.canSubmit()) {
            showToast("Faltan campos o la recuperación todavía no está lista");
            return;
        }
        if (submissions == null) {
            connection.beginSubmission();
            if (connection.mode() != NewGameConnectionDraft.Mode.JOIN) {
                table.snapshot();
            }
            connection.submissionFailed();
            showToast("Configuración validada · la preview no inicia la sesión");
            return;
        }
        try {
            submissions.submit(connection, table).whenComplete((request, failure) ->
                    Gdx.app.postRunnable(() -> completeSubmission(request, failure)));
            showToast("Conectando con la sala de espera…");
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
            activeField = null;
        } else {
            showToast(submissionError(cause));
        }
    }

    private void cancelOrReturnToMenu() {
        if (submissions != null && submissions.submitting()) {
            if (submissions.cancel()) {
                showToast("Cancelando conexión…");
            }
            return;
        }
        activeField = null;
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
            text(headingFont, title, x + 34f, y + h - 29f, GOLD, false);
            shapes.setColor(new Color(0x31445f90));
            shapes.rect(x + 30f, y + h - 71f, w - 60f, 1f);
        }
    }

    private void field(float x, float y, float w, String label, String value,
            String id, boolean secret) {
        boolean focused = id.equals(activeField);
        text(smallFont, label, x, y + 98f, MUTED, false);
        outerBox(x, y, w, 70f, focused || hovered(x, y, w, 70f) ? CYAN : LINE,
                pressed(x, y, w, 70f) ? new Color(0x0b1424ff) : PANEL_LIGHT);
        String visible = secret && !value.isEmpty() ? "•".repeat(value.length()) : value;
        textFit(uiFont, visible.isEmpty() ? "—" : visible, x + 22f, y + 44f,
                visible.isEmpty() ? DISABLED : Color.WHITE, false, w - 44f);
        hit(x, y, w, 70f, () -> activeField = id);
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
        Color track = new Color(0x253248ff).lerp(ORANGE, animation);
        shapes.setColor(track);
        roundedRect(tx, y + 19f, 66f, 38f, 19f);
        shapes.setColor(new Color(0x8290a4ff).lerp(GOLD, animation));
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
        text(smallFont, label, x, y + 99f, enabled ? MUTED : DISABLED, false);
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
        text(smallFont, label, x, y + 99f, enabled ? MUTED : DISABLED, false);
        Color border = enabled && hovered(x, y, w, 72f)
                ? CYAN : enabled ? LINE : new Color(0x253044ff);
        Color fill = enabled && pressed(x, y, w, 72f)
                ? new Color(0x0b1424ff) : enabled ? PANEL_LIGHT : new Color(0x0b111ddd);
        outerBox(x, y, w, 72f, border, fill);
        textFit(uiFont, value, x + 22f, y + 46f,
                enabled ? Color.WHITE : DISABLED, false, w - 88f);
        Color arrowColor = enabled ? GOLD : DISABLED;
        shapes.setColor(arrowColor);
        shapes.rectLine(x + w - 42f, y + 24f,
                x + w - 30f, y + 36f, 3f);
        shapes.rectLine(x + w - 30f, y + 36f,
                x + w - 42f, y + 48f, 3f);
        if (enabled) {
            hit(x, y, w, 72f, action);
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
        boolean hover = enabled && hovered(x, y, w, h);
        float hoverTarget = hover ? 1f : 0f;
        float hoverAmount = hoverAnimations.getOrDefault(label, hoverTarget);
        hoverAmount += (hoverTarget - hoverAmount)
                * Math.min(1f, frameDelta * 13f);
        hoverAnimations.put(label, hoverAmount);
        if (primary) {
            shapes.setColor(new Color(0x00000077));
            roundedRect(x + 5f, y - 6f, w, h, 14f);
        }
        if (hoverAmount > 0.01f) {
            Color glow = new Color(primary ? ORANGE : CYAN);
            glow.a = (primary ? 0.22f : 0.14f) * hoverAmount;
            shapes.setColor(glow);
            roundedRect(x - 4f * hoverAmount, y - 4f * hoverAmount,
                    w + 8f * hoverAmount, h + 8f * hoverAmount, 18f);
        }
        Color border = !enabled ? LINE : primary ? GOLD : hover ? CYAN : CYAN_DARK;
        Color fill = enabled && pressed(x, y, w, h)
                ? new Color(0x07111fd9)
                : primary ? new Color(ORANGE.r, ORANGE.g, ORANGE.b, 0.84f)
                : new Color(PANEL_LIGHT);
        outerBox(x, y, w, h, border, fill);
        if (primary) {
            shapes.setColor(new Color(0xffe07aaa));
            shapes.rect(x + 18f, y + h - 9f, w - 36f, 2f);
            shapes.setColor(new Color(0x6d4300aa));
            shapes.rect(x + 18f, y + 6f, w - 36f, 3f);
        }
        Color labelColor = !enabled ? DISABLED
                : primary && !pressed(x, y, w, h)
                        ? new Color(0x07111fff) : primary ? GOLD : CYAN;
        textFit(actionFont, label, x + w / 2f, y + h / 2f + 8f,
                labelColor, true, w - 30f);
        if (enabled) {
            hit(x, y, w, h, action);
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
        shapes.setColor(new Color(0x36d9ffcc));
        shapes.circle(cx, cy + 18f, 20f, 40);
        roundedRect(cx - 37f, cy - 39f, 74f, 38f, 19f);
        shapes.setColor(GOLD);
        shapes.circle(cx + 31f, cy + 34f, 7f, 24);
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
        hits.add(new Hit(new Rectangle(x, y, w, h), action));
    }

    private boolean hovered(float x, float y, float w, float h) {
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
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.bounds.contains(pointer)) {
                pressedHit = hit;
                return true;
            }
        }
        activeField = null;
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointerIndex, int button) {
        pointer.set(screenX, screenY);
        viewport.unproject(pointer);
        Hit released = pressedHit;
        pressedHit = null;
        if (released != null && released.bounds.contains(pointer)) {
            released.action.run();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.F11) {
            if (fullscreen) {
                Gdx.graphics.setWindowedMode(1600, 900);
            } else {
                Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
            }
            fullscreen = !fullscreen;
            return true;
        }
        if (keycode == Input.Keys.ESCAPE) {
            if (surface == Surface.NEW_GAME) {
                cancelOrReturnToMenu();
            } else if (surface == Surface.LOBBY) {
                if (lobbyConfirmation != null) {
                    lobbyConfirmation = null;
                } else {
                    lobbyConfirmation = LobbyConfirmation.LEAVE;
                }
            } else {
                Gdx.app.exit();
            }
            return true;
        }
        if ((keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER)
                && "lobbyChat".equals(activeField)) {
            sendLobbyChat();
            return true;
        }
        if (keycode == Input.Keys.BACKSPACE && activeField != null) {
            setActiveValue(removeLast(activeValue()));
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
            activeField = null;
            page = (page + 1) % 5;
            return true;
        }
        return false;
    }

    private static String removeLast(String value) {
        return value == null || value.isEmpty()
                ? "" : value.substring(0, value.offsetByCodePoints(value.length(), -1));
    }

    @Override
    public boolean keyTyped(char character) {
        if (activeField == null || Character.isISOControl(character)) {
            return false;
        }
        String value = activeValue();
        int limit = switch (activeField) {
            case "nick" -> 15;
            case "password" -> 30;
            case "port" -> 5;
            case "lobbyChat" -> 16 * 1024 * 1024;
            default -> 128;
        };
        if (value.length() >= limit || ("port".equals(activeField)
                && !Character.isDigit(character))) {
            return true;
        }
        if ("nick".equals(activeField) && character == '$') {
            return true;
        }
        setActiveValue(value + character);
        return true;
    }

    private String activeValue() {
        return switch (activeField) {
            case "nick" -> connection.nickname();
            case "password" -> connection.password();
            case "server" -> connection.server();
            case "port" -> connection.port();
            case "lobbyChat" -> lobbyChatDraft;
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
            default -> {
            }
        }
    }

    @Override
    public void dispose() {
        disposed = true;
        if (submissions != null) {
            submissions.cancel();
        }
        closeLobbySubscription();
        if (lobbySession != null) {
            lobbySession.close();
            lobbySession = null;
        }
        batch.dispose();
        shapes.dispose();
        feltTexture.dispose();
        titleFont.dispose();
        headingFont.dispose();
        actionFont.dispose();
        uiFont.dispose();
        smallFont.dispose();
        tinyFont.dispose();
    }

    @Override public boolean keyUp(int keycode) { return false; }
    @Override public boolean touchCancelled(int x, int y, int p, int b) { return false; }
    @Override public boolean touchDragged(int x, int y, int p) { return false; }
    @Override public boolean mouseMoved(int x, int y) {
        pointer.set(x, y);
        viewport.unproject(pointer);
        return false;
    }
    @Override public boolean scrolled(float amountX, float amountY) { return false; }

    private record TextItem(BitmapFont font, String text, float x, float y,
            Color color, boolean centered) {
    }

    private enum Surface {
        MENU, NEW_GAME, LOBBY
    }

    private enum LobbyConfirmation {
        START, LEAVE
    }

    private record Hit(Rectangle bounds, Runnable action) {
    }
}
