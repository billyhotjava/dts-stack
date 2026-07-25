package com.yuzhi.dts.platform.service.governance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.DbtProperties;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/**
 * dbt 指标模型自动生成���擎。
 * 根据 GovIndicatorDefinition 元数据自动生成 dbt SQL + schema.yml。
 */
@Service
public class DbtIndicatorGenerator {

	private static final Logger log = LoggerFactory.getLogger(DbtIndicatorGenerator.class);
	private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
	private static final Pattern SAFE_PATH_SEGMENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]*");
	private static final Pattern TEMPLATE_PLACEHOLDER = Pattern.compile("\\{([A-Za-z][A-Za-z0-9]*)}");
	private static final Pattern UNRESOLVED_PLACEHOLDER = Pattern.compile(
		"(?<!\\{)\\{[A-Za-z][A-Za-z0-9]*}(?!})");
	private static final Pattern ALLOWED_DBT_MACRO = Pattern.compile(
		"\\{\\{\\s*(?:ref\\('[A-Za-z_][A-Za-z0-9_]*'\\)|" +
		"source\\('[A-Za-z_][A-Za-z0-9_]*'\\s*,\\s*'[A-Za-z_][A-Za-z0-9_]*'\\))\\s*}}");
	private static final Pattern OWNED_SQL_PATH = Pattern.compile(
		"models/ads/[A-Za-z0-9][A-Za-z0-9_-]*/ind_[A-Za-z_][A-Za-z0-9_]*(?:_window)?\\.sql");
	private static final Pattern OWNED_SCHEMA_PATH = Pattern.compile(
		"models/ads/[A-Za-z0-9][A-Za-z0-9_-]*/[A-Za-z0-9][A-Za-z0-9_-]*_indicators_schema\\.yml");
	private static final String OWNERSHIP_MANIFEST_PATH = "models/ads/.dts-indicator-artifacts.json";
	private static final String ARTIFACT_LOCK_FILE = ".dts-indicator-artifacts.lock";
	private static final int OWNERSHIP_MANIFEST_VERSION = 2;
	private static final int LEGACY_OWNERSHIP_MANIFEST_VERSION = 1;
	private static final int MAX_OWNERSHIP_ENTRIES = 10_000;
	private static final int MAX_OWNERSHIP_MANIFEST_LENGTH = 65_536;
	private static final ReentrantLock ARTIFACT_TRANSACTION_LOCK = new ReentrantLock(true);
	private static final Set<String> ALLOWED_AGGREGATIONS = Set.of(
		"SUM", "COUNT", "COUNT_DISTINCT", "AVG", "MAX", "MIN", "RATIO", "CUSTOM");
	private static final Set<String> ALLOWED_JOIN_TYPES = Set.of("INNER", "LEFT", "RIGHT", "FULL");
	private static final Set<String> ALLOWED_LAYERS = Set.of("ODS", "DWD", "DWS", "ADS");
	private static final Set<String> ALLOWED_WINDOW_FUNCTIONS = Set.of("NONE", "YOY", "MOM", "YTD");
	private static final Set<String> ALLOWED_TIME_GRAINS = Set.of("DAY", "WEEK", "MONTH", "QUARTER", "YEAR");
	private static final int MAX_BATCH_SIZE = 100;
	private static final int MAX_DEPENDENCIES = 32;
	private static final int MAX_DIMENSIONS = 64;
	private static final int MAX_JOINS = 16;
	private static final int MAX_ARTIFACT_LENGTH = 1_048_576;
	private static final int MAX_JSON_LENGTH = 65_536;
	private static final int MAX_DEPENDENCY_JSON_LENGTH = 16_384;

	// ── SQL 模板 ──────────────────────────────────────────────

	private static final String SIMPLE_AGGREGATE_TEMPLATE = """
		-- 自动生成: {name} ({code})
		-- 生成时间: {generatedAt}

		SELECT
		{dimensionColumns}    DATE_TRUNC('{timeGrainUnit}', {dateColumn}) AS report_period,
		    {aggregationExpression} AS {code}
		FROM {sourceRef}
		{joinClause}WHERE 1=1
		{staticFilterClause}
		GROUP BY
		{dimensionGroupBy}    DATE_TRUNC('{timeGrainUnit}', {dateColumn})
		""";

	private static final String RATIO_TEMPLATE = """
		-- 自动生成: {name} ({code})
		-- 生成时间: {generatedAt}

		SELECT
		{dimensionColumns}    DATE_TRUNC('{timeGrainUnit}', {dateColumn}) AS report_period,
		    ({numeratorExpression}) AS _numerator,
		    ({denominatorExpression}) AS _denominator,
		    CASE
		        WHEN ({denominatorExpression}) = 0 THEN NULL
		        ELSE ROUND(
		            ({numeratorExpression})::numeric / ({denominatorExpression})::numeric,
		            {precisionScale}
		        )
		    END AS {code}
		FROM {sourceRef}
		{joinClause}WHERE 1=1
		{staticFilterClause}
		GROUP BY
		{dimensionGroupBy}    DATE_TRUNC('{timeGrainUnit}', {dateColumn})
		""";

	private static final String WINDOW_TEMPLATE_YOY = """
		-- 窗口函数(同比): {name} ({code})
		-- 生成时间: {generatedAt}

		SELECT
		    *,
		    LAG({code}, {periodsBack}) OVER (
		        PARTITION BY {partitionBy} ORDER BY report_period
		    ) AS {code}_prev_year,
		    CASE
		        WHEN LAG({code}, {periodsBack}) OVER (PARTITION BY {partitionBy} ORDER BY report_period) = 0 THEN NULL
		        ELSE ROUND(
		            ({code} - LAG({code}, {periodsBack}) OVER (PARTITION BY {partitionBy} ORDER BY report_period))::numeric
		            / NULLIF(LAG({code}, {periodsBack}) OVER (PARTITION BY {partitionBy} ORDER BY report_period), 0)::numeric,
		            4
		        )
		    END AS {code}_yoy
		FROM {baseRef}
		""";

	private static final String WINDOW_TEMPLATE_MOM = """
		-- 窗口函数(环比): {name} ({code})
		-- 生成时间: {generatedAt}

		SELECT
		    *,
		    LAG({code}, 1) OVER (
		        PARTITION BY {partitionBy} ORDER BY report_period
		    ) AS {code}_prev_period,
		    CASE
		        WHEN LAG({code}, 1) OVER (PARTITION BY {partitionBy} ORDER BY report_period) = 0 THEN NULL
		        ELSE ROUND(
		            ({code} - LAG({code}, 1) OVER (PARTITION BY {partitionBy} ORDER BY report_period))::numeric
		            / NULLIF(LAG({code}, 1) OVER (PARTITION BY {partitionBy} ORDER BY report_period), 0)::numeric,
		            4
		        )
		    END AS {code}_mom
		FROM {baseRef}
		""";

	private static final String WINDOW_TEMPLATE_YTD = """
		-- 窗口函数(累计): {name} ({code})
		-- 生成时间: {generatedAt}

		SELECT
		    *,
		    SUM({code}) OVER (
		        PARTITION BY {partitionBy}, EXTRACT(YEAR FROM report_period)
		        ORDER BY report_period
		    ) AS {code}_ytd
		FROM {baseRef}
		""";

	private static final String CUSTOM_TEMPLATE = """
		-- 自动生成(自定义): {name} ({code})
		-- 生成时间: {generatedAt}

		{expressionSql}
		""";

	private final GovIndicatorDefinitionRepository indicatorRepo;
	private final DbtProperties dbtProperties;
	private final ObjectMapper objectMapper;
	private final ControlledIndicatorDerivationCompiler derivationCompiler;
	private final IndicatorDerivationValidationService derivationValidationService;

	public DbtIndicatorGenerator(
			GovIndicatorDefinitionRepository indicatorRepo,
			DbtProperties dbtProperties,
			ObjectMapper objectMapper,
			ControlledIndicatorDerivationCompiler derivationCompiler,
			IndicatorDerivationValidationService derivationValidationService) {
		this.indicatorRepo = indicatorRepo;
		this.dbtProperties = dbtProperties;
		this.objectMapper = objectMapper;
		this.derivationCompiler = derivationCompiler;
		this.derivationValidationService = derivationValidationService;
	}

	// ── Public API ────────────────────────────────────────────

	/**
	 * 生成单个指标的 dbt SQL 文件。
	 */
	public String generate(UUID indicatorId) {
		return withArtifactTransaction(transaction -> {
			GovIndicatorDefinition def = indicatorRepo.findById(indicatorId)
				.orElseThrow(() -> new IllegalArgumentException("指标不存在: " + indicatorId));
			String relativePath = buildRelativePath(def);
			SqlArtifactPlan plan = planSqlArtifacts(List.of(def));
			try (
				ArtifactPromotion promotion = promoteArtifacts(
					transaction,
					plan.artifacts(),
					plan.deletions()
				)
			) {
				updateTargetModel(def);
				indicatorRepo.save(def);
				promotion.commit();
			}

			log.info("Generated dbt model: {} -> {}", def.getCode(), relativePath);
			return relativePath;
		});
	}

	/**
	 * 批量���成（拓扑排序后）。
	 */
	public List<String> generateBatch(List<UUID> indicatorIds) {
		validateIndicatorIds(indicatorIds);
		return withArtifactTransaction(transaction -> {
			List<UUID> sorted = topologicalSort(indicatorIds);
			List<GovIndicatorDefinition> definitions = loadDefinitions(sorted);
			SqlArtifactPlan plan = planSqlArtifacts(definitions);
			List<String> modelPaths = new ArrayList<>();
			for (GovIndicatorDefinition definition : definitions) {
				String modelPath = buildRelativePath(definition);
				modelPaths.add(modelPath);
			}
			try (
				ArtifactPromotion promotion = promoteArtifacts(
					transaction,
					plan.artifacts(),
					plan.deletions()
				)
			) {
				for (GovIndicatorDefinition definition : definitions) {
					updateTargetModel(definition);
					indicatorRepo.save(definition);
				}
				promotion.commit();
			}
			return List.copyOf(modelPaths);
		});
	}

	/**
	 * 生成 dbt 文件（不直接调 dbt run，也不改变指标发布生命周期）。
	 */
	public GenerationResult generateAndRun(List<UUID> indicatorIds) {
		validateIndicatorIds(indicatorIds);
		return withArtifactTransaction(transaction -> {
			List<UUID> sorted = topologicalSort(indicatorIds);
			List<GovIndicatorDefinition> definitions = loadDefinitions(sorted);
			SqlArtifactPlan plan = planSqlArtifacts(definitions);
			Map<String, String> artifacts = new LinkedHashMap<>(plan.artifacts());
			Set<String> deletions = new LinkedHashSet<>(plan.deletions());
			List<String> files = new ArrayList<>();
			for (GovIndicatorDefinition definition : definitions) {
				files.add(buildRelativePath(definition));
			}
			SchemaArtifactPlan schemaPlan = planSchemaArtifacts(
				definitions,
				plan.schemaDomains()
			);
			addArtifacts(artifacts, schemaPlan.artifacts());
			deletions.addAll(schemaPlan.deletions());
			deletions.removeAll(artifacts.keySet());
			try (
				ArtifactPromotion promotion = promoteArtifacts(
					transaction,
					artifacts,
					deletions
				)
			) {
				for (GovIndicatorDefinition definition : definitions) {
					updateTargetModel(definition);
					indicatorRepo.save(definition);
				}
				promotion.commit();
			}
			return new GenerationResult(indicatorIds.size(), List.copyOf(files), "READY", "PENDING", List.of());
		});
	}

	/**
	 * 预览 SQL（不写文件）。
	 */
	public Map<String, String> previewSql(UUID indicatorId) {
		GovIndicatorDefinition def = indicatorRepo.findById(indicatorId)
			.orElseThrow(() -> new IllegalArgumentException("指标不存在"));
		String sql = renderSql(def);
		Map<String, String> result = new LinkedHashMap<>();
		result.put("sql", sql);
		if (!"NONE".equals(normalizeWindowFunction(def.getWindowFunction()))) {
			result.put("windowSql", renderWindowSql(def));
		}
		return result;
	}

	/**
	 * 按 domain 生成 schema.yml。
	 */
	public void generateSchemaYml(List<UUID> indicatorIds) {
		validateIndicatorIds(indicatorIds);
		withArtifactTransaction(transaction -> {
			List<GovIndicatorDefinition> requested = loadDefinitions(indicatorIds);
			Set<String> domains = requested.stream()
				.map(this::normalizedDomain)
				.collect(Collectors.toCollection(LinkedHashSet::new));
			SchemaArtifactPlan plan = planSchemaArtifacts(requested, domains);
			try (
				ArtifactPromotion promotion = promoteArtifacts(
					transaction,
					plan.artifacts(),
					plan.deletions()
				)
			) {
				promotion.commit();
			}
			return null;
		});
	}

	private SchemaArtifactPlan planSchemaArtifacts(
		List<GovIndicatorDefinition> requested,
		Set<String> domains
	) {
		Map<String, GovIndicatorDefinition> definitions = new LinkedHashMap<>();
		Set<UUID> requestedIds = requested.stream()
			.map(GovIndicatorDefinition::getId)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());
		for (GovIndicatorDefinition definition : requested) {
			if (
				domains.contains(normalizedDomain(definition)) &&
				!"ARCHIVED".equalsIgnoreCase(definition.getStatus())
			) {
				definitions.put(indicatorIdentity(definition), definition);
			}
		}
		for (String domain : domains) {
			List<GovIndicatorDefinition> domainDefinitions = indicatorRepo.findByDomainIgnoreCase(domain);
			if (domainDefinitions == null) {
				domainDefinitions = List.of();
			}
			for (GovIndicatorDefinition definition : domainDefinitions) {
				if (
					definition != null &&
					!requestedIds.contains(definition.getId()) &&
					domain.equals(normalizedDomain(definition)) &&
					!"ARCHIVED".equalsIgnoreCase(definition.getStatus())
				) {
					definitions.putIfAbsent(indicatorIdentity(definition), definition);
				}
			}
			if ("general".equals(domain)) {
				List<GovIndicatorDefinition> allDefinitions = indicatorRepo.findAll();
				if (allDefinitions == null) {
					continue;
				}
				for (GovIndicatorDefinition definition : allDefinitions) {
					if (
						definition != null &&
						!requestedIds.contains(definition.getId()) &&
						isGeneralDomain(definition) &&
						!"ARCHIVED".equalsIgnoreCase(definition.getStatus())
					) {
						definitions.putIfAbsent(indicatorIdentity(definition), definition);
					}
				}
			}
		}
		Map<String, String> artifacts = renderSchemaArtifacts(List.copyOf(definitions.values()));
		Set<String> deletions = domains.stream()
			.map(this::schemaPathForDomain)
			.filter(path -> !artifacts.containsKey(path))
			.collect(Collectors.toCollection(LinkedHashSet::new));
		return new SchemaArtifactPlan(artifacts, Set.copyOf(deletions));
	}

	private boolean isGeneralDomain(GovIndicatorDefinition definition) {
		String domain = definition.getDomain();
		return !StringUtils.hasText(domain) || "general".equalsIgnoreCase(domain.trim());
	}

	private String indicatorIdentity(GovIndicatorDefinition definition) {
		if (definition.getId() != null) {
			return definition.getId().toString();
		}
		return normalizedDomain(definition) + ":" + requireSqlIdentifier(definition.getCode(), "指标编码");
	}

	private Map<String, String> renderSchemaArtifacts(List<GovIndicatorDefinition> definitions) {
		Map<String, List<GovIndicatorDefinition>> byDomain = new LinkedHashMap<>();
		for (GovIndicatorDefinition definition : definitions) {
			String domain = normalizedDomain(definition);
			byDomain.computeIfAbsent(domain, ignored -> new ArrayList<>()).add(definition);
		}
		Map<String, String> artifacts = new LinkedHashMap<>();
		for (var entry : byDomain.entrySet()) {
			StringBuilder yml = new StringBuilder();
			yml.append("version: 2\n\nmodels:\n");

			for (GovIndicatorDefinition def : entry.getValue()) {
				String indicatorCode = requireSqlIdentifier(def.getCode(), "指标编码");
				yml.append("  - name: ind_").append(indicatorCode).append("\n");
				String indicatorName = safeDisplayText(def.getName(), "指标名称");
				yml.append("    description: \"自动生成 - ").append(indicatorName).append("\"\n");
				yml.append("    columns:\n");

				// 指标列
				yml.append("      - name: ").append(indicatorCode).append("\n");
				yml.append("        description: \"").append(indicatorName);
				if (def.getUnit() != null) {
					yml.append(" (").append(safeDisplayText(def.getUnit(), "计量单位")).append(")");
				}
				yml.append("\"\n");

				// 阈值测试
				if (def.getThresholdMin() != null || def.getThresholdMax() != null) {
					yml.append("        tests:\n");
					if (def.getThresholdMin() != null) {
						yml.append("          - dbt_utils.accepted_range:\n");
						yml.append("              min_value: ").append(def.getThresholdMin()).append("\n");
					}
					if (def.getThresholdMax() != null) {
						yml.append("          - dbt_utils.accepted_range:\n");
						yml.append("              max_value: ").append(def.getThresholdMax()).append("\n");
					}
				}

				// report_period 列
				yml.append("      - name: report_period\n");
				yml.append("        description: \"报告周期\"\n");
				yml.append("        tests:\n");
				yml.append("          - not_null\n");

				// 维度列
				List<Map<String, String>> dims = parseDimensions(def.getDimensionFields());
				for (Map<String, String> dim : dims) {
					yml.append("      - name: ").append(dim.get("field")).append("\n");
					yml.append("        description: \"")
						.append(safeDisplayText(dim.getOrDefault("displayName", ""), "维度展示名"))
						.append("\"\n");
				}
			}

			String domain = entry.getKey();
			String path = schemaPathForDomain(domain);
			String content = yml.toString();
			validateRenderedArtifact(path, content);
			addArtifact(artifacts, path, content);
		}
		return artifacts;
	}

	// ── 拓扑排序 ─────────────────────────────────────────────

	public List<UUID> topologicalSort(List<UUID> indicatorIds) {
		validateIndicatorIds(indicatorIds);
		Map<String, UUID> codeToId = new LinkedHashMap<>();
		Map<UUID, List<String>> dependencies = new LinkedHashMap<>();

		for (UUID id : indicatorIds) {
			GovIndicatorDefinition def = indicatorRepo.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("指标不存在: " + id));
			String code = requireSqlIdentifier(def.getCode(), "指标编码");
			UUID existing = codeToId.putIfAbsent(code.toUpperCase(Locale.ROOT), id);
			if (existing != null) {
				throw new IllegalArgumentException("批量指标编码重复: " + code);
			}
			if (Boolean.TRUE.equals(def.getIsDerived()) && StringUtils.hasText(def.getDependencyIndicators())) {
				dependencies.put(id, parseDependencyCodes(def.getDependencyIndicators()));
			} else {
				dependencies.put(id, List.of());
			}
		}

		Map<UUID, Integer> inDegree = new LinkedHashMap<>();
		Map<UUID, List<UUID>> adjList = new LinkedHashMap<>();

		for (UUID id : indicatorIds) {
			inDegree.put(id, 0);
			adjList.put(id, new ArrayList<>());
		}

		for (var entry : dependencies.entrySet()) {
			for (String depCode : entry.getValue()) {
				UUID depId = codeToId.get(depCode.toUpperCase(Locale.ROOT));
				if (depId != null) {
					adjList.get(depId).add(entry.getKey());
					inDegree.merge(entry.getKey(), 1, Integer::sum);
				}
			}
		}

		Queue<UUID> queue = new LinkedList<>();
		for (var entry : inDegree.entrySet()) {
			if (entry.getValue() == 0) queue.add(entry.getKey());
		}

		List<UUID> sorted = new ArrayList<>();
		while (!queue.isEmpty()) {
			UUID curr = queue.poll();
			sorted.add(curr);
			for (UUID next : adjList.get(curr)) {
				inDegree.merge(next, -1, Integer::sum);
				if (inDegree.get(next) == 0) queue.add(next);
			}
		}

		if (sorted.size() < indicatorIds.size()) {
			throw new IllegalStateException("检测到循环依赖");
		}
		return sorted;
	}

	// ── 内部渲染 ─────────────────────────────────────────────

	private String renderSql(GovIndicatorDefinition def) {
		if (Boolean.TRUE.equals(def.getIsDerived())) {
			String rendered = renderDerivedSql(def);
			validateRenderedArtifact("preview.sql", rendered);
			return rendered;
		}
		String aggType = normalizeAggregation(def.getAggregationType());
		String template;
		if ("RATIO".equals(aggType)) {
			template = RATIO_TEMPLATE;
		} else if ("CUSTOM".equals(aggType)) {
			requireCurrentCustomValidation(def);
			template = CUSTOM_TEMPLATE;
		} else {
			template = SIMPLE_AGGREGATE_TEMPLATE;
		}
		String rendered = applyVars(template, buildVars(def, aggType));
		validateRenderedArtifact("preview.sql", rendered);
		return rendered;
	}

	private String renderDerivedSql(GovIndicatorDefinition target) {
		List<String> requestedDependencies = parseDependencyCodes(target.getDependencyIndicators());
		if (requestedDependencies.isEmpty()) {
			throw new IllegalArgumentException("派生指标至少需要一个依赖指标");
		}
		IndicatorDerivationValidationResult validation = derivationValidationService.validate(target.getId());
		if (!validation.valid()) {
			String message = validation.issues().stream()
				.map(IndicatorDerivationValidationResult.Issue::message)
				.collect(Collectors.joining("；"));
			throw new IllegalArgumentException("派生指标校验未通过：" + message);
		}
		List<String> dependencyCodes = validation.dependencyCodes().stream()
			.map(code -> requireSqlIdentifier(code, "依赖指标编码"))
			.toList();
		if (dependencyCodes.isEmpty()) {
			throw new IllegalArgumentException("派生指标至少需要一个依赖指标");
		}
		List<String> targetDimensions = dimensionFieldNames(target.getDimensionFields());
		Set<String> targetDimensionSet = normalizedFieldSet(targetDimensions);
		String targetTimeGrain = normalizeTimeGrain(target.getTimeGrain());
		Map<String, String> metricAliases = new LinkedHashMap<>();
		List<GovIndicatorDefinition> dependencies = new ArrayList<>();

		for (int index = 0; index < dependencyCodes.size(); index++) {
			String dependencyCode = requireSqlIdentifier(dependencyCodes.get(index), "依赖指标编码");
			GovIndicatorDefinition dependency = indicatorRepo.findFirstByCodeIgnoreCase(dependencyCode)
				.orElseThrow(() -> new IllegalArgumentException("依赖指标不存在: " + dependencyCode));
			if (!"PUBLISHED".equalsIgnoreCase(dependency.getStatus())) {
				throw new IllegalArgumentException("依赖指标尚未发布: " + dependencyCode);
			}
			if (!targetDimensionSet.equals(normalizedFieldSet(dimensionFieldNames(dependency.getDimensionFields())))
				|| !targetTimeGrain.equals(normalizeTimeGrain(dependency.getTimeGrain()))) {
				throw new IllegalArgumentException("依赖指标 " + dependencyCode + " 与目标指标的时间或维度粒度不一致");
			}
			dependencies.add(dependency);
			metricAliases.put(dependencyCode, "dep_" + index + "." + dependencyCode);
		}

		String targetCode = requireSqlIdentifier(target.getCode(), "目标指标编码");
		String compiledExpression = derivationCompiler.compile(target.getExpressionSql(), metricAliases);
		StringBuilder sql = new StringBuilder();
		sql.append("-- 自动生成(衍生): ")
			.append(safeDisplayText(target.getName(), "指标名称"))
			.append(" (")
			.append(targetCode)
			.append(")\n");
		sql.append("-- 生成时间: ").append(Instant.now()).append("\n\n");
		sql.append("SELECT\n");
		for (String dimension : targetDimensions) {
			String field = requireSqlIdentifier(dimension, "维度字段");
			sql.append("    dep_0.").append(field).append(" AS ").append(field).append(",\n");
		}
		sql.append("    dep_0.report_period AS report_period,\n");
		sql.append("    ").append(compiledExpression).append(" AS ").append(targetCode).append("\n");
		sql.append("FROM {{ ref('ind_").append(dependencyCodes.get(0)).append("') }} AS dep_0\n");
		for (int index = 1; index < dependencies.size(); index++) {
			String dependencyCode = requireSqlIdentifier(dependencyCodes.get(index), "依赖指标编码");
			sql.append("JOIN {{ ref('ind_").append(dependencyCode).append("') }} AS dep_").append(index).append("\n");
			sql.append("    ON dep_").append(index).append(".report_period = dep_0.report_period");
			for (String dimension : targetDimensions) {
				String field = requireSqlIdentifier(dimension, "维度字段");
				sql.append("\n    AND dep_").append(index).append(".").append(field)
					.append(" = dep_0.").append(field);
			}
			sql.append("\n");
		}
		return sql.toString();
	}

	private String renderWindowSql(GovIndicatorDefinition def) {
		String wf = normalizeWindowFunction(def.getWindowFunction());
		if ("NONE".equals(wf)) {
			throw new IllegalArgumentException("未配置窗口函数");
		}
		String template = switch (wf) {
			case "YOY" -> WINDOW_TEMPLATE_YOY;
			case "MOM" -> WINDOW_TEMPLATE_MOM;
			case "YTD" -> WINDOW_TEMPLATE_YTD;
			default -> throw new IllegalArgumentException("窗口函数不合法: " + wf);
		};
		String aggregation = Boolean.TRUE.equals(def.getIsDerived())
			? "SUM"
			: normalizeAggregation(def.getAggregationType());
		Map<String, String> vars = buildVars(def, aggregation);
		String indicatorCode = requireSqlIdentifier(def.getCode(), "指标编码");
		vars.put("baseRef", "{{ ref('ind_" + indicatorCode + "') }}");
		vars.put("periodsBack", "YOY".equals(wf) ? "12" : "1");

		List<Map<String, String>> dims = parseDimensions(def.getDimensionFields());
		String partitionBy = dims.isEmpty() ? "1"
			: dims.stream().map(d -> d.get("field")).collect(Collectors.joining(", "));
		vars.put("partitionBy", partitionBy);

		String rendered = applyVars(template, vars);
		validateRenderedArtifact("preview_window.sql", rendered);
		return rendered;
	}

	private Map<String, String> buildVars(GovIndicatorDefinition def, String aggregation) {
		boolean derived = Boolean.TRUE.equals(def.getIsDerived());
		String measureField = derived
			? requireSqlIdentifier(def.getCode(), "指标编码")
			: requireSqlIdentifier(def.getMeasureField(), "度量字段");
		String dateColumn = requireSqlIdentifier(
			StringUtils.hasText(def.getDateColumn()) ? def.getDateColumn() : "created_date",
			"日期字段");
		String numerator = "RATIO".equals(aggregation)
			? validateArithmeticExpression(def.getNumeratorExpression(), "分子表达式")
			: "0";
		String denominator = "RATIO".equals(aggregation)
			? validateArithmeticExpression(def.getDenominatorExpression(), "分母表达式")
			: "1";
		int precision = def.getPrecisionScale() != null ? def.getPrecisionScale() : 2;
		if (precision < 0 || precision > 12) {
			throw new IllegalArgumentException("小数位数必须在 0 到 12 之间");
		}
		if (StringUtils.hasText(def.getTargetLayer())) {
			normalizeLayer(def.getTargetLayer(), "ADS", "目标分层");
		}
		Map<String, String> v = new LinkedHashMap<>();
		v.put("name", safeDisplayText(def.getName(), "指标名称"));
		v.put("code", requireSqlIdentifier(def.getCode(), "指标编码"));
		v.put("generatedAt", Instant.now().toString());
		v.put("aggregationExpression", buildAggregationExpression(aggregation, measureField));
		v.put("measureField", measureField);
		v.put("numeratorExpression", numerator);
		v.put("denominatorExpression", denominator);
		v.put("precisionScale", String.valueOf(precision));
		v.put("dateColumn", dateColumn);
		v.put("timeGrainUnit", mapTimeGrainUnit(def.getTimeGrain()));
		v.put("expressionSql", "CUSTOM".equals(aggregation) ? validateCustomExpression(def.getExpressionSql()) : "NULL");
		v.put("sourceRef", derived ? "" : buildSourceRef(def));
		v.put("dimensionColumns", buildDimensionColumns(def));
		v.put("dimensionGroupBy", buildDimensionGroupBy(def));
		v.put("joinClause", derived ? "" : buildJoinClause(def));
		v.put("staticFilterClause", !derived && StringUtils.hasText(def.getStaticFilter())
			? "    AND " + validatePredicate(def.getStaticFilter(), "固定过滤条件") + "\n"
			: "");
		return v;
	}

	private String applyVars(String template, Map<String, String> vars) {
		Matcher matcher = TEMPLATE_PLACEHOLDER.matcher(template);
		//StringBuffer result = new StringBuffer();
		StringBuilder result = new StringBuilder();
		while (matcher.find()) {
			String value = vars.get(matcher.group(1));
			if (value == null) {
				throw new IllegalArgumentException("模板变量未提供: " + matcher.group(1));
			}
			matcher.appendReplacement(result, Matcher.quoteReplacement(value));
		}
		matcher.appendTail(result);
		return result.toString();
	}

	private String mapTimeGrainUnit(String grain) {
		String normalized = StringUtils.hasText(grain) ? grain.trim().toUpperCase(Locale.ROOT) : "MONTH";
		if (!ALLOWED_TIME_GRAINS.contains(normalized)) {
			throw new IllegalArgumentException("时间粒度不合法: " + normalized);
		}
		return switch (normalized) {
			case "DAY" -> "day";
			case "WEEK" -> "week";
			case "MONTH" -> "month";
			case "QUARTER" -> "quarter";
			case "YEAR" -> "year";
			default -> throw new IllegalArgumentException("时间粒度不合法: " + normalized);
		};
	}

	private String buildSourceRef(GovIndicatorDefinition def) {
		String table = requireSqlIdentifier(def.getSourceTable(), "来源表");
		String layer = normalizeLayer(def.getSourceLayer(), "DWD", "来源分层");
		if ("ODS".equals(layer)) {
			return "{{ source('public', '" + table + "') }}";
		}
		return "{{ ref('" + table + "') }}";
	}

	private String buildDimensionColumns(GovIndicatorDefinition def) {
		List<Map<String, String>> dims = parseDimensions(def.getDimensionFields());
		if (dims.isEmpty()) return "";
		StringBuilder sb = new StringBuilder();
		for (Map<String, String> dim : dims) {
			sb.append("    ").append(requireSqlIdentifier(dim.get("field"), "维度字段")).append(",\n");
		}
		return sb.toString();
	}

	private String buildDimensionGroupBy(GovIndicatorDefinition def) {
		List<Map<String, String>> dims = parseDimensions(def.getDimensionFields());
		if (dims.isEmpty()) return "";
		StringBuilder sb = new StringBuilder();
		for (Map<String, String> dim : dims) {
			sb.append("    ").append(requireSqlIdentifier(dim.get("field"), "维度字段")).append(",\n");
		}
		return sb.toString();
	}

	private String buildJoinClause(GovIndicatorDefinition def) {
		if (!StringUtils.hasText(def.getJoinConfig())) return "";
		try {
			List<Object> joins = readJsonStrict(
				def.getJoinConfig(),
				new TypeReference<List<Object>>() {},
				"joinConfig");
			if (joins.size() > MAX_JOINS) {
				throw new IllegalArgumentException("joinConfig 最多允许 " + MAX_JOINS + " 个关联");
			}
			StringBuilder sb = new StringBuilder();
			Set<String> availableAliases = new LinkedHashSet<>();
			availableAliases.add(requireSqlIdentifier(def.getSourceTable(), "来源表").toLowerCase(Locale.ROOT));
			for (Object item : joins) {
				if (!(item instanceof Map<?, ?> raw)) {
					throw new IllegalArgumentException("joinConfig 的每个关联必须是对象");
				}
				Set<String> allowedKeys = Set.of("type", "table", "alias", "on");
				for (Object key : raw.keySet()) {
					if (!(key instanceof String text) || !allowedKeys.contains(text)) {
						throw new IllegalArgumentException("joinConfig 包含不支持的字段: " + key);
					}
				}
				String type = requireJsonText(raw, "type", false, "LEFT").toUpperCase(Locale.ROOT);
				if (!ALLOWED_JOIN_TYPES.contains(type)) {
					throw new IllegalArgumentException("关联类型不合法: " + type);
				}
				String table = requireSqlIdentifier(requireJsonText(raw, "table", true, null), "关联表");
				String alias = requireSqlIdentifier(requireJsonText(raw, "alias", false, table), "关联别名");
				String normalizedAlias = alias.toLowerCase(Locale.ROOT);
				if (!availableAliases.add(normalizedAlias)) {
					throw new IllegalArgumentException("关联别名重复: " + alias);
				}
				String on = validateJoinCondition(
					requireJsonText(raw, "on", true, null),
					availableAliases,
					normalizedAlias);
				sb.append(type).append(" JOIN {{ ref('").append(table).append("') }} AS ")
				  .append(alias).append(" ON ").append(on).append("\n");
				availableAliases.add(table.toLowerCase(Locale.ROOT));
			}
			return sb.toString();
		} catch (IllegalArgumentException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalArgumentException("joinConfig 不是合法的关联数组", e);
		}
	}

	// ── JSON 解析帮助 ────────────────────────────────────────

	private List<Map<String, String>> parseDimensions(String json) {
		if (!StringUtils.hasText(json)) return List.of();
		try {
			List<Object> raw = readJsonStrict(
				json,
				new TypeReference<List<Object>>() {},
				"dimensionFields");
			if (raw.size() > MAX_DIMENSIONS) {
				throw new IllegalArgumentException("dimensionFields 最多允许 " + MAX_DIMENSIONS + " 个维度");
			}
			List<Map<String, String>> dimensions = new ArrayList<>();
			Set<String> seenFields = new LinkedHashSet<>();
			for (Object item : raw) {
				if (item instanceof String field && StringUtils.hasText(field)) {
					String normalized = requireSqlIdentifier(field, "维度字段");
					if (!seenFields.add(normalized.toLowerCase(Locale.ROOT))) {
						throw new IllegalArgumentException("维度字段重复: " + normalized);
					}
					dimensions.add(Map.of("field", normalized));
				} else if (item instanceof Map<?, ?> map) {
					for (Object key : map.keySet()) {
						if (!(key instanceof String text) || (!"field".equals(text) && !"displayName".equals(text))) {
							throw new IllegalArgumentException("dimensionFields 包含不支持的字段: " + key);
						}
					}
					if (!(map.get("field") instanceof String field) || !StringUtils.hasText(field)) {
						throw new IllegalArgumentException("dimensionFields 的 field 必须是非空字符串");
					}
					String normalized = requireSqlIdentifier(field, "维度字段");
					if (!seenFields.add(normalized.toLowerCase(Locale.ROOT))) {
						throw new IllegalArgumentException("维度字段重复: " + normalized);
					}
					Map<String, String> dimension = new LinkedHashMap<>();
					dimension.put("field", normalized);
					if (map.containsKey("displayName")) {
						if (!(map.get("displayName") instanceof String displayName)) {
							throw new IllegalArgumentException("维度展示名必须是字符串");
						}
						safeDisplayText(displayName, "维度展示名");
						dimension.put("displayName", displayName);
					}
					dimensions.add(dimension);
				} else {
					throw new IllegalArgumentException("dimensionFields 必须是维度字符串或对象数组");
				}
			}
			return List.copyOf(dimensions);
		} catch (IllegalArgumentException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalArgumentException("dimensionFields 不是合法的维度数组", e);
		}
	}

	private List<String> dimensionFieldNames(String json) {
		return parseDimensions(json).stream().map(item -> item.get("field")).toList();
	}

	private Set<String> normalizedFieldSet(List<String> fields) {
		LinkedHashSet<String> normalized = new LinkedHashSet<>();
		for (String field : fields) {
			normalized.add(requireSqlIdentifier(field, "维度字段").toLowerCase(Locale.ROOT));
		}
		return Set.copyOf(normalized);
	}

	private String normalizeTimeGrain(String value) {
		String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "MONTH";
		if (!ALLOWED_TIME_GRAINS.contains(normalized)) {
			throw new IllegalArgumentException("时间粒度不合法: " + normalized);
		}
		return normalized;
	}

	private String requireSqlIdentifier(String value, String label) {
		String candidate = value == null ? "" : value.trim();
		if (!SQL_IDENTIFIER.matcher(candidate).matches()) {
			throw new IllegalArgumentException(label + "不合法: " + candidate);
		}
		return candidate;
	}

	private List<String> parseDependencyCodes(String json) {
		if (!StringUtils.hasText(json)) return List.of();
		if (json.length() > MAX_DEPENDENCY_JSON_LENGTH) {
			throw new IllegalArgumentException(
				"dependencyIndicators 长度不能超过 " + MAX_DEPENDENCY_JSON_LENGTH);
		}
		try {
			List<Object> raw = readJsonStrict(
				json,
				new TypeReference<List<Object>>() {},
				"dependencyIndicators");
			if (raw.size() > MAX_DEPENDENCIES) {
				throw new IllegalArgumentException("dependencyIndicators 最多允许 " + MAX_DEPENDENCIES + " 个依赖指标");
			}
			List<String> result = new ArrayList<>();
			Set<String> seen = new LinkedHashSet<>();
			for (Object item : raw) {
				if (!(item instanceof String code) || !StringUtils.hasText(code)) {
					throw new IllegalArgumentException("dependencyIndicators 必须是非空指标编码数组");
				}
				String normalized = requireSqlIdentifier(code, "依赖指标编码");
				if (!seen.add(normalized.toUpperCase(Locale.ROOT))) {
					throw new IllegalArgumentException("dependencyIndicators 包含重复指标: " + normalized);
				}
				result.add(normalized);
			}
			return List.copyOf(result);
		} catch (IllegalArgumentException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalArgumentException("dependencyIndicators 不是合法的指标编码数组", e);
		}
	}

	private <T> T readJsonStrict(String json, TypeReference<T> type, String label) {
		if (json == null || json.length() > MAX_JSON_LENGTH) {
			throw new IllegalArgumentException(label + "长度超过限制");
		}
		try {
			return objectMapper
				.readerFor(type)
				.with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
				.readValue(json);
		} catch (Exception ex) {
			throw new IllegalArgumentException(label + "不是合法 JSON", ex);
		}
	}

	private Map<String, String> renderIndicatorArtifacts(GovIndicatorDefinition definition) {
		Map<String, String> artifacts = new LinkedHashMap<>();
		String modelPath = buildRelativePath(definition);
		addArtifact(artifacts, modelPath, renderSql(definition));
		if (!"NONE".equals(normalizeWindowFunction(definition.getWindowFunction()))) {
			String windowPath = modelPath.substring(0, modelPath.length() - ".sql".length()) + "_window.sql";
			addArtifact(artifacts, windowPath, renderWindowSql(definition));
		}
		return artifacts;
	}

	private SqlArtifactPlan planSqlArtifacts(List<GovIndicatorDefinition> definitions) {
		Map<String, OwnedArtifacts> ownership = loadOwnershipManifest();
		Map<String, String> artifacts = new LinkedHashMap<>();
		Set<String> deletions = new LinkedHashSet<>();
		Set<String> schemaDomains = new LinkedHashSet<>();
		for (GovIndicatorDefinition definition : definitions) {
			if (definition.getId() == null) {
				throw new IllegalArgumentException("指标缺少持久化 ID，不能生成受管产物");
			}
			Map<String, String> rendered = renderIndicatorArtifacts(definition);
			addArtifacts(artifacts, rendered);
			String modelPath = buildRelativePath(definition);
			String windowPath = rendered.keySet().stream()
				.filter(path -> path.endsWith("_window.sql"))
				.findFirst()
				.orElse(null);
			String key = definition.getId().toString();
			OwnedArtifacts previous = ownership.get(key);
			if (previous != null) {
				addStalePath(deletions, previous.modelPath(), modelPath);
				addStalePath(deletions, previous.windowPath(), windowPath);
				schemaDomains.add(previous.domain());
			} else {
				for (String legacyPath : discoverLegacyOwnedSqlPaths(definition)) {
					addStalePath(
						deletions,
						legacyPath,
						legacyPath.endsWith("_window.sql") ? windowPath : modelPath
					);
					schemaDomains.add(domainFromOwnedSqlPath(legacyPath));
				}
			}
			String domain = normalizedDomain(definition);
			schemaDomains.add(domain);
			ownership.put(
				key,
				new OwnedArtifacts(modelPath, windowPath, domain, schemaPathForDomain(domain))
			);
		}

		Set<String> stillOwned = new LinkedHashSet<>();
		for (OwnedArtifacts value : ownership.values()) {
			if (value.modelPath() != null) stillOwned.add(value.modelPath());
			if (value.windowPath() != null) stillOwned.add(value.windowPath());
		}
		deletions.removeAll(stillOwned);
		deletions.removeAll(artifacts.keySet());
		addArtifact(artifacts, OWNERSHIP_MANIFEST_PATH, renderOwnershipManifest(ownership));
		return new SqlArtifactPlan(
			artifacts,
			Set.copyOf(deletions),
			Set.copyOf(schemaDomains)
		);
	}

	private void addStalePath(Set<String> deletions, String previous, String current) {
		if (StringUtils.hasText(previous) && !previous.equals(current)) {
			deletions.add(requireOwnedSqlPath(previous));
		}
	}

	private Set<String> discoverLegacyOwnedSqlPaths(GovIndicatorDefinition definition) {
		String code = requireSqlIdentifier(definition.getCode(), "指标编码");
		Path adsRoot = projectPath().resolve("models/ads").normalize();
		if (!Files.exists(adsRoot, LinkOption.NOFOLLOW_LINKS)) {
			return Set.of();
		}
		if (
			Files.isSymbolicLink(adsRoot) ||
			!Files.isDirectory(adsRoot, LinkOption.NOFOLLOW_LINKS)
		) {
			throw new IllegalArgumentException("dbt 指标 ADS 目录不合法");
		}
		Set<String> discovered = new LinkedHashSet<>();
		try (var domains = Files.list(adsRoot)) {
			for (Path domainPath : domains.sorted().toList()) {
				String domain = domainPath.getFileName().toString();
				if (
					!SAFE_PATH_SEGMENT.matcher(domain).matches() ||
					Files.isSymbolicLink(domainPath) ||
					!Files.isDirectory(domainPath, LinkOption.NOFOLLOW_LINKS)
				) {
					continue;
				}
				for (String suffix : List.of(".sql", "_window.sql")) {
					String relativePath =
						"models/ads/" + domain + "/ind_" + code + suffix;
					Path candidate = projectPath().resolve(relativePath).normalize();
					if (Files.isSymbolicLink(candidate)) {
						throw new IllegalArgumentException("存量指标产物不能是符号链接: " + relativePath);
					}
					if (
						Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS) &&
						isLegacyGeneratedSql(candidate, code, suffix.startsWith("_window"))
					) {
						discovered.add(requireOwnedSqlPath(relativePath));
					}
				}
			}
		} catch (IOException failure) {
			throw new RuntimeException("扫描存量 dbt 指标产物失败", failure);
		}
		return Set.copyOf(discovered);
	}

	private boolean isLegacyGeneratedSql(Path candidate, String code, boolean window)
		throws IOException {
		if (Files.size(candidate) > MAX_ARTIFACT_LENGTH) {
			return false;
		}
		String firstLine;
		try (var lines = Files.lines(candidate)) {
			firstLine = lines.findFirst().orElse("");
		}
		String expectedPrefix = window ? "-- 窗口函数(" : "-- 自动生成";
		return firstLine.startsWith(expectedPrefix) && firstLine.endsWith("(" + code + ")");
	}

	private Map<String, OwnedArtifacts> loadOwnershipManifest() {
		Path manifest = projectPath().resolve(OWNERSHIP_MANIFEST_PATH).normalize();
		if (!Files.exists(manifest, LinkOption.NOFOLLOW_LINKS)) {
			return new LinkedHashMap<>();
		}
		if (Files.isSymbolicLink(manifest) || !Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS)) {
			throw new IllegalArgumentException("指标产物 ownership manifest 不是普通文件");
		}
		try {
			if (Files.size(manifest) > MAX_OWNERSHIP_MANIFEST_LENGTH) {
				throw new IllegalArgumentException("指标产物 ownership manifest 长度超过限制");
			}
			Map<String, Object> root = readJsonStrict(
				Files.readString(manifest),
				new TypeReference<Map<String, Object>>() {},
				"指标产物 ownership manifest");
			if (!root.keySet().equals(Set.of("version", "indicators"))) {
				throw new IllegalArgumentException("指标产物 ownership manifest 字段不合法");
			}
			Object version = root.get("version");
			if (
				!(version instanceof Number number) ||
				(
					number.intValue() != LEGACY_OWNERSHIP_MANIFEST_VERSION &&
					number.intValue() != OWNERSHIP_MANIFEST_VERSION
				)
			) {
				throw new IllegalArgumentException("指标产物 ownership manifest 版本不支持");
			}
			if (!(root.get("indicators") instanceof Map<?, ?> rawIndicators)) {
				throw new IllegalArgumentException("指标产物 ownership manifest.indicators 不合法");
			}
			if (rawIndicators.size() > MAX_OWNERSHIP_ENTRIES) {
				throw new IllegalArgumentException("指标产物 ownership manifest 条目超过限制");
			}
			Map<String, OwnedArtifacts> result = new LinkedHashMap<>();
			for (var entry : rawIndicators.entrySet()) {
				if (!(entry.getKey() instanceof String key)) {
					throw new IllegalArgumentException("指标产物 ownership manifest 指标 ID 不合法");
				}
				UUID.fromString(key);
				if (!(entry.getValue() instanceof Map<?, ?> rawValue)) {
					throw new IllegalArgumentException("指标产物 ownership manifest 条目不合法: " + key);
				}
				if (!rawValue.keySet().stream().allMatch(field ->
					"modelPath".equals(field) ||
					"windowPath".equals(field) ||
					"domain".equals(field) ||
					"schemaPath".equals(field))) {
					throw new IllegalArgumentException("指标产物 ownership manifest 路径字段不合法");
				}
				String modelPath = requireManifestPath(rawValue.get("modelPath"), true);
				String windowPath = requireManifestPath(rawValue.get("windowPath"), false);
				String inferredDomain = domainFromOwnedSqlPath(modelPath);
				String domain = requireManifestDomain(rawValue.get("domain"), inferredDomain);
				String schemaPath = requireManifestSchemaPath(
					rawValue.get("schemaPath"),
					domain
				);
				result.put(
					key,
					new OwnedArtifacts(modelPath, windowPath, domain, schemaPath)
				);
			}
			return result;
		} catch (IllegalArgumentException invalid) {
			throw invalid;
		} catch (Exception ex) {
			throw new IllegalArgumentException("读取指标产物 ownership manifest 失败", ex);
		}
	}

	private String requireManifestPath(Object raw, boolean required) {
		if (raw == null && !required) {
			return null;
		}
		if (!(raw instanceof String path) || !StringUtils.hasText(path)) {
			throw new IllegalArgumentException("指标产物 ownership manifest 路径不合法");
		}
		return requireOwnedSqlPath(path);
	}

	private String requireManifestDomain(Object raw, String inferredDomain) {
		if (raw == null) {
			return inferredDomain;
		}
		if (!(raw instanceof String domain)) {
			throw new IllegalArgumentException("指标产物 ownership manifest domain 不合法");
		}
		String normalized = requirePathSegment(domain, "指标产物 ownership manifest domain");
		if (!normalized.equals(inferredDomain)) {
			throw new IllegalArgumentException("指标产物 ownership manifest domain 与 modelPath 不一致");
		}
		return normalized;
	}

	private String requireManifestSchemaPath(Object raw, String domain) {
		String expected = schemaPathForDomain(domain);
		if (raw == null) {
			return expected;
		}
		if (!(raw instanceof String schemaPath)) {
			throw new IllegalArgumentException("指标产物 ownership manifest schemaPath 不合法");
		}
		String normalized = requireOwnedSchemaPath(schemaPath);
		if (!normalized.equals(expected)) {
			throw new IllegalArgumentException("指标产物 ownership manifest schemaPath 与 domain 不一致");
		}
		return normalized;
	}

	private String requireOwnedSqlPath(String path) {
		String normalized = Path.of(path).normalize().toString().replace('\\', '/');
		if (!normalized.equals(path) || !OWNED_SQL_PATH.matcher(normalized).matches()) {
			throw new IllegalArgumentException("指标产物 ownership 路径不合法: " + path);
		}
		return normalized;
	}

	private String domainFromOwnedSqlPath(String path) {
		String normalized = requireOwnedSqlPath(path);
		return Path.of(normalized).getName(2).toString();
	}

	private String requireOwnedSchemaPath(String path) {
		String normalized = Path.of(path).normalize().toString().replace('\\', '/');
		if (!normalized.equals(path) || !OWNED_SCHEMA_PATH.matcher(normalized).matches()) {
			throw new IllegalArgumentException("指标产物 ownership schema 路径不合法: " + path);
		}
		return normalized;
	}

	private String requireOwnedArtifactPath(String path) {
		String normalized = Path.of(path).normalize().toString().replace('\\', '/');
		if (OWNED_SQL_PATH.matcher(normalized).matches()) {
			return requireOwnedSqlPath(path);
		}
		return requireOwnedSchemaPath(path);
	}

	private String renderOwnershipManifest(Map<String, OwnedArtifacts> ownership) {
		Map<String, Object> indicators = new LinkedHashMap<>();
		ownership.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.forEach(entry -> {
				Map<String, Object> value = new LinkedHashMap<>();
				value.put("modelPath", requireOwnedSqlPath(entry.getValue().modelPath()));
				value.put(
					"windowPath",
					entry.getValue().windowPath() == null
						? null
						: requireOwnedSqlPath(entry.getValue().windowPath()));
				value.put("domain", requirePathSegment(entry.getValue().domain(), "指标域"));
				value.put(
					"schemaPath",
					requireOwnedSchemaPath(entry.getValue().schemaPath())
				);
				indicators.put(entry.getKey(), value);
			});
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("version", OWNERSHIP_MANIFEST_VERSION);
		root.put("indicators", indicators);
		try {
			String rendered = objectMapper.writeValueAsString(root);
			if (rendered.length() > MAX_OWNERSHIP_MANIFEST_LENGTH) {
				throw new IllegalArgumentException("指标产物 ownership manifest 长度超过限制");
			}
			return rendered;
		} catch (IllegalArgumentException invalid) {
			throw invalid;
		} catch (Exception ex) {
			throw new IllegalArgumentException("序列化指标产物 ownership manifest 失败", ex);
		}
	}

	private List<GovIndicatorDefinition> loadDefinitions(List<UUID> indicatorIds) {
		List<GovIndicatorDefinition> definitions = new ArrayList<>();
		for (UUID id : indicatorIds) {
			definitions.add(
				indicatorRepo.findById(id)
					.orElseThrow(() -> new IllegalArgumentException("指标不存在: " + id))
			);
		}
		return List.copyOf(definitions);
	}

	private void validateIndicatorIds(List<UUID> indicatorIds) {
		if (indicatorIds == null) {
			throw new IllegalArgumentException("indicatorIds 不能为空");
		}
		if (indicatorIds.size() > MAX_BATCH_SIZE) {
			throw new IllegalArgumentException("单次最多生成 " + MAX_BATCH_SIZE + " 个指标");
		}
		Set<UUID> unique = new LinkedHashSet<>();
		for (UUID id : indicatorIds) {
			if (id == null) {
				throw new IllegalArgumentException("indicatorIds 不能包含空值");
			}
			if (!unique.add(id)) {
				throw new IllegalArgumentException("indicatorIds 不能包含重复指标: " + id);
			}
		}
	}

	private void updateTargetModel(GovIndicatorDefinition definition) {
		definition.setTargetModelName("ind_" + requireSqlIdentifier(definition.getCode(), "指标编码"));
	}

	private void addArtifacts(Map<String, String> target, Map<String, String> additions) {
		for (var entry : additions.entrySet()) {
			addArtifact(target, entry.getKey(), entry.getValue());
		}
	}

	private void addArtifact(Map<String, String> artifacts, String path, String content) {
		if (artifacts.putIfAbsent(path, content) != null) {
			throw new IllegalArgumentException("生成目标文件重复: " + path);
		}
	}

	private String normalizedDomain(GovIndicatorDefinition definition) {
		return requirePathSegment(
			StringUtils.hasText(definition.getDomain())
				? definition.getDomain().trim().toLowerCase(Locale.ROOT)
				: "general",
			"指标域");
	}

	private String schemaPathForDomain(String domain) {
		String safeDomain = requirePathSegment(domain, "指标域");
		return requireOwnedSchemaPath(
			"models/ads/" + safeDomain + "/" + safeDomain + "_indicators_schema.yml"
		);
	}

	private String normalizeAggregation(String value) {
		String aggregation = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "SUM";
		if (!ALLOWED_AGGREGATIONS.contains(aggregation)) {
			throw new IllegalArgumentException("聚合方式不合法: " + aggregation);
		}
		return aggregation;
	}

	private String buildAggregationExpression(String aggregation, String measureField) {
		if ("COUNT_DISTINCT".equals(aggregation)) {
			return "COUNT(DISTINCT " + measureField + ")";
		}
		if ("RATIO".equals(aggregation) || "CUSTOM".equals(aggregation)) {
			return "NULL";
		}
		return aggregation + "(" + measureField + ")";
	}

	private String normalizeWindowFunction(String value) {
		String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "NONE";
		if (!ALLOWED_WINDOW_FUNCTIONS.contains(normalized)) {
			throw new IllegalArgumentException("窗口函数不合法: " + normalized);
		}
		return normalized;
	}

	private String normalizeLayer(String value, String fallback, String label) {
		String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : fallback;
		if (!ALLOWED_LAYERS.contains(normalized)) {
			throw new IllegalArgumentException(label + "不合法: " + normalized);
		}
		return normalized;
	}

	private void requireCurrentCustomValidation(GovIndicatorDefinition definition) {
		String signature = IndicatorValidationSignature.compute(definition, objectMapper, indicatorRepo);
		if (
			!"SUCCESS".equalsIgnoreCase(definition.getLastValidationStatus()) ||
			!StringUtils.hasText(definition.getLastValidationSignature()) ||
			!definition.getLastValidationSignature().equalsIgnoreCase(signature)
		) {
			throw new IllegalArgumentException("CUSTOM 指标必须先完成当前版本计算 SQL 校验");
		}
	}

	private String validateCustomExpression(String expression) {
		String candidate = requireText(expression, "CUSTOM 表达式不能为空");
		if (candidate.length() > 65_536) {
			throw new IllegalArgumentException("CUSTOM 表达式长度超过限制");
		}
		requireNoControlOrJinja(candidate, "CUSTOM 表达式");
		if (candidate.indexOf(';') >= 0 || candidate.contains("--") || candidate.contains("/*") || candidate.contains("*/")) {
			throw new IllegalArgumentException("CUSTOM 表达式只能包含一条无注释查询");
		}
		String upper = candidate.toUpperCase(Locale.ROOT);
		if (!(upper.startsWith("SELECT ") || upper.startsWith("WITH "))) {
			throw new IllegalArgumentException("CUSTOM 表达式必须是 SELECT 或 WITH 查询");
		}
		return candidate;
	}

	private String validateArithmeticExpression(String expression, String label) {
		String candidate = requireText(expression, label + "不能为空");
		requireNoControlOrJinja(candidate, label);
		return new ArithmeticExpressionParser(candidate, label).parse();
	}

	private String validatePredicate(String expression, String label) {
		String candidate = requireText(expression, label + "不能为空");
		requireNoControlOrJinja(candidate, label);
		return new PredicateParser(candidate, label).parse();
	}

	private String validateJoinCondition(String expression, Set<String> aliases, String joinedAlias) {
		String candidate = requireText(expression, "关联条件不能为空");
		requireNoControlOrJinja(candidate, "关联条件");
		return new JoinConditionParser(
			candidate,
			aliases,
			joinedAlias
		).parse();
	}

	private String requireJsonText(
		Map<?, ?> value,
		String key,
		boolean required,
		String fallback
	) {
		Object raw = value.get(key);
		if (raw == null) {
			if (required) {
				throw new IllegalArgumentException("joinConfig 缺少字段: " + key);
			}
			return fallback;
		}
		if (!(raw instanceof String text) || (required && !StringUtils.hasText(text))) {
			throw new IllegalArgumentException("joinConfig." + key + " 必须是非空字符串");
		}
		return StringUtils.hasText(text) ? text.trim() : fallback;
	}

	private String requireText(String value, String message) {
		if (!StringUtils.hasText(value)) {
			throw new IllegalArgumentException(message);
		}
		return value.trim();
	}

	private void requireNoControlOrJinja(String value, String label) {
		if (value.chars().anyMatch(Character::isISOControl)) {
			throw new IllegalArgumentException(label + "包含控制字符");
		}
		if (
			value.contains("{{") ||
			value.contains("}}") ||
			value.contains("{%") ||
			value.contains("%}") ||
			value.contains("{#") ||
			value.contains("#}")
		) {
			throw new IllegalArgumentException(label + "包含 Jinja 模板标记");
		}
	}

	private void validateRenderedArtifact(String relativePath, String content) {
		if (!StringUtils.hasText(content)) {
			throw new IllegalArgumentException("生成产物不能为空: " + relativePath);
		}
		if (content.length() > MAX_ARTIFACT_LENGTH) {
			throw new IllegalArgumentException("生成产物超过大小限制: " + relativePath);
		}
		boolean unsafeControl = content.chars().anyMatch(value ->
			Character.isISOControl(value) && value != '\n' && value != '\r' && value != '\t');
		if (unsafeControl) {
			throw new IllegalArgumentException("生成产物包含控制字符: " + relativePath);
		}
		if (UNRESOLVED_PLACEHOLDER.matcher(content).find()) {
			throw new IllegalArgumentException("生成产物包含未解析模板变量: " + relativePath);
		}
		String withoutAllowedMacros = ALLOWED_DBT_MACRO.matcher(content).replaceAll("");
		if (
			withoutAllowedMacros.contains("{{") ||
			withoutAllowedMacros.contains("{%") ||
			withoutAllowedMacros.contains("{#")
		) {
			throw new IllegalArgumentException("生成产物包含未授权 Jinja 模板: " + relativePath);
		}
	}

	// ── 文件操作 ─────────────────────────────────────────────

	private String buildRelativePath(GovIndicatorDefinition def) {
		String domain = normalizedDomain(def);
		String code = requireSqlIdentifier(def.getCode(), "指标编码");
		return "models/ads/" + domain + "/ind_" + code + ".sql";
	}

	private <T> T withArtifactTransaction(Function<ArtifactTransactionContext, T> action) {
		ARTIFACT_TRANSACTION_LOCK.lock();
		ArtifactFileLock artifactFileLock = null;
		ArtifactTransactionContext transaction = new ArtifactTransactionContext();
		boolean deferredToSpringTransaction = false;
		try {
			Path root = projectPath();
			Files.createDirectories(root);
			Path canonicalRoot = root.toRealPath();
			Path lockPath = canonicalRoot.resolve(ARTIFACT_LOCK_FILE);
			if (
				Files.isSymbolicLink(lockPath) ||
				Files.isDirectory(lockPath, LinkOption.NOFOLLOW_LINKS)
			) {
				throw new IllegalArgumentException("dbt 指标产物锁文件不合法");
			}
			artifactFileLock = acquireArtifactFileLock(lockPath);
			T result = action.apply(transaction);
			if (TransactionSynchronizationManager.isActualTransactionActive()) {
				if (!TransactionSynchronizationManager.isSynchronizationActive()) {
					throw new IllegalStateException("Spring 事务同步未启用，不能安全提交 dbt 指标产物");
				}
				ArtifactFileLock deferredLock = artifactFileLock;
				TransactionSynchronizationManager.registerSynchronization(
					new TransactionSynchronization() {
						@Override
						public void afterCompletion(int status) {
							try {
								transaction.complete(status == STATUS_COMMITTED);
							} finally {
								releaseArtifactFileLock(deferredLock);
							}
						}
					}
				);
				deferredToSpringTransaction = true;
			} else {
				transaction.complete(true);
			}
			return result;
		} catch (IllegalArgumentException invalid) {
			transaction.complete(false);
			throw invalid;
		} catch (IOException failure) {
			transaction.complete(false);
			throw new RuntimeException("获取 dbt 指标产物事务锁失败", failure);
		} catch (RuntimeException | Error failure) {
			transaction.complete(false);
			throw failure;
		} finally {
			if (!deferredToSpringTransaction) {
				releaseArtifactFileLock(artifactFileLock);
			}
		}
	}

	private ArtifactFileLock acquireArtifactFileLock(Path lockPath) throws IOException {
		FileChannel channel = null;
		try {
			channel = FileChannel.open(
				lockPath,
				StandardOpenOption.CREATE,
				StandardOpenOption.WRITE,
				LinkOption.NOFOLLOW_LINKS
			);
			return new ArtifactFileLock(channel, channel.lock());
		} catch (IOException | RuntimeException failure) {
			if (channel != null) {
				channel.close();
			}
			throw failure;
		}
	}

	private void releaseArtifactFileLock(ArtifactFileLock artifactFileLock) {
		try {
			if (artifactFileLock != null) {
				artifactFileLock.close();
			}
		} catch (IOException failure) {
			log.error("Failed to release dbt indicator artifact lock", failure);
		} finally {
			ARTIFACT_TRANSACTION_LOCK.unlock();
		}
	}

	private ArtifactPromotion promoteArtifacts(
		ArtifactTransactionContext transaction,
		Map<String, String> artifacts,
		Set<String> deletions
	) {
		if (artifacts.isEmpty() && deletions.isEmpty()) {
			ArtifactPromotion promotion = new ArtifactPromotion(null, List.of());
			transaction.track(promotion);
			return promotion;
		}
		Path transactionRoot = null;
		List<PromotionEntry> entries = new ArrayList<>();
		try {
			Path projectPath = projectPath();
			Files.createDirectories(projectPath);
			Path canonicalProjectPath = projectPath.toRealPath();
			transactionRoot = Files.createTempDirectory(canonicalProjectPath, ".indicator-stage-");
			Path stagedRoot = transactionRoot.resolve("staged");
			Path backupRoot = transactionRoot.resolve("backup");
			Files.createDirectories(stagedRoot);
			Files.createDirectories(backupRoot);

			for (var artifact : artifacts.entrySet()) {
				String relativePath = artifact.getKey();
				validateRenderedArtifact(relativePath, artifact.getValue());
				Path target = resolveArtifactTarget(canonicalProjectPath, relativePath);
				Path relative = Path.of(relativePath).normalize();
				Path staged = stagedRoot.resolve(relative).normalize();
				Path backup = backupRoot.resolve(relative).normalize();
				if (!staged.startsWith(stagedRoot) || !backup.startsWith(backupRoot)) {
					throw new IllegalArgumentException("dbt 输出路径越界: " + relativePath);
				}
				Files.createDirectories(staged.getParent());
				Files.writeString(staged, artifact.getValue());
				entries.add(new PromotionEntry(target, staged, backup));
			}
			for (String deletion : deletions.stream().sorted().toList()) {
				if (artifacts.containsKey(deletion)) {
					throw new IllegalArgumentException("同一 dbt 产物不能同时写入和删除: " + deletion);
				}
				String relativePath = requireOwnedArtifactPath(deletion);
				Path target = resolveArtifactTarget(canonicalProjectPath, relativePath);
				Path backup = backupRoot.resolve(Path.of(relativePath).normalize()).normalize();
				if (!backup.startsWith(backupRoot)) {
					throw new IllegalArgumentException("dbt 删除路径越界: " + relativePath);
				}
				entries.add(new PromotionEntry(target, null, backup));
			}

			for (PromotionEntry entry : entries) {
				if (Files.isSymbolicLink(entry.target)) {
					throw new IOException("拒绝覆盖符号链接: " + entry.target);
				}
				if (Files.isDirectory(entry.target, LinkOption.NOFOLLOW_LINKS)) {
					throw new IOException("拒绝以文件覆盖目录: " + entry.target);
				}
				if (Files.exists(entry.target, LinkOption.NOFOLLOW_LINKS)) {
					Files.createDirectories(entry.backup.getParent());
					moveReplace(entry.target, entry.backup);
					entry.hadOriginal = true;
				}
				if (entry.staged != null) {
					moveReplace(entry.staged, entry.target);
					entry.installed = true;
					log.debug("Promoted dbt file: {}", entry.target);
				} else {
					log.debug("Removed stale dbt file: {}", entry.target);
				}
			}
			ArtifactPromotion promotion = new ArtifactPromotion(transactionRoot, entries);
			transaction.track(promotion);
			return promotion;
		} catch (Exception failure) {
			try {
				rollbackEntries(entries);
			} catch (IOException rollbackFailure) {
				failure.addSuppressed(rollbackFailure);
			}
			cleanupTreeQuietly(transactionRoot);
			throw new RuntimeException("写入 dbt 产物失败，已回滚本批次", failure);
		}
	}

	private Path projectPath() {
		String projectDir = dbtProperties.getProjectDir();
		if (!StringUtils.hasText(projectDir)) {
			projectDir = "/opt/dts/dbt";
		}
		return Path.of(projectDir).toAbsolutePath().normalize();
	}

	private Path resolveArtifactTarget(Path canonicalProjectPath, String relativePath) throws IOException {
		Path relative = Path.of(relativePath);
		if (relative.isAbsolute() || relative.normalize().startsWith("..")) {
			throw new IllegalArgumentException("dbt 输出路径越界: " + relativePath);
		}
		Path target = canonicalProjectPath.resolve(relative).normalize();
		if (!target.startsWith(canonicalProjectPath)) {
			throw new IllegalArgumentException("dbt 输出路径越界: " + relativePath);
		}
		Files.createDirectories(target.getParent());
		Path canonicalParent = target.getParent().toRealPath();
		if (!canonicalParent.startsWith(canonicalProjectPath)) {
			throw new IllegalArgumentException("dbt 输出目录越界: " + relativePath);
		}
		return target;
	}

	private void moveReplace(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private void rollbackEntries(List<PromotionEntry> entries) throws IOException {
		IOException rollbackFailure = null;
		for (int index = entries.size() - 1; index >= 0; index--) {
			PromotionEntry entry = entries.get(index);
			try {
				if (entry.installed) {
					Files.deleteIfExists(entry.target);
				}
				if (entry.hadOriginal && Files.exists(entry.backup, LinkOption.NOFOLLOW_LINKS)) {
					Files.createDirectories(entry.target.getParent());
					moveReplace(entry.backup, entry.target);
				}
			} catch (IOException current) {
				if (rollbackFailure == null) {
					rollbackFailure = new IOException("回滚 dbt 产物失败");
				}
				rollbackFailure.addSuppressed(current);
			}
		}
		if (rollbackFailure != null) {
			throw rollbackFailure;
		}
	}

	private void cleanupTreeQuietly(Path root) {
		if (root == null || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
			return;
		}
		try (var paths = Files.walk(root)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(path);
			}
		} catch (IOException cleanupFailure) {
			log.warn("Failed to clean indicator staging directory {}: {}", root, cleanupFailure.getMessage());
		}
	}

	private String safeDisplayText(String value, String label) {
		if (value == null) return "";
		try {
			requireNoControlOrJinja(value, label);
		} catch (IllegalArgumentException invalid) {
			throw new IllegalArgumentException(label + "展示文本" + invalid.getMessage().substring(label.length()));
		}
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private String requirePathSegment(String value, String label) {
		String candidate = value == null ? "" : value.trim();
		if (!SAFE_PATH_SEGMENT.matcher(candidate).matches()) {
			throw new IllegalArgumentException(label + "不是安全路径片段: " + candidate);
		}
		return candidate;
	}

	private abstract static class SqlFragmentParser {

		protected static final int MAX_TOKENS = 256;
		protected static final int MAX_NESTING = 32;
		protected final String source;
		protected final String label;
		protected int position;
		protected int tokenCount;
		protected int nesting;

		private SqlFragmentParser(String source, String label) {
			if (source.length() > 4096) {
				throw new IllegalArgumentException(label + "长度超过限制");
			}
			this.source = source;
			this.label = label;
		}

		protected void skipWhitespace() {
			while (position < source.length() && Character.isWhitespace(source.charAt(position))) {
				position++;
			}
		}

		protected boolean consume(char expected) {
			skipWhitespace();
			if (position < source.length() && source.charAt(position) == expected) {
				position++;
				return true;
			}
			return false;
		}

		protected void expect(char expected) {
			if (!consume(expected)) {
				throw invalid("缺少字符 " + expected);
			}
		}

		protected boolean consumeKeyword(String keyword) {
			skipWhitespace();
			int end = position + keyword.length();
			if (end > source.length() || !source.regionMatches(true, position, keyword, 0, keyword.length())) {
				return false;
			}
			if (
				end < source.length() &&
				(Character.isLetterOrDigit(source.charAt(end)) || source.charAt(end) == '_')
			) {
				return false;
			}
			position = end;
			recordToken();
			return true;
		}

		protected String parseIdentifierPart() {
			skipWhitespace();
			int start = position;
			if (
				position >= source.length() ||
				!(Character.isLetter(source.charAt(position)) || source.charAt(position) == '_')
			) {
				throw invalid("需要 SQL 标识符");
			}
			position++;
			while (
				position < source.length() &&
				(Character.isLetterOrDigit(source.charAt(position)) || source.charAt(position) == '_')
			) {
				position++;
			}
			String value = source.substring(start, position);
			if (!SQL_IDENTIFIER.matcher(value).matches()) {
				throw invalid("SQL 标识符不合法");
			}
			recordToken();
			return value;
		}

		protected QualifiedIdentifier parseQualifiedIdentifier(boolean requireQualifier) {
			String first = parseIdentifierPart();
			skipWhitespace();
			if (position < source.length() && source.charAt(position) == '.') {
				position++;
				String second = parseIdentifierPart();
				return new QualifiedIdentifier(first + "." + second, first);
			}
			if (requireQualifier) {
				throw invalid("关联字段必须使用别名.字段格式");
			}
			return new QualifiedIdentifier(first, null);
		}

		protected String parseNumber() {
			skipWhitespace();
			int start = position;
			boolean digits = false;
			while (position < source.length() && Character.isDigit(source.charAt(position))) {
				position++;
				digits = true;
			}
			if (position < source.length() && source.charAt(position) == '.') {
				position++;
				while (position < source.length() && Character.isDigit(source.charAt(position))) {
					position++;
					digits = true;
				}
			}
			if (!digits) {
				throw invalid("数值不合法");
			}
			recordToken();
			return source.substring(start, position);
		}

		protected String parseStringLiteral() {
			skipWhitespace();
			if (position >= source.length() || source.charAt(position) != '\'') {
				throw invalid("需要字符串字面量");
			}
			int start = position++;
			int characters = 0;
			while (position < source.length()) {
				char current = source.charAt(position++);
				if (current == '\'') {
					if (position < source.length() && source.charAt(position) == '\'') {
						position++;
						characters++;
						continue;
					}
					recordToken();
					return source.substring(start, position);
				}
				characters++;
				if (characters > 512) {
					throw invalid("字符串字面量长度超过限制");
				}
			}
			throw invalid("字符串字面量未闭合");
		}

		protected void enterNesting() {
			nesting++;
			if (nesting > MAX_NESTING) {
				throw invalid("嵌套深度超过限制");
			}
		}

		protected void exitNesting() {
			nesting = Math.max(0, nesting - 1);
		}

		protected void recordToken() {
			tokenCount++;
			if (tokenCount > MAX_TOKENS) {
				throw invalid("token 数量超过限制");
			}
		}

		protected void requireEnd() {
			skipWhitespace();
			if (position != source.length()) {
				throw invalid("包含不受支持的字符或 SQL 片段");
			}
		}

		protected IllegalArgumentException invalid(String reason) {
			return new IllegalArgumentException(label + "不合法: " + reason + "（位置 " + position + "）");
		}
	}

	private static final class ArithmeticExpressionParser extends SqlFragmentParser {

		private static final Set<String> FUNCTIONS = Set.of(
			"sum", "count", "avg", "min", "max", "abs", "nullif", "round", "coalesce");

		private ArithmeticExpressionParser(String source, String label) {
			super(source, label);
		}

		private String parse() {
			String value = parseAdditive();
			requireEnd();
			return value;
		}

		private String parseAdditive() {
			String value = parseMultiplicative();
			while (true) {
				if (consume('+')) {
					recordToken();
					value = value + " + " + parseMultiplicative();
				} else if (consume('-')) {
					recordToken();
					value = value + " - " + parseMultiplicative();
				} else {
					return value;
				}
			}
		}

		private String parseMultiplicative() {
			String value = parseUnary();
			while (true) {
				if (consume('*')) {
					recordToken();
					value = value + " * " + parseUnary();
				} else if (consume('/')) {
					recordToken();
					value = value + " / " + parseUnary();
				} else {
					return value;
				}
			}
		}

		private String parseUnary() {
			if (consume('+')) {
				recordToken();
				return "+" + parseUnary();
			}
			if (consume('-')) {
				recordToken();
				return "-" + parseUnary();
			}
			return parsePrimary();
		}

		private String parsePrimary() {
			skipWhitespace();
			if (consume('(')) {
				enterNesting();
				try {
					String value = parseAdditive();
					expect(')');
					return "(" + value + ")";
				} finally {
					exitNesting();
				}
			}
			if (
				position < source.length() &&
				(Character.isDigit(source.charAt(position)) || source.charAt(position) == '.')
			) {
				return parseNumber();
			}
			if (
				position < source.length() &&
				(Character.isLetter(source.charAt(position)) || source.charAt(position) == '_')
			) {
				String identifier = parseIdentifierPart();
				skipWhitespace();
				if (position < source.length() && source.charAt(position) == '(') {
					return parseFunction(identifier);
				}
				if (position < source.length() && source.charAt(position) == '.') {
					position++;
					return identifier + "." + parseIdentifierPart();
				}
				return identifier;
			}
			throw invalid("表达式语法不完整");
		}

		private String parseFunction(String functionName) {
			String function = functionName.toLowerCase(Locale.ROOT);
			if (!FUNCTIONS.contains(function)) {
				throw invalid("不允许的函数: " + functionName);
			}
			expect('(');
			enterNesting();
			try {
				List<String> arguments = new ArrayList<>();
				skipWhitespace();
				if ("count".equals(function) && position < source.length() && source.charAt(position) == '*') {
					position++;
					recordToken();
					arguments.add("*");
					expect(')');
				} else {
					if (consume(')')) {
						throw invalid("函数参数不能为空");
					}
					while (true) {
						arguments.add(parseAdditive());
						if (consume(',')) {
							recordToken();
							continue;
						}
						expect(')');
						break;
					}
				}
				validateArity(function, arguments.size());
				return function + "(" + String.join(", ", arguments) + ")";
			} finally {
				exitNesting();
			}
		}

		private void validateArity(String function, int count) {
			boolean valid = switch (function) {
				case "sum", "count", "avg", "min", "max", "abs" -> count == 1;
				case "nullif" -> count == 2;
				case "round" -> count == 1 || count == 2;
				case "coalesce" -> count >= 2 && count <= 8;
				default -> false;
			};
			if (!valid) {
				throw invalid("函数 " + function + " 的参数数量不合法");
			}
		}
	}

	private static final class PredicateParser extends SqlFragmentParser {

		private static final int MAX_IN_VALUES = 32;

		private PredicateParser(String source, String label) {
			super(source, label);
		}

		private String parse() {
			String value = parseOr();
			requireEnd();
			return value;
		}

		private String parseOr() {
			String value = parseAnd();
			while (consumeKeyword("OR")) {
				value = value + " OR " + parseAnd();
			}
			return value;
		}

		private String parseAnd() {
			String value = parseNot();
			while (consumeKeyword("AND")) {
				value = value + " AND " + parseNot();
			}
			return value;
		}

		private String parseNot() {
			if (consumeKeyword("NOT")) {
				return "NOT " + parseNot();
			}
			return parsePredicatePrimary();
		}

		private String parsePredicatePrimary() {
			if (consume('(')) {
				enterNesting();
				try {
					String nested = parseOr();
					expect(')');
					return "(" + nested + ")";
				} finally {
					exitNesting();
				}
			}
			return parseComparison();
		}

		private String parseComparison() {
			String left = parseQualifiedIdentifier(false).text();
			if (consumeKeyword("IS")) {
				boolean not = consumeKeyword("NOT");
				if (!consumeKeyword("NULL")) {
					throw invalid("IS 仅允许 NULL");
				}
				return left + " IS " + (not ? "NOT " : "") + "NULL";
			}

			boolean not = consumeKeyword("NOT");
			if (consumeKeyword("IN")) {
				return left + (not ? " NOT IN " : " IN ") + parseInValues();
			}
			if (consumeKeyword("LIKE")) {
				return left + (not ? " NOT LIKE " : " LIKE ") + parseStringLiteral();
			}
			if (not) {
				throw invalid("NOT 后仅允许 IN 或 LIKE");
			}

			String operator = parseComparisonOperator();
			return left + " " + operator + " " + parsePredicateValue();
		}

		private String parseInValues() {
			expect('(');
			enterNesting();
			try {
				List<String> values = new ArrayList<>();
				while (true) {
					values.add(parseLiteral());
					if (values.size() > MAX_IN_VALUES) {
						throw invalid("IN 值数量超过限制");
					}
					if (consume(',')) {
						recordToken();
						continue;
					}
					expect(')');
					break;
				}
				return "(" + String.join(", ", values) + ")";
			} finally {
				exitNesting();
			}
		}

		private String parseComparisonOperator() {
			skipWhitespace();
			for (String operator : List.of("<=", ">=", "<>", "!=", "=", "<", ">")) {
				if (source.startsWith(operator, position)) {
					position += operator.length();
					recordToken();
					return operator;
				}
			}
			throw invalid("缺少受支持的比较运算符");
		}

		private String parsePredicateValue() {
			skipWhitespace();
			if (position < source.length() && source.charAt(position) == '\'') {
				return parseStringLiteral();
			}
			if (
				position < source.length() &&
				(Character.isDigit(source.charAt(position)) || source.charAt(position) == '.')
			) {
				return parseNumber();
			}
			if (consumeKeyword("TRUE")) return "TRUE";
			if (consumeKeyword("FALSE")) return "FALSE";
			if (consumeKeyword("NULL")) return "NULL";
			return parseQualifiedIdentifier(false).text();
		}

		private String parseLiteral() {
			skipWhitespace();
			if (position < source.length() && source.charAt(position) == '\'') {
				return parseStringLiteral();
			}
			if (
				position < source.length() &&
				(Character.isDigit(source.charAt(position)) || source.charAt(position) == '.')
			) {
				return parseNumber();
			}
			if (consumeKeyword("TRUE")) return "TRUE";
			if (consumeKeyword("FALSE")) return "FALSE";
			if (consumeKeyword("NULL")) return "NULL";
			throw invalid("IN 仅允许字面量");
		}
	}

	private static final class JoinConditionParser extends SqlFragmentParser {

		private final Set<String> aliases;
		private final String joinedAlias;

		private JoinConditionParser(String source, Set<String> aliases, String joinedAlias) {
			super(source, "关联条件");
			this.aliases = Set.copyOf(aliases);
			this.joinedAlias = joinedAlias;
		}

		private String parse() {
			List<String> clauses = new ArrayList<>();
			while (true) {
				QualifiedIdentifier left = parseQualifiedIdentifier(true);
				if (!consume('=')) {
					throw invalid("仅允许字段等值关联");
				}
				recordToken();
				QualifiedIdentifier right = parseQualifiedIdentifier(true);
				validateAlias(left);
				validateAlias(right);
				if (
					!joinedAlias.equalsIgnoreCase(left.qualifier()) &&
					!joinedAlias.equalsIgnoreCase(right.qualifier())
				) {
					throw invalid("每个关联条件都必须引用当前关联别名 " + joinedAlias);
				}
				clauses.add(left.text() + " = " + right.text());
				if (!consumeKeyword("AND")) {
					break;
				}
				if (clauses.size() >= 32) {
					throw invalid("关联条件数量超过限制");
				}
			}
			requireEnd();
			return String.join(" AND ", clauses);
		}

		private void validateAlias(QualifiedIdentifier identifier) {
			if (!aliases.contains(identifier.qualifier().toLowerCase(Locale.ROOT))) {
				throw invalid("引用了未声明的关联别名: " + identifier.qualifier());
			}
		}
	}

	private record QualifiedIdentifier(String text, String qualifier) {}

	private record OwnedArtifacts(
		String modelPath,
		String windowPath,
		String domain,
		String schemaPath
	) {}

	private record SqlArtifactPlan(
		Map<String, String> artifacts,
		Set<String> deletions,
		Set<String> schemaDomains
	) {}

	private record SchemaArtifactPlan(Map<String, String> artifacts, Set<String> deletions) {}

	private final class ArtifactTransactionContext {

		private final List<ArtifactPromotion> promotions = new ArrayList<>();
		private boolean completed;

		private void track(ArtifactPromotion promotion) {
			if (completed) {
				throw new IllegalStateException("dbt 指标产物事务已经结束");
			}
			promotions.add(promotion);
		}

		private void complete(boolean committed) {
			if (completed) {
				return;
			}
			completed = true;
			if (committed) {
				for (ArtifactPromotion promotion : promotions) {
					promotion.finalizeCommit();
				}
				return;
			}
			for (int index = promotions.size() - 1; index >= 0; index--) {
				promotions.get(index).rollback();
			}
		}
	}

	private final class ArtifactPromotion implements AutoCloseable {

		private final Path transactionRoot;
		private final List<PromotionEntry> entries;
		private boolean prepared;
		private boolean finished;

		private ArtifactPromotion(Path transactionRoot, List<PromotionEntry> entries) {
			this.transactionRoot = transactionRoot;
			this.entries = entries;
		}

		private void commit() {
			if (finished) {
				throw new IllegalStateException("dbt 指标产物 promotion 已结束");
			}
			prepared = true;
		}

		private void finalizeCommit() {
			if (finished) {
				return;
			}
			if (!prepared) {
				rollback();
				return;
			}
			finished = true;
			cleanupTreeQuietly(transactionRoot);
		}

		private void rollback() {
			if (finished) {
				return;
			}
			finished = true;
			try {
				rollbackEntries(entries);
			} catch (IOException rollbackFailure) {
				log.error("Failed to rollback generated indicator artifacts", rollbackFailure);
			} finally {
				cleanupTreeQuietly(transactionRoot);
			}
		}

		@Override
		public void close() {
			if (!prepared) {
				rollback();
			}
		}
	}

	private static final class ArtifactFileLock implements AutoCloseable {

		private final FileChannel channel;
		private final FileLock lock;
		private boolean closed;

		private ArtifactFileLock(FileChannel channel, FileLock lock) {
			this.channel = channel;
			this.lock = lock;
		}

		@Override
		public void close() throws IOException {
			if (closed) {
				return;
			}
			closed = true;
			IOException failure = null;
			try {
				lock.release();
			} catch (IOException current) {
				failure = current;
			}
			try {
				channel.close();
			} catch (IOException current) {
				if (failure == null) {
					failure = current;
				} else {
					failure.addSuppressed(current);
				}
			}
			if (failure != null) {
				throw failure;
			}
		}
	}

	private static final class PromotionEntry {

		private final Path target;
		private final Path staged;
		private final Path backup;
		private boolean hadOriginal;
		private boolean installed;

		private PromotionEntry(Path target, Path staged, Path backup) {
			this.target = target;
			this.staged = staged;
			this.backup = backup;
		}
	}
}
