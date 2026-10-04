package com.yuzhi.dts.platform.web.rest.internal;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/v1/lineage/backfill")
@PreAuthorize("hasAuthority('" + AuthoritiesConstants.SERVICE_INTERNAL + "')")
public class LineageBackfillResource {

    @GetMapping("/dry-run")
    public ResponseEntity<BackfillDryRunResponse> dryRun(
        @RequestParam(name = "type", required = false, defaultValue = "DBT_MODEL") String type,
        @RequestParam(name = "since", required = false) Instant since
    ) {
        BackfillPlan plan = planFor(type, since);
        if (!plan.supported()) {
            return ResponseEntity.badRequest().body(plan.response());
        }
        return ResponseEntity.ok(plan.response());
    }

    private static BackfillPlan planFor(String rawType, Instant requestedSince) {
        String type = normalizeType(rawType);
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return switch (type) {
            case "DBT_MODEL" -> supported(
                type,
                requestedSince != null ? requestedSince : now.minus(90, ChronoUnit.DAYS),
                90,
                "dbt artifacts",
                "Backfill recent dbt manifest column lineage snapshots into catalog_column_lineage effective windows.",
                List.of(
                    "load manifest.json and run_results.json from dbt artifact storage",
                    "resolve upstream and downstream catalog asset identities",
                    "compute column lineage edges and effective_from/effective_to windows",
                    "write dry-run counts only; no mutation in this endpoint"
                ),
                List.of("candidate edge count", "new window count", "changed window count", "stale edge candidates"),
                List.of("discard dry-run output", "run real backfill only after approval and snapshot export"),
                List.of()
            );
            case "ADDAX_RUN" -> supported(
                type,
                requestedSince != null ? requestedSince : now.minus(30, ChronoUnit.DAYS),
                30,
                "Airflow/Addax run log",
                "Backfill declared Addax ingestion lineage for the recent operational window.",
                List.of(
                    "scan Addax/Airflow run logs in the requested window",
                    "resolve source and ODS target catalog assets",
                    "prepare ingestion lineage observations",
                    "write dry-run counts only; no mutation in this endpoint"
                ),
                List.of("run count", "source asset count", "target asset count", "unresolved asset references"),
                List.of("discard dry-run output", "rerun ingestion lineage writer only after approval"),
                List.of()
            );
            case "OPENLINEAGE_EVENT" -> supported(
                type,
                requestedSince != null ? requestedSince : now,
                0,
                "live OpenLineage event stream",
                "Do not backfill OpenLineage events; start observing from the receiver enablement time.",
                List.of("verify receiver health", "record no historical mutation plan"),
                List.of("receiver health status", "first observed event timestamp"),
                List.of("disable receiver or replay approved events from source system"),
                List.of("OpenLineage event backfill is intentionally disabled in v2.2.3")
            );
            case "MANUAL_DECLARATION" -> supported(
                type,
                requestedSince != null ? requestedSince : now,
                0,
                "platform manual lineage declarations",
                "Do not backfill manual declarations; keep existing rows and use future edits as effective changes.",
                List.of("snapshot current manual lineage declarations", "record no historical mutation plan"),
                List.of("manual declaration count", "owner review queue"),
                List.of("restore snapshot if a future approved migration changes declarations"),
                List.of("Manual lineage uses current-state review, not historical replay")
            );
            default -> unsupported(type, requestedSince != null ? requestedSince : now);
        };
    }

    private static BackfillPlan supported(
        String type,
        Instant since,
        int lookbackDays,
        String dataSource,
        String strategy,
        List<String> plannedSteps,
        List<String> expectedOutputs,
        List<String> rollbackPlan,
        List<String> warnings
    ) {
        return new BackfillPlan(
            true,
            new BackfillDryRunResponse(
                type,
                true,
                false,
                since,
                lookbackDays,
                dataSource,
                strategy,
                plannedSteps,
                expectedOutputs,
                rollbackPlan,
                warnings
            )
        );
    }

    private static BackfillPlan unsupported(String type, Instant since) {
        return new BackfillPlan(
            false,
            new BackfillDryRunResponse(
                type,
                true,
                false,
                since,
                0,
                "unknown",
                "Unsupported lineage backfill type.",
                List.of(),
                List.of(),
                List.of("no mutation has been planned"),
                List.of("supported types: DBT_MODEL, ADDAX_RUN, OPENLINEAGE_EVENT, MANUAL_DECLARATION")
            )
        );
    }

    private static String normalizeType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return "DBT_MODEL";
        }
        return rawType.trim().replace('-', '_').toUpperCase(Locale.ROOT);
    }

    private record BackfillPlan(boolean supported, BackfillDryRunResponse response) {}

    public record BackfillDryRunResponse(
        String type,
        boolean dryRun,
        boolean mutatesData,
        Instant since,
        int lookbackDays,
        String dataSource,
        String strategy,
        List<String> plannedSteps,
        List<String> expectedOutputs,
        List<String> rollbackPlan,
        List<String> warnings
    ) {}
}
