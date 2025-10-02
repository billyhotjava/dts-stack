package com.yuzhi.dts.admin.service.audit;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class AuditCrypto {

    private static final String AES_ALGORITHM = "AES";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final String HMAC_ALGO = "HmacSHA256";
    private static final int GCM_TAG_LENGTH = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private AuditCrypto() {}

    static SecretKey buildKey(String base64) {
        byte[] keyBytes = Base64.getDecoder().decode(base64.trim());
        if (keyBytes.length != 16 && keyBytes.length != 32) {
            throw new IllegalArgumentException("Audit encryption key must be 128-bit or 256-bit");
        }
        return new SecretKeySpec(keyBytes, AES_ALGORITHM);
    }

    static SecretKey buildMacKey(String base64) {
        byte[] keyBytes = Base64.getDecoder().decode(base64.trim());
        return new SecretKeySpec(keyBytes, HMAC_ALGO);
    }

    static byte[] randomIv() {
        byte[] iv = new byte[12];
        RANDOM.nextBytes(iv);
        return iv;
    }

    static byte[] encrypt(byte[] plaintext, SecretKey key, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return cipher.doFinal(plaintext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt audit payload", e);
        }
    }

    static byte[] decrypt(byte[] cipherText, SecretKey key, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return cipher.doFinal(cipherText);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to decrypt audit payload", e);
        }
    }

    static String hmac(byte[] data, SecretKey key) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(key);
            return Base64.getEncoder().encodeToString(mac.doFinal(data));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to compute audit HMAC", e);
        }
    }

    static String chain(String previousSignature, String payloadHmac, SecretKey key) {
        byte[] previousBytes = previousSignature == null ? new byte[0] : previousSignature.getBytes(StandardCharsets.UTF_8);
        byte[] payloadBytes = payloadHmac.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(previousBytes.length + payloadBytes.length);
        buffer.put(previousBytes);
        buffer.put(payloadBytes);
        return hmac(buffer.array(), key);
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to compute SHA-256", e);
        }
    }
}
