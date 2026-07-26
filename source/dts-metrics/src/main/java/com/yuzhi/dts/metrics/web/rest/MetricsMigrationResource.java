package com.yuzhi.dts.metrics.web.rest;

import com.yuzhi.dts.metrics.service.MetricClassificationMigrationService;
import com.yuzhi.dts.metrics.service.MetricClassificationMigrationService.ApplyReport;
import com.yuzhi.dts.metrics.service.MetricClassificationMigrationService.DryRunReport;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics/migration")
public class MetricsMigrationResource {

    private final MetricClassificationMigrationService classificationMigrationService;

    public MetricsMigrationResource(
        MetricClassificationMigrationService classificationMigrationService
    ) {
        this.classificationMigrationService = classificationMigrationService;
    }

    @GetMapping("/semantic-dry-run")
    public Map<String, Object> semanticDryRun() {
        return Map.of(
            "status",
            "READY_FOR_DRY_RUN",
            "productionMigration",
            false,
            "checkedAt",
            Instant.now().toString(),
            "source",
            "dts-platform semantic_*",
            "target",
            "dts-metrics metric_*",
            "mappings",
            List.of(
                mapping("semantic_subject_domain", "metric_subject_domain", "subject domains"),
                mapping("semantic_business_object", "metric_business_object", "business objects"),
                mapping("semantic_dimension", "metric_dimension", "dimensions"),
                mapping("semantic_metric", "metric_metric", "metrics"),
                mapping("semantic_model", "metric_model", "semantic models"),
                mapping("semantic_generated_artifact", "metric_generated_artifact", "generated artifacts")
            ),
            "blockers",
            List.of(
                "requires platform asset contract lookup before writing target records",
                "requires platform permission check for preview and publish",
                "requires dbt release gate for generated artifacts"
            ),
            "rollback",
            List.of(
                "keep platform /api/semantic compatibility for one sprint",
                "do not delete semantic_* source records during dry-run",
                "published artifacts roll back through platform publish record"
            )
        );
    }

    @GetMapping("/classification-dry-run")
    public DryRunReport classificationDryRun() {
        return classificationMigrationService.dryRun();
    }

    @org.springframework.web.bind.annotation.PostMapping("/classification-apply")
    public ApplyReport classificationApply(
        @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int offset,
        @org.springframework.web.bind.annotation.RequestParam(defaultValue = "100") int limit
    ) {
        return classificationMigrationService.apply(offset, limit);
    }

    private static Map<String, Object> mapping(String source, String target, String purpose) {
        return Map.of("source", source, "target", target, "purpose", purpose);
    }
}
