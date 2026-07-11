package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceContract;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.BusinessProcessDto;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.BusinessProcessRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Sprint64GovernanceResourceTest {

    private final Sprint64GovernanceService service = mock(Sprint64GovernanceService.class);
    private final AuditService audit = mock(AuditService.class);
    private final Sprint64GovernanceResource resource = new Sprint64GovernanceResource(service, audit);

    @Test
    void processEndpointsKeepDomainAndProcessIdInTheContract() {
        UUID domainId = UUID.randomUUID();
        BusinessProcessDto process = new BusinessProcessDto(1, "node-plan-loop", domainId, "节点计划闭环", null, null, null);
        when(service.createProcess(eq(domainId), any(BusinessProcessRequest.class))).thenReturn(process);

        ApiResponse<BusinessProcessDto> response = resource.createProcess(domainId, new BusinessProcessRequest("node-plan-loop", "节点计划闭环", null));

        assertThat(response.getData()).isEqualTo(process);
        verify(service).createProcess(eq(domainId), any(BusinessProcessRequest.class));
    }

    @Test
    void contractEndpointsExposeLayerAndDimensionSeeds() {
        when(service.listWarehouseLayers()).thenReturn(Sprint64GovernanceContract.warehouseLayers());
        when(service.listConformedDimensions(any())).thenReturn(Sprint64GovernanceContract.conformedDimensions());

        assertThat(resource.listWarehouseLayers().getData()).hasSize(5);
        assertThat(resource.listConformedDimensions(UUID.randomUUID()).getData()).hasSize(8);
    }
}
