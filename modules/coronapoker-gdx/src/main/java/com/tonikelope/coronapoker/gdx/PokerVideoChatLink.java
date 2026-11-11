/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

/** Text-chat token for a YouTube selection, without accepting external URLs. */
final class PokerVideoChatLink {

    private static final String PREFIX = "coronapoker-video:1:";
    private static final int TITLE_LIMIT = 180;

    private PokerVideoChatLink() {
    }

    static String format(PokerVideoSearch.Video video) {
        return PREFIX + video.type() + ":" + video.id() + ":"
                + cleanTitle(video.title());
    }

    static PokerVideoSearch.Video parse(String message) {
        if (!message.startsWith(PREFIX)
                || message.length() > PREFIX.length() + 400) return null;
        String[] parts = message.substring(PREFIX.length()).split(":", 3);
        if (parts.length != 3 || !(PokerVideoSearch.TYPE_NORMAL.equals(parts[0])
                || PokerVideoSearch.TYPE_SHORTS.equals(parts[0]))) return null;
        try {
            return new PokerVideoSearch.Video(parts[1], cleanTitle(parts[2]),
                    parts[0]);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static String cleanTitle(String title) {
        String clean = title.replaceAll("[\\p{Cc}\\p{Cf}]", " ").strip();
        int length = clean.codePointCount(0, clean.length());
        return length <= TITLE_LIMIT ? clean
                : clean.substring(0, clean.offsetByCodePoints(0, TITLE_LIMIT));
    }
}
