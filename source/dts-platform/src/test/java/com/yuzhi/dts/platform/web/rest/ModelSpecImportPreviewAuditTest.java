package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.imports.ModelPackageFixtures;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.CompatibilityIssue;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.DbtCompatibilityView;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.ImportProjectionCompatibility;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.InspectArchiveResponse;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.InspectionCompatibility;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.MaterializationCompatibility;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ModelSpecImportPreviewAuditTest {

    @Test
    void inspectSuccessUsesSafeCorrelationAndOmitsDiagnosticMessages() {
        AuditService auditService = mock(AuditService.class);
        ModelSpecImportPreviewAudit audit = new ModelSpecImportPreviewAudit(auditService);
        var modelPackage = ModelPackageFixtures.validPackage();
        var compatibility = new DbtCompatibilityView(
            InspectionCompatibility.SUPPORTED,
            ImportProjectionCompatibility.IMPORTABLE,
            MaterializationCompatibility.CERTIFIED,
            "1.10.0",
            "v12",
            "postgres",
            "1.9.0",
            "certified-profile",
            List.of(
                new CompatibilityIssue(
                    "DBT_RUNTIME_NOT_CERTIFIED",
                    "MATERIALIZATION",
                    "RUNTIME",
                    "SECRET SQL select * from payroll",
                    false,
                    "USE_CERTIFIED_RUNTIME",
                    "compatibility-correlation"
                )
            )
        );
        var response = new InspectArchiveResponse(modelPackage, compatibility, "secret-proof", Instant.now());
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);

        audit.inspectSuccess(response, 1024, "request-correlation");

        verify(auditService)
            .auditActionStrict(
                eq(ModelSpecImportPreviewAudit.INSPECT_ACTION),
                eq(AuditStage.SUCCESS),
                eq(modelPackage.packageId()),
                payload.capture()
            );
        assertThat(payload.getValue()).isInstanceOf(Map.class);
        assertThat(payload.getValue().toString())
            .contains(
                "request-correlation",
                modelPackage.packageChecksum(),
                "DBT_RUNTIME_NOT_CERTIFIED",
                "compatibility-correlation"
            )
            .doesNotContain("SECRET SQL", "select * from payroll", "secret-proof", "message");
    }

    @Test
    void rejectionUsesSpecificActionAndSafeCorrelationContext() {
        AuditService auditService = mock(AuditService.class);
        ModelSpecImportPreviewAudit audit = new ModelSpecImportPreviewAudit(auditService);
        UUID runId = UUID.randomUUID();
        String packageChecksum = "a".repeat(64);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);

        audit.rejected(
            ModelSpecImportPreviewAudit.RETRY_ACTION,
            "MODEL_IMPORT_RETRY_NOT_ALLOWED",
            runId.toString(),
            "request-correlation",
            runId,
            packageChecksum
        );

        verify(auditService)
            .auditActionStrict(
                eq(ModelSpecImportPreviewAudit.RETRY_ACTION),
                eq(AuditStage.FAIL),
                eq(runId.toString()),
                payload.capture()
            );
        assertThat(payload.getValue()).isInstanceOf(Map.class);
        assertThat(payload.getValue().toString())
            .contains(
                "MODEL_IMPORT_RETRY_NOT_ALLOWED",
                "request-correlation",
                runId.toString(),
                packageChecksum
            )
            .doesNotContain("sql", "zip", "credential");
    }
}
