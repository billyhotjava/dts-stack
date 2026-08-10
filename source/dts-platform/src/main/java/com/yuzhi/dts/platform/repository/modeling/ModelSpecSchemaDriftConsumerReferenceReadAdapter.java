package com.yuzhi.dts.platform.repository.modeling;

import com.yuzhi.dts.platform.service.catalog.SchemaDriftConsumerReferenceReadPort;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads field-level usage from the current canonical ModelSpec heads without creating a second ledger. */
@Repository
public class ModelSpecSchemaDriftConsumerReferenceReadAdapter implements SchemaDriftConsumerReferenceReadPort {

    private static final Logger LOG = LoggerFactory.getLogger(ModelSpecSchemaDriftConsumerReferenceReadAdapter.class);

    private final JdbcTemplate jdbcTemplate;

    public ModelSpecSchemaDriftConsumerReferenceReadAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Set<String>> findCurrentReferencedFields(
        UUID catalogTableId,
        UUID connectionId,
        String namespace,
        String objectName
    ) {
        try {
            List<String> fields = jdbcTemplate.queryForList(
                """
                select distinct lower(regexp_replace(field_item ->> 'sourceFieldRef', '^.*\\.', '')) as field_name
                  from modeling_warehouse_plan_source source_binding
                  join modeling_model_spec model_spec
                    on model_spec.tenant_id = source_binding.tenant_id
                   and model_spec.plan_id = source_binding.plan_id
                   and model_spec.contract_version = 2
                   and model_spec.status <> 'ARCHIVED'
                  cross join lateral jsonb_array_elements(coalesce(model_spec.source_refs, '[]'::jsonb)) source_ref
                  cross join lateral jsonb_array_elements(coalesce(model_spec.fields, '[]'::jsonb)) field_item
                 where source_ref ->> 'sourceBindingId' = source_binding.id::text
                   and nullif(btrim(field_item ->> 'sourceFieldRef'), '') is not null
                   and (
                       nullif(btrim(source_ref ->> 'alias'), '') is null
                       or position('.' in field_item ->> 'sourceFieldRef') = 0
                       or lower(split_part(field_item ->> 'sourceFieldRef', '.', 1)) = lower(source_ref ->> 'alias')
                   )
                   and (
                       (
                           source_binding.source_type = 'CATALOG_TABLE'
                           and coalesce(
                               nullif(source_binding.locator_json ->> 'assetId', ''),
                               source_binding.source_id
                           ) = ?
                       )
                       or (
                           source_binding.source_type = 'CONNECTION_TABLE'
                           and nullif(source_binding.locator_json ->> 'connectionId', '') = ?
                           and lower(nullif(source_binding.locator_json ->> 'namespace', '')) = lower(?)
                           and lower(nullif(source_binding.locator_json ->> 'objectName', '')) = lower(?)
                       )
                   )
                 order by field_name
                """,
                String.class,
                catalogTableId == null ? null : catalogTableId.toString(),
                connectionId == null ? null : connectionId.toString(),
                namespace,
                objectName
            );
            Set<String> normalized = new LinkedHashSet<>();
            for (String field : fields) {
                if (field != null && !field.isBlank()) {
                    normalized.add(field.trim().toLowerCase(Locale.ROOT));
                }
            }
            return Optional.of(Set.copyOf(normalized));
        } catch (RuntimeException exception) {
            LOG.warn(
                "[schema-drift-consumers] lookup failed tableId={} connectionId={} namespace={} object={}: {}",
                catalogTableId,
                connectionId,
                namespace,
                objectName,
                exception.getMessage()
            );
            return Optional.empty();
        }
    }
}
