package com.yuzhi.dts.platform.service.modeling.serving;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Exact, read-only adapter for published atomic indicators bound to one ModelSpec revision. */
@Component
@Transactional(readOnly = true)
public class CatalogModelSemanticIndicatorReadAdapter {

    private final JdbcTemplate jdbcTemplate;

    public CatalogModelSemanticIndicatorReadAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AtomicIndicator> findPublishedAtomicIndicators(UUID modelSpecId, int modelRevision) {
        Objects.requireNonNull(modelSpecId, "modelSpecId is required");
        if (modelRevision < 1) {
            throw new IllegalArgumentException("modelRevision must be positive");
        }
        return jdbcTemplate.query(
            """
            select indicator.code, indicator.name, indicator.definition,
                   indicator.aggregation_type, indicator.measure_field,
                   indicator.date_column, indicator.time_grain, indicator.unit,
                   indicator.tags, indicator.data_level, indicator.version
              from gov_indicator_definition indicator
             where upper(coalesce(indicator.status, '')) = 'PUBLISHED'
               and upper(coalesce(indicator.metric_type, '')) = 'ATOMIC'
               and exists (
                   select 1
                     from jsonb_array_elements(
                         case
                             when jsonb_typeof(coalesce(indicator.source_refs, '[]'::jsonb)) = 'array'
                                 then coalesce(indicator.source_refs, '[]'::jsonb)
                             else '[]'::jsonb
                         end
                     ) source_ref
                    where upper(coalesce(source_ref ->> 'sourceType', '')) = 'SEMANTIC_MODEL_REVISION'
                      and source_ref ->> 'sourceId' = ?
                      and source_ref ->> 'sourceVersion' = ?
               )
             order by indicator.code
            """,
            (row, rowNumber) -> new AtomicIndicator(
                row.getString("code"),
                row.getString("name"),
                row.getString("definition"),
                row.getString("aggregation_type"),
                row.getString("measure_field"),
                row.getString("date_column"),
                row.getString("time_grain"),
                row.getString("unit"),
                row.getString("tags"),
                row.getString("data_level"),
                row.getString("version")
            ),
            modelSpecId.toString(),
            "r" + modelRevision
        );
    }

    public record AtomicIndicator(
        String code,
        String name,
        String definition,
        String aggregationType,
        String measureField,
        String dateColumn,
        String timeGrain,
        String unit,
        String tags,
        String dataLevel,
        String version
    ) {}
}
