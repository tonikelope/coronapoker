package com.tonikelope.coronapoker.core;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Security;

/** Process-wide cryptographically secure random generator. */
public final class SecureRandomService implements ApplicationService {

    private SecureRandom generator;
    private boolean fallback;

    @Override
    public synchronized void start() {
        if (generator != null) {
            return;
        }

        Security.setProperty("securerandom.drbg.config", "Hash_DRBG,SHA-512,256,reseed_only");
        try {
            generator = SecureRandom.getInstance("DRBG");
        } catch (NoSuchAlgorithmException unavailable) {
            generator = new SecureRandom();
            fallback = true;
        }
    }

    public synchronized SecureRandom generator() {
        if (generator == null) {
            throw new IllegalStateException("Secure random service has not started");
        }
        return generator;
    }

    public synchronized boolean usesFallback() {
        if (generator == null) {
            throw new IllegalStateException("Secure random service has not started");
        }
        return fallback;
    }

    @Override
    public void close() {
        // SecureRandom owns no closeable process resource.
    }
}
