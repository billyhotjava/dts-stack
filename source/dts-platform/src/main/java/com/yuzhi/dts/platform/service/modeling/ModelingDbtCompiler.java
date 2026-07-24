package com.yuzhi.dts.platform.service.modeling;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Deterministic compiler from the modeling contract to dbt project artifacts. */
public final class ModelingDbtCompiler {

    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final Pattern SOURCE_FIELD = Pattern.compile("^src_[0-9]+\\.[A-Za-z_][A-Za-z0-9_]*$");
    private static final Set<String> CAST_TYPES = Set.of("string", "integer", "bigint", "decimal", "date", "timestamp", "boolean");
    private static final Set<String> SETTINGS = Set.of("casts", "deduplicateBy", "dedupBy", "joins");
    private static final Set<String> JOIN_TYPES = Set.of("INNER", "LEFT", "RIGHT", "FULL");

    private ModelingDbtCompiler() {}

    public record CompiledArtifacts(String outputDirectory, Map<String, String> files) {}

    public static final class CompileException extends IllegalArgumentException {

        public CompileException(String message) {
            super(message);
        }
    }

    public static CompiledArtifacts compile(ModelingVNextContract.ModelSpec model) {
        validate(model);
        String name = model.name().trim();
        String layer = model.layer().name().toLowerCase();
        String outputDirectory = "models/" + layer + "/" + name + "/v" + model.revision();
        List<String> columns = selectedColumns(model);
        String sql = renderSql(model, columns);
        String schema = renderSchema(model, columns);
        String tests = renderTests(model);
        String docs = renderDocs(model, columns);
        return new CompiledArtifacts(
            outputDirectory,
            Map.of(
                name + ".sql", sql,
                name + ".yml", schema,
                name + ".tests.yml", tests,
                name + ".md", docs
            )
        );
    }

    /**
     * Compiles the ordinary designer mode through a system-managed ephemeral STG node. The
     * projection is revision-bound, so changing an implementation input cannot silently replay
     * an older set of mappings or settings.
     */
    public static CompiledArtifacts compile(ModelSpecCompilerProjection.ImplementationProjection projection) {
        if (projection == null) throw new CompileException("MODEL_IMPLEMENTATION_REQUIRED");
        validateImplementationProjection(projection);
        ModelingVNextContract.ModelSpec model = projection.model();
        validate(model, projection.inputMode() != ModelLifecycleContract.InputMode.GENERATED);
        if (model.implementationMode() != ModelingVNextContract.ImplementationMode.DESIGNER_GENERATED) {
            throw new CompileException("DBT_MANAGED_ARTIFACT_REQUIRED");
        }
        String name = implementationResourceName(projection.dbtUniqueId());
        String layer = model.layer().name().toLowerCase();
        String outputDirectory = "models/" + layer + "/" + name + "/v" + model.revision() + "/i" + projection.implementationRevision();
        List<String> columns = selectedColumns(model);
        String stgName = "stg_" + name;
        Map<String, String> files = new java.util.LinkedHashMap<>();
        files.put(stgName + ".sql", renderEphemeralStg(projection, columns));
        files.put(name + ".sql", renderModelSql(projection, columns, stgName));
        files.put(name + ".yml", renderSchema(model, columns, name));
        files.put(name + ".tests.yml", renderTests(model, name));
        return new CompiledArtifacts(outputDirectory, Map.copyOf(files));
    }

    private static void validate(ModelingVNextContract.ModelSpec model) {
        validate(model, true);
    }

    private static void validate(ModelingVNextContract.ModelSpec model, boolean sourceRequired) {
        if (model == null) throw new CompileException("MODEL_REQUIRED");
        if (isBlank(model.name())) throw new CompileException("MODEL_NAME_REQUIRED");
        if (model.revision() < 1) throw new CompileException("REVISION_INVALID");
        if (model.grain() == null || isBlank(model.grain().statement()) || model.grain().keys() == null || model.grain().keys().stream().noneMatch(ModelingDbtCompiler::notBlank)) {
            throw new CompileException("GRAIN_REQUIRED");
        }
        if (sourceRequired && (model.sourceRefs() == null || model.sourceRefs().isEmpty())) throw new CompileException("SOURCE_REQUIRED");
        if (model.sourceRefs() == null) return;
        for (ModelingVNextContract.SourceRef source : model.sourceRefs()) {
            if (source == null || isBlank(source.ref()) || source.layer() == null) throw new CompileException("SOURCE_REFERENCE_INVALID");
            if (model.layer() != ModelingVNextContract.Layer.ODS && source.layer().ordinal() > model.layer().ordinal()) {
                throw new CompileException("SOURCE_LAYER_INVALID");
            }
        }
    }

    private static List<String> selectedColumns(ModelingVNextContract.ModelSpec model) {
        Set<String> columns = new LinkedHashSet<>();
        addNonBlank(columns, model.grain().keys());
        if (model.standardBindings() != null) {
            addNonBlank(columns, model.standardBindings().stream().filter(Objects::nonNull).map(ModelingVNextContract.StandardBinding::fieldName).toList());
        }
        addNonBlank(columns, model.dimensions());
        addNonBlank(columns, model.metrics());
        return List.copyOf(columns);
    }

    private static String renderSql(ModelingVNextContract.ModelSpec model, List<String> columns) {
        String sources = model.sourceRefs().stream().map(ModelingDbtCompiler::renderSource).collect(Collectors.joining("\n"));
        String select = columns.stream().map(column -> "    " + column).collect(Collectors.joining(",\n"));
        String from = model.sourceRefs().get(0).kind().equalsIgnoreCase("DBT_MODEL")
            ? "from {{ ref('" + model.sourceRefs().get(0).ref() + "') }}"
            : "from {{ source('" + sourceNamespace(model.sourceRefs().get(0)) + "', '" + sourceName(model.sourceRefs().get(0)) + "') }}";
        return "-- generated by DTS modeling vNext, revision " + model.revision() + "\n" + sources + "\nselect\n" + select + "\n" + from + "\n";
    }

    private static String renderModelSql(
        ModelSpecCompilerProjection.ImplementationProjection projection,
        List<String> columns,
        String stgName
    ) {
        ModelingVNextContract.ModelSpec model = projection.model();
        String select = columns.stream().map(column -> "    " + column).collect(Collectors.joining(",\n"));
        String config = "{{ config(materialized='" + projection.materialization() + "', meta={"
            + "'tenantId':'" + jinjaString(projection.tenantId()) + "',"
            + "'modelSpecId':'" + model.id() + "',"
            + "'revision':" + model.revision() + ","
            + "'modelChecksum':'" + projection.modelChecksum() + "',"
            + "'implementationRevision':" + projection.implementationRevision() + ","
            + "'implementationChecksum':'" + projection.implementationChecksum() + "'"
            + "}) }}\n";
        return config + "-- generated by DTS modeling vNext, revision " + model.revision() + "\nselect\n"
            + select + "\nfrom {{ ref('" + stgName + "') }}\n";
    }

    private static String jinjaString(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private static String renderEphemeralStg(ModelSpecCompilerProjection.ImplementationProjection projection, List<String> columns) {
        ModelingVNextContract.ModelSpec model = projection.model();
        Map<String, String> mappings = mappingsByTarget(projection.fieldMappings());
        if (model.sourceRefs() != null && model.sourceRefs().size() > 1) {
            validateMultiSourceMappings(columns, mappings);
        }
        Map<String, String> casts = casts(projection.settings());
        String select = columns.stream().map(column -> renderMappedColumn(column, mappings, casts)).collect(Collectors.joining(",\n"));
        StringBuilder sql = new StringBuilder("{{ config(materialized='ephemeral') }}\nwith ");
        if (projection.inputMode() == ModelLifecycleContract.InputMode.GENERATED) {
            sql.append("generated_input as (\n    select current_date as generated_at\n),\ntransformed as (\n    select\n")
                .append(select)
                .append("\n    from generated_input");
        } else {
            List<ModelingVNextContract.SourceRef> sources = requireImplementationSources(model);
            for (int index = 0; index < sources.size(); index++) {
                if (index > 0) sql.append(",\n");
                sql.append("source_").append(index).append(" as (\n    select *\n    ")
                    .append(sourceFrom(sources.get(index))).append("\n)");
            }
            sql.append(",\ntransformed as (\n    select\n").append(select).append("\n    from source_0 src_0");
            for (JoinSpec join : joins(projection.settings(), sources.size())) {
                sql.append("\n    ").append(join.sqlType()).append(" source_").append(join.inputIndex())
                    .append(" src_").append(join.inputIndex()).append(" on ")
                    .append(join.leftField()).append(" = ").append(join.rightField());
            }
        }
        sql.append("\n)");
        List<String> dedupBy = settingValues(projection.settings(), "deduplicateBy");
        if (dedupBy.isEmpty()) dedupBy = settingValues(projection.settings(), "dedupBy");
        if (dedupBy.isEmpty()) {
            return sql.append("\nselect\n").append(columns.stream().map(column -> "    " + column).collect(Collectors.joining(",\n"))).append("\nfrom transformed\n").toString();
        }
        String partition = String.join(", ", dedupBy);
        return sql.append(",\nranked as (\n    select transformed.*, row_number() over (partition by ")
            .append(partition).append(" order by ").append(partition).append(") as __dts_row_number\n    from transformed\n)\nselect\n")
            .append(columns.stream().map(column -> "    " + column).collect(Collectors.joining(",\n")))
            .append("\nfrom ranked\nwhere __dts_row_number = 1\n").toString();
    }

    private static String sourceFrom(ModelingVNextContract.SourceRef source) {
        return source.kind().equalsIgnoreCase("DBT_MODEL")
            ? "from {{ ref('" + source.ref() + "') }}"
            : "from {{ source('" + sourceNamespace(source) + "', '" + sourceName(source) + "') }}";
    }

    private static List<ModelingVNextContract.SourceRef> requireImplementationSources(ModelingVNextContract.ModelSpec model) {
        if (model.sourceRefs() == null || model.sourceRefs().isEmpty()) throw new CompileException("SOURCE_REQUIRED");
        return model.sourceRefs();
    }

    private static void validateMultiSourceMappings(List<String> columns, Map<String, String> mappings) {
        if (!mappings.keySet().containsAll(columns)) throw new CompileException("MULTI_SOURCE_MAPPING_REQUIRED");
        if (columns.stream().map(mappings::get).anyMatch(value -> value == null || !SOURCE_FIELD.matcher(value).matches())) {
            throw new CompileException("MULTI_SOURCE_MAPPING_MUST_BE_QUALIFIED");
        }
    }

    private static List<JoinSpec> joins(Map<String, Object> settings, int sourceCount) {
        Object value = settings.get("joins");
        if (sourceCount == 1) {
            if (value instanceof List<?> values && !values.isEmpty()) throw new CompileException("SINGLE_SOURCE_JOIN_NOT_ALLOWED");
            return List.of();
        }
        if (!(value instanceof List<?> values) || values.size() != sourceCount - 1) {
            throw new CompileException("IMPLEMENTATION_JOIN_REQUIRED");
        }
        List<JoinSpec> joins = new java.util.ArrayList<>();
        for (Object item : values) {
            if (!(item instanceof Map<?, ?> raw)) throw new CompileException("IMPLEMENTATION_JOIN_INVALID");
            Object rawIndex = raw.get("inputIndex");
            if (!(rawIndex instanceof Number number) || number.doubleValue() != number.intValue()) {
                throw new CompileException("IMPLEMENTATION_JOIN_INVALID");
            }
            int inputIndex = number.intValue();
            String type = text(raw.get("type")).toUpperCase();
            String leftField = text(raw.get("leftField"));
            String rightField = text(raw.get("rightField"));
            if (
                inputIndex < 1 ||
                inputIndex >= sourceCount ||
                !JOIN_TYPES.contains(type) ||
                !SOURCE_FIELD.matcher(leftField).matches() ||
                !SOURCE_FIELD.matcher(rightField).matches() ||
                sourceIndex(leftField) >= inputIndex ||
                sourceIndex(rightField) != inputIndex
            ) {
                throw new CompileException("IMPLEMENTATION_JOIN_INVALID");
            }
            joins.add(new JoinSpec(inputIndex, type, leftField, rightField));
        }
        joins.sort(java.util.Comparator.comparingInt(JoinSpec::inputIndex));
        for (int index = 1; index < sourceCount; index++) {
            if (joins.get(index - 1).inputIndex() != index) throw new CompileException("IMPLEMENTATION_JOIN_COVERAGE_INVALID");
        }
        return List.copyOf(joins);
    }

    private static int sourceIndex(String value) {
        int dot = value.indexOf('.');
        return Integer.parseInt(value.substring(4, dot));
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private record JoinSpec(int inputIndex, String type, String leftField, String rightField) {
        String sqlType() {
            return "FULL".equals(type) ? "FULL OUTER JOIN" : type + " JOIN";
        }
    }

    private static Map<String, String> mappingsByTarget(List<ModelLifecycleContract.FieldMapping> mappings) {
        Map<String, String> result = new TreeMap<>();
        for (ModelLifecycleContract.FieldMapping mapping : mappings) result.putIfAbsent(mapping.targetField(), mapping.sourceField());
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> casts(Map<String, Object> settings) {
        Object value = settings.get("casts");
        if (!(value instanceof Map<?, ?> values)) return Map.of();
        Map<String, String> result = new TreeMap<>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && !entry.getKey().toString().isBlank() && !entry.getValue().toString().isBlank()) {
                result.put(entry.getKey().toString(), entry.getValue().toString());
            }
        }
        return result;
    }

    private static String renderMappedColumn(String column, Map<String, String> mappings, Map<String, String> casts) {
        String expression = mappings.getOrDefault(column, column);
        String type = casts.get(column);
        if (type == null) type = casts.get(expression);
        if (type != null) expression = "cast(" + expression + " as " + type + ")";
        return "        " + expression + (expression.equals(column) ? "" : " as " + column);
    }

    private static List<String> settingValues(Map<String, Object> settings, String key) {
        Object value = settings.get(key);
        if (!(value instanceof List<?> values)) return List.of();
        return values.stream().filter(Objects::nonNull).map(Object::toString).map(String::trim).filter(ModelingDbtCompiler::notBlank).toList();
    }

    private static void validateImplementationProjection(ModelSpecCompilerProjection.ImplementationProjection projection) {
        if (!SETTINGS.containsAll(projection.settings().keySet())) throw new CompileException("IMPLEMENTATION_SETTING_NOT_ALLOWED");
        for (ModelLifecycleContract.FieldMapping mapping : projection.fieldMappings()) {
            if (
                mapping == null ||
                !(identifier(mapping.sourceField()) || SOURCE_FIELD.matcher(mapping.sourceField()).matches()) ||
                !identifier(mapping.targetField())
            ) {
                throw new CompileException("IMPLEMENTATION_MAPPING_INVALID");
            }
        }
        Object casts = projection.settings().get("casts");
        if (casts != null) {
            if (!(casts instanceof Map<?, ?> values)) throw new CompileException("IMPLEMENTATION_CAST_INVALID");
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null || !identifier(entry.getKey().toString()) || !CAST_TYPES.contains(entry.getValue().toString().toLowerCase())) {
                    throw new CompileException("IMPLEMENTATION_CAST_INVALID");
                }
            }
        }
        validateIdentifierSetting(projection.settings(), "deduplicateBy");
        validateIdentifierSetting(projection.settings(), "dedupBy");
    }

    private static void validateIdentifierSetting(Map<String, Object> settings, String key) {
        Object value = settings.get(key);
        if (value == null) return;
        if (!(value instanceof List<?> values) || values.isEmpty() || values.stream().anyMatch(item -> item == null || !identifier(item.toString()))) {
            throw new CompileException("IMPLEMENTATION_SETTING_INVALID");
        }
    }

    private static boolean identifier(String value) {
        return value != null && IDENTIFIER.matcher(value).matches();
    }

    private static String implementationResourceName(String dbtUniqueId) {
        String[] parts = dbtUniqueId == null ? new String[0] : dbtUniqueId.trim().split("\\.", -1);
        if (parts.length != 3 || !"model".equals(parts[0]) || !identifier(parts[1]) || !identifier(parts[2])) {
            throw new CompileException("IMPLEMENTATION_DBT_UNIQUE_ID_INVALID");
        }
        return parts[2];
    }

    private static String renderSource(ModelingVNextContract.SourceRef source) {
        if (source.kind().equalsIgnoreCase("DBT_MODEL")) return "-- source: ref(" + source.ref() + ")";
        return "-- source: " + source.ref();
    }

    private static String renderSchema(ModelingVNextContract.ModelSpec model, List<String> columns) {
        return renderSchema(model, columns, model.name());
    }

    private static String renderSchema(ModelingVNextContract.ModelSpec model, List<String> columns, String resourceName) {
        StringBuilder yaml = new StringBuilder("version: 2\nmodels:\n  - name: ").append(resourceName).append("\n    description: \"").append(escape(model.grain().statement())).append("\"\n    columns:\n");
        for (String column : columns) {
            String standard = model.standardBindings() == null
                ? null
                : model.standardBindings().stream().filter(binding -> binding != null && column.equals(binding.fieldName())).map(ModelingVNextContract.StandardBinding::standardElementId).filter(ModelingDbtCompiler::notBlank).findFirst().orElse(null);
            yaml.append("      - name: ").append(column).append("\n        description: \"").append(escape(standard == null ? "模型字段" : "数据标准 " + standard)).append("\"\n");
            if (model.grain().keys().stream().anyMatch(key -> column.equals(key))) {
                yaml.append("        tests:\n          - not_null\n");
            }
        }
        return yaml.toString();
    }

    private static String renderTests(ModelingVNextContract.ModelSpec model) {
        return renderTests(model, model.name());
    }

    private static String renderTests(ModelingVNextContract.ModelSpec model, String resourceName) {
        String key = model.grain().keys().stream().filter(ModelingDbtCompiler::notBlank).findFirst().orElseThrow();
        return "version: 2\nmodels:\n  - name: " + resourceName + "\n    tests:\n      - unique:\n          column_name: " + key + "\n    columns:\n      - name: " + key + "\n        tests:\n          - not_null\n";
    }

    private static String renderDocs(ModelingVNextContract.ModelSpec model, List<String> columns) {
        return "# " + model.name() + "\n\n- 分层：" + model.layer() + "\n- 模型类型：" + model.modelType() + "\n- 业务粒度：" + model.grain().statement() + "\n- 字段：" + String.join(", ", columns) + "\n";
    }

    private static String sourceNamespace(ModelingVNextContract.SourceRef source) {
        String ref = source.ref();
        int separator = ref.indexOf('.');
        return separator > 0 ? ref.substring(0, separator) : "ods";
    }

    private static String sourceName(ModelingVNextContract.SourceRef source) {
        String ref = source.ref();
        int separator = ref.indexOf('.');
        return separator > 0 ? ref.substring(separator + 1) : ref;
    }

    private static void addNonBlank(Set<String> target, List<String> values) {
        if (values != null) values.stream().filter(ModelingDbtCompiler::notBlank).map(String::trim).forEach(target::add);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static boolean notBlank(String value) {
        return !isBlank(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
