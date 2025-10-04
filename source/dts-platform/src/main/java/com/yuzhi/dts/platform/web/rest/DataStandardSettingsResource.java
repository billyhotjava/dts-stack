package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.config.DataStandardProperties;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.web.rest.dto.DataStandardSettingsDto;
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
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
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
}
