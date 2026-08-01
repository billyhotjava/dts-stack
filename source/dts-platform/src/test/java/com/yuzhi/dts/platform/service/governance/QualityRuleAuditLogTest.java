package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.GovernanceProperties;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleBindingRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleVersionRepository;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(OutputCaptureExtension.class)
class QualityRuleAuditLogTest {

    @Test
    void serializationFailureLogUsesStableMetadataAndNeverIncludesExceptionSecrets(CapturedOutput output) throws Exception {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(any())).thenThrow(
            new SensitiveJsonProcessingException("password=top-secret token=leaked-token")
        );
        QualityRuleService service = new QualityRuleService(
            mock(GovRuleRepository.class),
            mock(GovRuleVersionRepository.class),
            mock(GovRuleBindingRepository.class),
            mock(CatalogDatasetRepository.class),
            mock(DefaultLakeDatasetGuard.class),
            mock(QualityAuditRecorder.class),
            objectMapper,
            mock(GovernanceProperties.class),
            mock(AccessChecker.class),
            mock(OrganizationVisibilityService.class),
            mock(QualityEffectiveDepartmentResolver.class),
            mock(QualityDatasetReadGuard.class)
        );

        String serialized = ReflectionTestUtils.invokeMethod(service, "writeDefinition", Map.of("sql", "select 1"));

        assertThat(serialized).isEqualTo("{}");
        assertThat(output.getAll())
            .contains("event=quality_rule_definition_serialize_failed")
            .contains("errorType=SensitiveJsonProcessingException")
            .doesNotContain("top-secret")
            .doesNotContain("leaked-token")
            .doesNotContain("password=");
    }

    private static final class SensitiveJsonProcessingException extends JsonProcessingException {

        private SensitiveJsonProcessingException(String message) {
            super(message);
        }
    }
}
