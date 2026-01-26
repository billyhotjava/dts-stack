package com.yuzhi.dts.admin.service.ops;

import com.yuzhi.dts.admin.domain.ChangeRequest;
import com.yuzhi.dts.admin.domain.SystemConfig;
import com.yuzhi.dts.admin.repository.SystemConfigRepository;
import com.yuzhi.dts.admin.security.SecurityUtils;
import com.yuzhi.dts.admin.service.ChangeRequestService;
import com.yuzhi.dts.admin.service.auditv2.AuditActionRequest;
import com.yuzhi.dts.admin.service.auditv2.AuditOperationKind;
import com.yuzhi.dts.admin.service.auditv2.AuditResultStatus;
import com.yuzhi.dts.admin.service.auditv2.AuditV2Service;
import com.yuzhi.dts.common.audit.ChangeSnapshot;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    /** 敏感配置键名模式（包含这些关键字的视为敏感） */
    private static final Set<String> SENSITIVE_KEY_PATTERNS = Set.of(
        "password", "secret", "token", "key", "credential", "auth"
    );

    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
        ".*(password|secret|token|key|credential|auth).*",
        Pattern.CASE_INSENSITIVE
    );

    private final SystemConfigRepository configRepository;
    private final ChangeRequestService changeRequestService;
    private final AuditV2Service auditV2Service;

    public OpsConfigService(
        SystemConfigRepository configRepository,
        ChangeRequestService changeRequestService,
        AuditV2Service auditV2Service
    ) {
        this.configRepository = configRepository;
        this.changeRequestService = changeRequestService;
        this.auditV2Service = auditV2Service;
    }

    /**
     * 获取所有配置，按分类分组
     */
    public Map<String, List<OpsConfigView>> getConfigsByCategory() {
        List<SystemConfig> configs = configRepository.findAll();
        Map<String, List<OpsConfigView>> result = new LinkedHashMap<>();

        // 定义分类顺序和中文名
        Map<String, String> categoryNames = Map.of(
            "FEATURE_TOGGLE", "功能开关",
            "SECURITY", "安全配置",
            "DATABASE", "数据库配置",
            "INTEGRATION", "集成配置",
            "SYSTEM", "系统配置"
        );

        // 按分类分组
        for (String category : List.of("FEATURE_TOGGLE", "SECURITY", "DATABASE", "INTEGRATION", "SYSTEM")) {
            List<OpsConfigView> items = configs.stream()
                .filter(c -> category.equals(c.getCategory() != null ? c.getCategory().name() : "SYSTEM"))
                .sorted(Comparator.comparingInt(SystemConfig::getSortOrder))
                .map(this::toView)
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
            .sorted(Comparator.comparingInt(SystemConfig::getSortOrder))
            .map(this::toView)
            .toList();
    }

    /**
     * 获取单个配置项
     */
    public Optional<OpsConfigView> getConfig(String key) {
        return configRepository.findByKey(key).map(this::toView);
    }

    /**
     * 更新配置（通过审批流程）
     */
    public ChangeRequest updateConfig(String key, String newValue, String reason, HttpServletRequest request) {
        String actor = SecurityUtils.getCurrentAuditableLogin();
        SystemConfig config = configRepository.findByKey(key)
            .orElseThrow(() -> new IllegalArgumentException("配置项不存在: " + key));

        if (!config.isEditable()) {
            throw new IllegalStateException("该配置项不可编辑: " + key);
        }

        // 构建变更前后数据（变更后值不脱敏，审批时需要看到实际值）
        Map<String, Object> before = toConfigMap(config);
        Map<String, Object> after = new LinkedHashMap<>(before);
        after.put("value", newValue);

        // 创建变更请求
        ChangeRequest cr = changeRequestService.draft(
            "CONFIG",
            "CONFIG_SET",
            key,
            after,
            before,
            reason
        );

        // 记录审计日志
        recordConfigUpdateAudit(actor, key, config, newValue, cr, request);

        return cr;
    }

    /**
     * 切换布尔类型配置（功能开关）
     * 支持所有布尔类型配置，不限于 FEATURE_TOGGLE 分类
     */
    public ChangeRequest toggleFeature(String key, boolean enabled, HttpServletRequest request) {
        String actor = SecurityUtils.getCurrentAuditableLogin();
        SystemConfig config = configRepository.findByKey(key)
            .orElseThrow(() -> new IllegalArgumentException("配置项不存在: " + key));

        // 只检查是否可编辑，不再限制分类
        if (!config.isEditable()) {
            throw new IllegalStateException("该配置项不可编辑: " + key);
        }

        String newValue = String.valueOf(enabled);
        Map<String, Object> before = toConfigMap(config);
        Map<String, Object> after = new LinkedHashMap<>(before);
        after.put("value", newValue);

        ChangeRequest cr = changeRequestService.draft(
            "CONFIG",
            enabled ? "TOGGLE_ENABLE" : "TOGGLE_DISABLE",
            key,
            after,
            before,
            enabled ? "启用功能: " + getDisplayName(config) : "禁用功能: " + getDisplayName(config)
        );

        recordToggleAudit(actor, key, config, enabled, cr, request);

        return cr;
    }

    // ============ 私有方法 ============

    /**
     * 转换为视图DTO（包含脱敏处理）
     */
    private OpsConfigView toView(SystemConfig config) {
        String maskedValue = config.isSensitive() || isSensitiveKey(config.getKey())
            ? maskValue(config.getValue())
            : config.getValue();

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
            config.getLastModifiedDate(),
            config.getLastModifiedBy()
        );
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
        return map;
    }

    /**
     * 记录配置更新审计
     */
    private void recordConfigUpdateAudit(
        String actor,
        String key,
        SystemConfig config,
        String newValue,
        ChangeRequest cr,
        HttpServletRequest request
    ) {
        try {
            String displayName = getDisplayName(config);
            String maskedOldValue = config.isSensitive() ? maskValue(config.getValue()) : config.getValue();
            String maskedNewValue = config.isSensitive() ? maskValue(newValue) : newValue;

            auditV2Service.record(
                AuditActionRequest.builder(actor, "OPS_CONFIG_UPDATE")
                    .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                    .target("system_config", String.valueOf(config.getId()), displayName)
                    .summary("修改运维配置：" + displayName + "，从 " + maskedOldValue + " 改为 " + maskedNewValue)
                    .result(AuditResultStatus.SUCCESS)
                    .changeRequestRef(String.valueOf(cr.getId()))
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
        boolean enabled,
        ChangeRequest cr,
        HttpServletRequest request
    ) {
        try {
            String displayName = getDisplayName(config);
            String action = enabled ? "启用" : "禁用";

            auditV2Service.record(
                AuditActionRequest.builder(actor, "OPS_TOGGLE_UPDATE")
                    .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                    .target("system_config", String.valueOf(config.getId()), displayName)
                    .summary(action + "功能开关：" + displayName)
                    .result(AuditResultStatus.SUCCESS)
                    .changeRequestRef(String.valueOf(cr.getId()))
                    .metadata("configKey", key)
                    .metadata("toggleAction", action)
                    .detail("before", Map.of("key", key, "enabled", !enabled))
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
