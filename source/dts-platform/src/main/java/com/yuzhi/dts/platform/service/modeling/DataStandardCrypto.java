package com.yuzhi.dts.platform.service.modeling;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class DataStandardCrypto {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final String AES = "AES";
    private static final int GCM_TAG_LENGTH = 128;

    private DataStandardCrypto() {}

    static SecretKey buildKey(String base64) {
        byte[] keyBytes = Base64.getDecoder().decode(base64.trim());
        if (keyBytes.length != 16 && keyBytes.length != 32) {
            throw new IllegalArgumentException("数据标准附件密钥长度必须为 128 位或 256 位");
        }
        return new SecretKeySpec(keyBytes, AES);
    }

    static byte[] randomIv() {
        byte[] iv = new byte[12];
        RANDOM.nextBytes(iv);
        return iv;
    }

    static byte[] encrypt(byte[] plain, SecretKey key, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return cipher.doFinal(plain);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("附件加密失败", e);
        }
    }

    static byte[] decrypt(byte[] cipherBytes, SecretKey key, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return cipher.doFinal(cipherBytes);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("附件解密失败", e);
        }
    }

    static String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("无法计算附件摘要", e);
        }
    }
}

