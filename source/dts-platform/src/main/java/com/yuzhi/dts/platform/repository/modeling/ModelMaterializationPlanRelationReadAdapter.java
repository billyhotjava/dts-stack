package com.yuzhi.dts.platform.repository.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.Snapshot;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationPlanRelationPort;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationPlanRelationPort.RelationObservation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Reads one latest observation per model without creating a materialization-side ledger. */
@Repository
public class ModelMaterializationPlanRelationReadAdapter implements ModelMaterializationPlanRelationPort {

    private final JdbcTemplate jdbcTemplate;

    public ModelMaterializationPlanRelationReadAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, RelationObservation> findLatest(
        String tenantId,
        UUID planId,
        String environment,
        String executionTargetKey,
        String adapter,
        List<Snapshot> snapshots
    ) {
        List<UUID> modelIds = snapshots == null
            ? List.of()
            : snapshots.stream().map(Snapshot::modelSpecId).distinct().sorted().toList();
        if (modelIds.isEmpty()) return Map.of();
        String placeholders = String.join(", ", java.util.Collections.nCopies(modelIds.size(), "?"));
        String sql = """
            with ranked as (
                select o.model_spec_id, o.model_revision, o.model_checksum,
                       o.implementation_revision, o.implementation_checksum,
                       o.id as relation_evidence_id, o.metadata_checksum,
                       o.verified, o.relation_exists,
                       c.execution_target_key, o.adapter,
                       o.database_name, o.schema_name, o.identifier,
                       row_number() over (
                           partition by o.model_spec_id
                           order by o.release_candidate_version desc,
                                    d.attempt desc,
                                    o.observation_attempt desc,
                                    o.observed_at desc,
                                    o.created_date desc,
                                    o.id desc
                       ) as observation_rank
                  from modeling_physical_relation_observation o
                  join modeling_model_release_candidate c
                    on c.tenant_id = o.tenant_id and c.id = o.release_candidate_id
                  join modeling_materialization_dispatch d
                    on d.tenant_id = o.tenant_id and d.id = o.pipeline_run_group_id
                   and d.candidate_id = c.id
                  join modeling_pipeline_run p
                    on p.id = o.pipeline_run_id and p.pipeline_run_group_id = d.id
                   and p.run_purpose = 'RELEASE_BUILD'
                 where o.tenant_id = ? and (c.plan_id = ? or c.status = 'PUBLISHED') and c.environment = ?
                   and c.execution_target_key = ? and lower(o.adapter) = lower(?)
                   and o.model_spec_id in (%s)
                   and c.status in ('BUILT', 'QUALITY_RUNNING', 'QUALITY_PASSED', 'REVIEW_PENDING',
                                    'APPROVED', 'PUBLISHING', 'PARTIAL', 'PUBLISHED')
                   and d.status = 'COMPLETED' and p.status = 'BUILT'
            )
            select * from ranked where observation_rank = 1 order by model_spec_id
            """.formatted(placeholders);
        List<Object> arguments = new ArrayList<>();
        arguments.add(tenantId);
        arguments.add(planId);
        arguments.add(environment);
        arguments.add(executionTargetKey);
        arguments.add(adapter);
        arguments.addAll(modelIds);
        LinkedHashMap<UUID, RelationObservation> result = new LinkedHashMap<>();
        jdbcTemplate.query(
            sql,
            row -> {
                RelationObservation observation = new RelationObservation(
                    row.getObject("model_spec_id", UUID.class),
                    row.getInt("model_revision"),
                    row.getString("model_checksum"),
                    row.getInt("implementation_revision"),
                    row.getString("implementation_checksum"),
                    row.getObject("relation_evidence_id", UUID.class),
                    row.getString("metadata_checksum"),
                    row.getBoolean("verified"),
                    row.getBoolean("relation_exists"),
                    row.getString("execution_target_key"),
                    row.getString("adapter"),
                    row.getString("database_name"),
                    row.getString("schema_name"),
                    row.getString("identifier")
                );
                result.put(observation.modelSpecId(), observation);
            },
            arguments.toArray()
        );
        return Map.copyOf(result);
    }
}
