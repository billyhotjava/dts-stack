package com.yuzhi.dts.platform.service.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage;
import com.yuzhi.dts.platform.domain.catalog.CatalogLineageJob;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetLineageRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogLineageJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CatalogDbtLineageService {

	private final CatalogDatasetRepository datasetRepo;
	private final CatalogDatasetLineageRepository lineageRepo;
	private final CatalogLineageJobRepository lineageJobRepo;
	private final ObjectMapper objectMapper;
	private final CatalogClassificationPropagationJobService propagationJobService;

	/** 未匹配明细返回上限，超出截断并标记 truncated */
	private static final int UNMATCHED_DETAIL_LIMIT = 200;

	public CatalogDbtLineageService(
		CatalogDatasetRepository datasetRepo,
		CatalogDatasetLineageRepository lineageRepo,
		CatalogLineageJobRepository lineageJobRepo,
		ObjectMapper objectMapper,
		CatalogClassificationPropagationJobService propagationJobService
	) {
		this.datasetRepo = datasetRepo;
		this.lineageRepo = lineageRepo;
		this.lineageJobRepo = lineageJobRepo;
		this.objectMapper = objectMapper;
		this.propagationJobService = propagationJobService;
	}

	@Transactional
	public Map<String, Object> importManifest(MultipartFile file) throws IOException {
		Map<?, ?> rawManifest = objectMapper.readValue(file.getInputStream(), Map.class);
		if (rawManifest == null || rawManifest.isEmpty()) {
			throw new IllegalArgumentException("manifest.json 内容为空");
		}
		Object nodesObj = rawManifest.get("nodes");
		if (nodesObj != null && !(nodesObj instanceof Map)) {
			throw new IllegalArgumentException("manifest.json 中 nodes 字段格式不正确，期望为对象");
		}
		@SuppressWarnings("unchecked")
		Map<String, Object> manifest = (Map<String, Object>) rawManifest;
		@SuppressWarnings("unchecked")
		Map<String, Object> nodes = nodesObj != null ? (Map<String, Object>) nodesObj : Collections.emptyMap();

		// 按 schema+表名（小写）建立索引；schema 缺失时退化纯表名，兼容既有数据
		Map<String, UUID> tableIndex = new HashMap<>();
		for (Object[] row : datasetRepo.findSchemaTableAndIdProjection()) {
			String hiveTable = (String) row[1];
			if (hiveTable == null) {
				continue;
			}
			UUID id = (UUID) row[2];
			String schema = row[0] == null ? null : String.valueOf(row[0]).trim().toLowerCase();
			tableIndex.put(hiveTable.toLowerCase(), id);
			if (schema != null && !schema.isBlank()) {
				tableIndex.put(schema + "." + hiveTable.toLowerCase(), id);
			}
		}

		// 只加载当前有效的 DBT 边（投影 id 对），不再全表加载
		Set<String> existingPairs = lineageRepo.findCurrentDbtPairs().stream()
			.map(row -> row[0] + ":" + row[1])
			.collect(Collectors.toSet());

		int skippedNotModel = 0;
		int skippedMalformed = 0;
		int skippedUnmatchedModel = 0;
		int skippedUnmatchedParent = 0;
		int skipped = 0;
		Set<String> toCreateKeys = new HashSet<>();
		List<CatalogDatasetLineage> toCreate = new ArrayList<>();
		Set<UUID> affectedDownstreamIds = new LinkedHashSet<>();
		List<Map<String, Object>> unmatched = new ArrayList<>();

		for (Map.Entry<String, Object> entry : nodes.entrySet()) {
			String nodeKey = entry.getKey();
			if (nodeKey == null || !nodeKey.startsWith("model.")) {
				// 非 model 节点（seed/source/exposure 等）不属于表级血缘导入范围
				skippedNotModel++;
				continue;
			}
			if (!(entry.getValue() instanceof Map)) {
				skippedMalformed++;
				continue;
			}

			@SuppressWarnings("unchecked")
			Map<String, Object> node = (Map<String, Object>) entry.getValue();
			String modelName = String.valueOf(node.getOrDefault("name", ""));
			String modelSchema = text(node.get("schema"));

			Object depsObj = node.getOrDefault("depends_on", Collections.emptyMap());
			if (!(depsObj instanceof Map)) {
				skippedMalformed++;
				continue;
			}
			@SuppressWarnings("unchecked")
			Map<String, Object> dependsOn = (Map<String, Object>) depsObj;
			Object parentObj = dependsOn.getOrDefault("nodes", Collections.emptyList());
			if (!(parentObj instanceof List)) {
				skippedMalformed++;
				continue;
			}
			@SuppressWarnings("unchecked")
			List<String> parentKeys = (List<String>) parentObj;

			UUID downstreamId = resolveDatasetId(tableIndex, modelSchema, modelName);
			if (downstreamId == null) {
				skippedUnmatchedModel++;
				skipped++;
				addUnmatched(unmatched, nodeKey, modelName, "下游模型未匹配到目录资产（schema.table）");
				continue;
			}
			CatalogLineageJob lineageJob = upsertDbtJob(nodeKey, modelName, node);

			for (String parentKey : parentKeys) {
				// dbt unique_id 形如 model.<project>.<schema>.<name> / source.<project>.<schema>.<name>：
				// schema 取倒数第 2 段，name 取末段
				String[] parentParts = parentKey.split("\\.");
				String parentName = parentParts[parentParts.length - 1];
				String parentSchema = parentParts.length >= 3 ? parentParts[parentParts.length - 2] : null;
				UUID upstreamId = resolveDatasetId(tableIndex, parentSchema, parentName);
				if (upstreamId == null) {
					skippedUnmatchedParent++;
					skipped++;
					addUnmatched(unmatched, parentKey, parentName, "上游父模型未匹配到目录资产（schema.table）");
					continue;
				}

				String pairKey = upstreamId + ":" + downstreamId;
				affectedDownstreamIds.add(downstreamId);
				if (!existingPairs.contains(pairKey) && !toCreateKeys.contains(pairKey)) {
					CatalogDatasetLineage lineage = new CatalogDatasetLineage();
					lineage.setUpstreamDatasetId(upstreamId);
					lineage.setDownstreamDatasetId(downstreamId);
					lineage.setRelationType("DBT");
					lineage.setDirection("UPSTREAM_TO_DOWNSTREAM");
					lineage.setNotes("Imported from dbt manifest");
					lineage.setVerificationStatus("DECLARED");
					lineage.setValidFrom(Instant.now());
					if (lineageJob != null) {
						lineage.setLineageJobId(lineageJob.getId());
					}
					toCreate.add(lineage);
					toCreateKeys.add(pairKey);
				}
			}
		}

		lineageRepo.saveAll(toCreate);
		int created = toCreate.size();
		String triggerRef = "dbt-manifest:" + sha256(objectMapper.writeValueAsString(manifest));
		int propagationEnqueued = 0;
		for (UUID downstreamId : affectedDownstreamIds) {
			if (propagationJobService.enqueue(downstreamId, "DBT", triggerRef)) {
				propagationEnqueued++;
			}
		}
		boolean truncated = unmatched.size() > UNMATCHED_DETAIL_LIMIT;
		List<Map<String, Object>> unmatchedDetail = truncated
			? unmatched.subList(0, UNMATCHED_DETAIL_LIMIT)
			: unmatched;
		return Map.of(
			"created",
			created,
			"skipped",
			skipped,
			"total",
			nodes.size(),
			"propagationEnqueued",
			propagationEnqueued,
			"skippedReasons",
			Map.of(
				"notModel",
				skippedNotModel,
				"malformedNode",
				skippedMalformed,
				"unmatchedModel",
				skippedUnmatchedModel,
				"unmatchedParent",
				skippedUnmatchedParent
			),
			"unmatched",
			unmatchedDetail,
			"truncated",
			truncated
		);
	}

	private void addUnmatched(List<Map<String, Object>> unmatched, String uniqueId, String name, String reason) {
		if (unmatched.size() >= UNMATCHED_DETAIL_LIMIT) {
			return;
		}
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("uniqueId", uniqueId);
		row.put("name", name);
		row.put("reason", reason);
		unmatched.add(row);
	}

	/** 优先按 schema.table 精确匹配，退化为纯表名（兼容未带 schema 的模型）。 */
	private UUID resolveDatasetId(Map<String, UUID> tableIndex, String schema, String table) {
		if (table == null || table.isBlank()) {
			return null;
		}
		String tableKey = table.trim().toLowerCase();
		if (schema != null && !schema.isBlank()) {
			UUID exact = tableIndex.get(schema.trim().toLowerCase() + "." + tableKey);
			if (exact != null) {
				return exact;
			}
		}
		return tableIndex.get(tableKey);
	}

	private String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8)));
		} catch (Exception ex) {
			throw new IllegalStateException("Unable to calculate dbt manifest checksum", ex);
		}
	}

	private CatalogLineageJob upsertDbtJob(String nodeKey, String modelName, Map<String, Object> node) {
		if (nodeKey == null || nodeKey.isBlank()) {
			return null;
		}
		String jobKey = truncate("DBT:" + nodeKey.trim(), 256);
		CatalogLineageJob job = lineageJobRepo.findByJobKey(jobKey).orElseGet(CatalogLineageJob::new);
		job.setJobKey(jobKey);
		job.setName(truncate(modelName != null && !modelName.isBlank() ? modelName : nodeKey, 256));
		job.setJobType("DBT_MODEL");
		job.setEngine("DBT");
		job.setRelationType("DBT");
		job.setProjectName(resolveDbtProjectName(nodeKey));
		job.setExternalId(truncate(nodeKey, 256));
		if (job.getStatus() == null || job.getStatus().isBlank()) {
			job.setStatus("declared");
		}
		job.setLastObservedAt(Instant.now());
		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("engine", "DBT");
		detail.put("uniqueId", nodeKey);
		detail.put("schema", text(node.get("schema")));
		detail.put("table", modelName);
		detail.put("originalFilePath", text(node.get("original_file_path")));
		detail.values().removeIf(value -> value == null || String.valueOf(value).isBlank());
		job.setDetailPayload(detail.toString());
		return lineageJobRepo.save(job);
	}

	private String resolveDbtProjectName(String uniqueId) {
		if (uniqueId == null || uniqueId.isBlank()) {
			return null;
		}
		String[] parts = uniqueId.split("\\.");
		if (parts.length < 3) {
			return null;
		}
		return parts[1] == null || parts[1].isBlank() ? null : parts[1];
	}

	private String text(Object value) {
		if (value == null) {
			return null;
		}
		String text = String.valueOf(value).trim();
		return text.isEmpty() ? null : text;
	}

	private String truncate(String value, int maxLength) {
		if (value == null || value.length() <= maxLength) {
			return value;
		}
		return value.substring(0, Math.max(0, maxLength));
	}
}
