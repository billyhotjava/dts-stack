package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ApplyRequest;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ApplyResponse;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ApplySummary;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.AttemptStatus;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.BeginDisposition;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyService;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.Kind;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.ModelSpecImportPreviewException;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.PreviewContext;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.PreviewRequest;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.PreviewResponse;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.PreviewRunResponse;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.PreviewSummary;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.RunStatus;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ModelSpecImportResourceTest {

    @Mock
    private ModelSpecImportPreviewService service;

    @Mock
    private ModelSpecImportPreviewRequestParser requestParser;

    @Mock
    private ModelSpecImportPreviewAudit audit;

    @Mock
    private ModelSpecImportPreviewAdmissionGate admissionGate;

    @Mock
    private ModelSpecImportApplyService applyService;

    @Mock
    private ModelSpecImportPreviewAdmissionGate.Admission admission;

    @Test
    void mapsExpiredAndValidationFailuresToStableApiCodes() {
        ModelSpecImportResource resource = new ModelSpecImportResource(service, requestParser, audit, admissionGate);

        var expired = resource.handlePreviewError(
            new ModelSpecImportPreviewException(
                "MODEL_IMPORT_PREVIEW_STALE",
                "Model import preview has expired",
                Kind.GONE,
                null
            )
        );
        var invalid = resource.handlePreviewError(
            new ModelSpecImportPreviewException(
                "MODEL_PACKAGE_SCHEMA_INVALID",
                "Invalid package",
                Kind.BAD_REQUEST,
                null
            )
        );

        assertThat(expired.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(expired.getBody()).isNotNull();
        assertThat(expired.getBody().getCode()).isEqualTo("MODEL_IMPORT_PREVIEW_STALE");
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalid.getBody()).isNotNull();
        assertThat(invalid.getBody().getCode()).isEqualTo("MODEL_PACKAGE_SCHEMA_INVALID");
        verify(audit).rejected("MODEL_IMPORT_PREVIEW_STALE");
        verify(audit).rejected("MODEL_PACKAGE_SCHEMA_INVALID");
    }

    @Test
    void mapsPreviewQuotaFailuresToTooManyRequests() {
        ModelSpecImportResource resource = new ModelSpecImportResource(service, requestParser, audit, admissionGate);

        var response = resource.handlePreviewError(
            new ModelSpecImportPreviewException(
                "MODEL_IMPORT_PREVIEW_QUOTA_EXCEEDED",
                "Active model import preview storage quota is exceeded",
                Kind.RATE_LIMITED,
                null
            )
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("MODEL_IMPORT_PREVIEW_QUOTA_EXCEEDED");
        verify(audit).rejected("MODEL_IMPORT_PREVIEW_QUOTA_EXCEEDED");
    }

    @Test
    void writesDomainAuditEvenWhenTheCallerRequestsSilentHttpAudit() {
        ModelSpecImportResource resource = new ModelSpecImportResource(service, requestParser, audit, admissionGate);
        HttpServletRequest httpRequest = org.mockito.Mockito.mock(HttpServletRequest.class);
        UUID planId = UUID.randomUUID();
        PreviewRequest parsed = new PreviewRequest(null, new PreviewContext(planId, Map.of(), Map.of()), List.of());
        PreviewResponse response = new PreviewResponse(
            UUID.randomUUID(),
            "a".repeat(64),
            "b".repeat(64),
            new PreviewSummary(1, 1, 0, 1, 0, 0, 0),
            List.of()
        );
        org.mockito.Mockito.lenient().when(httpRequest.getHeader("X-Audit-Silent")).thenReturn("true");
        when(admissionGate.enter()).thenReturn(admission);
        when(requestParser.parse(httpRequest)).thenReturn(parsed);
        when(service.preview(parsed)).thenReturn(response);

        resource.preview(httpRequest);

        org.mockito.InOrder admissionOrder = org.mockito.Mockito.inOrder(admissionGate, requestParser, admission, service);
        admissionOrder.verify(admissionGate).enter();
        admissionOrder.verify(requestParser).parse(httpRequest);
        admissionOrder.verify(admission).admitPlan(planId);
        admissionOrder.verify(service).preview(parsed);
        verify(audit).success(response);
        verify(admission).close();
    }

    @Test
    void mapsAdmissionRejectionDirectlyToTooManyRequests() {
        ModelSpecImportResource resource = new ModelSpecImportResource(service, requestParser, audit, admissionGate);
        var response = resource.handleRequestLimit(
            new ModelSpecImportPreviewRequestParser.RequestLimitException(
                HttpStatus.TOO_MANY_REQUESTS,
                "MODEL_IMPORT_PREVIEW_RATE_LIMITED",
                "Too many preview requests"
            )
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("MODEL_IMPORT_PREVIEW_RATE_LIMITED");
        verify(audit).rejected("MODEL_IMPORT_PREVIEW_RATE_LIMITED");
    }

    @Test
    void getDelegatesToAuthorizedServiceWithoutTriggeringGlobalCleanup() {
        ModelSpecImportResource resource = new ModelSpecImportResource(service, requestParser, audit, admissionGate);
        UUID runId = UUID.randomUUID();
        PreviewRunResponse response = new PreviewRunResponse(
            runId,
            UUID.randomUUID(),
            "a".repeat(64),
            "b".repeat(64),
            RunStatus.PREVIEWED,
            Instant.now().plusSeconds(60),
            new PreviewSummary(0, 0, 0, 0, 0, 0, 0),
            List.of()
        );
        when(service.get(runId)).thenReturn(response);

        ApiResponse<PreviewRunResponse> result = resource.get(runId);

        assertThat(result.getData()).isEqualTo(response);
        verify(service).get(runId);
    }

    @Test
    void applyUsesTheBoundedRouteParserBeforeTheApplyService() {
        ModelSpecImportResource resource = new ModelSpecImportResource(
            service,
            requestParser,
            audit,
            admissionGate,
            applyService
        );
        HttpServletRequest httpRequest = org.mockito.Mockito.mock(HttpServletRequest.class);
        UUID runId = UUID.randomUUID();
        ApplyRequest parsed = new ApplyRequest(
            runId,
            "a".repeat(64),
            List.of("model.pjm.fact"),
            "apply-key"
        );
        ApplyResponse response = new ApplyResponse(
            UUID.randomUUID(),
            runId,
            BeginDisposition.REPLAY,
            AttemptStatus.SUCCEEDED,
            ApplySummary.EMPTY,
            List.of()
        );
        when(requestParser.parseApply(httpRequest)).thenReturn(parsed);
        when(applyService.apply(parsed)).thenReturn(response);

        assertThat(resource.apply(httpRequest).getData()).isEqualTo(response);
        verify(requestParser).parseApply(httpRequest);
        verify(applyService).apply(parsed);
    }
}
