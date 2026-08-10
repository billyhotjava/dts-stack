package com.yuzhi.dts.platform.repository.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelWorkbenchCatalogContract.CatalogEntry;
import com.yuzhi.dts.platform.service.modeling.ModelWorkbenchCatalogContract.CatalogPage;
import com.yuzhi.dts.platform.service.modeling.ModelWorkbenchCatalogContract.CatalogQuery;
import com.yuzhi.dts.platform.service.modeling.ModelWorkbenchCatalogContract.EntryKind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Two-query, domain-scoped SQL projection for the model workbench. */
@Repository
public class ModelWorkbenchCatalogRepository {

    private final JdbcTemplate jdbcTemplate;

    public ModelWorkbenchCatalogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CatalogPage page(String tenantId, Set<UUID> visibleDomainIds, CatalogQuery query) {
        List<UUID> domains = visibleDomainIds.stream().sorted().toList();
        if (domains.isEmpty()) return CatalogPage.empty(query.page(), query.size());

        SqlQuery catalog = catalogSql(tenantId, domains, query);
        Long total = jdbcTemplate.queryForObject(
            catalog.sql() + " select count(*) from catalog" + catalog.whereClause(),
            Long.class,
            catalog.arguments().toArray()
        );
        long totalElements = total == null ? 0 : total;
        if (totalElements == 0) return CatalogPage.empty(query.page(), query.size());

        ArrayList<Object> pageArguments = new ArrayList<>(catalog.arguments());
        pageArguments.add(query.size());
        pageArguments.add((long) query.page() * query.size());
        List<CatalogEntry> content = jdbcTemplate.query(
            catalog.sql() +
            " select entry_kind, id, name, code, plan_id, domain_id, object_type, layer, status, revision" +
            " from catalog" +
            catalog.whereClause() +
            " order by lower(name), entry_kind, id limit ? offset ?",
            (row, rowNumber) ->
                new CatalogEntry(
                    EntryKind.valueOf(row.getString("entry_kind")),
                    row.getObject("id", UUID.class),
                    row.getString("name"),
                    row.getString("code"),
                    row.getObject("plan_id", UUID.class),
                    row.getObject("domain_id", UUID.class),
                    row.getString("object_type"),
                    row.getString("layer"),
                    row.getString("status"),
                    row.getInt("revision")
                ),
            pageArguments.toArray()
        );
        long pageCount = (totalElements + query.size() - 1) / query.size();
        return new CatalogPage(
            content,
            totalElements,
            query.page(),
            query.size(),
            (int) Math.min(Integer.MAX_VALUE, pageCount)
        );
    }

    private static SqlQuery catalogSql(String tenantId, List<UUID> domains, CatalogQuery query) {
        String placeholders = String.join(", ", Collections.nCopies(domains.size(), "?"));
        String sql = """
            with catalog as (
                select 'DIMENSION_DEFINITION' as entry_kind,
                       d.id, d.name, d.system_code as code, null::uuid as plan_id, d.domain_id,
                       'DIMENSION_DEFINITION' as object_type, null::varchar as layer, d.status, d.revision
                  from modeling_dimension_definition d
                 where d.tenant_id = ? and d.domain_id in (%s)
                union all
                select 'MODEL_SPEC' as entry_kind,
                       s.id, s.name,
                       coalesce(nullif(r.snapshot_json #>> '{implementationPolicy,physicalName}', ''), '—') as code,
                       s.plan_id, s.domain_id, s.model_type as object_type, s.layer, s.status, s.revision
                  from modeling_model_spec s
                  left join modeling_model_spec_revision r
                    on r.tenant_id = s.tenant_id
                   and r.model_spec_id = s.id
                   and r.revision = s.revision
                   and r.contract_version = 2
                 where s.tenant_id = ?
                   and s.contract_version = 2
                   and s.domain_id in (%s)
            )
            """.formatted(placeholders, placeholders);
        ArrayList<Object> arguments = new ArrayList<>();
        arguments.add(tenantId);
        arguments.addAll(domains);
        arguments.add(tenantId);
        arguments.addAll(domains);

        StringBuilder where = new StringBuilder(" where 1 = 1");
        if (query.query() != null) {
            where.append(" and (position(? in lower(name)) > 0 or position(? in lower(code)) > 0)");
            String searchText = query.query().toLowerCase(java.util.Locale.ROOT);
            arguments.add(searchText);
            arguments.add(searchText);
        }
        if (query.planId() != null) {
            where.append(" and plan_id = ?");
            arguments.add(query.planId());
        }
        if (query.domainId() != null) {
            where.append(" and domain_id = ?");
            arguments.add(query.domainId());
        }
        if (query.objectType() != null) {
            where.append(" and object_type = ?");
            arguments.add(query.objectType());
        }
        if (query.layer() != null) {
            where.append(" and layer = ?");
            arguments.add(query.layer().name());
        }
        if (query.status() != null) {
            where.append(" and status = ?");
            arguments.add(query.status());
        }
        return new SqlQuery(sql, where.toString(), List.copyOf(arguments));
    }

    private record SqlQuery(String sql, String whereClause, List<Object> arguments) {}
}
