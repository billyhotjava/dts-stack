package com.yuzhi.dts.addax;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-GCM 加解密，与 dts-ingestion 的 InfraSettingsCryptoService 字节级同款。
 * 文件布局：[IV:12B][AES-GCM ciphertext + 16B tag]（IV 置文件头，自包含）。
 * 仅用于运行期把上传密文解密到容器内存（tmpfs）供 Addax 读取。
 */
final class AddaxFileCrypto {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private AddaxFileCrypto() {}

    static SecretKey keyFromBase64(String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        return new SecretKeySpec(keyBytes, "AES");
    }

    /** payload = 完整 .enc 文件内容（含 IV 头）。 */
    static byte[] decrypt(byte[] payload, SecretKey key) throws GeneralSecurityException {
        if (payload.length <= IV_LENGTH) {
            throw new GeneralSecurityException("encrypted payload too short");
        }
        byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH);
        byte[] cipherText = Arrays.copyOfRange(payload, IV_LENGTH, payload.length);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));
        return cipher.doFinal(cipherText);
    }

    /** 产出 [IV:12B][ciphertext+tag]，与 dts-ingestion 落盘布局一致（测试与对称校验用）。 */
    static byte[] encrypt(byte[] plain, SecretKey key, byte[] iv) throws GeneralSecurityException {
        if (iv.length != IV_LENGTH) {
            throw new GeneralSecurityException("iv must be " + IV_LENGTH + " bytes");
        }
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));
        byte[] cipherText = cipher.doFinal(plain);
        byte[] out = new byte[IV_LENGTH + cipherText.length];
        System.arraycopy(iv, 0, out, 0, IV_LENGTH);
        System.arraycopy(cipherText, 0, out, IV_LENGTH, cipherText.length);
        return out;
    }
}
