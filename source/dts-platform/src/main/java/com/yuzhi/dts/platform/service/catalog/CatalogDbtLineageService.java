package com.yuzhi.dts.platform.service.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetLineageRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Service
public class CatalogDbtLineageService {

	private final CatalogDatasetRepository datasetRepo;
	private final CatalogDatasetLineageRepository lineageRepo;
	private final ObjectMapper objectMapper;

	public CatalogDbtLineageService(
		CatalogDatasetRepository datasetRepo,
		CatalogDatasetLineageRepository lineageRepo,
		ObjectMapper objectMapper
	) {
		this.datasetRepo = datasetRepo;
		this.lineageRepo = lineageRepo;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public Map<String, Object> importManifest(MultipartFile file) throws IOException {
		@SuppressWarnings("unchecked")
		Map<String, Object> manifest = objectMapper.readValue(file.getInputStream(), Map.class);
		@SuppressWarnings("unchecked")
		Map<String, Object> nodes = (Map<String, Object>) manifest.getOrDefault("nodes", Collections.emptyMap());

		// Pre-load all datasets indexed by hiveTable (lowercase) for fast lookup
		Map<String, UUID> tableIndex = new HashMap<>();
		for (CatalogDataset ds : datasetRepo.findAll()) {
			if (ds.getHiveTable() != null) {
				tableIndex.put(ds.getHiveTable().toLowerCase(), ds.getId());
			}
		}

		int created = 0;
		int skipped = 0;

		for (Map.Entry<String, Object> entry : nodes.entrySet()) {
			String nodeKey = entry.getKey();
			if (!nodeKey.startsWith("model.")) {
				continue;
			}

			@SuppressWarnings("unchecked")
			Map<String, Object> node = (Map<String, Object>) entry.getValue();
			String modelName = String.valueOf(node.getOrDefault("name", ""));

			@SuppressWarnings("unchecked")
			Map<String, Object> dependsOn = (Map<String, Object>) node.getOrDefault("depends_on", Collections.emptyMap());
			@SuppressWarnings("unchecked")
			List<String> parentKeys = (List<String>) dependsOn.getOrDefault("nodes", Collections.emptyList());

			UUID downstreamId = tableIndex.get(modelName.toLowerCase());
			if (downstreamId == null) {
				skipped++;
				continue;
			}

			for (String parentKey : parentKeys) {
				String parentName = parentKey.contains(".")
					? parentKey.substring(parentKey.lastIndexOf('.') + 1)
					: parentKey;
				UUID upstreamId = tableIndex.get(parentName.toLowerCase());
				if (upstreamId == null) {
					continue;
				}

				if (!lineageRepo.existsByUpstreamDatasetIdAndDownstreamDatasetId(upstreamId, downstreamId)) {
					CatalogDatasetLineage lineage = new CatalogDatasetLineage();
					lineage.setUpstreamDatasetId(upstreamId);
					lineage.setDownstreamDatasetId(downstreamId);
					lineage.setRelationType("DBT_MODEL");
					lineage.setDirection("DOWNSTREAM");
					lineage.setNotes("Imported from dbt manifest");
					lineageRepo.save(lineage);
					created++;
				}
			}
		}

		return Map.of("created", created, "skipped", skipped, "total", nodes.size());
	}
}
