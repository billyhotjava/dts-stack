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
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CatalogDbtLineageService {

	private final CatalogDatasetRepository datasetRepo;
	private final CatalogDatasetLineageRepository lineageRepo;
	private final CatalogLineageJobRepository lineageJobRepo;
	private final ObjectMapper objectMapper;

	public CatalogDbtLineageService(
		CatalogDatasetRepository datasetRepo,
		CatalogDatasetLineageRepository lineageRepo,
		CatalogLineageJobRepository lineageJobRepo,
		ObjectMapper objectMapper
	) {
		this.datasetRepo = datasetRepo;
		this.lineageRepo = lineageRepo;
		this.lineageJobRepo = lineageJobRepo;
		this.objectMapper = objectMapper;
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

		// Pre-load datasets indexed by hiveTable (lowercase) using projection query
		Map<String, UUID> tableIndex = new HashMap<>();
		for (Object[] row : datasetRepo.findHiveTableAndIdProjection()) {
			String hiveTable = (String) row[0];
			UUID id = (UUID) row[1];
			if (hiveTable != null) {
				tableIndex.put(hiveTable.toLowerCase(), id);
			}
		}

		// Load existing lineage pairs into memory Set to avoid N+1 queries
		Set<String> existingPairs = lineageRepo.findAll().stream()
			.filter(l -> l.getUpstreamDatasetId() != null && l.getDownstreamDatasetId() != null)
			.map(l -> l.getUpstreamDatasetId() + ":" + l.getDownstreamDatasetId())
			.collect(Collectors.toSet());

		int skipped = 0;
		Set<String> toCreateKeys = new HashSet<>();
		List<CatalogDatasetLineage> toCreate = new ArrayList<>();

		for (Map.Entry<String, Object> entry : nodes.entrySet()) {
			String nodeKey = entry.getKey();
			if (!nodeKey.startsWith("model.")) {
				continue;
			}
			if (!(entry.getValue() instanceof Map)) {
				skipped++;
				continue;
			}

			@SuppressWarnings("unchecked")
			Map<String, Object> node = (Map<String, Object>) entry.getValue();
			String modelName = String.valueOf(node.getOrDefault("name", ""));

			Object depsObj = node.getOrDefault("depends_on", Collections.emptyMap());
			if (!(depsObj instanceof Map)) {
				skipped++;
				continue;
			}
			@SuppressWarnings("unchecked")
			Map<String, Object> dependsOn = (Map<String, Object>) depsObj;
			Object parentObj = dependsOn.getOrDefault("nodes", Collections.emptyList());
			if (!(parentObj instanceof List)) {
				skipped++;
				continue;
			}
			@SuppressWarnings("unchecked")
			List<String> parentKeys = (List<String>) parentObj;

			UUID downstreamId = tableIndex.get(modelName.toLowerCase());
			if (downstreamId == null) {
				skipped++;
				continue;
			}
			CatalogLineageJob lineageJob = upsertDbtJob(nodeKey, modelName, node);

			for (String parentKey : parentKeys) {
				String parentName = parentKey.contains(".")
					? parentKey.substring(parentKey.lastIndexOf('.') + 1)
					: parentKey;
				UUID upstreamId = tableIndex.get(parentName.toLowerCase());
				if (upstreamId == null) {
					continue;
				}

				String pairKey = upstreamId + ":" + downstreamId;
				if (!existingPairs.contains(pairKey) && !toCreateKeys.contains(pairKey)) {
					CatalogDatasetLineage lineage = new CatalogDatasetLineage();
					lineage.setUpstreamDatasetId(upstreamId);
					lineage.setDownstreamDatasetId(downstreamId);
					lineage.setRelationType("DBT");
					lineage.setDirection("UPSTREAM_TO_DOWNSTREAM");
					lineage.setNotes("Imported from dbt manifest");
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
		return Map.of("created", created, "skipped", skipped, "total", nodes.size());
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
