package com.yuzhi.dts.admin.service.auditv2;

import com.yuzhi.dts.admin.domain.AuditOperationMapping;
import com.yuzhi.dts.admin.repository.AuditOperationMappingRepository;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * 规则引擎：维护 audit_operation_mapping 配置，供筛选项和分组展示使用。
 */
@Component
public class OperationMappingEngine {

    private static final Logger log = LoggerFactory.getLogger(OperationMappingEngine.class);

    public static final class RuleSummary {
        private final Long id;
        private final String moduleName;
        private final String operationGroup;
        private final String groupDisplayName;
        private final AuditOperationType operationType;
        private final String descriptionTemplate;
        private final String sourceTableTemplate;
        private final String sourceSystem;

        RuleSummary(AuditOperationMapping mapping) {
            this.id = mapping.getId();
            this.moduleName = safeTrim(mapping.getModuleName());
            this.operationGroup = effectiveGroupKey(mapping);
            this.groupDisplayName = effectiveGroupLabel(mapping);
            AuditOperationType type = AuditOperationType.from(mapping.getOperationType());
            this.operationType = type == AuditOperationType.UNKNOWN ? null : type;
            this.descriptionTemplate = safeTrim(mapping.getDescriptionTemplate());
            this.sourceTableTemplate = safeTrim(mapping.getSourceTableTemplate());
            this.sourceSystem = safeTrim(mapping.getSourceSystem());
        }

        public Long getId() {
            return id;
        }

        public String getModuleName() {
            return moduleName;
        }

        public String getOperationGroup() {
            return operationGroup;
        }

        public String getGroupDisplayName() {
            return groupDisplayName;
        }

        public String getOperationType() {
            return operationType == null ? null : operationType.getCode();
        }

        public String getOperationTypeLabel() {
            return operationType == null ? null : operationType.getDisplayName();
        }

        public String getDescriptionTemplate() {
            return descriptionTemplate;
        }

        public String getSourceTableTemplate() {
            return sourceTableTemplate;
        }

        public String getSourceSystem() {
            return sourceSystem;
        }
    }

    private static final class CompiledRule {
        final AuditOperationMapping raw;
        final PathPattern pattern;
        final Pattern statusPattern;

        CompiledRule(AuditOperationMapping raw, PathPattern pattern, Pattern statusPattern) {
            this.raw = raw;
            this.pattern = pattern;
            this.statusPattern = statusPattern;
        }
    }

    private final AuditOperationMappingRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final AuditResourceDictionaryService resourceDictionary;
    private final PathPatternParser parser = new PathPatternParser();
    private final Map<String, PathPattern> patternCache = new ConcurrentHashMap<>();
    private volatile List<CompiledRule> rules = List.of();

    public OperationMappingEngine(
        AuditOperationMappingRepository repository,
        JdbcTemplate jdbcTemplate,
        AuditResourceDictionaryService resourceDictionary
    ) {
        this.repository = Objects.requireNonNull(repository, "repository required");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate required");
        this.resourceDictionary = Objects.requireNonNull(resourceDictionary, "resourceDictionary required");
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        if (mappingTableExists()) {
            reload();
        } else {
            log.debug("audit_operation_mapping table not ready at startup; waiting for Liquibase");
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onLiquibaseReady() {
        if (!mappingTableExists()) {
            log.debug("audit_operation_mapping table still absent when Liquibase event triggered; will retry later");
            return;
        }
        reload();
    }

    @Scheduled(fixedDelay = 30000L)
    public void reload() {
        if (!mappingTableExists()) {
            log.debug("audit_operation_mapping table not available yet; skipping reload");
            return;
        }
        try {
            List<AuditOperationMapping> enabled = repository.findAllByEnabledTrueOrderByOrderValueAscIdAsc();
            List<CompiledRule> compiled = new ArrayList<>(enabled.size());
            for (AuditOperationMapping mapping : enabled) {
                String patternKey = normalizePattern(mapping.getUrlPattern());
                PathPattern parsed = patternCache.computeIfAbsent(patternKey, parser::parse);
                Pattern statusPattern = compileStatusPattern(mapping);
                compiled.add(new CompiledRule(mapping, parsed, statusPattern));
            }
            this.rules = compiled;
            log.info("Loaded {} audit operation mappings", compiled.size());
        } catch (DataAccessException ex) {
            log.debug("audit_operation_mapping unavailable during reload ({}); will retry", ex.getMessage());
        } catch (Exception ex) {
            log.warn("Failed to reload audit operation mappings", ex);
        }
    }

    public List<RuleSummary> describeRules() {
        List<CompiledRule> snapshot = rules;
        List<RuleSummary> summaries = new ArrayList<>(snapshot.size());
        for (CompiledRule compiled : snapshot) {
            summaries.add(new RuleSummary(compiled.raw));
        }
        return summaries;
    }

    private boolean mappingTableExists() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = current_schema() AND LOWER(table_name) = ?",
                Integer.class,
                "audit_operation_mapping"
            );
            return count != null && count > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private String normalizePattern(String pattern) {
        if (StringUtils.isBlank(pattern)) {
            return "/";
        }
        String normalized = Normalizer
            .normalize(pattern, Normalizer.Form.NFKC)
            .replaceAll("//+", "/")
            .trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized;
    }

    private Pattern compileStatusPattern(AuditOperationMapping mapping) {
        String statusRegex = mapping.getStatusCodeRegex();
        if (StringUtils.isBlank(statusRegex)) {
            return null;
        }
        try {
            return Pattern.compile(statusRegex, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException ex) {
            log.warn("Invalid status regex for audit mapping id {}: {}", mapping.getId(), ex.getMessage());
            return null;
        }
    }

    private static String effectiveGroupKey(AuditOperationMapping mapping) {
        if (StringUtils.isNotBlank(mapping.getOperationGroup())) {
            return mapping.getOperationGroup();
        }
        if (StringUtils.isNotBlank(mapping.getModuleName())) {
            return slugify(mapping.getModuleName());
        }
        return "general";
    }

    private static String effectiveGroupLabel(AuditOperationMapping mapping) {
        if (StringUtils.isNotBlank(mapping.getGroupDisplayName())) {
            return mapping.getGroupDisplayName();
        }
        if (StringUtils.isNotBlank(mapping.getModuleName())) {
            return mapping.getModuleName();
        }
        return "通用";
    }

    private static String slugify(String value) {
        if (StringUtils.isBlank(value)) {
            return "general";
        }
        return value
            .trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-|-$)", "");
    }

    private static String safeTrim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
