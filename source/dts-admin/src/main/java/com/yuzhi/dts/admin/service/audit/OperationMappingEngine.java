package com.yuzhi.dts.admin.service.audit;

import com.yuzhi.dts.admin.domain.AuditEvent;
import com.yuzhi.dts.admin.domain.AuditOperationMapping;
import com.yuzhi.dts.admin.repository.AuditOperationMappingRepository;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
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
 * 运行时按规则解析审计操作信息，生成监管友好的文案。
 * 当前阶段主要用于列表筛选项和前端展示元数据。
 */
@Component
public class OperationMappingEngine {

    private static final Logger log = LoggerFactory.getLogger(OperationMappingEngine.class);

    public static final class ResolvedOperation {
        public Long ruleId;
        public String moduleName;
        public String operationGroup;
        public String groupDisplayName;
        public AuditOperationType operationType;
        public String operationTypeLabel;
        public String description;
        public String sourceTable;
        public boolean ruleMatched;
        public Map<String, Object> attributes;
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

    private final AuditOperationMappingRepository repo;
    private final JdbcTemplate jdbcTemplate;
    private final AuditResourceDictionaryService resourceDictionary;
    private final PathPatternParser parser = new PathPatternParser();
    private final Map<String, PathPattern> patternCache = new ConcurrentHashMap<>();
    private volatile List<CompiledRule> rules = List.of();

    public OperationMappingEngine(
        AuditOperationMappingRepository repo,
        JdbcTemplate jdbcTemplate,
        AuditResourceDictionaryService resourceDictionary
    ) {
        this.repo = Objects.requireNonNull(repo, "repo required");
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
            List<AuditOperationMapping> enabled = repo.findAllByEnabledTrueOrderByOrderValueAscIdAsc();
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

    public Optional<ResolvedOperation> resolve(AuditEvent event) {
        if (event == null || rules.isEmpty()) {
            return Optional.empty();
        }
        // 当前版本仅提供兜底信息，后续如需精确匹配可扩展 requestPath 参数
        return Optional.empty();
    }

    public Optional<ResolvedOperation> resolveWithFallback(AuditEvent event) {
        if (event == null) {
            return Optional.empty();
        }
        ResolvedOperation resolved = new ResolvedOperation();
        resolved.ruleMatched = false;
        resolved.ruleId = null;
        resolved.moduleName = StringUtils.defaultIfBlank(event.getModuleLabel(), event.getModule());
        String groupLabel = StringUtils.defaultIfBlank(resolved.moduleName, "通用");
        resolved.operationGroup = StringUtils.defaultIfBlank(event.getOperationGroup(), slugify(groupLabel));
        resolved.groupDisplayName = groupLabel;
        AuditOperationType type = AuditOperationType.from(event.getOperationType());
        resolved.operationType = type == AuditOperationType.UNKNOWN ? null : type;
        resolved.operationTypeLabel = resolved.operationType != null ? resolved.operationType.getDisplayName() : null;
        resolved.description = StringUtils.defaultIfBlank(event.getSummary(), event.getOperationName());
        resolved.sourceTable = event.getTargetTable();
        resolved.attributes = Map.of();
        return Optional.of(resolved);
    }

    public List<RuleSummary> describeRules() {
        if (!mappingTableExists()) {
            return List.of();
        }
        try {
            return repo
                .findAllByEnabledTrueOrderByOrderValueAscIdAsc()
                .stream()
                .map(RuleSummary::new)
                .toList();
        } catch (DataAccessException ex) {
            log.debug("audit_operation_mapping unavailable when describing rules: {}", ex.getMessage());
            return List.of();
        }
    }

    private boolean mappingTableExists() {
        try {
            jdbcTemplate.queryForObject("select count(*) from audit_operation_mapping", Integer.class);
            return true;
        } catch (DataAccessException ex) {
            return false;
        }
    }

    private String normalizePattern(String pattern) {
        String normalized = StringUtils.defaultIfBlank(pattern, "/**").trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized;
    }

    private Pattern compileStatusPattern(AuditOperationMapping mapping) {
        String regex = safeTrim(mapping.getStatusCodeRegex());
        if (StringUtils.isBlank(regex)) {
            return null;
        }
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException ex) {
            log.warn("Invalid status_code_regex '{}' on audit rule {}", regex, mapping.getId());
            return null;
        }
    }

    private static String effectiveGroupKey(AuditOperationMapping mapping) {
        String explicit = safeTrim(mapping.getOperationGroup());
        if (StringUtils.isNotBlank(explicit)) {
            return explicit;
        }
        String label = safeTrim(mapping.getGroupDisplayName());
        if (StringUtils.isNotBlank(label)) {
            return slugify(label);
        }
        String module = safeTrim(mapping.getModuleName());
        if (StringUtils.isNotBlank(module)) {
            return slugify(module);
        }
        Long id = mapping.getId();
        return id != null ? "rule-" + id : "rule-" + UUID.randomUUID().toString().replace("-", "");
    }

    private static String effectiveGroupLabel(AuditOperationMapping mapping) {
        String label = safeTrim(mapping.getGroupDisplayName());
        if (StringUtils.isNotBlank(label)) {
            return label;
        }
        String module = safeTrim(mapping.getModuleName());
        if (StringUtils.isNotBlank(module)) {
            return module;
        }
        return "通用";
    }

    private static String safeTrim(String value) {
        return value == null ? null : value.trim();
    }

    private static String slugify(String value) {
        if (StringUtils.isBlank(value)) {
            return "general";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC);
        String lower = normalized.toLowerCase(Locale.ROOT);
        String slug = lower.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return StringUtils.defaultIfBlank(slug, "general");
    }
}
