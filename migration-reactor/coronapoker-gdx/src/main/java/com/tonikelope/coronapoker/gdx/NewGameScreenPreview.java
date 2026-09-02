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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Interactive visual-only preview of the real NewGameDialog inventory.
 *
 * This class cannot create a game and is not reachable from GdxLauncher. It is
 * intentionally isolated until the form is backed by the neutral core model.
 */
final class NewGameScreenPreview extends ApplicationAdapter implements InputProcessor {

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
    private String nick = "Jugador";
    private String password = "";
    private String server = "localhost";
    private String port = "7234";
    private boolean fullscreen;
    private boolean recover;
    private boolean upnp;
    private boolean fixedBuyin = true;
    private boolean increaseBlinds;
    private boolean blindCap;
    private boolean rebuy = true;
    private boolean rebuyLimit;
    private boolean botRebuy = true;
    private boolean botBalance;
    private boolean handLimit;
    private boolean thinkTime = true;
    private boolean ante;
    private boolean straddle;
    private boolean iwtsth;
    private boolean runItTwice;
    private int buyin = 10;
    private int minBb = 10;
    private int maxBb = 100;
    private int blindInterval = 60;
    private int blindCapRaises = 5;
    private int rebuyCount = 3;
    private int hands = 100;
    private int thinkSeconds = 40;
    private int showdownSeconds = 10;
    private int rabbit;
    private int difficulty = 1;
    private int blindIntervalType = 1;
    private int rebuyCapPolicy;
    private long toastUntil;
    private String toast = "";
    private float elapsed;
    private float frameDelta;
    private Hit pressedHit;

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

    private void drawFeltBackground() {
        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(feltTexture, 0f, 0f, WIDTH, HEIGHT,
                0f, 0f, WIDTH / feltTexture.getWidth(),
                HEIGHT / feltTexture.getHeight());
        batch.setColor(Color.WHITE);
        batch.end();
    }

    private void drawHeader() {
        shapes.setColor(LINE);
        shapes.rect(0f, 989f, WIDTH, 1f);
        backButton(32f, 1006f, 64f, 54f, () -> {
            if (page > 0) {
                page--;
            } else {
                showToast("PREVIEW AISLADA: no hay un menú anterior conectado");
            }
        });
        text(smallFont, "MENÚ PRINCIPAL  /", 122f, 1041f, MUTED, false);
        text(smallFont, "NUEVA TIMBA", 315f, 1041f, GOLD, false);
        text(tinyFont, "PREVIEW · NO CONECTADA AL CORE", 1725f,
                1039f, ORANGE, true);
        text(titleFont, "NUEVA TIMBA", 434f, 932f, new Color(0x000000aa), false);
        text(titleFont, "NUEVA TIMBA", 430f, 936f, GOLD, false);
    }

    private void drawProgress() {
        String[] names = {"NUEVA TIMBA", "CIEGAS", "COMPRA", "PARTIDA", "BOTS"};
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
            drawNavIcon(i, x + 39f, y + 39f,
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

        field(610f, 630f, 450f, "Nick:", nick, "nick", false);
        field(610f, 475f, 450f, "Contraseña:", password, "password", true);
        toggle(610f, 325f, 450f, "CONTINUAR TIMBA ANTERIOR:", recover,
                () -> recover = !recover, true);

        field(1170f, 630f, 430f, "Servidor:", server, "server", false);
        field(1630f, 630f, 175f, "", port, "port", false);
        toggle(1170f, 475f, 635f, "UPnP", upnp,
                () -> upnp = !upnp, true);
        choice(1170f, 320f, 635f, "Perfil de ajustes:", "Por defecto",
                () -> showToast("Perfil de ajustes: Por defecto"));
        button(1170f, 215f, 300f, 58f, "GUARDAR…", false,
                () -> showToast("Guardar…"));
        button(1500f, 215f, 305f, 58f, "BORRAR", false,
                () -> showToast("Borrar"));
    }

    private void drawBlindsPage() {
        panel(430f, 185f, 670f, 625f, "CIEGAS");
        panel(1130f, 185f, 725f, 625f, "AUMENTAR CIEGAS");

        choice(470f, 610f, 590f, "Estructura de ciegas:", "Por defecto",
                () -> showToast("Estructuras de ciegas"));
        choice(470f, 460f, 590f, "Ciegas iniciales:", "0.10 / 0.20",
                () -> showToast("Ciegas iniciales: 0.10 / 0.20"));
        button(470f, 300f, 590f, 70f, "ESTRUCTURAS DE CIEGAS", false,
                () -> showToast("Estructuras de ciegas"));

        toggle(1170f, 610f, 645f, "Aumentar ciegas:", increaseBlinds,
                () -> increaseBlinds = !increaseBlinds, true);
        choice(1170f, 460f, 305f, "",
                blindIntervalType == 1 ? "Minutos" : "Manos",
                () -> blindIntervalType = blindIntervalType == 1 ? 2 : 1);
        stepper(1510f, 460f, 305f, "", blindInterval, 1, 999,
                () -> blindInterval = Math.max(1, blindInterval - 5),
                () -> blindInterval += 5);
        toggle(1170f, 325f, 645f, "Tope de aumentos", blindCap,
                () -> blindCap = !blindCap, increaseBlinds);
        stepper(1170f, 205f, 645f, "", blindCapRaises,
                1, 99, () -> blindCapRaises = Math.max(1, blindCapRaises - 1),
                () -> blindCapRaises++, increaseBlinds && blindCap);
    }

    private void drawPurchasePage() {
        panel(430f, 185f, 670f, 625f, "COMPRA");
        panel(1130f, 185f, 725f, 625f, "RECOMPRA");

        toggle(470f, 625f, 590f, "Buy-in fijo", fixedBuyin,
                () -> fixedBuyin = !fixedBuyin, true);
        stepper(470f, 480f, 590f, "Compra inicial:", buyin, 1, 100,
                () -> buyin = Math.max(1, buyin - 1), () -> buyin++);
        stepper(470f, 315f, 280f, "Rango compra (CG):", minBb, 10, 500,
                () -> minBb = Math.max(10, minBb - 10),
                () -> minBb = Math.min(maxBb, minBb + 10));
        stepper(780f, 315f, 280f, "→", maxBb, 10, 500,
                () -> maxBb = Math.max(minBb, maxBb - 10),
                () -> maxBb = Math.min(500, maxBb + 10));

        toggle(1170f, 625f, 645f, "Recomprar", rebuy,
                () -> rebuy = !rebuy, true);
        toggle(1170f, 505f, 645f, "Límite recompra por jugador", rebuyLimit,
                () -> rebuyLimit = !rebuyLimit, rebuy);
        stepper(1170f, 365f, 645f, "", rebuyCount, 1, 999,
                () -> rebuyCount = Math.max(1, rebuyCount - 1),
                () -> rebuyCount++, rebuy && rebuyLimit);
        choice(1170f, 225f, 645f, "Tope recompra:",
                rebuyCapPolicy == 0 ? "BUYIN" : "Stack del jugador más alto",
                () -> rebuyCapPolicy = 1 - rebuyCapPolicy, rebuy);
    }

    private void drawGamePage() {
        panel(430f, 185f, 670f, 625f, "PARTIDA");
        panel(1130f, 185f, 725f, 625f, "");

        toggle(470f, 625f, 280f, "Límite de manos:", handLimit,
                () -> handLimit = !handLimit, true);
        stepper(780f, 615f, 280f, "", hands, 1, 99999,
                () -> hands = Math.max(1, hands - 10), () -> hands += 10,
                handLimit);
        toggle(470f, 495f, 280f, "Tiempo de pensar:", thinkTime,
                () -> thinkTime = !thinkTime, true);
        stepper(780f, 485f, 280f, "", thinkSeconds, 10, 120,
                () -> thinkSeconds = Math.max(10, thinkSeconds - 5),
                () -> thinkSeconds = Math.min(120, thinkSeconds + 5), thinkTime);
        stepper(470f, 325f, 590f, "Tiempo de showdown:",
                showdownSeconds, 5, 30,
                () -> showdownSeconds = Math.max(5, showdownSeconds - 1),
                () -> showdownSeconds = Math.min(30, showdownSeconds + 1));

        toggle(1170f, 635f, 305f, "ANTE", ante, () -> ante = !ante, true);
        toggle(1510f, 635f, 305f, "STRADDLE", straddle,
                () -> straddle = !straddle, true);
        toggle(1170f, 525f, 305f, "IWTSTH", iwtsth,
                () -> iwtsth = !iwtsth, true);
        toggle(1510f, 525f, 305f, "RUN IT TWICE", runItTwice,
                () -> runItTwice = !runItTwice, true);
        choice(1170f, 385f, 645f, "RABBIT HUNTING", rabbitText(),
                () -> rabbit = (rabbit + 1) % 4);
    }

    private String rabbitText() {
        return switch (rabbit) {
            case 1 -> "Gratis";
            case 2 -> "Gratis + SB";
            case 3 -> "Gratis + SB + BB";
            default -> "Desactivado";
        };
    }

    private void drawBotsPage() {
        panel(520f, 185f, 1230f, 625f, "BOTS");
        choice(580f, 610f, 1110f, "Dificultad bots:",
                difficulty == 0 ? "Fácil" : difficulty == 2 ? "Difícil" : "Media",
                () -> difficulty = (difficulty + 1) % 3);
        toggle(580f, 455f, 1110f, "Recomprar bots", botRebuy,
                () -> botRebuy = !botRebuy, rebuy);
        toggle(580f, 325f, 1110f, "Repartir saldo de bots entre humanos",
                botBalance, () -> botBalance = !botBalance, true);
    }

    private void drawFooter() {
        shapes.setColor(LINE);
        shapes.rect(0f, 129f, WIDTH, 1f);
        keyHint(430f, 55f, "ESC", "CERRAR");
        button(1165f, 31f, 250f, 70f, "CANCELAR", false,
                () -> showToast("PREVIEW: todavía no está conectada al core"));
        button(1445f, 31f, 410f, 70f, "CREAR TIMBA", true,
                () -> showToast("PREVIEW: todavía no está conectada al core"));
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
        boolean hover = hovered(x, y, w, h);
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
        Color border = primary ? GOLD : hover ? CYAN : CYAN_DARK;
        Color fill = pressed(x, y, w, h)
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
        Color labelColor = primary && !pressed(x, y, w, h)
                ? new Color(0x07111fff) : primary ? GOLD : CYAN;
        textFit(actionFont, label, x + w / 2f, y + h / 2f + 8f,
                labelColor, true, w - 30f);
        hit(x, y, w, h, action);
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
            Gdx.app.exit();
            return true;
        }
        if (keycode == Input.Keys.BACKSPACE && activeField != null) {
            setActiveValue(removeLast(activeValue()));
            return true;
        }
        if (activeField == null && keycode == Input.Keys.LEFT && page > 0) {
            page--;
            return true;
        }
        if (activeField == null && keycode == Input.Keys.RIGHT && page < 4) {
            page++;
            return true;
        }
        if (keycode == Input.Keys.TAB) {
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
            case "nick" -> nick;
            case "password" -> password;
            case "server" -> server;
            case "port" -> port;
            default -> "";
        };
    }

    private void setActiveValue(String value) {
        switch (activeField) {
            case "nick" -> nick = value;
            case "password" -> password = value;
            case "server" -> server = value;
            case "port" -> port = value;
            default -> {
            }
        }
    }

    @Override
    public void dispose() {
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

    private record Hit(Rectangle bounds, Runnable action) {
    }
}
