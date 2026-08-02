package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ApplyRequest;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ApplyResponse;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ModelSpecImportApplyException;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ApplySummary;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.AttemptStatus;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.BeginDisposition;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.RetryRequest;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyService;
import com.yuzhi.dts.platform.service.modeling.imports.ModelPackageFixtures;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.DbtCompatibilityView;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.ImportProjectionCompatibility;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.InspectionCompatibility;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.MaterializationCompatibility;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtCompatibilityEvaluator;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtModelArchiveInspectService;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtModelArchiveInspectService.ArchiveInspectionException;
import com.yuzhi.dts.platform.service.modeling.imports.proof.ModelSpecImportInspectionProofCodec;
import com.yuzhi.dts.platform.service.modeling.imports.proof.ModelSpecImportInspectionProofCodec.InspectionProofException;
import com.yuzhi.dts.platform.service.modeling.imports.proof.ModelSpecImportInspectionProofCodec.IssuedProof;
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
import org.springframework.mock.web.MockMultipartFile;

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
    private DbtModelArchiveInspectService archiveInspectService;

    @Mock
    private ModelSpecImportInspectionProofCodec inspectionProofCodec;

    @Mock
    private DbtCompatibilityEvaluator compatibilityEvaluator;

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
    }

    @Test
    void delegatesPreviewEvenWhenTheCallerRequestsSilentHttpAudit() {
        ModelSpecImportResource resource = resource();
        HttpServletRequest httpRequest = org.mockito.Mockito.mock(HttpServletRequest.class);
        UUID planId = UUID.randomUUID();
        var modelPackage = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(ModelPackageFixtures.validPackage());
        PreviewRequest parsed = new PreviewRequest(
            modelPackage,
            "inspection-proof",
            new PreviewContext(planId, Map.of(), Map.of()),
            List.of(),
            List.of()
        );
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

        org.mockito.InOrder admissionOrder = org.mockito.Mockito.inOrder(
            admissionGate,
            requestParser,
            inspectionProofCodec,
            admission,
            service
        );
        admissionOrder.verify(admissionGate).enter();
        admissionOrder.verify(requestParser).parse(httpRequest);
        admissionOrder.verify(inspectionProofCodec).verify("inspection-proof", modelPackage);
        admissionOrder.verify(admission).admitPlan(planId);
        admissionOrder.verify(service).preview(parsed);
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
            AttemptStatus.SUCCESS,
            ApplySummary.EMPTY,
            List.of()
        );
        when(requestParser.parseApply(httpRequest)).thenReturn(parsed);
        when(applyService.apply(parsed)).thenReturn(response);

        assertThat(resource.apply(httpRequest).getData()).isEqualTo(response);
        verify(requestParser).parseApply(httpRequest);
        verify(applyService).apply(parsed);
    }

    @Test
    void inspectArchiveDelegatesToTheDedicatedArchiveService() {
        ModelSpecImportResource resource = resource();
        MockMultipartFile archive = new MockMultipartFile("archive", "existing-dbt.zip", "application/zip", new byte[] { 1, 2 });
        var modelPackage = ModelPackageFixtures.validPackage();
        var compatibility = new DbtCompatibilityView(
            InspectionCompatibility.SUPPORTED,
            ImportProjectionCompatibility.IMPORTABLE,
            MaterializationCompatibility.NOT_CERTIFIED,
            null,
            modelPackage.dbt().manifestVersion(),
            modelPackage.dbt().adapterType(),
            null,
            null,
            List.of()
        );
        Instant expiresAt = Instant.parse("2026-08-02T01:30:00Z");
        when(admissionGate.enter()).thenReturn(admission);
        when(archiveInspectService.inspect(archive)).thenReturn(modelPackage);
        when(compatibilityEvaluator.evaluate(modelPackage)).thenReturn(compatibility);
        when(inspectionProofCodec.issue(modelPackage)).thenReturn(new IssuedProof("inspection-proof", expiresAt));

        var inspected = resource.inspectArchive(archive).getData();
        assertThat(inspected.modelPackage()).isEqualTo(modelPackage);
        assertThat(inspected.compatibility()).isEqualTo(compatibility);
        assertThat(inspected.inspectionProof()).isEqualTo("inspection-proof");
        assertThat(inspected.proofExpiresAt()).isEqualTo(expiresAt);
        org.mockito.InOrder inspectionOrder = org.mockito.Mockito.inOrder(
            admissionGate,
            archiveInspectService,
            admission
        );
        inspectionOrder.verify(admissionGate).enter();
        inspectionOrder.verify(archiveInspectService).inspect(archive);
        inspectionOrder.verify(admission).close();
        verify(audit).inspectSuccess(org.mockito.ArgumentMatchers.eq(inspected), org.mockito.ArgumentMatchers.eq(archive.getSize()), anyString());
    }

    @Test
    void blocksNonImportableArchiveWithoutIssuingAnInspectionProof() {
        ModelSpecImportResource resource = resource();
        MockMultipartFile archive = new MockMultipartFile(
            "archive",
            "blocked-dbt.zip",
            "application/zip",
            new byte[] { 1, 2 }
        );
        var modelPackage = ModelPackageFixtures.validPackage();
        var compatibility = new DbtCompatibilityView(
            InspectionCompatibility.SUPPORTED,
            ImportProjectionCompatibility.BLOCKED,
            MaterializationCompatibility.NOT_CERTIFIED,
            null,
            modelPackage.dbt().manifestVersion(),
            modelPackage.dbt().adapterType(),
            null,
            null,
            List.of()
        );
        when(admissionGate.enter()).thenReturn(admission);
        when(archiveInspectService.inspect(archive)).thenReturn(modelPackage);
        when(compatibilityEvaluator.evaluate(modelPackage)).thenReturn(compatibility);

        ModelSpecImportPreviewException blocked = org.assertj.core.api.Assertions.catchThrowableOfType(
            () -> resource.inspectArchive(archive),
            ModelSpecImportPreviewException.class
        );
        var response = resource.handlePreviewError(blocked);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("MODEL_IMPORT_PROJECTION_BLOCKED");
        verify(inspectionProofCodec, never()).issue(modelPackage);
        verify(admission).close();
    }

    @Test
    void mapsInspectionProofInvalidAndExpiredWithoutLeakingTheProof() {
        ModelSpecImportResource resource = resource();

        var invalid = resource.handleInspectionProofError(
            new InspectionProofException("DBT_IMPORT_INSPECTION_PROOF_INVALID", "invalid")
        );
        var expired = resource.handleInspectionProofError(
            new InspectionProofException("DBT_IMPORT_INSPECTION_PROOF_EXPIRED", "expired")
        );

        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(expired.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(invalid.getBody()).isNotNull();
        assertThat(expired.getBody()).isNotNull();
        assertThat(invalid.getBody().getCode()).isEqualTo("DBT_IMPORT_INSPECTION_PROOF_INVALID");
        assertThat(expired.getBody().getCode()).isEqualTo("DBT_IMPORT_INSPECTION_PROOF_EXPIRED");
    }

    @Test
    void mapsArchiveInspectionFailuresToStableApiCodesAndPayloadLimit() {
        ModelSpecImportResource resource = new ModelSpecImportResource(
            service,
            requestParser,
            audit,
            admissionGate,
            applyService,
            archiveInspectService
        );

        var response = resource.handleArchiveInspectionError(
            new ArchiveInspectionException("MODEL_IMPORT_ARCHIVE_TOO_LARGE", "dbt ZIP exceeds limit")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("MODEL_IMPORT_ARCHIVE_TOO_LARGE");
    }

    @Test
    void recordsInspectFailureWithTheInspectAction() {
        ModelSpecImportResource resource = resource();
        MockMultipartFile archive = new MockMultipartFile("archive", "bad.zip", "application/zip", new byte[] { 1 });
        ArchiveInspectionException failure = new ArchiveInspectionException(
            "MODEL_IMPORT_ARCHIVE_INVALID",
            "invalid archive"
        );
        when(admissionGate.enter()).thenReturn(admission);
        when(archiveInspectService.inspect(archive)).thenThrow(failure);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> resource.inspectArchive(archive))).isSameAs(failure);

        verify(audit)
            .rejected(
                org.mockito.ArgumentMatchers.eq(ModelSpecImportPreviewAudit.INSPECT_ACTION),
                org.mockito.ArgumentMatchers.eq("MODEL_IMPORT_ARCHIVE_INVALID"),
                org.mockito.ArgumentMatchers.eq("inspect"),
                anyString(),
                isNull(),
                isNull()
            );
        verify(admission).close();
    }

    @Test
    void recordsPreviewFailureWithThePreviewAction() {
        ModelSpecImportResource resource = resource();
        HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
        ModelSpecImportPreviewRequestParser.RequestLimitException failure =
            new ModelSpecImportPreviewRequestParser.RequestLimitException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "MODEL_IMPORT_PREVIEW_BODY_TOO_LARGE",
                "too large"
            );
        when(admissionGate.enter()).thenReturn(admission);
        when(requestParser.parse(request)).thenThrow(failure);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> resource.preview(request))).isSameAs(failure);

        verify(audit)
            .rejected(
                org.mockito.ArgumentMatchers.eq(ModelSpecImportPreviewAudit.PREVIEW_ACTION),
                org.mockito.ArgumentMatchers.eq("MODEL_IMPORT_PREVIEW_BODY_TOO_LARGE"),
                org.mockito.ArgumentMatchers.eq("preview"),
                anyString(),
                isNull(),
                isNull()
            );
        verify(admission).close();
    }

    @Test
    void recordsApplyFailureWithoutDuplicatingRetryTerminalAudit() {
        ModelSpecImportResource resource = resource();
        HttpServletRequest applyRequest = org.mockito.Mockito.mock(HttpServletRequest.class);
        HttpServletRequest retryRequest = org.mockito.Mockito.mock(HttpServletRequest.class);
        UUID runId = UUID.randomUUID();
        ApplyRequest parsedApply = new ApplyRequest(runId, "a".repeat(64), List.of("model.demo.a"), "apply-key");
        RetryRequest parsedRetry = new RetryRequest("a".repeat(64), "retry-key");
        ModelSpecImportApplyException applyFailure = new ModelSpecImportApplyException(
            "MODEL_IMPORT_APPLY_CONFLICT",
            "conflict",
            com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.Kind.CONFLICT,
            null
        );
        ApplyResponse retryResponse = new ApplyResponse(
            UUID.randomUUID(),
            runId,
            BeginDisposition.REPLAY,
            AttemptStatus.SUCCESS,
            ApplySummary.EMPTY,
            List.of()
        );
        when(requestParser.parseApply(applyRequest)).thenReturn(parsedApply);
        when(applyService.apply(parsedApply)).thenThrow(applyFailure);
        when(requestParser.parseRetry(retryRequest)).thenReturn(parsedRetry);
        when(applyService.retry(runId, parsedRetry)).thenReturn(retryResponse);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> resource.apply(applyRequest))).isSameAs(applyFailure);
        assertThat(resource.retry(runId, retryRequest).getData()).isEqualTo(retryResponse);

        verify(audit)
            .rejected(
                org.mockito.ArgumentMatchers.eq(ModelSpecImportPreviewAudit.APPLY_ACTION),
                org.mockito.ArgumentMatchers.eq("MODEL_IMPORT_APPLY_CONFLICT"),
                org.mockito.ArgumentMatchers.eq(runId.toString()),
                anyString(),
                org.mockito.ArgumentMatchers.eq(runId),
                isNull()
            );
    }

    @Test
    void recordsRetryFailureWithTheRetryActionAndRunIdentity() {
        ModelSpecImportResource resource = resource();
        HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
        UUID runId = UUID.randomUUID();
        RetryRequest parsed = new RetryRequest("a".repeat(64), "retry-key");
        ModelSpecImportApplyException failure = new ModelSpecImportApplyException(
            "MODEL_IMPORT_RETRY_NOT_ALLOWED",
            "retry not allowed",
            com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.Kind.CONFLICT,
            null
        );
        when(requestParser.parseRetry(request)).thenReturn(parsed);
        when(applyService.retry(runId, parsed)).thenThrow(failure);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> resource.retry(runId, request))).isSameAs(failure);

        verify(audit)
            .rejected(
                org.mockito.ArgumentMatchers.eq(ModelSpecImportPreviewAudit.RETRY_ACTION),
                org.mockito.ArgumentMatchers.eq("MODEL_IMPORT_RETRY_NOT_ALLOWED"),
                org.mockito.ArgumentMatchers.eq(runId.toString()),
                anyString(),
                org.mockito.ArgumentMatchers.eq(runId),
                isNull()
            );
    }

    private ModelSpecImportResource resource() {
        return new ModelSpecImportResource(
            service,
            requestParser,
            audit,
            admissionGate,
            applyService,
            archiveInspectService,
            inspectionProofCodec,
            compatibilityEvaluator
        );
    }
}
