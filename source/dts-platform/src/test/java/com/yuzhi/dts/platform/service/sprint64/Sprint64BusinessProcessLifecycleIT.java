package com.yuzhi.dts.platform.service.sprint64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.platform.IntegrationTest;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.BusinessProcessDto;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.BusinessProcessRequest;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.ModelingCandidateConfirmationRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class Sprint64BusinessProcessLifecycleIT {

    @Autowired
    private Sprint64GovernanceService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void deletingAConfirmedProcessRetiresTheStableIdAndPreservesBusMatrixEvidence() {
        UUID domainId = UUID.randomUUID();
        String processId = "project_execution_snapshot";
        try {
            BusinessProcessDto created = service.createProcess(
                domainId,
                new BusinessProcessRequest(processId, "项目执行快照", "一个项目每天一行")
            );
            jdbc.update(
                "insert into sprint64_bus_matrix (domain_id, process_id, dimension_id, enabled) values (?, ?, ?, true)",
                domainId,
                processId,
                "project"
            );

            service.deleteProcess(domainId, processId);

            BusinessProcessDto retired = service
                .listProcesses(domainId)
                .stream()
                .filter(item -> item.id().equals(created.id()))
                .findFirst()
                .orElseThrow();
            assertThat(retired.lifecycleStatus()).isEqualTo("RETIRED");
            assertThat(retired.confirmed()).isFalse();
			assertThatThrownBy(() ->
				service.confirmModelingCandidates(
					domainId,
					new ModelingCandidateConfirmationRequest(List.of(processId), List.of())
				)
			).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("已停用");
            assertThat(
                jdbc.queryForObject(
                    "select count(*) from sprint64_bus_matrix where domain_id = ? and process_id = ?",
                    Integer.class,
                    domainId,
                    processId
                )
            ).isEqualTo(1);
        } finally {
            jdbc.update("delete from sprint64_bus_matrix where domain_id = ?", domainId);
            jdbc.update("delete from sprint64_business_process where domain_id = ?", domainId);
        }
    }
}
