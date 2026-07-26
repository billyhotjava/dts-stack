package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CatalogDatasetClassificationProjection implements CatalogClassificationProjection {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CatalogDatasetClassificationProjection(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean supports(CatalogClassificationSnapshot snapshot) {
        return (
            snapshot != null &&
            "ASSET".equals(snapshot.getSubjectType()) &&
            "DATASET".equals(snapshot.getAssetType())
        );
    }

    @Override
    public void project(CatalogClassificationSnapshot snapshot) {
        jdbcTemplate.update(
            """
            update catalog_dataset dataset
               set classification = :effectiveLevel,
                   last_modified_date = current_timestamp
             where (
                    'source:' || coalesce(dataset.source_id::text, 'unknown')
                    || '/schema:' || regexp_replace(
                        regexp_replace(
                            regexp_replace(
                                lower(trim(coalesce(nullif(trim(dataset.hive_database), ''), 'default'))),
                                '[^a-z0-9_.:-]+',
                                '_',
                                'g'
                            ),
                            '_+',
                            '_',
                            'g'
                        ),
                        '^_+|_+$',
                        '',
                        'g'
                    )
                    || '/table:' || regexp_replace(
                        regexp_replace(
                            regexp_replace(
                                lower(trim(coalesce(nullif(trim(dataset.hive_table), ''), dataset.name))),
                                '[^a-z0-9_.:-]+',
                                '_',
                                'g'
                            ),
                            '_+',
                            '_',
                            'g'
                        ),
                        '^_+|_+$',
                        '',
                        'g'
                    )
                 ) = :subjectKey
               and case upper(coalesce(dataset.classification, ''))
                       when 'PUBLIC' then 0
                       when 'INTERNAL' then 1
                       when 'SECRET' then 2
                       when 'CONFIDENTIAL' then 3
                       else -1
                   end
                   <= case :effectiveLevel
                       when 'PUBLIC' then 0
                       when 'INTERNAL' then 1
                       when 'SECRET' then 2
                       when 'CONFIDENTIAL' then 3
                       else -1
                   end
            """,
            Map.of(
                "effectiveLevel",
                snapshot.getEffectiveLevel(),
                "subjectKey",
                snapshot.getSubjectKey()
            )
        );
    }
}
