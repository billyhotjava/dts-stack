package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.config.DataStandardProperties;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.web.rest.dto.DataStandardSettingsDto;
import com.yuzhi.dts.platform.web.rest.dto.DataStandardHealthDto;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import jakarta.validation.Valid;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/modeling")
@Transactional
public class DataStandardSettingsResource {

    private static final String MODELING_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";

    private final DataStandardProperties properties;
    private final AuditService audit;

    public DataStandardSettingsResource(DataStandardProperties properties, AuditService audit) {
        this.properties = properties;
        this.audit = audit;
    }

    @GetMapping("/standards/settings")
    public ApiResponse<DataStandardSettingsDto> getSettings() {
        DataStandardSettingsDto dto = new DataStandardSettingsDto();
        dto.setMaxFileSize(properties.getAttachment().getMaxFileSize());
        dto.setAllowedExtensions(properties.getAttachment().getAllowedExtensions().stream().toList());
        audit.audit("READ", "modeling.standard.settings", "attachment" );
        return ApiResponses.ok(dto);
    }

    @PutMapping("/standards/settings")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<DataStandardSettingsDto> updateSettings(@Valid @RequestBody DataStandardSettingsDto dto) {
        if (dto.getMaxFileSize() > 512L * 1024 * 1024) {
            throw new IllegalArgumentException("文件大小上限不得超过 512MB");
        }
        Set<String> extensions = dto
            .getAllowedExtensions()
            .stream()
            .map(value -> value.trim().toLowerCase(Locale.ROOT))
            .filter(StringUtils::hasText)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (extensions.isEmpty()) {
            throw new IllegalArgumentException("请至少保留一种允许的附件类型");
        }
        properties.getAttachment().setMaxFileSize(dto.getMaxFileSize());
        properties.getAttachment().setAllowedExtensions(extensions);
        DataStandardSettingsDto response = new DataStandardSettingsDto();
        response.setMaxFileSize(properties.getAttachment().getMaxFileSize());
        response.setAllowedExtensions(properties.getAttachment().getAllowedExtensions().stream().toList());
        audit.audit("UPDATE", "modeling.standard.settings", "attachment" );
        return ApiResponses.ok(response);
    }

    /**
     * Health endpoint for administrators. Validates encryption key format and storage settings so
     * the UI can surface friendly configuration errors before users attempt uploads.
     */
    @GetMapping("/standards/health")
    public ApiResponse<DataStandardHealthDto> health() {
        DataStandardHealthDto dto = new DataStandardHealthDto();
        String key = properties.getEncryptionKey();
        boolean keyConfigured = StringUtils.hasText(key);
        boolean keyValid = false;
        int keyBytes = 0;
        String msg = null;
        if (!keyConfigured) {
            msg = "未配置数据标准附件加密密钥 (DATA_STANDARD_ENCRYPTION_KEY)";
        } else {
            try {
                byte[] decoded = Base64.getDecoder().decode(key.trim());
                keyBytes = decoded.length;
                keyValid = (keyBytes == 16 || keyBytes == 32);
                if (!keyValid) {
                    msg = "密钥长度必须为 16 或 32 字节 (Base64)";
                }
            } catch (IllegalArgumentException ex) {
                msg = "密钥不是有效的 Base64 字符串";
            }
        }

        String strategy = String.valueOf(properties.getAttachment().getStorageStrategy());
        String storageDir = properties.getAttachment().getStorageDir();
        Boolean writable = null;
        if ("filesystem".equalsIgnoreCase(strategy)) {
            try {
                if (StringUtils.hasText(storageDir)) {
                    Path p = Path.of(storageDir);
                    // best-effort: do not create; only check existence and writability of parent
                    Path check = Files.exists(p) ? p : p.getParent();
                    writable = (check != null) && Files.isWritable(check);
                    if (writable == null || !writable) {
                        msg = (msg == null ? "上传目录不可写: " + storageDir : msg + "；上传目录不可写: " + storageDir);
                    }
                } else {
                    msg = (msg == null ? "未配置上传目录" : msg + "；未配置上传目录");
                }
            } catch (Exception ex) {
                writable = Boolean.FALSE;
                String reason = ex.getMessage() == null ? "未知错误" : ex.getMessage();
                msg = (msg == null ? "检查上传目录失败: " + reason : msg + "；检查上传目录失败: " + reason);
            }
        }

        boolean ok = keyConfigured && keyValid && ("filesystem".equalsIgnoreCase(strategy) ? Boolean.TRUE.equals(writable) : true);
        dto.setOk(ok);
        dto.setMessage(ok ? "OK" : (msg == null ? "配置不正确" : msg));
        dto.setKeyConfigured(keyConfigured);
        dto.setKeyValid(keyValid);
        dto.setKeyBytes(keyBytes);
        dto.setStorageStrategy(strategy);
        dto.setStorageDir(storageDir);
        dto.setStorageDirWritable(writable);
        return ApiResponses.ok(dto);
    }
}
