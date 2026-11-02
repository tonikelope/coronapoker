package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.table.TableVisualEvent;

/** Pure decisions for renderer-owned UI feedback sounds. */
final class GdxSoundFeedback {

    static final String PAUSE = "misc/pause.wav";
    static final String LAST_HAND_ON = "misc/last_hand_on.wav";
    static final String LAST_HAND_OFF = "misc/last_hand_off.wav";
    static final String YOUR_TURN = "misc/yourturn.wav";
    static final String VOLUME_CHANGE = "misc/volume_change.wav";
    static final String ZOOM_IN = "misc/zoom_in.wav";
    static final String ZOOM_OUT = "misc/zoom_out.wav";
    static final String FUNNY_RAISE = "misc/raise.wav";
    static final String FUNNY_NO_REBUY = "misc/norebuy.wav";
    static final String FUNNY_SPECIAL_CARD_KEY =
            "sonido_chorra_special_card";

    private GdxSoundFeedback() {
    }

    static boolean pauseStarted(boolean before, boolean after) {
        return !before && after;
    }

    static String lastHandTransition(boolean before, boolean after) {
        if (before == after) return "";
        return after ? LAST_HAND_ON : LAST_HAND_OFF;
    }

    static boolean localTurnStarted(TableVisualEvent.TurnTimer timer,
            String localNickname) {
        return timer.phase() == TableVisualEvent.TurnTimer.Phase.START
                && !localNickname.isBlank()
                && localNickname.equals(timer.nickname());
    }

    static boolean funnyRaise(TableVisualEvent.PlayerAction.ActionKind kind,
            boolean sillySounds) {
        return sillySounds && (kind
                == TableVisualEvent.PlayerAction.ActionKind.RAISE
                || kind == TableVisualEvent.PlayerAction.ActionKind.RERAISE);
    }

    static String avatarZoomTransition(boolean before, boolean after) {
        if (before == after) return "";
        return after ? ZOOM_IN : ZOOM_OUT;
    }

    static boolean funnyResource(String resource) {
        if (resource == null) return false;
        return resource.startsWith("joke/")
                || resource.equals(FUNNY_RAISE)
                || resource.equals(FUNNY_NO_REBUY)
                || resource.equals("misc/showyourcards.wav")
                || resource.equals("misc/indivisible.wav")
                || resource.equals("misc/lastcard.wav")
                || resource.equals("misc/badbeat.wav")
                || resource.equals("misc/youarelucky.wav");
    }

    /** Optional WAV paired with an ALL-IN cinematic, never the normal gunshot. */
    static boolean cinematicCompanionResource(String resource) {
        if (resource == null) return false;
        String normalized = resource.replace('\\', '/');
        return normalized.startsWith("allin/")
                && normalized.toLowerCase(java.util.Locale.ROOT)
                        .endsWith(".wav");
    }

    /** Individual preference governing one renderer-facing joke resource. */
    static String funnyPreferenceKey(String resource) {
        if (resource == null) return null;
        String normalized = resource.replace('\\', '/');
        if (normalized.contains("/allin/")) return "sonido_chorra_allin";
        if (normalized.contains("/fold/")) return "sonido_chorra_fold";
        if (normalized.contains("/showdown/")) {
            return "sonido_chorra_showdown";
        }
        if (normalized.contains("/winner/")) return "sonido_chorra_winner";
        if (normalized.contains("/loser/")) return "sonido_chorra_loser";
        return switch (normalized) {
            case FUNNY_RAISE -> "sonido_chorra_raise";
            case FUNNY_NO_REBUY -> "sonido_chorra_norebuy";
            case "misc/indivisible.wav" -> "sonido_chorra_indivisible";
            case "misc/lastcard.wav" -> "sonido_chorra_lastcard";
            case "misc/badbeat.wav" -> "sonido_chorra_badbeat";
            case "misc/youarelucky.wav" -> "sonido_chorra_lucky";
            case "misc/showyourcards.wav" -> "sonido_iwtsth";
            default -> null;
        };
    }
}
