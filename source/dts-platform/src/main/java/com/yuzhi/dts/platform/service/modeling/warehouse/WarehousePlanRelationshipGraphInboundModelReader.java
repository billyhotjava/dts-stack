package com.yuzhi.dts.platform.service.modeling.warehouse;

import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphProjectionSupport.POSTGRES_UUID_ORDER;

import com.yuzhi.dts.platform.service.modeling.ModelSpecContract;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecDomainReadAccessPort;
import com.yuzhi.dts.platform.service.modeling.ModelSpecFeatureFlags;
import com.yuzhi.dts.platform.service.modeling.ModelingVNextContract;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.support.SqlArrayValue;

/**
 * Bounded inbound lookup used only to reconstruct cross-window model edges.
 *
 * <p>The lookup returns authorized earlier canonical roots as exact current revisions. Snapshot
 * decoding remains in {@code ModelSpecApplicationService}; this component only discovers source
 * identities through bind parameters.
 */
@Repository
class WarehousePlanRelationshipGraphInboundModelReader {

    private static final int MAX_TARGETS = 500;
    private static final int MAX_SOURCE_LOOKAHEAD = 501;

    private final JdbcTemplate jdbcTemplate;
    private final ModelSpecDomainReadAccessPort domainReadAccess;
    private final ModelSpecFeatureFlags featureFlags;

    WarehousePlanRelationshipGraphInboundModelReader(
        JdbcTemplate jdbcTemplate,
        ModelSpecDomainReadAccessPort domainReadAccess,
        ModelSpecFeatureFlags featureFlags
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.domainReadAccess = domainReadAccess;
        this.featureFlags = featureFlags;
    }

    List<ModelRevisionRef> listCurrentTargets(
        String tenantId,
        UUID planId,
        Collection<ModelRevisionRef> targets
    ) {
        requireScope(tenantId, planId);
        List<ModelRevisionRef> boundedTargets = boundedTargets(targets);
        if (boundedTargets.isEmpty()) return List.of();
        List<UUID> orderedDomainIds = visibleDomainIds();
        StringBuilder sql = new StringBuilder(
            """
            select s.id, s.revision
              from modeling_model_spec s
             where s.tenant_id = ?
               and s.plan_id = ?
            """
        );
        List<Object> arguments = new ArrayList<>();
        arguments.add(tenantId);
        arguments.add(planId);
        appendCanonicalScope(sql, arguments);
        appendDomainScope(sql, arguments, orderedDomainIds);
        sql.append(" and (");
        for (int index = 0; index < boundedTargets.size(); index++) {
            if (index > 0) sql.append(" or ");
            sql.append("(s.id = ? and s.revision = ?)");
            ModelRevisionRef target = boundedTargets.get(index);
            arguments.add(target.modelSpecId());
            arguments.add(target.revision());
        }
        sql.append(") order by s.id limit ?");
        arguments.add(MAX_TARGETS);
        return queryReferences(sql, arguments);
    }

    List<ModelRevisionRef> listEarlierSources(
        String tenantId,
        UUID planId,
        UUID throughRootId,
        Collection<ModelRevisionRef> targets,
        int sourceLimit
    ) {
        requireScope(tenantId, planId);
        if (
            throughRootId == null ||
            sourceLimit < 1 ||
            sourceLimit > MAX_SOURCE_LOOKAHEAD
        ) {
            throw new IllegalArgumentException(
                "Relationship graph inbound lookup scope is invalid"
            );
        }
        List<ModelRevisionRef> boundedTargets = boundedTargets(targets);
        if (boundedTargets.isEmpty()) return List.of();

        List<UUID> orderedDomainIds = visibleDomainIds();
        StringBuilder sql = new StringBuilder(
            """
            select s.id, s.revision
              from modeling_model_spec s
             where s.tenant_id = ?
               and s.plan_id = ?
            """
        );
        List<Object> arguments = new ArrayList<>();
        arguments.add(tenantId);
        arguments.add(planId);
        appendCanonicalScope(sql, arguments);
        appendDomainScope(sql, arguments, orderedDomainIds);
        sql.append(" and (");
        for (int index = 0; index < boundedTargets.size(); index++) {
            if (index > 0) sql.append(" or ");
            sql.append(
                """
                (
                    s.id < ?
                    and (
                        s.depends_on @> cast(? as jsonb)
                        or s.dimension_refs @> cast(? as jsonb)
                    )
                )
                """
            );
            ModelRevisionRef target = boundedTargets.get(index);
            arguments.add(target.modelSpecId());
            String probe = targetProbe(target);
            arguments.add(probe);
            arguments.add(probe);
        }
        sql.append(") order by s.id limit ?");
        arguments.add(sourceLimit);
        /*
         * Keep candidate discovery on the two GIN containment indexes and bound it with the 501
         * lookahead. Applying the previous-root predicate in SQL can turn this back into a history
         * prefix scan; filtering the ordered hits here preserves the PostgreSQL UUID window order.
         */
        return queryReferences(sql, arguments)
            .stream()
            .filter(source ->
                source != null &&
                source.modelSpecId() != null &&
                POSTGRES_UUID_ORDER.compare(
                    source.modelSpecId(),
                    throughRootId
                ) <=
                0
            )
            .toList();
    }

    private static void requireScope(String tenantId, UUID planId) {
        if (tenantId == null || tenantId.isBlank() || planId == null) {
            throw new IllegalArgumentException(
                "Relationship graph inbound lookup scope is invalid"
            );
        }
    }

    private List<UUID> visibleDomainIds() {
        Set<UUID> visibleDomainIds = domainReadAccess.visibleDomainIds();
        return (visibleDomainIds == null ? Set.<UUID>of() : visibleDomainIds)
            .stream()
            .filter(Objects::nonNull)
            .sorted(POSTGRES_UUID_ORDER)
            .toList();
    }

    private void appendCanonicalScope(
        StringBuilder sql,
        List<Object> arguments
    ) {
        if (!featureFlags.canonicalReadEnabled()) {
            sql.append(" and s.contract_version <> ?");
            arguments.add(ModelSpecContract.CONTRACT_VERSION);
        }
    }

    private List<ModelRevisionRef> queryReferences(
        StringBuilder sql,
        List<Object> arguments
    ) {
        return jdbcTemplate.query(
            sql.toString(),
            (row, rowNumber) ->
                new ModelRevisionRef(
                    row.getObject("id", UUID.class),
                    row.getInt("revision")
                ),
            arguments.toArray()
        );
    }

    private static List<ModelRevisionRef> boundedTargets(
        Collection<ModelRevisionRef> targets
    ) {
        if (targets == null || targets.isEmpty()) return List.of();
        LinkedHashSet<ModelRevisionRef> unique = new LinkedHashSet<>();
        int inspected = 0;
        for (ModelRevisionRef target : targets) {
            inspected++;
            if (inspected > MAX_TARGETS) {
                throw new IllegalArgumentException(
                    "Relationship graph inbound targets exceed 500"
                );
            }
            if (
                target != null &&
                target.modelSpecId() != null &&
                target.revision() > 0
            ) {
                unique.add(target);
            }
        }
        if (unique.size() > MAX_TARGETS) {
            throw new IllegalArgumentException(
                "Relationship graph inbound targets exceed 500"
            );
        }
        return unique
            .stream()
            .sorted(
                Comparator
                    .comparing(
                        ModelRevisionRef::modelSpecId,
                        POSTGRES_UUID_ORDER
                    )
                    .thenComparingInt(ModelRevisionRef::revision)
            )
            .toList();
    }

    private static String targetProbe(ModelRevisionRef target) {
        return (
            "[{\"modelSpecId\":\"" +
            target.modelSpecId() +
            "\",\"revision\":" +
            target.revision() +
            "}]"
        );
    }

    private static void appendDomainScope(
        StringBuilder sql,
        List<Object> arguments,
        List<UUID> orderedDomainIds
    ) {
        sql.append(" and (");
        if (orderedDomainIds.isEmpty()) {
            sql.append(
                "s.domain_id is null and s.contract_version = ?"
            );
        } else {
            sql
                .append(
                    "s.domain_id = ANY (?::uuid[]) or (s.domain_id is null and s.contract_version = ?)"
                );
            arguments.add(
                new SqlArrayValue(
                    "uuid",
                    orderedDomainIds.toArray(UUID[]::new)
                )
            );
        }
        arguments.add(ModelingVNextContract.CONTRACT_VERSION);
        sql.append(')');
    }
}
