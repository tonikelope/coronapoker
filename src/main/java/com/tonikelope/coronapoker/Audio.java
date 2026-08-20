/*
 * Copyright (C) 2020 tonikelope
 _              _ _        _                  
| |_ ___  _ __ (_) | _____| | ___  _ __   ___ 
| __/ _ \| '_ \| | |/ / _ \ |/ _ \| '_ \ / _ \
| || (_) | | | | |   <  __/ | (_) | |_) |  __/
 \__\___/|_| |_|_|_|\_\___|_|\___/| .__/ \___|
 ____    ___  ____    ___  
|___ \  / _ \|___ \  / _ \ 
  __) || | | | __) || | | |
 / __/ | |_| |/ __/ | |_| |
|_____| \___/|_____| \___/ 

https://github.com/tonikelope/coronapoker
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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.BooleanControl;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.JLabel;
import javax.swing.Timer;

/**
 * Static audio subsystem: WAV/MP3 playback (including background music loops),
 * Google Translate TTS and voice-message playback, the volume/mute state
 * machine (master switch, per-window mutes, TTS ducking, voice-recording
 * silence), the Settings-dialog preview ("audition") player, and the looping
 * danger alert used by fatal-error popups.
 *
 * @author tonikelope
 */
public class Audio {

    public volatile static boolean AUDIO_AVAILABLE = true;
    public static volatile float MASTER_VOLUME;
    public static final float TTS_VOLUME = 2.0f;
    // Music attenuation while the TTS voice speaks (~ -10.5 dB)
    public static final float TTS_DUCKING = 0.3f;
    public static final Map.Entry<String, Float> ASCENSOR_VOLUME = new ConcurrentHashMap.SimpleEntry<>("misc/background_music.mp3", 0.4f);
    public static final Map.Entry<String, Float> STATS_VOLUME = new ConcurrentHashMap.SimpleEntry<>("misc/stats_music.mp3", 0.3f);
    public static final Map.Entry<String, Float> WAITING_ROOM_VOLUME = new ConcurrentHashMap.SimpleEntry<>("misc/waiting_room.mp3", 0.9f);
    public static final Map.Entry<String, Float> ABOUT_VOLUME = new ConcurrentHashMap.SimpleEntry<>("misc/about_music.mp3", 0.9f);
    public static final Map<String, Float> CUSTOM_VOLUMES = Map.ofEntries(ASCENSOR_VOLUME, STATS_VOLUME, WAITING_ROOM_VOLUME, ABOUT_VOLUME);
    public static final ConcurrentHashMap<String, CoronaMP3FilePlayer> MP3_LOOP = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, ConcurrentLinkedQueue<Clip>> WAVS_RESOURCES = new ConcurrentHashMap<>();
    // Reusable pre-opened clips for animation-synced SFX (the shuffle). A normal
    // play acquires and opens a fresh line every time; for a sound that must
    // start/stop in lockstep with each GIF cycle, that per-cycle open() can stall
    // on a busy device and overshoot the cycle's stop, leaving the cycle silent.
    // A preloaded clip opens the line ONCE and is then started/rewound/stopped
    // instantly, so it can never lose that race.
    public static final ConcurrentHashMap<String, Clip> PRELOADED_WAVS = new ConcurrentHashMap<>();
    public static final ConcurrentLinkedQueue<String> MP3_LOOP_MUTED = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean VOLUME_REFRESH_QUEUED = new AtomicBoolean(false);
    public static final Object TTS_LOCK = new Object();
    public static final Object VOL_LOCK = new Object();
    public static final int MAX_TTS_LENGTH = 150;
    public static final Map<String, String> TTS_ES_WORD_REPLACE;
    public static final Timer VOLUME_TIMER;
    public volatile static boolean MUTED_ALL = false;
    public volatile static boolean MUTED_WAV = false;
    // Derived state (see refreshMp3LoopMuteState): never set directly from anywhere, and only
    // this class reads it, so it stays private.
    private volatile static boolean MUTED_MP3_LOOP = false;
    // Total local silence while recording a voice message (the mic must not
    // pick up music, effects or other voices). Derived state: every volume
    // law consults it, so overlapping TTS/note playback windows cannot undo
    // it and the right state is restored whenever it drops.
    public volatile static boolean VOICE_RECORDING = false;
    // Reference count of active recordings. A fast re-record (release the voice
    // key and hold it again within the previous note's tail grace) overlaps two
    // recording windows; the older note's stop() must not lift the silence under
    // the newer one. Counting keeps VOICE_RECORDING true while ANY recording is
    // live, regardless of which thread increments/decrements first.
    private final static AtomicInteger VOICE_RECORDING_COUNT = new AtomicInteger(0);
    // How many TEMPORARY background-music silences are live at once, combined with whether the
    // global sound switch has it muted (see refreshMp3LoopMuteState).
    private final static AtomicInteger MP3_LOOP_MUTE_COUNT = new AtomicInteger(0);
    private volatile static boolean MUTED_MP3_LOOP_GLOBAL = false;
    public volatile static CoronaMP3FilePlayer TTS_PLAYER = null;

    // Settings-dialog "audition" (the play button next to each sound): only one at a time,
    // whether music (streaming player) or effect (clip). null = no audition of that kind in
    // progress. The effect token distinguishes auditions of the SAME wav (card-uncover and
    // my-cards share uncover.wav).
    private volatile static CoronaMP3FilePlayer PREVIEW_MP3_PLAYER = null;
    private volatile static String PREVIEW_WAV_SOUND = null;
    private volatile static Object PREVIEW_WAV_TOKEN = null;

    // Looping danger alert while a fatal-error popup (voided hand / security violation) is
    // open. The flag distinguishes "playback was requested" from "the clip is already open" so
    // the loop isn't left orphaned if the popup closes before the clip's async load finishes.
    private static final Object DANGER_ALERT_LOCK = new Object();
    private volatile static boolean DANGER_ALERT_ON = false;
    private volatile static Clip DANGER_ALERT_CLIP = null;

    // Blacklist for missing or corrupted sound files to prevent console flooding
    public static final Set<String> BLACKLISTED_SOUNDS = ConcurrentHashMap.newKeySet();

    static {

        Map<String, String> tts_es_replace = new HashMap<>();

        TTS_ES_WORD_REPLACE = Collections.unmodifiableMap(tts_es_replace);

        VOLUME_TIMER = new Timer(250, new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent ae) {

                VOLUME_TIMER.stop();

                refreshALLVolumes();
            }
        });

        VOLUME_TIMER.setRepeats(false);

        VOLUME_TIMER.setCoalesce(false);

    }

    public static String replaceWordsTTSMsg(String msg, Map<String, String> map) {

        for (Map.Entry<String, String> entry : map.entrySet()) {

            msg = msg.replaceAll("\\b" + Pattern.quote(entry.getKey()) + "\\b", entry.getValue());

        }

        return msg;
    }

    public static void playRandomWavResource(Map<String, String[]> sonidos) {

        ArrayList<String> sounds = new ArrayList<>();

        for (Map.Entry<String, String[]> entry : sonidos.entrySet()) {

            String folder = entry.getKey();

            String[] ficheros = entry.getValue();

            for (String fichero : ficheros) {
                sounds.add(folder + fichero);
            }
        }

        if (!sounds.isEmpty()) {
            int elegido = Helpers.CSPRNG_GENERATOR.nextInt(sounds.size());

            playWavResource(sounds.get(elegido));
        }
    }

    public static void playRandomWavResourceAndWait(Map<String, String[]> sonidos) {

        ArrayList<String> sounds = new ArrayList<>();

        for (Map.Entry<String, String[]> entry : sonidos.entrySet()) {

            String folder = entry.getKey();

            String[] ficheros = entry.getValue();

            for (String fichero : ficheros) {
                sounds.add(folder + fichero);
            }
        }

        if (!sounds.isEmpty()) {

            int elegido = Helpers.CSPRNG_GENERATOR.nextInt(sounds.size());

            playWavResourceAndWait(sounds.get(elegido));
        }
    }

    public static float findSoundVolume(String sound) {

        if (MASTER_VOLUME <= 0f) {
            return 0f;
        }

        Float custom = CUSTOM_VOLUMES.get(sound);

        return custom != null ? custom * MASTER_VOLUME : MASTER_VOLUME;
    }

    private static InputStream getSoundInputStream(String sound) {

        // Return null immediately if the file is known to be broken/missing
        if (BLACKLISTED_SOUNDS.contains(sound)) {
            return null;
        }

        if (Files.exists(Paths.get(sound))) {

            try {
                return new FileInputStream(sound);
            } catch (FileNotFoundException ex) {
                Logger.getLogger(Audio.class.getName()).log(Level.WARNING, "Sound file not found: {0}", ex.getMessage());
                BLACKLISTED_SOUNDS.add(sound);
                return null;
            }
        }

        if (Init.MOD != null) {

            if (Files.exists(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/sounds/" + sound))) {

                try {
                    return new FileInputStream(Helpers.getCurrentJarParentPath() + "/mod/sounds/" + sound);
                } catch (FileNotFoundException ex) {
                    Logger.getLogger(Audio.class.getName()).log(Level.WARNING, "Mod sound not found: {0}", ex.getMessage());
                    BLACKLISTED_SOUNDS.add(sound);
                    return null;
                }

            } else if (Files.exists(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/cinematics/" + sound))) {

                try {
                    return new FileInputStream(Helpers.getCurrentJarParentPath() + "/mod/cinematics/" + sound);
                } catch (FileNotFoundException ex) {
                    Logger.getLogger(Audio.class.getName()).log(Level.WARNING, "Mod cinematic sound not found: {0}", ex.getMessage());
                    BLACKLISTED_SOUNDS.add(sound);
                    return null;
                }
            }

        }

        InputStream is;

        if ((is = Helpers.class.getResourceAsStream("/sounds/" + sound)) != null || (is = Helpers.class.getResourceAsStream("/cinematics/" + sound)) != null) {
            return is;
        }

        Logger.getLogger(Audio.class.getName()).log(Level.INFO, "SOUND not found: {0}. Adding to blacklist.", sound);
        BLACKLISTED_SOUNDS.add(sound);

        return null;
    }

    public static void refreshALLVolumes() {

        refreshALLVolumes(true);

    }

    public static void refreshALLVolumes(boolean confirmation_sound) {

        // Quick refreshes fire on every keystroke / slider tick: coalesce them.
        // The debounced VOLUME_TIMER still runs a full refresh (with the
        // confirmation beep) at the end, so no final state is ever missed.
        if (!confirmation_sound && !VOLUME_REFRESH_QUEUED.compareAndSet(false, true)) {
            return;
        }

        try {

            queueVolumeRefresh(confirmation_sound);

        } catch (Exception ex) {
            // The flag is cleared inside the task, so a rejected submit (the
            // pool shut down between hands) would latch it and turn every later
            // refresh into a no-op: the game audio would stop being muted while
            // recording a voice note, for the rest of the session.
            if (!confirmation_sound) {
                VOLUME_REFRESH_QUEUED.set(false);
            }
            Logger.getLogger(Audio.class.getName()).log(Level.WARNING, "Cannot queue a volume refresh: {0}", ex.getMessage());
        }
    }

    private static void queueVolumeRefresh(boolean confirmation_sound) {

        Helpers.threadRun(() -> {

            if (!confirmation_sound) {
                VOLUME_REFRESH_QUEUED.set(false);
            }

            synchronized (VOL_LOCK) {

                try {

                    refreshALLWAVVolume();
                    refreshALLMP3LoopVolume();
                    refreshTTSVolume();

                    if (confirmation_sound && GameFrame.volumenSonidoOn()) {
                        playWavResource("misc/volume_change.wav");
                    }

                } catch (Exception ex) {
                    Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, "Error refreshing volumes: {0}", ex.getMessage());
                }

            }
        });
    }

    public static void refreshTTSVolume() {

        // Local snapshot: TTS() nulls the volatile field when playback ends.
        CoronaMP3FilePlayer tts_player = TTS_PLAYER;

        if (tts_player != null) {
            if (!GameFrame.SONIDOS || VOICE_RECORDING) {
                tts_player.setVolume(0f);
            } else {
                tts_player.setVolume(MASTER_VOLUME > 0f ? (TTS_VOLUME * MASTER_VOLUME > 1f ? 1f : TTS_VOLUME * MASTER_VOLUME) : 0f);
            }
        }

    }

    public static void refreshALLWAVVolume() {

        for (Map.Entry<String, ConcurrentLinkedQueue<Clip>> entry : WAVS_RESOURCES.entrySet()) {

            ConcurrentLinkedQueue<Clip> list = entry.getValue();

            for (Clip c : list) {

                try {
                    if (c != null && c.isOpen()) {
                        setClipVolume(entry.getKey(), c, false);
                    }
                } catch (Exception ex) {
                    Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, "Error setting WAV volume: {0}", ex.getMessage());
                }
            }
        }

        for (Map.Entry<String, Clip> entry : PRELOADED_WAVS.entrySet()) {
            try {
                Clip c = entry.getValue();
                if (c != null && c.isOpen()) {
                    setClipVolume(entry.getKey(), c, false);
                }
            } catch (Exception ex) {
                Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, "Error setting preloaded WAV volume: {0}", ex.getMessage());
            }
        }
    }

    public static void refreshALLMP3LoopVolume() {

        for (Map.Entry<String, CoronaMP3FilePlayer> entry : MP3_LOOP.entrySet()) {

            try {
                setMP3LoopPlayerVolume(entry.getKey(), entry.getValue());
            } catch (Exception ex) {
                Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, "Error setting MP3 loop volume: {0}", ex.getMessage());
            }

        }

    }

    /**
     * Computes the effective playback volume for a background music loop,
     * applying every mute source. Each track has its own per-window toggle
     * (in-game music governed by {@code MUSICA_AMBIENTAL}, waiting-room music
     * by {@code MUSICA_SALA}, etc.) — living here rather than in
     * {@link #MP3_LOOP_MUTED} lets those flags take effect from startup and be
     * read from anywhere in the game. A track is off if its own toggle is off
     * OR the music MASTER ({@code MUSICA}) is off.
     *
     * @param sound the loop's resource key (its map entry key in
     * {@link #MP3_LOOP})
     * @return the volume to apply, 0 when muted
     */
    public static float effectiveLoopVolume(String sound) {

        boolean game_music_off = ASCENSOR_VOLUME.getKey().equals(sound) && (!GameFrame.MUSICA || !GameFrame.MUSICA_AMBIENTAL);
        boolean room_music_off = WAITING_ROOM_VOLUME.getKey().equals(sound) && (!GameFrame.MUSICA || !GameFrame.MUSICA_SALA);
        boolean about_music_off = ABOUT_VOLUME.getKey().equals(sound) && (!GameFrame.MUSICA || !GameFrame.MUSICA_ABOUT);
        boolean stats_music_off = STATS_VOLUME.getKey().equals(sound) && (!GameFrame.MUSICA || !GameFrame.MUSICA_STATS);

        // Single source of truth for MP3 loop volume
        if (!GameFrame.SONIDOS || game_music_off || room_music_off || about_music_off || stats_music_off || MUTED_MP3_LOOP || VOICE_RECORDING || MP3_LOOP_MUTED.contains(sound)) {
            return 0f;
        }

        // With sounds enabled, MUTED_ALL is only ever raised while the TTS
        // voice speaks (muteAllExceptMp3Loops): duck the music under it.
        if (MUTED_ALL) {
            return findSoundVolume(sound) * TTS_DUCKING;
        }

        return findSoundVolume(sound);
    }

    public static void setMP3LoopPlayerVolume(String sound, CoronaMP3FilePlayer player) {

        player.setVolume(effectiveLoopVolume(sound));

    }

    /**
     * Re-applies the effective volume to a loop that's currently playing (no-op
     * if inactive). Used by the ambient-music toggle so the change is heard
     * instantly without stopping or reopening the line.
     */
    public static void refreshLoopVolume(String sound) {

        CoronaMP3FilePlayer player = MP3_LOOP.get(sound);

        if (player != null) {
            setMP3LoopPlayerVolume(sound, player);
        }
    }

    public static void setClipVolume(String sound, Clip clip, boolean bypass_muted) {
        setClipVolume(sound, clip, bypass_muted, false, false);
    }

    public static void setClipVolume(String sound, Clip clip, boolean bypass_muted, boolean force_silent) {
        setClipVolume(sound, clip, bypass_muted, force_silent, false);
    }

    /**
     * Applies the effective volume (or mute) to an open clip.
     *
     * @param bypass_muted skip {@code MUTED_ALL}/{@code MUTED_WAV} (other
     * silence sources still apply)
     * @param force_silent mutes THIS specific clip (as if the sound were muted)
     * WITHOUT skipping playback, so {@code playWavResourceAndWait} still waits
     * out its full duration. Used by disable-able BLOCKING effects (game over,
     * timeout) to go silent while keeping the pacing the wav's wait imposes.
     * @param force_preview AUDITION (Settings play button): forces normal
     * volume, skipping ALL gates (master SONIDOS off, mutes, force_silent,
     * recording), so an effect can be heard even with its checkbox disabled.
     * Only goes silent if the master volume is 0.
     */
    public static void setClipVolume(String sound, Clip clip, boolean bypass_muted, boolean force_silent, boolean force_preview) {

        FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);

        boolean mute_supported = clip.isControlSupported(BooleanControl.Type.MUTE);

        boolean silent = force_preview ? findSoundVolume(sound) == 0f
                : (force_silent || !GameFrame.SONIDOS || VOICE_RECORDING || findSoundVolume(sound) == 0f || ((MUTED_ALL || MUTED_WAV) && !bypass_muted));

        if (silent) {

            if (mute_supported) {
                ((BooleanControl) clip.getControl(BooleanControl.Type.MUTE)).setValue(true);
            } else {
                gainControl.setValue(gainControl.getMinimum());
            }

        } else {
            float db = Helpers.floatClean(20f * (float) Math.log10(findSoundVolume(sound)), 2);

            gainControl.setValue(Math.min(Math.max(db, gainControl.getMinimum()), gainControl.getMaximum()));

            if (mute_supported) {
                // A clip muted earlier (e.g. created while MASTER_VOLUME was 0) must
                // become audible again when the volume is raised mid-play.
                ((BooleanControl) clip.getControl(BooleanControl.Type.MUTE)).setValue(false);
            }
        }
    }

    public static boolean playWavResourceAndWait(String sound) {

        return playWavResourceAndWait(sound, true, false);

    }

    public static boolean playWavResourceAndWait(String sound, boolean force_close, boolean bypass_muted) {
        return playWavResourceAndWait(sound, force_close, bypass_muted, false);
    }

    /**
     * @param force_silent play the wav in SILENCE (clip muted) but still wait
     * out its full duration; for disable-able blocking effects whose wait time
     * must be preserved
     */
    public static boolean playWavResourceAndWait(String sound, boolean force_close, boolean bypass_muted, boolean force_silent) {
        return playWavResourceAndWait(sound, force_close, bypass_muted, force_silent, false);
    }

    /**
     * Plays a WAV resource synchronously, waiting for it to finish (or to be
     * stopped).
     *
     * @param force_close stop and clear any other clips already playing this
     * sound first
     * @param bypass_muted skip {@code MUTED_ALL}/{@code MUTED_WAV} (see
     * {@link #setClipVolume})
     * @param force_silent see the 4-arg overload
     * @param force_preview AUDITION: plays at normal volume, skipping the sound
     * gates (see {@link #setClipVolume}); used by {@link #previewWav} for the
     * Settings play button
     * @return true if playback (or its silent/emulated equivalent) completed;
     * false if the sound couldn't be found/opened
     */
    public static boolean playWavResourceAndWait(String sound, boolean force_close, boolean bypass_muted, boolean force_silent, boolean force_preview) {
        if (!GameFrame.TEST_MODE) {

            // Abort early if the file is blacklisted
            if (BLACKLISTED_SOUNDS.contains(sound)) {
                return false;
            }

            InputStream sound_stream;
            if ((sound_stream = getSoundInputStream(sound)) != null) {

                // 1. Open streams in a try-with-resources to satisfy the compiler and prevent memory leaks
                try (final BufferedInputStream bis = new BufferedInputStream(sound_stream); final javax.sound.sampled.AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(bis)) {

                    // Calculate exact duration mathematically (for Dummy Fallback)
                    long frames = audioInputStream.getFrameLength();
                    float frameRate = audioInputStream.getFormat().getFrameRate();
                    long durationMicros = (long) (1000000.0f * frames / frameRate);

                    // Clip only opens PCM audio: if the WAV arrives in a compressed encoding
                    // (mu-law / A-law, like voice notes), convert it to 16-bit PCM_SIGNED on the
                    // fly. Game effects are already PCM, so this block doesn't even trigger for
                    // them (PCM encoding => clip_input == audioInputStream, no cost).
                    final javax.sound.sampled.AudioFormat src_format = audioInputStream.getFormat();
                    final javax.sound.sampled.AudioFormat.Encoding enc = src_format.getEncoding();
                    javax.sound.sampled.AudioInputStream clip_input = audioInputStream;
                    if (enc != javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED && enc != javax.sound.sampled.AudioFormat.Encoding.PCM_UNSIGNED) {
                        javax.sound.sampled.AudioFormat pcm_format = new javax.sound.sampled.AudioFormat(
                                javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
                                src_format.getSampleRate(), 16, src_format.getChannels(),
                                src_format.getChannels() * 2, src_format.getSampleRate(), false);
                        clip_input = AudioSystem.getAudioInputStream(pcm_format, audioInputStream);
                    }

                    // 2. Request physical audio hardware in an inner try-with-resources
                    try (final Clip clip = AudioDeviceManager.getClip()) {

                        // Single lookup: stopWavResource() may remove the map entry at any
                        // time, so the queue must never be re-fetched (a null re-fetch NPEs
                        // and the generic catch used to blacklist the sound for the session).
                        final ConcurrentLinkedQueue<Clip> clip_queue = WAVS_RESOURCES.computeIfAbsent(sound, k -> new ConcurrentLinkedQueue<>());

                        // A one-shot clip posts a STOP event the moment it reaches
                        // the end of media (or when a concurrent force_close stops
                        // it). Waiting on that event lets the tail render in full
                        // and releases at the exact, click-free instant — unlike
                        // the former LOOP_CONTINUOUSLY + timed stop(), which cut the
                        // line at an arbitrary playhead position (parking for the
                        // data length ignored the output buffer latency, so the last
                        // buffer was still playing when the timer elapsed) and
                        // truncated the waveform mid-sample, producing the
                        // intermittent end-of-sound click.
                        final java.util.concurrent.CountDownLatch finished = new java.util.concurrent.CountDownLatch(1);

                        final javax.sound.sampled.LineListener end_listener = (javax.sound.sampled.LineEvent ev) -> {
                            if (ev.getType() == javax.sound.sampled.LineEvent.Type.STOP) {
                                finished.countDown();
                            }
                        };

                        try {

                            synchronized (clip_queue) {

                                if (force_close) {

                                    for (Clip c : clip_queue) {

                                        if (c != null) {
                                            synchronized (c) {
                                                if (c.isOpen() && c.isRunning()) {
                                                    c.stop();
                                                }
                                            }
                                        }
                                    }

                                    clip_queue.clear();
                                }

                                clip_queue.add(clip);

                                clip.open(clip_input);

                                setClipVolume(sound, clip, bypass_muted, force_silent, force_preview);

                                // Registered before start() so an ultra-short clip
                                // cannot finish before we are listening.
                                clip.addLineListener(end_listener);

                                clip.start();
                            }

                            try {
                                // Bounded only as a guard against a dropped event;
                                // the natural STOP releases long before this fires.
                                finished.await(clip.getMicrosecondLength() / 1000 + 500, java.util.concurrent.TimeUnit.MILLISECONDS);
                            } finally {
                                clip.removeLineListener(end_listener);
                            }

                            synchronized (clip) {
                                if (clip.isRunning()) {
                                    clip.stop();
                                }
                            }

                        } finally {
                            // Always deregister (also on LineUnavailable, which used to leak
                            // one queue entry per play while no audio device was present).
                            clip_queue.remove(clip);
                        }

                        // --- SUCCESS ZONE ---
                        if (clip.isOpen() && !AUDIO_AVAILABLE) {
                            AUDIO_AVAILABLE = true;
                            Logger.getLogger(Audio.class.getName()).log(Level.INFO, "Audio device detected. Sound effects restored.");
                        }

                        return true;

                    } catch (IllegalArgumentException | javax.sound.sampled.LineUnavailableException ex) {
                        // --- DUMMY AUDIO DEVICE ZONE ---
                        if (AUDIO_AVAILABLE) {
                            AUDIO_AVAILABLE = false;
                            Logger.getLogger(Audio.class.getName()).log(Level.WARNING, "WAV: No audio device found. Emulating playback duration ({0} ms) to maintain UI sync.", durationMicros / 1000);
                        }

                        // Fake the playback by sleeping exactly the duration of the audio clip
                        Helpers.parkThreadMicros(durationMicros);

                        return true; // Return true to signal that the "playback" finished normally

                    }
                } catch (UnsupportedAudioFileException | IOException ex) {
                    Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, "ERROR -> {0} | Exception: {1}", new Object[]{sound, ex.getMessage()});
                    // Blacklist only on hard FILE failures (corrupted header, unreadable
                    // stream) to prevent future spam.
                    BLACKLISTED_SOUNDS.add(sound);
                } catch (Exception ex) {
                    // Transient failure (e.g. a concurrent stop) — do NOT blacklist.
                    Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, "ERROR -> {0} | Exception: {1}", new Object[]{sound, ex.getMessage()});
                }
            }
        }
        return false;
    }

    public static void playLoopMp3Resource(String sound) {

        if (!GameFrame.TEST_MODE) {

            Helpers.threadRun(() -> {

                // Prevent starting the thread if we already know the sound is dead
                if (BLACKLISTED_SOUNDS.contains(sound)) {
                    return;
                }

                CoronaMP3FilePlayer audio_player = new CoronaMP3FilePlayer();
                MP3_LOOP.put(sound, audio_player);

                do {

                    try {

                        float vol = effectiveLoopVolume(sound);

                        InputStream is = getSoundInputStream(sound);
                        if (is == null) {
                            // File not found or stream is dead, break the infinite loop
                            MP3_LOOP.remove(sound, audio_player);
                            break;
                        }

                        // Attempt to play the audio file. play() returns true once an output line
                        // was opened (played / finished / cancelled / silent) and false ONLY when
                        // no line could be obtained (device busy/missing) — it swallows the
                        // LineUnavailableException internally, so we gate the retry ourselves.
                        boolean line_opened = audio_player.play(javax.sound.sampled.AudioSystem.getAudioInputStream(is), vol);

                        if (line_opened) {
                            // --- SUCCESS ZONE --- device is present; if it had been lost, note it's back.
                            if (!AUDIO_AVAILABLE) {
                                AUDIO_AVAILABLE = true;
                                Logger.getLogger(Audio.class.getName()).log(Level.INFO, "Audio device detected. Background music restored.");
                            }
                        } else {
                            // --- FAILURE ZONE (NO AUDIO DEVICE) --- without this backoff the do/while
                            // re-opens the stream and re-parses the header with ZERO delay, spinning a
                            // core at ~100% CPU until a device reappears or stopLoopMp3 runs. (play()
                            // no longer rethrows, so the catch(IllegalArgumentException) backoff below
                            // is now dead code.)
                            if (AUDIO_AVAILABLE) {
                                AUDIO_AVAILABLE = false;
                                Logger.getLogger(Audio.class.getName()).log(Level.WARNING, "No audio device found. Suppressing further MP3 errors until reconnected.");
                            }
                            Helpers.parkThreadMicros(2000000);
                        }

                    } catch (IllegalArgumentException ex) {
                        // --- FAILURE ZONE (NO AUDIO DEVICE) ---
                        if (AUDIO_AVAILABLE) {
                            AUDIO_AVAILABLE = false;
                            Logger.getLogger(Audio.class.getName()).log(Level.WARNING, "No audio device found. Suppressing further MP3 errors until reconnected.");
                            Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, "Exception: {0}", ex.getMessage());
                        }

                        Helpers.parkThreadMicros(2000000);

                    } catch (Exception ex) {
                        // Irrecoverable error for this specific file
                        Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, "MP3 Loop irrecoverable exception for {0}: {1}", new Object[]{sound, ex.getMessage()});
                        BLACKLISTED_SOUNDS.add(sound);
                        MP3_LOOP.remove(sound, audio_player);
                        break; // Kill the infinite loop immediately
                    }

                    // Generation check: a concurrent restart of this sound replaces the
                    // map entry with a fresh player, which must end THIS loop too (the
                    // old containsKey check kept zombie threads looping forever).
                } while (MP3_LOOP.get(sound) == audio_player);
            });

        }
    }

    private static boolean googleTranslatorTTSBASE64(String text, String lang, String filename) {

        String url = "https://www.google.com/async/translate_tts?client=firefox-b-d&yv=3&ttsp=tl:" + lang + ",txt:__TTS__,spd:1&async=_fmt:jspb";

        HttpURLConnection con = null;

        boolean error = false;

        try {

            URL url_api = new URL(url.replace("__TTS__", URLEncoder.encode(URLEncoder.encode(text.replaceAll("[^?!.]$", "$0."), "UTF-8").replace("+", "%20"))));

            con = (HttpURLConnection) url_api.openConnection();

            con.addRequestProperty("User-Agent", Helpers.USER_AGENT_WEB_BROWSER);

            con.setUseCaches(false);

            // TTS_LOCK is held while downloading: without timeouts a hung
            // connection would block TTS (and pool threads) forever.
            con.setConnectTimeout(Helpers.COOPERATIVE_SESSION_IO_TIMEOUT_MS);

            con.setReadTimeout(Helpers.COOPERATIVE_SESSION_IO_TIMEOUT_MS);

            try (InputStream is = con.getInputStream(); BufferedOutputStream bfos = new BufferedOutputStream(new FileOutputStream(System.getProperty("java.io.tmpdir") + "/" + filename + ".txt"))) {

                byte[] buffer = new byte[1024];

                int reads;

                while ((reads = is.read(buffer)) != -1) {

                    if (Thread.currentThread().isInterrupted()) {
                        throw new java.io.InterruptedIOException("table teardown");
                    }

                    bfos.write(buffer, 0, reads);
                }

            } catch (Exception ex) {

                error = true;
                if (!Thread.currentThread().isInterrupted()) {
                    Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, "TTS API Error: {0}", ex.getMessage());
                    Logger.getLogger(Audio.class.getName()).log(Level.WARNING, "TTS SERVICE Google Translator BASE64 ERROR!");
                }

            }

            if (error) {
                return false;
            }

            String mp3_b64 = new String(Files.readAllBytes(Paths.get(System.getProperty("java.io.tmpdir") + "/" + filename + ".txt")), StandardCharsets.UTF_8);

            Pattern pattern = Pattern.compile("\\[\"([^\\[\\]\"]+)\"\\]");

            Matcher matcher = pattern.matcher(mp3_b64);

            if (matcher.find()) {
                Files.write(Paths.get(System.getProperty("java.io.tmpdir") + "/" + filename), Base64.getDecoder().decode(matcher.group(1)));
            } else {
                error = true;
            }

        } catch (Exception ex) {
            error = true;
            if (!Thread.currentThread().isInterrupted()) {
                Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, "Base64 processing error: {0}", ex.getMessage());
                Logger.getLogger(Audio.class.getName()).log(Level.WARNING, "TTS SERVICE Google Translator BASE64 ERROR!");
            }

        } finally {

            if (con != null) {
                con.disconnect();
            }

            try {
                Files.deleteIfExists(Paths.get(System.getProperty("java.io.tmpdir") + "/" + filename + ".txt"));
            } catch (IOException ex) {
                Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, "Failed to delete temp file: {0}", ex.getMessage());
            }
        }

        return !error;

    }

    public static void TTS(String mensaje, JLabel chat_notify_label) {

        synchronized (TTS_LOCK) {

            if (mensaje != null && !"".equals(mensaje)) {

                String limpio = mensaje.toLowerCase().replaceAll("[^a-z0-9áéíóúñü@& ,.:;!?¡¿<>]", "").replaceAll(" {2,}", " ");

                if (GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {

                    limpio = Audio.replaceWordsTTSMsg(limpio, TTS_ES_WORD_REPLACE);
                }

                if (!"".equals(limpio) && limpio.length() <= MAX_TTS_LENGTH) {

                    // BE CAREFUL WITH WHAT IS SAID IN CHAT AS THESE ARE EXTERNAL SERVICES!! WE WILL SEE HOW LONG THEY LAST...
                    String filename = Helpers.genRandomString(30);

                    String[] tts_mp3bin_services;

                    if (GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {
                        tts_mp3bin_services = new String[]{
                            "http://translate.google.com/translate_tts?ie=UTF-8&total=1&idx=0&textlen=32&client=tw-ob&tl=es&q=__TTS__",
                            "https://text-to-speech-demo.ng.bluemix.net/api/v3/synthesize?text=__TTS__&voice=es-ES_LauraVoice&download=true&accept=audio%2Fmp3",};
                    } else {
                        tts_mp3bin_services = new String[]{
                            "http://translate.google.com/translate_tts?ie=UTF-8&total=1&idx=0&textlen=32&client=tw-ob&tl=en&q=__TTS__",
                            "https://text-to-speech-demo.ng.bluemix.net/api/v3/synthesize?text=__TTS__&voice=en-US_AllisonVoice&download=true&accept=audio%2Fmp3",};
                    }

                    boolean error;

                    int conta_service = 0;

                    do {
                        error = false;

                        HttpURLConnection con = null;

                        try {

                            URL url_api = new URL(tts_mp3bin_services[conta_service].replace("__TTS__", URLEncoder.encode(limpio, "UTF-8")));

                            con = (HttpURLConnection) url_api.openConnection();

                            con.addRequestProperty("User-Agent", Helpers.USER_AGENT_WEB_BROWSER);

                            con.setUseCaches(false);

                            // TTS_LOCK is held while downloading: without timeouts a hung
                            // connection would block TTS (and pool threads) forever.
                            con.setConnectTimeout(Helpers.COOPERATIVE_SESSION_IO_TIMEOUT_MS);

                            con.setReadTimeout(Helpers.COOPERATIVE_SESSION_IO_TIMEOUT_MS);

                            filename = Helpers.genRandomString(30);

                            try (InputStream is = con.getInputStream(); BufferedOutputStream bfos = new BufferedOutputStream(new FileOutputStream(System.getProperty("java.io.tmpdir") + "/" + filename))) {

                                byte[] buffer = new byte[1024];

                                int reads;

                                while ((reads = is.read(buffer)) != -1) {

                                    if (Thread.currentThread().isInterrupted()) {
                                        throw new java.io.InterruptedIOException("table teardown");
                                    }

                                    bfos.write(buffer, 0, reads);
                                }

                            } catch (Exception ex) {

                                if (!Thread.currentThread().isInterrupted()) {
                                    Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, "TTS download error: {0}", ex.getMessage());
                                    Logger.getLogger(Audio.class.getName()).log(Level.WARNING, "TTS SERVICE ({0}) ERROR!", String.valueOf(conta_service));
                                }
                                error = true;
                                conta_service++;
                            }

                        } catch (Exception ex) {

                            if (!Thread.currentThread().isInterrupted()) {
                                Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, "TTS connection error: {0}", ex.getMessage());
                                Logger.getLogger(Audio.class.getName()).log(Level.WARNING, "TTS SERVICE ({0}) ERROR!", String.valueOf(conta_service));
                            }
                            error = true;
                            conta_service++;

                        } finally {

                            if (con != null) {
                                con.disconnect();
                            }
                        }

                    } while (error && conta_service < tts_mp3bin_services.length
                            && !Thread.currentThread().isInterrupted());

                    if (error && !Thread.currentThread().isInterrupted()) {
                        // FALLBACK
                        error = !googleTranslatorTTSBASE64(limpio, GameFrame.DEFAULT_LANGUAGE.toLowerCase(), filename);
                    }

                    if (!error && !Thread.currentThread().isInterrupted()) {

                        muteAllExceptMp3Loops();

                        // The silence is ALWAYS lifted: if creating the player blew up (outside
                        // the try below) the game stayed mute for the rest of the session, while
                        // the speaker icon still claimed sound was on.
                        try {
                            Helpers.GUIRun(() -> {
                                chat_notify_label.setVisible(true);
                            });

                            TTS_PLAYER = new CoronaMP3FilePlayer();

                            // Short pre-roll: the duck lands in <= the music line
                            // buffer (120ms) and opening the voice line below takes
                            // >= 100ms on its own (same rationale as the voice
                            // messages).
                            Helpers.parkThreadMillis(100);

                            float volume = (GameFrame.SONIDOS && !VOICE_RECORDING && MASTER_VOLUME > 0f) ? (TTS_VOLUME * MASTER_VOLUME > 1f ? 1f : TTS_VOLUME * MASTER_VOLUME) : 0f;

                            try {

                                TTS_PLAYER.play(System.getProperty("java.io.tmpdir") + "/" + filename, volume);

                            } catch (Exception ex) {
                                Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, "TTS playback error: {0}", ex.getMessage());
                            } finally {
                                TTS_PLAYER = null;
                            }
                        } finally {
                            unmuteAllExceptMp3Loops();
                        }

                        Helpers.pausar(500);

                        Helpers.GUIRun(() -> {
                            chat_notify_label.setVisible(false);
                        });

                        try {
                            Files.deleteIfExists(Paths.get(System.getProperty("java.io.tmpdir") + "/" + filename));

                        } catch (IOException ex) {
                            Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, "Error deleting TTS temp file: {0}", ex.getMessage());
                        }

                    } else {
                        // The temp file used to be deleted only on success. When the service
                        // fails, whatever's left half-written on disk stuck around forever — one
                        // per message that didn't come through.
                        try {
                            Files.deleteIfExists(Paths.get(System.getProperty("java.io.tmpdir") + "/" + filename));

                        } catch (IOException ex) {
                            Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, "Error deleting the leftover TTS temp file: {0}", ex.getMessage());
                        }
                    }

                }

            }
        }
    }

    public static void setVoiceRecording(boolean recording) {

        // Count overlapping recordings instead of a plain toggle: the silence
        // stays up while ANY note is still being captured, so a fast re-record
        // is not un-muted by the previous note's stop() on another thread.
        int active = recording ? VOICE_RECORDING_COUNT.incrementAndGet()
                : VOICE_RECORDING_COUNT.updateAndGet(n -> n > 0 ? n - 1 : 0);

        VOICE_RECORDING = active > 0;

        // Reapply every volume law: silence on raise, and on drop restore
        // whatever the remaining flags dictate (e.g. a TTS window still open).
        // A failure here (the thread pool shut down between hands) must never
        // escape: the caller would leave the count raised and the game would
        // stay muted for the rest of the session.
        try {
            refreshALLVolumes(false);
        } catch (Exception ex) {
            Logger.getLogger(Audio.class.getName()).log(Level.WARNING, "Cannot refresh volumes on a voice recording change: {0}", ex.getMessage());
        }
    }

    // Bounded recovery for the output line being momentarily busy right after a
    // recording (capture-line close + the mute/unmute/duck volume churn). The
    // extra latency is only paid when the first open fails: (ATTEMPTS - 1)
    // backoffs at most. The happy path opens on the first attempt.
    private static final int VOICE_LINE_OPEN_ATTEMPTS = 4;
    private static final int VOICE_LINE_OPEN_BACKOFF_MILLIS = 120;

    // The voice/TTS line gain: silent when sound is off, while a recording is
    // live (the mic must not capture the playback) or when the master is muted.
    private static float voiceLineVolume() {
        return (GameFrame.SONIDOS && !VOICE_RECORDING && MASTER_VOLUME > 0f)
                ? Math.min(TTS_VOLUME * MASTER_VOLUME, 1f) : 0f;
    }

    public static void playVoiceMessage(byte[] wav, JLabel chat_notify_label) {

        if (wav == null || wav.length == 0) {
            return;
        }

        // Same pipeline as TTS: serialized under TTS_LOCK (a voice message and
        // a TTS never talk over each other) and played through TTS_PLAYER so
        // the volume refresh and the emergency stop also reach it. The label
        // is null for chat replays, which may also run in the waiting room
        // (no GameFrame).
        synchronized (TTS_LOCK) {

            muteAllExceptMp3Loops();

            // The silence is ALWAYS lifted (same reason as the TTS above): a failure creating
            // the player used to leave the game mute for the rest of the session.
            try {
                Helpers.GUIRun(() -> {
                    if (chat_notify_label != null) {
                        chat_notify_label.setVisible(true);
                    }
                });

                // Created before the pre-roll so a concurrent stop() (e.g. clicking
                // another note) during it still cancels this playback, as before.
                TTS_PLAYER = new CoronaMP3FilePlayer();

                // Short pre-roll: the duck lands in <= the music line buffer
                // (120ms) and opening the voice line below takes >= 100ms on its
                // own, so a long wait here just delays the voice.
                Helpers.parkThreadMillis(100);

                // true once the output line opened (played, finished or played
                // silent); false ONLY when the line could not be opened at all.
                boolean line_opened = false;

                // The output line can be momentarily unavailable right after a
                // recording: closing the capture line plus the mute/unmute/duck
                // churn leave the device busy for a few tens of ms, so a single
                // open would drop the note silently (the talk icon shows but
                // nothing plays). Wait it out with a small bounded backoff. The
                // happy path opens on the first attempt and is byte-for-byte
                // unchanged. A silent attempt (sound off, or a fresh recording
                // reopened the mic) or a dead audio device is never retried: there
                // is no audibility to recover by waiting.
                for (int attempt = 0; attempt < VOICE_LINE_OPEN_ATTEMPTS && !line_opened; attempt++) {

                    if (attempt > 0) {
                        Helpers.parkThreadMillis(VOICE_LINE_OPEN_BACKOFF_MILLIS);
                        TTS_PLAYER = new CoronaMP3FilePlayer();
                    }

                    float volume = voiceLineVolume();

                    try {
                        line_opened = TTS_PLAYER.play(AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav)), volume);
                    } catch (Exception ex) {
                        // Decode/stream error: not a line-open failure, retrying would not help
                        line_opened = true;
                        Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, "Voice message playback error: {0}", ex.getMessage());
                    } finally {
                        TTS_PLAYER = null;
                    }

                    if (!line_opened && (volume == 0f || !AUDIO_AVAILABLE)) {
                        break;
                    }
                }

                if (!line_opened && AUDIO_AVAILABLE && voiceLineVolume() > 0f) {
                    Logger.getLogger(Audio.class.getName()).log(Level.WARNING, "Voice message output line unavailable; note stays clickable in the chat");
                }
            } finally {
                unmuteAllExceptMp3Loops();
            }

            Helpers.pausar(500);

            Helpers.GUIRun(() -> {
                if (chat_notify_label != null) {
                    chat_notify_label.setVisible(false);
                }
            });
        }
    }

    public static void playWavResource(String sound) {

        playWavResource(sound, true);

    }

    public static void playWavResource(String sound, boolean force_close) {

        Helpers.threadRun(() -> {
            playWavResourceAndWait(sound, force_close, false);
        });

    }

    public static void stopWavResource(String sound) {
        Helpers.threadRun(() -> {
            ConcurrentLinkedQueue<Clip> list = WAVS_RESOURCES.remove(sound);

            if (list != null) {
                for (Clip c : list) {

                    if (c != null) {
                        synchronized (c) {
                            if (c.isOpen() && c.isRunning()) {
                                c.stop();
                            }
                        }
                    }

                }
            }
        });

    }

    // --- Audition (the play button in the Settings dialog) ---
    //
    // A resource (mp3 music or wav effect) plays at normal volume, skipping ALL sound gates (it
    // plays even with its checkbox or the master off; only the master volume is honored), capped
    // at max_millis. Only one audition at a time: starting another cuts the previous one. The
    // button flips to "stop" while playing and back to "play" when it ends (natural end, cap or
    // manual stop) via on_stop. Music uses the streaming player; effects use a clip.
    /**
     * Cuts off any audition in progress (music or effect). Its thread runs
     * on_stop (which flips the button back to "play") and, for music, restores
     * the background track. Called by the stop button, by switching auditions,
     * and by closing the Settings dialog.
     */
    public static void stopPreview() {

        CoronaMP3FilePlayer p = PREVIEW_MP3_PLAYER;
        if (p != null) {
            p.stop();
        }

        String w = PREVIEW_WAV_SOUND;
        if (w != null) {
            stopWavResource(w);
        }
    }

    /**
     * Routes by extension: {@code .mp3} = music (streaming), anything else =
     * effect (clip). Stops any other audition first so only one plays at a
     * time.
     */
    public static void previewResource(String sound, int max_millis, Runnable on_stop) {

        if (GameFrame.TEST_MODE) {
            if (on_stop != null) {
                Helpers.GUIRun(on_stop);
            }
            return;
        }

        stopPreview();

        if (sound.toLowerCase().endsWith(".mp3")) {
            previewMp3(sound, max_millis, on_stop);
        } else {
            previewWav(sound, max_millis, on_stop);
        }
    }

    // Music: own streaming player. Silences ONLY the matching background track (if it was
    // playing) to avoid hearing it doubled; does NOT touch the global loop mute (used by Crupier).
    private static void previewMp3(String sound, int max_millis, Runnable on_stop) {

        final CoronaMP3FilePlayer player = new CoronaMP3FilePlayer();

        PREVIEW_MP3_PLAYER = player;

        final boolean mute_match = MP3_LOOP.containsKey(sound) && !MP3_LOOP_MUTED.contains(sound);

        if (mute_match) {
            muteLoopMp3(sound);
        }

        // Duration cap: stops the audition at max_millis (play() blocks until end/stop).
        // Single-shot; if another audition already replaced it, the firing is a no-op.
        Timer cap = new Timer(max_millis, null);
        cap.setRepeats(false);
        cap.addActionListener((java.awt.event.ActionEvent ev) -> {
            cap.stop();
            if (PREVIEW_MP3_PLAYER == player) {
                Helpers.threadRun(player::stop);
            }
        });
        cap.start();

        Helpers.threadRun(() -> {
            try {
                InputStream is = getSoundInputStream(sound);
                if (is != null) {
                    player.play(javax.sound.sampled.AudioSystem.getAudioInputStream(is), findSoundVolume(sound));
                }
            } catch (Exception ex) {
                Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, "Preview MP3 error {0}: {1}", new Object[]{sound, ex.getMessage()});
            } finally {
                cap.stop();
                if (mute_match) {
                    unmuteLoopMp3(sound);
                }
                if (PREVIEW_MP3_PLAYER == player) {
                    PREVIEW_MP3_PLAYER = null;
                }
                if (on_stop != null) {
                    Helpers.GUIRun(on_stop);
                }
            }
        });
    }

    // Effect: clip via playWavResourceAndWait (force_preview skips the gates). The token tells
    // this audition apart from another one of the SAME sound (card-uncover and my-cards share
    // uncover.wav) so it doesn't clear the marker of whichever one has taken over.
    private static void previewWav(String sound, int max_millis, Runnable on_stop) {

        final Object token = new Object();

        PREVIEW_WAV_TOKEN = token;
        PREVIEW_WAV_SOUND = sound;

        Helpers.threadRun(() -> {
            // Duration cap: cuts the effect at max_millis if it runs longer (game_over,
            // iwtsth...). Single-shot; if the effect finishes first, the finally cancels the timer.
            Timer cap = new Timer(max_millis, null);
            cap.setRepeats(false);
            cap.addActionListener((java.awt.event.ActionEvent ev) -> {
                cap.stop();
                stopWavResource(sound);
            });
            cap.start();

            try {
                playWavResourceAndWait(sound, true, true, false, true);
            } finally {
                cap.stop();
                if (PREVIEW_WAV_TOKEN == token) {
                    PREVIEW_WAV_TOKEN = null;
                    PREVIEW_WAV_SOUND = null;
                }
                if (on_stop != null) {
                    Helpers.GUIRun(on_stop);
                }
            }
        });
    }

    // --- Looping danger alert (voided-hand / security-violation popup) ---
    /**
     * Starts the alert LOOPING (Clip.LOOP_CONTINUOUSLY) until
     * {@link #stopDangerAlertLoop()}: plays for as long as the fatal-error
     * popup stays open. Honors volume and mutes like any other effect
     * ({@link #setClipVolume}). No-op in TEST_MODE / dead file / no device.
     * Idempotent: stops any previous alert before starting.
     */
    public static void startDangerAlertLoop(String sound) {

        stopDangerAlertLoop();

        if (GameFrame.TEST_MODE || BLACKLISTED_SOUNDS.contains(sound)) {
            return;
        }

        DANGER_ALERT_ON = true;

        Helpers.threadRun(() -> {

            InputStream is = getSoundInputStream(sound);

            if (is == null) {
                return;
            }

            try (final BufferedInputStream bis = new BufferedInputStream(is); final javax.sound.sampled.AudioInputStream ais = AudioSystem.getAudioInputStream(bis)) {

                Clip clip = AudioDeviceManager.getClip();

                clip.open(ais);

                setClipVolume(sound, clip, false);

                synchronized (DANGER_ALERT_LOCK) {
                    // stopDangerAlertLoop may have requested a stop while we were loading the
                    // clip: don't start the loop (it would be orphaned) — just close it.
                    if (!DANGER_ALERT_ON) {
                        clip.close();
                        return;
                    }
                    DANGER_ALERT_CLIP = clip;
                    clip.loop(Clip.LOOP_CONTINUOUSLY);
                }

            } catch (Exception ex) {
                Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, "Danger alert loop error {0}: {1}", new Object[]{sound, ex.getMessage()});
            }
        });
    }

    /**
     * Stops the danger alert (if playing) and releases its line. Called when
     * the popup closes.
     */
    public static void stopDangerAlertLoop() {

        synchronized (DANGER_ALERT_LOCK) {

            DANGER_ALERT_ON = false;

            Clip clip = DANGER_ALERT_CLIP;

            DANGER_ALERT_CLIP = null;

            if (clip != null) {
                try {
                    clip.stop();
                    clip.close();
                } catch (Exception ex) {
                    Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, "Error stopping danger alert loop: {0}", ex.getMessage());
                }
            }
        }
    }

    /**
     * The OS takes time to "wake up" the audio endpoint the first time it's
     * opened and a line is started in this process (tens to hundreds of ms on
     * Windows), and during that activation it used to eat the first samples of
     * the first audible sound — the startup init.wav would occasionally come
     * out clipped. This synchronously plays a line of SILENCE to pay for that
     * cold start without anything being heard, leaving the device active so the
     * first real sound plays in full. Best-effort: if there's no device or the
     * format isn't supported, it's skipped (the sound plays as before, nothing
     * gets worse).
     */
    public static void warmAudioDevice() {

        if (GameFrame.TEST_MODE) {
            return;
        }

        try {

            javax.sound.sampled.AudioFormat format = new javax.sound.sampled.AudioFormat(44100f, 16, 2, true, false);

            javax.sound.sampled.SourceDataLine line = AudioDeviceManager.getSourceDataLine(format);

            try {
                line.open(format);
                line.start();

                // ~30 ms of silence: one full real playback cycle, enough for the endpoint to
                // finish activating.
                byte[] silence = new byte[format.getFrameSize() * Math.round(format.getFrameRate() * 0.03f)];
                line.write(silence, 0, silence.length);
                line.drain();
            } finally {
                line.stop();
                line.close();
            }

        } catch (Exception ex) {
            Logger.getLogger(Audio.class.getName()).log(Level.FINE, "Audio device warm-up skipped: {0}", ex.getMessage());
        }
    }

    /**
     * Opens (once) and keeps a reusable clip for a sound. Idempotent; safe to
     * call off the EDT before an animation. No-op in TEST_MODE, for
     * blacklisted/missing files, or when no audio device is available.
     */
    public static void preloadWav(String sound) {

        if (GameFrame.TEST_MODE || BLACKLISTED_SOUNDS.contains(sound) || PRELOADED_WAVS.containsKey(sound)) {
            return;
        }

        InputStream sound_stream = getSoundInputStream(sound);

        if (sound_stream == null) {
            return;
        }

        try (final BufferedInputStream bis = new BufferedInputStream(sound_stream); final javax.sound.sampled.AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(bis)) {

            Clip clip = AudioDeviceManager.getClip();

            clip.open(audioInputStream);

            // A concurrent preload of the same sound loses the race: keep the first.
            if (PRELOADED_WAVS.putIfAbsent(sound, clip) != null) {
                clip.close();
            } else if (!AUDIO_AVAILABLE) {
                AUDIO_AVAILABLE = true;
                Logger.getLogger(Audio.class.getName()).log(Level.INFO, "Audio device detected. Sound effects restored.");
            }

        } catch (IllegalArgumentException | javax.sound.sampled.LineUnavailableException ex) {
            if (AUDIO_AVAILABLE) {
                AUDIO_AVAILABLE = false;
                Logger.getLogger(Audio.class.getName()).log(Level.WARNING, "Preload: no audio device for {0}.", sound);
            }
        } catch (UnsupportedAudioFileException | IOException ex) {
            Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, "Preload ERROR -> {0} | {1}", new Object[]{sound, ex.getMessage()});
            BLACKLISTED_SOUNDS.add(sound);
        } catch (Exception ex) {
            Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, "Preload ERROR -> {0} | {1}", new Object[]{sound, ex.getMessage()});
        }
    }

    /**
     * (Re)starts a preloaded sound from frame 0 — instant, no line acquisition,
     * so it never loses a race against a concurrent stop. Lazily preloads if
     * needed.
     */
    public static void playPreloadedWav(String sound) {

        Clip clip = PRELOADED_WAVS.get(sound);

        if (clip == null) {
            preloadWav(sound);
            clip = PRELOADED_WAVS.get(sound);
        }

        if (clip != null) {
            synchronized (clip) {
                if (clip.isOpen()) {
                    clip.stop();
                    clip.flush();
                    clip.setFramePosition(0);
                    setClipVolume(sound, clip, false);
                    clip.start();
                }
            }
        }
    }

    public static void stopPreloadedWav(String sound) {

        Clip clip = PRELOADED_WAVS.get(sound);

        if (clip != null) {
            synchronized (clip) {
                if (clip.isOpen()) {
                    // stop + flush: discards the pending output buffer so the cut is EXACT (no
                    // tail audible after the cut frame), same as CoronaMP3FilePlayer.stop() does
                    // with its line.
                    clip.stop();
                    clip.flush();
                }
            }
        }
    }

    public static void closePreloadedWav(String sound) {

        Clip clip = PRELOADED_WAVS.remove(sound);

        if (clip != null) {
            synchronized (clip) {
                try {
                    clip.stop();
                    clip.close();
                } catch (Exception ex) {
                    Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, "Error closing preloaded WAV {0}: {1}", new Object[]{sound, ex.getMessage()});
                }
            }
        }
    }

    public static void closeAllPreloadedWavs() {

        for (String sound : new ArrayList<>(PRELOADED_WAVS.keySet())) {
            closePreloadedWav(sound);
        }
    }

    public static void stopLoopMp3(String sound) {

        CoronaMP3FilePlayer player = MP3_LOOP.remove(sound);

        MP3_LOOP_MUTED.remove(sound);

        if (player != null) {
            try {

                player.stop();

            } catch (Exception ex) {
                Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, "Error stopping MP3 loop: {0}", ex.getMessage());
            }
        }

    }

    public static void muteLoopMp3(String sound) {

        MP3_LOOP_MUTED.add(sound);

        CoronaMP3FilePlayer player = MP3_LOOP.get(sound);

        if (player != null) {
            try {

                player.setVolume(0f);

            } catch (Exception ex) {
                Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, "Error muting MP3 loop: {0}", ex.getMessage());
            }
        }

    }

    public static void unmuteLoopMp3(String sound) {

        MP3_LOOP_MUTED.remove(sound);

        CoronaMP3FilePlayer player = MP3_LOOP.get(sound);

        if (player != null) {
            try {

                player.setVolume(effectiveLoopVolume(sound));

            } catch (Exception ex) {
                Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, "Error unmuting MP3 loop: {0}", ex.getMessage());
            }
        }

    }

    public static void restartCurrentLoopMp3Resources() {

        ArrayList<String> sounds = new ArrayList<>(MP3_LOOP.keySet());

        ArrayList<String> muted = new ArrayList<>(MP3_LOOP_MUTED);

        for (String sound : sounds) {
            stopLoopMp3(sound);
        }

        // stopLoopMp3 clears the logical mute flags: restore them BEFORE
        // replaying so the new players start silent where they should.
        for (String sound : muted) {
            if (!MP3_LOOP_MUTED.contains(sound)) {
                MP3_LOOP_MUTED.add(sound);
            }
        }

        for (String sound : sounds) {
            playLoopMp3Resource(sound);
        }
    }

    public static void stopAllCurrentLoopMp3Resource() {

        Iterator<Map.Entry<String, CoronaMP3FilePlayer>> iterator = MP3_LOOP.entrySet().iterator();

        while (iterator.hasNext()) {

            Map.Entry<String, CoronaMP3FilePlayer> entry = iterator.next();

            try {

                iterator.remove();

                MP3_LOOP_MUTED.remove(entry.getKey());

                entry.getValue().stop();

            } catch (Exception ex) {
                Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, "Error stopping current MP3 loop: {0}", ex.getMessage());
            }
        }
    }

    public static void muteAll() {

        MUTED_ALL = true;

        // Global switch: does NOT go through the temporary-silence counter (see
        // refreshMp3LoopMuteState); the game also calls this to sync state, not only to toggle it.
        MUTED_MP3_LOOP_GLOBAL = true;

        refreshMp3LoopMuteState();

        muteAllWav();

    }

    /**
     * Mutes everything except MP3 loops, which are ducked instead so the music
     * stays audible but lowered under the TTS voice.
     */
    public static void muteAllExceptMp3Loops() {

        MUTED_ALL = true;

        muteAllWav();

        // Not muted, ducked: the music drops under the TTS voice
        refreshALLMP3LoopVolume();

    }

    /**
     * Counterpart of {@link #muteAllExceptMp3Loops()}. Do NOT use
     * {@link #unmuteAll()} here: that one also lifts the music's silence, which
     * this method never applied (it only ducks it) — so a Crupier sting that
     * was silencing the music mid-play would lose its silence and the music
     * would come back over it.
     */
    public static void unmuteAllExceptMp3Loops() {

        MUTED_ALL = false;

        unmuteAllWav();

        // Undoes the duck: volume returns to whatever the still-live flags dictate.
        refreshALLMP3LoopVolume();

    }

    public static void muteAllWav() {

        MUTED_WAV = true;

        for (Map.Entry<String, ConcurrentLinkedQueue<Clip>> entry : WAVS_RESOURCES.entrySet()) {

            ConcurrentLinkedQueue<Clip> list = entry.getValue();

            for (Clip c : list) {

                try {
                    if (c != null && c.isOpen()) {
                        ((BooleanControl) c.getControl(BooleanControl.Type.MUTE)).setValue(true);
                    }
                } catch (Exception ex) {
                    Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, "Error muting all WAV: {0}", ex.getMessage());
                }
            }
        }

        for (Clip c : PRELOADED_WAVS.values()) {
            try {
                if (c != null && c.isOpen()) {
                    ((BooleanControl) c.getControl(BooleanControl.Type.MUTE)).setValue(true);
                }
            } catch (Exception ex) {
                Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, "Error muting preloaded WAV: {0}", ex.getMessage());
            }
        }
    }

    // Music silence has TWO sources, and this is the only place where they're combined:
    //
    // 1) The player's GLOBAL sound switch (muteAll / unmuteAll), which lasts until changed. It
    //    can't be counted, because the game also applies it as a state sync and not only as a
    //    toggle: at the end of every hand, RESET_GAME calls muteAll again if sound is off.
    //    Counting it, two muteAll calls in a row would leave the count high and the music mute
    //    for the rest of the session.
    // 2) The TEMPORARY silences from Crupier stings (badbeat, lucky winner, losing player), which
    //    ARE counted: each runs on its own thread, and a multiway badbeat fires one PER loser,
    //    so with a plain boolean the first to finish would bring the music back over the rest.
    //    Same approach VOICE_RECORDING_COUNT already uses for voice notes.
    private static void refreshMp3LoopMuteState() {

        MUTED_MP3_LOOP = MUTED_MP3_LOOP_GLOBAL || MP3_LOOP_MUTE_COUNT.get() > 0;

        refreshALLMP3LoopVolume();
    }

    public static void muteAllLoopMp3() {

        MP3_LOOP_MUTE_COUNT.incrementAndGet();

        refreshMp3LoopMuteState();

    }

    public static void unmuteAllLoopMp3() {

        MP3_LOOP_MUTE_COUNT.updateAndGet(n -> n > 0 ? n - 1 : 0);

        refreshMp3LoopMuteState();

    }

    public static void unmuteAllWav() {

        MUTED_WAV = false;

        for (Map.Entry<String, ConcurrentLinkedQueue<Clip>> entry : WAVS_RESOURCES.entrySet()) {

            ConcurrentLinkedQueue<Clip> list = entry.getValue();

            for (Clip c : list) {

                try {

                    if (c != null && c.isOpen()) {
                        // Delegate: handles volume 0 via MUTE instead of feeding
                        // log10(0) = -Infinity into the gain control.
                        setClipVolume(entry.getKey(), c, false);
                    }

                } catch (Exception ex) {
                    Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, "Error unmuting all WAVs: {0}", ex.getMessage());
                }
            }
        }

        for (Map.Entry<String, Clip> entry : PRELOADED_WAVS.entrySet()) {
            try {
                Clip c = entry.getValue();
                if (c != null && c.isOpen()) {
                    setClipVolume(entry.getKey(), c, false);
                }
            } catch (Exception ex) {
                Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, "Error unmuting preloaded WAV: {0}", ex.getMessage());
            }
        }
    }

    public static void unmuteAll() {

        MUTED_ALL = false;

        // Lifts the GLOBAL silence. If a sting is still playing, the music stays muted until
        // the last one releases it — that's decided by refreshMp3LoopMuteState, not this method.
        MUTED_MP3_LOOP_GLOBAL = false;

        refreshMp3LoopMuteState();

        unmuteAllWav();

    }

    public static String getCurrentLoopMp3Playing() {

        for (Map.Entry<String, CoronaMP3FilePlayer> entry : MP3_LOOP.entrySet()) {

            // Skip logically muted loops: with two loops alive (e.g. waiting room
            // music over the muted background music) the CHM iteration order would
            // otherwise decide which one the caller gets.
            if (entry.getValue().isPlaying() && !MP3_LOOP_MUTED.contains(entry.getKey())) {
                return entry.getKey();
            }
        }

        return null;
    }

    public static void stopAllWavResources() {

        Iterator<Map.Entry<String, ConcurrentLinkedQueue<Clip>>> iterator = WAVS_RESOURCES.entrySet().iterator();

        while (iterator.hasNext()) {

            ConcurrentLinkedQueue<Clip> list = iterator.next().getValue();

            for (Clip c : list) {

                if (c != null) {
                    c.stop();
                }

            }

            iterator.remove();

        }
    }

    private Audio() {
    }

}
