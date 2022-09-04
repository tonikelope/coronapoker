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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.BooleanControl;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.swing.JLabel;
import javax.swing.Timer;
import org.apache.commons.codec.binary.Base64;

/**
 *
 * @author tonikelope
 */
public class Audio {

    public static volatile float MASTER_VOLUME;
    public static final float TTS_VOLUME = 2.0f;
    public static final Map.Entry<String, Float> ASCENSOR_VOLUME = new ConcurrentHashMap.SimpleEntry<String, Float>("misc/background_music.mp3", 0.4f);
    public static final Map.Entry<String, Float> STATS_VOLUME = new ConcurrentHashMap.SimpleEntry<String, Float>("misc/stats_music.mp3", 0.3f);
    public static final Map.Entry<String, Float> WAITING_ROOM_VOLUME = new ConcurrentHashMap.SimpleEntry<String, Float>("misc/waiting_room.mp3", 0.9f);
    public static final Map.Entry<String, Float> ABOUT_VOLUME = new ConcurrentHashMap.SimpleEntry<String, Float>("misc/about_music.mp3", 0.9f);
    public static final Map<String, Float> CUSTOM_VOLUMES = Map.ofEntries(ASCENSOR_VOLUME, STATS_VOLUME, WAITING_ROOM_VOLUME, ABOUT_VOLUME);
    public static final ConcurrentHashMap<String, CoronaMP3FilePlayer> MP3_LOOP = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, CoronaMP3FilePlayer> MP3_RESOURCES = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, ConcurrentLinkedQueue<Clip>> WAVS_RESOURCES = new ConcurrentHashMap<>();
    public static final ConcurrentLinkedQueue<String> MP3_LOOP_MUTED = new ConcurrentLinkedQueue<>();
    public static final Object TTS_LOCK = new Object();
    public static final Object VOL_LOCK = new Object();
    public static final Object CLIP_STOP_LOCK = new Object();
    public static final int MAX_TTS_LENGTH = 150;
    public static final Map<String, String> TTS_ES_WORD_REPLACE;
    public static final Timer VOLUME_TIMER;
    public volatile static boolean MUTED_ALL = false;
    public volatile static boolean MUTED_WAV = false;
    public volatile static boolean MUTED_MP3 = false;
    public volatile static boolean MUTED_MP3_LOOP = false;
    public volatile static CoronaMP3FilePlayer TTS_PLAYER = null;

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

        return CUSTOM_VOLUMES.containsKey(sound) ? (MASTER_VOLUME > 0f ? CUSTOM_VOLUMES.get(sound) * MASTER_VOLUME : 0f) : (TTS_PLAYER != null && MP3_RESOURCES.containsKey(sound) && ((CoronaMP3FilePlayer) MP3_RESOURCES.get(sound)) == TTS_PLAYER ? (MASTER_VOLUME > 0f ? (TTS_VOLUME * MASTER_VOLUME > 1f ? 1f : TTS_VOLUME * MASTER_VOLUME) : 0f) : (MASTER_VOLUME > 0f ? MASTER_VOLUME : 0f));
    }

    private static InputStream getSoundInputStream(String sound) {

        if (Files.exists(Paths.get(sound))) {

            try {
                return new FileInputStream(sound);
            } catch (FileNotFoundException ex) {
                Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        if (Init.MOD != null) {

            if (Files.exists(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/sounds/" + sound))) {

                try {
                    return new FileInputStream(Helpers.getCurrentJarParentPath() + "/mod/sounds/" + sound);
                } catch (FileNotFoundException ex) {
                    Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, ex);
                }

            } else if (Files.exists(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/cinematics/" + sound))) {

                try {
                    return new FileInputStream(Helpers.getCurrentJarParentPath() + "/mod/cinematics/" + sound);
                } catch (FileNotFoundException ex) {
                    Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

        }

        InputStream is;

        if ((is = Helpers.class.getResourceAsStream("/sounds/" + sound)) != null || (is = Helpers.class.getResourceAsStream("/cinematics/" + sound)) != null) {
            return is;
        }

        Logger.getLogger(Audio.class.getName()).log(Level.INFO, "NO se encuentra el SONIDO {0}", sound);

        return null;
    }

    public static void refreshALLVolumes() {

        Helpers.threadRun(new Runnable() {

            @Override
            public void run() {

                synchronized (VOL_LOCK) {

                    try {

                        refreshALLWAVVolume();
                        refreshALLMP3Volume();
                        refreshALLMP3LoopVolume();
                        refreshTTSVolume();

                        playWavResource("misc/volume_change.wav");

                    } catch (Exception ex) {
                        Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, ex);
                    }

                }

            }
        });
    }

    public static void refreshTTSVolume() {

        if (TTS_PLAYER != null) {
            if (!GameFrame.SONIDOS) {
                TTS_PLAYER.setVolume(0f);
            } else {
                TTS_PLAYER.setVolume(MASTER_VOLUME > 0f ? (TTS_VOLUME * MASTER_VOLUME > 1f ? 1f : TTS_VOLUME * MASTER_VOLUME) : 0f);
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
                    Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    public static void refreshALLMP3Volume() {

        for (Map.Entry<String, CoronaMP3FilePlayer> entry : MP3_RESOURCES.entrySet()) {

            try {
                setMP3PlayerVolume(entry.getKey(), entry.getValue());
            } catch (Exception ex) {
                Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, ex);
            }

        }

    }

    public static void refreshALLMP3LoopVolume() {

        for (Map.Entry<String, CoronaMP3FilePlayer> entry : MP3_LOOP.entrySet()) {

            try {
                setMP3LoopPlayerVolume(entry.getKey(), entry.getValue());
            } catch (Exception ex) {
                Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, ex);
            }

        }

    }

    public static void setMP3LoopPlayerVolume(String sound, CoronaMP3FilePlayer player) {

        if (!GameFrame.SONIDOS || MP3_LOOP_MUTED.contains(sound)) {
            player.setVolume(0f);
        } else {
            player.setVolume(findSoundVolume(sound));
        }

    }

    public static void setMP3PlayerVolume(String sound, CoronaMP3FilePlayer player) {

        if (!GameFrame.SONIDOS) {
            player.setVolume(0f);
        } else {
            player.setVolume(findSoundVolume(sound));
        }

    }

    public static void setClipVolume(String sound, Clip clip, boolean bypass_muted) {

        FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);

        if (!GameFrame.SONIDOS || findSoundVolume(sound) == 0f || ((MUTED_ALL || MUTED_WAV) && !bypass_muted)) {
            ((BooleanControl) clip.getControl(BooleanControl.Type.MUTE)).setValue(true);
        } else {
            float db = Helpers.floatClean(20f * (float) Math.log10(findSoundVolume(sound)), 2);
            gainControl.setValue(db >= gainControl.getMinimum() ? db : gainControl.getMinimum());
        }
    }

    public static boolean playWavResourceAndWait(String sound) {

        return playWavResourceAndWait(sound, true, false);

    }

    public static boolean playWavResourceAndWait(String sound, boolean force_close, boolean bypass_muted) {
        if (!GameFrame.TEST_MODE) {
            InputStream sound_stream;
            if ((sound_stream = getSoundInputStream(sound)) != null) {
                try (final BufferedInputStream bis = new BufferedInputStream(sound_stream); final Clip clip = AudioSystem.getClip()) {

                    if (force_close && WAVS_RESOURCES.get(sound) != null) {

                        synchronized (WAVS_RESOURCES.get(sound)) {
                            for (Clip c : WAVS_RESOURCES.get(sound)) {

                                if (c != null) {
                                    synchronized (c) {
                                        if (c.isOpen() && c.isRunning()) {
                                            c.stop();
                                        }
                                    }
                                }
                            }
                            WAVS_RESOURCES.get(sound).clear();

                            WAVS_RESOURCES.get(sound).add(clip);

                            clip.open(AudioSystem.getAudioInputStream(bis));

                            setClipVolume(sound, clip, bypass_muted);

                            clip.start();

                            clip.loop(Clip.LOOP_CONTINUOUSLY);

                        }

                    } else {

                        WAVS_RESOURCES.putIfAbsent(sound, new ConcurrentLinkedQueue<>());

                        synchronized (WAVS_RESOURCES.get(sound)) {

                            WAVS_RESOURCES.get(sound).add(clip);

                            clip.open(AudioSystem.getAudioInputStream(bis));

                            setClipVolume(sound, clip, bypass_muted);

                            clip.start();

                            clip.loop(Clip.LOOP_CONTINUOUSLY);
                        }
                    }

                    Helpers.parkThreadMicros(clip.getMicrosecondLength());

                    synchronized (clip) {
                        if (clip.isRunning()) {
                            clip.stop();
                        }
                    }

                    if (WAVS_RESOURCES.get(sound) != null) {
                        WAVS_RESOURCES.get(sound).remove(clip);
                    }

                    return true;

                } catch (Exception ex) {
                    Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, "ERROR -> {0}", sound);
                    Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        return false;
    }

    public static synchronized int getTotalLoopMp3Playing() {

        int tot = 0;

        for (Map.Entry<String, CoronaMP3FilePlayer> entry : MP3_LOOP.entrySet()) {

            if (entry.getValue().isPlaying()) {
                tot++;
            }
        }

        return tot;
    }

    public static boolean isLoopMp3Playing() {

        for (Map.Entry<String, CoronaMP3FilePlayer> entry : MP3_LOOP.entrySet()) {

            if (entry.getValue().isPlaying()) {

                return true;

            }
        }

        return false;

    }

    public static void playLoopMp3Resource(String sound) {

        if (!GameFrame.TEST_MODE) {

            Helpers.threadRun(new Runnable() {

                @Override
                public void run() {

                    CoronaMP3FilePlayer audio_player = new CoronaMP3FilePlayer();

                    MP3_LOOP.put(sound, audio_player);

                    do {

                        try {

                            float vol;

                            if (!GameFrame.SONIDOS || MP3_LOOP_MUTED.contains(sound)) {
                                vol = 0f;
                            } else {
                                vol = findSoundVolume(sound);
                            }

                            audio_player.play(javax.sound.sampled.AudioSystem.getAudioInputStream(getSoundInputStream(sound)), vol);

                        } catch (Exception ex) {
                            Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, ex);
                        }

                    } while (MP3_LOOP.containsKey(sound));

                }
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

            try ( InputStream is = con.getInputStream();  BufferedOutputStream bfos = new BufferedOutputStream(new FileOutputStream(System.getProperty("java.io.tmpdir") + "/" + filename + ".txt"))) {

                byte[] buffer = new byte[1024];

                int reads;

                while ((reads = is.read(buffer)) != -1) {

                    bfos.write(buffer, 0, reads);
                }

            } catch (Exception ex) {

                Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, ex);
                Logger.getLogger(Audio.class.getName()).log(Level.WARNING, "TTS SERVICE Google Translator BASE64 ERROR!");

            }

            String mp3_b64 = new String(Files.readAllBytes(Paths.get(System.getProperty("java.io.tmpdir") + "/" + filename + ".txt")), StandardCharsets.UTF_8);

            Pattern pattern = Pattern.compile("\\[\"([^\\[\\]\"]+)\"\\]");

            Matcher matcher = pattern.matcher(mp3_b64);

            if (matcher.find()) {
                Files.write(Paths.get(System.getProperty("java.io.tmpdir") + "/" + filename), Base64.decodeBase64(matcher.group(1)));
            } else {
                error = true;
            }

        } catch (Exception ex) {
            error = true;
            Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, ex);
            Logger.getLogger(Audio.class.getName()).log(Level.WARNING, "TTS SERVICE Google Translator BASE64 ERROR!");

        } finally {

            if (con != null) {
                con.disconnect();
            }

            try {
                Files.deleteIfExists(Paths.get(System.getProperty("java.io.tmpdir") + "/" + filename + ".txt"));
            } catch (IOException ex) {
                Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, ex);
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

                    //¡¡OJO CON LO QUE SE DICE POR EL CHAT QUE ESTOS SON SERVICIOS EXTERNOS!! VEREMOS LO QUE DURAN...
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

                            filename = Helpers.genRandomString(30);

                            try ( InputStream is = con.getInputStream();  BufferedOutputStream bfos = new BufferedOutputStream(new FileOutputStream(System.getProperty("java.io.tmpdir") + "/" + filename))) {

                                byte[] buffer = new byte[1024];

                                int reads;

                                while ((reads = is.read(buffer)) != -1) {

                                    bfos.write(buffer, 0, reads);
                                }

                            } catch (Exception ex) {

                                Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, ex);
                                Logger.getLogger(Audio.class.getName()).log(Level.WARNING, "TTS SERVICE (" + String.valueOf(conta_service) + ") ERROR!");
                                error = true;
                                conta_service++;
                            }

                        } catch (Exception ex) {

                            Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, ex);
                            Logger.getLogger(Audio.class.getName()).log(Level.WARNING, "TTS SERVICE (" + String.valueOf(conta_service) + ") ERROR!");
                            error = true;
                            conta_service++;

                        } finally {

                            if (con != null) {
                                con.disconnect();
                            }
                        }

                    } while (error && conta_service < tts_mp3bin_services.length);

                    if (error) {
                        //FALLBACK
                        error = !googleTranslatorTTSBASE64(limpio, GameFrame.DEFAULT_LANGUAGE.toLowerCase(), filename);
                    }

                    if (!error) {

                        muteAllExceptMp3Loops();

                        Helpers.GUIRun(new Runnable() {
                            @Override
                            public void run() {

                                GameFrame.getInstance().getSonidos_menu().setEnabled(false);

                                chat_notify_label.setVisible(true);
                            }
                        });

                        CoronaMP3FilePlayer TTS_PLAYER = new CoronaMP3FilePlayer();

                        float volume = (GameFrame.SONIDOS && MASTER_VOLUME > 0f) ? (TTS_VOLUME * MASTER_VOLUME > 1f ? 1f : TTS_VOLUME * MASTER_VOLUME) : 0f;

                        try {

                            TTS_PLAYER.play(System.getProperty("java.io.tmpdir") + "/" + filename, volume);

                        } catch (Exception ex) {
                            Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, ex);
                        } finally {
                            TTS_PLAYER = null;
                        }

                        unmuteAll();

                        Helpers.pausar(500);

                        Helpers.GUIRun(new Runnable() {
                            @Override
                            public void run() {

                                GameFrame.getInstance().getSonidos_menu().setEnabled(true);

                                chat_notify_label.setVisible(false);

                            }
                        });

                        try {
                            Files.deleteIfExists(Paths.get(System.getProperty("java.io.tmpdir") + "/" + filename));

                        } catch (IOException ex) {
                            Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, ex);
                        }

                    }

                }

            }
        }
    }

    public static void playWavResource(String sound) {

        playWavResource(sound, true);

    }

    public static void playWavResource(String sound, boolean force_close) {

        Helpers.threadRun(new Runnable() {
            @Override
            public void run() {
                playWavResourceAndWait(sound, force_close, false);
            }
        });

    }

    public static void stopWavResource(String sound) {

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

    }

    public static void stopLoopMp3(String sound) {

        CoronaMP3FilePlayer player = MP3_LOOP.remove(sound);

        MP3_LOOP_MUTED.remove(sound);

        if (player != null) {
            try {

                player.stop();

            } catch (Exception ex) {
                Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

    public static void pauseLoopMp3(String sound) {

        CoronaMP3FilePlayer player = MP3_LOOP.get(sound);

        if (player != null) {
            try {
                player.pause();

            } catch (Exception ex) {
                Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, ex);
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
                Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

    public static void unmuteLoopMp3(String sound) {

        MP3_LOOP_MUTED.remove(sound);

        CoronaMP3FilePlayer player = MP3_LOOP.get(sound);

        if (player != null) {
            try {

                if (!MUTED_ALL && !MUTED_MP3_LOOP) {
                    player.setVolume(findSoundVolume(sound));
                }

            } catch (Exception ex) {
                Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

    public static void resumeLoopMp3Resource(String sound) {

        CoronaMP3FilePlayer player = MP3_LOOP.get(sound);

        if (player != null) {

            try {
                player.resume();

            } catch (Exception ex) {
                Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, ex);
            }

        } else {
            playLoopMp3Resource(sound);
        }

    }

    public static void pauseCurrentLoopMp3Resource() {

        for (Map.Entry<String, CoronaMP3FilePlayer> entry : MP3_LOOP.entrySet()) {

            if (entry.getValue().isPlaying()) {

                try {
                    entry.getValue().pause();
                } catch (Exception ex) {
                    Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, ex);
                }

            }
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
                Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public static void muteAll() {

        MUTED_ALL = true;

        muteAllMp3();

        muteAllLoopMp3();

        muteAllWav();

    }

    public static void muteAllExceptMp3Loops() {

        MUTED_ALL = true;

        muteAllMp3();

        muteAllWav();

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
                    Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    public static void muteAllLoopMp3() {

        MUTED_MP3_LOOP = true;

        for (Map.Entry<String, CoronaMP3FilePlayer> entry : MP3_LOOP.entrySet()) {

            try {
                entry.getValue().setVolume(0f);
            } catch (Exception ex) {
                Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, ex);
            }

        }

    }

    public static void muteAllMp3() {

        MUTED_MP3 = true;

        for (Map.Entry<String, CoronaMP3FilePlayer> entry : MP3_RESOURCES.entrySet()) {

            try {
                entry.getValue().setVolume(0f);
            } catch (Exception ex) {
                Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, ex);
            }

        }

    }

    public static void unmuteAllLoopMp3() {

        MUTED_MP3_LOOP = false;

        for (Map.Entry<String, CoronaMP3FilePlayer> entry : MP3_LOOP.entrySet()) {

            try {

                if (!MP3_LOOP_MUTED.contains(entry.getKey()) && !MUTED_ALL) {
                    entry.getValue().setVolume(findSoundVolume(entry.getKey()));
                }

            } catch (Exception ex) {
                Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public static void unmuteAllMp3() {

        MUTED_MP3 = false;

        for (Map.Entry<String, CoronaMP3FilePlayer> entry : MP3_RESOURCES.entrySet()) {

            try {

                if (!MUTED_ALL) {
                    entry.getValue().setVolume(findSoundVolume(entry.getKey()));
                }

            } catch (Exception ex) {
                Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public static void unmuteAllWav() {

        MUTED_WAV = false;

        for (Map.Entry<String, ConcurrentLinkedQueue<Clip>> entry : WAVS_RESOURCES.entrySet()) {

            ConcurrentLinkedQueue<Clip> list = entry.getValue();

            for (Clip c : list) {

                try {

                    if (c != null && c.isOpen()) {
                        FloatControl gainControl = (FloatControl) c.getControl(FloatControl.Type.MASTER_GAIN);
                        gainControl.setValue(Helpers.floatClean(20 * (float) Math.log10(findSoundVolume(entry.getKey())), 3));
                        ((BooleanControl) c.getControl(BooleanControl.Type.MUTE)).setValue(false);
                    }

                } catch (Exception ex) {
                    Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    public static void unmuteAll() {

        MUTED_ALL = false;

        unmuteAllMp3();

        unmuteAllLoopMp3();

        unmuteAllWav();

    }

    public static String getCurrentLoopMp3Playing() {

        for (Map.Entry<String, CoronaMP3FilePlayer> entry : MP3_LOOP.entrySet()) {

            if (entry.getValue().isPlaying()) {
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
