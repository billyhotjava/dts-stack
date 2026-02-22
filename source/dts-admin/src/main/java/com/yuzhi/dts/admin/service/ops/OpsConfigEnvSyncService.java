package com.yuzhi.dts.admin.service.ops;

import com.yuzhi.dts.admin.domain.SystemConfig;
import com.yuzhi.dts.admin.repository.SystemConfigRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Seed env-backed runtime configs into system_config so Admin UI can manage them.
 * This service inserts missing keys only and never overrides existing values.
 */
@Component
public class OpsConfigEnvSyncService {

    private static final Logger log = LoggerFactory.getLogger(OpsConfigEnvSyncService.class);
    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(".*(password|secret|token|key|credential|auth|pwd).*", Pattern.CASE_INSENSITIVE);
    private static final Set<String> BOOLEAN_LITERALS = Set.of("true", "false");

    private final ConfigurableEnvironment environment;
    private final SystemConfigRepository configRepository;

    public OpsConfigEnvSyncService(ConfigurableEnvironment environment, SystemConfigRepository configRepository) {
        this.environment = environment;
        this.configRepository = configRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void syncFromEnvironment() {
        Set<String> propertyNames = collectPropertyNames();
        List<String> managedKeys = propertyNames
            .stream()
            .filter(this::isManagedEnvKey)
            .sorted(Comparator.naturalOrder())
            .toList();

        if (managedKeys.isEmpty()) {
            log.info("[ops-config] no managed env keys found for sync");
            return;
        }

        List<SystemConfig> existing = configRepository.findAll();
        Map<String, SystemConfig> existingByKey = new HashMap<>();
        Map<SystemConfig.Category, Integer> maxSort = new EnumMap<>(SystemConfig.Category.class);
        for (SystemConfig config : existing) {
            existingByKey.put(config.getKey(), config);
            SystemConfig.Category category = config.getCategory() != null ? config.getCategory() : SystemConfig.Category.SYSTEM;
            maxSort.merge(category, config.getSortOrder(), Math::max);
        }

        List<SystemConfig> toInsert = new ArrayList<>();
        for (String key : managedKeys) {
            if (existingByKey.containsKey(key)) {
                continue;
            }
            String value = environment.getProperty(key);
            SystemConfig.Category category = resolveCategory(key);
            SystemConfig config = new SystemConfig();
            config.setKey(key);
            config.setValue(value);
            config.setDescription("由环境变量同步: " + key);
            config.setCategory(category);
            config.setSensitive(isSensitiveKey(key));
            config.setDataType(resolveDataType(value));
            config.setEditable(true);
            config.setSortOrder(nextSort(maxSort, category));
            config.setDisplayName(key);
            config.setConfigScope(resolveScope(key));
            config.setRestartRequired(isRestartRequired(key));
            config.setValidationRule(resolveValidationRule(key));
            config.setOwner(resolveOwner(key));
            toInsert.add(config);
        }

        if (!toInsert.isEmpty()) {
            configRepository.saveAll(toInsert);
        }
        log.info("[ops-config] env sync done: managed={} inserted={} existing={}", managedKeys.size(), toInsert.size(), existingByKey.size());
    }

    private Set<String> collectPropertyNames() {
        Set<String> names = new LinkedHashSet<>();
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (source instanceof EnumerablePropertySource<?> enumerable) {
                for (String name : enumerable.getPropertyNames()) {
                    if (StringUtils.hasText(name)) {
                        names.add(name.trim());
                    }
                }
            }
        }
        return names;
    }

    private boolean isManagedEnvKey(String key) {
        return key.startsWith("DTS_AIRFLOW_") ||
        key.startsWith("DTS_OPENMETADATA_") ||
        key.startsWith("DTS_PLATFORM_OPENMETADATA_") ||
        key.startsWith("DTS_MDM_GATEWAY_") ||
        key.startsWith("DTS_SECURITY_IP_ALLOWLIST_") ||
        key.equals("ADMIN_ALLOWED_IPS") ||
        key.equals("ADMIN_BACKUP_IPS") ||
        key.equals("ADMIN_WHITELIST_CIDRS") ||
        key.equals("ADMIN_WEBAPP_PASSWORD_LOGIN_ENABLED") ||
        key.equals("PLATFORM_WEBAPP_PASSWORD_LOGIN_ENABLED") ||
        key.equals("VITE_ENABLE_SQL_WORKBENCH") ||
        key.startsWith("OAUTH2_") ||
        key.equals("OIDC_ISSUER_URI") ||
        key.startsWith("DTS_PKI_") ||
        key.startsWith("ANALYTICS_OIDC_") ||
        key.equals("ANALYTICS_ENCRYPTION_SECRET");
    }

    private SystemConfig.Category resolveCategory(String key) {
        if (
            key.startsWith("DTS_AIRFLOW_") ||
            key.startsWith("DTS_OPENMETADATA_") ||
            key.startsWith("DTS_PLATFORM_OPENMETADATA_") ||
            key.startsWith("DTS_MDM_GATEWAY_") ||
            key.startsWith("ANALYTICS_OIDC_") ||
            key.startsWith("OAUTH2_") ||
            key.equals("OIDC_ISSUER_URI")
        ) {
            return SystemConfig.Category.INTEGRATION;
        }
        if (
            key.startsWith("DTS_PKI_") ||
            key.startsWith("DTS_SECURITY_IP_ALLOWLIST_") ||
            key.equals("ADMIN_ALLOWED_IPS") ||
            key.equals("ADMIN_BACKUP_IPS") ||
            key.equals("ADMIN_WHITELIST_CIDRS") ||
            key.equals("ADMIN_WEBAPP_PASSWORD_LOGIN_ENABLED") ||
            key.equals("PLATFORM_WEBAPP_PASSWORD_LOGIN_ENABLED") ||
            key.equals("ANALYTICS_ENCRYPTION_SECRET")
        ) {
            return SystemConfig.Category.SECURITY;
        }
        if (key.equals("VITE_ENABLE_SQL_WORKBENCH")) {
            return SystemConfig.Category.FEATURE_TOGGLE;
        }
        return SystemConfig.Category.SYSTEM;
    }

    private SystemConfig.ConfigScope resolveScope(String key) {
        if (key.startsWith("DTS_MDM_GATEWAY_")) {
            // MDM gateway wiring is bootstrap-level in current compose deployment.
            return SystemConfig.ConfigScope.BOOTSTRAP;
        }
        if (
            key.startsWith("OAUTH2_") ||
            key.equals("OIDC_ISSUER_URI") ||
            key.startsWith("DTS_PKI_") ||
            key.startsWith("ANALYTICS_OIDC_") ||
            key.equals("ANALYTICS_ENCRYPTION_SECRET")
        ) {
            return SystemConfig.ConfigScope.RUNTIME_RESTART;
        }
        return SystemConfig.ConfigScope.RUNTIME;
    }

    private boolean isRestartRequired(String key) {
        SystemConfig.ConfigScope scope = resolveScope(key);
        return scope == SystemConfig.ConfigScope.RUNTIME_RESTART || scope == SystemConfig.ConfigScope.BOOTSTRAP;
    }

    private String resolveOwner(String key) {
        if (key.startsWith("DTS_PKI_") || key.startsWith("OAUTH2_") || key.equals("OIDC_ISSUER_URI")) {
            return "security";
        }
        if (key.startsWith("DTS_AIRFLOW_") || key.startsWith("DTS_OPENMETADATA_") || key.startsWith("DTS_PLATFORM_OPENMETADATA_")) {
            return "integration";
        }
        if (key.startsWith("DTS_MDM_GATEWAY_")) {
            return "admin";
        }
        return "platform";
    }

    private boolean isSensitiveKey(String key) {
        return StringUtils.hasText(key) && SENSITIVE_PATTERN.matcher(key).matches();
    }

    private SystemConfig.DataType resolveDataType(String value) {
        if (!StringUtils.hasText(value)) {
            return SystemConfig.DataType.STRING;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (BOOLEAN_LITERALS.contains(normalized)) {
            return SystemConfig.DataType.BOOLEAN;
        }
        if (normalized.matches("^-?\\d+$")) {
            return SystemConfig.DataType.INTEGER;
        }
        if ((normalized.startsWith("{") && normalized.endsWith("}")) || (normalized.startsWith("[") && normalized.endsWith("]"))) {
            return SystemConfig.DataType.JSON;
        }
        return SystemConfig.DataType.STRING;
    }

    private String resolveValidationRule(String key) {
        if ("DTS_PKI_MODE".equals(key)) {
            return "enum:disabled,gateway,api";
        }
        if ("DTS_PKI_API_TIMEOUT".equals(key)) {
            return "range:100,60000";
        }
        if (key.endsWith("_PORT")) {
            return "range:1,65535";
        }
        return null;
    }

    private int nextSort(Map<SystemConfig.Category, Integer> maxSort, SystemConfig.Category category) {
        int current = maxSort.getOrDefault(category, 0);
        int next = current + 10;
        maxSort.put(category, next);
        return next;
    }
}
