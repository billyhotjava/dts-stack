package com.yuzhi.dts.addax;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-GCM 加解密，与 dts-ingestion 的 FileUploadService 落盘格式字节级同款。
 * 文件布局（自描述，支持密钥轮转）：
 *   [verLen:1B][keyVersion:verLen B][IV:12B][AES-GCM ciphertext + 16B tag]
 * keyVersion 置于文件头使密文自带加密版本，runner 可在解密前校验/路由，避免多 key 误用。
 */
final class AddaxFileCrypto {

    static final String SEALED_JOB_PREFIX = "DTS_ADDAX_JOB_SEALED_V1:";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private AddaxFileCrypto() {}

    /** 解析出的密文三元组：加密版本、IV、密文（含 tag）。 */
    record EncryptedPayload(String keyVersion, byte[] iv, byte[] cipherText) {}

    static SecretKey keyFromBase64(String base64Key) throws GeneralSecurityException {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException ex) {
            throw new GeneralSecurityException("DTS_INFRA_ENCRYPTION_KEY is not valid Base64", ex);
        }
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new GeneralSecurityException(
                "DTS_INFRA_ENCRYPTION_KEY decoded length must be 16/24/32 bytes, got " + keyBytes.length);
        }
        return new SecretKeySpec(keyBytes, "AES");
    }

    /** 解析完整 .enc 文件内容为 [keyVersion, iv, cipherText]，不做解密。 */
    static EncryptedPayload parse(byte[] payload) throws GeneralSecurityException {
        if (payload.length < 1) {
            throw new GeneralSecurityException("encrypted payload is empty");
        }
        int verLen = payload[0] & 0xFF;
        int ivStart = 1 + verLen;
        if (payload.length <= ivStart + IV_LENGTH) {
            throw new GeneralSecurityException("encrypted payload too short");
        }
        String keyVersion = new String(payload, 1, verLen, StandardCharsets.UTF_8);
        byte[] iv = Arrays.copyOfRange(payload, ivStart, ivStart + IV_LENGTH);
        byte[] cipherText = Arrays.copyOfRange(payload, ivStart + IV_LENGTH, payload.length);
        return new EncryptedPayload(keyVersion, iv, cipherText);
    }

    static boolean isSealedJob(String value) {
        return value != null && value.trim().startsWith(SEALED_JOB_PREFIX);
    }

    static EncryptedPayload parseSealedJob(String token) throws GeneralSecurityException {
        if (!isSealedJob(token)) {
            throw new GeneralSecurityException("Addax job is not a supported sealed token");
        }
        byte[] payload;
        try {
            payload = Base64.getDecoder().decode(token.trim().substring(SEALED_JOB_PREFIX.length()));
        } catch (IllegalArgumentException ex) {
            throw new GeneralSecurityException("sealed Addax job payload is not valid Base64", ex);
        }
        try {
            return parse(payload);
        } finally {
            Arrays.fill(payload, (byte) 0);
        }
    }

    static byte[] decrypt(EncryptedPayload payload, SecretKey key) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, payload.iv()));
        return cipher.doFinal(payload.cipherText());
    }

    /** 产出 [verLen][keyVersion][IV][ciphertext+tag]，与 dts-ingestion 落盘布局一致（测试/对称校验用）。 */
    static byte[] encrypt(byte[] plain, SecretKey key, byte[] iv, String keyVersion) throws GeneralSecurityException {
        if (iv.length != IV_LENGTH) {
            throw new GeneralSecurityException("iv must be " + IV_LENGTH + " bytes");
        }
        byte[] verBytes = keyVersion.getBytes(StandardCharsets.UTF_8);
        if (verBytes.length > 255) {
            throw new GeneralSecurityException("keyVersion too long");
        }
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));
        byte[] cipherText = cipher.doFinal(plain);
        ByteArrayOutputStream out = new ByteArrayOutputStream(1 + verBytes.length + IV_LENGTH + cipherText.length);
        out.write(verBytes.length);
        out.writeBytes(verBytes);
        out.writeBytes(iv);
        out.writeBytes(cipherText);
        return out.toByteArray();
    }
}
