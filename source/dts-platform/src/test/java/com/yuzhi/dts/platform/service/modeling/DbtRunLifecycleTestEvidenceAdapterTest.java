package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.infra.InfraExternalRunLog;
import com.yuzhi.dts.platform.repository.infra.InfraExternalRunLogRepository;
import com.yuzhi.dts.platform.service.ops.ExternalRunLogService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DbtRunLifecycleTestEvidenceAdapterTest {

    private static final UUID MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final String CHECKSUM = "a".repeat(64);

    @Test
    void resolvesOnlyPersistedTerminalDbtEvidence() {
        InfraExternalRunLogRepository repository = mock(InfraExternalRunLogRepository.class);
        InfraExternalRunLog run = new InfraExternalRunLog();
        run.setExternalRunId("dbt-run-7");
        run.setStatus("SUCCESS");
        run.setEnabled(true);
        run.setOwnerDept("tenant-a");
        run.setArtifactId(MODEL_ID);
        run.setMetricsJson(
            """
            {"conf":{"models":"fact","modelSpecId":"30000000-0000-0000-0000-000000000001",
            "implementationRevision":1,"implementationChecksum":"%s","projectKey":"pjm",
            "dbtUniqueId":"model.pjm.fact"}}
            """.formatted(CHECKSUM)
        );
        when(repository.findFirstByEntryKeyIgnoreCaseAndExternalRunId(ExternalRunLogService.ENTRY_DBT, "dbt-run-7"))
            .thenReturn(Optional.of(run));

        assertThat(new DbtRunLifecycleTestEvidenceAdapter(repository, new ObjectMapper()).verify(request("dbt-run-7")).status())
            .isEqualTo("PASSED");
    }

    @Test
    void rejectsClientReferencesThatHaveNoPersistedDbtRun() {
        InfraExternalRunLogRepository repository = mock(InfraExternalRunLogRepository.class);
        when(repository.findFirstByEntryKeyIgnoreCaseAndExternalRunId(ExternalRunLogService.ENTRY_DBT, "missing"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> new DbtRunLifecycleTestEvidenceAdapter(repository, new ObjectMapper()).verify(request("missing")))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_TEST_EXTERNAL_RUN_NOT_FOUND");
    }

    @Test
    void rejectsEvidenceFromAnotherTenantOrImplementation() {
        InfraExternalRunLogRepository repository = mock(InfraExternalRunLogRepository.class);
        InfraExternalRunLog run = new InfraExternalRunLog();
        run.setExternalRunId("dbt-run-7");
        run.setStatus("SUCCESS");
        run.setEnabled(true);
        run.setOwnerDept("tenant-b");
        run.setArtifactId(MODEL_ID);
        run.setMetricsJson("{}");
        when(repository.findFirstByEntryKeyIgnoreCaseAndExternalRunId(ExternalRunLogService.ENTRY_DBT, "dbt-run-7"))
            .thenReturn(Optional.of(run));

        assertThatThrownBy(() -> new DbtRunLifecycleTestEvidenceAdapter(repository, new ObjectMapper()).verify(request("dbt-run-7")))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_TEST_EXTERNAL_RUN_CONTEXT_MISMATCH");
    }

    private static ModelLifecycleTestEvidencePort.VerificationRequest request(String externalRunId) {
        return new ModelLifecycleTestEvidencePort.VerificationRequest(
            externalRunId,
            "tenant-a",
            MODEL_ID,
            1,
            CHECKSUM,
            "pjm",
            "model.pjm.fact"
        );
    }
}
