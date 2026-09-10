package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import com.yuzhi.dts.platform.service.modeling.QualityEvidencePort;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Batch JDBC adapter over the existing rule-version, binding and quality-run ledgers. */
@Component
public class JdbcGovernanceQualityEvidenceAdapter implements QualityEvidencePort {

    private static final int MAX_REQUESTS = 100;
    private final JdbcTemplate jdbcTemplate;

    public JdbcGovernanceQualityEvidenceAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public List<QualityEvidence> read(List<QualityEvidenceRequest> requests) {
        List<QualityEvidenceRequest> scope = validated(requests);
        List<ResolvedRequest> resolved = resolve(scope);
        List<QualityEvidence> invalid = resolved
            .stream()
            .filter(item -> item.datasetId() == null)
            .map(item -> missing(item.request(), null, null, "ASSET_MISMATCH"))
            .toList();
        List<ResolvedRequest> readable = resolved.stream().filter(item -> item.datasetId() != null).toList();
        if (readable.isEmpty()) return invalid;

        List<BindingRow> bindings = loadBindings(readable);
        List<BindingRow> selected = readable.stream().flatMap(item -> selectBindings(item, bindings).stream()).distinct().toList();
        List<RunRow> runs = loadRuns(selected, readable.getFirst().request().asOf());

        List<QualityEvidence> result = new ArrayList<>(invalid);
        for (ResolvedRequest item : readable) {
            List<BindingRow> requestedBindings = selectBindings(item, bindings);
            if (requestedBindings.isEmpty()) {
                if (item.request().ruleVersionIds().isEmpty()) {
                    result.add(missing(item.request(), null, null, "MISSING"));
                } else {
                    for (UUID ruleVersionId : item.request().ruleVersionIds()) {
                        BindingRow elsewhere = bindings
                            .stream()
                            .filter(binding -> ruleVersionId.equals(binding.ruleVersionId()))
                            .findFirst()
                            .orElse(null);
                        result.add(
                            missing(
                                item.request(),
                                elsewhere == null ? null : elsewhere.ruleId(),
                                ruleVersionId,
                                elsewhere == null ? "MISSING" : "ASSET_MISMATCH"
                            )
                        );
                    }
                }
                continue;
            }
            Set<UUID> duplicatedVersions = duplicatedVersions(requestedBindings);
            for (BindingRow binding : requestedBindings) {
                if (duplicatedVersions.contains(binding.ruleVersionId())) {
                    result.add(evidence(item.request(), binding, null, List.of("BINDING_MISMATCH")));
                    continue;
                }
                RunRow run = latestRun(binding, runs);
                result.add(evaluate(item.request(), item.datasetId(), binding, run));
            }
        }
        return result
            .stream()
            .sorted(
                Comparator.comparing(QualityEvidence::assetKey)
                    .thenComparing(item -> item.ruleVersionId() == null ? "" : item.ruleVersionId().toString())
                    .thenComparing(item -> item.bindingId() == null ? "" : item.bindingId().toString())
            )
            .toList();
    }

    private List<ResolvedRequest> resolve(List<QualityEvidenceRequest> requests) {
        List<QualityEvidenceRequest> datasets = requests
            .stream()
            .filter(request -> request.assetType() == CatalogAssetType.DATASET)
            .toList();
        if (datasets.isEmpty()) {
            return requests.stream().map(request -> new ResolvedRequest(request, null)).toList();
        }
        List<String> assetKeys = datasets.stream().map(QualityEvidenceRequest::assetKey).distinct().toList();
        List<Object> arguments = new ArrayList<>(assetKeys.size() + 1);
        arguments.add(CatalogAssetType.DATASET.name());
        arguments.addAll(assetKeys);
        List<AssetIdentityRow> rows = jdbcTemplate.query(
            """
            select asset_key, resource_id
              from catalog_asset_semantic_projection
             where asset_type = ?
               and asset_key in (%s)
            """.formatted(placeholders(assetKeys.size())),
            (row, rowNumber) -> new AssetIdentityRow(row.getString("asset_key"), row.getObject("resource_id", UUID.class)),
            arguments.toArray()
        );
        Map<String, UUID> datasetIds = new LinkedHashMap<>();
        for (AssetIdentityRow row : rows) {
            if (row != null && row.assetKey() != null && row.datasetId() != null) {
                datasetIds.putIfAbsent(row.assetKey(), row.datasetId());
            }
        }
        return requests
            .stream()
            .map(request -> new ResolvedRequest(request, datasetIds.get(request.assetKey())))
            .toList();
    }

    private List<BindingRow> loadBindings(List<ResolvedRequest> requests) {
        List<UUID> datasetIds = requests.stream().map(ResolvedRequest::datasetId).distinct().toList();
        List<UUID> explicitVersions = requests
            .stream()
            .flatMap(item -> item.request().ruleVersionIds().stream())
            .distinct()
            .toList();
        String datasetPlaceholders = placeholders(datasetIds.size());
        String versionClause = explicitVersions.isEmpty()
            ? ""
            : " or binding.rule_version_id in (" + placeholders(explicitVersions.size()) + ")";
        List<Object> arguments = new ArrayList<>(datasetIds.size() + explicitVersions.size());
        arguments.addAll(datasetIds);
        arguments.addAll(explicitVersions);
        return jdbcTemplate.query(
            """
            select binding.id as binding_id,
                   binding.dataset_id,
                   version.rule_id,
                   binding.rule_version_id,
                   version.status as version_status
              from gov_rule_binding binding
              join gov_rule_version version on version.id = binding.rule_version_id
             where binding.dataset_id in (%s)%s
             order by binding.dataset_id, binding.rule_version_id, binding.id
            """.formatted(datasetPlaceholders, versionClause),
            (row, rowNumber) ->
                new BindingRow(
                    row.getObject("binding_id", UUID.class),
                    row.getObject("dataset_id", UUID.class),
                    row.getObject("rule_id", UUID.class),
                    row.getObject("rule_version_id", UUID.class),
                    row.getString("version_status")
                ),
            arguments.toArray()
        );
    }

    private List<RunRow> loadRuns(List<BindingRow> bindings, Instant asOf) {
        List<UUID> bindingIds = bindings.stream().map(BindingRow::bindingId).distinct().toList();
        if (bindingIds.isEmpty()) return List.of();
        List<Object> arguments = new ArrayList<>(bindingIds.size() + 1);
        arguments.addAll(bindingIds);
        arguments.add(Timestamp.from(asOf));
        return jdbcTemplate.query(
            """
            select distinct on (run.binding_id)
                   run.id as run_id,
                   run.rule_id,
                   run.rule_version_id,
                   run.binding_id,
                   run.dataset_id,
                   case when left(ltrim(run.metrics_json), 1) = '{'
                             and not (coalesce(run.metrics_json::jsonb ->> 'qualityOutcome', '') = 'PASSED'
                                  and coalesce(run.metrics_json::jsonb ->> 'executionOutcome', '') = 'OK')
                        then 'FAILED' else run.status end as status,
                   run.finished_at,
                   run.created_date
              from gov_quality_run run
             where run.binding_id in (%s)
               and upper(coalesce(run.trigger_type, '')) <> 'DRY_RUN'
               and coalesce(run.started_at, run.scheduled_at, run.created_date) <= ?
             order by run.binding_id,
                      coalesce(run.finished_at, run.started_at, run.scheduled_at, run.created_date) desc,
                      run.created_date desc,
                      run.id desc
            """.formatted(placeholders(bindingIds.size())),
            (row, rowNumber) ->
                new RunRow(
                    row.getObject("run_id", UUID.class),
                    row.getObject("rule_id", UUID.class),
                    row.getObject("rule_version_id", UUID.class),
                    row.getObject("binding_id", UUID.class),
                    row.getObject("dataset_id", UUID.class),
                    row.getString("status"),
                    instant(row.getTimestamp("finished_at")),
                    instant(row.getTimestamp("created_date"))
                ),
            arguments.toArray()
        );
    }

    private static List<BindingRow> selectBindings(ResolvedRequest request, List<BindingRow> bindings) {
        return bindings
            .stream()
            .filter(binding -> request.datasetId().equals(binding.datasetId()))
            .filter(binding ->
                request.request().ruleVersionIds().isEmpty()
                    ? "PUBLISHED".equals(normalized(binding.versionStatus()))
                    : request.request().ruleVersionIds().contains(binding.ruleVersionId())
            )
            .toList();
    }

    private static QualityEvidence evaluate(
        QualityEvidenceRequest request,
        UUID expectedDataset,
        BindingRow binding,
        RunRow run
    ) {
        if (run == null) return evidence(request, binding, null, List.of("MISSING"));
        List<String> violations = new ArrayList<>();
        if (!Objects.equals(expectedDataset, run.datasetId())) violations.add("ASSET_MISMATCH");
        if (!Objects.equals(binding.ruleVersionId(), run.ruleVersionId())) violations.add("VERSION_MISMATCH");
        if (!Objects.equals(binding.bindingId(), run.bindingId())) violations.add("BINDING_MISMATCH");
        String status = normalized(run.status());
        if (Set.of("QUEUED", "RUNNING").contains(status)) {
            violations.add("RUNNING");
        } else if ("FAILED".equals(status)) {
            violations.add("FAILED");
        } else if (!"SUCCEEDED".equals(status)) {
            violations.add("ERROR");
        } else if (run.finishedAt() == null) {
            violations.add("ERROR");
        } else if (run.finishedAt().isAfter(request.asOf())) {
            violations.add("ERROR");
        } else if (Duration.between(run.finishedAt(), request.asOf()).getSeconds() > request.maxAgeSeconds()) {
            violations.add("EXPIRED");
        }
        return evidence(request, binding, run, violations.stream().distinct().toList());
    }

    private static RunRow latestRun(BindingRow binding, List<RunRow> runs) {
        RunRow exact = runs.stream().filter(run -> binding.bindingId().equals(run.bindingId())).findFirst().orElse(null);
        if (exact != null) return exact;
        return runs
            .stream()
            .filter(run -> binding.ruleVersionId().equals(run.ruleVersionId()))
            .findFirst()
            .orElse(null);
    }

    private static Set<UUID> duplicatedVersions(List<BindingRow> bindings) {
        Map<UUID, Integer> counts = new LinkedHashMap<>();
        bindings.forEach(binding -> counts.merge(binding.ruleVersionId(), 1, Integer::sum));
        return counts
            .entrySet()
            .stream()
            .filter(entry -> entry.getValue() > 1)
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static QualityEvidence missing(
        QualityEvidenceRequest request,
        UUID ruleId,
        UUID ruleVersionId,
        String violation
    ) {
        return qualityEvidence(request.assetKey(), ruleId, ruleVersionId, null, null, "MISSING", null, List.of(violation));
    }

    private static QualityEvidence evidence(
        QualityEvidenceRequest request,
        BindingRow binding,
        RunRow run,
        List<String> violations
    ) {
        return qualityEvidence(
            request.assetKey(),
            binding.ruleId(),
            binding.ruleVersionId(),
            binding.bindingId(),
            run == null ? null : run.runId(),
            run == null ? "MISSING" : normalized(run.status()),
            run == null ? null : run.finishedAt(),
            violations
        );
    }

    private static QualityEvidence qualityEvidence(
        String assetKey,
        UUID ruleId,
        UUID ruleVersionId,
        UUID bindingId,
        UUID runId,
        String status,
        Instant finishedAt,
        List<String> violations
    ) {
        String canonical = String.join(
            "\n",
            assetKey,
            text(ruleId),
            text(ruleVersionId),
            text(bindingId),
            text(runId),
            normalized(status),
            text(finishedAt),
            String.join(",", violations)
        );
        return new QualityEvidence(
            assetKey,
            ruleId,
            ruleVersionId,
            bindingId,
            runId,
            status,
            finishedAt,
            DigestUtils.sha256Hex(canonical.getBytes(StandardCharsets.UTF_8)),
            violations
        );
    }

    private static List<QualityEvidenceRequest> validated(List<QualityEvidenceRequest> requests) {
        List<QualityEvidenceRequest> result = requests == null ? List.of() : List.copyOf(requests);
        if (result.isEmpty() || result.size() > MAX_REQUESTS || result.stream().anyMatch(item -> item == null)) {
            throw new IllegalArgumentException("quality evidence requests must contain between 1 and 100 items");
        }
        if (result.stream().map(QualityEvidenceRequest::assetKey).distinct().count() != result.size()) {
            throw new IllegalArgumentException("quality evidence requests must use unique asset keys");
        }
        if (result.stream().map(QualityEvidenceRequest::asOf).distinct().count() != 1) {
            throw new IllegalArgumentException("batch quality evidence requests must use the same asOf instant");
        }
        return result;
    }

    private static String placeholders(int size) {
        if (size < 1) throw new IllegalArgumentException("at least one SQL parameter is required");
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    private static Instant instant(java.sql.Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static String normalized(String value) {
        return value == null ? "UNKNOWN" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String text(Object value) {
        return value == null ? "-" : value.toString();
    }

    record BindingRow(UUID bindingId, UUID datasetId, UUID ruleId, UUID ruleVersionId, String versionStatus) {}

    record AssetIdentityRow(String assetKey, UUID datasetId) {}

    record RunRow(
        UUID runId,
        UUID ruleId,
        UUID ruleVersionId,
        UUID bindingId,
        UUID datasetId,
        String status,
        Instant finishedAt,
        Instant createdAt
    ) {}

    private record ResolvedRequest(QualityEvidenceRequest request, UUID datasetId) {}
}
