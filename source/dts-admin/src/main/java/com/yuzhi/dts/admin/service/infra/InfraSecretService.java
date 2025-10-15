package com.yuzhi.dts.admin.service.infra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.config.InfraSecurityProperties;
import com.yuzhi.dts.admin.domain.InfraDataSource;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
public class InfraSecretService {

    private static final Logger LOG = LoggerFactory.getLogger(InfraSecretService.class);
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH = 128;
    private static final String PLAINTEXT_KEY_VERSION = "PLAINTEXT";

    private final InfraSecurityProperties properties;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    private SecretKey secretKey;

    public InfraSecretService(InfraSecurityProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        String encoded = properties.getEncryptionKey();
        if (!org.springframework.util.StringUtils.hasText(encoded)) {
            LOG.warn("dts.admin.infra.encryption-key 未配置，数据源密钥将以明文存储");
            this.secretKey = null;
            return;
        }
        byte[] keyBytes = Base64.getDecoder().decode(encoded);
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    public void applySecrets(InfraDataSource entity, Map<String, Object> secrets) {
        if (CollectionUtils.isEmpty(secrets)) {
            entity.setSecureProps(null);
            entity.setSecureIv(null);
            entity.setSecureKeyVersion(null);
            return;
        }
        try {
            byte[] plain = objectMapper.writeValueAsString(secrets).getBytes(StandardCharsets.UTF_8);
            if (secretKey == null) {
                entity.setSecureProps(plain);
                entity.setSecureIv(null);
                entity.setSecureKeyVersion(PLAINTEXT_KEY_VERSION);
                return;
            }
            byte[] iv = randomIv();
            byte[] cipher = encrypt(plain, iv);
            entity.setSecureProps(cipher);
            entity.setSecureIv(iv);
            entity.setSecureKeyVersion(properties.getKeyVersion());
        } catch (JsonProcessingException | GeneralSecurityException e) {
            throw new IllegalStateException("保存数据源密钥失败", e);
        }
    }

    public Map<String, Object> readSecrets(InfraDataSource entity) {
        if (entity.getSecureProps() == null) {
            return Collections.emptyMap();
        }
        try {
            byte[] plain;
            if (PLAINTEXT_KEY_VERSION.equals(entity.getSecureKeyVersion()) || secretKey == null) {
                plain = entity.getSecureProps();
            } else {
                plain = decrypt(entity.getSecureProps(), entity.getSecureIv());
            }
            return objectMapper.readValue(new String(plain, StandardCharsets.UTF_8), Map.class);
        } catch (Exception e) {
            LOG.warn("解密数据源 {} 密钥失败: {}", entity.getId(), e.getMessage());
            return Collections.emptyMap();
        }
    }

    private byte[] encrypt(byte[] plain, byte[] iv) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH, iv));
        return cipher.doFinal(plain);
    }

    private byte[] decrypt(byte[] cipherText, byte[] iv) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH, iv));
        return cipher.doFinal(cipherText);
    }

    private byte[] randomIv() {
        byte[] iv = new byte[12];
        secureRandom.nextBytes(iv);
        return iv;
    }
}
