package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogLifecycleUsageRecorder {

    private final JdbcTemplate jdbcTemplate;
    private final CatalogClassificationService classificationService;

    public CatalogLifecycleUsageRecorder(
        JdbcTemplate jdbcTemplate,
        CatalogClassificationService classificationService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.classificationService = classificationService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(CatalogDataset dataset, String action, String actor) {
        if (dataset == null || dataset.getId() == null) {
            return;
        }
        CatalogClassificationSnapshot seal = classificationService
            .resolve("ASSET", CatalogAssetKey.dataset(dataset))
            .orElse(null);
        Instant now = Instant.now();
        String effectiveActor = actor == null || actor.isBlank() ? "system" : actor.trim();
        String auditActor = effectiveActor.length() <= 50 ? effectiveActor : effectiveActor.substring(0, 50);
        jdbcTemplate.update(
            """
            insert into catalog_lifecycle_event (
                id, dataset_id, stage, event_type, status, request_source,
                request_ref, seal_id, seal_version, effective_level,
                evidence_json, actor, occurred_at, created_by, created_date
            ) values (
                ?, ?, 'USE', ?, 'EXECUTED', 'DATA_ACCESS_GUARD',
                ?, ?, ?, ?, '{}'::jsonb, ?, ?, ?, ?
            )
            """,
            UUID.randomUUID(),
            dataset.getId(),
            action,
            dataset.getId().toString(),
            seal == null ? null : seal.getId(),
            seal == null || seal.getRecordVersion() == null ? null : seal.getRecordVersion(),
            seal == null ? dataset.getClassification() : seal.getEffectiveLevel(),
            effectiveActor,
            now,
            auditActor,
            now
        );
    }
}
