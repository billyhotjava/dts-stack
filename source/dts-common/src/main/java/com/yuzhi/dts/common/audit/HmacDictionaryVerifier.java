package com.yuzhi.dts.common.audit;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verifies HMAC-SHA256 signatures attached to audit dictionary files
 * (audit-button-registry.json, legacy-action-mappings.json).
 * <p>
 * In a governance-triad deployment the HMAC key is held by the authorization
 * administrator (authadmin); the system administrator (sysadmin) only has read
 * access to the dictionary file and the sidecar {@code .sig}. Tampering with
 * the dictionary alone produces a signature mismatch and the admin service
 * fails to start, so sysadmin cannot silently rewrite audit semantics.
 * <p>
 * Multiple keys may be configured to support graceful rotation: a sidecar that
 * verifies under any of the active or legacy keys is accepted. After the
 * rotation rollout, drain the legacy key list.
 */
public final class HmacDictionaryVerifier {

    private static final String ALGORITHM = "HmacSHA256";

    private HmacDictionaryVerifier() {}

    public enum Decision {
        VERIFIED,
        SIGNATURE_MISSING,
        SIGNATURE_INVALID,
        NO_KEY_CONFIGURED,
    }

    /**
     * Verifies the supplied content against the supplied signature using any
     * of the active or legacy keys. Constant-time comparison prevents timing
     * leaks across the configured key list.
     *
     * @param content     raw bytes of the dictionary file (e.g. JSON content)
     * @param signatureHex hex-encoded HMAC tag read from the sidecar; may be null/blank
     * @param activeKeyHex hex-encoded primary key; may be null/blank if not yet rotated in
     * @param legacyKeysHex zero or more hex-encoded legacy keys for the rotation window
     * @return decision describing why the verification passed or failed
     */
    public static Decision verify(
        byte[] content,
        String signatureHex,
        String activeKeyHex,
        Collection<String> legacyKeysHex
    ) {
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        if (signatureHex == null || signatureHex.isBlank()) {
            return Decision.SIGNATURE_MISSING;
        }
        byte[] presentedTag;
        try {
            presentedTag = decodeHex(signatureHex.trim());
        } catch (IllegalArgumentException ex) {
            return Decision.SIGNATURE_INVALID;
        }
        boolean anyKeyTried = false;
        boolean matched = false;
        for (String keyHex : keyOrder(activeKeyHex, legacyKeysHex)) {
            byte[] key;
            try {
                key = decodeHex(keyHex);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            if (key.length == 0) {
                continue;
            }
            anyKeyTried = true;
            byte[] expectedTag;
            try {
                expectedTag = computeTag(key, content);
            } catch (GeneralSecurityException ex) {
                continue;
            }
            // Loop through all keys regardless of early match to keep total time roughly constant.
            if (MessageDigest.isEqual(expectedTag, presentedTag)) {
                matched = true;
            }
        }
        if (!anyKeyTried) {
            return Decision.NO_KEY_CONFIGURED;
        }
        return matched ? Decision.VERIFIED : Decision.SIGNATURE_INVALID;
    }

    /**
     * Computes a fresh HMAC-SHA256 hex tag for the given content. Used by signing
     * tooling, not by the runtime verification path.
     */
    public static String sign(byte[] content, String keyHex) {
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        byte[] key = decodeHex(keyHex);
        if (key.length == 0) {
            throw new IllegalArgumentException("key must not be empty");
        }
        try {
            return encodeHex(computeTag(key, content));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC algorithm unavailable: " + ALGORITHM, ex);
        }
    }

    private static List<String> keyOrder(String active, Collection<String> legacy) {
        List<String> out = new java.util.ArrayList<>();
        if (active != null && !active.isBlank()) {
            out.add(active.trim());
        }
        if (legacy != null) {
            for (String key : legacy) {
                if (key != null && !key.isBlank()) {
                    out.add(key.trim());
                }
            }
        }
        return out;
    }

    private static byte[] computeTag(byte[] keyBytes, byte[] content) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(ALGORITHM);
        mac.init(new SecretKeySpec(keyBytes, ALGORITHM));
        return mac.doFinal(content);
    }

    private static byte[] decodeHex(String hex) {
        String stripped = hex.replaceAll("\\s+", "");
        int len = stripped.length();
        if ((len & 1) != 0) {
            throw new IllegalArgumentException("hex string has odd length");
        }
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(stripped.charAt(i), 16);
            int lo = Character.digit(stripped.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("non-hex character in signature/key");
            }
            out[i / 2] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static String encodeHex(byte[] bytes) {
        char[] hex = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xff;
            hex[i * 2] = HEX_CHARS[v >>> 4];
            hex[i * 2 + 1] = HEX_CHARS[v & 0x0f];
        }
        return new String(hex);
    }

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    /** Convenience for callers that have raw signature bytes (unit tests). */
    public static String toHex(byte[] bytes) {
        return encodeHex(bytes);
    }

    /** Convenience for callers that need to ASCII-encode the dictionary content for signing. */
    public static byte[] utf8(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }
}
