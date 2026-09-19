/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.core.audio.VoiceWavContract;

/** Legacy package adapter to the frontend-neutral voice wire contract. */
final class VoiceWavValidator {

    static boolean isValid(byte[] wav) {
        return VoiceWavContract.isValid(wav);
    }

    static String validationError(byte[] wav) {
        return VoiceWavContract.validationError(wav);
    }

    private VoiceWavValidator() {
    }
}
