package com.yuzhi.dts.platform.service.services;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Shared hasher for service tokens (stored as SHA-256 hash; plain token only returned once at issuance).
 */
public final class SvcTokenHasher {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private SvcTokenHasher() {}

    public static String generatePlainToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String buildHint(String token) {
        if (token == null) {
            return null;
        }
        if (token.length() <= 6) {
            return token;
        }
        return "****" + token.substring(token.length() - 4);
    }

    public static String hashToken(String token) {
        if (token == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

