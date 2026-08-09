package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.repository.modeling.DataMartRepository;
import com.yuzhi.dts.platform.repository.modeling.DataMartRepository.PlanBinding;
import com.yuzhi.dts.platform.repository.modeling.SubjectDomainRepository;
import com.yuzhi.dts.platform.repository.modeling.WarehouseLayerRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.DataMartApplicationService;
import com.yuzhi.dts.platform.service.modeling.DataMartContract.CreateCommand;
import com.yuzhi.dts.platform.service.modeling.DataMartContract.PlanBaselineCommand;
import com.yuzhi.dts.platform.service.modeling.SubjectDomainApplicationService;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehouseLayerApplicationService;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehouseLayerContract.CreateWarehouseLayerCommand;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ArchitectureDictionaryOwnerAuthorizationTest {

    private static final String TENANT = "default";
    private static final String ACTOR = "xiezm";

    private final ArchitectureDictionaryWriteGuard writeGuard = mock(ArchitectureDictionaryWriteGuard.class);
    private final AuditService auditService = mock(AuditService.class);

    @Test
    void warehouseLayerWriteStopsBeforeRepositoryWhenActorIsReadOnly() {
        WarehouseLayerRepository repository = mock(WarehouseLayerRepository.class);
        WarehouseLayerApplicationService service = new WarehouseLayerApplicationService(
            repository,
            auditService,
            writeGuard
        );
        denyWrite();

        assertThatThrownBy(() ->
            service.create(
                ACTOR,
                new CreateWarehouseLayerCommand("FIN_DETAIL", "财务明细层", "DWD", null, "fin_dwd_")
            )
        ).isInstanceOfSatisfying(ResponseStatusException.class, error ->
            org.assertj.core.api.Assertions.assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN)
        );

        verifyNoInteractions(repository);
    }

    @Test
    void dataMartWriteStopsBeforeRepositoryWhenActorIsReadOnly() {
        DataMartRepository repository = mock(DataMartRepository.class);
        DataMartApplicationService service = new DataMartApplicationService(
            repository,
            auditService,
            new ObjectMapper(),
            writeGuard
        );
        denyWrite();

        assertThatThrownBy(() ->
            service.create(
                TENANT,
                ACTOR,
                new CreateCommand(
                    "FIN_MART",
                    "财务集市",
                    "财务分析",
                    ACTOR,
                    List.of(UUID.randomUUID()),
                    "create-fin-mart"
                )
            )
        ).isInstanceOf(ResponseStatusException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void subjectDomainWriteStopsBeforeRepositoryWhenActorIsReadOnly() {
        SubjectDomainRepository repository = mock(SubjectDomainRepository.class);
        SubjectDomainApplicationService service = new SubjectDomainApplicationService(
            repository,
            auditService,
            new ObjectMapper(),
            writeGuard
        );
        denyWrite();

        assertThatThrownBy(() ->
            service.create(
                TENANT,
                ACTOR,
                new com.yuzhi.dts.platform.service.modeling.SubjectDomainContract.CreateCommand(
                    "BUDGET",
                    "预算主题",
                    "预算分析",
                    UUID.randomUUID(),
                    "create-budget"
                )
            )
        ).isInstanceOf(ResponseStatusException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void departmentScopedPlanBaselineDoesNotUseGlobalDictionaryGuard() {
        DataMartRepository repository = mock(DataMartRepository.class);
        DataMartApplicationService service = new DataMartApplicationService(
            repository,
            auditService,
            new ObjectMapper(),
            writeGuard
        );
        UUID planId = UUID.randomUUID();
        when(repository.readPlanBinding(TENANT, planId)).thenReturn(new PlanBinding(planId, List.of(), 0));
        when(repository.allCurrentForPlan(TENANT, planId, List.of())).thenReturn(true);
        when(repository.replacePlanBinding(eq(TENANT), eq(ACTOR), eq(planId), eq(0), eq(List.of()), any()))
            .thenReturn(1);

        service.savePlanBaseline(TENANT, ACTOR, planId, new PlanBaselineCommand(List.of(), 0));

        verifyNoInteractions(writeGuard);
    }

    private void denyWrite() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "read only"))
            .when(writeGuard)
            .requireWriteAccess();
    }
}
