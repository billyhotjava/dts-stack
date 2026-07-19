package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.domain.infra.InfraExternalRunLog;
import com.yuzhi.dts.platform.repository.infra.InfraExternalRunLogRepository;
import com.yuzhi.dts.platform.service.ops.ExternalRunLogService;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class DbtRunLifecycleTestEvidenceAdapter implements ModelLifecycleTestEvidencePort {

    private final InfraExternalRunLogRepository runs;

    public DbtRunLifecycleTestEvidenceAdapter(InfraExternalRunLogRepository runs) {
        this.runs = runs;
    }

    @Override
    public TestEvidence verify(String externalRunId) {
        if (externalRunId == null || externalRunId.isBlank()) {
            throw new ModelSpecException(
                "MODEL_TEST_EXTERNAL_RUN_REQUIRED",
                "A persisted dbt test run is required",
                ModelSpecException.Kind.UNPROCESSABLE
            );
        }
        InfraExternalRunLog run = runs
            .findFirstByEntryKeyIgnoreCaseAndExternalRunId(ExternalRunLogService.ENTRY_DBT, externalRunId.trim())
            .orElseThrow(() ->
                new ModelSpecException(
                    "MODEL_TEST_EXTERNAL_RUN_NOT_FOUND",
                    "The dbt test run was not found",
                    ModelSpecException.Kind.NOT_FOUND
                )
            );
        String status = normalize(run.getStatus());
        if (!"PASSED".equals(status) && !"FAILED".equals(status)) {
            throw new ModelSpecException(
                "MODEL_TEST_EXTERNAL_RUN_PENDING",
                "The dbt test run has not reached a terminal state",
                ModelSpecException.Kind.CONFLICT
            );
        }
        return new TestEvidence(run.getExternalRunId(), status, run.getMessage());
    }

    private static String normalize(String status) {
        String value = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if ("SUCCESS".equals(value) || "SUCCEEDED".equals(value) || "PASS".equals(value)) return "PASSED";
        if ("ERROR".equals(value) || "FAIL".equals(value)) return "FAILED";
        return value;
    }
}
