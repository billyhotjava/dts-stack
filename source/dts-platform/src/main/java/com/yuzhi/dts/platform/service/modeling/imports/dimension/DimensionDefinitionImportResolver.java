package com.yuzhi.dts.platform.service.modeling.imports.dimension;

import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract;
import com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Resolves a package's stable dimension system code to a visible CURRENT definition head. */
@Repository
public class DimensionDefinitionImportResolver {

    private final JdbcTemplate jdbcTemplate;
    private final CatalogDomainResolutionPort domainResolver;

    public DimensionDefinitionImportResolver(JdbcTemplate jdbcTemplate, CatalogDomainResolutionPort domainResolver) {
        this.jdbcTemplate = jdbcTemplate;
        this.domainResolver = domainResolver;
    }

    @Transactional(readOnly = true)
    public Optional<ResolvedDimensionDefinition> resolveCurrent(
        String tenantId,
        UUID planId,
        UUID domainId,
        String systemCode
    ) {
        if (
            tenantId == null ||
            tenantId.isBlank() ||
            planId == null ||
            domainId == null ||
            !ModelPackageContract.isDimensionDefinitionCode(systemCode)
        ) {
            return Optional.empty();
        }
        CatalogDomainResolutionPort.DomainResolution domain = domainResolver.resolve(domainId);
        if (domain == null || domain.status() != CatalogDomainResolutionPort.ResolutionStatus.AVAILABLE) {
            return Optional.empty();
        }
        return jdbcTemplate
            .query(
                """
                select d.id, d.revision, d.system_code, d.domain_id
                  from modeling_dimension_definition d
                  join modeling_warehouse_plan_domain plan_domain
                    on plan_domain.tenant_id = d.tenant_id
                   and plan_domain.domain_id = d.domain_id
                 where d.tenant_id = ?
                   and plan_domain.plan_id = ?
                   and plan_domain.confirmation_status = 'CONFIRMED'
                   and d.domain_id = ?
                   and d.system_code = ?
                   and d.status = 'CURRENT'
                """,
                (row, rowNumber) ->
                    new ResolvedDimensionDefinition(
                        row.getObject("id", UUID.class),
                        row.getInt("revision"),
                        row.getString("system_code"),
                        row.getObject("domain_id", UUID.class)
                    ),
                tenantId.trim(),
                planId,
                domainId,
                systemCode
            )
            .stream()
            .findFirst();
    }

    public record ResolvedDimensionDefinition(UUID id, int revision, String systemCode, UUID domainId) {}
}
