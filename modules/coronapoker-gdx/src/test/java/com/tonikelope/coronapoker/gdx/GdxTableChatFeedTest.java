/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.gdx.GdxTableChatFeed.Kind;
import com.tonikelope.coronapoker.gdx.GdxTableChatFeed.Line;
import com.tonikelope.coronapoker.gdx.GdxTableChatFeed.Visible;
import java.util.List;
import org.junit.jupiter.api.Test;

class GdxTableChatFeedTest {

    @Test
    void burstSharesOneFifoAcrossChatAndSystemNotices() {
        GdxTableChatFeed feed = new GdxTableChatFeed();
        Line text = new Line(Kind.TEXT, "Ana", "Hola", false, -1, null);
        Line joined = Line.notice(Kind.JOIN, "Luis", "se une a la timba");
        Line capture = Line.notice(Kind.SUCCESS, "", "Captura guardada");
        feed.add(text, 1f, true);
        feed.add(joined, 1f, true);
        for (int index = 0; index < GdxTableChatFeed.VISIBLE_LIMIT - 1; index++) {
            feed.add(Line.notice(Kind.INFO, "", "Notice " + index), 1f, true);
        }
        feed.add(capture, 1f, true);

        List<Line> visible = feed.visible(1f).stream().map(Visible::line).toList();
        assertEquals(GdxTableChatFeed.VISIBLE_LIMIT, visible.size());
        assertEquals("Notice 0", visible.get(0).text());
        assertEquals(capture, visible.get(visible.size() - 1));
        assertEquals(List.of(text, joined), feed.history().subList(0, 2));

        float expiration = 1f + GdxTableChatFeed.NOTICE_SECONDS;
        assertEquals(0.5f, feed.visible(expiration - 0.4f).get(0)
                .alpha(expiration - 0.4f), 0.001f);
        assertTrue(feed.visible(expiration).isEmpty());
        assertEquals(GdxTableChatFeed.VISIBLE_LIMIT + 2, feed.history().size());
    }

    @Test
    void historySurvivesMutedNotificationsAndTheTransportHistoryLimit() {
        GdxTableChatFeed feed = new GdxTableChatFeed();
        for (int index = 0; index < 650; index++) {
            feed.add(new Line(Kind.TEXT, "Ana", "Message " + index,
                    false, -1, null), index, false);
        }
        assertTrue(feed.visible(650f).isEmpty());
        assertEquals(650, feed.history().size());
        assertEquals("Message 0", feed.history().get(0).text());
        assertEquals("Message 649", feed.history().get(649).text());
    }

    @Test
    void chatContentCannotChangeThePresentationColors() {
        Line text = new Line(Kind.TEXT, "[red]Ana", "[]Hello\n[#000000]",
                true, -1, null);
        assertEquals("[#ffdc83][[red]Ana: [#f5f7fa][[]Hello [[#000000]",
                text.markup(text.text()));
        Line notice = Line.notice(Kind.LEAVE, "Ana", "leaves the game");
        assertEquals("[#ffad93]Ana [#ffad93]leaves the game",
                notice.markup(notice.text()));
    }
}
