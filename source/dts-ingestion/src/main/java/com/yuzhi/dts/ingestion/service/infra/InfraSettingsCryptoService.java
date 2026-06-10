package com.yuzhi.dts.ingestion.service.infra;

import com.yuzhi.dts.ingestion.config.InfraSecurityProperties;
import jakarta.annotation.PostConstruct;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class InfraSettingsCryptoService {

    private static final Logger LOG = LoggerFactory.getLogger(InfraSettingsCryptoService.class);
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH = 128;
    private static final String PLAINTEXT_KEY_VERSION = "PLAINTEXT";

    private final InfraSecurityProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    private SecretKey secretKey;

    public InfraSettingsCryptoService(InfraSecurityProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        String encoded = properties.getEncryptionKey();
        if (!StringUtils.hasText(encoded)) {
            LOG.warn("dts.platform.infra.encryption-key is not configured; service settings will be persisted as plaintext");
            this.secretKey = null;
            return;
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("dts.platform.infra.encryption-key 不是合法的 Base64 编码", ex);
        }
        // M3(Sprint-37): 密钥长度非法时启动即 fail-fast，避免延迟到首次加密才暴露 InvalidKeyException
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalStateException(
                "dts.platform.infra.encryption-key 解码后长度必须为 16/24/32 字节(AES-128/192/256)，当前 " + keyBytes.length + " 字节"
            );
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    public boolean isEncryptionReady() {
        return secretKey != null;
    }

    public String plaintextKeyVersion() {
        return PLAINTEXT_KEY_VERSION;
    }

    public String currentKeyVersion() {
        return properties.getKeyVersion();
    }

    /**
     * 通用加密：密钥未配置时返回明文（InfraSettings 的「明文告警回退」语义，调用方须自行用
     * {@link #isEncryptionReady()} 守护）。涉密文件加密请改用 {@link #encryptStrict}，禁止回退。
     */
    public byte[] encrypt(byte[] plain, byte[] iv) throws GeneralSecurityException {
        if (secretKey == null) {
            return plain;
        }
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH, iv));
        return cipher.doFinal(plain);
    }

    /**
     * 通用解密：密钥未配置时返回密文原文（与 {@link #encrypt} 的回退对称）。涉密文件解密请
     * 改用 {@link #decryptStrict}，禁止回退。
     */
    public byte[] decrypt(byte[] cipherText, byte[] iv) throws GeneralSecurityException {
        if (secretKey == null) {
            return cipherText;
        }
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH, iv));
        return cipher.doFinal(cipherText);
    }

    /**
     * 强制加密：密钥未配置时直接抛异常，绝不明文回退（涉密文件落盘专用，Sprint-37 M1）。
     * 区别于 {@link #encrypt} 的告警回退，杜绝因漏检查 isEncryptionReady() 导致的静默明文落盘。
     */
    public byte[] encryptStrict(byte[] plain, byte[] iv) throws GeneralSecurityException {
        if (secretKey == null) {
            throw new GeneralSecurityException("加密密钥未配置，禁止涉密文件明文回退");
        }
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH, iv));
        return cipher.doFinal(plain);
    }

    /** 强制解密：密钥未配置时直接抛异常，绝不返回密文原文（涉密文件专用，Sprint-37 M1）。 */
    public byte[] decryptStrict(byte[] cipherText, byte[] iv) throws GeneralSecurityException {
        if (secretKey == null) {
            throw new GeneralSecurityException("加密密钥未配置，无法解密涉密文件");
        }
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH, iv));
        return cipher.doFinal(cipherText);
    }

    public byte[] randomIv() {
        byte[] iv = new byte[12];
        secureRandom.nextBytes(iv);
        return iv;
    }
}
