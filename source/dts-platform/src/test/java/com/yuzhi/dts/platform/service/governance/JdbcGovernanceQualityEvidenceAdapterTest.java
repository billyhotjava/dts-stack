package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import com.yuzhi.dts.platform.service.modeling.QualityEvidencePort;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcGovernanceQualityEvidenceAdapterTest {

    private static final UUID DATASET_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final String ASSET_KEY =
        "source:10000000-0000-0000-0000-000000000001/schema:dwd/table:project_detail";
    private static final UUID RULE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID VERSION_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID BINDING_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID RUN_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final Instant AS_OF = Instant.parse("2026-08-17T00:00:00Z");

    @Test
    void returnsStablePassingEvidenceFromThePinnedVersionBindingAndRun() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate(
            List.of(asset(ASSET_KEY, DATASET_ID)),
            List.of(binding(VERSION_ID, "PUBLISHED")),
            List.of(run("SUCCEEDED", AS_OF.minusSeconds(60), DATASET_ID, VERSION_ID, BINDING_ID))
        );
        JdbcGovernanceQualityEvidenceAdapter adapter = new JdbcGovernanceQualityEvidenceAdapter(jdbc);

        List<QualityEvidencePort.QualityEvidence> evidence = adapter.read(
            List.of(request(List.of(VERSION_ID), 300))
        );

        assertThat(evidence).singleElement().satisfies(item -> {
            assertThat(item.assetKey()).isEqualTo(ASSET_KEY);
            assertThat(item.ruleId()).isEqualTo(RULE_ID);
            assertThat(item.ruleVersionId()).isEqualTo(VERSION_ID);
            assertThat(item.bindingId()).isEqualTo(BINDING_ID);
            assertThat(item.runId()).isEqualTo(RUN_ID);
            assertThat(item.status()).isEqualTo("SUCCEEDED");
            assertThat(item.violations()).isEmpty();
            assertThat(item.evidenceChecksum()).matches("[0-9a-f]{64}");
        });
        assertThat(jdbc.queries()).hasSize(3);
    }

    @Test
    void bindsTheEvidenceCutoffAsASqlTimestampForPostgres() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate(
            List.of(asset(ASSET_KEY, DATASET_ID)),
            List.of(binding(VERSION_ID, "PUBLISHED")),
            List.of(run("SUCCEEDED", AS_OF.minusSeconds(60), DATASET_ID, VERSION_ID, BINDING_ID))
        );

        new JdbcGovernanceQualityEvidenceAdapter(jdbc).read(List.of(request(List.of(VERSION_ID), 300)));

        assertThat(jdbc.arguments()).hasSize(3);
        assertThat(jdbc.arguments().get(2)).last().isEqualTo(Timestamp.from(AS_OF));
    }

    @Test
    void everyNonPassingOrStaleStateFailsClosedWithAStableViolation() {
        assertViolation(List.of(), List.of(), "MISSING");
        assertViolation(List.of(binding(VERSION_ID, "PUBLISHED")), List.of(), "MISSING");
        assertViolation(
            List.of(binding(VERSION_ID, "PUBLISHED")),
            List.of(run("RUNNING", null, DATASET_ID, VERSION_ID, BINDING_ID)),
            "RUNNING"
        );
        assertViolation(
            List.of(binding(VERSION_ID, "PUBLISHED")),
            List.of(run("FAILED", AS_OF.minusSeconds(10), DATASET_ID, VERSION_ID, BINDING_ID)),
            "FAILED"
        );
        assertViolation(
            List.of(binding(VERSION_ID, "PUBLISHED")),
            List.of(run("SUCCEEDED", AS_OF.minusSeconds(301), DATASET_ID, VERSION_ID, BINDING_ID)),
            "EXPIRED"
        );
        assertViolation(
            List.of(binding(VERSION_ID, "PUBLISHED")),
            List.of(run("SUCCEEDED", AS_OF.minusSeconds(10), UUID.randomUUID(), VERSION_ID, BINDING_ID)),
            "ASSET_MISMATCH"
        );
        assertViolation(
            List.of(binding(VERSION_ID, "PUBLISHED")),
            List.of(run("SUCCEEDED", AS_OF.minusSeconds(10), DATASET_ID, UUID.randomUUID(), BINDING_ID)),
            "VERSION_MISMATCH"
        );
        assertViolation(
            List.of(binding(VERSION_ID, "PUBLISHED")),
            List.of(run("SUCCEEDED", AS_OF.minusSeconds(10), DATASET_ID, VERSION_ID, UUID.randomUUID())),
            "BINDING_MISMATCH"
        );
    }

    @Test
    void anExplicitArchivedVersionRemainsPinnedWhenANewerVersionIsPublished() {
        UUID newerVersion = UUID.fromString("30000000-0000-0000-0000-000000000002");
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate(
            List.of(asset(ASSET_KEY, DATASET_ID)),
            List.of(binding(VERSION_ID, "ARCHIVED"), binding(newerVersion, "PUBLISHED")),
            List.of(run("SUCCEEDED", AS_OF.minusSeconds(10), DATASET_ID, VERSION_ID, BINDING_ID))
        );

        List<QualityEvidencePort.QualityEvidence> evidence = new JdbcGovernanceQualityEvidenceAdapter(jdbc).read(
            List.of(request(List.of(VERSION_ID), 300))
        );

        assertThat(evidence).singleElement().satisfies(item -> {
            assertThat(item.ruleVersionId()).isEqualTo(VERSION_ID);
            assertThat(item.violations()).isEmpty();
        });
    }

    @Test
    void anInvalidDatasetAssetKeyNeverFallsThroughToAQualityQuery() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate(List.of(), List.of(), List.of());

        List<QualityEvidencePort.QualityEvidence> evidence = new JdbcGovernanceQualityEvidenceAdapter(jdbc).read(
            List.of(new QualityEvidencePort.QualityEvidenceRequest(CatalogAssetType.DATASET, "source:missing/schema:dwd/table:missing", List.of(), AS_OF, 300))
        );

        assertThat(evidence).singleElement().satisfies(item -> assertThat(item.violations()).containsExactly("ASSET_MISMATCH"));
        assertThat(jdbc.queries()).singleElement().satisfies(sql -> assertThat(sql).contains("catalog_asset_semantic_projection"));
    }

    private static void assertViolation(
        List<JdbcGovernanceQualityEvidenceAdapter.BindingRow> bindings,
        List<JdbcGovernanceQualityEvidenceAdapter.RunRow> runs,
        String expected
    ) {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate(List.of(asset(ASSET_KEY, DATASET_ID)), bindings, runs);
        List<QualityEvidencePort.QualityEvidence> evidence = new JdbcGovernanceQualityEvidenceAdapter(jdbc).read(
            List.of(request(List.of(VERSION_ID), 300))
        );
        assertThat(evidence).singleElement().satisfies(item -> assertThat(item.violations()).contains(expected));
    }

    private static QualityEvidencePort.QualityEvidenceRequest request(List<UUID> versions, long maxAgeSeconds) {
        return new QualityEvidencePort.QualityEvidenceRequest(
            CatalogAssetType.DATASET,
            ASSET_KEY,
            versions,
            AS_OF,
            maxAgeSeconds
        );
    }

    private static JdbcGovernanceQualityEvidenceAdapter.BindingRow binding(UUID versionId, String status) {
        return new JdbcGovernanceQualityEvidenceAdapter.BindingRow(BINDING_ID, DATASET_ID, RULE_ID, versionId, status);
    }

    private static JdbcGovernanceQualityEvidenceAdapter.AssetIdentityRow asset(String assetKey, UUID datasetId) {
        return new JdbcGovernanceQualityEvidenceAdapter.AssetIdentityRow(assetKey, datasetId);
    }

    private static JdbcGovernanceQualityEvidenceAdapter.RunRow run(
        String status,
        Instant finishedAt,
        UUID datasetId,
        UUID versionId,
        UUID bindingId
    ) {
        return new JdbcGovernanceQualityEvidenceAdapter.RunRow(
            RUN_ID,
            RULE_ID,
            versionId,
            bindingId,
            datasetId,
            status,
            finishedAt,
            AS_OF.minusSeconds(120)
        );
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {

        private final List<JdbcGovernanceQualityEvidenceAdapter.BindingRow> bindings;
        private final List<JdbcGovernanceQualityEvidenceAdapter.RunRow> runs;
        private final List<JdbcGovernanceQualityEvidenceAdapter.AssetIdentityRow> assets;
        private final List<String> queries = new ArrayList<>();
        private final List<List<Object>> arguments = new ArrayList<>();

        private RecordingJdbcTemplate(
            List<JdbcGovernanceQualityEvidenceAdapter.AssetIdentityRow> assets,
            List<JdbcGovernanceQualityEvidenceAdapter.BindingRow> bindings,
            List<JdbcGovernanceQualityEvidenceAdapter.RunRow> runs
        ) {
            this.assets = assets;
            this.bindings = bindings;
            this.runs = runs;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            queries.add(sql);
            arguments.add(List.of(args));
            if (sql.contains("catalog_asset_semantic_projection")) return (List<T>) assets;
            return (List<T>) (sql.contains("from gov_rule_binding") ? bindings : runs);
        }

        private List<String> queries() {
            return queries;
        }

        private List<List<Object>> arguments() {
            return arguments;
        }
    }
}
