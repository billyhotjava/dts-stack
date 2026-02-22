package com.yuzhi.dts.admin.service.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.domain.SystemConfig;
import com.yuzhi.dts.admin.repository.SystemConfigRepository;
import com.yuzhi.dts.admin.security.SecurityUtils;
import com.yuzhi.dts.admin.service.auditv2.AuditActionRequest;
import com.yuzhi.dts.admin.service.auditv2.AuditResultStatus;
import com.yuzhi.dts.admin.service.auditv2.AuditV2Service;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 运维配置服务
 * - 配置CRUD操作
 * - 敏感信息脱敏处理
 * - 配置分类查询
 * - 审计日志记录
 */
@Service
@Transactional
public class OpsConfigService {

    private static final Logger log = LoggerFactory.getLogger(OpsConfigService.class);

    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
        ".*(password|secret|token|key|credential|auth).*",
        Pattern.CASE_INSENSITIVE
    );
    private static final String MASKED_SECRET = "******";

    private final SystemConfigRepository configRepository;
    private final AuditV2Service auditV2Service;
    private final ObjectMapper objectMapper;
    private final OpsConfigGroupingService groupingService;

    public OpsConfigService(
        SystemConfigRepository configRepository,
        AuditV2Service auditV2Service,
        ObjectMapper objectMapper,
        OpsConfigGroupingService groupingService
    ) {
        this.configRepository = configRepository;
        this.auditV2Service = auditV2Service;
        this.objectMapper = objectMapper;
        this.groupingService = groupingService;
    }

    /**
     * 获取所有配置，按分类分组
     */
    public Map<String, List<OpsConfigView>> getConfigsByCategory() {
        List<SystemConfig> configs = configRepository.findAll();
        Map<String, List<OpsConfigView>> result = new LinkedHashMap<>();

        // 按分类分组
        for (String category : List.of("FEATURE_TOGGLE", "SECURITY", "DATABASE", "INTEGRATION", "SYSTEM")) {
            List<OpsConfigView> items = configs.stream()
                .filter(c -> category.equals(c.getCategory() != null ? c.getCategory().name() : "SYSTEM"))
                .map(this::toView)
                .sorted(Comparator.comparingInt(OpsConfigView::groupOrder).thenComparingInt(OpsConfigView::sortOrder).thenComparing(OpsConfigView::key))
                .toList();
            if (!items.isEmpty()) {
                result.put(category, items);
            }
        }

        return result;
    }

    /**
     * 获取功能开关列表
     */
    public List<OpsConfigView> getFeatureToggles() {
        return configRepository.findAll().stream()
            .filter(c -> c.getCategory() == SystemConfig.Category.FEATURE_TOGGLE)
            .map(this::toView)
            .sorted(Comparator.comparingInt(OpsConfigView::groupOrder).thenComparingInt(OpsConfigView::sortOrder).thenComparing(OpsConfigView::key))
            .toList();
    }

    /**
     * 获取单个配置项
     */
    public Optional<OpsConfigView> getConfig(String key) {
        return configRepository.findByKey(key).map(this::toView);
    }

    /**
     * 更新配置（直接生效，无需审批）
     * @return 更新后的配置视图
     */
    public OpsConfigView updateConfig(String key, String newValue, HttpServletRequest request) {
        String actor = SecurityUtils.getCurrentAuditableLogin();
        SystemConfig config = configRepository.findByKey(key)
            .orElseThrow(() -> new IllegalArgumentException("配置项不存在: " + key));

        if (!config.isEditable()) {
            throw new IllegalStateException("该配置项不可编辑: " + key);
        }
        ensureMutable(config, key);
        validateValue(config, newValue);

        String oldValue = config.getValue();
        if ((config.isSensitive() || isSensitiveKey(config.getKey())) && MASKED_SECRET.equals(newValue)) {
            return toView(config);
        }
        config.setValue(newValue);
        configRepository.save(config);

        // 记录审计日志
        recordConfigUpdateAudit(actor, key, config, oldValue, newValue, request);

        log.info("Config updated: {} = {} (was: {}) by {}", key, newValue, oldValue, actor);
        return toView(config);
    }

    /**
     * 切换布尔类型配置（直接生效，无需审批）
     * @return 更新后的配置视图
     */
    public OpsConfigView toggleFeature(String key, boolean enabled, HttpServletRequest request) {
        String actor = SecurityUtils.getCurrentAuditableLogin();
        SystemConfig config = configRepository.findByKey(key)
            .orElseThrow(() -> new IllegalArgumentException("配置项不存在: " + key));

        if (!config.isEditable()) {
            throw new IllegalStateException("该配置项不可编辑: " + key);
        }
        ensureMutable(config, key);

        String oldValue = config.getValue();
        String newValue = String.valueOf(enabled);
        config.setValue(newValue);
        configRepository.save(config);

        // 记录审计日志
        recordToggleAudit(actor, key, config, oldValue, enabled, request);

        log.info("Feature toggled: {} = {} (was: {}) by {}", key, enabled, oldValue, actor);
        return toView(config);
    }

    // ============ 私有方法 ============

    /**
     * 转换为视图DTO（包含脱敏处理）
     */
    private OpsConfigView toView(SystemConfig config) {
        String maskedValue = config.isSensitive() || isSensitiveKey(config.getKey())
            ? maskValue(config.getValue())
            : config.getValue();
        OpsConfigGroupingService.GroupMeta groupMeta = groupingService.resolve(config);

        return new OpsConfigView(
            config.getId(),
            config.getKey(),
            maskedValue,
            config.getDescription(),
            config.getCategory() != null ? config.getCategory().name() : "SYSTEM",
            config.isSensitive() || isSensitiveKey(config.getKey()),
            config.getDataType() != null ? config.getDataType().name() : "STRING",
            config.isEditable(),
            config.getSortOrder(),
            config.getDisplayName(),
            config.getConfigScope() != null ? config.getConfigScope().name() : SystemConfig.ConfigScope.RUNTIME.name(),
            config.isRestartRequired(),
            config.getValidationRule(),
            config.getOwner(),
            groupMeta.groupKey(),
            groupMeta.groupLabel(),
            groupMeta.groupOrder(),
            config.getLastModifiedDate(),
            config.getLastModifiedBy()
        );
    }

    private void ensureMutable(SystemConfig config, String key) {
        if (config.getConfigScope() == SystemConfig.ConfigScope.BOOTSTRAP) {
            throw new IllegalStateException("该配置项属于启动期参数，请在 .env/compose 中维护: " + key);
        }
    }

    private void validateValue(SystemConfig config, String newValue) {
        if (!StringUtils.hasText(newValue)) {
            return;
        }
        switch (config.getDataType() != null ? config.getDataType() : SystemConfig.DataType.STRING) {
            case BOOLEAN -> {
                String normalized = newValue.trim().toLowerCase();
                if (!("true".equals(normalized) || "false".equals(normalized) || "1".equals(normalized) || "0".equals(normalized))) {
                    throw new IllegalArgumentException("布尔配置仅支持 true/false/1/0");
                }
            }
            case INTEGER -> {
                try {
                    Integer.parseInt(newValue.trim());
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("整数配置仅支持有效数字");
                }
            }
            case JSON -> {
                try {
                    objectMapper.readTree(newValue);
                } catch (Exception ex) {
                    throw new IllegalArgumentException("JSON 配置格式不合法");
                }
            }
            case STRING -> {
                // no-op
            }
        }
        validateByRule(config.getValidationRule(), newValue);
    }

    private void validateByRule(String rule, String value) {
        if (!StringUtils.hasText(rule) || value == null) {
            return;
        }
        String normalizedRule = rule.trim();
        if (normalizedRule.startsWith("regex:")) {
            String regex = normalizedRule.substring("regex:".length()).trim();
            if (StringUtils.hasText(regex) && !Pattern.compile(regex).matcher(value).matches()) {
                throw new IllegalArgumentException("配置值不满足校验规则");
            }
            return;
        }
        if (normalizedRule.startsWith("enum:")) {
            String body = normalizedRule.substring("enum:".length());
            List<String> allowed = java.util.Arrays.stream(body.split(",")).map(String::trim).filter(StringUtils::hasText).toList();
            if (!allowed.isEmpty() && !allowed.contains(value)) {
                throw new IllegalArgumentException("配置值不在允许范围: " + String.join(", ", allowed));
            }
            return;
        }
        if (normalizedRule.startsWith("range:")) {
            String body = normalizedRule.substring("range:".length());
            String[] parts = body.split(",");
            if (parts.length == 2) {
                try {
                    double min = Double.parseDouble(parts[0].trim());
                    double max = Double.parseDouble(parts[1].trim());
                    double current = Double.parseDouble(value.trim());
                    if (current < min || current > max) {
                        throw new IllegalArgumentException("配置值超出范围 [" + min + ", " + max + "]");
                    }
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("范围校验规则配置无效");
                }
            }
        }
    }

    /**
     * 判断配置键是否为敏感类型
     */
    private boolean isSensitiveKey(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        return SENSITIVE_PATTERN.matcher(key).matches();
    }

    /**
     * 脱敏处理：保留首尾字符，中间用***替代
     */
    private String maskValue(String value) {
        if (value == null || value.isEmpty()) {
            return "***";
        }
        if (value.length() <= 4) {
            return "***";
        }
        return value.substring(0, 1) + "***" + value.substring(value.length() - 1);
    }

    /**
     * 获取配置显示名
     */
    private String getDisplayName(SystemConfig config) {
        if (StringUtils.hasText(config.getDisplayName())) {
            return config.getDisplayName();
        }
        return config.getKey();
    }

    /**
     * 转换为Map（用于变更单和审计）
     */
    private Map<String, Object> toConfigMap(SystemConfig config) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", config.getId());
        map.put("key", config.getKey());
        map.put("value", config.getValue());
        map.put("description", config.getDescription());
        map.put("category", config.getCategory() != null ? config.getCategory().name() : null);
        map.put("displayName", config.getDisplayName());
        map.put("scope", config.getConfigScope() != null ? config.getConfigScope().name() : SystemConfig.ConfigScope.RUNTIME.name());
        map.put("restartRequired", config.isRestartRequired());
        map.put("validationRule", config.getValidationRule());
        map.put("owner", config.getOwner());
        return map;
    }

    /**
     * 记录配置更新审计
     */
    private void recordConfigUpdateAudit(
        String actor,
        String key,
        SystemConfig config,
        String oldValue,
        String newValue,
        HttpServletRequest request
    ) {
        try {
            String displayName = getDisplayName(config);
            boolean sensitive = config.isSensitive() || isSensitiveKey(config.getKey());
            String maskedOldValue = sensitive ? maskValue(oldValue) : oldValue;
            String maskedNewValue = sensitive ? maskValue(newValue) : newValue;

            auditV2Service.record(
                AuditActionRequest.builder(actor, "OPS_CONFIG_UPDATE")
                    .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                    .target("system_config", String.valueOf(config.getId()), displayName)
                    .summary("修改运维配置：" + displayName + "，从 " + maskedOldValue + " 改为 " + maskedNewValue)
                    .result(AuditResultStatus.SUCCESS)
                    .metadata("configKey", key)
                    .metadata("category", config.getCategory() != null ? config.getCategory().name() : "SYSTEM")
                    .detail("before", Map.of("key", key, "value", maskedOldValue))
                    .detail("after", Map.of("key", key, "value", maskedNewValue))
                    .client(resolveClientIp(request), request != null ? request.getHeader("User-Agent") : null)
                    .request(request != null ? request.getRequestURI() : "/api/admin/ops/configs/" + key, "PUT")
                    .build()
            );
        } catch (Exception ex) {
            log.warn("Failed to record ops config update audit: {}", ex.getMessage());
        }
    }

    /**
     * 记录功能开关审计
     */
    private void recordToggleAudit(
        String actor,
        String key,
        SystemConfig config,
        String oldValue,
        boolean enabled,
        HttpServletRequest request
    ) {
        try {
            String displayName = getDisplayName(config);
            String action = enabled ? "启用" : "禁用";
            boolean wasEnabled = "true".equalsIgnoreCase(oldValue);

            auditV2Service.record(
                AuditActionRequest.builder(actor, "OPS_TOGGLE_UPDATE")
                    .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                    .target("system_config", String.valueOf(config.getId()), displayName)
                    .summary(action + "功能开关：" + displayName)
                    .result(AuditResultStatus.SUCCESS)
                    .metadata("configKey", key)
                    .metadata("toggleAction", action)
                    .detail("before", Map.of("key", key, "enabled", wasEnabled))
                    .detail("after", Map.of("key", key, "enabled", enabled))
                    .client(resolveClientIp(request), request != null ? request.getHeader("User-Agent") : null)
                    .request(request != null ? request.getRequestURI() : "/api/admin/ops/feature-toggles/" + key, "PUT")
                    .build()
            );
        } catch (Exception ex) {
            log.warn("Failed to record ops toggle audit: {}", ex.getMessage());
        }
    }

    /**
     * 记录查看审计
     */
    public void recordViewAudit(String actor, String module, int count, HttpServletRequest request) {
        try {
            String buttonCode = "feature-toggles".equals(module) ? "OPS_TOGGLE_VIEW" : "OPS_CONFIG_VIEW";
            String summary = "feature-toggles".equals(module)
                ? "查看功能开关（共 " + count + " 项）"
                : "查看运维配置";

            auditV2Service.record(
                AuditActionRequest.builder(actor, buttonCode)
                    .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                    .summary(summary)
                    .result(AuditResultStatus.SUCCESS)
                    .metadata("count", count)
                    .client(resolveClientIp(request), request != null ? request.getHeader("User-Agent") : null)
                    .request(request != null ? request.getRequestURI() : "/api/admin/ops/" + module, "GET")
                    .allowEmptyTargets()
                    .build()
            );
        } catch (Exception ex) {
            log.warn("Failed to record ops view audit: {}", ex.getMessage());
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
