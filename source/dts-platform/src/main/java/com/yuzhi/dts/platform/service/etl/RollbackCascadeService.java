package com.yuzhi.dts.platform.service.etl;

import com.yuzhi.dts.platform.config.AirflowProperties;
import com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping;
import com.yuzhi.dts.platform.domain.modeling.ModelingSqlModel;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.infra.InfraOdsTableMappingRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingSqlModelRepository;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Platform-side cascade cleanup triggered after a successful rollback execute.
 * Each step is best-effort: failures are logged but do not abort subsequent steps.
 */
@Service
public class RollbackCascadeService {

    private static final Logger LOG = LoggerFactory.getLogger(RollbackCascadeService.class);

    private final InfraOdsTableMappingRepository odsMappingRepository;
    private final ModelingSqlModelRepository sqlModelRepository;
    private final ModelingSqlModelService sqlModelService;
    private final CatalogDatasetRepository datasetRepository;
    private final AirflowClient airflowClient;
    private final AirflowProperties airflowProperties;

    public RollbackCascadeService(
        InfraOdsTableMappingRepository odsMappingRepository,
        ModelingSqlModelRepository sqlModelRepository,
        ModelingSqlModelService sqlModelService,
        CatalogDatasetRepository datasetRepository,
        AirflowClient airflowClient,
        AirflowProperties airflowProperties
    ) {
        this.odsMappingRepository = odsMappingRepository;
        this.sqlModelRepository = sqlModelRepository;
        this.sqlModelService = sqlModelService;
        this.datasetRepository = datasetRepository;
        this.airflowClient = airflowClient;
        this.airflowProperties = airflowProperties;
    }

    /**
     * Platform-side cascade cleanup after Level 3 rollback.
     * <ol>
     *   <li>Find ODS mappings by sourceDataSourceId</li>
     *   <li>Find and delete associated SQL models + dbt files</li>
     *   <li>Delete associated catalog datasets</li>
     *   <li>Delete ODS mappings</li>
     * </ol>
     */
    @SuppressWarnings("unchecked")
    public void cascadeCleanup(Map<String, Object> request, Object ingestionResult) {
        UUID sourceDataSourceId = extractUuid(request, "sourceDataSourceId");
        if (sourceDataSourceId == null) {
            LOG.warn("[rollback-cascade] no sourceDataSourceId in request, skipping cascade cleanup");
            return;
        }
        LOG.info("[rollback-cascade] starting cascade cleanup for sourceDataSourceId={}", sourceDataSourceId);

        // Step 1: Find ODS mappings by connectionId (== sourceDataSourceId)
        List<InfraOdsTableMapping> mappings = List.of();
        try {
            mappings = odsMappingRepository.findByConnectionIdOrderByCreatedDateDesc(sourceDataSourceId);
            LOG.info("[rollback-cascade] found {} ODS mappings for connectionId={}", mappings.size(), sourceDataSourceId);
        } catch (Exception ex) {
            LOG.warn("[rollback-cascade] failed to find ODS mappings: {}", ex.getMessage());
        }

        // Step 2: Find and delete associated SQL models (which also deletes dbt files)
        try {
            List<ModelingSqlModel> models = sqlModelRepository.findBySourceDataSourceId(sourceDataSourceId);
            LOG.info("[rollback-cascade] found {} SQL models for sourceDataSourceId={}", models.size(), sourceDataSourceId);
            for (ModelingSqlModel model : models) {
                try {
                    sqlModelService.delete(model.getId(), null);
                    LOG.info("[rollback-cascade] deleted SQL model id={} name={}", model.getId(), model.getName());
                } catch (Exception ex) {
                    LOG.warn("[rollback-cascade] failed to delete SQL model id={}: {}", model.getId(), ex.getMessage());
                }
            }
        } catch (Exception ex) {
            LOG.warn("[rollback-cascade] failed to find/delete SQL models: {}", ex.getMessage());
        }

        // Step 3: Delete associated catalog datasets via ODS mapping datasetId
        for (InfraOdsTableMapping mapping : mappings) {
            UUID datasetId = mapping.getDatasetId();
            if (datasetId == null) {
                continue;
            }
            try {
                datasetRepository.deleteById(datasetId);
                LOG.info("[rollback-cascade] deleted catalog dataset id={} for ODS mapping {}.{}",
                    datasetId, mapping.getOdsSchema(), mapping.getOdsTable());
            } catch (Exception ex) {
                LOG.warn("[rollback-cascade] failed to delete dataset id={}: {}", datasetId, ex.getMessage());
            }
        }

        // Step 4: Delete ODS mappings
        for (InfraOdsTableMapping mapping : mappings) {
            try {
                odsMappingRepository.deleteById(mapping.getId());
                LOG.info("[rollback-cascade] deleted ODS mapping id={} table={}.{}",
                    mapping.getId(), mapping.getOdsSchema(), mapping.getOdsTable());
            } catch (Exception ex) {
                LOG.warn("[rollback-cascade] failed to delete ODS mapping id={}: {}", mapping.getId(), ex.getMessage());
            }
        }

        LOG.info("[rollback-cascade] cascade cleanup completed for sourceDataSourceId={}", sourceDataSourceId);
    }

    /**
     * Trigger dbt run --full-refresh for Level 2 rebuild.
     * Uses the default Airflow DAG with operation=run and full_refresh=true in conf.
     */
    public void triggerDbtFullRefresh(Map<String, Object> request) {
        String dagId = airflowProperties.getDagId();
        LOG.info("[rollback-cascade] triggering dbt full-refresh via dagId={}", dagId);

        Map<String, Object> conf = new LinkedHashMap<>();
        conf.put("operation", "run");
        conf.put("full_refresh", true);

        // If the request specifies a models selector, forward it
        Object models = request.get("models");
        if (models instanceof String modelsStr && !modelsStr.isBlank()) {
            conf.put("models", modelsStr.trim());
        } else {
            conf.put("models", "all");
        }

        Map<String, Object> payload = Map.of("conf", conf, "logical_date", Instant.now().toString());
        try {
            airflowClient.triggerDag(dagId, payload)
                .ifPresentOrElse(
                    result -> LOG.info("[rollback-cascade] dbt full-refresh triggered: {}", result),
                    () -> LOG.warn("[rollback-cascade] dbt full-refresh trigger returned empty result")
                );
        } catch (RuntimeException ex) {
            LOG.warn("[rollback-cascade] dbt full-refresh trigger failed for dagId={}: {}", dagId, ex.getMessage());
        }
    }

    private UUID extractUuid(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
