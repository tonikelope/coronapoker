/*
 * Copyright (C) 2020 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tonikelope.coronapoker;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Paint;
import java.text.DecimalFormat;
import java.util.Map;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.ItemLabelAnchor;
import org.jfree.chart.labels.ItemLabelPosition;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.SpiderWebPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.xy.XYDataset;

/**
 * Builds the themed JFreeChart panels shown beneath the statistics tables. Every chart
 * shares a light, flat look with the CoronaPoker orange accent and green/red money
 * semantics, and reuses {@link Helpers#GUI_FONT} so the charts blend with the dialog.
 *
 * @author tonikelope
 */
public final class StatsCharts {

    private static final Color ACCENT = new Color(255, 102, 0);
    private static final Color POSITIVE = new Color(46, 160, 67);
    private static final Color NEGATIVE = new Color(200, 55, 55);
    private static final Color GRID = new Color(225, 225, 225);
    private static final Color TITLE = new Color(55, 55, 55);
    private static final Color AXIS = new Color(110, 110, 110);
    private static final Color PANEL_BG = Color.WHITE;

    private StatsCharts() {
    }

    // ===== TAMAÑO DE LAS FUENTES DE LAS GRÁFICAS =====
    // Escala que multiplica el tamaño de TODOS los textos de las gráficas a la vez
    // (títulos, etiquetas de ejes, números sobre las barras, nombres de jugador,
    // ejes del radar...). 1.0 = tamaños originales. Se controla en vivo desde el
    // spinner del StatsDialog (setFontScale + refresco de la gráfica).
    private static volatile float FONT_SCALE = 1.3f;

    public static float getFontScale() {
        return FONT_SCALE;
    }

    public static void setFontScale(float scale) {
        FONT_SCALE = scale;
    }

    private static Font font(int style, float size) {
        float scaled = size * FONT_SCALE;
        Font base = Helpers.GUI_FONT;
        if (base == null) {
            base = new Font("Dialog", style, Math.round(scaled));
        }
        return base.deriveFont(style, scaled);
    }

    // Un valor no finito (NaN/Infinity) en el dataset hace que el eje del grafico
    // lance "IllegalArgumentException: Must be finite" en CADA repaint del EDT,
    // dejando el StatsDialog inservible. Caso real: un % nulo de la BD (jugador
    // que nunca llego a esa calle, timbas viejas) llega como Float.NEGATIVE_INFINITY
    // desde safeParsePercent. Se filtran/anulan antes de pintar.
    private static boolean isFinite(Double v) {
        return v != null && Double.isFinite(v);
    }

    /**
     * Wraps a chart in a ChartPanel with dialog-friendly defaults (white background, wheel
     * zoom, and a draw range wide enough not to rescale fonts on large monitors).
     */
    private static ChartPanel wrap(JFreeChart chart) {
        ChartPanel panel = new ChartPanel(chart);
        panel.setMouseWheelEnabled(true);
        panel.setBackground(PANEL_BG);
        panel.setOpaque(true);
        panel.setMinimumDrawWidth(0);
        panel.setMinimumDrawHeight(0);
        panel.setMaximumDrawWidth(4096);
        panel.setMaximumDrawHeight(2160);
        return panel;
    }

    private static void styleChrome(JFreeChart chart) {
        chart.setBackgroundPaint(PANEL_BG);
        chart.setBorderVisible(false);
        TextTitle t = chart.getTitle();
        if (t != null) {
            t.setFont(font(Font.BOLD, 17f));
            t.setPaint(TITLE);
        }
        if (chart.getLegend() != null) {
            chart.getLegend().setItemFont(font(Font.PLAIN, 12f));
            chart.getLegend().setBackgroundPaint(PANEL_BG);
        }
    }

    /**
     * Horizontal bar chart of a single value per player, each bar green when positive and
     * red when negative — the "scoreboard" view (profit/loss). JFreeChart draws the first
     * category on top, so pass the data highest-first to keep the leader on top.
     */
    public static ChartPanel benefitBars(Map<String, Double> data, String title, String valueAxisLabel) {
        final DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (Map.Entry<String, Double> e : data.entrySet()) {
            if (isFinite(e.getValue())) {
                dataset.addValue(e.getValue(), "v", e.getKey());
            }
        }

        JFreeChart chart = ChartFactory.createBarChart(title, null, valueAxisLabel, dataset, PlotOrientation.HORIZONTAL, false, true, false);
        styleChrome(chart);

        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(PANEL_BG);
        plot.setOutlineVisible(false);
        plot.setRangeGridlinePaint(GRID);
        plot.setRangeZeroBaselineVisible(true);
        plot.setRangeZeroBaselinePaint(AXIS);
        plot.setAxisOffset(new RectangleInsets(2, 2, 2, 2));

        CategoryAxis domain = plot.getDomainAxis();
        domain.setTickLabelFont(font(Font.BOLD, 13f));
        domain.setTickLabelPaint(TITLE);
        domain.setAxisLinePaint(AXIS);

        NumberAxis range = (NumberAxis) plot.getRangeAxis();
        range.setTickLabelFont(font(Font.PLAIN, 12f));
        range.setLabelFont(font(Font.PLAIN, 13f));
        range.setTickLabelPaint(AXIS);
        range.setLabelPaint(AXIS);
        range.setAxisLinePaint(AXIS);

        BarRenderer renderer = new BarRenderer() {
            @Override
            public Paint getItemPaint(int row, int column) {
                Number v = dataset.getValue(0, column);
                return v != null && v.doubleValue() < 0 ? NEGATIVE : POSITIVE;
            }
        };
        renderer.setBarPainter(new StandardBarPainter());
        renderer.setShadowVisible(false);
        renderer.setDrawBarOutline(false);
        renderer.setMaximumBarWidth(0.14);
        renderer.setDefaultItemLabelGenerator(new StandardCategoryItemLabelGenerator("{2}", new DecimalFormat("#,##0.#")));
        renderer.setDefaultItemLabelFont(font(Font.BOLD, 12f));
        // Etiquetas BLANCAS DENTRO de la barra (sobre el color): el gris oscuro
        // (TITLE) no contrastaba sobre barras de color. INSIDE3/INSIDE9 fija el
        // numero pegado al extremo de la barra para que el blanco caiga siempre
        // sobre la barra y no sobre el fondo claro del panel.
        renderer.setDefaultItemLabelPaint(Color.WHITE);
        renderer.setDefaultPositiveItemLabelPosition(new ItemLabelPosition(ItemLabelAnchor.INSIDE3, TextAnchor.CENTER_RIGHT));
        renderer.setDefaultNegativeItemLabelPosition(new ItemLabelPosition(ItemLabelAnchor.INSIDE9, TextAnchor.CENTER_LEFT));
        renderer.setDefaultItemLabelsVisible(true);
        plot.setRenderer(renderer);

        return wrap(chart);
    }

    public static final Color ORANGE = ACCENT;
    public static final Color BLUE = new Color(0, 120, 170);
    public static final Color PURPLE = new Color(120, 90, 200);

    /**
     * Vertical bar chart of integer counts per category, in the iteration order given. Used
     * for frequency distributions (e.g. how often each hand type wins).
     */
    public static ChartPanel countBars(Map<String, Integer> counts, String title, String valueAxisLabel, Color barColor) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            dataset.addValue(e.getValue(), "v", e.getKey());
        }

        JFreeChart chart = ChartFactory.createBarChart(title, null, valueAxisLabel, dataset, PlotOrientation.VERTICAL, false, true, false);
        styleChrome(chart);

        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(PANEL_BG);
        plot.setOutlineVisible(false);
        plot.setRangeGridlinePaint(GRID);
        plot.setAxisOffset(new RectangleInsets(2, 2, 2, 2));

        CategoryAxis domain = plot.getDomainAxis();
        domain.setTickLabelFont(font(Font.BOLD, 11f));
        domain.setTickLabelPaint(TITLE);
        domain.setAxisLinePaint(AXIS);
        domain.setCategoryLabelPositions(CategoryLabelPositions.UP_45);

        NumberAxis range = (NumberAxis) plot.getRangeAxis();
        range.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        range.setTickLabelFont(font(Font.PLAIN, 12f));
        range.setLabelFont(font(Font.PLAIN, 13f));
        range.setTickLabelPaint(AXIS);
        range.setLabelPaint(AXIS);
        range.setAxisLinePaint(AXIS);

        BarRenderer renderer = new BarRenderer();
        renderer.setBarPainter(new StandardBarPainter());
        renderer.setShadowVisible(false);
        renderer.setDrawBarOutline(false);
        renderer.setSeriesPaint(0, barColor);
        renderer.setMaximumBarWidth(0.11);
        renderer.setDefaultItemLabelGenerator(new StandardCategoryItemLabelGenerator("{2}", new DecimalFormat("0")));
        renderer.setDefaultItemLabelFont(font(Font.BOLD, 11f));
        renderer.setDefaultItemLabelPaint(TITLE);
        renderer.setDefaultItemLabelsVisible(true);
        plot.setRenderer(renderer);

        return wrap(chart);
    }

    /**
     * Multi-series XY line chart (one coloured line per player) — the session graph of
     * stack evolution across hands. The domain axis is treated as integer hand numbers.
     */
    public static ChartPanel lineChart(XYDataset dataset, String title, String xAxisLabel, String yAxisLabel) {
        JFreeChart chart = ChartFactory.createXYLineChart(title, xAxisLabel, yAxisLabel, dataset, PlotOrientation.VERTICAL, true, true, false);
        styleChrome(chart);

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(PANEL_BG);
        plot.setOutlineVisible(false);
        plot.setDomainGridlinePaint(GRID);
        plot.setRangeGridlinePaint(GRID);
        plot.setAxisOffset(new RectangleInsets(2, 2, 2, 2));

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        for (int i = 0; i < dataset.getSeriesCount(); i++) {
            renderer.setSeriesPaint(i, PALETTE[i % PALETTE.length]);
            renderer.setSeriesStroke(i, new BasicStroke(2.4f));
        }
        plot.setRenderer(renderer);

        NumberAxis domain = (NumberAxis) plot.getDomainAxis();
        domain.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        domain.setTickLabelFont(font(Font.PLAIN, 12f));
        domain.setLabelFont(font(Font.PLAIN, 13f));
        domain.setTickLabelPaint(AXIS);
        domain.setLabelPaint(AXIS);
        domain.setAxisLinePaint(AXIS);

        NumberAxis range = (NumberAxis) plot.getRangeAxis();
        range.setTickLabelFont(font(Font.PLAIN, 12f));
        range.setLabelFont(font(Font.PLAIN, 13f));
        range.setTickLabelPaint(AXIS);
        range.setLabelPaint(AXIS);
        range.setAxisLinePaint(AXIS);

        return wrap(chart);
    }

    private static final Color[] PALETTE = {
        new Color(255, 102, 0), new Color(0, 120, 170), new Color(46, 160, 75),
        new Color(170, 70, 160), new Color(210, 160, 0), new Color(120, 90, 200),
        new Color(0, 160, 160), new Color(200, 80, 80), new Color(110, 140, 40), new Color(90, 90, 90)
    };

    /**
     * Radar/spider chart with one axis per metric and one translucent web per player. The
     * scale is fixed 0..100, so it is meant for percentage metrics. {@code seriesByPlayer}
     * maps each player to a value per axis (in {@code axisLabels} order).
     */
    public static ChartPanel radar(String title, String[] axisLabels, Map<String, double[]> seriesByPlayer) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (Map.Entry<String, double[]> e : seriesByPlayer.entrySet()) {
            double[] vals = e.getValue();
            for (int a = 0; a < axisLabels.length; a++) {
                // Cada eje DEBE tener valor (no se puede saltar uno sin desalinear el
                // radar), asi que un valor no finito se sustituye por 0.
                double raw = a < vals.length ? vals[a] : 0.0;
                dataset.addValue(Double.isFinite(raw) ? raw : 0.0, e.getKey(), axisLabels[a]);
            }
        }

        SpiderWebPlot plot = new SpiderWebPlot(dataset);
        plot.setStartAngle(90);
        plot.setInteriorGap(0.30);
        plot.setBackgroundPaint(PANEL_BG);
        plot.setOutlineVisible(false);
        plot.setAxisLinePaint(new Color(200, 200, 200));
        plot.setWebFilled(true);
        plot.setForegroundAlpha(0.45f);
        plot.setMaxValue(100.0);
        plot.setLabelFont(font(Font.BOLD, 12f));
        plot.setLabelPaint(TITLE);

        int idx = 0;
        for (String ignored : seriesByPlayer.keySet()) {
            plot.setSeriesPaint(idx, PALETTE[idx % PALETTE.length]);
            idx++;
        }

        JFreeChart chart = new JFreeChart(title, font(Font.BOLD, 17f), plot, true);
        styleChrome(chart);
        return wrap(chart);
    }

    /**
     * Horizontal bar chart of one value per player in a single flat colour, with the bar
     * value printed using {@code labelFormat} (e.g. {@code "{2}%"} or {@code "{2}s"}).
     * JFreeChart draws the first category on top, so pass the data highest-first to keep
     * the leader on top.
     */
    public static ChartPanel valueBars(Map<String, Double> data, String title, String valueAxisLabel, String labelFormat, Color barColor) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (Map.Entry<String, Double> e : data.entrySet()) {
            if (isFinite(e.getValue())) {
                dataset.addValue(e.getValue(), "v", e.getKey());
            }
        }

        JFreeChart chart = ChartFactory.createBarChart(title, null, valueAxisLabel, dataset, PlotOrientation.HORIZONTAL, false, true, false);
        styleChrome(chart);

        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(PANEL_BG);
        plot.setOutlineVisible(false);
        plot.setRangeGridlinePaint(GRID);
        plot.setAxisOffset(new RectangleInsets(2, 2, 2, 2));

        CategoryAxis domain = plot.getDomainAxis();
        domain.setTickLabelFont(font(Font.BOLD, 13f));
        domain.setTickLabelPaint(TITLE);
        domain.setAxisLinePaint(AXIS);

        NumberAxis range = (NumberAxis) plot.getRangeAxis();
        range.setTickLabelFont(font(Font.PLAIN, 12f));
        range.setLabelFont(font(Font.PLAIN, 13f));
        range.setTickLabelPaint(AXIS);
        range.setLabelPaint(AXIS);
        range.setAxisLinePaint(AXIS);

        BarRenderer renderer = new BarRenderer();
        renderer.setBarPainter(new StandardBarPainter());
        renderer.setShadowVisible(false);
        renderer.setDrawBarOutline(false);
        renderer.setSeriesPaint(0, barColor);
        renderer.setMaximumBarWidth(0.14);
        renderer.setDefaultItemLabelGenerator(new StandardCategoryItemLabelGenerator(labelFormat, new DecimalFormat("#,##0.#")));
        renderer.setDefaultItemLabelFont(font(Font.BOLD, 12f));
        // Etiquetas BLANCAS DENTRO de la barra (sobre el color), igual que benefitBars:
        // el gris oscuro no contrastaba sobre barras azules/naranjas.
        renderer.setDefaultItemLabelPaint(Color.WHITE);
        renderer.setDefaultPositiveItemLabelPosition(new ItemLabelPosition(ItemLabelAnchor.INSIDE3, TextAnchor.CENTER_RIGHT));
        renderer.setDefaultNegativeItemLabelPosition(new ItemLabelPosition(ItemLabelAnchor.INSIDE9, TextAnchor.CENTER_LEFT));
        renderer.setDefaultItemLabelsVisible(true);
        plot.setRenderer(renderer);

        return wrap(chart);
    }
}
