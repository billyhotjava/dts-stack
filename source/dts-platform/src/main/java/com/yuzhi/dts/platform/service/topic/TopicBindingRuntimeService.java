package com.yuzhi.dts.platform.service.topic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping;
import com.yuzhi.dts.platform.domain.modeling.ModelingSqlModel;
import com.yuzhi.dts.platform.domain.topic.TopicBinding;
import com.yuzhi.dts.platform.domain.topic.TopicTemplate;
import com.yuzhi.dts.platform.domain.topic.TopicTemplateEntity;
import com.yuzhi.dts.platform.repository.infra.InfraOdsTableMappingRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingSqlModelRepository;
import com.yuzhi.dts.platform.repository.topic.TopicBindingRepository;
import com.yuzhi.dts.platform.repository.topic.TopicTemplateEntityRepository;
import com.yuzhi.dts.platform.repository.topic.TopicTemplateRepository;
import com.yuzhi.dts.platform.service.etl.DbtConfigService;
import com.yuzhi.dts.platform.service.etl.DbtTargetConnectionFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TopicBindingRuntimeService {

    private static final Logger LOG = LoggerFactory.getLogger(TopicBindingRuntimeService.class);
    private static final String GLOBAL_SCOPE = "GLOBAL";
    private static final String PROJECT_MANAGEMENT_TEMPLATE = "project-management";
    private static final String PROJECT_SUBJECT_ENTITY = "project_subject_domain";

    private final DbtConfigService dbtConfigService;
    private final TopicTemplateRepository templateRepository;
    private final TopicTemplateEntityRepository entityRepository;
    private final TopicBindingRepository bindingRepository;
    private final InfraOdsTableMappingRepository odsTableMappingRepository;
    private final ModelingSqlModelRepository modelingSqlModelRepository;
    private final DbtTargetConnectionFactory connectionFactory;
    private final ObjectMapper objectMapper;

    public TopicBindingRuntimeService(
        DbtConfigService dbtConfigService,
        TopicTemplateRepository templateRepository,
        TopicTemplateEntityRepository entityRepository,
        TopicBindingRepository bindingRepository,
        InfraOdsTableMappingRepository odsTableMappingRepository,
        ModelingSqlModelRepository modelingSqlModelRepository,
        DbtTargetConnectionFactory connectionFactory,
        ObjectMapper objectMapper
    ) {
        this.dbtConfigService = dbtConfigService;
        this.templateRepository = templateRepository;
        this.entityRepository = entityRepository;
        this.bindingRepository = bindingRepository;
        this.odsTableMappingRepository = odsTableMappingRepository;
        this.modelingSqlModelRepository = modelingSqlModelRepository;
        this.connectionFactory = connectionFactory;
        this.objectMapper = objectMapper;
    }

    public RuntimeCompilationResult compileRuntimeArtifacts() {
        DbtConfigService.DbtConfigView view = dbtConfigService.loadConfig();
        if (view == null || view.enabled() == false || view.config() == null || !StringUtils.hasText(view.config().projectDir())) {
            return new RuntimeCompilationResult(false, null, 0, Map.of(), List.of("dbt 工作区未启用"));
        }
        Path projectDir = Path.of(view.config().projectDir());
        Path sourcesPath = projectDir.resolve("models/__topic_bindings/topic_sources.yml");
        Path varsPath = projectDir.resolve("target/topic_binding_vars.json");

        List<TopicTemplate> templates = loadActiveTemplates();
        List<TopicTemplateEntity> entities = loadAllEntities();
        Map<UUID, TopicTemplate> templatesById = templates.stream().collect(Collectors.toMap(TopicTemplate::getId, value -> value));
        Map<UUID, TopicTemplateEntity> entitiesById = entities.stream().collect(Collectors.toMap(TopicTemplateEntity::getId, value -> value));

        List<TopicBinding> activeBindings = bindingRepository.findAll().stream()
            .filter(this::isActiveBinding)
            .filter(binding -> templatesById.containsKey(binding.getTemplateId()))
            .filter(binding -> entitiesById.containsKey(binding.getEntityId()))
            .sorted(Comparator.comparing(TopicBinding::getCreatedDate, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();

        List<String> warnings = new ArrayList<>();
        Map<String, Object> root = buildSources(templatesById, entitiesById, activeBindings, warnings);
        Map<String, Object> vars = buildVars(templatesById, entitiesById, activeBindings);

        try {
            Files.createDirectories(sourcesPath.getParent());
            Files.createDirectories(varsPath.getParent());
            Files.writeString(sourcesPath, YamlWriter.toYaml(root), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(varsPath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(vars), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return new RuntimeCompilationResult(true, sourcesPath.toString(), activeBindings.size(), vars, warnings);
        } catch (IOException ex) {
            LOG.warn("[topic-binding] failed to compile runtime artifacts: {}", ex.getMessage());
            List<String> issues = new ArrayList<>(warnings);
            issues.add("写入专题绑定运行文件失败: " + ex.getMessage());
            return new RuntimeCompilationResult(true, sourcesPath.toString(), activeBindings.size(), vars, issues);
        }
    }

    public BindingDiagnostics diagnose(String selector) {
        List<TopicTemplate> templates = loadActiveTemplates();
        List<TopicTemplateEntity> entities = loadAllEntities();
        Map<UUID, TopicTemplate> templatesById = templates.stream().collect(Collectors.toMap(TopicTemplate::getId, value -> value));
        Map<UUID, TopicTemplateEntity> entitiesById = entities.stream().collect(Collectors.toMap(TopicTemplateEntity::getId, value -> value));
        Map<String, TopicBinding> bindingByKey = bindingRepository.findAll().stream()
            .filter(this::isActiveBinding)
            .collect(Collectors.toMap(this::bindingKey, value -> value, (left, right) -> right, LinkedHashMap::new));

        Set<String> relevantTemplateCodes = resolveRelevantTemplateCodes(selector, templates);
        List<BindingRow> rows = new ArrayList<>();
        List<String> missingRequired = new ArrayList<>();

        for (TopicTemplate template : templates) {
            if (!relevantTemplateCodes.isEmpty() && !relevantTemplateCodes.contains(normalizeCode(template.getTemplateCode()))) {
                continue;
            }
            List<TopicTemplateEntity> templateEntities = entities.stream()
                .filter(entity -> template.getId().equals(entity.getTemplateId()))
                .sorted(Comparator.comparing(TopicTemplateEntity::getEntityCode, String.CASE_INSENSITIVE_ORDER))
                .toList();
            for (TopicTemplateEntity entity : templateEntities) {
                TopicBinding binding = bindingByKey.get(bindingKey(template.getId(), entity.getId()));
                ResolvedBindingTarget resolved = binding == null ? null : resolveBindingTarget(binding, entity);
                boolean bound = binding != null;
                if (Boolean.TRUE.equals(entity.getRequired()) && !bound) {
                    missingRequired.add(template.getTemplateCode() + "." + entity.getEntityCode());
                }
                rows.add(
                    new BindingRow(
                        template.getTemplateCode(),
                        template.getTemplateName(),
                        entity.getEntityCode(),
                        entity.getEntityName(),
                        Boolean.TRUE.equals(entity.getRequired()),
                        entity.getSourceName(),
                        entity.getTableName(),
                        entity.getExpectedSchema(),
                        bound,
                        resolved == null ? null : resolved.schemaName(),
                        resolved == null ? null : resolved.tableName(),
                        binding == null ? null : binding.getStatus(),
                        binding == null ? null : binding.getBatchId(),
                        binding == null ? null : binding.getOdsMappingId(),
                        binding == null ? null : binding.getNotes()
                    )
                );
            }
        }
        return new BindingDiagnostics(
            selector,
            relevantTemplateCodes.stream().sorted().toList(),
            rows,
            missingRequired
        );
    }

    private Map<String, Object> buildSources(
        Map<UUID, TopicTemplate> templatesById,
        Map<UUID, TopicTemplateEntity> entitiesById,
        List<TopicBinding> activeBindings,
        List<String> warnings
    ) {
        Map<String, SourceBucket> buckets = new LinkedHashMap<>();
        for (TopicBinding binding : activeBindings) {
            TopicTemplate template = templatesById.get(binding.getTemplateId());
            TopicTemplateEntity entity = entitiesById.get(binding.getEntityId());
            if (template == null || entity == null) {
                continue;
            }
            if (!shouldEmitRuntimeSource(template, entity)) {
                continue;
            }
            String sourceName = defaultText(entity.getSourceName(), normalizeCode(template.getTemplateCode()) + "_ods");
            ResolvedBindingTarget resolved = resolveBindingTarget(binding, entity);
            String schemaName = resolved.schemaName();
            SourceBucket bucket = buckets.computeIfAbsent(sourceName, key -> new SourceBucket(sourceName, schemaName));
            if (!bucket.schemaName.equalsIgnoreCase(schemaName)) {
                warnings.add("逻辑 source " + sourceName + " 绑定到了多个 schema，当前保留 " + bucket.schemaName + "，忽略 " + schemaName);
            }
            Map<String, Object> table = new LinkedHashMap<>();
            table.put("name", entity.getTableName());
            table.put("identifier", resolved.tableName());
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("template_code", template.getTemplateCode());
            meta.put("entity_code", entity.getEntityCode());
            meta.put("binding_mode", binding.getBindingMode());
            putIfText(meta, "binding_status", binding.getStatus());
            putIfText(meta, "schema_name", schemaName);
            if (binding.getBatchId() != null) {
                meta.put("batch_id", binding.getBatchId().toString());
            }
            if (binding.getOdsMappingId() != null) {
                meta.put("ods_mapping_id", binding.getOdsMappingId().toString());
            }
            if (binding.getDataSourceId() != null) {
                meta.put("data_source_id", binding.getDataSourceId().toString());
            }
            putIfText(meta, "bound_by", binding.getBoundBy());
            table.put("meta", meta);
            bucket.tables.add(table);
        }

        List<Map<String, Object>> sources = new ArrayList<>();
        for (SourceBucket bucket : buckets.values()) {
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("name", bucket.sourceName);
            source.put("schema", bucket.schemaName);
            source.put("tables", bucket.tables);
            sources.add(source);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 2);
        root.put("sources", sources);
        return root;
    }

    private boolean shouldEmitRuntimeSource(TopicTemplate template, TopicTemplateEntity entity) {
        String templateCode = normalizeCode(template.getTemplateCode());
        String entityCode = normalizeCode(entity.getEntityCode());
        if (PROJECT_MANAGEMENT_TEMPLATE.equals(templateCode) && PROJECT_SUBJECT_ENTITY.equals(entityCode)) {
            return false;
        }
        return true;
    }

    private ResolvedBindingTarget resolveBindingTarget(TopicBinding binding, TopicTemplateEntity entity) {
        String schemaName = defaultText(binding.getSchemaName(), entity.getExpectedSchema(), "ods");
        String tableName = defaultText(binding.getTableName(), entity.getTableName());
        InfraOdsTableMapping mapping = findMatchingMapping(binding, schemaName, tableName);
        ResolvedBindingTarget resolved = mapping == null
            ? new ResolvedBindingTarget(schemaName, tableName)
            : new ResolvedBindingTarget(
            defaultText(mapping.getOdsSchema(), schemaName, "public"),
            defaultText(mapping.getOdsTable(), tableName)
        );
        return correctTargetSchema(resolved);
    }

    private ResolvedBindingTarget correctTargetSchema(ResolvedBindingTarget target) {
        if (target == null || !StringUtils.hasText(target.tableName())) {
            return target;
        }
        try {
            DbtTargetConnectionFactory.TargetWarehouse warehouse = connectionFactory.resolveTarget();
            String currentSchema = defaultText(target.schemaName(), warehouse.schema(), "public");
            String fallbackSchema = defaultText(warehouse.schema(), "public");
            if (relationExists(warehouse, currentSchema, target.tableName())) {
                return new ResolvedBindingTarget(currentSchema, target.tableName());
            }
            if (!currentSchema.equalsIgnoreCase(fallbackSchema) && relationExists(warehouse, fallbackSchema, target.tableName())) {
                LOG.info("[topic-binding] corrected bound table {}.{} -> {}.{}", currentSchema, target.tableName(), fallbackSchema, target.tableName());
                return new ResolvedBindingTarget(fallbackSchema, target.tableName());
            }
        } catch (Exception ex) {
            LOG.debug("[topic-binding] failed to verify target schema for {}.{}: {}", target.schemaName(), target.tableName(), ex.getMessage());
        }
        return target;
    }

    private boolean relationExists(DbtTargetConnectionFactory.TargetWarehouse warehouse, String schemaName, String tableName) throws Exception {
        try (Connection connection = connectionFactory.open(warehouse)) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet tables = metadata.getTables(null, schemaName, tableName, new String[] { "TABLE", "VIEW" })) {
                return tables.next();
            }
        }
    }

    private InfraOdsTableMapping findMatchingMapping(TopicBinding binding, String schemaName, String tableName) {
        if (binding.getOdsMappingId() != null) {
            InfraOdsTableMapping mapping = odsTableMappingRepository.findById(binding.getOdsMappingId()).orElse(null);
            if (isEnabled(mapping)) {
                return mapping;
            }
        }
        InfraOdsTableMapping exact = odsTableMappingRepository.findFirstByOdsSchemaIgnoreCaseAndOdsTableIgnoreCase(schemaName, tableName).orElse(null);
        if (isEnabled(exact)) {
            return exact;
        }
        List<InfraOdsTableMapping> candidates = odsTableMappingRepository.findByEnabledTrueAndOdsTableIgnoreCaseOrderByCreatedDateDesc(tableName);
        if (candidates == null || candidates.size() != 1) {
            return null;
        }
        return candidates.get(0);
    }

    private boolean isEnabled(InfraOdsTableMapping mapping) {
        return mapping != null && Boolean.TRUE.equals(mapping.getEnabled());
    }

    private Map<String, Object> buildVars(
        Map<UUID, TopicTemplate> templatesById,
        Map<UUID, TopicTemplateEntity> entitiesById,
        List<TopicBinding> activeBindings
    ) {
        Map<String, Object> topicBindings = new LinkedHashMap<>();
        Map<String, Object> compatibilityVars = new LinkedHashMap<>();
        for (TopicBinding binding : activeBindings) {
            TopicTemplate template = templatesById.get(binding.getTemplateId());
            TopicTemplateEntity entity = entitiesById.get(binding.getEntityId());
            if (template == null || entity == null) {
                continue;
            }
            ResolvedBindingTarget resolved = resolveBindingTarget(binding, entity);
            String schemaName = resolved.schemaName();
            String physicalTable = resolved.tableName();
            @SuppressWarnings("unchecked")
            Map<String, Object> templateVars = (Map<String, Object>) topicBindings.computeIfAbsent(template.getTemplateCode(), key -> new LinkedHashMap<>());
            Map<String, Object> entityVars = new LinkedHashMap<>();
            entityVars.put("source_name", entity.getSourceName());
            entityVars.put("logical_table", entity.getTableName());
            entityVars.put("schema_name", schemaName);
            entityVars.put("physical_table", physicalTable);
            if (binding.getBatchId() != null) {
                entityVars.put("batch_id", binding.getBatchId().toString());
            }
            if (binding.getOdsMappingId() != null) {
                entityVars.put("ods_mapping_id", binding.getOdsMappingId().toString());
            }
            if (binding.getDataSourceId() != null) {
                entityVars.put("data_source_id", binding.getDataSourceId().toString());
            }
            templateVars.put(entity.getEntityCode(), entityVars);
            applyCompatibilityVars(template, entity, schemaName, physicalTable, compatibilityVars);
        }
        if (compatibilityVars.isEmpty()) {
            return Map.of("topic_bindings", topicBindings);
        }
        Map<String, Object> merged = new LinkedHashMap<>(compatibilityVars);
        merged.put("topic_bindings", topicBindings);
        return merged;
    }

    private void applyCompatibilityVars(
        TopicTemplate template,
        TopicTemplateEntity entity,
        String schemaName,
        String physicalTable,
        Map<String, Object> target
    ) {
        String templateCode = normalizeCode(template.getTemplateCode());
        String entityCode = normalizeCode(entity.getEntityCode());
        if (PROJECT_MANAGEMENT_TEMPLATE.equals(templateCode) && PROJECT_SUBJECT_ENTITY.equals(entityCode)) {
            target.put("project_management_ods_schema", schemaName);
            target.put("project_management_ods_table", physicalTable);
        }
    }

    private Set<String> resolveRelevantTemplateCodes(String selector, List<TopicTemplate> templates) {
        if (!StringUtils.hasText(selector) || "all".equalsIgnoreCase(selector.trim())) {
            return templates.stream()
                .map(TopicTemplate::getTemplateCode)
                .map(this::normalizeCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        Set<String> candidates = new LinkedHashSet<>();
        Set<String> selectorTokens = splitSelectorTokens(selector);
        Set<String> modelNames = new LinkedHashSet<>();
        for (String token : selectorTokens) {
            String normalized = token.toLowerCase(Locale.ROOT);
            if (normalized.startsWith("tag:")) {
                candidates.add(normalized.substring(4).trim());
            } else if (normalized.startsWith("model:")) {
                modelNames.add(normalized.substring(6).trim());
            } else if (!normalized.contains(":")) {
                modelNames.add(normalized);
            }
        }
        if (!modelNames.isEmpty()) {
            for (ModelingSqlModel model : modelingSqlModelRepository.findAll()) {
                if (model == null || !StringUtils.hasText(model.getName())) {
                    continue;
                }
                if (!modelNames.contains(model.getName().trim().toLowerCase(Locale.ROOT))) {
                    continue;
                }
                candidates.addAll(splitTags(model.getTags()));
            }
        }
        Set<String> available = templates.stream()
            .map(TopicTemplate::getTemplateCode)
            .map(this::normalizeCode)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        return candidates.stream()
            .map(this::normalizeCode)
            .filter(available::contains)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> splitSelectorTokens(String selector) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : selector.split("[,\\s]+")) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            String normalized = token.trim();
            while (normalized.startsWith("+")) {
                normalized = normalized.substring(1);
            }
            while (normalized.endsWith("+")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            if (StringUtils.hasText(normalized)) {
                tokens.add(normalized);
            }
        }
        return tokens;
    }

    private Set<String> splitTags(String rawTags) {
        if (!StringUtils.hasText(rawTags)) {
            return Set.of();
        }
        Set<String> tags = new LinkedHashSet<>();
        for (String token : rawTags.split("[,\\s]+")) {
            if (StringUtils.hasText(token)) {
                tags.add(normalizeCode(token));
            }
        }
        return tags;
    }

    private List<TopicTemplate> loadActiveTemplates() {
        return templateRepository.findAll().stream()
            .filter(template -> template != null && Boolean.TRUE.equals(template.getEnabled()))
            .filter(template -> !"INACTIVE".equalsIgnoreCase(defaultText(template.getStatus(), "ACTIVE")))
            .sorted(Comparator.comparing(TopicTemplate::getTemplateCode, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private List<TopicTemplateEntity> loadAllEntities() {
        return entityRepository.findAll().stream()
            .filter(entity -> entity != null && StringUtils.hasText(entity.getEntityCode()))
            .sorted(Comparator.comparing(TopicTemplateEntity::getEntityCode, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private boolean isActiveBinding(TopicBinding binding) {
        return binding != null
            && GLOBAL_SCOPE.equalsIgnoreCase(defaultText(binding.getScopeKey(), GLOBAL_SCOPE))
            && "ACTIVE".equalsIgnoreCase(defaultText(binding.getStatus(), "ACTIVE"));
    }

    private String bindingKey(TopicBinding binding) {
        return bindingKey(binding.getTemplateId(), binding.getEntityId());
    }

    private String bindingKey(UUID templateId, UUID entityId) {
        return String.valueOf(templateId) + "::" + String.valueOf(entityId);
    }

    private String normalizeCode(String value) {
        return defaultText(value, "").trim().toLowerCase(Locale.ROOT);
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String defaultText(String value, String fallback, String finalFallback) {
        if (StringUtils.hasText(value)) {
            return value.trim();
        }
        if (StringUtils.hasText(fallback)) {
            return fallback.trim();
        }
        return finalFallback;
    }

    private void putIfText(Map<String, Object> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value.trim());
        }
    }

    public record RuntimeCompilationResult(
        boolean enabled,
        String path,
        int activeBindings,
        Map<String, Object> vars,
        List<String> warnings
    ) {}

    public record BindingDiagnostics(
        String selector,
        List<String> relevantTemplateCodes,
        List<BindingRow> rows,
        List<String> missingRequired
    ) {}

    public record BindingRow(
        String templateCode,
        String templateName,
        String entityCode,
        String entityName,
        boolean required,
        String sourceName,
        String logicalTableName,
        String expectedSchema,
        boolean bound,
        String boundSchemaName,
        String boundTableName,
        String bindingStatus,
        UUID batchId,
        UUID odsMappingId,
        String notes
    ) {}

    private record SourceBucket(String sourceName, String schemaName, List<Map<String, Object>> tables) {
        private SourceBucket(String sourceName, String schemaName) {
            this(sourceName, schemaName, new ArrayList<>());
        }
    }

    private record ResolvedBindingTarget(String schemaName, String tableName) {}

    private static final class YamlWriter {
        private static String toYaml(Map<String, Object> root) {
            StringBuilder sb = new StringBuilder();
            writeMap(sb, root, 0);
            return sb.toString();
        }

        @SuppressWarnings("unchecked")
        private static void writeMap(StringBuilder sb, Map<String, Object> map, int indent) {
            String prefix = " ".repeat(Math.max(0, indent));
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                sb.append(prefix).append(entry.getKey()).append(":");
                Object value = entry.getValue();
                if (value instanceof Map<?, ?> nested) {
                    if (nested.isEmpty()) {
                        sb.append(" {}\n");
                    } else {
                        sb.append("\n");
                        writeMap(sb, (Map<String, Object>) nested, indent + 2);
                    }
                } else if (value instanceof List<?> list) {
                    if (list.isEmpty()) {
                        sb.append(" []\n");
                    } else {
                        sb.append("\n");
                        writeList(sb, list, indent + 2);
                    }
                } else {
                    sb.append(" ").append(scalar(value)).append("\n");
                }
            }
        }

        @SuppressWarnings("unchecked")
        private static void writeList(StringBuilder sb, List<?> list, int indent) {
            String prefix = " ".repeat(Math.max(0, indent));
            for (Object item : list) {
                sb.append(prefix).append("-");
                if (item instanceof Map<?, ?> nested) {
                    sb.append("\n");
                    writeMap(sb, (Map<String, Object>) nested, indent + 2);
                } else if (item instanceof List<?> nestedList) {
                    sb.append("\n");
                    writeList(sb, nestedList, indent + 2);
                } else {
                    sb.append(" ").append(scalar(item)).append("\n");
                }
            }
        }

        private static String scalar(Object value) {
            if (value == null) {
                return "\"\"";
            }
            if (value instanceof Number || value instanceof Boolean) {
                return value.toString();
            }
            String text = String.valueOf(value);
            if (text.isEmpty()) {
                return "\"\"";
            }
            return "\"" + text.replace("\"", "\\\"") + "\"";
        }
    }
}
