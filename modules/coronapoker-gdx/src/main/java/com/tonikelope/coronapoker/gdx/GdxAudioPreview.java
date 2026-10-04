/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.files.FileHandle;

/** Single-owner preview player shared by every row of one settings surface. */
final class GdxAudioPreview {

    private Music active;
    private String activeKey;
    private float elapsed;
    private float limitSeconds;

    boolean active(String key) {
        return key != null && key.equals(activeKey) && active != null;
    }

    void toggle(String key, GdxSettingsContract.AudioPreview preview,
            float masterVolume) {
        if (active(key)) {
            stop();
            return;
        }
        stop();
        if (preview == null || Gdx.audio == null) return;
        FileHandle file = Gdx.files.internal("sounds/" + preview.resource());
        if (!file.exists()) {
            file = Gdx.files.internal("cinematics/" + preview.resource());
        }
        if (!file.exists()) return;
        Music music = Gdx.audio.newMusic(file);
        active = music;
        activeKey = key;
        elapsed = 0f;
        limitSeconds = preview.limitSeconds();
        music.setVolume(Math.max(0f, Math.min(1f,
                masterVolume * preview.volume())));
        music.setOnCompletionListener(ignored -> {
            if (active == music) stop();
        });
        music.play();
    }

    void update(float delta) {
        if (active == null) return;
        elapsed += Math.max(0f, delta);
        if (elapsed >= limitSeconds) stop();
    }

    void stop() {
        Music music = active;
        active = null;
        activeKey = null;
        elapsed = 0f;
        limitSeconds = 0f;
        if (music == null) return;
        try {
            music.stop();
        } finally {
            music.dispose();
        }
    }
}
