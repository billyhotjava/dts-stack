package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.infra.InfraExternalRunLog;
import com.yuzhi.dts.platform.repository.infra.InfraExternalRunLogRepository;
import com.yuzhi.dts.platform.service.ops.ExternalRunLogService;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DbtRunLifecycleTestEvidenceAdapterTest {

    @Test
    void resolvesOnlyPersistedTerminalDbtEvidence() {
        InfraExternalRunLogRepository repository = mock(InfraExternalRunLogRepository.class);
        InfraExternalRunLog run = new InfraExternalRunLog();
        run.setExternalRunId("dbt-run-7");
        run.setStatus("SUCCESS");
        when(repository.findFirstByEntryKeyIgnoreCaseAndExternalRunId(ExternalRunLogService.ENTRY_DBT, "dbt-run-7"))
            .thenReturn(Optional.of(run));

        assertThat(new DbtRunLifecycleTestEvidenceAdapter(repository).verify("dbt-run-7").status()).isEqualTo("PASSED");
    }

    @Test
    void rejectsClientReferencesThatHaveNoPersistedDbtRun() {
        InfraExternalRunLogRepository repository = mock(InfraExternalRunLogRepository.class);
        when(repository.findFirstByEntryKeyIgnoreCaseAndExternalRunId(ExternalRunLogService.ENTRY_DBT, "missing"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> new DbtRunLifecycleTestEvidenceAdapter(repository).verify("missing"))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_TEST_EXTERNAL_RUN_NOT_FOUND");
    }
}
