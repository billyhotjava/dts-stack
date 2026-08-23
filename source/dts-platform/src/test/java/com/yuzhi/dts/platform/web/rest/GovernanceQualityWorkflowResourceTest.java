package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.governance.QualityWorkflowOrchestrator;
import com.yuzhi.dts.platform.service.governance.QualityWorkflowQueryService;
import com.yuzhi.dts.platform.service.governance.dto.QualityWorkflowRunDto;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class GovernanceQualityWorkflowResourceTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void retryWithoutAClientKeyUsesTheSourceWorkflowAsTheIdempotencyBoundary() {
        UUID workflowId = UUID.fromString("40000000-0000-0000-0000-000000000201");
        QualityWorkflowQueryService queryService = mock(QualityWorkflowQueryService.class);
        QualityWorkflowOrchestrator orchestrator = mock(QualityWorkflowOrchestrator.class);
        QualityWorkflowRunDto retried = mock(QualityWorkflowRunDto.class);
        GovernanceQualityWorkflowResource resource = new GovernanceQualityWorkflowResource(queryService, orchestrator);
        SecurityContextHolder
            .getContext()
            .setAuthentication(new TestingAuthenticationToken("xiezm", "n/a", AuthoritiesConstants.INST_DATA_OWNER));
        when(
            orchestrator.retryAuthorized(
                workflowId,
                "xiezm",
                "D01",
                "quality-workflow:retry:" + workflowId
            )
        ).thenReturn(retried);

        var response = resource.retry(workflowId, null, "D01");

        assertThat(response.getData()).isSameAs(retried);
        verify(orchestrator).retryAuthorized(
            workflowId,
            "xiezm",
            "D01",
            "quality-workflow:retry:" + workflowId
        );
    }
}
