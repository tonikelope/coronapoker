package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.GlyphLayout.GlyphRun;
import com.badlogic.gdx.graphics.g2d.GlyphLayout.WrapState;
import com.badlogic.gdx.utils.Align;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

/** Chooses word breaks when greedy wrapping would leave an uneven right edge. */
final class GdxParagraphReflow {

    private static final int MAX_WORDS = 96;
    private static final int MAX_CACHED_MESSAGES = 24;
    private static final Pattern WORD = Pattern.compile("[^ ]+");
    private static final Plan UNCHANGED = new Plan("", 0f, 0,
            new BitSet(), new BitSet(), new BitSet());
    // Text is laid out on the render thread. Weak font keys let old atlases go.
    private static final Map<BitmapFont, LinkedHashMap<Key, Plan>> CACHE =
            new WeakHashMap<>();

    private GdxParagraphReflow() {
    }

    record Plan(String text, float width, int lineCount, BitSet softBreakRuns, BitSet raggedRuns,
            BitSet wrappedEndRuns) {
    }

    private record Key(String text, float width, float maximumWidth, float fontScale) {
    }

    static Plan plan(BitmapFont font, String text, float width,
            float maximumWidth, float maxExtraSpace) {
        var key = new Key(text, width, maximumWidth, font.getScaleX());
        var cache = CACHE.computeIfAbsent(font, ignored -> new LinkedHashMap<>());
        Plan plan = cache.get(key);
        if (plan == null) {
            plan = build(font, text, width, maximumWidth, maxExtraSpace);
            if (cache.size() == MAX_CACHED_MESSAGES) {
                cache.remove(cache.keySet().iterator().next());
            }
            cache.put(key, plan);
        }
        return plan == UNCHANGED ? null : plan;
    }

    static boolean needsReflow(GlyphRun line, float width, float maxExtraSpace) {
        if (line.wrapState == WrapState.lastWrapped) return shortEnding(line, width);
        return line.wrapState == WrapState.wrapped
                && width - line.width > wordGaps(line) * maxExtraSpace;
    }

    private static boolean shortEnding(GlyphRun line, float width) {
        return line.width < width * (wordGaps(line) == 0 ? 0.55f : 0.28f);
    }

    static int wordGaps(GlyphRun line) {
        int gaps = 0;
        for (int i = 0; i < line.glyphs.size - 1; i++) {
            if (line.glyphs.get(i).id == ' ') gaps++;
        }
        return gaps;
    }

    private static Plan build(BitmapFont font, String text, float width,
            float maximumWidth, float maxExtraSpace) {
        if (maxExtraSpace <= 0f || text.indexOf(' ') < 0
                || (font.getData().markupEnabled && text.indexOf('[') >= 0)) {
            return UNCHANGED;
        }
        var scratch = new GlyphLayout();
        scratch.setText(font, text, Color.WHITE, width, Align.left, true);
        int originalLines = scratch.runs.size;
        float originalHeight = scratch.height;
        float step = Math.max(font.getSpaceXadvance(), width / 120f);
        float minimumWidth = width * 0.90f;
        Plan bestPlan = UNCHANGED;
        double bestCost = Double.POSITIVE_INFINITY;
        Map<String, ParagraphWords> measured = new HashMap<>();
        // Try the preferred measure, a slightly narrower measure, and (for
        // floating tooltips) a wider one. Never use more vertical space than
        // the original wrap.
        int narrower = (int) ((width - minimumWidth) / step);
        int wider = (int) Math.ceil((maximumWidth - width) / step);
        for (int tick = -narrower; tick <= wider; tick++) {
            float candidate = Math.min(maximumWidth, width + tick * step);
            Plan plan = buildAtWidth(scratch, font, text, candidate, maxExtraSpace, measured);
            scratch.setText(font, plan.text(), Color.WHITE, candidate, Align.left, true);
            if (scratch.runs.size == plan.lineCount() && scratch.runs.size <= originalLines
                    && scratch.height <= originalHeight + 0.01f
                    && scratch.width <= candidate) {
                double cost = widthCost(scratch, plan, width, maxExtraSpace)
                        + (scratch.runs.size - originalLines) * 2d;
                if (cost < bestCost) {
                    bestCost = cost;
                    bestPlan = plan;
                }
            }
        }
        return bestPlan;
    }

    private static double widthCost(GlyphLayout layout, Plan plan, float preferredWidth,
            float maxExtraSpace) {
        double largestStretch = 0d;
        double cost = 0d;
        for (int index = 0; index < layout.runs.size; index++) {
            GlyphRun line = layout.runs.get(index);
            if (plan.wrappedEndRuns().get(index) && shortEnding(line, plan.width())) {
                cost += 2d;
            }
            if (line.wrapState != WrapState.wrapped && !plan.softBreakRuns().get(index)) continue;
            if (plan.raggedRuns().get(index)) {
                cost += 1.5d + Math.pow(1d - line.width / plan.width(), 2);
                continue;
            }
            int gaps = wordGaps(line);
            if (gaps == 0) continue;
            largestStretch = Math.max(largestStretch,
                    (plan.width() - line.width) / (gaps * maxExtraSpace));
        }
        double resize = (preferredWidth - plan.width()) / (preferredWidth * 0.10d);
        return cost + largestStretch * largestStretch + 0.45d * resize * resize;
    }

    private static Plan buildAtWidth(GlyphLayout scratch, BitmapFont font, String text,
            float width, float maxExtraSpace, Map<String, ParagraphWords> measured) {
        var replacement = new StringBuilder(text.length());
        var softBreakRuns = new BitSet();
        var raggedRuns = new BitSet();
        var wrappedEndRuns = new BitSet();
        int runOffset = 0;
        String[] paragraphs = text.split("\\n", -1);
        for (int index = 0; index < paragraphs.length; index++) {
            if (index > 0) replacement.append('\n');
            String paragraph = paragraphs[index];
            scratch.setText(font, paragraph, Color.WHITE, width, Align.left, true);
            int lineCount = scratch.runs.size;
            if (lineCount > 1) wrappedEndRuns.set(runOffset + lineCount - 1);
            boolean needsReflow = false;
            for (GlyphRun line : scratch.runs) {
                if (needsReflow(line, width, maxExtraSpace)) {
                    needsReflow = true;
                    break;
                }
            }
            String balanced = null;
            if (needsReflow) {
                var words = measured.computeIfAbsent(paragraph, ParagraphWords::new);
                balanced = balance(scratch, font, paragraph, words,
                        lineCount, width, maxExtraSpace, true);
                if (balanced == null) {
                    // Keep the paragraph left-aligned if every possible
                    // arrangement would need excessive spaces on some line.
                    balanced = balance(scratch, font, paragraph, words,
                            lineCount, width, maxExtraSpace, false);
                    raggedRuns.set(runOffset, runOffset + lineCount - 1);
                }
            }
            if (balanced != null) {
                softBreakRuns.set(runOffset, runOffset + lineCount - 1);
            }
            replacement.append(balanced == null ? paragraph : balanced);
            runOffset += lineCount;
        }
        return new Plan(replacement.toString(), width, runOffset,
                softBreakRuns, raggedRuns, wrappedEndRuns);
    }

    private record Word(int start, int end) {
    }

    private static final class ParagraphWords {
        final List<Word> words;
        final float[][] widths;
        final int[][] gaps;

        ParagraphWords(String paragraph) {
            var ranges = new ArrayList<Word>();
            var matches = WORD.matcher(paragraph);
            if (paragraph.indexOf('\t') < 0 && paragraph.indexOf(' ') >= 0) {
                while (matches.find()) {
                    if (ranges.size() == MAX_WORDS) {
                        ranges.clear();
                        break;
                    }
                    ranges.add(new Word(matches.start(), matches.end()));
                }
            }
            words = ranges;
            widths = new float[words.size()][words.size() + 1];
            gaps = new int[words.size()][words.size() + 1];
            for (var row : widths) Arrays.fill(row, Float.NaN);
        }

        void measure(GlyphLayout scratch, BitmapFont font, String paragraph, int start, int end) {
            if (!Float.isNaN(widths[start][end])) return;
            scratch.setText(font, paragraph, words.get(start).start(), words.get(end - 1).end(),
                    Color.WHITE, 0f, Align.left, false, null);
            widths[start][end] = scratch.width;
            gaps[start][end] = scratch.runs.isEmpty() ? 0 : wordGaps(scratch.runs.first());
        }
    }

    private static String balance(GlyphLayout scratch, BitmapFont font,
            String paragraph, ParagraphWords metrics, int lines, float width,
            float maxExtraSpace, boolean justify) {
        int count = metrics.words.size();
        if (count < lines || lines < 2) return null;

        // Work backwards. Each entry is the best continuation using exactly
        // that many lines, so reflow can never make a popup taller.
        var costs = new double[count + 1][lines + 1];
        var breaks = new int[count][lines + 1];
        for (int start = count - 1; start >= 0; start--) {
            Arrays.fill(costs[start], Double.POSITIVE_INFINITY);
            for (int end = start + 1; end <= count; end++) {
                metrics.measure(scratch, font, paragraph, start, end);
                float naturalWidth = metrics.widths[start][end];
                float slack = width - naturalWidth;
                if (slack < 0f) break;
                if (end == count) {
                    float minimum = end - start == 1 ? 0.55f : 0.28f;
                    double shortfall = Math.max(0f, minimum - naturalWidth / width);
                    costs[start][1] = shortfall == 0d ? 0d : 4d + 12d * shortfall * shortfall;
                    breaks[start][1] = end;
                    continue;
                }
                int gaps = metrics.gaps[start][end];
                if (justify && (gaps == 0 || slack > gaps * maxExtraSpace)) continue;
                double cost = justify ? gaps * Math.pow(slack / (gaps * maxExtraSpace), 2)
                        : 12d * Math.pow(slack / width, 2);
                for (int left = 2; left <= lines; left++) {
                    double total = cost + costs[end][left - 1];
                    if (total < costs[start][left]) {
                        costs[start][left] = total;
                        breaks[start][left] = end;
                    }
                }
            }
        }
        if (!Double.isFinite(costs[0][lines])) return null;
        var result = new StringBuilder(paragraph.length());
        int start = 0;
        for (int left = lines; left > 0; left--) {
            int end = breaks[start][left];
            if (start > 0) result.append('\n');
            result.append(paragraph, metrics.words.get(start).start(), metrics.words.get(end - 1).end());
            start = end;
        }
        return result.toString();
    }
}
