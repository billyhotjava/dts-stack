package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceContract;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.BusinessProcessDto;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.BusinessProcessRequest;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.ConformedDimensionRequest;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.DimensionInUseException;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.ModelingCandidateConfirmationRequest;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.ModelingCandidateConfirmationResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class Sprint64GovernanceResourceTest {

    private final Sprint64GovernanceService service = mock(Sprint64GovernanceService.class);
    private final AuditService audit = mock(AuditService.class);
    private final Sprint64GovernanceResource resource = new Sprint64GovernanceResource(service, audit);

    @Test
    void processEndpointsKeepDomainAndProcessIdInTheContract() {
        UUID domainId = UUID.randomUUID();
        BusinessProcessDto process = new BusinessProcessDto(1, "node-plan-loop", domainId, "节点计划闭环", null, "MANUAL", null, null, true, null, null);
        when(service.createProcess(eq(domainId), any(BusinessProcessRequest.class))).thenReturn(process);

        ApiResponse<BusinessProcessDto> response = resource.createProcess(domainId, new BusinessProcessRequest("node-plan-loop", "节点计划闭环", null));

        assertThat(response.getData()).isEqualTo(process);
        verify(service).createProcess(eq(domainId), any(BusinessProcessRequest.class));
    }

    @Test
    void contractEndpointsExposeLayersAndAllowAnEmptyDomainDimensionCatalog() {
        when(service.listWarehouseLayers()).thenReturn(Sprint64GovernanceContract.warehouseLayers());
        when(service.listConformedDimensions(any())).thenReturn(List.of());

        assertThat(resource.listWarehouseLayers().getData()).hasSize(6);
        assertThat(resource.listConformedDimensions(UUID.randomUUID()).getData()).isEmpty();
    }

    @Test
    void dimensionAndCandidateEndpointsDelegateToTheDomainService() {
        UUID domainId = UUID.randomUUID();
        ConformedDimensionRequest request = new ConformedDimensionRequest("organization", "组织机构", "dim_organization");
        Sprint64GovernanceContract.ConformedDimensionDto dimension = new Sprint64GovernanceContract.ConformedDimensionDto(
            "organization",
            "组织机构",
            "dim_organization",
            List.of(domainId.toString()),
            "MANUAL",
            null,
            null,
            true
        );
        ModelingCandidateConfirmationRequest confirmation = new ModelingCandidateConfirmationRequest(
            List.of("process-a"),
            List.of("organization")
        );
        ModelingCandidateConfirmationResult confirmationResult = new ModelingCandidateConfirmationResult(1, 1);
        when(service.createConformedDimension(domainId, request)).thenReturn(dimension);
        when(service.confirmModelingCandidates(domainId, confirmation)).thenReturn(confirmationResult);

        assertThat(resource.createConformedDimension(domainId, request).getData()).isEqualTo(dimension);
        assertThat(resource.confirmModelingCandidates(domainId, confirmation).getData()).isEqualTo(confirmationResult);
        assertThat(resource.deleteConformedDimension(domainId, "organization").getData()).isTrue();
        verify(service).deleteConformedDimension(domainId, "organization");
    }

    @Test
    void referencedDimensionMapsToConflict() {
        UUID domainId = UUID.randomUUID();
        doThrow(new DimensionInUseException("organization"))
            .when(service)
            .deleteConformedDimension(domainId, "organization");

        assertThatThrownBy(() -> resource.deleteConformedDimension(domainId, "organization"))
            .isInstanceOfSatisfying(
                ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT)
            );
    }
}
