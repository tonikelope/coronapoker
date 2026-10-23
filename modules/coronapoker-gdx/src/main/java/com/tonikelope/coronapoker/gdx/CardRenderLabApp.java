/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

/** Interactive, pixel-accurate card renderer using CoronaPoker's real GPU path. */
final class CardRenderLabApp extends ApplicationAdapter {

    private static final float PANEL_WIDTH = 500f;
    private static final float PANEL_PADDING = 24f;
    private static final float ROW_HEIGHT = 76f;
    private static final float ARROW_SIZE = 44f;
    private static final float TOOLTIP_WIDTH = 440f;
    private static final float TOOLTIP_HEIGHT = 86f;
    private static final float CARD_CANVAS_SCALE
            = CoronaPokerGdxTable.cardShaderCanvasScale();
    private static final Color BACKGROUND = new Color(0x111923ff);
    private static final Color PREVIEW_BACKGROUND = new Color(0x2d333cff);
    private static final Color PANEL = new Color(0x162333ff);
    private static final Color ROW = new Color(0x203248ff);
    private static final Color ROW_SELECTED = new Color(0x31577aff);
    private static final Color ROW_HOVERED = new Color(0x29435dff);
    private static final Color SEPARATOR = new Color(0x0d1622ff);
    private static final Color ARROW_BACKGROUND = new Color(0x0e1d2cff);
    private static final Color TOOLTIP_BACKGROUND = new Color(0x0b1725f5);
    private static final Color ACCENT = new Color(0x5ed5ffff);
    private static final Color TEXT = new Color(0xf1f7ffff);
    private static final Color MUTED = new Color(0x9fb4c8ff);

    private final CardRenderLabModel model = new CardRenderLabModel();
    private final GdxCardSmaaRenderer smaa = new GdxCardSmaaRenderer();
    private final GlyphLayout glyph = new GlyphLayout();
    private final Vector2 pointer = new Vector2();
    private OrthographicCamera camera;
    private ScreenViewport viewport;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont titleFont;
    private BitmapFont valueFont;
    private BitmapFont labelFont;
    private ShaderProgram cardShader;
    private Texture face;
    private Texture back;
    private boolean smaaEffective;

    @Override
    public void create() {
        camera = new OrthographicCamera();
        viewport = new ScreenViewport(camera);
        batch = new SpriteBatch(256);
        shapes = new ShapeRenderer();
        titleFont = font(28);
        valueFont = font(21);
        labelFont = font(15);
        rebuildShader();
        reloadTextures();
        Gdx.input.setInputProcessor(new Controls());
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    @Override
    public void render() {
        GdxPixelText.beginFrame();
        viewport.apply();
        ScreenUtils.clear(BACKGROUND, true);
        drawBackground();

        CardRenderLabModel.RenderMode mode = model.mode();
        int renderingMode = mode.smaa()
                ? GdxCardSmaaRenderer.RENDERING_SMOOTH
                : GdxCardSmaaRenderer.RENDERING_NATIVE;
        smaaEffective = smaa.begin(Gdx.graphics.getBackBufferWidth(),
                Gdx.graphics.getBackBufferHeight(), renderingMode, batch);
        drawCard(mode.smoothArtwork());
        if (smaaEffective) {
            smaa.endAndComposite(batch, camera.combined, () -> {
                Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, 0);
                Gdx.gl.glViewport(0, 0,
                        Gdx.graphics.getBackBufferWidth(),
                        Gdx.graphics.getBackBufferHeight());
            });
        }
        drawInterface();
    }

    private void drawBackground() {
        float width = viewport.getWorldWidth();
        float height = viewport.getWorldHeight();
        float panelWidth = panelWidth(width);
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(PREVIEW_BACKGROUND);
        shapes.rect(panelWidth, 0f, width - panelWidth, height);
        shapes.setColor(PANEL);
        shapes.rect(0f, 0f, panelWidth, height);
        shapes.setColor(SEPARATOR);
        shapes.rect(panelWidth - 2f, 0f, 2f, height);
        shapes.end();
    }

    private void drawCard(boolean smoothArtwork) {
        float width = viewport.getWorldWidth();
        float height = viewport.getWorldHeight();
        float panelWidth = panelWidth(width);
        float centerX = panelWidth + (width - panelWidth) / 2f;
        float centerY = height * 0.50f;
        float cardWidth = model.cardWidth();
        float cardHeight = cardWidth * back.getHeight() / back.getWidth();
        float canvasWidth = cardWidth * CARD_CANVAS_SCALE;
        float canvasHeight = cardHeight * CARD_CANVAS_SCALE;

        batch.setProjectionMatrix(camera.combined);
        batch.setShader(cardShader);
        batch.begin();
        face.bind(1);
        back.bind(0);
        cardShader.setUniformi("u_texture", 0);
        cardShader.setUniformi("u_frontTexture", 1);
        cardShader.setUniformf("u_cornerRadius",
                CoronaPokerGdxTable.cardCornerRadius());
        cardShader.setUniformf("u_edgeSoftness",
                CoronaPokerGdxTable.cardEdgeSoftness());
        cardShader.setUniformf("u_perspective", 0f);
        cardShader.setUniformf("u_flipAngle", MathUtils.PI);
        cardShader.setUniformf("u_cardAspect", cardHeight / cardWidth);
        cardShader.setUniformf("u_canvasScale", CARD_CANVAS_SCALE);
        cardShader.setUniformf("u_artworkSmooth", smoothArtwork ? 1f : 0f);
        cardShader.setUniformf("u_artworkLodBias", model.lodBias());
        cardShader.setUniformf("u_overlay", 1f, 0.925f, 0f, 0f);
        batch.setColor(Color.WHITE);
        batch.draw(back, centerX - canvasWidth / 2f,
                centerY - canvasHeight / 2f,
                canvasWidth / 2f, canvasHeight / 2f,
                canvasWidth, canvasHeight, 1f, 1f,
                model.angleDegrees(), 0, 0,
                back.getWidth(), back.getHeight(), false, false);
        batch.end();
        batch.setShader(null);
    }

    private void drawInterface() {
        float width = viewport.getWorldWidth();
        float height = viewport.getWorldHeight();
        float panelWidth = panelWidth(width);
        float rowsTop = rowsTop(height);
        CardRenderLabModel.Setting hovered = hoveredSetting(panelWidth,
                rowsTop);
        Rectangle tooltip = hovered == null ? null
                : tooltipBounds(hovered, width, height, panelWidth, rowsTop);

        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (CardRenderLabModel.Setting setting
                : CardRenderLabModel.Setting.values()) {
            Rectangle row = rowBounds(setting, panelWidth, rowsTop);
            shapes.setColor(setting == model.selectedSetting()
                    ? ROW_SELECTED : setting == hovered ? ROW_HOVERED : ROW);
            shapes.rect(row.x, row.y, row.width, row.height);
            drawArrow(leftArrow(row), false);
            drawArrow(rightArrow(row), true);
        }
        if (tooltip != null) {
            shapes.setColor(ACCENT);
            shapes.rect(tooltip.x - 1f, tooltip.y - 1f,
                    tooltip.width + 2f, tooltip.height + 2f);
            shapes.setColor(TOOLTIP_BACKGROUND);
            shapes.rect(tooltip.x, tooltip.y, tooltip.width, tooltip.height);
        }
        shapes.end();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        titleFont.setColor(TEXT);
        GdxPixelText.draw(titleFont, batch, "CARD RENDER LAB", PANEL_PADDING,
                height - 28f, viewport);
        labelFont.setColor(MUTED);
        GdxPixelText.draw(labelFont, batch,
                "Mismo shader, texturas y SMAA que CoronaPoker",
                PANEL_PADDING, height - 61f, viewport);

        for (CardRenderLabModel.Setting setting
                : CardRenderLabModel.Setting.values()) {
            Rectangle row = rowBounds(setting, panelWidth, rowsTop);
            labelFont.setColor(setting == model.selectedSetting()
                    ? ACCENT : MUTED);
            GdxPixelText.draw(labelFont, batch, setting.label(), row.x + 60f,
                    row.y + row.height - 10f, viewport);
            valueFont.setColor(TEXT);
            drawCentered(valueFont, model.value(setting),
                    row.x + 58f, row.y + 4f,
                    row.width - 116f, row.height - 24f);
        }
        if (tooltip != null) {
            labelFont.setColor(TEXT);
            GdxPixelText.draw(labelFont, batch, hovered.tooltip(),
                    tooltip.x + 15f, tooltip.y + tooltip.height - 15f,
                    tooltip.width - 30f, Align.left, true, viewport);
        }

        float previewCenter = panelWidth + (width - panelWidth) / 2f;
        titleFont.setColor(TEXT);
        drawCentered(titleFont, model.deck() + " / " + model.card(),
                panelWidth + 20f, height - 68f,
                width - panelWidth - 40f, 38f);
        labelFont.setColor(MUTED);
        String status = "Tamaño físico: " + model.cardWidth()
                + " px   |   " + model.angleDegrees() + " grados   |   SMAA: "
                + (model.mode().smaa()
                        ? (smaaEffective ? "ACTIVO" : "NO DISPONIBLE")
                        : "DESACTIVADO");
        drawCentered(labelFont, status, panelWidth + 20f,
                height - 101f, width - panelWidth - 40f, 26f);
        labelFont.setColor(MUTED);
        drawCentered(labelFont,
                "Arriba/abajo: parametro    Izquierda/derecha: valor    R: restablecer    ESC: salir",
                panelWidth + 12f, 14f, width - panelWidth - 24f, 34f);
        labelFont.setColor(ACCENT);
        drawCentered(labelFont, "1 px del visor = 1 px del framebuffer",
                previewCenter - 230f, 52f, 460f, 25f);
        batch.end();
    }

    private void drawArrow(Rectangle bounds, boolean right) {
        shapes.setColor(ARROW_BACKGROUND);
        shapes.rect(bounds.x, bounds.y, bounds.width, bounds.height);
        shapes.setColor(ACCENT);
        float cx = bounds.x + bounds.width / 2f;
        float cy = bounds.y + bounds.height / 2f;
        float dx = right ? 8f : -8f;
        shapes.triangle(cx + dx, cy,
                cx - dx, cy + 11f,
                cx - dx, cy - 11f);
    }

    private void drawCentered(BitmapFont font, String text,
            float x, float y, float width, float height) {
        glyph.setText(font, text);
        GdxPixelText.draw(font, batch, glyph,
                x + (width - glyph.width) / 2f,
                y + (height + glyph.height) / 2f, viewport);
    }

    private void change(CardRenderLabModel.Setting setting, int delta) {
        String oldFace = model.cardResource();
        String oldBack = model.backResource();
        TextureFilter oldFilter = model.filter().minification();
        model.change(setting, delta);
        if (!oldFace.equals(model.cardResource())
                || !oldBack.equals(model.backResource())) {
            reloadTextures();
        } else if (oldFilter != model.filter().minification()) {
            applyTextureFilter();
        }
    }

    private void reset() {
        model.reset();
        reloadTextures();
    }

    private void reloadTextures() {
        disposeTexture(face);
        disposeTexture(back);
        face = cardTexture(model.cardResource());
        back = cardTexture(model.backResource());
        applyTextureFilter();
    }

    private static Texture cardTexture(String resource) {
        if (!Gdx.files.internal(resource).exists()) {
            throw new IllegalStateException("Missing card resource " + resource);
        }
        Texture texture = new Texture(Gdx.files.internal(resource), true);
        texture.setWrap(TextureWrap.ClampToEdge, TextureWrap.ClampToEdge);
        texture.setAnisotropicFilter(1f);
        return texture;
    }

    private void applyTextureFilter() {
        TextureFilter filter = model.filter().minification();
        face.setFilter(filter, TextureFilter.Linear);
        back.setFilter(filter, TextureFilter.Linear);
    }

    private void rebuildShader() {
        if (cardShader != null) cardShader.dispose();
        cardShader = new ShaderProgram(CoronaPokerGdxTable.CARD_VERTEX_SHADER,
                CoronaPokerGdxTable.CARD_FRAGMENT_SHADER);
        if (!cardShader.isCompiled()) {
            String log = cardShader.getLog();
            cardShader.dispose();
            cardShader = null;
            throw new IllegalStateException("Card Render Lab shader: " + log);
        }
    }

    private BitmapFont font(int size) {
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(
                Gdx.files.internal("fonts/Inter-Medium.ttf"));
        try {
            FreeTypeFontGenerator.FreeTypeFontParameter parameter
                    = new FreeTypeFontGenerator.FreeTypeFontParameter();
            parameter.size = size;
            parameter.characters = FreeTypeFontGenerator.DEFAULT_CHARS
                    + "áéíóúÁÉÍÓÚñÑüÜº";
            BitmapFont created = generator.generateFont(parameter);
            created.getRegion().getTexture().setFilter(
                    TextureFilter.Linear, TextureFilter.Linear);
            return created;
        } finally {
            generator.dispose();
        }
    }

    private static float panelWidth(float worldWidth) {
        return Math.min(PANEL_WIDTH, worldWidth * 0.42f);
    }

    private static float rowsTop(float worldHeight) {
        return worldHeight - 92f;
    }

    private static Rectangle rowBounds(CardRenderLabModel.Setting setting,
            float panelWidth, float rowsTop) {
        float x = PANEL_PADDING;
        float y = rowsTop - (setting.ordinal() + 1) * ROW_HEIGHT;
        return new Rectangle(x, y, panelWidth - PANEL_PADDING * 2f,
                ROW_HEIGHT - 8f);
    }

    private static Rectangle leftArrow(Rectangle row) {
        return new Rectangle(row.x + 7f,
                row.y + (row.height - ARROW_SIZE) / 2f,
                ARROW_SIZE, ARROW_SIZE);
    }

    private static Rectangle rightArrow(Rectangle row) {
        return new Rectangle(row.x + row.width - ARROW_SIZE - 7f,
                row.y + (row.height - ARROW_SIZE) / 2f,
                ARROW_SIZE, ARROW_SIZE);
    }

    private CardRenderLabModel.Setting hoveredSetting(float panelWidth,
            float rowsTop) {
        pointer.set(Gdx.input.getX(), Gdx.input.getY());
        viewport.unproject(pointer);
        for (CardRenderLabModel.Setting setting
                : CardRenderLabModel.Setting.values()) {
            if (rowBounds(setting, panelWidth, rowsTop).contains(pointer)) {
                return setting;
            }
        }
        return null;
    }

    private static Rectangle tooltipBounds(CardRenderLabModel.Setting setting,
            float worldWidth, float worldHeight, float panelWidth,
            float rowsTop) {
        Rectangle row = rowBounds(setting, panelWidth, rowsTop);
        float x = panelWidth + 16f;
        float width = Math.min(TOOLTIP_WIDTH, worldWidth - x - 16f);
        float y = MathUtils.clamp(row.y + (row.height - TOOLTIP_HEIGHT) / 2f,
                16f, worldHeight - TOOLTIP_HEIGHT - 16f);
        return new Rectangle(x, y, Math.max(260f, width), TOOLTIP_HEIGHT);
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        camera.update();
    }

    @Override
    public void dispose() {
        Gdx.input.setInputProcessor(null);
        smaa.dispose();
        disposeTexture(face);
        disposeTexture(back);
        if (cardShader != null) cardShader.dispose();
        if (titleFont != null) titleFont.dispose();
        if (valueFont != null) valueFont.dispose();
        if (labelFont != null) labelFont.dispose();
        if (batch != null) batch.dispose();
        if (shapes != null) shapes.dispose();
    }

    private static void disposeTexture(Texture texture) {
        if (texture != null) texture.dispose();
    }

    private final class Controls extends InputAdapter {

        @Override
        public boolean keyDown(int keycode) {
            switch (keycode) {
                case Input.Keys.UP -> model.select(-1);
                case Input.Keys.DOWN, Input.Keys.TAB -> model.select(1);
                case Input.Keys.LEFT -> change(model.selectedSetting(), -1);
                case Input.Keys.RIGHT -> change(model.selectedSetting(), 1);
                case Input.Keys.R -> reset();
                case Input.Keys.ESCAPE -> Gdx.app.exit();
                default -> { return false; }
            }
            return true;
        }

        @Override
        public boolean touchDown(int screenX, int screenY,
                int pointerIndex, int button) {
            pointer.set(screenX, screenY);
            viewport.unproject(pointer);
            float panelWidth = panelWidth(viewport.getWorldWidth());
            float rowsTop = rowsTop(viewport.getWorldHeight());
            for (CardRenderLabModel.Setting setting
                    : CardRenderLabModel.Setting.values()) {
                Rectangle row = rowBounds(setting, panelWidth, rowsTop);
                if (!row.contains(pointer)) continue;
                model.select(setting);
                if (leftArrow(row).contains(pointer)) change(setting, -1);
                if (rightArrow(row).contains(pointer)) change(setting, 1);
                return true;
            }
            return false;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            if (amountY == 0f) return false;
            change(model.selectedSetting(), amountY > 0f ? 1 : -1);
            return true;
        }
    }
}
