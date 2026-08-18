package com.yuzhi.dts.platform.service.modeling;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
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
    private static final Map<String, String> POSTGRES_CAST_TYPES = Map.of(
        "string",
        "text",
        "integer",
        "integer",
        "bigint",
        "bigint",
        "decimal",
        "numeric",
        "date",
        "date",
        "timestamp",
        "timestamp",
        "boolean",
        "boolean"
    );
	private static final Set<String> JOIN_TYPES = Set.of("INNER", "LEFT");
	private static final Set<String> FILTER_OPERATORS = Set.of(
		"EQ", "NE", "GT", "GTE", "LT", "LTE", "IN", "NOT_IN", "IS_NULL", "IS_NOT_NULL", "BETWEEN"
	);
	private static final Set<String> FILTER_VALUE_TYPES = Set.of("STRING", "NUMBER", "BOOLEAN", "DATE", "TIMESTAMP");
	private static final Set<String> AGGREGATION_FUNCTIONS = Set.of("SUM", "COUNT", "MIN", "MAX", "AVG", "COUNT_DISTINCT");
    private static final Set<String> DATE_DIMENSION_CONFIG_KEYS = Set.of("start", "end", "startYear", "endYear");
    private static final long DATE_DIMENSION_MAX_DAYS = 73_200;
    private static final Map<String, String> DATE_DIMENSION_EXPRESSIONS = Map.of(
        "date_key",
        "to_char(day_value, 'YYYYMMDD')::integer",
        "full_date",
        "day_value::date",
        "year_no",
        "extract(year from day_value)::integer",
        "quarter_no",
        "extract(quarter from day_value)::integer",
        "month_no",
        "extract(month from day_value)::integer",
        "iso_week_no",
        "extract(week from day_value)::integer",
        "is_workday",
        "extract(isodow from day_value)::integer between 1 and 5"
    );

    private ModelingDbtCompiler() {}

    public record CompiledArtifacts(String outputDirectory, Map<String, String> files) {}

    public static final class CompileException extends IllegalArgumentException {

        public CompileException(String message) {
            super(message);
        }
    }

    public static CompiledArtifacts compile(ModelingCompilerContract.CompilerModel model) {
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
        ModelImplementationExecutionPlanner.ExecutionPlan executionPlan =
            validateImplementationProjection(projection);
        ModelingCompilerContract.CompilerModel model = projection.model();
        validate(model, projection.inputMode() != ModelLifecycleContract.InputMode.GENERATED);
        if (model.implementationMode() != ModelingCompilerContract.ImplementationMode.DESIGNER_GENERATED) {
            throw new CompileException("DBT_MANAGED_ARTIFACT_REQUIRED");
        }
        String name = implementationResourceName(projection.dbtUniqueId());
        String layer = model.layer().name().toLowerCase();
        String outputDirectory = "models/" + layer + "/" + name + "/v" + model.revision() + "/i" + projection.implementationRevision();
        List<String> columns = selectedColumns(model);
        Map<String, ValidatedField> fieldTypes =
            validateTypedFields(projection, columns);
        String stgName = "stg_" + name;
        Map<String, String> files = new java.util.LinkedHashMap<>();
        files.put(stgName + ".sql", renderEphemeralStg(projection, columns));
        files.put(name + ".sql", renderModelSql(projection, columns, fieldTypes, stgName, executionPlan));
        files.put(name + ".yml", renderRunnableSchema(model, columns, fieldTypes, name));
        return new CompiledArtifacts(outputDirectory, Map.copyOf(files));
    }

    private static void validate(ModelingCompilerContract.CompilerModel model) {
        validate(model, true);
    }

    private static void validate(ModelingCompilerContract.CompilerModel model, boolean sourceRequired) {
        if (model == null) throw new CompileException("MODEL_REQUIRED");
        if (isBlank(model.name())) throw new CompileException("MODEL_NAME_REQUIRED");
        if (model.revision() < 1) throw new CompileException("REVISION_INVALID");
        if (model.grain() == null || isBlank(model.grain().statement()) || model.grain().keys() == null || model.grain().keys().stream().noneMatch(ModelingDbtCompiler::notBlank)) {
            throw new CompileException("GRAIN_REQUIRED");
        }
        if (sourceRequired && (model.sourceRefs() == null || model.sourceRefs().isEmpty())) throw new CompileException("SOURCE_REQUIRED");
        if (model.sourceRefs() == null) return;
        for (ModelingCompilerContract.SourceRef source : model.sourceRefs()) {
            if (source == null || isBlank(source.ref()) || source.layer() == null) throw new CompileException("SOURCE_REFERENCE_INVALID");
            if (model.layer() != ModelingCompilerContract.Layer.ODS && source.layer().ordinal() > model.layer().ordinal()) {
                throw new CompileException("SOURCE_LAYER_INVALID");
            }
        }
    }

    private static List<String> selectedColumns(ModelingCompilerContract.CompilerModel model) {
        Set<String> columns = new LinkedHashSet<>();
        addNonBlank(columns, model.grain().keys());
        if (model.standardBindings() != null) {
            addNonBlank(columns, model.standardBindings().stream().filter(Objects::nonNull).map(ModelingCompilerContract.StandardBinding::fieldName).toList());
        }
        addNonBlank(columns, model.dimensions());
        addNonBlank(columns, model.metrics());
        return List.copyOf(columns);
    }

    private static String renderSql(ModelingCompilerContract.CompilerModel model, List<String> columns) {
        String sources = model.sourceRefs().stream().map(ModelingDbtCompiler::renderSource).collect(Collectors.joining("\n"));
        String select = columns.stream().map(column -> "    " + column).collect(Collectors.joining(",\n"));
        String from = model.sourceRefs().get(0).kind().equalsIgnoreCase("DBT_MODEL")
            ? "from {{ ref('" + model.sourceRefs().get(0).ref() + "') }}"
            : "from {{ source('" + sourceNamespace(model.sourceRefs().get(0)) + "', '" + sourceName(model.sourceRefs().get(0)) + "') }}";
        return "-- generated by DTS modeling compiler, revision " + model.revision() + "\n" + sources + "\nselect\n" + select + "\n" + from + "\n";
    }

    private static String renderModelSql(
        ModelSpecCompilerProjection.ImplementationProjection projection,
        List<String> columns,
        Map<String, ValidatedField> fieldTypes,
        String stgName,
        ModelImplementationExecutionPlanner.ExecutionPlan executionPlan
    ) {
        ModelingCompilerContract.CompilerModel model = projection.model();
        String select = columns
            .stream()
            .map(column ->
                "    cast(" +
                column +
                " as " +
                fieldTypes.get(column).type().postgresType() +
                ") as " +
                column
            )
            .collect(Collectors.joining(",\n"));
        String uniqueKey = executionPlan.uniqueKey()
            .stream()
            .map(value -> "'" + jinjaString(value) + "'")
            .collect(Collectors.joining(",", "[", "]"));
        StringBuilder config = new StringBuilder("{{ config(materialized='")
            .append(executionPlan.effectiveMaterialization())
            .append("', alias='")
            .append(jinjaString(executionPlan.targetIdentifier()))
            .append("'");
        if ("incremental".equals(executionPlan.effectiveMaterialization())) {
            config.append(", unique_key=").append(uniqueKey);
        }
        config.append(", meta={")
            .append("'tenantId':'").append(jinjaString(projection.tenantId())).append("',")
            .append("'planId':'").append(jinjaString(projection.planId())).append("',")
            .append("'modelSpecId':'").append(model.id()).append("',")
            .append("'revision':").append(model.revision()).append(",")
            .append("'modelRevision':").append(model.revision()).append(",")
            .append("'modelChecksum':'").append(projection.modelChecksum()).append("',")
            .append("'implementationRevision':").append(projection.implementationRevision()).append(",")
            .append("'implementationChecksum':'").append(projection.implementationChecksum()).append("',")
            .append("'layer':'").append(model.layer().name()).append("',")
            .append("'targetIdentifier':'").append(jinjaString(executionPlan.targetIdentifier())).append("',")
            .append("'loadStrategy':'")
            .append(jinjaString(text(projection.settings().get("loadStrategy")).toUpperCase()))
            .append("'");
		if (projection.dependencyChecksum() != null) {
			config.append(",'dependencyChecksum':'").append(projection.dependencyChecksum()).append("'");
		}
        Object retentionDays = projection.settings().get("retentionDays");
        if (retentionDays instanceof Number days) config.append(",'retentionDays':").append(days.longValue());
        config.append("}) }}\n");
        return config + "-- generated by DTS modeling compiler, revision " + model.revision() + "\nselect\n"
            + select + "\nfrom {{ ref('" + stgName + "') }}\n";
    }

    private static String jinjaString(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private static String renderEphemeralStg(ModelSpecCompilerProjection.ImplementationProjection projection, List<String> columns) {
        ModelingCompilerContract.CompilerModel model = projection.model();
        Map<String, String> mappings = mappingsByTarget(projection.fieldMappings());
        if (model.sourceRefs() != null && model.sourceRefs().size() > 1) {
            validateMultiSourceMappings(columns, mappings);
        }
        Map<String, String> casts = casts(projection.settings());
		List<ModelingCompilerContract.SourceRef> sources = projection.inputMode() == ModelLifecycleContract.InputMode.GENERATED
			? List.of()
			: requireImplementationSources(model);
		Map<String, AggregationSpec> aggregations = aggregations(
			projection.settings(),
			columns,
			mappings,
			sources.size()
		);
		String select = columns
			.stream()
			.map(column -> renderProjectedColumn(column, mappings, casts, aggregations))
			.collect(Collectors.joining(",\n"));
        StringBuilder sql = new StringBuilder("{{ config(materialized='ephemeral') }}\nwith ");
        if (projection.inputMode() == ModelLifecycleContract.InputMode.GENERATED) {
			if (!aggregations.isEmpty() || projection.settings().containsKey("filters")) {
				throw new CompileException("GENERATED_TRANSFORM_SETTING_NOT_ALLOWED");
			}
            sql.append(renderDateDimensionInput(projection, columns))
                .append(",\ntransformed as (\n    select\n")
                .append(select)
                .append("\n    from generated_input");
        } else {
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
			List<String> filters = filters(projection.settings(), mappings, sources.size());
			if (!filters.isEmpty()) {
				sql.append("\n    where ").append(String.join("\n      and ", filters));
			}
			if (!aggregations.isEmpty()) {
				List<String> groupBy = groupByExpressions(projection.settings(), columns, mappings, casts);
				sql.append("\n    group by ").append(String.join(", ", groupBy));
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

    private static String renderDateDimensionInput(
        ModelSpecCompilerProjection.ImplementationProjection projection,
        List<String> columns
    ) {
        if (
            projection.inputs().size() != 1 ||
            !(projection.inputs().get(0) instanceof ModelLifecycleContract.GeneratedInput input) ||
            !ModelImplementationInputPolicy.DATE_DIMENSION_GENERATOR.equals(input.generatorType())
        ) {
            throw new CompileException("DATE_DIMENSION_INPUT_INVALID");
        }
        if (!projection.fieldMappings().isEmpty()) {
            throw new CompileException("DATE_DIMENSION_MAPPING_NOT_ALLOWED");
        }
        if (!DATE_DIMENSION_EXPRESSIONS.keySet().containsAll(columns)) {
            throw new CompileException("DATE_DIMENSION_FIELD_UNSUPPORTED");
        }
        DateRange range = dateDimensionRange(input.config());
        String generatedColumns = columns
            .stream()
            .map(column -> "        " + DATE_DIMENSION_EXPRESSIONS.get(column) + " as " + column)
            .collect(Collectors.joining(",\n"));
        return "generated_input as (\n    select\n" +
            generatedColumns +
            "\n    from generate_series(\n        " +
            range.startExpression() +
            ",\n        " +
            range.endExpression() +
            ",\n        interval '1 day'\n    ) as calendar(day_value)\n)";
    }

    private static DateRange dateDimensionRange(Map<String, Object> config) {
        Map<String, Object> values = config == null ? Map.of() : config;
        if (!DATE_DIMENSION_CONFIG_KEYS.containsAll(values.keySet())) {
            throw new CompileException("DATE_DIMENSION_CONFIG_INVALID");
        }
        LocalDate startDate = configuredDate(values, "start");
        LocalDate endDate = configuredDate(values, "end");
        Integer startYear = configuredYear(values, "startYear");
        Integer endYear = configuredYear(values, "endYear");
        if ((startDate != null && startYear != null) || (endDate != null && endYear != null)) {
            throw new CompileException("DATE_DIMENSION_CONFIG_INVALID");
        }
        if (startDate == null && startYear != null) startDate = LocalDate.of(startYear, 1, 1);
        if (endDate == null && endYear != null) endDate = LocalDate.of(endYear, 12, 31);
        if (startDate != null && endDate == null) {
            if (startDate.getYear() == 9999) throw new CompileException("DATE_DIMENSION_CONFIG_INVALID");
            endDate = LocalDate.of(startDate.getYear() + 1, 12, 31);
        }
        if (startDate != null && endDate != null) {
            long days = ChronoUnit.DAYS.between(startDate, endDate);
            if (days < 0 || days > DATE_DIMENSION_MAX_DAYS) {
                throw new CompileException("DATE_DIMENSION_CONFIG_INVALID");
            }
        }
        String startExpression = startDate == null
            ? "date_trunc('year', current_date)::date"
            : "date '" + startDate + "'";
        String endExpression = endDate == null
            ? "(date_trunc('year', current_date) + interval '2 years - 1 day')::date"
            : "date '" + endDate + "'";
        return new DateRange(startExpression, endExpression);
    }

    private static LocalDate configuredDate(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value == null) return null;
        if (!(value instanceof String text) || !text.matches("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")) {
            throw new CompileException("DATE_DIMENSION_CONFIG_INVALID");
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeException invalid) {
            throw new CompileException("DATE_DIMENSION_CONFIG_INVALID");
        }
    }

    private static Integer configuredYear(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value == null) return null;
        if (!(value instanceof Number number)) throw new CompileException("DATE_DIMENSION_CONFIG_INVALID");
        int year = number.intValue();
        if (number.doubleValue() != year || year < 1 || year > 9999) {
            throw new CompileException("DATE_DIMENSION_CONFIG_INVALID");
        }
        return year;
    }

    private record DateRange(String startExpression, String endExpression) {}

    private static String sourceFrom(ModelingCompilerContract.SourceRef source) {
        return source.kind().equalsIgnoreCase("DBT_MODEL")
            ? "from {{ ref('" + source.ref() + "') }}"
            : "from {{ source('" + sourceNamespace(source) + "', '" + sourceName(source) + "') }}";
    }

    private static List<ModelingCompilerContract.SourceRef> requireImplementationSources(ModelingCompilerContract.CompilerModel model) {
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
			return type + " JOIN";
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
                result.put(entry.getKey().toString(), entry.getValue().toString().toLowerCase());
            }
        }
        return result;
    }

    private static Map<String, ValidatedField> validateTypedFields(
        ModelSpecCompilerProjection.ImplementationProjection projection,
        List<String> columns
    ) {
        if (
            projection.typedFields() == null ||
            projection.typedFields().isEmpty()
        ) {
            throw new CompileException(
                "MODEL_IMPLEMENTATION_FIELD_TYPE_REQUIRED"
            );
        }
        Map<String, ValidatedField> fields =
            new java.util.LinkedHashMap<>();
        for (
            ModelSpecCompilerProjection.CompilerField field
            : projection.typedFields()
        ) {
            if (field == null || !identifier(field.name())) {
                throw new CompileException(
                    "MODEL_IMPLEMENTATION_FIELD_TYPE_COVERAGE_INVALID"
                );
            }
            ModelFieldPhysicalTypeContract.TypeDescriptor type;
            try {
                type = ModelFieldPhysicalTypeContract.requireSupported(
                    field.dataType()
                );
            } catch (IllegalArgumentException unsupported) {
                throw new CompileException(
                    "MODEL_IMPLEMENTATION_FIELD_TYPE_UNSUPPORTED"
                );
            }
            if (
                fields.putIfAbsent(
                    field.name(),
                    new ValidatedField(field, type)
                ) !=
                null
            ) {
                throw new CompileException(
                    "MODEL_IMPLEMENTATION_FIELD_TYPE_COVERAGE_INVALID"
                );
            }
        }
        if (
            fields.size() != columns.size() ||
            !fields.keySet().containsAll(columns)
        ) {
            throw new CompileException(
                "MODEL_IMPLEMENTATION_FIELD_TYPE_COVERAGE_INVALID"
            );
        }
        return Map.copyOf(fields);
    }

	private static String renderMappedColumn(String column, Map<String, String> mappings, Map<String, String> casts) {
		String expression = mappedExpression(column, mappings, casts);
		return "        " + expression + (expression.equals(column) ? "" : " as " + column);
	}

	private static String mappedExpression(String column, Map<String, String> mappings, Map<String, String> casts) {
		String expression = mappings.getOrDefault(column, column);
        String type = casts.get(column);
        if (type == null) type = casts.get(expression);
        if (type != null) expression = "cast(" + expression + " as " + POSTGRES_CAST_TYPES.get(type) + ")";
		return expression;
    }

	private static String renderProjectedColumn(
		String column,
		Map<String, String> mappings,
		Map<String, String> casts,
		Map<String, AggregationSpec> aggregations
	) {
		AggregationSpec aggregation = aggregations.get(column);
		if (aggregation == null) return renderMappedColumn(column, mappings, casts);
		String expression = aggregation.sqlExpression();
		String cast = casts.get(column);
		if (cast != null) expression = "cast(" + expression + " as " + POSTGRES_CAST_TYPES.get(cast) + ")";
		return "        " + expression + " as " + column;
	}

	private static List<String> filters(Map<String, Object> settings, Map<String, String> mappings, int sourceCount) {
		Object value = settings.get("filters");
		if (value == null) return List.of();
		if (!(value instanceof List<?> values) || values.isEmpty()) throw new CompileException("IMPLEMENTATION_FILTER_INVALID");
		List<String> result = new java.util.ArrayList<>();
		for (Object item : values) {
			if (!(item instanceof Map<?, ?> filter)) throw new CompileException("IMPLEMENTATION_FILTER_INVALID");
			String field = sourceExpression(text(filter.get("field")), mappings, sourceCount, "IMPLEMENTATION_FILTER_INVALID");
			String operator = text(filter.get("operator")).toUpperCase();
			String valueType = text(filter.get("valueType")).toUpperCase();
			if (!FILTER_OPERATORS.contains(operator) || !FILTER_VALUE_TYPES.contains(valueType)) {
				throw new CompileException("IMPLEMENTATION_FILTER_INVALID");
			}
			result.add(renderFilter(field, operator, valueType, filter.get("value")));
		}
		return List.copyOf(result);
	}

	private static String renderFilter(String field, String operator, String valueType, Object value) {
		return switch (operator) {
			case "IS_NULL" -> field + " is null";
			case "IS_NOT_NULL" -> field + " is not null";
			case "IN", "NOT_IN" -> {
				if (!(value instanceof List<?> values) || values.isEmpty()) throw new CompileException("IMPLEMENTATION_FILTER_INVALID");
				yield field + ("IN".equals(operator) ? " in (" : " not in (") + values
					.stream()
					.map(item -> sqlLiteral(valueType, item))
					.collect(Collectors.joining(", ")) + ")";
			}
			case "BETWEEN" -> {
				if (!(value instanceof List<?> values) || values.size() != 2) throw new CompileException("IMPLEMENTATION_FILTER_INVALID");
				yield field + " between " + sqlLiteral(valueType, values.get(0)) + " and " + sqlLiteral(valueType, values.get(1));
			}
			default -> field + " " + switch (operator) {
				case "EQ" -> "=";
				case "NE" -> "<>";
				case "GT" -> ">";
				case "GTE" -> ">=";
				case "LT" -> "<";
				case "LTE" -> "<=";
				default -> throw new CompileException("IMPLEMENTATION_FILTER_INVALID");
			} + " " + sqlLiteral(valueType, value);
		};
	}

	private static String sqlLiteral(String valueType, Object value) {
		try {
			return switch (valueType) {
				case "STRING" -> quote(requireTextValue(value));
				case "NUMBER" -> {
					if (!(value instanceof Number number)) throw new CompileException("IMPLEMENTATION_FILTER_VALUE_INVALID");
					yield new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
				}
				case "BOOLEAN" -> {
					if (!(value instanceof Boolean bool)) throw new CompileException("IMPLEMENTATION_FILTER_VALUE_INVALID");
					yield Boolean.toString(bool);
				}
				case "DATE" -> "date " + quote(LocalDate.parse(requireTextValue(value)).toString());
				case "TIMESTAMP" -> "cast(" + quote(validTimestamp(requireTextValue(value))) + " as timestamp)";
				default -> throw new CompileException("IMPLEMENTATION_FILTER_VALUE_INVALID");
			};
		} catch (DateTimeException | NumberFormatException invalid) {
			throw new CompileException("IMPLEMENTATION_FILTER_VALUE_INVALID");
		}
	}

	private static String requireTextValue(Object value) {
		if (!(value instanceof String text) || text.isBlank()) throw new CompileException("IMPLEMENTATION_FILTER_VALUE_INVALID");
		return text;
	}

	private static String validTimestamp(String value) {
		try {
			return OffsetDateTime.parse(value).toInstant().toString();
		} catch (DateTimeException ignored) {
			try {
				return Instant.parse(value).toString();
			} catch (DateTimeException alsoIgnored) {
				return LocalDateTime.parse(value).toString();
			}
		}
	}

	private static String quote(String value) {
		return "'" + value.replace("'", "''") + "'";
	}

	private static Map<String, AggregationSpec> aggregations(
		Map<String, Object> settings,
		List<String> columns,
		Map<String, String> mappings,
		int sourceCount
	) {
		Object value = settings.get("aggregations");
		List<String> groupBy = settingValues(settings, "groupBy");
		if (value == null && groupBy.isEmpty()) return Map.of();
		if (!(value instanceof List<?> values) || values.isEmpty() || groupBy.isEmpty()) {
			throw new CompileException("IMPLEMENTATION_AGGREGATION_INVALID");
		}
		Set<String> output = new LinkedHashSet<>(columns);
		if (groupBy.stream().anyMatch(field -> !output.contains(field))) {
			throw new CompileException("IMPLEMENTATION_GROUP_BY_INVALID");
		}
		Map<String, AggregationSpec> result = new TreeMap<>();
		for (Object item : values) {
			if (!(item instanceof Map<?, ?> raw)) throw new CompileException("IMPLEMENTATION_AGGREGATION_INVALID");
			String target = text(raw.get("targetField"));
			String function = text(raw.get("function")).toUpperCase();
			String source = sourceExpression(text(raw.get("sourceField")), mappings, sourceCount, "IMPLEMENTATION_AGGREGATION_INVALID");
			Object rawDistinct = raw.get("distinct");
			if (
				!identifier(target) ||
				!output.contains(target) ||
				groupBy.contains(target) ||
				!AGGREGATION_FUNCTIONS.contains(function) ||
				!(rawDistinct instanceof Boolean distinct) ||
				(distinct && !"COUNT".equals(function)) ||
				result.putIfAbsent(target, new AggregationSpec(function, source, distinct)) != null
			) {
				throw new CompileException("IMPLEMENTATION_AGGREGATION_INVALID");
			}
		}
		if (output.stream().anyMatch(field -> !groupBy.contains(field) && !result.containsKey(field))) {
			throw new CompileException("IMPLEMENTATION_AGGREGATION_COVERAGE_INVALID");
		}
		return Map.copyOf(result);
	}

	private static List<String> groupByExpressions(
		Map<String, Object> settings,
		List<String> columns,
		Map<String, String> mappings,
		Map<String, String> casts
	) {
		Set<String> requested = new LinkedHashSet<>(settingValues(settings, "groupBy"));
		return columns.stream().filter(requested::contains).map(column -> mappedExpression(column, mappings, casts)).toList();
	}

	private static String sourceExpression(
		String value,
		Map<String, String> mappings,
		int sourceCount,
		String errorCode
	) {
		String expression = mappings.getOrDefault(value, value);
		if (identifier(expression)) return expression;
		if (!SOURCE_FIELD.matcher(expression).matches() || sourceIndex(expression) >= sourceCount) {
			throw new CompileException(errorCode);
		}
		return expression;
	}

	private record AggregationSpec(String function, String sourceField, boolean distinct) {
		String sqlExpression() {
			if ("COUNT_DISTINCT".equals(function) || distinct) return "count(distinct " + sourceField + ")";
			return function.toLowerCase() + "(" + sourceField + ")";
		}
	}

    private static List<String> settingValues(Map<String, Object> settings, String key) {
        Object value = settings.get(key);
        if (!(value instanceof List<?> values)) return List.of();
        return values.stream().filter(Objects::nonNull).map(Object::toString).map(String::trim).filter(ModelingDbtCompiler::notBlank).toList();
    }

    private static ModelImplementationExecutionPlanner.ExecutionPlan validateImplementationProjection(
        ModelSpecCompilerProjection.ImplementationProjection projection
    ) {
        ModelImplementationExecutionPlanner.ValidationResult execution =
            ModelImplementationExecutionPlanner.plan(
                projection.keyFields(),
                projection.settings(),
                projection.materialization(),
                projection.dbtUniqueId(),
                ModelImplementationExecutionPlanner.DEFAULT_ADAPTER
            );
        if (!execution.valid()) throw new CompileException(execution.code());
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
                if (entry.getKey() == null || entry.getValue() == null || !identifier(entry.getKey().toString()) || !POSTGRES_CAST_TYPES.containsKey(entry.getValue().toString().toLowerCase())) {
                    throw new CompileException("IMPLEMENTATION_CAST_INVALID");
                }
            }
        }
        validateIdentifierSetting(projection.settings(), "deduplicateBy");
        validateIdentifierSetting(projection.settings(), "dedupBy");
        return execution.executionPlan();
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

    private static String renderSource(ModelingCompilerContract.SourceRef source) {
        if (source.kind().equalsIgnoreCase("DBT_MODEL")) return "-- source: ref(" + source.ref() + ")";
        return "-- source: " + source.ref();
    }

    private static String renderSchema(ModelingCompilerContract.CompilerModel model, List<String> columns) {
        return renderSchema(model, columns, model.name());
    }

    private static String renderSchema(ModelingCompilerContract.CompilerModel model, List<String> columns, String resourceName) {
        return renderSchema(model, columns, resourceName, false);
    }

    private static String renderRunnableSchema(
        ModelingCompilerContract.CompilerModel model,
        List<String> columns,
        Map<String, ValidatedField> fieldTypes,
        String resourceName
    ) {
        return renderSchema(
            model,
            columns,
            fieldTypes,
            resourceName,
            true
        );
    }

    private static String renderSchema(
        ModelingCompilerContract.CompilerModel model,
        List<String> columns,
        String resourceName,
        boolean includeModelTests
    ) {
        return renderSchema(
            model,
            columns,
            Map.of(),
            resourceName,
            includeModelTests
        );
    }

    private static String renderSchema(
        ModelingCompilerContract.CompilerModel model,
        List<String> columns,
        Map<String, ValidatedField> fieldTypes,
        String resourceName,
        boolean includeModelTests
    ) {
        StringBuilder yaml = new StringBuilder("version: 2\nmodels:\n  - name: ")
            .append(resourceName)
            .append("\n    description: \"")
            .append(escape(model.grain().statement()))
            .append("\"\n");
        if (includeModelTests) {
            String key = model.grain().keys().stream().filter(ModelingDbtCompiler::notBlank).findFirst().orElseThrow();
            yaml
                .append("    tests:\n      - unique:\n          column_name: ")
                .append(key)
                .append("\n");
        }
        yaml.append("    columns:\n");
        for (String column : columns) {
            String standard = model.standardBindings() == null
                ? null
                : model.standardBindings().stream().filter(binding -> binding != null && column.equals(binding.fieldName())).map(ModelingCompilerContract.StandardBinding::standardElementId).filter(ModelingDbtCompiler::notBlank).findFirst().orElse(null);
            yaml.append("      - name: ").append(column).append("\n        description: \"").append(escape(standard == null ? "模型字段" : "数据标准 " + standard)).append("\"\n");
            ValidatedField fieldType =
                fieldTypes.get(column);
            if (fieldType != null) {
                yaml
                    .append("        data_type: ")
                    .append(fieldType.type().postgresType())
                    .append("\n        meta:\n")
                    .append("          dts_logical_data_type: \"")
                    .append(escape(fieldType.type().logicalType()))
                    .append("\"\n")
                    .append("          dts_expected_physical_type: \"")
                    .append(escape(fieldType.type().postgresType()))
                    .append("\"\n")
                    .append("          dts_nullable: ")
                    .append(fieldType.field().nullable())
                    .append("\n");
            }
            if (model.grain().keys().stream().anyMatch(key -> column.equals(key))) {
                yaml.append("        tests:\n          - not_null\n");
            }
        }
        return yaml.toString();
    }

    private static String renderTests(ModelingCompilerContract.CompilerModel model) {
        return renderTests(model, model.name());
    }

    private static String renderTests(ModelingCompilerContract.CompilerModel model, String resourceName) {
        String key = model.grain().keys().stream().filter(ModelingDbtCompiler::notBlank).findFirst().orElseThrow();
        return "version: 2\nmodels:\n  - name: " + resourceName + "\n    tests:\n      - unique:\n          column_name: " + key + "\n    columns:\n      - name: " + key + "\n        tests:\n          - not_null\n";
    }

    private static String renderDocs(ModelingCompilerContract.CompilerModel model, List<String> columns) {
        return "# " + model.name() + "\n\n- 分层：" + model.layer() + "\n- 模型类型：" + model.modelType() + "\n- 业务粒度：" + model.grain().statement() + "\n- 字段：" + String.join(", ", columns) + "\n";
    }

    private static String sourceNamespace(ModelingCompilerContract.SourceRef source) {
        String ref = source.ref();
        int separator = ref.indexOf('.');
        return separator > 0 ? ref.substring(0, separator) : "ods";
    }

    private static String sourceName(ModelingCompilerContract.SourceRef source) {
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

    private record ValidatedField(
        ModelSpecCompilerProjection.CompilerField field,
        ModelFieldPhysicalTypeContract.TypeDescriptor type
    ) {}
}
