package com.yuzhi.dts.analytics.service;

import com.yuzhi.dts.analytics.domain.AnalyticsScreenDataset;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenDatasetRepository;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScreenDatasetService {

    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final AnalyticsScreenDatasetRepository repository;
    private final SecretKey secretKey;

    public ScreenDatasetService(
            AnalyticsScreenDatasetRepository repository,
            @Value("${screen.dataset.encryption-key:default-32-char-key-change-me!!}") String encryptionKey) {
        this.repository = repository;
        byte[] keyBytes = new byte[32];
        byte[] src = encryptionKey.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(src, 0, keyBytes, 0, Math.min(src.length, 32));
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    @Transactional
    public AnalyticsScreenDataset create(String name, String originalFileName,
            String fileType, String columnsMetaJson, String rowsDataJson,
            int rowCount, long fileSize, Long userId) {
        AnalyticsScreenDataset ds = new AnalyticsScreenDataset();
        ds.setUuid(UUID.randomUUID().toString());
        ds.setName(name);
        ds.setOriginalFileName(originalFileName);
        ds.setFileType(fileType);
        ds.setColumnsMeta(columnsMetaJson);
        ds.setEncryptedData(encrypt(rowsDataJson));
        ds.setRowCount(rowCount);
        ds.setFileSize(fileSize);
        ds.setCreatedBy(userId);
        return repository.save(ds);
    }

    public List<AnalyticsScreenDataset> listAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    public AnalyticsScreenDataset getByUuid(String uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new RuntimeException("Dataset not found: " + uuid));
    }

    public String decryptData(AnalyticsScreenDataset dataset) {
        return decrypt(dataset.getEncryptedData());
    }

    @Transactional
    public void deleteByUuid(String uuid) {
        repository.deleteByUuid(uuid);
    }

    private byte[] encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(GCM_IV_LENGTH + encrypted.length)
                    .put(iv).put(encrypted).array();
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    private String decrypt(byte[] data) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(data);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buf.get(iv);
            byte[] ciphertext = new byte[buf.remaining()];
            buf.get(ciphertext);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
