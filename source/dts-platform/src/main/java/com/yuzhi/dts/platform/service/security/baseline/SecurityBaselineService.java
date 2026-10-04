package com.yuzhi.dts.platform.service.security.baseline;

import com.yuzhi.dts.platform.domain.security.SecurityBaselineRemediation;
import com.yuzhi.dts.platform.repository.security.SecurityBaselineRemediationRepository;
import com.yuzhi.dts.platform.service.security.baseline.dto.SecurityBaselineCheckDto;
import com.yuzhi.dts.platform.service.security.baseline.request.SecurityBaselineRemediationUpdateRequest;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SecurityBaselineService {

    private static final List<BaselineDefinition> DEFINITIONS = List.of(
        new BaselineDefinition(
            "SEC_BASELINE_ACCOUNT_PASSWORD",
            "账号与口令策略",
            "账号口令",
            "HIGH",
            "MANUAL",
            "检查统一认证（Keycloak/PKI）账号口令策略是否符合涉密环境要求（复杂度、有效期、失败锁定、口令重置流程）。",
            "满足甲方安全基线要求，并形成配置截图/制度文件留痕。"
        ),
        new BaselineDefinition(
            "SEC_BASELINE_SESSION_TIMEOUT",
            "会话与超时策略",
            "会话管理",
            "MEDIUM",
            "MANUAL",
            "检查平台会话超时、刷新令牌策略、单点登出等配置；确认超时后访问受限。",
            "满足甲方要求（例如 30 分钟无操作失效），并留存配置说明。"
        ),
        new BaselineDefinition(
            "SEC_BASELINE_API_AUTHZ",
            "接口鉴权与权限控制",
            "接口安全",
            "HIGH",
            "MANUAL",
            "检查所有 API 是否要求鉴权；是否存在匿名可访问的敏感接口；是否启用最小权限原则。",
            "关键接口需鉴权、权限校验到位，并形成自查记录。"
        ),
        new BaselineDefinition(
            "SEC_BASELINE_AUDIT_LOG",
            "审计日志与留痕",
            "审计留痕",
            "HIGH",
            "AUTO",
            "检查审计日志是否开启；关键操作是否有中文动作码；是否支持检索导出。",
            "审计必须开启，关键操作可追溯。"
        ),
        new BaselineDefinition(
            "SEC_BASELINE_BACKUP",
            "备份与恢复策略",
            "备份恢复",
            "HIGH",
            "MANUAL",
            "检查数据库/配置/审计日志是否有定时备份策略；是否进行恢复验证与演练留痕。",
            "具备可验证的备份与恢复流程，并形成演练记录。"
        ),
        new BaselineDefinition(
            "SEC_BASELINE_TLS",
            "传输加密（HTTPS/TLS）",
            "网络安全",
            "HIGH",
            "MANUAL",
            "检查访问链路（浏览器→网关→服务）是否全程启用 HTTPS/TLS；证书管理是否规范。",
            "对外访问必须 HTTPS，证书定期更新并留痕。"
        )
    );

    private final SecurityBaselineRemediationRepository remediationRepository;

    public SecurityBaselineService(SecurityBaselineRemediationRepository remediationRepository) {
        this.remediationRepository = remediationRepository;
    }

    @Transactional(readOnly = true)
    public List<SecurityBaselineCheckDto> listChecks() {
        Map<String, SecurityBaselineRemediation> remediationMap = remediationRepository
            .findAll()
            .stream()
            .collect(java.util.stream.Collectors.toMap(SecurityBaselineRemediation::getCheckKey, r -> r, (a, b) -> a));

        return DEFINITIONS
            .stream()
            .map(def -> toDto(def, remediationMap.get(def.checkKey())))
            .toList();
    }

    public SecurityBaselineCheckDto updateRemediation(String checkKey, SecurityBaselineRemediationUpdateRequest request) {
        String normalizedKey = StringUtils.trimToNull(checkKey);
        if (normalizedKey == null) {
            throw new IllegalArgumentException("checkKey 不能为空");
        }
        BaselineDefinition def = DEFINITIONS
            .stream()
            .filter(d -> Objects.equals(d.checkKey(), normalizedKey))
            .findFirst()
            .orElseThrow(() -> new EntityNotFoundException("检查项不存在"));

        String status = normalizeStatus(request != null ? request.getStatus() : null);
        String notes = request != null ? StringUtils.trimToNull(request.getNotes()) : null;

        SecurityBaselineRemediation entity = remediationRepository.findById(normalizedKey).orElseGet(() -> {
            SecurityBaselineRemediation created = new SecurityBaselineRemediation();
            created.setCheckKey(normalizedKey);
            return created;
        });
        entity.setStatus(status);
        entity.setNotes(notes);
        remediationRepository.save(entity);

        return toDto(def, entity);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> exportReport() {
        List<SecurityBaselineCheckDto> checks = listChecks();
        String now = DateTimeFormatter.ISO_INSTANT.format(Instant.now());

        StringBuilder md = new StringBuilder();
        md.append("# 安全基线检查报告\n\n");
        md.append("生成时间：").append(now).append("\n\n");
        md.append("|检查项|分类|严重性|类型|状态|备注|\n");
        md.append("|---|---|---|---|---|---|\n");
        for (SecurityBaselineCheckDto c : checks) {
            md
                .append("|")
                .append(escapeCell(c.getTitle()))
                .append("|")
                .append(escapeCell(c.getCategory()))
                .append("|")
                .append(escapeCell(c.getSeverity()))
                .append("|")
                .append(escapeCell(c.getType()))
                .append("|")
                .append(escapeCell(defaultIfBlank(c.getRemediationStatus(), "NOT_STARTED")))
                .append("|")
                .append(escapeCell(Optional.ofNullable(c.getRemediationNotes()).orElse("")))
                .append("|\n");
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generatedAt", now);
        report.put("checks", checks);
        report.put("markdown", md.toString());
        return report;
    }

    private SecurityBaselineCheckDto toDto(BaselineDefinition def, SecurityBaselineRemediation remediation) {
        SecurityBaselineCheckDto dto = new SecurityBaselineCheckDto();
        dto.setCheckKey(def.checkKey());
        dto.setTitle(def.title());
        dto.setCategory(def.category());
        dto.setSeverity(def.severity());
        dto.setType(def.type());
        dto.setDescription(def.description());
        dto.setExpected(def.expected());
        if (remediation != null) {
            dto.setRemediationStatus(remediation.getStatus());
            dto.setRemediationNotes(remediation.getNotes());
            dto.setLastUpdatedAt(remediation.getLastModifiedDate());
            dto.setLastUpdatedBy(remediation.getLastModifiedBy());
        } else {
            dto.setRemediationStatus("NOT_STARTED");
        }
        return dto;
    }

    private String normalizeStatus(String raw) {
        String normalized = StringUtils.trimToNull(raw);
        if (normalized == null) {
            return "NOT_STARTED";
        }
        normalized = normalized.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "NOT_STARTED", "IN_PROGRESS", "DONE", "WAIVED" -> normalized;
            default -> throw new IllegalArgumentException("不支持的状态: " + normalized);
        };
    }

    private String escapeCell(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("|", "\\|").replace("\n", "<br/>").trim();
    }

    private String defaultIfBlank(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return raw;
    }

    private record BaselineDefinition(
        String checkKey,
        String title,
        String category,
        String severity,
        String type,
        String description,
        String expected
    ) {}
}

