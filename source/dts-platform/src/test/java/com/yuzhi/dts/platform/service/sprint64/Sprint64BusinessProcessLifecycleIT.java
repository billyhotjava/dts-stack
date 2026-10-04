package com.yuzhi.dts.platform.service.sprint64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.platform.IntegrationTest;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.BusinessProcessDto;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.BusinessProcessRequest;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.ModelingCandidateConfirmationRequest;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.BusinessProcessUpdateRequest;
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
    void editingPreservesIdentityAndReferencesAndCannotReviveRetiredProcesses() {
        UUID domainId = UUID.randomUUID();
        String processId = "prj_mgmt";
        try {
            BusinessProcessDto original = service.createProcess(domainId, new BusinessProcessRequest(processId, "项目管理", "原定义"));
            jdbc.update("insert into sprint64_bus_matrix (domain_id, process_id, dimension_id, enabled) values (?, ?, ?, true)",
                domainId, processId, "project");
            BusinessProcessDto updated = service.updateProcess(domainId, processId, new BusinessProcessUpdateRequest("项目管理修订", " "));
            assertThat(updated.id()).isEqualTo(original.id());
            assertThat(updated.domainId()).isEqualTo(original.domainId());
            assertThat(updated.processId()).isEqualTo(original.processId());
            assertThat(updated.sourceType()).isEqualTo(original.sourceType());
            assertThat(updated.confirmed()).isEqualTo(original.confirmed());
            assertThat(updated.name()).isEqualTo("项目管理修订");
            assertThat(updated.description()).isNull();
            assertThat(service.listProcesses(domainId)).hasSize(1);
            assertThat(jdbc.queryForObject("select count(*) from sprint64_bus_matrix where domain_id = ? and process_id = ?",
                Integer.class, domainId, processId)).isEqualTo(1);
            assertThatThrownBy(() -> service.updateProcess(UUID.randomUUID(), processId, new BusinessProcessUpdateRequest("错误数据域", null)))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.updateProcess(domainId, processId, new BusinessProcessUpdateRequest(" ", null)))
                .isInstanceOf(IllegalArgumentException.class);
            service.deleteProcess(domainId, processId);
            assertThatThrownBy(() -> service.updateProcess(domainId, processId, new BusinessProcessUpdateRequest("禁止恢复", null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("已停用");
            assertThat(service.listProcesses(domainId).get(0).lifecycleStatus()).isEqualTo("RETIRED");
        } finally {
            jdbc.update("delete from sprint64_bus_matrix where domain_id = ?", domainId);
            jdbc.update("delete from sprint64_business_process where domain_id = ?", domainId);
        }
    }

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
