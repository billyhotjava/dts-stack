package com.yuzhi.dts.platform.service.modeling.dbtdraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class DbtImplementationDraftRejectionAuditTest {

    private static final String MODEL_ID = "20000000-0000-0000-0000-000000000083";
    private static final String DRAFT_ID = "30000000-0000-0000-0000-000000000083";

    @Test
    void mapsTheRouteToItsPublicActionAndWritesOnlyBoundedMetadata() {
        DbtImplementationDraftAuditRecorder recorder = mock(DbtImplementationDraftAuditRecorder.class);
        DbtImplementationDraftRejectionAudit rejectionAudit = new DbtImplementationDraftRejectionAudit(recorder, "tenant-83");
        MockHttpServletRequest request = request("POST", "/" + DRAFT_ID + "/commit");

        rejectionAudit.recordBodyTooLarge(request, "correlation-83", 17_825_793L);

        verify(recorder).recordFailure(
            eq("MODELING_DBT_DRAFT_COMMIT"),
            eq(DRAFT_ID),
            argThat(payload -> safe(payload, "DBT_DRAFT_REQUEST_TOO_LARGE", "correlation-83"))
        );
    }

    @Test
    void propagatesStrictAuditFailureForPreServiceValidationRejections() {
        DbtImplementationDraftAuditRecorder recorder = mock(DbtImplementationDraftAuditRecorder.class);
        DbtImplementationDraftRejectionAudit rejectionAudit = new DbtImplementationDraftRejectionAudit(recorder, "tenant-83");
        MockHttpServletRequest request = request("PUT", "/" + DRAFT_ID + "/files");
        doThrow(new IllegalStateException("durable audit unavailable"))
            .when(recorder)
            .recordFailure(eq("MODELING_DBT_DRAFT_SAVE"), eq(DRAFT_ID), org.mockito.ArgumentMatchers.any());

        assertThatThrownBy(() -> rejectionAudit.recordValidationFailure(request, "correlation-83", 3))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("durable audit unavailable");
    }

    @Test
    void recordsMalformedJsonWithTrustedTenantAndWithoutRequestContent() {
        DbtImplementationDraftAuditRecorder recorder = mock(DbtImplementationDraftAuditRecorder.class);
        DbtImplementationDraftRejectionAudit rejectionAudit = new DbtImplementationDraftRejectionAudit(recorder, "tenant-83");
        MockHttpServletRequest request = request("POST", "");

        rejectionAudit.recordMalformedBody(request, "correlation-83");

        verify(recorder).recordFailure(
            eq("MODELING_DBT_DRAFT_CREATE"),
            eq(MODEL_ID),
            argThat(payload ->
                safe(payload, "DBT_DRAFT_REQUEST_MALFORMED", "correlation-83") &&
                "tenant-83".equals(payload.get("tenantId"))
            )
        );
    }

    private static MockHttpServletRequest request(String method, String suffix) {
        String path = "/api/modeling/model-specs/" + MODEL_ID + "/dbt-drafts" + suffix;
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return request;
    }

    private static boolean safe(Map<String, Object> payload, String code, String correlationId) {
        String serialized = String.valueOf(payload);
        return payload != null &&
        code.equals(payload.get("errorCode")) &&
        correlationId.equals(payload.get("correlationId")) &&
        "tenant-83".equals(payload.get("tenantId")) &&
        !serialized.contains("secret-body") &&
        !serialized.contains("select ") &&
        !serialized.contains("{{");
    }
}
