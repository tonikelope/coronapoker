/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Keeps the random catalogue intact while switching to saved favorites. */
final class PokerVideoCatalog {
    private static final int BACK_HISTORY = 120;
    private static final int RECENT_IDS = 4096;
    private final PokerVideoFavorites favorites;
    private final LinkedHashSet<String> recent = new LinkedHashSet<>();
    private List<PokerVideoSearch.Video> normal = List.of();
    private String type = PokerVideoSearch.TYPE_NORMAL;
    private boolean favoritesMode;
    private PokerVideoSearch.Video sharedVideo;
    private long revision;

    PokerVideoCatalog(PokerVideoFavorites favorites) {
        this.favorites = favorites;
    }

    synchronized void setType(String requested) {
        String next = PokerVideoSearch.videoType(requested);
        if (!type.equals(next)) {
            type = next;
            normal = List.of();
            recent.clear();
            if (!favoritesMode) revision++;
        }
    }

    synchronized void replaceNormal(List<PokerVideoSearch.Video> videos, String type) {
        setType(type);
        normal = List.copyOf(videos);
        recent.clear();
        videos.forEach(video -> recent.add(video.id()));
        if (!favoritesMode) revision++;
    }

    synchronized List<PokerVideoSearch.Video> extendNormal(
            List<PokerVideoSearch.Video> candidates, int currentIndex) {
        var additions = new ArrayList<PokerVideoSearch.Video>();
        for (var video : candidates) {
            if (recent.add(video.id())) additions.add(video);
        }
        if (additions.isEmpty()) return normal;
        int first = Math.max(0, currentIndex - BACK_HISTORY);
        var extended = new ArrayList<>(normal.subList(first, normal.size()));
        extended.addAll(additions);
        normal = List.copyOf(extended);
        while (recent.size() > RECENT_IDS) recent.remove(recent.iterator().next());
        return normal;
    }

    synchronized void toggleMode() {
        sharedVideo = null;
        favoritesMode = !favoritesMode;
        revision++;
    }

    synchronized void showShared(PokerVideoSearch.Video video) {
        if (video.equals(sharedVideo)) return;
        sharedVideo = video;
        revision++;
    }

    synchronized boolean sharedMode() {
        return sharedVideo != null;
    }

    synchronized void resumeCatalog() {
        if (sharedVideo == null) return;
        sharedVideo = null;
        revision++;
    }

    synchronized void toggleFavorite(PokerVideoSearch.Video video) throws IOException {
        favorites.toggle(video);
        if (favoritesMode) revision++;
    }

    synchronized boolean isFavorite(PokerVideoSearch.Video video) {
        return favorites.contains(video);
    }

    synchronized boolean favoritesMode() { return favoritesMode; }
    synchronized boolean hasNormalVideos() { return !normal.isEmpty(); }
    synchronized long revision() { return revision; }
    synchronized List<PokerVideoSearch.Video> videos() {
        if (sharedVideo != null) return List.of(sharedVideo);
        return favoritesMode ? favorites.videos() : normal;
    }
}
