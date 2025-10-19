package com.yuzhi.dts.admin.security.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SessionKeyGenerator {

    private static final Logger log = LoggerFactory.getLogger(SessionKeyGenerator.class);

    private SessionKeyGenerator() {}

    public static String fromToken(String tokenId, String tokenValue) {
        String hashed = hash(tokenValue);
        if (hashed != null && !hashed.isBlank()) {
            return hashed;
        }
        return tokenId;
    }

    private static String hash(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(tokenValue.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException ex) {
            log.debug("Failed to obtain SHA-256 digest for session key: {}", ex.getMessage());
            return Integer.toHexString(tokenValue.hashCode());
        }
    }
}
