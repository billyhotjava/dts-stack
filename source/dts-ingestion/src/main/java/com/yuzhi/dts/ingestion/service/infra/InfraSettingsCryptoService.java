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
        byte[] keyBytes = Base64.getDecoder().decode(encoded);
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

    public byte[] encrypt(byte[] plain, byte[] iv) throws GeneralSecurityException {
        if (secretKey == null) {
            return plain;
        }
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH, iv));
        return cipher.doFinal(plain);
    }

    public byte[] decrypt(byte[] cipherText, byte[] iv) throws GeneralSecurityException {
        if (secretKey == null) {
            return cipherText;
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
