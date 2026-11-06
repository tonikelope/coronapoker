/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.io.IOException;
import java.util.List;

/** Keeps the random catalogue intact while switching to saved favorites. */
final class PokerVideoCatalog {
    private final PokerVideoFavorites favorites;
    private List<PokerVideoSearch.Video> normal = List.of();
    private String type = PokerVideoSearch.TYPE_SHORTS;
    private boolean favoritesMode;
    private long revision;

    PokerVideoCatalog(PokerVideoFavorites favorites) {
        this.favorites = favorites;
    }

    synchronized void setType(String requested) {
        String next = PokerVideoSearch.videoType(requested);
        if (!type.equals(next)) {
            type = next;
            normal = List.of();
            revision++;
        }
    }

    synchronized void replaceNormal(List<PokerVideoSearch.Video> videos, String type) {
        setType(type);
        normal = List.copyOf(videos);
        if (!favoritesMode) revision++;
    }

    synchronized void toggleMode() {
        favoritesMode = !favoritesMode;
        revision++;
    }

    synchronized void toggleFavorite(PokerVideoSearch.Video video) throws IOException {
        favorites.toggle(video, type);
        if (favoritesMode) revision++;
    }

    synchronized boolean isFavorite(PokerVideoSearch.Video video) {
        return favorites.contains(video, type);
    }

    synchronized boolean favoritesMode() { return favoritesMode; }
    synchronized boolean hasNormalVideos() { return !normal.isEmpty(); }
    synchronized long revision() { return revision; }
    synchronized List<PokerVideoSearch.Video> videos() {
        return favoritesMode ? favorites.videos(type) : normal;
    }
}
