package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.infra.InfraExternalRunLog;
import com.yuzhi.dts.platform.repository.infra.InfraExternalRunLogRepository;
import com.yuzhi.dts.platform.service.ops.ExternalRunLogService;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class DbtRunLifecycleTestEvidenceAdapter implements ModelLifecycleTestEvidencePort {

    private final InfraExternalRunLogRepository runs;
    private final ObjectMapper objectMapper;

    public DbtRunLifecycleTestEvidenceAdapter(InfraExternalRunLogRepository runs, ObjectMapper objectMapper) {
        this.runs = runs;
        this.objectMapper = objectMapper;
    }

    @Override
    public TestEvidence verify(VerificationRequest request) {
        String externalRunId = request == null ? null : request.externalRunId();
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
        requireBoundContext(run, request);
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

    private void requireBoundContext(InfraExternalRunLog run, VerificationRequest request) {
        JsonNode conf = metrics(run).path("conf");
        boolean matches =
            Boolean.TRUE.equals(run.getEnabled()) &&
            Objects.equals(trim(run.getOwnerDept()), trim(request.tenantId())) &&
            Objects.equals(run.getArtifactId(), request.modelSpecId()) &&
            Objects.equals(text(conf, "modelSpecId"), request.modelSpecId().toString()) &&
            conf.path("implementationRevision").asInt(0) == request.implementationRevision() &&
            Objects.equals(text(conf, "implementationChecksum"), request.implementationChecksum()) &&
            Objects.equals(text(conf, "projectKey"), request.projectKey()) &&
            Objects.equals(text(conf, "dbtUniqueId"), request.dbtUniqueId()) &&
            selectorContains(text(conf, "models"), request.dbtUniqueId());
        if (!matches) {
            throw new ModelSpecException(
                "MODEL_TEST_EXTERNAL_RUN_CONTEXT_MISMATCH",
                "The dbt test run is not bound to the current tenant, model and implementation",
                ModelSpecException.Kind.CONFLICT
            );
        }
    }

    private JsonNode metrics(InfraExternalRunLog run) {
        try {
            return objectMapper.readTree(run.getMetricsJson());
        } catch (Exception malformed) {
            return objectMapper.createObjectNode();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : trim(value.asText());
    }

    private static boolean selectorContains(String selector, String dbtUniqueId) {
        if (selector == null || dbtUniqueId == null) return false;
        String resourceName = dbtUniqueId.substring(dbtUniqueId.lastIndexOf('.') + 1);
        for (String raw : selector.split("\\s+")) {
            String token = raw.trim();
            while (token.startsWith("+")) token = token.substring(1);
            while (token.endsWith("+")) token = token.substring(0, token.length() - 1);
            if (token.regionMatches(true, 0, "model:", 0, 6)) token = token.substring(6);
            if (token.equals(resourceName) || token.equals(dbtUniqueId)) return true;
        }
        return false;
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalize(String status) {
        String value = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if ("SUCCESS".equals(value) || "SUCCEEDED".equals(value) || "PASS".equals(value)) return "PASSED";
        if ("ERROR".equals(value) || "FAIL".equals(value)) return "FAILED";
        return value;
    }
}
