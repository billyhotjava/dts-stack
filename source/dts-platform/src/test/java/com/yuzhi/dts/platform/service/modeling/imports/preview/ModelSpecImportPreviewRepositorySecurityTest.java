package com.yuzhi.dts.platform.service.modeling.imports.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.imports.ModelPackageFixtures;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyPayloadCodec;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.RunStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class ModelSpecImportPreviewRepositorySecurityTest {

    @Test
    void rejectsSinglePreviewLargerThanPlanStorageBudgetBeforeDatabaseAccess() {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        ModelSpecImportPreviewRepository repository = new ModelSpecImportPreviewRepository(jdbc, new ObjectMapper());

        assertThatThrownBy(() ->
            repository.requirePreviewCapacity("tenant-a", UUID.randomUUID(), 129L * 1024L * 1024L)
        )
            .isInstanceOf(ModelSpecImportPreviewContract.ModelSpecImportPreviewException.class)
            .extracting("code")
            .isEqualTo("MODEL_IMPORT_PREVIEW_QUOTA_EXCEEDED");
    }

    @Test
    void saveStripsRawCompiledSqlAndFreeFormConfigFromDurablePackagePayload() throws Exception {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        ModelSpecImportPreviewRepository repository = new ModelSpecImportPreviewRepository(jdbc, new ObjectMapper());
        UUID runId = UUID.randomUUID();
        Instant now = Instant.now();
        String packageJson = """
            {"models":[{"sql":{"rawSql":"secret raw","compiledSql":"secret compiled","effectiveSql":"apply sql"},"config":{"password":"secret"}}]}
            """;
        ObjectMapper objectMapper = new ObjectMapper();
        ModelSpecImportApplyPayloadCodec.SanitizedPayload payload = new ModelSpecImportApplyPayloadCodec(objectMapper).sanitize(
            ModelPackageFixtures.validPackage()
        );
        String applyPlanJson =
            "{\"runId\":\"" +
            runId +
            "\",\"packageChecksum\":\"a\",\"applyPayloadChecksum\":\"" +
            payload.checksum() +
            "\",\"topology\":[],\"candidates\":[]}";
        var run = new ModelSpecImportPreviewRepository.PersistedRun(
            runId,
            "tenant-a",
            UUID.randomUUID(),
            "a".repeat(64),
            "dts.model-package/v1",
            packageJson,
            "{}",
            "{}",
            payload.json(),
            payload.checksum(),
            applyPlanJson,
            new ModelSpecImportApplyPayloadCodec(objectMapper).checksum(objectMapper.readTree(applyPlanJson)),
            "b".repeat(64),
            RunStatus.PREVIEWED,
            "{}",
            now.plusSeconds(60),
            "owner",
            now
        );

        repository.save(run, List.of());

        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), values.capture());
        String persistedPackage = (String) values.getValue()[5];
        assertThat(persistedPackage).doesNotContain("secret raw", "secret compiled", "password", "config");
        assertThat(persistedPackage).contains("apply sql");
    }

    @Test
    void redactsExpiredRunPayloadsWithoutDeletingTheRunAuditSummary() {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        ModelSpecImportPreviewRepository repository = new ModelSpecImportPreviewRepository(jdbc, new ObjectMapper());

        Instant cutoff = Instant.parse("2026-07-25T00:00:00Z");
        Timestamp timestamp = Timestamp.from(cutoff);
        repository.redactExpiredPayloads(cutoff);

        ArgumentCaptor<String> itemSql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(itemSql.capture(), eq(timestamp), eq(timestamp));
        assertThat(itemSql.getValue()).contains(
            "modeling_model_spec_import_run_item",
            "proposed_model_spec_json",
            "proposed_implementation_json",
            "issues_json"
        );

        ArgumentCaptor<String> runSql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(runSql.capture(), eq(timestamp), eq(timestamp), eq(timestamp));
        assertThat(runSql.getValue()).contains(
            "payload_redacted_at",
            "package_json",
            "request_json",
            "context_snapshot_json",
            "apply_payload_json",
            "apply_plan_json"
        );
        assertThat(runSql.getValue()).doesNotContain("delete", "summary_json");
    }
}
