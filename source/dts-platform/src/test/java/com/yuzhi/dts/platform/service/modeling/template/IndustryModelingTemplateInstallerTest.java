package com.yuzhi.dts.platform.service.modeling.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.platform.IntegrationTest;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.BusinessProcessRequest;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.BusMatrixRequest;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.ConformedDimensionRequest;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.DimensionInUseException;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.ModelingCandidateConfirmationRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@IntegrationTest
class IndustryModelingTemplateInstallerTest {

    private final UUID domainId = UUID.randomUUID();

    @Autowired
    private IndustryModelingTemplateCatalog catalog;

    @Autowired
    private IndustryModelingTemplateInstaller installer;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private Sprint64GovernanceService governanceService;

    @AfterEach
    void cleanDomainRows() {
        MapSqlParameterSource params = new MapSqlParameterSource("domainId", domainId);
        jdbc.update("delete from sprint64_bus_matrix where domain_id = :domainId", params);
        jdbc.update("delete from sprint64_conformed_dimension where domain_id = :domainId", params);
        jdbc.update("delete from sprint64_business_process where domain_id = :domainId", params);
    }

    @Test
    void installsOnceAndPreservesDomainOwnedChangesOnRepeatedInstall() {
        var template = catalog.requireTemplate("pjm");

        var first = installer.install(domainId, template);

        assertThat(first.status()).isEqualTo(IndustryModelingTemplateInstaller.InstallationStatus.INSTALLED);
        assertThat(first.createdProcesses()).isEqualTo(3);
        assertThat(first.createdDimensions()).isEqualTo(8);
        assertThat(count("sprint64_business_process")).isEqualTo(3);
        assertThat(count("sprint64_conformed_dimension")).isEqualTo(8);

        jdbc.update(
            "update sprint64_business_process set name = '客户自定义过程' where domain_id = :domainId and process_id = 'node-plan-loop'",
            new MapSqlParameterSource("domainId", domainId)
        );
        jdbc.update(
            "update sprint64_conformed_dimension set name = '客户自定义维度' where domain_id = :domainId and dimension_id = 'completion-status'",
            new MapSqlParameterSource("domainId", domainId)
        );

        var repeated = installer.install(domainId, template);

        assertThat(repeated.status()).isEqualTo(IndustryModelingTemplateInstaller.InstallationStatus.ALREADY_INSTALLED);
        assertThat(repeated.createdProcesses()).isZero();
        assertThat(repeated.createdDimensions()).isZero();
        assertThat(name("sprint64_business_process", "process_id", "node-plan-loop")).isEqualTo("客户自定义过程");
        assertThat(name("sprint64_conformed_dimension", "dimension_id", "completion-status")).isEqualTo("客户自定义维度");
    }

    @Test
    void blankDomainStaysEmptyAndManualProcessIsPersistedAsDomainOwnedData() {
        assertThat(governanceService.listProcesses(domainId)).isEmpty();
        assertThat(governanceService.listConformedDimensions(domainId)).isEmpty();

        var created = governanceService.createProcess(
            domainId,
            new Sprint64GovernanceService.BusinessProcessRequest("manual-process", "手工过程", "当前领域自行定义")
        );

        assertThat(created.sourceType()).isEqualTo("MANUAL");
        assertThat(created.sourceId()).isNull();
        assertThat(created.confirmed()).isTrue();
        assertThat(governanceService.listProcesses(domainId)).extracting(Sprint64GovernanceService.BusinessProcessDto::processId).containsExactly("manual-process");
        assertThat(governanceService.listConformedDimensions(domainId)).isEmpty();
    }

    @Test
    void manualDimensionIsImmediatelyConfirmedAndCanEnterTheMatrix() {
        governanceService.createProcess(domainId, new BusinessProcessRequest("manual-process", "手工过程", null));

        var dimension = governanceService.createConformedDimension(
            domainId,
            new ConformedDimensionRequest("organization", "组织机构", "dim_organization")
        );

        assertThat(dimension.sourceType()).isEqualTo("MANUAL");
        assertThat(dimension.confirmed()).isTrue();
        assertThat(
            governanceService.saveBusMatrix(domainId, new BusMatrixRequest("manual-process", "organization", true)).enabled()
        ).isTrue();
    }

    @Test
    void templateFactsMustBeConfirmedAtomicallyBeforeTheyEnterTheMatrix() {
        installer.install(domainId, catalog.requireTemplate("pjm"));

        assertThatThrownBy(() ->
            governanceService.saveBusMatrix(domainId, new BusMatrixRequest("node-plan-loop", "completion-status", true))
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("尚未确认");

        var result = governanceService.confirmModelingCandidates(
            domainId,
            new ModelingCandidateConfirmationRequest(List.of("node-plan-loop"), List.of("completion-status"))
        );

        assertThat(result.confirmedProcesses()).isEqualTo(1);
        assertThat(result.confirmedDimensions()).isEqualTo(1);
        assertThat(
            governanceService.saveBusMatrix(domainId, new BusMatrixRequest("node-plan-loop", "completion-status", true)).enabled()
        ).isTrue();
    }

    @Test
    void confirmationRejectsForeignDomainIdsWithoutPartialUpdates() {
        installer.install(domainId, catalog.requireTemplate("pjm"));

        assertThatThrownBy(() ->
            governanceService.confirmModelingCandidates(
                UUID.randomUUID(),
                new ModelingCandidateConfirmationRequest(List.of("node-plan-loop"), List.of("completion-status"))
            )
        ).isInstanceOf(IllegalArgumentException.class);

        assertThat(governanceService.listProcesses(domainId)).allMatch(item -> !item.confirmed());
        assertThat(governanceService.listConformedDimensions(domainId)).allMatch(item -> !item.confirmed());
    }

    @Test
    void referencedDimensionCannotBeDeleted() {
        governanceService.createProcess(domainId, new BusinessProcessRequest("manual-process", "手工过程", null));
        governanceService.createConformedDimension(domainId, new ConformedDimensionRequest("organization", "组织机构", null));
        governanceService.saveBusMatrix(domainId, new BusMatrixRequest("manual-process", "organization", true));

        assertThatThrownBy(() -> governanceService.deleteConformedDimension(domainId, "organization"))
            .isInstanceOf(DimensionInUseException.class);
    }

    private Integer count(String table) {
        return jdbc.queryForObject(
            "select count(*) from " + table + " where domain_id = :domainId",
            new MapSqlParameterSource("domainId", domainId),
            Integer.class
        );
    }

    private String name(String table, String keyColumn, String key) {
        return jdbc.queryForObject(
            "select name from " + table + " where domain_id = :domainId and " + keyColumn + " = :key",
            new MapSqlParameterSource(Map.of("domainId", domainId, "key", key)),
            String.class
        );
    }
}
