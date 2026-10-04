package com.yuzhi.dts.platform.service.modeling.imports.dimension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.modeling.imports.dimension.DimensionDefinitionImportResolver.ResolvedDimensionDefinition;
import com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort;
import com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort.DomainResolution;
import com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort.ResolutionStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class DimensionDefinitionImportResolverTest {

    private static final String TENANT = "tenant-a";
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID DOMAIN_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID DEFINITION_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final String SYSTEM_CODE = "dim_f691053e433443f49263f067372c5943";

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void resolvesCurrentDefinitionFromVisibleGlobalDomainWithoutLegacyPlanDomainMembership() {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        CatalogDomainResolutionPort domains = org.mockito.Mockito.mock(CatalogDomainResolutionPort.class);
        DimensionDefinitionImportResolver resolver = new DimensionDefinitionImportResolver(jdbc, domains);
        when(domains.resolve(DOMAIN_ID)).thenReturn(availableDomain());
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenReturn(List.of(new ResolvedDimensionDefinition(DEFINITION_ID, 2, SYSTEM_CODE, DOMAIN_ID)));

        var resolved = resolver.resolveCurrent(" tenant-a ", PLAN_ID, DOMAIN_ID, SYSTEM_CODE);

        assertThat(resolved).contains(new ResolvedDimensionDefinition(DEFINITION_ID, 2, SYSTEM_CODE, DOMAIN_ID));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), arguments.capture());
        assertThat(sql.getValue())
            .contains("d.tenant_id = ?", "d.domain_id = ?", "d.system_code = ?", "d.status = 'CURRENT'")
            .doesNotContain("modeling_warehouse_plan_domain", "plan_domain");
        assertThat(arguments.getValue()).containsExactly(TENANT, DOMAIN_ID, SYSTEM_CODE);
    }

    @Test
    void rejectsUnavailableDomainBeforeDatabaseAccess() {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        CatalogDomainResolutionPort domains = org.mockito.Mockito.mock(CatalogDomainResolutionPort.class);
        DimensionDefinitionImportResolver resolver = new DimensionDefinitionImportResolver(jdbc, domains);
        when(domains.resolve(DOMAIN_ID))
            .thenReturn(new DomainResolution(DOMAIN_ID, ResolutionStatus.FORBIDDEN, null, null, null, null));

        assertThat(resolver.resolveCurrent(TENANT, PLAN_ID, DOMAIN_ID, SYSTEM_CODE)).isEmpty();

        verify(jdbc, never()).query(anyString(), any(RowMapper.class), any(Object[].class));
    }

    @Test
    void stillRequiresModelingContextAndCanonicalDimensionCode() {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        CatalogDomainResolutionPort domains = org.mockito.Mockito.mock(CatalogDomainResolutionPort.class);
        DimensionDefinitionImportResolver resolver = new DimensionDefinitionImportResolver(jdbc, domains);

        assertThat(resolver.resolveCurrent(TENANT, null, DOMAIN_ID, SYSTEM_CODE)).isEmpty();
        assertThat(resolver.resolveCurrent(TENANT, PLAN_ID, DOMAIN_ID, "quality_status")).isEmpty();

        verify(domains, never()).resolve(any());
        verify(jdbc, never()).query(anyString(), any(RowMapper.class), any(Object[].class));
    }

    private static DomainResolution availableDomain() {
        return new DomainResolution(DOMAIN_ID, ResolutionStatus.AVAILABLE, "质量域", "QUALITY", null, null);
    }
}
