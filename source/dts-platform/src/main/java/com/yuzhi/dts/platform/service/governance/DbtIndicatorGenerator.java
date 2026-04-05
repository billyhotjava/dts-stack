package com.yuzhi.dts.platform.service.governance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.DbtProperties;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * dbt 指标模型自动生成���擎。
 * 根据 GovIndicatorDefinition 元数据自动生成 dbt SQL + schema.yml。
 */
@Service
public class DbtIndicatorGenerator {

	private static final Logger log = LoggerFactory.getLogger(DbtIndicatorGenerator.class);

	// ── SQL 模板 ──────────────────────────────────────────────

	private static final String SIMPLE_AGGREGATE_TEMPLATE = """
		-- 自动生成: {name} ({code})
		-- 生成时间: {generatedAt}

		SELECT
		{dimensionColumns}    DATE_TRUNC('{timeGrainUnit}', {dateColumn}) AS report_period,
		    {aggregationType}({measureField}) AS {code}
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

	private static final String DERIVED_TEMPLATE = """
		-- 自动生成(衍生): {name} ({code})
		-- 生成时间: {generatedAt}

		SELECT
		{dimensionColumns}    report_period,
		    {expressionSql} AS {code}
		FROM {dependencyRef}
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

	public DbtIndicatorGenerator(
			GovIndicatorDefinitionRepository indicatorRepo,
			DbtProperties dbtProperties,
			ObjectMapper objectMapper) {
		this.indicatorRepo = indicatorRepo;
		this.dbtProperties = dbtProperties;
		this.objectMapper = objectMapper;
	}

	// ── Public API ────────────────────────────────────────────

	/**
	 * 生成单个指标的 dbt SQL 文件。
	 */
	public String generate(UUID indicatorId) {
		GovIndicatorDefinition def = indicatorRepo.findById(indicatorId)
			.orElseThrow(() -> new IllegalArgumentException("指标不存在: " + indicatorId));

		String sql = renderSql(def);
		String relativePath = buildRelativePath(def);
		writeFile(relativePath, sql);

		// 窗口函数追加模型
		if (def.getWindowFunction() != null && !"NONE".equals(def.getWindowFunction())) {
			String windowSql = renderWindowSql(def);
			String windowPath = relativePath.replace(".sql", "_window.sql");
			writeFile(windowPath, windowSql);
		}

		// 更新 targetModelName
		String modelName = "ind_" + def.getCode();
		def.setTargetModelName(modelName);
		indicatorRepo.save(def);

		log.info("Generated dbt model: {} -> {}", def.getCode(), relativePath);
		return relativePath;
	}

	/**
	 * 批量���成（拓扑排序后）。
	 */
	public List<String> generateBatch(List<UUID> indicatorIds) {
		List<UUID> sorted = topologicalSort(indicatorIds);
		return sorted.stream().map(this::generate).toList();
	}

	/**
	 * 生成 + 更新状态（不直接调 dbt run，返回文件列表）。
	 */
	public GenerationResult generateAndRun(List<UUID> indicatorIds) {
		List<String> files = generateBatch(indicatorIds);
		generateSchemaYml(indicatorIds);

		List<String> warnings = new ArrayList<>();

		// 更新状态为 COMMITTED
		for (UUID id : indicatorIds) {
			GovIndicatorDefinition def = indicatorRepo.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("指标不存在: " + id));
			def.setStatus("COMMITTED");
			indicatorRepo.save(def);
		}

		return new GenerationResult(indicatorIds.size(), files, "READY", "PENDING", warnings);
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
		if (def.getWindowFunction() != null && !"NONE".equals(def.getWindowFunction())) {
			result.put("windowSql", renderWindowSql(def));
		}
		return result;
	}

	/**
	 * 按 domain 生成 schema.yml。
	 */
	public void generateSchemaYml(List<UUID> indicatorIds) {
		List<GovIndicatorDefinition> defs = indicatorIds.stream()
			.map(id -> indicatorRepo.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("指标不存在: " + id)))
			.toList();

		Map<String, List<GovIndicatorDefinition>> byDomain = defs.stream()
			.collect(Collectors.groupingBy(d ->
				d.getDomain() != null ? d.getDomain().toLowerCase() : "general"));

		for (var entry : byDomain.entrySet()) {
			StringBuilder yml = new StringBuilder();
			yml.append("version: 2\n\nmodels:\n");

			for (GovIndicatorDefinition def : entry.getValue()) {
				yml.append("  - name: ind_").append(def.getCode()).append("\n");
				yml.append("    description: \"自动生成 - ").append(safe(def.getName())).append("\"\n");
				yml.append("    columns:\n");

				// 指标列
				yml.append("      - name: ").append(def.getCode()).append("\n");
				yml.append("        description: \"").append(safe(def.getName()));
				if (def.getUnit() != null) yml.append(" (").append(def.getUnit()).append(")");
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
					yml.append("        description: \"").append(safe(dim.getOrDefault("displayName", ""))).append("\"\n");
				}
			}

			String path = "models/ads/" + entry.getKey() + "/" + entry.getKey() + "_indicators_schema.yml";
			writeFile(path, yml.toString());
		}
	}

	// ── 拓扑排序 ─────────────────────────────────────────────

	public List<UUID> topologicalSort(List<UUID> indicatorIds) {
		Map<String, UUID> codeToId = new HashMap<>();
		Map<UUID, List<String>> dependencies = new HashMap<>();

		for (UUID id : indicatorIds) {
			GovIndicatorDefinition def = indicatorRepo.findById(id).orElseThrow();
			codeToId.put(def.getCode(), id);
			if (Boolean.TRUE.equals(def.getIsDerived()) && StringUtils.hasText(def.getDependencyIndicators())) {
				dependencies.put(id, parseJsonArray(def.getDependencyIndicators()));
			} else {
				dependencies.put(id, List.of());
			}
		}

		Map<UUID, Integer> inDegree = new HashMap<>();
		Map<UUID, List<UUID>> adjList = new HashMap<>();

		for (UUID id : indicatorIds) {
			inDegree.put(id, 0);
			adjList.put(id, new ArrayList<>());
		}

		for (var entry : dependencies.entrySet()) {
			for (String depCode : entry.getValue()) {
				UUID depId = codeToId.get(depCode);
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
		String aggType = def.getAggregationType() != null ? def.getAggregationType() : "SUM";
		String template;
		if (Boolean.TRUE.equals(def.getIsDerived())) {
			template = DERIVED_TEMPLATE;
		} else if ("RATIO".equals(aggType)) {
			template = RATIO_TEMPLATE;
		} else if ("CUSTOM".equals(aggType)) {
			template = CUSTOM_TEMPLATE;
		} else {
			template = SIMPLE_AGGREGATE_TEMPLATE;
		}
		return applyVars(template, buildVars(def));
	}

	private String renderWindowSql(GovIndicatorDefinition def) {
		String wf = def.getWindowFunction();
		String template = switch (wf != null ? wf : "NONE") {
			case "YOY" -> WINDOW_TEMPLATE_YOY;
			case "MOM" -> WINDOW_TEMPLATE_MOM;
			case "YTD" -> WINDOW_TEMPLATE_YTD;
			default -> WINDOW_TEMPLATE_MOM;
		};
		Map<String, String> vars = buildVars(def);
		vars.put("baseRef", "{{ ref('ind_" + def.getCode() + "') }}");
		vars.put("periodsBack", "YOY".equals(wf) ? "12" : "1");

		List<Map<String, String>> dims = parseDimensions(def.getDimensionFields());
		String partitionBy = dims.isEmpty() ? "1"
			: dims.stream().map(d -> d.get("field")).collect(Collectors.joining(", "));
		vars.put("partitionBy", partitionBy);

		return applyVars(template, vars);
	}

	private Map<String, String> buildVars(GovIndicatorDefinition def) {
		Map<String, String> v = new LinkedHashMap<>();
		v.put("name", safe(def.getName()));
		v.put("code", def.getCode());
		v.put("generatedAt", Instant.now().toString());
		v.put("aggregationType", def.getAggregationType() != null ? def.getAggregationType() : "SUM");
		v.put("measureField", def.getMeasureField() != null ? def.getMeasureField() : "1");
		v.put("numeratorExpression", def.getNumeratorExpression() != null ? def.getNumeratorExpression() : "0");
		v.put("denominatorExpression", def.getDenominatorExpression() != null ? def.getDenominatorExpression() : "1");
		v.put("precisionScale", String.valueOf(def.getPrecisionScale() != null ? def.getPrecisionScale() : 2));
		v.put("dateColumn", def.getDateColumn() != null ? def.getDateColumn() : "created_date");
		v.put("timeGrainUnit", mapTimeGrainUnit(def.getTimeGrain()));
		v.put("expressionSql", def.getExpressionSql() != null ? def.getExpressionSql() : "NULL");
		v.put("sourceRef", buildSourceRef(def));
		v.put("dependencyRef", buildDependencyRef(def));
		v.put("dimensionColumns", buildDimensionColumns(def));
		v.put("dimensionGroupBy", buildDimensionGroupBy(def));
		v.put("joinClause", buildJoinClause(def));
		v.put("staticFilterClause", StringUtils.hasText(def.getStaticFilter())
			? "    AND " + def.getStaticFilter() + "\n" : "");
		return v;
	}

	private String applyVars(String template, Map<String, String> vars) {
		String result = template;
		for (var entry : vars.entrySet()) {
			result = result.replace("{" + entry.getKey() + "}", entry.getValue());
		}
		return result;
	}

	private String mapTimeGrainUnit(String grain) {
		if (grain == null) return "month";
		return switch (grain) {
			case "DAY" -> "day";
			case "WEEK" -> "week";
			case "MONTH" -> "month";
			case "QUARTER" -> "quarter";
			case "YEAR" -> "year";
			default -> "month";
		};
	}

	private String buildSourceRef(GovIndicatorDefinition def) {
		String table = def.getSourceTable();
		if (table == null) return "UNKNOWN_SOURCE";
		String layer = def.getSourceLayer();
		if ("ODS".equals(layer)) {
			return "{{ source('public', '" + table + "') }}";
		}
		return "{{ ref('" + table + "') }}";
	}

	private String buildDependencyRef(GovIndicatorDefinition def) {
		if (!Boolean.TRUE.equals(def.getIsDerived())) return "";
		List<String> deps = parseJsonArray(def.getDependencyIndicators());
		if (deps.isEmpty()) return "UNKNOWN_DEPENDENCY";
		return "{{ ref('ind_" + deps.get(0) + "') }}";
	}

	private String buildDimensionColumns(GovIndicatorDefinition def) {
		List<Map<String, String>> dims = parseDimensions(def.getDimensionFields());
		if (dims.isEmpty()) return "";
		StringBuilder sb = new StringBuilder();
		for (Map<String, String> dim : dims) {
			sb.append("    ").append(dim.get("field")).append(",\n");
		}
		return sb.toString();
	}

	private String buildDimensionGroupBy(GovIndicatorDefinition def) {
		List<Map<String, String>> dims = parseDimensions(def.getDimensionFields());
		if (dims.isEmpty()) return "";
		StringBuilder sb = new StringBuilder();
		for (Map<String, String> dim : dims) {
			sb.append("    ").append(dim.get("field")).append(",\n");
		}
		return sb.toString();
	}

	private String buildJoinClause(GovIndicatorDefinition def) {
		if (!StringUtils.hasText(def.getJoinConfig())) return "";
		try {
			List<Map<String, String>> joins = objectMapper.readValue(
				def.getJoinConfig(), new TypeReference<>() {});
			StringBuilder sb = new StringBuilder();
			for (Map<String, String> j : joins) {
				String type = j.getOrDefault("type", "LEFT");
				String table = j.get("table");
				String alias = j.getOrDefault("alias", table);
				String on = j.get("on");
				sb.append(type).append(" JOIN {{ ref('").append(table).append("') }} AS ")
				  .append(alias).append(" ON ").append(on).append("\n");
			}
			return sb.toString();
		} catch (Exception e) {
			log.warn("Failed to parse joinConfig: {}", e.getMessage());
			return "";
		}
	}

	// ── JSON 解析帮助 ────────────────────────────────────────

	private List<Map<String, String>> parseDimensions(String json) {
		if (!StringUtils.hasText(json)) return List.of();
		try {
			return objectMapper.readValue(json, new TypeReference<>() {});
		} catch (Exception e) {
			log.warn("Failed to parse dimensionFields: {}", e.getMessage());
			return List.of();
		}
	}

	private List<String> parseJsonArray(String json) {
		if (!StringUtils.hasText(json)) return List.of();
		try {
			return objectMapper.readValue(json, new TypeReference<>() {});
		} catch (Exception e) {
			log.warn("Failed to parse JSON array: {}", e.getMessage());
			return List.of();
		}
	}

	// ── 文件操作 ─────────────────────────────────────────────

	private String buildRelativePath(GovIndicatorDefinition def) {
		String domain = (def.getDomain() != null ? def.getDomain() : "general").toLowerCase();
		return "models/ads/" + domain + "/ind_" + def.getCode() + ".sql";
	}

	private void writeFile(String relativePath, String content) {
		try {
			String projectDir = dbtProperties.getProjectDir();
			if (!StringUtils.hasText(projectDir)) {
				projectDir = "/opt/dts/dbt";
			}
			Path fullPath = Path.of(projectDir).resolve(relativePath);
			Files.createDirectories(fullPath.getParent());
			Files.writeString(fullPath, content);
			log.debug("Wrote dbt file: {}", fullPath);
		} catch (IOException e) {
			throw new RuntimeException("写入 dbt 文件失败: " + relativePath, e);
		}
	}

	private String safe(String s) {
		return s != null ? s.replace("\"", "'") : "";
	}
}
