package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ModelingVNextWriterCompatibilityContractTest {

    @Test
    void legacyWriterSuppliesRevisionTenantAndCannotUpdateCanonicalRows() throws Exception {
        String source = source();

        assertThat(source)
            .contains("contract_version = 1")
            .contains("tenant_id, contract_version, spec_json")
            .contains("modeling_model_spec_revision.contract_version = 1")
            .contains("MODEL_SPEC_LEGACY_READONLY");
    }

    @Test
    void canonicalReleaseRegistrationUsesPlanAndConfirmedDomainInsteadOfBusinessObject() throws Exception {
        assertThat(source())
            .contains("isCanonicalModelSpec(tenantId, modelSpecId)")
            .contains("canonicalRegistrationValid(tenantId, modelSpecId)")
            .contains("d.confirmation_status = 'CONFIRMED'");
    }

    private static String source() throws Exception {
        java.nio.file.Path modulePath = java.nio.file.Path.of(
            "src/main/java/com/yuzhi/dts/platform/service/modeling/ModelingVNextApplicationService.java"
        );
        java.nio.file.Path repositoryPath = java.nio.file.Path.of(
            "source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelingVNextApplicationService.java"
        );
        return java.nio.file.Files.readString(java.nio.file.Files.exists(modulePath) ? modulePath : repositoryPath, StandardCharsets.UTF_8);
    }
}
