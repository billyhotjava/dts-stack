package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.config.DataStandardProperties;
import com.yuzhi.dts.platform.domain.modeling.DataStandard;
import com.yuzhi.dts.platform.domain.modeling.DataStandardAttachment;
import com.yuzhi.dts.platform.repository.modeling.DataStandardAttachmentRepository;
import com.yuzhi.dts.platform.repository.modeling.DataStandardRepository;
import com.yuzhi.dts.platform.service.modeling.dto.DataStandardAttachmentContent;
import com.yuzhi.dts.platform.service.modeling.dto.DataStandardAttachmentDto;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class DataStandardAttachmentService {

    private final DataStandardRepository standardRepository;
    private final DataStandardAttachmentRepository attachmentRepository;
    private final DataStandardProperties properties;

    public DataStandardAttachmentService(
        DataStandardRepository standardRepository,
        DataStandardAttachmentRepository attachmentRepository,
        DataStandardProperties properties
    ) {
        this.standardRepository = standardRepository;
        this.attachmentRepository = attachmentRepository;
        this.properties = properties;
    }

    public DataStandardAttachmentDto upload(UUID standardId, MultipartFile file, String version) {
        DataStandard standard = loadStandard(standardId);
        String extension = validateFile(file);
        SecretKey secretKey = resolveKey();
        byte[] bytes = toBytes(file);
        String detectedContentType = AttachmentSignatureValidator.validate(extension, bytes);
        byte[] iv = DataStandardCrypto.randomIv();
        byte[] cipher = DataStandardCrypto.encrypt(bytes, secretKey, iv);

        String strategy = String.valueOf(properties.getAttachment().getStorageStrategy());
        DataStandardAttachment attachment = new DataStandardAttachment();
        attachment.setStandard(standard);
        attachment.setVersion(StringUtils.hasText(version) ? version : standard.getCurrentVersion());
        attachment.setFileName(file.getOriginalFilename());
        attachment.setContentType(detectedContentType);
        attachment.setFileSize(file.getSize());
        attachment.setSha256(DataStandardCrypto.sha256(bytes));
        attachment.setKeyVersion(properties.getKeyVersion());
        attachment.setIv(iv);

        if ("filesystem".equalsIgnoreCase(strategy)) {
            // Persist metadata first to obtain attachment id
            attachment.setCipherBlob(null);
            attachment = attachmentRepository.save(attachment);
            writeEncryptedToFilesystem(standard.getId(), attachment.getId(), cipher);
            return DataStandardMapper.toDto(attachment);
        } else {
            // Default to database storage
            attachment.setCipherBlob(cipher);
            attachmentRepository.save(attachment);
            return DataStandardMapper.toDto(attachment);
        }
    }

    public List<DataStandardAttachmentDto> list(UUID standardId) {
        DataStandard standard = loadStandard(standardId);
        return attachmentRepository
            .findByStandardOrderByCreatedDateDesc(standard)
            .stream()
            .map(DataStandardMapper::toDto)
            .toList();
    }

    public DataStandardAttachmentContent download(UUID standardId, UUID attachmentId) {
        DataStandard standard = loadStandard(standardId);
        DataStandardAttachment attachment = attachmentRepository
            .findByIdAndStandard(attachmentId, standard)
            .orElseThrow(() -> new EntityNotFoundException("附件不存在"));
        SecretKey secretKey = resolveKey();
        byte[] cipher;
        String strategy = String.valueOf(properties.getAttachment().getStorageStrategy());
        if ("filesystem".equalsIgnoreCase(strategy)) {
            cipher = readEncryptedFromFilesystem(standardId, attachmentId);
        } else {
            cipher = attachment.getCipherBlob();
        }
        byte[] data = DataStandardCrypto.decrypt(cipher, secretKey, attachment.getIv());
        return new DataStandardAttachmentContent(data, attachment.getFileName(), attachment.getContentType());
    }

    public void delete(UUID standardId, UUID attachmentId) {
        DataStandard standard = loadStandard(standardId);
        DataStandardAttachment attachment = attachmentRepository
            .findByIdAndStandard(attachmentId, standard)
            .orElseThrow(() -> new EntityNotFoundException("附件不存在"));
        attachmentRepository.delete(attachment);
        // Best-effort delete filesystem blob if strategy is filesystem
        try {
            String strategy = String.valueOf(properties.getAttachment().getStorageStrategy());
            if ("filesystem".equalsIgnoreCase(strategy)) {
                Path p = buildFsPath(standardId, attachmentId);
                Files.deleteIfExists(p);
            }
        } catch (Exception ignore) {}
    }

    private String validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择需要上传的文件");
        }
        long maxSize = properties.getAttachment().getMaxFileSize();
        if (file.getSize() > maxSize) {
            long sizeMb = Math.max(1, maxSize / (1024 * 1024));
            throw new IllegalArgumentException("文件大小超过限制（最大 " + sizeMb + "MB）");
        }
        String filename = file.getOriginalFilename();
        if (!StringUtils.hasText(filename)) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        String ext = extractExtension(filename);
        Set<String> allowed = properties
            .getAttachment()
            .getAllowedExtensions()
            .stream()
            .map(value -> value.toLowerCase(Locale.ROOT))
            .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        if (!allowed.isEmpty() && !allowed.contains(ext)) {
            String available = String.join(", ", allowed);
            throw new IllegalArgumentException("不支持的文件类型: " + ext + "，请上传 " + available + " 文件");
        }
        return ext;
    }

    private byte[] toBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("读取上传文件失败", e);
        }
    }

    private SecretKey resolveKey() {
        String encodedKey = properties.getEncryptionKey();
        if (!StringUtils.hasText(encodedKey)) {
            throw new IllegalStateException("未配置数据标准附件加密密钥");
        }
        return DataStandardCrypto.buildKey(encodedKey);
    }

    private void writeEncryptedToFilesystem(UUID standardId, UUID attachmentId, byte[] cipher) {
        try {
            Path path = buildFsPath(standardId, attachmentId);
            Files.createDirectories(path.getParent());
            Files.write(path, cipher, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("写入附件文件失败", e);
        }
    }

    private byte[] readEncryptedFromFilesystem(UUID standardId, UUID attachmentId) {
        try {
            Path path = buildFsPath(standardId, attachmentId);
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new IllegalStateException("读取附件文件失败", e);
        }
    }

    private Path buildFsPath(UUID standardId, UUID attachmentId) {
        String base = properties.getAttachment().getStorageDir();
        if (!StringUtils.hasText(base)) {
            base = "/opt/dts/upload";
        }
        // Use a deterministic layout so we don't need to store file path in DB
        String dir = base.replaceAll("/+$", "") + "/data-standard/" + standardId;
        return Path.of(dir, attachmentId.toString() + ".enc");
    }

    private DataStandard loadStandard(UUID id) {
        return standardRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("数据标准不存在"));
    }

    private String extractExtension(String filename) {
        String name = filename.toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot == -1) {
            return "";
        }
        return name.substring(dot + 1);
    }
}
